package deepluckyblock.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import deepluckyblock.client.EnchantAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture l'aura d'un item (en main / GUI) AVANT son rendu, pour que
 * RenderTypeEnchantMixin recolore le glint. HEAD définit la couleur,
 * TAIL la réinitialise.
 *
 * <p>T310-GLINT-FLASH : En vue en main ({@code FIRST_PERSON_*} / {@code THIRD_PERSON_*},
 * jamais dans l'inventaire {@code GUI}), détecte la fenêtre d'attaque ou de tir actif
 * pour teinter temporairement le glint avec la couleur d'élément de {@code DeepGlintColor},
 * puis restaure instantanément la couleur d'origine de l'item dès la fin du coup.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererEnchantMixin {

    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;"
            + "ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "IILnet/minecraft/client/resources/model/BakedModel;)V";

    @Unique private static boolean dlbWasSwinging = false;
    @Unique private static int dlbSwingCycle = 0;
    @Unique private static long dlbMeleeFlashUntil = 0L;
    @Unique private static boolean dlbWasMainCrossbowCharged = false;
    @Unique private static boolean dlbWasOffCrossbowCharged = false;
    @Unique private static boolean dlbWasUsingRanged = false;
    @Unique private static long dlbRangedShotFlashUntil = 0L;

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void dlbCapture(ItemStack stack, ItemDisplayContext displayContext,
                            boolean inverted, PoseStack poseStack, MultiBufferSource buffer,
                            int light, int overlay, BakedModel model, CallbackInfo ci) {
        LocalPlayer heldOwner = dlbResolveHeldOwner(stack, displayContext);
        if (heldOwner != null) {
            // T333-UNIQUE-CONTEXTUAL-GLOW : Déclenchement selon les conditions uniques des enchantements de l'item tenu
            EnchantAura.captureContextual(heldOwner, stack, false);
        } else {
            EnchantAura.capture(stack);
        }
        if (EnchantAura.DEBUG && EnchantAura.DEBUG_VERBOSE) {
            deepluckyblock.util.DebugLog.out("[MIXIN] ItemRenderer.render() HEAD — stack="
                    + (stack == null ? "null" : stack.getItem().getDescriptionId())
                    + " hasFoil=" + (stack != null && stack.hasFoil())
                    + " intense=" + EnchantAura.isCurrentIntense()
                    + " captée=" + Integer.toHexString(EnchantAura.currentRgb()));
        }
    }

    @Unique
    private static LocalPlayer dlbResolveHeldOwner(ItemStack stack, ItemDisplayContext ctx) {
        if (stack == null || stack.isEmpty() || ctx == null) return null;
        boolean inHandContext = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!inHandContext) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc == null ? null : mc.player;
            if (player == null || player.level() == null) return null;
            if (stack == player.getMainHandItem() || stack == player.getOffhandItem()) {
                return player;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Unique
    private static boolean dlbCheckActiveAction(ItemStack stack, ItemDisplayContext ctx) {
        if (stack == null || stack.isEmpty() || ctx == null) return false;
        boolean inHandContext = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!inHandContext) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc == null ? null : mc.player;
            if (player == null || player.level() == null) return false;

            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            boolean isMain = (stack == main);
            boolean isOff = !isMain && (stack == off);
            if (!isMain && !isOff) return false;

            long gameTime = player.level().getGameTime();

            // 1) Suivi du cycle de frappe (mêlée / minage) avec rémanence courte (5 ticks)
            // pour que l'éclat intense soit nettement visible même sur les armes très rapides
            boolean swingingNow = player.swinging || player.attackAnim > 0.0F;
            if (swingingNow && !dlbWasSwinging) {
                dlbSwingCycle++;
            }
            dlbWasSwinging = swingingNow;

            InteractionHand activeArm = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
            boolean activeHandMatch = (isMain && activeArm == InteractionHand.MAIN_HAND)
                    || (isOff && activeArm == InteractionHand.OFF_HAND);
            if (swingingNow && activeHandMatch) {
                dlbMeleeFlashUntil = gameTime + 5L;
                return true;
            }
            if (gameTime <= dlbMeleeFlashUntil && activeHandMatch) {
                return true;
            }

            // 2) Détection tir d'arbalète (déchargement du trait) & relâchement arc/trident
            boolean mainCbCharged = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
            boolean offCbCharged = off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off);
            if ((dlbWasMainCrossbowCharged && !mainCbCharged && main.getItem() instanceof CrossbowItem)
                    || (dlbWasOffCrossbowCharged && !offCbCharged && off.getItem() instanceof CrossbowItem)) {
                dlbSwingCycle++;
                dlbRangedShotFlashUntil = gameTime + 12L;
            }
            dlbWasMainCrossbowCharged = mainCbCharged;
            dlbWasOffCrossbowCharged = offCbCharged;

            boolean usingNow = player.isUsingItem();
            if (usingNow && player.getUseItem() == stack && player.getTicksUsingItem() >= 4) {
                dlbWasUsingRanged = true;
                return true;
            }
            if (dlbWasUsingRanged && !usingNow) {
                dlbWasUsingRanged = false;
                dlbSwingCycle++;
                dlbRangedShotFlashUntil = gameTime + 12L;
            }
            return gameTime <= dlbRangedShotFlashUntil
                    && (stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
                        || stack.getItem() instanceof net.minecraft.world.item.TridentItem);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = RENDER_METHOD, at = @At("TAIL"))
    private void dlbClear(ItemStack stack, ItemDisplayContext displayContext,
                          boolean inverted, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay, BakedModel model, CallbackInfo ci) {
        EnchantAura.clear();
    }
}
