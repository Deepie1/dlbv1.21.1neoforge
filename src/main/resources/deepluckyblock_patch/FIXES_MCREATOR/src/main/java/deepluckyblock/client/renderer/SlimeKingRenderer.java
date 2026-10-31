package deepluckyblock.client.renderer;

import deepluckyblock.entity.SlimeKingEntity;
import deepluckyblock.client.model.Modelslime_king;
import deepluckyblock.client.model.animations.slime_kingAnimation;
import deepluckyblock.client.model.animations.slime_kingattkAnimation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

public class SlimeKingRenderer extends MobRenderer<SlimeKingEntity, SlimeKingRenderer.AnimatedModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/slimeking.png");

    public SlimeKingRenderer(EntityRendererProvider.Context context) {
        super(context, new AnimatedModel(context.bakeLayer(Modelslime_king.LAYER_LOCATION)), 4.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(SlimeKingEntity entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(SlimeKingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    protected void scale(SlimeKingEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(4.0f, 4.0f, 4.0f);
    }

    @Override
    protected void setupRotations(SlimeKingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float defaultScale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, defaultScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(0.0f));
    }

    protected static final class AnimatedModel extends Modelslime_king<SlimeKingEntity> {
        private final ModelPart root;
        private final HierarchicalModel<SlimeKingEntity> animator;

        public AnimatedModel(ModelPart root) {
            super(root);
            this.root = root;
            this.animator = new HierarchicalModel<SlimeKingEntity>() {
                @Override
                public ModelPart root() {
                    return root;
                }

                @Override
                public void setupAnim(SlimeKingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                    this.root().getAllParts().forEach(ModelPart::resetPose);

                    float walkSpeed = entity.getWalkAnimSpeed();
                    this.animate(entity.animationState1, slime_kingAnimation.slimewalk, ageInTicks, walkSpeed);

                    float atkSpeed = entity.getAttackAnimSpeed();
                    this.animate(entity.animationState0, slime_kingattkAnimation.slimeattack, ageInTicks, atkSpeed);
                }

                @Override
                public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                                            int packedLight, int packedOverlay, int packedColor) {
                }
            };
        }

        @Override
        public void setupAnim(SlimeKingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}