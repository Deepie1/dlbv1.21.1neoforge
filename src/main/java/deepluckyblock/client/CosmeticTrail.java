package deepluckyblock.client;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * T436-TRAINEE : mémoire des pas d'un joueur, côté client uniquement.
 *
 * <p><b>Problème résolu.</b> Une traînée dessinée « derrière le joueur » dans son repère local
 * n'est pas une traînée : elle tourne avec lui, elle défile quand il piétine, et elle ne dit
 * rien de son déplacement réel. Elle se lit comme un autocollant collé aux semelles.</p>
 *
 * <p><b>Solution.</b> On retient les dernières positions <em>du monde</em> où le joueur est
 * réellement passé. Une empreinte est déposée tous les {@link #PAS} blocs parcourus — jamais
 * à intervalle de temps — et elle s'éteint au bout de {@link #VIE_TICKS}. Deux conséquences,
 * qui sont exactement le comportement demandé :</p>
 * <ul>
 *   <li>le joueur <b>s'arrête</b> : plus aucun dépôt, les plus anciennes (donc les plus
 *       lointaines) s'éteignent les unes après les autres, la traînée <b>se rétracte</b>
 *       vers ses pieds puis disparaît ;</li>
 *   <li>il <b>repart</b> : les dépôts reprennent sous lui et la traînée <b>se redéploie</b>.</li>
 * </ul>
 *
 * <p>Le joueur qui tourne sur place ne fait pas pivoter sa traînée : elle est en coordonnées
 * monde, elle reste là où les pas ont eu lieu.</p>
 */
public final class CosmeticTrail {

    private CosmeticTrail() {}

    /** Distance entre deux empreintes, en blocs. */
    public static final double PAS = 0.42;
    /** Durée de vie d'une empreinte, en ticks (≈ 3 s). */
    public static final int VIE_TICKS = 60;
    /** Nombre maximal d'empreintes retenues par joueur. */
    public static final int MAX = 8;

    /** Une empreinte : où le pied s'est posé, et quand. */
    public static final class Pas {
        public double x, y, z;
        public long tick;
        /** 0 = fraîche, 1 = éteinte. Rempli par {@link #collecte}. */
        public float age;
    }

    private static final class Piste {
        final Pas[] pas = new Pas[MAX];
        int debut = 0;      // index de la plus ancienne
        int taille = 0;
        double lastX, lastY, lastZ;
        boolean amorcee = false;
        long vu;
        boolean solAvant = true;
        float chuteAvant = 0f;
    }

    private static final Map<Integer, Piste> PISTES = new HashMap<>();
    private static final Pas[] VIDE = new Pas[0];

    /** Durée d'une onde de choc, en ticks : au-delà elle a fini de s'éteindre. */
    public static final int ONDE_TICKS = 22;

    /**
     * Un atterrissage : où, quand, et de quelle hauteur. Détecté CÔTÉ CLIENT, sans
     * aucun paquet — la chute et le contact au sol sont déjà connus localement.
     */
    public static final class Impact {
        public double x, y, z;
        public long tick;
        public float chute;
    }

    private static final Map<Integer, Impact> IMPACTS = new HashMap<>();

    /**
     * À appeler une fois par tick : mémorise le moment où le joueur touche le sol et la
     * hauteur d'où il vient. C'est ce qui donne son écho à l'onde de choc.
     */
    public static void noteChute(Player p, long tick) {
        if (p == null) return;
        Piste piste = PISTES.get(p.getId());
        float avant = piste == null ? 0f : piste.chuteAvant;
        boolean solAvant = piste != null && piste.solAvant;
        float courante = p.fallDistance;
        if (piste == null) {
            piste = new Piste();
            PISTES.put(p.getId(), piste);
        }
        piste.vu = tick;
        // Le tick où l'on passe de « en l'air » à « au sol » : la hauteur utile est
        // celle du tick PRÉCÉDENT, car le contact remet fallDistance à zéro.
        if (p.onGround() && !solAvant && avant >= 1.0f) {
            Impact im = IMPACTS.get(p.getId());
            if (im == null) {
                im = new Impact();
                IMPACTS.put(p.getId(), im);
            }
            im.x = p.getX();
            im.y = p.getY();
            im.z = p.getZ();
            im.tick = tick;
            im.chute = Math.min(40f, avant);
        }
        piste.solAvant = p.onGround();
        piste.chuteAvant = courante;
    }

