package deepluckyblock.enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T352-ELEMENT-COVERAGE (révision T352b) : <b>recomptage par catégorie</b> de la couverture
 * élémentaire, après séparation de <b>Bois (brun)</b> et <b>Poison (vert foncé)</b>.
 *
 * <p>Chaque famille possède ici au moins <b>5 enchantements dédiés</b> dont <b>au moins 1 à distance</b>
 * (arc / arbalète / trident), et au moins <b>3 grosses animations</b> uniques. La commande
 * {@code /dlbvfx elements} affiche ce tableau en jeu et vérifie la présence réelle de chaque
 * enchantement dans le registre du serveur.</p>
 */
public final class ElementalCatalog {

    /** Nombre minimum d'enchantements dédiés attendus par famille. */
    public static final int MIN_ENCHANTMENTS = 5;
    /** Nombre minimum de grosses animations attendues par famille. */
    public static final int MIN_ANIMATIONS = 3;
    /** Nombre minimum d'enchantements À DISTANCE attendus par famille (arc/arbalète/trident). */
    public static final int MIN_RANGED = 1;

    /** Type d'arme portant l'enchantement (pour le recomptage « par armes disponibles »). */
    public enum Kind {
        MELEE("mêlée"),
        BOW("arc/arbalète"),
        TRIDENT("trident"),
        ARMOR("armure"),
        TOOL("outil");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Un enchantement dédié à une famille, avec le type d'arme qui le porte. */
    public record Entry(String path, Kind kind) {
    }

    private static final Map<DeepElement, List<Entry>> ENCHANTS = new LinkedHashMap<>();

    private static void put(DeepElement el, Entry... entries) {
        ENCHANTS.put(el, List.of(entries));
    }

    private static Entry e(String path, Kind kind) {
        return new Entry(path, kind);
    }

    static {
        // ── 木 Bois — BRUN (écorce, planches, récolte) ──
        put(DeepElement.WOOD,
                e("lumberjack", Kind.MELEE), e("tree_felling", Kind.MELEE),
                e("replanting", Kind.MELEE), e("bountiful_harvest", Kind.TOOL),
                e("farmers_hoe", Kind.TOOL), e("timber_arrow", Kind.BOW));
        // ── 毒 Poison — VERT FONCÉ (venin, miasme, peste) ──
        put(DeepElement.POISON,
                e("poison_fang", Kind.MELEE), e("poison_scar", Kind.MELEE),
                e("poison_arrow", Kind.BOW), e("briar_arrow", Kind.BOW),
                e("miasma_arrow", Kind.BOW));
        // ── 火 Feu — rouge/orange ──
        put(DeepElement.FIRE,
                e("fiery_sweep", Kind.MELEE), e("meteoric_slam", Kind.MELEE),
                e("brimstone", Kind.MELEE), e("burning_heart", Kind.ARMOR),
                e("cinder_volley", Kind.BOW), e("blazing_arrow", Kind.BOW));
        // ── 土 Terre — VERT CLAIR + BRUN ──
        put(DeepElement.EARTH,
                e("seismic_strike", Kind.MELEE), e("shield_quake", Kind.MELEE),
                e("earth_bound", Kind.MELEE), e("stonebound_roots", Kind.MELEE),
                e("sandburst_shot", Kind.BOW));
        // ── 金 Métal — argent/or, tranchant ──
        put(DeepElement.METAL,
                e("supreme_sharpness", Kind.MELEE), e("sharpened_sharpness", Kind.MELEE),
                e("guillotine", Kind.MELEE), e("annihilating_sweep", Kind.MELEE),
                e("piercing_barb", Kind.BOW));
        // ── 水 Eau — bleu ──
        put(DeepElement.WATER,
                e("hydro_shock", Kind.MELEE), e("ocean_current", Kind.MELEE),
                e("undertow", Kind.MELEE), e("frost_trail", Kind.MELEE),
                e("riptide_barb", Kind.BOW), e("water_arrow", Kind.BOW));
        // ── 雷 Foudre — jaune électrique ──
        put(DeepElement.THUNDER,
                e("chain_lightning", Kind.MELEE), e("storm_strike", Kind.MELEE),
                e("overcharge", Kind.MELEE), e("thunderstruck", Kind.MELEE),
                e("voltaic_shot", Kind.BOW));
        // ── 光 Lumière — blanc doré ──
        put(DeepElement.LIGHT,
                e("seraphs_judgment", Kind.MELEE), e("radiant_ward", Kind.ARMOR),
                e("king", Kind.MELEE), e("astral_pierce", Kind.BOW),
                e("radiant_bolt", Kind.BOW));
        // ── 暗 Ombre — violet nuit ──
        put(DeepElement.SHADOW,
                e("wither_blade", Kind.MELEE), e("shadow_hunt", Kind.MELEE),
                e("fear", Kind.MELEE), e("stealth_ambush", Kind.MELEE),
                e("wraith_arrow", Kind.BOW));
        // ── Sang — cramoisi ──
        put(DeepElement.BLOOD,
                e("blood_edge", Kind.MELEE), e("bloodthirst", Kind.MELEE),
                e("blood_pact", Kind.MELEE), e("blood_transfusion", Kind.MELEE),
                e("bloodletting_bolt", Kind.BOW));
        // ── Vide — indigo ──
        put(DeepElement.VOID,
                e("warp", Kind.MELEE), e("ender_hook", Kind.MELEE),
                e("voidlings_hunger", Kind.MELEE), e("teleport_arrow", Kind.BOW),
                e("null_shot", Kind.BOW));
        // ── Chaos — sombre + multicolore ──
        put(DeepElement.CHAOS,
                e("chaos_entropy", Kind.MELEE), e("chaos_nexus", Kind.MELEE),
                e("chaos_rupture", Kind.MELEE), e("chaos", Kind.BOW),
                e("entropy_bolt", Kind.BOW));
    }

