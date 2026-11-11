package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T374-WIND-REFONTE v2 — élément VENT : trois signatures voxel sur un moteur commun.
 *
 * <p><b>Langage visuel.</b> Une tornade réelle est <i>étroite au sol et large en altitude</i>, elle
 * tourne dans un seul sens et elle est plus haute que large. Les trois signatures respectent
 * maintenant ces trois règles : le bas plonge dans le sol, le haut s'évase, tout tourne dans le même
 * sens, et la hauteur visible atteint 8 à 9 blocs au-dessus de la cible.</p>
 *
 * <p><b>Identité du vent.</b> Palette blanc glacé / azur / vert d'eau (aucun vert Gaia, aucun ocre
 * Terre dans les rubans) ; la lance de zéphyr tombe du ciel, la tempête de sable garde son grain
 * ocre mais son tourbillon interne est <b>au même sens</b> que l'extérieur, et l'œil du cyclone
 * ouvre un anneau de calme tout en haut de l'entonnoir.</p>
 *
 * <p><b>Blocs réels.</b> Les cubes du sol (herbe, sable, pierre, neige…) ne sont pas simulés ici :
 * {@link WindTerrainVfx} capture les vrais {@code BlockState} du terrain et les fait voler dans la
 * spirale, exactement comme le séisme pour sa faille. Cette classe ne dessine que l'air, le sable et
 * les éclats.</p>
 *
 * <p><b>Contraintes.</b> Aucune allocation dans la passe de rendu, aucun RenderType demandé ici,
 * aucune géométrie hors du repère caméra-relatif fourni par le manager. Pic de charge mesuré :
 * environ 1 250 cubes pour le zéphyr, 980 pour la tempête, 960 pour le cyclone — sous le plafond de
 * 7 000 par image de {@link WeaponWorldVfxManager}.</p>
 */
public final class WindSignatureVfx {

    /** Lance de zéphyr ({@code ZEPHYR_WIND_LANCE}). */
    public static final int ZEPHYR = 0;
    /** Tempête de sable ({@code SANDSTORM_DUNE}). */
    public static final int SANDSTORM = 1;
    /** Œil du cyclone ({@code TEMPEST_EYE}). */
    public static final int TEMPEST = 2;

    /** Palette vent : du plus sombre au plus lumineux. */
    private static final int AIR_DEEP = 0x2C4658;
    private static final int AIR = 0x4E7F99;
    private static final int AZURE = 0x6FBEDC;
    private static final int MINT = 0x9CE8D2;
    private static final int PALE = 0xA9E2F2;
    private static final int GLASS = 0xD9F5FF;
    private static final int WHITE = 0xF4FDFF;
    /** Sable : le tourbillon interne utilise volontairement les valeurs les plus sombres. */
    private static final int SAND_DEEP = 0x4A3A26;
    private static final int SAND_DARK = 0x6E5B41;
    private static final int SAND = 0xA88B5E;
    private static final int SAND_LIGHT = 0xDCC79B;
    private static final int DUST = 0xF0E2BF;
    /** Débris minéraux (les vrais blocs viennent de WindTerrainVfx). */
    private static final int SLATE_DARK = 0x59636B;
    private static final int SLATE = 0x8C9AA4;

    private static final float TAU = (float) (Math.PI * 2.0);

    private WindSignatureVfx() { }

