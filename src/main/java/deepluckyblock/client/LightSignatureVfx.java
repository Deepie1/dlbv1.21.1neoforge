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

    /** Rayon extérieur synchronisé avec le serveur : fin, ouvert, puis fin avant extinction. */
    private static float seraphBeamRadius(float t) {
        float open = smooth((t - 0.12f) / 0.20f);
        float close = smooth((t - 0.74f) / 0.20f);
        return 0.10f + 0.68f * open * (1.0f - close);
    }

    /** SERAPH V3 — source céleste, cœur blanc/jaune, dilatation puis refermeture du rayon. */
    private static void renderSeraph(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.030f);
        float exit = 1.0f - smooth((t - 0.94f) / 0.055f);
        int a = Math.round(alpha * enter * exit);
        float drop = easeOut(t / 0.12f);
        float active = smooth((t - 0.105f) / 0.025f) * (1.0f - smooth((t - 0.90f) / 0.09f));
        float source = smooth(t / 0.075f) * (1.0f - smooth((t - 0.88f) / 0.11f));
        float radius = seraphBeamRadius(t);
        float headY = Mth.lerp(drop, 9.20f, -0.86f);

        // La source reprend le motif commun aux références : cercle céleste, étoile inscrite,
        // centre blanc. Deux couronnes contre-rotatives suffisent ; aucune gerbe volumétrique.
        for (int ring = 0; ring < 2; ring++) {
            float r = ring == 0 ? 1.42f : 0.96f;
            float spin = (ring == 0 ? 1.0f : -1.0f) * t * 0.85f;
            for (int part = 0; part < 32; part++) {
                float angle = part * TAU / 32.0f + spin;
                float next = (part + 1) * TAU / 32.0f + spin;
                v.glowLink(Mth.cos(angle) * r, 9.24f, Mth.sin(angle) * r,
                        Mth.cos(next) * r, 9.24f, Mth.sin(next) * r,
                        ring == 0 ? 0.018f : 0.013f,
                        ring == 0 ? GOLD : IVORY, WHITE, Math.round(a * source * 0.76f));
            }
        }
        for (int point = 0; point < 16; point++) {
            float a0 = point * TAU / 16.0f - t * 0.35f;
            float a1 = (point + 1) * TAU / 16.0f - t * 0.35f;
            float r0 = point % 2 == 0 ? 1.18f : 0.50f;
            float r1 = (point + 1) % 2 == 0 ? 1.18f : 0.50f;
            v.glowLink(Mth.cos(a0) * r0, 9.22f, Mth.sin(a0) * r0,
                    Mth.cos(a1) * r1, 9.22f, Mth.sin(a1) * r1,
                    0.016f, HONEY, WHITE, Math.round(a * source * 0.72f));
        }
        for (int rune = 0; rune < 8; rune++) {
            float angle = rune * TAU / 8.0f + t * 0.85f;
            v.outlineSquare(Mth.cos(angle) * 1.42f, 9.25f, Mth.sin(angle) * 1.42f,
                    0.12f, -angle, GOLD, Math.round(a * source * 0.66f));
        }
        astralShell(v, 0.0f, 9.22f, 0.0f, 0.18f + source * 0.06f,
                Math.round(a * source * 0.88f), t * 18.0f);

        if (active < 0.999f) {
            v.lightLink(0.0f, headY + 1.10f, 0.0f, 0.0f, headY, 0.0f,
                    0.035f, WHITE, IVORY, Math.round(a * (1.0f - active) * 0.90f));
        }
        if (active > 0.0f) {
            // Un seul prisme central et huit arêtes extérieures : pas de dizaines de couches
            // translucides superposées. Le coeur reste fin et blanc même à pleine ouverture.
            v.lightLink(0.0f, 9.20f, 0.0f, 0.0f, -0.86f, 0.0f,
                    0.035f + radius * 0.035f, WHITE, IVORY, Math.round(a * active));
            for (int strand = 0; strand < 3; strand++) {
                float angle = strand * TAU / 3.0f + t * 2.0f;
                float r = 0.055f + radius * 0.08f;
                v.glowLink(Mth.cos(angle) * r, 9.20f, Mth.sin(angle) * r,
                        Mth.cos(angle) * r, -0.86f, Mth.sin(angle) * r,
                        0.012f, strand == 1 ? WHITE : IVORY, WHITE,
                        Math.round(a * active * 0.82f));
            }
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f + t * 0.10f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                v.bodyLink(f, 9.20f, l, f, -0.86f, l,
                        0.040f, IVORY, Math.round(a * active * 0.24f));
                v.glowLink(f, 9.20f, l, f, -0.86f, l,
                        0.010f, side % 2 == 0 ? IVORY : HONEY, WHITE,
                        Math.round(a * active * 0.48f));
            }
            // Quatre jonctions seulement, pour lire l'ouverture sans créer un mur d'overdraw.
            for (int level = 0; level < 4; level++) {
                float y = -0.50f + level * 2.35f;
                for (int side = 0; side < 8; side++) {
                    float angle = side * TAU / 8.0f + t * 0.10f;
                    float next = (side + 1) * TAU / 8.0f + t * 0.10f;
                    v.glowLink(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                            Mth.cos(next) * radius, y, Mth.sin(next) * radius,
                            0.010f, HONEY, IVORY, Math.round(a * active * 0.30f));
                }
            }
            for (int packet = 0; packet < 8; packet++) {
                float travel = (t * 5.0f + packet / 8.0f) % 1.0f;
                float y = 9.05f - travel * 9.75f;
                float angle = packet * 2.3999632f;
                float r = radius * (0.25f + (packet % 3) * 0.18f);
                v.glow(Mth.cos(angle) * r, y, Mth.sin(angle) * r,
                        packet % 3 == 0 ? 0.035f : 0.022f,
                        packet % 2 == 0 ? WHITE : IVORY, Math.round(a * active * 0.72f));
            }
            // Terminal discret : douze filets bas et huit motes, jamais une seconde zone.
            for (int ray = 0; ray < 12; ray++) {
                float angle = ray * TAU / 12.0f;
                float outer = radius + 0.22f + (ray % 3) * 0.10f;
                v.glowLink(Mth.cos(angle) * radius * 0.65f, -0.82f, Mth.sin(angle) * radius * 0.65f,
                        Mth.cos(angle) * outer, -0.79f, Mth.sin(angle) * outer,
                        0.010f, HONEY, IVORY, Math.round(a * active * 0.28f));
            }
            for (int mote = 0; mote < 8; mote++) {
                float h = hash(salt, 2100 + mote * 31);
                float angle = mote * 2.3999632f + t;
                float r = radius + 0.15f + h * 0.30f;
                v.glow(Mth.cos(angle) * r, -0.65f + h * 0.30f, Mth.sin(angle) * r,
                        0.014f + h * 0.008f, mote % 3 == 0 ? WHITE : HONEY,
                        Math.round(a * active * 0.34f));
            }
        }
    }

    /** Trois vraies constellations : leurs positions normalisées et leurs liaisons sont stables. */
    private static final float[][] URSA_MAJOR = {
            {0, 0}, {0, .75f}, {.75f, .90f}, {.85f, .15f},
            {1.50f, -.05f}, {2.10f, -.35f}, {2.75f, -.15f}
    };
    private static final int[] URSA_LINKS = {0, 1, 1, 2, 2, 3, 3, 0, 3, 4, 4, 5, 5, 6};
    private static final float[][] CASSIOPEIA = {{0, 0}, {.60f, .45f}, {1.20f, -.05f}, {1.80f, .55f}, {2.40f, .10f}};
    private static final int[] CASSIOPEIA_LINKS = {0, 1, 1, 2, 2, 3, 3, 4};
    private static final float[][] ORION = {
            {-.50f, .80f}, {.50f, .75f}, {-.30f, .10f}, {0, 0}, {.30f, -.10f}, {-.55f, -.80f}, {.55f, -.75f}
    };
    private static final int[] ORION_LINKS = {0, 1, 0, 2, 1, 4, 2, 3, 3, 4, 2, 5, 4, 6, 5, 6};

    private static final float[][] CONSTELLATION_SOURCES = {
            {-3.39f, 1.45f, -0.65f}, {-1.63f, 2.27f, 0.55f}, {-4.25f, 0.70f, 0.70f}
    };

    /** ASTRAL V3 — silhouette sacrée épurée, chargée par de vraies constellations. */
    private static void renderAstral(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.045f), exit = 1.0f - smooth((t - 0.76f) / 0.22f);
        int a = Math.round(alpha * enter * exit);
        float forge = easeInOut(t / 0.17f);
        float travel = easeInOut((t - 0.07f) / 0.23f);
        float after = easeOut((t - 0.30f) / 0.23f);
        float head = -4.35f + travel * 4.60f + after * 4.35f;
        float impact = 1.0f - smooth(Math.abs(t - 0.30f) / 0.095f);
        float constellationAlpha = 1.0f - smooth((t - 0.16f) / 0.18f);
        float charge = smooth((t - 0.07f) / 0.13f) * (1.0f - smooth((t - 0.22f) / 0.10f));

        // Grande Ourse, Cassiopée et Orion ne sont pas des amas aléatoires : leur dessin reste
        // identifiable, puis trois canaux convergent vers la lame pour la charger.
        drawConstellation(v, URSA_MAJOR, URSA_LINKS, -4.90f, 1.55f, -0.65f,
                0.55f, 0.65f, constellationAlpha, a, t);
        drawConstellation(v, CASSIOPEIA, CASSIOPEIA_LINKS, -2.95f, 2.20f, 0.55f,
                0.55f, 0.65f, constellationAlpha, a, t + 1.0f);
        drawConstellation(v, ORION, ORION_LINKS, -4.25f, 0.70f, 0.70f,
                0.75f, 0.65f, constellationAlpha, a, t + 2.0f);
        for (int beam = 0; beam < 3; beam++) {
            float[] source = CONSTELLATION_SOURCES[beam];
            v.glowLink(source[0], source[1], source[2], head - 0.55f, 0.0f, 0.0f,
                    0.012f, beam == 1 ? LILAC : CYAN, WHITE, Math.round(a * charge * 0.68f));
            for (int packet = 0; packet < 2; packet++) {
                float u = (t * 5.0f + beam * 0.23f + packet * 0.5f) % 1.0f;
                v.glow(Mth.lerp(u, source[0], head - 0.55f), Mth.lerp(u, source[1], 0.0f),
                        Mth.lerp(u, source[2], 0.0f), 0.030f,
                        beam == 1 ? LILAC : ICE, Math.round(a * charge * 0.86f));
            }
        }

        float lock = smooth(t / 0.20f) * (1.0f - smooth((t - 0.31f) / 0.13f));
        for (int corner = 0; corner < 4; corner++) {
            float sx = (corner & 1) == 0 ? -1.0f : 1.0f, sy = (corner & 2) == 0 ? -1.0f : 1.0f;
            float r = 0.82f - lock * 0.28f, f = sx * r;
            v.glowLink(f, sy * r, -0.12f, f - sx * 0.25f, sy * r, -0.12f,
                    0.018f, CYAN, WHITE, Math.round(a * lock));
            v.glowLink(f, sy * r, -0.12f, f, sy * r - sy * 0.25f, -0.12f,
                    0.018f, CYAN, WHITE, Math.round(a * lock));
        }

        float lanceAlpha = 1.0f - smooth((t - 0.58f) / 0.25f);
        // Fût quadrangulaire : vingt sections, quatre rails continus et gravures espacées.
        for (int segment = 0; segment < 20; segment++) {
            float u = segment / 19.0f, f = head - 3.05f + u * 2.50f;
            float radius = 0.10f + 0.035f * Mth.sin(u * (float) Math.PI);
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + (float) Math.PI / 4.0f;
                float next = (side + 1) * TAU / 4.0f + (float) Math.PI / 4.0f;
                v.bodyLink(f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        f, Mth.cos(next) * radius, Mth.sin(next) * radius,
                        0.035f, side % 2 == 0 ? CYAN : VIOLET, Math.round(a * lanceAlpha * 0.36f));
                if (segment % 2 == 0) {
                    v.glowLink(f - 0.055f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                            f + 0.055f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                            0.014f, side % 2 == 0 ? ICE : LILAC, WHITE, Math.round(a * lanceAlpha * 0.80f));
                }
            }
            v.glow(f, 0.0f, 0.0f, 0.026f, segment % 5 == 0 ? WHITE : CYAN,
                    Math.round(a * lanceAlpha * 0.90f));
        }
        for (int rail = 0; rail < 4; rail++) {
            float angle = rail * TAU / 4.0f + (float) Math.PI / 4.0f;
            v.glowLink(head - 3.05f, Mth.cos(angle) * 0.10f, Mth.sin(angle) * 0.10f,
                    head - 0.55f, Mth.cos(angle) * 0.10f, Mth.sin(angle) * 0.10f,
                    0.014f, rail % 2 == 0 ? GOLD : CYAN, WHITE, Math.round(a * lanceAlpha * 0.76f));
        }
        // Lame sacrée en losange allongé : sept sections et quatre arêtes continues, pas une
        // accumulation de petits cubes. Les quatre arêtes blanches décrivent clairement la pointe.
        for (int section = 0; section < 7; section++) {
            float u = section / 6.0f, f = head - 0.55f + u * 0.85f;
            float radius = 0.34f * (1.0f - u) + 0.012f;
            for (int side = 0; side < 4; side++) {
                float angle = side * TAU / 4.0f + (float) Math.PI / 4.0f;
                float y = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                v.rot(f, y, l, 0.050f, 0.025f, 0.025f, 0.0f, 0.0f, angle,
                        side % 2 == 0 ? ICE : LILAC, Math.round(a * lanceAlpha * 0.88f));
                if (section < 6) {
                    float nextU = (section + 1) / 6.0f;
                    float nextR = 0.34f * (1.0f - nextU) + 0.012f;
                    v.glowLink(f, y, l, f + 0.85f / 6.0f,
                            Mth.cos(angle) * nextR, Mth.sin(angle) * nextR,
                            0.013f, side % 2 == 0 ? WHITE : CYAN, WHITE, Math.round(a * lanceAlpha));
                }
            }
        }
        // Croisée d'or et quatre ailettes angulaires : la silhouette reste lisible de profil.
        for (int wing = 0; wing < 4; wing++) {
            float angle = wing * TAU / 4.0f;
            float y0 = Mth.cos(angle) * 0.13f, l0 = Mth.sin(angle) * 0.13f;
            float y1 = Mth.cos(angle) * 0.50f, l1 = Mth.sin(angle) * 0.50f;
            v.glowLink(head - 0.68f, y0, l0, head - 0.78f, y1, l1,
                    0.022f, GOLD, IVORY, Math.round(a * lanceAlpha));
            v.glowLink(head - 0.78f, y1, l1, head - 0.48f, y1 * 0.72f, l1 * 0.72f,
                    0.020f, GOLD, WHITE, Math.round(a * lanceAlpha));
            v.glowLink(head - 0.48f, y1 * 0.72f, l1 * 0.72f, head - 0.38f, y0, l0,
                    0.016f, HONEY, WHITE, Math.round(a * lanceAlpha));
        }
        for (int rune = 0; rune < 6; rune++) {
            float f = head - 2.75f + rune * 0.34f;
            float y = rune % 2 == 0 ? 0.16f : -0.16f;
            v.glowLink(f - 0.07f, y, 0.0f, f, y + 0.07f, 0.0f,
                    0.010f, GOLD, WHITE, Math.round(a * lanceAlpha * 0.76f));
            v.glowLink(f, y + 0.07f, 0.0f, f + 0.07f, y, 0.0f,
                    0.010f, GOLD, WHITE, Math.round(a * lanceAlpha * 0.76f));
            v.glowLink(f + 0.07f, y, 0.0f, f, y - 0.07f, 0.0f,
                    0.010f, GOLD, WHITE, Math.round(a * lanceAlpha * 0.76f));
            v.glowLink(f, y - 0.07f, 0.0f, f - 0.07f, y, 0.0f,
                    0.010f, GOLD, WHITE, Math.round(a * lanceAlpha * 0.76f));
        }
        for (int strand = 0; strand < 3; strand++) for (int i = 0; i < 24; i++) {
            float u = i / 23.0f, angle = u * TAU * 1.6f + strand * TAU / 3.0f - t * 8.0f;
            float f = head - 3.05f + u * 3.05f;
            v.glow(f, Mth.cos(angle) * 0.19f, Mth.sin(angle) * 0.19f,
                    i % 6 == 0 ? 0.030f : 0.016f,
                    strand == 0 ? CYAN : strand == 1 ? LILAC : WHITE,
                    Math.round(a * lanceAlpha * (0.42f + 0.40f * u)));
        }
        astralShell(v, head - 0.57f, 0.0f, 0.0f, 0.20f,
                Math.round(a * lanceAlpha * 0.84f), t * 24.0f);

        // Sillage court : vingt étoiles, pas une seconde masse de géométrie autour de la lame.
        float wake = smooth((t - 0.13f) / 0.17f) * (1.0f - smooth((t - 0.53f) / 0.28f));
        for (int trail = 0; trail < 20; trail++) {
            float u = trail / 19.0f, h = hash(salt, 540 + trail * 29);
            float angle = trail * 2.3999632f - t * 3.0f;
            float r = (0.10f + u * 0.30f) * (0.65f + h * 0.35f);
            v.rot(head - 0.40f - u * 2.70f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.025f + h * 0.012f, 0.012f, 0.050f,
                    angle, t * 2.0f, h, trail % 4 == 0 ? WHITE : trail % 2 == 0 ? CYAN : LILAC,
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
                        ray % 2 == 0 ? 0.022f : 0.013f,
                        ray % 3 == 0 ? LILAC : ICE, WHITE, Math.round(a * impact));
            }
            // Reflet au sol : interaction avec le décor, sans prétendre agrandir la collision.
            for (int part = 0; part < 40; part++) {
                float angle = part * TAU / 40.0f, next = (part + 1) * TAU / 40.0f;
                float r = 0.35f + impact * 1.25f;
                v.glowLink(Mth.cos(angle) * r, -0.86f, Mth.sin(angle) * r,
                        Mth.cos(next) * r, -0.86f, Mth.sin(next) * r,
                        0.012f, LILAC, ICE, Math.round(a * impact * 0.52f));
            }
            astralShell(v, 0.12f, 0.0f, 0.0f, 0.23f + impact * 0.10f,
                    Math.round(a * impact * 0.82f), t * 30.0f);
        }
        for (int shard = 0; shard < 18; shard++) {
            float age = clamp((t - 0.30f - (shard % 3) * 0.006f) / 0.37f);
            if (age <= 0.0f || age >= 1.0f) continue;
            float h = hash(salt, 700 + shard * 43), angle = shard * 2.3999632f;
            float r = age * (0.30f + h * 1.45f);
            v.rot(age * 2.1f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                    0.045f, 0.018f + h * 0.015f, 0.014f,
                    angle, age * 4.0f, h * 3.0f, shard % 3 == 0 ? LILAC : ICE,
                    Math.round(a * (1.0f - age) * 0.86f));
        }
    }

    private static void drawConstellation(Voxels v, float[][] stars, int[] links,
                                          float baseF, float baseY, float l, float scaleF, float scaleY,
                                          float visibility, int alpha, float phase) {
        for (int i = 0; i < stars.length; i++) {
            float f = baseF + stars[i][0] * scaleF, y = baseY + stars[i][1] * scaleY;
            float pulse = 0.65f + 0.35f * Mth.sin(phase * 12.0f + i * 1.7f);
            v.glow(f, y, l, i % 3 == 0 ? 0.060f : 0.042f,
                    i % 2 == 0 ? WHITE : ICE, Math.round(alpha * visibility * pulse));
            // Quatre rayons seulement sur les étoiles principales : leur nom et leur dessin
            // restent lisibles sans douze arêtes de cube par étoile.
            if (i % 3 == 0) {
                v.glowLink(f - 0.12f, y, l, f + 0.12f, y, l,
                        0.010f, CYAN, WHITE, Math.round(alpha * visibility * 0.70f));
                v.glowLink(f, y - 0.12f, l, f, y + 0.12f, l,
                        0.010f, CYAN, WHITE, Math.round(alpha * visibility * 0.70f));
            }
        }
        for (int i = 0; i < links.length; i += 2) {
            float[] s0 = stars[links[i]], s1 = stars[links[i + 1]];
            v.glowLink(baseF + s0[0] * scaleF, baseY + s0[1] * scaleY, l,
                    baseF + s1[0] * scaleF, baseY + s1[1] * scaleY, l,
                    0.010f, BLUE, ICE, Math.round(alpha * visibility * 0.60f));
        }
    }

    /** CHRONO V2 — une vraie horloge frontale, ses engrenages et son pendule. */
    private static void renderChrono(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.055f);
        float exit = 1.0f - smooth((t - 0.82f) / 0.16f);
        int a = Math.round(alpha * enter * exit);
        float assemble = easeOut(t / 0.16f);
        float close = easeInOut((t - 0.34f) / 0.21f);
        float release = smooth((t - 0.55f) / 0.25f);
        float strike = 1.0f - smooth(Math.abs(t - 0.55f) / 0.070f);
        float handSpin = chronometerAngle(t);
        float centerY = 0.35f;
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
