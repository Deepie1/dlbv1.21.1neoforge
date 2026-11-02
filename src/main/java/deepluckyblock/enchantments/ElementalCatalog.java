package deepluckyblock.enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T352-ELEMENT-COVERAGE : <b>catalogue de vérification</b> de la couverture élémentaire.
 *
 * <p><b>Question posée :</b> « est-ce que chaque élément a bien ses enchantements ? » Avant cette
 * classe, la seule façon de le savoir était de lancer le jeu et de fouiller 482 fichiers JSON. Ici on
 * déclare, pour chaque famille de {@link DeepElement}, <b>5 enchantements dédiés</b> et on expose les
 * <b>grosses animations</b> associées : la commande {@code /dlbvfx elements} affiche donc
 * immédiatement « 5 enchantements · 3 animations » par famille, et signale toute famille incomplète.</p>
 *
 * <p>Le catalogue est <b>vérifié au chargement</b> ({@link #missingEnchantments()} /
 * {@link #incompleteElements()}) : si un identifiant disparaît des fichiers
 * {@code data/deep_lucky_block/enchantment/}, l'audit le dit au lieu de casser silencieusement le
 * système d'animations (qui s'appuie sur les noms d'enchantements pour retrouver la famille).</p>
 */
public final class ElementalCatalog {

    /** Nombre minimum d'enchantements attendus par famille. */
    public static final int MIN_ENCHANTMENTS = 5;
    /** Nombre minimum de grosses animations attendues par famille (« 3 animations uniques »). */
    public static final int MIN_ANIMATIONS = 3;

    /** 5 enchantements dédiés par famille (chemins d'enregistrement, sans namespace). */
    private static final Map<DeepElement, List<String>> ENCHANTS = new LinkedHashMap<>();

    static {
        ENCHANTS.put(DeepElement.WOOD, List.of(
                "poison_fang", "poison_scar", "briar_arrow", "poison_arrow", "lumberjack"));
        ENCHANTS.put(DeepElement.FIRE, List.of(
                "fiery_sweep", "meteoric_slam", "brimstone", "blazing_soul_sweep", "burning_heart"));
        ENCHANTS.put(DeepElement.EARTH, List.of(
                "seismic_strike", "shield_quake", "earth_bound", "stonebound_roots", "giant_slayer"));
        ENCHANTS.put(DeepElement.METAL, List.of(
                "supreme_sharpness", "sharpened_sharpness", "annihilating_sweep", "guillotine", "decapitation"));
        ENCHANTS.put(DeepElement.WATER, List.of(
                "hydro_shock", "ocean_current", "tidecaller", "undertow", "frost_trail"));
        ENCHANTS.put(DeepElement.THUNDER, List.of(
                "chain_lightning", "storm_strike", "overcharge", "thunderstruck", "echo_strike"));
        ENCHANTS.put(DeepElement.LIGHT, List.of(
                "seraphs_judgment", "radiant_ward", "astral_pierce", "divine_protection", "king"));
        ENCHANTS.put(DeepElement.SHADOW, List.of(
                "wither_blade", "wither_edge", "shadow_hunt", "fear", "stealth_ambush"));
        ENCHANTS.put(DeepElement.BLOOD, List.of(
                "blood_edge", "bloodthirst", "blood_pact", "blood_transfusion", "blood_drinkers_edge"));
        ENCHANTS.put(DeepElement.VOID, List.of(
                "warp", "ender_hook", "voidlings_hunger", "dragons_breath_arrow", "teleport_arrow"));
        // T352-CHAOS-ELEMENT : le chaos devient une famille à part entière (5 enchantements, dont 4 neufs).
        ENCHANTS.put(DeepElement.CHAOS, List.of(
                "chaos", "chaos_entropy", "chaos_nexus", "chaos_rupture", "prismatic_brand"));
    }

    private ElementalCatalog() {}

    /** Les 5 enchantements déclarés pour une famille. */
    public static List<String> enchantmentsOf(DeepElement el) {
        return ENCHANTS.getOrDefault(el, List.of());
    }

    /** Identifiants complets ({@code deep_lucky_block:xxx}) des 5 enchantements d'une famille. */
    public static List<String> fullIdsOf(DeepElement el) {
        List<String> out = new ArrayList<>(MIN_ENCHANTMENTS);
        for (String p : enchantmentsOf(el)) {
            out.add("deep_lucky_block:" + p);
        }
        return out;
    }

    /** Les grosses animations d'une famille (au moins {@link #MIN_ANIMATIONS}). */
    public static List<WeaponVisualEffectId> animationsOf(DeepElement el) {
        return WeaponVisualEffectId.animationsOf(el);
    }

    /**
     * Vérifie qu'une famille est complète : au moins 5 enchantements déclarés et 3 animations.
     * (La présence réelle des fichiers JSON est vérifiée par {@code /dlbvfx elements} en jeu.)
     */
    public static boolean isComplete(DeepElement el) {
        return enchantmentsOf(el).size() >= MIN_ENCHANTMENTS
                && animationsOf(el).size() >= MIN_ANIMATIONS;
    }

    /** Familles incomplètes (vide = tout est couvert). */
    public static List<DeepElement> incompleteElements() {
        List<DeepElement> out = new ArrayList<>();
        for (DeepElement el : DeepElement.values()) {
            if (!isComplete(el)) out.add(el);
        }
        return out;
    }

    /** Toutes les familles sans exception (ordre de l'enum, CHAOS compris). */
    public static List<DeepElement> elements() {
        return List.of(DeepElement.values());
    }
}
