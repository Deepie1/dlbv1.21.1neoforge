package deepluckyblock.mixin;

import deepluckyblock.compat.LuckyXpEventPoolCompatibility;

import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Redirige le pool exact utilise par les showers JACKPOT de Lucky XP. */
@Pseudo
@Mixin(targets = "com.lwi.luckyxp.event.LuckyBlockShower", remap = false)
public abstract class LuckyXpShowerMixin {

    @Redirect(
            method = "shower",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lwi/luckytweaks/api/LuckyTweaksApi;getLuckyBlockIds(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static List<ResourceLocation> deepLuckyBlock$jackpotPool(
            ResourceLocation dimension) {
        return LuckyXpEventPoolCompatibility.dimensionPool(
                dimension, "SERVER_JACKPOT");
    }
}
