package deepluckyblock.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Compatibilite optionnelle du Lucky Radar, sans dependance directe a Lucky Tools. */
public final class DeepRadarDropBonus {

    public static final int BONUS_PERCENT = 10;
    public static final String ACTIVE_MESSAGE =
            "+10% Deep Lucky Block drop chance ACTIVE";

    private static final ResourceLocation RADAR_ID =
            ResourceLocation.tryParse("luckytools:lucky_radar");
    private static final String DEEP_BLOCK_ID =
            "deep_lucky_block:deep_lucky_block";
    private static final String TRACKED_TAG = "Tracked";

    private static Item cachedRadar;

    private DeepRadarDropBonus() {}

    private static Item radarItem() {
        if (cachedRadar == null && RADAR_ID != null) {
            cachedRadar = BuiltInRegistries.ITEM.get(RADAR_ID);
        }
        return cachedRadar;
    }

    private static boolean isConfiguredRadar(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item radar = radarItem();
        if (radar == null || !stack.is(radar) || !deepluckyblock.util.DeepNbt.hasTag(stack)) return false;
        return DEEP_BLOCK_ID.equals(deepluckyblock.util.DeepNbt.tag(stack).getString(TRACKED_TAG));
    }

    /** Actif dans la main principale OU secondaire, exactement comme le Radar officiel. */
    public static boolean isActive(Player player) {
        return player != null && (isConfiguredRadar(player.getMainHandItem())
                || isConfiguredRadar(player.getOffhandItem()));
    }
}
