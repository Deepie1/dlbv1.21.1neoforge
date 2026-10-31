package deepluckyblock.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import deepluckyblock.client.EnchantAura;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture l'aura d'un item (en main / GUI) AVANT son rendu, pour que
 * RenderTypeEnchantMixin recolore le glint. HEAD définit la couleur,
 * TAIL la réinitialise.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererEnchantMixin {

    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;"
            + "ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "IILnet/minecraft/client/resources/model/BakedModel;)V";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void dlbCapture(ItemStack stack, ItemDisplayContext displayContext,
                            boolean inverted, PoseStack poseStack, MultiBufferSource buffer,
                            int light, int overlay, BakedModel model, CallbackInfo ci) {
        EnchantAura.capture(stack);
        if (EnchantAura.DEBUG && EnchantAura.DEBUG_VERBOSE) {
            System.out.println("[MIXIN] ItemRenderer.render() HEAD — stack="
                    + (stack == null ? "null" : stack.getItem().getDescriptionId())
                    + " hasFoil=" + (stack != null && stack.hasFoil())
                    + " captée=" + Integer.toHexString(EnchantAura.currentRgb()));
        }
    }

    @Inject(method = RENDER_METHOD, at = @At("TAIL"))
    private void dlbClear(ItemStack stack, ItemDisplayContext displayContext,
                          boolean inverted, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay, BakedModel model, CallbackInfo ci) {
        EnchantAura.clear();
    }
}
