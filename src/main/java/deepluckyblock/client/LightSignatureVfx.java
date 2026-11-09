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
        Voxels v = new Voxels(glow, body, mat, tmp, cx, cy, cz, dx, dz, t, variant != ASTRAL);
        if (variant == SERAPH) renderSeraph(v, t, alpha, salt);
        else if (variant == ASTRAL) renderAstral(v, t, alpha, salt);
        else renderChrono(v, t, alpha, salt);
    }

    /** SERAPH V4 — 0,5 s de ligne, 0,2 s de beam 0,6 × 16, puis extinction. */
    private static void renderSeraph(Voxels v, float t, int alpha, int salt) {
        float castEnd = 10.0f / 30.0f;
        float damageEnd = 14.0f / 30.0f;
        float line = 1.0f - smooth((t - (castEnd - 1.0f / 60.0f)) / (1.0f / 60.0f));
        float open = smooth((t - castEnd) / (1.0f / 30.0f));
        float close = smooth((t - damageEnd) / (2.0f / 30.0f));
        float beam = open * (1.0f - close);
        float seal = smooth(t / 0.08f) * (1.0f - smooth((t - damageEnd) / (1.0f - damageEnd)));
        float lineAlpha = line * (1.0f - open);
        int a = Math.round(alpha * smooth(t / 0.025f) * (1.0f - smooth((t - 0.92f) / 0.075f)));

        // Cercle divin vertical, face au lanceur : trois cercles concentriques, double étoile
        // à huit branches, trois satellites et runes diamant. Le mouvement est dans les plans
        // du sceau, pas dans une accumulation de volumes translucides.
        float cy = 14.50f;
        for (int ring = 0; ring < 3; ring++) {
            float radius = 1.92f - ring * 0.31f;
            float spin = (ring % 2 == 0 ? 1.0f : -1.0f) * t * (0.60f + ring * 0.18f);
            for (int part = 0; part < 32; part++) {
                float angle = part * TAU / 32.0f + spin, next = (part + 1) * TAU / 32.0f + spin;
                v.glowLink(Mth.cos(angle) * radius, cy + Mth.sin(angle) * radius, -0.12f - ring * 0.025f,
                        Mth.cos(next) * radius, cy + Mth.sin(next) * radius, -0.12f - ring * 0.025f,
                        ring == 0 ? 0.022f : 0.014f, ring == 1 ? IVORY : GOLD,
                        WHITE, Math.round(a * seal * (0.82f - ring * 0.10f)));
            }
        }
        for (int layer = 0; layer < 2; layer++) {
            float rotation = layer * TAU / 16.0f + (layer == 0 ? t * 0.32f : -t * 0.24f);
            for (int point = 0; point < 16; point++) {
                float a0 = point * TAU / 16.0f + rotation, a1 = (point + 1) * TAU / 16.0f + rotation;
                float r0 = point % 2 == 0 ? 1.55f : 0.66f;
                float r1 = (point + 1) % 2 == 0 ? 1.55f : 0.66f;
                v.glowLink(Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, -0.18f,
                        Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, -0.18f,
                        layer == 0 ? 0.020f : 0.013f,
                        layer == 0 ? HONEY : IVORY, WHITE, Math.round(a * seal * 0.84f));
            }
        }
        for (int rune = 0; rune < 8; rune++) {
            float angle = rune * TAU / 8.0f + t * 0.60f;
            float f = Mth.cos(angle) * 1.92f, y = cy + Mth.sin(angle) * 1.92f;
            v.glowLink(f - 0.09f, y, -0.14f, f, y + 0.09f, -0.14f, 0.012f, GOLD, WHITE, Math.round(a * seal));
            v.glowLink(f, y + 0.09f, -0.14f, f + 0.09f, y, -0.14f, 0.012f, GOLD, WHITE, Math.round(a * seal));
            v.glowLink(f + 0.09f, y, -0.14f, f, y - 0.09f, -0.14f, 0.012f, GOLD, WHITE, Math.round(a * seal));
            v.glowLink(f, y - 0.09f, -0.14f, f - 0.09f, y, -0.14f, 0.012f, GOLD, WHITE, Math.round(a * seal));
        }
        for (int satellite = 0; satellite < 3; satellite++) {
            float angle = satellite * TAU / 3.0f + (float) Math.PI / 2.0f - t * 0.20f;
            float cf = Mth.cos(angle) * 1.92f, sy = cy + Mth.sin(angle) * 1.92f;
            for (int part = 0; part < 12; part++) {
                float p0 = part * TAU / 12.0f - t, p1 = (part + 1) * TAU / 12.0f - t;
                v.glowLink(cf + Mth.cos(p0) * 0.23f, sy + Mth.sin(p0) * 0.23f, -0.14f,
                        cf + Mth.cos(p1) * 0.23f, sy + Mth.sin(p1) * 0.23f, -0.14f,
                        0.012f, IVORY, WHITE, Math.round(a * seal * 0.76f));
            }
        }
        v.bodyBox(0.0f, cy, -0.10f, 0.28f, 0.28f, 0.06f, WHITE, Math.round(a * seal * 0.28f));
        astralShell(v, 0.0f, cy, -0.16f, 0.22f, Math.round(a * seal), t * 18.0f);

        // ÉTAPE 1 : ligne 0,05, purement visuelle, strictement avant le tick 10.
        if (lineAlpha > 0.0f) {
            v.lightLink(0.0f, 15.10f, 0.0f, 0.0f, -0.90f, 0.0f,
                    0.050f, WHITE, IVORY, Math.round(a * lineAlpha));
        }
        // ÉTAPE 2 : une seule enveloppe intérieure et douze arêtes. Le volume intérieur est
        // réellement lumineux et le rayon extérieur atteint 0,55 + 0,05 = 0,60 bloc.
        if (beam > 0.0f) {
            v.bodyBox(0.0f, 7.10f, 0.0f, 0.48f, 8.0f, 0.48f,
                    IVORY, Math.round(a * beam * 0.20f));
            // Huit panneaux tangentiels forment un cylindre lisible sans pile de cubes pleins.
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f;
                v.bodyBox(Mth.cos(angle) * 0.50f, 7.10f, Mth.sin(angle) * 0.50f,
                        0.225f, 8.0f, 0.012f, IVORY, Math.round(a * beam * 0.16f),
                        -angle - Mth.HALF_PI, 0.0f, 0.0f);
            }
            v.lightLink(0.0f, 15.10f, 0.0f, 0.0f, -0.90f, 0.0f,
                    0.075f, WHITE, IVORY, Math.round(a * beam));
            for (int strand = 0; strand < 3; strand++) {
                float angle = strand * TAU / 3.0f + t * 2.0f;
                float f = Mth.cos(angle) * 0.17f, l = Mth.sin(angle) * 0.17f;
                v.glowLink(f, 15.10f, l, f, -0.90f, l,
                        0.025f, strand == 0 ? WHITE : IVORY, WHITE, Math.round(a * beam * 0.88f));
            }
            for (int side = 0; side < 12; side++) {
                float angle = side * TAU / 12.0f;
                float f = Mth.cos(angle) * 0.55f, l = Mth.sin(angle) * 0.55f;
                v.bodyLink(f, 15.10f, l, f, -0.90f, l,
                        0.050f, IVORY, Math.round(a * beam * 0.30f));
                v.glowLink(f, 15.10f, l, f, -0.90f, l,
                        0.014f, side % 3 == 0 ? WHITE : HONEY, WHITE, Math.round(a * beam * 0.64f));
            }
            for (int level = 0; level < 4; level++) {
                float y = -0.50f + level * 4.0f;
                for (int side = 0; side < 12; side++) {
                    float angle = side * TAU / 12.0f, next = (side + 1) * TAU / 12.0f;
                    v.glowLink(Mth.cos(angle) * 0.55f, y, Mth.sin(angle) * 0.55f,
                            Mth.cos(next) * 0.55f, y, Mth.sin(next) * 0.55f,
                            0.012f, HONEY, IVORY, Math.round(a * beam * 0.42f));
                }
            }
            for (int ray = 0; ray < 12; ray++) {
                float angle = ray * TAU / 12.0f;
                v.glowLink(Mth.cos(angle) * 0.45f, -0.82f, Mth.sin(angle) * 0.45f,
                        Mth.cos(angle) * 0.88f, -0.79f, Mth.sin(angle) * 0.88f,
                        0.012f, HONEY, IVORY, Math.round(a * beam * 0.34f));
            }
        }
    }

    /** Coordonnées équatoriales J2000 simplifiées : ascension droite en heures, déclinaison en degrés. */
    private record SkyStar(String name, double raHours, double decDegrees) { }
    private static float[] sky(SkyStar star) {
        double ra = star.raHours * Math.PI / 12.0, dec = Math.toRadians(star.decDegrees);
        return new float[]{(float) (Math.cos(dec) * Math.cos(ra)),
                (float) Math.sin(dec), (float) (Math.cos(dec) * Math.sin(ra))};
    }
    private static float[][] centered(float[][] stars) {
        float x = 0.0f, y = 0.0f, z = 0.0f;
        for (float[] star : stars) { x += star[0]; y += star[1]; z += star[2]; }
        x /= stars.length; y /= stars.length; z /= stars.length;
        for (float[] star : stars) { star[0] -= x; star[1] -= y; star[2] -= z; }
        return stars;
    }
    private static final float[][] URSA_MAJOR = centered(new float[][]{
            sky(new SkyStar("Dubhe", 11.062, 61.751)), sky(new SkyStar("Merak", 11.031, 56.382)),
            sky(new SkyStar("Phecda", 11.897, 53.695)), sky(new SkyStar("Megrez", 12.257, 57.033)),
            sky(new SkyStar("Alioth", 12.900, 55.960)), sky(new SkyStar("Mizar", 13.399, 54.925)),
            sky(new SkyStar("Alkaid", 13.792, 49.313))});
    private static final int[] URSA_LINKS = {0, 1, 1, 2, 2, 3, 3, 0, 3, 4, 4, 5, 5, 6};
    private static final float[][] CASSIOPEIA = centered(new float[][]{
            sky(new SkyStar("Caph", .153, 59.150)), sky(new SkyStar("Schedar", .675, 56.537)),
            sky(new SkyStar("Gamma Cas", .945, 60.717)), sky(new SkyStar("Ruchbah", 1.430, 60.235)),
            sky(new SkyStar("Segin", 1.907, 63.670))});
    private static final int[] CASSIOPEIA_LINKS = {0, 1, 1, 2, 2, 3, 3, 4};
    private static final float[][] ORION = centered(new float[][]{
            sky(new SkyStar("Betelgeuse", 5.919, 7.407)), sky(new SkyStar("Bellatrix", 5.418, 6.350)),
            sky(new SkyStar("Alnitak", 5.679, -1.943)), sky(new SkyStar("Alnilam", 5.604, -1.202)),
            sky(new SkyStar("Mintaka", 5.533, -.299)), sky(new SkyStar("Saiph", 5.796, -9.670)),
            sky(new SkyStar("Rigel", 5.242, -8.202))});
    private static final int[] ORION_LINKS = {0, 1, 0, 2, 1, 4, 2, 3, 3, 4, 2, 5, 4, 6, 5, 6};
    private static final float[][] CONSTELLATION_SOURCES = {
            {-3.50f, 3.20f, -2.60f}, {0.40f, 4.10f, 2.80f}, {3.40f, 2.90f, -2.20f}};

    /** ASTRAL V4 — trois constellations J2000 séparées, lance sacrée en composants réels. */
    private static void renderAstral(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.045f), exit = 1.0f - smooth((t - 0.76f) / 0.22f);
        int a = Math.round(alpha * enter * exit);
        float travel = easeInOut((t - 0.07f) / 0.23f), after = easeOut((t - 0.30f) / 0.23f);
        float head = -4.35f + travel * 4.60f + after * 4.35f;
        float impact = 1.0f - smooth(Math.abs(t - 0.30f) / 0.095f);
        float visibility = 1.0f - smooth((t - 0.42f) / 0.25f);
        float charge = smooth((t - 0.07f) / 0.13f) * (1.0f - smooth((t - 0.27f) / 0.15f));
        drawConstellation(v, URSA_MAJOR, URSA_LINKS, -3.50f, 3.20f, -2.60f,
                3.0f, t * 0.22f, 0.30f, visibility, a, t);
        drawConstellation(v, CASSIOPEIA, CASSIOPEIA_LINKS, 0.40f, 4.10f, 2.80f,
                3.4f, -t * 0.18f + 1.0f, -0.42f, visibility, a, t + 1.0f);
        drawConstellation(v, ORION, ORION_LINKS, 3.40f, 2.90f, -2.20f,
                3.2f, t * 0.20f + 2.0f, 0.48f, visibility, a, t + 2.0f);
        for (int beam = 0; beam < 3; beam++) {
            float[] source = CONSTELLATION_SOURCES[beam];
            float targetF = head - 3.05f + beam * 1.68f;
            v.glowLink(source[0], source[1], source[2], targetF, 0.0f, 0.0f,
                    0.012f, beam == 1 ? LILAC : CYAN, WHITE, Math.round(a * charge * 0.68f));
            for (int packet = 0; packet < 2; packet++) {
                float u = (t * 5.0f + beam * 0.23f + packet * 0.5f) % 1.0f;
                v.glow(Mth.lerp(u, source[0], targetF), Mth.lerp(u, source[1], 0.0f),
                        Mth.lerp(u, source[2], 0.0f), 0.030f,
                        beam == 1 ? LILAC : ICE, Math.round(a * charge * 0.86f));
            }
        }
        float lanceAlpha = 1.0f - smooth((t - 0.58f) / 0.25f);
        // Manche : vingt sections, quatre rails, quatre bagues de douille et un pommeau.
        for (int segment = 0; segment < 20; segment++) {
            float u = segment / 19.0f, f = head - 3.05f + u * 2.50f;
            float radius = 0.10f + 0.035f * Mth.sin(u * (float) Math.PI);
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + (float) Math.PI / 4.0f;
                float next = (side + 1) * TAU / 4.0f + (float) Math.PI / 4.0f;
                v.bodyLink(f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        f, Mth.cos(next) * radius, Mth.sin(next) * radius,
                        0.035f, side % 2 == 0 ? CYAN : VIOLET, Math.round(a * lanceAlpha * 0.40f));
                if (segment % 2 == 0) v.glowLink(f - 0.055f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        f + 0.055f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        0.014f, side % 2 == 0 ? ICE : LILAC, WHITE, Math.round(a * lanceAlpha * 0.82f));
            }
            v.glow(f, 0.0f, 0.0f, 0.026f, segment % 5 == 0 ? WHITE : CYAN, Math.round(a * lanceAlpha));
        }
        for (int rail = 0; rail < 4; rail++) {
            float angle = rail * TAU / 4.0f + (float) Math.PI / 4.0f;
            v.glowLink(head - 3.05f, Mth.cos(angle) * 0.10f, Mth.sin(angle) * 0.10f,
                    head - 0.55f, Mth.cos(angle) * 0.10f, Mth.sin(angle) * 0.10f,
                    0.014f, rail % 2 == 0 ? GOLD : CYAN, WHITE, Math.round(a * lanceAlpha * 0.82f));
        }
        for (int socket = 0; socket < 4; socket++) {
            float f = head - 0.96f + socket * 0.12f;
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f, next = (side + 1) * TAU / 8.0f;
                v.glowLink(f, Mth.cos(angle) * 0.18f, Mth.sin(angle) * 0.18f,
                        f, Mth.cos(next) * 0.18f, Mth.sin(next) * 0.18f,
                        0.016f, GOLD, WHITE, Math.round(a * lanceAlpha));
            }
        }
        astralShell(v, head - 3.10f, 0.0f, 0.0f, 0.17f, Math.round(a * lanceAlpha), t * 20.0f);
        for (int ray = 0; ray < 4; ray++) {
            float angle = ray * TAU / 4.0f;
            v.glowLink(head - 3.10f, 0.0f, 0.0f, head - 3.30f,
                    Mth.cos(angle) * 0.28f, Mth.sin(angle) * 0.28f,
                    0.018f, GOLD, WHITE, Math.round(a * lanceAlpha));
        }
        // Lame-largeur réelle : dix sections, quatre panneaux longitudinaux et quatre arêtes.
        // Le losange est large au premier tiers puis se rétrécit jusqu'à la pointe.
        for (int section = 0; section < 9; section++) {
            float u0 = section / 9.0f, u1 = (section + 1) / 9.0f;
            float f0 = head - 0.55f + u0 * 1.35f, f1 = head - 0.55f + u1 * 1.35f;
            float r0 = bladeWidth(u0), r1 = bladeWidth(u1);
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + (float) Math.PI / 4.0f;
                float next = (side + 1) * TAU / 4.0f + (float) Math.PI / 4.0f;
                float y0 = Mth.cos(angle) * r0, l0 = Mth.sin(angle) * r0;
                float y1 = Mth.cos(next) * r0, l1 = Mth.sin(next) * r0;
                v.bodyLink(f0, y0, l0, f0, y1, l1,
                        0.050f, side % 2 == 0 ? ICE : VIOLET, Math.round(a * lanceAlpha * 0.72f));
                v.bodyLink(f0, y0, l0, f1, Mth.cos(angle) * r1, Mth.sin(angle) * r1,
                        0.040f, side % 2 == 0 ? ICE : VIOLET, Math.round(a * lanceAlpha * 0.62f));
                v.glowLink(f0, y0, l0, f1, Mth.cos(angle) * r1, Mth.sin(angle) * r1,
                        0.016f, side % 2 == 0 ? WHITE : CYAN, WHITE, Math.round(a * lanceAlpha));
                if (section == 8) v.glowLink(f1, Mth.cos(angle) * r1, Mth.sin(angle) * r1,
                        f1, Mth.cos(next) * r1, Mth.sin(next) * r1,
                        0.012f, WHITE, ICE, Math.round(a * lanceAlpha));
            }
        }
        // Arête centrale et gouttière sombre : la lame n'est plus un simple cône lumineux.
        v.glowLink(head - 0.55f, 0.10f, 0.0f, head + 0.80f, 0.0f, 0.0f,
                0.020f, WHITE, IVORY, Math.round(a * lanceAlpha));
        v.glowLink(head - 0.55f, -0.10f, 0.0f, head + 0.80f, 0.0f, 0.0f,
                0.020f, WHITE, IVORY, Math.round(a * lanceAlpha));
        v.glowLink(head - 0.38f, 0.0f, 0.17f, head + 0.50f, 0.0f, 0.025f,
                0.018f, BLUE, CYAN, Math.round(a * lanceAlpha * 0.84f));
        // Croisée d'or, joyau central et gravures sur la lame.
        for (int wing = 0; wing < 4; wing++) {
            float angle = wing * TAU / 4.0f;
            float y0 = Mth.cos(angle) * 0.13f, l0 = Mth.sin(angle) * 0.13f;
            float y1 = Mth.cos(angle) * 0.55f, l1 = Mth.sin(angle) * 0.55f;
            v.glowLink(head - 0.68f, y0, l0, head - 0.82f, y1, l1, 0.025f, GOLD, IVORY, Math.round(a * lanceAlpha));
            v.glowLink(head - 0.82f, y1, l1, head - 0.48f, y1 * 0.72f, l1 * 0.72f, 0.022f, GOLD, WHITE, Math.round(a * lanceAlpha));
            v.glowLink(head - 0.48f, y1 * 0.72f, l1 * 0.72f, head - 0.38f, y0, l0, 0.018f, HONEY, WHITE, Math.round(a * lanceAlpha));
        }
        astralShell(v, head - 0.60f, 0.0f, 0.0f, 0.20f, Math.round(a * lanceAlpha), t * 24.0f);
        for (int rune = 0; rune < 5; rune++) {
            float f = head - 0.28f + rune * 0.18f, y = rune % 2 == 0 ? 0.15f : -0.15f;
            v.glowLink(f - 0.055f, y, 0.0f, f, y + 0.055f, 0.0f, 0.010f, GOLD, WHITE, Math.round(a * lanceAlpha));
            v.glowLink(f, y + 0.055f, 0.0f, f + 0.055f, y, 0.0f, 0.010f, GOLD, WHITE, Math.round(a * lanceAlpha));
            v.glowLink(f + 0.055f, y, 0.0f, f, y - 0.055f, 0.0f, 0.010f, GOLD, WHITE, Math.round(a * lanceAlpha));
            v.glowLink(f, y - 0.055f, 0.0f, f - 0.055f, y, 0.0f, 0.010f, GOLD, WHITE, Math.round(a * lanceAlpha));
        }
        for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 20; i++) {
            float u = i / 19.0f, angle = u * TAU * 1.4f + strand * (float) Math.PI - t * 8.0f;
            v.glow(head - 3.05f + u * 3.05f, Mth.cos(angle) * 0.18f, Mth.sin(angle) * 0.18f,
                    i % 5 == 0 ? 0.028f : 0.015f, strand == 0 ? CYAN : LILAC,
                    Math.round(a * lanceAlpha * (0.42f + 0.40f * u)));
        }
        float wake = smooth((t - 0.13f) / 0.17f) * (1.0f - smooth((t - 0.53f) / 0.28f));
        for (int trail = 0; trail < 16; trail++) {
            float u = trail / 15.0f, h = hash(salt, 540 + trail * 29), angle = trail * 2.3999632f - t * 3.0f;
            float r = (0.10f + u * 0.30f) * (0.65f + h * 0.35f);
            v.rot(head - 0.40f - u * 2.70f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.025f + h * 0.012f, 0.012f, 0.050f, angle, t * 2.0f, h,
                    trail % 4 == 0 ? WHITE : trail % 2 == 0 ? CYAN : LILAC,
                    Math.round(a * wake * (1.0f - u * 0.60f)));
        }
        if (impact > 0.0f) {
            for (int node = 0; node <= 32; node++) {
                float f = -4.35f + node * 8.70f / 32.0f;
                v.glow(f, 0.0f, 0.0f, node % 4 == 0 ? 0.028f : 0.015f,
                        node % 4 == 0 ? WHITE : CYAN, Math.round(a * impact * 0.68f));
            }
            for (int ray = 0; ray < 8; ray++) {
                float angle = ray * TAU / 8.0f, reach = (ray % 2 == 0 ? 1.55f : 0.90f) * impact;
                v.glowLink(0.12f, 0.0f, 0.0f, 0.12f, Mth.cos(angle) * reach, Mth.sin(angle) * reach,
                        ray % 2 == 0 ? 0.022f : 0.013f, ray % 3 == 0 ? LILAC : ICE, WHITE, Math.round(a * impact));
            }
            for (int part = 0; part < 40; part++) {
                float angle = part * TAU / 40.0f, next = (part + 1) * TAU / 40.0f, r = 0.35f + impact * 1.25f;
                v.glowLink(Mth.cos(angle) * r, -0.86f, Mth.sin(angle) * r,
                        Mth.cos(next) * r, -0.86f, Mth.sin(next) * r,
                        0.012f, LILAC, ICE, Math.round(a * impact * 0.52f));
            }
            astralShell(v, 0.12f, 0.0f, 0.0f, 0.25f + impact * 0.10f, Math.round(a * impact), t * 30.0f);
        }
        for (int shard = 0; shard < 12; shard++) {
            float age = clamp((t - 0.30f - (shard % 3) * 0.006f) / 0.37f);
            if (age <= 0.0f || age >= 1.0f) continue;
            float h = hash(salt, 700 + shard * 43), angle = shard * 2.3999632f, r = age * (0.30f + h * 1.45f);
            v.rot(age * 2.1f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.045f, 0.018f + h * 0.015f, 0.014f, angle, age * 4.0f, h * 3.0f,
                    shard % 3 == 0 ? LILAC : ICE, Math.round(a * (1.0f - age) * 0.86f));
        }
    }

    private static float bladeWidth(float u) {
        return u < 0.30f ? 0.16f + u / 0.30f * 0.30f : 0.46f * (1.0f - (u - 0.30f) / 0.70f) + 0.008f;
    }

    /** Projection sphérique rigide : rotations X/Y/Z, sans changer les distances angulaires. */
    private static void drawConstellation(Voxels v, float[][] stars, int[] links,
                                          float cf, float cy, float cl, float scale,
                                          float spin, float tilt, float visibility, int alpha, float phase) {
        float cs = Mth.cos(spin), sn = Mth.sin(spin), ct = Mth.cos(tilt), st = Mth.sin(tilt);
        for (int i = 0; i < stars.length; i++) {
            float[] star = stars[i];
            float x = star[0] * scale, y = star[1] * scale, z = star[2] * scale;
            float y1 = y * ct - z * st, z1 = y * st + z * ct;
            float f = x * cs + z1 * sn, l = -x * sn + z1 * cs;
            float pulse = 0.72f + 0.28f * Mth.sin(phase * 12.0f + i * 1.7f);
            v.glow(cf + f, cy + y1, cl + l, i % 3 == 0 ? 0.050f : 0.034f,
                    i % 2 == 0 ? WHITE : ICE, Math.round(alpha * visibility * pulse));
            if (i % 3 == 0) {
                v.glowLink(cf + f - 0.10f, cy + y1, cl + l, cf + f + 0.10f, cy + y1, cl + l,
                        0.010f, CYAN, WHITE, Math.round(alpha * visibility * 0.74f));
                v.glowLink(cf + f, cy + y1 - 0.10f, cl + l, cf + f, cy + y1 + 0.10f, cl + l,
                        0.010f, CYAN, WHITE, Math.round(alpha * visibility * 0.74f));
            }
        }
        for (int i = 0; i < links.length; i += 2) {
            skyLink(v, stars[links[i]], stars[links[i + 1]], cf, cy, cl, scale, cs, sn, ct, st,
                    Math.round(alpha * visibility * 0.64f));
        }
    }

    private static void skyLink(Voxels v, float[] s0, float[] s1, float cf, float cy, float cl,
                                float scale, float cs, float sn, float ct, float st, int alpha) {
        float x0 = s0[0] * scale, y0 = s0[1] * scale, z0 = s0[2] * scale;
        float x1 = s1[0] * scale, y1 = s1[1] * scale, z1 = s1[2] * scale;
        float ry0 = y0 * ct - z0 * st, rz0 = y0 * st + z0 * ct;
        float ry1 = y1 * ct - z1 * st, rz1 = y1 * st + z1 * ct;
        v.glowLink(cf + x0 * cs + rz0 * sn, cy + ry0, cl - x0 * sn + rz0 * cs,
                cf + x1 * cs + rz1 * sn, cy + ry1, cl - x1 * sn + rz1 * cs,
                0.010f, BLUE, ICE, alpha);
    }

    /** CHRONO V4 — horloge élevée et plan orienté vers la caméra du joueur. */
    private static void renderChrono(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.055f);
        float exit = 1.0f - smooth((t - 0.82f) / 0.16f);
        int a = Math.round(alpha * enter * exit);
        float assemble = easeOut(t / 0.16f);
        float close = easeInOut((t - 0.34f) / 0.21f);
        float release = smooth((t - 0.55f) / 0.25f);
        float strike = 1.0f - smooth(Math.abs(t - 0.55f) / 0.070f);
        float handSpin = chronometerAngle(t);
        float centerY = 4.05f;
        float radius = (2.10f - close * 0.28f + release * 0.18f) * assemble;

        // Quatre engrenages réels et reliés visuellement : sens alternés et ratios différents.
        for (int gear = 0; gear < 4; gear++) {
            float sx = (gear & 1) == 0 ? -1.0f : 1.0f;
            float sy = (gear & 2) == 0 ? -1.0f : 1.0f;
            float f = sx * 1.82f, y = centerY + sy * 1.48f;
            float gearRadius = gear % 2 == 0 ? 0.52f : 0.40f;
            int teeth = gear % 2 == 0 ? 16 : 12;
            float rotation = handSpin * (gear % 2 == 0 ? -0.50f : 0.75f) + gear;
            clockGear(v, f, y, 0.28f, gearRadius * assemble, teeth, rotation,
                    gear % 2 == 0 ? GOLD : CYAN, Math.round(a * 0.78f));
        }

        // Boîtier et double cadran vertical. Les 60 graduations rendent l'objet identifiable
        // avant même de regarder les aiguilles.
        for (int ring = 0; ring < 2; ring++) {
            float r = radius * (ring == 0 ? 1.0f : 0.88f);
            for (int part = 0; part < 72; part++) {
                float angle = part * TAU / 72.0f;
                float next = (part + 1) * TAU / 72.0f;
                float f0 = Mth.sin(angle) * r, y0 = centerY + Mth.cos(angle) * r;
                float f1 = Mth.sin(next) * r, y1 = centerY + Mth.cos(next) * r;
                int rgb = ring == 0 ? HONEY : TIME_DARK;
                v.lightLink(f0, y0, 0.0f, f1, y1, 0.0f,
                        ring == 0 ? 0.028f : 0.018f, rgb,
                        ring == 0 ? IVORY : CYAN, Math.round(a * (ring == 0 ? 0.82f : 0.52f)));
            }
        }
        for (int minute = 0; minute < 60; minute++) {
            float angle = minute * TAU / 60.0f;
            boolean hour = minute % 5 == 0;
            float inner = radius * (hour ? 0.79f : 0.83f);
            float outer = radius * 0.88f;
            v.glowLink(Mth.sin(angle) * inner, centerY + Mth.cos(angle) * inner, -0.035f,
                    Mth.sin(angle) * outer, centerY + Mth.cos(angle) * outer, -0.035f,
                    hour ? 0.025f : 0.010f, hour ? WHITE : GOLD, IVORY,
                    Math.round(a * (hour ? 0.95f : 0.58f)));
        }
        // Les douze chiffres sont dessinés en pixels 3×5, jamais remplacés par de simples cubes.
        for (int number = 1; number <= 12; number++) {
            float angle = (number % 12) * TAU / 12.0f;
            float r = radius * 0.68f;
            clockNumber(v, number, Mth.sin(angle) * r, centerY + Mth.cos(angle) * r,
                    -0.070f, 0.044f * assemble, number % 3 == 0 ? WHITE : IVORY,
                    Math.round(a * 0.94f));
        }

        // Trois aiguilles de vitesses et longueurs distinctes. La trotteuse cyan effectue les
        // tours visibles ; la grande aiguille suit au 1/12 et la petite au 1/60.
        clockHand(v, handSpin / 60.0f + 0.70f, centerY, radius * 0.40f,
                0.060f, GOLD, IVORY, a);
        clockHand(v, handSpin / 12.0f - 0.45f, centerY, radius * 0.63f,
                0.042f, WHITE, CYAN, a);
        clockHand(v, handSpin, centerY, radius * 0.80f,
                0.025f, CYAN, WHITE, a);
        astralShell(v, 0.0f, centerY, -0.10f, 0.16f + strike * 0.10f, a, t * 24.0f);

        // Pendule : son pivot est sous le cadran, sa masse balance au rythme de la trotteuse,
        // s'arrête avec elle à .55 puis inverse naturellement sa course pendant le rembobinage.
        float pendulum = Mth.sin(handSpin * 1.5f) * 0.48f;
        float pivotY = centerY - radius - 0.18f;
        float length = 1.10f * assemble;
        float bobF = Mth.sin(pendulum) * length;
        float bobY = pivotY - Mth.cos(pendulum) * length;
        v.lightLink(0.0f, pivotY, 0.05f, bobF, bobY, 0.05f,
                0.028f, GOLD, IVORY, Math.round(a * 0.85f));
        astralShell(v, bobF, bobY, 0.05f, 0.22f * assemble,
                Math.round(a * 0.88f), handSpin * 2.0f);
        v.glowLink(-0.16f, pivotY, 0.05f, 0.16f, pivotY, 0.05f,
                0.026f, HONEY, WHITE, a);

        // À la dette, le cadran se fracture en douze directions. Les éclats repartent ensuite
        // vers leurs positions initiales au lieu de tomber comme des débris ordinaires.
        if (strike > 0.0f) {
            for (int ray = 0; ray < 12; ray++) {
                float angle = ray * TAU / 12.0f;
                float inner = radius * 0.24f;
                float outer = radius * (0.50f + (ray % 3) * 0.13f) * strike;
                v.glowLink(Mth.sin(angle) * inner, centerY + Mth.cos(angle) * inner, -0.12f,
                        Mth.sin(angle) * outer, centerY + Mth.cos(angle) * outer, -0.12f,
                        0.018f, ray % 3 == 0 ? CYAN : WHITE, IVORY,
                        Math.round(a * strike * 0.82f));
            }
        }
        for (int shard = 0; shard < 42; shard++) {
            float h = hash(salt, 1600 + shard * 43);
            float local = t < 0.55f ? t / 0.55f : 1.0f - (t - 0.55f) / 0.45f;
            local = clamp(local);
            float angle = shard * 2.3999632f + handSpin * 0.12f;
            float r = (1.85f + h * 0.75f) * (0.65f + local * 0.35f);
            float y = centerY + (h - 0.5f) * 3.6f;
            v.rot(Mth.cos(angle) * r, y, Mth.sin(angle) * r * 0.35f,
                    0.025f + h * 0.016f, 0.018f, 0.050f,
                    angle, local * 3.0f, h * 2.0f,
                    shard % 4 == 0 ? CYAN : shard % 3 == 0 ? LILAC : HONEY,
                    Math.round(a * (0.26f + 0.50f * local)));
        }
    }

    private static void clockHand(Voxels v, float angle, float centerY, float length,
                                  float half, int rgb, int tip, int alpha) {
        float f = Mth.sin(angle) * length, y = centerY + Mth.cos(angle) * length;
        float backF = -Mth.sin(angle) * 0.24f, backY = centerY - Mth.cos(angle) * 0.24f;
        v.glowLink(backF, backY, -0.13f, f, y, -0.13f, half, rgb, tip, alpha);
        float arrow = 0.15f;
        for (int side = -1; side <= 1; side += 2) {
            float wingAngle = angle + side * 0.18f;
            float wf = Mth.sin(wingAngle) * (length - arrow);
            float wy = centerY + Mth.cos(wingAngle) * (length - arrow);
            v.glowLink(wf, wy, -0.13f, f, y, -0.13f, half * 0.75f, rgb, tip, alpha);
        }
    }

    private static void clockGear(Voxels v, float cf, float cy, float l, float radius,
                                  int teeth, float rotation, int rgb, int alpha) {
        if (radius <= 0.001f) return;
        for (int part = 0; part < teeth; part++) {
            float angle = part * TAU / teeth + rotation;
            float next = (part + 1) * TAU / teeth + rotation;
            float f0 = cf + Mth.cos(angle) * radius, y0 = cy + Mth.sin(angle) * radius;
            float f1 = cf + Mth.cos(next) * radius, y1 = cy + Mth.sin(next) * radius;
            v.lightLink(f0, y0, l, f1, y1, l, 0.022f, rgb, IVORY, alpha);
            float toothF = cf + Mth.cos(angle) * (radius + 0.11f);
            float toothY = cy + Mth.sin(angle) * (radius + 0.11f);
            v.glowLink(f0, y0, l, toothF, toothY, l, 0.027f, rgb, WHITE, alpha);
            if (part % 4 == 0) {
                v.glowLink(cf, cy, l, f0, y0, l, 0.020f, TIME_DARK, rgb,
                        Math.round(alpha * 0.78f));
            }
        }
        v.glow(cf, cy, l, radius * 0.22f, rgb, Math.round(alpha * 0.82f));
    }

    /** Chiffres 3×5 orientés vers le spectateur, pour une horloge immédiatement reconnaissable. */
    private static void clockNumber(Voxels v, int number, float cf, float cy, float l,
                                    float pixel, int rgb, int alpha) {
        String text = Integer.toString(number);
        float width = (text.length() * 4 - 1) * pixel;
        for (int digit = 0; digit < text.length(); digit++) {
            int value = text.charAt(digit) - '0';
            for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) {
                if ((FONT_3X5[value][row] & (1 << (2 - col))) == 0) continue;
                float f = cf - width * 0.5f + (digit * 4 + col + 0.5f) * pixel;
                float y = cy + (2 - row) * pixel;
                v.glow(f, y, l, pixel * 0.42f, rgb, alpha);
            }
        }
    }

    private static final int[][] FONT_3X5 = {
            {7, 5, 5, 5, 7}, // 0
            {2, 6, 2, 2, 7}, // 1
            {7, 1, 7, 4, 7}, // 2
            {7, 1, 7, 1, 7}, // 3
            {5, 5, 7, 1, 1}, // 4
            {7, 4, 7, 1, 7}, // 5
            {7, 4, 7, 5, 7}, // 6
            {7, 1, 2, 2, 2}, // 7
            {7, 5, 7, 5, 7}, // 8
            {7, 5, 7, 1, 7}  // 9
    };

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
        final boolean facePlane;

        Voxels(VertexConsumer glow, VertexConsumer body, Matrix4f mat, Vector3f tmp,
               float cx, float cy, float cz, float dx, float dz, float time, boolean facePlane) {
            this.glow = glow; this.body = body; this.mat = mat; this.tmp = tmp;
            this.cx = cx; this.cy = cy; this.cz = cz; this.dx = dx; this.dz = dz; this.time = time;
            this.facePlane = facePlane;
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
            float sx = facePlane ? dz * df + dx * dl : dx * df - dz * dl;
            float sz = facePlane ? -dx * df + dz * dl : dz * df + dx * dl;
            float yaw = (float) Math.atan2(sx, sz);
            float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
            emit(vc, (f0 + f1) * 0.5f, (y0 + y1) * 0.5f, (l0 + l1) * 0.5f,
                    half, length * 0.50f, half, yaw, pitch, 0.0f,
                    WeaponWorldVfxManager.mixRgb(rgb, tip, 0.30f), alpha);
        }

        void emit(VertexConsumer vc, float f, float y, float l, float hf, float hy, float hl,
                  float yaw, float pitch, float roll, int rgb, int alpha) {
            if (alpha <= 5 || hf <= 0.0005f || hy <= 0.0005f || hl <= 0.0005f) return;
            float x = facePlane ? cx + dz * f + dx * l : cx + dx * f - dz * l;
            float z = facePlane ? cz - dx * f + dz * l : cz + dz * f + dx * l;
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
