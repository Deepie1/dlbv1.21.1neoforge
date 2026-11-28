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
 *       conserve les sources continues de neige et le poids eau/terre du damier + halo ;
 *       le spread et la décision de pose appartiennent exclusivement à
 *       {@code StructureTerrainPrep}, pas à cette classe de mesures.</li>
 * </ol>
 */
public final class TerrainMother {

    /** Écart maximal autorisé (en blocs) entre le Y sol de deux chunks STRUCTURE adjacents (règle A1). */
    public static final int MAX_STRUCTURE_CHUNK_STEP_Y = 2;

    /** Marge de stockage monde observée autour du damier. */
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
    private volatile long snowInputRevision = 1L;

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
            snowInputRevision++;
        }
    }

    /** Stocke une fraction terrestre mesurée pour l'écotone eau/glace du halo. */
    public void setHaloLandWeight(int cx, int cz, float weight) {
        int i = cx - hx0, j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return;
        float v = Math.max(0f, Math.min(1f, weight));
        int idx = j * hw + i;
        if (Float.isNaN(haloLandWeight[idx]) || haloLandWeight[idx] != v) {
            haloLandWeight[idx] = v;
            snowInputRevision++;
        }
    }

    /** Signale une nouvelle mesure source au moteur de spread détenu par StructureTerrainPrep. */
    public void markSnowSourceDataChanged() { snowInputRevision++; }

    public long snowInputRevision() { return snowInputRevision; }
    public int snowGridMinChunkX() { return hx0; }
    public int snowGridMinChunkZ() { return hz0; }
    public int snowGridWidth() { return hw; }
    public int snowGridHeight() { return hh; }

    /** Potentiel source continu [0,1], sans créer d'enfant manquant. */
    public float snowSourceAtChunk(int cx, int cz) {
        int i = cx - gx0, j = cz - gz0;
        if (i >= 0 && j >= 0 && i < gw && j < gh) {
            Child c = grid[j * gw + i];
            if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE)
                return Math.max(0f, Math.min(1f, c.snowyRatio));
        }
        i = cx - hx0; j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return 0f;
        float value = haloSnowPotential[j * hw + i];
        return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }

    /** Poids terrestre continu [0,1], mesuré sans veto global par chunk. */
    public float snowLandWeightAtChunk(int cx, int cz) {
        int i = cx - gx0, j = cz - gz0;
        if (i >= 0 && j >= 0 && i < gw && j < gh) {
            Child c = grid[j * gw + i];
            if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE)
                return c.landWeight();
        }
        i = cx - hx0; j = cz - hz0;
        if (i < 0 || j < 0 || i >= hw || j >= hh) return 1f;
        float value = haloLandWeight[j * hw + i];
        return Float.isNaN(value) ? 1f : Math.max(0f, Math.min(1f, value));
    }

    public int knownSnowSourceChunks() {
        int known = 0;
        for (Child c : grid)
            if (c != null && c.present && c.surfaceYMedian != Integer.MIN_VALUE) known++;
        return known;
    }

    /** True si une source neige terrestre, y compris dans le halo, peut alimenter le spread. */
    public boolean hasSnowSources() {
        for (int z = hz0; z < hz0 + hh; z++) {
            for (int x = hx0; x < hx0 + hw; x++) {
                if (snowLandWeightAtChunk(x, z) > 0f && snowSourceAtChunk(x, z) > 0f) return true;
            }
        }
        return false;
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
            snowInputRevision++;
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
    private int snowRadiusChunks = HALO_CHUNKS;

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
            }
        } catch (java.util.concurrent.TimeoutException te) {
            aiFuture.cancel(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Analyse topologique du damier vue de haut par Mother :
     * choisit une portée de 1 à 3 chunks selon le mélange d'enfants {@code isSnowy=true/false}.
     */
    private void evaluateSnowRadius() {
        // Rayon d'audit fixe; le moteur de spread applique son rayon physique dans TerrainPrep.
        snowRadiusChunks = HALO_CHUNKS;
    }

    /** Enfants de contour réclamant un comblement, pour la passe de terrain A9. */
    public java.util.List<Child> contourRefillTargets() {
        java.util.List<Child> out = new java.util.ArrayList<>();
        for (Child c : grid) {
            if (c != null && c.present && !c.structureCore && c.needsRefill()) out.add(c);
        }
        return out;
    }

    // Cache d'enfants autonomes hors chaîne (sélection A1).
    private static final Map<Long, Child> STANDALONE = new HashMap<>();

    public static Child standaloneChild(int cx, int cz) {
        long key = (((long) cx) << 32) | (cz & 0xffffffffL);
        return STANDALONE.computeIfAbsent(key, ignored -> new Child(cx, cz));
    }

    public static void pruneStandalone() {
        if (STANDALONE.size() > 20_000) STANDALONE.clear();
    }
}
