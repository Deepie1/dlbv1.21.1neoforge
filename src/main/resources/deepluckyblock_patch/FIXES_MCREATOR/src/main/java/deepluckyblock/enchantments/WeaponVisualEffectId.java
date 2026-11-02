package deepluckyblock.enchantments;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * T342 + T343-VOXEL-3D-FIX : Catalogue complet des 30 effets visuels uniques d'armes en style
 * 100 % Minecraft (mini-pixels / cubes voxels 3D) couvrant l'intégralité des enchantements
 * d'épées, haches, tridents, arcs, arbalètes, faux (houes) et gantelets d'arts martiaux.
 */
public enum WeaponVisualEffectId {

    // === I. TRANCHANT, ALLONGE, ÉCHO & EXÉCUTION (1..5) ===
    RAZOR_GLEAM         (1,  Category.EDGE_SHARP, 35, true,  0xEEF8FF, 0x7CC6FF),
    SUPREME_PRISM_EDGE  (2,  Category.EDGE_SHARP, 85, true,  0xFFFFFF, 0x4CF2FF),
    SPECTRAL_REACH      (3,  Category.SPECIAL,    62, true,  0x7CF7E8, 0x1FB6CC),
    TWIN_ECHO_SLASH     (4,  Category.SPECIAL,    68, true,  0x5EE2FF, 0x2B6BFF),
    GUILLOTINE_CLEAVE   (5,  Category.SPECIAL,    80, false, 0xD91E36, 0xF0F4F8),

    // === II. COUPS CRITIQUES AMÉLIORÉS (6..8) ===
    GOLDEN_CRIT_STAR    (6,  Category.CRIT,       52, true,  0xFFE66D, 0xFFFFFF),
    FOCUSED_BULLSEYE    (7,  Category.CRIT,       64, true,  0xFFB703, 0x38F28D),
    OVERCLOCK_CYBER_X   (8,  Category.CRIT,       88, true,  0x00F5FF, 0xFF2A85),

    // === III. SANG, VAMPIRISME & CARNAGE (9..12) ===
    DRIPPING_BLOOD_EDGE (9,  Category.BLOOD,      66, false, 0xB8081E, 0xFF263B),
    CRIMSON_SIPHON      (10, Category.BLOOD,      84, false, 0x9E061A, 0xFF3858),
    VITAL_TRANSFUSION   (11, Category.BLOOD,      60, true,  0xFF4D79, 0xFFD166),
    CARNAGE_BERSERK     (12, Category.BLOOD,      86, false, 0xD40A20, 0xFF5A1F),

    // === IV. ÉLÉMENTS, EFFETS À DISTANCE & GRANDIOSES (13..30) ===
    MOLTEN_FIRE_WAVE    (13, Category.ELEMENT,    58, true,  0xFF6B1A, 0xFFD147),
    SOUL_FIRE_CRESCENT  (14, Category.ELEMENT,    76, true,  0x2EF2E6, 0x80FFFA),
    BRIMSTONE_METEOR    (15, Category.ELEMENT,    92, true,  0xFF3B00, 0xFFAE00),
    CHAIN_VOLT_ARC      (16, Category.ELEMENT,    87, true,  0x70E8FF, 0xE6FBFF),
    HYDRO_TIDAL_SURGE   (17, Category.ELEMENT,    60, true,  0x1CC8FF, 0xA8F0FF),
    ABYSSAL_MAELSTROM   (18, Category.ELEMENT,    89, true,  0x0A58CA, 0x3CEFFF),
    PERMAFROST_RIME     (19, Category.ELEMENT,    56, true,  0x8CE8FF, 0xE8FCFF),
    VENOM_BRIAR_FANG    (20, Category.ELEMENT,    63, false, 0x2BC418, 0x9DFF2E),
    SEISMIC_TECTONIC    (21, Category.ELEMENT,    82, false, 0xC97A2B, 0xFFD275),
    ZEPHYR_WIND_LANCE   (22, Category.ELEMENT,    67, true,  0xC7FFF0, 0x5EF2CE),
    SERAPH_SKY_PILLAR   (23, Category.ELEMENT,    96, true,  0xFFF3B0, 0xFFD043),
    WITHER_SHADOW_RIFT  (24, Category.ELEMENT,    78, false, 0x4A1275, 0xB44CFF),
    ASTRAL_STAR_LANCE   (25, Category.RANGED,     93, true,  0x8A7CFF, 0x9EF8FF),
    VOID_WARP_SPHERE    (26, Category.RANGED,     85, true,  0xB538FF, 0x3DF5FF),
    DRAGON_CHAOS_PRISM  (27, Category.RANGED,     90, true,  0xFF2EC4, 0x7A28FF),
    CHRONO_ETERNAL_HALO (28, Category.SPECIAL,    79, true,  0xFFC845, 0xFFF4CC),
    WARLORD_RALLY_SIGIL (29, Category.SPECIAL,    91, true,  0xFFD644, 0xFFF0B8),
    ANNIHILATING_ARC    (30, Category.SPECIAL,    95, true,  0xFFFFFF, 0x9ED8FF);

