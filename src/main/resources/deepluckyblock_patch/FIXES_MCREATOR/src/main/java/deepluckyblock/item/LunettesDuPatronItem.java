package deepluckyblock.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.Map;
import java.util.List;
import java.util.Collections;

import deepluckyblock.procedures.StrengthHelmetTickProcedure;

import deepluckyblock.init.DeepLuckyBlockModItems;

import deepluckyblock.client.model.ModelCustomModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

import com.google.common.collect.Iterables;

public abstract class LunettesDuPatronItem extends ArmorItem {
	public LunettesDuPatronItem(ArmorItem.Type type, Item.Properties properties) {
		super(DeepLuckyBlockModItems.LUNETTES_MATERIAL, type, properties.durability(
		new int[]{65, 75, 80, 55}[type.getSlot().getIndex()]));
	}

	public static class Helmet extends LunettesDuPatronItem {
		public Helmet() {
			super(ArmorItem.Type.HELMET, new Item.Properties().fireResistant().rarity(Rarity.EPIC));
		}

		@Override
		public void initializeClient(Consumer<IClientItemExtensions> consumer) {
			consumer.accept(new IClientItemExtensions() {
				private HumanoidModel armorModel = null;

				@Override
				@OnlyIn(Dist.CLIENT)
				public HumanoidModel getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel defaultModel) {
					if (armorModel == null) {
						armorModel = new HumanoidModel(new ModelPart(Collections.emptyList(),
								Map.of("head",
										// Correctif : on construit le modèle directement depuis createBodyLayer()
										// (aucune inscription de layer requise : fonctionne même si le registre
										// de layers du mod ne référence plus ModelCustomModel)
										ModelCustomModel.createBodyLayer().bakeRoot(),
										"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"body", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
										"left_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())))) {
							@Override
							// PORT 1.21.1 : renderToBuffer(PoseStack, VertexConsumer, light, overlay, packedColor).
								public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int packedColor) {
								VertexConsumer translucentTexture = Minecraft.getInstance().renderBuffers().bufferSource()
										.getBuffer(RenderType.entityTranslucent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/glasses.png")));
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
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/glasses.png");
		}

		@Override
		public void appendHoverText(ItemStack itemstack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
			super.appendHoverText(itemstack, context, list, flag);
			list.add(Component.translatable("item.deep_lucky_block.lunettes_du_patron_helmet.description_0"));
		}

		@Override
		public boolean makesPiglinsNeutral(ItemStack itemstack, LivingEntity entity) {
			return true;
		}

		@Override
		public void inventoryTick(ItemStack itemstack, Level world, Entity entity, int slot, boolean selected) {
			super.inventoryTick(itemstack, world, entity, slot, selected);
			if (entity instanceof Player player && Iterables.contains(player.getArmorSlots(), itemstack)) {
				StrengthHelmetTickProcedure.execute(entity);
			}
		}
	}
}
