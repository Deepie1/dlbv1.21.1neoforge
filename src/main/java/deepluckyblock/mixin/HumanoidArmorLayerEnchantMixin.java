package deepluckyblock.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import deepluckyblock.client.EnchantAura;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture la piece d'armure en cours de rendu pour que l'aura colorée
 * s'applique au glint de l'armure portée. HEAD : définit la couleur ;
 * TAIL : la réinitialise.
 *
 * NOTE v8 : le rendu des armures custom (Samouss / Lunettes du Patron)
 * passe par le mécanisme OFFICIEL NeoForge 1.21.1 — le hook
 * getHumanoidArmorModel de l'item est appelé par HumanoidArmorLayer
 * (getArmorModelHook -> ClientHooks.getArmorModel) et le rendu est declenche
 * par le ArmorMaterial.Layer declare dans DeepLuckyBlockModItems (sans
 * layer, NeoForge ne rend rien d'ou l'invisibilite). Plus de workaround
 * de rendu dans ce mixin.
 *
 * <p><b>FIX (glint armure violet malgré l'aura) :</b> la cible visait
 * l'ancienne surcharge à 6 paramètres de {@code renderArmorPiece}, marquée
 * {@code @Deprecated} dans les sources NeoForge 1.21.1. Cette surcharge
 * n'est PLUS appelée par le vrai chemin de rendu :
 * {@code HumanoidArmorLayer#render(...)} appelle directement la surcharge
 * PRIVÉE à 12 paramètres (avec {@code limbSwing, limbSwingAmount,
 * partialTick, ageInTicks, netHeadYaw, headPitch}), qui est celle qui
 * calcule le modèle, applique la teinte/trim ET appelle
 * {@code renderGlint(...) -> buffer.getBuffer(RenderType.armorEntityGlint())}.
 * Comme notre {@code @Inject} ciblait la surcharge jamais invoquée,
 * {@code EnchantAura.capture(...)} n'était JAMAIS appelé pour l'armure
 * portée -> {@code EnchantAura.currentRgb() == 0} -> le
 * {@code RenderTypeEnchantMixin} laissait passer le glint violet vanilla
 * de {@code RenderType.armorEntityGlint()} sans le retoucher.
 * ({@code injectors.defaultRequire: 0} dans {@code deep_lucky_block.mixins.json}
 * explique l'absence de tout crash/erreur au chargement : un {@code @Inject}
 * qui ne trouve pas sa cible échoue silencieusement.)
 *
 * <p>Fix : on retargete l'injection sur la VRAIE surcharge à 12 paramètres
 * (descriptor exact après erasure des génériques {@code T extends
 * LivingEntity} et {@code A extends HumanoidModel<T>} -> {@code
 * LivingEntity} / {@code HumanoidModel}). Même logique de capture/clear
 * qu'avant, juste la bonne cible.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerEnchantMixin {

    /**
     * Surcharge PRIVÉE réellement invoquée par {@code render(...)} :
     * {@code private void renderArmorPiece(PoseStack, MultiBufferSource,
     * T, EquipmentSlot, int, A, float limbSwing, float limbSwingAmount,
     * float partialTick, float ageInTicks, float netHeadYaw, float headPitch)}
     */
    private static final String RENDER_ARMOR_PIECE =
            "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/entity/EquipmentSlot;"
            + "ILnet/minecraft/client/model/HumanoidModel;"
            + "FFFFFF)V";

    @Inject(method = RENDER_ARMOR_PIECE, at = @At("HEAD"))
    private void deepEnchant_captureArmor(PoseStack poseStack, MultiBufferSource buffer,
                                           LivingEntity livingEntity, EquipmentSlot slot,
                                           int packedLight, HumanoidModel<?> model,
                                           float limbSwing, float limbSwingAmount, float partialTick,
                                           float ageInTicks, float netHeadYaw, float headPitch,
                                           CallbackInfo ci) {
        try {
            var piece = livingEntity.getItemBySlot(slot);
            boolean armorProcWindow = livingEntity.hurtTime > 0;
            EnchantAura.captureWithFlash(piece, armorProcWindow, livingEntity.tickCount / 10, true);
            if (EnchantAura.DEBUG && EnchantAura.DEBUG_VERBOSE) {
                deepluckyblock.util.DebugLog.out("[MIXIN] renderArmorPiece() HEAD slot=" + slot
                        + " item=" + (piece == null ? "null" : piece.getItem().getDescriptionId())
                        + " hurt=" + armorProcWindow
                        + " captée=" + Integer.toHexString(EnchantAura.currentRgb()));
            }
        } catch (Throwable t) {
            EnchantAura.clear();
            deepluckyblock.util.DebugLog.out("[MIXIN] renderArmorPiece() HEAD — exception: " + t);
        }
    }

    @Inject(method = RENDER_ARMOR_PIECE, at = @At("TAIL"))
    private void deepEnchant_clearArmor(PoseStack poseStack, MultiBufferSource buffer,
                                         LivingEntity livingEntity, EquipmentSlot slot,
                                         int packedLight, HumanoidModel<?> model,
                                         float limbSwing, float limbSwingAmount, float partialTick,
                                         float ageInTicks, float netHeadYaw, float headPitch,
                                         CallbackInfo ci) {
        EnchantAura.clear();
    }
}
