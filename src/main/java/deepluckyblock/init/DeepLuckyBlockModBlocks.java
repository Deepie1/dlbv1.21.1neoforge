/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package deepluckyblock.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.Block;

import deepluckyblock.block.DeepLuckyBlockBlock;
import deepluckyblock.block.BoCubeDeBoisBlock;

import deepluckyblock.DeepLuckyBlockMod;

public class DeepLuckyBlockModBlocks {
	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(DeepLuckyBlockMod.MODID);
	public static final DeferredBlock<Block> DEEP_LUCKY_BLOCK;
	public static final DeferredBlock<Block> BO_CUBE_DE_BOIS;
	static {
		DEEP_LUCKY_BLOCK = REGISTRY.register("deep_lucky_block", DeepLuckyBlockBlock::new);
		BO_CUBE_DE_BOIS = REGISTRY.register("bo_cube_de_bois", BoCubeDeBoisBlock::new);
	}
	// Start of user code block custom blocks
	// End of user code block custom blocks
}