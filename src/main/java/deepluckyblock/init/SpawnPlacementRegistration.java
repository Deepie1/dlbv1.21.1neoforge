package deepluckyblock.init;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.entity.CrimsonButterflyEntity;
import deepluckyblock.entity.EndKnightEntity;
import deepluckyblock.entity.SlimeKingEntity;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * PORT 1.21.1 : SpawnPlacements.register(...) est private/deprecate —
 * les placements s'enregistrent via l'event NeoForge RegisterSpawnPlacementsEvent
 * (mod bus), et le type utilise SpawnPlacementTypes (enum de top-level).
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class SpawnPlacementRegistration {

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(DeepLuckyBlockModEntities.END_KNIGHT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, world, reason, pos, random) -> false,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(DeepLuckyBlockModEntities.CRIMSON_BUTTERFLY.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CrimsonButterflyEntity::checkCrimsonButterflySpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(DeepLuckyBlockModEntities.SLIME_KING.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SlimeKingEntity::checkSlimeKingSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // PORT 1.21.1 : le biome modifier de shadow_mirror ajoute des spawns ->
        // il faut un placement (sinon avertissement ServerLifecycleHooks).
        event.register(DeepLuckyBlockModEntities.SHADOW_MIRROR.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, world, reason, pos, random) -> true,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
