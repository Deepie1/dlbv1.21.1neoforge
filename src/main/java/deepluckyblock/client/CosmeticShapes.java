package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T436-FORMES : les géométries cosmétiques réellement dessinées en jeu.
 *
 * <p><b>Problème résolu.</b> Jusqu'ici le jeu ne connaissait qu'une seule forme, {@code haloRing} :
 * un anneau de 10 à 16 points, rappelé avec un rayon et une couleur différents pour le Roi,
 * l'Égide, l'Esprit, l'Avatar #110, #097, #098, et même pour les auras au sol. Autrement dit
 * sept « cosmétiques » qui étaient le même objet repeint, et troué (10 points sur un périmètre
 * de 2 blocs laissent 80 % de vide). Les ailes, elles, sortaient d'une unique fonction partagée.
 * Rien de ce qui a été validé sur l'aperçu n'était branché sur le jeu.</p>
 *
 * <p><b>Règles tenues ici</b> (identiques à l'aperçu, et vérifiables à l'œil) :</p>
 * <ol>
 *   <li><b>Aucun écart.</b> Le nombre de cubes n'est JAMAIS une constante : il se déduit de la
 *       longueur mesurée de la forme, {@code n = ceil(L / 0,9w) + 1}.</li>
 *   <li><b>Une forme par ornement.</b> Aucune géométrie n'est partagée entre deux cosmétiques ;
 *       seuls les outils de tracé le sont, comme un pinceau entre deux tableaux.</li>
 *   <li><b>Jamais devant les yeux.</b> Rien n'occupe le cylindre de rayon 0,55 situé entre la
 *       taille et le sommet du crâne.</li>
 *   <li><b>Repère.</b> Les formes sont décrites dans le repère local du joueur, {@code +Z}
 *       devant lui, {@code y} compté depuis ses pieds. Le pinceau fait la rotation.</li>
 * </ol>
 */
public final class CosmeticShapes {

    private CosmeticShapes() {}

    // ---------------------------------------------------------------- outils

    /** Une courbe paramétrée : {@code u} de 0 à 1 vers un point local. */
    public interface Path {
        void at(float u, float[] out);
    }

    /** Une grandeur qui varie le long d'une courbe (largeur, épaisseur…). */
    public interface Scalar {
        float at(float u);
    }

    /** Couleur et opacité en un point, empaquetées en 0xAARRGGBB. */
    public interface Paint {
        int rgba(float u, float v);
    }

    public static int rgba(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Le pinceau : porte le repère du joueur, les tampons de rendu et le budget.
     * Toutes les coordonnées passées aux primitives sont LOCALES.
     */
    public static final class Brush {
        public VertexConsumer add;
        public VertexConsumer alpha;
        public Matrix4f mat;
        public Vector3f tmp;
        public double px, py, pz;
        public float fwdX, fwdZ, rightX, rightZ;
        public float time;
        public int budget;

        private final float[] a = new float[3];
        private final float[] b = new float[3];
        private final float[] c = new float[3];

        /** Un cube, en coordonnées locales du joueur. */
        public void cube(float lx, float ly, float lz, float hx, float hy, float hz,
                         int rgb, int al) {
            if (budget <= 0) return;
            float wx = (float) px + lx * rightX + lz * fwdX;
            float wz = (float) pz + lx * rightZ + lz * fwdZ;
            WeaponWorldVfxManager.emitVoxelCube(add, mat, tmp, wx, (float) py + ly, wz,
                    hx, hy, hz, rgb, al);
            budget--;
        }

        /** Un cube en coordonnées MONDE : pour ce qui est posé au sol et n'y bouge plus. */
        public void cubeWorld(double wx, double wy, double wz, float hx, float hy, float hz,
                              int rgb, int al) {
            if (budget <= 0) return;
            WeaponWorldVfxManager.emitVoxelCube(add, mat, tmp, (float) wx, (float) wy, (float) wz,
                    hx, hy, hz, rgb, al);
            budget--;
        }
    }

    private static int pasOuvert(float L, float w) {
        return Math.max(2, Mth.ceil(L / (0.9f * w)) + 1);
    }

    private static int pasFerme(float L, float w) {
        return Math.max(3, Mth.ceil(L / (0.9f * w)));
    }

    /** Longueur d'une courbe, mesurée et non devinée. */
    private static float longueur(Path p, float[] a, float[] b) {
        float L = 0f;
        p.at(0f, a);
        for (int i = 1; i <= 24; i++) {
            p.at(i / 24f, b);
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            L += Mth.sqrt(dx * dx + dy * dy + dz * dz);
            a[0] = b[0];
            a[1] = b[1];
            a[2] = b[2];
        }
        return L;
    }

    /** Trace une courbe pleine, d'épaisseur variable. */
    public static void courbe(Brush br, Path p, float w, Scalar ep, Paint paint) {
        float L = longueur(p, br.a, br.b);
        int n = pasOuvert(L, w);
        for (int i = 0; i < n && br.budget > 0; i++) {
            float u = i / (float) (n - 1);
            p.at(u, br.a);
            float h = ep == null ? w * 0.5f : ep.at(u);
            int col = paint.rgba(u, 0f);
            br.cube(br.a[0], br.a[1], br.a[2], h, h, h, col & 0xFFFFFF, (col >>> 24));
        }
    }

    /** Trace un segment plein entre deux points locaux. */
    public static void trait(Brush br, float x0, float y0, float z0,
                             float x1, float y1, float z1, float w, Paint paint) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float L = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        int n = pasOuvert(L, w);
        for (int i = 0; i < n && br.budget > 0; i++) {
            float u = i / (float) (n - 1);
            int col = paint.rgba(u, 0f);
            float h = w * 0.5f;
            br.cube(x0 + dx * u, y0 + dy * u, z0 + dz * u, h, h, h,
                    col & 0xFFFFFF, (col >>> 24));
        }
    }

    /**
     * Une bande : une courbe élargie latéralement. {@code v} va de -0,5 à +0,5 en travers.
     * Le nombre de rangées se déduit de la largeur, donc une bande n'est jamais ajourée.
     */
    public static void bande(Brush br, Path axe, float w, Scalar largeur,
                             float latX, float latY, float latZ, Paint paint, float hy) {
        float L = longueur(axe, br.a, br.b);
        int n = pasOuvert(L, w);
        for (int i = 0; i < n && br.budget > 0; i++) {
            float u = i / (float) (n - 1);
            axe.at(u, br.c);
            float lg = Math.max(0f, largeur.at(u));
            int nv = lg <= w * 0.5f ? 1 : pasOuvert(lg, w);
            for (int j = 0; j < nv && br.budget > 0; j++) {
                float v = nv == 1 ? 0f : (j / (float) (nv - 1)) - 0.5f;
                int col = paint.rgba(u, v);
                br.cube(br.c[0] + latX * lg * v, br.c[1] + latY * lg * v, br.c[2] + latZ * lg * v,
                        w * 0.5f, hy > 0 ? hy : w * 0.5f, w * 0.5f,
                        col & 0xFFFFFF, (col >>> 24));
            }
        }
    }

    /** Un anneau horizontal plein, de {@code rI} à {@code rE} : une plaque, pas un fil. */
    public static void plaqueAnnulaire(Brush br, float cy, float rI, float rE, float w,
                                       float phase, Paint paint, float hy) {
        int nu = pasFerme((float) (Math.PI * 2.0) * rE, w);
        int nv = rE - rI <= w * 0.5f ? 1 : pasOuvert(rE - rI, w);
        for (int i = 0; i < nu && br.budget > 0; i++) {
            float u = i / (float) nu;
            float ang = phase + (float) (Math.PI * 2.0) * u;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            for (int j = 0; j < nv && br.budget > 0; j++) {
                float v = nv == 1 ? 0f : j / (float) (nv - 1);
                float r = rI + (rE - rI) * v;
                int col = paint.rgba(u, v);
                br.cube(ca * r, cy, sa * r, w * 0.5f, hy, w * 0.5f,
                        col & 0xFFFFFF, (col >>> 24));
            }
        }
    }

    /** Un disque horizontal plein, en coordonnées monde (pour les marques au sol). */
    public static void disqueMonde(Brush br, double cx, double cy, double cz,
                                   float rayon, float w, int rgb, int al, float hy) {
        int anneaux = Math.max(1, pasOuvert(rayon, w));
        for (int k = 0; k < anneaux && br.budget > 0; k++) {
            float fr = k / (float) Math.max(1, anneaux - 1);
            float r = rayon * fr;
            if (r < 0.001f) {
                br.cubeWorld(cx, cy, cz, w * 0.5f, hy, w * 0.5f, rgb, al);
                continue;
            }
            int n = pasFerme((float) (Math.PI * 2.0) * r, w);
            for (int i = 0; i < n && br.budget > 0; i++) {
                float ang = (float) (Math.PI * 2.0) * i / n;
                br.cubeWorld(cx + Mth.cos(ang) * r, cy, cz + Mth.sin(ang) * r,
                        w * 0.5f, hy, w * 0.5f, rgb, Math.round(al * (1f - fr * 0.25f)));
            }
        }
    }

