package deepluckyblock.client;

import deepluckyblock.enchantments.WeaponSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 EnchantmentTooltipHandler — l'encart COMPÉTENCE
 * ═══════════════════════════════════════════════════════════════
 *
 * <p><b>Ce que cette classe n'affiche PAS.</b> Le nom et la description des enchantements
 * ordinaires sont déjà posés par {@code EnchantmentTooltipFixProcedure}. Les réafficher
 * produirait deux fois le même texte — c'est le défaut qu'avait la première version.</p>
 *
 * <p><b>Les deux sortes de compétences.</b></p>
 * <ul>
 *   <li>Les <b>signatures</b> sont greffées sur des enchantements qui gardent leurs effets
 *       passifs : {@code advanced_sharpness} ajoute des dégâts à chaque coup, compétence ou
 *       non. Leur ligne d'enchantement reste donc dans la liste, et l'encart n'ajoute que
 *       ce qui concerne la compétence.</li>
 *   <li>Les <b>capacités activées</b> — invocation d'armée, détecteur de minerais, grappin,
 *       traction, loyauté sincère, explosion de fusée — ne sont rien d'autre que leur
 *       pouvoir. Leur ligne a été retirée de la liste, donc l'encart porte <b>leur nom</b>
 *       et <b>la description de l'auteur</b> : sans eux, l'enchantement serait invisible.</li>
 * </ul>
 *
 * <p><b>Le geste affiché est celui qui existait déjà.</b> Chaque capacité garde le
 * déclencheur qu'elle avait avant d'entrer dans l'encart. Aucun n'a été ajouté : annoncer
 * une touche qui ne fait rien serait pire que ne rien annoncer du tout.</p>
 *
 * <p><b>Le gras.</b> La touche sur laquelle appuyer et le temps de recharge sont en gras.
 * Ce sont les deux informations qu'on vient chercher en survolant l'objet, et elles se
 * perdaient dans la phrase.</p>
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public class EnchantmentTooltipHandler {

    /** Retrait des lignes de détail, aligné sur celui de la liste d'enchantements. */
    private static final String INDENT = "    ";

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

        boolean detail = Screen.hasShiftDown();
        List<Component> lines = event.getToolTip();

        for (WeaponSkill.Entry entry : skills) {
            String path = entry.enchantPath();
            int rgb = entry.element().primary & 0xFFFFFF;
            Style titleStyle = Style.EMPTY.withColor(rgb).withBold(true);

            if (!entry.ownsEnchant()) {
                // Signature : son enchantement garde sa ligne et sa description plus haut.
                lines.add(Component.translatable("tooltip.deep_lucky_block.skill_header",
                        entry.cooldownSeconds()).withStyle(titleStyle));
                lines.add(Component.translatable("skill.deep_lucky_block." + path + ".1")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("skill.deep_lucky_block." + path + ".2")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                continue;
            }

            // Capacite activee : l'encart est le SEUL endroit ou elle apparait.
            lines.add(Component.translatable("tooltip.deep_lucky_block.skill_named",
                    Component.translatable("enchantment.deep_lucky_block." + path))
                    .withStyle(titleStyle));

            MutableComponent trigger = Component.literal(INDENT)
                    .append(Component.translatable(entry.cast().triggerKey())
                            .withStyle(ChatFormatting.BOLD));
            if (entry.cooldownSeconds() > 0) {
                // Pas de recharge inventee : seules celles qui existent vraiment
                // s'affichent. Quatre de ces capacites n'en ont aucune.
                trigger.append(Component.translatable("tooltip.deep_lucky_block.cooldown_suffix",
                        entry.cooldownSeconds()).withStyle(ChatFormatting.BOLD));
            }
            lines.add(trigger.withStyle(ChatFormatting.GRAY));

            if (!detail) continue;
            // La description de l'auteur, telle que la liste l'aurait affichee : meme
            // source, meme mise en forme, meme repli. Elle ne s'affiche que touche Maj
            // enfoncee, exactement comme les descriptions de la liste.
            appendAuthorDescription(lines, stack, path);
        }
    }

    /**
     * Ajoute la description écrite par l'auteur pour cet enchantement. Silencieuse si
     * l'enchantement est introuvable ou n'a pas de description : une capacité sans texte
     * garde son titre et son geste, ce qui suffit à la jouer.
     */
    private static void appendAuthorDescription(List<Component> lines, ItemStack stack, String path) {
        try {
            Enchantment en = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:" + path);
            if (en == null) return;
            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(en);
            if (id == null) return;
            int level = Math.max(1, deepluckyblock.util.DeepEnchant.getLevel(stack, en));
            lines.addAll(deepluckyblock.procedures.EnchantmentTooltipFixProcedure.describe(id, level));
        } catch (Throwable ignored) {
            // Une infobulle ne doit jamais faire tomber le rendu : au pire il manque
            // la description, le titre et le geste restent.
        }
    }
}
