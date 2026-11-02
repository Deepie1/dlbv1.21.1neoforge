package deepluckyblock.enchantments;

import deepluckyblock.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;


import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * CustomEnchantment — PORT 1.20.1 -> 1.21.1 (réécriture complète).
 *
 * ⚠️ EN 1.21.1, {@code Enchantment} EST UN RECORD : impossible de l'étendre
 * ou de surcharger ses méthodes (getMaxLevel/getMinCost/canEnchant/
 * formatName/checkCompatibility... n'existent plus comme overrides).
 *
 * Architecture de repli :
 *  - {@link #create} construit le record Enchantment vanilla (description =
 *    Component translatable "enchantment.deep_lucky_block.<id>", définition
 *    avec weight/rareté, coûts constants — la table d'enchante vanilla est
 *    DÉSACTIVÉE par ENABLE_VANILLA_INTEGRATION=false, le Lucky Block pose les
 *    enchants directement via les DataComponents).
 *  - Les métadonnées du mod (catégorie, slots, max level, treasure/curse)
 *    vivent dans une carte statique path -> Meta, remplie par create().
 *  - Toutes les règles métier (canEnchant, conflits, nom coloré, curse...)
 *    sont des méthodes STATIQUES prenant l'Enchantment + son path :
 *      CustomEnchantment.canEnchantMod(en, stack)
 *      CustomEnchantment.checkCompatibilityMod(a, b)
 *      CustomEnchantment.formatNameMod(en, level)
 *      CustomEnchantment.isCurseMod(en) / isTreasureMod / getMaxLevelMod / pathOf
 *
 * NB : EnchantmentCategory (vanilla) a disparu en 1.20.5+ -> l'enum
 * {@link Cat} ci-dessous reprend les 13 catégories du mod.
 */
public final class CustomEnchantment {

    private CustomEnchantment() {}

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  ⚙  INTÉGRATION À LA TABLE D'ENCHANTEMENT / LOOT / VILLAGEOIS         ║
    // ║     Interrupteur principal — actuellement DÉSACTIVÉ                  ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    public static boolean ENABLE_VANILLA_INTEGRATION = false;
    public static boolean ALLOW_CURSES_AT_TABLE = false;
    public static boolean ALLOW_CURSES_IN_LOOT = true;
    public static int MAX_LEVEL_AT_TABLE = 5;

    /** Remplace l'ancienne EnchantmentCategory (supprimée en 1.20.5+). */
    public enum Cat {
        WEAPON, BOW, CROSSBOW, TRIDENT, DIGGER, BREAKABLE, FISHING_ROD,
        WEARABLE, ARMOR, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET
    }

    /** Coûts de la table par rareté — [min, max] (T307-RARITY : 7 paliers jusqu'à LEGENDARY). */
    public static final Map<Rarity, int[]> TABLE_COST_BY_RARITY =
        new java.util.EnumMap<>(java.util.Map.of(
            Rarity.COMMON,    new int[]{ 1,  30},
            Rarity.UNCOMMON,  new int[]{10,  42},
            Rarity.RARE,      new int[]{18,  58},
            Rarity.VERY_RARE, new int[]{28,  80},
            Rarity.EPIC,      new int[]{38, 105},
            Rarity.MYTHIC,    new int[]{50, 135},
            Rarity.LEGENDARY, new int[]{65, 175}
        ));

    /** Coût minimal en fonction du niveau : chaque niveau décale la fenêtre. */
    private static final int COST_STEP_PER_LEVEL = 9;

    /** Coût hors d'atteinte : aucun eCost vanilla ne monte aussi haut. */
    private static final int UNREACHABLE_COST = 9999;

    // ── Métadonnées du mod (path -> Meta) ────────────────────────────────────
    private record Meta(Cat category, EquipmentSlot[] slots, int maxLevel,
                        Rarity rarity, boolean treasure, boolean curse) {}

    private static final Map<ResourceLocation, Meta> META = new HashMap<>();

    // ══════════════════════════════════════════════════════════════════════
    // FABRICATION — remplace les anciens constructeurs
    // (id, Rarity, Cat, EquipmentSlot[] slots, int maxLevel [, treasure, curse])
    // ══════════════════════════════════════════════════════════════════════
    public static Enchantment create(String id, Rarity rarity, Cat category,
                                     EquipmentSlot[] slots, int maxLevel) {
        return create(id, rarity, category, slots, maxLevel, false, false);
    }

    public static Enchantment create(String id, Rarity rarity, Cat category,
                                     EquipmentSlot[] slots, int maxLevel,
                                     boolean isTreasure, boolean isCurse) {
        String path = id.toLowerCase(Locale.ROOT);
        int max = Math.max(1, maxLevel);
        META.put(ResourceLocation.parse("deep_lucky_block:" + path),
                new Meta(category, slots, max, rarity, isTreasure, isCurse));
        return new Enchantment(
                Component.translatable("enchantment." + NS() + "." + path),
                new Enchantment.EnchantmentDefinition(
                        HolderSet.empty(),            // supportedItems : la table vanilla est désactivée
                        Optional.empty(),              // primary items
                        rarity.weight(),               // weight de tirage (rareté)
                        max,                           // max_level
                        new Enchantment.Cost(1, 0),           // min_cost
                        new Enchantment.Cost(1, 0),           // max_cost
                        1,                             // anvil_cost
                        List.of(slotGroups(category, slots)) // equipment slots
                ),
                HolderSet.empty(),                     // exclusions : gérées par DeepEnchant.areCompatible()
                DataComponentMap.EMPTY                 // effets : dans les handlers/events du mod
        );
    }

    private static String NS() {
        return "deep_lucky_block";
    }

    // ── Accès aux métadonnées ───────────────────────────────────────────────
    public static Meta meta(Enchantment en) {
        return META.get(pathRL(en));
    }

    public static ResourceLocation pathRL(Enchantment en) {
        return deepluckyblock.util.DeepEnchant.getKeySafe(en);
    }

    public static String pathOf(Enchantment en) {
        ResourceLocation rl = pathRL(en);
        return rl == null ? "" : rl.getPath();
    }

    public static Cat getCatMod(Enchantment en) {
        Meta m = meta(en);
        return m == null ? null : m.category();
    }

    public static EquipmentSlot[] getSlotsMod(Enchantment en) {
        Meta m = meta(en);
        return m == null ? new EquipmentSlot[0] : m.slots();
    }

    /** Ne JAMAIS plafonner : GenerateLuckyItemProcedure clampe sur getMaxLevel(). */
    public static int getMaxLevelMod(Enchantment en) {
        Meta m = meta(en);
        return m == null ? en.getMaxLevel() : m.maxLevel();
    }

    public static Rarity getRarityMod(Enchantment en) {
        Meta m = meta(en);
        return m == null ? null : m.rarity();
    }

    /** T307-RARITY : Accès direct à la rareté depuis un ID ("deep_lucky_block:foo" ou "foo"). */
    public static Rarity getRarityByPath(String idOrPath) {
        if (idOrPath == null || idOrPath.isEmpty()) return null;
        String clean = idOrPath.toLowerCase(Locale.ROOT);
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            String ns = clean.substring(0, colon);
            if (!NS().equals(ns) && !"supreme_lucky_block".equals(ns)) return null;
            clean = clean.substring(colon + 1);
        }
        Meta m = META.get(ResourceLocation.parse(NS() + ":" + clean));
        return m == null ? null : m.rarity();
    }

    /** T307-RARITY / T317-LANG : Badge localisé (EN ou FR selon la langue active du jeu) et coloré pour Shift. */
    public static MutableComponent formatRarityBadge(Rarity rarity) {
        if (rarity == null) return Component.empty();
        String key = "tooltip.deep_lucky_block.rarity." + rarity.name().toLowerCase(Locale.ROOT);
        boolean fr = deepluckyblock.util.TranslationFallback.isFrench();
        String fallback = switch (rarity) {
            case COMMON -> fr ? "Commun" : "Common";
            case UNCOMMON -> fr ? "Peu commun" : "Uncommon";
            case RARE -> "Rare";
            case VERY_RARE -> fr ? "Tr\u00e8s rare" : "Very Rare";
            case EPIC -> fr ? "\u00c9pique" : "Epic";
            case MYTHIC -> fr ? "Mythique" : "Mythic";
            case LEGENDARY -> fr ? "L\u00e9gendaire" : "Legendary";
        };
        int rgb = switch (rarity) {
            case COMMON -> 0xB8C2CC;
            case UNCOMMON -> 0x55FF55;
            case RARE -> 0x55AAFF;
            case VERY_RARE -> 0x00DDDD;
            case EPIC -> 0xB366FF;
            case MYTHIC -> 0xFF55CC;
            case LEGENDARY -> 0xFFAA00;
        };
        return Component.literal("[")
                .append(deepluckyblock.util.TranslationFallback.translatableOrFallback(key, fallback))
                .append("]")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static boolean isTreasureMod(Enchantment en) {
        Meta m = meta(en);
        return m != null && m.treasure();
    }

    /** Curse de ce mod (les curses vanilla sont détequées par leur path). */
    public static boolean isCurseMod(Enchantment en) {
        Meta m = meta(en);
        if (m != null) return m.curse();
        return en != null && pathOf(en).contains("curse");
    }

    // ══════════════════════════════════════════════════════════════════════
    // RÈGLES DE PLACEMENT (remplace canEnchant)
    // ══════════════════════════════════════════════════════════════════════
    private static final Set<String> ELYTRA_ONLY = Set.of(
            "aerodynamic", "air_brake", "winging", "iron_wings", "rocket_blast",
            "wind_rider", "ground_skimmer", "curse_of_turbulence",
            "curse_of_brittle_wings", "curse_of_the_sky_conductor");

    public static boolean canEnchantMod(Enchantment en, ItemStack stack) {
        if (en == null || stack == null || stack.isEmpty()) return false;
        String path = pathOf(en);
        Cat category = getCatMod(en);
        if (ELYTRA_ONLY.contains(path) && !(stack.getItem() instanceof ElytraItem)) return false;
        if (category == null) return true;
        // Les enchants d'arc/arbalète ne vont que sur arcs/arbalètes
        if (category == Cat.BOW || category == Cat.CROSSBOW) {
            if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) return false;
        }
        // Vérification stricte du slot d'armure
        if (stack.getItem() instanceof ArmorItem armorItem) {
            EquipmentSlot itemSlot = armorItem.getEquipmentSlot();
            boolean slotAllowed = false;
            for (EquipmentSlot s : getSlotsMod(en)) {
                if (s == itemSlot) { slotAllowed = true; break; }
            }
            if (!slotAllowed) return false;
        }
        // Les enchants de pelle/houe ne vont que sur leur outil dédié
        String n = path.toLowerCase(Locale.ROOT);
        if (n.contains("shovel") && !(stack.getItem() instanceof ShovelItem)) {
            return false;
        }
        if (n.endsWith("_hoe") && !(stack.getItem() instanceof HoeItem)) {
            return false;
        }
        // Les enchants de houe/pelle/bouclier/botte ne vont pas sur les épées
        if (stack.getItem() instanceof SwordItem) {
            if (n.contains("hoe") || n.contains("shovel") || n.contains("shield")
                    || n.contains("boot") || n.contains("step") || n.contains("harvest")
                    || n.contains("sickle")) {
                return false;
            }
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFLITS (remplace checkCompatibility) — table d'origine 1.20.1
    // ══════════════════════════════════════════════════════════════════════
    private static final Set<String> PHASING_ALLOWED = Set.of(
            "phasing_bolts", "zero_gravity_shot", "ranger", "quick_charge", "inverted_arrows",
            "instant_shot", "point_blank_shot", "weak_force", "retrieval",
            "unbreaking", "mending");
    private static final Set<String> PIERCE_UNSAFE = Set.of(
            "brimstone", "delay", "meteor", "storm_strike", "thunderstruck",
            "water_arrow", "dry_arrow", "teleport_arrow", "swap_arrow",
            "grapple", "briar_arrow");
    private static final Set<String> TOY_UNSAFE = Set.of(
            "brimstone", "delay", "meteor", "storm_strike", "thunderstruck",
            "water_arrow", "dry_arrow", "poison_arrow", "dragons_breath_arrow",
            "heavy_impact", "wraith_arrow", "briar_arrow", "suppression_shot",
            "elemental_chain");

    public static boolean checkCompatibilityMod(Enchantment a, Enchantment b) {
        if (a == null || b == null) return true;
        String pa = pathOf(a), pb = pathOf(b);
        if (pa.equals(pb) && !isVanilla(a) && !isVanilla(b)) return false;
        // T306-FAMILIES : vanilla vs vanilla uniquement (&&) pour préserver les conflits vanilla <-> mod (infinity/retrieval, mending/basic_mending, etc.)
        if (isVanilla(a) && isVanilla(b)) return true;
        if ((pa.equals("phasing_bolts") && !PHASING_ALLOWED.contains(pb))
                || (pb.equals("phasing_bolts") && !PHASING_ALLOWED.contains(pa))) return false;
        if ((pa.equals("astral_pierce") && PIERCE_UNSAFE.contains(pb))
                || (pb.equals("astral_pierce") && PIERCE_UNSAFE.contains(pa))) return false;
        if ((pa.equals("toy") && TOY_UNSAFE.contains(pb))
                || (pb.equals("toy") && TOY_UNSAFE.contains(pa))) return false;
        if (pair(pa, pb, "water_arrow", "dry_arrow")
                || pair(pa, pb, "infinity", "retrieval")
                || pair(pa, pb, "astral_pierce", "piercing")
                || pair(pa, pb, "sniper", "weak_force")
                || pair(pa, pb, "storm_strike", "thunderstruck")
                || pair(pa, pb, "ricochet", "bounce")
                || pair(pa, pb, "pulling_power", "grappling_hook")
                || pair(pa, pb, "grappling_hook", "ender_hook")
                || pair(pa, pb, "infernal_line", "cooked_catch")
                || pair(pa, pb, "explosive_return", "sincere_loyalty")
                || pair(pa, pb, "aerodynamic", "wind_rider")
                || pair(pa, pb, "chain_lightning", "hydro_shock")
                || pair(pa, pb, "chain_lightning", "channeling")
                || pair(pa, pb, "unbreakable", "eternal")) return false;
        if (pa.equals("riptide") || pb.equals("riptide")) {
            String otherPath = pa.equals("riptide") ? pb : pa;
            if (Set.of("homing", "sincere_loyalty", "scorching_prongs",
                    "wind_lance", "harpoon", "lingering_skewer").contains(otherPath)) return false;
        }
        if (Set.of("teleport_arrow", "swap_arrow", "grapple").contains(pa)
                && Set.of("teleport_arrow", "swap_arrow", "grapple").contains(pb)) return false;
        if (Set.of("brimstone", "delay", "meteor").contains(pa)
                && Set.of("brimstone", "delay", "meteor").contains(pb)) return false;
        if (Set.of("fan_volley", "scatter", "multishot").contains(pa)
                && Set.of("fan_volley", "scatter", "multishot").contains(pb)) return false;
        if (Set.of("momentum", "trailblazer", "windrunner").contains(pa)
                && Set.of("momentum", "trailblazer", "windrunner").contains(pb)) return false;
        // T306-FAMILIES : progression Enfant -> Upgrade par famille globale (un seul palier actif par famille).
        for (Set<String> family : UPGRADE_FAMILIES) {
            if (family.contains(pa) && family.contains(pb) && !pa.equals(pb)) return false;
        }
        if (ELEMENTAL_HEART_IDS.contains(pa) && ELEMENTAL_HEART_IDS.contains(pb) && !pa.equals(pb)) return false;
        return true;
    }

    /**
     * T306-FAMILIES : Familles globales évolutives (Enfant moins fort -> Upgrade).
     * Deux paliers de la même famille sont mutuellement exclusifs (enclume ET Lucky Block).
     */
    public static final List<Set<String>> UPGRADE_FAMILIES = List.of(
            Set.of("sharp_edge", "reinforced_sharpness", "advanced_sharpness", "ultra_sharpness", "extreme_sharpness", "supreme_sharpness"),
            Set.of("basic_swiftness", "swiftness"),
            Set.of("basic_lightness", "lightness"),
            Set.of("basic_leap_power", "leap_power"),
            Set.of("basic_chrono_mending", "chrono_mending"),
            Set.of("basic_mending", "mending"),
            Set.of("minor_crit_rate", "crit_rate"),
            Set.of("minor_crit_amplifier", "crit_amplifier", "overclocked_crit"),
            Set.of("minor_dive_strike", "dive_strike", "meteoric_slam"),
            Set.of("sweeping_enhancement", "reinforced_sweeping", "annihilating_sweep"),
            Set.of("sweeping_fire", "fiery_sweep"),
            Set.of("bloodthirst", "bloodthirsty", "blood_drinkers_edge"),
            Set.of("promising_blade", "giant_slayer", "titan_slayer"),
            Set.of("tree_felling", "lumberjack"),
            Set.of("vein_miner", "extracting"),
            Set.of("utility_trench", "universal_trench"),
            Set.of("stinky_feet", "super_stinky_feet"),
            Set.of("rock_breaking_gauntlet", "diamond_glow_gauntlet"),
            Set.of("army_summon", "summon_army"),
            Set.of("echolocation", "omniscient_perception"),
            Set.of("big_step", "giant_step")
    );

    /**
     * Les 7 cœurs élémentaires à condition de régénération (voir
     * {@code deepluckyblock.util.ElementalHeartConditions}). Mutuellement
     * exclusifs entre eux : un seul actif par plastron (voir
     * {@link #checkCompatibilityMod} et
     * {@code GenerateLuckyItemProcedure#conflictsWithApplied}, qui réutilise
     * cette même liste pour bloquer aussi les combinaisons générées
     * procéduralement par les Lucky Blocks).
     */
    public static final Set<String> ELEMENTAL_HEART_IDS = Set.of(
            "fire_heart", "water_heart", "earth_heart", "wind_heart",
            "electric_heart", "shadow_heart", "light_heart");

    private static boolean pair(String a, String b, String x, String y) {
        return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
    }

    private static boolean isVanilla(Enchantment en) {
        ResourceLocation rl = pathRL(en);
        return rl != null && !NS().equals(rl.getNamespace());
    }

    // ══════════════════════════════════════════════════════════════════════
    // NOM AFFICHÉ (couleurs + romains) — remplace formatName
    // ══════════════════════════════════════════════════════════════════════
    public static MutableComponent formatNameMod(Enchantment en, int level) {
        String path = pathOf(en);
        // FIX (rapporté en jeu : "le titre de certains enchantements
        // s'affiche en erreur / clé brute non traduite", ex. titans_grasp,
        // light_heart) : Component.translatable() seul retombe sur la clé
        // brute ("enchantment.deep_lucky_block.xxx") si Language.getInstance()
        // ne connaît pas (encore/plus) la clé à l'instant du rendu -- déjà
        // observé et corrigé côté DESCRIPTION (voir EnchantmentTooltipFixProcedure
        // FIX v42), mais le NOM n'avait jusqu'ici aucun filet de secours
        // équivalent. On utilise désormais TranslationFallback, qui lit en
        // dernier recours le JSON packagé du mod (jamais affecté par l'état
        // de Language) si la traduction normale échoue.
        String key = "enchantment." + NS() + "." + path;
        String readableFallback = path.replace('_', ' ');
        MutableComponent name = deepluckyblock.util.TranslationFallback.translatableOrFallback(key, readableFallback);
        name = name.withStyle(colorFor(path));
        if (level != 1 || getMaxLevelMod(en) != 1) {
            name = name.append(" ").append(Component.literal(toRoman(level)));
        }
        return name;
    }

    private static Style colorFor(String idKey) {
        if (idKey.contains("curse")) return Style.EMPTY.withColor(ChatFormatting.RED);
        String n = idKey.toLowerCase(Locale.ROOT);
        if (n.contains("inner_fear")) return Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE);
        if (n.contains("dragon") || n.contains("ender") || n.contains("void")
                || n.contains("warp") || n.contains("shadow") || n.contains("teleport")
                || n.contains("wraith") || n.contains("night")) return Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE);
        if (n.contains("breath") || n.contains("oxygen") || n.contains("ascending_gale")
                || n.contains("wind") || n.contains("cloud") || n.contains("air")
                || n.contains("lung") || n.contains("hover")) return Style.EMPTY.withColor(TextColor.fromRgb(0xDDEEFF));
        if (n.contains("conduit") || n.contains("water") || n.contains("ocean")
                || n.contains("aqua") || n.contains("tide") || n.contains("amphibious")
                || n.contains("buoy") || n.contains("depth")) return Style.EMPTY.withColor(ChatFormatting.BLUE);
        if (n.contains("leap") || n.contains("jump") || n.contains("slope_strider") || n.equals("big_step")) return Style.EMPTY.withColor(ChatFormatting.GREEN);
        if (n.equals("giant_step")) return Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00));
        if (n.contains("lumber") || n.contains("tree") || n.contains("vein")
                || n.contains("harvest") || n.contains("weeding") || n.contains("farmer")
                || n.contains("forest") || n.contains("moss") || n.contains("plant")
                || n.contains("sprout") || n.contains("nature") || n.contains("growth")) return Style.EMPTY.withColor(ChatFormatting.GREEN);
        if (n.contains("poison") || n.contains("venom") || n.contains("toxic")) return Style.EMPTY.withColor(ChatFormatting.DARK_GREEN);
        if (n.contains("fire") || n.contains("blaze") || n.contains("flame")
                || n.contains("burn") || n.contains("infernal") || n.contains("magma")
                || n.contains("lava") || n.contains("scorch") || n.contains("molten")
                || n.contains("fiery") || n.contains("blazing") || n.contains("meteor")) return Style.EMPTY.withColor(ChatFormatting.GOLD);
        if (n.contains("haste") || n.contains("hypoxia")) return Style.EMPTY.withColor(ChatFormatting.GOLD);
        // T307-RARITY : Teinte de prestige pour les paliers Épique / Mythique / Légendaire sans élément dédié
        Rarity r = getRarityByPath(n);
        if (r == Rarity.LEGENDARY) return Style.EMPTY.withColor(TextColor.fromRgb(0xFFBB33));
        if (r == Rarity.MYTHIC) return Style.EMPTY.withColor(TextColor.fromRgb(0xFF66CC));
        if (r == Rarity.EPIC) return Style.EMPTY.withColor(TextColor.fromRgb(0xB877FF));
        return Style.EMPTY.withColor(ChatFormatting.WHITE);
    }

    // Plafond de securite : au-dela, la chaine romaine deviendrait absurdement
    // longue (des dizaines de "M") et sans interet pratique. 10000 couvre tres
    // largement le niveau 1000 force par commande (affiche "M") tout en restant
    // lisible. Ancien plafond de 200 empechait TOUT affichage romain correct
    // au-dela de 200 -> repli sur le chiffre brut, symptome du bug signale.
    private static final int MAX_ROMAN_MOD = 10000;

    private static String toRoman(int number) {
        if (number <= 0) return String.valueOf(number);
        if (number > MAX_ROMAN_MOD) return String.valueOf(number);
        // Table complete (milliers inclus) : notation romaine standard, valable
        // pour n'importe quel niveau force (jusqu'a MAX_ROMAN_MOD), pas
        // seulement les niveaux de jeu normaux.
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                sb.append(symbols[i]);
                number -= values[i];
            }
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // AIDE INTERNE
    // ══════════════════════════════════════════════════════════════════════
    private static EquipmentSlotGroup slotGroups(Cat category, EquipmentSlot[] slots) {
        if (slots != null && slots.length == 1 && slots[0] != null) {
            return switch (slots[0]) {
                case MAINHAND -> EquipmentSlotGroup.MAINHAND;
                case OFFHAND -> EquipmentSlotGroup.OFFHAND;
                case HEAD -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS -> EquipmentSlotGroup.LEGS;
                case FEET -> EquipmentSlotGroup.FEET;
                default -> EquipmentSlotGroup.ANY;
            };
        }
        return EquipmentSlotGroup.ANY;
    }
}
