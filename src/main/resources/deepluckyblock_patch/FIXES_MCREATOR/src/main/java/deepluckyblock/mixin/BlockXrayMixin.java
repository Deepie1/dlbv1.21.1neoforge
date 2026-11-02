package deepluckyblock.mixin;

import deepluckyblock.client.MinersEyeClientXray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * T341-MINERS-EYE-XRAY : Règles d'exposition de faces pour la sphère X-Ray locale de {@code miners_eye} :
 * <ul>
 *   <li>Minerais dans la sphère : affiche toutes les faces en contact avec la roche effacée/semi-transparente.</li>
 *   <li>Roche semi-transparente dans la sphère : n'affiche que la paroi directement au contact de l'air
 *       (zéro empilement de feuilles translucides roche-contre-roche).</li>
 *   <li>Roche opaque juste à l'extérieur de la sphère (bord à R+1) : affiche sa face interne pour refermer
 *       proprement l'horizon de la sphère sans trou vers le vide.</li>
 * </ul>
 */
@Mixin(Block.class)
public abstract class BlockXrayMixin {

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void dlb$minersEyeShouldRenderFace(BlockState state, BlockGetter level, BlockPos pos,
                                                      Direction face, BlockPos neighborPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!MinersEyeClientXray.isVanillaMeshXrayActive()) {
            return;
        }
        boolean selfInSphere = MinersEyeClientXray.isPosInSphere(pos);
        boolean neighborInSphere = MinersEyeClientXray.isPosInSphere(neighborPos);
        if (!selfInSphere && !neighborInSphere) {
            return;
        }

        BlockState neighbor = level.getBlockState(neighborPos);
        if (selfInSphere) {
            if (MinersEyeClientXray.isPreservedBlock(state)) {
                // Minerai : révèle sa face dès que le voisin dans la sphère est de la roche atténuée
                if (neighborInSphere && !MinersEyeClientXray.isPreservedBlock(neighbor)) {
                    cir.setReturnValue(true);
                }
                return;
            }
            // Roche dans la sphère : seule la paroi au contact direct de l'air/non-occultant est dessinée en translucide
            boolean exposedToAir = neighbor.isAir() || (!neighbor.canOcclude() && neighbor.getFluidState().isEmpty());
            cir.setReturnValue(exposedToAir);
        } else {
            // Bloc hors sphère collé au bord de la sphère : referme proprement la paroi si le voisin interne est atténué
            if (!MinersEyeClientXray.isPreservedBlock(neighbor)) {
                cir.setReturnValue(true);
            }
        }
    }
}
