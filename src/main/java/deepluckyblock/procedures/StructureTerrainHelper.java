package deepluckyblock.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * StructureTerrainHelper : nettoie et smooth le terrain autour des structures.
 *
 * Phase 1 (preClear) : efface tout dans l'emprise de Y=1 au sommet de la structure.
 *                      Batche via TestProcedure.schedule (evite le freeze).
 *                      Bedrock (Y<=0) JAMAIS touche.
 *
 * Phase 2 (smooth) : apres le paste, cree une pente naturelle en anneau autour
 *                    de l'emprise. Lerp entre baseY (sol de la structure) et le
 *                    terrain naturel. Remplit avec herbe/terre/pierre.
 *                    La structure elle-meme n'est PAS touchee.
 *
 * Integration dans Structures5.generate() :
 *   1) Avant le paste : StructureTerrainHelper.preClear(level, min, max, () -> { /* lancer le paste *\/ });
 *   2) Apres le paste : StructureTerrainHelper.smooth(level, min, max, baseY);
 */
public class StructureTerrainHelper {

    private static final int CLEAR_COLUMNS_PER_TICK = 150;
    private static final int SMOOTH_RING = 7;

    // =========================================================================
    // PHASE 1 : PRE-CLEAR (batche)
    // =========================================================================

    /**
     * Efface tout dans l'emprise de la structure, de Y=1 au sommet.
     * Batche pour eviter le freeze. Appelle onDone quand termine.
     *
     * @param min    Coin min de l'emprise (incluant la rotation)
     * @param max    Coin max de l'emprise
     * @param onDone Callback appele quand le clear est termine (pour lancer le paste)
     */
    public static void preClear(ServerLevel level, BlockPos min, BlockPos max, Runnable onDone) {
        int topY = Math.min(max.getY() + 3, level.getMaxBuildHeight() - 1);
        int bottomY = 1; // Y=0 = bedrock, on ne touche jamais

        // Collecter les colonnes
        List<int[]> cols = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                cols.add(new int[]{x, z});
            }
        }

        int totalBatches = (cols.size() + CLEAR_COLUMNS_PER_TICK - 1) / CLEAR_COLUMNS_PER_TICK;
        if (totalBatches == 0) {
            if (onDone != null) onDone.run();
            return;
        }

        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

        for (int b = 0; b < totalBatches; b++) {
            final int batchIdx = b;
            final int batchTotal = totalBatches;
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + b + 1, () -> {
                int start = batchIdx * CLEAR_COLUMNS_PER_TICK;
                int end = Math.min(start + CLEAR_COLUMNS_PER_TICK, cols.size());

                for (int i = start; i < end; i++) {
                    int cx = cols.get(i)[0];
                    int cz = cols.get(i)[1];
                    for (int y = bottomY; y <= topY; y++) {
                        BlockState s = level.getBlockState(mut.set(cx, y, cz));
                        if (!s.isAir() && !s.is(Blocks.BEDROCK)) {
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }

                // Dernier batch : lancer le callback (paste)
                if (batchIdx == batchTotal - 1 && onDone != null) {
                    onDone.run();
                }
            });
        }
    }

    // =========================================================================
    // PHASE 2 : SMOOTH TERRAIN (apres paste)
    // =========================================================================

    /**
     * Smooth le terrain en anneau autour de l'emprise de la structure.
     * Cree une transition naturelle entre le sol de la structure et le terrain.
     *
     * @param min    Coin min de l'emprise (structure paste)
     * @param max    Coin max de l'emprise
     * @param baseY  Niveau du sol de la structure (Y de la base)
     */
    public static void smooth(ServerLevel level, BlockPos min, BlockPos max, int baseY) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int ring = SMOOTH_RING;
        int minY = 1; // bedrock preserve

        for (int x = min.getX() - ring; x <= max.getX() + ring; x++) {
            for (int z = min.getZ() - ring; z <= max.getZ() + ring; z++) {

                // Skip l'interieur de la structure (ne pas abimer le build)
                if (x >= min.getX() && x <= max.getX() && z >= min.getZ() && z <= max.getZ()) {
                    continue;
                }

                // Distance au bord de l'emprise
                int distX = Math.max(min.getX() - x, 0) + Math.max(x - max.getX(), 0);
                int distZ = Math.max(min.getZ() - z, 0) + Math.max(z - max.getZ(), 0);
                int dist = Math.max(distX, distZ);
                if (dist > ring) continue;

                // Lerp : 0 au bord (match baseY) -> 1 au loin (match terrain naturel)
                float t = (float) dist / ring;
                int natural = deepluckyblock.util.SafeSurface.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int target = Math.max(minY, Math.round(baseY * (1f - t) + natural * t));

                // Hauteur actuelle du terrain
                int current = natural;

                if (current < target) {
                    // Combler : monter le terrain
                    for (int y = current + 1; y <= target; y++) {
                        BlockState fill = (y == target)
                                ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : (y >= target - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                        level.setBlock(mut.set(x, y, z), fill, 2);
                    }
                } else if (current > target) {
                    // Abaisser : retirer le surplus
                    for (int y = target + 1; y <= current; y++) {
                        BlockState s = level.getBlockState(mut.set(x, y, z));
                        if (!s.isAir() && !s.is(Blocks.BEDROCK)) {
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    // Surface en herbe
                    BlockState surface = level.getBlockState(mut.set(x, target, z));
                    if (surface.isAir() || surface.is(Blocks.STONE) || surface.is(Blocks.DIRT)) {
                        level.setBlock(mut, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * Variante : smooth + remplace les blocs flottants par de l'air.
     * Appeler APRES smooth() pour nettoyer les blocs isolés.
     */
    public static void cleanupFloating(ServerLevel level, BlockPos min, BlockPos max) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int ring = SMOOTH_RING;

        for (int x = min.getX() - ring; x <= max.getX() + ring; x++) {
            for (int z = min.getZ() - ring; z <= max.getZ() + ring; z++) {
                // Retirer les blocs flottants (pas de support en dessous)
                int surfaceY = deepluckyblock.util.SafeSurface.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = surfaceY + 1; y <= surfaceY + 8; y++) {
                    BlockState s = level.getBlockState(mut.set(x, y, z));
                    if (!s.isAir() && !s.is(Blocks.BEDROCK)) {
                        // Verifier s'il y a un support solide en dessous
                        BlockState below = level.getBlockState(mut.set(x, y - 1, z));
                        if (below.isAir()) {
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
}
