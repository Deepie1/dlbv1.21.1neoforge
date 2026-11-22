package deepluckyblock.enchantments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T410-SKILLS — LES QUARANTE-CINQ SIGNATURES DEVIENNENT DES COMPÉTENCES.
 *
 * <p>Jusqu’ici une grosse animation tombait au hasard : 5 % sur un coup normal, 76 % sur un
 * critique. Le joueur la subissait plus qu’il ne la déclenchait. Chaque signature est
 * désormais un SKILL : on le lance soi-même, par un clic droit sur un mob, arme de mêlée en
 * main, et il repart en recharge.</p>
 *
 * <p><b>Le cooldown n’est pas choisi à la main.</b> Il sort d’une formule unique appliquée
 * aux quarante-cinq signatures, à partir de deux grandeurs relevées dans le code :</p>
 * <pre>
 *   charge   = nombre d’événements de dégâts + durée de l’animation / 40 ticks
 *   cooldown = 30 s + 2 s par point de charge, plafonné à 90 s
 * </pre>
 * <p>Les événements de dégâts sont les appels à {@code hurtWithCapturedWeapon} de la
 * signature ; la durée est celle de sa chorégraphie. Une animation qui frappe souvent ET
 * longtemps coûte donc plus cher qu’un éclat bref. Le plancher de 30 s est imposé ; le
 * plafond de 90 s évite qu’une signature ne devienne inutilisable.</p>
 *
 * <p>La recharge vit dans le NBT de l’arme ({@value #READY_AT_KEY}, date de jeu à laquelle le
 * skill redevient disponible). Elle suit donc l’arme, survit à la déconnexion, et le client
 * la lit sans paquet supplémentaire — c’est ce qui permet au glint de changer tout seul.</p>
 */
public final class WeaponSkill {

    private WeaponSkill() {}

    /** Date de jeu (tick monde) à laquelle le skill de cette arme redevient lançable. */
    public static final String READY_AT_KEY = "dlbSkillReadyAt";
    /** Mémoire du glint d’origine, pour le rendre intact après usage. */
    public static final String GLINT_SAVED_KEY = "dlbSkillGlintSaved";
    /** Drapeau d’état du glint : 1 = glint de skill chargé posé, 0/absent = glint normal. */
    public static final String GLINT_ON_KEY = "dlbSkillGlintOn";
    /**
     * T421 : mémoire du <b>palier</b> d'origine, symétrique de {@link #GLINT_SAVED_KEY}.
     *
     * <p>Son absence était la vraie cause du « le halo ne change pas ». La pose sauvegardait
     * la couleur mais pas le palier ; le retrait restaurait donc la couleur et laissait le
     * palier à {@link #CHARGED_GLINT_TIER}. Dès la première compétence lancée, l'arme restait
     * au palier 13 <b>dans les deux états</b> : il ne subsistait qu'un écart de teinte, et sur
     * une arme dont le rang est déjà rouge, une compétence BLOOD prête ne se voyait plus du
     * tout. {@code -1} signifie « aucun palier forcé », à distinguer du palier 0.</p>
     */
    public static final String GLINT_SAVED_TIER_KEY = "dlbSkillGlintSavedTier";
    /** Palier de glint imposé quand le skill est chargé (échelle 1..20 de {@code /glint}). */
    public static final int CHARGED_GLINT_TIER = 13;

    /**
     * T429 — LA MARQUE QUI FAIT D'UN ENCHANTEMENT UNE COMPETENCE.
     *
     * <p><b>Le defaut corrige.</b> Le butin annonce une competence sur 5 % des armes de melee,
     * et en donnait a 100 % d'entre elles. La cause n'etait pas le tirage, qui est juste :
     * c'etait la definition meme de « porter une competence ». {@code resolve} demandait a
     * {@code WeaponVisualEffectId.forEnchantmentLocation} si l'enchantement correspond a une
     * animation — et cette table en reconnait 253, dont 24 enchantements vanilla. Sur une
     * epee, Solidite, Raccommodage ou Tranchant suffisaient donc a ouvrir le clic droit :
     * les pools d'epee sont reconnus a 100 % par cette table, du rang COMMON a ETERNAL.</p>
     *
     * <p>Une animation n'est pas un pouvoir. Desormais une signature n'est une competence que
     * si <b>le tirage a 5 % l'a deliberement posee</b> et a inscrit son chemin ici. Les
     * signatures ramassees dans le butin gardent exactement ce qu'elles ont toujours ete :
     * des animations au coup porte, lues par {@code resolveLayers}, que cette marque ne
     * touche jamais.</p>
     *
     * <p>Les huit capacites activees ({@code effect() == null}) echappent a la regle : elles
     * ne sont rien d'autre qu'un pouvoir, sans animation ni bonus passif. Les priver de
     * competence faute de marque les rendrait muettes.</p>
     */
    public static final String GRANTED_KEY = "dlbSkillGranted";

    /**
     * Inscrit sur l'arme la competence que le tirage vient de lui accorder.
     *
     * <p>Seul appelant legitime : le code qui POSE la competence, juste apres avoir ajoute
     * l'enchantement correspondant. Marquer un chemin que l'arme ne porte pas ne donne rien :
     * {@link #scan} croise toujours la marque avec les enchantements reellement presents.</p>
     */
    public static void markGranted(ItemStack stack, String enchantPath) {
        if (stack == null || stack.isEmpty() || enchantPath == null || enchantPath.isBlank()) return;
        try {
            net.minecraft.nbt.CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack).copy();
            tag.putString(GRANTED_KEY, enchantPath);
            deepluckyblock.util.DeepNbt.commit(stack, tag);
        } catch (Throwable ignored) {
            // NBT indisponible : l'arme repart sans competence, jamais avec une competence fantome.
        }
    }

    /** Chemin de la competence accordee a cette arme, ou {@code null} si le tirage ne l'a pas choisie. */
    public static String grantedPath(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        net.minecraft.nbt.CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        if (tag == null) return null;
        String chemin = tag.getString(GRANTED_KEY);
        return (chemin == null || chemin.isBlank()) ? null : chemin;
    }

    /**
     * Comment le joueur déclenche la compétence.
     *
     * <p>Chaque valeur décrit un geste qui existait <b>déjà</b> dans le mod. Aucune n'a été
     * inventée pour les besoins de l'encart : un enchantement qu'on range dans les
     * compétences garde exactement le geste qui le lançait avant, sinon l'encart
     * mentirait et le joueur chercherait une touche qui ne fait rien.</p>
     */
    public enum Cast {
        /** Clic droit sur un mob, arme de mêlée en main : les 45 signatures. */
        ON_MOB,
        /** Clic droit, objet en main. */
        IN_HAND,
        /** Clic droit avec la canne à pêche, ligne lancée : on ramène. */
        ROD_REEL,
        /** Clic droit <b>main vide</b>, trident lancé. */
        EMPTY_HAND,
        /** Clic droit sur une fusée, en vol d'Élytres. */
        ELYTRA_ROCKET,
        /** S'accroupir en plein sprint, au sol. */
        SNEAK_SPRINT;

        /** Clé de traduction de la phrase qui décrit ce geste. */
        public String triggerKey() {
            return "tooltip.deep_lucky_block.cast." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Une compétence.
     *
     * @param effect        l'animation jouée, ou {@code null} pour une capacité activée qui
     *                      n'a pas de signature visuelle (invocation d'armée, détecteur…)
     * @param element       l'élément, qui donne la couleur du titre et du halo
     * @param cast          comment on la lance
     * @param ownsEnchant   vrai quand l'enchantement n'est QUE cette compétence. Dans ce cas
     *                      il quitte la liste d'enchantements de l'infobulle et n'apparaît
     *                      plus que dans l'encart SKILL — sinon il serait écrit deux fois.
     */
    public record Entry(WeaponVisualEffectId effect, String enchantPath, int cooldownSeconds,
                        DeepElement element, Cast cast, boolean ownsEnchant) {
        public int cooldownTicks() { return cooldownSeconds * 20; }
    }

    private static final Map<WeaponVisualEffectId, Entry> BY_EFFECT = new LinkedHashMap<>(64);
    /** Index par chemin d'enchantement : sert à l'infobulle et au tirage de butin. */
    private static final Map<String, Entry> BY_PATH = new LinkedHashMap<>(64);

    /**
     * T425 — rang de declaration de chaque competence, pour departager deux competences
     * de MEME niveau sans jamais dependre de l'ordre de lecture.
     *
     * <p>{@code DeepEnchant.map} renvoie un {@code HashMap} : son ordre depend des adresses
     * memoire des objets {@code Enchantment} et change d'un lancement du jeu au suivant.
     * Arbitrer dessus ferait varier la competence retenue d'une partie a l'autre, couleur du
     * halo comprise. Ce rang-la, lui, ne bouge jamais.</p>
     */
    private static final Map<String, Integer> ORDER = new java.util.HashMap<>(64);

    private static void put(WeaponVisualEffectId fx, String path, int seconds) {
        Entry e = new Entry(fx, path, seconds, fx.element(), Cast.ON_MOB, ACTIVE_ONLY.contains(path));
        BY_EFFECT.put(fx, e);
        BY_PATH.put(path, e);
    }

    /**
     * Enchantements qui ne sont RIEN D'AUTRE qu'une capacité activée : pas de bonus passif,
     * juste un pouvoir qu'on déclenche et une recharge. Ce sont eux qui quittent la liste
     * d'enchantements pour l'encart SKILL.
     *
     * <p>Les quarante-cinq signatures, elles, RESTENT dans la liste : leur enchantement est
     * un vrai enchantement avec ses effets passifs — {@code advanced_sharpness} ajoute des
     * dégâts à chaque coup, et le masquer priverait le joueur d'une information qui n'a
     * rien à voir avec la compétence.</p>
     */
    private static final java.util.Set<String> ACTIVE_ONLY = java.util.Set.of(
            "army_summon", "summon_army", "ore_detector",
            "pulling_power", "grappling_hook", "sincere_loyalty", "rocket_blast",
            "slide");

    /**
     * Inscrit une capacité activée avec le geste qui la lançait déjà.
     *
     * @param seconds recharge réelle en secondes, ou <b>0</b> quand l'enchantement n'en a
     *                pas : l'encart n'affiche alors aucune recharge plutôt que d'en inventer
     */
    private static void active(String path, int seconds, DeepElement element, Cast cast) {
        BY_PATH.put(path, new Entry(null, path, seconds, element, cast, true));
    }

    static {
        put(WeaponVisualEffectId.GUILLOTINE_CLEAVE, "guillotine", 38);
        put(WeaponVisualEffectId.DRIPPING_BLOOD_EDGE, "blood_edge", 36);
        put(WeaponVisualEffectId.CRIMSON_SIPHON, "blood_drinkers_edge", 40);
        put(WeaponVisualEffectId.VITAL_TRANSFUSION, "blood_transfusion", 37);
        put(WeaponVisualEffectId.CARNAGE_BERSERK, "mass_slaughter", 39);
        put(WeaponVisualEffectId.CHAOS_KALEIDO_LOOP, "chaos_entropy", 38);
        put(WeaponVisualEffectId.ENTROPY_SPHERE, "chaos_nexus", 38);
        put(WeaponVisualEffectId.BIG_BANG_SCAR, "chaos_rupture", 40);
        put(WeaponVisualEffectId.FEIGENBAUM_CASCADE, "feigenbaum_cascade", 38);
        put(WeaponVisualEffectId.SANDPILE_CRITICAL, "sandpile_critical", 42);
        put(WeaponVisualEffectId.SEISMIC_TECTONIC, "seismic_strike", 36);
        put(WeaponVisualEffectId.TITAN_GRASP, "titan_grasp", 37);
        put(WeaponVisualEffectId.WARLORD_RALLY_SIGIL, "army_summon", 40);
        put(WeaponVisualEffectId.MOLTEN_FIRE_WAVE, "fiery_sweep", 35);
        put(WeaponVisualEffectId.SOUL_FIRE_CRESCENT, "blazing_soul_sweep", 36);
        put(WeaponVisualEffectId.BRIMSTONE_METEOR, "brimstone", 42);
        put(WeaponVisualEffectId.SERAPH_SKY_PILLAR, "seraphs_judgment", 34);
        put(WeaponVisualEffectId.ASTRAL_STAR_LANCE, "astral_pierce", 38);
        put(WeaponVisualEffectId.CHRONO_ETERNAL_HALO, "chrono_mending", 37);
        put(WeaponVisualEffectId.RAZOR_GLEAM, "sharp_edge", 32);
        put(WeaponVisualEffectId.SUPREME_PRISM_EDGE, "advanced_sharpness", 38);
        put(WeaponVisualEffectId.ANNIHILATING_ARC, "annihilating_sweep", 35);
        put(WeaponVisualEffectId.VENOM_BRIAR_FANG, "poison_fang", 36);
        put(WeaponVisualEffectId.MIASMA_SHROUD, "miasma_shroud", 60);
        put(WeaponVisualEffectId.PLAGUE_NOVA, "plague_nova", 36);
        put(WeaponVisualEffectId.SPECTRAL_REACH, "extension", 36);
        put(WeaponVisualEffectId.WITHER_SHADOW_RIFT, "wither_blade", 42);
        put(WeaponVisualEffectId.ECLIPSE_VEIL, "eclipse_veil", 56);
        put(WeaponVisualEffectId.CHAIN_VOLT_ARC, "thundercall_pact", 54);
        put(WeaponVisualEffectId.MJOLNIR_HAMMERFALL, "overclocked_crit", 42);
        put(WeaponVisualEffectId.STORMCALL_JUDGMENT, "echo_strike", 40);
        put(WeaponVisualEffectId.VOID_WARP_SPHERE, "warp", 38);
        put(WeaponVisualEffectId.DRAGON_CHAOS_PRISM, "dragons_breath_arrow", 40);
        put(WeaponVisualEffectId.NULL_RIFT_FISSURE, "null_rift", 38);
        put(WeaponVisualEffectId.HORIZON_GATE, "horizon_gate", 39);
        put(WeaponVisualEffectId.CHAIN_COLLAPSE, "chain_collapse", 40);
        put(WeaponVisualEffectId.HYDRO_TIDAL_SURGE, "hydro_shock", 35);
        put(WeaponVisualEffectId.ABYSSAL_MAELSTROM, "abyssal_undertow", 42);
        put(WeaponVisualEffectId.PERMAFROST_RIME, "heavy_slow_hit", 48);
        put(WeaponVisualEffectId.ZEPHYR_WIND_LANCE, "wind_lance", 38);
        put(WeaponVisualEffectId.SANDSTORM_DUNE, "sandstorm_dune", 37);
        put(WeaponVisualEffectId.TEMPEST_EYE, "tempest", 42);
        put(WeaponVisualEffectId.THORN_MAW_ROOT, "thorn_maw", 37);
        put(WeaponVisualEffectId.VERDANT_SAP_BLOOM, "verdant_sap_bloom", 37);
        put(WeaponVisualEffectId.WILDWOOD_AWAKENING, "wildwood_awakening", 35);
    }

    /**
     * Recharge de la glissade, en ticks. Declaree ici pour que l'infobulle et le
     * handler lisent le meme nombre ; c'est {@code UniversalEnchantmentHandler} qui
     * la pose sur le joueur.
     */
    public static final int SLIDE_COOLDOWN_TICKS = 44;

    /** Recharge du détecteur de minerais, en ticks. Lue par le handler qui la pose. */
    public static final int ORE_DETECTOR_COOLDOWN_TICKS = 300;

    static {
        // T413 — LES SEPT CAPACITES ACTIVEES DU MOD, AVEC LEUR GESTE D'ORIGINE.
        //
        // Toutes les recharges ci-dessous sont RELEVEES dans le code, aucune n'est
        // choisie : UniversalEnchantmentHandler pose 360 ticks pour summon_army et
        // 440 pour army_summon. Les quatre capacites de FishingTridentElytraHandler
        // n'ont pas de recharge du tout : on inscrit 0, et l'encart n'en affiche
        // aucune. Inventer une recharge pour faire joli serait un changement de jeu
        // que personne n'a demande.
        active("summon_army", 18, DeepElement.EARTH, Cast.IN_HAND);
        active("ore_detector", ORE_DETECTOR_COOLDOWN_TICKS / 20, DeepElement.METAL, Cast.IN_HAND);

        // army_summon RETROUVE SON SEUL DECLENCHEUR D'ORIGINE.
        // T410 lui avait ajoute un second geste (clic droit sur un mob, via la
        // signature WARLORD_RALLY_SIGIL) avec sa propre recharge, a cote du clic
        // droit en main qu'il avait deja. Un enchantement, un geste : on retire
        // celui qui avait ete ajoute en le sortant de la table des signatures,
        // et on le reinscrit avec sa vraie recharge de 440 ticks.
        Entry rally = BY_EFFECT.remove(WeaponVisualEffectId.WARLORD_RALLY_SIGIL);
        active("army_summon", 22, rally != null ? rally.element() : DeepElement.EARTH, Cast.IN_HAND);

        // Les quatre capacites de la canne, du trident et des Elytres. Elles vivent
        // dans FishingTridentElytraHandler et n'ont jamais eu de recharge.
        active("pulling_power", 0, DeepElement.WATER, Cast.ROD_REEL);
        active("grappling_hook", 0, DeepElement.WIND, Cast.ROD_REEL);
        active("sincere_loyalty", 0, DeepElement.LIGHT, Cast.EMPTY_HAND);
        active("rocket_blast", 0, DeepElement.FIRE, Cast.ELYTRA_ROCKET);

        // La glissade : s'accroupir en plein sprint, au sol. Sa recharge de 44 ticks
        // est posee par UniversalEnchantmentHandler (state.slideCooldownUntil), elle
        // n'est pas inventee ici non plus. 44 ticks font un peu plus de deux secondes :
        // l'encart arrondit a 2, la valeur du jeu ne bouge pas.
        active("slide", SLIDE_COOLDOWN_TICKS / 20, DeepElement.WIND, Cast.SNEAK_SPRINT);
    }

    static {
        // T425 — L'ORDRE DE DEPARTAGE, FIGE UNE FOIS LES DEUX TABLES REMPLIES.
        //
        // Il est calcule dans un TROISIEME bloc statique, et pas plus haut : un bloc
        // statique ne voit que ce qui a deja ete initialise au-dessus de lui, et
        // army_summon ne prend sa forme definitive que dans le bloc precedent (retire des
        // signatures, reinscrit comme capacite).
        //
        // BY_PATH est un LinkedHashMap : les 44 signatures y sont entrees d'abord, les
        // capacites ensuite. A niveau egal, une signature l'emporte donc sur une capacite
        // — c'est elle que le halo et le clic droit savent montrer.
        int rang = 0;
        for (String chemin : BY_PATH.keySet()) {
            ORDER.putIfAbsent(chemin, rang++);
        }
    }

    /**
     * Vrai si cet enchantement doit être retiré de la liste d'enchantements de l'infobulle
     * parce qu'il est entièrement décrit par l'encart SKILL.
     */
    public static boolean isSkillEnchantment(net.minecraft.resources.ResourceLocation id) {
        if (id == null || !"deep_lucky_block".equals(id.getNamespace())) return false;
        Entry e = BY_PATH.get(id.getPath());
        return e != null && e.ownsEnchant();
    }

    /**
     * Une competence LUE sur un objet : l'entree de la table, les enchantements reellement
     * portes qui y menent, et le plus haut niveau parmi eux.
     *
     * <p>Plusieurs enchantements peuvent mener a la meme competence : les alias du mod
     * designent une seule signature ({@code thorn_maw}, {@code briar_cage}...). Sans les
     * garder tous, le nettoyage retirerait un alias et la competence reviendrait par
     * l'autre, a chaque tick.</p>
     */
    public record Found(Entry entry, List<net.minecraft.resources.ResourceLocation> carried, int level) {}

    /**
     * La competence que cet enchantement porte sur CET objet, ou {@code null}. Les deux
     * lectures du mod sont reunies ici pour qu'elles ne puissent plus diverger :
     *
     * <ol>
     *   <li><b>signature</b> — seulement si l'objet sait en lancer une. On passe par la table
     *       d'animations pour conserver les alias du MOD, mais on part des enchantements
     *       REELLEMENT portes : {@code resolveLayers} accepte des alias vanilla
     *       (Raccommodage vers CHRONO_ETERNAL_HALO) et attribuait des competences que
     *       l'arme ne porte pas (T415) ;</li>
     *   <li><b>capacite activee</b> — {@code BY_PATH} avec {@code effect() == null}. Le test
     *       est indispensable : {@code put()} range AUSSI les 44 chemins de signature dans
     *       cette table, et sans lui une signature s'affichait sur n'importe quel objet
     *       (T423).</li>
     * </ol>
     */
    /**
     * T445 — UNE COMPETENCE NE SE RECONNAIT PLUS QU'A SON PROPRE ENCHANTEMENT.
     *
     * <p><b>Ce qui a ete retire.</b> Cette methode passait par
     * {@code WeaponVisualEffectId.forEnchantmentLocation}, c'est-a-dire par la TABLE
     * D'ALIAS : les 45 signatures y sont chacune declarees sous une dizaine de noms
     * d'enchantement, vanilla compris. N'importe lequel de ces alias produisait donc
     * l'entree de competence correspondante. {@code blaze_sanctuary} rendait
     * {@code SOUL_FIRE_CRESCENT}, {@code minecraft:looting} rendait le Sceau de
     * Commandement Martial, et ainsi de suite pour 229 chemins.</p>
     *
     * <p><b>La regle.</b> Un enchantement est un enchantement, une competence est une
     * competence, et aucun des deux ne mene a l'autre. Seul le chemin CANONIQUE d'une
     * signature — celui que {@code put()} declare, celui que le tirage de butin accorde
     * et inscrit dans {@code dlbSkillGranted} — la designe encore. Les alias ne sont plus
     * lus ici du tout : ils ne servent qu'aux armes a distance, dans
     * {@link WeaponVisualEffectId#resolveLayers}.</p>
     *
     * <p>Les capacites activees ({@code effect() == null} : {@code army_summon},
     * {@code ore_detector}, la glissade...) sont inchangees. Ce sont de vrais
     * enchantements avec un vrai geste, elles n'empruntent aucune animation de
     * competence.</p>
     */
    private static Entry resolve(net.minecraft.resources.ResourceLocation loc, boolean melee) {
        if (loc == null || !"deep_lucky_block".equals(loc.getNamespace())) return null;
        Entry parChemin = BY_PATH.get(loc.getPath());
        if (parChemin == null) return null;
        // Capacite activee : elle vit par son enchantement, c'est sa nature.
        if (parChemin.effect() == null) return parChemin;
        // Signature : uniquement son propre enchantement, et uniquement sur une arme de
        // melee. La marque dlbSkillGranted reste exigee juste apres, dans scan().
        return melee ? parChemin : null;
    }

    /**
     * T425 — LECTURE BRUTE : tout ce que cet objet porte comme competence, AVANT la regle
     * d'unicite.
     *
     * <p>Elle peut en rendre plusieurs, et c'est voulu : il faut les voir toutes pour
     * arbitrer laquelle reste, savoir lesquelles retirer, et les montrer dans
     * {@code /dlbskill check}. Le jeu, lui, ne lit jamais cette liste : il passe par
     * {@link #allOn(ItemStack)}, qui n'en rend qu'une.</p>
     */
    public static List<Found> scan(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        boolean melee = canCastSignature(stack);
        // T429 : la competence que le tirage a 5 % a accordee a CETTE arme. Hors d'elle, une
        // signature portee reste une animation de coup et n'ouvre aucun clic droit.
        final String accordee = grantedPath(stack);
        java.util.LinkedHashMap<String, Entry> entrees = new java.util.LinkedHashMap<>(4);
        java.util.LinkedHashMap<String, java.util.ArrayList<net.minecraft.resources.ResourceLocation>> portes =
                new java.util.LinkedHashMap<>(4);
        java.util.LinkedHashMap<String, Integer> niveaux = new java.util.LinkedHashMap<>(4);
        for (Map.Entry<net.minecraft.world.item.enchantment.Enchantment, Integer> tenu
                : deepluckyblock.util.DeepEnchant.map(stack).entrySet()) {
            net.minecraft.resources.ResourceLocation loc =
                    deepluckyblock.util.DeepEnchant.getKeySafe(tenu.getKey());
            Entry trouvee = resolve(loc, melee);
            if (trouvee == null) continue;
            String cle = trouvee.enchantPath();
            // Une signature non accordee n'est pas une competence : elle reste l'animation
            // que l'enchantement a toujours jouee au coup porte. Les capacites activees
            // (effect() == null) passent sans marque : elles n'ont rien d'autre a offrir.
            if (trouvee.effect() != null && !cle.equals(accordee)) continue;
            entrees.putIfAbsent(cle, trouvee);
            portes.computeIfAbsent(cle, k -> new java.util.ArrayList<>(2)).add(loc);
            niveaux.merge(cle, tenu.getValue() == null ? 0 : tenu.getValue(), Math::max);
        }
        java.util.ArrayList<Found> out = new java.util.ArrayList<>(entrees.size());
        for (Map.Entry<String, Entry> paire : entrees.entrySet()) {
            out.add(new Found(paire.getValue(), List.copyOf(portes.get(paire.getKey())),
                    niveaux.getOrDefault(paire.getKey(), 0)));
        }
        return List.copyOf(out);
    }

    /**
     * T425 — UNE SEULE COMPETENCE PAR OBJET.
     *
     * <p><b>Le defaut repare.</b> Un objet pouvait en porter deux : le butin faisait deux
     * tirages successifs, et surtout les 45 signatures sont de vrais enchantements du pool
     * commun, donc deux pouvaient tomber ensemble sur la meme arme sans aucun tirage de
     * competence. Or le lanceur n'en connait qu'une ({@link #firstOn}) et la recharge est
     * une clef NBT UNIQUE ({@value #READY_AT_KEY}) : lancer l'une mettait l'autre en
     * recharge. Deux competences annoncees, une seule jouable, la seconde comme annulee.</p>
     *
     * <p><b>Laquelle reste.</b> Le plus haut niveau d'enchantement ; a niveau egal, l'ordre
     * de declaration de la table ({@code ORDER}), ou les signatures passent avant les
     * capacites. Jamais l'ordre de lecture : il n'est pas stable d'une partie a l'autre.</p>
     *
     * <p>La liste renvoyee garde sa forme, pour que l'infobulle, le halo et le lanceur
     * continuent de lire la meme chose — elle ne contient simplement plus jamais deux
     * elements.</p>
     */
    public static List<Entry> allOn(ItemStack stack) {
        Entry gardee = retainedOn(stack);
        return gardee == null ? List.of() : List.of(gardee);
    }

    /** T425 : la competence retenue par la regle d'unicite, ou {@code null}. */
    public static Entry retainedOn(ItemStack stack) {
        Found meilleure = gardeeSur(stack);
        return meilleure == null ? null : meilleure.entry();
    }

    /**
     * T445 : la competence retenue AVEC son niveau, ou {@code null}.
     *
     * <p>Meme resolution que {@link #retainedOn}, au niveau pres. Elle existe pour que le
     * rendu des armes de melee ait une source unique : depuis T445, une arme de melee ne
     * montre que l'animation de la competence qu'on lui a accordee, et le niveau de cette
     * competence est ce qui en regle l'ampleur.</p>
     */
    public static Found retainedFoundOn(ItemStack stack) {
        return gardeeSur(stack);
    }

    /**
     * T470 — LA COMPETENCE ACCORDEE PASSE AVANT TOUT LE RESTE.
     *
     * <p>{@link #winner} arbitre au plus haut niveau. Cet arbitrage convient entre deux
     * signatures, mais pas face a une capacite activee : celles-ci passent sans marque et
     * portent souvent un niveau superieur. Une arme qui aurait recu {@code army_summon} du
     * pool commun ET la competence du tirage a 5 % aurait donc vu la capacite l'emporter —
     * et comme une capacite n'a pas d'animation ({@code effect() == null}), l'arme serait
     * redevenue muette : pas de calque, pas de clic droit, pas d'encart. La marque est le
     * titre, elle gagne.</p>
     */
    private static Found accordeeParmi(List<Found> trouvees, String accordee) {
        if (accordee == null || trouvees == null) return null;
        for (Found f : trouvees) {
            if (accordee.equals(f.entry().enchantPath())) return f;
        }
        return null;
    }

    /** La competence que cet objet fait valoir : la marque d'abord, l'arbitrage ensuite. */
    private static Found gardeeSur(ItemStack stack) {
        List<Found> trouvees = scan(stack);
        Found marquee = accordeeParmi(trouvees, grantedPath(stack));
        return marquee != null ? marquee : winner(trouvees);
    }

    /** La gagnante : plus haut niveau, puis ordre de declaration. Deterministe. */
    private static Found winner(List<Found> trouvees) {
        Found meilleure = null;
        int meilleurRang = Integer.MAX_VALUE;
        for (Found f : trouvees) {
            int rang = ORDER.getOrDefault(f.entry().enchantPath(), Integer.MAX_VALUE);
            if (meilleure == null || f.level() > meilleure.level()
                    || (f.level() == meilleure.level() && rang < meilleurRang)) {
                meilleure = f;
                meilleurRang = rang;
            }
        }
        return meilleure;
    }

    /**
     * T425 : les enchantements de competence EN TROP sur cet objet. Vide quand il n'en porte
     * qu'une, c'est-a-dire sur tous les objets normaux.
     */
    public static List<net.minecraft.resources.ResourceLocation> extraSkillEnchants(ItemStack stack) {
        List<Found> trouvees = scan(stack);
        if (trouvees.size() <= 1) return List.of();
        // T470 : la marque decide qui reste, sinon le menage retirerait la competence que
        // le tirage vient d'accorder au profit d'une capacite activee de plus haut niveau.
        Found marquee = accordeeParmi(trouvees, grantedPath(stack));
        Found gardee = marquee != null ? marquee : winner(trouvees);
        java.util.ArrayList<net.minecraft.resources.ResourceLocation> out = new java.util.ArrayList<>(4);
        for (Found f : trouvees) {
            if (f == gardee) continue;
            out.addAll(f.carried());
        }
        return List.copyOf(out);
    }

    /**
     * T425 — RETIRE LES COMPETENCES EN TROP. Renvoie vrai quand l'objet a change.
     *
     * <p>Destructif et assume : l'enchantement perdant quitte l'objet, bonus passif compris.
     * Le masquer sans le retirer laisserait sur l'arme un enchantement qui pese dans sa
     * puissance affichee sans plus rien faire.</p>
     *
     * <p><b>Serveur uniquement.</b> Modifier une pile cote client la ferait resynchroniser
     * au tick suivant sans rien corriger.</p>
     */
    public static boolean enforceSingleSkill(ItemStack stack) {
        List<net.minecraft.resources.ResourceLocation> enTrop = extraSkillEnchants(stack);
        if (enTrop.isEmpty()) return false;
        boolean change = false;
        for (net.minecraft.resources.ResourceLocation id : enTrop) {
            net.minecraft.world.item.enchantment.Enchantment en =
                    deepluckyblock.util.DeepEnchant.byId(id.toString());
            if (en == null) continue;
            if (deepluckyblock.util.DeepEnchant.getLevel(stack, en) <= 0) continue;
            deepluckyblock.util.DeepEnchant.remove(stack, en);
            // On ne declare un changement qu'une fois le retrait CONSTATE. Sans cette
            // relecture, un retrait qui echouerait (holder introuvable) ferait rediffuser
            // l'inventaire au client toutes les cinq ticks, indefiniment.
            if (deepluckyblock.util.DeepEnchant.getLevel(stack, en) <= 0) change = true;
        }
        return change;
    }

    /**
     * T425 — LES COMPETENCES QU'UN OBJET APPORTERAIT A UN AUTRE, pour l'enclume.
     *
     * <p>On lit les enchantements de {@code source} — y compris ceux STOCKES sur un livre —
     * mais on juge leur eligibilite sur {@code cible} : c'est elle qui decide si une
     * signature peut s'y lancer. Un livre n'est jamais une arme de melee ; se fier a lui
     * ferait croire qu'aucune signature n'est apportee.</p>
     */
    public static java.util.Set<String> skillPathsOffered(ItemStack source, ItemStack cible) {
        if (source == null || source.isEmpty()) return java.util.Set.of();
        boolean melee = canCastSignature(cible);
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(4);
        for (net.minecraft.world.item.enchantment.Enchantment en
                : deepluckyblock.util.DeepEnchant.mapWithStored(source).keySet()) {
            Entry e = resolve(deepluckyblock.util.DeepEnchant.getKeySafe(en), melee);
            if (e != null) out.add(e.enchantPath());
        }
        return java.util.Set.copyOf(out);
    }

    /**
     * T425 — variante liee a un objet de {@code isSkillEnchantment(id)}.
     *
     * <p>Une capacite activee quitte la liste d'enchantements de l'infobulle parce que
     * l'encart COMPETENCES la decrit entierement. Depuis que cet encart n'affiche qu'une
     * competence, la perdante ne doit plus etre masquee : elle serait invisible des deux
     * cotes tant que le nettoyage ne l'a pas retiree.</p>
     */
    public static boolean isSkillEnchantment(ItemStack stack, net.minecraft.resources.ResourceLocation id) {
        if (!isSkillEnchantment(id)) return false;
        if (stack == null || stack.isEmpty()) return true;
        Entry gardee = retainedOn(stack);
        return gardee != null && gardee.enchantPath().equals(id.getPath());
    }

    /**
     * Armes de melee sur lesquelles une signature peut se lancer. Le lanceur
     * ({@code WeaponSkillHandler}), le halo de disponibilite et le tirage de butin lisent
     * cette MEME methode : une seule definition, donc jamais de competence annoncee quelque
     * part et injouable ailleurs.
     *
     * <p><b>T424 — la pioche et la pelle en font partie.</b> Elles en etaient exclues, et
     * c'etait faux : dans ce mod, epee, hache, pioche, pelle, masse et houe sont toutes des
     * armes de melee. L'exclusion se voyait en jeu — une pioche portant {@code sharp_edge}
     * affichait « Eclat du Rasoir » sans halo ni clic qui fonctionne. Cette liste rejoint
     * donc {@link WeaponVisualEffectId#isWeaponItem}, qui acceptait deja pioche et pelle via
     * {@code TieredItem} : les deux definitions ne se contredisent plus.</p>
     *
     * <p>Le trident, l'arc et l'arbalete restent dehors : leur clic droit a deja un role
     * (lancer, bander) que la competence n'a pas a confisquer. Sur une pioche, une pelle ou
     * une houe, le clic droit <b>sur un mob</b> ne fait rien en vanilla — rien n'est perdu.</p>
     */
    public static boolean canCastSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                || stack.getItem() instanceof net.minecraft.world.item.PickaxeItem
                || stack.getItem() instanceof net.minecraft.world.item.ShovelItem
                || stack.getItem() instanceof net.minecraft.world.item.MaceItem
                || stack.getItem() instanceof net.minecraft.world.item.HoeItem;
    }

    /** Les chemins d'enchantement des 45 signatures, pour le tirage de butin. */
    public static List<String> signaturePaths() {
        return BY_EFFECT.values().stream().map(Entry::enchantPath).toList();
    }

    /**
     * T417 : les elements qui possedent AU MOINS une signature, dans l'ordre de la table.
     *
     * <p>Le butin tire « un skill d'element aleatoire » : il tire donc d'abord un element
     * ici, puis une signature dans {@link #signaturePathsOf(DeepElement)}. Tirer directement
     * parmi les 45 signatures ne reviendrait PAS au meme : les elements ne portent pas le
     * meme nombre de signatures, et les mieux fournis sortiraient bien plus souvent.</p>
     */
    public static List<DeepElement> elementsWithSignature() {
        java.util.LinkedHashSet<DeepElement> out = new java.util.LinkedHashSet<>(16);
        for (Entry e : BY_EFFECT.values()) {
            if (e.element() != null) out.add(e.element());
        }
        return List.copyOf(out);
    }

    /** T417 : les signatures d'un element donne, pour le tirage de butin. */
    public static List<String> signaturePathsOf(DeepElement element) {
        if (element == null) return List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>(8);
        for (Entry e : BY_EFFECT.values()) {
            if (e.element() == element) out.add(e.enchantPath());
        }
        return List.copyOf(out);
    }

    /**
     * Compétence portée par cette arme, ou {@code null}. On reprend la résolution de couches
     * déjà utilisée par le rendu : même arme, même enchantement, même animation — le skill ne
     * peut pas diverger de ce que l’arme montre.
     */
    public static Entry firstOn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        // T415-FIX-1 : MEME source que l'encart d'infobulle (allOn). Avant ce correctif,
        // firstOn relisait resolveLayers() directement et acceptait donc les alias vanilla :
        // une epee portant seulement Raccommodage etait traitee comme une arme a competence,
        // recevait le halo « prete » et lancait une signature au clic droit sur un mob.
        for (Entry e : allOn(stack)) {
            // effect() non nul par construction ici, mais la garde documente l'invariant :
            // seule une signature se lance sur un mob, pas une capacite activee.
            if (e.effect() != null && e.cast() == Cast.ON_MOB) return e;
        }
        return null;
    }

    /** Tick monde auquel le skill redevient disponible (0 = jamais utilisé). */
    public static long readyAt(ItemStack stack) {
        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        return tag == null ? 0L : tag.getLong(READY_AT_KEY);
    }

    /** Vrai si le skill est prêt à partir. C’est cette réponse que le glint traduit. */
    public static boolean isCharged(ItemStack stack, long gameTime) {
        return gameTime >= readyAt(stack);
    }

    /**
     * T418 : cette arme porte-t-elle une competence de cet element, actuellement PRETE ?
     *
     * <p>Le rendu des cubes du Vide et du Chaos pose exactement cette question. Elle vit ici,
     * et pas dans le renderer, pour une raison deja apprise a nos depens : le halo et les
     * cubes doivent s'allumer et s'eteindre <b>ensemble</b>. S'ils interrogeaient deux codes
     * differents — l'un les enchantements, l'autre la recharge — on verrait les cubes tourner
     * autour d'une arme encore en recharge, ou un halo « pret » sans cubes.</p>
     *
     * <p>Le client peut repondre : {@code dlbSkillReadyAt} voyage dans CUSTOM_DATA, et le
     * temps du monde est synchronise. Avant T415 cette donnee n'arrivait jamais a jour ;
     * c'est le meme correctif de synchronisation qui rend ce test fiable.</p>
     */
    public static boolean hasChargedSkillOfElement(ItemStack stack, DeepElement element, long gameTime) {
        if (stack == null || stack.isEmpty() || element == null) return false;
        for (Entry e : allOn(stack)) {
            if (e.element() == element) {
                return isCharged(stack, gameTime);
            }
        }
        return false;
    }

    /** Secondes restantes avant rechargement, arrondies au supérieur. */
    public static int remainingSeconds(ItemStack stack, long gameTime) {
        long left = readyAt(stack) - gameTime;
        return left <= 0L ? 0 : (int) ((left + 19L) / 20L);
    }

    /** Démarre la recharge après un lancement réussi. */
    public static void startCooldown(ItemStack stack, long gameTime, Entry entry) {
        if (stack == null || stack.isEmpty() || entry == null) return;
        CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack);
        tag.putLong(READY_AT_KEY, gameTime + entry.cooldownTicks());
        deepluckyblock.util.DeepNbt.commit(stack, tag);
    }
}
