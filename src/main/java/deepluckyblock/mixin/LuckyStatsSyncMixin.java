package deepluckyblock.mixin;

import deepluckyblock.compat.LuckyStatsSyncCompatibility;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Synchronise le client juste apres l'ecriture officielle d'un percentile.
 *
 * L'injection vise PlayerStatsAccess.addLuckPercent : elle couvre donc les
 * resultats Deep ET les Lucky Blocks originaux, sans modifier leur calcul ni
 * les regles d'exclusion des tirages deterministes.
 *
 * @Pseudo + target texte gardent Lucky Stats totalement facultatif.
 */
@Pseudo
@Mixin(targets = "com.lwi.luckystats.util.PlayerStatsAccess", remap = false)
public abstract class LuckyStatsSyncMixin {

    @Inject(
            method = "addLuckPercent(Lnet/minecraft/world/entity/player/Player;D)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void deepLuckyBlock$syncAfterLuckSample(
            Player player, double percentile, CallbackInfo callback) {
        if (player instanceof ServerPlayer) {
            LuckyStatsSyncCompatibility.sync((ServerPlayer) player);
        }
    }
}
