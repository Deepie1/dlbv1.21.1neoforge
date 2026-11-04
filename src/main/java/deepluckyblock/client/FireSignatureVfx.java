package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Trois signatures Fire distinctes, inspirées des références VFX recensées : vague de lave au sol,
 * croissant de feu d'âme épais, météore avec traîne et cratère. Uniquement visuel : aucun gameplay.
 */
public final class FireSignatureVfx {
    public static final int MOLTEN = 0;
    public static final int SOUL = 1;
    public static final int METEOR = 2;

    private static final int CRUST = 0xFF351B19;
    private static final int LAVA_DARK = 0xFF8E2816;
    private static final int LAVA = 0xFFE34B16;
    private static final int HOT = 0xFFFF941F;
    private static final int CORE = 0xFFFFE783;
    private static final int SOUL_DARK = 0xFF07536B;
    private static final int SOUL_BLUE = 0xFF119FAF;
    private static final int SOUL_CYAN = 0xFF43E5DE;
    private static final int SOUL_CORE = 0xFFC4FFFF;
    private static final int ROCK = 0xFF30272A;
    private static final int ROCK_HOT = 0xFF6D3528;

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

    /** MOLTEN FIRE WAVE : une crête liquide avance, s'enroule, éclabousse puis retourne au sol. */
    private static void renderMolten(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.10f);
        float exit = smooth01((t - 0.76f) / 0.24f);
        float envelope = birth * (1.0f - exit);
        float ground = cy - 0.48f * S;
        float travel = (-1.45f + 1.85f * smooth01((t - 0.05f) / 0.58f)) * S;
        float sideX = dirZ, sideZ = -dirX;
        int a = Math.round(alpha * envelope);

        // Dix-sept colonnes jointives, deux couches : la vague est une masse, pas des fils.
        for (int lane = -8; lane <= 8; lane++) {
            float laneWave = 0.88f + 0.12f * Mth.cos(lane * 1.37f);
            for (int k = 0; k < 28; k++) {
                float u = k / 27.0f;
                float crest = 0.56f * u * u * smooth01((t - 0.18f) / 0.34f);
                float forward = travel + crest * S;
                float y = ground + u * 1.58f * laneWave * S * birth - exit * 1.45f * S;
                float side = lane * 0.105f * S;
                for (int layer = 0; layer < 2; layer++) {
                    float depth = (layer - 0.5f) * 0.085f * S;
                    float half = (0.061f + 0.008f * (1.0f - u)) * S * (1.0f - 0.48f * exit);
                    int rgb = u > 0.86f ? CORE : u > 0.65f ? HOT : layer == 0 ? LAVA : LAVA_DARK;
                    cube(vc, mat, tmp,
                            cx + dirX * (forward + depth) + sideX * side,
                            y, cz + dirZ * (forward + depth) + sideZ * side,
                            half, half * 1.06f, half, rgb, a);
                }
            }
        }

        // Dalles laissées par le front : elles naissent derrière lui puis refroidissent.
        for (int row = 0; row < 7; row++) {
            float reveal = smooth01((t - 0.10f - row * 0.045f) / 0.14f);
            if (reveal <= 0.0f) continue;
            float along = (-1.30f + row * 0.31f) * S;
            for (int lane = -4; lane <= 4; lane++) {
                float h = hash01(salt, row * 31 + lane * 7 + 80);
                if (h < 0.15f && row > 1) continue;
                float side = lane * 0.205f * S;
                float hs = (0.095f + h * 0.018f) * S * reveal * (1.0f - exit * 0.65f);
                cube(vc, mat, tmp, cx + dirX * along + sideX * side,
                        ground + 0.025f * S - exit * 0.24f * S,
                        cz + dirZ * along + sideZ * side,
                        hs, 0.022f * S * reveal, hs,
                        h > 0.72f ? HOT : h > 0.36f ? LAVA_DARK : CRUST,
                        Math.round(a * (0.62f + 0.30f * reveal)));
            }
        }