    /** Le dernier atterrissage encore visible, ou {@code null} si l'onde est éteinte. */
    public static Impact impact(Player p, long tick) {
        if (p == null) return null;
        Impact im = IMPACTS.get(p.getId());
        if (im == null) return null;
        long age = tick - im.tick;
        return (age < 0L || age >= ONDE_TICKS) ? null : im;
    }

    /**
     * Enregistre la position courante si le joueur a parcouru assez de distance.
     * Appelé une fois par tick client et par joueur visible.
     */
    public static void suivre(Player p, long tick) {
        if (p == null) return;
        Piste piste = PISTES.get(p.getId());
        if (piste == null) {
            piste = new Piste();
            PISTES.put(p.getId(), piste);
        }
        piste.vu = tick;

        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (!piste.amorcee) {
            piste.amorcee = true;
            piste.lastX = x;
            piste.lastY = y;
            piste.lastZ = z;
            depose(piste, x, y, z, tick);
            return;
        }
        double dx = x - piste.lastX, dz = z - piste.lastZ;
        // Le saut vertical ne compte pas : sinon un joueur qui saute sur place
        // sèmerait des empreintes empilées sans jamais avancer.
        if (dx * dx + dz * dz >= PAS * PAS) {
            piste.lastX = x;
            piste.lastY = y;
            piste.lastZ = z;
            depose(piste, x, y, z, tick);
        }
        purge(piste, tick);
    }

    private static void depose(Piste piste, double x, double y, double z, long tick) {
        Pas slot;
        if (piste.taille < MAX) {
            int idx = (piste.debut + piste.taille) % MAX;
            if (piste.pas[idx] == null) piste.pas[idx] = new Pas();
            slot = piste.pas[idx];
            piste.taille++;
        } else {
            slot = piste.pas[piste.debut];           // on recycle la plus ancienne
            piste.debut = (piste.debut + 1) % MAX;
        }
        slot.x = x;
        slot.y = y;
        slot.z = z;
        slot.tick = tick;
    }

    private static void purge(Piste piste, long tick) {
        while (piste.taille > 0) {
            Pas vieille = piste.pas[piste.debut];
            if (tick - vieille.tick < VIE_TICKS) break;
            piste.debut = (piste.debut + 1) % MAX;
            piste.taille--;
        }
    }

    /**
     * Empreintes vivantes, de la plus RÉCENTE à la plus ancienne, avec leur âge rempli.
     * Le tableau retourné est réutilisé d'un appel à l'autre : à consommer immédiatement.
     */
    public static Pas[] collecte(Player p, long tick, float partialTick) {
        Piste piste = PISTES.get(p.getId());
        if (piste == null || piste.taille == 0) return VIDE;
        purge(piste, tick);
        if (piste.taille == 0) return VIDE;

        Pas[] sortie = new Pas[piste.taille];
        for (int i = 0; i < piste.taille; i++) {
            // on parcourt à rebours : index 0 = la plus récente
            int idx = (piste.debut + piste.taille - 1 - i) % MAX;
            Pas src = piste.pas[idx];
            src.age = Math.min(1f, (tick - src.tick + partialTick) / (float) VIE_TICKS);
            sortie[i] = src;
        }
        return sortie;
    }

    /** Oublie les joueurs qu'on ne voit plus (changement de dimension, déconnexion). */
    public static void nettoie(long tick) {
        Iterator<Map.Entry<Integer, Piste>> it = PISTES.entrySet().iterator();
        while (it.hasNext()) {
            if (tick - it.next().getValue().vu > 200L) it.remove();
        }
        Iterator<Map.Entry<Integer, Impact>> ii = IMPACTS.entrySet().iterator();
        while (ii.hasNext()) {
            if (tick - ii.next().getValue().tick > 200L) ii.remove();
        }
    }

    /** Vidage complet (changement de monde). */
    public static void vide() {
        PISTES.clear();
        IMPACTS.clear();
    }
}
