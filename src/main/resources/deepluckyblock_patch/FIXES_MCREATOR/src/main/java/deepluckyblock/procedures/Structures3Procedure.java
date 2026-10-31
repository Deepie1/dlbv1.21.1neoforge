package deepluckyblock.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static deepluckyblock.procedures.StructuresProcedure.*;

public class Structures3Procedure {

    static void buildFairyGarden(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 4, 15);
        int R = 12;

        applyGrassFade15(level, c, 15, random);

        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R) continue;
            BlockPos tp = findFirstSolidBelow(level, c.offset(dx, 0, dz));
            if (tp == null || !isNaturalSurface(level.getBlockState(tp))) continue;
            set(level, tp, random.nextInt(4) == 0 ? Blocks.MOSS_BLOCK : (random.nextInt(6) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK));
        }

        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 10 || dist > R + 0.5) continue;
            BlockPos tp = findFirstSolidBelow(level, c.offset(dx, 0, dz));
            if (tp == null) continue;
            int di = (int) Math.round(dist);
            set(level, tp, di == 12 ? Blocks.MOSSY_STONE_BRICK_SLAB : (di == 11 ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSSY_STONE_BRICKS));
        }

        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int rx = (int) Math.round(Math.cos(a) * 9), rz = (int) Math.round(Math.sin(a) * 9);
            BlockPos base = c.offset(rx, 0, rz);
            set(level, base, Blocks.CHISELED_STONE_BRICKS);
            set(level, base.above(), Blocks.MOSSY_STONE_BRICKS);
            set(level, base.above(2), Blocks.CHISELED_STONE_BRICKS);
            set(level, base.above(3), Blocks.SEA_LANTERN);
            for (Direction dir : HORIZONTALS) placeGlowLichen(level, base.above(2).relative(dir), dir.getOpposite());
            for (Direction dir : HORIZONTALS) if (random.nextInt(2) == 0) placeVineOnFace(level, base.above(1).relative(dir), dir.getOpposite());
        }

        for (int dy = 0; dy < 5; dy++) for (int tx = 0; tx <= 1; tx++) for (int tz = 0; tz <= 1; tz++) set(level, c.offset(tx, dy, tz), Blocks.WARPED_HYPHAE);
        for (int dy = 5; dy < 8; dy++) set(level, c.offset(0, dy, 0), Blocks.WARPED_STEM);
        for (Direction dir : HORIZONTALS) for (int step = 1; step <= 2; step++) set(level, c.offset(dir.getStepX() * step, 7, dir.getStepZ() * step), Blocks.WARPED_STEM);
        for (int dx = -4; dx <= 4; dx++) for (int dy = -1; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 4 || random.nextInt(4) == 0) continue;
            BlockPos leafPos = c.offset(dx, 9 + dy, dz);
            if (level.getBlockState(leafPos).isAir() && isLeafSafeForWater(level, leafPos)) set(level, leafPos, randomLeafState(random));
        }

        for (int i = 0; i < 20; i++) {
            int gx = -3 + random.nextInt(7), gz = -3 + random.nextInt(7);
            BlockPos vineStart = c.offset(gx, 8, gz);
            if (!level.getBlockState(vineStart).isAir()) placeCaveVinesHanging(level, vineStart, 3 + random.nextInt(4), random);
        }

        for (int tx = 0; tx <= 1; tx++) for (int tz = 0; tz <= 1; tz++) set(level, c.offset(tx, -1, tz), Blocks.AMETHYST_BLOCK);

        placeCreatorRewardGlow(level, c.offset(0, 8, 0), random);

        placeFairyRing(level, c, 3, random, new Block[]{Blocks.POPPY, Blocks.DANDELION});
        placeFairyRing(level, c, 5, random, new Block[]{Blocks.BLUE_ORCHID, Blocks.ALLIUM});
        placeFairyRing(level, c, 7, random, new Block[]{Blocks.AZURE_BLUET, Blocks.WHITE_TULIP});
        placeFairyRing(level, c, 9, random, new Block[]{Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER});

        for (int i = 0; i < 60; i++) {
            double t = i / 60.0, angle = t * Math.PI * 3, radius = 10 * (1 - t);
            BlockPos tp = findFirstSolidBelow(level, c.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius)));
            if (tp != null) set(level, tp, random.nextInt(2) == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB);
        }

        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int rx = (int) Math.round(Math.cos(a) * 9), rz = (int) Math.round(Math.sin(a) * 9);
            set(level, c.offset(rx, 5, rz), Blocks.CHAIN);
            set(level, c.offset(rx, 4, rz), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }

        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            spawnMagicParticleEmitter(level, c.getX() + Math.cos(a) * 6 + 0.5, c.getY() + 3, c.getZ() + Math.sin(a) * 6 + 0.5);
        }

        for (int i = 0; i < 12; i++) {
            int mx = -R + random.nextInt(R * 2), mz = -R + random.nextInt(R * 2);
            if (Math.sqrt(mx * mx + mz * mz) > R - 1) continue;
            BlockPos tp = findFirstSolidBelow(level, c.offset(mx, 0, mz));
            if (tp == null || !level.getBlockState(tp).is(Blocks.GRASS_BLOCK)) continue;
            BlockPos above = tp.above();
            if (level.getBlockState(above).isAir()) set(level, above, random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
        }

        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos tp = findFirstSolidBelow(level, c.offset((int) Math.round(Math.cos(a) * 11), 0, (int) Math.round(Math.sin(a) * 11)));
            if (tp != null && level.getBlockState(tp).is(Blocks.GRASS_BLOCK) && level.getBlockState(tp.above()).isAir())
                set(level, tp.above(), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        }

        int mareCx = 0, mareCz = -R - 3;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2.5) continue;
            BlockPos tp = findFirstSolidBelow(level, c.offset(mareCx + dx, 0, mareCz + dz));
            if (tp == null) continue;
            if (d >= 1.8) set(level, tp, Blocks.MOSSY_STONE_BRICKS);
            else { set(level, tp, Blocks.MOSSY_STONE_BRICKS); set(level, tp.above(), Blocks.WATER); }
        }
        set(level, c.offset(mareCx, 1, mareCz), Blocks.LILY_PAD);
        set(level, c.offset(mareCx + 1, 1, mareCz), Blocks.LILY_PAD);

        BlockPos chestPos = c.offset(0, 0, 3);
        placeChestFacing(level, chestPos, Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, chestPos, random);
        addLuckyBlocksToChest(level, chestPos, 3 + random.nextInt(3), random);
        for (Direction dir : HORIZONTALS) setIfAir(level, chestPos.relative(dir).below(), Blocks.PINK_CANDLE);
        placeCreatorRewardGlow(level, c.offset(0, 12, 0), random);

        set(level, c.offset(2, 6, 0), Blocks.BEE_NEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        Bee bee = new Bee(EntityType.BEE, level);
        bee.moveTo(c.getX() + 3.5, c.getY() + 7, c.getZ() + 0.5);
        level.addFreshEntity(bee);

        ensureExteriorLighting(level, c, 15, random);

        for (int i = 0; i < 15; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 5 + random.nextInt(6);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK))
                placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 300, random);
    }

    private static void placeFairyRing(ServerLevel level, BlockPos c, int radius, RandomSource random, Block[] flowerSet) {
        int steps = (int)(radius * 6);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2.0 / steps;
            BlockPos tp = findFirstSolidBelow(level, c.offset((int) Math.round(Math.cos(a) * radius), 0, (int) Math.round(Math.sin(a) * radius)));
            if (tp == null || !level.getBlockState(tp).is(Blocks.GRASS_BLOCK)) continue;
            BlockPos above = tp.above();
            if (level.getBlockState(above).isAir() && random.nextInt(3) != 0)
                set(level, above, flowerSet[random.nextInt(flowerSet.length)]);
        }
    }

    static void buildPodium(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 12);
        int R = 7;

        for (int dy = -10; dy < 0; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++)
            if (Math.sqrt(dx * dx + dz * dz) <= R) set(level, c.offset(dx, dy, dz), randomStoneBrick(random));

        applyGrassFade15(level, c, 15, random);

        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R) continue;
            set(level, c.offset(dx, 0, dz), (int) Math.round(dist) == R ? Blocks.MOSSY_STONE_BRICK_SLAB : (random.nextInt(3) == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE));
        }

        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) if (Math.sqrt(dx * dx + dz * dz) <= 6) set(level, c.offset(dx, 1, dz), Blocks.SMOOTH_STONE);
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) if (Math.sqrt(dx * dx + dz * dz) <= 5) set(level, c.offset(dx, 2, dz), Blocks.SMOOTH_STONE);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4) continue;
            set(level, c.offset(dx, 3, dz), d > 3 ? Blocks.SMOOTH_QUARTZ : Blocks.GOLD_BLOCK);
        }

        for (Direction dir : HORIZONTALS) for (int step = 0; step < 3; step++)
            set(level, c.offset(dir.getStepX() * (6 - step), 1 + step, dir.getStepZ() * (6 - step)),
                    Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dir.getOpposite()));

        set(level, c.offset(0, 4, 0), Blocks.POLISHED_DIORITE);
        for (Direction dir : HORIZONTALS) set(level, c.offset(dir.getStepX(), 4, dir.getStepZ()), Blocks.POLISHED_ANDESITE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (Math.abs(dx) == 1 && Math.abs(dz) == 1) set(level, c.offset(dx, 4, dz), Blocks.POLISHED_GRANITE);

        set(level, c.offset(0, 5, 0), Blocks.SMOOTH_QUARTZ);
        set(level, c.offset(0, 6, 0), Blocks.SMOOTH_QUARTZ);
        set(level, c.offset(0, 7, 0), Blocks.CAMPFIRE);

        for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) set(level, c.offset(dx, 6, dz), Blocks.SOUL_TORCH);
        for (int i = 0; i < 4; i++) set(level, c.offset((int) Math.round(Math.cos(i * Math.PI / 2.0) * 2), 3, (int) Math.round(Math.sin(i * Math.PI / 2.0) * 2)), Blocks.SEA_LANTERN);

        ItemStack[] weaponItems = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.TRIDENT), new ItemStack(Items.NETHERITE_AXE)};
        Direction[] statueDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int i = 0; i < 4; i++) {
            Direction dir = statueDirs[i];
            int stx = dir.getStepX() * 6, stz = dir.getStepZ() * 6;
            set(level, c.offset(stx, 1, stz), Blocks.CHISELED_STONE_BRICKS);
            set(level, c.offset(stx, 2, stz), Blocks.POLISHED_BLACKSTONE);
            set(level, c.offset(stx, 3, stz), Blocks.POLISHED_BLACKSTONE);
            set(level, c.offset(stx, 4, stz), Blocks.PLAYER_HEAD);
            placeItemFrameVertical(level, c.offset(stx + dir.getStepX(), 3, stz + dir.getStepZ()), dir, weaponItems[i]);
        }

        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int tx = (int) Math.round(Math.cos(a) * 8), tz = (int) Math.round(Math.sin(a) * 8);
            BlockPos tp = findFirstSolidBelow(level, c.offset(tx, 0, tz));
            if (tp == null) continue;
            set(level, tp, Blocks.MOSSY_COBBLESTONE);
            set(level, tp.above(), Blocks.OAK_FENCE); set(level, tp.above(2), Blocks.OAK_FENCE);
            set(level, tp.above(3), Blocks.OAK_FENCE); set(level, tp.above(4), Blocks.SOUL_TORCH);
        }

        BlockPos chestPos = c.offset(0, 4, 3);
        placeChestFacing(level, chestPos, Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, chestPos, random);
        addLuckyBlocksToChest(level, chestPos, 4 + random.nextInt(3), random);
        for (Direction dir : new Direction[]{Direction.EAST, Direction.WEST}) set(level, chestPos.relative(dir), Blocks.YELLOW_CANDLE);
        placeCreatorRewardGlow(level, c.offset(0, 9, 0), random);

        ensureExteriorLighting(level, c, 15, random);

        for (Direction dir : statueDirs) {
            int stx = dir.getStepX() * 6, stz = dir.getStepZ() * 6;
            for (int dy = 2; dy <= 3; dy++) for (Direction vd : HORIZONTALS) if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(stx + vd.getStepX(), dy, stz + vd.getStepZ()), vd.getOpposite());
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            BlockPos tp = findFirstSolidBelow(level, c.offset((int) Math.round(Math.cos(a) * 8), 0, (int) Math.round(Math.sin(a) * 8)));
            if (tp != null) placeHangingLeavesSafe(level, tp.above(3), 2, random);
        }
        for (Direction dir : HORIZONTALS) placeCaveVinesHanging(level, c.offset(dir.getStepX(), 6, dir.getStepZ()), 3, random);

        for (int i = 0; i < 15; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 10 + random.nextInt(6);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 150, random);
    }

    static void buildCrystalSanctuary(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 12);
        int R = 6;

        for (int dy = -10; dy < 0; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++)
            if (Math.sqrt(dx * dx + dz * dz) >= R - 0.5 && Math.sqrt(dx * dx + dz * dz) <= R + 0.3)
                set(level, c.offset(dx, dy, dz), randomStoneBrick(random));

        applyGrassFade15(level, c, 15, random);

        for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > R - 0.5) continue;
            set(level, c.offset(dx, 0, dz), random.nextInt(4) == 0 ? Blocks.AMETHYST_BLOCK : (random.nextInt(3) == 0 ? Blocks.BUDDING_AMETHYST : Blocks.CALCITE));
        }

        for (int dy = 1; dy <= 8; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < R - 0.5 || dist > R + 0.3) continue;
            Block wall = dy <= 3 ? (random.nextInt(2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.MOSSY_STONE_BRICKS)
                    : (dy <= 6 ? (random.nextInt(2) == 0 ? Blocks.CALCITE : Blocks.SMOOTH_QUARTZ) : Blocks.AMETHYST_BLOCK);
            set(level, c.offset(dx, dy, dz), wall);
        }

        for (int dy = 1; dy <= 8; dy++) for (Direction dir : HORIZONTALS) set(level, c.offset(dir.getStepX() * R, dy, dir.getStepZ() * R), Blocks.STRIPPED_DARK_OAK_LOG);

        for (int dy = 0; dy <= 3; dy++) {
            int rr = R - dy - 1;
            if (rr < 0) break;
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < rr - 0.5 || d > rr + 0.3) continue;
                set(level, c.offset(dx, 9 + dy, dz), (dy % 2 == 0) ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS);
            }
        }
        set(level, c.offset(0, 12, 0), Blocks.AMETHYST_BLOCK);
        set(level, c.offset(0, 13, 0), Blocks.LARGE_AMETHYST_BUD);
        set(level, c.offset(0, 14, 0), Blocks.AMETHYST_CLUSTER);

        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, c.offset(dx, 1, dz), Blocks.AMETHYST_BLOCK);
        for (int dy = 2; dy <= 4; dy++) set(level, c.offset(0, dy, 0), Blocks.AMETHYST_BLOCK);
        for (Direction dir : HORIZONTALS) set(level, c.offset(dir.getStepX(), 4, dir.getStepZ()), Blocks.LARGE_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, dir));

        placeCreatorRewardGlow(level, c.offset(0, 11, 0), random);

        for (Direction dir : HORIZONTALS) set(level, c.offset(dir.getStepX() * 3, 0, dir.getStepZ() * 3), Blocks.SEA_LANTERN);
        set(level, c.offset(2, 0, 2), Blocks.SEA_LANTERN);
        set(level, c.offset(-2, 0, -2), Blocks.SEA_LANTERN);

        for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2.0 + Math.PI / 4.0; spawnMagicParticleEmitter(level, c.getX() + Math.cos(a) * 3 + 0.5, c.getY() + 2, c.getZ() + Math.sin(a) * 3 + 0.5); }
        spawnMagicParticleEmitter(level, c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5);

        for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) set(level, c.offset(dx, dy, R), Blocks.AIR);
        buildGrandEntrance(level, c.offset(0, 1, R), Direction.SOUTH, 2, random);

        placeChestFacing(level, c.offset(0, 5, 0), Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, c.offset(0, 5, 0), random);
        addLuckyBlocksToChest(level, c.offset(0, 5, 0), 4 + random.nextInt(3), random);
        for (Direction dir : HORIZONTALS) setIfAir(level, c.offset(dir.getStepX(), 5, dir.getStepZ()), Blocks.PURPLE_CANDLE);

        placeChestFacing(level, c.offset(-3, 1, -3), Direction.SOUTH, "minecraft:chests/stronghold_library", random);
        addLuckyBlocksToChest(level, c.offset(-3, 1, -3), 1 + random.nextInt(2), random);

        ensureLighting(level, c.offset(-R + 1, 1, -R + 1), c.offset(R - 1, 8, R - 1), random);
        ensureExteriorLighting(level, c, 15, random);

        for (int dy = 2; dy <= 8; dy++) for (Direction dir : HORIZONTALS) if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(dir.getStepX() * (R + 1), dy, dir.getStepZ() * (R + 1)), dir);
        for (int i = 0; i < 10; i++) { int cx2 = -3 + random.nextInt(7), cz2 = -3 + random.nextInt(7); BlockPos ds = c.offset(cx2, 9, cz2); if (!level.getBlockState(ds).isAir()) placeCaveVinesHanging(level, ds, 2 + random.nextInt(3), random); }
        for (Direction dir : HORIZONTALS) for (int dy = 3; dy <= 6; dy += 2) placeGlowLichen(level, c.offset(dir.getStepX() * (R - 1), dy, dir.getStepZ() * (R - 1)), dir.getOpposite());

        for (int i = 0; i < 15; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * (R + 3 + random.nextInt(5))), 0, (int)(Math.sin(ang) * (R + 3 + random.nextInt(5)))));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 150, random);
    }

    static void buildFloatingIsland(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos ground = o.offset(0, 1, 12);
        int R_ISLAND = 10;
        int Y_ISLAND = 35;

        applyGrassFade15(level, ground, 15, random);

        for (int dx = -R_ISLAND; dx <= R_ISLAND; dx++) for (int dz = -R_ISLAND; dz <= R_ISLAND; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > R_ISLAND) continue;
            set(level, ground.offset(dx, Y_ISLAND, dz), random.nextInt(4) == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK);
        }
        for (int dy = 1; dy <= 3; dy++) { int rr = R_ISLAND - dy - 1; if (rr < 0) break; for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) if (Math.sqrt(dx * dx + dz * dz) <= rr) set(level, ground.offset(dx, Y_ISLAND - dy, dz), random.nextInt(2) == 0 ? Blocks.STONE : Blocks.DIRT); }
        for (int dy = 4; dy <= 7; dy++) { int rr = Math.max(0, R_ISLAND - dy * 2); for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) if (Math.sqrt(dx * dx + dz * dz) <= rr) set(level, ground.offset(dx, Y_ISLAND - dy, dz), random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE); }

        for (int dx = -R_ISLAND; dx <= R_ISLAND; dx++) for (int dz = -R_ISLAND; dz <= R_ISLAND; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < R_ISLAND - 0.5 || d > R_ISLAND) continue;
            if (random.nextInt(3) != 0) set(level, ground.offset(dx, Y_ISLAND + 1, dz), Blocks.MOSSY_COBBLESTONE_WALL);
        }

        set(level, ground.offset(R_ISLAND - 1, Y_ISLAND, 0), Blocks.WATER);
        set(level, ground.offset(R_ISLAND - 1, Y_ISLAND + 1, 0), Blocks.WATER);
        set(level, ground.offset(-R_ISLAND + 1, Y_ISLAND, 0), Blocks.WATER);
        set(level, ground.offset(-R_ISLAND + 1, Y_ISLAND + 1, 0), Blocks.WATER);

        int SX = -2, SZ = -2, SW = 5, SH = 4;
        for (int dy = 1; dy <= SH; dy++) for (int dx = 0; dx < SW; dx++) for (int dz = 0; dz < SW; dz++) {
            boolean corner = (dx == 0 || dx == SW - 1) && (dz == 0 || dz == SW - 1);
            boolean edge = dx == 0 || dx == SW - 1 || dz == 0 || dz == SW - 1;
            if (corner) set(level, ground.offset(SX + dx, Y_ISLAND + dy, SZ + dz), Blocks.STRIPPED_DARK_OAK_LOG);
            else if (edge) set(level, ground.offset(SX + dx, Y_ISLAND + dy, SZ + dz), randomStoneBrick(random));
        }
        buildConeRoof(level, ground.offset(0, Y_ISLAND + SH + 1, 0), 2, random);
        set(level, ground.offset(0, Y_ISLAND + 1, 0), Blocks.POLISHED_BLACKSTONE);
        set(level, ground.offset(0, Y_ISLAND + 2, 0), Blocks.AMETHYST_CLUSTER);
        set(level, ground.offset(0, Y_ISLAND + 1, SZ + SW - 1), Blocks.AIR);
        set(level, ground.offset(0, Y_ISLAND + 2, SZ + SW - 1), Blocks.AIR);

        BlockPos chestPos = ground.offset(0, Y_ISLAND + 1, SZ + 1);
        placeChestFacing(level, chestPos, Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, chestPos, random);
        addLuckyBlocksToChest(level, chestPos, 3 + random.nextInt(3), random);
        for (Direction dir : new Direction[]{Direction.EAST, Direction.WEST}) setIfAir(level, chestPos.relative(dir), Blocks.CANDLE);
        placeCreatorRewardGlow(level, ground.offset(0, Y_ISLAND + SH + 2, 0), random);

        ensureExteriorLighting(level, ground.offset(0, Y_ISLAND, 0), R_ISLAND, random);

        for (int i = 0; i < 3; i++) { double a = i * (Math.PI * 2 / 3); spawnMagicParticleEmitter(level, ground.getX() + Math.cos(a) * 1.5 + 0.5, ground.getY() + Y_ISLAND + 3, ground.getZ() + Math.sin(a) * 1.5 + 0.5); }

        for (int i = 0; i < 3; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 4 + random.nextInt(4);
            int tx = (int) Math.round(Math.cos(ang) * rad), tz = (int) Math.round(Math.sin(ang) * rad);
            if (Math.abs(tx) <= 2 && Math.abs(tz) <= 2) continue;
            BlockPos base = ground.offset(tx, Y_ISLAND + 1, tz);
            if (!level.getBlockState(base).isAir()) continue;
            for (int h = 0; h < 4; h++) set(level, base.above(h), Blocks.OAK_LOG);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 2; dy <= 4; dy++)
                if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 3) <= 3 && level.getBlockState(base.offset(dx, dy, dz)).isAir()) set(level, base.offset(dx, dy, dz), randomLeafState(random));
        }

        for (int dx = -R_ISLAND; dx <= R_ISLAND; dx++) for (int dz = -R_ISLAND; dz <= R_ISLAND; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > R_ISLAND || d < R_ISLAND - 3 || random.nextInt(2) != 0) continue;
            BlockPos bottom = null;
            for (int dy = 0; dy <= 10; dy++) { BlockPos test = ground.offset(dx, Y_ISLAND - dy, dz); if (!level.getBlockState(test).isAir() && level.getBlockState(test.below()).isAir()) { bottom = test; break; } }
            if (bottom != null) placeCaveVinesHanging(level, bottom, 5 + random.nextInt(6), random);
        }

        for (int dx = -R_ISLAND + 2; dx <= R_ISLAND - 2; dx++) for (int dz = -R_ISLAND + 2; dz <= R_ISLAND - 2; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > R_ISLAND - 3 || random.nextInt(3) != 0) continue;
            BlockPos bottom = null;
            for (int dy = 0; dy <= 10; dy++) { BlockPos test = ground.offset(dx, Y_ISLAND - dy, dz); if (!level.getBlockState(test).isAir() && level.getBlockState(test.below()).isAir()) { bottom = test; break; } }
            if (bottom != null) placeHangingLeavesSafe(level, bottom.below(), 4 + random.nextInt(4), random);
        }

        for (int dy = 1; dy <= SH; dy++) for (Direction dir : HORIZONTALS) if (random.nextInt(2) == 0) placeVineOnFace(level, ground.offset(dir.getStepX() * 3, Y_ISLAND + dy, dir.getStepZ() * 3), dir.getOpposite());

        BlockPos groundChest = ground.offset(R_ISLAND + 3, 0, 0);
        BlockPos tp = findFirstSolidBelow(level, groundChest);
        if (tp != null) { set(level, tp, Blocks.MOSSY_COBBLESTONE); placeChestFacing(level, tp.above(), Direction.WEST, "minecraft:chests/shipwreck_treasure", random); addLuckyBlocksToChest(level, tp.above(), 1 + random.nextInt(2), random); }

        for (int i = 0; i < 15; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 6 + random.nextInt(10);
            BlockPos tb = findFirstSolidBelow(level, ground.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, ground, 15, 180, random);
    }
}