package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Les trois grandes signatures Lumière. Les volumes opaques/translucides et les contours
 * additifs sont deux passes séparées : la lumière reste visible sans transformer les cubes en
 * panneaux plats. Toutes les positions sont relatives au centre de la cible.
 */
public final class LightSignatureVfx {
    public static final int SERAPH = 0;
    public static final int ASTRAL = 1;
    public static final int CHRONO = 2;

    private static final int GOLD_DEEP = 0x70511D;
    private static final int GOLD = 0xD7A83E;
    private static final int HONEY = 0xF5D77B;
    private static final int IVORY = 0xFFF0B5;
    private static final int WHITE = 0xFFFDF1;
    private static final int CYAN = 0x83E6F2;
    private static final int ICE = 0xC6F7FF;
    private static final int BLUE = 0x568CCB;
    private static final int VIOLET = 0x9D83D5;
    private static final int LILAC = 0xD9C7FF;
    private static final int TIME_DARK = 0x344465;
    private static final float TAU = (float) (Math.PI * 2.0);

    private LightSignatureVfx() { }

    /** glow = passe additive ; body = passe alpha réelle, jamais le même buffer. */
    public static void render(VertexConsumer glow, VertexConsumer body, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t, float dx, float dz,
                              int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 0) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        Voxels v = new Voxels(glow, body, mat, tmp, cx, cy, cz, dx, dz, t);
        if (variant == SERAPH) renderSeraph(v, t, alpha, salt);
        else if (variant == ASTRAL) renderAstral(v, t, alpha, salt);
        else renderChrono(v, t, alpha, salt);
    }

    /** SERAPH : six ailes articulées, chute des lances, puis pilier de jugement au tick 40/100. */
    private static void renderSeraph(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.055f);
        float exit = 1.0f - smooth((t - 0.76f) / 0.22f);
        int a = Math.round(alpha * enter * exit);
        float charge = easeInOut(t / 0.29f);
        float fall = easeIn((t - 0.12f) / 0.28f);
        float impact = 1.0f - smooth(Math.abs(t - 0.40f) / 0.12f);
        float pillar = smooth((t - 0.385f) / 0.035f) * (1.0f - smooth((t - 0.62f) / 0.17f));
        float wingHeight = 4.45f - fall * 3.70f;

        // Sceau au sol : les deux couronnes tournent en sens inverse, puis s'immobilisent au hit.
        for (int ring = 0; ring < 2; ring++) {
            int parts = 80;
            float radius = (1.10f + ring * 0.63f) * (0.82f + charge * 0.18f);
            float spin = (ring == 0 ? 1.0f : -1.0f) * charge * 1.45f;
            for (int i = 0; i < parts; i++) {
                float angle = i * TAU / parts + spin;
                float next = (i + 1) * TAU / parts + spin;
                int rgb = (i % 10 == 0) ? WHITE : (ring == 0 ? HONEY : GOLD);
                v.glowLink(Mth.cos(angle) * radius, -0.88f, Mth.sin(angle) * radius,
                        Mth.cos(next) * radius, -0.88f, Mth.sin(next) * radius,
                        ring == 0 ? 0.022f : 0.016f, rgb, IVORY,
                        Math.round(a * (0.46f + impact * 0.54f)));
            }
        }
        for (int rune = 0; rune < 8; rune++) {
            float angle = rune * TAU / 8.0f + charge * 0.35f;
            float f = Mth.cos(angle) * 1.40f, l = Mth.sin(angle) * 1.40f;
            // Croix voxel et petit sceau carré, lisibles du dessus comme de trois quarts.
            v.glowLink(f - 0.13f, -0.86f, l, f + 0.13f, -0.86f, l,
                    0.018f, HONEY, WHITE, a);
            v.glowLink(f, -0.86f, l - 0.13f, f, -0.86f, l + 0.13f,
                    0.018f, HONEY, WHITE, a);
            v.outlineSquare(f, -0.85f, l, 0.20f, angle, GOLD, Math.round(a * 0.70f));
        }

        // Les six ailes sont de vraies rangées de plumes. Elles s'ouvrent, accompagnent la chute,
        // puis se replient autour du faisceau au lieu de rester comme un décor statique.
        for (int pair = 0; pair < 3; pair++) {
            float baseY = wingHeight + (pair - 1) * 0.40f;
            float open = smooth((charge - pair * 0.10f) / 0.72f);
            for (int side = -1; side <= 1; side += 2) {
                for (int feather = 0; feather < 14; feather++) {
                    float u = feather / 13.0f;
                    float sweep = (0.24f + u * (1.30f + pair * 0.26f)) * open;
                    float rootF = side * 0.16f;
                    float rootY = baseY + (pair - 1) * 0.12f;
                    float tipF = side * (0.20f + sweep);
                    float tipY = baseY + (0.60f - u * 1.25f) * open + pair * 0.12f;
                    float tipL = (pair - 1) * 0.30f + side * 0.06f;
                    float fold = 1.0f - smooth((t - 0.37f) / 0.12f) * 0.55f;
                    tipF *= fold;
                    for (int joint = 0; joint < 5; joint++) {
                        float q0 = joint / 5.0f, q1 = (joint + 1) / 5.0f;
                        float bend0 = Mth.sin(q0 * (float) Math.PI) * 0.20f;
                        float bend1 = Mth.sin(q1 * (float) Math.PI) * 0.20f;
                        float f0 = Mth.lerp(q0, rootF, tipF);
                        float f1 = Mth.lerp(q1, rootF, tipF);
                        float y0 = Mth.lerp(q0, rootY, tipY) + bend0;
                        float y1 = Mth.lerp(q1, rootY, tipY) + bend1;
                        int rgb = joint == 4 ? WHITE : (feather % 4 == 0 ? GOLD : IVORY);
                        v.lightLink(f0, y0, tipL, f1, y1, tipL + (pair - 1) * 0.08f,
                                0.035f + (1.0f - u) * 0.015f, rgb, HONEY,
                                Math.round(a * (0.48f + 0.52f * open)));
                    }
                    // Quill doré et extrémité cubique qui garde la silhouette voxel.
                    v.glowLink(rootF, rootY, tipL, tipF, tipY, tipL,
                            0.013f, GOLD, WHITE, Math.round(a * 0.72f));
                    v.glow(tipF, tipY, tipL, 0.037f, WHITE, Math.round(a * 0.85f));
                }
            }
        }
        seraphHeart(v, 0.0f, wingHeight + 0.10f, 0.0f, 0.38f + impact * 0.13f,
                charge * 2.2f, a, t);

        // Douze lances descendent réellement. Leur tête atteint le sceau à t=.40.
        for (int spear = 0; spear < 12; spear++) {
            float angle = spear * TAU / 12.0f + 0.13f;
            float stagger = (spear % 3) * 0.012f;
            float drop = easeIn((t - 0.10f - stagger) / (0.30f - stagger));
            float radius = 0.60f + (spear % 2) * 0.62f;
            float f = Mth.cos(angle) * radius * (1.0f - drop * 0.76f);
            float l = Mth.sin(angle) * radius * (1.0f - drop * 0.76f);
            float head = Mth.lerp(5.75f + (spear % 4) * 0.22f, -0.72f, drop);
            float trail = 0.75f + (1.0f - drop) * 0.80f;
            v.lightLink(f, head + trail, l, f, head, l, 0.038f,
                    spear % 3 == 0 ? WHITE : HONEY, IVORY,
                    Math.round(a * (drop > 0.98f ? 1.0f - smooth((t - 0.43f) / 0.13f) : 0.85f)));
            crystalTip(v, f, head, l, 0.14f, 0.28f, GOLD, WHITE,
                    Math.round(a * (1.0f - smooth((t - 0.46f) / 0.14f))));
        }

        // Le pilier est un volume transparent octogonal, pas un simple trait. Trois âmes additives
        // de largeurs différentes montent dans le volume après l'impact.
        if (pillar > 0.0f) {
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f;
                float f = Mth.cos(angle) * 0.46f, l = Mth.sin(angle) * 0.46f;
                v.bodyLink(f, -0.76f, l, f, 4.76f, l, 0.055f, IVORY,
                        Math.round(a * pillar * 0.32f));
                v.glowLink(f, -0.76f, l, f, 4.76f, l, 0.014f,
                        side % 2 == 0 ? HONEY : IVORY, WHITE, Math.round(a * pillar * 0.50f));
            }
            for (int strand = 0; strand < 3; strand++) {
                float angle = strand * TAU / 3.0f + t * 2.0f;
                float f = Mth.cos(angle) * 0.10f, l = Mth.sin(angle) * 0.10f;
                v.lightLink(f, -0.76f, l, f, 4.76f, l, 0.025f,
                        strand == 1 ? WHITE : HONEY, IVORY, Math.round(a * pillar * 0.74f));
            }
            for (int layer = 0; layer < 34; layer++) {
                float y = -0.78f + layer * 0.165f;
                for (int side = 0; side < 8; side++) {
                    float angle = side * TAU / 8.0f + layer * 0.018f;
                    float next = (side + 1) * TAU / 8.0f + layer * 0.018f;
                    float radius = 0.45f + 0.08f * Mth.sin(layer * 0.38f + t * 18.0f);
                    v.bodyLink(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                            Mth.cos(next) * radius, y, Mth.sin(next) * radius,
                            0.095f, side % 2 == 0 ? IVORY : HONEY,
                            Math.round(a * pillar * 0.20f));
                }
                for (int strand = 0; strand < 3; strand++) {
                    float angle = layer * 0.24f + strand * TAU / 3.0f - t * 9.0f;
                    float radius = 0.09f + strand * 0.10f;
                    v.glow(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                            0.035f + strand * 0.008f, strand == 1 ? WHITE : HONEY,
                            Math.round(a * pillar * (0.68f + 0.20f * Mth.sin(layer + strand))));
                }
            }
            for (int ray = 0; ray < 16; ray++) {
                float angle = ray * TAU / 16.0f;
                float reach = 0.55f + impact * 1.55f;
                v.glowLink(Mth.cos(angle) * 0.35f, -0.77f, Mth.sin(angle) * 0.35f,
                        Mth.cos(angle) * reach, -0.77f, Mth.sin(angle) * reach,
                        0.024f, GOLD, WHITE, Math.round(a * pillar * impact));
            }
        }
        // Éclats d'après-jugement : une gerbe basse qui ne masque pas la chute initiale.
        for (int i = 0; i < 44; i++) {
            float age = clamp((t - 0.40f - (i % 5) * 0.004f) / 0.36f);
            if (age <= 0.0f || age >= 1.0f) continue;
            float angle = i * 2.3999632f;
            float h = hash(salt, i * 37);
            float radius = age * (0.65f + h * 1.45f);
            float y = -0.72f + age * (0.40f + h * 1.70f) - age * age * 0.85f;
            v.rot(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                    0.022f + h * 0.018f, 0.050f, 0.018f, angle, age * 3.0f, h * 2.0f,
                    i % 5 == 0 ? WHITE : GOLD, Math.round(a * (1.0f - age)));
        }
    }

    /** ASTRAL : constellation qui forge une lance, voyage continu, perforation à 24/80 ticks. */
    private static void renderAstral(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.045f);
        float exit = 1.0f - smooth((t - 0.76f) / 0.22f);
        int a = Math.round(alpha * enter * exit);
        float forge = easeInOut(t / 0.17f);
        float travel = easeInOut((t - 0.07f) / 0.23f);
        float after = easeOut((t - 0.30f) / 0.23f);
        float head = -4.35f + travel * 4.60f + after * 4.35f;
        float impact = 1.0f - smooth(Math.abs(t - 0.30f) / 0.095f);

        // Sept étoiles nommées par leur position : la constellation est reliée puis aspirée dans
        // la queue de la lance. Elle ne suit pas la projectile comme une traînée générique.
        float[][] stars = {
                {-4.75f, 1.45f, -0.40f}, {-4.10f, 0.65f, 0.30f}, {-3.45f, 1.65f, 0.55f},
                {-3.05f, 0.25f, -0.45f}, {-2.35f, 1.00f, 0.15f}, {-1.80f, 1.90f, -0.35f},
                {-1.30f, 0.35f, 0.40f}
        };
        float constellationAlpha = 1.0f - smooth((t - 0.12f) / 0.22f);
        for (int i = 0; i < stars.length; i++) {
            float[] s = stars[i];
            float pull = smooth((t - 0.10f - i * 0.006f) / 0.18f);
            float f = Mth.lerp(pull, s[0], head - 2.40f);
            float y = Mth.lerp(pull, s[1], 0.0f);
            float l = Mth.lerp(pull, s[2], 0.0f);
            star(v, f, y, l, 0.13f + (i % 3) * 0.055f,
                    i % 2 == 0 ? ICE : LILAC, Math.round(a * constellationAlpha), i);
            if (i > 0) {
                float[] p = stars[i - 1];
                v.glowLink(p[0], p[1], p[2], s[0], s[1], s[2], 0.012f,
                        BLUE, ICE, Math.round(a * constellationAlpha * 0.55f));
            }
        }

        // Réticule de visée à la cible : ses quatre coins se ferment avant l'arrivée de la pointe.
        float lock = smooth(t / 0.20f) * (1.0f - smooth((t - 0.31f) / 0.13f));
        for (int corner = 0; corner < 4; corner++) {
            float sx = (corner & 1) == 0 ? -1.0f : 1.0f;
            float sy = (corner & 2) == 0 ? -1.0f : 1.0f;
            float radius = 0.90f - lock * 0.35f;
            float f = sx * radius;
            v.glowLink(f, sy * radius, -0.12f, f - sx * 0.28f, sy * radius, -0.12f,
                    0.019f, CYAN, WHITE, Math.round(a * lock));
            v.glowLink(f, sy * radius, -0.12f, f, sy * radius - sy * 0.28f, -0.12f,
                    0.019f, CYAN, WHITE, Math.round(a * lock));
        }

        // La lance se compose d'un fût octogonal creux, de facettes translucides, d'une pointe
        // cristalline et de trois orbites. Chaque pièce partage exactement la même position head.
        float lanceAlpha = 1.0f - smooth((t - 0.58f) / 0.25f);
        for (int segment = 0; segment < 34; segment++) {
            float u = segment / 33.0f;
            float f = head - 3.15f + u * 2.65f;
            float radius = 0.09f + 0.08f * Mth.sin(u * (float) Math.PI);
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f + u * 1.4f;
                float next = (side + 1) * TAU / 8.0f + u * 1.4f;
                float y0 = Mth.cos(angle) * radius, l0 = Mth.sin(angle) * radius;
                float y1 = Mth.cos(next) * radius, l1 = Mth.sin(next) * radius;
                v.bodyLink(f, y0, l0, f, y1, l1, 0.052f,
                        side % 2 == 0 ? CYAN : VIOLET, Math.round(a * lanceAlpha * 0.28f));
                if (segment % 2 == 0) {
                    v.glowLink(f - 0.075f, y0, l0, f + 0.075f, y0, l0,
                            0.018f, side % 2 == 0 ? ICE : LILAC, WHITE,
                            Math.round(a * lanceAlpha * 0.76f));
                }
            }
            v.glow(f, 0.0f, 0.0f, 0.032f, segment % 5 == 0 ? WHITE : CYAN,
                    Math.round(a * lanceAlpha * 0.90f));
        }
        // Pointe à gradins : quatorze sections, huit faces et un arête lumineuse variable.
        for (int section = 0; section < 14; section++) {
            float u = section / 13.0f;
            float f = head - 0.50f + u * 0.76f;
            float radius = 0.29f * (1.0f - u) + 0.018f;
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f;
                float y = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                v.rot(f, y, l, 0.065f, 0.035f, 0.035f,
                        0.0f, 0.0f, angle, side % 2 == 0 ? ICE : LILAC,
                        Math.round(a * lanceAlpha * 0.74f));
                if ((side + section) % 3 == 0) {
                    v.glowLink(f - 0.055f, y, l, f + 0.055f, y, l,
                            0.012f, WHITE, CYAN, Math.round(a * lanceAlpha));
                }
            }
        }
        for (int strand = 0; strand < 3; strand++) {
            for (int i = 0; i < 42; i++) {
                float u = i / 41.0f;
                float angle = u * TAU * 2.4f + strand * TAU / 3.0f - t * 12.0f;
                float radius = 0.26f + 0.08f * Mth.sin(u * TAU);
                float f = head - 3.15f + u * 3.15f;
                v.glow(f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        0.020f + (i % 7 == 0 ? 0.018f : 0.0f),
                        strand == 0 ? CYAN : strand == 1 ? LILAC : WHITE,
                        Math.round(a * lanceAlpha * (0.38f + 0.50f * u)));
            }
        }
        // Manche arrière et trois ailettes stabilisatrices : l'objet reste identifiable en orbite.
        for (int fin = 0; fin < 3; fin++) {
            float angle = fin * TAU / 3.0f + t * 2.0f;
            for (int step = 0; step < 9; step++) {
                float u = step / 8.0f;
                float f = head - 3.30f + u * 0.58f;
                float radius = 0.34f * (1.0f - u) + 0.08f;
                v.glowLink(f, Mth.cos(angle) * 0.10f, Mth.sin(angle) * 0.10f,
                        f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        0.021f, VIOLET, ICE, Math.round(a * lanceAlpha));
            }
        }
        astralShell(v, head - 0.43f, 0.0f, 0.0f, 0.27f,
                Math.round(a * lanceAlpha * 0.75f), t * 24.0f);

        // Au contact, une diffraction en étoile remplace l'explosion sphérique habituelle.
        if (impact > 0.0f) {
            // Corridor de contact visible : 8,70 blocs centrés sur la cible, exactement celui
            // testé par segmentHitsEntity au tick 24 (avant application de sizeScale).
            for (int node = 0; node <= 48; node++) {
                float f = -4.35f + node * 8.70f / 48.0f;
                v.glow(f, 0.0f, 0.0f, node % 6 == 0 ? 0.030f : 0.018f,
                        node % 6 == 0 ? WHITE : CYAN, Math.round(a * impact * 0.62f));
            }
            for (int ray = 0; ray < 10; ray++) {
                float angle = ray * TAU / 10.0f;
                float reach = (ray % 2 == 0 ? 1.85f : 1.10f) * impact;
                v.glowLink(0.0f, 0.0f, 0.0f, 0.12f,
                        Mth.cos(angle) * reach, Mth.sin(angle) * reach,
                        ray % 2 == 0 ? 0.026f : 0.015f,
                        ray % 3 == 0 ? LILAC : ICE, WHITE, Math.round(a * impact));
            }
            for (int plane = 0; plane < 3; plane++) {
                float angle = plane * TAU / 3.0f + t * 2.0f;
                astralShell(v, 0.12f, Mth.cos(angle) * 0.42f, Mth.sin(angle) * 0.42f,
                        0.20f + impact * 0.12f, Math.round(a * impact * 0.70f), t * 30.0f + plane);
            }
        }
        for (int shard = 0; shard < 38; shard++) {
            float age = clamp((t - 0.30f - (shard % 4) * 0.005f) / 0.37f);
            if (age <= 0.0f || age >= 1.0f) continue;
            float h = hash(salt, 700 + shard * 43);
            float angle = shard * 2.3999632f;
            float radius = age * (0.35f + h * 2.0f);
            v.rot(age * 2.4f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                    0.055f, 0.020f + h * 0.022f, 0.018f,
                    angle, age * 4.0f, h * 3.0f, shard % 3 == 0 ? LILAC : ICE,
                    Math.round(a * (1.0f - age) * 0.86f));
        }
    }

    /** CHRONO : armillaire à trois plans, détente à 55/100, puis rembobinage des éclats. */
    private static void renderChrono(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.06f);
        float exit = 1.0f - smooth((t - 0.79f) / 0.19f);
        int a = Math.round(alpha * enter * exit);
        float assemble = easeOut(t / 0.20f);
        float close = easeInOut((t - 0.32f) / 0.23f);
        float release = smooth((t - 0.55f) / 0.25f);
        float strike = 1.0f - smooth(Math.abs(t - 0.55f) / 0.075f);
        float radius = (2.30f - close * 1.30f + release * 1.00f) * assemble;
        float handSpin = chronometerAngle(t);

        // Trois plans orthogonaux de l'armillaire. Les maillons sont segmentés pour laisser voir
        // la cible et chaque douzième maillon porte une dent d'engrenage.
        for (int plane = 0; plane < 3; plane++) {
            float spin = (plane - 1) * handSpin * 0.22f;
            for (int ring = 0; ring < 2; ring++) {
                float r = radius * (ring == 0 ? 1.0f : 0.78f);
                int parts = 88;
                for (int i = 0; i < parts; i++) {
                    float angle = i * TAU / parts + spin;
                    float next = (i + 1) * TAU / parts + spin;
                    float[] p0 = clockPlane(plane, r, angle);
                    float[] p1 = clockPlane(plane, r, next);
                    int rgb = plane == 0 ? HONEY : plane == 1 ? CYAN : LILAC;
                    v.lightLink(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                            ring == 0 ? 0.023f : 0.015f, rgb, WHITE,
                            Math.round(a * (ring == 0 ? 0.76f : 0.48f)));
                    if (i % 11 == 0 && ring == 0) {
                        float[] tooth = clockPlane(plane, r + 0.13f, angle);
                        v.glowLink(p0[0], p0[1], p0[2], tooth[0], tooth[1], tooth[2],
                                0.031f, rgb, IVORY, Math.round(a * 0.88f));
                    }
                }
            }
            // Douze index et quatre grands repères, comme une horloge lisible plutôt qu'un tore.
            for (int hour = 0; hour < 12; hour++) {
                float angle = hour * TAU / 12.0f;
                float[] inner = clockPlane(plane, radius * 0.84f, angle);
                float[] outer = clockPlane(plane, radius * (hour % 3 == 0 ? 1.08f : 0.96f), angle);
                v.glowLink(inner[0], inner[1], inner[2], outer[0], outer[1], outer[2],
                        hour % 3 == 0 ? 0.038f : 0.021f,
                        hour % 3 == 0 ? WHITE : (plane == 1 ? CYAN : HONEY), IVORY, a);
                if (hour % 3 == 0) {
                    astralShell(v, outer[0], outer[1], outer[2], 0.11f,
                            Math.round(a * 0.72f), t * 20.0f + hour + plane * 3.0f);
                }
            }
        }

        // Aiguilles à détente : leur angle avance par crans, s'arrête à la fermeture puis recule.
        for (int hand = 0; hand < 3; hand++) {
            float angle = handSpin * (hand == 0 ? 1.0f : hand == 1 ? -0.42f : 0.17f) + hand * 1.8f;
            float length = radius * (hand == 0 ? 0.88f : hand == 1 ? 0.64f : 0.46f);
            float[] tip = clockPlane(hand, length, angle);
            int rgb = hand == 0 ? WHITE : hand == 1 ? CYAN : LILAC;
            v.glowLink(0.0f, 0.0f, 0.0f, tip[0], tip[1], tip[2],
                    hand == 0 ? 0.034f : 0.024f, rgb, IVORY, a);
            // Contrepoids et pointe fléchée de chaque aiguille.
            float[] back = clockPlane(hand, -0.30f * assemble, angle);
            v.glowLink(0.0f, 0.0f, 0.0f, back[0], back[1], back[2], 0.030f, GOLD, rgb, a);
            float[] left = clockPlane(hand, length - 0.19f, angle + 0.12f);
            float[] right = clockPlane(hand, length - 0.19f, angle - 0.12f);
            v.glowLink(left[0], left[1], left[2], tip[0], tip[1], tip[2], 0.022f, rgb, WHITE, a);
            v.glowLink(right[0], right[1], right[2], tip[0], tip[1], tip[2], 0.022f, rgb, WHITE, a);
            // Après-images discrètes de l'aiguille principale, jamais confondues avec une hitbox.
            if (hand == 0 && t < 0.55f) {
                for (int ghost = 1; ghost <= 4; ghost++) {
                    float oldAngle = angle - ghost * 0.12f;
                    float[] oldTip = clockPlane(hand, length, oldAngle);
                    v.glowLink(0.0f, 0.0f, 0.0f, oldTip[0], oldTip[1], oldTip[2],
                            0.012f, BLUE, CYAN, Math.round(a * (0.24f - ghost * 0.04f)));
                }
            }
        }

        // Cage temporelle centrale : son volume est translucide, les douze arêtes défilent.
        astralShell(v, 0.0f, 0.0f, 0.0f, 0.48f + close * 0.20f + strike * 0.25f,
                Math.round(a * (0.68f + strike * 0.32f)), t * 32.0f);
        for (int layer = -3; layer <= 3; layer++) {
            float r = (4 - Math.abs(layer)) * 0.075f;
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + handSpin * 0.20f + layer * 0.20f;
                v.glow(Mth.cos(angle) * r, layer * 0.12f, Mth.sin(angle) * r,
                        0.045f, layer == 0 ? WHITE : (side % 2 == 0 ? HONEY : CYAN), a);
            }
        }

        // Éclats de mémoire : trajectoire aller mémorisée par leur index, puis exacte marche arrière.
        for (int shard = 0; shard < 48; shard++) {
            float h = hash(salt, 1200 + shard * 47);
            float local = t < 0.55f ? t / 0.55f : 1.0f - (t - 0.55f) / 0.45f;
            local = clamp(local);
            float angle = shard * 2.3999632f + handSpin * 0.18f;
            float r = (0.65f + h * 2.0f) * (0.45f + local * 0.55f);
            float y = (h - 0.5f) * 2.2f + Mth.sin(angle * 2.0f) * 0.30f;
            v.rot(Mth.cos(angle) * r, y, Mth.sin(angle) * r,
                    0.035f + h * 0.020f, 0.020f, 0.070f,
                    angle, local * 4.0f, h * 3.0f,
                    shard % 4 == 0 ? LILAC : shard % 3 == 0 ? CYAN : HONEY,
                    Math.round(a * (0.35f + 0.65f * local)));
        }
        if (strike > 0.0f) {
            for (int ray = 0; ray < 12; ray++) {
                float angle = ray * TAU / 12.0f;
                float reach = 0.55f + strike * 0.95f;
                v.glowLink(Mth.cos(angle) * 0.20f, 0.0f, Mth.sin(angle) * 0.20f,
                        Mth.cos(angle) * reach, 0.0f, Mth.sin(angle) * reach,
                        0.021f, ray % 3 == 0 ? LILAC : IVORY, WHITE,
                        Math.round(a * strike));
            }
        }
    }

    /** Angle en crans : accélération, arrêt franc à .55, rembobinage visible ensuite. */
    private static float chronometerAngle(float t) {
        if (t < 0.20f) return smooth(t / 0.20f) * 2.0f;
        if (t < 0.55f) {
            float u = (t - 0.20f) / 0.35f;
            float continuous = 2.0f + u * 8.0f + u * u * 4.0f;
            return (float) Math.floor(continuous * 8.0f) / 8.0f;
        }
        float rewind = smooth((t - 0.55f) / 0.38f);
        return 14.0f - rewind * 10.0f;
    }

    private static float[] clockPlane(int plane, float radius, float angle) {
        float a = Mth.cos(angle) * radius, b = Mth.sin(angle) * radius;
        if (plane == 0) return new float[]{a, 0.0f, b};
        if (plane == 1) return new float[]{a, b, 0.0f};
        return new float[]{0.0f, a, b};
    }

    private static void seraphHeart(Voxels v, float f, float y, float l, float radius,
                                    float spin, int alpha, float t) {
        for (int layer = -4; layer <= 4; layer++) {
            float r = radius * (1.0f - Math.abs(layer) / 5.0f);
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + spin + layer * 0.20f;
                v.glowLink(f + Mth.cos(angle) * r, y + layer * radius * 0.30f, l + Mth.sin(angle) * r,
                        f + Mth.cos(angle + TAU / 4.0f) * r, y + layer * radius * 0.30f,
                        l + Mth.sin(angle + TAU / 4.0f) * r,
                        0.030f, side % 2 == 0 ? GOLD : IVORY, WHITE, alpha);
            }
        }
        astralShell(v, f, y, l, radius * 0.62f, Math.round(alpha * 0.76f), t * 26.0f);
    }

    private static void crystalTip(Voxels v, float f, float y, float l, float radius,
                                   float length, int rgb, int tip, int alpha) {
        for (int section = 0; section < 5; section++) {
            float u = section / 4.0f;
            float r = radius * (1.0f - u * 0.88f);
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + 0.7853982f;
                v.glow(f + Mth.cos(angle) * r, y - u * length, l + Mth.sin(angle) * r,
                        0.028f, section == 4 ? tip : rgb, alpha);
            }
        }
    }

    private static void star(Voxels v, float f, float y, float l, float radius,
                             int rgb, int alpha, int phase) {
        for (int ray = 0; ray < 8; ray++) {
            float angle = ray * TAU / 8.0f;
            float length = radius * (ray % 2 == 0 ? 1.0f : 0.48f);
            v.glowLink(f, y, l, f + Mth.cos(angle) * length, y + Mth.sin(angle) * length, l,
                    ray % 2 == 0 ? 0.018f : 0.011f, rgb, WHITE, alpha);
        }
        astralShell(v, f, y, l, radius * 0.36f, Math.round(alpha * 0.70f), phase);
    }

    /** Cube de verre énergétique : 12 arêtes, chacune divisée en deux segments animés. */
    private static void astralShell(Voxels v, float f, float y, float l, float half,
                                    int alpha, float phase) {
        v.bodyBox(f, y, l, half * 0.88f, half * 0.88f, half * 0.88f,
                ICE, Math.round(alpha * 0.13f));
        int edge = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int s1 = -1; s1 <= 1; s1 += 2) for (int s2 = -1; s2 <= 1; s2 += 2) {
                float[] start = new float[3], end = new float[3];
                int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
                start[a1] = end[a1] = s1 * half;
                start[a2] = end[a2] = s2 * half;
                start[axis] = -half;
                end[axis] = half;
                float scan = 0.5f + 0.5f * Mth.sin(phase * 0.55f + edge * 1.71f);
                float middle = -half + scan * half * 2.0f;
                float[] split = start.clone();
                split[axis] = middle;
                int rgb = (edge + Math.round(phase)) % 4 == 0 ? WHITE : (edge % 3 == 0 ? HONEY : CYAN);
                v.glowLink(f + start[0], y + start[1], l + start[2],
                        f + split[0], y + split[1], l + split[2],
                        0.012f + scan * 0.008f, rgb, WHITE, Math.round(alpha * (0.50f + scan * 0.50f)));
                v.glowLink(f + split[0], y + split[1], l + split[2],
                        f + end[0], y + end[1], l + end[2],
                        0.012f + (1.0f - scan) * 0.008f, rgb, WHITE,
                        Math.round(alpha * (0.50f + (1.0f - scan) * 0.50f)));
                edge++;
            }
        }
    }

    private static final class Voxels {
        final VertexConsumer glow, body;
        final Matrix4f mat;
        final Vector3f tmp;
        final float cx, cy, cz, dx, dz, time;

        Voxels(VertexConsumer glow, VertexConsumer body, Matrix4f mat, Vector3f tmp,
               float cx, float cy, float cz, float dx, float dz, float time) {
            this.glow = glow; this.body = body; this.mat = mat; this.tmp = tmp;
            this.cx = cx; this.cy = cy; this.cz = cz; this.dx = dx; this.dz = dz; this.time = time;
        }

        void glow(float f, float y, float l, float half, int rgb, int alpha) {
            emit(glow, f, y, l, half, half, half, 0.0f, 0.0f, 0.0f, rgb, alpha);
        }

        void bodyBox(float f, float y, float l, float hf, float hy, float hl, int rgb, int alpha) {
            emit(body, f, y, l, hf, hy, hl, 0.0f, 0.0f, 0.0f, rgb, alpha);
        }

        void rot(float f, float y, float l, float hf, float hy, float hl,
                 float yaw, float pitch, float roll, int rgb, int alpha) {
            emit(glow, f, y, l, hf, hy, hl, yaw, pitch, roll, rgb, alpha);
        }

        void bodyLink(float f0, float y0, float l0, float f1, float y1, float l1,
                      float half, int rgb, int alpha) {
            link(body, f0, y0, l0, f1, y1, l1, half, rgb, rgb, alpha);
        }

        void glowLink(float f0, float y0, float l0, float f1, float y1, float l1,
                      float half, int rgb, int tip, int alpha) {
            link(glow, f0, y0, l0, f1, y1, l1, half, rgb, tip, alpha);
        }

        /** Facette alpha + âme additive : la transparence reste lisible sur fond clair et sombre. */
        void lightLink(float f0, float y0, float l0, float f1, float y1, float l1,
                       float half, int rgb, int tip, int alpha) {
            link(body, f0, y0, l0, f1, y1, l1, half * 1.75f, rgb, rgb, Math.round(alpha * 0.24f));
            link(glow, f0, y0, l0, f1, y1, l1, half, rgb, tip, alpha);
        }

        void outlineSquare(float f, float y, float l, float half, float yaw, int rgb, int alpha) {
            float c = Mth.cos(yaw), s = Mth.sin(yaw);
            for (int side = 0; side < 4; side++) {
                float a0 = side * TAU / 4.0f + 0.7853982f;
                float a1 = (side + 1) * TAU / 4.0f + 0.7853982f;
                float x0 = Mth.cos(a0) * half, z0 = Mth.sin(a0) * half;
                float x1 = Mth.cos(a1) * half, z1 = Mth.sin(a1) * half;
                glowLink(f + x0 * c - z0 * s, y, l + x0 * s + z0 * c,
                        f + x1 * c - z1 * s, y, l + x1 * s + z1 * c,
                        0.013f, rgb, WHITE, alpha);
            }
        }

        void link(VertexConsumer vc, float f0, float y0, float l0, float f1, float y1, float l1,
                  float half, int rgb, int tip, int alpha) {
            float df = f1 - f0, dy = y1 - y0, dl = l1 - l0;
            float length = Mth.sqrt(df * df + dy * dy + dl * dl);
            if (length < 0.001f || alpha <= 5) return;
            float sx = dx * df - dz * dl, sz = dz * df + dx * dl;
            float yaw = (float) Math.atan2(sx, sz);
            float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
            emit(vc, (f0 + f1) * 0.5f, (y0 + y1) * 0.5f, (l0 + l1) * 0.5f,
                    half, length * 0.53f, half, yaw, pitch, 0.0f,
                    WeaponWorldVfxManager.mixRgb(rgb, tip, 0.30f), alpha);
        }

        void emit(VertexConsumer vc, float f, float y, float l, float hf, float hy, float hl,
                  float yaw, float pitch, float roll, int rgb, int alpha) {
            if (alpha <= 5 || hf <= 0.0005f || hy <= 0.0005f || hl <= 0.0005f) return;
            float x = cx + dx * f - dz * l;
            float z = cz + dz * f + dx * l;
            WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy + y, z,
                    hf, hy, hl, yaw, pitch, roll, rgb, alpha);
        }
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float easeOut(float x) {
        x = clamp(x);
        return 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x);
    }

    private static float easeIn(float x) {
        x = clamp(x);
        return x * x * x;
    }

    private static float easeInOut(float x) {
        x = clamp(x);
        return x < 0.5f ? 4.0f * x * x * x
                : 1.0f - (float) Math.pow(-2.0f * x + 2.0f, 3.0f) * 0.5f;
    }

    private static float clamp(float x) { return Mth.clamp(x, 0.0f, 1.0f); }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
