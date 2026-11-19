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
 * 🌀 EnchantmentTooltipHandler — l'encart SKILL
 * ═══════════════════════════════════════════════════════════════
 *
 * <p><b>Ce que cette classe n'affiche PAS.</b> Le nom et la description des enchantements
 * sont déjà posés par {@code EnchantmentTooltipFixProcedure}. Les réafficher produirait
 * deux fois le même texte — c'est le défaut qu'avait la première version de ce fichier.</p>
 *
 * <p><b>Les deux sortes de compétences, et pourquoi elles ne s'affichent pas pareil.</b></p>
 * <ul>
 *   <li>Les <b>quarante-cinq signatures</b> sont greffées sur de vrais enchantements, qui
 *       gardent leurs effets passifs : {@code advanced_sharpness} ajoute des dégâts à chaque
 *       coup, que l'on lance la compétence ou non. Leur ligne d'enchantement reste donc à sa
 *       place, et l'encart n'ajoute que ce qui concerne la compétence.</li>
 *   <li>Les <b>capacités activées</b> — invocation d'armée, détecteur de minerais — ne sont
 *       rien d'autre que leur pouvoir. Leur ligne d'enchantement a été retirée de la liste
 *       ({@code WeaponSkill.isSkillEnchantment}), donc l'encart doit porter <b>leur nom</b> :
 *       sans lui le joueur ne saurait plus ce qu'il a sur son objet.</li>
 * </ul>
 *
 * <p>Les textes vivent sous {@code skill.deep_lucky_block.*}, un espace de noms à part :
 * aucune collision possible avec les clés d'enchantement, et un export MCreator ne peut pas
 * les écraser.</p>
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class EnchantmentTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        List<WeaponSkill.Entry> skills;
        try {
            skills = WeaponSkill.allOn(stack);
        } catch (Throwable ignored) {
            return;
        }
        if (skills.isEmpty()) return;

        List<Component> lines = event.getToolTip();
        for (WeaponSkill.Entry entry : skills) {
            String path = entry.enchantPath();
            int rgb = entry.element().primary & 0xFFFFFF;

            Component header;
            if (entry.ownsEnchant()) {
                // La ligne d'enchantement a disparu de la liste : le nom passe ici. Le
                // declencheur suit le mode de lancement reel — army_summon est une
                // signature, elle part sur un mob, pas en l'air.
                String key = entry.cast() == WeaponSkill.Cast.IN_HAND
                        ? "tooltip.deep_lucky_block.skill_header_named"
                        : "tooltip.deep_lucky_block.skill_header_named_mob";
                header = Component.translatable(key,
                        Component.translatable("enchantment.deep_lucky_block." + path),
                        entry.cooldownSeconds());
            } else {
                header = Component.translatable("tooltip.deep_lucky_block.skill_header",
                        entry.cooldownSeconds());
            }
            lines.add(header.copy().withStyle(Style.EMPTY.withColor(rgb).withBold(true)));
            lines.add(Component.translatable("skill.deep_lucky_block." + path + ".1")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("skill.deep_lucky_block." + path + ".2")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
