package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * GARDE-FOU D'EDITION (T10) -- implementation de la section « gravity /
 * fluid ticks pendant un gros edit » du guide d'optimisation 1.21.1.
 *
 * <p>Pendant qu'une structure est en cours de travail (preparation du terrain,
 * paste, carve, decorate), la zone modifiee est le siege de deux cascades :
 *
 * <ul>
 *   <li><b>chute de blocs</b> : le NBT de nos structures contient de vrais
 *       volumes de sable/gravier (mesure au parseur NBT : shipdead 59 987
 *       blocs de sable, crimsonlake 16 265 sable + 252 gravier, citadel 440
 *       gravier). Chaque bloc de gravite pose planifie un tick de chute ;
 *       si le terrain sous lui est retaille entre-temps (c'est exactement ce
 *       que font carve/smooth/fillGapsAndSteps), la chaine entiere tombe d'un
 *       coup -> des milliers de {@link FallingBlockEntity} en un tick.</li>
 *   <li><b>tick de fluide</b> : chaque bloc d'eau de la zone a au moins un
 *       tick de fluide en attente. Elargir un trou pres d'une flaque declenche
 *       une propagation RECURSIVE (1 source -> des centaines de ticks), c'est
 *       l'« eau qui coule » pendant le pas de paste/decorate.</li>
 * </ul>
 *
 * <p>Le guide recommande de neutraliser ces deux sources pendant la fenetre
 * d'edition (son « FluidTickSuppressor » + mode « instant/batch » des falling
 * blocks). On le fait ici <b>sans mixin</b>, avec l'API vanilla
 * {@code LevelTicks.clearArea(BoundingBox)} : a chaque tick de la fenetre, les
 * ticks de bloc et de fluide EN ATTENTE dans la zone sont annules. Les blocs
 * tombes dans le vide ne sont donc jamais convertis en entites, et l'eau ne se
 * propage pas pendant que le terrain bouge. Une fois la fenetre fermee, la
 * zone obeit de nouveau au jeu normal (le clamp n'enleve aucun tick futur, il
 * annule seulement ceux qui pendent pendant le travail).
 *
 * <p>La zone est celle tenue en memoire par {@link ChunkKeeper} : le clamp
 * couvre donc exactement prepZone -> paste -> carve -> decorate, et s'arrete
 * de lui-meme si la zone est relachee (garde-fou 600 s inclus).
 *
 * <p>La meme boucle sert de <b>mesure de preuve</b> : duree de la fenetre,
 * MSPT lisse (getCurrentSmoothedTickTime), pire pic, nombre de ticks au-dessus
 * de 50 ms, et nombre maximal d'entites de chute presentes dans la zone. C'est
 * cette ligne qui permet de verifier « 0 lag, 0 freeze » par log, sans Spark.
 *
 * <p>Interrupteur : {@link #ENABLED} (mettre a false et recompiler desactive
 * completement le clamp sans toucher au reste du pipeline).
 */
public final class TerrainEditClamp {

    /** Interrupteur global : false = aucun tick annule (mesure seule). */
    public static boolean ENABLED = true;

    /** On echantillonne les entites de chute tous les N ticks (cout faible). */
    private static final int ENTITY_SAMPLE_TICKS = 5;

    /** T16 : seuil de la sonde de tick (ms). 1500 ms = 30 ticks de retard. */
    private static final long LAGPROBE_MS = 1500L;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ServerLevel, Run> RUNS = new HashMap<>();

    private TerrainEditClamp() {}

    private static final class Run {
        int minX, minY, minZ, maxX, maxY, maxZ;
        long startMs = System.currentTimeMillis();
        long startTick;
        int ticks;
        float worstMs;           // MSPT lisse (valeur du serveur, pour reference)
        double sumMs;
        int spikes;              // ticks lisses > 50 ms
        int samples;
        // Mesure HONNETE du rythme reel : ecart en ms entre deux passages du
        // pilote (= periode de tick observee). Le MSPT lisse du serveur ne voit
        // pas les phases ou le thread principal est accapare (mesure du 20/09 :
        // 3,8 ms de MSPT lisse alors que le serveur tournait a ~2 TPS) : c'est
        // cette periode qui dit la verite au joueur.
        long lastRunMs;
        long worstPeriodMs;
        double sumPeriodMs;
        int periodSamples;
        int slowTicks;           // periodes > 100 ms (moins de 10 TPS)
        int stallTicks;          // periodes > 1000 ms (plus d'une seconde)
        int maxFalling;          // entites de chute observes dans la zone
        boolean driverScheduled;
    }

    // ------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------

