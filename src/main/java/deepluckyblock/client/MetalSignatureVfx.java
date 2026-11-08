package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Trois grandes signatures Métal, sculptées en voxels et chorégraphiées séparément. */
public final class MetalSignatureVfx {
    public static final int RAZOR = 0;
    public static final int PRISM = 1;
    public static final int ANNIHILATE = 2;

    private static final int BLACK_STEEL = 0x252B31;
    private static final int STEEL_DARK = 0x424B54;
    private static final int STEEL = 0x6F7C87;
    private static final int SILVER = 0xA7B2BC;
    private static final int EDGE = 0xD4DDE4;
    private static final int GLINT = 0xF2F7FA;
    private static final int GRIP = 0x25272D;
    private static final int LACQUER = 0x171D24;
    private static final int BRONZE = 0x9A8060;
    private static final int PRISM_CYAN = 0x9DD8E2;
    private static final int PRISM_ROSE = 0xD8B4D2;
    private static final int PRISM_GOLD = 0xE0D3A6;
    private static final int PRISM_BLUE = 0x849EBD;
    private static final float TAU = (float) (Math.PI * 2.0);

    private MetalSignatureVfx() { }

    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t, float dx, float dz,
                              int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 0) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        Voxels v = new Voxels(vc, mat, tmp, cx, cy, cz, dx, dz);
        if (variant == RAZOR) renderRazor(v, t, alpha, salt);
        else if (variant == PRISM) renderPrism(v, t, alpha, salt);
        else renderAnnihilatingArc(v, t, alpha, salt);
    }

    /**
     * RAZOR GLEAM — un iaido complet : saya, lame courbe à hamon, tsuba, tsuka tressé,
     * déplacement du reflet puis une seule section diagonale. Impact serveur : 14/72.
     */
    private static void renderRazor(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.065f);
        float exit = 1.0f - smooth((t - 0.78f) / 0.22f);
        int a = Math.round(alpha * enter * exit);
        float draw = smooth((t - 0.065f) / 0.13f);
        float recoil = smooth((t - 0.38f) / 0.35f);
        float bladeBase = -2.15f + draw * 3.65f - recoil * 0.35f;
        float bladeY = 0.32f + Mth.sin(draw * (float) Math.PI) * 0.42f;

        // Le fourreau reste derrière : la sortie de lame est lisible avant l'impact.
        float sayaAlpha = 1.0f - smooth((t - 0.28f) / 0.35f) * 0.72f;
        for (int part = 0; part < 30; part++) {
            float u = part / 29.0f;
            float f = -2.55f + u * 3.25f;
            float y = 0.05f + u * u * 0.22f;
            v.rot(f, y, 0.0f, 0.070f, 0.095f, 0.115f, 0.0f, 0.0f, -0.06f * u,
                    part % 6 == 0 ? STEEL_DARK : LACQUER, Math.round(a * sayaAlpha));
            v.box(f, y + 0.080f, -0.012f, 0.064f, 0.012f, 0.025f,
                    part % 5 == 0 ? EDGE : STEEL, Math.round(a * sayaAlpha * 0.72f));
        }
        for (int ring = 0; ring < 3; ring++) {
            float f = -2.38f + ring * 1.22f;
            for (int side = 0; side < 12; side++) {
                float angle = side * TAU / 12.0f;
                v.box(f, 0.10f + Mth.sin(angle) * 0.14f, Mth.cos(angle) * 0.14f,
                        0.025f, 0.030f, 0.030f, ring == 2 ? EDGE : BRONZE,
                        Math.round(a * sayaAlpha));
            }
        }
        // Sageo : cordon en deux boucles asymétriques sous le fourreau.
        for (int cord = 0; cord < 2; cord++) {
            for (int i = 0; i < 24; i++) {
                float u = i / 23.0f;
                float f = -1.65f + cord * 0.55f + Mth.cos(u * TAU) * (0.28f + cord * 0.08f);
                float y = -0.18f - Mth.sin(u * TAU) * (0.30f + cord * 0.08f);
                v.box(f, y, 0.03f + cord * 0.04f, 0.022f, 0.022f, 0.022f,
                        cord == 0 ? STEEL_DARK : BRONZE, Math.round(a * sayaAlpha * 0.85f));
            }
        }

        razorKatana(v, bladeBase, bladeY, a);

        // Un seul reflet parcourt la courbure ; il ne devient jamais une seconde lame.
        float gleamTravel = clamp((t - 0.09f) / 0.48f);
        if (gleamTravel > 0.0f && gleamTravel < 1.0f) {
            float f = bladeBase + 0.20f + gleamTravel * 3.05f;
            float y = bladeY + katanaCurve(gleamTravel) - 0.11f;
            v.rot(f, y, 0.0f, 0.15f, 0.035f, 0.045f, 0.0f, 0.0f, 0.70f,
                    GLINT, a);
            for (int axis = 0; axis < 4; axis++) {
                float angle = axis * TAU / 4.0f + 0.7853982f;
                v.link(f - Mth.cos(angle) * 0.20f, y - Mth.sin(angle) * 0.20f, 0.0f,
                        f + Mth.cos(angle) * 0.20f, y + Mth.sin(angle) * 0.20f, 0.0f,
                        0.018f, GLINT, EDGE, a);
            }
        }

        // La coupe est une section continue, pas une gerbe de slashes concentriques.
        float cut = smooth((t - 0.175f) / 0.105f);
        if (cut > 0.0f && cut < 1.0f) {
            int visible = Math.round(cut * 72);
            for (int i = 0; i < visible; i++) {
                float u = i / 71.0f;
                float f = -2.55f + u * 5.10f;
                float y = -1.02f + u * 2.18f;
                float fade = 1.0f - cut * 0.38f;
                v.rot(f, y, 0.0f, 0.052f, 0.022f, 0.018f, 0.0f, 0.0f, 0.405f,
                        i % 8 == 0 ? GLINT : EDGE, Math.round(a * fade));
                v.box(f, y + 0.045f, 0.035f, 0.040f, 0.010f, 0.012f,
                        SILVER, Math.round(a * fade * 0.48f));
                if (i % 9 == 0) {
                    v.link(f - 0.06f, y - 0.10f, -0.015f, f + 0.06f, y + 0.10f, 0.015f,
                            0.012f, GLINT, SILVER, Math.round(a * fade * 0.75f));
                }
            }
        }

        float flash = 1.0f - smooth(Math.abs(t - 14.0f / 72.0f) / 0.095f);
        if (flash > 0.0f) {
            for (int axis = 0; axis < 8; axis++) {
                float angle = axis * TAU / 8.0f;
                float radius = (axis % 2 == 0 ? 0.95f : 0.55f) * flash;
                v.link(-Mth.cos(angle) * 0.10f, 0.18f - Mth.sin(angle) * 0.10f, 0.0f,
                        Mth.cos(angle) * radius, 0.18f + Mth.sin(angle) * radius, 0.0f,
                        axis % 2 == 0 ? 0.022f : 0.014f, GLINT, EDGE, Math.round(a * flash));
            }
            for (int corner = 0; corner < 4; corner++) {
                float angle = corner * TAU / 4.0f + 0.7853982f;
                v.rot(Mth.cos(angle) * 0.55f, 0.18f + Mth.sin(angle) * 0.55f, 0.0f,
                        0.12f, 0.025f, 0.025f, 0.0f, 0.0f, angle,
                        GLINT, Math.round(a * flash * 0.80f));
            }
        }

        // Limaille arrachée à la section, avec trajectoires individuelles déterministes.
        for (int i = 0; i < 54; i++) {
            float h = hash(salt, 100 + i * 37);
            float age = clamp((t - 0.19f) / 0.55f);
            if (age <= 0.0f) continue;
            float angle = i * 2.3999632f;
            float r = age * (0.45f + h * 1.75f);
            v.rot(Mth.cos(angle) * r, 0.18f + age * (h - 0.32f) * 1.75f, Mth.sin(angle) * r,
                    0.025f, 0.014f, 0.055f, angle, age * 3.0f, h * 2.0f,
                    i % 5 == 0 ? GLINT : STEEL, Math.round(a * (1.0f - age) * 0.80f));
        }
    }

    private static void razorKatana(Voxels v, float base, float y, int alpha) {
        // Quatre matériaux par segment : dos, corps, hamon irrégulier et bevel tranchant.
        for (int part = 0; part < 36; part++) {
            float u = part / 35.0f;
            float f = base + 0.12f + u * 3.30f;
            float curve = katanaCurve(u);
            float width = u > 0.88f ? Math.max(0.018f, 0.115f * (1.0f - (u - 0.88f) / 0.12f)) : 0.115f;
            v.box(f, y + curve + 0.080f, 0.0f, 0.058f, 0.040f, width * 0.55f,
                    STEEL_DARK, alpha);
            v.box(f, y + curve, 0.0f, 0.060f, 0.048f, width,
                    part % 7 == 0 ? EDGE : SILVER, alpha);
            v.box(f, y + curve - 0.073f, 0.0f, 0.061f, 0.018f, width * 0.78f,
                    EDGE, alpha);
            float hamon = -0.025f + Mth.sin(u * 34.0f) * 0.018f;
            v.box(f, y + curve + hamon, -width * 0.76f, 0.048f, 0.012f, 0.012f,
                    part % 3 == 0 ? GLINT : STEEL, Math.round(alpha * 0.86f));
        }
        // Habaki et seppa, puis tsuba octogonale ajourée.
        for (int layer = 0; layer < 4; layer++) {
            v.box(base + 0.05f - layer * 0.055f, y, 0.0f,
                    0.035f, 0.125f, 0.135f, layer == 0 ? BRONZE : STEEL_DARK, alpha);
        }
        for (int side = 0; side < 20; side++) {
            float angle = side * TAU / 20.0f;
            float radius = side % 2 == 0 ? 0.31f : 0.28f;
            v.rot(base - 0.18f, y + Mth.sin(angle) * radius, Mth.cos(angle) * radius,
                    0.038f, 0.047f, 0.047f, 0.0f, angle, 0.0f,
                    side % 5 == 0 ? BRONZE : STEEL_DARK, alpha);
        }
        for (int spoke = 0; spoke < 4; spoke++) {
            float angle = spoke * TAU / 4.0f;
            v.link(base - 0.18f, y, 0.0f, base - 0.18f,
                    y + Mth.sin(angle) * 0.26f, Mth.cos(angle) * 0.26f,
                    0.025f, BRONZE, STEEL, alpha);
        }
        // Tsuka : douze traverses et losanges d'ito alternés, pas un simple manche gris.
        for (int grip = 0; grip < 13; grip++) {
            float f = base - 0.32f - grip * 0.095f;
            v.box(f, y, 0.0f, 0.055f, 0.075f, 0.085f, GRIP, alpha);
            v.rot(f, y + 0.075f, 0.0f, 0.035f, 0.018f, 0.105f,
                    0.0f, 0.0f, grip % 2 == 0 ? 0.62f : -0.62f,
                    grip % 3 == 0 ? SILVER : STEEL_DARK, alpha);
            if (grip % 2 == 0) {
                v.rot(f, y - 0.076f, 0.0f, 0.040f, 0.018f, 0.040f,
                        0.0f, 0.0f, 0.7853982f, EDGE, Math.round(alpha * 0.78f));
            }
        }
        v.box(base - 1.58f, y, 0.0f, 0.095f, 0.090f, 0.105f, BRONZE, alpha);
        v.box(base - 1.70f, y, 0.0f, 0.035f, 0.075f, 0.080f, EDGE, alpha);
    }

    private static float katanaCurve(float u) {
        return u * u * 0.19f + Mth.sin(u * (float) Math.PI) * 0.055f;
    }

    /**
     * SUPREME PRISM EDGE — double prisme triangulaire à facettes, trois lames taillées,
     * lumière incidente et spectres brisés. Ricochets serveur : 42/120.
     */
    private static void renderPrism(Voxels v, float t, int alpha, int salt) {
        float birth = smooth(t / 0.085f);
        float exit = 1.0f - smooth((t - 0.82f) / 0.18f);
        int a = Math.round(alpha * birth * exit);
        float open = smooth((t - 0.025f) / 0.20f);
        float strike = smooth((t - 0.31f) / 0.09f);
        float spin = t * 3.1f;
        float prismY = 0.48f + open * 0.28f;
        float radius = 0.30f + open * 0.86f;
        float halfHeight = 0.45f + open * 0.86f;

        prismCage(v, prismY, radius, halfHeight, spin, a, false);
        prismCage(v, prismY, radius * 0.62f, halfHeight * 0.70f, -spin * 1.45f + 0.52f,
                Math.round(a * 0.74f), true);

        // Facettes rectangulaires individuelles sur les trois pans, avec dispersion discrète.
        int[] spectrum = {PRISM_CYAN, SILVER, PRISM_ROSE, PRISM_GOLD, PRISM_BLUE};
        for (int side = 0; side < 3; side++) {
            float angle = spin + side * TAU / 3.0f;
            float next = spin + (side + 1) * TAU / 3.0f;
            for (int level = 0; level < 7; level++) {
                float y = prismY - halfHeight + (level + 0.5f) * halfHeight * 2.0f / 7.0f;
                for (int tile = 0; tile < 5; tile++) {
                    float u = (tile + 0.5f) / 5.0f;
                    float f = Mth.lerp(u, Mth.cos(angle) * radius, Mth.cos(next) * radius);
                    float l = Mth.lerp(u, Mth.sin(angle) * radius, Mth.sin(next) * radius);
                    int rgb = color(STEEL, spectrum[(side + level + tile) % spectrum.length], 0.46f);
                    v.rot(f, y, l, 0.075f, halfHeight / 8.5f, 0.025f,
                            -angle, 0.0f, 0.0f, rgb, Math.round(a * 0.52f));
                }
            }
        }
        // Noyau stratifié : neuf couches triangulaires, séparées par des interstices réels.
        for (int layer = -4; layer <= 4; layer++) {
            float layerRadius = (5 - Math.abs(layer)) * radius * 0.095f;
            for (int side = 0; side < 3; side++) {
                float angle = -spin * 1.8f + side * TAU / 3.0f;
                float next = -spin * 1.8f + (side + 1) * TAU / 3.0f;
                v.link(Mth.cos(angle) * layerRadius, prismY + layer * 0.19f, Mth.sin(angle) * layerRadius,
                        Mth.cos(next) * layerRadius, prismY + layer * 0.19f, Mth.sin(next) * layerRadius,
                        0.045f, layer == 0 ? GLINT : SILVER, spectrum[(layer + 5) % spectrum.length],
                        Math.round(a * 0.82f));
            }
        }

        float converge = smooth((t - 0.07f) / 0.22f);
        for (int blade = 0; blade < 3; blade++) {
            float angle = blade * TAU / 3.0f + spin * 0.34f;
            float bladeRadius = Mth.lerp(converge, 3.05f, 1.65f) + strike * 0.72f;
            prismBlade(v, angle, bladeRadius, 0.10f + blade * 0.36f, a, blade);
        }

        // Rayon incident blanc, puis séparation visible en deux ricochets et un spectre libre.
        float incident = smooth((t - 0.20f) / 0.11f);
        if (incident > 0.0f && incident < 1.0f) {
            for (int i = 0; i < 38; i++) {
                float u = i / 37.0f;
                if (u > incident) continue;
                v.box(-4.5f + u * 4.1f, prismY, 0.0f, 0.065f, 0.035f, 0.035f,
                        i % 6 == 0 ? GLINT : EDGE, Math.round(a * (0.45f + u * 0.55f)));
            }
        }
        if (strike > 0.0f) {
            for (int ray = 0; ray < 3; ray++) {
                float angle = ray * TAU / 3.0f + spin * 0.20f;
                for (int strand = 0; strand < 3; strand++) {
                    int rgb = strand == 0 ? PRISM_CYAN : strand == 1 ? EDGE : PRISM_ROSE;
                    for (int i = 0; i < 32; i++) {
                        float u = i / 31.0f;
                        if (u > strike) continue;
                        float r = 0.72f + u * (ray == 2 ? 3.25f : 3.85f);
                        float bend = Mth.sin(u * (float) Math.PI * 2.0f) * (0.12f + strand * 0.035f);
                        float y = prismY + (ray - 1) * 0.25f + (strand - 1) * 0.065f
                                + Mth.sin(u * 5.0f + ray) * 0.10f;
                        float f = Mth.cos(angle) * r - Mth.sin(angle) * bend;
                        float l = Mth.sin(angle) * r + Mth.cos(angle) * bend;
                        v.rot(f, y, l, 0.045f, 0.018f, 0.026f, angle, 0.0f, 0.0f,
                                i % 8 == 0 ? GLINT : rgb, Math.round(a * (1.0f - u * 0.46f)));
                    }
                }
            }
            // Deux éclats terminaux matérialisent les cibles secondaires, jamais la cible principale.
            for (int end = 0; end < 2; end++) {
                float angle = end * TAU / 3.0f + spin * 0.20f;
                float f = Mth.cos(angle) * 4.55f, l = Mth.sin(angle) * 4.55f;
                crystalBurst(v, f, prismY + (end - 0.5f) * 0.50f, l, 0.28f + strike * 0.22f,
                        spin + end, Math.round(a * strike), spectrum[end * 2]);
            }
        }

        // Petites facettes orbitales : elles tournent sur elles-mêmes et autour du prisme.
        for (int shard = 0; shard < 48; shard++) {
            float h = hash(salt, 500 + shard * 41);
            float angle = shard * 2.3999632f + spin * (0.25f + h * 0.35f);
            float r = 1.45f + h * 1.55f + strike * 0.55f;
            float y = prismY + Mth.sin(angle * 1.7f) * (0.55f + h * 0.65f);
            v.rot(Mth.cos(angle) * r, y, Mth.sin(angle) * r,
                    0.025f + h * 0.035f, 0.055f, 0.018f,
                    angle, spin + h * 3.0f, h * 2.0f,
                    spectrum[shard % spectrum.length], Math.round(a * (0.40f + h * 0.42f)));
        }
    }

    private static void prismCage(Voxels v, float y, float radius, float halfHeight,
                                  float spin, int alpha, boolean inner) {
        for (int side = 0; side < 3; side++) {
            float angle = spin + side * TAU / 3.0f;
            float next = spin + (side + 1) * TAU / 3.0f;
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float nf = Mth.cos(next) * radius, nl = Mth.sin(next) * radius;
            int rgb = inner ? PRISM_BLUE : EDGE;
            v.link(f, y - halfHeight, l, f, y + halfHeight, l,
                    inner ? 0.018f : 0.030f, rgb, SILVER, alpha);
            v.link(f, y + halfHeight, l, nf, y + halfHeight, nl,
                    inner ? 0.018f : 0.032f, rgb, GLINT, alpha);
            v.link(f, y - halfHeight, l, nf, y - halfHeight, nl,
                    inner ? 0.018f : 0.027f, STEEL, rgb, alpha);
            if (!inner) {
                for (int rivet = 0; rivet < 5; rivet++) {
                    float ry = y - halfHeight + rivet * halfHeight * 0.5f;
                    v.box(f, ry, l, 0.050f, 0.035f, 0.050f,
                            rivet % 2 == 0 ? GLINT : SILVER, Math.round(alpha * 0.88f));
                }
            }
        }
    }

    private static void prismBlade(Voxels v, float angle, float radius, float y,
                                   int alpha, int blade) {
        float rf = Mth.cos(angle), rl = Mth.sin(angle);
        float tf = -rl, tl = rf;
        for (int part = 0; part < 24; part++) {
            float u = part / 23.0f;
            float r = radius - u * 1.62f;
            float rise = u * u * 0.24f;
            float width = u > 0.82f ? Math.max(0.016f, 0.10f * (1.0f - (u - 0.82f) / 0.18f)) : 0.10f;
            int spectral = blade == 0 ? PRISM_CYAN : blade == 1 ? PRISM_ROSE : PRISM_GOLD;
            v.box(rf * r, y + rise + 0.065f, rl * r, 0.048f, 0.032f, width * 0.65f,
                    STEEL_DARK, alpha);
            v.box(rf * r, y + rise, rl * r, 0.050f, 0.038f, width,
                    part % 6 == blade * 2 ? spectral : SILVER, alpha);
            v.box(rf * r + tf * width * 0.72f, y + rise - 0.055f, rl * r + tl * width * 0.72f,
                    0.042f, 0.016f, 0.025f, EDGE, alpha);
            if (part < 18) {
                v.box(rf * r - tf * width * 0.72f, y + rise + 0.010f, rl * r - tl * width * 0.72f,
                        0.035f, 0.020f, 0.018f, spectral, Math.round(alpha * 0.80f));
            }
        }
        // Garde triangulaire, poignée et gemme taille prisme.
        for (int side = 0; side < 3; side++) {
            float ga = side * TAU / 3.0f + angle;
            float gb = (side + 1) * TAU / 3.0f + angle;
            v.link(rf * (radius + 0.10f) + Mth.cos(ga) * 0.30f, y + Mth.sin(ga) * 0.30f,
                    rl * (radius + 0.10f) + Mth.sin(ga) * 0.30f,
                    rf * (radius + 0.10f) + Mth.cos(gb) * 0.30f, y + Mth.sin(gb) * 0.30f,
                    rl * (radius + 0.10f) + Mth.sin(gb) * 0.30f,
                    0.028f, EDGE, STEEL, alpha);
        }
        for (int grip = 0; grip < 7; grip++) {
            v.box(rf * (radius + 0.23f + grip * 0.09f), y, rl * (radius + 0.23f + grip * 0.09f),
                    0.055f, 0.050f, 0.050f, grip % 2 == 0 ? GRIP : STEEL_DARK, alpha);
        }
        crystalBurst(v, rf * (radius + 0.10f), y, rl * (radius + 0.10f), 0.16f,
                angle, alpha, blade == 0 ? PRISM_CYAN : blade == 1 ? PRISM_ROSE : PRISM_GOLD);
    }

    private static void crystalBurst(Voxels v, float f, float y, float l,
                                     float radius, float spin, int alpha, int rgb) {
        for (int axis = 0; axis < 6; axis++) {
            float angle = axis * TAU / 6.0f + spin;
            v.link(f, y, l, f + Mth.cos(angle) * radius, y + (axis % 2 == 0 ? radius : -radius) * 0.45f,
                    l + Mth.sin(angle) * radius, 0.022f, rgb, GLINT, alpha);
        }
        v.rot(f, y, l, radius * 0.28f, radius * 0.55f, radius * 0.28f,
                spin, 0.0f, 0.7853982f, GLINT, alpha);
    }

    /**
     * ANNIHILATING ARC — deux croissants-guillotines mécaniques se déplient en fronts
     * horizontaux. Chaque maillon possède corps, arête, dent et rivet. Repoussement : 42/140.
     */
    private static void renderAnnihilatingArc(Voxels v, float t, int alpha, int salt) {
        float birth = smooth(t / 0.055f);
        float exit = 1.0f - smooth((t - 0.85f) / 0.15f);
        int a = Math.round(alpha * birth * exit);
        float charge = smooth((t - 0.02f) / 0.24f);
        float release = smooth((t - 0.30f) / 0.24f);
        float counter = smooth((t - 0.52f) / 0.25f);
        float mechanismAlpha = 1.0f - release * 0.74f;

        arcMechanism(v, charge, release, Math.round(a * mechanismAlpha));

        // Les mâchoires commencent verticales, puis s'aplatissent en devenant les fronts.
        for (int jaw = 0; jaw < 2; jaw++) {
            float yaw = jaw * (float) Math.PI + charge * 1.35f;
            for (int part = 0; part < 58; part++) {
                float u = part / 57.0f;
                float angle = yaw - 2.32f + u * 4.64f;
                float radius = 2.75f - charge * 0.45f + release * 0.55f;
                float vertical = (1.0f - release) * 0.88f;
                float f = Mth.cos(angle) * radius;
                float l = Mth.sin(angle) * radius * (jaw == 0 ? 1.0f : -1.0f);
                float y = 0.40f + jaw * 0.46f + Mth.sin(angle) * radius * vertical;
                float fade = 1.0f - release * 0.78f;
                // Dos épais, corps riveté et arête intérieure claire.
                v.rot(f * 1.035f, y + 0.045f, l * 1.035f, 0.065f, 0.075f, 0.145f,
                        angle, 0.0f, 0.0f, BLACK_STEEL, Math.round(a * fade));
                v.rot(f, y, l, 0.055f, 0.050f, 0.125f,
                        angle, 0.0f, 0.0f, part % 7 == 0 ? SILVER : STEEL, Math.round(a * fade));
                v.rot(f * 0.955f, y - 0.035f, l * 0.955f, 0.040f, 0.022f, 0.105f,
                        angle, 0.0f, 0.0f, EDGE, Math.round(a * fade));
                if (part % 3 == 0) {
                    // Trois voxels en escalier forment une dent réelle, pas une ligne pointillée.
                    for (int tooth = 0; tooth < 3; tooth++) {
                        float tr = 1.07f + tooth * 0.035f;
                        v.rot(f * tr, y - tooth * 0.025f, l * tr,
                                0.035f, 0.025f, 0.080f - tooth * 0.018f,
                                angle, 0.0f, 0.0f, tooth == 2 ? GLINT : EDGE, Math.round(a * fade));
                    }
                }
                if (part % 6 == 0) {
                    v.box(f, y + 0.105f, l, 0.045f, 0.025f, 0.045f,
                            part % 12 == 0 ? BRONZE : SILVER, Math.round(a * fade));
                }
            }
        }

        // Front principal : 96 lames tangentes articulées, dents extérieures et contre-arête.
        if (release > 0.0f && release < 1.0f) {
            float radius = 0.72f + release * 4.85f;
            int ringAlpha = Math.round(a * (1.0f - release * 0.68f));
            for (int part = 0; part < 96; part++) {
                float angle = part * TAU / 96.0f + release * 1.15f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                v.rot(f, 0.20f, l, 0.055f, 0.048f, radius * TAU / 192.0f * 1.10f,
                        angle, 0.0f, 0.0f, part % 8 == 0 ? SILVER : STEEL, ringAlpha);
                v.rot(f * 1.025f, 0.16f, l * 1.025f, 0.035f, 0.020f, radius * TAU / 192.0f,
                        angle, 0.0f, 0.0f, EDGE, ringAlpha);
                v.rot(f * 0.965f, 0.24f, l * 0.965f, 0.026f, 0.028f, radius * TAU / 220.0f,
                        angle, 0.0f, 0.0f, STEEL_DARK, ringAlpha);
                if (part % 3 == 0) {
                    v.link(f * 1.02f, 0.18f, l * 1.02f, f * 1.11f, 0.10f, l * 1.11f,
                            0.030f, EDGE, GLINT, ringAlpha);
                    v.box(f, 0.29f, l, 0.040f, 0.022f, 0.040f,
                            part % 6 == 0 ? BRONZE : SILVER, ringAlpha);
                }
            }
        }

        // Contre-arc oblique : chevrons doubles, rotation inverse et hauteur différente.
        if (counter > 0.0f && counter < 1.0f) {
            float radius = 0.95f + counter * 4.30f;
            int ringAlpha = Math.round(a * (1.0f - counter * 0.74f));
            for (int part = 0; part < 80; part++) {
                float angle = -part * TAU / 80.0f - counter * 1.55f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                float y = 0.72f + Mth.sin(angle * 2.0f) * 0.10f;
                v.rot(f, y, l, 0.040f, 0.032f, 0.115f,
                        angle + 0.42f, 0.0f, 0.40f, part % 5 == 0 ? GLINT : SILVER, ringAlpha);
                v.rot(f * 0.975f, y + 0.07f, l * 0.975f, 0.026f, 0.022f, 0.095f,
                        angle - 0.42f, 0.0f, -0.40f, EDGE, ringAlpha);
            }
        }

        // Éclats lourds tangentiels, nettement différents des limailles fines de Razor.
        for (int i = 0; i < 76; i++) {
            float h = hash(salt, 900 + i * 43);
            float age = clamp((t - 0.30f) / 0.56f);
            if (age <= 0.0f) continue;
            float angle = i * 2.3999632f + age * 0.55f;
            float r = age * (2.0f + h * 3.65f);
            v.rot(Mth.cos(angle) * r, 0.20f + age * (0.25f + h * 1.55f), Mth.sin(angle) * r,
                    0.030f + h * 0.025f, 0.020f, 0.075f + h * 0.06f,
                    angle + age * 4.0f, age * 5.0f, h * 3.0f,
                    i % 6 == 0 ? GLINT : STEEL, Math.round(a * (1.0f - age) * 0.76f));
        }
    }

    private static void arcMechanism(Voxels v, float charge, float release, int alpha) {
        if (alpha <= 0) return;
        float spin = charge * 2.4f + release * 1.8f;
        // Trois couronnes armillaires avec axes et rayons rivetés.
        for (int ring = 0; ring < 3; ring++) {
            float radius = 0.48f + ring * 0.23f;
            float y = 0.42f + ring * 0.20f;
            for (int part = 0; part < 24; part++) {
                float angle = part * TAU / 24.0f + spin * (ring % 2 == 0 ? 1.0f : -1.0f);
                float next = (part + 1) * TAU / 24.0f + spin * (ring % 2 == 0 ? 1.0f : -1.0f);
                v.link(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                        Mth.cos(next) * radius, y, Mth.sin(next) * radius,
                        0.026f, ring == 1 ? BRONZE : STEEL, EDGE, alpha);
                if (part % 6 == 0) {
                    v.link(0.0f, 0.56f, 0.0f, Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                            0.022f, STEEL_DARK, SILVER, Math.round(alpha * 0.82f));
                }
            }
        }
        for (int layer = 0; layer < 7; layer++) {
            v.rot(0.0f, 0.12f + layer * 0.15f, 0.0f,
                    0.18f - Math.abs(layer - 3) * 0.018f, 0.065f, 0.18f - Math.abs(layer - 3) * 0.018f,
                    spin + layer * 0.22f, 0.0f, 0.0f,
                    layer == 3 ? GLINT : BLACK_STEEL, alpha);
        }
        for (int rune = 0; rune < 12; rune++) {
            float angle = rune * TAU / 12.0f - spin * 0.55f;
            v.rot(Mth.cos(angle) * 1.12f, 0.34f, Mth.sin(angle) * 1.12f,
                    0.035f, 0.020f, 0.095f, angle, 0.0f, rune % 2 == 0 ? 0.55f : -0.55f,
                    rune % 3 == 0 ? BRONZE : EDGE, Math.round(alpha * 0.85f));
        }
    }

    /** Coordonnées locales orientées ; toutes les primitives ajoutent exactement une fois cy. */
    private record Voxels(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                          float cx, float cy, float cz, float dx, float dz) {
        void cube(float f, float y, float l, float half, int rgb, int alpha) {
            box(f, y, l, half, half, half, rgb, alpha);
        }

        void box(float f, float y, float l, float hf, float hy, float hl, int rgb, int alpha) {
            if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
            float x = cx + dx * f - dz * l;
            float z = cz + dz * f + dx * l;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + y, z, hf, hy, hl, rgb, alpha);
        }

        void rot(float f, float y, float l, float hf, float hy, float hl,
                 float yaw, float pitch, float roll, int rgb, int alpha) {
            if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
            float x = cx + dx * f - dz * l;
            float z = cz + dz * f + dx * l;
            WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy + y, z,
                    hf, hy, hl, yaw, pitch, roll, rgb, alpha);
        }

        void link(float f0, float y0, float l0, float f1, float y1, float l1,
                  float half, int rgb, int tip, int alpha) {
            float df = f1 - f0, dy = y1 - y0, dl = l1 - l0;
            float length = Mth.sqrt(df * df + dy * dy + dl * dl);
            if (length < 0.001f || alpha <= 0) return;
            float sx = dx * df - dz * dl, sz = dz * df + dx * dl;
            float yaw = (float) Math.atan2(sx, sz);
            float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
            rot((f0 + f1) * 0.5f, (y0 + y1) * 0.5f, (l0 + l1) * 0.5f,
                    half, length * 0.56f, half, yaw, pitch, 0.0f, color(rgb, tip, 0.32f), alpha);
        }
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
