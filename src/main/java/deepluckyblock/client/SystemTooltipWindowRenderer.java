package deepluckyblock.client;

import com.mojang.datafixers.util.Either;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.Rarity;
import deepluckyblock.enchantments.CustomEnchantment;
import deepluckyblock.enchantments.EnchantmentComboRegistry;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboDef;
import deepluckyblock.procedures.EnchantmentTooltipFixProcedure;
import deepluckyblock.util.DeepEnchant;
import deepluckyblock.util.TranslationFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * T322-SYSTEM-WINDOW : Mise en page holographique style « Fenêtre Système » (Solo Leveling / ORV)
 * pour tous les objets enchantés.
 *
 * Objectifs UX :
 *  1) Fenêtre élargie adaptative (~240-288 px hors Shift, ~316-340 px sous Shift selon l'écran)
 *     afin d'aérer la lecture et faire tenir les descriptions sur 1 à 2 lignes maximum.
 *  2) Hors Shift : grille 2 colonnes alignée au pixel dès 3 enchantements (8 runes = 4 lignes !)
 *     avec séparateurs vectoriels fins et sections claires (Runes / Synergie Éveillée / Attributs).
 *  3) Sous Shift : badge [Rareté] aligné à droite, descriptions concises sans répétition du nom,
 *     micro-séparateurs de 3 px entre runes, et pagination automatique à la molette au-delà de
 *     5 runes pour ne jamais déborder de l'écran.
 *  4) Cadre abyssal bleu nuit + bordure mana-cyan lumineuse + 4 coins holographiques ┏ ┓ ┗ ┛.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class SystemTooltipWindowRenderer {

    private SystemTooltipWindowRenderer() {}

    // Palette Système (Solo Leveling / Omniscient Reader's Viewpoint)
    private static final int BG_ABYSS_TOP      = 0xF4060D1C;
    private static final int BG_ABYSS_BOTTOM   = 0xF6030711;
    private static final int BORDER_MANA_TOP   = 0xFF38BDF8;
    private static final int BORDER_MANA_BOT   = 0xFF1D4ED8;
    private static final int CORNER_STARLIGHT  = 0xFFE0F2FE;
    private static final int CYAN_BRIGHT       = 0xFF38BDF8;
    private static final int CYAN_SOFT         = 0xFF7DD3FC;
    private static final int GOLD_SYNERGY      = 0xFFFBBF24;

    private static final int MAX_SHIFT_RUNES_PER_PAGE = 5;

    // État léger de pagination sous Shift & géométrie courante du cadre
    private static ItemStack trackedStack = ItemStack.EMPTY;
    private static int currentShiftPage = 0;
    private static int activeTotalShiftPages = 1;
    private static long lastShiftTooltipNs = 0L;

    private static int frameBoxX = -1;
    private static int frameBoxY = -1;
    private static int frameBoxW = 0;
    private static int frameBoxH = 0;

    // =========================================================================
    // Mod Event Bus : enregistrement des composants graphiques de la fenêtre
    // =========================================================================

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SystemHeaderData.class, SystemHeaderClient::new);
        event.register(SystemSectionData.class, SystemSectionClient::new);
        event.register(SystemRuneGridRowData.class, SystemRuneGridRowClient::new);
        event.register(SystemSplitRowData.class, SystemSplitRowClient::new);
        event.register(SystemRuleData.class, SystemRuleClient::new);
    }

    public record SystemHeaderData(int minWidth, String leftTitle, String rightTag, int accentColor)
            implements TooltipComponent {}

    public record SystemSectionData(int minWidth, String title, String rightHint, boolean goldTheme)
            implements TooltipComponent {}

    public record SystemRuneGridRowData(int minWidth, Component leftRune, Component rightRune)
            implements TooltipComponent {}

    public record SystemSplitRowData(int minWidth, Component leftText, Component rightBadge)
            implements TooltipComponent {}

    public record SystemRuleData(int minWidth, int height, int colorArgb)
            implements TooltipComponent {}

    // =========================================================================
    // Rendu vectoriel ClientTooltipComponent
    // =========================================================================

    private record SystemHeaderClient(SystemHeaderData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 12;
        }

        @Override
        public int getWidth(Font font) {
            int textW = font.width(data.leftTitle) + font.width(data.rightTag) + 18;
            return Math.max(data.minWidth, textW);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(getWidth(font), frameBoxW > 0 ? frameBoxW : data.minWidth);

            // 1) Coins holographiques extérieurs ┏ ┓ ┗ ┛ sur l'ensemble de la fenêtre Système
            if (frameBoxW > 0 && frameBoxH > 0 && Math.abs(x - frameBoxX) <= 2) {
                int left = frameBoxX - 4;
                int top = frameBoxY - 4;
                int right = frameBoxX + frameBoxW + 4;
                int bottom = frameBoxY + frameBoxH + 4;
                int arm = 6;

                // Lueurs horizontales centrales haute & basse
                if (right - left > 20) {
                    g.fill(left + 8, top, right - 8, top + 1, 0x887DD3FC);
                    g.fill(left + 8, bottom - 1, right - 8, bottom, 0x5538BDF8);
                }

                // Coin Haut-Gauche ┏
                g.fill(left, top, left + arm, top + 1, CORNER_STARLIGHT);
                g.fill(left, top, left + 1, top + arm, CORNER_STARLIGHT);
                // Coin Haut-Droit ┓
                g.fill(right - arm, top, right, top + 1, CORNER_STARLIGHT);
                g.fill(right - 1, top, right, top + arm, CORNER_STARLIGHT);
                // Coin Bas-Gauche ┗
                g.fill(left, bottom - 1, left + arm, bottom, CORNER_STARLIGHT);
                g.fill(left, bottom - arm, left + 1, bottom, CORNER_STARLIGHT);
                // Coin Bas-Droit ┛
                g.fill(right - arm, bottom - 1, right, bottom, CORNER_STARLIGHT);
                g.fill(right - 1, bottom - arm, right, bottom, CORNER_STARLIGHT);
            }

            // 2) Bandeau subtil [ SYSTEM ] + compteur de runes à droite
            g.fill(x, y, x + w, y + 9, 0x261E3A8A);
            g.drawString(font, data.leftTitle, x + 2, y + 1, CYAN_BRIGHT, true);
            if (data.rightTag != null && !data.rightTag.isEmpty()) {
                int rw = font.width(data.rightTag);
                g.drawString(font, data.rightTag, x + w - rw - 2, y + 1, CYAN_SOFT, true);
            }

            // 3) Filet lumineux cyan dégradé sous l'en-tête + joyau d'accentuation
            int third = Math.max(12, w / 3);
            g.fill(x, y + 10, x + 4, y + 11, data.accentColor | 0xFF000000);
            g.fill(x + 4, y + 10, x + third, y + 11, 0xCC38BDF8);
            g.fill(x + third, y + 10, x + third * 2, y + 11, 0x882563EB);
            g.fill(x + third * 2, y + 10, x + w, y + 11, 0x441E3A8A);
        }
    }

    private record SystemSectionClient(SystemSectionData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 11;
        }

        @Override
        public int getWidth(Font font) {
            int rw = (data.rightHint != null && !data.rightHint.isEmpty()) ? font.width(data.rightHint) + 12 : 0;
            return Math.max(data.minWidth, font.width(data.title) + rw + 20);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(getWidth(font), frameBoxW > 0 ? frameBoxW : data.minWidth);
            int titleColor = data.goldTheme ? GOLD_SYNERGY : CYAN_SOFT;
            int lineColor = data.goldTheme ? 0x66F59E0B : 0x5538BDF8;

            g.drawString(font, data.title, x + 1, y + 2, titleColor, true);
            int lineStart = x + font.width(data.title) + 6;
            int lineEnd = x + w - 2;

            if (data.rightHint != null && !data.rightHint.isEmpty()) {
                int hw = font.width(data.rightHint);
                g.drawString(font, data.rightHint, x + w - hw - 1, y + 2, CYAN_BRIGHT, true);
                lineEnd = x + w - hw - 7;
            }
            if (lineEnd > lineStart + 4) {
                g.fill(lineStart, y + 6, lineEnd, y + 7, lineColor);
            }
        }
    }

    private record SystemRuneGridRowClient(SystemRuneGridRowData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return data.minWidth;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(data.minWidth, frameBoxW > 0 ? frameBoxW : data.minWidth);
            int gap = 10;
            int colW = Math.max(70, (w - gap) / 2);

            drawTrimmed(font, g, data.leftRune, x + 2, y + 1, colW - 4);
            if (data.rightRune != null) {
                int sepX = x + colW + 4;
                g.fill(sepX, y + 2, sepX + 1, y + 8, 0x3338BDF8);
                drawTrimmed(font, g, data.rightRune, x + colW + gap, y + 1, colW - 4);
            }
        }

        private static void drawTrimmed(Font font, GuiGraphics g, Component comp, int x, int y, int maxPx) {
            if (comp == null) return;
            if (font.width(comp) <= maxPx) {
                g.drawString(font, comp, x, y, 0xFFE2E8F0, true);
            } else {
                String ellipsis = "\u2026";
                int avail = Math.max(16, maxPx - font.width(ellipsis));
                FormattedText sub = font.substrByWidth(comp, avail);
                FormattedCharSequence seq = FormattedCharSequence.composite(
                        net.minecraft.locale.Language.getInstance().getVisualOrder(sub),
                        Component.literal(ellipsis).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                g.drawString(font, seq, x, y, 0xFFE2E8F0, true);
            }
        }
    }

    private record SystemSplitRowClient(SystemSplitRowData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            int bw = data.rightBadge != null ? font.width(data.rightBadge) + 10 : 0;
            return Math.max(data.minWidth, font.width(data.leftText) + bw + 4);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(getWidth(font), frameBoxW > 0 ? frameBoxW : data.minWidth);
            g.drawString(font, data.leftText, x + 2, y + 1, 0xFFF1F5F9, true);
            if (data.rightBadge != null) {
                int lw = font.width(data.leftText);
                int bw = font.width(data.rightBadge);
                int bx = x + w - bw - 1;
                if (bx - (x + 2 + lw) > 12) {
                    g.fill(x + 6 + lw, y + 5, bx - 4, y + 6, 0x1F38BDF8);
                }
                g.drawString(font, data.rightBadge, bx, y + 1, 0xFFFFFFFF, true);
            }
        }
    }

    private record SystemRuleClient(SystemRuleData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return data.height;
        }

        @Override
        public int getWidth(Font font) {
            return data.minWidth;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(data.minWidth, frameBoxW > 0 ? frameBoxW : data.minWidth);
            int lineY = y + (data.height / 2);
            g.fill(x + 2, lineY, x + w - 2, lineY + 1, data.colorArgb);
        }
    }

    // =========================================================================
    // Game Event Bus : construction de la mise en page, couleurs & molette
    // =========================================================================

    @EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) return;

            Map<Enchantment, Integer> map = DeepEnchant.map(stack);
            if (map.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null) return;
            Font font = mc.font;
            Player viewer = mc.player;
            boolean shift = Screen.hasShiftDown();

            if (!ItemStack.isSameItemSameComponents(stack, trackedStack)) {
                trackedStack = stack.copy();
                currentShiftPage = 0;
            }
            if (!shift) {
                currentShiftPage = 0;
                activeTotalShiftPages = 1;
            }

            List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(e -> {
                ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                return id == null ? "" : id.toString();
            }));

            int screenW = Math.max(320, event.getScreenWidth());
            int totalRunes = entries.size();
            boolean fr = TranslationFallback.isFrench();

            // Calcul de la largeur cible élargie (confort de lecture optimal sans sortir de l'écran)
            int targetWidth;
            if (shift) {
                targetWidth = Math.min(336, Math.max(216, (int) (screenW * 0.55)));
            } else {
                int maxCompactAllowed = Math.min(288, Math.max(188, (int) (screenW * 0.48)));
                int baseCompact = Math.min(242, maxCompactAllowed);
                if (totalRunes >= 3) {
                    int widestRunePx = 0;
                    for (Map.Entry<Enchantment, Integer> e : entries) {
                        ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                        if (id == null) continue;
                        Component title = decorateBullet(e.getKey(),
                                EnchantmentTooltipFixProcedure.formatRuneTitle(e.getKey(), id, e.getValue()), false);
                        widestRunePx = Math.max(widestRunePx, font.width(title));
                    }
                    targetWidth = Math.min(maxCompactAllowed, Math.max(baseCompact, widestRunePx * 2 + 22));
                } else {
                    targetWidth = baseCompact;
                }
            }
            event.setMaxWidth(Math.max(targetWidth + 24, (int) (screenW * 0.68)));

            // Retrait propre des lignes texte provisoires marquées par EnchantmentTooltipFixProcedure
            List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
            if (elements.isEmpty()) return;

            for (int i = elements.size() - 1; i >= 1; i--) {
                Either<FormattedText, TooltipComponent> el = elements.get(i);
                boolean strip = el.left().filter(ft ->
                        ft instanceof Component c && EnchantmentTooltipFixProcedure.isSystemManagedComponent(c))
                        .isPresent();
                if (strip) {
                    elements.remove(i);
                }
            }

            // Point d'insertion : juste après le titre de l'objet (et éventuelle icône Bundle en index 1)
            int insertAt = 1;
            if (elements.size() > 1 && elements.get(1).right().isPresent()) {
                insertAt = 2;
            }

            List<Either<FormattedText, TooltipComponent>> sysBlock = new ArrayList<>();

            // 1) Bandeau supérieur [ SYSTÈME ] + compteur de runes
            String headerLeft = TranslationFallback.resolve(shift
                    ? "tooltip.deep_lucky_block.system.header_inspect"
                    : "tooltip.deep_lucky_block.system.header_scan");
            if (headerLeft == null) {
                headerLeft = shift
                        ? (fr ? "\u2756 [ SYST\u00c8ME \u00b7 D\u00c9TAILS ]" : "\u2756 [ SYSTEM \u00b7 DETAILS ]")
                        : (fr ? "\u2756 [ SYST\u00c8ME \u00b7 ANALYSE ]" : "\u2756 [ SYSTEM \u00b7 SCAN ]");
            }
            String countFmt = TranslationFallback.resolve(totalRunes == 1
                    ? "tooltip.deep_lucky_block.system.rune_single"
                    : "tooltip.deep_lucky_block.system.runes_count");
            String rightCount = totalRunes == 1
                    ? (countFmt != null ? countFmt : "1 RUNE")
                    : String.format(Locale.ROOT, countFmt != null ? countFmt : "%s RUNES", totalRunes);

            int accent = EnchantAura.auraColor(stack);
            if (accent == 0) accent = CYAN_BRIGHT;
            sysBlock.add(Either.right(new SystemHeaderData(targetWidth, headerLeft, rightCount, accent)));

            // 2) Résumé DPS / Armure effective compact (uniquement sous Shift)
            if (shift) {
                Component combatSummary = EnchantmentTooltipFixProcedure.buildCompactCombatSummary(stack);
                if (combatSummary != null) {
                    sysBlock.add(Either.left(combatSummary));
                }
            }

            // 3) Section COMPÉTENCES & RUNES
            String runesTitle = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_runes");
            if (runesTitle == null) runesTitle = fr ? "\u2756 COMP\u00c9TENCES & RUNES" : "\u2756 SKILLS & RUNES";

            if (!shift) {
                sysBlock.add(Either.right(new SystemSectionData(targetWidth, runesTitle, "", false)));
                List<Component> formattedRunes = new ArrayList<>(entries.size());
                for (Map.Entry<Enchantment, Integer> e : entries) {
                    ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                    if (id == null) continue;
                    formattedRunes.add(decorateBullet(e.getKey(),
                            EnchantmentTooltipFixProcedure.formatRuneTitle(e.getKey(), id, e.getValue()), false));
                }
                if (formattedRunes.size() >= 3) {
                    for (int i = 0; i < formattedRunes.size(); i += 2) {
                        Component left = formattedRunes.get(i);
                        Component right = (i + 1 < formattedRunes.size()) ? formattedRunes.get(i + 1) : null;
                        sysBlock.add(Either.right(new SystemRuneGridRowData(targetWidth, left, right)));
                    }
                } else {
                    for (Component r : formattedRunes) {
                        sysBlock.add(Either.left(Component.literal(" ").append(r)));
                    }
                }
            } else {
                lastShiftTooltipNs = System.nanoTime();
                int totalPages = Math.max(1, (totalRunes + MAX_SHIFT_RUNES_PER_PAGE - 1) / MAX_SHIFT_RUNES_PER_PAGE);
                activeTotalShiftPages = totalPages;
                if (currentShiftPage >= totalPages) currentShiftPage = 0;

                String scrollHint = "";
                if (totalPages > 1) {
                    String hintTpl = TranslationFallback.resolve("tooltip.deep_lucky_block.system.scroll_hint");
                    scrollHint = String.format(Locale.ROOT,
                            hintTpl != null ? hintTpl : (fr ? "\u27f3 Molette : %s/%s" : "\u27f3 Scroll: %s/%s"),
                            currentShiftPage + 1, totalPages);
                }
                sysBlock.add(Either.right(new SystemSectionData(targetWidth, runesTitle, scrollHint, false)));

                int startIdx = currentShiftPage * MAX_SHIFT_RUNES_PER_PAGE;
                int endIdx = Math.min(totalRunes, startIdx + MAX_SHIFT_RUNES_PER_PAGE);

                for (int i = startIdx; i < endIdx; i++) {
                    Map.Entry<Enchantment, Integer> e = entries.get(i);
                    Enchantment ench = e.getKey();
                    int lvl = e.getValue();
                    ResourceLocation id = DeepEnchant.getKeySafe(ench);
                    if (id == null) continue;

                    Component leftTitle = decorateBullet(ench,
                            EnchantmentTooltipFixProcedure.formatRuneTitle(ench, id, lvl), true);
                    Component rightBadge = null;
                    if (EnchantmentTooltipFixProcedure.isModEnchantmentId(id)) {
                        Rarity rar = CustomEnchantment.getRarityByPath(id.getPath());
                        if (rar != null) {
                            rightBadge = CustomEnchantment.formatRarityBadge(rar);
                        }
                    }
                    sysBlock.add(Either.right(new SystemSplitRowData(targetWidth, leftTitle, rightBadge)));

                    String cleanDesc = EnchantmentTooltipFixProcedure.resolveCleanDescription(id, lvl);
                    if (cleanDesc != null && !cleanDesc.isBlank()) {
                        for (Component descLine : wrapByPixelWidth(font, cleanDesc, targetWidth - 6,
                                "  \u00a7b\u2514 \u00a77", "    \u00a77", 3)) {
                            sysBlock.add(Either.left(descLine));
                        }
                    }

                    if (i < endIdx - 1) {
                        sysBlock.add(Either.right(new SystemRuleData(targetWidth, 3, 0x2638BDF8)));
                    }
                }
            }

            // 4) Section SYNERGIE ÉVEILLÉE (uniquement si un combo est actif sur cette pièce)
            List<ComboDef> activeCombos = EnchantmentComboRegistry.getActiveCombosOnStack(stack, viewer);
            if (!activeCombos.isEmpty()) {
                String synTitle = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_synergy");
                if (synTitle == null) synTitle = fr ? "\u2756 SYNERGIE \u00c9VEILL\u00c9E" : "\u2756 AWAKENED SYNERGY";
                sysBlock.add(Either.right(new SystemSectionData(targetWidth, synTitle, "", true)));

                for (ComboDef c : activeCombos) {
                    sysBlock.add(Either.left(Component.literal(" \u00a76\u2726 ")
                            .append(c.translatedName())
                            .append(" ")
                            .append(c.tier().badge())));
                    if (shift) {
                        String resolvedDesc = TranslationFallback.resolve(c.descKey());
                        if (resolvedDesc == null || resolvedDesc.isBlank()) {
                            resolvedDesc = c.translatedDesc().getString();
                        }
                        for (Component synLine : wrapByPixelWidth(font, resolvedDesc, targetWidth - 6,
                                "   \u00a7e\u25b8 \u00a77", "     \u00a77", 3)) {
                            sysBlock.add(Either.left(synLine));
                        }
                        MutableComponent piecesLabel = TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.combo_pieces_label",
                                fr ? "Pi\u00e8ces : " : "Pieces: ").withStyle(ChatFormatting.DARK_AQUA);
                        sysBlock.add(Either.left(Component.literal("   \u00a78\u2022 ")
                                .append(piecesLabel)
                                .append(EnchantmentComboRegistry.buildComboPiecesSummary(c, viewer))));
                    }
                }
            }

            // 5) Séparateur fin avant les Attributs / Lore restants (en supprimant la ligne vide vanilla)
            while (insertAt < elements.size()) {
                Either<FormattedText, TooltipComponent> nextEl = elements.get(insertAt);
                if (nextEl.left().isPresent() && nextEl.left().get().getString().isBlank()) {
                    elements.remove(insertAt);
                } else {
                    break;
                }
            }
            if (insertAt < elements.size()) {
                sysBlock.add(Either.right(new SystemRuleData(targetWidth, 5, 0x5038BDF8)));
            }

            elements.addAll(insertAt, sysBlock);

            // 6) Pied de fenêtre discret hors Shift
            if (!shift) {
                elements.add(Either.right(new SystemRuleData(targetWidth, 4, 0x3838BDF8)));
                String holdShift = TranslationFallback.resolve("tooltip.deep_lucky_block.system.hold_shift_compact");
                if (holdShift == null) {
                    holdShift = fr
                            ? "\u25b8 Maintenez [SHIFT] pour inspecter les runes"
                            : "\u25b8 Hold [SHIFT] to inspect rune details";
                }
                elements.add(Either.left(Component.literal(" " + holdShift).withStyle(ChatFormatting.DARK_AQUA)));
            }
        }

        @SubscribeEvent
        public static void onRenderTooltipColor(RenderTooltipEvent.Color event) {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty() || DeepEnchant.map(stack).isEmpty()) {
                frameBoxW = 0;
                frameBoxH = 0;
                return;
            }

            event.setBackgroundStart(BG_ABYSS_TOP);
            event.setBackgroundEnd(BG_ABYSS_BOTTOM);
            event.setBorderStart(BORDER_MANA_TOP);
            event.setBorderEnd(BORDER_MANA_BOT);

            Font font = event.getFont();
            List<ClientTooltipComponent> comps = event.getComponents();
            int w = 0;
            int h = comps.size() == 1 ? -2 : 0;
            for (ClientTooltipComponent c : comps) {
                w = Math.max(w, c.getWidth(font));
                h += c.getHeight();
            }
            frameBoxX = event.getX();
            frameBoxY = event.getY();
            frameBoxW = w;
            frameBoxH = h;
        }

        @SubscribeEvent
        public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
            if (!Screen.hasShiftDown() || activeTotalShiftPages <= 1) return;
            if (System.nanoTime() - lastShiftTooltipNs > 180_000_000L) return;
            double dy = event.getScrollDeltaY();
            if (Math.abs(dy) < 0.01) return;

            if (dy < 0) {
                currentShiftPage = (currentShiftPage + 1) % activeTotalShiftPages;
            } else {
                currentShiftPage = (currentShiftPage - 1 + activeTotalShiftPages) % activeTotalShiftPages;
            }
            event.setCanceled(true);
        }
    }

    private static List<Component> wrapByPixelWidth(Font font, String text, int maxWidthPx,
                                                    String firstPrefix, String contPrefix, int maxLines) {
        List<Component> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder(firstPrefix);
        String activeColor = "\u00a77";
        boolean hasWordOnLine = false;

        for (int wi = 0; wi < words.length; wi++) {
            String word = words[wi];
            if (word.isEmpty()) continue;

            String candidate = hasWordOnLine ? (current + " " + word) : (current + word);
            if (hasWordOnLine && font.width(candidate) > maxWidthPx) {
                if (out.size() + 1 >= maxLines) {
                    out.add(Component.literal(current + "\u00a78\u2026"));
                    return out;
                }
                out.add(Component.literal(current.toString()));
                current = new StringBuilder(contPrefix).append(activeColor).append(word);
                hasWordOnLine = true;
            } else {
                if (hasWordOnLine) current.append(' ');
                current.append(word);
                hasWordOnLine = true;
            }

            for (int ci = 0; ci < word.length() - 1; ci++) {
                if (word.charAt(ci) == '\u00a7') {
                    activeColor = "\u00a7" + word.charAt(ci + 1);
                }
            }
        }

        if (hasWordOnLine && out.size() < maxLines) {
            out.add(Component.literal(current.toString()));
        }
        return out;
    }

    private static Component decorateBullet(Enchantment ench, Component baseTitle, boolean highlightTitle) {
        boolean curse = CustomEnchantment.isCurseMod(ench);
        MutableComponent bullet = Component.literal("\u25c6 ")
                .withStyle(curse ? ChatFormatting.RED : ChatFormatting.AQUA);
        if (curse) {
            return bullet.append(baseTitle.copy().withStyle(ChatFormatting.RED));
        }
        if (highlightTitle) {
            return bullet.append(baseTitle.copy().withStyle(ChatFormatting.WHITE));
        }
        return bullet.append(baseTitle);
    }
}
