package deepluckyblock.enchantments;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
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
 * T342-WEAPON-VFX-30 : Catalogue unifié des 30 effets visuels uniques d'enchantements d'armes
 * (lueurs animées sur le tranchant de la lame, gouttes de sang, arcs électriques, flammes, givre,
 * critiques améliorés, siphons vampiriques, piliers célestes, vortex abyssaux, tirs astraux...).
 *
 * Classe commune sans dépendance client (utilisable côté Serveur et Client).
 */
public enum WeaponVisualEffectId {

    // === I. TRANCHANT, ALLONGE, ÉCHO & EXÉCUTION (1..5) ===
    RAZOR_GLEAM         (1,  Category.EDGE_SHARP, 35, true,  0xE8F4FF, 0x9BC4E2),
    SUPREME_PRISM_EDGE  (2,  Category.EDGE_SHARP, 85, true,  0xFFFFFF, 0x62F4FF),
    SPECTRAL_REACH      (3,  Category.SPECIAL,    62, true,  0x7CF7E8, 0x2AB7CA),
    TWIN_ECHO_SLASH     (4,  Category.SPECIAL,    68, true,  0x5EE2FF, 0x2B6BFF),
    GUILLOTINE_CLEAVE   (5,  Category.SPECIAL,    80, false, 0xD91E36, 0xF0F4F8),

    // === II. COUPS CRITIQUES AMÉLIORÉS (6..8) ===
    GOLDEN_CRIT_STAR    (6,  Category.CRIT,       52, true,  0xFFE66D, 0xFFFFFF),
    FOCUSED_BULLSEYE    (7,  Category.CRIT,       64, true,  0xFFB703, 0x38F28D),
    OVERCLOCK_CYBER_X   (8,  Category.CRIT,       88, true,  0x00F5FF, 0xFF2A85),

    // === III. SANG, VAMPIRISME & CARNAGE (9..12) ===
    DRIPPING_BLOOD_EDGE (9,  Category.BLOOD,      66, false, 0xA8061A, 0xFF2438),
    CRIMSON_SIPHON      (10, Category.BLOOD,      84, false, 0x8F0416, 0xFF3355),
    VITAL_TRANSFUSION   (11, Category.BLOOD,      60, true,  0xFF4D79, 0xFFD166),
    CARNAGE_BERSERK     (12, Category.BLOOD,      86, false, 0xC9081E, 0xFF5A1F),

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
    ZEPHYR_WIND_LANCE   (22, Category.ELEMENT,    67, true,  0xC7FFF0, 0x70F5D2),
    SERAPH_SKY_PILLAR   (23, Category.ELEMENT,    96, true,  0xFFF3B0, 0xFFD043),
    WITHER_SHADOW_RIFT  (24, Category.ELEMENT,    78, false, 0x3B0D5E, 0xA339FF),
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
    private static final Map<String, WeaponVisualEffectId> ENCHANT_MAP = new HashMap<>(192);

