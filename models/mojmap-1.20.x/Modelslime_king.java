// Made with Blockbench 5.1.5
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelslime_king<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("modid", "slime_king"), "main");
	private final ModelPart body;
	private final ModelPart crown;
	private final ModelPart ONLYBOD;
	private final ModelPart eyes;

	public Modelslime_king(ModelPart root) {
		this.body = root.getChild("body");
		this.crown = this.body.getChild("crown");
		this.ONLYBOD = this.body.getChild("ONLYBOD");
		this.eyes = this.ONLYBOD.getChild("eyes");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(),
				PartPose.offset(4.0F, 15.0F, -5.0F));

		PartDefinition crown = body.addOrReplaceChild("crown", CubeListBuilder.create(),
				PartPose.offset(4.0F, -4.0F, -3.0F));

		PartDefinition cube_r1 = crown.addOrReplaceChild("cube_r1",
				CubeListBuilder.create().texOffs(0, 0).addBox(-17.0F, -14.0F, -1.0F, 18.0F, 14.0F, 18.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1304F, 0.0114F, 0.0865F));

		PartDefinition ONLYBOD = body.addOrReplaceChild("ONLYBOD",
				CubeListBuilder.create().texOffs(0, 32)
						.addBox(-12.0F, -7.0F, -3.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)).texOffs(0, 64)
						.addBox(-8.0F, -2.0F, 1.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(2, 1)
						.addBox(-3.0F, 4.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyes = ONLYBOD.addOrReplaceChild("eyes",
				CubeListBuilder.create().texOffs(0, 1)
						.addBox(-9.0F, -2.0F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(0, 1)
						.addBox(-2.0F, -2.0F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.eyes.yRot = netHeadYaw / (180F / (float) Math.PI);
		this.eyes.xRot = headPitch / (180F / (float) Math.PI);
	}
}