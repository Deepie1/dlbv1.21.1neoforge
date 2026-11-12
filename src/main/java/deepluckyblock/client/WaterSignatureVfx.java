package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T375-WATER-REFONTE — élément <b>EAU</b> : trois signatures voxel sur un moteur commun.
 *
 * <p><b>Pourquoi cette classe ?</b> L'eau était le dernier des cinq éléments chinois classiques à
 * n'avoir <i>aucun</i> moteur dédié : ses trois signatures étaient dessinées en ligne dans
 * {@code WeaponWorldVfxManager#impactWater} (une nappe de cubes, une « vague » générique et un
 * buisson de piques de glace) et duraient 14 à 22 ticks, soit moins d'une seconde — là où le feu,
 * la terre, le métal, la lumière, le poison, la nature et le vent ont tous reçu une chorégraphie de
 * 80 à 170 ticks avec son propre fichier. Cette classe met l'eau au même niveau.</p>
 *
 * <p><b>Langage visuel.</b> Une masse d'eau réelle n'est pas une plaque bleue : elle a une
 * <i>épaisseur</i> (le volume est translucide, l'écume est opaque), elle <i>déferle</i> (la crête
 * avance plus vite que la base et finit par basculer), et elle <i>revient</i> (reflux + rides).
 * Un tourbillon réel tourne en <b>rotation différentielle</b> : plus on approche de l'axe, plus
 * c'est rapide et plus c'est bas. Le givre, lui, ne « pousse » pas : il <i>court</i> au sol en
 * étoile, puis cristallise en pics, puis <b>éclate</b>.</p>
 *
 * <ul>
 *   <li>{@link #SURGE} — <b>Raz-de-marée</b> ({@code HYDRO_TIDAL_SURGE}, 104 ticks) : la mer se
 *       retire, un mur d'eau de 4,6 blocs se lève derrière la cible, le traverse, la lèvre
 *       s'enroule et déferle en écume, puis la nappe reflue en rides concentriques.</li>
 *   <li>{@link #MAELSTROM} — <b>Maelström abyssal</b> ({@code ABYSSAL_MAELSTROM}, 150 ticks) : un
 *       bassin en entonnoir se creuse dans le sol, cinq anneaux tournent de plus en plus vite vers
 *       une gorge noire, trois bras d'écume en spirale logarithmique y plongent, puis tout implose
 *       et un <b>geyser</b> de 9 blocs recrache ce qui a été avalé.</li>
 *   <li>{@link #RIME} — <b>Givre du permafrost</b> ({@code PERMAFROST_RIME}, 120 ticks) : le gel
 *       court au sol en étoile de givre, neuf pics de glace percent le sol, la cible est enfermée
 *       dans un bloc translucide qui se fissure, tremble, puis <b>éclate</b> en soixante éclats.</li>
 * </ul>
 *
 * <p><b>Blocs réels.</b> Comme pour le vent et le séisme, les vrais {@code BlockState} du terrain
 * sont capturés et animés par une classe séparée ({@link WaterTerrainVfx}) : la vague pousse les
 * blocs devant elle, le maelström les avale en spirale. Cette classe ne dessine que l'eau, l'écume
 * et la glace.</p>
 *
 * <p><b>Contraintes.</b> Aucune allocation dans la passe de rendu, aucun {@code RenderType}
 * demandé ici, tout est exprimé dans le repère caméra-relatif fourni par le manager. Charge mesurée
 * au pic : ≈ 560 cubes pour le raz-de-marée, ≈ 470 pour le maelström, ≈ 580 pour le givre —
 * sous le plafond de 7 000 cubes par image de {@link WeaponWorldVfxManager}.</p>
 */
public final class WaterSignatureVfx {

    /** Raz-de-marée ({@code HYDRO_TIDAL_SURGE}). */
    public static final int SURGE = 0;
    /** Maelström abyssal ({@code ABYSSAL_MAELSTROM}). */
    public static final int MAELSTROM = 1;
    /** Givre du permafrost ({@code PERMAFROST_RIME}). */
    public static final int RIME = 2;

    /** Palette eau : de l'abysse à l'écume. */
    private static final int ABYSS = 0x0B3550;
    private static final int DEEP = 0x14557F;
    private static final int OCEAN = 0x1E7BA8;
    private static final int AZURE = 0x37A6D6;
    private static final int SHALLOW = 0x62C7E8;
    private static final int SPRAY = 0xA9E6F7;
    private static final int FOAM = 0xE8FBFF;
    private static final int WHITE = 0xF6FEFF;
    /** Palette givre : la glace garde une dominante plus froide et plus claire que l'eau liquide. */
    private static final int ICE_DEEP = 0x2E6F93;
    private static final int ICE = 0x7FC9E8;
    private static final int ICE_PALE = 0xBFEBFA;
    private static final int FROST = 0xE4F7FF;

    private static final float TAU = (float) (Math.PI * 2.0);

    private WaterSignatureVfx() { }

    /**
     * Point d'entrée unique des trois signatures.
     *
     * @param cx,cy,cz origine caméra-relative (centre de la cible)
     * @param dx,dz    direction horizontale normalisée (sens de marche de la vague)
     * @param t        progression 0..1 sur la durée visuelle
     * @param alpha    opacité de base fournie par le manager (0..255)
     * @param salt     graine stable de l'instance
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        if (variant == MAELSTROM) renderMaelstrom(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else if (variant == RIME) renderRime(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else renderSurge(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
    }

    // ============================================================================================
    // 1. RAZ-DE-MARÉE — retrait, mur d'eau qui avance, lèvre qui déferle, reflux
    // ============================================================================================

    /** Hauteur du mur d'eau au plus fort du déferlement, en blocs. */
    private static final float SURGE_HEIGHT = 4.60f;
    /** Demi-largeur du front, en blocs : la vague est un mur, pas un jet. */
    private static final float SURGE_HALF_WIDTH = 3.40f;
    /** Nombre de colonnes du mur. Chaque colonne a sa propre hauteur et sa propre phase. */
    private static final int SURGE_COLUMNS = 19;

    /**
     * Chorégraphie (104 ticks) :
     * <ul>
     *   <li>0,00–0,18 <b>retrait</b> : l'eau est aspirée vers l'arrière, le sol se découvre ;</li>
     *   <li>0,10–0,54 <b>montée et marche</b> : le mur se lève à 3,4 blocs derrière la cible et
     *       avance jusqu'à 2,8 blocs devant elle ;</li>
     *   <li>0,34–0,64 <b>déferlement</b> : la lèvre s'enroule vers l'avant et bascule ;</li>
     *   <li>0,50–0,82 <b>écume et gerbes</b> : projections balistiques et moutons blancs ;</li>
     *   <li>0,66–1,00 <b>reflux</b> : rides concentriques au sol et nappe qui s'efface.</li>
     * </ul>
     */
    private static void renderSurge(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.05f) * (1.0f - smooth((t - 0.90f) / 0.10f)));
        if (a <= 4) return;

        float draw = smooth(t / 0.18f);                        // retrait initial
        float lift = smooth((t - 0.08f) / 0.34f);              // montée du mur
        float march = smooth((t - 0.12f) / 0.46f);             // avancée du front
        float curl = smooth((t - 0.34f) / 0.26f);              // enroulement de la lèvre
        float burst = smooth((t - 0.50f) / 0.16f) * (1.0f - smooth((t - 0.82f) / 0.18f));
        float ebb = smooth((t - 0.66f) / 0.30f);               // reflux
        float body = (1.0f - ebb * 0.86f);

        // ---- 1. Retrait : l'eau recule et découvre le sol (3 arcs de succion, 72 cubes) ----
        if (draw < 1.0f || ebb > 0.0f) {
            float pull = Math.max(1.0f - draw, ebb * 0.85f);
            for (int arc = 0; arc < 3; arc++) {
                float radius = (1.10f + arc * 0.78f) * (0.65f + 0.55f * pull);
                for (int i = 0; i < 24; i++) {
                    float u = (i / 23.0f) * 2.0f - 1.0f;
                    float h = hash(salt, 7100 + arc * 61 + i * 7);
                    float lateral = u * radius * 1.35f;
                    float forward = -radius * (0.55f + 0.45f * Mth.cos(u * 1.4f)) - pull * 0.70f;
                    float y = -1.12f + 0.05f * h + 0.08f * Mth.sin(t * 7.0f + i * 0.9f);
                    float size = (0.070f + 0.045f * h) * (0.55f + 0.45f * pull);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, forward, lateral, y,
                            size * 1.5f, size * 0.42f, size * 1.5f,
                            mix(DEEP, OCEAN, h), Math.round(a * pull * 0.62f));
                }
            }
        }

        // ---- 2. Le mur d'eau : 19 colonnes pleines, crête irrégulière, lèvre enroulée ----
        // La position du front va de -3,4 (derrière la cible) à +2,8 (devant elle).
        float front = Mth.lerp(march, -3.40f, 2.80f);
        if (body > 0.02f) {
            for (int c = 0; c < SURGE_COLUMNS; c++) {
                float u = (c / (float) (SURGE_COLUMNS - 1)) * 2.0f - 1.0f;      // -1 .. +1
                float hc = hash(salt, 7300 + c * 29);
                float hc2 = hash(salt, 7400 + c * 53);
                // Profil du front : plein au centre, effilé aux extrémités, jamais symétrique.
                float profile = (1.0f - u * u * 0.74f) * (0.86f + 0.26f * hc);
                float columnH = SURGE_HEIGHT * lift * profile * body;
                if (columnH < 0.12f) continue;
                float lateral = u * SURGE_HALF_WIDTH;
                // Le front n'est pas droit : il se recourbe en arc, les ailes restent en retard.
                float forward = front - (u * u) * 1.15f + (hc2 - 0.5f) * 0.22f;
                int steps = Math.max(3, Mth.floor(columnH / 0.36f));
                for (int s = 0; s < steps; s++) {
                    float v = (s + 0.5f) / steps;                                 // 0 bas .. 1 crête
                    // Cisaillement : le haut de la colonne avance plus vite que le pied.
                    float shear = curl * v * v * 1.45f + march * v * 0.38f;
                    float y = -1.12f + columnH * v;
                    float half = (0.235f - 0.085f * v) * (0.90f + 0.22f * hc);
                    float depth = 0.30f + 0.26f * (1.0f - v);
                    int rgb = mix(mix(ABYSS, OCEAN, v * 0.85f), AZURE, v * v);
                    int ca = Math.round(a * body * (0.62f + 0.34f * v));
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            forward + shear, lateral + Mth.sin(v * 3.1f + hc * 6.0f) * 0.10f, y,
                            depth, 0.21f + 0.06f * (1.0f - v), half,
                            0.0f, -curl * v * 0.85f, (hc2 - 0.5f) * 0.30f, rgb, ca);
                }
                // ---- Lèvre : le sommet bascule vers l'avant et se creuse (tube de la vague) ----
                if (curl > 0.03f) {
                    int lipSteps = 5;
                    for (int s = 0; s < lipSteps; s++) {
                        float v = s / (float) (lipSteps - 1);
                        float arcAngle = curl * (0.35f + v * 1.95f);
                        float r = 0.52f + 0.20f * hc;
                        float ly = -1.12f + columnH + Mth.sin(arcAngle) * r * 0.70f - v * 0.10f;
                        float lf = forward + curl * 1.45f + (1.0f - Mth.cos(arcAngle)) * r * 1.35f;
                        int rgb = mix(AZURE, FOAM, 0.30f + 0.60f * v);
                        rot(vc, mat, tmp, cx, cy, cz, dx, dz, lf, lateral, ly,
                                0.30f, 0.17f, 0.215f + 0.03f * hc,
                                0.0f, -arcAngle, 0.0f, rgb,
                                Math.round(a * body * curl * (0.80f - 0.25f * v)));
                    }
                }
            }
        }

        // ---- 3. Écume : moutons blancs discontinus sur la crête (jamais une nappe continue) ----
        if (curl > 0.05f && ebb < 0.85f) {
            for (int i = 0; i < 58; i++) {
                float h = hash(salt, 7600 + i * 17);
                if (h > 0.62f) continue;                       // discontinue par construction
                float h2 = hash(salt, 7700 + i * 31);
                float h3 = hash(salt, 7800 + i * 11);
                float u = (h2 * 2.0f - 1.0f);
                float columnH = SURGE_HEIGHT * lift * (1.0f - u * u * 0.74f) * body;
                float lf = front - (u * u) * 1.15f + curl * (1.10f + 0.90f * h3);
                float ly = -1.12f + columnH * (0.86f + 0.22f * h3) + curl * 0.22f;
                float size = 0.055f + 0.055f * h3;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, lf, u * SURGE_HALF_WIDTH, ly,
                        size, size * (0.70f + 0.40f * h), size,
                        mix(FOAM, WHITE, h3), Math.round(a * (1.0f - ebb) * 0.92f));
            }
        }

        // ---- 4. Gerbes balistiques : l'eau qui part en l'air au moment du déferlement ----
        if (burst > 0.01f) {
            for (int i = 0; i < 46; i++) {
                float h = hash(salt, 7900 + i * 13);
                float h2 = hash(salt, 8000 + i * 23);
                float h3 = hash(salt, 8100 + i * 37);
                float birth = 0.50f + h * 0.18f;
                float life = (t - birth) / 0.30f;
                if (life <= 0.0f || life >= 1.0f) continue;
                float speed = 2.10f + 2.60f * h2;
                float side = (h3 * 2.0f - 1.0f) * 2.60f;
                float dForward = front + life * speed * (0.45f + 0.55f * h2);
                float dY = -1.12f + life * (3.60f + 1.70f * h) - life * life * (4.30f + 1.60f * h2);
                if (dY < -1.16f) continue;
                float size = (0.055f + 0.050f * h3) * (1.0f - life * 0.45f);
                int rgb = mix(SPRAY, WHITE, h2 * 0.75f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, dForward, side * (0.55f + 0.45f * life), dY,
                        size, size * 1.30f, size,
                        life * 7.0f * (h - 0.5f), life * 5.0f, life * 4.0f, rgb,
                        Math.round(a * burst * (1.0f - life * 0.80f)));
            }
        }

        // ---- 5. Reflux : trois rides concentriques plates qui s'éloignent et meurent ----
        if (ebb > 0.02f) {
            for (int ring = 0; ring < 3; ring++) {
                float phase = ebb - ring * 0.14f;
                if (phase <= 0.0f) continue;
                float radius = 0.90f + phase * (4.60f + ring * 0.85f);
                int count = 30 + ring * 6;
                float ringFade = (1.0f - smooth(phase * 1.12f)) * (1.0f - ebb * 0.25f);
                for (int i = 0; i < count; i++) {
                    float ang = i * TAU / count + ring * 0.22f;
                    float h = hash(salt, 8200 + ring * 97 + i * 5);
                    if (h > 0.86f) continue;
                    float r = radius * (0.95f + 0.10f * Mth.sin(ang * 3.0f + ebb * 5.0f));
                    float y = -1.14f + 0.045f * Mth.sin(ang * 5.0f + t * 12.0f);
                    float size = 0.085f + 0.055f * h;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            size * 1.45f, 0.030f + 0.020f * h, size * 1.45f,
                            mix(SHALLOW, FOAM, h), Math.round(a * ringFade * 0.70f));
                }
            }
        }

        // 7. RUISSELLEMENT — l'eau redescend le long de la face du mur en filets verticaux.
        // Sans cela la paroi est une surface lisse et on ne lit pas sa hauteur.
        if (lift > 0.12f && body > 0.05f) {
            for (int i = 0; i < 34; i++) {
                float h = hash(salt, 8400 + i * 23);
                float h2 = hash(salt, 8500 + i * 41);
                float h3 = hash(salt, 8600 + i * 13);
                float u = h2 * 2.0f - 1.0f;
                float columnH = SURGE_HEIGHT * lift * (1.0f - u * u * 0.74f) * body;
                if (columnH < 0.35f) continue;
                // Le filet part du sommet et descend : phase propre, vitesse propre.
                float fall = fract(t * (0.70f + 0.55f * h3) + h);
                float fy = -1.12f + columnH * (1.0f - fall * 0.92f);
                float forward = front - (u * u) * 1.15f + curl * 0.55f + (h3 - 0.5f) * 0.20f;
                float len = 0.10f + 0.16f * (1.0f - fall);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        forward, u * SURGE_HALF_WIDTH, fy,
                        0.055f, len, 0.045f + 0.025f * h3,
                        mix(SHALLOW, FOAM, 0.35f + 0.55f * (1.0f - fall)),
                        Math.round(a * body * (0.55f - 0.30f * fall)));
            }
        }

        // 8. SOL MOUILLÉ — nappe sombre qui s'étend devant le front : la vague laisse une trace,
        // et surtout cela ancre le mur au terrain au lieu de le laisser flotter.
        if (march > 0.02f) {
            float wetFront = front + 1.10f;
            for (int i = 0; i < 40; i++) {
                float h = hash(salt, 8700 + i * 19);
                float h2 = hash(salt, 8800 + i * 7);
                float along = -3.60f + (wetFront + 3.60f) * h;
                float lateral = (h2 * 2.0f - 1.0f) * SURGE_HALF_WIDTH * (1.05f - 0.25f * h);
                float fade = 1.0f - ebb * 0.80f;
                if (fade <= 0.04f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        along, lateral, -1.16f + 0.015f * h,
                        0.30f + 0.18f * h, 0.022f, 0.30f + 0.18f * h2,
                        mix(DEEP, OCEAN, h * 0.60f),
                        Math.round(a * fade * 0.34f));
            }
        }
    }

    // ============================================================================================
    // 2. MAELSTRÖM ABYSSAL — bassin en entonnoir, rotation différentielle, gorge, geyser
    // ============================================================================================

    /** Rayon du bassin au plus large, en blocs. */
    private static final float BASIN_RADIUS = 4.35f;
    /** Profondeur apparente de l'entonnoir, en blocs. */
    private static final float BASIN_DEPTH = 3.30f;
    /** Hauteur du geyser final, en blocs. */
    private static final float GEYSER_HEIGHT = 9.00f;
    /** Anneaux concentriques du bassin : du bord (0) à la gorge (4). */
    private static final int BASIN_RINGS = 5;

    /**
     * Chorégraphie (150 ticks) :
     * <ul>
     *   <li>0,00–0,20 <b>ouverture</b> : l'eau afflue, le bassin se creuse du bord vers l'axe ;</li>
     *   <li>0,12–0,74 <b>rotation différentielle</b> : cinq anneaux tournent, de plus en plus vite
     *       et de plus en plus bas vers la gorge ; trois bras d'écume en spirale les relient ;</li>
     *   <li>0,60–0,80 <b>implosion</b> : les anneaux se referment sur l'axe, la gorge s'éteint ;</li>
     *   <li>0,76–1,00 <b>geyser</b> : une colonne de 9 blocs jaillit, couronne d'écume et retombée.</li>
     * </ul>
     */
    private static void renderMaelstrom(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.05f) * (1.0f - smooth((t - 0.93f) / 0.07f)));
        if (a <= 4) return;

        float open = smooth(t / 0.20f);
        float spinPhase = t * 10.0f;
        float implode = smooth((t - 0.60f) / 0.20f);
        float geyser = smooth((t - 0.76f) / 0.14f) * (1.0f - smooth((t - 0.94f) / 0.06f));
        float basinLife = (1.0f - implode);

        // ---- 1. Les cinq anneaux du bassin (rotation différentielle : l'axe tourne le plus vite) ----
        if (basinLife > 0.02f) {
            for (int ring = 0; ring < BASIN_RINGS; ring++) {
                float k = ring / (float) (BASIN_RINGS - 1);                  // 0 bord .. 1 gorge
                float radius = BASIN_RADIUS * (1.0f - k * 0.80f) * open
                        * (1.0f - implode * (0.25f + 0.70f * k));
                if (radius < 0.10f) continue;
                float depth = -0.95f - BASIN_DEPTH * k * k * open;
                // Vitesse angulaire : ×1 au bord, ×3,4 à la gorge — c'est ce différentiel qui fait
                // « tourbillon » plutôt que « roue ». Le sens est unique, comme pour la tornade.
                float omega = 1.0f + k * 2.40f;
                int count = Math.max(18, Math.round(44 - ring * 4.0f));
                for (int i = 0; i < count; i++) {
                    float h = hash(salt, 9100 + ring * 211 + i * 7);
                    float ang = i * TAU / count + spinPhase * omega + ring * 0.47f;
                    float wobble = 0.90f + 0.16f * Mth.sin(ang * 3.0f + t * 9.0f + ring);
                    float r = radius * wobble;
                    float y = depth + 0.10f * Mth.sin(ang * 4.0f + t * 11.0f) - 0.05f * h;
                    // Chaque cube est incliné sur la pente de l'entonnoir : la paroi est lisse.
                    float slope = -(0.42f + 0.95f * k);
                    float size = (0.185f - 0.055f * k) * (0.85f + 0.30f * h);
                    int rgb = mix(mix(SHALLOW, OCEAN, k), ABYSS, k * k * 0.85f);
                    int ca = Math.round(a * basinLife * (0.52f + 0.40f * (1.0f - k)));
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            size * 1.55f, size * 0.52f, size * 1.05f,
                            ang, slope, 0.0f, rgb, ca);
                }
            }
        }

        // ---- 2. Trois bras d'écume en spirale logarithmique, du bord à la gorge ----
        if (basinLife > 0.02f) {
            for (int arm = 0; arm < 4; arm++) {
                float armPhase = arm * (TAU / 4.0f);
                for (int i = 0; i < 30; i++) {
                    float v = i / 29.0f;                                       // 0 bord .. 1 gorge
                    float h = hash(salt, 9400 + arm * 131 + i * 17);
                    if (h > 0.90f) continue;
                    float radius = BASIN_RADIUS * (1.0f - v * 0.88f) * open
                            * (1.0f - implode * (0.20f + 0.75f * v));
                    // Spirale : l'angle s'enroule de 1,6 tour entre le bord et la gorge.
                    float ang = armPhase + v * TAU * 1.60f + spinPhase * (1.0f + v * 2.40f);
                    float y = -0.95f - BASIN_DEPTH * v * v * open + 0.06f * Mth.sin(v * 20.0f + t * 14.0f);
                    float size = (0.085f - 0.030f * v) * (0.80f + 0.45f * h);
                    int rgb = mix(FOAM, SPRAY, v * 0.80f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * radius, Mth.sin(ang) * radius, y,
                            size * 1.35f, size * 0.55f, size * 1.35f,
                            rgb, Math.round(a * basinLife * (0.90f - 0.35f * v)));
                }
            }
        }

        // ---- 3. La gorge : cylindre sombre qui aspire, pulsant, au fond de l'entonnoir ----
        if (basinLife > 0.02f) {
            float throat = 0.62f * open * (1.0f - implode);
            for (int level = 0; level < 4; level++) {
                float lv = level / 3.0f;
                float r = throat * (1.0f - lv * 0.55f);
                if (r < 0.05f) continue;
                float y = -0.95f - BASIN_DEPTH * open * (0.72f + 0.26f * lv);
                int count = 14 - level * 2;
                for (int i = 0; i < count; i++) {
                    float ang = i * TAU / count + spinPhase * (3.4f + lv * 1.6f);
                    float pulse = 0.88f + 0.18f * Mth.sin(t * 24.0f + lv * 2.4f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r * pulse, Mth.sin(ang) * r * pulse, y,
                            0.115f, 0.085f, 0.115f,
                            mix(ABYSS, DEEP, lv * 0.45f),
                            Math.round(a * basinLife * (0.95f - 0.20f * lv)));
                }
            }
        }

        // ---- 4. Couronne du bord : l'eau qui déborde du bassin, en crête tournante ----
        if (open > 0.05f && basinLife > 0.02f) {
            int count = 44;
            for (int i = 0; i < count; i++) {
                float h = hash(salt, 9700 + i * 19);
                float ang = i * TAU / count + spinPhase * 0.85f;
                float r = BASIN_RADIUS * open * (1.02f + 0.08f * Mth.sin(ang * 5.0f + t * 8.0f))
                        * (1.0f - implode * 0.30f);
                float y = -1.02f + 0.30f * open * (0.55f + 0.45f * Mth.sin(ang * 3.0f - t * 10.0f))
                        + 0.06f * h;
                float size = 0.095f + 0.055f * h;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        size * 1.40f, size * 0.70f, size * 0.95f,
                        ang, -0.35f, 0.0f, mix(AZURE, FOAM, h * 0.65f),
                        Math.round(a * basinLife * 0.78f));
            }
        }

        // ---- 5. Geyser : la colonne recrachée, sa couronne et ses retombées ----
        if (geyser > 0.01f) {
            float jet = smooth((t - 0.76f) / 0.10f);
            int steps = 26;
            for (int s = 0; s < steps; s++) {
                float v = (s + 0.5f) / steps;
                float h = hash(salt, 9900 + s * 23);
                // Le jet se construit de bas en haut : chaque tranche attend son tour.
                float local = Mth.clamp((jet - v * 0.55f) / 0.45f, 0.0f, 1.0f);
                if (local <= 0.0f) continue;
                float y = -1.10f + GEYSER_HEIGHT * v * local;
                float r = (0.78f - 0.46f * v) * (0.85f + 0.30f * h) * local;
                int count = Math.max(5, Math.round(11 - v * 5.0f));
                for (int i = 0; i < count; i++) {
                    float ang = i * TAU / count + spinPhase * (1.6f + v * 1.2f);
                    float size = (0.145f - 0.070f * v) * (0.85f + 0.28f * h);
                    int rgb = mix(mix(OCEAN, SHALLOW, v), WHITE, v * v * 0.80f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            size, size * 1.55f, size, rgb,
                            Math.round(a * geyser * (0.92f - 0.30f * v)));
                }
            }
            // Couronne : l'eau qui retombe en parapluie au sommet du jet.
            float crown = smooth((t - 0.84f) / 0.12f);
            if (crown > 0.02f) {
                for (int i = 0; i < 40; i++) {
                    float h = hash(salt, 10100 + i * 29);
                    float h2 = hash(salt, 10200 + i * 13);
                    float ang = i * TAU / 40.0f + h * 0.5f;
                    float reach = crown * (1.50f + 2.40f * h2);
                    float y = -1.10f + GEYSER_HEIGHT * (0.92f + 0.08f * h)
                            - crown * crown * (4.20f + 3.10f * h2);
                    if (y < -1.14f) continue;
                    float size = 0.065f + 0.050f * h2;
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * reach, Mth.sin(ang) * reach, y,
                            size, size * 1.25f, size,
                            ang, crown * 3.0f, crown * 2.0f,
                            mix(SPRAY, WHITE, h), Math.round(a * geyser * (1.0f - crown * 0.55f)));
                }
            }
        }

        // 7. NAPPE DE SURFACE — anneau d'eau plaqué au sol, DÉBORDANT du bassin.
        //
        // Rôle technique autant qu'esthétique : depuis que le sol est réellement effacé à l'écran
        // (blocs fantômes, voir GhostBlockMask), il existe une arête nette entre le terrain intact
        // et le bord du trou. Cette nappe la recouvre : l'œil voit de l'eau qui tourne par-dessus
        // le rebord, pas une découpe géométrique.
        if (open > 0.04f) {
            float sheet = basinLife * (0.55f + 0.45f * open);
            for (int ring = 0; ring < 3; ring++) {
                float k = ring / 2.0f;
                float radius = BASIN_RADIUS * (1.02f + 0.16f * k) * open;
                int count = 38 + ring * 8;
                for (int i = 0; i < count; i++) {
                    float h = hash(salt, 10400 + ring * 137 + i * 11);
                    if (h > 0.88f) continue;
                    float ang = i * TAU / count + spinPhase * (0.55f - 0.12f * k) + ring * 0.31f;
                    float r = radius * (0.97f + 0.07f * Mth.sin(ang * 4.0f + t * 7.0f));
                    float y = -1.15f + 0.055f * Mth.sin(ang * 6.0f + t * 9.0f) + 0.02f * h;
                    float size = 0.115f + 0.070f * h;
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            size * 1.50f, 0.028f + 0.018f * h, size * 1.10f,
                            -ang, 0.0f, 0.0f,
                            mix(mix(OCEAN, SHALLOW, k), FOAM, h * 0.45f),
                            Math.round(a * sheet * (0.62f - 0.14f * k)));
                }
            }
        }

        // 8. BULLES — elles remontent du fond de la gorge, toujours, même pendant l'implosion.
        // C'est le seul élément qui se lit DANS le trou et qui en donne la profondeur.
        if (open > 0.08f) {
            for (int i = 0; i < 30; i++) {
                float h = hash(salt, 10600 + i * 29);
                float h2 = hash(salt, 10700 + i * 17);
                float h3 = hash(salt, 10800 + i * 43);
                float climb = fract(t * (0.42f + 0.38f * h3) + h);
                float ang = h2 * TAU + spinPhase * 1.20f * climb;
                float r = (0.22f + 0.95f * h2) * open * (1.0f - climb * 0.55f);
                float y = -0.95f - BASIN_DEPTH * open * (1.0f - climb) + 0.35f * climb;
                float size = (0.045f + 0.040f * h3) * (0.60f + 0.55f * climb);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        size, size, size,
                        mix(SPRAY, WHITE, climb * 0.70f),
                        Math.round(a * basinLife * (0.30f + 0.55f * climb)));
            }
        }
    }

    // ============================================================================================
    // 3. GIVRE DU PERMAFROST — étoile de gel, pics, prison de glace, éclatement
    // ============================================================================================

    /** Hauteur du plus grand pic de glace, en blocs. */
    private static final float SPIKE_HEIGHT = 3.20f;
    /** Nombre de pics de la couronne. */
    private static final int SPIKE_COUNT = 9;

    /**
     * Chorégraphie (120 ticks) :
     * <ul>
     *   <li>0,00–0,22 <b>étoile de gel</b> : huit branches de givre courent au sol en craquelant ;</li>
     *   <li>0,10–0,46 <b>pics</b> : neuf pics percent le sol en couronne, trois gros sous la cible ;</li>
     *   <li>0,30–0,66 <b>prison</b> : un bloc de glace translucide se referme sur la cible ;</li>
     *   <li>0,60–0,80 <b>fissures</b> : des veines sombres courent dans le bloc, qui tremble ;</li>
     *   <li>0,78–1,00 <b>éclatement</b> : soixante éclats partent en tournant, les pics s'effondrent.</li>
     * </ul>
     */
    private static void renderRime(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.04f) * (1.0f - smooth((t - 0.92f) / 0.08f)));
        if (a <= 4) return;

        float creep = smooth(t / 0.22f);
        float grow = smooth((t - 0.10f) / 0.36f);
        float cage = smooth((t - 0.30f) / 0.36f);
        float crack = smooth((t - 0.60f) / 0.20f);
        float shatter = smooth((t - 0.78f) / 0.14f);
        float solid = 1.0f - shatter;

        // ---- 1. Étoile de givre : huit branches qui courent au sol, avec ramifications ----
        for (int branch = 0; branch < 8; branch++) {
            float base = branch * TAU / 8.0f + hash(salt, 11000 + branch) * 0.35f;
            int segs = 16;
            for (int s = 0; s < segs; s++) {
                float v = (s + 1) / (float) segs;
                float local = Mth.clamp((creep - v * 0.55f) / 0.45f, 0.0f, 1.0f);
                if (local <= 0.0f) continue;
                float h = hash(salt, 11100 + branch * 97 + s * 11);
                float ang = base + (h - 0.5f) * 0.30f * v;
                float r = v * 4.60f * local;
                float y = -1.15f + 0.02f * h;
                float size = (0.115f - 0.055f * v) * (0.80f + 0.40f * h);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        size * 1.30f, 0.032f + 0.022f * h, size * 1.30f,
                        mix(ICE_PALE, FROST, h), Math.round(a * local * (0.92f - 0.30f * v) * solid));
                // Ramification : une épine latérale un segment sur trois.
                if (s % 3 == 2 && h > 0.35f) {
                    float side = (hash(salt, 11200 + branch * 31 + s) - 0.5f) > 0.0f ? 1.0f : -1.0f;
                    float sAng = ang + side * 0.62f;
                    float sr = r * 0.82f;
                    float len = 0.42f * (1.0f - v) * local;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(sAng) * (sr + len), Mth.sin(sAng) * (sr + len), y,
                            0.070f, 0.028f, 0.070f,
                            mix(ICE, ICE_PALE, h), Math.round(a * local * 0.70f * solid));
                }
            }
        }

        // ---- 2. Couronne de pics : neuf aiguilles inclinées vers l'extérieur ----
        for (int p = 0; p < SPIKE_COUNT; p++) {
            float h = hash(salt, 11400 + p * 53);
            float h2 = hash(salt, 11500 + p * 29);
            float ang = p * TAU / SPIKE_COUNT + (h - 0.5f) * 0.25f;
            float radius = 1.70f + 1.05f * h2;
            float spikeH = SPIKE_HEIGHT * (0.52f + 0.48f * h) * grow * (1.0f - shatter * 0.85f);
            if (spikeH < 0.10f) continue;
            int steps = Math.max(4, Mth.floor(spikeH / 0.26f));
            float tilt = 0.16f + 0.22f * h2;
            for (int s = 0; s < steps; s++) {
                float v = (s + 0.5f) / steps;
                float y = -1.15f + spikeH * v;
                float lean = tilt * v * v * 1.30f;
                float r = radius + lean;
                float half = (0.205f - 0.165f * v) * (0.85f + 0.30f * h);
                int rgb = mix(mix(ICE_DEEP, ICE, v), FROST, v * v * 0.70f);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        half * 1.20f, spikeH / steps * 0.62f, half * 1.20f,
                        ang, tilt * v, (h2 - 0.5f) * 0.40f, rgb,
                        Math.round(a * (0.70f + 0.28f * v) * (1.0f - shatter * 0.70f)));
            }
        }
        // Trois pics majeurs sous la cible : ils l'encadrent au lieu de la traverser.
        for (int p = 0; p < 3; p++) {
            float h = hash(salt, 11700 + p * 71);
            float ang = p * TAU / 3.0f + 0.4f;
            float spikeH = (2.05f + 0.75f * h) * grow * (1.0f - shatter * 0.80f);
            int steps = Math.max(5, Mth.floor(spikeH / 0.24f));
            for (int s = 0; s < steps; s++) {
                float v = (s + 0.5f) / steps;
                float y = -1.15f + spikeH * v;
                float r = 0.86f + v * 0.30f;
                float half = (0.235f - 0.185f * v) * (0.90f + 0.20f * h);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        half * 1.25f, spikeH / steps * 0.60f, half * 1.25f,
                        ang, 0.24f * v, 0.0f, mix(ICE, FROST, v * 0.80f),
                        Math.round(a * (0.72f + 0.26f * v) * (1.0f - shatter * 0.70f)));
            }
        }

        // ---- 3. Prison de glace : coque translucide autour de la cible, arêtes plus denses ----
        if (cage > 0.02f && solid > 0.02f) {
            float shell = 0.92f * cage;
            float shiver = crack * 0.045f * Mth.sin(t * 52.0f);
            for (int layer = 0; layer < 3; layer++) {
                float lv = layer / 2.0f;
                float r = shell * (0.62f + 0.38f * lv);
                int count = 18 - layer * 3;
                for (int i = 0; i < count; i++) {
                    float h = hash(salt, 11900 + layer * 101 + i * 13);
                    float ang = i * TAU / count + lv * 0.4f + shiver;
                    for (int level = 0; level < 4; level++) {
                        float ly = -0.95f + level * 0.52f;
                        float squeeze = 1.0f - Math.abs(level - 1.5f) * 0.18f;
                        float size = (0.165f + 0.055f * h) * squeeze;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                Mth.cos(ang) * r * squeeze, Mth.sin(ang) * r * squeeze,
                                ly + shiver * 0.5f,
                                size, size * 1.05f, size,
                                mix(ICE, ICE_PALE, h * 0.70f),
                                Math.round(a * cage * solid * (0.38f + 0.22f * lv)));
                    }
                }
            }
            // Veines de fracture : lignes sombres qui apparaissent à partir de 0,60.
            if (crack > 0.03f) {
                for (int i = 0; i < 22; i++) {
                    float h = hash(salt, 12100 + i * 37);
                    float h2 = hash(salt, 12200 + i * 17);
                    if (h > crack) continue;
                    float ang = h2 * TAU;
                    float ly = -0.90f + h * 2.05f;
                    float r = shell * (0.58f + 0.40f * h2);
                    rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, ly,
                            0.028f, 0.165f + 0.120f * h2, 0.028f,
                            ang, (h2 - 0.5f) * 1.6f, (h - 0.5f) * 1.2f,
                            mix(ICE_DEEP, ABYSS, 0.45f), Math.round(a * crack * solid * 0.85f));
                }
            }
        }

        // ---- 4. Éclatement : soixante éclats partent en tournant, puis retombent ----
        if (shatter > 0.01f) {
            for (int i = 0; i < 60; i++) {
                float h = hash(salt, 12400 + i * 19);
                float h2 = hash(salt, 12500 + i * 29);
                float h3 = hash(salt, 12600 + i * 7);
                float ang = h2 * TAU;
                float speed = 1.40f + 3.20f * h;
                float r = shatter * speed;
                float y = -0.70f + h3 * 1.70f + shatter * (1.70f + 1.10f * h2)
                        - shatter * shatter * (3.40f + 2.10f * h);
                if (y < -1.16f) continue;
                float size = (0.085f + 0.075f * h3) * (1.0f - shatter * 0.42f);
                float spin = shatter * (7.0f + 9.0f * h2);
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        size * 1.30f, size * 0.70f, size,
                        ang + spin, spin * 0.75f, spin * 0.55f,
                        mix(mix(ICE, ICE_PALE, h3), FROST, h2 * 0.55f),
                        Math.round(a * (1.0f - shatter * 0.72f)));
            }
            // Souffle de froid au sol : un anneau plat qui s'échappe de l'éclatement.
            for (int i = 0; i < 34; i++) {
                float h = hash(salt, 12800 + i * 11);
                float ang = i * TAU / 34.0f;
                float r = 0.80f + shatter * (4.40f + 1.10f * h);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, -1.14f + 0.03f * h,
                        0.115f, 0.030f, 0.115f,
                        mix(FROST, WHITE, h), Math.round(a * (1.0f - shatter) * 0.80f));
            }
        }

        // 5. BRUME DE FROID — nappe basse qui rampe au sol pendant toute la durée.
        // Elle recouvre la jonction entre le terrain réel et le sol gelé à l'écran
        // (blocs fantômes, voir GhostBlockMask) : sans elle on verrait la découpe du disque.
        if (creep > 0.03f) {
            float haze = creep * (1.0f - shatter * 0.55f);
            for (int i = 0; i < 46; i++) {
                float h = hash(salt, 13000 + i * 31);
                float h2 = hash(salt, 13100 + i * 17);
                float h3 = hash(salt, 13200 + i * 53);
                float drift = fract(t * (0.10f + 0.09f * h3) + h);
                float ang = h2 * TAU + drift * 0.85f;
                float r = (0.70f + 3.80f * h) * creep;
                float y = -1.14f + 0.10f * h3 + 0.05f * Mth.sin(t * 4.0f + h * 9.0f);
                float size = 0.16f + 0.14f * h3;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        size * 1.45f, 0.030f + 0.022f * h2, size * 1.45f,
                        mix(ICE_PALE, FROST, h2),
                        Math.round(a * haze * (0.26f - 0.10f * (r / 4.50f))));
            }
        }

        // 6. CRISTAUX EN SUSPENSION — de la glace qui flotte et tourne lentement autour de la
        // prison. C'est ce qui distingue un bloc de glace posé là d'un sort de givre actif.
        if (cage > 0.04f && solid > 0.04f) {
            for (int i = 0; i < 26; i++) {
                float h = hash(salt, 13400 + i * 37);
                float h2 = hash(salt, 13500 + i * 23);
                float h3 = hash(salt, 13600 + i * 11);
                float orbit = t * (0.55f + 0.70f * h3) + h2 * TAU;
                float r = 1.05f + 1.45f * h;
                float y = -0.85f + 1.90f * h2 + 0.14f * Mth.sin(t * 3.4f + h * 7.0f);
                float size = 0.045f + 0.055f * h3;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(orbit) * r, Mth.sin(orbit) * r, y,
                        size, size * 1.70f, size,
                        orbit, t * 1.8f + h * 6.0f, h2 * 3.0f,
                        mix(ICE, FROST, h3), Math.round(a * cage * solid * (0.45f + 0.35f * h)));
            }
        }
    }

    // ============================================================================================
    // Boîte à outils (mêmes conventions que WindSignatureVfx : avant / latéral / hauteur)
    // ============================================================================================

    /** Cube aligné : {@code f} = axe attaquant→cible, {@code lateral} = perpendiculaire, {@code y} = hauteur. */
    private static void box(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + y, z, hf, hy, hl, rgb, alpha);
    }

    /** Cube orienté (lacet, tangage, roulis) dans le même repère que {@link #box}. */
    private static void rot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl,
                            float yaw, float pitch, float roll, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy + y, z,
                hf, hy, hl, yaw, pitch, roll, rgb, alpha);
    }

    private static int mix(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, clamp(u));
    }

    private static float clamp(float value) { return Mth.clamp(value, 0.0f, 1.0f); }

    /** Partie fractionnaire positive — sert aux cycles qui se rejouent (ruissellement, bulles). */
    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float smooth(float x) {
        x = clamp(x);
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
