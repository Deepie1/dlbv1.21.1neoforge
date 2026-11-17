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
 * payée par l'Ombre. Pire : la famille ne peut pas être <b>violette</b>, parce que l'Ombre
 * possède déjà le violet (0x4D318E / 0x7100A7). Un second violet, ce serait refaire au niveau
 * du VFX l'erreur du glint Feu / Sang.</p>
 *
 * <p>D'où le choix : <b>corps presque noirs, horizon CYAN-BLANC</b>. Noir + blanc froid se
 * lit « coupure dans le réel », n'existe nulle part ailleurs dans le mod, et tient sur
 * n'importe quel fond. Le violet n'apparaît qu'à un seul endroit de tout le fichier — ce
 * qu'on aperçoit <i>de l'autre côté</i> de la faille — et il y signifie « il y a un
 * ailleurs ». L'orange, lui, ne sort que quand le vide rend ce qu'il a pris.</p>
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

    // =================================================================================
    //  PALETTE
    // =================================================================================

    /** L'intérieur. Employé UNIQUEMENT en lèvre intérieure, jamais en aplat de remplissage. */
    private static final int NOTHING   = 0x04030A;
    /** Un cran au-dessus du rien : l'ombre portée d'une lèvre. */
    private static final int ABYSS     = 0x0C0A18;
    /** Corps des masses. {@code DeepElement.VOID.primary} relevé vers le bleu : le gris pur disparaît. */
    private static final int BASALT    = 0x1A1726;
    /** Face éclairée d'un corps, arêtes mortes de la grille. */
    private static final int SLATE     = 0x2E2B40;
    /** Première teinte franchement froide : la transition vers l'horizon. */
    private static final int HALO      = 0x4A5E78;
    /** <b>Teinte maîtresse.</b> Le cyan de l'horizon — c'est elle qui dit « vide » et non « ombre ». */
    private static final int HORIZON   = 0x7FD4E8;
    /** Surexposition de l'horizon : les lèvres vives, les nœuds encore intacts. */
    private static final int GLACIER   = 0xBFF2FF;
    /** Le fil de l'anneau. Jamais en aplat, jamais sur plus d'un cube d'épaisseur. */
    private static final int SINGULAR  = 0xFFFFFF;

    /** Matière ÉTRANGÈRE n°1 — ce que le vide avale : le sol du monde. */
    private static final int ENDSTONE  = 0xD8D2A4;
    /** Sa face d'ombre. Sans elle, un fragment avalé est une tache jaune. */
    private static final int ENDSTONE_DK = 0x8A855F;
    /** Matière ÉTRANGÈRE n°2 — l'unique violet du fichier : l'AUTRE CÔTÉ, aperçu dans la fente. */
    private static final int CHORUS    = 0x8E5BA6;
    /** Ce qui RESSORT du vide, en complémentaire du cyan. N'apparaît qu'à la restitution. */
    private static final int INVERT    = 0xFF9A4A;

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
