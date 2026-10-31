package deepluckyblock.item;

import deepluckyblock.procedures.ShadowEssenceRightClickProcedure;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import java.util.List;

public class ShadowEssenceItem extends Item {

    public ShadowEssenceItem() {
        super(new Item.Properties()
            .stacksTo(16)
            .fireResistant()
            .rarity(Rarity.EPIC));
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0f;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // PORT 1.21.1 : appendHoverText(ItemStack, Item.TooltipContext, List, TooltipFlag).
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.literal("§7Hold an enchanted item in your offhand"));
        tooltip.add(Component.literal("§7and right-click with this in your main hand"));
        tooltip.add(Component.literal("§7to §6§ldouble§7 all enchantments."));
        tooltip.add(Component.literal("§7Costs §c2§7 experience levels."));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> ar = super.use(world, player, hand);

        if (hand == InteractionHand.MAIN_HAND) {
            ShadowEssenceRightClickProcedure.execute(world, player.getX(), player.getY(), player.getZ(), player);
        }

        return ar;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        super.useOn(context);

        Player player = context.getPlayer();
        if (player != null && context.getHand() == InteractionHand.MAIN_HAND) {
            ShadowEssenceRightClickProcedure.execute(
                context.getLevel(),
                context.getClickLocation().x,
                context.getClickLocation().y,
                context.getClickLocation().z,
                player
            );
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            ShadowEssenceRightClickProcedure.execute(
                player.level(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player
            );
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state,
                             net.minecraft.core.BlockPos pos, LivingEntity entity) {
        if (entity instanceof Player player) {
            ShadowEssenceRightClickProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ(), player);
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }
}