    public enum Category {
        EDGE_SHARP,
        CRIT,
        BLOOD,
        ELEMENT,
        RANGED,
        SPECIAL
    }

    public record ResolvedLayer(WeaponVisualEffectId effect, int level) {}

    public final int id;
    public final Category category;
    public final int priority;
    public final boolean additive;
    public final int primaryRgb;
    public final int secondaryRgb;

    WeaponVisualEffectId(int id, Category category, int priority, boolean additive, int primaryRgb, int secondaryRgb) {
        this.id = id;
        this.category = category;
        this.priority = priority;
        this.additive = additive;
        this.primaryRgb = primaryRgb;
        this.secondaryRgb = secondaryRgb;
    }

    private static final WeaponVisualEffectId[] BY_ID = new WeaponVisualEffectId[31];
    private static final Map<String, WeaponVisualEffectId> ENCHANT_MAP = new HashMap<>(256);
    private static final Map<String, WeaponVisualEffectId> GAUNTLET_MAP = new HashMap<>(24);

    static {
        for (WeaponVisualEffectId v : values()) {
            BY_ID[v.id] = v;
        }

        // #01 Reflet d'Acier Affûté
        register(RAZOR_GLEAM, "sharp_edge", "reinforced_sharpness", "new_edge", "sickle_damage",
                "haste_surge", "haste_continuation", "minecraft:sharpness", "minecraft:impaling",
                "minecraft:power", "minecraft:piercing", "minecraft:quick_charge", "minecraft:efficiency");
        // #02 Prisme Diamant Suprême
        register(SUPREME_PRISM_EDGE, "advanced_sharpness", "sharpened_sharpness", "ultra_sharpness",
                "extreme_sharpness", "supreme_sharpness", "peerless");
        // #03 Allonge Fantôme
        register(SPECTRAL_REACH, "extension", "titans_grasp", "heavy_range", "short_reach");
        // #04 Double Rémanence d'Écho
        register(TWIN_ECHO_SLASH, "echo_strike", "combo_slash", "swift_strike", "duelists_mastery",
                "instant_shot", "ricochet", "bounce", "minecraft:multishot");
        // #05 Fil d'Exécution & Guillotine
        register(GUILLOTINE_CLEAVE, "guillotine", "decapitation", "beheading", "desperate_kill", "reapers_scythe");

        // #06 Étoile Critique Dorée
        register(GOLDEN_CRIT_STAR, "minor_crit_rate", "crit_rate", "minor_crit_amplifier", "crit_amplifier");
        // #07 Réticule Critique Focalisé
        register(FOCUSED_BULLSEYE, "focused_crit", "focused_ambush", "sense", "marked_prey",
                "point_blank_shot", "ranger");
        // #08 Surcharge Cyber-Critique
        register(OVERCLOCK_CYBER_X, "overclocked_crit", "overcharge");

        // #09 Lame Sanglante Goutte-à-Goutte
        register(DRIPPING_BLOOD_EDGE, "blood_edge", "bloodthirst", "bloodthirsty", "predators_scent", "lingering_skewer");
        // #10 Siphon Vampirique Artériel
        register(CRIMSON_SIPHON, "blood_drinkers_edge", "leech", "devour", "blood_pact", "transfer", "soul_sacrifice_slash");
        // #11 Transfusion Vitale
        register(VITAL_TRANSFUSION, "blood_transfusion", "curse_of_mercy", "toy");
        // #12 Bain de Sang & Furie Berserk
        register(CARNAGE_BERSERK, "mass_slaughter", "berserk", "brutal", "battle_scarred", "battle_hardened", "retaliation");

        // #13 Couronne de Braise & Balayage Ardent
        register(MOLTEN_FIRE_WAVE, "fiery_sweep", "sweeping_fire", "sweeping_burn_slash", "blazing_trauma",
                "scorching_prongs", "blazing_arrow", "torch", "flint_heart", "nether_corrosion",
                "minecraft:fire_aspect", "minecraft:flame");
        // #14 Flamme d'Âme Turquoise
        register(SOUL_FIRE_CRESCENT, "blazing_soul_sweep", "blaze_sanctuary", "soul_reaping_sickle", "soul_bound");
        // #15 Déflagration Soufrée & Météore
        register(BRIMSTONE_METEOR, "brimstone", "meteor", "meteoric_slam");
        // #16 Arcs Voltaïques & Chaîne d'Éclairs
        register(CHAIN_VOLT_ARC, "thundercall_pact", "chain_lightning", "thunderstruck", "storm_strike",
                "elemental_chain", "curse_of_the_lightning_rod", "minecraft:channeling");
        // #17 Courant Marin & Choc Hydro
        register(HYDRO_TIDAL_SURGE, "hydro_shock", "ocean_current", "sea_pogo", "water_arrow", "dry_arrow", "minecraft:riptide");
        // #18 Maelström des Abysses
        register(ABYSSAL_MAELSTROM, "abyssal_undertow", "tidecaller", "harpoon", "grapple", "curse_of_the_depths");
        // #19 Givre Cristallin
        register(PERMAFROST_RIME, "heavy_slow_hit", "sluggish_weakness", "suppression_shot", "weakness_trauma",
                "weakness_wound", "exhaustion", "sighing_strike", "blunt", "weak_force", "minecraft:silk_touch");
        // #20 Croc Venimeux & Ronces
        register(VENOM_BRIAR_FANG, "poison_fang", "poison_scar", "poison_arrow", "briar_arrow",
                "minecraft:bane_of_arthropods");
        // #21 Onde Tellurique & Brise-Géant
        register(SEISMIC_TECTONIC, "seismic_strike", "giant_slayer", "titan_slayer", "guard_breaker", "mighty_strike",
                "heavy_impact", "dive_strike", "minor_dive_strike", "disarm", "interrupting_strike",
                "curse_of_tremor", "lumberjack", "tree_felling", "minecraft:knockback", "minecraft:punch");
        // #22 Lance Éolienne & Rafale Céleste
        register(ZEPHYR_WIND_LANCE, "wind_lance", "peregrine", "recoil_launch", "fan_volley", "scatter",
                "whisper", "string_snap");
        // #23 Jugement du Séraphin
        register(SERAPH_SKY_PILLAR, "seraphs_judgment", "minecraft:smite");
        // #24 Lame Flétrie & Chasse d'Ombre
        register(WITHER_SHADOW_RIFT, "wither_blade", "wither_edge", "shadow_hunt", "stealth_ambush", "stealth_hunter",
                "fear", "agony_breaker", "cursed_blade", "transfer_misfortune", "undead_curse", "nether_curse",
                "overworld_curse", "end_corrosion", "minecraft:vanishing_curse");
        // #25 Percée Astrale & Tir Laser
        register(ASTRAL_STAR_LANCE, "astral_pierce", "sniper", "tracer_shot", "homing", "minecraft:infinity");
        // #26 Distorsion Spatiale & Singularité
        register(VOID_WARP_SPHERE, "warp", "teleport_arrow", "swap_arrow", "phasing_bolts", "wraith_arrow",
                "enemy_attractor", "zero_gravity_shot", "retrieval", "sincere_loyalty",
                "inverted_arrows", "curse_of_the_hostile_boomerang", "minecraft:loyalty");
        // #27 Souffle Draconique & Chaos Prismatique
        register(DRAGON_CHAOS_PRISM, "dragons_breath_arrow", "chaos");
        // #28 Aiguille Temporelle & Lame Éternelle
        register(CHRONO_ETERNAL_HALO, "chrono_mending", "basic_chrono_mending", "basic_mending",
                "eternal", "promising_blade", "delay", "unbreakable", "recycle", "minecraft:mending", "minecraft:unbreaking");
        // #29 Sceau de Commandement Martial
        register(WARLORD_RALLY_SIGIL, "army_summon", "summon_army", "leader", "cavalier", "lone_wolf",
                "plunder_wealth", "food_power_strike", "minecraft:looting", "minecraft:fortune");
        // #30 Déchirure Annihilatrice
        register(ANNIHILATING_ARC, "annihilating_sweep", "sweeping_slash", "sweeping_strike", "sweeping_enhancement",
                "reinforced_sweeping", "sickle_slash", "explosive_return", "minecraft:sweeping_edge");

        // Arts martiaux mains nues (plastrons gantelets)
        GAUNTLET_MAP.put("straight_punch_gauntlet", RAZOR_GLEAM);
        GAUNTLET_MAP.put("iron_fist_gauntlet", SEISMIC_TECTONIC);
        GAUNTLET_MAP.put("diamond_shard_gauntlet", SUPREME_PRISM_EDGE);
        GAUNTLET_MAP.put("focused_punch_gauntlet", FOCUSED_BULLSEYE);
        GAUNTLET_MAP.put("flurry_gauntlet", TWIN_ECHO_SLASH);
        GAUNTLET_MAP.put("blazing_fist_gauntlet", MOLTEN_FIRE_WAVE);
        GAUNTLET_MAP.put("frost_palm_gauntlet", PERMAFROST_RIME);
        GAUNTLET_MAP.put("thunder_palm_gauntlet", CHAIN_VOLT_ARC);
        GAUNTLET_MAP.put("venom_claw_gauntlet", VENOM_BRIAR_FANG);
        GAUNTLET_MAP.put("blood_claw_gauntlet", CRIMSON_SIPHON);
        GAUNTLET_MAP.put("shockwave_palm_gauntlet", ANNIHILATING_ARC);
        GAUNTLET_MAP.put("chi_strike_gauntlet", SERAPH_SKY_PILLAR);
    }

