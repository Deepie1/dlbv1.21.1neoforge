
// Made with Blockbench 5.1.6
// Export adapté pour le workspace MCreator "supreme_lucky_block" (forge-1.20.1)
// La classe est nommée ModelCustomModel pour correspondre à l'import de LunettesDuPatronItem.java
public class ModelCustomModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("deep_lucky_block", "lunettesdupatron"), "main");
	private final ModelPart LunettesDuPatron;

	public ModelCustomModel(ModelPart root) {
		this.LunettesDuPatron = root.getChild("LunettesDuPatron");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition LunettesDuPatron = partdefinition.addOrReplaceChild("LunettesDuPatron", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-5.0F, -6.0F, -4.6F, 10.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r1 = LunettesDuPatron.addOrReplaceChild("cube_r1",
				CubeListBuilder.create().texOffs(12, 20).addBox(0.5F, -2.0F, -5.0F, 0.0F, 2.0F, 6.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-5.0F, -3.0F, 0.0F, -0.176F, 0.1289F, 0.0078F));

		PartDefinition cube_r2 = LunettesDuPatron.addOrReplaceChild("cube_r2",
				CubeListBuilder.create().texOffs(0, 20).addBox(0.2F, -2.0F, -5.0F, 0.0F, 2.0F, 6.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(4.0F, -3.0F, 0.0F, -0.1772F, -0.1719F, 0.0F));

		return LayerDefinition.create(meshdefinition, 32, 32);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		LunettesDuPatron.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.LunettesDuPatron.yRot = netHeadYaw / (180F / (float) Math.PI);
		this.LunettesDuPatron.xRot = headPitch / (180F / (float) Math.PI);
	}
}
