package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WATER DAM — GEL ET RESTITUTION DE L'EAU (T13).
 *
 * <p>Implementation de la section 27 du guide : « LE PROBLEME DE L'EAU :
 * BARRAGES PARFAITS ET SMOOTH ».
 *
 * <p><b>Le probleme, tel que le guide le decrit.</b> Quand on retaille le
 * terrain autour d'un lac ou d'une riviere, l'eau detecte le changement de
 * contour, reprogramme des ticks de fluide en cascade, « tombe » et se repand.
 * Nos anciennes reponses a ce probleme construisaient de la MATIERE (mottes de
 * terre, berges, barrages en grass_block) : visuellement ca se voyait (« je vois
 * clairement des spheres sur l'eau qui n'ont jamais ete smooth ! de l'eau qui
 * coule »). Le guide propose l'inverse, et c'est mieux :
 *
 * <ol>
 *   <li><b>geler</b> l'eau de la zone (on la retire, en memorisant son etat
 *       exact) ;</li>
 *   <li>travailler le terrain <b>a sec</b> (aucune physique, aucune cascade) ;</li>
 *   <li><b>restituter</b> l'eau intelligemment : la ou le sol existe encore on
 *       remet l'eau (source si elle l'etait, sinon son niveau d'origine), et la
 *       ou le sol a disparu (le smooth a creuse) l'eau <b>suit le nouveau sol</b>
 *       au lieu de rester suspendue.</li>
 * </ol>
 *
 * <p>Aucun bloc nouveau n'est construit : rien a voir avec les anciens barrages
 * en blocs. L'eau est simplement retiree puis remise, dans la bonne
 * configuration.
 *
 * <p><b>Pourquoi ca ne produit plus de cascade</b> : la fenetre
 * {@link TerrainEditClamp} est encore ouverte pendant le degel (le gel et la
 * restitution se font a l'interieur), donc les ticks de bloc et de fluide de la
 * zone sont annules pendant toute l'operation. L'eau est donc remise dans un
 * etat deja stable, sans un seul tick de propagation. Le guide insiste sur ce
 * point (sa section 28 : « il faut EMPECHER le serveur de tick l'eau pendant nos
 * operations ») — ici on y arrive <b>sans mixin</b>, avec l'API vanilla
 * {@code LevelTicks.clearArea}.
 *
 * <p><b>Bornes</b> : la zone scannee s'arrete a {@link #SCAN_DEPTH} blocs sous
 * la surface (les nappes profondes ne sont pas touchees), le nombre de blocs
 * geles est plafonne a {@link #MAX_FROZEN}, et tout se fait en tranches
 * (aucune passe ne tient plus de quelques millisecondes d'un tick).
 */
public final class WaterDam {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Meme jeu de drapeaux que le reste du pipeline terrain
     * (`Structures4Procedure` / `Structures5Procedure`, ou la constante est
     * privee) : pas de mise a jour de voisinage, pas de drop, formes connues.
     * L'eau et les blocs de gravite ne sont donc pas reveilles par nos ecritures.
     */
    private static final int FAST_FLAG = 2 | 16 | 32;

    /** Profondeur scannee sous la surface (l'eau de nos zones est en surface). */
    private static final int SCAN_DEPTH = 48;
    /**
     * Plafond de SECURITE du nombre de blocs geles.
     *
     * <p>HISTORIQUE (defaut corrige le 20/09 au soir, rapporte en jeu) : le
     * plafond etait de 60 000, et une cote reelle en contient beaucoup plus.
     * Le log client est sans ambiguite :
     * {@code [DLB-WATER] eau gelee : 60000 blocs sur 14463 colonnes (PLAFOND
     * ATTEINT : le reste de l'eau n'est pas gele)} -- sur les 30 589 colonnes de
     * la zone, 16 126 n'ont JAMAIS ete gelees. Cette eau restee en place a ete
     * traversee par le remodelage du terrain (elle se retrouve suspendue,
     * decalee, ou en mur a la limite de la partie gelee), puis le degel a repose
     * une eau deja fausse. Le plafond ne sert donc plus a limiter la memoire (un
     * bloc gele coute 12 octets : 1 000 000 de blocs = 12 Mo, transitoires) mais
     * seulement a empecher une derive absurde.
     */
    private static final int MAX_FROZEN = 1_000_000;
    /** Colonnes traitees par tranche (gel). */
    private static final int COLUMNS_PER_SLICE = 192;
    /** Blocs restitues par tranche (degel). */
    private static final int THAW_PER_SLICE = 2_000;
    /** Budget de temps par tranche. */
    private static final long BUDGET_MS = 4L;

    // === T22 : LE DEGEL S'AUTO-ETRANGLE (mesure en jeu du 21/09) ===
    // Deuxieme structure generee sur un monde DEJA modifie : la sonde a mesure
    // des ticks de 13 004 / 12 909 / 13 625 ms pendant « cleanupZone
    // 29568/30589 », puis le chien de garde a tue le serveur (« A single server
    // tick took 60.20 seconds »). Le libelle etait en fait PERIME : la phase
    // reelle etait le DEGEL (33 551 blocs d'eau reposes), juste apres
    // cleanupZone. Chaque setBlock d'eau declenche une verification du moteur
    // de lumiere ; sur une zone deja retaillee deux fois, ces verifications
    // s'accumulent et saturent le thread principal.
    // Correctif : le degel REGARDE le rythme reel du serveur (ecart entre deux
    // tranches) et s'espace tout seul quand les ticks s'allongent, au lieu de
    // demander une tranche par tick quoi qu'il arrive.
    private static long lastSliceMs = 0L;

    /** Ecart minimal impose entre deux tranches quand le serveur est lent (ms). */
    private static final long SLOW_TICK_MS = 200L;
    /** Nombre de tranches a sauter apres une tranche lente. */
    private static int pauseSlices = 0;

    private static final Map<ServerLevel, Frozen> ZONES = new HashMap<>();

    private WaterDam() {}

    /** Etat memorise d'une zone gelee. */
    private static final class Frozen {
        final List<long[]> entries = new ArrayList<>();   // {posLong, stateId}
        final List<BlockState> states = new ArrayList<>();
        // T20 : colonnes qui contenaient de l'eau, et HAUTEUR de la surface
        // d'eau. Indispensable depuis que le gel precede le lissage : sans
        // cette information, la heightmap du smooth voit le FOND de l'ocean a
        // la place de sa surface et draine tout le rivage vers le bas (mesure
        // client du 20/09 : « ecart de sol avant/apres smooth = -20 blocs
        // (63 -> 43) », « sol de l'emprise irregulier apres smooth (median=49
        // contre 63) »).
        final Map<Long, Integer> waterTop = new HashMap<>();
        int columns;
        boolean capped;
    }

    /** Cle de colonne (x, z) -> index utilisable dans une Map. */
    private static long colKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * Vrai si la colonne contenait de l'eau au moment du gel.
     *
     * <p>Utilise par {@code SafeSurface.captureHeightmap} : une colonne gelee
     * doit garder sa SURFACE D'EAU comme hauteur de reference, pas son fond.
     */
    public static boolean frozenColumn(ServerLevel level, int x, int z) {
        Frozen f = ZONES.get(level);
        return f != null && f.waterTop.containsKey(colKey(x, z));
    }

    /** Nombre de colonnes ou de l'eau a ete gelee (0 si aucun gel actif). */
    public static int frozenColumns(ServerLevel level) {
        Frozen f = ZONES.get(level);
        return f == null ? 0 : f.waterTop.size();
    }

    /** Surface d'eau gelee de la colonne (Integer.MIN_VALUE si aucune). */
    public static int frozenColumnTop(ServerLevel level, int x, int z) {
        Frozen f = ZONES.get(level);
        if (f == null) return Integer.MIN_VALUE;
        Integer v = f.waterTop.get(colKey(x, z));
        return v == null ? Integer.MIN_VALUE : v;
    }

    /** Vrai si une zone est actuellement gelee pour ce niveau. */
    public static boolean isFrozen(ServerLevel level) {
        return level != null && ZONES.containsKey(level);
    }

    /** Nombre de blocs d'eau actuellement geles. */
    public static int frozenCount(ServerLevel level) {
        Frozen f = ZONES.get(level);
        return f == null ? 0 : f.entries.size();
    }

    // ------------------------------------------------------------------
    // 1) GEL
    // ------------------------------------------------------------------

    /**
     * Gele l'eau de la zone rectangulaire (en blocs). Appele AVANT toute
     * modification de terrain : c'est le pendant de l'ancien
     * {@code placeWaterDams()}, mais sans construire un seul bloc de barrage.
     *
     * @param onDone appele une fois la zone entierement gelee (le pipeline
     *               terrain continue alors sur une zone seche)
     */
    public static void freeze(ServerLevel level, int x0, int z0, int x1, int z1, Runnable onDone) {
        deepluckyblock.util.DebugLog.setPhase("gel de l'eau");
        Frozen f = new Frozen();
        ZONES.put(level, f);
        List<int[]> cols = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) cols.add(new int[]{x, z});
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-WATER] gel de l'eau sur la zone {}x{} ({} colonnes, profondeur {} sous la surface) -- le terrain sera retaille A SEC",
                x1 - x0 + 1, z1 - z0 + 1, cols.size(), SCAN_DEPTH);
        freezeSlice(level, f, cols, 0, onDone);
    }

    private static void freezeSlice(ServerLevel level, Frozen f, List<int[]> cols, int cursor, Runnable onDone) {
        // T22 : + pauseSlices -> le gel s'espace lui-meme quand le serveur sature.
        final int delay = 1 + Math.max(0, pauseSlices);
        deepluckyblock.procedures.TestProcedure.schedule(level,
                deepluckyblock.procedures.TestProcedure.currentTick(level) + delay,
                () -> {
                    long t0 = System.currentTimeMillis();
                    boolean slow = lastSliceMs != 0L && (t0 - lastSliceMs) > SLOW_TICK_MS;
                    if (slow) pauseSlices = Math.min(10, pauseSlices + 1);
                    else if (pauseSlices > 0) pauseSlices--;
                    lastSliceMs = t0;
                    deepluckyblock.util.DebugLog.setPhase("gel de l'eau " + cursor + "/" + cols.size());
                    BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
                    int i = cursor;
                    int end = Math.min(cursor + COLUMNS_PER_SLICE, cols.size());
                    for (; i < end; i++) {
                        if (System.currentTimeMillis() - t0 > BUDGET_MS) break;
                        int[] col = cols.get(i);
                        int x = col[0], z = col[1];
                        if (!deepluckyblock.util.SafeSurface.isLoadedAt(level, x, z)) continue;
                        int surf = deepluckyblock.util.SafeSurface.height(level, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) + 8;
                        int top = Math.min(surf, level.getMaxBuildHeight() - 1);
                        int bot = Math.max(surf - SCAN_DEPTH, level.getMinBuildHeight());
                        for (int y = top; y >= bot; y--) {
                            BlockState st = level.getBlockState(mut.set(x, y, z));
                            FluidState fs = st.getFluidState();
                            if (!fs.is(FluidTags.WATER)) continue;
                            if (f.entries.size() >= MAX_FROZEN) { f.capped = true; break; }
                            f.entries.add(new long[]{mut.asLong(), 0});
                            f.states.add(st);
                            f.waterTop.merge(colKey(x, z), y, Math::max);   // T20
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(),
                                    FAST_FLAG);
                        }
                        f.columns++;
                        if (f.capped) break;
                    }
                    if (i < cols.size() && !f.capped) {
                        if (pauseSlices > 0) {   // T22 : le serveur est lent, on respire
                            if (pauseSlices >= 5)
                                deepluckyblock.util.DebugLog.structure(
                                        "gel de l'eau : serveur lent (tick > {} ms) -- tranche reportee de {} ticks "
                                                + "({}/{} colonnes)", SLOW_TICK_MS, pauseSlices, i, cols.size());
                        }
                        freezeSlice(level, f, cols, i, onDone);
                        return;
                    }
                    deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-WATER] eau gelee : {} blocs sur {} colonnes{}", f.entries.size(), f.columns,
                            f.capped ? " (PLAFOND ATTEINT : le reste de l'eau n'est pas gele)" : "");
                    if (onDone != null) onDone.run();
                });
    }

    // ------------------------------------------------------------------
    // 2) DEGEL INTELLIGENT
    // ------------------------------------------------------------------

    /**
     * Restitue l'eau gelee. A appeler a la FIN du pipeline terrain, quand le
     * relief est definitif.
     *
     * @param keepOutMin / keepOutMax emprise de la STRUCTURE : l'eau n'y est
     *        jamais remise (elle y a ete remplacee par la construction, et l'eau
     *        du NBT — fontaines, blocs waterlogged — ne doit pas etre ecrasee).
     */
    public static void thaw(ServerLevel level, BlockPos keepOutMin, BlockPos keepOutMax, Runnable onDone) {
        Frozen f = ZONES.remove(level);
        if (f == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Stats st = new Stats();
        pauseSlices = 0;
        lastSliceMs = 0L;
        deepluckyblock.util.DebugLog.setPhase("degel de l'eau 0/" + f.entries.size());
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-WATER] debut du degel : {} blocs d'eau memorises", f.entries.size());
        thawSlice(level, f, 0, st, keepOutMin, keepOutMax, onDone);
    }

    /** Compteurs publies a la fin du degel. */
    private static final class Stats {
        int restored, moved, dropped, skipped;
    }

    private static void thawSlice(ServerLevel level, Frozen f, int cursor, Stats st,
                                  BlockPos koMin, BlockPos koMax, Runnable onDone) {
        // T22 : meme report auto-regule que le gel.
        final int delay = 1 + Math.max(0, pauseSlices);
        deepluckyblock.procedures.TestProcedure.schedule(level,
                deepluckyblock.procedures.TestProcedure.currentTick(level) + delay,
                () -> {
                    long t0 = System.currentTimeMillis();
                    boolean slow = lastSliceMs != 0L && (t0 - lastSliceMs) > SLOW_TICK_MS;
                    if (slow) pauseSlices = Math.min(10, pauseSlices + 1);
                    else if (pauseSlices > 0) pauseSlices--;
                    lastSliceMs = t0;
                    deepluckyblock.util.DebugLog.setPhase("degel de l'eau " + cursor + "/" + f.entries.size()
                            + (pauseSlices > 0 ? " (etrangle x" + pauseSlices + ")" : ""));
                    BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
                    int i = cursor;
                    int end = Math.min(cursor + THAW_PER_SLICE, f.entries.size());
                    for (; i < end; i++) {
                        if (System.currentTimeMillis() - t0 > BUDGET_MS) break;
                        BlockPos pos = BlockPos.of(f.entries.get(i)[0]);
                        BlockState old = f.states.get(i);
                        // Emprise de la structure : on n'y touche pas.
                        if (koMin != null && pos.getX() >= koMin.getX() && pos.getX() <= koMax.getX()
                                && pos.getZ() >= koMin.getZ() && pos.getZ() <= koMax.getZ()
                                && pos.getY() >= koMin.getY() && pos.getY() <= koMax.getY()) {
                            st.skipped++;
                            continue;
                        }
                        BlockState cur = level.getBlockState(pos);
                        // La position a ete occupee par autre chose (terrain retaille,
                        // glace, neige...) : on ne force pas de l'eau dedans.
                        if (!cur.isAir() && !cur.getFluidState().is(FluidTags.WATER)) { st.skipped++; continue; }
                        // === T19 : RESTITUTION EXACTE ===
                        // Avant, l'eau etait "amelioree" a la repose : toute case
                        // dont le bloc du dessous etait plein devenait une SOURCE
                        // (level=0), et l'eau sans support etait deplacee au
                        // nouveau sol. Deux consequences observees en jeu
                        // (captures du 20/09) :
                        //   - une source ne s'ecoule JAMAIS : les 55 391 blocs
                        //     remis en place formaient des MURS d'eau verticaux
                        //     permanents (visibles sur le rivage, en pleine
                        //     falaise, au-dessus des crateres) ;
                        //   - le deplacement au "nouveau sol" reconstruisait de
                        //     l'eau a des endroits ou il n'y en avait jamais eu
                        //     (40 520 blocs dans un seul run).
                        // Desormais : etat d'origine a la position d'origine, ou
                        // rien. L'eau courante reste courante et se stabilise
                        // toute seule (les ticks de fluide ne sont pas concernes
                        // par le gel), les sources restent des sources.
                        level.setBlock(pos, old, FAST_FLAG);
                        st.restored++;
                    }
                    if (i < f.entries.size()) {
                        if (pauseSlices > 0) {
                            if (pauseSlices >= 5)
                                deepluckyblock.util.DebugLog.warn(LOGGER, "[DLB-WATER] degel : le serveur encaisse mal les ecritures "
                                                + "(tick > {} ms) -- tranche reportee de {} ticks ({}/{} blocs d'eau)",
                                        SLOW_TICK_MS, pauseSlices, i, f.entries.size());
                        }
                        thawSlice(level, f, i, st, koMin, koMax, onDone);
                        return;
                    }
                    deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-WATER] degel termine : {} blocs d'eau restitues a leur position EXACTE, {} recouverts par le terrain (abandonnes : l'eau n'existe plus la), {} ignores (emprise de la structure)",
                            st.restored, st.dropped + st.moved, st.skipped);
                    if (onDone != null) onDone.run();
                });
    }
}
