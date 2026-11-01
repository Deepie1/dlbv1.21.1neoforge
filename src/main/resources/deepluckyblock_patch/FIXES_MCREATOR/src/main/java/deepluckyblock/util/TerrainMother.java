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

    /** Écart maximal autorisé (en blocs) entre le Y sol de deux chunks STRUCTURE adjacents (règle A1). */
    public static final int MAX_STRUCTURE_CHUNK_STEP_Y = 2;

    /** Marge halo (en chunks) observée autour du damier pour éliminer tout effet de bord sur l'aura neige. */
    public static final int HALO_CHUNKS = 3;

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

    /** Mère active (une seule chaîne terrain à la fois, garantie par TerrainChain / FIFO). */
    public static TerrainMother current() { return CURRENT; }

    /** Installe la mère de la zone (boîte = zone finale tenue par ChunkKeeper, en chunks). */
    public static TerrainMother begin(int gx0, int gz0, int gx1, int gz1) {
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
            m.auraQuartGrid = null;
        }
        CURRENT = null;
    }

    private final int gx0, gz0, gw, gh;
    private final Child[] grid;

    // Halo externe (gx0 - HALO_CHUNKS .. gx1 + HALO_CHUNKS) pour que le bord du chantier
    // connaisse les chunks secs / enneigés situés juste au-delà de ChunkKeeper (zéro mur en ligne 15).
    private final int hx0, hz0, hw, hh;
    private final byte[] haloSnowy; // -1 = inconnu, 0 = false (sec), 1 = true (neige)

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
        this.haloSnowy = new byte[hw * hh];
        java.util.Arrays.fill(this.haloSnowy, (byte) -1);
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

    /** Renseigne la réponse {@code =isSnowy} d'un chunk du halo externe (sans le générer). */
    public void setHaloSnowy(int cx, int cz, boolean snowy) {
        int i = cx - hx0, j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return;
        byte v = (byte) (snowy ? 1 : 0);
        if (haloSnowy[j * hw + i] != v) {
            haloSnowy[j * hw + i] = v;
            auraQuartGrid = null;
        }
    }

    /** T305 : marque un chunk du halo comme glace/eau (exclu de tout dégradé de neige : ni source 1 ni puits 0). */
    public void setHaloIceOrWater(int cx, int cz) {
        int i = cx - hx0, j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return;
        if (haloSnowy[j * hw + i] != 2) {
            haloSnowy[j * hw + i] = 2;
            auraQuartGrid = null;
        }
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
        int clamped = Math.max(1, Math.min(4, r));
        if (snowRadiusChunks != clamped || !snowRadiusLocked) {
            snowRadiusChunks = clamped;
            snowRadiusLocked = true;
            auraQuartGrid = null;
        }
    }

    /**
     * Analyse topologique du damier vue de haut par Mother :
     * choisit une portée de 1 à 3 chunks selon le mélange d'enfants {@code isSnowy=true/false}.
     */
    private void evaluateSnowRadius() {
        if (snowRadiusLocked) return;
        int snowy = 0, total = 0;
        for (Child c : grid) {
            if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE) {
                total++;
                if (c.isSnowy()) snowy++;
            }
        }
        if (total == 0) return;
        float frac = snowy / (float) total;
        if (frac >= 0.92f || frac <= 0.08f) snowRadiusChunks = 1;
        else if (frac >= 0.25f && frac <= 0.75f) snowRadiusChunks = 3;
        else snowRadiusChunks = 2;
    }

    /** État neigeux unifié d'une case (cx, cz) du damier étendu : 2 = glace/eau (neutre), 1 = neige, 0 = terre sèche, -1 = inconnu. */
    private int cellSnowState(int cx, int cz) {
        Child c = child(cx, cz);
        if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE) {
            if (c.isIceOrWaterDominated()) return 2;
            return c.isSnowy() ? 1 : 0;
        }
        int hi = cx - hx0, hj = cz - hz0;
        if (hi >= 0 && hj >= 0 && hi < hw && hj < hh) {
            byte b = haloSnowy[hj * hw + hi];
            if (b >= 0) return b;
        }
        return -1; // inconnu : sera prolongé par le plus proche enfant connu (clamp bord WorldEdit)
    }

    /**
     * Construit en une passe (~0,18 ms) la carte continue d'aura neige au pas QuartPos (4 blocs) :
     * <ol>
     *   <li>Lecture {@code isSnowy()} sur chaque case de TERRE FERME (+ halo de 3 chunks) ;</li>
     *   <li><b>Exclusion stricte de la GLACE et de l'EAU (T305)</b> : toute case glace/eau reçoit un
     *       poids {@code cellWeight = 0} au numérateur ET au dénominateur de la Gaussienne normalisée.
     *       Ainsi : (a) un chunk neigeux bordant un lac gelé reste à {@code 1.0 / 1.0 = 1.0} jusqu'à la
     *       rive sans aucun dégradé avec la glace ; (b) un chunk non-neige entouré par 3 côtés de neige
     *       et 1 côté de glace reçoit le dégradé entrant depuis <b>les 3 bords neigeux à la fois</b>,
     *       sans jamais impliquer le bord de glace ;</li>
     *   <li><b>Convolution gaussienne 2D séparable normalisée</b> ({@code num / den}) sur toute la
     *       grille QuartPos continue.</li>
     * </ol>
     */
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
        int totalLandKnown = 0, totalLandSnowy = 0;

        for (int cj = 0; cj < hh; cj++) {
            int cz = hz0 + cj;
            for (int ci = 0; ci < hw; ci++) {
                int cx = hx0 + ci;
                int st = cellSnowState(cx, cz);
                if (st < 0) {
                    int clampedCx = Math.max(gx0, Math.min(gx0 + gw - 1, cx));
                    int clampedCz = Math.max(gz0, Math.min(gz0 + gh - 1, cz));
                    st = cellSnowState(clampedCx, clampedCz);
                }
                int idx = cj * hw + ci;
                if (st == 2) {
                    // T305 : GLACE / EAU = poids 0 absolu (ni source de neige, ni puits de fonte !).
                    cellPotential[idx] = 0.0f;
                    cellWeight[idx] = 0.0f;
                    continue;
                }
                Child ch = child(cx, cz);
                float lw = (ch != null && ch.present && ch.surfaceYMedian != Integer.MIN_VALUE)
                        ? ch.landWeight() : 1.0f;
                if (lw <= 0.15f) {
                    cellPotential[idx] = 0.0f;
                    cellWeight[idx] = 0.0f;
                    continue;
                }
                if (st < 0) st = 0;
                else {
                    totalLandKnown++;
                    if (st == 1) totalLandSnowy++;
                }
                float val = (st == 1) ? 1.0f : 0.0f;
                if (plan != null && val > 0f) {
                    Float boost = plan.childAuraBoost().get((((long) cx) << 32) | (cz & 0xffffffffL));
                    if (boost != null) {
                        val *= boost;
                    }
                }
                cellPotential[idx] = val;
                cellWeight[idx] = lw;
            }
        }

        if (totalLandSnowy == 0) {
            auraAllZero = true;
            auraAllOne = false;
            float[] empty = new float[0];
            auraQuartGrid = empty;
            return empty;
        }
        if (totalLandSnowy == totalLandKnown && totalLandKnown > 0 && plan == null) {
            auraAllOne = true;
            auraAllZero = false;
            float[] full = new float[0];
            auraQuartGrid = full;
            return full;
        }
        auraAllZero = false;
        auraAllOne = false;

        // Étape 2 (T305) : pont doux uniquement sur couloirs opposés entre deux terres neigeuses,
        // SANS remplissage uniforme de boîte (0.62) afin qu'un chunk entouré de 3 côtés neige
        // (+ 1 côté glace/sec) conserve un vrai dégradé progressif entrant depuis ses 3 bords neigeux !
        final float[] linkedPotential = cellPotential.clone();
        for (int cj = 1; cj < hh - 1; cj++) {
            for (int ci = 1; ci < hw - 1; ci++) {
                int idx = cj * hw + ci;
                if (cellWeight[idx] <= 0.15f || cellPotential[idx] > 0f) continue;
                boolean n = cellWeight[(cj - 1) * hw + ci] > 0.15f && cellPotential[(cj - 1) * hw + ci] > 0.5f;
                boolean s = cellWeight[(cj + 1) * hw + ci] > 0.15f && cellPotential[(cj + 1) * hw + ci] > 0.5f;
                boolean w = cellWeight[cj * hw + (ci - 1)] > 0.15f && cellPotential[cj * hw + (ci - 1)] > 0.5f;
                boolean e = cellWeight[cj * hw + (ci + 1)] > 0.15f && cellPotential[cj * hw + (ci + 1)] > 0.5f;
                int card = (n ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0) + (e ? 1 : 0);
                if (card == 4) {
                    linkedPotential[idx] = 0.42f; // enclave 4/4 terres neigeuses
                } else if ((n && s && !w && !e) || (w && e && !n && !s)) {
                    linkedPotential[idx] = 0.22f; // pont doux entre deux massifs opposés
                }
            }
        }

        // Étape 3 (T306-ECOTONE) : Convolution éparse sur réseau hexagonal jitterisé
        // (KdotJPG ScatteredBiomeBlender, noyau quartique (R^2 - d^2)^2, SANS grille carrée),
        // où toute cellule GLACE / EAU retourne -1f (poids strictement nul : 0 dégradé avec l'ICE,
        // et fondu entrant depuis les 3 côtés neigeux si le 4e côté est de l'ICE !).
        final int qW = hw * 4;
        final int qH = hh * 4;
        final int qx0 = hx0 * 4;
        final int qz0 = hz0 * 4;
        final int rChunks = Math.max(1, Math.min(3, snowRadiusChunks));
        final double radiusBlocks = (rChunks == 1) ? 23.0 : (rChunks == 2 ? 35.0 : 47.0);
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
                    float w = cellWeight[cIdx];
                    if (w <= 0.08f) return -1f; // GLACE / EAU : ignoré (0 influence !)
                    return linkedPotential[cIdx];
                });

        this.auraQx0 = qx0;
        this.auraQz0 = qz0;
        this.auraQw = qW;
        this.auraQh = qH;
        this.auraQuartGrid = out;
        return out;
    }

    /**
     * Densité d'aura de neige continue en {@code (x, z)} :
     * interpolation Hermite C1 sur le champ hexagonal jitterisé {@link BiomeEcotoneSpread},
     * combinée à un domain-warp Simplex 2D isotrope bi-octave (±9 blocs, sans biais d'axe X/Z)
     * et garde stricte anti-glace/eau.
     *
     * <p>Renvoie {@code -1f} uniquement si aucun enfant n'a encore été renseigné.
     */
    private boolean isIceOrWaterCell(int cx, int cz) {
        if (cx >= gx0 && cx < gx0 + gw && cz >= gz0 && cz < gz0 + gh) {
            Child c = grid[(cz - gz0) * gw + (cx - gx0)];
            return c != null && c.present && c.isIceOrWaterDominated();
        }
        int hx0 = gx0 - HALO_CHUNKS, hz0 = gz0 - HALO_CHUNKS;
        int hw = gw + 2 * HALO_CHUNKS, hh = gh + 2 * HALO_CHUNKS;
        if (cx >= hx0 && cx < hx0 + hw && cz >= hz0 && cz < hz0 + hh) {
            return haloSnowy[(cz - hz0) * hw + (cx - hx0)] == 2;
        }
        return false;
    }

    public float snowDensity(int x, int z) {
        awaitAiPlanOnce();
        evaluateSnowRadius();
        float[] field = ensureAuraField();
        if (field == null) return -1f;
        // T305-NO-ICE : la glace/eau = 0 neige strict et 0 degrade avec l'ice !
        if (isIceOrWaterCell(x >> 4, z >> 4)) return 0f;
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
            int wcx = ((int) Math.floor(sampleX + wx)) >> 4;
            int wcz = ((int) Math.floor(sampleZ + wz)) >> 4;
            if (!isIceOrWaterCell(wcx, wcz)) {
                sampleX += wx;
                sampleZ += wz;
            }
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
        if (aura >= 0.85f) return true;
        if (aura <= 0.04f) return false;
        float t = Math.max(0f, Math.min(1f, (aura - 0.04f) / (0.85f - 0.04f)));
        float smoothT = t * t * (3f - 2f * t);
        float env = 4f * smoothT * (1f - smoothT);
        float patch = BiomeEcotoneSpread.warpedPatchSigned(seed ^ 0xD217F00D5EEDL, x, z); // [-0.5, +0.5]
        return (smoothT + 0.62f * env * patch) >= 0.50f;
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