        // Éclaboussures issues de la crête, avec gravité et extinction à la fin.
        for (int p = 0; p < 34; p++) {
            float launch = 0.28f + (p % 6) * 0.045f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 43 + 300);
            float lane = (p % 9 - 4) * 0.205f * S;
            float time = age * 2.15f;
            float vx = dirX * (0.55f + h * 0.75f) * S + sideX * lane * 0.30f;
            float vz = dirZ * (0.55f + h * 0.75f) * S + sideZ * lane * 0.30f;
            float vy = (1.20f + hash01(salt, p * 47 + 12) * 0.95f) * S;
            float px = cx + dirX * (travel + 0.48f * S) + sideX * lane + vx * time;
            float py = ground + 1.05f * S + vy * time - 2.70f * S * time * time;
            float pz = cz + dirZ * (travel + 0.48f * S) + sideZ * lane + vz * time;
            if (py < ground) continue;
            float hs = (0.030f + 0.025f * h) * S * (1.0f - exit);
            cube(vc, mat, tmp, px, py, pz, hs, hs * 1.25f, hs,
                    h > 0.68f ? CORE : h > 0.30f ? HOT : LAVA, Math.round(a * 0.82f));
        }
    }

    /** SOUL FIRE CRESCENT : large croissant cyan, rémanence, puis trois âmes qui se dispersent. */
    private static void renderSoul(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.10f);
        float sweep = smooth01((t - 0.06f) / 0.34f);
        float impact = smooth01((t - 0.48f) / 0.18f);
        float exit = smooth01((t - 0.72f) / 0.28f);
        float sideX = dirZ, sideZ = -dirX;
        float centerForward = (-1.30f + 1.45f * sweep + 0.30f * impact) * S;
        float radius = (0.82f + 0.14f * impact) * S;
        int crescentA = Math.round(alpha * birth * (1.0f - exit) * (1.0f - impact * 0.42f));

        // Arc vertical de 268 degrés, trois cubes d'épaisseur et extrémités effilées.
        for (int k = 0; k < 84; k++) {
            float u = k / 83.0f;
            if (u > sweep) continue;
            float angle = -2.34f + u * 4.68f;
            float taper = 0.52f + 0.48f * Mth.sin(u * (float) Math.PI);
            for (int thick = -1; thick <= 1; thick++) {
                float r = radius + thick * 0.064f * S * taper;
                float lateral = Mth.cos(angle) * r;
                float y = cy + Mth.sin(angle) * r + 0.10f * S;
                float hs = 0.052f * S * taper * (1.0f - exit * 0.72f);
                int rgb = thick == 0 ? SOUL_CYAN : thick < 0 ? SOUL_DARK : SOUL_BLUE;
                if (u > 0.43f && u < 0.57f && thick == 0) rgb = SOUL_CORE;
                cube(vc, mat, tmp, cx + dirX * centerForward + sideX * lateral,
                        y, cz + dirZ * centerForward + sideZ * lateral,
                        hs, hs, hs, rgb, crescentA);
            }
        }

        // Deux rémanences discrètes qui suivent le même arc, jamais une seconde attaque.
        for (int echo = 1; echo <= 2; echo++) {
            float back = centerForward - echo * 0.16f * S;
            for (int k = 0; k < 56; k++) {
                float u = k / 55.0f;
                if (u > sweep) continue;
                float angle = -2.30f + u * 4.60f;
                float lateral = Mth.cos(angle) * radius;
                float y = cy + Mth.sin(angle) * radius + 0.10f * S;
                float hs = 0.030f * S * (1.0f - exit);
                cube(vc, mat, tmp, cx + dirX * back + sideX * lateral, y,
                        cz + dirZ * back + sideZ * lateral, hs, hs, hs,
                        echo == 1 ? SOUL_BLUE : SOUL_DARK,
                        Math.round(crescentA * (0.36f / echo)));
            }
        }

        // Après le contact, trois âmes spirales montent et s'éteignent.
        for (int wisp = 0; wisp < 3; wisp++) {
            for (int k = 0; k < 30; k++) {
                float u = k / 29.0f;
                float reveal = smooth01((t - 0.42f - wisp * 0.035f - u * 0.10f) / 0.15f);
                if (reveal <= 0.0f) continue;
                float age = Mth.clamp((t - 0.42f) / 0.58f, 0.0f, 1.0f);
                float angle = wisp * 2.094f + age * 5.2f + u * 1.4f;
                float rad = (0.24f + age * (0.48f + wisp * 0.12f) + u * 0.12f) * S;
                float px = cx + Mth.cos(angle) * rad + dirX * 0.15f * S;
                float pz = cz + Mth.sin(angle) * rad + dirZ * 0.15f * S;
                float py = cy - 0.18f * S + (u * 0.62f + age * (0.62f + wisp * 0.15f)) * S;
                float hs = (0.038f - u * 0.012f) * S * reveal * (1.0f - exit);
                cube(vc, mat, tmp, px, py, pz, hs, hs * 1.35f, hs,
                        k % 7 == 0 ? SOUL_CORE : wisp == 1 ? SOUL_CYAN : SOUL_BLUE,
                        Math.round(alpha * reveal * (1.0f - exit)));
            }
        }
    }

    /** BRIMSTONE METEOR : un noyau rocheux incandescent descend, frappe et laisse un cratère. */
    private static void renderMeteor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dirX, float dirZ, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.07f);
        float descent = smooth01((t - 0.04f) / 0.40f);
        float impact = smooth01((t - 0.44f) / 0.15f);
        float exit = smooth01((t - 0.78f) / 0.22f);
        float ground = cy - 0.48f * S;
        float startForward = -1.85f * S;
        float mx = cx + dirX * startForward * (1.0f - descent);
        float mz = cz + dirZ * startForward * (1.0f - descent);
        float my = ground + (1.0f - descent) * 4.20f * S + 0.38f * S;

        // Noyau plein de 7x7x7 cellules maximum, retiré dès l'impact.
        if (t < 0.49f) {
            float rockFade = 1.0f - smooth01((t - 0.43f) / 0.06f);
            float cell = 0.105f * S * birth;
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                float d = Mth.sqrt(x * x + y * y + z * z);
                if (d > 3.35f) continue;
                float h = hash01(salt, (x + 5) * 113 + (y + 5) * 37 + z + 20);
                int rgb = h > 0.83f ? HOT : h > 0.63f ? ROCK_HOT : ROCK;
                cube(vc, mat, tmp, mx + x * cell, my + y * cell, mz + z * cell,
                        cell * 0.54f, cell * 0.54f, cell * 0.54f, rgb,
                        Math.round(alpha * rockFade * birth));
            }

            // Traîne attachée à la trajectoire : roche -> orange -> jaune au contact du noyau.
            for (int k = 1; k <= 20; k++) {
                float u = k / 20.0f;
                float tx = mx - dirX * startForward * u * 0.55f;
                float tz = mz - dirZ * startForward * u * 0.55f;
                float ty = my + u * 1.85f * S;
                for (int lobe = 0; lobe < 3; lobe++) {
                    float ang = lobe * 2.094f + k * 1.71f;
                    float hs = (0.068f - u * 0.032f) * S * birth;
                    cube(vc, mat, tmp, tx + Mth.cos(ang) * hs * 0.70f, ty,
                            tz + Mth.sin(ang) * hs * 0.70f, hs, hs * 1.30f, hs,
                            u < 0.25f ? CORE : u < 0.68f ? HOT : LAVA_DARK,
                            Math.round(alpha * birth * (1.0f - u * 0.65f)));
                }
            }
        }

        // Onde et cratère concentriques au sol, puis refroidissement/dispersion.
        for (int ring = 0; ring < 3; ring++) {
            float reveal = smooth01((t - 0.44f - ring * 0.035f) / 0.14f);
            if (reveal <= 0.0f) continue;
            float radius = (0.32f + ring * 0.34f + impact * 0.34f) * S;
            for (int k = 0; k < 48; k++) {
                if ((k + ring * 3) % 11 == 0) continue;
                float ang = k * (float) (Math.PI * 2.0) / 48.0f;
                float hs = (0.055f - ring * 0.008f) * S * reveal * (1.0f - exit * 0.70f);
                cube(vc, mat, tmp, cx + Mth.cos(ang) * radius,
                        ground + 0.035f * S - exit * 0.28f * S,
                        cz + Mth.sin(ang) * radius, hs, 0.026f * S * reveal, hs,
                        ring == 0 && k % 4 == 0 ? CORE : ring == 2 ? CRUST : HOT,
                        Math.round(alpha * reveal * (1.0f - exit) * (0.92f - ring * 0.16f)));
            }
        }

        // Fragments projetés par l'impact, sans appliquer de dégâts supplémentaires.
        for (int p = 0; p < 38; p++) {
            float age = t - 0.45f - (p % 5) * 0.012f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 59 + 700);
            float ang = p * 2.3999632f;
            float time = age * 2.0f;
            float speed = (0.70f + h * 1.15f) * S;
            float px = cx + Mth.cos(ang) * speed * time;
            float pz = cz + Mth.sin(ang) * speed * time;
            float py = ground + 0.16f * S + (1.15f + h * 1.45f) * S * time - 3.10f * S * time * time;
            if (py < ground - 0.05f * S) continue;
            float hs = (0.030f + h * 0.035f) * S * (1.0f - exit);
            cube(vc, mat, tmp, px, py, pz, hs, hs, hs,
                    p % 4 == 0 ? ROCK : h > 0.58f ? HOT : LAVA_DARK,
                    Math.round(alpha * (1.0f - exit) * 0.88f));
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
