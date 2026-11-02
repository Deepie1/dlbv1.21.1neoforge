package deepluckyblock.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.IEventBus;

import deepluckyblock.Rarity;

/**
 * ModEnchantments : Registre des enchantements autonomes.
 *
 * Modifs appliquees :
 *  - VEIL -> VERY_RARE, niveau max 2 (reserve MYTHIC+).
 *  + POISON_ARROW (fleche verte, nuage de poison).
 *  + DIVINE_PROTECTION (remplace les protections vanilla a luck 70+).
 *  + FIRE_PROTECTION_ENHANCEMENT, PROJECTILE_PROTECTION_ENHANCEMENT.
 *  + INFERNAL_ARMOR (RELIC, feu/lave -90%).
 */
@SuppressWarnings({"removal", "deprecation"})
public class ModEnchantments {

        private static final EquipmentSlot[] HANDS = new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final EquipmentSlot[] ALL_SLOTS = EquipmentSlot.values();
    private static final EquipmentSlot[] MAIN_HAND = new EquipmentSlot[]{EquipmentSlot.MAINHAND};
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] HEAD = new EquipmentSlot[]{EquipmentSlot.HEAD};
    private static final EquipmentSlot[] CHEST = new EquipmentSlot[]{EquipmentSlot.CHEST};
    private static final EquipmentSlot[] LEGS = new EquipmentSlot[]{EquipmentSlot.LEGS};
    private static final EquipmentSlot[] FEET = new EquipmentSlot[]{EquipmentSlot.FEET};
    private static final EquipmentSlot[] OFF_HAND = new EquipmentSlot[]{EquipmentSlot.OFFHAND};
    // PORT 1.21.1 : bloc d'initialisation statique -> remplace le DeferredRegister
    // (Registries.ENCHANTMENT est un registre DATAPACK depuis 1.21 ; RegisterEvent ne se
    // declenche JAMAIS pour ce type de registre, donc les suppliers d'un DeferredRegister
    // n'etaient EN FAIT JAMAIS executes -> CustomEnchantment.META restait vide et TOUT le
    // systeme d'enchantement custom du mod etait casse silencieusement). Les ~395
    // enchantements reels sont maintenant de vraies entrees de registre chargees depuis
    // les JSON de data/deep_lucky_block/enchantment/*.json (seule methode valide en 1.21+).
    // Ce bloc appelle CustomEnchantment.create() de maniere EAGER et GARANTIE (execution au
    // chargement de la classe) uniquement pour peupler CustomEnchantment.META (categorie,
    // slots, niveau max, rarete, treasure/curse) consommee par canEnchantMod/getMaxLevelMod/
    // getSlotsMod/isCurseMod/checkCompatibilityMod/formatNameMod dans tout le mod.
    static {

    // ===================== 1. EPEES & COMBAT (SWORDS) =====================
            CustomEnchantment.create("supreme_sharpness", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 100);
            CustomEnchantment.create("advanced_sharpness", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 25);
            CustomEnchantment.create("extreme_sharpness", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 20);
            CustomEnchantment.create("ultra_sharpness", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 15);
            CustomEnchantment.create("titan_slayer", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("giant_slayer", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("swift_strike", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("extension", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("cursed_blade", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("battle_hardened", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("new_edge", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("blood_drinkers_edge", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("bloodthirst", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("bloodthirsty", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("blood_pact", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("blood_transfusion", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("blood_edge", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("wither_edge", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("wither_blade", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1);
            CustomEnchantment.create("crit_rate", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("dive_strike", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("meteoric_slam", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("combo_slash", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("annihilating_sweep", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("fiery_sweep", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sweeping_burn_slash", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("unbreakable", Rarity.LEGENDARY, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 1);
            CustomEnchantment.create("soul_bound", Rarity.MYTHIC, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 1);
            CustomEnchantment.create("beheading", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("decapitation", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("guillotine", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("summon_army", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1);
            CustomEnchantment.create("shadow_hunt", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("plunder_wealth", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("weakness_wound", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("weakness_trauma", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("brutal", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("mighty_strike", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("sighing_strike", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("flint_strike", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("promising_blade", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("minor_dive_strike", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("poison_scar", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("predators_scent", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("sense", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("desperate_kill", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("food_power_strike", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("devour", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("experience_harvest", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("blaze_sanctuary", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1);
            CustomEnchantment.create("straight_punch_gauntlet", Rarity.COMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5);
            CustomEnchantment.create("lone_wolf", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("mass_slaughter", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sluggish_weakness", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("heavy_slow_hit", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("duelists_mastery", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sharp_edge", Rarity.COMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("reinforced_sharpness", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("sharpened_sharpness", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("blazing_trauma", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("blazing_soul_sweep", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sweeping_fire", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sweeping_enhancement", Rarity.COMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sweeping_strike", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("sweeping_slash", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("agony_breaker", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("focused_ambush", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("cavalier", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("leader", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("peerless", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10);
            CustomEnchantment.create("poison_fang", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("reinforced_sweeping", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);

    // ===================== 2. HACHES (AXES) =====================
            CustomEnchantment.create("lumberjack", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5);
            CustomEnchantment.create("tree_felling", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);

    // ===================== 3. PIOCHES & OUTILS (PICKAXES & TOOLS) =====================
            CustomEnchantment.create("extracting", Rarity.LEGENDARY, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1);
            CustomEnchantment.create("core_harvesting", Rarity.LEGENDARY, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1);
            CustomEnchantment.create("molten", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1);
            CustomEnchantment.create("vein_miner", Rarity.VERY_RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("burrowing", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);

    // ===================== 4. PELLES (SHOVELS) =====================
            CustomEnchantment.create("shovel_halberd", Rarity.VERY_RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 5);
            CustomEnchantment.create("heavy_hit", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("armor_pry", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("shovel_blade", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("scooping", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("snow_remover", Rarity.COMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1);

    // ===================== 5. ARCS & ARBALETES (BOWS & CROSSBOWS) =====================
            CustomEnchantment.create("phasing_bolts", Rarity.MYTHIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("storm_strike", Rarity.MYTHIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("delay", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("sniper", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("fan_volley", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("chaos", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            // T352-CHAOS-ELEMENT : 4 nouveaux enchantements de la famille CHAOS (sombre + multicolore).
            // Ils complètent `chaos` pour donner 5 enchantements dédiés à cette nouvelle famille.
            CustomEnchantment.create("chaos_entropy", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("chaos_nexus", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("chaos_rupture", Rarity.LEGENDARY, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("prismatic_brand", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("brimstone", Rarity.LEGENDARY, CustomEnchantment.Cat.CROSSBOW, MAIN_HAND, 5);
            CustomEnchantment.create("scatter", Rarity.RARE, CustomEnchantment.Cat.CROSSBOW, MAIN_HAND, 1);
            CustomEnchantment.create("torch", Rarity.COMMON, CustomEnchantment.Cat.CROSSBOW, MAIN_HAND, 1);
            // T340-RANGED-OVERHAUL : poison_arrow maxLevel 2 -> 3 (aligné sur eternal_level_100_pool >= III et progression I->III)
            CustomEnchantment.create("poison_arrow", Rarity.COMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);

    // ===================== 6. TRIDENTS, MASSES & PECHE =====================
            CustomEnchantment.create("warp", Rarity.MYTHIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("leech", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            // T340-RANGED-OVERHAUL : thunderstruck maxLevel 1 -> 3 (aligné sur .desc "+3 dégâts magiques/niv.")
            CustomEnchantment.create("thunderstruck", Rarity.EPIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("meteor", Rarity.LEGENDARY, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("disarm", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("grapple", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);

    // ===================== 7. CASQUES (HELMETS) =====================
            CustomEnchantment.create("commander", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("illager_commander", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("undead_commander", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("omniscient_perception", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("veil", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("mystic_mind", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);
            CustomEnchantment.create("assimilation", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1);

    // ===================== 8. PLASTRONS (CHESTPLATES) =====================
            CustomEnchantment.create("inner_fear", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1);
            CustomEnchantment.create("wardenspine", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("punching_armor", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 10);
            CustomEnchantment.create("adrenaline", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("strafe", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("amphibious", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1);

    // ===================== 9. JAMBIERES (LEGGINGS) =====================
            CustomEnchantment.create("slide", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("dash", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("leap_power", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 5);
            CustomEnchantment.create("basic_leap_power", Rarity.COMMON, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);

    // ===================== 10. BOTTES (BOOTS) =====================
            CustomEnchantment.create("ascending_gale", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 10);
            CustomEnchantment.create("buoy", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("bouncy", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("electric_speed", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("sticky", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1);
            CustomEnchantment.create("blaze_walker", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1);

    // ===================== 11. BOUCLIERS (SHIELDS) =====================
            CustomEnchantment.create("bash_power", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, HANDS, 3);
            CustomEnchantment.create("bash_damage", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, HANDS, 3);
            CustomEnchantment.create("shield_efficiency", Rarity.COMMON, CustomEnchantment.Cat.BREAKABLE, HANDS, 1);

    // ===================== 12. UNIVERSELS & REPARATION =====================
            CustomEnchantment.create("chrono_mending", Rarity.LEGENDARY, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 5);
            CustomEnchantment.create("basic_chrono_mending", Rarity.VERY_RARE, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 3);
            CustomEnchantment.create("basic_mending", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 5);
            CustomEnchantment.create("phoenix", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR, ARMOR, 3);
            CustomEnchantment.create("eternal", Rarity.LEGENDARY, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 1);
            CustomEnchantment.create("magnetic", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 1);

    // ===================== 13. PROTECTIONS PERSONNALISEES =====================
            CustomEnchantment.create("explosion_guard", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR, ARMOR, 5, false, false);
            CustomEnchantment.create("fire_protection_enhancement", Rarity.RARE, CustomEnchantment.Cat.ARMOR, ARMOR, 4);
            CustomEnchantment.create("projectile_protection_enhancement", Rarity.RARE, CustomEnchantment.Cat.ARMOR, ARMOR, 4);
            CustomEnchantment.create("divine_protection", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR, ARMOR, 5);
            CustomEnchantment.create("infernal_armor", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1);

    // ===================== 13b. COEURS ELEMENTAIRES (plastron uniquement) =====================
    // +1 coeur (2 PV) par niveau, niveau max 3 -> jusqu'a +3 coeurs (6 PV).
    // Rendus dans le HUD comme des coeurs vanilla recolores/texturés par
    // élément (voir client/ElementalHeartOverlay + EnchantAura pour le theme
    // de couleur), un enchantement distinct par élément pour permettre de
    // les cumuler sur des plastrons différents / de les distinguer en jeu.
            CustomEnchantment.create("fire_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("water_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("earth_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("wind_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("electric_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("shadow_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("light_heart", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);

    // ===================== 13c. ENCHANTEMENTS LIES AUX COEURS ELEMENTAIRES =====================
    // 8 enchantements pensés comme des "pactes elementaires" (inspiration
    // manhwa : chaque element a un vrai avantage ET un vrai defaut, sauf le
    // pacte des tenebres qui est franchement un enchantement maudit).
    // Chacun est restreint a une piece d'armure ou a l'arme, comme demande.
            CustomEnchantment.create("ember_core", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            CustomEnchantment.create("tidal_ward", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("stonebound_roots", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("gale_step", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("overcharge", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("radiant_ward", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            // Umbral Pact est un pacte maudit : il ne va PAS dans la liste des
            // curses "malediction pure" (il apporte un vrai avantage offensif),
            // mais son cout (vulnerabilite + saignee de vie) et son aura
            // visuelle malsaine (voir ElementalHeartOverlay) le signalent
            // clairement comme un choix risque.
            CustomEnchantment.create("umbral_pact", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("heart_resonance", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1);

    // ===================== 13d. PACTES ELEMENTAIRES (2e vague, retour utilisateur) =====================
    // 8 nouveaux enchantements lies aux coeurs elementaires -- inspiration
    // manhwa/webtoon : chaque element a un VRAI avantage et un VRAI cout
    // (sauf les deux marques "maudit" qui sont volontairement a double
    // tranchant tres defavorable). Chacun restreint a une piece precise
    // (ou a l'arme/aux bottes) comme demande.
            // SALAMANDER'S VOW (Fire, casque) : regeneration passive quand
            // en feu au lieu d'en souffrir -- trope "corps de salamandre" --
            // mais s'annule immediatement au contact de l'eau (faiblesse
            // classique feu/eau).
            CustomEnchantment.create("salamanders_vow", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            // RIPTIDE CURSE (Water, jambieres, MAUDIT) : nage extremement
            // rapide et respiration aquatique totale, mais degats accrus
            // hors de l'eau -- "poisson hors de l'eau" pousse a l'extreme.
            CustomEnchantment.create("riptide_curse", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3, false, true);
            // TITAN'S GRASP (Earth, arme) : degats de melee augmentes mais
            // vitesse d'attaque reduite -- le colosse lent et ecrasant.
            CustomEnchantment.create("titans_grasp", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            // ZEPHYR'S GAMBIT (Wind, plastron, FUN) : chance de micro-
            // teleportation d'esquive quand on encaisse un coup critique --
            // "insaisissable comme le vent".
            CustomEnchantment.create("zephyrs_gambit", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            // THUNDERCALL PACT (Electric, arme) : chaine electrique
            // renforcee sous la pluie -- synergie eau/electricite assumee
            // plutot que la faiblesse classique.
            CustomEnchantment.create("thundercall_pact", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            // VOIDLING'S HUNGER (Shadow, casque, MAUDIT) : vol de vie accru
            // la nuit, mais saignee de vie passive en plein jour --
            // "créature des tenebres qui craint la lumiere du soleil".
            CustomEnchantment.create("voidlings_hunger", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3, false, true);
            // SERAPH'S JUDGMENT (Light, arme) : degats punitifs contre les
            // morts-vivants, mais degats reduits contre tout le reste --
            // "lumiere purificatrice" a specialisation stricte.
            CustomEnchantment.create("seraphs_judgment", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            // CHIMERA'S WHIM (multi-element, bottes, FUN) : a chaque saut,
            // declenche un effet aleatoire parmi les elements de coeur
            // actifs sur le joueur -- la "chimere instable" imprevisible.
            CustomEnchantment.create("chimeras_whim", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1);

    // ===================== 14. MALEDICTIONS / CURSES + RESTE =====================
            CustomEnchantment.create("nether_corrosion", Rarity.VERY_RARE, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 1, false, true);
            CustomEnchantment.create("end_corrosion", Rarity.VERY_RARE, CustomEnchantment.Cat.BREAKABLE, ALL_SLOTS, 1, false, true);
            CustomEnchantment.create("fear", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("haste_surge", Rarity.COMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 10, false, false); // positif : affiché ORANGE, plus rouge
            CustomEnchantment.create("breath_holding", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false); // positif : affiché BLEU
            CustomEnchantment.create("high_altitude_hypoxia", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false); // positif (protège en altitude) : affiché ORANGE
            CustomEnchantment.create("deep_hypoxia", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false); // positif (protège en profondeur) : affiché ORANGE
            CustomEnchantment.create("curse_of_tremor", Rarity.RARE, CustomEnchantment.Cat.BREAKABLE, HANDS, 1, false, true);
            CustomEnchantment.create("inverted_arrows", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("whisper", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("flint_heart", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("ricochet", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("ranger", Rarity.EPIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("zero_gravity_shot", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("enemy_attractor", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1, true, false);
            CustomEnchantment.create("astral_pierce", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("heavy_range", Rarity.RARE, CustomEnchantment.Cat.CROSSBOW, MAIN_HAND, 5);
            CustomEnchantment.create("peregrine", Rarity.RARE, CustomEnchantment.Cat.CROSSBOW, MAIN_HAND, 3);
            CustomEnchantment.create("wraith_arrow", Rarity.LEGENDARY, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("blazing_arrow", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("dragons_breath_arrow", Rarity.MYTHIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("water_arrow", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("dry_arrow", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("heavy_impact", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("teleport_arrow", Rarity.EPIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("swap_arrow", Rarity.MYTHIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("toy", Rarity.COMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("instant_shot", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("transfer", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 1);
            CustomEnchantment.create("weak_force", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 3, false, true); // négatif -> rouge forcé
            CustomEnchantment.create("string_snap", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 3, false, true); // négatif -> rouge forcé
            CustomEnchantment.create("retrieval", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 10);
            CustomEnchantment.create("rain_nourishment", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 5, false, false);
            CustomEnchantment.create("fast_eating", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3, false, false);
            CustomEnchantment.create("pest_aversion", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false);
            CustomEnchantment.create("king", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 5, false, false);
            CustomEnchantment.create("general", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3, false, false);
            CustomEnchantment.create("priests_blessing", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false);
            CustomEnchantment.create("breath_heal", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false);
            CustomEnchantment.create("night_veil", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false);
            CustomEnchantment.create("resistance_burst", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 10, false, false);
            CustomEnchantment.create("sustained_resistance", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("pain_devour", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("stomach_pouch", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("big_stomach", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("sprint_impact", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 10, false, false);
            CustomEnchantment.create("block", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("life_block", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("oceans_blessing", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("iron_lung", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("last_stand_toughness", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("heartbeat_rhythm", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 10, false, false);
            CustomEnchantment.create("martial_rhythm", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("powerful_thorns", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 2, false, false);
            CustomEnchantment.create("soul_repulsion_blast", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 2, false, false);
            CustomEnchantment.create("item_attraction", Rarity.COMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("dread_aura", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("thirsty_heart", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("conduit_heart", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("pebblekin", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("heavy_wound_punch_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("empty_fist_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("red_strike_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("tendon_break_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("bone_break_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("focused_punch_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("straight_reach_gauntlet", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("wing_chun_gauntlet", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("sprint_punch_gauntlet", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("rock_breaking_gauntlet", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("diamond_glow_gauntlet", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("blaze_battle", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("blaze_repulsion_heart", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("scorching_bath", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 10, false, false);
            CustomEnchantment.create("fire_devour", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("burning_heart", Rarity.COMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("fire_shell", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("blazing_rage", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("meat_armor", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("veteran_of_a_hundred_battles", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("tempered_body", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, false);
            CustomEnchantment.create("gluttony", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 5, false, false);
            CustomEnchantment.create("sturdy_brute", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("steel_body", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("corpulent", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("shelter", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("campfire_heal", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, false);
            CustomEnchantment.create("focused_steadiness", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3, false, false);
            CustomEnchantment.create("magma_walker", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("meteor_stomp", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("cloud_stride", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("basic_lightness", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 5, false, false);
            CustomEnchantment.create("lightness", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 5, false, false);
            CustomEnchantment.create("basic_swiftness", Rarity.COMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("swiftness", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("rapid_drop_stop", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("twenty_fourth_blessing", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("air_walk", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("aerial_cushion_step", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("panic_escape", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("last_stand_escape", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("oxygen_sprint", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("hypoxia_haste", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("stinky_feet", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("super_stinky_feet", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("air_hover", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, false);
            CustomEnchantment.create("crash_landing", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, true);
            CustomEnchantment.create("shadow_sprint", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("light_walker", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("slope_strider", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 10, false, false);
            CustomEnchantment.create("shortness_of_breath", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, true);
            CustomEnchantment.create("enemys_pursuit", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 1, false, true);
            CustomEnchantment.create("chest_tightness", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1, false, true);
            CustomEnchantment.create("fracture", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 1, false, true);
            CustomEnchantment.create("wound_crack", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 3, false, true);
            CustomEnchantment.create("crippled", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 2, false, true); // négatif -> rouge forcé
            CustomEnchantment.create("armor_damage", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 3, false, true);
            CustomEnchantment.create("armor_shatter", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 2, false, true); // négatif -> rouge forcé
            CustomEnchantment.create("noise_disturbance", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, true);
            CustomEnchantment.create("lightning_calamity", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, true);
            CustomEnchantment.create("wing_toughness_armor", Rarity.LEGENDARY, CustomEnchantment.Cat.WEARABLE, CHEST, 1, false, false);
            CustomEnchantment.create("earth_bound", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR, ARMOR, 1, false, true); // négatif (chute accélérée) -> rouge forcé
            CustomEnchantment.create("undead_curse", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("overworld_curse", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("nether_curse", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("curse_of_the_end", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, true);
            CustomEnchantment.create("battle_scarred", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("blunt", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("exhaustion", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);
            CustomEnchantment.create("short_reach", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("minor_crit_rate", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5, false, false);
            CustomEnchantment.create("focused_crit", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("minor_crit_amplifier", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 5, false, false);
            CustomEnchantment.create("crit_amplifier", Rarity.VERY_RARE, CustomEnchantment.Cat.BREAKABLE, MAIN_HAND, 5, false, false);
            CustomEnchantment.create("overclocked_crit", Rarity.MYTHIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 10, false, false);
            CustomEnchantment.create("sickle_damage", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("sickle_slash", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 5, false, false);
            CustomEnchantment.create("bountiful_harvest", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("loot_harvest", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("farmers_hoe", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("weeding_hoe", Rarity.COMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("soul_reaping_sickle", Rarity.EPIC, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 10, false, false);
            CustomEnchantment.create("soul_sacrifice_slash", Rarity.LEGENDARY, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("reapers_scythe", Rarity.LEGENDARY, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 5, false, false);
            CustomEnchantment.create("utility_trench", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("universal_trench", Rarity.MYTHIC, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("shield_quake", Rarity.RARE, CustomEnchantment.Cat.WEAPON, OFF_HAND, 2, false, false);
            CustomEnchantment.create("hatred_draw", Rarity.UNCOMMON, CustomEnchantment.Cat.WEAPON, OFF_HAND, 1, false, true); // négatif (provoque les mobs) -> rouge forcé
            CustomEnchantment.create("ore_detector", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("horse_boots", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("food_is_heaven", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, false);
            CustomEnchantment.create("transfer_misfortune", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("point_blank_shot", Rarity.UNCOMMON, CustomEnchantment.Cat.BOW, MAIN_HAND, 3, false, false);
            CustomEnchantment.create("recycle", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, MAIN_HAND, 1, false, false);
            CustomEnchantment.create("cloud_walker", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, false);
            CustomEnchantment.create("stealth_hunter", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3, false, false);

    // ===================== 15. AJOUTS SYNCHRONISES V19 =====================
    // Anciens IDs présents dans les pools/handlers mais absents du registre.
            CustomEnchantment.create("army_summon", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1);
            CustomEnchantment.create("berserk", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("bounce", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("exercise", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("haste_continuation", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("military_shovel", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);

    // Six enchantements de déplacement.
            CustomEnchantment.create("momentum", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("trailblazer", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("wall_runner", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 2);
            CustomEnchantment.create("big_step", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("giant_step", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 5);
            CustomEnchantment.create("evasive_footwork", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("combat_lunge", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("windrunner", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);

    // Casques.
            CustomEnchantment.create("mental_clarity", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            CustomEnchantment.create("foresight", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            CustomEnchantment.create("miners_eye", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 5);
            CustomEnchantment.create("echolocation", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 2);
            CustomEnchantment.create("concentration", Rarity.RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 3);
            CustomEnchantment.create("curse_of_whispers", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_HEAD, HEAD, 1, false, true);

    // Plastrons.
            CustomEnchantment.create("adaptive_shell", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("second_wind", Rarity.LEGENDARY, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 1);
            CustomEnchantment.create("bastion_heart", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("kinetic_conversion", Rarity.RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("vital_reserve", Rarity.EPIC, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3);
            CustomEnchantment.create("curse_of_the_glass_boned_champion", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_CHEST, CHEST, 3, false, true);

    // Jambières.
            CustomEnchantment.create("phantom_step", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 2);
            CustomEnchantment.create("pivot", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("anchoring", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("combat_roll", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("hunters_pace", Rarity.RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("carriers_stride", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3);
            CustomEnchantment.create("curse_of_brittle_knees", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_LEGS, LEGS, 3, false, true);

    // Bottes.
            CustomEnchantment.create("spring_coil_soles", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("tidal_step", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("frost_trail", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 2);
            CustomEnchantment.create("ember_step", Rarity.RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 2);
            CustomEnchantment.create("surefooted", Rarity.UNCOMMON, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("dimensional_step", Rarity.MYTHIC, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3);
            CustomEnchantment.create("curse_of_quicksand", Rarity.VERY_RARE, CustomEnchantment.Cat.ARMOR_FEET, FEET, 3, false, true);

    // Armes de mêlée.
            CustomEnchantment.create("echo_strike", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("interrupting_strike", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("guard_breaker", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("stealth_ambush", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("retaliation", Rarity.RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("marked_prey", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("seismic_strike", Rarity.EPIC, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 3);
            CustomEnchantment.create("curse_of_mercy", Rarity.VERY_RARE, CustomEnchantment.Cat.WEAPON, MAIN_HAND, 1, false, true);

    // Outils.
            CustomEnchantment.create("selective_excavation", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("mining_stability", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 2);
            CustomEnchantment.create("delicate_extraction", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("prospecting", Rarity.RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("replanting", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 1);
            CustomEnchantment.create("composting", Rarity.UNCOMMON, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3);
            CustomEnchantment.create("curse_of_cave_in", Rarity.VERY_RARE, CustomEnchantment.Cat.DIGGER, MAIN_HAND, 3, false, true);

    // Projectiles.
            CustomEnchantment.create("tracer_shot", Rarity.VERY_RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 5);
            CustomEnchantment.create("briar_arrow", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("suppression_shot", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("elemental_chain", Rarity.EPIC, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("recoil_launch", Rarity.RARE, CustomEnchantment.Cat.BOW, MAIN_HAND, 3);
            CustomEnchantment.create("ocean_current", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);

    // Boucliers.
            CustomEnchantment.create("perfect_parry", Rarity.EPIC, CustomEnchantment.Cat.BREAKABLE, OFF_HAND, 3);
            CustomEnchantment.create("curse_of_resonance", Rarity.VERY_RARE, CustomEnchantment.Cat.BREAKABLE, OFF_HAND, 3, false, true);

    // ===================== 16. PECHE, TRIDENT ET ELYTRA =====================
    // Canne à pêche (15)
            CustomEnchantment.create("pulling_power", Rarity.VERY_RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 5);
            CustomEnchantment.create("grappling_hook", Rarity.EPIC, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3);
            CustomEnchantment.create("abyssal_dredge", Rarity.RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3);
            CustomEnchantment.create("reliability", Rarity.RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 2);
            CustomEnchantment.create("experienced_fishing", Rarity.UNCOMMON, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3);
            CustomEnchantment.create("infernal_line", Rarity.LEGENDARY, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 1);
            CustomEnchantment.create("thunder_hook", Rarity.EPIC, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3);
            CustomEnchantment.create("barbed_hook", Rarity.VERY_RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 5);
            CustomEnchantment.create("cooked_catch", Rarity.UNCOMMON, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 1);
            CustomEnchantment.create("anglers_blessing", Rarity.VERY_RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 5);
            CustomEnchantment.create("ender_hook", Rarity.EPIC, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 1);
            CustomEnchantment.create("monster_fishing", Rarity.EPIC, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3);
            CustomEnchantment.create("curse_of_false_bite", Rarity.UNCOMMON, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("curse_of_the_abyss", Rarity.RARE, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("curse_of_scorching_line", Rarity.UNCOMMON, CustomEnchantment.Cat.FISHING_ROD, MAIN_HAND, 3, false, true);

    // Trident (15)
            CustomEnchantment.create("homing", Rarity.MYTHIC, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 4);
            CustomEnchantment.create("hydro_shock", Rarity.MYTHIC, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 5);
            CustomEnchantment.create("sincere_loyalty", Rarity.LEGENDARY, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 1);
            CustomEnchantment.create("scorching_prongs", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("wind_lance", Rarity.VERY_RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("harpoon", Rarity.EPIC, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 5);
            CustomEnchantment.create("abyssal_undertow", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("chain_lightning", Rarity.MYTHIC, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("lingering_skewer", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("tidecaller", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("sea_pogo", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3);
            CustomEnchantment.create("explosive_return", Rarity.EPIC, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 2);
            CustomEnchantment.create("curse_of_the_hostile_boomerang", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("curse_of_the_lightning_rod", Rarity.RARE, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3, false, true);
            CustomEnchantment.create("curse_of_the_depths", Rarity.UNCOMMON, CustomEnchantment.Cat.TRIDENT, MAIN_HAND, 3, false, true);

    // Elytra (10)
            CustomEnchantment.create("aerodynamic", Rarity.RARE, CustomEnchantment.Cat.BREAKABLE, CHEST, 3);
            CustomEnchantment.create("air_brake", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, CHEST, 1);
            CustomEnchantment.create("winging", Rarity.EPIC, CustomEnchantment.Cat.BREAKABLE, CHEST, 1);
            CustomEnchantment.create("iron_wings", Rarity.EPIC, CustomEnchantment.Cat.BREAKABLE, CHEST, 3);
            CustomEnchantment.create("rocket_blast", Rarity.LEGENDARY, CustomEnchantment.Cat.BREAKABLE, CHEST, 1);
            CustomEnchantment.create("wind_rider", Rarity.MYTHIC, CustomEnchantment.Cat.BREAKABLE, CHEST, 3);
            CustomEnchantment.create("ground_skimmer", Rarity.RARE, CustomEnchantment.Cat.BREAKABLE, CHEST, 3);
            CustomEnchantment.create("curse_of_turbulence", Rarity.UNCOMMON, CustomEnchantment.Cat.BREAKABLE, CHEST, 3, false, true);
            CustomEnchantment.create("curse_of_brittle_wings", Rarity.RARE, CustomEnchantment.Cat.BREAKABLE, CHEST, 3, false, true);
            CustomEnchantment.create("curse_of_the_sky_conductor", Rarity.RARE, CustomEnchantment.Cat.BREAKABLE, CHEST, 3, false, true);

        }

    /**
     * PORT 1.21.1 : ne fait plus d'enregistrement de registre (voir bloc static ci-dessus).
     * Conserve pour compatibilite avec DeepLuckyBlockMod.java (et ses copies patch
     * FIXES_MCREATOR) qui appellent ModEnchantments.register(modBus) — cet appel garantit
     * juste le chargement de la classe (donc l'execution du bloc static de METADATA).
     */
    public static void register(IEventBus eventBus) {
        // no-op : voir le bloc static ci-dessus.
    }
}
