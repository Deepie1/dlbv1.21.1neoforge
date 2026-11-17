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
    /**
     * T376 : neuvième famille, la foudre (arc en chaîne, marteau de Thor, appel de l'orage).
     * Correspond à {@link deepluckyblock.enchantments.DeepElement#THUNDER}, « Foudre ».
     */
    public static final String THUNDER = "thunder";
    /**
     * T377 : dixieme famille, l’ombre (allonge spectrale, faille du Fletrisseur, voile
     * d’eclipse). Correspond a {@link deepluckyblock.enchantments.DeepElement#SHADOW},
     * « Ombre », qui est aussi l’element de toutes les malediction
     * ({@code isCursePath}).
     */
    public static final String SHADOW = "shadow";
    /**
     * T379 : onzieme famille, le sang (couperet, fil qui goutte, siphon carmin, transfusion
     * vitale, carnage berserk). Correspond a {@link deepluckyblock.enchantments.DeepElement#BLOOD},
     * « Sang ». C'est la seule famille a compter CINQ signatures au lieu de trois.
     */
    public static final String BLOOD = "blood";
    /**
     * T388 : douzieme famille, le vide (sphere de distorsion, prisme du dragon, faille du
     * neant). Correspond a {@link deepluckyblock.enchantments.DeepElement#VOID}, « Vide ».
     *
     * <p>A ne pas confondre avec l'alias {@code void_shade} de {@link #SHADOW} : l'ombre est
     * une absence de LUMIERE, le vide une absence d'ESPACE. Les deux familles se touchent par
     * le mot, jamais par le geste — l'ombre marque et aveugle, le vide tire et souleve.</p>
     */
    public static final String VOID = "void";

    /** Ordre d'affichage de l'aide et de l'autocomplétion : un seul nom par famille, en anglais. */
    public static final String[] ALL =
            {FIRE, WATER, THUNDER, SHADOW, BLOOD, VOID, WIND, EARTH, METAL, LIGHT, POISON, WOOD};

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
            // foudre / thunder (l'électricité et l'orage sont la même famille, DeepElement.THUNDER)
            case "foudre", "thunder", "lightning", "eclair", "éclair", "volt", "electric",
                 "electrique", "électrique", "orage", "storm", "bolt" -> THUNDER;
            // ombre / shadow (les tenebres, les maledictions et le spectral sont la meme
            // famille, DeepElement.SHADOW — cf. isCursePath)
            case "ombre", "shadow", "tenebre", "tenebres", "ténèbre", "ténèbres",
                 "dark", "darkness", "noir", "nuit", "night", "spectre", "spectral",
                 "eclipse", "éclipse", "malediction", "malédiction", "curse",
                 "wither", "fletrissure", "flétrissure", "void_shade" -> SHADOW;
            // sang / blood (la saignee, le prelevement et la frenesie sont la meme famille,
            // DeepElement.BLOOD). « gore » et « carnage » sont admis parce que ce sont les
            // deux mots que les joueurs tapent en premier pour CARNAGE_BERSERK.
            case "sang", "blood", "sanguine", "crimson", "carmin", "gore", "carnage",
                 "hemorragie", "hémorragie", "saignee", "saignée", "bleed", "bleeding",
                 "vampire", "vampirique", "siphon", "guillotine", "couperet" -> BLOOD;
            // vide / void (la distorsion, le prisme du dragon et la dechirure sont la meme
            // famille, DeepElement.VOID). « end », « ender » et « dragon » sont admis parce
            // que le prisme EST le dragon de l'End : c'est le mot que le joueur tape.
            // Attention : « void_shade » reste un alias de l'OMBRE, et il est teste plus
            // haut — l'ordre des branches d'un switch sur chaines est l'ordre d'ecriture.
            case "vide", "void", "neant", "néant", "nothing", "null", "nul",
                 "abysse", "abyss", "abyssal", "singularite", "singularité", "singularity",
                 "trou", "trounoir", "blackhole", "black_hole", "horizon",
                 "end", "ender", "dragon", "prisme", "prism",
                 "faille", "fissure", "rift", "warp", "distorsion", "distortion" -> VOID;
            default -> null;
        };
    }

    /** Couleur de glint (0xRRGGBB) de la famille canonique ; blanc doré si inconnue. */
    public static int glintColor(String canonical) {
        if (canonical == null) return 0xFFF3B0;
        return switch (canonical) {
            // T387 : aligne sur la base du glint (DeepGlintColor.FIRE). L'ambre franc
            // est la seule teinte de la palette ou le canal vert depasse 120 ; c'est ce
            // canal, et non la teinte, qui separe le feu du sang d'un coup d'oeil.
            case FIRE -> 0xFF8210;
            case WIND -> 0x9FF2DC;
            case EARTH -> 0x9FC25E;
            case METAL -> 0xC4C8CD;
            case LIGHT -> 0xFFD75E;
            case POISON -> 0x46C24C;
            case WOOD -> 0xA9743C;      // DeepElement.WOOD.primary — écorce, pas feuillage
            case WATER -> 0x62C7E8;
            case THUNDER -> 0x5E3AFF;   // DeepElement.THUNDER.primary — violet électrique
            // DeepElement.SHADOW.accent, et NON sa primaire 0x4D318E : sur une lame, le glint
            // est un reflet, donc la valeur la plus claire de l’element. La primaire, plus
            // sombre, se confondait avec l’acier non eclaire.
            case SHADOW -> 0x7100A7;
            // DeepElement.BLOOD.highlight 0xFF0000 serait un rouge primaire pur, qui sur une
            // lame se confond avec les particules de degats. T387 : descendu de 0xE01822
            // vers l'ecarlate sombre, pour que le vert tombe a 8 la ou le feu est a 130.
            // La pulsation (DeepGlintColor.familyBreath) fait le reste.
            case BLOOD -> 0xCD082A;
            // T388 — POURQUOI PAS LE CYAN DE L'HORIZON (0x7FD4E8), qui est pourtant la
            // couleur que le joueur VOIT dans l'animation ? Parce que l'Eau tient deja
            // 0x62C7E8 : (98,199,232) contre (127,212,232), soit un ecart de 29, 13 et 0 sur
            // les trois canaux. La regle acquise en T386/T387 est qu'il faut un canal entier
            // d'ecart (>= 120) avec CHAQUE famille, sans quoi deux lames se confondent en
            // main. Le cyan echouait sur les trois.
            //
            // Restait a chercher dans la palette du Vide la teinte qui n'appartient a
            // personne : CHORUS 0x8E5BA6, « l'autre cote ». Montee a sa valeur de REFLET
            // (meme raisonnement que l'Ombre : sur une lame, un glint est un reflet, donc la
            // valeur la plus claire de l'element), elle donne ce magenta. Verifie un par un :
            // >= 120 d'ecart sur au moins un canal contre les onze autres familles, le plus
            // serre etant la Terre (131 sur le vert).
            case VOID -> 0xFF3FD8;
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
            case THUNDER -> "§d";  // violet clair : l'arc est violet, pas bleu comme l'eau
            case SHADOW -> "§5";  // violet fonce — doit rester distinct du §d de la Foudre
            case BLOOD -> "§4";   // rouge sombre — le §c est deja pris par le Feu
            // T388 : les seize codes hérités ne contiennent qu'un magenta (§d) et qu'un
            // violet (§5), et les deux sont pris — par la Foudre et par l'Ombre. Le Vide
            // prend donc §9, ce qui reste du cote froid de la roue et demeure lisible sur
            // le fond sombre d'une infobulle (§0 et §8 n'y sont plus qu'une tache).
            case VOID -> "§9";
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
            case THUNDER -> "Thunder";
            case SHADOW -> "Shadow";
            case BLOOD -> "Blood";
            case VOID -> "Void";
            default -> "Animation";
        };
    }
}
