package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class WeedRemovalConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> weedBlockList;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("杂草清除附魔配置 / Weed Removal Config").push("weed_removal");
        weedBlockList = builder.comment("需要清除的方块列表").defineList("weedBlockList", getDefaultWeedBlockList(), obj -> obj instanceof String);
        builder.pop();
    }
    private static List<String> getDefaultWeedBlockList() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:grass"); list.add("minecraft:tall_grass");
        list.add("minecraft:fern"); list.add("minecraft:large_fern");
        list.add("minecraft:dead_bush"); list.add("minecraft:dandelion");
        list.add("minecraft:poppy"); list.add("minecraft:blue_orchid");
        list.add("minecraft:allium"); list.add("minecraft:azure_bluet");
        list.add("minecraft:red_tulip"); list.add("minecraft:orange_tulip");
        list.add("minecraft:white_tulip"); list.add("minecraft:pink_tulip");
        list.add("minecraft:oxeye_daisy"); list.add("minecraft:cornflower");
        list.add("minecraft:lily_of_the_valley"); list.add("minecraft:wither_rose");
        list.add("minecraft:sunflower"); list.add("minecraft:lilac");
        list.add("minecraft:rose_bush"); list.add("minecraft:peony");
        list.add("minecraft:seagrass"); list.add("minecraft:tall_seagrass");
        return list;
    }
}
