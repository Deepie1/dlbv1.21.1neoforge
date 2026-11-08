package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Terre active : Seismic, Titan's Grasp et Rally. Sandstorm est une archive Wind inactive. */
public final class EarthSignatureVfx {
    public static final int SEISMIC = 0;
    public static final int TITAN = 1;
    public static final int RALLY = 2;
    public static final int SANDSTORM = 3; // ARCHIVE WIND — conserver pour la refonte ultérieure.

    private static final int FAULT = 0x160F0B;
    private static final int SOIL_DARK = 0x3B2616;
    private static final int SOIL = 0x5C3B1A;
    private static final int CLAY = 0x82552B;
    private static final int STONE_DARK = 0x292A25;
    private static final int STONE = 0x4B4D40;
    private static final int STONE_LIGHT = 0x777B61;
    private static final int SAND_DARK = 0x745027;
    private static final int SAND = 0xB78945;
    private static final int SAND_LIGHT = 0xDDBE78;
    private static final int DUST = 0xEAD7A4;
    private static final int EARTH = 0x9FC25E;
    private static final int EARTH_LIGHT = 0xD6E39B;
    private static final int GOLD = 0xD9A83E;
    private static final int BANNER = 0x43502A;

    private static final int CACHE_LIMIT = 1536;
    private static final Map<Long, Float> GROUND_CACHE = new HashMap<>(CACHE_LIMIT);
    private static final Map<Long, Boolean> SOLID_CACHE = new HashMap<>(CACHE_LIMIT);
    private static ClientLevel terrainLevel;

    private EarthSignatureVfx() { }

    public static void beginTerrainTick(ClientLevel level) {
        if (level == null) return;
        ensureLevel(level);
        GROUND_CACHE.clear();
        SOLID_CACHE.clear();
    }

    /** Préchauffe le disque utile hors de la passe de rendu. */
    public static void prepareTerrain(ClientLevel level, double x, double y, double z, float worldScale) {
        if (level == null) return;
        ensureLevel(level);
        worldScale = Math.max(0.05f, worldScale);
        for (int ix = -8; ix <= 8; ix++) for (int iz = -8; iz <= 8; iz++) {
            sampleGround(level, x + ix * 0.5 * worldScale, y, z + iz * 0.5 * worldScale);
        }
    }

