package deepluckyblock.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import java.util.HashMap;
import java.util.Map;

public class ShadowEssenceRightClickProcedure {

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null) return;
        if (world.isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        // Anti-spam & Anti-double exécution (debounce de 0.5 seconde = 500 ms)
        long now = System.currentTimeMillis();
        if (now - player.getPersistentData().getLong("slb_last_shadow_msg") < 500) {
            return;
        }
        player.getPersistentData().putLong("slb_last_shadow_msg", now);

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (!(mainHand.getItem() instanceof deepluckyblock.item.ShadowEssenceItem)) {
            return;
        }

        if (offHand.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cRien dans la main secondaire !"));
            return;
        }

        // PORT 1.21.1 : les enchantements vivent dans le composant ItemEnchantments -> DeepEnchant
        Map<Enchantment, Integer> enchants = deepluckyblock.util.DeepEnchant.map(offHand);
        if (enchants.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cL'item en offhand n'a pas d'enchantements !"));
            return;
        }

        if (player.experienceLevel < 2 && !player.isCreative()) {
            player.sendSystemMessage(Component.literal("§cPas assez de niveaux d'XP (2 requis) !"));
            return;
        }

        enchants.forEach((e, l) -> deepluckyblock.util.DeepEnchant.setLevel(offHand, e, l * 2));

        mainHand.shrink(1);

        if (!player.isCreative()) {
            player.giveExperienceLevels(-2);
        }

        player.sendSystemMessage(Component.literal("§aEnchantements doublés avec succès !"));
    }
}