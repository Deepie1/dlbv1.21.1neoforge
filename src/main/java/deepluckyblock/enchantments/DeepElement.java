package deepluckyblock.enchantments;

import java.util.Locale;

/**
 * T350-ELEMENT-FAMILIES : familles élémentaires (système chinois Wu Xing étendu) qui pilotent
 * désormais la couleur, l'ordre d'affichage et le choix des animations d'enchantement.
 *
 * <p><b>Pourquoi ?</b> Les VFX d'arme étaient classés par « catégorie technique » (tranchant, critique,
 * sang, élément, distance, spécial) et joués dans un ordre hérité de la priorité : le résultat était un
 * mélange d'animations très différentes qui semblaient sortir « n'importe quand ». On trie maintenant
 * par <b>famille élémentaire</b>, ce qui donne une couleur et une ambiance cohérentes :
 * feu = rouge/orange, poison & nature = vert, eau & givre = bleu, foudre = jaune électrique,
 * métal = argent/or, terre = ocre, lumière = blanc doré, ombre = violet, sang = cramoisi, vide = indigo.</p>
 *
 * <p>Les cinq éléments chinois classiques sont présents (Bois, Feu, Terre, Métal, Eau), complétés par
 * Foudre, Lumière et Ombre (demandés explicitement), plus Sang et Vide qui existaient déjà dans le mod.</p>
 */
public enum DeepElement {

    /** 木 Bois / nature / récolte / poison & venin (vert). */
    WOOD("Bois", 0x34C759, 0x1E7A33, 0xA8FF7A),
    /** 火 Feu / lave / météore / braise (rouge-orangé). */
    FIRE("Feu", 0xFF4A10, 0xB01B00, 0xFFE08A),
    /** 土 Terre / roche / séisme / sable (ocre). */
    EARTH("Terre", 0xC97A2B, 0x7A3E10, 0xF0C888),
    /** 金 Métal / acier / or / tranchant (argent & or). */
    METAL("Métal", 0xE9F1FF, 0x7F93AE, 0xFFFFFF),
    /** 水 Eau / glace / océan / givre (bleu). */
    WATER("Eau", 0x1CC8FF, 0x0A4FA8, 0xA8F0FF),
    /** 雷 Foudre / éclair / surcadence (jaune électrique & cyan). */
    THUNDER("Foudre", 0xFFF45A, 0x2C7BD6, 0xFFFFFF),
    /** 光 Lumière / saint / astral / or radieux. */
    LIGHT("Lumière", 0xFFF3B0, 0xFFB01F, 0xFFFFFF),
    /** 暗 Ombre / spectre / effroi / wither (violet sombre). */
    SHADOW("Ombre", 0x7A34D0, 0x25073F, 0xC06BFF),
    /** Sang / vampirisme (cramoisi) — famille historique du mod. */
    BLOOD("Sang", 0xC8102A, 0x5C0410, 0xFF5A78),
    /** Vide / ender / dragon / warp (indigo-cyan). */
    VOID("Vide", 0x8A7CFF, 0x2A1B7A, 0x9EF8FF),
    /**
     * T352-CHAOS-ELEMENT : 乱 Chaos / entropie / discordance prismatique.
     * Base volontairement <b>très sombre</b> (noir violacé) pour que les effets chaos restent
     * « dark », avec un accent multicolore : les animations chaos font défiler la teinte (HSV)
     * sur toute la roue chromatique par-dessus cette base sombre.
     */
    CHAOS("Chaos", 0x2B0B38, 0x120616, 0xFF4FD8);

    public final String displayName;
    public final int primary;
    public final int secondary;
    public final int highlight;

    DeepElement(String displayName, int primary, int secondary, int highlight) {
        this.displayName = displayName;
        this.primary = primary;
        this.secondary = secondary;
        this.highlight = highlight;
    }

    /**
     * T350-CURSE-NO-FX : les malédictions ne produisent AUCUN glint ni particule custom.
     * Détection par mots-clés sur le chemin d'enregistrement (vanilla + enchantements du mod).
     */
    public static boolean isCursePath(String path) {
        if (path == null) return false;
        String s = path.toLowerCase(Locale.ROOT);
        return s.contains("curse") || s.contains("cursed") || s.contains("malediction")
                || s.contains("blemish") || s.contains("unlucky") || s.contains("calamity")
                || s.contains("penalty") || s.contains("handicap");
    }

