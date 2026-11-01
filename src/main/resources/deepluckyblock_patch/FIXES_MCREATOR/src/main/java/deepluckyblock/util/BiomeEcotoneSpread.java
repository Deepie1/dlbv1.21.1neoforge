package deepluckyblock.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * T306-ECOTONE : Moteur pur-Java (zero dependance Minecraft/SLF4J) de spread et degrade
 * inter-biomes realiste, inspire de :
 * <ul>
 *   <li><b>ScatteredBiomeBlender</b> de KdotJPG (CC0, <i>Fast Biome Blending, Without Squareness</i>) :
 *       echantillonnage sur reseau triangulaire/hexagonal Simplex jitterise en 24 directions +
 *       convolution radiale quartique normalisee {@code (R^2 - d^2)^2} — elimine 100 % des
 *       biais d'axes X/Z et des bordures carrees de chunks ;</li>
 *   <li><b>Domain-Warped Isotropic 2D Simplex fBm</b> (3 octaves : langues macro 19 bl, ilots
 *       meso 7.5 bl, frange micro 3.2 bl) : remplace le bruit blanc par bloc ({@code posNoise})
 *       qui produisait un damier/confetti 1x1 artificiel ;</li>
 *   <li><b>Ecotones ecologiques en coussins discontinus</b> : poches naturelles de transition
 *       (ex. herbe &harr; sable via terre grossiere, plaine &harr; podzol via mousse/terre grossiere)
 *       modulees par bruit meso pour eviter tout liseret continu ;</li>
 *   <li><b>Exclusion stricte GLACE / EAU</b> : tout point d'echantillonnage tombant sur de la
 *       glace ou de l'eau a un poids terrestre strictement nul ({@code materialId < 0}) — aucun
 *       degrade avec l'ICE, et si un chunk est borde par 3 cotes terrestres (neige/sable/podzol)
 *       et 1 cote ICE, les 3 cotes terrestres fusionnent naturellement vers l'interieur.</li>
 * </ul>
 */
public final class BiomeEcotoneSpread {

    private BiomeEcotoneSpread() {}

    // =========================================================================
    // 1. CONSTANTES DU RESEAU HEXAGONAL JITTERISE (KdotJPG ScatteredBiomeBlender)
    // =========================================================================
    private static final double F2 = 0.3660254037844386;   // (sqrt(3) - 1) / 2
    private static final double G2 = 0.21132486540518713;  // (3 - sqrt(3)) / 6
    private static final double SQRT_HALF = Math.sqrt(0.5);
    private static final double TRIANGLE_HEIGHT = SQRT_HALF;
    private static final double INVERSE_TRIANGLE_HEIGHT = SQRT_HALF * 2.0;
    private static final double TRIANGLE_CIRCUMRADIUS = TRIANGLE_HEIGHT * (2.0 / 3.0);
    private static final double JITTER_AMOUNT = TRIANGLE_HEIGHT * 0.88;
    private static final double MAX_GRID_DIST = JITTER_AMOUNT + TRIANGLE_CIRCUMRADIUS;

    private static final int PRIME_X = 7691;
    private static final int PRIME_Z = 30869;

    private static final int N_VECTORS = 24;
    private static final int N_VEC_REP = 32;
    private static final int VEC_MASK = N_VEC_REP - 1;
    private static final int SINCOS_OFFSET = 8;
    private static final double[] JITTER_SINCOS = new double[40];

    // 12 vecteurs gradients unitaires isotropes pour Simplex 2D
    private static final double[] GRAD2 = new double[24];

    static {
        for (int i = 0, j = 0; i < N_VECTORS; i++) {
            JITTER_SINCOS[j] = Math.sin((i + 0.5) * ((2.0 * Math.PI) / N_VECTORS)) * JITTER_AMOUNT;
            j++;
            if ((j & 3) == 1) {
                JITTER_SINCOS[j] = JITTER_SINCOS[j - 1];
                j++;
            }
        }
        for (int j = N_VEC_REP; j < JITTER_SINCOS.length; j++) {
            JITTER_SINCOS[j] = JITTER_SINCOS[j - N_VEC_REP];
        }
        for (int i = 0; i < 12; i++) {
            double ang = (i * 2.0 * Math.PI) / 12.0;
            GRAD2[i * 2] = Math.cos(ang);
            GRAD2[i * 2 + 1] = Math.sin(ang);
        }
    }

