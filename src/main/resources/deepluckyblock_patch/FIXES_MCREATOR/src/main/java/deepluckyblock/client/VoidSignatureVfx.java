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

    /** Hauteur du sol dans le repère local, reçue à chaque image (voir {@code probeGroundOffset}). */
    private static float GROUND_Y = -0.86f;

    /** Épaisseur d'une dalle posée au sol. */
    private static final float SLAB_H = 0.030f;

    /** Pesanteur locale, pour les seuls fragments rendus par le vide (rien d'autre ne tombe ici). */
    private static final float GRAVITY = 9.0f;

    private static final float TAU = 6.2831855f;

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
        GROUND_Y = Mth.clamp(groundOffset, -4.0f, 0.10f);
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
        float open = smooth(clamp((t - 0.10f) / 0.24f));
        float fall = smooth(clamp((t - 0.70f) / 0.10f));
        float rs = (0.05f + 1.10f * open) * (1.0f - fall);
        // --- Basculement de la grille : plan vertical au début, disque d'accrétion ensuite. ---
        float tip = smooth(clamp((t - 0.46f) / 0.24f));
        // --- Redressement élastique après la chute : la grille dépasse puis revient. ---
        float snap = clamp((t - 0.70f) / 0.22f);
        float rebound = snap <= 0.0f ? 0.0f
                : Mth.sin(snap * Mth.PI * 1.6f) * (1.0f - snap) * 0.55f;
        float yc = 0.46f;

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
    //  II. PRISME DU DRAGON — 190 ticks
    // =================================================================================

    /**
     * Chronologie (190 ticks) :
     * <ul>
     *   <li>0,00–0,10 <b>la descente</b> : un octaèdre en FIL DE FER tombe du ciel en trois
     *       à-coups. Douze arêtes, aucune face : on voit à travers, ce qui est la règle de
     *       l'élément appliquée à un solide ;</li>
     *   <li>0,10–0,22 <b>la mise en rotation</b> : il monte en régime autour d'un axe
     *       incliné, et quatre éclats d'{@link #ENDSTONE} sont arrachés au sol et verrouillés
     *       en orbite à son équateur ;</li>
     *   <li>0,22–0,32 <b>le trait d'entrée</b> : un unique faisceau PLEIN arrive de la main
     *       du porteur ({@code f < 0}). C'est la seule chose pleine de toute la signature,
     *       et c'est ce qui donne sa valeur au creux de tout le reste ;</li>
     *   <li>0,32–0,62 <b>la réfraction</b> : il ressort en SEPT faisceaux divergents, tous
     *       creux — huit cubes en couronne, rien au milieu. Ils s'allongent en séquence, pas
     *       ensemble : un éventail s'ouvre, il ne se déplie pas d'un bloc ;</li>
     *   <li>0,50–0,74 <b>les ailes</b> : à l'apogée les sept se rabattent en deux ailes
     *       membranées — quatre nervures et une membrane pointillée. Une aile se lit
     *       instantanément en silhouette, et ne partage rien avec le crâne de l'Ombre ;</li>
     *   <li>0,74–0,88 <b>la fracture</b> : les douze arêtes se séparent aux sommets et
     *       dérivent. Le cœur est mis à nu — et le cœur, c'est un TROU ;</li>
     *   <li>0,88–1,00 <b>l'éclat</b> : anneau au sol, colonne de motes {@link #INVERT}, et
     *       les quatre éclats d'endstone retombent sous la pesanteur.</li>
     * </ul>
     */
    private static void renderPrism(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float yc = 1.12f;

        // --- Descente en trois à-coups. Un objet qui se pose d'un glissement continu n'a
        //     pas de masse ; trois paliers suffisent à lui en donner. ---
        float drop = clamp(t / 0.10f);
        float stair = (Mth.floor(drop * 3.0f) + smooth(fract(drop * 3.0f))) / 3.0f;
        float py = yc + (1.0f - stair) * 4.2f;

        // --- Régime de rotation : il monte, tient, puis se casse à la fracture. ---
        float spinUp = smooth(clamp((t - 0.08f) / 0.14f));
        float broken = clamp((t - 0.74f) / 0.14f);
        float spin = t * (2.4f + 9.0f * spinUp) * (1.0f - broken * 0.85f);
        float tiltAxis = 0.34f;

        float scale = 0.72f * (1.0f + 0.10f * Mth.sin(t * 17.0f) * spinUp);

        // ---- 1. L'OCTAÈDRE EN FIL DE FER ----------------------------------------------
        // Six sommets : deux pôles, quatre à l'équateur. Douze arêtes, et rien d'autre.
        {
            float[][] vert = new float[6][3];
            for (int i = 0; i < 4; i++) {
                float ang = spin + i * (TAU / 4.0f);
                vert[i][0] = Mth.cos(ang) * scale;
                vert[i][1] = Mth.sin(ang) * tiltAxis * scale;
                vert[i][2] = Mth.sin(ang) * scale * 0.42f;
            }
            vert[4][0] = 0.0f; vert[4][1] = 0.0f; vert[4][2] = scale * 1.34f;
            vert[5][0] = 0.0f; vert[5][1] = 0.0f; vert[5][2] = -scale * 1.34f;
            int[][] edges = {
                    {0, 1}, {1, 2}, {2, 3}, {3, 0},
                    {0, 4}, {1, 4}, {2, 4}, {3, 4},
                    {0, 5}, {1, 5}, {2, 5}, {3, 5}
            };
            for (int e = 0; e < edges.length; e++) {
                int i0 = edges[e][0];
                int i1 = edges[e][1];
                // La fracture : chaque arête s'écarte du centre dans sa propre direction.
                float push = broken * (0.55f + 0.85f * hash(salt + 51, e * 23));
                float mfx = (vert[i0][0] + vert[i1][0]) * 0.5f;
                float mly = (vert[i0][1] + vert[i1][1]) * 0.5f;
                float mzz = (vert[i0][2] + vert[i1][2]) * 0.5f;
                float ml = Math.max(0.0001f, Mth.sqrt(mfx * mfx + mly * mly + mzz * mzz));
                float ofx = mfx / ml * push;
                float ofl = mly / ml * push;
                float ofy = mzz / ml * push;
                int rgb = e < 4 ? HORIZON : (e < 8 ? HALO : SLATE);
                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        vert[i0][0] + ofx, vert[i0][1] + ofl, py + vert[i0][2] + ofy,
                        vert[i1][0] + ofx, vert[i1][1] + ofl, py + vert[i1][2] + ofy,
                        0.026f, rgb, opaque(a * (1.0f - broken * 0.55f)));
            }
            // Les six sommets, plus vifs que les arêtes : ils donnent la forme au premier
            // coup d'œil, avant même qu'on ait suivi une arête.
            for (int i = 0; i < 6; i++) {
                float push = broken * 0.70f;
                float vl = Math.max(0.0001f, Mth.sqrt(vert[i][0] * vert[i][0]
                        + vert[i][1] * vert[i][1] + vert[i][2] * vert[i][2]));
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        vert[i][0] * (1.0f + push / vl), vert[i][1] * (1.0f + push / vl),
                        py + vert[i][2] * (1.0f + push / vl),
                        0.045f, 0.045f, 0.045f, GLACIER, opaque(a));
            }
            // ---- LE CŒUR ----
            // Avant la fracture : une bille de chorus, la seule matière pleine à l'intérieur.
            // Après : un TROU bordé de blanc. La signature se vide par le milieu.
            if (broken < 0.18f) {
                float core = 0.085f + 0.030f * Mth.sin(t * 23.0f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, py,
                        core, core, core, CHORUS, opaque(a));
            } else {
                float hole = 0.14f + 0.26f * (broken - 0.18f);
                int pts = 26;
                for (int i = 0; i < pts; i++) {
                    float ang = (i + 0.5f) / pts * TAU;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * hole, 0.0f, py + Mth.sin(ang) * hole,
                            0.022f, 0.022f, 0.022f, SINGULAR, opaque(a * (1.0f - broken * 0.4f)));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            0.0f, Mth.cos(ang) * hole, py + Mth.sin(ang) * hole,
                            0.022f, 0.022f, 0.022f, GLACIER, opaque(a * (1.0f - broken * 0.4f)));
                }
            }
        }

        // ---- 2. LES QUATRE ÉCLATS EN ORBITE -------------------------------------------
        // Matière étrangère : du sol arraché. Sans eux, le prisme flotte dans le néant et
        // on perd l'échelle autant que le rapport au monde.
        {
            float lift = smooth(clamp((t - 0.12f) / 0.12f));
            float thrown = clamp((t - 0.86f) / 0.14f);
            for (int i = 0; i < 4; i++) {
                float h1 = hash(salt + 61, i * 29);
                float ang = spin * 0.6f + i * (TAU / 4.0f) + 0.4f;
                float orbit = 1.35f + 0.22f * h1;
                float fy = GROUND_Y + (py - GROUND_Y) * lift + 0.10f * Mth.sin(t * 11.0f + i);
                float ff = Mth.cos(ang) * orbit * lift;
                float ll = Mth.sin(ang) * orbit * lift;
                if (thrown > 0.0f) {
                    float d = thrown * (3.4f + 1.2f * h1);
                    ff += Mth.cos(ang) * d;
                    ll += Mth.sin(ang) * d;
                    fy = fy + thrown * 0.9f - GRAVITY * 0.10f * thrown * thrown;
                    if (fy < GROUND_Y + 0.05f) fy = GROUND_Y + 0.05f;
                }
                float sz = 0.090f + 0.040f * h1;
                // Chaque éclat est un petit assemblage de trois cubes, pas un cube : un cube
                // isolé se lit comme un pixel de l'interface, trois se lisent comme un débris.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, fy,
                        sz, sz * 0.62f, sz, ENDSTONE, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ff + sz * 0.7f, ll - sz * 0.5f, fy - sz * 0.4f,
                        sz * 0.55f, sz * 0.45f, sz * 0.55f, ENDSTONE_DK, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, ff - sz * 0.6f, ll + sz * 0.6f, fy + sz * 0.3f,
                        sz * 0.40f, sz * 0.40f, sz * 0.40f, ENDSTONE, opaque(a * 0.92f));
            }
        }

        // ---- 3. LE TRAIT D'ENTRÉE ------------------------------------------------------
        // Il vient de la main : f négatif. Plein, fin, brillant — l'exception du fichier.
        {
            float inb = clamp((t - 0.22f) / 0.07f) * (1.0f - clamp((t - 0.60f) / 0.10f));
            if (inb > 0.01f) {
                int seg = 30;
                for (int i = 0; i < seg; i++) {
                    float u = (i + 0.5f) / seg;
                    float ff = -4.6f + 4.6f * u;
                    // Le faisceau pulse vers l'intérieur : des paquets qui remontent le trait.
                    float pk = 0.5f + 0.5f * Mth.sin(u * 22.0f - t * 70.0f);
                    float w = (0.028f + 0.020f * pk) * inb;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, 0.0f, py,
                            w, w, w, pk > 0.78f ? SINGULAR : GLACIER, opaque(a * inb));
                }
            }
        }

        // ---- 4. LA RÉFRACTION : SEPT FAISCEAUX CREUX -----------------------------------
        {
            float wings = smooth(clamp((t - 0.50f) / 0.18f)) * (1.0f - clamp((t - 0.74f) / 0.10f));
            for (int b = 0; b < 7; b++) {
                // Ouverture EN SÉQUENCE : chaque faisceau part trois ticks après le précédent.
                float born = 0.32f + b * 0.016f;
                float life = clamp((t - born) / 0.12f) * (1.0f - clamp((t - 0.78f) / 0.10f));
                if (life <= 0.01f) continue;
                float spreadAng = (b - 3) * 0.30f;
                // LE COU. Le faisceau central ne se rabat pas — sin(0) = 0 — il partait
                // donc tout droit en travers de la silhouette et cassait les deux ailes
                // en deux moities sans rapport. Pendant la phase des ailes il se raccourcit
                // et se releve : c'est la tete du dragon, et la silhouette se ferme.
                boolean neck = b == 3;
                // Le plan de l'éventail bascule lentement : un éventail figé est un décor.
                float planeTip = 0.30f * Mth.sin((t - 0.32f) * 3.2f);
                float len = (1.6f + 4.4f * smooth(life)) * (0.78f + 0.22f * Mth.cos(spreadAng));
                if (neck) len *= 1.0f - 0.52f * wings;
                // 4,5 segments par bloc et non 7 : le tube reste continu a l'oeil, et les
                // sept faisceaux passent de 2 128 cubes a environ 950.
                int seg = Math.max(10, Math.round(len * 4.5f));
                for (int s = 0; s < seg; s++) {
                    float u = (s + 0.5f) / seg;
                    float d = u * len;
                    float ff = d * Mth.cos(spreadAng);
                    float ll = d * Mth.sin(spreadAng);
                    float yy = py + d * planeTip * Mth.sin(spreadAng * 1.6f);
                    // --- LE RABATTEMENT EN AILE ---
                    if (wings > 0.01f) {
                        float sweep = wings * (0.55f + 0.30f * u);
                        float back = -Math.abs(Mth.sin(spreadAng)) * d * sweep * 1.15f;
                        float up = Mth.sin(u * Mth.PI * 0.72f) * d * sweep * 0.72f;
                        if (neck) {
                            // Le cou monte en courbe et se penche vers l'avant.
                            back = 0.0f;
                            up = Mth.sin(u * Mth.PI * 0.55f) * d * wings * 1.55f;
                        }
                        ff += back;
                        yy += up;
                    }
                    // --- LE TUBE CREUX ---
                    // Huit cubes en couronne, zéro au centre. Un faisceau plein serait un
                    // bâton lumineux de plus ; celui-ci, on le traverse du regard.
                    float rad = (0.072f + 0.030f * Mth.sin(u * 9.0f - t * 30.0f)) * (1.0f - u * 0.45f);
                    int ring = u < 0.08f ? 8 : 6;
                    // La couronne doit etre PERPENDICULAIRE au faisceau, pas alignee sur le
                    // repere : un tube dont les anneaux restent paralleles aux axes se
                    // vrille visiblement des que le faisceau s'ecarte de l'axe.
                    float perpF = -Mth.sin(spreadAng);
                    float perpL = Mth.cos(spreadAng);
                    for (int k = 0; k < ring; k++) {
                        float ang = (k + (s & 1) * 0.5f) / ring * TAU;
                        float ofl = Mth.cos(ang) * rad;
                        float ofy = Mth.sin(ang) * rad;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                ff + perpF * ofl, ll + perpL * ofl, yy + ofy,
                                0.020f, 0.020f, 0.020f,
                                k == 0 ? GLACIER : ((b + k) % 3 == 0 ? HORIZON : HALO),
                                opaque(a * life * (0.70f + 0.30f * (1.0f - u))));
                    }
                    // La POINTE : plus vive, et c'est elle qui touche.
                    if (s == seg - 1) {
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                                0.055f, 0.055f, 0.055f, SINGULAR, opaque(a * life));
                    }
                }
                // --- LA MEMBRANE ---
                // Pointillée, entre deux nervures voisines : une aile sans membrane est un
                // peigne. Pointillée et non pleine, parce qu'une membrane pleine du Vide
                // serait une surface — et le Vide n'a pas de surface.
                if (wings > 0.02f && b < 6) {
                    float nextAng = (b + 1 - 3) * 0.30f;
                    int dots = 26;
                    for (int k = 0; k < dots; k++) {
                        float u = (k + 0.5f) / dots;
                        float d = u * len * 0.92f;
                        float sweep = wings * (0.55f + 0.30f * u);
                        float m = 0.5f;
                        float sa = spreadAng + (nextAng - spreadAng) * m;
                        float ff = d * Mth.cos(sa) - Math.abs(Mth.sin(sa)) * d * sweep * 1.15f;
                        float ll = d * Mth.sin(sa);
                        float yy = py + Mth.sin(u * Mth.PI * 0.72f) * d * sweep * 0.72f
                                + d * 0.30f * Mth.sin((t - 0.32f) * 3.2f) * Mth.sin(sa * 1.6f);
                        float sz = 0.046f * wings * (1.0f - u * 0.4f);
                        if (sz <= 0.005f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                                sz, sz, sz, (k & 3) == 0 ? HORIZON : SLATE,
                                opaque(a * wings * 0.80f));
                    }
                }
            }
        }

        // ---- 5. L'ÉCLAT ----------------------------------------------------------------
        {
            float burst = clamp((t - 0.88f) / 0.12f);
            if (burst > 0.001f) {
                float ease = smooth(burst);
                float rr = 0.5f + 4.6f * ease;
                int pts = 56;
                for (int i = 0; i < pts; i++) {
                    float ang = (i + 0.5f) / pts * TAU;
                    float th = 0.078f * (1.0f - ease * 0.82f);
                    if (th <= 0.006f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.016f,
                            th, SLAB_H, th, i % 7 == 0 ? INVERT : HORIZON, opaque(a * 0.92f));
                }
                // La colonne : ce que le prisme avait pris, qui remonte.
                for (int i = 0; i < 26; i++) {
                    float h1 = hash(salt + 71, i * 13);
                    float h2 = hash(salt + 72, i * 17);
                    float u = fract(h1 + ease * 1.3f);
                    float yy = GROUND_Y + u * 4.4f;
                    float rad = (0.18f + 0.55f * h2) * (1.0f - u * 0.45f);
                    float ang = h2 * TAU + u * 2.6f;
                    float sz = 0.036f * (1.0f - u * 0.6f);
                    if (sz <= 0.005f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rad, Mth.sin(ang) * rad, yy,
                            sz, sz, sz, h1 > 0.65f ? INVERT : GLACIER, opaque(a * (1.0f - u * 0.5f)));
                }
            }
        }
    }

    // =================================================================================
    //  III. FAILLE DU NÉANT — 170 ticks
    // =================================================================================

    /**
     * Chronologie (170 ticks) :
     * <ul>
     *   <li>0,00–0,08 <b>la fêlure</b> : un cheveu vertical de {@link #SINGULAR}, large de
     *       douze millièmes. Rien d'autre. Huit ticks de presque rien — même leçon que le
     *       suspens du Couperet : c'est le vide AVANT l'événement qui fait atterrir
     *       l'événement ;</li>
     *   <li>0,08–0,24 <b>l'ouverture</b> : la ligne s'ouvre en amande, large au milieu. Les
     *       deux lèvres sont <b>dentelées et DÉCALÉES</b> — leurs dents viennent de deux
     *       graines différentes, donc elles s'engrènent sans correspondre. Du papier déchiré,
     *       pas coupé. À l'intérieur : <i>zéro cube</i> ;</li>
     *   <li>0,16–0,42 <b>la propagation</b> : le pied de la faille atteint le sol et une
     *       crevasse part des deux côtés, avec deux embranchements ;</li>
     *   <li>0,30–0,60 <b>l'aspiration</b> : des blocs du monde sont traînés vers la fente,
     *       basculent par-dessus la lèvre et ne ressortent pas ;</li>
     *   <li>0,40–0,64 <b>l'autre côté</b> : à travers la fente, trois fois, brièvement, une
     *       seconde grille apparaît — tournée à quatre-vingt-dix degrés et en
     *       {@link #CHORUS}. Le seul violet du fichier, et il veut dire « il y a un
     *       ailleurs » ;</li>
     *   <li>0,64–0,82 <b>la fermeture</b> : les lèvres se rejoignent mais ne s'emboîtent
     *       plus — décalées d'une dent, la couture reste visible ;</li>
     *   <li>0,82–1,00 <b>la cicatrice</b> : la couture refroidit du blanc au basalte et se
     *       rétracte. La crevasse au sol est la dernière à se refermer.</li>
     * </ul>
     */
    private static void renderRift(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float yBase = GROUND_Y + 0.05f;
        float height = 3.05f;

        // Ouverture puis fermeture. L'asymétrie est volontaire : ça s'ouvre vite (seize
        // ticks) et ça se referme lentement (trente), parce qu'une déchirure se fait d'un
        // coup et se recoud mal.
        float opening = smooth(clamp((t - 0.06f) / 0.17f));
        float closing = smooth(clamp((t - 0.64f) / 0.18f));
        float gape = opening * (1.0f - closing);
        float scar = clamp((t - 0.80f) / 0.20f);

        // ---- 1. LA FÊLURE puis LES DEUX LÈVRES -----------------------------------------
        {
            int steps = 46;
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps;                 // 0 = pied, 1 = sommet
                float yy = yBase + u * height;
                // L'AMANDE : large au milieu, nulle aux deux bouts. Puissance 0,62 pour que
                // la pointe reste pointue — une racine carrée donnerait une ellipse molle.
                float profile = Mth.sin(u * Mth.PI);
                profile = (float) Math.pow(profile, 0.62);
                float wMax = 0.82f * gape;
                float w = wMax * profile;
                // Les DENTS. Deux hachages différents pour les deux lèvres : c'est tout le
                // sujet. Avec le même, les dents s'emboîtent et on lit une fermeture éclair.
                float toothL = (hash(salt + 81, i * 13) - 0.5f) * 0.14f * profile;
                float toothR = (hash(salt + 82, i * 29) - 0.5f) * 0.14f * profile;
                // Le décalage d'une dent à la fermeture : les lèvres ne retrouvent pas leur
                // place. C'est la cicatrice, et elle se voit.
                float slip = closing * 0.085f * Mth.sin(u * 23.0f + salt * 0.017f);
                float lL = -w + toothL + slip;
                float lR = w + toothR - slip;
                // Léger serpentement vertical : une déchirure n'est pas un trait de règle.
                float wander = 0.16f * Mth.sin(u * 4.3f + salt * 0.013f);

                float lipW = 0.030f + 0.020f * profile;
                int lipRgb = scar > 0.02f
                        ? (scar > 0.62f ? BASALT : (scar > 0.30f ? HALO : HORIZON))
                        : GLACIER;
                // Lèvre gauche : liseré clair dehors, noir dedans. Le noir n'est pas un
                // remplissage, c'est l'épaisseur de la lèvre — un seul cube.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, wander, lL, yy,
                        lipW, lipW * 1.25f, lipW, lipRgb, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, wander, lL + 0.030f, yy,
                        lipW * 0.70f, lipW, lipW * 0.70f, NOTHING, opaque(a * 0.95f));
                // Lèvre droite.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, wander, lR, yy,
                        lipW, lipW * 1.25f, lipW, lipRgb, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, wander, lR - 0.030f, yy,
                        lipW * 0.70f, lipW, lipW * 0.70f, NOTHING, opaque(a * 0.95f));
                // Le fil blanc de la fêlure, avant l'ouverture et après la fermeture.
                if (gape < 0.14f || scar > 0.02f) {
                    float th = 0.013f * (1.0f - scar * 0.85f);
                    if (th > 0.003f) {
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, wander, (lL + lR) * 0.5f, yy,
                                th, th * 2.4f, th, scar > 0.55f ? HORIZON : SINGULAR, opaque(a));
                    }
                }
                // Quelques ÉCHARDES qui se détachent de la lèvre : une déchirure fibre.
                if ((i % 7) == 3 && gape > 0.25f) {
                    float h1 = hash(salt + 83, i * 31);
                    float out = 0.10f + 0.26f * h1;
                    float side = (i & 1) == 0 ? -1.0f : 1.0f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            wander + (h1 - 0.5f) * 0.14f,
                            (side < 0 ? lL : lR) + side * out, yy + (h1 - 0.5f) * 0.12f,
                            0.018f, 0.030f, 0.018f, HORIZON, opaque(a * 0.85f));
                }
            }
        }

        // ---- 2. L'AUTRE CÔTÉ ------------------------------------------------------------
        // Trois apparitions brèves, jamais de la même longueur. Clippées dans l'amande :
        // on ne voit l'autre grille QUE par la fente, ce qui prouve que la fente est un trou.
        {
            float[][] windows = {{0.40f, 0.055f}, {0.49f, 0.030f}, {0.57f, 0.070f}};
            for (int wi = 0; wi < windows.length; wi++) {
                float w0 = windows[wi][0];
                float wd = windows[wi][1];
                if (t < w0 || t > w0 + wd) continue;
                float p = (t - w0) / wd;
                float fade = Mth.sin(p * Mth.PI);
                int rows = 9;
                for (int r = 0; r < rows; r++) {
                    float u = (r + 0.5f) / rows;
                    float yy = yBase + u * height;
                    float profile = (float) Math.pow(Mth.sin(u * Mth.PI), 0.62);
                    float wMax = 0.82f * gape * profile;
                    if (wMax < 0.05f) continue;
                    int cols = Math.max(2, Math.round(wMax * 14.0f));
                    for (int c = 0; c < cols; c++) {
                        float lv = (c + 0.5f) / cols;
                        float ll = -wMax + lv * wMax * 2.0f;
                        // La grille d'en face est TOURNÉE : ses lignes sont horizontales là
                        // où les nôtres sont verticales. C'est ce qui dit « autre espace ».
                        float g = fract(yy * 1.7f + wi * 0.37f + t * 0.8f);
                        if (g > 0.30f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                0.16f * Mth.sin(u * 4.3f + salt * 0.013f) + 0.06f, ll, yy,
                                0.016f, 0.014f, 0.016f, CHORUS, opaque(a * fade * 0.95f));
                    }
                }
            }
        }

        // ---- 3. LA CREVASSE AU SOL ------------------------------------------------------
        // Elle part du pied, dans les deux sens, le long de l'axe LATÉRAL — la faille est un
        // plan, sa trace au sol est une ligne, pas un disque. C'est la seule aire de ce type
        // dans tout le mod, et le gameplay la reprend telle quelle (corridor, pas cercle).
        {
            float run = smooth(clamp((t - 0.15f) / 0.24f));
            float heal = clamp((t - 0.84f) / 0.16f);
            if (run > 0.01f && heal < 1.0f) {
                float reach = 4.6f * run;
                int steps = 40;
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 1; i <= steps; i++) {
                        float u = i / (float) steps;
                        float d = u * reach;
                        if (d < 0.08f) continue;
                        // La crevasse se referme depuis le bout : la cicatrisation remonte
                        // vers le centre, elle ne s'efface pas partout à la fois.
                        if (u > 1.0f - heal) continue;
                        float wander = 0.30f * Mth.sin(d * 1.9f + side * 1.1f + salt * 0.011f);
                        float gap = (0.20f + 0.16f * Mth.sin(d * 3.1f)) * (1.0f - u * 0.62f) * gape;
                        if (gap < 0.012f) continue;
                        float jag = (hash(salt + 91, i * 17 + (side + 1) * 500) - 0.5f) * 0.10f;
                        for (int lip = -1; lip <= 1; lip += 2) {
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    wander + lip * gap + jag, side * d, GROUND_Y + 0.014f,
                                    0.052f, SLAB_H, 0.062f,
                                    heal > 0.1f ? BASALT : HORIZON, opaque(a * 0.95f));
                        }
                        // Les dalles qui BASCULENT dans la fente. Chacune a sa propre
                        // inclinaison : des dalles toutes identiques feraient un escalier.
                        if ((i % 3) == 0) {
                            float h1 = hash(salt + 92, i * 23 + (side + 1) * 700);
                            float tipIn = smooth(clamp((run - u) * 2.2f));
                            float sink = tipIn * (0.22f + 0.30f * h1);
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    wander + (h1 - 0.5f) * 0.42f, side * d + (h1 - 0.5f) * 0.3f,
                                    GROUND_Y + 0.05f - sink,
                                    0.085f, 0.028f + 0.030f * tipIn, 0.085f,
                                    h1 > 0.5f ? ENDSTONE : ENDSTONE_DK,
                                    opaque(a * (1.0f - tipIn * 0.55f)));
                        }
                        // Deux embranchements, à un tiers et aux deux tiers.
                        if (i == steps / 3 || i == (steps * 2) / 3) {
                            float bl = 0.9f + 0.7f * hash(salt + 93, i);
                            int bs = 10;
                            for (int k = 1; k <= bs; k++) {
                                float bu = k / (float) bs;
                                float bd = bu * bl * run;
                                float bf = wander + side * bd * 0.86f;
                                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                        bf, side * d + bd * 0.52f, GROUND_Y + 0.014f,
                                        0.040f * (1.0f - bu * 0.6f), SLAB_H,
                                        0.040f * (1.0f - bu * 0.6f),
                                        HALO, opaque(a * 0.80f * (1.0f - bu * 0.5f)));
                            }
                        }
                    }
                }
            }
        }

        // ---- 4. L'ASPIRATION -------------------------------------------------------------
        // Ce que le corridor avale. Les blocs sont traînés le long du SOL vers l'axe, puis
        // basculent par-dessus la lèvre et s'amincissent jusqu'à rien. Aucun ne ressort.
        {
            float suck = clamp((t - 0.26f) / 0.36f);
            if (suck > 0.001f) {
                for (int i = 0; i < 22; i++) {
                    float h1 = hash(salt + 101, i * 13);
                    float h2 = hash(salt + 102, i * 17);
                    float h3 = hash(salt + 103, i * 19);
                    float start = h1 * 0.40f;
                    float p = clamp((suck - start) / (0.46f + 0.34f * h2));
                    if (p <= 0.0f || p >= 1.0f) continue;
                    float f0 = (h2 - 0.5f) * 5.0f;
                    float l0 = (h3 - 0.5f) * 8.2f;
                    // Trajet : d'abord le long du sol vers l'axe f = 0, puis la bascule.
                    float glide = smooth(clamp(p / 0.72f));
                    float ff = f0 * (1.0f - glide);
                    float ll = l0 * (1.0f - glide * 0.28f);
                    float yy = GROUND_Y + 0.07f;
                    float tipOver = clamp((p - 0.68f) / 0.32f);
                    if (tipOver > 0.0f) {
                        yy += tipOver * 0.55f - GRAVITY * 0.055f * tipOver * tipOver;
                        ff *= (1.0f - tipOver);
                    }
                    float sz = (0.080f + 0.040f * h1) * (1.0f - tipOver * 0.92f);
                    if (sz <= 0.006f) continue;
                    // Tumbling : chaque bloc tourne à sa vitesse, lu par un décalage de sa
                    // face sombre. Sans lui, un bloc qui glisse est un palet de hockey.
                    float roll = p * (6.0f + 7.0f * h3);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, ff, ll, yy,
                            sz, sz * 0.72f, sz, ENDSTONE, opaque(a * (1.0f - tipOver * 0.4f)));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ff + Mth.cos(roll) * sz * 0.7f, ll + Mth.sin(roll) * sz * 0.7f,
                            yy - sz * 0.45f,
                            sz * 0.48f, sz * 0.40f, sz * 0.48f, ENDSTONE_DK,
                            opaque(a * (1.0f - tipOver * 0.4f)));
                }
            }
        }

        // ---- 5. LA CICATRICE ---------------------------------------------------------------
        // Quelques motes qui montent de la couture, et rien d'autre. La signature ne se
        // termine pas sur un effet, elle se termine sur une trace.
        {
            if (scar > 0.02f) {
                for (int i = 0; i < 14; i++) {
                    float h1 = hash(salt + 111, i * 13);
                    float h2 = hash(salt + 112, i * 17);
                    float u = fract(h1 + scar * 1.1f);
                    float yy = yBase + u * height * 1.25f;
                    float sz = 0.026f * (1.0f - u * 0.7f) * (1.0f - scar * 0.4f);
                    if (sz <= 0.005f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            0.16f * Mth.sin(u * 4.3f + salt * 0.013f) + (h2 - 0.5f) * 0.20f,
                            (h1 - 0.5f) * 0.30f, yy,
                            sz, sz, sz, h2 > 0.70f ? INVERT : HORIZON,
                            opaque(a * (1.0f - u * 0.6f)));
                }
            }
        }
    }


    // =================================================================================
    //  IV. PORTE D'HORIZON — 180 ticks
    // =================================================================================

    /**
     * 虚 <b>LA PORTE D'HORIZON</b> — un TORE posé au sol, et ce qui y tombe ressort ailleurs.
     *
     * <p><b>Pourquoi un tore ?</b> Les trois premières signatures du Vide occupent trois
     * catégories de forme : un volume (la sphère), un solide convexe (l'octaèdre du prisme),
     * une surface plane (la faille). Il manquait la seule forme fermée qui porte un TROU —
     * et c'est précisément la forme qui dit « on passe à travers ». Une sphère n'a pas
     * d'autre côté ; un anneau, si.</p>
     *
     * <p><b>Le piège du tube, payé une fois déjà sur la couronne du Prisme.</b> Un anneau
     * tubulaire dont la section est construite sur les axes du monde se VRILLE : la section
     * tourne avec l'angle au lieu de rester perpendiculaire à l'âme. Le repère est donc
     * explicite ici — tangente, normale radiale, verticale — et recalculé à chaque pas.</p>
     *
     * <ul>
     *   <li>0,00–0,12 <b>le tracé</b> : un fil unique court au sol en cercle, comme une
     *       craie. L'empreinte existe avant la porte ;</li>
     *   <li>0,12–0,30 <b>le gonflement</b> : le fil s'épaissit en tore. Corps {@link #BASALT},
     *       crête {@link #HORIZON} — un tube d'un seul ton serait un beignet ;</li>
     *   <li>0,22–0,78 <b>la membrane</b> : l'intérieur se remplit d'une <b>spirale
     *       logarithmique</b> à deux brins qui tourne et DESCEND. Pas un disque : un
     *       escalier. Un disque plein aurait bouché le trou, c'est-à-dire supprimé le
     *       sujet ;</li>
     *   <li>0,30–0,46 <b>la sortie</b> : un second tore, 0,62 fois plus petit et <b>incliné</b>,
     *       apparaît 2,25 blocs plus haut. L'inclinaison est ce qui interdit de le lire comme
     *       un écho du premier ;</li>
     *   <li>0,34–0,86 <b>le transit</b> : sept fragments d'{@link #ENDSTONE} tombent dans le
     *       tore du bas, <b>cessent d'exister</b> sous la membrane, puis <b>réapparaissent</b>
     *       au tore du haut en {@link #INVERT} et retombent en dehors ;</li>
     *   <li>0,86–1,00 <b>la fermeture</b> : les deux tores <b>rentrent dans leur propre
     *       trou</b> — le rayon de l'âme tombe à zéro pendant que la section tient. Ils ne
     *       s'effacent pas, ils se résorbent.</li>
     * </ul>
     */
    private static void renderGate(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        float a = alpha;
        float yFloor = GROUND_Y + 0.04f;

        // Ouverture, puis résorption. Le rayon de l'âme est la SEULE chose qui tombe à la
        // fermeture : la section garde son épaisseur, sinon le tube maigrit au lieu de
        // rentrer sur lui-même, et un tube qui maigrit est un tube qui s'efface.
        float grow = smooth(clamp((t - 0.04f) / 0.26f));
        float shut = smooth(clamp((t - 0.86f) / 0.14f));
        float ring = (0.34f + 1.78f * grow) * (1.0f - shut);
        float spin = t * 3.4f + salt * 0.0013f;

        // ---- 1. LE TRACÉ AU SOL ---------------------------------------------------------
        // Avant que la porte existe, son empreinte est dessinée. Le fil n'est pas un cercle
        // complet d'emblée : il COURT, et son extrémité est plus claire que sa traîne.
        {
            float draw = clamp(t / 0.12f);
            if (draw < 1.0f) {
                int n = 76;
                int upTo = Math.max(1, Math.round(n * draw));
                for (int i = 0; i <= upTo; i++) {
                    float ang = i / (float) n * TAU;
                    float head = i / (float) Math.max(1, upTo);
                    float rr = 2.12f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yFloor,
                            0.034f, SLAB_H, 0.034f,
                            head > 0.92f ? SINGULAR : (head > 0.70f ? GLACIER : HALO),
                            opaque(a * (0.45f + 0.55f * head)));
                }
            }
        }

        // ---- 2. LE TORE DU BAS ----------------------------------------------------------
        if (ring > 0.02f) {
            float tube = 0.145f * (0.35f + 0.65f * grow);
            torus(vc, mat, tmp, cx, cy, cz, dx, dz,
                    0.0f, 0.0f, yFloor + tube * 1.15f,
                    ring, tube, 0.0f, spin, a, salt + 11, false);
        }

        // ---- 3. LA MEMBRANE : SPIRALE LOGARITHMIQUE À DEUX BRINS ------------------------
        // r = a·e^(b·θ) prise à l'envers : on part du bord et on tombe vers le centre. Deux
        // brins décalés d'un demi-tour, sans quoi la nappe tourne visiblement « d'un côté »
        // et ressemble à un disque rayé.
        {
            float life = clamp((t - 0.22f) / 0.10f) * (1.0f - clamp((t - 0.78f) / 0.12f));
            if (life > 0.01f && ring > 0.25f) {
                for (int brin = 0; brin < 2; brin++) {
                    float phase = brin * Mth.PI;
                    int steps = 54;
                    float prevF = 0.0f, prevL = 0.0f, prevY = 0.0f;
                    for (int i = 0; i <= steps; i++) {
                        float u = i / (float) steps;              // 0 = bord, 1 = centre
                        float theta = phase - spin * 1.7f + u * 4.6f;
                        float rr = ring * 0.94f * (float) Math.exp(-2.35 * u);
                        // L'escalier : la nappe DESCEND en spiralant. Sans cette chute, deux
                        // spirales à plat ne sont qu'un motif de tapis.
                        float yy = yFloor + 0.10f - 0.52f * u * u * life;
                        float f = Mth.cos(theta) * rr;
                        float l = Mth.sin(theta) * rr;
                        if (i > 0) {
                            float fade = (1.0f - u * 0.45f) * life;
                            int rgb = u < 0.18f ? HORIZON : (u < 0.55f ? HALO : ABYSS);
                            segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    prevF, prevL, prevY, f, l, yy,
                                    0.030f + 0.018f * (1.0f - u), 0.072f,
                                    rgb, opaque(a * fade));
                        }
                        prevF = f; prevL = l; prevY = yy;
                    }
                }
            }
        }

        // ---- 4. LE TORE DU HAUT, INCLINÉ ------------------------------------------------
        float outGrow = smooth(clamp((t - 0.30f) / 0.16f)) * (1.0f - shut);
        float outRing = ring * 0.62f * outGrow;
        float outY = yFloor + 2.25f;
        float tilt = 0.52f;
        if (outRing > 0.04f) {
            torus(vc, mat, tmp, cx, cy, cz, dx, dz,
                    0.0f, 0.0f, outY,
                    outRing, 0.105f, tilt, -spin * 1.35f, a * 0.95f, salt + 23, true);
        }

        // ---- 5. LE TRANSIT --------------------------------------------------------------
        // Sept fragments. Chacun a trois vies : il TOMBE dans le tore du bas, il N'EXISTE
        // PAS entre les deux, il RESSORT du tore du haut. La phase morte est ce qui prouve
        // qu'il a traversé quelque chose — sans elle, on voit un objet voler en arc.
        {
            for (int k = 0; k < 7; k++) {
                float born = 0.34f + k * 0.072f;
                float age = (t - born) / 0.30f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float angIn = hash(salt + 31, k * 7) * TAU;
                float rIn = ring * (0.42f + 0.44f * hash(salt + 32, k * 13));
                float angOut = hash(salt + 33, k * 17) * TAU;
                float rOut = outRing * (0.30f + 0.50f * hash(salt + 34, k * 19));
                float size = 0.075f + 0.045f * hash(salt + 35, k * 23);

                if (age < 0.38f) {
                    // --- LA CHUTE. Il arrive de 1,6 bloc au-dessus et plonge dans la nappe.
                    float u = age / 0.38f;
                    float yy = yFloor + 1.60f - 1.62f * u * u;
                    float sh = size * (1.0f - u * 0.25f);
                    fragment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(angIn) * rIn, Mth.sin(angIn) * rIn, yy,
                            sh, u * 9.0f + k, ENDSTONE, ENDSTONE_DK, opaque(a));
                } else if (age < 0.52f) {
                    // --- LE RIEN. Quatorze centièmes de la vie du fragment sans aucun cube.
                    continue;
                } else {
                    // --- LA SORTIE. Il jaillit du tore du haut, en INVERT, et retombe hors
                    //     du cercle : ce qui est ressorti ne revient pas dans la porte.
                    float u = (age - 0.52f) / 0.48f;
                    float rr = rOut + u * 1.45f;
                    float yy = outY + 0.42f * Mth.sin(u * Mth.PI * 0.80f) - 1.15f * u * u;
                    float sh = size * (1.0f - u * 0.35f);
                    if (u < 0.14f) {
                        // L'éclat de sortie : trois cubes sur l'arête du tore du haut.
                        float flash = 1.0f - u / 0.14f;
                        for (int s = 0; s < 3; s++) {
                            float sa = angOut + (s - 1) * 0.16f;
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    Mth.cos(sa) * outRing, Mth.sin(sa) * outRing,
                                    outY + Mth.cos(sa) * outRing * Mth.sin(tilt) * 0.0f,
                                    0.055f * flash + 0.020f, 0.055f * flash + 0.020f,
                                    0.055f * flash + 0.020f, SINGULAR, opaque(a * flash));
                        }
                    }
                    fragment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(angOut) * rr, Mth.sin(angOut) * rr, yy,
                            sh, u * 11.0f + k, INVERT, ENDSTONE_DK, opaque(a * (1.0f - u * 0.5f)));
                }
            }
        }

        // ---- 6. LES QUATRE MONTANTS -----------------------------------------------------
        // Ce qui relie les deux tores, et seulement une fois qu'ils existent tous les deux :
        // quatre tiges verticales très fines, en pointillé large. Sans elles, le tore du
        // haut flotte sans raison ; avec un tube plein, on retomberait sur le conduit du
        // Siphon, qui appartient au Sang.
        if (outGrow > 0.06f) {
            for (int k = 0; k < 4; k++) {
                float ang = k * (TAU / 4.0f) + spin * 0.4f;
                float climb = clamp((t - 0.34f) / 0.14f);
                float yTop = yFloor + 0.12f + (outY - yFloor - 0.12f) * climb;
                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * ring * 0.96f, Mth.sin(ang) * ring * 0.96f, yFloor + 0.12f,
                        Mth.cos(ang) * outRing * 0.96f, Mth.sin(ang) * outRing * 0.96f, yTop,
                        0.022f, 0.195f, HALO, opaque(a * 0.80f * outGrow));
            }
        }
    }

    // =================================================================================
    //  V. CONSTELLATION NULLE — 200 ticks
    // =================================================================================

    /**
     * 虚 <b>LA CONSTELLATION NULLE</b> — sept nœuds, leurs fils, et la trame qui se déchire.
     *
     * <p><b>Pourquoi un graphe ?</b> Les quatre autres signatures du Vide sont CENTRÉES :
     * tout y rayonne d'un point ou d'un plan. C'est le dernier schéma spatial disponible, et
     * c'est aussi le seul qui puisse dire visuellement « ces choses-là sont SOLIDAIRES » —
     * ce qu'aucune forme centrée ne peut exprimer, puisqu'elle n'a qu'un centre.</p>
     *
     * <p><b>Les fils pendent avant de se tendre.</b> Une arête droite d'emblée est un trait
     * de règle : elle ne pèse rien et ne raconte rien. Une <b>caténaire</b> ({@code cosh})
     * qui s'affaisse puis se redresse d'un coup donne au graphe une matière — on comprend
     * que quelque chose TIRE. C'est la même leçon que le disque d'accrétion : une surface
     * d'épaisseur nulle n'existe pas, un fil sans poids non plus.</p>
     *
     * <ul>
     *   <li>0,00–0,10 <b>l'apparition</b> : sept nœuds, un toutes les trois frames, à des
     *       positions tirées d'un hachage. Chacun est un petit <b>octaèdre</b> à six
     *       pointes — pas un cube : le cube appartient à la grille de la Sphère ;</li>
     *   <li>0,10–0,38 <b>les fils pendent</b> : onze arêtes molles, en pointillé {@link #HALO},
     *       affaissées de 28 % de leur portée ;</li>
     *   <li>0,38–0,70 <b>la tension</b> : une arête se tend toutes les quatre frames. Elle
     *       devient droite et {@link #GLACIER}, un éclat blanc la parcourt, et <b>ses deux
     *       nœuds se rapprochent</b> ;</li>
     *   <li>0,56–0,86 <b>la convergence</b> : tous les nœuds glissent vers leur barycentre ;</li>
     *   <li>0,86–0,94 <b>le point</b> : les sept se superposent en un seul cube
     *       {@link #SINGULAR} ;</li>
     *   <li>0,94–1,00 <b>la déchirure</b> : la trame reste imprimée, puis chaque arête
     *       <b>casse par son milieu</b> et ses deux moitiés se rétractent vers les bouts.
     *       Une trame qui s'effacerait d'un bloc ne dirait pas qu'elle était tendue.</li>
     * </ul>
     */
    private static void renderLattice(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        float a = alpha;
        final int N = 7;

        // Les onze arêtes. Un graphe complet (21 arêtes) serait une boule de laine ; un
        // cycle simple (7) n'aurait aucune profondeur. Onze donne des triangles ET des
        // chemins longs, ce qui est exactement ce qu'on veut lire : une structure.
        final int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 0},
                {0, 3}, {1, 4}, {2, 5}, {3, 6}
        };

        float converge = smooth(clamp((t - 0.56f) / 0.30f));
        /* La DECHIRURE passe AVANT le point : si la trame se resorbait d'abord, les aretes
           auraient une longueur nulle au moment de rompre et personne ne verrait rien rompre. */
        float tear = clamp((t - 0.86f) / 0.07f);
        float collapse = smooth(clamp((t - 0.92f) / 0.08f));

        // ---- Positions des nœuds --------------------------------------------------------
        float[] nf = new float[N];
        float[] nl = new float[N];
        float[] ny = new float[N];
        float bf = 0.0f, bl = 0.0f, by = 0.0f;
        for (int i = 0; i < N; i++) {
            // Répartition en couronne irrégulière : un tirage libre sur les trois axes
            // donnait régulièrement deux nœuds superposés, et deux nœuds superposés
            // ressemblent à un défaut d'affichage, pas à une constellation.
            float ang = (i / (float) N) * TAU + (hash(salt + 41, i * 7) - 0.5f) * 0.70f;
            float rad = 1.25f + 1.55f * hash(salt + 42, i * 11);
            nf[i] = Mth.cos(ang) * rad;
            nl[i] = Mth.sin(ang) * rad;
            ny[i] = 0.10f + 2.05f * hash(salt + 43, i * 13);
            bf += nf[i]; bl += nl[i]; by += ny[i];
        }
        bf /= N; bl /= N; by /= N;

        // Le rapprochement dû aux arêtes tendues, puis la convergence générale.
        float[] pf = new float[N];
        float[] pl = new float[N];
        float[] py = new float[N];
        for (int i = 0; i < N; i++) {
            float pull = converge * 0.46f + collapse * 0.54f;
            // Un léger flottement avant la convergence : rien n'est jamais parfaitement
            // immobile dans cette famille, et un nœud figé se lit comme un repère d'éditeur.
            float drift = (1.0f - converge) * 0.055f;
            pf[i] = nf[i] + (bf - nf[i]) * pull + Mth.sin(t * 5.1f + i * 1.7f) * drift;
            pl[i] = nl[i] + (bl - nl[i]) * pull + Mth.cos(t * 4.3f + i * 2.3f) * drift;
            py[i] = ny[i] + (by - ny[i]) * pull + Mth.sin(t * 6.2f + i * 0.9f) * drift * 0.7f;
        }

        // ---- 1. LES ARÊTES --------------------------------------------------------------
        for (int e = 0; e < edges.length; e++) {
            int i = edges[e][0];
            int j = edges[e][1];
            // Chaque arête naît avec son nœud le plus tardif, et se tend à son tour.
            float born = 0.10f + Math.max(i, j) * 0.012f;
            if (t < born) continue;
            // L'ORDRE DE TENSION est tiré d'un hachage, pas séquentiel : une tension qui
            // ferait le tour du graphe dans l'ordre des indices se lirait comme un balayage.
            float tenseAt = 0.38f + hash(salt + 51, e * 29) * 0.30f;
            float tense = smooth(clamp((t - tenseAt) / 0.085f));
            float sagAmount = (1.0f - tense) * 0.28f;

            float len = dist(pf[i], pl[i], py[i], pf[j], pl[j], py[j]);
            if (len < 0.02f) continue;

            // La déchirure : l'arête casse par le MILIEU et les deux moitiés se rétractent.
            float cutIn = tear * 0.5f;
            int steps = Math.max(6, Math.min(22, Math.round(len * 7.0f)));
            float prevF = 0.0f, prevL = 0.0f, prevY = 0.0f;
            boolean havePrev = false;
            for (int s = 0; s <= steps; s++) {
                float u = s / (float) steps;
                if (tear > 0.001f && Math.abs(u - 0.5f) < cutIn) { havePrev = false; continue; }
                float f = pf[i] + (pf[j] - pf[i]) * u;
                float l = pl[i] + (pl[j] - pl[i]) * u;
                float y = py[i] + (py[j] - py[i]) * u;
                // LA CATÉNAIRE. cosh normalisé : nul aux deux bouts, maximal au milieu, et
                // la courbe est plus pleine qu'une parabole — c'est ce qui donne le poids.
                float k = (u - 0.5f) * 2.0f;
                float sag = (float) ((Math.cosh(1.65) - Math.cosh(1.65 * k)) / (Math.cosh(1.65) - 1.0));
                y -= sag * sagAmount * len * 0.42f;
                if (havePrev) {
                    /* DEUX tons quand la trame est tendue : une fois resserrees, onze aretes
                       d'un seul ton ne font plus qu'une masse grise. La parite les redecoupe. */
                    int rgb = tense > 0.55f ? ((e & 1) == 0 ? GLACIER : HORIZON)
                                            : (tense > 0.12f ? HORIZON : HALO);
                    float w = 0.023f + 0.016f * tense;
                    segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                            prevF, prevL, prevY, f, l, y,
                            w, 0.052f, rgb, opaque(a * (0.66f + 0.34f * tense) * (1.0f - tear * 0.4f)));
                }
                prevF = f; prevL = l; prevY = y;
                havePrev = true;
            }

            // L'ÉCLAT qui parcourt l'arête au moment où elle se tend. Il part du nœud de
            // plus petit indice et arrive à l'autre en 8,5 % de l'animation : c'est le seul
            // endroit du fichier où quelque chose se propage LE LONG d'une arête.
            float sparkU = (t - tenseAt) / 0.085f;
            if (sparkU > 0.0f && sparkU < 1.0f) {
                float f = pf[i] + (pf[j] - pf[i]) * sparkU;
                float l = pl[i] + (pl[j] - pl[i]) * sparkU;
                float y = py[i] + (py[j] - py[i]) * sparkU;
                float k = (sparkU - 0.5f) * 2.0f;
                float sag = (float) ((Math.cosh(1.65) - Math.cosh(1.65 * k)) / (Math.cosh(1.65) - 1.0));
                y -= sag * (1.0f - smooth(sparkU)) * 0.28f * len * 0.42f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        0.055f, 0.055f, 0.055f, SINGULAR, opaque(a));
            }
        }

        // ---- 2. LES NŒUDS ---------------------------------------------------------------
        for (int i = 0; i < N; i++) {
            float born = i * 0.0135f;
            float wake = smooth(clamp((t - born) / 0.040f));
            if (wake <= 0.01f) continue;
            float size = (0.095f + 0.030f * hash(salt + 44, i * 17)) * wake;
            // À l'effondrement, les nœuds ne rétrécissent pas : ils se superposent. C'est la
            // superposition qui fait le point blanc, et elle est lisible parce que les sept
            // arrivent ensemble.
            node(vc, mat, tmp, cx, cy, cz, dx, dz, pf[i], pl[i], py[i],
                    size, t * 2.1f + i * 0.9f, collapse, a);
        }

        // ---- 3. LE POINT ----------------------------------------------------------------
        if (collapse > 0.02f) {
            float puff = collapse * (1.0f - tear);
            box(vc, mat, tmp, cx, cy, cz, dx, dz, bf, bl, by,
                    0.085f * puff + 0.02f, 0.085f * puff + 0.02f, 0.085f * puff + 0.02f,
                    SINGULAR, opaque(a));
            // Six pointes courtes : sans elles, le point est un pixel blanc et rien d'autre.
            for (int s = 0; s < 6; s++) {
                float sf = (s == 0 ? 1 : s == 1 ? -1 : 0) * 0.20f * puff;
                float sl = (s == 2 ? 1 : s == 3 ? -1 : 0) * 0.20f * puff;
                float sy = (s == 4 ? 1 : s == 5 ? -1 : 0) * 0.20f * puff;
                segment(vc, mat, tmp, cx, cy, cz, dx, dz,
                        bf, bl, by, bf + sf, bl + sl, by + sy,
                        0.022f, 0.045f, GLACIER, opaque(a * puff));
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
