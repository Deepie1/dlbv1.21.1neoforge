package deepluckyblock.config;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class OreDetectorConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> lowTierOres;
    public static ModConfigSpec.ConfigValue<List<? extends String>> mediumTierOres;
    public static ModConfigSpec.ConfigValue<List<? extends String>> highTierOres;
    public static ModConfigSpec.ConfigValue<Integer> detectionRadius;
    public static ModConfigSpec.ConfigValue<Integer> sneakingDetectionRadius;
    public static ModConfigSpec SPEC;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }
    public static void init(ModConfigSpec.Builder builder) {
        builder.comment("矿探附魔配置 / Ore Detector Config").push("ore_detector");
        lowTierOres = builder.comment("低级矿物列表").defineList("lowTierOres", getDefaultLowTierOres(), obj -> obj instanceof String);
        mediumTierOres = builder.comment("中级矿物列表").defineList("mediumTierOres", getDefaultMediumTierOres(), obj -> obj instanceof String);
        highTierOres = builder.comment("高级矿物列表").defineList("highTierOres", getDefaultHighTierOres(), obj -> obj instanceof String);
        detectionRadius = builder.comment("探测半径").define("detectionRadius", 12);
        sneakingDetectionRadius = builder.comment("潜行时探测半径").define("sneakingDetectionRadius", 5);
        builder.pop();
    }
    private static List<String> getDefaultLowTierOres() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:coal_ore"); list.add("minecraft:deepslate_coal_ore");
        list.add("minecraft:copper_ore"); list.add("minecraft:deepslate_copper_ore");
        return list;
    }
    private static List<String> getDefaultMediumTierOres() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:iron_ore"); list.add("minecraft:deepslate_iron_ore");
        list.add("minecraft:gold_ore"); list.add("minecraft:deepslate_gold_ore");
        list.add("minecraft:lapis_ore"); list.add("minecraft:deepslate_lapis_ore");
        list.add("minecraft:redstone_ore"); list.add("minecraft:deepslate_redstone_ore");
        list.add("minecraft:nether_gold_ore"); list.add("minecraft:nether_quartz_ore");
        return list;
    }
    private static List<String> getDefaultHighTierOres() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:diamond_ore"); list.add("minecraft:deepslate_diamond_ore");
        list.add("minecraft:ancient_debris"); list.add("minecraft:emerald_ore");
        list.add("minecraft:deepslate_emerald_ore");
        return list;
    }
}