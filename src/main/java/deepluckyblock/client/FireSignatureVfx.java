package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Signatures Fire HD en voxels de tailles libres. Les masses utilisent de grands blocs, les lobes
 * des blocs moyens et les veines/braises de petits cubes. Adaptation originale des principes
 * publics d'IGNIS et de PYROLUX Lite (advection, bruit déformé, puffs et particules analytiques).
 * Aucun asset, shader ou addon de ces packs n'est redistribué.
 */
public final class FireSignatureVfx {
    public static final int MOLTEN = 0;
    public static final int SOUL = 1;
    public static final int METEOR = 2;

    // Croûte -> rouges/oranges dominants -> îlots ambre et jaune doré de la texture fournie.
    private static final int[] LAVA = {
            0xA92F10, 0xBC3913, 0xC03B16, 0xC14316, 0xC7481A,
            0xC74D19, 0xCB511C, 0xCD591E, 0xD16221, 0xD26F24,
            0xD97529, 0xD8852D, 0xE08D34, 0xE4A53F, 0xEDCD59
    };
    private static final int[] SOUL_COLORS = {
            0x061D33, 0x07334A, 0x07506A, 0x08758B, 0x079BA8,
            0x12BCC2, 0x2ADBD4, 0x62EDDF, 0x9BFAE9, 0xD7FFF5, 0xF1FFFF
    };
    private static final int ROCK = 0x211C20;
    private static final int ROCK_RED = 0x4A2522;
    private static final int ROCK_HOT = 0x843622;
    private static final int SMOKE = 0x342D31;
    private static final int SMOKE_LIGHT = 0x554449;

    private static final int TERRAIN_CACHE_LIMIT = 768;
    private static final Map<Long, Float> TERRAIN_CACHE = new HashMap<>(TERRAIN_CACHE_LIMIT);
    private static ClientLevel terrainLevel;

    private FireSignatureVfx() { }

    /** Invalide les hauteurs de la frame précédente : les blocs modifiés sont pris en compte. */
    public static void beginTerrainTick(ClientLevel level) {
        if (level == null) return;
        ensureTerrainLevel(level);
        TERRAIN_CACHE.clear();
    }

    /** Précalcule les colonnes utiles au tick, hors de la passe de rendu. */
    public static void prepareTerrain(ClientLevel level, double worldX, double worldY, double worldZ,
                                      float dx, float dz, float worldScale) {
        if (level == null) return;
        ensureTerrainLevel(level);
        worldScale = Math.max(0.05f, worldScale);
        boolean openSky = level.canSeeSky(BlockPos.containing(worldX, worldY + 1.0, worldZ));
        for (int fi = -4; fi <= 8; fi++) {
            float f = fi * 0.5f;
            for (int li = -3; li <= 3; li++) {
                float l = li * 0.5f;
                sampleGround(level, worldX + (dx * f - dz * l) * worldScale, worldY,
                        worldZ + (dz * f + dx * l) * worldScale, openSky);
            }
        }
    }

    /** Préchauffe le disque AOE complet afin d'éviter les requêtes de terrain dans la passe de rendu. */
    public static void prepareMeteorTerrain(ClientLevel level, double worldX, double worldY, double worldZ,
                                            float worldScale) {
        if (level == null) return;
        ensureTerrainLevel(level);
        worldScale = Math.max(0.05f, worldScale);
        boolean openSky = level.canSeeSky(BlockPos.containing(worldX, worldY + 1.0, worldZ));
        for (int fi = -8; fi <= 8; fi++) {
            for (int li = -8; li <= 8; li++) {
                sampleGround(level, worldX + fi * 0.5 * worldScale, worldY,
                        worldZ + li * 0.5 * worldScale, openSky);
            }
        }
    }

    private static void ensureTerrainLevel(ClientLevel level) {
        if (terrainLevel == level) return;
        terrainLevel = level;
        TERRAIN_CACHE.clear();
    }

    /** Hauteur absolue de la surface réelle, avec repli local sous les grottes et structures. */
    private static float sampleGround(ClientLevel level, double worldX, double originY, double worldZ,
                                      boolean openSky) {
        ensureTerrainLevel(level);
        int x = Mth.floor(worldX);
        int z = Mth.floor(worldZ);
        int reference = ((Mth.floor(originY) + 128) << 1) | (openSky ? 1 : 0);
        long key = ((x & 0x1FFFFFFL) << 39) | ((z & 0x1FFFFFFL) << 14) | (reference & 0x3FFFL);
        Float cached = TERRAIN_CACHE.get(key);
        if (cached != null) return cached;

        float fallback = (float) (originY - 0.48);
        float surface;
        if (openSky) {
            // Heightmap Minecraft actualisée : suit collines, escaliers, trous et falaises.
            surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        } else {
            // Sous un plafond, la heightmap serait le toit : on cherche le vrai plancher local.
            surface = fallback;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int start = Mth.floor(originY + 1.0);
            int end = Mth.floor(originY - 8.0);
            for (int y = start; y >= end; y--) {
                pos.set(x, y, z);
                VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;
                float top = y + (float) shape.max(Direction.Axis.Y);
                if (top <= originY + 0.75) {
                    surface = top;
                    break;
                }
            }
        }
        if (TERRAIN_CACHE.size() >= TERRAIN_CACHE_LIMIT) TERRAIN_CACHE.clear();
        TERRAIN_CACHE.put(key, surface);
        return surface;
    }

    /** Convertit la hauteur monde en coordonnées caméra utilisées par le moteur de voxels. */
    private record TerrainView(ClientLevel level, double worldX, double worldY, double worldZ,
                               float dx, float dz, float baseGround, float S, float worldScale, boolean openSky) {
        float at(float f, float l) {
            if (level == null) return baseGround;
            float worldGround = sampleGround(level,
                    worldX + (dx * f - dz * l) * worldScale, worldY,
                    worldZ + (dz * f + dx * l) * worldScale, openSky);
            float fallbackWorldGround = (float) (worldY - 0.48 * S * worldScale);
            return baseGround + (worldGround - fallbackWorldGround) / worldScale;
        }
    }

