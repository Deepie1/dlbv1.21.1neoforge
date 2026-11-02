/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;

import deepluckyblock.client.model.Modelslime_king;
import deepluckyblock.client.model.Modelsamouss;
import deepluckyblock.client.model.Modelender_knight;
import deepluckyblock.client.model.Modelbutterfly;
import deepluckyblock.client.model.ModelCustomModel;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class DeepLuckyBlockModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(ModelCustomModel.LAYER_LOCATION, ModelCustomModel::createBodyLayer);
		event.registerLayerDefinition(Modelbutterfly.LAYER_LOCATION, Modelbutterfly::createBodyLayer);
		event.registerLayerDefinition(Modelsamouss.LAYER_LOCATION, Modelsamouss::createBodyLayer);
		event.registerLayerDefinition(Modelslime_king.LAYER_LOCATION, Modelslime_king::createBodyLayer);
		event.registerLayerDefinition(Modelender_knight.LAYER_LOCATION, Modelender_knight::createBodyLayer);
	}
}