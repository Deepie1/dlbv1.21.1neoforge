package deepluckyblock.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.Map;
import java.util.HashMap;

public class AnvilUpdateProcedure {

    public static void execute(ItemStack left, ItemStack right, ItemStack output, net.minecraft.world.inventory.AnvilMenu menu) {

        if (right.getItem() == deepluckyblock.init.DeepLuckyBlockModItems.SHADOW_ESSENCE.get() && !left.isEmpty()) {

            Map<Enchantment, Integer> enchantments = deepluckyblock.util.DeepEnchant.map(left); // PORT 1.21.1

            if (!enchantments.isEmpty()) {

                output.setCount(1);

                if (deepluckyblock.util.DeepNbt.hasTag(left)) {
                    deepluckyblock.util.DeepNbt.setTag(output, deepluckyblock.util.DeepNbt.tag(left).copy());
                } else {
                    deepluckyblock.util.DeepNbt.setTag(output, null);
                }

                // PORT 1.21.1 : les enchantements ne sont plus dans la NBT
                // ("Enchantments") mais dans le composant ItemEnchantments.
                enchantments.forEach((e, l) -> deepluckyblock.util.DeepEnchant.setLevel(output, e, l * 2));

                menu.setMaximumCost(2);
                menu.repairItemCountCost = 0;

                right.shrink(1);
            }
        }
    }
}