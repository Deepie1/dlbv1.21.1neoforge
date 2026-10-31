package deepluckyblock.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import static deepluckyblock.procedures.StructuresProcedure.*;

public class Structures1Procedure {

    static void buildGrandManoir(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos so = o.offset(-20, 1, -2);
        int MXN = 10, MXX = 30, MZN = 10, MZX = 30;
        int MW = MXX - MXN + 1, MD = MZX - MZN + 1;
        int AXN = 3, AXX = 9, AZN = 6, AZX = 24;
        int H = 30;
        int cx = (MXN + MXX) / 2, cz = (MZN + MZX) / 2;

        for (int dy = -10; dy < 0; dy++) {
            for (int x = MXN; x <= MXX; x++) for (int z = MZN; z <= MZX; z++)
                if (x == MXN || x == MXX || z == MZN || z == MZX)
                    set(level, so.offset(x, dy, z), randomStoneBrick(random));
            for (int x = AXN; x <= AXX; x++) for (int z = AZN; z <= AZX; z++)
                if (x == AXN || x == AXX || z == AZN || z == AZX)
                    set(level, so.offset(x, dy, z), randomStoneBrick(random));
        }

        applyGrassFade15(level, so.offset(cx, 0, cz), 15, random);

        for (int y = 0; y <= H; y++) {
            for (int x = MXN; x <= MXX; x++) for (int z = MZN; z <= MZX; z++) {
                boolean corner = (x == MXN || x == MXX) && (z == MZN || z == MZX);
                boolean edge = x == MXN || x == MXX || z == MZN || z == MZX;
                if (corner) set(level, so.offset(x, y, z), Blocks.STRIPPED_DARK_OAK_LOG);
                else if (edge) {
                    set(level, so.offset(x, y, z), randomStoneBrick(random));
                    if (y % 4 == 2 && (x % 5 == 0 || z % 5 == 0) && random.nextInt(3) == 0) {
                        Direction facing = x == MXN ? Direction.EAST : x == MXX ? Direction.WEST : z == MZN ? Direction.SOUTH : Direction.NORTH;
                        BlockPos btnPos = so.offset(x + facing.getStepX(), y, z + facing.getStepZ());
                        if (level.getBlockState(btnPos).isAir())
                            set(level, btnPos, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
                    }
                }
            }
            if (y <= H) {
                set(level, so.offset(cx, y, MZN - 1), Blocks.STRIPPED_DARK_OAK_LOG);
                set(level, so.offset(cx, y, MZX + 1), Blocks.STRIPPED_DARK_OAK_LOG);
                set(level, so.offset(MXN - 1, y, cz), Blocks.STRIPPED_DARK_OAK_LOG);
                set(level, so.offset(MXX + 1, y, cz), Blocks.STRIPPED_DARK_OAK_LOG);
            }
        }

        for (int y = 0; y <= H; y++) {
            for (int x = AXN; x <= AXX; x++) for (int z = AZN; z <= AZX; z++) {
                boolean corner = (x == AXN || x == AXX) && (z == AZN || z == AZX);
                boolean edge = x == AXN || x == AXX || z == AZN || z == AZX;
                if (corner) set(level, so.offset(x, y, z), Blocks.STRIPPED_DARK_OAK_LOG);
                else if (edge) set(level, so.offset(x, y, z), random.nextInt(5) == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.WARPED_PLANKS);
            }
        }

        clearBox(level, so.offset(MXN + 1, 1, MZN + 1), so.offset(MXX - 1, H, MZX - 1));
        clearBox(level, so.offset(AXN + 1, 1, AZN + 1), so.offset(AXX - 1, H, AZX - 1));

        for (int y = 1; y <= 3; y++) for (int z = AZN + 3; z <= AZN + 6; z++) {
            set(level, so.offset(MXN, y, z), Blocks.AIR);
            set(level, so.offset(AXX, y, z), Blocks.AIR);
            set(level, so.offset(AXX + 1, y, z), Blocks.AIR);
        }
        for (int z = AZN + 3; z <= AZN + 6; z++) {
            set(level, so.offset(AXX + 1, 0, z), Blocks.SPRUCE_PLANKS);
            set(level, so.offset(AXX + 1, 4, z), Blocks.SPRUCE_PLANKS);
        }

        int f1 = 10, f2 = 20;
        for (int x = MXN + 1; x <= MXX - 1; x++) for (int z = MZN + 1; z <= MZX - 1; z++) {
            set(level, so.offset(x, 0, z), random.nextInt(8) == 0 ? Blocks.MOSS_BLOCK : (random.nextInt(4) == 0 ? Blocks.WARPED_PLANKS : Blocks.SPRUCE_PLANKS));
            if (!(x >= cx - 1 && x <= cx + 1 && z >= cz - 1 && z <= cz + 1)) {
                set(level, so.offset(x, f1, z), Blocks.SPRUCE_PLANKS);
                set(level, so.offset(x, f2, z), Blocks.SPRUCE_PLANKS);
            }
        }
        for (int x = AXN + 1; x <= AXX - 1; x++) for (int z = AZN + 1; z <= AZX - 1; z++)
            set(level, so.offset(x, 0, z), random.nextInt(3) == 0 ? Blocks.WARPED_PLANKS : Blocks.SPRUCE_PLANKS);

        for (int fl : new int[]{f1, f2}) {
            for (int x = MXN; x <= MXX; x++) { set(level, so.offset(x, fl, MZN), Blocks.STRIPPED_DARK_OAK_LOG); set(level, so.offset(x, fl, MZX), Blocks.STRIPPED_DARK_OAK_LOG); }
            for (int z = MZN; z <= MZX; z++) { set(level, so.offset(MXN, fl, z), Blocks.STRIPPED_DARK_OAK_LOG); set(level, so.offset(MXX, fl, z), Blocks.STRIPPED_DARK_OAK_LOG); }
        }

        for (int i = 0; i < f1 - 1; i++) {
            double a = i * Math.PI / 2;
            set(level, so.offset(cx + (int) Math.round(Math.cos(a)), 1 + i, cz + (int) Math.round(Math.sin(a))),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, HORIZONTALS[i % 4]));
        }
        for (int i = 0; i < f2 - f1 - 1; i++) {
            double a = i * Math.PI / 2;
            set(level, so.offset(cx + (int) Math.round(Math.cos(a)), f1 + 1 + i, cz + (int) Math.round(Math.sin(a))),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, HORIZONTALS[i % 4]));
        }

