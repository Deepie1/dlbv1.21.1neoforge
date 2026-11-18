package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T397-CHAOS-REFONTE — élément <b>CHAOS</b> : trois signatures, un moteur, et les
 * <b>Pixels 3D</b> comme matière première.
 *
 * <p>Le Chaos était le dernier titre du mod resté sous la seconde : {@code BIG_BANG_SCAR}
 * tenait <b>22 ticks</b>, {@code CHAOS_KALEIDO_LOOP} et {@code ENTROPY_SPHERE} dix-huit,
 * et les trois partageaient une sphère générique de quarante lignes dans
 * {@code WeaponWorldVfxManager}. Aucun gameplay serveur, aucune famille {@code /anim}, aucun
 * moteur propre : l'élément existait à l'état de déclaration.</p>
 *
 * <h2>Le problème propre à cet élément</h2>
 *
 * <p>Toutes les familles précédentes ont un sujet <i>matériel</i> — une flamme, un os, une
 * vague, un trou. Le chaos n'a pas de matière : il a une <b>structure</b>, et cette structure
 * est la rupture de symétrie. Un rendu qui se contente d'agiter des cubes au hasard ne
 * produit pas du chaos, il produit du bruit — et le bruit est ennuyeux parce qu'on ne peut
 * rien y lire. Trois règles en découlent, et tout ce fichier les applique.</p>
 *
 * <ol>
 *   <li><b>Le chaos se lit CONTRE un ordre.</b> Chaque signature pose d'abord une structure
 *       manifestement régulière — six branches identiques, trois cercles orthogonaux, un
 *       anneau parfait — puis la brise. Sans la régularité initiale, la rupture ne se voit
 *       pas : c'est exactement la leçon de la grille témoin du Vide, transposée.</li>
 *   <li><b>Le désordre doit être RÉVERSIBLE à l'œil.</b> Un éclatement pur est illisible en
 *       trois images. Les trois signatures reviennent donc vers leur forme d'origine au
 *       moins une fois avant la fin, pour que le spectateur comprenne ce qui a été cassé.</li>
 *   <li><b>La couleur est la seule chose qui ne se range jamais.</b> Les corps restent
 *       sombres et stables — le violet profond de la famille — et toute la discordance passe
 *       par les <b>arêtes</b>, qui défilent en continu. C'est la consigne d'origine de
 *       {@code DeepElement.CHAOS} (« le corps conserve une base très sombre et stable, seuls
 *       les contours reçoivent les teintes dynamiques »), et c'est aussi, par chance, ce que
 *       le Pixel 3D fait nativement.</li>
 * </ol>
 *
 * <h2>Pourquoi les Pixels 3D portent l'essentiel</h2>
 *
 * <p>Un cube voxel ordinaire est peint d'une couleur unie décidée par le processeur. Un
 * Pixel 3D est un cube dont les douze arêtes défilent en arc-en-ciel sans que le processeur
 * ne calcule une seule couleur, et dont le cœur reste anthracite translucide. C'est la
 * définition visuelle du Chaos telle qu'elle était écrite dans l'énumération depuis le début,
 * livrée gratuitement par le fragment shader. Les structures porteuses (branches, cercles,
 * anneau) restent donc en voxels — il leur faut une teinte stable — et tout ce qui vole, se
 * disperse ou scintille passe par {@link VoidPixelManager}.</p>
 *
 * <h2>La palette</h2>
 *
 * <p>{@code DeepElement.CHAOS} vaut (0x2B0B38, 0x120616, 0x5B2A68). Trois violets sombres :
 * utilisables tels quels pour les corps, inutilisables pour tout le reste. L'échelle
 * ci-dessous monte des corps presque noirs jusqu'au cœur blanc du Big Bang, et aucun barreau
 * n'est interchangeable — c'est elle qui tient la lisibilité des trois signatures.</p>
 */
public final class ChaosSignatureVfx {

    private ChaosSignatureVfx() {}

    // =================================================================================
    //  IDENTIFIANTS
    // =================================================================================

    /** I — Boucle Kaléidoscopique : six branches qui se répètent, puis se désalignent. */
    public static final int KALEIDO = 0;
    /** II — Sphère d'Entropie : trois cercles orthogonaux qui se délitent en poussière. */
    public static final int ENTROPY = 1;
    /** III — Cicatrice du Big Bang : compression totale, puis expansion. */
    public static final int BIGBANG = 2;

    // =================================================================================
    //  PALETTE — luminance croissante, aucun barreau interchangeable
    // =================================================================================

    /** Corps des structures. Presque noir, mais violet : un noir pur ferait un trou. */
    private static final int BODY = 0x120616;
    /** Deuxième niveau de corps, pour détacher deux pièces superposées. */
    private static final int BODY_LIT = 0x2B0B38;
    /** Armature : ce qui tient la forme et doit rester lisible à dix mètres. */
    private static final int FRAME = 0x5B2A68;
    /**
     * T397-LISIBILITE — le verre du kaléidoscope, ajouté après mesure au banc.
     *
     * <p>Capture à t = 0,62 : la figure entière était un nuage de taches noires. En
     * cause, l'échelle d'origine — {@code BODY} à 18 de luminance, {@code BODY_LIT} à 43,
     * {@code FRAME} à 90 — appliquée à des cubes de sept centimètres sur un fond de nuit.
     * Les trois tons existaient bien (règle « un ton ⇒ trois teintes »), mais les deux
     * premiers tombaient <i>sous</i> le fond, donc l'écart utile n'était que de deux tons.
     *
     * <p>Ce violet clair est le quatrième barreau, et il devient le ton par défaut de tout
     * ce qui <b>porte</b> la forme : grands cercles, méridiens, cœur de branche. Le corps
     * presque noir reste réservé à ce qui doit <b>masquer</b> — l'anneau du Big Bang, les
     * reflets au sol — et c'est désormais sa seule raison d'être.</p>
     */
    private static final int GLASS = 0x8E58B4;
    /** Liseré d'arête des structures voxel. Le seul violet franc de l'échelle. */
    private static final int EDGE = 0x9B3FD0;
    /** Points de rupture : là où la symétrie casse. Rose néon. */
    private static final int BREAK = 0xFF48C8;
    /** Cyan électrique — l'autre pôle de la discordance, jamais mélangé au rose. */
    private static final int SPARK = 0x3FE8FF;
    /** Cœur de la singularité. Le seul blanc du fichier, et il ne sert qu'au Big Bang. */
    private static final int CORE = 0xFFFFFF;

