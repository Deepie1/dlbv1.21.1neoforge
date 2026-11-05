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

/** Chorégraphies Poison : perforation de ronces, shroud défensif et nova de peste. */
public final class PoisonSignatureVfx {
    public static final int VENOM = 0;
    public static final int MIASMA = 1;
    public static final int PLAGUE = 2;

    private static final int DEEP = 0x061A0C;
    private static final int STEM = 0x0A3D16;
    private static final int BODY = 0x1E7A32;
    private static final int ACID = 0x63E05C;
    private static final int TIP = 0xB6FF86;
    private static final int PURPLE_DEEP = 0x13091E;
    private static final int PURPLE = 0x4D1C68;
    private static final int VIOLET = 0x8A3FA0;
    private static final int ROT = 0x17130D;
    private static final int STONE = 0x302D2A;
    private static final int BLOOD = 0x8C2430;

    private static final int CACHE_LIMIT = 1536;
    private static final Map<Long, Float> GROUND_CACHE = new HashMap<>(CACHE_LIMIT);
    private static final Map<Long, Boolean> SOLID_CACHE = new HashMap<>(CACHE_LIMIT);
    private static ClientLevel terrainLevel;

    private PoisonSignatureVfx() { }

    public static void beginTerrainTick(ClientLevel level) {
        if (level == null) return;
        ensureLevel(level);
        GROUND_CACHE.clear();
        SOLID_CACHE.clear();
    }

