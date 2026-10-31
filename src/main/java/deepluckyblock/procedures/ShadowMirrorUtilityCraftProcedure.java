package deepluckyblock.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.Map;
import java.util.HashMap;

public class ShadowMirrorUtilityCraftProcedure {

    public static void execute(ItemStack craftedItem) {
        if (craftedItem.isEmpty()) return;

        // PORT 1.21.1 : composant ItemEnchantments -> DeepEnchant
        Map<Enchantment, Integer> enchantments = deepluckyblock.util.DeepEnchant.map(craftedItem);

        if (!enchantments.isEmpty()) {
            enchantments.forEach((e, l) -> deepluckyblock.util.DeepEnchant.setLevel(craftedItem, e, l * 2));
        }
    }
}