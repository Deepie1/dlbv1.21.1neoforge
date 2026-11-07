/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import deepluckyblock.item.ShadowEssenceItem;
import deepluckyblock.item.SamoussItem;
import deepluckyblock.item.LunettesDuPatronItem;
import deepluckyblock.item.FirarainPotionItem;

import deepluckyblock.DeepLuckyBlockMod;

public class DeepLuckyBlockModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(BuiltInRegistries.ITEM, DeepLuckyBlockMod.MODID);

	// PORT 1.21.1 : ArmorMaterial est un record registre (BuiltInRegistries.ARMOR_MATERIAL).
	public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
	        DeferredRegister.create(BuiltInRegistries.ARMOR_MATERIAL, DeepLuckyBlockMod.MODID);
	// FIX v8 (armures invisibles) : NeoForge 1.21.1 recupere bien le MODELE custom
	// via le hook getHumanoidArmorModel (IClientItemExtensions), MAIS la boucle de
	// rendu de HumanoidArmorLayer ne s execute que s il y a au moins un
	// ArmorMaterial.Layer dans le material. Avec List.of() (vide) -> 0 rendu ->
	// armure invisible. Un layer non teintable avec la texture du mod active le
	// rendu ; la texture effective vient de Item.getArmorTexture (surchargee par
	// SamoussItem / LunettesDuPatronItem) et le modele du hook custom.
	private static final ResourceLocation ARMOR_LAYER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/texture.png");
	private static final ResourceLocation GLASSES_LAYER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "textures/entities/glasses.png");
	public static final net.neoforged.neoforge.registries.DeferredHolder<ArmorMaterial, ArmorMaterial> SAMOUSS_MATERIAL = ARMOR_MATERIALS.register("samouss",
	        () -> new ArmorMaterial(
	                Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, 6,
	                        ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.BOOTS, 3),
	                16,
	                SoundEvents.ARMOR_EQUIP_LEATHER,
	                () -> Ingredient.of(Items.LEATHER),
	                List.of(new ArmorMaterial.Layer(ARMOR_LAYER_TEXTURE)),
	                1.6f,
	                0f));
	public static final net.neoforged.neoforge.registries.DeferredHolder<ArmorMaterial, ArmorMaterial> LUNETTES_MATERIAL = ARMOR_MATERIALS.register("lunettes_du_patron",
	        () -> new ArmorMaterial(
	                Map.of(ArmorItem.Type.HELMET, 0, ArmorItem.Type.CHESTPLATE, 0,
	                        ArmorItem.Type.LEGGINGS, 0, ArmorItem.Type.BOOTS, 1),
	                5,
	                SoundEvents.ARMOR_EQUIP_CHAIN,
	                () -> Ingredient.of(Items.CHAIN),
	                List.of(new ArmorMaterial.Layer(GLASSES_LAYER_TEXTURE)),
	                0f,
	                0f));
	public static final DeferredHolder<Item, Item> FIRARAIN_POTION;
	public static final DeferredHolder<Item, Item> DEEP_LUCKY_BLOCK;
	public static final DeferredHolder<Item, Item> SHADOW_ESSENCE;
	public static final DeferredHolder<Item, Item> SHADOW_MIRROR_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> END_KNIGHT_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> SLIME_KING_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> CRIMSON_BUTTERFLY_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> LUNETTES_DU_PATRON_HELMET;
	public static final DeferredHolder<Item, Item> SAMOUSS_HELMET;
	public static final DeferredHolder<Item, Item> SAMOUSS_CHESTPLATE;
	public static final DeferredHolder<Item, Item> SAMOUSS_LEGGINGS;
	public static final DeferredHolder<Item, Item> SAMOUSS_BOOTS;
	public static final DeferredHolder<Item, Item> SAMOUSS_BOSS_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> JERRY_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> BEN_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> BO_CUBE_DE_BOIS;
	static {
		FIRARAIN_POTION = REGISTRY.register("firarain_potion", FirarainPotionItem::new);
			// FIX SLB : le Deep Lucky Block DOIT etre un DeepLuckyBlockItem (pas un
			// BlockItem generique) pour beneficier de son nom colore par tiers de
			// luck (getName()) et de son tooltip (appendHoverText()).
			DEEP_LUCKY_BLOCK = REGISTRY.register(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.getId().getPath(),
					() -> new deepluckyblock.item.DeepLuckyBlockItem(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get(),
							new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
		SHADOW_ESSENCE = REGISTRY.register("shadow_essence", ShadowEssenceItem::new);
		SHADOW_MIRROR_SPAWN_EGG = REGISTRY.register("shadow_mirror_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.SHADOW_MIRROR, -16777216, -15073251, new Item.Properties()));
		END_KNIGHT_SPAWN_EGG = REGISTRY.register("end_knight_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.END_KNIGHT, -16777216, -12386237, new Item.Properties()));
		SLIME_KING_SPAWN_EGG = REGISTRY.register("slime_king_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.SLIME_KING, -16711690, -9728, new Item.Properties()));
		CRIMSON_BUTTERFLY_SPAWN_EGG = REGISTRY.register("crimson_butterfly_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.CRIMSON_BUTTERFLY, -6750157, -6750208, new Item.Properties()));
		LUNETTES_DU_PATRON_HELMET = REGISTRY.register("lunettes_du_patron_helmet", LunettesDuPatronItem.Helmet::new);
		SAMOUSS_HELMET = REGISTRY.register("samouss_helmet", SamoussItem.Helmet::new);
		SAMOUSS_CHESTPLATE = REGISTRY.register("samouss_chestplate", SamoussItem.Chestplate::new);
		SAMOUSS_LEGGINGS = REGISTRY.register("samouss_leggings", SamoussItem.Leggings::new);
		SAMOUSS_BOOTS = REGISTRY.register("samouss_boots", SamoussItem.Boots::new);
		SAMOUSS_BOSS_SPAWN_EGG = REGISTRY.register("samouss_boss_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.SAMOUSS_BOSS, -987, -15987, new Item.Properties()));
		JERRY_SPAWN_EGG = REGISTRY.register("jerry_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.JERRY, -1, -1, new Item.Properties()));
		BEN_SPAWN_EGG = REGISTRY.register("ben_spawn_egg", () -> new DeferredSpawnEggItem(DeepLuckyBlockModEntities.BEN, -1, -1, new Item.Properties()));
		BO_CUBE_DE_BOIS = block(DeepLuckyBlockModBlocks.BO_CUBE_DE_BOIS, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static DeferredHolder<Item, Item> block(DeferredHolder<Block, Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredHolder<Item, Item> block(DeferredHolder<Block, Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}
}