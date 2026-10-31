package deepluckyblock.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 EnchantmentTooltipHandler — Délégué à EnchantmentTooltipFixProcedure
 * ═══════════════════════════════════════════════════════════════
 *
 * ⚙️ FONCTIONNEMENT :
 * L'intégralité du rendu SHIFT (affichage du nom formaté en chiffres romains
 * suivi immédiatement par sa description en ligne 2) est désormais géré et unifié
 * dans EnchantmentTooltipFixProcedure.java pour garantir un ordre parfait.
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class EnchantmentTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // Géré intégralement par EnchantmentTooltipFixProcedure.java (zéro doublon / zéro conflit d'index)
    }
}