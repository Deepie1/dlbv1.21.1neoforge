package deepluckyblock.procedures;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Commande /dlbaudit — lance l'audit de montée en niveau depuis le jeu.
 *
 *   /dlbaudit           → audit jusqu'au niveau 200 (défaut)
 *   /dlbaudit <niveau>  → audit jusqu'au niveau demandé (1 à 1000)
 *
 * Réservée aux opérateurs (niveau de permission 2) : l'audit parcourt les
 * 395 enchantements sur 22 paliers, ce n'est pas une commande de joueur.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class EnchantmentAuditCommand {

    private EnchantmentAuditCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("dlbaudit")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    run(ctx.getSource(), EnchantmentLevelAuditProcedure.DEFAULT_MAX_LEVEL);
                    return 1;
                })
                .then(Commands.argument("niveau", IntegerArgumentType.integer(1, 1000))
                    .executes(ctx -> {
                        run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "niveau"));
                        return 1;
                    }))
        );
    }

    private static void run(net.minecraft.commands.CommandSourceStack source, int maxLevel) {
        // PORT 1.21.1 : source.getEntity() renvoie Entity (pas ServerPlayer).
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        EnchantmentLevelAuditProcedure.Report report = EnchantmentLevelAuditProcedure.run(maxLevel);
        report.log();
        if (player != null) {
            report.tell(player);
        } else {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    "Audit terminé : " + report.total + " enchantements, "
                            + report.failures.size() + " crash(s). Voir les logs."), true);
        }
    }
}
