package deepluckyblock.client.renderer;

import deepluckyblock.entity.ShadowMirrorEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public class ShadowMirrorRenderer extends HumanoidMobRenderer<ShadowMirrorEntity, HumanoidModel<ShadowMirrorEntity>> {

    private final ResourceLocation entityTexture = ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/shadow.png");

    public ShadowMirrorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<ShadowMirrorEntity>(context.bakeLayer(ModelLayers.PLAYER)), 0.8f);

        this.addLayer(new RenderLayer<ShadowMirrorEntity, HumanoidModel<ShadowMirrorEntity>>(this) {
            final ResourceLocation LAYER_TEXTURE = ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/shadow.png");

            @Override
            public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, ShadowMirrorEntity entity,
                               float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
                VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(LAYER_TEXTURE));
                this.getParentModel().renderToBuffer(poseStack, vertexConsumer, light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0), 654311423); // blanc translucide vanilla
            }
        });

        this.addLayer(new HumanoidArmorLayer(this,
            new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowMirrorEntity entity) {
        return entityTexture;
    }

    @Override
    protected boolean isBodyVisible(ShadowMirrorEntity entity) {
        return true;
    }

    @Override
    protected boolean isShaking(ShadowMirrorEntity entity) {
        return true;
    }
}