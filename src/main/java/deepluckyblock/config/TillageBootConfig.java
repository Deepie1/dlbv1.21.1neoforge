package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class TillageBootConfig {
    public static ModConfigSpec.BooleanValue enabled;
    public static ModConfigSpec.ConfigValue<List<? extends String>> cropMappings;
    public static ModConfigSpec.ConfigValue<Integer> range;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("耕耘锄附魔配置 / Farmers Hoe Config").push("tillage_boot");
        enabled = builder.comment("是否启用耕耘锄附魔功能").define("enabled", true);
        range = builder.comment("工作范围（半径），例如：1表示3x3区域，2表示5x5区域").define("range", 2);
        cropMappings = builder.comment("农作物种子ID列表").defineList("crop_mappings", getDefaultCropMappings(), obj -> obj instanceof String);
        builder.pop();
    }
    private static List<String> getDefaultCropMappings() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:wheat_seeds");
        list.add("minecraft:carrot");
        list.add("minecraft:potato");
        list.add("minecraft:beetroot_seeds");
        list.add("minecraft:melon_seeds");
        list.add("minecraft:pumpkin_seeds");
        return list;
    }
}