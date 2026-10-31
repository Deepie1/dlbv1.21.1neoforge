package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * SettleGate -- T16 : porte de stabilisation de fin de pipeline.
 *
 * POURQUOI (mesure en jeu du 20/09/2026, log `t13_run2`) : a la fin de la
 * preparation de terrain, le serveur est mort sur « A single server tick took
 * 60.27 seconds ». Le dernier log ecrit etait une phase terminee : le pipeline
 * declarait le terrain pret, rendait la zone au chunk system, et le tick
 * SUIVANT -- que plus rien n'instrumentait -- partait en vrille. Conclusion :
 * « derniere passe finie » != « monde retombe ». Les restes a digerer sont
 * connus et mesurables :
 *   - des entites de chute (sable/gravier) encore en vol, que la fenetre
 *     DLB-CLAMP cesse de neutraliser des qu'elle se ferme ;
 *   - le moteur de lumiere (ThreadedLevelLightEngine) qui a encaisse les
 *     dizaines de milliers d'ecritures du paste + des smooths et qui continue
 *     de propager sur 169 chunks.
 *
 * La porte observe ces deux compteurs et ne rend la zone au chunk system que
 * lorsqu'ils sont retombes (2 ticks consecutifs au repos), avec un delai
 * maximal borne (200 ticks = 10 s) : une structure ne peut donc JAMAIS rester
 * gelee indefiniment derriere cette porte. Chaque etat est journalise, y
 * compris l'abandon au bout du delai -- pas de succes silencieux.
 *
 * Reference guide : sections 30 (tenir sous les 30 s), 31 (time-slicing) et 45
 * (synthese du pipeline : « ne jamais declarer termine sans verifier »).
 */
public final class SettleGate {

    private SettleGate() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Duree maximale d'attente (ticks). Au-dela, on continue en le disant. */
    private static final int MAX_WAIT_TICKS = 200;
    /** Nombre de ticks consecutifs au repos exiges avant de valider. */
    private static final int STABLE_TICKS = 2;
    /** Marge autour de l'emprise : attrape les entites de chute des abords. */
    private static final int MARGIN = 24;
    /** Frequence des comptes rendus d'attente (ticks). */
    private static final int REPORT_EVERY = 20;

    /** Attend la stabilisation de la zone puis execute onDone (jamais deux fois). */
    public static void wait(ServerLevel level, BlockPos min, BlockPos max, Runnable onDone) {
        if (level == null) { if (onDone != null) onDone.run(); return; }
        step(level, min, max, 0, 0, onDone);
    }

    private static void step(ServerLevel level, BlockPos min, BlockPos max,
                             int elapsed, int stable, Runnable onDone) {
        final int falling = fallingIn(level, min, max);
        final boolean lightBusy = lightBusy(level);
        final boolean pending = falling > 0 || lightBusy;

        if (elapsed == 0) {
            LOGGER.info("[DLB-SETTLE] fin de pipeline : attente de stabilisation de la zone "
                            + "(entites de chute : {} en vol, moteur de lumiere : {}) -- la zone sera "
                            + "rendue au chunk system quand le monde sera retombe (max {} ticks)",
                    falling, lightBusy ? "occupe" : "au repos", MAX_WAIT_TICKS);
        }

        int newStable = pending ? 0 : stable + 1;

        if (newStable >= STABLE_TICKS && elapsed > 0) {
            LOGGER.info("[DLB-SETTLE] zone stabilisee apres {} tick(s) d'attente ({} s) : aucune entite "
                            + "de chute, moteur de lumiere au repos -- le terrain est REELLEMENT definitif",
                    newStable, String.format("%.1f", elapsed / 20.0));
            if (onDone != null) onDone.run();
            return;
        }
        if (elapsed >= MAX_WAIT_TICKS) {
            LOGGER.warn("[DLB-SETTLE] delai maximal atteint ({} ticks) : la zone n'est pas encore au repos "
                            + "({} entite(s) de chute, lumiere {}) -- on continue quand meme pour ne pas "
                            + "bloquer la structure ; les passes restantes filtreront",
                    MAX_WAIT_TICKS, falling, lightBusy ? "occupee" : "au repos");
            if (onDone != null) onDone.run();
            return;
        }
        if (elapsed > 0 && elapsed % REPORT_EVERY == 0 && pending) {
            LOGGER.info("[DLB-SETTLE] attente de stabilisation : {} tick(s), {} entite(s) de chute, "
                            + "lumiere {}", elapsed, falling, lightBusy ? "occupee" : "au repos");
        }
        deepluckyblock.procedures.TestProcedure.schedule(level,
                deepluckyblock.procedures.TestProcedure.currentTick(level) + 1,
                () -> step(level, min, max, elapsed + 1, newStable, onDone));
    }

    /** Entites de chute presentes dans l'emprise elargie. */
    public static int fallingIn(ServerLevel level, BlockPos min, BlockPos max) {
        try {
            AABB box = new AABB(min.getX() - MARGIN, level.getMinBuildHeight(), min.getZ() - MARGIN,
                    max.getX() + 1 + MARGIN, level.getMaxBuildHeight(), max.getZ() + 1 + MARGIN);
            return level.getEntitiesOfClass(FallingBlockEntity.class, box).size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Vrai si le moteur de lumiere a encore des mises a jour en attente. */
    public static boolean lightBusy(ServerLevel level) {
        try {
            net.minecraft.world.level.lighting.LevelLightEngine engine =
                    level.getChunkSource().getLightEngine();
            if (engine instanceof ThreadedLevelLightEngine threaded) {
                return threaded.hasLightWork();
            }
            return engine.hasLightWork();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
