package deepluckyblock.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T289 / T303 : CERVEAU MERE ({@link TerrainMother}) + CERVEAUX ENFANTS PAR CHUNK ({@link Child}).
 *
 * <p>Architecture Mère / Enfants alignée sur le plan Phases A / B / C et sur deux
 * références open-source éprouvées :
 * <ul>
 *   <li><b>EngineHub/WorldEdit</b> ({@code GaussianKernel}, {@code HeightMapFilter},
 *       {@code SnowHeightMap}) : convolution gaussienne séparable normalisée sur
 *       grille continue + épaisseur de neige progressive (3 &rarr; 2 &rarr; 1 couches) ;</li>
 *   <li><b>Vanilla Minecraft {@code net.minecraft.world.level.levelgen.blending.Blender}</b>
 *       ({@code QuartPos} 4 blocs + domain-warp bruité type {@code SHIFT_NOISE}) :
 *       aucune fenêtre entière indexée par {@code x >> 4} ni coupure de bord de chunk.</li>
 * </ul>
 *
 * <p><b>Rôles :</b>
 * <ol>
 *   <li><b>{@link Child}</b> (1 cerveau indépendant par chunk 16&times;16, peuplé en A3/A4
 *       avant toute modification) : photographie la hauteur sol min / médiane / max
 *       ({@link Child#highestGroundY()}), détecte les murs verticaux de blocs empilés
 *       droits ({@link Child#hasVerticalWall()}) et les crevasses ({@link Child#hasCrevasse()}),
 *       mémorise le nombre d'arbres photographiés et répond instantanément en O(1) à
 *       {@code isSnowy()} ({@code true}/{@code false}), {@code needsSmooth()} et
 *       {@code needsRefill()}.</li>
 *   <li><b>{@link TerrainMother}</b> : seule responsable des chunks centraux de la
 *       <b>STRUCTURE</b> ({@link #setStructureBounds(int, int, int, int)}), garde tous
 *       les enfants du chantier actifs et ouverts, vérifie en A1 que l'écart de Y sol
 *       entre chunks structure adjacents ne dépasse jamais <b>2 blocs</b>
 *       ({@link #worstAdjacentGap(int, int, int, int)}), interroge ses enfants pour
 *       décider du re-remplissage de crevasses et des smooths ({@link #compileVerdict()}),
 *       et calcule sur le damier complet (+ halo externe de 3 chunks) une aura de neige
 *       continue sur 1 à 3 chunks avec liaisons entre masses neigeuses, sans aucune
 *       délimitation de chunk ni ligne épaisse en bordure.</li>
 * </ol>
 */
public final class TerrainMother {

    /** Instantané immuable du potentiel continu échantillonné à l'échelle colonne/quart. */
    public static final class SnowSpreadSnapshot {
        private final int qx0, qz0, qw, qh;
        private final float[] potential;
        private SnowSpreadSnapshot(int qx0, int qz0, int qw, int qh, float[] potential) {
            this.qx0 = qx0; this.qz0 = qz0; this.qw = qw; this.qh = qh;
            this.potential = java.util.Arrays.copyOf(potential, potential.length);
        }
        /** Potentiel continu interpolé à la colonne monde; NaN = hors instantané. */
        public float rateAtBlock(int x, int z) {
            double fx = (x - 2.0) * 0.25 - qx0;
            double fz = (z - 2.0) * 0.25 - qz0;
            if (fx < 0 || fz < 0 || fx >= qw - 1 || fz >= qh - 1) return Float.NaN;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            float tx = (float) (fx - ix), tz = (float) (fz - iz);
            float a = potential[iz * qw + ix], b = potential[iz * qw + ix + 1];
            float c = potential[(iz + 1) * qw + ix], d = potential[(iz + 1) * qw + ix + 1];
            float top = a + (b - a) * tx, bottom = c + (d - c) * tx;
            return top + (bottom - top) * tz;
        }
    }

    /** Écart maximal autorisé (en blocs) entre le Y sol de deux chunks STRUCTURE adjacents (règle A1). */
    public static final int MAX_STRUCTURE_CHUNK_STEP_Y = 2;

    /** Marge de stockage monde observée autour du damier. */
    public static final int HALO_CHUNKS = 3;
    /** Densité à partir de laquelle la couverture devient pleine; source utile et frange sont entre 0 et cette valeur. */
    public static final float SNOW_FULL_COVERAGE_AURA = 0.78f;
    /** Rayon du noyau continu, inférieur aux 48 blocs disponibles dans le halo de trois chunks. */
    private static final double SNOW_FIELD_RADIUS_BLOCKS = 47.0;

    // ------------------------------------------------------------------
    // ENFANT (1 cerveau par chunk 16x16)
    // ------------------------------------------------------------------
    public static final class Child {
        public final int cx, cz;
        /** Hauteurs sol vierges (MOTION_BLOCKING_NO_LEAVES hors feuilles, figées au scout A3). */
        public int surfaceYMedian = Integer.MIN_VALUE;
        public int surfaceYMin = Integer.MIN_VALUE;
        public int surfaceYMax = Integer.MIN_VALUE;
        /** Plus grand saut vertical entre deux colonnes voisines du chunk (blocs empilés droits). */
        public int maxStepJump = 0;
        /** Nombre de colonnes en crevasse locale (>= 4 blocs sous leurs voisines). */
        public int crevasseColumns = 0;
        /** Arbres photographiés dans ce chunk en A3/A4 pour replantation en C4. */
        public int savedTrees = 0;
        /** Ratio vierge de colonnes enneigées [0..1] et réponse booléenne franche isSnowy. */
        public float snowyRatio;
        private Boolean snowyOverride = null;
        /** Ratios eau / glace photographiés au scout. */
        public float waterRatio, iceRatio;
        /** true = mur quasi vertical ou saut brutal détecté dans ce chunk. */
        public boolean steep;
        /** true si ce chunk appartient à l'emprise centrale de la structure (géré par Mother). */
        public boolean structureCore = false;
        /** true tant que Mother maintient cet enfant ouvert et actif dans le chantier. */
        public boolean present = true;

        Child(int cx, int cz) { this.cx = cx; this.cz = cz; }

        /** Fixe explicitement la réponse booléenne {@code =isSnowy} de l'enfant. */
        public void setSnowy(boolean snowy) { this.snowyOverride = snowy; }

        /** Poids de terre ferme hors eau et glace [0..1] (T305 : la glace et l'eau sont exclues de tout dégradé). */
        public float landWeight() {
            float lw = 1.0f - waterRatio - iceRatio;
            return lw < 0f ? 0f : (lw > 1f ? 1f : lw);
        }

        /** Vrai si le chunk est dominé par la glace ou l'eau (interdit de transitionner avec lui). */
        public boolean isIceOrWaterDominated() {
            return iceRatio >= 0.30f || (waterRatio + iceRatio) >= 0.55f;
        }

        /** QUESTION MERE « =isSnowy » (true / false) : hors nappe d'eau/glace (R4 + T305). */
        public boolean isSnowy() {
            if (isIceOrWaterDominated()) return false;
            if (snowyOverride != null) return snowyOverride;
            return snowyRatio >= 0.45f;
        }

        /** Y sol le plus haut mesuré dans cet enfant (demande instantanée Mother -> Child). */
        public int highestGroundY() { return surfaceYMax; }

        /** Étendue interne du chunk (max - min). */
        public int span() {
            return surfaceYMin == Integer.MIN_VALUE ? 0 : surfaceYMax - surfaceYMin;
        }

        /** Vrai si le chunk présente des blocs empilés droits / saut trop raide pour une colline naturelle. */
        public boolean hasVerticalWall() {
            return steep || maxStepJump >= 4 || span() > 14;
        }

        /** Vrai si le chunk contient une crevasse ou un effondrement local à combler. */
        public boolean hasCrevasse() {
            return crevasseColumns > 0;
        }

        /** Réponse instantanée à Mother : ce chunk réclame-t-il un re-remplissage de crevasse ? */
        public boolean needsRefill() {
            return present && surfaceYMedian != Integer.MIN_VALUE && hasCrevasse();
        }

        /** Réponse instantanée à Mother : ce chunk réclame-t-il un lissage (smooth) ? */
        public boolean needsSmooth() {
            return present && surfaceYMedian != Integer.MIN_VALUE && (hasVerticalWall() || span() > 3);
        }
    }

    /** Synthèse instantanée des réponses de tous les enfants à Mother (fin de A3/A4, avant A9). */
    public record MotherVerdict(
            int totalChildren,
            int coreChildren,
            int contourChildren,
            int highestGroundY,
            int worstStructureChunkGap,
            int maxStepJump,
            int wallChildren,
            int crevasseChildren,
            int snowyChildren,
            boolean requestRefill,
            boolean requestSmooth,
            int snowAuraRadiusChunks
    ) {}

    // ------------------------------------------------------------------
    // MERE
    // ------------------------------------------------------------------
    private static volatile TerrainMother CURRENT;
    private static volatile SnowSpreadSnapshot LAST_SNOW_SPREAD_SNAPSHOT;
    private volatile SnowSpreadSnapshot snowSpreadSnapshot;

    /** Mère active (une seule chaîne terrain à la fois, garantie par TerrainChain / FIFO). */
    public static TerrainMother current() { return CURRENT; }

    /** Instantané déjà calculé; ne déclenche jamais la construction coûteuse du champ. */
    public static SnowSpreadSnapshot latestSnowSpreadSnapshot() {
        TerrainMother m = CURRENT;
        return m != null ? m.snowSpreadSnapshot : LAST_SNOW_SPREAD_SNAPSHOT;
    }

    private void publishSnowSpreadSnapshot(float[] potential, int qx0, int qz0, int qw, int qh) {
        SnowSpreadSnapshot snapshot = new SnowSpreadSnapshot(qx0, qz0, qw, qh, potential);
        snowSpreadSnapshot = snapshot;
        LAST_SNOW_SPREAD_SNAPSHOT = snapshot;
    }

    /** Installe la mère de la zone (boîte = zone finale tenue par ChunkKeeper, en chunks). */
    public static TerrainMother begin(int gx0, int gz0, int gx1, int gz1) {
        LAST_SNOW_SPREAD_SNAPSHOT = null;
        TerrainMother m = new TerrainMother(gx0, gz0, gx1, gz1);
        CURRENT = m;
        return m;
    }

    /** Dissout la mère en fin de chaîne (C11) ou sur abandon propre. */
    public static void end() {
        TerrainMother m = CURRENT;
        if (m != null) {
            java.util.concurrent.CompletableFuture<MotherAISnowDirector.SnowPlan> f = m.aiFuture;
            if (f != null && !f.isDone()) f.cancel(true);
            m.aiPlan = null;
            if (m.snowSpreadSnapshot != null) LAST_SNOW_SPREAD_SNAPSHOT = m.snowSpreadSnapshot;
            m.auraQuartGrid = null;
        }
        CURRENT = null;
    }

    private final int gx0, gz0, gw, gh;
    private final Child[] grid;

    // Halo externe (gx0 - HALO_CHUNKS .. gx1 + HALO_CHUNKS) pour que le bord du chantier
    // connaisse les chunks secs / enneigés situés juste au-delà de ChunkKeeper (zéro mur en ligne 15).
    private final int hx0, hz0, hw, hh;
    private final float[] haloSnowPotential; // NaN = inconnu; [0,1] = source neige continue
    private final float[] haloLandWeight; // [0,1] fraction terrestre, eau/glace sans veto par chunk

    private int scx0 = Integer.MIN_VALUE, scz0 = Integer.MIN_VALUE;
    private int scx1 = Integer.MIN_VALUE, scz1 = Integer.MIN_VALUE;
    private long worldSeed = 0L;

    // Grille continue d'aura neige précalculée au pas QuartPos (4 blocs, comme Vanilla Blender)
    private volatile float[] auraQuartGrid = null;
    private int auraQx0, auraQz0, auraQw, auraQh;
    private boolean auraAllZero = false;
    private boolean auraAllOne = false;

    private TerrainMother(int gx0, int gz0, int gx1, int gz1) {
        this.gx0 = gx0;
        this.gz0 = gz0;
        this.gw = Math.max(1, gx1 - gx0 + 1);
        this.gh = Math.max(1, gz1 - gz0 + 1);
        this.grid = new Child[gw * gh];
        this.hx0 = gx0 - HALO_CHUNKS;
        this.hz0 = gz0 - HALO_CHUNKS;
        this.hw = gw + HALO_CHUNKS * 2;
        this.hh = gh + HALO_CHUNKS * 2;
        this.haloSnowPotential = new float[hw * hh];
        this.haloLandWeight = new float[hw * hh];
        java.util.Arrays.fill(this.haloSnowPotential, Float.NaN);
        java.util.Arrays.fill(this.haloLandWeight, Float.NaN);
    }

    /** Associe la semence du monde pour le domain-warp organique (façon Vanilla Blender SHIFT_NOISE). */
    public void setWorldSeed(long seed) {
        if (this.worldSeed != seed) {
            this.worldSeed = seed;
        }
    }

    /** Déclare les chunks centraux de la STRUCTURE dont Mother a la responsabilité directe. */
    public void setStructureBounds(int scx0, int scz0, int scx1, int scz1) {
        this.scx0 = scx0;
        this.scz0 = scz0;
        this.scx1 = scx1;
        this.scz1 = scz1;
        for (int cx = scx0; cx <= scx1; cx++) {
            for (int cz = scz0; cz <= scz1; cz++) {
                Child c = child(cx, cz);
                if (c != null) c.structureCore = true;
            }
        }
    }

    /** Enregistre une mesure continue du potentiel neigeux d'un chunk du halo (aucune décision binaire). */
    public void setHaloSnowPotential(int cx, int cz, float potential) {
        int i = cx - hx0, j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return;
        float v = Math.max(0f, Math.min(1f, potential));
        int idx = j * hw + i;
        if (Float.isNaN(haloSnowPotential[idx]) || haloSnowPotential[idx] != v) {
            haloSnowPotential[idx] = v;
            auraQuartGrid = null;
        }
    }

    /** Stocke une fraction terrestre mesurée pour l'écotone eau/glace du halo. */
    public void setHaloLandWeight(int cx, int cz, float weight) {
        int i = cx - hx0, j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return;
        haloLandWeight[j * hw + i] = Math.max(0f, Math.min(1f, weight));
        auraQuartGrid = null;
    }

    /** Invalide le champ d'aura précalculé après mise à jour d'enfants. */
    public void invalidateSnowField() {
        this.auraQuartGrid = null;
    }

    /** Enfant du chunk s'il est dans la grille suivie (null = hors zone suivie). */
    public Child child(int cx, int cz) {
        int i = cx - gx0, j = cz - gz0;
        if (i < 0 || j < 0 || i >= gw || j >= gh) return null;
        int k = j * gw + i;
        Child c = grid[k];
        if (c == null) {
            c = new Child(cx, cz);
            if (scx0 != Integer.MIN_VALUE && cx >= scx0 && cx <= scx1 && cz >= scz0 && cz <= scz1) {
                c.structureCore = true;
            }
            grid[k] = c;
            auraQuartGrid = null;
        }
        return c;
    }

    /** Tous les enfants effectivement scoutés (pour MotherSnowBrain et diagnostics). */
    public List<Child> allChildren() {
        List<Child> out = new ArrayList<>();
        for (Child c : grid) if (c != null && c.present) out.add(c);
        return out;
    }

    // ------------------------------------------------------------------
    // A1 — Veto inter-chunks STRUCTURE : écart max <= 2 blocs entre deux
    // chunks structure adjacents pour éviter tout terrain qui s'affaisse.
    // ------------------------------------------------------------------
    public int worstAdjacentGap(int qcx0, int qcz0, int qcx1, int qcz1) {
        int worst = Integer.MIN_VALUE;
        boolean any = false;
        for (int cx = qcx0; cx <= qcx1; cx++) {
            for (int cz = qcz0; cz <= qcz1; cz++) {
                Child c = child(cx, cz);
                if (c == null || c.surfaceYMedian == Integer.MIN_VALUE) continue;
                Child e = (cx + 1 <= qcx1) ? child(cx + 1, cz) : null;
                Child s = (cz + 1 <= qcz1) ? child(cx, cz + 1) : null;
                if (e != null && e.surfaceYMedian != Integer.MIN_VALUE) {
                    worst = Math.max(worst, Math.abs(c.surfaceYMedian - e.surfaceYMedian));
                    any = true;
                }
                if (s != null && s.surfaceYMedian != Integer.MIN_VALUE) {
                    worst = Math.max(worst, Math.abs(c.surfaceYMedian - s.surfaceYMedian));
                    any = true;
                }
            }
        }
        return any ? worst : Integer.MIN_VALUE;
    }

    /**
     * Interroge instantanément tous les enfants ouverts (coeur structure + contours) :
     * Y sol le plus haut, murs de blocs empilés droits, crevasses et damier {@code isSnowy}.
     */
    public MotherVerdict compileVerdict() {
        awaitAiPlanOnce();
        evaluateSnowRadius();
        int total = 0, core = 0, contour = 0;
        int highestY = Integer.MIN_VALUE, maxJump = 0;
        int walls = 0, crevasses = 0, snowy = 0;
        boolean refill = false, smooth = false;
        for (Child c : grid) {
            if (c == null || !c.present || c.surfaceYMedian == Integer.MIN_VALUE) continue;
            total++;
            if (c.structureCore) core++; else contour++;
            if (c.highestGroundY() > highestY) highestY = c.highestGroundY();
            if (c.maxStepJump > maxJump) maxJump = c.maxStepJump;
            if (c.hasVerticalWall()) walls++;
            if (c.hasCrevasse()) crevasses++;
            if (c.isSnowy()) snowy++;
            if (c.needsRefill()) refill = true;
            if (c.needsSmooth()) smooth = true;
        }
        int structGap = (scx0 != Integer.MIN_VALUE)
                ? worstAdjacentGap(scx0, scz0, scx1, scz1)
                : Integer.MIN_VALUE;
        return new MotherVerdict(total, core, contour, highestY, structGap, maxJump,
                walls, crevasses, snowy, refill, smooth, snowRadiusChunks);
    }

    // ------------------------------------------------------------------
    // AURA DE NEIGE CONTINUE MULTI-CHUNKS (refonte complète T303)
    // ------------------------------------------------------------------
    private int snowRadiusChunks = 2;
    private boolean snowRadiusLocked = false;

    private volatile java.util.concurrent.CompletableFuture<MotherAISnowDirector.SnowPlan> aiFuture = null;
    private volatile MotherAISnowDirector.SnowPlan aiPlan = null;
    private volatile boolean aiAwaited = false;

    /** Lance la consultation IA / cerveau embarqué MotherSnowBrain (une fois par zone). */
    public void startAiConsultation() {
        if (aiFuture != null) return;
        aiFuture = MotherAISnowDirector.consultAsync(this);
    }

    /** T303 : relance la consultation IA une fois tous les enfants du damier enregistrés (A3->A4). */
    public void refreshAiConsultation() {
        aiFuture = MotherAISnowDirector.consultAsync(this);
        aiAwaited = false;
        awaitAiPlanOnce();
    }

    /** T303 : expose le plan IA actif (MotherSnowBrain embarqué ou Ollama) pour audit/logs. */
    public MotherAISnowDirector.SnowPlan activeAiPlan() {
        awaitAiPlanOnce();
        return aiPlan;
    }

    private void awaitAiPlanOnce() {
        if (aiAwaited) return;
        aiAwaited = true;
        if (aiFuture == null) return;
        try {
            MotherAISnowDirector.SnowPlan p = aiFuture.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (p != null) {
                aiPlan = p;
                if (p.radiusChunks() > 0) setSnowRadiusChunks(p.radiusChunks());
                auraQuartGrid = null;
            }
        } catch (java.util.concurrent.TimeoutException te) {
            aiFuture.cancel(true);
        } catch (Throwable ignored) {
        }
    }

    /** Impose le rayon de l'aura en chunks (1..4) et invalide la grille précalculée. */
    public void setSnowRadiusChunks(int r) {
        // API gardée pour l'ancien plan IA; la portée ne dépend plus d'un compte de chunks.
        snowRadiusChunks = HALO_CHUNKS;
        snowRadiusLocked = true;
        auraQuartGrid = null;
    }

    /**
     * Analyse topologique du damier vue de haut par Mother :
     * choisit une portée de 1 à 3 chunks selon le mélange d'enfants {@code isSnowy=true/false}.
     */
    private void evaluateSnowRadius() {
        // Rayon fixe indépendant du motif de chunks; le halo contractuel vaut trois chunks.
        snowRadiusChunks = HALO_CHUNKS;
    }

    /** Potentiel source mesuré [0,1], sans seuil de classification par chunk. */
    private float cellSnowPotential(int cx, int cz) {
        Child c = child(cx, cz);
        if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE)
            return Math.max(0f, Math.min(1f, c.snowyRatio));
        int i = cx - hx0, j = cz - hz0;
        if (i >= 0 && j >= 0 && i < hw && j < hh) {
            float value = haloSnowPotential[j * hw + i];
            return Float.isNaN(value) ? 0f : value;
        }
        return 0f;
    }

    /** Construit le champ continu au pas QuartPos depuis ratios sources et poids eau/terre fractionnaires. */
    private float[] ensureAuraField() {
        awaitAiPlanOnce();
        float[] ready = auraQuartGrid;
        if (ready != null) return ready;

        int known = 0;
        for (Child c : grid) {
            if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE) {
                known++;
            }
        }
        if (known == 0) return null;

        final MotherAISnowDirector.SnowPlan plan = aiPlan;
        final float[] cellPotential = new float[hw * hh];
        final float[] cellWeight = new float[hw * hh];
        boolean anyLand = false;

        for (int cj = 0; cj < hh; cj++) {
            int cz = hz0 + cj;
            for (int ci = 0; ci < hw; ci++) {
                int cx = hx0 + ci;
                int idx = cj * hw + ci;
                Child ch = child(cx, cz);
                float land = 1f;
                if (ch != null && ch.present && ch.surfaceYMedian != Integer.MIN_VALUE) {
                    // Fondu continu de la part eau/glace observée, pas de veto catégoriel du chunk.
                    land = Math.max(0f, 1f - Math.min(1f, ch.waterRatio + ch.iceRatio));
                } else if (ci >= 0 && cj >= 0 && ci < hw && cj < hh
                        && !Float.isNaN(haloLandWeight[idx])) {
                    land = haloLandWeight[idx];
                }
                cellWeight[idx] = land;
                float source = cellSnowPotential(cx, cz) * land;
                if (plan != null && source > 0f) {
                    Float boost = plan.childAuraBoost().get((((long) cx) << 32) | (cz & 0xffffffffL));
                    if (boost != null) source = Math.max(0f, Math.min(1f, source * boost));
                }
                cellPotential[idx] = source;
                anyLand |= land > 0.15f;
            }
        }

        boolean anySource = false;
        boolean allSource = true;
        for (int i = 0; i < cellPotential.length; i++) {
            if (cellWeight[i] > 0.15f) {
                anySource |= cellPotential[i] > 0f;
                allSource &= cellPotential[i] >= 0.999f;
            }
        }
        if (!anySource) {
            auraAllZero = true;
            auraAllOne = false;
            auraQx0 = hx0 * 4; auraQz0 = hz0 * 4; auraQw = hw * 4; auraQh = hh * 4;
            auraQuartGrid = new float[auraQw * auraQh];
            publishSnowSpreadSnapshot(auraQuartGrid, auraQx0, auraQz0, auraQw, auraQh);
            return auraQuartGrid;
        }
        if (allSource && anyLand && plan == null) {
            auraAllOne = true;
            auraAllZero = false;
            auraQx0 = hx0 * 4; auraQz0 = hz0 * 4; auraQw = hw * 4; auraQh = hh * 4;
            auraQuartGrid = new float[auraQw * auraQh];
            java.util.Arrays.fill(auraQuartGrid, 1f);
            publishSnowSpreadSnapshot(auraQuartGrid, auraQx0, auraQz0, auraQw, auraQh);
            return auraQuartGrid;
        }
        auraAllZero = false;
        auraAllOne = false;
        final float[] linkedPotential = cellPotential.clone();

        // Étape 3 (T306-ECOTONE) : Convolution éparse sur réseau hexagonal jitterisé
        // (KdotJPG ScatteredBiomeBlender, noyau quartique (R^2 - d^2)^2, SANS grille carrée),
        // où toute cellule GLACE / EAU retourne -1f (poids strictement nul : 0 dégradé avec l'ICE,
        // et fondu entrant depuis les 3 côtés neigeux si le 4e côté est de l'ICE !).
        final int qW = hw * 4;
        final int qH = hh * 4;
        final int qx0 = hx0 * 4;
        final int qz0 = hz0 * 4;
        final double radiusBlocks = SNOW_FIELD_RADIUS_BLOCKS;
        final long effSeed = worldSeed != 0L ? worldSeed : 0x517CC1B727220A95L;

        final int fHx0 = hx0, fHz0 = hz0, fHw = hw, fHh = hh;
        final float[] out = BiomeEcotoneSpread.buildScatteredSnowField(
                effSeed, qx0, qz0, qW, qH, radiusBlocks,
                (wx, wz) -> {
                    int ccx = wx >> 4;
                    int ccz = wz >> 4;
                    int ci = ccx - fHx0;
                    int cj = ccz - fHz0;
                    if (ci < 0 || ci >= fHw || cj < 0 || cj >= fHh) return -1f;
                    int cIdx = cj * fHw + ci;
                    // Eau/glace entrent comme potentiel nul et poids continu, pas comme coupure catégorielle.
                    return linkedPotential[cIdx];
                });

        this.auraQx0 = qx0;
        this.auraQz0 = qz0;
        this.auraQw = qW;
        this.auraQh = qH;
        this.auraQuartGrid = out;
        publishSnowSpreadSnapshot(out, qx0, qz0, qW, qH);
        return out;
    }

    /**
     * Densite continue au niveau colonne: interpolation du champ jitterise et domain-warp
     * isotrope applique sans garde de chunk. L'eau/glace est geree par son poids fractionnaire
     * puis par le garde physique colonne au point de pose.
     * Renvoie -1f uniquement si aucun enfant n'a encore ete renseigne.
     */


    public float snowDensity(int x, int z) {
        awaitAiPlanOnce();
        evaluateSnowRadius();
        float[] field = ensureAuraField();
        if (field == null) return -1f;
        if (auraAllZero) return 0f;
        if (auraAllOne) return 1f;

        double sampleX = x + 0.5;
        double sampleZ = z + 0.5;
        if (worldSeed != 0L) {
            // T306-ECOTONE : Domain-warp Simplex 2D isotrope bi-octave (aucun alignement X/Z)
            double wx = BiomeEcotoneSpread.simplex2(worldSeed ^ 0x6A09E667F3BCC909L, x / 22.0, z / 22.0) * 7.5
                      + BiomeEcotoneSpread.simplex2(worldSeed ^ 0xBB67AE8584CAA73BL, x / 9.5,  z / 9.5)  * 2.8;
            double wz = BiomeEcotoneSpread.simplex2(worldSeed ^ 0x3C6EF372FE94F82BL, x / 22.0 + 19.1, z / 22.0 - 13.7) * 7.5
                      + BiomeEcotoneSpread.simplex2(worldSeed ^ 0xA54FF53A5F1D36F1L, x / 9.5  - 7.3,  z / 9.5  + 11.9) * 2.8;
            sampleX += wx;
            sampleZ += wz;
        }

        // Coordonnée continue dans la grille QuartPos (centre d'un quart = q*4 + 2.0)
        double fqX = (sampleX - 2.0) * 0.25 - auraQx0;
        double fqZ = (sampleZ - 2.0) * 0.25 - auraQz0;
        double cx = Math.max(0.0, Math.min(auraQw - 1.0001, fqX));
        double cz = Math.max(0.0, Math.min(auraQh - 1.0001, fqZ));

        int ix = (int) Math.floor(cx);
        int iz = (int) Math.floor(cz);
        int ix1 = Math.min(auraQw - 1, ix + 1);
        int iz1 = Math.min(auraQh - 1, iz + 1);
        float fx = (float) (cx - ix);
        float fz = (float) (cz - iz);
        // Lissage Quintique Perlin C2 entre quarts (6t^5 - 15t^4 + 10t^3) : zéro cassure de dérivée
        fx = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f);
        fz = fz * fz * fz * (fz * (fz * 6f - 15f) + 10f);

        float v00 = field[iz * auraQw + ix];
        float v10 = field[iz * auraQw + ix1];
        float v01 = field[iz1 * auraQw + ix];
        float v11 = field[iz1 * auraQw + ix1];
        float lo = v00 + (v10 - v00) * fx;
        float hi = v01 + (v11 - v01) * fx;
        return lo + (hi - lo) * fz;
    }

    /**
     * T306-ECOTONE : Décision finale de couverture neigeuse en {@code (x, z)} par langues et
     * congères cohérentes Simplex fBm domain-warpées (zéro confetti 1x1, zéro bordure carrée) :
     * <ul>
     *   <li>{@code aura >= 0.85f} (coeur de massif enneigé) : manteau continu ;</li>
     *   <li>{@code aura <= 0.04f} (hors portée d'aura) : zéro neige ;</li>
     *   <li>entre les deux : enveloppe d'écotone {@code 4*t*(1-t)} modulée par des langues
     *       organiques multi-échelles (19 bl + 7.5 bl + 3.2 bl) imitant de vraies plaques de fonte/congères.</li>
     * </ul>
     */
    public boolean shouldPlaceSnow(long seed, int x, int z) {
        setWorldSeed(seed);
        float aura = snowDensity(x, z);
        if (aura < 0f) return false;
        if (aura >= SNOW_FULL_COVERAGE_AURA) return true;
        if (aura <= 0f) return false;
        float t = Math.max(0f, Math.min(1f, aura / SNOW_FULL_COVERAGE_AURA));
        float coverageProbability = t * t * (3f - 2f * t);
        float patch = BiomeEcotoneSpread.warpedPatchSigned(seed ^ 0xD217F00D5EEDL, x, z) + 0.5f;
        // La variation spatiale tranche la probabilité continue; aucune discontinuité au seuil d'un chunk.
        return patch < coverageProbability;
    }

    /**
     * T306-ECOTONE : Épaisseur progressive réaliste des congères (1 à 3 couches) :
     * fine (1 couche) sur toute la frange d'écotone externe, ondulation organique 1-2 couches
     * en zone médiane, et vraies congères éoliennes 1-3 couches au coeur du manteau neigeux.
     */
    public int snowLayersAt(long seed, int x, int z) {
        setWorldSeed(seed);
        float aura = snowDensity(x, z);
        if (aura < 0.46f) return 1;
        double drift = 0.5 + 0.5 * (0.68 * BiomeEcotoneSpread.simplex2(seed ^ 0x9E3779B97F4A7C15L, x / 13.0, z / 13.0)
                                  + 0.32 * BiomeEcotoneSpread.simplex2(seed ^ 0xBF58476D1CE4E5B9L, x / 5.5,  z / 5.5));
        if (aura < 0.72f) {
            return drift < 0.58 ? 1 : 2;
        }
        return drift < 0.52 ? 1 : (drift < 0.71 ? 2 : 3);
    }

    private static double smoothValueNoise(long seed, int x, int z, double scale) {
        int gx = (int) Math.floor(x / scale);
        int gz = (int) Math.floor(z / scale);
        double fx = (x / scale) - gx;
        double fz = (z / scale) - gz;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double v00 = hash01(seed, gx, gz);
        double v10 = hash01(seed, gx + 1, gz);
        double v01 = hash01(seed, gx, gz + 1);
        double v11 = hash01(seed, gx + 1, gz + 1);
        double lo = v00 + (v10 - v00) * fx;
        double hi = v01 + (v11 - v01) * fx;
        return lo + (hi - lo) * fz;
    }

    private static double hash01(long seed, int gx, int gz) {
        long h = seed ^ ((long) gx * 0x9E3779B97F4A7C15L) ^ ((long) gz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return ((h >>> 11) & 0xFFFFFL) / 1048576.0;
    }

    /**
     * T303-E : renvoie la liste des enfants de CONTOUR (hors coeur structure)
     * qui signalent des crevasses profondes ({@link Child#needsRefill()}) afin que
     * la Mere ordonne leur comblement cible avant le lissage A9.
     */
    public java.util.List<Child> contourRefillTargets() {
        java.util.List<Child> out = new java.util.ArrayList<>();
        for (Child c : grid) {
            if (c != null && c.present && !c.structureCore && c.needsRefill()) {
                out.add(c);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Cache d'enfants STANDALONE hors chaîne (sélection de zone A1)
    // ------------------------------------------------------------------
    private static final Map<Long, Child> STANDALONE = new HashMap<>();

    public static Child standaloneChild(int cx, int cz) {
        long k = (((long) cx) << 32) | (cz & 0xffffffffL);
        return STANDALONE.computeIfAbsent(k, kk -> new Child(cx, cz));
    }

    public static void pruneStandalone() {
        if (STANDALONE.size() > 20_000) STANDALONE.clear();
    }
}
