package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Trois chorégraphies Poison en voxels : morsure de ronces, miasme vivant et nova de dards. */
public final class PoisonSignatureVfx {
    public static final int VENOM = 0;
    public static final int MIASMA = 1;
    public static final int PLAGUE = 2;

    private static final int DEEP = 0x061A0C;
    private static final int STEM = 0x0A3D16;
    private static final int BODY = 0x1E7A32;
    private static final int ACID = 0x63E05C;
    private static final int TIP = 0xB6FF86;

    private PoisonSignatureVfx() { }

    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001f) { dirX = 0.0f; dirZ = 1.0f; }
        else { dirX /= length; dirZ /= length; }
        if (variant == VENOM) renderVenom(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt);
        else if (variant == MIASMA) renderMiasma(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt);
        else renderPlague(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, alpha, salt);
    }

    /** Deux ronces construisent les mâchoires ; la morsure réelle a lieu vers t=0,41. */
    private static void renderVenom(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        float birth = smooth(t / 0.16f);
        float bite = smooth((t - 0.20f) / 0.23f);
        float retreat = smooth((t - 0.80f) / 0.20f);
        float env = birth * (1.0f - retreat);
        float ground = cy - 0.56f;

        for (int jaw = 0; jaw < 2; jaw++) {
            float sign = jaw == 0 ? 1.0f : -1.0f;
            float jawY = cy + sign * (0.42f + 0.65f * (1.0f - bite)) * env;
            // Arcade de la gencive : une structure continue, jamais des crocs flottant seuls.
            for (int tooth = 0; tooth < 9; tooth++) {
                float lateral = (tooth - 4) * 0.235f;
                float arch = 0.12f * (1.0f - Math.abs(lateral));
                float forward = -0.10f + arch;
                float h = 0.095f + 0.018f * hash(salt, jaw * 71 + tooth);
                localCube(vc, mat, tmp, cx, jawY, cz, dx, dz, forward, 0.0f, lateral,
                        h, color(STEM, BODY, tooth / 8.0f), Math.round(alpha * env));

                float length = (tooth == 4 ? 0.84f : (tooth == 3 || tooth == 5 ? 0.68f : 0.47f))
                        * (0.35f + 0.65f * birth);
                float fx = forward + 0.14f;
                float rootY = jawY - sign * 0.04f;
                shard(vc, mat, tmp, cx, rootY, cz, dx, dz, fx, lateral,
                        length, 0.040f, -sign, BODY, TIP,
                        Math.round(alpha * env), salt + jaw * 97 + tooth * 13);
            }

            // Deux ronces porteuses par mâchoire, révélées progressivement depuis le sol.
            for (int side = 0; side < 2; side++) {
                float lateral = side == 0 ? -1.03f : 1.03f;
                int segments = 18;
                int visible = Math.max(1, Math.round(segments * birth));
                float prevF = -1.42f, prevY = ground, prevL = lateral * 1.32f;
                for (int k = 1; k <= visible; k++) {
                    float u = k / (float) segments;
                    float f = Mth.lerp(u, -1.42f, -0.10f)
                            + 0.18f * Mth.sin(u * 5.4f + jaw * 1.7f) * Mth.sin(u * 3.14159f);
                    float y = Mth.lerp(u, ground, jawY)
                            + 0.16f * Mth.sin(u * 3.14159f) * sign;
                    float l = Mth.lerp(u, lateral * 1.32f, lateral)
                            + 0.11f * Mth.sin(u * 6.0f + side) * Mth.sin(u * 3.14159f);
                    orientedLink(vc, mat, tmp, cx, cy, cz, dx, dz,
                            prevF, prevY, prevL, f, y, l, 0.050f * (1.0f - 0.35f * u),
                            color(DEEP, BODY, u), Math.round(alpha * env));
                    if (k % 4 == 0) {
                        localCube(vc, mat, tmp, cx, y, cz, dx, dz,
                                f + 0.08f, 0.04f * sign, l + (side == 0 ? -0.08f : 0.08f),
                                0.035f, ACID, Math.round(alpha * env * 0.90f));
                    }
                    prevF = f; prevY = y; prevL = l;
                }
            }
        }

        // Venin : gouttes qui partent des crocs après la morsure et accélèrent vers le sol.
        for (int d = 0; d < 10; d++) {
            float launch = 0.49f + (d % 5) * 0.035f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float time = age * 4.2f;
            float lateral = (d % 5 - 2) * 0.30f;
            float f = 0.05f + (hash(salt, d * 31) - 0.5f) * 0.18f;
            float y = cy + 0.10f - 0.28f * time - 0.90f * time * time;
            if (y < ground) continue;
            float size = 0.020f + 0.012f * hash(salt, d * 43);
            localCube(vc, mat, tmp, cx, y, cz, dx, dz, f, 0.0f, lateral,
                    size, d % 3 == 0 ? TIP : ACID, Math.round(alpha * (1.0f - retreat)));
        }
        acidPool(vc, mat, tmp, cx, ground + 0.018f, cz, dx, dz,
                0.12f, 0.0f, smooth((t - 0.52f) / 0.28f), 0.92f, alpha, salt + 500);
    }

    /** Un miasme continu qui avance, roule sur lui-même, enveloppe puis se disperse. */
    private static void renderMiasma(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt) {
        float birth = smooth(t / 0.13f);
        float exit = smooth((t - 0.80f) / 0.20f);
        float env = birth * (1.0f - exit);
        float spread = smooth((t - 0.08f) / 0.48f);
        float ground = cy - 0.56f;

        // Nappe rampante reliée : les volumes se chevauchent et suivent une advection avant.
        for (int b = 0; b < 13; b++) {
            float h = hash(salt, b * 37);
            float f = -1.65f + b * 0.275f + 0.72f * spread;
            float lateral = 0.52f * Mth.sin(b * 0.83f + t * 2.0f) + (h - 0.5f) * 0.30f;
            float y = ground + 0.14f + 0.10f * Mth.sin(t * 6.0f + b * 0.72f);
            float radius = (0.34f + 0.13f * h) * (0.55f + 0.65f * spread);
            billow(vc, mat, tmp, cx, y, cz, dx, dz, f, lateral, radius, 0.58f,
                    t * 2.2f + b, salt + b * 101, Math.round(alpha * env * 0.52f));
        }

        // Le voile principal s'enroule autour de la cible ; il ne forme pas une sphère de bulles.
        for (int b = 0; b < 9; b++) {
            float h = hash(salt, 700 + b * 41);
            float angle = b * 0.698f + t * 2.4f;
            float radius = (0.28f + 0.64f * spread) * (0.82f + 0.24f * h);
            float f = Mth.cos(angle) * radius + 0.25f * spread;
            float lateral = Mth.sin(angle) * radius;
            float y = cy - 0.18f + b * 0.105f + 0.12f * Mth.sin(t * 5.0f + b);
            float billowRadius = (b == 0 ? 0.56f : 0.40f + 0.13f * h)
                    * (0.64f + 0.48f * birth) * (1.0f + 0.32f * exit);
            billow(vc, mat, tmp, cx, y, cz, dx, dz, f, lateral, billowRadius, 0.90f,
                    t * 3.0f + b * 0.7f, salt + 900 + b * 131,
                    Math.round(alpha * env * (b == 0 ? 0.68f : 0.58f)));
        }

        // Spores issues du voile : trajectoires ascendantes en spirale, pas de pluie aléatoire.
        for (int s = 0; s < 18; s++) {
            float h = hash(salt, 1200 + s * 47);
            float reveal = smooth((t - 0.18f - (s % 6) * 0.025f) / 0.18f) * (1.0f - exit);
            if (reveal <= 0.0f) continue;
            float angle = s * 0.83f + t * (1.4f + h);
            float radius = 0.38f + 0.74f * spread + 0.12f * Mth.sin(t * 4.0f + s);
            float f = Mth.cos(angle) * radius;
            float lateral = Mth.sin(angle) * radius;
            float y = ground + 0.24f + (0.35f + 1.25f * h) * t;
            float size = 0.018f + 0.016f * h;
            localRot(vc, mat, tmp, cx, y, cz, dx, dz, f, lateral,
                    size, size * 1.35f, size, angle, 0.35f, t,
                    s % 4 == 0 ? TIP : ACID, Math.round(alpha * reveal * 0.78f));
        }
    }

    /** Charge, impulsion, puis exactement dix dards sur des trajectoires balistiques distinctes. */
    private static void renderPlague(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, int alpha, int salt) {
        float ground = cy - 0.56f;
        float charge = smooth(t / 0.22f);
        float burst = smooth((t - 0.22f) / 0.10f);
        float exit = smooth((t - 0.86f) / 0.14f);

        // Cœur qui se contracte avant l'éclatement.
        if (t < 0.34f) {
            float core = (0.34f - 0.18f * burst) * charge;
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                if (x * x + y * y + z * z > 6) continue;
                float size = core * 0.42f;
                localCube(vc, mat, tmp, cx, cy + y * core * 0.42f, cz, dx, dz,
                        x * core * 0.42f, 0.0f, z * core * 0.42f, size,
                        y > 0 ? ACID : BODY, Math.round(alpha * (1.0f - burst)));
            }
        }

        // Onde de nova : elle n'existe qu'au moment de l'impulsion.
        if (burst > 0.0f && t < 0.52f) {
            float wave = smooth((t - 0.22f) / 0.30f);
            float radius = 0.28f + 2.80f * wave;
            for (int i = 0; i < 48; i++) {
                float angle = i * 0.1308997f;
                localCube(vc, mat, tmp, cx, ground + 0.045f, cz, dx, dz,
                        Mth.cos(angle) * radius, 0.0f, Mth.sin(angle) * radius,
                        0.040f * (1.0f - 0.55f * wave), i % 3 == 0 ? TIP : ACID,
                        Math.round(alpha * (1.0f - wave)));
            }
        }

        for (int i = 0; i < 10; i++) {
            float h = hash(salt, 1500 + i * 59);
            float angle = i * 0.62831853f + 0.10f;
            float launch = 0.245f + (i & 1) * 0.018f;
            float u = Mth.clamp((t - launch) / 0.59f, 0.0f, 1.0f);
            if (u <= 0.0f) continue;
            float range = 2.35f + 0.48f * h;
            float vy = 2.35f + 0.42f * h;
            // Chaque dard revient exactement au niveau du sol à u=1, quelle que soit sa portée.
            float gravity = 2.0f * (vy + 0.60f);
            float f = Mth.cos(angle) * (0.16f + range * u);
            float lateral = Mth.sin(angle) * (0.16f + range * u);
            float y = cy + 0.04f + vy * u - 0.5f * gravity * u * u;
            float df = Mth.cos(angle) * range;
            float dl = Mth.sin(angle) * range;
            float dy = vy - gravity * u;
            dart(vc, mat, tmp, cx, y, cz, dx, dz, f, lateral, df, dy, dl,
                    0.72f + 0.20f * h, BODY, TIP, Math.round(alpha * (1.0f - exit)), salt + i * 79);

            for (int trail = 1; trail <= 4; trail++) {
                float pu = Math.max(0.0f, u - trail * 0.045f);
                float tf = Mth.cos(angle) * (0.16f + range * pu);
                float tl = Mth.sin(angle) * (0.16f + range * pu);
                float ty = cy + 0.04f + vy * pu - 0.5f * gravity * pu * pu;
                localCube(vc, mat, tmp, cx, ty, cz, dx, dz, tf, 0.0f, tl,
                        0.022f - trail * 0.003f, trail == 1 ? ACID : BODY,
                        Math.round(alpha * (0.42f - trail * 0.065f) * (1.0f - exit)));
            }

            if (u >= 0.995f) {
                acidPool(vc, mat, tmp, cx, ground + 0.018f, cz, dx, dz,
                        Mth.cos(angle) * (0.16f + range), Mth.sin(angle) * (0.16f + range),
                        smooth((t - 0.84f) / 0.12f), 0.42f, alpha, salt + 1800 + i);
            }
        }
    }

    /** Volume de vapeur avec cavités internes et dérive cohérente des mailles. */
    private static void billow(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float cx, float cy, float cz, float dx, float dz,
                               float f, float lateral, float radius, float vertical,
                               float time, int seed, int alpha) {
        if (alpha <= 5) return;
        float cell = radius * 0.48f;
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            float nx = x / 2.15f, ny = y / 1.75f, nz = z / 2.15f;
            float distance = nx * nx + ny * ny + nz * nz;
            if (distance > 1.08f) continue;
            float h = hash(seed, x * 733 + y * 379 + z * 191);
            if (distance < 0.20f && h < 0.36f) continue;
            float drift = Mth.sin(time + x * 0.7f + z * 0.9f) * cell * 0.14f;
            float px = x * cell + drift;
            float py = y * cell * vertical + drift * 0.30f;
            float pz = z * cell - drift * 0.55f;
            float half = cell * (0.50f + 0.16f * h);
            int rgb = color(DEEP, BODY, Mth.clamp(0.18f + 0.15f * (y + 2) + 0.30f * h, 0.0f, 1.0f));
            if (h > 0.86f && y > 0) rgb = ACID;
            localRot(vc, mat, tmp, cx, cy + py, cz, dx, dz, f + px, lateral + pz,
                    half, half * (0.78f + 0.16f * h), half,
                    time * 0.16f + h, h * 0.40f, time * 0.10f, rgb,
                    Math.round(alpha * (0.50f + 0.42f * h) * (1.0f - distance * 0.20f)));
        }
    }

    private static void acidPool(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float centerF, float centerL, float reveal, float radius,
                                 int alpha, int seed) {
        if (reveal <= 0.0f) return;
        for (int i = 0; i < 14; i++) {
            float angle = i * 2.39996f;
            float r = radius * reveal * (0.25f + 0.75f * hash(seed, i * 31));
            float f = centerF + Mth.cos(angle) * r;
            float l = centerL + Mth.sin(angle) * r;
            float size = (0.045f + 0.025f * hash(seed, i * 47)) * reveal;
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, 0.0f, l,
                    size, 0.012f, size * 0.82f, i % 4 == 0 ? ACID : STEM,
                    Math.round(alpha * reveal * 0.62f));
        }
    }

    private static void shard(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float f, float lateral, float length, float half, float directionY,
                              int base, int tip, int alpha, int salt) {
        float startY = cy;
        float endY = cy + directionY * length;
        orientedLink(vc, mat, tmp, cx, cy, cz, dx, dz,
                f, startY, lateral, f + 0.10f, endY, lateral * 0.94f,
                half, base, alpha, tip);
        localCube(vc, mat, tmp, cx, endY, cz, dx, dz,
                f + 0.10f, 0.0f, lateral * 0.94f, half * 0.42f, tip,
                Math.round(alpha * 0.86f));
    }

    private static void dart(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float dx, float dz,
                             float f, float lateral, float vf, float vy, float vl,
                             float length, int base, int tip, int alpha, int salt) {
        float norm = Mth.sqrt(vf * vf + vy * vy + vl * vl);
        if (norm < 0.001f) return;
        float nf = vf / norm, ny = vy / norm, nl = vl / norm;
        for (int k = 0; k < 4; k++) {
            float u0 = -0.50f + k * 0.25f;
            float u1 = u0 + 0.24f;
            float size = 0.044f * (1.0f - k * 0.16f);
            orientedLink(vc, mat, tmp, cx, cy, cz, dx, dz,
                    f + nf * length * u0, cy + ny * length * u0, lateral + nl * length * u0,
                    f + nf * length * u1, cy + ny * length * u1, lateral + nl * length * u1,
                    size, k >= 2 ? tip : base, Math.round(alpha * (0.78f + 0.07f * k)), tip);
        }
    }

    private static void orientedLink(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float dx, float dz,
                                     float f0, float y0, float l0, float f1, float y1, float l1,
                                     float half, int rgb, int alpha) {
        orientedLink(vc, mat, tmp, cx, cy, cz, dx, dz, f0, y0, l0, f1, y1, l1, half, rgb, alpha, rgb);
    }

    private static void orientedLink(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float dx, float dz,
                                     float f0, float y0, float l0, float f1, float y1, float l1,
                                     float half, int rgb, int alpha, int tip) {
        float df = f1 - f0, dy = y1 - y0, dl = l1 - l0;
        float length = Mth.sqrt(df * df + dy * dy + dl * dl);
        if (length < 0.001f) return;
        float wx0 = dx * f0 - dz * l0, wz0 = dz * f0 + dx * l0;
        float wx1 = dx * f1 - dz * l1, wz1 = dz * f1 + dx * l1;
        float sx = wx1 - wx0, sz = wz1 - wz0;
        float yaw = (float) Math.atan2(sx, sz);
        float pitch = (float) Math.acos(Mth.clamp(dy / length, -1.0f, 1.0f));
        float f = (f0 + f1) * 0.5f, l = (l0 + l1) * 0.5f;
        localRot(vc, mat, tmp, cx, (y0 + y1) * 0.5f, cz, dx, dz, f, l,
                half, length * 0.56f, half, yaw, pitch, 0.0f,
                WeaponWorldVfxManager.mixRgb(rgb, tip, 0.32f), alpha);
    }

    private static void localCube(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float f, float yOffset, float lateral, float half, int rgb, int alpha) {
        localBox(vc, mat, tmp, cx, cy + yOffset, cz, dx, dz, f, 0.0f, lateral,
                half, half, half, rgb, alpha);
    }

    private static void localBox(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f, float yOffset, float lateral,
                                 float hf, float hy, float hl, int rgb, int alpha) {
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + yOffset, z, hf, hy, hl, rgb, alpha);
    }

    private static void localRot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f, float lateral, float hf, float hy, float hl,
                                 float yaw, float pitch, float roll, int rgb, int alpha) {
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy, z,
                hf, hy, hl, yaw, pitch, roll, rgb, alpha);
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
}
