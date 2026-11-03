package deepluckyblock.advancements;

import deepluckyblock.enchantments.PixelFx;
import deepluckyblock.DeepLuckyBlockMod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Helpers gameplay de l'intégration des Advancements (Forge 1.20.1) :
 *
 * - Drops de Deep Lucky Blocks brillants (rewards de milestones),
 *   glow retiré automatiquement après 10 s (tick par tick, SANS Thread.sleep)
 * - Bonus +0-10 niveaux d'enchantements (applyEnchantBonus)
 *
 * La logique de comptage / milestones / persistance est dans DeepLuckyBlockAdvancements.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public class DeepLuckyBlockAdvancementIntegration {

    /** Bonus max possible pour les enchantements. */
    public static final int BONUS_MAX = 10;

    private static final int GLOW_DURATION_TICKS = 10 * 20; // 10 secondes

    // ✅ CORRIGÉ : suppression du "new" devant ResourceLocation.tryParse (erreur de compilation)
    private static final ResourceLocation DEEP_LUCKY_BLOCK_ITEM_ID =
            ResourceLocation.tryParse(DeepLuckyBlockMod.MODID + ":deep_lucky_block");

    // Enchantements sans bonus (cappés à 1 ou non-scalables)
    private static final Set<String> NO_BONUS_ENCHANTS = Set.of(
            "minecraft:mending",
            "minecraft:silk_touch",
            "minecraft:infinity",
            "minecraft:loyalty",
            "minecraft:multishot",
            "minecraft:riptide",
            "minecraft:channeling",
            "minecraft:soul_speed",
            "minecraft:swift_sneak"
    );

    /** ItemEntities en attente de retrait du glow (-> ticks restants). Thread serveur uniquement. */
    private static final Map<ItemEntity, Integer> GLOWING_ITEMS = new HashMap<>();

    // ==================== DROP REWARDS (blocs brillants) ====================

    /** Fait tomber des Deep Lucky Blocks brillants ; le glow est retiré automatiquement après 10 s. */
    public static void dropGlowingLuckyBlocks(ServerLevel level, ServerPlayer player, int count) {
        if (count <= 0) return;

        // Item du Deep Lucky Block (fallback diamant si introuvable)
        Item luckyItem = BuiltInRegistries.ITEM.get(DEEP_LUCKY_BLOCK_ITEM_ID);

        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3.0;

            double x = player.getX() + offsetX;
            double y = player.getY() + 1.0;
            double z = player.getZ() + offsetZ;

            ItemStack stack = luckyItem != null ? new ItemStack(luckyItem) : new ItemStack(Items.DIAMOND);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§b§l✦ Deep Lucky Block ✦"));

            ItemEntity itemEntity = new ItemEntity(level, x, y, z, stack);
            itemEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 0.5,
                    0.3 + level.random.nextDouble() * 0.3,
                    (level.random.nextDouble() - 0.5) * 0.5);
            itemEntity.setGlowingTag(true);
            itemEntity.setPickUpDelay(20); // 1 s avant pickup

            level.addFreshEntity(itemEntity);
            GLOWING_ITEMS.put(itemEntity, GLOW_DURATION_TICKS);

            PixelFx.send(level, ParticleTypes.ENCHANT, x, y, z, 10, 0.3, 0.3, 0.3, 0.05);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);

        player.displayClientMessage(Component.literal("§b§l✦ +" + count + " Deep Lucky Blocks! ✦"), true);
    }

    /** Décompte du glow tick par tick (⚠ jamais de Thread.sleep : ça gèlerait le serveur). */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (GLOWING_ITEMS.isEmpty()) return;

        Iterator<Map.Entry<ItemEntity, Integer>> it = GLOWING_ITEMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemEntity, Integer> entry = it.next();
            ItemEntity item = entry.getKey();

            if (!item.isAlive()) { // ramassé ou despawn
                it.remove();
                continue;
            }

            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                item.setGlowingTag(false);
                if (item.level() instanceof ServerLevel serverLevel) {
                    PixelFx.send(serverLevel, ParticleTypes.END_ROD,
                            item.getX(), item.getY(), item.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
                }
                it.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }

    // ==================== BONUS ENCHANTEMENTS ====================

    /**
     * Applique le bonus uniquement si le joueur l'a débloqué (500 blocs).
     * Pratique à appeler depuis GenerateLuckyItemProcedure.
     */
    public static void applyEnchantBonusIfActive(ServerPlayer player, ItemStack stack) {
        if (DeepLuckyBlockAdvancements.hasEnchantBonus(player)) {
            applyEnchantBonus(stack, player.getRandom());
        }
    }

    /** Version java.util.Random (pour les appels existants). */
    public static void applyEnchantBonus(ItemStack stack, Random random) {
        applyEnchantBonusInternal(stack, random::nextInt);
    }

    /** Version RandomSource (API vanilla : level.random, player.getRandom()...). */
    public static void applyEnchantBonus(ItemStack stack, RandomSource random) {
        applyEnchantBonusInternal(stack, random::nextInt);
    }

    /**
     * Applique un bonus de +0-10 niveaux sur les enchantements d'un item.
     * Ignoré pour les enchantements cappés à level 1 (mending, silk touch...) et la blacklist.
     */
    private static void applyEnchantBonusInternal(ItemStack stack, IntUnaryOperator rng) {
        if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return;

        Map<Enchantment, Integer> enchantments = deepluckyblock.util.DeepEnchant.map(stack); // PORT 1.21.1
        Map<Enchantment, Integer> newEnchantments = new HashMap<>(enchantments);
        boolean modified = false;

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchant = entry.getKey();
            if (!canHaveBonus(enchant)) continue;

            int currentLevel = entry.getValue();
            int maxLevel = enchant.getMaxLevel();
            if (currentLevel >= maxLevel) continue;

            int bonus = rng.applyAsInt(BONUS_MAX + 1); // +0 à +10 niveaux
            if (bonus > 0) {
                // NOTE : cappé au niveau max vanilla. Supprime Math.min pour autoriser
                // à DÉPASSER le niveau max (ex. Sharpness VIII).
                int newLevel = Math.min(currentLevel + bonus, maxLevel);
                newEnchantments.put(enchant, newLevel);
                modified = true;
            }
        }

        if (modified) {
            // PORT 1.21.1 : setEnchantments(Map, ItemStack) n'existe plus
            for (Map.Entry<Enchantment, Integer> e : newEnchantments.entrySet()) {
                deepluckyblock.util.DeepEnchant.setLevel(stack, e.getKey(), e.getValue());
            }
        }
    }

    private static boolean canHaveBonus(Enchantment enchant) {
        ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(enchant);
        if (id == null) return false;

        // Pas de bonus si cappé à level 1 (mending, silk touch...) ou dans la blacklist
        return enchant.getMaxLevel() > 1 && !NO_BONUS_ENCHANTS.contains(id.toString());
    }
}