    /** Préchauffe le disque utile, hors de la passe de rendu. */
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
                              float dirX, float dirZ, int variant, int intensity,
                              float dragX, float dragZ, float worldScale, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001f) { dirX = 0.0f; dirZ = 1.0f; }
        else { dirX /= length; dirZ /= length; }
        TerrainView terrain = new TerrainView(level, worldX, worldY, worldZ, cy, dirX, dirZ,
                Math.max(0.05f, worldScale));
        if (variant == VENOM) {
            renderVenom(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        } else if (variant == MIASMA) {
            renderMiasma(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain, dragX, dragZ);
        } else {
            renderPlague(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, intensity, alpha, salt, terrain);
        }
    }

    /** VENOM : poussée, perforation, éclaboussure, patch de ronces puis pourrissement. */
    private static void renderVenom(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float thrust = smooth(t / 0.20f);
        float impact = smooth((t - 0.20f) / 0.075f);
        float patch = smooth((t - 0.255f) / 0.22f);
        float rot = smooth((t - 0.80f) / 0.20f);
        float spectral = smooth(t / 0.07f) * (1.0f - smooth((t - 0.56f) / 0.20f));
        float ground = terrain.at(0.0f, 0.0f);
        float mawF = -1.90f + 1.90f * thrust;
        int briar = color(BODY, ROT, rot);

        // Mâchoire spectrale autour de la lame : arcade, crocs et volume crânien translucide.
        for (int jaw = 0; jaw < 2; jaw++) {
            float sign = jaw == 0 ? 1.0f : -1.0f;
            float jawY = cy + sign * (0.16f + 0.52f * (1.0f - impact));
            for (int tooth = 0; tooth < 9; tooth++) {
                float lateral = (tooth - 4) * 0.20f;
                float f = mawF + 0.10f * (1.0f - Math.abs(lateral));
                cube(vc, mat, tmp, cx, jawY, cz, dx, dz, f, lateral, 0.080f,
                        color(PURPLE, BODY, tooth / 8.0f), Math.round(alpha * spectral * 0.72f));
                float fang = tooth == 4 ? 0.66f : (tooth == 3 || tooth == 5 ? 0.54f : 0.38f);
                link(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f + 0.06f, jawY, lateral, f + 0.17f, jawY - sign * fang, lateral * 0.91f,
                        0.037f, BODY, TIP, Math.round(alpha * spectral * 0.88f));
            }
            // Deux traces de plasma jumelles suivent exactement la poussée.
            for (int trail = 0; trail < 22; trail++) {
                float u = trail / 21.0f;
                float f = Mth.lerp(u, -1.95f, mawF + 0.10f);
                float y = cy + sign * (0.28f + 0.06f * Mth.sin(u * 7.0f + t * 14.0f));
                float l = sign * 0.43f;
                cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                        0.022f + 0.018f * u, u > 0.78f ? TIP : ACID,
                        Math.round(alpha * spectral * (0.25f + 0.70f * u)));
            }
        }
        for (int i = 0; i < 18; i++) {
            float u = i / 17.0f;
            float f = mawF - 0.62f + 0.48f * u;
            float y = cy + 0.18f * Mth.sin(u * 3.14159f);
            float l = (hash(salt, i * 31) - 0.5f) * (0.75f - 0.35f * u);
            cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, 0.055f,
                    i % 3 == 0 ? VIOLET : BODY, Math.round(alpha * spectral * 0.26f));
        }

        // Ronces enroulées autour du bras et de l'arme pendant la poussée.
        for (int vine = 0; vine < 2; vine++) {
            float previousF = -1.90f, previousY = cy, previousL = 0.0f;
            for (int k = 1; k <= 20; k++) {
                float u = k / 20.0f;
                float f = Mth.lerp(u, -1.90f, -0.34f);
                float angle = u * 10.0f + vine * 3.14159f;
                float y = cy + 0.19f * Mth.sin(angle);
                float l = 0.19f * Mth.cos(angle);
                link(vc, mat, tmp, cx, cy, cz, dx, dz, previousF, previousY, previousL, f, y, l,
                        0.030f, STEM, briar, Math.round(alpha * spectral * 0.94f));
                if (k % 4 == 0) {
                    cube(vc, mat, tmp, cx, y + 0.055f, cz, dx, dz, f + 0.04f, l,
                            0.028f, ACID, Math.round(alpha * spectral));
                }
                previousF = f; previousY = y; previousL = l;
            }
        }

        // Perforation : flash violet localisé, puis jets de venin derrière la cible.
        if (impact > 0.0f && t < 0.43f) {
            float flash = (1.0f - smooth((t - 0.20f) / 0.23f)) * impact;
            for (int i = 0; i < 30; i++) {
                float angle = i * 2.39996f;
                float radius = (0.08f + 0.68f * smooth((t - 0.20f) / 0.16f))
                        * (0.45f + 0.55f * hash(salt, 300 + i));
                float f = Mth.cos(angle) * radius;
                float l = Mth.sin(angle) * radius;
                float y = cy + (hash(salt, 350 + i) - 0.5f) * 0.70f;
                cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, 0.025f + 0.035f * flash,
                        i % 4 == 0 ? TIP : VIOLET, Math.round(alpha * flash));
            }
        }
        for (int jet = 0; jet < 18; jet++) {
            float age = t - 0.235f - (jet % 6) * 0.009f;
            if (age <= 0.0f || age > 0.34f) continue;
            float time = age * 5.0f;
            float h = hash(salt, 500 + jet * 37);
            float f = 0.12f + (0.65f + 1.10f * h) * time;
            float l = (hash(salt, 530 + jet * 41) - 0.5f) * 1.25f * time;
            float y = cy + 0.16f + (0.35f + h) * time - 1.20f * time * time;
            float floor = terrain.at(f, l);
            if (y < floor + 0.03f) continue;
            box(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    0.026f + 0.024f * h, 0.040f, 0.026f, jet % 4 == 0 ? TIP : ACID,
                    Math.round(alpha * (1.0f - age / 0.34f)));
        }
        acidPool(vc, mat, tmp, cx, cz, dx, dz, 0.55f, 0.0f,
                smooth((t - 0.29f) / 0.20f) * (1.0f - rot), 1.05f, alpha, salt + 600, terrain);

        // Fracture du sol et fragments maintenus par les nouvelles racines.
        for (int crack = 0; crack < 7; crack++) {
            groundCrack(vc, mat, tmp, cx, cz, dx, dz, crack * 0.897f + 0.2f,
                    1.20f + 0.55f * hash(salt, 700 + crack), impact, rot,
                    salt + 700 + crack * 53, terrain, true, alpha);
        }

        // Neuf racines rampantes : chaque maille suit le collider local, y compris escaliers et coins.
        for (int root = 0; root < 9; root++) {
            float angle = root * 0.698f + 0.12f;
            float length = 1.45f + 0.85f * hash(salt, 900 + root * 47);
            float previousF = 0.0f, previousL = 0.0f, previousY = ground + 0.06f;
            int visible = Math.round(18 * patch);
            for (int k = 1; k <= visible; k++) {
                float u = k / 18.0f;
                float turn = (hash(salt, 920 + root * 71 + k) - 0.5f) * 0.34f;
                float f = Mth.cos(angle + turn) * length * u;
                float l = Mth.sin(angle + turn) * length * u;
                float y = terrain.at(f, l) + 0.065f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz, previousF, previousY, previousL, f, y, l,
                        0.046f * (1.0f - 0.48f * u), STEM, briar,
                        Math.round(alpha * (1.0f - 0.45f * rot)));
                if (k % 3 == 0) {
                    link(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                            f + 0.08f, y + 0.22f * (1.0f - rot), l + 0.04f,
                            0.022f, briar, rot > 0.4f ? ROT : ACID,
                            Math.round(alpha * (1.0f - 0.30f * rot)));
                }
                previousF = f; previousL = l; previousY = y;
            }
            if (patch > 0.55f && t < 0.90f) {
                float f = Mth.cos(angle) * length, l = Mth.sin(angle) * length;
                float y = terrain.at(f, l) + 0.22f;
                billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l, 0.19f, 0.55f,
                        t * 4.0f + root, salt + 1000 + root, DEEP, ACID,
                        Math.round(alpha * patch * (1.0f - rot) * 0.24f), 1, terrain);
            }
        }

        // Constriction visible des chevilles, sans modifier l'IA ou le déplacement du mob.
        for (int ring = 0; ring < 3; ring++) {
            float y = ground + 0.16f + ring * 0.20f;
            for (int i = 0; i < 28; i++) {
                float angle = i * 0.2243995f + ring * 0.35f + patch * 1.4f;
                float radius = 0.55f - 0.13f * patch;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                cube(vc, mat, tmp, cx, y + 0.04f * Mth.sin(angle * 2.0f), cz, dx, dz, f, l,
                        0.032f, briar, Math.round(alpha * patch * (1.0f - 0.40f * rot)));
                if ((i + ring * 3) % 11 == 0 && rot < 0.65f) {
                    cube(vc, mat, tmp, cx, y + 0.03f, cz, dx, dz, f * 0.92f, l * 0.92f,
                            0.018f, BLOOD, Math.round(alpha * patch * (1.0f - rot)));
                }
            }
        }

        // Embers spectraux et cendres de ronces pendant le déclin.
        for (int i = 0; i < 34; i++) {
            float h = hash(salt, 1300 + i * 31);
            float angle = i * 2.39996f;
            float radius = 0.25f + 1.65f * h;
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float floor = terrain.at(f, l);
            float y = floor + 0.18f + (1.0f - rot) * (0.30f + 0.85f * h);
            int rgb = t < 0.72f ? ACID : (i % 3 == 0 ? ROT : STONE);
            cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, 0.014f + 0.015f * h,
                    rgb, Math.round(alpha * smooth((t - 0.62f) / 0.15f) * (1.0f - 0.55f * rot)));
        }
    }

    /** MIASMA : cylindre violet/vert, suivi avec inertie, cendres, terrain et implosion. */
    private static void renderMiasma(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt, TerrainView terrain,
                                     float dragX, float dragZ) {
        float birth = smooth(t / 0.10f);
        float collapse = smooth((t - 0.86f) / 0.14f);
        float env = birth * (1.0f - collapse);
        float ground = terrain.at(0.0f, 0.0f);
        float radius = (0.92f * birth + 0.05f * Mth.sin(t * 27.0f)) * (1.0f - 0.93f * collapse);
        float dragF = dx * dragX + dz * dragZ;
        float dragL = -dz * dragX + dx * dragZ;

        // Venting initial depuis les épaules, jointures et sternum ; noyau violet nécrotique.
        for (int vent = 0; vent < 8; vent++) {
            float side = (vent & 1) == 0 ? -1.0f : 1.0f;
            float y = cy - 0.30f + (vent / 2) * 0.24f;
            float f = side * (0.16f + 0.10f * birth);
            float l = side * 0.28f;
            billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                    0.15f + 0.13f * birth, 0.85f, t * 5.0f + vent,
                    salt + 1500 + vent, PURPLE_DEEP, vent % 3 == 0 ? VIOLET : ACID,
                    Math.round(alpha * (1.0f - smooth((t - 0.08f) / 0.16f)) * 0.62f), 1, terrain);
        }
        float coreFlash = smooth(t / 0.055f) * (1.0f - smooth((t - 0.07f) / 0.10f));
        pustule(vc, mat, tmp, cx, cy + 0.15f, cz, dx, dz, 0.0f, 0.0f,
                0.26f + 0.12f * coreFlash, PURPLE, VIOLET,
                Math.round(alpha * coreFlash * 0.75f), salt + 1600);

        // Couche intérieure chaos : lourde et presque opaque.
        for (int level = 0; level < 3; level++) for (int arm = 0; arm < 6; arm++) {
            float h = hash(salt, 1700 + level * 61 + arm);
            float angle = -t * 5.2f + arm * 1.0472f + level * 0.24f;
            float r = radius * (0.38f + 0.06f * Mth.sin(t * 8.0f + arm));
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = ground + 0.32f + level * 0.56f + 0.09f * Mth.sin(t * 9.0f + h * 6.0f);
            billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                    0.27f * (1.0f - 0.65f * collapse), 1.10f, t * 4.0f + arm,
                    salt + 1800 + level * 100 + arm, PURPLE_DEEP, PURPLE,
                    Math.round(alpha * env * 1.25f), 1, terrain);
        }

        // Couche extérieure poison : cylindre bouillonnant, vert translucide.
        for (int level = 0; level < 4; level++) for (int arm = 0; arm < 10; arm++) {
            float h = hash(salt, 2100 + level * 79 + arm);
            float angle = -t * 4.1f + arm * 0.6283185f + level * 0.31f;
            float wind = 0.045f * Mth.sin(t * 34.0f + level) * Mth.sin(t * 3.14159f);
            float r = radius + wind + (h - 0.5f) * 0.12f;
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = ground + 0.20f + level * 0.47f + 0.10f * Mth.sin(t * 11.0f + arm);
            billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                    (0.25f + 0.07f * h) * (1.0f - 0.72f * collapse), 1.05f,
                    t * 5.0f + arm * 0.7f, salt + 2200 + level * 100 + arm,
                    DEEP, arm % 4 == 0 ? TIP : ACID,
                    Math.round(alpha * env * (0.34f + 0.16f * h)), 1, terrain);
        }

        // Traîne comète réelle : le centre suit déjà avec retard, ces volumes prolongent ce retard.
        float dragLength = Mth.sqrt(dragF * dragF + dragL * dragL);
        if (dragLength > 0.015f) {
            for (int trail = 0; trail < 8; trail++) {
                float u = (trail + 1) / 8.0f;
                float f = dragF * (0.65f + u * 1.75f);
                float l = dragL * (0.65f + u * 1.75f) + 0.13f * Mth.sin(t * 8.0f + trail);
                float y = ground + 0.30f + (trail % 3) * 0.37f;
                billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                        0.22f + 0.08f * u, 0.90f, t * 4.0f + trail,
                        salt + 2600 + trail, PURPLE_DEEP, ACID,
                        Math.round(alpha * env * (1.0f - u * 0.65f) * Math.min(0.65f, dragLength * 2.5f)),
                        1, terrain);
            }
        }

        // Cendres, spores et braises montent des pieds vers la tête dans le cylindre.
        for (int i = 0; i < 144; i++) {
            float h = hash(salt, 2800 + i * 43);
            float cycle = (t * (2.2f + h) + h * 7.0f + i * 0.381966f) % 1.0f;
            float angle = -t * 4.0f + i * 2.39996f;
            float r = radius * (0.28f + 0.80f * hash(salt, 2850 + i));
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = ground + 0.08f + cycle * 1.92f;
            float fade = smooth(cycle / 0.10f) * (1.0f - smooth((cycle - 0.76f) / 0.24f));
            float size = 0.010f + 0.016f * h;
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size, size * 1.45f, size,
                    angle, 0.25f, t, i % 9 == 0 ? VIOLET : (i % 4 == 0 ? TIP : ACID),
                    Math.round(alpha * env * fade * 0.82f));
        }
    }

    /** PLAGUE : pustules, snap, front 360°, fissures, spores et dissolution centre -> extérieur. */
    private static void renderPlague(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int intensity, int alpha, int salt, TerrainView terrain) {
        float charge = smooth(t / 0.21f);
        float burst = smooth((t - 0.234375f) / 0.075f);
        float wave = smooth((t - 0.234375f) / 0.34f);
        float maxRadius = 3.30f + Math.min(12, Math.max(1, intensity)) * 0.12f;
        float ground = terrain.at(0.0f, 0.0f);

        // Tinge du lanceur et pustules thoraciques qui gonflent jusqu'au snap.
        if (t < 0.31f) {
            float fade = 1.0f - smooth((t - 0.234375f) / 0.09f);
            for (int i = 0; i < 54; i++) {
                float angle = i * 2.39996f;
                float y = cy - 0.36f + 1.12f * hash(salt, 3100 + i);
                float r = 0.31f + 0.06f * Mth.sin(y * 7.0f);
                cube(vc, mat, tmp, cx, y, cz, dx, dz, Mth.cos(angle) * r, Mth.sin(angle) * r,
                        0.025f, ACID, Math.round(alpha * charge * fade * 0.32f));
            }
            for (int i = 0; i < 14; i++) {
                float angle = i * 2.39996f;
                float shoulder = i < 6 ? 0.43f : 0.31f;
                float f = Mth.cos(angle) * shoulder;
                float l = Mth.sin(angle) * shoulder;
                float y = cy + 0.05f + (i % 5) * 0.17f;
                float throb = 1.0f + 0.16f * Mth.sin(t * 62.0f + i * 1.7f);
                float size = (0.035f + 0.14f * charge) * throb;
                pustule(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size,
                        i % 3 == 0 ? PURPLE : BODY, i % 4 == 0 ? TIP : ACID,
                        Math.round(alpha * fade * (0.55f + 0.40f * charge)), salt + 3200 + i);
                if (charge > 0.55f) {
                    float bubbleY = y + size + 0.12f * ((t * 7.0f + i * 0.31f) % 1.0f);
                    cube(vc, mat, tmp, cx, bubbleY, cz, dx, dz, f, l,
                            0.016f + 0.009f * hash(salt, 3250 + i), TIP,
                            Math.round(alpha * charge * fade * 0.70f));
                }
            }
        }

        // Snap : noyau violet et violent anneau physique horizontal.
        if (burst > 0.0f && t < 0.48f) {
            float flash = (1.0f - smooth((t - 0.234375f) / 0.16f)) * burst;
            pustule(vc, mat, tmp, cx, cy + 0.10f, cz, dx, dz, 0.0f, 0.0f,
                    0.30f + 0.45f * flash, PURPLE_DEEP, VIOLET,
                    Math.round(alpha * flash), salt + 3400);
            float radius = 0.24f + maxRadius * wave;
            for (int layer = 0; layer < 3; layer++) for (int i = 0; i < 72; i++) {
                float angle = i * 0.08726646f;
                float r = radius - layer * 0.14f;
                float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
                float y = terrain.at(f, l) + 0.055f + layer * 0.035f;
                float size = (0.060f - layer * 0.012f) * (1.0f - 0.45f * wave);
                cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size,
                        layer == 0 ? VIOLET : (i % 4 == 0 ? TIP : ACID),
                        Math.round(alpha * burst * (1.0f - 0.68f * wave) * (1.0f - layer * 0.18f)));
            }
        }

        // Fissures toxiques sous le lanceur, chaque segment épouse le sol.
        for (int crack = 0; crack < 12; crack++) {
            groundCrack(vc, mat, tmp, cx, cz, dx, dz, crack * 0.5235988f + 0.08f,
                    maxRadius * (0.55f + 0.32f * hash(salt, 3500 + crack)), burst,
                    smooth((t - 0.82f) / 0.18f), salt + 3500 + crack * 59, terrain, false, alpha);
        }

        // Mur de smog et filaments projetés : dissolution différée du centre vers l'extérieur.
        for (int arm = 0; arm < 28; arm++) {
            float h = hash(salt, 3800 + arm * 47);
            float angle = arm * 0.2243995f;
            float r = (0.28f + maxRadius * wave) * (0.90f + 0.16f * h);
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float normalized = Mth.clamp(r / maxRadius, 0.0f, 1.0f);
            float decay = smooth((t - (0.70f + 0.18f * normalized)) / 0.12f);
            float y = terrain.at(f, l) + 0.22f + 0.20f * Mth.sin(t * 8.0f + arm);
            billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                    0.31f + 0.15f * h, 0.80f, t * 5.0f + arm,
                    salt + 3900 + arm, arm % 4 == 0 ? PURPLE_DEEP : DEEP,
                    arm % 5 == 0 ? VIOLET : ACID,
                    Math.round(alpha * burst * (1.0f - decay) * (0.42f + 0.18f * h)), 2, terrain);
            if ((arm & 1) == 0) {
                for (int trail = 1; trail <= 3; trail++) {
                    float tr = Math.max(0.0f, r - trail * 0.32f);
                    float tf = Mth.cos(angle) * tr, tl = Mth.sin(angle) * tr;
                    float ty = terrain.at(tf, tl) + 0.20f + trail * 0.08f;
                    billow(vc, mat, tmp, cx, cz, dx, dz, tf, ty, tl,
                            0.22f - trail * 0.025f, 0.70f, t * 4.0f + trail,
                            salt + 4000 + arm * 7 + trail, PURPLE_DEEP, ACID,
                            Math.round(alpha * burst * (1.0f - decay) * (0.28f - trail * 0.045f)),
                            1, terrain);
                }
            }
        }

        // Micro-spores et embers rapides dans tout le volume de la nova.
        for (int i = 0; i < 150; i++) {
            float h = hash(salt, 4300 + i * 43);
            float h2 = hash(salt, 4350 + i * 47);
            float angle = i * 2.39996f + t * (i % 5 == 0 ? 8.0f : 1.8f);
            float radius = maxRadius * wave * (0.15f + 0.85f * h);
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float normalized = Mth.clamp(radius / maxRadius, 0.0f, 1.0f);
            float decay = smooth((t - (0.70f + 0.18f * normalized)) / 0.12f);
            float y = terrain.at(f, l) + 0.12f + h2 * (0.35f + 1.15f * wave);
            float size = i % 5 == 0 ? 0.022f : 0.010f + 0.010f * h2;
            int rgb = i % 11 == 0 ? VIOLET : (i % 4 == 0 ? TIP : ACID);
            if (i % 5 == 0) {
                float df = Mth.cos(angle) * 0.12f, dl = Mth.sin(angle) * 0.12f;
                link(vc, mat, tmp, cx, cy, cz, dx, dz, f - df, y - 0.03f, l - dl,
                        f + df, y + 0.03f, l + dl, size, rgb, TIP,
                        Math.round(alpha * burst * (1.0f - decay) * 0.86f));
            } else {
                cube(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size, rgb,
                        Math.round(alpha * burst * (1.0f - decay) * (0.42f + 0.46f * h2)));
            }
        }

        // Haze résiduelle au sol après le front, avec décroissance centre -> périphérie.
        for (int i = 0; i < 64; i++) {
            float h = hash(salt, 4700 + i * 31);
            float angle = i * 2.39996f;
            float radius = maxRadius * (0.15f + 0.80f * h);
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float decay = smooth((t - (0.72f + 0.18f * radius / maxRadius)) / 0.12f);
            float reveal = smooth((t - 0.32f) / 0.20f) * (1.0f - decay);
            float y = terrain.at(f, l) + 0.022f;
            box(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    0.065f + 0.04f * h, 0.010f, 0.065f, i % 6 == 0 ? ACID : STEM,
                    Math.round(alpha * reveal * 0.42f));
        }
    }

    /** Impact réel sur le shroud : flare vert à l'impact + impulsion violette. */
    public static void renderDefensePulse(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float cx, float cy, float cz, float t,
                                          float dirX, float dirZ, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f) return;
        float wave = 1.0f - (1.0f - t) * (1.0f - t);
        float fade = 1.0f - t;
        float radius = 0.32f + 1.28f * wave;
        for (int i = 0; i < 40; i++) {
            float angle = i * 0.1570796f;
            cube(vc, mat, tmp, cx, cy + 0.10f, cz, dirX, dirZ,
                    Mth.cos(angle) * radius, Mth.sin(angle) * radius, 0.032f,
                    i % 4 == 0 ? ACID : VIOLET, Math.round(alpha * fade));
        }
        for (int i = 0; i < 24; i++) {
            float h = hash(salt, i * 37);
            float angle = i * 2.39996f;
            float r = 0.12f + 0.72f * wave * h;
            float f = 0.52f + Mth.cos(angle) * r;
            float l = Mth.sin(angle) * r;
            float y = cy + 0.12f + (h - 0.5f) * 0.80f;
            cube(vc, mat, tmp, cx, y, cz, dirX, dirZ, f, l,
                    0.018f + 0.025f * h, i % 3 == 0 ? TIP : ACID, Math.round(alpha * fade));
        }
    }

    /** Contagion d'une cible touchée par le front : enveloppe verte et wisps de décomposition. */
    public static void renderContagion(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float cy, float cz, float t, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f) return;
        float env = smooth(t / 0.08f) * (1.0f - smooth((t - 0.72f) / 0.28f));
        float flinch = Mth.sin(Mth.clamp(t / 0.20f, 0.0f, 1.0f) * 3.14159f)
                * (1.0f - smooth((t - 0.18f) / 0.20f));
        for (int i = 0; i < 48; i++) {
            float h = hash(salt, i * 41);
            float angle = i * 2.39996f + t * 0.8f;
            float y = cy - 0.52f + 1.25f * h - 0.12f * flinch;
            float radius = 0.34f + 0.12f * flinch + 0.07f * Mth.sin(y * 8.0f + t * 4.0f);
            cube(vc, mat, tmp, cx, y, cz, 1.0f, 0.0f,
                    Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                    0.022f + 0.012f * h, i % 6 == 0 ? TIP : ACID,
                    Math.round(alpha * env * 0.55f));
        }
        for (int wisp = 0; wisp < 9; wisp++) {
            float h = hash(salt, 100 + wisp * 37);
            float cycle = (t * 2.0f + wisp * 0.381966f) % 1.0f;
            float angle = wisp * 2.39996f + t * 2.0f;
            float f = Mth.cos(angle) * (0.28f + cycle * 0.22f);
            float l = Mth.sin(angle) * (0.28f + cycle * 0.22f);
            float y = cy - 0.35f + cycle * 1.55f;
            cube(vc, mat, tmp, cx, y, cz, 1.0f, 0.0f, f, l,
                    0.025f + 0.020f * (1.0f - cycle), wisp % 3 == 0 ? VIOLET : ACID,
                    Math.round(alpha * env * (1.0f - cycle) * 0.50f));
        }
    }

    /** Fissure en escalier avec sludge ; Venom ajoute des fragments de pierre soulevés. */
    private static void groundCrack(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cz, float dx, float dz,
                                    float angle, float length, float reveal, float decay, int seed,
                                    TerrainView terrain, boolean rocks, int alpha) {
        if (reveal <= 0.0f) return;
        float f = 0.0f, l = 0.0f;
        int visible = Math.round(10 * reveal);
        for (int step = 1; step <= visible; step++) {
            float turn = (hash(seed, step * 31) - 0.5f) * 0.60f;
            float distance = length / 10.0f;
            f += Mth.cos(angle + turn) * distance;
            l += Mth.sin(angle + turn) * distance;
            float y = terrain.at(f, l) + 0.025f;
            float size = 0.055f + 0.025f * hash(seed, step * 47);
            box(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size, 0.012f, size * 0.65f,
                    color(PURPLE_DEEP, ROT, decay), Math.round(alpha * reveal * (1.0f - decay * 0.45f)));
            box(vc, mat, tmp, cx, y + 0.012f, cz, dx, dz, f, l, size * 0.42f, 0.010f, size * 0.32f,
                    decay > 0.65f ? STEM : ACID, Math.round(alpha * reveal * (1.0f - decay) * 0.78f));
            if (rocks && step % 3 == 0) {
                float lift = 0.12f + 0.32f * reveal * hash(seed, step * 59);
                rot(vc, mat, tmp, cx, y + lift, cz, dx, dz, f + 0.08f, l - 0.05f,
                        size, size * 1.3f, size, angle, turn, step,
                    color(STONE, ROT, decay), Math.round(alpha * reveal * (1.0f - decay * 0.65f)));
                link(vc, mat, tmp, cx, y, cz, dx, dz, f, y, l, f + 0.08f, y + lift, l - 0.05f,
                        0.018f, STEM, BODY, Math.round(alpha * reveal * (1.0f - decay)));
            }
        }
    }

    private static void acidPool(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cz, float dx, float dz,
                                 float centerF, float centerL, float reveal, float radius,
                                 int alpha, int seed, TerrainView terrain) {
        if (reveal <= 0.0f) return;
        for (int i = 0; i < 22; i++) {
            float angle = i * 2.39996f;
            float r = radius * (0.20f + 0.80f * hash(seed, i * 31));
            float f = centerF + Mth.cos(angle) * r, l = centerL + Mth.sin(angle) * r;
            float y = terrain.at(f, l) + 0.018f;
            float size = 0.040f + 0.035f * hash(seed, i * 47);
            box(vc, mat, tmp, cx, y, cz, dx, dz, f, l, size, 0.010f, size * 0.82f,
                    i % 5 == 0 ? ACID : STEM, Math.round(alpha * reveal * 0.68f));
        }
    }

    /** Vapeur volumétrique à cavités. Chaque voxel solide est masqué par les colliders. */
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
            int rgb = color(deep, light, Mth.clamp(0.16f + 0.14f * (y + grid) + 0.36f * h, 0.0f, 1.0f));
            rot(vc, mat, tmp, cx, cy + py, cz, dx, dz, f + px, lateral + pz,
                    half, half * (0.80f + 0.14f * h), half,
                    time * 0.16f + h, h * 0.40f, time * 0.10f, rgb,
                    Math.round(alpha * (0.52f + 0.42f * h) * (1.0f - distance * 0.18f)));
        }
    }

    private static void pustule(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                float cx, float cy, float cz, float dx, float dz,
                                float f, float lateral, float radius, int base, int tip,
                                int alpha, int seed) {
        if (alpha <= 5 || radius <= 0.01f) return;
        float cell = radius * 0.58f;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x * x + y * y + z * z > 3) continue;
            float h = hash(seed, x * 71 + y * 37 + z * 19);
            cube(vc, mat, tmp, cx, cy + y * cell, cz, dx, dz,
                    f + x * cell, lateral + z * cell, cell * (0.58f + 0.12f * h),
                    color(base, tip, 0.20f + 0.65f * h), Math.round(alpha * (0.68f + 0.30f * h)));
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
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy, z, hf, hy, hl, rgb, alpha);
    }

    private static void rot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float hf, float hy, float hl,
                            float yaw, float pitch, float roll, int rgb, int alpha) {
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
        if (length < 0.001f) return;
        float sx = dx * df - dz * dl, sz = dz * df + dx * dl;
        float yaw = (float) Math.atan2(sx, sz);
        float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
        rot(vc, mat, tmp, cx, (y0 + y1) * 0.5f, cz, dx, dz,
                (f0 + f1) * 0.5f, (l0 + l1) * 0.5f,
                half, length * 0.56f, half, yaw, pitch, 0.0f,
                WeaponWorldVfxManager.mixRgb(rgb, tip, 0.32f), alpha);
    }

    private static int color(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, Mth.clamp(u, 0.0f, 1.0f));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
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
