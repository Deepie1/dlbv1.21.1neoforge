package deepluckyblock.procedures;

import deepluckyblock.enchantments.PixelFx;
import com.mojang.logging.LogUtils;
import deepluckyblock.compat.DeepRadarDropBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Random;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * SupremeLuckyBlockDropProcedure : drop du Deep Lucky Block.
 *
 * Intercepte le cassage des blocs "lucky" tiers (autres mods / pack) et fait
 * tomber un Deep Lucky Block avec un effet visuel/sonore.
 *
 * CONFIG-DRIVEN (defaut TRUE) : les joueurs hors modpack obtiennent leurs blocs.
 * Lucky World Invasion Reloaded peut mettre enableThirdPartyLuckyBlockDrops=false
 * dans deep_lucky_block-lucky_drop.toml pour eviter les drops doubles.
 *
 * Bonus Lucky Radar : si un Radar tenu en main suit exactement
 * deep_lucky_block:deep_lucky_block, la chance passe de 10 % a 20 % (+10 points).
 *
 * C'est le SEUL fichier de drop du mod : ne pas en creer un second
 * (deux abonnes a BlockEvent.BreakEvent = double tirage).
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public class SupremeLuckyBlockDropProcedure {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = false;
    private static final int DROP_CHANCE_PERCENT = 10;
    private static final String DEEP_LUCKY_BLOCK_ID = "deep_lucky_block:deep_lucky_block";
    private static Item DEEP_LUCKY_BLOCK_ITEM = null;
    private static boolean LOOKUP_DONE = false;
    private static final Random RANDOM = new Random();

    // ===================== CONFIG (defaut TRUE) =====================
    public static final ModConfigSpec.BooleanValue ENABLE_THIRD_PARTY_LUCKY_BLOCK_DROPS;
    public static final ModConfigSpec DROP_SPEC;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Drop du Deep's Lucky Block a la casse des blocs 'lucky' d'autres mods.",
                "Defaut true : les joueurs hors modpack obtiennent des blocs.",
                "Mettre a false dans Lucky World Invasion Reloaded pour eviter les drops doubles.");
        ENABLE_THIRD_PARTY_LUCKY_BLOCK_DROPS = b.define("enableThirdPartyLuckyBlockDrops", true);
        DROP_SPEC = b.build();
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Config : desactive si le modpack (LWI Reloaded) l'a mis a false
        try {
            if (!ENABLE_THIRD_PARTY_LUCKY_BLOCK_DROPS.get()) return;
        } catch (Throwable ignored) {
            return;
        }
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.isCanceled()) return;

        Block block = event.getState().getBlock();
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null) return;

        String path = key.getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("lucky")) return;

        // Ne jamais redropper les blocs du mod lui-meme (boucles / drops infinis)
        if (key.getNamespace().equals("deep_lucky_block")) return;
        if (path.equals("deep_lucky_block")) return;

        // PORT 1.21.1 : BreakEvent.getPlayer()
        Player player = event.getPlayer();
        boolean radarBonus = DeepRadarDropBonus.isActive(player);
        int effectiveChance = DROP_CHANCE_PERCENT
                + (radarBonus ? DeepRadarDropBonus.BONUS_PERCENT : 0);

        if (DEBUG && radarBonus) {
            deepluckyblock.util.DebugLog.info(LOGGER, "[DeepRadar] +{}% active; total Deep block drop chance={} %",
                    DeepRadarDropBonus.BONUS_PERCENT, effectiveChance);
        }

        if (RANDOM.nextInt(100) >= effectiveChance) return;

        Item deepBlock = getDeepLuckyBlock();
        if (deepBlock == null) return;

        BlockPos pos = event.getPos();
        spawnGlowingDeepLuckyBlock(level, pos, deepBlock);
    }

    private static void spawnGlowingDeepLuckyBlock(ServerLevel level, BlockPos pos, Item deepBlock) {
        ItemStack stack = new ItemStack(deepBlock, 1);
        ItemEntity entity = new ItemEntity(level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                stack);

        entity.setGlowingTag(true);
        entity.setNoGravity(false);
        entity.setInvulnerable(true);
        entity.setDeltaMovement(
                (RANDOM.nextDouble() - 0.5) * 0.15,
                0.25,
                (RANDOM.nextDouble() - 0.5) * 0.15);
        entity.setPickUpDelay(10);

        CompoundTag nbt = new CompoundTag();
        entity.save(nbt);
        nbt.putShort("Age", (short) -32768);
        nbt.putShort("Health", (short) 32767);
        nbt.putBoolean("Invulnerable", true);
        nbt.putBoolean("PersistenceRequired", true);
        nbt.putInt("Lifespan", Integer.MAX_VALUE);
        entity.load(nbt);

        level.addFreshEntity(entity);

        level.playSound(null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0f, 1.5f);
        level.playSound(null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, 0.8f, 1.2f);

        PixelFx.send(level, ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.05);
        PixelFx.send(level, ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                15, 0.4, 0.4, 0.4, 0.1);
        PixelFx.send(level, ParticleTypes.FIREWORK,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                10, 0.2, 0.2, 0.2, 0.15);
    }

    private static Item getDeepLuckyBlock() {
        if (LOOKUP_DONE) return DEEP_LUCKY_BLOCK_ITEM;

        try {
            DEEP_LUCKY_BLOCK_ITEM = BuiltInRegistries.ITEM.get(
                    ResourceLocation.tryParse(DEEP_LUCKY_BLOCK_ID));

            if (DEEP_LUCKY_BLOCK_ITEM == null) {
                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    if (key != null && key.toString().equals(DEEP_LUCKY_BLOCK_ID)) {
                        DEEP_LUCKY_BLOCK_ITEM = item;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        LOOKUP_DONE = true;
        return DEEP_LUCKY_BLOCK_ITEM;
    }
}
