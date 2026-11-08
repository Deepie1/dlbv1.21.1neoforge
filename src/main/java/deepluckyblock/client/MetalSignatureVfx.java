package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Trois signatures Métal : précision, ricochet prismatique et arc circulaire annulateur. */
public final class MetalSignatureVfx {
    public static final int RAZOR = 0;
    public static final int PRISM = 1;
    public static final int ANNIHILATE = 2;

    private static final int STEEL_DARK = 0x454B52;
    private static final int STEEL = 0x747A82;
    private static final int SILVER = 0x9EA3AA;
    private static final int EDGE = 0xC4C8CD;
    private static final int GLINT = 0xE2E5E8;
    private static final int GRIP = 0x353A40;

    private MetalSignatureVfx() { }

    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t, float dx, float dz,
                              int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 0) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        if (variant == RAZOR) renderRazor(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else if (variant == PRISM) renderPrism(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else renderAnnihilatingArc(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
    }

    /** RAZOR GLEAM — un seul iaido, une lame complète et un reflet qui frappe au tick 10. */
    private static void renderRazor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        float enter = smooth(t / 0.08f);
        float exit = 1.0f - smooth((t - 0.76f) / 0.24f);
        int a = Math.round(alpha * enter * exit);
        float draw = smooth((t - 0.055f) / 0.125f); // Impact à 10/56 = 0,179.
        float bladeF = -2.85f + draw * 5.70f;
        float bladeY = 0.12f + 0.22f * Mth.sin(draw * (float) Math.PI);

        // Lame d'escrime complète, tranchant asymétrique, fuller, garde, grip et pommeau.
        for (int part = 0; part < 20; part++) {
            float u = part / 19.0f;
            float f = bladeF - 0.95f + u * 2.25f;
            float width = part > 16 ? Math.max(0.012f, 0.070f - (part - 16) * 0.018f) : 0.070f;
            box(vc, mat, tmp, cx, cy + (bladeY), cz, dx, dz, f, 0.0f,
                    0.075f, 0.030f, width, part % 4 == 0 ? EDGE : SILVER, a);
            box(vc, mat, tmp, cx, cy + (bladeY + 0.035f), cz, dx, dz, f, -0.018f,
                    0.070f, 0.012f, 0.018f, GLINT, a);
            if (part < 15) box(vc, mat, tmp, cx, cy + (bladeY + 0.006f), cz, dx, dz, f, 0.030f,
                    0.055f, 0.012f, 0.012f, STEEL_DARK, Math.round(a * 0.72f));
        }
        for (int guard = -4; guard <= 4; guard++) {
            box(vc, mat, tmp, cx, cy + (bladeY), cz, dx, dz, bladeF - 1.03f, guard * 0.060f,
                    0.045f, 0.050f, 0.042f, guard == 0 ? EDGE : STEEL, a);
        }
        for (int grip = 0; grip < 6; grip++) {
            box(vc, mat, tmp, cx, cy + (bladeY), cz, dx, dz,
                    bladeF - 1.15f - grip * 0.085f, 0.0f,
                    0.052f, 0.050f, 0.050f, (grip & 1) == 0 ? GRIP : STEEL_DARK, a);
        }
        cube(vc, mat, tmp, cx, cy + (bladeY), cz, dx, dz, bladeF - 1.70f, 0.0f, 0.075f, EDGE, a);

        // Trait unique : aucune gerbe de slashes concurrents.
        float cut = smooth((t - 0.14f) / 0.12f);
        if (cut > 0.0f && cut < 1.0f) {
            for (int i = 0; i < 42; i++) {
                float u = i / 41.0f;
                if (u > cut) continue;
                float f = -1.85f + u * 3.70f;
                float y = -0.68f + u * 1.42f;
                box(vc, mat, tmp, cx, cy + (y), cz, dx, dz, f, 0.0f,
                        0.055f, 0.025f, 0.018f, i % 5 == 0 ? GLINT : EDGE,
                        Math.round(a * (1.0f - cut * 0.45f)));
            }
        }
        float flash = 1.0f - smooth(Math.abs(t - 0.179f) / 0.09f);
        if (flash > 0.0f) {
            for (int axis = 0; axis < 3; axis++) {
                float angle = axis * 1.5707963f + 0.7853982f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        -Mth.cos(angle) * 0.72f * flash, 0.18f - Mth.sin(angle) * 0.72f * flash, 0.0f,
                        Mth.cos(angle) * 0.72f * flash, 0.18f + Mth.sin(angle) * 0.72f * flash, 0.0f,
                        0.022f, GLINT, EDGE, Math.round(a * flash));
            }
        }
        for (int i = 0; i < 24; i++) {
            float h = hash(salt, 100 + i * 37);
            float age = clamp((t - 0.17f) / 0.50f);
            if (age <= 0.0f) continue;
            float angle = i * 2.3999632f;
            float r = age * (0.35f + h * 1.20f);
            box(vc, mat, tmp, cx, cy + (0.15f + age * (h - 0.35f) * 1.25f), cz, dx, dz,
                    Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.018f, 0.018f, 0.018f, i % 3 == 0 ? GLINT : STEEL,
                    Math.round(a * (1.0f - age) * 0.72f));
        }
    }

    /** SUPREME PRISM EDGE — prisme octaédrique stylisé, trois lames et réfraction au tick 34. */
    private static void renderPrism(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        float birth = smooth(t / 0.10f);
        float exit = 1.0f - smooth((t - 0.80f) / 0.20f);
        int a = Math.round(alpha * birth * exit);
        float open = smooth((t - 0.04f) / 0.22f);
        float strike = smooth((t - 0.30f) / 0.075f); // Ricochet serveur à 34/96 = 0,354.
        float spin = t * 4.2f;

        // Prisme central : six sommets, douze arêtes, facettes internes et noyau plein.
        float prismRadius = 0.35f + open * 0.65f;
        float prismY = 0.30f + open * 0.35f;
        float[] top = {0.0f, prismY + prismRadius * 1.35f, 0.0f};
        float[] bottom = {0.0f, prismY - prismRadius * 1.35f, 0.0f};
        for (int side = 0; side < 4; side++) {
            float angle = spin + side * 1.5707963f;
            float f = Mth.cos(angle) * prismRadius, l = Mth.sin(angle) * prismRadius;
            float nextAngle = spin + (side + 1) * 1.5707963f;
            float nf = Mth.cos(nextAngle) * prismRadius, nl = Mth.sin(nextAngle) * prismRadius;
            link(vc, mat, tmp, cx, cy, cz, dx, dz, top[0], top[1], top[2], f, prismY, l,
                    0.025f, EDGE, SILVER, a);
            link(vc, mat, tmp, cx, cy, cz, dx, dz, bottom[0], bottom[1], bottom[2], f, prismY, l,
                    0.025f, STEEL, EDGE, a);
            link(vc, mat, tmp, cx, cy, cz, dx, dz, f, prismY, l, nf, prismY, nl,
                    0.022f, SILVER, EDGE, a);
            for (int facet = 1; facet <= 3; facet++) {
                float u = facet / 4.0f;
                box(vc, mat, tmp, cx, cy + (Mth.lerp(u, prismY, top[1])), cz, dx, dz,
                        f * u, l * u, 0.055f, 0.055f, 0.055f,
                        color(STEEL, EDGE, u), Math.round(a * 0.72f));
            }
        }
        for (int layer = -4; layer <= 4; layer++) {
            float width = (5 - Math.abs(layer)) * 0.075f * open;
            rot(vc, mat, tmp, cx, cy + (prismY + layer * 0.13f * open), cz, dx, dz, 0.0f, 0.0f,
                    width, 0.075f, width, spin, 0.0f, 0.0f,
                    layer == 0 ? GLINT : SILVER, Math.round(a * 0.78f));
        }

        // Trois lames extérieures convergent, puis repartent selon trois directions de ricochet.
        float converge = smooth((t - 0.08f) / 0.24f);
        for (int blade = 0; blade < 3; blade++) {
            float angle = blade * 2.0943951f + spin * 0.30f;
            float radius = Mth.lerp(converge, 2.35f, 1.18f) + strike * 1.15f;
            prismBlade(vc, mat, tmp, cx, cy, cz, dx, dz, angle, radius, 0.10f + blade * 0.25f,
                    a, blade);
        }

        if (strike > 0.0f) {
            for (int ray = 0; ray < 3; ray++) {
                float angle = ray * 2.0943951f + spin * 0.30f;
                for (int i = 0; i < 30; i++) {
                    float u = i / 29.0f;
                    if (u > strike) continue;
                    float r = 0.55f + u * 3.65f;
                    float y = 0.18f + ray * 0.25f + Mth.sin(u * 5.0f) * 0.08f;
                    box(vc, mat, tmp, cx, cy + (y), cz, dx, dz,
                            Mth.cos(angle) * r, Mth.sin(angle) * r,
                            0.035f, 0.022f, 0.022f,
                            i % 6 == 0 ? GLINT : EDGE, Math.round(a * (1.0f - u * 0.55f)));
                }
            }
        }
        for (int i = 0; i < 36; i++) {
            float h = hash(salt, 500 + i * 41);
            float age = clamp((t - 0.35f) / 0.48f);
            if (age <= 0.0f) continue;
            float angle = i * 2.3999632f;
            float r = 0.45f + age * (0.80f + h * 1.85f);
            box(vc, mat, tmp, cx, cy + (0.40f + (h - 0.5f) * age * 2.1f), cz, dx, dz,
                    Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.025f, 0.025f, 0.025f, i % 4 == 0 ? GLINT : STEEL,
                    Math.round(a * (1.0f - age) * 0.76f));
        }
    }

    private static void prismBlade(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float dx, float dz,
                                   float angle, float radius, float y, int alpha, int blade) {
        float radialF = Mth.cos(angle), radialL = Mth.sin(angle);
        for (int part = 0; part < 14; part++) {
            float u = part / 13.0f;
            float r = radius - u * 1.20f;
            float width = part > 10 ? Math.max(0.015f, 0.070f - (part - 10) * 0.018f) : 0.070f;
            box(vc, mat, tmp, cx, cy + (y + u * 0.16f), cz, dx, dz,
                    radialF * r, radialL * r, 0.055f, 0.025f, width,
                    part % 4 == blade % 4 ? EDGE : SILVER, alpha);
        }
        box(vc, mat, tmp, cx, cy + (y - 0.05f), cz, dx, dz, radialF * (radius + 0.10f), radialL * (radius + 0.10f),
                0.12f, 0.065f, 0.12f, STEEL_DARK, alpha);
        for (int guard = -3; guard <= 3; guard++) {
            float tangentF = -radialL, tangentL = radialF;
            box(vc, mat, tmp, cx, cy + (y), cz, dx, dz,
                    radialF * (radius + 0.02f) + tangentF * guard * 0.065f,
                    radialL * (radius + 0.02f) + tangentL * guard * 0.065f,
                    0.040f, 0.040f, 0.040f, guard == 0 ? EDGE : STEEL, alpha);
        }
    }

    /** ANNIHILATING ARC — charge refermée puis double front tranchant à 360° au tick 32. */
    private static void renderAnnihilatingArc(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              float cx, float cy, float cz, float t,
                                              float dx, float dz, int alpha, int salt) {
        float birth = smooth(t / 0.06f);
        float exit = 1.0f - smooth((t - 0.84f) / 0.16f);
        int a = Math.round(alpha * birth * exit);
        float charge = smooth((t - 0.025f) / 0.235f);
        float release = smooth((t - 0.286f) / 0.22f);
        float second = smooth((t - 0.53f) / 0.24f);

        // Deux mâchoires de lame se referment pendant la charge, sans toucher le sol réel.
        for (int jaw = 0; jaw < 2; jaw++) {
            float jawAngle = jaw * (float) Math.PI + charge * 2.35f;
            for (int i = 0; i < 52; i++) {
                float u = i / 51.0f;
                float angle = jawAngle + u * 2.15f;
                float radius = 3.20f - charge * 0.80f;
                float y = 0.10f + jaw * 0.38f + Mth.sin(u * (float) Math.PI) * 0.28f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                rot(vc, mat, tmp, cx, cy + (y), cz, dx, dz, f, l,
                        0.035f, 0.035f, 0.115f, angle, 0.0f, 0.0f,
                    i % 5 == 0 ? EDGE : STEEL, Math.round(a * (1.0f - release * 0.70f)));
            }
        }

        // Front principal continu : 160 maillons, arête externe et contre-lame intérieure.
        if (release > 0.0f && release < 1.0f) {
            float radius = 0.65f + release * 4.75f;
            int ringAlpha = Math.round(a * (1.0f - release * 0.72f));
            for (int i = 0; i < 160; i++) {
                float angle = i * (float) (Math.PI * 2.0 / 160.0);
                float next = (i + 1) * (float) (Math.PI * 2.0 / 160.0);
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                float nf = Mth.cos(next) * radius, nl = Mth.sin(next) * radius;
                link(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.18f, l, nf, 0.18f, nl,
                        0.030f, SILVER, EDGE, ringAlpha);
                if ((i & 3) == 0) {
                    link(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f * 0.92f, 0.18f, l * 0.92f, f * 1.07f, 0.18f, l * 1.07f,
                            0.025f, EDGE, GLINT, ringAlpha);
                }
            }
        }

        // Second arc inverse, plus haut, pour achever la lecture « annihilation » sur 360°.
        if (second > 0.0f && second < 1.0f) {
            float radius = 0.80f + second * 4.35f;
            int ringAlpha = Math.round(a * (1.0f - second * 0.78f));
            for (int i = 0; i < 128; i++) {
                float angle = -i * (float) (Math.PI * 2.0 / 128.0) + second * 1.4f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                rot(vc, mat, tmp, cx, cy + (0.72f), cz, dx, dz, f, l,
                        0.038f, 0.030f, 0.120f, angle, 0.0f, 0.0f,
                        i % 4 == 0 ? GLINT : SILVER, ringAlpha);
            }
        }

        for (int i = 0; i < 72; i++) {
            float h = hash(salt, 900 + i * 43);
            float age = clamp((t - 0.28f) / 0.55f);
            if (age <= 0.0f) continue;
            float angle = i * 2.3999632f;
            float r = age * (2.0f + h * 3.5f);
            box(vc, mat, tmp, cx, cy + (0.15f + age * (0.15f + h * 1.30f)), cz, dx, dz,
                    Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.022f, 0.022f, 0.022f, i % 6 == 0 ? GLINT : STEEL,
                    Math.round(a * (1.0f - age) * 0.70f));
        }
    }

    private static void cube(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f, float lateral, float half, int rgb, int alpha) {
        box(vc, mat, tmp, cx, cy, cz, dx, dz, f, lateral, half, half, half, rgb, alpha);
    }

    private static void box(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float hf, float hy, float hl, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy, z, hf, hy, hl, rgb, alpha);
    }

    private static void rot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float hf, float hy, float hl,
                            float yaw, float pitch, float roll, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy, z,
                hf, hy, hl, yaw, pitch, roll, rgb, alpha);
    }

    private static void link(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f0, float y0, float l0, float f1, float y1, float l1,
                             float half, int rgb, int tip, int alpha) {
        float df = f1 - f0, dy = y1 - y0, dl = l1 - l0;
        float length = Mth.sqrt(df * df + dy * dy + dl * dl);
        if (length < 0.001f || alpha <= 0) return;
        float sx = dx * df - dz * dl, sz = dz * df + dx * dl;
        float yaw = (float) Math.atan2(sx, sz);
        float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
        rot(vc, mat, tmp, cx, cy + (y0 + y1) * 0.5f, cz, dx, dz,
                (f0 + f1) * 0.5f, (l0 + l1) * 0.5f,
                half, length * 0.56f, half, yaw, pitch, 0.0f, color(rgb, tip, 0.32f), alpha);
    }

    private static int color(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, Mth.clamp(u, 0.0f, 1.0f));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float clamp(float x) {
        return Mth.clamp(x, 0.0f, 1.0f);
    }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