    public static void render(ClientLevel level, double worldX, double worldY, double worldZ,
                              VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant,
                              float worldScale, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001f) { dirX = 0.0f; dirZ = 1.0f; }
        else { dirX /= length; dirZ /= length; }
        TerrainView terrain = new TerrainView(level, worldX, worldY, worldZ, cy, dirX, dirZ,
                Math.max(0.05f, worldScale));
        if (variant == SEISMIC) {
            // Le sol réel est capturé/reprojeté par SeismicTerrainVfx dans une passe opaque dédiée.
            // Aucune particule ou roche générique ne doit être ajoutée ici.
            return;
        } else if (variant == TITAN) {
            renderTitanGrasp(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        } else if (variant == SANDSTORM) {
            // Archive Wind : volontairement hors de la rotation Terre, code conservé pour plus tard.
            renderSandstorm(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        } else {
            renderRally(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        }
    }

    /** TITAN : proportions étudiées d'après la référence Stone Golem Hand (Free3D #6418), retranscrites en voxels. */
    private static void renderTitanGrasp(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float t,
                                         float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float rise = smooth(t / 0.22f);
        float close = smooth((t - 0.25f) / 0.18f);
        float pull = smooth((t - 0.43f) / 0.37f);
        float crumble = smooth((t - 0.86f) / 0.14f);
        float env = rise * (1.0f - crumble);
        if (env <= 0.0f) return;
        int a = Math.round(alpha * env);
        float ground = terrain.at(0.0f, 0.0f);
        // La paume reste derrière le mob (f = 0,85) ; les doigts se referment vers f = 0.
        float base = ground - 2.40f + rise * 2.40f - pull * 3.0f;
        float sizeFade = 1.0f - crumble * 0.30f;
        float originF = 0.85f;

        // Avant-bras large et plein : huit sections qui se chevauchent, sans cavités internes.
        for (int layer = 0; layer < 8; layer++) for (int ix = -3; ix <= 3; ix++) for (int iz = -3; iz <= 3; iz++) {
            if (ix * ix + iz * iz > 10) continue;
            float h = hash(salt, 4100 + layer * 97 + ix * 31 + iz * 17);
            float scatter = crumble * crumble;
            float f = originF + ix * 0.22f;
            float y = base - 1.50f + layer * 0.20f;
            box(vc, mat, tmp, cx, y - scatter * (0.25f + h * 0.45f), cz, dx, dz,
                    f + (h - 0.5f) * scatter * 0.55f, iz * 0.22f + (0.5f - h) * scatter * 0.45f,
                    0.135f * sizeFade, 0.125f * sizeFade, 0.135f * sizeFade,
                    color(STONE_DARK, STONE_LIGHT, 0.42f + layer * 0.035f + h * 0.24f), a);
        }

        // Paume verticale pleine : trois couches d'épaisseur, sept de haut et neuf de large.
        for (int layer = 0; layer < 7; layer++) for (int ix = -1; ix <= 1; ix++) for (int iz = -4; iz <= 4; iz++) {
            if (Math.abs(iz) == 4 && (layer == 0 || layer == 6)) continue;
            float h = hash(salt, 4400 + layer * 101 + ix * 37 + iz * 19);
            float scatter = crumble * crumble;
            box(vc, mat, tmp, cx, base + layer * 0.21f - scatter * h * 0.40f, cz, dx, dz,
                    originF + ix * 0.20f + (h - 0.5f) * scatter * 0.60f,
                    iz * 0.22f + (0.5f - h) * scatter * 0.50f,
                    0.130f * sizeFade, 0.125f * sizeFade, 0.130f * sizeFade,
                    color(STONE_DARK, STONE_LIGHT, 0.52f + layer * 0.035f + h * 0.20f), a);
        }

        // Quatre doigts massifs : chaque phalange possède un corps et deux joues latérales.
        float[] lengths = {0.50f, 0.55f, 0.52f, 0.44f};
        for (int finger = 0; finger < 4; finger++) {
            float lateral = (finger - 1.5f) * 0.50f;
            float f = 0.94f, y = base + 1.38f, l = lateral;
            float lengthScale = lengths[finger] / 0.50f;
            int rgb = color(STONE_DARK, STONE_LIGHT, 0.68f + finger * 0.045f);
            for (int segment = 0; segment < 3; segment++) {
                float theta = 0.08f + segment * 0.10f + close * (0.85f + segment * 0.75f);
                float length = (0.50f - segment * 0.06f) * lengthScale;
                float nextF = f - Mth.sin(theta) * length;
                float nextY = y + Mth.cos(theta) * length;
                float half = (0.170f - finger * 0.007f - segment * 0.015f) * sizeFade;
                golemSegment(vc, mat, tmp, cx, cz, dx, dz, f, y, l, nextF, nextY, l, half, rgb, a);
                f = nextF;
                y = nextY;
            }
            // Capuchon terminal large et plat : pas un ongle pointu humain.
            for (int side = -1; side <= 1; side++) {
                box(vc, mat, tmp, cx, y + 0.015f, cz, dx, dz, f - 0.035f, l + side * 0.085f,
                        0.065f * sizeFade, 0.045f * sizeFade, 0.070f * sizeFade, STONE_LIGHT, a);
            }
        }

        // Pouce opposable à trois phalanges, assez long pour verrouiller la prise sur le côté.
        float tf = 0.82f, ty = base + 0.52f, tl = -0.94f;
        float mf = 0.50f - close * 0.05f, my = base + 0.82f, ml = -0.78f + close * 0.04f;
        float ef = 0.12f - close * 0.04f, ey = base + 1.00f - close * 0.08f, el = -0.55f + close * 0.16f;
        float xf = -0.12f, xy = base + 0.88f - close * 0.14f, xl = -0.25f + close * 0.10f;
        golemSegment(vc, mat, tmp, cx, cz, dx, dz, tf, ty, tl, mf, my, ml,
                0.180f * sizeFade, STONE, a);
        golemSegment(vc, mat, tmp, cx, cz, dx, dz, mf, my, ml, ef, ey, el,
                0.165f * sizeFade, STONE, a);
        golemSegment(vc, mat, tmp, cx, cz, dx, dz, ef, ey, el, xf, xy, xl,
                0.145f * sizeFade, STONE_LIGHT, a);

        // Plaques minérales, fissures et mousse sont posées sur la face de la paume, jamais en lévitation.
        for (int detail = 0; detail < 24; detail++) {
            int row = detail / 6, col = detail % 6;
            float h = hash(salt, 4650 + detail * 41);
            float f = 1.185f;
            float l = -0.55f + col * 0.22f;
            float y = base + 0.18f + row * 0.27f;
            float size = 0.025f + h * 0.035f;
            box(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    0.018f, size * (detail % 3 == 0 ? 1.8f : 0.8f), size,
                    detail % 5 == 0 ? EARTH : detail % 3 == 0 ? SOIL_DARK : STONE_LIGHT,
                    Math.round(a * 0.88f));
        }
        // Gros éclats de surface sur l'avant-bras, raccordés à sa face frontale.
        for (int plate = 0; plate < 24; plate++) {
            int layer = plate / 4, col = plate % 4;
            float h = hash(salt, 4720 + plate * 43);
            box(vc, mat, tmp, cx, base - 1.38f + layer * 0.22f, cz, dx, dz,
                    1.64f, -0.33f + col * 0.22f,
                    0.035f, 0.075f + h * 0.035f, 0.085f,
                    plate % 6 == 0 ? EARTH : color(STONE, STONE_LIGHT, h), Math.round(a * 0.90f));
        }

        // Éclats lourds à l'émergence, à l'enfouissement et à l'effritement.
        for (int chip = 0; chip < 96; chip++) {
            float h = hash(salt, 4800 + chip * 43);
            float angle = chip * 2.3999632f;
            float radius = 0.75f + h * 1.45f + pull * 0.35f;
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float arc = Mth.sin(clamp(t * 2.2f + h * 0.35f) * 3.1415927f);
            float y = terrain.at(f, l) + 0.05f + arc * (0.25f + h * 0.80f) - pull * 0.35f;
            float size = (0.030f + h * 0.055f) * (1.0f - crumble * 0.45f);
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size, size * 0.75f, size * 1.15f,
                    angle + t * 2.0f, h * 1.2f, t * 1.4f,
                    chip % 7 == 0 ? EARTH : STONE, Math.round(a * (0.35f + h * 0.45f)));
        }
    }

    /** Phalange de golem : trois volumes longitudinaux et trois volumes d'articulation jointifs. */
    private static void golemSegment(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cz, float dx, float dz,
                                     float f0, float y0, float l0, float f1, float y1, float l1,
                                     float half, int rgb, int alpha) {
        for (int side = -1; side <= 1; side++) {
            float offset = side * half * 0.62f;
            link(vc, mat, tmp, cx, (y0 + y1) * 0.5f, cz, dx, dz,
                    f0, y0, l0 + offset, f1, y1, l1 + offset,
                    half * (side == 0 ? 1.0f : 0.64f), rgb, STONE_LIGHT, alpha);
        }
        cube(vc, mat, tmp, cx, y1, cz, dx, dz, f1, l1, half * 1.08f, rgb, alpha);
        for (int side = -1; side <= 1; side += 2) {
            cube(vc, mat, tmp, cx, y1, cz, dx, dz, f1, l1 + side * half * 0.62f,
                    half * 0.58f, color(STONE, STONE_LIGHT, 0.35f), alpha);
        }
    }

    /** ARCHIVE WIND — SANDSTORM : dune migratrice, vortex et grains, reprise ultérieurement. */
    private static void renderSandstorm(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float birth = smooth(t / 0.075f);
        float exit = smooth((t - 0.80f) / 0.20f);
        float env = birth * (1.0f - exit);
        int a = Math.round(alpha * env);
        float duneGrow = smooth((t - 0.035f) / 0.27f) * (1.0f - smooth((t - 0.70f) / 0.24f));
        float duneCenter = -0.35f + smooth((t - 0.08f) / 0.60f) * 0.95f;

        // Corps de la dune : strates elliptiques et crête asymétrique, jamais un simple cône.
        for (int level = 0; level < 6; level++) {
            float u = level / 5.0f;
            float radius = (1.30f - u * 0.90f) * duneGrow;
            if (radius <= 0.01f) continue;
            for (int i = 0; i < 30; i++) {
                float angle = i * (float) (Math.PI * 2.0 / 30.0) + level * 0.19f;
                float h = hash(salt, 1000 + level * 67 + i * 17);
                float f = duneCenter + Mth.cos(angle) * radius * (1.0f + 0.22f * u);
                float l = Mth.sin(angle) * radius * 0.68f;
                float crest = Math.max(0.0f, Mth.cos(angle - 0.55f)) * 0.18f * duneGrow;
                float y = terrain.at(f, l) + 0.10f + u * 0.23f * duneGrow + crest;
                float size = (0.15f - u * 0.045f) * (0.88f + h * 0.24f);
                rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                        size * 1.18f, size * 0.64f, size,
                        angle + t * 0.35f, h * 0.25f, 0.0f,
                        color(SAND_DARK, SAND_LIGHT, 0.22f + 0.55f * u + h * 0.15f),
                        Math.round(a * duneGrow * (0.52f + 0.30f * h)));
            }
        }

        // Nappes de sable qui glissent sur le sol dans la direction du vent.
        for (int i = 0; i < 72; i++) {
            float h = hash(salt, 1300 + i * 31);
            float cycle = fract(t * (0.75f + h * 0.55f) + i * 0.381966f);
            float angle = i * 2.3999632f;
            float radius = 0.45f + cycle * 2.15f;
            float f = Mth.cos(angle) * radius + cycle * 0.65f;
            float l = Mth.sin(angle) * radius * 0.74f;
            float y = terrain.at(f, l) + 0.035f + 0.05f * Mth.sin(cycle * (float) Math.PI);
            float size = 0.035f + 0.035f * h;
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    size * 1.9f, 0.012f, size * 0.65f, angle, 0.0f, 0.0f,
                i % 6 == 0 ? DUST : SAND, Math.round(a * (1.0f - cycle) * 0.54f));
        }

