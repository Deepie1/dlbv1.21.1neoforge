package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;

public class RangeFootBlockConfig {
    public static ModConfigSpec.ConfigValue<Integer> range;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("范围方块附魔配置 / Range Foot Placement Config").push("range_foot_block");
        range = builder.comment("工作范围（半径），例如：1表示3x3区域，2表示5x5区域").define("range", 2);
        builder.pop();
    }
}