    public static void render(ClientLevel level, double worldX, double worldY, double worldZ,
                              VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant, float scale, float worldScale,
                              int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f) return;
        boolean openSky = level != null
                && level.canSeeSky(BlockPos.containing(worldX, worldY + 1.0, worldZ));
        TerrainView terrain = new TerrainView(level, worldX, worldY, worldZ, dirX, dirZ,
                cy - 0.48f * scale, scale, Math.max(0.05f, worldScale), openSky);
        if (variant == MOLTEN) renderMolten(vc, mat, tmp, cx, cy, cz, t,
                dirX, dirZ, scale, alpha, salt, terrain);
        else if (variant == SOUL) renderSoul(vc, mat, tmp, cx, cy, cz, t,
                dirX, dirZ, scale, alpha, salt, terrain);
        else renderMeteor(vc, mat, tmp, cx, cy, cz, t,
                dirX, dirZ, scale, alpha, salt, terrain);
    }

    /** Mini-tsunami : cubes soudés, crête advectée, déferlement puis dispersion sans chute globale. */
    private static void renderMolten(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, float S, int alpha, int salt, TerrainView terrain) {
        float exit = smooth01((t - 0.94f) / 0.06f);
        float env = 1.0f - exit;
        float ground = terrain.at(0.0f, 0.0f);
        float breakup = waveBreakup(t);

        // Volume plein : chaque maille est un CUBE (hf=hy=hl), avec chevauchement sur les trois axes.
        // Les petites variations de taille ne créent pas de trous dans le mur de lave.
        for (int lane = -6; lane <= 6; lane++) {
            float lateral = waveLateral(t, lane) * S;
            float noise = waveLaneNoise(t, lane, salt);
            for (int k = 0; k < 15; k++) {
                float u = k / 14.0f;
                float front = waveFrontAt(t, u, lane) * S;
                float thickness = (0.24f + 0.50f * Mth.sin(u * (float) Math.PI)
                        + 0.18f * (1.0f - u)) * (1.0f + 0.35f * breakup) * S;
                float columnGround = terrain.at(front, lateral);
                float y = columnGround + waveHeightLocal(t, u, noise) * S;
                int layers = u > 0.82f ? 3 : 5;
                float previousF = 0.0f;
                float previousCubeY = 0.0f;
                float previousCube = 0.0f;
                for (int d = 0; d < layers; d++) {
                    float v = d / (float) (layers - 1);
                    float roundBack = 0.30f * Mth.sin(v * (float) Math.PI) * (1.0f - 0.35f * u) * S;
                    float backRoll = 0.06f * Mth.sin(t * 7.0f - v * 3.0f + lane * 0.4f)
                            * Mth.sin(v * (float) Math.PI) * S;
                    float f = front - thickness * v - roundBack + backRoll;
                    // Courbe portante du dos : le front garde toute sa hauteur, l'arrière retombe
                    // à 18 % suivant un cosinus. La ligne supérieure n'est donc plus horizontale.
                    float depthHeight = 0.18f + 0.82f * Mth.cos(v * (float) Math.PI * 0.5f);
                    float aboveBase = Math.max(0.0f, y - columnGround - 0.10f * S);
                    float backUndulation = 0.045f * Mth.sin(t * 6.0f - v * 2.0f + lane * 0.3f)
                            * Mth.sin(v * (float) Math.PI) * S;
                    float cubeY = y + terrain.at(f, lateral) - columnGround
                        - aboveBase * (1.0f - depthHeight) + backUndulation;
                    float h = hash01(salt, lane * 91 + k * 17 + d * 41 + 40);
                    float cube = (u > 0.82f ? 0.135f + 0.025f * h : 0.170f + 0.040f * h) * S;
                    int rgb = lavaRgb(f / S, lateral / S, u, t, salt + lane * 29 + k * 7 + d * 13);
                    // Un cube de jonction adapte sa taille aux fortes pentes : la courbe reste pleine.
                    if (d > 0 && Math.abs(cubeY - previousCubeY) > (cube + previousCube) * 0.90f) {
                        float bridge = Math.min(0.21f * S, Math.max(Math.max(cube, previousCube),
                                Math.abs(cubeY - previousCubeY) * 0.32f));
                        localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                                (f + previousF) * 0.5f, (cubeY + previousCubeY) * 0.5f, lateral,
                                bridge, rgb, Math.round(alpha * env));
                    }
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, cubeY, lateral,
                            cube, rgb, Math.round(alpha * env));
                    if (d == 0 && (k + lane) % 3 == 0) {
                        float detail = (0.045f + 0.030f * h) * S;
                        localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                                front + cube * 0.82f,
                                y + (hash01(salt, k * 41 + lane) - 0.5f) * cube,
                                lateral + (hash01(salt, k + lane * 33) - 0.5f) * cube,
                                detail,
                                lavaRgb(front / S + 0.4f, lateral / S, u + 0.2f,
                                        t + 0.17f, salt + k),
                                Math.round(alpha * env * 0.96f));
                    }
                    previousF = f;
                    previousCubeY = cubeY;
                    previousCube = cube;
                }
            }

            if ((lane & 1) == 0) {
                float u = 0.67f;
                float f = waveFrontAt(t, u, lane) * S;
                float y = terrain.at(f, lateral) + waveHeightLocal(t, u, noise) * S;
                flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, lateral,
                        (0.48f + 0.30f * hash01(salt, lane + 100)) * (1.0f - breakup * 0.65f),
                        0.080f + 0.022f * hash01(salt, lane + 120),
                        0.52f, 0.10f * Mth.sin(lane), t * 9.0f + lane,
                        false, S, Math.round(alpha * env), salt + lane * 43);
            }
            if ((lane + 6) % 4 == 0) {
                float f = waveFrontAt(t, 1.0f, lane) * S;
                float y = terrain.at(f, lateral) + waveHeightLocal(t, 1.0f, noise) * S;
                flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, lateral,
                        0.34f * (1.0f - breakup * 0.75f), 0.065f,
                        -0.20f, 0.0f, t * 10.0f + lane * 2.0f,
                        false, S, Math.round(alpha * env * 0.90f), salt + lane * 67 + 900);
            }
        }

        // Petits projectiles ENTRAINÉS par la vague : ils suivent sa crête et son déferlement.
        for (int p = 0; p < 24; p++) {
            float h = hash01(salt, p * 47 + 1080);
            float reveal = smooth01((t - 0.16f - (p % 6) * 0.015f) / 0.08f)
                    * (1.0f - smooth01((t - 0.76f) / 0.16f));
            if (reveal <= 0.0f) continue;
            int lane = p % 13 - 6;
            float u = 0.55f + 0.38f * h;
            float noise = waveLaneNoise(t, lane, salt);
            float f = (waveFrontAt(t, u, lane) + 0.10f * Mth.sin(t * 12.0f + p)) * S;
            float l = (waveLateral(t, lane) + 0.07f * Mth.cos(t * 9.0f + p)) * S;
            float y = terrain.at(f, l) + (waveHeightLocal(t, u, noise)
                    + 0.12f + 0.08f * Mth.sin(t * 11.0f + p)) * S;
            float size = (0.025f + 0.025f * h) * S;
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    size, lavaDropletRgb(h), Math.round(alpha * env * reveal));
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f - size * 1.35f, y - size * 0.35f, l,
                    size * 0.52f, h > 0.55f ? LAVA[12] : LAVA[8],
                    Math.round(alpha * env * reveal * 0.75f));
        }

        // Sillage : chaque rangée reprend une position PASSÉE du front, avec un retard croissant.
        // Il avance donc avec la vague et forme des dos arrondis au lieu d'une grille de feu statique.
        for (int row = 0; row < 5; row++) {
            float history = Math.max(0.0f, t - 0.055f - row * 0.042f);
            for (int lane = -5; lane <= 5; lane++) {
                int id = row * 31 + lane + 150;
                float h = hash01(salt, id);
                float reveal = smooth01((t - 0.10f - row * 0.025f - h * 0.020f) / 0.12f);
                if (reveal <= 0.0f) continue;
                if (h < 0.20f) continue;
                float along = (waveTravel(history) - 0.70f - row * 0.13f
                        + 0.10f * Mth.sin(lane * 0.65f + t * 5.0f - row) + (h - 0.5f) * 0.18f) * S;
                float side = (lane * 0.225f + 0.06f * Mth.sin(t * 4.0f + row + lane * 0.4f)
                        + (hash01(salt, id + 700) - 0.5f) * 0.12f) * S;
                float rollerY = terrain.at(along, side) + (0.075f + 0.05f * h
                        + 0.035f * Mth.sin(t * 6.0f - row * 0.7f + lane * 0.3f)) * S;
                float rollerSize = (0.080f + 0.055f * h) * S * reveal;
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz, along, rollerY, side,
                        rollerSize, lavaRgb(along / S, side / S, 0.25f, t, salt + id),
                        Math.round(alpha * reveal * env * 0.94f));
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        along - rollerSize * 0.90f, rollerY - rollerSize * 0.22f,
                        side + rollerSize * 0.38f, rollerSize * 0.72f,
                        LAVA[4 + (id & 3)], Math.round(alpha * reveal * env * 0.84f));
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        along + rollerSize * 0.65f, rollerY + rollerSize * 0.38f,
                        side - rollerSize * 0.30f, rollerSize * 0.48f,
                        LAVA[9 + (id & 3)], Math.round(alpha * reveal * env * 0.82f));
                if (h > 0.58f && ((lane + row) & 3) == 0) {
                    flame(vc, mat, tmp, cx, cy, cz, dx, dz, along + 0.05f * S,
                            rollerY + rollerSize * 0.65f, side,
                            (0.20f + 0.24f * h) * reveal * (1.0f - breakup * 0.40f),
                            0.045f + 0.025f * h, 0.55f + 0.25f * h,
                            (h - 0.5f) * 0.60f, t * 12.0f + row + lane,
                            false, S, Math.round(alpha * reveal * env), salt + id * 23);
                }
            }
        }

        // Éclats libres : naissent dans la masse à leur instant de lancement, puis gravité réelle.
        for (int p = 0; p < 36; p++) {
            float h = hash01(salt, p * 47 + 1200);
            float launch = 0.23f + (p % 8) * 0.042f + h * 0.018f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            int lane = p % 13 - 6;
            float side = (waveLateral(launch, lane)
                    + (hash01(salt, p * 13) - 0.5f) * 0.04f) * S;
            float originF = waveFrontAt(launch, 0.72f, lane) * S;
            float launchNoise = waveLaneNoise(launch, lane, salt);
            float originY = terrain.at(originF, side) + waveHeightLocal(launch, 0.72f, launchNoise) * S;
            float time = age * 5.6f;
            float vf = (0.85f + 1.25f * h) * S;
            float vl = (lane * 0.035f + (hash01(salt, p * 59) - 0.5f) * 0.65f) * S;
            float vy = (1.45f + 1.85f * hash01(salt, p * 61)) * S;
            float gravity = 6.8f * S;
            float f = originF + vf * time;
            float l = side + vl * time;
            float y = originY + vy * time - 0.5f * gravity * time * time;
            float hitF = originF + vf * ((vy + Mth.sqrt(vy * vy)) / gravity);
            float hitL = side + vl * ((vy + Mth.sqrt(vy * vy)) / gravity);
            float landingGround = terrain.at(hitF, hitL);
            float hit = (vy + Mth.sqrt(Math.max(0.0f, vy * vy
                    + 2.0f * gravity * (originY - landingGround)))) / gravity;
            hitF = originF + vf * hit;
            hitL = side + vl * hit;
            landingGround = terrain.at(hitF, hitL);
            float size = (0.035f + 0.055f * h) * S;
            if (time < hit) {
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        size, lavaDropletRgb(h), Math.round(alpha * env));
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - vf * 0.022f, y + size * 0.65f, l - vl * 0.022f,
                        size * 0.52f, h > 0.55f ? LAVA[12] : LAVA[8],
                        Math.round(alpha * env * 0.85f));
            } else if (time < hit + 0.28f) {
                float spread = (time - hit) * 1.8f;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, hitF,
                        landingGround + 0.035f * S, hitL,
                        size * (1.4f + spread), 0.025f * S, size * (1.2f + spread),
                        LAVA[7 + (p & 3)], Math.round(alpha * env * (1.0f - spread)));
                for (int sign = -1; sign <= 1; sign += 2) {
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            hitF + sign * spread * size * 2.0f,
                            landingGround + (0.06f + spread * 0.12f) * S,
                            hitL + sign * spread * size, size * 0.38f,
                            LAVA[10], Math.round(alpha * env * (1.0f - spread)));
                }
            }
        }
        // Splash du déferlement : la lèvre tombe mais conserve sa vitesse d'avance.
        for (int p = 0; p < 28; p++) {
            float h = hash01(salt, p * 47 + 1450);
            float launch = 0.57f + (p % 8) * 0.028f + h * 0.018f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            int lane = p % 13 - 6;
            float u = 0.82f + 0.16f * hash01(salt, p * 43 + 1451);
            float originF = waveFrontAt(launch, u, lane) * S;
            float side = waveLateral(launch, lane) * S;
            float originY = terrain.at(originF, side)
                    + waveHeightLocal(launch, u, waveLaneNoise(launch, lane, salt)) * S;
            float time = age * 5.6f;
            float vf = (1.15f + 1.35f * h) * S;
            float vl = (lane * 0.020f + (hash01(salt, p * 59 + 1452) - 0.5f) * 0.85f) * S;
            float vy = (0.65f + 1.25f * hash01(salt, p * 61 + 1453)) * S;
            float gravity = 7.4f * S;
            float f = originF + vf * time;
            float l = side + vl * time;
            float y = originY + vy * time - 0.5f * gravity * time * time;
            float approximateHit = (vy + Mth.sqrt(vy * vy)) / gravity;
            float hitF = originF + vf * approximateHit;
            float hitL = side + vl * approximateHit;
            float landingGround = terrain.at(hitF, hitL);
            float hit = (vy + Mth.sqrt(Math.max(0.0f, vy * vy
                    + 2.0f * gravity * (originY - landingGround)))) / gravity;
            hitF = originF + vf * hit;
            hitL = side + vl * hit;
            landingGround = terrain.at(hitF, hitL);
            float size = (0.030f + 0.045f * h) * S;
            if (time < hit) {
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        size, lavaDropletRgb(h), Math.round(alpha * env));
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - vf * 0.025f, y + size * 0.30f, l - vl * 0.025f,
                        size * 0.48f, LAVA[9 + (p & 3)], Math.round(alpha * env * 0.78f));
            } else if (time < hit + 0.22f) {
                float spread = (time - hit) * 2.2f;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, hitF,
                        landingGround + 0.032f * S, hitL,
                        size * (1.5f + spread), 0.024f * S, size * (1.3f + spread),
                        LAVA[7 + (p & 3)], Math.round(alpha * env * (1.0f - spread)));
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        hitF + spread * size * 2.0f,
                        landingGround + (0.05f + spread * 0.10f) * S, hitL,
                        size * 0.42f, LAVA[11], Math.round(alpha * env * (1.0f - spread)));
            }
        }
        smoke(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, ground, 0.0f,
                t, 0.69f, 18, S, alpha, salt, 0.75f);
    }

    /** Les deux flammes se transforment réellement : hélice corporelle -> croissant tressé. */
    private static void renderSoul(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, float S, int alpha, int salt, TerrainView terrain) {
        float birth = smooth01(t / 0.09f);
        float exit = smooth01((t - 0.80f) / 0.20f);
        float env = birth * (1.0f - exit);
        float ground = terrain.at(0.0f, 0.0f);
        float sweep = smooth01((t - 0.30f) / 0.38f);
        float radius = (0.82f + 0.12f * sweep) * S;
        float center = (-0.16f + 0.24f * sweep) * S;
        float moonCenterY = ground + 1.32f * S;

        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 64; k++) {
                float u = k / 63.0f;
                float reveal = smooth01((t - u * 0.12f) / 0.10f);
                if (reveal <= 0.0f) continue;
                float morph = smooth01((t - 0.17f - u * 0.075f) / 0.32f);
                float helixAngle = strand * (float) Math.PI + u * 10.4f + t * 7.0f;
                float helixRadius = (0.49f - 0.12f * u) * S;
                float hf = Mth.cos(helixAngle) * helixRadius;
                float hl = Mth.sin(helixAngle) * helixRadius;
                float hy = terrain.at(hf, hl) + (0.10f + u * 1.72f) * S;

                float arcAngle = -2.34f + u * 4.68f;
                float braid = Mth.sin(u * 24.0f + strand * (float) Math.PI + t * 7.0f);
                float taper = 0.25f + 0.75f * (float) Math.pow(Mth.sin(u * (float) Math.PI), 0.65f);
                float ar = radius + (strand == 0 ? -0.045f : 0.045f) * S + braid * 0.050f * S;
                float af = center + braid * 0.065f * S;
                float al = Mth.cos(arcAngle) * ar;
                float ay = moonCenterY + Mth.sin(arcAngle) * ar;
                float f = Mth.lerp(morph, hf, af);
                float l = Mth.lerp(morph, hl, al);
                float y = Mth.lerp(morph, hy, ay);
                float width = (0.058f + 0.066f * taper * (0.35f + 0.65f * morph)) * S * reveal * (1.0f - exit * 0.50f);

                // Gros corps + deux lobes latéraux + petit cœur : une flamme, pas un fil géométrique.
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        width, width * 1.18f, width, soulColor(strand == 0 ? 6 : 4),
                        Math.round(alpha * env * reveal));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f + width * 0.45f, y - width * 0.30f, l - width * 0.40f,
                        width * 0.72f, width * 0.82f, width * 0.72f,
                        soulColor(strand == 0 ? 3 : 2), Math.round(alpha * env * 0.84f));
                if ((k & 1) == 0) {
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f - width * 0.52f, y + width * 0.30f, l + width * 0.42f,
                            width * 0.40f, soulColor(k % 9 == 0 ? 9 : 7),
                            Math.round(alpha * env * 0.90f));
                }
                if (k % 8 == 0) {
                    float outwardF = Mth.lerp(morph, Mth.cos(helixAngle) * 0.25f, 0.30f);
                    float outwardL = Mth.lerp(morph, Mth.sin(helixAngle) * 0.25f,
                            Mth.cos(arcAngle) * 0.48f);
                    flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                            (0.24f + 0.25f * taper) * (1.0f - exit * 0.65f),
                            (0.055f + 0.025f * taper) * (0.65f + 0.35f * morph), outwardF, outwardL,
                            t * 10.0f + k * 0.7f + strand, true, S,
                            Math.round(alpha * env * reveal), salt + k * 37 + strand * 401);
                }
            }
        }

        // Aura de sol segmentée : quelques grandes plaques, jamais une ligne fine pleine.
        for (int p = 0; p < 16; p++) {
            float angle = p * (float) (Math.PI * 2.0 / 16.0) + t * 0.35f;
            float r = (0.52f + 0.06f * Mth.sin(t * 6.0f + p)) * S;
            float hs = (0.065f + 0.025f * (p & 1)) * S * birth * (1.0f - exit);
            float auraF = Mth.cos(angle) * r;
            float auraL = Mth.sin(angle) * r;
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, auraF,
                    terrain.at(auraF, auraL) + 0.035f * S, auraL,
                    hs * 1.35f, 0.022f * S, hs, soulColor(p % 7 == 0 ? 7 : 2),
                    Math.round(alpha * env * 0.60f));
        }

        // Braises fermées : elles naissent sur la tresse et héritent de son mouvement angulaire.
        for (int p = 0; p < 40; p++) {
            float launch = 0.28f + (p % 10) * 0.037f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 53 + 1700);
            float u = (p % 31) / 30.0f;
            float angle = -2.34f + u * 4.68f;
            int strand = p & 1;
            float launchMorph = smooth01((launch - 0.17f - u * 0.075f) / 0.32f);
            float launchSweep = smooth01((launch - 0.30f) / 0.38f);
            float launchRadius = (0.82f + 0.12f * launchSweep) * S;
            float launchCenter = (-0.16f + 0.24f * launchSweep) * S;
            float helixAngle = strand * (float) Math.PI + u * 10.4f + launch * 7.0f;
            float helixRadius = (0.49f - 0.12f * u) * S;
            float braid = Mth.sin(u * 24.0f + strand * (float) Math.PI + launch * 7.0f);
            float ar = launchRadius + (strand == 0 ? -0.045f : 0.045f) * S + braid * 0.050f * S;
            float originF = Mth.lerp(launchMorph, Mth.cos(helixAngle) * helixRadius,
                    launchCenter + braid * 0.065f * S);
            float originL = Mth.lerp(launchMorph, Mth.sin(helixAngle) * helixRadius, Mth.cos(angle) * ar);
            float originY = Mth.lerp(launchMorph,
                    terrain.at(originF, originL) + (0.10f + u * 1.72f) * S,
                    moonCenterY + Mth.sin(angle) * ar);
            float time = age * 2.8f;
            float f = originF + (h - 0.5f) * time * 0.35f * S;
            float l = originL + Mth.cos(angle) * time * (0.12f + h * 0.25f) * S;
            float y = originY + time * 0.32f * S;
            float hs = (0.014f + 0.026f * h) * S * (1.0f - exit);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    hs, hs * 1.55f, hs, soulColor(p % 11 == 0 ? 10 : p % 3 == 0 ? 8 : 6),
                    Math.round(alpha * (1.0f - exit) * (1.0f - age * 0.55f)));
        }
        smoke(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, ground, 0.0f,
                t, 0.76f, 14, S, alpha, salt, 0.45f);
    }

    /** Chute céleste rapide et rectiligne : côté aléatoire stable, impact exact sur la cible. */
    private static void renderMeteor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, float S, int alpha, int salt, TerrainView terrain) {
        float ground = terrain.at(0.0f, 0.0f);
        float exit = smooth01((t - 0.82f) / 0.18f);
        float q = Mth.clamp((t - 0.015f) / 0.220f, 0.0f, 1.0f);
        float birth = smooth01(t / 0.025f);
        float flightFade = 1.0f - smooth01((t - 0.235f) / 0.025f);
        float mf = meteorF(q, salt) * S;
        float ml = meteorL(q, salt) * S;
        float my = ground + meteorY(q) * S;

        if (flightFade > 0.0f && q > 0.0f) {
            float core = 0.27f * S * birth;
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz, mf, my, ml,
                    core, ROCK, Math.round(alpha * birth * flightFade));
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                    mf + core * 0.35f, my + core * 0.25f, ml + core * 0.18f,
                    core * 0.78f, ROCK_RED, Math.round(alpha * birth * flightFade));
            for (int r = 0; r < 14; r++) {
                float h = hash01(salt, r * 43 + 2000);
                float angle = r * 2.3999632f + t * (r & 1) * 2.0f;
                float band = ((r % 5) - 2) * 0.105f * S;
                float radial = (0.23f + 0.06f * h) * S * birth;
                float size = (0.105f + 0.075f * h) * S * birth;
                float f = mf + Mth.cos(angle) * radial;
                float l = ml + Mth.sin(angle) * radial;
                float y = my + band;
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        size, h > 0.78f ? ROCK_HOT : h > 0.52f ? ROCK_RED : ROCK,
                        Math.round(alpha * birth * flightFade));
                if ((r & 3) == 0) {
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + size * 0.62f, y + size * 0.22f, l,
                            size * 0.24f, h > 0.5f ? LAVA[12] : LAVA[8],
                            Math.round(alpha * birth * flightFade));
                }
            }

            // Grosse combustion advectée : les volumes pulsants suivent le PASSÉ du rocher.
            // Les petits cubes ne servent qu'à adoucir la silhouette, jamais à construire tout le feu.
            float currentTime = q * METEOR_FLIGHT_SECONDS;
            float trailReveal = smooth01(q / 0.16f);
            float meteorDistance = meteorDistance(salt);
            float backUnitF = meteorF(0.0f, salt) / meteorDistance;
            float backUnitL = meteorL(0.0f, salt) / meteorDistance;
            for (int k = 0; k < 20; k++) {
                float age = k * 0.041f;
                float historyTime = currentTime - age;
                if (historyTime <= 0.0f) continue;
                float hq = historyTime / METEOR_FLIGHT_SECONDS;
                float life = Mth.clamp(1.0f - age / 0.82f, 0.0f, 1.0f);
                float n1 = valueNoise(k * 0.37f, t * 4.6f, 1.7f, salt + 5100);
                float n2 = valueNoise(k * 0.61f, 8.3f, t * 5.4f, salt + 5200);
                float pulse = Math.max(0.45f, 0.68f + 0.38f * Mth.sin(t * 27.0f - k * 0.73f)
                        + 0.45f * (n1 - 0.5f));
                float base = (0.085f + 0.165f * life) * S * birth * trailReveal * pulse;
                float spread = (0.060f + age * 0.38f) * S;
                float billow = (Mth.sin(t * 17.0f - age * 11.0f + k * 0.41f) * age * 0.65f
                        + (n2 - 0.5f) * age * 0.35f) * S;
                float f = meteorF(hq, salt) * S + (n1 - 0.5f) * spread * 2.4f
                        - backUnitL * billow;
                float l = meteorL(hq, salt) * S + (n2 - 0.5f) * spread * 2.4f
                        + backUnitF * billow;
                float y = ground + meteorY(hq) * S
                        + Mth.sin(t * 19.0f + k * 1.17f) * spread * 0.65f
                        + Mth.cos(t * 14.0f - age * 9.0f) * age * 0.32f * S;
                int rgb = age < 0.10f ? LAVA[14] : age < 0.28f ? LAVA[12]
                        : age < 0.52f ? LAVA[9] : age < 0.70f ? LAVA[6] : LAVA[3];
                if (k < 8 || (k + Mth.floor(t * 30.0f)) % 5 != 0) {
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l, base, rgb,
                            Math.round(alpha * birth * flightFade * trailReveal
                                    * (0.42f + 0.58f * life) * (0.78f + 0.22f * pulse)));
                }
                if ((k & 1) == 0) {
                    float detail = base * (0.38f + 0.16f * n2);
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + (n2 - 0.5f) * base * 2.2f,
                            y + base * (0.35f + 0.45f * n1),
                            l + (n1 - 0.5f) * base * 2.0f,
                            detail, age < 0.35f ? LAVA[13] : LAVA[7],
                            Math.round(alpha * birth * flightFade * trailReveal * life * 0.88f));
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f - backUnitF * base * 1.25f, y - base * 0.30f,
                            l - backUnitL * base * 1.25f,
                            detail * 0.72f, LAVA[5 + (k & 3)],
                            Math.round(alpha * birth * flightFade * trailReveal * life * 0.70f));
                }
                if (k % 4 == 0) {
                    float aheadQ = Math.min(1.0f, hq + 0.01f);
                    float tangent = Mth.atan2(meteorY(aheadQ) - meteorY(hq),
                            meteorF(aheadQ, salt) - meteorF(hq, salt));
                    float streak = (0.18f + 0.25f * life) * S * trailReveal
                            * (0.82f + 0.30f * n2);
                    localRotBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                            streak, base * 0.30f, base * 0.42f, tangent,
                            age < 0.30f ? LAVA[12] : LAVA[6],
                            Math.round(alpha * birth * flightFade * trailReveal * life * 0.58f));
                }
                if (k % 6 == 0) {
                    flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                            (0.34f + 0.26f * life) * trailReveal, 0.095f + 0.040f * life,
                            backUnitF * 1.45f, backUnitL * 1.45f,
                            t * 17.0f + k * 0.9f, false, S,
                            Math.round(alpha * birth * flightFade * trailReveal * life), salt + k * 31);
                }
            }

            // Combustion : chaque braise naît sur la trajectoire, hérite de son déplacement,
            // puis est soufflé VERS L'ARRIÈRE avec turbulence, rétrécissement et refroidissement.
            for (int p = 0; p < 56; p++) {
                float h = hash01(salt, p * 47 + 2180);
                float life = fract(t * 5.5f + p * 0.381966f + h);
                float envelope = smooth01(life / 0.07f)
                        * (1.0f - smooth01((life - 0.52f) / 0.48f));
                float sparkAge = life * 0.68f;
                float historyTime = currentTime - sparkAge;
                if (historyTime <= 0.0f || envelope <= 0.0f) continue;
                float hq = historyTime / METEOR_FLIGHT_SECONDS;
                float drag = (sparkAge * (0.90f + 1.10f * h)
                        + sparkAge * sparkAge * 3.5f) * S;
                float side = Mth.sin(t * 15.0f + p * 2.17f) * sparkAge * (0.50f + 0.80f * h) * S;
                float f = meteorF(hq, salt) * S + backUnitF * drag - backUnitL * side;
                float l = meteorL(hq, salt) * S + backUnitL * drag + backUnitF * side;
                float y = ground + meteorY(hq) * S + sparkAge * (0.35f + 0.65f * h) * S
                        + Mth.cos(t * 13.0f + p) * sparkAge * 0.22f * S;
                float size = (0.025f + 0.050f * h) * (1.0f - life * 0.72f) * S;
                int rgb = life < 0.24f ? LAVA[14] : life < 0.52f ? LAVA[11]
                        : life < 0.78f ? LAVA[7] : LAVA[3];
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l, size, rgb,
                        Math.round(alpha * birth * flightFade * trailReveal * envelope * 0.96f));
                if (p % 5 == 0) {
                    localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + backUnitF * size * 1.8f, y + size * 0.35f,
                            l + backUnitL * size * 1.8f,
                            size * 0.48f, LAVA[6],
                            Math.round(alpha * birth * flightFade * trailReveal * envelope * 0.66f));
                }
            }
        }

        // Conversion voxel du sol NAPALM : nappe basse continue, langues autonomes et scorch crénelé.
        meteorGroundFire(vc, mat, tmp, cx, cy, cz, dx, dz, t, S, alpha, salt, terrain);

        for (int p = 0; p < 44; p++) {
            float age = t - 0.240f - (p % 7) * 0.010f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 59 + 2900);
            float angle = p * 2.3999632f;
            float time = age * 6.4f;
            float speed = (1.40f + 3.00f * h) * S;
            float f = Mth.cos(angle) * speed * time;
            float l = Mth.sin(angle) * speed * time;
            float vy = (1.65f + 2.75f * hash01(salt, p * 61)) * S;
            float y = ground + 0.28f * S + vy * time - 3.65f * S * time * time;
            if (y < terrain.at(f, l)) continue;
            float size = (0.025f + 0.075f * h) * S * (1.0f - exit);
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    size, p % 5 == 0 ? ROCK_RED : lavaDropletRgb(h),
                    Math.round(alpha * (1.0f - exit)));
            if ((p & 3) == 0) {
                localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - Mth.cos(angle) * size, y + size * 0.7f, l - Mth.sin(angle) * size,
                        size * 0.42f, LAVA[12], Math.round(alpha * (1.0f - exit) * 0.80f));
            }
        }
        smoke(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, ground, 0.0f,
                t, 0.238f, 28, S, alpha, salt, 1.0f);
    }

    /**
     * AOE de sol inspirée des mécanismes NAPALM/SCORCH : distribution en disque, bords bruités,
     * langues basses, braises et fissures qui se révèlent pendant le refroidissement.
     * Toutes les surfaces sont des boîtes carrées fines posées sur la hauteur réelle du terrain.
     */
    private static void meteorGroundFire(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float dx, float dz,
                                         float t, float S, int alpha, int salt, TerrainView terrain) {
        float impact = smooth01((t - 0.235f) / 0.16f);
        if (impact <= 0.0f) return;
        float exit = smooth01((t - 0.82f) / 0.18f);
        float burn = impact * (1.0f - smooth01((t - 0.64f) / 0.18f));
        float scorch = impact * (1.0f - exit);
        float cell = 0.32f * S;

        // Nappe continue : les dalles se chevauchent légèrement, sans gros cubes verticaux.
        for (int ix = -12; ix <= 12; ix++) {
            for (int iz = -12; iz <= 12; iz++) {
                float baseF = ix * 0.32f, baseL = iz * 0.32f;
                float distance = (float) Math.sqrt(baseF * baseF + baseL * baseL);
                float edgeNoise = fbm(baseF * 0.65f, 1.7f, baseL * 0.65f, salt + 6200);
                if (distance + (edgeNoise - 0.5f) * 0.55f > 4.0f * impact) continue;
                int cellId = (ix + 16) * 64 + iz + 16;
                float h = hash01(salt, cellId + 6300);
                float spots = smooth01((fbm(baseF * 1.9f, 3.1f, baseL * 1.9f, salt + 6400)
                        - 0.46f) / 0.28f);
                float f = ix * cell, l = iz * cell;
                float ground = terrain.at(f, l);
                float edgeCool = 1.0f - smooth01((distance - 2.8f) / 1.2f);
                float heat = burn * spots * edgeCool;
                int crust = heat > 0.55f ? ROCK_HOT : spots > 0.40f ? ROCK_RED : ROCK;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f, ground + 0.026f * S, l,
                        0.166f * S, 0.025f * S * scorch, 0.166f * S,
                        crust, Math.round(alpha * scorch * (0.62f + 0.20f * edgeNoise)));
                if (heat > 0.035f) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f, ground + 0.055f * S, l,
                            0.105f * S, 0.012f * S, 0.105f * S,
                            heat > 0.62f ? LAVA[8] : heat > 0.28f ? LAVA[5] : LAVA[2],
                            Math.round(alpha * heat * 0.72f));
                }
                // Environ 6 % des dalles portent une vraie langue, répartie dans tout le disque.
                if (burn > 0.015f && distance < 3.85f && h > 0.94f) {
                    float height = (0.32f + 0.98f * h) * (0.72f + 0.28f * edgeCool) * burn;
                    flame(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f, ground + 0.075f * S, l, height, 0.055f + 0.050f * h,
                            0.18f + 0.18f * h, (h - 0.5f) * 0.35f,
                            t * 12.0f + h * 6.28f, false, S,
                            Math.round(alpha * burn * 0.88f), salt + cellId * 37);
                }
            }
        }

        // Fissures ramifiées de fin : marche aléatoire déterministe, aucune ligne parfaite.
        for (int crack = 0; crack < 9; crack++) {
            float h = hash01(salt, crack * 83 + 6500);
            float baseAngle = crack * 2.3999632f + (h - 0.5f) * 0.55f;
            float f = 0.0f, l = 0.0f;
            for (int step = 0; step < 12; step++) {
                float turn = (hash01(salt, crack * 191 + step * 17 + 6510) - 0.5f) * 0.72f;
                float angle = baseAngle + turn;
                f += Mth.cos(angle) * 0.31f * S;
                l += Mth.sin(angle) * 0.31f * S;
                if (f * f + l * l > 16.0f * S * S) continue;
                float reveal = smooth01((t - 0.58f - crack * 0.009f - step * 0.006f) / 0.10f)
                        * (1.0f - exit);
                if (reveal <= 0.0f) continue;
                float ground = terrain.at(f, l);
                float half = (0.090f + 0.035f * h) * S;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f, ground + 0.077f * S, l, half, 0.018f * S, half,
                        ROCK, Math.round(alpha * reveal * 0.92f));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f, ground + 0.091f * S, l, half * 0.42f, 0.020f * S, half * 0.42f,
                        t < 0.78f ? LAVA[11] : LAVA[6], Math.round(alpha * reveal * 0.95f));
                if (step > 3 && (step & 3) == 0) {
                    float bf = f + Mth.cos(angle + 1.5708f) * 0.17f * S;
                    float bl = l + Mth.sin(angle + 1.5708f) * 0.17f * S;
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            bf, terrain.at(bf, bl) + 0.086f * S, bl,
                            half * 0.56f, 0.019f * S, half * 0.56f,
                            LAVA[4], Math.round(alpha * reveal * 0.78f));
                }
            }
        }

        // Braises qui naissent dans la nappe plutôt que sur un anneau artificiel.
        for (int p = 0; p < 28; p++) {
            float h = hash01(salt, p * 47 + 6700);
            float cycle = fract(t * 3.1f + p * 0.381966f + h);
            float envelope = smooth01(cycle / 0.08f) * (1.0f - smooth01((cycle - 0.62f) / 0.38f));
            if (burn * envelope <= 0.0f) continue;
            float angle = p * 2.3999632f + h;
            float radius = (float) Math.sqrt(hash01(salt, p * 59 + 6710)) * 3.75f * S;
            float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            float ground = terrain.at(f, l);
            float size = (0.018f + 0.022f * h) * S;
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f + cycle * 0.18f * S, ground + (0.16f + cycle * (0.75f + h)) * S, l,
                    size, cycle < 0.35f ? LAVA[12] : LAVA[7],
                    Math.round(alpha * burn * envelope * 0.90f));
        }
    }

    private static float waveTravel(float t) {
        // Translation positive jusqu'à la dernière frame : la vague quitte la zone, elle ne s'arrête pas.
        return -2.0f + 4.0f * t;
    }

    private static float waveBreakup(float t) {
        return smooth01((t - 0.57f) / 0.36f);
    }

    private static float waveLateral(float t, int lane) {
        // Treize rangées de 0.215 + les demi-cubes : largeur verrouillée à 3 blocs.
        return lane * 0.215f;
    }

    private static float waveFrontLocal(float t, float u, int lane) {
        float breakup = waveBreakup(t);
        float curl = smooth01((t - 0.13f) / 0.32f) * (1.0f - 0.45f * breakup);
        float tornLip = 0.12f * Mth.sin(lane * 1.37f + t * 3.0f) * u * u;
        float laneCurve = -0.26f * (float) Math.pow(Math.abs(lane) / 6.0f, 2.0) * u * u;
        return -0.58f + 0.58f * u + 1.05f * curl * (float) Math.pow(u, 3.2f)
                + tornLip + laneCurve;
    }

    private static float waveFrontAt(float t, float u, int lane) {
        float breakup = waveBreakup(t);
        // La crête tombe VERS L'AVANT et la base s'étale ; la vague ne remonte jamais en arrière.
        return waveTravel(t) + waveFrontLocal(t, u, lane)
                + breakup * breakup * (0.18f + 0.75f * u * u * u);
    }

    private static float waveLaneNoise(float t, int lane, int salt) {
        float arch = 1.0f - 0.24f * (float) Math.pow(Math.abs(lane) / 6.0f, 1.5);
        return (0.96f + 0.05f * Mth.sin(lane * 1.37f + (salt & 31))
                + 0.075f * Mth.sin(t * 4.0f + lane * 0.70f)
                + 0.040f * Mth.sin(t * 7.0f - lane * 0.50f)) * arch;
    }

    private static float waveHeightLocal(float t, float u, float noise) {
        float birth = smooth01((t - 0.015f - u * 0.055f) / 0.13f);
        float breakup = waveBreakup(t);
        float energy = 1.0f - 0.72f * breakup * (0.25f + 0.75f * u);
        float lipFall = 0.85f * breakup * breakup * u * u * u;
        float profile = Mth.sin(u * 2.55f) * 1.72f * noise * birth * energy;
        float sink = smooth01((t - 0.82f) / 0.18f);
        return 0.10f + Math.max(0.0f, profile - lipFall) - 2.40f * sink;
    }

    /** Dalles multi-échelles : masse large, fissures moyennes, braises minuscules, flamme pleine. */
    private static void groundPatch(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float dx, float dz,
                                    float f, float ground, float l, float reveal, float height,
                                    boolean withFlame, boolean soul, float S, int alpha, int seed, float time) {
        float h = hash01(seed, 17);
        float size = (0.135f + 0.050f * h) * S * reveal;
        int a = Math.round(alpha * reveal);
        localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, ground + 0.025f * S, l,
                size * 1.22f, 0.026f * S * reveal, size,
                soul ? soulColor(1) : LAVA[(seed & 3) + 1], Math.round(a * 0.88f));
        localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                f + size * 0.15f, ground + 0.051f * S, l - size * 0.12f,
                size * 0.76f, 0.018f * S * reveal, size * 0.68f,
                soul ? soulColor(5) : LAVA[6 + (seed & 3)], Math.round(a * 0.86f));
        // Deux fissures chaudes et une croûte sombre, posées sur la même dalle.
        localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                f - size * 0.30f, ground + 0.070f * S, l + size * 0.16f,
                size * 0.16f, 0.012f * S * reveal, size * 0.58f,
                soul ? soulColor(8) : LAVA[12], Math.round(a * 0.90f));
        localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                f + size * 0.35f, ground + 0.071f * S, l - size * 0.22f,
                size * 0.34f, 0.013f * S * reveal, size * 0.14f,
                soul ? soulColor(3) : LAVA[2], Math.round(a * 0.84f));
        if (withFlame) {
            flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, ground + 0.07f * S, l,
                    height * reveal, 0.060f + 0.025f * h,
                    0.20f, (h - 0.5f) * 0.35f, time * 8.0f + h * 6.28f,
                    soul, S, a, seed);
        }
    }

    /**
     * Flamme vivante : bruit déformé défilant vers le haut, hauteur variable et flammèches autonomes.
     * Réimplémentation voxel des principes de la version gratuite PYROLUX Lite, sans reprendre son code.
     */
    private static void flame(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float f, float y, float l, float height, float width,
                              float windF, float windL, float phase, boolean soul, float S,
                              int alpha, int seed) {
        if (height <= 0.003f || alpha <= 0) return;
        float jitter = hash01(seed, 33);
        float time = phase;
        float rootF = (valueNoise(time * 0.07f, jitter * 9.0f, 1.7f, seed) - 0.5f) * width * 0.34f * S;
        float rootL = (valueNoise(time * 0.06f, jitter * 7.0f, 8.3f, seed ^ 991) - 0.5f) * width * 0.34f * S;
        float flick = 0.82f + 0.20f * Mth.sin(time * 1.70f + jitter * 20.0f)
                + 0.22f * (valueNoise(time * 0.42f, jitter * 10.0f, 0.0f, seed) - 0.5f);
        float animatedHeight = height * flick;
        int levels = 8;
        for (int k = 0; k < levels; k++) {
            float u = k / (float) (levels - 1);
            // Deux champs lisses remontent à des vitesses différentes : la silhouette se scinde et
            // se referme au lieu de se balancer comme un objet rigide.
            float n1 = fbm(jitter * 3.1f + u * 0.55f, u * 1.50f - time * 0.42f,
                    jitter * 7.0f + time * 0.03f, seed);
            float n2 = fbm(jitter * 5.7f + u * 1.40f, u * 3.20f - time * 0.68f,
                    2.7f + time * 0.06f, seed ^ 0x51F2);
            float warpF = (n1 - 0.5f) * 2.15f * u + (n2 - 0.5f) * 0.55f * u;
            float warpL = (n2 - 0.5f) * 1.75f * u + (n1 - 0.5f) * 0.50f * u;
            float pf = f + rootF + (windF * u * u * animatedHeight + warpF * width) * S;
            float pl = l + rootL + (windL * u * u * animatedHeight + warpL * width) * S;
            float py = y + u * animatedHeight * S;
            float profile = Math.max(0.055f, (float) Math.pow(1.0f - u, 0.80f)
                    * (0.70f + 0.30f * smooth01(u / 0.25f)));
            float w = width * profile * S * (0.78f + 0.38f * n1);
            float hy = Math.max(animatedHeight * S / levels * 0.70f, w * 0.58f);
            int core = soul ? soulRamp(u, true) : fireRamp(u, true);
            int outer = soul ? soulRamp(u, false) : fireRamp(u, false);
            if (u < 0.84f || n1 + n2 > 0.62f) {
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                        w * 0.78f, hy, w * 0.82f, core, Math.round(alpha * (1.0f - u * 0.25f)));
            }
            if (u < 0.80f && (k & 1) == 0) {
                float breathe = 0.72f + 0.28f * Mth.sin(time * 1.9f + k * 0.8f + jitter * 6.0f);
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        pf + w * (0.48f + 0.20f * n2), py - hy * 0.18f, pl + w * (n1 - 0.5f) * 0.35f,
                        w * 0.82f * breathe, hy * 0.88f, w * 0.84f, outer, Math.round(alpha * 0.84f));
                if (k < 6) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            pf + w * (n2 - 0.5f) * 0.4f, py - hy * 0.24f, pl - w * 0.54f,
                            w * 0.48f, hy * 0.72f, w * 0.48f,
                            soul ? soulColor(2) : LAVA[5 + (k & 1)], Math.round(alpha * 0.70f));
                }
            }
            // Le haut se divise en deux langues dont la distance et l'existence varient avec le bruit.
            if (u > 0.36f && n1 + n2 > 0.86f) {
                float split = smooth01((u - 0.36f) / 0.64f);
                float separation = width * S * split * (0.28f + 0.60f * n2);
                float lobeW = w * (0.36f + 0.18f * n1);
                for (int sign = -1; sign <= 1; sign += 2) {
                    if ((k + sign) % 3 == 0) continue;
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            pf + separation * sign, py + hy * (0.10f + 0.18f * n2),
                            pl - separation * sign * 0.42f,
                            lobeW, hy * 0.64f, lobeW,
                            soul ? soulColor(4 + (k & 1)) : LAVA[6 + (k & 3)],
                            Math.round(alpha * (0.45f + 0.35f * n2) * (1.0f - u * 0.35f)));
                }
            }
            if (k % 4 == 0) {
                float aura = w * 1.46f;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                        aura, hy * 1.32f, aura, core, Math.round(alpha * 0.09f * (1.0f - u * 0.45f)));
            }
        }

        // Flammèches : cycles fermés ascendants, taille 0.42 -> 0.08, turbulence et refroidissement.
        for (int p = 0; p < 3; p++) {
            float cycle = fract(time * (0.34f + 0.08f * jitter) + p * 0.217f + jitter);
            float envelope = smooth01(cycle / 0.12f) * (1.0f - smooth01((cycle - 0.52f) / 0.48f));
            if (envelope <= 0.0f) continue;
            float h = hash01(seed, p * 47 + 400);
            float turbulenceF = (valueNoise(h * 9.0f, cycle * 2.1f - time * 0.16f, 1.3f, seed + p) - 0.5f)
                    * width * 2.8f * cycle;
            float turbulenceL = (valueNoise(h * 7.0f, cycle * 1.8f - time * 0.13f, 8.1f, seed ^ (p * 997)) - 0.5f)
                    * width * 2.3f * cycle;
            float pf = f + rootF + (windF * animatedHeight * cycle * 0.55f + turbulenceF) * S;
            float pl = l + rootL + (windL * animatedHeight * cycle * 0.55f + turbulenceL) * S;
            float py = y + animatedHeight * S * (0.12f + cycle * 1.42f);
            float size = width * S * (0.62f - 0.46f * cycle) * (0.82f + 0.36f * h);
            float heat = 0.18f + cycle * 0.78f;
            int rgb = soul ? soulRamp(heat, false) : fireRamp(heat, false);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                    size, size * (1.10f + 0.35f * (1.0f - cycle)), size,
                    rgb, Math.round(alpha * envelope * 0.88f));
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                    pf - size * 0.20f, py + size * 0.25f, pl + size * 0.16f,
                    size * 0.46f, size * 0.62f, size * 0.46f,
                    soul ? soulRamp(heat, true) : fireRamp(heat, true),
                    Math.round(alpha * envelope * 0.78f));
        }
        for (int e = 0; e < 2; e++) {
            float cycle = fract(time * (0.39f + 0.035f * e) + e * 0.319f + jitter * 1.7f);
            float envelope = smooth01(cycle / 0.06f) * (1.0f - smooth01((cycle - 0.48f) / 0.52f));
            if (envelope <= 0.0f) continue;
            float h = hash01(seed, e * 71 + 620);
            float originU = 0.38f + 0.42f * h;
            float pf = f + rootF + (windF * animatedHeight * originU
                    + (h - 0.5f) * width * 2.2f * cycle
                    + Mth.sin(time + cycle * 5.0f + e) * width * 0.40f) * S;
            float pl = l + rootL + (windL * animatedHeight * originU
                    + (hash01(seed, e * 73 + 621) - 0.5f) * width * 2.0f * cycle) * S;
            float py = y + animatedHeight * S * (originU + cycle * 1.25f);
            float size = width * S * (0.12f - 0.075f * cycle) * (0.65f + 0.50f * h);
            float heat = 0.35f + cycle * 0.62f;
            localCube(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl, size,
                    soul ? soulRamp(heat, e == 0) : fireRamp(heat, e == 0),
                    Math.round(alpha * envelope * 0.92f));
        }
    }

    private static float fract(float value) {
        return value - Mth.floor(value);
    }

    private static float fbm(float x, float y, float z, int seed) {
        return valueNoise(x, y, z, seed) * 0.6667f
                + valueNoise(x * 2.07f + 17.1f, y * 2.07f + 5.3f, z * 2.07f + 11.7f,
                seed ^ 0x2F6B) * 0.3333f;
    }

    /** Value noise 3D lisse, indépendant du shader Godot mais fondé sur le même principe public. */
    private static float valueNoise(float x, float y, float z, int seed) {
        int xi = Mth.floor(x), yi = Mth.floor(y), zi = Mth.floor(z);
        float tx = smooth01(x - xi), ty = smooth01(y - yi), tz = smooth01(z - zi);
        float n000 = lattice(seed, xi, yi, zi);
        float n100 = lattice(seed, xi + 1, yi, zi);
        float n010 = lattice(seed, xi, yi + 1, zi);
        float n110 = lattice(seed, xi + 1, yi + 1, zi);
        float n001 = lattice(seed, xi, yi, zi + 1);
        float n101 = lattice(seed, xi + 1, yi, zi + 1);
        float n011 = lattice(seed, xi, yi + 1, zi + 1);
        float n111 = lattice(seed, xi + 1, yi + 1, zi + 1);
        float nx00 = Mth.lerp(tx, n000, n100);
        float nx10 = Mth.lerp(tx, n010, n110);
        float nx01 = Mth.lerp(tx, n001, n101);
        float nx11 = Mth.lerp(tx, n011, n111);
        return Mth.lerp(tz, Mth.lerp(ty, nx00, nx10), Mth.lerp(ty, nx01, nx11));
    }

    private static float lattice(int seed, int x, int y, int z) {
        return hash01(seed ^ x * 73856093 ^ y * 19349663 ^ z * 83492791, 0);
    }

    private static int lavaRgb(float f, float l, float u, float time, int salt) {
        float field = Mth.sin(f * 3.7f + l * 4.3f + time * 0.70f)
                + 0.65f * Mth.sin(f * 8.1f - l * 2.8f - u * 5.0f + time * 1.4f)
                + 0.35f * Mth.sin(l * 13.7f + u * 9.0f + (salt & 15));
        float crust = Mth.sin(f * 2.1f - l * 3.4f + (salt & 31) * 0.17f)
                + Mth.sin(l * 5.2f + u * 4.3f);
        if (crust > 1.05f && u < 0.88f) return LAVA[1 + (salt & 2)];
        if (field > 1.45f) return LAVA[12 + (salt & 1)];
        if (field > 0.85f) return LAVA[10 + (salt & 1)];
        if (field > -0.10f) return LAVA[7 + (salt % 3 + 3) % 3];
        if (field > -0.90f) return LAVA[4 + (salt & 2)];
        return LAVA[2 + (salt & 1)];
    }

    private static int lavaDropletRgb(float h) {
        return h > 0.80f ? LAVA[13] : h > 0.52f ? LAVA[11] : h > 0.20f ? LAVA[8] : LAVA[5];
    }

    private static int fireRamp(float u, boolean core) {
        if (u < 0.14f) return core ? LAVA[13] : LAVA[10];
        if (u < 0.38f) return core ? LAVA[11] : LAVA[8];
        if (u < 0.70f) return core ? LAVA[9] : LAVA[7];
        if (u < 0.90f) return core ? LAVA[7] : LAVA[5];
        return core ? LAVA[6] : LAVA[3];
    }

    private static int soulRamp(float u, boolean core) {
        if (u < 0.14f) return core ? soulColor(10) : soulColor(8);
        if (u < 0.40f) return core ? soulColor(8) : soulColor(6);
        if (u < 0.72f) return core ? soulColor(6) : soulColor(4);
        return core ? soulColor(4) : soulColor(1);
    }

    private static int soulColor(int index) {
        return SOUL_COLORS[Mth.clamp(index, 0, SOUL_COLORS.length - 1)];
    }

    private static final float METEOR_FLIGHT_SECONDS = 1.408f;

    /** Trajectoire droite stable : le côté du ciel dépend de la graine de l'instance, pas du tick. */
    private static float meteorF(float q, int salt) {
        float angle = hash01(salt, 9100) * (float) (Math.PI * 2.0);
        float distance = 3.20f + 1.40f * hash01(salt, 9101);
        return Mth.cos(angle) * distance * (1.0f - q);
    }

    private static float meteorL(float q, int salt) {
        float angle = hash01(salt, 9100) * (float) (Math.PI * 2.0);
        float distance = 3.20f + 1.40f * hash01(salt, 9101);
        return Mth.sin(angle) * distance * (1.0f - q);
    }

    private static float meteorY(float q) {
        return 0.12f + 13.50f * (1.0f - q);
    }

    private static void smoke(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float f, float ground, float l, float t, float start,
                              int count, float S, int alpha, int salt, float spread) {
        for (int p = 0; p < count; p++) {
            float age = t - start - (p % 8) * 0.017f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 67 + 3200);
            boolean large = (p & 3) == 0;
            float lifetime = large ? 0.12f + 0.16f * h : 0.24f + 0.16f * h;
            float life = Mth.clamp(age / lifetime, 0.0f, 1.0f);
            if (!large && life >= 1.0f) continue;

            if (!large || life < 1.0f) {
                float fade = (1.0f - smooth01((life - 0.45f) / 0.55f)) * smooth01(age / 0.04f);
                float angle = p * 2.3999632f + life * 1.8f;
                float radius = (0.16f + h * spread + life * 0.38f) * S;
                float pf = f + Mth.cos(angle) * radius + life * 0.25f * S;
                float pl = l + Mth.sin(angle) * radius;
                float py = ground + (0.28f + h * 0.48f + life * 1.65f) * S;
                float size = (0.095f + h * 0.115f + life * 0.075f) * S;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                        size * 1.15f, size * 0.82f, size,
                        large ? ROCK_HOT : SMOKE, Math.round(alpha * fade * 0.32f));
            }
            if (!large) continue;

            // Le petit résidu survit au gros puff orange et s'éteint à son propre seuil aléatoire.
            float residualLife = Mth.clamp(age / (0.30f + 0.16f * h), 0.0f, 1.0f);
            if (residualLife >= 1.0f) continue;
            float residualFade = (1.0f - smooth01((residualLife - 0.45f) / 0.55f))
                    * smooth01(age / 0.04f);
            float angle = p * 2.3999632f + residualLife * 1.8f;
            float radius = (0.16f + h * spread + residualLife * 0.38f) * S;
            float pf = f + Mth.cos(angle) * radius + residualLife * 0.25f * S;
            float pl = l + Mth.sin(angle) * radius;
            float py = ground + (0.28f + h * 0.48f + residualLife * 1.65f) * S;
            float size = (0.095f + h * 0.115f + residualLife * 0.075f) * S;
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                    pf + size * 0.55f, py - size * 0.30f, pl,
                    size * 0.62f, size * 0.55f, size * 0.65f,
                    SMOKE, Math.round(alpha * residualFade * 0.24f));
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

    private static void localCube(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float f, float y, float l, float half, int rgb, int alpha) {
        localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l, half, half, half, rgb, alpha);
    }

    /** Coordonnées locales : x = attaque, z = latéral. Le voxel garde ses dimensions libres. */
    private static void localBox(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f, float y, float l, float hf, float hy, float hl,
                                 int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float sideX = -dz, sideZ = dx;
        float x = cx + dx * f + sideX * l;
        float z = cz + dz * f + sideZ * l;
        float yaw = Mth.atan2(-dz, dx);
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, y, z, hf, hy, hl,
                yaw, 0.0f, 0.0f, rgb, alpha);
    }
}