        // Trois bras denses montent en spiralant ; leur rayon se rétrécit au sommet.
        for (int arm = 0; arm < 3; arm++) for (int level = 0; level < 14; level++) {
            float u = level / 13.0f;
            float reveal = smooth((t - 0.07f - u * 0.14f) / 0.18f);
            if (reveal <= 0.0f) continue;
            float angle = arm * 2.0943951f + u * 7.4f + t * 15.0f;
            float radius = 0.92f - u * 0.52f + 0.10f * Mth.sin(t * 9.0f + u * 11.0f);
            float f = duneCenter * 0.35f + Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = terrain.at(f, l) + 0.22f + u * 2.05f;
            float size = 0.23f - u * 0.075f;
            billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l, size, 1.10f,
                    t * 9.0f + arm + u * 3.0f, salt + 1700 + arm * 211 + level * 37,
                    SAND_DARK, level % 4 == 0 ? DUST : SAND_LIGHT,
                    Math.round(a * reveal * (0.38f + 0.28f * (1.0f - u))), 1, terrain);
        }

        // Rubans de vent horizontaux qui ceinturent le vortex sans devenir un anneau fixe.
        for (int band = 0; band < 4; band++) for (int i = 0; i < 34; i++) {
            float u = i / 33.0f;
            float angle = u * 4.8f + band * 1.57f + t * (10.0f + band);
            float radius = 1.05f + band * 0.17f + 0.12f * Mth.sin(u * 9.0f + t * 7.0f);
            float f = Mth.cos(angle) * radius + duneCenter * 0.20f;
            float l = Mth.sin(angle) * radius;
            float y = terrain.at(f, l) + 0.32f + band * 0.36f + u * 0.32f;
            float size = 0.022f + 0.012f * hash(salt, 2100 + band * 83 + i);
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    size * 2.4f, size * 0.45f, size * 0.65f,
                    angle, 0.18f, 0.0f, band % 2 == 0 ? DUST : SAND_LIGHT,
                    Math.round(a * 0.46f));
        }

        // Grains individuels : naissance basse, advection hélicoïdale, puis dispersion et extinction.
        for (int i = 0; i < 180; i++) {
            float h = hash(salt, 2500 + i * 43);
            float cycle = fract(t * (1.45f + h * 1.25f) + i * 0.381966f + h * 3.0f);
            float angle = i * 2.3999632f + cycle * 8.5f + t * 9.0f;
            float radius = 0.30f + cycle * (0.75f + h * 0.75f);
            float f = Mth.cos(angle) * radius + cycle * cycle * 0.55f;
            float l = Mth.sin(angle) * radius;
            float y = terrain.at(f, l) + 0.12f + cycle * (1.35f + h * 1.10f);
            float fade = smooth(cycle / 0.08f) * (1.0f - smooth((cycle - 0.72f) / 0.28f));
            float size = 0.010f + 0.018f * h;
            cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size,
                    i % 9 == 0 ? DUST : SAND_LIGHT, Math.round(a * fade * 0.76f));
        }

        // Quelques graviers lourds retombent au lieu de suivre le vent.
        for (int p = 0; p < 12; p++) {
            float age = t - 0.20f - (p % 4) * 0.035f;
            if (age <= 0.0f) continue;
            float h = hash(salt, 3000 + p * 47);
            float time = age * 4.8f;
            float angle = p * 2.3999632f;
            float f = Mth.cos(angle) * time * (0.25f + h * 0.45f);
            float l = Mth.sin(angle) * time * (0.25f + h * 0.45f);
            float y = terrain.at(f, l) + 0.10f + (0.65f + h) * time - 2.8f * time * time;
            if (y < terrain.at(f, l)) continue;
            cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, 0.026f + h * 0.022f,
                    SAND_DARK, Math.round(a * 0.72f));
        }
    }

    /** RALLY : sceau fermé, quatre étendards manufacturés et glaive de commandement complet. */
    private static void renderRally(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float birth = smooth(t / 0.065f);
        float exit = smooth((t - 0.83f) / 0.17f);
        float env = birth * (1.0f - exit);
        int a = Math.round(alpha * env);
        float circle = smooth((t - 0.025f) / 0.24f);
        float radius = 0.35f + 1.45f * circle;

        // Deux cercles jointifs et CONTINUS : plus aucun secteur vide ne dessine un faux trou.
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < 96; i++) {
            float angle = i * (float) (Math.PI * 2.0 / 96.0) + ring * 0.025f;
            float r = radius - ring * 0.17f;
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = terrain.at(f, l) + 0.030f;
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    0.040f, 0.018f, 0.070f, angle, 0.0f, 0.0f,
                    ring == 0 ? GOLD : EARTH, Math.round(a * circle * 0.84f));
        }

        // Huit glyphes fermés et leur rayonnement intérieur, tous ancrés sur le collider du sol.
        for (int rune = 0; rune < 8; rune++) {
            float reveal = smooth((t - 0.10f - rune * 0.014f) / 0.16f);
            if (reveal <= 0.0f) continue;
            float angle = rune * (float) (Math.PI / 4.0);
            float f = Mth.cos(angle) * 1.25f, l = Mth.sin(angle) * 1.25f;
            groundRune(vc, mat, tmp, cx, cz, dx, dz, f, l, angle + t * 0.20f,
                    0.16f * reveal, rune % 2 == 0 ? EARTH_LIGHT : GOLD,
                    Math.round(a * reveal), terrain);
            groundLink(vc, mat, tmp, cx, cz, dx, dz,
                    Mth.cos(angle) * 0.88f, Mth.sin(angle) * 0.88f,
                    Mth.cos(angle) * 1.08f, Mth.sin(angle) * 1.08f,
                    0.018f, EARTH, Math.round(a * reveal * 0.72f), terrain);
        }

        // Emblème central : deux glaives croisés dans un cadre carré entièrement fermé.
        float emblem = smooth((t - 0.13f) / 0.20f);
        for (int sign = -1; sign <= 1; sign += 2) {
            groundLink(vc, mat, tmp, cx, cz, dx, dz, -0.55f, sign * -0.55f,
                    0.55f, sign * 0.55f, 0.025f, GOLD, Math.round(a * emblem), terrain);
            groundLink(vc, mat, tmp, cx, cz, dx, dz, -0.72f, sign * -0.72f,
                    -0.45f, sign * -0.45f, 0.045f, EARTH_LIGHT, Math.round(a * emblem), terrain);
        }
        for (int side = 0; side < 4; side++) {
            float angle = side * 1.5707963f + 0.7853982f;
            float f0 = Mth.cos(angle) * 0.83f, l0 = Mth.sin(angle) * 0.83f;
            float f1 = Mth.cos(angle + 1.5707963f) * 0.83f, l1 = Mth.sin(angle + 1.5707963f) * 0.83f;
            groundLink(vc, mat, tmp, cx, cz, dx, dz, f0, l0, f1, l1,
                    0.018f, EARTH, Math.round(a * emblem * 0.72f), terrain);
        }

        // Quatre étendards complets : socle, mât, barre, finial, tissu, emblème et franges.
        for (int banner = 0; banner < 4; banner++) {
            float reveal = smooth((t - 0.17f - banner * 0.035f) / 0.22f);
            if (reveal <= 0.0f) continue;
            float angle = banner * 1.5707963f;
            float tangentF = Mth.cos(angle + 1.5707963f), tangentL = Mth.sin(angle + 1.5707963f);
            float radialF = Math.abs(Mth.cos(angle)), radialL = Math.abs(Mth.sin(angle));
            float f = Mth.cos(angle) * 1.45f, l = Mth.sin(angle) * 1.45f;
            float ground = terrain.at(f, l);
            float height = 1.65f * reveal;

            // Socle pierreux en deux couches pleines, avec couronne dorée.
            for (int layer = 0; layer < 2; layer++) for (int ix = -1; ix <= 1; ix++) for (int iz = -1; iz <= 1; iz++) {
                box(vc, mat, tmp, cx, ground + 0.055f + layer * 0.105f, cz, dx, dz,
                        f + ix * 0.17f, l + iz * 0.17f, 0.105f, 0.065f, 0.105f,
                        layer == 1 && (ix == 0 || iz == 0) ? GOLD : STONE, Math.round(a * reveal));
            }
            // Mât continu : les volumes se chevauchent légèrement à chaque joint.
            for (int part = 0; part < 14; part++) {
                float u = part / 13.0f;
                box(vc, mat, tmp, cx, ground + 0.18f + u * height, cz, dx, dz, f, l,
                        0.032f, Math.max(0.025f, height / 13.0f * 0.62f), 0.032f,
                        part == 13 ? GOLD : STONE_LIGHT, Math.round(a * reveal));
            }
            float top = ground + 0.18f + height;
            cube(vc, mat, tmp, cx, top + 0.095f, cz, dx, dz, f, l, 0.085f,
                    GOLD, Math.round(a * reveal));
            box(vc, mat, tmp, cx, top + 0.19f, cz, dx, dz, f, l,
                    0.028f, 0.065f, 0.028f, EARTH_LIGHT, Math.round(a * reveal));

            float clothReveal = smooth((reveal - 0.32f) / 0.68f);
            // Barre transversale qui porte réellement toute la largeur du tissu.
            for (int bar = 0; bar < 9; bar++) {
                float offset = bar * 0.10f;
                box(vc, mat, tmp, cx, top - 0.025f, cz, dx, dz,
                        f + tangentF * offset, l + tangentL * offset,
                        0.045f * radialF + 0.068f * radialL, 0.045f,
                        0.045f * radialL + 0.068f * radialF, GOLD, Math.round(a * clothReveal));
            }
            // Tissu 7×7 sans cellule manquante ; bordure, croix et losange sont tissés dans la trame.
            for (int row = 0; row < 7; row++) for (int col = 0; col < 7; col++) {
                float offset = 0.045f + col * 0.10f;
                float wave = Mth.sin(t * 9.0f + col * 0.62f + banner) * 0.022f * clothReveal;
                float clothF = f + tangentF * offset + Mth.cos(angle) * wave;
                float clothL = l + tangentL * offset + Mth.sin(angle) * wave;
                float y = top - 0.13f - row * 0.10f;
                boolean border = row == 0 || col == 0 || col == 6;
                boolean cross = col == 3 || row == 3;
                boolean diamond = Math.abs(col - 3) + Math.abs(row - 3) == 2;
                int rgb = border ? GOLD : (cross || diamond) ? EARTH_LIGHT : BANNER;
                box(vc, mat, tmp, cx, y, cz, dx, dz, clothF, clothL,
                        (0.022f * radialF + 0.066f * radialL) * clothReveal,
                        0.072f * clothReveal,
                        (0.022f * radialL + 0.066f * radialF) * clothReveal,
                        rgb, Math.round(a * clothReveal * 0.94f));
            }
            // Franges inférieures attachées à chaque colonne, plus aucun bas de tissu coupé.
            for (int col = 0; col < 7; col++) {
                float offset = 0.045f + col * 0.10f;
                box(vc, mat, tmp, cx, top - 0.13f - 7 * 0.10f, cz, dx, dz,
                        f + tangentF * offset, l + tangentL * offset,
                        (0.024f * radialF + 0.058f * radialL) * clothReveal, 0.075f * clothReveal,
                        (0.024f * radialL + 0.058f * radialF) * clothReveal,
                        col % 2 == 0 ? GOLD : EARTH, Math.round(a * clothReveal));
            }
        }

        // Glaive central complet : lame à fuller, pointe, quillons, grip et pommeau.
        float swordRise = smooth((t - 0.25f) / 0.24f);
        float ground = terrain.at(0.0f, 0.0f);
        float swordBase = ground + 0.65f + swordRise * 0.45f;
        for (int part = 0; part < 14; part++) {
            float u = part / 13.0f;
            float half = part >= 11 ? Math.max(0.018f, 0.067f - (part - 10) * 0.016f) : 0.067f;
            float y = swordBase + 0.43f + u * 1.16f;
            box(vc, mat, tmp, cx, y, cz, dx, dz, 0.0f, 0.0f,
                    half, 0.065f, 0.038f, part % 4 == 0 ? EARTH_LIGHT : STONE_LIGHT,
                    Math.round(a * swordRise * 0.96f));
            if (part < 11) {
                box(vc, mat, tmp, cx, y, cz, dx, dz, 0.0f, -0.041f,
                        0.018f, 0.060f, 0.010f, EARTH, Math.round(a * swordRise * 0.84f));
            }
        }
        box(vc, mat, tmp, cx, swordBase + 1.67f, cz, dx, dz, 0.0f, 0.0f,
                0.028f, 0.050f, 0.028f, EARTH_LIGHT, Math.round(a * swordRise * 0.96f));
        for (int guard = -4; guard <= 4; guard++) {
            box(vc, mat, tmp, cx, swordBase + 0.37f - Math.abs(guard) * 0.012f, cz, dx, dz,
                    0.0f, guard * 0.075f, 0.048f, 0.042f, 0.048f,
                    Math.abs(guard) == 4 ? EARTH_LIGHT : GOLD, Math.round(a * swordRise));
        }
        for (int grip = 0; grip < 5; grip++) {
            box(vc, mat, tmp, cx, swordBase + 0.27f - grip * 0.085f, cz, dx, dz,
                    0.0f, 0.0f, 0.044f, 0.052f, 0.044f,
                    grip % 2 == 0 ? SOIL_DARK : GOLD, Math.round(a * swordRise));
        }
        cube(vc, mat, tmp, cx, swordBase - 0.18f, cz, dx, dz, 0.0f, 0.0f,
                0.075f, EARTH, Math.round(a * swordRise));
        cube(vc, mat, tmp, cx, swordBase - 0.25f, cz, dx, dz, 0.0f, 0.0f,
                0.045f, GOLD, Math.round(a * swordRise));

        // Impulsion de ralliement : chevrons qui partent dans quatre directions à 0,44 de la piste.
        float rally = clamp((t - 0.44f) / 0.34f);
        if (rally > 0.0f && rally < 1.0f) {
            for (int direction = 0; direction < 4; direction++) {
                float angle = direction * 1.5707963f;
                for (int chevron = 0; chevron < 3; chevron++) {
                    float r = 0.65f + rally * 2.25f - chevron * 0.22f;
                    float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
                    float sideF = Mth.cos(angle + 1.5707963f) * 0.22f;
                    float sideL = Mth.sin(angle + 1.5707963f) * 0.22f;
                    float y = terrain.at(f, l) + 0.07f + chevron * 0.055f;
                    link(vc, mat, tmp, cx, y, cz, dx, dz,
                            f - sideF, y, l - sideL, f + Mth.cos(angle) * 0.18f, y,
                            l + Mth.sin(angle) * 0.18f, 0.025f, GOLD, EARTH_LIGHT,
                            Math.round(a * (1.0f - rally) * (1.0f - chevron * 0.18f)));
                    link(vc, mat, tmp, cx, y, cz, dx, dz,
                            f + sideF, y, l + sideL, f + Mth.cos(angle) * 0.18f, y,
                            l + Mth.sin(angle) * 0.18f, 0.025f, GOLD, EARTH_LIGHT,
                            Math.round(a * (1.0f - rally) * (1.0f - chevron * 0.18f)));
                }
            }
        }

        // Motes de moral ascendants, déterministes et entièrement éteints à la fermeture.
        for (int i = 0; i < 64; i++) {
            float h = hash(salt, 3600 + i * 43);
            float cycle = fract(t * (0.65f + h * 0.45f) + i * 0.381966f);
            float angle = i * 2.3999632f + cycle * 2.4f;
            float r = 0.45f + h * 1.10f;
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = terrain.at(f, l) + 0.10f + cycle * (1.25f + h * 0.75f);
            float fade = smooth(cycle / 0.10f) * (1.0f - smooth((cycle - 0.70f) / 0.30f));
            float size = 0.014f + h * 0.020f;
            cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size,
                    i % 4 == 0 ? GOLD : EARTH_LIGHT, Math.round(a * fade * 0.68f));
        }
    }

    private static void groundRune(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cz, float dx, float dz,
                                   float f, float l, float angle, float size,
                                   int rgb, int alpha, TerrainView terrain) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        float ax = f + (-size * c - -size * s), az = l + (-size * s + -size * c);
        float bx = f + (size * c - -size * s), bz = l + (size * s + -size * c);
        float cx2 = f + (size * c - size * s), cz2 = l + (size * s + size * c);
        float dx2 = f + (0.0f * c - size * 1.5f * s), dz2 = l + (0.0f * s + size * 1.5f * c);
        groundLink(vc, mat, tmp, cx, cz, dx, dz, ax, az, bx, bz, 0.022f, rgb, alpha, terrain);
        groundLink(vc, mat, tmp, cx, cz, dx, dz, bx, bz, cx2, cz2, 0.022f, rgb, alpha, terrain);
        groundLink(vc, mat, tmp, cx, cz, dx, dz, cx2, cz2, dx2, dz2, 0.022f, rgb, alpha, terrain);
        groundLink(vc, mat, tmp, cx, cz, dx, dz, dx2, dz2, ax, az, 0.022f, rgb, alpha, terrain);
    }

    private static void groundLink(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cz, float dx, float dz,
                                   float f0, float l0, float f1, float l1,
                                   float half, int rgb, int alpha, TerrainView terrain) {
        float y0 = terrain.at(f0, l0) + 0.032f;
        float y1 = terrain.at(f1, l1) + 0.032f;
        link(vc, mat, tmp, cx, (y0 + y1) * 0.5f, cz, dx, dz,
                f0, y0, l0, f1, y1, l1, half, rgb, EARTH_LIGHT, alpha);
    }

    /** Volume de poussière ou de sable avec cavités et suppression dans les blocs solides. */
    private static void billow(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float cx, float cz, float dx, float dz,
                               float f, float cy, float lateral, float radius, float vertical,
                               float time, int seed, int deep, int light, int alpha,
                               int detail, TerrainView terrain) {
        if (alpha <= 5 || radius <= 0.015f) return;
        int grid = detail >= 2 ? 2 : 1;
        float divisor = grid == 2 ? 2.15f : 1.18f;
        float cell = radius / (grid + 0.35f);
        for (int x = -grid; x <= grid; x++) for (int y = -grid; y <= grid; y++) for (int z = -grid; z <= grid; z++) {
            float nx = x / divisor, ny = y / (divisor * 0.82f), nz = z / divisor;
            float distance = nx * nx + ny * ny + nz * nz;
            if (distance > 1.10f) continue;
            float h = hash(seed, x * 733 + y * 379 + z * 191);
            if (distance < 0.18f && h < 0.34f) continue;
            float drift = Mth.sin(time + x * 0.7f + z * 0.9f) * cell * 0.14f;
            float px = x * cell + drift;
            float py = y * cell * vertical + drift * 0.30f;
            float pz = z * cell - drift * 0.55f;
            if (terrain.blocked(f + px, cy + py, lateral + pz)) continue;
            float half = cell * (0.52f + 0.15f * h);
            int rgb = color(deep, light, clamp(0.16f + 0.14f * (y + grid) + 0.36f * h));
            rot(vc, mat, tmp, cx, cy + py, cz, dx, dz, f + px, lateral + pz,
                    half, half * (0.80f + 0.14f * h), half,
                    time * 0.16f + h, h * 0.40f, time * 0.10f, rgb,
                    Math.round(alpha * (0.52f + 0.42f * h) * (1.0f - distance * 0.18f)));
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
        rot(vc, mat, tmp, cx, (y0 + y1) * 0.5f, cz, dx, dz,
                (f0 + f1) * 0.5f, (l0 + l1) * 0.5f,
                half, length * 0.56f, half, yaw, pitch, 0.0f,
                color(rgb, tip, 0.32f), alpha);
    }

    private static int color(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, clamp(u));
    }

    private static float clamp(float value) {
        return Mth.clamp(value, 0.0f, 1.0f);
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float fract(float value) {
        return value - Mth.floor(value);
    }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }

    private static void ensureLevel(ClientLevel level) {
        if (terrainLevel == level) return;
        terrainLevel = level;
        GROUND_CACHE.clear();
        SOLID_CACHE.clear();
    }

    private static float sampleGround(ClientLevel level, double worldX, double originY, double worldZ) {
        ensureLevel(level);
        int x = Mth.floor(worldX), z = Mth.floor(worldZ);
        int originBlockY = Mth.floor(originY);
        long key = (((long) x & 0x3FFFFFFL) << 38)
                ^ (((long) z & 0x3FFFFFFL) << 12) ^ ((long) (originBlockY + 2048) & 0xFFFL);
        Float cached = GROUND_CACHE.get(key);
        if (cached != null) return cached;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(originY + 0.75));
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(originY - 24.0));
        float surface = (float) originY;
        for (int y = maxY; y >= minY; y--) {
            pos.set(x, y, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = shape.max(Direction.Axis.Y);
            if (!Double.isFinite(top)) continue;
            double candidate = y + top;
            if (candidate > originY + 0.751 || candidate < originY - 24.0) continue;
            surface = (float) candidate;
            break;
        }
        if (GROUND_CACHE.size() >= CACHE_LIMIT) GROUND_CACHE.clear();
        GROUND_CACHE.put(key, surface);
        return surface;
    }

    private record TerrainView(ClientLevel level, double worldX, double worldY, double worldZ,
                               float cameraY, float dx, float dz, float worldScale) {
        float at(float f, float lateral) {
            if (level == null) return cameraY - 0.56f;
            double wx = worldX + (dx * f - dz * lateral) * worldScale;
            double wz = worldZ + (dz * f + dx * lateral) * worldScale;
            return cameraY + (sampleGround(level, wx, worldY, wz) - (float) worldY) / worldScale;
        }

        boolean blocked(float f, float y, float lateral) {
            if (level == null) return false;
            double wx = worldX + (dx * f - dz * lateral) * worldScale;
            double wy = worldY + (y - cameraY) * worldScale;
            double wz = worldZ + (dz * f + dx * lateral) * worldScale;
            int bx = Mth.floor(wx), by = Mth.floor(wy), bz = Mth.floor(wz);
            long key = (((long) bx & 0x3FFFFFFL) << 38) ^ (((long) bz & 0x3FFFFFFL) << 12)
                    ^ ((long) (by + 2048) & 0xFFFL);
            Boolean cached = SOLID_CACHE.get(key);
            if (cached != null) return cached;
            BlockPos pos = new BlockPos(bx, by, bz);
            boolean solid = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
            if (SOLID_CACHE.size() >= CACHE_LIMIT) SOLID_CACHE.clear();
            SOLID_CACHE.put(key, solid);
            return solid;
        }
    }
}
