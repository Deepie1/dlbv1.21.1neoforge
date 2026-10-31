package deepluckyblock.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import static deepluckyblock.procedures.StructuresProcedure.*;

public class Structures2Procedure {

    static void buildWatchtower(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 12);
        int R = 3;

        for (int dy = -10; dy < 0; dy++)
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++)
                if (dx == -R || dx == R || dz == -R || dz == R)
                    set(level, c.offset(dx, dy, dz), randomStoneBrick(random));

        applyGrassFade15(level, c, 15, random);

        for (int dy = 0; dy <= 5; dy++) {
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                boolean corner = (dx == -R || dx == R) && (dz == -R || dz == R);
                boolean edge = dx == -R || dx == R || dz == -R || dz == R;
                if (corner) {
                    set(level, c.offset(dx, dy, dz), Blocks.STRIPPED_DARK_OAK_LOG);
                    if (dy <= 3) {
                        int cx2 = dx > 0 ? 1 : -1, cz2 = dz > 0 ? 1 : -1;
                        set(level, c.offset(dx + cx2, dy, dz), randomStoneBrick(random));
                        set(level, c.offset(dx, dy, dz + cz2), randomStoneBrick(random));
                    }
                } else if (edge) {
                    set(level, c.offset(dx, dy, dz), randomStoneBrick(random));
                    if (dy == 2 && random.nextInt(3) == 0) {
                        Direction f = dx == -R ? Direction.EAST : dx == R ? Direction.WEST : dz == -R ? Direction.SOUTH : Direction.NORTH;
                        BlockPos bp = c.offset(dx + f.getStepX(), dy, dz + f.getStepZ());
                        if (level.getBlockState(bp).isAir())
                            set(level, bp, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f));
                    }
                }
            }
        }

        for (int dy = 6; dy <= 16; dy++) {
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                boolean corner = (dx == -R || dx == R) && (dz == -R || dz == R);
                boolean edge = dx == -R || dx == R || dz == -R || dz == R;
                if (corner) set(level, c.offset(dx, dy, dz), Blocks.STRIPPED_DARK_OAK_LOG);
                else if (edge) {
                    if (dy == 6 || dy == 11 || dy == 16 || dx == 0 || dz == 0)
                        set(level, c.offset(dx, dy, dz), Blocks.STRIPPED_DARK_OAK_LOG);
                    else set(level, c.offset(dx, dy, dz), Blocks.SPRUCE_PLANKS);
                }
            }
            set(level, c.offset(0, dy, -R - 1), Blocks.STRIPPED_DARK_OAK_LOG);
            set(level, c.offset(0, dy, R + 1), Blocks.STRIPPED_DARK_OAK_LOG);
            set(level, c.offset(-R - 1, dy, 0), Blocks.STRIPPED_DARK_OAK_LOG);
            set(level, c.offset(R + 1, dy, 0), Blocks.STRIPPED_DARK_OAK_LOG);
        }

        clearBox(level, c.offset(-R + 1, 1, -R + 1), c.offset(R - 1, 16, R - 1));

        for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++)
            set(level, c.offset(dx, 0, dz), random.nextInt(4) == 0 ? Blocks.MOSS_BLOCK :
                    (random.nextInt(3) == 0 ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS));

        for (int fy : new int[]{8, 14}) {
            for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) {
                if (dx == R - 1 && dz == 0) continue;
                set(level, c.offset(dx, fy, dz), Blocks.SPRUCE_PLANKS);
            }
        }

        for (int dy = 1; dy <= 17; dy++)
            set(level, c.offset(R - 1, dy, 0), Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.WEST));

        set(level, c.offset(0, 1, R), Blocks.AIR);
        set(level, c.offset(0, 2, R), Blocks.AIR);
        placeDoor(level, c.offset(0, 1, R), Direction.SOUTH);
        buildGrandEntrance(level, c.offset(0, 1, R), Direction.SOUTH, 1, random);

        for (int fy : new int[]{4, 10, 15}) {
            set(level, c.offset(-R, fy, 0), Blocks.GLASS);
            set(level, c.offset(R, fy, 0), Blocks.GLASS);
            set(level, c.offset(0, fy, -R), Blocks.GLASS);
            set(level, c.offset(0, fy, R), Blocks.GLASS);
        }

        int PR = R + 1;
        for (int dx = -PR; dx <= PR; dx++) for (int dz = -PR; dz <= PR; dz++)
            set(level, c.offset(dx, 17, dz), Blocks.SPRUCE_PLANKS);
        for (int dx = -PR; dx <= PR; dx++) {
            set(level, c.offset(dx, 18, -PR), Blocks.SPRUCE_FENCE);
            set(level, c.offset(dx, 18, PR), Blocks.SPRUCE_FENCE);
        }
        for (int dz = -PR + 1; dz <= PR - 1; dz++) {
            set(level, c.offset(-PR, 18, dz), Blocks.SPRUCE_FENCE);
            set(level, c.offset(PR, 18, dz), Blocks.SPRUCE_FENCE);
        }
        set(level, c.offset(R - 1, 17, 0), Blocks.AIR);

        buildConeRoof(level, c.offset(0, 19, 0), PR - 1, random);

        for (int fy : new int[]{3, 9, 15})
            set(level, c.offset(-R + 1, fy, 0), Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

        set(level, c.offset(0, 1, R + 2), Blocks.OAK_FENCE);
        set(level, c.offset(0, 2, R + 2), Blocks.OAK_FENCE);
        set(level, c.offset(0, 3, R + 2), Blocks.OAK_FENCE);
        set(level, c.offset(0, 4, R + 2), Blocks.RED_BANNER);

        set(level, c.offset(0, 18, 0), Blocks.POLISHED_ANDESITE);
        set(level, c.offset(0, 19, 0), Blocks.CHISELED_STONE_BRICKS);
        placeChestFacing(level, c.offset(0, 20, 0), Direction.SOUTH, "minecraft:chests/end_city_treasure", random);
        addCreatorRewardToChest(level, c.offset(0, 20, 0), random);
        addLuckyBlocksToChest(level, c.offset(0, 20, 0), 2 + random.nextInt(4), random);
        for (Direction dir : HORIZONTALS) set(level, c.offset(dir.getStepX(), 19, dir.getStepZ()), Blocks.CANDLE);
        placeCreatorRewardGlow(level, c.offset(0, 22, 0), random);

        placeChestFacing(level, c.offset(-R + 1, 1, R - 1), Direction.EAST, "minecraft:chests/simple_dungeon", random);
        addLuckyBlocksToChest(level, c.offset(-R + 1, 1, R - 1), 1 + random.nextInt(2), random);

        for (int dy = 2; dy < 17; dy++)
            for (Direction dir : HORIZONTALS)
                if (random.nextInt(2) == 0)
                    placeVineOnFace(level, c.offset(dir.getStepX() * (R + 1), dy, dir.getStepZ() * (R + 1)), dir);

        for (int i = 0; i < 4; i++) {
            int dx = (i % 2 == 0) ? -PR : PR;
            int dz = (i < 2) ? -PR : PR;
            placeHangingLeavesSafe(level, c.offset(dx, 19, dz), 4, random);
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            int lx = (int) Math.round(Math.cos(a) * PR);
            int lz = (int) Math.round(Math.sin(a) * PR);
            placeHangingLeavesSafe(level, c.offset(lx, 19, lz), 5, random);
        }

        ensureLighting(level, c.offset(-R + 1, 1, -R + 1), c.offset(R - 1, 16, R - 1), random);
        ensureExteriorLighting(level, c, 15, random);

        for (int i = 0; i < 15; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 6 + random.nextInt(8);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK))
                placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 120, random);
    }

    static void buildFarm(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 12);
        int GW = 13, GD = 9, GH = 8;
        int gx0 = -GW / 2, gz0 = -GD / 2;

        for (int dy = -10; dy < 0; dy++)
            for (int dx = 0; dx < GW; dx++) for (int dz = 0; dz < GD; dz++)
                if (dx == 0 || dx == GW - 1 || dz == 0 || dz == GD - 1)
                    set(level, c.offset(gx0 + dx, dy, gz0 + dz), randomStoneBrick(random));

        applyGrassFade15(level, c, 15, random);

        for (int dy = 0; dy < GH; dy++)
            for (int dx = 0; dx < GW; dx++) for (int dz = 0; dz < GD; dz++) {
                boolean corner = (dx == 0 || dx == GW - 1) && (dz == 0 || dz == GD - 1);
                boolean edge = dx == 0 || dx == GW - 1 || dz == 0 || dz == GD - 1;
                if (dy < 2 && edge) set(level, c.offset(gx0 + dx, dy, gz0 + dz), randomStoneBrick(random));
                else if (corner) set(level, c.offset(gx0 + dx, dy, gz0 + dz), Blocks.STRIPPED_DARK_OAK_LOG);
                else if (edge) {
                    if (dx == GW / 2 || dz == GD / 2 || dy == GH - 1)
                        set(level, c.offset(gx0 + dx, dy, gz0 + dz), Blocks.STRIPPED_DARK_OAK_LOG);
                    else set(level, c.offset(gx0 + dx, dy, gz0 + dz), Blocks.SPRUCE_PLANKS);
                }
            }

        clearBox(level, c.offset(gx0 + 1, 1, gz0 + 1), c.offset(gx0 + GW - 2, GH - 1, gz0 + GD - 2));

        for (int dx = 1; dx < GW - 1; dx++) for (int dz = 1; dz < GD - 1; dz++)
            set(level, c.offset(gx0 + dx, 0, gz0 + dz), random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.SPRUCE_PLANKS);

        buildGableRoof(level, c.offset(gx0, GH, gz0), GW, GD, random);

        set(level, c.offset(-1, 1, gz0 + GD - 1), Blocks.AIR);
        set(level, c.offset(-1, 2, gz0 + GD - 1), Blocks.AIR);
        set(level, c.offset(0, 1, gz0 + GD - 1), Blocks.AIR);
        set(level, c.offset(0, 2, gz0 + GD - 1), Blocks.AIR);
        placeDoor(level, c.offset(-1, 1, gz0 + GD - 1), Direction.SOUTH);
        placeDoor(level, c.offset(0, 1, gz0 + GD - 1), Direction.SOUTH);
        buildGrandEntrance(level, c.offset(0, 1, gz0 + GD - 1), Direction.SOUTH, 2, random);

        for (int dx = 3; dx < GW - 2; dx += 4) {
            set(level, c.offset(gx0 + dx, 3, gz0), Blocks.GLASS);
            set(level, c.offset(gx0 + dx, 3, gz0 + GD - 1), Blocks.GLASS);
        }

        for (int i = 0; i < 6; i++) {
            int hx = gx0 + 2 + random.nextInt(GW - 4), hz = gz0 + 2 + random.nextInt(GD - 4);
            set(level, c.offset(hx, 1, hz), Blocks.HAY_BLOCK);
            if (random.nextBoolean()) set(level, c.offset(hx, 2, hz), Blocks.HAY_BLOCK);
        }

        placeItemFrameVertical(level, c.offset(gx0 + 1, 3, gz0 + 1), Direction.EAST, new ItemStack(Items.IRON_HOE));
        placeItemFrameVertical(level, c.offset(gx0 + 1, 3, gz0 + 2), Direction.EAST, new ItemStack(Items.IRON_SHOVEL));

        set(level, c.offset(gx0 + GW - 2, 1, gz0 + 1), Blocks.POLISHED_ANDESITE);
        set(level, c.offset(gx0 + GW - 2, 2, gz0 + 1), Blocks.CHISELED_STONE_BRICKS);
        placeChestFacing(level, c.offset(gx0 + GW - 2, 3, gz0 + 1), Direction.WEST, "minecraft:chests/village/village_plains_house", random);
        addCreatorRewardToChest(level, c.offset(gx0 + GW - 2, 3, gz0 + 1), random);
        addLuckyBlocksToChest(level, c.offset(gx0 + GW - 2, 3, gz0 + 1), 2 + random.nextInt(3), random);
        for (Direction dir : HORIZONTALS) setIfAir(level, c.offset(gx0 + GW - 2 + dir.getStepX(), 2, gz0 + 1 + dir.getStepZ()), Blocks.CANDLE);
        placeCreatorRewardGlow(level, c.offset(gx0 + GW - 2, 5, gz0 + 1), random);

        int FX0 = gx0 + GW + 2, FZ0 = gz0, FW = 15, FD = 15;
        for (int dx = 0; dx < FW; dx++) for (int dz = 0; dz < FD; dz++) {
            if (dz == FD / 2) {
                set(level, c.offset(FX0 + dx, -1, FZ0 + dz), Blocks.DIRT);
                set(level, c.offset(FX0 + dx, 0, FZ0 + dz), Blocks.WATER);
            } else {
                set(level, c.offset(FX0 + dx, -1, FZ0 + dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                Block crop = switch (random.nextInt(4)) { case 0 -> Blocks.WHEAT; case 1 -> Blocks.CARROTS; case 2 -> Blocks.POTATOES; default -> Blocks.BEETROOTS; };
                BlockState cs = crop.defaultBlockState();
                if (crop == Blocks.BEETROOTS) cs = cs.setValue(BeetrootBlock.AGE, 3);
                else cs = cs.setValue(CropBlock.AGE, 7);
                set(level, c.offset(FX0 + dx, 0, FZ0 + dz), cs);
            }
        }

        set(level, c.offset(FX0 + FW - 1, 0, FZ0 - 1), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));

        int TX = gx0 - 6, TZ = 0;
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) set(level, c.offset(TX + dx, 0, TZ + dz), Blocks.MOSSY_COBBLESTONE);
        set(level, c.offset(TX + 1, 1, TZ + 1), Blocks.DARK_OAK_FENCE);
        set(level, c.offset(TX + 1, 2, TZ + 1), Blocks.DARK_OAK_FENCE);
        set(level, c.offset(TX, 2, TZ + 1), Blocks.DARK_OAK_FENCE);
        set(level, c.offset(TX + 2, 2, TZ + 1), Blocks.DARK_OAK_FENCE);
        set(level, c.offset(TX + 1, 1, TZ + 2), Blocks.MOSSY_COBBLESTONE);
        BlockPos signPos = c.offset(TX + 1, 1, TZ + 1);
        set(level, signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("§8R.I.P.")).setMessage(1, Component.literal("§8Pépé Roger")).setMessage(2, Component.literal("§c<3")).setMessage(3, Component.literal("1892-1976")), true);
            sign.setChanged();
        }
        for (int dx = -1; dx <= 3; dx++) for (int dz = -1; dz <= 3; dz++) {
            if (dx >= 0 && dx < 3 && dz >= 0 && dz < 3) continue;
            BlockPos fp = c.offset(TX + dx, 1, TZ + dz);
            if (level.getBlockState(fp).isAir() && level.getBlockState(fp.below()).is(Blocks.GRASS_BLOCK))
                if (random.nextInt(2) == 0) set(level, fp, Blocks.POPPY);
        }
        set(level, c.offset(TX, 1, TZ), Blocks.SOUL_TORCH);
        set(level, c.offset(TX + 2, 1, TZ), Blocks.SOUL_TORCH);

        placeChestFacing(level, c.offset(TX - 1, 0, TZ + 1), Direction.WEST, "minecraft:chests/simple_dungeon", random);
        addLuckyBlocksToChest(level, c.offset(TX - 1, 0, TZ + 1), 1, random);

        ensureLighting(level, c.offset(gx0 + 1, 1, gz0 + 1), c.offset(gx0 + GW - 2, GH - 1, gz0 + GD - 2), random);
        ensureExteriorLighting(level, c, 15, random);

        for (int dy = 2; dy < GH; dy++)
            for (int dx = 0; dx < GW; dx++) {
                if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(gx0 + dx, dy, gz0 - 1), Direction.SOUTH);
                if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(gx0 + dx, dy, gz0 + GD), Direction.NORTH);
            }
        for (int dx = 0; dx < GW; dx += 2) {
            if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, c.offset(gx0 + dx, GH, gz0), 3, random);
            if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, c.offset(gx0 + dx, GH, gz0 + GD - 1), 3, random);
        }
        for (int i = 0; i < 20; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 10 + random.nextInt(5);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK))
                placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 200, random);
    }

    static void buildForge(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 10);
        int W = 11, D = 9, H = 7;
        int x0 = -W / 2, z0 = -D / 2;

        for (int dy = -10; dy <= 2; dy++)
            for (int dx = 0; dx < W; dx++) for (int dz = 0; dz < D; dz++)
                if (dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1) {
                    Block b = dy < 3 ? (random.nextInt(2) == 0 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE) : randomStoneBrick(random);
                    set(level, c.offset(x0 + dx, dy, z0 + dz), b);
                }

        applyGrassFade15(level, c, 15, random);

        for (int dy = 3; dy <= H; dy++)
            for (int dx = 0; dx < W; dx++) for (int dz = 0; dz < D; dz++) {
                boolean corner = (dx == 0 || dx == W - 1) && (dz == 0 || dz == D - 1);
                boolean edge = dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1;
                if (corner) set(level, c.offset(x0 + dx, dy, z0 + dz), Blocks.STRIPPED_DARK_OAK_LOG);
                else if (edge) {
                    if (dy == H || dx == W / 2 || dz == D / 2) set(level, c.offset(x0 + dx, dy, z0 + dz), Blocks.STRIPPED_DARK_OAK_LOG);
                    else set(level, c.offset(x0 + dx, dy, z0 + dz), Blocks.SPRUCE_PLANKS);
                }
            }

        clearBox(level, c.offset(x0 + 1, -1, z0 + 1), c.offset(x0 + W - 2, H, z0 + D - 2));

        for (int dx = 1; dx < W - 1; dx++) for (int dz = 1; dz < D - 1; dz++)
            set(level, c.offset(x0 + dx, -1, z0 + dz), random.nextInt(3) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);

        buildGableRoof(level, c.offset(x0, H + 1, z0), W, D, random);

        for (int dy = -1; dy <= H + 8; dy++) {
            set(level, c.offset(x0 + 1, dy, z0 + 1), Blocks.COBBLESTONE);
            set(level, c.offset(x0 + 2, dy, z0 + 1), Blocks.COBBLESTONE);
            set(level, c.offset(x0 + 1, dy, z0 + 2), Blocks.COBBLESTONE);
            set(level, c.offset(x0 + 2, dy, z0 + 2), Blocks.COBBLESTONE);
        }
        clearBox(level, c.offset(x0 + 1, 0, z0 + 1), c.offset(x0 + 2, 1, z0 + 2));
        set(level, c.offset(x0 + 1, 0, z0 + 1), Blocks.CAMPFIRE);
        set(level, c.offset(x0 + 2, 0, z0 + 2), Blocks.CAMPFIRE);

        set(level, c.offset(x0 + W / 2, 0, z0 + D / 2), Blocks.SMOOTH_STONE);
        set(level, c.offset(x0 + W / 2, 1, z0 + D / 2), Blocks.ANVIL);

        set(level, c.offset(x0 + 3, 0, z0 + 1), Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, c.offset(x0 + 4, 0, z0 + 1), Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, c.offset(x0 + 2, 3, z0 + 2), Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

        placeWaterCauldron(level, c.offset(x0 + W / 2 + 1, 0, z0 + D / 2));
        set(level, c.offset(x0 + W / 2 + 2, 0, z0 + D / 2), Blocks.LAVA);

        set(level, c.offset(x0 + W - 3, 0, z0 + 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        set(level, c.offset(x0 + W - 3, 1, z0 + 1), Blocks.GRINDSTONE);
        set(level, c.offset(x0 + W - 2, 0, z0 + 2), Blocks.STONECUTTER);

        set(level, c.offset(x0 + 5, 0, z0 + 6), Blocks.BARREL);
        set(level, c.offset(x0 + 5, 1, z0 + 6), Blocks.BARREL);
        set(level, c.offset(x0 + 6, 0, z0 + 6), Blocks.BARREL);
        set(level, c.offset(x0 + 4, 0, z0 + 6), Blocks.BARREL);

        placeItemFrameVertical(level, c.offset(x0 + W / 2, 4, z0 + D - 1), Direction.NORTH, new ItemStack(Items.DIAMOND_SWORD));
        placeItemFrameVertical(level, c.offset(x0 + W / 2 - 2, 4, z0 + D - 1), Direction.NORTH, new ItemStack(Items.IRON_INGOT));
        placeItemFrameVertical(level, c.offset(x0 + W / 2 + 2, 4, z0 + D - 1), Direction.NORTH, new ItemStack(Items.IRON_PICKAXE));

        for (int dx = W - 4; dx <= W - 2; dx++) {
            set(level, c.offset(x0 + dx, 0, z0 + D - 2), Blocks.OAK_FENCE);
            set(level, c.offset(x0 + dx, 1, z0 + D - 2), Blocks.OAK_SLAB);
        }

        BlockPos chestP = c.offset(x0 + W / 2, 0, z0 + D - 3);
        placeChestFacing(level, chestP, Direction.NORTH, "minecraft:chests/village/village_weaponsmith", random);
        addCreatorRewardToChest(level, chestP, random);
        addLuckyBlocksToChest(level, chestP, 3 + random.nextInt(3), random);
        for (Direction dir : new Direction[]{Direction.EAST, Direction.WEST}) setIfAir(level, chestP.relative(dir), Blocks.CANDLE);
        placeCreatorRewardGlow(level, c.offset(x0 + W / 2, 3, z0 + D - 3), random);

        set(level, c.offset(x0 + W / 2, 5, z0 + D - 1), Blocks.BLACK_BANNER);

        set(level, c.offset(x0 + 2, 3, z0 + D - 1), Blocks.AIR);
        set(level, c.offset(x0 + 2, 4, z0 + D - 1), Blocks.AIR);
        placeDoor(level, c.offset(x0 + 2, 3, z0 + D - 1), Direction.SOUTH);
        buildGrandEntrance(level, c.offset(x0 + 2, 3, z0 + D - 1), Direction.SOUTH, 1, random);

        for (int dx = 5; dx < W - 3; dx += 3) {
            set(level, c.offset(x0 + dx, 5, z0), Blocks.IRON_BARS);
            set(level, c.offset(x0 + dx, 5, z0 + D - 1), Blocks.IRON_BARS);
        }

        set(level, c.offset(x0 + W / 2, H, z0 + D / 2), Blocks.CHAIN);
        set(level, c.offset(x0 + W / 2, H - 1, z0 + D / 2), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));

        set(level, c.offset(x0 + 1, 3, z0 + D / 2), Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        set(level, c.offset(x0 + W - 2, 3, z0 + D / 2), Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));

        ensureLighting(level, c.offset(x0 + 1, -1, z0 + 1), c.offset(x0 + W - 2, H, z0 + D - 2), random);
        ensureExteriorLighting(level, c, 15, random);

        for (int dy = 3; dy < H; dy++)
            for (int dx = 0; dx < W; dx++) {
                if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(x0 + dx, dy, z0 - 1), Direction.SOUTH);
                if (random.nextInt(2) == 0) placeVineOnFace(level, c.offset(x0 + dx, dy, z0 + D), Direction.NORTH);
            }
        for (int dx = 0; dx < W; dx += 2) {
            if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, c.offset(x0 + dx, H + 1, z0), 3, random);
            if (random.nextInt(2) == 0) placeHangingLeavesSafe(level, c.offset(x0 + dx, H + 1, z0 + D - 1), 3, random);
        }
        for (int i = 0; i < 12; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 8 + random.nextInt(6);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK))
                placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        applyBonemeal(level, c, 15, 120, random);
    }

    static void buildBunker(ServerLevel level, BlockPos o, RandomSource random) {
        BlockPos c = o.offset(0, 1, 8);

        applyGrassFade15(level, c, 15, random);

        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            set(level, c.offset(dx, 0, dz), randomStoneBrick(random));
        set(level, c.offset(0, 0, 0), Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.OPEN, true));

        set(level, c.offset(0, 1, -2), Blocks.OAK_FENCE);
        BlockPos signPos = c.offset(0, 2, -2);
        set(level, signPos, Blocks.OAK_SIGN);
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("§c§l⚠ ACCÈS")).setMessage(1, Component.literal("§c§lINTERDIT ⚠")).setMessage(2, Component.literal("§7Danger")).setMessage(3, Component.literal("§7Militaire")), true);
            sign.setChanged();
        }

        for (int dy = -1; dy >= -15; dy--) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx == -1 || dx == 1 || dz == -1 || dz == 1) set(level, c.offset(dx, dy, dz), Blocks.IRON_BARS);
            }
            set(level, c.offset(0, dy, 0), Blocks.AIR);
            set(level, c.offset(0, dy, -1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            if (dy % 3 == 0) set(level, c.offset(1, dy, 0), Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }

        int BY_BOT = -15, BY_TOP = -5, BW = 21, BD = 15;
        int bx0 = -BW / 2, bz0 = -BD / 2 + 2;

        for (int dy = BY_BOT; dy <= BY_TOP; dy++)
            for (int dx = 0; dx < BW; dx++) for (int dz = 0; dz < BD; dz++) {
                boolean edge = dx == 0 || dx == BW - 1 || dz == 0 || dz == BD - 1 || dy == BY_BOT || dy == BY_TOP;
                if (edge) {
                    Block b = random.nextInt(4) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : (random.nextInt(2) == 0 ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE);
                    set(level, c.offset(bx0 + dx, dy, bz0 + dz), b);
                }
            }

        for (int dx = 1; dx < BW - 1; dx++) for (int dz = 1; dz < BD - 1; dz++) {
            set(level, c.offset(bx0 + dx, BY_BOT + 1, bz0 + dz), Blocks.POLISHED_BLACKSTONE);
            set(level, c.offset(bx0 + dx, BY_TOP, bz0 + dz), Blocks.DEEPSLATE_TILES);
        }

        int sep1 = 5, sep2 = 15;
        for (int dy = BY_BOT + 1; dy < BY_TOP; dy++) for (int dz = 1; dz < BD - 1; dz++) {
            set(level, c.offset(bx0 + sep1, dy, bz0 + dz), Blocks.DEEPSLATE_BRICKS);
            set(level, c.offset(bx0 + sep2, dy, bz0 + dz), Blocks.DEEPSLATE_BRICKS);
        }
        for (int dy : new int[]{BY_BOT + 2, BY_BOT + 3}) {
            set(level, c.offset(bx0 + sep1, dy, bz0 + BD / 2), Blocks.AIR);
            set(level, c.offset(bx0 + sep2, dy, bz0 + BD / 2), Blocks.AIR);
        }
        set(level, c.offset(bx0 + sep1, BY_BOT + 2, bz0 + BD / 2), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, c.offset(bx0 + sep1, BY_BOT + 3, bz0 + BD / 2), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, c.offset(bx0 + sep2, BY_BOT + 2, bz0 + BD / 2), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, c.offset(bx0 + sep2, BY_BOT + 3, bz0 + BD / 2), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        for (int dx = 3; dx < BW - 2; dx += 4) for (int dz = 3; dz < BD - 2; dz += 4)
            set(level, c.offset(bx0 + dx, BY_TOP - 1, bz0 + dz), Blocks.REDSTONE_LAMP);

        for (int dy = BY_BOT + 2; dy <= BY_BOT + 3; dy++) set(level, c.offset(0, dy, bz0 - 1), Blocks.AIR);

        ensureLighting(level, c.offset(bx0 + 1, BY_BOT + 1, bz0 + 1), c.offset(bx0 + BW - 2, BY_TOP - 1, bz0 + BD - 2), random);

        placeItemFrameVertical(level, c.offset(bx0 + 1, BY_BOT + 3, bz0 + 2), Direction.EAST, new ItemStack(Items.IRON_SWORD));
        placeItemFrameVertical(level, c.offset(bx0 + 1, BY_BOT + 3, bz0 + 4), Direction.EAST, new ItemStack(Items.CROSSBOW));
        placeItemFrameVertical(level, c.offset(bx0 + 1, BY_BOT + 3, bz0 + 6), Direction.EAST, new ItemStack(Items.BOW));
        placeItemFrameVertical(level, c.offset(bx0 + 1, BY_BOT + 3, bz0 + 8), Direction.EAST, new ItemStack(Items.SHIELD));
        placeChestFacing(level, c.offset(bx0 + 2, BY_BOT + 2, bz0 + BD - 2), Direction.WEST, "minecraft:chests/simple_dungeon", random);
        addLuckyBlocksToChest(level, c.offset(bx0 + 2, BY_BOT + 2, bz0 + BD - 2), 2, random);

        int s2cx = bx0 + (sep1 + sep2) / 2, s2cz = bz0 + BD / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, c.offset(s2cx + dx, BY_BOT + 2, s2cz + dz), Blocks.POLISHED_BLACKSTONE);
        set(level, c.offset(s2cx, BY_BOT + 3, s2cz), Blocks.CHISELED_POLISHED_BLACKSTONE);
        placeChestFacing(level, c.offset(s2cx, BY_BOT + 4, s2cz), Direction.SOUTH, "minecraft:chests/bastion_treasure", random);
        addCreatorRewardToChest(level, c.offset(s2cx, BY_BOT + 4, s2cz), random);
        addLuckyBlocksToChest(level, c.offset(s2cx, BY_BOT + 4, s2cz), 5 + random.nextInt(6), random);
        for (Direction dir : HORIZONTALS) setIfAir(level, c.offset(s2cx + dir.getStepX(), BY_BOT + 3, s2cz + dir.getStepZ()), Blocks.RED_CANDLE);
        placeCreatorRewardGlow(level, c.offset(s2cx, BY_BOT + 6, s2cz), random);

        set(level, c.offset(bx0 + sep1 + 2, BY_BOT + 4, bz0 + 1), Blocks.GRAY_WALL_BANNER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, c.offset(bx0 + sep1 + 2, BY_BOT + 4, bz0 + BD - 2), Blocks.BLACK_WALL_BANNER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        placeItemFrameVertical(level, c.offset(s2cx - 3, BY_BOT + 4, bz0 + 1), Direction.SOUTH, new ItemStack(Items.NETHER_STAR));
        placeItemFrameVertical(level, c.offset(s2cx + 3, BY_BOT + 4, bz0 + 1), Direction.SOUTH, new ItemStack(Items.TOTEM_OF_UNDYING));

        int s3cx = bx0 + (sep2 + BW - 1) / 2;
        for (int i = 0; i < 8; i++) { int by = BY_BOT + 2 + (i / 4); set(level, c.offset(bx0 + sep2 + 2 + (i % 4), by, bz0 + 2), Blocks.BARREL); }
        placeChestFacing(level, c.offset(s3cx, BY_BOT + 2, bz0 + BD - 2), Direction.NORTH, "minecraft:chests/stronghold_corridor", random);
        addLuckyBlocksToChest(level, c.offset(s3cx, BY_BOT + 2, bz0 + BD - 2), 1 + random.nextInt(2), random);
        placeWaterCauldron(level, c.offset(s3cx - 2, BY_BOT + 2, bz0 + BD - 2));
        set(level, c.offset(s3cx + 2, BY_BOT + 2, bz0 + BD - 2), Blocks.COMPOSTER);
        set(level, c.offset(s3cx, BY_BOT + 2, bz0 + 2), Blocks.BREWING_STAND);
        set(level, c.offset(bx0 + BW - 3, BY_BOT + 2, bz0 + 2), Blocks.TNT);

        for (int i = 0; i < 10; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int rad = 3 + random.nextInt(4);
            BlockPos tb = findFirstSolidBelow(level, c.offset((int)(Math.cos(ang) * rad), 0, (int)(Math.sin(ang) * rad)));
            if (tb != null && level.getBlockState(tb).is(Blocks.GRASS_BLOCK))
                placeLeafyBushSafe(level, tb.above(1), 1 + random.nextInt(2), 0.3, random);
        }
        for (int dy = -1; dy >= -8; dy--)
            for (Direction dir : HORIZONTALS)
                if (random.nextInt(3) == 0)
                    placeVineOnFace(level, c.offset(dir.getStepX() * 2, dy, dir.getStepZ() * 2), dir.getOpposite());

        ensureExteriorLighting(level, c, 15, random);
        applyBonemeal(level, c, 15, 100, random);
    }
}