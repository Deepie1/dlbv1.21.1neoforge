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

    /**
     * SERAPH V2 — un seul rayon céleste vertical. Aucune aile, aucun mécanisme accessoire :
     * noyau blanc, enveloppe alpha et très légère diffusion au sol. Dégâts serveur tous les
     * quatre ticks de 12 à 92, uniquement sur les entités encore présentes dans le cylindre.
     */
    private static void renderSeraph(Voxels v, float t, int alpha, int salt) {
        float enter = smooth(t / 0.035f);
        float exit = 1.0f - smooth((t - 0.88f) / 0.11f);
        int a = Math.round(alpha * enter * exit);
        float drop = easeOut(t / 0.12f);
        float active = smooth((t - 0.105f) / 0.025f) * (1.0f - smooth((t - 0.86f) / 0.12f));
        float headY = Mth.lerp(drop, 9.20f, -0.86f);
        float pulse = 0.90f + 0.10f * Mth.sin(t * 74.0f);

        // Petit marqueur d'arrivée : pas de sceau géant. Il s'efface dès que le vrai rayon existe.
        float marker = (1.0f - active) * smooth(t / 0.025f);
        for (int side = 0; side < 16; side++) {
            float angle = side * TAU / 16.0f;
            float next = (side + 1) * TAU / 16.0f;
            v.glowLink(Mth.cos(angle) * 0.54f, -0.87f, Mth.sin(angle) * 0.54f,
                    Mth.cos(next) * 0.54f, -0.87f, Mth.sin(next) * 0.54f,
                    0.016f, HONEY, WHITE, Math.round(a * marker * 0.60f));
        }

        if (active < 0.999f) {
            // Pendant la chute, seule la tête et sa courte queue existent : le rayon ne naît pas
            // instantanément sur toute sa hauteur.
            v.lightLink(0.0f, headY + 1.55f, 0.0f, 0.0f, headY, 0.0f,
                    0.060f, WHITE, IVORY, Math.round(a * (1.0f - active) * 0.90f));
            for (int side = 0; side < 8; side++) {
                float angle = side * TAU / 8.0f;
                float f = Mth.cos(angle) * 0.24f, l = Mth.sin(angle) * 0.24f;
                v.bodyLink(f, headY + 1.20f, l, f, headY, l,
                        0.070f, HONEY, Math.round(a * (1.0f - active) * 0.22f));
            }
        }

        if (active > 0.0f) {
            // Noyau continu fin, entouré de trois filaments qui descendent réellement vers la cible.
            v.lightLink(0.0f, 9.20f, 0.0f, 0.0f, -0.86f, 0.0f,
                    0.065f, WHITE, IVORY, Math.round(a * active * pulse));
            for (int strand = 0; strand < 3; strand++) {
                float angle = strand * TAU / 3.0f + t * 4.0f;
                float f = Mth.cos(angle) * 0.095f, l = Mth.sin(angle) * 0.095f;
                v.glowLink(f, 9.20f, l, f, -0.86f, l, 0.017f,
                        strand == 1 ? WHITE : HONEY, IVORY, Math.round(a * active * 0.72f));
            }

            // Enveloppe cylindrique douze faces : deux passes alpha/additive, mais un alpha assez
            // faible pour voir le mob et le décor à travers. Le cylindre n'est jamais opaque.
            for (int side = 0; side < 12; side++) {
                float angle = side * TAU / 12.0f;
                float f = Mth.cos(angle) * 0.48f, l = Mth.sin(angle) * 0.48f;
                v.bodyLink(f, 9.20f, l, f, -0.86f, l, 0.090f, HONEY,
                        Math.round(a * active * 0.34f));
                v.glowLink(f, 9.20f, l, f, -0.86f, l, 0.011f,
                        side % 3 == 0 ? IVORY : HONEY, WHITE, Math.round(a * active * 0.72f));
            }
            for (int layer = 0; layer < 48; layer++) {
                float y = -0.80f + layer * 10.0f / 48.0f;
                for (int side = 0; side < 12; side++) {
                    float angle = side * TAU / 12.0f + layer * 0.006f;
                    float next = (side + 1) * TAU / 12.0f + layer * 0.006f;
                    v.bodyLink(Mth.cos(angle) * 0.48f, y, Mth.sin(angle) * 0.48f,
                            Mth.cos(next) * 0.48f, y, Mth.sin(next) * 0.48f,
                            0.075f, side % 2 == 0 ? IVORY : HONEY,
                            Math.round(a * active * 0.20f));
                }
            }

            // Quelques paquets lumineux descendant dans le cylindre ; le reste du faisceau reste
            // parfaitement stable, pour que la zone de dégâts soit évidente.
            for (int packet = 0; packet < 18; packet++) {
                float travel = (t * 7.0f + packet / 18.0f) % 1.0f;
                float y = 9.10f - travel * 9.90f;
                float angle = packet * 2.3999632f;
                float radius = 0.12f + (packet % 3) * 0.10f;
                v.glow(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                        packet % 4 == 0 ? 0.042f : 0.026f,
                        packet % 3 == 0 ? WHITE : HONEY, Math.round(a * active * 0.68f));
            }

            // Très léger spread au bout du rayon : gerbes basses, transparentes et limitées à
            // 1,55 bloc. Elles ne constituent pas une seconde zone de dégâts.
            float spread = smooth((t - 0.12f) / 0.16f);
            for (int ray = 0; ray < 24; ray++) {
                float angle = ray * TAU / 24.0f + (ray % 2) * 0.035f;
                float inner = 0.34f;
                float outer = 0.72f + spread * (0.38f + (ray % 4) * 0.15f);
                float y = -0.82f + (ray % 3) * 0.018f;
                v.glowLink(Mth.cos(angle) * inner, y, Mth.sin(angle) * inner,
                        Mth.cos(angle) * outer, y + 0.035f, Mth.sin(angle) * outer,
                        ray % 4 == 0 ? 0.019f : 0.011f,
                        ray % 3 == 0 ? WHITE : HONEY, IVORY,
                        Math.round(a * active * (ray % 4 == 0 ? 0.34f : 0.20f)));
            }
            for (int mote = 0; mote < 26; mote++) {
                float h = hash(salt, 2100 + mote * 31);
                float angle = mote * 2.3999632f + t * 0.35f;
                float radius = 0.36f + h * 0.95f;
                float y = -0.78f + (0.08f + h * 0.22f) * (0.5f + 0.5f * Mth.sin(t * 20.0f + mote));
                v.glow(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                        0.012f + h * 0.012f, mote % 5 == 0 ? WHITE : HONEY,
                        Math.round(a * active * 0.34f));
            }
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
        // Trois cages de distorsion restent solidaires du projectile. Elles créent une
        // succession de portails au lieu d'un simple laser, sans modifier le corridor réel.
        for (int gate = 0; gate < 3; gate++) {
            float f = head - 2.65f + gate * 0.92f;
            float gateRadius = (0.58f + gate * 0.13f) * (0.70f + forge * 0.30f);
            astralGate(v, f, gateRadius, t * (gate % 2 == 0 ? 3.8f : -3.2f) + gate,
                    gate == 1 ? LILAC : CYAN, Math.round(a * lanceAlpha * 0.82f));
        }
        // Constellation en creux le long du fût : huit noeuds reliés suivent la pointe.
        for (int node = 0; node < 8; node++) {
            float f = head - 3.05f + node * 0.40f;
            float angle = node * 2.3999632f - t * 7.0f;
            float radius = 0.22f + (node % 3) * 0.07f;
            float y = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            astralShell(v, f, y, l, 0.07f + (node % 2) * 0.025f,
                    Math.round(a * lanceAlpha * 0.82f), t * 24.0f + node);
            if (node > 0) {
                float previousF = f - 0.40f, previousAngle = (node - 1) * 2.3999632f - t * 7.0f;
                float previousRadius = 0.22f + ((node - 1) % 3) * 0.07f;
                v.glowLink(previousF, Mth.cos(previousAngle) * previousRadius,
                        Mth.sin(previousAngle) * previousRadius, f, y, l,
                        0.012f, BLUE, ICE, Math.round(a * lanceAlpha * 0.74f));
            }
        }
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
        // Huit plaques longitudinales facettées : un projectile construit, pas un trait.
        for (int armor = 0; armor < 8; armor++) {
            float angle = armor * TAU / 8.0f + t * 1.1f;
            for (int plate = 0; plate < 9; plate++) {
                float f = head - 3.05f + plate * 0.29f;
                float r = 0.19f + Mth.sin((plate + 1) * 0.32f) * 0.07f;
                v.rot(f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                        0.12f, 0.027f, 0.040f, 0.0f, 0.0f, angle,
                        armor % 2 == 0 ? ICE : VIOLET,
                        Math.round(a * lanceAlpha * (0.56f + 0.30f * Mth.sin(plate + t * 16.0f))));
            }
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
        // Trois ailettes secondaires à contre-rotation, chacune avec ses nervures.
        for (int fin = 0; fin < 3; fin++) {
            float angle = -fin * TAU / 3.0f - t * 3.2f + 0.52f;
            float previousF = head - 3.10f, previousR = 0.10f;
            for (int step = 1; step <= 12; step++) {
                float u = step / 12.0f;
                float f = head - 3.10f + u * 0.78f;
                float r = 0.10f + Mth.sin(u * Mth.PI) * 0.47f;
                v.glowLink(previousF, Mth.cos(angle) * previousR, Mth.sin(angle) * previousR,
                        f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                        0.018f, fin == 1 ? LILAC : CYAN, WHITE,
                        Math.round(a * lanceAlpha * 0.90f));
                if (step % 3 == 0) v.glowLink(f, 0.0f, 0.0f,
                        f, Mth.cos(angle) * r, Mth.sin(angle) * r,
                        0.012f, VIOLET, ICE, Math.round(a * lanceAlpha * 0.64f));
                previousF = f; previousR = r;
            }
        }
        // Six arcs orbitaux échelonnés, chacun composé de douze voxels lumineux orientés.
        for (int arc = 0; arc < 6; arc++) {
            float base = arc * TAU / 6.0f + t * (arc % 2 == 0 ? 4.0f : -3.0f);
            float f = head - 2.85f + arc * 0.51f;
            for (int step = 0; step < 12; step++) {
                float angle = base + step * 0.11f;
                float radius = 0.48f + 0.08f * Mth.sin(step * 0.4f + arc);
                v.rot(f + step * 0.023f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                        0.025f, 0.018f, 0.045f, angle, t * 2.0f, arc,
                        step % 4 == 0 ? WHITE : arc % 2 == 0 ? CYAN : LILAC,
                        Math.round(a * lanceAlpha * 0.68f));
            }
        }
        astralShell(v, head - 0.43f, 0.0f, 0.0f, 0.27f,
                Math.round(a * lanceAlpha * 0.75f), t * 24.0f);

        // Sillage d'étoiles reliées : il se rétracte après la perforation et n'étend jamais
        // artificiellement la zone de dégâts côté serveur.
        float wake = smooth((t - 0.13f) / 0.17f) * (1.0f - smooth((t - 0.53f) / 0.28f));
        for (int trail = 0; trail < 48; trail++) {
            float u = trail / 47.0f;
            float h = hash(salt, 540 + trail * 29);
            float f = head - 0.35f - u * 3.65f;
            float angle = trail * 2.3999632f - t * 4.5f;
            float radius = (0.15f + u * 0.55f) * (0.55f + h * 0.45f);
            float y = Mth.cos(angle) * radius, l = Mth.sin(angle) * radius;
            v.rot(f, y, l, 0.030f + h * 0.018f, 0.014f, 0.065f,
                    angle, t * 3.0f, h, trail % 5 == 0 ? WHITE : trail % 2 == 0 ? CYAN : LILAC,
                    Math.round(a * wake * (1.0f - u * 0.62f)));
            if (trail > 0 && trail % 4 == 0) {
                v.glowLink(f + 0.12f, y * 0.70f, l * 0.70f, f, y, l,
                        0.010f, BLUE, ICE, Math.round(a * wake * 0.58f));
            }
        }

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
            // Deux fronts de choc : un disque transversal et une onde horizontale.
            for (int front = 0; front < 2; front++) {
                float radius = 0.35f + impact * (1.35f + front * 0.48f);
                for (int step = 0; step < 56; step++) {
                    float angle = step * TAU / 56.0f;
                    float next = (step + 1) * TAU / 56.0f;
                    if (front == 0) {
                        v.glowLink(0.12f, Mth.cos(angle) * radius, Mth.sin(angle) * radius,
                                0.12f, Mth.cos(next) * radius, Mth.sin(next) * radius,
                                0.018f, CYAN, WHITE, Math.round(a * impact * 0.68f));
                    } else {
                        v.glowLink(Mth.cos(angle) * radius, -0.86f, Mth.sin(angle) * radius,
                                Mth.cos(next) * radius, -0.86f, Mth.sin(next) * radius,
                                0.014f, LILAC, ICE, Math.round(a * impact * 0.46f));
                    }
                }
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
            for (int part = 0; part < 120; part++) {
                float angle = part * TAU / 120.0f;
                float next = (part + 1) * TAU / 120.0f;
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
        astralShell(v, cf, cy, l, radius * 0.22f, Math.round(alpha * 0.82f), rotation * 3.0f);
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

    /** Portail octogonal lié à la lance : double jante, rayons et cubes lumineux scannés. */
    private static void astralGate(Voxels v, float f, float radius, float rotation,
                                   int rgb, int alpha) {
        for (int ring = 0; ring < 2; ring++) for (int side = 0; side < 8; side++) {
            float angle = side * TAU / 8.0f + rotation;
            float next = (side + 1) * TAU / 8.0f + rotation;
            float r = radius * (ring == 0 ? 1.0f : 0.78f);
            float y0 = Mth.cos(angle) * r, l0 = Mth.sin(angle) * r;
            float y1 = Mth.cos(next) * r, l1 = Mth.sin(next) * r;
            v.glowLink(f, y0, l0, f, y1, l1,
                    ring == 0 ? 0.023f : 0.014f, ring == 0 ? rgb : VIOLET, WHITE,
                    Math.round(alpha * (ring == 0 ? 1.0f : 0.62f)));
            if (ring == 0) {
                v.glowLink(f, y0 * 0.26f, l0 * 0.26f, f, y0, l0,
                        0.012f, BLUE, rgb, Math.round(alpha * 0.64f));
                astralShell(v, f, y0, l0, 0.075f,
                        Math.round(alpha * 0.74f), rotation * 5.0f + side);
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
