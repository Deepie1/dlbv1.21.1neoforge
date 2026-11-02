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
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
 * T325 / T326 : Panneau d'infobulle d'inspiration fantasy / manhwa sombre (sans aucun texte
 * littéral « SYSTEM » ou « SCAN »), aéré, adaptatif à la taille d'écran, avec :
 *  - Titre de l'objet centré en haut dans la police dédiée « Pixel Musketeer »
 *    (assets/deep_lucky_block/font/pixel_musketeer.json), légèrement agrandi et rendu brillant
 *    (halo elliptique respirant, ombre colorée prestige, reflet stellaire mobile et filet centré
 *    à gemme centrale).
 *  - Artefacts mystiques déterministes-aléatoires autour du titre lorsque l'objet possède des
 *    enchantements rares / épiques / mythiques / légendaires ou un combo actif (6 archétypes
 *    pixel-art distincts tirés par graine stable de l'objet).
 *  - Terminologie stricte du mod : « ENCHANTEMENTS » & « SYNERGIE ACTIVE » (uniquement lorsqu'une
 *    synergie est active sur le joueur).
 *  - Adaptation automatique de l'échelle à la résolution / GUI Scale pour zéro débordement.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class SystemTooltipWindowRenderer {

    private SystemTooltipWindowRenderer() {}

    private static final ResourceLocation PIXEL_MUSKETEER_FONT =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "pixel_musketeer");

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

    // Échelles typographiques internes (Pixel Musketeer a déjà des capitales de 9px vs 7px vanilla,
    // donc S_TITLE = 1.05f donne un titre subtilement plus grand et majestueux sans alourdir).
    private static final float S_TITLE   = 1.05f;
    private static final float S_SECTION = 0.86f;
    private static final float S_ITEM    = 0.92f;
    private static final float S_ATTR    = 0.88f;
    private static final float S_DESC    = 0.80f;
    private static final float S_SUB     = 0.75f;

    private static final float PANEL_SCALE_MULTIPLIER = 1.50f;
    private static final int MAX_SHIFT_ENCHANTS_STANDARD = 4;
    private static final int MAX_SHIFT_ENCHANTS_COMPACT_SCREEN = 3;

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

    /**
     * En-tête centré en police Pixel Musketeer, stylisé brillant, avec artefacts optionnels
     * (artifactVariant = -1 si aucun artefact, ou 0..5 selon la graine rare de l'objet).
     */
    private record TitleRow(
            Component styledTitle,
            int mainRgb,
            int brightRgb,
            int rarityTier,
            int artifactVariant,
            int itemSeed,
            int y,
            int height
    ) implements CardRow {}

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
            double timeSec = (System.nanoTime() * 1.0e-9);

            float sc = data.uiScale();

            // 1) Coins extérieurs lumineux proportionnels à l'échelle x1.5
            if (frameBoxW > 0 && frameBoxH > 0 && Math.abs(x - frameBoxX) <= 3) {
                int left = Math.max(1, frameBoxX - 4);
                int top = Math.max(1, frameBoxY - 4);
                int right = frameBoxX + frameBoxW + 4;
                int bottom = frameBoxY + frameBoxH + 4;
                int arm = Math.max(6, Math.round(6 * sc));
                int ct = sc >= 1.25f ? 2 : 1;

                if (right - left > arm * 2 + 8) {
                    g.fill(left + arm + 2, top, right - arm - 2, top + 1, 0x887DD3FC);
                    g.fill(left + arm + 2, bottom - 1, right - arm - 2, bottom, 0x5538BDF8);
                }
                g.fill(left, top, left + arm, top + ct, CORNER_STARLIGHT);
                g.fill(left, top, left + ct, top + arm, CORNER_STARLIGHT);
                g.fill(right - arm, top, right, top + ct, CORNER_STARLIGHT);
                g.fill(right - ct, top, right, top + arm, CORNER_STARLIGHT);
                g.fill(left, bottom - ct, left + arm, bottom, CORNER_STARLIGHT);
                g.fill(left, bottom - arm, left + ct, bottom, CORNER_STARLIGHT);
                g.fill(right - arm, bottom - ct, right, bottom, CORNER_STARLIGHT);
                g.fill(right - ct, bottom - arm, right, bottom, CORNER_STARLIGHT);
            }

            int lw = data.logicalWidth();
            int padX = 4;
            int innerW = lw - padX * 2;
            int centerX = lw / 2;

            // 2a) Halo du titre, artefacts pixel-art et filets 1px en coordonnées écran exactes
            for (CardRow row : data.rows()) {
                if (row instanceof TitleRow tr) {
                    renderTitleBackdropAndArtifacts(g, font, x, y, sc, lw, padX, innerW, centerX, tr, timeSec);
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

            // 2b) Rendu vectoriel des textes mis à l'échelle
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(sc, sc, 1.0F);

            for (CardRow row : data.rows()) {
                if (row instanceof TitleRow tr) {
                    drawRadiantCenteredTitle(g, font, tr, lw, innerW, timeSec);
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

        /**
         * Dessine le halo elliptique doux derrière le titre centré, le filet symétrique fuselé
         * à gemme centrale animée, ainsi que l'artefact mystique aléatoire de l'objet (si rare).
         */
        private static void renderTitleBackdropAndArtifacts(
                GuiGraphics g, Font font, int baseX, int baseY, float sc,
                int lw, int padX, int innerW, int centerX, TitleRow tr, double timeSec) {

            int rawTitleW = Math.max(12, font.width(tr.styledTitle()));
            int availForTitle = tr.artifactVariant() >= 0 ? Math.max(80, innerW - 44) : Math.max(80, innerW - 12);
            float effScale = Math.min(S_TITLE, availForTitle / (float) rawTitleW);
            int halfTitleW = Math.max(8, Math.round((rawTitleW * effScale) * 0.5f));

            float pulse = 0.86f + 0.14f * (float) Math.sin(timeSec * 2.4 + (tr.itemSeed() & 0xFF) * 0.05);
            int mainRgb = tr.mainRgb();
            int brightRgb = tr.brightRgb();
            int cy = tr.y() + 9; // centre vertical visuel des capitales Pixel Musketeer

            // 1) Halo elliptique progressif derrière le titre centré
            fillLogicalRect(g, baseX, baseY, sc, centerX - halfTitleW - 14, cy - 4, centerX + halfTitleW + 14, cy + 5,
                    withAlpha(mainRgb, Math.round(13 * pulse)));
            fillLogicalRect(g, baseX, baseY, sc, centerX - halfTitleW - 8, cy - 5, centerX + halfTitleW + 8, cy + 6,
                    withAlpha(mainRgb, Math.round(22 * pulse)));
            fillLogicalRect(g, baseX, baseY, sc, centerX - halfTitleW - 2, cy - 4, centerX + halfTitleW + 2, cy + 5,
                    withAlpha(brightRgb, Math.round(28 * pulse)));

            // 2) Filet symétrique fuselé centré sous le titre + éclat mobile + gemme centrale
            int barY = tr.y() + 18;
            int wingHalf = Math.min(innerW / 2 - 4, Math.max(halfTitleW + 22, innerW * 2 / 5));
            int midHalf = Math.max(18, wingHalf * 3 / 5);
            int coreHalf = Math.max(8, wingHalf / 4);

            fillHairlineH(g, baseX, baseY, sc, centerX - wingHalf, centerX + wingHalf, barY, withAlpha(mainRgb, 62));
            fillHairlineH(g, baseX, baseY, sc, centerX - midHalf, centerX + midHalf, barY, withAlpha(mainRgb, 148));
            fillHairlineH(g, baseX, baseY, sc, centerX - coreHalf, centerX + coreHalf, barY, withAlpha(brightRgb, 225));

            // Reflet stellaire glissant le long du filet
            int sweepOffset = (int) Math.round(Math.sin(timeSec * 1.85 + (tr.itemSeed() & 31)) * (midHalf - 6));
            fillHairlineH(g, baseX, baseY, sc, centerX + sweepOffset - 4, centerX + sweepOffset + 4, barY,
                    withAlpha(0xFFFFFF, 210));

            // Gemme losange au centre du filet (proportionnelle à l'échelle x1.5)
            int sxCenter = baseX + Math.round(centerX * sc);
            int syBar = baseY + Math.round(barY * sc);
            int gemOuter = Math.max(1, Math.round(1.2f * sc));
            int gemCore = Math.max(1, Math.round(0.8f * sc));
            g.fill(sxCenter - gemOuter, syBar - gemOuter, sxCenter + gemOuter + 1, syBar + gemOuter + 1, withAlpha(mainRgb, 230));
            g.fill(sxCenter, syBar, sxCenter + gemCore, syBar + gemCore, 0xFFFFFFFF);

            // 3) Artefacts mystiques autour du titre (uniquement si déclenchés par la rareté)
            if (tr.artifactVariant() < 0) return;

            int lx = centerX - halfTitleW - 6;
            int rx = centerX + halfTitleW + 6;
            int minX = padX + 2;
            int maxX = padX + innerW - 2;
            int bob = (int) Math.round(Math.sin(timeSec * 2.8 + (tr.itemSeed() & 15)) * 1.0);
            int twinkleA = 165 + (int) Math.round(85 * (0.5 + 0.5 * Math.sin(timeSec * 4.2 + ((tr.itemSeed() >>> 4) & 15))));

            switch (tr.artifactVariant()) {
                case 0 -> {
                    // Archétype 0 : Ailes Séraphiques étagées + poussière d'étoile
                    if (lx - 10 >= minX && rx + 10 <= maxX) {
                        fillPixelPip(g, baseX, baseY, sc, lx, cy, brightRgb, 240);
                        fillPixelPip(g, baseX, baseY, sc, rx - 1, cy, brightRgb, 240);

                        fillHairlineH(g, baseX, baseY, sc, Math.max(minX, lx - 8), lx - 2, cy - 2, withAlpha(mainRgb, 215));
                        fillHairlineH(g, baseX, baseY, sc, Math.max(minX, lx - 12), lx - 3, cy, withAlpha(brightRgb, 235));
                        fillHairlineH(g, baseX, baseY, sc, Math.max(minX, lx - 9), lx - 2, cy + 2, withAlpha(mainRgb, 175));

                        fillHairlineH(g, baseX, baseY, sc, rx + 2, Math.min(maxX, rx + 8), cy - 2, withAlpha(mainRgb, 215));
                        fillHairlineH(g, baseX, baseY, sc, rx + 3, Math.min(maxX, rx + 12), cy, withAlpha(brightRgb, 235));
                        fillHairlineH(g, baseX, baseY, sc, rx + 2, Math.min(maxX, rx + 9), cy + 2, withAlpha(mainRgb, 175));

                        if (lx - 15 >= minX && rx + 15 <= maxX) {
                            fillPixelPip(g, baseX, baseY, sc, lx - 15, cy - 1 + bob, 0xFFFFFF, twinkleA);
                            fillPixelPip(g, baseX, baseY, sc, rx + 14, cy - 1 - bob, 0xFFFFFF, twinkleA);
                        }
                    }
                }
                case 1 -> {
                    // Archétype 1 : Éclats de Relique Cristalline asymétriques en lévitation
                    int dyAsym = ((tr.itemSeed() >>> 9) & 1) == 0 ? -1 : 1;
                    for (int side : new int[]{-1, 1}) {
                        int bx = (side < 0) ? (lx - 2) : (rx + 2);
                        if (bx - 2 < minX || bx + 2 > maxX) continue;
                        int cyLocal = cy + (side < 0 ? bob : -bob);
                        // Petit cristal losange à cœur blanc
                        fillHairlineV(g, baseX, baseY, sc, bx, cyLocal - 1, cyLocal + 2, withAlpha(mainRgb, 220));
                        fillHairlineH(g, baseX, baseY, sc, bx - 1, bx + 2, cyLocal, withAlpha(mainRgb, 220));
                        fillPixelPip(g, baseX, baseY, sc, bx, cyLocal, 0xFFFFFF, 248);

                        int s1 = bx + side * 5;
                        int s2 = bx + side * 9;
                        if (s1 >= minX && s1 + 1 <= maxX) {
                            fillHairlineH(g, baseX, baseY, sc, Math.min(s1, s1 + side * 2), Math.max(s1, s1 + side * 2) + 1,
                                    cy - 2 * dyAsym, withAlpha(brightRgb, 215));
                        }
                        if (s2 >= minX && s2 < maxX) {
                            fillPixelPip(g, baseX, baseY, sc, s2, cy + 2 * dyAsym, 0xFFFFFF, twinkleA);
                        }
                    }
                }
                case 2 -> {
                    // Archétype 2 : Lames / Hallebardes Runiques jumelles (◄══╪  ...  ╪══►)
                    if (lx - 13 >= minX && rx + 13 <= maxX) {
                        // Garde verticale
                        fillHairlineV(g, baseX, baseY, sc, lx - 1, cy - 2, cy + 3, withAlpha(brightRgb, 230));
                        fillHairlineV(g, baseX, baseY, sc, rx + 1, cy - 2, cy + 3, withAlpha(brightRgb, 230));
                        // Hampe runique
                        fillHairlineH(g, baseX, baseY, sc, lx - 11, lx + 1, cy, withAlpha(mainRgb, 225));
                        fillHairlineH(g, baseX, baseY, sc, rx, rx + 12, cy, withAlpha(mainRgb, 225));
                        // Pointe étincelante
                        fillPixelPip(g, baseX, baseY, sc, lx - 12, cy, 0xFFFFFF, 245);
                        fillPixelPip(g, baseX, baseY, sc, rx + 12, cy, 0xFFFFFF, 245);
                        if (tr.rarityTier() >= 4 && lx - 15 >= minX && rx + 15 <= maxX) {
                            fillPixelPip(g, baseX, baseY, sc, lx - 15, cy + bob, brightRgb, twinkleA);
                            fillPixelPip(g, baseX, baseY, sc, rx + 15, cy - bob, brightRgb, twinkleA);
                        }
                    }
                }
                case 3 -> {
                    // Archétype 3 : Croissants Astraux & Étoiles Polaires latérales (sans gêner les accents hauts)
                    for (int side : new int[]{-1, 1}) {
                        int bx = (side < 0) ? (lx - 1) : (rx + 1);
                        if (bx - 3 < minX || bx + 3 > maxX) continue;
                        fillHairlineV(g, baseX, baseY, sc, bx, cy - 2, cy + 3, withAlpha(brightRgb, 230));
                        fillPixelPip(g, baseX, baseY, sc, bx - side, cy - 3, mainRgb, 195);
                        fillPixelPip(g, baseX, baseY, sc, bx - side, cy + 3, mainRgb, 195);

                        int starX = bx + side * 5;
                        if (starX - 1 >= minX && starX + 2 <= maxX) {
                            int sy = cy + (side < 0 ? bob : -bob);
                            fillHairlineH(g, baseX, baseY, sc, starX - 1, starX + 2, sy, withAlpha(brightRgb, twinkleA));
                            fillHairlineV(g, baseX, baseY, sc, starX, sy - 1, sy + 2, withAlpha(brightRgb, twinkleA));
                            fillPixelPip(g, baseX, baseY, sc, starX, sy, 0xFFFFFF, 250);
                        }
                    }
                }
                case 4 -> {
                    // Archétype 4 : Constellation Abyssale reliée par fils de mana
                    for (int side : new int[]{-1, 1}) {
                        int n1x = (side < 0) ? (lx - 1) : (rx + 1);
                        int n2x = n1x + side * 5;
                        int n3x = n1x + side * 10;
                        if (n3x < minX || n3x >= maxX) continue;

                        fillHairlineH(g, baseX, baseY, sc, Math.min(n1x, n2x), Math.max(n1x, n2x) + 1, cy - 1, withAlpha(mainRgb, 85));
                        fillHairlineH(g, baseX, baseY, sc, Math.min(n2x, n3x), Math.max(n2x, n3x) + 1, cy + 1, withAlpha(mainRgb, 70));

                        fillPixelPip(g, baseX, baseY, sc, n1x, cy + 1, 0xFFFFFF, 245);
                        fillPixelPip(g, baseX, baseY, sc, n2x, cy - 2, brightRgb, 225);
                        fillPixelPip(g, baseX, baseY, sc, n3x, cy + 1 + bob, 0xFFFFFF, twinkleA);
                    }
                }
                default -> {
                    // Archétype 5 : Sceaux Reliques Gothiques [❖ ... ❖]
                    if (lx - 6 >= minX && rx + 7 <= maxX) {
                        fillHairlineV(g, baseX, baseY, sc, lx - 6, cy - 3, cy + 4, withAlpha(mainRgb, 220));
                        fillHairlineH(g, baseX, baseY, sc, lx - 6, lx - 2, cy - 3, withAlpha(brightRgb, 230));
                        fillHairlineH(g, baseX, baseY, sc, lx - 6, lx - 2, cy + 3, withAlpha(brightRgb, 230));
                        fillPixelPip(g, baseX, baseY, sc, lx - 3, cy, 0xFFFFFF, twinkleA);

                        fillHairlineV(g, baseX, baseY, sc, rx + 6, cy - 3, cy + 4, withAlpha(mainRgb, 220));
                        fillHairlineH(g, baseX, baseY, sc, rx + 2, rx + 6, cy - 3, withAlpha(brightRgb, 230));
                        fillHairlineH(g, baseX, baseY, sc, rx + 2, rx + 6, cy + 3, withAlpha(brightRgb, 230));
                        fillPixelPip(g, baseX, baseY, sc, rx + 3, cy, 0xFFFFFF, twinkleA);
                    }
                }
            }
        }

        /**
         * Rend le titre centré avec Pixel Musketeer : ombre prestige colorée + léger bloom
         * supérieur + glyphes brillants haute netteté.
         */
        private static void drawRadiantCenteredTitle(GuiGraphics g, Font font, TitleRow tr,
                                                     int lw, int innerW, double timeSec) {
            Component comp = tr.styledTitle();
            int rawW = Math.max(1, font.width(comp));
            int availW = tr.artifactVariant() >= 0 ? Math.max(80, innerW - 44) : Math.max(80, innerW - 12);
            float effScale = Math.min(S_TITLE, availW / (float) rawW);

            float drawW = rawW * effScale;
            float drawX = (lw - drawW) * 0.5f;
            // Avec pixel_musketeer.json (height=16, ascent=13), getBearingY place le haut des accents
            // à drawY - 6*effScale : drawY = tr.y() + 6.3f positionne l'accent aigu exactement à tr.y().
            float drawY = tr.y() + 6.2f;

            int darkShadow = darkenRgb(tr.mainRgb(), 0.28f) | 0xDD000000;
            int shimmerAlpha = 36 + (int) Math.round(28 * (0.5 + 0.5 * Math.sin(timeSec * 3.0 + (tr.itemSeed() & 7))));
            int bloomColor = withAlpha(0xFFFFFF, shimmerAlpha);

            // Passe 1 : ombre prestige profonde (+0.85, +0.85)
            g.pose().pushPose();
            g.pose().translate(drawX + 0.85f, drawY + 0.85f, 0.0F);
            g.pose().scale(effScale, effScale, 1.0F);
            g.drawString(font, stripColorsForShadow(comp), 0, 0, darkShadow, false);
            g.pose().popPose();

            // Passe 2 : glyphes principaux lumineux (sans l'ombre grise terne vanilla)
            g.pose().pushPose();
            g.pose().translate(drawX, drawY, 0.0F);
            g.pose().scale(effScale, effScale, 1.0F);
            g.drawString(font, comp, 0, 0, tr.brightRgb() | 0xFF000000, false);
            g.pose().popPose();

            // Passe 3 : reflet brillant subtil respirant sur la crête des lettres (-0.30y)
            g.pose().pushPose();
            g.pose().translate(drawX, drawY - 0.30f, 0.0F);
            g.pose().scale(effScale, effScale, 1.0F);
            g.drawString(font, stripColorsForShadow(comp), 0, 0, bloomColor, false);
            g.pose().popPose();
        }

        private static void fillLogicalRect(GuiGraphics g, int baseX, int baseY, float sc,
                                            int lx0, int ly0, int lx1, int ly1, int color) {
            int sx0 = baseX + Math.round(lx0 * sc);
            int sy0 = baseY + Math.round(ly0 * sc);
            int sx1 = baseX + Math.round(lx1 * sc);
            int sy1 = baseY + Math.round(ly1 * sc);
            if (sx1 > sx0 && sy1 > sy0) {
                g.fill(sx0, sy0, sx1, sy1, color);
            }
        }

        private static void fillPixelPip(GuiGraphics g, int baseX, int baseY, float sc,
                                         int lx, int ly, int rgb, int alpha) {
            int sx = baseX + Math.round(lx * sc);
            int sy = baseY + Math.round(ly * sc);
            int sz = Math.max(1, Math.round(sc));
            g.fill(sx, sy, sx + sz, sy + sz, withAlpha(rgb, alpha));
        }

        private static void fillHairlineH(GuiGraphics g, int baseX, int baseY, float sc, int lx0, int lx1, int ly, int color) {
            int sx0 = baseX + Math.round(lx0 * sc);
            int sx1 = baseX + Math.round(lx1 * sc);
            int sy = baseY + Math.round(ly * sc);
            int th = sc >= 1.35f ? 2 : 1;
            if (sx1 > sx0) {
                g.fill(sx0, sy, sx1, sy + th, color);
            }
        }

        private static void fillHairlineV(GuiGraphics g, int baseX, int baseY, float sc, int lx, int ly0, int ly1, int color) {
            int sx = baseX + Math.round(lx * sc);
            int sy0 = baseY + Math.round(ly0 * sc);
            int th = sc >= 1.35f ? 2 : 1;
            int sy1 = Math.max(sy0 + th, baseY + Math.round(ly1 * sc));
            g.fill(sx, sy0, sx + th, sy1, color);
        }

        private static void fillAccentBarV(GuiGraphics g, int baseX, int baseY, float sc, int lx, int ly0, int ly1, int color) {
            int sx = baseX + Math.round(lx * sc);
            int sy0 = baseY + Math.round(ly0 * sc);
            int barW = Math.max(2, Math.round(1.8f * sc));
            int sy1 = Math.max(sy0 + 1, baseY + Math.round(ly1 * sc));
            g.fill(sx, sy0, sx + barW, sy1, color);
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
            Component rawItemTitle = extractComponent(elements.get(0));
            if (rawItemTitle == null) rawItemTitle = stack.getHoverName();

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

            List<ComboDef> activeCombos = EnchantmentComboRegistry.getActiveCombosOnStack(stack, viewer);

            // 2) Évaluation de la rareté prestige, de la graine déterministe et de l'artefact du titre
            int maxRarityTier = computeMaxRarityTier(entries, activeCombos);
            int itemSeed = computeDeterministicSeed(stack, rawItemTitle, entries);
            int artifactVariant = chooseArtifactVariant(maxRarityTier, itemSeed);

            int mainPrestigeRgb = prestigeMainRgb(maxRarityTier);
            int auraRgb = EnchantAura.auraColor(stack) & 0xFFFFFF;
            if (auraRgb != 0 && maxRarityTier >= 2) {
                mainPrestigeRgb = blendRgb(mainPrestigeRgb, auraRgb, 0.30f);
            }
            int brightPrestigeRgb = blendRgb(mainPrestigeRgb, 0xFFFFFF, 0.72f);

            Component styledTitle = stylizeTitleWithMusketeer(rawItemTitle, brightPrestigeRgb);
            int titleScaledW = (int) Math.ceil(font.width(styledTitle) * S_TITLE);

            int totalEnchants = entries.size();
            boolean fr = TranslationFallback.isFrench();
            int screenW = Math.max(280, event.getScreenWidth());
            int screenH = Math.max(180, event.getScreenHeight());

            // 3) Calcul de la largeur logique confortable (centrage du titre + artefacts inclus)
            int padX = 4;
            int logicalW = shift ? 308 : 238;
            int minTitleBoxW = titleScaledW + padX * 2 + (artifactVariant >= 0 ? 48 : 20);
            logicalW = Math.min(344, Math.max(logicalW, minTitleBoxW));

            if (!shift && totalEnchants >= 3) {
                int maxColPx = 0;
                for (Map.Entry<Enchantment, Integer> e : entries) {
                    ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
                    if (id == null) continue;
                    Component c = decorateEnchantBullet(e.getKey(),
                            EnchantmentTooltipFixProcedure.formatEnchantTitle(e.getKey(), id, e.getValue()), false);
                    maxColPx = Math.max(maxColPx, (int) Math.ceil(font.width(c) * S_ITEM));
                }
                logicalW = Math.min(320, Math.max(logicalW, maxColPx * 2 + 28));
            }

            for (ComboDef c : activeCombos) {
                int comboLineW = (int) Math.ceil(font.width(Component.literal("\u2726 ").append(c.translatedName())) * S_ITEM)
                        + (int) Math.ceil(font.width(c.tier().badge()) * S_SUB) + 24;
                logicalW = Math.min(344, Math.max(logicalW, comboLineW));
            }

            int innerW = logicalW - padX * 2;

            // 4) Construction aérée des lignes logiques
            List<CardRow> rows = new ArrayList<>();
            int curY = 2;

            rows.add(new TitleRow(styledTitle, mainPrestigeRgb, brightPrestigeRgb,
                    maxRarityTier, artifactVariant, itemSeed, curY, 21));
            curY += 23; // Espace respirant sous le filet à gemme centrale

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
                int maxPerPage = (screenH < 360) ? MAX_SHIFT_ENCHANTS_COMPACT_SCREEN : MAX_SHIFT_ENCHANTS_STANDARD;
                int totalPages = Math.max(1, (totalEnchants + maxPerPage - 1) / maxPerPage);
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

                int startIdx = currentShiftPage * maxPerPage;
                int endIdx = Math.min(totalEnchants, startIdx + maxPerPage);
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

            // --- SECTION : SYNERGIE ACTIVE (affichée uniquement lorsqu'une synergie est active) ---
            if (!activeCombos.isEmpty()) {
                curY += 5;
                String comboHeader;
                if (activeCombos.size() > 1) {
                    String fmt = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_combos");
                    comboHeader = String.format(Locale.ROOT,
                            fmt != null ? fmt : (fr ? "\u2756 SYNERGIES ACTIVES (%s)" : "\u2756 ACTIVE SYNERGIES (%s)"),
                            activeCombos.size());
                } else {
                    String s = TranslationFallback.resolve("tooltip.deep_lucky_block.system.section_combo");
                    comboHeader = s != null ? s : (fr ? "\u2756 SYNERGIE ACTIVE" : "\u2756 ACTIVE SYNERGY");
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

            // 5) T328 : Facteur d'échelle agrandi x1.5 (pannel + polices), tout en conservant le
            // plafond strict anti-débordement pour ne jamais dépasser de la fenêtre
            float refScale;
            if (screenH >= 420 && screenW >= 700) {
                refScale = 0.94f;
            } else if (screenH >= 310 && screenW >= 520) {
                refScale = 0.86f;
            } else if (screenH >= 255) {
                refScale = 0.78f;
            } else {
                refScale = 0.72f;
            }
            float baseScale = refScale * PANEL_SCALE_MULTIPLIER; // 1.08f .. 1.41f (x1.5)

            float maxAllowedW = Math.max(150.0f, screenW - 18.0f);
            float maxAllowedH = Math.max(110.0f, screenH - 18.0f);
            float screenFitCap = Math.min(maxAllowedW / logicalW, maxAllowedH / logicalH);
            float fitScale = Math.min(baseScale, screenFitCap);
            fitScale = Math.min(screenFitCap, Math.max(0.55f, fitScale));

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

    // =========================================================================
    // Utilitaires de style Pixel Musketeer, rareté, graine et couleurs
    // =========================================================================

    private static int computeMaxRarityTier(List<Map.Entry<Enchantment, Integer>> entries, List<ComboDef> activeCombos) {
        int tier = 0;
        if (!activeCombos.isEmpty()) {
            tier = 6;
        }
        for (Map.Entry<Enchantment, Integer> e : entries) {
            ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
            if (id != null && EnchantmentTooltipFixProcedure.isModEnchantmentId(id)) {
                Rarity r = CustomEnchantment.getRarityByPath(id.getPath());
                if (r != null) {
                    int rt = switch (r) {
                        case COMMON -> 0;
                        case UNCOMMON -> 1;
                        case RARE -> 2;
                        case VERY_RARE -> 3;
                        case EPIC -> 4;
                        case MYTHIC -> 5;
                        case LEGENDARY -> 6;
                    };
                    tier = Math.max(tier, rt);
                }
            } else if (e.getValue() >= 5) {
                tier = Math.max(tier, 2);
            }
        }
        if (entries.size() >= 6) {
            tier = Math.max(tier, 3);
        } else if (entries.size() >= 4) {
            tier = Math.max(tier, 2);
        }
        return tier;
    }

    private static int computeDeterministicSeed(ItemStack stack, Component rawTitle,
                                                List<Map.Entry<Enchantment, Integer>> entries) {
        int h = 0x9E3779B9;
        h = mix32(h ^ stack.getItem().toString().hashCode());
        if (rawTitle != null) {
            h = mix32(h ^ rawTitle.getString().hashCode());
        }
        for (Map.Entry<Enchantment, Integer> e : entries) {
            ResourceLocation id = DeepEnchant.getKeySafe(e.getKey());
            if (id != null) {
                h = mix32(h ^ id.toString().hashCode());
            }
            h = mix32(h ^ (e.getValue() * 0x85EBCA6B));
        }
        return h;
    }

    private static int mix32(int z) {
        z ^= (z >>> 16);
        z *= 0x7FEB352D;
        z ^= (z >>> 15);
        z *= 0x846CA68B;
        z ^= (z >>> 16);
        return z;
    }

    /**
     * Retourne -1 (pas d'artefact) ou un archétype 0..5 de manière déterministe-aléatoire
     * selon la rareté de l'objet : absent sur Commun, occasionnel sur Peu commun / Rare,
     * fréquent sur Très rare / Épique, garanti sur Mythique / Légendaire / Combo actif.
     */
    private static int chooseArtifactVariant(int rarityTier, int itemSeed) {
        if (rarityTier <= 0) return -1;
        int roll = (itemSeed >>> 16) & 0xFF; // 0..255
        int threshold = switch (rarityTier) {
            case 1 -> 52;   // ~20% Peu commun
            case 2 -> 148;  // ~58% Rare
            case 3 -> 196;  // ~77% Très rare
            case 4 -> 232;  // ~91% Épique
            default -> 256; // 100% Mythique / Légendaire / Combo actif
        };
        if (roll >= threshold) return -1;
        return Math.floorMod(itemSeed, 6);
    }

    private static int prestigeMainRgb(int rarityTier) {
        return switch (rarityTier) {
            case 6 -> 0xFACC15; // Or solaire Légendaire / Combo
            case 5 -> 0xF472B6; // Rose céleste Mythique
            case 4 -> 0xC084FC; // Améthyste astrale Épique
            case 3 -> 0x60A5FA; // Azur profond Très rare
            case 2 -> 0x38BDF8; // Cyan mana Rare
            case 1 -> 0x34D399; // Émeraude stellaire Peu commun
            default -> 0x7DD3FC; // Argent glacé Commun
        };
    }

    /**
     * Applique la police Pixel Musketeer au titre de l'objet (y compris sur les noms Lucky Block
     * contenant des codes inline §6§l / §r / multi-couleurs), retire le gras §l pour garder la
     * finesse calligraphique exacte de Pixel Musketeer, et rehausse chaque teinte vers un éclat
     * nacré brillant.
     */
    private static Component stylizeTitleWithMusketeer(Component src, int defaultBrightRgb) {
        if (src == null) return Component.empty();
        MutableComponent out = Component.empty();
        src.visit((style, content) -> {
            if (content == null || content.isEmpty()) return java.util.Optional.empty();
            appendParsedMusketeerSegments(out, content, style, defaultBrightRgb);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static void appendParsedMusketeerSegments(MutableComponent out, String text,
                                                      Style inheritedStyle, int defaultBrightRgb) {
        TextColor inheritedTc = inheritedStyle != null ? inheritedStyle.getColor() : null;
        Integer curRgb = inheritedTc != null ? (inheritedTc.getValue() & 0xFFFFFF) : null;
        boolean curItalic = inheritedStyle != null && inheritedStyle.isItalic();

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00a7' && i + 1 < text.length()) {
                if (buf.length() > 0) {
                    out.append(buildMusketeerSpan(buf.toString(), curRgb, curItalic, defaultBrightRgb));
                    buf.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(++i));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == ChatFormatting.RESET) {
                        curRgb = null;
                        curItalic = false;
                    } else if (fmt.isColor() && fmt.getColor() != null) {
                        curRgb = fmt.getColor() & 0xFFFFFF;
                        curItalic = false;
                    } else if (fmt == ChatFormatting.ITALIC) {
                        curItalic = true;
                    }
                    // On ignore volontairement BOLD (§l) pour ne jamais empâter Pixel Musketeer
                }
                continue;
            }
            buf.append(ch);
        }
        if (buf.length() > 0) {
            out.append(buildMusketeerSpan(buf.toString(), curRgb, curItalic, defaultBrightRgb));
        }
    }

    private static MutableComponent buildMusketeerSpan(String segment, Integer rawRgb,
                                                       boolean italic, int defaultBrightRgb) {
        int targetRgb;
        if (rawRgb == null || rawRgb == 0xFFFFFF || rawRgb == 0x55FFFF || rawRgb == 0xFFFF55) {
            targetRgb = defaultBrightRgb;
        } else {
            targetRgb = blendRgb(rawRgb, 0xFFFFFF, 0.28f);
        }
        Style s = Style.EMPTY
                .withFont(PIXEL_MUSKETEER_FONT)
                .withBold(false)
                .withItalic(italic)
                .withColor(TextColor.fromRgb(targetRgb));
        return Component.literal(segment).withStyle(s);
    }

    /**
     * Clone le titre avec la police Pixel Musketeer mais sans couleur forcée (ni codes §)
     * afin que les passes d'ombre colorée et de reflet crête utilisent exactement la couleur passée.
     */
    private static Component stripColorsForShadow(Component styled) {
        MutableComponent out = Component.empty();
        styled.visit((style, content) -> {
            if (content == null || content.isEmpty()) return java.util.Optional.empty();
            Style shadowStyle = Style.EMPTY
                    .withFont(PIXEL_MUSKETEER_FONT)
                    .withBold(false)
                    .withItalic(style != null && style.isItalic());
            out.append(Component.literal(stripSectionCodes(content)).withStyle(shadowStyle));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String stripSectionCodes(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static int blendRgb(int rgbA, int rgbB, float t) {
        float u = 1.0f - t;
        int r = Math.min(255, Math.round(((rgbA >>> 16) & 0xFF) * u + ((rgbB >>> 16) & 0xFF) * t));
        int g = Math.min(255, Math.round(((rgbA >>> 8) & 0xFF) * u + ((rgbB >>> 8) & 0xFF) * t));
        int b = Math.min(255, Math.round((rgbA & 0xFF) * u + (rgbB & 0xFF) * t));
        return (r << 16) | (g << 8) | b;
    }

    private static int darkenRgb(int rgb, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(((rgb >>> 16) & 0xFF) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(((rgb >>> 8) & 0xFF) * factor)));
        int b = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * factor)));
        return (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
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
