package deepluckyblock.enchantments;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T421 : LE NOM PROPRE DE CHAQUE ANIMATION.
 *
 * <p><b>Le defaut corrige.</b> L'encart COMPETENCE affichait le nom de
 * l'ENCHANTEMENT porteur. Une arme montrait donc « Blood Transfusion III »
 * dans ses enchantements et « Blood Transfusion III » dans ses competences :
 * deux fois la meme ligne, et aucun moyen de savoir laquelle etait le pouvoir.
 * Meme chose pour « Sharp Edge III ».</p>
 *
 * <p>Or chaque animation porte deja son nom depuis sa creation : c'est son
 * identifiant d'effet. Cette table ne fait que le rendre lisible, en anglais et
 * en francais. Le nom du skill ne peut donc plus se confondre avec celui de
 * l'enchantement qui le porte.</p>
 *
 * <p><b>Les petites animations ont un nom, pas une promesse de puissance.</b>
 * Les deux accents de coup critique (l'etoile doree, la mouche) sont nommes eux
 * aussi, mais ils restent ce qu'ils sont : de petits effets, ni rares ni forts.</p>
 */
public final class SkillNames {

    private SkillNames() {}

    /** nom anglais, nom francais. */
    public record Name(String en, String fr) {}

    private static final Map<WeaponVisualEffectId, Name> NAMES = new LinkedHashMap<>(64);

    private static void n(WeaponVisualEffectId fx, String en, String fr) {
        NAMES.put(fx, new Name(en, fr));
    }

    static {
        // I. Tranchant, allonge, echo, execution
        n(WeaponVisualEffectId.RAZOR_GLEAM,          "Razor Gleam",         "Éclat du Rasoir");
        n(WeaponVisualEffectId.SUPREME_PRISM_EDGE,   "Supreme Prism Edge",  "Tranchant Prismatique");
        n(WeaponVisualEffectId.SPECTRAL_REACH,       "Spectral Reach",      "Allonge Spectrale");
        n(WeaponVisualEffectId.STORMCALL_JUDGMENT,   "Stormcall Judgment",  "Appel de l'Orage");
        n(WeaponVisualEffectId.GUILLOTINE_CLEAVE,    "Guillotine Cleave",   "Couperet de Guillotine");
        // II. Coups critiques — les deux petites animations
        n(WeaponVisualEffectId.GOLDEN_CRIT_STAR,     "Golden Crit Star",    "Étoile Critique Dorée");
        n(WeaponVisualEffectId.FOCUSED_BULLSEYE,     "Focused Bullseye",    "Mouche Focalisée");
        n(WeaponVisualEffectId.MJOLNIR_HAMMERFALL,   "Mjolnir Hammerfall",  "Marteau de Thor");
        // III. Sang
        n(WeaponVisualEffectId.DRIPPING_BLOOD_EDGE,  "Dripping Blood Edge", "Lame Saignante");
        n(WeaponVisualEffectId.CRIMSON_SIPHON,       "Crimson Siphon",      "Siphon Carmin");
        n(WeaponVisualEffectId.VITAL_TRANSFUSION,    "Vital Transfusion",   "Transfusion Vitale");
        n(WeaponVisualEffectId.CARNAGE_BERSERK,      "Carnage Berserk",     "Carnage Berserk");
        // IV. Elements, distance, grandioses
        n(WeaponVisualEffectId.MOLTEN_FIRE_WAVE,     "Molten Fire Wave",    "Vague de Magma");
        n(WeaponVisualEffectId.SOUL_FIRE_CRESCENT,   "Soul Fire Crescent",  "Croissant d'Âmes");
        n(WeaponVisualEffectId.BRIMSTONE_METEOR,     "Brimstone Meteor",    "Météore de Soufre");
        n(WeaponVisualEffectId.CHAIN_VOLT_ARC,       "Chain Volt Arc",      "Arc Fulgurant en Chaîne");
        n(WeaponVisualEffectId.HYDRO_TIDAL_SURGE,    "Hydro Tidal Surge",   "Raz-de-Marée");
        n(WeaponVisualEffectId.ABYSSAL_MAELSTROM,    "Abyssal Maelstrom",   "Maelström Abyssal");
        n(WeaponVisualEffectId.PERMAFROST_RIME,      "Permafrost Rime",     "Givre du Permafrost");
        n(WeaponVisualEffectId.VENOM_BRIAR_FANG,     "Venom Briar Fang",    "Croc de Ronce Venimeux");
        n(WeaponVisualEffectId.SEISMIC_TECTONIC,     "Seismic Tectonic",    "Faille Tectonique");
        n(WeaponVisualEffectId.ZEPHYR_WIND_LANCE,    "Zephyr Wind Lance",   "Lance de Zéphyr");
        n(WeaponVisualEffectId.SERAPH_SKY_PILLAR,    "Seraph Sky Pillar",   "Pilier du Séraphin");
        n(WeaponVisualEffectId.WITHER_SHADOW_RIFT,   "Wither Shadow Rift",  "Faille du Flétrisseur");
        n(WeaponVisualEffectId.ASTRAL_STAR_LANCE,    "Astral Star Lance",   "Lance Astrale");
        n(WeaponVisualEffectId.VOID_WARP_SPHERE,     "Void Warp Sphere",    "Sphère de Distorsion");
        n(WeaponVisualEffectId.DRAGON_CHAOS_PRISM,   "Dragon Chaos Prism",  "Prisme du Dragon");
        n(WeaponVisualEffectId.CHRONO_ETERNAL_HALO,  "Chrono Eternal Halo", "Halo Éternel");
        n(WeaponVisualEffectId.WARLORD_RALLY_SIGIL,  "Warlord Rally Sigil", "Sceau de Ralliement");
        n(WeaponVisualEffectId.ANNIHILATING_ARC,     "Annihilating Arc",    "Arc d'Annihilation");
        // VII. Troisiemes signatures et familles tardives
        n(WeaponVisualEffectId.THORN_MAW_ROOT,       "Thorn Maw",           "Mâchoire de Ronces");
        n(WeaponVisualEffectId.TITAN_GRASP,          "Titan Grasp",         "Poigne du Colosse");
        n(WeaponVisualEffectId.ECLIPSE_VEIL,         "Eclipse Veil",        "Voile d'Éclipse");
        n(WeaponVisualEffectId.NULL_RIFT_FISSURE,    "Null Rift",           "Faille du Néant");
        n(WeaponVisualEffectId.CHAOS_KALEIDO_LOOP,   "Kaleido Loop",        "Boucle Kaléidoscopique");
        n(WeaponVisualEffectId.ENTROPY_SPHERE,       "Entropy Sphere",      "Sphère d'Entropie");
        n(WeaponVisualEffectId.BIG_BANG_SCAR,        "Big Bang Scar",       "Déchirure du Big Bang");
        n(WeaponVisualEffectId.VERDANT_SAP_BLOOM,    "Verdant Sap Bloom",   "Sève Vive");
        n(WeaponVisualEffectId.MIASMA_SHROUD,        "Miasma Shroud",       "Voile de Miasme");
        n(WeaponVisualEffectId.PLAGUE_NOVA,          "Plague Nova",         "Nova Venimeuse");
        n(WeaponVisualEffectId.SANDSTORM_DUNE,       "Sandstorm Dune",      "Dune de Tempête");
        n(WeaponVisualEffectId.TEMPEST_EYE,          "Tempest Eye",         "Œil du Cyclone");
        n(WeaponVisualEffectId.WILDWOOD_AWAKENING,   "Wildwood Awakening",  "Éveil de la Forêt");
        n(WeaponVisualEffectId.HORIZON_GATE,         "Horizon Gate",        "Porte d'Horizon");
        n(WeaponVisualEffectId.CHAIN_COLLAPSE,       "Chain Collapse",      "Effondrement en Chaîne");
        n(WeaponVisualEffectId.FEIGENBAUM_CASCADE,   "Feigenbaum Cascade",  "Cascade de Feigenbaum");
        n(WeaponVisualEffectId.SANDPILE_CRITICAL,    "Sandpile Critical",   "Tas de Sable Critique");
    }

    /**
     * Les huit capacites activees n'ont pas d'animation : leur Entry porte
     * {@code effect == null}. Elles ont pourtant besoin d'un nom a elles, pour la
     * meme raison que les autres — sinon l'encart repete le nom de l'enchantement.
     */
    private static final Map<String, Name> BY_PATH_NAMES = new LinkedHashMap<>(16);

    private static void p(String path, String en, String fr) {
        BY_PATH_NAMES.put(path, new Name(en, fr));
    }

    static {
        p("summon_army",     "Iron Muster",      "Levée de Fer");
        p("army_summon",     "Warlord's Call",   "Appel du Seigneur de Guerre");
        p("ore_detector",    "Deep Survey",      "Sondage Profond");
        p("pulling_power",   "Tow Line",         "Câble de Traction");
        p("grappling_hook",  "Skyhook",          "Grappin Céleste");
        p("sincere_loyalty", "Homing Throw",     "Lancer Fidèle");
        p("rocket_blast",    "Booster Burst",    "Poussée de Fusée");
        p("slide",           "Ground Slide",     "Glissade au Sol");
    }

    /**
     * Nom propre d'une competence, animation ou capacite activee.
     *
     * <p>C'est le point d'entree unique de l'infobulle : il garantit qu'aucune
     * competence ne peut porter le nom de son enchantement.</p>
     */
    public static String ofSkill(WeaponVisualEffectId fx, String enchantPath, boolean french) {
        if (fx != null) return of(fx, french);
        Name n = (enchantPath == null) ? null : BY_PATH_NAMES.get(enchantPath);
        if (n != null) return french ? n.fr() : n.en();
        return (enchantPath == null) ? "" : titleCase(enchantPath.toUpperCase(java.util.Locale.ROOT));
    }

    /** Nombre de capacites activees nommees : lu par le banc de controle. */
    public static int namedActiveCount() {
        return BY_PATH_NAMES.size();
    }

    /**
     * Nom propre de l'animation, dans la langue du client.
     *
     * <p>Repli volontairement lisible plutot que vide : si une animation future
     * arrivait sans entree ici, l'encart afficherait son identifiant mis en forme
     * (« Nouvelle Animation ») au lieu d'une ligne muette.</p>
     */
    public static String of(WeaponVisualEffectId fx, boolean french) {
        if (fx == null) return "";
        Name n = NAMES.get(fx);
        if (n != null) return french ? n.fr() : n.en();
        return titleCase(fx.name());
    }

    /** Vrai si toutes les animations connues ont un nom : lu par le banc de controle. */
    public static int namedCount() {
        return NAMES.size();
    }

    private static String titleCase(String constant) {
        StringBuilder sb = new StringBuilder(constant.length());
        for (String part : constant.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
