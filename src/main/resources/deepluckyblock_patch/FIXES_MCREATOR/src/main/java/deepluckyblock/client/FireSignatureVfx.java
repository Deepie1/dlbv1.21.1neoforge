package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
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

    private FireSignatureVfx() { }

    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dirX, float dirZ, int variant, float scale,
                              int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f) return;
        if (variant == MOLTEN) renderMolten(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
        else if (variant == SOUL) renderSoul(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
        else renderMeteor(vc, mat, tmp, cx, cy, cz, t, dirX, dirZ, scale, alpha, salt);
    }

    /** Mini-tsunami : volume plein, crête en surplomb, texture de lave et splash balistique. */
    private static void renderMolten(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, float S, int alpha, int salt) {
        float exit = smooth01((t - 0.82f) / 0.18f);
        float env = 1.0f - exit;
        float ground = cy - 0.48f * S;
        float travel = waveTravel(t) * S;

        // Le profil (arrière -> face -> crête -> lèvre) est extrudé en VOLUME sur toute la largeur.
        for (int lane = -8; lane <= 8; lane++) {
            float lateral = lane * 0.165f * S;
            float noise = 0.90f + 0.08f * Mth.sin(lane * 1.37f + (salt & 31))
                    + 0.05f * Mth.sin(t * 4.0f + lane * 0.70f);
            for (int k = 0; k < 22; k++) {
                float u = k / 21.0f;
                float front = travel + waveFrontLocal(t, u, lane) * S;
                float thickness = (0.26f + 0.22f * (1.0f - u) + 0.62f * Mth.sin(u * (float) Math.PI)) * S;
                float back = front - thickness + 0.035f * Mth.sin(lane * 1.9f) * u * S;
                float center = (front + back) * 0.5f;
                float halfDepth = Math.max(0.055f * S, (front - back) * 0.5f);
                float y = ground + waveHeightLocal(t, u, noise) * S;
                float hy = Math.max(0.035f * S, 0.072f * noise * S * (1.0f - 0.55f * collapse(t)));
                float halfLane = 0.105f * S;
                int rgb = lavaRgb(center / S, lateral / S, u, t, salt + lane * 29 + k * 7);
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, center, y, lateral,
                        halfDepth, hy, halfLane, rgb, Math.round(alpha * env));

                // Texture cohérente sur la face : petits patchs posés sur les gros volumes.
                float detail = (0.035f + 0.030f * hash01(salt, lane * 91 + k * 17 + 40)) * S;
                float patchY = y + (hash01(salt, k * 41 + lane) - 0.5f) * hy;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        front + detail * 0.25f, patchY,
                        lateral + (hash01(salt, k + lane * 33) - 0.5f) * 0.07f * S,
                        detail * 0.52f, detail * 0.90f, detail * 1.35f,
                        lavaRgb(front / S + 0.4f, lateral / S, u + 0.2f, t + 0.17f, salt + k),
                        Math.round(alpha * env * 0.96f));
                if ((k + lane & 1) == 0) {
                    // La face arrière est aussi texturée : aucun mur uni lorsque la caméra orbite.
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            back - detail * 0.20f, patchY,
                            lateral + (hash01(salt, k * 73 + lane * 11) - 0.5f) * 0.06f * S,
                            detail * 0.60f, detail * 0.82f, detail * 1.40f,
                            lavaRgb(back / S - 0.3f, lateral / S, u + 0.1f, t + 0.30f, salt + 500 + k),
                            Math.round(alpha * env * 0.86f));
                    // Patch supérieur plus large et très plat : les zones sombres restent des zones.
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            center + halfDepth * 0.25f, y + hy * 0.92f, lateral,
                            Math.min(halfDepth * 0.55f, 0.13f * S), 0.018f * S, 0.070f * S,
                            lavaRgb(center / S, lateral / S + 0.3f, u, t, salt + 800 + k),
                            Math.round(alpha * env * 0.88f));
                }
            }

            // Deux rangées de flammes épaisses : crête principale et lèvre qui retombe.
            if ((lane & 1) == 0) {
                float u = 0.67f;
                float f = travel + waveFrontLocal(t, u, lane) * S;
                float y = ground + waveHeightLocal(t, u, noise) * S;
                flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, lateral,
                        (0.48f + 0.30f * hash01(salt, lane + 100)) * (1.0f - collapse(t)),
                        0.080f + 0.022f * hash01(salt, lane + 120),
                        0.52f, 0.10f * Mth.sin(lane), t * 9.0f + lane,
                        false, S, Math.round(alpha * env), salt + lane * 43);
            }
            if ((lane + 8) % 3 == 0) {
                float f = travel + waveFrontLocal(t, 1.0f, lane) * S;
                float y = ground + waveHeightLocal(t, 1.0f, noise) * S;
                flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, lateral,
                        0.34f * (1.0f - collapse(t)), 0.065f,
                        -0.20f, 0.0f, t * 10.0f + lane * 2.0f,
                        false, S, Math.round(alpha * env * 0.90f), salt + lane * 67 + 900);
            }
        }

        // Combustible laissé par le front : grandes dalles, fissures sombres et feux entretenus.
        for (int row = 0; row < 7; row++) {
            float along = (-1.45f + row * 0.34f) * S;
            for (int lane = -4; lane <= 4; lane++) {
                int id = row * 31 + lane + 150;
                float h = hash01(salt, id);
                if (h < 0.10f && row > 0) continue;
                float reveal = smooth01((t - 0.13f - row * 0.040f - h * 0.025f) / 0.14f);
                if (reveal <= 0.0f) continue;
                groundPatch(vc, mat, tmp, cx, cy, cz, dx, dz, along, ground,
                        lane * 0.255f * S, reveal * env, 0.38f + h * 0.34f,
                        (row + lane & 1) == 0, false, S, alpha, salt + id * 19, t);
            }
        }

        // Chaque goutte part de la crête À SON INSTANT DE LANCEMENT, puis subit une vraie gravité.
        for (int p = 0; p < 72; p++) {
            float h = hash01(salt, p * 47 + 1200);
            float launch = 0.23f + (p % 8) * 0.042f + h * 0.018f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            int lane = p % 17 - 8;
            float side = (lane * 0.165f + (hash01(salt, p * 13) - 0.5f) * 0.04f) * S;
            float originF = (waveTravel(launch) + waveFrontLocal(launch, 0.72f, lane)) * S;
            float launchNoise = 0.90f + 0.08f * Mth.sin(lane * 1.37f + (salt & 31))
                    + 0.05f * Mth.sin(launch * 4.0f + lane * 0.70f);
            float originY = ground + waveHeightLocal(launch, 0.72f, launchNoise) * S;
            float time = age * 5.6f;
            float vf = (0.85f + 1.25f * h) * S;
            float vl = (lane * 0.035f + (hash01(salt, p * 59) - 0.5f) * 0.65f) * S;
            float vy = (1.45f + 1.85f * hash01(salt, p * 61)) * S;
            float gravity = 6.8f * S;
            float f = originF + vf * time;
            float l = side + vl * time;
            float y = originY + vy * time - 0.5f * gravity * time * time;
            float hit = (vy + Mth.sqrt(vy * vy + 2.0f * gravity * (originY - ground))) / gravity;
            float size = (0.035f + 0.055f * h) * S;
            if (time < hit) {
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        size * 1.15f, size * 1.35f, size, lavaDropletRgb(h),
                        Math.round(alpha * env));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - vf * 0.022f, y + size * 0.65f, l - vl * 0.022f,
                        size * 0.52f, size * 0.55f, size * 0.52f,
                        h > 0.55f ? LAVA[12] : LAVA[8], Math.round(alpha * env * 0.85f));
            } else if (time < hit + 0.28f) {
                // Contact : aplatissement et trois éclats latéraux, pas une téléportation décorative.
                float spread = (time - hit) * 1.8f;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, ground + 0.035f * S, l,
                        size * (1.4f + spread), 0.025f * S, size * (1.2f + spread),
                        LAVA[7 + (p & 3)], Math.round(alpha * env * (1.0f - spread)));
                for (int s = -1; s <= 1; s += 2) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + s * spread * size * 2.0f, ground + (0.06f + spread * 0.12f) * S,
                            l + s * spread * size, size * 0.38f, size * 0.50f, size * 0.38f,
                            LAVA[10], Math.round(alpha * env * (1.0f - spread)));
                }
            }
        }
        smoke(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, ground, 0.0f,
                t, 0.69f, 34, S, alpha, salt, 0.75f);
    }

    /** Les deux flammes se transforment réellement : hélice corporelle -> croissant tressé. */
    private static void renderSoul(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, float S, int alpha, int salt) {
        float birth = smooth01(t / 0.09f);
        float exit = smooth01((t - 0.80f) / 0.20f);
        float env = birth * (1.0f - exit);
        float ground = cy - 0.48f * S;
        float sweep = smooth01((t - 0.30f) / 0.38f);
        float radius = (0.82f + 0.12f * sweep) * S;
        float center = (-0.16f + 0.24f * sweep) * S;

        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 78; k++) {
                float u = k / 77.0f;
                float reveal = smooth01((t - u * 0.12f) / 0.10f);
                if (reveal <= 0.0f) continue;
                float morph = smooth01((t - 0.17f - u * 0.075f) / 0.32f);
                float helixAngle = strand * (float) Math.PI + u * 10.4f + t * 7.0f;
                float helixRadius = (0.49f - 0.12f * u) * S;
                float hf = Mth.cos(helixAngle) * helixRadius;
                float hl = Mth.sin(helixAngle) * helixRadius;
                float hy = ground + (0.10f + u * 1.72f) * S;

                float arcAngle = -2.34f + u * 4.68f;
                float braid = Mth.sin(u * 24.0f + strand * (float) Math.PI + t * 7.0f);
                float taper = 0.25f + 0.75f * (float) Math.pow(Mth.sin(u * (float) Math.PI), 0.65f);
                float ar = radius + (strand == 0 ? -0.045f : 0.045f) * S + braid * 0.050f * S;
                float af = center + braid * 0.065f * S;
                float al = Mth.cos(arcAngle) * ar;
                float ay = cy + Mth.sin(arcAngle) * ar + 0.08f * S;
                float f = Mth.lerp(morph, hf, af);
                float l = Mth.lerp(morph, hl, al);
                float y = Mth.lerp(morph, hy, ay);
                float width = (0.038f + 0.048f * taper * (0.35f + 0.65f * morph)) * S * reveal * (1.0f - exit * 0.50f);

                // Gros corps + deux lobes latéraux + petit cœur : une flamme, pas un fil géométrique.
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        width, width * 1.18f, width, soulColor(strand == 0 ? 6 : 4),
                        Math.round(alpha * env * reveal));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f + width * 0.45f, y - width * 0.30f, l - width * 0.40f,
                        width * 0.72f, width * 0.82f, width * 0.72f,
                        soulColor(strand == 0 ? 3 : 2), Math.round(alpha * env * 0.84f));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - width * 0.40f, y + width * 0.25f, l + width * 0.35f,
                        width * 0.48f, width * 0.55f, width * 0.48f,
                        soulColor(k % 9 == 0 ? 9 : 7), Math.round(alpha * env * 0.90f));
                if (k % 4 == 0) {
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
        for (int p = 0; p < 28; p++) {
            float angle = p * (float) (Math.PI * 2.0 / 28.0) + t * 0.35f;
            float r = (0.52f + 0.06f * Mth.sin(t * 6.0f + p)) * S;
            float hs = (0.065f + 0.025f * (p & 1)) * S * birth * (1.0f - exit);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, Mth.cos(angle) * r,
                    ground + 0.035f * S, Mth.sin(angle) * r,
                    hs * 1.35f, 0.022f * S, hs, soulColor(p % 7 == 0 ? 7 : 2),
                    Math.round(alpha * env * 0.60f));
        }

        // Braises fermées : elles naissent sur la tresse et héritent de son mouvement angulaire.
        for (int p = 0; p < 86; p++) {
            float launch = 0.28f + (p % 10) * 0.037f;
            float age = t - launch;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 53 + 1700);
            float u = (p % 39) / 38.0f;
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
            float originY = Mth.lerp(launchMorph, ground + (0.10f + u * 1.72f) * S,
                    cy + Mth.sin(angle) * ar + 0.08f * S);
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
                t, 0.76f, 24, S, alpha, salt, 0.45f);
    }

    /** Météore rapide : traîne = historique exact de la trajectoire, puis explosion et cratère. */
    private static void renderMeteor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float t,
                                     float dx, float dz, float S, int alpha, int salt) {
        float ground = cy - 0.48f * S;
        float exit = smooth01((t - 0.82f) / 0.18f);
        float q = Mth.clamp((t - 0.025f) / 0.375f, 0.0f, 1.0f);
        float birth = smooth01(t / 0.055f);
        float flightFade = 1.0f - smooth01((t - 0.400f) / 0.055f);
        float mf = meteorF(q) * S;
        float my = ground + meteorY(q) * S;

        if (flightFade > 0.0f && q > 0.0f) {
            // Corps principal en gros blocs ; croûte moyenne et micro-fissures par-dessus.
            float core = 0.27f * S * birth;
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, mf, my, 0.0f,
                    core, core * 0.94f, core, ROCK, Math.round(alpha * birth * flightFade));
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, mf + core * 0.35f, my + core * 0.25f, 0.0f,
                    core * 0.82f, core * 0.74f, core * 0.85f, ROCK_RED,
                    Math.round(alpha * birth * flightFade));
            for (int r = 0; r < 26; r++) {
                float h = hash01(salt, r * 43 + 2000);
                float angle = r * 2.3999632f + t * (r & 1) * 2.0f;
                float band = ((r % 5) - 2) * 0.105f * S;
                float radial = (0.23f + 0.06f * h) * S * birth;
                float size = (0.070f + 0.065f * h) * S * birth;
                float f = mf + Mth.cos(angle) * radial;
                float l = Mth.sin(angle) * radial;
                float y = my + band;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                        size, size * (0.80f + 0.35f * h), size,
                        h > 0.78f ? ROCK_HOT : h > 0.52f ? ROCK_RED : ROCK,
                        Math.round(alpha * birth * flightFade));
                if ((r & 3) == 0) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + size * 0.62f, y + size * 0.22f, l,
                            size * 0.24f, size * 0.52f, size * 0.30f,
                            h > 0.5f ? LAVA[12] : LAVA[8], Math.round(alpha * birth * flightFade));
                }
            }

            // 34 échantillons historiques. Aucune droite arbitraire : chacun appelle meteorF/Y.
            float currentTime = q * 1.45f;
            for (int k = 0; k < 34; k++) {
                float age = k * 0.043f;
                float historyTime = currentTime - age;
                if (historyTime <= 0.0f) continue;
                float hq = historyTime / 1.45f;
                float f = meteorF(hq) * S;
                float y = ground + meteorY(hq) * S;
                float life = 1.0f - age / 1.46f;
                float base = (0.065f + 0.145f * life) * S * birth;
                for (int lobe = 0; lobe < 5; lobe++) {
                    float angle = lobe * 2.3999632f + k * 0.81f + t * 4.0f;
                    float spread = base * (0.35f + age * 0.35f);
                    float size = base * (lobe == 0 ? 1.0f : 0.68f);
                    int rgb = age < 0.08f ? (lobe == 0 ? LAVA[14] : LAVA[12])
                            : age < 0.35f ? (lobe == 0 ? LAVA[11] : LAVA[8])
                            : age < 0.80f ? LAVA[6 + (lobe & 1)] : LAVA[3 + (lobe & 1)];
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f + Mth.cos(angle) * spread,
                            y + Mth.sin(angle * 1.31f) * spread,
                            Mth.sin(angle) * spread,
                            size * (1.0f + 0.35f * life), size, size,
                            rgb, Math.round(alpha * birth * flightFade * (0.35f + 0.65f * life)));
                }
                if ((k & 1) == 0 && k < 26) {
                    float tangent = Mth.atan2((-1.20f - 3.72f * historyTime) * 1.45f, 3.40f);
                    float streak = (0.14f + 0.22f * life) * S;
                    localRotBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, 0.0f,
                            streak, base * 0.26f, base * 0.34f, tangent,
                            age < 0.35f ? LAVA[11] : LAVA[6],
                            Math.round(alpha * birth * flightFade * life * 0.48f));
                }
                if (k % 4 == 0 && k < 24) {
                    flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, 0.0f,
                            0.34f + 0.25f * life, 0.070f + 0.035f * life,
                            -0.65f, 0.0f, t * 11.0f + k,
                            false, S, Math.round(alpha * birth * flightFade * life), salt + k * 31);
                }
            }
        }

        // Impact : gros lobes expansifs puis flammes épaisses qui refroidissent vers le crimson.
        float impact = smooth01((t - 0.400f) / 0.16f);
        for (int lobe = 0; lobe < 18; lobe++) {
            float h = hash01(salt, lobe * 71 + 2300);
            float reveal = smooth01((t - 0.400f - (lobe % 4) * 0.012f) / 0.12f);
            if (reveal <= 0.0f) continue;
            float angle = lobe * 2.3999632f;
            float radius = (0.10f + impact * (0.55f + h * 0.85f)) * S;
            float f = Mth.cos(angle) * radius;
            float l = Mth.sin(angle) * radius;
            float y = ground + (0.10f + impact * (0.25f + h * 0.55f)) * S;
            float mass = (0.10f + 0.13f * h) * S * reveal * (1.0f - exit * 0.55f);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    mass * 1.30f, mass, mass * 1.30f,
                    LAVA[5 + (lobe % 5)], Math.round(alpha * reveal * (1.0f - exit) * 0.82f));
            flame(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    (0.55f + 0.65f * h) * reveal * (1.0f - exit * 0.60f),
                    0.110f + 0.060f * h, Mth.cos(angle) * 0.65f, Mth.sin(angle) * 0.65f,
                    t * 10.0f + lobe, false, S,
                    Math.round(alpha * reveal * (1.0f - exit)), salt + lobe * 97);
        }

        // Cratère : plaques de combustible étendues et flammes persistantes, puis extinction.
        for (int p = 0; p < 42; p++) {
            float h = hash01(salt, p * 47 + 2600);
            float angle = p * 2.3999632f;
            float reveal = smooth01((t - 0.43f - (p % 6) * 0.017f) / 0.14f);
            if (reveal <= 0.0f || p % 13 == 0) continue;
            float radius = (0.28f + (p % 4) * 0.27f + impact * 0.18f) * S;
            groundPatch(vc, mat, tmp, cx, cy, cz, dx, dz,
                    Mth.cos(angle) * radius, ground, Mth.sin(angle) * radius,
                    reveal * (1.0f - exit), 0.36f + h * 0.48f,
                    (p & 1) == 0, false, S, alpha, salt + p * 83, t);
        }

        // Débris et lave de l'impact : trajectoires balistiques et contacts au sol.
        for (int p = 0; p < 82; p++) {
            float age = t - 0.405f - (p % 7) * 0.010f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 59 + 2900);
            float angle = p * 2.3999632f;
            float time = age * 6.4f;
            float speed = (0.75f + 1.85f * h) * S;
            float f = Mth.cos(angle) * speed * time;
            float l = Mth.sin(angle) * speed * time;
            float vy = (1.65f + 2.75f * hash01(salt, p * 61)) * S;
            float y = ground + 0.28f * S + vy * time - 3.65f * S * time * time;
            if (y < ground) continue;
            float size = (0.025f + 0.075f * h) * S * (1.0f - exit);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, f, y, l,
                    size, size * (p % 4 == 0 ? 0.8f : 1.35f), size,
                    p % 5 == 0 ? ROCK_RED : lavaDropletRgb(h),
                    Math.round(alpha * (1.0f - exit)));
            if ((p & 3) == 0) {
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        f - Mth.cos(angle) * size, y + size * 0.7f, l - Mth.sin(angle) * size,
                        size * 0.42f, size * 0.42f, size * 0.42f,
                        LAVA[12], Math.round(alpha * (1.0f - exit) * 0.80f));
            }
        }
        smoke(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, ground, 0.0f,
                t, 0.43f, 52, S, alpha, salt, 1.0f);
    }

    private static float waveTravel(float t) {
        return -1.20f + 1.80f * smooth01((t - 0.055f) / 0.55f);
    }

    private static float waveFrontLocal(float t, float u, int lane) {
        float curl = smooth01((t - 0.13f) / 0.32f);
        float tornLip = 0.12f * Mth.sin(lane * 1.37f + t * 3.0f) * u * u;
        return -0.58f + 0.58f * u + 0.82f * curl * (float) Math.pow(u, 3.2f) + tornLip;
    }

    private static float collapse(float t) {
        return smooth01((t - 0.69f) / 0.26f);
    }

    private static float waveHeightLocal(float t, float u, float noise) {
        float birth = smooth01((t - 0.015f - u * 0.055f) / 0.13f);
        float height = 1.72f * noise * birth * (1.0f - 0.78f * collapse(t));
        return Mth.sin(u * 2.35f) * height - collapse(t) * 0.34f;
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
        float flick = 0.86f + 0.14f * Mth.sin(time * 1.35f + jitter * 20.0f)
                + 0.16f * (valueNoise(time * 0.42f, jitter * 10.0f, 0.0f, seed) - 0.5f);
        float animatedHeight = height * flick;
        int levels = 11;
        for (int k = 0; k < levels; k++) {
            float u = k / (float) (levels - 1);
            // Deux champs lisses remontent à des vitesses différentes : la silhouette se scinde et
            // se referme au lieu de se balancer comme un objet rigide.
            float n1 = fbm(jitter * 3.1f + u * 0.55f, u * 1.50f - time * 0.24f,
                    jitter * 7.0f + time * 0.03f, seed);
            float n2 = fbm(jitter * 5.7f + u * 1.40f, u * 3.20f - time * 0.42f,
                    2.7f + time * 0.06f, seed ^ 0x51F2);
            float warpF = (n1 - 0.5f) * 1.30f * u + (n2 - 0.5f) * 0.35f * u;
            float warpL = (n2 - 0.5f) * 1.10f * u + (n1 - 0.5f) * 0.30f * u;
            float pf = f + rootF + (windF * u * u * animatedHeight + warpF * width) * S;
            float pl = l + rootL + (windL * u * u * animatedHeight + warpL * width) * S;
            float py = y + u * animatedHeight * S;
            float profile = Math.max(0.055f, (float) Math.pow(1.0f - u, 0.80f)
                    * (0.70f + 0.30f * smooth01(u / 0.25f)));
            float w = width * profile * S * (0.78f + 0.38f * n1);
            float hy = Math.max(animatedHeight * S / levels * 0.70f, w * 0.58f);
            int core = soul ? soulRamp(u, true) : fireRamp(u, true);
            int outer = soul ? soulRamp(u, false) : fireRamp(u, false);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                    w * 0.78f, hy, w * 0.82f, core, Math.round(alpha * (1.0f - u * 0.25f)));
            if (u < 0.80f) {
                float breathe = 0.72f + 0.28f * Mth.sin(time * 1.9f + k * 0.8f + jitter * 6.0f);
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        pf + w * (0.48f + 0.20f * n2), py - hy * 0.18f, pl + w * (n1 - 0.5f) * 0.35f,
                        w * 0.65f * breathe, hy * 0.88f, w * 0.70f, outer, Math.round(alpha * 0.84f));
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        pf - w * 0.48f, py - hy * 0.12f, pl + w * (0.44f + 0.18f * n1),
                        w * 0.58f, hy * 0.82f, w * 0.62f, outer, Math.round(alpha * 0.76f));
                if (k < 7) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            pf + w * (n2 - 0.5f) * 0.4f, py - hy * 0.24f, pl - w * 0.54f,
                            w * 0.68f, hy * 0.82f, w * 0.62f,
                            soul ? soulColor(2) : LAVA[5 + (k & 1)], Math.round(alpha * 0.70f));
                }
            }
            // Le haut se divise en deux langues dont la distance et l'existence varient avec le bruit.
            if (u > 0.36f && n1 + n2 > 0.72f) {
                float split = smooth01((u - 0.36f) / 0.64f);
                float separation = width * S * split * (0.28f + 0.60f * n2);
                float lobeW = w * (0.36f + 0.18f * n1);
                for (int sign = -1; sign <= 1; sign += 2) {
                    localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                            pf + separation * sign, py + hy * (0.10f + 0.18f * n2),
                            pl - separation * sign * 0.42f,
                            lobeW, hy * 0.64f, lobeW,
                            soul ? soulColor(4 + (k & 1)) : LAVA[6 + (k & 3)],
                            Math.round(alpha * (0.45f + 0.35f * n2) * (1.0f - u * 0.35f)));
                }
            }
            if (k % 3 == 0) {
                float aura = w * 1.46f;
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                        aura, hy * 1.32f, aura, core, Math.round(alpha * 0.09f * (1.0f - u * 0.45f)));
            }
        }

        // Flammèches : cycles fermés ascendants, taille 0.42 -> 0.08, turbulence et refroidissement.
        for (int p = 0; p < 5; p++) {
            float cycle = fract(time * 0.13f + p * 0.217f + jitter);
            float envelope = smooth01(cycle / 0.12f) * (1.0f - smooth01((cycle - 0.58f) / 0.42f));
            if (envelope <= 0.0f) continue;
            float h = hash01(seed, p * 47 + 400);
            float turbulenceF = (valueNoise(h * 9.0f, cycle * 2.1f - time * 0.16f, 1.3f, seed + p) - 0.5f)
                    * width * 2.0f * cycle;
            float turbulenceL = (valueNoise(h * 7.0f, cycle * 1.8f - time * 0.13f, 8.1f, seed ^ (p * 997)) - 0.5f)
                    * width * 1.7f * cycle;
            float pf = f + rootF + (windF * animatedHeight * cycle * 0.55f + turbulenceF) * S;
            float pl = l + rootL + (windL * animatedHeight * cycle * 0.55f + turbulenceL) * S;
            float py = y + animatedHeight * S * (0.18f + cycle * 1.15f);
            float size = width * S * (0.42f - 0.34f * cycle) * (0.78f + 0.42f * h);
            float heat = 0.18f + cycle * 0.78f;
            int rgb = soul ? soulRamp(heat, false) : fireRamp(heat, false);
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                    size, size * (1.10f + 0.35f * (1.0f - cycle)), size,
                    rgb, Math.round(alpha * envelope * 0.64f));
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                    pf - size * 0.20f, py + size * 0.25f, pl + size * 0.16f,
                    size * 0.46f, size * 0.62f, size * 0.46f,
                    soul ? soulRamp(heat, true) : fireRamp(heat, true),
                    Math.round(alpha * envelope * 0.58f));
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

    /** Trajectoire unique du météore ; la traîne réutilise ces deux fonctions dans le passé. */
    private static float meteorF(float q) {
        float time = q * 1.45f;
        return -3.40f + (3.40f / 1.45f) * time;
    }

    private static float meteorY(float q) {
        float time = q * 1.45f;
        return 6.00f - 1.20f * time - 1.86f * time * time;
    }

    private static void smoke(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              float f, float ground, float l, float t, float start,
                              int count, float S, int alpha, int salt, float spread) {
        for (int p = 0; p < count; p++) {
            float age = t - start - (p % 8) * 0.017f;
            if (age <= 0.0f) continue;
            float h = hash01(salt, p * 67 + 3200);
            float life = Mth.clamp(age / 0.31f, 0.0f, 1.0f);
            float fade = (1.0f - life) * smooth01(age / 0.05f);
            float angle = p * 2.3999632f + life * 1.8f;
            float radius = (0.16f + h * spread + life * 0.38f) * S;
            float pf = f + Mth.cos(angle) * radius + life * 0.25f * S;
            float pl = l + Mth.sin(angle) * radius;
            float py = ground + (0.28f + h * 0.48f + life * 1.65f) * S;
            float size = (0.095f + h * 0.115f + life * 0.075f) * S;
            localBox(vc, mat, tmp, cx, cy, cz, dx, dz, pf, py, pl,
                    size * 1.15f, size * 0.82f, size,
                    (p & 3) == 0 ? SMOKE_LIGHT : SMOKE, Math.round(alpha * fade * 0.32f));
            if ((p & 3) == 0) {
                localBox(vc, mat, tmp, cx, cy, cz, dx, dz,
                        pf + size * 0.55f, py - size * 0.30f, pl,
                        size * 0.62f, size * 0.55f, size * 0.65f,
                        SMOKE, Math.round(alpha * fade * 0.24f));
            }
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
