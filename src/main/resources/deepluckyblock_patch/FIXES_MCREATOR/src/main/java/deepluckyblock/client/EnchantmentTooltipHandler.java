package deepluckyblock.client;

import deepluckyblock.enchantments.WeaponSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 EnchantmentTooltipHandler — l'encart SKILL
 * ═══════════════════════════════════════════════════════════════
 *
 * <p>Le rendu classique des enchantements (nom en chiffres romains + description sous SHIFT)
 * reste intégralement géré par {@code EnchantmentTooltipFixProcedure} : on ne le double pas.
 * Cette classe n'ajoute qu'une chose, et seulement sur les armes qui en portent une —
 * <b>l'encart de compétence</b>.</p>
 *
 * <p>Trois lignes, toujours les mêmes :</p>
 * <ol>
 *   <li>le mot <b>SKILL</b> suivi du nom de la compétence, <b>à la couleur exacte de son
 *       élément</b> (la vraie valeur RVB de {@code DeepElement}, pas un code de couleur
 *       approché : Minecraft 1.21 accepte les couleurs vraies dans un {@code Style}) ;</li>
 *   <li>la description : ce que fait le skill, puis comment on le lance, puis sa recharge ;</li>
 *   <li>rien d'autre. Pas de voile gris sur l'icône, pas de ligne « prêt » : <b>c'est le
 *       halo de l'arme qui dit si la compétence est chargée</b>, et il le dit en permanence,
 *       sans qu'on ait besoin de survoler l'objet.</li>
 * </ol>
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class EnchantmentTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        WeaponSkill.Entry entry;
        try {
            entry = WeaponSkill.firstOn(stack);
        } catch (Throwable ignored) {
            return;
        }
        if (entry == null) return;

        List<Component> lines = event.getToolTip();
        String path = entry.enchantPath();
        int rgb = entry.effect().element().primary & 0xFFFFFF;

        MutableComponent title = Component.translatable("tooltip.deep_lucky_block.skill")
                .append(Component.literal(" · "))
                .append(Component.translatable("enchantment.deep_lucky_block." + path));
        lines.add(title.withStyle(Style.EMPTY.withColor(rgb).withBold(true)));

        // La description porte déjà le mode de lancement ET la recharge : elles sont écrites
        // dans la même chaîne, donc traduites d'un bloc et impossibles à désynchroniser.
        lines.add(Component.translatable("enchantment.deep_lucky_block." + path + ".desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
