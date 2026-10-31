package deepluckyblock.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import deepluckyblock.entity.CrimsonButterflyEntity;
import deepluckyblock.client.model.animations.butterflyAnimation;
import deepluckyblock.client.model.Modelbutterfly;
import deepluckyblock.procedures.CrimsonButterflyModelVisualScaleProcedure;

public class CrimsonButterflyRenderer extends MobRenderer<CrimsonButterflyEntity, CrimsonButterflyRenderer.AnimatedModel> {

    // Texture
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/crimson_butterfly.png");

    public CrimsonButterflyRenderer(EntityRendererProvider.Context context) {
        super(context, new AnimatedModel(context.bakeLayer(Modelbutterfly.LAYER_LOCATION)), 0.1f);
    }

    @Override
    public ResourceLocation getTextureLocation(CrimsonButterflyEntity entity) {
        return TEXTURE;
    }

    /**
     * COPIE DU SLIME KING : active la transparence (alpha de la texture PNG).
     */
    @Override
    protected RenderType getRenderType(CrimsonButterflyEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    /**
     * ⚠️ APPELLE LA PROCEDURE MCreator pour récupérer la taille.
     * La procedure CrimsonButterflyModelVisualScaleProcedure.execute(entity) retourne
     * la taille (scale) du papillon entre 0.5 et 1.3 basée sur l'UUID.
     */
    @Override
    protected void scale(CrimsonButterflyEntity entity, PoseStack poseStack, float partialTick) {
        // Récupère la taille via la procedure MCreator (cast double → float)
        double size = CrimsonButterflyModelVisualScaleProcedure.execute(entity);
        float scale = (float) size;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected void setupRotations(CrimsonButterflyEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float defaultScale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, defaultScale);
    }

    /**
     * ⚠️ Full bright : le papillon est toujours visible, même dans le noir.
     */
    @Override
    public void render(CrimsonButterflyEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, 0xF000F0);
    }

    protected static final class AnimatedModel extends Modelbutterfly<CrimsonButterflyEntity> {
        private final ModelPart root;
        private final HierarchicalModel<CrimsonButterflyEntity> animator;

        public AnimatedModel(ModelPart root) {
            super(root);
            this.root = root;
            this.animator = new HierarchicalModel<CrimsonButterflyEntity>() {
                @Override
                public ModelPart root() {
                    return root;
                }

                @Override
                public void setupAnim(CrimsonButterflyEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                    this.root().getAllParts().forEach(ModelPart::resetPose);
                    // Offset d'animation pour désynchroniser les papillons
                    int offset = entity.getAnimationOffset();
                    this.animate(entity.animationState0, butterflyAnimation.butterfly_fly, ageInTicks + offset, 1.0F);
                }

                @Override
                public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                                            int packedLight, int packedOverlay, int packedColor) {
                    // vide (comme Slime King)
                }
            };
        }

        @Override
        public void setupAnim(CrimsonButterflyEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