    private ElementalCatalog() {}

    public static List<Entry> entriesOf(DeepElement el) {
        return ENCHANTS.getOrDefault(el, List.of());
    }

    /** Chemins simples des enchantements dédiés. */
    public static List<String> enchantmentsOf(DeepElement el) {
        List<String> out = new ArrayList<>();
        for (Entry entry : entriesOf(el)) out.add(entry.path());
        return out;
    }

    /** Identifiants complets ({@code deep_lucky_block:xxx}). */
    public static List<String> fullIdsOf(DeepElement el) {
        List<String> out = new ArrayList<>();
        for (String p : enchantmentsOf(el)) out.add("deep_lucky_block:" + p);
        return out;
    }

    /** Nombre d'enchantements dédiés à distance (arc/arbalète + trident). */
    public static int rangedCount(DeepElement el) {
        int n = 0;
        for (Entry entry : entriesOf(el)) {
            if (entry.kind() == Kind.BOW || entry.kind() == Kind.TRIDENT) n++;
        }
        return n;
    }

    /** Nombre d'enchantements dédiés par type d'arme. */
    public static int countOf(DeepElement el, Kind kind) {
        int n = 0;
        for (Entry entry : entriesOf(el)) {
            if (entry.kind() == kind) n++;
        }
        return n;
    }

    /** Grosses animations d'une famille (au moins {@link #MIN_ANIMATIONS}). */
    public static List<WeaponVisualEffectId> animationsOf(DeepElement el) {
        return WeaponVisualEffectId.animationsOf(el);
    }

    /** Une famille est complète si elle a 5 ench. dédiés, ≥1 à distance et 3 animations. */
    public static boolean isComplete(DeepElement el) {
        return entriesOf(el).size() >= MIN_ENCHANTMENTS
                && rangedCount(el) >= MIN_RANGED
                && animationsOf(el).size() >= MIN_ANIMATIONS;
    }

    public static boolean isRangedComplete(DeepElement el) {
        return rangedCount(el) >= MIN_RANGED;
    }

    /** Familles incomplètes (vide = tout est couvert). */
    public static List<DeepElement> incompleteElements() {
        List<DeepElement> out = new ArrayList<>();
        for (DeepElement el : DeepElement.values()) {
            if (!isComplete(el)) out.add(el);
        }
        return out;
    }

    /** Familles sans couverture à distance. */
    public static List<DeepElement> elementsWithoutRanged() {
        List<DeepElement> out = new ArrayList<>();
        for (DeepElement el : DeepElement.values()) {
            if (!isRangedComplete(el)) out.add(el);
        }
        return out;
    }

    public static List<DeepElement> elements() {
        return List.of(DeepElement.values());
    }
}
