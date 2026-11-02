package deepluckyblock.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pont optionnel vers le pool dimensionnel officiel de Lucky Tweaks.
 *
 * Lucky XP 1.0.3 appelle ce pool a trois endroits distincts : resultat serveur,
 * shower jackpot et bande visuelle cliente. Les mixins Lucky XP redirigent ces
 * appels ici ; on conserve la liste officielle et on ajoute uniquement le Deep
 * Lucky Block. Aucune entree worldgen n'est creee.
 */
public final class LuckyXpEventPoolCompatibility {

    private static final ResourceLocation DEEP_LUCKY_BLOCK_ID =
            ResourceLocation.tryParse("deep_lucky_block:deep_lucky_block");

    private static final Set<String> LOGGED_SOURCES =
            ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean ICON_LOGGED = new AtomicBoolean();

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean failureLogged;
    private static Method getDimensionPool;

    private LuckyXpEventPoolCompatibility() {}

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded("luckytweaks")) return;

        try {
            Class<?> api = Class.forName(
                    "com.lwi.luckytweaks.api.LuckyTweaksApi");
            getDimensionPool = api.getMethod(
                    "getLuckyBlockIds", ResourceLocation.class);
            available = true;
        } catch (Throwable error) {
            logFailureOnce(error);
        }
    }

    /**
     * Retourne une copie sure du pool officiel, avec Deep present une fois.
     * source identifie l'appel exact de Lucky XP pour le diagnostic runtime.
     */
    public static List<ResourceLocation> dimensionPool(
            ResourceLocation dimension, String source) {
        List<ResourceLocation> result = new ArrayList<>();

        initialize();
        if (available && getDimensionPool != null) {
            try {
                Object value = getDimensionPool.invoke(null, dimension);
                if (value instanceof Iterable<?>) {
                    for (Object entry : (Iterable<?>) value) {
                        if (entry instanceof ResourceLocation) {
                            result.add((ResourceLocation) entry);
                        }
                    }
                }
            } catch (Throwable error) {
                available = false;
                logFailureOnce(error);
            }
        }

        boolean deepRegistered = false;
        if (DEEP_LUCKY_BLOCK_ID != null) {
            Block deepBlock = BuiltInRegistries.BLOCK.get(DEEP_LUCKY_BLOCK_ID);
            deepRegistered = deepBlock != null && deepBlock != Blocks.AIR;
            if (deepRegistered && !result.contains(DEEP_LUCKY_BLOCK_ID)) {
                result.add(DEEP_LUCKY_BLOCK_ID);
            }
        }

        String dimensionName = dimension != null ? dimension.toString() : "<null>";
        String sourceName = source != null ? source : "unknown";
        if (LOGGED_SOURCES.add(sourceName + "|" + dimensionName)) {
            deepluckyblock.util.DebugLog.out("[DeepLuckyBlock/LuckyXP] " + sourceName
                    + " pool " + dimensionName + ": size=" + result.size()
                    + ", deepPresent=" + result.contains(DEEP_LUCKY_BLOCK_ID)
                    + ", deepRegistered=" + deepRegistered);
        }
        return result;
    }

    public static boolean isDeepLuckyBlock(ResourceLocation id) {
        return DEEP_LUCKY_BLOCK_ID != null && DEEP_LUCKY_BLOCK_ID.equals(id);
    }

    public static Block deepLuckyBlock() {
        if (DEEP_LUCKY_BLOCK_ID == null) return null;
        Block block = BuiltInRegistries.BLOCK.get(DEEP_LUCKY_BLOCK_ID);
        return block == Blocks.AIR ? null : block;
    }

    public static void logClientIconRendered() {
        if (ICON_LOGGED.compareAndSet(false, true)) {
            deepluckyblock.util.DebugLog.out("[DeepLuckyBlock/LuckyXP] CLIENT Deep item icon rendered");
        }
    }

    private static void logFailureOnce(Throwable error) {
        if (failureLogged) return;
        failureLogged = true;
        deepluckyblock.util.DebugLog.err("[DeepLuckyBlock/LuckyXP] official dimension pool unavailable: "
                + error);
    }
}