    /** Une petite boule pleine : jamais un pixel seul perdu en l'air. */
    public static void bille(Brush br, float cx, float cy, float cz, float r,
                             int rgb, int al, int rgbBord) {
        float h = r * 0.62f;
        br.cube(cx, cy, cz, h, h, h, rgb, al);
        br.cube(cx + r, cy, cz, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
        br.cube(cx - r, cy, cz, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
        br.cube(cx, cy + r, cz, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
        br.cube(cx, cy - r, cz, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
        br.cube(cx, cy, cz + r, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
        br.cube(cx, cy, cz - r, h * 0.78f, h * 0.78f, h * 0.78f, rgbBord, Math.round(al * 0.85f));
    }

    private static int mix(int a, int b, float t) {
        return WeaponWorldVfxManager.mixRgb(a, b, Mth.clamp(t, 0f, 1f));
    }

    // =====================================================================
    //  1. HALO GÉNÉRIQUE — plaque cannelée, dentée, avec comète et lucioles
    // =====================================================================
    public static void haloContinu(Brush br, float crane) {
        final float t = br.time;
        final float y = crane + 0.205f + Mth.sin(t * 1.1f) * 0.012f;
        final float rI = 0.236f, rE = 0.330f, w = 0.046f;
        final float ph = t * 0.20f;
        final float comete = (t * 0.17f) % 1f;

        plaqueAnnulaire(br, y, rI, rE, w, ph, (u, v) -> {
            float d = Math.abs(u - comete);
            if (d > 0.5f) d = 1f - d;
            float tete = Math.max(0f, 1f - d * 12f);
            float q = comete - u;
            if (q < 0f) q += 1f;
            float queue = Math.max(0f, 1f - q * 6f) * 0.70f;
            float g = Math.max(tete, queue);
            float cannelure = 0.5f + 0.5f * Mth.cos((float) (Math.PI * 2.0) * u * 36f);
            float bord = Math.abs(v - 0.5f) * 2f;
            int or = mix(0xD99A14, 0xFFF3C8, 0.20f + cannelure * 0.40f + (1f - bord) * 0.34f);
            int al = Math.round((188 + cannelure * 34 + g * 56) * (1f - bord * 0.26f));
            return rgba(mix(or, 0xFFFFFF, g * 0.88f), Math.min(255, al));
        }, 0.011f);

        for (int i = 0; i < 20 && br.budget > 0; i++) {
            float ang = ph + (float) (Math.PI * 2.0) * i / 20f;
            float lg = (i % 3 == 0) ? 0.056f : 0.030f;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            trait(br, ca * (rE - 0.012f), y, sa * (rE - 0.012f),
                    ca * (rE + lg), y, sa * (rE + lg), 0.032f,
                    (u, v) -> rgba(mix(0xFFD764, 0xFFFFFF, u), Math.round(228 - u * 130)));
        }
        for (int i = 0; i < 3 && br.budget > 0; i++) {
            float ang = -ph * 1.7f + (float) (Math.PI * 2.0) * i / 3f;
            float r = rE + 0.085f + Mth.sin(t * 1.9f + i * 2.1f) * 0.022f;
            bille(br, Mth.cos(ang) * r, y + 0.034f + Mth.sin(t * 2.4f + i) * 0.018f,
                    Mth.sin(ang) * r, 0.016f, 0xFFF3C8, 215, 0xFFC63D);
        }
    }

    // =====================================================================
    //  2. #110 AVATAR — soleil à deux couronnes de rayons
    // =====================================================================
    public static void soleilAvatar(Brush br, float crane) {
        final float t = br.time;
        final float ph = t * 0.24f, y = crane + 0.234f;
        final float rI = 0.148f, rE = 0.212f;

        plaqueAnnulaire(br, y, rI, rE, 0.046f, ph, (u, v) -> {
            float grain = 0.5f + 0.5f * Mth.cos((float) (Math.PI * 2.0) * u * 24f);
            return rgba(mix(0xE08A00, 0xFFF4C4, v * 0.7f + grain * 0.3f), Math.round(252 - v * 18));
        }, 0.011f);

        for (int i = 0; i < 12 && br.budget > 0; i++) {
            final float ang = ph + (float) (Math.PI * 2.0) * i / 12f;
            final float ca = Mth.cos(ang), sa = Mth.sin(ang);
            final float lng = 0.178f * (1f + Mth.sin(t * 2.0f + i * 0.9f) * 0.14f);
            bande(br, (u, o) -> {
                o[0] = ca * (rE - 0.016f + lng * u);
                o[1] = y + u * 0.026f;
                o[2] = sa * (rE - 0.016f + lng * u);
            }, 0.042f, (u) -> 0.062f * (1f - u) * (1f - u * 0.30f), -sa, 0f, ca,
                    (u, v) -> rgba(mix(0xFFC21E, 0xFFFFFF, u + (1f - Math.abs(v) * 2f) * 0.25f),
                            Math.round(250 - u * 135)), 0.011f);

            final float b = ang + (float) Math.PI / 12f;
            final float cb = Mth.cos(b), sb = Mth.sin(b);
            final float lc = 0.082f * (1f - Mth.sin(t * 2.0f + i * 0.9f) * 0.14f);
            bande(br, (u, o) -> {
                o[0] = cb * (rE - 0.012f + lc * u);
                o[1] = y - u * 0.012f;
                o[2] = sb * (rE - 0.012f + lc * u);
            }, 0.038f, (u) -> 0.040f * (1f - u), -sb, 0f, cb,
                    (u, v) -> rgba(mix(0xFF9A00, 0xFFE9A0, u), Math.round(228 - u * 120)), 0.010f);
        }
        for (int i = 0; i < 4 && br.budget > 0; i++) {
            float ang = -ph * 1.6f + (float) (Math.PI * 2.0) * i / 4f;
            float r = rE + 0.075f + Mth.sin(t * 2.6f + i * 1.6f) * 0.030f;
            bille(br, Mth.cos(ang) * r, y + 0.030f, Mth.sin(ang) * r, 0.018f, 0xFFE9A0, 225, 0xFF8A00);
        }
    }

    // =====================================================================
    //  3. #097 — chaîne de maillons ovales croisés
    // =====================================================================
    public static void chaineEternelle(Brush br, float crane) {
        final float t = br.time;
        final float y = crane + 0.205f, R = 0.252f;
        final int n = 14;
        for (int k = 0; k < n && br.budget > 0; k++) {
            final float a0 = t * 0.30f + (float) (Math.PI * 2.0) * k / n;
            final float cx = Mth.cos(a0) * R, cz = Mth.sin(a0) * R;
            final boolean debout = (k % 2) == 0;
            final float rx = debout ? 0.034f : 0.055f;
            final float ry = debout ? 0.055f : 0.022f;
            final float tx = -Mth.sin(a0), tz = Mth.cos(a0);
            final int teinte = debout ? 0xFFD264 : 0xC78A14;
            courbe(br, (u, o) -> {
                float ang = (float) (Math.PI * 2.0) * u;
                float ex = Mth.cos(ang) * rx, ey = Mth.sin(ang) * ry;
                o[0] = cx + tx * ex;
                o[1] = y + ey;
                o[2] = cz + tz * ex;
            }, 0.030f, (u) -> 0.015f,
                    (u, v) -> rgba(mix(teinte, 0xFFF3CF, 0.3f + 0.4f * Mth.sin(u * 6.3f)), 244));
        }
    }

    // =====================================================================
    //  4. #098 — six vraies épées dressées
    // =====================================================================
    public static void epeesDieuLame(Brush br, float crane) {
        final float t = br.time;
        final float ph = -t * 0.28f, r = 0.268f;
        for (int i = 0; i < 6 && br.budget > 0; i++) {
            final float ang = ph + (float) (Math.PI * 2.0) * i / 6f;
            final float ca = Mth.cos(ang), sa = Mth.sin(ang);
            final float tx = -sa, tz = ca;
            final float yg = crane + 0.172f + Mth.sin(t * 1.6f + i * 1.047f) * 0.020f;
            final float Lb = 0.268f, pencher = 0.135f;

            bande(br, (u, o) -> {
                o[0] = ca * (r + pencher * u);
                o[1] = yg + 0.034f + Lb * u;
                o[2] = sa * (r + pencher * u);
            }, 0.036f, (u) -> 0.060f * (u > 0.82f ? (1f - u) / 0.18f : 1f - u * u * 0.30f),
                    tx, 0f, tz, (u, v) -> {
                        float d = Math.min(1f, Math.abs(v) * 2f);
                        float gouttiere = (float) Math.pow(1f - d, 2.4);
                        int c = mix(mix(0xB07A10, 0xFFBE28, 0.35f + (1f - d) * 0.45f), 0xFFFFFF,
                                gouttiere * 0.70f + (d > 0.86f ? 0.45f : 0f));
                        return rgba(c, Math.round(252 - u * 26));
                    }, 0f);

            for (int s = -1; s <= 1; s += 2) {
                final int cote = s;
                courbe(br, (u, o) -> {
                    o[0] = ca * r + tx * cote * 0.082f * u;
                    o[1] = yg + 0.030f * u * u;
                    o[2] = sa * r + tz * cote * 0.082f * u;
                }, 0.032f, (u) -> 0.017f - u * 0.003f,
                        (u, v) -> rgba(mix(0xFFE9A0, 0xFFB400, u * 0.7f), 246));
                bille(br, ca * r + tx * cote * 0.086f, yg + 0.032f, sa * r + tz * cote * 0.086f,
                        0.015f, 0xFFF3C8, 246, 0xFFB400);
            }
            for (int k = 0; k < 4 && br.budget > 0; k++) {
                br.cube(ca * r, yg - 0.020f - k * 0.019f, sa * r, 0.017f, 0.010f, 0.017f,
                        mix(0x8A4A08, (k % 2) == 1 ? 0xD89A3C : 0x5E2F04, 0.5f), 240);
            }
            bille(br, ca * r, yg - 0.108f, sa * r, 0.020f,
                    mix(0xFFD98A, 0xFFFFFF, (Mth.sin(t * 2f + i) + 1f) / 2f), 248, 0xA85F0C);
        }
    }

    // =====================================================================
    //  5. ROI — couronne royale sertie
    // =====================================================================
    public static void couronneRoi(Brush br, float crane) {
        final float t = br.time;
        final float r = 0.170f, yb = crane + 0.022f, hb = 0.066f;
        final float apex = yb + hb + 0.200f;
        final float lueur = (Mth.sin(t * 1.5f) + 1f) / 2f;
        final int OR = 0xB07A10, ORCLAIR = 0xFFE07A, ORVIF = 0xFFF6D0;

        // bandeau à double jonc
        int nu = pasFerme((float) (Math.PI * 2.0) * r, 0.042f);
        int nv = pasOuvert(hb, 0.042f);
        for (int i = 0; i < nu && br.budget > 0; i++) {
            float ang = (float) (Math.PI * 2.0) * i / nu;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            float grain = 0.5f + 0.5f * Mth.cos((float) (Math.PI * 2.0) * (i / (float) nu) * 28f);
            for (int j = 0; j < nv && br.budget > 0; j++) {
                float v = j / (float) (nv - 1);
                float jonc = (v < 0.17f || v > 0.83f) ? 1f : 0f;
                br.cube(ca * r, yb + hb * v, sa * r, 0.021f, 0.021f, 0.021f,
                        mix(mix(OR, ORCLAIR, 0.20f + grain * 0.35f), ORVIF, jonc * 0.75f), 246);
            }
        }

        // huit pierres serties, alternées, séparées par des perles
        for (int i = 0; i < 8 && br.budget > 0; i++) {
            float ang = (float) (Math.PI * 2.0) * i / 8f + (float) Math.PI / 8f;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            boolean rubis = (i % 2) == 0;
            int teinte = rubis ? mix(0xC01030, 0xFF7090, lueur * 0.55f)
                    : mix(0x0E8A46, 0x74E8A8, lueur * 0.55f);
            br.cube(ca * (r + 0.012f), yb + hb * 0.5f, sa * (r + 0.012f),
                    0.019f, 0.021f, 0.019f, teinte, 248);
            br.cube(ca * (r + 0.020f), yb + hb * 0.5f, sa * (r + 0.020f),
                    0.011f, 0.013f, 0.011f, mix(teinte, 0xFFFFFF, 0.55f), 240);
            float b = ang + (float) (Math.PI * 2.0) / 16f;
            br.cube(Mth.cos(b) * (r + 0.010f), yb + hb * 0.5f, Mth.sin(b) * (r + 0.010f),
                    0.013f, 0.013f, 0.013f, mix(0xFFF6D0, 0xFFFFFF, lueur * 0.4f), 244);
        }

        // quatre croix pattées et quatre fleurs de lys, en alternance
        for (int i = 0; i < 8 && br.budget > 0; i++) {
            final float ang = (float) (Math.PI * 2.0) * i / 8f - (float) Math.PI / 2f;
            final float ca = Mth.cos(ang), sa = Mth.sin(ang);
            final float tx = -sa, tz = ca;
            final float y0 = yb + hb;
            if (i % 2 == 0) {
                bande(br, (u, o) -> {
                    o[0] = ca * r;
                    o[1] = y0 + 0.098f * u;
                    o[2] = sa * r;
                }, 0.034f, (u) -> 0.046f * (0.55f + 0.45f * Math.abs(u * 2f - 1f)),
                        tx, 0f, tz, (u, v) -> rgba(mix(ORCLAIR, ORVIF, u), 250), 0f);
                bande(br, (u, o) -> {
                    o[0] = ca * r + tx * (u - 0.5f) * 0.098f;
                    o[1] = y0 + 0.058f;
                    o[2] = sa * r + tz * (u - 0.5f) * 0.098f;
                }, 0.034f, (u) -> 0.040f * (0.55f + 0.45f * Math.abs(u * 2f - 1f)),
                        0f, 1f, 0f, (u, v) -> rgba(ORCLAIR, 248), 0f);
                float gros = (i == 0) ? 1.55f : 1.0f;
                br.cube(ca * (r + 0.008f), y0 + 0.058f, sa * (r + 0.008f),
                        0.014f * gros, 0.014f * gros, 0.014f * gros,
                        mix(0xC01030, 0xFF8FA8, 0.25f + lueur * 0.6f), 252);
            } else {
                trait(br, ca * r, y0, sa * r, ca * r, y0 + 0.086f, sa * r, 0.034f,
                        (u, v) -> rgba(mix(ORCLAIR, ORVIF, u), 248));
                for (int s = -1; s <= 1; s += 2) {
                    final int cote = s;
                    courbe(br, (u, o) -> {
                        float e = 0.012f + 0.046f * Mth.sin(u * 1.9f);
                        o[0] = ca * r + tx * cote * e;
                        o[1] = y0 + 0.016f + 0.062f * u - 0.020f * u * u;
                        o[2] = sa * r + tz * cote * e;
                    }, 0.030f, (u) -> 0.015f,
                            (u, v) -> rgba(mix(ORCLAIR, ORVIF, u), Math.round(244 - u * 40)));
                }
            }
        }

        // quatre arches qui se rejoignent sur le monde
        for (int i = 0; i < 4 && br.budget > 0; i++) {
            final float ang = (float) (Math.PI * 2.0) * i / 4f - (float) Math.PI / 2f;
            final float ca = Mth.cos(ang), sa = Mth.sin(ang);
            final float y0 = yb + hb + 0.100f;
            courbe(br, (u, o) -> {
                float rr = r * (1f - u) + 0.012f * Mth.sin((float) Math.PI * u);
                o[0] = ca * rr;
                o[1] = y0 + (apex - y0) * (float) Math.pow(u, 0.62);
                o[2] = sa * rr;
            }, 0.038f, (u) -> 0.019f - u * 0.004f,
                    (u, v) -> rgba(mix(OR, ORVIF, 0.35f + u * 0.55f), 250));
        }

        // le monde, son cercle équatorial, sa croix
        bille(br, 0f, apex + 0.014f, 0f, 0.024f, mix(0x2A6CC8, 0x9CD0FF, lueur), 250, 0xFFE07A);
        courbe(br, (u, o) -> {
            float ang = (float) (Math.PI * 2.0) * u;
            o[0] = Mth.cos(ang) * 0.026f;
            o[1] = apex + 0.014f;
            o[2] = Mth.sin(ang) * 0.026f;
        }, 0.022f, (u) -> 0.011f, (u, v) -> rgba(ORVIF, 250));
        trait(br, 0f, apex + 0.034f, 0f, 0f, apex + 0.088f, 0f, 0.024f,
                (u, v) -> rgba(mix(ORCLAIR, 0xFFFFFF, u), 252));
        trait(br, -0.024f, apex + 0.068f, 0f, 0.024f, apex + 0.068f, 0f, 0.022f,
                (u, v) -> rgba(ORVIF, 250));
    }

    // =====================================================================
    //  6. ESPRIT MYSTIQUE — lemniscate épaisse, runes, satellites
    // =====================================================================
    public static void espritMystique(Brush br, float crane) {
        final float t = br.time;
        final float A = 0.305f, y = crane + 0.260f, spin = t * 0.42f;
        for (int rang = 0; rang < 2; rang++) {
            final float dy = rang == 0 ? 0f : 0.032f;
            final float ep = rang == 0 ? 0.020f : 0.015f;
            final int al = rang == 0 ? 215 : 180;
            courbe(br, (u, o) -> {
                float th = (float) (Math.PI * 2.0) * u;
                float d = 1f + Mth.sin(th) * Mth.sin(th);
                float lx = A * Mth.cos(th) / d, lz = A * Mth.sin(th) * Mth.cos(th) / d;
                o[0] = lx * Mth.cos(spin) - lz * Mth.sin(spin);
                o[1] = y + dy + Mth.sin(th * 2f) * 0.026f;
                o[2] = lx * Mth.sin(spin) + lz * Mth.cos(spin);
            }, 0.034f, (u) -> ep, (u, v) -> {
                float eclat = 0f;
                for (int k = 0; k < 3; k++) {
                    float d = Math.abs(u - ((t * 0.21f + k / 3f) % 1f));
                    if (d > 0.5f) d = 1f - d;
                    eclat = Math.max(eclat, Math.max(0f, 1f - d * 13f));
                }
                int c = mix(mix(0x7B3BE8, 0xC08BFF, (Mth.sin(u * 12.566f) + 1f) / 2f), 0xF2E6FF, eclat);
                return rgba(c, Math.min(255, Math.round(al + eclat * 45)));
            });
        }
        // trois runes carrées
        for (int k = 0; k < 3 && br.budget > 0; k++) {
            final float ang = spin * 1.4f + (float) (Math.PI * 2.0) * k / 3f;
            final float cx = Mth.cos(ang) * 0.335f, cz = Mth.sin(ang) * 0.335f;
            final float yy = y + Mth.sin(t * 1.5f + k * 1.6f) * 0.040f;
            final float puls = 0.5f + 0.5f * Mth.sin(t * 2.2f + k);
            final float cote = 0.040f;
            courbe(br, (u, o) -> {
                float q = u * 4f;
                int e = ((int) q) % 4;
                float f = q - (int) q;
                float[] sx = {-1f, 1f, 1f, -1f};
                float[] sz = {-1f, -1f, 1f, 1f};
                float[] nx = {1f, 1f, -1f, -1f};
                float[] nz = {-1f, 1f, 1f, -1f};
                o[0] = cx + (sx[e] + (nx[e] - sx[e]) * f) * cote;
                o[1] = yy;
                o[2] = cz + (sz[e] + (nz[e] - sz[e]) * f) * cote;
            }, 0.030f, (u) -> 0.015f,
                    (u, v) -> rgba(mix(0x9B5BFF, 0xF2E6FF, puls), Math.round(160 + puls * 70)));
        }
        for (int k = 0; k < 2 && br.budget > 0; k++) {
            float ang = -spin * 2.1f + (float) Math.PI * k;
            bille(br, Mth.cos(ang) * 0.215f, y + 0.090f + Mth.sin(t * 1.8f + k * 2f) * 0.030f,
                    Mth.sin(ang) * 0.215f, 0.017f, 0xE6D2FF, 230, 0x7B3BE8);
        }
    }

    // =====================================================================
    //  7. ÉGIDE RADIEUSE — mur de pavois (surtout pas une coupole)
    // =====================================================================
    public static void egideRadieuse(Brush br, float crane) {
        final float t = br.time;
        final float ph = t * 0.26f, R = 0.302f, yc = crane + 0.240f;
        final float Lx = 0.196f, Hy = 0.232f;
        for (int i = 0; i < 6 && br.budget > 0; i++) {
            final float ang = ph + (float) (Math.PI * 2.0) * i / 6f;
            final float ca = Mth.cos(ang), sa = Mth.sin(ang);
            final float tx = -sa, tz = ca;
            final float bat = Mth.sin(t * 1.7f + i * 1.05f) * 0.012f;

            bande(br, (u, o) -> {
                o[0] = ca * (R + bat);
                o[1] = yc + Hy * (0.5f - u);
                o[2] = sa * (R + bat);
            }, 0.046f, (u) -> Lx * (u < 0.40f ? 1f : 1f - (float) Math.pow((u - 0.40f) / 0.60f, 1.7)),
                    tx, 0f, tz, (u, v) -> {
                        float bordLat = Math.abs(v) * 2f;
                        boolean rim = (u < 0.10f || u > 0.93f) || bordLat > 0.80f;
                        int c = mix(mix(0x2E5A80, 0x6F9FC8, 0.35f + (1f - bordLat) * 0.5f),
                                0xE8F6FF, rim ? 0.85f : 0f);
                        return rgba(c, rim ? 250 : Math.round(168 + (1f - bordLat) * 46));
                    }, 0f);

            bille(br, ca * (R + 0.026f + bat), yc + Hy * 0.10f, sa * (R + 0.026f + bat), 0.020f,
                    mix(0xFFF2C0, 0xFFFFFF, (Mth.sin(t * 2.1f + i) + 1f) / 2f), 252, 0xFFC63D);

            float[] du = {0.14f, 0.14f, 0.52f, 0.52f};
            float[] dv = {-0.33f, 0.33f, -0.26f, 0.26f};
            for (int k = 0; k < 4 && br.budget > 0; k++) {
                float prof = du[k] < 0.40f ? 1f : 1f - (float) Math.pow((du[k] - 0.40f) / 0.60f, 1.7);
                float lv = Lx * prof * dv[k];
                br.cube(ca * (R + 0.016f + bat) + tx * lv, yc + Hy * (0.5f - du[k]),
                        sa * (R + 0.016f + bat) + tz * lv, 0.012f, 0.012f, 0.012f, 0xE8F6FF, 246);
            }

            final float b = ang + (float) Math.PI / 6f;
            final float cb = Mth.cos(b), sb = Mth.sin(b);
            final float puls = 0.55f + 0.45f * Mth.sin(t * 2.4f + i * 1.9f);
            bande(br, (u, o) -> {
                o[0] = cb * (R - 0.010f + 0.155f * u * puls);
                o[1] = yc + 0.012f;
                o[2] = sb * (R - 0.010f + 0.155f * u * puls);
            }, 0.050f, (u) -> 0.050f * (1f - u), -sb, 0f, cb,
                    (u, v) -> rgba(mix(0xFFF2C0, 0xFFFFFF, u),
                            Math.max(0, Math.round((235 - u * 165) * puls))), 0.012f);
        }
    }

    // =====================================================================
    //  8. AILE DE L'ARCHANGE — charpente, rémiges, couvertures, duvet
    // =====================================================================
    /** Une plume : rachis clair au centre, vexilles asymétriques, barbes. */
    private static void plume(Brush br, float[] base, float[] dir, float L, float larg,
                              float[] lat, int couleur, int pointe, int alpha, float ombre,
                              int barbes, float w) {
        for (int s = -1; s <= 1; s += 2) {
            final int cote = s;
            final float gain = (s < 0 ? 0.80f : 1.0f) * 0.5f;
            bande(br, (u, o) -> {
                float dec = larg * profilPlume(u) * gain * 0.5f * cote;
                o[0] = base[0] + dir[0] * L * u + lat[0] * dec;
                o[1] = base[1] + dir[1] * L * u + lat[1] * dec;
                o[2] = base[2] + dir[2] * L * u + lat[2] * dec;
            }, w, (u) -> larg * profilPlume(u) * gain, lat[0], lat[1], lat[2], (u, v) -> {
                float d = Math.min(1f, Math.abs(v * 2f + cote));
                float rachis = (float) Math.pow(1f - d, 2.2);
                float strie = 0.5f + 0.5f * Mth.cos(u * 26f + v * 11f);
                int c = mix(mix(couleur, pointe, (float) Math.pow(u, 0.78)), 0xFFFFFF,
                        rachis * 0.50f + strie * 0.10f);
                return rgba(c, Math.max(0, Math.round((alpha - u * 40) * ombre * (1f - d * 0.18f))));
            }, 0f);
        }
        for (int i = 0; i < barbes && br.budget > 0; i++) {
            float u = 0.34f + 0.52f * (i / (float) Math.max(1, barbes - 1));
            float demi = larg * profilPlume(u) * 0.5f;
            for (int s = -1; s <= 1; s += 2) {
                float bx = base[0] + dir[0] * L * u + lat[0] * demi * s;
                float by = base[1] + dir[1] * L * u + lat[1] * demi * s;
                float bz = base[2] + dir[2] * L * u + lat[2] * demi * s;
                trait(br, bx, by, bz,
                        bx + lat[0] * demi * s * 0.34f + dir[0] * 0.042f,
                        by + lat[1] * demi * s * 0.34f + dir[1] * 0.042f,
                        bz + lat[2] * demi * s * 0.34f + dir[2] * 0.042f, w * 0.80f,
                        (uu, vv) -> rgba(mix(pointe, 0xFFFFFF, uu * 0.4f),
                                Math.max(0, Math.round((alpha - 86) * ombre))));
            }
        }
    }

    private static float profilPlume(float u) {
        float ouvre = Mth.clamp((u - 0.08f) / 0.40f, 0f, 1f);
        float s = ouvre * ouvre * (3f - 2f * ouvre);
        return (0.26f + 0.74f * s) * (1f - (float) Math.pow(u, 3) * 0.94f);
    }

    private static void norm(float[] v) {
        float n = Mth.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (n > 1.0e-5f) {
            v[0] /= n;
            v[1] /= n;
            v[2] /= n;
        }
    }

    /** Latérale d'une plume : perpendiculaire à sa direction, dans le plan du dos. */
    private static float[] lateralePour(float[] d) {
        float[] ref = {0f, 0f, 1f};
        float dot = d[0] * ref[0] + d[1] * ref[1] + d[2] * ref[2];
        float[] out = {ref[0] - d[0] * dot, ref[1] - d[1] * dot, ref[2] - d[2] * dot};
        norm(out);
        return out;
    }

    public static void aileSeraphin(Brush br, int side, float echelle) {
        final float t = br.time;
        final float s = side;
        final float resp = Mth.sin(t * 1.15f);
        final float[] E = {s * 0.110f * echelle, 1.400f * echelle, -0.200f};
        final float[] C = {s * 0.235f * echelle, (1.722f + resp * 0.026f) * echelle, -0.316f};
        final float[] P = {s * 0.398f * echelle, (1.988f + resp * 0.042f) * echelle, -0.398f};
        final float[] M = {s * 0.524f * echelle, (1.930f + resp * 0.048f) * echelle, -0.442f};
        final int OR = 0xD99420, CHAUD = 0xFFD264, CLAIR = 0xFFF3CF, PALE = 0xFFFBEA;

        // charpente : humérus, avant-bras, main
        courbe(br, (u, o) -> {
            if (u < 0.42f) {
                float w = u / 0.42f;
                o[0] = E[0] + (C[0] - E[0]) * w;
                o[1] = E[1] + (C[1] - E[1]) * w;
                o[2] = E[2] + (C[2] - E[2]) * w;
            } else if (u < 0.80f) {
                float w = (u - 0.42f) / 0.38f;
                o[0] = C[0] + (P[0] - C[0]) * w;
                o[1] = C[1] + (P[1] - C[1]) * w;
                o[2] = C[2] + (P[2] - C[2]) * w;
            } else {
                float w = (u - 0.80f) / 0.20f;
                o[0] = P[0] + (M[0] - P[0]) * w;
                o[1] = P[1] + (M[1] - P[1]) * w;
                o[2] = P[2] + (M[2] - P[2]) * w;
            }
        }, 0.062f, (u) -> 0.031f - u * 0.013f,
                (u, v) -> rgba(mix(OR, CLAIR, 0.30f + u * 0.45f), Math.round(250 - u * 30)));
        bille(br, C[0], C[1], C[2], 0.030f, CLAIR, 250, OR);
        bille(br, P[0], P[1], P[2], 0.026f, CLAIR, 250, OR);

        // sept rémiges primaires, qui s'écartent comme des doigts
        for (int i = 0; i < 7 && br.budget > 0; i++) {
            float f = i / 6f;
            float[] base = {P[0] + (M[0] - P[0]) * f * 0.92f,
                    P[1] + (M[1] - P[1]) * f * 0.92f,
                    P[2] + (M[2] - P[2]) * f * 0.92f};
            float L = (0.590f + 0.600f * (float) Math.pow(f, 0.88) + ((i % 2) == 1 ? -0.054f : 0.038f))
                    * echelle;
            float[] d = {s * (0.010f + 0.520f * f * f), -0.975f, -0.170f - 0.070f * f};
            norm(d);
            plume(br, base, d, L, 0.176f, lateralePour(d), OR, PALE, 244,
                    (i % 2) == 1 ? 0.72f : 1.0f, 2, 0.084f);
        }
        // quatre secondaires
        for (int i = 0; i < 4 && br.budget > 0; i++) {
            float f = i / 3f;
            float[] base = {C[0] + (P[0] - C[0]) * f * 0.90f,
                    C[1] + (P[1] - C[1]) * f * 0.90f,
                    C[2] + (P[2] - C[2]) * f * 0.90f};
            float L = (0.470f + 0.145f * f) * echelle;
            float[] d = {s * (0.022f + 0.150f * f), -0.980f, -0.185f};
            norm(d);
            plume(br, base, d, L, 0.154f, lateralePour(d), OR, CLAIR, 232,
                    (i % 2) == 1 ? 0.78f : 1.0f, 1, 0.082f);
        }
        // deux rangées de couvertures étagées
        for (int rang = 0; rang < 2; rang++) {
            int n = rang == 1 ? 3 : 5;
            float lg = rang == 1 ? 0.136f : 0.215f;
            int al = rang == 1 ? 220 : 236;
            for (int i = 0; i < n && br.budget > 0; i++) {
                float f = i / (float) (n - 1);
                float w2 = 0.06f + f * 0.84f;
                float[] dep = {C[0] + (P[0] - C[0]) * w2,
                        C[1] + (P[1] - C[1]) * w2 - rang * 0.052f,
                        C[2] + (P[2] - C[2]) * w2 - 0.016f * rang};
                float L = (lg + 0.040f * Mth.sin((float) Math.PI * f)) * echelle;
                float[] d = {s * (0.030f + 0.070f * f), -0.984f, -0.150f};
                norm(d);
                plume(br, dep, d, L, 0.130f - rang * 0.016f, lateralePour(d),
                        mix(OR, CHAUD, 0.30f + rang * 0.35f), CLAIR, al,
                        (i % 2) == 1 ? 0.80f : 1.0f, 0, 0.070f);
            }
        }
        // épaulette
        bande(br, (u, o) -> {
            o[0] = E[0] + (C[0] - E[0]) * u;
            o[1] = E[1] + (C[1] - E[1]) * u;
            o[2] = E[2] + (C[2] - E[2]) * u;
        }, 0.070f, (u) -> 0.084f * (1f - u * 0.30f), 0f, 0f, 1f, (u, v) -> {
            float bord = Math.abs(v) * 2f;
            return rgba(mix(OR, CHAUD, 0.25f + (1f - bord) * 0.45f), Math.round(248 - u * 26));
        }, 0f);
        // duvet d'emplanture
        for (int i = 0; i < 3 && br.budget > 0; i++) {
            float u = 0.14f + 0.70f * (i / 2f);
            float[] p = {E[0] + (C[0] - E[0]) * u, E[1] + (C[1] - E[1]) * u - 0.030f,
                    E[2] + (C[2] - E[2]) * u - 0.040f};
            float[] d = {s * 0.10f, -0.98f, -0.16f};
            norm(d);
            plume(br, p, d, (0.108f + (i % 2) * 0.022f) * echelle, 0.104f, lateralePour(d),
                    mix(OR, CHAUD, 0.5f), CLAIR, 214, (i % 2) == 1 ? 0.82f : 1.0f, 0, 0.066f);
        }
        // alula
        for (int i = 0; i < 2 && br.budget > 0; i++) {
            float[] d = {s * (0.46f + i * 0.16f), -0.80f, -0.36f};
            norm(d);
            float[] base = {M[0], M[1] - 0.012f * i, M[2]};
            plume(br, base, d, (0.244f - i * 0.046f) * echelle, 0.118f, lateralePour(d),
                    CHAUD, PALE, 234, 1.0f, 1, 0.076f);
        }
    }

    // =====================================================================
    //  9. VOILURE DE GIVRE — membrane cristalline, aucune plume
    // =====================================================================
    public static void voilureGivre(Brush br, int side, float echelle) {
        final float t = br.time;
        final float s = side;
        final int GIVRE = 0x9ACFF5, BLANC = 0xFFFFFF, OMBRE = 0x3E6A92, CYAN = 0xD6F0FF;
        final float souffle = Mth.sin(t * 1.5f);
        final float[] E = {s * 0.118f * echelle, 1.388f * echelle, -0.174f};
        final float[] P = {s * (0.790f + souffle * 0.022f) * echelle,
                (1.792f + souffle * 0.040f) * echelle, -0.332f};
        final float[] dc = {0f, -0.880f, -0.470f};
        norm(dc);

        courbe(br, (u, o) -> {
            o[0] = E[0] + (P[0] - E[0]) * u;
            o[1] = E[1] + (P[1] - E[1]) * u;
            o[2] = E[2] + (P[2] - E[2]) * u;
        }, 0.062f, (u) -> 0.029f - u * 0.012f,
                (u, v) -> rgba(mix(mix(OMBRE, GIVRE, 0.45f + u * 0.35f), BLANC, 0.18f),
                        Math.round(252 - u * 22)));

        // membrane : cinq panneaux alternés
        for (int k = 0; k < 5; k++) {
            final int kk = k;
            final float u0 = k / 5f, u1 = (k + 1) / 5f;
            final float clair = (k % 2) == 1 ? 0.52f : 0f;
            int nu = pasOuvert(0.19f * echelle, 0.082f);
            for (int i = 0; i < nu && br.budget > 0; i++) {
                float a = i / (float) (nu - 1);
                float u = u0 + (u1 - u0) * a;
                float corde = 0.455f * echelle * (1f - 0.615f * u);
                int nv = pasOuvert(corde, 0.082f);
                for (int j = 0; j < nv && br.budget > 0; j++) {
                    float b = j / (float) (nv - 1);
                    float cc = corde * b;
                    float gel = 0.5f + 0.5f * Mth.cos(a * 9f + b * 13f + kk * 2.1f);
                    br.cube(E[0] + (P[0] - E[0]) * u + dc[0] * cc,
                            E[1] + (P[1] - E[1]) * u + dc[1] * cc,
                            E[2] + (P[2] - E[2]) * u + dc[2] * cc,
                            0.041f, 0.015f, 0.041f,
                            mix(mix(OMBRE, GIVRE, 0.30f + clair + b * 0.30f + gel * 0.16f), CYAN, 0.20f),
                            Math.max(0, Math.round(238 - b * 52 + gel * 18)));
                }
            }
        }
        // six nervures, fracturées en Y
        for (int i = 0; i < 6 && br.budget > 0; i++) {
            float u = 0.055f + 0.90f * (i / 5f);
            float corde = 0.455f * echelle * (1f - 0.615f * u);
            float bx = E[0] + (P[0] - E[0]) * u, by = E[1] + (P[1] - E[1]) * u,
                    bz = E[2] + (P[2] - E[2]) * u;
            trait(br, bx, by, bz, bx + dc[0] * corde, by + dc[1] * corde, bz + dc[2] * corde, 0.052f,
                    (uu, vv) -> rgba(mix(GIVRE, BLANC, 0.55f + uu * 0.40f), Math.round(252 - uu * 34)));
            float mx = bx + dc[0] * corde * 0.46f, my = by + dc[1] * corde * 0.46f,
                    mz = bz + dc[2] * corde * 0.46f;
            for (int c = -1; c <= 1; c += 2) {
                float[] dir = {dc[0] + c * 0.52f * s, dc[1] * 0.70f, dc[2] - c * 0.18f};
                norm(dir);
                trait(br, mx, my, mz, mx + dir[0] * corde * 0.34f, my + dir[1] * corde * 0.34f,
                        mz + dir[2] * corde * 0.34f, 0.050f,
                        (uu, vv) -> rgba(mix(CYAN, BLANC, uu * 0.6f), Math.round(180 - uu * 90)));
            }
        }
        // bord de fuite festonné
        courbe(br, (u, o) -> {
            float corde = 0.455f * echelle * (1f - 0.615f * u) * (1f + 0.075f * Mth.sin(u * 19f));
            o[0] = E[0] + (P[0] - E[0]) * u + dc[0] * corde;
            o[1] = E[1] + (P[1] - E[1]) * u + dc[1] * corde;
            o[2] = E[2] + (P[2] - E[2]) * u + dc[2] * corde;
        }, 0.054f, (u) -> 0.021f,
                (u, v) -> rgba(mix(GIVRE, BLANC, 0.40f + 0.35f * Mth.sin(u * 19f)), 232));
        // six stalactites
        for (int k = 0; k < 6 && br.budget > 0; k++) {
            float u = 0.105f + 0.835f * (k / 5f);
            float corde = 0.455f * echelle * (1f - 0.615f * u);
            final float bx = E[0] + (P[0] - E[0]) * u + dc[0] * corde;
            final float by = E[1] + (P[1] - E[1]) * u + dc[1] * corde;
            final float bz = E[2] + (P[2] - E[2]) * u + dc[2] * corde;
            final float lg = 0.146f - k * 0.015f + Mth.sin(t * 2.1f + k) * 0.012f;
            courbe(br, (w, o) -> {
                o[0] = bx + s * 0.016f * w * w;
                o[1] = by - lg * w;
                o[2] = bz - 0.020f * w;
            }, 0.046f, (w) -> 0.024f * (1f - w * 0.75f),
                    (w, v) -> rgba(mix(CYAN, BLANC, w * 0.75f), Math.round(250 - w * 90)));
            if (k % 2 == 0) bille(br, bx + s * 0.016f, by - lg, bz - 0.020f, 0.013f, BLANC, 212, GIVRE);
        }
        bille(br, P[0], P[1], P[2], 0.026f, BLANC, 246, GIVRE);
    }

    // =====================================================================
    //  10-12. ORNEMENTS DE SOL — chacun laisse SA trace du passage
    // =====================================================================

    /** #106 : sceau gravé, et les sceaux imprimés que le joueur laisse derrière lui. */
    public static void sceauGuerre(Brush br, CosmeticTrail.Pas[] pas, double solY) {
        final float t = br.time;
        final float y = 0.028f, ph = -t * 0.16f;
        final float rExt = 0.505f, rInt = 0.292f;

        plaqueAnnulaire(br, y, rExt - 0.039f, rExt, 0.078f, ph,
                (u, v) -> rgba(mix(0xFF6A1E, 0xFFC46B, (Mth.sin(6.283f * u * 3f + t) + 1f) / 2f), 214),
                0.012f);
        plaqueAnnulaire(br, y, rInt - 0.035f, rInt, 0.070f, -ph * 1.7f,
                (u, v) -> rgba(0xFFA23C, 196), 0.011f);
        for (int i = 0; i < 4 && br.budget > 0; i++) {
            float a = ph + (float) (Math.PI * 2.0) * i / 4f + 0.39f;
            float ca = Mth.cos(a), sa = Mth.sin(a);
            trait(br, ca * rInt, y, sa * rInt, ca * rExt, y, sa * rExt, 0.062f,
                    (u, v) -> rgba(mix(0xFFA23C, 0xFF6A1E, u), 202));
            float b = a + (float) (Math.PI * 2.0) / 8f;
            float cb = Mth.cos(b), sb = Mth.sin(b), rm = (rExt + rInt) / 2f;
            float puls = 0.5f + 0.5f * Mth.sin(t * 1.9f + i * 1.57f);
            for (int c = -1; c <= 1; c += 2) {
                trait(br, cb * (rm - 0.055f) - sb * 0.060f * c, y, sb * (rm - 0.055f) + cb * 0.060f * c,
                        cb * (rm + 0.062f), y, sb * (rm + 0.062f), 0.048f,
                        (u, v) -> rgba(mix(0xFF8A33, 0xFFE0B0, puls), Math.round(150 + puls * 70)));
            }
        }
        // les empreintes : elles restent où le joueur est passé, et s'éteignent
        for (int k = 0; k < pas.length && br.budget > 0; k++) {
            CosmeticTrail.Pas e = pas[k];
            float vie = 1f - e.age;
            int al = Math.round(176 * vie * vie);
            if (al < 12) continue;
            float R = rExt * (1f - e.age * 0.55f) * 0.86f;
            int n = pasFerme((float) (Math.PI * 2.0) * R, 0.074f);
            for (int i = 0; i < n && br.budget > 0; i++) {
                float a = (float) (Math.PI * 2.0) * i / n;
                br.cubeWorld(e.x + Mth.cos(a) * R, solY + 0.024, e.z + Mth.sin(a) * R,
                        0.037f, 0.010f, 0.037f,
                        mix(0xC43A08, 0xFF9A3C, (Mth.sin(6.283f * i / n * 3f) + 1f) / 2f), al);
            }
        }
    }

    /** #062 : flaques sous les pieds, et la coulée d'infection laissée derrière. */
    public static void pesteAmbulante(Brush br, CosmeticTrail.Pas[] pas, double solY) {
        final float t = br.time;
        float[] ang = {0.35f, 2.45f, 4.05f, 5.45f};
        float[] dist = {0.30f, 0.37f, 0.26f, 0.40f};
        float[] ray = {0.108f, 0.090f, 0.098f, 0.076f};
        for (int k = 0; k < 4 && br.budget > 0; k++) {
            float osc = Mth.sin(t * 0.7f + k * 1.3f) * 0.012f;
            float cx = Mth.cos(ang[k]) * (dist[k] + osc), cz = Mth.sin(ang[k]) * (dist[k] + osc);
            int anneaux = Math.max(1, pasOuvert(ray[k], 0.062f));
            for (int j = 0; j < anneaux && br.budget > 0; j++) {
                float fr = j / (float) Math.max(1, anneaux - 1);
                float r = ray[k] * fr;
                int n = r < 0.01f ? 1 : pasFerme((float) (Math.PI * 2.0) * r, 0.062f);
                for (int i = 0; i < n && br.budget > 0; i++) {
                    float a = (float) (Math.PI * 2.0) * i / n;
                    br.cube(cx + Mth.cos(a) * r, 0.018f, cz + Mth.sin(a) * r, 0.031f, 0.009f, 0.031f,
                            mix(0x2F5E08, 0x7FBF22, fr * 0.8f + 0.2f), Math.round(205 - fr * 70));
                }
            }
        }
        for (int i = 0; i < 9 && br.budget > 0; i++) {
            float p = (t * 0.33f + i * 0.111f) % 1f;
            float a = i * 2.618f + Mth.sin(i * 4.7f) * 1.4f;
            float r = 0.18f + (i % 4) * 0.075f;
            bille(br, Mth.cos(a) * r, 0.030f + p * 0.305f, Mth.sin(a) * r,
                    0.0165f * (0.75f + p * 0.55f), mix(0x9BDC33, 0x3E7A0C, p),
                    Math.max(0, Math.round(215 * (1f - p * p))), 0xD8F2A0);
        }
        for (int k = 0; k < pas.length && br.budget > 0; k++) {
            CosmeticTrail.Pas e = pas[k];
            float R = (0.165f - k * 0.013f) * (1f - e.age * 0.55f);
            if (R < 0.030f) continue;
            int al = Math.round(196 * (1f - e.age) * (1f - e.age * 0.5f));
            if (al < 14) continue;
            disqueMonde(br, e.x, solY + 0.014, e.z, R, 0.070f,
                    mix(0x2A5409, 0x79BE1E, 0.45f), al, 0.009f);
            if (k % 2 == 0 && al > 70) {
                br.cubeWorld(e.x, solY + 0.034 + Math.abs(Mth.sin(t * 1.6f + k)) * 0.046,
                        e.z, 0.012f, 0.012f, 0.012f, 0x7FBF22, Math.round(al * 0.75f));
            }
        }
    }

    /** Aura de Terreur : ronces autour des pieds, et la terre blessée du passage. */
    public static void auraTerreur(Brush br, CosmeticTrail.Pas[] pas, double solY) {
        final float t = br.time;
        final float ph = -t * 0.19f;
        for (int i = 0; i < 8 && br.budget > 0; i++) {
            final float a = ph + (float) (Math.PI * 2.0) * i / 8f;
            final float ca = Mth.cos(a), sa = Mth.sin(a);
            final float r0 = 0.355f;
            final float haut = 0.300f + Mth.sin(t * 1.25f + i * 0.70f) * 0.070f;
            courbe(br, (u, o) -> {
                float dr = 0.145f * Mth.sin(u * 1.95f) + 0.060f * u * u;
                o[0] = ca * (r0 + dr);
                o[1] = 0.012f + haut * (float) Math.pow(u, 0.82);
                o[2] = sa * (r0 + dr);
            }, 0.030f, (u) -> 0.028f * (1f - u * 0.38f),
                    (u, v) -> rgba(mix(0x2C0752, 0xA24BFF, (float) Math.pow(u, 0.7)),
                            Math.round(215 - u * 55)));
            if (i % 2 == 0) {
                trait(br, ca * r0, 0.010f, sa * r0, ca * (r0 + 0.235f), 0.010f, sa * (r0 + 0.235f),
                        0.030f, (u, v) -> rgba(mix(0x6B1FB5, 0x1B0433, u),
                                Math.max(0, Math.round(175 * (1f - u * 0.85f)))));
            }
        }
        for (int k = 0; k < pas.length && br.budget > 0; k++) {
            CosmeticTrail.Pas e = pas[k];
            int al = Math.round(190 * (1f - e.age) * (1f - e.age * 0.6f));
            if (al < 16) continue;
            float h = (0.175f - k * 0.026f) * (1f - e.age * 0.55f);
            if (h < 0.030f) continue;
            for (int c = -1; c <= 1; c += 2) {
                float xr = c * (0.115f + k * 0.022f);
                int n = pasOuvert(h, 0.042f);
                for (int i = 0; i < n && br.budget > 0; i++) {
                    float u = i / (float) (n - 1);
                    br.cubeWorld(e.x + xr + c * 0.055 * u * u,
                            solY + 0.010 + h * Math.pow(u, 0.80),
                            e.z - 0.045 * u, 0.023f * (1f - u * 0.40f), 0.023f * (1f - u * 0.40f),
                            0.023f * (1f - u * 0.40f),
                            mix(0x230540, 0x8A36D8, (float) Math.pow(u, 0.7)),
                            Math.max(0, Math.round(al - u * 50)));
                }
            }
        }
    }

    // =====================================================================
    //  13. COUSSIN DE NUAGES — le seul ornement en GROS pixels
    // =====================================================================
    /**
     * Un nuage n'a pas d'arête à suivre, il a du VOLUME : il se dessine donc en
     * gros pixels jointifs et de plusieurs tailles, et non au trait comme les
     * autres ornements. C'est le mélange des tailles qui fait le moutonnement.
     *
     * <p>Dix-neuf bouffées sur trois étages, 1,33 bloc d'envergure et 0,96
     * d'épaisseur : la galette commence 0,14 bloc SOUS les pieds et monte à
     * mi-cuisse, le joueur est dedans jusqu'aux genoux.</p>
     */
    // [dx, dz, rayon horizontal, rayon vertical, hauteur, grain, graine]
    private static final float[][] BOUFFEES = {
            // étage 0 : la couronne basse, les plus gros pixels
            {0.000f,  0.000f, 0.355f, 0.215f, 0.165f, 0.090f,  0.7f},
            {0.400f,  0.085f, 0.300f, 0.180f, 0.150f, 0.090f,  1.9f},
            {-0.390f, 0.130f, 0.290f, 0.175f, 0.145f, 0.090f,  3.1f},
            {0.140f, -0.400f, 0.290f, 0.172f, 0.142f, 0.078f,  4.3f},
            {-0.165f, 0.395f, 0.285f, 0.170f, 0.152f, 0.078f,  5.5f},
            {0.335f, -0.335f, 0.250f, 0.155f, 0.136f, 0.078f,  6.7f},
            {-0.325f, -0.340f, 0.245f, 0.152f, 0.134f, 0.078f,  7.9f},
            {0.070f,  0.455f, 0.245f, 0.152f, 0.140f, 0.078f,  9.1f},
            {-0.080f, -0.465f, 0.240f, 0.148f, 0.132f, 0.078f, 10.3f},
            // étage 1 : la masse médiane, elle remonte entre les chevilles
            {0.000f,  0.030f, 0.310f, 0.205f, 0.360f, 0.090f, 11.5f},
            {0.300f, -0.080f, 0.225f, 0.158f, 0.330f, 0.078f, 12.7f},
            {-0.310f, 0.060f, 0.220f, 0.155f, 0.335f, 0.078f, 13.9f},
            {0.060f,  0.330f, 0.215f, 0.152f, 0.322f, 0.078f, 15.1f},
            {-0.050f, -0.335f, 0.212f, 0.150f, 0.318f, 0.078f, 16.3f},
            // étage 2 : la crête, pixels moyens, elle coiffe le tout
            {-0.010f, 0.010f, 0.250f, 0.175f, 0.545f, 0.078f, 17.5f},
            {0.215f,  0.130f, 0.168f, 0.125f, 0.575f, 0.066f, 18.7f},
            {-0.220f, -0.115f, 0.162f, 0.122f, 0.562f, 0.066f, 19.9f},
            {0.030f, -0.220f, 0.158f, 0.118f, 0.588f, 0.066f, 21.1f},
            {-0.035f, 0.215f, 0.154f, 0.116f, 0.570f, 0.066f, 22.3f}};

    private static final int NUAGE_BAS = 0x6B88AF, NUAGE_HAUT = 0xFFFFFF;
    private static final float NUAGE_Y_BAS = -0.07f, NUAGE_Y_ETENDUE = 0.80f;

    private static float aleaPixel(float a, float b, float c) {
        double v = Math.sin(a * 127.1 + b * 311.7 + c * 74.7) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    public static void coussinNuage(Brush br, CosmeticTrail.Pas[] pas, double solY) {
        final float t = br.time;
        for (int k = 0; k < BOUFFEES.length && br.budget > 0; k++) {
            final float[] b = BOUFFEES[k];
            final float g = 1f + Mth.sin(t * 1.35f + k * 1.25f) * 0.060f;
            final float flot = Mth.sin(t * 0.95f + k * 0.80f) * 0.018f;
            bouffeeNuage(br, b[0], b[4] * g + flot, b[1], b[2] * g, b[3] * g, b[2] * g,
                    b[5], b[6], 238, NUAGE_Y_BAS, NUAGE_Y_ETENDUE, false, 0, 0, 0);
        }

        // Le sillage : des volutes posées là où le joueur est passé. Elles montent
        // en se délitant, et se rétractent dès qu'il s'arrête puisque plus rien
        // n'est déposé.
        if (pas == null) return;
        for (int k = 0; k < pas.length && k < 6 && br.budget > 0; k++) {
            CosmeticTrail.Pas e = pas[k];
            float vie = Mth.clamp(e.age, 0f, 1f);
            float R = (0.205f - k * 0.016f) * (1f - vie * 0.55f);
            int al = Math.round(214f * (1f - vie * 0.85f));
            float cote = Mth.sin(k * 1.35f + t * 0.6f) * 0.082f;
            float haut = 0.085f + vie * 0.150f;
            // Le grain a un plancher : en dessous le gros pixel ne rentre plus dans
            // la volute, qui se réduirait à UN cube seul perdu en l'air.
            float grain = Math.max(0.052f, R * 0.32f);
            if (al < 20 || R < grain * 2.9f) continue;
            // yBas est compare a l'altitude MONDE du cube : il doit donc inclure solY,
            // sinon la teinte du sillage est calculee dans un autre repere que lui.
            bouffeeNuage(br, 0, 0, 0, R, R * 0.66f, R, grain, 20f + k * 2.3f, al,
                    (float) (solY + haut - R * 0.8f), R * 1.9f, true,
                    e.x + cote * br.rightX, solY + haut, e.z + cote * br.rightZ);
        }
    }

    /**
     * Remplit un ellipsoïde de gros cubes JOINTIFS (pas = deux fois le grain) :
     * c'est la condition pour qu'on voie chaque pixel au lieu d'une patate lissée.
     * La taille est uniforme dans une bouffée ; la variété vient du grain, qui
     * change d'une bouffée à l'autre.
     *
     * @param monde {@code true} pour poser la bouffée en coordonnées MONDE
     *              (le sillage reste où le pas a été déposé), {@code false} pour
     *              la poser dans le repère du joueur (la galette le suit).
     */
    private static void bouffeeNuage(Brush br, float cx, float cy, float cz,
                                     float rx, float ry, float rz, float grain, float graine,
                                     int al0, float yBas, float yEtendue,
                                     boolean monde, double wx, double wy, double wz) {
        final float pas = grain * 2f;
        final int nx = Mth.ceil(rx / pas), ny = Mth.ceil(ry / pas), nz = Mth.ceil(rz / pas);
        for (int ix = -nx; ix <= nx && br.budget > 0; ix++) {
            for (int iy = -ny; iy <= ny && br.budget > 0; iy++) {
                for (int iz = -nz; iz <= nz && br.budget > 0; iz++) {
                    float px = ix * pas, py = iy * pas, pz = iz * pas;
                    float d = (float) Math.sqrt((px / rx) * (px / rx)
                            + (py / ry) * (py / ry) + (pz / rz) * (pz / rz));
                    // Bord bosselé mais COHÉRENT (fonction lisse de la position) :
                    // des lobes larges, pas du bruit qui isolerait des cubes.
                    float lobe = 0.11f * Mth.sin(px * 6.5f + graine) * Mth.cos(pz * 5.5f - graine)
                            + 0.07f * Mth.sin(py * 8.0f + graine * 2.1f);
                    if (d > 1f + lobe) continue;
                    // La teinte suit la hauteur RÉELLE dans le nuage, pas la hauteur
                    // dans la bouffée : sinon le sommet d'un lobe bas serait aussi
                    // clair que la crête et l'éclairage se contredirait.
                    float hGlob = Mth.clamp(((monde ? (float) wy : cy) + py - yBas) / yEtendue, 0f, 1f);
                    float r = aleaPixel(ix + graine, iy - graine, iz + graine * 2f);
                    float f = Mth.clamp(0.34f + 0.62f * (float) Math.pow(hGlob, 0.78)
                            + (1f - Math.min(1f, d)) * 0.07f
                            + (r - 0.5f) * 0.12f, 0f, 1f);
                    // Teinte QUANTIFIÉE sur cinq valeurs : du pixel-art franc. Un
                    // dégradé continu recolle tous les cubes en une seule tache.
                    int rgb = mix(NUAGE_BAS, NUAGE_HAUT, Math.round(f * 4f) / 4f);
                    if (monde) {
                        br.cubeWorld(wx + px, wy + py, wz + pz, grain, grain, grain, rgb, al0);
                    } else {
                        br.cube(cx + px, cy + py, cz + pz, grain, grain, grain, rgb, al0);
                    }
                }
            }
        }
    }

    // =====================================================================
    //  14. ONDE DE CHOC — aucune traînée, mais la mémoire de la chute
    // =====================================================================
    /**
     * Cette onde ne naît qu'à l'atterrissage : le joueur ne la promène pas, donc pas
     * de traînée. En revanche elle porte la hauteur tombée — un écho intérieur de un à
     * quatre anneaux concentriques, et une gerbe d'autant plus haute.
     *
     * @param p      avancement de l'onde, 0 à l'impact, 1 quand elle s'éteint
     * @param chute  hauteur réellement tombée, en blocs
     * @param solY   altitude du sol touché, en repère caméra
     */
    public static void ondeChoc(Brush br, float p, float chute, double cx, double solY, double cz) {
        if (p <= 0f || p >= 1f) return;
        final float t = br.time;
        final float force = Math.min(1f, chute / 14f);
        final int nEcho = 1 + Math.min(3, (int) Math.floor(chute / 3.5f));

        anneauChoc(br, p, 0.24f, 0.72f + force * 0.62f, 0xFFE9A0, 1f, cx, solY, cz, t);
        for (int k = 1; k <= nEcho && br.budget > 0; k++) {
            float pk = p - k * 0.18f;
            if (pk <= 0f) continue;
            anneauChoc(br, pk, 0.10f, 0.52f + force * 0.30f, 0xFFF6D0,
                    0.78f - k * 0.14f, cx, solY, cz, t);
        }

        final int nd = 4 + Math.round(force * 8f);
        for (int i = 0; i < nd && br.budget > 0; i++) {
            float a = i * 2.399f + t * 0.4f;
            float q = (p + i * 0.137f) % 1f;
            float h = (0.18f + force * 0.62f) * Mth.sin((float) Math.PI * q);
            float rr = 0.07f + q * (0.20f + force * 0.22f);
            int al = Math.round(206f * (1f - q) * (0.45f + force * 0.55f));
            if (al < 16) continue;
            billeMonde(br, cx + Mth.cos(a) * rr, solY + 0.030f + h, cz + Mth.sin(a) * rr,
                    0.016f, mix(0xFFD089, 0x8A5A20, q), al, 0xFFF3C8);
        }
    }

    /** Un anneau de l'onde : le nombre de cubes suit le rayon, donc il reste plein. */
    private static void anneauChoc(Brush br, float p, float rayon0, float amplitude,
                                   int teinte, float force2, double cx, double solY, double cz, float t) {
        final float r = rayon0 + p * amplitude, w = 0.068f;
        final int al = Math.round(248f * (float) Math.pow(1f - p, 1.25) * force2);
        if (al < 10 || r < 0.05f) return;
        final int n = pasFerme((float) (Math.PI * 2.0) * r, w);
        for (int i = 0; i < n && br.budget > 0; i++) {
            float a = (float) (Math.PI * 2.0) * i / n;
            float ca = Mth.cos(a), sa = Mth.sin(a);
            float bosse = 0.045f * Mth.sin((float) Math.PI * (1f - p)) * (0.8f + 0.2f * Mth.sin(a * 7f + t));
            br.cubeWorld(cx + ca * r, solY + 0.030f + bosse, cz + sa * r,
                    w * 0.5f, 0.016f, w * 0.5f, mix(teinte, 0xFF8A20, p), al);
            if (i % 5 == 0) {
                br.cubeWorld(cx + ca * (r - 0.030f), solY + 0.030f + bosse * 0.45f, cz + sa * (r - 0.030f),
                        0.022f, 0.014f, 0.022f, mix(0xFFC77A, 0x8A5A20, p), Math.round(al * 0.55f));
            }
        }
    }

    /** Même bille, mais ancrée au point d'impact et non au joueur. */
    private static void billeMonde(Brush br, double cx, double cy, double cz, float r,
                                   int rgb, int al, int rgbBord) {
        float h = r * 0.62f, hb = h * 0.78f;
        int ab = Math.round(al * 0.85f);
        br.cubeWorld(cx, cy, cz, h, h, h, rgb, al);
        br.cubeWorld(cx + r, cy, cz, hb, hb, hb, rgbBord, ab);
        br.cubeWorld(cx - r, cy, cz, hb, hb, hb, rgbBord, ab);
        br.cubeWorld(cx, cy + r, cz, hb, hb, hb, rgbBord, ab);
        br.cubeWorld(cx, cy - r, cz, hb, hb, hb, rgbBord, ab);
        br.cubeWorld(cx, cy, cz + r, hb, hb, hb, rgbBord, ab);
        br.cubeWorld(cx, cy, cz - r, hb, hb, hb, rgbBord, ab);
    }
}
