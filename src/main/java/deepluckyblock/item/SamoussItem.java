package deepluckyblock.item;


import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.Map;
import java.util.Collections;

import deepluckyblock.init.DeepLuckyBlockModItems;

import deepluckyblock.client.model.Modelsamouss;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public abstract class SamoussItem extends ArmorItem {

	/*
	 * CORRECTION DE POSITION DES PIECES D'ARMURE (casque + plastron).
	 *
	 * Le modele Blockbench est construit avec une convention de coordonnees
	 * differente du rendu d'armure de Minecraft (y=0 = le cou du joueur, +Y vers
	 * le bas : tete de -8 a 0, torse de 0 a 12). Sans correction, les pieces
	 * flottent au-dessus du joueur.
	 *
	 * Les positions ci-dessous ont ete determinees par mesure en environnement
	 * de dev : elles placent le haut du casque a y=-8 et celui du plastron a y=0.
	 * Elles sont appliquees via setPos (API publique, identique en dev ET en
	 * production) : AUCUNE reflexion, donc plus de crash au demarrage du jeu.
	 *
	 * NOTE : si tu regeneres/modifies le modele dans Blockbench, ces deux
	 * valeurs devront peut-etre etre ajustees.
	 */
	private static void fixArmorPositions(Modelsamouss model) {
		model.samousshelmet.setPos(model.samousshelmet.x, 6.0F, model.samousshelmet.z);
		model.samousschestplate.setPos(model.samousschestplate.x, 2.0F, model.samousschestplate.z);
		// [debug retire] trace de calage d'armure, correctif valide depuis
	}

	public SamoussItem(ArmorItem.Type type, Item.Properties properties) {
		super(DeepLuckyBlockModItems.SAMOUSS_MATERIAL, type, properties.durability(
			new int[]{195, 225, 240, 165}[type.getSlot().getIndex()]));
	}

	public static class Helmet extends SamoussItem {
		public Helmet() {
			super(ArmorItem.Type.HELMET, new Item.Properties().fireResistant().rarity(Rarity.RARE));
		}

		@Override
		public void initializeClient(Consumer<IClientItemExtensions> consumer) {
			consumer.accept(new IClientItemExtensions() {
				private HumanoidModel armorModel = null;

				@Override
				@OnlyIn(Dist.CLIENT)
				public HumanoidModel getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel defaultModel) {
					if (armorModel == null) {
						Modelsamouss model = new Modelsamouss(Minecraft.getInstance().getEntityModels().bakeLayer(Modelsamouss.LAYER_LOCATION));
						fixArmorPositions(model); // correction position : casque sur la tete
						armorModel = new HumanoidModel(new ModelPart(Collections.emptyList(),
								Map.of("head", model.samousshelmet, "hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "body",
										new ModelPart(Collections.emptyList(), Collections.emptyMap()), "right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "left_arm",
										new ModelPart(Collections.emptyList(), Collections.emptyMap()), "right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "left_leg",
										new ModelPart(Collections.emptyList(), Collections.emptyMap()))));
					}
					armorModel.crouching = living.isShiftKeyDown();
					armorModel.riding = defaultModel.riding;
					armorModel.young = living.isBaby();
					return armorModel;
				}
			});
		}

		@Override
		public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean isInner) {
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/texture.png");
		}

		@Override
		public boolean makesPiglinsNeutral(ItemStack itemstack, LivingEntity entity) {
			return true;
		}
	}

	public static class Chestplate extends SamoussItem {
		public Chestplate() {
			super(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE));
		}

		@Override
		public void initializeClient(Consumer<IClientItemExtensions> consumer) {
			consumer.accept(new IClientItemExtensions() {
				private HumanoidModel armorModel = null;

				@Override
				@OnlyIn(Dist.CLIENT)
				public HumanoidModel getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel defaultModel) {
					if (armorModel == null) {
						Modelsamouss model = new Modelsamouss(Minecraft.getInstance().getEntityModels().bakeLayer(Modelsamouss.LAYER_LOCATION));
						fixArmorPositions(model); // correction position : plastron sur le torse
						armorModel = new HumanoidModel(new ModelPart(Collections.emptyList(),
								Map.of("body", model.samousschestplate,
										"left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"head", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"left_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())))) {
							@Override
							// PORT 1.21.1 : renderToBuffer(PoseStack, VertexConsumer, light, overlay, packedColor).
								public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int packedColor) {
								VertexConsumer translucentTexture = Minecraft.getInstance().renderBuffers().bufferSource()
										.getBuffer(RenderType.entityTranslucent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/texture.png")));
								super.renderToBuffer(poseStack, translucentTexture, packedLight, packedOverlay, packedColor);
							}
						};
					}
					armorModel.crouching = living.isShiftKeyDown();
					armorModel.riding = defaultModel.riding;
					armorModel.young = living.isBaby();
					return armorModel;
				}
			});
		}

		@Override
		public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean isInner) {
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/texture.png");
		}
	}

	public static class Boots extends SamoussItem {
		public Boots() {
			super(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE));
		}

		@Override
		public void initializeClient(Consumer<IClientItemExtensions> consumer) {
			consumer.accept(new IClientItemExtensions() {
				private HumanoidModel armorModel = null;

				@Override
				@OnlyIn(Dist.CLIENT)
				public HumanoidModel getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel defaultModel) {
					if (armorModel == null) {
						Modelsamouss model = new Modelsamouss(Minecraft.getInstance().getEntityModels().bakeLayer(Modelsamouss.LAYER_LOCATION));
						armorModel = new HumanoidModel(new ModelPart(Collections.emptyList(),
								Map.of("left_leg", model.leftleg1, "right_leg", model.rightleg1, "head", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "body",
										new ModelPart(Collections.emptyList(), Collections.emptyMap()), "right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()), "left_arm",
										new ModelPart(Collections.emptyList(), Collections.emptyMap()))));
					}
					armorModel.crouching = living.isShiftKeyDown();
					armorModel.riding = defaultModel.riding;
					armorModel.young = living.isBaby();
					return armorModel;
				}
			});
		}

		@Override
		public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean isInner) {
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/texture.png");
		}

		@Override
		public boolean makesPiglinsNeutral(ItemStack itemstack, LivingEntity entity) {
			return true;
		}
	}
}