        for (int wy : new int[]{3, 13, 23}) {
            for (int x = MXN + 3; x < MXX - 2; x += 4) {
                set(level, so.offset(x, wy, MZN), Blocks.GLASS); set(level, so.offset(x, wy + 1, MZN), Blocks.GLASS);
                set(level, so.offset(x, wy, MZX), Blocks.GLASS); set(level, so.offset(x, wy + 1, MZX), Blocks.GLASS);
            }
            for (int z = MZN + 3; z < MZX - 2; z += 4) {
                set(level, so.offset(MXN, wy, z), Blocks.GLASS); set(level, so.offset(MXN, wy + 1, z), Blocks.GLASS);
                set(level, so.offset(MXX, wy, z), Blocks.GLASS); set(level, so.offset(MXX, wy + 1, z), Blocks.GLASS);
            }
        }
        for (int z = AZN + 3; z < AZX - 2; z += 4) { set(level, so.offset(AXN, 3, z), Blocks.GLASS); set(level, so.offset(AXN, 4, z), Blocks.GLASS); }

        for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) set(level, so.offset(cx + dx, dy, MZX), Blocks.AIR);
        placeDoor(level, so.offset(cx - 1, 1, MZX), Direction.SOUTH);
        placeDoor(level, so.offset(cx, 1, MZX), Direction.SOUTH);
        placeDoor(level, so.offset(cx + 1, 1, MZX), Direction.SOUTH);
        buildGrandEntrance(level, so.offset(cx, 1, MZX), Direction.SOUTH, 3, random);

        set(level, so.offset(AXN, 1, AZN + 9), Blocks.AIR); set(level, so.offset(AXN, 2, AZN + 9), Blocks.AIR);
        set(level, so.offset(AXN, 1, AZN + 10), Blocks.AIR); set(level, so.offset(AXN, 2, AZN + 10), Blocks.AIR);
        placeDoor(level, so.offset(AXN, 1, AZN + 9), Direction.WEST);
        placeDoor(level, so.offset(AXN, 1, AZN + 10), Direction.WEST);

        for (int y = 1; y <= H + 15; y++) for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
            set(level, so.offset(MXN + 2 + dx, y, MZN + 2 + dz), Blocks.COBBLESTONE);
        clearBox(level, so.offset(MXN + 2, 1, MZN + 2), so.offset(MXN + 3, 2, MZN + 3));
        set(level, so.offset(MXN + 2, 1, MZN + 2), Blocks.CAMPFIRE);
        set(level, so.offset(MXN + 3, 1, MZN + 3), Blocks.CAMPFIRE);

        for (int dx = -2; dx <= 2; dx++) { set(level, so.offset(cx + dx, 1, cz + 5), Blocks.SPRUCE_FENCE); set(level, so.offset(cx + dx, 2, cz + 5), Blocks.SPRUCE_PRESSURE_PLATE); }
        set(level, so.offset(cx - 3, 1, cz + 5), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        set(level, so.offset(cx + 3, 1, cz + 5), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));

        set(level, so.offset(MXN + 3, f1 + 1, MZX - 3), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT));
        set(level, so.offset(MXN + 4, f1 + 1, MZX - 3), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD));

        for (int x = MXN + 3; x <= MXX - 3; x += 4) for (int dy = 1; dy <= 3; dy++) {
            set(level, so.offset(x, f2 + dy, MZN + 2), Blocks.BOOKSHELF); set(level, so.offset(x, f2 + dy, MZX - 2), Blocks.BOOKSHELF);
        }
        set(level, so.offset(cx, f2 + 1, cz + 3), Blocks.LECTERN);

        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, so.offset(cx + dx, f2 + 1, cz - 3 + dz), Blocks.POLISHED_ANDESITE);
        set(level, so.offset(cx, f2 + 2, cz - 3), Blocks.CHISELED_STONE_BRICKS);
        placeChestFacing(level, so.offset(cx, f2 + 3, cz - 3), Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, so.offset(cx, f2 + 3, cz - 3), random);
        addLuckyBlocksToChest(level, so.offset(cx, f2 + 3, cz - 3), 4 + random.nextInt(5), random);
        for (int dx : new int[]{-1, 1}) set(level, so.offset(cx + dx, f2 + 2, cz - 3), Blocks.CANDLE);
        set(level, so.offset(cx, f2 + 2, cz - 4), Blocks.CANDLE); set(level, so.offset(cx, f2 + 2, cz - 2), Blocks.CANDLE);
        placeCreatorRewardGlow(level, so.offset(cx, f2 + 5, cz - 3), random);

        placeChestFacing(level, so.offset(MXX - 2, 1, MZN + 2), Direction.WEST, "minecraft:chests/stronghold_library", random);
        addLuckyBlocksToChest(level, so.offset(MXX - 2, 1, MZN + 2), 2 + random.nextInt(3), random);
        placeChestFacing(level, so.offset(MXX - 2, f1 + 1, MZX - 2), Direction.WEST, "minecraft:chests/simple_dungeon", random);
        addLuckyBlocksToChest(level, so.offset(MXX - 2, f1 + 1, MZX - 2), 1 + random.nextInt(2), random);
        placeChestFacing(level, so.offset(AXN + 2, 1, AZN + 2), Direction.EAST, "minecraft:chests/simple_dungeon", random);
        addLuckyBlocksToChest(level, so.offset(AXN + 2, 1, AZN + 2), 1 + random.nextInt(2), random);

        buildGableRoof(level, so.offset(MXN, H + 1, MZN), MW, MD, random);
        buildGableRoof(level, so.offset(AXN, H + 1, AZN), AXX - AXN + 1, AZX - AZN + 1, random);

        ensureLighting(level, so.offset(MXN + 1, 1, MZN + 1), so.offset(MXX - 1, H, MZX - 1), random);
        ensureLighting(level, so.offset(AXN + 1, 1, AZN + 1), so.offset(AXX - 1, H, AZX - 1), random);
        ensureExteriorLighting(level, so.offset(cx, 0, cz), 15, random);

        for (int y = 2; y < H - 2; y++) {
            for (int x = MXN; x <= MXX; x++) { if (random.nextInt(2) == 0) placeVineOnFace(level, so.offset(x, y, MZN - 1), Direction.SOUTH); if (random.nextInt(2) == 0) placeVineOnFace(level, so.offset(x, y, MZX + 1), Direction.NORTH); }
            for (int z = MZN; z <= MZX; z++) { if (random.nextInt(2) == 0) placeVineOnFace(level, so.offset(MXN - 1, y, z), Direction.EAST); if (random.nextInt(2) == 0) placeVineOnFace(level, so.offset(MXX + 1, y, z), Direction.WEST); }
        }
        for (int x = MXN; x <= MXX; x += 2) { if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, so.offset(x, H, MZN), 4, random); if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, so.offset(x, H, MZX), 4, random); }
        for (int z = MZN; z <= MZX; z += 2) { if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, so.offset(MXN, H, z), 4, random); if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, so.offset(MXX, H, z), 4, random); }
        for (int i = 0; i < 10; i++) { int gx = MXN + 2 + random.nextInt(MW - 4), gz = MZN + 2 + random.nextInt(MD - 4); placeCaveVinesHanging(level, so.offset(gx, random.nextBoolean() ? f2 : f1, gz), 2 + random.nextInt(3), random); }
        for (int y = 3; y < H; y += 5) for (Direction dir : HORIZONTALS) placeGlowLichen(level, so.offset(cx + dir.getStepX() * (MW / 2 - 2), y, cz + dir.getStepZ() * (MD / 2 - 2)), dir.getOpposite());
        for (int y = 3; y < H; y += 6) for (int x = MXN + 3; x < MXX - 2; x += 5) {
            set(level, so.offset(x, y, MZN + 1), Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            set(level, so.offset(x, y, MZX - 1), Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }
        for (int i = 0; i < 30; i++) { double ang = random.nextDouble() * Math.PI * 2; int rad = 10 + random.nextInt(5); BlockPos tb = findFirstSolidBelow(level, so.offset(cx + (int)(Math.cos(ang) * rad), 0, cz + (int)(Math.sin(ang) * rad))); if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random); }
        applyBonemeal(level, so.offset(cx, 0, cz), 15, 200, random);
    }

    static void buildMageTower(ServerLevel level, BlockPos o, RandomSource random) {
        int R = 20, H = 30;
        BlockPos c = o.offset(0, 1, R + 8);
        for (int dy = -10; dy <= 0; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) { double dist = Math.sqrt(dx * dx + dz * dz); if (dist >= R - 0.5 && dist <= R + 0.3) set(level, c.offset(dx, dy, dz), randomStoneBrick(random)); }
        applyGrassFade15(level, c, 15, random);
        for (int dy = 1; dy <= H; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) { double dist = Math.sqrt(dx * dx + dz * dz); if (dist >= R - 0.5 && dist <= R + 0.3) set(level, c.offset(dx, dy, dz), randomStoneBrick(random)); }
        for (int dy = 1; dy < H; dy++) for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) if (Math.sqrt(dx * dx + dz * dz) < R - 0.5) set(level, c.offset(dx, dy, dz), Blocks.AIR);
        for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) if (Math.sqrt(dx * dx + dz * dz) < R - 0.5) set(level, c.offset(dx, 0, dz), random.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.POLISHED_DEEPSLATE);
        set(level, c, Blocks.AMETHYST_BLOCK);
        for (int fy : new int[]{8, 16, 24}) for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) { if (Math.sqrt(dx * dx + dz * dz) >= R - 0.5 || (Math.abs(dx) <= 2 && Math.abs(dz) <= 2)) continue; set(level, c.offset(dx, fy, dz), Blocks.SPRUCE_PLANKS); }
        buildFullSpiralStairs(level, c.offset(0, 1, 0), R - 3, H - 2);
        for (int layer = 0; layer < 4; layer++) { int wy = 4 + layer * 7; for (Direction dir : HORIZONTALS) { set(level, c.offset(dir.getStepX() * R, wy, dir.getStepZ() * R), Blocks.GLASS); set(level, c.offset(dir.getStepX() * R, wy + 1, dir.getStepZ() * R), Blocks.GLASS); set(level, c.offset(dir.getStepX() * R, wy + 2, dir.getStepZ() * R), Blocks.PURPLE_STAINED_GLASS); } }
        for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) set(level, c.offset(dx, dy, -R), Blocks.AIR);
        buildGrandEntrance(level, c.offset(0, 1, -R), Direction.NORTH, 3, random);
        int altarY = H - 3;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.sqrt(dx * dx + dz * dz) <= 3.5) set(level, c.offset(dx, altarY, dz), Blocks.POLISHED_BLACKSTONE);
        set(level, c.offset(0, altarY + 1, 0), Blocks.ENCHANTING_TABLE);
        for (Direction d : HORIZONTALS) { set(level, c.offset(d.getStepX() * 3, altarY + 1, d.getStepZ() * 3), Blocks.BOOKSHELF); set(level, c.offset(d.getStepX() * 3, altarY + 2, d.getStepZ() * 3), Blocks.BOOKSHELF); }
        placeChestFacing(level, c.offset(0, altarY + 1, -3), Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, c.offset(0, altarY + 1, -3), random);
        addLuckyBlocksToChest(level, c.offset(0, altarY + 1, -3), 4 + random.nextInt(6), random);
        placeCreatorRewardGlow(level, c.offset(0, altarY + 3, -3), random);
        buildConeRoof(level, c.offset(0, H + 1, 0), R - 2, random);
        ensureLighting(level, c.offset(-R + 1, 1, -R + 1), c.offset(R - 1, H, R - 1), random);
        ensureExteriorLighting(level, c, 15, random);
        for (int fy : new int[]{8, 16, 24}) for (int i = 0; i < 15; i++) { int gx = -R + 2 + random.nextInt(R * 2 - 3), gz = -R + 2 + random.nextInt(R * 2 - 3); if (Math.sqrt(gx * gx + gz * gz) >= R - 1.5 || (Math.abs(gx) <= 3 && Math.abs(gz) <= 3)) continue; placeCaveVinesHanging(level, c.offset(gx, fy, gz), 2 + random.nextInt(4), random); }
        for (int i = 0; i < 30; i++) { double a = i * Math.PI / 15.0; placeHangingLeavesSafe(level, c.offset((int) Math.round(Math.cos(a) * R), H, (int) Math.round(Math.sin(a) * R)), 3, random); }
        for (int i = 0; i < 20; i++) { double ang = random.nextDouble() * Math.PI * 2; int rad = R + 3 + random.nextInt(6); BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad))); if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random); }
        applyBonemeal(level, c, 15, 200, random);
    }

    static void buildEgyptianPyramid(ServerLevel level, BlockPos o, RandomSource random) {
        int base = 41, layers = 11;
        BlockPos start = o.offset(-base / 2, 1, 30);
        for (int dy = -10; dy <= 0; dy++) for (int dx = 0; dx < base; dx++) for (int dz = 0; dz < base; dz++) if (dy == 0 || dx == 0 || dz == 0 || dx == base - 1 || dz == base - 1) set(level, start.offset(dx, dy, dz), random.nextInt(3) == 0 ? Blocks.SANDSTONE : Blocks.SAND);
        applySandFade15(level, start.offset(base / 2, 0, base / 2), 15, random);
        for (int layer = 0; layer < layers; layer++) { int size = base - layer * 4; for (int dx = 0; dx < size; dx++) for (int dz = 0; dz < size; dz++) { Block b = layer < 3 ? Blocks.CUT_SANDSTONE : (layer < 7 ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE); if (random.nextInt(10) == 0) b = Blocks.MOSSY_COBBLESTONE; else if (random.nextInt(15) == 0) b = Blocks.CHISELED_SANDSTONE; set(level, start.offset(dx + layer * 2, layer, dz + layer * 2), b); } }
        for (int dy = layers - 1; dy >= 1; dy--) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(level, start.offset(base / 2 + dx, dy, base / 2 + dz), Blocks.AIR);
        int cW = 15, cx0 = base / 2 - cW / 2, cz0 = base / 2 - cW / 2;
        clearBox(level, start.offset(cx0 + 1, 1, cz0 + 1), start.offset(cx0 + cW - 2, 6, cz0 + cW - 2));
        for (int dy = 1; dy <= 6; dy++) { for (int dx = 0; dx < cW; dx++) { set(level, start.offset(cx0 + dx, dy, cz0), Blocks.CHISELED_SANDSTONE); set(level, start.offset(cx0 + dx, dy, cz0 + cW - 1), Blocks.CHISELED_SANDSTONE); } for (int dz = 1; dz < cW - 1; dz++) { set(level, start.offset(cx0, dy, cz0 + dz), Blocks.CHISELED_SANDSTONE); set(level, start.offset(cx0 + cW - 1, dy, cz0 + dz), Blocks.CHISELED_SANDSTONE); } }
        for (int dx = 1; dx < cW - 1; dx++) for (int dz = 1; dz < cW - 1; dz++) { Block floor = ((dx + dz) & 1) == 0 ? Blocks.ORANGE_CONCRETE : Blocks.YELLOW_CONCRETE; if (Math.abs(dx - cW / 2) <= 1 && Math.abs(dz - cW / 2) <= 1) floor = Blocks.GOLD_BLOCK; set(level, start.offset(cx0 + dx, 0, cz0 + dz), floor); }
        for (int cn = 0; cn < 4; cn++) { int sx = (cn % 2 == 0) ? 2 : cW - 3, sz = (cn < 2) ? 2 : cW - 3; BlockPos sp = start.offset(cx0 + sx, 1, cz0 + sz); set(level, sp, Blocks.SPAWNER); if (level.getBlockEntity(sp) instanceof SpawnerBlockEntity spawner) spawner.getSpawner().setEntityId(EntityType.ZOMBIE, level, random, sp); }
        placeCreatorRewardGlow(level, start.offset(base / 2, 1, base / 2), random);
        placeChestFacing(level, start.offset(cx0 + 2, 1, cz0 + cW / 2), Direction.EAST, "minecraft:chests/desert_pyramid", random);
        addLuckyBlocksToChest(level, start.offset(cx0 + 2, 1, cz0 + cW / 2), 3 + random.nextInt(5), random);
        addCreatorRewardToChest(level, start.offset(cx0 + 2, 1, cz0 + cW / 2), random);
        placeChestFacing(level, start.offset(cx0 + cW - 3, 1, cz0 + cW / 2), Direction.WEST, "minecraft:chests/end_city_treasure", random);
        addLuckyBlocksToChest(level, start.offset(cx0 + cW - 3, 1, cz0 + cW / 2), 2 + random.nextInt(4), random);
        set(level, start.offset(base / 2, 3, base / 2 - 2), Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, start.offset(base / 2, 3, base / 2 + 2), Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        for (int layer = 2; layer < layers; layer++) for (int i = 0; i < 4; i++) placeHangingLeavesSafe(level, start.offset(layer * 2 + random.nextInt(Math.max(1, base - layer * 4)), layer, layer * 2 + random.nextInt(Math.max(1, base - layer * 4))), 2, random);
        for (int i = 0; i < 25; i++) { double ang = random.nextDouble() * Math.PI * 2; int rad = base / 2 + 8 + random.nextInt(8); BlockPos tb = findFirstSolidBelow(level, start.offset(base / 2 + (int)(Math.cos(ang) * rad), 0, base / 2 + (int)(Math.sin(ang) * rad))); if (tb != null && !level.getBlockState(tb).is(Blocks.SAND) && isNaturalSurface(level.getBlockState(tb))) placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random); }
    }

    static void buildFountain(ServerLevel level, BlockPos o, RandomSource random) {
        int R_OUT = 8, R_IN = 3, R_RING = 5, PODIUM_H = 2;
        BlockPos center = o.offset(0, 2, R_OUT + 7);

        applyGrassFade15(level, center, 15, random);

        for (int dx = -R_OUT; dx <= R_OUT; dx++) for (int dz = -R_OUT; dz <= R_OUT; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R_OUT || dist <= R_RING) continue;
            int di = (int) Math.round(dist);
            Block fb = (di == R_RING + 1 || di == R_OUT) ? Blocks.DARK_PRISMARINE : (((di + R_RING) % 2 == 0) ? Blocks.PRISMARINE_BRICKS : Blocks.PRISMARINE);
            set(level, center.offset(dx, -1, dz), fb);
            set(level, center.offset(dx, 0, dz), Blocks.AIR);
        }

        for (int dx = -R_OUT - 4; dx <= R_OUT + 4; dx++) for (int dz = -R_OUT - 4; dz <= R_OUT + 4; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R_OUT && dist <= R_OUT + 1.0) { set(level, center.offset(dx, -1, dz), randomStoneBrick(random)); set(level, center.offset(dx, 0, dz), randomStoneBrick(random)); }
            else if (dist > R_OUT + 1.0 && dist <= R_OUT + 1.8) { set(level, center.offset(dx, -1, dz), randomStoneBrick(random)); set(level, center.offset(dx, 0, dz), randomStoneBrick(random)); set(level, center.offset(dx, 1, dz), randomStoneBrickWall(random)); }
            else if (dist > R_OUT + 1.8 && dist <= R_OUT + 2.6) set(level, center.offset(dx, 0, dz), randomStoneBrickSlab(random));
        }

        for (int dx = -R_RING; dx <= R_RING; dx++) for (int dz = -R_RING; dz <= R_RING; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R_RING || dist <= R_IN) continue;
            set(level, center.offset(dx, 0, dz), ((int) Math.round(dist) == R_IN + 1) ? Blocks.MOSSY_COBBLESTONE : (((int) Math.round(dist) & 1) == 0 ? randomStoneBrick(random) : Blocks.MOSSY_COBBLESTONE));
            set(level, center.offset(dx, 1, dz), Blocks.AIR);
        }

        for (int dx = -R_OUT + 1; dx <= R_OUT - 1; dx++) for (int dz = -R_OUT + 1; dz <= R_OUT - 1; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist >= R_OUT || dist <= R_RING) continue;
            set(level, center.offset(dx, 0, dz), Blocks.WATER);
        }

        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            int lx = (int) Math.round(Math.cos(a) * (R_OUT - 2));
            int lz = (int) Math.round(Math.sin(a) * (R_OUT - 2));
            BlockPos lightPos = center.offset(lx, -1, lz);
            set(level, lightPos, Blocks.SEA_LANTERN);
        }

        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            double px = center.getX() + Math.cos(a) * (R_OUT - 3) + 0.5;
            double pz = center.getZ() + Math.sin(a) * (R_OUT - 3) + 0.5;
            spawnRegenCloud(level, px, center.getY() + 0.5, pz);
        }

        for (int dx = -R_IN - 1; dx <= R_IN + 1; dx++) for (int dz = -R_IN - 1; dz <= R_IN + 1; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > R_IN) continue;
            for (int py = -1; py <= PODIUM_H; py++) set(level, center.offset(dx, py, dz), randomStoneBrick(random));
        }
        for (int dx = -R_IN + 1; dx <= R_IN - 1; dx++) for (int dz = -R_IN + 1; dz <= R_IN - 1; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > R_IN - 1) continue;
            set(level, center.offset(dx, PODIUM_H, dz), Blocks.DARK_PRISMARINE);
            set(level, center.offset(dx, PODIUM_H + 1, dz), Blocks.WATER);
        }

        for (Direction dir : HORIZONTALS) {
            int ox = dir.getStepX() * R_IN, oz = dir.getStepZ() * R_IN;
            set(level, center.offset(ox, PODIUM_H + 1, oz), Blocks.WATER);
            int rx = dir.getStepX() * (R_IN + 1), rz = dir.getStepZ() * (R_IN + 1);
            set(level, center.offset(rx, PODIUM_H, rz), randomStoneBrickStairs(random).defaultBlockState().setValue(StairBlock.FACING, dir));
            set(level, center.offset(rx, PODIUM_H + 1, rz), Blocks.WATER);
            set(level, center.offset(dir.getStepX() * R_RING, 0, dir.getStepZ() * R_RING), Blocks.WATER);
            set(level, center.offset(dir.getStepX() * R_RING, 1, dir.getStepZ() * R_RING), Blocks.AIR);
        }

        int pillarBase = PODIUM_H + 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { set(level, center.offset(dx, PODIUM_H + 1, dz), Blocks.CHISELED_STONE_BRICKS); set(level, center.offset(dx, pillarBase, dz), randomStoneBrick(random)); }
        set(level, center.offset(0, pillarBase + 1, 0), Blocks.CHISELED_STONE_BRICKS);
        set(level, center.offset(0, pillarBase + 2, 0), Blocks.MOSSY_STONE_BRICK_WALL);
        set(level, center.offset(0, pillarBase + 3, 0), Blocks.CHISELED_STONE_BRICKS);
        set(level, center.offset(0, pillarBase + 4, 0), Blocks.STONE_BRICK_WALL);
        set(level, center.offset(0, pillarBase + 5, 0), Blocks.CHISELED_STONE_BRICKS);

        int eyeY = pillarBase + 6;
        set(level, center.offset(0, eyeY + 1, 0), Blocks.BLUE_STAINED_GLASS);
        set(level, center.offset(0, eyeY + 3, 0), Blocks.LIGHTNING_ROD);

        placeCreatorRewardGlow(level, center.offset(0, eyeY + 5, 0), random);

        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            int ax = (int) Math.round(Math.cos(a) * (R_RING - 1)), az = (int) Math.round(Math.sin(a) * (R_RING - 1));
            for (int dy = 1; dy <= 3; dy++) set(level, center.offset(ax, dy, az), dy == 3 ? Blocks.CHISELED_STONE_BRICKS : randomStoneBrickWall(random));
            placeLeafyBushSafe(level, center.offset(ax, 4, az), 1, 0.30, random);
            placeHangingLeavesSafe(level, center.offset(ax, 2, az), 1, random);
        }

        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int cx2 = (int) Math.round(Math.cos(a) * (R_OUT + 4)), cz2 = (int) Math.round(Math.sin(a) * (R_OUT + 4));
            set(level, center.offset(cx2, 0, cz2), Blocks.MOSSY_COBBLESTONE);
            set(level, center.offset(cx2, 1, cz2), Blocks.AMETHYST_BLOCK);
            set(level, center.offset(cx2, 2, cz2), random.nextBoolean() ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD);
        }

        spawnMagicParticleEmitter(level, center.getX() + 0.5, center.getY() + PODIUM_H + 1.5, center.getZ() + 0.5);

        for (int dy = pillarBase + 2; dy <= eyeY; dy++) for (Direction dir : HORIZONTALS)
            if (random.nextInt(3) == 0) placeGlowLichen(level, center.offset(dir.getStepX(), dy, dir.getStepZ()), dir.getOpposite());

        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            int lx = (int) Math.round(Math.cos(a) * (R_OUT + 2));
            int lz = (int) Math.round(Math.sin(a) * (R_OUT + 2));
            placeHangingLeavesSafe(level, center.offset(lx, 2, lz), 3, random);
        }

        BlockPos chestPos = center.offset(0, 0, R_OUT + 2);
        set(level, chestPos, Blocks.AIR);
        placeChestFacing(level, chestPos, Direction.NORTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, chestPos, random);
        addLuckyBlocksToChest(level, chestPos, 2 + random.nextInt(3), random);

        ensureExteriorLighting(level, center, 15, random);

        applyBonemeal(level, center, 15, 150, random);
    }

    private static void spawnRegenCloud(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.entity.AreaEffectCloud cloud =
                new net.minecraft.world.entity.AreaEffectCloud(level, x, y, z);
        cloud.setRadius(2.5f);
        cloud.setDuration(Integer.MAX_VALUE);
        cloud.setRadiusPerTick(0);
        // PORT 1.21.1 : setParticle prend des ParticleOptions (defauts du type).
        cloud.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true));
        // PORT 1.21.1 : setFixedColor supprimé (couleur via ParticleOptions).
        level.addFreshEntity(cloud);
    }
}