    static {
        for (WeaponVisualEffectId v : values()) {
            BY_ID[v.id] = v;
        }

        // #01 Reflet d'Acier Affûté
        register(RAZOR_GLEAM, "sharp_edge", "reinforced_sharpness", "new_edge", "minecraft:sharpness", "minecraft:impaling", "minecraft:power");
        // #02 Prisme Diamant Suprême
        register(SUPREME_PRISM_EDGE, "advanced_sharpness", "sharpened_sharpness", "ultra_sharpness", "extreme_sharpness", "supreme_sharpness", "peerless");
        // #03 Allonge Fantôme
        register(SPECTRAL_REACH, "extension", "titans_grasp", "heavy_range");
        // #04 Double Rémanence d'Écho
        register(TWIN_ECHO_SLASH, "echo_strike", "combo_slash", "swift_strike", "duelists_mastery", "instant_shot", "ricochet", "bounce");
        // #05 Fil d'Exécution & Guillotine
        register(GUILLOTINE_CLEAVE, "guillotine", "decapitation", "beheading", "desperate_kill");

        // #06 Étoile Critique Dorée
        register(GOLDEN_CRIT_STAR, "minor_crit_rate", "crit_rate", "minor_crit_amplifier", "crit_amplifier");
        // #07 Réticule Critique Focalisé
        register(FOCUSED_BULLSEYE, "focused_crit", "focused_ambush", "sense", "marked_prey", "point_blank_shot", "ranger");
        // #08 Surcharge Cyber-Critique
        register(OVERCLOCK_CYBER_X, "overclocked_crit", "overcharge");

        // #09 Lame Sanglante Goutte-à-Goutte
        register(DRIPPING_BLOOD_EDGE, "blood_edge", "bloodthirst", "bloodthirsty", "predators_scent", "lingering_skewer");
        // #10 Siphon Vampirique Artériel
        register(CRIMSON_SIPHON, "blood_drinkers_edge", "leech", "devour", "blood_pact", "transfer");
        // #11 Transfusion Vitale
        register(VITAL_TRANSFUSION, "blood_transfusion");
        // #12 Bain de Sang & Furie Berserk
        register(CARNAGE_BERSERK, "mass_slaughter", "berserk", "brutal", "battle_scarred", "battle_hardened", "retaliation");

        // #13 Couronne de Braise & Balayage Ardent
        register(MOLTEN_FIRE_WAVE, "fiery_sweep", "sweeping_fire", "sweeping_burn_slash", "blazing_trauma",
                "scorching_prongs", "blazing_arrow", "torch", "flint_heart", "minecraft:fire_aspect", "minecraft:flame");
        // #14 Flamme d'Âme Turquoise
        register(SOUL_FIRE_CRESCENT, "blazing_soul_sweep", "blaze_sanctuary");
        // #15 Déflagration Soufrée & Météore
        register(BRIMSTONE_METEOR, "brimstone", "meteor", "meteoric_slam");
        // #16 Arcs Voltaïques & Chaîne d'Éclairs
        register(CHAIN_VOLT_ARC, "thundercall_pact", "chain_lightning", "thunderstruck", "storm_strike", "elemental_chain", "minecraft:channeling");
        // #17 Courant Marin & Choc Hydro
        register(HYDRO_TIDAL_SURGE, "hydro_shock", "ocean_current", "sea_pogo", "water_arrow", "dry_arrow", "minecraft:riptide");
        // #18 Maelström des Abysses
        register(ABYSSAL_MAELSTROM, "abyssal_undertow", "tidecaller", "harpoon", "grapple");
        // #19 Givre Cristallin
        register(PERMAFROST_RIME, "heavy_slow_hit", "sluggish_weakness", "suppression_shot", "weakness_trauma", "weakness_wound", "exhaustion", "sighing_strike");
        // #20 Croc Venimeux & Ronces
        register(VENOM_BRIAR_FANG, "poison_fang", "poison_scar", "poison_arrow", "briar_arrow");
        // #21 Onde Tellurique & Brise-Géant
        register(SEISMIC_TECTONIC, "seismic_strike", "giant_slayer", "titan_slayer", "guard_breaker", "mighty_strike",
                "heavy_impact", "dive_strike", "minor_dive_strike", "disarm", "interrupting_strike");
        // #22 Lance Éolienne & Rafale Céleste
        register(ZEPHYR_WIND_LANCE, "wind_lance", "peregrine", "recoil_launch", "fan_volley", "scatter", "whisper");
        // #23 Jugement du Séraphin
        register(SERAPH_SKY_PILLAR, "seraphs_judgment", "minecraft:smite");
        // #24 Lame Flétrie & Chasse d'Ombre
        register(WITHER_SHADOW_RIFT, "wither_blade", "wither_edge", "shadow_hunt", "stealth_ambush", "stealth_hunter",
                "fear", "agony_breaker", "cursed_blade", "transfer_misfortune", "undead_curse", "nether_curse", "overworld_curse");
        // #25 Percée Astrale & Tir Laser
        register(ASTRAL_STAR_LANCE, "astral_pierce", "sniper", "tracer_shot", "homing");
        // #26 Distorsion Spatiale & Singularité
        register(VOID_WARP_SPHERE, "warp", "teleport_arrow", "swap_arrow", "phasing_bolts", "wraith_arrow",
                "enemy_attractor", "zero_gravity_shot", "retrieval", "sincere_loyalty");
        // #27 Souffle Draconique & Chaos Prismatique
        register(DRAGON_CHAOS_PRISM, "dragons_breath_arrow", "chaos");
        // #28 Aiguille Temporelle & Lame Éternelle
        register(CHRONO_ETERNAL_HALO, "chrono_mending", "basic_chrono_mending", "eternal", "promising_blade", "delay", "unbreakable");
        // #29 Sceau de Commandement Martial
        register(WARLORD_RALLY_SIGIL, "army_summon", "summon_army", "leader", "cavalier", "lone_wolf", "plunder_wealth", "food_power_strike");
        // #30 Déchirure Annihilatrice
        register(ANNIHILATING_ARC, "annihilating_sweep", "sweeping_slash", "sweeping_strike", "sweeping_enhancement",
                "reinforced_sweeping", "explosive_return", "minecraft:sweeping_edge");
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

    /** Vrai si l'item est une arme (épée, hache, trident, masse, arc, arbalète ou arme moddée). */
    public static boolean isWeaponItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem
                || item instanceof MaceItem
                || item instanceof ProjectileWeaponItem
                || item instanceof BowItem
                || item instanceof CrossbowItem) {
            return true;
        }
        if (item instanceof TieredItem) {
            String desc = item.getDescriptionId();
            return desc.contains("sword") || desc.contains("blade") || desc.contains("katana")
                    || desc.contains("dagger") || desc.contains("scythe") || desc.contains("halberd")
                    || desc.contains("spear") || desc.contains("glaive") || desc.contains("rapier")
                    || desc.contains("axe") || desc.contains("hammer") || desc.contains("greatsword");
        }
        return false;
    }

    /**
     * Extrait jusqu'à {@code maxLayers} effets visuels distincts triés par priorité décroissante,
     * en privilégiant la diversité des catégories (ex: Tranchant + Sang + Foudre) afin que les
     * armes Lucky Block multi-enchantées combinent harmonieusement leurs animations.
     */
    public static List<ResolvedLayer> resolveLayers(ItemStack stack, int maxLayers) {
        if (!isWeaponItem(stack)) return List.of();
        ItemEnchantments ench = stack.getEnchantments();
        if (ench.isEmpty()) return List.of();

        Map<WeaponVisualEffectId, Integer> bestLevelPerEffect = null;
        for (Holder<Enchantment> entry : ench.keySet()) {
            Optional<ResourceKey<Enchantment>> rk = entry.unwrapKey();
            if (rk.isEmpty()) continue;
            WeaponVisualEffectId fx = forEnchantmentLocation(rk.get().location());
            if (fx == null) continue;
            int lvl = ench.getLevel(entry);
            if (lvl <= 0) continue;
            if (bestLevelPerEffect == null) {
                bestLevelPerEffect = new HashMap<>(8);
            }
            // Bonus d'échelle interne pour les paliers de tranchant (advanced..supreme)
            String path = rk.get().location().getPath();
            int effectiveLvl = boostSharpnessTierLevel(path, lvl);
            bestLevelPerEffect.merge(fx, effectiveLvl, Math::max);
        }

        if (bestLevelPerEffect == null || bestLevelPerEffect.isEmpty()) {
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

        // Passe 1 : 1 meilleur représentant par catégorie (diversité visuelle : lame + sang + élément)
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
        // Passe 2 : complète avec les plus hautes priorités restantes
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
