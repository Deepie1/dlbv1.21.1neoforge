package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T374-WIND-REFONTE — élément VENT : trois signatures détaillées sur un moteur voxel commun.
 *
 * <p><b>Pourquoi une classe dédiée ?</b> Le vent était éclaté : la lance vivait dans
 * {@link GaiaWorldTreeVfx} sous forme de modèle compacté (variant {@code ZEPHYR}), et Sandstorm
 * dormait en archive dans {@link EarthSignatureVfx} avec du code Terre. Aucune des deux n'avait de
 * langage visuel propre : ni rubans, ni tourbillons, ni grain. Cette classe leur donne une identité
 * unique — blanc glacé, azur, ardoise — et ajoute la troisième signature, l'Œil du cyclone.</p>
 *
 * <p><b>Densité.</b> Chaque signature est composée de plusieurs couches indépendantes (sol, tresse,
 * noyau, corps, couronne, éclats, brume) qui apparaissent et disparaissent à des instants différents.
 * Le pic de charge reste sous les 2 000 cubes par image, très loin du plafond de 7 000 du manager :
 * plusieurs impacts simultanés tiennent sans être tronqués.</p>
 *
 * <p><b>Signatures.</b></p>
 * <ul>
 *   <li>{@link #ZEPHYR} — <i>Lance de zéphyr</i> : trois anneaux de poussière aspirés au sol, six brins
 *       hélicoïdaux contra-rotatifs qui se resserrent en tresse, noyau lumineux, puis trois lances
 *       d'air comprimé (fût double, huit ailettes, pointe de cristal, deux hélices de sillage) qui
 *       percent la cible et éclosent en couronne de compression, éclats de plumes et brume.</li>
 *   <li>{@link #SANDSTORM} — <i>Tempête de sable</i> : cinq strates de dune, cinq bras spiralés,
 *       sept rubans, trois mini-tornades, cent soixante grains et un voile ocre.</li>
 *   <li>{@link #TEMPEST} — <i>Œil du cyclone</i> : deux entonnoirs contra-rotatifs de huit brins,
 *       noyau, anneau de calme à rayons, débris en orbite, mur de nuages, éclatement puis
 *       effondrement.</li>
 * </ul>
 *
 * <p><b>Contraintes respectées.</b> Aucun appel dans la passe de rendu ne peut allouer de tableau :
 * tout est statique ou scalaire. Aucun RenderType n'est demandé ici — les cubes passent par les
 * émetteurs de {@link WeaponWorldVfxManager}, qui possède depuis V6.4 sa propre source de buffers
 * (le bug {@code Not building!} venait de deux RenderType sur une même source). Aucune géométrie
 * n'est écrite hors du repère fourni par l'appelant, donc le rendu reste aligné sur la caméra.</p>
 */
public final class WindSignatureVfx {

    /** Lance de zéphyr (identifiant {@code ZEPHYR_WIND_LANCE}). */
    public static final int ZEPHYR = 0;
    /** Tempête de sable (identifiant {@code SANDSTORM_DUNE}, réactivé). */
    public static final int SANDSTORM = 1;
    /** Œil du cyclone (identifiant {@code TEMPEST_EYE}). */
    public static final int TEMPEST = 2;

    /** Palette vent : du plus sombre au plus lumineux. */
    private static final int AIR_DEEP = 0x2C4658;
    private static final int AIR = 0x4E7F99;
    private static final int AZURE = 0x6FBEDC;
    private static final int PALE = 0xA9E2F2;
    private static final int GLASS = 0xD9F5FF;
    private static final int WHITE = 0xF4FDFF;
    private static final int SLATE_DARK = 0x59636B;
    private static final int SLATE = 0x8C9AA4;
    private static final int STEEL = 0xB9C6CE;
    private static final int SAND_DARK = 0x6E5B41;
    private static final int SAND = 0xA88B5E;
    private static final int SAND_LIGHT = 0xDCC79B;
    private static final int DUST = 0xF0E2BF;

    private static final float TAU = (float) (Math.PI * 2.0);

    private WindSignatureVfx() { }

    /**
     * Point d'entrée unique des trois signatures.
     *
     * @param cx,cy,cz origine caméra-relative de l'effet (centre de la cible)
     * @param dx,dz    direction horizontale normalisée attaquant → cible
     * @param t        progression 0..1 sur la durée visuelle de l'effet
     * @param alpha    opacité de base fournie par le manager (0..255)
     * @param salt     graine stable de l'instance, pour un tirage déterministe
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        if (variant == SANDSTORM) renderSandstorm(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else if (variant == TEMPEST) renderTempest(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else renderZephyr(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
    }

    // ============================================================================================
    // 1. ZÉPHYR — aspiration, tresse, trois lances, couronne de compression, éclosion
    // ============================================================================================

    /**
     * Chorégraphie (durée serveur 80 ticks) :
     * <ul>
     *   <li>0,00–0,20 aspiration : trois anneaux de poussière se resserrent au sol (144 cubes) ;</li>
     *   <li>0,06–0,80 colonne : cinq brins extérieurs et trois brins intérieurs forment un entonnoir
     *       large en haut qui se resserre vers la lance (272) ;</li>
     *   <li>0,10–0,70 formation : la lance principale descend (30 sections + 14 cubes de pointe,
     *       8 ailettes, deux hélices de 26, dix plumes) ;</li>
     *   <li>0,22–0,46 descente : trois lances satellites en trident (3 × 34) ;</li>
     *   <li>0,44–0,58 percement : couronne de compression au sol (112) ;</li>
     *   <li>0,48–0,92 éclosion : éclats (40), annelets (72), brume (12) ;</li>
     *   <li>0,84–1,00 dissipation totale.</li>
     * </ul>
     * Pic de charge : environ 840 cubes par image.
     */
    private static void renderZephyr(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.04f) * (1.0f - smooth((t - 0.86f) / 0.14f)));
        if (a <= 4) return;
        float suck = smooth(t / 0.20f);
        float drop = easeOut((t - 0.22f) / 0.24f);
        float pierce = smooth((t - 0.44f) / 0.09f);
        float blow = smooth((t - 0.50f) / 0.40f);
        float fade = 1.0f - smooth((t - 0.84f) / 0.16f);
        float impact = 1.0f - smooth(Math.abs(t - 0.49f) / 0.085f);
        float core = smooth(t / 0.16f) * (1.0f - smooth((t - 0.70f) / 0.30f));
        float shards = smooth((t - 0.48f) / 0.20f) * (1.0f - smooth((t - 0.88f) / 0.12f));

        // ---- 1. Sol : trois anneaux de poussière aspirés vers la base (144 cubes) ----
        for (int ring = 0; ring < 3; ring++) {
            float baseRadius = 0.90f + ring * 0.72f;
            float spinDir = (ring % 2 == 0) ? 1.0f : -1.0f;
            for (int i = 0; i < 48; i++) {
                float h = hash(salt, 2100 + ring * 137 + i * 13);
                float ang = i * TAU / 48.0f + t * (5.5f + ring * 1.4f) * spinDir + ring * 0.31f;
                float radius = baseRadius * (0.90f + 0.18f * Mth.sin(t * 9.0f + i * 0.7f))
                        * (0.34f + 0.66f * suck);
                float f = Mth.cos(ang) * radius;
                float l = Mth.sin(ang) * radius * 0.95f;
                float y = -1.15f + h * 0.06f + 0.10f * suck * Mth.sin(ang * 2.0f + t * 6.0f);
                float size = (0.046f + 0.028f * h) * (1.0f + ring * 0.20f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        size * 1.75f, size * 0.45f, size * 1.20f,
                        ang, 0.0f, 0.0f,
                        color(AIR_DEEP, PALE, 0.26f + h * 0.36f + ring * 0.06f),
                        Math.round(a * fade * suck * (0.34f + 0.28f * h)));
            }
        }

        // ---- 2. Colonne d'air : entonnoir large en haut, resserré vers la lance (272 cubes) ----
        for (int layer = 0; layer < 2; layer++) {
            int strands = (layer == 0) ? 5 : 3;
            int levels = (layer == 0) ? 40 : 24;
            for (int strand = 0; strand < strands; strand++) {
                float spinDir = (layer == 0)
                        ? ((strand % 2 == 0) ? 1.0f : -1.0f)
                        : ((strand % 2 == 0) ? -1.0f : 1.0f);
                float phase = strand * TAU / strands + layer * 0.7f;
                for (int level = 0; level < levels; level++) {
                    float u = level / (float) (levels - 1);
                    float rise = smooth((t - 0.06f - u * 0.10f) / 0.24f) * (1.0f - smooth((t - 0.80f) / 0.18f));
                    if (rise <= 0.01f) continue;
                    float h = hash(salt, 2400 + layer * 601 + strand * 173 + level * 29);
                    float rTop = (layer == 0) ? 1.00f : 0.58f;
                    float rLow = (layer == 0) ? 0.46f : 0.22f;
                    float radius = (rTop + (rLow - rTop) * u) * (1.0f + 0.10f * Mth.sin(t * 8.0f + strand + u * 7.0f));
                    float ang = phase + u * 12.5f * spinDir + t * (11.0f + strand * 0.6f) * spinDir;
                    float y = 3.30f - u * 1.96f + 0.05f * Mth.sin(t * 6.0f + u * 12.0f);
                    float size = ((layer == 0 ? 0.090f : 0.066f) - u * (layer == 0 ? 0.046f : 0.026f))
                            * (0.84f + h * 0.30f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                            size * 1.15f, size, size * 1.15f,
                            color(AIR_DEEP, (layer == 0) ? PALE : WHITE, 0.30f + u * 0.46f + h * 0.18f),
                            Math.round(a * rise * (layer == 0
                                    ? (0.22f + 0.22f * h)
                                    : (0.18f + 0.18f * h))));
                }
            }
        }

        // ---- 3. Lance principale : axe vertical, pointe en bas, âme lumineuse ----
        float tipY = Mth.lerp(drop, 0.78f, -0.86f) - pierce * 0.22f;
        float wobbleX = Mth.cos(t * 2.0f) * 0.02f;
        float wobbleZ = Mth.sin(t * 2.0f) * 0.02f;
        for (int seg = 0; seg < 30; seg++) {
            float u0 = seg / 29.0f;
            float y0 = tipY + 0.14f + u0 * 2.10f;
            float half = 0.130f - u0 * 0.062f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, wobbleX, wobbleZ, y0,
                    half, half * 1.02f, half,
                    t * 3.0f + u0 * 2.0f, 0.0f, 0.0f,
                    color(PALE, WHITE, 0.22f + u0 * 0.70f),
                    Math.round(a * 1.02f));
        }
        for (int tip = 0; tip < 14; tip++) {
            float u0 = tip / 14.0f;
            float y0 = tipY - 0.02f - u0 * 0.34f;
            float half = 0.152f * (1.0f - u0 * 0.95f) + 0.010f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, y0,
                    half, half, half, 0.0f, 0.0f, 0.0f,
                    color(GLASS, WHITE, 0.55f + u0 * 0.42f),
                    Math.round(a * (0.96f - u0 * 0.16f)));
        }
        for (int fin = 0; fin < 8; fin++) {
            float fa = fin * TAU / 8.0f + t * 1.4f;
            float reach = 0.30f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(fa) * reach, Mth.sin(fa) * reach, tipY + 2.28f,
                    0.058f, 0.058f, 0.058f, fa, 0.0f, 0.0f,
                    (fin % 2 == 0) ? PALE : AZURE,
                    Math.round(a * 0.90f));
        }
        for (int helix = 0; helix < 2; helix++) {
            float spinDir = (helix == 0) ? 1.0f : -1.0f;
            for (int step = 0; step < 26; step++) {
                float u0 = step / 25.0f;
                float ra = u0 * TAU * 2.4f * spinDir + t * 22.0f * spinDir + helix * 1.7f;
                float y0 = tipY + 0.20f + u0 * 2.20f;
                float r0 = (0.30f + u0 * 0.10f) + blow * (0.80f + u0 * 0.40f);
                float size = 0.026f + 0.008f * hash(salt, 4100 + step * 17 + helix * 7);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ra) * r0, Mth.sin(ra) * r0, y0,
                        size, size, size * 3.0f, ra, 0.0f, 0.0f,
                        (helix == 0) ? AZURE : GLASS,
                        Math.round(a * fade * (1.0f - blow * 0.40f) * (0.60f - u0 * 0.24f)));
            }
        }
        for (int plume = 0; plume < 10; plume++) {
            float h = hash(salt, 4300 + plume * 41);
            float drift = blow * (0.70f + h * 1.80f);
            float pa = plume * TAU / 10.0f + t * 2.2f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(pa) * (0.34f + drift * 1.05f),
                    Mth.sin(pa) * (0.34f + drift * 1.05f),
                    tipY + 1.10f + h * 1.20f + drift * 0.55f,
                    0.030f, 0.125f, 0.024f, pa, 0.0f, 0.0f,
                    (plume % 3 == 0) ? WHITE : PALE,
                    Math.round(a * fade * (1.0f - blow) * (0.42f + h * 0.28f)));
        }

        // ---- 4. Trois lances satellites en trident autour de l'axe (3 × 34 cubes) ----
        for (int spear = 0; spear < 3; spear++) {
            float ang = spear * 2.0943951f + 0.60f;
            float rad = 0.58f * (1.0f - drop * 0.55f);
            float tipY2 = tipY + 0.30f + spear * 0.06f;
            float lat = Mth.cos(ang) * rad;
            float fwd = Mth.sin(ang) * rad;
            for (int seg = 0; seg < 14; seg++) {
                float u0 = seg / 14.0f;
                float y0 = tipY2 + 0.10f + u0 * 1.30f;
                float half = 0.072f - u0 * 0.032f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, fwd, lat, y0,
                        half, half, half, ang, 0.0f, 0.0f,
                        color(PALE, WHITE, 0.30f + u0 * 0.55f),
                        Math.round(a * 0.92f));
            }
            for (int tip = 0; tip < 8; tip++) {
                float u0 = tip / 8.0f;
                float y0 = tipY2 - 0.01f - u0 * 0.24f;
                float half = 0.086f * (1.0f - u0 * 0.92f) + 0.008f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, fwd, lat, y0,
                        half, half, half, 0.0f, 0.0f, 0.0f,
                        color(GLASS, WHITE, 0.55f + u0 * 0.40f),
                        Math.round(a * 0.92f));
            }
            for (int step = 0; step < 12; step++) {
                float u0 = step / 11.0f;
                float ra = u0 * TAU * 1.8f + t * 18.0f * ((spear % 2 == 0) ? 1.0f : -1.0f) + spear;
                float y0 = tipY2 + 0.16f + u0 * 1.15f;
                float r0 = 0.16f + u0 * 0.06f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fwd + Mth.cos(ra) * r0, lat + Mth.sin(ra) * r0, y0,
                        0.020f, 0.020f, 0.062f, ra, 0.0f, 0.0f,
                        AZURE,
                        Math.round(a * fade * (0.42f - u0 * 0.16f)));
            }
        }

        // ---- 5. Couronne de compression au sol : deux anneaux de cubes + éclats (112) ----
        if (impact > 0.02f) {
            float reach = 0.34f + (1.0f - impact) * 1.75f;
            for (int i = 0; i < 48; i++) {
                float ang = i * TAU / 48.0f;
                float size = (i % 4 == 0) ? 0.050f : 0.026f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * reach, Mth.sin(ang) * reach, -0.55f,
                        size, size, size, ang, 0.0f, 0.0f,
                        (i % 4 == 0) ? WHITE : GLASS,
                        Math.round(a * impact * 0.94f));
            }
            float reach2 = reach * 0.72f;
            for (int i = 0; i < 36; i++) {
                float ang = i * TAU / 36.0f;
                float size = (i % 3 == 0) ? 0.032f : 0.020f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * reach2, Mth.sin(ang) * reach2, -1.10f,
                        size, size, size, ang, 0.0f, 0.0f,
                        (i % 3 == 0) ? PALE : GLASS,
                        Math.round(a * impact * 0.62f));
            }
            for (int pit = 0; pit < 28; pit++) {
                float h = hash(salt, 5100 + pit * 53);
                float pa = pit * TAU / 28.0f + h * 0.5f;
                float pr = reach * (0.55f + h * 0.75f);
                float size = 0.046f + 0.026f * h;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(pa) * pr, Mth.sin(pa) * pr, -0.60f + (h - 0.5f) * 0.55f,
                        size, size, size, pa, 0.0f, 0.0f,
                        (pit % 3 == 0) ? WHITE : ((pit % 3 == 1) ? GLASS : PALE),
                        Math.round(a * impact * (0.48f + h * 0.32f)));
            }
        }

        // ---- 6. Éclats emportés par l'éclosion (40 cubes) ----
        for (int shard = 0; shard < 40; shard++) {
            float h = hash(salt, 5600 + shard * 67);
            float h2 = hash(salt, 5700 + shard * 41);
            float pa = shard * 2.3999632f + t * (1.6f + h * 2.2f);
            float pr = 0.42f + blow * (1.15f + h * 1.85f);
            float y = -0.55f + (h2 - 0.30f) * 1.40f + blow * (0.45f + h * 1.10f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(pa) * pr, Mth.sin(pa) * pr, y,
                    0.028f + 0.012f * h2, 0.110f + 0.065f * h, 0.024f,
                    pa, 0.0f, 0.0f,
                    (shard % 4 == 0) ? WHITE : ((shard % 3 == 0) ? GLASS : PALE),
                    Math.round(a * shards * (1.0f - blow * 0.40f) * (0.46f + 0.30f * h)));
        }

        // ---- 7. Brume périphérique (12 cubes très discrets) ----
        for (int mist = 0; mist < 12; mist++) {
            float h = hash(salt, 6100 + mist * 43);
            float pa = mist * TAU / 12.0f + t * 3.2f + h;
            float pr = 2.05f + h * 0.90f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(pa) * pr, Mth.sin(pa) * pr, -0.85f + h * 2.60f + blow * 0.30f,
                    0.130f, 0.130f, 0.130f, pa, 0.0f, 0.0f,
                    AIR, Math.round(a * fade * (0.06f + 0.06f * h)));
        }

        // ---- 8. Quatre annelets secondaires le long de la colonne (72 cubes) ----
        for (int cell = 0; cell < 4; cell++) {
            float ca = cell * 2.0943951f + 0.70f;
            float cf = Mth.cos(ca) * (1.00f + 0.30f * cell);
            float cl = Mth.sin(ca) * (1.00f + 0.30f * cell);
            float cyy = 0.70f + cell * 0.62f + impact * 0.20f;
            float scale = (0.13f + impact * 0.20f) * (1.0f - blow * 0.55f);
            if (scale <= 0.02f) continue;
            for (int i = 0; i < 18; i++) {
                float h = hash(salt, 6800 + cell * 97 + i * 19);
                float ang = i * TAU / 18.0f + t * 16.0f * ((cell % 2 == 0) ? 1.0f : -1.0f);
                float rr = scale * (0.75f + 0.45f * h);
                float size = 0.017f + 0.013f * h;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        cf + Mth.cos(ang) * rr, cl + Mth.sin(ang) * rr, cyy,
                        size, size, size, ang, 0.0f, 0.0f,
                        color(AZURE, WHITE, 0.35f + h * 0.45f),
                        Math.round(a * impact * (0.34f + 0.28f * h)));
            }
        }
    }

    // ============================================================================================
    // 2. SANDSTORM — dune, cinq bras, sept rubans, trois mini-tornades, grains
    // ============================================================================================

    /**
     * Chorégraphie (durée 140 ticks) : la dune se forme (0,00–0,30), les bras montent (0,06–0,60),
     * les grains et les rubans tourbillonnent tant que la tempête vit, puis tout retombe (0,72–1,00).
     * Pic de charge : environ 1 240 cubes par image.
     */
    private static void renderSandstorm(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.07f) * (1.0f - smooth((t - 0.74f) / 0.26f)));
        if (a <= 4) return;
        float grow = smooth(t / 0.30f) * (1.0f - smooth((t - 0.70f) / 0.30f));
        float spin = t * 9.0f;
        float settle = smooth((t - 0.72f) / 0.28f);
        float crestWave = Mth.sin(t * 7.0f);

        // ---- 1. Dune : cinq strates elliptiques de 40 cubes, crête asymétrique (200 cubes) ----
        for (int layer = 0; layer < 5; layer++) {
            float u = layer / 4.0f;
            float radius = (1.46f - u * 0.86f) * Math.max(0.06f, grow);
            if (radius <= 0.02f) continue;
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 6100 + layer * 71 + i * 19);
                float ang = i * TAU / 40.0f + layer * 0.22f + spin * (0.22f + layer * 0.03f);
                float f = Mth.cos(ang) * radius * (1.0f + 0.20f * u);
                float l = Mth.sin(ang) * radius * 0.72f;
                float crest = Math.max(0.0f, Mth.cos(ang - 0.55f)) * (0.24f - u * 0.10f) * grow;
                float ripple = 0.045f * Mth.sin(ang * 6.0f + t * 9.0f + layer) * grow;
                float y = -1.14f + u * 0.42f * grow + crest + ripple - settle * (0.24f + h * 0.22f);
                float size = (0.148f - u * 0.038f) * (0.84f + h * 0.30f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        size * 1.24f, size * 0.60f, size,
                        ang + t * 0.30f, h * 0.26f, 0.0f,
                        color(SAND_DARK, SAND_LIGHT, 0.22f + u * 0.44f + h * 0.18f),
                        Math.round(a * grow * (0.50f + 0.26f * h)));
            }
        }

        // ---- 2. Cinq bras spiralés qui montent en se resserrant (130 cubes) ----
        for (int arm = 0; arm < 5; arm++) for (int level = 0; level < 26; level++) {
            float u = level / 25.0f;
            float reveal = smooth((t - 0.05f - u * 0.12f) / 0.20f) * (1.0f - settle);
            if (reveal <= 0.01f) continue;
            float h = hash(salt, 6500 + arm * 149 + level * 23);
            float ang = arm * 1.2566371f + u * 8.4f + t * 13.0f;
            float radius = (1.16f - u * 0.72f) * (0.90f + 0.14f * Mth.sin(t * 9.0f + u * 12.0f));
            float f = Mth.cos(ang) * radius;
            float l = Mth.sin(ang) * radius;
            float y = -0.92f + u * 2.78f;
            float size = (0.170f - u * 0.070f) * (0.84f + h * 0.32f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size * 1.32f, size, size * 1.16f,
                    ang, u * 0.75f, 0.0f,
                    (level % 5 == 0) ? DUST : SAND_LIGHT,
                    Math.round(a * reveal * (0.32f + 0.30f * (1.0f - u))));
        }

        // ---- 3. Sept rubans horizontaux tournant à des vitesses différentes (336 cubes) ----
        for (int band = 0; band < 7; band++) for (int i = 0; i < 48; i++) {
            float u = i / 47.0f;
            float h = hash(salt, 6900 + band * 83 + i * 11);
            float ang = u * 5.2f + band * 0.90f + t * (9.5f + band * 1.3f) * ((band % 2 == 0) ? 1.0f : -1.0f);
            float radius = 1.02f + band * 0.15f + 0.12f * Mth.sin(u * 9.0f + t * 8.0f);
            float f = Mth.cos(ang) * radius;
            float l = Mth.sin(ang) * radius;
            float y = -0.92f + band * 0.30f + u * 0.34f + settle * 0.32f * h;
            float size = 0.022f + 0.013f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size * 2.8f, size * 0.48f, size * 0.72f,
                    ang, 0.20f, 0.0f,
                    (band % 3 == 0) ? DUST : SAND,
                    Math.round(a * (1.0f - settle) * (0.26f + 0.20f * h)));
        }

        // ---- 4. Trois mini-tornades satellites (90 cubes) ----
        for (int cell = 0; cell < 3; cell++) {
            float ca = cell * 2.0943951f + 1.30f;
            float cf = Mth.cos(ca) * (1.55f + 0.18f * cell);
            float cl = Mth.sin(ca) * (1.55f + 0.18f * cell);
            float born = smooth((t - 0.16f - cell * 0.05f) / 0.24f) * (1.0f - settle);
            if (born <= 0.01f) continue;
            for (int i = 0; i < 30; i++) {
                float u = i / 29.0f;
                float h = hash(salt, 7300 + cell * 101 + i * 17);
                float ang = u * 9.0f + t * 18.0f + cell;
                float rr = (0.42f - u * 0.30f) * (0.85f + 0.25f * h);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        cf + Mth.cos(ang) * rr, cl + Mth.sin(ang) * rr,
                        -0.95f + u * 1.70f + 0.05f * Mth.sin(t * 8.0f + i),
                        0.026f + 0.016f * h, 0.026f + 0.016f * h, 0.026f + 0.016f * h,
                        ang, 0.0f, 0.0f,
                        (i % 6 == 0) ? DUST : SAND_LIGHT,
                        Math.round(a * born * (0.30f + 0.26f * h)));
            }
        }

        // ---- 5. Grains individuels : advection hélicoïdale puis extinction (160 cubes) ----
        for (int grain = 0; grain < 160; grain++) {
            float h = hash(salt, 7500 + grain * 47);
            float h2 = hash(salt, 7700 + grain * 61);
            float cycle = fract(t * (1.10f + h * 0.90f) + grain * 0.6180339f);
            float ang = grain * 2.3999632f + spin * (0.8f + h2 * 0.7f);
            float radius = 0.28f + cycle * 2.10f;
            float f = Mth.cos(ang) * radius;
            float l = Mth.sin(ang) * radius;
            float y = Mth.lerp(cycle, -1.08f, 2.45f) + h2 * 0.30f;
            float size = 0.024f + 0.024f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size, size, size, ang, cycle * 3.0f, 0.0f,
                    (grain % 5 == 0) ? DUST : (grain % 3 == 0 ? SAND_LIGHT : SAND),
                    Math.round(a * (1.0f - cycle) * (0.42f + 0.28f * h)));
        }

        // ---- 6. Voile de poussière : trente-six cubes larges et translucides (36 cubes) ----
        for (int veil = 0; veil < 36; veil++) {
            float h = hash(salt, 8000 + veil * 37);
            float ang = veil * TAU / 36.0f + t * 4.0f + h;
            float radius = 1.30f + h * 0.95f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -0.80f + h * 2.60f + settle * 0.40f + 0.10f * crestWave,
                    0.150f + 0.060f * h, 0.120f, 0.150f + 0.060f * h,
                    ang, h * TAU, 0.0f,
                    SAND_DARK,
                    Math.round(a * (0.08f + 0.08f * h)));
        }

        // ---- 7. Éclats de sable arrachés à la crête (50 cubes) ----
        float fling = smooth((t - 0.34f) / 0.26f) * (1.0f - settle);
        for (int shard = 0; shard < 50; shard++) {
            float h = hash(salt, 8400 + shard * 59);
            float h2 = hash(salt, 8500 + shard * 43);
            float ang = shard * 2.3999632f + t * (2.0f + h * 2.4f);
            float radius = 0.55f + fling * (0.90f + h * 1.50f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -0.75f + h2 * 1.85f + fling * (0.35f + h * 0.75f),
                    0.030f + 0.014f * h2, 0.105f + 0.060f * h, 0.026f,
                    ang, h2 * 2.2f, ang * 0.6f,
                    (shard % 4 == 0) ? DUST : SAND_LIGHT,
                    Math.round(a * fling * (0.34f + 0.26f * h)));
        }
    }

    // ============================================================================================
    // 3. TEMPEST — deux entonnoirs contra-rotatifs, œil, débris, mur de nuages
    // ============================================================================================

    /**
     * Chorégraphie (durée 120 ticks) : ouverture des deux entonnoirs (0,00–0,26), l'œil s'ouvre
     * (0,18–0,40), les débris orbitent (0,25–0,78), éclatement sommital (0,62–0,86), effondrement
     * (0,78–1,00). Pic de charge : environ 1 250 cubes par image.
     */
    private static void renderTempest(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.06f) * (1.0f - smooth((t - 0.80f) / 0.20f)));
        if (a <= 4) return;
        float open = smooth(t / 0.26f);
        float collapse = smooth((t - 0.78f) / 0.22f);
        float eye = smooth((t - 0.18f) / 0.22f) * (1.0f - collapse);
        float spin = t * 12.0f;
        float shrink = 1.0f - collapse * 0.55f;
        float burst = smooth((t - 0.62f) / 0.38f);

        // ---- 1. Deux entonnoirs de huit brins contra-rotatifs (352 cubes) ----
        for (int funnel = 0; funnel < 2; funnel++) {
            float spinDir = (funnel == 0) ? 1.0f : -1.0f;
            float baseRadius = (funnel == 0) ? 0.26f : 1.02f;
            float spread = (funnel == 0) ? 1.08f : 1.52f;
            for (int strand = 0; strand < 8; strand++) {
                float phase = strand * TAU / 8.0f;
                for (int level = 0; level < 22; level++) {
                    float u = level / 21.0f;
                    float h = hash(salt, 8100 + funnel * 401 + strand * 97 + level * 13);
                    float ang = phase + u * 7.4f * spinDir + spin * spinDir * (1.0f - u * 0.40f);
                    float radius = (baseRadius + u * spread) * shrink
                            * (0.90f + 0.14f * h) * open;
                    float y = -1.15f + u * 3.80f;
                    float size = (0.096f + u * 0.028f) * (0.84f + h * 0.30f);
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                            size, size, size,
                            ang, u * 0.50f * spinDir, ang * 0.30f,
                            color((funnel == 0) ? AIR : AIR_DEEP,
                                    (funnel == 0) ? WHITE : PALE,
                                    (funnel == 0 ? 0.40f : 0.28f) + u * 0.46f + h * 0.18f),
                            Math.round(a * open * (1.0f - collapse * 0.35f)
                                    * (0.36f + 0.28f * (1.0f - u * (funnel == 0 ? 0.5f : 1.0f)))));
                }
            }
        }

        // ---- 2. Noyau : colonne centrale torsadée (34 cubes) ----
        for (int step = 0; step < 34; step++) {
            float u = step / 33.0f;
            float h = hash(salt, 8600 + step * 29);
            float ang = t * 9.0f + u * 5.0f;
            float radius = 0.10f + 0.06f * Mth.sin(t * 12.0f + u * 6.0f);
            float size = (0.078f - u * 0.030f) * (0.84f + 0.24f * Mth.sin(t * 16.0f + step));
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -1.05f + u * 3.70f,
                    size, size * 1.50f, size,
                    ang, u * 1.1f, 0.0f,
                    color(GLASS, WHITE, 0.35f + h * 0.50f),
                    Math.round(a * open * (1.0f - collapse * 0.60f) * (0.40f + 0.32f * h)));
        }

        // ---- 3. Œil calme : anneau de 44 segments, dix rayons de neuf cubes (134 cubes) ----
        if (eye > 0.02f) {
            float eyeY = 2.74f;
            float eyeR = (1.16f + 0.05f * Mth.sin(t * 9.0f)) * open;
            for (int i = 0; i < 44; i++) {
                float a0 = i * TAU / 44.0f;
                float a1 = (i + 1) * TAU / 44.0f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a0) * eyeR, Mth.sin(a0) * eyeR, eyeY,
                        Mth.cos(a1) * eyeR, Mth.sin(a1) * eyeR, eyeY,
                        (i % 4 == 0) ? 0.030f : 0.016f,
                        (i % 4 == 0) ? WHITE : GLASS, WHITE,
                        Math.round(a * eye * ((i % 4 == 0) ? 0.94f : 0.64f)));
            }
            for (int spoke = 0; spoke < 10; spoke++) {
                float sa = spoke * TAU / 10.0f + spin * 0.35f;
                for (int step = 1; step <= 9; step++) {
                    float r0 = eyeR * (0.16f + step * 0.092f);
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(sa) * r0, Mth.sin(sa) * r0, eyeY,
                            0.026f, 0.026f, 0.026f,
                            sa, 0.0f, 0.0f,
                            (step % 3 == 0) ? WHITE : AZURE,
                            Math.round(a * eye * (0.24f + 0.30f * (1.0f - step / 9.0f))));
                }
            }
        }

        // ---- 4. Débris arrachés en orbite croisée (26 cubes) ----
        for (int debris = 0; debris < 26; debris++) {
            float h = hash(salt, 8800 + debris * 59);
            float h2 = hash(salt, 8900 + debris * 71);
            float orbit = 1.00f + h * 1.35f;
            float speed = 1.15f + h2 * 1.10f;
            float ang = debris * 2.3999632f + t * speed * 6.0f;
            float radius = orbit * (1.0f - collapse * 0.60f) * open;
            float size = 0.058f + h2 * 0.060f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -0.85f + h * 3.10f + 0.18f * Mth.sin(t * 6.0f + debris),
                    size, size * 0.85f, size,
                    ang, h2 * 2.0f, ang * 0.4f,
                    (debris % 3 == 0) ? SLATE : (debris % 3 == 1 ? SLATE_DARK : STEEL),
                    Math.round(a * open * (1.0f - collapse * 0.40f) * (0.50f + 0.30f * h)));
        }

        // ---- 5. Anneau de sol : soixante segments qui se resserrent (60 cubes) ----
        float groundR = (2.55f * open * shrink) * (0.94f + 0.06f * Mth.sin(t * 7.0f));
        for (int i = 0; i < 60; i++) {
            float a0 = i * TAU / 60.0f;
            float a1 = (i + 1) * TAU / 60.0f;
            float r0 = groundR * (0.96f + 0.06f * Mth.sin(i * 1.7f + t * 7.0f));
            float r1 = groundR * (0.96f + 0.06f * Mth.sin((i + 1) * 1.7f + t * 7.0f));
            link(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(a0) * r0, Mth.sin(a0) * r0, -1.16f,
                    Mth.cos(a1) * r1, Mth.sin(a1) * r1, -1.16f,
                    (i % 5 == 0) ? 0.026f : 0.013f,
                    (i % 5 == 0) ? PALE : AIR, GLASS,
                    Math.round(a * open * 0.52f * (1.0f - collapse * 0.30f)));
        }

        // ---- 6. Mur de nuages : deux bandes de six colonnes autour des entonnoirs (240 cubes) ----
        for (int band = 0; band < 2; band++) for (int column = 0; column < 6; column++) {
            float colAng = column * TAU / 6.0f + band * 0.52f + spin * 0.22f;
            float colRadius = (band == 0) ? 1.75f : 2.35f;
            for (int step = 0; step < 20; step++) {
                float u = step / 19.0f;
                float h = hash(salt, 9200 + band * 311 + column * 53 + step * 11);
                float wobble = 0.16f * Mth.sin(t * 7.0f + column + u * 5.0f);
                float radius = (colRadius + wobble) * open * (1.0f - collapse * 0.30f);
                float y = -0.55f + u * 3.15f + 0.10f * Mth.sin(t * 5.0f + step);
                float size = (0.115f - u * 0.030f) * (0.80f + h * 0.34f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(colAng) * radius, Mth.sin(colAng) * radius, y,
                        size, size * 1.10f, size,
                        colAng, u * 0.40f, colAng * 0.3f,
                        color(AIR_DEEP, PALE, 0.24f + u * 0.42f + h * 0.18f),
                        Math.round(a * open * (0.16f + 0.16f * h)));
            }
        }

        // ---- 7. Poussière périphérique : cent vingt grains soufflés (120 cubes) ----
        for (int grain = 0; grain < 120; grain++) {
            float h = hash(salt, 9600 + grain * 41);
            float h2 = hash(salt, 9700 + grain * 53);
            float cycle = fract(t * (0.90f + h * 1.10f) + grain * 0.6180339f);
            float ang = grain * 2.3999632f + t * (2.4f + h2 * 2.6f);
            float radius = (1.30f + cycle * 2.10f) * (1.0f - collapse * 0.35f) * open;
            float size = 0.020f + 0.018f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -1.05f + cycle * 2.90f + h2 * 0.25f,
                    size, size, size,
                    ang, cycle * 2.4f, 0.0f,
                    (grain % 4 == 0) ? WHITE : (grain % 2 == 0 ? PALE : AIR),
                    Math.round(a * (1.0f - cycle) * (0.24f + 0.20f * h)));
        }

        // ---- 8. Éclatement sommital (40 cubes) ----
        if (burst > 0.02f) {
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 9900 + i * 43);
                float ang = i * TAU / 40.0f + spin;
                float radius = (0.45f + burst * (1.25f + h * 1.15f)) * open;
                float y = 2.92f + burst * (0.45f + h * 0.95f);
                float size = 0.046f + h * 0.038f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                        size, size * 0.90f, size,
                        ang, h * 1.6f, 0.0f,
                        (i % 4 == 0) ? WHITE : (i % 2 == 0 ? GLASS : PALE),
                        Math.round(a * burst * (0.42f + 0.26f * h) * (1.0f - collapse * 0.50f)));
            }
        }

        // ---- 9. Effondrement : la matière retombe vers le centre (40 cubes) ----
        if (collapse > 0.20f) {
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 10200 + i * 37);
                float ang = i * TAU / 40.0f + spin * 2.0f;
                float radius = (1.0f - collapse) * 1.45f * (0.55f + h * 0.70f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                        1.90f + (h - 0.5f) * 0.60f - collapse * 1.30f,
                        0.048f, 0.048f, 0.048f,
                        ang, collapse * 2.0f, 0.0f,
                        WHITE,
                        Math.round(a * collapse * 0.58f));
            }
        }
    }

    // ============================================================================================
    // Outils géométriques — aucune allocation, aucun accès au niveau
    // ============================================================================================

    /** Cube aligné sur les axes, exprimé en repère (avant, latéral, hauteur). */
    private static void box(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + y, z, hf, hy, hl, rgb, alpha);
    }

    /** Cube orienté : mêmes conventions que les autres signatures (lacet, tangage, roulis). */
    private static void rot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl,
                            float yaw, float pitch, float roll, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy + y, z,
                hf, hy, hl, yaw, pitch, roll, rgb, alpha);
    }

    /** Segment voxel : longueur réelle, orientation déduite des extrémités. */
    private static void link(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f0, float l0, float y0,
                             float f1, float l1, float y1,
                             float half, int rgb, int tip, int alpha) {
        float df = f1 - f0, dl = l1 - l0, dy = y1 - y0;
        float length = Mth.sqrt(df * df + dl * dl + dy * dy);
        if (length < 0.001f || alpha <= 0) return;
        float sx = dx * df - dz * dl, sz = dz * df + dx * dl;
        float yaw = (float) Math.atan2(sx, sz);
        float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
        rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                (f0 + f1) * 0.5f, (l0 + l1) * 0.5f, (y0 + y1) * 0.5f,
                half, length * 0.56f, half, yaw, pitch, 0.0f,
                color(rgb, tip, 0.32f), alpha);
    }

    private static int color(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, clamp(u));
    }

    private static float clamp(float value) { return Mth.clamp(value, 0.0f, 1.0f); }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float easeOut(float x) {
        x = clamp(x);
        return 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x);
    }

    private static float fract(float value) { return value - Mth.floor(value); }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
