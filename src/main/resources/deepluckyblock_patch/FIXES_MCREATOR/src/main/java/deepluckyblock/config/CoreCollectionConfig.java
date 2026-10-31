package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class CoreCollectionConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> allowedBlocks;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("核心采集附魔配置 / Core Harvesting Config").push("core_collection");
        allowedBlocks = builder.comment("可以采集的方块列表").defineList("allowedBlocks", getDefaultAllowedBlocks(), obj -> obj instanceof String);
        builder.pop();
    }
    private static List<String> getDefaultAllowedBlocks() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:end_portal_frame");
        list.add("minecraft:budding_amethyst");
        list.add("minecraft:command_block");
        list.add("minecraft:spawner");
        list.add("minecraft:barrier");
        list.add("minecraft:bedrock");
        return list;
    }
}