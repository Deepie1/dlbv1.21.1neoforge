package deepluckyblock.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import java.util.Map;
import deepluckyblock.network.DeepLuckyBlockModVariables;

public class DropsProcedure {
    public static void execute(LevelAccessor world, ItemStack itemstack) {
        DeepLuckyBlockModVariables.MapVariables.get(world).luck = Mth.nextInt(RandomSource.create(), 1, 10);
        DeepLuckyBlockModVariables.MapVariables.get(world).markSyncDirty();
        if (DeepLuckyBlockModVariables.MapVariables.get(world).luck <= Mth.nextInt(RandomSource.create(), 1, 10)) {
            if (DeepLuckyBlockModVariables.MapVariables.get(world).luck <= 10) {
                {
                    // PORT 1.21.1 : les constantes Enchantments sont des Holder et
                    // les enchantements vivent dans le composant ItemEnchantments.
                    Enchantment adp = deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access()).get(Enchantments.PROTECTION);
                    if (deepluckyblock.util.DeepEnchant.has(itemstack, adp)) {
                        deepluckyblock.util.DeepEnchant.remove(itemstack, adp);
                    }
                }
            }
        }
    }
}