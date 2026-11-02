package deepluckyblock.mixin;

import deepluckyblock.client.MinersEyeClientXray;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * T341-MINERS-EYE-XRAY : Révèle les parois latérales et inférieures de l'eau, de la lave et de tous
 * les liquides moddés ({@link LiquidBlockRenderer}) lorsqu'ils touchent de la roche atténuée dans la
 * sphère de {@code miners_eye}.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererXrayMixin {

    @Inject(method = "isFaceOccludedByState", at = @At("HEAD"), cancellable = true)
    private static void dlb$minersEyeLiquidNotOccluded(BlockGetter level, Direction face, float height,
                                                       BlockPos neighborPos, BlockState neighborState,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!MinersEyeClientXray.isVanillaMeshXrayActive()) {
            return;
        }
        if (MinersEyeClientXray.isPosInSphere(neighborPos) && !MinersEyeClientXray.isPreservedBlock(neighborState)) {
            cir.setReturnValue(false);
        }
    }
}
