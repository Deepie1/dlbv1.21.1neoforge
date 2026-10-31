package deepluckyblock.mixin;

import deepluckyblock.compat.LuckyXpEventPoolCompatibility;

import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Redirige le pool du resultat serveur exact de la roulette Lucky XP. */
@Pseudo
@Mixin(targets = "com.lwi.luckyxp.event.EventRolls", remap = false)
public abstract class LuckyXpEventRollsMixin {

    @Redirect(
            method = "randomBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lwi/luckytweaks/api/LuckyTweaksApi;getLuckyBlockIds(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static List<ResourceLocation> deepLuckyBlock$serverResultPool(
            ResourceLocation dimension) {
        return LuckyXpEventPoolCompatibility.dimensionPool(
                dimension, "SERVER_RESULT");
    }
}
