package deepluckyblock.client;

import deepluckyblock.compat.DeepRadarDropBonus;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Affichage client pur : apparait instantanement quand le Radar configure est
 * tenu et disparait des qu'il quitte les deux mains. Aucun paquet action-bar,
 * donc aucun conflit avec les messages des events du Lucky Block.
 *
 * PORT 1.21.1 : RenderGuiOverlayEvent -> RenderGuiLayerEvent ; le nom de la
 * couche est une ResourceLocation ("hotbar") via event.getName().
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class DeepRadarDropBonusOverlay {

    private DeepRadarDropBonusOverlay() {}

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!"hotbar".equals(event.getName().getPath())) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
                || !DeepRadarDropBonus.isActive(minecraft.player)) return;

        Component text = Component.literal(DeepRadarDropBonus.ACTIVE_MESSAGE)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        int centerX = event.getGuiGraphics().guiWidth() / 2;
        int y = event.getGuiGraphics().guiHeight() - 72;
        event.getGuiGraphics().drawCenteredString(
                minecraft.font, text, centerX, y, 0xFFFFFF);
    }
}
