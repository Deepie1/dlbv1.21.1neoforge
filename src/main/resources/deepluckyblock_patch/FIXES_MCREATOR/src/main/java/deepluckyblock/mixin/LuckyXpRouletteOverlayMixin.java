package deepluckyblock.mixin;

import deepluckyblock.compat.LuckyXpEventPoolCompatibility;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Corrige les deux parties clientes de la roulette Lucky XP :
 * - pool des cases qui defilent ;
 * - icone Deep, rendue via son ItemStack plutot que via la texture particle du
 *   modele de bloc (chemin officiel reserve aux namespaces hors "lucky").
 */
@Pseudo
@Mixin(targets = "com.lwi.luckyxp.client.RouletteOverlay", remap = false)
public abstract class LuckyXpRouletteOverlayMixin {

    @Redirect(
            method = "luckyIds",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lwi/luckytweaks/api/LuckyTweaksApi;getLuckyBlockIds(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static List<ResourceLocation> deepLuckyBlock$visibleRoulettePool(
            ResourceLocation dimension) {
        return LuckyXpEventPoolCompatibility.dimensionPool(
                dimension, "CLIENT_ROULETTE");
    }

    @Inject(
            method = "renderBlockIcon",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void deepLuckyBlock$renderOwnItemIcon(
            GuiGraphics graphics, ResourceLocation id,
            int centerX, int centerY, int size,
            CallbackInfo callback) {
        if (!LuckyXpEventPoolCompatibility.isDeepLuckyBlock(id)) return;

        Block block = LuckyXpEventPoolCompatibility.deepLuckyBlock();
        if (block == null) return;

        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                centerX - size / 2.0,
                centerY - size / 2.0,
                0.0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(new ItemStack(block), 0, 0);
        graphics.pose().popPose();
        LuckyXpEventPoolCompatibility.logClientIconRendered();
        callback.cancel();
    }
}
