package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import java.util.Arrays;

public class CropHarvestConfig {
    public static class Common {
        public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
        public static final ModConfigSpec.ConfigValue<List<? extends String>> CROP_HARVEST_CROPS = BUILDER
                .comment("List of crops that the Crop Harvest enchantment will work on.")
                .defineList("cropHarvestCrops", Arrays.asList(
                        "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots", "minecraft:nether_wart"),
                        obj -> obj instanceof String);
        public static final ModConfigSpec SPEC = BUILDER.build();
        public static List<String> cropHarvestCrops;
    }
    public static void registerConfigs() {
        Common.cropHarvestCrops = (List<String>) Common.CROP_HARVEST_CROPS.get();
    }
}