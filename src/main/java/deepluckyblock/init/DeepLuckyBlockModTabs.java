/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;

import deepluckyblock.DeepLuckyBlockMod;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class DeepLuckyBlockModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeepLuckyBlockMod.MODID);

	@SubscribeEvent
	public static void buildTabContentsVanilla(BuildCreativeModeTabContentsEvent tabData) {
		if (CreativeModeTabs.SPAWN_EGGS.equals(tabData.getTabKey())) {
			tabData.accept(DeepLuckyBlockModItems.SHADOW_MIRROR_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.END_KNIGHT_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.SLIME_KING_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.CRIMSON_BUTTERFLY_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.SAMOUSS_BOSS_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.JERRY_SPAWN_EGG.get());
			tabData.accept(DeepLuckyBlockModItems.BEN_SPAWN_EGG.get());
		} else if (CreativeModeTabs.COMBAT.equals(tabData.getTabKey())) {
			tabData.accept(DeepLuckyBlockModItems.LUNETTES_DU_PATRON_HELMET.get());
			tabData.accept(DeepLuckyBlockModItems.SAMOUSS_HELMET.get());
			tabData.accept(DeepLuckyBlockModItems.SAMOUSS_CHESTPLATE.get());
			tabData.accept(DeepLuckyBlockModItems.SAMOUSS_LEGGINGS.get());
			tabData.accept(DeepLuckyBlockModItems.SAMOUSS_BOOTS.get());
		}
	}
}