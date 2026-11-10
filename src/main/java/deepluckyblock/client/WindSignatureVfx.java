package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T374-WIND-REFONTE — élément VENT : trois signatures sur un moteur voxel commun.
 *
 * <p><b>Pourquoi une classe dédiée ?</b> Le vent était éclaté : la lance vivait dans
 * {@link GaiaWorldTreeVfx} sous forme de modèle compacté (variant {@code ZEPHYR}), et Sandstorm
 * dormait en archive dans {@link EarthSignatureVfx} avec du code Terre. Aucune des deux n'avait de
 * langage visuel propre : ni rubans, ni tourbillons, ni grain. Cette classe leur donne une identité
 * unique — blanc glacé, azur, ardoise — et ajoute la troisième signature, l'Œil du cyclone.</p>
 *
 * <p><b>Signatures.</b></p>
 * <ul>
 *   <li>{@link #ZEPHYR} — <i>Lance de zéphyr</i> : trois lances d'air comprimé convergent, percent la
 *       cible et se dissolvent en plumes de vent ; un vortex se forme autour de l'impact.</li>
 *   <li>{@link #SANDSTORM} — <i>Tempête de sable</i> : jupe de dune, trois bras spiralés, rubans
 *       horizontaux et grains individuels, en ocre et poussière.</li>
 *   <li>{@link #TEMPEST} — <i>Œil du cyclone</i> : deux entonnoirs contra-rotatifs, anneau de calme
 *       et débris en orbite, puis effondrement final.</li>
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
    // 1. ZÉPHYR — trois lances convergentes, vortex et plumes de vent
    // ============================================================================================

    /**
     * Chorégraphie (durée serveur 80 ticks) :
     * 0,00–0,18 le vortex s'ouvre ; 0,18–0,42 les trois lances convergent ; 0,42–0,52 percement ;
     * 0,52–0,84 les rubans sont emportés vers l'extérieur ; 0,84–1,00 dissipation totale.
     */
    private static void renderZephyr(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.045f) * (1.0f - smooth((t - 0.84f) / 0.16f)));
        if (a <= 4) return;
        float fly = easeOut((t - 0.16f) / 0.26f);          // convergence des lances
        float pierce = smooth((t - 0.42f) / 0.10f);        // percement puis maintien
        float blow = smooth((t - 0.50f) / 0.44f);          // rubans emportés
        float vortex = smooth(t / 0.20f) * (1.0f - smooth((t - 0.60f) / 0.40f));
        float impact = 1.0f - smooth(Math.abs(t - 0.46f) / 0.085f);

        // --- Vortex d'air : trois bras spiralés qui se resserrent vers la cible. ---
        for (int arm = 0; arm < 3; arm++) for (int level = 0; level < 15; level++) {
            float u = level / 14.0f;
            float h = hash(salt, 3100 + arm * 131 + level * 17);
            float angle = arm * 2.0943951f + u * 6.2f + t * 16.0f;
            float radius = 1.38f - u * 0.95f + 0.10f * Mth.sin(t * 11.0f + u * 9.0f);
            float f = Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = -1.10f + u * 3.70f + 0.10f * Mth.sin(t * 7.0f + arm);
            float size = (0.115f - u * 0.045f) * (0.82f + h * 0.36f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size * 1.55f, size * 0.95f, size * 1.10f,
                    angle, u * 0.60f, angle * 0.35f,
                    color(AIR_DEEP, PALE, 0.28f + u * 0.50f + h * 0.20f),
                    Math.round(a * vortex * (0.30f + 0.24f * h)));
        }

        // --- Anneau de sol : le vent balaie le pourtour avant que les lances n'arrivent. ---
        float ringA = smooth(t / 0.16f) * (1.0f - smooth((t - 0.55f) / 0.30f));
        if (ringA > 0.01f) {
            for (int i = 0; i < 30; i++) {
                float a0 = i * TAU / 30.0f, a1 = (i + 1) * TAU / 30.0f;
                float r = 1.42f + 0.22f * Mth.sin(t * 8.0f + i);
                link(vc, mat, tmp, cx, cy - 1.12f, cz, dx, dz,
                        Mth.cos(a0) * r, Mth.sin(a0) * r, 0.0f,
                        Mth.cos(a1) * r, Mth.sin(a1) * r, 0.0f,
                        0.020f, i % 5 == 0 ? GLASS : AZURE, WHITE,
                        Math.round(a * ringA * 0.52f));
            }
        }

        // --- Trois lances : fût effilé, quatre ailettes, pointe de cristal, ruban hélicoïdal. ---
        for (int spear = 0; spear < 3; spear++) {
            float angle = spear * 2.0943951f + 1.05f + t * 0.45f;
            float approach = Mth.lerp(fly, 1.0f, 0.06f);
            float startF = -3.60f + spear * 0.30f;
            float head = Mth.lerp(fly, startF, 0.42f) + pierce * 0.18f;
            float lateral = Mth.sin(angle) * 1.62f * approach;
            float forward = Mth.cos(angle) * 1.62f * approach;
            float tilt = 0.16f * (1.0f - fly);
            int spearAlpha = Math.round(a * (0.70f + 0.30f * (1.0f - blow)));

            // Fût : neuf sections jointives, légèrement coniques.
            for (int seg = 0; seg < 9; seg++) {
                float u0 = seg / 9.0f, u1 = (seg + 1) / 9.0f;
                float f0 = head - 2.10f + u0 * 1.82f, f1 = head - 2.10f + u1 * 1.82f;
                float half = 0.088f - u0 * 0.030f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + f0, lateral, Mth.sin(tilt) * (f0 - head) * 0.20f,
                        forward + f1, lateral, Mth.sin(tilt) * (f1 - head) * 0.20f,
                        half, AIR, PALE, Math.round(spearAlpha * 0.62f));
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + f0, lateral + 0.001f, Mth.sin(tilt) * (f0 - head) * 0.20f,
                        forward + f1, lateral + 0.001f, Mth.sin(tilt) * (f1 - head) * 0.20f,
                        half * 0.44f, GLASS, WHITE, Math.round(spearAlpha * 0.80f));
            }

            // Quatre ailettes arrière : elles stabilisent la lance et accrochent la lumière.
            for (int fin = 0; fin < 4; fin++) {
                float fa = fin * TAU / 4.0f + angle;
                float base = head - 2.05f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + base + 0.16f, lateral + Mth.cos(fa) * 0.20f,
                        Mth.sin(fa) * 0.20f,
                        0.050f, 0.085f, 0.014f, fa, 0.20f, 0.0f,
                        fin % 2 == 0 ? PALE : AZURE, Math.round(spearAlpha * 0.66f));
            }

            // Pointe : cinq sections de 0,14 à 0,01 — c'est elle qui « perce ».
            for (int tip = 0; tip < 5; tip++) {
                float u0 = tip / 5.0f, u1 = (tip + 1) / 5.0f;
                float f0 = head - 0.28f + u0 * 0.86f, f1 = head - 0.28f + u1 * 0.86f;
                float half = 0.140f * (1.0f - u0) + 0.008f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + f0, lateral, Mth.sin(tilt) * (f0 - head) * 0.20f,
                        forward + f1, lateral, Mth.sin(tilt) * (f1 - head) * 0.20f,
                        half, GLASS, WHITE, Math.round(spearAlpha * 0.92f));
            }

            // Ruban hélicoïdal : le « sillage » visible qui tourne autour du fût.
            float spin = t * 26.0f + spear * 1.4f + blow * 5.0f;
            float ribbonRadius = 0.20f + blow * 1.10f;
            for (int step = 0; step < 16; step++) {
                float u0 = step / 16.0f, u1 = (step + 1) / 16.0f;
                float ra0 = u0 * TAU * 1.7f + spin, ra1 = u1 * TAU * 1.7f + spin;
                float f0 = Mth.lerp(u0, head - 2.05f, head + 0.30f);
                float f1 = Mth.lerp(u1, head - 2.05f, head + 0.30f);
                float r0 = ribbonRadius * (0.46f + u0 * 0.70f);
                float r1 = ribbonRadius * (0.46f + u1 * 0.70f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + f0, lateral + Mth.cos(ra0) * r0,
                        Mth.sin(ra0) * r0 + Mth.sin(tilt) * (f0 - head) * 0.20f,
                        0.026f, 0.115f, 0.026f, ra0, u0 * 0.9f, 0.0f,
                        u0 < 0.5f ? AZURE : GLASS,
                        Math.round(a * (1.0f - blow * 0.55f) * (0.62f - u0 * 0.28f)));
                if (step % 2 == 0) {
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            forward + f1, lateral + Mth.cos(ra1) * r1,
                            Mth.sin(ra1) * r1 + Mth.sin(tilt) * (f1 - head) * 0.20f,
                            0.018f, 0.070f, 0.018f, ra1, u1 * 0.9f, 0.0f,
                            WHITE, Math.round(a * (1.0f - blow * 0.75f) * 0.46f));
                }
            }

            // Trois plumes arrachées qui dérivent derrière la lance.
            for (int plume = 0; plume < 3; plume++) {
                float h = hash(salt, 4300 + spear * 97 + plume * 41);
                float drift = blow * (0.80f + h * 1.60f);
                float pa = angle + (plume - 1) * 0.55f + t * 2.0f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward + head - 2.30f - drift * 1.40f + h * 0.30f,
                        lateral + Mth.cos(pa) * (0.30f + drift * 0.90f),
                        Mth.sin(pa) * (0.30f + drift * 0.90f) + drift * 0.55f,
                        0.038f, 0.150f, 0.030f, pa, t * 3.0f + h, pa * 0.5f,
                        plume == 1 ? WHITE : PALE,
                        Math.round(a * (1.0f - blow) * (0.34f + h * 0.26f)));
            }
        }

        // --- Impact : anneau de compression + poussière d'air. ---
        if (impact > 0.02f) {
            float reach = 0.30f + (1.0f - impact) * 1.55f;
            for (int i = 0; i < 24; i++) {
                float a0 = i * TAU / 24.0f, a1 = (i + 1) * TAU / 24.0f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a0) * reach, Mth.sin(a0) * reach, 0.10f,
                        Mth.cos(a1) * reach, Mth.sin(a1) * reach, 0.10f,
                        i % 3 == 0 ? 0.024f : 0.014f, i % 3 == 0 ? WHITE : GLASS, WHITE,
                        Math.round(a * impact * 0.86f));
            }
            for (int puff = 0; puff < 12; puff++) {
                float h = hash(salt, 5100 + puff * 53);
                float pa = puff * TAU / 12.0f + h;
                float pr = reach * (0.65f + h * 0.55f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(pa) * pr, Mth.sin(pa) * pr, (h - 0.5f) * 0.55f,
                        0.070f, 0.060f, 0.070f, pa, h * TAU, 0.0f,
                        puff % 3 == 0 ? WHITE : PALE, Math.round(a * impact * (0.40f + h * 0.30f)));
            }
        }
    }

    // ============================================================================================
    // 2. SANDSTORM — dune, trois bras spiralés, rubans et grains (ocre)
    // ============================================================================================

    /**
     * Chorégraphie (durée 140 ticks) : la dune se forme (0,00–0,30), les bras montent (0,06–0,60),
     * les grains tourbillonnent tant que la tempête vit, puis tout retombe (0,72–1,00).
     */
    private static void renderSandstorm(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.07f) * (1.0f - smooth((t - 0.74f) / 0.26f)));
        if (a <= 4) return;
        float grow = smooth(t / 0.30f) * (1.0f - smooth((t - 0.70f) / 0.30f));
        float spin = t * 9.0f;
        float settle = smooth((t - 0.72f) / 0.28f);

        // --- Jupe de dune : trois strates elliptiques, crête asymétrique. ---
        for (int layer = 0; layer < 3; layer++) {
            float u = layer / 2.0f;
            float radius = (1.34f - u * 0.52f) * Math.max(0.06f, grow);
            if (radius <= 0.02f) continue;
            for (int i = 0; i < 24; i++) {
                float h = hash(salt, 6100 + layer * 71 + i * 19);
                float angle = i * TAU / 24.0f + layer * 0.22f + spin * 0.22f;
                float f = Mth.cos(angle) * radius * (1.0f + 0.18f * u);
                float l = Mth.sin(angle) * radius * 0.70f;
                float crest = Math.max(0.0f, Mth.cos(angle - 0.55f)) * 0.20f * grow;
                float y = -1.12f + u * 0.26f * grow + crest - settle * (0.22f + h * 0.20f);
                float size = (0.155f - u * 0.030f) * (0.86f + h * 0.28f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        size * 1.22f, size * 0.62f, size,
                        angle + t * 0.30f, h * 0.28f, 0.0f,
                        color(SAND_DARK, SAND_LIGHT, 0.24f + u * 0.42f + h * 0.18f),
                        Math.round(a * grow * (0.54f + 0.26f * h)));
            }
        }

        // --- Trois bras spiralés qui montent en se resserrant. ---
        for (int arm = 0; arm < 3; arm++) for (int level = 0; level < 16; level++) {
            float u = level / 15.0f;
            float reveal = smooth((t - 0.06f - u * 0.12f) / 0.20f) * (1.0f - settle);
            if (reveal <= 0.01f) continue;
            float h = hash(salt, 6500 + arm * 149 + level * 23);
            float angle = arm * 2.0943951f + u * 7.6f + t * 14.0f;
            float radius = (1.02f - u * 0.62f) * (0.90f + 0.14f * Mth.sin(t * 9.0f + u * 12.0f));
            float f = Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = -0.92f + u * 2.55f;
            float size = (0.185f - u * 0.075f) * (0.85f + h * 0.30f);
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size * 1.35f, size, size * 1.15f,
                    angle, u * 0.75f, 0.0f,
                    level % 4 == 0 ? DUST : SAND_LIGHT,
                    Math.round(a * reveal * (0.34f + 0.30f * (1.0f - u))));
        }

        // --- Rubans horizontaux : quatre ceintures qui tournent à des vitesses différentes. ---
        for (int band = 0; band < 4; band++) for (int i = 0; i < 26; i++) {
            float u = i / 25.0f;
            float h = hash(salt, 6900 + band * 83 + i * 11);
            float angle = u * 5.2f + band * 1.57f + t * (11.0f + band * 1.5f);
            float radius = 1.08f + band * 0.16f + 0.11f * Mth.sin(u * 9.0f + t * 8.0f);
            float f = Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = -0.86f + band * 0.42f + u * 0.30f + settle * 0.30f * h;
            float size = 0.024f + 0.014f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size * 2.6f, size * 0.50f, size * 0.70f,
                    angle, 0.20f, 0.0f,
                    band % 2 == 0 ? DUST : SAND,
                    Math.round(a * (1.0f - settle) * (0.30f + 0.22f * h)));
        }

        // --- Grains individuels : advection hélicoïdale puis extinction. ---
        for (int grain = 0; grain < 48; grain++) {
            float h = hash(salt, 7300 + grain * 47);
            float h2 = hash(salt, 7400 + grain * 61);
            float cycle = fract(t * (1.10f + h * 0.90f) + grain * 0.6180339f);
            float angle = grain * 2.3999632f + spin * (0.8f + h2 * 0.7f);
            float radius = 0.30f + cycle * 1.85f;
            float f = Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = Mth.lerp(cycle, -1.05f, 2.20f) + h2 * 0.30f;
            float size = 0.028f + 0.026f * h;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                    size, size, size, angle, cycle * 3.0f, 0.0f,
                    grain % 5 == 0 ? DUST : grain % 3 == 0 ? SAND_LIGHT : SAND,
                    Math.round(a * (1.0f - cycle) * (0.44f + 0.28f * h)));
        }

        // --- Voile de poussière : quelques cubes larges et translucides qui ferment l'arrière. ---
        for (int veil = 0; veil < 12; veil++) {
            float h = hash(salt, 7800 + veil * 37);
            float angle = veil * TAU / 12.0f + t * 4.0f + h;
            float radius = 1.45f + h * 0.55f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                    -0.70f + h * 1.50f + settle * 0.40f,
                    0.16f, 0.13f, 0.16f, angle, h * TAU, 0.0f,
                    SAND_DARK, Math.round(a * (0.10f + 0.10f * h)));
        }
    }

    // ============================================================================================
    // 3. TEMPEST — deux entonnoirs contra-rotatifs et œil calme
    // ============================================================================================

    /**
     * Chorégraphie (durée 120 ticks) : les deux entonnoirs se déploient (0,00–0,26), l'œil s'ouvre
     * (0,20–0,55), les débris orbitent (0,30–0,80), puis tout s'effondre vers le centre (0,80–1,00).
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

        // --- Deux entonnoirs contra-rotatifs : trois brins chacun, sens opposés. ---
        for (int funnel = 0; funnel < 2; funnel++) {
            float dir = funnel == 0 ? 1.0f : -1.0f;
            float offset = funnel == 0 ? -0.34f : 0.34f;
            for (int strand = 0; strand < 3; strand++) for (int level = 0; level < 16; level++) {
                float u = level / 15.0f;
                float h = hash(salt, 8100 + funnel * 401 + strand * 97 + level * 13);
                float angle = strand * 2.0943951f + u * 7.0f * dir + spin * dir * (1.0f - u * 0.35f);
                float radius = (0.28f + u * 2.30f) * shrink * (0.88f + h * 0.20f) * open;
                float f = Mth.cos(angle) * radius + offset * (1.0f - u);
                float l = Mth.sin(angle) * radius;
                float y = -1.15f + u * 3.80f;
                float size = (0.105f + u * 0.020f) * (0.80f + h * 0.32f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        size * 1.60f, size * 0.85f, size,
                        angle, u * 0.55f * dir, 0.0f,
                        color(AIR_DEEP, PALE, 0.22f + u * 0.46f + h * 0.22f),
                        Math.round(a * open * (1.0f - collapse * 0.35f) * (0.26f + 0.22f * (1.0f - u))));
            }
        }

        // --- Œil : anneau net et centre parfaitement vide (le calme du cyclone). ---
        if (eye > 0.02f) {
            float eyeY = 1.42f;
            float eyeR = 0.86f * open * (1.0f + 0.06f * Mth.sin(t * 9.0f));
            for (int i = 0; i < 30; i++) {
                float a0 = i * TAU / 30.0f + spin * 0.35f, a1 = (i + 1) * TAU / 30.0f + spin * 0.35f;
                link(vc, mat, tmp, cx, cy + eyeY, cz, dx, dz,
                        Mth.cos(a0) * eyeR, Mth.sin(a0) * eyeR, 0.0f,
                        Mth.cos(a1) * eyeR, Mth.sin(a1) * eyeR, 0.0f,
                        i % 6 == 0 ? 0.026f : 0.014f,
                        i % 6 == 0 ? WHITE : GLASS, WHITE,
                        Math.round(a * eye * 0.72f));
            }
            for (int spoke = 0; spoke < 6; spoke++) {
                float sa = spoke * TAU / 6.0f + spin * 0.35f;
                link(vc, mat, tmp, cx, cy + eyeY, cz, dx, dz,
                        Mth.cos(sa) * eyeR * 0.20f, Mth.sin(sa) * eyeR * 0.20f, 0.0f,
                        Mth.cos(sa) * eyeR * 0.86f, Mth.sin(sa) * eyeR * 0.86f, 0.0f,
                        0.012f, AZURE, WHITE, Math.round(a * eye * 0.34f));
            }
        }

        // --- Débris en orbite : morceaux d'ardoise arrachés au sol, deux orbites croisées. ---
        for (int debris = 0; debris < 9; debris++) {
            float h = hash(salt, 8700 + debris * 59);
            float h2 = hash(salt, 8800 + debris * 71);
            float orbit = 0.95f + h * 1.25f;
            float speed = 1.15f + h2 * 1.10f;
            float angle = debris * 2.3999632f + t * speed * 6.0f;
            float y = -0.85f + h * 3.10f + Mth.sin(t * 6.0f + debris) * 0.18f;
            float radius = orbit * (1.0f - collapse * 0.60f) * open;
            float size = 0.060f + h2 * 0.055f;
            rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(angle) * radius, Mth.sin(angle) * radius, y,
                    size * 1.30f, size * 0.80f, size,
                    angle, t * 2.4f + h * TAU, angle * 0.7f,
                    debris % 3 == 0 ? SLATE : debris % 3 == 1 ? SLATE_DARK : STEEL,
                    Math.round(a * open * (0.46f + h * 0.30f) * (1.0f - collapse * 0.40f)));
        }

        // --- Anneau de sol : la base des deux entonnoirs. ---
        for (int i = 0; i < 32; i++) {
            float a0 = i * TAU / 32.0f + spin * 0.55f, a1 = (i + 1) * TAU / 32.0f + spin * 0.55f;
            float r0 = (2.55f * open * shrink) * (0.94f + 0.06f * Mth.sin(i * 1.7f + t * 7.0f));
            link(vc, mat, tmp, cx, cy - 1.16f, cz, dx, dz,
                    Mth.cos(a0) * r0, Mth.sin(a0) * r0, 0.0f,
                    Mth.cos(a1) * r0, Mth.sin(a1) * r0, 0.0f,
                    i % 4 == 0 ? 0.022f : 0.012f, i % 4 == 0 ? PALE : AIR, WHITE,
                    Math.round(a * open * 0.46f * (1.0f - collapse * 0.30f)));
        }

        // --- Éclatement sommital : le vent est recraché vers le haut en fin de cycle. ---
        float burst = smooth((t - 0.62f) / 0.38f);
        if (burst > 0.02f) {
            for (int i = 0; i < 20; i++) {
                float h = hash(salt, 9100 + i * 43);
                float angle = i * TAU / 20.0f + spin;
                float radius = (0.55f + burst * (1.35f + h * 1.10f)) * open;
                float y = 2.55f + burst * (0.55f + h * 0.90f);
                float size = 0.048f + h * 0.038f;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(angle) * radius, Mth.sin(angle) * radius, y,
                        size * 1.40f, size * 0.75f, size,
                        angle, burst * 1.2f + h, 0.0f,
                        i % 4 == 0 ? WHITE : i % 2 == 0 ? GLASS : PALE,
                        Math.round(a * burst * (0.34f + h * 0.24f) * (1.0f - collapse * 0.50f)));
            }
        }

        // --- Effondrement : les deux entonnoirs se rejoignent en un point, puis s'éteignent. ---
        if (collapse > 0.25f) {
            for (int i = 0; i < 14; i++) {
                float h = hash(salt, 9500 + i * 37);
                float angle = i * TAU / 14.0f + spin * 2.0f;
                float radius = (1.0f - collapse) * 1.30f * (0.6f + h * 0.6f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        1.42f + (h - 0.5f) * 0.50f,
                        0.055f, 0.055f, 0.055f, angle, h * TAU, 0.0f,
                        WHITE, Math.round(a * collapse * 0.60f));
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