    /** Au-delà, le tamis de densité décime les cubes : tout alpha utile passe par là. */
    private static final int SOLID = 200;

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

        switch (variant) {
            case ENTROPY -> renderEntropy(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt, ground);
            case BIGBANG -> renderBigBang(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt, ground);
            default -> renderKaleido(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt, ground);
        }
    }

    // =================================================================================
    //  I. BOUCLE KALÉIDOSCOPIQUE — 150 ticks
    // =================================================================================

    /**
     * Six branches rigoureusement identiques, disposées à soixante degrés, qui tournent
     * ensemble — puis l'une d'elles décroche, et les cinq autres la suivent une par une.
     *
     * <p><b>Ce qui fait l'effet.</b> Un kaléidoscope n'est pas joli parce qu'il est coloré,
     * il est joli parce qu'il est <i>exactement</i> symétrique. La première moitié de
     * l'animation construit donc une symétrie d'ordre six sans la moindre entorse : même
     * longueur, même phase, même cadence pour les six branches. La rupture n'arrive qu'à
     * 46 % — et parce qu'elle arrive tard, elle se voit.</p>
     *
     * <p><b>La boucle.</b> À 78 %, les branches se réalignent de force : la figure
     * redevient un kaléidoscope parfait pendant une douzaine de ticks, puis se brise pour de
     * bon. C'est la deuxième règle du fichier — sans ce retour à l'ordre, le spectateur ne
     * saurait jamais ce qui a été cassé.</p>
     */
    private static void renderKaleido(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt, float ground) {
        final int ARMS = 6;
        final float YC = 1.05f;
        float spin = t * 3.40f;

        // Désalignement : nul jusqu'à 46 %, puis croissant ; annulé de force sur la
        // fenêtre de réalignement [0.78, 0.86], d'où la soustraction du second terme.
        float chaosAmount = smooth(t, 0.46f, 0.74f);
        float relock = smooth(t, 0.78f, 0.82f) * (1.0f - smooth(t, 0.86f, 0.92f));
        chaosAmount *= (1.0f - relock);

        float reach = 0.55f + 2.35f * smooth(t, 0.04f, 0.42f);
        float fade = 1.0f - smooth(t, 0.90f, 1.0f);
        int a = Math.round(alpha * fade);
        if (a <= 8) return;

        for (int arm = 0; arm < ARMS; arm++) {
            float base = arm * (Mth.TWO_PI / ARMS);
            // Le décrochage de chaque branche a son propre retard : la symétrie ne casse
            // pas d'un bloc, elle s'effrite dans l'ordre. Une rupture simultanée aurait
            // simplement ressemblé à un changement d'échelle.
            float stagger = (arm * 0.37f) % 1.0f;
            float broken = chaosAmount * (0.35f + 0.65f * stagger);
            float ang = base + spin + broken * (1.25f * sin(arm * 2.1f + t * 9.0f));
            float armReach = reach * (1.0f - broken * 0.22f * (1.0f + sin(arm * 1.7f)));

            float ca = cos(ang);
            float sa = sin(ang);

            // --- LA BRANCHE : une épine segmentée, pas un trait. Huit nœuds, chacun un
            //     peu plus fin que le précédent, avec un décalage latéral sinusoïdal qui
            //     donne à la branche sa silhouette de cristal.
            // T397-DENSE-KALEIDO — DOUZE nœuds et non huit, et surtout : chaque nœud est
            // un TRIANGLE de trois cubes, pas un cube isolé. Mesuré sur la première
            // version : huit cubes espacés de trente centimètres sur une branche de 2,9
            // blocs se lisent comme une ligne pointillée, pas comme une épine de cristal.
            // La règle « élargir avant de détailler » dit quoi faire — donner du VOLUME à
            // la branche d'abord, ciseler ensuite.
            int nodes = 12;
            for (int n = 1; n <= nodes; n++) {
                float u = n / (float) nodes;
                float dist = armReach * u;
                float lateral = 0.14f * sin(u * 5.6f + arm * 1.3f) * (1.0f + broken * 2.4f);
                float px = cx + (ca * dist - sa * lateral) * dx - (sa * dist + ca * lateral) * dz;
                float pz = cz + (ca * dist - sa * lateral) * dz + (sa * dist + ca * lateral) * dx;
                float py = cy + YC + 0.18f * sin(u * 4.1f + t * 6.0f + arm);

                float half = 0.072f * (1.12f - 0.55f * u);
                int rgb = n <= 3 ? GLASS : (n <= 8 ? FRAME : BODY_LIT);
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                        half, half, half, rgb, opaque(a));

                // LE TRIANGLE. Deux cubes de plus, décalés perpendiculairement à la branche
                // et en hauteur, qui font de chaque nœud une section triangulaire. Trois
                // points suffisent à définir un plan : c'est le minimum pour qu'une branche
                // cesse d'être une ligne et devienne un prisme.
                float wing = half * 1.55f * (1.0f + broken * 0.8f);
                float wx = -sa * wing, wz = ca * wing;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                        px + wx * dx - wz * dz, py - half * 0.85f, pz + wx * dz + wz * dx,
                        half * 0.70f, half * 0.70f, half * 0.70f, rgb, opaque(a * 0.90f));
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                        px - wx * dx + wz * dz, py - half * 0.85f, pz - wx * dz - wz * dx,
                        half * 0.70f, half * 0.70f, half * 0.70f, rgb, opaque(a * 0.90f));

                // LE LIANT. Un cube entre ce nœud et le précédent : sans lui, douze
                // sections restent douze sections. Avec lui, c'est une tige continue.
                if (n > 1) {
                    float ub = (n - 0.5f) / nodes;
                    float db = armReach * ub;
                    float lb = 0.14f * sin(ub * 5.6f + arm * 1.3f) * (1.0f + broken * 2.4f);
                    float bx = cx + (ca * db - sa * lb) * dx - (sa * db + ca * lb) * dz;
                    float bz = cz + (ca * db - sa * lb) * dz + (sa * db + ca * lb) * dx;
                    float by = cy + YC + 0.18f * sin(ub * 4.1f + t * 6.0f + arm);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, bx, by, bz,
                            half * 0.62f, half * 0.62f, half * 0.62f, FRAME, opaque(a * 0.86f));
                }

                // Liseré d'arête : un cube minuscule au-dessus du nœud. Un corps sombre
                // sans liseré disparaît sur un décor sombre — règle acquise.
                if ((n % 3) == 0) {
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py + half * 1.55f, pz,
                            half * 0.38f, half * 0.38f, half * 0.38f, EDGE, opaque(a * 0.92f));
                }

                // --- LES PIXELS 3D : la matière du kaléidoscope. Chaque nœud en sème un
                //     par poignée de frames, projeté vers l'extérieur. Ce sont eux qui
                //     portent les arêtes arc-en-ciel, donc la couleur de la famille.
                if (n >= 4 && ((salt + arm * 7 + n * 13 + (int) (t * 150.0f)) % 9) == 0) {
                    float spread = 0.012f + 0.022f * broken;
                    VoidPixelManager.spawnPixel(
                            WeaponWorldVfxManager.camX() + px, WeaponWorldVfxManager.camY() + py,
                            WeaponWorldVfxManager.camZ() + pz,
                            ca * spread * dx - sa * spread * dz,
                            0.004f + 0.010f * sin(arm + n),
                            ca * spread * dz + sa * spread * dx,
                            0.075f + 0.045f * u, 14 + (n & 3) * 4,
                            VoidPixelManager.MODE_RAINBOW, 0.0f, 0.12f);
                }
            }

            // --- LE MIROIR. Un kaléidoscope est fait de miroirs : chaque branche a son
            //     reflet sous elle, plus sombre et deux fois plus court. C'est ce qui
            //     différencie une étoile à six branches d'un vrai kaléidoscope.
            if (ground > -9.0f) {
                for (int n = 2; n <= 9; n++) {
                    float u = n / 9.0f;
                    float dist = armReach * u * 0.62f;
                    float px = cx + ca * dist * dx - sa * dist * dz;
                    float pz = cz + ca * dist * dz + sa * dist * dx;
                    float half = 0.055f * (1.0f - 0.4f * u);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + ground + 0.04f, pz,
                            half, half * 0.35f, half, BODY_LIT, opaque(a * 0.55f));
                }
            }

            // --- LE POINT DE RUPTURE. Visible uniquement quand la branche décroche :
            //     un cube rose au coude, à l'endroit exact où la symétrie cède.
            if (broken > 0.05f) {
                float dist = armReach * 0.58f;
                float px = cx + ca * dist * dx - sa * dist * dz;
                float pz = cz + ca * dist * dz + sa * dist * dx;
                float h = 0.05f + 0.07f * broken;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC, pz,
                        h, h, h, BREAK, opaque(a * (0.45f + 0.55f * broken)));
            }
        }

        // --- LA MANDALA. Trois anneaux concentriques de dix-huit cubes, à soixante degrés
        //     les uns des autres : c'est l'image qu'un vrai kaléidoscope renvoie, et elle
        //     manquait entièrement à la première version. Chaque anneau perd ses cubes au
        //     fur et à mesure que le désordre monte, et il les perd dans un ordre FIXE
        //     (hachage de l'indice) pour qu'un cube disparu ne revienne jamais.
        for (int ring = 0; ring < 3; ring++) {
            float rr = reach * (0.26f + ring * 0.29f);
            float ringSpin = spin * (ring % 2 == 0 ? 1.0f : -0.75f);
            int ringCubes = 18;
            for (int k = 0; k < ringCubes; k++) {
                if (hash(salt + ring * 77, k * 23) < chaosAmount * 0.85f) continue;
                float ang = k / (float) ringCubes * Mth.TWO_PI + ringSpin;
                float rx = cos(ang) * rr, rz2 = sin(ang) * rr;
                float px = cx + rx * dx - rz2 * dz;
                float pz = cz + rx * dz + rz2 * dx;
                float py = cy + YC + 0.07f * sin(ang * 3.0f + t * 5.0f);
                float h = 0.052f - ring * 0.008f;
                int rgb = (k % 6 == 0) ? EDGE : (ring == 1 ? GLASS : FRAME);
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                        h, h, h, rgb, opaque(a * (0.92f - ring * 0.08f)));
            }
        }

        // --- LES PANNEAUX DE MIROIR. Entre deux branches voisines, la paroi de verre que
        //     le kaléidoscope place pour fabriquer la symétrie. Sept cubes en biais, de
        //     plus en plus écartés : sans eux, la figure est une étoile, pas un
        //     kaléidoscope. Ils s'effacent dès que la symétrie casse — c'est bien la paroi
        //     qui se brise, pas les branches qui bougent.
        float paneAlpha = 1.0f - chaosAmount;
        if (paneAlpha > 0.05f) {
            for (int arm = 0; arm < ARMS; arm++) {
                float mid = (arm + 0.5f) * (Mth.TWO_PI / ARMS) + spin;
                float cm = cos(mid), sm = sin(mid);
                for (int k = 1; k <= 7; k++) {
                    float u = k / 7.0f;
                    float dist = reach * u * 0.86f;
                    float px = cx + (cm * dist) * dx - (sm * dist) * dz;
                    float pz = cz + (cm * dist) * dz + (sm * dist) * dx;
                    float py = cy + YC + 0.22f * sin(u * Mth.PI);
                    float h = 0.040f + 0.016f * u;
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                            h, h * 2.1f, h, FRAME, opaque(a * paneAlpha * 0.78f));
                }
            }
        }

        // --- LE TUBE. Un kaléidoscope se regarde par un bout : cinq cubes empilés sur
        //     l'axe, qui donnent à la figure une profondeur verticale qu'une mandala plate
        //     n'a jamais. Ils tournent avec le moyeu.
        for (int k = 0; k < 5; k++) {
            float hy = (k - 2) * 0.22f;
            float r = 0.13f + 0.05f * Math.abs(hy);
            float ang = -spin * 2.2f + k * 1.26f;
            float px = cx + (cos(ang) * r) * dx - (sin(ang) * r) * dz;
            float pz = cz + (cos(ang) * r) * dz + (sin(ang) * r) * dx;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + hy, pz,
                    0.048f, 0.048f, 0.048f, k == 2 ? SPARK : FRAME, opaque(a * 0.88f));
        }

        // --- LE MOYEU. Six faces, et il tourne à contresens des branches : c'est le seul
        //     élément qui reste parfaitement régulier du début à la fin, et c'est lui qui
        //     rend le désordre des branches lisible.
        float hubSpin = -spin * 1.7f;
        for (int k = 0; k < 6; k++) {
            float ang = hubSpin + k * (Mth.TWO_PI / 6.0f);
            float r = 0.30f + 0.05f * sin(t * 12.0f + k);
            float px = cx + (cos(ang) * r) * dx - (sin(ang) * r) * dz;
            float pz = cz + (cos(ang) * r) * dz + (sin(ang) * r) * dx;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC, pz,
                    0.075f, 0.075f, 0.075f, k % 2 == 0 ? EDGE : SPARK, opaque(a));
        }
        // Le cœur du moyeu, en Pixels 3D à teinte imposée cyan : il bat, il ne défile pas.
        if (((salt + (int) (t * 150.0f)) & 3) == 0) {
            VoidPixelManager.spawnPixel(
                    WeaponWorldVfxManager.camX() + cx, WeaponWorldVfxManager.camY() + cy + YC,
                    WeaponWorldVfxManager.camZ() + cz,
                    (hash(salt, 11) - 0.5f) * 0.055f, (hash(salt, 23) - 0.3f) * 0.045f,
                    (hash(salt, 37) - 0.5f) * 0.055f,
                    0.10f, 18, VoidPixelManager.MODE_TINTED, 0.52f, 0.0f);
        }
    }

    // =================================================================================
    //  II. SPHÈRE D'ENTROPIE — 170 ticks
    // =================================================================================

    /**
     * Trois cercles orthogonaux, nets et réguliers, qui perdent leurs cubes un par un.
     *
     * <p><b>Ce qui fait l'effet.</b> L'entropie n'est pas une explosion : c'est une
     * dégradation <i>monotone</i>. Les trois grands cercles sont donc tracés en entier au
     * début, puis chaque cube a une probabilité croissante — et jamais décroissante —
     * d'être remplacé par un Pixel 3D qui s'échappe. À la fin, l'armature a disparu et il ne
     * reste que la poussière qu'elle est devenue. Rien n'est jamais rendu.</p>
     *
     * <p><b>Le choix du cube qui tombe.</b> Il est tiré d'un hachage de l'indice, pas d'un
     * aléatoire par image : un cube qui a chuté reste tombé. Un tirage par frame aurait fait
     * clignoter l'armature entière au lieu de l'éroder, et l'érosion est tout le sujet.</p>
     */
    private static void renderEntropy(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt, float ground) {
        final float YC = 1.15f;
        // T397-DENSE-ENTROPY — trente-quatre segments et non vingt-six. Mesure : trois
        // cercles de vingt-six cubes à 1,87 bloc de rayon laissent 45 cm entre deux cubes,
        // soit six fois leur taille. Ce n'était pas une sphère, c'était trois colliers.
        final int SEGMENTS = 34;
        float radius = 0.35f + 1.52f * smooth(t, 0.02f, 0.30f);
        // L'entropie monte, et ne redescend jamais : pas de smooth retour, une rampe.
        float entropy = clamp((t - 0.16f) / 0.70f);
        float fade = 1.0f - smooth(t, 0.88f, 1.0f);
        int a = Math.round(alpha * fade);
        if (a <= 8) return;

        float spin = t * 1.95f;

        for (int ring = 0; ring < 3; ring++) {
            // Trois plans orthogonaux. Chacun tourne à sa propre vitesse — trois cercles
            // qui tournent ensemble se lisent comme une sphère solide, et on perdrait
            // justement la lecture des trois plans.
            float ringSpin = spin * (1.0f + ring * 0.33f);
            for (int s = 0; s < SEGMENTS; s++) {
                float u = s / (float) SEGMENTS;
                float ang = u * Mth.TWO_PI + ringSpin;

                // Le cube est-il déjà tombé ? Un seuil fixe par cube, comparé à l'entropie
                // courante. Déterministe, donc irréversible.
                float threshold = hash(salt + ring * 131, s * 17);
                boolean fallen = entropy > threshold;

                float lx, ly, lz;
                float ca = cos(ang), sa = sin(ang);
                if (ring == 0) { lx = ca * radius; ly = sa * radius; lz = 0.0f; }
                else if (ring == 1) { lx = 0.0f; ly = ca * radius; lz = sa * radius; }
                else { lx = ca * radius; ly = 0.0f; lz = sa * radius; }

                float px = cx + lx * dx - lz * dz;
                float pz = cz + lx * dz + lz * dx;
                float py = cy + YC + ly;

                if (!fallen) {
                    float half = 0.072f;
                    int rgb = (s % 5 == 0) ? EDGE : (ring == 0 ? GLASS : FRAME);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                            half, half, half, rgb, opaque(a));
                } else if (((salt + s * 31 + ring * 7 + (int) (t * 170.0f)) % 23) == 0) {
                    // Le cube tombé ne disparaît pas : il DEVIENT un Pixel 3D qui dérive.
                    // C'est la seule façon de montrer que la matière n'est pas détruite,
                    // seulement désordonnée — ce qui est la définition de l'entropie.
                    VoidPixelManager.spawnPixel(
                            WeaponWorldVfxManager.camX() + px, WeaponWorldVfxManager.camY() + py,
                            WeaponWorldVfxManager.camZ() + pz,
                            (hash(s, ring * 3 + 1) - 0.5f) * 0.030f,
                            (hash(s, ring * 3 + 2) - 0.5f) * 0.024f,
                            (hash(s, ring * 3 + 3) - 0.5f) * 0.030f,
                            0.085f, 26 + (s & 7) * 3,
                            VoidPixelManager.MODE_RAINBOW, 0.0f, 0.30f);
                }
            }
        }

        // --- LES MÉRIDIENS. Six demi-cercles verticaux qui joignent les deux pôles :
        //     c'est ce qui transforme trois grands cercles en SPHÈRE. Ils s'érodent avec
        //     la même loi que les cercles — même hachage, même seuil — donc la structure
        //     entière se délite d'un seul mouvement, et non par couches.
        for (int m = 0; m < 6; m++) {
            float lon = m / 6.0f * Mth.PI + spin * 0.45f;
            float cl = cos(lon), sl2 = sin(lon);
            for (int k = 1; k < 16; k++) {
                if (entropy > hash(salt + 313 + m * 59, k * 41)) continue;
                float lat = k / 16.0f * Mth.PI - Mth.PI * 0.5f;
                float cr = cos(lat) * radius * 0.97f;
                float lx = cl * cr, lz = sl2 * cr, ly = sin(lat) * radius * 0.97f;
                float px = cx + lx * dx - lz * dz;
                float pz = cz + lx * dz + lz * dx;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + ly, pz,
                        0.045f, 0.045f, 0.045f, (k % 4 == 0) ? EDGE : FRAME,
                        opaque(a * 0.80f));
            }
        }

        // --- LA COQUE INTERNE. Trois cercles au tiers du rayon, qui tournent à l'envers.
        //     Une sphère vide se lit comme un anneau de profil ; une sphère à deux coques
        //     garde son volume sous tous les angles. C'est la règle « volume plein ⇒ CAGE »
        //     appliquée à une cage qui, elle, se vide.
        for (int ring = 0; ring < 3; ring++) {
            float rr = radius * 0.52f;
            float ringSpin = -spin * (1.0f + ring * 0.21f);
            for (int s2 = 0; s2 < 22; s2++) {
                if (entropy * 1.25f > hash(salt + 517 + ring * 97, s2 * 13)) continue;
                float ang = s2 / 22.0f * Mth.TWO_PI + ringSpin;
                float ca2 = cos(ang), sa2 = sin(ang);
                float lx, ly, lz;
                if (ring == 0) { lx = ca2 * rr; ly = sa2 * rr; lz = 0.0f; }
                else if (ring == 1) { lx = 0.0f; ly = ca2 * rr; lz = sa2 * rr; }
                else { lx = ca2 * rr; ly = 0.0f; lz = sa2 * rr; }
                float px = cx + lx * dx - lz * dz;
                float pz = cz + lx * dz + lz * dx;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + ly, pz,
                        0.050f, 0.050f, 0.050f, (s2 % 7 == 0) ? SPARK : GLASS,
                        opaque(a * 0.84f));
            }
        }

        // --- LE NOYAU. Il grossit exactement comme l'armature maigrit : la matière passe
        //     de l'ordre au désordre, elle ne s'évapore pas. Octaèdre, six sommets.
        float coreR = 0.12f + 0.34f * entropy;
        // Les douze arêtes de l'octaèdre, trois cubes chacune. Six sommets seuls se lisent
        // comme six points sans rapport ; avec les arêtes, c'est un solide.
        for (int e = 0; e < 12; e++) {
            int pa = e % 3, pb = (e / 3) % 2, pc = e / 6;
            float ax = (pa == 0 ? (pb == 0 ? coreR : -coreR) : 0.0f);
            float ay = (pa == 1 ? (pb == 0 ? coreR : -coreR) : 0.0f);
            float az = (pa == 2 ? (pb == 0 ? coreR : -coreR) : 0.0f);
            float bx = (pa == 0 ? 0.0f : (pc == 0 ? coreR : -coreR));
            float by = (pa == 1 ? 0.0f : (pa == 0 ? (pc == 0 ? coreR : -coreR) : 0.0f));
            float bz = (pa == 2 ? 0.0f : (pa == 1 ? (pc == 0 ? coreR : -coreR) : 0.0f));
            for (int k = 1; k <= 3; k++) {
                float u = k / 4.0f;
                float ox = ax + (bx - ax) * u, oy = ay + (by - ay) * u, oz = az + (bz - az) * u;
                float px = cx + ox * dx - oz * dz;
                float pz = cz + ox * dz + oz * dx;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + oy, pz,
                        0.030f, 0.030f, 0.030f, EDGE, opaque(a * 0.78f));
            }
        }
        for (int axis = 0; axis < 6; axis++) {
            float ox = (axis == 0 ? coreR : axis == 1 ? -coreR : 0.0f);
            float oy = (axis == 2 ? coreR : axis == 3 ? -coreR : 0.0f);
            float oz = (axis == 4 ? coreR : axis == 5 ? -coreR : 0.0f);
            float px = cx + ox * dx - oz * dz;
            float pz = cz + ox * dz + oz * dx;
            float h = 0.055f + 0.045f * entropy;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + oy, pz,
                    h, h, h, BREAK, opaque(a));
        }
        // Centre : un Pixel 3D monochrome, pour que le noyau ne soit pas qu'un bloc rose.
        if (((salt + (int) (t * 170.0f)) % 5) == 0) {
            VoidPixelManager.spawnPixel(
                    WeaponWorldVfxManager.camX() + cx, WeaponWorldVfxManager.camY() + cy + YC,
                    WeaponWorldVfxManager.camZ() + cz,
                    (hash(salt, 51) - 0.5f) * 0.018f, (hash(salt, 52) - 0.5f) * 0.018f,
                    (hash(salt, 53) - 0.5f) * 0.018f,
                    0.12f, 22, VoidPixelManager.MODE_MONO, 0.0f, 0.0f);
        }

        // --- LA TRACE AU SOL. Un disque pointillé qui s'efface à mesure que l'entropie
        //     monte : l'ordre s'en va aussi par terre, pas seulement en l'air.
        if (ground > -9.0f) {
            for (int band = 0; band < 2; band++) {
                int marks = band == 0 ? 28 : 20;
                float r = radius * (band == 0 ? 1.08f : 0.64f);
                for (int m = 0; m < marks; m++) {
                    if (hash(salt + 907 + band * 211, m * 29) < entropy) continue;
                    float ang = m / (float) marks * Mth.TWO_PI
                            - spin * (band == 0 ? 0.5f : -0.8f);
                    float px = cx + (cos(ang) * r) * dx - (sin(ang) * r) * dz;
                    float pz = cz + (cos(ang) * r) * dz + (sin(ang) * r) * dx;
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + ground + 0.03f, pz,
                            0.070f, 0.022f, 0.070f, band == 0 ? GLASS : FRAME,
                            opaque(a * (band == 0 ? 0.70f : 0.58f)));
                }
            }
        }
    }

    // =================================================================================
    //  III. CICATRICE DU BIG BANG — 200 ticks
    // =================================================================================

    /**
     * Deux temps, et rien entre les deux : <b>tout rentre</b>, puis <b>tout sort</b>.
     *
     * <p><b>Ce qui fait l'effet.</b> Une explosion seule n'impressionne personne — le jeu en
     * est plein. Ce qui frappe, c'est l'<i>aspiration</i> qui la précède : quatre-vingts
     * ticks pendant lesquels la matière converge vers un point de plus en plus petit et de
     * plus en plus lumineux, jusqu'à un cœur blanc d'un seul cube. Le silence visuel de
     * cette phase est ce qui donne son poids à la suivante.</p>
     *
     * <p><b>L'anneau noir.</b> Au moment de la détonation, un tore de corps presque noirs
     * s'ouvre à l'horizontale et grandit plus vite que les rayons. Il n'émet aucune lumière :
     * son rôle est de <i>masquer</i>, pour que l'explosion ait un bord net au lieu de se
     * diluer. C'est la règle du Vide — on dessine le bord — appliquée à l'envers.</p>
     */
    private static void renderBigBang(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt, float ground) {
        final float YC = 1.20f;
        final float BANG = 0.42f;      // l'instant de la détonation
        float fade = 1.0f - smooth(t, 0.90f, 1.0f);
        int a = Math.round(alpha * fade);
        if (a <= 8) return;

        if (t < BANG) {
            // ---------------------------------------------------------------- COMPRESSION
            float u = t / BANG;                       // 0 → 1 sur la phase d'aspiration
            float pull = u * u;                       // accélère : l'effondrement s'emballe
            // 40 fragments qui tombent vers le centre en spirale. Leur rayon de départ est
            // fixe par fragment, donc on les voit vraiment converger — un rayon tiré à
            // chaque image aurait donné un nuage qui palpite.
            for (int k = 0; k < 40; k++) {
                float r0 = 1.4f + 2.6f * hash(salt + 11, k * 13);
                float r = r0 * (1.0f - pull * 0.94f);
                float ang = hash(salt + 29, k * 7) * Mth.TWO_PI + pull * 6.5f;
                float h0 = (hash(salt + 43, k * 19) - 0.5f) * 2.4f;
                float px = cx + (cos(ang) * r) * dx - (sin(ang) * r) * dz;
                float pz = cz + (cos(ang) * r) * dz + (sin(ang) * r) * dx;
                float py = cy + YC + h0 * (1.0f - pull * 0.90f);
                float half = 0.055f + 0.040f * (1.0f - pull);
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                        half, half, half, k % 4 == 0 ? GLASS : FRAME, opaque(a));
                // T397-DENSE-BANG — LA COMÈTE. Trois cubes derrière le fragment, sur le
                // chemin qu'il vient de parcourir. Quarante points qui se rapprochent ne
                // disent pas qu'ils TOMBENT ; quarante comètes, si. La traînée pointe vers
                // l'extérieur, donc l'œil lit le sens du mouvement sans ambiguïté.
                for (int q = 1; q <= 3; q++) {
                    float back = 1.0f + q * 0.075f;
                    float rq = r * back;
                    float aq = ang - q * 0.085f;
                    float tx = cx + (cos(aq) * rq) * dx - (sin(aq) * rq) * dz;
                    float tz = cz + (cos(aq) * rq) * dz + (sin(aq) * rq) * dx;
                    float ty = cy + YC + h0 * (1.0f - pull * 0.90f) * back;
                    float th = half * (0.72f - q * 0.14f);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, tx, ty, tz,
                            th, th, th, BODY_LIT, opaque(a * (0.80f - q * 0.16f)));
                }
                // Traînée : un Pixel 3D lâché derrière le fragment, sans gravité, qui
                // marque le chemin parcouru. C'est ce qui rend l'aspiration lisible.
                if (((salt + k * 5 + (int) (t * 200.0f)) % 7) == 0) {
                    VoidPixelManager.spawnPixel(
                            WeaponWorldVfxManager.camX() + px, WeaponWorldVfxManager.camY() + py,
                            WeaponWorldVfxManager.camZ() + pz,
                            0.0f, 0.0f, 0.0f, 0.070f, 12,
                            VoidPixelManager.MODE_RAINBOW, 0.0f, 0.0f);
                }
            }
            // Le cœur : il grossit et blanchit à mesure que tout le reste rentre.
            float coreH = 0.05f + 0.13f * pull;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, cx, cy + YC, cz,
                    coreH, coreH, coreH, pull > 0.72f ? CORE : BREAK, opaque(a));
            // LA CAGE DE CONFINEMENT. Douze cubes sur un cuboctaèdre qui se referme avec
            // le cœur : c'est elle qui rend l'effondrement visible de près, là où le cœur
            // seul n'est qu'un point lumineux qui grandit.
            float cageR = (0.95f - 0.78f * pull);
            for (int k = 0; k < 12; k++) {
                float ang = k / 12.0f * Mth.TWO_PI + pull * 4.0f;
                float tier = ((k % 3) - 1) * 0.55f;
                float rr = cageR * (1.0f - 0.35f * Math.abs(tier));
                float px = cx + (cos(ang) * rr) * dx - (sin(ang) * rr) * dz;
                float pz = cz + (cos(ang) * rr) * dz + (sin(ang) * rr) * dx;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                        px, cy + YC + tier * cageR, pz,
                        0.038f, 0.038f, 0.038f, (k % 3 == 0) ? SPARK : EDGE,
                        opaque(a * (0.55f + 0.45f * pull)));
            }
            return;
        }

        // ---------------------------------------------------------------- EXPANSION
        float e = (t - BANG) / (1.0f - BANG);          // 0 → 1 après la détonation
        float wave = (float) Math.sqrt(e);             // fondu linéaire = marche visible

        // --- LE CŒUR, qui se vide. Il reste blanc trois ticks puis s'effondre sur lui-même.
        float coreH = 0.18f * (1.0f - smooth(e, 0.0f, 0.22f));
        if (coreH > 0.004f) {
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, cx, cy + YC, cz,
                    coreH, coreH, coreH, CORE, opaque(a));
        }

        // --- LES RAYONS. Douze, et pas un éventail plat : ils partent dans toutes les
        //     directions de l'espace, tirés d'une distribution en spirale de Fibonacci
        //     pour qu'aucun ne se superpose à un autre.
        final int RAYS = 16;
        for (int k = 0; k < RAYS; k++) {
            float phi = (k + 0.5f) / RAYS;
            float cosTheta = 1.0f - 2.0f * phi;
            float sinTheta = Mth.sqrt(Math.max(0.0f, 1.0f - cosTheta * cosTheta));
            float golden = k * 2.39996f + salt * 0.017f;
            float ux = sinTheta * cos(golden);
            float uy = cosTheta;
            float uz = sinTheta * sin(golden);

            float reach = 4.6f * wave;
            int steps = 12;
            for (int n = 1; n <= steps; n++) {
                float f = n / (float) steps;
                float dist = reach * f;
                // Le rayon est en retard sur sa pointe : les cubes du fond n'apparaissent
                // qu'une fois la pointe passée, sinon le rayon surgit d'un bloc.
                if (f > wave * 1.15f) break;
                float px = cx + (ux * dist) * dx - (uz * dist) * dz;
                float pz = cz + (ux * dist) * dz + (uz * dist) * dx;
                float py = cy + YC + uy * dist;
                float half = 0.095f * (1.0f - 0.62f * f);
                int rgb = n <= 3 ? CORE : (n <= 7 ? BREAK : GLASS);
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, py, pz,
                        half, half, half, rgb, opaque(a * (1.0f - 0.35f * f)));
                // LES BARBES. Deux cubes perpendiculaires un pas sur trois : un rayon
                // nu est un trait, un rayon barbelé est une onde de choc. Elles
                // s'écartent avec la distance, donc le rayon s'évase au lieu de filer.
                if ((n % 3) == 0) {
                    float bw = half * (1.8f + 2.4f * f);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            px - uz * bw * dx - ux * bw * dz, py,
                            pz - uz * bw * dz + ux * bw * dx,
                            half * 0.55f, half * 0.55f, half * 0.55f, EDGE,
                            opaque(a * (0.72f - 0.30f * f)));
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            px + uz * bw * dx + ux * bw * dz, py,
                            pz + uz * bw * dz - ux * bw * dx,
                            half * 0.55f, half * 0.55f, half * 0.55f, EDGE,
                            opaque(a * (0.72f - 0.30f * f)));
                }
            }
            // Un Pixel 3D en bout de rayon, projeté vers l'extérieur : la matière créée
            // par l'explosion, qui continue sa route quand le rayon s'est déjà éteint.
            if (((salt + k * 17 + (int) (t * 200.0f)) % 4) == 0) {
                float dist = 4.6f * wave;
                float px = cx + (ux * dist) * dx - (uz * dist) * dz;
                float pz = cz + (ux * dist) * dz + (uz * dist) * dx;
                VoidPixelManager.spawnPixel(
                        WeaponWorldVfxManager.camX() + px,
                        WeaponWorldVfxManager.camY() + cy + YC + uy * dist,
                        WeaponWorldVfxManager.camZ() + pz,
                        ux * 0.055f, uy * 0.055f, uz * 0.055f,
                        0.095f, 20, VoidPixelManager.MODE_RAINBOW, 0.0f, 0.22f);
            }
        }

        // --- L'ANNEAU NOIR. Horizontal, plus rapide que les rayons, et muet : il ne
        //     brille pas, il tranche. Trente-deux corps presque noirs avec un liseré
        //     violet sur un cube sur quatre — un anneau de noir pur serait invisible.
        float ringR = 5.4f * wave * 1.18f;
        if (ringR > 0.4f) {
            int ringCubes = 48;
            float thin = 1.0f - smooth(e, 0.62f, 1.0f);
            for (int k = 0; k < ringCubes; k++) {
                float ang = k / (float) ringCubes * Mth.TWO_PI;
                float px = cx + (cos(ang) * ringR) * dx - (sin(ang) * ringR) * dz;
                float pz = cz + (cos(ang) * ringR) * dz + (sin(ang) * ringR) * dx;
                float h = (0.16f + 0.06f * sin(k * 1.7f)) * thin;
                if (h < 0.01f) continue;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC, pz,
                        h, h * 1.45f, h, BODY, opaque(a));
                if ((k & 1) == 0) {
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + YC + h * 1.7f, pz,
                            h * 0.46f, h * 0.46f, h * 0.46f, (k & 3) == 0 ? SPARK : EDGE,
                            opaque(a * 0.92f));
                }
                // La couronne traînante : un second cube, en retrait et plus bas, qui
                // donne de l'épaisseur au tore. Un anneau d'un cube de section se lit
                // comme un cercle dessiné, pas comme un volume qui passe.
                float lagR = ringR * 0.88f;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                        cx + (cos(ang) * lagR) * dx - (sin(ang) * lagR) * dz,
                        cy + YC - h * 0.75f,
                        cz + (cos(ang) * lagR) * dz + (sin(ang) * lagR) * dx,
                        h * 0.70f, h * 0.95f, h * 0.70f, BODY, opaque(a * 0.72f));
            }
        }

        // --- LA CICATRICE AU SOL. Ce qui donne son nom à la signature : l'explosion
        //     laisse une marque qui, elle, ne s'efface pas avant la fin de l'animation.
        if (ground > -9.0f) {
            int scar = 34;
            for (int k = 0; k < scar; k++) {
                float u = k / (float) (scar - 1);
                float along = (u - 0.5f) * 2.0f * 3.2f * Mth.clamp(wave * 1.4f, 0.0f, 1.0f);
                float jitter = (hash(salt + 613, k * 11) - 0.5f) * 0.55f;
                float px = cx + along * dx - jitter * dz;
                float pz = cz + along * dz + jitter * dx;
                float h = 0.095f * (1.0f - 0.55f * Math.abs(u - 0.5f) * 2.0f);
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, px, cy + ground + 0.035f, pz,
                        h, 0.026f, h * 0.72f, k % 3 == 0 ? EDGE : FRAME, opaque(a * 0.88f));
            }
        }
    }

    // =================================================================================
    //  OUTILS
    // =================================================================================

    /** Hachage déterministe 0..1. Deux entiers entrent, aucune allocation ne sort. */
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFF) / 8388607.0f;
    }

    /** Smoothstep borné, la seule interpolation du fichier : un fondu linéaire se voit. */
    private static float smooth(float x, float from, float to) {
        if (to <= from) return x >= to ? 1.0f : 0.0f;
        float u = Mth.clamp((x - from) / (to - from), 0.0f, 1.0f);
        return u * u * (3.0f - 2.0f * u);
    }

    private static float clamp(float v) { return Mth.clamp(v, 0.0f, 1.0f); }

    private static float sin(float v) { return Mth.sin(v); }

    private static float cos(float v) { return Mth.cos(v); }

    /**
     * Seuil d'opacité. Le tamis de densité décime les cubes sous 200 d'alpha : un fondu
     * d'alpha ne fait pas disparaître, il TROUE. L'effacement se joue donc sur la
     * géométrie, et tout alpha utile remonte au plancher.
     */
    private static int opaque(float v) {
        int i = Math.round(v);
        if (i <= 10) return 0;
        return i < SOLID ? SOLID : Math.min(255, i);
    }
}
