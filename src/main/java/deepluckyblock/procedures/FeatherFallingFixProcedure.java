package deepluckyblock.procedures;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import deepluckyblock.DeepLuckyBlockMod;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class FeatherFallingFixProcedure {

    @SubscribeEvent
    public static void onEntityHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        // Vérifie si la source de dégâts est une chute (Fall)
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            // Récupère les bottes et le niveau de Feather Falling (Chute amortie)
            ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
            int level = deepluckyblock.util.DeepEnchant.keyLevel(entity.level().registryAccess(), boots, Enchantments.FEATHER_FALLING);

            if (level > 0) {
                // Plafonne le niveau effectif pris en compte à 50 maximum
                int effectiveLevel = Math.min(50, level);

                // Formule : 2% par niveau -> 100% de réduction (immunité totale) au niveau 50
                float reductionPercent = effectiveLevel * 2.0f;

                float currentDamage = event.getAmount();
                float reducedDamage = currentDamage * (1.0f - (reductionPercent / 100.0f));

                if (reducedDamage < 0.0f) {
                    reducedDamage = 0.0f;
                }

                // Applique les dégâts corrigés
                event.setAmount(reducedDamage);
            }
        }
    }
}