    private static void register(WeaponVisualEffectId effect, String... keys) {
        for (String k : keys) {
            ENCHANT_MAP.put(k, effect);
        }
    }

    public static WeaponVisualEffectId byId(int id) {
        return (id >= 1 && id < BY_ID.length) ? BY_ID[id] : null;
    }

    public static WeaponVisualEffectId forEnchantmentLocation(ResourceLocation loc) {
        if (loc == null) return null;
        if ("deep_lucky_block".equals(loc.getNamespace())) {
            return ENCHANT_MAP.get(loc.getPath());
        }
        return ENCHANT_MAP.get(loc.toString());
    }

    /** Vrai si l'item est une arme (épée, hache, trident, masse, arc, arbalète, faux/houe ou arme moddée). */
    public static boolean isWeaponItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof EnchantedBookItem
                || item instanceof ArmorItem
                || item instanceof ElytraItem
                || item instanceof ShieldItem) {
            return false;
        }
        if (item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem
                || item instanceof MaceItem
                || item instanceof HoeItem
                || item instanceof ProjectileWeaponItem
                || item instanceof BowItem
                || item instanceof CrossbowItem) {
            return true;
        }
        return item instanceof TieredItem;
    }

    /** Résout les effets visuels de gantelet lorsque le porteur frappe à mains nues. */
    public static List<ResolvedLayer> resolveBareHandGauntletLayers(LivingEntity entity, int maxLayers) {
        if (entity == null || !entity.getMainHandItem().isEmpty()) return List.of();
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest == null || chest.isEmpty()) return List.of();
        ItemEnchantments ench = chest.getEnchantments();
        if (ench.isEmpty()) return List.of();

        List<ResolvedLayer> out = new ArrayList<>(4);
        for (Holder<Enchantment> entry : ench.keySet()) {
            Optional<ResourceKey<Enchantment>> rk = entry.unwrapKey();
            if (rk.isEmpty()) continue;
            String path = rk.get().location().getPath();
            WeaponVisualEffectId fx = GAUNTLET_MAP.get(path);
            if (fx == null && path.contains("gauntlet")) {
                fx = SEISMIC_TECTONIC;
            }
            if (fx != null) {
                out.add(new ResolvedLayer(fx, Math.max(1, ench.getLevel(entry))));
            }
        }
        out.sort(Comparator.comparingInt((ResolvedLayer l) -> l.effect().priority).reversed());
        return out.size() <= maxLayers ? out : out.subList(0, maxLayers);
    }

    public static List<ResolvedLayer> resolveLayers(ItemStack stack, int maxLayers) {
        if (!isWeaponItem(stack)) return List.of();
        ItemEnchantments ench = stack.getEnchantments();
        if (ench.isEmpty()) return List.of();

        boolean isPureUtilityTool = !(stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem);

        Map<WeaponVisualEffectId, Integer> bestLevelPerEffect = null;
        boolean hasExplicitCombatEnchant = false;

        for (Holder<Enchantment> entry : ench.keySet()) {
            Optional<ResourceKey<Enchantment>> rk = entry.unwrapKey();
            if (rk.isEmpty()) continue;
            ResourceLocation loc = rk.get().location();
            String path = loc.getPath();

            // Sur pioche/pelle/houe, ignore efficacité/solidité/fortune vanilla seules s'il n'y a pas d'enchant d'arme
            if (isPureUtilityTool && "minecraft".equals(loc.getNamespace())
                    && (path.equals("efficiency") || path.equals("unbreaking") || path.equals("fortune")
                        || path.equals("mending") || path.equals("silk_touch"))) {
                continue;
            }

            WeaponVisualEffectId fx = forEnchantmentLocation(loc);
            if (fx == null) continue;
            int lvl = ench.getLevel(entry);
            if (lvl <= 0) continue;

            hasExplicitCombatEnchant = true;
            if (bestLevelPerEffect == null) {
                bestLevelPerEffect = new HashMap<>(8);
            }
            int effectiveLvl = boostSharpnessTierLevel(path, lvl);
            bestLevelPerEffect.merge(fx, effectiveLvl, Math::max);
        }

        if (bestLevelPerEffect == null || bestLevelPerEffect.isEmpty() || (isPureUtilityTool && !hasExplicitCombatEnchant)) {
            return List.of();
        }

        List<ResolvedLayer> all = new ArrayList<>(bestLevelPerEffect.size());
        for (Map.Entry<WeaponVisualEffectId, Integer> e : bestLevelPerEffect.entrySet()) {
            all.add(new ResolvedLayer(e.getKey(), e.getValue()));
        }
        all.sort(Comparator.comparingInt((ResolvedLayer l) -> l.effect().priority).reversed());

        if (all.size() <= maxLayers) {
            return all;
        }

        List<ResolvedLayer> picked = new ArrayList<>(maxLayers);
        int usedCatMask = 0;
        for (ResolvedLayer layer : all) {
            int bit = 1 << layer.effect().category.ordinal();
            if ((usedCatMask & bit) == 0) {
                usedCatMask |= bit;
                picked.add(layer);
                if (picked.size() >= maxLayers) return picked;
            }
        }
        for (ResolvedLayer layer : all) {
            if (!picked.contains(layer)) {
                picked.add(layer);
                if (picked.size() >= maxLayers) break;
            }
        }
        return picked;
    }

    private static int boostSharpnessTierLevel(String path, int lvl) {
        return switch (path) {
            case "supreme_sharpness" -> lvl + 8;
            case "extreme_sharpness" -> lvl + 6;
            case "ultra_sharpness" -> lvl + 4;
            case "sharpened_sharpness" -> lvl + 3;
            case "advanced_sharpness" -> lvl + 2;
            case "reinforced_sharpness" -> lvl + 1;
            default -> lvl;
        };
    }
}
