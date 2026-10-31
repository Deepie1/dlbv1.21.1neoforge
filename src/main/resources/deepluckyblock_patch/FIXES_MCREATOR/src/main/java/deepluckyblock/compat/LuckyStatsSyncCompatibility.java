package deepluckyblock.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Push immediat et facultatif du payload natif Lucky Stats vers son client.
 *
 * Lucky Stats possede deja un polling client. Ce filet de securite utilise ses
 * propres StatsCollector et StatsDataPacket : aucune statistique parallele n'est
 * creee et aucune cle NBT n'est reecrite ici.
 *
 * ⚠️ PORT 1.21.1 : Forge SimpleChannel n'existe plus dans NeoForge. Le canal de
 * Lucky Stats est resolu par reflection et son send() est invoque generiquement
 * (best effort) ; si l'API a change, le polling natif de Lucky Stats reste actif.
 */
public final class LuckyStatsSyncCompatibility {

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean failureLogged;

    private static Method collectStats;
    private static Constructor<?> statsPacketConstructor;
    private static Object statsChannel;

    private LuckyStatsSyncCompatibility() {}

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded("luckystats")) return;

        try {
            Class<?> collectorClass = Class.forName(
                    "com.lwi.luckystats.util.StatsCollector");
            collectStats = collectorClass.getMethod("collect", ServerPlayer.class);

            Class<?> packetClass = Class.forName(
                    "com.lwi.luckystats.net.StatsDataPacket");
            statsPacketConstructor = packetClass.getConstructor(CompoundTag.class);

            Class<?> networkClass = Class.forName(
                    "com.lwi.luckystats.net.NetworkHandler");
            Object channel = networkClass.getField("CHANNEL").get(null);
            if (channel != null) {
                statsChannel = channel;
                available = true;
            }
        } catch (Throwable error) {
            logFailureOnce(error);
        }
    }

    public static void sync(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded("luckystats")) return;
        initialize();
        if (!available || collectStats == null
                || statsPacketConstructor == null || statsChannel == null) return;

        try {
            Object payload = collectStats.invoke(null, player);
            if (!(payload instanceof CompoundTag)) return;

            CompoundTag statsPayload = (CompoundTag) payload;
            Object packet = statsPacketConstructor.newInstance(statsPayload);

            // Best effort : cherche un send(Object... ou (TargetPoint, Object))
            // sur le canal de Lucky Stats sans dependre d'une classe Forge/NF.
            Method send = null;
            // PORT 1.21.1 : plus de PacketDistributor.PLAYER -> on cherche un send(ServerPlayer, ...)
            for (Method m : statsChannel.getClass().getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1].isInstance(packet)
                        && m.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class)) {
                    send = m;
                    break;
                }
            }
            if (send != null) {
                send.invoke(statsChannel, player, packet);
            }
        } catch (Throwable error) {
            available = false;
            logFailureOnce(error);
        }
    }

    private static void logFailureOnce(Throwable error) {
        if (failureLogged) return;
        failureLogged = true;
        System.err.println("[DeepLuckyBlock/LuckyStats] immediate sync unavailable; "
                + "native polling remains active: " + error);
    }
}