    /** Ouvre (ou etend) la fenetre d'edition sur la boite donnee. */
    public static void start(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (level == null) return;
        Run r = RUNS.get(level);
        if (r == null) {
            r = new Run();
            r.startTick = level.getGameTime();
            r.minX = minX; r.minY = minY; r.minZ = minZ;
            r.maxX = maxX; r.maxY = maxY; r.maxZ = maxZ;
            RUNS.put(level, r);
            DebugLog.perfBegin();   // T47 : chronometre du pipeline
            deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-CLAMP] fenetre d'edition ouverte sur {}x{}x{} a {} -- ticks de chute et de fluide neutralises dans la zone",
                    maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, minX + "," + minZ);
        } else {
            r.minX = Math.min(r.minX, minX); r.minY = Math.min(r.minY, minY); r.minZ = Math.min(r.minZ, minZ);
            r.maxX = Math.max(r.maxX, maxX); r.maxY = Math.max(r.maxY, maxY); r.maxZ = Math.max(r.maxZ, maxZ);
        }
        if (!r.driverScheduled) {
            r.driverScheduled = true;
            drive(level);
        }
    }

    /** Ferme la fenetre d'edition et publie le bilan chiffre. */
    public static void stop(ServerLevel level, String reason) {
        Run r = RUNS.remove(level);
        if (r == null) return;
        long ms = System.currentTimeMillis() - r.startMs;
        double avg = r.samples > 0 ? r.sumMs / r.samples : 0.0;
        double avgPeriod = r.periodSamples > 0 ? r.sumPeriodMs / (double) r.periodSamples : 0.0;
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-CLAMP] fenetre fermee ({}) : {} s de travail, {} ticks ; periode de tick moyenne {} ms, pire {} ms (20 TPS = 50 ms) ; {} tick(s) > 100 ms dont {} > 1 s ; MSPT lisse {} ms ; chute max {} entite(s)",
                reason, ms / 1000, r.ticks, String.format("%.0f", avgPeriod), r.worstPeriodMs,
                r.slowTicks, r.stallTicks, String.format("%.1f", avg), r.maxFalling);
        // T47 : ou est passe le temps (phases classees par duree).
        String perf = DebugLog.perfSummary();
        if (!perf.isEmpty()) deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-PERF] {}", perf);
        // T53 : fin de pipeline -> le cache de chunks est vide (aucune reference de chunk
        // conservee entre deux structures ; le chunk system reste seul maitre de la memoire).
        deepluckyblock.util.SafeSurface.clearCache();
    }

    /** Vrai si la fenetre est ouverte (utilisable comme tempo par les passes). */
    public static boolean active(ServerLevel level) {
        return RUNS.containsKey(level);
    }

    // ------------------------------------------------------------------
    // Boucle par tick
    // ------------------------------------------------------------------

    private static void drive(ServerLevel level) {
        Run r = RUNS.get(level);
        if (r == null) return;
        // La zone n'est plus tenue par ChunkKeeper -> fin de fenetre.
        if (!ChunkKeeper.held(level)) {
            stop(level, "zone relachee");
            return;
        }

        BoundingBox box = new BoundingBox(r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ);
        if (ENABLED) {
            // Annule les ticks de bloc (dont les chutes de sable/gravier) et les
            // ticks de fluide QUI PENDENT dans la zone. Nouveau a chaque tick :
            // le paste et les passes en reprogramment en permanence, ils sont
            // donc neutralises tant que la fenetre est ouverte.
            try {
                level.getBlockTicks().clearArea(box);
            } catch (Throwable t) {
                LOGGER.warn("[DLB-CLAMP] clearArea(blockTicks) indisponible : {}", t.toString());
                ENABLED = false;
            }
            try {
                level.getFluidTicks().clearArea(box);
            } catch (Throwable t) {
                LOGGER.warn("[DLB-CLAMP] clearArea(fluidTicks) indisponible : {}", t.toString());
                ENABLED = false;
            }
        }

        // Mesure 1 : periode de tick REELLE (temps entre deux passages).
        long nowMs = System.currentTimeMillis();
        if (r.lastRunMs != 0L) {
            long period = nowMs - r.lastRunMs;
            r.sumPeriodMs += period;
            r.periodSamples++;
            if (period > r.worstPeriodMs) r.worstPeriodMs = period;
            if (period > 100L) r.slowTicks++;
            if (period > 1000L) r.stallTicks++;
            // T16 : sonde de tick. Le chien de garde de Minecraft tue le serveur
            // au premier tick de plus de 60 s SANS dire ce qui tournait ; ce log
            // nomme la phase en cours (DebugLog.setPhase, alimente par step() et
            // par les boucles decoupees) au moment exact du ralentissement.
            // C'est la seule mesure qui remonte l'information AVANT la mort du
            // serveur -- indispensable pour ne pas corriger a l'aveugle.
            if (period >= LAGPROBE_MS) {
                LOGGER.warn("[DLB-LAGPROBE] periode de tick de {} ms pendant la phase \u00ab {} \u00bb"
                                + " -- c'est cette phase qui accapare le thread principal ({} ; {} tick(s) > 1 s au total)",
                        period, deepluckyblock.util.DebugLog.phaseNow(), ChunkKeeper.stats(level), r.stallTicks);
            }
        }
        r.lastRunMs = nowMs;

        // Mesure 2 : MSPT lisse du serveur (pour reference).
        try {
            float mspt = level.getServer().getCurrentSmoothedTickTime();
            r.sumMs += mspt;
            r.samples++;
            r.ticks++;
            if (mspt > r.worstMs) r.worstMs = mspt;
            if (mspt > 50.0f) r.spikes++;
        } catch (Throwable ignored) {
            r.ticks++;
        }
        if (r.ticks % ENTITY_SAMPLE_TICKS == 0) {
            try {
                int n = level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(
                        r.minX, r.minY, r.minZ, r.maxX + 1.0, r.maxY + 1.0, r.maxZ + 1.0)).size();
                if (n > r.maxFalling) r.maxFalling = n;
            } catch (Throwable ignored) {
                // mesure optionnelle
            }
        }

        deepluckyblock.procedures.TestProcedure.schedule(level,
                deepluckyblock.procedures.TestProcedure.currentTick(level) + 1, () -> drive(level));
    }

    /** Boite courante, au format {minX, minY, minZ, maxX, maxY, maxZ}, ou null. */
    public static int[] currentBox(ServerLevel level) {
        Run r = RUNS.get(level);
        if (r == null) return null;
        return new int[]{ r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ };
    }

    /** Utilitaire : boite d'une structure elargie du ring de terrain. */
    public static int[] boxOf(BlockPos min, BlockPos max, int ring) {
        return new int[]{ min.getX() - ring, Math.max(min.getY() - 32, -64), min.getZ() - ring,
                          max.getX() + ring, Math.min(max.getY() + 32, 319), max.getZ() + ring };
    }
}
