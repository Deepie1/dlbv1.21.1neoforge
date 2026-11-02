package deepluckyblock.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.blockentity.DeepLuckyBlockBlockEntity;
import deepluckyblock.init.DeepLuckyBlockModBlocks;
import deepluckyblock.procedures.GenerateLuckyItemProcedure;
import deepluckyblock.procedures.TestProcedure;

import java.util.List;

/**
 * T312-DLB-LAUNCH : Commande unifiée `/dlb` pour lancer de vrais Deep Lucky Blocks
 * infusés à une luck donnée (-100..+100), forcés en GOOD ("g") ou en BAD ("b") :
 *
 * <ul>
 *   <li>{@code /dlb g 0 10}  -> lance 10 Lucky Blocks normaux (luck infusée 0), tous garantis GOOD (positifs)</li>
 *   <li>{@code /dlb b 0 10}  -> lance 10 Lucky Blocks normaux (luck infusée 0), tous garantis BAD (négatifs)</li>
 *   <li>{@code /dlb g 10 10} -> lance 10 Lucky Blocks infusés à luck +10, tous garantis GOOD (positifs)</li>
 *   <li>{@code /dlb b -25 5} -> lance 5 Lucky Blocks maudits à luck -25, tous garantis BAD</li>
 * </ul>
 *
 * Conserve également les sous-commandes de diagnostic :
 * {@code /dlb probs <luck>}, {@code /dlb w <luck> <count>} et {@code /dlb rank <rang> [count]}.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class DlbCommand {

    private DlbCommand() {}

    private static final List<String> LUCK_SUGGESTIONS = List.of(
            "0", "10", "25", "50", "75", "100", "-25", "-50", "-100"
    );
    private static final List<String> COUNT_SUGGESTIONS = List.of(
            "1", "5", "10", "20", "50", "100"
    );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LUCK =
            (ctx, builder) -> SharedSuggestionProvider.suggest(LUCK_SUGGESTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_COUNT =
            (ctx, builder) -> SharedSuggestionProvider.suggest(COUNT_SUGGESTIONS, builder);

    private static boolean opOnly() {
        return "1".equals(System.getProperty("dlb.cmdOp", "0"));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dlb");
        if (opOnly()) {
            root.requires(src -> src.hasPermission(2));
        }

        // /dlb g <luck> [count]  (et alias /dlb good <luck> [count])
        root.then(buildBranchNode("g", true))
            .then(buildBranchNode("good", true))
            // /dlb b <luck> [count]  (et alias /dlb bad <luck> [count])
            .then(buildBranchNode("b", false))
            .then(buildBranchNode("bad", false))
            // /dlb probs <luck> (T231 : probabilités exactes de la roue à cette luck infusée)
            .then(Commands.literal("probs")
                .then(Commands.argument("luck", IntegerArgumentType.integer(-100, 100))
                    .suggests(SUGGEST_LUCK)
                    .executes(ctx -> GenerateLuckyItemProcedure.runProbsCommand(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "luck")))))
            // /dlb w <luck> <count> (T227 : simulation roue pure avec crânes inclus)
            .then(Commands.literal("w")
                .then(Commands.argument("luck", IntegerArgumentType.integer(-100, 100))
                    .suggests(SUGGEST_LUCK)
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                        .suggests(SUGGEST_COUNT)
                        .executes(ctx -> GenerateLuckyItemProcedure.runWheelCommand(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "luck"),
                                IntegerArgumentType.getInteger(ctx, "count"))))))
            // /dlb rank <rang> [count] (ancien mode debug à rang forcé direct)
            .then(Commands.literal("rank")
                .then(Commands.argument("rang", StringArgumentType.word())
                    .executes(ctx -> GenerateLuckyItemProcedure.runDebugCommand(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "rang"),
                            1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .suggests(SUGGEST_COUNT)
                        .executes(ctx -> GenerateLuckyItemProcedure.runDebugCommand(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "rang"),
                                IntegerArgumentType.getInteger(ctx, "count"))))));

        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBranchNode(String literalName, boolean forceGood) {
        return Commands.literal(literalName)
                .then(Commands.argument("luck", IntegerArgumentType.integer(-100, 100))
                        .suggests(SUGGEST_LUCK)
                        .executes(ctx -> launchLuckyBlocks(
                                ctx.getSource(),
                                forceGood,
                                IntegerArgumentType.getInteger(ctx, "luck"),
                                1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .suggests(SUGGEST_COUNT)
                                .executes(ctx -> launchLuckyBlocks(
                                        ctx.getSource(),
                                        forceGood,
                                        IntegerArgumentType.getInteger(ctx, "luck"),
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    /**
     * Pose (si l'emplacement est libre) puis casse {@code count} Deep Lucky Blocks
     * infusés à {@code infusedLuck} (-100..+100), garantis GOOD ({@code forceGood=true})
     * ou BAD ({@code forceGood=false}).
     */
    private static int launchLuckyBlocks(CommandSourceStack src, boolean forceGood, int infusedLuck, int count) {
        ServerLevel level = src.getLevel();
        deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        Vec3 origin = src.getPosition();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            player = FakePlayerFactory.getMinecraft(level);
            player.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        }

        // Orientation horizontale devant l'exécutant (grille de 5 colonnes espacées de 2 blocs
        // pour que tous les hologrammes de roulette restent parfaitement lisibles)
        float yawRad = player.getYRot() * ((float) Math.PI / 180.0F);
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);
        double rightX = fwdZ;
        double rightZ = -fwdX;

        final int baseY = Mth.floor(origin.y);
        final int clampedLuck = Mth.clamp(infusedLuck, -100, 100);
        final String luckDesc = clampedLuck == 0
                ? "normaux (luck 0)"
                : ("infusés à luck " + (clampedLuck > 0 ? "+" + clampedLuck : String.valueOf(clampedLuck)));
        final String modeLabel = forceGood ? "§a§lGOOD§r§a (positifs)" : "§c§lBAD§r§c (négatifs)";
        final String prefix = forceGood ? "§a[DLB] " : "§c[DLB] ";

        src.sendSuccess(() -> Component.literal(prefix + "Lancement de §f" + count
                + (forceGood ? "§a" : "§c") + " Lucky Block(s) " + luckDesc + " -> " + modeLabel + " !"), false);

        final int startTick = TestProcedure.currentTick(level);
        final Block luckyBlock = DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get();
        final BlockState lbState = luckyBlock.defaultBlockState();
        final String branchTag = forceGood ? "g" : "b";

        for (int i = 0; i < count; i++) {
            final int index = i;
            double forwardDist;
            double sideOffset;
            if (count == 1) {
                forwardDist = 2.5;
                sideOffset = 0.0;
            } else {
                int cols = Math.min(5, count);
                int col = index % 5;
                int row = (index / 5) % 6;
                double centerCol = (cols - 1) * 0.5;
                sideOffset = (col - centerCol) * 2.0;
                forwardDist = 3.0 + row * 2.0;
            }

            final int px = Mth.floor(origin.x + fwdX * forwardDist + rightX * sideOffset);
            final int pz = Mth.floor(origin.z + fwdZ * forwardDist + rightZ * sideOffset);
            final int py = findPlacementY(level, px, baseY, pz);
            final BlockPos pos = new BlockPos(px, py, pz);

            final int placeTick = startTick + index * 5;
            final int breakTick = placeTick + 6;

            // Étape 1 : Apparition visuelle du Lucky Block infusé (uniquement si le bloc est de l'air pur,
            // afin de ne JAMAIS écraser de coffre ou de décor existant)
            TestProcedure.schedule(level, placeTick, () -> {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, lbState, 3);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
                        dlBe.setLuck(clampedLuck);
                        dlBe.setSlbLuckBonus(0);
                        dlBe.setChanged();
                    }
                    if (be != null) {
                        be.getPersistentData().putInt("Luck", clampedLuck);
                        be.getPersistentData().putString("dlbForcedBranch", branchTag);
                        be.getPersistentData().putBoolean("dlbAutoBreak", true);
                        be.setChanged();
                    }
                    level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.85F, 1.15F);
                } else {
                    // Emplacement occupé : on déclenche directement la casse sans toucher au bloc du monde
                    level.levelEvent(2001, pos, Block.getId(lbState));
                    TestProcedure.executeForcedInfusedOutcome(level, px, py, pz, clampedLuck, forceGood);
                }
            });

            // Étape 2 : Casse automatique du Lucky Block infusé 6 ticks (0,3 s) après sa pose
            TestProcedure.schedule(level, breakTick, () -> {
                if (!level.getBlockState(pos).is(luckyBlock)) {
                    // Soit déjà cassé manuellement (géré par dlbForcedBranch), soit non posé car non-air
                    return;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null && !be.getPersistentData().getBoolean("dlbAutoBreak")) {
                    // Ne casse jamais un Lucky Block posé manuellement par un joueur
                    return;
                }
                level.removeBlock(pos, false);
                level.levelEvent(2001, pos, Block.getId(lbState));
                TestProcedure.executeForcedInfusedOutcome(level, px, py, pz, clampedLuck, forceGood);
            });
        }

        return count;
    }

    /** Trouve une hauteur Y propre au ras du sol proche de {@code refY} (±4 blocs). */
    private static int findPlacementY(ServerLevel level, int x, int refY, int z) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -4; dy--) {
            int y = refY + dy;
            mpos.set(x, y, z);
            BlockState here = level.getBlockState(mpos);
            mpos.set(x, y - 1, z);
            BlockState below = level.getBlockState(mpos);
            if (here.isAir() && !below.isAir() && below.getFluidState().isEmpty()) {
                return y;
            }
        }
        return refY;
    }
}
