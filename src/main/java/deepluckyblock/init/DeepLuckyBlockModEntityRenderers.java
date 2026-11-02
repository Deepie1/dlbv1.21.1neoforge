/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;

import deepluckyblock.client.renderer.*;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class DeepLuckyBlockModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(DeepLuckyBlockModEntities.SHADOW_MIRROR.get(), ShadowMirrorRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.END_KNIGHT.get(), EndKnightRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.SLIME_KING.get(), SlimeKingRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.CRIMSON_BUTTERFLY.get(), CrimsonButterflyRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.SAMOUSS_BOSS.get(), SamoussBossRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.JERRY.get(), JerryRenderer::new);
		event.registerEntityRenderer(DeepLuckyBlockModEntities.BEN.get(), BenRenderer::new);
	}
}