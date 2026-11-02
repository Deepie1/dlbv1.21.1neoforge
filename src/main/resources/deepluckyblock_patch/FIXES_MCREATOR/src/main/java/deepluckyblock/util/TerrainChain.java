package deepluckyblock.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * UNE SEULE CHAINE DE TERRAIN A LA FOIS.
 *
 * <p>Les passes de terrain de {@code StructureTerrainPrep} partagent des champs
 * statiques : nom de la structure en cours, liste des arbres sauves, colonnes de
 * barrage d'eau, emprise protegee, libelles de passes en cours... Si deux
 * structures declenchent leur chaine terrain en meme temps, la seconde ecrase
 * l'etat de la premiere : la structure A replante les arbres sauves pour B,
 * B seme les decors de A, et la protection d'emprise saute au milieu d'un
 * smooth de A. Le resultat est un terrain melange, difficile a diagnostiquer
 * (le log ne montre qu'un enchainement d'etapes qui semble normal).
 *
 * <p>La chaine suivante n'est donc pas perdue : elle est <b>reportee</b>. Chaque
 * appelant qui ne peut pas prendre la chaine replanifie son travail quelques
 * ticks plus tard (voir {@code StructureTerrainPrep.chainWait}).
 *
 * <p>Garde-fou anti-blocage : si le proprietaire ne donne plus aucun signe de
 * vie (aucun battement de coeur pendant {@link #STEAL_MS}), le verrou est repris
 * de force avec un WARN. Une chaine ne peut donc jamais rester bloquee pour
 * toujours, meme si une passe a echoue en cours de route.
 */
public final class TerrainChain {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Duree sans progression au-dela de laquelle le verrou est repris de force. */
    private static final long STEAL_MS = 90_000L;

    private static String owner = null;
    private static long lastActivity = 0L;

    private TerrainChain() {}

    /** Proprietaire courant ("aucune chaine" s'il n'y en a pas). */
    public static synchronized String owner() {
        return owner == null ? "aucune chaine" : owner;
    }

    /** Signale que la chaine avance (appele a chaque tranche de travail). */
    public static synchronized void heartbeat() {
        lastActivity = System.currentTimeMillis();
    }

    /**
     * Tente de prendre la chaine terrain.
     *
     * @return true si elle est libre (ou deja a nous) : l'appelant peut travailler.
     *         false si une autre chaine tourne : l'appelant doit se reporter.
     */
    public static synchronized boolean acquire(String tag) {
        long now = System.currentTimeMillis();
        if (owner == null || owner.equals(tag)) {
            owner = tag;
            lastActivity = now;
            return true;
        }
        if (now - lastActivity > STEAL_MS) {
            deepluckyblock.util.DebugLog.warn(LOGGER, "[DLB-CHAIN] chaine terrain '{}' sans progression depuis {} s -- reprise de force par '{}'",
                    owner, (now - lastActivity) / 1000, tag);
            owner = tag;
            lastActivity = now;
            return true;
        }
        return false;
    }

    /** Rend la chaine (uniquement si on en est le proprietaire). */
    public static synchronized void release(String tag) {
        if (tag != null && tag.equals(owner)) {
            owner = null;
            lastActivity = System.currentTimeMillis();
        }
    }

    /** Vrai si c'est a nous (pour eviter les logs parasites). */
    public static synchronized boolean owns(String tag) {
        return tag != null && tag.equals(owner);
    }
}
