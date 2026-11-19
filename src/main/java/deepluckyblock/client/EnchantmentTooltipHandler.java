package deepluckyblock.client;

import deepluckyblock.enchantments.WeaponSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 EnchantmentTooltipHandler — l'encart SKILL, et rien d'autre
 * ═══════════════════════════════════════════════════════════════
 *
 * <p><b>Ce que cette classe n'affiche PAS, et pourquoi.</b> Le nom de l'enchantement et sa
 * description sont déjà posés par {@code EnchantmentTooltipFixProcedure} — il les insère
 * lui-même dans la liste (nom en chiffres romains, puis la description sous SHIFT). Les
 * réafficher ici produirait deux fois le même texte sur chaque arme. C'est exactement le
 * défaut qu'avait la première version de ce fichier : trois lignes ajoutées dont deux
 * étaient des copies.</p>
 *
 * <p><b>Ce qu'elle ajoute, et qui n'existe nulle part ailleurs</b> — trois lignes qui
 * parlent de la <i>compétence</i>, pas de l'enchantement :</p>
 * <ol>
 *   <li>l'en-tête, <b>à la couleur exacte de l'élément</b> (vraie valeur RVB de
 *       {@code DeepElement}, pas un code de couleur approché) : comment on la lance et
 *       combien de temps elle met à revenir ;</li>
 *   <li>l'effet, en chiffres relevés dans le code : nombre d'impacts, durée ;</li>
 *   <li>la phrase qui dit ce qu'on voit.</li>
 * </ol>
 *
 * <p>Les trois clés vivent sous {@code skill.deep_lucky_block.*}, un espace de noms à
 * part : elles ne peuvent donc pas entrer en collision avec les clés d'enchantement du
 * mod, ni être écrasées par un export MCreator.</p>
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

        lines.add(Component.translatable("tooltip.deep_lucky_block.skill_header",
                        entry.cooldownSeconds())
                .withStyle(Style.EMPTY.withColor(rgb).withBold(true)));
        lines.add(Component.translatable("skill.deep_lucky_block." + path + ".1")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("skill.deep_lucky_block." + path + ".2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
