package deepluckyblock.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;

import deepluckyblock.entity.JerryEntity;

public class JerryRenderer extends HumanoidMobRenderer<JerryEntity, HumanoidModel<JerryEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("deep_lucky_block:textures/entities/mastu-team-claquette-crazy-town-s3-on-planetminecraft-com.png");

	public JerryRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<JerryEntity>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		// V40 : couche d'armure RETIREE volontairement.
		// Le mob porte une armure enchantee cote serveur (bonus + resistances)
		// mais rien n'est rendu : ni les pieces, ni le reflet d'enchantement.
		// Pour re-afficher l'armure, restaure la ligne ci-dessous :
		// this.addLayer(new HumanoidArmorLayer(this, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
	}

	@Override
	public ResourceLocation getTextureLocation(JerryEntity entity) {
		return entityTexture;
	}
}