package deepluckyblock.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.blockentity.DeepLuckyBlockBlockEntity;

/**
 * Registre canonique (et unique) des BlockEntityType du mod.
 *
 * IMPORTANT : DeepLuckyBlockModBlockEntities.REGISTRY.register(bus) est appele
 * dans le constructeur de DeepLuckyBlockMod. Ne PAS redeclarer ce type ailleurs
 * (l'ancien auto-enregistrement dans DeepLuckyBlockBlockEntity a ete supprime).
 */
public class DeepLuckyBlockModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DeepLuckyBlockMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeepLuckyBlockBlockEntity>> DEEP_LUCKY_BLOCK_ENTITY =
            REGISTRY.register("deep_lucky_block_entity", () -> BlockEntityType.Builder
                    .of(DeepLuckyBlockBlockEntity::new, DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get())
                    .build(null));
}
