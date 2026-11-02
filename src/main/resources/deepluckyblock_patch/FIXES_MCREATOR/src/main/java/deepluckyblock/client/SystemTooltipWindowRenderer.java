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
import net.minecraft.locale.Language;
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
 * T325-SYSTEM-PANEL-V2 : Panneau d'infobulle style « System Window » (Solo Leveling / ORV)
 * entièrement unifié, aéré et mis à l'échelle dynamiquement selon la taille de l'écran.
 *
 * Corrections clés (retours captures d'écran en jeu) :
 *  1) Terminologie exacte du mod uniquement : « ENCHANTEMENTS » & « COMBO ACTIF »
 *     (retrait total de « SKILLS & RUNES », « 1 RUNE », « SYSTEM · SCAN » et « AWAKENED SYNERGY »).
 *  2) Zéro double retour à la ligne cassé : tout le panneau est rendu dans un unique
 *     ClientTooltipComponent vectoriel -> ClientHooks ne re-coupe jamais les phrases au milieu.
 *  3) Hiérarchie typographique claire & marges respirantes :
 *     - Titre de l'objet (1.00x)
 *     - En-têtes de section espacés (0.86x, cyan #38BDF8 / or #FBBF24)
 *     - Noms d'enchantements / combos en blanc pur (0.92x) avec badge [Rareté] aligné à droite
 *     - Descriptions plus fines (0.80x) en blanc-glace haute lisibilité (#DCE7F5) derrière une
 *       fine barre verticale colorée, avec 6 px d'air entre chaque bloc.
 *  4) Police adaptative à la résolution + anti-débordement absolu : si l'écran / la fenêtre est
 *     petite ou en grand GUI Scale, le facteur d'échelle s'adapte automatiquement pour que
 *     100 % du panneau tienne toujours à l'écran sans jamais être coupé en haut ni en bas.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class SystemTooltipWindowRenderer {

    private SystemTooltipWindowRenderer() {}

    // Palette haute lisibilité (contraste AAA sur fond abyssal)
    private static final int BG_ABYSS_TOP     = 0xF6060D1D;
    private static final int BG_ABYSS_BOTTOM  = 0xF8030711;
    private static final int BORDER_MANA_TOP  = 0xFF38BDF8;
    private static final int BORDER_MANA_BOT  = 0xFF1D4ED8;
    private static final int CORNER_STARLIGHT = 0xFFE0F2FE;

    private static final int CYAN_HEADER      = 0xFF38BDF8;
    private static final int CYAN_HINT        = 0xFF7DD3FC;
    private static final int GOLD_HEADER      = 0xFFFBBF24;
    private static final int TEXT_BRIGHT      = 0xFFF8FAFC;
    private static final int TEXT_DESC_ICE    = 0xFFDCE7F5;

    // Échelles typographiques internes (relatives à uiScale)
    private static final float S_TITLE   = 1.00f;
    private static final float S_SECTION = 0.86f;
    private static final float S_ITEM    = 0.92f;
    private static final float S_ATTR    = 0.88f;
    private static final float S_DESC    = 0.80f;
    private static final float S_SUB     = 0.75f;

    private static final int MAX_SHIFT_ENCHANTS_PER_PAGE = 4;

    private static ItemStack trackedStack = ItemStack.EMPTY;
    private static int currentShiftPage = 0;
    private static int activeTotalShiftPages = 1;
    private static long lastShiftTooltipNs = 0L;

    private static int frameBoxX = -1;
    private static int frameBoxY = -1;
    private static int frameBoxW = 0;
    private static int frameBoxH = 0;

    // =========================================================================
    // Mod Event Bus : enregistrement du composant unifié
    // =========================================================================

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SystemCardData.class, SystemCardClient::new);
    }

    // =========================================================================
    // Modèle de lignes internes pré-mesurées en coordonnées logiques
    // =========================================================================

    private sealed interface CardRow permits
            TitleRow, SectionHeaderRow, EnchantGridRow, SplitItemRow,
            IndentedBarTextRow, IndentedBarSeqRow, PlainComponentRow, RuleRow, SpacerRow {}

    private record TitleRow(Component title, int accentColor, int y, int height) implements CardRow {}

    private record SectionHeaderRow(String title, String rightHint, boolean gold, int y, int height) implements CardRow {}

    private record EnchantGridRow(Component left, Component right, int y, int height) implements CardRow {}

    private record SplitItemRow(Component left, Component rightBadge, boolean goldLeader, int y, int height) implements CardRow {}

    private record IndentedBarTextRow(String text, int barColor, float relScale, int y, int height) implements CardRow {}

    private record IndentedBarSeqRow(FormattedCharSequence seq, int barColor, float relScale, int y, int height) implements CardRow {}

    private record PlainComponentRow(Component comp, float relScale, int defaultColor, int xOffset, int y, int height) implements CardRow {}

    private record RuleRow(int colorArgb, int y, int height) implements CardRow {}

    private record SpacerRow(int y, int height) implements CardRow {}

    public static final class SystemCardData implements TooltipComponent {
        private final int logicalWidth;
        private final int logicalHeight;
        private final float uiScale;
        private final boolean compensateSingleComponent;
        private final List<CardRow> rows;

        SystemCardData(int logicalWidth, int logicalHeight, float uiScale,
                       boolean compensateSingleComponent, List<CardRow> rows) {
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.uiScale = uiScale;
            this.compensateSingleComponent = compensateSingleComponent;
            this.rows = rows;
        }

        int logicalWidth() {
            return logicalWidth;
        }

        float uiScale() {
            return uiScale;
        }

        List<CardRow> rows() {
            return rows;
        }

        int scaledWidth() {
            return Math.max(80, (int) Math.ceil(logicalWidth * uiScale));
        }

        int scaledHeight() {
            int h = Math.max(24, (int) Math.ceil(logicalHeight * uiScale));
            return compensateSingleComponent ? (h + 2) : h;
        }
    }

    private record SystemCardClient(SystemCardData data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return data.scaledHeight();
        }

        @Override
        public int getWidth(Font font) {
            return data.scaledWidth();
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            // 1) Coins extérieurs Solo Leveling / ORV (1:1 pixels écran pour une netteté parfaite)
            if (frameBoxW > 0 && frameBoxH > 0 && Math.abs(x - frameBoxX) <= 3) {
                int left = frameBoxX - 4;
                int top = frameBoxY - 4;
                int right = frameBoxX + frameBoxW + 4;
                int bottom = frameBoxY + frameBoxH + 4;
                int arm = 6;

                if (right - left > 20) {
                    g.fill(left + 8, top, right - 8, top + 1, 0x887DD3FC);
                    g.fill(left + 8, bottom - 1, right - 8, bottom, 0x5538BDF8);
                }
                g.fill(left, top, left + arm, top + 1, CORNER_STARLIGHT);
                g.fill(left, top, left + 1, top + arm, CORNER_STARLIGHT);
                g.fill(right - arm, top, right, top + 1, CORNER_STARLIGHT);
                g.fill(right - 1, top, right, top + arm, CORNER_STARLIGHT);
                g.fill(left, bottom - 1, left + arm, bottom, CORNER_STARLIGHT);
                g.fill(left, bottom - arm, left + 1, bottom, CORNER_STARLIGHT);
                g.fill(right - arm, bottom - 1, right, bottom, CORNER_STARLIGHT);
                g.fill(right - 1, bottom - arm, right, bottom, CORNER_STARLIGHT);
            }

            // 2) Contenu interne mis à l'échelle proportionnellement à l'écran
            int lw = data.logicalWidth();
            float sc = data.uiScale();
            int padX = 4;
            int innerW = lw - padX * 2;

            // Passe 2a : filets horizontaux 1px et barres verticales d'accentuation en pixels écran exacts
            // (évite toute disparition sub-pixel quand sc < 1.0 sur petit écran)
            for (CardRow row : data.rows()) {
                if (row instanceof TitleRow tr) {
                    int barY = tr.y() + 11;
                    int third = Math.max(12, innerW / 3);
                    fillHairlineH(g, x, y, sc, padX, padX + 8, barY, tr.accentColor() | 0xFF000000);
                    fillHairlineH(g, x, y, sc, padX + 8, padX + third, barY, 0xCC38BDF8);
                    fillHairlineH(g, x, y, sc, padX + third, padX + third * 2, barY, 0x772563EB);
                    fillHairlineH(g, x, y, sc, padX + third * 2, padX + innerW, barY, 0x331E3A8A);
                } else if (row instanceof SectionHeaderRow sr) {
                    int lColor = sr.gold() ? 0x66F59E0B : 0x5538BDF8;
                    int titlePx = scaledWidth(font, sr.title(), S_SECTION);
                    int lineStart = padX + titlePx + 6;
                    int lineEnd = padX + innerW;
                    if (sr.rightHint() != null && !sr.rightHint().isEmpty()) {
                        int rw = scaledWidth(font, sr.rightHint(), S_SUB);
                        lineEnd = padX + innerW - rw - 6;
                    }
                    if (lineEnd > lineStart + 4) {
                        fillHairlineH(g, x, y, sc, lineStart, lineEnd, sr.y() + 5, lColor);
                    }
                } else if (row instanceof EnchantGridRow gr && gr.right() != null) {
                    int gap = 12;
                    int colW = Math.max(60, (innerW - gap) / 2);
                    fillHairlineV(g, x, y, sc, padX + colW + 5, gr.y() + 1, gr.y() + gr.height() - 1, 0x3438BDF8);
                } else if (row instanceof SplitItemRow sp && sp.rightBadge() != null) {
                    int badgePx = scaledWidth(font, sp.rightBadge(), S_SUB);
                    int maxLeftPx = Math.max(80, innerW - badgePx - 10);
                    int leftPx = Math.min(maxLeftPx, scaledWidth(font, sp.left(), S_ITEM));
                    int bx = padX + innerW - badgePx;
                    if (bx - (padX + 2 + leftPx) > 10) {
                        int leaderCol = sp.goldLeader() ? 0x2AF59E0B : 0x2638BDF8;
                        fillHairlineH(g, x, y, sc, padX + 6 + leftPx, bx - 4, sp.y() + 5, leaderCol);
                    }
                } else if (row instanceof IndentedBarTextRow ib) {
                    fillAccentBarV(g, x, y, sc, padX + 4, ib.y(), ib.y() + ib.height(), ib.barColor());
                } else if (row instanceof IndentedBarSeqRow iseq) {
                    fillAccentBarV(g, x, y, sc, padX + 4, iseq.y(), iseq.y() + iseq.height(), iseq.barColor());
                } else if (row instanceof RuleRow rr) {
                    fillHairlineH(g, x, y, sc, padX, padX + innerW, rr.y() + rr.height() / 2, rr.colorArgb());
                }
            }

            // Passe 2b : textes vectoriels mis à l'échelle
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(sc, sc, 1.0F);

            for (CardRow row : data.rows()) {
                if (row instanceof TitleRow tr) {
                    drawScaledComponent(g, font, tr.title(), padX, tr.y() + 1, S_TITLE, 0xFFFFFFFF);
                } else if (row instanceof SectionHeaderRow sr) {
                    int tColor = sr.gold() ? GOLD_HEADER : CYAN_HEADER;
                    drawScaledString(g, font, sr.title(), padX, sr.y() + 1, S_SECTION, tColor);
                    if (sr.rightHint() != null && !sr.rightHint().isEmpty()) {
                        int rw = scaledWidth(font, sr.rightHint(), S_SUB);
                        drawScaledString(g, font, sr.rightHint(), padX + innerW - rw, sr.y() + 2, S_SUB, CYAN_HINT);
                    }
                } else if (row instanceof EnchantGridRow gr) {
                    int gap = 12;
                    int colW = Math.max(60, (innerW - gap) / 2);
                    drawScaledTrimmed(g, font, gr.left(), padX + 2, gr.y() + 1, S_ITEM, colW - 4, TEXT_BRIGHT);
                    if (gr.right() != null) {
                        drawScaledTrimmed(g, font, gr.right(), padX + colW + gap, gr.y() + 1, S_ITEM, colW - 4, TEXT_BRIGHT);
                    }
                } else if (row instanceof SplitItemRow sp) {
                    int badgePx = sp.rightBadge() != null ? scaledWidth(font, sp.rightBadge(), S_SUB) : 0;
                    int maxLeftPx = sp.rightBadge() != null ? Math.max(80, innerW - badgePx - 10) : (innerW - 4);
                    drawScaledTrimmed(g, font, sp.left(), padX + 2, sp.y() + 1, S_ITEM, maxLeftPx, TEXT_BRIGHT);
                    if (sp.rightBadge() != null) {
                        int bx = padX + innerW - badgePx;
                        drawScaledComponent(g, font, sp.rightBadge(), bx, sp.y() + 2, S_SUB, 0xFFFFFFFF);
                    }
                } else if (row instanceof IndentedBarTextRow ib) {
                    drawScaledString(g, font, ib.text(), padX + 10, ib.y() + 1, ib.relScale(), TEXT_DESC_ICE);
                } else if (row instanceof IndentedBarSeqRow iseq) {
                    drawScaledSeq(g, font, iseq.seq(), padX + 10, iseq.y() + 1, iseq.relScale(), TEXT_DESC_ICE);
                } else if (row instanceof PlainComponentRow pr) {
                    drawScaledTrimmed(g, font, pr.comp(), padX + pr.xOffset(), pr.y() + 1,
                            pr.relScale(), innerW - pr.xOffset(), pr.defaultColor());
                }
            }

            g.pose().popPose();
        }

        private static void fillHairlineH(GuiGraphics g, int baseX, int baseY, float sc, int lx0, int lx1, int ly, int color) {
            int sx0 = baseX + Math.round(lx0 * sc);
            int sx1 = baseX + Math.round(lx1 * sc);
            int sy = baseY + Math.round(ly * sc);
            if (sx1 > sx0) {
                g.fill(sx0, sy, sx1, sy + 1, color);
            }
        }

        private static void fillHairlineV(GuiGraphics g, int baseX, int baseY, float sc, int lx, int ly0, int ly1, int color) {
            int sx = baseX + Math.round(lx * sc);
            int sy0 = baseY + Math.round(ly0 * sc);
            int sy1 = Math.max(sy0 + 1, baseY + Math.round(ly1 * sc));
            g.fill(sx, sy0, sx + 1, sy1, color);
        }

        private static void fillAccentBarV(GuiGraphics g, int baseX, int baseY, float sc, int lx, int ly0, int ly1, int color) {
            int sx = baseX + Math.round(lx * sc);
            int sy0 = baseY + Math.round(ly0 * sc);
            int sy1 = Math.max(sy0 + 1, baseY + Math.round(ly1 * sc));
            g.fill(sx, sy0, sx + 2, sy1, color);
        }

        private static int scaledWidth(Font font, String s, float relScale) {
            return (int) Math.ceil(font.width(s) * relScale);
        }

        private static int scaledWidth(Font font, Component c, float relScale) {
            return (int) Math.ceil(font.width(c) * relScale);
        }

        private static void drawScaledString(GuiGraphics g, Font font, String text, int x, int y, float relScale, int color) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(relScale, relScale, 1.0F);
            g.drawString(font, text, 0, 0, color, true);
            g.pose().popPose();
        }

        private static void drawScaledComponent(GuiGraphics g, Font font, Component comp, int x, int y, float relScale, int color) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(relScale, relScale, 1.0F);
            g.drawString(font, comp, 0, 0, color, true);
            g.pose().popPose();
        }

        private static void drawScaledSeq(GuiGraphics g, Font font, FormattedCharSequence seq, int x, int y, float relScale, int color) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(relScale, relScale, 1.0F);
            g.drawString(font, seq, 0, 0, color, true);
            g.pose().popPose();
        }

        private static void drawScaledTrimmed(GuiGraphics g, Font font, Component comp,
                                              int x, int y, float relScale, int maxLogicalPx, int color) {
            if (comp == null) return;
            int maxFontPx = Math.max(24, (int) Math.floor(maxLogicalPx / relScale));
            if (font.width(comp) <= maxFontPx) {
                drawScaledComponent(g, font, comp, x, y, relScale, color);
            } else {
                String ellipsis = "\u2026";
                int avail = Math.max(16, maxFontPx - font.width(ellipsis));
                FormattedText sub = font.substrByWidth(comp, avail);
                FormattedCharSequence seq = FormattedCharSequence.composite(
                        Language.getInstance().getVisualOrder(sub),
                        Component.literal(ellipsis).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                drawScaledSeq(g, font, seq, x, y, relScale, color);
            }
        }
    }

    // =========================================================================
    // Game Event Bus : construction du panneau, positionnement sûr & molette
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

            List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
            if (elements.isEmpty()) return;

            // 1) Extraction du titre, d'éventuels TooltipComponent images (ex: Bundle), et des attributs natifs
            Component itemTitle = extractComponent(elements.get(0));
            if (itemTitle == null) itemTitle = stack.getHoverName();

            List<TooltipComponent> preservedImages = new ArrayList<>();
            List<Component> nativeExtraLines = new ArrayList<>();
            for (int i = 1; i < elements.size(); i++) {
                Either<FormattedText, TooltipComponent> el = elements.get(i);
                if (el.right().isPresent()) {
                    preservedImages.add(el.right().get());
                } else if (el.left().isPresent()) {
                    Component c = extractComponent(el);
                    if (c == null || EnchantmentTooltipFixProcedure.isSystemManagedComponent(c)) continue;
                    if (c.getString().isBlank()) continue;
                    nativeExtraLines.add(c);
                }
            }

            List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(e -> {
                ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                return id == null ? "" : id.toString();
            }));

            int totalEnchants = entries.size();
            boolean fr = TranslationFallback.isFrench();
            int screenW = Math.max(280, event.getScreenWidth());
            int screenH = Math.max(180, event.getScreenHeight());

            // 2) Calcul de la largeur logique confortable (avant mise à l'échelle écran)
            int padX = 4;
            int logicalW = shift ? 308 : 236;
            logicalW = Math.max(logicalW, font.width(itemTitle) + padX * 2 + 8);

            if (!shift && totalEnchants >= 3) {
                int maxColPx = 0;
                for (Map.Entry<Enchantment, Integer> e : entries) {
                    ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                    if (id == null) continue;
                    Component c = decorateEnchantBullet(e.getKey(),
                            EnchantmentTooltipFixProcedure.formatEnchantTitle(e.getKey(), id, e.getValue()), false);
                    maxColPx = Math.max(maxColPx, (int) Math.ceil(font.width(c) * S_ITEM));
                }
                logicalW = Math.min(310, Math.max(logicalW, maxColPx * 2 + 28));
            }

            List<ComboDef> activeCombos = EnchantmentComboRegistry.getActiveCombosOnStack(stack, viewer);
            for (ComboDef c : activeCombos) {
                int comboLineW = (int) Math.ceil(font.width(Component.literal("\u2726 ").append(c.translatedName())) * S_ITEM)
                        + (int) Math.ceil(font.width(c.tier().badge()) * S_SUB) + 24;
                logicalW = Math.min(340, Math.max(logicalW, comboLineW));
            }

            int innerW = logicalW - padX * 2;

            // 3) Construction aérée des lignes logiques avec espacements nets
            List<CardRow> rows = new ArrayList<>();
            int curY = 2;

            int accent = EnchantAura.auraColor(stack);
            if (accent == 0) accent = CYAN_HEADER;
            rows.add(new TitleRow(itemTitle, accent, curY, 13));
            curY += 16; // +3 px d'air sous le filet du titre

            // --- SECTION : ENCHANTEMENTS ---
            String enchHeaderFmt = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_enchantments");
            String enchHeader = String.format(Locale.ROOT,
                    enchHeaderFmt != null ? enchHeaderFmt : (fr ? "\u2756 ENCHANTEMENTS (%s)" : "\u2756 ENCHANTMENTS (%s)"),
                    totalEnchants);

            if (!shift) {
                rows.add(new SectionHeaderRow(enchHeader, "", false, curY, 10));
                curY += 12;

                List<Component> formatted = new ArrayList<>(totalEnchants);
                for (Map.Entry<Enchantment, Integer> e : entries) {
                    ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                    if (id == null) continue;
                    formatted.add(decorateEnchantBullet(e.getKey(),
                            EnchantmentTooltipFixProcedure.formatEnchantTitle(e.getKey(), id, e.getValue()), false));
                }

                int rowH = 10;
                if (formatted.size() >= 3) {
                    for (int i = 0; i < formatted.size(); i += 2) {
                        Component left = formatted.get(i);
                        Component right = (i + 1 < formatted.size()) ? formatted.get(i + 1) : null;
                        rows.add(new EnchantGridRow(left, right, curY, rowH));
                        curY += rowH + 1;
                    }
                } else {
                    for (Component item : formatted) {
                        rows.add(new PlainComponentRow(item, S_ITEM, TEXT_BRIGHT, 2, curY, rowH));
                        curY += rowH + 1;
                    }
                }
            } else {
                lastShiftTooltipNs = System.nanoTime();
                int totalPages = Math.max(1, (totalEnchants + MAX_SHIFT_ENCHANTS_PER_PAGE - 1) / MAX_SHIFT_ENCHANTS_PER_PAGE);
                activeTotalShiftPages = totalPages;
                if (currentShiftPage >= totalPages) currentShiftPage = 0;

                String scrollHint = "";
                if (totalPages > 1) {
                    String tpl = TranslationFallback.resolve("tooltip.deep_lucky_block.system.scroll_hint");
                    scrollHint = String.format(Locale.ROOT,
                            tpl != null ? tpl : (fr ? "\u27f3 Molette %s/%s" : "\u27f3 Scroll %s/%s"),
                            currentShiftPage + 1, totalPages);
                }
                rows.add(new SectionHeaderRow(enchHeader, scrollHint, false, curY, 10));
                curY += 12;

                int startIdx = currentShiftPage * MAX_SHIFT_ENCHANTS_PER_PAGE;
                int endIdx = Math.min(totalEnchants, startIdx + MAX_SHIFT_ENCHANTS_PER_PAGE);
                int descMaxFontPx = Math.max(140, (int) Math.floor((innerW - 12) / S_DESC));
                int descLineH = Math.max(8, (int) Math.ceil(9 * S_DESC) + 1);

                for (int i = startIdx; i < endIdx; i++) {
                    Map.Entry<Enchantment, Integer> e = entries.get(i);
                    Enchantment ench = e.getKey();
                    int lvl = e.getValue();
                    ResourceLocation id = DeepEnchant.getKeySafe(ench);
                    if (id == null) continue;

                    Component leftTitle = decorateEnchantBullet(ench,
                            EnchantmentTooltipFixProcedure.formatEnchantTitle(ench, id, lvl), true);
                    Component rightBadge = null;
                    if (EnchantmentTooltipFixProcedure.isModEnchantmentId(id)) {
                        Rarity rar = CustomEnchantment.getRarityByPath(id.getPath());
                        if (rar != null) {
                            rightBadge = CustomEnchantment.formatRarityBadge(rar);
                        }
                    }
                    rows.add(new SplitItemRow(leftTitle, rightBadge, false, curY, 10));
                    curY += 11;

                    String cleanDesc = EnchantmentTooltipFixProcedure.resolveCleanDescription(id, lvl);
                    if (cleanDesc != null && !cleanDesc.isBlank()) {
                        int barCol = CustomEnchantment.isCurseMod(ench) ? 0xAAEF4444 : 0xAA38BDF8;
                        List<String> wrapped = wrapStringExact(font, cleanDesc, descMaxFontPx, 3);
                        for (String line : wrapped) {
                            rows.add(new IndentedBarTextRow(line, barCol, S_DESC, curY, descLineH));
                            curY += descLineH;
                        }
                    }

                    if (i < endIdx - 1) {
                        rows.add(new SpacerRow(curY, 5));
                        curY += 5;
                    }
                }
            }

            // --- SECTION : COMBO ACTIF (affiché uniquement si actif sur le joueur) ---
            if (!activeCombos.isEmpty()) {
                curY += 5; // Air net avant la section Combo
                String comboHeader;
                if (activeCombos.size() > 1) {
                    String fmt = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_combos");
                    comboHeader = String.format(Locale.ROOT,
                            fmt != null ? fmt : (fr ? "\u2756 COMBOS ACTIFS (%s)" : "\u2756 ACTIVE COMBOS (%s)"),
                            activeCombos.size());
                } else {
                    String s = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_combo");
                    comboHeader = s != null ? s : (fr ? "\u2756 COMBO ACTIF" : "\u2756 ACTIVE COMBO");
                }
                rows.add(new SectionHeaderRow(comboHeader, "", true, curY, 10));
                curY += 12;

                int synDescFontPx = Math.max(140, (int) Math.floor((innerW - 12) / S_DESC));
                int synPiecesFontPx = Math.max(150, (int) Math.floor((innerW - 12) / S_SUB));
                int synDescLineH = Math.max(8, (int) Math.ceil(9 * S_DESC) + 1);
                int synSubLineH = Math.max(7, (int) Math.ceil(9 * S_SUB) + 1);

                for (int ci = 0; ci < activeCombos.size(); ci++) {
                    ComboDef c = activeCombos.get(ci);
                    Component leftCombo = Component.literal("\u00a76\u2726 ").append(c.translatedName());
                    rows.add(new SplitItemRow(leftCombo, c.tier().badge(), true, curY, 10));
                    curY += 11;

                    if (shift) {
                        String resolvedDesc = TranslationFallback.resolve(c.descKey());
                        if (resolvedDesc == null || resolvedDesc.isBlank()) {
                            resolvedDesc = c.translatedDesc().getString();
                        }
                        for (String line : wrapStringExact(font, resolvedDesc, synDescFontPx, 3)) {
                            rows.add(new IndentedBarTextRow(line, 0xBBF59E0B, S_DESC, curY, synDescLineH));
                            curY += synDescLineH;
                        }

                        curY += 2;
                        MutableComponent piecesLine = TranslationFallback.translatableOrFallback(
                                        "tooltip.deep_lucky_block.combo_pieces_label",
                                        fr ? "Pi\u00e8ces : " : "Pieces: ")
                                .withStyle(ChatFormatting.AQUA)
                                .append(EnchantmentComboRegistry.buildComboPiecesSummary(c, viewer));
                        List<FormattedCharSequence> pieceSplits = font.split(piecesLine, synPiecesFontPx);
                        int maxPieceLines = Math.min(3, pieceSplits.size());
                        for (int pi = 0; pi < maxPieceLines; pi++) {
                            rows.add(new IndentedBarSeqRow(pieceSplits.get(pi), 0x6638BDF8, S_SUB, curY, synSubLineH));
                            curY += synSubLineH;
                        }
                    }

                    if (ci < activeCombos.size() - 1) {
                        curY += 4;
                    }
                }
            }

            // --- SECTION : ATTRIBUTS NATIFS & DPS RÉEL ---
            Component combatSummary = shift ? EnchantmentTooltipFixProcedure.buildCompactCombatSummary(stack) : null;
            if (!nativeExtraLines.isEmpty() || combatSummary != null) {
                curY += 4;
                rows.add(new RuleRow(0x5038BDF8, curY, 5));
                curY += 7;

                int attrLineH = Math.max(8, (int) Math.ceil(9 * S_ATTR) + 1);
                for (Component extra : nativeExtraLines) {
                    rows.add(new PlainComponentRow(extra, S_ATTR, 0xFFCBD5E1, 1, curY, attrLineH));
                    curY += attrLineH;
                }
                if (combatSummary != null) {
                    curY += 2;
                    int dpsLineH = Math.max(8, (int) Math.ceil(9 * S_DESC) + 1);
                    rows.add(new PlainComponentRow(combatSummary, S_DESC, TEXT_DESC_ICE, 2, curY, dpsLineH));
                    curY += dpsLineH;
                }
            }

            // --- PIED DE PANNEAU HORS SHIFT ---
            if (!shift) {
                curY += 3;
                rows.add(new RuleRow(0x3638BDF8, curY, 4));
                curY += 6;
                String holdShift = TranslationFallback.resolve("tooltip.deep_lucky_block.system.hold_shift_compact");
                if (holdShift == null) {
                    holdShift = fr
                            ? "\u25b8 Maintenez [SHIFT] pour voir les d\u00e9tails"
                            : "\u25b8 Hold [SHIFT] to view details";
                }
                int footH = Math.max(7, (int) Math.ceil(9 * S_SUB) + 1);
                rows.add(new PlainComponentRow(
                        Component.literal(holdShift).withStyle(ChatFormatting.AQUA),
                        S_SUB, CYAN_HINT, 2, curY, footH));
                curY += footH;
            }

            int logicalH = curY + 2;

            // 4) Calcul du facteur d'échelle adaptatif à l'écran (garantit 0 débordement sur petit écran !)
            float baseScale;
            if (screenH >= 420 && screenW >= 700) {
                baseScale = 0.94f;
            } else if (screenH >= 310 && screenW >= 520) {
                baseScale = 0.86f;
            } else if (screenH >= 255) {
                baseScale = 0.78f;
            } else {
                baseScale = 0.72f;
            }

            float maxAllowedW = Math.max(150.0f, screenW - 24.0f);
            float maxAllowedH = Math.max(110.0f, screenH - 22.0f);
            float fitScale = Math.min(baseScale, Math.min(maxAllowedW / logicalW, maxAllowedH / logicalH));
            fitScale = Math.max(0.50f, fitScale);

            boolean singleComponent = preservedImages.isEmpty();
            elements.clear();
            elements.add(Either.right(new SystemCardData(logicalW, logicalH, fitScale, singleComponent, rows)));
            for (TooltipComponent img : preservedImages) {
                elements.add(Either.right(img));
            }
            event.setMaxWidth(screenW);
        }

        @SubscribeEvent
        public static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty() || DeepEnchant.map(stack).isEmpty()) return;

            // Empêche DefaultTooltipPositioner de placer le haut du panneau hors écran (y - 12 < 6)
            if (event.getY() < 20) {
                event.setY(20);
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

    private static Component extractComponent(Either<FormattedText, TooltipComponent> el) {
        if (el.left().isEmpty()) return null;
        FormattedText ft = el.left().get();
        if (ft instanceof Component c) return c;
        return Component.literal(ft.getString());
    }

    private static Component decorateEnchantBullet(Enchantment ench, Component baseTitle, boolean brightWhite) {
        boolean curse = CustomEnchantment.isCurseMod(ench);
        MutableComponent bullet = Component.literal("\u25c6 ")
                .withStyle(curse ? ChatFormatting.RED : ChatFormatting.AQUA);
        if (curse) {
            return bullet.append(baseTitle.copy().withStyle(ChatFormatting.RED));
        }
        if (brightWhite) {
            return bullet.append(baseTitle.copy().withStyle(ChatFormatting.WHITE));
        }
        return bullet.append(baseTitle);
    }

    /**
     * Découpage mot-par-mot mesuré exactement en pixels Font, en conservant les codes couleur §
     * d'une ligne à l'autre sans jamais couper un mot orphelin.
     */
    private static List<String> wrapStringExact(Font font, String text, int maxFontPx, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        String activeColor = "";
        boolean hasWord = false;

        for (String word : words) {
            if (word.isEmpty()) continue;
            String candidate = hasWord ? (current + " " + word) : word;
            if (hasWord && font.width(candidate) > maxFontPx) {
                if (out.size() + 1 >= maxLines) {
                    out.add(current + "\u00a78\u2026");
                    return out;
                }
                out.add(current.toString());
                current = new StringBuilder(activeColor).append(word);
            } else {
                if (hasWord) current.append(' ');
                current.append(word);
                hasWord = true;
            }

            for (int i = 0; i < word.length() - 1; i++) {
                if (word.charAt(i) == '\u00a7') {
                    char code = Character.toLowerCase(word.charAt(i + 1));
                    activeColor = (code == 'r') ? "" : ("\u00a7" + code);
                }
            }
        }
        if (hasWord && out.size() < maxLines) {
            out.add(current.toString());
        }
        return out;
    }
}