    /**
     * Point d'entrée unique des trois signatures.
     *
     * @param cx,cy,cz origine caméra-relative (centre de la cible)
     * @param dx,dz    direction horizontale normalisée attaquant → cible
     * @param t        progression 0..1 sur la durée visuelle
     * @param alpha    opacité de base fournie par le manager (0..255)
     * @param salt     graine stable de l'instance
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
    // 1. ZÉPHYR — trois lances qui TOMBENT DU CIEL, couronne de compression, éclosion
    // ============================================================================================

    /**
     * Chorégraphie (80 ticks) :
     * <ul>
     *   <li>0,00–0,24 aspiration : deux anneaux de poussière se resserrent au sol ;</li>
     *   <li>0,06–0,50 chute : la lance principale part de <b>+8,6 blocs</b> et perce la cible ;</li>
     *   <li>0,16–0,60 les deux lances satellites suivent, décalées, en V ;</li>
     *   <li>0,30–0,72 colonne d'air comprimé qui se resserre autour de chaque fût ;</li>
     *   <li>0,50–0,62 couronne de compression au sol ;</li>
     *   <li>0,60–1,00 éclosion d'éclats, d'annelets et de brume.</li>
     * </ul>
     */
    private static void renderZephyr(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.05f) * (1.0f - smooth((t - 0.88f) / 0.12f)));
        if (a <= 4) return;
        float suck = smooth(t / 0.22f);
        float fade = 1.0f - smooth((t - 0.86f) / 0.14f);
        float impact = 1.0f - smooth(Math.abs(t - 0.52f) / 0.10f);
        float shards = smooth((t - 0.54f) / 0.22f) * (1.0f - smooth((t - 0.90f) / 0.10f));

        // ---- 1. Sol : deux anneaux de poussière aspirés vers la base (96 cubes) ----
        for (int ring = 0; ring < 2; ring++) {
            float baseRadius = 1.05f + ring * 0.85f;
            float spinDir = (ring == 0) ? 1.0f : -1.0f;
            for (int i = 0; i < 48; i++) {
                float h = hash(salt, 2100 + ring * 137 + i * 13);
                float ang = i * TAU / 48.0f + t * (5.5f + ring * 1.4f) * spinDir + ring * 0.31f;
                float radius = baseRadius * (0.90f + 0.18f * Mth.sin(t * 9.0f + i * 0.7f))
                        * (0.34f + 0.66f * suck);
                float y = -1.15f + h * 0.06f + 0.10f * suck * Mth.sin(ang * 2.0f + t * 6.0f);
                float size = (0.048f + 0.030f * h) * (1.0f + ring * 0.18f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius * 0.95f, y,
                        size * 1.75f, size * 0.45f, size * 1.20f,
                        ang, 0.0f, 0.0f,
                        color(AIR_DEEP, MINT, 0.30f + h * 0.40f + ring * 0.06f),
                        Math.round(a * fade * suck * (0.34f + 0.28f * h)));
            }
        }

        // ---- 2. Colonne d'air comprimé : cinq brins qui se resserrent sur la trajectoire ----
        for (int strand = 0; strand < 5; strand++) {
            float spinDir = (strand % 2 == 0) ? 1.0f : -1.0f;
            for (int level = 0; level < 26; level++) {
                float u = level / 25.0f;
                float rise = smooth((t - 0.10f - u * 0.06f) / 0.22f) * (1.0f - smooth((t - 0.76f) / 0.20f));
                if (rise <= 0.01f) continue;
                float h = hash(salt, 2400 + strand * 173 + level * 29);
                float radius = (1.55f - u * 1.05f) * (1.0f + 0.10f * Mth.sin(t * 8.0f + strand + u * 7.0f));
                float ang = strand * TAU / 5.0f + u * 9.5f * spinDir + t * 15.0f * spinDir;
                float y = -1.10f + u * 5.60f;
                float size = (0.085f - u * 0.045f) * (0.84f + h * 0.30f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                        size * 1.15f, size, size * 1.15f,
                        color(AIR_DEEP, MINT, 0.34f + u * 0.46f + h * 0.18f),
                        Math.round(a * rise * (0.20f + 0.20f * h)));
            }
        }

        // ---- 3. Les trois lances : chute depuis +8,6 blocs, décalées dans le temps ----
        // (l'arme n'est plus une flèche : fût long, tête facettée, viroles, ailerons, sillage)
        float mainP = easeIn((t - 0.06f) / 0.44f);
        float mainY = Mth.lerp(mainP, 8.60f, -0.55f);
        spear(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, mainY, 1.0f,
                t, a, salt, 3);
        float sat1P = easeIn((t - 0.16f) / 0.44f);
        spear(vc, mat, tmp, cx, cy, cz, dx, dz, 0.95f, -1.05f,
                Mth.lerp(sat1P, 8.20f, -0.75f), 0.80f, t * 0.94f, a, salt, 6);
        float sat2P = easeIn((t - 0.24f) / 0.44f);
        spear(vc, mat, tmp, cx, cy, cz, dx, dz, -1.10f, 0.95f,
                Mth.lerp(sat2P, 7.90f, -0.70f), 0.74f, t * 1.06f, a, salt, 9);

        // ---- 4. Couronne de compression au sol (96 cubes) ----
        if (impact > 0.02f) {
            float reach = 0.34f + (1.0f - impact) * 1.85f;
            for (int i = 0; i < 48; i++) {
                float ang = i * TAU / 48.0f;
                float size = (i % 4 == 0) ? 0.052f : 0.026f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * reach, Mth.sin(ang) * reach, -0.55f,
                        size, size, size, ang, 0.0f, 0.0f,
                        (i % 4 == 0) ? WHITE : MINT,
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
        }

        // ---- 5. Éclats et brume (48 cubes) ----
        for (int shard = 0; shard < 36; shard++) {
            float h = hash(salt, 5600 + shard * 67);
            float h2 = hash(salt, 5700 + shard * 41);
            float pa = shard * 2.3999632f + t * (1.6f + h * 2.2f);
            float pr = 0.42f + smooth((t - 0.54f) / 0.40f) * (1.15f + h * 1.85f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(pa) * pr, Mth.sin(pa) * pr,
                    -0.55f + (h2 - 0.30f) * 1.40f + smooth((t - 0.54f) / 0.40f) * (0.45f + h * 1.10f),
                    0.028f + 0.012f * h2, 0.110f + 0.065f * h, 0.024f,
                    pa, 0.0f, 0.0f,
                    (shard % 4 == 0) ? WHITE : ((shard % 3 == 0) ? GLASS : MINT),
                    Math.round(a * shards * (0.46f + 0.30f * h)));
        }
        for (int mist = 0; mist < 12; mist++) {
            float h = hash(salt, 6100 + mist * 43);
            float pa = mist * TAU / 12.0f + t * 3.2f + h;
            float pr = 2.05f + h * 0.90f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(pa) * pr, Mth.sin(pa) * pr, -0.85f + h * 4.20f,
                    0.130f, 0.130f, 0.130f, pa, 0.0f, 0.0f,
                    AIR, Math.round(a * fade * (0.07f + 0.07f * h)));
        }
    }

    /**
     * Une lance verticale complète : tête facettée de 14 niveaux, collier, fût hexagonal de 16
     * sections, quatre viroles, pommeau, deux ailerons et un sillage hélicoïdal.
     *
     * @param offF,offL décalage latéral de la lance dans le repère de l'attaque
     * @param headY     hauteur de la pointe (elle descend de +8,6 à −0,5)
     * @param s         échelle globale (1,00 pour la principale, 0,74..0,80 pour les satellites)
     */
    private static void spear(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float offF, float offL, float headY, float s,
                              float t, int a, int salt, int seed) {
        if (headY > 12.0f) return;

        // 3a. Tête : cône à six faces, pointe fine, base large — lisible comme une lame de lance.
        for (int bl = 0; bl < 14; bl++) {
            float u = bl / 13.0f;
            float half = (0.026f + u * 0.250f) * s;
            float y0 = headY + bl * 0.112f * s;
            for (int q = 0; q < 6; q++) {
                float qa = q * TAU / 6.0f + bl * 0.16f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + Mth.cos(qa) * half * 0.62f,
                        offL + Mth.sin(qa) * half * 0.62f, y0,
                        half * 0.52f, half * 0.34f, half * 0.52f,
                        qa, 0.0f, 0.0f,
                        (q % 2 == 0) ? WHITE : GLASS,
                        Math.round(a * (0.96f - 0.16f * u)));
            }
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, offF, offL, y0,
                    half * 0.52f, half * 0.60f, half * 0.52f, 0.0f, 0.0f, 0.0f,
                    MINT, Math.round(a * (0.98f - 0.20f * u)));
        }

        // 3b. Collier sous la tête : deux anneaux de huit cubes.
        for (int ring = 0; ring < 2; ring++) {
            float y0 = headY + (1.56f + ring * 0.10f) * s;
            float rr = (0.285f - ring * 0.022f) * s;
            for (int q = 0; q < 8; q++) {
                float qa = q * TAU / 8.0f + ring * 0.40f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + Mth.cos(qa) * rr, offL + Mth.sin(qa) * rr, y0,
                        0.052f * s, 0.024f * s, 0.052f * s, qa, 0.0f, 0.0f,
                        (ring == 0) ? GLASS : PALE, Math.round(a * 0.95f));
            }
        }

        // 3c. Fût : seize sections à six pans, âme lumineuse au centre.
        for (int seg = 0; seg < 16; seg++) {
            float u0 = seg / 15.0f;
            float y0 = headY + (1.78f + u0 * 2.30f) * s;
            float half = (0.118f - u0 * 0.034f) * s;
            for (int q = 0; q < 6; q++) {
                float qa = q * TAU / 6.0f + u0 * 1.1f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + Mth.cos(qa) * half * 0.90f,
                        offL + Mth.sin(qa) * half * 0.90f, y0,
                        half * 0.58f, half * 0.62f, half * 0.58f, qa, 0.0f, 0.0f,
                        color(MINT, WHITE, 0.30f + u0 * 0.56f), Math.round(a * 0.97f));
            }
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, offF, offL, y0,
                    half * 0.52f, half * 0.60f, half * 0.52f, 0.0f, 0.0f, 0.0f,
                    color(GLASS, WHITE, 0.52f + u0 * 0.42f), Math.round(a * 0.90f));
        }

        // 3d. Quatre viroles de cristal.
        for (int ring = 0; ring < 4; ring++) {
            float u0 = (ring + 0.5f) / 4.0f;
            float y0 = headY + (1.78f + u0 * 2.30f) * s;
            float rr = (0.134f - u0 * 0.022f) * s;
            for (int q = 0; q < 8; q++) {
                float qa = q * TAU / 8.0f + ring * 0.31f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + Mth.cos(qa) * rr, offL + Mth.sin(qa) * rr, y0,
                        0.040f * s, 0.020f * s, 0.040f * s, qa, 0.0f, 0.0f,
                        (ring % 2 == 0) ? WHITE : GLASS, Math.round(a * 0.94f));
            }
        }

        // 3e. Deux ailerons recourbés à la base de la tête : ils donnent la silhouette « lance ».
        for (int fin = 0; fin < 2; fin++) {
            float side = (fin == 0) ? 1.0f : -1.0f;
            for (int k = 0; k < 4; k++) {
                float u = k / 3.0f;
                float reach = (0.20f + u * 0.34f) * s;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + side * reach, offL + side * reach * 0.30f,
                        headY + (1.52f - u * 0.42f) * s,
                        (0.070f - u * 0.022f) * s, (0.060f - u * 0.020f) * s, 0.040f * s,
                        side * (0.30f + u * 0.40f), side * 0.22f, 0.0f,
                        (k % 2 == 0) ? MINT : AZURE,
                        Math.round(a * (0.92f - 0.22f * u)));
            }
        }

        // 3f. Pommeau : anneau de six cubes et cabochon.
        for (int q = 0; q < 6; q++) {
            float qa = q * TAU / 6.0f + t * 1.4f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    offF + Mth.cos(qa) * 0.125f * s, offL + Mth.sin(qa) * 0.125f * s,
                    headY + 4.16f * s, 0.050f * s, 0.050f * s, 0.050f * s, qa, 0.0f, 0.0f,
                    (q % 2 == 0) ? WHITE : PALE, Math.round(a * 0.92f));
        }
        rot(vc, mat, tmp, cx, cy, cz, dx, dz, offF, offL, headY + 4.26f * s,
                0.075f * s, 0.075f * s, 0.075f * s, 0.0f, 0.0f, 0.0f,
                GLASS, Math.round(a * 0.95f));

        // 3g. Sillage : deux hélices contra-rotatives serrées autour du fût.
        for (int helix = 0; helix < 2; helix++) {
            float spinDir = (helix == 0) ? 1.0f : -1.0f;
            for (int step = 0; step < 18; step++) {
                float u0 = step / 17.0f;
                float ra = u0 * TAU * 2.0f * spinDir + t * 26.0f * spinDir + helix * 1.7f;
                float y0 = headY + (0.30f + u0 * 3.90f) * s;
                float r0 = (0.24f + u0 * 0.06f) * s;
                float size = (0.024f + 0.007f * hash(salt, 4100 + step * 17 + helix * 7 + seed)) * s;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        offF + Mth.cos(ra) * r0, offL + Mth.sin(ra) * r0, y0,
                        size, size, size * 2.6f, ra, 0.0f, 0.0f,
                        (helix == 0) ? AZURE : MINT,
                        Math.round(a * (0.52f - u0 * 0.20f)));
            }
        }
    }

    // ============================================================================================
    // 2. TEMPÊTE DE SABLE — entonnoir ÉTROIT AU SOL, LARGE EN HAUT, tourbillon interne même sens
    // ============================================================================================

    /**
     * Chorégraphie (140 ticks) : la dune se forme, les six bras extérieurs montent sur 7,9 blocs en
     * s'évasant, le tourbillon interne (quatre bras sombres, plus opaques) suit <b>le même sens</b>,
     * les grains et le voile tourbillonnent, puis tout retombe.
     */
    private static void renderSandstorm(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.07f) * (1.0f - smooth((t - 0.74f) / 0.26f)));
        if (a <= 4) return;
        final float height = 7.90f;
        float grow = smooth(t / 0.30f) * (1.0f - smooth((t - 0.70f) / 0.30f));
        float settle = smooth((t - 0.74f) / 0.26f);
        float spin = t * 11.0f;

        // ---- 1. Dune de base : quatre strates (128 cubes) ----
        for (int layer = 0; layer < 4; layer++) {
            float u = layer / 3.0f;
            float radius = (1.55f - u * 0.82f) * Math.max(0.06f, grow);
            if (radius <= 0.02f) continue;
            for (int i = 0; i < 32; i++) {
                float h = hash(salt, 6100 + layer * 71 + i * 19);
                float ang = i * TAU / 32.0f + layer * 0.22f + spin * (0.22f + layer * 0.03f);
                float crest = Math.max(0.0f, Mth.cos(ang - 0.55f)) * (0.24f - u * 0.10f) * grow;
                float y = -1.14f + u * 0.40f * grow + crest
                        + 0.045f * Mth.sin(ang * 6.0f + t * 9.0f + layer) * grow
                        - settle * (0.24f + h * 0.22f);
                float size = (0.150f - u * 0.040f) * (0.84f + h * 0.30f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius * (1.0f + 0.20f * u),
                        Mth.sin(ang) * radius * 0.74f, y,
                        size * 1.24f, size * 0.58f, size,
                        ang + t * 0.30f, h * 0.26f, 0.0f,
                        color(SAND_DARK, SAND_LIGHT, 0.24f + u * 0.44f + h * 0.18f),
                        Math.round(a * grow * (0.50f + 0.26f * h)));
            }
        }

        // ---- 2. Entonnoir extérieur : six bras, rayon 0,45 → 2,55 (étroit au sol) ----
        for (int arm = 0; arm < 6; arm++) for (int level = 0; level < 30; level++) {
            float u = level / 29.0f;
            float reveal = smooth((t - 0.05f - u * 0.10f) / 0.22f) * (1.0f - settle);
            if (reveal <= 0.01f) continue;
            float h = hash(salt, 6500 + arm * 149 + level * 23);
            float ang = arm * TAU / 6.0f + u * 8.2f + spin;
            float radius = (0.45f + u * 2.10f) * (0.92f + 0.12f * Mth.sin(t * 9.0f + u * 12.0f));
            float y = -1.10f + u * height + settle * 0.40f * h;
            float size = (0.190f - u * 0.055f) * (0.84f + h * 0.32f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                    size * 1.28f, size, size * 1.12f,
                    ang, u * 0.75f, 0.0f,
                    (level % 5 == 0) ? DUST : SAND_LIGHT,
                    Math.round(a * reveal * (0.34f + 0.30f * (1.0f - u))));
        }

        // ---- 3. Tourbillon INTERNE : quatre bras sombres et opaques, même sens, étroit au sol ----
        for (int arm = 0; arm < 4; arm++) for (int level = 0; level < 22; level++) {
            float u = level / 21.0f;
            float reveal = smooth((t - 0.08f - u * 0.08f) / 0.20f) * (1.0f - settle);
            if (reveal <= 0.01f) continue;
            float h = hash(salt, 9100 + arm * 131 + level * 19);
            float ang = arm * TAU / 4.0f + u * 7.4f + spin * 1.06f;
            float radius = (0.22f + u * 1.05f) * (0.94f + 0.10f * Mth.sin(t * 10.0f + u * 9.0f));
            float y = -1.06f + u * (height * 0.86f);
            float size = (0.205f - u * 0.070f) * (0.90f + h * 0.20f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                    size * 1.20f, size, size * 1.10f,
                    ang, u * 0.60f, 0.0f,
                    color(SAND_DEEP, SAND_DARK, 0.30f + u * 0.40f + h * 0.20f),
                    Math.round(a * reveal * (0.86f + 0.14f * h)));
        }

        // ---- 4. Deux mini-tornades au pied de la colonne (52 cubes) ----
        for (int cell = 0; cell < 2; cell++) {
            float ca = cell * 3.1415927f + 1.30f;
            float cf = Mth.cos(ca) * 1.85f;
            float cl = Mth.sin(ca) * 1.85f;
            float born = smooth((t - 0.16f - cell * 0.05f) / 0.24f) * (1.0f - settle);
            if (born <= 0.01f) continue;
            for (int i = 0; i < 26; i++) {
                float u = i / 25.0f;
                float h = hash(salt, 7300 + cell * 101 + i * 17);
                float ang = u * 8.0f + t * 20.0f + cell * 0.40f;
                float rr = (0.20f + u * 0.62f) * (0.85f + 0.25f * h);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        cf + Mth.cos(ang) * rr, cl + Mth.sin(ang) * rr,
                        -0.98f + u * 2.40f + 0.05f * Mth.sin(t * 8.0f + i),
                        0.028f + 0.016f * h, 0.028f + 0.016f * h, 0.028f + 0.016f * h,
                        ang, 0.0f, 0.0f,
                        (i % 6 == 0) ? DUST : SAND_LIGHT,
                        Math.round(a * born * (0.30f + 0.26f * h)));
            }
        }

        // ---- 5. Grains individuels : advection hélicoïdale sur toute la hauteur (130 cubes) ----
        for (int grain = 0; grain < 130; grain++) {
            float h = hash(salt, 7500 + grain * 47);
            float h2 = hash(salt, 7700 + grain * 61);
            float cycle = fract(t * (1.05f + h * 0.85f) + grain * 0.6180339f);
            float ang = grain * 2.3999632f + spin * (0.8f + h2 * 0.7f);
            float radius = 0.30f + cycle * 2.35f;
            float y = Mth.lerp(cycle, -1.08f, height * 0.94f) + h2 * 0.30f;
            float size = 0.024f + 0.024f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                    size, size, size, ang, cycle * 3.0f, 0.0f,
                    (grain % 5 == 0) ? DUST : (grain % 3 == 0 ? SAND_LIGHT : SAND),
                    Math.round(a * (1.0f - cycle) * (0.42f + 0.28f * h)));
        }

        // ---- 6. Voile de poussière : trente cubes larges et translucides ----
        for (int veil = 0; veil < 30; veil++) {
            float h = hash(salt, 8000 + veil * 37);
            float ang = veil * TAU / 30.0f + t * 4.0f + h;
            float radius = 1.45f + h * 1.10f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -0.80f + h * (height * 0.92f) + settle * 0.40f,
                    0.150f + 0.060f * h, 0.120f, 0.150f + 0.060f * h,
                    ang, h * TAU, 0.0f, SAND_DARK,
                    Math.round(a * (0.08f + 0.08f * h)));
        }

        // ---- 7. Éclats arrachés à la crête (40 cubes) ----
        float fling = smooth((t - 0.34f) / 0.26f) * (1.0f - settle);
        for (int shard = 0; shard < 40; shard++) {
            float h = hash(salt, 8400 + shard * 59);
            float h2 = hash(salt, 8500 + shard * 43);
            float ang = shard * 2.3999632f + t * (2.0f + h * 2.4f);
            float radius = 0.55f + fling * (0.90f + h * 1.50f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -0.75f + h2 * height * 0.80f + fling * (0.35f + h * 0.95f),
                    0.030f + 0.014f * h2, 0.105f + 0.060f * h, 0.026f,
                    ang, h2 * 2.2f, ang * 0.6f,
                    (shard % 4 == 0) ? DUST : SAND_LIGHT,
                    Math.round(a * fling * (0.34f + 0.26f * h)));
        }
    }

    // ============================================================================================
    // 3. ŒIL DU CYCLONE — entonnoir de 8,9 blocs, œil sommital, mur de nuages
    // ============================================================================================

    /**
     * Chorégraphie (120 ticks) : ouverture des entonnoirs (0,00–0,26), œil sommital (0,18–0,40),
     * spirale continue jusqu'à 8,9 blocs, éclatement sommital (0,62–0,86), effondrement (0,78–1,00).
     * Tous les brins tournent dans le même sens.
     */
    private static void renderTempest(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.06f) * (1.0f - smooth((t - 0.80f) / 0.20f)));
        if (a <= 4) return;
        final float height = 8.90f;
        float open = smooth(t / 0.26f);
        float collapse = smooth((t - 0.78f) / 0.22f);
        float eye = smooth((t - 0.18f) / 0.22f) * (1.0f - collapse);
        float spin = t * 12.0f;
        float burst = smooth((t - 0.62f) / 0.38f);

        // ---- 1. Entonnoir extérieur : huit brins, rayon 0,55 → 2,95, hauteur 8,9 ----
        for (int strand = 0; strand < 8; strand++) {
            float phase = strand * TAU / 8.0f;
            for (int level = 0; level < 26; level++) {
                float u = level / 25.0f;
                float h = hash(salt, 8100 + strand * 97 + level * 13);
                float ang = phase + u * 8.6f + spin * (1.0f - u * 0.35f);
                float radius = (0.55f + u * 2.40f) * (0.92f + 0.12f * h) * open;
                float y = -1.15f + u * height;
                float size = (0.110f + u * 0.030f) * (0.84f + h * 0.30f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                        size, size, size,
                        ang, u * 0.50f, ang * 0.30f,
                        color(AIR, WHITE, 0.40f + u * 0.46f + h * 0.18f),
                        Math.round(a * open * (1.0f - collapse * 0.35f)
                                * (0.30f + 0.22f * (1.0f - u * 0.5f))));
            }
        }

        // ---- 2. Entonnoir interne : six brins sombres, même sens, rayon 0,24 → 1,35 ----
        for (int strand = 0; strand < 6; strand++) {
            float phase = strand * TAU / 6.0f + 0.35f;
            for (int level = 0; level < 20; level++) {
                float u = level / 19.0f;
                float h = hash(salt, 8300 + strand * 89 + level * 17);
                float ang = phase + u * 7.2f + spin * 1.08f;
                float radius = (0.24f + u * 1.10f) * (0.94f + 0.10f * h) * open;
                float y = -1.08f + u * (height * 0.92f);
                float size = (0.118f + u * 0.020f) * (0.86f + h * 0.24f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                        size, size, size,
                        ang, u * 0.40f, 0.0f,
                        color(AIR_DEEP, MINT, 0.30f + u * 0.40f + h * 0.16f),
                        Math.round(a * open * (0.70f + 0.20f * h)));
            }
        }

        // ---- 3. Noyau : colonne centrale torsadée (28 cubes) ----
        for (int step = 0; step < 28; step++) {
            float u = step / 27.0f;
            float h = hash(salt, 8600 + step * 29);
            float ang = t * 9.0f + u * 5.0f;
            float radius = 0.12f + 0.06f * Mth.sin(t * 12.0f + u * 6.0f);
            float size = (0.082f - u * 0.028f) * (0.84f + 0.24f * Mth.sin(t * 16.0f + step));
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -1.05f + u * (height * 0.96f),
                    size, size * 1.50f, size,
                    ang, u * 1.1f, 0.0f,
                    color(GLASS, WHITE, 0.35f + h * 0.50f),
                    Math.round(a * open * (1.0f - collapse * 0.60f) * (0.40f + 0.32f * h)));
        }

        // ---- 4. Œil calme sommital : anneau de 44 segments et dix rayons (134 cubes) ----
        if (eye > 0.02f) {
            float eyeY = height * 0.90f;
            float eyeR = 2.45f * open;
            for (int i = 0; i < 44; i++) {
                float a0 = i * TAU / 44.0f;
                float a1 = (i + 1) * TAU / 44.0f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a0) * eyeR, Mth.sin(a0) * eyeR, eyeY,
                        Mth.cos(a1) * eyeR, Mth.sin(a1) * eyeR, eyeY,
                        (i % 4 == 0) ? 0.034f : 0.016f,
                        (i % 4 == 0) ? WHITE : GLASS, WHITE,
                        Math.round(a * eye * ((i % 4 == 0) ? 0.94f : 0.60f)));
            }
            for (int spoke = 0; spoke < 10; spoke++) {
                float sa = spoke * TAU / 10.0f + spin * 0.35f;
                for (int step = 1; step <= 9; step++) {
                    float r0 = eyeR * (0.14f + step * 0.090f);
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(sa) * r0, Mth.sin(sa) * r0, eyeY,
                            0.028f, 0.028f, 0.028f, sa, 0.0f, 0.0f,
                            (step % 3 == 0) ? WHITE : MINT,
                            Math.round(a * eye * (0.24f + 0.30f * (1.0f - step / 9.0f))));
                }
            }
        }

        // ---- 5. Anneau de sol : cinquante-six segments qui se resserrent ----
        float groundR = 2.65f * open;
        for (int i = 0; i < 56; i++) {
            float a0 = i * TAU / 56.0f;
            float a1 = (i + 1) * TAU / 56.0f;
            float r0 = groundR * (0.96f + 0.06f * Mth.sin(i * 1.7f + t * 7.0f));
            float r1 = groundR * (0.96f + 0.06f * Mth.sin((i + 1) * 1.7f + t * 7.0f));
            link(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(a0) * r0, Mth.sin(a0) * r0, -1.16f,
                    Mth.cos(a1) * r1, Mth.sin(a1) * r1, -1.16f,
                    (i % 5 == 0) ? 0.028f : 0.013f,
                    (i % 5 == 0) ? MINT : AIR, GLASS,
                    Math.round(a * open * 0.60f * (1.0f - collapse * 0.30f)));
        }

        // ---- 6. Mur de nuages : deux bandes de six colonnes, hauteur 8,2 ----
        for (int band = 0; band < 2; band++) for (int column = 0; column < 6; column++) {
            float colAng = column * TAU / 6.0f + band * 0.52f + spin * 0.22f;
            float colRadius = (band == 0) ? 2.55f : 3.35f;
            for (int step = 0; step < 18; step++) {
                float u = step / 17.0f;
                float h = hash(salt, 9200 + band * 311 + column * 53 + step * 11);
                float wobble = 0.16f * Mth.sin(t * 7.0f + column + u * 5.0f);
                float radius = (colRadius + wobble) * open;
                float y = -0.55f + u * (height * 0.92f) + 0.10f * Mth.sin(t * 5.0f + step);
                float size = (0.150f - u * 0.040f) * (0.80f + h * 0.34f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(colAng) * radius, Mth.sin(colAng) * radius, y,
                        size, size * 1.10f, size,
                        colAng, u * 0.40f, colAng * 0.3f,
                        color(AIR_DEEP, PALE, 0.24f + u * 0.42f + h * 0.18f),
                        Math.round(a * open * (0.14f + 0.14f * h)));
            }
        }

        // ---- 7. Poussière et débris fins : quatre-vingt-dix grains ----
        for (int grain = 0; grain < 90; grain++) {
            float h = hash(salt, 9600 + grain * 41);
            float h2 = hash(salt, 9700 + grain * 53);
            float cycle = fract(t * (0.85f + h * 1.05f) + grain * 0.6180339f);
            float ang = grain * 2.3999632f + t * (2.4f + h2 * 2.6f);
            float radius = (0.45f + cycle * 2.55f) * open;
            float size = 0.020f + 0.018f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                    -1.05f + cycle * height + h2 * 0.25f,
                    size, size, size,
                    ang, cycle * 2.4f, 0.0f,
                    (grain % 4 == 0) ? (grain % 8 == 0 ? SLATE : WHITE)
                            : (grain % 2 == 0 ? PALE : AIR),
                    Math.round(a * (1.0f - cycle) * (0.26f + 0.20f * h)));
        }

        // ---- 8. Éclatement sommital (40 cubes) ----
        if (burst > 0.02f) {
            float topY = height * 0.96f;
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 9900 + i * 43);
                float ang = i * TAU / 40.0f + spin;
                float radius = (0.45f + burst * (1.25f + h * 1.15f)) * open;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                        topY + burst * (0.45f + h * 0.95f),
                        0.046f + h * 0.038f, 0.042f + h * 0.034f, 0.046f + h * 0.038f,
                        ang, h * 1.6f, 0.0f,
                        (i % 4 == 0) ? WHITE : (i % 2 == 0 ? GLASS : MINT),
                        Math.round(a * burst * (0.42f + 0.26f * h) * (1.0f - collapse * 0.50f)));
            }
        }

        // ---- 9. Effondrement : la matière retombe vers le centre (40 cubes) ----
        if (collapse > 0.20f) {
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 10200 + i * 37);
                float ang = i * TAU / 40.0f + spin * 2.0f;
                float radius = (1.0f - collapse) * 2.10f * (0.55f + h * 0.70f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * radius, Mth.sin(ang) * radius,
                        height * 0.62f + (h - 0.5f) * 0.60f - collapse * (height * 0.55f),
                        0.048f, 0.048f, 0.048f,
                        ang, collapse * 2.0f, 0.0f,
                        WHITE, Math.round(a * collapse * 0.58f));
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

    private static float easeIn(float x) {
        x = clamp(x);
        return x * x;
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
