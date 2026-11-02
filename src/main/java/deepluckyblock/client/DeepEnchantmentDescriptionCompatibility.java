package deepluckyblock.client;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Compatibilite client avec Enchantment Descriptions (modid "enchdesc").
 *
 * Quand ce mod est present, il reste le gestionnaire de reference du texte
 * Hold Shift et des descriptions. Ce handler passe a LOWEST, apres les autres
 * fournisseurs de tooltip, et ne conserve qu'une description par enchantement,
 * y compris pour les enchantements Minecraft de base. Sans enchdesc, il ne
 * touche absolument pas aux tooltips du mod.
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class DeepEnchantmentDescriptionCompatibility {

    private static boolean diagnosticLogged;

    private DeepEnchantmentDescriptionCompatibility() {}

    public static boolean externalDescriptionModLoaded() {
        return ModList.get().isLoaded("enchdesc");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!externalDescriptionModLoaded()) return;

        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        // T338-HAND-ENCHANT-TOOLTIP : Dédoublonne aussi les équipements enchantés à la main et les livres enchantés.
        Map<Enchantment, Integer> enchMap = deepluckyblock.util.DeepEnchant.mapWithStored(stack);
        if (enchMap.isEmpty()) return;

        Map<String, String> descriptionKeys = new HashMap<>();
        Map<String, String> renderedDescriptions = new HashMap<>();
        Language language = Language.getInstance();

        // PORT 1.21.1 : lecture des composants ENCHANTMENTS + STORED_ENCHANTMENTS via DeepEnchant
        for (Map.Entry<Enchantment, Integer> enchEntry : enchMap.entrySet()) {
            Enchantment enchantment = enchEntry.getKey();
            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(enchantment);
            if (id == null) continue;

            String base = "enchantment." + id.getNamespace() + "." + id.getPath();
            addDescriptionKey(base + ".desc", base, language,
                    descriptionKeys, renderedDescriptions);
            addDescriptionKey(base + ".description", base, language,
                    descriptionKeys, renderedDescriptions);
        }
        if (descriptionKeys.isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        if (tooltip == null || tooltip.isEmpty()) return;

        // Regroupe les lignes qui representent la meme description, qu'elles
        // soient encore translatables ou deja converties en texte litteral.
        Map<String, List<Integer>> occurrences = new HashMap<>();
        for (int index = 0; index < tooltip.size(); index++) {
            Component line = tooltip.get(index);
            String identity = descriptionIdentity(
                    line, descriptionKeys, renderedDescriptions);
            if (identity != null) {
                occurrences.computeIfAbsent(identity, ignored -> new ArrayList<>())
                        .add(index);
            }
        }

        Set<Integer> remove = new HashSet<>();
        for (List<Integer> indices : occurrences.values()) {
            if (indices.size() <= 1) continue;

            // Enchantment Descriptions utilise la cle .desc avec DARK_GRAY.
            // Cette occurrence est prioritaire ; sinon on conserve la premiere.
            int keep = indices.get(0);
            int bestScore = descriptionLineScore(
                    tooltip.get(keep), descriptionKeys);
            for (int index : indices) {
                int score = descriptionLineScore(
                        tooltip.get(index), descriptionKeys);
                if (score > bestScore) {
                    keep = index;
                    bestScore = score;
                }
            }
            for (int index : indices) {
                if (index != keep) remove.add(index);
            }
        }

        // Si enchdesc a deja pose son propre message Hold Shift, supprime les
        // notices Deep equivalentes afin de lui laisser seul cette responsabilite.
        boolean enchdescNoticePresent = tooltip.stream()
                .anyMatch(line -> hasTranslationKey(
                        line, "enchdesc.activate.message"));
        if (enchdescNoticePresent) {
            for (int index = 0; index < tooltip.size(); index++) {
                if (remove.contains(index)) continue;
                Component line = tooltip.get(index);
                String key = translationKey(line);
                if (key != null && isDeepShiftNoticeKey(key)) {
                    remove.add(index);
                }
            }
        }

        if (!remove.isEmpty()) {
            List<Integer> descending = new ArrayList<>(remove);
            descending.sort(java.util.Comparator.reverseOrder());
            for (int index : descending) {
                if (index >= 0 && index < tooltip.size()) tooltip.remove(index);
            }
        }

        if (!diagnosticLogged) {
            diagnosticLogged = true;
            deepluckyblock.util.DebugLog.out("[DeepLuckyBlock/Tooltip] enchdesc detected; "
                    + "all enchantment descriptions delegated");
        }
    }

    private static void addDescriptionKey(
            String key, String identity, Language language,
            Map<String, String> keys, Map<String, String> rendered) {
        if (key == null || !language.has(key)) return;
        keys.put(key, identity);
        String text = normalize(language.getOrDefault(key, key));
        if (!text.isEmpty()) rendered.put(text, identity);
    }

    private static String descriptionIdentity(
            Component line, Map<String, String> keys,
            Map<String, String> renderedDescriptions) {
        String key = translationKey(line);
        if (key != null && keys.containsKey(key)) return keys.get(key);
        return renderedDescriptions.get(normalize(line.getString()));
    }

    private static int descriptionLineScore(
            Component line, Map<String, String> keys) {
        int score = 0;
        String key = translationKey(line);
        if (key != null && keys.containsKey(key)) score += 2;
        if (line.getStyle().getColor() != null
                && line.getStyle().getColor().getValue()
                == ChatFormatting.DARK_GRAY.getColor()) {
            score += 4;
        }
        return score;
    }

    private static boolean hasTranslationKey(Component line, String expected) {
        String key = translationKey(line);
        return expected.equals(key);
    }

    private static String translationKey(Component line) {
        if (line != null && line.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return null;
    }

    private static boolean isDeepShiftNoticeKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        boolean deepKey = lower.startsWith("tooltip.deep_lucky_block.")
                || lower.startsWith("message.deep_lucky_block.");
        return deepKey && (lower.contains("shift")
                || lower.contains("hold")
                || lower.contains("description"));
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
