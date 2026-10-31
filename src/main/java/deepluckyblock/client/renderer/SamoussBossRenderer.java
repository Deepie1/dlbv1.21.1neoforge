package deepluckyblock.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;

import deepluckyblock.entity.SamoussBossEntity;

public class SamoussBossRenderer extends HumanoidMobRenderer<SamoussBossEntity, HumanoidModel<SamoussBossEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("deep_lucky_block:textures/entities/samouss-crazytown-3-on-planetminecraft-com.png");

	public SamoussBossRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<SamoussBossEntity>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		// V41 : COUCHE D'ARMURE RESTAURÉE POUR SAMOUSS UNIQUEMENT.
		// Samouss porte son full set Samouss (casque/chapeau + plastron +
		// bottes) équipé dans SamoussBossEntity.applyEquipment() : cette couche
		// le rend visible, reflet d'enchantement compris (les pièces sont
		// enchantées par GenerateBad/GenerateLuckyItem).
		// Ben et Jerry gardent leurs renderers SANS couche : leur armure
		// technique invisible doit le rester.
		this.addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
	}

	@Override
	public ResourceLocation getTextureLocation(SamoussBossEntity entity) {
		return entityTexture;
	}

	@Override
	protected boolean isShaking(SamoussBossEntity entity) {
		return true;
	}
}
