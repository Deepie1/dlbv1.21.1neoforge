/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

import deepluckyblock.entity.*;

import deepluckyblock.DeepLuckyBlockMod;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class DeepLuckyBlockModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DeepLuckyBlockMod.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<ShadowMirrorEntity>> SHADOW_MIRROR = register("shadow_mirror",
			// PORT 1.21.1 : LivingEntity.getDimensions est final -> dimensions sur le EntityType.
			EntityType.Builder.<ShadowMirrorEntity>of(ShadowMirrorEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(0.6f, 2.95f));
	public static final DeferredHolder<EntityType<?>, EntityType<EndKnightEntity>> END_KNIGHT = register("end_knight",
			EntityType.Builder.<EndKnightEntity>of(EndKnightEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(1.2f, 4.0f));
	public static final DeferredHolder<EntityType<?>, EntityType<SlimeKingEntity>> SLIME_KING = register("slime_king",
			EntityType.Builder.<SlimeKingEntity>of(SlimeKingEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(3.0f, 3.0f));
	public static final DeferredHolder<EntityType<?>, EntityType<CrimsonButterflyEntity>> CRIMSON_BUTTERFLY = register("crimson_butterfly", EntityType.Builder.<CrimsonButterflyEntity>of(CrimsonButterflyEntity::new, MobCategory.CREATURE)
			.setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune().sized(0.1f, 0.1f));
	public static final DeferredHolder<EntityType<?>, EntityType<SamoussBossEntity>> SAMOUSS_BOSS = register("samouss_boss",
			EntityType.Builder.<SamoussBossEntity>of(SamoussBossEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<JerryEntity>> JERRY = register("jerry",
			EntityType.Builder.<JerryEntity>of(JerryEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<BenEntity>> BEN = register("ben",
			EntityType.Builder.<BenEntity>of(BenEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)
					.sized(0.6f, 1.8f));

	// Start of user code block custom entities
	// End of user code block custom entities
	private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> (EntityType<T>) entityTypeBuilder.build(registryname));
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ShadowMirrorEntity.init();
			EndKnightEntity.init();
			SlimeKingEntity.init();
			CrimsonButterflyEntity.init();
			SamoussBossEntity.init();
			JerryEntity.init();
			BenEntity.init();
		});
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(SHADOW_MIRROR.get(), ShadowMirrorEntity.createAttributes().build());
		event.put(END_KNIGHT.get(), EndKnightEntity.createAttributes().build());
		event.put(SLIME_KING.get(), SlimeKingEntity.createAttributes().build());
		event.put(CRIMSON_BUTTERFLY.get(), CrimsonButterflyEntity.createAttributes().build());
		event.put(SAMOUSS_BOSS.get(), SamoussBossEntity.createAttributes().build());
		event.put(JERRY.get(), JerryEntity.createAttributes().build());
		event.put(BEN.get(), BenEntity.createAttributes().build());
	}
    // ══ enumExtensions (Raid.RaiderType) — PORT 1.21.1 ══
    // RaiderType.create() n'existe plus ; le type est declare dans
    // META-INF/enumextensions.json et resolu via Raid.RaiderType.valueOf("DEEP_LUCKY_BLOCK_SAMOUSS_BOSS").
    // 1.21.1 : la référence est appelée pour CHAQUE paramètre avec son index.
    // Paramètre 0 = Supplier<EntityType<? extends Raider>>, paramètre 1 = int[] (spawns par vague).
    public static Object enumExtSamoussParams(int index, Class<?> type) {
        if (index == 0) {
            return (java.util.function.Supplier<?>) () -> SAMOUSS_BOSS.get();
        }
        return new int[]{0, 4, 3, 3, 4, 4, 4, 2};
    }
}
