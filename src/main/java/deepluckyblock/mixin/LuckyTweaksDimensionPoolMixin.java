package deepluckyblock.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajoute le Deep Lucky Block au pool dimensionnel expose par Lucky Tweaks.
 *
 * Lucky XP utilise precisement cette surcharge pour sa roulette et ses showers.
 * Le bloc Deep n'est pas dans le registre worldgen du mod Lucky Block : il est
 * donc reconnu par la liste globale, mais absent de la liste par dimension.
 * Cette injection corrige uniquement cette liste ; elle n'ajoute aucune
 * generation naturelle.
 *
 * @Pseudo + target texte gardent Lucky Tweaks totalement facultatif.
 */
@Pseudo
@Mixin(targets = "com.lwi.luckytweaks.api.LuckyTweaksApi", remap = false)
public abstract class LuckyTweaksDimensionPoolMixin {

    private static final ResourceLocation DEEP_LUCKY_BLOCK_ID =
            ResourceLocation.tryParse("deep_lucky_block:deep_lucky_block");

    @Inject(
            method = "getLuckyBlockIds(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void deepLuckyBlock$includeInDimensionPool(
            ResourceLocation dimension,
            CallbackInfoReturnable<List<ResourceLocation>> callback) {
        if (DEEP_LUCKY_BLOCK_ID == null) return;

        Block deepBlock = BuiltInRegistries.BLOCK.get(DEEP_LUCKY_BLOCK_ID);
        if (deepBlock == null || deepBlock == Blocks.AIR) return;

        List<ResourceLocation> original = callback.getReturnValue();
        if (original == null || original.contains(DEEP_LUCKY_BLOCK_ID)) return;

        List<ResourceLocation> extended = new ArrayList<>(original);
        extended.add(DEEP_LUCKY_BLOCK_ID);
        callback.setReturnValue(extended);
    }
}
