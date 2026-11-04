package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Fire signatures en voxels 3D. Adaptation originale des principes publiquement documentés par
 * IGNIS : surfaces de combustible, nappes de flamme advectées, lobes d'explosion et braises
 * analytiques. Aucun asset ni générateur payant n'est copié ou redistribué.
 */
public final class FireSignatureVfx {
    public static final int MOLTEN = 0;
    public static final int SOUL = 1;
    public static final int METEOR = 2;

    private static final int CRUST = 0xFF28191A;
    private static final int SMOKE = 0xFF41383A;
    private static final int RED = 0xFF8E1D16;
    private static final int ORANGE = 0xFFE34713;
    private static final int AMBER = 0xFFFF8B17;
    private static final int HOT = 0xFFFFD75F;
    private static final int WHITE = 0xFFFFF3C2;
    private static final int SOUL_DARK = 0xFF073C59;
    private static final int SOUL_BLUE = 0xFF087E9C;
    private static final int SOUL_CYAN = 0xFF25D5D4;
    private static final int SOUL_HOT = 0xFF8FFFF0;
    private static final int SOUL_WHITE = 0xFFDCFFFF;
    private static final int ROCK = 0xFF292226;
    private static final int ROCK_HOT = 0xFF713528;

    private FireSignatureVfx() { }

    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant, float scale,
                              int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f) return;
        if (variant == MOLTEN) renderMolten(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
        else if (variant == SOUL) renderSoul(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
        else renderMeteor(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
    }

    /** MOLTEN : rassemblement, déferlante, sol incendié en boucle, extinction et fumée. */
    private static void renderMolten(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.11f);
        float exit = smooth01((t - 0.80f) / 0.20f);
        float ground = cy - 0.48f * S;
        float travel = (-1.65f + 2.05f * smooth01((t - 0.07f) / 0.50f)) * S;
        float sideX = dirZ, sideZ = -dirX;
        float env = birth * (1.0f - exit);

        // La nappe principale est pleine et animée : sa crête avance plus vite que sa base.
        for (int lane = -8; lane <= 8; lane++) {
            float side = lane * 0.105f * S;
            float seed = lane * 1.713f + (salt & 31);
            float heightNoise = 0.88f + 0.12f * Mth.sin(seed + t * 8.0f);
            for (int k = 0; k < 26; k++) {
                float u = k / 25.0f;
                float curl = 0.64f * u * u * smooth01((t - 0.15f) / 0.30f);
                float turbulence = Mth.sin(t * 17.0f - u * 6.2f + seed) * 0.035f * u * S;
                float forward = travel + curl * S + turbulence;
                float localBirth = smooth01((t - 0.018f - u * 0.065f) / 0.11f);
                float y = ground + u * 1.48f * heightNoise * S * localBirth - exit * 1.25f * S;
                for (int layer = 0; layer < 2; layer++) {
                    float depth = (layer - 0.5f) * 0.085f * S;
                    float half = (0.060f + 0.010f * (1.0f - u)) * S * (1.0f - exit * 0.45f);
                    int rgb = fireRamp(u, layer == 0);
                    cube(vc, mat, tmp, cx + dirX * (forward + depth) + sideX * side, y,
                            cz + dirZ * (forward + depth) + sideZ * side,
                            half, half * 1.08f, half, rgb, Math.round(alpha * env));
                }
            }
            // Langues réellement ancrées à la crête : elles suivent son déplacement et son advection.
            float crestBirth = smooth01((t - 0.083f) / 0.11f);
            float crestY = ground + 1.43f * heightNoise * S * crestBirth - exit * 1.25f * S;
            float crestForward = travel + 0.62f * S * smooth01((t - 0.15f) / 0.30f);
            flame(vc, mat, tmp, cx + dirX * crestForward + sideX * side, crestY,
                    cz + dirZ * crestForward + sideZ * side,
                    (0.34f + 0.16f * hash01(salt, lane + 90)) * S,
                    0.038f * S, dirX * 0.55f, dirZ * 0.55f,
                    t * 8.0f + seed, false, S, Math.round(alpha * env), salt + lane * 19);
        }

        // Surface de combustible : dalles, nappes et braises persistent après le passage du front.
        for (int row = 0; row < 7; row++) {
            float along = (-1.48f + row * 0.30f) * S;
            for (int lane = -4; lane <= 4; lane++) {
                int id = row * 23 + lane + 100;
                float h = hash01(salt, id);
                if (h < 0.13f && row > 0) continue;
                float reveal = smooth01((t - 0.20f - row * 0.035f - h * 0.035f) / 0.13f);
                if (reveal <= 0.0f) continue;
                float side = lane * 0.235f * S;
                float px = cx + dirX * along + sideX * side;
                float pz = cz + dirZ * along + sideZ * side;
                groundPatch(vc, mat, tmp, px, ground, pz, reveal * (1.0f - exit),
                        0.30f + h * 0.34f, dirX, dirZ, t, false, S, alpha, salt + id * 17);
            }
        }

        // Projection de gouttes de lave issues de la crête, jamais d'une pluie décorative globale.
        for (int p = 0; p < 44; p++) {
            float launch = 0.26f + (p % 7) * 0.040f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 43 + 300);
            float side = (p % 11 - 5) * 0.18f * S;
            float time = age * 2.20f;
            float px = cx + dirX * (travel + 0.52f * S) + sideX * side
                    + (dirX * (0.65f + h * 0.85f) + sideX * side * 0.32f) * time;
            float pz = cz + dirZ * (travel + 0.52f * S) + sideZ * side
                    + (dirZ * (0.65f + h * 0.85f) + sideZ * side * 0.32f) * time;
            float py = ground + 1.10f * S + (1.25f + h) * S * time - 2.8f * S * time * time;
            if (py < ground) continue;
            float hs = (0.028f + h * 0.028f) * S * (1.0f - exit);
            cube(vc, mat, tmp, px, py, pz, hs, hs * 1.35f, hs,
                    h > 0.66f ? HOT : h > 0.25f ? AMBER : ORANGE, Math.round(alpha * env));
        }
        smokeColumn(vc, mat, tmp, cx, ground, cz, t, 0.68f, 28, S, alpha, salt, dirX, dirZ);
    }

    /** SOUL : deux flammes s'entortillent, tressent un croissant qui tranche, puis se dissipent. */
    private static void renderSoul(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.10f);
        float weaveExit = smooth01((t - 0.42f) / 0.22f);
        float slash = smooth01((t - 0.30f) / 0.34f);
        float exit = smooth01((t - 0.76f) / 0.24f);
        float sideX = dirZ, sideZ = -dirX;
        float ground = cy - 0.48f * S;

        // Entrée logique : deux serpents de feu d'âme montent du sol en double hélice autour du corps.
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 84; k++) {
                float u = k / 83.0f;
                float reveal = smooth01((t - u * 0.19f) / 0.12f);
                if (reveal <= 0.0f) continue;
                float angle = strand * (float) Math.PI + u * 9.4f + t * 7.0f;
                float radius = (0.48f - u * 0.15f) * S;
                float px = cx + Mth.cos(angle) * radius;
                float pz = cz + Mth.sin(angle) * radius;
                float py = ground + u * 1.62f * S;
                float hs = 0.045f * S * reveal * (1.0f - weaveExit * 0.65f);
                cube(vc, mat, tmp, px, py, pz, hs, hs * 1.16f, hs,
                        strand == 0 ? SOUL_CYAN : SOUL_BLUE,
                        Math.round(alpha * birth * reveal * (1.0f - weaveExit)));
                if (k % 6 == 0) {
                    flame(vc, mat, tmp, px, py, pz, 0.18f * S, 0.025f * S,
                            Mth.cos(angle) * 0.35f, Mth.sin(angle) * 0.35f,
                            t * 9.0f + k, true, S,
                            Math.round(alpha * birth * (1.0f - weaveExit)), salt + k + strand * 99);
                }
            }
        }

        // Le croissant n'est plus un anneau plein : deux rubans tressés de flammes le composent.
        float centerForward = (-0.30f + 0.55f * slash + 0.12f * smooth01((t - 0.62f) / 0.16f)) * S;
        float radius = (0.78f + 0.16f * slash) * S;
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 96; k++) {
                float u = k / 95.0f;
                float reveal = smooth01((slash - u * 0.28f) / 0.20f);
                if (reveal <= 0.0f) continue;
                float angle = -2.28f + u * 4.56f;
                float braid = Mth.sin(u * 22.0f + strand * (float) Math.PI + t * 8.0f) * 0.035f * S;
                float r = radius + (strand == 0 ? -0.045f : 0.045f) * S + braid;
                float lateral = Mth.cos(angle) * r;
                float py = cy + Mth.sin(angle) * r + 0.08f * S;
                float px = cx + dirX * centerForward + sideX * lateral;
                float pz = cz + dirZ * centerForward + sideZ * lateral;
                float taper = 0.40f + 0.60f * Mth.sin(u * (float) Math.PI);
                float hs = 0.041f * S * taper * reveal * (1.0f - exit * 0.65f);
                cube(vc, mat, tmp, px, py, pz, hs, hs, hs,
                        strand == 0 ? SOUL_CYAN : SOUL_BLUE,
                        Math.round(alpha * reveal * (1.0f - exit)));
                if (k % 4 == 0) {
                    flame(vc, mat, tmp, px, py, pz,
                            (0.14f + 0.13f * taper) * S, 0.024f * S,
                            dirX * 0.75f + sideX * Mth.cos(angle) * 0.32f,
                            dirZ * 0.75f + sideZ * Mth.cos(angle) * 0.32f,
                            t * 11.0f + angle * 2.0f, true, S,
                            Math.round(alpha * reveal * (1.0f - exit)), salt + k * 13 + strand * 51);
                }
            }
        }

        // Braises d'âme arrachées par le déplacement du croissant, avec une trajectoire cohérente.
        for (int p = 0; p < 64; p++) {
            float launch = 0.40f + (p % 9) * 0.025f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 61 + 500);
            float angle = p * 2.3999632f + age * 4.0f;
            float time = age * 1.8f;
            float radiusE = (0.24f + time * (0.38f + h * 0.40f)) * S;
            float px = cx + dirX * centerForward + Mth.cos(angle) * radiusE;
            float pz = cz + dirZ * centerForward + Mth.sin(angle) * radiusE;
            float py = cy - 0.15f * S + (h * 0.70f + time * 0.65f) * S;
            float hs = (0.018f + h * 0.018f) * S * (1.0f - exit);
            cube(vc, mat, tmp, px, py, pz, hs, hs * 1.45f, hs,
                    p % 9 == 0 ? SOUL_WHITE : p % 3 == 0 ? SOUL_HOT : SOUL_CYAN,
                    Math.round(alpha * (1.0f - exit) * (1.0f - age * 0.45f)));
        }
        smokeColumn(vc, mat, tmp, cx, ground, cz, t, 0.72f, 20, S, alpha, salt, dirX, dirZ);
    }

    /** BRIMSTONE : météore à couronne de flammes, impact en lobes, cratère brûlant puis fumée. */
    private static void renderMeteor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.07f);
        float descent = smooth01((t - 0.035f) / 0.39f);
        float impact = smooth01((t - 0.425f) / 0.15f);
        float exit = smooth01((t - 0.79f) / 0.21f);
        float ground = cy - 0.48f * S;
        float start = -2.10f * S;
        float mx = cx + dirX * start * (1.0f - descent);
        float mz = cz + dirZ * start * (1.0f - descent);
        float my = ground + (1.0f - descent) * 4.50f * S + 0.40f * S;

        if (t < 0.48f) {
            float fade = 1.0f - smooth01((t - 0.425f) / 0.055f);
            float cell = 0.108f * S * birth;
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                float d = Mth.sqrt(x * x + y * y + z * z);
                if (d > 3.35f) continue;
                float h = hash01(salt, (x + 5) * 113 + (y + 5) * 37 + z + 20);
                cube(vc, mat, tmp, mx + x * cell, my + y * cell, mz + z * cell,
                        cell * 0.55f, cell * 0.55f, cell * 0.55f,
                        h > 0.82f ? AMBER : h > 0.60f ? ROCK_HOT : ROCK,
                        Math.round(alpha * birth * fade));
            }
            // Couronne et traîne volumétrique : chaque lobe suit la position historique du noyau.
            for (int k = 0; k < 22; k++) {
                float u = k / 21.0f;
                float tx = mx - dirX * start * u * 0.58f;
                float tz = mz - dirZ * start * u * 0.58f;
                float ty = my + u * 2.05f * S;
                for (int lobe = 0; lobe < 6; lobe++) {
                    float ang = lobe * (float) (Math.PI / 3.0) + k * 0.91f + t * 5.0f;
                    float spread = (0.045f + u * 0.12f) * S;
                    float hs = (0.072f - u * 0.026f) * S * birth;
                    cube(vc, mat, tmp, tx + Mth.cos(ang) * spread, ty + Mth.sin(ang * 1.7f) * spread,
                            tz + Mth.sin(ang) * spread, hs, hs * 1.28f, hs,
                            u < 0.20f ? WHITE : u < 0.55f ? AMBER : u < 0.84f ? ORANGE : RED,
                            Math.round(alpha * birth * (1.0f - u * 0.55f)));
                }
                if (k % 3 == 0) {
                    flame(vc, mat, tmp, tx, ty, tz, (0.26f + u * 0.22f) * S,
                            0.035f * S, -dirX * 0.55f, -dirZ * 0.55f,
                            t * 8.0f + k, false, S,
                            Math.round(alpha * birth * (1.0f - u * 0.55f)), salt + k * 7);
                }
            }
        }

        // Lobes de détonation qui gonflent depuis le point d'impact et refroidissent vers le rouge.
        for (int lobe = 0; lobe < 14; lobe++) {
            float h = hash01(salt, lobe * 71 + 800);
            float reveal = smooth01((t - 0.425f - (lobe % 4) * 0.012f) / 0.12f);
            if (reveal <= 0.0f) continue;
            float ang = lobe * 2.3999632f;
            float radius = (0.12f + impact * (0.48f + h * 0.70f)) * S;
            float px = cx + Mth.cos(ang) * radius;
            float pz = cz + Mth.sin(ang) * radius;
            float py = ground + (0.08f + impact * (0.20f + h * 0.58f)) * S;
            flame(vc, mat, tmp, px, py, pz, (0.38f + h * 0.58f) * S * reveal,
                    (0.045f + h * 0.030f) * S, Mth.cos(ang) * 0.65f, Mth.sin(ang) * 0.65f,
                    t * 10.0f + lobe, false, S,
                    Math.round(alpha * reveal * (1.0f - exit)), salt + lobe * 31);
        }

        // Cratère de combustible : anneau brisé et flammes basses, avec extinction en fin de vie.
        for (int p = 0; p < 36; p++) {
            float h = hash01(salt, p * 47 + 900);
            float ang = p * 2.3999632f;
            float reveal = smooth01((t - 0.46f - (p % 6) * 0.018f) / 0.13f);
            if (reveal <= 0.0f || (p % 11 == 0)) continue;
            float radius = (0.40f + (p % 3) * 0.30f + impact * 0.20f) * S;
            float px = cx + Mth.cos(ang) * radius;
            float pz = cz + Mth.sin(ang) * radius;
            groundPatch(vc, mat, tmp, px, ground, pz, reveal * (1.0f - exit),
                    0.24f + h * 0.36f, Mth.cos(ang), Mth.sin(ang), t,
                    false, S, alpha, salt + p * 29);
        }

        for (int p = 0; p < 54; p++) {
            float age = t - 0.435f - (p % 6) * 0.012f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 59 + 1000);
            float ang = p * 2.3999632f;
            float time = age * 2.0f;
            float speed = (0.70f + h * 1.25f) * S;
            float px = cx + Mth.cos(ang) * speed * time;
            float pz = cz + Mth.sin(ang) * speed * time;
            float py = ground + 0.15f * S + (1.20f + h * 1.55f) * S * time - 3.15f * S * time * time;
            if (py < ground - 0.05f * S) continue;
            float hs = (0.025f + h * 0.032f) * S * (1.0f - exit);
            cube(vc, mat, tmp, px, py, pz, hs, hs, hs,
                    p % 5 == 0 ? ROCK : h > 0.60f ? HOT : ORANGE,
                    Math.round(alpha * (1.0f - exit)));
        }
        smokeColumn(vc, mat, tmp, cx, ground, cz, t, 0.46f, 48, S, alpha, salt, dirX, dirZ);
    }

    /** Surface IGNIS-like : plaque chaude + langue ancrée + lueur basse. reveal pilote entrée/sortie. */
    private static void groundPatch(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float x, float ground, float z, float reveal, float height,
                                    float windX, float windZ, float time, boolean soul, float S,
                                    int alpha, int seed) {
        float h = hash01(seed, 17);
        float plate = (0.085f + h * 0.025f) * S * reveal;
        int a = Math.round(alpha * reveal);
        cube(vc, mat, tmp, x, ground + 0.022f * S, z, plate * 1.35f, 0.018f * S * reveal,
                plate * 1.35f, soul ? SOUL_DARK : CRUST, Math.round(a * 0.64f));
        cube(vc, mat, tmp, x, ground + 0.040f * S, z, plate * 0.72f, 0.014f * S * reveal,
                plate * 0.72f, soul ? SOUL_CYAN : AMBER, Math.round(a * 0.55f));
        flame(vc, mat, tmp, x, ground + 0.045f * S, z, height * S * reveal,
                (0.026f + h * 0.015f) * S, windX * 0.35f, windZ * 0.35f,
                time * 8.0f + h * 6.28f, soul, S, a, seed);
    }

    /**
     * Langue de feu continue : racine blanche, corps orange/ambre, pointe rouge (ou rampe d'âme).
     * Le bruit est advecté vers le haut et les cubes se recouvrent : aucune ligne de points isolés.
     */
    private static void flame(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float x, float y, float z, float height, float width,
                              float windX, float windZ, float phase, boolean soul, float S,
                              int alpha, int seed) {
        if (height <= 0.002f || alpha <= 0) return;
        float jitter = hash01(seed, 33);
        int segments = 13;
        for (int k = 0; k < segments; k++) {
            float u = k / (float) (segments - 1);
            float advect = Mth.sin(phase - u * 5.2f + jitter * 4.0f) * u * u;
            float curl = Mth.sin(phase * 0.63f + u * 8.0f + seed) * u;
            float px = x + (windX * u * u * height + advect * width * 1.8f + curl * width * 0.55f);
            float pz = z + (windZ * u * u * height + Mth.cos(phase - u * 4.7f + seed) * u * width);
            float py = y + u * height;
            float taper = (1.0f - u * 0.78f) * (0.90f + 0.10f * Mth.sin(phase + u * 9.0f));
            float hs = width * taper;
            int rgb = soul ? soulRamp(u, true) : fireRamp(u, true);
            cube(vc, mat, tmp, px, py, pz, hs, Math.max(hs, height / segments * 0.72f), hs,
                    rgb, Math.round(alpha * (1.0f - u * 0.30f)));
            // Lobe extérieur lié au même centre : volume irrégulier, sans particule détachée.
            if (k < segments - 2) {
                float outer = hs * 0.72f;
                cube(vc, mat, tmp, px + Mth.sin(seed + u * 5.0f) * hs * 0.68f,
                        py - height / segments * 0.18f,
                        pz + Mth.cos(seed + u * 5.0f) * hs * 0.68f,
                        outer, outer * 1.12f, outer, soul ? soulRamp(u, false) : fireRamp(u, false),
                        Math.round(alpha * 0.72f * (1.0f - u * 0.35f)));
            }
            if (k % 3 == 0) {
                float aura = hs * 1.70f;
                cube(vc, mat, tmp, px, py, pz, aura, aura * 1.16f, aura, rgb,
                        Math.round(alpha * 0.13f * (1.0f - u * 0.45f)));
            }
        }
    }

    private static int fireRamp(float u, boolean core) {
        if (u < 0.15f) return core ? WHITE : HOT;
        if (u < 0.43f) return core ? HOT : AMBER;
        if (u < 0.76f) return core ? AMBER : ORANGE;
        return core ? ORANGE : RED;
    }

    private static int soulRamp(float u, boolean core) {
        if (u < 0.16f) return core ? SOUL_WHITE : SOUL_HOT;
        if (u < 0.48f) return core ? SOUL_HOT : SOUL_CYAN;
        if (u < 0.78f) return core ? SOUL_CYAN : SOUL_BLUE;
        return core ? SOUL_BLUE : SOUL_DARK;
    }

    /** Fumée seulement après la combustion : cubes qui montent, s'écartent et disparaissent. */
    private static void smokeColumn(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float ground, float cz, float t, float start,
                                    int count, float S, int alpha, int salt, float dirX, float dirZ) {
        for (int p = 0; p < count; p++) {
            float age = t - start - (p % 8) * 0.018f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 67 + 1200);
            float life = Mth.clamp(age / 0.30f, 0.0f, 1.0f);
            float fade = (1.0f - life) * smooth01(age / 0.05f);
            float ang = p * 2.3999632f + life * 1.8f;
            float radius = (0.15f + h * 0.75f + life * 0.35f) * S;
            float px = cx + Mth.cos(ang) * radius + dirX * life * 0.35f * S;
            float pz = cz + Mth.sin(ang) * radius + dirZ * life * 0.35f * S;
            float py = ground + (0.22f + h * 0.45f + life * 1.55f) * S;
            float hs = (0.055f + h * 0.055f + life * 0.05f) * S;
            cube(vc, mat, tmp, px, py, pz, hs, hs * 0.82f, hs, SMOKE,
                    Math.round(alpha * fade * 0.34f));
        }
    }

    private static float smooth01(float value) {
        float x = Mth.clamp(value, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float hash01(int seed, int n) {
        int h = seed ^ n * 0x45D9F3B;
        h = (h ^ (h >>> 16)) * 0x45D9F3B;
        h = (h ^ (h >>> 16)) * 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }

    private static void cube(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float x, float y, float z, float hx, float hy, float hz,
                             int rgb, int alpha) {
        if (alpha <= 0 || hx <= 0.0f || hy <= 0.0f || hz <= 0.0f) return;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, y, z, hx, hy, hz, rgb, alpha);
    }
}
