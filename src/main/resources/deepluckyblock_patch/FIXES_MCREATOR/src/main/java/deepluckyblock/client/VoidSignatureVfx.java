package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T388-VOID-REFONTE — élément <b>VIDE</b> : trois signatures voxel sur un moteur commun.
 *
 * <p><b>Pourquoi cette classe ?</b> Les trois titres du Vide étaient, avec le Chaos, les
 * derniers du mod sous la seconde : {@code VOID_WARP_SPHERE} tenait <b>16 ticks</b>,
 * {@code NULL_RIFT_FISSURE} 18, {@code DRAGON_CHAOS_PRISM} 22. Ils partageaient en outre un
 * unique bloc générique de trente lignes dans {@code WeaponWorldVfxManager#impactVoid} : la
 * même coquille de cubes inclinés pour les trois, avec deux branchements cosmétiques. Rien
 * n'y distinguait une sphère de distorsion d'une faille.</p>
 *
 * <h2>Le problème propre à cet élément — et il est unique dans le mod</h2>
 *
 * <p>Toutes les familles précédentes dessinent <i>quelque chose</i> : une flamme, un arc, un
 * liquide, un os. Le vide est l'<b>absence</b> de quelque chose. Or un moteur voxel ne sait
 * qu'<b>ajouter</b> de la matière. Dessiner « rien » en ajoutant des cubes est une
 * contradiction dans les termes, et c'est exactement le mur sur lequel l'ancien rendu s'est
 * écrasé : faute de savoir dessiner le vide, il dessinait une boule.</p>
 *
 * <p>Trois règles en sortent, et tout ce fichier les applique.</p>
 * <ol>
 *   <li><b>On ne dessine jamais le vide, on dessine son BORD.</b> L'intérieur de tout volume
 *       de vide contient <i>zéro cube</i>. Ce qui est tracé, c'est la lèvre : une coquille
 *       d'un cube d'épaisseur. Le noir du monde passe au travers, et ce noir EST l'effet.
 *       C'est l'inverse exact de la règle de l'Ombre — là-bas le liseré disait « ceci est un
 *       trou », ici <b>le liseré est l'objet tout entier</b>.</li>
 *   <li><b>Une déformation sans référence est invisible.</b> Chaque signature porte donc sa
 *       propre <b>grille témoin</b> : droite, régulière, manifestement fabriquée. C'est elle
 *       que le vide tord. Sans elle, une courbure ne se lit pas comme une courbure, elle se
 *       lit comme « des trucs qui bougent ».</li>
 *   <li><b>Ce qui entre ne ressort pas intact.</b> Toute matière qui franchit la lèvre est
 *       <i>étirée</i> le long du rayon, amincie, puis annulée. Rien ne retraverse. Quand le
 *       vide se referme, ce qu'il a avalé revient en couleur <b>inversée</b> : c'est la seule
 *       façon pour le spectateur de savoir que ça a été avalé.</li>
 * </ol>
 *
 * <h2>La palette, et pourquoi elle n'est pas grise</h2>
 *
 * <p>{@code DeepElement.VOID} vaut (0x444444, 0x000000, 0x5B5B5B) : du gris pur. Inutilisable
 * tel quel — gris sur gris est invisible, et le noir pur ne fabrique qu'un trou, leçon déjà
 * payée par l'Ombre.</p>
 *
 * <p><b>T390 — repeinte.</b> La version T388/T389 posait « corps presque noirs, horizon
 * CYAN-BLANC », par crainte de marcher sur le violet de l'Ombre (0x4D318E / 0x7100A7). Ce
 * cyan a été rejeté : il appartenait à la Glace plus qu'au Vide. Les douze constantes
 * viennent maintenant des quatre gammes retenues pour l'élément —
 * <b>Deep Abyssal Void</b> (#050D25), le <b>charbon</b> (#000000 → #8c8c8c),
 * <b>Call of the Void</b> (#0e0216, #5b175d, #6c13a4, #9518e2, #2d0043) et la série
 * <b>bleu/mauve délavée</b> (#282077 → #E2BFAE).</p>
 *
 * <p>La règle de la repeinte : <b>la luminance de chaque rôle est conservée, seule la teinte
 * change</b>. Tout le fichier repose sur une échelle qui monte des corps presque noirs (8)
 * jusqu'au fil de l'anneau (255) ; intervertir deux barreaux rendrait les cinq signatures
 * illisibles, aussi justes soient les couleurs prises une à une. L'échelle actuelle :
 * 8 &lt; 13 &lt; 21 &lt; 44 &lt; 88 &lt; 202 &lt; 234 &lt; 255.</p>
 *
 * <p>Une exception a cette regle, et elle est volontaire : <b>la levre</b>. La conserver a
 * luminance egale (189) imposait un ton tres clair, et aucune des gammes n'offre de couleur
 * chromatique a ce niveau — on retombait sur un blanc sale, lisible mais sans identite,
 * c'est-a-dire sur une repeinte pour rien. La levre prend donc le <b>violet neon #9518E2</b>
 * et descend a 84. Ce qui porte une figure, ce n'est pas l'ecart levre/fond mais l'ecart
 * <i>levre/corps</i> : 84 contre 21, soit quatre fois, contre cinq fois auparavant. Les deux
 * crans au-dessus (surexposition #D7B4F7, fil de l'anneau blanc) gardent leur luminance et
 * ramenent la lumiere exactement la ou il en faut.</p>
 *
 * <p>Deuxieme invariant : les deux tons de matiere etrangere (#D8D2A4 / #8A855F) ne changent
 * pas. Ils representent le monde avale, pas le vide ; les repeindre aux couleurs du vide
 * supprimerait le contraste qui fait lire l'engloutissement. Et puisque le violet est
 * desormais la teinte de la maison, l'AUTRE COTE apercu dans la fente passe au bleu ardoise
 * #4B5295 : c'est lui, maintenant, qui doit detonner.</p>
 *
 * <p>Le fond d'abysse en parallaxe qui s'ouvre au cœur des cinq figures n'est pas peint ici :
 * c'est un core shader, voir {@link VoidPortalFx} et {@link ModShaders}.</p>
 *
 * <h2>Les trois signatures</h2>
 *
 * <ul>
 *   <li><b>Sphère de Distorsion</b> (160 t) — une <i>LENTILLE</i>. Pas une boule : ce qu'on
 *       voit, c'est le monde plié autour. Une grille de 7 × 7 tiges droites se courbe en
 *       {@code r − k/r}, et les nœuds passés sous l'horizon <b>disparaissent</b> : la sphère
 *       est dessinée par les nœuds qui manquent.</li>
 *   <li><b>Prisme du Dragon</b> (190 t) — une <i>RÉFRACTION</i>. Un octaèdre en fil de fer
 *       reçoit un trait plein et le ressort en sept faisceaux <b>creux</b>, qu'on traverse du
 *       regard. À l'apogée les sept se rabattent en deux ailes membranées.</li>
 *   <li><b>Faille du Néant</b> (170 t) — une <i>DÉCHIRURE</i>. Un objet à deux dimensions
 *       dans un monde qui en a trois. Deux lèvres dentelées et <b>décalées</b> — du papier
 *       déchiré, pas coupé — qui ne se recollent jamais tout à fait.</li>
 * </ul>
 *
 * <p>Repère commun aux signatures du mod : {@code (dx, dz)} pointe de l'ATTAQUANT vers la
 * CIBLE ; {@code f > 0} est au-delà de la victime, {@code f < 0} du côté du porteur. Le
 * prisme s'en sert pour de bon — son trait d'entrée vient de la main.</p>
 */
public final class VoidSignatureVfx {

    private VoidSignatureVfx() {}

    // =================================================================================
    //  VARIANTES
    // =================================================================================

    public static final int WARP = 0;
    public static final int PRISM = 1;
    public static final int RIFT = 2;
    /** T389 : la quatrième. Un TORE — la seule surface fermée à trou de la famille. */
    public static final int GATE = 3;
    /** T389 : la cinquième. Un GRAPHE — le seul objet NON CENTRÉ de la famille. */
    public static final int CHAIN = 4;

    // =================================================================================
    //  PALETTE
    // =================================================================================

    /** L'intérieur. Employé UNIQUEMENT en lèvre intérieure, jamais en aplat de remplissage. */
    private static final int NOTHING   = 0x0E0216;
    /** Un cran au-dessus du rien : l'ombre portée d'une lèvre. */
    private static final int ABYSS     = 0x050D25;
    /** Corps des masses. La prune de « Call of the Void » : un noir qui a une teinte, donc qui tient. */
    private static final int BASALT    = 0x2D0043;
    /** Face éclairée d'un corps, arêtes mortes de la grille. */
    private static final int SLATE     = 0x282077;
    /** Le violet moyen : la transition entre le corps presque noir et la lèvre. */
    private static final int HALO      = 0x6C13A4;
    /** <b>Teinte maîtresse.</b> La lèvre : le violet néon de « Call of the Void ». Elle dit « vide ». */
    private static final int HORIZON   = 0x9518E2;
    /** Surexposition de la lèvre (#9518E2 éclairci) : lèvres vives, nœuds encore intacts. */
    private static final int GLACIER   = 0xD7B4F7;
    /** Le fil de l'anneau. Jamais en aplat, jamais sur plus d'un cube d'épaisseur. */
    private static final int SINGULAR  = 0xFFFFFF;

    /** Matière ÉTRANGÈRE n°1 — ce que le vide avale : le sol du monde. */
    private static final int ENDSTONE  = 0xD8D2A4;
    /** Sa face d'ombre. Sans elle, un fragment avalé est une tache jaune. */
    private static final int ENDSTONE_DK = 0x8A855F;
    /** Matière ÉTRANGÈRE n°2 — le seul ton FROID du fichier : l'AUTRE CÔTÉ, aperçu dans la fente. */
    private static final int CHORUS    = 0x4B5295;
    /** Ce qui RESSORT du vide : le rose cendré, seul ton chaud face à une lèvre froide. Restitution seule. */
    private static final int INVERT    = 0xB18E8C;

    private static final int SOLID_FLOOR = 208;

    // =================================================================================
    //  CONSTANTES DE SCÈNE
    // =================================================================================

    /**
     * Hauteur du sol dans le repère local, reçue à chaque image (voir
     * {@code probeGroundOffset}).
     *
     * <p><b>La valeur de repli n'est pas arbitraire.</b> L'origine d'une signature est le
     * TORSE de la victime — {@code target.getY() + bbHeight * 0,50} côté serveur. Pour un
     * corps d'un mètre quatre-vingts, le sol est donc à <b>0,90 sous l'origine</b>, et c'est
     * exactement ce repli. Toute figure qui touche le sol doit partir d'ici et de nulle part
     * ailleurs : un plancher dessiné plus bas que le plancher réel fait flotter la victime
     * au-dessus de son propre effet.</p>
     */
    private static float GROUND_Y = -0.90f;

    /** Épaisseur d'une dalle posée au sol. */
    private static final float SLAB_H = 0.030f;

    /** Pesanteur locale, pour les seuls fragments rendus par le vide (rien d'autre ne tombe ici). */
    private static final float GRAVITY = 9.0f;

    private static final float TAU = 6.2831855f;

    // =================================================================================
    //  LES TROIS COTES PARTAGÉES AVEC LA FENÊTRE D'ABYSSE
    // =================================================================================
    //
    // T390b — {@link VoidPortalFx} doit poser sa fenêtre EXACTEMENT dans l'ouverture que
    // la figure encadre. Un disque qui déborde de la lèvre cesse d'être un trou : il
    // redevient un objet, avec sa silhouette circulaire à lui, et c'est précisément la
    // règle « on ne dessine jamais le vide, on dessine son bord » qu'il viole.
    //
    // Or le rayon de l'horizon, l'écartement de la faille et le rayon de la porte sont
    // calculés au milieu de trois méthodes de rendu de plusieurs centaines de lignes. Les
    // recopier dans la fenêtre, c'était s'engager à les maintenir à deux endroits — la
    // palette du Vide a déjà appris ce que ça coûte. Ils sont donc extraits ici, publics,
    // et les trois renderers les appellent comme n'importe qui d'autre.

    /** Hauteur du centre de la Sphère de Distorsion au-dessus de l'origine de l'effet. */
    public static final float WARP_YC = 0.46f;

    /** Hauteur totale de la Faille, du bas des lèvres au sommet. */
    public static final float RIFT_HEIGHT = 3.05f;

    /** Demi-écartement maximal des deux lèvres, atteint à mi-hauteur. */
    public static final float RIFT_LIP = 0.82f;

    /** Rayon de l'horizon de la sphère : ouvre en 0,24, tient, puis tombe en 0,10. */
    public static float warpHorizon(float t) {
        float open = smooth(clamp((t - 0.10f) / 0.24f));
        float fall = smooth(clamp((t - 0.70f) / 0.10f));
        return (0.05f + 1.10f * open) * (1.0f - fall);
    }

    /** Écartement de la faille, 0 → 1 → 0. Asymétrique : s'ouvre vite, se recoud mal. */
    public static float riftGape(float t) {
        float opening = smooth(clamp((t - 0.06f) / 0.17f));
        float closing = smooth(clamp((t - 0.64f) / 0.18f));
        return opening * (1.0f - closing);
    }

    /** Rayon de l'anneau de la Porte : de 0,34 à 2,12, puis retour à zéro. */
    public static float gateRing(float t) {
        float grow = smooth(clamp((t - 0.04f) / 0.26f));
        float shut = smooth(clamp((t - 0.86f) / 0.14f));
        return (0.34f + 1.78f * grow) * (1.0f - shut);
    }

    /** Le sol tel que le rendu l'emploie. Mêmes bornes que {@link #render}. */
    public static float groundClamp(float groundOffset) {
        return Mth.clamp(groundOffset, -4.0f, 0.10f);
    }

    /** Sol de repli, publié pour que l'aperçu pose SON plancher au même endroit. */
    public static float groundFallback() { return -0.90f; }

    // ---------------------------------------------------------------------------------
    //  PRISME — le cristal, son régime et sa tranche équatoriale
    // ---------------------------------------------------------------------------------

    /** Hauteur du centre du cristal au-dessus de l'origine de l'effet. */
    public static final float PRISM_YC = 1.12f;

    /** Demi-diagonale de l'octaèdre. Les six sommets sont à {@code ±PRISM_W} sur un axe. */
    public static final float PRISM_W = 1.15f;

    /** Inclinaison de l'axe de rotation. Un cristal d'aplomb a l'air posé, pas lâché. */
    public static final float PRISM_TILT = 0.34f;

    /** Instant du premier des sept faisceaux : tick 62 sur 190, exactement comme au serveur. */
    public static final float PRISM_FIRE0 = 62.0f / 190.0f;

    /** Écart entre deux faisceaux : neuf ticks, exactement comme au serveur. */
    public static final float PRISM_FIRE_STEP = 9.0f / 190.0f;

    /** Instant de la fracture : tick 150 sur 190. */
    public static final float PRISM_BREAK = 150.0f / 190.0f;

    /** Instant de l'éclat : tick 170 sur 190. */
    public static final float PRISM_BURST = 170.0f / 190.0f;

    /**
     * Angle du cristal. Il monte en régime, tient, et la fracture le casse — on ne voit pas
     * une toupie s'arrêter, on voit une toupie dont les morceaux partent.
     */
    public static float prismSpin(float t) {
        float spinUp = smooth(clamp((t - 0.07f) / 0.14f));
        float broken = clamp((t - PRISM_BREAK) / 0.08f);
        // 2,4 + 9,0 faisait près de deux tours par seconde : l'œil n'a pas le temps de
        // reconstruire l'octaèdre et ne voit qu'une cage qui gigote. C'est ce que
        // l'utilisateur a lu comme « il se déforme ». Un objet ne se reconnaît qu'à une
        // vitesse où sa silhouette tient en place.
        return t * (2.0f + 4.2f * spinUp) * (1.0f - broken * 0.85f);
    }

    /** Présence du corps : nulle avant qu'il se pose, pleine en régime, nulle à la fracture. */
    public static float prismBody(float t) {
        float in = smooth(clamp(t / 0.085f));
        float out = smooth(clamp((t - PRISM_BREAK) / 0.045f));
        return in * (1.0f - out) * (1.0f + 0.055f * Mth.sin(t * 21.0f));
    }

    /**
     * Le plan de la tranche équatoriale et son rayon utile, pour la fenêtre d'abysse.
     *
     * <p>L'équateur d'un octaèdre qui tourne autour d'un axe incliné n'est pas horizontal :
     * la fenêtre doit donc recevoir ses DEUX vecteurs de base, pas un mode. Sortie :
     * {@code {af, al, ay, bf, bl, by, rayon}} dans le repère (f = axe de l'attaque,
     * latéral, hauteur).</p>
     */
    public static void prismSlice(float t, float[] out) {
        float spin = prismSpin(t);
        float ct = Mth.cos(PRISM_TILT);
        float st = Mth.sin(PRISM_TILT);
        float cs = Mth.cos(spin);
        float sn = Mth.sin(spin);
        out[0] = cs;         out[1] = sn;        out[2] = 0.0f;
        out[3] = -sn * ct;   out[4] = cs * ct;   out[5] = st;
        // Le carré équatorial a une demi-diagonale de w ; son cercle inscrit vaut w/√2.
        // On reste à 0,62 w : la fenêtre ne touche jamais les quatre arêtes de l'équateur.
        out[6] = PRISM_W * prismBody(t) * 0.84f;
    }

    // ---------------------------------------------------------------------------------
    //  FAILLE — la déchirure est une LIGNE, et sa longueur se calcule
    // ---------------------------------------------------------------------------------

    /**
     * Demi-longueur de la déchirure à pleine propagation. Le corridor du serveur fait neuf
     * blocs de long : la fissure dessinée en fait 8,6, soit le corridor moins ses bouts.
     */
    public static final float RIFT_REACH = 4.30f;

    /** Instant de la fermeture éclair : tick 140 sur 170. */
    public static final float RIFT_CLOSE = 140.0f / 170.0f;

    /** Propagation de la fissure, 0 → 1. Une fêlure court, elle n'apparaît pas d'un bloc. */
    public static float riftRun(float t) {
        return smooth(clamp((t - 0.012f) / 0.085f));
    }

    /** Fermeture par les pointes : 1 tant que la faille tient, 0 une fois recousue. */
    public static float riftZip(float t) {
        return 1.0f - smooth(clamp((t - RIFT_CLOSE) / 0.11f));
    }

    /** Demi-longueur visible : elle croît en propageant, puis se raccourcit en se refermant. */
    public static float riftHalfLength(float t) {
        return RIFT_REACH * riftRun(t) * riftZip(t);
    }

    /** Hauteur de la déchirure au point de nucléation (elle s'annule aux deux pointes). */
    public static float riftCrown(float t) {
        return RIFT_HEIGHT * riftRun(t);
    }

    /**
     * Profil d'ouverture d'une fissure de Griffith : maximal au point de nucléation, nul aux
     * pointes, en racine. Une fente d'épaisseur constante n'est pas une fissure, c'est une
     * fente découpée aux ciseaux.
     */
    public static float riftProfile(float u) {
        float v = 1.0f - u * u;
        return v <= 0.0f ? 0.0f : Mth.sqrt(v);
    }

    // ---------------------------------------------------------------------------------
    //  LE PIÈGE À LOUP — ouverture, armement, claquement
    // ---------------------------------------------------------------------------------
    //
    // Ce qui manquait à la Faille n'était pas de l'intensité, c'était un MÉCANISME. Deux
    // lèvres qui s'écartent ne racontent rien : on ne voit jamais ce qui serait pris. Un
    // piège à loup, si — et sa violence tient tout entière au contraste entre la durée de
    // l'attente et la brièveté du claquement. Cent dix ticks béant, cinq pour mordre.

    /** Demi-écartement des deux charnières, au sol. */
    public static final float RIFT_HINGE = 0.52f;

    /** Longueur d'une mâchoire, de sa charnière à la pointe de ses dents. */
    public static final float RIFT_JAW = 2.05f;

    /** Nombre de côtes — donc de dents — par mâchoire. */
    public static final int RIFT_TEETH = 9;

    /** Ouverture maximale, mesurée depuis la verticale, vers l'extérieur. */
    public static final float RIFT_OPEN = 1.08f;

    /** Courbure de la mâchoire : elle se recourbe vers l'intérieur en fin d'arc. */
    public static final float RIFT_CURVE = 0.52f;

    /** Durée du claquement : sept ticks sur cent soixante-dix, soit quatre pour cent. */
    public static final float RIFT_SNAP = 0.042f;

    /** Nombre d'échos de vitesse laissés par le bord denté pendant le claquement. */
    public static final int RIFT_GHOSTS = 9;

    /** Instant où le piège finit de s'ouvrir. */
    public static final float RIFT_ARMED = 0.30f;

    /**
     * Angle de fermeture : celui pour lequel les deux pointes se rejoignent exactement sur
     * l'axe. Il se CALCULE — l'écrire à la main ferait rater le contact d'un cheveu, et un
     * piège à loup qui ne ferme pas n'est pas un piège.
     *
     * <p>La mâchoire étant courbée de {@link #RIFT_CURVE}, le déport horizontal de sa
     * pointe vaut {@code ∫ sin(ang − RIFT_CURVE·h²) dh} ; on cherche l'angle qui l'amène à
     * {@code −RIFT_HINGE / RIFT_JAW}. Dix pas de dichotomie suffisent, et le résultat est
     * une constante : le calcul ne se refait pas par frame.</p>
     */
    public static final float RIFT_SHUT = riftSolveShut();

    private static float riftSolveShut() {
        float target = -RIFT_HINGE / RIFT_JAW;
        float lo = -1.4f;
        float hi = 0.0f;
        for (int it = 0; it < 24; it++) {
            float mid = (lo + hi) * 0.5f;
            if (riftReachOf(mid) > target) hi = mid; else lo = mid;
        }
        return (lo + hi) * 0.5f;
    }

    /** Déport horizontal de la pointe, en fraction de {@link #RIFT_JAW}, pour un angle donné. */
    private static float riftReachOf(float ang) {
        float acc = 0.0f;
        int n = 16;
        for (int i = 0; i < n; i++) {
            float h = (i + 0.5f) / n;
            acc += Mth.sin(ang - RIFT_CURVE * h * h);
        }
        return acc / n;
    }

    /**
     * Angle des mâchoires au temps {@code t}. C'est toute la chorégraphie du piège.
     *
     * <ul>
     *   <li>avant le tick 2, fermé — on ne voit rien venir ;</li>
     *   <li>jusqu'au tick 51, l'ouverture, en {@code 1 − (1−u)³} : vif puis lent, comme un
     *       ressort qu'on bande ;</li>
     *   <li>jusqu'au tick 140, ARMÉ. Il tremble, et le tremblement s'accélère à l'approche
     *       — c'est le seul avertissement que reçoit la victime ;</li>
     *   <li><b>tick 140, le claquement : cinq ticks</b>, en {@code u³}. Une interpolation
     *       linéaire ferait une fermeture de garage.</li>
     * </ul>
     */
    public static float riftJawAngle(float t) {
        if (t <= 0.012f) return RIFT_SHUT;
        if (t < RIFT_ARMED) {
            float u = clamp((t - 0.012f) / (RIFT_ARMED - 0.012f));
            float ease = 1.0f - (1.0f - u) * (1.0f - u) * (1.0f - u);
            return RIFT_SHUT + (RIFT_OPEN - RIFT_SHUT) * ease;
        }
        if (t < RIFT_CLOSE) {
            // Armé. Le frisson était à 1,5° puis 5° : sur quatre-vingt-dix ticks, ça ne
            // se lit pas comme une bête qui guette, ça se lit comme une image qui saute.
            // Divisé par trois. Une attente doit être IMMOBILE pour être tendue.
            float dread = clamp((t - RIFT_ARMED) / (RIFT_CLOSE - RIFT_ARMED));
            float tremor = (0.008f + 0.020f * dread * dread)
                    * Mth.sin(t * (31.0f + 44.0f * dread));
            // LA MORSURE MANQUÉE. TROIS, pas neuf : neuf à-coups sur la durée de
            // l'attente brouillaient le vrai claquement, et c'est précisément pour ça que
            // l'effet de mâchoire manquait. Trois, mais profondes — 62 % de la course —
            // aux aspirations 2, 5 et 8. On les compte, et on les redoute.
            float miss = 0.0f;
            for (int k = 1; k < 9; k += 3) {
                float age = t - (20.0f + k * 10.0f) / 170.0f;
                if (age < 0.0f) break;
                if (age > 0.072f) continue;
                float u = age / 0.072f;
                // Vif à l'aller, mou au retour : un ressort qui se détend, pas un métronome.
                float bite = u < 0.28f ? (u / 0.28f) * (u / 0.28f)
                                       : 1.0f - smooth((u - 0.28f) / 0.72f);
                if (bite > miss) miss = bite;
            }
            // ET LE PIÈGE SE TEND. L'attente dure quatre secondes et demie ; sans une
            // dérive lente et monotone elle n'a pas de direction, et une attente sans
            // direction paraît deux fois plus longue qu'elle n'est. La gueule se
            // referme donc de 16 % de sa course pendant qu'elle guette : on ne le voit
            // pas bouger, on voit que ça s'est resserré.
            float tighten = (RIFT_OPEN - RIFT_SHUT) * 0.16f * smooth(dread);
            return RIFT_OPEN - tighten + tremor - (RIFT_OPEN - RIFT_SHUT) * 0.62f * miss;
        }
        float u = clamp((t - RIFT_CLOSE) / RIFT_SNAP);
        // u^2,2 : la fermeture traîne un peu puis avale tout. En u³ elle tombait ENTRE
        // deux images — mesuré au banc : à mi-course les mâchoires n'avaient bougé que
        // de onze pour cent, et le claquement n'existait pas à l'écran.
        float snap = u * u * (float) Math.pow(u, 0.2);
        // LE CHOC D'ACIER. Un piège à loup ne s'arrête pas doucement : les deux mâchoires
        // se percutent, rebondissent de quelques degrés et se recollent en vibrant. Trois
        // oscillations amorties, et c'est tout le « claquement » — sans elles la
        // fermeture est une porte de garage qui arrive en butée.
        float post = clamp((t - RIFT_CLOSE - RIFT_SNAP) / 0.075f);
        float ring = Mth.sin(post * Mth.PI * 6.0f) * 0.26f * (1.0f - post) * (1.0f - post);
        return RIFT_OPEN + (RIFT_SHUT - RIFT_OPEN) * snap + ring;
    }

    /** Vitesse angulaire des mâchoires, par unité de {@code t}. Elle pilote les traînées. */
    public static float riftJawSpeed(float t) {
        return Math.abs(riftJawAngle(t + 0.004f) - riftJawAngle(t - 0.004f)) / 0.008f;
    }

    /**
     * Un point de la mâchoire. {@code h} court de 0 à la charnière jusqu'à 1 à la pointe.
     *
     * <p>L'arc est intégré pas à pas plutôt que posé en ligne droite : une mâchoire droite
     * est une planche, et c'est la courbure qui fait qu'on lit un piège et non un pont
     * levant. Elle se recourbe vers l'intérieur, donc elle CUEILLE.</p>
     *
     * @param out {f, y} dans le repère de l'attaque, hors décalage de charnière
     */
    public static void riftJawPoint(float h, float ang, float reach, float[] out) {
        int n = Math.max(2, Mth.ceil(h * 14.0f));
        float f = 0.0f;
        float y = 0.0f;
        for (int i = 0; i < n; i++) {
            float hh = h * (i + 0.5f) / n;
            float local = ang - RIFT_CURVE * hh * hh;
            f += Mth.sin(local);
            y += Mth.cos(local);
        }
        out[0] = f / n * h * reach;
        out[1] = y / n * h * reach;
    }

    // ---------------------------------------------------------------------------------
    //  TRAME — l'anneau de nœuds, sa contraction et la cellule centrale
    // ---------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------
    //  CHAÎNE — les sept stations, et leurs horaires
    // ---------------------------------------------------------------------------------
    //
    // Toute cette section est la transcription d'un seul bout de serveur :
    //
    //     int delay = Math.max(14, 40 - depth * 5);
    //     chainLink(..., depth + 1, currentTick + delay, ...);
    //
    // soit, en partant d'un premier tir au tick 30 : 30, 70, 105, 135, 160, 180, 195. Sept
    // maillons tiennent dans les deux cents ticks, le huitième tomberait à 209 et n'est
    // donc jamais dessiné. Les écarts se resserrent — c'est TOUTE la lecture de la
    // signature, et c'est pour ça que les horaires ne sont pas lissés en une formule : on
    // veut pouvoir poser le doigt sur le tick.

    /** Nombre de stations dessinées. Le huitième maillon du serveur tombe après la fin. */
    public static final int CHAIN_LINKS = 7;

    /** Instant d'implosion de la station {@code i}, en fraction de la durée (200 ticks). */
    public static float chainFire(int i) {
        int tick = 30;
        for (int k = 0; k < i; k++) tick += Math.max(14, 40 - k * 5);
        return tick / 200.0f;
    }

    /** Durée du saut d'une station à la suivante : huit ticks, le fil court vite. */
    public static final float CHAIN_TRAVEL = 8.0f / 200.0f;

    /** Instant où le fil atteint la station {@code i} et arme son compte à rebours. */
    public static float chainArrive(int i) {
        return i <= 0 ? 0.0f : chainFire(i - 1) + CHAIN_TRAVEL;
    }

    /**
     * Position de la station {@code i}, dans le plan (avant, latéral).
     *
     * <p><b>Phyllotaxie.</b> L'angle avance de 2,39996 rad — l'angle d'or — et le rayon
     * suit √i. C'est la disposition des graines d'un tournesol, et elle a ici une propriété
     * qu'aucune marche aléatoire ne donne : <b>deux stations ne se superposent jamais</b>,
     * quel que soit le hachage. Une chaîne qui repasserait sur elle-même serait illisible,
     * et le serveur ne le fait pas non plus (les corps déjà consommés sont écartés).</p>
     *
     * <p>Le bruit ajouté reste borné à ±0,4 rad sur l'angle et ±15 % sur le rayon : assez
     * pour que deux jets ne donnent pas la même figure, trop peu pour casser la propriété.</p>
     */
    public static void chainStation(int i, int salt, float[] out) {
        float ang = i * 2.39996f + (hash(salt, i * 17 + 3) - 0.5f) * 0.80f;
        float rad = 1.55f * Mth.sqrt(i) * (0.85f + 0.30f * hash(salt, i * 29 + 7));
        out[0] = Mth.cos(ang) * rad;
        out[1] = Mth.sin(ang) * rad;
        out[2] = CHAIN_YC + (hash(salt, i * 41 + 11) - 0.5f) * 0.30f;
    }

    /**
     * Rayon de l'implosion de la station {@code i}. C'est l'escalade du serveur, rendue
     * visible : {@code blastR = 1,4 + depth x 0,25}, exactement.
     */
    public static float chainBlast(int i) {
        return 1.40f + i * 0.25f;
    }

    /** Hauteur du centre d'une station : le torse d'un corps. */
    public static final float CHAIN_YC = 0.28f;

    /**
     * Rayon de l'anneau de compte à rebours d'une station, ou 0 si elle n'est pas armée.
     *
     * <p>C'est la seule fenêtre de la signature, et c'est une fenêtre à CUBES : l'anneau
     * est fait de petits cubes d'abysse qui tournent et se referment. Quand le rayon atteint
     * zéro, le corps implose. Le joueur peut donc <b>compter le temps qui lui reste</b> en
     * regardant, ce qu'aucune autre signature du mod ne permet.</p>
     */
    public static float chainCell(float t) {
        for (int i = CHAIN_LINKS - 1; i >= 0; i--) {
            float a = chainArrive(i);
            float f = chainFire(i);
            if (t < a || t >= f) continue;
            float w = clamp((t - a) / Math.max(1.0e-4f, f - a));
            return (1.25f + i * 0.10f) * (1.0f - smooth(w)) + 0.12f;
        }
        return 0.0f;
    }

    // ---------------------------------------------------------------------------------
    //  PORTE — l'anneau, et les sept transits
    // ---------------------------------------------------------------------------------

    /** Premier transit : tick 34 sur 180. */
    public static final float GATE_PASS0 = 34.0f / 180.0f;

    /** Écart entre deux transits : dix-huit ticks. */
    public static final float GATE_PASS_STEP = 18.0f / 180.0f;

    /** Fermeture : tick 150 sur 180. */
    public static final float GATE_SHUT = 150.0f / 180.0f;

    // =================================================================================
    //  ENTRÉE
    // =================================================================================

    /**
     * @param dx,dz        direction unitaire ATTAQUANT → CIBLE
     * @param groundOffset écart entre la surface réelle et le centre de l'effet
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt,
                              float groundOffset) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        GROUND_Y = groundClamp(groundOffset);
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }

        switch (variant) {
            case PRISM -> renderPrism(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            case RIFT -> renderRift(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            case GATE -> renderGate(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            case CHAIN -> renderChain(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            default -> renderWarp(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        }
    }

    // =================================================================================
    //  I. SPHÈRE DE DISTORSION — 160 ticks
    // =================================================================================

    /**
     * Chronologie (160 ticks) :
     * <ul>
     *   <li>0,00–0,12 <b>l'amorce</b> : un point blanc, et autour de lui une grille de 7 × 7
     *       tiges droites, parfaitement régulière. Douze ticks d'espace NORMAL — c'est eux
     *       qui rendent les douze suivants lisibles ;</li>
     *   <li>0,12–0,34 <b>la courbure</b> : chaque nœud est tiré vers le centre en
     *       {@code r − k/r}. La loi est en inverse du rayon, donc violente au bord du trou et
     *       nulle au loin : c'est ce contraste qui se lit comme une lentille, pas comme un
     *       zoom. Les nœuds passés sous l'horizon <b>ne sont plus tracés</b> ;</li>
     *   <li>0,30–0,46 <b>l'anneau d'Einstein</b> : un fil de {@link #SINGULAR} d'un cube
     *       d'épaisseur, exactement au rayon de l'horizon. La seule lumière de la scène ;</li>
     *   <li>0,30–0,66 <b>l'accrétion</b> : des blocs d'{@link #ENDSTONE} descendent en spirale
     *       logarithmique et s'<b>étirent</b> en approchant — chaîne de cinq cubes dont
     *       l'écart croît et la section décroît en {@code (r/r₀)^1,6}. À l'horizon, section
     *       nulle : rien ne traverse ;</li>
     *   <li>0,46–0,70 <b>l'aplatissement</b> : la grille bascule en disque et tourne
     *       <i>différentiellement</i>, l'intérieur plus vite que l'extérieur ;</li>
     *   <li>0,70–0,80 <b>l'effondrement</b> : l'horizon tombe à zéro en huit ticks, la grille
     *       se redresse d'un coup en dépassant — la lentille n'est plus là ;</li>
     *   <li>0,80–1,00 <b>la restitution</b> : tout l'avalé ressort en cône d'{@link #INVERT},
     *       en couleur inversée, et la grille s'efface en RÉTRÉCISSANT ses tiges.</li>
     * </ul>
     */
    private static void renderWarp(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        float a = alpha;
        // --- Rayon de l'horizon. Il ouvre lentement, tient, puis tombe en huit ticks. ---
        float rs = warpHorizon(t);
        // --- Basculement de la grille : plan vertical au début, disque d'accrétion ensuite. ---
        float tip = smooth(clamp((t - 0.46f) / 0.24f));
        // --- Redressement élastique après la chute : la grille dépasse puis revient. ---
        float snap = clamp((t - 0.70f) / 0.22f);
        float rebound = snap <= 0.0f ? 0.0f
                : Mth.sin(snap * Mth.PI * 1.6f) * (1.0f - snap) * 0.55f;
        float yc = WARP_YC;

        // ---- 1. LA GRILLE TÉMOIN ------------------------------------------------------
        // Sept par sept. Le pas est constant : c'est la seule chose régulière de la scène,
        // et toute la lisibilité de la courbure en dépend.
        {
            float life = clamp(t / 0.07f) * (1.0f - clamp((t - 0.88f) / 0.12f));
            if (life > 0.01f) {
                // Neuf par neuf au pas de 0,78 : demi-largeur 3,12 contre un horizon
                // qui plafonne a 1,15. Avant (7 au pas de 0,62, demi-largeur 1,86), le
                // trou avalait presque toute la maille des son ouverture et il ne restait
                // plus rien a tordre.
                int n = 9;
                float step = 0.78f;
                float half = (n - 1) * 0.5f * step;
                // Les deux familles de tiges : le long de f, puis le long de lateral.
                for (int pass = 0; pass < 2; pass++) {
                    for (int row = 0; row < n; row++) {
                        float fixed = -half + row * step;
                        // Trente-deux échantillons par tige : en dessous, la courbure se lit
                        // en ligne brisée — on verrait le maillage au lieu de la déformation.
                        int seg = 24;
                        float prevF = 0.0f;
                        float prevL = 0.0f;
                        float prevY = 0.0f;
                        boolean prevIn = false;
                        for (int s = 0; s <= seg; s++) {
                            float u = -half + (s / (float) seg) * (half * 2.0f);
                            float f0 = pass == 0 ? u : fixed;
                            float l0 = pass == 0 ? fixed : u;
                            // --- LA LENTILLE ---
                            float r0 = Mth.sqrt(f0 * f0 + l0 * l0);
                            if (r0 < 0.0001f) continue;
                            float pull = rs * rs * 0.62f / Math.max(r0, 0.55f);
                            float r1 = r0 - pull * (1.0f + rebound);
                            boolean swallowed = r1 <= rs * 0.98f;
                            float k = r1 / r0;
                            float f1 = f0 * k;
                            float l1 = l0 * k;
                            // Bascule : le plan vertical (f, y) devient un disque (f, lateral).
                            // Le disque d'accretion n'est pas un rasoir d'epaisseur nulle :
                            // il EVASE vers l'exterieur et GONDOLE. Sans ces deux termes, une
                            // fois a plat la grille devient une feuille de papier — et une
                            // feuille vue par la tranche disparait completement.
                            float flare = (0.035f + 0.085f * clamp(r1 / 3.2f))
                                    * Mth.sin(Mth.atan2(l1, f1) * 2.0f + t * 5.0f);
                            float yFlat = yc + flare;
                            float yVert = yc + l1 * 0.72f;
                            float y1 = yVert + (yFlat - yVert) * tip;
                            // La coordonnee laterale n'existe QUE dans le disque : tant
                            // que la grille est verticale, le second axe est la hauteur.
                            float l1m = l1 * tip;
                            // Rotation différentielle du disque : l'intérieur plus vite.
                            if (tip > 0.01f) {
                                float spin = tip * (0.9f + 2.6f / Math.max(0.45f, r1)) * (t - 0.46f) * 2.3f;
                                float cs = Mth.cos(spin);
                                float sn = Mth.sin(spin);
                                float ff = f1 * cs - l1m * sn;
                                l1m = f1 * sn + l1m * cs;
                                f1 = ff;
                            }
                            if (s > 0 && prevIn && !swallowed) {
                                // Le segment : d'autant plus brillant et épais qu'il est tordu.
                                float bend = clamp(pull / 0.55f);
                                float w = (0.020f + 0.020f * bend) * life;
                                int rgb = bend > 0.62f ? GLACIER : (bend > 0.28f ? HORIZON : (bend > 0.10f ? HALO : SLATE));
                                // Le PAS suit la courbure : pointille au loin, jointif au bord.
                                float pitch = 0.040f + 0.105f * (1.0f - bend);
                                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                        prevF, prevL, prevY, f1, l1m, y1, w, pitch, rgb,
                                        opaque(a * (0.52f + 0.48f * bend) * life));
                            }
                            prevF = f1; prevL = l1m; prevY = y1; prevIn = !swallowed;
                        }
                    }
                }
                // Les NŒUDS encore intacts. Ils marquent le pas de la grille : sans eux, on
                // ne sait plus que la maille était régulière au départ.
                for (int ix = 0; ix < n; ix++) {
                    for (int iz = 0; iz < n; iz++) {
                        float f0 = -half + ix * step;
                        float l0 = -half + iz * step;
                        float r0 = Mth.sqrt(f0 * f0 + l0 * l0);
                        if (r0 < 0.0001f) continue;
                        float pull = rs * rs * 0.62f / Math.max(r0, 0.55f);
                        float r1 = r0 - pull * (1.0f + rebound);
                        if (r1 <= rs * 0.98f) continue;
                        float k = r1 / r0;
                        float f1 = f0 * k;
                        float l1 = l0 * k;
                        float flare = (0.035f + 0.085f * clamp(r1 / 3.2f))
                                * Mth.sin(Mth.atan2(l1, f1) * 2.0f + t * 5.0f);
                        float y1 = yc + l1 * 0.72f * (1.0f - tip) + flare * tip;
                        float l1m = l1 * tip;
                        if (tip > 0.01f) {
                            float spin = tip * (0.9f + 2.6f / Math.max(0.45f, r1)) * (t - 0.46f) * 2.3f;
                            float cs = Mth.cos(spin);
                            float sn = Mth.sin(spin);
                            float ff = f1 * cs - l1m * sn;
                            l1m = f1 * sn + l1m * cs;
                            f1 = ff;
                        }
                        float near = clamp((1.3f - (r1 - rs)) / 1.3f);
                        float sz = (0.034f + 0.020f * near) * life;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, f1, l1m, y1,
                                sz, sz, sz, near > 0.55f ? GLACIER : HORIZON,
                                opaque(a * (0.60f + 0.40f * near) * life));
                    }
                }
            }
        }

        // ---- 2. L'HORIZON ------------------------------------------------------------
        // TROIS GRANDS CERCLES, et rien d'autre. Le premier jet dessinait treize paralleles
        // de latitude : depuis l'exterieur on voyait la face avant ET la face arriere, et
        // les vingt-six rangees de cubes sombres se lisaient comme une BOUILLIE GRISE, pas
        // comme un trou. Exactement l'erreur que la regle n 1 interdit. Trois cercles
        // orthogonaux suffisent a dire « sphere » — c'est la convention du gyroscope — et
        // laissent l'interieur VRAIMENT vide.
        if (rs > 0.03f) {
            int pts = Math.max(18, Math.round(rs * 42.0f));
            for (int plane = 0; plane < 3; plane++) {
                for (int j = 0; j < pts; j++) {
                    float ang = (j + plane * 0.33f) / pts * TAU;
                    float ca = Mth.cos(ang) * rs;
                    float sa = Mth.sin(ang) * rs;
                    float pf;
                    float pl;
                    float py;
                    if (plane == 0) { pf = ca; pl = sa; py = 0.0f; }
                    else if (plane == 1) { pf = ca; pl = 0.0f; py = sa; }
                    else { pf = 0.0f; pl = ca; py = sa; }
                    // La lebre n'est jamais uniforme : un bord stable se lit comme une bille.
                    float flick = 0.78f + 0.22f * Mth.sin(ang * 7.0f + t * 52.0f + plane * 2.1f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, yc + py,
                            0.028f, 0.028f, 0.028f,
                            flick > 0.93f ? GLACIER : (plane == 0 ? HALO : SLATE),
                            opaque(a * 0.92f));
                }
            }
        }

        // ---- 3. L'ANNEAU D'EINSTEIN ---------------------------------------------------
        {
            float ringLife = clamp((t - 0.28f) / 0.08f) * (1.0f - clamp((t - 0.70f) / 0.08f));
            if (ringLife > 0.01f && rs > 0.08f) {
                int pts = 72;
                float rr = rs * 1.14f;
                for (int i = 0; i < pts; i++) {
                    float ang = (i + 0.5f) / pts * TAU;
                    // L'anneau est plus épais là où la grille est la plus tordue : il « mange »
                    // la lumière des tiges voisines, et c'est ce qui le relie à la scène.
                    float th = 0.016f + 0.010f * Mth.sin(ang * 3.0f + t * 9.0f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr * tip,
                            yc + Mth.sin(ang) * rr * 0.72f * (1.0f - tip),
                            th, th, th, SINGULAR, opaque(a * ringLife));
                    // La frange : un cran en dehors, plus froide, deux fois plus fine.
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr * 1.10f, Mth.sin(ang) * rr * 1.10f * tip,
                            yc + Mth.sin(ang) * rr * 1.10f * 0.72f * (1.0f - tip),
                            th * 0.62f, th * 0.62f, th * 0.62f, GLACIER, opaque(a * ringLife * 0.85f));
                }
            }
        }

        // ---- 4. L'ACCRÉTION : CE QUI TOMBE DEDANS --------------------------------------
        // Dix-huit blocs du monde, chacun sur sa propre spirale, chacun étiré en approchant.
        {
            float acc = clamp((t - 0.26f) / 0.40f);
            if (acc > 0.001f && rs > 0.05f) {
                for (int i = 0; i < 30; i++) {
                    float h1 = hash(salt + 31, i * 13);
                    float h2 = hash(salt + 32, i * 17);
                    float h3 = hash(salt + 33, i * 19);
                    float start = h1 * 0.42f;
                    float p = clamp((acc - start) / (0.52f + 0.30f * h2));
                    if (p <= 0.0f || p >= 1.0f) continue;
                    float r0 = 1.9f + 1.5f * h2;
                    // Spirale logarithmique : le rayon décroît en exponentielle, pas en
                    // ligne droite. Une chute linéaire vers un trou noir se lit comme un
                    // ascenseur, exactement comme une goutte à vitesse constante.
                    float r = rs + (r0 - rs) * (float) Math.exp(-3.1 * p);
                    float ang = h3 * TAU + p * (5.4f + 2.2f * h1);
                    float yy = yc + (h2 - 0.5f) * 1.5f * (1.0f - p) + (1.0f - tip) * 0.0f;
                    spaghetti(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ang, r, yy, rs, (0.070f + 0.032f * h1),
                            a * (1.0f - 0.25f * p), i);
                }
            }
        }

        // ---- 5. LA RESTITUTION ---------------------------------------------------------
        // Ce qui a été avalé revient, inversé. C'est la seule apparition de l'orange.
        {
            float give = clamp((t - 0.78f) / 0.22f);
            if (give > 0.001f) {
                float ease = smooth(give);
                for (int i = 0; i < 40; i++) {
                    float h1 = hash(salt + 41, i * 11);
                    float h2 = hash(salt + 42, i * 13);
                    float h3 = hash(salt + 43, i * 17);
                    // Un CÔNE le long de l'axe attaquant → cible, pas une sphère : le vide
                    // recrache dans la direction du coup, sinon la restitution n'a plus de
                    // rapport avec ce que le joueur a fait.
                    float spread = 0.42f + 0.55f * h1;
                    float d = (0.30f + 4.3f * ease) * (0.45f + 0.75f * h2);
                    float ang = h3 * TAU;
                    float ff = d + Mth.cos(ang) * d * spread * 0.30f;
                    float ll = Mth.sin(ang) * d * spread * 0.52f;
                    float yy = yc + Mth.cos(ang * 1.7f + h1 * 4.0f) * d * spread * 0.46f
                            - GRAVITY * 0.030f * ease * ease;
                    if (yy < GROUND_Y + 0.02f) continue;
                    float sz = (0.052f + 0.030f * h2) * (1.0f - ease * 0.72f);
                    if (sz <= 0.006f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                            sz, sz, sz, h1 > 0.72f ? ENDSTONE : INVERT, opaque(a * (1.0f - ease * 0.3f)));
                }
                // L'onde au sol : plate, large, et elle s'efface en s'amincissant.
                float rr = 0.4f + 4.8f * ease;
                int pts = 52;
                for (int i = 0; i < pts; i++) {
                    float ang = (i + 0.5f) / pts * TAU;
                    float wob = 1.0f + 0.09f * Mth.sin(ang * 4.0f + salt * 0.011f);
                    float th = 0.072f * (1.0f - ease * 0.80f);
                    if (th <= 0.006f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr * wob, Mth.sin(ang) * rr * wob, GROUND_Y + 0.016f,
                            th, SLAB_H, th, i % 5 == 0 ? INVERT : HORIZON, opaque(a * 0.90f));
                }
            }
        }
    }

    // =================================================================================
    //  II. PRISME DU DRAGON — 190 ticks — il DISTRIBUE
    // =================================================================================

    // ---------------------------------------------------------------------------------
    //  LE DRAGON DE L'END — modèle vanilla, à l'unité de modèle près
    // ---------------------------------------------------------------------------------
    //
    // La liste de boîtes vient de {@code net.minecraft.client.model.EnderDragonModel}
    // ({@code ModelDragon.addBox} dans les versions historiques) : mêmes origines, mêmes
    // tailles, mêmes points de pivot. Rien n'est inventé ici, et c'est le but — la version
    // précédente dessinait une anatomie de fantaisie et ne ressemblait à rien.
    //
    // <p><b>Le cou et la queue sont la même pièce.</b> Vanilla répète la boîte de
    // 10×10×10 cinq fois devant et douze fois derrière, décalées de dix unités. C'est de
    // là que vient la démarche de serpent, et ce n'est pas une chose qu'on devine.</p>
    //
    // <p><b>Repères.</b> Le modèle Minecraft a son Y vers le BAS et son nez vers les z
    // NÉGATIFS. La conversion vers le repère de cette classe (f = axe de l'attaque,
    // lateral, y vers le haut) est donc, pour une boîte de centre (mx, my, mz) :
    // avant = −mz, haut = −my, côté = mx.</p>
    //
    // <p><b>Boîtes orientées.</b> Un modèle articulé ne se dessine pas avec des cubes
    // alignés sur les axes. Le manager sait poser des boîtes tournées depuis toujours
    // ({@code emitVoxelBoxRotated}) et cette classe ne s'en était jamais servie. Le dragon
    // entier coûte désormais une soixantaine de cubes — contre près de huit cents pour le
    // bricolage qu'il remplace.</p>

    /** Blocs par unité de modèle. Le dragon complet mesure un peu moins de 4 blocs. */
    private static final float DRAGON_U = 0.015f;

    /** Pas entre deux segments de cou ou de queue, en unités de modèle. */
    private static final float DRAGON_SEG = 10.0f;

    /**
     * Compose roulis → tangage → lacet exactement comme {@code emitVoxelBoxRotated}, et
     * rend l'offset dans le repère de l'attaque.
     *
     * <p>Axes locaux : <b>x = l'avant</b> (le nez), <b>y = le haut</b>, <b>z = le côté</b>.
     * Avec cette convention le paramètre {@code roll} pique du nez, le paramètre
     * {@code pitch} donne le ROULIS (il tourne autour de l'axe du nez) et {@code yaw}
     * donne le cap. L'inversion n'est pas une coquille : elle vient de l'ordre dans lequel
     * le manager compose ses trois matrices, et il vaut mieux la nommer que la subir.</p>
     *
     * @param out {avant, haut, côté}
     */
    private static void dragonRot(float x, float y, float z,
                                  float nose, float bank, float yaw, float[] out) {
        float cr = Mth.cos(nose), sr = Mth.sin(nose);
        float cp = Mth.cos(bank), sp = Mth.sin(bank);
        float cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        float x1 = x * cr - y * sr;
        float y1 = x * sr + y * cr;
        float y2 = y1 * cp - z * sp;
        float z2 = y1 * sp + z * cp;
        out[0] = x1 * cy + z2 * sy;
        out[1] = y2;
        out[2] = -x1 * sy + z2 * cy;
    }

    /**
     * Une boîte du modèle vanilla, posée dans le monde.
     *
     * @param bf,bl,by le pivot de la pièce, dans le repère de l'attaque
     * @param mx,my,mz le CENTRE de la boîte en unités de modèle, repère Minecraft
     * @param sx,sy,sz la TAILLE de la boîte en unités de modèle
     */
    private static void dragonBox(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float bf, float bl, float by,
                                  float nose, float bank, float yaw,
                                  float mx, float my, float mz,
                                  float sx, float sy, float sz,
                                  int rgb, int alpha, float[] tmpOff) {
        dragonRot(-mz * DRAGON_U, -my * DRAGON_U, mx * DRAGON_U, nose, bank, yaw, tmpOff);
        float hf = sz * 0.5f * DRAGON_U;
        float hy = sy * 0.5f * DRAGON_U;
        float hl = sx * 0.5f * DRAGON_U;
        if (hf < 0.004f) hf = 0.004f;
        if (hy < 0.004f) hy = 0.004f;
        if (hl < 0.004f) hl = 0.004f;
        float f = bf + tmpOff[0];
        float lateral = bl + tmpOff[2];
        float worldX = cx + dx * f - dz * lateral;
        float worldZ = cz + dz * f + dx * lateral;
        // Le lacet du repère de l'attaque s'ajoute à celui de la pièce : la boîte doit
        // tourner avec la figure, pas seulement avec le dragon.
        float frameYaw = (float) Math.atan2(-dz, dx);
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp,
                worldX, cy + by + tmpOff[1], worldZ,
                hf, hy, hl, frameYaw + yaw, bank, nose, rgb, alpha);
    }

    /**
     * Une station de l'échine, à {@code s} blocs derrière le museau.
     *
     * <p>Le dragon décrit un cercle ; l'échine suit donc l'arc, et le cap de chaque
     * segment est la TANGENTE à l'arc en ce point. C'est ce qui remplace le « retard
     * angulaire » bricolé de la version précédente : il n'y a plus rien à régler, la
     * géométrie s'en charge.</p>
     *
     * @param out {avant, côté, haut, cap}
     */
    private static void dragonSpine(float s, float orbit, float radius, float yBase,
                                    float t, float[] out) {
        float a = orbit - s / Math.max(0.8f, radius);
        float ca = Mth.cos(a);
        float sa = Mth.sin(a);
        out[0] = ca * radius;
        out[1] = sa * radius;
        // L'ondulation : elle court le long de la bête, donc elle dépend de s ET du temps.
        // Elle est nulle au museau — une tête qui gigote fait un jouet, pas un dragon.
        float amp = 0.055f * Mth.clamp(s * 0.55f, 0.0f, 1.0f);
        out[2] = yBase + Mth.sin(s * 1.55f - t * 5.6f) * amp;
        out[3] = (float) Math.atan2(-ca, -sa);
    }

    /** Le bout du museau, pour accrocher le souffle. {@code out} = {avant, côté, haut}. */
    private static void dragonMuzzle(float orbit, float radius, float yBase, float t,
                                     float[] out) {
        float[] sp = new float[4];
        dragonSpine(0.0f, orbit, radius, yBase, t, sp);
        float[] off = new float[3];
        dragonRot(24.0f * DRAGON_U, 2.0f * DRAGON_U, 0.0f, 0.0f, 0.0f, sp[3], off);
        out[0] = sp[0] + off[0];
        out[1] = sp[1] + off[2];
        out[2] = sp[2] + off[1];
    }

    /**
     * Une demi-voilure : le panneau {@code skin} de 56 × 56 unités du modèle vanilla.
     *
     * <p>Vanilla le peint d'une texture ; ici il n'y a pas de texture, seulement des
     * cubes. Un panneau plein de deux blocs de côté se lirait comme un drap, et le damier
     * essayé d'abord se lisait comme un damier. La membrane est donc une CAGE :
     * <b>cinq doigts</b> perpendiculaires à l'os, un <b>bord de fuite</b> qui les relie, et
     * une toile ajourée entre eux. C'est l'anatomie d'une aile — et c'est ce qui la fait
     * lire comme telle même de loin.</p>
     *
     * @param span fraction de l'envergure (le bout d'aile est un peu plus étroit)
     */
    private static void dragonWing(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float dx, float dz,
                                   float pf, float pl, float py,
                                   float bank, float yaw, float side, float span,
                                   float taper0, int al, int alSoft, float[] off) {
        float chord = 56.0f * span;                      // profondeur maximale
        for (int d = 0; d < 5; d++) {
            // d = 0 est le BOUT de l'aile, d = 4 l'épaule : la voilure s'effile vers
            // l'extérieur. C'est cette oblique qui fait lire « aile » plutôt que
            // « échelle » — un carré de 56 sur 56 sans texture reste un carré.
            float mx = (-56.0f + (d + 0.5f) * 56.0f / 5.0f) * side;
            float ch = chord * (taper0 + (1.0f - taper0) * (d + 0.5f) / 5.0f);
            // Le doigt : il court de l'os jusqu'au bord de fuite.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py,
                    0.0f, bank, yaw, mx, 0.0f, 2.0f + ch * 0.5f,
                    1.6f, 1.6f, ch, (d == 4) ? HORIZON : SLATE, al, off);
            // La toile entre deux doigts : plus fine qu'eux, donc un liseré naturel.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py,
                    0.0f, bank, yaw, mx, 0.0f, 2.0f + ch * 0.5f,
                    56.0f / 5.0f * 0.88f, 0.8f, ch * 0.88f, BASALT, alSoft, off);
            // La marche du bord de fuite. Cinq marches ferment l'oblique ; sans elles,
            // l'aile s'effiloche en cinq tiges.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py,
                    0.0f, bank, yaw, mx, 0.0f, 2.0f + ch,
                    56.0f / 5.0f, 2.2f, 2.2f, HALO, al, off);
        }
        // Le doigt TERMINAL : il prolonge l'os au-dela de la derniere cote et ferme la
        // pointe. C'est lui qu'on cherche du regard pour savoir ou l'aile s'arrete, et
        // son absence est exactement ce que montraient les captures.
        if (taper0 < 0.20f) {
            for (int k = 1; k <= 3; k++) {
                float h = k / 3.0f;
                dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py,
                        0.0f, bank, yaw, (-56.0f - h * 9.0f) * side, 0.0f,
                        2.0f + chord * 0.10f * (1.0f - h),
                        3.4f - h * 2.0f, 1.8f - h * 0.9f, 3.4f - h * 2.0f,
                        k == 3 ? GLACIER : SLATE, al, off);
            }
        }
    }

    /**
     * Le dragon entier.
     *
     * @param gape ouverture de la mâchoire, 0 fermée → 1 grande ouverte
     * @param glow charge du souffle, 0 éteint → 1 au bord du tir
     */
    private static void dragon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float cx, float cy, float cz, float dx, float dz,
                               float orbit, float radius, float yBase, float t,
                               float gape, float glow, float a) {
        int al = opaque(a);
        if (al <= 0) return;
        int alSoft = opaque(a * 0.90f);
        float[] sp = new float[4];
        float[] off = new float[3];
        float[] piv = new float[3];

        float segStep = DRAGON_SEG * DRAGON_U;              // un segment, en blocs

        // ---- LA QUEUE : douze fois la boîte du cou, derrière le corps. ----------------
        // Elle est dessinée en premier pour que le corps la recouvre là où elles se
        // chevauchent — vanilla fait pareil, et c'est ce qui soude la croupe à la queue.
        // Le pas SUIT l'affinement : à pas constant, une queue qui rétrécit finit en
        // pointillé — le piège arithmétique, quatrième fois. Ici les douze segments
        // restent jointifs du bassin à la pointe.
        float sTail = 98.0f * DRAGON_U;
        for (int i = 0; i < 12; i++) {
            float taper = 1.0f - 0.062f * i;                 // la queue s'affine
            sTail += segStep * taper;
            dragonSpine(sTail, orbit, radius, yBase, t, sp);
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, sp[0], sp[1], sp[2],
                    0.0f, 0.0f, sp[3], 0.0f, 0.0f, 0.0f,
                    10.0f * taper, 10.0f * taper, 10.0f * taper,
                    (i % 3 == 0) ? SLATE : BASALT, alSoft, off);
            // L'écaille dorsale de la pièce « neck », qui court donc aussi sur la queue.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, sp[0], sp[1], sp[2],
                    0.0f, 0.0f, sp[3], 0.0f, -7.0f * taper, 0.0f,
                    2.0f * taper, 4.0f * taper, 6.0f * taper,
                    (i % 2 == 0) ? HORIZON : HALO, al, off);
        }

        // ---- LE CORPS : 24 × 24 × 64, pivot à 50 unités derrière le museau. -----------
        float bodyS = 50.0f * DRAGON_U;
        dragonSpine(bodyS + 16.0f * DRAGON_U, orbit, radius, yBase, t, sp);
        float bodyYaw = sp[3];
        float bodyF = sp[0];
        float bodyL = sp[1];
        float bodyY = sp[2];
        dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, bodyF, bodyL, bodyY,
                0.0f, 0.0f, bodyYaw, 0.0f, 12.0f - 4.0f, 0.0f, 24.0f, 24.0f, 64.0f,
                BASALT, al, off);
        for (int k = 0; k < 3; k++) {
            float mz = -4.0f + k * 20.0f;                    // les trois écailles du dos
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, bodyF, bodyL, bodyY,
                    0.0f, 0.0f, bodyYaw, 0.0f, -3.0f - 4.0f, mz, 2.0f, 6.0f, 12.0f,
                    HORIZON, al, off);
        }

        // ---- LES AILES. Pivot (−12, 5, 2) par rapport au pivot du corps. --------------
        // Le battement tourne autour de l'axe du nez : c'est le paramètre « bank ».
        // L'amplitude était de 0,52 rad : à l'écran, un frémissement. À 0,95 le battement
        // devient l'animation principale de la bête, ce qu'il est chez vanilla aussi.
        float flap = Mth.sin(t * (4.6f + 3.2f * glow) - 0.6f);
        for (int wing = 0; wing < 2; wing++) {
            float side = wing == 0 ? 1.0f : -1.0f;
            float bank = (0.10f + 0.95f * flap) * side;
            // Le pivot, non banqué : la rotation passe PAR lui.
            dragonRot(-2.0f * DRAGON_U, -5.0f * DRAGON_U + 4.0f * DRAGON_U,
                    -12.0f * DRAGON_U * side, 0.0f, 0.0f, bodyYaw, piv);
            float wf = bodyF + piv[0];
            float wl = bodyL + piv[2];
            float wy = bodyY + piv[1];
            // L'humérus, puis la phalange : deux fois 56 unités, soit plus de deux blocs
            // par aile. L'envergure dépasse le corps — c'est le cas chez vanilla aussi.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, wf, wl, wy,
                    0.0f, bank, bodyYaw, -28.0f * side, 0.0f, 0.0f, 56.0f, 8.0f, 8.0f,
                    SLATE, al, off);
            dragonWing(vc, mat, tmp, cx, cy, cz, dx, dz, wf, wl, wy,
                    bank, bodyYaw, side, 1.0f, 0.55f, al, alSoft, off);
            // Le bout d'aile : pivot (−56, 0, 0) par rapport à l'aile, et il casse vers le
            // haut. C'est l'articulation qui empêche l'aile d'être une planche.
            float tipBank = bank + 0.70f * side * (0.5f + 0.5f * flap);
            dragonRot(0.0f, 0.0f, -56.0f * DRAGON_U * side, 0.0f, bank, bodyYaw, piv);
            float tf = wf + piv[0];
            float tl = wl + piv[2];
            float ty = wy + piv[1];
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, tf, tl, ty,
                    0.0f, tipBank, bodyYaw, -28.0f * side, 0.0f, 0.0f, 56.0f, 4.0f, 4.0f,
                    SLATE, al, off);
            dragonWing(vc, mat, tmp, cx, cy, cz, dx, dz, tf, tl, ty,
                    // Le BOUT de l'aile s'effile presque à rien : sans cette pointe la
                    // voilure se terminait au carré, et c'est ce que les captures
                    // montraient — une aile sans bout.
                    tipBank, bodyYaw, side, 0.95f, 0.08f, al, alSoft, off);
        }

        // ---- LES PATTES, repliées : la bête vole, elle ne marche pas. -----------------
        float tuck = 0.85f;
        for (int leg = 0; leg < 4; leg++) {
            boolean rear = leg >= 2;
            float side = (leg % 2 == 0) ? 1.0f : -1.0f;
            float px = (rear ? -16.0f : -12.0f) * side;
            float py = rear ? 16.0f : 20.0f;
            float pz = rear ? 42.0f : 2.0f;
            dragonRot(-pz * DRAGON_U, (-py + 4.0f) * DRAGON_U, px * DRAGON_U,
                    0.0f, 0.0f, bodyYaw, piv);
            float lf = bodyF + piv[0];
            float ll2 = bodyL + piv[2];
            float ly = bodyY + piv[1];
            float thighW = rear ? 16.0f : 8.0f;
            float thighH = rear ? 32.0f : 24.0f;
            // Le fémur pique vers l'arrière quand les pattes sont repliées.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, lf, ll2, ly,
                    tuck, 0.0f, bodyYaw, 0.0f, thighH * 0.5f - 4.0f, 0.0f,
                    thighW, thighH, thighW, BASALT, alSoft, off);
            // Le tibia, replié en sens inverse : un genou, donc une patte.
            dragonRot(0.0f, -thighH * DRAGON_U, 0.0f, tuck, 0.0f, bodyYaw, piv);
            float kf = lf + piv[0];
            float kl = ll2 + piv[2];
            float ky = ly + piv[1];
            float shinW = rear ? 12.0f : 6.0f;
            float shinH = rear ? 32.0f : 24.0f;
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, kf, kl, ky,
                    -tuck * 1.5f, 0.0f, bodyYaw, 0.0f, shinH * 0.5f, rear ? 6.0f : 0.0f,
                    shinW, shinH, shinW, SLATE, alSoft, off);
            // Le pied, griffes vers l'avant.
            dragonRot(0.0f, -shinH * DRAGON_U, 0.0f, -tuck * 1.5f, 0.0f, bodyYaw, piv);
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                    kf + piv[0], kl + piv[2], ky + piv[1],
                    -tuck * 0.4f, 0.0f, bodyYaw,
                    0.0f, rear ? 2.0f : 1.4f, rear ? -5.0f : -3.0f,
                    rear ? 11.0f : 6.0f, rear ? 4.0f : 3.0f, rear ? 14.0f : 11.0f,
                    HALO, al, off);
        }

        // ---- LE COU : cinq fois la même boîte, entre le corps et la tête. -------------
        for (int i = 5; i >= 1; i--) {
            float s = i * segStep;
            dragonSpine(s, orbit, radius, yBase, t, sp);
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, sp[0], sp[1], sp[2],
                    0.0f, 0.0f, sp[3], 0.0f, 0.0f, 0.0f, 10.0f, 10.0f, 10.0f,
                    (i % 2 == 0) ? SLATE : BASALT, alSoft, off);
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, sp[0], sp[1], sp[2],
                    0.0f, 0.0f, sp[3], 0.0f, -7.0f, 0.0f, 2.0f, 4.0f, 6.0f,
                    HORIZON, al, off);
        }

        // ---- LA TÊTE. -----------------------------------------------------------------
        dragonSpine(0.0f, orbit, radius, yBase, t, sp);
        float hYaw = sp[3];
        // Elle pique légèrement vers le cristal quand le souffle se charge.
        float hNose = -0.18f * glow;
        float hf2 = sp[0];
        float hl2 = sp[1];
        float hy2 = sp[2];
        // upperhead 16×16×16 puis upperlip 12×5×16 : le museau du dragon de l'End est un
        // bloc carré suivi d'une lèvre plate, et c'est à ça qu'on le reconnaît.
        dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, hf2, hl2, hy2,
                hNose, 0.0f, hYaw, 0.0f, 0.0f, -2.0f, 16.0f, 16.0f, 16.0f,
                BASALT, al, off);
        dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, hf2, hl2, hy2,
                hNose, 0.0f, hYaw, 0.0f, 1.5f, -16.0f, 12.0f, 5.0f, 16.0f,
                SLATE, al, off);
        for (int sideIdx = 0; sideIdx < 2; sideIdx++) {
            float sgn = sideIdx == 0 ? -1.0f : 1.0f;
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, hf2, hl2, hy2,
                    hNose, 0.0f, hYaw, sgn * 4.0f, -10.0f, -1.0f, 2.0f, 4.0f, 6.0f,
                    HORIZON, al, off);
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, hf2, hl2, hy2,
                    hNose, 0.0f, hYaw, sgn * 4.0f, -2.0f, -20.0f, 2.0f, 2.0f, 4.0f,
                    glow > 0.5f ? SINGULAR : GLACIER, al, off);
            // Les yeux ne sont pas dans le modèle vanilla, ils sont dans sa TEXTURE. Il en
            // faut donc deux, posés à la main sur les joues : sans eux le dragon n'a pas
            // de regard, et on ne sait pas qu'il vise.
            dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz, hf2, hl2, hy2,
                    hNose, 0.0f, hYaw, sgn * 8.0f, -4.0f, -6.0f, 1.5f, 3.0f, 4.0f,
                    glow > 0.35f ? SINGULAR : HORIZON, al, off);
        }
        // La mâchoire : pivot (0, 4, −8) dans la tête, elle s'ouvre vers le BAS.
        float jaw = 0.12f + 0.55f * gape;
        dragonRot(8.0f * DRAGON_U, -4.0f * DRAGON_U, 0.0f, hNose, 0.0f, hYaw, piv);
        dragonBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                hf2 + piv[0], hl2 + piv[2], hy2 + piv[1],
                hNose - jaw, 0.0f, hYaw, 0.0f, 2.0f, -8.0f, 12.0f, 4.0f, 16.0f,
                BASALT, al, off);

        // ---- LA CHARGE DU SOUFFLE, dans la gueule. ------------------------------------
        if (glow > 0.03f) {
            float[] mz2 = new float[3];
            dragonMuzzle(orbit, radius, yBase, t, mz2);
            float r = 0.045f + 0.085f * glow;
            for (int k = 0; k < 7; k++) {
                float ang = k * TAU / 7.0f + t * 9.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        mz2[0] + Mth.cos(ang) * r, mz2[1] + Mth.sin(ang) * r,
                        mz2[2] - jaw * 0.10f + Mth.sin(ang * 2.0f) * r * 0.6f,
                        0.026f, 0.026f, 0.026f,
                        glow > 0.70f ? SINGULAR : GLACIER, opaque(a * glow));
            }
        }
    }



    /**
     * <b>Un prisme fait une seule chose : il prend un rayon et il en sort plusieurs.</b> Le
     * serveur tire SEPT faisceaux, un par corps, au tick 62 puis toutes les neuf frames
     * ({@code applyDragonPrismGameplay}). L'image ne décore pas ce calendrier, elle l'EST :
     * les sept rayons sont tracés par réfraction réelle dans l'octaèdre, un indice par
     * faisceau, et chacun part à l'instant exact où le serveur inflige ses dégâts.
     *
     * <h3>Le calcul</h3>
     * <ul>
     *   <li>le rayon incident est transporté dans le repère du cristal, puis intersecté
     *       avec l'octaèdre {@code |f|+|l|+|y| ≤ w} par découpe sur huit plans ;</li>
     *   <li>à l'entrée comme à la sortie, <b>Snell-Descartes vectoriel</b> avec un indice
     *       par faisceau — 1,14 à 1,62. C'est la DISPERSION qui ouvre l'éventail : les sept
     *       rayons ne divergent pas parce qu'on leur a donné sept angles, ils divergent
     *       parce qu'ils ne voient pas le même verre ;</li>
     *   <li>la <b>réflexion totale</b> est traitée, et elle arrive : les indices hauts
     *       rebondissent à l'intérieur avant de trouver une face qui les laisse sortir,
     *       d'où deux faisceaux qui partent d'une arête inattendue ;</li>
     *   <li>chaque tir donne un <b>recul</b> au cristal, opposé au rayon et amorti : sept
     *       coups de canon dans sept directions, donc un corps qui tressaute.</li>
     * </ul>
     *
     * <h3>La chronologie, en ticks du serveur</h3>
     * <ul>
     *   <li><b>4 / 10 / 16</b> la descente en trois à-coups — les trois sons du serveur ;</li>
     *   <li><b>42</b> le trait d'entrée frappe la face : un faisceau PLEIN, le seul de la
     *       famille ;</li>
     *   <li><b>62 → 116</b> les sept tirs. Un faisceau creux par corps touché, qui s'arrête
     *       AU SOL s'il descend, avec sa marque d'impact dessus ;</li>
     *   <li><b>150</b> la fracture : les douze arêtes se séparent, tombent sous la pesanteur
     *       et rebondissent ;</li>
     *   <li><b>170</b> l'éclat : l'anneau au sol et la colonne d'{@link #INVERT}.</li>
     * </ul>
     */
    private static void renderPrism(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float body = prismBody(t);
        float spin = prismSpin(t);
        float w = PRISM_W * body;
        float broken = clamp((t - PRISM_BREAK) / 0.10f);
        float floorY = GROUND_Y + 0.05f;

        // --- La descente, en trois paliers. Un objet qui se pose d'un glissement continu
        //     n'a pas de masse ; trois paliers suffisent à lui en donner. ---
        float drop = clamp(t / 0.085f);
        float stair = (Mth.floor(drop * 3.0f) + smooth(fract(drop * 3.0f))) / 3.0f;
        float yc = PRISM_YC + (1.0f - stair) * 4.6f;

        float[] p = new float[3];
        float[] re = new float[3];
        float[] rp = new float[3];
        float[] rd = new float[3];

        // ---- 1. LE RECUL, calculé avant tout le reste ---------------------------------
        float recF = 0.0f;
        float recL = 0.0f;
        float recY = 0.0f;
        for (int k = 0; k < 7; k++) {
            float fire = PRISM_FIRE0 + k * PRISM_FIRE_STEP;
            if (t < fire || t > PRISM_BREAK) continue;
            if (!prismRay(k, prismSpin(fire), PRISM_W * prismBody(fire), re, rp, rd)) continue;
            float kick = (1.0f - smooth(clamp((t - fire) / 0.085f))) * 0.055f;
            recF -= rd[0] * kick;
            recL -= rd[1] * kick;
            recY -= rd[2] * kick;
        }

        // ---- 2. LE TRAIT D'ENTRÉE -----------------------------------------------------
        // Tick 42 : il FRAPPE. Avant, il approche ; après, il alimente, et son intensité
        // bat au rythme des tirs. C'est la seule chose pleine de toute la signature, et
        // c'est elle qui donne sa valeur au creux de tout le reste.
        {
            float come = clamp((t - 0.175f) / 0.046f);
            float live = (1.0f - smooth(clamp((t - 0.62f) / 0.10f)));
            if (come > 0.0f && live > 0.01f) {
                float head = -3.4f + (3.4f - w * 0.60f) * smooth(come);
                float beat = 0.74f + 0.26f * Mth.sin(t * 58.0f);
                float spread = 0.62f * PRISM_W;
                // L'ÂME du trait, pleine et blanche : c'est elle qui frappe au tick 42.
                for (float f = -3.4f; f <= head; f += 0.085f) {
                    float grow = clamp((f + 3.4f) / 0.9f);
                    // 0,140 bloc de large : deux fois l'arête du cristal, trois fois un
                    // faisceau. Elle reste la chose la plus épaisse de la signature —
                    // c'est son rôle — mais une brique n'est pas un trait.
                    float th = (0.024f + 0.032f * grow) * live;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.0f, yc, th, th, th,
                            grow > 0.82f ? SINGULAR : GLACIER,
                            opaque(a * (0.55f + 0.45f * grow) * beat * live));
                }
                // LES SEPT FILS, aux endroits exacts où les sept rayons entrent. On voit
                // donc arriver sept choses parallèles, et on verra sortir sept choses
                // divergentes : c'est le verbe de la signature, en une image.
                // Un pas de 0,13 pour des cubes de 0,048 : il y avait plus de vide que de
                // matière, et ces sept lignes de tirets en travers de la figure étaient
                // LE pointillé que montraient les captures. Deux corrections, pas une :
                // le pas passe sous la taille du cube — sinon rien ne touche rien — et
                // les fils ne courent plus que sur les deux derniers blocs, là où ils
                // convergent vers les sept points d'entrée. L'approche reste dite par
                // l'âme du trait ; sept tirets parallèles ne la disaient pas mieux.
                float tail = Math.max(-3.4f, head - 2.0f);
                for (int k = 0; k < 7; k++) {
                    float off = k * (TAU / 7.0f) + 0.40f;
                    float ol = Mth.cos(off) * spread;
                    float oy = Mth.sin(off) * spread;
                    for (float f = tail; f <= head; f += 0.038f) {
                        float grow = clamp((f + 3.4f) / 1.2f);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, ol, yc + oy,
                                0.020f, 0.020f, 0.020f,
                                k % 3 == 0 ? GLACIER : (k % 3 == 1 ? HORIZON : HALO),
                                opaque(a * (0.30f + 0.45f * grow) * live));
                    }
                }
            }
        }

        // ---- 1 bis. LE DRAGON ---------------------------------------------------------
        // Il arrive au tick 20, tourne autour du cristal, et c'est LUI qui souffle. Le
        // cristal n'est plus l'acteur : il est le prisme qui découpe le souffle. Deux
        // objets, deux rôles, et le titre se lit sans légende.
        //
        // L'orbite se resserre à chaque tir (le dragon plonge sur sa proie) puis s'ouvre à
        // la fracture, quand il lâche le cristal et s'en va.
        {
            float enter = smooth(clamp((t - 0.105f) / 0.075f));
            float leave = smooth(clamp((t - PRISM_BURST) / 0.09f));
            float present = enter * (1.0f - leave);
            if (present > 0.01f) {
                // Quel faisceau se prépare, et à quel point ? C'est ce qui pilote la
                // gueule, les yeux et la vitesse de battement.
                float gape = 0.0f;
                float glow = 0.0f;
                for (int k = 0; k < 7; k++) {
                    float fire = PRISM_FIRE0 + k * PRISM_FIRE_STEP;
                    float before = clamp((t - (fire - 0.055f)) / 0.055f);
                    float after = 1.0f - clamp((t - fire) / 0.045f);
                    float g = before * after;
                    if (g > glow) glow = g;
                    float open = before * (1.0f - clamp((t - fire) / 0.085f));
                    if (open > gape) gape = open;
                }
                // La fracture : le dragon rugit, gueule grande ouverte, une seconde.
                float roar = clamp((t - PRISM_BREAK) / 0.045f) * (1.0f - clamp((t - PRISM_BREAK - 0.06f) / 0.07f));
                gape = Math.max(gape, roar);
                glow = Math.max(glow, roar * 0.65f);

                float orbit = t * 4.3f + 0.9f;
                // Le dragon descend avec le cristal, puis plonge d'un demi-bloc à chaque
                // tir : la trajectoire n'est pas un cercle plat, c'est une approche.
                float dive = 0.42f * glow;
                float radius = (3.25f - 0.55f * smooth(clamp((t - PRISM_FIRE0) / 0.30f))) * (1.0f - 0.10f * dive)
                        + leave * 2.6f;
                float yBase = yc + 0.95f - dive * 0.55f + (1.0f - enter) * 3.4f + leave * 1.8f;
                dragon(vc, mat, tmp, cx, cy, cz, dx, dz, orbit, radius, yBase, t,
                        gape, glow, a * present);

                // LE SOUFFLE : de la gueule du dragon jusqu'à la face du cristal. C'est le
                // trait d'entrée du serveur (tick 42), mais on voit maintenant d'où il
                // vient. Il ne s'allume que quand le dragon tire.
                if (glow > 0.12f) {
                    float[] head = new float[3];
                    dragonMuzzle(orbit, radius, yBase, t, head);
                    float bx = head[0];
                    float bl = head[1];
                    float by = head[2];
                    // 76 pas au lieu de 38 : à 0,013 de côté, un pas de 0,08 laissait un
                    // pointillé. Fin ET continu, c'est un pas plus court, pas un cube
                    // plus gros — le piège de densité se paie ici aussi.
                    int steps = 76;
                    for (int k = 1; k <= steps; k++) {
                        float u = k / (float) steps;
                        float swell = Mth.sin(u * Mth.PI) * 0.10f * glow;
                        float th = (0.021f + 0.008f * glow) * (1.0f - u * 0.45f) + swell * 0.10f;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                bx + (0.0f - bx) * u, bl + (0.0f - bl) * u,
                                by + (yc - by) * u + Mth.sin(u * Mth.PI) * 0.18f,
                                th, th, th,
                                u > 0.78f ? SINGULAR : (k % 3 == 0 ? GLACIER : HORIZON),
                                opaque(a * glow * (0.45f + 0.55f * u) * present));
                    }
                }
            }
        }

        // ---- 2 bis. LES TRAJETS INTERNES ----------------------------------------------
        // Entre l'entrée et la sortie, le rayon est DANS le verre, et il y est coudé. C'est
        // la seule preuve visible que l'éventail vient d'une réfraction et non d'un angle
        // écrit à la main — et le faisceau s'allume quand son tour approche.
        if (body > 0.05f && t > 0.19f) {
            for (int k = 0; k < 7; k++) {
                float fire = PRISM_FIRE0 + k * PRISM_FIRE_STEP;
                float charge = clamp((t - 0.19f) / Math.max(0.02f, fire - 0.19f));
                float after = 1.0f - smooth(clamp((t - fire) / 0.22f));
                float glow = charge * (0.35f + 0.65f * after);
                if (glow < 0.04f) continue;
                if (!prismRay(k, spin, w, re, rp, rd)) continue;
                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        re[0], re[1], yc + re[2], rp[0], rp[1], yc + rp[2],
                        0.022f + 0.014f * glow, 0.055f,
                        glow > 0.80f ? SINGULAR : (k % 2 == 0 ? GLACIER : HORIZON),
                        opaque(a * (0.30f + 0.60f * glow)));
            }
        }

        // ---- 3. LES SEPT FAISCEAUX ----------------------------------------------------
        for (int k = 0; k < 7; k++) {
            float fire = PRISM_FIRE0 + k * PRISM_FIRE_STEP;
            if (t < fire) continue;
            float age = t - fire;
            // Éteints AVANT que les arêtes se désolidarisent : ils tenaient encore à 22 %
            // pendant la fracture, et sept traits sans source au milieu des éclats ne se
            // lisent plus comme des faisceaux — juste comme des traits.
            float live = 1.0f - smooth(clamp((t - PRISM_BREAK + 0.055f) / 0.045f));
            if (live <= 0.01f) continue;
            if (!prismRay(k, prismSpin(fire), PRISM_W * prismBody(fire), re, rp, rd)) continue;
            float reach = 3.9f * smooth(clamp(age / 0.055f));
            if (reach < 0.08f) continue;
            float bf = rp[0];
            float bl = rp[1];
            float by = PRISM_YC + rp[2];
            // Arrêt AU SOL. Un faisceau qui plonge sous le plancher est un faisceau qui
            // n'a touché personne : il s'arrête sur la surface, et il y laisse sa marque.
            boolean grounded = false;
            if (rd[2] < -0.03f) {
                float toFloor = (GROUND_Y + 0.03f - by) / rd[2];
                if (toFloor > 0.05f && toFloor < reach) { reach = toFloor; grounded = true; }
            }
            // Base orthonormée autour du rayon, pour une section CREUSE : cinq cubes en
            // couronne, rien au milieu. Un faisceau plein serait de la matière.
            float ux;
            float ul;
            float uy;
            if (Math.abs(rd[2]) < 0.90f) {
                ux = -rd[1]; ul = rd[0]; uy = 0.0f;
            } else {
                ux = 1.0f; ul = 0.0f; uy = 0.0f;
            }
            float un = Mth.sqrt(ux * ux + ul * ul + uy * uy);
            if (un < 1.0e-4f) { ux = 1.0f; ul = 0.0f; uy = 0.0f; un = 1.0f; }
            ux /= un; ul /= un; uy /= un;
            float wx = rd[1] * uy - rd[2] * ul;
            float wl = rd[2] * ux - rd[0] * uy;
            float wy = rd[0] * ul - rd[1] * ux;
            // LE PAS EST PLUS PETIT QUE LE CUBE : c'est la seule façon d'obtenir un trait
            // plein. L'ancienne couronne de cinq cubes espacée de 0,155 était large ET
            // trouée — large parce qu'elle faisait 0,156 bloc de diamètre, trouée parce
            // qu'un anneau tous les 0,155 pour des cubes de 0,026 ne se touche jamais.
            // Un cœur d'un cube par pas, et le faisceau fait 0,046 de large, sans trou.
            int steps = Math.max(6, Math.min(110, Math.round(reach / 0.042f)));
            // HALO sur un trait de 0,05 bloc disparaît : un tube large pouvait se
            // permettre un ton sombre, une ligne fine non. Deux tons clairs seulement,
            // sinon un faisceau sur trois ne se voit pas et les deux autres ont l'air
            // d'être en pointillé à côté de lui.
            int tone = (k & 1) == 0 ? GLACIER : HORIZON;
            for (int sIdx = 0; sIdx <= steps; sIdx++) {
                float u = sIdx / (float) steps;
                float d = u * reach;
                float ff = bf + rd[0] * d;
                float ll = bl + rd[1] * d;
                float yy = by + rd[2] * d;
                float core = 0.025f * (1.0f - 0.30f * u);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                        core, core, core,
                        sIdx == steps ? SINGULAR : tone,
                        opaque(a * (0.52f + 0.48f * (1.0f - u)) * live));
                // Pas d'étincelle satellite : mesuré au banc, un cube décalé tous les
                // quatre pas dessine une SECONDE ligne, trouée celle-là, et c'est elle que
                // l'œil suit — la décoration imitait exactement le défaut qu'on vient de
                // corriger. La vrille passe donc par la TEINTE du cœur, pas par un satellite.
                if (sIdx % 5 == 0 && sIdx < steps) {
                    float shimmer = 0.5f + 0.5f * Mth.sin(d * 2.4f + t * 6.0f + k);
                    // MÊME taille que le cœur : grossie de 22 %, la perle se détachait et
                    // le faisceau se lisait comme un chapelet. Seule la teinte change.
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy, core, core, core,
                            shimmer > 0.72f ? SINGULAR : GLACIER,
                            opaque(a * (0.55f + 0.40f * shimmer) * live));
                }
            }
            // L'IMPACT. Au sol c'est une marque plaquée sur la surface ; en l'air, l'étoile
            // du corps touché. Dans les deux cas elle naît à l'instant du dégât serveur.
            float ef = bf + rd[0] * reach;
            float el = bl + rd[1] * reach;
            float ey = by + rd[2] * reach;
            float flash = 1.0f - clamp(age / 0.085f);
            if (grounded) {
                float rr = 0.14f + 0.52f * clamp(age / 0.16f);
                int ring = Math.max(10, Math.round(rr * 22.0f));
                for (int i = 0; i < ring; i++) {
                    float ang = i / (float) ring * TAU;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ef + Mth.cos(ang) * rr, el + Mth.sin(ang) * rr, GROUND_Y + 0.016f,
                            0.040f, SLAB_H, 0.040f, (i & 1) == 0 ? tone : HALO,
                            opaque(a * (0.80f - 0.55f * clamp(age / 0.22f)) * live));
                }
            }
            if (flash > 0.02f) {
                for (int i = 0; i < 6; i++) {
                    float ang = i * (TAU / 6.0f) + t * 3.0f;
                    float rr = 0.10f + 0.26f * (1.0f - flash);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ef + Mth.cos(ang) * rr, el + Mth.sin(ang) * rr, ey + (i % 2 == 0 ? 0.08f : -0.08f),
                            0.036f, 0.036f, 0.036f, SINGULAR, opaque(a * flash * live));
                }
            }
        }

        // ---- 3 bis. CE QU'IL Y A DANS LE CRISTAL --------------------------------------
        // Pas du verre : du RIEN. Neuf cubes d'abysse en suspension, qui tournent avec lui
        // et se dispersent à la fracture. C'est la même texture à couches que la fenêtre
        // de la Faille, mais posée sur des VOLUMES — un prisme n'a pas d'intérieur à
        // montrer à travers une surface, il a un contenu.
        if (body > 0.05f) {
            float scatter = clamp((t - PRISM_BREAK) / 0.14f);
            for (int k = 0; k < 9; k++) {
                float u = (k + 0.5f) / 9.0f;
                float phi = (float) Math.acos(1.0f - 2.0f * u);
                float theta = k * 2.39996f + t * 1.9f;
                float rr = w * 0.46f * (0.45f + 0.55f * hash(salt, k * 71))
                        * (1.0f + scatter * 5.5f);
                float sinp = Mth.sin(phi);
                float px = Mth.cos(theta) * sinp * rr;
                float pl = Mth.sin(theta) * sinp * rr;
                float py = Mth.cos(phi) * rr;
                float half = (0.052f + 0.022f * hash(salt, k * 73)) * (1.0f - scatter * 0.5f);
                if (half <= 0.006f) continue;
                float fy = yc + py + recY + (scatter > 0.0f
                        ? -0.5f * GRAVITY * (t - PRISM_BREAK) * (t - PRISM_BREAK) * 9.0f : 0.0f);
                if (fy < floorY) fy = floorY;
                VoidPortalFx.queueCube(
                        cx + (px + recF) * dx - (pl + recL) * dz,
                        cy + fy,
                        cz + (px + recF) * dz + (pl + recL) * dx,
                        half, theta);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, px + recF, pl + recL, fy,
                        half * 1.16f, half * 1.16f, half * 1.16f,
                        NOTHING, opaque(a * (1.0f - scatter * 0.35f)));
            }
        }

        // ---- 4. LE CRISTAL ------------------------------------------------------------
        {
            float[] vert = new float[18];
            for (int v = 0; v < 6; v++) {
                float vf = v == 0 ? w : (v == 1 ? -w : 0.0f);
                float vl2 = v == 2 ? w : (v == 3 ? -w : 0.0f);
                float vy2 = v == 4 ? w : (v == 5 ? -w : 0.0f);
                spinPoint(p, vf, vl2, vy2, PRISM_TILT, spin);
                vert[v * 3] = p[0] + recF;
                vert[v * 3 + 1] = p[1] + recL;
                vert[v * 3 + 2] = yc + p[2] + recY;
            }
            // Quatre arêtes d'équateur d'abord : ce sont elles qui encadrent la fenêtre.
            final int[] edges = { 0, 2, 2, 1, 1, 3, 3, 0, 0, 4, 2, 4, 1, 4, 3, 4, 0, 5, 2, 5, 1, 5, 3, 5 };
            float sec = (t - PRISM_BREAK) * 9.5f;
            for (int e = 0; e < 12; e++) {
                int i0e = edges[e * 2];
                int i1e = edges[e * 2 + 1];
                float f0 = vert[i0e * 3];
                float l0 = vert[i0e * 3 + 1];
                float y0 = vert[i0e * 3 + 2];
                float f1 = vert[i1e * 3];
                float l1 = vert[i1e * 3 + 1];
                float y1 = vert[i1e * 3 + 2];
                float fade = 1.0f;
                if (broken > 0.0f) {
                    // LA FRACTURE. Chaque arête part avec sa vitesse propre, tourne autour
                    // de son milieu, tombe et REBONDIT. Douze morceaux qui s'évanouissent en
                    // l'air seraient douze morceaux sans poids.
                    float mf = (f0 + f1) * 0.5f;
                    float ml = (l0 + l1) * 0.5f;
                    float my = (y0 + y1) * 0.5f;
                    float out = Mth.sqrt(mf * mf + ml * ml) + 0.001f;
                    float sp = 0.9f + 1.1f * hash(salt, e * 7);
                    float ddf = mf / out * sp;
                    float ddl = ml / out * sp;
                    float nf = mf + ddf * sec;
                    float nl = ml + ddl * sec;
                    float ny = ballistic(my, 1.1f + 1.6f * hash(salt, e * 13), sec, floorY);
                    float roll = sec * (1.8f + 3.4f * hash(salt, e * 19));
                    float cr = Mth.cos(roll);
                    float sr = Mth.sin(roll);
                    float hf = (f1 - f0) * 0.5f;
                    float hl = (l1 - l0) * 0.5f;
                    float hy = (y1 - y0) * 0.5f;
                    float rf = hf * cr - hl * sr;
                    float rl = hf * sr + hl * cr;
                    f0 = nf - rf; f1 = nf + rf;
                    l0 = nl - rl; l1 = nl + rl;
                    y0 = ny - hy; y1 = ny + hy;
                    fade = 1.0f - smooth(clamp((t - PRISM_BREAK) / 0.20f)) * 0.75f;
                }
                boolean equator = e < 4;
                // 0,080 bloc de large : deux fois le faisceau, trois fois les cubes
                // voisins. L'arête d'un objet ne doit pas peser plus que l'objet.
                float th = (equator ? 0.032f : 0.024f) * (0.55f + 0.45f * body + 0.45f * broken);
                int tone = broken > 0.0f ? (((e & 1) == 0) ? BASALT : SLATE)
                        : (equator ? GLACIER : HORIZON);
                // En clair, et PAS avec segment() : ce helper plafonne à 26 cubes, et une
                // arête de 1,63 bloc à ce pas en demande 34 — affiner par segment() aurait
                // rendu l'octaèdre pointillé. Le pas suit la taille du cube, toujours.
                float ef2 = f1 - f0;
                float el2 = l1 - l0;
                float ey2 = y1 - y0;
                float elen = Mth.sqrt(ef2 * ef2 + el2 * el2 + ey2 * ey2);
                int en = Math.max(2, Math.min(60, Math.round(elen / Math.max(0.020f, th * 1.55f))));
                int ealpha = opaque(a * 0.95f * fade);
                for (int es = 0; es <= en; es++) {
                    float eu = es / (float) en;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f0 + ef2 * eu, l0 + el2 * eu, y0 + ey2 * eu,
                            th, th, th, tone, ealpha);
                }
            }
            // Les six sommets, marqués. Sans eux l'octaèdre se lit comme une cage molle.
            if (broken <= 0.0f) {
                for (int v = 0; v < 6; v++) {
                    float pulse = 0.70f + 0.30f * Mth.sin(t * 19.0f + v * 1.7f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            vert[v * 3], vert[v * 3 + 1], vert[v * 3 + 2],
                            0.052f, 0.052f, 0.052f, pulse > 0.92f ? SINGULAR : GLACIER,
                            opaque(a * pulse));
                }
            }
        }

        // ---- 5. LES QUATRE ÉCLATS ARRACHÉS AU SOL -------------------------------------
        // Ils viennent du plancher, se verrouillent à l'équateur, et y retombent. Le vide
        // n'invente pas de matière : il prend celle du monde, et elle redescend.
        {
            float rise = smooth(clamp((t - 0.10f) / 0.14f));
            for (int i = 0; i < 4; i++) {
                float ang = i * (TAU / 4.0f) + spin * 0.85f;
                float orbit = PRISM_W * 1.46f;
                float sf = Mth.cos(ang) * orbit;
                float sl = Mth.sin(ang) * orbit;
                float home = yc + Mth.sin(spin * 0.7f + i) * 0.10f;
                float sy = GROUND_Y + (home - GROUND_Y) * rise;
                if (broken > 0.0f) {
                    float fallSec = (t - PRISM_BREAK) * 9.5f;
                    sf += Mth.cos(ang) * 1.25f * fallSec;
                    sl += Mth.sin(ang) * 1.25f * fallSec;
                    sy = ballistic(home, 0.7f, fallSec, floorY);
                }
                fragment(vc, mat, tmp, cx, cy, cz, dx, dz, sf, sl, sy,
                        0.072f, spin * 1.4f + i, ENDSTONE, ENDSTONE_DK,
                        opaque(a * (0.35f + 0.65f * rise)));
            }
        }

        // ---- 6. L'ÉCLAT — tick 170 ----------------------------------------------------
        {
            float burst = clamp((t - PRISM_BURST) / 0.105f);
            if (burst > 0.0f) {
                float rr = 0.3f + 2.3f * smooth(burst);
                int ring = Math.max(22, Math.round(rr * 20.0f));
                for (int i = 0; i < ring; i++) {
                    float ang = i / (float) ring * TAU;
                    float wob = 1.0f + 0.06f * Mth.sin(ang * 7.0f + t * 9.0f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr * wob, Mth.sin(ang) * rr * wob, GROUND_Y + 0.016f,
                            0.052f, SLAB_H, 0.052f, (i % 5 == 0) ? INVERT : HORIZON,
                            opaque(a * (1.0f - burst) * 0.95f));
                }
                // La colonne : ce que le prisme a pris ressort, et ressort en INVERT.
                for (int i = 0; i < 14; i++) {
                    float u = (i / 14.0f + burst * 1.4f) % 1.0f;
                    float ang = i * 2.39996f + t * 4.0f;
                    float rr2 = 0.18f + 0.52f * u;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr2, Mth.sin(ang) * rr2,
                            GROUND_Y + 0.05f + u * 3.4f,
                            0.042f, 0.042f, 0.042f, (i & 1) == 0 ? INVERT : GLACIER,
                            opaque(a * (1.0f - u) * (1.0f - burst * 0.5f)));
                }
            }
        }
    }

    /**
     * Le trajet d'un des sept rayons DANS le cristal, par réfraction réelle.
     *
     * <p>Le rayon incident part de {@code f = −3} et passe par le centre : dans le repère du
     * cristal il passe donc par l'origine, quelle que soit la rotation. Il entre, se
     * réfracte, traverse, se réfracte encore — et s'il est pris en réflexion totale il
     * rebondit à l'intérieur. Au troisième rebond on le laisse sortir par la normale : un
     * faisceau prisonnier ne vaut rien à l'écran, et le serveur en tire SEPT, pas cinq.</p>
     *
     * @param outP point de sortie, repère de l'effet, relatif au centre du cristal
     * @param outD direction de sortie, unitaire, repère de l'effet
     */
    private static boolean prismRay(int k, float spin, float w,
                                    float[] outE, float[] outP, float[] outD) {
        if (w <= 0.06f) return false;
        float[] o = new float[3];
        float[] d = new float[3];
        float[] hit = new float[8];
        float[] dir = new float[3];
        // LE FAISCEAU INCIDENT EST UN CYLINDRE, pas un crayon. Les sept rayons entrent
        // donc à sept endroits de sa section : faces différentes, incidences différentes,
        // et c'est ÇA qui ouvre l'éventail. Un rayon qui passe par le centre d'un corps
        // symétrique en ressort presque dans l'axe, quel que soit son indice — la première
        // version le faisait, et les sept faisceaux se superposaient en un seul trait.
        float off = k * (TAU / 7.0f) + 0.40f;
        float spread = 0.62f * w;
        unspinPoint(o, -3.0f, Mth.cos(off) * spread, Mth.sin(off) * spread, PRISM_TILT, spin);
        unspinPoint(d, 1.0f, 0.0f, 0.0f, PRISM_TILT, spin);
        if (!octaHit(o[0], o[1], o[2], d[0], d[1], d[2], w, hit)) return false;
        // DISPERSION : un indice par faisceau. C'est tout ce qui les sépare.
        float n = 1.14f + k * 0.08f;
        float px = o[0] + d[0] * hit[0];
        float pl = o[1] + d[1] * hit[0];
        float py = o[2] + d[2] * hit[0];
        spinPoint(outE, px, pl, py, PRISM_TILT, spin);
        refract(dir, d[0], d[1], d[2], hit[1], hit[2], hit[3], 1.0f / n);
        float ix = dir[0];
        float il = dir[1];
        float iy = dir[2];
        boolean escaped = false;
        for (int bounce = 0; bounce < 3 && !escaped; bounce++) {
            if (!octaHit(px + ix * 0.002f, pl + il * 0.002f, py + iy * 0.002f, ix, il, iy, w, hit)) {
                break;
            }
            float travel = hit[4];
            px += ix * travel;
            pl += il * travel;
            py += iy * travel;
            escaped = refract(dir, ix, il, iy, hit[5], hit[6], hit[7], n);
            ix = dir[0];
            il = dir[1];
            iy = dir[2];
            if (!escaped && bounce == 2) {
                ix = hit[5]; il = hit[6]; iy = hit[7];
                escaped = true;
            }
        }
        spinPoint(outP, px, pl, py, PRISM_TILT, spin);
        spinPoint(outD, ix, il, iy, PRISM_TILT, spin);
        float len = Mth.sqrt(outD[0] * outD[0] + outD[1] * outD[1] + outD[2] * outD[2]);
        if (len < 1.0e-4f) return false;
        outD[0] /= len; outD[1] /= len; outD[2] /= len;
        // L'ÉVENTAIL EST RABATTU DEVANT. Après trois réflexions internes, le rayon piégé
        // est relâché le long de la NORMALE de la face : une direction qui n'a plus de
        // rapport avec l'axe d'entrée et qui peut pointer n'importe où. Mesuré au banc,
        // un ou deux des sept repartaient EN ARRIÈRE à presque chaque instant (jusqu'à
        // 124°) — à l'écran, ils « rentrent » dans le prisme, et c'est très exactement ce
        // qu'on nous a signalé. On borne donc l'inclinaison à [18°, 62°] en gardant
        // l'AZIMUT intact : les sept conservent leur ordre et leur dispersion, mais ils
        // partent tous du bon côté. La signature DISTRIBUE ; ce qu'elle envoie s'en va.
        float perp = Mth.sqrt(outD[1] * outD[1] + outD[2] * outD[2]);
        float tilt = (float) Math.atan2(perp, outD[0]);
        // COMPRESSION, pas écrêtage : borner par Mth.clamp collait la moitié des sept
        // pile sur 62° — ils gardaient leur azimut mais perdaient leur angle propre, donc
        // la dispersion, qui est le sujet même de la figure. L'intervalle [0, π] est donc
        // REPLIÉ linéairement sur [18°, 62°] : l'ordre des sept et l'écart entre leurs
        // indices survivent intacts, et aucun ne repart en arrière.
        float bound = 0.31f + (1.08f - 0.31f) * (tilt / Mth.PI);
        {
            float az = k * (TAU / 7.0f) + 0.40f;
            float cl = perp < 1.0e-4f ? Mth.cos(az) : outD[1] / perp;
            float cy = perp < 1.0e-4f ? Mth.sin(az) : outD[2] / perp;
            float sb = Mth.sin(bound);
            outD[0] = Mth.cos(bound);
            outD[1] = cl * sb;
            outD[2] = cy * sb;
        }
        return true;
    }

    // =================================================================================
    //  III. FAILLE DU NÉANT — 170 ticks — elle TRIE
    // =================================================================================

    /**
     * <b>La faille est un PIÈGE À LOUP.</b> Le serveur ne ratisse pas un disque mais un
     * corridor de neuf blocs de long sur trois de large, PERPENDICULAIRE à l'axe du coup
     * ({@code applyNullRiftGameplay}) : se tenir dans son axe ou à deux pas de côté change
     * tout. Deux versions de cette figure ont été jugées trop discrètes, et le diagnostic
     * était faux les deux fois : le problème n'était pas l'intensité, c'était l'absence de
     * MÉCANISME. Deux lèvres qui s'écartent ne racontent rien, parce qu'on ne voit jamais
     * ce qui serait pris. Un piège, si.
     *
     * <h3>Le calcul</h3>
     * <ul>
     *   <li><b>deux mâchoires</b> de {@link #RIFT_JAW} montées sur des charnières à
     *       ±{@link #RIFT_HINGE}, dont l'arc est INTÉGRÉ pas à pas avec une courbure
     *       {@link #RIFT_CURVE} : une mâchoire droite est une planche, c'est la courbure
     *       qui fait qu'elle cueille ;</li>
     *   <li><b>l'angle de fermeture se résout</b> ({@link #RIFT_SHUT}) au lieu d'être écrit
     *       à la main — dix pas de dichotomie pour que les deux pointes se rejoignent
     *       exactement sur l'axe. Un piège qui ferme à un cheveu près ne ferme pas ;</li>
     *   <li><b>neuf dents par mâchoire</b>, décalées d'un demi-pas d'une mâchoire à
     *       l'autre : elles s'ENGRÈNENT au lieu de se percuter, et une dent sur deux est
     *       plus courte — une rangée régulière fait un peigne ;</li>
     *   <li><b>les traînées</b> : pendant le claquement le bord denté parcourt deux radians
     *       en sept ticks. Il est redessiné à {@link #RIFT_GHOSTS} positions qu'il vient de
     *       quitter, sinon l'œil ne voit que l'arrivée et le coup n'existe pas. Hors
     *       claquement la vitesse est nulle et les échos ne coûtent rien ;</li>
     *   <li><b>le splash</b> : trente-quatre paraboles, chacune avec quatre positions
     *       antérieures sur la MÊME parabole. Une gerbe sans traînée est une pluie de
     *       confettis ;</li>
     *   <li><b>les ressorts de torsion</b> aux deux bouts de la charnière : leur pas se
     *       resserre avec la charge. C'est l'énergie stockée, rendue visible.</li>
     * </ul>
     *
     * <h3>La chronologie, en ticks du serveur</h3>
     * <ul>
     *   <li><b>2 → 51</b> le piège s'arme, en {@code 1−(1−u)³} ;</li>
     *   <li><b>20 → 100</b> les neuf aspirations, et à chacune une <b>morsure manquée</b> :
     *       il se referme d'un tiers puis se rouvre. Neuf fois il essaie, la dixième il
     *       mord — le compte des neuf se lit sur le mécanisme ;</li>
     *   <li><b>51 → 140</b> armé, et il tremble de plus en plus ;</li>
     *   <li><b>112</b> l'autre côté : ce qui remonte par la fente est en {@link #CHORUS} ;</li>
     *   <li><b>140 → 147</b> le claquement, en {@code u^2,2}. En {@code u³} sur cinq ticks
     *       il tombait entre deux images — mesuré au banc ;</li>
     *   <li><b>140 → 170</b> l'éjection latérale et la cicatrice au sol.</li>
     * </ul>
     */
    private static void riftJaws(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float half, float base, float ang,
                                 float a, float scale, boolean ghost) {
        if (a <= 1.0f || half <= 0.05f) return;
        float[] pt = new float[2];
        float[] tip = new float[2];
        float[] prevTip = new float[3];
        for (int side = -1; side <= 1; side += 2) {
            boolean haveTip = false;
            prevTip[0] = 0.0f;
            prevTip[1] = 0.0f;
            for (int i = 0; i < RIFT_TEETH; i++) {
                // Les dents d'une mâchoire tombent à MI-CHEMIN de celles de l'autre : les
                // deux rangées s'imbriquent, elles ne se cognent pas. Un piège à loup se
                // reconnaît à ça avant tout.
                float shift = side > 0 ? 0.0f : 0.5f;
                float u = -1.0f + 2.0f * (i + shift) / (RIFT_TEETH - 1 + 0.5f);
                float prof = riftProfile(u);
                if (prof < 0.10f) continue;
                float ll = u * half;
                float reach = RIFT_JAW * (0.42f + 0.58f * prof);
                riftJawPoint(1.0f, ang, reach, tip);
                float tipF = side * (RIFT_HINGE + tip[0]);
                float tipY = base + tip[1];

                if (!ghost) {
                    // LA CÔTE, intégrée le long de l'arc : une mâchoire droite est une
                    // planche, c'est la courbure qui fait qu'elle cueille.
                    int steps = 7;
                    float pf = side * RIFT_HINGE;
                    float py = base;
                    for (int k = 1; k <= steps; k++) {
                        float h = k / (float) steps;
                        riftJawPoint(h, ang, reach, pt);
                        float ff = side * (RIFT_HINGE + pt[0]);
                        float yy = base + pt[1];
                        float th = (0.044f - 0.016f * h) * scale;
                        segment(vc, mat, tmp, cx, cy, cz, dx, dz, pf, ll, py, ff, ll, yy,
                                th, 0.038f,
                                (i % 2 == 0) ? SLATE : BASALT,
                                opaque(a * (0.72f + 0.28f * prof)));
                        pf = ff;
                        py = yy;
                    }
                }

                // LA DENT : un éclat qui prolonge la tangente de la pointe, et qui
                // s'allonge d'une dent sur deux — une rangée régulière fait un peigne.
                float local = ang - RIFT_CURVE;
                float grow = (i % 2 == 0) ? 1.0f : 0.68f;
                float tl = 0.40f * grow * (0.45f + 0.55f * prof);
                int tn = 3;
                for (int k = 1; k <= tn; k++) {
                    float h = k / (float) tn;
                    float sz = (0.062f - 0.014f * k) * scale;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            tipF + side * Mth.sin(local) * tl * h,
                            ll, tipY + Mth.cos(local) * tl * h,
                            sz, sz, sz,
                            k == tn ? SINGULAR : GLACIER,
                            opaque(a * (0.78f + 0.22f * prof)));
                }

                // LA LÈVRE : elle relie les pointes. Sans elle les dents flottent.
                if (haveTip) {
                    segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            prevTip[0], prevTip[1], prevTip[2],
                            tipF, ll, tipY, 0.034f * scale, 0.042f,
                            HORIZON, opaque(a * 0.80f));
                }
                prevTip[0] = tipF;
                prevTip[1] = ll;
                prevTip[2] = tipY;
                haveTip = true;
            }
        }
    }

    private static void renderRift(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float run = riftRun(t);
        float gape = riftGape(t);
        float half = riftHalfLength(t);
        float crown = riftCrown(t);
        float base = GROUND_Y + 0.03f;
        float lipMax = RIFT_LIP * gape;

        // ---- 1. LA FÊLURE AU SOL ------------------------------------------------------
        // Elle précède tout, elle survit à tout. Le tronc court le long du corridor, les
        // branches en partent avec une génération de moins — un verre ne casse pas droit.
        {
            float scar = clamp((t - RIFT_CLOSE) / 0.18f);
            float reach = RIFT_REACH * run;
            int n = 38;
            float prevF = 0.0f;
            float prevL = -reach;
            for (int i = 0; i <= n; i++) {
                float u = -1.0f + 2.0f * i / n;
                float ll = u * reach;
                float jag = (hash(salt, i * 31) - 0.5f) * 0.18f * (1.0f - Math.abs(u) * 0.4f);
                if (i > 0) {
                    segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            prevF, prevL, GROUND_Y + 0.014f, jag, ll, GROUND_Y + 0.014f,
                            0.034f, 0.048f,
                            scar > 0.5f ? BASALT : (Math.abs(u) < 0.5f ? HORIZON : HALO),
                            opaque(a * (0.92f - 0.35f * scar)));
                }
                prevF = jag;
                prevL = ll;
            }
            // Les branches. Sept, de part et d'autre, longueur décroissante vers les pointes.
            for (int b = 0; b < 7; b++) {
                float u = (hash(salt, b * 47) - 0.5f) * 1.7f;
                float side = (b & 1) == 0 ? 1.0f : -1.0f;
                float len = (0.35f + 0.85f * hash(salt, b * 59)) * (1.0f - Math.abs(u) * 0.6f);
                float ang = (0.5f + 0.7f * hash(salt, b * 71)) * side;
                float sl = u * reach;
                float ef = Mth.sin(ang) * len;
                float el = sl + Mth.cos(ang) * len * 0.5f;
                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.0f, sl, GROUND_Y + 0.014f, ef, el, GROUND_Y + 0.014f,
                        0.026f, 0.055f, scar > 0.5f ? ABYSS : SLATE, opaque(a * 0.80f));
            }
        }

        if (half > 0.05f && crown > 0.05f) {
            // ---- 2. LES DEUX MÂCHOIRES ------------------------------------------------
            // Le piège. Neuf côtes par mâchoire, intégrées le long d'un arc qui se
            // recourbe vers l'intérieur, et un bord denté dont les dents sont décalées
            // d'un demi-pas d'une mâchoire à l'autre : elles s'ENGRÈNENT, elles ne se
            // percutent pas. C'est ce décalage qui fait qu'on lit « piège » et non
            // « clapet ».
            float ang = riftJawAngle(t);
            float speed = riftJawSpeed(t);
            riftJaws(vc, mat, tmp, cx, cy, cz, dx, dz, half, base, ang, a, 1.0f, false);

            // ---- 2 bis. LES TRAÎNÉES DE VITESSE ---------------------------------------
            // Pendant le claquement le bord denté parcourt deux radians en cinq ticks :
            // sans trace, l'œil ne voit que la position d'arrivée et le coup n'existe
            // pas. On redessine donc le bord aux angles qu'il VENAIT de quitter, de plus
            // en plus pâle. Hors claquement la vitesse est nulle et il n'y a rien à payer.
            if (speed > 2.5f) {
                float ghost = clamp((speed - 2.5f) / 22.0f);
                for (int g = 1; g <= RIFT_GHOSTS; g++) {
                    float back = g * 0.0042f;
                    float pastAngle = riftJawAngle(Math.max(0.0f, t - back));
                    if (Math.abs(pastAngle - ang) < 0.012f) continue;
                    float fade = ghost * (1.0f - g / (float) (RIFT_GHOSTS + 1));
                    riftJaws(vc, mat, tmp, cx, cy, cz, dx, dz, half, base, pastAngle,
                            a * fade * 0.55f, 0.72f, true);
                }
            }

            // ---- 2 ter. LES RESSORTS --------------------------------------------------
            // Un piège à loup se tend par deux ressorts de torsion posés aux deux bouts
            // de la charnière. Ils disent l'énergie stockée : serrés quand la gueule est
            // ouverte, détendus une fois qu'elle a mordu.
            {
                float load = clamp((ang - RIFT_SHUT) / Math.max(0.1f, RIFT_OPEN - RIFT_SHUT));
                for (int end = -1; end <= 1; end += 2) {
                    float ll = end * half * 0.98f;
                    int coils = 16;
                    for (int i = 0; i < coils; i++) {
                        float v = i / (float) (coils - 1);
                        // Le pas de l'hélice se resserre avec la charge : c'est ce qui se
                        // voit, plus que le rayon.
                        float turn = v * TAU * (1.4f + 1.6f * load);
                        float rr = 0.20f * (1.0f - 0.22f * load);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                Mth.sin(turn) * rr,
                                ll + end * v * 0.16f * (1.0f - 0.4f * load),
                                base + 0.16f + Mth.cos(turn) * rr,
                                0.030f, 0.030f, 0.030f,
                                load > 0.6f ? GLACIER : HALO,
                                opaque(a * (0.55f + 0.45f * load)));
                    }
                }
            }

            // ---- 2 quater. L'ONDE DE CHOC ---------------------------------------------
            // Les deux rangées se percutent : un éclat blanc part du centre de la ligne de
            // morsure et la parcourt dans les deux sens à vitesse finie. C'est ce qui
            // donne au claquement sa TAILLE — sans lui, la mâchoire mord en silence.
            {
                float shock = t - RIFT_CLOSE - RIFT_SNAP * 0.82f;
                if (shock > 0.0f && shock < 0.10f) {
                    float front = shock * 42.0f;                 // en unités de u
                    float fade = 1.0f - clamp(shock / 0.10f);
                    for (int i = 0; i < 26; i++) {
                        float lu = -1.0f + 2.0f * i / 25.0f;
                        float prof = riftProfile(lu);
                        if (prof < 0.08f) continue;
                        float near = 1.0f - clamp(Math.abs(Math.abs(lu) - front) / 0.30f);
                        if (near <= 0.0f) continue;
                        float[] jt = new float[2];
                        riftJawPoint(1.0f, ang, RIFT_JAW * (0.42f + 0.58f * prof), jt);
                        float sz = 0.030f + 0.075f * near;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                0.0f, lu * half, base + jt[1] * 0.96f, sz, sz, sz,
                                near > 0.55f ? SINGULAR : GLACIER,
                                opaque(a * fade * (0.35f + 0.65f * near)));
                    }
                }
            }

            // ---- 3. LE SPLASH DE VOID -------------------------------------------------
            // À l'instant du contact, ce que la gueule contenait est expulsé. Chaque
            // gerbe est une parabole AVEC SA TRAÎNÉE : sans traînée, une gerbe est une
            // pluie de confettis — on voit des points, pas un jaillissement.
            {
                float bite = t - RIFT_CLOSE - RIFT_SNAP * 0.75f;
                if (bite > 0.0f && bite < 0.22f) {
                    float sec = bite * 9.0f;
                    float fade = 1.0f - clamp(bite / 0.22f);
                    for (int i = 0; i < 34; i++) {
                        int seed = i * 173;
                        float ll = (hash(salt, seed) - 0.5f) * 2.0f * half * 0.96f;
                        float prof = riftProfile(clamp(Math.abs(ll) / Math.max(0.2f, half)));
                        if (prof < 0.10f) continue;
                        float side = (i & 1) == 0 ? 1.0f : -1.0f;
                        // Vite et bas sur les côtés, haut et lent au centre : la gueule
                        // chasse la matière par ses commissures.
                        float vf = side * (1.3f + 3.1f * hash(salt, seed + 1)) * (0.45f + 0.55f * prof);
                        float vy = (2.6f + 4.2f * hash(salt, seed + 2)) * prof;
                        float vl = (hash(salt, seed + 3) - 0.5f) * 1.8f;
                        float y0 = base + RIFT_JAW * 0.42f * prof;
                        // La traînée : quatre positions en arrière sur la MÊME parabole.
                        for (int q = 3; q >= 0; q--) {
                            float ss = sec - q * 0.055f;
                            if (ss <= 0.0f) continue;
                            float yy = ballistic(y0, vy, ss, GROUND_Y + 0.04f);
                            float sz = (0.055f - q * 0.009f) * (0.5f + 0.5f * prof);
                            int rgb = q == 0
                                    ? (hash(salt, seed + 5) > 0.72f ? SINGULAR : GLACIER)
                                    : (q == 1 ? HORIZON : HALO);
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    vf * ss, ll + vl * ss, yy, sz, sz, sz, rgb,
                                    opaque(a * fade * (1.0f - q * 0.19f)));
                        }
                    }
                    // L'onde au sol : un anneau plat qui s'échappe de la ligne de morsure.
                    float ring = sec * 1.35f;
                    if (ring < 3.4f) {
                        for (int i = 0; i < 30; i++) {
                            float ll = (-1.0f + 2.0f * i / 29.0f) * half;
                            float prof = riftProfile(clamp(Math.abs(ll) / Math.max(0.2f, half)));
                            for (int side = -1; side <= 1; side += 2) {
                                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                        side * ring * (0.35f + 0.65f * prof), ll,
                                        GROUND_Y + 0.022f,
                                        0.046f, SLAB_H, 0.046f,
                                        (i % 4 == 0) ? GLACIER : HORIZON,
                                        opaque(a * fade * 0.85f * prof));
                            }
                        }
                    }
                }
            }

            // ---- 4. L'AUTRE CÔTÉ — tick 112 -------------------------------------------
            // Le seul ton FROID du dossier, et il ne sert qu'ici : ce qui remonte par la
            // fente ne vient pas du monde.
            {
                float other = clamp((t - 112.0f / 170.0f) / 0.10f) * (1.0f - clamp((t - RIFT_CLOSE) / 0.08f));
                if (other > 0.01f) {
                    for (int i = 0; i < 12; i++) {
                        float u = (hash(salt, i * 113) - 0.5f) * 1.5f;
                        float prof = riftProfile(u);
                        if (prof < 0.2f) continue;
                        float rise = fract(hash(salt, i * 127) + t * 0.9f);
                        float yy = base + crown * prof * rise;
                        float ll = u * half;
                        float ff = (hash(salt, i * 131) - 0.5f) * lipMax * prof * 1.2f;
                        float sz = 0.040f * (1.0f - rise * 0.5f);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy, sz, sz, sz,
                                CHORUS, opaque(a * other * (1.0f - rise)));
                    }
                }
            }
        }

        // ---- 5. LES NEUF ASPIRATIONS --------------------------------------------------
        // Tick 20, puis toutes les dix frames. Chacune arrache une vague des DEUX côtés du
        // corridor et la tire vers le PLAN — pas vers le centre : le serveur ne corrige que
        // la composante perpendiculaire, et entasser tout au milieu effacerait la ligne.
        for (int k = 0; k < 9; k++) {
            float when = (20.0f + k * 10.0f) / 170.0f;
            if (t < when) break;
            float age = t - when;
            if (age > 0.16f) continue;
            float u01 = clamp(age / 0.16f);

            // ---- LE COUP, à l'instant exact. Trois objets, trois échelles de temps. ----
            // (1) LA DÉCHARGE : un arc qui saute d'une lèvre à l'autre, en travers de la
            //     fente, sur toute sa longueur. Quatre frames. C'est l'éclair du coup.
            float flash = 1.0f - clamp(age / 0.024f);
            if (flash > 0.0f) {
                float[] jawTip = new float[2];
                int arcs = 5;
                for (int arc = 0; arc < arcs; arc++) {
                    float lu = (arc + 0.5f) / arcs * 1.80f - 0.90f;
                    float prof = riftProfile(lu);
                    if (prof < 0.12f) continue;
                    float ll = lu * half;
                    // L'écartement RÉEL des mâchoires à cet instant : l'arc doit partir
                    // d'une rangée de dents et arriver sur l'autre, sinon il flotte.
                    riftJawPoint(1.0f, riftJawAngle(t), RIFT_JAW * (0.42f + 0.58f * prof), jawTip);
                    float span = RIFT_HINGE + jawTip[0];
                    int bolt = 9;
                    float prevFa = -span;
                    float prevYa = base + crown * prof * (0.25f + 0.45f * hash(salt, k * 17 + arc));
                    for (int b = 1; b <= bolt; b++) {
                        float v = b / (float) bolt;
                        float jig = (hash(salt, k * 211 + arc * 13 + b) - 0.5f) * 0.30f * prof;
                        float ffa = -span + 2.0f * span * v;
                        float yya = prevYa + jig * 0.6f;
                        segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                prevFa, ll, prevYa, ffa, ll + jig * 0.5f, yya,
                                0.026f + 0.020f * flash, 0.030f,
                                flash > 0.55f ? SINGULAR : GLACIER,
                                opaque(a * flash));
                        prevFa = ffa;
                        prevYa = yya;
                    }
                }
            }

            // (2) LA GUEULE SE CRISPE. Le mouvement est maintenant porté par les
            //     mâchoires elles-mêmes (voir la morsure manquée dans riftJawAngle) ; il
            //     ne reste ici que la traîne lumineuse qui court le long du bord denté,
            //     de la pointe gauche à la pointe droite, à chaque aspiration.
            float crisp = 1.0f - clamp(age / 0.085f);
            if (crisp > 0.02f) {
                float sweep = -1.0f + 2.0f * clamp(age / 0.085f);
                for (int side = -1; side <= 1; side += 2) {
                    float[] jt = new float[2];
                    for (int i = 0; i < 13; i++) {
                        float lu = -1.0f + 2.0f * i / 12.0f;
                        float prof = riftProfile(lu);
                        if (prof < 0.10f) continue;
                        float near = 1.0f - clamp(Math.abs(lu - sweep) / 0.42f);
                        if (near <= 0.0f) continue;
                        riftJawPoint(1.0f, riftJawAngle(t),
                                RIFT_JAW * (0.42f + 0.58f * prof), jt);
                        float sz = 0.034f + 0.030f * near;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                side * (RIFT_HINGE + jt[0]), lu * half, base + jt[1],
                                sz, sz, sz, near > 0.65f ? SINGULAR : GLACIER,
                                opaque(a * crisp * near));
                    }
                }
            }

            // (3) LE CHIFFRE : une barre au sol qui s'allonge d'un cran par aspiration et
            //     RESTE. Au neuvième coup elle est pleine. Le joueur peut compter ce qu'il
            //     a pris — aucune autre signature du dossier ne le permet.
            for (int done = 0; done <= k; done++) {
                float ll = (done - 4.0f) * 0.42f;
                float fresh = done == k ? (1.0f - clamp(age / 0.10f)) : 0.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        RIFT_LIP * 1.9f, ll, GROUND_Y + 0.030f + fresh * 0.10f,
                        0.055f, 0.055f + fresh * 0.04f, 0.055f,
                        fresh > 0.3f ? SINGULAR : (done % 2 == 0 ? GLACIER : HORIZON),
                        opaque(a * (0.55f + 0.45f * fresh)));
            }
            for (int i = 0; i < 5; i++) {
                int seed = k * 311 + i * 29;
                float side = (i & 1) == 0 ? 1.0f : -1.0f;
                float f0 = side * (1.6f + 1.8f * hash(salt, seed));
                float ll = (hash(salt, seed + 7) - 0.5f) * 2.0f * RIFT_REACH * run;
                float prof = riftProfile(clamp(Math.abs(ll) / Math.max(0.2f, RIFT_REACH * run)));
                // Attraction en 1/r² intégrée grossièrement : le débris part lentement et
                // finit avalé. Une interpolation linéaire aurait l'air d'un tapis roulant.
                float pull = u01 * u01 * (3.0f - 2.0f * u01);
                float ff = f0 * (1.0f - pull);
                float yy = GROUND_Y + 0.06f + pull * (0.35f + 0.9f * prof) + 0.25f * Mth.sin(pull * 3.1f);
                if (Math.abs(ff) < lipMax * prof * 0.8f) continue;     // avalé
                fragment(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                        0.052f * (1.0f - 0.4f * pull), pull * 7.0f + i,
                        (i % 3 == 0) ? ENDSTONE : HALO, ENDSTONE_DK,
                        opaque(a * (0.45f + 0.55f * (1.0f - pull))));
            }
            // La vague au sol : la poussière glisse vers la ligne, elle ne vole pas.
            for (int i = 0; i < 7; i++) {
                float side = (i & 1) == 0 ? 1.0f : -1.0f;
                float f0 = side * (0.9f + 2.4f * hash(salt, k * 53 + i));
                float ll = (hash(salt, k * 59 + i * 3) - 0.5f) * 2.0f * RIFT_REACH * run;
                float ff = f0 * (1.0f - u01);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, GROUND_Y + 0.020f,
                        0.034f, SLAB_H, 0.034f, (i % 4 == 0) ? GLACIER : HALO,
                        opaque(a * (1.0f - u01) * 0.85f));
            }
        }

        // ---- 6. LA FERMETURE — tick 140 -----------------------------------------------
        // Les pointes se rejoignent (c'est riftZip qui raccourcit la demi-longueur), et ce
        // qui était dedans part DE CÔTÉ, en ±f, comme le voidPush du serveur.
        {
            float shut = clamp((t - RIFT_CLOSE) / 0.17f);
            if (shut > 0.0f) {
                float sec = (t - RIFT_CLOSE) * 8.5f;
                for (int i = 0; i < 14; i++) {
                    float side = (i & 1) == 0 ? 1.0f : -1.0f;
                    float ll = (hash(salt, i * 151) - 0.5f) * 2.0f * RIFT_REACH * 0.9f;
                    float speed = 1.5f + 1.6f * hash(salt, i * 157);
                    float ff = side * speed * sec;
                    float y0 = base + RIFT_HEIGHT * 0.35f * hash(salt, i * 163);
                    float yy = ballistic(y0, 1.3f, sec, GROUND_Y + 0.05f);
                    fragment(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                            0.054f, sec * 5.0f + i, (i % 3 == 0) ? INVERT : GLACIER, BASALT,
                            opaque(a * (1.0f - shut * 0.8f)));
                }
            }
        }
    }

    // =================================================================================
    //  IV. PORTE D'HORIZON — 180 ticks — elle EXPULSE
    // =================================================================================

    /**
     * <b>La porte est un TOURNIQUET.</b> Les quatre autres signatures gardent leurs victimes
     * dans la zone ; celle-ci les en SORT. Le serveur fait sept transits
     * ({@code applyHorizonGateGameplay}, ticks 34 puis toutes les dix-huit) : il prend à
     * chaque fois le corps le plus proche de l'anneau, le jette par-dessus, et le repose
     * plus loin sur un azimut qui tourne d'un septième de tour. Au bout des sept, le paquet
     * est disséminé EN ÉTOILE — et c'est exactement ce que l'image dessine.
     *
     * <h3>Le calcul</h3>
     * <ul>
     *   <li>les sept transits sont de vraies <b>paraboles</b> : vitesse de départ, pesanteur
     *       à 9, rebond amorti à l'arrivée. L'azimut avance de {@code 2π/7} par passage,
     *       donc les sept arcs se referment en étoile à sept branches ;</li>
     *   <li>au-dessus de la mare, l'air est <b>lentillé</b> : les verticales d'une cage de
     *       seize montants sont déviées en {@code −k/r}, violemment au bord du trou et
     *       presque pas au loin. C'est la même loi que la Sphère, et c'est ce qui fait
     *       qu'on lit « horizon » et pas « anneau décoratif » ;</li>
     *   <li>la fermeture du serveur dit « le rayon de l'âme se résorbe, la section tient » :
     *       c'est pris au mot — le tore se referme sur lui-même jusqu'à devenir une boule
     *       de section, qui éclate.</li>
     * </ul>
     *
     * <h3>La chronologie, en ticks du serveur</h3>
     * <ul>
     *   <li><b>0 / 5 / 10 / 15</b> le tracé au sol : quatre passes de dix motes à 2,12 de
     *       rayon, décalées d'un quart de pas — la boucle exacte du serveur ;</li>
     *   <li><b>24</b> l'ouverture ; <b>34 → 142</b> les sept transits ;</li>
     *   <li><b>150</b> la fermeture et l'onde au sol ; <b>168</b> la marque s'efface.</li>
     * </ul>
     */
    private static void renderGate(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float ring = gateRing(t);
        float floorY = GROUND_Y + 0.04f;
        float open = smooth(clamp((t - 24.0f / 180.0f) / 0.12f));
        float shut = clamp((t - GATE_SHUT) / 0.12f);
        float seedAng = hash(salt, 3) * TAU;

        // ---- 1. LE TRACÉ AU SOL — ticks 0, 5, 10, 15 ----------------------------------
        for (int step = 0; step < 4; step++) {
            float when = step * 5.0f / 180.0f;
            if (t < when) continue;
            float age = clamp((t - when) / 0.22f);
            for (int i = 0; i < 10; i++) {
                float ang = (i + step * 2.5f) / 10.0f * TAU;
                float rr = 2.12f;
                float sz = (0.055f - 0.020f * age) * (1.0f - 0.5f * open);
                if (sz <= 0.008f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.016f,
                        sz, SLAB_H, sz, step == 3 ? GLACIER : HORIZON,
                        opaque(a * (0.40f + 0.18f * step) * (1.0f - age * 0.4f)));
            }
        }

        // ---- 2. L'ANNEAU --------------------------------------------------------------
        // Posé à plat, section pleine. La fermeture résorbe l'âme et garde la section :
        // le tore devient une boule, puis rien.
        {
            float soul = ring * (1.0f - smooth(shut));
            float tube = (0.17f + 0.035f * Mth.sin(t * 13.0f)) * (0.35f + 0.65f * open) * (1.0f + shut * 0.8f);
            if (soul > 0.04f && tube > 0.01f) {
                torus(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.0f, 0.0f, floorY + tube * 0.9f, soul, tube, 0.0f, t * 3.4f,
                        a * (0.55f + 0.45f * open), salt, false);
            }
        }

        // ---- 3. LA CAGE LENTILLÉE -----------------------------------------------------
        // Seize montants au-dessus de la mare, déviés en −k/r : l'air lui-même tombe dedans.
        if (open > 0.02f && shut < 1.0f) {
            float strength = ring * ring * 0.42f * open * (1.0f - shut);
            int posts = 16;
            for (int i = 0; i < posts; i++) {
                // Chaque montant a SA phase, SA fréquence et SA vitesse de dérive : seize
                // colonnes qui ondulent ensemble se lisent comme un rideau tiré par un
                // fil, seize colonnes déphasées se lisent comme de l'air qui tombe.
                float phase = hash(salt, i * 19) * TAU;
                float wave = 0.72f + 0.55f * hash(salt, i * 23);
                float drift = (hash(salt, i * 31) - 0.5f) * 0.22f;
                float ang = i / (float) posts * TAU + t * (0.25f + drift);
                float r0 = ring * (1.04f + 0.26f * hash(salt, i * 17));
                float prevF = 0.0f;
                float prevL = 0.0f;
                float prevY = 0.0f;
                // Vingt-deux tronçons et non dix : un montant est une COURBE, et dix
                // cordes la rendent anguleuse dès que la déviation devient forte.
                int seg = 22;
                for (int h = 0; h <= seg; h++) {
                    float u = h / (float) seg;
                    float yy = floorY + u * 2.45f;
                    // Plus on est haut, moins le trou tire : la déviation décroît en 1/(1+y²).
                    float bend = strength / Math.max(0.5f, r0) / (1.0f + yy * yy * 0.55f);
                    float rr = Math.max(ring * 0.22f, r0 - bend);
                    // L'ONDULATION. Elle monte le long du montant (u décalé dans le temps),
                    // elle est nulle au pied — le montant est planté dans le sol — et
                    // maximale en haut, où rien ne le retient.
                    // L'ONDULATION, pour de bon. Elle valait 0,17 bloc sur un montant de
                    // 2,45 : sept pour cent, donc rien. Trois choses la rendent enfin
                    // visible et ASYMÉTRIQUE :
                    //   — deux harmoniques de périodes non commensurables, d'amplitudes
                    //     inégales : la courbe ne se répète jamais à l'œil et n'a pas
                    //     d'axe de symétrie ;
                    //   — une enveloppe en u^1,35 : nulle au pied, mais qui part plus
                    //     tard et monte plus fort qu'une rampe linéaire ;
                    //   — et surtout une BOSSE QUI MONTE, renflement gaussien remontant
                    //     le long du montant et sortant par le haut. C'est elle qu'on
                    //     appelle une vague, et c'est elle qui manquait.
                    float env = (float) Math.pow(u, 1.35);
                    float travel = fract(t * 0.31f + i * 0.0625f + hash(salt, i * 37));
                    float bump = (u - travel) * 3.6f;
                    bump = (float) Math.exp(-bump * bump) * (0.55f + 0.45f * wave);
                    float swirl = (Mth.sin(phase + u * 5.6f * wave - t * 3.1f) * 0.62f
                            + Mth.sin(phase * 1.7f + u * 2.21f * wave + t * 1.9f) * 0.31f)
                            * env + bump * 0.46f;
                    float lateral = (Mth.cos(phase + u * 4.3f * wave - t * 2.6f) * 0.42f
                            + Mth.cos(phase * 0.6f + u * 1.63f * wave - t * 1.3f) * 0.25f)
                            * env + bump * 0.22f;
                    float ff = Mth.cos(ang + lateral * 0.5f) * (rr + swirl);
                    float ll = Mth.sin(ang + lateral * 0.5f) * (rr + swirl);
                    if (h > 0) {
                        float pull = clamp(bend / 0.9f);
                        // Pas de 0,026 : STRICTEMENT sous la taille du cube (0,028 à
                        // 0,048), donc les cubes se recouvrent et le montant est un trait
                        // plein. C'est le défaut que montraient les captures — un pas plus
                        // grand que le cube donne un pointillé, quoi qu'on fasse par ailleurs.
                        segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                prevF, prevL, prevY, ff, ll, yy,
                                0.028f + 0.020f * pull, 0.026f,
                                pull > 0.55f ? HORIZON : (pull > 0.25f ? HALO : SLATE),
                                opaque(a * (0.42f + 0.52f * pull) * open * (1.0f - shut)));
                    }
                    prevF = ff;
                    prevL = ll;
                    prevY = yy;
                }
                // LA CRÊTE qui remonte le montant : un point vif qui monte en boucle, pour
                // que l'œil voie le SENS du courant. Sans lui l'ondulation est ambiguë.
                float crest = fract(t * 0.42f + i * 0.0625f);
                float cy2 = floorY + crest * 2.45f;
                float cbend = strength / Math.max(0.5f, r0) / (1.0f + cy2 * cy2 * 0.55f);
                float crr = Math.max(ring * 0.22f, r0 - cbend)
                        + Mth.sin(phase + crest * 5.6f * wave - t * 3.1f) * 0.17f * crest;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * crr, Mth.sin(ang) * crr, cy2,
                        0.042f, 0.042f, 0.042f, GLACIER,
                        opaque(a * open * (1.0f - shut) * (1.0f - crest * 0.55f)));
            }
        }

        // ---- 4. LES SEPT TRANSITS -----------------------------------------------------
        // Pris, avalé, recraché. L'azimut avance d'un septième de tour par passage ; les
        // sept arcs dessinent l'étoile que le serveur dessine avec des corps.
        for (int pass = 0; pass < 7; pass++) {
            float when = GATE_PASS0 + pass * GATE_PASS_STEP;
            if (t < when - 0.035f) break;
            float ang = seedAng + pass * (TAU / 7.0f);
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            float inAng = ang + 2.6f;
            float age = t - when;
            // -- phase A : le corps est traîné jusqu'à la lèvre, puis bascule --
            if (age < 0.0f) {
                float u = clamp((age + 0.035f) / 0.035f);
                float rr = ring * (1.9f - 0.9f * u);
                fragment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(inAng) * rr, Mth.sin(inAng) * rr,
                        floorY + 0.22f - 0.18f * u, 0.085f, u * 5.0f,
                        ENDSTONE, ENDSTONE_DK, opaque(a * 0.9f));
                continue;
            }
            // -- phase B : avalé. La mare encaisse, l'anneau palpite --
            if (age < 0.030f) {
                float u = age / 0.030f;
                for (int i = 0; i < 9; i++) {
                    float aa = i / 9.0f * TAU + u * 5.0f;
                    float rr = ring * (0.25f + 0.70f * u);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(aa) * rr, Mth.sin(aa) * rr, floorY + 0.06f + u * 0.25f,
                            0.045f, 0.045f, 0.045f, SINGULAR, opaque(a * (1.0f - u)));
                }
                continue;
            }
            // -- phase C : la parabole. Vraies cotes : 3,5 bl/s en avant, 4,4 bl/s en haut,
            //    pesanteur 9 — apogée à une demi-seconde, poser à trois mètres. --
            float sec = (age - 0.030f) * 9.0f;
            float dist = 3.5f * sec;
            float yy = ballistic(floorY + 0.10f, 4.4f, sec, floorY + 0.05f);
            float ff = ca * dist;
            float ll = sa * dist;
            float life = 1.0f - clamp((sec - 1.9f) / 0.8f);
            if (life <= 0.01f) continue;
            fragment(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                    0.082f, sec * 6.5f + pass, INVERT, BASALT, opaque(a * life));
            // La traînée : l'arc déjà parcouru, en motes qui s'éteignent.
            int trail = 9;
            for (int i = 1; i <= trail; i++) {
                float back = sec - i * 0.085f;
                if (back <= 0.0f) break;
                float by = ballistic(floorY + 0.10f, 4.4f, back, floorY + 0.05f);
                float bd = 3.5f * back;
                float fade = (1.0f - i / (float) (trail + 1)) * life;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ca * bd, sa * bd, by,
                        0.030f, 0.030f, 0.030f, (i & 1) == 0 ? INVERT : HALO,
                        opaque(a * fade * 0.75f));
            }
        }

        // ---- 5. LA FERMETURE — tick 150 -----------------------------------------------
        if (shut > 0.0f) {
            // L'onde au sol : ce que le voidPush radial fait aux corps, en poussière.
            float rr = 0.4f + 3.0f * smooth(shut);
            int n = Math.max(24, Math.round(rr * 18.0f));
            for (int i = 0; i < n; i++) {
                float ang = i / (float) n * TAU;
                float wob = 1.0f + 0.05f * Mth.sin(ang * 6.0f + t * 11.0f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr * wob, Mth.sin(ang) * rr * wob, GROUND_Y + 0.016f,
                        0.046f, SLAB_H, 0.046f, (i % 6 == 0) ? SINGULAR : HORIZON,
                        opaque(a * (1.0f - shut) * 0.90f));
            }
            // Et la boule de section qui reste quand l'âme a disparu.
            float core = (1.0f - shut) * 0.42f;
            if (core > 0.03f) {
                for (int i = 0; i < 16; i++) {
                    float aa = i * 2.39996f + t * 7.0f;
                    float el = (hash(salt, i * 23) - 0.5f) * 2.4f;
                    float rr2 = core * Mth.cos(el);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(aa) * rr2, Mth.sin(aa) * rr2, floorY + 0.2f + core * Mth.sin(el),
                            0.040f, 0.040f, 0.040f, (i & 1) == 0 ? GLACIER : HORIZON,
                            opaque(a * (1.0f - shut)));
                }
            }
        }
    }

    // =================================================================================
    //  V. CONSTELLATION NULLE — 200 ticks — elle LIE
    // =================================================================================

    /**
     * <b>La trame est un CONTRAT, et un contrat se tend.</b> Le serveur capture jusqu'à sept
     * corps À LA POSE, puis serre ONZE FOIS ({@code applyNullLatticeGameplay}, ticks 40 à
     * 140), converge au 160 et ROMPT au 184. Chaque tension frappe tout le monde pour une
     * petite part : le total par corps est modeste, le total sur le groupe est énorme.
     *
     * <h3>Le calcul — c'est une vraie simulation</h3>
     *
     * <p>Les sept nœuds ne sont pas interpolés entre deux poses : ils sont <b>intégrés</b>.
     * À chaque image on rejoue le système masses-ressorts depuis zéro, à pas fixe
     * (1/240 d'effet, soit 24 pas par seconde de jeu) — onze ressorts, un amortissement, et
     * une impulsion vers le barycentre à chacune des onze tensions, aux ticks exacts du
     * serveur. Rejouer depuis le début à chaque image coûte huit cents itérations et rend
     * le résultat <b>déterministe</b> : pas d'état à garder entre deux frames, et la même
     * image au même {@code t}, toujours.</p>
     *
     * <p>Ce que ça donne à l'œil, et qu'aucune interpolation ne donne : la trame
     * <b>oscille</b>. Chaque tension la fait sonner, elle dépasse sa consigne, revient,
     * et la suivante arrive avant qu'elle soit calmée. Les arêtes encore molles pendent en
     * <b>chaînette</b> ; les arêtes tendues vibrent et blanchissent à proportion de leur
     * <b>déformation</b> — on voit laquelle porte. À la rupture, chacune casse par le
     * milieu et les deux moitiés se rétractent en fouet.</p>
     */
    /**
     * V. L'EFFONDREMENT EN CHAÎNE — 200 ticks. Elle PROPAGE.
     *
     * <p><b>Ce qu'il faut comprendre en la regardant, sans légende :</b> que ça court d'un
     * corps à l'autre, que chaque étape est plus grosse que la précédente, et qu'il reste un
     * compte à rebours avant que ça saute. Les trois sont des objets séparés à l'écran — le
     * fil, les implosions, l'anneau qui se referme — et aucun n'a besoin des deux autres pour
     * se lire.</p>
     *
     * <p><b>Ce qui n'est pas repris des quatre autres signatures.</b> Pas de traction vers un
     * centre (la Sphère), pas de faisceaux simultanés (le Prisme), pas de zone en corridor
     * (la Faille), pas de trou fixe (la Porte). La chaîne est la seule figure du Vide qui
     * SE DÉPLACE : son centre d'intérêt n'est jamais deux fois au même endroit.</p>
     *
     * <p><b>Les cubes d'abysse.</b> L'anneau de compte à rebours et la coquille d'implosion
     * ne sont pas des voxels teintés : ce sont de vrais cubes remplis par le core shader du
     * portail, posés par {@link VoidPortalFx#queueCube}. C'est la seule façon d'avoir la
     * texture à couches sur un volume plutôt que sur une surface, et ça évite de percer une
     * fenêtre dans une figure qui n'a pas d'intérieur.</p>
     */
    private static void renderChain(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    double cx, double cy, double cz, float t,
                                    float dx, float dz, float alpha, int salt) {
        float a = alpha;
        float floorY = GROUND_Y;
        float[] st = new float[3];
        float[] nx = new float[3];

        // ---- 1. LE CIRCUIT : le fil qui court, et qui reste ---------------------------
        // Un maillon sur deux dessine son segment vers le suivant. Avant le départ, rien ;
        // pendant le saut, une tête vive qui avance ; après, un fil mince qui PULSE — le
        // circuit est chargé, et sa charge remonte à chaque nouvelle implosion.
        for (int i = 0; i + 1 < CHAIN_LINKS; i++) {
            float leave = chainFire(i);
            if (t < leave) break;
            chainStation(i, salt, st);
            chainStation(i + 1, salt, nx);
            float run = clamp((t - leave) / CHAIN_TRAVEL);
            float live = smooth(run);

            // La tête : elle va d'une station à l'autre en huit ticks, et elle est le seul
            // objet de la figure qui se déplace à vitesse constante. Tout le reste pulse.
            int steps = 22;
            for (int k = 1; k <= steps; k++) {
                float u = k / (float) steps;
                if (u > live) break;
                float ff = st[0] + (nx[0] - st[0]) * u;
                float ll = st[1] + (nx[1] - st[1]) * u;
                // Le fil ne va pas droit : il s'affaisse, comme une décharge qui cherche
                // son chemin. L'écart est latéral ET vertical, et il se résorbe derrière.
                float sag = Mth.sin(u * TAU * 1.5f + i * 2.1f) * 0.22f * (1.0f - u * 0.4f);
                float yy = st[2] + (nx[2] - st[2]) * u + Mth.sin(u * Mth.PI) * 0.30f + sag * 0.5f;
                float side = Mth.cos(u * TAU * 1.5f + i * 2.1f) * 0.16f;
                float head = 1.0f - clamp((live - u) / 0.22f);          // 1 sur la tête
                float age = clamp((t - leave - CHAIN_TRAVEL) / 0.10f);  // 1 une fois posé
                float th = (0.020f + 0.055f * head) * (1.0f - 0.45f * age);
                float beat = 0.55f + 0.45f * Mth.sin(t * 26.0f - u * 7.0f + i);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        ff - (nx[1] - st[1]) * 0.0f + side * (nx[1] - st[1]) * 0.25f,
                        ll + side,
                        yy, th, th, th,
                        head > 0.60f ? SINGULAR : (head > 0.22f ? GLACIER : HORIZON),
                        opaque(a * (0.30f + 0.70f * head) * (0.55f + 0.45f * beat)));
            }
        }

        // ---- 2. LES STATIONS ----------------------------------------------------------
        for (int i = 0; i < CHAIN_LINKS; i++) {
            float arrive = chainArrive(i);
            if (t < arrive) break;
            float fire = chainFire(i);
            chainStation(i, salt, st);
            float sf = st[0];
            float sl = st[1];
            float sy = st[2];

            if (t < fire) {
                // ---- 2a. LE COMPTE À REBOURS ------------------------------------------
                // Quatorze cubes d'abysse en anneau, qui tournent de plus en plus vite et
                // se referment sur le corps. Le rayon EST le temps restant : c'est la
                // seule horloge lisible du dossier.
                float w = clamp((t - arrive) / Math.max(1.0e-4f, fire - arrive));
                float r = (1.25f + i * 0.10f) * (1.0f - smooth(w)) + 0.12f;
                float spin = t * (6.0f + 16.0f * w) + i * 1.3f;
                int ring = 14;
                for (int k = 0; k < ring; k++) {
                    float ang = k * TAU / ring + spin;
                    float ca = Mth.cos(ang);
                    float sa = Mth.sin(ang);
                    float tilt = Mth.sin(ang * 2.0f + t * 3.0f) * 0.22f * (1.0f - w);
                    float half = 0.055f + 0.030f * w;
                    VoidPortalFx.queueCube(cx + (sf + ca * r) * dx - (sl + sa * r) * dz,
                            cy + sy + tilt,
                            cz + (sf + ca * r) * dz + (sl + sa * r) * dx,
                            half, ang * 0.7f + t);
                    // Une monture voxel sombre derrière chaque cube : sur fond clair, un
                    // cube de portail seul n'a pas de bord et bave dans le décor.
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, sf + ca * r, sl + sa * r, sy + tilt,
                            half * 1.18f, half * 1.18f, half * 1.18f,
                            NOTHING, opaque(a * 0.85f));
                }
                // Le CERCLE AU SOL sous la station armée : le joueur qui regarde ses pieds
                // doit savoir qu'il est le prochain, même si l'anneau est hors de vue.
                float groundR = r * 1.35f;
                int marks = 24;
                for (int k = 0; k < marks; k++) {
                    float ang = k * TAU / marks - spin * 0.4f;
                    float blink = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(t * 22.0f + k * 0.9f));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            sf + Mth.cos(ang) * groundR, sl + Mth.sin(ang) * groundR,
                            floorY + 0.012f, 0.040f, SLAB_H, 0.040f,
                            blink > 0.75f ? HORIZON : HALO, opaque(a * blink * (0.35f + 0.65f * w)));
                }
                // Les trois BATTEMENTS du serveur : à chaque tiers du compte à rebours un
                // anneau d'onde part du corps. Ce sont les trois chimes qu'on entend.
                for (int beat = 0; beat < 3; beat++) {
                    float when = arrive + (fire - arrive) * beat / 3.0f;
                    float age = (t - when) / 0.055f;
                    if (age < 0.0f || age > 1.0f) continue;
                    float rr = 0.25f + age * 1.5f;
                    int n = 18;
                    for (int k = 0; k < n; k++) {
                        float ang = k * TAU / n;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                sf + Mth.cos(ang) * rr, sl + Mth.sin(ang) * rr, sy,
                                0.030f, 0.030f, 0.030f, GLACIER,
                                opaque(a * (1.0f - age) * 0.75f));
                    }
                }
            } else {
                // ---- 2b. L'IMPLOSION, puis le cratère ---------------------------------
                float age = (t - fire) * 200.0f / 26.0f;   // 1 au bout de 26 ticks
                float blast = chainBlast(i);

                if (age < 1.0f) {
                    // La COQUILLE qui s'effondre. Elle part du rayon de souffle du serveur
                    // et tombe au centre en (1-u)^3 : lent d'abord, brutal à la fin.
                    float fall = 1.0f - age;
                    float r = blast * fall * fall * fall;
                    int shell = 26 + i * 3;
                    for (int k = 0; k < shell; k++) {
                        float u = (k + 0.5f) / shell;
                        float phi = (float) Math.acos(1.0f - 2.0f * u);
                        float theta = k * 2.39996f + t * 1.2f;
                        float sinp = Mth.sin(phi);
                        float px = Mth.cos(theta) * sinp * r;
                        float pz = Mth.sin(theta) * sinp * r;
                        float py = Mth.cos(phi) * r * 0.80f;
                        float half = 0.045f + 0.045f * age;
                        VoidPortalFx.queueCube(cx + (sf + px) * dx - (sl + pz) * dz,
                                cy + sy + py,
                                cz + (sf + px) * dz + (sl + pz) * dx, half, theta);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, sf + px, sl + pz, sy + py,
                                half * 1.15f, half * 1.15f, half * 1.15f,
                                NOTHING, opaque(a * (0.55f + 0.45f * age)));
                    }
                    // Le FLASH : une sphère d'arêtes qui part en sens inverse, pour que
                    // l'effondrement ne se lise pas comme une simple disparition.
                    float flash = clamp((age - 0.62f) / 0.38f);
                    if (flash > 0.0f) {
                        float fr = blast * 0.55f * flash;
                        for (int ringIdx = 0; ringIdx < 3; ringIdx++) {
                            int n = 30;
                            for (int k = 0; k < n; k++) {
                                float ang = k * TAU / n;
                                float ca = Mth.cos(ang) * fr;
                                float sa = Mth.sin(ang) * fr;
                                float px = ringIdx == 0 ? ca : (ringIdx == 1 ? ca : 0.0f);
                                float py = ringIdx == 0 ? sa : (ringIdx == 1 ? 0.0f : ca);
                                float pz = ringIdx == 0 ? 0.0f : (ringIdx == 1 ? sa : sa);
                                box(vc, mat, tmp, cx, cy, cz, dx, dz, sf + px, sl + pz, sy + py,
                                        0.030f, 0.030f, 0.030f,
                                        flash > 0.55f ? GLACIER : SINGULAR,
                                        opaque(a * (1.0f - flash) * 0.9f));
                            }
                        }
                    }
                }

                // L'ONDE AU SOL. Elle court jusqu'au rayon de souffle du serveur et
                // s'arrête là : le joueur peut mesurer la zone qui vient de le toucher.
                float wave = (t - fire) * 200.0f / 34.0f;
                if (wave < 1.0f) {
                    float rr = blast * smooth(wave);
                    int n = 44 + i * 4;
                    for (int k = 0; k < n; k++) {
                        float ang = k * TAU / n + hash(salt, i * 7 + k) * 0.12f;
                        float jag = 1.0f + (hash(salt, k * 13 + i) - 0.5f) * 0.10f;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                sf + Mth.cos(ang) * rr * jag, sl + Mth.sin(ang) * rr * jag,
                                floorY + 0.014f, 0.055f, SLAB_H, 0.055f,
                                (k & 3) == 0 ? GLACIER : HORIZON,
                                opaque(a * (1.0f - wave) * 0.95f));
                    }
                }

                // LE CRATÈRE. Il reste jusqu'à la fin : à la dernière image on doit pouvoir
                // compter les stations parcourues en regardant le sol.
                {
                    float scar = clamp((t - fire) * 200.0f / 12.0f);
                    int n = 20;
                    for (int k = 0; k < n; k++) {
                        float ang = hash(salt, i * 101 + k) * TAU;
                        float rr = chainBlast(i) * (0.25f + 0.55f * hash(salt, i * 103 + k));
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                sf + Mth.cos(ang) * rr, sl + Mth.sin(ang) * rr,
                                floorY + 0.008f, 0.050f, SLAB_H * 0.7f, 0.050f,
                                (k & 1) == 0 ? BASALT : SLATE, opaque(a * scar * 0.80f));
                    }
                }

                // LES DÉBRIS arrachés par l'implosion. Quatre par station, en vraie
                // balistique : ils retombent et rebondissent une fois.
                for (int k = 0; k < 4; k++) {
                    float sec = (t - fire) * 10.0f;
                    float ang = hash(salt, i * 57 + k * 11) * TAU;
                    float speed = 1.7f + 1.5f * hash(salt, i * 59 + k * 13);
                    float rr = speed * sec * 0.55f;
                    float yy = ballistic(sy, 3.4f + 2.0f * hash(salt, i * 61 + k), sec,
                            floorY + 0.06f);
                    float sz = 0.055f + 0.030f * hash(salt, i * 67 + k);
                    fragment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            sf + Mth.cos(ang) * rr, sl + Mth.sin(ang) * rr, yy,
                            sz, sec * 2.4f + k, ENDSTONE, ENDSTONE_DK,
                            opaque(a * (1.0f - clamp((t - fire) / 0.30f) * 0.45f)));
                }
            }
        }

        // ---- 3. LA DÉTONATION FINALE --------------------------------------------------
        // Elle paie la longueur de la chaîne. Deux temps : le circuit se VIDE vers la
        // dernière station (une onde qui remonte le fil à l'envers, très vite), puis la
        // sphère part. Sans le premier temps, la détonation sort de nulle part.
        {
            float last = chainFire(CHAIN_LINKS - 1);
            float drain = (t - last) * 200.0f / 10.0f;
            if (drain > 0.0f && drain < 1.0f) {
                float u = 1.0f - drain;      // on remonte de la fin vers le début
                for (int i = 0; i + 1 < CHAIN_LINKS; i++) {
                    chainStation(i, salt, st);
                    chainStation(i + 1, salt, nx);
                    float seg = (CHAIN_LINKS - 2 - i) / (float) (CHAIN_LINKS - 1);
                    float local = clamp((u - seg) * (CHAIN_LINKS - 1));
                    if (local <= 0.0f || local >= 1.0f) continue;
                    float ff = st[0] + (nx[0] - st[0]) * local;
                    float ll = st[1] + (nx[1] - st[1]) * local;
                    float yy = st[2] + (nx[2] - st[2]) * local + Mth.sin(local * Mth.PI) * 0.30f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                            0.075f, 0.075f, 0.075f, SINGULAR, opaque(a));
                }
            }

            float boom = (t - last - 10.0f / 200.0f) * 200.0f / 30.0f;
            if (boom > 0.0f) {
                chainStation(CHAIN_LINKS - 1, salt, st);
                float grow = smooth(clamp(boom));
                float fade = 1.0f - clamp((boom - 0.55f) / 0.45f);
                // Le rayon suit la règle du serveur : 2,0 + 0,30 x maillons.
                float r = (2.0f + 0.30f * CHAIN_LINKS) * grow;
                if (fade > 0.01f) {
                    int n = 54;
                    for (int k = 0; k < n; k++) {
                        float u = (k + 0.5f) / n;
                        float phi = (float) Math.acos(1.0f - 2.0f * u);
                        float theta = k * 2.39996f + t * 0.8f;
                        float sinp = Mth.sin(phi);
                        float px = Mth.cos(theta) * sinp * r;
                        float pz = Mth.sin(theta) * sinp * r;
                        float py = Mth.cos(phi) * r * 0.75f;
                        float half = 0.075f * (1.0f - grow * 0.4f);
                        VoidPortalFx.queueCube(cx + (st[0] + px) * dx - (st[1] + pz) * dz,
                                cy + st[2] + py,
                                cz + (st[0] + px) * dz + (st[1] + pz) * dx, half, theta);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, st[0] + px, st[1] + pz, st[2] + py,
                                half * 1.2f, half * 1.2f, half * 1.2f,
                                NOTHING, opaque(a * fade));
                    }
                    // La colonne d'éjection : le serveur pousse radialement ET vers le
                    // haut, alors la figure montre les deux.
                    int cols = 16;
                    for (int k = 0; k < cols; k++) {
                        float ang = k * TAU / cols + t * 0.6f;
                        float rr = r * (0.55f + 0.45f * hash(salt, k * 23));
                        for (float h = 0.0f; h < 1.0f; h += 0.14f) {
                            float yy = st[2] + h * (1.6f + 0.9f * grow);
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    st[0] + Mth.cos(ang) * rr * (1.0f - h * 0.35f),
                                    st[1] + Mth.sin(ang) * rr * (1.0f - h * 0.35f), yy,
                                    0.040f, 0.040f, 0.040f, INVERT,
                                    opaque(a * fade * (1.0f - h) * 0.85f));
                        }
                    }
                }
            }
        }
    }

    private static void segment(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                float cx, float cy, float cz, float dx, float dz,
                                float f0, float l0, float y0,
                                float f1, float l1, float y1,
                                float w, int rgb, int alpha) {
        segment(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0, f1, l1, y1,
                w, Math.max(0.030f, w * 1.15f), rgb, alpha);
    }

    /**
     * Même segment, mais avec un <b>pas</b> explicite entre deux cubes.
     *
     * <p>La grille de la Sphère s'en sert pour une raison qui n'est pas une optimisation :
     * <b>elle est dense là où l'espace est dense</b>. Loin de la lentille, l'espace n'est pas
     * perturbé, les tiges sont donc pointillées ; près de l'horizon, où tout se tasse, les
     * cubes se touchent. On lit la compression de l'espace dans la densité du trait, en plus
     * de la lire dans sa courbure — et au passage le coût tombe de 3 000 cubes à 1 500.</p>
     */
    private static void segment(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                float cx, float cy, float cz, float dx, float dz,
                                float f0, float l0, float y0,
                                float f1, float l1, float y1,
                                float w, float pitch, int rgb, int alpha) {
        if (alpha <= 0 || w <= 0.0f) return;
        float df = f1 - f0;
        float dl = l1 - l0;
        float dy = y1 - y0;
        float len = Mth.sqrt(df * df + dl * dl + dy * dy);
        if (len < 0.0005f) return;
        // PLANCHER D'ESPACEMENT. Sans lui (pas = w x 1,15), une tige qui s'amincit en
        // disparaissant voit son pas tomber avec son epaisseur : LE FONDU RAJOUTE DES
        // CUBES. Mesure sur le miroir : 5 195 a la derniere image contre 598 au milieu.
        int n = Math.max(1, Math.min(26, Math.round(len / Math.max(0.030f, pitch))));
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f0 + df * u, l0 + dl * u, y0 + dy * u, w, w, w, rgb, alpha);
        }
    }

    /**
     * La SPAGHETTIFICATION : un bloc du monde qui approche de l'horizon, étiré le long du
     * rayon et aminci jusqu'à rien.
     *
     * <p>C'est la règle n° 3 de l'élément rendue littérale. La chaîne compte cinq maillons
     * dont l'écartement croît et la section décroît en {@code (r/r₀)^1,6} : à l'horizon la
     * section est nulle, donc <b>rien ne traverse</b>. Un bloc qui arriverait entier et
     * disparaîtrait d'un coup se lirait comme un défaut d'affichage.</p>
     */
    private static void spaghetti(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float ang, float r, float y, float rs, float size,
                                  float alpha, int idx) {
        if (r <= rs * 0.98f) return;
        float over = clamp((r - rs) / 1.9f);           // 0 juste au bord, 1 au loin
        float stretch = 1.0f + 2.6f * (1.0f - over) * (1.0f - over);
        float thin = (float) Math.pow(over, 1.6);
        int links = 5;
        for (int k = 0; k < links; k++) {
            float back = k * 0.085f * stretch;
            float rr = r + back;
            float sz = size * thin * (1.0f - k * 0.13f);
            if (sz <= 0.006f) continue;
            // La section s'écrase dans le sens tangentiel : étiré en long, comprimé en large.
            float along = sz * (0.6f + 1.9f * (1.0f - over));
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * rr, Mth.sin(ang) * rr, y,
                    along, sz * 0.75f, sz * 0.75f,
                    k == 0 ? (over < 0.22f ? GLACIER : ENDSTONE)
                           : (((idx + k) & 1) == 0 ? ENDSTONE : ENDSTONE_DK),
                    opaque(alpha * (1.0f - k * 0.10f)));
        }
    }


    /**
     * Un TORE : un anneau tubulaire, âme de rayon {@code ring}, section de rayon
     * {@code tube}, éventuellement incliné de {@code tilt} autour de l'axe {@code f}.
     *
     * <p><b>Le repère est explicite, et ce n'est pas du zèle.</b> Construire la section sur
     * les axes du monde fait VRILLER le tube : la section tourne avec l'angle au lieu de
     * rester perpendiculaire à l'âme, et l'anneau prend l'aspect d'un ruban tordu. Les trois
     * vecteurs — tangente, normale radiale, verticale locale — sont donc recalculés à chaque
     * pas, exactement comme pour la couronne du Prisme.</p>
     *
     * @param dashed section tracée en quatre points au lieu de huit : le tore du haut est
     *               plus petit et plus loin, un tube plein à cette taille redevient un trait
     */
    private static void torus(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float of, float ol, float oy,
                              float ring, float tube, float tilt, float phase,
                              float alpha, int salt, boolean dashed) {
        if (ring <= 0.01f || tube <= 0.003f) return;
        int around = Math.max(24, Math.min(64, Math.round(ring * 26.0f)));
        int section = dashed ? 4 : 8;
        float ct = Mth.cos(tilt);
        float st = Mth.sin(tilt);
        for (int i = 0; i < around; i++) {
            float ang = i / (float) around * TAU;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            // Centre de la section, dans le plan incliné autour de l'axe f.
            float rf = ca * ring;
            float rl = sa * ring * ct;
            float ry = sa * ring * st;
            // Repère local : normale radiale (vers l'extérieur) et verticale du plan.
            float nfx = ca,            nlx = sa * ct,  nyx = sa * st;
            float vfx = 0.0f,          vlx = -st,      vyx = ct;
            // Scintillement : la crête du tube est vive, le ventre est mort. Sans ce
            // contraste, un tore est un beignet uniforme et ne tourne visiblement pas.
            for (int s = 0; s < section; s++) {
                float sang = s / (float) section * TAU + phase + ang * 0.5f;
                float cs = Mth.cos(sang);
                float ss = Mth.sin(sang);
                float f = of + rf + (nfx * cs + vfx * ss) * tube;
                float l = ol + rl + (nlx * cs + vlx * ss) * tube;
                float y = oy + ry + (nyx * cs + vyx * ss) * tube;
                boolean crest = ss > 0.35f;
                float flick = 0.74f + 0.26f * Mth.sin(ang * 5.0f + phase * 3.0f + s * 1.1f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        0.030f, 0.030f, 0.030f,
                        crest ? (flick > 0.93f ? GLACIER : HORIZON)
                              : (((i + s) & 1) == 0 ? BASALT : ABYSS),
                        opaque(alpha * (crest ? flick : 0.85f)));
            }
        }
        // L'ARÊTE INTÉRIEURE. Un seul fil clair sur le bord du trou : c'est lui qui dit
        // qu'il y a un TROU, et non un disque sombre au milieu d'un anneau clair.
        int lipN = Math.max(20, Math.round(ring * 22.0f));
        for (int i = 0; i < lipN; i++) {
            float ang = i / (float) lipN * TAU;
            float rr = ring - tube * 0.92f;
            if (rr <= 0.02f) break;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            float pulse = 0.68f + 0.32f * Mth.sin(ang * 3.0f - phase * 4.0f);
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    of + ca * rr, ol + sa * rr * ct, oy + sa * rr * st,
                    0.024f, 0.024f, 0.024f,
                    pulse > 0.90f ? SINGULAR : GLACIER, opaque(alpha * pulse));
        }
    }

    /**
     * Un FRAGMENT du monde : trois cubes assemblés et tournés, pas un cube isolé.
     *
     * <p>Un cube seul qui tombe est un pixel qui tombe. Trois cubes décalés et inclinés
     * lisent comme un éclat de matière, et leur rotation prouve qu'ils sont EN CHUTE et non
     * en translation.</p>
     */
    private static void fragment(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f, float l, float y, float size, float roll,
                                 int bright, int dark, int alpha) {
        if (size <= 0.008f) return;
        float c = Mth.cos(roll);
        float s = Mth.sin(roll);
        for (int k = 0; k < 3; k++) {
            float off = (k - 1) * size * 0.78f;
            float ox = off * c;
            float oy = off * s * 0.55f;
            float oz = off * s;
            float sz = size * (k == 1 ? 1.0f : 0.72f);
            box(vc, mat, tmp, cx, cy, cz, dx, dz, f + ox, l + oz, y + oy,
                    sz, sz, sz, k == 1 ? bright : dark, alpha);
        }
    }

    /**
     * Un NŒUD de la constellation : un octaèdre noir hérissé de six pointes claires.
     *
     * <p>Le cœur est en {@link #NOTHING} — du noir pur, qui serait invisible seul. Les six
     * pointes sont son liseré obligatoire, et elles tournent : c'est à elles qu'on voit que
     * le nœud existe, et à leur longueur qu'on voit s'il est encore vivant.</p>
     */
    private static void node(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f, float l, float y, float size, float roll,
                             float collapse, float alpha) {
        if (size <= 0.004f) return;
        float fade = 1.0f - collapse * 0.75f;
        // Le cœur : quatre cubes en losange, pas un cube. Un cube seul redeviendrait un
        // élément de la grille de la Sphère, et les deux signatures se confondraient.
        for (int k = 0; k < 4; k++) {
            float ang = k * (TAU / 4.0f) + roll;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f + Mth.cos(ang) * size * 0.46f, l + Mth.sin(ang) * size * 0.46f, y,
                    size * 0.52f, size * 0.52f, size * 0.52f, NOTHING, opaque(alpha * fade));
        }
        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                size * 0.40f, size * 0.90f, size * 0.40f, ABYSS, opaque(alpha * fade));
        // Les six pointes. Elles se rétractent à l'effondrement : le nœud rentre en
        // lui-même avant de se superposer aux autres.
        float spike = size * (1.55f + 0.45f * Mth.sin(roll * 1.7f)) * (1.0f - collapse);
        for (int s = 0; s < 6; s++) {
            float sf = (s == 0 ? 1 : s == 1 ? -1 : 0) * spike;
            float sl = (s == 2 ? 1 : s == 3 ? -1 : 0) * spike;
            float sy = (s == 4 ? 1 : s == 5 ? -1 : 0) * spike;
            // Rotation des pointes autour de la verticale, pour les quatre horizontales.
            if (s < 4) {
                float c = Mth.cos(roll);
                float sn = Mth.sin(roll);
                float rf = sf * c - sl * sn;
                sl = sf * sn + sl * c;
                sf = rf;
            }
            segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f, l, y, f + sf, l + sl, y + sy,
                    size * 0.26f, 0.040f,
                    s >= 4 ? GLACIER : HORIZON, opaque(alpha * 0.92f * fade));
        }
    }

    // =================================================================================
    //  CALCUL — ce que les quatre signatures ont en commun
    // =================================================================================

    /**
     * Repère du cristal : inclinaison de {@code tilt} autour de l'axe {@code f}, puis
     * rotation de {@code spin} autour de la verticale. Les six sommets, les douze arêtes,
     * la tranche équatoriale et les sept rayons passent tous par ici — une seule définition
     * de « le cristal est tourné comme ça », donc aucune pièce ne peut dériver des autres.
     */
    private static void spinPoint(float[] out, float f, float l, float y,
                                  float tilt, float spin) {
        float ct = Mth.cos(tilt);
        float st = Mth.sin(tilt);
        float l1 = l * ct - y * st;
        float y1 = l * st + y * ct;
        float cs = Mth.cos(spin);
        float sn = Mth.sin(spin);
        out[0] = f * cs - l1 * sn;
        out[1] = f * sn + l1 * cs;
        out[2] = y1;
    }

    /** Transformation inverse de {@link #spinPoint} : du monde vers le repère du cristal. */
    private static void unspinPoint(float[] out, float f, float l, float y,
                                    float tilt, float spin) {
        float cs = Mth.cos(spin);
        float sn = Mth.sin(spin);
        float f1 = f * cs + l * sn;
        float l1 = -f * sn + l * cs;
        float ct = Mth.cos(tilt);
        float st = Mth.sin(tilt);
        out[0] = f1;
        out[1] = l1 * ct + y * st;
        out[2] = -l1 * st + y * ct;
    }

    /**
     * Intersection d'un rayon et de l'OCTAÈDRE {@code |f| + |l| + |y| ≤ w}, par découpe de
     * l'intervalle sur les huit plans.
     *
     * <p>Huit plans, huit contraintes {@code s·X ≤ w} : on rétrécit {@code [tProche, tLoin]}
     * à chaque plan et on retient lequel a serré de chaque côté — ce sont les deux normales
     * dont la réfraction a besoin. Un octaèdre étant convexe, ça suffit : pas de parcours
     * de faces, pas de cas particulier.</p>
     *
     * @param out {@code {tEntrée, nf, nl, ny, tSortie, nf, nl, ny}}, normales SORTANTES
     * @return faux si le rayon manque le corps
     */
    private static boolean octaHit(float ox, float ol, float oy,
                                   float dxr, float dlr, float dyr,
                                   float w, float[] out) {
        if (w <= 0.001f) return false;
        float near = -1.0e9f;
        float far = 1.0e9f;
        int nearPlane = -1;
        int farPlane = -1;
        for (int plane = 0; plane < 8; plane++) {
            float sf = (plane & 1) == 0 ? 1.0f : -1.0f;
            float sl = (plane & 2) == 0 ? 1.0f : -1.0f;
            float sy = (plane & 4) == 0 ? 1.0f : -1.0f;
            float denom = sf * dxr + sl * dlr + sy * dyr;
            float num = w - (sf * ox + sl * ol + sy * oy);
            if (Math.abs(denom) < 1.0e-6f) {
                if (num < 0.0f) return false;      // rayon parallèle et déjà dehors
                continue;
            }
            float hit = num / denom;
            if (denom > 0.0f) {
                if (hit < far) { far = hit; farPlane = plane; }
            } else {
                if (hit > near) { near = hit; nearPlane = plane; }
            }
        }
        if (far <= near || farPlane < 0 || nearPlane < 0) return false;
        float inv = 0.57735027f;                   // 1/√3 : les normales doivent être unitaires
        out[0] = near;
        out[1] = ((nearPlane & 1) == 0 ? 1.0f : -1.0f) * inv;
        out[2] = ((nearPlane & 2) == 0 ? 1.0f : -1.0f) * inv;
        out[3] = ((nearPlane & 4) == 0 ? 1.0f : -1.0f) * inv;
        out[4] = far;
        out[5] = ((farPlane & 1) == 0 ? 1.0f : -1.0f) * inv;
        out[6] = ((farPlane & 2) == 0 ? 1.0f : -1.0f) * inv;
        out[7] = ((farPlane & 4) == 0 ? 1.0f : -1.0f) * inv;
        return true;
    }

    /**
     * Snell-Descartes sous forme vectorielle, <b>réflexion totale comprise</b>.
     *
     * <p>{@code eta} est le rapport des indices ; la normale est retournée pour faire face
     * au rayon. Si le radicande est négatif il n'y a pas de rayon transmis : on réfléchit,
     * et c'est ce cas-là qui fait sortir deux des sept faisceaux par une face inattendue.</p>
     *
     * @return vrai s'il y a eu réfraction, faux si réflexion totale
     */
    private static boolean refract(float[] out, float ix, float il, float iy,
                                   float nx, float nl, float ny, float eta) {
        float cosi = -(ix * nx + il * nl + iy * ny);
        if (cosi < 0.0f) { cosi = -cosi; nx = -nx; nl = -nl; ny = -ny; }
        float k = 1.0f - eta * eta * (1.0f - cosi * cosi);
        if (k < 0.0f) {
            out[0] = ix + 2.0f * cosi * nx;
            out[1] = il + 2.0f * cosi * nl;
            out[2] = iy + 2.0f * cosi * ny;
            return false;
        }
        float c = eta * cosi - Mth.sqrt(k);
        out[0] = eta * ix + c * nx;
        out[1] = eta * il + c * nl;
        out[2] = eta * iy + c * ny;
        float len = Mth.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        if (len > 1.0e-5f) { out[0] /= len; out[1] /= len; out[2] /= len; }
        return true;
    }

    /**
     * Flèche d'une CHAÎNETTE : la courbe que prend un câble qui pend, pas une parabole et
     * surtout pas un arc de cercle.
     *
     * <p>{@code u} va de 0 à 1 le long de la corde, {@code slack} est l'excès de longueur
     * rapporté à la corde. Sans ça, une arête de la trame encore molle se lit comme une
     * arête tendue mal placée ; avec, on voit au premier coup d'œil laquelle porte.</p>
     */
    private static float catenary(float u, float slack) {
        if (slack <= 0.0001f) return 0.0f;
        float shape = 2.2f + 6.0f * clamp(slack);
        float middle = (float) Math.cosh(shape * 0.5f);
        float here = (float) Math.cosh(shape * (u - 0.5f));
        return (middle - here) / shape * (0.9f + 2.4f * slack);
    }

    /**
     * Un cube soumis à la pesanteur qui REBONDIT sur le sol, au lieu de le traverser ou de
     * s'y coller. Renvoie la hauteur ; {@code sec} est en secondes depuis le lâcher.
     */
    private static float ballistic(float y0, float vy, float sec, float floorY) {
        float y = y0 + vy * sec - 0.5f * GRAVITY * sec * sec;
        if (y >= floorY) return y;
        float over = floorY - y;
        float damp = 1.0f / (1.0f + over * 2.6f);
        return floorY + Math.abs(Mth.sin(over * 4.2f)) * 0.22f * damp;
    }

    /** Distance entre deux points du repère local. */
    private static float dist(float f0, float l0, float y0, float f1, float l1, float y1) {
        float df = f1 - f0;
        float dl = l1 - l0;
        float dy = y1 - y0;
        return Mth.sqrt(df * df + dl * dl + dy * dy);
    }

    // =================================================================================
    //  OUTILS
    // =================================================================================

    /** Cube aligné : {@code f} = axe attaquant→cible, {@code lateral} = perpendiculaire. */
    private static void box(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + y, z, hf, hy, hl, rgb, alpha);
    }

    /**
     * Seuil d'opacité. Le tamis {@code pixelDensity} décime les cubes sous 200 d'alpha : un
     * fondu d'alpha ne fait pas disparaître, il TROUE. Tout passe donc par ici, et
     * l'effacement se joue sur la géométrie — ici, en rétrécissant les arêtes.
     */
    private static int opaque(float v) {
        int i = Math.round(v);
        if (i <= 10) return 0;
        return i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i);
    }

    private static float clamp(float value) { return Mth.clamp(value, 0.0f, 1.0f); }

    private static float fract(float x) { return x - (float) Math.floor(x); }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
