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
import net.minecraft.world.item.ItemStack;
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

    /** Coûts de la table par rareté — [min, max] (utilisé si intégration ON). */
    public static final Map<Rarity, int[]> TABLE_COST_BY_RARITY =
        new java.util.EnumMap<>(java.util.Map.of(
            Rarity.COMMON,    new int[]{ 1,  35},   // USED / COMMON
            Rarity.UNCOMMON,  new int[]{12,  45},   // UNCOMMON
            Rarity.RARE,      new int[]{20,  65},   // RARE / EPIC
            Rarity.VERY_RARE, new int[]{36,  110}   // LEGENDARY et au-dessus
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
            "wind_rider", "ground_skimmer", "turbulence_curse",
            "brittle_wings_curse", "sky_conductor_curse");

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
        // Les enchants de houe/pelle/bouclier/botte ne vont pas sur les épées
        if (stack.getItem() instanceof SwordItem) {
            String n = path.toLowerCase(Locale.ROOT);
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
            "phasing", "steady_shot", "ranger", "inverted_arrows",
            "instant_shot", "point_shoot", "weak_force", "retrieval",
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
        // vanilla vs vanilla : les conflits vanilla sont gérés par le jeu
        if (isVanilla(a) || isVanilla(b)) return true;
        if ((pa.equals("phasing") && !PHASING_ALLOWED.contains(pb))
                || (pb.equals("phasing") && !PHASING_ALLOWED.contains(pa))) return false;
        if ((pa.equals("pierce") && PIERCE_UNSAFE.contains(pb))
                || (pb.equals("pierce") && PIERCE_UNSAFE.contains(pa))) return false;
        if ((pa.equals("toy") && TOY_UNSAFE.contains(pb))
                || (pb.equals("toy") && TOY_UNSAFE.contains(pa))) return false;
        if (pair(pa, pb, "water_arrow", "dry_arrow")
                || pair(pa, pb, "infinity", "retrieval")
                || pair(pa, pb, "sniper", "weak_force")
                || pair(pa, pb, "storm_strike", "thunderstruck")
                || pair(pa, pb, "ricochet", "bounce")
                || pair(pa, pb, "pulling_power", "fishing_grapple")
                || pair(pa, pb, "fishing_grapple", "ender_hook")
                || pair(pa, pb, "infernal_line", "cooked_catch")
                || pair(pa, pb, "explosive_return", "sincere_loyalty")
                || pair(pa, pb, "aerodynamic", "wind_rider")
                || pair(pa, pb, "chain_lightning", "hydro_shock")
                || pair(pa, pb, "chain_lightning", "channeling")) return false;
        if (pa.equals("riptide") || pb.equals("riptide")) {
            String otherPath = pa.equals("riptide") ? pb : pa;
            if (Set.of("homing_trident", "sincere_loyalty", "scorching_prongs",
                    "wind_lance", "harpoon", "lingering_skewer").contains(otherPath)) return false;
        }
        if (Set.of("teleport_arrow", "swap_arrow", "grapple").contains(pa)
                && Set.of("teleport_arrow", "swap_arrow", "grapple").contains(pb)) return false;
        if (Set.of("brimstone", "delay", "meteor").contains(pa)
                && Set.of("brimstone", "delay", "meteor").contains(pb)) return false;
        if (Set.of("multishot_bow", "scatter", "multishot").contains(pa)
                && Set.of("multishot_bow", "scatter", "multishot").contains(pb)) return false;
        if (Set.of("momentum", "trailblazer", "windrunner").contains(pa)
                && Set.of("momentum", "trailblazer", "windrunner").contains(pb)) return false;
        // Correctif (audit demande utilisateur, équilibrage des 7 cœurs
        // élémentaires) : sans cette règle, RIEN n'empêchait de graver
        // plusieurs cœurs élémentaires différents (ex: fire_heart +
        // water_heart + earth_heart...) sur le MÊME plastron via enclume/
        // commandes -- cumulant alors leurs bonus de PV ET tous leurs
        // pouvoirs actifs simultanément (dégâts bonus, réduction de dégâts,
        // vol de vie, procs, faim/saturation, minage, répulsion de mobs...),
        // ce qui casserait complètement l'équilibrage prévu "un cœur =
        // un choix". Les 7 cœurs sont donc désormais mutuellement exclusifs
        // entre eux (un seul actif à la fois par plastron), exactement comme
        // les familles momentum/trailblazer/windrunner ci-dessus.
        if (ELEMENTAL_HEART_IDS.contains(pa) && ELEMENTAL_HEART_IDS.contains(pb) && !pa.equals(pb)) return false;
        return true;
    }

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
        if (n.contains("breath") || n.contains("oxygen") || n.contains("gale")
                || n.contains("wind") || n.contains("cloud") || n.contains("air")
                || n.contains("lung") || n.contains("hover")) return Style.EMPTY.withColor(TextColor.fromRgb(0xDDEEFF));
        if (n.contains("conduit") || n.contains("water") || n.contains("ocean")
                || n.contains("aqua") || n.contains("tide") || n.contains("amphibious")
                || n.contains("buoy") || n.contains("depth")) return Style.EMPTY.withColor(ChatFormatting.BLUE);
        if (n.contains("leap") || n.contains("jump") || n.contains("high_step")) return Style.EMPTY.withColor(ChatFormatting.GREEN);
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
