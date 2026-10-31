package deepluckyblock.client.renderer;

import deepluckyblock.entity.EndKnightEntity;
import deepluckyblock.client.model.Modelender_knight;
import deepluckyblock.client.model.animations.ender_knightAnimationAttack;
import deepluckyblock.client.model.animations.ender_knightAnimationDeath;
import deepluckyblock.client.model.animations.ender_knightAnimationWalk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HierarchicalModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

@SuppressWarnings({"removal", "deprecation"})
public class EndKnightRenderer extends MobRenderer<EndKnightEntity, Modelender_knight<EndKnightEntity>> {

    private final ResourceLocation entityTexture = ResourceLocation.tryParse("deep_lucky_block:textures/entities/texture2.0.png");

    public EndKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new AnimatedModel(context.bakeLayer(Modelender_knight.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(EndKnightEntity entity) {
        return entityTexture;
    }

    @Override
    protected void setupRotations(EndKnightEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float defaultScale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, defaultScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
    }

    private static final class AnimatedModel extends Modelender_knight<EndKnightEntity> {
        private final ModelPart root;
        private final HierarchicalModel<EndKnightEntity> animator;

        public AnimatedModel(ModelPart root) {
            super(root);
            this.root = root;
            this.animator = new HierarchicalModel<EndKnightEntity>() {
                @Override
                public ModelPart root() {
                    return AnimatedModel.this.root;
                }

                @Override
                public void setupAnim(EndKnightEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                    this.root().getAllParts().forEach(ModelPart::resetPose);
                    this.animate(entity.animationState2, ender_knightAnimationWalk.walk, ageInTicks, 1f);
                    this.animate(entity.animationState3, ender_knightAnimationWalk.walk, ageInTicks, 2f);
                    this.animate(entity.animationState0, ender_knightAnimationAttack.attack, ageInTicks, 1f);
                    this.animate(entity.animationState1, ender_knightAnimationDeath.death, ageInTicks, 1f);
                }
            };
        }

        @Override
        public void setupAnim(EndKnightEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}