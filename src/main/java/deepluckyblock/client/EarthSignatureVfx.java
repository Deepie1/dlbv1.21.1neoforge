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

/** Trois chorégraphies Terre : rupture tectonique, tempête de sable et sceau de ralliement. */
public final class EarthSignatureVfx {
    public static final int SEISMIC = 0;
    public static final int SANDSTORM = 1;
    public static final int RALLY = 2;

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
            renderSeismic(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        } else if (variant == SANDSTORM) {
            renderSandstorm(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        } else {
            renderRally(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt, terrain);
        }
    }

    /**
     * SEISMIC : rupture tectonique asymétrique. La chronologie n'est pas une sinusoïde :
     * anticipation, rupture sèche, propagation à vitesse décroissante, répliques amorties puis
     * sédimentation. Techniques étudiées dans Monumenta (retards par fronts), Arena-Brawl (poses
     * interpolées et ground sampling), MutantBeasts/Goety (progression par terrain), Fire/Poison
     * (colliders, volumes à cavités et collisions balistiques). Géométrie entièrement procédurale.
     */
    private static void renderSeismic(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float birth = smooth(t / 0.035f);
        float exit = smooth((t - 0.87f) / 0.13f);
        int a = Math.round(alpha * birth * (1.0f - exit));

        // Quelques graviers tremblent AVANT la rupture, avec une fréquence qui décroît brutalement.
        float anticipation = 1.0f - smooth((t - 0.055f) / 0.035f);
        if (anticipation > 0.0f) {
            for (int p = 0; p < 22; p++) {
                float h = hash(salt, 5000 + p * 41);
                float angle = p * 2.3999632f;
                float radius = 0.18f + h * 0.85f;
                float f = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
                float jitter = Mth.sin(t * 145.0f + p * 2.7f) * 0.022f * anticipation;
                float y = terrain.at(f, l) + 0.035f + Math.abs(jitter) * 1.8f;
                cube(vc, mat, tmp, cx, y, cz, dx, dz, f + jitter, l - jitter * 0.6f,
                        0.018f + h * 0.020f, p % 4 == 0 ? SOIL : STONE_DARK,
                        Math.round(a * anticipation * 0.78f));
            }
        }

        // La faille est une tranchée irrégulière orientée, pas une étoile de droites parfaites.
        renderMainFault(vc, mat, tmp, cx, cz, dx, dz, t, a, salt, terrain);

        // Six masses rocheuses distinctes naissent le long de la faille. Leur peau 3D érodée,
        // leurs strates et leur pivot commun évitent complètement la rangée de plaques rectangulaires.
        for (int chunk = 0; chunk < 6; chunk++) {
            float h = hash(salt, 5200 + chunk * 53);
            float f = -0.62f + chunk * 0.64f + (h - 0.5f) * 0.20f;
            float side = chunk % 2 == 0 ? -1.0f : 1.0f;
            float l = faultLateral(f, salt) + side * (0.10f + 0.14f * h);
            float start = 0.074f + chunk * 0.027f + h * 0.018f;
            float local = clamp((t - start) / (0.15f + h * 0.055f));
            if (local <= 0.0f) continue;
            float rise = outBack(local);
            float collapse = easeIn((t - 0.71f - chunk * 0.009f) / 0.19f);
            float afterAge = Math.max(0.0f, t - start - 0.20f);
            float aftershock = damped(afterAge, 37.0f, 13.0f) * 0.075f
                    + damped(Math.max(0.0f, t - 0.48f - chunk * 0.006f), 43.0f, 18.0f) * 0.035f;
            float ground = terrain.at(f, l);
            float y = ground - 0.18f + rise * (0.20f + h * 0.30f)
                    + aftershock - collapse * 0.36f;
            float tilt = side * (0.18f + h * 0.42f) * rise
                    + damped(afterAge, 29.0f, 11.0f) * 0.10f;
            float yaw = (hash(salt, 5210 + chunk * 59) - 0.5f) * 1.35f;
            float cell = 0.075f + h * 0.065f;
            float chunkAlpha = (1.0f - smooth((t - 0.82f) / 0.16f));
            roughTectonicChunk(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                    cell, yaw, tilt, salt + 5300 + chunk * 211,
                    Math.round(a * chunkAlpha), terrain);

            // Chaque rupture chasse un nuage latéral, après le soulèvement et non en boucle fixe.
            for (int puff = 0; puff < 3; puff++) {
                float age = t - start - 0.035f - puff * 0.018f;
                if (age <= 0.0f) continue;
                float life = clamp(age / (0.22f + h * 0.08f));
                if (life >= 1.0f) continue;
                float spread = side * (0.18f + life * (0.48f + puff * 0.15f));
                float pf = f + (puff - 1) * 0.15f + life * 0.18f;
                float pl = l + spread;
                float py = terrain.at(pf, pl) + 0.11f + life * 0.24f;
                billow(vc, mat, tmp, cx, cz, dx, dz, pf, py, pl,
                        0.16f + life * 0.24f, 0.58f, t * 5.0f + puff,
                        salt + 5600 + chunk * 71 + puff, STONE_DARK, DUST,
                        Math.round(a * smooth(age / 0.035f) * (1.0f - life) * 0.42f),
                        puff == 0 ? 2 : 1, terrain);
            }
        }

        // Front de pression : uniquement poussière et gravillons, irrégulier et incomplet.
        float pressure = easeOut((t - 0.082f) / 0.34f);
        if (pressure > 0.0f && pressure < 1.0f) {
            for (int i = 0; i < 104; i++) {
                float h = hash(salt, 5900 + i * 37);
                if (h < 0.30f) continue;
                float angle = i * 2.3999632f;
                float radius = (0.30f + pressure * 3.45f)
                        * (0.82f + 0.24f * noise1(angle * 1.7f + pressure * 2.0f, salt + 5901));
                float f = Mth.cos(angle) * radius + pressure * 0.35f;
                float l = Mth.sin(angle) * radius * (0.84f + h * 0.20f);
                float y = terrain.at(f, l) + 0.045f + h * 0.055f;
                float size = 0.025f + h * 0.050f;
                rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                        size * 1.6f, size * 0.45f, size, angle, h * 0.3f, 0.0f,
                        i % 5 == 0 ? DUST : STONE_LIGHT,
                        Math.round(a * (1.0f - pressure) * (0.18f + h * 0.24f)));
            }
        }

