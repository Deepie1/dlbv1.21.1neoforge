package deepluckyblock.client;

import deepluckyblock.enchantments.WeaponSkill;
import deepluckyblock.procedures.EnchantmentTooltipFixProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 EnchantmentTooltipHandler — la section COMPÉTENCES de l'infobulle ordinaire
 * ═══════════════════════════════════════════════════════════════
 *
 * <p>Les objets gradés du Lucky Block s'affichent dans la fenêtre {@code [SYSTÈME]}, qui
 * dessine sa propre section COMPÉTENCES. Cette classe-ci s'occupe de <b>tous les autres
 * objets</b>, ceux qui gardent l'infobulle normale de Minecraft, et leur donne la même
 * structure que la liste d'enchantements juste au-dessus :</p>
 *
 * <ol>
 *   <li>un en-tête de section, <b>❖ COMPÉTENCES (n)</b> ;</li>
 *   <li>un titre par compétence, et <b>rien d'autre</b> — comme un enchantement ;</li>
 *   <li>le détail sous <b>Maj</b> seulement : le geste, la recharge, puis la description.</li>
 * </ol>
 *
 * <p><b>Pourquoi le titre est celui de l'enchantement.</b> Une compétence n'a pas de nom à
 * elle : elle est le pouvoir d'un enchantement. Lui en inventer un obligerait à écrire
 * quarante-cinq textes de plus pour dire ce que le nom de l'enchantement dit déjà.</p>
 *
 * <p><b>Pourquoi ces lignes sont marquées « gérées par le système ».</b> La fenêtre
 * {@code [SYSTÈME]} recopie en bas, dans la police des attributs, toute ligne qu'elle ne
 * reconnaît pas. Sans ce marquage, les compétences apparaîtraient deux fois sur un objet
 * gradé : une fois dans sa section, une fois en bas en petits caractères.</p>
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

        List<Component> lines = event.getToolTip();
        boolean detail = Screen.hasShiftDown();

        lines.add(EnchantmentTooltipFixProcedure.markSystemManaged(
                Component.literal(header(skills.size())).withStyle(ChatFormatting.AQUA)));

        for (WeaponSkill.Entry entry : skills) {
            lines.add(EnchantmentTooltipFixProcedure.markSystemManaged(title(stack, entry)));
            if (!detail) continue;

            lines.add(EnchantmentTooltipFixProcedure.markSystemManaged(
                    Component.literal(INDENT + trigger(entry)).withStyle(ChatFormatting.GRAY)));
            for (Component line : description(stack, entry)) {
                lines.add(EnchantmentTooltipFixProcedure.markSystemManaged(line));
            }
        }
    }

    /** « ❖ COMPÉTENCES (n) », exactement la forme de l'en-tête des enchantements. */
    private static String header(int count) {
        String fmt = deepluckyblock.util.TranslationFallback.resolve(
                "tooltip.deep_lucky_block.system.section_skills");
        if (fmt == null || fmt.isBlank()) fmt = "\u2756 SKILLS (%s)";
        try {
            return String.format(Locale.ROOT, fmt, count);
        } catch (Throwable ignored) {
            return fmt;
        }
    }

    /** Le nom de l'enchantement qui porte la compétence, mis en forme comme dans la liste. */
    private static Component title(ItemStack stack, WeaponSkill.Entry entry) {
        String path = entry.enchantPath();
        try {
            Enchantment ench = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:" + path);
            if (ench != null) {
                ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
                if (id != null) {
                    int lvl = Math.max(1, deepluckyblock.util.DeepEnchant.getLevel(stack, ench));
                    return EnchantmentTooltipFixProcedure.formatEnchantTitle(ench, id, lvl);
                }
            }
        } catch (Throwable ignored) {
            // enchantement introuvable : on retombe sur le nom traduit.
        }
        return Component.translatable("enchantment.deep_lucky_block." + path)
                .withStyle(ChatFormatting.AQUA);
    }

    /** Geste et recharge, en gras : les deux informations qu'on vient chercher. */
    private static String trigger(WeaponSkill.Entry entry) {
        String gesture = deepluckyblock.util.TranslationFallback.resolve(entry.cast().triggerKey());
        if (gesture == null || gesture.isBlank()) gesture = entry.cast().name();
        StringBuilder out = new StringBuilder("\u00a7l").append(gesture).append("\u00a7r\u00a77");
        if (entry.cooldownSeconds() > 0) {
            String tpl = deepluckyblock.util.TranslationFallback.resolve(
                    "tooltip.deep_lucky_block.cooldown_suffix");
            String txt;
            try {
                txt = tpl != null ? String.format(Locale.ROOT, tpl, entry.cooldownSeconds())
                        : " \u00b7 " + entry.cooldownSeconds() + " s";
            } catch (Throwable ignored) {
                txt = " \u00b7 " + entry.cooldownSeconds() + " s";
            }
            out.append("\u00a7l").append(txt).append("\u00a7r\u00a77");
        }
        return out.toString();
    }

    /**
     * La description : celle de l'auteur pour une capacité activée — son enchantement n'a
     * plus de ligne dans la liste, personne d'autre ne l'affiche — et le texte propre à la
     * compétence pour une signature, dont l'enchantement garde sa ligne plus haut.
     */
    private static List<Component> description(ItemStack stack, WeaponSkill.Entry entry) {
        String path = entry.enchantPath();
        if (entry.ownsEnchant()) {
            try {
                Enchantment ench = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:" + path);
                if (ench != null) {
                    ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
                    if (id != null) {
                        int lvl = Math.max(1, deepluckyblock.util.DeepEnchant.getLevel(stack, ench));
                        List<Component> authored = EnchantmentTooltipFixProcedure.describe(id, lvl);
                        if (!authored.isEmpty()) return authored;
                    }
                }
            } catch (Throwable ignored) {
                // on retombe sur les deux lignes de compétence ci-dessous.
            }
        }
        MutableComponent one = Component.translatable("skill.deep_lucky_block." + path + ".1")
                .withStyle(ChatFormatting.GRAY);
        MutableComponent two = Component.translatable("skill.deep_lucky_block." + path + ".2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        return List.of(Component.literal(INDENT).append(one), Component.literal(INDENT).append(two));
    }
}
