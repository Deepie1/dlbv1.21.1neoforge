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

    /** Comment le joueur déclenche la compétence. */
    public enum Cast {
        /** Clic droit sur un mob, arme de mêlée en main : les 45 signatures. */
        ON_MOB,
        /** Clic droit, objet en main : les enchantements qui SONT une capacité activée. */
        IN_HAND;
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
    private static final java.util.Set<String> ACTIVE_ONLY =
            java.util.Set.of("army_summon", "summon_army", "ore_detector");

    private static void active(String path, int seconds, DeepElement element) {
        BY_PATH.put(path, new Entry(null, path, seconds, element, Cast.IN_HAND, true));
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

    /** Recharge du détecteur de minerais, en ticks. Lue par le handler qui la pose. */
    public static final int ORE_DETECTOR_COOLDOWN_TICKS = 300;

    static {
        // T411 — LES CAPACITES ACTIVEES QUI NE SONT PAS DES SIGNATURES.
        // Recharges RELEVEES dans le code, pas choisies : UniversalEnchantmentHandler
        // l. 4466 pose 360 ticks pour summon_army et 440 pour army_summon.
        // ore_detector n'en avait aucune — une capacité activée sans recharge se
        // spamme à chaque tick ; elle reçoit la sienne, posée dans le même handler.
        // army_summon est DEJA une signature (WARLORD_RALLY_SIGIL) : il n'est pas
        // réinscrit ici, son entrée de signature suffit. ACTIVE_ONLY le marque
        // quand même, pour qu'il quitte la liste d'enchantements.
        active("summon_army", 18, DeepElement.EARTH);
        active("ore_detector", ORE_DETECTOR_COOLDOWN_TICKS / 20, DeepElement.METAL);
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
        for (WeaponVisualEffectId.ResolvedLayer layer : WeaponVisualEffectId.resolveLayers(stack, 2)) {
            Entry e = BY_EFFECT.get(layer.effect());
            if (e != null) found.putIfAbsent(e.enchantPath(), e);
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
     * Compétence portée par cette arme, ou {@code null}. On reprend la résolution de couches
     * déjà utilisée par le rendu : même arme, même enchantement, même animation — le skill ne
     * peut pas diverger de ce que l’arme montre.
     */
    public static Entry firstOn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        for (WeaponVisualEffectId.ResolvedLayer layer : layers) {
            Entry e = BY_EFFECT.get(layer.effect());
            // effect() non nul par construction ici, mais la garde documente l'invariant :
            // seule une signature se lance sur un mob, pas une capacite activee.
            if (e != null && e.effect() != null && e.cast() == Cast.ON_MOB) return e;
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
