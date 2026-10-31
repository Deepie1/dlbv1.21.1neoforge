package deepluckyblock.mixin;

import deepluckyblock.client.EnchantGlintTyped;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirige les RenderType de glint VANILLA vers la version teintée de l'aura
 * (EnchantGlintTyped). Si aucune couleur n'est capturée (item non enchanté),
 * on laisse le glint violet vanilla.
 *
 * 1.21.1 : RenderType n'expose plus que 5 methods glint —
 * glint(), glintTranslucent(), entityGlint(), entityGlintDirect(), armorEntityGlint().
 * glintDirect() et armorGlint() ont ete supprimees (ne plus les injecter).
 */
@Mixin(RenderType.class)
public abstract class RenderTypeEnchantMixin {

    @Inject(method = "glint", at = @At("HEAD"), cancellable = true)
    private static void enchantGlint(CallbackInfoReturnable<RenderType> cir) {
        RenderType rt = EnchantGlintTyped.currentRenderType("glint");
        if (rt != null) cir.setReturnValue(rt);
    }

    @Inject(method = "glintTranslucent", at = @At("HEAD"), cancellable = true)
    private static void enchantGlintTranslucent(CallbackInfoReturnable<RenderType> cir) {
        RenderType rt = EnchantGlintTyped.currentRenderType("glintTranslucent");
        if (rt != null) cir.setReturnValue(rt);
    }

    @Inject(method = "entityGlint", at = @At("HEAD"), cancellable = true)
    private static void enchantEntityGlint(CallbackInfoReturnable<RenderType> cir) {
        RenderType rt = EnchantGlintTyped.currentRenderType("entityGlint");
        if (rt != null) cir.setReturnValue(rt);
    }

    @Inject(method = "entityGlintDirect", at = @At("HEAD"), cancellable = true)
    private static void enchantEntityGlintDirect(CallbackInfoReturnable<RenderType> cir) {
        RenderType rt = EnchantGlintTyped.currentRenderType("entityGlintDirect");
        if (rt != null) cir.setReturnValue(rt);
    }

    // ---- Armure (halo sur les pieces portees) ----

    @Inject(method = "armorEntityGlint", at = @At("HEAD"), cancellable = true)
    private static void enchantArmorEntityGlint(CallbackInfoReturnable<RenderType> cir) {
        RenderType rt = EnchantGlintTyped.currentRenderType("armorEntityGlint");
        if (rt != null) cir.setReturnValue(rt);
    }
}