        // Débris issus des différents points de rupture, avec gravité, contact, rebond et repos.
        for (int p = 0; p < 96; p++) {
            float h = hash(salt, 6200 + p * 43);
            float originF = -1.85f + hash(salt, 6201 + p * 47) * 4.55f;
            float side = p % 2 == 0 ? -1.0f : 1.0f;
            float originL = faultLateral(originF, salt) + side * 0.08f;
            float launch = 0.082f + (originF + 1.85f) * 0.036f
                    + hash(salt, 6202 + p * 53) * 0.065f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float time = age * 5.6f;
            float vf = (hash(salt, 6203 + p * 59) - 0.35f) * (0.45f + h * 0.85f);
            float vl = side * (0.35f + h * 1.25f);
            float vy = 0.70f + 2.05f * hash(salt, 6204 + p * 61);
            float gravity = 7.4f;
            float originY = terrain.at(originF, originL) + 0.06f;
            float f = originF + vf * time, l = originL + vl * time;
            float landing = terrain.at(f, l);
            float hit = (vy + Mth.sqrt(Math.max(0.0f, vy * vy
                    + 2.0f * gravity * (originY - landing)))) / gravity;
            float hitF = originF + vf * hit, hitL = originL + vl * hit;
            landing = terrain.at(hitF, hitL);
            boolean large = p % 8 == 0;
            float size = large ? 0.075f + h * 0.065f : 0.018f + h * 0.045f;
            float drawX, drawY, drawZ, spin;
            if (time < hit) {
                drawX = f; drawZ = l;
                drawY = originY + vy * time - 0.5f * gravity * time * time;
                spin = time * (2.0f + h * 4.0f);
            } else {
                float since = time - hit;
                if (since > 0.85f + h * 0.45f) continue;
                float slide = Math.min(since, 0.32f);
                drawX = hitF + vf * slide * 0.16f;
                drawZ = hitL + vl * slide * 0.16f;
                landing = terrain.at(drawX, drawZ);
                float bounce = since < 0.30f
                        ? Math.abs(Mth.sin(since * 19.0f)) * (float) Math.exp(-since * 9.0f) * 0.16f : 0.0f;
                drawY = landing + size * 0.70f + bounce;
                spin = hit * (2.0f + h * 4.0f) + slide * 1.4f;
            }
            float fade = time < hit ? 1.0f : 1.0f - smooth((time - hit - 0.62f) / 0.60f);
            rot(vc, mat, tmp, cx, drawY, cz, dx, dz, drawX, drawZ,
                    size, size * (0.55f + h * 0.35f), size * (0.75f + h * 0.45f),
                    spin + p, spin * 0.72f, spin * 0.41f,
                    p % 5 == 0 ? SOIL_DARK : (large ? STONE : STONE_LIGHT),
                    Math.round(a * fade * 0.90f));
            if (large) {
                cube(vc, mat, tmp, cx, drawY + size * 0.42f, cz, dx, dz,
                        drawX + size * 0.65f, drawZ - size * 0.35f, size * 0.52f,
                        STONE_DARK, Math.round(a * fade * 0.82f));
            }
        }

