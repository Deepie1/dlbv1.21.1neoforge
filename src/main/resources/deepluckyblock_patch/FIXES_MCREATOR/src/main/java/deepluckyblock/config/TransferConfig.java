package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class TransferConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> blockedContainers;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("传输附魔配置 / Transfer Config").push("transfer");
        blockedContainers = builder.comment("传输附魔无法传输物品到的容器方块列表").defineList("blockedContainers", getDefaultBlockedContainers(), obj -> obj instanceof String);
        builder.pop();
    }
    private static List<String> getDefaultBlockedContainers() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:jukebox");
        list.add("minecraft:chiseled_bookshelf");
        return list;
    }
}