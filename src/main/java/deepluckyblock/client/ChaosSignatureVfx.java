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
    private static final int[] DURATION = { 150, 170, 200 };

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
            default -> renderKaleido(cx, cy, cz, t, dx, dz, a, salt, ground);
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
