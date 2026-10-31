package deepluckyblock.advancements;

import deepluckyblock.DeepLuckyBlockMod;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Random;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Système d'Advancements pour Deep Lucky Block (Forge 1.20.1).
 *
 * - Ne compte que les Deep Lucky Blocks (pas les autres lucky blocks)
 * - Déclenche le VRAI trigger custom "deep_lucky_block_broken" (pour les JSON)
 *   ET accorde directement les advancements (award par nom de critère)
 * - Milestones à 10, 50, 100, 500, 1000 (advancement + rewards brillants
 *   via DeepLuckyBlockAdvancementIntegration)
 * - Bonus +0-10 niveaux d'enchantements après 500 blocs cassés
 * - Advancement "negative_streak" via vrai trigger custom
 *
 * ⚠ PAS de "bus = ...MOD" : BlockEvent est posté sur le bus FORGE (défaut).
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public class DeepLuckyBlockAdvancements {

    private static final String MODID = DeepLuckyBlockMod.MODID;

    // ==================== CONFIGURATION ====================
    private static final int[] MILESTONES = {10, 50, 100, 500, 1000};
    private static final int BONUS_THRESHOLD = 500;

    // ==================== NBT KEYS ====================
    private static final String TAG_DEEP_LUCKY_BROKEN_COUNT = "slb_deep_lucky_blocks_broken";
    private static final String TAG_MILESTONE_ACHIEVED = "slb_milestone_achieved_";
    private static final String TAG_BONUS_ACTIVE = "slb_enchant_bonus_active";

    // ==================== BLOCK ID ====================
    // ✅ CORRIGÉ : nom de variable + "=" + tryParse (l'ancienne ligne ne compilait pas)
    private static final ResourceLocation DEEP_LUCKY_BLOCK_ID = ResourceLocation.tryParse(MODID + ":deep_lucky_block");

    // ==================== TRACK DEEP LUCKY BLOCK BREAK ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeepLuckyBlockBroken(BlockEvent.BreakEvent event) {
        // getLevel() renvoie un LevelAccessor, getPlayer() un Player -> instanceof
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();

        // Vérifie que c'est NOTRE Deep Lucky Block
        if (!isDeepLuckyBlock(serverLevel, pos)) return;

        // Incrémente le compteur persistant
        int count = getDeepLuckyBlockCount(player) + 1;
        setDeepLuckyBlockCount(player, count);

        // 1) Déclenche le VRAI trigger custom (advancements JSON qui l'utilisent)
        DeepLuckyBlockAdvancementTrigger.TRIGGER.get().trigger(player, count);

        // 2) Accord direct par critère (advancements JSON avec trigger "impossible"
        //    — le défaut MCreator — fonctionnent aussi, sans trigger custom)
        grantAdvancementFully(serverLevel, player, "root");
        grantAdvancementFully(serverLevel, player, "deep_lucky_block_broken");

        // Milestones
        checkAndGrantMilestones(serverLevel, player, count);

        // Active le bonus d'enchantements à 500 blocs
        if (count >= BONUS_THRESHOLD && !hasBonusActive(player)) {
            activateEnchantBonus(serverLevel, player);
        }
    }

    // ==================== NEGATIVE STREAK ====================

    /**
     * À appeler depuis GenerateLuckyItemProcedure quand tu détectes une série négative.
     * Déclenche le vrai trigger custom ET accorde l'advancement directement.
     */
    public static void triggerNegativeStreak(ServerPlayer player) {
        if (player == null) return;

        ServerLevel level = player.serverLevel();

        // Triggers les éventuels écouteurs — INSTANCE est l'objet enregistré dans
        // CriteriaTriggers (voir DeepLuckyBlockBrokenTrigger#onCommonSetup)
        DeepLuckyBlockBrokenTrigger.TRIGGER.get().trigger(player);
        grantHub(player, "hub_luck");
        grantAdvancementFully(level, player, "negative_streak");

        // 🔴 Bad Luck Streak : prix de consolation = +0-6 blocs brillants (glow 10 s)
        DeepLuckyBlockAdvancementIntegration.dropGlowingLuckyBlocks(level, player, level.random.nextInt(7));
    }

    // ==================== LUCKY STREAK ====================

    /**
     * À appeler depuis GenerateLuckyItemProcedure quand la série de chance monte
     * (ex. après le "+5 Luck"). Accorde l'achievement "lucky_streak"
     * et drop 🟢 +0-5 Deep Lucky Blocks brillants (glow 10 s).
     */
    public static void onLuckyStreak(ServerPlayer player) {
        if (player == null) return;

        ServerLevel level = player.serverLevel();
        grantHub(player, "hub_luck");
        grantAdvancementFully(level, player, "lucky_streak");
        // Chaîne de Chance : comptabilise cette série (3 -> achievement lucky_chain).
        onLuckyChain(player);

        // +0-5 blocs brillants
        DeepLuckyBlockAdvancementIntegration.dropGlowingLuckyBlocks(level, player, level.random.nextInt(6));
    }

    // ==================== VÉRIFICATION DEEP LUCKY BLOCK ====================

    private static boolean isDeepLuckyBlock(Level level, BlockPos pos) {
        // Méthode 1 : registry name exact du bloc
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (blockId != null && blockId.equals(DEEP_LUCKY_BLOCK_ID)) {
            return true;
        }

        // Méthode 2 : fallback via le BlockEntity si présent
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            ResourceLocation beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            return beId != null && beId.toString().toLowerCase().contains("deep_lucky_block");
        }

        return false;
    }

    // ==================== HELPER ADVANCEMENT ====================

    /** Accorde complètement un advancement (tous ses critères), s'il existe et n'est pas déjà obtenu. */
    private static void grantAdvancementFully(ServerLevel level, ServerPlayer player, String path) {
        AdvancementHolder advancement = level.getServer().getAdvancements().get(ResourceLocation.tryParse(MODID + ":" + path));
        if (advancement == null) return; // JSON absent -> ignoré silencieusement

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;

        // award() accorde UN critère -> on boucle sur tous les critères restants
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    // ==================== MILESTONES ====================

    private static void checkAndGrantMilestones(ServerLevel level, ServerPlayer player, int count) {
        for (int milestone : MILESTONES) {
            if (count >= milestone && !hasMilestone(player, milestone)) {
                grantMilestone(level, player, milestone);
            }
        }
    }

    private static void grantMilestone(ServerLevel level, ServerPlayer player, int milestone) {
        setMilestone(player, milestone, true);

        // Advancement correspondant
        grantAdvancementFully(level, player, "milestone_" + milestone);

        // Feedback
        player.displayClientMessage(Component.literal(getMilestoneTitle(milestone)), true);

        // ⚠ SoundSource.PLAYERS (PLAYER n'existe pas)
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

        // Rewards en Deep Lucky Blocks brillants (glow retiré auto après 10 s)
        int rewardCount = getMilestoneRewardCount(level, milestone);
        if (rewardCount > 0) {
            DeepLuckyBlockAdvancementIntegration.dropGlowingLuckyBlocks(level, player, rewardCount);
        }
    }

    private static String getMilestoneTitle(int milestone) {
        return switch (milestone) {
            case 10 -> "§7§l✦ Getting Started ✦ §f- 10 Deep Lucky Blocks brisés!";
            case 50 -> "§a§l✦ Lucky Explorer ✦ §f- 50 Deep Lucky Blocks brisés!";
            case 100 -> "§6§l✦ Lucky Maniac ✦ §f- 100 Deep Lucky Blocks brisés! §b(+0-10 rewards!)";
            case 500 -> "§d§l✦ Lucky Master ✦ §f- 500 Deep Lucky Blocks brisés! §b(+0-10 niveaux d'enchant bonus!)";
            case 1000 -> "§c§l✦ LUCKY LEGEND ✦ §f- 1000 Deep Lucky Blocks brisés! §b(+100 rewards!)";
            default -> "§f✦ Milestone: " + milestone + " ✦";
        };
    }

    private static int getMilestoneRewardCount(ServerLevel level, int milestone) {
        return switch (milestone) {
            case 10 -> 2;                          // 2 blocs fixes
            case 50 -> 5;                          // 5 blocs fixes
            case 100 -> level.random.nextInt(11);  // 0-10 blocs
            case 500 -> level.random.nextInt(21);  // 0-20 blocs
            case 1000 -> 100;                      // 100 blocs fixes
            default -> 0;
        };
    }

    // ==================== BONUS ENCHANTEMENTS (activation) ====================

    private static void activateEnchantBonus(ServerLevel level, ServerPlayer player) {
        setBonusActive(player, true);

        player.displayClientMessage(Component.literal(
                "§b§l✦ Système de Bonus Enchantements Activé! ✦\n" +
                        "§fTes items enchantés peuvent maintenant avoir +0-10 niveaux bonus!"), true);

        // ⚠ SoundEvents.BELL n'existe pas -> BELL_BLOCK
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    // ==================== DÉLÉGUÉS (la logique est dans l'Integration) ====================

    /** Applique le bonus uniquement si le joueur l'a débloqué (500 blocs). */
    public static void applyEnchantBonusIfActive(ServerPlayer player, ItemStack stack) {
        DeepLuckyBlockAdvancementIntegration.applyEnchantBonusIfActive(player, stack);
    }

    /** Version java.util.Random. */
    public static void applyEnchantBonus(ItemStack stack, Random random) {
        DeepLuckyBlockAdvancementIntegration.applyEnchantBonus(stack, random);
    }

    /** Version RandomSource (level.random, player.getRandom()...). */
    public static void applyEnchantBonus(ItemStack stack, RandomSource random) {
        DeepLuckyBlockAdvancementIntegration.applyEnchantBonus(stack, random);
    }

    // ==================== PERSISTENCE (sur le ServerPlayer) ====================

    private static int getDeepLuckyBlockCount(ServerPlayer player) {
        return player.getPersistentData().getInt(TAG_DEEP_LUCKY_BROKEN_COUNT);
    }

    private static void setDeepLuckyBlockCount(ServerPlayer player, int count) {
        player.getPersistentData().putInt(TAG_DEEP_LUCKY_BROKEN_COUNT, count);
    }

    private static boolean hasMilestone(ServerPlayer player, int milestone) {
        return player.getPersistentData().getBoolean(TAG_MILESTONE_ACHIEVED + milestone);
    }

    private static void setMilestone(ServerPlayer player, int milestone, boolean achieved) {
        player.getPersistentData().putBoolean(TAG_MILESTONE_ACHIEVED + milestone, achieved);
    }

    private static boolean hasBonusActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(TAG_BONUS_ACTIVE);
    }

    private static void setBonusActive(ServerPlayer player, boolean active) {
        player.getPersistentData().putBoolean(TAG_BONUS_ACTIVE, active);
    }

    // ==================== API PUBLIQUE ====================

    public static int getPlayerDeepLuckyBlockCount(ServerPlayer player) {
        return getDeepLuckyBlockCount(player);
    }

    public static boolean hasEnchantBonus(ServerPlayer player) {
        return hasBonusActive(player);
    }

    /** Bonus max possible pour les enchantements. */
    public static int getBonusMax() {
        return DeepLuckyBlockAdvancementIntegration.BONUS_MAX;
    }

    // ==================== NOUVEAUX ACHIEVEMENTS (API pour les hooks) ====================

    private static final String TAG_STRUCTURE_BITMASK = "slb_struct_found_bitmask";
    private static final String TAG_LUCKY_CHAIN_COUNT = "slb_lucky_chain_count";
    private static final String TAG_ULT_COLLECT_COUNT = "slb_ultimate_collect_count";
    private static final String TAG_CREATOR_COUNT = "slb_creator_item_count";

    /** Cache simple (flag NBT) pour n'accorder un hub qu'une fois. */
    private static boolean hasFlag(ServerPlayer player, String key) {
        return player.getPersistentData().getBoolean("slb_adv_hub_" + key);
    }
    private static void setFlag(ServerPlayer player, String key) {
        player.getPersistentData().putBoolean("slb_adv_hub_" + key, true);
    }

    /** Verrouille & accorde un hub de catégorie (une seule fois). */
    private static void grantHub(ServerPlayer player, String hub) {
        if (player == null || hasFlag(player, hub)) return;
        setFlag(player, hub);
        grantCustomAchievement(player, hub, 0);
    }

    /**
     * Accord un achievement custom (par chemin relatif au modid) et, en option,
     * drop des Deep Lucky Blocks brillants en récompense.
     * Utilise le trigger vanilla "minecraft:impossible" + award direct (comme lucky_streak).
     */
    public static void grantCustomAchievement(ServerPlayer player, String path, int rewardBlocks) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        // Si déjà obtenu -> on ne ré-accorde PAS la récompense (sinon 2 blocs
        // retomberaient à chaque spawn, même achievement déjà débloqué).
        AdvancementHolder advancement = level.getServer().getAdvancements().get(
                ResourceLocation.tryParse(MODID + ":" + path));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        grantAdvancementFully(level, player, path);
        if (rewardBlocks > 0) {
            DeepLuckyBlockAdvancementIntegration.dropGlowingLuckyBlocks(level, player, rewardBlocks);
        }
    }

    /** Achievement : le Trio Team Claquette (Jerry + Ben + Samouss) a spawné en même temps. */
    public static void grantTrioClaquette(ServerPlayer player) {
        grantHub(player, "hub_creators");
        grantCustomAchievement(player, "trio_claquette", 0);
    }

    /**
     * Achievement : une structure a spawné. Chaque structure a SON achievement
     * ("structure_<cle>"), et donne 2 Deep Lucky Blocks à l'obtention.
     * Clefs attendues : citadel, observatory, dragon, circus, ship, everest.
     * Débloque aussi le hub "Explorateur" et l'achievement "Maître des Structures"
     * une fois les 6 structures différentes trouvées.
     */
    public static void grantStructureSpawned(ServerPlayer player, String structureKey) {
        if (structureKey == null || structureKey.isBlank()) return;
        grantHub(player, "hub_exploration");
        grantCustomAchievement(player, "structure_" + structureKey, 2);

        // Sous-branche de zone (aère l'arbre : 3 zones au lieu de 6 feuilles plates).
        String zoneHub = switch (structureKey) {
            case "citadel", "observatory" -> "exp_forest";
            case "dragon", "circus"       -> "exp_ruins";
            case "ship", "everest"        -> "exp_extremes";
            default -> null;
        };
        if (zoneHub != null) grantHub(player, zoneHub);

        // Track les 6 structures différentes (bitmask).
        int bit = switch (structureKey) {
            case "citadel" -> 1;      case "observatory" -> 2;
            case "dragon" -> 4;       case "circus" -> 8;
            case "ship" -> 16;        case "everest" -> 32;
            default -> 0;
        };
        if (bit == 0) return;
        int mask = player.getPersistentData().getInt(TAG_STRUCTURE_BITMASK) | bit;
        player.getPersistentData().putInt(TAG_STRUCTURE_BITMASK, mask);
        if ((mask & 63) == 63) { // toutes les 6 bitmask set
            grantCustomAchievement(player, "all_structures", 10);
        }
    }

    /** Achievement : le tout premier ULTIMATE est apparu au joueur. */
    public static void grantFirstUltimate(ServerPlayer player) {
        grantHub(player, "hub_grade");
        // Débloquer la chaîne de parents pour que first_ultimate -> grade_eternal
        // -> grade_tier_max -> hub_grade s'affiche toujours correctement.
        grantHub(player, "grade_tier_max");
        grantHub(player, "grade_eternal");
        grantCustomAchievement(player, "first_ultimate", 0);

        // Le Connaisseur : 3 objets ULTIMATE (ETERNAL) au total.
        int count = player.getPersistentData().getInt(TAG_ULT_COLLECT_COUNT) + 1;
        player.getPersistentData().putInt(TAG_ULT_COLLECT_COUNT, count);
        if (count >= 3) {
            grantCustomAchievement(player, "ultimate_collector", 0);
        }
    }

    /**
     * Achievement "un grade obtenu" : à appeler à chaque tirage POSITIF donnant un
     * item de grade <gradeKey> (lowercase, ex. "rare", "mythic", "eternal").
     * Accorde grade_<gradeKey> + le hub "L'Apogée".
     */
    public static void grantGrade(ServerPlayer player, String gradeKey) {
        if (player == null || gradeKey == null || gradeKey.isBlank()) return;
        grantHub(player, "hub_grade");

        // Sous-branche de palier (aère l'arbre : 4 paliers au lieu de 15 feuilles plates).
        String tierHub = switch (gradeKey.toLowerCase(java.util.Locale.ROOT)) {
            case "used", "worn", "common", "uncommon"       -> "grade_tier_low";
            case "unusual", "remarkable", "rare", "outstanding" -> "grade_tier_mid";
            case "epic", "exceptional", "legendary"         -> "grade_tier_high";
            case "mythic", "relic", "masterwork", "eternal" -> "grade_tier_max";
            default -> null;
        };
        if (tierHub != null) grantHub(player, tierHub);

        grantCustomAchievement(player, "grade_" + gradeKey.toLowerCase(java.util.Locale.ROOT), 0);
    }

    /**
     * Achievement "item de Créateur" : à appeler à chaque Creator Event.
     * Compte 1 / 5 / 10 et débloque le hub "Le Syndicat des Créateurs".
     */
    public static void grantCreatorItem(ServerPlayer player) {
        if (player == null) return;
        grantHub(player, "hub_creators");
        int count = player.getPersistentData().getInt(TAG_CREATOR_COUNT) + 1;
        player.getPersistentData().putInt(TAG_CREATOR_COUNT, count);
        grantCustomAchievement(player, "creator_1", 0);
        if (count >= 5) grantCustomAchievement(player, "creator_5", 0);
        if (count >= 10) grantCustomAchievement(player, "creator_10", 0);
    }

    /** Achievement : l'événement ultra rare Crimson Lake a été trouvé. */
    public static void grantCrimsonLake(ServerPlayer player) {
        grantHub(player, "hub_events");
        grantCustomAchievement(player, "crimson_lake", 0);
    }

    /** Chaîne de Chance : à appeler à chaque déclenchement d'une série de chance. */
    public static void onLuckyChain(ServerPlayer player) {
        if (player == null) return;
        grantHub(player, "hub_luck");
        int count = player.getPersistentData().getInt(TAG_LUCKY_CHAIN_COUNT) + 1;
        player.getPersistentData().putInt(TAG_LUCKY_CHAIN_COUNT, count);
        if (count >= 3) {
            grantCustomAchievement(player, "lucky_chain", 0);
        }
    }
}