    /**
     * Famille élémentaire déduite du chemin d'un enchantement (mêmes mots-clés que
     * {@code DeepGlintColor}, mais centralisés ici et exprimés en familles).
     */
    public static DeepElement fromEnchantPath(String path) {
        if (path == null) return METAL;
        String s = path.toLowerCase(Locale.ROOT);
        if (isCursePath(s)) return SHADOW;

        // Foudre
        if (s.contains("thunder") || s.contains("lightning") || s.contains("storm")
                || s.contains("channeling") || s.contains("electric") || s.contains("overclock")
                || s.contains("overcharge") || s.contains("sonic") || s.contains("echo")) {
            return THUNDER;
        }
        // Poison / venin / nature -> Bois (vert)
        if (s.contains("poison") || s.contains("venom") || s.contains("toxic")
                || s.contains("briar") || s.contains("fang") || s.contains("scorpion")
                || s.contains("nature") || s.contains("harvest") || s.contains("wood")
                || s.contains("lumber") || s.contains("tree") || s.contains("replant")
                || s.contains("farm") || s.contains("crop") || s.contains("vein")
                || s.contains("plague") || s.contains("peste")) {
            return WOOD;
        }
        // Feu
        if (s.contains("fire") || s.contains("flame") || s.contains("blaze")
                || s.contains("lava") || s.contains("magma") || s.contains("meteor")
                || s.contains("phoenix") || s.contains("scorch") || s.contains("ember")
                || s.contains("infernal") || s.contains("brimstone") || s.contains("molten")
                || s.contains("burning") || s.contains("soul_fire") || s.contains("volcan")) {
            return FIRE;
        }
        // Eau / glace
        if (s.contains("water") || s.contains("ocean") || s.contains("ice")
                || s.contains("frost") || s.contains("conduit") || s.contains("hydro")
                || s.contains("tide") || s.contains("undertow") || s.contains("snow")
                || s.contains("riptide") || s.contains("abyss") || s.contains("maelstrom")) {
            return WATER;
        }
        // Terre / roche / séisme
        if (s.contains("earth") || s.contains("seismic") || s.contains("tectonic")
                || s.contains("rock") || s.contains("stone") || s.contains("quake")
                || s.contains("sand") || s.contains("mountain") || s.contains("bulwark")) {
            return EARTH;
        }
        // Sang / vampirisme
        if (s.contains("blood") || s.contains("leech") || s.contains("vamp")
                || s.contains("devour") || s.contains("execute") || s.contains("behead")
                || s.contains("guillotine") || s.contains("carnage") || s.contains("siphon")) {
            return BLOOD;
        }
        // Ombre / spectre / effroi
        if (s.contains("shadow") || s.contains("phantom") || s.contains("veil")
                || s.contains("stealth") || s.contains("ghost") || s.contains("wraith")
                || s.contains("dark") || s.contains("dread") || s.contains("wither")
                || s.contains("soul") || s.contains("night")) {
            return SHADOW;
        }
        // T352-CHAOS-ELEMENT : chaos / entropie / prismatique / discorde (avant le Vide, sinon
        // « chaos » retombait dans la famille Vide).
        if (s.contains("chaos") || s.contains("entropy") || s.contains("prism")
                || s.contains("discord") || s.contains("anarch") || s.contains("kaleido")
                || s.contains("pandemon") || s.contains("mirage")) {
            return CHAOS;
        }
        // Vide / ender / dragon
        if (s.contains("void") || s.contains("ender") || s.contains("dragon")
                || s.contains("warp") || s.contains("portal") || s.contains("chaos")) {
            return VOID;
        }
        // Lumière / saint / astral
        if (s.contains("light") || s.contains("holy") || s.contains("seraph")
                || s.contains("astral") || s.contains("radiant") || s.contains("celestial")
                || s.contains("star") || s.contains("sun") || s.contains("halo")
                || s.contains("angel") || s.contains("king") || s.contains("divine")) {
            return LIGHT;
        }
        // Métal par défaut (tranchant, acier, or, critiques)
        return METAL;
    }

    /** Nombre de familles (masque de bits utilisable sur 16 bits). */
    public static final int COUNT = values().length;

    /** Masque de bits des familles (bit {@code ordinal()}). */
    public static final int MASK_ALL = (1 << COUNT) - 1;

    /** Couleur multicolore du chaos : teinte qui tourne sur toute la roue (base sombre conservée). */
    public static int chaosHue(float timeSeconds, float offset, int baseRgb) {
        int hsv = net.minecraft.util.Mth.hsvToRgb((timeSeconds * 0.33f + offset) % 1.0f, 0.85f, 1.0f);
        // 25 % seulement de la couleur vive : le chaos reste sombre, mais bariolé.
        int r = (int) (((baseRgb >> 16) & 0xFF) * 0.75f + ((hsv >> 16) & 0xFF) * 0.25f);
        int g = (int) (((baseRgb >> 8) & 0xFF) * 0.75f + ((hsv >> 8) & 0xFF) * 0.25f);
        int b = (int) ((baseRgb & 0xFF) * 0.75f + (hsv & 0xFF) * 0.25f);
        return (r << 16) | (g << 8) | b;
    }

    /** Couleur principale adoucie pour un rendu de pixel (mélange vers le blanc). */
    public int tint(int rgb, float amount) {
        float a = Math.max(0.0f, Math.min(1.0f, amount));
        int r = (int) (((rgb >> 16) & 0xFF) * (1.0f - a) + 255 * a);
        int g = (int) (((rgb >> 8) & 0xFF) * (1.0f - a) + 255 * a);
        int b = (int) ((rgb & 0xFF) * (1.0f - a) + 255 * a);
        return (r << 16) | (g << 8) | b;
    }
}