        // Deux répliques tardives rouvrent partiellement quelques branches et soulèvent la poussière.
        for (int echo = 0; echo < 2; echo++) {
            float start = 0.43f + echo * 0.16f;
            float age = t - start;
            if (age <= 0.0f || age > 0.22f) continue;
            float pulse = damped(age, 42.0f, 17.0f);
            for (int p = 0; p < 9; p++) {
                float f = -1.50f + p * 0.42f;
                float l = faultLateral(f, salt) + (p % 2 == 0 ? -0.32f : 0.32f);
                float y = terrain.at(f, l) + 0.09f + Math.abs(pulse) * 0.12f;
                billow(vc, mat, tmp, cx, cz, dx, dz, f, y, l,
                        0.13f + Math.abs(pulse) * 0.08f, 0.48f, t * 7.0f + p,
                        salt + 6800 + echo * 300 + p, SOIL_DARK, DUST,
                        Math.round(a * Math.abs(pulse) * 0.28f), 1, terrain);
            }
        }
    }

    /** Tranchée principale : deux lèvres indépendantes, parois verticales et fond sombre. */
    private static void renderMainFault(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cz, float dx, float dz,
                                        float t, int alpha, int salt, TerrainView terrain) {
        float close = smooth((t - 0.86f) / 0.14f);
        for (int step = 0; step < 40; step++) {
            float f = -2.55f + step * 0.145f;
            float reveal = smooth((t - 0.050f - step * 0.0032f) / 0.065f);
            if (reveal <= 0.0f) continue;
            float center = faultLateral(f, salt);
            float n = noise1(f * 2.7f, salt + 7001);
            float width = (0.018f + n * 0.065f + 0.11f * (float) Math.exp(-f * f * 0.42f))
                    * reveal * (1.0f - close * 0.82f);
            float y = terrain.at(f, center);
            float floorY = y - 0.18f * reveal * (1.0f - close);
            box(vc, mat, tmp, cx, floorY, cz, dx, dz, f, center,
                    0.085f, 0.025f, Math.max(0.012f, width * 1.25f), FAULT,
                    Math.round(alpha * reveal * (1.0f - close * 0.55f) * 0.94f));
            for (int side = -1; side <= 1; side += 2) {
                float bankL = center + side * (width + 0.035f);
                float bankY = terrain.at(f, bankL);
                float wallHeight = 0.07f + reveal * (0.08f + n * 0.07f);
                box(vc, mat, tmp, cx, bankY - wallHeight * 0.45f, cz, dx, dz, f, bankL,
                        0.080f, wallHeight, 0.025f + width * 0.25f,
                        side < 0 ? STONE_DARK : SOIL_DARK,
                        Math.round(alpha * reveal * (1.0f - close * 0.65f) * 0.88f));
                // Une lèvre sur deux possède une croûte plus claire, jamais un liseré magique symétrique.
                if ((step + side) % 3 != 0) {
                    box(vc, mat, tmp, cx, bankY + 0.012f, cz, dx, dz,
                            f, bankL + side * 0.035f, 0.078f, 0.014f, 0.030f,
                            step % 5 == 0 ? CLAY : STONE_LIGHT,
                            Math.round(alpha * reveal * (1.0f - close) * 0.64f));
                }
            }
        }

        // Les branches naissent de la faille principale avec des angles et des longueurs variés.
        for (int branch = 0; branch < 18; branch++) {
            float h = hash(salt, 7100 + branch * 43);
            float startF = -2.15f + h * 4.70f;
            float side = branch % 2 == 0 ? -1.0f : 1.0f;
            float startL = faultLateral(startF, salt);
            float reveal = smooth((t - 0.10f - (startF + 2.15f) * 0.025f
                    - hash(salt, 7101 + branch * 47) * 0.08f) / 0.15f);
            float decay = smooth((t - 0.80f - (branch % 4) * 0.018f) / 0.18f);
            faultBranch(vc, mat, tmp, cx, cz, dx, dz, startF, startL, side,
                    0.45f + h * 1.05f, reveal, decay, salt + 7200 + branch * 89,
                    Math.round(alpha * (0.52f + h * 0.35f)), terrain);
        }
    }

    private static void faultBranch(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cz, float dx, float dz,
                                    float startF, float startL, float side, float length,
                                    float reveal, float decay, int seed, int alpha, TerrainView terrain) {
        if (reveal <= 0.0f || alpha <= 4) return;
        float f = startF, l = startL;
        float directionF = (hash(seed, 1) - 0.5f) * 0.85f;
        float directionL = side * (0.70f + hash(seed, 2) * 0.55f);
        int visible = Math.round((7 + Math.round(hash(seed, 3) * 6)) * reveal);
        for (int step = 1; step <= visible; step++) {
            float turn = (noise1(step * 0.83f, seed) - 0.5f) * 0.65f;
            float stride = length / 11.0f;
            f += (directionF + turn) * stride;
            l += (directionL - turn * side * 0.45f) * stride;
            float y = terrain.at(f, l) + 0.018f;
            float taper = 1.0f - step / 15.0f;
            float size = (0.022f + 0.028f * hash(seed, step * 31)) * taper * (1.0f - decay * 0.72f);
            float angle = (float) Math.atan2(directionF + turn, directionL);
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    size * 1.55f, 0.010f, Math.max(0.008f, size * 0.48f),
                    angle, 0.0f, 0.0f, FAULT,
                    Math.round(alpha * reveal * (1.0f - decay * 0.70f)));
            if (step % 4 == 0) {
                box(vc, mat, tmp, cx, y + 0.008f, cz, dx, dz,
                        f + side * size, l, size * 0.55f, 0.012f, size * 0.40f,
                        SOIL_DARK, Math.round(alpha * reveal * (1.0f - decay) * 0.60f));
            }
        }
    }

    /** Volume rocheux irrégulier : seule la peau est émise, les strates restent lisibles en coupe. */
    private static void roughTectonicChunk(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           float cx, float cz, float dx, float dz,
                                           float centerF, float centerY, float centerL,
                                           float cell, float yaw, float tilt,
                                           int seed, int alpha, TerrainView terrain) {
        float cyaw = Mth.cos(yaw), syaw = Mth.sin(yaw);
        float ct = Mth.cos(tilt), st = Mth.sin(tilt);
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 6; y++) for (int z = -3; z <= 3; z++) {
            if (!rockSolid(x, y, z, seed) || !rockSkin(x, y, z, seed)) continue;
            float h = hash(seed, x * 733 + y * 379 + z * 191);
            float lx = x * cell, ly = y * cell, lz = z * cell;
            float tiltedX = lx * ct - ly * st;
            float tiltedY = lx * st + ly * ct;
            float rf = tiltedX * cyaw - lz * syaw;
            float rl = tiltedX * syaw + lz * cyaw;
            float f = centerF + rf, l = centerL + rl;
            float py = centerY + tiltedY;
            if (terrain.blocked(f, py, l)) continue;
            int rgb;
            int top = 3 + Mth.floor(hash(seed, 17) * 4.0f);
            if (y >= top - 1 && h > 0.30f) rgb = h > 0.94f ? STONE_LIGHT : SOIL;
            else if (y <= 1) rgb = h > 0.55f ? SOIL_DARK : STONE_DARK;
            else if (y == 3 && h > 0.40f) rgb = CLAY;
            else rgb = h > 0.72f ? STONE_LIGHT : (h < 0.24f ? STONE_DARK : STONE);
            float half = cell * (0.53f + h * 0.08f);
            rot(vc, mat, tmp, cx, py, cz, dx, dz, f, l,
                    half, half * (0.88f + h * 0.18f), half,
                    yaw + h * 0.08f, tilt, tilt * 0.35f, rgb,
                    Math.round(alpha * (0.82f + h * 0.16f)));
        }
    }

    private static boolean rockSkin(int x, int y, int z, int seed) {
        return !rockSolid(x + 1, y, z, seed) || !rockSolid(x - 1, y, z, seed)
                || !rockSolid(x, y + 1, z, seed) || !rockSolid(x, y - 1, z, seed)
                || !rockSolid(x, y, z + 1, seed) || !rockSolid(x, y, z - 1, seed);
    }

    private static boolean rockSolid(int x, int y, int z, int seed) {
        if (y < 0 || y > 6 || Math.abs(x) > 3 || Math.abs(z) > 3) return false;
        float ny = y / 6.0f;
        float shift = Mth.sin(y * 1.17f + (seed & 15)) * 0.42f;
        float nx = (x - shift) / 3.15f;
        float nz = (z + shift * 0.45f) / 3.05f;
        float taper = 0.76f + hash(seed, 23) * 0.30f - ny * 0.56f;
        float erosion = (hash(seed, x * 73856093 ^ y * 19349663 ^ z * 83492791) - 0.5f) * 0.32f;
        float shape = nx * nx * (0.90f + ny * 0.32f) + nz * nz * (1.05f - ny * 0.18f);
        int maxHeight = 3 + Mth.floor(hash(seed, 17) * 4.0f);
        return shape < taper + erosion && y <= maxHeight - Mth.floor(Math.abs(nx + nz) * 2.0f);
    }

    private static float faultLateral(float f, int salt) {
        float phase = (salt & 63) * 0.17f;
        return Mth.sin(f * 1.62f + phase) * 0.24f
                + Mth.sin(f * 4.35f + phase * 0.63f) * 0.085f
                + (noise1(f * 2.15f, salt + 7000) - 0.5f) * 0.16f;
    }

    private static float noise1(float x, int seed) {
        int i = Mth.floor(x);
        float u = smooth(x - i);
        float a = hash(seed, i * 73856093);
        float b = hash(seed, (i + 1) * 73856093);
        return a + (b - a) * u;
    }

    private static float easeOut(float x) {
        x = clamp(x);
        return 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x);
    }

    private static float easeIn(float x) {
        x = clamp(x);
        return x * x * x;
    }

    private static float outBack(float x) {
        x = clamp(x);
        float c = 1.70158f;
        float u = x - 1.0f;
        return 1.0f + (c + 1.0f) * u * u * u + c * u * u;
    }

    private static float damped(float age, float frequency, float decay) {
        if (age <= 0.0f) return 0.0f;
        return Mth.sin(age * frequency) * (float) Math.exp(-age * decay);
    }

    /** SANDSTORM : dune migratrice, vortex à trois bras, nappes de vent et retombée des grains. */
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

    /** RALLY : cercle runique, étendards montants, glaive central et chevrons de commandement. */
    private static void renderRally(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt, TerrainView terrain) {
        float birth = smooth(t / 0.065f);
        float exit = smooth((t - 0.83f) / 0.17f);
        float env = birth * (1.0f - exit);
        int a = Math.round(alpha * env);
        float circle = smooth((t - 0.025f) / 0.24f);
        float radius = 0.35f + 1.45f * circle;

        // Double cercle runique ancré sur le sol ; les segments vides sont volontaires.
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < 72; i++) {
            if ((i + ring * 2) % (ring == 0 ? 8 : 11) == 0) continue;
            float angle = i * (float) (Math.PI * 2.0 / 72.0) + ring * 0.04f;
            float r = radius - ring * 0.17f;
            float f = Mth.cos(angle) * r, l = Mth.sin(angle) * r;
            float y = terrain.at(f, l) + 0.030f;
            rot(vc, mat, tmp, cx, y, cz, dx, dz, f, l,
                    0.070f, 0.014f, 0.026f, angle, 0.0f, 0.0f,
                    ring == 0 ? GOLD : EARTH, Math.round(a * circle * 0.80f));
        }

        // Huit glyphes angulaires, chacun formé de trois traits plats et non d'un point générique.
        for (int rune = 0; rune < 8; rune++) {
            float reveal = smooth((t - 0.10f - rune * 0.014f) / 0.16f);
            if (reveal <= 0.0f) continue;
            float angle = rune * (float) (Math.PI / 4.0);
            float f = Mth.cos(angle) * 1.25f, l = Mth.sin(angle) * 1.25f;
            groundRune(vc, mat, tmp, cx, cz, dx, dz, f, l, angle + t * 0.20f,
                    0.16f * reveal, rune % 2 == 0 ? EARTH_LIGHT : GOLD,
                    Math.round(a * reveal), terrain);
        }

        // Emblème central : deux glaives croisés au sol, encadrés par un carré brisé.
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
                    0.018f, EARTH, Math.round(a * emblem * 0.68f), terrain);
        }

        // Quatre étendards montent séquentiellement ; le tissu ondule en cubes articulés.
        for (int banner = 0; banner < 4; banner++) {
            float reveal = smooth((t - 0.17f - banner * 0.035f) / 0.22f);
            if (reveal <= 0.0f) continue;
            float angle = banner * 1.5707963f;
            float f = Mth.cos(angle) * 1.45f, l = Mth.sin(angle) * 1.45f;
            float ground = terrain.at(f, l);
            float height = 1.42f * reveal;
            for (int part = 0; part < 9; part++) {
                float u = part / 8.0f;
                if (u > reveal) continue;
                cube(vc, mat, tmp, cx, ground + 0.08f + u * height, cz, dx, dz, f, l,
                        0.035f, part == 8 ? GOLD : STONE_LIGHT, Math.round(a * 0.90f));
            }
            for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) {
                float u = col / 4.0f;
                float wave = Mth.sin(t * 10.0f + col * 0.85f + banner) * 0.045f * reveal;
                float clothF = f + Mth.cos(angle + 1.5707963f) * (0.10f + col * 0.12f);
                float clothL = l + Mth.sin(angle + 1.5707963f) * (0.10f + col * 0.12f);
                float y = ground + height - 0.12f - row * 0.115f + wave;
                int rgb = row == 0 || col == 4 ? GOLD : ((row + col) % 5 == 0 ? EARTH : BANNER);
                rot(vc, mat, tmp, cx, y, cz, dx, dz, clothF, clothL,
                        0.070f, 0.065f, 0.022f, angle, wave * 3.0f, wave * 2.0f,
                        rgb, Math.round(a * reveal * (0.80f + u * 0.15f)));
            }
        }

        // Glaive de commandement central : lame claire, garde dorée et pommeau terrestre.
        float swordRise = smooth((t - 0.25f) / 0.24f);
        float ground = terrain.at(0.0f, 0.0f);
        float swordBase = ground - 0.35f + swordRise * 0.45f;
        for (int part = 0; part < 10; part++) {
            float u = part / 9.0f;
            float half = part > 7 ? 0.065f - (part - 7) * 0.016f : 0.065f;
            cube(vc, mat, tmp, cx, swordBase + 0.40f + u * 1.05f, cz, dx, dz, 0.0f, 0.0f,
                    Math.max(0.022f, half), part % 3 == 0 ? EARTH_LIGHT : STONE_LIGHT,
                    Math.round(a * swordRise * 0.92f));
        }
        for (int guard = -3; guard <= 3; guard++) {
            cube(vc, mat, tmp, cx, swordBase + 0.37f, cz, dx, dz,
                    0.0f, guard * 0.085f, 0.045f, GOLD, Math.round(a * swordRise));
        }
        for (int grip = 0; grip < 4; grip++) {
            cube(vc, mat, tmp, cx, swordBase + 0.27f - grip * 0.10f, cz, dx, dz,
                    0.0f, 0.0f, 0.042f, grip == 3 ? EARTH : SOIL_DARK, Math.round(a * swordRise));
        }

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

        // Motes de moral ascendants : ils apparaissent après le sceau et s'envolent en spirale.
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
