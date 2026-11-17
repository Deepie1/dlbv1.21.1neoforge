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
     *   <li>0,00–0,09 <b>les crochets</b> : quatre hameçons d'os partent du porteur et mordent ;</li>
     *   <li>0,09–0,20 <b>le conduit</b> : une double hélice se tend de la victime au porteur ;</li>
     *   <li>0,20–0,80 <b>la pompe</b> : six bols remontent le conduit en le faisant GONFLER à
     *       leur passage, et le niveau de la victime descend ;</li>
     *   <li>0,80–0,90 <b>la vidange</b> : le conduit s'amincit et se détend ;</li>
     *   <li>0,90–1,00 <b>la rupture</b> : il casse au milieu, les deux moitiés fouettent.</li>
     * </ul>
     *
     * <p>Le conduit est dessiné de {@code f = 0} (la victime) à {@code f = −REACH} (le porteur) :
     * c'est la seule signature du sang dont la lecture dépend de savoir qui prend à qui.</p>
     */
    private static void renderSiphon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt,
                                     float linkLen, float linkEndY, boolean linkBroken) {
        final int a = Math.round(alpha * smooth(t / 0.03f));
        if (a <= 4) return;

        final float bite = smooth(t / 0.09f);
        final float build = smooth((t - 0.09f) / 0.11f);
        final float drain = clamp((t - 0.20f) / 0.60f);
        final float slack = clamp((t - 0.80f) / 0.10f);
        // Rupture : en fin d'animation, OU des que le porteur est sorti de la laisse. Le
        // second cas n'est pas un decor — le serveur arrete la pompe au meme moment.
        final float snap = Math.max(clamp((t - 0.90f) / 0.10f), linkBroken ? 1.0f : 0.0f);

        final float mouthY = 0.95f;                                // point de ponction
        final float handY = linkEndY;                              // main du porteur, EN VRAI

        // ---- 1. LES CROCHETS ----
        // Quatre hameçons d'os plantés en couronne autour du point de ponction. Ils donnent
        // au conduit une ACCROCHE : un tuyau qui touche une peau sans rien y planter flotte.
        if (bite > 0.001f) {
            for (int i = 0; i < 4; i++) {
                float ang = i * HALF_PI + 0.42f;
                float rr = 0.34f;
                float plunge = smooth(bite);
                float lat = Mth.cos(ang) * rr;
                float yy = mouthY + Mth.sin(ang) * rr * 0.75f;
                float f = 0.14f - 0.16f * plunge;
                // la hampe
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f + 0.12f, lat * 1.22f, yy + Mth.sin(ang) * 0.06f,
                        0.105f, 0.030f, 0.030f, BONE, opaque(a * 0.95f));
                // la pointe recourbée
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f + 0.02f, lat * 1.02f, yy,
                        0.040f, 0.038f, 0.038f, BONE, opaque(a));
                // l'anneau de serrage : il relie les quatre crochets a la bouche du conduit,
                // sans quoi ils flottent autour de la cible comme quatre eclats detaches.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f + 0.20f, lat * 0.62f, yy * 0.5f + mouthY * 0.5f,
                        0.030f, 0.030f, 0.030f, IRON_DK, opaque(a * 0.85f));
                // le sang à la morsure
                if (plunge > 0.6f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f + 0.05f, lat * 1.06f, yy - 0.05f,
                            0.016f, 0.030f, 0.016f, ARTERY, opaque(a));
                }
            }
        }

        // ---- 2. LE CONDUIT ----
        // Double hélice. Un tuyau droit ne dit pas dans quel sens il coule ; deux brins
        // enroulés, si — et ils donnent au gonflement des bols quelque chose à déformer.
        // La longueur du conduit est la distance REELLE au porteur, pas la constante REACH.
        // Le nombre de segments suit : 62 pour sept blocs, soit un segment tous les 11 cm,
        // qu'il y en ait trois ou douze.
        final float reach = linkLen;
        final int segs = Math.max(26, Math.round(62.0f * reach / REACH));
        float frontier = build;                                     // avancée de la construction
        for (int i = 0; i < segs; i++) {
            float u = (i + 0.5f) / segs;                            // 0 = victime, 1 = porteur
            if (u > frontier) continue;
            // Rupture : à partir de 0,90 le conduit s'ouvre au milieu et les deux moitiés
            // se rétractent vers leurs extrémités.
            if (snap > 0.0f) {
                float gap = 0.5f * snap;
                if (u > 0.5f - gap && u < 0.5f + gap) continue;
            }
            float f = -u * reach;
            // Le ventre est proportionnel a la portee : un conduit de trois blocs qui pend
            // de 55 cm comme un conduit de sept racle le sol.
            float sag = (0.55f - 0.30f * drain) * (reach / REACH);
            float yy = mouthY + (handY - mouthY) * u
                    - Mth.sin(u * Mth.PI) * sag;                       // le conduit PEND
            // Gonflement : somme des six bols qui passent.
            float bulge = 0.0f;
            for (int b = 0; b < 6; b++) {
                float launch = 0.20f + b * 0.098f;
                float travel = (t - launch) / 0.30f;
                if (travel <= 0.0f || travel >= 1.0f) continue;
                float pos = smooth(travel);
                float d = Math.abs(u - pos);
                if (d < 0.085f) bulge += (1.0f - d / 0.085f) * (1.0f - 0.45f * b / 6.0f);
            }
            float thin = 1.0f - 0.42f * slack;
            // Rayon d'helice 0,135 et non 0,085 : a 0,085 pour des brins epais de 0,052,
            // les deux brins se CHEVAUCHAIENT et le conduit sortait en tige unique — on
            // perdait l'enroulement, donc le sens d'ecoulement, qui est tout le sujet.
            float r = (0.135f + 0.105f * bulge) * thin;
            // Les deux brins. 26 radians sur la longueur, soit un peu plus de quatre tours :
            // a 15 (2,4 tours sur sept blocs) l'helice etait trop lache pour se lire.
            for (int s = 0; s < 2; s++) {
                // 26 radians pour sept blocs : le PAS de l'helice est une propriete du
                // tuyau, pas de la distance. Il se recalcule donc sur la portee reelle.
                float phase = u * (26.0f * reach / REACH) + s * Mth.PI + t * 2.4f;
                float ox = Mth.cos(phase) * r;
                float oz = Mth.sin(phase) * r;
                int rgb = bulge > 0.35f ? FRESH : (s == 0 ? GORE : VENOUS);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f + ox * 0.6f, oz, yy + ox * 0.45f,
                        reach / segs * 0.60f, 0.024f + 0.020f * bulge, 0.024f + 0.020f * bulge,
                        rgb, opaque(a * 0.95f));
            }
            // L'âme : le filet sombre au centre, qui donne de l'épaisseur à l'hélice.
            box(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.0f, yy,
                    reach / segs * 0.62f, 0.019f + 0.030f * bulge, 0.019f + 0.030f * bulge,
                    CLOT, opaque(a * 0.85f));
            // Le méniscus du bol : un liseré clair juste devant lui.
            if (bulge > 0.55f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.0f, yy + 0.034f + 0.020f * bulge,
                        reach / segs * 0.5f, 0.012f, 0.040f, SPRAY, opaque(a));
            }
        }

        // ---- 3. LA VICTIME SE VIDE ----
        // Une colonne à l'intérieur de la silhouette, dont le niveau descend. C'est la seule
        // façon de montrer un volume qu'on retire sans dessiner l'intérieur d'un corps.
        if (drain > 0.001f) {
            float full = 1.55f;
            float level = full * (1.0f - drain * 0.88f);
            // 14 points et non 8 : a huit, l'espacement sur un rayon de 0,30 valait 0,235
            // pour des cubes de 0,06 — une CAGE de fil de fer flottant a cote de la cible,
            // pas un corps qui se vide.
            int rings = 11;
            for (int i = 0; i < rings; i++) {
                float v = (i + 0.5f) / rings;
                float yy = GROUND_Y + 0.35f + v * level;
                float rr = 0.30f * (1.0f - 0.25f * v);
                int pts = 14;
                for (int k = 0; k < pts; k++) {
                    float ang = (k + 0.5f) / pts * 6.2831855f + v * 1.4f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                            0.046f, 0.030f, 0.046f,
                            v > 0.80f ? ARTERY : (v > 0.40f ? GORE : VENOUS), opaque(a * 0.88f));
                }
            }
            // LE MENISQUE. C'est lui qui transforme une colonne en volume de liquide : un
            // disque clair pose exactement sur le niveau restant, qui DESCEND a vue.
            float topY = GROUND_Y + 0.35f + level;
            float topR = 0.30f * 0.75f;
            for (int k = 0; k < 16; k++) {
                float ang = (k + 0.5f) / 16.0f * 6.2831855f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * topR, Mth.sin(ang) * topR, topY + 0.02f,
                        0.050f, 0.016f, 0.050f, SPRAY, opaque(a));
            }
        }

        // ---- 4. LE FOUET DE RUPTURE ----
        if (snap > 0.001f) {
            for (int s = 0; s < 2; s++) {
                float end = s == 0 ? 0.5f - 0.5f * snap : 0.5f + 0.5f * snap;
                float f = -end * reach;
                float yy = mouthY + (handY - mouthY) * end - Mth.sin(end * Mth.PI) * 0.25f;
                float lash = Mth.sin(snap * Mth.PI * 2.0f) * 0.42f * (1.0f - snap);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lash, yy + Math.abs(lash) * 0.5f,
                        0.070f, 0.044f, 0.044f, FRESH, opaque(a));
                // Les gouttes projetées par le fouet.
                for (int i = 0; i < 5; i++) {
                    float h1 = hash(salt + 111 + s * 7, i * 19);
                    float age = snap;
                    float d = (0.4f + 1.6f * h1) * age;
                    float dy = 0.3f * age - GRAVITY * 0.05f * age * age;
                    drop(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + (h1 - 0.5f) * 0.6f, lash + d * (s == 0 ? 1.0f : -1.0f),
                            yy + dy, 0.024f, a);
                }
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
        final float scale = 0.92f * gather * (1.0f - fade * 0.55f);

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
     * Le CŒUR. Une vingtaine de tranches horizontales dont la section est l'<b>union de deux
     * disques</b> de rayons inégaux — le ventricule gauche, plus gros, et le droit.
     *
     * <p>Chaque tranche est tracée comme un contour : pour chaque angle on calcule la distance
     * à la frontière de l'union, c'est-à-dire la plus grande des deux intersections de rayon.
     * Dessiner deux cylindres séparés aurait donné un sablier vu de face ; c'est le contour
     * commun qui fait l'organe.</p>
     *
     * <p>Par-dessus : la crosse aortique, le tronc pulmonaire, la veine cave, et surtout les
     * <b>artères coronaires</b> — deux liserés clairs qui descendent en serpentant sur la
     * surface. Ce sont elles qui font lire « organe » plutôt que « masse rouge ».</p>
     */
    private static void drawHeart(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float hf, float hl, float hy,
                                  float s, float contract, float t, int alpha, int salt) {
        final int slices = 17;
        // Le cœur se couche en avant ET sur le côté. L'inclinaison latérale n'est pas un
        // ornement : l'apex d'un cœur ne pointe pas droit en bas, il part vers la gauche.
        // Sans elle, la silhouette est un bulbe symétrique — une ampoule, pas un organe.
        final float lean = 0.22f;
        final float leanL = 0.30f;

        for (int i = 0; i < slices; i++) {
            float v = (i + 0.5f) / slices;                 // 0 = pointe (bas), 1 = base (haut)
            float yy = hy + (v - 0.42f) * 1.55f * s;
            float fOff = hf + lean * (v - 0.42f) * 1.55f * s;
            float lOff = hl + leanL * (v - 0.42f) * 1.55f * s;

            // Profil : pointu en bas, large aux deux tiers, resserré à la base.
            float w = Mth.sin(Mth.sqrt(v) * 2.55f) * (0.52f - 0.10f * v);
            if (w <= 0.012f) continue;
            // La systole écrase surtout le milieu : un organe ne se contracte pas en bloc.
            w *= s * (1.0f - contract * (0.55f + 0.85f * Mth.sin(v * Mth.PI)));

            // Les deux ventricules.
            float c1 = -0.20f * w, r1 = 0.70f * w;         // gauche, le gros
            float c2 = 0.26f * w, r2 = 0.54f * w;          // droit

            int pts = 20;
            for (int k = 0; k < pts; k++) {
                float ang = (k + 0.5f) / pts * 6.2831855f;
                float ux = Mth.cos(ang), uz = Mth.sin(ang);
                float rr = Math.max(rayToCircle(c1, 0.0f, r1, ux, uz),
                                    rayToCircle(c2, 0.0f, r2, ux, uz));
                if (rr <= 0.004f) continue;
                // LA VALEUR FAIT LE VOLUME. Trois teintes séparées de deux crans donnaient
                // une masse uniforme une fois l'ombrage des faces appliqué : le cœur sortait
                // en bouillie rouge. Cinq crans, et surtout un second terme — la base est
                // plus claire que la pointe — parce qu'un éclairage purement horizontal
                // laisse toutes les tranches identiques et aplatit l'organe en disque.
                float lit = 0.5f + 0.5f * Mth.cos(ang - 0.9f);
                lit = lit * 0.72f + 0.28f * v;
                int rgb = lit > 0.78f ? SPRAY
                        : (lit > 0.60f ? FRESH
                        : (lit > 0.43f ? ARTERY
                        : (lit > 0.26f ? GORE : VENOUS)));
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + ux * rr, lOff + uz * rr, yy,
                        0.034f * s, 1.55f * s / slices * 0.56f, 0.034f * s,
                        rgb, opaque(alpha * 0.95f));
            }

            // Le sillon interventriculaire : le creux sombre entre les deux ventricules.
            if (v > 0.12f && v < 0.88f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, fOff + 0.03f * w, lOff + 0.03f * w, yy,
                        0.030f * s, 1.55f * s / slices * 0.5f, 0.022f * s,
                        CLOT, opaque(alpha * 0.9f));
            }
        }

        // ---- Les gros vaisseaux ----
        float baseY = hy + 0.58f * 1.55f * s * 0.5f + 0.42f * s;
        // Crosse aortique : un quart de tore qui part vers le haut puis se couche.
        for (int i = 0; i < 11; i++) {
            float u = (i + 0.5f) / 11.0f;
            float ang = u * HALF_PI * 1.15f;
            float rr = 0.42f * s;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    hf + lean * 0.3f - Mth.sin(ang) * rr * 0.4f,
                    hl + 0.08f * s + Mth.sin(ang) * rr,
                    baseY + Mth.cos(ang) * rr * 0.75f,
                    0.052f * s, 0.052f * s, 0.052f * s,
                    i < 3 ? ARTERY : GORE, opaque(alpha * 0.95f));
        }
        // Tronc pulmonaire, plus court et plus sombre.
        for (int i = 0; i < 7; i++) {
            float u = (i + 0.5f) / 7.0f;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    hf + 0.16f * s, hl + 0.20f * s,
                    baseY + u * 0.52f * s,
                    0.046f * s, 0.046f * s, 0.046f * s, VENOUS, opaque(alpha * 0.92f));
        }
        // Veine cave : elle arrive par-dessous, à l'arrière.
        for (int i = 0; i < 6; i++) {
            float u = (i + 0.5f) / 6.0f;
            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                    hf - 0.28f * s, hl - 0.26f * s,
                    baseY - 0.10f * s + u * 0.40f * s,
                    0.040f * s, 0.040f * s, 0.040f * s, CLOT, opaque(alpha * 0.9f));
        }

        // ---- Les coronaires ----
        // Deux liserés clairs qui serpentent du haut vers la pointe. Elles pulsent avec la
        // systole : c'est le seul endroit où le cœur s'éclaircit, donc le seul qui batte à l'œil.
        for (int c = 0; c < 2; c++) {
            float side = c == 0 ? 1.0f : -1.0f;
            int segs = 15;
            for (int i = 0; i < segs; i++) {
                float v = 0.88f - (i + 0.5f) / segs * 0.80f;
                float w = Mth.sin(Mth.sqrt(v) * 2.55f) * (0.52f - 0.10f * v) * s;
                float ang = side * (0.75f + v * 1.25f) + Mth.sin(v * 5.0f) * 0.22f;
                float yy = hy + (v - 0.42f) * 1.55f * s;
                float fOff = hf + lean * (v - 0.42f) * 1.55f * s;
                float lOff = hl + leanL * (v - 0.42f) * 1.55f * s;
                // 0,88 et non 1,02 : la frontière de l'union des deux ventricules culmine à
                // 0,90·w, donc une coronaire posée à 1,02·w FLOTTE à côté du cœur au lieu
                // de courir dessus. Et elle est deux fois plus épaisse : à 0,020 elle était
                // plus fine qu'un pixel de la surface et disparaissait dans les joints.
                float rr = w * 0.88f;
                int rgb = contract > 0.05f ? PLASMA : SPRAY;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fOff + Mth.cos(ang) * rr, lOff + Mth.sin(ang) * rr, yy,
                        0.038f * s, 0.040f * s, 0.038f * s, rgb, opaque(alpha));
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
