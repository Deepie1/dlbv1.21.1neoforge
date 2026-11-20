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
    /** Palier de glint imposé quand le skill est chargé (échelle 1..20 de {@code /glint}). */
    public static final int CHARGED_GLINT_TIER = 13;

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

    /**
     * Vrai si cet enchantement doit être retiré de la liste d'enchantements de l'infobulle
     * parce qu'il est entièrement décrit par l'encart SKILL.
     */
    public static boolean isSkillEnchantment(net.minecraft.resources.ResourceLocation id) {
        if (id == null || !"deep_lucky_block".equals(id.getNamespace())) return false;
        Entry e = BY_PATH.get(id.getPath());
        return e != null && e.ownsEnchant();
    }

    /** Toutes les compétences portées par cet objet, sans doublon, dans l'ordre de pose. */
    public static List<Entry> allOn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        java.util.LinkedHashMap<String, Entry> found = new java.util.LinkedHashMap<>(4);
        // 1. les signatures : on passe par la table d'enchantements complète, car une
        //    même animation est atteignable par plusieurs clés (thorn_maw, briar_cage…).
        // ... mais seulement si cet objet sait lancer une signature. La resolution de
        // couches est VISUELLE : un trident avec sincere_loyalty resout vers le meme
        // effet que la signature "warp". Sans ce test, l'encart annoncerait sur un
        // trident un clic droit sur un mob qui ne declenche rien, et le joueur
        // chercherait une touche inexistante.
        // T415-FIX-1 : on part des ENCHANTEMENTS DU MOD reellement portes, et non de
        // resolveLayers(). La resolution de couches accepte des alias VANILLA :
        // minecraft:mending et minecraft:unbreaking resolvent vers CHRONO_ETERNAL_HALO,
        // minecraft:silk_touch vers une autre couche. Une epee lootee portant Raccommodage
        // se voyait donc attribuer la competence « chrono_mending » qu'elle ne porte PAS,
        // en plus de sa vraie competence : c'est l'origine des competences vues par deux.
        // On garde forEnchantmentLocation pour conserver les alias du MOD (briar_cage,
        // thorn_maw... pointent vers la meme couche que leur signature canonique).
        if (canCastSignature(stack)) {
            for (net.minecraft.world.item.enchantment.Enchantment en
                    : deepluckyblock.util.DeepEnchant.map(stack).keySet()) {
                net.minecraft.resources.ResourceLocation loc =
                        deepluckyblock.util.DeepEnchant.getKeySafe(en);
                if (loc == null || !"deep_lucky_block".equals(loc.getNamespace())) continue;
                WeaponVisualEffectId fx = WeaponVisualEffectId.forEnchantmentLocation(loc);
                if (fx == null) continue;
                Entry e = BY_EFFECT.get(fx);
                if (e != null) found.putIfAbsent(e.enchantPath(), e);
            }
        }
        // 2. les capacités activées, lues par le MEME chemin que le filtre d'infobulle :
        //    DeepEnchant.map + getKeySafe. Deux lectures différentes pourraient diverger,
        //    et un enchantement retiré de la liste sans apparaître dans l'encart serait
        //    purement et simplement invisible.
        for (net.minecraft.world.item.enchantment.Enchantment en
                : deepluckyblock.util.DeepEnchant.map(stack).keySet()) {
            net.minecraft.resources.ResourceLocation loc =
                    deepluckyblock.util.DeepEnchant.getKeySafe(en);
            if (loc == null || !"deep_lucky_block".equals(loc.getNamespace())) continue;
            Entry e = BY_PATH.get(loc.getPath());
            if (e != null) found.putIfAbsent(e.enchantPath(), e);
        }
        return List.copyOf(found.values());
    }

    /**
     * Armes sur lesquelles une signature peut reellement se lancer. Le lanceur
     * ({@code WeaponSkillHandler}) et le tirage de butin lisent cette MEME methode : poser
     * une competence sur une pioche produirait un pouvoir que rien ne peut declencher.
     */
    public static boolean canCastSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
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
