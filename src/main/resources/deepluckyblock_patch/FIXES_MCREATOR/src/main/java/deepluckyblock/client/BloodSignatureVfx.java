package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T379-BLOOD-REFONTE — élément <b>SANG</b> : cinq signatures voxel sur un moteur commun.
 *
 * <p><b>Pourquoi cette classe ?</b> Le sang était la dernière famille du mod encore dessinée en
 * ligne dans {@code WeaponWorldVfxManager#impactBlood} — quatre blocs de six lignes : une barre
 * qui descend, deux vrilles d'hélice, une taillade. Durées : <b>14 à 16 ticks</b>, soit moins
 * d'une seconde, contre 80 à 250 pour les dix familles déjà refondues. Et c'est la seule famille
 * à compter <b>cinq</b> titres, là où les autres en ont trois : le sang avait donc le plus de
 * retard et le plus de surface à couvrir.</p>
 *
 * <h2>Le problème propre à cet élément</h2>
 *
 * <p>Les familles précédentes dessinent de l'<i>énergie</i> : une flamme, un arc, un éclat. Elles
 * ont le droit d'être lumineuses, symétriques et apesanteurs. Le sang, non — c'est un
 * <b>liquide</b>, et un liquide qui ne tombe pas n'est pas du liquide, c'est de la peinture
 * rouge. Le piège de cet élément est donc inverse de celui de l'ombre : non pas « comment rendre
 * visible », mais « comment rendre PESANT ».</p>
 *
 * <p>Quatre règles en découlent, et tout ce fichier les applique :</p>
 * <ol>
 *   <li><b>Rien ne flotte.</b> Toute masse détachée tombe en {@code y₀ − g·τ²}, jamais en
 *       descente linéaire : l'œil lit l'accélération, pas la vitesse. Une goutte à vitesse
 *       constante se lit comme un ascenseur.</li>
 *   <li><b>Tout liquide a un MÉNISQUE.</b> Chaque masse reçoit un liseré clair sur sa face
 *       haute ou son bord d'attaque, et s'assombrit vers le bas. C'est la contrepartie de la
 *       règle du liseré de l'ombre : là-bas le liseré disait « ceci est un trou », ici il dit
 *       « ceci a une surface ».</li>
 *   <li><b>Le rouge seul ne fait pas une image.</b> Six rouges du presque-noir au rose pâle, et
 *       surtout des matières ÉTRANGÈRES pour s'y opposer : l'acier et l'os du couperet, sans
 *       quoi cinq signatures rouges sur un mob rouge de dégâts forment une bouillie.</li>
 *   <li><b>On efface par la GÉOMÉTRIE.</b> Règle héritée : le tamis {@code pixelDensity} décime
 *       les cubes sous 200 d'alpha, donc un fondu d'alpha ne fait pas disparaître, il troue.
 *       Tout passe par {@link #opaque(float)} ; une flaque s'efface en rétrécissant.</li>
 * </ol>
 *
 * <h2>Les cinq signatures</h2>
 *
 * <p>Chaque titre a son <b>sujet</b>, et deux signatures ne partagent aucune primitive :</p>
 * <ul>
 *   <li><b>Couperet</b> (150 t) — une MACHINE. La seule géométrie à angles droits du mod :
 *       montants, linteau, rainures, chaîne, mouton, lame biseautée. Son sujet est
 *       l'<i>attente</i> : douze ticks de suspension immobile avant huit ticks de chute.</li>
 *   <li><b>Fil qui goutte</b> (120 t) — une PLAIE. Intime, à l'échelle du corps : une entaille
 *       qui s'ouvre, sept rigoles qui descendent à des vitesses différentes, des gouttes qui
 *       se détachent et éclaboussent, une flaque qui s'étale et se ride.</li>
 *   <li><b>Siphon</b> (170 t) — un TRANSFERT. Quatre crochets mordent, un conduit hélicoïdal
 *       relie la victime au porteur, et six bols de sang le remontent en le faisant gonfler.
 *       La victime se vide à vue, le conduit s'amincit, puis rompt.</li>
 *   <li><b>Transfusion</b> (140 t) — un ORGANE. Un cœur anatomique en voxels, deux ventricules
 *       de tailles inégales, crosse aortique et veines coronaires, qui bat en
 *       <i>lub-dub</i> et pousse un bol dans les vaisseaux à chaque systole.</li>
 *   <li><b>Carnage</b> (180 t) — une FRÉNÉSIE. Onze taillades dans onze plans différents, à
 *       cadence accélérée, qui laissent chacune un arc rémanent d'où le sang s'égoutte, puis
 *       une onde au sol et une brume basse.</li>
 * </ul>
 *
 * <p>Le repère est celui des autres signatures : {@code (dx, dz)} pointe de l'ATTAQUANT vers la
 * CIBLE, donc {@code f > 0} est au-delà de la victime et {@code f < 0} du côté du porteur. Le
 * siphon et la transfusion s'en servent pour de bon — ce sont des signatures à deux extrémités.</p>
 */
public final class BloodSignatureVfx {

    private BloodSignatureVfx() {}

    // =================================================================================
    //  VARIANTES
    // =================================================================================

    public static final int GUILLOTINE = 0;
    public static final int EDGE = 1;
    public static final int SIPHON = 2;
    public static final int TRANSFUSION = 3;
    public static final int CARNAGE = 4;

    // =================================================================================
    //  PALETTE
    // =================================================================================
    //  Six rouges, et surtout TROIS matières étrangères. Un enchantement de sang frappe
    //  presque toujours une créature déjà couverte de particules de dégâts rouges : sans
    //  acier, sans os et sans le rose pâle des reflets, la signature se noie dans sa cible.

    /** Sang coagulé, presque noir. C'est le CREUX des masses, jamais leur contour. */
    private static final int CLOT    = 0x2B0507;
    /** Sang veineux, sombre et mat : le corps des flaques et des rigoles. */
    private static final int VENOUS  = 0x5E0A10;
    /** Chair ouverte : la teinte de volume la plus employée. */
    private static final int GORE    = 0x8E0F16;
    /** Sang artériel, vif : tout ce qui vient d'être tranché. */
    private static final int ARTERY  = 0xC2121B;
    /** Éclaboussure fraîche : les bords d'attaque. */
    private static final int FRESH   = 0xE8252C;
    /** Le MÉNISQUE : liseré clair du haut des masses. Jamais en aplat, seulement en bord. */
    private static final int SPRAY   = 0xFF5A52;
    /** Plasma / reflet sur une goutte. Rare, et c'est ce qui la fait lire comme mouillée. */
    private static final int PLASMA  = 0xFFB9A8;

    /** Acier du couperet : la matière étrangère principale. */
    private static final int IRON    = 0x6E7279;
    /** Ombre de l'acier. */
    private static final int IRON_DK = 0x3A3E44;
    /** Tranchant poli : la seule chose plus claire que le plasma dans tout le fichier. */
    private static final int EDGE_LT = 0xD9DEE6;
    /** Bois des montants et du linteau. */
    private static final int TIMBER  = 0x4A3426;
    /** Arête éclairée du bois. Sans elle, les montants sont deux trous noirs. */
    private static final int TIMBER_LIT = 0x8A6A4C;
    /** Os : les crochets du siphon, et les éclats du carnage. */
    private static final int BONE    = 0xE8E0CC;

    private static final int SOLID_FLOOR = 208;

    // =================================================================================
    //  CONSTANTES DE SCÈNE
    // =================================================================================

    /**
     * Hauteur du sol dans le repère local, reçue à chaque image (voir
     * {@code WeaponWorldVfxManager.probeGroundOffset}). La valeur ci-dessous n'est qu'un repli :
     * la refonte de l'ombre a montré qu'une constante en dur enterre l'animation sous toute
     * cible plus courte qu'un joueur.
     */
    private static float GROUND_Y = -0.86f;

    /** Épaisseur d'une dalle posée au sol. Assez fine pour être une surface, pas une marche. */
    private static final float SLAB_H = 0.030f;

    /** Distance porteur → cible retenue pour les signatures à deux extrémités. */
    private static final float REACH = 7.0f;

    /** Accélération de la pesanteur, en unités locales par unité de temps normalisé au carré. */
    private static final float GRAVITY = 11.0f;

    private static final float HALF_PI = 1.5707964f;

    // =================================================================================
    //  ENTRÉE
    // =================================================================================

    /**
     * @param dx,dz  direction unitaire ATTAQUANT → CIBLE
     * @param groundOffset écart entre la surface réelle et le centre de l'effet
     */
    /**
     * T380-BLOOD-LINK : {@code linkLen}, {@code linkEndY} et {@code linkBroken} decrivent le
     * LIEN vivant entre la victime (l'ancre, en {@code f = 0}) et le porteur (en
     * {@code f = -linkLen}, a la hauteur {@code linkEndY} au-dessus de l'ancre).
     *
     * <p>Avant eux, le siphon et la transfusion tendaient leur conduit sur la constante
     * {@code REACH = 7} dans la direction gelee au moment du coup. A trois blocs de
     * distance le tuyau traversait le porteur et continuait quatre blocs derriere lui ;
     * a dix, il s'arretait en l'air. Et aucun des deux bouts ne suivait son entite : il
     * suffisait d'un pas de cote pour que le conduit ne relie plus rien.</p>
     *
     * <p>Les trois autres signatures ignorent ces parametres : le couperet est plante dans
     * le SOL (et sa victime est clouee), l'arete marque une entaille et la flaque qui en
     * tombe, le carnage occupe une aire. Toutes trois sont fixes cote serveur aussi.</p>
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt,
                              float groundOffset,
                              float linkLen, float linkEndY, boolean linkBroken) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        GROUND_Y = Mth.clamp(groundOffset, -4.0f, 0.10f);
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }

        switch (variant) {
            case EDGE -> renderEdge(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            case SIPHON -> renderSiphon(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt,
                    linkLen, linkEndY, linkBroken);
            case TRANSFUSION -> renderTransfusion(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt,
                    linkLen, linkEndY, linkBroken);
            case CARNAGE -> renderCarnage(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
            default -> renderGuillotine(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        }
    }

    // =================================================================================
    //  I. COUPERET DE GUILLOTINE — 150 ticks
    // =================================================================================

    /**
     * Chronologie (150 ticks) :
     * <ul>
     *   <li>0,00–0,10 <b>la plantation</b> : deux montants jaillissent du sol en dépassant leur
     *       hauteur puis en retombant dessus — une poutre plantée rebondit ;</li>
     *   <li>0,10–0,16 <b>le linteau</b> : il claque en place entre les deux montants ;</li>
     *   <li>0,16–0,38 <b>la hisse</b> : la lame monte en <b>cinq à-coups</b> de chaîne et non
     *       d'un glissement continu. C'est ce qui dit qu'un mécanisme la tire ;</li>
     *   <li>0,38–0,50 <b>le suspens</b> : tout s'arrête. La lame tremble d'un demi-pixel et
     *       <i>une seule goutte</i> tombe de son fil. Douze ticks de rien, qui sont le sujet
     *       de la signature : sans eux la chute n'est qu'un objet qui bouge ;</li>
     *   <li>0,50–0,555 <b>la chute</b> : huit ticks ;</li>
     *   <li>0,555–0,62 <b>l'impact</b> : la lame s'enfonce, anneau de choc, gerbe horizontale ;</li>
     *   <li>0,62–0,82 <b>la nappe</b> : une lame de sang monte du trait de coupe, se courbe et
     *       retombe en gouttes ;</li>
     *   <li>0,82–1,00 <b>le retrait</b> : la machine se ravale dans le sol, la tache reste.</li>
     * </ul>
     */
    private static void renderGuillotine(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float t,
                                         float dx, float dz, int alpha, int salt) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float plant = smooth(t / 0.10f);
        final float lintel = smooth((t - 0.10f) / 0.06f);
        final float hoist = clamp((t - 0.16f) / 0.22f);
        final float fall = smooth((t - 0.50f) / 0.055f);
        final float bite = smooth((t - 0.555f) / 0.065f);
        final float sheet = clamp((t - 0.62f) / 0.20f);
        final float sink = smooth((t - 0.82f) / 0.18f);
        final int strobe = (int) (t * 30.0f);

        // Hauteur du couperet. La hisse procède par PALIERS : cinq tractions de chaîne, chacune
        // suivie d'un court repos. Un glissement linéaire donnait un ascenseur ; les paliers
        // donnent un treuil, et c'est la seule différence entre un décor et une machine.
        // topY plafonne a 3,05 et non 4,05 : le couperet mesure 1,40 bloc de la pointe du
        // biseau au sommet du mouton, et le linteau est a 4,60. A 4,05 la masse TRAVERSAIT
        // la poutre — le defaut le plus voyant de la premiere passe.
        final float topY = 3.05f;
        final float neckY = 1.35f;
        float bladeY;
        if (t < 0.16f) {
            bladeY = neckY;
        } else if (hoist < 1.0f) {
            float steps = hoist * 5.0f;
            int done = (int) steps;
            float inner = smooth((steps - done) / 0.62f);          // traction brève, repos long
            bladeY = neckY + (topY - neckY) * ((done + inner) / 5.0f);
        } else if (t < 0.50f) {
            // Suspens : la lame est en haut et TREMBLE d'un demi-pixel. Immobile pour de bon
            // elle se lirait comme une image fixe ; ce frémissement dit qu'elle est retenue.
            bladeY = topY + (hash(salt + 3, strobe) - 0.5f) * 0.016f;
        } else {
            bladeY = topY - (topY - (GROUND_Y + 0.22f)) * fall;
        }

        final float structureDrop = sink * 5.4f;                   // la machine se ravale

        // ---- 1. LES DEUX MONTANTS ----
        // Ils jaillissent en DÉPASSANT leur hauteur de 8 % puis retombent dessus. Une poutre
        // qu'on plante rebondit ; sans ce dépassement, elle pousse comme une tige de blé.
        {
            float over = 1.0f + 0.08f * Mth.sin(clamp(t / 0.10f) * Mth.PI);
            float h = 4.6f * plant * over;
            if (h > 0.02f) {
                for (int side = -1; side <= 1; side += 2) {
                    float lat = side * 0.98f;
                    int segs = 13;
                    for (int i = 0; i < segs; i++) {
                        float u = (i + 0.5f) / segs;
                        float yy = GROUND_Y + u * h - structureDrop;
                        if (yy < GROUND_Y - 0.3f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, yy,
                                0.115f, h / segs * 0.52f, 0.115f, TIMBER, opaque(a * 0.95f));
                        // Arête éclairée. Deux poutres brunes sur un monde sombre sont deux
                        // trous noirs : même règle que l'ombre, aucune masse sans liseré.
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, -0.112f, lat, yy,
                                0.020f, h / segs * 0.50f, 0.118f, TIMBER_LIT, opaque(a * 0.85f));
                        // Rainure de guidage : le trait clair sur la face intérieure. C'est elle
                        // qui dit que quelque chose COULISSE là-dedans.
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat - side * 0.108f, yy,
                                0.030f, h / segs * 0.50f, 0.022f, IRON_DK, opaque(a * 0.80f));
                    }
                    // Ferrures : trois bagues de métal, aux tiers.
                    for (int k = 0; k < 3; k++) {
                        float yy = GROUND_Y + h * (0.22f + 0.30f * k) - structureDrop;
                        if (yy < GROUND_Y - 0.3f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, yy,
                                0.132f, 0.042f, 0.132f, IRON, opaque(a * 0.90f));
                    }
                }
                // Terre déplacée au pied des montants, pendant la plantation seulement.
                if (t < 0.22f) {
                    float burst = Mth.sin(clamp(t / 0.14f) * Mth.PI);
                    for (int i = 0; i < 14; i++) {
                        float h1 = hash(salt + 11, i * 29);
                        float h2 = hash(salt + 12, i * 31);
                        float ang = h1 * 6.2831855f;
                        float rr = (0.85f + 0.9f * h2) * burst;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                Mth.cos(ang) * rr, Mth.sin(ang) * rr,
                                GROUND_Y + 0.04f + burst * 0.30f * h2,
                                0.040f, 0.040f, 0.040f, TIMBER, opaque(a * 0.55f * burst));
                    }
                }
            }
        }

        // ---- 2. LE LINTEAU ----
        if (lintel > 0.001f) {
            float yy = GROUND_Y + 4.60f - structureDrop;
            float w = 1.18f * lintel;
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, yy,
                    0.135f, 0.115f, w, TIMBER, opaque(a));
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, yy + 0.108f,
                    0.140f, 0.026f, w * 0.96f, IRON, opaque(a * 0.85f));
            box(vc, mat, tmp, cx, cy, cz, dx, dz, -0.132f, 0.0f, yy,
                    0.020f, 0.112f, w * 0.98f, TIMBER_LIT, opaque(a * 0.85f));
            // Poulie : deux joues et un axe. Petit, mais c'est l'objet qui explique la chaîne.
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, yy - 0.16f,
                    0.055f, 0.090f, 0.090f, IRON_DK, opaque(a * 0.90f));
        }

        // ---- 3. LA CHAÎNE ----
        // Elle n'existe que tant que la lame pend. Maillons alternés, un sur deux tourné d'un
        // quart de tour : une chaîne dont tous les maillons sont dans le même plan est une
        // crémaillère.
        if (lintel > 0.4f && t < 0.52f) {
            float top = GROUND_Y + 4.42f - structureDrop;
            float bottom = GROUND_Y + bladeY + 1.42f;   // sommet du mouton
            int links = Math.max(1, (int) ((top - bottom) / 0.17f));
            for (int i = 0; i < links; i++) {
                float u = (i + 0.5f) / links;
                float yy = bottom + (top - bottom) * u;
                boolean flat = (i & 1) == 0;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, yy,
                        flat ? 0.052f : 0.022f, 0.062f, flat ? 0.022f : 0.052f,
                        IRON, opaque(a * 0.88f));
            }
        }

        // ---- 4. LE COUPERET ----
        // Le biseau est obtenu en COLONNES : chaque colonne latérale a son propre bas, décalé
        // le long de la diagonale. Une lame dessinée d'un bloc puis inclinée en roulis se
        // serait vissée dans ses rainures.
        if (t > 0.14f || plant > 0.9f) {
            float by = GROUND_Y + bladeY;
            // 24 colonnes et non 17 : a 17, la marche du biseau (0,036) depassait la largeur
            // d'une colonne (0,051) et le tranchant se lisait comme une scie.
            int cols = 24;
            float halfW = 0.86f;
            float bevel = 0.62f;                                   // dénivelé du tranchant
            // BODY_H est la hauteur de lame SOUS le mouton, la meme pour toutes les colonnes.
            // Sans elle, « high » etait constant et la colonne de droite finissait a 0,04 de
            // haut : la lame se reduisait a un triangle, c'est-a-dire a un eclat de verre.
            // Un couperet de guillotine est un TRAPEZE — corps rectangulaire, bord oblique.
            final float bodyH = 0.46f;
            for (int i = 0; i < cols; i++) {
                float u = (i + 0.5f) / cols;
                float lat = -halfW + 2.0f * halfW * u;
                float low = by + bevel * u;                        // le tranchant descend à gauche
                float high = by + bevel + bodyH;
                float mid = (low + high) * 0.5f;
                float hh = (high - low) * 0.5f;
                if (hh <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, mid,
                        0.058f, hh, halfW / cols, IRON, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -0.050f, lat, mid,
                        0.016f, hh * 0.92f, halfW / cols * 0.9f, IRON_DK, opaque(a * 0.85f));
                // Le fil : un liseré clair exactement sur la diagonale basse.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, low + 0.018f,
                        0.062f, 0.018f, halfW / cols, EDGE_LT, opaque(a));
                // Sang séché sur l'acier, dès que la lame a mordu.
                if (bite > 0.02f && hash(salt + 21, i * 17) < 0.45f) {
                    float sm = 0.10f + 0.30f * hash(salt + 22, i * 19);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.058f, lat,
                            low + 0.10f + sm * 0.5f,
                            0.012f, sm * 0.5f, halfW / cols * 0.7f, VENOUS, opaque(a * 0.9f));
                }
            }
            // Le mouton : la masse qui donne son poids à la lame. Il est posé SUR le corps
            // de lame (by + bevel + bodyH), pas à une hauteur fixe.
            float crown = by + bevel + bodyH;
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, crown + 0.15f,
                    0.082f, 0.150f, halfW * 0.72f, IRON_DK, opaque(a));
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, crown + 0.31f,
                    0.088f, 0.022f, halfW * 0.70f, IRON, opaque(a * 0.9f));

            // Traînée de chute : trois rémanences au-dessus de la lame pendant les huit ticks.
            if (t >= 0.50f && fall < 1.0f) {
                for (int k = 1; k <= 3; k++) {
                    float lag = by + k * 0.52f * (1.0f - fall);
                    if (lag > GROUND_Y + topY) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, lag,
                            0.040f, 0.030f, halfW * (1.0f - k * 0.18f),
                            EDGE_LT, opaque(a * (0.42f - k * 0.10f)));
                }
            }
        }

        // ---- 5. LA GOUTTE DU SUSPENS ----
        // Une seule, et c'est pour cela qu'on la voit. Elle naît du fil à 0,40 et tombe.
        if (t > 0.40f && t < 0.52f) {
            float age = (t - 0.40f) / 0.12f;
            float y0 = GROUND_Y + topY;
            float yy = y0 - GRAVITY * 0.085f * age * age;
            if (yy > GROUND_Y + 0.02f) {
                drop(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, -0.52f, yy, 0.044f, a);
            } else {
                splash(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, -0.52f,
                        clamp((age - 0.55f) / 0.45f), 0.55f, a, salt + 31);
            }
        }

        // ---- 6. L'IMPACT ----
        if (bite > 0.001f) {
            // Anneau de choc au sol : il part vite et s'arrête net.
            float rr = 0.30f + 2.35f * smooth(bite);
            int pts = 30;
            for (int i = 0; i < pts; i++) {
                float ang = (i + 0.5f) / pts * 6.2831855f;
                float wob = 1.0f + 0.14f * Mth.sin(ang * 3.0f + salt * 0.01f);
                float thin = 1.0f - bite * 0.55f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr * wob, Mth.sin(ang) * rr * wob, GROUND_Y + 0.012f,
                        0.075f * thin, SLAB_H, 0.075f * thin, ARTERY, opaque(a * 0.88f));
            }
            // Gerbe horizontale : le sang part à plat, dans le plan du trait de coupe.
            int jets = 22;
            for (int i = 0; i < jets; i++) {
                float h1 = hash(salt + 41, i * 23);
                float h2 = hash(salt + 42, i * 29);
                float ang = (h1 - 0.5f) * 2.6f + (i % 2 == 0 ? 0.0f : Mth.PI);
                float speed = 1.6f + 2.6f * h2;
                float age = bite;
                float d = speed * age;
                float yy = GROUND_Y + neckY - GRAVITY * 0.055f * age * age + 0.25f * age;
                if (yy < GROUND_Y + 0.03f) continue;
                float sz = 0.052f * (1.0f - 0.45f * age);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * d, Mth.sin(ang) * d, yy,
                        sz, sz, sz, i % 3 == 0 ? FRESH : ARTERY, opaque(a));
            }
        }

        // ---- 7. LA NAPPE ----
        // Une lame de sang qui monte du trait de coupe, se courbe sous son poids et retombe.
        // Elle est faite de colonnes indépendantes dont le sommet suit une parabole : une
        // nappe d'un seul tenant se lirait comme un drap, pas comme du liquide.
        if (sheet > 0.001f) {
            int cols = 19;
            for (int i = 0; i < cols; i++) {
                float u = (i + 0.5f) / cols;
                float lat = (u - 0.5f) * 2.1f;
                float phase = clamp(sheet * 1.35f - Math.abs(u - 0.5f) * 0.55f);
                if (phase <= 0.0f) continue;
                float rise = Mth.sin(phase * Mth.PI) * (1.45f - 0.85f * Math.abs(u - 0.5f) * 2.0f);
                if (rise <= 0.02f) continue;
                int segs = 7;
                for (int k = 0; k < segs; k++) {
                    float v = (k + 0.5f) / segs;
                    float yy = GROUND_Y + 0.06f + rise * v;
                    float lean = 0.42f * rise * v * v;             // elle se couche en montant
                    int rgb = v > 0.80f ? SPRAY : (v > 0.45f ? ARTERY : GORE);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, lean, lat, yy,
                            0.038f, rise / segs * 0.56f, 0.062f, rgb, opaque(a * 0.95f));
                }
                // La crête se détache en gouttes dans la seconde moitié.
                if (phase > 0.55f) {
                    float age = (phase - 0.55f) / 0.45f;
                    float yy = GROUND_Y + 0.06f + rise - GRAVITY * 0.05f * age * age;
                    if (yy > GROUND_Y + 0.04f) {
                        drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                                0.42f * rise + 0.5f * age, lat, yy, 0.034f, a);
                    }
                }
            }
        }

        // ---- 8. LA TACHE ----
        if (bite > 0.1f) {
            pool(vc, mat, tmp, cx, cy, cz, dx, dz,
                    0.0f, 0.0f, 1.65f * smooth(clamp((t - 0.56f) / 0.26f)) * (1.0f - sink * 0.75f),
                    a, salt + 51);
        }
    }

    // =================================================================================
    //  II. FIL QUI GOUTTE — 120 ticks
    // =================================================================================

    /**
     * Chronologie (120 ticks) :
     * <ul>
     *   <li>0,00–0,08 <b>le trait</b> : une ligne claire en diagonale, sans épaisseur. C'est le
     *       passage de la lame, pas encore une plaie ;</li>
     *   <li>0,08–0,22 <b>l'ouverture</b> : le trait s'écarte en amande, le creux sombre apparaît ;</li>
     *   <li>0,22–0,55 <b>les rigoles</b> : sept coulées descendent à des vitesses différentes,
     *       chacune terminée par une perle qui grossit ;</li>
     *   <li>0,30–0,85 <b>les gouttes</b> : quand une perle est trop lourde elle se détache,
     *       tombe en parabole et éclabousse ;</li>
     *   <li>0,25–0,95 <b>la flaque</b> : elle s'étale, et chaque goutte y creuse une ride ;</li>
     *   <li>0,85–1,00 <b>la fermeture</b> : l'amande se pince, la flaque rétrécit.</li>
     * </ul>
     */
    private static void renderEdge(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float trace = smooth(t / 0.08f);
        final float open = smooth((t - 0.08f) / 0.14f);
        final float shut = smooth((t - 0.85f) / 0.15f);
        final float gape = open * (1.0f - shut);

        final float woundY = 0.95f;                                // hauteur du torse
        final float tilt = 0.52f;                                  // l'entaille est oblique
        final float span = 1.25f;

        // ---- 1. L'ENTAILLE ----
        {
            int segs = 21;
            for (int i = 0; i < segs; i++) {
                float u = (i + 0.5f) / segs;
                float along = (u - 0.5f) * 2.0f;                   // -1 .. 1
                float lat = along * span;
                float yy = woundY + along * tilt * 0.5f;
                float reveal = clamp((trace - Math.abs(along) * 0.35f) * 1.6f);
                if (reveal <= 0.0f) continue;
                // Largeur en amande : nulle aux pointes, maximale au centre.
                float lens = Mth.sqrt(Math.max(0.0f, 1.0f - along * along));
                float w = 0.012f + 0.145f * lens * gape;

                // Le creux. Il n'est dessiné que s'il a de l'épaisseur, sinon on ne garde
                // que les deux lèvres : une plaie fermée est un trait, pas un trait épais.
                if (w > 0.035f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.055f, lat, yy,
                            0.030f, w, 0.064f, CLOT, opaque(a));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.070f, lat, yy,
                            0.016f, w * 0.62f, 0.052f, VENOUS, opaque(a * 0.95f));
                }
                // Les deux lèvres, écartées par l'ouverture.
                for (int s = -1; s <= 1; s += 2) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.082f, lat, yy + s * w,
                            0.022f, 0.020f, 0.062f, s > 0 ? FRESH : ARTERY,
                            opaque(a * reveal));
                }
                // Perles le long de la lèvre basse : le sang qui affleure avant de couler.
                if (gape > 0.3f && hash(salt + 61, i * 13) < 0.55f) {
                    float bead = 0.016f + 0.018f * hash(salt + 62, i * 17);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.092f, lat, yy - w - 0.014f,
                            bead, bead, bead, ARTERY, opaque(a));
                }
            }
        }

        // ---- 2. LES RIGOLES ----
        // Sept, à sept vitesses. Des coulées synchronisées se lisent comme un peigne ; c'est
        // l'écart entre elles qui fait le ruissellement.
        final int runnels = 7;
        for (int i = 0; i < runnels; i++) {
            float h1 = hash(salt + 71, i * 23);
            float h2 = hash(salt + 72, i * 29);
            float h3 = hash(salt + 73, i * 31);
            float along = (i + 0.5f) / runnels * 2.0f - 1.0f;
            float lat = along * span * 0.88f + (h1 - 0.5f) * 0.10f;
            float top = woundY + along * tilt * 0.5f - 0.04f;
            float start = 0.22f + h1 * 0.14f;
            float speed = 0.55f + 0.75f * h2;
            float grow = clamp((t - start) * speed * 2.2f);
            if (grow <= 0.0f) continue;
            float len = (top - GROUND_Y) * grow * (1.0f - shut * 0.9f);
            if (len <= 0.02f) continue;

            int segs = Math.max(2, (int) (len / 0.085f));
            for (int k = 0; k < segs; k++) {
                float v = (k + 0.5f) / segs;
                float yy = top - len * v;
                // La rigole SERPENTE : une coulée parfaitement droite est un trait de peinture.
                float wig = Mth.sin(v * 7.0f + h3 * 6.28f) * 0.028f * (1.0f - v * 0.4f);
                float w = (0.026f - 0.011f * v) * (0.8f + 0.4f * h2);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.086f, lat + wig, yy,
                        0.016f, len / segs * 0.56f, w,
                        v < 0.25f ? ARTERY : (v < 0.7f ? GORE : VENOUS), opaque(a * 0.95f));
            }
            // La perle de tête : elle grossit jusqu'à se détacher.
            float swell = fract(grow * 2.6f + h3);
            float bead = 0.018f + 0.026f * swell;
            float by = top - len;
            if (by > GROUND_Y + 0.03f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.088f, lat, by,
                        bead * 0.8f, bead, bead, ARTERY, opaque(a));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.096f, lat, by + bead * 0.5f,
                        bead * 0.34f, bead * 0.30f, bead * 0.34f, PLASMA, opaque(a * 0.9f));
            }
        }

        // ---- 3. LES GOUTTES ----
        // Chacune a sa date de naissance ; leur chute est une vraie parabole et leur impact
        // creuse une ride dans la flaque.
        {
            final int drops = 16;
            for (int i = 0; i < drops; i++) {
                float h1 = hash(salt + 81, i * 19);
                float h2 = hash(salt + 82, i * 23);
                float born = 0.30f + h1 * 0.52f;
                float age = (t - born) / 0.26f;
                if (age <= 0.0f || age >= 1.6f) continue;
                float lat = (h2 - 0.5f) * span * 1.7f;
                float y0 = woundY - 0.25f - h1 * 0.5f;
                float yy = y0 - GRAVITY * 0.085f * age * age;
                if (yy > GROUND_Y + 0.03f) {
                    drop(vc, mat, tmp, cx, cy, cz, dx, dz, 0.088f, lat, yy,
                            0.026f + 0.012f * h2, a);
                } else {
                    float hit = clamp((age - Mth.sqrt((y0 - GROUND_Y) / (GRAVITY * 0.085f))) / 0.35f);
                    if (hit > 0.0f && hit < 1.0f) {
                        splash(vc, mat, tmp, cx, cy, cz, dx, dz, 0.088f, lat, hit,
                                0.42f, a, salt + 91 + i);
                    }
                }
            }
        }

        // ---- 4. LA FLAQUE ----
        {
            float grow = smooth(clamp((t - 0.25f) / 0.45f));
            float r = 1.15f * grow * (1.0f - shut * 0.70f);
            if (r > 0.04f) {
                pool(vc, mat, tmp, cx, cy, cz, dx, dz, 0.16f, 0.0f, r, a, salt + 101);
            }
        }
    }

    // =================================================================================
    //  III. SIPHON CARMIN — 170 ticks
    // =================================================================================

    /**
     * Chronologie (170 ticks) :
     * <ul>
     *   <li>0,00-0,08 <b>la morsure</b> : la bride se plaque sur la victime et quatre crochets
     *       se plantent autour du point de ponction ;</li>
     *   <li>0,08-0,20 <b>le tuyau</b> : il se construit de la victime vers le porteur et le
     *       calice apparait dans la main au moment ou le tuyau l'atteint ;</li>
     *   <li>0,20-0,80 <b>la pompe</b> : six bols remontent le tuyau en le faisant gonfler, et
     *       le calice monte d'un sixieme a chaque arrivee ;</li>
     *   <li>0,80-0,90 <b>la tension</b> : le tuyau s'amincit, l'arc s'aplatit ;</li>
     *   <li>0,90-1,00 <b>la rupture</b> : le tuyau s'arrache de la victime et se retracte vers
     *       le calice, qui reste plein dans la main.</li>
     * </ul>
     *
     * <p><b>T381 : refonte pour la lisibilite.</b> La version precedente dessinait une double
     * helice qui pendait, plus une colonne de niveau batie depuis le SOL — a l'ecran, une
     * saucisse posee par terre a cote d'un tonneau, et rien aux deux bouts. Trois corrections
     * de fond : le point de ponction est maintenant a la hauteur de l'ancre (la poitrine) et
     * non 95 cm au-dessus ; le tuyau passe PAR-DESSUS au lieu de pendre, comme un vrai siphon ;
     * et les deux extremites portent un objet reconnaissable, l'embout et le calice. La
     * colonne de niveau a disparu : elle etait invisible en jeu (dans le corps du mob) et
     * illisible dans l'apercu. C'est le calice qui raconte le volume pris.</p>
     */
    private static void renderSiphon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt,
                                     float linkLen, float linkEndY, boolean linkBroken) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float bite  = smooth(t / 0.08f);
        final float build = smooth((t - 0.08f) / 0.12f);
        final float drain = clamp((t - 0.20f) / 0.60f);
        final float slack = clamp((t - 0.80f) / 0.10f);
        final float snap  = Math.max(clamp((t - 0.90f) / 0.10f), linkBroken ? 1.0f : 0.0f);

        final float reach = linkLen;
        // Le point de ponction est a la POITRINE, c'est-a-dire a l'ancre elle-meme. L'ancien
        // 0,95 placait la morsure au-dessus de la tete de la plupart des mobs.
        final float mouthY = 0.12f;
        final float handY  = linkEndY;
        // Un siphon MONTE puis redescend : c'est ce qui le distingue d'un tuyau pose. L'arc
        // s'aplatit a mesure que le conduit se tend. Proportionnel a la portee, sinon un
        // conduit de deux blocs se cabre comme une anse de panier.
        final float arch = (0.30f + 0.085f * reach) * (1.0f - 0.30f * drain);

        // Combien de bols sont DEJA arrives dans le calice : c'est le niveau du verre.
        float poured = 0.0f;
        for (int b = 0; b < 6; b++) {
            float launch = 0.20f + b * 0.098f;
            poured += smooth((t - launch - 0.30f) / 0.05f);
        }
        final float fill = Math.min(1.0f, poured / 6.0f);

        // ---------------------------------------------------------------------------------
        //  A. L'EMBOUT : ce qui est visse dans la victime.
        // ---------------------------------------------------------------------------------
        if (bite > 0.001f && snap < 0.85f) {
            float set = smooth(bite) * (1.0f - snap);
            // La bride : deux couronnes de fer de rayon decroissant, de la peau vers le tuyau.
            for (int ring = 0; ring < 2; ring++) {
                float rr = (0.26f - ring * 0.07f) * set;
                float ff = 0.02f - ring * 0.10f;
                int pts = 10;
                for (int k = 0; k < pts; k++) {
                    float ang = (k + 0.5f) / pts * 6.2831855f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            ff, Mth.cos(ang) * rr, mouthY + Mth.sin(ang) * rr,
                            0.042f, 0.048f, 0.048f,
                            ring == 0 ? IRON_DK : IRON, opaque(a * 0.95f));
                }
            }
            // Quatre crochets d'os plantes en croix, pointe vers l'interieur.
            for (int i = 0; i < 4; i++) {
                float ang = i * HALF_PI + 0.42f;
                float lat = Mth.cos(ang) * 0.30f * set;
                float yy  = mouthY + Mth.sin(ang) * 0.30f * set;
                float plunge = 0.16f - 0.13f * set;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        plunge + 0.11f, lat * 1.30f, yy + Mth.sin(ang) * 0.09f,
                        0.110f, 0.040f, 0.040f, BONE, opaque(a * 0.95f));
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        plunge, lat * 1.05f, yy, 0.048f, 0.046f, 0.046f, BONE, opaque(a));
                if (set > 0.6f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            plunge - 0.02f, lat * 1.10f, yy - 0.06f,
                            0.018f, 0.034f, 0.018f, ARTERY, opaque(a));
                }
            }
        }

        // ---------------------------------------------------------------------------------
        //  B. LE TUYAU : un arc par-dessus, a cotes, qui gonfle au passage des bols.
        // ---------------------------------------------------------------------------------
        final int segs = Math.max(30, Math.round(9.0f * reach));
        final float segLen = reach / segs;
        for (int i = 0; i < segs; i++) {
            float u = (i + 0.5f) / segs;                       // 0 = victime, 1 = porteur
            if (u > build) continue;
            // La rupture part de la VICTIME : le tuyau s'arrache la ou il etait plante, et
            // le bout libre file vers le calice. Couper au milieu ne racontait rien.
            if (u < snap) continue;

            float f  = u * (-reach + 0.22f);
            float yy = mouthY + (handY - mouthY) * u + Mth.sin(u * Mth.PI) * arch;
            // LE BEC VERSEUR. Sans lui le tuyau s'arretait a l'aplomb du calice, au-dessus
            // du bord : deux objets voisins, pas un tuyau qui se DEVERSE dedans. On vise le
            // bord de la coupe — une descente libre le faisait ressortir 50 cm SOUS le pied.
            if (u > 0.90f) {
                float k = smooth((u - 0.90f) / 0.10f);
                yy = yy + (handY + 0.22f - yy) * k;
            }
            // Le fouet du bout libre, juste apres l'arrachement.
            float whip = snap > 0.001f && u < snap + 0.16f
                    ? Mth.sin((t - 0.90f) * 34.0f) * 0.30f * (1.0f - snap) : 0.0f;

            float bulge = 0.0f;
            for (int b = 0; b < 6; b++) {
                float launch = 0.20f + b * 0.098f;
                float travel = (t - launch) / 0.30f;
                if (travel <= 0.0f || travel >= 1.0f) continue;
                float pos = smooth(travel);
                float d = Math.abs(u - pos);
                if (d < 0.075f) bulge += (1.0f - d / 0.075f);
            }
            bulge = Math.min(1.0f, bulge);

            float thin = 1.0f - 0.30f * slack;
            float r = (0.085f + 0.075f * bulge) * thin;
            int body = bulge > 0.30f ? FRESH : GORE;

            // Le corps du tuyau.
            box(vc, mat, tmp, cx, cy, cz, dx, dz, f, whip, yy + whip * 0.4f,
                    segLen * 0.62f, r, r, body, opaque(a * 0.96f));
            // La crete claire et l'ombre basse : sans elles un tuyau voxel est un ruban plat.
            box(vc, mat, tmp, cx, cy, cz, dx, dz, f, whip, yy + whip * 0.4f + r * 0.80f,
                    segLen * 0.58f, r * 0.30f, r * 0.52f, ARTERY, opaque(a * 0.85f));
            box(vc, mat, tmp, cx, cy, cz, dx, dz, f, whip, yy + whip * 0.4f - r * 0.82f,
                    segLen * 0.58f, r * 0.26f, r * 0.50f, CLOT, opaque(a * 0.80f));

            // LES COTES. Un anneau tous les 36 cm, qui GLISSE vers le porteur : c'est ce
            // glissement qui dit le sens de l'ecoulement. L'helice le disait aussi, mais
            // seulement de pres ; une cote se lit a dix blocs.
            float band = (u * reach * 2.35f) - t * 3.1f;
            float frac = band - Mth.floor(band);
            if (frac < 0.14f) {
                // Bande SOMBRE et non veineuse : sur un tuyau rouge, une cote rouge foncee
                // ne se voit pas. Un anneau presque noir, lui, se lit a dix blocs, et son
                // glissement vers le porteur donne le sens de l'ecoulement.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, whip, yy + whip * 0.4f,
                        segLen * 0.80f, r * 1.30f, r * 1.30f, CLOT, opaque(a * 0.95f));
            }
            // Le front du bol : un liseré clair juste devant le renflement.
            if (bulge > 0.55f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, whip, yy + whip * 0.4f + r * 1.05f,
                        segLen * 0.46f, 0.014f, r * 1.30f, SPRAY, opaque(a));
            }
        }

        // ---------------------------------------------------------------------------------
        //  C. LE CALICE : le tuyau mene ICI, dans la main du porteur, et le verre se remplit.
        // ---------------------------------------------------------------------------------
        {
            float born = smooth((t - 0.16f) / 0.06f);
            if (born > 0.01f) {
                // Le calice est tenu DEVANT le porteur, pas dans son torse.
                float cf = -reach + 0.22f;
                float base = handY - 0.20f;                     // fond de la coupe
                float lip  = handY + 0.26f;                     // bord
                float sc = born;
                // Le pied et la tige : ce qui transforme un bol en calice tenu a la main.
                for (int k = 0; k < 8; k++) {
                    float ang = (k + 0.5f) / 8.0f * 6.2831855f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            cf + Mth.cos(ang) * 0.18f * sc, Mth.sin(ang) * 0.18f * sc,
                            base - 0.32f, 0.050f, 0.032f, 0.050f, IRON_DK, opaque(a * 0.90f));
                }
                box(vc, mat, tmp, cx, cy, cz, dx, dz, cf, 0.0f, base - 0.16f,
                        0.040f, 0.090f, 0.040f, IRON, opaque(a * 0.92f));
                // LA COUPE. Six rangs serres : la paroi doit etre pleine. A trois rangs
                // espaces de dix-sept centimetres, le calice sortait en ressort a boudin.
                final int walls = 6;
                for (int ring = 0; ring < walls; ring++) {
                    float v = ring / (float) (walls - 1);
                    float rr = (0.19f + 0.16f * v) * sc;
                    float yy = base + (lip - base) * v;
                    int pts = 12 + ring;
                    for (int k = 0; k < pts; k++) {
                        float ang = (k + 0.5f) / pts * 6.2831855f + ring * 0.21f;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                cf + Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                                0.046f, 0.042f, 0.046f,
                                ring == walls - 1 ? EDGE_LT : (ring == 0 ? IRON_DK : IRON),
                                opaque(a * 0.95f));
                    }
                }
                // Le fond, sans quoi on voit le sol a travers le verre.
                for (int k = 0; k < 7; k++) {
                    float ang = (k + 0.5f) / 7.0f * 6.2831855f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            cf + Mth.cos(ang) * 0.09f * sc, Mth.sin(ang) * 0.09f * sc, base - 0.02f,
                            0.055f, 0.030f, 0.055f, IRON_DK, opaque(a * 0.95f));
                }
                // LE SANG DANS LE VERRE. C'est le compteur de la signature : six bols, six
                // crans. Un spectateur comprend le vol de vie sans lire un seul chiffre.
                if (fill > 0.01f) {
                    // UN VOLUME, PAS UN DISQUE. La premiere version posait une galette plate
                    // au niveau du liquide : derriere la paroi de la coupe, elle etait
                    // invisible, et un calice plein ressemblait a un calice vide. On empile
                    // donc des tranches du fond jusqu'au niveau, et seule la derniere porte
                    // le menisque clair.
                    float lv = 0.14f + 0.78f * fill;
                    float top = base + (lip - base) * lv;
                    int layers = Math.max(3, Math.round((top - base) / 0.045f));
                    for (int ly = 0; ly < layers; ly++) {
                        float v = (ly + 0.5f) / layers;
                        float yy = base + (top - base) * v + 0.02f;
                        // La coupe s'evase : le liquide suit son profil interieur.
                        float prof = (yy - base) / Math.max(0.05f, lip - base);
                        float rr = (0.17f + 0.14f * prof) * sc;
                        boolean surface = ly == layers - 1;
                        int pts = surface ? 12 : 9;
                        for (int k = 0; k < pts; k++) {
                            float ang = (k + 0.5f) / pts * 6.2831855f + ly * 0.7f;
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    cf + Mth.cos(ang) * rr * 0.84f, Mth.sin(ang) * rr * 0.84f, yy,
                                    0.066f, 0.034f, 0.066f,
                                    surface ? SPRAY : (v > 0.5f ? FRESH : GORE),
                                    opaque(a * 0.95f));
                        }
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, cf, 0.0f, yy,
                                0.075f, 0.034f, 0.075f,
                                surface ? ARTERY : VENOUS, opaque(a * 0.95f));
                    }
                }
                // L'eclaboussure a l'arrivee d'un bol : le verre encaisse.
                for (int b = 0; b < 6; b++) {
                    float land = 0.20f + b * 0.098f + 0.30f;
                    float age = (t - land) / 0.05f;
                    if (age <= 0.0f || age >= 1.0f) continue;
                    for (int k = 0; k < 4; k++) {
                        float h1 = hash(salt + 211 + b, k * 13);
                        float ang = h1 * 6.2831855f;
                        float d = 0.10f + 0.22f * age;
                        drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                                cf + Mth.cos(ang) * d, Mth.sin(ang) * d,
                                lip + 0.30f * age - GRAVITY * 0.06f * age * age,
                                0.026f, Math.round(a * (1.0f - age)));
                    }
                }
            }
        }

        // ---------------------------------------------------------------------------------
        //  D. CE QUI TOMBE : la plaie goutte, et une petite flaque se forme sous la victime.
        // ---------------------------------------------------------------------------------
        if (drain > 0.02f) {
            for (int k = 0; k < 4; k++) {
                float h1 = hash(salt + 331, k * 17);
                float cycle = (t * 3.1f + h1) % 1.0f;
                float fall = cycle;
                float yy = mouthY - 0.10f - GRAVITY * 0.055f * fall * fall;
                if (yy < GROUND_Y + 0.05f) continue;
                drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.06f + (h1 - 0.5f) * 0.12f, (hash(salt + 332, k) - 0.5f) * 0.26f,
                        yy, 0.026f, Math.round(a * 0.85f));
            }
            float r = 0.22f + 0.46f * smooth(drain);
            pool(vc, mat, tmp, cx, cy, cz, dx, dz, 0.05f, 0.0f, r, Math.round(a * 0.80f),
                    salt + 341);
        }

        // ---------------------------------------------------------------------------------
        //  E. L'ARRACHEMENT : les crochets sautent et le sang gicle au point de ponction.
        // ---------------------------------------------------------------------------------
        if (snap > 0.001f) {
            for (int i = 0; i < 4; i++) {
                float ang = i * HALF_PI + 0.42f;
                float d = 0.30f + 1.1f * snap;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.10f + 0.5f * snap, Mth.cos(ang) * d,
                        mouthY + Mth.sin(ang) * d * 0.6f - GRAVITY * 0.04f * snap * snap,
                        0.080f, 0.038f, 0.038f, BONE, opaque(a * (1.0f - snap)));
            }
            for (int k = 0; k < 9; k++) {
                float h1 = hash(salt + 351, k * 23);
                float h2 = hash(salt + 352, k * 29);
                float d = (0.3f + 1.5f * h1) * snap;
                drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.05f - d * 0.5f, (h2 - 0.5f) * 1.8f * snap,
                        mouthY + 0.4f * snap - GRAVITY * 0.07f * snap * snap,
                        0.028f, Math.round(a * (1.0f - snap * 0.8f)));
            }
        }
    }

    // =================================================================================
    //  IV. TRANSFUSION VITALE — 140 ticks
    // =================================================================================

    /**
     * Chronologie (140 ticks) :
     * <ul>
     *   <li>0,00–0,12 <b>l'assemblage</b> : des gouttes montent de la victime et convergent ;</li>
     *   <li>0,12–0,22 <b>le premier battement</b>, faible ;</li>
     *   <li>0,22–0,80 <b>six battements</b> de plus en plus amples. Chaque systole pousse un
     *       bol dans les vaisseaux et lâche un anneau de pression ;</li>
     *   <li>0,80–0,92 <b>la résorption</b> : le rythme ralentit, le cœur se défait en pluie ;</li>
     *   <li>0,92–1,00 <b>la flaque tiède</b> qui rétrécit.</li>
     * </ul>
     *
     * <p>Le cœur bat en <b>lub-dub</b> : deux contractions rapprochées puis un silence plus long.
     * Un battement unique et régulier donne un ballon qu'on gonfle ; c'est le DOUBLE coup qui
     * fait lire un organe.</p>
     */
    private static void renderTransfusion(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float cx, float cy, float cz, float t,
                                          float dx, float dz, int alpha, int salt,
                                          float linkLen, float linkEndY, boolean linkBroken) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float gather = smooth(t / 0.12f);
        // Le lien casse precipite la resorption : les vaisseaux s'arrachent, le coeur se
        // degonfle. Le serveur arrete les battements au meme instant et a la meme distance.
        final float fade = Math.max(smooth((t - 0.80f) / 0.12f), linkBroken ? 1.0f : 0.0f);

        // Position du cœur : au MILIEU du segment porteur-victime, et à 1,60 bloc au-dessus
        // de l'ancre. Ces deux nombres ne sont pas choisis pour la mise en page : ce sont
        // exactement ceux du serveur (hx/hz = milieu des deux, hy = oy + 1,6). Avant, le cœur
        // était dessiné à 1,30 bloc du point d'impact et 2,55 de haut, c'est-à-dire AILLEURS
        // que le point d'où partaient réellement les battements et le soin.
        final float heartF = -linkLen * 0.5f;
        final float heartY = 1.60f;
        final float scale = 1.45f * gather * (1.0f - fade * 0.55f);

        // --- Le rythme. Sept battements, répartis de 0,16 à 0,84, chacun en lub-dub. ---
        float contract = 0.0f;
        int beatIndex = -1;
        float beatAge = 0.0f;
        for (int b = 0; b < 7; b++) {
            float at = 0.16f + b * 0.098f;
            float age = (t - at) / 0.098f;
            if (age < 0.0f || age > 1.0f) continue;
            beatIndex = b;
            beatAge = age;
            float amp = 0.10f + 0.085f * (b / 6.0f);
            // lub : court et fort. dub : plus tard, plus faible. puis plat.
            float lub = Mth.sin(clamp(age / 0.22f) * Mth.PI);
            float dub = age > 0.30f ? Mth.sin(clamp((age - 0.30f) / 0.24f) * Mth.PI) * 0.62f : 0.0f;
            contract = amp * Math.max(lub, dub);
        }
        final float beat = 1.0f - contract;

        // ---- 1. L'ASSEMBLAGE ----
        if (gather < 1.0f) {
            for (int i = 0; i < 20; i++) {
                float h1 = hash(salt + 121, i * 17);
                float h2 = hash(salt + 122, i * 19);
                float u = clamp(gather * 1.4f - h1 * 0.4f);
                if (u <= 0.0f) continue;
                float ang = h2 * 6.2831855f;
                float rr = (1.5f - 1.4f * u) * (0.5f + h1);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        heartF * u + Mth.cos(ang) * rr,
                        Mth.sin(ang) * rr,
                        GROUND_Y + 0.4f + (heartY - GROUND_Y - 0.4f) * u,
                        0.030f, 0.030f, 0.030f, ARTERY, opaque(a * (1.0f - u * 0.3f)));
            }
        }

        // ---- 2. LE CŒUR ----
        if (scale > 0.03f) {
            drawHeart(vc, mat, tmp, cx, cy, cz, dx, dz,
                    heartF, 0.0f, heartY, scale * beat, contract, t, a, salt);
        }

        // ---- 3. L'ANNEAU DE PRESSION ----
        // Un par systole. Il part du cœur, à plat, et s'évanouit en s'élargissant.
        if (beatIndex >= 0 && beatAge < 0.55f) {
            float rr = 0.45f + 2.2f * smooth(beatAge / 0.55f);
            float thin = 1.0f - beatAge / 0.55f;
            int pts = 22;
            for (int i = 0; i < pts; i++) {
                float ang = (i + 0.5f) / pts * 6.2831855f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        heartF + Mth.cos(ang) * rr, Mth.sin(ang) * rr, heartY,
                        0.044f * thin, 0.030f * thin, 0.044f * thin,
                        SPRAY, opaque(a * 0.75f * thin));
            }
        }

        // ---- 4. LES VAISSEAUX ----
        // Deux en entrée (depuis la victime), deux en sortie (vers le porteur). Ils sont
        // dessinés en arcs et non en droites : un vaisseau tendu est un câble.
        {
            for (int v = 0; v < 4; v++) {
                boolean intake = v < 2;
                float side = (v % 2 == 0) ? 1.0f : -1.0f;
                // Entrée : la victime, à l'ancre. Sortie : le porteur, là où il est.
                float endF = intake ? 0.0f : -linkLen;
                float endY = intake ? 1.05f : linkEndY;
                // Le pas doit rester inferieur au diametre du tube, sinon on dessine un
                // collier de perles et non un vaisseau. Et il se mesure sur la LONGUEUR
                // REELLE du trajet, pas sur son ecart horizontal : les deux vaisseaux
                // d'entree ne couvrent que 1,30 bloc en f mais descendent de 1,50 en
                // hauteur, plus la fleche de leur arc. Dimensionnes sur f seul, ils
                // sortaient en pointilles alors que les deux autres etaient pleins.
                float sagMax = intake ? 0.45f : 0.62f;
                float run = Mth.sqrt((endF - heartF) * (endF - heartF)
                        + (endY - heartY) * (endY - heartY)) + sagMax * 2.0f;
                int segs = Math.max(18, Math.round(run / 0.055f));
                for (int i = 0; i < segs; i++) {
                    float u = (i + 0.5f) / segs;
                    float f = heartF + (endF - heartF) * u;
                    float sag = Mth.sin(u * Mth.PI) * (intake ? 0.45f : 0.62f);
                    float yy = heartY + (endY - heartY) * u - sag;
                    float lat = side * (0.22f + 0.42f * Mth.sin(u * Mth.PI));
                    // Gonflement du bol poussé par la dernière systole.
                    float swell = 0.0f;
                    if (beatIndex >= 0) {
                        float pos = intake ? (1.0f - beatAge * 1.5f) : beatAge * 1.5f;
                        float d = Math.abs(u - pos);
                        if (d < 0.14f) swell = (1.0f - d / 0.14f);
                    }
                    float r = (0.040f + 0.052f * swell) * (1.0f - fade * 0.8f);
                    if (r <= 0.006f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lat, yy,
                            Math.abs(endF - heartF) / segs * 0.58f, r, r,
                            swell > 0.4f ? FRESH : (intake ? VENOUS : ARTERY), opaque(a * 0.92f));
                }
            }
        }

        // ---- 5. LA RÉSORPTION ----
        if (fade > 0.001f) {
            for (int i = 0; i < 24; i++) {
                float h1 = hash(salt + 131, i * 23);
                float h2 = hash(salt + 132, i * 29);
                float age = clamp((fade - h1 * 0.35f) * 1.5f);
                if (age <= 0.0f) continue;
                float ang = h2 * 6.2831855f;
                float rr = 0.5f + 0.7f * h1;
                float y0 = heartY;
                float yy = y0 - GRAVITY * 0.10f * age * age;
                if (yy < GROUND_Y + 0.05f) continue;
                drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                        heartF + Mth.cos(ang) * rr - linkLen * 0.28f * age,
                        Mth.sin(ang) * rr, yy, 0.028f, a);
            }
            pool(vc, mat, tmp, cx, cy, cz, dx, dz, heartF, 0.0f,
                    0.85f * fade * (1.0f - clamp((t - 0.94f) / 0.06f)), a, salt + 141);
        }
    }

    /**
     * Le CŒUR, modèle anatomique.
     *
     * <p><b>T382 — pourquoi la version précédente sortait en amande.</b> Son profil valait
     * {@code sin(sqrt(v)·2,55)·(0,52−0,10·v)} : une courbe maximale au MILIEU (0,46 à
     * mi-hauteur) et qui retombe aux deux bouts (0,23 à la base). Un volume large au centre
     * et pointu en haut comme en bas est une amande. Un cœur est large <i>en haut</i> — la
     * base, où arrivent les gros vaisseaux — et pointu <i>en bas</i>, une seule fois.</p>
     *
     * <p>Le corps est bâti en tranches horizontales dont la section est l'<b>union de deux
     * disques inégaux</b> : le ventricule gauche, plus gros et plus rond, et le droit, plus
     * petit, appliqué sur son flanc. Là où les deux frontières se croisent naît le
     * <b>sillon interventriculaire</b>, qu'on creuse d'une ligne sombre et que suit
     * l'artère interventriculaire antérieure. Au-dessus des ventricules, le <b>sillon
     * auriculo-ventriculaire</b> — la ceinture du cœur — puis les deux <b>oreillettes</b>,
     * chacune coiffée de son <b>auricule</b>, ces deux petites oreilles plissées sans
     * lesquelles un cœur n'est qu'un cône.</p>
     *
     * <p>Les gros vaisseaux font la moitié de la lecture : <b>crosse aortique</b> et ses
     * trois branches (tronc brachio-céphalique, carotide, subclavière), <b>tronc
     * pulmonaire</b> qui croise devant l'aorte et bifurque en Y, <b>veine cave supérieure</b>
     * à droite, <b>cave inférieure</b> sous elle, deux <b>veines pulmonaires</b> à l'arrière.
     * Ils sont épais : un vaisseau plus fin que la maille de la coque disparaît dans les
     * joints.</p>
     *
     * <p>La systole écrase les ventricules et <b>gonfle</b> les oreillettes au même instant :
     * c'est ce qu'il se passe dans un cœur, et c'est ce qui donne au battement sa vie.</p>
     */
    private static void drawHeart(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float hf, float hl, float hy,
                                  float s, float contract, float t, int alpha, int salt) {
        final int slices = 20;
        // 1,22 et non 1,34 : a 1,34 pour 0,90 de large, le corps sortait en cone. Un coeur
        // humain tient dans un rapport hauteur/largeur de 1,35 environ — il est TRAPU.
        final float H = 1.22f * s;                 // apex -> sommet des oreillettes
        final float AV = 0.74f;                    // hauteur relative du sillon AV
        // Le cœur se couche en avant et sur le côté : l'apex part vers l'avant-gauche.
        final float lean = 0.20f;
        final float leanL = 0.26f;
        final float step = H / slices;

        for (int i = 0; i < slices; i++) {
            float v = (i + 0.5f) / slices;             // 0 = apex (bas), 1 = sommet (haut)
            float yy = hy + (v - 0.46f) * H;
            float fOff = hf + lean * (v - 0.46f) * H;
            float lOff = hl + leanL * (v - 0.46f) * H;

            boolean atrium = v > AV;
            float w;
            if (!atrium) {
                // VENTRICULES : pointe en bas, largeur maximale juste sous le sillon AV.
                float u = v / AV;
                w = 0.60f * Mth.sin(1.5707964f * (float) Math.pow(u, 0.62))
                        * (0.62f + 0.38f * (float) Math.sqrt(u))
                        * (1.0f - 0.09f * u * u * u * u);
                // L'apex est une POINTE ARRONDIE, pas une section coupee : sans ce facteur
                // la tranche du bas mesure encore 26 cm de large et le coeur finit en sabot.
                w *= Math.min(1.0f, (float) Math.sqrt(u / 0.07f));
                w *= 1.0f - contract * (0.26f * Mth.sin(u * Mth.PI) + 0.10f);
            } else {
                // OREILLETTES : plus etroites que la ceinture, et elles se REMPLISSENT
                // pendant que les ventricules se vident.
                float u = (v - AV) / (1.0f - AV);
                w = 0.546f * (1.0f - 0.34f * u * u) * (1.0f - 0.16f * u);
                w *= 1.0f + contract * 0.10f;
            }
            if (w <= 0.012f) continue;
            w *= s;

            // Les deux cavites. Le gauche est plus gros et recule ; le droit s'applique
            // sur son flanc avant-droit. C'est leur union qui donne la section en oeuf
            // dissymetrique, et le point ou leurs frontieres se croisent est le sillon.
            float c1 = -0.14f * w, r1 = atrium ? 0.74f * w : 0.80f * w;
            float c2 = 0.30f * w, r2 = atrium ? 0.60f * w : 0.64f * w;

            // 28 points de 0,050 et non 24 de 0,042 : a 24, l'espacement sur la
            // circonference valait 0,10 pour des cubes de 0,084 — des cannelures verticales
            // sur toute la hauteur, un chapiteau dorique plutot qu'un muscle.
            int pts = 28;
            for (int k = 0; k < pts; k++) {
                float ang = (k + 0.5f) / pts * 6.2831855f;
                float ux = Mth.cos(ang), uz = Mth.sin(ang) * 0.86f;   // legerement aplati
                float d1 = rayToCircle(c1, 0.0f, r1, ux, uz);
                float d2 = rayToCircle(c2, 0.0f, r2, ux, uz);
                float rr = Math.max(d1, d2);
                if (rr <= 0.004f) continue;
                // Le sillon : la ou les deux frontieres se valent, on rentre la surface.
                boolean groove = !atrium && Math.abs(d1 - d2) < 0.060f * w && d1 > 0.0f && d2 > 0.0f;
                if (groove) rr *= 0.93f;
                // L'ECHANCRURE : entre les deux oreillettes, la paroi plonge. C'est le creux
                // du haut — le seul trait qui distingue un coeur d'un oeuf en silhouette.
                if (atrium) {
                    float ua = (v - AV) / (1.0f - AV);
                    float notch = Math.max(0.0f, 1.0f - Math.abs(d1 - d2) / (0.42f * w));
                    rr *= 1.0f - 0.62f * ua * ua * notch;
                }

                // Le cote sombre valait 0,30 x v, donc VENOUS partout : le muscle sortait
                // en masse brune. Plancher a 0,55 et seuils abaisses — un myocarde est un
                // rouge VIF, c'est le sang veineux des oreillettes qui est sombre.
                // La face opposee a la lumiere tombait a 0,10 : un PAN NOIR large comme la
                // moitie de l'organe, qui le creusait en gouttiere. On garde un relief, mais
                // le creux le plus sombre reste du muscle rouge.
                float lit = 0.70f + 0.30f * Mth.cos(ang - 0.9f);
                lit = lit * 0.70f + 0.30f * v;
                if (atrium) lit *= 0.84f;                      // les oreillettes sont ternes
                // Le myocarde n'est pas lisse : un grain fige (meme cube, meme teinte)
                // casse l'aspect plastique sans scintiller d'une image a l'autre.
                float grain = hash(salt + 141, k * 31 + i * 7);
                lit *= 0.93f + 0.14f * grain;
                int rgb = groove ? VENOUS
                        : (lit > 0.84f ? SPRAY
                        : (lit > 0.66f ? FRESH
                        : (lit > 0.48f ? ARTERY
                        : (lit > 0.32f ? GORE : VENOUS))));
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + ux * rr, lOff + uz * rr, yy,
                        0.050f * s, step * 0.66f, 0.050f * s,
                        rgb, opaque(alpha * 0.95f));
            }
        }

        // LA CALOTTE DE L'APEX. Les tranches s'arretent sur une section plate de 16 cm :
        // vu de dessous, le coeur etait coupe net. Trois cubes la referment en pointe.
        {
            float v0 = 0.5f / slices;
            float yy = hy + (v0 - 0.46f) * H;
            float fOff = hf + lean * (v0 - 0.46f) * H;
            float lOff = hl + leanL * (v0 - 0.46f) * H;
            for (int k = 0; k < 3; k++) {
                float d = (k + 1) / 3.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff - lean * d * 0.10f * H, lOff - leanL * d * 0.10f * H,
                        yy - d * 0.085f * H,
                        (0.085f - 0.024f * k) * s, step * 0.55f, (0.085f - 0.024f * k) * s,
                        k == 2 ? VENOUS : GORE, opaque(alpha * 0.95f));
            }
        }

        // ---- LE SILLON AURICULO-VENTRICULAIRE : la ceinture du coeur ----
        {
            float v = AV;
            float yy = hy + (v - 0.46f) * H;
            float fOff = hf + lean * (v - 0.46f) * H;
            float lOff = hl + leanL * (v - 0.46f) * H;
            float w = 0.60f * s * 0.91f * (1.0f - contract * 0.12f);
            float c1 = -0.14f * w, r1 = 0.80f * w, c2 = 0.30f * w, r2 = 0.64f * w;
            for (int k = 0; k < 30; k++) {
                float ang = (k + 0.5f) / 30.0f * 6.2831855f;
                float ux = Mth.cos(ang), uz = Mth.sin(ang) * 0.86f;
                float rr = Math.max(rayToCircle(c1, 0.0f, r1, ux, uz),
                                    rayToCircle(c2, 0.0f, r2, ux, uz));
                if (rr <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + ux * rr * 0.99f, lOff + uz * rr * 0.99f, yy,
                        0.080f * s, 0.036f * s, 0.080f * s, VENOUS, opaque(alpha * 0.94f));
                // L'artere circonflexe, dans le sillon, cote gauche seulement.
                if (Mth.cos(ang) < -0.1f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            fOff + ux * rr * 1.0f, lOff + uz * rr * 1.0f, yy + 0.012f * s,
                            0.046f * s, 0.024f * s, 0.046f * s,
                            contract > 0.05f ? SPRAY : ARTERY, opaque(alpha * 0.9f));
                }
            }
        }

        // ---- LES AURICULES : deux rabats plisses qui EPOUSENT la base ----
        // Elles partaient droit sur le cote comme deux bras : une auricule est une poche
        // couchee CONTRE l'oreillette, qui part de l'avant et s'enroule vers l'arriere.
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1.0f : -1.0f;
            float v = AV + 0.10f;
            float yy = hy + (v - 0.46f) * H;
            float fOff = hf + lean * (v - 0.46f) * H;
            float lOff = hl + leanL * (v - 0.46f) * H;
            for (int k = 0; k < 8; k++) {
                float u = (k + 0.5f) / 8.0f;
                float ang = sg * (0.42f + 1.30f * u);           // de l'avant vers l'arriere
                float rad = (0.50f + 0.14f * Mth.sin(u * Mth.PI)) * s;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + Mth.cos(ang) * rad * 0.92f,
                        lOff + Mth.sin(ang) * rad * 0.80f,
                        yy + 0.07f * s - u * u * 0.16f * s,
                        0.058f * s, 0.052f * s, 0.058f * s,
                        k % 2 == 0 ? GORE : ARTERY, opaque(alpha * 0.92f));
            }
        }

        // ---- LES GROS VAISSEAUX ----
        final float baseY = hy + (1.0f - 0.46f) * H;
        final float baseF = hf + lean * (1.0f - 0.46f) * H;
        final float baseL = hl + leanL * (1.0f - 0.46f) * H;

        // L'AORTE. Sa crosse est couchee dans le plan de la LIAISON (l'axe f) et non sur
        // le cote : de biais, la camera la voyait par la tranche et l'arc se refermait en
        // patate. Une crosse ne se lit que de profil, et c'est elle qui dit « aorte ».
        final int aortaSegs = 20;
        for (int i = 0; i < aortaSegs; i++) {
            float u = (i + 0.5f) / aortaSegs;
            float pf, py;
            if (u < 0.30f) {                                    // ascendante
                float k = u / 0.30f;
                pf = 0.12f - 0.02f * k;
                py = -0.08f + k * 0.34f;
            } else if (u < 0.76f) {                             // la crosse
                float k = (u - 0.30f) / 0.46f;
                float ang = k * Mth.PI;
                pf = 0.10f - (1.0f - Mth.cos(ang)) * 0.33f;
                py = 0.26f + Mth.sin(ang) * 0.24f;
            } else {                                            // descendante
                float k = (u - 0.76f) / 0.24f;
                pf = -0.56f;
                py = 0.26f - k * 0.16f;
            }
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    baseF + pf * s, baseL - 0.06f * s, baseY + py * s,
                    (0.086f - 0.030f * u) * s, (0.086f - 0.030f * u) * s,
                    (0.086f - 0.030f * u) * s,
                    u < 0.45f ? FRESH : ARTERY, opaque(alpha * 0.96f));
        }
        // Deux troncs sur le dos de la crosse. Trois rangees de trois faisaient un crenelage
        // de chateau fort ; deux moignons suffisent a dire « branches ».
        for (int b = 0; b < 2; b++) {
            float ff = -0.11f - b * 0.17f;
            for (int k = 0; k < 2; k++) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        baseF + (ff - (b == 0 ? 0.06f : -0.02f) * k) * s, baseL - 0.06f * s,
                        baseY + (0.50f + k * 0.075f) * s,
                        0.044f * s, 0.048f * s, 0.044f * s, FRESH, opaque(alpha * 0.92f));
            }
        }

        // LE TRONC PULMONAIRE : devant l'aorte, plus sombre, il se divise en Y.
        for (int i = 0; i < 11; i++) {
            float u = (i + 0.5f) / 11.0f;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    baseF + (0.02f + 0.06f * u) * s, baseL + (0.20f - 0.03f * u) * s,
                    baseY + (-0.10f + u * 0.42f) * s,
                    0.078f * s, 0.078f * s, 0.078f * s, GORE, opaque(alpha * 0.95f));
        }
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1.0f : -1.0f;
            for (int k = 0; k < 3; k++) {
                float u = (k + 0.5f) / 3.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        baseF + (0.08f + sg * u * 0.30f) * s, baseL + (0.17f - 0.02f * u) * s,
                        baseY + (0.30f + 0.05f * u - u * u * 0.07f) * s,
                        0.060f * s, 0.060f * s, 0.060f * s, VENOUS, opaque(alpha * 0.92f));
            }
        }

        // LES VEINES CAVES, en arriere et a droite, sombres : elles RAMENENT le sang.
        for (int i = 0; i < 8; i++) {
            float u = (i + 0.5f) / 8.0f;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    baseF + (0.32f + 0.03f * u) * s, baseL - 0.20f * s,
                    baseY + (-0.10f + u * 0.34f) * s,
                    0.068f * s, 0.068f * s, 0.068f * s, GORE, opaque(alpha * 0.92f));
        }
        for (int i = 0; i < 5; i++) {
            float u = (i + 0.5f) / 5.0f;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    baseF + 0.34f * s, baseL - 0.17f * s, baseY - (0.10f + u * 0.24f) * s,
                    0.064f * s, 0.064f * s, 0.064f * s, VENOUS, opaque(alpha * 0.90f));
        }
        // Les veines pulmonaires : quatre moignons a l'arriere, discrets.
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1.0f : -1.0f;
            for (int k = 0; k < 2; k++) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        baseF + sg * 0.19f * s, baseL - (0.26f + k * 0.09f) * s,
                        baseY - 0.11f * s,
                        0.054f * s, 0.054f * s, 0.054f * s, CLOT, opaque(alpha * 0.88f));
            }
        }

        // ---- L'ARTERE INTERVENTRICULAIRE ANTERIEURE ----
        // Elle descend DANS le sillon, de la base a l'apex. C'est le detail qui fait lire
        // « organe » : une masse rouge sans coronaire reste une masse.
        {
            int segs = 16;
            for (int i = 0; i < segs; i++) {
                float v = AV - (i + 0.5f) / segs * (AV - 0.06f);
                float u = v / AV;
                float w = 0.60f * s * Mth.sin(1.5707964f * (float) Math.pow(u, 0.62))
                        * (0.62f + 0.38f * (float) Math.sqrt(u))
                        * (1.0f - 0.09f * u * u * u * u)
                        * Math.min(1.0f, (float) Math.sqrt(u / 0.10f))
                        * (1.0f - contract * (0.26f * Mth.sin(u * Mth.PI) + 0.10f));
                float yy = hy + (v - 0.46f) * H;
                float fOff = hf + lean * (v - 0.46f) * H;
                float lOff = hl + leanL * (v - 0.46f) * H;
                // Le sillon est a l'avant, entre les deux cavites : angle proche de +0,55 rad,
                // qui glisse vers l'apex ou les deux ventricules se rejoignent.
                float ang = 0.55f + 0.30f * (1.0f - u) + Mth.sin(u * 4.0f) * 0.10f;
                float rr = w * 0.96f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + Mth.cos(ang) * rr, lOff + Mth.sin(ang) * rr * 0.86f, yy,
                        0.038f * s, step * 0.58f, 0.038f * s,
                        contract > 0.05f ? PLASMA : SPRAY, opaque(alpha));
                // Une diagonale sur deux : les branches de la coronaire.
                if (i % 5 == 2) {
                    float ang2 = ang - 0.55f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            fOff + Mth.cos(ang2) * rr, lOff + Mth.sin(ang2) * rr * 0.86f,
                            yy - step * 0.5f,
                            0.032f * s, step * 0.5f, 0.032f * s, SPRAY, opaque(alpha * 0.85f));
                }
            }
        }
    }

    /**
     * Distance de l'origine à la frontière d'un cercle, le long d'une direction unitaire.
     * Rend 0 si le rayon manque le cercle. C'est la brique de l'union des deux ventricules.
     */
    private static float rayToCircle(float ccx, float ccz, float r, float ux, float uz) {
        float b = ccx * ux + ccz * uz;
        float c = ccx * ccx + ccz * ccz - r * r;
        float disc = b * b - c;
        if (disc < 0.0f) return 0.0f;
        float d = b + Mth.sqrt(disc);
        return d > 0.0f ? d : 0.0f;
    }

    // =================================================================================
    //  V. CARNAGE BERSERK — 180 ticks
    // =================================================================================

    /**
     * Chronologie (180 ticks) :
     * <ul>
     *   <li>0,00–0,14 <b>la montée</b> : trois pulsations au sol, de plus en plus rapprochées ;</li>
     *   <li>0,14–0,62 <b>la salve</b> : onze taillades, chacune dans son propre plan, à cadence
     *       ACCÉLÉRÉE — l'écart entre deux coups se réduit de moitié du début à la fin ;</li>
     *   <li>0,46–0,90 <b>l'égouttement</b> : chaque arc rémanent lâche ses gouttes ;</li>
     *   <li>0,62–0,72 <b>l'apogée</b> : une onde de sang balaie le sol ;</li>
     *   <li>0,30–0,95 <b>la brume</b> : une nappe basse qui monte lentement ;</li>
     *   <li>0,90–1,00 <b>la retombée</b> : tout rétrécit.</li>
     * </ul>
     *
     * <p>C'est la signature d'AIRE du sang : elle ne vise rien, elle occupe. Les onze plans
     * sont tirés d'un hachage stable, donc deux carnages ne se ressemblent jamais tout en
     * restant reproductibles d'une image à l'autre.</p>
     */
    private static void renderCarnage(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float wind = clamp(t / 0.14f);
        final float apex = clamp((t - 0.62f) / 0.10f);
        final float settle = smooth((t - 0.90f) / 0.10f);

        // ---- 1. LA MONTÉE ----
        // Trois pulsations au sol, dont l'écart se resserre. C'est l'annonce : sans elle, la
        // première taillade arrive sans que l'œil sache où regarder.
        if (t < 0.20f) {
            float[] when = {0.015f, 0.065f, 0.105f};
            for (int k = 0; k < 3; k++) {
                float age = (t - when[k]) / 0.055f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float rr = 0.4f + (1.1f + 0.5f * k) * smooth(age);
                float thin = 1.0f - age;
                int pts = 20 + k * 4;
                for (int i = 0; i < pts; i++) {
                    float ang = (i + 0.5f) / pts * 6.2831855f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.014f,
                            0.062f * thin, SLAB_H, 0.062f * thin,
                            k == 2 ? FRESH : ARTERY, opaque(a * 0.85f * thin));
                }
            }
            // Fissures sous les pieds, qui s'allongent avec la montée.
            for (int i = 0; i < 9; i++) {
                float h1 = hash(salt + 151, i * 23);
                float ang = h1 * 6.2831855f;
                float len = 1.5f * wind * (0.5f + h1);
                int segs = 6;
                for (int k = 0; k < segs; k++) {
                    float u = (k + 0.5f) / segs;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * len * u, Mth.sin(ang) * len * u, GROUND_Y + 0.010f,
                            0.034f * (1.0f - u * 0.6f), SLAB_H, 0.034f * (1.0f - u * 0.6f),
                            VENOUS, opaque(a * 0.75f));
                }
            }
        }

        // ---- 2. LA SALVE ----
        final int strikes = 11;
        for (int sIdx = 0; sIdx < strikes; sIdx++) {
            // Cadence accélérée : l'écart entre deux coups passe de 0,062 à 0,031.
            float u = sIdx / (float) (strikes - 1);
            float at = 0.14f + 0.48f * (u - 0.22f * u * (1.0f - u) * 2.0f);
            // Duree de vie 0,30 et non 0,22 : a 0,22, seules quatre taillades coexistaient
            // au plus fort de la salve. Une FRENESIE qui ne montre que quatre arcs n'est
            // qu'une suite de coups — c'est leur CHEVAUCHEMENT qui fait la frenesie. A 0,30,
            // huit arcs se superposent au pic.
            float age = (t - at) / 0.30f;
            if (age <= 0.0f) continue;

            float h1 = hash(salt + 161, sIdx * 29);
            float h2 = hash(salt + 162, sIdx * 31);
            float h3 = hash(salt + 163, sIdx * 37);
            float planeYaw = h1 * 6.2831855f;
            float planeTilt = (h2 - 0.5f) * 1.5f;
            float radius = 1.35f + 1.05f * h3;
            float centerY = 0.55f + 1.35f * h2;
            float sweepFrom = -1.15f - 0.5f * h1;
            float sweepTo = 1.15f + 0.5f * h3;

            // L'arc balaie pendant les 28 premiers pour cent de sa vie, puis reste rémanent.
            float sweep = smooth(clamp(age / 0.28f));
            float remnant = clamp((age - 0.28f) / 0.72f);
            if (remnant >= 1.0f) continue;

            int segs = 20;
            for (int i = 0; i < segs; i++) {
                float v = (i + 0.5f) / segs;
                if (v > sweep) continue;
                float ang = sweepFrom + (sweepTo - sweepFrom) * v;
                // Point sur l'arc, dans un plan incliné autour du centre.
                float px = Mth.cos(ang) * radius;
                float py = Mth.sin(ang) * radius;
                float f = px * Mth.cos(planeYaw) - py * Mth.sin(planeYaw) * Mth.sin(planeTilt);
                float lat = px * Mth.sin(planeYaw) + py * Mth.cos(planeYaw) * Mth.sin(planeTilt);
                float yy = centerY + py * Mth.cos(planeTilt);
                if (yy < GROUND_Y + 0.03f) continue;

                // Épaisseur : fine aux deux bouts, large au milieu — une taillade n'est pas
                // un ruban d'épaisseur constante, c'est le passage d'un tranchant.
                float body = Mth.sin(v * Mth.PI);
                // On efface par la GÉOMÉTRIE — mais pas linéairement. Un rétrécissement
                // proportionnel réduisait l'arc à un fil dès le tiers de sa vie : la mesure
                // disait « sept taillades vivantes », l'image n'en montrait que trois.
                // La racine garde l'arc épais pendant la moitié de sa vie puis l'escamote.
                float fadeGeom = Mth.sqrt(1.0f - remnant);
                float hw = (0.028f + 0.085f * body) * fadeGeom;
                if (hw <= 0.004f) continue;
                boolean head = v > sweep - 0.09f && remnant <= 0.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lat, yy,
                        hw, hw * 1.5f, hw, head ? SPRAY : (body > 0.55f ? ARTERY : GORE),
                        opaque(a * 0.95f));
                // Le fil clair sur le bord d'attaque de l'arc.
                if (body > 0.30f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lat, yy + hw * 1.6f,
                            hw * 0.55f, 0.012f * fadeGeom, hw * 0.55f, FRESH, opaque(a * 0.9f));
                }

                // ---- 3. L'ÉGOUTTEMENT ----
                // Un arc rémanent lâche des gouttes, sinon il flotte comme un néon.
                if (remnant > 0.12f && (i % 4) == 0) {
                    float dAge = (remnant - 0.12f) / 0.88f;
                    float dy = yy - GRAVITY * 0.09f * dAge * dAge;
                    if (dy > GROUND_Y + 0.04f) {
                        drop(vc, mat, tmp, cx, cy, cz, dx, dz, f, lat, dy, 0.024f, a);
                    }
                }
            }
        }

        // ---- 4. L'APOGÉE ----
        if (apex > 0.001f && apex < 1.0f) {
            float rr = 0.5f + 4.4f * smooth(apex);
            float thin = 1.0f - apex * 0.8f;
            int pts = 46;
            for (int i = 0; i < pts; i++) {
                float ang = (i + 0.5f) / pts * 6.2831855f;
                float wob = 1.0f + 0.11f * Mth.sin(ang * 5.0f + salt * 0.013f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr * wob, Mth.sin(ang) * rr * wob, GROUND_Y + 0.016f,
                        0.090f * thin, SLAB_H, 0.090f * thin, FRESH, opaque(a));
                // Le bourrelet : la crête de l'onde, un cran en arrière.
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * (rr - 0.22f) * wob, Mth.sin(ang) * (rr - 0.22f) * wob,
                        GROUND_Y + 0.05f * thin,
                        0.055f * thin, 0.038f * thin, 0.055f * thin, ARTERY, opaque(a * 0.9f));
            }
        }

        // ---- 5. LA BRUME ----
        // Nappe basse, lente, peu dense. Elle ne doit jamais masquer les taillades : c'est le
        // sol de la scène, pas un personnage.
        {
            float mist = clamp((t - 0.30f) / 0.30f) * (1.0f - settle);
            if (mist > 0.01f) {
                for (int i = 0; i < 34; i++) {
                    float h1 = hash(salt + 171, i * 19);
                    float h2 = hash(salt + 172, i * 23);
                    float h3 = hash(salt + 173, i * 29);
                    float ang = h1 * 6.2831855f;
                    float rr = (0.5f + 3.6f * h2) * Math.min(1.0f, mist * 1.4f);
                    float rise = (t - 0.30f) * (0.35f + 0.45f * h3);
                    float yy = GROUND_Y + 0.10f + rise;
                    float sz = (0.055f + 0.055f * h3) * mist;
                    if (sz <= 0.006f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                            sz, sz * 0.62f, sz, i % 4 == 0 ? GORE : VENOUS,
                            opaque(a * 0.70f));
                }
            }
        }

        // ---- 6. LE SOL TREMPÉ ----
        {
            float soak = smooth(clamp((t - 0.18f) / 0.50f)) * (1.0f - settle * 0.8f);
            if (soak > 0.02f) {
                pool(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, 2.35f * soak, a, salt + 181);
                // Éclaboussures lointaines : des taches isolées, hors de la flaque centrale.
                for (int i = 0; i < 13; i++) {
                    float h1 = hash(salt + 191, i * 23);
                    float h2 = hash(salt + 192, i * 29);
                    float ang = h1 * 6.2831855f;
                    float rr = 2.4f + 2.0f * h2;
                    float r2 = (0.16f + 0.22f * h1) * soak;
                    if (r2 <= 0.02f) continue;
                    pool(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, r2, a, salt + 201 + i);
                }
            }
        }
    }

    // =================================================================================
    //  PRIMITIVES DE LIQUIDE
    // =================================================================================

    /**
     * Une GOUTTE. Jamais un cube seul : un corps sombre, un méniscus clair au sommet et une
     * traînée fine au-dessus. C'est la traînée qui donne la vitesse — une goutte sans elle
     * ressemble à un grain en lévitation.
     */
    private static void drop(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f, float lateral, float y, float r, int alpha) {
        if (r <= 0.004f) return;
        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lateral, y, r * 0.86f, r, r * 0.86f,
                GORE, opaque(alpha));
        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lateral, y + r * 0.72f,
                r * 0.44f, r * 0.34f, r * 0.44f, PLASMA, opaque(alpha * 0.95f));
        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lateral, y + r * 1.55f,
                r * 0.26f, r * 0.62f, r * 0.26f, ARTERY, opaque(alpha * 0.70f));
    }

    /**
     * Une ÉCLABOUSSURE : la couronne de gouttelettes qui part d'un impact, et le disque
     * sombre qu'elle laisse. {@code u} va de 0 (contact) à 1 (retombée complète).
     */
    private static void splash(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float cx, float cy, float cz, float dx, float dz,
                               float f, float lateral, float u, float power, int alpha, int seed) {
        if (u <= 0.0f || u >= 1.0f) return;
        int petals = 9;
        for (int i = 0; i < petals; i++) {
            float h1 = hash(seed, i * 17);
            float h2 = hash(seed + 1, i * 19);
            float ang = (i + h1 * 0.6f) / petals * 6.2831855f;
            float d = power * (0.25f + 0.70f * h2) * smooth(u);
            float yy = GROUND_Y + 0.02f + power * (0.42f * Mth.sin(u * Mth.PI)) * (0.5f + h1);
            float sz = 0.022f * (1.0f - u * 0.55f) * (0.7f + 0.6f * h2);
            if (sz <= 0.004f) continue;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f + Mth.cos(ang) * d, lateral + Mth.sin(ang) * d, yy,
                    sz, sz, sz, i % 3 == 0 ? FRESH : ARTERY, opaque(alpha * 0.9f));
        }
        // La tache laissée par l'impact : elle grandit puis reste.
        pool(vc, mat, tmp, cx, cy, cz, dx, dz, f, lateral, power * 0.30f * smooth(u), alpha, seed + 7);
    }

    /**
     * Une FLAQUE. Un disque irrégulier de dalles, avec un bord clair : sans méniscus, une
     * flaque est un autocollant. Le contour est bruité par deux harmoniques, donc jamais rond.
     */
    private static void pool(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f, float lateral, float radius, int alpha, int seed) {
        if (radius <= 0.03f) return;
        float n1 = hash(seed, 3) * 6.2831855f;
        float n2 = hash(seed + 1, 5) * 6.2831855f;
        int rings = Math.max(2, (int) (radius / 0.16f));
        for (int r = 0; r < rings; r++) {
            float rv = (r + 0.5f) / rings;
            int pts = Math.max(8, (int) (10 + 26 * rv));
            for (int i = 0; i < pts; i++) {
                float ang = (i + 0.5f) / pts * 6.2831855f;
                float wob = 1.0f + 0.18f * Mth.sin(ang * 3.0f + n1) + 0.11f * Mth.sin(ang * 5.0f + n2);
                float rr = radius * rv * wob;
                boolean rim = r == rings - 1;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f + Mth.cos(ang) * rr, lateral + Mth.sin(ang) * rr,
                        GROUND_Y + (rim ? 0.016f : 0.008f),
                        radius / rings * 0.62f, SLAB_H, radius / rings * 0.62f,
                        rim ? ARTERY : (rv > 0.55f ? VENOUS : CLOT), opaque(alpha * 0.92f));
            }
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
     * Seuil d'opacité. Le tamis {@code pixelDensity} décime les cubes sous 200 d'alpha : une
     * masse de sang qu'on ferait fondre en baissant l'alpha se trouerait au lieu de s'estomper.
     * Tout passe donc par ici, et l'effacement se joue sur la géométrie.
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