    /** Callback d'echantillonnage terrestre : retourne >= 0 pour un sol terrestre valide, ou -1 pour ICE/EAU/absent. */
    @FunctionalInterface
    public interface TerrestrialMaterialSampler {
        int sampleMaterialAt(int worldX, int worldZ);
    }

    /** Callback d'ecotone : retourne l'ID d'un bloc de transition ecologique entre matA et matB, ou -1 si aucun. */
    @FunctionalInterface
    public interface EcotoneResolver {
        int resolveEcotone(int matA, int matB, double mesoSigned);
    }

    /** Callback d'echantillonnage de neige terrestre : retourne [0..1] sur terre ferme, ou -1f sur ICE/EAU/inconnu. */
    @FunctionalInterface
    public interface TerrestrialSnowSampler {
        float sampleSnowPotential(int worldX, int worldZ);
    }

    private static final class LatticeOffset {
        final int xsvp, zsvp;
        final double xv, zv;
        LatticeOffset(int xsv, int zsv) {
            this.xsvp = xsv * PRIME_X;
            this.zsvp = zsv * PRIME_Z;
            double t = (xsv + zsv) * -G2;
            this.xv = xsv + t;
            this.zv = zsv + t;
        }
    }

    public static final class ScatteredConfig {
        final double frequency;
        final double invFrequency;
        final double blendRadius;
        final double blendRadiusSq;
        final LatticeOffset[] offsets;

        public ScatteredConfig(double frequency, double blendRadiusPadding, int regionHalfDiag) {
            this.frequency = frequency;
            this.invFrequency = 1.0 / frequency;
            this.blendRadius = blendRadiusPadding + (MAX_GRID_DIST / frequency);
            this.blendRadiusSq = this.blendRadius * this.blendRadius;

            double searchWorldRadius = this.blendRadius + regionHalfDiag;
            double maxGridDist = searchWorldRadius * frequency + MAX_GRID_DIST;
            double maxGridDistSq = maxGridDist * maxGridDist;
            int rings = (int) Math.ceil(maxGridDist * INVERSE_TRIANGLE_HEIGHT) + 1;

            List<LatticeOffset> list = new ArrayList<>();
            for (int r = -rings; r <= rings; r++) {
                for (int q = -rings; q <= rings; q++) {
                    LatticeOffset lo = new LatticeOffset(q, r);
                    if (lo.xv * lo.xv + lo.zv * lo.zv <= maxGridDistSq) {
                        list.add(lo);
                    }
                }
            }
            this.offsets = list.toArray(new LatticeOffset[0]);
        }
    }

    /** Config par defaut pour l'habillage inter-biomes par chunk 16x16 (rayon effectif ~26 blocs, ~1.65 chunks). */
    public static final ScatteredConfig DEFAULT_SURFACE_CONFIG =
            new ScatteredConfig(0.088, 9.5, 12);

