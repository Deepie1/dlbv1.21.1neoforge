package deepluckyblock.item;

import deepluckyblock.block.DeepLuckyBlockBlock;
import deepluckyblock.init.DeepLuckyBlockModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DeepLuckyBlockItem extends BlockItem {

    public DeepLuckyBlockItem() {
        super(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get(), new Item.Properties().stacksTo(64));
    }

    public DeepLuckyBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public static int getLuckBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !deepluckyblock.util.DeepNbt.hasTag(stack)) return 0;
        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        if (tag == null || !tag.contains(DeepLuckyBlockBlock.NBT_LUCK_KEY)) return 0;
        return tag.getInt(DeepLuckyBlockBlock.NBT_LUCK_KEY);
    }

    public static void setLuckBonus(ItemStack stack, int luck) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack);
        tag.putInt(DeepLuckyBlockBlock.NBT_LUCK_KEY,
                Math.max(DeepLuckyBlockBlock.MIN_LUCK_BONUS, Math.min(DeepLuckyBlockBlock.MAX_LUCK_BONUS, luck)));
        deepluckyblock.util.DeepNbt.setTag(stack, tag);
    }

    @Override
    public Component getName(ItemStack stack) {
        int luck = getLuckBonus(stack);
        if (luck >= 100) {
            return Component.literal("§6§l✦ DEEP'S LUCKY BLOCK [MAX] ✦");
        } else if (luck <= -100) {
            return Component.literal("§4§l✦ DEEP'S LUCKY BLOCK [MAUDIT MAX] ✦");
        } else if (luck <= -50) {
            return Component.literal("§c§lDeep's Lucky Block §4[" + luck + "%]");
        } else if (luck < 0) {
            return Component.literal("§6§lDeep's Lucky Block §c[" + luck + "%]");
        } else if (luck >= 50) {
            return Component.literal("§6§lDeep's Lucky Block §b[+" + luck + "%]");
        } else if (luck > 0) {
            return Component.literal("§6§lDeep's Lucky Block §7[+" + luck + "%]");
        }
        return Component.literal("§6§lDeep's Lucky Block");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, context, list, flag);
    }
}