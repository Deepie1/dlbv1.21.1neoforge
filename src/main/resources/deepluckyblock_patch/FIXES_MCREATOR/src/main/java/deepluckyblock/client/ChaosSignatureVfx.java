package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T397-CHAOS — élément <b>CHAOS</b> : trois signatures, et <b>rien d'autre que des Pixels
 * 3D</b>.
 *
 * <h2>La règle de l'élément, et elle ne souffre aucune exception</h2>
 *
 * <p><b>Aucun corps n'est coloré. Toute la couleur est sur les arêtes.</b> Pas une seule
 * ligne de ce fichier n'appelle {@code emitVoxelCube} : la totalité de la géométrie —
 * armature comprise — passe par {@link VoidPixelManager}, donc par le core shader
 * {@code void_pixel}, donc par des faces en <b>niveaux de gris</b> surmontées d'arêtes
 * lumineuses dont la teinte défile.</p>
 *
 * <p>La première version de ce fichier peignait les structures en violets unis et réservait
 * les Pixels 3D aux étincelles. C'était un contresens : un corps violet et une arête violette
 * se disputent l'œil, la forme devient une tache, et il faut alors monter les corps en
 * luminance pour les récupérer — ce qui les fait rivaliser avec les arêtes. Le gris règle le
 * conflit à la racine. Il ne reste qu'une variable pour dire « ceci est devant cela » (la
 * luminance, trente-deux crans) et une pour dire « ceci est vivant » (la teinte de l'arête).</p>
 *
 * <h2>Le vrai sujet : que le titre se lise en une image</h2>
 *
 * <p>La refonte précédente échouait sur le point le plus simple. « Boucle Kaléidoscopique »
 * montrait six branches rayonnantes : un rosace, pas un kaléidoscope. « Sphère d'Entropie »
 * montrait une sphère qui perd des cubes : une sphère abîmée, pas de l'entropie. « Cicatrice
 * du Big Bang » commençait par quatre-vingts ticks d'aspiration : une implosion, donc
 * exactement l'inverse de ce que le titre annonce.</p>
 *
 * <p>Les trois sont refaites autour de <b>ce qui définit le mot</b>, et non autour de ce qui
 * en est l'ambiance :</p>
 *
 * <ol>
 *   <li><b>Un kaléidoscope, c'est une symétrie MIROIR répétée.</b> Pas une rotation : un
 *       groupe diédral. Chaque motif est copié douze fois — six rotations, chacune doublée de
 *       sa réflexion — et les <b>parois de miroir</b> sont visibles entre les secteurs. C'est
 *       le reflet, pas la répétition, qui fait reconnaître l'objet au premier coup d'œil.</li>
 *   <li><b>L'entropie, c'est la perte d'information, et elle est visible dans le GRIS.</b> La
 *       sphère commence en treillis parfait, toutes cellules au même gris, toutes arêtes en
 *       luminance seule. À mesure que le front passe, chaque cellule tire son gris au hasard
 *       et son mode d'arête monte vers le spectre complet : le désordre se lit littéralement
 *       dans la dispersion des valeurs, et pas seulement dans les positions.</li>
 *   <li><b>Un big bang, c'est un point qui devient un univers.</b> Il commence par un point
 *       et finit par une coquille en expansion dont chaque fragment traîne derrière lui une
 *       <b>traînée radiale</b> — la direction unique de toute la matière. L'aspiration
 *       initiale est ramenée à seize pour cent du temps : c'est une inspiration, pas le
 *       sujet.</li>
 * </ol>
 */
public final class ChaosSignatureVfx {

    private ChaosSignatureVfx() {}

    // =================================================================================
    //  IDENTIFIANTS
    // =================================================================================

    /** I — Boucle Kaléidoscopique : douze reflets, qui perdent puis retrouvent l'accord. */
    public static final int KALEIDO = 0;
    /** II — Sphère d'Entropie : un treillis parfait que le désordre traverse. */
    public static final int ENTROPY = 1;
    /** III — Cicatrice du Big Bang : un point, puis un univers, puis une marque. */
    public static final int BIGBANG = 2;
    /** IV — Cascade de Bifurcation : une équation qui se dédouble jusqu'au brouillard. */
    public static final int FEIGEN = 3;
    /** V — Le Grain de Trop : un tas qui monte en silence, puis tombe d'un seul bloc. */
    public static final int SANDPILE = 4;

    // =================================================================================
    //  L'ÉCHELLE DE GRIS — la seule « palette » qui subsiste
    // =================================================================================
    //
    //  Trente-deux crans sont disponibles ; neuf sont nommés, et aucun n'est
    //  interchangeable. Le cran zéro n'est pas utilisé : à 0,055 de luminance il se
    //  confond avec le fond de nuit, et c'est la leçon payée à la première passe de
    //  lisibilité (« le noir n'est pas une couleur sur fond de nuit »).

    /** Masque. Ce qui doit CACHER ce qu'il y a derrière, sans jamais attirer l'œil. */
    private static final int G_MASK = 2;
    /** Corps profond : la matière au repos. */
    private static final int G_DEEP = 6;
    /** Corps courant. Le gris de référence du treillis en ordre. */
    private static final int G_BODY = 11;
    /** Corps éclairé : une pièce qui passe devant une autre. */
    private static final int G_MID = 16;
    /** Armature : ce qui tient la forme et doit se lire à dix mètres. */
    private static final int G_FRAME = 21;
    /** Verre. Le gris des pièces qu'on doit voir en premier. */
    private static final int G_GLASS = 25;
    /** Éclat. Presque blanc, réservé aux événements. */
    private static final int G_FLASH = 29;
    /** Le cœur. Blanc franc, et il ne sert qu'à la singularité du Big Bang. */
    private static final int G_CORE = 31;

    // =================================================================================
    //  LES TEINTES D'ARÊTE — utilisées par les modes TINTED, DUAL, PULSE et EMBER
    // =================================================================================
    //
    //  Exprimées en position sur la roue (0..1), et non en RGB : le shader fait la
    //  conversion. Les modes RAINBOW, SPECTRUM, MONO et STROBE les ignorent.

    private static final float H_CYAN = 0.52f;
    private static final float H_BLUE = 0.62f;
    private static final float H_VIOLET = 0.74f;
    private static final float H_MAGENTA = 0.86f;
    private static final float H_ROSE = 0.93f;
    /** Le seul ton chaud du fichier. La braise de la cicatrice, et rien d'autre. */
    private static final float H_EMBER = 0.07f;

    // Raccourcis de mode, pour que la géométrie ci-dessous reste lisible.
    private static final int M_RAINBOW = VoidPixelManager.MODE_RAINBOW;
    private static final int M_TINTED = VoidPixelManager.MODE_TINTED;
    private static final int M_MONO = VoidPixelManager.MODE_MONO;
    private static final int M_SPECTRUM = VoidPixelManager.MODE_SPECTRUM;
    private static final int M_PULSE = VoidPixelManager.MODE_PULSE;
    private static final int M_DUAL = VoidPixelManager.MODE_DUAL;
    private static final int M_STROBE = VoidPixelManager.MODE_STROBE;
    private static final int M_EMBER = VoidPixelManager.MODE_EMBER;

    // =================================================================================
    //  ENVELOPPE D'ENTREE ET DE SORTIE
    // =================================================================================

    /**
     * Fractions de la duree consacrees au fondu d'entree et au fondu de sortie.
     *
     * <p>L'entree est courte (6 % : neuf ticks sur cent cinquante) parce qu'une signature
     * d'impact doit <b>repondre au coup</b> : au-dela d'une demi-seconde on ne lit plus une
     * frappe mais une invocation. La sortie est presque trois fois plus longue (16 %, soit
     * vingt-quatre a trente-deux ticks) parce que l'oeil pardonne une arrivee rapide et
     * jamais un arrachage : c'est la coupure franche qui se remarque, pas le demarrage.</p>
     */
    private static final float FADE_IN = 0.06f;
    private static final float FADE_OUT = 0.16f;

    /**
     * Duree de chaque signature, en ticks, indexee par variante. Elle sert uniquement a
     * convertir l'avancement 0..1 en <b>ticks restants</b> : sans cette conversion on ne
     * peut pas savoir si un pixel qui nait a encore la place de mourir.
     */
    private static final int[] DURATION = { 150, 170, 200, 170, 200 };

    /** Ticks restants avant la fin de la signature, poses par {@link #render}. */
    private static int envRemain = 9999;

    /**
     * Enveloppe de l'image courante, posee par {@link #render} et appliquee par les deux
     * seuls emetteurs du fichier, {@link #put} et {@link #pixel}.
     *
     * <p>Un champ plutot qu'un parametre de plus sur seize : le rendu client est
     * mono-thread, la valeur est reecrite au premier geste de chaque image, et la faire
     * descendre a la main jusqu'aux deux cents sites d'emission aurait garanti qu'on en
     * oublie -- or il suffit d'un seul oubli pour qu'un cube reste opaque pendant que
     * tout le reste s'efface.</p>
     */
    private static float envAlpha = 1.0f;

    // =================================================================================
    //  DISPATCH
    // =================================================================================

    /**
     * @param t      avancement 0..1 de la signature
     * @param dirX   axe attaquant → cible, normalisé
     * @param ground ordonnée du sol relative au centre, déjà sondée par l'appelant
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant, int alpha,
                              int salt, float ground) {
        float dx = dirX;
        float dz = dirZ;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }

        // L'alpha arrive en 0..255 depuis le gestionnaire ; la couche de structure
        // travaille en 0..1, comme le shader.
        float a = Mth.clamp(alpha / 255.0f, 0.0f, 1.0f);

        // ENTREE ET SORTIE EN DOUCEUR, DE RIEN A RIEN. smooth() est un smoothstep : sa
        // derivee est nulle aux deux bornes, donc la signature ne « part » pas et ne
        // « s'arrete » pas -- elle accelere puis ralentit. Une rampe lineaire, elle, a une
        // pente constante : l'oeil voit le debut et la fin de la rampe comme deux ruptures,
        // ce qui est exactement le defaut qu'on corrige ici.
        envAlpha = smooth(t, 0.0f, FADE_IN) * (1.0f - smooth(t, 1.0f - FADE_OUT, 1.0f));
        if (envAlpha <= 0.0f) return;
        int duration = DURATION[variant >= 0 && variant < DURATION.length ? variant : 0];
        envRemain = Math.max(0, Math.round((1.0f - t) * duration));

        switch (variant) {
            case ENTROPY -> renderEntropy(cx, cy, cz, t, dx, dz, a, salt, ground);
            case BIGBANG -> renderBigBang(cx, cy, cz, t, dx, dz, a, salt, ground);
            case FEIGEN -> renderFeigenbaum(cx, cy, cz, t, dx, dz, a, salt, ground);
            case SANDPILE -> renderSandpile(cx, cy, cz, t, dx, dz, a, salt, ground);
            default -> renderKaleido(cx, cy, cz, t, dx, dz, a, salt, ground);
        }
        renderStrikes(cx, cy, cz, t, dx, dz, a, salt, variant, duration);
    }

    // =================================================================================
    //  LES COUPS — QUINZE GESTES, QUINZE FORMES
    // =================================================================================

    /**
     * T401b — CHAQUE TEMPS FORT A SON PROPRE GESTE.
     *
     * <h3>Ce que la première version faisait de travers</h3>
     *
     * <p>Elle avait une seule routine de frappe — une lance qui tombe, une couronne qui
     * s'ouvre — recopiée <b>quinze fois</b>, avec pour seules différences un angle qui
     * tournait et un facteur d'échelle. Six fois de suite dans le kaléidoscope, huit dans
     * la sphère, une dans le big bang : le même objet, à la même vitesse, avec la même
     * silhouette. Ce n'était pas une chorégraphie, c'était un métronome. Et comme la
     * deuxième occurrence n'apprend plus rien à l'œil, les quatorze suivantes devenaient
     * du bruit — exactement l'effet inverse de celui qu'on cherchait.</p>
     *
     * <h3>La règle appliquée ici</h3>
     *
     * <p><b>Aucun temps fort ne partage sa géométrie avec un autre.</b> Pas un angle de
     * décalage : une <i>forme</i>, une <i>direction d'arrivée</i>, une <i>vitesse</i> et un
     * <i>registre chromatique</i> qui lui appartiennent. Et chaque geste découle de ce que
     * sa signature <b>est</b> :</p>
     * <ul>
     *   <li>le <b>kaléidoscope</b> est un instrument à miroirs, donc ses coups sont des
     *       réflexions : ils convergent par douze, se croisent, se referment en cage, se
     *       démultiplient, s'enroulent, se replient ;</li>
     *   <li>la <b>sphère d'entropie</b> ne percute pas, elle <b>érode</b> : ses huit
     *       pulsations mordent, cisaillent, s'effondrent, vrillent, pleuvent, implosent,
     *       traversent, dissolvent — et le désordre monte à chacune, comme le niveau que
     *       le serveur accumule dans {@code CHAOS_ENTROPIES} ;</li>
     *   <li>le <b>big bang</b> n'a qu'un coup mais quatorze <b>tractions</b> avant lui :
     *       une traction n'est pas une frappe, elle <i>tire</i>, et sa forme doit s'étirer
     *       vers le foyer au lieu de s'abattre dessus.</li>
     * </ul>
     *
     * <p>Les ticks ne sont pas choisis pour l'image : ce sont ceux de
     * {@code applyKaleidoGameplay}, {@code applyEntropySphereGameplay} et
     * {@code applyBigBangGameplay}, à l'unité près. Si l'un bouge, l'autre doit bouger.</p>
     */
    private static final int[] BEATS_KALEIDO = { 30, 48, 66, 84, 102, 120 };
    private static final int[] BEATS_ENTROPY = { 22, 38, 54, 70, 86, 102, 118, 134 };
    private static final int BEAT_BIGBANG = 84;

    /** Ticks de préparation avant le contact, puis de résolution après. */
    private static final int STRIKE_WIND = 9;
    private static final int STRIKE_BURST = 8;

    private static final float TAU = 6.2831853f;

    private static void renderStrikes(float cx, float cy, float cz, float t,
                                      float dx, float dz, float a, int salt,
                                      int variant, int duration) {
        switch (variant) {
            case ENTROPY -> entropyStrikes(cx, cy, cz, t, dx, dz, a, salt, duration);
            case BIGBANG -> bigBangStrikes(cx, cy, cz, t, dx, dz, a, salt, duration);
            case FEIGEN -> feigenStrikes(cx, cy, cz, t, dx, dz, a, salt, duration);
            case SANDPILE -> sandpileStrikes(cx, cy, cz, t, dx, dz, a, salt, duration);
            default -> kaleidoStrikes(cx, cy, cz, t, dx, dz, a, salt, duration);
        }
    }

    // ---------------------------------------------------------------------------------
    //  KALÉIDOSCOPE — six réflexions, six gestes
    // ---------------------------------------------------------------------------------

    private static void kaleidoStrikes(float cx, float cy, float cz, float t,
                                       float dx, float dz, float a, int salt, int duration) {
        float now = t * duration;
        final float aim = 1.05f;
        float spin = t * TAU;

        for (int k = 0; k < BEATS_KALEIDO.length; k++) {
            float since = now - BEATS_KALEIDO[k];
            if (since < -STRIKE_WIND || since > STRIKE_BURST) continue;
            boolean pre = since < 0.0f;
            float w = pre ? 1.0f + since / STRIKE_WIND : 1.0f;   // 0 → 1 : la montée
            float r = pre ? 0.0f : since / STRIKE_BURST;         // 0 → 1 : la résolution
            float base = salt * 0.11f + k;

            switch (k) {
                // I. LES DOUZE CONVERGENTS — la symétrie d'ordre douze se referme d'un coup
                //    sur le corps, et le choc ressort en colonne. C'est le geste fondateur
                //    du kaléidoscope : douze reflets, un seul point d'arrivée.
                case 0 -> {
                    if (pre) {
                        float e = w * w;
                        for (int i = 0; i < 12; i++) {
                            float ang = i / 12.0f * TAU + base;
                            float head = 3.90f - 3.70f * e;
                            for (int j = 0; j < 5; j++) {
                                float rr = head + j * 0.21f;
                                put(cx, cy, cz, dx, dz,
                                        Mth.cos(ang) * rr, aim + 0.07f * j, Mth.sin(ang) * rr,
                                        0.112f - j * 0.014f, ang + spin,
                                        (i & 1) == 0 ? M_RAINBOW : M_SPECTRUM,
                                        j == 0 ? G_FLASH : G_BODY, 0.0f,
                                        a * (0.98f - j * 0.13f) * w);
                            }
                        }
                    } else {
                        float f = 1.0f - r;
                        for (int j = 0; j < 15; j++) {
                            float up = aim + j * 0.18f * (0.30f + r * 1.7f);
                            float rr = 0.11f + j * 0.034f;
                            float ca = j * 1.1f + spin * 2.0f;
                            put(cx, cy, cz, dx, dz, Mth.cos(ca) * rr, up, Mth.sin(ca) * rr,
                                    0.135f * f, ca, M_STROBE, j < 3 ? G_CORE : G_GLASS,
                                    0.0f, a * f * (1.0f - j * 0.042f));
                        }
                    }
                }

                // II. LES DEUX LAMES CROISÉES — une lame et son reflet balaient en sens
                //     contraires et se rencontrent exactement sur le corps. Rien ne tombe :
                //     ça TRANCHE, à l'horizontale, dans deux directions à la fois.
                case 1 -> {
                    float s = pre ? (w - 1.0f) : r;              // -1 → 0 → +1
                    float fade = 1.0f - Math.abs(s) * 0.42f;
                    for (int m = 0; m < 2; m++) {
                        float dir = (m == 0) ? 1.0f : -1.0f;
                        float cross = s * dir * 2.70f;
                        float tilt = (m == 0) ? 0.30f : -0.30f;
                        for (int i = -11; i <= 11; i++) {
                            float along = i * 0.175f;
                            float wx = (m == 0) ? cross : along;
                            float wz = (m == 0) ? along : cross;
                            put(cx, cy, cz, dx, dz, wx, aim + tilt * along, wz,
                                    0.155f - Math.abs(i) * 0.0068f, 0.055f,
                                    0.155f - Math.abs(i) * 0.0068f, tilt * 3.0f + spin,
                                    (m == 0) ? M_RAINBOW : M_SPECTRUM,
                                    (i == 0) ? G_FLASH : G_BODY, 0.0f, a * fade);
                        }
                    }
                    if (!pre && r < 0.55f) {
                        float f = 1.0f - r / 0.55f;
                        put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 0.36f * f + 0.07f,
                                spin * 3.0f, M_STROBE, G_CORE, 0.0f, a * f);
                    }
                }

                // III. LA CAGE QUI SE REFERME — six parois verticales descendent, se
                //      resserrent jusqu'à traverser le corps, puis se rouvrent sur le vide.
                //      Un écrasement, pas une percussion : la seule frappe sans projectile.
                case 2 -> {
                    float close = pre ? w : 1.0f;
                    float back = pre ? 0.0f : r;
                    float rad = 2.60f - 2.28f * (close * close) + 2.45f * back * back;
                    float fade = pre ? w : (1.0f - back);
                    for (int wall = 0; wall < 6; wall++) {
                        float ang = wall / 6.0f * TAU + spin * 0.35f;
                        for (int j = 0; j < 11; j++) {
                            put(cx, cy, cz, dx, dz,
                                    Mth.cos(ang) * rad, 0.10f + j * 0.225f, Mth.sin(ang) * rad,
                                    0.070f, 0.070f, 0.118f, ang,
                                    (j % 3 == 0) ? M_DUAL : M_RAINBOW,
                                    (j % 3 == 0) ? G_FRAME : G_BODY,
                                    (j % 3 == 0) ? H_CYAN : 0.0f, a * fade * 0.94f);
                        }
                    }
                    if (!pre && back < 0.70f) {
                        float f = 1.0f - back / 0.70f;
                        for (int i = 0; i < 12; i++) {
                            float ca = i / 12.0f * TAU + spin;
                            put(cx, cy, cz, dx, dz, Mth.cos(ca) * 0.24f,
                                    aim + 0.20f * Mth.sin(i * 2.1f), Mth.sin(ca) * 0.24f,
                                    0.165f * f, ca, M_SPECTRUM, G_CORE, 0.0f, a * f * 0.92f);
                        }
                    }
                }

                // IV. LE PRISME ÉCLATÉ — l'exact inverse du premier geste. UN éclat entre
                //     en diagonale par le haut ; DOUZE ressortent. Le kaléidoscope ne
                //     renvoie jamais ce qu'on lui donne : il le multiplie.
                case 3 -> {
                    if (pre) {
                        float e = w * w * w;                       // long guet, arrivée sèche
                        float hh = 4.30f - 4.12f * e;
                        for (int j = 0; j < 8; j++) {
                            float rr = hh + j * 0.15f;
                            put(cx, cy, cz, dx, dz, rr * 0.70f,
                                    aim + 1.75f * (1.0f - e) + j * 0.05f, rr * -0.70f,
                                    0.185f - j * 0.016f, spin * 4.0f,
                                    M_SPECTRUM, j == 0 ? G_CORE : G_FLASH, 0.0f,
                                    a * (1.0f - j * 0.10f) * Mth.clamp(w * 1.8f, 0.0f, 1.0f));
                        }
                    } else {
                        float out = r * r * 0.55f + r * 0.45f;
                        float f = 1.0f - r * 0.80f;
                        for (int i = 0; i < 12; i++) {
                            float ang = i / 12.0f * TAU + base;
                            float pitch = ((i % 3) - 1) * 0.60f;
                            for (int j = 0; j < 4; j++) {
                                float rr = (0.22f + j * 0.32f) * (0.35f + out * 3.7f);
                                put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                                        aim + pitch * rr * 0.48f, Mth.sin(ang) * rr,
                                        0.105f - j * 0.013f, ang + spin,
                                        (i % 3 == 0) ? M_STROBE : M_RAINBOW,
                                        (i % 3 == 0) ? G_GLASS : G_BODY, 0.0f,
                                        a * f * (0.95f - j * 0.14f));
                            }
                        }
                    }
                }

                // V. LA SPIRALE DE REFLETS — un fil unique s'enroule du haut vers le bas en
                //    se resserrant, et mord à chaque tour. Le seul geste CONTINU des six :
                //    il ne connaît pas d'instant d'impact, il visse.
                case 4 -> {
                    float p = pre ? w * 0.58f : 0.58f + r * 0.42f;
                    float f = pre ? Mth.clamp(w * 2.2f, 0.0f, 1.0f) : (1.0f - r * 0.72f);
                    for (int i = 0; i < 34; i++) {
                        float u = i / 33.0f;
                        float lead = Mth.clamp(p * 1.30f - u * 0.30f, 0.0f, 1.0f);
                        if (lead <= 0.01f) continue;
                        float ang = u * TAU * 2.6f + spin * 1.5f + base;
                        float rr = (1.70f - 1.26f * p) * (0.40f + 0.60f * (1.0f - u * 0.5f));
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, 2.45f - u * 2.30f,
                                Mth.sin(ang) * rr, 0.086f + 0.032f * Mth.sin(u * 9.0f), ang,
                                (i % 4 == 0) ? M_SPECTRUM : M_RAINBOW,
                                (i % 4 == 0) ? G_FRAME : G_BODY, 0.0f, a * f * lead);
                    }
                }

                // VI. LE REPLIEMENT — dix-huit rayons s'écrasent au centre et toute la
                //     figure rejaillit en colonne. C'est le coup final : le plus large à la
                //     préparation, le plus haut à la sortie.
                default -> {
                    if (pre) {
                        float e = w * w;
                        for (int i = 0; i < 18; i++) {
                            float ang = i / 18.0f * TAU + base;
                            for (int j = 0; j < 6; j++) {
                                float rr = (4.40f - 4.05f * e) * (0.55f + j * 0.11f);
                                put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                                        aim + (1.0f - e) * 0.95f * Mth.sin(i * 0.9f),
                                        Mth.sin(ang) * rr, 0.092f, ang + spin, M_RAINBOW,
                                        (j == 0) ? G_FLASH : G_BODY, 0.0f, a * w * 0.90f);
                            }
                        }
                    } else {
                        float f = 1.0f - r * 0.84f;
                        for (int j = 0; j < 24; j++) {
                            float up = aim + j * 0.27f * (0.22f + r * 2.1f);
                            float sway = 0.32f * Mth.sin(j * 0.8f + spin * 2.0f) * (0.3f + r);
                            float ca = j * 0.7f + spin * 3.0f;
                            put(cx, cy, cz, dx, dz, Mth.cos(ca) * sway, up, Mth.sin(ca) * sway,
                                    0.175f - j * 0.0046f, ca,
                                    (j % 2 == 0) ? M_SPECTRUM : M_STROBE,
                                    (j < 4) ? G_CORE : (j < 12 ? G_FLASH : G_GLASS),
                                    0.0f, a * f * (1.0f - j * 0.026f));
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    //  SPHÈRE D'ENTROPIE — huit façons de se défaire
    // ---------------------------------------------------------------------------------

    private static void entropyStrikes(float cx, float cy, float cz, float t,
                                       float dx, float dz, float a, int salt, int duration) {
        float now = t * duration;
        final float aim = 1.15f;
        float spin = t * TAU;

        for (int k = 0; k < BEATS_ENTROPY.length; k++) {
            float since = now - BEATS_ENTROPY[k];
            if (since < -STRIKE_WIND || since > STRIKE_BURST) continue;
            boolean pre = since < 0.0f;
            float w = pre ? 1.0f + since / STRIKE_WIND : 1.0f;
            float r = pre ? 0.0f : since / STRIKE_BURST;
            // Le désordre MONTE d'une pulsation à l'autre : ce n'est pas un ornement, c'est
            // la grandeur que le serveur empile dans CHAOS_ENTROPIES, plafonnée à six.
            float grit = 0.10f + k * 0.13f;
            float base = salt * 0.09f + k * 1.7f;

            switch (k) {
                // 1. LA MORSURE DE TREILLIS — douze cellules se détachent de la structure,
                //    viennent mordre, et repartent avec un morceau.
                case 0 -> {
                    for (int i = 0; i < 12; i++) {
                        float ang = i * 2.39996f + base;
                        float rr = pre ? (2.45f - 2.05f * w * w) : (0.38f + 2.1f * r * r);
                        float f = pre ? w : (1.0f - r);
                        float hh = 0.150f * (pre ? 1.0f : 1.0f - 0.42f * r);
                        float up = aim + Mth.sin(i * 1.7f) * 0.50f * rr * 0.42f;
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, up, Mth.sin(ang) * rr,
                                hh, ang + spin, M_TINTED, G_MID, H_VIOLET, a * f * 0.95f);
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr * 0.86f, up * 0.97f,
                                Mth.sin(ang) * rr * 0.86f, hh * 0.60f, -ang,
                                M_RAINBOW, G_BODY, 0.0f, a * f * 0.78f);
                    }
                }

                // 2. L'ANNEAU DE CISAILLEMENT — un disque mince se referme à l'horizontale
                //    et tranche à hauteur de torse. Plat, large, et tremblant.
                case 1 -> {
                    float rad = pre ? (2.95f - 2.52f * w) : (0.42f + 3.0f * r * r);
                    float f = pre ? w : (1.0f - r * 0.88f);
                    for (int i = 0; i < 36; i++) {
                        float ang = i / 36.0f * TAU + spin * 0.8f;
                        float wob = grit * 0.22f * Mth.sin(i * 3.1f + spin * 4.0f);
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * (rad + wob), aim + wob,
                                Mth.sin(ang) * (rad + wob),
                                0.145f, 0.038f, 0.145f, ang,
                                (i % 5 == 0) ? M_DUAL : M_MONO,
                                (i % 5 == 0) ? G_GLASS : G_MID,
                                H_CYAN, a * f * 0.92f);
                    }
                }

                // 3. L'EFFONDREMENT DE SECTEUR — un quartier entier de la coquille lâche et
                //    tombe en biais sur le corps. Asymétrique, et c'est tout l'intérêt :
                //    les sept autres gestes sont centrés, celui-ci arrive d'un seul côté.
                case 2 -> {
                    float fall = pre ? (1.0f - w) : 0.0f;
                    float f = pre ? Mth.clamp(w * 1.7f, 0.0f, 1.0f) : (1.0f - r);
                    float side = base;
                    for (int i = 0; i < 9; i++) {
                        float ang = side + (i - 4) * 0.165f;
                        for (int j = 0; j < 7; j++) {
                            float rr = 0.60f + j * 0.32f;
                            float drop = fall * (2.60f + j * 0.30f);
                            float slip = pre ? 0.0f : r * r * 1.3f;
                            put(cx, cy, cz, dx, dz,
                                    Mth.cos(ang) * (rr + slip), aim + drop - j * 0.06f + slip * 0.4f,
                                    Mth.sin(ang) * (rr + slip),
                                    0.125f - j * 0.008f, ang + j * 0.4f,
                                    M_PULSE, (j < 2) ? G_FLASH : G_MID, H_MAGENTA,
                                    a * f * (0.95f - j * 0.08f));
                        }
                    }
                }

                // 4. LE VRILLAGE — deux hélices de sens contraires se serrent sur le corps
                //    et le tordent. Aucun autre geste ne tourne sur lui-même.
                case 3 -> {
                    float tight = pre ? w : 1.0f - r * 0.55f;
                    float f = pre ? w : (1.0f - r * 0.9f);
                    for (int helix = 0; helix < 2; helix++) {
                        float sense = (helix == 0) ? 1.0f : -1.0f;
                        for (int i = 0; i < 24; i++) {
                            float u = i / 23.0f;
                            float ang = sense * (u * TAU * 2.2f + spin * 2.4f) + helix * 3.14159f;
                            float rr = (2.70f - 2.15f * tight) * (0.45f + 0.55f * Mth.sin(u * 3.14159f));
                            put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                                    0.20f + u * 2.15f, Mth.sin(ang) * rr,
                                    0.098f - 0.020f * Math.abs(u - 0.5f) * 2.0f, ang,
                                    M_SPECTRUM, (i % 5 == 0) ? G_FLASH : G_BODY,
                                    0.0f, a * f * 0.93f);
                        }
                    }
                }

                // 5. LA PLUIE DE CELLULES — ça tombe DROIT, du dessus, en colonne large.
                //    Aucune direction horizontale : après quatre gestes latéraux, l'œil a
                //    besoin d'un axe neuf.
                case 4 -> {
                    float f = pre ? Mth.clamp(w * 1.6f, 0.0f, 1.0f) : (1.0f - r * 0.95f);
                    float drop = pre ? (1.0f - w) : 0.0f;
                    for (int i = 0; i < 28; i++) {
                        float ang = i * 2.39996f + base;
                        float rr = 0.30f + (i % 7) * 0.305f;
                        float stagger = ((i * 37) % 13) / 13.0f;
                        float up = aim + 1.05f + drop * (3.4f + stagger * 1.8f)
                                - (pre ? 0.0f : r * 1.25f);
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, up, Mth.sin(ang) * rr,
                                0.108f + 0.030f * stagger, ang * 2.0f + spin,
                                (i % 6 == 0) ? M_STROBE : M_MONO,
                                (i % 6 == 0) ? G_GLASS : G_MID, 0.0f, a * f * 0.90f);
                    }
                    if (!pre && r < 0.5f) {
                        float g = 1.0f - r / 0.5f;
                        for (int i = 0; i < 14; i++) {
                            float ca = i / 14.0f * TAU;
                            float sp = 0.30f + r * 1.9f;
                            put(cx, cy, cz, dx, dz, Mth.cos(ca) * sp, 0.10f, Mth.sin(ca) * sp,
                                    0.088f * g, ca, M_MONO, G_DEEP, 0.0f, a * g * 0.75f);
                        }
                    }
                }

                // 6. L'IMPLOSION PONCTUELLE — tout ce qui traîne converge en UN point, et il
                //    ne reste rien. Le seul geste qui se termine par une absence plutôt que
                //    par un éclat : après cinq gestes de plus en plus bruyants, un vide.
                case 5 -> {
                    if (pre) {
                        float e = w * w * w;
                        for (int i = 0; i < 40; i++) {
                            float ang = i * 2.39996f + base;
                            float pitchU = ((i * 11) % 17) / 17.0f;
                            float rr = (3.20f - 3.05f * e) * (0.5f + 0.5f * pitchU);
                            put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                                    aim + (pitchU - 0.5f) * 2.4f * (1.0f - e) , Mth.sin(ang) * rr,
                                    0.070f + 0.055f * e, ang + spin * 2.0f,
                                    M_EMBER, G_FRAME, H_EMBER, a * (0.45f + 0.55f * e));
                        }
                        if (w > 0.80f) {
                            float g = (w - 0.80f) / 0.20f;
                            put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 0.10f + 0.45f * g,
                                    spin * 6.0f, M_SPECTRUM, G_CORE, 0.0f, a * g);
                        }
                    } else {
                        // Le point s'éteint en se contractant : pas d'onde, pas de débris.
                        float g = 1.0f - r;
                        put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 0.62f * g * g,
                                spin * 6.0f, M_SPECTRUM, G_CORE, 0.0f, a * g);
                        put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 0.95f * g * g * g,
                                -spin * 2.0f, M_DUAL, G_DEEP, H_VIOLET, a * g * 0.45f);
                    }
                }

                // 7. L'ONDE DE DÉSAGRÉGATION — une coquille sphérique naît SUR le corps et
                //    le traverse en s'élargissant. Le seul geste centrifuge pur.
                case 6 -> {
                    float grow = pre ? w * 0.25f : 0.25f + r * 0.75f;
                    float f = pre ? w * 0.8f : (1.0f - r * 0.92f);
                    float rr = 0.25f + grow * 3.1f;
                    for (int ringIdx = 0; ringIdx < 5; ringIdx++) {
                        float lat = (ringIdx - 2) * 0.38f;
                        float ringR = rr * Mth.sqrt(Math.max(0.05f, 1.0f - lat * lat * 0.62f));
                        int n = 8 + ringIdx * 2;
                        for (int i = 0; i < n; i++) {
                            float ang = i / (float) n * TAU + ringIdx * 0.6f + spin;
                            float jitter = grit * 0.30f * Mth.sin(i * 5.3f + ringIdx);
                            put(cx, cy, cz, dx, dz, Mth.cos(ang) * (ringR + jitter),
                                    aim + lat * rr * 0.85f, Mth.sin(ang) * (ringR + jitter),
                                    0.100f * (1.0f - grow * 0.35f), ang,
                                    M_RAINBOW, (ringIdx == 2) ? G_FLASH : G_BODY,
                                    0.0f, a * f * 0.94f);
                        }
                    }
                }

                // 8. LA DISSOLUTION — un nuage dense enveloppe le corps, puis part en
                //    poussière vers le haut. Dernière pulsation, et la seule qui ne fasse
                //    aucune figure nette : à ce niveau d'entropie, il n'y a plus de forme.
                default -> {
                    float f = pre ? w : (1.0f - r);
                    for (int i = 0; i < 46; i++) {
                        float ang = i * 2.39996f + base;
                        float seedU = ((i * 29) % 23) / 23.0f;
                        float cloud = pre ? (1.0f - w) * 2.1f + 0.50f : 0.50f + r * 2.7f;
                        float rr = cloud * (0.35f + 0.65f * seedU);
                        float up = aim + (seedU - 0.42f) * 1.5f * cloud
                                + (pre ? 0.0f : r * r * 1.7f);
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, up, Mth.sin(ang) * rr,
                                (0.055f + 0.075f * seedU) * (pre ? 1.0f : 1.0f - 0.4f * r),
                                ang * 3.0f + spin,
                                (i % 4 == 0) ? M_DUAL : M_TINTED,
                                (i % 4 == 0) ? G_FRAME : G_DEEP, H_ROSE, a * f * 0.88f);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    //  BIG BANG — quatorze tractions, puis UNE détonation
    // ---------------------------------------------------------------------------------

    private static void bigBangStrikes(float cx, float cy, float cz, float t,
                                       float dx, float dz, float a, int salt, int duration) {
        float now = t * duration;
        final float aim = 1.20f;
        float spin = t * TAU;

        // --- LES QUATORZE TRACTIONS (t+6+k*6). Ce ne sont PAS des coups : la singularité
        //     tire. La forme doit donc s'étirer vers le foyer et se resserrer d'une
        //     traction à l'autre, au lieu de s'abattre. Elles sont brèves et sans éclat :
        //     garder un impact à chacune aurait écrasé la détonation qui les suit.
        for (int k = 0; k < 14; k++) {
            float since = now - (6 + k * 6);
            if (since < -3.0f || since > 5.0f) continue;
            float u = Mth.clamp((since + 3.0f) / 8.0f, 0.0f, 1.0f);
            float f = Mth.sin(u * 3.14159f);                 // naît et meurt d'un souffle
            float grip = 3.40f - k * 0.165f;                 // l'étau se referme
            for (int i = 0; i < 5; i++) {
                float ang = k * 2.39996f + i * 1.2566f + salt * 0.07f;
                for (int j = 0; j < 10; j++) {
                    float rr = grip * (1.0f - u * 0.82f) * (0.26f + j * 0.090f);
                    put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                            aim + 0.60f * (1.0f - u) * Mth.sin(i * 1.9f + k) + j * 0.028f,
                            Mth.sin(ang) * rr,
                            0.090f - j * 0.0052f, ang,
                            M_TINTED, (j < 2) ? G_FRAME : G_DEEP, H_BLUE,
                            a * f * (0.92f - j * 0.062f));
                }
            }
        }

        // --- LA DÉTONATION (t+84), en trois temps qui ne se ressemblent pas : la matière
        //     se compacte, le noyau claque, l'onde part. C'est le seul geste du mod qui
        //     dure plus de vingt ticks, et il n'y en a qu'un par animation.
        float bang = now - BEAT_BIGBANG;
        if (bang >= -16.0f && bang <= 26.0f) {

            // TEMPS 1 — LA COMPRESSION. Une coquille de soixante éclats se referme.
            if (bang < 0.0f) {
                float c = 1.0f + bang / 16.0f;                // 0 → 1
                float e = c * c * c;
                for (int i = 0; i < 60; i++) {
                    float ang = i * 2.39996f + salt * 0.05f;
                    float lat = ((i * 13) % 19) / 19.0f - 0.5f;
                    float rr = (5.40f - 5.10f * e) * (0.55f + 0.45f * Mth.cos(lat * 3.0f));
                    put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, aim + lat * 3.4f * (1.0f - e * 0.7f),
                            Mth.sin(ang) * rr, 0.085f + 0.075f * e, ang + spin * 2.0f,
                            (i % 5 == 0) ? M_DUAL : M_TINTED,
                            (i % 5 == 0) ? G_GLASS : G_MID, H_BLUE, a * (0.40f + 0.60f * e));
                }
                if (c > 0.72f) {
                    float g = (c - 0.72f) / 0.28f;
                    put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 0.12f + 0.70f * g * g,
                            spin * 8.0f, M_SPECTRUM, G_CORE, 0.0f, a * g);
                }
            } else {

                // TEMPS 2 — L'ÉCLAIR, quatre ticks. Un noyau massif et seize rayons droits.
                if (bang < 4.0f) {
                    float g = 1.0f - bang / 4.0f;
                    put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f, 1.35f * g + 0.25f,
                            spin * 9.0f, M_SPECTRUM, G_CORE, 0.0f, a * g);
                    for (int i = 0; i < 16; i++) {
                        float ang = i / 16.0f * TAU;
                        float lat = ((i % 4) - 1.5f) * 0.55f;
                        for (int j = 1; j < 9; j++) {
                            float rr = j * (0.55f + (1.0f - g) * 0.9f);
                            put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, aim + lat * j * 0.22f,
                                    Mth.sin(ang) * rr, 0.26f - j * 0.022f, ang,
                                    M_STROBE, (j < 3) ? G_FLASH : G_GLASS, 0.0f,
                                    a * g * (1.0f - j * 0.09f));
                        }
                    }
                }

                // TEMPS 3 — L'ONDE DE CHOC, vingt-six ticks. Un anneau au sol qui court
                //     loin, et une calotte qui monte. Lente, pour qu'on la suive.
                float s = bang / 26.0f;
                float wave = 0.6f + s * 7.4f;
                float f = (1.0f - s) * (1.0f - s * 0.35f);
                int n = 44;
                for (int i = 0; i < n; i++) {
                    float ang = i / (float) n * TAU + spin * 0.25f;
                    put(cx, cy, cz, dx, dz, Mth.cos(ang) * wave, 0.12f + 0.30f * s,
                            Mth.sin(ang) * wave, 0.195f * f + 0.035f, 0.070f, 0.195f * f + 0.035f,
                            ang, (i % 4 == 0) ? M_EMBER : M_RAINBOW,
                            (i % 4 == 0) ? G_FLASH : G_BODY, H_EMBER, a * f * 0.95f);
                }
                for (int ringIdx = 0; ringIdx < 4; ringIdx++) {
                    float lat = 0.25f + ringIdx * 0.22f;
                    float capR = wave * 0.62f * Mth.sqrt(Math.max(0.04f, 1.0f - lat * lat));
                    int m = 10 + ringIdx * 3;
                    for (int i = 0; i < m; i++) {
                        float ang = i / (float) m * TAU + ringIdx * 0.5f - spin * 0.4f;
                        put(cx, cy, cz, dx, dz, Mth.cos(ang) * capR,
                                aim + lat * wave * 0.72f, Mth.sin(ang) * capR,
                                0.115f * f + 0.02f, ang, M_RAINBOW, G_MID, 0.0f, a * f * 0.80f);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    //  CASCADE — six vagues, et chacune porte deux fois plus de coups que la précédente
    // ---------------------------------------------------------------------------------

    /**
     * SIX VAGUES, SIX GESTES, ET TOUS FRAPPENT LA VICTIME.
     *
     * <p>Le premier jet faisait tomber les impacts <i>sur le diagramme</i> — au point de
     * l'attracteur correspondant au rang du coup. L'idée était jolie et le résultat
     * n'attaquait rien : la matière se déployait dans l'espace sans jamais traverser le
     * corps visé. <b>Une figure n'est pas une attaque.</b> Ici les soixante-trois coups
     * arrivent tous sur la cible, à hauteur de torse, et la repartent de l'autre côté.</p>
     *
     * <p>Ce que la bifurcation garde : le <b>nombre</b>. Un, deux, quatre, huit, seize,
     * trente-deux. Ce n'est pas un décor, c'est ce qui dicte la forme de chaque vague —
     * on ne frappe pas à trente-deux comme on frappe à un.</p>
     *
     * <table>
     *   <tr><td>0</td><td>1 coup</td><td><b>la lance</b></td><td>longitudinale, elle
     *       traverse de part en part</td></tr>
     *   <tr><td>1</td><td>2 coups</td><td><b>les mâchoires</b></td><td>verticale, du haut
     *       et du bas, elles se referment sur le torse</td></tr>
     *   <tr><td>2</td><td>4 coups</td><td><b>les anneaux</b></td><td>quatre cercles
     *       verticaux qui se resserrent et se croisent dans le corps</td></tr>
     *   <tr><td>3</td><td>8 coups</td><td><b>la gerbe</b></td><td>frontale, un cône de
     *       pointes qui entre devant et ressort derrière</td></tr>
     *   <tr><td>4</td><td>16 coups</td><td><b>le tourbillon</b></td><td>rotatif, seize
     *       arcs qui enroulent la cible avant de la couper</td></tr>
     *   <tr><td>5</td><td>32 coups</td><td><b>la grêle</b></td><td>verticale et saturée,
     *       plus une onde au sol</td></tr>
     * </table>
     *
     * <p>Aucune de ces six géométries n'est une autre à l'échelle près, et aucune ne
     * partage son axe d'arrivée avec sa voisine : longitudinal, vertical, radial,
     * frontal, rotatif, puis vertical saturé. Les durées tombent de huit ticks à trois et
     * demi — la fin est une grêle, pas un métronome.</p>
     */
    private static void feigenStrikes(float cx, float cy, float cz, float t,
                                      float dx, float dz, float a, int salt, int duration) {
        float now = t * duration;
        float spin = t * TAU;
        final float aim = 1.10f;                     // hauteur du torse

        for (int k = 0; k < BEATS_FEIGEN.length; k++) {
            float since = now - BEATS_FEIGEN[k];
            float windK = 8.0f - k * 0.9f;
            float burstK = 7.0f - k * 0.7f;
            if (since < -windK || since > burstK) continue;
            boolean pre = since < 0.0f;
            float w = pre ? 1.0f + since / windK : 1.0f;   // 0 → 1 pendant l'approche
            float r01 = pre ? 0.0f : since / burstK;       // 0 → 1 pendant l'éclat
            float e = w * w;
            float fade = 1.0f - r01 * 0.88f;

            switch (k) {

                // ===== VAGUE 0 — UN COUP — LA LANCE ================================
                // Elle arrive par l'axe, de derriere l'attaquant, et ressort derriere la
                // cible. C'est le seul geste de la signature qui soit LONGITUDINAL.
                case 0: {
                    if (pre) {
                        float tip = -7.0f + 7.0f * e;
                        for (int j = 0; j < 20; j++) {
                            float back = tip - j * 0.33f;
                            float hh = 0.17f - j * 0.0065f;
                            put(cx, cy, cz, dx, dz, back, aim, 0.0f,
                                    hh * 1.9f, hh, hh, spin * 0.5f,
                                    (j == 0) ? M_STROBE : M_DUAL,
                                    (j == 0) ? G_CORE : G_FRAME, H_CYAN,
                                    a * w * (1.0f - j * 0.042f));
                        }
                    } else {
                        // LE PASSAGE. Le fut file de -1,2 a +4,6 : il TRAVERSE.
                        float head = -1.2f + 5.8f * r01;
                        for (int j = 0; j < 24; j++) {
                            float back = head - j * 0.30f;
                            float hh = 0.155f - j * 0.0050f;
                            put(cx, cy, cz, dx, dz, back, aim, 0.0f,
                                    hh * 2.1f, hh, hh, spin,
                                    (j < 2) ? M_STROBE : M_SPECTRUM,
                                    (j < 2) ? G_CORE : G_GLASS, H_CYAN,
                                    a * fade * (1.0f - j * 0.035f));
                        }
                        // L'ECLABOUSSURE au point d'entree, dans le plan perpendiculaire.
                        int n = 16;
                        for (int j = 0; j < n; j++) {
                            float ca = j / (float) n * TAU;
                            float rr = 0.20f + 1.45f * Mth.sqrt(r01);
                            put(cx, cy, cz, dx, dz, -0.10f,
                                    aim + sin(ca) * rr, cos(ca) * rr,
                                    0.085f, 0.085f, 0.085f, ca,
                                    M_DUAL, G_FRAME, H_CYAN, a * fade * 0.85f);
                        }
                    }
                    break;
                }

                // ===== VAGUE 1 — DEUX COUPS — LES MACHOIRES ========================
                // Deux lames PLATES, une qui tombe et une qui monte, qui se rejoignent
                // exactement sur le torse. Arrivee VERTICALE, et rien d'autre dans la
                // signature n'arrive par le haut sauf la grele — qui, elle, est un essaim.
                case 1: {
                    for (int jaw = 0; jaw < 2; jaw++) {
                        float dir = (jaw == 0) ? 1.0f : -1.0f;
                        float gap = pre ? (3.40f * (1.0f - e)) : 0.0f;
                        float yy = aim + dir * gap;
                        int n = 30;
                        for (int j = 0; j < n; j++) {
                            float u = j / (float) (n - 1) - 0.5f;
                            // Une lame, donc LARGE et MINCE : 2,9 de long, 0,05 d'epais.
                            float bite = pre ? 1.0f : (1.0f - 0.55f * r01);
                            put(cx, cy, cz, dx, dz, u * 2.90f * bite, yy,
                                    dir * 0.18f * (pre ? 1.0f : (1.0f - r01)),
                                    0.075f, 0.050f, 0.165f, 0.0f,
                                    (j % 6 == 0) ? M_STROBE : M_SPECTRUM,
                                    (j % 6 == 0) ? G_CORE : G_FRAME, H_MAGENTA,
                                    a * (pre ? w : fade) * 0.95f);
                        }
                    }
                    if (!pre) {
                        // LA MORSURE : un disque PLAT chasse dans le plan du contact.
                        int n = 34;
                        for (int j = 0; j < n; j++) {
                            float ca = j / (float) n * TAU + spin;
                            float rr = 0.25f + 2.55f * Mth.sqrt(r01);
                            put(cx, cy, cz, dx, dz, cos(ca) * rr, aim, sin(ca) * rr,
                                    0.115f * fade + 0.02f, 0.045f, 0.115f * fade + 0.02f,
                                    ca, M_STROBE, G_GLASS, H_MAGENTA, a * fade * 0.90f);
                        }
                    }
                    break;
                }

                // ===== VAGUE 2 — QUATRE COUPS — LES ANNEAUX ========================
                // Quatre cercles VERTICAUX, a quatre azimuts, qui se resserrent et se
                // croisent DANS le corps. Arrivee radiale par contraction.
                case 2: {
                    float rad = pre ? (3.30f - 2.95f * e) : (0.35f + 2.10f * r01);
                    for (int ring = 0; ring < 4; ring++) {
                        float az = ring * (TAU / 8.0f) + salt * 0.03f;
                        float ca0 = cos(az), sa0 = sin(az);
                        int n = 18;
                        for (int j = 0; j < n; j++) {
                            float p = j / (float) n * TAU;
                            float ox = cos(p) * rad, oy = sin(p) * rad;
                            put(cx, cy, cz, dx, dz, ox * ca0, aim + oy, ox * sa0,
                                    0.095f, 0.095f, 0.095f, p + spin,
                                    (ring % 2 == 0) ? M_DUAL : M_PULSE,
                                    (j % 5 == 0) ? G_GLASS : G_FRAME,
                                    (ring % 2 == 0) ? H_BLUE : H_VIOLET,
                                    a * (pre ? w : fade) * 0.92f);
                        }
                    }
                    if (!pre) {
                        // LE NOEUD : la ou les quatre anneaux se sont croises.
                        float cf = 1.0f - Mth.clamp(r01 / 0.5f, 0.0f, 1.0f);
                        if (cf > 0.0f) {
                            put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f,
                                    0.14f + 0.46f * cf, spin * 6.0f,
                                    M_STROBE, G_CORE, 0.0f, a * cf);
                        }
                    }
                    break;
                }

                // ===== VAGUE 3 — HUIT COUPS — LA GERBE FRONTALE ====================
                // Un cone de pointes qui entre PAR DEVANT et ressort DERRIERE : c'est le
                // seul geste dont la direction soit celle de l'attaque elle-meme.
                case 3: {
                    for (int i = 0; i < 8; i++) {
                        float ca = i / 8.0f * TAU + 0.21f;
                        float open = 1.15f;
                        float travel = pre ? (-4.30f + 4.10f * e) : (-0.20f + 4.00f * r01);
                        int n = 7;
                        for (int j = 0; j < n; j++) {
                            float back = travel - j * 0.27f;
                            // Le cone se referme en avançant : large derriere, pointu devant.
                            float sp = open * Mth.clamp((0.30f - back) * 0.42f, 0.06f, 1.0f);
                            float hh = 0.125f - j * 0.011f;
                            put(cx, cy, cz, dx, dz, back,
                                    aim + sin(ca) * sp, cos(ca) * sp,
                                    hh * 1.7f, hh, hh, ca,
                                    (j == 0) ? M_STROBE : M_SPECTRUM,
                                    (j == 0) ? G_CORE : G_MID, H_ROSE,
                                    a * (pre ? w : fade) * (1.0f - j * 0.09f));
                        }
                    }
                    break;
                }

                // ===== VAGUE 4 — SEIZE COUPS — LE TOURBILLON =======================
                // Seize arcs qui ENROULENT la cible sur deux tours avant de la couper.
                // Arrivee rotative : la matiere tourne au lieu de foncer.
                case 4: {
                    float wind = pre ? (1.0f - e) : 0.0f;
                    for (int i = 0; i < 16; i++) {
                        float base = i / 16.0f * TAU;
                        int n = 9;
                        for (int j = 0; j < n; j++) {
                            float u = j / (float) (n - 1);
                            // Deux tours d'enroulement pendant l'approche, puis la spire
                            // se vide d'un coup vers l'exterieur.
                            float ang = base + u * 2.6f + wind * TAU * 2.0f + spin * 2.2f;
                            float rr = pre ? (2.60f * wind + 0.45f + u * 0.5f)
                                           : (0.40f + 3.20f * r01 + u * 0.35f);
                            float yy = aim + (u - 0.5f) * (pre ? 1.30f : 0.55f);
                            put(cx, cy, cz, dx, dz, cos(ang) * rr, yy, sin(ang) * rr,
                                    0.105f - j * 0.006f, 0.062f, 0.105f - j * 0.006f, ang,
                                    (j == 0) ? M_STROBE : M_EMBER,
                                    (j == 0) ? G_FLASH : G_MID, H_EMBER,
                                    a * (pre ? w : fade) * (0.95f - j * 0.055f));
                        }
                    }
                    break;
                }

                // ===== VAGUE 5 — TRENTE-DEUX COUPS — LA GRELE ======================
                // Saturation. Trente-deux pierres dans un cylindre d'un bloc et demi
                // autour du corps, plus une onde au sol : a trente-deux, on ne vise plus,
                // on noie.
                default: {
                    for (int i = 0; i < 32; i++) {
                        float ang = i * 2.39996f + salt * 0.05f;
                        float rr = 0.18f + 1.30f * hash(i, salt + 17);
                        float lag = hash(i * 7, salt + 23) * 0.55f;
                        float fall = pre ? Mth.clamp((e - lag) / 0.45f, 0.0f, 1.0f)
                                         : 1.0f;
                        float yy = aim + 3.60f * (1.0f - fall) + 0.55f - r01 * 1.50f;
                        int n = 4;
                        for (int j = 0; j < n; j++) {
                            float hh = 0.105f - j * 0.016f;
                            put(cx, cy, cz, dx, dz, cos(ang) * rr,
                                    yy + j * 0.30f, sin(ang) * rr,
                                    hh, hh * 1.6f, hh, ang,
                                    (j == 0) ? M_STROBE : M_SPECTRUM,
                                    (j == 0) ? G_CORE : G_GLASS, 0.0f,
                                    a * (pre ? w : fade) * (0.95f - j * 0.14f));
                        }
                    }
                    if (!pre) {
                        // L'ONDE AU SOL : trente-deux impacts font un seul bruit.
                        float wv = 0.4f + 3.6f * Mth.sqrt(r01);
                        int n = 30;
                        for (int j = 0; j < n; j++) {
                            float ca = j / (float) n * TAU;
                            put(cx, cy, cz, dx, dz, cos(ca) * wv, 0.10f, sin(ca) * wv,
                                    0.12f * fade + 0.02f, 0.05f, 0.12f * fade + 0.02f, ca,
                                    M_SPECTRUM, G_FLASH, 0.0f, a * fade * 0.85f);
                        }
                    }
                    break;
                }
            }
        }

        // ===== LA FENETRE DE PERIODE TROIS — 132 / 140 / 148 ======================
        // En plein chaos, l'ordre revient : la carte logistique retrouve vraiment une
        // periode exacte vers r = 3,83. Trois anneaux nets se detachent de la couronne
        // et se REFERMENT sur la victime. C'est le seul geste repete de la signature --
        // il l'est parce que la periode, elle, est reellement exacte.
        //
        // La premiere version plantait ici une colonne verticale de quatorze cubes au-
        // dessus de la cible : une tour qui flottait sans rapport avec la figure. Un
        // geste doit sortir de ce que la signature EST, pas se poser a cote.
        for (int s = 0; s < 3; s++) {
            float since = now - (132 + s * 8);
            if (since < -5.0f || since > 6.0f) continue;
            boolean pre = since < 0.0f;
            float w = pre ? 1.0f + since / 5.0f : 1.0f;
            float r01 = pre ? 0.0f : since / 6.0f;
            float fade = pre ? w : (1.0f - r01 * 0.88f);

            for (int ring = 0; ring < 3; ring++) {
                // Les trois rayons de la fenetre : ils se detachent de la couronne...
                float base = 0.75f + ring * 0.95f;
                // ...puis tombent sur le corps.
                float rad = pre ? base : base * (1.0f - r01 * 0.92f);
                int n = 22;
                for (int j = 0; j < n; j++) {
                    float th = j / (float) n * TAU + ring * 0.42f + spin * 0.8f;
                    put(cx, cy, cz, dx, dz, cos(th) * rad,
                            aim + sin(th * 2.0f + ring) * 0.11f, sin(th) * rad,
                            0.105f, 0.140f, 0.105f, th,
                            (j % 7 == 0) ? M_STROBE : M_DUAL,
                            (j % 7 == 0) ? G_CORE : G_FRAME,
                            0.46f + ring * 0.21f, a * fade * 0.95f);
                }
            }
            if (!pre && r01 > 0.55f) {
                // LA RENCONTRE : les trois anneaux arrivent ensemble sur le torse.
                float cf = (r01 - 0.55f) / 0.45f;
                float g = 1.0f - cf;
                put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f,
                        0.16f + 0.42f * g, spin * 7.0f,
                        M_STROBE, G_CORE, 0.0f, a * g * 0.95f);
            }
        }
    }

    // ---------------------------------------------------------------------------------
    //  GRAIN DE TROP — trois frôlements, puis une seule fois tout
    // ---------------------------------------------------------------------------------

    /**
     * Trois micro-glissements et une avalanche. Les trois premiers sont <b>volontairement
     * maigres</b> : quelques cubes qui dévalent le flanc du tas, et rien d'autre. Ils ne
     * sont pas là pour frapper mais pour dire que ça tient encore — et pour que le
     * quatrième, qui dure vingt-cinq ticks au lieu de six, soit le seul événement de
     * l'animation. Une signature qui frappe sans arrêt ne peut pas, par construction,
     * produire un instant qui compte.
     */
    private static void sandpileStrikes(float cx, float cy, float cz, float t,
                                        float dx, float dz, float a, int salt, int duration) {
        float now = t * duration;
        float spin = t * TAU;

        // --- LES TROIS MICRO-GLISSEMENTS. Six ticks chacun, et ils rampent au sol.
        for (int k = 0; k < 3; k++) {
            float since = now - BEATS_SANDPILE[k];
            if (since < 0.0f || since > 6.0f) continue;
            float u = since / 6.0f;
            float f = (1.0f - u) * (1.0f - u);
            float side = k * 2.39996f + salt * 0.11f;
            for (int i = 0; i < 14; i++) {
                float along = i / 13.0f;
                float ang = side + (along - 0.5f) * 0.55f;
                float rr = 0.45f + along * (1.3f + u * 2.4f);
                put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                        0.55f - along * 0.42f + 0.18f * (1.0f - u), Mth.sin(ang) * rr,
                        0.105f - along * 0.030f, 0.070f, 0.105f - along * 0.030f, ang,
                        M_MONO, (i < 3) ? G_MID : G_DEEP, 0.0f, a * f * 0.85f);
            }
        }

        // --- L'AVALANCHE. Vingt-cinq ticks, le seul vrai coup de la signature.
        float since = now - BEATS_SANDPILE[3];
        if (since < -6.0f || since > 25.0f) return;

        if (since < 0.0f) {
            // LE DERNIER SOUFFLE : six ticks ou le tas se fige et le grain touche. Rien
            // ne bouge, et c'est ce silence-la qui fait le coup.
            float w = 1.0f + since / 6.0f;
            float g = w * w * w;
            put(cx, cy, cz, dx, dz, 0.0f, 1.25f, 0.0f, 0.10f + 0.55f * g,
                    spin * 10.0f, M_STROBE, G_CORE, 0.0f, a * g);
            return;
        }

        float u = since / 25.0f;
        float f = (1.0f - u) * (1.0f - u * 0.45f);

        // 1. LE CRATERE : le tas s'est vide par le centre, et le centre est un trou.
        for (int i = 0; i < 28; i++) {
            float ang = i / 28.0f * TAU + spin * 0.3f;
            float rr = 0.35f + 1.05f * Mth.sqrt(u);
            put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr, 0.12f + 0.55f * (1.0f - u),
                    Mth.sin(ang) * rr, 0.175f * f + 0.03f, 0.090f, 0.175f * f + 0.03f, ang,
                    M_EMBER, (i % 3 == 0) ? G_FLASH : G_MID, H_EMBER, a * f * 0.95f);
        }

        // 2. LA COULEE : huit langues de matiere qui descendent les flancs et vont loin.
        //    C'est elle qui porte le coup — large, basse, et dirigee vers l'exterieur.
        for (int lane = 0; lane < 8; lane++) {
            float ang = lane / 8.0f * TAU + salt * 0.05f;
            int n = 16;
            for (int i = 0; i < n; i++) {
                float along = i / (float) (n - 1);
                float lead = Mth.clamp(u * 2.2f - along * 0.9f, 0.0f, 1.0f);
                if (lead <= 0.02f) continue;
                float rr = 0.5f + along * (1.6f + u * 6.2f);
                float wob = 0.22f * Mth.sin(along * 7.0f + lane);
                put(cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr - Mth.sin(ang) * wob,
                        0.14f + 0.55f * (1.0f - along) * (1.0f - u * 0.6f),
                        Mth.sin(ang) * rr + Mth.cos(ang) * wob,
                        (0.150f - along * 0.055f), 0.085f, (0.150f - along * 0.055f), ang,
                        (i % 5 == 0) ? M_EMBER : M_MONO,
                        (i % 5 == 0) ? G_FLASH : (along < 0.4f ? G_MID : G_DEEP),
                        H_EMBER, a * f * lead * 0.95f);
            }
        }

        // 3. LA POUSSIERE : elle monte pendant que tout le reste descend. Sans elle, une
        //    avalanche ressemble a des cubes qui glissent ; avec elle, c'est un ecroulement.
        for (int i = 0; i < 34; i++) {
            float ang = i * 2.39996f + salt * 0.09f;
            float seedU = ((i * 17) % 23) / 23.0f;
            float rr = (0.4f + seedU * 2.6f) * (0.4f + u * 1.9f);
            put(cx, cy, cz, dx, dz, Mth.cos(ang) * rr,
                    0.20f + u * (1.4f + seedU * 2.6f), Mth.sin(ang) * rr,
                    0.060f + 0.055f * seedU, ang * 3.0f + spin,
                    M_TINTED, G_DEEP, H_EMBER, a * f * (0.40f + 0.45f * seedU));
        }
    }

    // =================================================================================
    //  I. BOUCLE KALÉIDOSCOPIQUE — 150 ticks
    // =================================================================================

    /**
     * Un vrai kaléidoscope : un <b>groupe diédral d'ordre six</b>.
     *
     * <h3>Ce qui fait qu'on le reconnaît, et que la version précédente n'avait pas</h3>
     *
     * <p>L'ancienne version disposait six branches identiques à soixante degrés. C'est une
     * symétrie de <b>rotation</b> — un rouet, une rosace, un logo. Un kaléidoscope est une
     * symétrie de <b>réflexion</b> : deux miroirs formant un angle de trente degrés, et
     * l'image que l'œil reçoit est le pavage du plan par les réflexions successives d'un
     * unique secteur. Chaque motif y apparaît donc <b>douze</b> fois — six fois droit, six
     * fois retourné — et c'est la paire droite/retournée, accolée le long de son axe, qui
     * déclenche la reconnaissance immédiate.</p>
     *
     * <p>Quatre éléments supplémentaires, tous indispensables au premier coup d'œil :</p>
     * <ul>
     *   <li>les <b>parois de miroir</b> : douze rayons de cubes, du centre au bord. Sans
     *       elles, l'œil voit un motif symétrique ; avec elles, il voit un <i>instrument</i>.
     *       Les six fentes de pliage et les six axes de réflexion ont deux gris différents,
     *       parce qu'ils n'ont pas le même rôle ;</li>
     *   <li>le <b>disque plein</b> : un kaléidoscope remplit son cercle bord à bord. Trente-
     *       quatre perles par secteur, donc quatre cent huit cubes, et non douze brindilles ;</li>
     *   <li>les <b>anneaux concentriques</b>, qui donnent la lecture radiale du motif ;</li>
     *   <li>la <b>cerclure</b> : le bord net du tube, qui ferme la figure.</li>
     * </ul>
     *
     * <h3>Et la boucle</h3>
     *
     * <p>Le titre dit « boucle ». Chaque perle dérive sur des sinusoïdes dont les fréquences
     * sont des <b>entiers</b> en fraction de la durée : à {@code t = 1}, toutes sont revenues
     * exactement à leur position de {@code t = 0}. La signature se referme sur elle-même, et
     * on peut la jouer en continu sans raccord.</p>
     */
    private static void renderKaleido(float cx, float cy, float cz, float t,
                                      float dx, float dz, float a, int salt, float ground) {
        final float YC = 1.05f;
        final int SECTORS = 6;
        /** Largeur du secteur fondamental : trente degres, l'angle des deux miroirs. */
        final float WEDGE = Mth.PI / 6.0f;
        final float CELL = Mth.PI / 3.0f;
        /**
         * NEUF ECLATS DE CINQ CUBES, et non quarante perles isolees.
         *
         * <p>Mesure au banc : trente-quatre perles independantes, copiees douze fois,
         * donnent bien une figure rigoureusement symetrique — et elle se lit comme un
         * confetti. L'oeil ne peut pas verifier une symetrie sur des points : il lui faut
         * une <b>forme</b> dont il puisse suivre le contour et reconnaitre le retournement.
         * Chaque eclat est donc un court segment de cinq cubes ; accole a son reflet le
         * long de l'axe du miroir, il forme un chevron, et c'est ce chevron repete douze
         * fois qui fait dire « kaleidoscope » avant meme d'avoir compte les secteurs.</p>
         */
        final int SHARDS = 9;
        final int PER = 5;

        float open = smooth(t, 0.01f, 0.26f);
        float radius = 0.42f + 2.42f * open;
        float spin = t * 2.05f;

        float chaos = smooth(t, 0.46f, 0.62f) * (1.0f - smooth(t, 0.70f, 0.80f));
        float relock = smooth(t, 0.78f, 0.83f) * (1.0f - smooth(t, 0.86f, 0.93f));
        float baseYaw = (float) Mth.atan2(dz, dx);

        // --- LES ECLATS, et leurs douze reflets ------------------------------------
        for (int sh = 0; sh < SHARDS; sh++) {
            float band = 0.16f + 0.78f * hash(salt + sh, 1);
            float aC = 0.055f + (WEDGE - 0.11f) * hash(salt + sh, 2);
            float dirR = (hash(salt + sh, 3) - 0.5f) * 0.40f;
            float dirA = (hash(salt + sh, 4) - 0.5f) * 0.17f;
            float size = 0.036f + 0.030f * hash(salt + sh, 5);
            // Harmoniques ENTIERES : c'est ce qui referme la boucle a t = 1.
            int k1 = 1 + (sh % 3);
            int k2 = 1 + ((sh * 7) % 4);
            float wobR = 0.070f * sin(Mth.TWO_PI * (k1 * t + hash(salt + sh, 6)));
            float wobA = 0.080f * sin(Mth.TWO_PI * (k2 * t + hash(salt + sh, 7)));

            int mode;
            float hue;
            int gray;
            int kind = (sh + salt) % 4;
            if (kind == 0) { mode = M_DUAL; hue = H_CYAN; gray = G_FRAME; }
            else if (kind == 1) { mode = M_RAINBOW; hue = 0.0f; gray = G_BODY; }
            else if (kind == 2) { mode = M_PULSE; hue = H_VIOLET; gray = G_MID; }
            else { mode = M_TINTED; hue = H_MAGENTA; gray = G_DEEP; }
            if (chaos > 0.45f && ((sh * 5 + salt) % 3) == 0) { mode = M_STROBE; gray = G_GLASS; }

            for (int c = 0; c < PER; c++) {
                float u = c / (PER - 1.0f) - 0.5f;
                float r0 = Mth.clamp(band + dirR * u + wobR, 0.08f, 1.0f);
                float a0 = Mth.clamp(aC + dirA * u + wobA, 0.012f, WEDGE - 0.012f);

                for (int sct = 0; sct < SECTORS; sct++) {
                    for (int m = 0; m < 2; m++) {
                        // LA REFLEXION. m = 0 : l'eclat tel quel. m = 1 : son image dans
                        // le miroir porte par l'axe a WEDGE. Les deux pavent la cellule de
                        // soixante degres, et six cellules font le tour.
                        float local = (m == 0) ? a0 : (CELL - a0);
                        float rr = r0;
                        if (chaos > 0.001f) {
                            int cid = sh * 97 + sct * 13 + m * 7;
                            local += chaos * 0.26f * (hash(salt + cid, 11) - 0.5f);
                            rr += chaos * 0.22f * (hash(salt + cid, 12) - 0.5f);
                        }
                        float ang = spin + sct * CELL + local;
                        float rad = rr * radius;
                        // LA GALETTE EST PLATE. Mesure : vingt centimetres d'epaisseur
                        // suffisaient a brouiller la symetrie des que la vue s'ecartait de
                        // la verticale, parce que la hauteur deplace la projection d'un
                        // reflet mais pas de son voisin. Un kaleidoscope est une image
                        // plane, et le rendu doit l'etre aussi.
                        float up = YC + 0.045f * (1.0f - rr) * (1.0f - rr);
                        float grow = Mth.clamp(open * 1.5f - rr * 0.30f, 0.0f, 1.0f);
                        put(cx, cy, cz, dx, dz, cos(ang) * rad, up, sin(ang) * rad,
                                size * grow, baseYaw + ang, mode,
                                relock > 0.35f ? G_FLASH : gray, hue,
                                a * (0.82f + 0.18f * relock) * grow);
                    }
                }
            }
        }

        // --- LES PAROIS DE MIROIR. Douze rayons continus : six fentes de pliage (entre
        //     cellules) et six axes de reflexion (au milieu de chaque cellule). C'est la
        //     piece qui transforme « motif symetrique » en « instrument d'optique ».
        for (int w = 0; w < 12; w++) {
            boolean fold = (w % 2) == 0;
            float ang = spin + w * WEDGE;
            int steps = 30;
            for (int k = 1; k <= steps; k++) {
                float rr = k / (float) steps;
                // Pendant le decrochage, les parois se rompent en pointille : un miroir
                // fele. Le test porte sur k, donc la rupture est stable d'une image a
                // l'autre au lieu de gresiller.
                if (chaos > 0.25f && hash(salt + w * 31, k * 17) < chaos * 0.55f) continue;
                float rad = rr * radius * 1.02f;
                float up = YC + 0.045f * (1.0f - rr) * (1.0f - rr);
                put(cx, cy, cz, dx, dz, cos(ang) * rad, up, sin(ang) * rad,
                        fold ? 0.040f : 0.026f, baseYaw + ang,
                        fold ? M_DUAL : M_MONO, fold ? G_GLASS : G_FRAME,
                        fold ? H_BLUE : 0.0f, a * (fold ? 0.98f : 0.80f) * open);
            }
        }

        // --- LES ANNEAUX CONCENTRIQUES. Un kaleidoscope se lit aussi en bandes.
        for (int ring = 0; ring < 3; ring++) {
            float rr = 0.34f + ring * 0.27f;
            int marks = 34 + ring * 14;
            for (int k = 0; k < marks; k++) {
                if (chaos > 0.3f && hash(salt + 400 + ring, k) < chaos * 0.6f) continue;
                float ang = spin * (ring % 2 == 0 ? 1.0f : -0.65f)
                        + k / (float) marks * Mth.TWO_PI;
                float rad = rr * radius;
                put(cx, cy, cz, dx, dz, cos(ang) * rad,
                        YC + 0.045f * (1.0f - rr) * (1.0f - rr), sin(ang) * rad,
                        0.019f, baseYaw + ang, M_MONO, G_MID, 0.0f, a * 0.62f * open);
            }
        }

        // --- LA CERCLURE. Le bord net du tube. Elle est CLAIRE et non masquante : un
        //     cercle presque noir sur fond de nuit ne ferme rien du tout.
        int rim = 86;
        for (int k = 0; k < rim; k++) {
            float ang = spin * 0.8f + k / (float) rim * Mth.TWO_PI;
            float rad = radius * 1.07f;
            put(cx, cy, cz, dx, dz, cos(ang) * rad, YC, sin(ang) * rad,
                    0.036f, baseYaw + ang, M_DUAL, G_GLASS, H_ROSE, a * 0.95f * open);
        }

        // --- LES ECLATS LIBRES. Au decrochage, le tube crache des perles par le bord.
        if (chaos > 0.30f && ((salt + (int) (t * 150.0f)) % 3) == 0) {
            int k = (salt + (int) (t * 150.0f) * 7) % rim;
            float ang = spin * 0.8f + k / (float) rim * Mth.TWO_PI;
            float rad = radius * 1.07f;
            float f = cos(ang) * rad;
            float lat = sin(ang) * rad;
            pixel(
                    WeaponWorldVfxManager.camX() + cx + f * dx - lat * dz,
                    WeaponWorldVfxManager.camY() + cy + YC,
                    WeaponWorldVfxManager.camZ() + cz + f * dz + lat * dx,
                    cos(ang) * 0.055f, 0.030f, sin(ang) * 0.055f,
                    0.085f, 26, M_STROBE, 0.0f, 0.22f, G_GLASS);
        }
        // Au realignement, une couronne de monochromes jaillit vers le haut : l'ordre
        // revenu se signale par une couleur qui se tait.
        if (relock > 0.5f && ((salt + (int) (t * 150.0f)) % 2) == 0) {
            float ang = hash(salt, (int) (t * 150.0f)) * Mth.TWO_PI;
            float rad = radius * 0.7f;
            float f = cos(ang) * rad;
            float lat = sin(ang) * rad;
            pixel(
                    WeaponWorldVfxManager.camX() + cx + f * dx - lat * dz,
                    WeaponWorldVfxManager.camY() + cy + YC,
                    WeaponWorldVfxManager.camZ() + cz + f * dz + lat * dx,
                    0.0, 0.085, 0.0, 0.07f, 20, M_MONO, 0.0f, 0.10f, G_FLASH);
        }

        // --- LE REFLET AU SOL. Le tube pose son disque par terre, en plus sombre.
        if (ground > -9.0f) {
            for (int k = 0; k < 44; k++) {
                float ang = -spin * 0.5f + k / 44.0f * Mth.TWO_PI;
                float rad = radius * (0.5f + 0.55f * hash(salt + 77, k));
                put(cx, cy, cz, dx, dz, cos(ang) * rad, ground + 0.03f, sin(ang) * rad,
                        0.040f, 0.016f, 0.040f, baseYaw, M_MONO, G_MASK, 0.0f,
                        a * 0.55f * open);
            }
        }
    }

    // =================================================================================
    //  II. SPHÈRE D'ENTROPIE — 170 ticks
    // =================================================================================

    /**
     * Un treillis parfait, et un front de désordre qui le traverse.
     *
     * <h3>Rendre l'entropie visible, et pas seulement la casse</h3>
     *
     * <p>L'ancienne version retirait des cubes d'une sphère en fil de fer. Une sphère qui
     * perd des morceaux n'est pas de l'entropie : c'est de l'érosion, et un spectateur y lit
     * « un objet abîmé ». L'entropie est la <b>perte d'information</b> — le passage d'un état
     * que trois nombres décrivent à un état qu'il faut énumérer cellule par cellule.</p>
     *
     * <p>Elle est donc mise en scène sur <b>quatre variables à la fois</b>, chacune partant
     * d'une valeur unique partagée par les trois cent quarante cellules, et chacune finissant
     * tirée au hasard :</p>
     *
     * <table><caption>Les quatre canaux du désordre</caption>
     *   <tr><th></th><th>en ordre</th><th>en désordre</th></tr>
     *   <tr><td>position</td><td>sur le treillis, au centimètre</td><td>déplacée, jusqu'à 60 cm</td></tr>
     *   <tr><td>taille</td><td>identique partout</td><td>de moitié à double</td></tr>
     *   <tr><td><b>gris</b></td><td><b>toutes au cran 16</b></td><td><b>tiré uniformément sur 32</b></td></tr>
     *   <tr><td>arête</td><td>MONO — luminance seule</td><td>SPECTRUM puis STROBE</td></tr>
     * </table>
     *
     * <p>La ligne du gris est celle qui porte l'effet. Au début, la sphère est d'un gris
     * rigoureusement uniforme : l'œil lit une surface. À la fin, les mêmes cellules couvrent
     * toute l'échelle du noir au blanc : l'œil ne lit plus rien, et c'est exactement ce qu'on
     * voulait montrer. <b>L'histogramme s'aplatit sous les yeux du spectateur.</b></p>
     *
     * <h3>Le front</h3>
     *
     * <p>Le désordre ne frappe pas partout en même temps — il <b>traverse</b> la sphère le
     * long de l'axe attaquant → cible, avec un bord déchiqueté. Il existe donc toujours, au
     * milieu de l'animation, une moitié encore propre à côté d'une moitié détruite : c'est
     * cette <i>cohabitation</i> qui se lit comme de l'entropie, là où une destruction
     * uniforme se lirait comme un fondu.</p>
     */
    private static void renderEntropy(float cx, float cy, float cz, float t,
                                      float dx, float dz, float a, int salt, float ground) {
        final float YC = 1.15f;
        final int LAT = 16;

        // Rayon final ramene de 1,82 a 1,56 : a 1,82, trente-quatre cellules sur
        // l'equateur sont espacees de 34 cm pour une largeur de 17 — donc un
        // pointille, et un pointille ne se lit jamais comme une surface ordonnee.
        float radius = 0.38f + 1.18f * smooth(t, 0.01f, 0.24f);
        // Monotone, et elle ne redescend jamais : le second principe n'a pas d'exception.
        float S = clamp((t - 0.05f) / 0.66f);
        float spin = t * 1.15f;
        float baseYaw = (float) Mth.atan2(dz, dx);

        for (int i = 0; i < LAT; i++) {
            float th = (i + 0.5f) / LAT * Mth.PI;
            float sinTh = sin(th);
            float cosTh = cos(th);
            int ring = Math.max(4, Math.round(LAT * 2.15f * sinTh));
            for (int k = 0; k < ring; k++) {
                float ph = k / (float) ring * Mth.TWO_PI + spin + i * 0.21f;
                // Position sur le treillis, en repère local (f, up, lat).
                float ux = sinTh * cos(ph);
                float uy = cosTh;
                float uz = sinTh * sin(ph);

                int cid = i * 131 + k * 7;
                // LE FRONT : il balaie l'axe f, avec un bord déchiqueté par le hachage.
                // 0,62 contre 0,38 : assez de hasard pour que le bord ne soit pas une
                // ligne droite, assez de balayage pour qu'on voie une direction.
                float thr = 0.62f * (1.0f - (ux + 1.0f) * 0.5f) + 0.38f * hash(salt + cid, 3);
                float d = clamp((S - thr) / 0.30f);

                float off = 0.62f * d * d;
                float jx = (hash(salt + cid, 4) - 0.5f) * off;
                float jy = (hash(salt + cid, 5) - 0.5f) * off;
                float jz = (hash(salt + cid, 6) - 0.5f) * off;

                // La cellule a fini sa course : elle se détache et part en Pixel libre.
                if (d >= 0.995f) {
                    if (((cid + (int) (t * 170.0f)) % 23) == 0) {
                        float f = (ux * radius + jx);
                        float la = (uz * radius + jz);
                        pixel(
                                WeaponWorldVfxManager.camX() + cx + f * dx - la * dz,
                                WeaponWorldVfxManager.camY() + cy + YC + uy * radius + jy,
                                WeaponWorldVfxManager.camZ() + cz + f * dz + la * dx,
                                ux * 0.022f, 0.012f, uz * 0.022f,
                                0.075f, 30, M_SPECTRUM, 0.0f, 0.34f,
                                (int) (hash(salt + cid, 7) * 31.0f));
                    }
                    continue;
                }

                float size = 0.085f * (1.0f + 1.05f * d * (hash(salt + cid, 8) - 0.45f));
                // LE GRIS : du cran 16 pour tout le monde, vers un tirage uniforme.
                int gray = Math.round(Mth.lerp(d, 6.0f, 15.0f + hash(salt + cid, 9) * 16.0f));
                // L'ARÊTE : la discipline monochrome cède par paliers jusqu'au stroboscope.
                int mode;
                if (d < 0.02f) mode = M_MONO;
                else if (d < 0.30f) mode = M_RAINBOW;
                else if (d < 0.62f) mode = M_SPECTRUM;
                else mode = M_STROBE;

                float f = ux * radius + jx;
                float up = YC + uy * radius + jy;
                float la = uz * radius + jz;
                put(cx, cy, cz, dx, dz, f, up, la, size,
                        baseYaw + ph + d * 2.4f, mode, gray, H_CYAN, a * (1.0f - 0.25f * d));
            }
        }

        // --- LES TROIS GRANDS CERCLES. Ils cèdent EN DERNIER : ce sont eux qui gardent la
        //     mémoire de la forme initiale, et sans cette mémoire le spectateur ne saurait
        //     plus, à la fin, ce qui a été détruit.
        for (int c = 0; c < 3; c++) {
            int seg = 44;
            for (int k = 0; k < seg; k++) {
                float ang = k / (float) seg * Mth.TWO_PI + spin * (1.0f + c * 0.3f);
                float ux, uy, uz;
                if (c == 0) { ux = cos(ang); uy = sin(ang); uz = 0.0f; }
                else if (c == 1) { ux = cos(ang); uy = 0.0f; uz = sin(ang); }
                else { ux = 0.0f; uy = cos(ang); uz = sin(ang); }
                float thr = 0.55f + 0.42f * hash(salt + 600 + c * 29, k);
                if (S > thr) continue;
                float rr = radius * 1.015f;
                put(cx, cy, cz, dx, dz, ux * rr, YC + uy * rr, uz * rr,
                        0.034f, baseYaw + ang, M_DUAL, G_GLASS, H_BLUE, a * 0.95f);
            }
        }

        // --- LE NOYAU. Il grossit exactement comme l'armature maigrit, et il est le seul
        //     objet de la signature qui soit TOUJOURS au spectre complet : le désordre y
        //     est déjà total, c'est de là qu'il vient.
        float coreR = 0.10f + 0.40f * S;
        for (int v = 0; v < 6; v++) {
            float ux = (v == 0 ? 1 : v == 1 ? -1 : 0);
            float uy = (v == 2 ? 1 : v == 3 ? -1 : 0);
            float uz = (v == 4 ? 1 : v == 5 ? -1 : 0);
            for (int k = 0; k <= 6; k++) {
                float u = k / 6.0f;
                float rr = coreR * u;
                put(cx, cy, cz, dx, dz, ux * rr, YC + uy * rr, uz * rr,
                        0.030f + 0.030f * (1.0f - u), baseYaw + t * 6.0f,
                        M_SPECTRUM, Math.round(Mth.lerp(u, 31.0f, 12.0f)), 0.0f, a);
            }
        }

        // --- LE FANTÔME. Entre 88 % et 95 %, le treillis d'origine réapparaît une seconde,
        //     très pâle, puis s'éteint. C'est la règle « le désordre doit être réversible à
        //     l'œil » : sans ce rappel, personne ne peut dire ce qui a été perdu.
        float ghost = smooth(t, 0.88f, 0.91f) * (1.0f - smooth(t, 0.93f, 0.97f));
        if (ghost > 0.01f) {
            for (int i = 0; i < LAT; i += 2) {
                float th = (i + 0.5f) / LAT * Mth.PI;
                int ring = Math.max(4, Math.round(LAT * 2.15f * sin(th)));
                for (int k = 0; k < ring; k += 2) {
                    float ph = k / (float) ring * Mth.TWO_PI + spin + i * 0.21f;
                    float ux = sin(th) * cos(ph);
                    float uy = cos(th);
                    float uz = sin(th) * sin(ph);
                    put(cx, cy, cz, dx, dz, ux * radius, YC + uy * radius, uz * radius,
                            0.060f, baseYaw, M_MONO, G_MID, 0.0f, a * ghost * 0.55f);
                }
            }
        }

        // --- LA TRACE AU SOL. Elle s'efface au même rythme : l'ordre s'en va aussi par terre.
        if (ground > -9.0f) {
            for (int band = 0; band < 2; band++) {
                int marks = band == 0 ? 30 : 20;
                float rr = radius * (band == 0 ? 1.10f : 0.62f);
                for (int m = 0; m < marks; m++) {
                    if (hash(salt + 907 + band * 211, m * 29) < S) continue;
                    float ang = m / (float) marks * Mth.TWO_PI - spin * (band == 0 ? 0.5f : -0.8f);
                    put(cx, cy, cz, dx, dz, cos(ang) * rr, ground + 0.03f, sin(ang) * rr,
                            0.060f, 0.018f, 0.060f, baseYaw, M_MONO,
                            band == 0 ? G_FRAME : G_BODY, 0.0f, a * 0.62f);
                }
            }
        }
    }

    // =================================================================================
    //  III. CICATRICE DU BIG BANG — 200 ticks
    // =================================================================================

    /**
     * Un point, puis un univers, puis une marque.
     *
     * <h3>Pourquoi l'aspiration passe de quatre-vingts ticks à trente</h3>
     *
     * <p>L'ancienne version consacrait quarante-deux pour cent de sa durée à faire converger
     * la matière vers un point. Vue pour la première fois, elle se lit donc comme une
     * <b>implosion</b> — le contraire du titre. L'aspiration est un effet d'annonce : elle
     * doit être assez longue pour qu'on la remarque, assez courte pour qu'on ne croie pas
     * qu'elle est le sujet. Seize pour cent.</p>
     *
     * <h3>Ce qui rend une explosion lisible comme un big bang</h3>
     *
     * <p><b>La traînée radiale.</b> Une coquille de fragments en expansion, sans traînée, est
     * un nuage de points qui grandit — on ne voit pas <i>d'où</i> il vient. Chaque fragment
     * porte donc derrière lui quatre cubes alignés sur le rayon, de plus en plus petits et
     * sombres. Les cent cinquante traînées pointent toutes vers le même point, et ce faisceau
     * de directions convergentes <b>est</b> l'image du big bang.</p>
     *
     * <p><b>La toile cosmique.</b> Une coquille parfaitement régulière est une boule de
     * discothèque. À partir de soixante pour cent, chaque fragment est attiré vers l'un des
     * quatorze nœuds tirés au départ : la coquille lisse se grumelle en filaments, exactement
     * comme la matière réelle l'a fait. Un pont de deux cubes relie chaque fragment à son
     * nœud, et la structure cesse d'être une surface pour devenir un réseau.</p>
     *
     * <p><b>Le refroidissement.</b> Les arêtes changent de mode avec le temps : blanc
     * monochrome dans la boule de feu, spectre complet pendant l'expansion, puis la braise
     * ambrée de la cicatrice. La couleur raconte la température.</p>
     */
    private static void renderBigBang(float cx, float cy, float cz, float t,
                                      float dx, float dz, float a, int salt, float ground) {
        final float YC = 1.20f;
        /** L'instant de la détonation. Seize pour cent, et pas quarante-deux. */
        final float BANG = 0.16f;
        final int DEBRIS = 170;
        final int TRAIL = 5;
        final int NODES = 14;
        final float GOLD = 2.3999632f;

        float baseYaw = (float) Mth.atan2(dz, dx);

        // =============== 1. LA SINGULARITÉ (0 → 16 %) ===============================
        if (t < BANG + 0.04f) {
            float pull = clamp(t / BANG);
            float fade = 1.0f - smooth(t, BANG, BANG + 0.04f);
            // Les fragments qui tombent. Trajectoire en u², donc l'accélération se voit.
            for (int j = 0; j < 46; j++) {
                float u = 1.0f - pull * pull * (0.55f + 0.45f * hash(salt + j, 1));
                float y0 = 1.0f - 2.0f * (j + 0.5f) / 46.0f;
                float rr = Mth.sqrt(Math.max(0.0f, 1.0f - y0 * y0));
                float phi = j * GOLD + pull * 5.0f;
                float rad = (0.5f + 2.6f * u);
                float f = cos(phi) * rr * rad;
                float la = sin(phi) * rr * rad;
                float up = YC + y0 * rad;
                put(cx, cy, cz, dx, dz, f, up, la, 0.040f + 0.030f * (1.0f - u),
                        baseYaw + phi, M_PULSE, G_MID, H_BLUE, a * fade);
                // Une courte comète derrière chaque fragment : on voit la direction.
                for (int k = 1; k <= 2; k++) {
                    float rk = rad + k * 0.16f;
                    put(cx, cy, cz, dx, dz, cos(phi) * rr * rk, YC + y0 * rk, sin(phi) * rr * rk,
                            0.026f - k * 0.006f, baseYaw + phi, M_TINTED, G_DEEP, H_BLUE,
                            a * fade * (0.55f - k * 0.16f));
                }
            }
            // LE POINT. Il grossit en luminance, pas en taille : une singularité n'est pas
            // un gros objet, c'est un petit objet insoutenable.
            float cs = 0.05f + 0.10f * pull;
            put(cx, cy, cz, dx, dz, 0.0f, YC, 0.0f, cs, baseYaw,
                    M_MONO, Math.round(Mth.lerp(pull, 14.0f, 31.0f)), 0.0f, a);
            if (pull > 0.55f) {
                // La cage qui se referme : huit arêtes de cube autour du point.
                float cr = 0.72f * (1.0f - pull) + 0.08f;
                for (int e = 0; e < 12; e++) {
                    float ang = e / 12.0f * Mth.TWO_PI + t * 9.0f;
                    put(cx, cy, cz, dx, dz, cos(ang) * cr, YC + sin(ang * 2.0f) * cr * 0.5f,
                            sin(ang) * cr, 0.030f, baseYaw + ang, M_DUAL, G_FRAME, H_ROSE,
                            a * fade);
                }
            }
        }

        // =============== 2. LA BOULE DE FEU (16 → 24 %) =============================
        float flash = smooth(t, BANG, BANG + 0.035f) * (1.0f - smooth(t, BANG + 0.05f, 0.24f));
        if (flash > 0.01f) {
            float fr = 0.18f + 1.5f * smooth(t, BANG, 0.24f);
            for (int j = 0; j < 90; j++) {
                float y0 = 1.0f - 2.0f * (j + 0.5f) / 90.0f;
                float rr = Mth.sqrt(Math.max(0.0f, 1.0f - y0 * y0));
                float phi = j * GOLD;
                put(cx, cy, cz, dx, dz, cos(phi) * rr * fr, YC + y0 * fr, sin(phi) * rr * fr,
                        0.075f * flash + 0.020f, baseYaw + phi, M_MONO, G_CORE, 0.0f,
                        a * flash);
            }
        }

        // =============== 3. LA COQUILLE EN EXPANSION (18 % → fin) ===================
        if (t > BANG) {
            // Racine carrée : l'expansion est violente puis ralentit. Un fondu linéaire
            // donnerait une bulle de savon qui monte à vitesse constante.
            float wave = Mth.sqrt(clamp((t - BANG) / (1.0f - BANG)));
            float R = 4.8f * wave;
            // La toile : à partir de 60 %, la coquille se grumelle.
            float web = smooth(t, 0.58f, 0.86f);
            float dim = 1.0f - smooth(t, 0.80f, 1.0f) * 0.75f;

            for (int j = 0; j < DEBRIS; j++) {
                float y0 = 1.0f - 2.0f * (j + 0.5f) / DEBRIS;
                float rr = Mth.sqrt(Math.max(0.0f, 1.0f - y0 * y0));
                float phi = j * GOLD;
                float ux = cos(phi) * rr;
                float uy = y0;
                float uz = sin(phi) * rr;

                // Le nœud de rattachement de ce fragment, et l'attraction vers lui.
                if (web > 0.001f) {
                    int n = j % NODES;
                    float ny0 = 1.0f - 2.0f * (n + 0.5f) / NODES;
                    float nrr = Mth.sqrt(Math.max(0.0f, 1.0f - ny0 * ny0));
                    float nphi = n * GOLD * 3.0f + 0.7f;
                    ux = Mth.lerp(web * 0.72f, ux, cos(nphi) * nrr);
                    uy = Mth.lerp(web * 0.72f, uy, ny0);
                    uz = Mth.lerp(web * 0.72f, uz, sin(nphi) * nrr);
                }
                // Épaisseur de coquille : sans elle, cent cinquante points sur une sphère
                // mathématique font une grille, pas des débris.
                float thick = 1.0f - 0.22f * hash(salt + j, 2);

                // Le refroidissement, fragment par fragment : blanc, puis spectre, puis
                // arc-en-ciel froid. Décalé par le hachage pour qu'aucune frontière nette
                // ne traverse la coquille.
                float heat = clamp(1.6f - wave * 1.9f - 0.3f * hash(salt + j, 3));
                // Bande SPECTRUM resserree : mesure au banc, avec 0,26 la quasi-totalite
                // de la coquille tombait dans la roue complete et l'element perdait son
                // identite froide. Le vert et le jaune restent un EVENEMENT, pas un fond.
                int mode = heat > 0.55f ? M_MONO : (heat > 0.38f ? M_SPECTRUM : M_RAINBOW);
                int gray = Math.round(Mth.lerp(heat, 9.0f + hash(salt + j, 4) * 13.0f, 31.0f));

                for (int k = 0; k <= TRAIL; k++) {
                    // LA TRAÎNÉE RADIALE. Elle est derrière, donc à un rayon plus PETIT :
                    // elle marque le chemin déjà parcouru, et toutes les traînées de la
                    // coquille pointent vers le même point d'origine.
                    //
                    // Premier réglage mesuré au banc : pas de 0,070, opacité 0,62 et corps
                    // au gris 6 — les traînées étaient là, et INVISIBLES. Or elles sont la
                    // seule chose qui distingue « big bang » de « nuage de points qui
                    // grandit ». Elles sont donc plus longues, plus opaques, et leur gris
                    // descend en RAMPE depuis celui de la tête au lieu de tomber d'un coup
                    // au presque-noir.
                    float rk = R * thick * (1.0f - k * 0.085f);
                    if (rk < 0.05f) break;
                    float sz = (0.052f + 0.030f * hash(salt + j, 5)) * (1.0f - k * 0.13f);
                    float alp = a * dim * (k == 0 ? 1.0f : 0.84f - k * 0.11f);
                    put(cx, cy, cz, dx, dz, ux * rk, YC + uy * rk, uz * rk, sz,
                            baseYaw + phi + k * 0.3f, k == 0 ? mode : M_TINTED,
                            k == 0 ? gray : Math.max(4, gray - k * 3), H_VIOLET, alp);
                }

                // Le pont vers le nœud : c'est lui qui fait le FILAMENT, donc le réseau.
                if (web > 0.25f && (j % 2) == 0) {
                    int n = j % NODES;
                    float ny0 = 1.0f - 2.0f * (n + 0.5f) / NODES;
                    float nrr = Mth.sqrt(Math.max(0.0f, 1.0f - ny0 * ny0));
                    float nphi = n * GOLD * 3.0f + 0.7f;
                    for (int b = 1; b <= 2; b++) {
                        float u = b / 3.0f;
                        float bx = Mth.lerp(u, ux, cos(nphi) * nrr);
                        float by = Mth.lerp(u, uy, ny0);
                        float bz = Mth.lerp(u, uz, sin(nphi) * nrr);
                        float rk = R * thick * 0.97f;
                        put(cx, cy, cz, dx, dz, bx * rk, YC + by * rk, bz * rk,
                                0.026f, baseYaw, M_TINTED, G_MASK, H_MAGENTA,
                                a * dim * web * 0.75f);
                    }
                }
            }

            // --- L'ONDE DE CHOC AU SOL. Un anneau presque noir qui court plus vite que la
            //     coquille. Il n'émet rien : son rôle est de MASQUER, donc de donner une
            //     échelle au reste.
            int ringN = 76;
            float ringR = R * 1.16f;
            for (int k = 0; k < ringN; k++) {
                float ang = k / (float) ringN * Mth.TWO_PI;
                float h = 0.30f * (1.0f - wave);
                put(cx, cy, cz, dx, dz, cos(ang) * ringR, YC - 0.30f, sin(ang) * ringR,
                        0.11f, 0.040f + h, 0.11f, baseYaw + ang,
                        M_DUAL, G_MASK, H_ROSE, a * dim * (1.0f - wave * 0.45f));
            }

            // --- LES PIXELS LIBRES. Un par image environ, lancés sur le rayon.
            if (((salt + (int) (t * 200.0f)) % 2) == 0) {
                int j = (salt * 7 + (int) (t * 200.0f) * 13) % DEBRIS;
                float y0 = 1.0f - 2.0f * (j + 0.5f) / DEBRIS;
                float rr = Mth.sqrt(Math.max(0.0f, 1.0f - y0 * y0));
                float phi = j * GOLD;
                float ux = cos(phi) * rr;
                float uz = sin(phi) * rr;
                pixel(
                        WeaponWorldVfxManager.camX() + cx + (ux * R) * dx - (uz * R) * dz,
                        WeaponWorldVfxManager.camY() + cy + YC + y0 * R,
                        WeaponWorldVfxManager.camZ() + cz + (ux * R) * dz + (uz * R) * dx,
                        ux * 0.075f, y0 * 0.075f, uz * 0.075f, 0.090f, 34,
                        M_SPECTRUM, 0.0f, 0.18f, (int) (hash(salt + j, 9) * 31.0f));
            }
        }

        // =============== 4. LA CICATRICE (80 % → fin) ===============================
        float scar = smooth(t, 0.78f, 0.88f);
        if (scar > 0.01f && ground > -9.0f) {
            for (int m = 0; m < 42; m++) {
                float ang = hash(salt + 331, m) * Mth.TWO_PI;
                float rad = 0.5f + 4.3f * hash(salt + 332, m);
                float flick = 0.45f + 0.55f * hash(salt + 333, m + (int) (t * 20.0f));
                put(cx, cy, cz, dx, dz, cos(ang) * rad, ground + 0.04f, sin(ang) * rad,
                        0.075f, 0.024f, 0.075f, baseYaw + ang, M_EMBER,
                        Math.round(Mth.lerp(flick, 4.0f, 18.0f)), H_EMBER, a * scar * flick);
            }
            if (((salt + (int) (t * 200.0f)) % 5) == 0) {
                int m = (int) (t * 200.0f) % 42;
                float ang = hash(salt + 331, m) * Mth.TWO_PI;
                float rad = 0.5f + 4.3f * hash(salt + 332, m);
                pixel(
                        WeaponWorldVfxManager.camX() + cx + (cos(ang) * rad) * dx
                                - (sin(ang) * rad) * dz,
                        WeaponWorldVfxManager.camY() + cy + ground + 0.10f,
                        WeaponWorldVfxManager.camZ() + cz + (cos(ang) * rad) * dz
                                + (sin(ang) * rad) * dx,
                        0.0, 0.026, 0.0, 0.065f, 30, M_EMBER, H_EMBER, -0.10f, G_DEEP);
            }
        }
    }

    // =================================================================================
    //  OUTILS
    // =================================================================================

    /**
     * Pose une boîte de structure, en convertissant le repère local de la signature
     * (f = attaquant→cible, up = hauteur, lat = latéral) vers le monde.
     *
     * <p>C'est le <b>seul</b> point de sortie géométrique du fichier : il n'existe aucun
     * appel à {@code emitVoxelCube}, et c'est la garantie mécanique que pas un corps ne
     * sortira coloré.</p>
     */
    private static void put(float cx, float cy, float cz, float dx, float dz,
                            float f, float up, float lat,
                            float hx, float hy, float hz, float spin,
                            int mode, int gray, float hue, float alpha) {
        float av = alpha * envAlpha;
        if (av <= 0.004f) return;   // meme seuil que le miroir JS, et meme effet : rien
        VoidPixelManager.emitStructureBox(
                WeaponWorldVfxManager.camX() + cx + f * dx - lat * dz,
                WeaponWorldVfxManager.camY() + cy + up,
                WeaponWorldVfxManager.camZ() + cz + f * dz + lat * dx,
                hx, hy, hz, spin, mode, gray, hue, av);
    }

    /** Variante cubique, de loin la plus fréquente. */
    private static void put(float cx, float cy, float cz, float dx, float dz,
                            float f, float up, float lat, float half, float spin,
                            int mode, int gray, float hue, float alpha) {
        put(cx, cy, cz, dx, dz, f, up, lat, half, half, half, spin, mode, gray, hue, alpha);
    }

    /**
     * Naissance d'un Pixel 3D libre, sous enveloppe.
     *
     * <p>Un pixel libre survit a la signature qui l'a emis : c'est voulu pour les debris
     * du big bang, qui doivent finir leur chute. Mais un pixel ne a 95 % de la
     * choregraphie avec quarante ticks de vie <b>reste seul en l'air une seconde apres que
     * tout le reste a disparu</b>, et c'est precisement la queue sale qu'on cherche a
     * supprimer. On raccourcit donc sa vie dans la meme proportion que l'enveloppe : les
     * derniers nes s'eteignent avec elle, et comme chaque pixel a deja son propre fondu
     * lisse, ils s'eteignent eux aussi en douceur.</p>
     */
    private static void pixel(double x, double y, double z,
                              double vx, double vy, double vz,
                              float size, int life, int mode, float hue,
                              float gravity, int gray) {
        if (envAlpha <= 0.02f) return;
        // S'IL NE RESTE PAS LA PLACE DE MOURIR, ON NE NAIT PAS. Douze ticks est le plancher
        // mesure : en dessous, le fondu propre du pixel (montee sur 8 % de sa vie, descente
        // sur les 30 derniers %) se joue en deux ou trois images et se lit comme un
        // clignotement -- exactement le defaut qu'on corrige, mais en pire.
        if (envRemain < 12) return;
        int trimmed = Math.min(life, envRemain);
        VoidPixelManager.spawnPixel(x, y, z, vx, vy, vz, size, trimmed, mode, hue,
                gravity, gray);
    }

    // =================================================================================
    //  IV. CASCADE DE BIFURCATION — 170 ticks
    // =================================================================================

    /**
     * T402 — LA FIGURE N'EST PAS DESSINÉE, ELLE EST <b>CALCULÉE</b>.
     *
     * <h3>Le concept</h3>
     *
     * <p>Les trois premières signatures illustrent le chaos ; celle-ci en est une. On
     * itère la <b>carte logistique</b> — {@code x = r·x·(1−x)}, une ligne d'arithmétique —
     * et on place un cube à chaque valeur que l'orbite visite. Pour {@code r} petit
     * l'orbite tient en <b>un</b> point ; passé 3, elle en occupe <b>deux</b> ; puis
     * quatre, huit, seize, et à 3,5699… elle n'en finit plus : c'est le
     * <b>diagramme de Feigenbaum</b>, et c'est la définition opératoire du chaos.</p>
     *
     * <p>Rien ici n'est une sinusoïde habillée. Les positions sortent de l'itération, y
     * compris les <b>fenêtres de périodicité</b> — ces bandes nettes qui réapparaissent
     * en plein brouillard, dont la plus large, la fenêtre de période 3 vers 3,83, est
     * visible à l'œil nu dans la figure. On ne pourrait pas les inventer à la main : il
     * faut faire le calcul pour les obtenir, et c'est précisément ce qui donne à cette
     * signature une texture qu'aucune autre du mod ne peut avoir.</p>
     *
     * <h3>La mise en espace</h3>
     *
     * <p>{@code r} court le long de l'axe attaquant → cible : le diagramme <b>fonce sur
     * la victime</b>, la tige unique derrière elle, le brouillard devant. {@code x} est
     * la hauteur. Et comme un diagramme plat disparaît vu par la tranche — défaut déjà
     * payé sur la fenêtre de la Porte —, la nappe est <b>épaissie latéralement</b> : la
     * dispersion transverse croît avec le désordre, si bien que la tige est un fil et le
     * chaos un volume.</p>
     *
     * <h3>Le rythme</h3>
     *
     * <p>Six vagues, aux ticks 24, 48, 70, 88, 102, 112 : les intervalles se resserrent
     * (24, 22, 18, 14, 10) comme la cascade elle-même converge. La vague {@code k} porte
     * {@code 2^k} coups — un, puis deux, quatre, huit, seize, trente-deux. Soixante-trois
     * impacts de plus en plus nombreux, de plus en plus petits, de plus en plus serrés :
     * l'exact contraire du Grain de Trop qui la suit.</p>
     */
    private static final float FEIGEN_R0 = 2.70f;
    private static final float FEIGEN_R1 = 3.99f;

    /**
     * r(t) — LES PALIERS SONT CALES SUR LES SIX VAGUES DE COUPS, et c'est le coeur de la
     * signature : a la vague k, l'orbite a exactement 2^k valeurs, donc <b>2^k anneaux</b>,
     * et la vague compte <b>2^k impacts</b>. Le nombre qu'on voit et le nombre qu'on
     * encaisse sont le meme nombre.
     *
     * <p>Une rampe reguliere ne donne pas ca : les fenetres de periode 4, 8 et 16 sont
     * minuscules (3,449→3,544 puis 3,544→3,564 puis 3,564→3,569) et defileraient en un
     * quart de seconde. Les valeurs ci-dessous posent r AU MILIEU de chaque fenetre a
     * l'instant exact de la vague correspondante, et dans la fenetre de periode trois
     * (3,8284→3,8415) pendant les ticks 132→148.</p>
     */
    private static final float[] FEIGEN_T  = { 0f, 24f, 48f, 70f, 88f, 102f, 112f, 132f, 148f, 170f };
    private static final float[] FEIGEN_RV = { 2.700f, 2.920f, 3.200f, 3.490f, 3.5550f,
                                               3.5660f, 3.600f, 3.8350f, 3.8420f, 3.990f };

    private static float feigenR(float tick) {
        if (tick <= FEIGEN_T[0]) return FEIGEN_RV[0];
        for (int i = 1; i < FEIGEN_T.length; i++) {
            if (tick <= FEIGEN_T[i]) {
                float u = (tick - FEIGEN_T[i - 1]) / (FEIGEN_T[i] - FEIGEN_T[i - 1]);
                return FEIGEN_RV[i - 1] + (FEIGEN_RV[i] - FEIGEN_RV[i - 1]) * u;
            }
        }
        return FEIGEN_RV[FEIGEN_RV.length - 1];
    }

    /** Les six temps : ils se resserrent, comme les doublements de période. */
    private static final int[] BEATS_FEIGEN = { 24, 48, 70, 88, 102, 112 };

    /** Combien de pas d'orbite on trace. Un tampon fixe : la boucle de rendu tourne
     *  soixante fois par seconde et n'a aucune raison d'allouer à chaque trame. */
    private static final int FEIGEN_SAMPLES = 96;
    private static final float[] FEIGEN_ORBIT = new float[FEIGEN_SAMPLES];
    /** Tampons de tri : l'orbite range ses valeurs pour qu'on puisse COMPTER ses branches. */
    private static final float[] FEIGEN_SORT = new float[FEIGEN_SAMPLES];
    private static final float[] FEIGEN_CLUSTER = new float[FEIGEN_SAMPLES];
    private static final int[] FEIGEN_BRANCH = new int[FEIGEN_SAMPLES];

    /**
     * T402b — {@code r} N'EST PLUS UNE DIRECTION DE L'ESPACE, C'EST LE TEMPS.
     *
     * <p>La première version traçait le diagramme de Feigenbaum tel qu'on le publie :
     * {@code r} en abscisse sur un axe du monde, {@code x} en hauteur. C'est une figure
     * exacte et c'est un <b>graphique</b> — mis en volume, épaissi latéralement pour ne
     * pas disparaître par la tranche, il devient une nappe informe qui ne ressemble à
     * rien. Un diagramme scientifique ne devient pas une attaque parce qu'on le dessine
     * en cubes.</p>
     *
     * <p>Ici {@code r} monte <b>avec le temps</b>, et l'orbite est calculée à <b>un
     * seul</b> {@code r} à la fois. Chaque valeur que prend l'attracteur devient le
     * <b>rayon d'un anneau autour de la victime</b>. La conséquence est directe et c'est
     * tout l'intérêt :</p>
     *
     * <ul>
     *   <li>{@code r} &lt; 3,00 : l'orbite a <b>une</b> valeur — <b>un anneau</b>.</li>
     *   <li>3,00 → 3,449 : <b>deux</b> valeurs — l'anneau <b>se dédouble sous les
     *       yeux</b>.</li>
     *   <li>3,449 → 3,544 : <b>quatre</b> anneaux. Puis huit, puis seize.</li>
     *   <li>au-delà de 3,57 : le brouillard — une <b>couronne pleine</b>.</li>
     *   <li>vers 3,83, la <b>fenêtre de période trois</b> : <b>trois anneaux nets
     *       réapparaissent en plein chaos</b>, puis se redissolvent. Personne ne l'a
     *       écrit : c'est l'itération qui le fait.</li>
     * </ul>
     *
     * <p>Le rayon porte aussi la <b>teinte</b> ({@code 0,46 + 0,46·x}) : deux anneaux de
     * rayons voisins ne sont donc jamais de la même couleur, et l'œil compte les anneaux
     * sans avoir à les mesurer. Sans ce codage, quatre anneaux proches se lisent comme
     * une bande large.</p>
     */
    private static void renderFeigenbaum(float cx, float cy, float cz, float t,
                                         float dx, float dz, float a, int salt, float ground) {
        final float aim = 1.10f;

        // r MONTE AVEC LE TEMPS, par paliers cales sur les vagues de coups.
        float r = feigenR(t * 170.0f);

        // L'ITERATION, pour de bon. 220 pas brûlés pour que l'orbite se pose exactement
        // sur son attracteur : sans le transitoire, les anneaux bavent.
        float x = 0.42f + 0.11f * hash(salt, 7);
        for (int i = 0; i < 220; i++) x = r * x * (1.0f - x);
        for (int i = 0; i < FEIGEN_SAMPLES; i++) {
            x = r * x * (1.0f - x);
            FEIGEN_ORBIT[i] = Mth.clamp(x, 0.015f, 0.985f);
        }

        // LE MICROSCOPE. Les fenêtres de la cascade sont minuscules : la période 4 tient
        // entre 3,449 et 3,544, la période 8 entre 3,544 et 3,564, la 16 entre 3,564 et
        // 3,569. Un rayon proportionnel à x les écraserait toutes sur la même épaisseur
        // de cube — on verrait un anneau, puis deux, puis plus jamais rien de nouveau.
        // Alors le rayon ne mesure pas x : il mesure la POSITION DE x DANS L'ATTRACTEUR
        // DU MOMENT. L'instrument suit sa cible en zoomant, exactement comme on présente
        // l'autosimilarité du diagramme — et chaque dédoublement s'ouvre à pleine largeur.
        float lo = 1.0f, hi = 0.0f;
        for (int i = 0; i < FEIGEN_SAMPLES; i++) {
            float v = FEIGEN_ORBIT[i];
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }
        float mid = (lo + hi) * 0.5f;
        float span = hi - lo;

        // COMBIEN DE BRANCHES ? On trie l'orbite et on regroupe les valeurs confondues :
        // une période 4 ne donne que quatre valeurs distinctes, répétées vingt-quatre fois
        // chacune. Le compte qui sort d'ici n'est pas décrété, il est MESURÉ sur l'orbite.
        System.arraycopy(FEIGEN_ORBIT, 0, FEIGEN_SORT, 0, FEIGEN_SAMPLES);
        for (int i = 1; i < FEIGEN_SAMPLES; i++) {
            float v = FEIGEN_SORT[i];
            int j = i - 1;
            while (j >= 0 && FEIGEN_SORT[j] > v) { FEIGEN_SORT[j + 1] = FEIGEN_SORT[j]; j--; }
            FEIGEN_SORT[j + 1] = v;
        }
        final float MERGE = 2.0e-5f;   // deux valeurs plus proches que ça sont LA MÊME
        int branches = 0;
        for (int i = 0; i < FEIGEN_SAMPLES; i++) {
            if (branches == 0 || FEIGEN_SORT[i] - FEIGEN_CLUSTER[branches - 1] > MERGE) {
                FEIGEN_CLUSTER[branches++] = FEIGEN_SORT[i];
            }
        }
        for (int i = 0; i < FEIGEN_SAMPLES; i++) {
            int b = branches - 1;
            for (int c = 0; c < branches; c++) {
                if (FEIGEN_ORBIT[i] <= FEIGEN_CLUSTER[c] + MERGE) { b = c; break; }
            }
            FEIGEN_BRANCH[i] = b;
        }

        // LE RÉGULATEUR. Au début, le rayon porte la VRAIE position de la valeur dans
        // l'attracteur : les deux premiers anneaux naissent l'un de l'autre, comme la
        // bifurcation le fait vraiment. Plus haut, les branches se serrent en paquets
        // autosimilaires et huit anneaux superposés ne se comptent plus ; le rayon glisse
        // alors vers le RANG de la branche, régulièrement espacé. L'ordre et le nombre
        // restent exacts — seul l'écartement est redressé, pour qu'il reste comptable.
        float regul = Mth.clamp((r - 3.40f) / 0.16f, 0.0f, 1.0f);
        // `open` fait ECLORE le dédoublement au lieu de le faire claquer : à r = 3,00 la
        // largeur du 2-cycle vaut zéro et grandit en racine carrée, donc les deux anneaux
        // s'écartent progressivement entre les ticks 31 et 48.
        float open = Mth.clamp(span / 0.28f, 0.0f, 1.0f);
        final float R_MID = 1.55f;
        final float R_HALF = 1.05f;

        // LE REPLI final : les anneaux se referment sur la victime au lieu de s'éteindre.
        float close = smooth(t, 0.82f, 0.95f);
        float live = 1.0f - 0.80f * close;
        float spin = t * 1.15f;
        // L'assemblage bascule lentement : un anneau strictement horizontal disparaît vu
        // par la tranche, et en jeu la caméra est à hauteur d'œil.
        float tilt = 0.20f + 0.13f * sin(t * TAU * 0.5f);

        // UN ANNEAU PAR BRANCHE. Le nombre d'anneaux n'est pas un paramètre et ne peut
        // pas l'être : c'est le nombre de valeurs distinctes que l'orbite vient de
        // prendre. Chaque anneau reçoit un nombre de cubes proportionnel à son rayon,
        // sinon le plus petit serait un amas et le plus grand un pointillé.
        if (branches <= 20) {
            for (int b = 0; b < branches; b++) {
                float metric = span > 1.0e-5f ? (FEIGEN_CLUSTER[b] - mid) / span * 2.0f : 0.0f;
                float rank = branches > 1 ? b / (float) (branches - 1) * 2.0f - 1.0f : 0.0f;
                float norm = metric + (rank - metric) * regul;
                float rad = (R_MID + norm * R_HALF * open) * live;
                float hue = 0.46f + 0.23f * (norm + 1.0f);   // le rayon FAIT la couleur
                // Budget : deux cent soixante cubes pour la couronne entière. Au-delà
                // de huit branches, les anneaux se font pointillés — ce qui est exact :
                // on approche du point d'accumulation, et il n'y a plus de place.
                int n = Mth.clamp(Math.round(Math.min(rad * 23.0f, 260.0f / branches)), 7, 44);
                for (int j = 0; j < n; j++) {
                    // Chaque anneau est décalé : sans ça, les cubes s'alignent en rayons
                    // et la figure se lit comme une roue au lieu d'une série d'anneaux.
                    float th = j / (float) n * TAU + spin + b * 0.37f;
                    float px = cos(th) * rad;
                    float pz = sin(th) * rad;
                    float py = aim + pz * tilt + 0.055f * sin(th * 3.0f + b);
                    put(cx, cy, cz, dx, dz, px, py, pz,
                            0.098f, 0.132f, 0.098f, th,
                            (j % 6 == 0) ? M_DUAL : M_TINTED,
                            (j % 6 == 0) ? G_FRAME : G_BODY, hue, a * 0.92f);
                    // L'OMBRE AU SOL fait d'un empilement de cubes un jeu d'anneaux
                    // CONCENTRIQUES : sans elle, on ne voit pas qu'ils ont un centre.
                    if (j % 3 == 0) {
                        put(cx, cy, cz, dx, dz, px, ground + 0.07f, pz,
                                0.072f, 0.028f, 0.072f, th,
                                M_MONO, G_MASK, 0.0f, a * 0.40f);
                    }
                }
            }
        } else {
            // LE BROUILLARD. Au-delà de vingt branches il n'y a plus rien à compter :
            // l'orbite ne se pose plus. On montre alors les quatre-vingt-seize valeurs
            // telles quelles, et la couronne se remplit.
            for (int i = 0; i < FEIGEN_SAMPLES; i++) {
                float metric = span > 1.0e-5f ? (FEIGEN_ORBIT[i] - mid) / span * 2.0f : 0.0f;
                float rank = FEIGEN_BRANCH[i] / (float) (branches - 1) * 2.0f - 1.0f;
                float norm = metric + (rank - metric) * regul;
                float rad = (R_MID + norm * R_HALF * open) * live;
                float th = i / (float) FEIGEN_SAMPLES * TAU * 3.0f + spin;
                float px = cos(th) * rad;
                float pz = sin(th) * rad;
                float py = aim + pz * tilt + 0.085f * sin(th * 3.0f + i * 0.7f);
                put(cx, cy, cz, dx, dz, px, py, pz,
                        0.098f, 0.132f, 0.098f, th,
                        (i % 8 == 0) ? M_DUAL : M_TINTED,
                        (i % 8 == 0) ? G_FRAME : G_BODY,
                        0.46f + 0.23f * (norm + 1.0f), a * 0.92f);
                if (i % 3 == 0) {
                    put(cx, cy, cz, dx, dz, px, ground + 0.07f, pz,
                            0.072f, 0.028f, 0.072f, th,
                            M_MONO, G_MASK, 0.0f, a * 0.40f);
                }
            }
        }

        // LES MONTANTS. Douze barreaux verticaux qui relient le sol à l'anneau le plus
        // large : ils donnent une hauteur à la figure et empêchent la couronne de flotter
        // sans rapport avec le terrain.
        float outer = (R_MID + R_HALF * open) * live;
        for (int p = 0; p < 12; p++) {
            float th = p / 12.0f * TAU + spin;
            float px = cos(th) * outer;
            float pz = sin(th) * outer;
            float top = aim + pz * tilt;
            for (int j = 0; j < 5; j++) {
                float u = j / 4.0f;
                float py = ground + 0.10f + (top - ground - 0.10f) * u;
                put(cx, cy, cz, dx, dz, px, py, pz,
                        0.050f, 0.050f, 0.050f, th,
                        M_MONO, G_DEEP, 0.0f, a * 0.30f * (0.35f + 0.65f * u));
            }
        }

        // LE CŒUR. Un noyau à l'aplomb de la victime, qui dit où est le centre de tout
        // ça — et qui bat de plus en plus vite à mesure que r approche du chaos.
        float heat = Mth.clamp((r - 3.40f) / (FEIGEN_R1 - 3.40f), 0.0f, 1.0f);
        float beat = 0.5f + 0.5f * sin(t * TAU * (3.0f + heat * 14.0f));
        put(cx, cy, cz, dx, dz, 0.0f, aim, 0.0f,
                (0.13f + 0.07f * beat) * (1.0f - 0.5f * close), t * 6.0f,
                heat > 0.5f ? M_STROBE : M_PULSE, G_CORE, 0.0f,
                a * (0.55f + 0.35f * beat));
    }

    // =================================================================================
    //  V. LE GRAIN DE TROP — 200 ticks
    // =================================================================================

    /**
     * T402 — UNE FIGURE QUI N'EST PAS ÉCRITE : ELLE <b>ARRIVE</b>.
     *
     * <h3>Le concept</h3>
     *
     * <p>Le tas de sable de Bak, Tang et Wiesenfeld. Une grille ; on y dépose des grains
     * un par un ; dès qu'une case en contient quatre, elle <b>se déverse</b> sur ses
     * quatre voisines, ce qui peut les faire déborder à leur tour. Le système s'organise
     * tout seul vers un état <b>critique</b> où un seul grain de plus provoque parfois
     * une avalanche qui traverse toute la grille. C'est la criticalité auto-organisée :
     * un chaos qui n'a ni équation ni trajectoire, seulement une règle locale et une
     * catastrophe différée.</p>
     *
     * <p>L'automate tourne pour de bon. Personne n'a placé ces cubes : leur hauteur est
     * le contenu d'une case après N dépôts, et les motifs en losange qui apparaissent
     * sont ceux, bien connus, du tas de sable abélien. On ne les a ni dessinés ni
     * approchés.</p>
     *
     * <h3>Pourquoi c'est l'exact contraire de la cascade</h3>
     *
     * <table>
     *   <tr><td></td><td><b>Cascade</b></td><td><b>Grain de trop</b></td></tr>
     *   <tr><td>cadence</td><td>63 coups qui accélèrent</td><td>le silence, puis UN coup</td></tr>
     *   <tr><td>forme</td><td>une nappe horizontale qui se divise</td><td>une pile verticale qui s'écroule</td></tr>
     *   <tr><td>couleur</td><td>le spectre entier</td><td>des gris qui virent à la braise</td></tr>
     *   <tr><td>nature</td><td>une équation exacte</td><td>un automate émergent</td></tr>
     * </table>
     *
     * <h3>Le rythme</h3>
     *
     * <p>Cent-cinquante ticks d'empilement où il ne se passe <b>presque</b> rien — trois
     * micro-glissements seulement, aux ticks 52, 92 et 126, qui disent que ça tient
     * encore. Puis le tick 150 : l'avalanche. Un seul coup, et il vaut tous les autres.
     * Le silence qui précède n'est pas du temps perdu, c'est ce qui donne sa valeur au
     * dernier grain — une animation qui frappe sans arrêt ne peut pas, par construction,
     * produire un instant qui compte.</p>
     */
    private static final int PILE_N = 19;        // grille 19×19, centrée sur la victime
    private static final int PILE_GRAINS = 900;  // dépôts au moment de la rupture

    /** Les trois micro-glissements, puis l'avalanche. */
    private static final int[] BEATS_SANDPILE = { 52, 92, 126, 150 };

    /** Dernier état calculé, mémorisé par nombre de grains : l'automate est déterministe. */
    private static int[] pileCache = null;
    private static int pileCacheGrains = -1;

    /**
     * Fait tourner l'automate pour {@code grains} dépôts et renvoie la grille.
     *
     * <p>Les dépôts tombent au centre, comme dans le modèle d'origine : c'est la seule
     * façon d'obtenir les losanges concentriques. Le résultat ne dépend que du nombre de
     * grains, donc une seule simulation par tick suffit — d'où le cache, sans lequel on
     * relancerait l'automate soixante fois par seconde pour un résultat identique.</p>
     */
    private static int[] sandpile(int grains) {
        if (pileCache != null && pileCacheGrains == grains) return pileCache;
        int[] g = new int[PILE_N * PILE_N];
        int mid = PILE_N / 2;
        for (int n = 0; n < grains; n++) {
            g[mid * PILE_N + mid]++;
            // L'EFFONDREMENT EN CHAINE. Une case a quatre grains se vide sur ses quatre
            // voisines ; ce qui sort du bord est perdu, exactement comme dans le modele.
            boolean moved = true;
            int guard = 0;
            while (moved && guard++ < 4000) {
                moved = false;
                for (int y = 0; y < PILE_N; y++) {
                    for (int x = 0; x < PILE_N; x++) {
                        int i = y * PILE_N + x;
                        if (g[i] < 4) continue;
                        g[i] -= 4;
                        moved = true;
                        if (x > 0) g[i - 1]++;
                        if (x < PILE_N - 1) g[i + 1]++;
                        if (y > 0) g[i - PILE_N]++;
                        if (y < PILE_N - 1) g[i + PILE_N]++;
                    }
                }
            }
        }
        pileCache = g;
        pileCacheGrains = grains;
        return g;
    }

    private static void renderSandpile(float cx, float cy, float cz, float t,
                                       float dx, float dz, float a, int salt, float ground) {
        float tick = t * 200.0f;
        // LA MONTEE occupe les trois quarts du temps et ne fait rien d'autre que monter.
        float build = Mth.clamp(tick / 150.0f, 0.0f, 1.0f);
        boolean fallen = tick >= 150.0f;
        float after = fallen ? Mth.clamp((tick - 150.0f) / 50.0f, 0.0f, 1.0f) : 0.0f;

        int grains = Math.max(1, Math.round(PILE_GRAINS * build * build));
        int[] g = sandpile(grains);

        // LA PLAQUE, ET NON LE CONE. L'automate sature a TROIS : une case stable n'en
        // contient jamais quatre. Le tas de Bak-Tang-Wiesenfeld n'est donc pas un tas au
        // sens courant mais un PLATEAU FRACTAL, et toute sa beaute se lit de dessus. La
        // grille est large (19x19) et la maille serree (0,30) pour que le motif ait la
        // place d'apparaitre ; la hauteur, elle, tient en trois crans.
        final float cell = 0.30f;
        final float mid = (PILE_N - 1) * 0.5f;

        for (int y = 0; y < PILE_N; y++) {
            for (int x = 0; x < PILE_N; x++) {
                int h = g[y * PILE_N + x];
                if (h <= 0) continue;
                float fx = (x - mid) * cell;
                float lz = (y - mid) * cell;
                float dist = Mth.sqrt(fx * fx + lz * lz);

                for (int k = 0; k < h; k++) {
                    // L'AVALANCHE : les colonnes partent vers l'exterieur en tombant, et
                    // les plus hautes partent les premieres — c'est ce qui rend
                    // l'effondrement lisible comme un ECROULEMENT et non comme un fondu.
                    // LE DEPART EST IMMEDIAT, et c'est tout le sujet. Une loi en
                    // after*after donnerait une avalanche qui demarre en douceur --
                    // autrement dit pas une avalanche. La racine carree part d'un coup
                    // puis ralentit : c'est ce que fait un tas qui lache.
                    // LE FRONT RAYONNE DEPUIS LE POINT DE CHUTE. Tout s'effondrer
                    // d'un bloc ne montrerait pas d'ou ca part ; ici une case ne lache
                    // que lorsque le front l'atteint, et le front sort du centre — la
                    // ou le grain est tombe. C'est le titre rendu visible.
                    float front = after * 7.5f;
                    float local = fallen
                            ? Mth.clamp((front - dist) / 1.30f, 0.0f, 1.0f) : 0.0f;
                    float blow = fallen
                            ? Mth.sqrt(local) * (0.60f + 0.40f * local) * (1.0f + k * 0.22f)
                            : 0.0f;
                    // LE REBOND. Les cubes sont jetes en l'air puis retombent ; les plus
                    // hauts de la pile sont jetes le plus loin et le plus haut.
                    float hop = fallen
                            ? sin(Math.min(1.0f, blow) * TAU * 0.5f) * (0.9f + k * 0.40f)
                            : 0.0f;
                    float dirx = dist > 0.001f ? fx / dist : 0.3f;
                    float dirz = dist > 0.001f ? lz / dist : 0.3f;
                    float px = fx + dirx * blow * 4.2f;
                    float pz = lz + dirz * blow * 4.2f;
                    float py = ground + 0.10f + k * 0.19f + hop;
                    if (py < ground + 0.08f) py = ground + 0.08f;

                    // Une case qui n'a pas encore lache garde sa couleur de repos :
                    // sans cela toute la plaque virerait a la braise d'un seul coup et
                    // le front serait invisible.
                    boolean gone = fallen && local > 0.02f;

                    // LA TENSION. Plus le tas est proche de la rupture, plus les sommets
                    // tremblent : c'est le seul indice, pendant sept secondes, que quelque
                    // chose va arriver.
                    float stress = build * build * (k >= 2 ? 1.0f : 0.25f);
                    float shake = gone ? 0.0f
                            : stress * 0.045f * sin(t * 62.0f + x * 2.3f + y * 1.7f + k);

                    float fade = gone ? (1.0f - local * 0.92f) : (0.55f + 0.45f * build);
                    // Les cubes du dessus sont les plus instables : ils sont aussi les plus
                    // clairs, pour que le regard aille ou ca va casser.
                    // LA HAUTEUR EST PORTEE PAR LA COULEUR, et c'est indispensable :
                    // vue de dessus, une plaque de trois crans n'a pas de relief, donc
                    // sans code couleur le motif fractal ne se lit pas du tout.
                    int gray = (h == 3) ? G_MID : (h == 2 ? G_BODY : G_DEEP);
                    int mode = gone ? M_EMBER : ((h == 3) ? M_PULSE : M_TINTED);
                    float hue = gone ? H_EMBER : ((h == 3) ? H_ROSE : H_VIOLET);
                    put(cx, cy, cz, dx, dz, px + shake, py, pz + shake,
                            0.125f, 0.095f, 0.125f, 0.0f,
                            mode, gray, hue, a * fade * 0.95f);
                }
            }
        }

        // LE GRAIN QUI VA TOMBER. Un seul cube, haut au-dessus du tas, qui descend pendant
        // toute la montee. On sait donc ce qu'on attend, et on voit combien il reste.
        // LES EBOULEMENTS DE DEMONSTRATION. Pendant la montee, six cases lachent sous
        // les yeux : la case source blanchit et QUATRE grains partent vers ses quatre
        // voisines. C'est la regle entiere de l'automate, montree au lieu d'etre ecrite —
        // et c'est ce qui manquait pour que la plaque soit autre chose qu'un damier.
        if (!fallen) {
            for (int ev = 0; ev < 6; ev++) {
                float begin = 16.0f + ev * 21.0f;
                float age = tick - begin;
                if (age < 0.0f || age > 13.0f) continue;
                float u = age / 13.0f;
                float pop = sin(u * TAU * 0.5f);          // nait et meurt en douceur
                int sx = 3 + (int) (hash(ev * 13 + 1, salt) * (PILE_N - 6));
                int sy = 3 + (int) (hash(ev * 13 + 2, salt) * (PILE_N - 6));
                float bx = (sx - mid) * cell;
                float bz = (sy - mid) * cell;
                float by = ground + 0.10f + 2.0f * 0.19f;

                // LA CASE QUI LACHE.
                put(cx, cy, cz, dx, dz, bx, by, bz,
                        0.145f, 0.110f, 0.145f, 0.0f,
                        M_STROBE, G_CORE, 0.0f, a * pop * 0.95f);

                // SES QUATRE GRAINS, un par voisine, exactement comme le modele.
                for (int d = 0; d < 4; d++) {
                    float ox = (d == 0) ? 1.0f : (d == 1 ? -1.0f : 0.0f);
                    float oz = (d == 2) ? 1.0f : (d == 3 ? -1.0f : 0.0f);
                    float slide = u * cell;
                    put(cx, cy, cz, dx, dz, bx + ox * slide,
                            by + 0.16f * pop, bz + oz * slide,
                            0.062f, 0.062f, 0.062f, u * 6.0f,
                            M_PULSE, G_FLASH, H_ROSE, a * pop * 0.90f);
                }
            }
        }

        if (!fallen) {
            float drop = 4.6f - 4.1f * build;
            float gy = ground + 1.0f + drop;

            // LE GRAIN. Il est GROS et il est SEUL : c'est le titre de la signature, et
            // au premier jet on ne le voyait pas. Trois cent soixante-dix mille cubes de
            // plaque autour, et l'objet qui donne son nom a l'animation faisait quatorze
            // centiemes de bloc.
            float gh = 0.20f + 0.09f * build;
            put(cx, cy, cz, dx, dz, 0.0f, gy, 0.0f, gh, t * 14.0f,
                    M_STROBE, G_CORE, 0.0f, a * (0.55f + 0.45f * build));
            // Sa coque, qui bat : un cube seul a cette taille se lit comme un cube de la
            // plaque qui aurait decolle. La coque dit que celui-la est d'une autre nature.
            put(cx, cy, cz, dx, dz, 0.0f, gy, 0.0f, gh * 1.95f, -t * 9.0f,
                    M_PULSE, G_MASK, H_ROSE, a * (0.20f + 0.26f * build));

            // LE FIL A PLOMB. Sans lui, le grain flotte quelque part en l'air et rien ne
            // dit OU il va tomber — donc rien n'annonce ce qui va se passer.
            int marks = 10;
            float topY = ground + 0.70f;
            for (int i = 1; i < marks; i++) {
                float u = i / (float) marks;
                float yy = topY + (gy - topY) * u;
                put(cx, cy, cz, dx, dz, 0.0f, yy, 0.0f,
                        0.038f, 0.038f, 0.038f, 0.0f,
                        M_MONO, G_MASK, 0.0f, a * 0.34f * (1.0f - u * 0.55f));
            }

            // LA CIBLE AU SOL, qui se resserre a mesure que le grain descend : on voit
            // combien de temps il reste.
            float ring = 1.05f - 0.62f * build;
            int n = 14;
            for (int i = 0; i < n; i++) {
                float ang = i / (float) n * TAU + t * 1.4f;
                put(cx, cy, cz, dx, dz, cos(ang) * ring, ground + 0.78f, sin(ang) * ring,
                        0.052f, 0.052f, 0.052f, ang,
                        M_PULSE, G_GLASS, H_ROSE, a * (0.30f + 0.45f * build));
            }
        }

        // L'ONDE DE DECHARGE : le front de l'avalanche, au sol, qui court vers l'exterieur.
        if (fallen && after < 0.85f) {
            float wave = 0.5f + after * 7.0f;
            float f = (1.0f - after / 0.85f);
            for (int i = 0; i < 40; i++) {
                float ang = i / 40.0f * TAU;
                put(cx, cy, cz, dx, dz, cos(ang) * wave, ground + 0.10f, sin(ang) * wave,
                        0.155f * f + 0.03f, 0.055f, 0.155f * f + 0.03f, ang,
                        (i % 4 == 0) ? M_EMBER : M_MONO,
                        (i % 4 == 0) ? G_FLASH : G_MID, H_EMBER, a * f * 0.92f);
            }
        }
    }

    /**
     * Hachage déterministe à deux entiers, miroir exact de celui du JS d'aperçu.
     * {@code Math.imul} côté JS impose de rester en arithmétique 32 bits ici aussi.
     */
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFF) / 8388607.0f;
    }

    /** Rampe lissée entre deux bornes. Trois arguments, et c'est volontaire. */
    private static float smooth(float x, float from, float to) {
        if (to <= from) return x >= to ? 1.0f : 0.0f;
        float u = Mth.clamp((x - from) / (to - from), 0.0f, 1.0f);
        return u * u * (3.0f - 2.0f * u);
    }

    private static float clamp(float v) { return Mth.clamp(v, 0.0f, 1.0f); }

    private static float sin(float v) { return Mth.sin(v); }

    private static float cos(float v) { return Mth.cos(v); }
}
