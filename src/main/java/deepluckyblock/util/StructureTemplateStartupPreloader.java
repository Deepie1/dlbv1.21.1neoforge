package deepluckyblock.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Met une seule fois tous les gros templates du mod en cache au démarrage du
 * serveur. Le travail lourd reste sur le thread DLB-StructureLoader : cette
 * classe ne bloque jamais le thread serveur.
 *
 * <p>StructureTemplateCache utilise un loader mono-thread et des ensembles
 * concurrents. Les huit demandes sont donc traitées successivement, sans parse
 * concurrent ni double lecture, puis conservées en mémoire pour toute la
 * session. Un appel ultérieur à requestAsync/get rejoint simplement le cache.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class StructureTemplateStartupPreloader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StructureTemplateStartupPreloader() {
    }

    /**
     * T156 : déclenché pendant le chargement DU JEU, avant l'écran des mondes.
     * La lecture, décompression et filtration lourde des NBT se font ici. Le
     * démarrage d'une map ne fait ensuite que résoudre les états de blocs avec
     * les registres du serveur, opération impossible avant leur création.
     */
    @EventBusSubscriber(modid = "deep_lucky_block", bus = EventBusSubscriber.Bus.MOD)
    public static final class GameLaunchHook {
        private GameLaunchHook() {}

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            StructureTemplateCache.preloadCompressedAtGameLaunch();
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer() == null) return;
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) return;

        int requested = 0;
        for (String name : StructureTemplateCache.ALL_STRUCTURE_NAMES) {
            if (!StructureTemplateCache.isCached(name)
                    && !StructureTemplateCache.isPending(name)) {
                StructureTemplateCache.requestAsync(overworld, name);
                requested++;
            }
        }
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-T148] préchargement de démarrage : {}/{} template(s) mis en file; "
                        + "lecture unique asynchrone, cache conservé pour toute la session",
                requested, StructureTemplateCache.ALL_STRUCTURE_NAMES.size());
    }
}
