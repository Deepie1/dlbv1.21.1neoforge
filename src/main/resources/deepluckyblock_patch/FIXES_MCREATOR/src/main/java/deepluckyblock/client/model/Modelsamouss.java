package deepluckyblock.client.model;

import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.EntityModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports
public class Modelsamouss<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "modelsamouss"), "main");
	public final ModelPart samoussboots;
	public final ModelPart leftleg1;
	public final ModelPart rightleg1;
	public final ModelPart samousshelmet;
	public final ModelPart head1;
	public final ModelPart samousschestplate;
	public final ModelPart body;
	public final ModelPart bone;

	public Modelsamouss(ModelPart root) {
		this.samoussboots = root.getChild("samoussboots");
		this.leftleg1 = this.samoussboots.getChild("leftleg1");
		this.rightleg1 = this.samoussboots.getChild("rightleg1");
		this.samousshelmet = root.getChild("samousshelmet");
		this.head1 = this.samousshelmet.getChild("head1");
		this.samousschestplate = root.getChild("samousschestplate");
		this.body = this.samousschestplate.getChild("body");
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition samoussboots = partdefinition.addOrReplaceChild("samoussboots", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition leftleg1 = samoussboots.addOrReplaceChild("leftleg1", CubeListBuilder.create(), PartPose.offset(1.9F, -12.0F, 0.0F));
		PartDefinition cube_r1 = leftleg1.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(-1, 45).addBox(-2.0F, -2.0F, -3.0F, 6.0F, 3.0F, 15.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.1F, 12.0F, -10.0F, 0.1309F, -0.2618F, 0.0F));
		PartDefinition rightleg1 = samoussboots.addOrReplaceChild("rightleg1", CubeListBuilder.create(), PartPose.offset(-1.9F, -12.0F, 0.0F));
		PartDefinition cube_r2 = rightleg1.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(43, 39).addBox(-2.0F, -2.0F, -3.0F, 6.0F, 3.0F, 15.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-5.1F, 13.0F, -8.0F, 0.1802F, 0.3034F, 0.0238F));
		PartDefinition samousshelmet = partdefinition.addOrReplaceChild("samousshelmet", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition head1 = samousshelmet.addOrReplaceChild("head1", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition cube_r3 = head1.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(48, 16).addBox(-12.0F, -37.0F, -1.3F, 4.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.1309F, 0.0F, 0.3054F));
		PartDefinition cube_r4 = head1.addOrReplaceChild("cube_r4",
				CubeListBuilder.create().texOffs(0, 24).addBox(-6.0F, -34.0F, -1.0F, 12.0F, 11.0F, 10.0F, new CubeDeformation(0.0F)).texOffs(6, 98).addBox(-15.0F, -38.0F, 4.0F, 29.0F, 15.0F, 0.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.1309F, 0.0F, 0.0F));
		PartDefinition samousschestplate = partdefinition.addOrReplaceChild("samousschestplate", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition body = samousschestplate.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition cube_r5 = body.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(9, 12).addBox(-7.0F, -6.0F, 21.0F, 1.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-8.0F, 20.0F, -2.0F, -0.268F, 0.3702F, -0.1794F));
		PartDefinition cube_r6 = body.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(7, 6).addBox(-3.0F, -10.0F, 6.0F, 1.0F, 1.0F, 16.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(3.0F, 24.0F, 0.0F, -0.2618F, -0.3054F, 0.0F));
		PartDefinition cube_r7 = body.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -19.0F, -4.0F, 13.0F, 13.0F, 11.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, -0.0436F));
		PartDefinition cube_r8 = body.addOrReplaceChild("cube_r8",
				CubeListBuilder.create().texOffs(32, 63).addBox(-5.0F, -26.0F, 10.0F, 2.0F, 9.0F, 0.0F, new CubeDeformation(0.0F)).texOffs(40, 45).addBox(2.0F, -26.0F, 10.0F, 2.0F, 9.0F, 0.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.2182F, 0.0F, 0.0F));
		PartDefinition cube_r9 = body.addOrReplaceChild("cube_r9",
				CubeListBuilder.create().texOffs(60, 16).addBox(2.0F, -24.0F, -7.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)).texOffs(56, 16).addBox(-4.0F, -24.0F, -7.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.2182F, 0.0F, 0.0F));
		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(13.0F, 26.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int rgb) {
		samoussboots.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
		samousshelmet.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
		samousschestplate.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.rightleg1.xRot = Mth.cos(limbSwing * 1.0F) * 1.0F * limbSwingAmount;
		this.leftleg1.xRot = Mth.cos(limbSwing * 1.0F) * -1.0F * limbSwingAmount;
	}
}