package deepluckyblock.procedures;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class LuckyChestAutofillProcedure {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = false;

    private static List<Item> ALL_ITEMS_CACHE = null;
    private static List<Item> LUCKY_BLOCK_CACHE = null;
    private static List<Item> APPLE_ITEM_CACHE = null;
    private static List<Enchantment> ENCHANTMENT_CACHE = null;
    private static Item deep_lucky_block = null;
    private static boolean deep_lucky_block_SEARCHED = false;

    // --- Verrouillage des coffres ---
    // Rayon de detection des mobs hostiles autour d'un coffre verrouille.
    private static final double LOCK_MOB_RADIUS = 15.0;
    // 70% des coffres sont verrouilles. Le coffre Ultimate est toujours verrouille.
    private static final double LOCK_CHANCE = 0.70;
    // Duree du glow au clic sur un coffre verrouille : 10 secondes.
    private static final int GLOW_DURATION_TICKS = 200;

    private enum ChestRank {
        CHEST,
        GOOD_CHEST,
        RARE_CHEST,
        EPIC_CHEST,
        ULTIMATE_CHEST
    }

    // ==================== INTERACTION COFFRE ====================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        if (player == null) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest)) return;

        CompoundTag data = chest.getPersistentData();

        if (!data.getBoolean("slb_auto_chest")) return;

        int rankId = data.getInt("slb_chest_rank");

        // Verrouillage : decide une fois pour toutes. 70% des coffres, et
        // toujours verrouille pour le coffre Ultimate.
        if (!data.getBoolean("slb_filled")) {
            if (!data.contains("slb_chest_locked")) {
                ChestBlockEntity lockPartner = findPartnerChest(level, pos);
                boolean locked;
                if (lockPartner != null && lockPartner.getPersistentData().contains("slb_chest_locked")) {
                    // Coffre double : les deux moities partagent le meme etat.
                    locked = lockPartner.getPersistentData().getBoolean("slb_chest_locked");
                } else {
                    locked = level.getRandom().nextDouble() < LOCK_CHANCE;
                }
                // Le coffre Ultimate est FORCEMENT verrouille.
                if (rankId == 5) locked = true;
                data.putBoolean("slb_chest_locked", locked);
                chest.setChanged();
                if (lockPartner != null && !lockPartner.getPersistentData().contains("slb_chest_locked")) {
                    lockPartner.getPersistentData().putBoolean("slb_chest_locked", locked);
                    lockPartner.setChanged();
                }
            }

            // Verrouille meme en creatif. Se deverouille quand le detecteur de
            // mobs hostiles dans le rayon retourne 0.
            if (data.getBoolean("slb_chest_locked")) {
                List<Mob> enemies = level.getEntitiesOfClass(
                        Mob.class,
                        new AABB(pos).inflate(LOCK_MOB_RADIUS),
                        m -> m.isAlive() && (m instanceof Monster
                                || m.getPersistentData().getBoolean("slb_sanctuary_equipped"))
                );

                if (!enemies.isEmpty()) {
                    event.setCanceled(true);

                    // Petit texte rouge au-dessus de la barre d'action.
                    if (player instanceof ServerPlayer sp) {
                        sp.connection.send(new ClientboundSetActionBarTextPacket(
                                Component.literal("Locked, there are still hostile mobs nearby")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                    }

                    level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.8f);

                    // Glow des mobs (effet spectral arrow) pendant 10s, uniquement au clic.
                    glowHostiles(enemies);
                    return;
                }
            }
        }

        if (data.getBoolean("slb_filled")) return;

        ChestRank rank = switch (rankId) {
            case 1 -> ChestRank.CHEST;
            case 2 -> ChestRank.GOOD_CHEST;
            case 3 -> ChestRank.RARE_CHEST;
            case 4 -> ChestRank.EPIC_CHEST;
            case 5 -> ChestRank.ULTIMATE_CHEST;
            default -> ChestRank.CHEST;
        };

        try {
            fillChest(chest, rank, level, pos);
            data.putBoolean("slb_filled", true);
            chest.setChanged();
            // Marquer aussi l'autre moitie d'un coffre double comme remplie,
            // sinon elle se re-remplirait au prochain clic.
            ChestBlockEntity fillPartner = findPartnerChest(level, pos);
            if (fillPartner != null && !fillPartner.getPersistentData().getBoolean("slb_filled")) {
                fillPartner.getPersistentData().putBoolean("slb_filled", true);
                fillPartner.setChanged();
            }
            debug("Coffre rempli | rang=" + rank.name() + " | pos=" + pos.toShortString());
        } catch (Exception e) {
            LOGGER.error("[SLB-CHEST] Erreur auto-fill coffre", e);
        }
    }

    private static void glowHostiles(List<Mob> enemies) {
        for (Mob m : enemies) {
            try {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0, false, false, false));
            } catch (Exception ignored) {
                // un mob peut etre en cours de suppression, on ignore
            }
        }
    }

    // ==================== REMPLISSAGE ====================

    private static void fillChest(ChestBlockEntity chest, ChestRank rank, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();

        // Un coffre double = deux moities : on remplit les deux d'un seul coup.
        List<ChestBlockEntity> halves = new ArrayList<>();
        halves.add(chest);
        ChestBlockEntity partner = findPartnerChest(level, pos);
        if (partner != null) halves.add(partner);

        for (ChestBlockEntity h : halves) {
            for (int i = 0; i < h.getContainerSize(); i++) h.setItem(i, ItemStack.EMPTY);
        }

        List<ItemStack> items = generateContents(rank, random, level);

        // Tous les slots de toutes les moities, melanges.
        List<int[]> slots = new ArrayList<>();
        for (int h = 0; h < halves.size(); h++) {
            for (int i = 0; i < halves.get(h).getContainerSize(); i++) {
                slots.add(new int[]{h, i});
            }
        }
        Collections.shuffle(slots, new java.util.Random(random.nextLong()));

        int limit = Math.min(items.size(), slots.size());
        for (int i = 0; i < limit; i++) {
            int[] slot = slots.get(i);
            halves.get(slot[0]).setItem(slot[1], items.get(i));
        }

        for (ChestBlockEntity h : halves) h.setChanged();

        // Garde-fou : un seul oeuf du Shadow Mirror maximum dans tout le coffre.
        ensureSingleEgg(halves);
    }

    /** Trouve l'autre moitie d'un coffre double, ou null si c'est un coffre simple. */
    private static ChestBlockEntity findPartnerChest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        if (!state.hasProperty(ChestBlock.TYPE)) return null;
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;

        Direction dir = ChestBlock.getConnectedDirection(state);
        BlockEntity be = level.getBlockEntity(pos.relative(dir));
        return (be instanceof ChestBlockEntity c) ? c : null;
    }

    /** Garde au plus un oeuf du Shadow Mirror dans l'ensemble des moities du coffre. */
    private static void ensureSingleEgg(List<ChestBlockEntity> halves) {
        Item egg = getShadowMirrorEgg();
        if (egg == null) return;

        boolean found = false;
        for (ChestBlockEntity h : halves) {
            for (int i = 0; i < h.getContainerSize(); i++) {
                ItemStack s = h.getItem(i);
                if (!s.isEmpty() && s.getItem() == egg) {
                    if (found) {
                        h.setItem(i, ItemStack.EMPTY);
                    } else {
                        found = true;
                    }
                }
            }
            h.setChanged();
        }
    }

    private static List<ItemStack> generateContents(ChestRank rank, RandomSource random, ServerLevel level) {
        List<ItemStack> items = new ArrayList<>();

        items.addAll(generateBaseLoot(rank, random));
        items.addAll(generateFoodRewards(rank, random));

        ItemStack r1 = getRandomRegisteredItem(random);
        if (!r1.isEmpty()) items.add(r1);

        if (rank.ordinal() >= ChestRank.RARE_CHEST.ordinal()) {
            ItemStack r2 = getRandomRegisteredItem(random);
            if (!r2.isEmpty()) items.add(r2);
        }

        items.addAll(generateLuckyItemRewards(rank, random, level));
        items.addAll(generateLuckyBlockRewards(rank, random));
        items.addAll(generatedeepluckyblockRewards(rank, random));
        items.addAll(generateAppleRewards(rank, random));

        if (shouldGiveNetherStar(rank, random)) {
            items.add(new ItemStack(Items.NETHER_STAR, 1));
        }

        items.addAll(generateShadowMirrorEggRewards(rank, random));

        if (rank == ChestRank.EPIC_CHEST) {
            items.addAll(generateEpicExtras(random));
        }
        if (rank == ChestRank.ULTIMATE_CHEST) {
            items.addAll(generateUltimateExtras(random));
        }

        // Filtre dur : aucun item du mod ne finit dans nos coffres, SAUF notre
        // bloc et l'oeuf du Shadow Mirror. Le Shadow Essence, lui, est remplace
        // par une pomme random (vanilla + autres mods).
        items = filterModItems(items, random);

        Collections.shuffle(items, new java.util.Random(random.nextLong()));
        return items;
    }

    private static List<ItemStack> generateLuckyItemRewards(ChestRank rank, RandomSource random, ServerLevel level) {
        List<ItemStack> out = new ArrayList<>();

        int luckMin;
        int luckMax;
        int chance;

        switch (rank) {
            case CHEST -> {
                luckMin = 20;
                luckMax = 30;
                chance = 8;
            }
            case GOOD_CHEST -> {
                luckMin = 30;
                luckMax = 45;
                chance = 13;
            }
            case RARE_CHEST -> {
                luckMin = 45;
                luckMax = 60;
                chance = 20;
            }
            case EPIC_CHEST -> {
                luckMin = 60;
                luckMax = 80;
                chance = 27;
            }
            case ULTIMATE_CHEST -> {
                luckMin = 80;
                luckMax = 100;
                chance = 33;
            }
            default -> {
                return out;
            }
        }

        if (random.nextInt(100) >= chance) return out;

        int luck = luckMin + random.nextInt(luckMax - luckMin + 1);

        try {
            TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(luck);
            ItemStack luckyItem = GenerateLuckyItemProcedure.generate(level, luck, tier, random);
            if (luckyItem != null && !luckyItem.isEmpty()) {
                out.add(luckyItem);
            }
        } catch (Exception e) {
            debug("Erreur GenerateLuckyItemProcedure: " + e.getMessage());
        }

        if (rank == ChestRank.ULTIMATE_CHEST && random.nextInt(100) < 17) {
            int luck2 = luckMin + random.nextInt(luckMax - luckMin + 1);
            try {
                TestProcedure.QualityTier tier2 = TestProcedure.QualityTier.getQualityTier(luck2);
                ItemStack luckyItem2 = GenerateLuckyItemProcedure.generate(level, luck2, tier2, random);
                if (luckyItem2 != null && !luckyItem2.isEmpty()) {
                    out.add(luckyItem2);
                }
            } catch (Exception ignored) {
            }
        }

        return out;
    }

    /**
     * Oeuf du Shadow Mirror : 60% de chance, UNIQUEMENT dans les coffres Ultimate.
     */
    private static List<ItemStack> generateShadowMirrorEggRewards(ChestRank rank, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        if (rank != ChestRank.ULTIMATE_CHEST) return out;

        Item egg = getShadowMirrorEgg();
        if (egg == null) return out;

        if (random.nextInt(100) < 60) {
            out.add(new ItemStack(egg, 1));
        }
        return out;
    }

    private static List<ItemStack> generateFoodRewards(ChestRank rank, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();

        switch (rank) {
            case CHEST -> {
                maybeAdd(items, Items.BREAD, 4 + random.nextInt(5), 85, random);
                maybeAdd(items, Items.APPLE, 3 + random.nextInt(5), 70, random);
                maybeAdd(items, Items.COOKED_BEEF, 3 + random.nextInt(4), 60, random);
                maybeAdd(items, Items.CARROT, 4 + random.nextInt(5), 50, random);
                maybeAdd(items, Items.BAKED_POTATO, 3 + random.nextInt(4), 50, random);
            }
            case GOOD_CHEST -> {
                maybeAdd(items, Items.BREAD, 6 + random.nextInt(7), 85, random);
                maybeAdd(items, Items.COOKED_BEEF, 4 + random.nextInt(5), 75, random);
                maybeAdd(items, Items.COOKED_PORKCHOP, 4 + random.nextInt(5), 65, random);
                maybeAdd(items, Items.COOKED_CHICKEN, 3 + random.nextInt(5), 60, random);
                maybeAdd(items, Items.PUMPKIN_PIE, 2 + random.nextInt(4), 45, random);
                maybeAdd(items, Items.MELON_SLICE, 5 + random.nextInt(6), 40, random);
            }
            case RARE_CHEST -> {
                maybeAdd(items, Items.COOKED_BEEF, 6 + random.nextInt(7), 85, random);
                maybeAdd(items, Items.COOKED_PORKCHOP, 5 + random.nextInt(6), 80, random);
                maybeAdd(items, Items.COOKED_CHICKEN, 5 + random.nextInt(6), 70, random);
                maybeAdd(items, Items.COOKED_SALMON, 3 + random.nextInt(5), 55, random);
                maybeAdd(items, Items.PUMPKIN_PIE, 4 + random.nextInt(5), 60, random);
                maybeAdd(items, Items.CAKE, 1, 30, random);
                maybeAdd(items, Items.HONEY_BOTTLE, 2 + random.nextInt(3), 40, random);
            }
            case EPIC_CHEST -> {
                maybeAdd(items, Items.COOKED_BEEF, 10 + random.nextInt(11), 85, random);
                maybeAdd(items, Items.COOKED_PORKCHOP, 8 + random.nextInt(9), 80, random);
                maybeAdd(items, Items.COOKED_SALMON, 6 + random.nextInt(7), 70, random);
                maybeAdd(items, Items.CAKE, 1 + random.nextInt(2), 45, random);
                maybeAdd(items, Items.PUMPKIN_PIE, 6 + random.nextInt(7), 65, random);
                maybeAdd(items, Items.HONEY_BOTTLE, 3 + random.nextInt(4), 55, random);
                maybeAdd(items, Items.MUSHROOM_STEW, 3 + random.nextInt(4), 45, random);
                maybeAdd(items, Items.RABBIT_STEW, 2 + random.nextInt(3), 40, random);
            }
            case ULTIMATE_CHEST -> {
                maybeAdd(items, Items.COOKED_BEEF, 16 + random.nextInt(17), 90, random);
                maybeAdd(items, Items.COOKED_PORKCHOP, 12 + random.nextInt(13), 85, random);
                maybeAdd(items, Items.COOKED_SALMON, 10 + random.nextInt(11), 80, random);
                maybeAdd(items, Items.CAKE, 2 + random.nextInt(3), 60, random);
                maybeAdd(items, Items.PUMPKIN_PIE, 10 + random.nextInt(11), 75, random);
                maybeAdd(items, Items.HONEY_BOTTLE, 5 + random.nextInt(6), 70, random);
                maybeAdd(items, Items.RABBIT_STEW, 4 + random.nextInt(5), 60, random);
                maybeAdd(items, Items.BEETROOT_SOUP, 4 + random.nextInt(5), 55, random);
                maybeAdd(items, Items.GLISTERING_MELON_SLICE, 4 + random.nextInt(5), 50, random);
            }
        }

        return items;
    }

    private static List<ItemStack> generateBaseLoot(ChestRank rank, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();

        switch (rank) {
            case CHEST -> {
                maybeAdd(items, Items.IRON_INGOT, 2 + random.nextInt(4), 85, random);
                maybeAdd(items, Items.COAL, 3 + random.nextInt(5), 60, random);
                maybeAdd(items, Items.ARROW, 4 + random.nextInt(9), 50, random);
                maybeAdd(items, Items.STRING, 2 + random.nextInt(3), 35, random);
                maybeAdd(items, Items.TORCH, 4 + random.nextInt(5), 50, random);
            }
            case GOOD_CHEST -> {
                maybeAdd(items, Items.IRON_INGOT, 4 + random.nextInt(5), 85, random);
                maybeAdd(items, Items.GOLD_INGOT, 2 + random.nextInt(4), 70, random);
                maybeAdd(items, Items.REDSTONE, 4 + random.nextInt(7), 50, random);
                maybeAdd(items, Items.LAPIS_LAZULI, 3 + random.nextInt(5), 50, random);
                maybeAdd(items, Items.EXPERIENCE_BOTTLE, 4 + random.nextInt(5), 35, random);
                maybeAdd(items, Items.ENDER_PEARL, 1 + random.nextInt(2), 25, random);
                maybeAdd(items, Items.SADDLE, 1, 15, random);
                maybeAdd(items, Items.NAME_TAG, 1, 15, random);
            }
            case RARE_CHEST -> {
                maybeAdd(items, Items.DIAMOND, 2 + random.nextInt(3), 75, random);
                maybeAdd(items, Items.EMERALD, 4 + random.nextInt(5), 70, random);
                maybeAdd(items, Items.GOLD_INGOT, 5 + random.nextInt(5), 80, random);
                maybeAdd(items, Items.EXPERIENCE_BOTTLE, 8 + random.nextInt(9), 60, random);
                maybeAdd(items, Items.ENDER_PEARL, 1 + random.nextInt(2), 25, random);
                maybeAdd(items, Items.BLAZE_ROD, 1 + random.nextInt(2), 25, random);
                maybeAdd(items, Items.IRON_HORSE_ARMOR, 1, 20, random);
                maybeAdd(items, Items.BOOK, 1 + random.nextInt(2), 30, random);
                maybeAddEnchantedBook(items, 15, random);
            }
            case EPIC_CHEST -> {
                maybeAdd(items, Items.DIAMOND, 4 + random.nextInt(5), 80, random);
                maybeAdd(items, Items.EMERALD, 6 + random.nextInt(7), 80, random);
                maybeAdd(items, Items.EXPERIENCE_BOTTLE, 12 + random.nextInt(13), 75, random);
                maybeAdd(items, Items.ENDER_PEARL, 1 + random.nextInt(2), 35, random);
                maybeAdd(items, Items.NETHERITE_SCRAP, 1 + random.nextInt(2), 25, random);
                maybeAdd(items, Items.BLAZE_ROD, 2 + random.nextInt(3), 45, random);
                maybeAdd(items, Items.DIAMOND_HORSE_ARMOR, 1, 15, random);
                maybeAdd(items, Items.GHAST_TEAR, 1 + random.nextInt(2), 20, random);
                maybeAddEnchantedBook(items, 35, random);
                maybeAddEnchantedBook(items, 15, random);
            }
            case ULTIMATE_CHEST -> {
                maybeAdd(items, Items.DIAMOND, 8 + random.nextInt(9), 95, random);
                maybeAdd(items, Items.EMERALD, 12 + random.nextInt(13), 90, random);
                maybeAdd(items, Items.EXPERIENCE_BOTTLE, 24 + random.nextInt(25), 90, random);
                maybeAdd(items, Items.ENDER_PEARL, 2 + random.nextInt(2), 40, random);
                maybeAdd(items, Items.NETHERITE_SCRAP, 3 + random.nextInt(4), 55, random);
                maybeAdd(items, Items.BLAZE_ROD, 4 + random.nextInt(5), 65, random);
                maybeAdd(items, Items.CHORUS_FRUIT, 4 + random.nextInt(5), 50, random);
                maybeAdd(items, Items.END_CRYSTAL, 1, 20, random);
                maybeAdd(items, Items.DRAGON_BREATH, 1 + random.nextInt(3), 35, random);
                maybeAdd(items, Items.GHAST_TEAR, 2 + random.nextInt(3), 40, random);
                maybeAdd(items, Items.NETHERITE_INGOT, 1, 30, random);
                maybeAdd(items, Items.DIAMOND_BLOCK, 1, 15, random);
                maybeAdd(items, Items.IRON_BLOCK, 2 + random.nextInt(3), 40, random);
                maybeAdd(items, Items.GOLD_BLOCK, 1 + random.nextInt(2), 35, random);
                maybeAddEnchantedBook(items, 55, random);
                maybeAddEnchantedBook(items, 30, random);
            }
        }

        return items;
    }

    private static List<ItemStack> generateEpicExtras(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        maybeAdd(items, Items.SHULKER_SHELL, 1, 15, random);
        maybeAdd(items, Items.ENDER_EYE, 1 + random.nextInt(2), 20, random);
        maybeAdd(items, Items.NETHERITE_INGOT, 1, 8, random);
        return items;
    }

    private static List<ItemStack> generateUltimateExtras(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        maybeAdd(items, Items.ELYTRA, 1, 1, random); // 1x sur 100 comme demande
        maybeAdd(items, Items.TOTEM_OF_UNDYING, 1, 5, random); // Tres rare et seulement dispo en Ultimate
        maybeAdd(items, Items.NETHERITE_INGOT, 1 + random.nextInt(2), 20, random);
        maybeAdd(items, Items.SHULKER_SHELL, 1 + random.nextInt(2), 30, random);
        maybeAdd(items, Items.ENDER_EYE, 2 + random.nextInt(3), 35, random);
        maybeAdd(items, Items.BEACON, 1, 3, random);
        maybeAdd(items, Items.CONDUIT, 1, 5, random);
        maybeAdd(items, Items.HEART_OF_THE_SEA, 1, 8, random);
        maybeAdd(items, Items.TRIDENT, 1, 4, random);

        return items;
    }

    private static void maybeAddEnchantedBook(List<ItemStack> items, int chancePercent, RandomSource random) {
        if (random.nextInt(100) >= chancePercent) return;

        if (ENCHANTMENT_CACHE == null) buildEnchantmentCache();
        if (ENCHANTMENT_CACHE.isEmpty()) return;

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        int enchantsCount = 1 + random.nextInt(3);

        Collections.shuffle(ENCHANTMENT_CACHE, new java.util.Random(random.nextLong()));

        int added = 0;
        for (Enchantment ench : ENCHANTMENT_CACHE) {
            if (added >= enchantsCount) break;
            if (ench == null) continue;

            int maxLvl = ench.getMaxLevel();
            int lvl = 1 + random.nextInt(Math.max(1, maxLvl));

            try {
                // PORT 1.21.1 : plus d'EnchantedBookItem.addEnchantment -> composant ItemEnchantments.
                net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(book,
                        m -> m.set(deepluckyblock.util.DeepEnchant.holder(ench), lvl));
                added++;
            } catch (Exception ignored) {
            }
        }

        if (added > 0) {
            items.add(book);
        }
    }

    private static void buildEnchantmentCache() {
        ENCHANTMENT_CACHE = new ArrayList<>();
        for (Enchantment ench : deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access())) {
            if (ench == null) continue;
            ENCHANTMENT_CACHE.add(ench);
        }
        debug("Cache enchantements : " + ENCHANTMENT_CACHE.size() + " trouves");
    }

    private static List<ItemStack> generatedeepluckyblockRewards(ChestRank rank, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();

        Item slb = getdeepluckyblock();

        switch (rank) {
            case CHEST -> {
                if (slb != null && random.nextInt(100) < 8) {
                    out.add(new ItemStack(slb, 1));
                }
            }
            case GOOD_CHEST -> {
                if (slb != null && random.nextInt(100) < 15) {
                    out.add(new ItemStack(slb, 1 + random.nextInt(2)));
                }
            }
            case RARE_CHEST -> {
                if (slb != null && random.nextInt(100) < 30) {
                    out.add(new ItemStack(slb, 1 + random.nextInt(3)));
                }
            }
            case EPIC_CHEST -> {
                if (slb != null) {
                    out.add(new ItemStack(slb, 2 + random.nextInt(4)));
                }
            }
            case ULTIMATE_CHEST -> {
                if (slb != null) {
                    out.add(new ItemStack(slb, 4 + random.nextInt(7)));
                }
            }
        }

        return out;
    }

    private static Item getdeepluckyblock() {
        if (!deep_lucky_block_SEARCHED) {
            deep_lucky_block = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("deep_lucky_block:deep_lucky_block"));
            if (deep_lucky_block == null) {
                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    if (key != null && key.toString().equals("deep_lucky_block:deep_lucky_block")) {
                        deep_lucky_block = item;
                        break;
                    }
                }
            }
            deep_lucky_block_SEARCHED = true;
        }
        return deep_lucky_block;
    }

    /**
     * Resolve le spawn egg du Shadow Mirror. Le nom exact en registre n'est pas
     * garanti, donc on teste plusieurs candidats puis une recherche floue.
     * Renvoie null si rien trouve (pas de crash, l'oeuf est juste absent).
     */
    private static Item shadow_mirror_egg = null;
    private static boolean shadow_mirror_egg_SEARCHED = false;

    private static Item getShadowMirrorEgg() {
        if (!shadow_mirror_egg_SEARCHED) {
            shadow_mirror_egg_SEARCHED = true;

            String[] candidates = {
                    "deep_lucky_block:shadow_mirror_spawn_egg",
                    "deep_lucky_block:shadow_mirror_egg",
                    "deep_lucky_block:shadow_egg"
            };
            for (String id : candidates) {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
                if (it != null && it != Items.AIR) {
                    shadow_mirror_egg = it;
                    break;
                }
            }

            if (shadow_mirror_egg == null) {
                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    if (key == null) continue;
                    String s = key.toString().toLowerCase(Locale.ROOT);
                    if (s.startsWith("deep_lucky_block:") && s.contains("shadow") && s.contains("egg")) {
                        shadow_mirror_egg = item;
                        break;
                    }
                }
            }

            debug("Shadow Mirror egg: "
                    + (shadow_mirror_egg != null ? BuiltInRegistries.ITEM.getKey(shadow_mirror_egg) : "NON TROUVE"));
        }
        return shadow_mirror_egg;
    }

    private static List<ItemStack> generateLuckyBlockRewards(ChestRank rank, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();

        if (LUCKY_BLOCK_CACHE == null) buildLuckyBlockCache();
        if (LUCKY_BLOCK_CACHE.isEmpty()) return out;

        int maxRoll;
        int chance;

        switch (rank) {
            case CHEST -> {
                maxRoll = 1;
                chance = 10;
            }
            case GOOD_CHEST -> {
                maxRoll = 2;
                chance = 18;
            }
            case RARE_CHEST -> {
                maxRoll = 3;
                chance = 28;
            }
            case EPIC_CHEST -> {
                maxRoll = 3;
                chance = 40;
            }
            case ULTIMATE_CHEST -> {
                maxRoll = 3;
                chance = 65;
            }
            default -> {
                maxRoll = 0;
                chance = 0;
            }
        }

        if (random.nextInt(100) >= chance) return out;

        int amount = 1 + random.nextInt(maxRoll);
        Collections.shuffle(LUCKY_BLOCK_CACHE, new java.util.Random(random.nextLong()));

        for (int i = 0; i < amount && i < LUCKY_BLOCK_CACHE.size(); i++) {
            Item block = LUCKY_BLOCK_CACHE.get(i);
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(block);
            if (key != null && key.toString().equals("deep_lucky_block:deep_lucky_block")) continue;
            out.add(new ItemStack(block, 1));
        }

        return out;
    }

    private static List<ItemStack> generateAppleRewards(ChestRank rank, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();

        int goldenChance;
        int notchChance;
        int goldenMin;
        int goldenMax;

        switch (rank) {
            case CHEST -> {
                goldenChance = 10;
                notchChance = 0;
                goldenMin = 1;
                goldenMax = 1;
            }
            case GOOD_CHEST -> {
                goldenChance = 15;
                notchChance = 0;
                goldenMin = 1;
                goldenMax = 1;
            }
            case RARE_CHEST -> {
                goldenChance = 20;
                notchChance = 0;
                goldenMin = 1;
                goldenMax = 2;
            }
            case EPIC_CHEST -> {
                goldenChance = 25;
                notchChance = 0;
                goldenMin = 1;
                goldenMax = 3;
            }
            case ULTIMATE_CHEST -> {
                goldenChance = 35;
                notchChance = 2;
                goldenMin = 2;
                goldenMax = 4;
            }
            default -> {
                goldenChance = 0;
                notchChance = 0;
                goldenMin = 0;
                goldenMax = 0;
            }
        }

        boolean gotNotch = random.nextInt(100) < notchChance;

        if (gotNotch) {
            out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
        } else if (random.nextInt(100) < goldenChance) {
            out.add(new ItemStack(Items.GOLDEN_APPLE, goldenMin + random.nextInt(goldenMax - goldenMin + 1)));
        }

        return out;
    }

    private static boolean shouldGiveNetherStar(ChestRank rank, RandomSource random) {
        return switch (rank) {
            case ULTIMATE_CHEST -> random.nextInt(100) < 5; // 5% de chance et uniquement dans un coffre Ultimate
            default -> false;
        };
    }

    private static ItemStack getRandomRegisteredItem(RandomSource random) {
        try {
            if (ALL_ITEMS_CACHE == null) buildAllItemsCache();
            if (ALL_ITEMS_CACHE.isEmpty()) return ItemStack.EMPTY;

            Item item = ALL_ITEMS_CACHE.get(random.nextInt(ALL_ITEMS_CACHE.size()));
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;

            int count = getRandomCountForItem(item, random);
            return new ItemStack(item, Math.max(1, count));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void buildAllItemsCache() {
        ALL_ITEMS_CACHE = new ArrayList<>();

        java.util.Set<String> EXCLUDED = java.util.Set.of(
                "debug", "command_block", "structure_void", "structure_block",
                "jigsaw", "barrier", "light", "petrified", "shadow_essence",
                "elytra", "totem", "nether_star", "enchanted_golden_apple",
                "netherite", "dragon_egg", "beacon", "conduit", "spawner", "deep_lucky_block:shadow_essence"
        );

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null || !"minecraft".equals(key.getNamespace())) continue;

            String id = key.toString().toLowerCase(Locale.ROOT);

            boolean excluded = false;
            for (String ex : EXCLUDED) {
                if (id.contains(ex)) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) continue;

            ALL_ITEMS_CACHE.add(item);
        }

        debug("Cache items construit : " + ALL_ITEMS_CACHE.size() + " items disponibles");
    }

    private static void buildLuckyBlockCache() {
        LUCKY_BLOCK_CACHE = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) continue;

            String id = key.toString().toLowerCase(Locale.ROOT);

            boolean hasLucky = id.contains("lucky");
            boolean hasBlock = id.contains("block") || id.contains("blocks");

            if (hasLucky && hasBlock) {
                LUCKY_BLOCK_CACHE.add(item);
            }
        }

        debug("Cache lucky blocks : " + LUCKY_BLOCK_CACHE.size() + " trouves");
    }

    private static int getRandomCountForItem(Item item, RandomSource random) {
        // PORT 1.21.1 : getMaxStackSize(ItemStack).
        int maxStack = item.getMaxStackSize(new net.minecraft.world.item.ItemStack(item));
        if (maxStack <= 1) return 1;
        if (maxStack <= 16) return 1 + random.nextInt(Math.min(4, maxStack));
        return 1 + random.nextInt(Math.min(16, maxStack));
    }

    private static void maybeAdd(List<ItemStack> items, Item item, int count, int chancePercent, RandomSource random) {
        if (random.nextInt(100) < chancePercent) {
            items.add(new ItemStack(item, Math.max(1, count)));
        }
    }

    /**
     * Filtre dur anti-fuite : aucun item du namespace deep_lucky_block ne finit
     * dans nos coffres, SAUF notre bloc et l'oeuf du Shadow Mirror.
     *
     * Cas special Shadow Essence : on ne le laisse jamais passer. A la place on
     * met une pomme random (vanilla + autres mods, comestible, id contient
     * "apple"). Ca rend la fuite inoffensive et ca file un consommable au joueur.
     */
    private static List<ItemStack> filterModItems(List<ItemStack> items, RandomSource random) {
        Item ourBlock = getdeepluckyblock();
        Item egg = getShadowMirrorEgg();
        List<ItemStack> out = new ArrayList<>(items.size());

        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null && "deep_lucky_block".equals(key.getNamespace())) {
                boolean allowed = (ourBlock != null && stack.getItem() == ourBlock)
                        || (egg != null && stack.getItem() == egg);
                if (!allowed) {
                    String id = key.toString().toLowerCase(Locale.ROOT);
                    if (id.contains("shadow_essence")) {
                        ItemStack apple = getRandomAppleItem(random);
                        debug("Shadow Essence remplace par pomme : "
                                + BuiltInRegistries.ITEM.getKey(apple.getItem()));
                        out.add(apple);
                    } else {
                        debug("Item mod bloque dans coffre : " + key);
                    }
                    continue;
                }
            }
            out.add(stack);
        }
        return out;
    }

    /**
     * Pomme random : tout item consommable dont l'id contient "apple", peu
     * importe le mod, sauf notre propre mod (pour ne pas reintroduire un item
     * deep_lucky_block). Fallback enchanted golden apple si rien trouve.
     */
    private static ItemStack getRandomAppleItem(RandomSource random) {
        if (APPLE_ITEM_CACHE == null) buildAppleItemCache();
        if (APPLE_ITEM_CACHE.isEmpty()) return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);

        Item apple = APPLE_ITEM_CACHE.get(random.nextInt(APPLE_ITEM_CACHE.size()));
        // Une seule pomme, deux maximum.
        int count = 1 + random.nextInt(2);
        return new ItemStack(apple, count);
    }

    private static void buildAppleItemCache() {
        APPLE_ITEM_CACHE = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) continue;

            // On exclut notre mod pour eviter de remettre un item deep_lucky_block.
            if ("deep_lucky_block".equals(key.getNamespace())) continue;

            String id = key.toString().toLowerCase(Locale.ROOT);
            if (!id.contains("apple")) continue;

            // Uniquement les consommables (nourriture).
            if (!(item.components().get(net.minecraft.core.component.DataComponents.FOOD) != null)) continue;

            // Vanilla : on garde UNIQUEMENT l'enchanted golden apple.
            // La pomme regular et la golden apple vanilla sont exclues.
            // Les pommes des autres mods sont toutes acceptees.
            if ("minecraft".equals(key.getNamespace())) {
                String path = key.getPath();
                if (path.equals("apple") || path.equals("golden_apple")) continue;
            }

            APPLE_ITEM_CACHE.add(item);
        }
        debug("Cache items apple : " + APPLE_ITEM_CACHE.size() + " trouves");
    }

    private static void debug(String msg) {
        if (DEBUG) LOGGER.info("[SLB-CHEST] {}", msg);
    }
}
