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
    public static final int LATTICE = 4;

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
        return t * (2.4f + 9.0f * spinUp) * (1.0f - broken * 0.85f);
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
    //  TRAME — l'anneau de nœuds, sa contraction et la cellule centrale
    // ---------------------------------------------------------------------------------

    /**
     * Rayon de l'anneau de nœuds. La capture du serveur ratisse six blocs ; on en dessine
     * la MOITIÉ, parce qu'une trame de douze blocs de large sort du cadre et qu'aucun des
     * sept corps n'est jamais à la limite exacte de la zone.
     */
    public static final float LATTICE_R = 3.00f;

    /** Première mise en tension : tick 40 sur 200. */
    public static final float LATTICE_TENSION0 = 40.0f / 200.0f;

    /** Écart entre deux mises en tension : dix ticks. */
    public static final float LATTICE_TENSION_STEP = 10.0f / 200.0f;

    /** Convergence : tick 160 sur 200. */
    public static final float LATTICE_CONVERGE = 160.0f / 200.0f;

    /** Rupture : tick 184 sur 200. Les arêtes cassent par le milieu. */
    public static final float LATTICE_SNAP = 184.0f / 200.0f;

    /** Rayon visé par les onze tensions, hors oscillation : c'est la consigne, pas l'état. */
    public static float latticeWeb(float t) {
        float pull = smooth(clamp((t - LATTICE_TENSION0) / 0.50f));
        float conv = smooth(clamp((t - LATTICE_CONVERGE) / 0.12f));
        float burst = smooth(clamp((t - LATTICE_SNAP) / 0.06f));
        return LATTICE_R * (1.0f - 0.42f * pull) * (1.0f - 0.52f * conv) * (1.0f + 1.8f * burst);
    }

    /** La cellule que la trame enserre : le trou de la constellation. Nulle à la rupture. */
    public static float latticeCell(float t) {
        float open = smooth(clamp((t - 0.30f) / 0.22f));
        float shut = smooth(clamp((t - LATTICE_SNAP) / 0.035f));
        float pull = smooth(clamp((t - LATTICE_TENSION0) / 0.50f));
        float conv = smooth(clamp((t - LATTICE_CONVERGE) / 0.12f));
        float ring = LATTICE_R * (1.0f - 0.42f * pull) * (1.0f - 0.52f * conv);
        return ring * 0.52f * open * (1.0f - shut);
    }

    /** Hauteur du centre de la trame : le torse des corps liés. */
    public static final float LATTICE_YC = 0.28f;

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
            case LATTICE -> renderLattice(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
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
            float kick = (1.0f - smooth(clamp((t - fire) / 0.085f))) * 0.11f;
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
                    float th = (0.030f + 0.040f * grow) * live;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.0f, yc, th, th, th,
                            grow > 0.82f ? SINGULAR : GLACIER,
                            opaque(a * (0.55f + 0.45f * grow) * beat * live));
                }
                // LES SEPT FILS, aux endroits exacts où les sept rayons entrent. On voit
                // donc arriver sept choses parallèles, et on verra sortir sept choses
                // divergentes : c'est le verbe de la signature, en une image.
                for (int k = 0; k < 7; k++) {
                    float off = k * (TAU / 7.0f) + 0.40f;
                    float ol = Mth.cos(off) * spread;
                    float oy = Mth.sin(off) * spread;
                    for (float f = -3.4f; f <= head; f += 0.13f) {
                        float grow = clamp((f + 3.4f) / 1.2f);
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, ol, yc + oy,
                                0.024f, 0.024f, 0.024f,
                                k % 3 == 0 ? GLACIER : (k % 3 == 1 ? HORIZON : HALO),
                                opaque(a * (0.30f + 0.45f * grow) * live));
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
            float live = 1.0f - smooth(clamp((t - PRISM_BREAK + 0.02f) / 0.10f));
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
            int steps = Math.max(3, Math.min(40, Math.round(reach / 0.155f)));
            int tone = k % 3 == 0 ? GLACIER : (k % 3 == 1 ? HORIZON : HALO);
            float rad = 0.052f + 0.012f * Mth.sin(t * 24.0f + k);
            for (int sIdx = 0; sIdx <= steps; sIdx++) {
                float u = sIdx / (float) steps;
                float d = u * reach;
                float ff = bf + rd[0] * d;
                float ll = bl + rd[1] * d;
                float yy = by + rd[2] * d;
                float taper = 1.0f - 0.45f * u;
                for (int c = 0; c < 5; c++) {
                    float ang = c * (TAU / 5.0f) + d * 2.4f + t * 6.0f;
                    float ca = Mth.cos(ang) * rad * taper;
                    float sa = Mth.sin(ang) * rad * taper;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ff + ux * ca + wx * sa, ll + ul * ca + wl * sa, yy + uy * ca + wy * sa,
                            0.026f, 0.026f, 0.026f,
                            sIdx == steps ? SINGULAR : tone,
                            opaque(a * (0.52f + 0.48f * (1.0f - u)) * live));
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
                float th = (equator ? 0.040f : 0.029f) * (0.55f + 0.45f * body + 0.45f * broken);
                int tone = broken > 0.0f ? (((e & 1) == 0) ? BASALT : SLATE)
                        : (equator ? GLACIER : HORIZON);
                segment(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0, f1, l1, y1,
                        th, equator ? 0.052f : 0.062f, tone, opaque(a * 0.95f * fade));
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
        return true;
    }

    // =================================================================================
    //  III. FAILLE DU NÉANT — 170 ticks — elle TRIE
    // =================================================================================

    /**
     * <b>La faille est une LIGNE, et c'est sa seule idée.</b> Le serveur ne ratisse pas un
     * disque mais un corridor de neuf blocs de long sur trois de large, PERPENDICULAIRE à
     * l'axe du coup ({@code applyNullRiftGameplay}) : se tenir dans son axe ou à deux pas de
     * côté change tout. La figure dit exactement ça — une déchirure de 8,6 blocs couchée en
     * travers du chemin de l'arme, et rien au-dessus de la victime.
     *
     * <h3>Le calcul</h3>
     * <ul>
     *   <li><b>propagation</b> : la fêlure part du point d'impact et COURT vers ses deux
     *       pointes. Elle n'apparaît pas d'un bloc, elle se propage, et ses branches
     *       secondaires partent du tronc avec une génération de moins ;</li>
     *   <li><b>profil de Griffith</b> : l'ouverture et la hauteur suivent
     *       {@code √(1 − u²)} — maximales au point de nucléation, nulles aux pointes. Une
     *       fente d'épaisseur constante n'est pas une fissure, c'est une découpe ;</li>
     *   <li><b>fibres</b> : entre les deux lèvres, des filaments de matière étirés qui
     *       s'amincissent, se nécrosent et CASSENT quand l'écartement passe leur limite.
     *       Leur nombre tombe à mesure que la faille bâille ;</li>
     *   <li><b>fermeture éclair</b> : au tick 140 les pointes se rejoignent vers le centre,
     *       et ce qui était dedans est éjecté PERPENDICULAIREMENT — exactement le
     *       {@code voidPush} du serveur, qui pousse selon ±l.</li>
     * </ul>
     *
     * <h3>La chronologie, en ticks du serveur</h3>
     * <ul>
     *   <li><b>2</b> la fêlure ; <b>14</b> l'ouverture ;</li>
     *   <li><b>20 → 100</b> neuf aspirations : à chacune, une vague de débris est arrachée
     *       des deux côtés du corridor et tirée VERS LE PLAN, pas vers le centre ;</li>
     *   <li><b>112</b> l'autre côté : ce qui remonte par la fente est en {@link #CHORUS} ;</li>
     *   <li><b>140</b> la fermeture, et l'éjection latérale ;</li>
     *   <li><b>140 → 170</b> la cicatrice au sol, qui refroidit.</li>
     * </ul>
     */
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
            // ---- 2. LES DEUX LÈVRES ---------------------------------------------------
            // Le bord de la déchirure : un arc qui monte de la pointe gauche au sommet et
            // redescend. Les deux lèvres sont décalées de ±l'ouverture, qui suit le profil.
            int n = 34;
            for (int side = -1; side <= 1; side += 2) {
                float prevF = 0.0f;
                float prevL = 0.0f;
                float prevY = 0.0f;
                for (int i = 0; i <= n; i++) {
                    float u = -1.0f + 2.0f * i / n;
                    float prof = riftProfile(u);
                    float ll = u * half;
                    float yy = base + crown * prof;
                    float jag = (hash(salt, i * 37 + (side > 0 ? 0 : 500)) - 0.5f) * 0.10f * prof;
                    float ff = side * (lipMax * prof * 0.92f + 0.035f) + jag;
                    if (i > 0) {
                        float bright = prof * (0.55f + 0.45f * Mth.sin(u * 9.0f - t * 14.0f));
                        segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                prevF, prevL, prevY, ff, ll, yy,
                                0.036f + 0.018f * prof, 0.050f,
                                bright > 0.62f ? SINGULAR : (prof > 0.45f ? GLACIER : HORIZON),
                                opaque(a * (0.68f + 0.32f * prof)));
                    }
                    prevF = ff;
                    prevL = ll;
                    prevY = yy;
                }
                // Les NERVURES intérieures : quatre niveaux qui montrent l'épaisseur de la
                // lèvre. Sans elles la déchirure est un trait, et un trait n'a pas de bord.
                for (int level = 1; level <= 4; level++) {
                    float h = level / 5.0f;
                    float prevF2 = 0.0f;
                    float prevL2 = 0.0f;
                    boolean started = false;
                    for (int i = 0; i <= n; i++) {
                        float u = -1.0f + 2.0f * i / n;
                        float prof = riftProfile(u);
                        if (prof <= h) { started = false; continue; }
                        // Ouverture à cette hauteur : une lentille, pas un rectangle.
                        float inner = Mth.sqrt(Math.max(0.0f, 1.0f - (h / prof) * (h / prof)));
                        float ff = side * lipMax * prof * inner * 0.88f;
                        float ll = u * half;
                        float yy = base + crown * h;
                        if (started) {
                            segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    prevF2, prevL2, yy, ff, ll, yy, 0.024f, 0.070f,
                                    (level & 1) == 0 ? BASALT : SLATE, opaque(a * 0.72f));
                        }
                        prevF2 = ff;
                        prevL2 = ll;
                        started = true;
                    }
                }
            }

            // ---- 3. LES FIBRES --------------------------------------------------------
            // De la matière étirée en travers du trou. Elles s'amincissent, se nécrosent au
            // milieu, et CASSENT : à pleine ouverture il n'en reste presque plus.
            {
                int fibres = 9;
                for (int i = 0; i < fibres; i++) {
                    float u = (hash(salt, i * 83) - 0.5f) * 1.74f;
                    float prof = riftProfile(u);
                    if (prof < 0.12f) continue;
                    float limit = 0.22f + 0.62f * hash(salt, i * 97);
                    float strain = clamp(gape / limit);
                    if (strain >= 1.0f) continue;                    // celle-ci a cassé
                    float h = (0.15f + 0.70f * hash(salt, i * 101)) * prof;
                    float ll = u * half;
                    float yy = base + crown * h;
                    float span = lipMax * prof * 0.92f;
                    int seg = 7;
                    for (int sIdx = 0; sIdx <= seg; sIdx++) {
                        float v = -1.0f + 2.0f * sIdx / seg;
                        float neck = 1.0f - 0.85f * strain * (1.0f - v * v);
                        float th = (0.028f * neck);
                        if (th < 0.006f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                v * span, ll + v * 0.04f, yy + (1.0f - v * v) * 0.05f * strain,
                                th, th, th, strain > 0.66f ? GLACIER : HORIZON,
                                opaque(a * (0.55f + 0.45f * (1.0f - strain))));
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
                float ang = i / (float) posts * TAU + t * 0.25f;
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                float r0 = ring * (1.04f + 0.26f * hash(salt, i * 17));
                float prevF = 0.0f;
                float prevL = 0.0f;
                float prevY = 0.0f;
                int seg = 10;
                for (int h = 0; h <= seg; h++) {
                    float u = h / (float) seg;
                    float yy = floorY + u * 2.45f;
                    // Plus on est haut, moins le trou tire : la déviation décroît en 1/(1+y²).
                    float bend = strength / Math.max(0.5f, r0) / (1.0f + yy * yy * 0.55f);
                    float rr = Math.max(ring * 0.22f, r0 - bend);
                    float ff = ca * rr;
                    float ll = sa * rr;
                    if (h > 0) {
                        float pull = clamp(bend / 0.9f);
                        segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                prevF, prevL, prevY, ff, ll, yy,
                                0.014f + 0.012f * pull, 0.115f - 0.045f * pull,
                                pull > 0.55f ? HORIZON : (pull > 0.25f ? HALO : SLATE),
                                opaque(a * (0.30f + 0.55f * pull) * open * (1.0f - shut)));
                    }
                    prevF = ff;
                    prevL = ll;
                    prevY = yy;
                }
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
    private static void renderLattice(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        float a = alpha;
        final int n = 7;
        final int[] edges = { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 0, 3, 1, 4, 2, 5, 3, 6 };
        float[] px = new float[n];
        float[] pl = new float[n];
        float[] py = new float[n];
        float[] rest = new float[11];
        latticeSim(t, salt, px, pl, py, rest);
        float snapped = clamp((t - LATTICE_SNAP) / 0.08f);
        float sec = (t - LATTICE_SNAP) * 10.0f;

        // ---- 1. LES SEPT NŒUDS, ET LEUR MARQUE AU SOL ---------------------------------
        // Chaque nœud est un CORPS : il a des pieds. La marque au sol dit lequel est lié,
        // et elle s'allume d'un cran à chaque tension encaissée — onze crans en tout.
        int tensions = 0;
        for (int e = 0; e < 11; e++) {
            if (t >= LATTICE_TENSION0 + e * LATTICE_TENSION_STEP) tensions++;
        }
        for (int i = 0; i < n; i++) {
            float wake = clamp((t - i * 4.0f / 200.0f) / 0.03f);
            if (wake <= 0.0f) continue;
            float load = tensions / 11.0f;
            node(vc, mat, tmp, cx, cy, cz, dx, dz, px[i], pl[i], py[i],
                    0.140f * wake, t * 3.4f + i * 1.3f, snapped, a * wake);
            // L'attache au sol : trois cubes, pas une colonne. Le nœud tient à un corps.
            for (int k = 1; k <= 3; k++) {
                float u = k / 4.0f;
                float yy = py[i] + (GROUND_Y + 0.05f - py[i]) * u;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, px[i], pl[i], yy,
                        0.022f, 0.022f, 0.022f, SLATE, opaque(a * wake * (0.55f - 0.12f * k)));
            }
            int mark = 12;
            for (int k = 0; k < mark; k++) {
                float ang = k / (float) mark * TAU + t * 0.8f;
                float rr = 0.40f + 0.06f * Mth.sin(ang * 3.0f + t * 6.0f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        px[i] + Mth.cos(ang) * rr, pl[i] + Mth.sin(ang) * rr, GROUND_Y + 0.014f,
                        0.032f, SLAB_H, 0.032f,
                        load > 0.66f ? GLACIER : (load > 0.33f ? HORIZON : HALO),
                        opaque(a * wake * (0.40f + 0.55f * load) * (1.0f - snapped * 0.7f)));
            }
        }

        // ---- 2. LES ONZE ARÊTES -------------------------------------------------------
        for (int e = 0; e < 11; e++) {
            int i0 = edges[e * 2];
            int i1 = edges[e * 2 + 1];
            float wake = clamp((t - Math.max(i0, i1) * 4.0f / 200.0f - 0.02f) / 0.05f);
            if (wake <= 0.0f) continue;
            float f0 = px[i0];
            float l0 = pl[i0];
            float y0 = py[i0];
            float f1 = px[i1];
            float l1 = pl[i1];
            float y1 = py[i1];
            float len = dist(f0, l0, y0, f1, l1, y1);
            float target = rest[e] * latticeRest(t);
            float strain = target > 0.01f ? (len - target) / target : 0.0f;
            float slack = strain < 0.0f ? -strain : 0.0f;
            float taut = clamp(strain * 2.6f);
            // La verticale locale, pour que la chaînette pende vers le bas et pas de côté.
            int seg = 12;
            float prevF = f0;
            float prevL = l0;
            float prevY = y0;
            for (int sIdx = 1; sIdx <= seg; sIdx++) {
                float u = sIdx / (float) seg;
                float ff = f0 + (f1 - f0) * u;
                float ll = l0 + (l1 - l0) * u;
                float yy = y0 + (y1 - y0) * u;
                // MOU : chaînette. TENDU : onde stationnaire, d'autant plus ample que la
                // déformation est grande — une corde trop tirée chante.
                yy -= catenary(u, slack) * len * 0.42f;
                if (taut > 0.02f) {
                    float wave = Mth.sin(u * Mth.PI * 3.0f + t * 46.0f) * 0.045f * taut;
                    yy += wave;
                    ff += wave * 0.5f;
                }
                if (snapped > 0.0f) {
                    // RUPTURE : l'arête casse par le milieu, et chaque moitié se rétracte
                    // vers son nœud en fouettant.
                    float frac = u < 0.5f ? u / 0.5f : (1.0f - u) / 0.5f;
                    float back = Math.min(1.0f, sec * 1.6f);
                    float whip = Mth.sin(frac * Mth.PI * 2.0f - sec * 9.0f) * 0.30f * (1.0f - back);
                    float shrink = 1.0f - back * 0.9f;
                    float anchorF = u < 0.5f ? f0 : f1;
                    float anchorL = u < 0.5f ? l0 : l1;
                    float anchorY = u < 0.5f ? y0 : y1;
                    ff = anchorF + (ff - anchorF) * shrink;
                    ll = anchorL + (ll - anchorL) * shrink;
                    yy = anchorY + (yy - anchorY) * shrink + whip;
                }
                int tone = snapped > 0.0f ? SLATE
                        : (taut > 0.72f ? SINGULAR : (taut > 0.34f ? GLACIER : (slack > 0.08f ? SLATE : HORIZON)));
                float th = (0.018f + 0.016f * taut) * wake;
                segment(vc, mat, tmp, cx, cy, cz, dx, dz, prevF, prevL, prevY, ff, ll, yy,
                        th, 0.050f, tone,
                        opaque(a * wake * (0.42f + 0.58f * taut) * (1.0f - snapped * 0.55f)));
                prevF = ff;
                prevL = ll;
                prevY = yy;
            }
        }

        // ---- 3. LES PULSATIONS DE TENSION ---------------------------------------------
        // À chaque tension, le serveur frappe TOUS les corps à la même frame. À l'écran,
        // une bille claire part de chaque nœud et court sur chaque arête : ce qui arrive à
        // l'un arrive à tous, et on le voit arriver.
        for (int e = 0; e < 11; e++) {
            float when = LATTICE_TENSION0 + e * LATTICE_TENSION_STEP;
            if (t < when) break;
            float age = t - when;
            if (age > 0.05f) continue;
            float u = age / 0.05f;
            for (int k = 0; k < 11; k++) {
                int i0 = edges[k * 2];
                int i1 = edges[k * 2 + 1];
                for (int dirn = 0; dirn < 2; dirn++) {
                    float uu = dirn == 0 ? u : 1.0f - u;
                    float ff = px[i0] + (px[i1] - px[i0]) * uu;
                    float ll = pl[i0] + (pl[i1] - pl[i0]) * uu;
                    float yy = py[i0] + (py[i1] - py[i0]) * uu;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                            0.044f, 0.044f, 0.044f, SINGULAR, opaque(a * (1.0f - u) * 0.95f));
                }
            }
        }

        // ---- 4. LA RUPTURE — tick 184 -------------------------------------------------
        if (snapped > 0.0f) {
            for (int e = 0; e < 11; e++) {
                int i0 = edges[e * 2];
                int i1 = edges[e * 2 + 1];
                float mf = (px[i0] + px[i1]) * 0.5f;
                float ml = (pl[i0] + pl[i1]) * 0.5f;
                float my = (py[i0] + py[i1]) * 0.5f;
                float out = Mth.sqrt(mf * mf + ml * ml) + 0.001f;
                float sp = 1.2f + 1.4f * hash(salt, e * 37);
                float ff = mf + mf / out * sp * sec;
                float ll = ml + ml / out * sp * sec;
                float yy = ballistic(my, 0.9f, sec, GROUND_Y + 0.05f);
                fragment(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                        0.044f, sec * 6.0f + e, GLACIER, SLATE,
                        opaque(a * (1.0f - clamp(sec / 1.6f))));
            }
        }
    }

    /**
     * Le rapport de longueur au repos imposé par les onze tensions : c'est la CONSIGNE que
     * le serveur applique aux corps, pas l'état du système — lequel oscille autour.
     */
    private static float latticeRest(float t) {
        float pull = smooth(clamp((t - LATTICE_TENSION0) / 0.50f));
        float conv = smooth(clamp((t - LATTICE_CONVERGE) / 0.12f));
        return (1.0f - 0.46f * pull) * (1.0f - 0.30f * conv);
    }

    /**
     * La simulation. Rejouée depuis zéro à chaque image, à pas FIXE : c'est ce qui la rend
     * déterministe sans garder un seul octet d'état entre deux frames.
     *
     * @param rest longueurs initiales des onze arêtes, remplies au passage
     */
    private static void latticeSim(float t, int salt, float[] px, float[] pl, float[] py,
                                   float[] rest) {
        final int n = 7;
        final int[] edges = { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 0, 3, 1, 4, 2, 5, 3, 6 };
        float[] vf = new float[n];
        float[] vl = new float[n];
        float[] vy = new float[n];
        float[] af = new float[n];
        float[] al = new float[n];
        float[] ay = new float[n];
        // Pose initiale : un anneau IRRÉGULIER. Sept corps ne se rangent pas en heptagone.
        for (int i = 0; i < n; i++) {
            float ang = i * (TAU / n) + (hash(salt, i * 17) - 0.5f) * 0.62f;
            float r = LATTICE_R * (0.72f + 0.46f * hash(salt, i * 31));
            px[i] = Mth.cos(ang) * r;
            pl[i] = Mth.sin(ang) * r;
            py[i] = LATTICE_YC + (hash(salt, i * 53) - 0.5f) * 1.35f;
        }
        for (int e = 0; e < 11; e++) {
            int i0 = edges[e * 2];
            int i1 = edges[e * 2 + 1];
            rest[e] = dist(px[i0], pl[i0], py[i0], px[i1], pl[i1], py[i1]);
        }
        final int total = 240;
        final float dt = 10.0f / total;                 // 200 ticks = 10 s
        final float dtT = 1.0f / total;                 // le même pas, exprimé en t
        int steps = Math.min(total, Math.max(0, Math.round(t * total)));
        for (int s = 0; s < steps; s++) {
            float now = s * dtT;
            for (int i = 0; i < n; i++) { af[i] = 0.0f; al[i] = 0.0f; ay[i] = 0.0f; }
            boolean broken = now >= LATTICE_SNAP;
            if (!broken) {
                float target = latticeRest(now);
                for (int e = 0; e < 11; e++) {
                    int i0 = edges[e * 2];
                    int i1 = edges[e * 2 + 1];
                    float ddf = px[i1] - px[i0];
                    float ddl = pl[i1] - pl[i0];
                    float ddy = py[i1] - py[i0];
                    float len = Mth.sqrt(ddf * ddf + ddl * ddl + ddy * ddy);
                    if (len < 0.0005f) continue;
                    float force = 5.4f * (len - rest[e] * target) / len;
                    af[i0] += ddf * force; al[i0] += ddl * force; ay[i0] += ddy * force;
                    af[i1] -= ddf * force; al[i1] -= ddl * force; ay[i1] -= ddy * force;
                }
                // L'impulsion des onze tensions, aux ticks exacts du serveur.
                for (int e = 0; e < 11; e++) {
                    float when = LATTICE_TENSION0 + e * LATTICE_TENSION_STEP;
                    if (when < now || when >= now + dtT) continue;
                    float bf = 0.0f;
                    float bl = 0.0f;
                    for (int i = 0; i < n; i++) { bf += px[i]; bl += pl[i]; }
                    bf /= n; bl /= n;
                    for (int i = 0; i < n; i++) {
                        float ddf = bf - px[i];
                        float ddl = bl - pl[i];
                        float d = Mth.sqrt(ddf * ddf + ddl * ddl);
                        if (d < 0.05f) continue;
                        float kick = 1.05f / dt;
                        vf[i] += ddf / d * kick * dt;
                        vl[i] += ddl / d * kick * dt;
                        vy[i] += (hash(salt, e * 13 + i) - 0.4f) * 0.55f;
                    }
                }
                // La convergence du tick 160 : une secousse, pas une téléportation.
                if (LATTICE_CONVERGE >= now && LATTICE_CONVERGE < now + dtT) {
                    for (int i = 0; i < n; i++) {
                        float d = Mth.sqrt(px[i] * px[i] + pl[i] * pl[i]) + 0.001f;
                        vf[i] -= px[i] / d * 1.8f;
                        vl[i] -= pl[i] / d * 1.8f;
                        vy[i] += 0.45f;
                    }
                }
            } else if (LATTICE_SNAP >= now - dtT && LATTICE_SNAP < now) {
                // La rupture : les liens lâchent, les corps partent avec ce qu'ils avaient.
                for (int i = 0; i < n; i++) {
                    float d = Mth.sqrt(px[i] * px[i] + pl[i] * pl[i]) + 0.001f;
                    vf[i] += px[i] / d * (2.1f + hash(salt, i * 61));
                    vl[i] += pl[i] / d * (2.1f + hash(salt, i * 67));
                    vy[i] += 1.1f * hash(salt, i * 71);
                }
            }
            float damp = broken ? 0.995f : 0.955f;
            for (int i = 0; i < n; i++) {
                vf[i] = (vf[i] + af[i] * dt) * damp;
                vl[i] = (vl[i] + al[i] * dt) * damp;
                vy[i] = (vy[i] + (ay[i] - (broken ? GRAVITY * 0.25f : 0.0f)) * dt) * damp;
                px[i] += vf[i] * dt;
                pl[i] += vl[i] * dt;
                py[i] += vy[i] * dt;
                // Aucun nœud ne descend sous le plancher : un corps lié reste un corps posé.
                float lowest = GROUND_Y + 0.55f;
                if (py[i] < lowest) { py[i] = lowest; if (vy[i] < 0.0f) vy[i] = -vy[i] * 0.35f; }
            }
        }
    }

    // =================================================================================
    //  PRIMITIVES PROPRES AU VIDE
    // =================================================================================

    /**
     * Un SEGMENT entre deux points du repère local, tracé en cubes jointifs.
     *
     * <p>C'est la primitive de base de la famille : le Vide ne dessine que des arêtes, donc
     * tout passe par ici. Le nombre de cubes suit la longueur réelle — un pas fixe donnerait
     * une tige pointillée quand elle s'allonge et un gaspillage quand elle raccourcit.</p>
     */
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