    // =========================================================================
    // 2. BRUIT SIMPLEX 2D ISOTROPE + DOMAIN-WARPED FBM (ZERO GRILLE CARREE)
    // =========================================================================

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static int gradIndex12(long seed, int i, int j) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) (Long.remainderUnsigned(h, 12L));
    }

    /**
     * Bruit Simplex 2D isotrope continu dans [-1.0, +1.0], sans aucun biais d'axe X/Z.
     */
    public static double simplex2(long seed, double x, double z) {
        double s = (x + z) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(z + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double z0 = z - (j - t);

        int i1, j1;
        if (x0 > z0) { i1 = 1; j1 = 0; }
        else          { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + G2;
        double z1 = z0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double z2 = z0 - 1.0 + 2.0 * G2;

        double n0 = 0.0, n1 = 0.0, n2 = 0.0;

        double t0 = 0.5 - x0 * x0 - z0 * z0;
        if (t0 > 0.0) {
            t0 *= t0;
            int gi0 = gradIndex12(seed, i, j) * 2;
            n0 = t0 * t0 * (GRAD2[gi0] * x0 + GRAD2[gi0 + 1] * z0);
        }
        double t1 = 0.5 - x1 * x1 - z1 * z1;
        if (t1 > 0.0) {
            t1 *= t1;
            int gi1 = gradIndex12(seed, i + i1, j + j1) * 2;
            n1 = t1 * t1 * (GRAD2[gi1] * x1 + GRAD2[gi1 + 1] * z1);
        }
        double t2 = 0.5 - x2 * x2 - z2 * z2;
        if (t2 > 0.0) {
            t2 *= t2;
            int gi2 = gradIndex12(seed, i + 1, j + 1) * 2;
            n2 = t2 * t2 * (GRAD2[gi2] * x2 + GRAD2[gi2 + 1] * z2);
        }

        // Facteur de normalisation exact du Simplex 2D (~70.0) borne dans [-1, 1]
        double val = 70.0 * (n0 + n1 + n2);
        return val < -1.0 ? -1.0 : (val > 1.0 ? 1.0 : val);
    }

    /**
     * Champ d'affinite organique fBm 3 octaves AVEC domain-warping prealable,
     * normalise dans [-0.5, +0.5] :
     *  - Warp basse frequence (±7.5 blocs) pour courber les langues de biome ;
     *  - Octave 1 (19 blocs) : langues/peninsules principales d'ecotone ;
     *  - Octave 2 (7.5 blocs) : ilots/clairieres cohesifs de 4 a 8 blocs ;
     *  - Octave 3 (3.2 blocs) : dentelle fine de bord de tache (trop faible pour creer du 1x1 isole).
     */
    public static float warpedPatchSigned(long seed, int x, int z) {
        double wx = x + simplex2(seed ^ 0x6A09E667F3BCC909L, x * 0.044, z * 0.044) * 7.5;
        double wz = z + simplex2(seed ^ 0xBB67AE8584CAA73BL, x * 0.044 + 17.3, z * 0.044 - 11.9) * 7.5;

        double o1 = simplex2(seed ^ 0x3C6EF372FE94F82BL, wx / 19.0, wz / 19.0) * 0.55;
        double o2 = simplex2(seed ^ 0xA54FF53A5F1D36F1L, wx / 7.5,  wz / 7.5)  * 0.33;
        double o3 = simplex2(seed ^ 0x510E527FADE682D1L, wx / 3.2,  wz / 3.2)  * 0.12;
        // Contraste sigmoide doux pour couvrir pleinement [-0.5, +0.5]
        double sum = (o1 + o2 + o3) * 0.72;
        if (sum < -0.5) return -0.5f;
        if (sum >  0.5) return  0.5f;
        return (float) sum;
    }

    /** Meme champ ramene dans [0.0, 1.0]. */
    public static float warpedPatch01(long seed, int x, int z) {
        return warpedPatchSigned(seed, x, z) + 0.5f;
    }

    /**
     * Facteur de groupement botanique [0.0, 1.0] pour la vegetation :
     * cree des clairieres naturelles et des bosquets d'herbes/fleurs plutot qu'un semis uniforme.
     */
    public static float vegetationClumpFactor(long seed, int x, int z) {
        double n = 0.62 * simplex2(seed ^ 0x7F4A7C159E3779B9L, x / 14.0, z / 14.0)
                 + 0.38 * simplex2(seed ^ 0x27D4EB2F165667B1L, x / 5.5,  z / 5.5);
        float v = (float) (0.5 + 0.5 * n);
        // Accentue les clairieres (< 0.35 -> epars) et les touffes (> 0.6 -> dense)
        return Math.max(0.15f, Math.min(1.35f, 0.30f + 1.35f * v * v));
    }

    /**
     * Selecteur de colonie florale coherent [0.0, 1.0) par patch de ~11 blocs :
     * les fleurs voisines appartiennent a la meme espece locale (coquelicots ensemble,
     * bleuets ensemble, marguerites ensemble) comme la generation florale vanilla !
     */
    public static float flowerColonySelector(long seed, int x, int z) {
        double wx = x + simplex2(seed ^ 0x1F83D9ABFB41BD6BL, x / 16.0, z / 16.0) * 4.5;
        double wz = z + simplex2(seed ^ 0x5BE0CD19137E2179L, x / 16.0, z / 16.0) * 4.5;
        double v = 0.5 + 0.5 * (0.75 * simplex2(seed ^ 0x9B05688C2B3E6C1FL, wx / 12.0, wz / 12.0)
                              + 0.25 * simplex2(seed ^ 0xCBBB9D5DC1059ED8L, wx / 4.5,  wz / 4.5));
        // Etirement d'histogramme doux (Simplex 2D est concentre autour de 0.5)
        double stretched = 0.5 + (v - 0.5) * 1.45;
        if (stretched < 0.0) return 0.0f;
        if (stretched >= 0.9999) return 0.9999f;
        return (float) stretched;
    }

    // =========================================================================
    // 3. MELANGEUR DE SURFACE PAR CHUNK 16x16 (SCATTERED CONVOLUTION + ECOTONES)
    // =========================================================================

    /**
     * Calcule pour les 256 colonnes d'un chunk {@code (chunkX, chunkZ)} l'ID du materiau
     * de surface terrestre en combinant :
     *  1) la convolution eparse sur points hexagonaux jitterises (zero grille carree, 0 poids sur ICE/EAU),
     *  2) la selection competitive par taches fBm domain-warpees (zero confetti 1x1),
     *  3) les coussins d'ecotone ecologique discontinus aux interfaces inter-biomes.
     *
     * @return tableau {@code int[256]} indexe par {@code (lz << 4) | lx}, contenant l'ID choisi
     *         (ou {@code fallbackMaterialId} si aucun point terrestre n'existe a portee).
     */
    public static int[] blendChunkSurface(long seed,
                                          int chunkX,
                                          int chunkZ,
                                          int fallbackMaterialId,
                                          ScatteredConfig cfg,
                                          TerrestrialMaterialSampler sampler,
                                          EcotoneResolver ecotoneResolver) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        double centerX = baseX + 7.5;
        double centerZ = baseZ + 7.5;

        double sx = centerX * cfg.frequency;
        double sz = centerZ * cfg.frequency;
        double skew = (sx + sz) * F2;
        double xs = sx + skew, zs = sz + skew;
        int xsb = fastFloor(xs), zsb = fastFloor(zs);
        double xsi = xs - xsb, zsi = zs - zsb;
        double p = 2.0 * xsi - zsi, q = 2.0 * zsi - xsi, r = xsi + zsi;
        if (r > 1.0) {
            if (p < 0.0) zsb += 1;
            else if (q < 0.0) xsb += 1;
            else { xsb += 1; zsb += 1; }
        } else {
            if (p > 1.0) xsb += 1;
            else if (q > 1.0) zsb += 1;
        }

        int xsbp = xsb * PRIME_X;
        int zsbp = zsb * PRIME_Z;
        double bt = (xsb + zsb) * -G2;
        double xb = xsb + bt, zb = zsb + bt;

        int seedLo = (int) (seed & 0xFFFFFFFFL);
        int seedHi = (int) (seed >>> 32);

        int cap = cfg.offsets.length;
        double[] ptX = new double[cap];
        double[] ptZ = new double[cap];
        int[] ptMat = new int[cap];
        int ptCount = 0;

        // Liste compacte des materiaux distincts rencontres autour de ce chunk (typiquement 1 a 4)
        int[] uniqueMats = new int[12];
        int uniqueCount = 0;

        for (int i = 0; i < cap; i++) {
            LatticeOffset lo = cfg.offsets[i];
            int xsvp = xsbp + lo.xsvp;
            int zsvp = zsbp + lo.zsvp;
            int hash = xsvp ^ zsvp;
            hash = ((seedLo ^ hash) * 668908897) ^ ((seedHi ^ hash) * 35311);
            int indexBase = (hash & 0x3FFFFFF) * 0x5555555;
            int idx = (indexBase >>> 26) & VEC_MASK;

            double wx = (xb + lo.xv + JITTER_SINCOS[idx]) * cfg.invFrequency;
            double wz = (zb + lo.zv + JITTER_SINCOS[idx + SINCOS_OFFSET]) * cfg.invFrequency;

            // Elagage boite englobante du chunk + blendRadius
            double dxBox = Math.abs(wx - centerX) - 8.0;
            double dzBox = Math.abs(wz - centerZ) - 8.0;
            if (dxBox >= cfg.blendRadius || dzBox >= cfg.blendRadius) continue;
            if (dxBox > 0.0 && dzBox > 0.0 && (dxBox * dxBox + dzBox * dzBox) >= cfg.blendRadiusSq) continue;

            int mat = sampler.sampleMaterialAt(fastFloor(wx + 0.5), fastFloor(wz + 0.5));
            if (mat < 0) continue; // ICE / EAU / hors-monde : poids strictement NUL !

            int slot = -1;
            for (int u = 0; u < uniqueCount; u++) {
                if (uniqueMats[u] == mat) { slot = u; break; }
            }
            if (slot < 0) {
                if (uniqueCount >= uniqueMats.length) {
                    uniqueMats = Arrays.copyOf(uniqueMats, uniqueMats.length * 2);
                }
                slot = uniqueCount++;
                uniqueMats[slot] = mat;
            }

            ptX[ptCount] = wx;
            ptZ[ptCount] = wz;
            ptMat[ptCount] = slot;
            ptCount++;
        }

        int[] result = new int[256];
        if (uniqueCount == 0) {
            Arrays.fill(result, fallbackMaterialId);
            return result;
        }
        // Fast-path : 1 seul biome terrestre dans tout le rayon de 26 blocs -> chunk 100 % homogene en O(1) !
        if (uniqueCount == 1) {
            Arrays.fill(result, uniqueMats[0]);
            return result;
        }

        double rSq = cfg.blendRadiusSq;
        double[] weights = new double[uniqueCount];

        for (int lz = 0; lz < 16; lz++) {
            int wz = baseZ + lz;
            double cz = wz + 0.5;
            int row = lz << 4;
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                double cx = wx + 0.5;
                Arrays.fill(weights, 0.0);
                double totalW = 0.0;

                for (int pIdx = 0; pIdx < ptCount; pIdx++) {
                    double dx = cx - ptX[pIdx];
                    double dz = cz - ptZ[pIdx];
                    double dSq = dx * dx + dz * dz;
                    if (dSq < rSq) {
                        double w = rSq - dSq;
                        w *= w; // Noyau quartique lisse C1 (r^2 - d^2)^2
                        weights[ptMat[pIdx]] += w;
                        totalW += w;
                    }
                }

                if (totalW <= 1.0e-9) {
                    result[row + lx] = uniqueMats[0];
                    continue;
                }

                double invTotal = 1.0 / totalW;
                int bestSlot = 0, secondSlot = -1;
                double bestScore = -1.0e9, secondScore = -1.0e9;

                for (int u = 0; u < uniqueCount; u++) {
                    double wu = weights[u] * invTotal;
                    if (wu <= 0.015) continue;
                    if (wu >= 0.985) {
                        bestSlot = u;
                        bestScore = 2.0;
                        secondSlot = -1;
                        break;
                    }
                    int matId = uniqueMats[u];
                    long matSeed = seed ^ ((matId + 1L) * 0x9E3779B97F4A7C15L);
                    // Enveloppe 4*w*(1-w) : 0 au coeur d'un biome pur, maximale (1.0) au coeur de l'ecotone !
                    double boundaryEnv = 4.0 * wu * (1.0 - wu);
                    double patch = warpedPatchSigned(matSeed, wx, wz); // [-0.5, +0.5]
                    double score = wu + 0.56 * boundaryEnv * patch;
                    if (score > bestScore) {
                        secondScore = bestScore;
                        secondSlot = bestSlot;
                        bestScore = score;
                        bestSlot = u;
                    } else if (score > secondScore) {
                        secondScore = score;
                        secondSlot = u;
                    }
                }

                int chosenMat = uniqueMats[bestSlot];
                // Coussins discontinus d'ecotone a la frontiere exacte entre les 2 biomes dominants
                if (ecotoneResolver != null && secondSlot >= 0 && (bestScore - secondScore) < 0.075) {
                    double cushion = simplex2(seed ^ 0xEC0709E1AB35C1D3L, wx / 6.2, wz / 6.2);
                    if (cushion > 0.12) {
                        double meso = simplex2(seed ^ 0x4F2B7A1983D5E16BL, wx / 14.0, wz / 14.0);
                        int eco = ecotoneResolver.resolveEcotone(chosenMat, uniqueMats[secondSlot], meso);
                        if (eco >= 0) {
                            chosenMat = eco;
                        }
                    }
                }
                result[row + lx] = chosenMat;
            }
        }
        return result;
    }

    // =========================================================================
    // 4. CONVOLUTION EPARSE DE NEIGE (POUR TERRAINMOTHER — ZERO CARRE, ZERO ICE)
    // =========================================================================

    /**
     * Construit un champ continu de neige sur une grille QuartPos (pas de 4 blocs) mais evalue
     * par <b>convolution hexagonale jitterisee sans grille carree</b> (KdotJPG ScatteredBiomeBlender),
     * en ignorant strictement tout echantillon GLACE / EAU ({@code potential < 0f}).
     *
     * @param seed        graine du monde
     * @param qx0         origine QuartPos X (worldX = qx * 4 + 2)
     * @param qz0         origine QuartPos Z (worldZ = qz * 4 + 2)
     * @param qW          largeur en quarts
     * @param qH          hauteur en quarts
     * @param radiusBlocks rayon d'aura en blocs (ex. 22, 34, 46 selon rChunks = 1..3)
     * @param sampler     potentiel neigeux terrestre en (worldX, worldZ) dans [0..1], ou -1f sur ICE/EAU
     */
    public static float[] buildScatteredSnowField(long seed,
                                                  int qx0,
                                                  int qz0,
                                                  int qW,
                                                  int qH,
                                                  double radiusBlocks,
                                                  TerrestrialSnowSampler sampler) {
        double freq = 0.095; // ~1 point jitterise tous les ~10.5 blocs (couverture dense et ultra-rapide)
        double invFreq = 1.0 / freq;
        double r = Math.max(18.0, radiusBlocks);
        double rSq = r * r;

        double minWx = qx0 * 4.0 - r - 12.0;
        double maxWx = (qx0 + qW) * 4.0 + r + 12.0;
        double minWz = qz0 * 4.0 - r - 12.0;
        double maxWz = (qz0 + qH) * 4.0 + r + 12.0;

        // Balayage englobant dans l'espace Simplex skew
        double[] cornersX = {minWx * freq, maxWx * freq, minWx * freq, maxWx * freq};
        double[] cornersZ = {minWz * freq, minWz * freq, maxWz * freq, maxWz * freq};
        int minI = Integer.MAX_VALUE, maxI = Integer.MIN_VALUE;
        int minJ = Integer.MAX_VALUE, maxJ = Integer.MIN_VALUE;
        for (int c = 0; c < 4; c++) {
            double sk = (cornersX[c] + cornersZ[c]) * F2;
            int si = fastFloor(cornersX[c] + sk);
            int sj = fastFloor(cornersZ[c] + sk);
            if (si < minI) minI = si;
            if (si > maxI) maxI = si;
            if (sj < minJ) minJ = sj;
            if (sj > maxJ) maxJ = sj;
        }
        minI -= 2; maxI += 2; minJ -= 2; maxJ += 2;

        int maxPts = Math.max(64, (maxI - minI + 1) * (maxJ - minJ + 1));
        float[] px = new float[maxPts];
        float[] pz = new float[maxPts];
        float[] pv = new float[maxPts];
        int nPts = 0;

        int seedLo = (int) (seed & 0xFFFFFFFFL);
        int seedHi = (int) (seed >>> 32);

        // Indexation spatiale par cellule de 32 blocs pour O(1) par quart
        int cellShift = 5; // 32 blocs
        int cellX0 = fastFloor(minWx) >> cellShift;
        int cellZ0 = fastFloor(minWz) >> cellShift;
        int cellW = Math.max(1, (fastFloor(maxWx) >> cellShift) - cellX0 + 1);
        int cellH = Math.max(1, (fastFloor(maxWz) >> cellShift) - cellZ0 + 1);
        int[] head = new int[cellW * cellH];
        Arrays.fill(head, -1);
        int[] next = new int[maxPts];

        for (int i = minI; i <= maxI; i++) {
            int ip = i * PRIME_X;
            for (int j = minJ; j <= maxJ; j++) {
                int jp = j * PRIME_Z;
                int hash = ip ^ jp;
                hash = ((seedLo ^ hash) * 668908897) ^ ((seedHi ^ hash) * 35311);
                int indexBase = (hash & 0x3FFFFFF) * 0x5555555;
                int idx = (indexBase >>> 26) & VEC_MASK;

                double t = (i + j) * -G2;
                double wx = (i + t + JITTER_SINCOS[idx]) * invFreq;
                double wz = (j + t + JITTER_SINCOS[idx + SINCOS_OFFSET]) * invFreq;
                if (wx < minWx || wx > maxWx || wz < minWz || wz > maxWz) continue;

                float pot = sampler.sampleSnowPotential(fastFloor(wx + 0.5), fastFloor(wz + 0.5));
                if (pot < 0f) continue; // ICE ou EAU : 0 poids dans le denominateur et le numerateur !

                if (nPts >= px.length) {
                    int nCap = px.length * 2;
                    px = Arrays.copyOf(px, nCap);
                    pz = Arrays.copyOf(pz, nCap);
                    pv = Arrays.copyOf(pv, nCap);
                    next = Arrays.copyOf(next, nCap);
                }
                int cxCell = (fastFloor(wx) >> cellShift) - cellX0;
                int czCell = (fastFloor(wz) >> cellShift) - cellZ0;
                if (cxCell < 0 || cxCell >= cellW || czCell < 0 || czCell >= cellH) continue;

                px[nPts] = (float) wx;
                pz[nPts] = (float) wz;
                pv[nPts] = pot;
                int cIdx = czCell * cellW + cxCell;
                next[nPts] = head[cIdx];
                head[cIdx] = nPts;
                nPts++;
            }
        }

        float[] out = new float[qW * qH];
        if (nPts == 0) return out;

        int cellRad = ((int) Math.ceil(r) >> cellShift) + 1;
        float rSqF = (float) rSq;

        for (int qz = 0; qz < qH; qz++) {
            float wz = (qz0 + qz) * 4.0f + 2.0f;
            int czCenter = (((int) Math.floor(wz)) >> cellShift) - cellZ0;
            int czMin = Math.max(0, czCenter - cellRad);
            int czMax = Math.min(cellH - 1, czCenter + cellRad);
            int row = qz * qW;

            for (int qx = 0; qx < qW; qx++) {
                float wx = (qx0 + qx) * 4.0f + 2.0f;
                int cxCenter = (((int) Math.floor(wx)) >> cellShift) - cellX0;
                int cxMin = Math.max(0, cxCenter - cellRad);
                int cxMax = Math.min(cellW - 1, cxCenter + cellRad);

                double accV = 0.0, accW = 0.0;
                for (int cz = czMin; cz <= czMax; cz++) {
                    int baseCell = cz * cellW;
                    for (int cx = cxMin; cx <= cxMax; cx++) {
                        for (int p = head[baseCell + cx]; p != -1; p = next[p]) {
                            float dx = wx - px[p];
                            float dz = wz - pz[p];
                            float dSq = dx * dx + dz * dz;
                            if (dSq < rSqF) {
                                double w = rSqF - dSq;
                                w *= w; // (R^2 - d^2)^2
                                accV += pv[p] * w;
                                accW += w;
                            }
                        }
                    }
                }
                out[row + qx] = accW > 1.0e-6 ? (float) Math.max(0.0, Math.min(1.0, accV / accW)) : 0f;
            }
        }
        return out;
    }
}
