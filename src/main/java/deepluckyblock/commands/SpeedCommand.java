package deepluckyblock.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import deepluckyblock.DeepLuckyBlockMod;

/** /speed 1..10 : vitesse de vol, sans modifier le paramètre utilisé par la FOV. */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class SpeedCommand {
    private SpeedCommand() {}
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("speed")
            .requires(s -> s.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("vitesse", IntegerArgumentType.integer(1, 10)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                int speed = IntegerArgumentType.getInteger(ctx, "vitesse");
                player.getAbilities().setFlyingSpeed(0.05F * speed);
                // walkingSpeed intervient dans le calcul client de FOV : le conserver
                // à sa valeur vanilla supprime le zoom signalé avec T194.
                player.getAbilities().setWalkingSpeed(0.10F);
                player.onUpdateAbilities();
                ctx.getSource().sendSuccess(() -> Component.literal("Vitesse de vol réglée sur " + speed + "/10"), false);
                return speed;
            })));
    }
}
