package deepluckyblock.block;

import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

/**
 * PORT 1.21.1 :
 *  - ForgeSoundType -> DeferredSoundType (meme package NeoForge, meme constructeur)
 *  - BuiltInRegistries.SOUND_EVENT.get(...) -> BuiltInRegistries.SOUND_EVENT.get(...)
 *  - canSustainPlant renvoie desormais TriState (plus boolean) et le parametre
 *    plant est un BlockState (IPlantable a ete remplace par SpecialPlantable).
 */
public class BoCubeDeBoisBlock extends FallingBlock {
	public BoCubeDeBoisBlock() {
		super(BlockBehaviour.Properties.of()
				.sound(new DeferredSoundType(1.0f, 1.0f, () -> BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.anvil.break")), () -> BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.cherry_wood.step")),
						() -> BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.wood.place")), () -> BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.wood.fall")),
						() -> BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.wood.fall"))))
				.strength(-1, 3600000).lightLevel(blockstate -> 1).speedFactor(15f).jumpFactor(7f).hasPostProcess((bs, br, bp) -> true).emissiveRendering((bs, br, bp) -> true).ignitedByLava());
	}

	// PORT 1.21.1 : FallingBlock.codec() est maintenant abstrait (registre de blocs).
	@Override
	protected MapCodec<BoCubeDeBoisBlock> codec() {
		return simpleCodec(props -> new BoCubeDeBoisBlock());
	}

	// PORT 1.21.1 : appendHoverText prend (ItemStack, Item.TooltipContext, List, TooltipFlag).
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, context, list, flag);
		list.add(Component.translatable("block.deep_lucky_block.bo_cube_de_bois.description_0"));
		// T31 (consigne du 20/09) : ligne du bas « Incassable » -- le bloc l'est deja
		// (durete -1), la description le dit maintenant explicitement.
		list.add(Component.translatable("block.deep_lucky_block.bo_cube_de_bois.description_1"));
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return 2;
	}

	@Override
	public TriState canSustainPlant(BlockState state, BlockGetter world, BlockPos pos, Direction direction, BlockState plant) {
		return TriState.TRUE;
	}
}
