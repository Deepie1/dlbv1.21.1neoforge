
// Made with Blockbench 5.1.5
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelender_knight<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("supreme_lucky_block", "ender_knight"), "main");
	private final ModelPart body2;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart PENTALON;
	private final ModelPart bone3;
	private final ModelPart bone;
	private final ModelPart bras;
	private final ModelPart left_arm;
	private final ModelPart avant_bras1;
	private final ModelPart right_arm;
	private final ModelPart avant_bras2;
	private final ModelPart sword;
	private final ModelPart left_leg;
	private final ModelPart jambe2;
	private final ModelPart left_boots;
	private final ModelPart right_leg;
	private final ModelPart bone2;
	private final ModelPart right_boot;

	public Modelender_knight(ModelPart root) {
		this.body2 = root.getChild("body2");
		this.head = this.body2.getChild("head");
		this.body = this.body2.getChild("body");
		this.PENTALON = this.body.getChild("PENTALON");
		this.bone3 = this.body.getChild("bone3");
		this.bone = this.body.getChild("bone");
		this.bras = this.body2.getChild("bras");
		this.left_arm = this.bras.getChild("left_arm");
		this.avant_bras1 = this.left_arm.getChild("avant bras1");
		this.right_arm = this.bras.getChild("right_arm");
		this.avant_bras2 = this.right_arm.getChild("avant_bras2");
		this.sword = this.bras.getChild("sword");
		this.left_leg = this.body2.getChild("left_leg");
		this.jambe2 = this.left_leg.getChild("jambe2");
		this.left_boots = this.jambe2.getChild("left_boots");
		this.right_leg = this.body2.getChild("right_leg");
		this.bone2 = this.right_leg.getChild("bone2");
		this.right_boot = this.bone2.getChild("right_boot");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body2 = partdefinition.addOrReplaceChild("body2", CubeListBuilder.create(),
				PartPose.offset(-5.0F, -35.0F, 0.0F));

		PartDefinition head = body2.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 34).addBox(-3.0F,
				-8.0F, 0.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = body2.addOrReplaceChild("body", CubeListBuilder.create(),
				PartPose.offset(2.0F, 10.0F, 10.0F));

		PartDefinition PENTALON = body.addOrReplaceChild("PENTALON", CubeListBuilder.create().texOffs(22, 17)
				.addBox(-4.0F, -15.0F, -1.0F, 5.0F, 5.0F, 12.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-1.0F, 25.0F, -11.0F));

		PartDefinition bone3 = body.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(22, 0).addBox(-5.0F,
				-15.0F, 0.0F, 5.0F, 3.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 5.0F, -13.0F));

		PartDefinition bone = body.addOrReplaceChild("bone", CubeListBuilder.create(),
				PartPose.offset(-6.0F, 12.0F, -6.0F));

		PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1",
				CubeListBuilder.create().texOffs(3, 80).addBox(1.0F, -1.0F, -1.0F, 0.0F, 29.0F, 14.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -21.0F, -6.0F, 0.0F, 0.0F, 0.2182F));

		PartDefinition cube_r2 = bone.addOrReplaceChild("cube_r2",
				CubeListBuilder.create().texOffs(64, 27).addBox(0.0F, -7.5F, 0.5F, 1.0F, 5.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, -5.0F, 5.0F, 1.5708F, 0.0F, -0.2443F));

		PartDefinition cube_r3 = bone.addOrReplaceChild("cube_r3",
				CubeListBuilder.create().texOffs(60, 27).addBox(0.0F, -7.5F, 0.5F, 1.0F, 5.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, -8.0F, 5.0F, 1.5708F, 0.0F, 0.1484F));

		PartDefinition cube_r4 = bone.addOrReplaceChild("cube_r4",
				CubeListBuilder.create().texOffs(56, 27).addBox(0.0F, -8.0F, 0.0F, 1.0F, 6.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.0F, -12.0F, 5.0F, 1.5708F, 0.0F, 0.2512F));

		PartDefinition cube_r5 = bone.addOrReplaceChild("cube_r5",
				CubeListBuilder.create().texOffs(76, 13).addBox(0.0F, -11.0F, 0.0F, 1.0F, 9.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(3.0F, -14.0F, 6.5F, 1.5708F, 0.0F, 0.2512F));

		PartDefinition cube_r6 = bone.addOrReplaceChild("cube_r6",
				CubeListBuilder.create().texOffs(76, 23).addBox(0.0F, -10.0F, -1.0F, 1.0F, 8.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(4.0F, -17.0F, 6.0F, 1.5708F, 0.0F, 0.2512F));

		PartDefinition cube_r7 = bone
				.addOrReplaceChild("cube_r7",
						CubeListBuilder.create().texOffs(12, 64).addBox(-1.0F, -16.0F, -1.0F, 2.0F, 14.0F, 2.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.2512F));

		PartDefinition cube_r8 = bone
				.addOrReplaceChild("cube_r8",
						CubeListBuilder.create().texOffs(24, 72).addBox(-1.0F, -9.0F, -1.0F, 2.0F, 7.0F, 2.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(3.0F, 1.0F, 0.0F, 0.0F, 0.0F, -0.2724F));

		PartDefinition bras = body2.addOrReplaceChild("bras", CubeListBuilder.create(),
				PartPose.offset(5.0F, -1.0F, 11.0F));

		PartDefinition left_arm = bras.addOrReplaceChild("left_arm", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2237F, -0.3269F, 0.0414F));

		PartDefinition cube_r9 = left_arm.addOrReplaceChild("cube_r9",
				CubeListBuilder.create().texOffs(28, 50).addBox(-6.0F, -7.0F, 4.0F, 7.0F, 7.0F, 7.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-3.0F, 5.0F, -3.0F, -0.0475F, 0.346F, -0.1392F));

		PartDefinition cube_r10 = left_arm.addOrReplaceChild("cube_r10",
				CubeListBuilder.create().texOffs(32, 72).addBox(-0.6559F, -16.8126F, 3.6882F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, -0.4363F, -0.2182F));

		PartDefinition cube_r11 = left_arm.addOrReplaceChild("cube_r11",
				CubeListBuilder.create().texOffs(72, 0).addBox(-0.6559F, -16.8126F, 3.6882F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 21.0F, 2.0F, 0.0F, -0.4363F, -0.2182F));

		PartDefinition avant_bras1 = left_arm.addOrReplaceChild("avant bras1", CubeListBuilder.create(),
				PartPose.offsetAndRotation(9.0F, 21.0F, -5.0F, -0.1238F, 0.3272F, -0.0641F));

		PartDefinition cube_r12 = avant_bras1.addOrReplaceChild("cube_r12",
				CubeListBuilder.create().texOffs(36, 72).addBox(-7.7373F, -15.3515F, 0.2329F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.3491F, 0.0F, -0.6545F));

		PartDefinition cube_r13 = avant_bras1.addOrReplaceChild("cube_r13",
				CubeListBuilder.create().texOffs(20, 72).addBox(-7.7373F, -15.3515F, 0.2329F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, -0.3491F, 0.0F, -0.6545F));

		PartDefinition right_arm = bras.addOrReplaceChild("right_arm", CubeListBuilder.create(),
				PartPose.offsetAndRotation(-20.0F, 4.0F, -16.0F, 0.2237F, 0.3269F, -0.0414F));

		PartDefinition cube_r14 = right_arm.addOrReplaceChild("cube_r14",
				CubeListBuilder.create().texOffs(76, 0).addBox(-1.3783F, -17.699F, 0.538F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(23.0F, 19.0F, 5.0F, 0.0F, -0.4363F, -0.2182F));

		PartDefinition cube_r15 = right_arm.addOrReplaceChild("cube_r15",
				CubeListBuilder.create().texOffs(40, 72).addBox(-1.3783F, -17.699F, 0.538F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(23.0F, 19.0F, 7.0F, 0.0F, -0.4363F, -0.2182F));

		PartDefinition cube_r16 = right_arm.addOrReplaceChild("cube_r16",
				CubeListBuilder.create().texOffs(0, 50).addBox(-6.0F, -5.0F, -1.0F, 7.0F, 7.0F, 7.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(20.0F, 2.0F, 4.0F, 0.0F, -0.3054F, 0.0F));

		PartDefinition avant_bras2 = right_arm.addOrReplaceChild("avant_bras2", CubeListBuilder.create(),
				PartPose.offsetAndRotation(26.0F, 18.0F, 15.0F, 0.2269F, -1.1551F, -0.1684F));

		PartDefinition cube_r17 = avant_bras2.addOrReplaceChild("cube_r17",
				CubeListBuilder.create().texOffs(48, 72).addBox(-5.6647F, -14.4049F, -0.3781F, 1.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.0F, 6.0F, 3.0F, 0.0059F, 0.056F, -0.4435F));

		PartDefinition cube_r18 = avant_bras2.addOrReplaceChild(
				"cube_r18", CubeListBuilder.create().texOffs(44, 72).addBox(-4.7711F, -13.7464F, -0.734F, 1.0F, 12.0F,
						1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, 3.0F, 2.0F, 0.0F, 0.0F, -0.6545F));

		PartDefinition sword = bras.addOrReplaceChild("sword", CubeListBuilder.create(),
				PartPose.offset(4.0F, 20.0F, -6.0F));

		PartDefinition cube_r19 = sword
				.addOrReplaceChild("cube_r19",
						CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -40.0F, -1.5F, 10.0F, 40.0F, 1.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition cube_r20 = sword.addOrReplaceChild("cube_r20",
				CubeListBuilder.create().texOffs(68, 68).addBox(-1.0F, -13.0F, -1.0F, 2.0F, 13.0F, 2.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-4.0F, 9.0F, -1.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition left_leg = body2.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(56, 50)
						.addBox(-3.0F, 0.0F, -2.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)).texOffs(0, 64)
						.addBox(-2.0F, 3.0F, -1.0F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 25.0F, 7.0F));

		PartDefinition jambe2 = left_leg.addOrReplaceChild("jambe2", CubeListBuilder.create(),
				PartPose.offset(0.0F, 15.0F, 1.5F));

		PartDefinition cube_r21 = jambe2.addOrReplaceChild("cube_r21",
				CubeListBuilder.create().texOffs(60, 68).addBox(-2.0F, -13.0F, 0.5F, 2.0F, 13.0F, 2.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-1.0F, 13.0F, -2.5F, 0.0F, 0.0F, 0.1309F));

		PartDefinition left_boots = jambe2.addOrReplaceChild("left_boots",
				CubeListBuilder.create().texOffs(0, 41)
						.addBox(-1.0F, 1.0F, -3.0F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).texOffs(54, 42)
						.addBox(-1.0F, 4.0F, -3.0F, 8.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-2.0F, 12.0F, -0.5F));

		PartDefinition right_leg = body2.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(56, 17)
						.addBox(-2.5F, 0.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)).texOffs(60, 0)
						.addBox(-1.5F, 2.0F, -1.5F, 3.0F, 13.0F, 3.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-0.5F, 25.0F, 0.5F));

		PartDefinition bone2 = right_leg.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(52, 68).addBox(
				-2.0F, 0.0F, -0.5F, 2.0F, 13.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 15.0F, -0.5F));

		PartDefinition right_boot = bone2.addOrReplaceChild("right_boot",
				CubeListBuilder.create().texOffs(56, 60)
						.addBox(-2.0F, 0.0F, -2.0F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).texOffs(54, 34)
						.addBox(-2.0F, 3.0F, -2.0F, 8.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-2.0F, 13.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		body2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.head.yRot = netHeadYaw / (180F / (float) Math.PI);
		this.head.xRot = headPitch / (180F / (float) Math.PI);
	}
}