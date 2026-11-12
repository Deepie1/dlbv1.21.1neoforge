package deepluckyblock.util;

import java.util.Locale;

/**
 * T374-ANIM-FAMILIES : source unique des familles d'animation des épées {@code /anim}.
 *
 * <p><b>Pourquoi ?</b> Les anciennes valeurs NBT ({@code feu}, {@code vent}, {@code terre},
 * {@code gaia}…) étaient dupliquées à la main dans trois fichiers (commande, gestionnaire client,
 * écouteur serveur) et mélangeaient français et anglais : la commande exposait par exemple
 * {@code terre} <i>et</i> {@code earth} pour la même famille, avec deux noms affichés différents.
 * Tout passe désormais par les noms <b>anglais canoniques</b> — {@code fire}, {@code wind},
 * {@code earth}, {@code metal}, {@code light}, {@code poison}, {@code wood}, {@code water} — et les
 * valeurs restent acceptées en lecture (les épées déjà données continuent de fonctionner), sans
 * jamais réapparaître comme doublon dans l'autocomplétion.</p>
 *
 * <p>Chaque famille porte aussi sa couleur de <b>glint</b> : l'épée de démonstration scintille
 * exactement de la teinte de son élément, via la clé NBT forcée lue par
 * {@link deepluckyblock.client.EnchantAura#GLINT_FORCE_NBT_KEY} (même mécanisme que
 * {@code /glint <couleur>}).</p>
 */
public final class AnimFamily {

    public static final String FIRE = "fire";
    public static final String WIND = "wind";
    public static final String EARTH = "earth";
    public static final String METAL = "metal";
    public static final String LIGHT = "light";
    public static final String POISON = "poison";
    /**
     * Bois. S'appelait {@code nature} jusqu'au 12 novembre 2026 : c'était un nom inventé, alors
     * que la famille correspond à {@link deepluckyblock.enchantments.DeepElement#WOOD}, « Bois ».
     * Les anciennes valeurs NBT {@code nature} et {@code gaia} restent lues.
     */
    public static final String WOOD = "wood";
    /** T375 : huitième famille, l'eau (raz-de-marée, maelström abyssal, givre du permafrost). */
    public static final String WATER = "water";

    /** Ordre d'affichage de l'aide et de l'autocomplétion : un seul nom par famille, en anglais. */
    public static final String[] ALL = {FIRE, WATER, WIND, EARTH, METAL, LIGHT, POISON, WOOD};

    private AnimFamily() { }

    /**
     * Normalise une valeur NBT ({@code DlbAnimFamily}) vers le nom canonique anglais.
     *
     * @return le nom canonique, ou {@code null} si la valeur n'est pas une famille connue
     */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        return switch (v) {
            // feu / fire
            case "feu", "fire", "flamme", "flame" -> FIRE;
            // vent / wind
            case "vent", "wind", "air", "tempete", "tempête" -> WIND;
            // terre / earth
            case "terre", "earth", "ground", "seisme", "séisme" -> EARTH;
            // métal / metal
            case "metal", "métal", "acier", "steel" -> METAL;
            // lumière / light
            case "light", "lumiere", "lumière", "sacre", "sacré", "holy" -> LIGHT;
            // poison / venin
            case "poison", "venin", "venom", "toxic" -> POISON;
            // bois / wood (anciennement exposé sous le nom inventé « nature »)
            case "gaia", "nature", "bois", "wood", "foret", "forêt", "tree" -> WOOD;
            // eau / water (le givre appartient à la même famille, comme dans DeepElement.WATER)
            case "eau", "water", "aqua", "glace", "ice", "givre", "frost" -> WATER;
            default -> null;
        };
    }

    /** Couleur de glint (0xRRGGBB) de la famille canonique ; blanc doré si inconnue. */
    public static int glintColor(String canonical) {
        if (canonical == null) return 0xFFF3B0;
        return switch (canonical) {
            case FIRE -> 0xFF6A2A;
            case WIND -> 0x9FF2DC;
            case EARTH -> 0x9FC25E;
            case METAL -> 0xC4C8CD;
            case LIGHT -> 0xFFD75E;
            case POISON -> 0x46C24C;
            case WOOD -> 0xA9743C;      // DeepElement.WOOD.primary — écorce, pas feuillage
            case WATER -> 0x62C7E8;
            default -> 0xFFF3B0;
        };
    }

    /** Code couleur Minecraft (§) du nom affiché, aligné sur la teinte du glint. */
    public static String colorCode(String canonical) {
        if (canonical == null) return "§f";
        return switch (canonical) {
            case FIRE -> "§c";
            case WIND -> "§b";
            case EARTH -> "§a";   // 0x9FC25E : la terre du mod est olive, pas dorée
            case METAL -> "§7";
            case LIGHT -> "§e";
            case POISON -> "§2";
            case WOOD -> "§6";    // écorce brun doré — doit rester distinct de la Terre
            case WATER -> "§3";
            default -> "§f";
        };
    }

    /** Titre anglais affiché sur l'épée et dans l'aide ({@code Fire}, {@code Wind}…). */
    public static String displayName(String canonical) {
        if (canonical == null) return "Animation";
        return switch (canonical) {
            case FIRE -> "Fire";
            case WIND -> "Wind";
            case EARTH -> "Earth";
            case METAL -> "Metal";
            case LIGHT -> "Light";
            case POISON -> "Poison";
            case WOOD -> "Wood";
            case WATER -> "Water";
            default -> "Animation";
        };
    }
}
