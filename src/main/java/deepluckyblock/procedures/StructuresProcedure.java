package deepluckyblock.procedures;

import deepluckyblock.enchantments.PixelFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.registries.BuiltInRegistries;

public class StructuresProcedure {

    static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM,
            Blocks.BLUE_ORCHID, Blocks.PINK_TULIP, Blocks.WHITE_TULIP
    };
    static final Block[] LEAF_MIX = {
            Blocks.OAK_LEAVES, Blocks.OAK_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_LEAVES
    };

    public static String generate(ServerLevel level, double x, double y, double z, RandomSource random) {
        BlockPos origin = new BlockPos((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z));
        int eventId = random.nextInt(18);
        if (eventId >= 12) eventId++;
        return dispatchStructure(level, origin, random, eventId);
    }

    public static String dispatchStructure(ServerLevel level, BlockPos o, RandomSource random, int id) {
        return switch (id) {
            case 0 -> { Structures1Procedure.buildGrandManoir(level, o, random); yield "§aGrand Manoir Envahi par la Nature"; }
            case 1 -> { Structures1Procedure.buildMageTower(level, o, random); yield "§5Tour du Mage Arcane"; }
            case 2 -> { Structures1Procedure.buildEgyptianPyramid(level, o, random); yield "§6Pyramide Égyptienne Oubliée"; }
            case 3 -> { Structures1Procedure.buildFountain(level, o, random); yield "§bFontaine Céleste Moussue"; }
            case 4 -> { Structures2Procedure.buildWatchtower(level, o, random); yield "§eTour de Guet Délabrée"; }
            case 5 -> { Structures2Procedure.buildFarm(level, o, random); yield "§2Ferme des Anciens"; }
            case 6 -> { Structures2Procedure.buildForge(level, o, random); yield "§6Forge du Forgeron Abandonnée"; }
            case 7 -> { Structures2Procedure.buildBunker(level, o, random); yield "§8Bunker Blindé Enfoui"; }
            case 8 -> { Structures3Procedure.buildFairyGarden(level, o, random); yield "§aJardin Féerique Sauvage"; }
            case 9 -> { Structures3Procedure.buildPodium(level, o, random); yield "§ePodium du Champion Oublié"; }
            case 10 -> { Structures3Procedure.buildCrystalSanctuary(level, o, random); yield "§dSanctuaire de Cristal"; }
            case 11 -> { Structures3Procedure.buildFloatingIsland(level, o, random); yield "§3Île Flottante Mystique"; }
            case 13 -> { Structures4Procedure.spawnEverest(level, o); yield "§bL'Everest d'Inoxtag"; }
            case 14 -> { Structures4Procedure.spawnShipdead(level, o); yield "§7Épave du Vieux Marin"; }
            case 15 -> { Structures4Procedure.spawnCircus(level, o); yield "§cCirque Abandonné du Vieux Monde"; }
            case 16 -> { Structures5Procedure.buildSylvanCitadel(level, o, random); yield "§2Citadelle Sylvestre des Anciens"; }
            case 17 -> { Structures5Procedure.buildFaeObservatory(level, o, random); yield "§dObservatoire Féerique de la Lune"; }
            case 18 -> { Structures5Procedure.buildVerdantDragonSanctum(level, o, random); yield "§aSanctuaire Verdoyant du Dragon"; }
            default -> { Structures1Procedure.buildFountain(level, o, random); yield "§bFontaine Céleste Moussue"; }
        };
    }

    static Block randomStoneBrick(RandomSource r) {
        int v = r.nextInt(100);
        if (v < 40) return Blocks.STONE_BRICKS;
        if (v < 70) return Blocks.MOSSY_STONE_BRICKS;
        if (v < 90) return Blocks.CRACKED_STONE_BRICKS;
        return Blocks.CHISELED_STONE_BRICKS;
    }
    static Block randomStoneBrickSlab(RandomSource r) {
        return r.nextInt(100) < 55 ? Blocks.STONE_BRICK_SLAB : Blocks.MOSSY_STONE_BRICK_SLAB;
    }
    static Block randomStoneBrickStairs(RandomSource r) {
        return r.nextInt(100) < 55 ? Blocks.STONE_BRICK_STAIRS : Blocks.MOSSY_STONE_BRICK_STAIRS;
    }
    static Block randomStoneBrickWall(RandomSource r) {
        return r.nextInt(100) < 55 ? Blocks.STONE_BRICK_WALL : Blocks.MOSSY_STONE_BRICK_WALL;
    }

    static BlockState randomLeafState(RandomSource r) {
        return LEAF_MIX[r.nextInt(LEAF_MIX.length)].defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }
    static boolean isLeafSafeForWater(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values())
            if (level.getBlockState(pos.relative(d)).is(Blocks.WATER)) return false;
        return true;
    }
    static void placeLeafyBushSafe(ServerLevel level, BlockPos center, int radius, double smooth, RandomSource r) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.sqrt(dx*dx+dy*dy+dz*dz) > radius + 0.3 || r.nextDouble() < smooth) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir() || !isLeafSafeForWater(level, pos)) continue;
                    set(level, pos, randomLeafState(r));
                }
    }

    static void placeHangingLeavesSafe(ServerLevel level, BlockPos top, int maxDrop, RandomSource r) {
        int drop = 1 + r.nextInt(maxDrop);
        for (int i = 0; i <= drop; i++) {
            BlockPos pos = top.below(i);
            if (!level.getBlockState(pos).isAir() || !isLeafSafeForWater(level, pos)) {
                if (i > 0 && r.nextInt(2) == 0) {
                    for (int ground = 0; ground < r.nextInt(4); ground++) {
                        BlockPos gp = pos.above().relative(HORIZONTALS[r.nextInt(4)], ground + 1);
                        if (level.getBlockState(gp).isAir() && isLeafSafeForWater(level, gp)) {
                            set(level, gp, randomLeafState(r));
                            if (r.nextInt(3) == 0) {
                                Direction branch = HORIZONTALS[r.nextInt(4)];
                                BlockPos bp = gp.relative(branch);
                                if (level.getBlockState(bp).isAir() && isLeafSafeForWater(level, bp))
                                    set(level, bp, randomLeafState(r));
                            }
                        }
                    }
                }
                break;
            }
            if (i == 0 || r.nextInt(3) != 0) set(level, pos, randomLeafState(r));
        }
    }

    static void placeCaveVinesHanging(ServerLevel level, BlockPos top, int length, RandomSource r) {
        for (int i = 1; i <= length; i++) {
            BlockPos pos = top.below(i);
            if (!level.getBlockState(pos).isAir()) break;
            if (i == length)
                set(level, pos, Blocks.CAVE_VINES.defaultBlockState()
                        .setValue(BlockStateProperties.BERRIES, r.nextBoolean()));
            else set(level, pos, Blocks.CAVE_VINES_PLANT.defaultBlockState());
        }
    }
    static void placeVineOnFace(ServerLevel level, BlockPos pos, Direction dir) {
        if (!level.getBlockState(pos).isAir()) return;
        BlockState vine = Blocks.VINE.defaultBlockState();
        BooleanProperty prop = switch (dir) {
            case NORTH -> VineBlock.NORTH; case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST; case WEST -> VineBlock.WEST;
            default -> null;
        };
        if (prop != null) vine = vine.setValue(prop, true);
        set(level, pos, vine);
    }

    static BlockPos findFirstSolidBelow(ServerLevel level, BlockPos start) {
        for (int dy = 10; dy >= -50; dy--) {
            BlockPos pos = start.offset(0, dy, 0);
            BlockState state = level.getBlockState(pos);
            BlockState above = level.getBlockState(pos.above());
            if (isNaturalSurface(state) && (above.isAir()
                    || above.is(Blocks.GRASS_BLOCK) || above.is(Blocks.TALL_GRASS)
                    || above.is(Blocks.FERN) || above.is(Blocks.LARGE_FERN)
                    || above.is(Blocks.SNOW)))
                return pos;
        }
        return null;
    }
    static boolean isNaturalSurface(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.PODZOL) || s.is(Blocks.STONE) || s.is(Blocks.MOSS_BLOCK)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SAND) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MUD) || s.is(Blocks.MYCELIUM)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.RED_SAND);
    }

    static void applyGrassFade15(ServerLevel level, BlockPos center, int maxRadius, RandomSource random) {
        int R = Math.min(maxRadius, 15);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R) continue;
            BlockPos tp = findFirstSolidBelow(level, center.offset(dx, 0, dz));
            if (tp == null || !isNaturalSurface(level.getBlockState(tp))) continue;
            double chance;
            if (dist <= 10) chance = 1.0 - (dist / 10.0) * 0.8;
            else if (dist <= 13) chance = 0.05;
            else chance = 0.01;
            if (random.nextDouble() < chance) set(level, tp, Blocks.GRASS_BLOCK);
        }
    }

    static void applySandFade15(ServerLevel level, BlockPos center, int maxRadius, RandomSource random) {
        int R = Math.min(maxRadius, 15);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R) continue;
            BlockPos tp = findFirstSolidBelow(level, center.offset(dx, 0, dz));
            if (tp == null || !isNaturalSurface(level.getBlockState(tp))) continue;
            double chance;
            if (dist <= 10) chance = 1.0 - (dist / 10.0) * 0.8;
            else if (dist <= 13) chance = 0.05;
            else chance = 0.01;
            if (random.nextDouble() < chance) set(level, tp, Blocks.SAND);
        }
    }

    static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
    }
    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
    static void setIfAir(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockState(pos).isAir()) set(level, pos, block);
    }
    static void clearBox(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos p : BlockPos.betweenClosed(from, to)) set(level, p.immutable(), Blocks.AIR);
    }
    static void placeGlowLichen(ServerLevel level, BlockPos pos, Direction dir) {
        if (!level.getBlockState(pos).isAir()) return;
        set(level, pos, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(dir), true));
    }
    static void placeDoor(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, Blocks.AIR); set(level, pos.above(), Blocks.AIR);
        set(level, pos, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }
    static void placeDoublePlant(ServerLevel level, BlockPos base, Block plant) {
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.above()).isAir()) return;
        set(level, base, plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, base.above(), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static void placeLight(ServerLevel level, BlockPos pos, int lightLevel) {
        try {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("minecraft", "light");
            Block lightBlock = BuiltInRegistries.BLOCK.get(rl);
            if (lightBlock != null && lightBlock != Blocks.AIR) {
                BlockState state = lightBlock.defaultBlockState();
                if (state.hasProperty(BlockStateProperties.LEVEL)) {
                    state = state.setValue(BlockStateProperties.LEVEL, Math.min(15, Math.max(0, lightLevel)));
                }
                if (level.getBlockState(pos).isAir()) {
                    set(level, pos, state);
                }
            }
        } catch (Exception ignored) {}
    }

    static void ensureLighting(ServerLevel level, BlockPos from, BlockPos to, RandomSource random) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        for (int x = minX + 1; x < maxX; x += 3)
            for (int z = minZ + 1; z < maxZ; z += 3) {
                BlockPos lp = new BlockPos(x, minY + 1, z);
                if (level.getBlockState(lp).isAir())
                    placeLight(level, lp, 8);
            }

        for (int y = minY + 2; y < maxY; y += 4) {
            for (int x = minX + 2; x < maxX; x += 4) {
                BlockPos lp = new BlockPos(x, y, minZ + 1);
                if (level.getBlockState(lp).isAir()) placeLight(level, lp, 5);
                lp = new BlockPos(x, y, maxZ - 1);
                if (level.getBlockState(lp).isAir()) placeLight(level, lp, 5);
            }
            for (int z = minZ + 2; z < maxZ; z += 4) {
                BlockPos lp = new BlockPos(minX + 1, y, z);
                if (level.getBlockState(lp).isAir()) placeLight(level, lp, 5);
                lp = new BlockPos(maxX - 1, y, z);
                if (level.getBlockState(lp).isAir()) placeLight(level, lp, 5);
            }
        }
    }

    static void ensureExteriorLighting(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        for (int dx = -radius; dx <= radius; dx += 4) for (int dz = -radius; dz <= radius; dz += 4) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            BlockPos tp = findFirstSolidBelow(level, center.offset(dx, 0, dz));
            if (tp == null) continue;
            BlockPos lightPos = tp.above();
            if (level.getBlockState(lightPos).isAir())
                placeLight(level, lightPos, 6);
        }
    }

    static void buildGableRoof(ServerLevel level, BlockPos ro, int w, int d, RandomSource r) {
        BlockPos roofStart = ro.offset(-1, 0, -1);
        int rw = w + 2;
        int rd = d + 2;
        int peakH = rw / 2 + 1;

        for (int dy = 0; dy < peakH; dy++) {
            int inset = dy;
            for (int dx = inset; dx < rw - inset; dx++) {
                for (int dz = 0; dz < rd; dz++) {
                    boolean isEdge = (dx == inset || dx == rw - 1 - inset);

                    if (dy > 0 && dy < peakH - 1 && r.nextInt(20) == 0) {
                        set(level, roofStart.offset(dx, dy, dz), Blocks.MOSS_BLOCK);
                        BlockPos groundBelow = findFirstSolidBelow(level, roofStart.offset(dx, -1, dz));
                        if (groundBelow != null) {
                            BlockPos carpetPos = groundBelow.above();
                            if (level.getBlockState(carpetPos).isAir()) {
                                if (r.nextInt(10) != 0)
                                    set(level, carpetPos, Blocks.MOSS_CARPET);
                            }
                        }
                        continue;
                    }

                    if (isEdge) {
                        Direction facing = (dx == inset) ? Direction.WEST : Direction.EAST;
                        Block stair = r.nextInt(4) == 0 ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.DARK_OAK_STAIRS;
                        set(level, roofStart.offset(dx, dy, dz),
                                stair.defaultBlockState().setValue(StairBlock.FACING, facing));
                    } else {
                        set(level, roofStart.offset(dx, dy, dz),
                                r.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.DARK_OAK_PLANKS);
                    }
                }
            }
        }

        for (int dz = 0; dz < rd; dz++) {
            set(level, roofStart.offset(0, -1, dz),
                    Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            set(level, roofStart.offset(rw - 1, -1, dz),
                    Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
    }

    static void buildConeRoof(ServerLevel level, BlockPos ct, int radius, RandomSource r) {
        int extR = radius + 1;
        for (int dy = 0; dy <= extR + 3; dy++) {
            int rr = Math.max(0, extR - dy);
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                double d = Math.sqrt(dx*dx + dz*dz);
                if (d > rr + 0.5 || d < rr - 0.5) continue;
                if (dy > 0 && dy < extR && r.nextInt(15) == 0) {
                    set(level, ct.offset(dx, dy, dz), Blocks.MOSS_BLOCK);
                    continue;
                }
                set(level, ct.offset(dx, dy, dz),
                        r.nextInt(4) == 0 ? Blocks.MOSS_BLOCK : Blocks.DARK_OAK_PLANKS);
            }
        }
        set(level, ct.offset(0, extR + 3, 0), Blocks.LIGHTNING_ROD);

        int baseR = extR;
        for (int dx = -baseR; dx <= baseR; dx++) for (int dz = -baseR; dz <= baseR; dz++) {
            double d = Math.sqrt(dx*dx + dz*dz);
            if (d > baseR + 0.5 || d < baseR - 0.5) continue;
            set(level, ct.offset(dx, -1, dz),
                    r.nextInt(2) == 0 ? Blocks.DARK_OAK_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB);
        }
    }

    static void buildFullSpiralStairs(ServerLevel level, BlockPos ct, int radius, int height) {
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int y = 0; y < height; y++) {
            double a = (y / (double) height) * Math.PI * 8;
            set(level, ct.offset((int) Math.round(Math.cos(a) * radius), y, (int) Math.round(Math.sin(a) * radius)),
                    Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dirs[y % 4]));
        }
    }

    static void placeChestFacing(ServerLevel level, BlockPos pos, Direction facing, String loot, RandomSource r) {
        set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        String[] p = loot.split(":", 2);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity c)
            c.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(p[0], p.length > 1 ? p[1] : p[0])), r.nextLong());
    }
    static void addLuckyBlocksToChest(ServerLevel level, BlockPos pos, int count, RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity ch)) return;
        try {
            Block lb = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "deep_lucky_block"));
            if (lb == null || lb == Blocks.AIR) return;
            ch.unpackLootTable(null);
            for (int i = 0; i < ch.getContainerSize(); i++)
                if (ch.getItem(i).isEmpty()) { ch.setItem(i, new ItemStack(lb.asItem(), count)); return; }
        } catch (Exception ignored) {}
    }
    static void addCreatorRewardToChest(ServerLevel level, BlockPos pos, RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity ch)) return;
        try {
            TestProcedure.BranchResult payload = GenerateLuckyItemProcedure.generateChestPayload(
                    level, pos.getX(), pos.getY(), pos.getZ(), 50 + r.nextInt(31), r);
            if (payload != null && payload.items != null && !payload.items.isEmpty()) {
                ch.unpackLootTable(null);
                for (int i = 0; i < ch.getContainerSize(); i++)
                    if (ch.getItem(i).isEmpty()) { ch.setItem(i, payload.items.get(0).copy()); return; }
            }
        } catch (Exception ignored) {}
    }

    static void placeCreatorRewardGlow(ServerLevel level, BlockPos pos, RandomSource r) {
        ItemStack reward = null;
        try {
            TestProcedure.BranchResult payload = GenerateLuckyItemProcedure.generateChestPayload(
                    level, pos.getX(), pos.getY(), pos.getZ(), 60 + r.nextInt(21), r);
            if (payload != null && payload.items != null && !payload.items.isEmpty())
                reward = payload.items.get(0).copy();
        } catch (Exception ignored) {}
        if (reward == null) reward = new ItemStack(Items.NETHER_STAR);
        set(level, pos, Blocks.AIR);
        ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, reward);
        ie.setGlowingTag(true);
        ie.setUnlimitedLifetime();
        level.addFreshEntity(ie);
        PixelFx.send(level, ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                20, 0.4, 0.4, 0.4, 0.03);
        placeLight(level, pos.above(), 12);
    }

    static void spawnMagicParticleEmitter(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.entity.AreaEffectCloud cloud =
                new net.minecraft.world.entity.AreaEffectCloud(level, x, y, z);
        cloud.setRadius(0.5f);
        cloud.setDuration(Integer.MAX_VALUE);
        cloud.setParticle(ParticleTypes.END_ROD);
        level.addFreshEntity(cloud);
    }

    @Deprecated
    static void spawnGlowingMarkerPig(ServerLevel level, double x, double y, double z, String name) {
        placeCreatorRewardGlow(level, new BlockPos((int)x, (int)y, (int)z), RandomSource.create());
    }

    static void placeItemFrameVertical(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
        if (!level.getBlockState(pos.relative(facing.getOpposite())).isSolid()) return;
        ItemFrame frame = new ItemFrame(level, pos, facing);
        frame.setItem(stack, false);
        frame.setInvulnerable(true);
        level.addFreshEntity(frame);
    }

    static void applyBonemeal(ServerLevel level, BlockPos center, int radius, int attempts, RandomSource random) {
        for (int i = 0; i < attempts; i++) {
            int bx = -radius + random.nextInt(radius * 2);
            int bz = -radius + random.nextInt(radius * 2);
            BlockPos tb = findFirstSolidBelow(level, center.offset(bx, 0, bz));
            if (tb == null || !level.getBlockState(tb).is(Blocks.GRASS_BLOCK)) continue;
            BlockPos above = tb.above();
            if (!level.getBlockState(above).isAir()) continue;
            int roll = random.nextInt(10);
            if (roll < 5) set(level, above, Blocks.GRASS_BLOCK);
            else if (roll < 7) set(level, above, Blocks.FERN);
            else if (roll < 9) set(level, above, FLOWERS[random.nextInt(FLOWERS.length)]);
            else placeDoublePlant(level, above, random.nextBoolean() ? Blocks.TALL_GRASS : Blocks.LARGE_FERN);
        }
    }

    static void buildGrandEntrance(ServerLevel level, BlockPos doorPos, Direction facing, int width, RandomSource r) {
        int half = width / 2;
        Direction perp = facing.getClockWise();

        for (int step = 0; step < 3; step++) {
            for (int side = -half; side <= half; side++) {
                BlockPos stepPos = doorPos.relative(facing, step + 1).relative(perp, side).below(step);
                set(level, stepPos, randomStoneBrickStairs(r).defaultBlockState()
                        .setValue(StairBlock.FACING, facing));
            }
        }

        for (int side : new int[]{-half - 1, half + 1}) {
            BlockPos pillarBase = doorPos.relative(perp, side);
            set(level, pillarBase, randomStoneBrick(r));
            set(level, pillarBase.above(), randomStoneBrick(r));
            set(level, pillarBase.above(2), Blocks.OAK_FENCE);
            set(level, pillarBase.above(3), Blocks.LANTERN);
        }

        int archY = 3;
        for (int side = -half - 1; side <= half + 1; side++)
            set(level, doorPos.above(archY).relative(perp, side), Blocks.CHISELED_STONE_BRICKS);

        for (int side : new int[]{-half - 1, half + 1}) {
            BlockPos bp = doorPos.relative(perp, side).above();
            setIfAir(level, bp.relative(facing), Blocks.STONE_BUTTON);
        }

        placeLight(level, doorPos.relative(facing).above(), 10);
    }

    static void placeWaterCauldron(ServerLevel level, BlockPos pos) {
        set(level, pos, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    static void placeEmptyCauldron(ServerLevel level, BlockPos pos) {
        set(level, pos, Blocks.CAULDRON);
    }
}