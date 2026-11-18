package deepluckyblock.enchantments;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.DeepElement;
import deepluckyblock.enchantments.WeaponVfxSubtle;
import deepluckyblock.network.DeepPayloads;
import deepluckyblock.procedures.TestProcedure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T343-VOXEL-SERVER-SYNC : Diffusion serveur S2C 100 % fonctionnelle des 30 effets d'armes :
 * <ul>
 *   <li>Synchronise les sillages de mini-pixels 3D en direct sur chaque flèche, carreau et trident
 *       enchanté dès leur apparition ({@link EntityJoinLevelEvent}).</li>
 *   <li>Gère les salves multi-flèches ({@code fan_volley}, {@code scatter}, {@code multishot}),
 *       les coups à dégâts nuls ({@code blood_transfusion}, {@code curse_of_mercy}, {@code toy}),
 *       les critiques mêlée et distance, les gantelets à mains nues et les bannières clic-droit.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class WeaponVfxServerListener {

    // T346-VFX-TEST : kinds exposés publiquement pour la commande de test /dlbvfx
    public static final int KIND_SWING_SLASH       = 0;
    public static final int KIND_IMPACT            = 1;
    public static final int KIND_BEAM_SIPHON       = 2;
    public static final int KIND_PROJECTILE_ATTACH = 3;
    public static final int KIND_METEOR_TRACK = 4;
    public static final int KIND_MIASMA_DEFENSE = 5;
    public static final int KIND_PLAGUE_CONTAGION = 6;
    public static final int KIND_MIASMA_CHAIN = 7;
    /**
     * Un maillon RÉEL de la chaîne d'éclairs : « de ce point-là, vers ce corps-là ». Le client
     * ne devine plus où sauter, il reçoit la position de chaque victime au moment où elle est
     * touchée. C'est le seul « kind » dont la charge utile est un <b>segment</b> et non un point.
     */
    public static final int KIND_THUNDER_LINK = 8;

    private static final int MIASMA_HOST_TICKS = 120;
    private static final int MIASMA_SWAP_TICKS = 60;
    private static final int MIASMA_TRAVEL_TICKS = 20;
    private static final int MIASMA_MAX_TOTAL_TICKS = 600;
    private static final double MIASMA_DETECTION_RADIUS = 8.0;
    private static int NEXT_MIASMA_CHAIN_ID = 1;

    /** Une brume serveur unique, avec hôte courant, historique et génération annulant les anciennes tâches. */
    private static final class MiasmaChain {
        final int id;
        final int casterId;
        final int startedAt;
        final float pulseDamage;
        final Set<Integer> visited = new HashSet<>();
        LivingEntity host;
        int generation;
        int hostStartedAt;
        long shieldExpiry;
        double cloudX;
        double cloudY;
        double cloudZ;
        double travelFromX;
        double travelFromY;
        double travelFromZ;
        boolean traveling;
        boolean active = true;

        MiasmaChain(int id, int casterId, int startedAt, float pulseDamage) {
            this.id = id;
            this.casterId = casterId;
            this.startedAt = startedAt;
            this.pulseDamage = pulseDamage;
        }
    }

    private static final Map<Integer, Long> LAST_ATTACKER_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_PROJECTILE_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CRIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_RALLY_TICK = new ConcurrentHashMap<>();
    /** T351-BIG-BONUS : dernier tick où une GROSSE animation (critique) a été jouée, par attaquant. */
    private static final Map<Integer, Long> LAST_BIG_ANIM_TICK = new ConcurrentHashMap<>();
    /** T352-ELEMENT-COMBO : dernier tick d'un combo multi-éléments, par attaquant (anti-doublon). */
    private static final Map<Integer, Long> LAST_ELEMENT_COMBO_TICK = new ConcurrentHashMap<>();
    /** T349-VFX-FREQUENCY : cooldown réel et aléatoire par (attaquant, effet). */
    private static final Map<String, Long> EFFECT_COOLDOWN = new ConcurrentHashMap<>();
    /** Emprises Wood : immobilisation seule ou stun complet, expiration et état NoAi à restaurer. */
    private static final Map<Integer, MovementHold> MOVEMENT_HOLDS = new ConcurrentHashMap<>();
    /** Soul Fire : montée, maintien au centre de la lune, descente et restauration de l'IA. */
    private static final Map<Integer, SoulHold> SOUL_HOLDS = new ConcurrentHashMap<>();
    /** Etat NoAi vanilla avant la premiere emprise concurrente, restaure apres la derniere. */
    private static final Map<Integer, Boolean> SIGNATURE_BASELINE_NO_AI = new ConcurrentHashMap<>();
    /** Combustion Soul réelle : 80 ticks de flammes bleues et de dégâts périodiques. */
    private static final Map<Integer, SoulBurn> SOUL_BURNS = new ConcurrentHashMap<>();
    /** Porteurs du shroud : les hits reçus pendant cette fenêtre déclenchent sa réaction visuelle. */
    private static final Map<Integer, Long> MIASMA_SHIELDS = new ConcurrentHashMap<>();
    /** Poison de secours pour les cibles immunisées (notamment le Husk du dummy), jamais létal seul. */
    private static final Map<Integer, SignaturePoison> SIGNATURE_POISONS = new ConcurrentHashMap<>();
    private static final int SIGNATURE_FIRE_TICKS = 80;
    private static final int METEOR_AOE_FIRE_TICKS = 60;
    private static final int METEOR_IMPACT_TICKS = 30;
    private static final double METEOR_FIRE_RADIUS = 4.0;

    /**
     * Emprise de vent : le mob tourne autour de l'axe de la tornade en montant, puis est éjecté.
     *
     * <p>T374-WIND-WEAVE : {@code r1}, {@code spin}, {@code rise}, la durée de vol et {@code wobble}
     * sont tirés au sort <b>par mob</b>. Deux créatures happées au même instant ne suivent donc
     * jamais la même hélice : elles s'entremêlent, certaines filent vers l'œil (rayon &lt; 0,8),
     * d'autres restent sur la paroi, et elles n'atteignent pas le sommet en même temps.</p>
     */
    private record WindHold(long expires, long started, double cx, double cz, double baseY,
                            double r0, double r1, double rise, double phase, double spin,
                            float pulse, int period, int attackerId, double wobble, int release) { }

    private static final Map<Integer, WindHold> WIND_HOLDS = new ConcurrentHashMap<>();
    /**
     * T376-THUNDER — <b>CHARGE STATIQUE</b>, le debuff cumulable de la famille foudre.
     *
     * <p>Chaque frappe de foudre dépose une pile sur sa victime ({@link #STATIC_MAX} au plus).
     * Chaque pile majore de {@link #STATIC_PER_STACK} les dégâts de foudre qu'elle reçoit
     * ensuite ; à saturation elle <b>claque</b> toute seule. C'est le liant des trois
     * signatures : l'arc en chaîne pose les piles sur beaucoup de monde, l'appel de l'orage les
     * consomme sur une cible unique, le marteau en pose une par frappe de zone.</p>
     */
    private static final Map<Integer, StaticCharge> STATIC_CHARGES = new ConcurrentHashMap<>();
    /** Au-delà, la charge se décharge d'elle-même au lieu de s'accumuler. */
    private static final int STATIC_MAX = 5;
    /** Majoration des dégâts de foudre par pile accumulée. */
    private static final float STATIC_PER_STACK = 0.15f;
    /** Durée de vie d'une charge sans nouvelle frappe (ticks). */
    private static final int STATIC_DECAY = 140;
    /** Après une éjection, le mob est intouchable un court instant (sinon il est repris en vol). */
    private static final Map<Integer, Long> WIND_RECAPTURE = new ConcurrentHashMap<>();

    /**
     * T374-WIND-VORTEX : une <b>zone</b> de tornade, indépendante des victimes déjà attrapées.
     * Tant qu'elle vit, elle aspire en continu toute créature qui entre dans son cylindre : c'est
     * le fonctionnement des mods de tornade (aspiration zonale), là où la v6 ne traitait qu'une
     * liste figée au moment de l'impact — d'où l'impression qu'« aucun mob du rayon » n'était pris.
     */
    private record WindZone(ServerLevel level, long started, long expires, double cx, double cz,
                            double baseY, double radius, double rise, double spin, float pulse,
                            int period, int attackerId, int flightMin, int flightMax,
                            boolean blind, boolean converge, int release) { }

    /** Éjection classique de tornade : loin, à l'horizontale, direction aléatoire. */
    private static final int RELEASE_FLING = 0;
    /** Éjection de geyser (maelström) : très haut, presque à la verticale. */
    private static final int RELEASE_GEYSER = 1;

    private static final List<WindZone> WIND_ZONES = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Une seule passe d'aspiration par tick de jeu, quel que soit l'entité qui déclenche le hook. */
    private static long LAST_WIND_ZONE_TICK = Long.MIN_VALUE;

    // --------------------------------------------------------------------------------------------
    // T375-WATER-MOTION : mouvement des victimes de l'eau
    // --------------------------------------------------------------------------------------------

    /**
     * <b>Surf du raz-de-marée.</b> Première version : douze impulsions espacées de quatre ticks.
     * Entre deux impulsions le mob retombait au sol et reprenait son IA — il « sautait » douze fois
     * au lieu d'être <i>porté</i>, et comme la poussée était la même pour tous ils avançaient en
     * rang d'oignon.
     *
     * <p>Désormais, un mob touché par le front est inscrit ici et <b>poussé à chaque tick</b> par
     * {@link #tickSurgeRide}, avec des paramètres <b>tirés au sort pour lui</b> : vitesse
     * d'entraînement, portance, dérive latérale, phase de ballottement, durée de transport. Deux
     * mobs happés par la même passe ne suivent donc pas la même trajectoire — ils se croisent, se
     * doublent, et ne sont pas projetés au même instant.</p>
     *
     * <p>Le verrou est <b>mou</b> : on coupe l'IA ({@code setNoAi}) pour que le mob ne puisse pas
     * nager à contre-courant, mais on ne le téléporte pas — il garde sa physique, bute contre les
     * murs et roule. C'est la différence volontaire avec l'emprise du maelström, qui est un verrou
     * <b>dur</b> (position imposée par {@code teleportTo} sur une hélice).</p>
     */
    private record SurgeRide(long expires, long started, double dirX, double dirZ,
                             double speed, double lift, double drift, double bob,
                             double roll, float pulse, int attackerId, int period) { }

    private static final Map<Integer, SurgeRide> SURGE_RIDES = new ConcurrentHashMap<>();
    /** Après avoir été jeté par la vague, un mob n'est pas réembarqué par la passe suivante. */
    private static final Map<Integer, Long> SURGE_RECAPTURE = new ConcurrentHashMap<>();

    /**
     * <b>Prise du permafrost.</b> Compteur d'exposition : rester dans la couronne de pics ne doit
     * pas seulement ralentir, cela doit finir par <b>figer</b>. Au troisième passage de froid, le
     * mob est immobilisé jusqu'à l'éclatement — il ne peut plus sortir de la zone avant la rupture.
     */
    private static final Map<Integer, Integer> FROST_EXPOSURE = new ConcurrentHashMap<>();

    private record MovementHold(long expires, double x, double y, double z, boolean previousNoAi,
                                  boolean fullStun, double releaseY, long started, double burialDepth) { }
    /** Piles de charge statique sur une créature, avec leur instant de péremption. */
    private record StaticCharge(int stacks, long expires) { }
    private record SoulHold(long expires, long riseStart, long riseEnd, long descendStart,
                            double x, double baseY, double targetY, double z, boolean previousNoAi,
                            int attackerId, float weaponDamage) { }
    private record SoulBurn(long expires, long nextDamage, int attackerId,
                            float weaponDamage, int period) { }
    private record SignaturePoison(long expires, long nextDamage, int casterId) { }
    /** Empêche les dégâts différés d'une signature de relancer une nouvelle salve d'attaque. */
    private static final Set<Integer> INTERNAL_SIGNATURE_DAMAGE = new HashSet<>();

    /** T351 : attaque NORMALE -> 5 % de chance de déclencher l'animation pixel. */
    private static final int NORMAL_TRIGGER_PERCENT = 5;
    /** T351 : coup CRITIQUE -> 66 % de chance ; T359-CRIT-RATE : +10 points -> 76 %. */
    private static final int CRIT_TRIGGER_PERCENT = 76;
    /** T351 : part des déclenchements qui jouent un accent discret. */
    private static final int SUBTLE_SHARE_PERCENT = 90;
    /** Ancien bonus du pipeline projectile/critique manuel, conservé hors de la nouvelle mêlée. */
    private static final float BIG_ANIM_DAMAGE_BONUS = 1.05f;
    /** Mêlée éligible : un seul jet 0..99 = normal 0..76, slash 77..96, grande forme 97..99. */
    private static final int MELEE_NORMAL_MAX_ROLL = 76;
    private static final int MELEE_SLASH_MAX_ROLL = 96;
    private static final float MELEE_SLASH_DAMAGE_MULTIPLIER = 1.05f;
    /** Toute grande forme reste réellement agrandie, même sur une très petite cible. */
    private static final float BIG_VISUAL_BASE_SCALE = 1.20f;
    private static final float BIG_VISUAL_MEASURED_FACTOR = 0.55f;
    /**
     * T352-ELEMENT-COMBO : sur une arme portant PLUSIEURS éléments, 6 % de chance de jouer en
     * simultané une grosse animation de CHAQUE famille présente (une couleur par élément).
     */
    private static final int ELEMENT_COMBO_PERCENT = 6;
    /** Garde-fou : au-delà de 6 éléments, on se limite aux 6 plus prioritaires (anti-spam réseau). */
    private static final int ELEMENT_COMBO_MAX = 6;

    private static final WeaponVisualEffectId[] ANIM_FIRE = {
            WeaponVisualEffectId.MOLTEN_FIRE_WAVE, WeaponVisualEffectId.SOUL_FIRE_CRESCENT,
            WeaponVisualEffectId.BRIMSTONE_METEOR
    };
    /**
     * Bois. ZEPHYR_WIND_LANCE a été retiré de cette rotation : c'est la lance de VENT, elle
     * appartient à {@link #ANIM_WIND}. Sa présence ici est ce qui faisait jouer une animation
     * d'air pâle — lue comme « de l'eau » — quand on demandait du bois.
     */
    private static final WeaponVisualEffectId[] ANIM_WOOD = {
            WeaponVisualEffectId.THORN_MAW_ROOT, WeaponVisualEffectId.VERDANT_SAP_BLOOM,
            WeaponVisualEffectId.WILDWOOD_AWAKENING
    };
    private static final WeaponVisualEffectId[] ANIM_POISON = {
            WeaponVisualEffectId.VENOM_BRIAR_FANG, WeaponVisualEffectId.MIASMA_SHROUD,
            WeaponVisualEffectId.PLAGUE_NOVA
    };
    private static final WeaponVisualEffectId[] ANIM_EARTH = {
            WeaponVisualEffectId.SEISMIC_TECTONIC, WeaponVisualEffectId.TITAN_GRASP,
            WeaponVisualEffectId.WARLORD_RALLY_SIGIL
    };
    private static final WeaponVisualEffectId[] ANIM_METAL = {
            WeaponVisualEffectId.RAZOR_GLEAM, WeaponVisualEffectId.SUPREME_PRISM_EDGE,
            WeaponVisualEffectId.ANNIHILATING_ARC
    };
    /** Famille VENT : les trois signatures de vent tournent sur la même épée. */
    private static final WeaponVisualEffectId[] ANIM_WIND = {
            WeaponVisualEffectId.ZEPHYR_WIND_LANCE, WeaponVisualEffectId.SANDSTORM_DUNE,
            WeaponVisualEffectId.TEMPEST_EYE
    };
    /** Famille EAU (T375) : raz-de-marée, maelström abyssal et givre du permafrost. */
    private static final WeaponVisualEffectId[] ANIM_WATER = {
            WeaponVisualEffectId.HYDRO_TIDAL_SURGE, WeaponVisualEffectId.ABYSSAL_MAELSTROM,
            WeaponVisualEffectId.PERMAFROST_RIME
    };
    /**
     * Famille FOUDRE (T376) : arc fulgurant en chaîne, marteau de Thor, appel de l'orage.
     */
    private static final WeaponVisualEffectId[] ANIM_THUNDER = {
            WeaponVisualEffectId.CHAIN_VOLT_ARC, WeaponVisualEffectId.MJOLNIR_HAMMERFALL,
            WeaponVisualEffectId.STORMCALL_JUDGMENT
    };
    /**
     * Famille OMBRE (T377) : allonge spectrale, faille du Flétrisseur, voile d'éclipse.
     */
    private static final WeaponVisualEffectId[] ANIM_SHADOW = {
            WeaponVisualEffectId.SPECTRAL_REACH, WeaponVisualEffectId.WITHER_SHADOW_RIFT,
            WeaponVisualEffectId.ECLIPSE_VEIL
    };
    /**
     * T379-BLOOD : la seule famille a CINQ signatures. Le compteur NBT {@code DlbAnimNext}
     * tourne sur la longueur du tableau, donc rien d'autre n'a besoin d'etre adapte : les
     * cinq titres sortent dans l'ordre, sans reroll ni repetition.
     */
    private static final WeaponVisualEffectId[] ANIM_BLOOD = {
            WeaponVisualEffectId.GUILLOTINE_CLEAVE, WeaponVisualEffectId.DRIPPING_BLOOD_EDGE,
            WeaponVisualEffectId.CRIMSON_SIPHON, WeaponVisualEffectId.VITAL_TRANSFUSION,
            WeaponVisualEffectId.CARNAGE_BERSERK
    };
    /**
     * T388-VOID puis T389 : douzieme famille, portee a CINQ titres comme le sang. La sphere
     * rassemble, le prisme distribue, la faille trie, la porte EXPULSE, la chaine PROPAGE.
     *
     * <p>Les cinq verbes sont distincts a dessein : deux signatures qui deplacent de la meme
     * maniere ne sont qu'une seule signature jouee deux fois.</p>
     */
    private static final WeaponVisualEffectId[] ANIM_VOID = {
            WeaponVisualEffectId.VOID_WARP_SPHERE, WeaponVisualEffectId.DRAGON_CHAOS_PRISM,
            WeaponVisualEffectId.NULL_RIFT_FISSURE, WeaponVisualEffectId.HORIZON_GATE,
            WeaponVisualEffectId.CHAIN_COLLAPSE
    };
    private static final WeaponVisualEffectId[] ANIM_CHAOS = {
            WeaponVisualEffectId.CHAOS_KALEIDO_LOOP, WeaponVisualEffectId.ENTROPY_SPHERE,
            WeaponVisualEffectId.BIG_BANG_SCAR
    };
    private static final WeaponVisualEffectId[] ANIM_LIGHT = {
            WeaponVisualEffectId.SERAPH_SKY_PILLAR, WeaponVisualEffectId.ASTRAL_STAR_LANCE,
            WeaponVisualEffectId.CHRONO_ETERNAL_HALO
    };

    private WeaponVfxServerListener() {}

    /**
     * T359-BIG-ANIM-SCALE : échelle des grosses animations d'après le VOLUME du mob touché.
     *
     * <p>Référence = joueur / zombie (0,6 × 0,6 × 1,8) -> 1.0. Le facteur suit la racine du
     * rapport de volume, borné entre <b>0.30</b> (poulet, poisson, silverfish : animation
     * minuscule) et <b>4.0</b> (ravager, wither, dragon : animation énorme).</p>
     */
    /** Valeur de repli pour les déclencheurs de démonstration hors LivingIncomingDamageEvent. */
    private static float estimatedWeaponDamage(LivingEntity attacker) {
        if (attacker == null) return 0.0f;
        var attribute = attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return attribute == null ? 1.0f : Math.max(0.0f, (float) attribute.getValue());
    }

    /**
     * Base de dégâts d'une signature : <b>exactement celle du coup qui l'a lancée,
     * enchantements compris</b>.
     *
     * <p><b>Le défaut corrigé ici.</b> Toutes les familles calculaient leur base avec
     * {@link #estimatedWeaponDamage}, c'est-à-dire l'attribut {@code ATTACK_DAMAGE} seul.
     * Cet attribut contient bien l'arme tenue et la Force, mais <b>pas</b> les enchantements
     * de dégâts : depuis la 1.21 Tranchant, Châtiment et Fléau des arthropodes ne sont plus
     * des modificateurs d'attribut, ils sont appliqués au moment du coup par
     * {@code EnchantmentHelper.modifyDamage}. Une épée en diamant Tranchant V lançait donc
     * des signatures calibrées sur 8 dégâts alors que son coup normal en fait 11 : les
     * animations étaient systématiquement plus faibles que l'arme qui les déclenche, et
     * enchanter son arme n'avait aucun effet sur elles.</p>
     *
     * <p><b>Pourquoi la cible compte.</b> Châtiment et Fléau ne donnent leur bonus que contre
     * certaines familles de mobs : la base est donc calculée <i>contre le corps qui a reçu le
     * coup</i>, celui qui a déclenché la signature. Les victimes secondaires d'un effet de
     * zone héritent de cette base. C'est volontaire et c'est le comportement le plus lisible :
     * la signature est la conséquence d'<i>un</i> coup porté sur <i>un</i> corps, et son
     * ampleur est fixée à cet instant — pas recalculée mob par mob trois secondes plus tard,
     * alors que le joueur a peut-être déjà rangé son épée.</p>
     *
     * <p>L'arme lue est celle tenue à l'instant du déclenchement, et la valeur obtenue est
     * transportée dans les tâches programmées : changer d'arme pendant l'animation ne change
     * plus rien, dans un sens comme dans l'autre.</p>
     */
    private static float signatureBaseDamage(LivingEntity attacker, LivingEntity target) {
        float base = estimatedWeaponDamage(attacker);
        if (attacker == null || target == null) return base;
        if (!(attacker.level() instanceof ServerLevel sl)) return base;
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return base;
        DamageSource source = attacker instanceof Player player
                ? target.damageSources().playerAttack(player)
                : target.damageSources().mobAttack(attacker);
        float enchanted = net.minecraft.world.item.enchantment.EnchantmentHelper
                .modifyDamage(sl, weapon, target, source, base);
        return Math.max(0.0f, Float.isFinite(enchanted) ? enchanted : base);
    }

    /**
     * Dégât normal Minecraft : armure, résistance, enchantements de protection et attributs de
     * défense restent appliqués. Seules les i-frames sont réarmées pour permettre le rythme demandé
     * de 6/7 ticks. Le marqueur évite toute récursion dans le pipeline VFX de l'attaquant.
     */
    private static boolean hurtWithCapturedWeapon(ServerLevel sl, int attackerId,
                                                   LivingEntity target, float amount) {
        if (!target.isAlive() || target.level() != sl || !(amount > 0.0f)) return false;
        LivingEntity attacker = sl.getEntity(attackerId) instanceof LivingEntity living ? living : null;
        DamageSource source = attacker instanceof Player player
                ? target.damageSources().playerAttack(player)
                : attacker != null ? target.damageSources().mobAttack(attacker)
                : target.damageSources().generic();
        // T385-RECUL : un tic de signature n'est pas un coup d'epee. target.hurt()
        // applique le recul vanilla, TOUJOURS dans la direction attaquant -> victime :
        // les onze taillades du Carnage, les six bols du Siphon et surtout l'hemorragie
        // qui bat sans arret poussaient la cible toujours dans le meme sens, et elle
        // sortait de sa propre animation en glissant. On lui rend sa vitesse.
        // Les reculs VOULUS (onde du Carnage, projection du Couperet) sont poses APRES
        // l'appel et ne sont donc pas touches.
        Vec3 momentum = target.getDeltaMovement();
        INTERNAL_SIGNATURE_DAMAGE.add(target.getId());
        try {
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(source, amount);
            if (hurt && target.isAlive()) {
                target.setDeltaMovement(momentum);
                target.hurtMarked = false;
            }
            return hurt;
        } finally {
            INTERNAL_SIGNATURE_DAMAGE.remove(target.getId());
        }
    }

    /** Les pulses Poison ne doivent pas masquer le tick vanilla de poison qui suit quelques ticks après. */
    private static boolean hurtPoisonPulse(ServerLevel sl, int attackerId,
                                           LivingEntity target, float amount) {
        boolean hurt = hurtWithCapturedWeapon(sl, attackerId, target, amount);
        if (hurt && target.isAlive()) target.invulnerableTime = 0;
        return hurt;
    }

    private static boolean signatureContact(LivingEntity entity, double x, double y, double z,
                                            double radius) {
        return entity.getBoundingBox().inflate(radius).contains(x, y, z);
    }

    private static float sizeScaleOf(Entity entity) {
        if (entity == null) return 1.0f;
        double width = Math.max(0.05, entity.getBbWidth());
        double height = Math.max(0.05, entity.getBbHeight());
        double volume = width * width * height;
        double reference = 0.6 * 0.6 * 1.8;                 // joueur / zombie standard
        float scale = (float) Math.sqrt(volume / reference);
        return net.minecraft.util.Mth.clamp(scale, 0.30f, 4.0f);
    }

    /** Largeur contractuelle de Séraphin : plus grande dimension horizontale de la hitbox body. */
    private static float seraphBeamWidthOf(Entity entity) {
        if (entity == null) return 2.40f;
        AABB body = entity.getBoundingBox();
        float width = (float) Math.max(body.maxX - body.minX, body.maxZ - body.minZ);
        return net.minecraft.util.Mth.clamp(width, 0.20f, 6.0f);
    }

    private static float lightVisualScaleOf(WeaponVisualEffectId fx, Entity target, float fallback) {
        return fx == WeaponVisualEffectId.SERAPH_SKY_PILLAR ? seraphBeamWidthOf(target) : fallback;
    }

    /**
     * Classe l'échelle mesurée dans l'intervalle existant 0.30..4.0, puis la convertit en bonus
     * entier de 5 à 15 %. Aucun second tirage : une cible plus grande donne toujours un bonus
     * supérieur ou égal, et le même résultat pilote la taille de la grande animation.
     */
    private static int bigMeleeBonusPercent(float measuredScale) {
        float rank = (measuredScale - 0.30f) / (4.0f - 0.30f);
        return 5 + Math.round(net.minecraft.util.Mth.clamp(rank, 0.0f, 1.0f) * 10.0f);
    }

    /** Échelle visuelle monotone 1.365..3.40 : toujours une GRANDE animation, jamais une miniature. */
    private static float bigMeleeVisualScale(float measuredScale) {
        return BIG_VISUAL_BASE_SCALE + BIG_VISUAL_MEASURED_FACTOR * measuredScale;
    }

    /** Échelle exacte d'une grosse frappe de mêlée sur cette cible, pour {@code /dummytest}. */
    public static float debugBigMeleeScale(Entity target) {
        return bigMeleeVisualScale(sizeScaleOf(target));
    }

    /** Marque le coup critique du tick ; l'animation correspondante est choisie une seule fois dans onLivingHurtVisuals. */
    public static void markMeleeCrit(Player attacker) {
        if (attacker == null || !(attacker.level() instanceof ServerLevel sl)) return;
        LAST_CRIT_TICK.put(attacker.getId(), sl.getGameTime());
    }

    /** Démonstration explicite d'un effet critique (la mécanique normale passe par markMeleeCrit). */
    public static void notifyMeleeCrit(Player attacker, Entity target,
                                       WeaponVisualEffectId critStyle, int intensity) {
        if (attacker == null || target == null) return;
        LivingEntity livingTarget = target instanceof LivingEntity living ? living : null;
        if (!(attacker.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        LAST_CRIT_TICK.put(attacker.getId(), now);

        WeaponVisualEffectId fx = (critStyle != null) ? critStyle : WeaponVisualEffectId.GOLDEN_CRIT_STAR;

        // T351/T359 : sur un coup critique, 76 % de chance de déclencher l'animation pixel.
        if (sl.random.nextInt(100) >= CRIT_TRIGGER_PERCENT) return;
        String critKey = "crit:" + attacker.getId() + ":" + fx.id;
        Long critReady = EFFECT_COOLDOWN.get(critKey);
        if (critReady != null && now < critReady) return;
        EFFECT_COOLDOWN.put(critKey, now + 16L + sl.random.nextInt(20));

        float cx = (float) target.getX();
        float cy = (float) (target.getY() + target.getBbHeight() * 0.55);
        float cz = (float) target.getZ();

        // T351 : la plupart des critiques jouent seulement un accent discret (bien plus subtil).
        // Ne lance pas le combo multicolore avant ce choix : sinon un accent discret pouvait quand
        // même être accompagné de plusieurs grosses animations élémentaires simultanées.
        if (playSubtleAccent(sl, attacker.getMainHandItem(), fx, cx, cy, cz,
                (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ())) {
            return;
        }

        // T352-ELEMENT-COMBO : combo possible uniquement quand le tirage a choisi une vraie grosse anim.
        if (livingTarget != null) {
            tryElementCombo(sl, attacker.getId(), attacker.getMainHandItem(), cx, cy, cz,
                    (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ(),
                    sizeScaleOf(target), livingTarget, signatureBaseDamage(attacker, livingTarget));
        }

        // Grosse animation jouée -> le dégât de ce coup sera amplifié de 5 % (voir onLivingHurtVisuals).
        LAST_BIG_ANIM_TICK.put(attacker.getId(), now);
        // LA FAILLE EST LA SEULE SIGNATURE QUI NE SE JOUE PAS SUR LE MOB. Elle s'ouvre
        // derrière lui et souffle à travers : son point de référence dépend donc du groupe
        // visé, pas du seul corps touché. On le recule ici, avant l'envoi, pour que l'image
        // et le cône de dégâts partent exactement du même endroit.
        if (fx == WeaponVisualEffectId.WITHER_SHADOW_RIFT && livingTarget != null) {
            double[] dir = towardWielder(attacker, cx, cz);
            double[] anchor = riftAnchor(sl, cx, cy, cz, dir[0], dir[1], attacker.getId());
            cx = (float) anchor[0];
            cz = (float) anchor[1];
        }
        broadcastNear(sl, cx, cy, cz, fx.id, KIND_IMPACT, Math.max(2, intensity),
                cx, cy, cz, (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ(),
                paletteOf(attacker.getMainHandItem(), fx), lightVisualScaleOf(fx, target, sizeScaleOf(target)));
        if (livingTarget != null) {
            applyFireGameplay(attacker, livingTarget, fx, sizeScaleOf(target));
            applyWoodGameplay(attacker, livingTarget, fx, sizeScaleOf(target),
                    signatureBaseDamage(attacker, livingTarget));
            applyEarthGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyMetalGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyLightGameplay(attacker, livingTarget, fx,
                    signatureBaseDamage(attacker, livingTarget), sizeScaleOf(target));
            applyPoisonGameplay(attacker, livingTarget, fx);
            applyWaterGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyThunderGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyShadowGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyBloodGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
            applyVoidGameplay(attacker, livingTarget, fx, signatureBaseDamage(attacker, livingTarget));
        }
    }

    /** Appelé lors d'un rebond d'éclair en chaîne entre deux entités. */
    public static void notifyChainArc(ServerLevel sl, Entity from, Entity to, int level) {
        if (sl == null || from == null || to == null) return;
        float fx = (float) from.getX();
        float fy = (float) (from.getY() + from.getBbHeight() * 0.5);
        float fz = (float) from.getZ();
        float tx = (float) to.getX();
        float ty = (float) (to.getY() + to.getBbHeight() * 0.5);
        float tz = (float) to.getZ();
        broadcastNear(sl, tx, ty, tz,
                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_BEAM_SIPHON, Math.max(1, level),
                fx, fy, fz, tx, ty, tz);
    }

    /**
     * Attache automatiquement un sillage de mini-pixels 3D en temps réel à toute flèche, carreau
     * ou trident tiré avec une arme enchantée !
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile proj)) return;
        if (!(proj instanceof AbstractArrow)) return;

        // Planifié à J+1 tick afin que ClientboundAddEntityPacket et le snapshot d'arme soient déjà prêts
        TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 1, () -> {
            if (!proj.isAlive()) return;
            if (!(proj.getOwner() instanceof LivingEntity shooter)) return;

            ItemStack weapon = ItemStack.EMPTY;
            if (proj instanceof ThrownTrident trident) {
                weapon = trident.getPickupItemStackOrigin();
            } else if (proj instanceof AbstractArrow arrow) {
                weapon = UniversalEnchantmentHandler.resolveArrowWeapon(arrow, shooter);
            }
            if (weapon.isEmpty()) {
                weapon = shooter.getMainHandItem();
            }
            List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 1);
            if (layers.isEmpty()) return;

            WeaponVisualEffectId.ResolvedLayer top = layers.get(0);
            broadcastNear(sl, (float) proj.getX(), (float) proj.getY(), (float) proj.getZ(),
                    top.effect().id, KIND_PROJECTILE_ATTACH, top.level(),
                    (float) proj.getX(), (float) proj.getY(), (float) proj.getZ(),
                    (float) proj.getId(), 0.0f, 0.0f);
        });
    }

    /**
     * T349-VFX-FREQUENCY : les effets d'attaque partaient à CHAQUE coup (et se répétaient). On applique
     * maintenant un vrai cooldown aléatoire par effet (26..59 ticks) + un % de chance de déclenchement.
     * Les effets critiques gardent leur chance naturelle (ils sont déjà rares) mais restent throttlés.
     */
    /**
     * T351-SUBTLE : joue un accent discret (2..9 mini-pixels) au lieu de la grosse animation.
     * Retourne {@code true} si un accent a effectivement été envoyé.
     */
    private static boolean playSubtleAccent(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx,
                                            float tx, float ty, float tz,
                                            float ax, float ay, float az) {
        if (sl.random.nextInt(100) >= SUBTLE_SHARE_PERCENT) return false;
        int accentIndex = pickAccentIndex(sl, weapon, fx);
        int palette = paletteOf(weapon, fx);
        // Direction de frappe : de l'attaquant vers la cible, prolongée au-delà de la cible.
        float dx = tx - ax, dy = ty - ay, dz = tz - az;
        float ex = tx + dx * 0.35f;
        float ey = ty + dy * 0.35f;
        float ez = tz + dz * 0.35f;
        broadcastNear(sl, tx, ty, tz, 0, WeaponVfxSubtle.NET_KIND_BASE + accentIndex, 1,
                tx, ty, tz, ex, ey, ez, palette);
        return true;
    }

    /**
     * T351-SUBTLE-ECHO : choisit l'accent discret à jouer.
     *
     * <p>Une fois sur deux on prend l'<b>écho de la grosse animation</b> de l'arme (même arc, même
     * colonne, même éclair... en minuscule) : le joueur reconnaît la signature de son enchantement
     * même quand la grande forme ne part pas. L'autre moitié pioche dans les accents élémentaires,
     * pour éviter toute répétition.</p>
     */
    private static int pickAccentIndex(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx) {
        if (fx != null && sl.random.nextInt(100) < 50) {
            List<WeaponVfxSubtleEcho.Echo> pool = WeaponVfxSubtleEcho.forEffect(fx.id);
            if (!pool.isEmpty()) {
                return WeaponVfxSubtle.COUNT + pool.get(sl.random.nextInt(pool.size())).index();
            }
        }
        DeepElement el = pickElement(sl, weapon, fx);
        return WeaponVfxSubtle.pick(el, sl.random.nextLong()).index();
    }

    /**
     * T352-ELEMENT-COMBO : joue, en même temps, la signature de chaque famille élémentaire portée par
     * l'arme (6 % de chance, armes multi-éléments uniquement). Chaque animation part avec un masque
     * réduit à SA famille : les couleurs restent donc pures et bien distinctes (feu rouge, eau bleue,
     * chaos bariolé...), au lieu d'être moyennées.
     *
     * @return vrai si le combo élémentaire a été déclenché
     */
    private static boolean tryElementCombo(ServerLevel sl, int attackerId, ItemStack weapon,
                                           float tx, float ty, float tz,
                                           float ax, float ay, float az, LivingEntity target) {
        LivingEntity attacker = sl.getEntity(attackerId) instanceof LivingEntity living ? living : null;
        return tryElementCombo(sl, attackerId, weapon, tx, ty, tz, ax, ay, az, 1.0f, target,
                signatureBaseDamage(attacker, target));
    }

    /** T359-BIG-ANIM-SCALE : variante qui applique l'échelle de la cible à chacune des signatures. */
    private static boolean tryElementCombo(ServerLevel sl, int attackerId, ItemStack weapon,
                                           float tx, float ty, float tz,
                                           float ax, float ay, float az, float sizeScale,
                                           LivingEntity target, float weaponDamage) {
        // Un seul combo par coup : le critique et le handler d'impact peuvent viser le même tick.
        long now = sl.getGameTime();
        Long last = LAST_ELEMENT_COMBO_TICK.get(attackerId);
        if (last != null && now - last <= 1L) return false;
        if (sl.random.nextInt(100) >= ELEMENT_COMBO_PERCENT) return false;
        List<DeepElement> els = WeaponVisualEffectId.elementsOf(weapon);
        if (els.size() < 2) return false;
        LAST_ELEMENT_COMBO_TICK.put(attackerId, now);
        if (LAST_ELEMENT_COMBO_TICK.size() > 512) LAST_ELEMENT_COMBO_TICK.clear();

        int played = 0;
        for (DeepElement el : els) {
            if (played >= ELEMENT_COMBO_MAX) break;
            if (el == null) continue;
            WeaponVisualEffectId sig = WeaponVisualEffectId.signatureFor(el);
            if (sig == null) continue;
            broadcastNear(sl, tx, ty, tz, sig.id, KIND_IMPACT, 3,
                    tx, ty, tz, ax, ay, az, 1 << el.ordinal(), lightVisualScaleOf(sig, target, sizeScale));
            LivingEntity comboAttacker = sl.getEntity(attackerId) instanceof LivingEntity living ? living : null;
            applyFireGameplay(comboAttacker, target, sig, sizeScale, weaponDamage);
            applyWoodGameplay(comboAttacker, target, sig, sizeScale, weaponDamage);
            applyEarthGameplay(comboAttacker, target, sig, weaponDamage);
            applyMetalGameplay(comboAttacker, target, sig, weaponDamage);
            applyLightGameplay(comboAttacker, target, sig, weaponDamage, sizeScale);
            applyPoisonGameplay(comboAttacker, target, sig);
            applyWaterGameplay(comboAttacker, target, sig, weaponDamage);
            applyThunderGameplay(comboAttacker, target, sig, weaponDamage);
            applyShadowGameplay(comboAttacker, target, sig, weaponDamage);
            applyBloodGameplay(comboAttacker, target, sig, weaponDamage);
            // Étincelles de la famille, une par élément : le mélange de couleurs se voit tout de suite.
            WeaponVfxSubtle.Subtle accent = WeaponVfxSubtle.pick(el, sl.random.nextLong());
            broadcastNear(sl, tx, ty, tz, 0, WeaponVfxSubtle.NET_KIND_BASE + accent.index(), 1,
                    tx, ty, tz, ax, ay, az, 1 << el.ordinal());
            played++;
        }
        return played > 1;
    }

    /** T351-ELEMENT-BLEND : masque des éléments de l'arme (0 si inconnu) pour mélanger les couleurs. */
    private static int paletteOf(ItemStack weapon, WeaponVisualEffectId fx) {
        int mask = WeaponVisualEffectId.elementMaskOf(weapon);
        if (mask == 0 && fx != null) mask = 1 << fx.element.ordinal();
        return mask;
    }

    /** Famille élémentaire à utiliser : une des familles portées par l'arme (variété), sinon celle de l'effet. */
    private static DeepElement pickElement(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx) {
        List<DeepElement> els = WeaponVisualEffectId.elementsOf(weapon);
        if (els.isEmpty()) return (fx == null) ? DeepElement.METAL : fx.element;
        return els.get(sl.random.nextInt(els.size()));
    }

    private static boolean shouldPlay(int attackerId, WeaponVisualEffectId fx, ServerLevel sl, boolean crit) {
        long now = sl.getGameTime();
        String key = attackerId + ":" + fx.id;
        Long ready = EFFECT_COOLDOWN.get(key);
        if (ready != null && now < ready) return false;

        // T351/T359 : 5 % sur une attaque normale, 76 % sur un coup critique.
        int chance = crit ? CRIT_TRIGGER_PERCENT : NORMAL_TRIGGER_PERCENT;
        if (sl.random.nextInt(100) >= chance) {
            // Nouvelle tentative possible très vite : on ne veut pas « perdre » un long cooldown sur un jet raté
            EFFECT_COOLDOWN.put(key, now + 4L);
            return false;
        }
        int cooldown = crit
                ? 18 + sl.random.nextInt(22)
                : 45 + sl.random.nextInt(45);
        EFFECT_COOLDOWN.put(key, now + cooldown);
        if (EFFECT_COOLDOWN.size() > 2048) {
            EFFECT_COOLDOWN.clear();
        }
        return true;
    }

    /** Direction, contacts hostiles et combustion finale des trois signatures Fire. */
    private static void applyFireGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx, float visualScale) {
        applyFireGameplay(attacker, target, fx, visualScale, signatureBaseDamage(attacker, target));
    }

    private static void applyFireGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx, float visualScale,
                                          float weaponDamage) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        int attackerId = attacker == null ? -1 : attacker.getId();
        weaponDamage = Math.max(0.0f, weaponDamage);
        boolean molten = fx == WeaponVisualEffectId.MOLTEN_FIRE_WAVE;
        boolean soul = fx == WeaponVisualEffectId.SOUL_FIRE_CRESCENT;
        boolean meteor = fx == WeaponVisualEffectId.BRIMSTONE_METEOR;
        if (!molten && !soul && !meteor) return;

        long now = sl.getGameTime();
        int scheduleTick = TestProcedure.currentTick(sl);
        if (molten) scheduleWaveIgnition(sl, attacker, target, scheduleTick, weaponDamage);
        if (meteor) {
            scheduleMeteorTracking(sl, target, scheduleTick);
            scheduleMeteorExplosionZone(sl, attacker, target, scheduleTick, weaponDamage);
        }
        scheduleFireSoundtrack(sl, attacker, target, fx, scheduleTick);

        // La fin de chorégraphie réapplique exactement quatre secondes de feu à la cible.
        int duration = meteor ? 128 : (molten ? 92 : 112);
        TestProcedure.schedule(sl, scheduleTick + duration, () -> {
            if (!target.isAlive() || target.level() != sl) return;
            if (soul) igniteSoulForFourSeconds(target);
            else igniteNormalForFourSeconds(target);
        });

        if (!soul) return;
        SoulHold previous = SOUL_HOLDS.get(target.getId());
        if (previous != null && previous.expires() > now) return;
        rememberSignatureAi(target);
        boolean previousNoAi = previous != null ? previous.previousNoAi()
                : target instanceof Mob mob && mob.isNoAi();
        double x = target.getX(), baseY = target.getY(), z = target.getZ();
        // FireSignatureVfx place la lune à 1.32 * scale au-dessus du sol local. Le centre du
        // corps doit coïncider avec elle : on convertit donc cette hauteur en Y des pieds.
        double targetY = baseY + 1.32 * net.minecraft.util.Mth.clamp(visualScale, 0.30f, 4.0f)
                - target.getBbHeight() * 0.50;
        SOUL_HOLDS.put(target.getId(), new SoulHold(now + 104, now + 12, now + 22,
                now + 90, x, baseY, targetY, z, previousNoAi, attackerId, weaponDamage));
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        target.stopUsingItem();
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0f;
    }

    private static void igniteNormalForFourSeconds(LivingEntity target) {
        igniteNormalForTicks(target, SIGNATURE_FIRE_TICKS);
    }

    private static void igniteNormalForTicks(LivingEntity target, int ticks) {
        SOUL_BURNS.remove(target.getId());
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), ticks));
    }

    private static void igniteSoulForFourSeconds(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        SoulBurn previous = SOUL_BURNS.get(target.getId());
        long nextDamage = previous != null && previous.expires() > now ? previous.nextDamage() : now;
        SOUL_BURNS.put(target.getId(), new SoulBurn(now + SIGNATURE_FIRE_TICKS, nextDamage, -1, 1.0f, 20));
        // La flamme orange vanilla est remplacée par notre combustion Soul bleue.
        target.setRemainingFireTicks(0);
        emitSoulBurnParticles(target, 10);
    }

    private static void emitSoulBurnParticles(LivingEntity target, int count) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.48, target.getZ(), count,
                Math.max(0.08, target.getBbWidth() * 0.24), target.getBbHeight() * 0.30,
                Math.max(0.08, target.getBbWidth() * 0.24), 0.012);
    }

    private static void tickSoulBurn(LivingEntity target) {
        SoulBurn burn = SOUL_BURNS.get(target.getId());
        if (burn == null || !(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!target.isAlive() || now >= burn.expires()) {
            SOUL_BURNS.remove(target.getId());
            return;
        }
        target.setRemainingFireTicks(0);
        if (now % 3L == 0L) emitSoulBurnParticles(target, 4);
        if (now >= burn.nextDamage()) {
            if (burn.attackerId() >= 0 && burn.period() == 6) {
                hurtWithCapturedWeapon(sl, burn.attackerId(), target, burn.weaponDamage() * 0.001f);
            } else {
                target.hurt(target.damageSources().onFire(), 1.0f);
            }
            SOUL_BURNS.put(target.getId(), new SoulBurn(burn.expires(), now + burn.period(),
                    burn.attackerId(), burn.weaponDamage(), burn.period()));
        }
    }

    private record WaveOrigin(double x, double y, double z, double dirX, double dirZ) { }

    /** Départ un bloc devant le joueur en mêlée, sans rapprocher la vague d'une cible distante. */
    private static WaveOrigin waveOrigin(LivingEntity attacker, LivingEntity primaryTarget) {
        double dx = attacker == null ? 1.0 : primaryTarget.getX() - attacker.getX();
        double dz = attacker == null ? 0.0 : primaryTarget.getZ() - attacker.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        double dirX = dx / length;
        double dirZ = dz / length;
        double shift = Math.max(0.0, 3.0 - length);
        return new WaveOrigin(primaryTarget.getX() + dirX * shift, primaryTarget.getY(),
                primaryTarget.getZ() + dirZ * shift, dirX, dirZ);
    }

    /** Corridor de neuf blocs : front = -2 .. +7, largeur = trois blocs, durée = 92 ticks. */
    private static void scheduleWaveIgnition(ServerLevel sl, LivingEntity attacker,
                                             LivingEntity primaryTarget, int now, float weaponDamage) {
        WaveOrigin origin = waveOrigin(attacker, primaryTarget);
        double originX = origin.x(), originY = origin.y(), originZ = origin.z();
        double dirX = origin.dirX(), dirZ = origin.dirZ();
        int attackerId = attacker == null ? -1 : attacker.getId();
        int primaryId = primaryTarget.getId();

        // Un contact toutes les 0,35 s, tant que le corps reste dans le rouleau animé et sa queue.
        for (int delay = 7; delay <= 91; delay += 7) {
            double front = -2.0 + 9.0 * (delay / 92.0);
            TestProcedure.schedule(sl, now + delay, () -> {
                double centerX = originX + dirX * (front - 0.65);
                double centerZ = originZ + dirZ * (front - 0.65);
                AABB query = new AABB(centerX - 3.6, originY - 8.0, centerZ - 3.6,
                        centerX + 3.6, originY + 8.0, centerZ + 3.6);
                for (LivingEntity entity : sl.getEntitiesOfClass(LivingEntity.class, query)) {
                    if (!entity.isAlive()) continue;
                    boolean primary = entity.getId() == primaryId;
                    if (!primary && (entity.getId() == attackerId || !(entity instanceof Enemy))) continue;
                    double rx = entity.getX() - originX;
                    double rz = entity.getZ() - originZ;
                    double along = rx * dirX + rz * dirZ;
                    double lateral = rx * -dirZ + rz * dirX;
                    double radius = entity.getBbWidth() * 0.5;
                    if (along > front + 0.90 + radius || along < front - 2.20 - radius
                            || Math.abs(lateral) > 1.50 + radius) continue;
                    hurtWithCapturedWeapon(sl, attackerId, entity, weaponDamage * 0.001f);
                    // Non cumulatif : la durée restante ne dépasse jamais dix secondes.
                    igniteNormalForTicks(entity, 200);
                }
            });
        }
    }

    /** La tête chercheuse suit la cible sans la stun, puis s'arrête à l'impact. */
    private static void scheduleMeteorTracking(ServerLevel sl, LivingEntity target, int now) {
        int targetId = target.getId();
        for (int delay = 0; delay <= METEOR_IMPACT_TICKS; delay += 2) {
            TestProcedure.schedule(sl, now + delay, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                float x = (float) target.getX();
                float y = (float) (target.getY() + target.getBbHeight() * 0.5);
                float z = (float) target.getZ();
                broadcastNear(sl, x, y, z, WeaponVisualEffectId.BRIMSTONE_METEOR.id,
                        KIND_METEOR_TRACK, 0, x, y, z, targetId, 0.0f, 0.0f);
            });
        }
    }

    /** Zone persistante de trois secondes ; chaque ennemi touché brûle trois secondes. */
    private static void scheduleMeteorExplosionZone(ServerLevel sl, LivingEntity attacker,
                                                    LivingEntity primaryTarget, int now,
                                                    float weaponDamage) {
        int attackerId = attacker == null ? -1 : attacker.getId();
        int primaryId = primaryTarget.getId();
        TestProcedure.schedule(sl, now + METEOR_IMPACT_TICKS, () -> {
            if (primaryTarget.level() != sl) return;
            double x = primaryTarget.getX(), y = primaryTarget.getY(), z = primaryTarget.getZ();
            // Le rocher touche directement le mob suivi au tick visuel 30, sans stun.
            if (primaryTarget.isAlive()) {
                hurtWithCapturedWeapon(sl, attackerId, primaryTarget, weaponDamage * 2.0f);
            }
            Set<Integer> zoneIgnited = new HashSet<>();
            for (int delay = 0; delay <= METEOR_AOE_FIRE_TICKS; delay += 10) {
                TestProcedure.schedule(sl, now + METEOR_IMPACT_TICKS + delay, () -> {
                    AABB area = new AABB(x - METEOR_FIRE_RADIUS, y - 2.0, z - METEOR_FIRE_RADIUS,
                            x + METEOR_FIRE_RADIUS, y + 3.0, z + METEOR_FIRE_RADIUS);
                    for (LivingEntity entity : sl.getEntitiesOfClass(LivingEntity.class, area)) {
                        if (!entity.isAlive() || zoneIgnited.contains(entity.getId())) continue;
                        boolean primary = entity.getId() == primaryId;
                        if (!primary && (entity.getId() == attackerId || !(entity instanceof Enemy))) continue;
                        double dx = entity.getX() - x, dz = entity.getZ() - z;
                        if (dx * dx + dz * dz > METEOR_FIRE_RADIUS * METEOR_FIRE_RADIUS) continue;
                        zoneIgnited.add(entity.getId());
                        igniteNormalForTicks(entity, METEOR_AOE_FIRE_TICKS);
                    }
                });
            }
        });
    }

    /** Bruitages vanilla placés sur les phases et les positions réelles des animations. */
    private static void scheduleFireSoundtrack(ServerLevel sl, LivingEntity attacker,
                                               LivingEntity target, WeaponVisualEffectId fx, int now) {
        if (fx == WeaponVisualEffectId.MOLTEN_FIRE_WAVE) {
            WaveOrigin origin = waveOrigin(attacker, target);
            int[] lavaTicks = {0, 16, 32, 48, 64, 80};
            for (int delay : lavaTicks) {
                double front = -2.0 + 9.0 * (delay / 92.0);
                double x = origin.x() + origin.dirX() * front;
                double z = origin.z() + origin.dirZ() * front;
                TestProcedure.schedule(sl, now + delay, () -> sl.playSound(null, x, origin.y(), z,
                        SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 0.72f, 1.05f + delay * 0.0015f));
            }
            TestProcedure.schedule(sl, now, () -> sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.58f, 1.25f));
            TestProcedure.schedule(sl, now + 86, () -> sl.playSound(null,
                    target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.72f, 1.10f));
            return;
        }
        if (fx == WeaponVisualEffectId.SOUL_FIRE_CRESCENT) {
            TestProcedure.schedule(sl, now, () -> sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.48f, 1.75f));
            TestProcedure.schedule(sl, now + 22, () -> sl.playSound(null,
                    target.getX(), target.getY() + 0.8, target.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.42f, 1.65f));
            for (int delay : new int[]{46, 74}) {
                TestProcedure.schedule(sl, now + delay, () -> sl.playSound(null,
                        target.getX(), target.getY(), target.getZ(),
                        SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.36f, 1.85f));
            }
            TestProcedure.schedule(sl, now + 104, () -> sl.playSound(null,
                    target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.50f, 1.70f));
            return;
        }
        if (fx != WeaponVisualEffectId.BRIMSTONE_METEOR) return;
        TestProcedure.schedule(sl, now, () -> {
            sl.playSound(null, target.getX(), target.getY() + 6.0, target.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.90f, 0.65f);
            sl.playSound(null, target.getX(), target.getY() + 4.0, target.getZ(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.40f, 1.65f);
        });
        TestProcedure.schedule(sl, now + 12, () -> sl.playSound(null,
                target.getX(), target.getY() + 2.0, target.getZ(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.46f, 0.80f));
        TestProcedure.schedule(sl, now + METEOR_IMPACT_TICKS, () -> {
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.10f, 0.82f);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 0.78f, 0.85f);
        });
        TestProcedure.schedule(sl, now + 50, () -> sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.48f, 0.95f));
        TestProcedure.schedule(sl, now + 70, () -> sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 0.42f, 1.15f));
        TestProcedure.schedule(sl, now + 90, () -> sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.42f, 0.95f));
    }

    private static void releaseSoulHold(LivingEntity target, long expectedExpiry) {
        SoulHold hold = SOUL_HOLDS.get(target.getId());
        if (hold == null || hold.expires() != expectedExpiry) return;
        SOUL_HOLDS.remove(target.getId());
        refreshSignatureAi(target);
        target.teleportTo(hold.x(), hold.baseY(), hold.z());
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0f;
    }

    /** Gameplay Poison synchronisé avec la perforation, le shroud et le front de contagion. */
    private static void applyPoisonGameplay(LivingEntity attacker, LivingEntity target,
                                            WeaponVisualEffectId fx) {
        if (target == null || !(target.level() instanceof ServerLevel sl)) return;
        if (fx != WeaponVisualEffectId.VENOM_BRIAR_FANG
                && fx != WeaponVisualEffectId.MIASMA_SHROUD
                && fx != WeaponVisualEffectId.PLAGUE_NOVA) return;
        int now = TestProcedure.currentTick(sl);
        // Les signatures sont centrées sur la cible touchée, pas sur l'attaquant.
        LivingEntity bearer = target;
        LivingEntity caster = attacker;
        int casterId = attacker == null ? -1 : attacker.getId();
        float plaguePulseDamage = Math.max(0.25f, signatureBaseDamage(attacker, target) * 0.16f);

        if (fx == WeaponVisualEffectId.VENOM_BRIAR_FANG) {
            double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.50, z = target.getZ();
            TestProcedure.schedule(sl, now + 7, () -> sl.playSound(null, x, y, z,
                    SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.52f, 0.70f));
            TestProcedure.schedule(sl, now + 26, () -> {
                if (target.isAlive() && target.level() == sl) {
                    applyVisiblePoison(sl, casterId, target, 110);
                }
                sl.playSound(null, x, y, z, SoundEvents.SPIDER_AMBIENT,
                        SoundSource.PLAYERS, 0.52f, 0.64f);
                sl.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH,
                        SoundSource.PLAYERS, 0.86f, 1.28f);
            });
            TestProcedure.schedule(sl, now + 35, () -> sl.playSound(null, x, target.getY(), z,
                    SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.38f, 0.72f));
            TestProcedure.schedule(sl, now + 48, () -> sl.playSound(null, x, target.getY(), z,
                    SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.56f, 0.64f));
            for (int delay = 56; delay <= 84; delay += 14) {
                TestProcedure.schedule(sl, now + delay, () -> sl.playSound(null, x, target.getY(), z,
                        SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.20f, 1.35f));
            }
            TestProcedure.schedule(sl, now + 98, () -> sl.playSound(null, x, target.getY(), z,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.34f, 0.52f));
            return;
        }

        if (fx == WeaponVisualEffectId.MIASMA_SHROUD) {
            int chainId = NEXT_MIASMA_CHAIN_ID++;
            if (NEXT_MIASMA_CHAIN_ID <= 0 || NEXT_MIASMA_CHAIN_ID > 1_000_000) NEXT_MIASMA_CHAIN_ID = 1;
            float pulseDamage = Math.max(0.25f, signatureBaseDamage(attacker, target) * 0.12f);
            MiasmaChain chain = new MiasmaChain(chainId, casterId, now, pulseDamage);
            chain.visited.add(target.getId());
            sl.playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.50, target.getZ(),
                    SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.52f, 0.54f);
            beginMiasmaHost(sl, chain, target, now);
            TestProcedure.schedule(sl, now + MIASMA_MAX_TOTAL_TICKS,
                    () -> finishMiasmaChain(sl, chain, chain.generation));
            return;
        }

        // Plague : le centre est figé au snap ; chaque front élargit la contagion déjà visible.
        double[] blast = {bearer.getX(), bearer.getY() + bearer.getBbHeight() * 0.50, bearer.getZ()};
        Set<Integer> infected = new HashSet<>();
        TestProcedure.schedule(sl, now + 4, () -> sl.playSound(null, blast[0], blast[1], blast[2],
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.50f, 0.52f));
        TestProcedure.schedule(sl, now + 18, () -> sl.playSound(null, blast[0], blast[1], blast[2],
                SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.52f, 0.62f));
        int[] waveTicks = {30, 38, 46, 54, 62, 70, 78, 86, 94, 102};
        double[] radii = {1.20, 2.20, 3.25, 4.20, 4.20, 4.20, 4.20, 4.20, 4.20, 4.20};
        for (int wave = 0; wave < waveTicks.length; wave++) {
            final int waveIndex = wave;
            TestProcedure.schedule(sl, now + waveTicks[wave], () -> {
                if (waveIndex == 0) {
                    blast[0] = bearer.getX();
                    blast[1] = bearer.getY() + bearer.getBbHeight() * 0.50;
                    blast[2] = bearer.getZ();
                    sl.playSound(null, blast[0], blast[1], blast[2], SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.PLAYERS, 0.78f, 1.38f);
                    sl.playSound(null, blast[0], blast[1], blast[2], SoundEvents.SLIME_SQUISH,
                            SoundSource.PLAYERS, 0.88f, 0.58f);
                } else {
                    sl.playSound(null, blast[0], blast[1], blast[2], SoundEvents.WITCH_DRINK,
                            SoundSource.PLAYERS, 0.28f, 0.82f + waveIndex * 0.12f);
                }
                poisonNovaWave(sl, target, caster, blast[0], blast[1], blast[2],
                        radii[waveIndex], 100, plaguePulseDamage, casterId, infected);
            });
        }
        TestProcedure.schedule(sl, now + 102, () -> sl.playSound(null, blast[0], blast[1], blast[2],
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.42f, 0.54f));
    }

    /** Poison vanilla réellement visible dans le HUD et sur l'entité, avec source PvP conservée. */
    private static boolean applyVisiblePoison(ServerLevel sl, int casterId,
                                               LivingEntity target, int durationTicks) {
        if (target == null || !target.isAlive() || target.level() != sl) return false;
        LivingEntity caster = sl.getEntity(casterId) instanceof LivingEntity living ? living : null;
        int duration = Math.max(1, durationTicks);
        boolean vanilla = target.addEffect(new MobEffectInstance(MobEffects.POISON,
                duration, 0, false, true, true), caster);
        if (vanilla || target.hasEffect(MobEffects.POISON)) {
            SIGNATURE_POISONS.remove(target.getId());
            return true;
        }
        // Un Husk est immunisé : le vrai DoT de la signature prend le relais au même rythme que Poison I.
        long now = sl.getGameTime();
        SignaturePoison previous = SIGNATURE_POISONS.get(target.getId());
        long nextDamage = previous != null && previous.expires() > now
                ? previous.nextDamage() : now + 25L;
        SIGNATURE_POISONS.put(target.getId(), new SignaturePoison(now + duration, nextDamage, casterId));
        emitSignaturePoisonParticles(target, 5);
        return true;
    }

    private static void emitSignaturePoisonParticles(LivingEntity target, int count) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + target.getBbHeight() * 0.52, target.getZ(), count,
                Math.max(0.08, target.getBbWidth() * 0.32), target.getBbHeight() * 0.34,
                Math.max(0.08, target.getBbWidth() * 0.32), 0.02);
    }

    private static void tickSignaturePoison(LivingEntity target) {
        SignaturePoison poison = SIGNATURE_POISONS.get(target.getId());
        if (poison == null || !(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!target.isAlive() || now >= poison.expires()) {
            SIGNATURE_POISONS.remove(target.getId());
            return;
        }
        if (now % 5L == 0L) emitSignaturePoisonParticles(target, 2);
        if (now < poison.nextDamage()) return;
        // Comme le poison vanilla, ce DoT seul laisse au moins un demi-cœur.
        float amount = Math.min(1.0f, Math.max(0.0f, target.getHealth() - 1.0f));
        if (amount > 0.0f) hurtPoisonPulse(sl, poison.casterId(), target, amount);
        if (target.isAlive()) {
            SIGNATURE_POISONS.put(target.getId(),
                    new SignaturePoison(poison.expires(), now + 25L, poison.casterId()));
        } else {
            SIGNATURE_POISONS.remove(target.getId());
        }
    }

    /**
     * Début d'un hôte : 6 s maximum sans relais. Après 3 s, les cibles sont recherchées
     * chaque tick depuis la position courante du nuage ; seule la limite globale de 30 s demeure.
     */
    private static void beginMiasmaHost(ServerLevel sl, MiasmaChain chain,
                                        LivingEntity host, int now) {
        if (!chain.active || host == null) return;
        if (now - chain.startedAt >= MIASMA_MAX_TOTAL_TICKS) {
            finishMiasmaChain(sl, chain, chain.generation);
            return;
        }
        LivingEntity previousHost = chain.host;
        if (previousHost != null && previousHost != host) {
            chain.travelFromX = chain.cloudX;
            chain.travelFromY = chain.cloudY;
            chain.travelFromZ = chain.cloudZ;
            chain.traveling = true;
        } else {
            chain.traveling = false;
        }
        chain.host = host;
        chain.hostStartedAt = now;
        refreshMiasmaCloudPosition(chain, now);
        int generation = ++chain.generation;
        chain.shieldExpiry = sl.getGameTime() + MIASMA_HOST_TICKS;
        MIASMA_SHIELDS.put(host.getId(), chain.shieldExpiry);
        if (MIASMA_SHIELDS.size() > 256) MIASMA_SHIELDS.clear();
        applyVisiblePoison(sl, chain.casterId, host, 100);
        broadcastMiasmaChain(sl, chain, host, false);
        TestProcedure.schedule(sl, now + 1,
                () -> tickMiasmaCloud(sl, chain, generation, host));

        int[] pulseOffsets = {20, 48, 76, 102};
        for (int pulse = 0; pulse < pulseOffsets.length; pulse++) {
            float pitch = 0.56f + pulse * 0.11f;
            TestProcedure.schedule(sl, now + pulseOffsets[pulse], () -> {
                if (!isCurrentMiasmaHost(chain, generation, host)) return;
                refreshMiasmaCloudPosition(chain, TestProcedure.currentTick(sl));
                double x = chain.cloudX, y = chain.cloudY, z = chain.cloudZ;
                miasmaDamagePulse(sl, chain, host, x, y, z);
                sl.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH,
                        SoundSource.PLAYERS, 0.30f, pitch);
            });
        }

        for (int corrosionOffset = 20; corrosionOffset <= 100; corrosionOffset += 20) {
            final int elapsed = corrosionOffset;
            TestProcedure.schedule(sl, now + elapsed,
                    () -> corrodeMiasmaHost(sl, chain, generation, host, elapsed));
        }

        TestProcedure.schedule(sl, now + 100, () -> {
            if (isCurrentMiasmaHost(chain, generation, host)) broadcastMiasmaChain(sl, chain, host, true);
        });
        TestProcedure.schedule(sl, now + MIASMA_HOST_TICKS,
                () -> finishMiasmaChain(sl, chain, generation));
    }

    /**
     * Position autoritaire recalculée à chaque tick. Pendant le transfert, elle suit le même
     * smoothstep et le même arc que le rendu ; ensuite elle épouse la position actuelle de l'hôte.
     * À partir de 3 s, une nouvelle liste d'entités est demandée au monde à chaque tick.
     */
    private static void tickMiasmaCloud(ServerLevel sl, MiasmaChain chain, int generation,
                                        LivingEntity host) {
        if (!isCurrentMiasmaHost(chain, generation, host)) return;
        // Le cycle et les tâches utilisent tous les deux le tickCount du scheduler. Le gameTime
        // du niveau possède une autre origine et pouvait empêcher hostAge d'atteindre 60 ticks.
        int now = TestProcedure.currentTick(sl);
        refreshMiasmaCloudPosition(chain, now);
        int hostAge = now - chain.hostStartedAt;
        if (now - chain.startedAt >= MIASMA_MAX_TOTAL_TICKS || hostAge >= MIASMA_HOST_TICKS) {
            finishMiasmaChain(sl, chain, generation);
            return;
        }
        boolean canFinishTravel = now + MIASMA_TRAVEL_TICKS
                <= chain.startedAt + MIASMA_MAX_TOTAL_TICKS;
        if ((!host.isAlive() || hostAge >= MIASMA_SWAP_TICKS) && canFinishTravel) {
            LivingEntity next = findNextMiasmaHost(sl, chain, host);
            if (next != null) {
                applyVisiblePoison(sl, chain.casterId, host, 100); // 5 s à l'hôte quitté.
                MIASMA_SHIELDS.remove(host.getId(), chain.shieldExpiry);
                chain.visited.add(next.getId());
                sl.playSound(null, next.getX(), next.getY() + next.getBbHeight() * 0.50, next.getZ(),
                        SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.42f, 0.72f);
                beginMiasmaHost(sl, chain, next, now); // Aucun plafond de nombre d'hôtes.
                return;
            }
            // Aucun mob pour le moment : nouvelle recherche au tick suivant jusqu'à la fin des 6 s.
        }
        TestProcedure.schedule(sl, now + 1, () -> tickMiasmaCloud(sl, chain, generation, host));
    }

    private static void refreshMiasmaCloudPosition(MiasmaChain chain, int now) {
        LivingEntity host = chain.host;
        if (host == null) return;
        double targetX = host.getX();
        double targetY = host.getY() + host.getBbHeight() * 0.50;
        double targetZ = host.getZ();
        if (!chain.traveling) {
            chain.cloudX = targetX;
            chain.cloudY = targetY;
            chain.cloudZ = targetZ;
            return;
        }
        float progress = Math.max(0.0f, Math.min(1.0f,
                (now - chain.hostStartedAt) / (float) MIASMA_TRAVEL_TICKS));
        float glide = progress * progress * (3.0f - 2.0f * progress);
        chain.cloudX = chain.travelFromX + (targetX - chain.travelFromX) * glide;
        chain.cloudY = chain.travelFromY + (targetY - chain.travelFromY) * glide
                + Math.sin(progress * Math.PI) * 0.65;
        chain.cloudZ = chain.travelFromZ + (targetZ - chain.travelFromZ) * glide;
        if (progress >= 1.0f) chain.traveling = false;
    }

    /** Usure réelle des quatre pièces ; après 2 s, très faible dégât magique hors armure. */
    private static void corrodeMiasmaHost(ServerLevel sl, MiasmaChain chain, int generation,
                                          LivingEntity host, int elapsedTicks) {
        if (!isCurrentMiasmaHost(chain, generation, host) || !host.isAlive() || host.level() != sl) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = host.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem()) {
                armor.hurtAndBreak(1, sl, host, item -> { });
            }
        }
        if (elapsedTicks < 40) return;
        INTERNAL_SIGNATURE_DAMAGE.add(host.getId());
        try {
            host.invulnerableTime = 0;
            if (host.hurt(host.damageSources().magic(), 0.05f)) host.invulnerableTime = 0;
        } finally {
            INTERNAL_SIGNATURE_DAMAGE.remove(host.getId());
        }
    }

    private static boolean isCurrentMiasmaHost(MiasmaChain chain, int generation, LivingEntity host) {
        return chain.active && chain.generation == generation && chain.host == host;
    }

    /**
     * Recherche sphère stricte de 8 blocs depuis le centre actuel du nuage, jamais depuis une
     * ancienne coordonnée. Le monde fournit une nouvelle liste à chaque appel ; PvP inclus.
     */
    private static LivingEntity findNextMiasmaHost(ServerLevel sl, MiasmaChain chain, LivingEntity host) {
        refreshMiasmaCloudPosition(chain, TestProcedure.currentTick(sl));
        double x = chain.cloudX, y = chain.cloudY, z = chain.cloudZ;
        LivingEntity nearest = null;
        double best = MIASMA_DETECTION_RADIUS * MIASMA_DETECTION_RADIUS;
        double r = MIASMA_DETECTION_RADIUS;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - r, y - r, z - r, x + r, y + r, z + r))) {
            if (!candidate.isAlive() || candidate == host || candidate.getId() == chain.casterId
                    || chain.visited.contains(candidate.getId())) continue;
            double distance = candidate.distanceToSqr(x, y, z);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Pulse AoE : aucun filtre Enemy, donc les joueurs PvP sont des cibles comme les mobs. */
    private static void miasmaDamagePulse(ServerLevel sl, MiasmaChain chain, LivingEntity host,
                                          double x, double y, double z) {
        double radius = 2.65;
        for (LivingEntity living : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - 2.0, z - radius, x + radius, y + 2.0, z + radius))) {
            if (!living.isAlive() || living.getId() == chain.casterId
                    || living.distanceToSqr(x, y, z) > radius * radius) continue;
            applyVisiblePoison(sl, chain.casterId, living, 100);
            hurtPoisonPulse(sl, chain.casterId, living, chain.pulseDamage);
        }
        applyVisiblePoison(sl, chain.casterId, host, 100);
    }

    /** Dissipation après au plus 6 s sans saut : poison final de 10 s sur le dernier hôte. */
    private static void finishMiasmaChain(ServerLevel sl, MiasmaChain chain, int generation) {
        if (!chain.active || chain.generation != generation) return;
        chain.active = false;
        LivingEntity host = chain.host;
        if (host != null) {
            applyVisiblePoison(sl, chain.casterId, host, 200);
            MIASMA_SHIELDS.remove(host.getId(), chain.shieldExpiry);
            broadcastMiasmaChain(sl, chain, host, true);
            sl.playSound(null, host.getX(), host.getY(), host.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.54f, 0.46f);
        }
    }

    private static void broadcastMiasmaChain(ServerLevel sl, MiasmaChain chain,
                                             LivingEntity host, boolean ending) {
        float x = (float) host.getX();
        float y = (float) (host.getY() + host.getBbHeight() * 0.50);
        float z = (float) host.getZ();
        broadcastNear(sl, x, y, z, WeaponVisualEffectId.MIASMA_SHROUD.id,
                KIND_MIASMA_CHAIN, 3, x, y, z,
                chain.id, host.getId(), ending ? 1.0f : 0.0f,
                1 << DeepElement.POISON.ordinal(), sizeScaleOf(host));
    }

    /** Pulse de nova : dégâts constants + poison ; enveloppe visuelle une seule fois par cible. */
    private static void poisonNovaWave(ServerLevel sl, LivingEntity victim, LivingEntity caster,
                                       double x, double y, double z, double radius, int duration,
                                       float pulseDamage, int casterId, Set<Integer> infected) {
        for (LivingEntity living : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - 1.8, z - radius, x + radius, y + 2.2, z + radius))) {
            if (!living.isAlive() || living == caster || living.distanceToSqr(x, y, z) > radius * radius) continue;
            applyVisiblePoison(sl, casterId, living, duration);
            hurtPoisonPulse(sl, casterId, living, pulseDamage);
            if (!living.isAlive() || !infected.add(living.getId())) continue;
            float lx = (float) living.getX();
            float ly = (float) (living.getY() + living.getBbHeight() * 0.50);
            float lz = (float) living.getZ();
            broadcastNear(sl, lx, ly, lz, WeaponVisualEffectId.PLAGUE_NOVA.id,
                    KIND_PLAGUE_CONTAGION, 3, lx, ly, lz,
                    living.getId(), 0.0f, 0.0f,
                    1 << DeepElement.POISON.ordinal(), sizeScaleOf(living));
        }
    }

    /** Flare vert + ring violet déclenchés par un vrai hit reçu, sans changer les dégâts vanilla. */
    private static void triggerMiasmaDefense(ServerLevel sl, LivingEntity bearer, Entity source) {
        Long expiry = MIASMA_SHIELDS.get(bearer.getId());
        if (expiry == null) return;
        if (sl.getGameTime() > expiry) {
            MIASMA_SHIELDS.remove(bearer.getId());
            return;
        }
        float x = (float) bearer.getX();
        float y = (float) (bearer.getY() + bearer.getBbHeight() * 0.50);
        float z = (float) bearer.getZ();
        float sx = source != null ? (float) source.getX() : x;
        float sy = source != null ? (float) source.getY() : y;
        float sz = source != null ? (float) source.getZ() : z;
        broadcastNear(sl, x, y, z, WeaponVisualEffectId.MIASMA_SHROUD.id,
                KIND_MIASMA_DEFENSE, 4, x, y, z, sx, sy, sz,
                1 << DeepElement.POISON.ordinal(), sizeScaleOf(bearer));
        sl.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.34f, 1.42f);
    }

    /**
     * Gameplay Métal : trois rôles volontairement différents.
     * Razor = lame perforante joueur → ennemi + slash AoE ; Prism = trois lames + deux ricochets ;
     * Arc = dégâts cumulables par lame + contrôle de six ennemis maximum. Aucun second Annihilating Sweep.
     */

    /**
     * Gameplay Lumière synchronisé avec la géométrie visible. Les dégâts utilisent le montant de
     * l'attaque capturé, conservent le crédit du tueur et passent par le verrou anti-récursion.
     */
    private static void applyLightGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage, float visualScale) {
        if (target == null || !(target.level() instanceof ServerLevel sl)) return;
        if (fx != WeaponVisualEffectId.SERAPH_SKY_PILLAR
                && fx != WeaponVisualEffectId.ASTRAL_STAR_LANCE
                && fx != WeaponVisualEffectId.CHRONO_ETERNAL_HALO) return;
        int now = TestProcedure.currentTick(sl);
        int attackerId = attacker == null ? -1 : attacker.getId();
        float captured = Math.max(0.0f, weaponDamage);
        final float lightScale = net.minecraft.util.Mth.clamp(visualScale, 0.20f, 6.0f);
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.50, z = target.getZ();

        if (fx == WeaponVisualEffectId.SERAPH_SKY_PILLAR) {
            List<LivingEntity> beamTargets = new ArrayList<>();
            beamTargets.add(target);
            for (LivingEntity nearby : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(x - 10.0, y - 10.0, z - 10.0, x + 10.0, y + 10.0, z + 10.0))) {
                if (nearby.getId() == target.getId() || nearby.getId() == attackerId
                        || !(nearby instanceof Enemy) || nearby instanceof Player || !nearby.isAlive()) continue;
                if (nearby.distanceToSqr(x, y, z) <= 100.0) beamTargets.add(nearby);
            }
            beamTargets.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
            for (LivingEntity beamTarget : beamTargets) {
                boolean primary = beamTarget.getId() == target.getId();
                float bx = (float) beamTarget.getX();
                float by = (float) (beamTarget.getY() + beamTarget.getBbHeight() * 0.50);
                float bz = (float) beamTarget.getZ();
                float beamWidth = seraphBeamWidthOf(beamTarget);
                // Position fixe pendant le cast et les quatre ticks actifs, attaques conservées.
                applyMovementHold(beamTarget, sl.getGameTime() + 15L, false);
                if (!primary) {
                    float ax = (float) (attacker == null ? bx : attacker.getX());
                    float ay = (float) (attacker == null ? by + 1.0 : attacker.getEyeY());
                    float az = (float) (attacker == null ? bz + 1.0 : attacker.getZ());
                    broadcastNear(sl, bx, by, bz, fx.id, KIND_IMPACT, 6,
                            bx, by, bz, ax, ay, az,
                            attacker == null ? 1 << fx.element.ordinal()
                                    : paletteOf(attacker.getMainHandItem(), fx), beamWidth);
                }
                lightSound(sl, bx, by, bz, now, 0, SoundEvents.AMETHYST_BLOCK_CHIME,
                        primary ? 0.34f : 0.16f, primary ? 1.30f : 1.65f);
                lightSound(sl, bx, by, bz, now, 9, SoundEvents.BEACON_ACTIVATE,
                        primary ? 0.52f : 0.22f, primary ? 1.55f : 1.85f);
                lightSound(sl, bx, by, bz, now, 10, SoundEvents.TOTEM_USE,
                        primary ? 0.38f : 0.16f, 1.80f);
                lightSound(sl, bx, by, bz, now, 12, SoundEvents.NOTE_BLOCK_BELL.value(),
                        primary ? 0.30f : 0.12f, 2.00f);
                lightSound(sl, bx, by, bz, now, 24, SoundEvents.BEACON_DEACTIVATE,
                        primary ? 0.30f : 0.12f, 1.55f);
                lightSound(sl, bx, by, bz, now, 0, SoundEvents.BEACON_AMBIENT,
                        primary ? 0.24f : 0.10f, 2.00f);
                lightSound(sl, bx, by, bz, now, 10, SoundEvents.BEACON_AMBIENT,
                        primary ? 0.28f : 0.12f, 2.00f);
                lightSound(sl, bx, by, bz, now, 14, SoundEvents.BEACON_POWER_SELECT,
                        primary ? 0.24f : 0.10f, 1.90f);
                for (int delay = 10; delay <= 14; delay += 2) {
                    TestProcedure.schedule(sl, now + delay, () -> {
                        if (!beamTarget.isAlive() || beamTarget.level() != sl
                                || !seraphRayContact(beamTarget, bx, by, bz, beamWidth * 11.0f / 24.0f, 1.0f)) return;
                        boolean undead = beamTarget.getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE);
                        float factor = (undead ? 0.25f : 0.18f) * (primary ? 1.0f : 0.70f);
                        float cap = (undead ? 1.75f : 1.25f) * (primary ? 1.0f : 0.70f);
                        float amount = net.minecraft.util.Mth.clamp(captured * factor,
                                primary ? 0.10f : 0.07f, cap);
                        if (!hurtWithCapturedWeapon(sl, attackerId, beamTarget, amount)) return;
                        beamTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, false, true, true));
                    });
                }
            }
            return;
        }

        if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE) {
            // La primaire est immobilisée AVANT le départ et jusqu'après la perforation.
            applyMovementHold(target, sl.getGameTime() + 40L, false);
            double rawX = attacker == null ? 0.0 : target.getX() - attacker.getX();
            double rawZ = attacker == null ? 1.0 : target.getZ() - attacker.getZ();
            double len = Math.max(0.001, Math.sqrt(rawX * rawX + rawZ * rawZ));
            double forwardX = rawX / len, forwardZ = rawZ / len;
            // Droite du joueur : gauche (-18) -> cible -> droite (+18), jamais depuis son corps.
            double dirX = -forwardZ, dirZ = forwardX;
            for (int chime = 0; chime <= 12; chime += 4) {
                lightSound(sl, x, y, z, now, chime, SoundEvents.AMETHYST_BLOCK_CHIME,
                        0.18f, 1.35f + chime * 0.035f);
            }
            lightSound(sl, x, y, z, now, 4, SoundEvents.TRIDENT_THROW.value(), 0.30f, 1.20f);
            lightSound(sl, x, y, z, now, 14, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.50f);
            lightSound(sl, x, y, z, now, 15, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.30f, 1.70f);
            lightSound(sl, x, y, z, now, 20, SoundEvents.TRIDENT_THROW.value(), 0.42f, 1.45f);
            lightSound(sl, x, y, z, now, 24, SoundEvents.PLAYER_ATTACK_CRIT, 0.52f, 1.80f);
            lightSound(sl, x, y, z, now, 30, SoundEvents.ANVIL_PLACE, 0.20f, 1.75f);
            lightSound(sl, x, y, z, now, 36, SoundEvents.PLAYER_ATTACK_SWEEP, 0.20f, 1.90f);
            lightSound(sl, x, y, z, now, 40, SoundEvents.TRIDENT_THROW.value(), 0.25f, 1.80f);
            lightSound(sl, x, y, z, now, 48, SoundEvents.AMETHYST_BLOCK_CHIME, 0.28f, 1.85f);
            lightSound(sl, x, y, z, now, 64, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.18f, 1.25f);
            Set<Integer> spearHits = new HashSet<>();
            for (int delay = 4; delay <= 52; delay++) {
                int age = delay;
                TestProcedure.schedule(sl, now + delay, () -> tickAstralSpear(sl, attacker, attackerId,
                        target, x, y, z, dirX, dirZ, captured, lightScale, age, spearHits));
            }
            return;
        }

        // Chrono V5 AoE : chaque mob non-joueur dans dix blocs reçoit sa propre horloge,
        // sa dette et son verrou. Les animaux AgeableMob peuvent donc aussi rajeunir.
        List<LivingEntity> chronoTargets = new ArrayList<>();
        chronoTargets.add(target);
        for (LivingEntity nearby : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - 10.0, y - 10.0, z - 10.0, x + 10.0, y + 10.0, z + 10.0))) {
            if (nearby.getId() == target.getId() || nearby.getId() == attackerId
                    || nearby instanceof Player || !nearby.isAlive()) continue;
            if (nearby.distanceToSqr(x, y, z) <= 100.0) chronoTargets.add(nearby);
        }
        chronoTargets.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        for (LivingEntity chronoTarget : chronoTargets) {
            boolean primary = chronoTarget.getId() == target.getId();
            float cx = (float) chronoTarget.getX();
            float cy = (float) (chronoTarget.getY() + chronoTarget.getBbHeight() * 0.50);
            float cz = (float) chronoTarget.getZ();
            // Dix secondes de position fixe, sans NoAi : chaque mob peut toujours attaquer.
            applyMovementHold(chronoTarget, sl.getGameTime() + 200L, false);
            if (!primary) {
                float ax = (float) (attacker == null ? cx : attacker.getX());
                float ay = (float) (attacker == null ? cy + 1.0 : attacker.getEyeY());
                float az = (float) (attacker == null ? cz + 1.0 : attacker.getZ());
                broadcastNear(sl, cx, cy, cz, fx.id, KIND_IMPACT, 6,
                        cx, cy, cz, ax, ay, az,
                        attacker == null ? 1 << fx.element.ordinal()
                                : paletteOf(attacker.getMainHandItem(), fx), sizeScaleOf(chronoTarget));
            }
            // Warden, Wither et Dragon restent insensibles au ralentissement, comme auparavant.
            if (chronoTarget instanceof Enemy && chronoTarget.getType() != EntityType.ENDER_DRAGON
                    && chronoTarget.getType() != EntityType.WITHER && chronoTarget.getType() != EntityType.WARDEN) {
                chronoTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        105, 1, false, true, true));
            }
            float soundScale = primary ? 1.0f : 0.45f;
            // Un son uniquement lorsque le cran de l'aiguille change : avant = hat, retour = snare.
            for (int tick = 1; tick < 100; tick++) {
                int step = chronoVisualStep(tick), previous = chronoVisualStep(tick - 1);
                if (step > previous) {
                    lightSound(sl, cx, cy, cz, now, tick, SoundEvents.NOTE_BLOCK_HAT.value(),
                            0.055f * soundScale, 1.90f);
                } else if (step < previous) {
                    lightSound(sl, cx, cy, cz, now, tick, SoundEvents.NOTE_BLOCK_SNARE.value(),
                            0.045f * soundScale, 1.55f);
                }
            }
            lightSound(sl, cx, cy, cz, now, 0, SoundEvents.AMETHYST_BLOCK_CHIME, 0.30f * soundScale, 0.75f);
            lightSound(sl, cx, cy, cz, now, 0, SoundEvents.ENDERMAN_TELEPORT, 0.20f * soundScale, 1.35f);
            for (int bell = 15; bell <= 45; bell += 15) {
                lightSound(sl, cx, cy, cz, now, bell, SoundEvents.NOTE_BLOCK_BELL.value(),
                        0.16f * soundScale, 0.85f + bell * 0.012f);
            }
            lightSound(sl, cx, cy, cz, now, 55, SoundEvents.NOTE_BLOCK_BELL.value(), 0.55f * soundScale, 1.55f);
            lightSound(sl, cx, cy, cz, now, 55, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.24f * soundScale, 1.45f);
            lightSound(sl, cx, cy, cz, now, 58, SoundEvents.BONE_MEAL_USE, 0.26f * soundScale, 1.60f);
            lightSound(sl, cx, cy, cz, now, 86, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.25f * soundScale, 0.90f);
            lightSound(sl, cx, cy, cz, now, 100, SoundEvents.BEACON_DEACTIVATE, 0.22f * soundScale, 1.25f);
            lightSound(sl, cx, cy, cz, now, 200, SoundEvents.BEACON_DEACTIVATE, 0.30f * soundScale, 0.85f);
            TestProcedure.schedule(sl, now + 55, () -> {
                if (!chronoTarget.isAlive() || chronoTarget.level() != sl) return;
                float amount = primary
                        ? net.minecraft.util.Mth.clamp(captured * 0.24f, 0.12f, 2.00f)
                        : net.minecraft.util.Mth.clamp(captured * 0.10f, 0.06f, 1.10f);
                if (!hurtWithCapturedWeapon(sl, attackerId, chronoTarget, amount)) return;
                // Santé post-dégâts, même entité et équipement préservé pour CHAQUE cible de l'AoE.
                if (chronoTarget.isAlive()) rejuvenateIfVanillaBabyExists(chronoTarget);
                chronoTarget.hurtMarked = true;
            });
        }
    }

    /** Position de la pointe au tick serveur, strictement identique au rendu de 80 ticks. */
    private static float astralHeadAtTick(int delay) {
        float t = delay / 80.0f;
        float travel = easeInOutUnit((t - 0.04f) / 0.26f);
        float after = easeOutUnit((t - 0.30f) / 0.30f);
        return -18.0f + travel * 18.0f + after * 18.0f;
    }

    private static float smoothUnit(float x) {
        x = net.minecraft.util.Mth.clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float easeOutUnit(float x) {
        x = net.minecraft.util.Mth.clamp(x, 0.0f, 1.0f);
        return 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x);
    }

    private static float easeInOutUnit(float x) {
        x = net.minecraft.util.Mth.clamp(x, 0.0f, 1.0f);
        return x < 0.5f ? 4.0f * x * x * x
                : 1.0f - (float) Math.pow(-2.0f * x + 2.0f, 3.0f) * 0.5f;
    }

    /** Contact balayé de la longue lance : une touche et un push par ennemi, avant ou après la primaire. */
    private static void tickAstralSpear(ServerLevel sl, LivingEntity attacker, int attackerId,
                                        LivingEntity primary, double x, double y, double z,
                                        double dirX, double dirZ, float captured, float scale,
                                        int delay, Set<Integer> spearHits) {
        float head = astralHeadAtTick(delay) * scale;
        double tail = head - 9.0 * scale, tip = head + 1.20 * scale;
        double startX = x + dirX * tail, startZ = z + dirZ * tail;
        double endX = x + dirX * tip, endZ = z + dirZ * tip;
        List<LivingEntity> candidates = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(Math.min(startX, endX) - 1.0, y - 2.5 * scale, Math.min(startZ, endZ) - 1.0,
                        Math.max(startX, endX) + 1.0, y + 2.5 * scale, Math.max(startZ, endZ) + 1.0)));
        candidates.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        float primaryDamage = net.minecraft.util.Mth.clamp(captured * 0.30f, 0.15f, 2.75f);
        for (LivingEntity entity : candidates) {
            if (spearHits.contains(entity.getId()) || !lightTarget(entity, primary, attackerId)) continue;
            boolean isPrimary = entity.getId() == primary.getId();
            // La primaire reste verrouillée et reçoit son pulse à l'impact visuel du tick 24.
            if (isPrimary && delay != 24) continue;
            boolean guaranteedPrimary = isPrimary && delay == 24;
            if (!guaranteedPrimary
                    && !segmentHitsEntity(startX, y, startZ, endX, y, endZ, entity, 0.22 * scale)) continue;
            if (!isPrimary && attacker != null && !attacker.hasLineOfSight(entity)) continue;
            float amount = isPrimary ? primaryDamage : primaryDamage * 0.50f;
            if (!hurtWithCapturedWeapon(sl, attackerId, entity, amount)) continue;
            spearHits.add(entity.getId());
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true, true));
            float push = isPrimary ? 0.30f : 0.45f;
            entity.push(dirX * push, 0.10, dirZ * push);
            entity.hurtMarked = true;
        }
    }

    /** Crans visuels de Chrono : les mêmes entiers pilotent l'angle et les deux tick sonores. */
    private static int chronoVisualStep(int tick) {
        tick = net.minecraft.util.Mth.clamp(tick, 0, 99);
        if (tick < 20) return (int) Math.floor(smoothUnit(tick / 20.0f) * 16.0f);
        if (tick < 55) {
            float u = (tick - 20) / 35.0f;
            float continuous = 2.0f + u * 8.0f + u * u * 4.0f;
            return (int) Math.floor(continuous * 8.0f);
        }
        float continuous = 14.0f - smoothUnit((tick - 55) / 38.0f) * 10.0f;
        return (int) Math.ceil(continuous * 8.0f - 1.0E-6);
    }

    /** La primaire conserve son éligibilité PvP ; les touches collatérales restent enemies-only. */
    private static boolean lightTarget(LivingEntity entity, LivingEntity primary, int attackerId) {
        if (!entity.isAlive() || entity.getId() == attackerId) return false;
        return entity.getId() == primary.getId() || (entity instanceof Enemy && !(entity instanceof Player));
    }

    /**
     * Rajeunit sans remplacer l'entité. Zombie couvre aussi Husk, Drowned, ZombieVillager et
     * ZombifiedPiglin ; Piglin et AgeableMob ont leurs propres données bébé vanilla. Skeleton,
     * Wither, Warden et les autres types sans forme bébé ne sont volontairement pas fabriqués.
     */
    private static boolean rejuvenateIfVanillaBabyExists(LivingEntity entity) {
        float healthAfterDebt = entity.getHealth();
        if (entity instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            zombie.setBaby(true);
        } else if (entity instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            piglin.setBaby(true);
        } else if (entity instanceof net.minecraft.world.entity.AgeableMob ageable) {
            ageable.setBaby(true);
        } else {
            return false;
        }
        // setBaby ne doit ni soigner ni infliger de dégâts. On restaure explicitement les PV
        // restants après la dette, sans jamais dépasser le maximum courant de l'entité.
        entity.setHealth(Math.min(healthAfterDebt, entity.getMaxHealth()));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 105, 0, false, true, true));
        return true;
    }

    /** Collision propre au grand rayon vertical de Séraphin. */
    private static boolean seraphRayContact(LivingEntity entity, double x, double y, double z,
                                            double radius, float lightScale) {
        AABB box = entity.getBoundingBox();
        if (box.maxY < y - 0.90 * lightScale || box.minY > y + 79.10 * lightScale) return false;
        return discContact(box, x, z, radius);
    }

    private static boolean discContact(AABB box, double x, double z, double radius) {
        double nearestX = net.minecraft.util.Mth.clamp(x, box.minX, box.maxX);
        double nearestZ = net.minecraft.util.Mth.clamp(z, box.minZ, box.maxZ);
        double dx = x - nearestX, dz = z - nearestZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Collision segment/AABB par la méthode des slabs : aucun tunneling entre deux positions. */
    private static boolean segmentHitsEntity(double x0, double y0, double z0,
                                             double x1, double y1, double z1,
                                             LivingEntity entity, double padding) {
        AABB box = entity.getBoundingBox().inflate(padding);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double min = 0.0, max = 1.0;
        double[] origin = {x0, y0, z0}, delta = {dx, dy, dz};
        double[] lower = {box.minX, box.minY, box.minZ}, upper = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-8) {
                if (origin[axis] < lower[axis] || origin[axis] > upper[axis]) return false;
                continue;
            }
            double a = (lower[axis] - origin[axis]) / delta[axis];
            double b = (upper[axis] - origin[axis]) / delta[axis];
            if (a > b) { double swap = a; a = b; b = swap; }
            min = Math.max(min, a);
            max = Math.min(max, b);
            if (min > max) return false;
        }
        return max >= 0.0 && min <= 1.0;
    }

    private static void lightSound(ServerLevel sl, double x, double y, double z,
                                   int now, int delay, SoundEvent sound, float volume, float pitch) {
        TestProcedure.schedule(sl, now + delay,
                () -> sl.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch));
    }

    private static void applyMetalGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage) {
        if (fx != WeaponVisualEffectId.RAZOR_GLEAM && fx != WeaponVisualEffectId.SUPREME_PRISM_EDGE
                && fx != WeaponVisualEffectId.ANNIHILATING_ARC) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        int now = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        int attackerId = attacker == null ? -1 : attacker.getId();
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.5, z = target.getZ();
        float bladeScale = sizeScaleOf(target);
        double rawAttackX = attacker == null ? 0.0 : target.getX() - attacker.getX();
        double rawAttackZ = attacker == null ? 1.0 : target.getZ() - attacker.getZ();
        double attackLength = Math.max(0.001, Math.sqrt(rawAttackX * rawAttackX + rawAttackZ * rawAttackZ));
        final double attackX = rawAttackX / attackLength;
        final double attackZ = rawAttackZ / attackLength;
        double prismX = -attackX, prismZ = -attackZ;

        if (fx == WeaponVisualEffectId.RAZOR_GLEAM) {
            // Contact réel de la percée puis du slash de 95°. Un mob ne reçoit qu'un hit par activation.
            float piercing = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.35f, 0.20f, 3.00f);
            long holdExpiry = gameNow + 13;
            boolean heldByMetal = tryMetalHold(target, holdExpiry, true);
            Set<Integer> bladeHits = new HashSet<>();
            for (int delay = 4; delay <= 44; delay++) {
                int age = delay;
                TestProcedure.schedule(sl, now + delay, () -> tickRazorBlade(sl, attackerId,
                        x, y, z, attackX, attackZ, age, piercing, bladeScale, bladeHits));
            }
            metalSound(sl, x, y, z, now, 0, SoundEvents.ANVIL_PLACE, 0.10f, 0.95f);
            metalSound(sl, x, y, z, now, 8, SoundEvents.PLAYER_ATTACK_SWEEP, 0.46f, 1.90f);
            metalSound(sl, x, y, z, now, 12, SoundEvents.PLAYER_ATTACK_CRIT, 0.58f, 1.60f);
            metalSound(sl, x, y, z, now, 12, SoundEvents.ANVIL_PLACE, 0.14f, 2.00f);
            TestProcedure.schedule(sl, now + 12, () -> {
                // Garantie de la primaire si sa hitbox spécifique a glissé entre deux segments voxel.
                if (target.isAlive() && target.level() == sl
                        && hurtMetalContact(sl, attackerId, target, piercing, true, bladeHits)) {
                    target.push(attackX * 0.28, 0.12, attackZ * 0.28);
                    target.hurtMarked = true;
                }
                if (heldByMetal) releaseMovementHold(target, holdExpiry);
            });
            return;
        }

        if (fx == WeaponVisualEffectId.SUPREME_PRISM_EDGE) {
            // La primaire sert de prisme, mais ne subit aucun dégât additionnel. Deux secondaires à 30 %.
            float ricochetDamage = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.30f, 0.15f, 2.50f);
            long holdExpiry = gameNow + 31;
            boolean heldByMetal = tryMetalHold(target, holdExpiry, false); // Position fixe, attaques conservées.
            float bladeDamage = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.20f, 0.10f, 1.75f);
            Set<Integer> bladeHits = new HashSet<>();
            for (int delay = 4; delay <= 36; delay++) {
                int age = delay;
                TestProcedure.schedule(sl, now + delay, () -> tickPrismBlades(sl, attackerId,
                        x, y, z, prismX, prismZ, age, bladeDamage, bladeScale, bladeHits));
            }
            TestProcedure.schedule(sl, now + 20, () -> {
                if (target.isAlive() && target.level() == sl) {
                    hurtMetalContact(sl, attackerId, target, bladeDamage, false, bladeHits);
                }
            });
            metalSound(sl, x, y, z, now, 0, SoundEvents.AMETHYST_BLOCK_CHIME, 0.40f, 1.10f);
            metalSound(sl, x, y, z, now, 18, SoundEvents.AMETHYST_BLOCK_CHIME, 0.24f, 1.55f);
            metalSound(sl, x, y, z, now, 30, SoundEvents.NOTE_BLOCK_BELL.value(), 0.38f, 1.35f);
            TestProcedure.schedule(sl, now + 30, () -> {
                List<LivingEntity> targets = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(x - 4.5, y - 3.0, z - 4.5, x + 4.5, y + 3.0, z + 4.5)));
                targets.removeIf(candidate -> !(candidate instanceof Enemy) || !candidate.isAlive()
                        || candidate.getId() == target.getId() || candidate.getId() == attackerId
                        || candidate.distanceToSqr(x, y, z) > 20.25
                        || !target.hasLineOfSight(candidate));
                targets.sort((a, b) -> Double.compare(a.distanceToSqr(x, y, z), b.distanceToSqr(x, y, z)));
                int ricochets = Math.min(2, targets.size());
                for (int i = 0; i < ricochets; i++) {
                    LivingEntity secondary = targets.get(i);
                    if (!hurtWithCapturedWeapon(sl, attackerId, secondary, ricochetDamage)) continue;
                    broadcastNear(sl, (float) x, (float) y, (float) z,
                            WeaponVisualEffectId.SUPREME_PRISM_EDGE.id, KIND_BEAM_SIPHON, 1,
                            (float) x, (float) y, (float) z,
                            (float) secondary.getX(), (float) (secondary.getY() + secondary.getBbHeight() * 0.5),
                            (float) secondary.getZ(), 1 << DeepElement.METAL.ordinal());
                    // Un timbre par ricochet, à la position de la cible touchée et non en boucle au centre.
                    sl.playSound(null, secondary.getX(), secondary.getY(), secondary.getZ(),
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                            0.30f, 1.50f + i * 0.35f);
                }
                if (heldByMetal) releaseMovementHold(target, holdExpiry);
            });
            return;
        }

        // Les lames d'Arc blessent par contact, indépendamment des six mobs retenus pour le contrôle.
        float bladeDamage = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.18f, 0.10f, 2.00f);
        Set<ArcBladeHit> bladeHits = new HashSet<>();
        // Arc sélectionne au plus six ennemis à l'ouverture. Aucun mob entrant plus tard n'est ajouté.
        double radius = 4.0;
        List<LivingEntity> controlled = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - 3.0, z - radius, x + radius, y + 3.0, z + radius)));
        controlled.removeIf(candidate -> !(candidate instanceof Enemy) || !candidate.isAlive()
                || candidate.getId() == attackerId || candidate.distanceToSqr(x, y, z) > radius * radius);
        controlled.sort((a, b) -> Double.compare(a.distanceToSqr(x, y, z), b.distanceToSqr(x, y, z)));
        if (controlled.size() > 6) controlled = new ArrayList<>(controlled.subList(0, 6));
        // La sélection capturée par la lambda doit être finale : controlled vient d'être borné/réaffecté.
        final List<LivingEntity> controlledTargets = List.copyOf(controlled);
        long holdExpiry = gameNow + 31;
        Set<Integer> metalHeld = new HashSet<>();
        for (LivingEntity candidate : controlledTargets) {
            // Maintien jusqu'au premier contact ou au tick 30 ; le hit libère ce seul hold Metal.
            if (tryMetalHold(candidate, holdExpiry, false)) metalHeld.add(candidate.getId());
        }
        for (int delay = 2; delay <= 58; delay++) {
            int age = delay;
            TestProcedure.schedule(sl, now + delay, () -> tickArcBlades(sl, attackerId,
                    x, y, z, prismX, prismZ, age, bladeDamage, bladeScale,
                    bladeHits, holdExpiry, metalHeld));
        }
        metalSound(sl, x, y, z, now, 0, SoundEvents.ANVIL_PLACE, 0.20f, 0.65f); // cliquetis du mécanisme.
        metalSound(sl, x, y, z, now, 12, SoundEvents.ANVIL_PLACE, 0.16f, 0.82f);
        metalSound(sl, x, y, z, now, 24, SoundEvents.PLAYER_ATTACK_SWEEP, 0.28f, 0.62f);
        metalSound(sl, x, y, z, now, 30, SoundEvents.PLAYER_ATTACK_SWEEP, 0.82f, 0.65f);
        metalSound(sl, x, y, z, now, 30, SoundEvents.ANVIL_LAND, 0.42f, 0.78f);
        metalSound(sl, x, y, z, now, 46, SoundEvents.PLAYER_ATTACK_SWEEP, 0.40f, 0.95f);
        TestProcedure.schedule(sl, now + 30, () -> {
            for (LivingEntity candidate : controlledTargets) {
                if (!candidate.isAlive() || candidate.level() != sl
                        || candidate.distanceToSqr(x, y, z) > 20.25) continue;
                MovementHold active = MOVEMENT_HOLDS.get(candidate.getId());
                // Ne jamais libérer ou combattre une emprise Earth/Poison, même à expiration identique.
                if (!metalHeld.contains(candidate.getId()) && active != null) continue;
                if (metalHeld.contains(candidate.getId()) && active != null && active.expires() == holdExpiry) {
                    releaseMovementHold(candidate, holdExpiry);
                }
                double dx = candidate.getX() - x, dz = candidate.getZ() - z;
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length < 0.05) {
                    double angle = candidate.getId() * 2.3999632;
                    dx = Math.cos(angle) * 0.05;
                    dz = Math.sin(angle) * 0.05;
                    length = 0.05;
                }
                double force = 0.70 + 0.45 * (1.0 - Math.min(radius, length) / radius);
                candidate.push(dx / length * force, 0.30, dz / length * force);
                candidate.fallDistance = 0.0f;
                candidate.hurtMarked = true;
            }
        });
    }

    /**
     * Contact des lames Métal : les tests suivent les segments voxel du renderer, côté serveur.
     * Razor/Prism utilisent un set par mob ; Arc utilise une clé (mob, lame) pour cumuler les lames.
     */
    private static List<LivingEntity> metalContactTargets(ServerLevel level, int attackerId,
                                                           double x, double y, double z, double radius) {
        double verticalRadius = Math.max(3.0, radius);
        return level.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - verticalRadius, z - radius,
                        x + radius, y + verticalRadius, z + radius),
                e -> e.isAlive() && e.getId() != attackerId
                        && !(e instanceof Player) && e.getType() != EntityType.ARMOR_STAND);
    }

    private static LivingEntity metalAttacker(ServerLevel level, int attackerId) {
        Entity entity = level.getEntity(attackerId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Une clé par mob ET par lame physique : les hits de lames différentes sont cumulables. */
    private record ArcBladeHit(int entityId, int bladeId) { }
    private record ArcBladePose(double x, double y, double z) { }

    private static boolean hurtArcBladeContact(ServerLevel level, int attackerId, LivingEntity target,
                                               float amount, int bladeId, Set<ArcBladeHit> hitIds) {
        if (target == null || !target.isAlive() || target.level() != level
                || target.getId() == attackerId || target instanceof Player
                || target.getType() == EntityType.ARMOR_STAND
                || !hitIds.add(new ArcBladeHit(target.getId(), bladeId))) return false;
        LivingEntity attacker = metalAttacker(level, attackerId);
        if (attacker != null && target.isAlliedTo(attacker)) return false;
        return hurtMetalEntity(level, attacker, target, amount, false);
    }

    private static boolean hurtMetalContact(ServerLevel level, int attackerId, LivingEntity target,
                                            float amount, boolean sharp, Set<Integer> hitIds) {
        if (target == null || !target.isAlive() || target.level() != level
                || target.getId() == attackerId || target instanceof Player
                || target.getType() == EntityType.ARMOR_STAND || !hitIds.add(target.getId())) return false;
        LivingEntity attacker = metalAttacker(level, attackerId);
        if (attacker != null && target.isAlliedTo(attacker)) return false;
        return hurtMetalEntity(level, attacker, target, amount, sharp);
    }

    private static boolean bladeSegmentHit(LivingEntity target, double centerX, double centerY, double centerZ,
                                           double angle, double length, double halfWidth, double scale) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double dx = target.getX() - centerX, dz = target.getZ() - centerZ;
        double forward = dx * cos + dz * sin;
        double lateral = -dx * sin + dz * cos;
        double padding = halfWidth * scale + 0.12 + target.getBbWidth() * 0.5;
        double verticalPadding = 0.22 * scale;
        double targetBottom = target.getY(), targetTop = targetBottom + target.getBbHeight();
        return forward >= -padding && forward <= length * scale + padding
                && Math.abs(lateral) <= padding
                && targetTop >= centerY - 1.15 * scale - verticalPadding
                && targetBottom <= centerY + 1.15 * scale + verticalPadding;
    }

    private static double contactEaseOut(double value) {
        value = net.minecraft.util.Mth.clamp(value, 0.0, 1.0);
        return 1.0 - Math.pow(1.0 - value, 3.0);
    }

    private static double contactEaseInOut(double value) {
        value = net.minecraft.util.Mth.clamp(value, 0.0, 1.0);
        return value < 0.5 ? 4.0 * value * value * value
                : 1.0 - Math.pow(-2.0 * value + 2.0, 3.0) * 0.5;
    }

    private static double contactEaseIn(double value) {
        value = net.minecraft.util.Mth.clamp(value, 0.0, 1.0);
        return value * value * value;
    }

    private static double contactSmooth(double value) {
        value = net.minecraft.util.Mth.clamp(value, 0.0, 1.0);
        return value * value * (3.0 - 2.0 * value);
    }

    private static boolean bladePointHit(LivingEntity target, double x, double y, double z,
                                         double horizontal, double vertical, double scale) {
        double dx = target.getX() - x, dz = target.getZ() - z;
        double radius = horizontal * scale + target.getBbWidth() * 0.5;
        return dx * dx + dz * dz <= radius * radius
                && target.getY() + target.getBbHeight() >= y - vertical * scale
                && target.getY() <= y + vertical * scale;
    }

    /** Collision continue : le segment parcouru entre deux ticks ne peut plus traverser un mob sans hit. */
    private static boolean sweptBladeHit(LivingEntity target, ArcBladePose previous, ArcBladePose current,
                                         double horizontal, double vertical, double scale) {
        double segmentX = current.x() - previous.x(), segmentZ = current.z() - previous.z();
        double targetX = target.getX() - previous.x(), targetZ = target.getZ() - previous.z();
        double lengthSquared = segmentX * segmentX + segmentZ * segmentZ;
        double u = lengthSquared < 1.0E-8 ? 0.0
                : net.minecraft.util.Mth.clamp((targetX * segmentX + targetZ * segmentZ) / lengthSquared, 0.0, 1.0);
        double closestX = previous.x() + segmentX * u, closestZ = previous.z() + segmentZ * u;
        double dx = target.getX() - closestX, dz = target.getZ() - closestZ;
        double radius = horizontal * scale + target.getBbWidth() * 0.5;
        if (dx * dx + dz * dz > radius * radius) return false;
        double bladeBottom = Math.min(previous.y(), current.y()) - vertical * scale;
        double bladeTop = Math.max(previous.y(), current.y()) + vertical * scale;
        return target.getY() + target.getBbHeight() >= bladeBottom && target.getY() <= bladeTop;
    }

    /** Position exacte d'une des 292 lames du renderer à une progression donnée. */
    private static ArcBladePose arcBladePose(double x, double y, double z, double dirX, double dirZ,
                                             double scale, double t, int bladeId) {
        if (bladeId < 1000) {
            int jaw = bladeId / 58, part = bladeId % 58;
            double charge = contactEaseInOut((t - 0.02) / 0.26);
            double release = contactEaseOut((t - 0.3125) / 0.11);
            double radius = 2.45 + Math.sin(charge * Math.PI) * 0.35 + release * 0.55;
            double yaw = jaw * Math.PI + Math.sin(charge * Math.PI) * 0.25;
            double angle = yaw - 1.18 + part / 57.0 * 2.36;
            double f = Math.cos(angle) * radius;
            double l = Math.sin(angle) * radius * release
                    + (jaw == 0 ? -0.45 : 0.45) * (1.0 - release);
            double localY = 0.40 + jaw * 0.32 + Math.sin(angle) * radius * (1.0 - release) * 0.88;
            return new ArcBladePose(x + (dirX * f - dirZ * l) * scale,
                    y + localY * scale, z + (dirZ * f + dirX * l) * scale);
        }
        if (bladeId < 2000) {
            int part = bladeId - 1000;
            double release = contactEaseOut((t - 0.3125) / 0.11);
            double radius = (0.72 + release * 4.85) * scale;
            double angle = part * Math.PI * 2.0 / 96.0 + release * 0.65;
            return new ArcBladePose(x + Math.cos(angle) * radius,
                    y + 0.20 * scale, z + Math.sin(angle) * radius);
        }
        int part = bladeId - 2000;
        double counter = contactEaseOut((t - 0.43) / 0.10);
        double radius = (0.95 + counter * 4.30) * scale;
        double angle = -part * Math.PI * 2.0 / 80.0 - counter * 0.90;
        double localY = 0.72 + Math.sin(angle * 2.0) * 0.10;
        return new ArcBladePose(x + Math.cos(angle) * radius,
                y + localY * scale, z + Math.sin(angle) * radius);
    }

    private static void hitArcSweep(ServerLevel level, int attackerId, List<LivingEntity> targets,
                                    ArcBladePose previous, ArcBladePose current, float amount, float scale,
                                    int bladeId, Set<ArcBladeHit> hitIds, Set<Integer> hitTargets) {
        for (LivingEntity target : targets) {
            if (!sweptBladeHit(target, previous, current, 0.36, 0.42, scale)) continue;
            if (hurtArcBladeContact(level, attackerId, target, amount, bladeId, hitIds)) {
                hitTargets.add(target.getId());
            }
        }
    }

    /** Un seul recul radial par tick touché, quelle que soit la quantité de lames cumulées. */
    private static void pushArcContact(LivingEntity target, double x, double z,
                                       double dirX, double dirZ, double scale,
                                       long holdExpiry, Set<Integer> metalHeld) {
        if (!target.isAlive()) return;
        // Le hold de charge annulait le recul au tick suivant. Libérer SEULEMENT celui de cet Arc.
        if (metalHeld.contains(target.getId())) {
            MovementHold active = MOVEMENT_HOLDS.get(target.getId());
            if (active != null && active.expires() == holdExpiry) releaseMovementHold(target, holdExpiry);
            metalHeld.remove(target.getId());
        }
        double dx = target.getX() - x, dz = target.getZ() - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double centerForce = 1.0 - Math.min(1.0, distance / Math.max(1.0, 5.5 * scale));
        if (distance < 0.001) { dx = dirX; dz = dirZ; distance = 1.0; }
        double force = 0.16 + 0.34 * centerForce;
        target.push(dx / distance * force, 0.08 + 0.08 * centerForce, dz / distance * force);
        target.hurtMarked = true;
    }

    private static void tickRazorBlade(ServerLevel level, int attackerId,
                                       double x, double y, double z, double dirX, double dirZ,
                                       int age, float amount, float scale, Set<Integer> hitIds) {
        double t = age / 48.0;
        if (t > 0.88) return;
        double rush = contactEaseOut(t / 0.14);
        double centerX = x - dirX * 4.0 * scale * (1.0 - rush);
        double centerZ = z - dirZ * 4.0 * scale * (1.0 - rush);
        double anticipation = contactSmooth(t / 0.12);
        double draw = contactEaseIn((t - 0.12) / 0.13);
        double recoil = contactEaseOut((t - 0.25) / 0.20);
        double bladeBase = -2.15 - anticipation * 0.28 + draw * 3.95 - recoil * 0.45;
        double angle = Math.atan2(dirZ, dirX);
        if (t > 0.25) angle += contactEaseInOut((t - 0.25) / 0.13) * 1.65;
        double root = bladeBase + 0.12;
        double rootX = centerX + Math.cos(angle) * root * scale;
        double rootZ = centerZ + Math.sin(angle) * root * scale;
        for (LivingEntity target : metalContactTargets(level, attackerId, centerX, y, centerZ, 6.0 * scale)) {
            if (!bladeSegmentHit(target, rootX, y - 1.15 * scale, rootZ,
                    angle, 3.30, 0.34, scale)) continue;
            if (hurtMetalContact(level, attackerId, target, amount, true, hitIds)) {
                // Projection légère, vers l'ennemi et non vers le joueur.
                target.push(dirX * 0.28, 0.12, dirZ * 0.28);
                target.hurtMarked = true;
            }
        }
    }

    private static void tickPrismBlades(ServerLevel level, int attackerId,
                                        double x, double y, double z, double dirX, double dirZ,
                                        int age, float amount, float scale, Set<Integer> hitIds) {
        double t = age / 84.0;
        if (t > 0.43) return;
        double converge = contactEaseIn((t - 0.08) / 0.22);
        double strike = contactEaseOut((t - 0.335) / 0.065);
        double radius = (3.05 + (1.65 - 3.05) * converge + strike * 0.72) * scale;
        double spin = 0.85 * contactEaseOut(t / 0.24) + 0.35 * contactSmooth((t - 0.22) / 0.10);
        for (LivingEntity target : metalContactTargets(level, attackerId, x, y, z, 5.0 * scale)) {
            for (int blade = 0; blade < 3; blade++) {
                double angle = blade * Math.PI * 2.0 / 3.0 + spin * 0.34;
                double bladeX = dirX * Math.cos(angle) - dirZ * Math.sin(angle);
                double bladeZ = dirZ * Math.cos(angle) + dirX * Math.sin(angle);
                double outerX = x + bladeX * radius, outerZ = z + bladeZ * radius;
                // prismBlade dessine du rayon extérieur vers radius - 1.62 (lame entrante).
                if (bladeSegmentHit(target, outerX, y - 1.05 * scale, outerZ,
                        Math.atan2(-bladeZ, -bladeX), 1.62, 0.42, scale)) {
                    hurtMetalContact(level, attackerId, target, amount, false, hitIds);
                    break;
                }
            }
        }
    }

    private static void tickArcBlades(ServerLevel level, int attackerId,
                                      double x, double y, double z, double dirX, double dirZ,
                                      int age, float amount, float scale, Set<ArcBladeHit> hitIds,
                                      long holdExpiry, Set<Integer> metalHeld) {
        double t = age / 96.0, previousT = Math.max(0.0, (age - 1) / 96.0);
        double charge = contactEaseInOut((t - 0.02) / 0.26);
        double release = contactEaseOut((t - 0.3125) / 0.11);
        double counter = contactEaseOut((t - 0.43) / 0.10);
        List<LivingEntity> targets = metalContactTargets(level, attackerId, x, y, z, 6.0 * scale);
        Set<Integer> hitTargets = new HashSet<>();

        // Deux cisailles centrales ferment le trou des anneaux : le mob au centre est toujours coupé.
        if (t >= 0.04 && t <= 0.47) {
            double coreRadius = 0.95 + charge * 0.45;
            for (int core = 0; core < 2; core++) {
                double coreY = y + (0.35 + core * 0.32) * scale;
                for (LivingEntity target : targets) {
                    if (bladePointHit(target, x, coreY, z, coreRadius, 0.72, scale)
                            && hurtArcBladeContact(level, attackerId, target, amount,
                                    3000 + core, hitIds)) {
                        hitTargets.add(target.getId());
                    }
                }
            }
        }

        // Mâchoires : chacun des 116 maillons balaie son segment précédent → actuel.
        if (t >= 0.02 && t <= 0.46) {
            for (int bladeId = 0; bladeId < 116; bladeId++) {
                ArcBladePose current = arcBladePose(x, y, z, dirX, dirZ, scale, t, bladeId);
                ArcBladePose previous = previousT >= 0.02
                        ? arcBladePose(x, y, z, dirX, dirZ, scale, previousT, bladeId) : current;
                hitArcSweep(level, attackerId, targets, previous, current, amount, scale,
                        bladeId, hitIds, hitTargets);
            }
        }
        // Front principal : collision continue des 96 lames, y compris entre deux ticks rapides.
        if (release > 0.0 && t < 0.47) {
            for (int bladeId = 1000; bladeId < 1096; bladeId++) {
                ArcBladePose current = arcBladePose(x, y, z, dirX, dirZ, scale, t, bladeId);
                ArcBladePose previous = previousT > 0.3125
                        ? arcBladePose(x, y, z, dirX, dirZ, scale, previousT, bladeId) : current;
                hitArcSweep(level, attackerId, targets, previous, current, amount, scale,
                        bladeId, hitIds, hitTargets);
            }
        }
        // Contre-arc : les 80 mini-lames obliques utilisent le même volume balayé.
        if (counter > 0.0 && t < 0.59) {
            for (int bladeId = 2000; bladeId < 2080; bladeId++) {
                ArcBladePose current = arcBladePose(x, y, z, dirX, dirZ, scale, t, bladeId);
                ArcBladePose previous = previousT > 0.43
                        ? arcBladePose(x, y, z, dirX, dirZ, scale, previousT, bladeId) : current;
                hitArcSweep(level, attackerId, targets, previous, current, amount, scale,
                        bladeId, hitIds, hitTargets);
            }
        }
        // Les dégâts se cumulent par lame ; le recul reste lisible et plus fort près du centre.
        for (LivingEntity target : targets) {
            if (hitTargets.contains(target.getId())) {
                pushArcContact(target, x, z, dirX, dirZ, scale, holdExpiry, metalHeld);
            }
        }
    }

    /** Les contrôles Métal ignorent joueurs, boss et Warden et n'écrasent jamais une emprise existante. */
    private static boolean tryMetalHold(LivingEntity target, long expiry, boolean fullStun) {
        if (!(target instanceof Enemy) || target.getType() == net.minecraft.world.entity.EntityType.ENDER_DRAGON
                || target.getType() == net.minecraft.world.entity.EntityType.WITHER
                || target.getType() == net.minecraft.world.entity.EntityType.WARDEN
                || MOVEMENT_HOLDS.containsKey(target.getId())) return false;
        applyMovementHold(target, expiry, fullStun);
        return true;
    }

    /** Un hit de contact conserve le crédit du tueur et le verrou anti-récursion. */
    private static boolean hurtMetalEntity(ServerLevel sl, LivingEntity attacker,
                                           LivingEntity target, float amount, boolean sharp) {
        if (!target.isAlive() || target.level() != sl || !(amount > 0.0f)) return false;
        if (!sharp) return hurtWithCapturedWeapon(sl, attacker == null ? -1 : attacker.getId(), target, amount);
        DamageSource source = attacker != null
                ? sl.damageSources().indirectMagic(attacker, attacker) : sl.damageSources().magic();
        INTERNAL_SIGNATURE_DAMAGE.add(target.getId());
        try {
            target.invulnerableTime = 0;
            return target.hurt(source, amount);
        } finally {
            INTERNAL_SIGNATURE_DAMAGE.remove(target.getId());
        }
    }

    private static void metalSound(ServerLevel sl, double x, double y, double z,
                                   int now, int delay, SoundEvent sound, float volume, float pitch) {
        TestProcedure.schedule(sl, now + delay,
                () -> sl.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch));
    }

    /** Une seule piste sonore par activation, même lorsque Seismic enterre plusieurs ennemis. */
    private static void scheduleEarthSoundtrack(ServerLevel sl, LivingEntity target,
                                                WeaponVisualEffectId fx) {
        int now = TestProcedure.currentTick(sl);
        double x = target.getX(), y = target.getY(), z = target.getZ();
        if (fx == WeaponVisualEffectId.TITAN_GRASP) {
            earthSound(sl, x, y, z, now, 0, SoundEvents.WARDEN_EMERGE, 0.72f, 0.55f / 1.2f); // awaken.
            earthSound(sl, x, y, z, now, 8, SoundEvents.WARDEN_EMERGE, 0.88f, 0.82f); // main hors de terre.
            earthSound(sl, x, y, z, now, 10, SoundEvents.GRASS_BREAK, 0.62f, 0.58f);
            for (int delay = 120; delay < 312; delay += 40) {
                earthSound(sl, x, y, z, now, delay, SoundEvents.ROOTED_DIRT_BREAK, 0.20f, 0.50f);
            }
            earthSound(sl, x, y, z, now, 312, SoundEvents.GRASS_BREAK, 0.44f, 0.82f);
            return;
        }
        if (fx == WeaponVisualEffectId.SEISMIC_TECTONIC) {
            earthSound(sl, x, y, z, now, 0, SoundEvents.WARDEN_EMERGE, 0.64f, 0.55f / 1.2f); // awaken.
            earthSound(sl, x, y, z, now, 4, SoundEvents.GRASS_BREAK, 0.68f, 0.54f);
            earthSound(sl, x, y, z, now, 12, SoundEvents.GENERIC_EXPLODE.value(), 0.40f, 0.70f);
            earthSound(sl, x, y, z, now, 30, SoundEvents.ROOTED_DIRT_BREAK, 0.34f, 0.62f);
            return;
        }
        if (fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) {
            earthSound(sl, x, y, z, now, 80, SoundEvents.GENERIC_EXPLODE.value(), 0.56f, 0.58f);
            earthSound(sl, x, y, z, now, 80, SoundEvents.GRASS_BREAK, 0.72f, 0.52f);
            earthSound(sl, x, y, z, now, 80, SoundEvents.WARDEN_STEP, 0.52f, 0.64f);
            earthSound(sl, x, y, z, now, 90, SoundEvents.WARDEN_STEP, 0.34f, 0.72f);
            earthSound(sl, x, y, z, now, 100, SoundEvents.ROOTED_DIRT_BREAK, 0.40f, 0.76f);
            earthSound(sl, x, y, z, now, 110, SoundEvents.GRASS_BREAK, 0.26f, 0.50f);
        }
    }

    private static void earthSound(ServerLevel sl, double x, double y, double z,
                                   int now, int delay, SoundEvent sound, float volume, float pitch) {
        TestProcedure.schedule(sl, now + delay,
                () -> sl.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch));
    }

    /** TITAN : trois blocs sous terre, dix secondes de maintien après l'enfouissement, puis sortie. */
    private static void applyTitanGameplay(LivingEntity attacker, LivingEntity target,
                                           ServerLevel sl, float weaponDamage) {
        long gameNow = sl.getGameTime();
        long holdEndGame = gameNow + 312; // 112 ticks de chorégraphie + 200 ticks entièrement enterré.
        int scheduleNow = TestProcedure.currentTick(sl);
        int attackerId = attacker == null ? -1 : attacker.getId();
        double originalY = target.getY();
        applyMovementHold(target, holdEndGame, true, originalY, originalY, gameNow, 3.0);

        // Vingt petits pulses, uniquement pendant les dix secondes passées à trois blocs sous terre.
        float pulseDamage = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.020f, 0.04f, 0.20f);
        for (int delay = 112; delay < 312; delay += 10) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
                if (hold == null || hold.expires() != holdEndGame) return;
                hurtWithCapturedWeapon(sl, attackerId, target, pulseDamage);
            });
        }
        TestProcedure.schedule(sl, scheduleNow + 312, () -> releaseMovementHold(target, holdEndGame));
    }

    /** RALLY : tous les mobs du cercle sont gelés, puis libérés et propulsés au tick 80. */
    private static void applyRallyGameplay(LivingEntity target, ServerLevel sl) {
        double x = target.getX(), y = target.getY(), z = target.getZ();
        double radius = 1.80; // Rayon exact du cercle lumineux complètement déployé.
        int scheduleNow = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        long freezeExpiry = gameNow + 81; // Libération programmée à 80, avant l'expiration automatique.
        List<LivingEntity> rallied = new ArrayList<>();
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius))) {
            if (!candidate.isAlive() || candidate.distanceToSqr(x, y, z) > radius * radius) continue;
            // Tous les mobs sont concernés ; le joueur qui déclenche le sceau reste libre.
            if (!(candidate instanceof Mob)) continue;
            if (candidate instanceof Enemy) {
                candidate.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        600, 0, false, true, true)); // Weakness I, 30 secondes.
            }
            rallied.add(candidate);
            applyMovementHold(candidate, freezeExpiry, true);
        }

        TestProcedure.schedule(sl, scheduleNow + 80, () -> {
            for (LivingEntity entity : rallied) {
                if (!entity.isAlive() || entity.level() != sl) continue;
                MovementHold hold = MOVEMENT_HOLDS.get(entity.getId());
                if (hold == null || hold.expires() != freezeExpiry) continue;
                // L'IA et le contrôle sont rendus AVANT la propulsion : plus aucun maintien Rally.
                releaseMovementHold(entity, freezeExpiry);
                launchRallyEntity(entity);
            }
        });
    }

    private static final double RALLY_LAUNCH_VELOCITY = upwardVelocityForHeight(30.0);

    /** Vélocité vanilla approximant une apex de 30 blocs, sans téléportation ni maintien horizontal. */
    private static void launchRallyEntity(LivingEntity entity) {
        entity.setDeltaMovement(0.0, RALLY_LAUNCH_VELOCITY, 0.0);
        entity.fallDistance = 0.0f;
        entity.hurtMarked = true;
    }

    private static double upwardVelocityForHeight(double wantedHeight) {
        double low = 0.0, high = 5.0;
        for (int iteration = 0; iteration < 32; iteration++) {
            double velocity = (low + high) * 0.5;
            double height = 0.0, apex = 0.0;
            for (int tick = 0; tick < 200 && velocity > 0.0; tick++) {
                height += velocity;
                apex = Math.max(apex, height);
                velocity = (velocity - 0.08) * 0.98; // Gravité et friction vanilla des LivingEntity.
            }
            if (apex < wantedHeight) low = (low + high) * 0.5;
            else high = (low + high) * 0.5;
        }
        return high;
    }

    /** SEISMIC : enterre uniquement les ennemis au contact ou très proches de la fissure centrale. */
    private static void applySeismicToNearbyEnemies(LivingEntity attacker, LivingEntity target,
                                                    ServerLevel sl, float weaponDamage) {
        double x = target.getX(), y = target.getY(), z = target.getZ();
        double radius = 2.0;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius))) {
            // Enemy exclut les animaux passifs, les animaux apprivoisés et les joueurs.
            if (!(candidate instanceof Enemy) || !candidate.isAlive()
                    || candidate.distanceToSqr(x, y, z) > radius * radius) continue;
            applySeismicGameplay(attacker, candidate, sl, weaponDamage);
        }
    }

    /** SEISMIC : enfouissement temporaire, faibles dégâts rythmés puis restitution et Lenteur III. */
    private static void applySeismicGameplay(LivingEntity attacker, LivingEntity target,
                                             ServerLevel sl, float weaponDamage) {
        long gameNow = sl.getGameTime();
        long holdEndGame = gameNow + 112;
        int scheduleNow = TestProcedure.currentTick(sl);
        int attackerId = attacker == null ? -1 : attacker.getId();
        double originalY = target.getY();
        applyMovementHold(target, holdEndGame, true, originalY - 1.0, originalY);

        // Dix-huit petits pulses sur 5,6 s, bornés pour rester faibles même avec une grosse arme.
        float pulseDamage = net.minecraft.util.Mth.clamp(Math.max(0.0f, weaponDamage) * 0.030f, 0.06f, 0.30f);
        for (int delay = 6; delay < 112; delay += 6) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
                if (hold == null || hold.expires() != holdEndGame) return;
                hurtWithCapturedWeapon(sl, attackerId, target, pulseDamage);
            });
        }
        TestProcedure.schedule(sl, scheduleNow + 112, () -> {
            if (!target.isAlive() || target.level() != sl) return;
            MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
            // Une autre emprise a remplacé Seismic : ne pas la libérer ni appliquer son aftermath.
            if (hold != null && hold.expires() != holdEndGame) return;
            if (hold != null) releaseMovementHold(target, holdEndGame);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    600, 2, false, true, true)); // 30 s, amplifier 2 = Lenteur III.
        });
    }

    // ================================================================================================
    // VENT — réactions des mobs, dégâts répartis sur plusieurs ticks, sons du jeu
    // ================================================================================================

    /** Aiguillage des trois signatures de vent vers leur gameplay. */
    // ============================================================================================
    // 水 EAU (T375) — raz-de-marée, maelström abyssal, givre du permafrost
    // ============================================================================================

    /**
     * Routage des trois signatures d'eau. Jusqu'ici l'élément n'avait <b>aucun</b> gameplay : les
     * animations duraient moins d'une seconde et ne faisaient rien aux mobs. Chacune a maintenant
     * son comportement, construit sur les mêmes briques que les familles déjà refondues
     * (emprises, zones, pulses d'arme capturée, sons vanilla).
     */
    private static void applyWaterGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.HYDRO_TIDAL_SURGE) {
            applySurgeGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.ABYSSAL_MAELSTROM) {
            applyMaelstromGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.PERMAFROST_RIME) {
            applyRimeGameplay(attacker, target, sl, damage);
        }
    }

    /**
     * RAZ-DE-MARÉE (104 ticks) : le mur d'eau <b>marche vraiment</b>. Un front large de 3,6 blocs
     * se déplace de 3,4 blocs derrière la cible jusqu'à 2,8 blocs devant elle en douze passes
     * (ticks 12 → 56).
     *
     * <p><b>Ce que fait le front.</b> Il n'applique pas une impulsion : il <b>embarque</b>. Toute
     * créature dans la bande {@code |along| ≤ 1,70 && |side| ≤ 3,60} est inscrite dans
     * {@link #SURGE_RIDES} et devient un « surfeur » poussé à chaque tick par
     * {@link #tickSurgeRide} pendant 26 à 42 ticks — durée tirée au sort pour elle.</p>
     *
     * <p><b>Pourquoi ce changement.</b> La première version donnait douze poussées identiques
     * espacées de quatre ticks. Entre deux poussées le mob retombait, reprenait son IA et se
     * retournait ; comme la poussée valait {@code (1,15 ; 0,52)} pour tout le monde, les mobs
     * avançaient groupés, au même rythme, exactement sur le même patron. Chaque surfeur a
     * maintenant sa vitesse (0,62 à 1,08), sa portance (0,26 à 0,50), sa dérive latérale
     * (± 0,08 à 0,38), sa phase de ballottement, son sens de culbute et sa durée de transport :
     * ils s'entremêlent, se doublent, et ne sont pas <b>jetés</b> au même instant.</p>
     *
     * <p><b>Verrou mou.</b> L'IA est coupée pendant le transport — sans cela le mob nage à
     * contre-courant — mais la position n'est jamais imposée : il garde sa physique, bute contre
     * les murs et peut tomber dans un trou. C'est volontairement l'inverse du maelström, qui
     * impose une hélice par {@code teleportTo}.</p>
     */
    private static void applySurgeGameplay(LivingEntity attacker, LivingEntity target,
                                           ServerLevel sl, float weaponDamage) {
        final int scheduleNow = TestProcedure.currentTick(sl);
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final double cx = target.getX();
        final double cy = target.getY();
        final double cz = target.getZ();
        // Les mobs déjà frappés par CE mur : l'AoE ne blesse qu'une fois, mais pousse à chaque passe.
        final java.util.Set<Integer> struck = java.util.concurrent.ConcurrentHashMap.newKeySet();
        // Direction de marche : attaquant → cible (la vague traverse la cible et continue).
        double rawX = attacker == null ? target.getLookAngle().x : cx - attacker.getX();
        double rawZ = attacker == null ? target.getLookAngle().z : cz - attacker.getZ();
        double norm = Math.sqrt(rawX * rawX + rawZ * rawZ);
        final double dirX = norm < 0.05 ? 0.0 : rawX / norm;
        final double dirZ = norm < 0.05 ? 1.0 : rawZ / norm;
        final float pulse = net.minecraft.util.Mth.clamp(weaponDamage * 0.085f, 0.05f, 0.55f);

        sl.playSound(null, cx, cy, cz, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.80f, 0.70f);
        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§3[Raz-de-marée] §fmur d'eau en marche §7— 6,2 blocs de course · front de 3,6 · "
                            + "les mobs happés SURFENT 26-42 ticks puis sont jetés"), true);
        }

        // Pas de décapage du sol pour le raz-de-marée : une vague DÉFERLE, elle ne creuse pas.
        // (GhostBlockMask.scourLine reste disponible, mais n'est plus appelé ici.)

        final int passes = 12;
        for (int pass = 0; pass < passes; pass++) {
            final float progress = pass / (float) (passes - 1);
            final int delay = 12 + pass * 4;
            final double front = -3.40 + progress * 6.20;
            final boolean breaking = pass >= 6;
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                double fx2 = cx + dirX * front;
                double fz2 = cz + dirZ * front;
                sl.playSound(null, fx2, cy, fz2, breaking ? SoundEvents.AMBIENT_UNDERWATER_EXIT
                        : SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.42f, 0.78f);
                // Mur d'eau visible sur toute la largeur du front, à chaque passe.
                PixelFx.send(sl, ParticleTypes.SPLASH, fx2, cy + 0.40, fz2,
                        18, 1.70 * Math.abs(dirZ) + 0.30, 0.90, 1.70 * Math.abs(dirX) + 0.30, 0.09);
                PixelFx.send(sl, ParticleTypes.BUBBLE, fx2, cy - 0.10, fz2,
                        10, 1.40, 0.35, 1.40, 0.05);
                long gameTick = sl.getGameTime();
                for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(fx2 - 3.60, cy - 1.60, fz2 - 3.60,
                                fx2 + 3.60, cy + 4.60, fz2 + 3.60))) {
                    if (!victim.isAlive() || victim instanceof Player) continue;
                    if (attackerId >= 0 && victim.getId() == attackerId) continue;
                    // Le front est une BANDE : seules les créatures que le mur touche à CETTE passe
                    // sont emportées, pas tout le cylindre (sinon la vague n'avance plus, elle gifle).
                    double rx = victim.getX() - fx2, rz = victim.getZ() - fz2;
                    double along = rx * dirX + rz * dirZ;
                    double side = -rx * dirZ + rz * dirX;
                    // Mur épais : 2,60 d'épaisseur sur 3,90 de demi-largeur. Tout ce que le mur
                    // traverse est touché — c'est une AoE, pas un cône de tir.
                    if (Math.abs(along) > 2.60 || Math.abs(side) > 3.90) continue;
                    victim.clearFire();

                    // ---- AoE : la vague POUSSE tout ce qu'elle touche, surfeur ou non ----
                    // Le mob recule avec le mur même s'il ne peut pas être embarqué (déjà jeté,
                    // tenu par une tornade…). Le poids de la poussée suit la place dans le mur :
                    // de plein fouet au centre, effleuré sur les bords.
                    double head = 1.0 - Math.min(1.0, Math.abs(side) / 3.90);
                    double shove = (breaking ? 0.95 : 0.62) * (0.45 + 0.55 * head);
                    Vec3 mv = victim.getDeltaMovement();
                    victim.setDeltaMovement(mv.x * 0.30 + dirX * shove,
                            Math.max(mv.y, 0.16 + 0.22 * head),
                            mv.z * 0.30 + dirZ * shove);
                    victim.fallDistance = 0.0f;
                    victim.hurtMarked = true;
                    if (struck.add(victim.getId())) {
                        // Première rencontre avec le mur : c'est là que la vague blesse.
                        hurtWithCapturedWeapon(sl, attackerId, victim, pulse);
                        PixelFx.send(sl, ParticleTypes.SPLASH, victim.getX(),
                                victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                                12, 0.34, 0.30, 0.34, 0.14);
                    }

                    if (SURGE_RIDES.containsKey(victim.getId())) continue;   // déjà porté
                    Long cooldown = SURGE_RECAPTURE.get(victim.getId());
                    if (cooldown != null && gameTick < cooldown) continue;   // vient d'être jeté
                    if (WIND_HOLDS.containsKey(victim.getId())) continue;    // tenu par une tornade
                    var rnd = victim.getRandom();
                    // ---- paramètres PROPRES À CE MOB : deux victimes ne surfent jamais pareil ----
                    double speed = 0.62 + rnd.nextDouble() * 0.46;          // 0,62 → 1,08 bloc/tick
                    double lift = 0.26 + rnd.nextDouble() * 0.24;           // portance de la crête
                    double drift = (rnd.nextDouble() * 0.30 + 0.08)         // dérive latérale
                            * (rnd.nextBoolean() ? 1.0 : -1.0);
                    double bob = rnd.nextDouble() * Math.PI * 2.0;          // phase de ballottement
                    double roll = (rnd.nextDouble() * 14.0 + 6.0)           // culbute dans l'eau
                            * (rnd.nextBoolean() ? 1.0 : -1.0);
                    int carry = 26 + rnd.nextInt(17);                       // 26 → 42 ticks portés
                    rememberSignatureAi(victim);
                    MOVEMENT_HOLDS.remove(victim.getId());
                    SURGE_RIDES.put(victim.getId(), new SurgeRide(gameTick + carry, gameTick,
                            dirX, dirZ, speed, lift, drift, bob, roll, pulse, attackerId, 9));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1, false, true, true));
                    // (les dégâts ont déjà été appliqués par l'AoE ci-dessus, au premier contact)
                    // Gerbe d'embarquement, centrée sur le mob : on voit QUI vient d'être happé.
                    PixelFx.send(sl, ParticleTypes.SPLASH, victim.getX(),
                            victim.getY() + victim.getBbHeight() * 0.65, victim.getZ(),
                            14, 0.30, 0.26, 0.30, 0.16);
                    sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.45f, 0.95f);
                }
            });
        }
        // Traîne : la nappe qui suit le mur continue de pousser VERS L'AVANT, doucement. Elle ne
        // ramène plus personne en arrière — une vague ne recrache pas ses victimes vers le tireur.
        TestProcedure.schedule(sl, scheduleNow + 74, () -> {
            sl.playSound(null, cx, cy, cz, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.PLAYERS, 0.70f, 0.58f);
            PixelFx.send(sl, ParticleTypes.CURRENT_DOWN, cx, cy - 0.60, cz, 26, 2.60, 0.25, 2.60, 0.02);
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cx - 5.0, cy - 2.0, cz - 5.0, cx + 5.0, cy + 3.0, cz + 5.0))) {
                if (!victim.isAlive() || victim instanceof Player) continue;
                if (attackerId >= 0 && victim.getId() == attackerId) continue;
                if (SURGE_RIDES.containsKey(victim.getId())) continue;
                victim.setDeltaMovement(dirX * 0.38, 0.16, dirZ * 0.38);
                victim.hurtMarked = true;
            }
        });
        TestProcedure.schedule(sl, scheduleNow + 104, () -> {
            sl.playSound(null, cx, cy, cz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.55f, 0.62f);
            PixelFx.send(sl, ParticleTypes.SPLASH, cx, cy, cz, 20, 2.20, 0.30, 2.20, 0.05);
        });
    }

    /**
     * Transport : appelé à chaque tick pour chaque mob porté par la vague.
     *
     * <p>Trois forces se composent — l'entraînement dans l'axe de marche, la dérive latérale (un
     * mob n'est jamais poussé exactement droit dans une vague réelle), et la portance de la crête
     * qui monte puis retombe en {@code sin(u·π)}. Le mob tourne aussi sur lui-même
     * ({@code setYRot}) : c'est ce qui donne l'impression d'être <b>roulé</b> plutôt que poussé.</p>
     *
     * <p>Aucun {@code teleportTo} ici, volontairement : le mob conserve sa physique, il peut donc
     * être bloqué par un mur, tomber dans un trou ou percuter un autre mob — la vague le <i>pousse</i>,
     * elle ne le <i>téléporte</i> pas.</p>
     */
    private static void tickSurgeRide(LivingEntity target) {
        SurgeRide ride = SURGE_RIDES.get(target.getId());
        if (ride == null) return;
        long now = target.level().getGameTime();
        if (!target.isAlive() || now >= ride.expires()) {
            releaseSurgeRide(target, ride);
            return;
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        target.stopUsingItem();
        double elapsed = now - ride.started();
        double span = Math.max(1.0, (double) (ride.expires() - ride.started()));
        double u = Math.max(0.0, Math.min(1.0, elapsed / span));
        // Entraînement : fort à l'avant du transport, il faiblit quand la vague se couche.
        double drive = ride.speed() * (0.55 + 0.45 * Math.sin(Math.min(1.0, u * 1.15) * Math.PI));
        double swayX = -ride.dirZ(), swayZ = ride.dirX();
        double sway = ride.drift() * Math.sin(elapsed * 0.31 + ride.bob());
        double vx = ride.dirX() * drive + swayX * sway;
        double vz = ride.dirZ() * drive + swayZ * sway;
        // Portance : porté sur la crête, puis reposé. Le + 0,04·sin donne le ballottement.
        double vy = ride.lift() * Math.sin(u * Math.PI) + 0.04 * Math.sin(elapsed * 0.44 + ride.bob());
        target.setDeltaMovement(vx, Math.max(vy, target.getDeltaMovement().y * 0.35), vz);
        target.setYRot((float) (target.getYRot() + ride.roll() * 0.35));
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
        if (target.level() instanceof ServerLevel sl) {
            if (elapsed % 3 == 0) {
                PixelFx.send(sl, ParticleTypes.SPLASH, target.getX(),
                        target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                        4, 0.26, 0.18, 0.26, 0.05);
            }
            if (elapsed % 7 == 0) {
                PixelFx.send(sl, ParticleTypes.BUBBLE, target.getX(),
                        target.getY() + target.getBbHeight() * 0.30, target.getZ(),
                        3, 0.22, 0.20, 0.22, 0.02);
            }
            if (ride.period() > 0 && elapsed > 0 && elapsed % ride.period() == 0 && ride.pulse() > 0.0f) {
                hurtWithCapturedWeapon(sl, ride.attackerId(), target, ride.pulse());
            }
        }
    }

    /**
     * Fin du transport : la vague <b>jette</b> ce qu'elle portait. La projection a lieu quand la
     * durée tirée au sort à l'embarquement expire — donc à un instant différent pour chaque mob,
     * ce qui évite l'effet « tout le monde décolle à la même frame ».
     *
     * <p>Elle est dirigée vers l'avant de la vague (c'est une lame de fond, pas une explosion) avec
     * un écart angulaire aléatoire de ±0,45 rad, une force horizontale de 1,45 à 2,30 et une
     * portance de 0,70 à 1,10. {@code fallDistance} est remis à zéro juste avant : toute la chute
     * qui suit est comptée par le jeu et ajoute ses propres dégâts.</p>
     */
    private static void releaseSurgeRide(LivingEntity target, SurgeRide ride) {
        SURGE_RIDES.remove(target.getId());
        refreshSignatureAi(target);
        if (!target.isAlive()) return;
        var rnd = target.getRandom();
        double spread = (rnd.nextDouble() - 0.5) * 0.90;
        double cos = Math.cos(spread), sin = Math.sin(spread);
        double ejectX = ride.dirX() * cos - ride.dirZ() * sin;
        double ejectZ = ride.dirX() * sin + ride.dirZ() * cos;
        double power = 1.45 + rnd.nextDouble() * 0.85;
        double lift = 0.70 + rnd.nextDouble() * 0.40;
        target.setDeltaMovement(ejectX * power, lift, ejectZ * power);
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, false, true, true));
        SURGE_RECAPTURE.put(target.getId(), target.level().getGameTime() + 45);
        if (target.level() instanceof ServerLevel sl) {
            PixelFx.send(sl, ParticleTypes.SPLASH, target.getX(),
                    target.getY() + target.getBbHeight() * 0.50, target.getZ(),
                    22, 0.34, 0.30, 0.34, 0.22);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.60f, 0.72f);
            if (ride.pulse() > 0.0f) {
                hurtWithCapturedWeapon(sl, ride.attackerId(), target, Math.min(3.2f, ride.pulse() * 2.2f));
            }
        }
    }

    /**
     * MAELSTRÖM ABYSSAL (150 ticks) : même machinerie de zone que la tornade — aspiration continue,
     * hélices toutes différentes — mais réglée en <b>entonnoir</b> : rayon 4,35, montée courte de
     * 2,30 (on tourne au ras de l'eau, on ne s'envole pas), rotation rapide 0,42 rad/tick, et
     * surtout {@code converge} : <b>tous</b> les rayons d'arrivée visent la gorge (0,18 à 1,13),
     * chacun le sien, si bien que les mobs se resserrent en spirale au lieu de rester sur la paroi.
     * La libération est de type {@link #RELEASE_GEYSER} : au lieu d'être projetés loin, ils sont
     * <b>recrachés à la verticale</b> (poussée verticale 1,85 à 2,40), et c'est la chute qui fait
     * le reste des dégâts.
     */
    private static void applyMaelstromGameplay(LivingEntity attacker, LivingEntity target,
                                               ServerLevel sl, float weaponDamage) {
        final int scheduleNow = TestProcedure.currentTick(sl);
        final long gameNow = sl.getGameTime();
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final double cx = target.getX();
        final double cy = target.getY() + target.getBbHeight() * 0.5;
        final double cz = target.getZ();
        final double baseY = target.getY();
        final float pulse = net.minecraft.util.Mth.clamp(weaponDamage * 0.075f, 0.05f, 0.50f);

        // ------------------------------------------------------------------------------------
        // LE TROU. Le sol n'est pas cassé : on envoie aux clients à portée un paquet de mise à
        // jour de bloc qui annonce « air », sans toucher au monde (GhostBlockMask). Profil
        // parabolique sur 4,35 blocs de rayon et 3,3 de fond, creusé du centre vers le bord à
        // 3,4 ticks par bloc de distance — soit la même vitesse que la spirale qui l'aspire —
        // puis rebouché à partir du tick 118, bord d'abord (dernière colonne au tick ~129).
        //
        // v1.5 : le rebouchage était calé sur le tick 128, donc la dernière colonne revenait au
        // tick ~139 — soit APRÈS la mort du bassin d'eau (tick 120) et pile au moment où toute
        // l'animation commence son fondu. Le joueur voyait le cratère de terre à nu pendant une
        // seconde et demie. Avancé à 118, le sol réapparaît entre les ticks 118 et 129, c'est-à-
        // dire sous la nappe de fermeture qui est pleinement opaque de 114 à 141 : la terre
        // revient à l'abri des regards, et l'eau ne s'efface qu'ensuite.
        // ------------------------------------------------------------------------------------
        int carved = GhostBlockMask.digFunnel(sl,
                net.minecraft.core.BlockPos.containing(cx, baseY, cz),
                4.35, 3.30, 3,
                gameNow + 14, gameNow + 118, 3.4, 2.6);

        WindZone zone = new WindZone(sl, gameNow, gameNow + 118, cx, cz, baseY,
                4.35, 2.30, 0.42, pulse, 11, attackerId, 55, 100, false, true, RELEASE_GEYSER);
        WIND_ZONES.add(zone);
        int grabbed = captureWindZone(zone, sl);
        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§3[Maelström abyssal] §f" + grabbed + " créature(s) avalée(s) §7— succion 7,2 → "
                            + "capture 4,35 · " + carved + " blocs de sol effacés · geyser au tick 114"), true);
        }

        // ------------------------------------------------------------------------------------
        // L'INTÉRIEUR DU TROU. Le creusement rendait le trou lisible mais vide : on y voyait le
        // mob voler dans du rien. Toutes les 2 ticks on remplit le volume de l'entonnoir — paroi
        // en spirale, gorge, et colonne d'air remontante — avec des particules d'eau réelles.
        // ------------------------------------------------------------------------------------
        for (int delay = 6; delay < 124; delay += 2) {
            final int step = delay;
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                double phase = step * 0.42;                      // même vitesse que la spirale
                double openK = Math.min(1.0, step / 24.0);
                // Paroi : 3 bras en spirale, du bord jusqu'à la gorge.
                for (int arm = 0; arm < 3; arm++) {
                    for (int k = 0; k < 6; k++) {
                        double v = k / 5.0;                      // 0 bord .. 1 gorge
                        double r = 4.35 * (1.0 - v * 0.82) * openK;
                        double ang = phase * (1.0 + v * 2.40) + arm * 2.0944 + v * 10.05;
                        double px = cx + Math.cos(ang) * r;
                        double pz = cz + Math.sin(ang) * r;
                        double py = baseY - 0.10 - 3.30 * v * v * openK;
                        PixelFx.send(sl, ParticleTypes.SPLASH, px, py, pz, 2, 0.10, 0.06, 0.10, 0.02);
                        if (k >= 3) {
                            PixelFx.send(sl, ParticleTypes.BUBBLE, px, py, pz, 2, 0.12, 0.14, 0.12, 0.03);
                        }
                    }
                }
                // Gorge : le fond de l'entonnoir aspire en permanence.
                double gy = baseY - 0.10 - 3.30 * openK;
                PixelFx.send(sl, ParticleTypes.BUBBLE_COLUMN_UP, cx, gy, cz, 6, 0.42, 0.20, 0.42, 0.06);
                PixelFx.send(sl, ParticleTypes.UNDERWATER, cx, gy + 0.60, cz, 8, 0.70, 0.50, 0.70, 0.02);
                // Remontée : l'air arraché au fond repart vers la surface le long de l'axe.
                double climb = (step % 20) / 20.0;
                PixelFx.send(sl, ParticleTypes.BUBBLE, cx, gy + climb * 3.40, cz,
                        5, 0.55 * (1.0 - climb) + 0.10, 0.25, 0.55 * (1.0 - climb) + 0.10, 0.04);
                // Embruns au-dessus du bord : la jonction trou / terrain reste masquée.
                PixelFx.send(sl, ParticleTypes.SPLASH, cx, baseY + 0.25, cz,
                        4, 4.10 * openK, 0.18, 4.10 * openK, 0.03);
            });
        }

        sl.playSound(null, cx, cy, cz, SoundEvents.AMBIENT_UNDERWATER_LOOP, SoundSource.PLAYERS, 0.85f, 0.55f);
        for (int delay = 14; delay < 118; delay += 16) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> sl.playSound(null, cx, cy, cz,
                    SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.PLAYERS, 0.50f, 0.62f));
        }

        // ------------------------------------------------------------------------------------
        // Succion d'approche : la zone ne capture que dans 4,35 blocs, mais un maelström se voit
        // de plus loin. De 4,35 à 7,20 les créatures sont TIRÉES vers la gorge sans être prises :
        // elles arrivent d'elles-mêmes dans la zone de capture. Composante tangentielle ajoutée
        // pour qu'elles entrent en SPIRALE, pas en ligne droite comme attirées par un aimant.
        // ------------------------------------------------------------------------------------
        final double outerPull = 7.20;
        for (int delay = 8; delay < 112; delay += 3) {
            final float ramp = Math.min(1.0f, delay / 40.0f);
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(cx - outerPull, baseY - 3.0, cz - outerPull,
                                cx + outerPull, baseY + 4.0, cz + outerPull))) {
                    if (!victim.isAlive() || victim instanceof Player) continue;
                    if (attackerId >= 0 && victim.getId() == attackerId) continue;
                    if (WIND_HOLDS.containsKey(victim.getId())) continue;   // déjà dans l'entonnoir
                    double rx = victim.getX() - cx, rz = victim.getZ() - cz;
                    double dist = Math.sqrt(rx * rx + rz * rz);
                    if (dist < 4.35 || dist > outerPull) continue;
                    double ux = rx / dist, uz = rz / dist;
                    // Plus on est près du bord de capture, plus ça tire fort.
                    double grip = (0.16 + 0.13 * (1.0 - (dist - 4.35) / (outerPull - 4.35))) * ramp;
                    Vec3 current = victim.getDeltaMovement();
                    victim.setDeltaMovement(
                            current.x - ux * grip - uz * grip * 0.55,
                            Math.max(current.y, -0.12),
                            current.z - uz * grip + ux * grip * 0.55);
                    victim.hurtMarked = true;
                    victim.clearFire();
                }
            });
        }
        // Noyade : tant que le tourbillon tourne, les happés manquent d'air (dégâts déjà donnés par
        // la zone) et sortent étourdis. On ajoute ici la lenteur « eau lourde » sur toute la cuvette.
        for (int delay = 20; delay < 112; delay += 22) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(cx - 4.35, baseY - 2.0, cz - 4.35,
                                cx + 4.35, baseY + 3.0, cz + 4.35))) {
                    if (!victim.isAlive() || victim instanceof Player) continue;
                    if (attackerId >= 0 && victim.getId() == attackerId) continue;
                    victim.clearFire();
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, false, true, true));
                    // Noyade : la réserve d'air descend pour de bon. Les créatures qui respirent
                    // sous l'eau ne sont pas concernées — le jeu leur remet l'air de lui-même.
                    victim.setAirSupply(Math.max(-18, victim.getAirSupply() - 55));
                    PixelFx.send(sl, ParticleTypes.BUBBLE, victim.getX(),
                            victim.getY() + victim.getBbHeight() * 0.70, victim.getZ(),
                            5, 0.22, 0.26, 0.22, 0.04);
                }
                PixelFx.send(sl, ParticleTypes.BUBBLE_COLUMN_UP, cx, baseY + 0.20, cz,
                        14, 1.90, 0.40, 1.90, 0.08);
                PixelFx.send(sl, ParticleTypes.CURRENT_DOWN, cx, baseY + 0.60, cz,
                        18, 2.40, 0.80, 2.40, 0.04);
            });
        }
        // Geyser : la gorge se referme et recrache. Tout ce qui est encore tenu part à la verticale.
        TestProcedure.schedule(sl, scheduleNow + 114, () -> {
            sl.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55f, 1.45f);
            sl.playSound(null, cx, cy, cz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.90f, 0.70f);
            // Colonne d'eau sur les neuf blocs du geyser : le jet se voit même sans regarder le sol.
            for (int step = 0; step < 10; step++) {
                double gy = baseY + step * 0.95;
                PixelFx.send(sl, ParticleTypes.SPLASH, cx, gy, cz,
                        9, 0.45 - step * 0.025, 0.45, 0.45 - step * 0.025, 0.22);
                PixelFx.send(sl, ParticleTypes.BUBBLE_COLUMN_UP, cx, gy, cz,
                        5, 0.34, 0.30, 0.34, 0.16);
            }
            // Rejoue la signature côté client, en réduit, pile sur l'axe du jet.
            broadcastNear(sl, (float) cx, (float) cy, (float) cz,
                    WeaponVisualEffectId.ABYSSAL_MAELSTROM.id, KIND_IMPACT, 5,
                    (float) cx, (float) baseY, (float) cz,
                    (float) cx, (float) (baseY + 6.0), (float) cz, 0, 0.55f);
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cx - 2.60, baseY - 2.0, cz - 2.60, cx + 2.60, baseY + 4.0, cz + 2.60))) {
                if (!victim.isAlive() || victim instanceof Player) continue;
                if (attackerId >= 0 && victim.getId() == attackerId) continue;
                WindHold held = WIND_HOLDS.get(victim.getId());
                if (held != null) {
                    releaseWindHold(victim, held);
                } else {
                    // Pas tenu par l'entonnoir (entré trop tard) : recraché quand même, mais chacun
                    // avec sa force et un léger écart latéral, sinon ils retombent tous au même point.
                    var rnd = victim.getRandom();
                    double scatter = rnd.nextDouble() * Math.PI * 2.0;
                    double push = 0.22 + rnd.nextDouble() * 0.38;
                    victim.setDeltaMovement(Math.cos(scatter) * push,
                            1.55 + rnd.nextDouble() * 0.55, Math.sin(scatter) * push);
                    victim.fallDistance = 0.0f;
                    victim.hurtMarked = true;
                    victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, true, true));
                }
                PixelFx.send(sl, ParticleTypes.SPLASH, victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.50, victim.getZ(),
                        16, 0.30, 0.36, 0.30, 0.24);
                hurtWithCapturedWeapon(sl, attackerId, victim, Math.min(4.5f, pulse * 2.6f));
            }
        });
        TestProcedure.schedule(sl, scheduleNow + 150, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.PLAYERS, 0.60f, 0.80f));
    }

    /**
     * GIVRE DU PERMAFROST (120 ticks) : la cible est <b>prise dans la glace</b> — immobilisation
     * complète 72 ticks (comme la mâchoire d'épines), gel vanilla visible ({@code ticksFrozen}),
     * Lenteur III et Fatigue, pulses de froid toutes les dix ticks — puis la prison
     * <b>éclate</b> au tick 94 : dégâts de rupture, projection, et la couronne de pics blesse tout
     * ce qui se trouve encore dans les 3,6 blocs.
     */
    private static void applyRimeGameplay(LivingEntity attacker, LivingEntity target,
                                          ServerLevel sl, float weaponDamage) {
        final int scheduleNow = TestProcedure.currentTick(sl);
        final long gameNow = sl.getGameTime();
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final double cx = target.getX();
        final double cy = target.getY() + target.getBbHeight() * 0.5;
        final double cz = target.getZ();
        final float pulse = net.minecraft.util.Mth.clamp(weaponDamage * 0.045f, 0.04f, 0.32f);
        // T375-WATER-MOTION : l'emprise expirait au tick 72 alors que la prison n'éclate qu'au
        // tick 94 — la cible avait 22 ticks pour s'enfuir AVANT la rupture, et l'éclatement
        // frappait le vide. Le verrou tient maintenant jusqu'à l'instant de la rupture.
        final long holdEnd = gameNow + 94;

        applyMovementHold(target, holdEnd, true);
        target.clearFire();
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 260));

        // Le sol GÈLE à l'écran — glace compacte au cœur, neige au bord — en suivant l'étoile
        // de givre dessinée par le moteur, puis dégèle de l'extérieur vers le centre. Blocs
        // fantômes : le terrain du joueur n'est pas transformé, il n'y a rien à déneiger après.
        int frosted = GhostBlockMask.freezeSurface(sl,
                net.minecraft.core.BlockPos.containing(cx, target.getY(), cz),
                4.20, 3, gameNow + 2, gameNow + 100, 4.6, 3.2);

        sl.playSound(null, cx, cy, cz, SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 0.85f, 0.55f);
        sl.playSound(null, cx, cy, cz, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.55f, 0.65f);
        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b[Givre du permafrost] §fcible prise dans la glace §7— verrou jusqu'au tick 94 · "
                            + frosted + " blocs de sol gelés · 3 passages de froid = gel"), true);
        }

        // Le gel se propage : tout ce qui entre dans la couronne de pics est ralenti et gelé.
        for (int delay = 10; delay <= 90; delay += 10) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(cx - 3.60, cy - 2.40, cz - 3.60, cx + 3.60, cy + 2.80, cz + 3.60))) {
                    if (!victim.isAlive() || victim instanceof Player) continue;
                    if (attackerId >= 0 && victim.getId() == attackerId) continue;
                    victim.clearFire();
                    victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 200));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                    victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1, false, true, true));
                    hurtWithCapturedWeapon(sl, attackerId, victim, pulse);
                    PixelFx.send(sl, ParticleTypes.SNOWFLAKE, victim.getX(),
                            victim.getY() + victim.getBbHeight() * 0.60, victim.getZ(),
                            6, 0.28, 0.32, 0.28, 0.01);
                    // Exposition : rester dans la couronne ne doit pas seulement ralentir, cela
                    // doit FIGER. Au troisième passage de froid le mob est pris jusqu'à la rupture :
                    // il ne peut plus sortir de la zone avant l'éclatement.
                    int id = victim.getId();
                    int exposure = FROST_EXPOSURE.merge(id, 1, Integer::sum);
                    if (exposure == 3 && victim != target && !MOVEMENT_HOLDS.containsKey(id)
                            && !WIND_HOLDS.containsKey(id) && !SURGE_RIDES.containsKey(id)) {
                        applyMovementHold(victim, holdEnd, true);
                        victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 400));
                        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.50f, 0.80f);
                        PixelFx.send(sl, ParticleTypes.SNOWFLAKE, victim.getX(),
                                victim.getY() + victim.getBbHeight() * 0.50, victim.getZ(),
                                22, 0.34, 0.45, 0.34, 0.03);
                    }
                }
                PixelFx.send(sl, ParticleTypes.SNOWFLAKE, cx, cy - 0.80, cz, 12, 2.40, 0.30, 2.40, 0.01);
            });
        }
        // Craquements avant la rupture.
        TestProcedure.schedule(sl, scheduleNow + 78, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.35f, 0.75f));
        TestProcedure.schedule(sl, scheduleNow + 86, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.45f, 0.62f));
        // Éclatement : la prison explose, les éclats blessent, la cible est libérée et projetée.
        TestProcedure.schedule(sl, scheduleNow + 94, () -> {
            releaseMovementHold(target, holdEnd);
            sl.playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.52f);
            sl.playSound(null, cx, cy, cz, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.70f, 0.80f);
            PixelFx.send(sl, ParticleTypes.SNOWFLAKE, cx, cy, cz, 60, 1.30, 1.00, 1.30, 0.45);
            PixelFx.send(sl, ParticleTypes.WHITE_ASH, cx, cy, cz, 36, 1.60, 0.80, 1.60, 0.30);
            // Rejoue la signature côté client au moment exact de la rupture.
            broadcastNear(sl, (float) cx, (float) cy, (float) cz,
                    WeaponVisualEffectId.PERMAFROST_RIME.id, KIND_IMPACT, 4,
                    (float) cx, (float) cy, (float) cz,
                    (float) cx, (float) cy, (float) cz, 0, 0.50f);
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cx - 3.60, cy - 2.40, cz - 3.60, cx + 3.60, cy + 2.80, cz + 3.60))) {
                if (!victim.isAlive() || victim instanceof Player) continue;
                if (attackerId >= 0 && victim.getId() == attackerId) continue;
                // Tout ce que l'exposition avait figé est libéré ici, pas avant : sinon les mobs
                // gelés par la couronne restaient bloqués après la fin de l'animation.
                int id = victim.getId();
                FROST_EXPOSURE.remove(id);
                MovementHold frozen = MOVEMENT_HOLDS.get(id);
                if (frozen != null && frozen.expires() == holdEnd) releaseMovementHold(victim, holdEnd);
                double rx = victim.getX() - cx, rz = victim.getZ() - cz;
                double len = Math.sqrt(rx * rx + rz * rz);
                double ux = len < 0.05 ? 0.0 : rx / len;
                double uz = len < 0.05 ? 1.0 : rz / len;
                // Souffle de rupture : plus on est près du cœur de la prison, plus on part fort.
                double nearness = 1.0 - Math.min(1.0, len / 3.60);
                var rnd = victim.getRandom();
                double power = (0.55 + 0.80 * nearness) * (0.85 + rnd.nextDouble() * 0.45);
                double lift = (0.42 + 0.38 * nearness) * (0.85 + rnd.nextDouble() * 0.45);
                victim.setDeltaMovement(ux * power, lift, uz * power);
                victim.fallDistance = 0.0f;
                victim.hurtMarked = true;
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1, false, true, true));
                // Les éclats font d'autant plus mal qu'on était près du bloc de glace.
                hurtWithCapturedWeapon(sl, attackerId, victim,
                        Math.min(5.0f, pulse * (3.0f + 3.6f * (float) nearness)));
                PixelFx.send(sl, ParticleTypes.SNOWFLAKE, victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        14, 0.30, 0.34, 0.30, 0.12);
            }
        });
        TestProcedure.schedule(sl, scheduleNow + 120, () -> {
            sl.playSound(null, cx, cy, cz, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.50f, 0.85f);
            // Filet de sécurité : aucun compteur d'exposition ne survit à l'animation.
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cx - 6.0, cy - 4.0, cz - 6.0, cx + 6.0, cy + 4.0, cz + 6.0))) {
                FROST_EXPOSURE.remove(victim.getId());
            }
        });
    }

    // ============================================================================================
    // 影 OMBRE (T377) — allonge spectrale, faille du Flétrisseur, voile d'éclipse
    // ============================================================================================

    /**
     * Piles de <b>Marque d'ombre</b> posées sur une créature, avec leur instant de péremption.
     *
     * <p>C'est le liant des trois signatures, comme la charge statique l'est pour la foudre —
     * mais il fonctionne à l'envers. La charge statique majore les <i>dégâts</i> ; la marque
     * d'ombre, elle, majore la <b>durée des afflictions</b>. L'ombre du mod est l'élément des
     * malédictions ({@code DeepElement.isCursePath} renvoie {@code SHADOW} pour toutes), donc
     * sa ressource partagée devait porter sur les états, pas sur les points de vie.</p>
     */
    private record UmbralMark(int stacks, long expires) { }

    /** Marques d'ombre en cours, par identifiant d'entité. */
    private static final Map<Integer, UmbralMark> UMBRAL_MARKS = new ConcurrentHashMap<>();
    /** Au-delà, la marque s'effondre d'elle-même au lieu de s'accumuler. */
    private static final int MARK_MAX = 4;
    /** Allongement des afflictions par pile accumulée. */
    private static final float MARK_PER_STACK = 0.35f;
    /** Durée de vie d'une marque sans nouvelle imposition (ticks). */
    private static final int MARK_DECAY = 160;

    /** Rayon d'aspiration de la faille, et portée du cône de souffle. */
    private static final double RIFT_PULL_RADIUS = 8.0;
    private static final double RIFT_BREATH_RANGE = 6.5;

    /** Recul de la faille derrière l'ancrage. Égal à {@code RIFT_BACK} du rendu client. */
    private static final double RIFT_BACK = 2.0;

    /**
     * Où centrer la Faille du Flétrisseur, en fonction de ce qu'il y a à souffler.
     *
     * <p>Le client ouvre toujours la faille {@code RIFT_BACK} blocs derrière le point qu'on
     * lui envoie, et souffle de là vers le porteur sur {@code RIFT_BREATH_RANGE} blocs. Le
     * cône est donc entièrement déterminé par ce point — et c'est pour ça qu'il faut le
     * choisir ici plutôt que de prendre la cible les yeux fermés.</p>
     *
     * <p><b>La règle.</b> La bouche doit être derrière le mob le plus éloigné du porteur,
     * sinon celui-là se retrouve dans le dos du crâne et ne reçoit rien alors qu'il est
     * visuellement dans le jet. Mais elle ne peut pas reculer indéfiniment : au-delà de la
     * portée, c'est le mob le plus PROCHE qui sort du cône par l'autre bout. Quand le
     * groupe est plus long que le souffle, on privilégie donc le proche et on accepte de
     * perdre le fond — un souffle qui rate ce qu'il touche presque est pire qu'un souffle
     * trop court.</p>
     *
     * @param dirX direction unitaire de la cible VERS le porteur
     * @return {@code {x, z}} du point à envoyer au client
     */
    private static double[] riftAnchor(ServerLevel sl, double tX, double tY, double tZ,
                                       double dirX, double dirZ, int attackerId) {
        final double awayX = -dirX;
        final double awayZ = -dirZ;
        double maxS = 0.0;
        double minS = 0.0;
        for (LivingEntity victim : shadowVictims(sl, tX, tY, tZ, RIFT_BREATH_RANGE * 0.70, 4.0, attackerId)) {
            double s = (victim.getX() - tX) * awayX + (victim.getZ() - tZ) * awayZ;
            if (s > maxS) maxS = s;
            if (s < minS) minS = s;
        }
        double mouth = maxS + RIFT_BACK;
        double usable = RIFT_BREATH_RANGE * 0.92;
        if (mouth - minS > usable) mouth = minS + usable;
        double anchor = Math.max(0.0, mouth - RIFT_BACK);
        return new double[]{tX + awayX * anchor, tZ + awayZ * anchor};
    }

    /** Direction unitaire d'un point vers le porteur ; {@code (0,1)} à défaut. */
    private static double[] towardWielder(LivingEntity attacker, double fromX, double fromZ) {
        double ax = attacker != null ? attacker.getX() - fromX : 0.0;
        double az = attacker != null ? attacker.getZ() - fromZ : 1.0;
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 0.01) return new double[]{0.0, 1.0};
        return new double[]{ax / len, az / len};
    }
    /** Rayon du dôme d'éclipse. Doit rester aligné sur {@code ShadowSignatureVfx.DOME_R}. */
    private static final double DOME_RADIUS = 7.0;

    /**
     * Ciel confisque pendant une eclipse. On retient l'heure du monde AVANT la signature,
     * l'instant ou on l'a prise, et l'instant ou on doit la rendre.
     *
     * <p>La cle est le nom de la dimension et non le {@code ServerLevel} lui-meme : une
     * carte statique qui retient un niveau l'empeche d'etre liberee quand le monde est
     * decharge.</p>
     */
    private record SkyTheft(long originalDayTime, long startedAt, long endsAt) {}

    private static final Map<String, SkyTheft> ECLIPSE_SKY = new ConcurrentHashMap<>();

    /**
     * Un dôme d'éclipse actif : une <b>prison</b>, et non plus seulement une zone.
     *
     * <p>Jusqu'ici le voile était un simple contrat de position — ce qui restait dessous
     * souffrait, ce qui en sortait était tranquille. C'est désormais l'inverse : ce qui est
     * entré ne ressort plus, et souffre en continu tant que le voile tient.</p>
     *
     * <p>La clé de la carte est le nom de la dimension suivi de l'identifiant du porteur,
     * jamais le {@link ServerLevel} lui-même : une carte statique qui retient un niveau
     * l'empêche d'être libéré au déchargement du monde. Un porteur n'a qu'un dôme à la fois,
     * donc un second lancer remplace le premier au lieu de les empiler.</p>
     *
     * @param captives identifiants des entités déjà vues à l'intérieur. On ne retient pas
     *                 qui est dans le rayon à l'instant t mais qui y est <i>entré</i> :
     *                 sans cette mémoire, la coque aspirerait les passants qui frôlent le
     *                 bord de l'extérieur, ce qui serait un piège et non une prison.
     * @param burnFrom premier tick de monde où la brûlure s'applique : la pose du voile.
     *                 Avant elle, le dôme n'est qu'une calotte suspendue — enfermer et
     *                 brûler sous une porte encore ouverte serait injouable.
     */
    private record EclipseDome(double x, double y, double z, double radius,
                               long burnFrom, long expires, int attackerId, float burn,
                               java.util.Set<Integer> captives) {}

    private static final Map<String, EclipseDome> ECLIPSE_DOMES = new ConcurrentHashMap<>();

    /** Période de la brûlure, en ticks. Dix : c'est l'invulnérabilité vanilla après un coup. */
    private static final int ECLIPSE_BURN_PERIOD = 10;

    /**
     * Qui l'éclipse a le droit de frapper et d'enfermer.
     *
     * <p>{@link #shadowVictims} ne convenait pas : il écarte <b>tous</b> les joueurs, ce qui
     * vide l'éclipse de son sens en PvP, et il ramasse <b>tous</b> les mobs, donc les vaches
     * du pâturage et les villageois d'à côté. Les règles ici sont explicites :</p>
     * <ul>
     *   <li>le <b>porteur</b> n'est jamais touché ni retenu — c'est son ombre, il entre et
     *       sort à volonté ;</li>
     *   <li>les <b>joueurs</b> le sont, sauf en créatif ou en spectateur. Si le serveur
     *       interdit le PvP, {@code hurt} refusera le coup de lui-même : c'est le bon
     *       comportement, on ne le contourne pas ;</li>
     *   <li>les <b>compagnons apprivoisés</b> (loup, chat, perroquet) sont épargnés, même
     *       quand ils se battent — un chien qui meurt sous l'ombre de son maître est un bug
     *       aux yeux du joueur, quelle que soit la règle invoquée ;</li>
     *   <li>les <b>hostiles</b> ({@link Enemy}) le sont toujours ;</li>
     *   <li>les <b>neutres</b> — enderman provoqué, abeille, golem de fer en colère — le sont
     *       uniquement s'ils ont une cible : c'est ce qui sépare un animal d'un agresseur.</li>
     * </ul>
     * Tout le reste (bétail, villageois, PNJ, animaux marins, golems au repos) passe au
     * travers du voile sans rien subir.
     */
    private static boolean eclipsePrey(LivingEntity e, int attackerId) {
        if (e == null || !e.isAlive()) return false;
        if (attackerId >= 0 && e.getId() == attackerId) return false;
        if (e instanceof Player player) return !player.isCreative() && !player.isSpectator();
        if (e instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob mob && mob.getTarget() != null;
    }

    /** Recense ce que l'éclipse peut frapper sous le dôme — joueurs compris, passifs exclus. */
    private static List<LivingEntity> eclipseVictims(ServerLevel sl, double cx, double cy, double cz,
                                                     double radius, double halfHeight, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - halfHeight, cz - radius,
                        cx + radius, cy + halfHeight, cz + radius))) {
            if (!eclipsePrey(candidate, attackerId)) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            if (ddx * ddx + ddz * ddz > radius * radius) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * La cage et la brûlure, une fois par entité et par tick.
     *
     * <p><b>Pourquoi ici et pas dans des tâches programmées ?</b> Un {@code schedule} toutes
     * les dix ticks suffirait pour les dégâts, mais pas pour la paroi : entre deux tâches un
     * joueur qui sprinte parcourt plus de quatre blocs et serait déjà dehors. Le confinement
     * n'a de sens qu'évalué à chaque tick, donc dans le tick d'entité déjà en place.</p>
     *
     * <p>La contrainte est une <b>sphère</b> et non un cylindre : elle ferme le dessus, et
     * c'est par le dessus qu'on s'échappe (saut, élytres, perle). Vers le bas elle ne gêne
     * personne — sept blocs de creusement en dix secondes, cela n'arrive pas — mais elle
     * ferme quand même la fuite par en dessous, gratuitement.</p>
     *
     * <p>Une emprise en cours ({@code MOVEMENT_HOLDS}, souffle de la faille, tornade…) a la
     * priorité sur la paroi : deux systèmes qui téléportent la même entité dans le même tick
     * la font vibrer. La brûlure, elle, continue de s'appliquer.</p>
     */
    private static void tickEclipseCage(LivingEntity target) {
        if (ECLIPSE_DOMES.isEmpty()) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        final String dim = sl.dimension().location().toString();
        final long now = sl.getGameTime();
        final int id = target.getId();
        for (Map.Entry<String, EclipseDome> entry : ECLIPSE_DOMES.entrySet()) {
            EclipseDome dome = entry.getValue();
            if (now >= dome.expires()) { ECLIPSE_DOMES.remove(entry.getKey()); continue; }
            if (!entry.getKey().startsWith(dim + "|")) continue;
            if (now < dome.burnFrom()) continue;
            if (!eclipsePrey(target, dome.attackerId())) continue;

            double dx = target.getX() - dome.x();
            double dy = target.getY() + target.getBbHeight() * 0.5 - dome.y();
            double dz = target.getZ() - dome.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= dome.radius()) dome.captives().add(id);
            else if (!dome.captives().contains(id)) continue;   // simple passant : qu'il passe

            // --- LA BRÛLURE, à période fixe et pour tout le monde pareil ---
            if ((now - dome.burnFrom()) % ECLIPSE_BURN_PERIOD == 0L) {
                hurtWithCapturedWeapon(sl, dome.attackerId(), target, dome.burn());
                PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                        4, 0.22, 0.32, 0.22, 0.012);
            }

            // --- LA PAROI ---
            if (MOVEMENT_HOLDS.containsKey(id) || SOUL_HOLDS.containsKey(id)
                    || WIND_HOLDS.containsKey(id) || SURGE_RIDES.containsKey(id)) return;
            double limit = dome.radius() - 0.45;
            if (dist <= limit || dist < 0.001) return;

            double k = limit / dist;
            double nx = dome.x() + dx * k;
            double ny = dome.y() + dy * k - target.getBbHeight() * 0.5;
            double nz = dome.z() + dz * k;
            // On annule la part SORTANTE de la vitesse et on en renvoie un peu vers le
            // centre. Remettre la vitesse à zéro collerait la victime à la coque ; la
            // renvoyer en entier la ferait rebondir d'un mur à l'autre.
            Vec3 v = target.getDeltaMovement();
            double ux = dx / dist, uy = dy / dist, uz = dz / dist;
            double out = v.x * ux + v.y * uy + v.z * uz;
            if (out > 0.0) {
                target.setDeltaMovement(v.x - ux * out * 1.25,
                        v.y - uy * out * 1.25, v.z - uz * out * 1.25);
                target.hasImpulse = true;
            }
            target.fallDistance = 0.0f;
            if (target instanceof ServerPlayer sp) {
                sp.connection.teleport(nx, ny, nz, sp.getYRot(), sp.getXRot());
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetEntityMotionPacket(sp));
            } else {
                target.teleportTo(nx, ny, nz);
            }
            // La paroi doit se VOIR à l'endroit précis où on la heurte, sinon le joueur
            // croit à un défaut de collision. Le son est espacé : à chaque tick, ce serait
            // un bourdonnement continu.
            PixelFx.send(sl, ParticleTypes.SCULK_CHARGE_POP, nx, ny + target.getBbHeight() * 0.5, nz,
                    5, 0.14, 0.22, 0.14, 0.02);
            PixelFx.send(sl, ParticleTypes.PORTAL, nx, ny + target.getBbHeight() * 0.5, nz,
                    7, 0.20, 0.30, 0.20, 0.06);
            if (now % 8L == 0L) {
                sl.playSound(null, nx, ny, nz, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.PLAYERS, 0.35f, 1.45f);
            }
            return;
        }
    }

    /**
     * Force le monde a minuit pour la duree de l'eclipse.
     *
     * <p><b>Le piege, et la raison de tout ce code.</b> Deux eclipses qui se chevauchent
     * cassent le monde si on les traite naivement : la seconde enregistre <i>minuit</i>
     * comme heure d'origine, et quand la premiere se termine elle rend minuit. Le monde
     * reste alors dans la nuit pour toujours, et le joueur n'a aucun moyen de comprendre
     * pourquoi. On ne retient donc l'heure QU'A LA PREMIERE prise ; les suivantes se
     * contentent de reculer l'echeance.</p>
     *
     * <p>Le changement est propage aux clients par la liste des joueurs, qui reemet
     * l'heure toutes les secondes : le ciel s'assombrit donc en moins d'une seconde,
     * pendant que le disque monte. C'est volontairement laisse au serveur plutot que
     * d'envoyer le paquet a la main — un constructeur de paquet se trompe en silence.</p>
     */
    private static void stealSky(ServerLevel sl, int durationTicks) {
        final String key = sl.dimension().location().toString();
        final long gameNow = sl.getGameTime();
        final long ends = gameNow + durationTicks;
        ECLIPSE_SKY.compute(key, (k, cur) -> {
            if (cur == null) return new SkyTheft(sl.getDayTime(), gameNow, ends);
            // Deja de nuit : on garde l'heure d'origine de la PREMIERE eclipse et on ne
            // fait que repousser l'echeance.
            return cur.endsAt() >= ends ? cur : new SkyTheft(cur.originalDayTime(), cur.startedAt(), ends);
        });
        sl.setDayTime(18000L);
    }

    /**
     * Rend le ciel. Ne fait rien si une eclipse plus recente tient encore la dimension :
     * c'est elle qui rendra l'heure, et elle a conserve l'originale.
     *
     * <p>On rend {@code origine + temps ecoule} et non {@code origine} tout court, sinon
     * chaque eclipse remonterait l'horloge du monde de dix secondes. Si le cycle du jour
     * est gele, ces dix secondes sont rendues en trop — huit dixiemes de pour cent d'un
     * cycle, invisible, et c'est le prix a payer pour ne pas dependre d'une gamerule dont
     * on ne peut pas verifier le nom sans compilateur.</p>
     */
    private static void returnSky(ServerLevel sl) {
        final String key = sl.dimension().location().toString();
        final SkyTheft theft = ECLIPSE_SKY.get(key);
        if (theft == null) return;
        if (sl.getGameTime() < theft.endsAt()) return;
        ECLIPSE_SKY.remove(key);
        sl.setDayTime(theft.originalDayTime() + (sl.getGameTime() - theft.startedAt()));
    }
    /** Longueur du couloir de l'allonge, et sa demi-largeur. */
    private static final double REACH_LENGTH = 9.0;

    /**
     * Distance de la paume au point d'impact, le long du couloir, pour une traction donnée.
     *
     * <p>C'est la transcription exacte de {@code handF} dans le rendu client
     * ({@code ShadowSignatureVfx.renderGrasp}) : {@code -REACH_SPAN*0.06 - REACH_SPAN*0.64*pull},
     * au signe près puisque l'écran compte vers l'arrière et le serveur vers le porteur.
     * Les deux doivent rester collées : dès qu'elles divergent, la proie est traînée à côté
     * du poing, ce qui est pire que pas de traction du tout.</p>
     */
    private static double graspHandDistance(double pull) {
        return REACH_LENGTH * 0.06 + REACH_LENGTH * 0.64 * pull;
    }
    private static final double REACH_HALF_WIDTH = 1.35;

    /**
     * Pose des piles de marque et renvoie le total après ajout. À saturation la marque
     * s'effondre sur place via {@link #collapseMark}.
     */
    private static int addUmbralMark(ServerLevel sl, LivingEntity victim, int stacks, int attackerId) {
        if (victim == null || !victim.isAlive() || stacks <= 0) return 0;
        final int id = victim.getId();
        final int now = TestProcedure.currentTick(sl);
        // Purge opportuniste. Une entree n'est retiree que par saturation : une creature
        // marquee qui meurt laisse donc la sienne pour toujours. C'est le comportement de
        // STATIC_CHARGES, et ce n'est pas une raison pour le reproduire — au-dela de 256
        // entrees on balaie les perimees. Le cout est nul en pratique (la methode n'est
        // appelee que sur une frappe d'ombre) et la carte ne peut plus deriver.
        if (UMBRAL_MARKS.size() > 256) UMBRAL_MARKS.values().removeIf(m -> m.expires() <= now);
        UmbralMark prior = UMBRAL_MARKS.get(id);
        int had = (prior == null || prior.expires() <= now) ? 0 : prior.stacks();
        int total = had + stacks;
        if (total >= MARK_MAX) {
            UMBRAL_MARKS.remove(id);
            collapseMark(sl, victim, attackerId, total);
            return 0;
        }
        UMBRAL_MARKS.put(id, new UmbralMark(total, now + MARK_DECAY));
        PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.80, victim.getZ(),
                3 + total * 2, 0.26, 0.34, 0.26, 0.012);
        return total;
    }

    /** Allongement de durée apporté par les marques présentes, sans les consommer. */
    private static int markedDuration(ServerLevel sl, LivingEntity victim, int baseTicks) {
        if (victim == null) return baseTicks;
        UmbralMark mark = UMBRAL_MARKS.get(victim.getId());
        if (mark == null || mark.expires() <= TestProcedure.currentTick(sl)) return baseTicks;
        return Math.round(baseTicks * (1.0f + MARK_PER_STACK * mark.stacks()));
    }

    /**
     * Effondrement de la marque à saturation : la victime encaisse d'un coup toute l'ombre
     * accumulée, et se retrouve aveuglée.
     *
     * <p>Contrairement au claquage de la charge statique, l'effondrement <b>ne se propage
     * pas</b> aux voisins. La raison est la même que celle qui a forcé la garde anti-récursion
     * de {@code dischargeStatic} : une marque qui en poserait une autre pourrait se renvoyer la
     * balle en anneau. Ici on tranche à la source — l'effondrement ne pose aucune marque, donc
     * la question ne se pose jamais.</p>
     */
    private static void collapseMark(ServerLevel sl, LivingEntity victim, int attackerId, int stacks) {
        final double vx = victim.getX();
        final double vy = victim.getY() + victim.getBbHeight() * 0.55;
        final double vz = victim.getZ();
        hurtWithCapturedWeapon(sl, attackerId, victim, 1.30f * stacks);
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, true, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 110, 0, true, false));
        sl.playSound(null, vx, vy, vz, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS,
                0.50f, 1.45f);
        PixelFx.send(sl, ParticleTypes.SQUID_INK, vx, vy, vz, 40, 0.55, 0.60, 0.55, 0.06);
        PixelFx.send(sl, ParticleTypes.SCULK_CHARGE_POP, vx, vy, vz, 16, 0.42, 0.50, 0.42, 0.04);
        broadcastNear(sl, (float) vx, (float) vy, (float) vz,
                WeaponVisualEffectId.WITHER_SHADOW_RIFT.id, KIND_IMPACT, 3,
                (float) vx, (float) vy, (float) vz,
                (float) vx, (float) vy, (float) vz, 0, 0.40f);
    }

    // ============================================================================================
    //  T379-BLOOD — gameplay des CINQ signatures du sang
    // ============================================================================================
    //
    //  Le sang arrivait dans l'etat exact ou l'ombre se trouvait avant la T377 : cinq animations
    //  de 14 a 16 ticks qui ne faisaient RIEN. Ni degats differes, ni afflictions, ni soin.
    //
    //  La famille est batie autour d'une idee que les dix autres n'ont pas : le sang ne frappe
    //  pas, il COUTE. Tout ce qui suit applique une regle de transfert — ce que la victime perd,
    //  le porteur le gagne, et ce qui est perdu continue de couler apres le coup.
    //
    //  Trois pieces communes, reprises par les cinq titres :
    //
    //    1. L'HEMORRAGIE ({@link #BLEEDS}) — la marque de la famille, l'equivalent de la marque
    //       d'ombre. Elle s'empile jusqu'a cinq, saigne toutes les demi-secondes, et surtout
    //       elle PUNIT LA FUITE : une creature qui court perd jusqu'a deux fois et demie plus
    //       de sang qu'une creature immobile. C'est la seule affliction du mod dont l'intensite
    //       depend de ce que fait la victime, et elle change la lecture d'un combat — on
    //       n'echappe pas a une saignee, on la subit plus vite.
    //
    //    2. LA FLAQUE ({@link #BLOOD_POOLS}) — une zone au sol, posee par l'arete qui goutte et
    //       par le carnage, qui ralentit et re-saigne ceux qui y restent. C'est la contrepartie
    //       au sol de l'hemorragie : la premiere suit la victime, la seconde tient le terrain.
    //
    //    3. LA FERVEUR ({@link #bloodFervor}) — le buff du porteur. Elle n'est jamais donnee
    //       d'office : elle se paie en sang verse, et son intensite suit ce qui a reellement
    //       ete preleve. Un coup qui rate ne nourrit personne.
    //
    //  AIRE ET DUEL. Chacune des cinq distingue explicitement les deux cas, comme l'ombre :
    //  le mot « solo » dans ce bloc veut dire « une seule victime dans la zone », et il donne
    //  systematiquement un tarif plein plus un effet supplementaire, pour qu'aucune signature
    //  ne soit decevante en duel. Mais les formes de zone, elles, different a dessein :
    //  le couperet coupe une LIGNE, le siphon prend une GRAPPE, la transfusion un DISQUE a deux
    //  camps, le carnage un disque qui s'elargit a chaque taillade.

    /** Rayon utile des signatures de sang qui travaillent autour de la victime. */
    private static final double BLOOD_RADIUS = 6.0;

    /**
     * T380-BLOOD-LINK : distance a laquelle un conduit de sang casse.
     *
     * <p>C'est la MEME valeur que {@code WeaponWorldVfxManager.BLOOD_LINK_BREAK}. Les deux
     * cotes rompent donc au meme instant sans echanger un paquet de plus : le client ne
     * fait que mesurer, comme le serveur, la distance entre deux entites qu'il voit tous
     * les deux.</p>
     */
    private static final double BLOOD_LINK_BREAK = 9.0;

    /**
     * Le lien tient-il encore ? Sert au siphon et a la transfusion, les deux seules
     * signatures du mod dont le visuel RELIE deux etres : tant que le conduit est dessine,
     * il doit etre vrai, et des qu'il ne l'est plus, il doit casser des deux cotes.
     */
    /** Le lien tient. */
    private static final int LINK_HOLDS = 0;
    /** La distance l'a rompu : quelqu'un a fui, et cela se paie. */
    private static final int LINK_TORN = 1;
    /** Il n'y a plus de lien a rompre : un des deux corps a disparu. Personne ne paie. */
    private static final int LINK_GONE = 2;

    /**
     * Etat de la laisse.
     *
     * <p>T380-BLOOD-LINK, deuxieme passe : la premiere version repondait par un simple oui/non,
     * et traitait donc « le porteur est mort » exactement comme « la victime a fui » — la
     * victime encaissait l'arrachement parce que son bourreau venait de tomber. Les deux cas
     * sont maintenant distincts : seule la DISTANCE fait payer.</p>
     */
    private static int bloodLinkState(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return LINK_GONE;
        if (!attacker.isAlive() || !target.isAlive()) return LINK_GONE;
        if (attacker.level() != target.level()) return LINK_GONE;
        return attacker.distanceToSqr(target) <= BLOOD_LINK_BREAK * BLOOD_LINK_BREAK
                ? LINK_HOLDS : LINK_TORN;
    }

    /**
     * La rupture : les crochets s'arrachent. Fuir coute, sinon la laisse serait un cadeau —
     * il suffirait de courir pour annuler la signature sans rien payer.
     */
    private static void bloodLinkSnap(ServerLevel sl, LivingEntity attacker, LivingEntity target,
                                      int attackerId, float base) {
        if (target == null || !target.isAlive()) return;
        double vx = target.getX();
        double vy = target.getY() + target.getBbHeight() * 0.55;
        double vz = target.getZ();
        sl.playSound(null, vx, vy, vz, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.85f, 0.70f);
        sl.playSound(null, vx, vy, vz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.70f, 0.95f);
        PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, vx, vy, vz, 30, 0.70, 0.55, 0.70, 0.09);
        PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, vx, vy, vz, 10, 0.40, 0.35, 0.40, 0.05);
        // L'arrachement : une seule fois, et deux piles d'hemorragie. La victime est libre,
        // elle n'est pas indemne.
        bloodHurt(sl, attackerId, target, base * 0.45f);
        // T394 : soixante ticks « a partir de maintenant » etait une fuite — un lien qui
        // casse sur la fin de la chorégraphie faisait saigner trois secondes apres elle.
        // L'arrachement ne peut plus depasser le mur du porteur.
        addBleedUntil(sl, target, 2, attackerId, base * 0.11f, sl.getGameTime() + 60L, BLEED_PERIOD);
        if (attacker != null && attacker.isAlive()) {
            sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.45f, 1.10f);
        }
    }

    /** Nombre maximal de piles d'hemorragie. Au-dela, une saignee de plus ne se lit plus. */
    private static final int BLEED_MAX = 5;

    /**
     * T387 : marge apres la fin d'une animation pendant laquelle une hemorragie peut encore
     * battre. Vingt ticks, une seconde — assez pour que la derniere goutte tombe, trop peu
     * pour qu'on se demande d'ou vient le degat.
     */
    private static final int BLEED_GRACE = 20;

    /**
     * Gouttes de sang. La poussiere coloree est la seule particule vanilla qui lise
     * vraiment << sang >> : {@code FALLING_DRIPSTONE_LAVA} est orange et se lit comme de
     * la lave, {@code DAMAGE_INDICATOR} comme un coeur brise.
     */
    private static final net.minecraft.core.particles.DustParticleOptions BLOOD_DUST =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.64f, 0.02f, 0.05f), 1.35f);

    /** La meme, plus sombre et plus grosse : ce qui a deja coule et qui poisse. */
    private static final net.minecraft.core.particles.DustParticleOptions BLOOD_DUST_DARK =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.31f, 0.01f, 0.03f), 1.70f);

    /**
     * Gerbe de gouttes AUTOUR d'un corps : un anneau a mi-hauteur plus une retombee au sol.
     *
     * <p>Demande explicite apres essai en jeu : << pour les degats lies au sang, mets des
     * particules / gouttes de sang autour du mob >>. Un nuage centre sur l'entite disparait
     * dans son modele ; c'est l'ANNEAU, legerement plus large que la hitbox, qui se voit.</p>
     */
    private static void bloodDrops(ServerLevel sl, LivingEntity victim, int intensity, long seed) {
        if (sl == null || victim == null) return;
        double vx = victim.getX();
        double vz = victim.getZ();
        double w = Math.max(0.35, victim.getBbWidth());
        double h = Math.max(0.60, victim.getBbHeight());
        int ring = Math.max(4, Math.min(18, 4 + intensity * 2));
        for (int i = 0; i < ring; i++) {
            double ang = (i + (seed % 11) * 0.09) / ring * Math.PI * 2.0;
            double rr = w * (0.42 + 0.34 * (((i * 37 + seed) % 11) / 10.0));
            PixelFx.send(sl, BLOOD_DUST,
                    vx + Math.cos(ang) * rr,
                    victim.getY() + h * (0.18 + 0.58 * (((i * 53 + seed) % 13) / 12.0)),
                    vz + Math.sin(ang) * rr,
                    1, 0.015, 0.015, 0.015, 0.0);
        }
        // Ce qui tombe : au pied, large et sombre.
        PixelFx.send(sl, BLOOD_DUST_DARK, vx, victim.getY() + 0.07, vz,
                2 + intensity, w * 0.55, 0.03, w * 0.55, 0.0);
    }

    /**
     * Degat de la famille Sang : la perte de vie ET les gouttes, toujours ensemble.
     *
     * <p>Les neuf points de degats du bloc appelaient {@code hurtWithCapturedWeapon} nu.
     * Le joueur voyait la barre de vie descendre sans rien voir couler — et quand il
     * voyait quelque chose, c'etait {@code FALLING_DRIPSTONE_LAVA}, orange, qui se lit
     * comme de la lave et non comme du sang. Un seul passage obligatoire, et la
     * famille entiere saigne.</p>
     *
     * @return vrai si le coup a porte
     */
    private static boolean bloodHurt(ServerLevel sl, int attackerId, LivingEntity victim,
                                     float amount) {
        boolean hit = hurtWithCapturedWeapon(sl, attackerId, victim, amount);
        if (hit && sl != null && victim != null) {
            // L'intensite suit le degat : une taillade de volee jette moins qu'une
            // guillotine. Plafonnee, sinon une arme tardive noircirait l'ecran.
            int weight = 2 + (int) Math.min(7.0f, Math.max(0.0f, amount) * 0.85f);
            bloodDrops(sl, victim, weight, sl.getGameTime() * 31L + victim.getId());
        }
        return hit;
    }

    /**
     * T387 : hemorragie bornee par une ECHEANCE ABSOLUE, en temps de monde.
     *
     * <p>Toutes les saignees d'une meme signature visent la meme fin, quelle que soit
     * l'heure a laquelle elles sont posees. C'est la seule facon d'empecher une
     * choregraphie de 180 ticks de saigner pendant 260.</p>
     */
    private static void addBleedUntil(ServerLevel sl, LivingEntity victim, int stacks,
                                      int attackerId, float perStack, long deadline,
                                      int periodTicks) {
        if (sl == null) return;
        int period = Math.max(2, periodTicks);
        // T394 : l'echeance demandee est d'abord rabattue sur LE MUR du porteur.
        long limit = bloodDeadline(attackerId, deadline);
        long remaining = limit - sl.getGameTime();
        // Le plancher « au moins une perte » est supprime : c'est lui qui transformait
        // chaque morsure de flaque tardive en une periode de rab APRES l'echeance. Une
        // hemorragie qui n'a plus le temps de battre ne doit pas etre posee du tout.
        if (remaining < period) return;
        addBleed(sl, victim, stacks, attackerId, perStack, (int) remaining, period);
    }

    /** Periode d'une perte de sang, en ticks de MONDE. */
    private static final int BLEED_PERIOD = 10;

    /**
     * Part de la perte rendue au porteur. Volontairement inferieure a la moitie : un vol de
     * vie integral rend n'importe quelle arme de sang invincible en melee prolongee.
     */
    private static final float BLEED_DRAIN_SHARE = 0.42f;

    /**
     * Hemorragie posee sur une victime.
     *
     * @param stacks     piles accumulees (1..{@link #BLEED_MAX})
     * @param attackerId porteur a crediter du vol de vie, ou -1
     * @param expires    fin, en temps de MONDE ({@code getGameTime})
     * @param nextTick   prochaine perte, en temps de MONDE
     * @param perStack   degats d'une pile a chaque periode
     */
    private record Bleed(int stacks, int attackerId, long expires, long nextTick, float perStack,
                         int period) { }

    private static final Map<Integer, Bleed> BLEEDS = new ConcurrentHashMap<>();

    /**
     * T394 — LE MUR DU SANG. Echeance absolue, par porteur, au-dela de laquelle plus aucun
     * degat de la famille sang ne peut etre inflige.
     *
     * <p>T387 avait borne chaque source separement. Chacune respectait sa borne et la somme
     * debordait quand meme : la flaque vivait deux secondes de plus que la saignee, son
     * plancher « au moins une perte » rajoutait une periode a chaque morsure, et
     * l'arrachement de lien s'offrait trois secondes a partir de n'importe quand. Borner
     * les sources une par une ne suffit pas — il en reapparait toujours une. Un mur unique,
     * ecrit au lancement de la signature et consulte par tout le monde, est la seule forme
     * de cette regle qui tienne.</p>
     */
    private static final Map<Integer, Long> BLOOD_END = new ConcurrentHashMap<>();

    /**
     * Pose le mur pour ce porteur. Si une signature plus longue est deja en cours, on garde
     * la plus lointaine : enchainer deux signatures ne doit pas tronquer la premiere.
     */
    private static void setBloodWall(int attackerId, long end) {
        if (attackerId < 0) return;
        BLOOD_END.merge(attackerId, end, Math::max);
    }

    /**
     * Rabat une echeance sur le mur du porteur. Renvoie l'echeance effective, qui peut etre
     * deja passee — a l'appelant de ne rien poser dans ce cas.
     */
    private static long bloodDeadline(int attackerId, long wanted) {
        if (attackerId < 0) return wanted;
        Long wall = BLOOD_END.get(attackerId);
        return wall == null ? wanted : Math.min(wanted, wall);
    }

    /** Vrai si le mur de ce porteur est franchi : plus rien ne doit saigner de son fait. */
    private static boolean bloodExpired(ServerLevel sl, int attackerId) {
        if (attackerId < 0) return false;
        Long wall = BLOOD_END.get(attackerId);
        if (wall == null) return false;
        if (sl.getGameTime() < wall) return false;
        BLOOD_END.remove(attackerId);
        return true;
    }

    /**
     * Flaque de sang au sol.
     *
     * @param expires fin, en temps de MONDE
     * @param nextBite prochaine morsure de la flaque, en temps de MONDE
     */
    private record BloodPool(String dimension, double x, double y, double z, double radius,
                             long expires, long nextBite, int attackerId, float perBite) { }

    private static final Map<Integer, BloodPool> BLOOD_POOLS = new ConcurrentHashMap<>();
    private static int bloodPoolSequence = 0;

    /**
     * Pose ou renforce une hemorragie.
     *
     * <p>Les piles s'additionnent mais la duree ne s'additionne pas : elle est repoussee. Sans
     * cela, enchainer cinq petits coups donnerait une saignee de plusieurs minutes, et le
     * joueur perdrait tout rapport entre ce qu'il fait et ce qu'il voit.</p>
     */
    private static void addBleed(ServerLevel sl, LivingEntity victim, int stacks,
                                 int attackerId, float perStack, int durationTicks) {
        addBleed(sl, victim, stacks, attackerId, perStack, durationTicks, BLEED_PERIOD);
    }

    /**
     * T385-CADENCE : meme hemorragie, mais a la CADENCE de la signature qui l'a posee.
     *
     * <p>Les cinq battaient toutes a dix ticks : la perte de vie etait le meme metronome
     * dans les cinq cas, et c'est ce metronome qu'on entend le plus longtemps. Le couperet
     * saigne lentement et lourdement, l'arete qui goutte est rapide par definition, le
     * carnage est irregulierement rapide. Le total reste calibre, seule la repartition
     * change.</p>
     *
     * @param periodTicks ticks entre deux pertes
     */
    private static void addBleed(ServerLevel sl, LivingEntity victim, int stacks,
                                 int attackerId, float perStack, int durationTicks,
                                 int periodTicks) {
        if (victim == null || !victim.isAlive() || stacks <= 0) return;
        long now = sl.getGameTime();
        Bleed previous = BLEEDS.get(victim.getId());
        int total = stacks;
        float unit = perStack;
        if (previous != null && previous.expires() > now) {
            total = Math.min(BLEED_MAX, previous.stacks() + stacks);
            // On garde la saignee la plus forte des deux : un grand coup ne doit pas etre
            // dilue par les petits qui le suivent.
            unit = Math.max(previous.perStack(), perStack);
        } else {
            total = Math.min(BLEED_MAX, stacks);
        }
        int period = Math.max(2, periodTicks);
        if (previous != null && previous.expires() > now) period = Math.min(period, previous.period());
        long next = previous != null && previous.expires() > now ? previous.nextTick() : now + period;
        // T387 : on ne RACCOURCIT pas une saignee en cours, mais on ne la rallonge que si la
        // nouvelle va plus loin. Avant, chaque appel ecrasait l'echeance avec la sienne —
        // la morsure de flaque, qui repasse toutes les 20 ticks avec 80 ticks en poche,
        // repoussait indefiniment la fin. C'est la boucle qui faisait saigner sans fin.
        long expiry = now + durationTicks;
        if (previous != null && previous.expires() > expiry) expiry = previous.expires();
        BLEEDS.put(victim.getId(), new Bleed(total, attackerId, expiry, next, unit, period));
        PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                2 + total, victim.getBbWidth() * 0.35, victim.getBbHeight() * 0.30,
                victim.getBbWidth() * 0.35, 0.01);
        bloodDrops(sl, victim, 2 + total, now);
    }

    /** Piles d'hemorragie actuellement posees sur une cible, 0 si aucune. */
    private static int bleedStacks(ServerLevel sl, LivingEntity victim) {
        if (victim == null) return 0;
        Bleed bleed = BLEEDS.get(victim.getId());
        if (bleed == null || bleed.expires() <= sl.getGameTime()) return 0;
        return bleed.stacks();
    }

    /**
     * Saignement, appele a chaque tick d'entite.
     *
     * <p><b>La fuite coute cher.</b> Le multiplicateur suit la vitesse horizontale : immobile,
     * la victime perd le tarif de base ; a pleine course, deux fois et demie. C'est la piece
     * qui donne son caractere a la famille — la panique aggrave la blessure, et un joueur qui
     * comprend cela cesse de courir, ce qui le remet a portee du porteur.</p>
     */
    private static void tickBleed(LivingEntity target) {
        if (target == null) return;
        Bleed bleed = BLEEDS.get(target.getId());
        if (bleed == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!target.isAlive() || now >= bleed.expires()
                || bloodExpired(sl, bleed.attackerId())) {
            BLEEDS.remove(target.getId());
            return;
        }
        if (now < bleed.nextTick()) return;

        Vec3 motion = target.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float effort = 1.0f + 1.5f * (float) Math.min(1.0, speed / 0.22);
        float amount = bleed.perStack() * bleed.stacks() * effort;

        double vx = target.getX();
        double vy = target.getY() + target.getBbHeight() * 0.45;
        double vz = target.getZ();

        boolean hurt = bleed.attackerId() >= 0
                ? hurtWithCapturedWeapon(sl, bleed.attackerId(), target, amount)
                : target.hurt(target.damageSources().generic(), amount);

        // --- Le transfert. Ce que la victime perd, le porteur en reprend une part. ---
        if (hurt && bleed.attackerId() >= 0
                && sl.getEntity(bleed.attackerId()) instanceof LivingEntity drinker
                && drinker.isAlive()) {
            float fed = amount * BLEED_DRAIN_SHARE;
            if (drinker.getHealth() < drinker.getMaxHealth()) {
                drinker.heal(fed);
            } else {
                // Deja au maximum : le surplus devient de l'absorption, plafonnee a quatre
                // coeurs. Sans ce plafond, une melee longue empilerait un bouclier infini.
                var existing = drinker.getEffect(MobEffects.ABSORPTION);
                int level = existing == null ? 0 : Math.min(3, existing.getAmplifier() + 1);
                drinker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, level, true, true));
            }
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                    drinker.getX(), drinker.getY() + drinker.getBbHeight() * 0.7, drinker.getZ(),
                    2, 0.22, 0.25, 0.22, 0.0);
        }

        int particles = 2 + bleed.stacks() + (effort > 2.0f ? 4 : 0);
        PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, vx, vy, vz, particles,
                target.getBbWidth() * 0.32, target.getBbHeight() * 0.28, target.getBbWidth() * 0.32, 0.0);
        // T387 : l'anneau de gouttes. Sans lui, une perte de vie par hemorragie ne se
        // distingue d'aucune autre : le joueur voit des degats tomber sans savoir pourquoi.
        bloodDrops(sl, target, bleed.stacks() + (effort > 2.0f ? 3 : 1), now);
        if (effort > 2.0f) {
            // La course projette : on l'entend et on la voit gicler derriere.
            PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, vx, vy, vz, 3, 0.30, 0.22, 0.30, 0.02);
            sl.playSound(null, vx, vy, vz, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS,
                    0.22f, 0.70f);
        }
        BLEEDS.put(target.getId(), new Bleed(bleed.stacks(), bleed.attackerId(), bleed.expires(),
                now + bleed.period(), bleed.perStack(), bleed.period()));
    }

    /**
     * Pose une flaque de sang persistante.
     *
     * <p>La dimension est retenue par son identifiant et non par une reference au niveau :
     * une carte statique qui retient un {@code ServerLevel} empeche son dechargement, piege
     * deja paye en T376.</p>
     */
    private static void addBloodPool(ServerLevel sl, double x, double y, double z,
                                     double radius, int durationTicks, int attackerId, float perBite) {
        long now = sl.getGameTime();
        int key = ++bloodPoolSequence;
        BLOOD_POOLS.put(key, new BloodPool(sl.dimension().location().toString(), x, y, z, radius,
                now + durationTicks, now + 20L, attackerId, perBite));
        PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, x, y + 0.1, z,
                28, radius * 0.45, 0.06, radius * 0.45, 0.0);
        sl.playSound(null, x, y, z, SoundEvents.MUD_PLACE, SoundSource.PLAYERS, 0.55f, 0.60f);
    }

    /** Morsure periodique des flaques, et leur extinction. */
    private static void tickBloodPools(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        // Le mur se nettoie ici : une carte statique indexee par porteur qui ne perd jamais
        // ses entrees est la meme fuite que STATIC_CHARGES, deja payee une fois.
        if (!BLOOD_END.isEmpty()) {
            long wallNow = sl.getGameTime();
            BLOOD_END.values().removeIf(end -> end <= wallNow);
        }
        if (BLOOD_POOLS.isEmpty()) return;
        String here = sl.dimension().location().toString();
        long now = sl.getGameTime();
        for (Map.Entry<Integer, BloodPool> entry : BLOOD_POOLS.entrySet()) {
            BloodPool pool = entry.getValue();
            if (!pool.dimension().equals(here)) continue;
            if (now >= pool.expires() || bloodExpired(sl, pool.attackerId())) {
                BLOOD_POOLS.remove(entry.getKey());
                continue;
            }
            if (now < pool.nextBite()) continue;
            BLOOD_POOLS.put(entry.getKey(), new BloodPool(pool.dimension(), pool.x(), pool.y(),
                    pool.z(), pool.radius(), pool.expires(), now + 20L, pool.attackerId(), pool.perBite()));
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, pool.x(), pool.y() + 0.08, pool.z(),
                    10, pool.radius() * 0.5, 0.04, pool.radius() * 0.5, 0.0);
            for (LivingEntity victim : bloodVictims(sl, pool.x(), pool.y(), pool.z(),
                    pool.radius(), 2.2, pool.attackerId())) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false));
                // T387 : bornee a la flaque elle-meme. Avec 80 ticks en dur, chaque
                // morsure repoussait la fin de la saignee de quatre secondes, toutes les
                // secondes : une victime qui reste dans la flaque ne cessait jamais de
                // saigner. C'etait la vraie source des << degats sans fin >>.
                addBleedUntil(sl, victim, 1, pool.attackerId(), pool.perBite(),
                        pool.expires(), BLEED_PERIOD);
                bloodDrops(sl, victim, 2, now + victim.getId());
            }
        }
    }

    /**
     * Victimes d'une zone de sang. Meme regle que {@code shadowVictims} : les joueurs sont
     * exclus, donc aucune de ces signatures ne se retourne contre un allie, et le porteur
     * n'est jamais sa propre cible.
     */
    private static List<LivingEntity> bloodVictims(ServerLevel sl, double cx, double cy, double cz,
                                                   double radius, double halfHeight, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - halfHeight, cz - radius,
                        cx + radius, cy + halfHeight, cz + radius))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            if (ddx * ddx + ddz * ddz > radius * radius) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * Victimes situees sous le TRAIT DE COUPE du couperet.
     *
     * <p>Aucune autre signature du mod n'a cette forme de zone. Un couperet ne frappe pas un
     * disque : il frappe une ligne, celle de sa lame, large d'a peine plus d'un bloc. On
     * mesure donc la distance a un SEGMENT perpendiculaire a l'axe porteur→cible, et non au
     * point d'impact. Resultat lisible en jeu : trois mobs alignes dans le sens de la lame
     * tombent ensemble, trois mobs alignes face au porteur non.</p>
     *
     * @param dirX,dirZ axe porteur→cible, unitaire
     * @param halfSpan  demi-longueur de la lame
     * @param thickness demi-epaisseur du trait
     */
    private static List<LivingEntity> bladeLineVictims(ServerLevel sl, double cx, double cy, double cz,
                                                       double dirX, double dirZ, double halfSpan,
                                                       double thickness, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        double range = Math.max(halfSpan, thickness) + 1.5;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - range, cy - 3.0, cz - range, cx + range, cy + 3.0, cz + range))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            // Projection sur l'axe (profondeur) et sur sa perpendiculaire (le long de la lame).
            double depth = ddx * dirX + ddz * dirZ;
            double along = -ddx * dirZ + ddz * dirX;
            double pad = candidate.getBbWidth() * 0.5;
            if (Math.abs(depth) > thickness + pad) continue;
            if (Math.abs(along) > halfSpan + pad) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * Allies a soigner par la transfusion : le porteur, les autres joueurs, et les creatures
     * apprivoisees. C'est la seule aire BIENVEILLANTE de tout le mod, et elle est volontairement
     * plus courte que l'aire de prelevement — on prend loin, on redistribue pres.
     */
    private static List<LivingEntity> transfusionAllies(ServerLevel sl, LivingEntity attacker,
                                                        double cx, double cy, double cz, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (attacker != null && attacker.isAlive()) out.add(attacker);
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - 3.0, cz - radius, cx + radius, cy + 3.0, cz + radius))) {
            if (!candidate.isAlive() || out.contains(candidate)) continue;
            boolean friendly = candidate instanceof Player
                    || (candidate instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame());
            if (!friendly) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            if (ddx * ddx + ddz * ddz > radius * radius) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * LA FERVEUR : le buff du porteur, paye en sang verse.
     *
     * <p>Elle ne donne jamais un effet fixe. Le degre monte avec la quantite reellement
     * prelevee, et retombe seul. Un coup dans le vide ne nourrit rien — c'est la contrepartie
     * du fait que le sang frappe plus fort que les autres familles quand il touche.</p>
     *
     * @param spilled sang verse, en points de degats
     */
    private static void bloodFervor(ServerLevel sl, LivingEntity attacker, float spilled, int ticks) {
        if (attacker == null || !attacker.isAlive() || spilled <= 0.1f) return;
        int level = spilled >= 9.0f ? 2 : (spilled >= 4.0f ? 1 : 0);
        attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, level, true, true));
        if (level >= 1) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    ticks, 0, true, true));
        }
        PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.75, attacker.getZ(),
                8 + level * 6, 0.30, 0.40, 0.30, 0.01);
        sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.45f, 0.70f + level * 0.12f);
    }

    /** Fondu d'entree du sang : on previent avant de frapper, comme pour la foudre et l'ombre. */
    private static void bloodSwell(ServerLevel sl, int now, double x, double y, double z,
                                   int steps, float volume) {
        for (int k = 0; k < steps; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + step * 3, () ->
                    sl.playSound(null, x, y, z, SoundEvents.BEEHIVE_DRIP, SoundSource.PLAYERS,
                            volume * (0.25f + 0.17f * step), 0.55f + 0.06f * step));
        }
    }

    /**
     * Routage des CINQ signatures du sang.
     *
     * <p>Les cinq partagent l'hemorragie et la ferveur, et se repondent : le couperet ouvre,
     * l'arete entretient, le siphon encaisse, la transfusion redistribue, le carnage dissemine.
     * Chacune traite explicitement les deux cas demandes — <b>plusieurs ennemis</b> et
     * <b>ennemi seul</b>.</p>
     */
    private static void applyBloodGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.GUILLOTINE_CLEAVE) {
            applyGuillotineGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.DRIPPING_BLOOD_EDGE) {
            applyBloodEdgeGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
            applyCrimsonSiphonGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.VITAL_TRANSFUSION) {
            applyVitalTransfusionGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.CARNAGE_BERSERK) {
            applyCarnageGameplay(attacker, target, sl, damage);
        }
    }

    // --------------------------------------------------------------------------------------
    //  I. COUPERET DE GUILLOTINE (150 ticks)
    // --------------------------------------------------------------------------------------

    /**
     * Le couperet est une EXECUTION : son interet n'est pas de frapper fort, c'est de frapper
     * plus fort ce qui est deja entame. Le bonus suit la vie MANQUANTE de la victime, de sorte
     * qu'une cible a pleine vie encaisse un bon coup et qu'une cible a bout de souffle est
     * tranchee net. C'est aussi la seule signature du mod a clouer sa cible pendant toute sa
     * montee : les quatre-vingt-trois ticks d'attente seraient absurdes si la victime pouvait
     * simplement s'ecarter.
     *
     * <p>Chronologie, calee sur l'animation : plantation 0→15, linteau 15, hisse 24→57,
     * SUSPENS 57→75, chute 75, morsure 83, nappe 93→123, retrait 123→150.</p>
     */
    private static void applyGuillotineGameplay(LivingEntity attacker, LivingEntity target,
                                                ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.6f, weaponDamage * 0.95f);
        // T387 : l'echeance de TOUS les degats differes de cette signature. << now >> est
        // l'horloge de l'ordonnanceur ; les hemorragies et les flaques, elles, vivent en
        // temps de monde — il faut donc les deux, prises au meme instant.
        final long bleedEnd = sl.getGameTime() + 150 + BLEED_GRACE;
        // T394 : LE MUR. Au-dela, plus un degat de sang de ce porteur, quelle que
        // soit la source qui le demande. Et la flaque meurt AVEC la saignee : a
        // BLEED_GRACE x 2 elle vivait une seconde de plus et reposait une
        // hemorragie a chaque morsure.
        setBloodWall(attackerId, bleedEnd);
        final long poolEnd = bleedEnd;

        // Axe de la lame : perpendiculaire a porteur→cible.
        double axX = attacker != null ? target.getX() - attacker.getX()
                : -Math.sin(Math.toRadians(target.getYRot()));
        double axZ = attacker != null ? target.getZ() - attacker.getZ()
                : Math.cos(Math.toRadians(target.getYRot()));
        double axLen = Math.sqrt(axX * axX + axZ * axZ);
        if (axLen < 0.01) { axX = 0.0; axZ = 1.0; axLen = 1.0; }
        final double dirX = axX / axLen;
        final double dirZ = axZ / axLen;

        // --- La victime est clouee sous la lame, du premier montant a la morsure. ---
        // Elle n'est pas etourdie : elle se defend, elle ne s'echappe pas. Et l'emprise est
        // relachee DEUX ticks apres la coupe, pas avant : relachee a la chute, la victime
        // aurait le temps de sortir du cadre pendant que la lame descend.
        applyMovementHold(target, sl.getGameTime() + 86L, false);

        // --- 0→15 : la plantation. Deux poutres entrent dans le sol. ---
        sl.playSound(null, ox, oy, oz, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.55f, 0.52f);
        bloodSwell(sl, now, ox, oy, oz, 4, 0.70f);
        for (int k = 0; k < 2; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 2 + step * 6, () -> {
                double side = step == 0 ? 1.0 : -1.0;
                sl.playSound(null, ox - dirZ * side * 1.0, oy - 0.8, oz + dirX * side * 1.0,
                        SoundEvents.NETHER_WOOD_PLACE, SoundSource.PLAYERS, 0.70f, 0.48f);
                PixelFx.send(sl, ParticleTypes.CRIT,
                        ox - dirZ * side * 1.0, oy - 1.2, oz + dirX * side * 1.0,
                        12, 0.22, 0.10, 0.22, 0.04);
            });
        }
        TestProcedure.schedule(sl, now + 15, () ->
                sl.playSound(null, ox, oy + 2.0, oz, SoundEvents.NETHER_WOOD_PLACE,
                        SoundSource.PLAYERS, 0.85f, 0.40f));

        // --- 24→57 : la hisse. Cinq a-coups de chaine, hauteur tonale montante. ---
        for (int k = 0; k < 5; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 24 + step * 7, () -> {
                sl.playSound(null, ox, oy + 1.2 + step * 0.45, oz, SoundEvents.CHAIN_PLACE,
                        SoundSource.PLAYERS, 0.62f, 0.72f + step * 0.09f);
                sl.playSound(null, ox, oy + 1.2 + step * 0.45, oz, SoundEvents.IRON_TRAPDOOR_OPEN,
                        SoundSource.PLAYERS, 0.28f, 0.60f + step * 0.07f);
            });
        }

        // --- 57→75 : LE SUSPENS. Rien, et c'est voulu. Une seule goutte au tick 62. ---
        TestProcedure.schedule(sl, now + 62, () -> {
            sl.playSound(null, ox, oy + 2.6, oz, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                    SoundSource.PLAYERS, 0.80f, 1.30f);
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, ox, oy + 2.4, oz,
                    1, 0.0, 0.0, 0.0, 0.0);
        });
        // La victime comprend ce qui l'attend : lenteur et faiblesse pendant l'attente.
        TestProcedure.schedule(sl, now + 58, () -> {
            if (!target.isAlive()) return;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, true, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
        });

        // --- 75 : la chute. ---
        TestProcedure.schedule(sl, now + 75, () -> {
            sl.playSound(null, ox, oy + 2.0, oz, SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.00f, 0.52f);
            sl.playSound(null, ox, oy + 1.5, oz, SoundEvents.CHAIN_BREAK,
                    SoundSource.PLAYERS, 0.70f, 0.85f);
        });

        // --- 83 : LA MORSURE. ---
        TestProcedure.schedule(sl, now + 83, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.00f, 0.72f);
            sl.playSound(null, ox, oy, oz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.95f, 0.62f);
            sl.playSound(null, ox, oy, oz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.55f, 0.55f);
            PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, ox, oy, oz, 40, 1.10, 0.35, 1.10, 0.14);
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, ox, oy + 0.4, oz, 50, 1.30, 0.50, 1.30, 0.05);

            List<LivingEntity> cut = bladeLineVictims(sl, ox, oy, oz, dirX, dirZ, 1.35, 0.85, attackerId);
            if (!cut.contains(target) && target.isAlive()) cut.add(target);
            boolean solo = cut.size() <= 1;
            float spilled = 0.0f;

            for (LivingEntity victim : cut) {
                // L'EXECUTION : le bonus suit la vie manquante. A pleine vie, 1,00x ;
                // a un quart de vie, 1,94x ; a l'agonie, 2,25x.
                float missing = 1.0f - Math.max(0.0f, Math.min(1.0f,
                        victim.getHealth() / Math.max(1.0f, victim.getMaxHealth())));
                float execute = 1.0f + 1.25f * missing;
                // SOLO : plein tarif. EN LIGNE : tarif de zone, mais l'execution reste —
                // c'est elle le sujet de la signature, pas sa puissance brute.
                float amount = base * (solo ? 1.35f : 0.80f) * execute;
                if (bloodHurt(sl, attackerId, victim, amount)) spilled += amount;
                // La coupe sectionne : on ne manie plus ses outils de la meme facon.
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        solo ? 160 : 90, solo ? 1 : 0, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                        solo ? 140 : 80, 1, true, false));
                // 0,085 -> 0,115 : la saignee ne court plus que jusqu'a la fin de
                // l'echafaud, donc chaque perte doit peser davantage pour que le total
                // reste celui d'avant. Moins long, pas moins fort.
                addBleedUntil(sl, victim, solo ? 3 : 2, attackerId, base * 0.115f, bleedEnd, 14);
                PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        14, 0.35, 0.35, 0.35, 0.10);
            }

            // SOLO uniquement : la lame reste plantee, et la victime est maintenue au sol
            // vingt ticks de plus. En foule, ce serait une immobilisation de masse.
            if (solo && target.isAlive()) {
                applyMovementHold(target, sl.getGameTime() + 20L, false);
            }

            // --- Le buff : le couperet nourrit le porteur a proportion de ce qu'il a verse.
            bloodFervor(sl, attacker, spilled, 140);
            // Et s'il a acheve quelqu'un, la ferveur se double d'une resistance breve : le
            // porteur vient de regarder une execution, pas de prendre un coup.
            boolean killed = false;
            for (LivingEntity victim : cut) if (!victim.isAlive()) killed = true;
            if (killed && attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, true, true));
                sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.35f, 0.55f);
            }
        });

        // --- 93→123 : la nappe. Le sang monte du trait et retombe en flaque. ---
        TestProcedure.schedule(sl, now + 93, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 0.70f, 0.55f);
            addBloodPool(sl, ox, target.getY(), oz, 1.9,
                    (int) (poolEnd - sl.getGameTime()), attackerId, base * 0.07f);
        });
        TestProcedure.schedule(sl, now + 123, () ->
                sl.playSound(null, ox, oy, oz, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.50f, 0.45f));
    }

    // --------------------------------------------------------------------------------------
    //  II. FIL QUI GOUTTE (120 ticks)
    // --------------------------------------------------------------------------------------

    /**
     * L'arete qui goutte est la signature d'ENTRETIEN : elle frappe peu et saigne longtemps.
     * Tout son interet est dans la duree, et c'est la seule des cinq dont le total de degats
     * depasse largement le coup initial.
     *
     * <p>Son aire n'est pas une explosion mais une FLAQUE, qui tient le terrain cent ticks
     * apres la fin de l'animation : le sang verse reste un danger. En duel, la flaque est plus
     * petite mais la saignee monte directement a quatre piles.</p>
     */
    private static void applyBloodEdgeGameplay(LivingEntity attacker, LivingEntity target,
                                               ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.2f, weaponDamage * 0.74f);
        // T387 : l'echeance de TOUS les degats differes de cette signature. << now >> est
        // l'horloge de l'ordonnanceur ; les hemorragies et les flaques, elles, vivent en
        // temps de monde — il faut donc les deux, prises au meme instant.
        final long bleedEnd = sl.getGameTime() + 120 + BLEED_GRACE;
        // T394 : LE MUR. Au-dela, plus un degat de sang de ce porteur, quelle que
        // soit la source qui le demande. Et la flaque meurt AVEC la saignee : a
        // BLEED_GRACE x 2 elle vivait une seconde de plus et reposait une
        // hemorragie a chaque morsure.
        setBloodWall(attackerId, bleedEnd);
        final long poolEnd = bleedEnd;

        sl.playSound(null, ox, oy, oz, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.70f, 1.35f);
        bloodSwell(sl, now, ox, oy, oz, 3, 0.55f);
        // T385-VERROU : l'entaille et les rigoles sont dessinees SUR le corps. Une cible
        // libre de marcher pendant 120 ticks laissait son entaille derriere elle.
        // 44 ticks : l'ouverture et les quatre rigoles. Les gouttes, elles, peuvent tomber
        // pendant que la bete repart.
        applyMovementHold(target, sl.getGameTime() + 44L, false);

        // --- 10 : l'ouverture de l'entaille. ---
        TestProcedure.schedule(sl, now + 10, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.60f, 1.10f);
            PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, ox, oy, oz, 18, 0.40, 0.30, 0.40, 0.06);

            List<LivingEntity> near = bloodVictims(sl, ox, oy, oz, 3.2, 2.4, attackerId);
            if (!near.contains(target) && target.isAlive()) near.add(target);
            boolean solo = near.size() <= 1;
            float spilled = 0.0f;
            for (LivingEntity victim : near) {
                boolean primary = victim == target;
                float amount = base * (primary ? (solo ? 0.95f : 0.80f) : 0.45f);
                if (bloodHurt(sl, attackerId, victim, amount)) spilled += amount;
                // SOLO : quatre piles d'un coup, c'est-a-dire presque le maximum. EN FOULE :
                // deux sur la cible, une sur les voisins. La saignee se dilue, pas la duree.
                int stacks = primary ? (solo ? 4 : 2) : 1;
                // T380-BLOOD-DPS : 0,075 par pile et non 0,115, duree 200 et non 220.
                //
                // Le calcul complet des cinq signatures a montre que cette ligne faisait de
                // l'arete la plus grosse source de degats de la famille : quatre piles a
                // 0,115 sur vingt-deux tics valent 60 points pour une cible immobile et 150
                // pour une cible qui court — plus que le Carnage, qui est cense etre « la
                // plus puissante des cinq », et avec six secondes d'animation contre neuf.
                // La signature se decrivait elle-meme comme celle qui « frappe peu et saigne
                // longtemps » ; elle frappait en realite le plus fort du mod. On garde les
                // quatre piles, qui sont son identite, et on baisse le tarif unitaire.
                // L'arete posait 200 ticks de saignee des le tick 10, soit 90 de plus
                // que son animation : c'etait la pire des cinq.
                addBleedUntil(sl, victim, stacks, attackerId, base * 0.075f, bleedEnd, 6);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        primary ? 70 : 40, 0, true, false));
            }
            bloodFervor(sl, attacker, spilled, 120);
        });

        // --- 26→66 : les rigoles. Sept coulees, sept dates. ---
        for (int k = 0; k < 7; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 26 + step * 6, () -> {
                sl.playSound(null, ox, oy - 0.3 - step * 0.08, oz,
                        SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, SoundSource.PLAYERS,
                        0.32f, 1.10f - step * 0.045f);
                PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        ox, oy - 0.2, oz, 6, 0.40, 0.35, 0.40, 0.0);
            });
        }

        // --- 36→102 : les gouttes qui touchent le sol, et la flaque qui s'etale. ---
        TestProcedure.schedule(sl, now + 36, () -> {
            boolean solo = bloodVictims(sl, ox, oy, oz, 4.0, 2.6, attackerId).size() <= 1;
            // La flaque de duel est plus petite mais mord plus fort : en duel elle sert a
            // interdire un pas de recul, en foule a couvrir une zone de passage.
            addBloodPool(sl, ox, target.getY(), oz, solo ? 1.7 : 2.8,
                    (int) (poolEnd - sl.getGameTime()), attackerId,
                    base * (solo ? 0.12f : 0.08f));
        });
        for (int k = 0; k < 5; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 44 + step * 14, () ->
                    sl.playSound(null, ox, target.getY() + 0.1, oz, SoundEvents.POINTED_DRIPSTONE_LAND,
                            SoundSource.PLAYERS, 0.40f, 0.85f - step * 0.05f));
        }

        // --- 102 : la fermeture. Un dernier soin au porteur a hauteur de la saignee posee.
        TestProcedure.schedule(sl, now + 102, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.SPONGE_ABSORB, SoundSource.PLAYERS, 0.35f, 1.20f);
            if (attacker != null && attacker.isAlive()) {
                int stacks = bleedStacks(sl, target);
                if (stacks > 0) attacker.heal(0.5f * stacks);
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  III. SIPHON CARMIN (170 ticks)
    // --------------------------------------------------------------------------------------

    /**
     * Le siphon est un TRANSFERT etale : six prelevements, un par bol qui remonte le conduit.
     * Son interet n'est ni la pointe de degats ni la zone, c'est la regularite — six fenetres
     * ou la victime doit rompre le contact, faute de quoi elle paie six fois.
     *
     * <p>En duel le conduit est unique et prend plein tarif. En foule il se ramifie : jusqu'a
     * trois conduits secondaires, chacun a tarif reduit, chacun avec son propre rayon visible
     * ({@code KIND_BEAM_SIPHON}) pour qu'on voie d'ou vient le sang. Le sixieme bol rompt le
     * conduit : si la victime principale est alors sous le tiers de sa vie, la rupture est
     * violente et la saignee double.</p>
     */
    private static void applyCrimsonSiphonGameplay(LivingEntity attacker, LivingEntity target,
                                                   ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.4f, weaponDamage * 0.86f);
        // T387 : l'echeance de TOUS les degats differes de cette signature. << now >> est
        // l'horloge de l'ordonnanceur ; les hemorragies et les flaques, elles, vivent en
        // temps de monde — il faut donc les deux, prises au meme instant.
        final long bleedEnd = sl.getGameTime() + 170 + BLEED_GRACE;
        // T394 : LE MUR. Au-dela, plus un degat de sang de ce porteur, quelle que
        // soit la source qui le demande. Et la flaque meurt AVEC la saignee : a
        // BLEED_GRACE x 2 elle vivait une seconde de plus et reposait une
        // hemorragie a chaque morsure.
        setBloodWall(attackerId, bleedEnd);
        final long poolEnd = bleedEnd;

        // --- 0→15 : les quatre crochets mordent. ---
        sl.playSound(null, ox, oy, oz, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.70f, 1.25f);
        bloodSwell(sl, now, ox, oy, oz, 4, 0.65f);
        // T385-VERROU : quatre crochets sont VISSES dans la victime et un conduit la relie
        // au porteur. Une victime qui s'eloigne casse son propre conduit au bout de neuf
        // blocs. L'emprise couvre la ponction (0 -> 150) et lache AVANT la rupture du
        // tick 153, pour que l'arrachement se lise comme une liberation.
        applyMovementHold(target, sl.getGameTime() + 150L, false);
        for (int k = 0; k < 4; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 3 + step * 3, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS,
                        0.50f, 1.05f + step * 0.08f);
                PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, ox, oy, oz, 5, 0.35, 0.25, 0.35, 0.03);
            });
        }
        // La morsure retient : la cible peut se battre, pas se desengager librement.
        TestProcedure.schedule(sl, now + 15, () -> {
            if (!target.isAlive()) return;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1, true, false));
        });

        // --- 34→117 : les six bols. ---
        // T380-BLOOD-LINK : la laisse. Le commentaire de cette methode promettait depuis le
        // premier jour « six fenetres ou la victime doit rompre le contact » — et rien dans
        // le code ne lui permettait de rompre quoi que ce soit : elle etait ponctionnee a
        // n'importe quelle distance. Le conduit est maintenant une vraie contrainte pour les
        // DEUX : la victime peut s'arracher, et le porteur ne peut pas se mettre a l'abri en
        // continuant de boire.
        final java.util.concurrent.atomic.AtomicBoolean snapped =
                new java.util.concurrent.atomic.AtomicBoolean();
        for (int k = 0; k < 6; k++) {
            final int bolus = k;
            TestProcedure.schedule(sl, now + 34 + bolus * 17, () -> {
                if (snapped.get()) return;
                if (!target.isAlive() && bolus < 5) return;
                int link = bloodLinkState(attacker, target);
                if (link != LINK_HOLDS) {
                    snapped.set(true);
                    // Mort du porteur : le conduit tombe, sans facture pour la victime.
                    if (link == LINK_TORN) bloodLinkSnap(sl, attacker, target, attackerId, base);
                    return;
                }
                // Les voisins sont pris autour de la victime LA OU ELLE EST, pas autour du
                // point ou elle se tenait il y a cinq secondes.
                double vx = target.getX();
                double vy = target.getY() + target.getBbHeight() * 0.55;
                double vz = target.getZ();
                List<LivingEntity> grapple = bloodVictims(sl, vx, vy, vz, 5.5, 3.0, attackerId);
                if (!grapple.contains(target) && target.isAlive()) grapple.add(target);
                boolean solo = grapple.size() <= 1;
                float drawn = 0.0f;
                int secondary = 0;

                for (LivingEntity victim : grapple) {
                    boolean primary = victim == target;
                    // SOLO : 0,30 par bol, soit 1,80 fois les degats de base sur six bols.
                    // EN GRAPPE : 0,22 sur la principale et 0,13 sur les autres — le total
                    // monte, le tarif individuel baisse.
                    float amount = base * (primary ? (solo ? 0.30f : 0.22f) : 0.13f);
                    if (bloodHurt(sl, attackerId, victim, amount)) drawn += amount;
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false));
                    PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                            6, 0.28, 0.30, 0.28, 0.02);
                    // Les conduits secondaires sont VISIBLES : trois au plus, sinon la scene
                    // devient illisible et le reseau de rayons coute plus qu'il ne raconte.
                    if (!primary && secondary < 3 && attacker != null) {
                        secondary++;
                        broadcastNear(sl, (float) victim.getX(), (float) victim.getY(), (float) victim.getZ(),
                                WeaponVisualEffectId.CRIMSON_SIPHON.id, KIND_BEAM_SIPHON, 3,
                                (float) victim.getX(), (float) (victim.getY() + victim.getBbHeight() * 0.5f),
                                (float) victim.getZ(),
                                (float) attacker.getX(), (float) (attacker.getY() + attacker.getBbHeight() * 0.6f),
                                (float) attacker.getZ(), 0, 0.55f);
                    }
                }

                // --- Le porteur encaisse. Plus de la moitie du sang preleve lui revient :
                //     c'est la signature la plus genereuse de la famille, et la plus lente.
                if (attacker != null && attacker.isAlive() && drawn > 0.0f) {
                    float fed = drawn * 0.58f;
                    if (attacker.getHealth() < attacker.getMaxHealth()) {
                        attacker.heal(fed);
                    } else {
                        var existing = attacker.getEffect(MobEffects.ABSORPTION);
                        int level = existing == null ? 0 : Math.min(3, existing.getAmplifier() + 1);
                        attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, level, true, true));
                    }
                    PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                            attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.75, attacker.getZ(),
                            8, 0.26, 0.35, 0.26, 0.01);
                }
                sl.playSound(null, vx, vy, vz, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS,
                        0.50f, 0.62f + bolus * 0.055f);
                sl.playSound(null, vx, vy, vz, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS,
                        0.30f, 0.70f + bolus * 0.05f);
            });
        }

        // --- 153 : la rupture. ---
        TestProcedure.schedule(sl, now + 153, () -> {
            // Deja casse en route : la signature est finie, on ne la termine pas deux fois.
            if (snapped.get()) return;
            sl.playSound(null, ox, oy, oz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.80f, 0.80f);
            sl.playSound(null, ox, oy, oz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.45f, 0.70f);
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, ox, oy, oz, 36, 0.80, 0.60, 0.80, 0.08);
            if (!target.isAlive()) return;
            float left = target.getHealth() / Math.max(1.0f, target.getMaxHealth());
            if (left < 0.34f) {
                // La victime est trop vide : le conduit claque et emporte le reste.
                bloodHurt(sl, attackerId, target, base * 1.10f);
                // La rupture tombe au tick 153 d'une animation de 170 : il ne reste
                // qu'une poignee de battements. Le degat perdu est rendu a l'impact
                // ci-dessus (0,75 -> 1,10 de base), ou il se VOIT.
                addBleedUntil(sl, target, 3, attackerId, base * 0.16f, bleedEnd, 10);
                sl.playSound(null, ox, oy, oz, SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.60f, 0.70f);
            } else {
                addBleedUntil(sl, target, 1, attackerId, base * 0.12f, bleedEnd, 10);
            }
            bloodFervor(sl, attacker, base * 1.4f, 160);
        });
    }

    // --------------------------------------------------------------------------------------
    //  IV. TRANSFUSION VITALE (140 ticks)
    // --------------------------------------------------------------------------------------

    /**
     * La transfusion est la seule signature BIENVEILLANTE du mod : elle prend d'un cote et
     * rend de l'autre. C'est aussi la seule dont l'identifiant est declare {@code additive} et
     * qui accepte un evenement de degats nul — elle peut donc se declencher sur un coup qui ne
     * fait rien, ce qui est exactement ce qu'on attend d'un soin.
     *
     * <p>Sept battements, en lub-dub. Chacun preleve sur les ennemis d'un disque de six blocs
     * et redistribue dans un disque plus court de quatre : on prend loin, on rend pres. En
     * duel le prelevement est presque double et le dernier battement pose une resistance ;
     * en foule il se repartit, et le total soigne davantage.</p>
     */
    /**
     * Le coeur de la transfusion : le MILIEU du segment porteur-victime, 1,60 bloc au-dessus
     * de la victime. Recalcule a chaque battement sur les positions vivantes.
     *
     * <p>Ces deux nombres sont aussi ceux du rendu ({@code heartF = -linkLen / 2},
     * {@code heartY = 1.60}). Quand ils divergent, on obtient ce qu'on avait avant : un
     * organe dessine a un endroit et des battements qui soignent a un autre.</p>
     */
    private static double[] transfusionHeart(LivingEntity attacker, LivingEntity target,
                                             double fallbackX, double fallbackY, double fallbackZ) {
        if (target == null || !target.isAlive()) {
            return new double[]{fallbackX, fallbackY, fallbackZ};
        }
        double vx = target.getX();
        double vy = target.getY() + target.getBbHeight() * 0.55;
        double vz = target.getZ();
        double ax = attacker != null && attacker.isAlive() ? attacker.getX() : vx;
        double az = attacker != null && attacker.isAlive() ? attacker.getZ() : vz;
        return new double[]{(vx + ax) * 0.5, vy + 1.6, (vz + az) * 0.5};
    }

    private static void applyVitalTransfusionGameplay(LivingEntity attacker, LivingEntity target,
                                                      ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.0f, weaponDamage * 0.62f);
        // T387 : l'echeance de TOUS les degats differes de cette signature. << now >> est
        // l'horloge de l'ordonnanceur ; les hemorragies et les flaques, elles, vivent en
        // temps de monde — il faut donc les deux, prises au meme instant.
        final long bleedEnd = sl.getGameTime() + 140 + BLEED_GRACE;
        // T394 : LE MUR. Au-dela, plus un degat de sang de ce porteur, quelle que
        // soit la source qui le demande. Et la flaque meurt AVEC la saignee : a
        // BLEED_GRACE x 2 elle vivait une seconde de plus et reposait une
        // hemorragie a chaque morsure.
        setBloodWall(attackerId, bleedEnd);
        final long poolEnd = bleedEnd;
        // T385-VERROU : la transfusion est l'autre signature a LIEN. Le coeur est calcule
        // au milieu du segment porteur-victime ; si la victime marche, le coeur derive et
        // les vaisseaux s'etirent. Sept battements = 106 ticks, plus la resorption.
        applyMovementHold(target, sl.getGameTime() + 118L, false);
        // Le coeur flotte entre les deux : c'est de la qu'on entend battre. Ces trois
        // nombres sont ceux que le rendu client reproduit — milieu du segment, 1,6 bloc plus
        // haut que l'ancre. Ils ne servent plus que de REPLI : a chaque battement, le coeur
        // est recalcule sur les positions vivantes, sinon il reste accroche au sol que les
        // deux combattants ont quitte depuis longtemps.
        final double hx = attacker != null ? (ox + attacker.getX()) * 0.5 : ox;
        final double hz = attacker != null ? (oz + attacker.getZ()) * 0.5 : oz;
        final double hy = oy + 1.6;
        final java.util.concurrent.atomic.AtomicBoolean torn =
                new java.util.concurrent.atomic.AtomicBoolean();

        bloodSwell(sl, now, hx, hy, hz, 3, 0.60f);
        TestProcedure.schedule(sl, now + 14, () ->
                sl.playSound(null, hx, hy, hz, SoundEvents.SCULK_CATALYST_BLOOM,
                        SoundSource.PLAYERS, 0.55f, 0.85f));

        for (int k = 0; k < 7; k++) {
            final int beat = k;
            final int at = 22 + beat * 14;
            // LE LUB-DUB. Deux coups rapproches puis un silence : un battement unique et
            // regulier donne un ballon qu'on gonfle, pas un organe. Quatre ticks d'ecart,
            // le second plus sourd et plus faible — c'est tout ce qu'il faut.
            TestProcedure.schedule(sl, now + at, () -> {
                if (torn.get()) return;
                double[] h = transfusionHeart(attacker, target, hx, hy, hz);
                sl.playSound(null, h[0], h[1], h[2], SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                        0.55f + beat * 0.05f, 0.80f + beat * 0.035f);
            });
            TestProcedure.schedule(sl, now + at + 4, () -> {
                if (torn.get()) return;
                double[] h = transfusionHeart(attacker, target, hx, hy, hz);
                sl.playSound(null, h[0], h[1], h[2], SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                        0.34f + beat * 0.03f, 0.62f + beat * 0.03f);
            });

            TestProcedure.schedule(sl, now + at, () -> {
                if (torn.get()) return;
                // Les vaisseaux d'entree sont plantes dans la victime : hors de portee, ils
                // s'arrachent, exactement comme le conduit du siphon.
                int link = bloodLinkState(attacker, target);
                if (link != LINK_HOLDS) {
                    torn.set(true);
                    if (link == LINK_TORN) bloodLinkSnap(sl, attacker, target, attackerId, base * 0.5f);
                    return;
                }
                double[] h = transfusionHeart(attacker, target, hx, hy, hz);
                PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, h[0], h[1], h[2],
                        10 + beat * 2, 0.45, 0.40, 0.45, 0.02);

                // --- LE PRELEVEMENT. Autour de la victime vivante, pas de son souvenir. ---
                double vx = target.getX();
                double vy = target.getY() + target.getBbHeight() * 0.55;
                double vz = target.getZ();
                List<LivingEntity> donors = bloodVictims(sl, vx, vy, vz, BLOOD_RADIUS, 3.2, attackerId);
                if (!donors.contains(target) && target.isAlive()) donors.add(target);
                boolean solo = donors.size() <= 1;
                float drawn = 0.0f;
                for (LivingEntity donor : donors) {
                    float amount = base * (solo ? 0.40f : 0.22f);
                    if (bloodHurt(sl, attackerId, donor, amount)) drawn += amount;
                    donor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
                    if (beat % 2 == 1) {
                        donor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, true, false));
                    }
                    PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR,
                            donor.getX(), donor.getY() + donor.getBbHeight() * 0.5, donor.getZ(),
                            4, 0.30, 0.30, 0.30, 0.03);
                }

                // --- LA REDISTRIBUTION. ---
                if (drawn <= 0.0f) return;
                List<LivingEntity> allies = transfusionAllies(sl, attacker, h[0], h[1] - 1.0, h[2], 4.0);
                if (allies.isEmpty()) return;
                // Le soin total vaut 85 % du sang preleve, partage entre les allies. Le
                // porteur en recoit toujours au moins la moitie : c'est lui qui a frappe.
                float pot = drawn * 0.85f;
                float mine = attacker != null && attacker.isAlive() ? pot * 0.55f : 0.0f;
                float rest = allies.size() > 1 ? (pot - mine) / (allies.size() - 1) : 0.0f;
                for (LivingEntity ally : allies) {
                    float share = (ally == attacker) ? mine + (allies.size() == 1 ? pot - mine : 0.0f) : rest;
                    if (share <= 0.0f) continue;
                    if (ally.getHealth() < ally.getMaxHealth()) {
                        ally.heal(share);
                    } else {
                        var existing = ally.getEffect(MobEffects.ABSORPTION);
                        int level = existing == null ? 0 : Math.min(2, existing.getAmplifier() + 1);
                        ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 140, level, true, true));
                    }
                    PixelFx.send(sl, ParticleTypes.HEART,
                            ally.getX(), ally.getY() + ally.getBbHeight() * 0.85, ally.getZ(),
                            2, 0.25, 0.20, 0.25, 0.01);
                }
                // Une regeneration legere accompagne les battements impairs, pour que le
                // soin continue entre deux systoles au lieu d'arriver par a-coups.
                if (beat % 2 == 0) {
                    for (LivingEntity ally : allies) {
                        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
                    }
                }
                // DUEL : le dernier battement blinde le porteur. En foule, le soin reparti
                // est deja la recompense, et une resistance de groupe serait de trop.
                if (solo && beat == 6 && attacker != null && attacker.isAlive()) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, true, true));
                    sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.30f, 0.80f);
                }
            });
        }

        // --- 112 : la resorption. Sous le coeur, la ou il est. ---
        TestProcedure.schedule(sl, now + 112, () -> {
            if (torn.get()) return;
            double[] h = transfusionHeart(attacker, target, hx, hy, hz);
            sl.playSound(null, h[0], h[1], h[2], SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.35f, 1.30f);
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, h[0], h[1], h[2], 24, 0.55, 0.60, 0.55, 0.04);
            double poolY = target.isAlive() ? target.getY() : oy - 1.0;
            addBloodPool(sl, h[0], poolY, h[2], 1.3,
                    (int) (poolEnd - sl.getGameTime()), attackerId, base * 0.06f);
        });
    }

    // --------------------------------------------------------------------------------------
    //  V. CARNAGE BERSERK (180 ticks)
    // --------------------------------------------------------------------------------------

    /**
     * Le carnage est la signature d'AIRE du sang, et la seule du mod dont la cadence
     * s'accelere : onze taillades dont l'ecart se reduit de moitie du debut a la fin. Elle ne
     * vise rien, elle occupe.
     *
     * <p><b>La frenesie se paie.</b> Chaque taillade qui touche nourrit le porteur, jusqu'a
     * trois paliers de ferveur. Mais la signature se termine par une RETOMBEE : soixante ticks
     * de lenteur d'action une fois la salve finie. Une frenesie sans contrecoup serait un buff
     * gratuit, et celle-ci est la plus puissante des cinq.</p>
     *
     * <p>En duel, les onze taillades convergent : chacune frappe deux fois plus fort et la
     * cible ne peut pas fuir la zone, puisqu'elle en est le centre.</p>
     */
    private static void applyCarnageGameplay(LivingEntity attacker, LivingEntity target,
                                             ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.3f, weaponDamage * 0.80f);
        // T387 : l'echeance de TOUS les degats differes de cette signature. << now >> est
        // l'horloge de l'ordonnanceur ; les hemorragies et les flaques, elles, vivent en
        // temps de monde — il faut donc les deux, prises au meme instant.
        final long bleedEnd = sl.getGameTime() + 180 + BLEED_GRACE;
        // T394 : LE MUR. Au-dela, plus un degat de sang de ce porteur, quelle que
        // soit la source qui le demande. Et la flaque meurt AVEC la saignee : a
        // BLEED_GRACE x 2 elle vivait une seconde de plus et reposait une
        // hemorragie a chaque morsure.
        setBloodWall(attackerId, bleedEnd);
        final long poolEnd = bleedEnd;
        final java.util.concurrent.atomic.AtomicInteger landed =
                new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger spilled =
                new java.util.concurrent.atomic.AtomicInteger();
        // T385-VERROU : onze taillades sur 87 ticks. Sans emprise, la premiere poussait la
        // cible hors du faisceau et les dix suivantes taillaient dans le vide. On tient
        // jusqu'au tick 112 et on LACHE avant l'onde du tick 115 : c'est elle, et elle
        // seule, qui doit projeter.
        applyMovementHold(target, sl.getGameTime() + 112L, false);

        // --- 0→25 : la montee. Trois pulsations, ecart decroissant. ---
        int[] winds = {3, 12, 19};
        for (int k = 0; k < 3; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + winds[step], () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                        0.30f + step * 0.14f, 0.55f + step * 0.09f);
                PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, ox, target.getY() + 0.1, oz,
                        10 + step * 6, 1.0 + step * 0.5, 0.08, 1.0 + step * 0.5, 0.04);
            });
        }

        // --- 25→112 : les onze taillades. ---
        for (int k = 0; k < 11; k++) {
            final int strike = k;
            float u = strike / 10.0f;
            // Meme loi que l'animation : l'ecart passe de six a trois ticks.
            final int at = Math.round(25 + 87.0f * (u - 0.22f * u * (1.0f - u) * 2.0f));
            final double radius = 2.6 + 0.9 * u;
            // T386 : le SIFFLEMENT de la lame qui descend. L'animation la fait naitre
            // vingt-cinq ticks avant son impact, haut au-dessus de la cible ; sans ce
            // signal, le joueur subit onze coups sans jamais entendre ARRIVER le suivant.
            // Neuf ticks d'avance : assez pour que l'oreille anticipe, trop peu pour que
            // le sifflement se detache du coup qu'il annonce.
            if (at >= 10) {
                TestProcedure.schedule(sl, now + at - 9, () -> sl.playSound(null, ox, oy + 2.6, oz,
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                        0.40f, 0.50f + strike * 0.022f));
            }
            TestProcedure.schedule(sl, now + at, () -> {
                List<LivingEntity> hit = bloodVictims(sl, ox, oy, oz, radius, 3.0, attackerId);
                if (!hit.contains(target) && target.isAlive()
                        && target.distanceToSqr(ox, oy, oz) <= radius * radius) {
                    hit.add(target);
                }
                boolean solo = hit.size() <= 1;
                sl.playSound(null, ox, oy, oz, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                        0.55f, 0.85f + strike * 0.035f);
                PixelFx.send(sl, ParticleTypes.DAMAGE_INDICATOR, ox, oy, oz,
                        8, radius * 0.4, 0.5, radius * 0.4, 0.08);
                if (hit.isEmpty()) return;

                landed.incrementAndGet();
                for (LivingEntity victim : hit) {
                    // SOLO : 0,42 par taillade, soit 4,6 fois les degats de base sur onze
                    // coups. EN FOULE : 0,24, mais sur tout le monde a la fois.
                    float amount = base * (solo ? 0.42f : 0.24f);
                    if (bloodHurt(sl, attackerId, victim, amount)) {
                        spilled.addAndGet(Math.round(amount * 100.0f));
                    }
                    // Une taillade sur deux ouvre : onze piles seraient absurdes, cinq est
                    // deja le plafond.
                    if ((strike & 1) == 0) {
                        addBleedUntil(sl, victim, 1, attackerId, base * 0.075f, bleedEnd, 8);
                    }
                    PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                            5, 0.30, 0.35, 0.30, 0.03);
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS,
                        0.30f, 1.05f + strike * 0.03f);

                // --- LA FERVEUR MONTE. Trois paliers, aux quatrieme, septieme et dixieme
                //     coups qui TOUCHENT. Une frenesie qui monte dans le vide n'a pas de sens.
                int count = landed.get();
                if ((count == 4 || count == 7 || count == 10) && attacker != null && attacker.isAlive()) {
                    int level = count == 4 ? 0 : (count == 7 ? 1 : 2);
                    attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, level, true, true));
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                            120, level == 2 ? 1 : 0, true, true));
                    attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, level, true, true));
                    sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                            SoundEvents.HOGLIN_ANGRY, SoundSource.PLAYERS, 0.45f, 0.70f + level * 0.15f);
                }
            });
        }

        // --- 115 : l'apogee. Une onde balaie le sol. ---
        TestProcedure.schedule(sl, now + 115, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.GOAT_RAM_IMPACT, SoundSource.PLAYERS, 0.85f, 0.60f);
            sl.playSound(null, ox, oy, oz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.70f, 0.50f);
            PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, ox, target.getY() + 0.15, oz,
                    60, 2.2, 0.10, 2.2, 0.06);
            List<LivingEntity> swept = bloodVictims(sl, ox, oy, oz, 5.0, 3.0, attackerId);
            boolean solo = swept.size() <= 1;
            for (LivingEntity victim : swept) {
                bloodHurt(sl, attackerId, victim, base * (solo ? 0.95f : 0.55f));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, true, false));
                addBleedUntil(sl, victim, solo ? 2 : 1, attackerId, base * 0.115f, bleedEnd, 8);
                // L'onde repousse. Sans composante verticale la poussee est absorbee par la
                // premiere marche et la victime glisse contre le talus (piege de la T376).
                double ddx = victim.getX() - ox;
                double ddz = victim.getZ() - oz;
                double flat = Math.sqrt(ddx * ddx + ddz * ddz);
                if (flat > 0.08) {
                    victim.setDeltaMovement(ddx / flat * 0.52, 0.34, ddz / flat * 0.52);
                    victim.hasImpulse = true;
                    victim.fallDistance = 0.0f;
                    if (victim instanceof ServerPlayer sp) {
                        sp.connection.send(new net.minecraft.network.protocol.game
                                .ClientboundSetEntityMotionPacket(sp));
                    }
                }
            }
            addBloodPool(sl, ox, target.getY(), oz, 3.0,
                    (int) (poolEnd - sl.getGameTime()), attackerId, base * 0.07f);
        });

        // --- 130→170 : la brume. Elle ralentit ce qui reste dedans. ---
        for (int k = 0; k < 3; k++) {
            TestProcedure.schedule(sl, now + 130 + k * 20, () -> {
                for (LivingEntity victim : bloodVictims(sl, ox, oy, oz, 4.2, 2.6, attackerId)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, true, false));
                }
                PixelFx.send(sl, ParticleTypes.FALLING_DRIPSTONE_LAVA, ox, target.getY() + 0.5, oz,
                        18, 1.8, 0.35, 1.8, 0.01);
            });
        }

        // --- 175 : LA RETOMBEE. Les bras retombent, et c'est le prix de la frenesie. ---
        TestProcedure.schedule(sl, now + 175, () -> {
            bloodFervor(sl, attacker, spilled.get() / 100.0f, 100);
            if (attacker == null || !attacker.isAlive()) return;
            if (landed.get() >= 6) {
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0, true, true));
                sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.50f, 0.70f);
            }
        });
    }


    // ============================================================================================
    //  T388-VOID — LE VIDE : SPHERE DE DISTORSION, PRISME DU DRAGON, FAILLE DU NEANT
    // ============================================================================================
    //
    //  L'IDENTITE DE JEU DE CETTE FAMILLE, et pourquoi elle n'est ni l'ombre ni le sang.
    //
    //  Le sang PUNIT : il saigne, il draine, il recompense l'acharnement. L'ombre AVEUGLE :
    //  elle marque, elle affaiblit, elle recompense l'embuscade. Le vide, lui, DEPLACE. Sa
    //  monnaie n'est pas la perte de vie, c'est la POSITION : il tire, il souleve, il ejecte,
    //  il retient. Toute sa valeur tactique est la — un ennemi arrache a sa place est un
    //  ennemi qui ne fait plus ce qu'il faisait.
    //
    //  Consequence assumee sur la recompense du porteur : le vide ne lui donne PAS de degats
    //  en plus (ce serait la ferveur du sang une seconde fois). Il lui donne de la MOBILITE —
    //  vitesse et chute lente. La famille dont le sujet est le deplacement recompense par du
    //  deplacement.
    //
    //  L'EMPREINTE (VOID_MARKS) est leur ressource commune. Quatre empreintes sur un corps et
    //  l'espace cede : implosion, soulevement, remise a zero. T387 est applique DES LE DEPART —
    //  toute empreinte porte une ECHEANCE ABSOLUE, et aucun appel ne peut la repousser au-dela
    //  de l'animation qui l'a posee.
    //

    /**
     * L'EMPREINTE DU NEANT posee sur une victime.
     *
     * @param stacks   empreintes accumulees (1..{@link #VOID_MARK_MAX})
     * @param attackerId porteur a crediter de l'echo, ou -1
     * @param expires  fin, en temps de MONDE ({@code getGameTime})
     * @param perStack degats d'une empreinte a l'implosion
     */
    private record VoidMark(int stacks, int attackerId, long expires, float perStack) { }

    private static final Map<Integer, VoidMark> VOID_MARKS = new ConcurrentHashMap<>();

    /** Au-dela de quatre, l'espace cede : c'est le seuil, pas un plafond decoratif. */
    private static final int VOID_MARK_MAX = 4;

    /**
     * Echos banques par porteur, avec leur peremption.
     *
     * <p>Un compteur nu aurait eu deux defauts, et le second est un bogue : il n'aurait
     * jamais ete vide (une carte statique indexee par identifiant d'entite qui ne se purge
     * pas est exactement la fuite deja constatee sur {@code BLOOD_POOLS}), et surtout il
     * aurait recompense l'ACCUMULATION au lieu de l'ENCHAINEMENT — trois effondrements
     * separes par dix minutes de marche auraient paye comme trois effondrements en six
     * secondes. L'echo se perime donc en {@link #VOID_ECHO_WINDOW} ticks.</p>
     *
     * @param count   effondrements declenches dans la fenetre
     * @param expires fin de la fenetre, en temps de MONDE
     */
    private record VoidEcho(int count, long expires) { }

    private static final Map<Integer, VoidEcho> VOID_ECHOES = new ConcurrentHashMap<>();

    /** Dix secondes : au-dela, la serie est cassee et le compteur repart de zero. */
    private static final long VOID_ECHO_WINDOW = 200L;

    // ======================================================================================
    //  T396-VOID-GRIP : L'EMPRISE DU VIDE
    // ======================================================================================

    /**
     * Ce que le Vide tient, le Vide le garde.
     *
     * <p><b>Le defaut repare.</b> Les cinq signatures repositionnaient leurs victimes a la
     * main ({@code setPos}, {@code voidPush}) sans jamais declarer qu'elles les TENAIENT.
     * Un seul degat venu d'ailleurs — un seul coup d'un autre mob, une fleche, le joueur
     * lui-meme — suffisait alors : {@code LivingEntity.hurt} appelle {@code knockback}, la
     * cible encaisse une impulsion horizontale, et comme l'enfoncement de la Porte
     * d'Horizon ne corrigeait que l'ordonnee, le corps glissait hors du puits en gardant sa
     * profondeur. L'animation continuait a jouer dans le vide, a cote de sa victime.</p>
     *
     * <p><b>Trois verrous, pas un.</b> Annuler le recul ne suffit pas, et figer la position
     * non plus :</p>
     * <ol>
     *   <li>le <b>recul</b> est refuse a la source ({@link LivingKnockBackEvent}), avant
     *       meme que l'impulsion ne soit ecrite — c'est le seul endroit ou l'on attrape
     *       aussi bien la fleche que le coup d'epee et la detonation de creeper ;</li>
     *   <li>la <b>derive</b> residuelle est effacee a chaque tick, parce qu'une entite
     *       poussee par simple collision n'a jamais declenche d'evenement de recul ;</li>
     *   <li>l'<b>intention</b> est coupee quand {@code fullStun} est demande : un mob dont
     *       l'IA marche encore sort du puits par ses propres pattes, sans aucun degat.</li>
     * </ol>
     *
     * <p><b>Pourquoi une table separee de {@code MOVEMENT_HOLDS}.</b> L'emprise de bois
     * teleporte sa cible sur {@code x, y, z} a chaque tick : elle est parfaite pour un mob
     * qu'on cloue, inutilisable pour les signatures du Vide, dont tout l'interet est de
     * DEPLACER leurs victimes — la sphere les rassemble, la Porte les enfonce, la chaine
     * les tire. L'emprise du Vide ne dicte donc jamais l'ordonnee : elle interdit a
     * quiconque d'AUTRE de bouger le corps, et laisse la signature le mener.</p>
     *
     * @param expires  peremption, en temps de MONDE ({@code getGameTime})
     * @param ax       ancre en x, ou {@code NaN} si la signature mene elle-meme le corps
     * @param az       ancre en z, meme convention
     * @param fullStun coupe l'IA et les attaques de la cible
     */
    private record VoidGrip(long expires, double ax, double az, boolean fullStun) { }

    private static final Map<Integer, VoidGrip> VOID_GRIPS = new ConcurrentHashMap<>();

    /**
     * Pose ou prolonge une emprise. Un deuxieme appel ne REMPLACE pas le premier : il garde
     * la peremption la plus lointaine et le verrou le plus fort, sans quoi le dernier coup
     * d'une rafale — toujours le plus court — relacherait la prise etablie par le premier.
     *
     * @param anchor true pour clouer aussi la position horizontale sur place
     */
    private static void voidGrip(LivingEntity victim, int ticks, boolean anchor, boolean fullStun) {
        if (victim == null || !victim.isAlive()) return;
        if (!(victim.level() instanceof ServerLevel sl)) return;
        long until = sl.getGameTime() + Math.max(1, ticks);
        VoidGrip previous = VOID_GRIPS.get(victim.getId());
        double ax = anchor ? victim.getX() : Double.NaN;
        double az = anchor ? victim.getZ() : Double.NaN;
        if (previous != null) {
            until = Math.max(until, previous.expires());
            fullStun = fullStun || previous.fullStun();
            // L'ancre d'origine prime : elle marque l'endroit ou le corps a ete saisi.
            if (!Double.isNaN(previous.ax())) { ax = previous.ax(); az = previous.az(); }
        }
        rememberSignatureAi(victim);
        VOID_GRIPS.put(victim.getId(), new VoidGrip(until, ax, az, fullStun));
        if (fullStun) {
            if (victim instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
            victim.stopUsingItem();
        }
    }

    /** Relache une emprise et rend l'IA, sans reveiller un corps tenu par une autre prise. */
    private static void releaseVoidGrip(LivingEntity victim) {
        if (victim == null) return;
        if (VOID_GRIPS.remove(victim.getId()) == null) return;
        refreshSignatureAi(victim);
    }

    /** Vrai tant que l'emprise court. Lu par {@link #refreshSignatureAi} et par le recul. */
    private static boolean isVoidGripped(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        VoidGrip grip = VOID_GRIPS.get(living.getId());
        return grip != null && living.level().getGameTime() < grip.expires();
    }

    /** Vrai seulement pour les emprises qui coupent aussi l'intention de la cible. */
    private static boolean isVoidStunned(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        VoidGrip grip = VOID_GRIPS.get(living.getId());
        return grip != null && grip.fullStun() && living.level().getGameTime() < grip.expires();
    }

    /**
     * Tick d'emprise. Il ne touche JAMAIS a l'ordonnee : l'enfoncement de la Porte, le
     * soulevement de la sphere et la traction de la chaine restent maitres du corps sur
     * l'axe vertical. Seule la derive horizontale non voulue est effacee.
     */
    private static void tickVoidGrip(LivingEntity target) {
        if (target == null) return;
        VoidGrip grip = VOID_GRIPS.get(target.getId());
        if (grip == null) return;
        if (!target.isAlive() || target.level().getGameTime() >= grip.expires()) {
            releaseVoidGrip(target);
            return;
        }
        if (grip.fullStun()) {
            if (target instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
        }
        if (Double.isNaN(grip.ax())) return;
        // Ancrage : on remet le corps sur sa colonne de capture. La tolerance evite de
        // lutter contre les corrections sub-pixel du moteur a chaque tick.
        Vec3 motion = target.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z > 1.0E-6) {
            target.setDeltaMovement(0.0, motion.y, 0.0);
        }
        double dx = grip.ax() - target.getX();
        double dz = grip.az() - target.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            target.setPos(grip.ax(), target.getY(), grip.az());
            if (target instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetEntityMotionPacket(sp));
            } else {
                target.hurtMarked = true;
            }
        }
    }

    /**
     * Le recul est refuse a tout corps tenu par le Vide. L'evenement est aussi annule quand
     * c'est l'ATTAQUANT qui est tenu : un mob fige ne projette pas ce qu'il frappe.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVoidGripKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;
        if (isVoidGripped(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Gouttes du vide : un ANNEAU de poussiere cyan autour du corps, plus un point blanc.
     *
     * <p>Meme raison que {@code bloodDrops} (T387) : un nuage centre sur l'entite disparait
     * dans son modele. Et meme exigence de lisibilite que le VFX — le gris de
     * {@code DeepElement.VOID} serait invisible, donc la poussiere est cyan.</p>
     */
    private static void voidMotes(ServerLevel sl, LivingEntity victim, int intensity, long seed) {
        if (sl == null || victim == null) return;
        double vx = victim.getX();
        double vz = victim.getZ();
        double w = Math.max(0.35, victim.getBbWidth());
        double h = Math.max(0.60, victim.getBbHeight());
        int ring = Math.max(5, Math.min(20, 5 + intensity * 3));
        for (int i = 0; i < ring; i++) {
            double ang = (i + (seed % 13) * 0.08) / ring * Math.PI * 2.0;
            double rr = w * (0.50 + 0.40 * (((i * 41 + seed) % 11) / 10.0));
            PixelFx.send(sl, VOID_DUST,
                    vx + Math.cos(ang) * rr,
                    victim.getY() + h * (0.14 + 0.62 * (((i * 59 + seed) % 13) / 12.0)),
                    vz + Math.sin(ang) * rr,
                    1, 0.012, 0.012, 0.012, 0.0);
        }
        PixelFx.send(sl, ParticleTypes.PORTAL, vx, victim.getY() + h * 0.55, vz,
                2 + intensity, w * 0.40, h * 0.30, w * 0.40, 0.02);
    }

    /** Cyan d'horizon, accorde sur {@code VoidSignatureVfx.HORIZON} (0x7FD4E8). */
    private static final net.minecraft.core.particles.DustParticleOptions VOID_DUST =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.50f, 0.83f, 0.91f), 1.25f);

    /** Orange de restitution, accorde sur {@code VoidSignatureVfx.INVERT} (0xFF9A4A). */
    private static final net.minecraft.core.particles.DustParticleOptions VOID_DUST_INVERT =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(1.00f, 0.60f, 0.29f), 1.55f);

    /**
     * Degat de la famille Vide : la perte de vie ET la poussiere, toujours ensemble.
     * Meme principe que {@code bloodHurt} (T387) — aucun coup de la famille sans trace.
     */
    private static boolean voidHurt(ServerLevel sl, int attackerId, LivingEntity victim,
                                    float amount) {
        boolean hit = hurtWithCapturedWeapon(sl, attackerId, victim, amount);
        if (hit && sl != null && victim != null) {
            int weight = 2 + (int) Math.min(6.0f, Math.max(0.0f, amount) * 0.75f);
            voidMotes(sl, victim, weight, sl.getGameTime() * 29L + victim.getId());
        }
        return hit;
    }

    /**
     * Pose une empreinte, et DETONE si le quatrieme tombe.
     *
     * <p>L'echeance est absolue : {@code deadline} est calcule par la signature au moment ou
     * elle demarre, et {@code addVoidMark} ne peut que la raccourcir, jamais l'allonger.
     * C'est la lecon T387 appliquee avant d'avoir le bogue.</p>
     */
    private static void addVoidMark(ServerLevel sl, LivingEntity victim, int stacks,
                                    int attackerId, float perStack, long deadline) {
        if (sl == null || victim == null || !victim.isAlive() || stacks <= 0) return;
        long now = sl.getGameTime();
        if (deadline <= now) return;
        VoidMark previous = VOID_MARKS.get(victim.getId());
        int total = stacks;
        float unit = perStack;
        if (previous != null && previous.expires() > now) {
            total = previous.stacks() + stacks;
            unit = Math.max(previous.perStack(), perStack);
        }
        if (total >= VOID_MARK_MAX) {
            voidCollapse(sl, victim, attackerId, unit, total);
            return;
        }
        // On ne RALLONGE jamais : min(nouvelle, ancienne restante) si une empreinte court deja.
        long expiry = previous != null && previous.expires() > now
                ? Math.min(deadline, previous.expires()) : deadline;
        VOID_MARKS.put(victim.getId(), new VoidMark(total, attackerId, expiry, unit));
        voidMotes(sl, victim, 1 + total, now + victim.getId());
        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.30f, 1.30f + total * 0.18f);
    }

    /**
     * L'ESPACE CEDE. Quatre empreintes, et le point ou elles sont posees cesse d'exister une
     * fraction de seconde : implosion, soulevement, et un echo pour le porteur.
     *
     * <p>Le soulevement n'est pas decoratif. C'est la seule facon de rendre la famille
     * lisible en jeu : une creature qui decolle du sol dit « ici, la gravite a lache », et
     * c'est exactement ce que la signature pretend faire.</p>
     */
    private static void voidCollapse(ServerLevel sl, LivingEntity victim, int attackerId,
                                     float perStack, int stacks) {
        if (sl == null || victim == null || !victim.isAlive()) return;
        VOID_MARKS.remove(victim.getId());
        double vx = victim.getX();
        double vy = victim.getY() + victim.getBbHeight() * 0.55;
        double vz = victim.getZ();
        voidHurt(sl, attackerId, victim, perStack * stacks * 1.15f);
        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, true, false));
        PixelFx.send(sl, VOID_DUST, vx, vy, vz, 26, 0.55, 0.65, 0.55, 0.05);
        PixelFx.send(sl, ParticleTypes.REVERSE_PORTAL, vx, vy, vz, 20, 0.30, 0.45, 0.30, 0.22);
        PixelFx.send(sl, VOID_DUST_INVERT, vx, vy, vz, 10, 0.40, 0.40, 0.40, 0.06);
        sl.playSound(null, vx, vy, vz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.80f, 0.62f);
        sl.playSound(null, vx, vy, vz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.45f, 1.45f);
        voidEcho(sl, attackerId);
    }

    /**
     * Un ECHO de plus au porteur. Aux troisieme et septieme, l'espace se desserre autour de
     * lui : vitesse et chute lente.
     *
     * <p><b>Pas de degats en plus.</b> La ferveur du sang donne deja Force a un porteur qui
     * s'acharne ; refaire la meme recompense ici, ce serait avoir ecrit deux fois la meme
     * famille. Le vide paie en terrain.</p>
     */
    private static void voidEcho(ServerLevel sl, int attackerId) {
        if (sl == null || attackerId < 0) return;
        if (!(sl.getEntity(attackerId) instanceof LivingEntity carrier) || !carrier.isAlive()) return;
        long now = sl.getGameTime();
        VoidEcho previous = VOID_ECHOES.get(attackerId);
        int echoes = previous != null && previous.expires() > now ? previous.count() + 1 : 1;
        VOID_ECHOES.put(attackerId, new VoidEcho(echoes, now + VOID_ECHO_WINDOW));
        if (echoes != 3 && echoes != 7) return;
        int level = echoes >= 7 ? 1 : 0;
        carrier.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, level, true, true));
        carrier.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, true));
        PixelFx.send(sl, VOID_DUST, carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.5,
                carrier.getZ(), 18, 0.40, 0.55, 0.40, 0.03);
        sl.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55f, 0.85f + level * 0.25f);
        if (echoes >= 7) VOID_ECHOES.remove(attackerId);
    }

    /**
     * Tick de l'empreinte : la rendre VISIBLE, la faire expirer, et vider les deux cartes.
     *
     * <p><b>Pourquoi un tick alors que l'empreinte ne fait pas de degats ?</b> Parce qu'une
     * ressource a seuil qu'on ne voit pas n'est pas une ressource : si rien ne distingue un
     * corps a trois empreintes d'un corps a une, le joueur ne peut pas decider de frapper
     * celui-la plutot que l'autre, et tout le systeme se reduit a du hasard. Le halo compte
     * donc a voix haute — une graine de poussiere par empreinte, deux fois par seconde.</p>
     *
     * <p>L'expiration est lue sur l'echeance ABSOLUE posee par la signature : rien ici ne
     * peut la repousser, ce qui est la lecon payee en T387 sur l'hemorragie sans fin.</p>
     */
    private static void tickVoidMark(LivingEntity target) {
        if (target == null) return;
        VoidMark mark = VOID_MARKS.get(target.getId());
        if (mark == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!target.isAlive() || now >= mark.expires()) {
            VOID_MARKS.remove(target.getId());
            if (target.isAlive()) {
                // L'empreinte ne s'efface pas en silence : l'espace se referme.
                PixelFx.send(sl, VOID_DUST, target.getX(),
                        target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        4, 0.25, 0.30, 0.25, 0.0);
            }
            return;
        }
        if (now % 10L != 0L) return;
        double w = Math.max(0.35, target.getBbWidth());
        double h = Math.max(0.60, target.getBbHeight());
        // Une graine par empreinte, posees en etoile au-dessus de la tete : on COMPTE.
        for (int i = 0; i < mark.stacks(); i++) {
            double ang = i / (double) VOID_MARK_MAX * Math.PI * 2.0 + now * 0.06;
            PixelFx.send(sl, VOID_DUST,
                    target.getX() + Math.cos(ang) * w * 0.62,
                    target.getY() + h * 1.08,
                    target.getZ() + Math.sin(ang) * w * 0.62,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        // Au troisieme, l'espace tremble deja : le joueur voit venir l'effondrement.
        if (mark.stacks() >= VOID_MARK_MAX - 1 && now % 20L == 0L) {
            PixelFx.send(sl, ParticleTypes.PORTAL, target.getX(),
                    target.getY() + h * 0.55, target.getZ(), 3, w * 0.5, h * 0.3, w * 0.5, 0.04);
        }
    }

    /**
     * Balayage des deux cartes du vide. Appele une fois toutes les cinq secondes, par le
     * tick d'entite deja en place : aucun abonnement supplementaire, et aucune carte
     * statique qui grossit pendant toute une partie.
     */
    private static void sweepVoidTables(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now % 100L != 0L) return;
        VOID_MARKS.entrySet().removeIf(e -> e.getValue().expires() <= now);
        VOID_ECHOES.entrySet().removeIf(e -> e.getValue().expires() <= now);
        VOID_GRIPS.entrySet().removeIf(e -> e.getValue().expires() <= now);
    }

    /**
     * Victimes d'une zone de vide. Meme regle que {@code bloodVictims} : les joueurs sont
     * exclus, donc aucune de ces signatures ne se retourne contre un allie.
     */
    private static List<LivingEntity> voidVictims(ServerLevel sl, double cx, double cy, double cz,
                                                  double radius, double halfHeight, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - halfHeight, cz - radius,
                        cx + radius, cy + halfHeight, cz + radius))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            if (ddx * ddx + ddz * ddz > radius * radius) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * Victimes d'un CORRIDOR : une bande de longueur {@code length} le long de l'axe
     * {@code (ax, az)}, large de {@code halfWidth} de part et d'autre.
     *
     * <p>C'est la forme de zone de la Faille, et elle n'existe nulle part ailleurs dans le
     * mod sous cette forme : la faille est un PLAN, sa trace au sol est une LIGNE. Un disque
     * aurait touche ce qui se trouve devant et derriere la dechirure, c'est-a-dire ce qui
     * n'en est separe par rien. Se placer dans son axe ou a cote doit faire la difference.</p>
     */
    private static List<LivingEntity> voidCorridor(ServerLevel sl, double cx, double cy, double cz,
                                                   double ax, double az, double length,
                                                   double halfWidth, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        double reach = length * 0.5;
        double span = reach + halfWidth;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - span, cy - 2.6, cz - span, cx + span, cy + 2.6, cz + span))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            double along = ddx * ax + ddz * az;              // projection sur l'axe
            double across = ddx * (-az) + ddz * ax;          // distance signee a l'axe
            if (Math.abs(along) > reach) continue;
            if (Math.abs(across) > halfWidth) continue;
            out.add(candidate);
        }
        return out;
    }

    /**
     * Routage des CINQ signatures du vide.
     *
     * <p>Les cinq partagent l'empreinte et l'echo, et se repondent par le VERBE : la sphere
     * RASSEMBLE (elle tire tout vers un point), le prisme DISTRIBUE (un faisceau par corps),
     * la faille TRIE (une ligne, pas un disque), la porte EXPULSE (elle avale et recrache
     * ailleurs), la trame LIE (ce qui touche un noeud touche tous les noeuds). Chacune traite
     * explicitement les deux cas demandes — <b>plusieurs ennemis</b> et <b>ennemi seul</b>.</p>
     */
    private static void applyVoidGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.VOID_WARP_SPHERE) {
            applyWarpSphereGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.DRAGON_CHAOS_PRISM) {
            applyDragonPrismGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.NULL_RIFT_FISSURE) {
            applyNullRiftGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.HORIZON_GATE) {
            applyHorizonGateGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.CHAIN_COLLAPSE) {
            applyChainCollapseGameplay(attacker, target, sl, damage);
        }
    }

    // --------------------------------------------------------------------------------------
    //  I. SPHERE DE DISTORSION (160 ticks) — elle RASSEMBLE
    // --------------------------------------------------------------------------------------

    /**
     * La sphere est un AIMANT. Son interet n'est pas de frapper fort, c'est de mettre six
     * ennemis au meme endroit — apres quoi n'importe quelle autre arme les prend tous.
     *
     * <p><b>Foule</b> : six tractions de cinq ticks, chacune ramene vers le centre a une
     * vitesse proportionnelle a la distance ; Lenteur II pendant la duree. <b>Duel</b> : il
     * n'y a personne a rassembler, donc la sphere change de metier — elle s'accroche a
     * l'unique cible, la souleve, et chaque traction tape deux fois et demie plus fort.
     * C'est le meme objet, pas la meme utilite, et aucun des deux cas n'est decevant.</p>
     */
    private static void applyWarpSphereGameplay(LivingEntity attacker, LivingEntity target,
                                                ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.4f, weaponDamage * 0.82f);
        final long markEnd = sl.getGameTime() + 160L + 20L;
        final boolean solo = voidVictims(sl, ox, oy, oz, 5.0, 3.0, attackerId).size() <= 1;

        // --- 0 -> 18 : l'amorce. On previent avant de tirer, comme la foudre et le sang. ---
        for (int k = 0; k < 5; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + step * 4, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                        0.22f + 0.07f * step, 0.45f + 0.09f * step);
                PixelFx.send(sl, VOID_DUST, ox, oy, oz, 6 + step * 3,
                        2.2 - step * 0.3, 1.2, 2.2 - step * 0.3, 0.02);
            });
        }
        TestProcedure.schedule(sl, now + 20, () ->
                sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                        0.75f, 0.45f));

        // --- 22 -> 52 : SIX TRACTIONS. Le coeur de la signature. ---
        for (int k = 0; k < 6; k++) {
            final int pull = k;
            TestProcedure.schedule(sl, now + 22 + pull * 5, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, 5.0, 3.0, attackerId);
                for (LivingEntity victim : victims) {
                    double ddx = ox - victim.getX();
                    double ddz = oz - victim.getZ();
                    double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (dist > 0.25) {
                        // Vitesse PROPORTIONNELLE a la distance : ce qui est loin arrive vite,
                        // ce qui est pres ne traverse pas le centre. Une vitesse constante
                        // ferait osciller les proches autour du point comme un pendule.
                        double speed = Math.min(0.78, 0.17 + dist * 0.085);
                        voidPush(sl, victim, ddx / dist * speed, 0.055, ddz / dist * speed);
                    }
                    // T396-GRIP : la sphere MENE ses victimes, elle ne les cloue pas —
                    // donc pas d'ancre. Le bail de douze ticks est renouvele par la
                    // traction suivante (toutes les cinq) et expire de lui-meme a la
                    // derniere, ce qui rend l'IA juste avant l'effondrement.
                    voidGrip(victim, 12, false, true);
                    voidHurt(sl, attackerId, victim, base * (solo ? 1.35f : 0.55f));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, false));
                    if (solo) {
                        // Duel : il n'y a rien a rassembler, alors la sphere SOULEVE.
                        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0, true, true));
                    }
                    if ((pull & 1) == 0) {
                        addVoidMark(sl, victim, 1, attackerId, base * 0.28f, markEnd);
                    }
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.40f, 0.70f + pull * 0.09f);
                PixelFx.send(sl, VOID_DUST, ox, oy, oz, 14, 1.4, 0.9, 1.4, 0.05);
            });
        }

        // --- 112 : L'EFFONDREMENT. Tout ce qui a ete rassemble paie d'un coup. ---
        TestProcedure.schedule(sl, now + 112, () -> {
            PixelFx.send(sl, ParticleTypes.REVERSE_PORTAL, ox, oy, oz, 50, 0.6, 0.8, 0.6, 0.35);
            PixelFx.send(sl, VOID_DUST, ox, oy, oz, 40, 1.0, 1.0, 1.0, 0.10);
            sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.38f);
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.70f, 0.55f);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 3.2, 2.4, attackerId)) {
                voidHurt(sl, attackerId, victim, base * (solo ? 1.60f : 0.95f));
                addVoidMark(sl, victim, 2, attackerId, base * 0.30f, markEnd);
            }
            // Le porteur a tenu a cote d'une etoile qui s'effondre : l'espace est lache
            // autour de lui pendant cinq secondes.
            if (attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, true));
            }
        });

        // --- 128 : LA RESTITUTION. Ce qui a ete avale ressort, et repousse. ---
        TestProcedure.schedule(sl, now + 128, () -> {
            PixelFx.send(sl, VOID_DUST_INVERT, ox, oy, oz, 44, 1.6, 0.9, 1.6, 0.26);
            sl.playSound(null, ox, oy, oz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.85f, 0.75f);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 4.6, 2.6, attackerId)) {
                double ddx = victim.getX() - ox;
                double ddz = victim.getZ() - oz;
                double dist = Math.max(0.3, Math.sqrt(ddx * ddx + ddz * ddz));
                // T396-GRIP : on RELACHE avant de projeter. Une emprise encore active
                // effacerait la composante horizontale du rejet au tick suivant.
                releaseVoidGrip(victim);
                voidPush(sl, victim, ddx / dist * 0.62, 0.34, ddz / dist * 0.62);
                voidHurt(sl, attackerId, victim, base * 0.70f);
                addVoidMark(sl, victim, 1, attackerId, base * 0.26f, markEnd);
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  II. PRISME DU DRAGON (190 ticks) — il DISTRIBUE
    // --------------------------------------------------------------------------------------

    /**
     * Le prisme est un REPARTITEUR : sept coups, sept corps, un chacun.
     *
     * <p><b>T395</b> : ce que le client dessine n'est plus un faisceau mais une LANCE qui
     * se plante — memes ticks, memes cibles, meme repartition. Le serveur n'a pas bouge,
     * seuls le son et la gerbe suivent l'arme : un impact, plus un carillon.</p>
     *
     * <p><b>Foule</b> : jusqu'a sept victimes distinctes recoivent un coup, portees en
     * sequence ; chacune est marquee ET rendue visible a travers les murs (Lueur), ce qui
     * est le seul debuff franchement UTILE de la famille — on voit qui porte une empreinte.
     * <b>Duel</b> : moins de trois corps et les sept faisceaux CONVERGENT sur le meme ;
     * chaque trait tape moins, mais sept traits sur un seul corps posent quatre empreintes,
     * donc la detonation est garantie. Repartir recompense la foule, converger recompense le
     * duel : la meme arme, deux lectures.</p>
     */
    private static void applyDragonPrismGameplay(LivingEntity attacker, LivingEntity target,
                                                 ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.5f, weaponDamage * 0.88f);
        final long markEnd = sl.getGameTime() + 190L + 20L;
        final int targetId = target.getId();
        final boolean solo = voidVictims(sl, ox, oy, oz, 6.5, 3.0, attackerId).size() < 3;

        // --- 0 -> 20 : la descente, en trois a-coups. On l'entend se poser. ---
        for (int k = 0; k < 3; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 4 + step * 6, () -> {
                sl.playSound(null, ox, oy + 2.0 - step * 0.7, oz, SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.55f, 0.60f + step * 0.16f);
                PixelFx.send(sl, VOID_DUST, ox, oy + 2.2 - step * 0.7, oz, 10, 0.5, 0.2, 0.5, 0.02);
            });
        }
        // --- 42 : le trait d'entree. ---
        TestProcedure.schedule(sl, now + 42, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.80f, 1.35f);
            PixelFx.send(sl, ParticleTypes.END_ROD, ox, oy, oz, 26, 0.35, 0.35, 0.35, 0.08);
        });

        // --- 62 -> 125 : SEPT LANCES, une toutes les neuf frames. ---
        for (int b = 0; b < 7; b++) {
            final int beam = b;
            TestProcedure.schedule(sl, now + 62 + beam * 9, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, 6.5, 3.0, attackerId);
                LivingEntity hit;
                if (solo || victims.isEmpty()) {
                    // CONVERGENCE : tout sur la cible d'origine.
                    hit = sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? le
                            : (victims.isEmpty() ? null : victims.get(0));
                } else {
                    // REPARTITION : un corps distinct par lance, en tournant.
                    hit = victims.get(beam % victims.size());
                }
                if (hit == null) return;
                // T396-GRIP : une lance qui TRAVERSE un corps le cloue sur place. Ancre
                // ferme pendant quatorze ticks : le temps que la hampe ressorte, et la
                // victime ne derive plus d'un coup de recul recu entre deux lances.
                voidGrip(hit, 14, true, true);
                float amount = base * (solo ? 0.46f : 0.62f);
                voidHurt(sl, attackerId, hit, amount);
                hit.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, true, false));
                addVoidMark(sl, hit, 1, attackerId, base * 0.26f, markEnd);
                // La gerbe part du TORSE et vers le bas : quelque chose a traverse, ce
                // n'est plus une lumiere qui effleure.
                PixelFx.send(sl, VOID_DUST, hit.getX(),
                        hit.getY() + hit.getBbHeight() * 0.6, hit.getZ(), 16, 0.18, 0.26, 0.18, 0.09);
                PixelFx.send(sl, ParticleTypes.END_ROD, hit.getX(),
                        hit.getY() + hit.getBbHeight() * 0.6, hit.getZ(), 8, 0.22, 0.30, 0.22, 0.06);
                sl.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                        0.34f, 1.45f + beam * 0.05f);
                sl.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.34f, 0.95f + beam * 0.07f);
            });
        }

        // --- 150 : LA FRACTURE. Le prisme se casse, et ce qu'il a pris manque aux victimes. ---
        TestProcedure.schedule(sl, now + 150, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.95f, 0.70f);
            PixelFx.send(sl, VOID_DUST, ox, oy, oz, 34, 0.8, 0.8, 0.8, 0.14);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 6.5, 3.0, attackerId)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1, true, false));
            }
            if (attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, true, true));
            }
        });

        // --- 170 : L'ECLAT. L'anneau au sol et la colonne. ---
        TestProcedure.schedule(sl, now + 170, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.90f, 0.85f);
            PixelFx.send(sl, VOID_DUST_INVERT, ox, target.getY() + 0.2, oz, 40, 2.0, 0.25, 2.0, 0.05);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 4.4, 2.6, attackerId)) {
                voidHurt(sl, attackerId, victim, base * 0.80f);
                victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0, true, true));
                addVoidMark(sl, victim, 1, attackerId, base * 0.28f, markEnd);
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  III. FAILLE DU NEANT (170 ticks) — elle TRIE
    // --------------------------------------------------------------------------------------

    /**
     * La faille est une LIGNE. C'est sa seule idee, et elle la tient jusqu'au bout.
     *
     * <p>Son aire n'est pas un disque mais un corridor de neuf blocs de long sur trois de
     * large, perpendiculaire a l'axe du coup — la dechirure est un plan, sa trace au sol est
     * une ligne. Se tenir dans son axe ou a deux pas de cote change tout, et c'est la seule
     * signature du mod ou le placement lateral decide.</p>
     *
     * <p><b>Foule</b> : neuf aspirations tirent vers l'axe, puis la fermeture ejecte
     * perpendiculairement — on entre par le cote et on ressort par le cote. <b>Duel</b> : une
     * seule victime n'est pas tiree, elle est RETENUE ({@code applyMovementHold} 70 ticks) et
     * chaque aspiration tape presque deux fois plus fort : elle ne peut pas sortir du
     * corridor, la faille prend donc son temps.</p>
     */
    private static void applyNullRiftGameplay(LivingEntity attacker, LivingEntity target,
                                              ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.2f, weaponDamage * 0.76f);
        final long markEnd = sl.getGameTime() + 170L + 20L;

        // L'axe du corridor : PERPENDICULAIRE a la direction du coup, parce que la dechirure
        // s'ouvre en travers du chemin de l'arme. Normalise, et avec un repli si l'attaquant
        // et la cible sont exactement superposes.
        double rawX = attacker == null ? 1.0 : target.getX() - attacker.getX();
        double rawZ = attacker == null ? 0.0 : target.getZ() - attacker.getZ();
        double rawLen = Math.sqrt(rawX * rawX + rawZ * rawZ);
        if (rawLen < 0.001) { rawX = 1.0; rawZ = 0.0; rawLen = 1.0; }
        final double axX = -rawZ / rawLen;
        final double axZ = rawX / rawLen;

        final boolean solo = voidCorridor(sl, ox, oy, oz, axX, axZ, 9.0, 1.6, attackerId).size() <= 1;

        // --- 0 -> 12 : la felure. Presque rien, et c'est voulu. ---
        TestProcedure.schedule(sl, now + 2, () ->
                sl.playSound(null, ox, oy, oz, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.35f, 1.80f));
        TestProcedure.schedule(sl, now + 14, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.70f, 0.50f);
            PixelFx.send(sl, VOID_DUST, ox, oy, oz, 22, 0.20, 1.4, 0.20, 0.04);
        });

        // --- DUEL : la victime unique est RETENUE. Elle ne peut pas sortir du corridor. ---
        if (solo) {
            applyMovementHold(target, sl.getGameTime() + 70L, false);
            // T396-GRIP : l'emprise de bois reposait la cible chaque tick SANS interdire
            // le recul — on la voyait partir puis revenir d'une saccade a chaque coup
            // encaisse. L'emprise du Vide refuse l'impulsion a la source.
            voidGrip(target, 70, true, true);
        }

        // --- 20 -> 100 : NEUF ASPIRATIONS, toutes les dix frames. ---
        for (int k = 0; k < 9; k++) {
            final int pull = k;
            TestProcedure.schedule(sl, now + 20 + pull * 10, () -> {
                List<LivingEntity> victims = voidCorridor(sl, ox, oy, oz, axX, axZ, 9.0, 1.6, attackerId);
                for (LivingEntity victim : victims) {
                    double ddx = victim.getX() - ox;
                    double ddz = victim.getZ() - oz;
                    // On ne tire pas vers le CENTRE mais vers l'AXE : composante
                    // perpendiculaire seule. Tirer vers le centre entasserait tout le
                    // corridor en un point et la ligne ne se lirait plus.
                    double across = ddx * (-axZ) + ddz * axX;
                    if (!solo && Math.abs(across) > 0.20) {
                        double dir = across > 0 ? -1.0 : 1.0;
                        double speed = Math.min(0.46, 0.10 + Math.abs(across) * 0.14);
                        voidPush(sl, victim, -axZ * dir * speed, 0.035, axX * dir * speed);
                    }
                    // T396-GRIP : la faille ASPIRE vers son axe, donc pas d'ancre en
                    // foule ; en duel l'ancre posee plus haut tient deja.
                    voidGrip(victim, 12, false, true);
                    voidHurt(sl, attackerId, victim, base * (solo ? 0.52f : 0.30f));
                    if (pull % 3 == 0) {
                        addVoidMark(sl, victim, 1, attackerId, base * 0.30f, markEnd);
                    }
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.30f, 0.55f + pull * 0.05f);
                for (int s = -1; s <= 1; s += 2) {
                    PixelFx.send(sl, VOID_DUST, ox + axX * 2.6 * s, target.getY() + 0.25, oz + axZ * 2.6 * s,
                            8, 0.9, 0.12, 0.9, 0.03);
                }
            });
        }

        // --- 112 : L'AUTRE COTE. Ce qu'elles ont vu ne s'oublie pas tout de suite. ---
        TestProcedure.schedule(sl, now + 112, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.55f, 1.40f);
            PixelFx.send(sl, ParticleTypes.PORTAL, ox, oy, oz, 46, 0.25, 1.3, 0.25, 0.30);
            for (LivingEntity victim : voidCorridor(sl, ox, oy, oz, axX, axZ, 9.0, 1.6, attackerId)) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, true, false));
            }
        });

        // --- 140 : LA FERMETURE. Les levres se rejoignent, et ce qui etait dedans part. ---
        TestProcedure.schedule(sl, now + 140, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.70f, 1.55f);
            sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.85f, 0.70f);
            PixelFx.send(sl, VOID_DUST_INVERT, ox, oy, oz, 34, 0.35, 1.3, 0.35, 0.16);
            for (LivingEntity victim : voidCorridor(sl, ox, oy, oz, axX, axZ, 9.0, 1.6, attackerId)) {
                voidHurt(sl, attackerId, victim, base * 1.10f);
                addVoidMark(sl, victim, 2, attackerId, base * 0.32f, markEnd);
                double ddx = victim.getX() - ox;
                double ddz = victim.getZ() - oz;
                double across = ddx * (-axZ) + ddz * axX;
                double dir = across >= 0 ? 1.0 : -1.0;
                releaseVoidGrip(victim);
                voidPush(sl, victim, -axZ * dir * 0.70, 0.30, axX * dir * 0.70);
            }
            if (attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, true));
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  IV. PORTE D'HORIZON (180 ticks) — elle EXPULSE
    // --------------------------------------------------------------------------------------

    /**
     * La porte est un TOURNIQUET. Les quatre autres signatures gardent leurs victimes dans
     * la zone ; celle-ci les en SORT, une par une, et chacune ressort dans une direction
     * differente de la precedente.
     *
     * <p><b>L'ENGLOUTISSEMENT</b> (T395) est ce qui se passe ENTRE les transits. La porte
     * est au sol : ce qui tombe dedans ne reste pas dessus. Chaque corps pris dans l'anneau
     * s'enfonce de son propre compte, lentement — cent trente-quatre ticks pour la pleine
     * profondeur — et la fermeture le rend a la surface. Trois regles que ca impose :</p>
     * <ul>
     *   <li>on ne touche QUE l'ordonnee. Les transits poussent a l'horizontale, et une
     *       reposition complete chaque tick aurait annule le tourniquet ;</li>
     *   <li>la profondeur est plafonnee a la hauteur des YEUX moins 0,28 bloc, donc le
     *       regard reste dehors. Au-dela, {@code isInWall()} facture de l'asphyxie a
     *       chaque tick et la porte deviendrait une arme a degats continus — ce qu'elle
     *       n'est pas, sa monnaie est la POSITION ;</li>
     *   <li>un corps expulse par un transit est rendu a la surface AVANT d'etre pousse,
     *       sinon sa trajectoire part d'un bloc sous le sol.</li>
     * </ul>
     *
     * <p><b>Foule</b> : sept transits, chacun prend le corps le plus proche de l'anneau,
     * l'envoie par-dessus et le repose a trois metres sur un azimut qui tourne — au bout des
     * sept, le paquet est dissemine en etoile. C'est l'exact inverse de la sphere, et c'est
     * pour ca que les deux coexistent. <b>Duel</b> : il n'y a rien a disseminer, donc la
     * porte devient un MOULIN — la meme victime repasse a chaque transit, chasse d'un cote
     * puis de l'autre, et chaque passage tape deux fois plus fort. Elle ne va nulle part,
     * mais elle n'arrete jamais de tomber.</p>
     */
    private static void applyHorizonGateGameplay(LivingEntity attacker, LivingEntity target,
                                                 ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.3f, weaponDamage * 0.80f);
        final long markEnd = sl.getGameTime() + 180L + 20L;
        final boolean solo = voidVictims(sl, ox, oy, oz, 4.2, 2.8, attackerId).size() <= 1;
        // L'azimut de depart est tire de la position : deux portes voisines ne disseminent
        // pas dans le meme sens, sinon la deuxieme defait le travail de la premiere.
        final double seedAng = ((long) (ox * 31.0) + (long) (oz * 17.0) & 63L) / 64.0 * Math.PI * 2.0;

        // --- 0 -> 18 : le TRACE au sol. L'empreinte existe avant la porte. ---
        for (int k = 0; k < 4; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + step * 5, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                        0.20f + 0.06f * step, 0.60f + 0.12f * step);
                for (int i = 0; i < 10; i++) {
                    double ang = (i + step * 2.5) / 10.0 * Math.PI * 2.0;
                    PixelFx.send(sl, VOID_DUST, ox + Math.cos(ang) * 2.12,
                            target.getY() + 0.08, oz + Math.sin(ang) * 2.12, 2, 0.04, 0.02, 0.04, 0.01);
                }
            });
        }

        // --- 24 : L'OUVERTURE. ---
        TestProcedure.schedule(sl, now + 24, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.80f, 0.55f);
            PixelFx.send(sl, ParticleTypes.PORTAL, ox, oy, oz, 52, 1.6, 0.8, 1.6, 0.26);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 2.4, 2.4, attackerId)) {
                addVoidMark(sl, victim, 1, attackerId, base * 0.26f, markEnd);
            }
        });

        // --- 16 -> 172 : L'ENGLOUTISSEMENT, puis la restitution. ---
        // Un etat par corps, et pas une profondeur commune : un corps qui tombe dans
        // l'anneau en retard commence a zero, sinon il se teleporterait a la profondeur
        // des autres. {surface, avancement, dernier tick vu dans l'anneau}.
        final Map<Integer, double[]> swallowed = new HashMap<>();
        final Map<Integer, Integer> spatOut = new HashMap<>();
        final int sinkEnd = now + 150;
        for (int d = 16; d <= 172; d += 2) {
            final int at = now + d;
            TestProcedure.schedule(sl, at, () -> {
                boolean rising = at > sinkEnd;
                if (!rising) {
                    for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 2.90, 3.0, attackerId)) {
                        int id = victim.getId();
                        Integer grace = spatOut.get(id);
                        if (grace != null && at < grace) continue;
                        double[] st = swallowed.get(id);
                        if (st == null) {
                            // Pas en l'air : on enregistre la SURFACE, et un corps qui
                            // retombe encore n'en a pas encore une.
                            if (!victim.onGround()) continue;
                            st = new double[] { victim.getY(), 0.0, at };
                            swallowed.put(id, st);
                        }
                        st[2] = at;
                        // T396-GRIP : LE correctif de l'enfoncement. On ne corrigeait que
                        // l'ordonnee ; un seul coup recu d'ailleurs poussait le corps hors
                        // de l'anneau en lui laissant sa profondeur, et le puits continuait
                        // a jouer a cote de sa victime. L'ancre cloue la colonne de
                        // capture, le bail de dix ticks est renouvele toutes les deux
                        // frames tant que le corps est tenu, et s'eteint seul des qu'il
                        // sort — la remontee reste libre.
                        voidGrip(victim, 10, true, true);
                    }
                }
                // Copie des cles : on retire des corps en cours de route (morts, rendus),
                // et une carte qu'on modifie pendant qu'on la parcourt leve.
                for (Integer id : new ArrayList<>(swallowed.keySet())) {
                    double[] st = swallowed.get(id);
                    if (st == null) continue;
                    if (!(sl.getEntity(id) instanceof LivingEntity body) || !body.isAlive()) {
                        swallowed.remove(id);
                        continue;
                    }
                    // DOUCEMENT : 134 ticks pour descendre, 22 pour remonter. Un corps
                    // sorti de l'anneau remonte tout seul — la porte ne tient que ce qui
                    // est dedans.
                    boolean held = !rising && st[2] == at;
                    st[1] = held ? Math.min(1.0, st[1] + 2.0 / 134.0)
                                 : Math.max(0.0, st[1] - 2.0 / 22.0);
                    // Le plafond est mesure sur les YEUX et pas sur la taille : un corps
                    // trapu (araignee, 0,90 de haut, oeil a 0,585) passait sous la surface
                    // avec un simple ratio, et isInWall() facture de l'asphyxie a chaque
                    // tick. Vingt-huit centimetres de marge au-dessus du regard.
                    double cap = Math.min(1.35, Math.max(0.0, body.getEyeHeight() - 0.28));
                    double deep = cap * st[1];
                    body.setPos(body.getX(), st[0] - deep, body.getZ());
                    Vec3 motion = body.getDeltaMovement();
                    body.setDeltaMovement(motion.x, 0.0, motion.z);
                    body.fallDistance = 0.0f;
                    if (st[1] <= 0.0) { swallowed.remove(id); continue; }
                    if (st[1] > 0.05 && (at & 7) == 0) {
                        PixelFx.send(sl, VOID_DUST, body.getX(), st[0] + 0.05, body.getZ(),
                                4, 0.30, 0.02, 0.30, 0.01);
                    }
                }
            });
        }

        // --- 34 -> 142 : SEPT TRANSITS, toutes les dix-huit frames. ---
        for (int k = 0; k < 7; k++) {
            final int pass = k;
            TestProcedure.schedule(sl, now + 34 + pass * 18, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, 3.4, 2.8, attackerId);
                if (victims.isEmpty()) return;
                // Le corps le plus proche de l'anneau : la porte avale ce qui est tombe
                // dedans, pas ce qui passait au large.
                LivingEntity taken = victims.get(0);
                double bestGap = Double.MAX_VALUE;
                for (LivingEntity candidate : victims) {
                    double ddx = candidate.getX() - ox;
                    double ddz = candidate.getZ() - oz;
                    double gap = Math.abs(Math.sqrt(ddx * ddx + ddz * ddz) - 2.12);
                    if (gap < bestGap) { bestGap = gap; taken = candidate; }
                }
                final LivingEntity passenger = taken;
                // LA PORTE LE RECRACHE. Il remonte AVANT d'etre pousse : sinon la parabole
                // part d'un bloc sous le sol, et les vingt-six ticks de repit qui suivent
                // laissent l'arc se terminer avant que l'anneau ne le reprenne.
                double[] held = swallowed.remove(passenger.getId());
                if (held != null) {
                    releaseVoidGrip(passenger);
                    passenger.setPos(passenger.getX(), held[0], passenger.getZ());
                }
                spatOut.put(passenger.getId(), now + 34 + pass * 18 + 26);
                // En foule l'azimut TOURNE (dissemination) ; en duel il fait un demi-tour a
                // chaque passage (va-et-vient). Deux lectures du meme geste.
                double outAng = solo
                        ? seedAng + pass * Math.PI
                        : seedAng + pass * (Math.PI * 2.0 / 7.0);
                double throwSpeed = solo ? 0.52 : 0.78;
                voidPush(sl, passenger, Math.cos(outAng) * throwSpeed, solo ? 0.46 : 0.58,
                        Math.sin(outAng) * throwSpeed);
                voidHurt(sl, attackerId, passenger, base * (solo ? 0.92f : 0.45f));
                passenger.addEffect(new MobEffectInstance(MobEffects.LEVITATION, solo ? 18 : 26,
                        0, true, false));
                if (pass % 2 == 0) {
                    addVoidMark(sl, passenger, 1, attackerId, base * 0.28f, markEnd);
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                        0.55f, 0.70f + pass * 0.06f);
                PixelFx.send(sl, VOID_DUST, ox, oy, oz, 14, 1.1, 0.6, 1.1, 0.05);
                PixelFx.send(sl, VOID_DUST_INVERT, passenger.getX(),
                        passenger.getY() + passenger.getBbHeight() * 0.60, passenger.getZ(),
                        18, 0.30, 0.45, 0.30, 0.12);
            });
        }

        // --- 150 : LA FERMETURE. Le rayon de l'ame se resorbe, la section tient. ---
        TestProcedure.schedule(sl, now + 150, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.75f, 0.60f);
            sl.playSound(null, ox, oy, oz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.50f, 1.70f);
            PixelFx.send(sl, VOID_DUST_INVERT, ox, oy, oz, 40, 1.2, 0.9, 1.2, 0.18);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 3.4, 2.8, attackerId)) {
                double[] st = swallowed.get(victim.getId());
                // CE QU'ELLE A LE PLUS AVALE PAIE LE PLUS. Rester dans l'anneau coute, en
                // sortir se paie en transits : les deux lectures ont un prix.
                float deep = st == null ? 0.0f : (float) st[1];
                voidHurt(sl, attackerId, victim, base * (1.25f + 0.45f * deep));
                addVoidMark(sl, victim, 2, attackerId, base * 0.32f, markEnd);
            }
        });

        // --- 172 : LA RESTITUTION. Ce qu'elle a pris, elle le rend — a la surface, et de
        //     travers. L'ejection etait a la fermeture ; elle y annulait la remontee, un
        //     corps pousse en l'air pendant qu'on lui tient l'ordonnee ne va nulle part.
        TestProcedure.schedule(sl, now + 172, () -> {
            boolean any = false;
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 3.4, 2.8, attackerId)) {
                // Filet de securite : si un arrondi a laisse un corps au fond, il remonte
                // ici. La porte ne garde personne.
                double[] st = swallowed.remove(victim.getId());
                if (st != null) victim.setPos(victim.getX(), st[0], victim.getZ());
                releaseVoidGrip(victim);
                double ddx = victim.getX() - ox;
                double ddz = victim.getZ() - oz;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist < 0.05) { ddx = Math.cos(seedAng); ddz = Math.sin(seedAng); dist = 1.0; }
                voidPush(sl, victim, ddx / dist * 0.82, 0.46, ddz / dist * 0.82);
                PixelFx.send(sl, VOID_DUST_INVERT, victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.40, victim.getZ(),
                        16, 0.28, 0.40, 0.28, 0.10);
                any = true;
            }
            for (Integer id : swallowed.keySet()) {
                double[] st = swallowed.get(id);
                if (st != null && sl.getEntity(id) instanceof LivingEntity body && body.isAlive()) {
                    body.setPos(body.getX(), st[0], body.getZ());
                }
            }
            swallowed.clear();
            if (any) {
                sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                        0.70f, 0.55f);
            }
        });

        // --- 168 : le porteur. Mobilite, jamais degats. ---
        TestProcedure.schedule(sl, now + 168, () -> {
            if (attacker == null || !attacker.isAlive()) return;
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, true));
        });
    }

    // --------------------------------------------------------------------------------------
    //  V. CONSTELLATION NULLE (200 ticks) — elle LIE
    // --------------------------------------------------------------------------------------

    /**
     * La trame est un CONTRAT. Les corps presents au moment ou elle se noue deviennent
     * solidaires : a partir de la, ce qui arrive a l'un arrive a tous, ou qu'ils aillent.
     *
     * <p>C'est la seule signature du mod dont la zone est <b>figee a la pose</b> : la liste
     * est capturee une fois, et fuir n'en sort pas. Les quatre autres signatures du vide
     * reinterrogent le monde a chaque battement et laissent donc toujours une porte de
     * sortie ; celle-ci est le prix a payer pour qu'un lien veuille dire quelque chose.</p>
     *
     * <p><b>Foule</b> : jusqu'a sept noeuds, onze mises en tension, et chaque tension frappe
     * TOUT LE MONDE pour une petite part — le total par corps est modeste, le total sur le
     * groupe est enorme. <b>Duel</b> : une trame a un seul noeud n'a pas d'arete, alors elle
     * se referme sur sa victime — celle-ci est RETENUE quatre secondes et prend l'integralite
     * des tensions pour elle seule.</p>
     */
    /**
     * V. L'EFFONDREMENT EN CHAINE (200 ticks) — elle PROPAGE.
     *
     * <p><b>La mecanique, et pourquoi elle n'existe nulle part ailleurs dans le mod.</b> Les
     * quatre autres signatures du Vide choisissent leurs victimes une fois (ou a chaque
     * battement) et les traitent EN PARALLELE. Celle-ci n'en touche qu'une a la fois, et
     * c'est la victime elle-meme qui designe la suivante : un seul corps est amorce, il
     * implose au bout de son compte a rebours, et l'implosion amorce le corps VIVANT LE PLUS
     * PROCHE qui n'a pas encore servi. La signature est donc une sequence, pas une salve.</p>
     *
     * <p><b>L'escalade.</b> Chaque maillon frappe {@code x1,35} plus fort que le precedent et
     * son compte a rebours est plus court ({@code 40 - maillon x 5}, plancher 14). Une chaine
     * de six corps finit par une detonation enorme ; une chaine d'un seul corps ne finit par
     * rien du tout. <b>C'est la premiere signature du mod dont la puissance depend de la
     * DENSITE et de l'ESPACEMENT des ennemis</b>, et non du nombre brut : six mobs colles les
     * uns aux autres donnent une chaine complete, six mobs disperses a dix blocs n'en donnent
     * aucune.</p>
     *
     * <p><b>Le renforcement, et pourquoi il est voulu.</b> Chaque implosion aspire sa victime
     * VERS le maillon suivant. Les corps se rapprochent donc a mesure que la chaine court, ce
     * qui rend le maillon d'apres plus facile a trouver. La signature s'auto-alimente : c'est
     * exactement la lecture que doit avoir une reaction en chaine, et c'est ce que montre
     * l'animation.</p>
     *
     * <p><b>Le cas du duel.</b> Sans voisin, la chaine se replie sur sa propre victime : le
     * meme corps est reamorce, mais l'escalade tombe a {@code x1,18} et le compte a rebours
     * reste fixe a 30. On garde une signature jouable en duel sans lui offrir la courbe
     * exponentielle qui est la recompense de la foule.</p>
     */
    private static void applyChainCollapseGameplay(LivingEntity attacker, LivingEntity target,
                                                   ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double oy0 = target.getY() + target.getBbHeight() * 0.50;
        final float base = Math.max(1.3f, weaponDamage * 0.80f);
        final long markEnd = sl.getGameTime() + 200L + 20L;

        // La chaine porte son etat dans un tableau d'une case plutot que dans un champ :
        // une lambda ne capture que du final, et chaque maillon doit pouvoir ecrire ce que
        // le suivant lira. Les identifiants deja consommes sont dans un Set partage.
        final java.util.Set<Integer> burned = new java.util.HashSet<>();
        chainLink(sl, attackerId, attacker, target.getId(), 0, now + 30, base, markEnd,
                target.getX(), oy0, target.getZ(), burned);
    }

    /** Longueur maximale d'une chaine. Au-dela, la detonation finale passerait l'ecran en blanc. */
    private static final int CHAIN_MAX_LINKS = 8;

    /**
     * Un maillon : le compte a rebours, l'implosion, et le relais vers le suivant.
     *
     * <p>La recursion passe par {@code TestProcedure.schedule} et non par un appel direct :
     * chaque maillon est un evenement date, et la pile ne grandit jamais.</p>
     */
    private static void chainLink(ServerLevel sl, int attackerId, LivingEntity attacker,
                                  int victimId, int link, int fireTick, float base,
                                  long markEnd, double ox0, double oy0, double oz0,
                                  java.util.Set<Integer> burned) {
        if (sl == null || link >= CHAIN_MAX_LINKS) return;
        final int depth = link;
        final int who = victimId;

        // --- LE COMPTE A REBOURS. Trois battements avant l'implosion, de plus en plus
        //     rapproches a mesure que la chaine accelere. C'est le seul avertissement
        //     que recoit le joueur, et il doit etre AUDIBLE avant d'etre visible.
        final int span = Math.max(6, (fireTick - TestProcedure.currentTick(sl)) - 2);
        for (int beat = 0; beat < 3; beat++) {
            final int rank = beat;
            TestProcedure.schedule(sl, fireTick - span + span * beat / 3, () -> {
                LivingEntity armed = latticeNode(sl, who);
                if (armed == null) return;
                sl.playSound(null, armed.getX(), armed.getY(), armed.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.30f + rank * 0.12f, 0.62f + rank * 0.20f + depth * 0.05f);
                if (rank == 0) {
                    armed.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            Math.max(20, span + 20), 0, true, false));
                }
                PixelFx.send(sl, VOID_DUST, armed.getX(),
                        armed.getY() + armed.getBbHeight() * 0.5, armed.getZ(),
                        5 + rank * 4, 0.6, 0.6, 0.6, 0.03);
            });
        }

        // --- L'IMPLOSION. ---
        TestProcedure.schedule(sl, fireTick, () -> {
            LivingEntity node = latticeNode(sl, who);
            if (node == null) {
                // Le corps amorce est mort avant son tour : la chaine s'arrete ici, mais
                // elle PAIE quand meme ce qu'elle avait accumule. Sinon tuer le maillon
                // courant serait une parade gratuite — et c'est precisement ce que faisait
                // ce code avant T394, puisque la detonation sortait sur un corps nul sans
                // rien toucher. Elle se fait donc a la position de REPLI, celle de
                // l'implosion precedente.
                chainDetonate(sl, attackerId, attacker, null, depth, base, markEnd,
                        ox0, oy0, oz0, burned);
                return;
            }
            burned.add(who);
            final double nx = node.getX();
            final double ny = node.getY() + node.getBbHeight() * 0.50;
            final double nz = node.getZ();
            final float punch = base * 0.70f * (float) Math.pow(1.35, depth);

            sl.playSound(null, nx, ny, nz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                    0.70f, Math.max(0.35f, 0.58f - depth * 0.045f));
            sl.playSound(null, nx, ny, nz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    0.40f, 0.70f + depth * 0.07f);
            PixelFx.send(sl, ParticleTypes.PORTAL, nx, ny, nz, 30 + depth * 10,
                    0.7 + depth * 0.10, 0.8, 0.7 + depth * 0.10, 0.26);
            PixelFx.send(sl, VOID_DUST_INVERT, nx, ny, nz, 18 + depth * 6, 0.6, 0.6, 0.6, 0.14);

            // T396-GRIP : le maillon est SAISI pendant son implosion. Sans cela le
            // souffle du maillon precedent le deplacait, et l'aspiration vers l'heritier
            // partait d'un point que la chaine n'avait jamais vise.
            voidGrip(node, 16, true, true);
            voidHurt(sl, attackerId, node, punch);
            addVoidMark(sl, node, 1, attackerId, base * 0.28f, markEnd);
            node.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 24 + depth * 4, 0, true, false));

            // Le SOUFFLE de l'implosion : tout ce qui est juste a cote est secoue, pour
            // une fraction du coup. Ce n'est pas une seconde zone de degats, c'est la
            // preuve que l'implosion a un volume.
            double blastR = 1.4 + depth * 0.25;
            for (LivingEntity near : voidVictims(sl, nx, ny, nz, blastR, 2.2, attackerId)) {
                if (near.getId() == who) continue;
                voidHurt(sl, attackerId, near, punch * 0.30f);
            }

            // --- LE RELAIS. Le corps vivant le plus proche qui n'a pas encore servi. ---
            LivingEntity next = null;
            double bestSqr = Double.MAX_VALUE;
            // 7,0 et non 5,5 : c'est la distance a laquelle se tient encore un groupe
            // « disperse », et en dessous la chaine s'arretait au deuxieme maillon.
            for (LivingEntity candidate : voidVictims(sl, nx, ny, nz, 7.0, 3.2, attackerId)) {
                if (burned.contains(candidate.getId())) continue;
                double ddx = candidate.getX() - nx;
                double ddy = candidate.getY() + candidate.getBbHeight() * 0.5 - ny;
                double ddz = candidate.getZ() - nz;
                double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
                if (d2 < bestSqr) { bestSqr = d2; next = candidate; }
            }

            if (next == null) {
                // SOLO, ou plus personne a portee. La chaine se replie sur sa victime :
                // meme corps, escalade reduite, compte a rebours fixe. Deux maillons au
                // plus de cette sorte, apres quoi on detone — un duel ne doit pas durer
                // les deux cents ticks a lui tout seul.
                if (depth < 2 && node.isAlive()) {
                    applyMovementHold(node, sl.getGameTime() + 34L, false);
                    chainLink(sl, attackerId, attacker, who, depth + 1,
                            TestProcedure.currentTick(sl) + 30, base * 0.87f, markEnd,
                            nx, ny, nz, burned);
                    burned.remove(who);
                } else {
                    chainDetonate(sl, attackerId, attacker, node, depth, base, markEnd,
                            nx, ny, nz, burned);
                }
                return;
            }

            // L'ASPIRATION VERS LE SUIVANT. C'est elle qui rend la chaine auto-alimentee :
            // chaque maillon rapproche les corps, donc allonge la chaine d'apres.
            final LivingEntity heir = next;
            double ddx = heir.getX() - nx;
            double ddz = heir.getZ() - nz;
            double flat = Math.sqrt(ddx * ddx + ddz * ddz);
            if (flat > 0.25) {
                double speed = Math.min(0.52, 0.16 + flat * 0.075);
                releaseVoidGrip(node);
                voidPush(sl, node, ddx / flat * speed, 0.18, ddz / flat * speed);
            }
            sl.playSound(null, nx, ny, nz, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS,
                    0.26f, 1.15f + depth * 0.07f);

            // Le porteur est paye en terrain, jamais en Force : a partir du quatrieme
            // maillon la chaine lui rend de la vitesse pour qu'il suive sa propre onde.
            if (depth >= 3 && attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, true));
            }

            int delay = Math.max(14, 40 - depth * 5);
            chainLink(sl, attackerId, attacker, heir.getId(), depth + 1,
                    TestProcedure.currentTick(sl) + delay, base, markEnd, nx, ny, nz, burned);
        });
    }

    /**
     * La detonation finale, au bout de la chaine.
     *
     * <p>Sa force est PROPORTIONNELLE a la longueur parcourue : {@code base x 0,9 x maillons}.
     * C'est la recompense de la densite, et c'est le seul endroit de la famille ou le nombre
     * d'ennemis touches multiplie les degats au lieu de les diviser.</p>
     */
    private static void chainDetonate(ServerLevel sl, int attackerId, LivingEntity attacker,
                                      LivingEntity last, int links, float base, long markEnd,
                                      double ox0, double oy0, double oz0,
                                      java.util.Set<Integer> burned) {
        if (sl == null) return;
        // Position de REPLI quand le dernier corps est mort entre deux maillons : celle de
        // l'implosion precedente. Sortir sur un corps nul revenait a offrir une parade.
        final double dx = last != null ? last.getX() : ox0;
        final double dy = last != null ? last.getY() + last.getBbHeight() * 0.50 : oy0;
        final double dz = last != null ? last.getZ() : oz0;
        final int chain = Math.max(1, links + 1);
        final float blast = base * 0.90f * chain;
        final double radius = 2.0 + 0.30 * chain;

        sl.playSound(null, dx, dy, dz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 1.0f, 0.50f);
        sl.playSound(null, dx, dy, dz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.60f, 1.45f);
        PixelFx.send(sl, ParticleTypes.PORTAL, dx, dy, dz, 70 + chain * 12,
                radius * 0.42, 1.0, radius * 0.42, 0.42);
        PixelFx.send(sl, VOID_DUST_INVERT, dx, dy, dz, 54, radius * 0.35, 0.8, radius * 0.35, 0.24);

        for (LivingEntity victim : voidVictims(sl, dx, dy, dz, radius, 3.0, attackerId)) {
            voidHurt(sl, attackerId, victim, blast);
            addVoidMark(sl, victim, 2, attackerId, base * 0.34f, markEnd);
            double ddx = victim.getX() - dx;
            double ddz = victim.getZ() - dz;
            double flat = Math.sqrt(ddx * ddx + ddz * ddz);
            if (flat < 0.05) { ddx = 1.0; ddz = 0.0; flat = 1.0; }
            voidPush(sl, victim, ddx / flat * 0.74, 0.40, ddz / flat * 0.74);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, false));
        }
        // --- LE GLANAGE. Le relais ne prend que le plus proche non brule, la chaine
        //     plafonne a huit maillons et le souffle d'une implosion ne porte qu'a 1,4
        //     bloc : avec six corps disperses, deux ou trois regardaient la chaine sans
        //     jamais la sentir. Tout corps vivant dans le rayon de travail qui n'a JAMAIS
        //     ete un maillon prend donc sa part — reduite, parce que la regle de la
        //     famille reste que la puissance vient de la densite, mais non nulle, parce
        //     que personne ne doit rester spectateur a trois metres.
        if (burned != null) {
            float gleaning = base * 0.55f;
            for (LivingEntity bystander : voidVictims(sl, dx, dy, dz, radius + 3.4, 3.4, attackerId)) {
                if (burned.contains(bystander.getId())) continue;
                voidHurt(sl, attackerId, bystander, gleaning);
                addVoidMark(sl, bystander, 1, attackerId, base * 0.22f, markEnd);
                bystander.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false));
            }
        }
        if (attacker != null && attacker.isAlive()) {
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, true));
        }
    }

    /**
     * Un corps du Vide, releve par son identifiant.
     *
     * <p>Les signatures du Vide capturent des IDENTIFIANTS, pas des references : une
     * reference retenue cent ticks garde en memoire une entite morte et retirée du monde.
     * Meme precaution que pour les {@code ServerLevel} indexes par dimension. La chaine en
     * depend plus que les autres, puisqu'elle rebondit de corps en corps sur deux cents
     * ticks et que n'importe lequel peut mourir entre deux maillons.</p>
     */
    private static LivingEntity latticeNode(ServerLevel sl, int id) {
        if (sl == null || id < 0) return null;
        return sl.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * Pousse une entite, en tenant compte du cas du joueur.
     *
     * <p>Piege deja paye trois fois dans ce fichier : {@code setDeltaMovement} sur un
     * {@code ServerPlayer} n'est jamais applique cote client tant que le paquet n'est pas
     * envoye, et {@code hurtMarked} ferait en plus repartir le recul vanilla par-dessus.</p>
     */
    private static void voidPush(ServerLevel sl, LivingEntity victim, double vx, double vy, double vz) {
        if (victim == null || !victim.isAlive()) return;
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x * 0.55 + vx, Math.max(motion.y, 0.0) + vy, motion.z * 0.55 + vz);
        victim.hasImpulse = true;
        victim.fallDistance = 0.0f;
        if (victim instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        } else {
            victim.hurtMarked = true;
        }
    }

    // ======================================================================================
    //  T397-CHAOS : LES TROIS SIGNATURES DU CHAOS
    // ======================================================================================

    /** Rose neon de rupture, accorde sur {@code ChaosSignatureVfx.BREAK} (0xFF48C8). */
    private static final net.minecraft.core.particles.DustParticleOptions CHAOS_DUST =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(1.00f, 0.28f, 0.78f), 1.30f);

    /** Cyan electrique, accorde sur {@code ChaosSignatureVfx.SPARK} (0x3FE8FF). */
    private static final net.minecraft.core.particles.DustParticleOptions CHAOS_DUST_SPARK =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.25f, 0.91f, 1.00f), 1.15f);

    /**
     * LA DISCORDANCE — ressource partagee par les trois signatures du Chaos.
     *
     * <p>Chaque famille du mod a une monnaie : le Sang a le saignement, le Vide a
     * l'empreinte. Celle du Chaos est la discordance, et elle ne ressemble a aucune des
     * deux parce qu'elle ne s'additionne pas dans le temps — elle <b>differe</b>. Tout
     * degat du Chaos encaisse une part dans un tampon ; ce tampon ne fait rien, ne tique
     * pas, ne se voit pas, et retombe d'un coup a l'echeance. C'est la traduction exacte de
     * ce que le kaleidoscope montre a l'ecran : une image repetee avec un decalage.</p>
     *
     * <p><b>Pourquoi un tampon et pas des degats sur la duree.</b> Un poison classique
     * recompense celui qui frappe puis s'enfuit ; il rend la fuite optimale. Le report
     * recompense l'inverse : la victime sait ce qui arrive et le joueur sait quand, donc
     * les deux ont une raison de rester. Et comme la somme tombe en UNE fois, elle peut
     * tuer — ce qu'un poison a un demi-coeur par seconde ne fait jamais.</p>
     *
     * @param pending  degats en attente
     * @param dueAt    instant de la retombee, en temps de MONDE
     * @param attackerId porteur a crediter, ou -1
     */
    private record ChaosDiscord(float pending, long dueAt, int attackerId) { }

    private static final Map<Integer, ChaosDiscord> CHAOS_DISCORDS = new ConcurrentHashMap<>();

    /**
     * L'ENTROPIE — compteur propre a la sphere, et le seul debuff a paliers du mod.
     *
     * <p>Il monte tant que la victime reste dans le champ et redescend des qu'elle en sort.
     * Ce n'est pas une nuance : c'est ce qui fait de la sphere une signature de ZONE plutot
     * qu'une signature de cible. Une valeur qui ne redescendrait jamais aurait rendu la
     * sortie inutile, donc la zone decorative.</p>
     *
     * @param level   palier courant, 0 a {@link #CHAOS_ENTROPY_MAX}
     * @param expires peremption si la victime ne revient pas
     */
    private record ChaosEntropy(int level, long expires) { }

    private static final Map<Integer, ChaosEntropy> CHAOS_ENTROPIES = new ConcurrentHashMap<>();

    /** Six paliers : au-dela, le debuff deviendrait une condamnation plutot qu'une pression. */
    private static final int CHAOS_ENTROPY_MAX = 6;

    /**
     * Routage des trois signatures du Chaos. Meme forme que les onze familles precedentes :
     * un aiguillage, et rien d'autre dans cette methode.
     */
    private static void applyChaosGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.CHAOS_KALEIDO_LOOP) {
            applyKaleidoGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.ENTROPY_SPHERE) {
            applyEntropySphereGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.BIG_BANG_SCAR) {
            applyBigBangGameplay(attacker, target, sl, damage);
        }
    }

    /** Degat du Chaos : la perte de vie ET la trace, toujours ensemble. */
    private static boolean chaosHurt(ServerLevel sl, int attackerId, LivingEntity victim,
                                     float amount) {
        boolean hit = hurtWithCapturedWeapon(sl, attackerId, victim, amount);
        if (hit && sl != null && victim != null) {
            PixelFx.send(sl, CHAOS_DUST, victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.60, victim.getZ(),
                    4 + (int) Math.min(8.0f, amount * 0.7f), 0.26, 0.34, 0.26, 0.04);
        }
        return hit;
    }

    /**
     * Met une part de degats en attente. Un second appel avant l'echeance ADDITIONNE la
     * somme sans repousser la date : le report doit rester previsible, sinon une rafale
     * rapide repousserait indefiniment sa propre punition.
     */
    private static void addDiscord(ServerLevel sl, LivingEntity victim, float amount,
                                   int attackerId, int delayTicks) {
        if (sl == null || victim == null || !victim.isAlive() || amount <= 0.0f) return;
        int id = victim.getId();
        long now = sl.getGameTime();
        ChaosDiscord previous = CHAOS_DISCORDS.get(id);
        float total = amount;
        long due = now + Math.max(10, delayTicks);
        if (previous != null && previous.dueAt() > now) {
            total += previous.pending();
            due = previous.dueAt();
        }
        CHAOS_DISCORDS.put(id, new ChaosDiscord(total, due, attackerId));
        victim.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                (int) Math.max(20L, due - now), 0, true, false));
    }

    /**
     * Tick de la discordance. Deux choses, et la seconde est la plus importante : la
     * retombee ne se produit QUE si la victime est encore vivante a l'echeance. Une
     * victime achevee entre-temps ne declenche rien — le report ne doit pas creer de
     * degats fantomes sur un corps deja mort.
     */
    private static void tickChaosDiscord(LivingEntity target) {
        if (target == null) return;
        ChaosDiscord d = CHAOS_DISCORDS.get(target.getId());
        if (d == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!target.isAlive()) { CHAOS_DISCORDS.remove(target.getId()); return; }
        // L'avertissement : une graine rose par demi-seconde, de plus en plus serree a
        // mesure que l'echeance approche. Une ressource a retardement qu'on ne voit pas
        // venir est une punition arbitraire.
        long left = d.dueAt() - now;
        if (left > 0) {
            long period = left < 20L ? 2L : (left < 40L ? 5L : 10L);
            if (now % period == 0L) {
                PixelFx.send(sl, CHAOS_DUST, target.getX(),
                        target.getY() + target.getBbHeight() * 1.05, target.getZ(),
                        2, 0.18, 0.05, 0.18, 0.0);
            }
            return;
        }
        CHAOS_DISCORDS.remove(target.getId());
        float amount = d.pending();
        if (amount <= 0.0f) return;
        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.70f, 0.55f);
        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.45f, 1.70f);
        PixelFx.send(sl, CHAOS_DUST, target.getX(),
                target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                26, 0.35, 0.45, 0.35, 0.16);
        PixelFx.send(sl, CHAOS_DUST_SPARK, target.getX(),
                target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                18, 0.30, 0.40, 0.30, 0.12);
        chaosHurt(sl, d.attackerId(), target, amount);
    }

    /** Monte l'entropie d'un cran et applique le palier atteint. */
    private static void addEntropy(ServerLevel sl, LivingEntity victim, int steps, int holdTicks) {
        if (sl == null || victim == null || !victim.isAlive() || steps <= 0) return;
        int id = victim.getId();
        long now = sl.getGameTime();
        ChaosEntropy previous = CHAOS_ENTROPIES.get(id);
        int level = steps;
        if (previous != null && previous.expires() > now) {
            level = Math.min(CHAOS_ENTROPY_MAX, previous.level() + steps);
        }
        CHAOS_ENTROPIES.put(id, new ChaosEntropy(level, now + Math.max(20, holdTicks)));
        // Les paliers. Chacun ajoute une contrainte differente plutot que d'empiler le
        // meme ralentissement : trois crans de Lenteur ne se distinguent pas a l'oeil,
        // trois contraintes de nature differente si.
        if (level >= 2) {
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 0, true, false));
        }
        if (level >= 4) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 1, true, false));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, true, false));
        }
        if (level >= CHAOS_ENTROPY_MAX) {
            victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 110, 1, true, false));
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, true, false));
        }
    }

    /** Entropie courante, 0 si la victime est sortie du champ assez longtemps. */
    private static int entropyOf(ServerLevel sl, LivingEntity victim) {
        if (sl == null || victim == null) return 0;
        ChaosEntropy e = CHAOS_ENTROPIES.get(victim.getId());
        if (e == null || e.expires() <= sl.getGameTime()) return 0;
        return e.level();
    }

    /** Purge des deux tables du Chaos. Une carte statique qui ne se vide pas est une fuite. */
    private static void sweepChaosTables(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now % 100L != 0L) return;
        CHAOS_ENTROPIES.entrySet().removeIf(e -> e.getValue().expires() <= now);
        // La discordance se retire elle-meme a l'echeance ; on ne balaie que les entrees
        // dont le porteur a disparu sans jamais etre tique (dechargement de chunk).
        CHAOS_DISCORDS.entrySet().removeIf(e -> e.getValue().dueAt() + 200L <= now);
    }

    // --------------------------------------------------------------------------------------
    //  I. BOUCLE KALEIDOSCOPIQUE (150 ticks) — elle REPETE
    // --------------------------------------------------------------------------------------

    /**
     * Six branches, six frappes, et tout ce qui est frappe revient une seconde fois.
     *
     * <p><b>Foule</b> : les six branches prennent six corps DIFFERENTS, en tournant. Chacun
     * repart avec une part de discordance, et les six retombees arrivent ensemble a la
     * fermeture de la boucle — un seul coup d'arme, six punitions simultanees quinze
     * secondes plus tard. <b>Duel</b> : il n'y a personne a repartir, donc les six branches
     * CONVERGENT ; chaque frappe tape pres du double et la discordance s'empile sur le meme
     * corps, ce qui produit la plus grosse retombee unique de la famille. Repartir
     * recompense la foule, converger recompense le duel : la meme arme, deux lectures.</p>
     *
     * <p>Le porteur est paye en VITESSE, jamais en Force : la regle du mod depuis le Sang.</p>
     */
    private static void applyKaleidoGameplay(LivingEntity attacker, LivingEntity target,
                                             ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.4f, weaponDamage * 0.84f);
        final int targetId = target.getId();
        final boolean solo = voidVictims(sl, ox, oy, oz, 5.5, 3.0, attackerId).size() < 2;

        // --- 0 -> 24 : LE MONTAGE. Les six miroirs se mettent en place, un par un, et on
        //     les entend se verrouiller. L'avertissement avant la figure.
        for (int k = 0; k < 6; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + step * 4, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.30f + 0.05f * step, 0.80f + 0.14f * step);
                double ang = step / 6.0 * Math.PI * 2.0;
                PixelFx.send(sl, CHAOS_DUST_SPARK,
                        ox + Math.cos(ang) * 1.9, oy, oz + Math.sin(ang) * 1.9,
                        5, 0.10, 0.25, 0.10, 0.02);
            });
        }
        TestProcedure.schedule(sl, now + 26, () ->
                sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.PLAYERS, 0.85f, 1.55f));

        // --- 30 -> 120 : SIX FRAPPES, une toutes les dix-huit frames.
        for (int k = 0; k < 6; k++) {
            final int arm = k;
            TestProcedure.schedule(sl, now + 30 + arm * 18, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, 5.5, 3.0, attackerId);
                LivingEntity hit;
                if (solo || victims.isEmpty()) {
                    hit = sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? le
                            : (victims.isEmpty() ? null : victims.get(0));
                } else {
                    hit = victims.get(arm % victims.size());
                }
                if (hit == null) return;
                float amount = base * (solo ? 0.58f : 0.34f);
                chaosHurt(sl, attackerId, hit, amount);
                // LA REPETITION : la moitie du coup est mise de cote et retombera a la
                // fermeture de la boucle. L'echeance est calee sur la FIN de l'animation,
                // pas sur un delai glissant, donc les six retombees arrivent ensemble.
                int delay = Math.max(14, (150 - (30 + arm * 18)) + 14);
                addDiscord(sl, hit, amount * (solo ? 0.95f : 0.70f), attackerId, delay);
                hit.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, false));
                sl.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.42f, 0.70f + arm * 0.13f);
                sl.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.22f, 1.60f + arm * 0.06f);
                PixelFx.send(sl, CHAOS_DUST, hit.getX(),
                        hit.getY() + hit.getBbHeight() * 0.6, hit.getZ(), 14, 0.22, 0.30, 0.22, 0.08);
            });
        }

        // --- 126 : LE REALIGNEMENT. La figure redevient parfaite une derniere fois, et
        //     c'est le signal que les retombees arrivent.
        TestProcedure.schedule(sl, now + 126, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.90f, 0.95f);
            PixelFx.send(sl, CHAOS_DUST_SPARK, ox, oy, oz, 40, 1.1, 0.7, 1.1, 0.22);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, 5.5, 3.0, attackerId)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, false));
            }
        });

        // --- 142 : LA BOUCLE SE FERME. Le porteur est paye en terrain.
        TestProcedure.schedule(sl, now + 142, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 0.80f, 0.70f);
            PixelFx.send(sl, CHAOS_DUST, ox, oy, oz, 46, 1.3, 0.8, 1.3, 0.26);
            if (attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, true, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, true));
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  II. SPHERE D'ENTROPIE (170 ticks) — elle DESORDONNE
    // --------------------------------------------------------------------------------------

    /**
     * Un champ qui punit la PRESENCE, pas le contact.
     *
     * <p>Les huit pulsations ne tapent presque rien : trois dixiemes de l'arme. Ce qui fait
     * mal, c'est l'entropie accumulee, qui multiplie chaque pulsation suivante et empile
     * trois contraintes distinctes. Rester dix secondes dans la sphere coute donc beaucoup
     * plus cher que d'y passer ; en sortir fait redescendre le compteur. C'est la seule
     * signature du mod ou la bonne reponse de la victime est de <b>partir</b>, et elle est
     * ecrite comme ca exprime.</p>
     *
     * <p><b>Foule</b> : 4,6 blocs de rayon, tout le monde accumule en meme temps, et les
     * paliers tombent ensemble. <b>Duel</b> : plus personne a enfermer, donc la sphere se
     * RESSERRE a 2,8 blocs et accumule pres de deux fois plus vite — une cible seule ne
     * peut pas s'en extraire a la marche, elle doit depenser quelque chose.</p>
     *
     * <p>Le porteur recoit de la RESISTANCE, et c'est la seule famille a en donner :
     * l'entropie est un desordre qui l'epargne, puisqu'il en est le centre.</p>
     */
    private static void applyEntropySphereGameplay(LivingEntity attacker, LivingEntity target,
                                                   ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.3f, weaponDamage * 0.78f);
        final boolean solo = voidVictims(sl, ox, oy, oz, 4.6, 3.0, attackerId).size() <= 1;
        final double radius = solo ? 2.8 : 4.6;

        // --- 0 -> 18 : LES TROIS CERCLES se tracent, un par plan.
        for (int k = 0; k < 3; k++) {
            final int ring = k;
            TestProcedure.schedule(sl, now + 2 + ring * 6, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.34f, 0.52f + ring * 0.22f);
                for (int i = 0; i < 12; i++) {
                    double ang = i / 12.0 * Math.PI * 2.0;
                    double rx = Math.cos(ang) * radius * 0.92;
                    double ry = Math.sin(ang) * radius * 0.92;
                    PixelFx.send(sl, CHAOS_DUST_SPARK,
                            ox + (ring == 1 ? 0.0 : rx), oy + (ring == 0 ? ry : (ring == 1 ? rx : 0.0)),
                            oz + (ring == 2 ? ry : (ring == 1 ? ry : 0.0)),
                            1, 0.03, 0.03, 0.03, 0.0);
                }
            });
        }

        // --- 22 -> 134 : HUIT PULSATIONS, toutes les seize frames.
        for (int k = 0; k < 8; k++) {
            final int pulse = k;
            TestProcedure.schedule(sl, now + 22 + pulse * 16, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, radius, 3.0, attackerId);
                for (LivingEntity victim : victims) {
                    // L'entropie monte AVANT le degat : la pulsation qui fait franchir un
                    // palier doit deja etre payee au nouveau tarif, sinon le palier n'a
                    // d'effet qu'au coup suivant et le joueur ne fait pas le lien.
                    addEntropy(sl, victim, solo ? 2 : 1, 70);
                    int level = entropyOf(sl, victim);
                    float amount = base * 0.30f * (1.0f + level * 0.42f);
                    chaosHurt(sl, attackerId, victim, amount);
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                        0.26f, 0.42f + pulse * 0.07f);
                PixelFx.send(sl, CHAOS_DUST, ox, oy, oz, 10 + pulse * 2,
                        radius * 0.40, 0.9, radius * 0.40, 0.06);
            });
        }

        // --- 60 et 110 : LE DELITEMENT. Deux moments ou l'armature laisse tomber des
        //     morceaux ; chacun enfonce d'un cran supplementaire ceux qui sont restes.
        for (int k = 0; k < 2; k++) {
            final int phase = k;
            TestProcedure.schedule(sl, now + 60 + phase * 50, () -> {
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_BREAK,
                        SoundSource.PLAYERS, 0.55f, 0.62f + phase * 0.20f);
                PixelFx.send(sl, CHAOS_DUST_SPARK, ox, oy, oz, 30, radius * 0.5, 1.0, radius * 0.5, 0.18);
                for (LivingEntity victim : voidVictims(sl, ox, oy, oz, radius, 3.0, attackerId)) {
                    addEntropy(sl, victim, 1, 80);
                }
            });
        }

        // --- 150 : L'EFFONDREMENT DU NOYAU. Le compteur se solde : chaque palier accumule
        //     devient des degats, et c'est la que rester se paie vraiment.
        TestProcedure.schedule(sl, now + 150, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.95f, 0.62f);
            sl.playSound(null, ox, oy, oz, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55f, 1.35f);
            PixelFx.send(sl, CHAOS_DUST, ox, oy, oz, 60, radius * 0.45, 1.1, radius * 0.45, 0.34);
            PixelFx.send(sl, CHAOS_DUST_SPARK, ox, oy, oz, 40, radius * 0.35, 0.9, radius * 0.35, 0.26);
            for (LivingEntity victim : voidVictims(sl, ox, oy, oz, radius * 1.15, 3.0, attackerId)) {
                int level = entropyOf(sl, victim);
                if (level <= 0) continue;
                chaosHurt(sl, attackerId, victim, base * 0.34f * level);
                CHAOS_ENTROPIES.remove(victim.getId());
            }
            if (attacker != null && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 140, 1, true, true));
            }
        });
    }

    // --------------------------------------------------------------------------------------
    //  III. CICATRICE DU BIG BANG (200 ticks) — elle CREE puis DETRUIT
    // --------------------------------------------------------------------------------------

    /**
     * Deux temps, et rien entre les deux.
     *
     * <p><b>L'aspiration</b> dure quatre-vingt-quatre ticks et ne fait presque aucun degat.
     * Son role est de RASSEMBLER et d'annoncer : quatorze tractions, un compte a rebours
     * audible qui accelere, et une Lenteur qui s'aggrave. Le joueur a quatre secondes pour
     * comprendre ce qui va arriver — c'est ce delai qui rend la detonation lisible plutot
     * que subie.</p>
     *
     * <p><b>La detonation</b> paie au NOMBRE : chaque corps present au-dela du premier
     * ajoute douze pour cent a la part de tout le monde, plafonnee a six corps. C'est la
     * recompense de l'aspiration, et c'est pour ca que les deux phases ne sont pas
     * separables.</p>
     *
     * <p><b>Duel</b> : il n'y a personne a rassembler, donc l'aspiration devient un
     * CLOUAGE — la cible est tenue par l'emprise du Vide, sans knockback possible — et la
     * detonation frappe pres du double. La signature ne perd pas son interet a un contre
     * un, elle en change.</p>
     *
     * <p><b>La cicatrice</b> reste quatre-vingt-dix ticks apres l'explosion et brule ce qui
     * s'y attarde. C'est la seule zone persistante de la famille, et elle justifie le nom.</p>
     */
    private static void applyBigBangGameplay(LivingEntity attacker, LivingEntity target,
                                             ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.50;
        final double oz = target.getZ();
        final float base = Math.max(1.6f, weaponDamage * 0.92f);
        final boolean solo = voidVictims(sl, ox, oy, oz, 6.0, 3.2, attackerId).size() <= 1;

        // --- 0 -> 78 : QUATORZE TRACTIONS. Le compte a rebours de l'effondrement.
        for (int k = 0; k < 14; k++) {
            final int pull = k;
            TestProcedure.schedule(sl, now + 6 + pull * 6, () -> {
                List<LivingEntity> victims = voidVictims(sl, ox, oy, oz, 6.0, 3.2, attackerId);
                for (LivingEntity victim : victims) {
                    double ddx = ox - victim.getX();
                    double ddz = oz - victim.getZ();
                    double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (solo) {
                        // Duel : on ne tire pas, on CLOUE. L'emprise du Vide refuse le
                        // recul exterieur, donc la cible ne peut pas etre sortie du foyer
                        // par un tiers pendant les quatre secondes d'aspiration.
                        voidGrip(victim, 14, true, true);
                    } else if (dist > 0.30) {
                        double speed = Math.min(0.72, 0.13 + dist * 0.082);
                        voidPush(sl, victim, ddx / dist * speed, 0.045, ddz / dist * speed);
                    }
                    if ((pull % 3) == 0) {
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                                40, Math.min(2, pull / 5), true, false));
                    }
                }
                // Le tic-tac : la hauteur monte a chaque traction. C'est l'avertissement,
                // et il doit etre audible avant d'etre visible.
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_HIT,
                        SoundSource.PLAYERS, 0.24f + pull * 0.025f, 0.55f + pull * 0.075f);
                PixelFx.send(sl, CHAOS_DUST_SPARK, ox, oy, oz, 4 + pull, 1.6, 1.0, 1.6, 0.03);
            });
        }

        // --- 84 : LA DETONATION.
        TestProcedure.schedule(sl, now + 84, () -> {
            List<LivingEntity> caught = voidVictims(sl, ox, oy, oz, 6.4, 3.4, attackerId);
            // La prime de foule : douze pour cent par corps au-dela du premier, plafonnee
            // a six. Sans plafond, un troupeau de poulets vaudrait un boss.
            int bodies = Math.min(6, Math.max(1, caught.size()));
            float crowd = 1.0f + 0.12f * (bodies - 1);
            float punch = base * (solo ? 2.20f : 1.15f) * crowd;

            sl.playSound(null, ox, oy, oz, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 1.0f, 0.52f);
            sl.playSound(null, ox, oy, oz, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 0.90f, 1.30f);
            sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.80f, 0.42f);
            PixelFx.send(sl, CHAOS_DUST, ox, oy, oz, 90, 1.0, 1.0, 1.0, 0.62);
            PixelFx.send(sl, CHAOS_DUST_SPARK, ox, oy, oz, 70, 0.8, 0.8, 0.8, 0.52);

            for (LivingEntity victim : caught) {
                double ddx = victim.getX() - ox;
                double ddz = victim.getZ() - oz;
                double dist = Math.max(0.35, Math.sqrt(ddx * ddx + ddz * ddz));
                // Degressif avec la distance, jamais nul : le bord de l'explosion doit
                // rester une explosion, pas une limite administrative.
                float falloff = (float) Math.max(0.38, 1.0 - dist / 7.0);
                releaseVoidGrip(victim);
                chaosHurt(sl, attackerId, victim, punch * falloff);
                addDiscord(sl, victim, punch * falloff * 0.42f, attackerId, 46);
                voidPush(sl, victim, ddx / dist * 0.96, 0.52, ddz / dist * 0.96);
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, false));
            }
            if (attacker != null && attacker.isAlive()) {
                // Il a tenu au centre d'un big bang : absorption, et de quoi retomber.
                attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 180, 1, true, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, true));
            }
        });

        // --- 96 -> 186 : LA CICATRICE. Sept passages, un toutes les quinze frames.
        for (int k = 0; k < 7; k++) {
            final int burn = k;
            TestProcedure.schedule(sl, now + 96 + burn * 15, () -> {
                // Elle retrecit : 3,2 blocs au debut, 1,4 a la fin. Une zone qui garde son
                // rayon jusqu'a disparaitre d'un coup ne donne aucune information ; une
                // zone qui se referme dit au joueur combien de temps il lui reste.
                double r = 3.2 - burn * 0.26;
                for (LivingEntity victim : voidVictims(sl, ox, oy, oz, r, 2.4, attackerId)) {
                    chaosHurt(sl, attackerId, victim, base * 0.17f);
                    addEntropy(sl, victim, 1, 60);
                }
                sl.playSound(null, ox, oy, oz, SoundEvents.AMETHYST_BLOCK_HIT,
                        SoundSource.PLAYERS, 0.20f, 1.50f - burn * 0.07f);
                for (int i = 0; i < 8; i++) {
                    double ang = (i + burn * 1.7) / 8.0 * Math.PI * 2.0;
                    PixelFx.send(sl, CHAOS_DUST,
                            ox + Math.cos(ang) * r * 0.85, oy - 0.9, oz + Math.sin(ang) * r * 0.85,
                            2, 0.10, 0.04, 0.10, 0.01);
                }
            });
        }
    }

    /**
     * Routage des trois signatures d'ombre. Comme la foudre avant la T376, l'ombre n'avait
     * <b>aucun</b> gameplay : trois animations de 14 à 18 ticks qui ne faisaient rien.
     *
     * <p>Les trois partagent la <b>marque d'ombre</b> et se répondent : l'allonge en pose sur
     * une ligne, la faille en pose sur tout un disque en aspirant, l'éclipse en pose à chaque
     * battement du dôme. Chacune traite explicitement les deux cas demandés — <b>plusieurs
     * ennemis</b> et <b>ennemi seul</b> — pour qu'aucune ne soit décevante en duel.</p>
     */
    private static void applyShadowGameplay(LivingEntity attacker, LivingEntity target,
                                            WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.SPECTRAL_REACH) {
            applySpectralReachGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.WITHER_SHADOW_RIFT) {
            applyWitherRiftGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.ECLIPSE_VEIL) {
            applyEclipseVeilGameplay(attacker, target, sl, damage);
        }
    }

    /**
     * Déplace une créature vers un point, en traversant le mur invisible du client.
     *
     * <p><b>Le piège.</b> {@code setDeltaMovement} seul n'a aucun effet sur un <i>joueur</i> :
     * son client fait autorité sur sa position et écrase la valeur au tick suivant.
     * {@code hurtMarked} ne suffit pas non plus. La seule méthode fiable, établie en T376 et
     * confirmée sur plusieurs mods 1.21, est d'envoyer explicitement le paquet de vélocité au
     * client concerné. Sur un mob, rien de tout cela n'est nécessaire.</p>
     *
     * <p>Une composante verticale est toujours ajoutée : une traction purement horizontale est
     * absorbée par la première marche, et la victime reste collée au talus en glissant.</p>
     */
    private static void shadowYank(ServerLevel sl, LivingEntity victim,
                                   double toX, double toY, double toZ, double strength) {
        if (victim == null || !victim.isAlive()) return;
        double ddx = toX - victim.getX();
        double ddz = toZ - victim.getZ();
        double flat = Math.sqrt(ddx * ddx + ddz * ddz);
        if (flat < 0.05) return;
        double rise = Math.max(0.0, toY - victim.getY());
        // Plus la cible est loin, plus elle part fort, mais on borne : à huit blocs une
        // traction proportionnelle enverrait le mob en orbite au-dessus de la faille.
        double push = Math.min(1.15, strength * (0.35 + 0.085 * flat));
        double vy = 0.26 + Math.min(0.42, rise * 0.18);
        victim.setDeltaMovement(ddx / flat * push, vy, ddz / flat * push);
        victim.hasImpulse = true;
        victim.fallDistance = 0.0f;
        if (victim instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    /**
     * Recense les créatures hostiles autour d'un point. Exclut l'attaquant et les joueurs : un
     * effet de zone qui ramasse le porteur ou ses alliés est un piège, pas une signature.
     */
    private static List<LivingEntity> shadowVictims(ServerLevel sl, double cx, double cy, double cz,
                                                    double radius, double halfHeight, int attackerId) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - halfHeight, cz - radius,
                        cx + radius, cy + halfHeight, cz + radius))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            if (ddx * ddx + ddz * ddz > radius * radius) continue;
            out.add(candidate);
        }
        return out;
    }

    /** Afflictions communes de l'ombre, allongées par les marques déjà posées. */
    private static void shadowAfflict(ServerLevel sl, LivingEntity victim,
                                      int slowTicks, int blindTicks, int weakTicks) {
        if (victim == null || !victim.isAlive()) return;
        if (slowTicks > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    markedDuration(sl, victim, slowTicks), 1, true, false));
        }
        if (blindTicks > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    markedDuration(sl, victim, blindTicks), 0, true, false));
        }
        if (weakTicks > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    markedDuration(sl, victim, weakTicks), 0, true, false));
        }
    }

    // ============================================================================================
    //  Conception sonore de l'ombre — même procédé que la foudre (T376)
    // ============================================================================================
    //
    //  La foudre a établi quatre pièces, et elles valent pour toute signature longue :
    //    1. un FONDU D'ENTRÉE qui prévient avant que l'image existe ({@code chargeSwell}) ;
    //    2. des ACCENTS D'ÉTAPE posés exactement sur les jalons de l'animation cliente ;
    //    3. une DISPERSION aléatoire qui donne une largeur à la zone ({@code staticCrackle}) ;
    //    4. un FONDU DE SORTIE décalé dans l'espace ({@code rollingThunder}) — sans lui, un
    //       événement de dix secondes s'arrête net, ce qu'aucun événement ne fait.
    //
    //  Ce qui change pour l'ombre, et c'est délibéré : la foudre monte en hauteur tonale
    //  (l'air se charge, ça se tend vers l'aigu), l'ombre DESCEND (quelque chose s'enfonce et
    //  s'approche par en dessous). Même courbe, sens inverse.
    //
    //  Tous les sons ci-dessous ont été vérifiés contre le registre {@code sound_event} de la
    //  1.21.1 : ceux déclarés par {@code registerForHolder} exigent {@code .value()}, les
    //  autres non. C'est la seule source d'erreur de compilation de ce bloc.

    /**
     * Fondu d'entrée : l'ombre se charge. Volume croissant, intervalle décroissant — la même
     * courbe {@code i²·1,15 + i·2,2} que la foudre — mais la hauteur tonale <b>descend</b>.
     */
    private static void shadowSwell(ServerLevel sl, int now, double x, double y, double z,
                                    int steps, float volume) {
        for (int i = 0; i < steps; i++) {
            final float v = volume * (0.26f + 0.74f * i / Math.max(1, steps - 1));
            final float pitch = 0.98f - 0.085f * i;
            final int when = now + (int) Math.round(i * i * 1.15 + i * 2.2);
            TestProcedure.schedule(sl, when, () -> {
                sl.playSound(null, x, y, z, SoundEvents.SCULK_CLICKING,
                        SoundSource.PLAYERS, v * 0.78f, pitch + 0.42f);
                sl.playSound(null, x, y, z, SoundEvents.SCULK_BLOCK_CHARGE,
                        SoundSource.PLAYERS, v * 0.62f, pitch);
            });
        }
    }

    /**
     * Chuchotements courts tirés au hasard autour d'un point : l'équivalent exact du
     * crépitement statique de la foudre. C'est ce qui donne une <b>largeur</b> à l'effet —
     * un son unique au centre reste un point, quelle que soit la taille de l'image.
     */
    private static void shadowWhispers(ServerLevel sl, double x, double y, double z,
                                       double spread, int count, float volume) {
        for (int i = 0; i < count; i++) {
            double ox = x + (sl.random.nextDouble() - 0.5) * spread * 2.0;
            double oz = z + (sl.random.nextDouble() - 0.5) * spread * 2.0;
            sl.playSound(null, ox, y, oz, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.PLAYERS, volume, 0.52f + sl.random.nextFloat() * 0.46f);
        }
    }

    /**
     * Fondu de sortie : l'ombre se retire. Quatre répliques de plus en plus faibles, graves et
     * espacées, <b>décalées autour du point d'impact</b> pour qu'elles ne sortent pas toutes du
     * même haut-parleur — c'est ce décalage spatial, et non le volume, qui fait entendre que
     * la chose s'éloigne.
     */
    private static void fadingUmbra(ServerLevel sl, int now, double x, double y, double z,
                                    float volume) {
        final int[] WHEN = {9, 23, 42, 67};
        final float[] GAIN = {0.74f, 0.49f, 0.30f, 0.16f};
        for (int i = 0; i < WHEN.length; i++) {
            final float v = volume * GAIN[i];
            final float pitch = 0.84f - 0.075f * i;
            final double ox = x + (i % 2 == 0 ? 6.0 : -5.0) * (i + 1) * 0.5;
            final double oz = z + (i % 2 == 0 ? -4.0 : 7.0) * (i + 1) * 0.5;
            final boolean thick = v > 0.20f;
            TestProcedure.schedule(sl, now + WHEN[i], () -> {
                sl.playSound(null, ox, y + 2.2, oz,
                        SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(),
                        SoundSource.PLAYERS, v, pitch);
                if (thick) {
                    sl.playSound(null, ox, y + 0.8, oz, SoundEvents.SCULK_BLOCK_SPREAD,
                            SoundSource.PLAYERS, v * 0.70f, pitch * 0.88f);
                }
            });
        }
    }

    /**
     * La nappe d'ambiance pendant la durée. Une signature de dix secondes qui ne produit du son
     * qu'à ses jalons laisse des trous de trois à quatre secondes : l'effet paraît fini avant
     * de l'être. Trois timbres alternent pour qu'aucun ne devienne une boucle reconnaissable.
     */
    private static void umbralAmbience(ServerLevel sl, int now, double x, double y, double z,
                                       int from, int to, int period, float volume) {
        int k = 0;
        for (int t = from; t <= to; t += Math.max(1, period), k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + t, () -> {
                switch (step % 3) {
                    case 0 -> sl.playSound(null, x, y + 1.6, z,
                            SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(),
                            SoundSource.PLAYERS, volume, 0.62f);
                    case 1 -> sl.playSound(null, x, y + 0.9, z, SoundEvents.WARDEN_TENDRIL_CLICKS,
                            SoundSource.PLAYERS, volume * 0.70f, 0.58f);
                    default -> sl.playSound(null, x, y + 2.4, z,
                            SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(),
                            SoundSource.PLAYERS, volume * 0.85f, 0.70f);
                }
            });
        }
    }

    /**
     * ALLONGE SPECTRALE (118 ticks) — la signature <b>de ligne</b> de la famille.
     *
     * <p><b>Plusieurs ennemis.</b> Le couloir d'ombre est un volume réel : neuf blocs de long,
     * deux blocs sept de large, tracé de la cible vers le porteur. Tout ce qui s'y trouve est
     * lacéré par les lames qui se lèvent au passage de la main (ticks 70 à 97), et marqué. Ce
     * n'est pas un cône centré sur la victime : c'est le chemin que l'ombre vient de parcourir,
     * donc le joueur sait exactement où il frappe — devant lui, en ligne.</p>
     *
     * <p><b>Ennemi seul.</b> La main ne lâche pas : la cible est <b>clouée</b> pendant la montée
     * (40 ticks sans étourdissement complet, elle se défend encore), puis <b>ramenée</b> vers le
     * porteur au tick 70. L'énergie des lames non dépensées est reversée en trois serrages
     * (ticks 76, 88, 100). Ramener la cible à portée est en soi la récompense : l'allonge sert à
     * agripper ce qui fuit.</p>
     *
     * <p><b>Buff du porteur.</b> La main draine ce qu'elle tient : chaque victime du couloir
     * rend 1,5 point d'absorption au porteur, plafonné à 6. C'est la contrepartie du risque
     * pris à ramener un paquet d'ennemis sur soi.</p>
     */
    private static void applySpectralReachGameplay(LivingEntity attacker, LivingEntity target,
                                                   ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.4f, weaponDamage * 0.85f);

        // Axe du couloir : de la cible VERS le porteur. S'il n'y a pas d'attaquant (aperçu
        // /dlbvfx), on prend le regard de la cible pour que le couloir existe quand même.
        double axX;
        double axZ;
        if (attacker != null) {
            axX = attacker.getX() - ox;
            axZ = attacker.getZ() - oz;
        } else {
            axX = -Math.sin(Math.toRadians(target.getYRot()));
            axZ = Math.cos(Math.toRadians(target.getYRot()));
        }
        double axLen = Math.sqrt(axX * axX + axZ * axZ);
        if (axLen < 0.01) { axX = 0.0; axZ = 1.0; axLen = 1.0; }
        final double dirX = axX / axLen;
        final double dirZ = axZ / axLen;

        // --- La cible est clouée le temps que la main se lève. ---
        // DEUX HORLOGES : l'emprise expire en temps de MONDE (getGameTime), la tâche différée
        // compte en ticks de SERVEUR (currentTick). Les noms disent lequel est lequel.
        // L'emprise court jusqu'au tick 72, soit DEUX ticks après la traction du tick 70.
        // Quarante ticks — la valeur de la chaîne de foudre — ne suffisaient pas ici : la
        // main se lève au tick 53 et ne tire qu'au tick 70, donc la victime aurait eu dix-sept
        // ticks pour sortir du cadre pendant que la main, dessinée au point d'impact figé, se
        // refermait sur du vide. Sans étourdissement complet : elle est tenue, pas désactivée,
        // et continue de se défendre.
        //
        // L'emprise court maintenant jusqu'au tick 100, soit la fin de la TRACTION et non son
        // début : la victime n'est pas clouée puis relâchée d'un coup, elle est tenue PENDANT
        // tout le trajet (voir la boucle d'emport ci-dessous).
        final long graspHoldEnd = sl.getGameTime() + 100L;
        applyMovementHold(target, graspHoldEnd, false);

        // --- L'ombre se détache. On l'entend glisser avant de la voir. ---
        sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.55f, 0.72f);
        sl.playSound(null, ox, oy - 0.8, oz, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS,
                0.60f, 0.52f);
        // Le fondu d'entrée est placé à MI-COULOIR et non sur la cible : la coulée vient
        // du porteur, et c'est de ce côté que le joueur doit tourner la tête.
        shadowSwell(sl, now, ox + dirX * (REACH_LENGTH * 0.45), oy - 0.7,
                oz + dirZ * (REACH_LENGTH * 0.45), 5, 0.72f);
        for (int k = 0; k < 5; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 3 + step * 3, () -> {
                double d = 0.9 + step * 1.7;
                PixelFx.send(sl, ParticleTypes.SQUID_INK,
                        ox + dirX * d, oy - 1.05, oz + dirZ * d, 12, 0.45, 0.06, 0.45, 0.004);
                PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                        ox + dirX * d, oy - 0.95, oz + dirZ * d, 4, 0.30, 0.10, 0.30, 0.006);
                // LA COULÉE S'ENTEND AVANCER : chaque palier sonne à sa propre distance le
                // long du couloir, hauteur tonale descendante. Un son unique au point
                // d'impact aurait décrit un éclat, pas un déplacement.
                sl.playSound(null, ox + dirX * d, oy - 0.95, oz + dirZ * d,
                        SoundEvents.SOUL_SAND_STEP, SoundSource.PLAYERS,
                        0.62f, 0.78f - step * 0.055f);
            });
        }

        // --- L'ÉCHINE (ticks 15 → 32) : onze vertèbres se soulèvent en vague qui s'éloigne.
        //     Quatre accents suffisent à la décrire — un par vertèbre serait un crépitement
        //     de machine à écrire. La hauteur MONTE ici, contrairement au reste de la
        //     famille : quelque chose se redresse, et le sens tonal doit suivre le geste.
        for (int k = 0; k < 4; k++) {
            final int step = k;
            final double d = 1.4 + step * 1.9;
            TestProcedure.schedule(sl, now + 15 + step * 5, () -> {
                sl.playSound(null, ox + dirX * d, oy - 0.5, oz + dirZ * d,
                        SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS,
                        0.55f, 0.52f + step * 0.07f);
                sl.playSound(null, ox + dirX * d, oy - 0.3, oz + dirZ * d,
                        SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS,
                        0.40f, 0.62f + step * 0.06f);
            });
        }

        // --- LE DÉCOLLEMENT (tick 32) : la main pivote autour du poignet et sort du sol.
        //     C'est le seul moment de la signature où un objet franchit la surface, et
        //     warden.dig est exactement ce bruit-là.
        TestProcedure.schedule(sl, now + 32, () -> {
            double hx = ox + dirX * (REACH_LENGTH * 0.06);
            double hz = oz + dirZ * (REACH_LENGTH * 0.06);
            sl.playSound(null, hx, oy - 0.6, hz, SoundEvents.WARDEN_DIG,
                    SoundSource.PLAYERS, 0.85f, 0.62f);
            sl.playSound(null, hx, oy - 0.4, hz, SoundEvents.SOUL_SOIL_BREAK,
                    SoundSource.PLAYERS, 0.70f, 0.55f);
            shadowWhispers(sl, hx, oy - 0.2, hz, 1.6, 3, 0.45f);
        });
        TestProcedure.schedule(sl, now + 42, () -> sl.playSound(null,
                ox + dirX * (REACH_LENGTH * 0.06), oy, oz + dirZ * (REACH_LENGTH * 0.06),
                SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 0.58f, 0.48f));

        // --- LA POIGNE (ticks 53 → 68) : les phalanges se replient une à une. Trois clics
        //     secs et courts, de plus en plus aigus et de plus en plus rapprochés — la
        //     même compression d'intervalle que le fondu d'entrée, en miniature.
        for (int k = 0; k < 3; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 53 + step * 5 - step, () -> sl.playSound(null,
                    ox + dirX * (REACH_LENGTH * 0.06), oy + 0.2, oz + dirZ * (REACH_LENGTH * 0.06),
                    SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS,
                    0.52f + step * 0.08f, 0.86f + step * 0.16f));
        }

        // --- Les lames du couloir (ticks 70 à 97) ---
        // Un seul balayage par vague, et une liste partagée des corps déjà lacérés : sans elle,
        // un mob immobile au milieu du couloir encaisserait les quatre vagues.
        final java.util.Set<Integer> raked = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final java.util.concurrent.atomic.AtomicInteger drained =
                new java.util.concurrent.atomic.AtomicInteger();
        for (int w = 0; w < 4; w++) {
            final int wave = w;
            TestProcedure.schedule(sl, now + 70 + wave * 9, () -> {
                // Le volume balayé avance le long du couloir, comme les lames à l'écran.
                double from = wave * (REACH_LENGTH / 4.0);
                double to = (wave + 1) * (REACH_LENGTH / 4.0);
                double midD = (from + to) * 0.5;
                double mx = ox + dirX * midD;
                double mz = oz + dirZ * midD;
                double seg = (to - from) * 0.5 + REACH_HALF_WIDTH;
                for (LivingEntity victim : shadowVictims(sl, mx, oy, mz, seg, 3.0, attackerId)) {
                    if (raked.contains(victim.getId())) continue;
                    // Projection sur l'axe : on ne garde que ce qui est VRAIMENT dans le
                    // couloir, pas dans le disque circonscrit. Sans ce filtre, le couloir
                    // ramasse les coins et redevient une zone ronde.
                    double rx = victim.getX() - ox;
                    double rz = victim.getZ() - oz;
                    double along = rx * dirX + rz * dirZ;
                    double across = Math.abs(rx * dirZ - rz * dirX);
                    if (along < from - 0.6 || along > to + 0.6) continue;
                    if (across > REACH_HALF_WIDTH) continue;
                    raked.add(victim.getId());
                    hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.62f);
                    shadowAfflict(sl, victim, 70, 0, 90);
                    addUmbralMark(sl, victim, 1, attackerId);
                    drained.incrementAndGet();
                    PixelFx.send(sl, ParticleTypes.SQUID_INK,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                            16, 0.32, 0.42, 0.32, 0.05);
                    sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.45f, 0.62f);
                }
            });
        }

        // =================== LA PRISE, PUIS L'EMPORT ===================
        // Ce bloc remplace l'ancienne « traction », qui était UNE impulsion au tick 70. Une
        // impulsion ne se lit pas : la victime partait d'un coup, en glissade, sans rapport
        // visible avec la main qui se refermait sur elle. Ici la main SAISIT (tick 66), puis
        // EMPORTE tick par tick (68 → 97) en traînant tout ce qu'elle tient, et ne lâche
        // qu'au tick 100. Les trois temps correspondent exactement à ceux du rendu.

        // Qui est dans le poing. On retient l'entité ET son décalage au moment de la prise :
        // déplacer tout le monde sur un même point les ferait se pousser les uns les autres
        // (la collision des mobs), ce qui donne un tas qui vibre. On translate donc la
        // FORMATION entière, chacun gardant sa place relative.
        final Map<Integer, double[]> hauled = new ConcurrentHashMap<>();

        // --- La prise (tick 66) : tout ce qui est sous la paume part avec elle ---
        TestProcedure.schedule(sl, now + 66, () -> {
            double hx = ox + dirX * graspHandDistance(0.0);
            double hz = oz + dirZ * graspHandDistance(0.0);
            sl.playSound(null, hx, oy, hz, SoundEvents.WARDEN_ATTACK_IMPACT,
                    SoundSource.PLAYERS, 0.78f, 0.56f);
            sl.playSound(null, hx, oy, hz, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.80f, 0.58f);
            PixelFx.send(sl, ParticleTypes.SQUID_INK, hx, oy, hz, 45, 0.85, 0.70, 0.85, 0.05);
            // La paume fait deux blocs et demi à l'échelle 2,45 : tout ce qui tient dedans
            // est emporté, pas seulement la cible du coup.
            List<LivingEntity> caught = shadowVictims(sl, hx, oy, hz, 2.60, 3.0, attackerId);
            if (!caught.contains(target) && target.isAlive()) caught.add(target);
            for (LivingEntity victim : caught) {
                if (victim == null || !victim.isAlive()) continue;
                applyMovementHold(victim, graspHoldEnd, false);
                hauled.put(victim.getId(), new double[]{victim.getX(), victim.getZ()});
                shadowAfflict(sl, victim, 90, 45, 120);
                addUmbralMark(sl, victim, 1, attackerId);
                sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.70f, 0.58f);
            }
        });

        // --- L'emport (ticks 68 à 97), un pas par tick ---
        // Le pas suit la MÊME courbe que la main à l'écran, sinon la proie avance à côté du
        // poing au lieu d'être dedans.
        for (int k = 0; k <= 29; k++) {
            final int tick = 68 + k;
            TestProcedure.schedule(sl, now + tick, () -> {
                float pull = smoothTick(tick, 68L, 97L);
                double travel = graspHandDistance(pull) - graspHandDistance(0.0);
                // On s'arrête à deux blocs du porteur : le ramener DANS le joueur le ferait
                // rebondir par la collision, et le mouvement se lirait comme un bug.
                if (attacker != null && attacker.isAlive()) {
                    double bx = attacker.getX() - ox;
                    double bz = attacker.getZ() - oz;
                    double bl = Math.sqrt(bx * bx + bz * bz);
                    travel = Math.min(travel, Math.max(0.0, bl - 2.0 - graspHandDistance(0.0)));
                }
                for (Map.Entry<Integer, double[]> entry : hauled.entrySet()) {
                    if (!(sl.getEntity(entry.getKey()) instanceof LivingEntity victim)
                            || !victim.isAlive()) {
                        hauled.remove(entry.getKey());
                        continue;
                    }
                    double[] from = entry.getValue();
                    dragMovementHold(victim, from[0] + dirX * travel, from[1] + dirZ * travel);
                    // La traînée : sans elle, un corps qui glisse sur dix ticks n'a pas de
                    // vitesse lisible — c'est la seule chose qui dit qu'il est TRAÎNÉ.
                    if ((tick & 1) == 0) {
                        PixelFx.send(sl, ParticleTypes.SQUID_INK,
                                victim.getX(), victim.getY() + 0.25, victim.getZ(),
                                6, 0.22, 0.14, 0.22, 0.012);
                    }
                    // --- LES DÉGÂTS EN TEMPS RÉEL ---
                    // Un battement toutes les six ticks pendant tout le trajet, au lieu d'une
                    // seule addition à l'arrivée : on sent la main serrer PENDANT qu'elle tire,
                    // et la barre de vie descend sous les yeux du joueur.
                    if (tick % 6 == 0) {
                        hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.17f);
                        PixelFx.send(sl, ParticleTypes.SCULK_CHARGE_POP,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                                7, 0.24, 0.30, 0.24, 0.02);
                        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35f, 0.48f);
                    }
                }
            });
        }

        // --- Le lâcher (tick 100) : la main rend ce qu'elle tient, aux pieds du porteur ---
        TestProcedure.schedule(sl, now + 100, () -> {
            for (Integer id : hauled.keySet()) {
                if (!(sl.getEntity(id) instanceof LivingEntity victim)) continue;
                releaseMovementHold(victim, graspHoldEnd);
                if (!victim.isAlive()) continue;
                // Une bascule vers l'avant : on ne lâche pas un corps à l'arrêt, on le jette.
                shadowYank(sl, victim, victim.getX() + dirX * 1.6,
                        victim.getY() + 0.35, victim.getZ() + dirZ * 1.6, 0.55);
                PixelFx.send(sl, ParticleTypes.PORTAL,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        30, 0.40, 0.55, 0.40, 0.30);
            }
            releaseMovementHold(target, graspHoldEnd);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.55f, 0.46f);
        });

        // --- Le solo : trois serrages si le couloir n'a ramassé personne d'autre ---
        for (int k = 0; k < 3; k++) {
            final int squeeze = k;
            TestProcedure.schedule(sl, now + 76 + squeeze * 12, () -> {
                if (!target.isAlive()) return;
                int others = drained.get();
                if (others > 1) return;         // la foule a déjà payé, pas de double récompense
                hurtWithCapturedWeapon(sl, attackerId, target, base * 0.34f);
                PixelFx.send(sl, ParticleTypes.SCULK_CHARGE_POP,
                        target.getX(), target.getY() + target.getBbHeight() * 0.60, target.getZ(),
                        10, 0.26, 0.32, 0.26, 0.02);
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.55f, 0.72f);
            });
        }

        // --- Le buff du porteur : l'ombre rend ce qu'elle a pris ---
        TestProcedure.schedule(sl, now + 104, () -> {
            if (attacker == null || !attacker.isAlive()) return;
            int taken = Math.max(1, drained.get());
            int amplifier = Math.min(2, (taken - 1) / 2);
            attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 220, amplifier, true, true));
            sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.50f, 1.45f);
            PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                    attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.60, attacker.getZ(),
                    18, 0.35, 0.50, 0.35, 0.02);
        });
    }

    /**
     * FAILLE DU FLÉTRISSEUR (160 ticks) — la signature <b>d'aspiration</b> de la famille.
     *
     * <p><b>Plusieurs ennemis.</b> Le souffle du crâne balaie un cône de six blocs et demi
     * devant la faille (tick 62), puis la faille <b>inspire</b> : tout ce qui se trouve dans un
     * rayon de huit blocs est tiré vers elle, en quatre secousses (ticks 90 à 114). Le
     * regroupement est l'intérêt de la signature — elle ne tue pas la foule, elle la
     * <i>rassemble</i>, ce que les deux autres signatures d'ombre savent ensuite exploiter.</p>
     *
     * <p><b>Ennemi seul.</b> Sans voisin à rassembler, l'aspiration n'aurait rien donné :
     * l'énergie est reversée en <b>flétrissure concentrée</b> — la cible unique reçoit
     * {@code WITHER} au niveau II au lieu du niveau I, pour le double de la durée, et la
     * fermeture (tick 122) la frappe au plein tarif au lieu du tarif de zone.</p>
     *
     * <p><b>Buff du porteur.</b> La faille recrache ce qu'elle digère : à la fermeture, le
     * porteur récupère un point de vie par victime aspirée, plafonné à quatre.</p>
     */
    private static void applyWitherRiftGameplay(LivingEntity attacker, LivingEntity target,
                                                ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ty = target.getY() + target.getBbHeight() * 0.55;
        final float base = Math.max(1.6f, weaponDamage * 0.90f);

        // Direction du souffle : de la faille vers l'attaquant, comme à l'écran.
        final double[] dir = towardWielder(attacker, target.getX(), target.getZ());
        final double dirX = dir[0];
        final double dirZ = dir[1];

        // LA BOUCHE. Le cône ne part PAS de la cible : il part de la faille, qui s'ouvre
        // deux blocs derrière l'ancrage (lequel recule encore si plusieurs mobs sont alignés).
        // C'est le défaut que l'ancienne version cachait : le mob touché était à l'origine
        // du souffle, donc à distance nulle, donc toujours épargné par le test d'angle et
        // rattrapé seulement par la clause {@code d > 0.6}. Il est maintenant VRAIMENT dans
        // le jet, à deux blocs de la gueule.
        final double[] anchor = riftAnchor(sl, target.getX(), ty, target.getZ(), dirX, dirZ, attackerId);
        final double ox = anchor[0] - dirX * RIFT_BACK;
        final double oy = ty;
        final double oz = anchor[1] - dirZ * RIFT_BACK;

        // --- Tout ce qui est dans le jet est cloué jusqu'APRÈS le souffle. ---
        // L'emprise s'arrêtait au tick 56 alors que le souffle part au tick 62 : la cible
        // était donc libérée six ticks AVANT d'être soufflée, et un mob qui court sortait
        // du cône pile au mauvais moment. Elle court maintenant jusqu'au tick 70.
        final long riftHoldEnd = sl.getGameTime() + 70L;
        final List<LivingEntity> pinned = new ArrayList<>();
        pinned.add(target);
        for (LivingEntity victim : shadowVictims(sl, ox, oy, oz, RIFT_BREATH_RANGE, 4.0, attackerId)) {
            double rx = victim.getX() - ox;
            double rz = victim.getZ() - oz;
            double d = Math.sqrt(rx * rx + rz * rz);
            if (d > 0.6 && (rx * dirX + rz * dirZ) / d < 0.37) continue;
            if (!pinned.contains(victim)) pinned.add(victim);
        }
        for (LivingEntity victim : pinned) {
            applyMovementHold(victim, riftHoldEnd, false);
            final LivingEntity held = victim;
            TestProcedure.schedule(sl, now + 70, () -> releaseMovementHold(held, riftHoldEnd));
        }

        // --- L'ouverture ---
        // --- LA FÊLURE (ticks 0 → 11). Un trait d'un dixième de bloc qui clignote : il
        //     faut un son SEC, pas une nappe. La pierre qui cède, puis le fondu d'entrée
        //     par-dessus — le fondu court jusqu'au tick 11 et s'achève donc exactement
        //     quand l'amande commence à s'écarter.
        sl.playSound(null, ox, oy, oz, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS,
                0.75f, 0.42f);
        shadowSwell(sl, now, ox, oy + 0.6, oz, 4, 0.68f);

        // --- L'OUVERTURE (tick 11). Une fente qui s'écarte, pas une explosion : le
        //     volet du coffre-fort vaut mieux que n'importe quel son de portail.
        TestProcedure.schedule(sl, now + 11, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.VAULT_OPEN_SHUTTER,
                    SoundSource.PLAYERS, 0.90f, 0.48f);
            shadowWhispers(sl, ox, oy + 0.4, oz, 1.4, 4, 0.50f);
        });
        TestProcedure.schedule(sl, now + 22, () -> sl.playSound(null, ox, oy + 0.8, oz,
                SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.68f, 0.52f));

        sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.60f, 1.30f);
        TestProcedure.schedule(sl, now + 12, () ->
                sl.playSound(null, ox, oy, oz, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.50f, 0.55f));
        // --- LE CRÂNE S'ASSEMBLE (ticks 40 → 56). Deux cents pièces d'os sortent de la
        //     faille en trente ticks ; trois accents d'os de plus en plus GRAVES disent
        //     que la chose grossit. Montants, ils auraient dit qu'elle rétrécit.
        for (int k = 0; k < 3; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 40 + step * 8, () -> {
                sl.playSound(null, ox, oy + 0.9 + step * 0.25, oz, SoundEvents.BONE_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.62f, 0.74f - step * 0.11f);
                sl.playSound(null, ox, oy + 0.7, oz, SoundEvents.WITHER_SKELETON_STEP,
                        SoundSource.PLAYERS, 0.42f, 0.55f - step * 0.06f);
            });
        }
        TestProcedure.schedule(sl, now + 32, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.42f, 1.55f);
            PixelFx.send(sl, ParticleTypes.SCULK_SOUL, ox, oy + 0.4, oz, 26, 0.22, 0.95, 0.22, 0.02);
        });

        // --- Le souffle (tick 62) : un cône devant la faille ---
        final java.util.concurrent.atomic.AtomicInteger swallowed =
                new java.util.concurrent.atomic.AtomicInteger();
        TestProcedure.schedule(sl, now + 62, () -> {
            // Le tir de flétrissure, une frame AVANT le râle : on entend la gueule se vider,
        // puis le souffle lui-même. Inversés, les deux sons se lisent comme un seul bruit.
        TestProcedure.schedule(sl, now + 61, () -> {
            sl.playSound(null, ox, oy + 0.9, oz, SoundEvents.WITHER_SHOOT,
                    SoundSource.PLAYERS, 0.85f, 0.52f);
            sl.playSound(null, ox, oy + 0.9, oz, SoundEvents.GHAST_SHOOT,
                    SoundSource.PLAYERS, 0.55f, 0.46f);
        });
        // Les trois crânes mineurs du souffle, à 0,30 d'échelle : même tir, plus aigu et
        // plus faible, légèrement décalés pour qu'on les compte.
        for (int k = 0; k < 3; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 70 + step * 5, () -> sl.playSound(null,
                    ox, oy + 0.8, oz, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS,
                    0.38f, 1.18f + step * 0.10f));
        }
        // L'INSPIRATION (tick 88). Le cri du hurleur est le seul son de la banque qui
        // donne l'impression d'aspirer plutôt que d'émettre.
        TestProcedure.schedule(sl, now + 88, () -> {
            sl.playSound(null, ox, oy + 0.6, oz, SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    SoundSource.PLAYERS, 0.78f, 0.62f);
            shadowWhispers(sl, ox, oy + 0.5, oz, 4.5, 5, 0.42f);
        });
        sl.playSound(null, ox, oy, oz, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.70f, 0.75f);
            for (LivingEntity victim : shadowVictims(sl, ox, oy, oz, RIFT_BREATH_RANGE, 4.0, attackerId)) {
                double rx = victim.getX() - ox;
                double rz = victim.getZ() - oz;
                double d = Math.sqrt(rx * rx + rz * rz);
                // Cône de 100° : au-delà, on est derrière la faille, le souffle ne porte pas.
                if (d > 0.6 && (rx * dirX + rz * dirZ) / d < 0.37) continue;
                hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.55f);
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                        markedDuration(sl, victim, 100), 0, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                        markedDuration(sl, victim, 140), 0, true, false));
                addUmbralMark(sl, victim, 1, attackerId);
                PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        14, 0.30, 0.38, 0.30, 0.02);
            }
        });

        // --- L'aspiration (ticks 90 à 114) ---
        for (int k = 0; k < 4; k++) {
            final int pulse = k;
            TestProcedure.schedule(sl, now + 90 + pulse * 8, () -> {
                List<LivingEntity> caught = shadowVictims(sl, ox, oy, oz, RIFT_PULL_RADIUS, 5.0, attackerId);
                if (pulse == 0) swallowed.set(caught.size());
                sl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                        0.42f, 0.48f + pulse * 0.06f);
                for (LivingEntity victim : caught) {
                    // On tire vers la faille, pas dessus : s'arrêter à un bloc évite que la
                    // victime oscille autour du centre d'une secousse à l'autre.
                    double rx = victim.getX() - ox;
                    double rz = victim.getZ() - oz;
                    double d = Math.sqrt(rx * rx + rz * rz);
                    if (d < 1.1) continue;
                    shadowYank(sl, victim, ox, oy, oz, 0.70);
                    if (pulse % 2 == 0) {
                        hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.22f);
                        shadowAfflict(sl, victim, 60, 0, 0);
                    }
                    PixelFx.send(sl, ParticleTypes.REVERSE_PORTAL,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                            8, 0.24, 0.30, 0.24, 0.12);
                }
            });
        }

        // --- La fermeture (tick 122) ---
        // --- LA FERMETURE (tick 118). Le volet se referme quatre ticks avant la
        //     détonation du tick 122 : la fente se pince d'abord, le contrecoup suit.
        TestProcedure.schedule(sl, now + 118, () -> sl.playSound(null, ox, oy, oz,
                SoundEvents.VAULT_CLOSE_SHUTTER, SoundSource.PLAYERS, 0.88f, 0.44f));
        // --- LA CICATRICE (ticks 136 → 160). Une balafre de cendre qui s'effrite, puis
        //     le fondu de sortie qui déborde volontairement la fin de la signature.
        TestProcedure.schedule(sl, now + 136, () -> {
            sl.playSound(null, ox, oy - 0.4, oz, SoundEvents.SCULK_BLOCK_SPREAD,
                    SoundSource.PLAYERS, 0.60f, 0.48f);
            shadowWhispers(sl, ox, oy, oz, 2.2, 3, 0.34f);
        });
        fadingUmbra(sl, now + 140, ox, oy, oz, 0.80f);
        TestProcedure.schedule(sl, now + 122, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.75f, 1.40f);
            PixelFx.send(sl, ParticleTypes.SQUID_INK, ox, oy, oz, 60, 1.40, 0.70, 1.40, 0.10);
            List<LivingEntity> inside = shadowVictims(sl, ox, oy, oz, 6.4, 4.0, attackerId);
            boolean solo = inside.size() <= 1;
            for (LivingEntity victim : inside) {
                // SOLO : plein tarif et flétrissure II. EN FOULE : tarif de zone et flétrissure I.
                hurtWithCapturedWeapon(sl, attackerId, victim, base * (solo ? 1.05f : 0.68f));
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                        markedDuration(sl, victim, solo ? 200 : 100), solo ? 1 : 0, true, false));
                shadowAfflict(sl, victim, 80, 60, 100);
                addUmbralMark(sl, victim, 1, attackerId);
            }
            // --- Le buff du porteur : la faille recrache ---
            if (attacker != null && attacker.isAlive()) {
                int fed = Math.min(4, Math.max(swallowed.get(), inside.size()));
                if (fed > 0) {
                    attacker.heal(fed);
                    attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                            60 + fed * 30, 0, true, true));
                    PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                            attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                            6 + fed * 4, 0.32, 0.45, 0.32, 0.02);
                }
            }
        });
    }

    /**
     * VOILE D'ÉCLIPSE (200 ticks) — la signature <b>de contrôle de terrain</b> de la famille.
     *
     * <p><b>Plusieurs ennemis.</b> Un dôme de sept blocs de rayon se pose au tick 48 et tient
     * jusqu'au tick 178. Tant qu'il tient, il <b>bat</b> toutes les dix-huit ticks : chaque
     * battement aveugle, ralentit et marque tout ce qui est dessous.</p>
     *
     * <p><b>Ce n'est plus un contrat de position, c'est une prison.</b> Le dôme était une zone
     * qu'on pouvait quitter — comme l'appel de l'orage, ce qui restait dedans souffrait, ce qui
     * en sortait était tranquille. Désormais, ce qui est entré <b>ne ressort plus</b> : une
     * paroi sphérique ({@link #tickEclipseCage}) renvoie à l'intérieur tout ce qui l'atteint,
     * par les côtés comme par le dessus, et une <b>brûlure</b> s'applique à période fixe —
     * toutes les {@value #ECLIPSE_BURN_PERIOD} ticks, la même pour tout le monde — par-dessus
     * les battements. Les deux changements vont ensemble : enfermer sans brûler ferait une
     * cage inoffensive, brûler sans enfermer laisserait la victime sortir au premier tick.</p>
     *
     * <p><b>Qui est concerné</b> est décidé par {@link #eclipsePrey} : les joueurs le sont
     * (c'est la seule signature de la famille qui compte en PvP), le porteur ne l'est jamais,
     * et le bétail, les villageois et les compagnons apprivoisés traversent le voile sans rien
     * subir — une éclipse qui massacre le troupeau du joueur est un bug, pas une signature.</p>
     *
     * <p><b>Ennemi seul.</b> Le dôme se <b>resserre</b> sur la cible unique : les trois
     * griffures finales (tick 152) convergent toutes sur elle au lieu de se répartir, soit le
     * triple des dégâts. Aucun joueur ne perd à utiliser l'éclipse en duel.</p>
     *
     * <p><b>Buff du porteur.</b> C'est <i>son</i> ombre : sous le dôme, le porteur frappe plus
     * fort ({@code DAMAGE_BOOST}) pendant toute la durée du voile. Il n'y voit pas mieux que
     * ses victimes pour autant — une éclipse qui rend la vue à celui qui l'invoque annule son
     * propre sujet. C'est la seule signature de l'ombre qui récompense le fait de rester au
     * contact, et c'est voulu — l'éclipse est une arène, pas une fuite.</p>
     */
    private static void applyEclipseVeilGameplay(LivingEntity attacker, LivingEntity target,
                                                 ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        // Le dôme est ancré sur le POINT D'IMPACT et n'y bouge plus. Si on le recalculait
        // depuis la cible à chaque battement, le joueur ne pourrait pas jouer la zone — et
        // l'effet paraîtrait « visé sur le joueur », défaut déjà corrigé pour l'orage.
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.5f, weaponDamage * 0.88f);

        // --- LA CAGE. Le dôme n'est pas qu'un décor ni qu'un contrat de position : ce qui
        //     entre dessous n'en ressort plus et brûle en continu. On l'enregistre tout de
        //     suite, avec des échéances en TEMPS DE MONDE et non en ticks programmés : le
        //     tick d'entité qui applique la paroi lit getGameTime(), et mélanger les deux
        //     horloges est l'erreur qui laisse un effet actif bien après sa fin.
        //     La paroi ne se ferme qu'au tick 48, quand l'ourlet du voile touche le sol ;
        //     elle se rouvre au tick 178, quand le voile part en lambeaux. Enfermer avant
        //     que la porte existe, ou après qu'elle s'est ouverte, serait incompréhensible.
        final long gameNow = sl.getGameTime();
        final String domeKey = sl.dimension().location().toString() + "|" + attackerId;
        ECLIPSE_DOMES.put(domeKey, new EclipseDome(ox, oy, oz, DOME_RADIUS,
                gameNow + 48L, gameNow + 178L, attackerId, base * 0.12f,
                ConcurrentHashMap.newKeySet()));

        // --- LA NUIT. Le monde passe a minuit pour les deux cents ticks de la signature,
        //     puis retrouve l'heure qu'il avait, avancee du temps ecoule. C'est le seul
        //     effet du mod qui touche a l'etat du MONDE et non a celui des entites, d'ou
        //     la machinerie de stealSky/returnSky : il faut pouvoir le rendre meme si deux
        //     eclipses se chevauchent. ---
        stealSky(sl, 200);
        sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.00f, 0.42f);

        // --- La montee du soleil noir : cinq battements de plus en plus rapproches, et une
        //     COLONNE de particules qui monte jusqu'a la hauteur du disque. Sans la colonne,
        //     le joueur n'a aucune raison de lever les yeux, et il rate l'eclipse. ---
        sl.playSound(null, ox, oy + 8.0, oz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.85f, 0.65f);
        for (int k = 0; k < 5; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 4 + step * 8, () -> {
                sl.playSound(null, ox, oy + 6.0, oz, SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 0.50f + step * 0.12f, 0.58f + step * 0.05f);
                // la colonne : six paliers par battement, du sol jusqu'a vingt-trois blocs
                for (int h = 0; h < 6; h++) {
                    double hy = oy + 1.0 + h * 3.8 + step * 0.6;
                    PixelFx.send(sl, ParticleTypes.SCULK_SOUL, ox, hy, oz, 6, 0.55, 0.30, 0.55, 0.012);
                    PixelFx.send(sl, ParticleTypes.PORTAL, ox, hy, oz, 5, 0.85, 0.45, 0.85, 0.035);
                }
            });
        }
        // le disque lui-meme, a vingt-trois blocs : une bouffee d'encre et de souffle
        TestProcedure.schedule(sl, now + 22, () -> {
            sl.playSound(null, ox, oy + 20.0, oz, SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 1.00f, 0.50f);
            PixelFx.send(sl, ParticleTypes.SQUID_INK, ox, oy + 22.0, oz, 70, 2.60, 2.60, 2.60, 0.02);
            PixelFx.send(sl, ParticleTypes.REVERSE_PORTAL, ox, oy + 22.0, oz, 60, 3.20, 3.20, 3.20, 0.06);
            PixelFx.send(sl, ParticleTypes.DRAGON_BREATH, ox, oy + 22.0, oz, 40, 3.60, 3.60, 3.60, 0.01);
        });

        // --- LA COURONNE SE DÉPLOIE (ticks 26 → 42). Entre le disque (tick 22) et la pose
        //     du voile (tick 48), l'image montre 48 filaments qui s'allument un par un et
        //     la bande son ne disait rien. Trois paliers ascendants comblent exactement
        //     cet intervalle : c'est le seul moment lumineux de la signature, il doit
        //     sonner comme un appareil qui s'amorce et non comme une menace.
        TestProcedure.schedule(sl, now + 26, () -> sl.playSound(null, ox, oy + 14.0, oz,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.62f, 0.52f));
        TestProcedure.schedule(sl, now + 34, () -> sl.playSound(null, ox, oy + 16.0, oz,
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.58f, 0.60f));
        TestProcedure.schedule(sl, now + 42, () -> {
            sl.playSound(null, ox, oy + 18.0, oz, SoundEvents.ENDERMAN_SCREAM,
                    SoundSource.PLAYERS, 0.34f, 0.44f);
            shadowWhispers(sl, ox, oy + 2.0, oz, DOME_RADIUS * 0.8, 4, 0.40f);
        });

        // --- Le voile se pose (tick 48) ---
        TestProcedure.schedule(sl, now + 48, () -> {
            // La paroi devient effective à cet instant précis (EclipseDome.burnFrom) :
            // le verrou doit s'ENTENDRE, sinon le premier joueur qui s'y cogne croit à un bug.
            sl.playSound(null, ox, oy, oz, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.85f, 0.55f);
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.80f, 0.55f);
            sl.playSound(null, ox, oy, oz, SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.90f, 0.70f);
            sl.playSound(null, ox, oy, oz, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.55f, 0.48f);
            PixelFx.send(sl, ParticleTypes.SQUID_INK, ox, oy + 1.0, oz, 80, 3.20, 1.20, 3.20, 0.04);
            // L'OURLET. Le dome est un contrat de position : sa limite doit exister au sol,
            // pas seulement dans le rendu client. Trente-deux bouffees sur le cercle.
            for (int i = 0; i < 32; i++) {
                double ang = i * (Math.PI * 2.0) / 32.0;
                double rx = ox + Math.cos(ang) * DOME_RADIUS;
                double rz = oz + Math.sin(ang) * DOME_RADIUS;
                PixelFx.send(sl, ParticleTypes.SCULK_SOUL, rx, oy - 0.6, rz, 3, 0.10, 0.45, 0.10, 0.015);
                if (i % 4 == 0) {
                    PixelFx.send(sl, ParticleTypes.END_ROD, rx, oy - 0.5, rz, 2, 0.08, 0.30, 0.08, 0.008);
                }
            }
        });
        // --- La cendre, pendant toute la nuit : six bouffees espacees de 18 ticks. Un
        //     dome silencieux et vide se lit comme un decor ; il faut qu'il PLEUVE. ---
        for (int k = 0; k < 6; k++) {
            TestProcedure.schedule(sl, now + 56 + k * 18, () -> {
                PixelFx.send(sl, ParticleTypes.ASH, ox, oy + 3.5, oz, 55, 3.40, 2.20, 3.40, 0.01);
                PixelFx.send(sl, ParticleTypes.WHITE_ASH, ox, oy + 4.5, oz, 25, 3.00, 2.00, 3.00, 0.01);
                PixelFx.send(sl, ParticleTypes.PORTAL, ox, oy + 1.2, oz, 30, 2.80, 1.40, 2.80, 0.05);
            });
        }

        // --- LA NUIT (ticks 84 → 148). LE PLUS GROS DÉFAUT DE LA BANDE SON : entre le
        //     premier battement (68) et l'anneau de diamant (140), il n'y avait QUE les
        //     battements, espacés de 18 ticks. Presque une seconde de silence entre
        //     chacun, pendant que l'image montre de la cendre qui tombe, des braises qui
        //     montent et un sceau qui tourne : l'effet paraissait fini avant de l'être.
        //     La nappe tourne toutes les 13 ticks — volontairement premier avec 18 pour
        //     que nappe et battements ne tombent jamais deux fois de suite ensemble.
        umbralAmbience(sl, now, ox, oy, oz, 84, 148, 13, 0.46f);
        // ... et la poussière d'âmes qui traîne sous le dôme, décalée des deux autres
        //     horloges pour achever de remplir la grille.
        for (int k = 0; k < 4; k++) {
            TestProcedure.schedule(sl, now + 92 + k * 15, () ->
                    shadowWhispers(sl, ox, oy + 0.8, oz, DOME_RADIUS * 0.85, 3, 0.34f));
        }

        // --- Les battements (ticks 68 à 140, un toutes les 18 ticks) ---
        // Le premier tombe au tick 68, pas au tick 54 : c'est là que l'ourlet du dôme touche
        // le sol à l'écran (le voile atteint 0,55 de fermeture à t = 0,345, soit 69 ticks sur
        // 200). Plus tôt, le joueur encaissait un battement alors que la demi-sphère était
        // encore une calotte suspendue au-dessus de lui : la zone frappait avant d'exister.
        // Le dernier tombe au tick 140, en même temps que l'anneau de diamant — le battement
        // final et l'avertissement ne font plus qu'un son.
        for (int k = 0; k < 5; k++) {
            final int beat = k;
            TestProcedure.schedule(sl, now + 68 + beat * 18, () -> {
                List<LivingEntity> inside = eclipseVictims(sl, ox, oy, oz, DOME_RADIUS, 6.0, attackerId);
                sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                        0.55f, 0.62f);
                for (LivingEntity victim : inside) {
                    hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.30f);
                    shadowAfflict(sl, victim, 50, 60, 0);
                    // Une marque tous les deux battements : à chaque battement, une foule
                    // saturerait en deux secondes et l'effondrement perdrait tout son sens.
                    if (beat % 2 == 0) addUmbralMark(sl, victim, 1, attackerId);
                    PixelFx.send(sl, ParticleTypes.SCULK_SOUL,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                            8, 0.28, 0.40, 0.28, 0.015);
                }
                // --- Le buff du porteur, renouvelé tant qu'il reste sous le dôme ---
                if (attacker != null && attacker.isAlive()) {
                    double adx = attacker.getX() - ox;
                    double adz = attacker.getZ() - oz;
                    if (adx * adx + adz * adz <= DOME_RADIUS * DOME_RADIUS) {
                        // Pas de NIGHT_VISION : l'eclipse PLONGE l'arene dans le noir, et
                        // rendre la vue au porteur detruisait son propre sujet. Il frappe
                        // plus fort dans une obscurite qu'il subit aussi.
                        attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, true));
                    }
                }
            });
        }

        // --- L'anneau de diamant (tick 140) : l'avertissement, et le seul instant clair ---
        TestProcedure.schedule(sl, now + 140, () -> {
            sl.playSound(null, ox, oy + 3.0, oz, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS,
                    0.80f, 1.10f);
            sl.playSound(null, ox, oy + 18.0, oz, SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS,
                    0.65f, 0.55f);
            PixelFx.send(sl, ParticleTypes.END_ROD, ox, oy + 3.0, oz, 24, 0.60, 0.60, 0.60, 0.05);
            // l'eclat la-haut, sur le limbe
            PixelFx.send(sl, ParticleTypes.FLASH, ox, oy + 22.0, oz, 3, 0.80, 0.80, 0.80, 0.0);
            PixelFx.send(sl, ParticleTypes.END_ROD, ox, oy + 22.0, oz, 55, 3.60, 3.60, 3.60, 0.16);
        });
        // L'ONDE DE CHOC, douze ticks plus tard. La lumiere part d'en haut, le sol repond
        // APRES : simultanes, les deux evenements n'en font qu'un et on perd le lien de
        // cause a effet. Le decalage est le meme que celui du rendu client.
        TestProcedure.schedule(sl, now + 152 - 4, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.PLAYERS,
                    0.90f, 0.60f);
            for (int i = 0; i < 40; i++) {
                double ang = i * (Math.PI * 2.0) / 40.0;
                PixelFx.send(sl, ParticleTypes.SONIC_BOOM,
                        ox + Math.cos(ang) * DOME_RADIUS * 0.85, oy - 0.5,
                        oz + Math.sin(ang) * DOME_RADIUS * 0.85, 1, 0.05, 0.05, 0.05, 0.0);
            }
        });

        // --- Les griffures (tick 152) ---
        TestProcedure.schedule(sl, now + 152, () -> {
            sl.playSound(null, ox, oy, oz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.90f, 0.85f);
            // Trois lames balaient le dôme à l'écran, décalées de 0,13 de progression
            // chacune : trois passages d'air décalés d'autant, et non un seul coup. Un
            // son unique pour trois traits visibles, c'est l'image qui ment.
            for (int c = 0; c < 3; c++) {
                final int blade = c;
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + blade * 5, () ->
                        sl.playSound(null,
                                ox + Math.cos(blade * 2.1) * DOME_RADIUS * 0.6, oy + 1.4,
                                oz + Math.sin(blade * 2.1) * DOME_RADIUS * 0.6,
                                SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS,
                                0.70f, 0.52f + blade * 0.07f));
            }
            List<LivingEntity> inside = eclipseVictims(sl, ox, oy, oz, DOME_RADIUS, 6.0, attackerId);
            // SOLO : les trois griffures convergent sur l'unique victime. EN FOULE : chacune
            // prend sa part. Le facteur est explicite pour que la règle reste lisible.
            float claws = inside.size() <= 1 ? 3.0f : 1.0f;
            for (LivingEntity victim : inside) {
                hurtWithCapturedWeapon(sl, attackerId, victim, base * 0.80f * claws);
                shadowAfflict(sl, victim, 100, 100, 140);
                addUmbralMark(sl, victim, 2, attackerId);
                PixelFx.send(sl, ParticleTypes.SQUID_INK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        34, 0.45, 0.55, 0.45, 0.08);
                sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.70f, 0.52f);
            }
        });

        // --- LE DÔME CÈDE (ticks 164 → 171). Les griffures courent jusqu'au tick 176 ;
        //     après le troisième passage de lame (tick 162) la bande son retombait à
        //     zéro alors que l'image montre encore le voile en train de se déchirer.
        //     Deux craquements de membrane, là où les lames ont ouvert le tissu.
        for (int k = 0; k < 2; k++) {
            final int step = k;
            TestProcedure.schedule(sl, now + 164 + step * 7, () -> {
                sl.playSound(null, ox + (step == 0 ? 3.2 : -2.8), oy + 2.6,
                        oz + (step == 0 ? -2.6 : 3.4), SoundEvents.SCULK_VEIN_BREAK,
                        SoundSource.PLAYERS, 0.72f, 0.46f + step * 0.08f);
                shadowWhispers(sl, ox, oy + 1.8, oz, DOME_RADIUS * 0.7, 3, 0.38f);
            });
        }

        // --- La levée (tick 178) ---
        TestProcedure.schedule(sl, now + 178, () -> {
            // Le dôme expire tout seul par son échéance en temps de monde ; on le retire
            // quand même ici pour ne rien laisser dans la carte si plus aucune entité ne
            // tick dans cette dimension (une carte statique qu'on ne purge jamais est une
            // fuite, même petite).
            ECLIPSE_DOMES.remove(domeKey);
            sl.playSound(null, ox, oy, oz, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.45f, 1.30f);
            sl.playSound(null, ox, oy, oz, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.35f, 1.40f);
            // Le fondu de sortie part d'ICI et non du tick 200 : il doit couvrir la
            // levée du voile, pas commencer quand tout est déjà fini. Ses quatre
            // répliques débordent volontairement la fin de la signature.
            fadingUmbra(sl, TestProcedure.currentTick(sl), ox, oy, oz, 0.95f);
            PixelFx.send(sl, ParticleTypes.ASH, ox, oy + 1.5, oz, 70, 3.00, 1.60, 3.00, 0.03);
            PixelFx.send(sl, ParticleTypes.REVERSE_PORTAL, ox, oy + 2.0, oz, 50, 2.40, 2.60, 2.40, 0.09);
        });

        // --- LE JOUR REVIENT (tick 200) ---
        // Programme a la duree EXACTE de la signature, pas une frame de moins : le ciel
        // doit rester noir tant que le dome est a l'ecran, sinon on voit le soleil se
        // rallumer a travers une eclipse encore en cours.
        TestProcedure.schedule(sl, now + 200, () -> {
            returnSky(sl);
            sl.playSound(null, ox, oy, oz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    0.50f, 1.25f);
        });
    }

    // ============================================================================================
    // 雷 FOUDRE (T376) — arc en chaîne, marteau de Thor, appel de l'orage
    // ============================================================================================

    /**
     * Routage des trois signatures de foudre. Comme l'eau avant la T375, la foudre n'avait
     * <b>aucun</b> gameplay : trois animations de moins d'une demi-seconde qui ne faisaient rien.
     *
     * <p>Les trois signatures partagent une ressource commune, la <b>charge statique</b>
     * ({@link #STATIC_CHARGES}), et se répondent : l'arc en chaîne en pose sur toute une foule,
     * le marteau en pose sur tout un carré de 3×3, l'appel de l'orage en sème sur une zone
     * avant de l'écraser. Chacune gère explicitement les deux cas de figure demandés
     * — <b>plusieurs ennemis</b> (propagation) et <b>ennemi seul</b> (report de l'énergie non
     * dépensée), pour qu'aucune ne soit décevante en duel.</p>
     */
    private static void applyThunderGameplay(LivingEntity attacker, LivingEntity target,
                                             WeaponVisualEffectId fx, float weaponDamage) {
        if (fx == null || target == null) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.CHAIN_VOLT_ARC) {
            applyChainVoltGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.MJOLNIR_HAMMERFALL) {
            applyHammerGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.STORMCALL_JUDGMENT) {
            applyStormcallGameplay(attacker, target, sl, damage);
        }
    }

    /**
     * Ajoute des piles de charge statique et renvoie le total après ajout. À saturation la
     * charge est consommée sur place par {@link #dischargeStatic}.
     */
    private static int addStaticCharge(ServerLevel sl, LivingEntity victim, int add, int attackerId) {
        if (victim == null || !victim.isAlive() || add <= 0) return 0;
        long now = TestProcedure.currentTick(sl);
        int id = victim.getId();
        StaticCharge current = STATIC_CHARGES.get(id);
        int base = (current == null || current.expires() <= now) ? 0 : current.stacks();
        int total = base + add;
        PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.72, victim.getZ(),
                3 + total * 2, 0.26, 0.34, 0.26, 0.04);
        if (total >= STATIC_MAX) {
            STATIC_CHARGES.remove(id);
            dischargeStatic(sl, victim, attackerId, total);
            return 0;
        }
        STATIC_CHARGES.put(id, new StaticCharge(total, now + STATIC_DECAY));
        return total;
    }

    /** Majoration de dégâts apportée par les piles présentes, sans les consommer. */
    private static float staticMultiplier(ServerLevel sl, LivingEntity victim) {
        if (victim == null) return 1.0f;
        StaticCharge charge = STATIC_CHARGES.get(victim.getId());
        if (charge == null || charge.expires() <= TestProcedure.currentTick(sl)) return 1.0f;
        return 1.0f + STATIC_PER_STACK * charge.stacks();
    }

    /** Retire les piles d'une créature et renvoie combien il y en avait. */
    private static int consumeStatic(ServerLevel sl, LivingEntity victim) {
        if (victim == null) return 0;
        StaticCharge charge = STATIC_CHARGES.remove(victim.getId());
        if (charge == null || charge.expires() <= TestProcedure.currentTick(sl)) return 0;
        return charge.stacks();
    }

    /**
     * Claquage de la charge à saturation : la victime encaisse, et l'arc saute aux voisins
     * immédiats en leur laissant une pile. C'est ce qui fait qu'un groupe « s'allume » en
     * cascade quand on le travaille à la foudre.
     */
    private static void dischargeStatic(ServerLevel sl, LivingEntity victim, int attackerId, int stacks) {
        final double vx = victim.getX();
        final double vy = victim.getY() + victim.getBbHeight() * 0.55;
        final double vz = victim.getZ();
        hurtWithCapturedWeapon(sl, attackerId, victim, 1.10f * stacks);
        sl.playSound(null, vx, vy, vz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.42f, 1.65f);
        PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, vx, vy, vz, 34, 0.55, 0.60, 0.55, 0.22);
        broadcastNear(sl, (float) vx, (float) vy, (float) vz,
                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_IMPACT, 3,
                (float) vx, (float) vy, (float) vz,
                (float) vx, (float) vy, (float) vz, 0, 0.42f);
        int splash = 0;
        for (LivingEntity near : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(vx - 3.20, vy - 2.20, vz - 3.20, vx + 3.20, vy + 2.40, vz + 3.20))) {
            if (splash >= 3) break;
            if (!near.isAlive() || near == victim || near instanceof Player) continue;
            if (attackerId >= 0 && near.getId() == attackerId) continue;
            splash++;
            hurtWithCapturedWeapon(sl, attackerId, near, 0.55f * stacks);
            // Pile déposée SANS repasser par addStaticCharge : sinon un voisin déjà chargé
            // claquerait à son tour depuis cette pile, qui rechargerait le premier, et ainsi
            // de suite — une décharge en anneau se refermant sur elle-même suffit à faire
            // déborder la pile d'appels. Une éclaboussure ne peut donc pas en déclencher
            // une autre : il faut une nouvelle frappe du joueur.
            long id = near.getId();
            StaticCharge prior = STATIC_CHARGES.get((int) id);
            int had = (prior == null || prior.expires() <= TestProcedure.currentTick(sl))
                    ? 0 : prior.stacks();
            STATIC_CHARGES.put((int) id,
                    new StaticCharge(Math.min(STATIC_MAX - 1, had + 1),
                            TestProcedure.currentTick(sl) + STATIC_DECAY));
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                    near.getX(), near.getY() + near.getBbHeight() * 0.70, near.getZ(),
                    6, 0.24, 0.30, 0.24, 0.05);
        }
    }

    /**
     * ARC FULGURANT EN CHAÎNE (130 ticks) — la signature <b>de zone</b> de la famille.
     *
     * <p><b>Plusieurs ennemis.</b> Le trait tombe sur la cible désignée puis <b>saute</b> de
     * proche en proche, six relais au plus ({@code CHAIN_LINKS} côté client dessine exactement le
     * même nombre). Chaque saut cherche la créature vivante la plus proche du dernier maillon
     * dans un rayon de 7 blocs, jamais déjà touchée, et coûte 18 % des dégâts : la chaîne
     * s'épuise, elle ne tond pas une armée indéfiniment. Chaque maillon dépose une pile de
     * charge statique, si bien qu'une foule travaillée à l'arc finit par claquer toute seule.</p>
     *
     * <p><b>Ennemi seul.</b> Sans voisin, la chaîne n'aurait servi à rien : l'énergie des relais
     * inutilisés est <b>reversée sur la cible unique</b> sous forme de trois décharges
     * rapprochées (ticks 26, 38, 50) valant chacune 26 % des dégâts par maillon non dépensé, et
     * la cible reçoit directement trois piles. Un duel reste donc payant.</p>
     */
    private static void applyChainVoltGameplay(LivingEntity attacker, LivingEntity target,
                                               ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final double ox = target.getX();
        final double oy = target.getY() + target.getBbHeight() * 0.55;
        final double oz = target.getZ();
        final float base = Math.max(1.6f, weaponDamage * 0.92f);

        // --- La cible est clouée sur place le temps que l'éclair descende. ---
        // « Tu as oublié de lock le mob en place. » Exact, et c'est visible : le point de
        // chute est figé ici, à l'instant du coup, parce qu'un éclair ne poursuit pas sa
        // proie. Mais le mob, lui, continue de marcher pendant les vingt ticks de descente.
        // Il suffit qu'il fasse un pas de côté pour que le trait tombe à côté de lui.
        //
        // DEUX HORLOGES, et il ne faut pas les confondre : l'emprise expire en temps de MONDE
        // (getGameTime, qui suit la sauvegarde), la tâche différée compte en ticks de SERVEUR
        // (currentTick, remis à zéro à chaque démarrage). Les noms disent lequel est lequel.
        //
        // Quarante ticks couvrent l'ionisation, les quatre colonnes, la frappe principale au
        // vingtième et le premier bond de la chaîne. Sans étourdissement complet : le mob
        // est immobilisé, pas désactivé — il se défend encore, il ne peut simplement plus
        // sortir de la mire. L'emprise se libère seule à l'expiration ; on la rend quand même
        // explicitement, pour que l'IA reprenne la main au tick exact et pas au suivant.
        final long chainHoldEnd = sl.getGameTime() + 40L;
        applyMovementHold(target, chainHoldEnd, false);
        TestProcedure.schedule(sl, now + 40, () -> releaseMovementHold(target, chainHoldEnd));

        // --- Le ciel se charge. On l'entend avant de le voir. ---
        // Un orage ne commence pas par l'éclair : il commence par cette pression sourde qui
        // fait lever la tête. La source est à vingt-deux blocs, là où le plafond grésille à
        // l'écran, et le fondu d'entrée accélère jusqu'à la frappe.
        sl.playSound(null, ox, oy + 22.0, oz, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                SoundSource.PLAYERS, 0.70f, 0.88f);
        chargeSwell(sl, now, ox, oy + 16.0, oz, 6, 0.80f);
        sl.playSound(null, ox, oy, oz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.55f, 1.35f);
        // Ionisation : le sol crépite AVANT la frappe, le joueur voit le coup arriver.
        TestProcedure.schedule(sl, now + 6, () -> {
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                    ox, oy - 0.90, oz, 26, 1.80, 0.25, 1.80, 0.05);
            staticCrackle(sl, ox, oy - 0.70, oz, 2.20, 4, 0.55f);
        });

        // --- Quatre éclairs tombent du plafond, SUR CE QUI SE TROUVE AUTOUR ---
        // Avant, les points de chute étaient calculés : angle d'or, rayon fixe. C'était joli
        // sur le papier et inutile en jeu — quatre colonnes tombaient à côté des mobs pendant
        // qu'un groupe entier regardait. Maintenant chaque colonne cherche un corps vivant et
        // tombe DESSUS. La géométrie ne sert plus que de repli : s'il n'y a personne, l'éclair
        // doit bien tomber quelque part, et il retrouve alors sa position d'origine.
        //
        // L'ensemble partage une liste des corps déjà visés pour que deux colonnes ne se
        // superposent pas. Quand tout le monde a été servi, la liste est vidée : avec deux
        // mobs on préfère deux colonnes chacun plutôt que deux dans le vide.
        final java.util.Set<Integer> aimed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (int k = 0; k < 4; k++) {
            final int index = k;
            final int when = 5 + index * 7;
            TestProcedure.schedule(sl, now + when, () -> {
                LivingEntity mark = nearestChainTarget(sl, ox, oy, oz,
                        CHAIN_STRIKE_RADIUS, aimed, attackerId);
                if (mark == null && !aimed.isEmpty()) {
                    aimed.clear();
                    mark = nearestChainTarget(sl, ox, oy, oz,
                            CHAIN_STRIKE_RADIUS, aimed, attackerId);
                }
                double hx;
                double hz;
                // La HAUTEUR suit elle aussi le corps visé. Sans ça, une colonne tombait bien
                // sur les bonnes coordonnées au sol mais son volume de contact restait calé
                // sur la hauteur de la cible initiale : un mob perché sur un talus ou au fond
                // d'un trou prenait l'éclair en pleine figure et n'encaissait rien.
                double hy;
                if (mark != null) {
                    aimed.add(mark.getId());
                    hx = mark.getX();
                    hz = mark.getZ();
                    hy = mark.getY() + mark.getBbHeight() * 0.55;
                } else {
                    double aa = index * 2.3998277 + 0.7;
                    double rr = 3.60 + 5.40 * ((index * 3 + 1) % 4) / 3.0;
                    hx = ox + Math.cos(aa) * rr;
                    hz = oz + Math.sin(aa) * rr;
                    hy = oy;
                }
                // Le son part d'en haut : la décharge descend, elle ne jaillit pas du sol.
                sl.playSound(null, hx, hy + 14.0, hz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.PLAYERS, 0.52f, 1.42f + 0.07f * index);
                sl.playSound(null, hx, hy, hz, SoundEvents.MACE_SMASH_GROUND,
                        SoundSource.PLAYERS, 0.50f, 1.25f);
                PixelFx.send(sl, ParticleTypes.FLASH, hx, hy + 0.50, hz, 1, 0.0, 0.0, 0.0, 0.0);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, hx, hy + 0.30, hz,
                        24, 0.30, 0.70, 0.30, 0.22);
                broadcastNear(sl, (float) hx, (float) hy, (float) hz,
                        WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_IMPACT, 2,
                        (float) hx, (float) hy, (float) hz,
                        (float) hx, (float) hy, (float) hz, 0, 0.34f);
                // La colonne touche sur toute sa hauteur, le temps d'un éclair : trois ticks.
                addThunderHit(sl, attackerId, sl.getGameTime(),
                        sl.getGameTime() + 3L, hx, hz,
                        hy + 3.0, hy + 3.0, 1.30, 4.20, base * 0.42f, false, 30);
            });
        }

        // --- Tick 20 : la frappe principale ---
        TestProcedure.schedule(sl, now + 20, () -> {
            if (!target.isAlive()) return;
            // Plus gros que les quatre précédents, moins que le marteau : c'est un éclair,
            // pas une enclume. thunderBoom à 0,30 donne le claquement sans le sub.
            thunderBoom(sl, ox, oy, oz, 0.30f);
            PixelFx.send(sl, ParticleTypes.FLASH, ox, oy, oz, 2, 0.10, 0.10, 0.10, 0.0);
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, ox, oy, oz, 48, 0.70, 0.90, 0.70, 0.30);
            hurtWithCapturedWeapon(sl, attackerId, target,
                    base * staticMultiplier(sl, target));
            addStaticCharge(sl, target, 1, attackerId);
            // Électrocuté : les muscles se verrouillent une seconde et demie.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    30, 2, true, false));
        });

        // --- La chaîne : chaque relais est cherché AU MOMENT du bond ---
        // Avant, les six maillons étaient figés d'un coup au tick 24. Deux conséquences, les
        // deux mauvaises : un mob mort entre-temps faisait disparaître son maillon au lieu de
        // le passer au suivant, et la boîte de recherche restait collée à la hauteur de la
        // cible initiale — un mob trois blocs plus haut n'existait pas. La chaîne avançait
        // donc sur un itinéraire décidé d'avance, et sautait du monde.
        //
        // Maintenant elle se construit bond par bond : à chaque relais on repart de la
        // position RÉELLE du corps qu'on vient de toucher et on cherche le plus proche vivant
        // qui n'a pas encore été touché. C'est ce que fait un arc électrique.
        TestProcedure.schedule(sl, now + 24, () -> {
            java.util.Set<Integer> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
            seen.add(target.getId());
            if (attackerId >= 0) seen.add(attackerId);
            boolean anyLink = nearestChainTarget(sl, ox, oy, oz,
                    CHAIN_HOP_RADIUS, seen, attackerId) != null;

            // Le sol garde une trace et l'orage s'éloigne : fondu de sortie commun aux
            // deux cas, programmé une seule fois depuis le repérage.
            TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 75, () -> {
                sl.playSound(null, ox, oy - 0.8, oz, SoundEvents.COPPER_BULB_TURN_ON,
                        SoundSource.PLAYERS, 0.55f, 0.72f);
                staticCrackle(sl, ox, oy - 0.8, oz, 3.00, 5, 0.40f);
            });
            rollingThunder(sl, TestProcedure.currentTick(sl) + 20, ox, oy, oz, 0.62f);

            if (!anyLink) {
                // ----- CAS ENNEMI SEUL : l'énergie des six relais revient sur la cible -----
                final float solo = base * 0.26f * 6.0f / 3.0f;
                for (int shot = 0; shot < 3; shot++) {
                    final int rank = shot;
                    final int delay = 2 + shot * 12;
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + delay, () -> {
                        if (!target.isAlive()) return;
                        hurtWithCapturedWeapon(sl, attackerId, target,
                                solo * staticMultiplier(sl, target));
                        addStaticCharge(sl, target, 1, attackerId);
                        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS,
                                0.80f, 1.05f + 0.12f * rank);
                        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.50f, 1.72f);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                                26, 1, true, false));
                        PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                                target.getX(), target.getY() + target.getBbHeight() * 0.6,
                                target.getZ(), 22, 0.40, 0.55, 0.40, 0.18);
                        broadcastNear(sl, (float) target.getX(), (float) target.getY(),
                                (float) target.getZ(),
                                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_IMPACT, 2,
                                (float) target.getX(), (float) target.getY(), (float) target.getZ(),
                                (float) target.getX(), (float) target.getY(), (float) target.getZ(),
                                0, 0.38f);
                    });
                }
                return;
            }

            // ----- CAS PLUSIEURS ENNEMIS : la chaîne saute, et s'épuise -----
            TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 4, () ->
                    chainVoltHop(sl, attackerId, ox, oy, oz, ox, oy, oz, seen, 0, base));
        });
    }

    /** Portée d'un saut de la chaîne. Au-delà, l'arc ne tient plus. */
    private static final double CHAIN_HOP_RADIUS = 8.0;
    /** Portée de recherche des quatre colonnes qui tombent autour du point visé. */
    private static final double CHAIN_STRIKE_RADIUS = 10.0;
    /** Nombre maximal de relais après la cible initiale. */
    private static final int CHAIN_MAX_HOPS = 6;
    /** Ticks entre deux relais. La chaîne doit se lire comme une chaîne, pas comme une salve. */
    private static final int CHAIN_HOP_DELAY = 7;

    /**
     * Le corps vivant le plus proche d'un point, parmi ceux qui n'ont pas déjà été servis.
     *
     * <p>Trois détails qui comptent. La recherche se fait dans un <b>disque</b> : la boîte
     * passée au moteur est carrée parce qu'il ne sait pas chercher autrement, mais un coin de
     * carré est à 1,41 fois le rayon annoncé et l'arc n'y porte pas. La hauteur est centrée sur
     * le point de départ et non sur la cible initiale, sinon un mob perché ou dans un trou
     * n'existe tout simplement pas. Et l'attaquant est exclu, évidemment.
     */
    private static LivingEntity nearestChainTarget(ServerLevel sl, double cx, double cy, double cz,
                                                   double radius, java.util.Set<Integer> skip,
                                                   int attackerId) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - 6.0, cz - radius,
                        cx + radius, cy + 6.0, cz + radius))) {
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (attackerId >= 0 && candidate.getId() == attackerId) continue;
            if (skip.contains(candidate.getId())) continue;
            double ddx = candidate.getX() - cx;
            double ddz = candidate.getZ() - cz;
            double d = ddx * ddx + ddz * ddz;
            if (d > radius * radius) continue;
            if (d < bestDist) { bestDist = d; best = candidate; }
        }
        return best;
    }

    /**
     * Un bond de la chaîne, puis le suivant.
     *
     * <p>La méthode cherche elle-même sa victime depuis le point d'où part l'arc, la frappe,
     * puis se reprogramme depuis la position de cette victime. Rien n'est décidé d'avance :
     * un mob qui meurt, qui arrive ou qui se déplace pendant la propagation est pris en compte
     * au bond suivant. L'ensemble des corps déjà touchés circule d'un bond à l'autre — une
     * chaîne ne repasse pas deux fois sur le même.
     */
    private static void chainVoltHop(ServerLevel sl, int attackerId,
                                     double origX, double origY, double origZ,
                                     double fx, double fy, double fz,
                                     java.util.Set<Integer> seen, int rank, float base) {
        LivingEntity victim = nearestChainTarget(sl, fx, fy, fz, CHAIN_HOP_RADIUS, seen, attackerId);
        if (victim == null) return;
        seen.add(victim.getId());

        final double jx = victim.getX();
        final double jy = victim.getY() + victim.getBbHeight() * 0.55;
        final double jz = victim.getZ();
        final float share = base * (float) Math.pow(0.82, rank + 1);

        hurtWithCapturedWeapon(sl, attackerId, victim, share * staticMultiplier(sl, victim));
        addStaticCharge(sl, victim, 1, attackerId);
        // Un relais n'est pas un coup de tonnerre : c'est un arc qui saute d'un corps à
        // l'autre. Le son du conduit attaque bien plus vite et monte en hauteur à chaque bond,
        // ce qui rend la chaîne audible comme une chaîne.
        sl.playSound(null, jx, jy, jz, SoundEvents.CONDUIT_ATTACK_TARGET,
                SoundSource.PLAYERS, 0.85f, 1.10f + 0.09f * rank);
        sl.playSound(null, jx, jy, jz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 0.36f, 1.60f);
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 1, true, false));
        victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 70, 0, true, false));
        PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, jx, jy, jz, 26, 0.42, 0.52, 0.42, 0.20);
        // Le maillon part vers le client : origine de la chaîne (pour retrouver l'animation
        // en cours) et position RÉELLE de la victime. L'arc sera tracé entre les deux vrais
        // points, plus sur un anneau inventé.
        broadcastNear(sl, (float) jx, (float) jy, (float) jz,
                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_THUNDER_LINK, rank,
                (float) origX, (float) origY, (float) origZ,
                (float) jx, (float) jy, (float) jz, 0, 1.0f);

        if (rank + 1 >= CHAIN_MAX_HOPS) return;
        TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + CHAIN_HOP_DELAY, () ->
                chainVoltHop(sl, attackerId, origX, origY, origZ, jx, jy, jz, seen, rank + 1, base));
    }

    /**
     * MARTEAU DE THOR (250 ticks) — la signature <b>de frappe</b> de la famille.
     *
     * <p>Elle remplace la « surcadence cyber-X », qui était un bonus de cadence : un buff ne se
     * voit pas, et celui-ci n'était lisible que dans les chiffres. Le marteau fait l'inverse —
     * il ne donne rien au porteur, il <b>frappe</b>, et il laisse une trace sur le terrain.
     *
     * <p><b>L'impact.</b> Au tick 50, exactement quand le marteau touche le sol à l'écran, un
     * carré de <b>3×3 blocs</b> centré sur la cible prend <b>deux fois les dégâts de l'arme</b>
     * qui a déclenché le coup. Deux fois, et pas deux fois quelque chose : la valeur reçue ici
     * est celle de l'événement de dégâts, <b>enchantements et effets d'attaque compris</b>, donc
     * un Tranchant V ou une potion de Force se répercutent tels quels.
     *
     * <p><b>L'aura.</b> L'impact laisse le sol sous tension <b>dix secondes</b> (200 ticks) sur
     * un <b>disque de 6 blocs de rayon</b> — bien plus large que la frappe elle-même. Tout ce
     * qui s'y trouve — <b>mobs et joueurs</b>, l'attaquant excepté — prend une décharge toutes
     * les secondes, dont la <b>puissance décroît du centre vers le bord</b> : plein tarif au
     * point de chute, 15 % à la limite. Le plancher existe pour que le bord reste une menace
     * plutôt qu'une ligne morte.
     *
     * <p>C'est un <b>disque</b> et non un carré : la boîte de recherche est carrée parce que
     * le moteur ne sait pas chercher autrement, mais la distance est vérifiée ensuite.
     *
     * <p><b>Plusieurs ennemis.</b> Le 3×3 de la frappe touche tout le monde à plein tarif, sans
     * dégressivité : c'est le sens d'un coup de masse. <b>Ennemi seul</b> : l'aura reste, et
     * comme un duel se déroule rarement immobile, elle redevient une menace au moindre recul.
     */
    private static void applyHammerGameplay(LivingEntity attacker, LivingEntity target,
                                            ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        // DEUX HORLOGES, et il ne faut pas les confondre. TestProcedure.schedule compte en
        // ticks de SERVEUR (getTickCount, remis à zéro à chaque démarrage) ; les zones
        // persistantes comparent des temps de MONDE (getGameTime, qui suit la sauvegarde).
        // Mélanger les deux faisait expirer l'aura avant même qu'elle n'existe, sur tout
        // monde plus vieux que la session en cours. Le vent sépare déjà les deux, on fait
        // pareil. Les noms disent lequel est lequel.
        final int now = TestProcedure.currentTick(sl);
        // « Deux fois l'attaque de l'arme » au sens strict. Le plancher ne sert qu'aux armes
        // sans dégâts déclarés, pour qu'un marteau divin ne fasse jamais zéro.
        final float strike = Math.max(2.0f, weaponDamage * 2.0f);

        // Le centre est figé au moment du coup : la zone marque le SOL, pas la cible.
        final double zx = target.getX();
        final double zy = target.getY();
        final double zz = target.getZ();

        // --- Le porteur dégage. Sinon il ne voit rien du tout. ---
        // Un marteau de trente blocs de haut qui tombe à un mètre de vous ne se regarde pas :
        // la caméra est DANS la masse, et tout ce qu'on en retire est un aplat jaune qui
        // remplit l'écran. Le coup part, on ne sait pas de quoi. On le recule donc de six
        // blocs au moment de l'invocation — il lui reste cinquante ticks pour se retourner et
        // voir la chose arriver. Il ne prend aucun dégât de son propre marteau, ce n'est pas
        // une question d'équilibrage mais de cadrage.
        clearTheBlastZone(sl, attacker, zx, zz, HAMMER_CLEARANCE);

        // --- L'appel : le marteau se matérialise à trente blocs, et ça s'entend ---
        sl.playSound(null, zx, zy + 1.0, zz, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                0.55f, 0.70f);
        // Le grondement d'invocation part de très haut : la source sonore est au point
        // d'apparition du marteau, pas sur la cible. On lève la tête avant de comprendre.
        sl.playSound(null, zx, zy + 26.0, zz, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                SoundSource.PLAYERS, 1.00f, 0.72f);
        TestProcedure.schedule(sl, now + 4, () ->
                sl.playSound(null, zx, zy + 22.0, zz, SoundEvents.WARDEN_SONIC_CHARGE,
                        SoundSource.PLAYERS, 0.80f, 0.55f));

        // --- La chute : un bruit d'ENCLUME, et il se rapproche ---
        // Cinq passages répartis sur les quarante-deux ticks de descente. La hauteur de la
        // source suit la vraie trajectoire (chute en t²), donc le son descend avec l'objet ;
        // le volume monte et la hauteur tonale aussi, ce que l'oreille lit comme un
        // rapprochement. C'est tout le principe d'un effet Doppler, sans en avoir un.
        final int[] FALL_CUE = {10, 22, 32, 40, 46};
        for (int step = 0; step < FALL_CUE.length; step++) {
            final int when = FALL_CUE[step];
            // même loi que le rendu : 30 blocs parcourus en t², sur 42 ticks à partir du 8ᵉ
            final double prog = Math.min(1.0, Math.max(0.0, (when - 8.0) / 42.0));
            final double hy = zy + 30.0 * (1.0 - prog * prog);
            final float gain = 0.30f + 0.62f * (float) (prog * prog);
            final float pitch = 0.58f + 0.52f * (float) prog;
            TestProcedure.schedule(sl, now + when, () -> {
                sl.playSound(null, zx, hy, zz, SoundEvents.ANVIL_FALL,
                        SoundSource.PLAYERS, gain, pitch);
                sl.playSound(null, zx, hy, zz, SoundEvents.WIND_CHARGE_THROW,
                        SoundSource.PLAYERS, gain * 0.85f, pitch * 0.80f);
                sl.playSound(null, zx, hy, zz, SoundEvents.NOTE_BLOCK_BELL.value(),
                        SoundSource.PLAYERS, 0.26f, pitch + 0.30f);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, zx, hy, zz,
                        18, 1.00, 1.40, 1.00, 0.06);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, zx, zy + 14.0, zz,
                        22, 2.60, 7.00, 2.60, 0.04);
            });
        }

        // --- Et le marteau touche ce qu'il traverse ---
        // Un objet de deux mètres qui tombe de trente blocs ne peut pas ignorer ce qui se
        // trouve sur son chemin. Le volume de contact descend exactement comme le rendu ; un
        // mob perché à mi-hauteur encaisse au moment précis où la tête passe sur lui, avec la
        // moitié de la force — le plein tarif reste pour l'écrasement au sol.
        final long gameNow = sl.getGameTime();
        addThunderHit(sl, attackerId, gameNow + 8L, gameNow + 50L, zx, zz,
                zy + 30.0, zy + 0.30, 1.45, 1.00, strike * 0.50f, true, 40);

        // --- Tick 50 : la frappe. Le 3×3 prend deux fois l'arme ---
        // 50 et non 40 : la chute dure maintenant 42 ticks, parce qu'un corps lâché de 30
        // blocs met réellement ce temps-là à tomber. Le coup doit porter à l'instant exact
        // où le marteau touche le sol à l'écran, sinon le joueur voit deux événements.
        TestProcedure.schedule(sl, now + 50, () -> {
            // LE coup. Quatre couches empilées par thunderBoom — claquement, tonnerre,
            // souffle, sub — plus l'enclume qui se pose, parce que c'est exactement ce que
            // l'œil voit : une masse de métal qui finit sa course dans le sol.
            thunderBoom(sl, zx, zy, zz, 1.0f);
            sl.playSound(null, zx, zy, zz, SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 1.10f, 0.48f);
            sl.playSound(null, zx, zy, zz, SoundEvents.TRIDENT_THROW.value(),
                    SoundSource.PLAYERS, 0.80f, 0.55f);
            // Le tonnerre roule ensuite et s'éteint en quatre répliques : un orage ne
            // s'arrête jamais net.
            rollingThunder(sl, TestProcedure.currentTick(sl), zx, zy, zz, 1.0f);
            PixelFx.send(sl, ParticleTypes.FLASH, zx, zy + 0.4, zz, 2, 0.10, 0.10, 0.10, 0.0);
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, zx, zy + 0.3, zz,
                    80, 1.50, 0.50, 1.50, 0.42);

            // 3×3 blocs : 1,5 de part et d'autre du centre. La hauteur est volontairement
            // généreuse — un marteau qui écrase une dalle touche aussi ce qui la survole.
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(zx - 1.5, zy - 1.5, zz - 1.5, zx + 1.5, zy + 2.5, zz + 1.5))) {
                if (!victim.isAlive()) continue;
                if (attackerId >= 0 && victim.getId() == attackerId) continue;
                hurtWithCapturedWeapon(sl, attackerId, victim, strike);
                addStaticCharge(sl, victim, 2, attackerId);
                // Sonné. Un coup de masse n'a jamais tué que la moitié des gens qu'il met au
                // sol : les trois secondes suivantes, la victime bouge mal et frappe mou.
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        60, 2, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                        80, 1, true, false));
                // Le souffle : il pousse vers l'extérieur et vers le haut, depuis le point de
                // chute. Au centre exact la direction est indéterminée, on soulève alors tout
                // droit plutôt que de diviser par zéro.
                double px = victim.getX() - zx;
                double pz = victim.getZ() - zz;
                double pl = Math.sqrt(px * px + pz * pz);
                if (pl < 1.0e-3) { px = 0.0; pz = 0.0; } else { px /= pl; pz /= pl; }
                victim.push(px * 0.52, 0.46, pz * 0.52);
                victim.hurtMarked = true;
                sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.90f, 0.80f);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        22, 0.34, 0.44, 0.34, 0.20);
            }

            // Le sol reste sous tension dix secondes à partir de l'impact.
            // getGameTime() et non now : on est DANS le lambda, il s'exécute au tick de
            // l'impact, et tickShockZones lit la même horloge. C'est ce décalage d'horloge
            // qui empêchait l'aura d'apparaître.
            SHOCK_ZONES.add(new ShockZone(sl, sl.getGameTime() + SHOCK_ZONE_TICKS, zx, zy, zz,
                    attackerId, Math.max(0.5f, weaponDamage * 0.22f), SHOCK_ZONE_RADIUS));
        });
    }

    /** De combien de blocs le porteur recule à l'invocation du marteau. */
    private static final double HAMMER_CLEARANCE = 11.0;
    /**
     * Recul du porteur à l'appel de l'orage. Plus court que celui du marteau : le nuage ne
     * tombe pas sur le porteur, il faut seulement sortir de sa jupe pour le voir en entier.
     */
    private static final double STORM_CLEARANCE = 10.0;

    /**
     * Écarte le porteur du point de chute, en franchissant le relief au lieu de s'y écraser.
     *
     * <p>Une poussée horizontale pure ne vaut rien : un mur, un talus ou une simple marche
     * derrière le joueur l'arrête au premier bloc et il reste dans la zone. La méthode
     * <b>sonde le trajet</b> avant de pousser — douze pas d'un demi-bloc, et à chaque pas la
     * hauteur libre la plus basse où la boîte du joueur tient. La plus haute de ces valeurs
     * donne la montée à franchir, et c'est elle qui fixe la composante verticale.
     *
     * <p>Le reste est de la balistique. La hauteur d'un saut vaut à peu près
     * {@code v² / 2g} avec {@code g = 0,08} bloc par tick² ; on inverse pour obtenir la vitesse
     * verticale, ce qui donne au passage la durée du vol. Horizontalement, un corps en l'air
     * conserve 91 % de sa vitesse par tick : la distance parcourue est donc une série
     * géométrique, {@code v·(1−0,91ⁿ)/0,09}, qu'on inverse à son tour pour viser les six blocs
     * demandés. Sans ce calcul, une poussée constante envoie à trois blocs ou à quinze selon la
     * hauteur du saut.
     *
     * <p>La distance de chute accumulée est remise à zéro à l'apogée : le joueur n'a pas
     * demandé ce vol, il n'a pas à en payer l'atterrissage.
     */
    private static void clearTheBlastZone(ServerLevel sl, LivingEntity rider,
                                          double fromX, double fromZ, double distance) {
        if (rider == null || !rider.isAlive()) return;
        // Seul un joueur recule. La raison d'être de ce recul est une raison de CADRAGE : il
        // faut une caméra assez loin pour voir le marteau. Un mob n'a pas de caméra, et le
        // projeter à six blocs à chacun de ses coups ne ferait que casser son combat.
        if (!(rider instanceof Player)) return;

        // Direction : du point de chute vers le porteur. S'il est pile dessus, la direction
        // n'existe pas — on prend alors l'inverse de son regard, c'est-à-dire « en arrière ».
        double dx = rider.getX() - fromX;
        double dz = rider.getZ() - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.35) {
            Vec3 look = rider.getLookAngle();
            dx = -look.x;
            dz = -look.z;
            len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-4) { dx = 0.0; dz = -1.0; len = 1.0; }
        }
        dx /= len;
        dz /= len;

        // Sondage du relief. On cherche, à chaque pas, la hauteur la plus basse à laquelle la
        // boîte du joueur passe. Un mur plein compte pour la montée maximale : mieux vaut
        // sauter dedans que rester sous le marteau.
        final AABB box = rider.getBoundingBox();
        final double halfW = box.getXsize() * 0.5;
        final double height = box.getYsize();
        final double footY = rider.getY();
        double rise = 0.0;
        final int probes = 12;
        for (int s = 1; s <= probes; s++) {
            double t = distance * s / probes;
            double px = rider.getX() + dx * t;
            double pz = rider.getZ() + dz * t;
            double clear = -1.0;
            for (double h = 0.0; h <= 5.0; h += 0.5) {
                AABB probe = new AABB(px - halfW, footY + h, pz - halfW,
                        px + halfW, footY + h + height, pz + halfW);
                if (sl.noCollision(rider, probe)) { clear = h; break; }
            }
            if (clear < 0.0) clear = 5.0;
            if (clear > rise) rise = clear;
        }

        final double climb = Math.min(rise, 4.5);
        // v = racine(2·g·h), avec un bloc de marge pour ne pas raser l'arête.
        // Puis TOUT EST DOUBLÉ : la hauteur atteinte varie comme le carré de la vitesse,
        // doubler v ne monte donc pas deux fois plus haut mais QUATRE fois. C'est demandé,
        // c'est assumé, et c'est ce qui fait qu'on domine la scène au lieu de la longer.
        // Le plafond reste : au-delà de 1,50 le vol dure plus longtemps que l'animation.
        double vy = Math.sqrt(0.16 * (climb + 1.15)) * 2.0;
        vy = Math.max(0.84, Math.min(1.50, vy));
        final int flight = (int) Math.max(10.0, 2.0 * vy / 0.08);
        // Série géométrique de raison 0,91 : distance = v·(1−0,91ⁿ)/0,09.
        final double reach = Math.max(0.25, 1.0 - Math.pow(0.91, flight));
        double vh = distance * 0.09 / reach;
        vh = Math.max(0.18, Math.min(1.35, vh));

        // ---- ET MAINTENANT, LE FAIRE SAVOIR AU CLIENT ----
        // Premier essai : setDeltaMovement + hurtMarked, comme partout ailleurs dans ce
        // fichier. Aucun effet, et pour une raison qui n'a rien d'évident. Pour un MOB le
        // serveur fait autorité : on change sa vitesse, il bouge. Pour un JOUEUR c'est le
        // client qui simule son propre déplacement ; la vitesse posée côté serveur n'est
        // qu'un avis, et elle est écrasée dès le paquet de position suivant. Pire, le coup
        // part depuis l'intérieur de l'événement de dégâts, donc AVANT que le serveur ne
        // traite le reste du tick : quand le paquet de vélocité différé finit par être
        // construit, la composante verticale a déjà été remise à zéro par la collision avec
        // le sol, et le joueur reçoit un vecteur mort.
        //
        // La seule méthode fiable est de fabriquer le paquet nous-mêmes, avec le vecteur
        // voulu, et de le remettre directement à la connexion du joueur. On ne lève
        // volontairement PAS hurtMarked : il provoquerait l'envoi d'un second paquet en fin
        // de tick, construit à partir de la vitesse serveur entre-temps rabotée, qui
        // annulerait celui-ci.
        final Vec3 launch = new Vec3(dx * vh, vy, dz * vh);
        rider.setDeltaMovement(launch);
        rider.hasImpulse = true;
        rider.fallDistance = 0.0F;
        if (rider instanceof ServerPlayer pushed) {
            pushed.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(pushed));
        }
        sl.playSound(null, rider.getX(), rider.getY() + 0.5, rider.getZ(),
                SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.70f, 1.25f);
        PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                rider.getX(), rider.getY() + 0.40, rider.getZ(), 14, 0.30, 0.20, 0.30, 0.08);
        // Pas de dégâts de chute pour un vol qu'on n'a pas demandé. La distance accumulée est
        // remise à zéro toutes les quatre frames jusqu'à la frappe : elle ne dépasse jamais
        // deux blocs, donc jamais le seuil des trois. La garantie couvre tout l'enchaînement,
        // y compris le cas où le recul fait passer par-dessus un bord.
        // Le vol dure maintenant deux fois la montée, plus la retombée depuis un apogée
        // quatre fois plus haut. La boucle couvrait « montée + 28 » ; elle s'arrêtait donc
        // en plein ciel et rendait au joueur toute la chute. On couvre l'aller-retour.
        final int start = TestProcedure.currentTick(sl);
        for (int t = 4; t <= 2 * flight + 30; t += 4) {
            TestProcedure.schedule(sl, start + t, () -> {
                if (rider.isAlive()) rider.fallDistance = 0.0F;
            });
        }

        // Filet de sécurité. On mesure le déplacement depuis SA position de départ à lui, et
        // non depuis le point de chute : la distance au point de chute dépend de l'allonge de
        // l'arme et ne dit rien de fiable. Huit ticks après une poussée réussie il a parcouru
        // plus de quatre blocs ; s'il en est encore à moins de deux, le vecteur a été mangé
        // en route et on le renvoie. Une seule fois, et seulement s'il est toujours au-dessus
        // du cratère : l'objectif est de rattraper un échec, pas de catapulter deux fois ni de
        // reprendre la main sur un joueur qui s'est déjà écarté tout seul.
        final double homeX = rider.getX();
        final double homeZ = rider.getZ();
        TestProcedure.schedule(sl, start + 8, () -> {
            if (!(rider instanceof ServerPlayer again) || !again.isAlive()) return;
            double movedX = again.getX() - homeX;
            double movedZ = again.getZ() - homeZ;
            if (movedX * movedX + movedZ * movedZ >= 4.0) return;
            again.setDeltaMovement(launch);
            again.hasImpulse = true;
            again.fallDistance = 0.0F;
            again.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(again));
        });
    }

    // --------------------------------------------------------------------------------------------
    // Sol électrifié du marteau
    // --------------------------------------------------------------------------------------------

    /**
     * Une dalle sous tension laissée par le marteau. Le centre est figé : c'est le <b>sol</b>
     * qui est électrifié, pas la cible, et la zone ne suit donc personne.
     */
    private record ShockZone(ServerLevel level, long expires, double cx, double cy, double cz,
                             int attackerId, float tickDamage, double radius) { }

    /** Durée de l'aura : dix secondes. */
    private static final long SHOCK_ZONE_TICKS = 200L;
    /** Rayon de l'aura, en blocs. La décharge décroît du centre vers ce bord. */
    private static final double SHOCK_ZONE_RADIUS = 6.0;
    /** Intervalle entre deux décharges pour une même victime. */
    private static final long SHOCK_ZONE_PERIOD = 20L;

    private static final List<ShockZone> SHOCK_ZONES =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Une seule passe par tick de jeu, quelle que soit l'entité qui déclenche le hook. */
    private static long LAST_SHOCK_ZONE_TICK = Long.MIN_VALUE;
    /** Prochain tick où chaque victime pourra reprendre une décharge. */
    private static final java.util.Map<Integer, Long> SHOCK_COOLDOWN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Balaye les dalles sous tension. Même garde-fou que pour les tornades : le hook est appelé
     * une fois par entité vivante et par tick, donc sans ce verrou une dalle frapperait autant
     * de fois qu'il y a de mobs chargés dans le monde.
     */
    private static void tickShockZones(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now == LAST_SHOCK_ZONE_TICK) return;
        LAST_SHOCK_ZONE_TICK = now;
        if (SHOCK_ZONES.isEmpty()) {
            if (!SHOCK_COOLDOWN.isEmpty()) SHOCK_COOLDOWN.clear();
            return;
        }
        // Une dalle qui s'éteint le fait savoir. Sans ça, dix secondes de grésillement
        // s'arrêtent sur une image, et le joueur ne sait pas s'il peut revenir marcher
        // dessus. Le petit déclic de cuivre dit « c'est fini », et il ne coûte rien.
        for (ShockZone zone : SHOCK_ZONES) {
            if (now >= zone.expires() && zone.level() == sl) {
                sl.playSound(null, zone.cx(), zone.cy() + 0.3, zone.cz(),
                        SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.PLAYERS, 0.50f, 0.70f);
                sl.playSound(null, zone.cx(), zone.cy() + 0.3, zone.cz(),
                        SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 0.42f, 1.30f);
            }
        }
        SHOCK_ZONES.removeIf(zone -> now >= zone.expires() || zone.level() != sl);
        if (!SHOCK_COOLDOWN.isEmpty()) SHOCK_COOLDOWN.values().removeIf(until -> now >= until + 80L);
        for (ShockZone zone : SHOCK_ZONES) {
            final double r = zone.radius();
            // Grésillement : des étincelles tirées au hasard dans le DISQUE, deux fois plus
            // denses vers le centre — la racine carrée donnerait un tirage uniforme, la
            // puissance 0,72 resserre vers le milieu, là où la décharge fait le plus mal.
            if ((now & 3L) == 0L) {
                for (int k = 0; k < 3; k++) {
                    double rr = r * Math.pow(sl.random.nextDouble(), 0.72);
                    double aa = sl.random.nextDouble() * Math.PI * 2.0;
                    PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                            zone.cx() + Math.cos(aa) * rr, zone.cy() + 0.12,
                            zone.cz() + Math.sin(aa) * rr, 2, 0.06, 0.03, 0.06, 0.02);
                }
            }
            // Le bruit de la dalle. Une fois toutes les quinze frames environ, à un endroit
            // tiré au hasard dans le disque : asymétrique comme les étincelles, et assez
            // rare pour qu'on l'entende encore au bout de dix secondes. Un crépitement par
            // tick serait un bourdonnement, et un bourdonnement, on cesse de l'écouter.
            if ((now % 15L) == 0L) {
                double rr = r * Math.pow(sl.random.nextDouble(), 0.55);
                double aa = sl.random.nextDouble() * Math.PI * 2.0;
                sl.playSound(null, zone.cx() + Math.cos(aa) * rr, zone.cy() + 0.25,
                        zone.cz() + Math.sin(aa) * rr, SoundEvents.REDSTONE_TORCH_BURNOUT,
                        SoundSource.PLAYERS, 0.42f, 1.25f + sl.random.nextFloat() * 0.60f);
            }
            if ((now % 47L) == 0L) {
                sl.playSound(null, zone.cx(), zone.cy() + 0.4, zone.cz(),
                        SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.38f, 0.82f);
            }
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(zone.cx() - r, zone.cy() - 1.0, zone.cz() - r,
                            zone.cx() + r, zone.cy() + 2.5, zone.cz() + r))) {
                if (!victim.isAlive()) continue;
                if (zone.attackerId() >= 0 && victim.getId() == zone.attackerId()) continue;
                // Disque, pas carré : la boîte de recherche est carrée, l'aura ne l'est pas.
                double ddx = victim.getX() - zone.cx();
                double ddz = victim.getZ() - zone.cz();
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist > r) continue;
                Long until = SHOCK_COOLDOWN.get(victim.getId());
                if (until != null && now < until) continue;
                SHOCK_COOLDOWN.put(victim.getId(), now + SHOCK_ZONE_PERIOD);
                // Puissance décroissante : plein tarif au centre, 15 % au bord. Le plancher
                // existe pour que le bord reste une menace et non une ligne morte.
                float fall = (float) (1.0 - dist / r);
                hurtWithCapturedWeapon(sl, zone.attackerId(), victim,
                        zone.tickDamage() * (0.15f + 0.85f * fall));
                // Rester dedans coûte de la mobilité, et d'autant plus qu'on est au centre.
                // C'est ce qui transforme l'aura en contrôle de terrain : on peut encaisser
                // la décharge, mais on sort moins vite qu'on n'est entré.
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        30, fall > 0.55f ? 1 : 0, true, false));
                // Un son court sur la victime elle-même, pas sur toute la zone : on sait
                // qu'on vient de prendre une décharge sans que la dalle devienne une alarme.
                sl.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.4,
                        victim.getZ(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS,
                        0.34f + 0.26f * fall, 1.55f);
                // Pas de son ici : une décharge par seconde et par victime pendant dix
                // secondes deviendrait vite insupportable. Les étincelles suffisent à dire
                // que la dalle mord.
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.35, victim.getZ(),
                        10, 0.26, 0.30, 0.26, 0.10);
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Dégâts par CONTACT — la géométrie à l'écran touche vraiment
    // --------------------------------------------------------------------------------------------

    /**
     * Un volume qui suit une pièce de l'animation et blesse ce qu'il traverse.
     *
     * <p>Jusqu'ici, les dégâts de foudre étaient posés par <i>minuterie</i> : à tel tick, tout
     * ce qui se trouve dans tel rayon encaisse. C'est juste pour une onde de choc, qui est
     * effectivement instantanée et omniprésente, mais c'est faux pour un objet qui <b>tombe</b>.
     * Le marteau met quarante-deux ticks à descendre trente blocs : pendant ce temps il occupe
     * une position précise, et un mob qui se tient à mi-hauteur — sur un toit, un échafaudage,
     * en vol — devrait le prendre en pleine tête <i>avant</i> qu'il n'atteigne le sol.
     *
     * <p>Un volume de contact règle ça. Il connaît sa hauteur à chaque tick, suit exactement la
     * même loi que le rendu (chute en {@code t²}), et ne frappe une victime <b>qu'une seule
     * fois</b> : traverser un corps n'est pas y rester. Le jeu de clés {@code hit} sert à ça.
     *
     * @param y0     hauteur au départ, {@code y1} hauteur à l'arrivée (identiques = volume fixe)
     * @param t0     tick d'apparition, {@code t1} tick de disparition
     * @param radius rayon horizontal, {@code halfHeight} demi-hauteur du cylindre
     * @param ease   {@code true} si la descente accélère en {@code t²}, {@code false} si linéaire
     */
    private record ThunderHit(ServerLevel level, int attackerId, long t0, long t1,
                              double cx, double cz, double y0, double y1,
                              double radius, double halfHeight, float damage, boolean ease,
                              int slowTicks, Set<Integer> hit) { }

    private static final List<ThunderHit> THUNDER_HITS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Une seule passe par tick de jeu, comme pour les dalles et les tornades. */
    private static long LAST_THUNDER_HIT_TICK = Long.MIN_VALUE;

    /** Enregistre un volume de contact. Le jeu de clés est créé ici, un par volume. */
    private static void addThunderHit(ServerLevel sl, int attackerId, long t0, long t1,
                                      double cx, double cz, double y0, double y1,
                                      double radius, double halfHeight, float damage,
                                      boolean ease, int slowTicks) {
        if (!(damage > 0.0f) || t1 <= t0) return;
        THUNDER_HITS.add(new ThunderHit(sl, attackerId, t0, t1, cx, cz, y0, y1,
                radius, halfHeight, damage, ease,
                slowTicks, java.util.concurrent.ConcurrentHashMap.newKeySet()));
    }

    /**
     * Balaye les volumes de contact. Le verrou de tick est indispensable : le hook appelant est
     * tiré une fois par entité vivante, donc sans lui un marteau frapperait autant de fois
     * qu'il y a de mobs chargés dans la dimension.
     */
    private static void tickThunderHits(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now == LAST_THUNDER_HIT_TICK) return;
        LAST_THUNDER_HIT_TICK = now;
        if (THUNDER_HITS.isEmpty()) return;
        THUNDER_HITS.removeIf(h -> now > h.t1() || h.level() != sl);

        for (ThunderHit h : THUNDER_HITS) {
            if (now < h.t0()) continue;
            // Position du volume à cet instant précis. La même courbe que le rendu, sinon
            // l'oreille et l'œil ne diraient pas la même chose que les points de vie.
            double p = (double) (now - h.t0()) / (double) Math.max(1L, h.t1() - h.t0());
            if (p > 1.0) p = 1.0;
            double k = h.ease() ? p * p : p;
            double cy = h.y0() + (h.y1() - h.y0()) * k;

            double r = h.radius();
            double hh = h.halfHeight();
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(h.cx() - r, cy - hh, h.cz() - r,
                            h.cx() + r, cy + hh + 1.2, h.cz() + r))) {
                if (!victim.isAlive()) continue;
                if (h.attackerId() >= 0 && victim.getId() == h.attackerId()) continue;
                if (h.hit().contains(victim.getId())) continue;
                double ddx = victim.getX() - h.cx();
                double ddz = victim.getZ() - h.cz();
                if (ddx * ddx + ddz * ddz > r * r) continue;
                // Recouvrement vertical réel : le volume contre la boîte du mob, pas son pied.
                double vTop = victim.getY() + victim.getBbHeight();
                if (vTop < cy - hh || victim.getY() > cy + hh) continue;

                h.hit().add(victim.getId());
                hurtWithCapturedWeapon(sl, h.attackerId(), victim,
                        h.damage() * staticMultiplier(sl, victim));
                addStaticCharge(sl, victim, 1, h.attackerId());
                if (h.slowTicks() > 0) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            h.slowTicks(), 1, true, false));
                }
                // Le son de touche : il part de la VICTIME, pas du centre de l'effet. C'est ce
                // qui fait entendre qu'on a été accroché au passage plutôt que soufflé de loin.
                sl.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                        victim.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS,
                        0.70f, 1.15f + sl.random.nextFloat() * 0.25f);
                sl.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                        victim.getZ(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS,
                        0.42f, 1.50f);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        26, 0.34, 0.44, 0.34, 0.26);
                PixelFx.send(sl, ParticleTypes.FLASH,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Palette sonore de la foudre
    // --------------------------------------------------------------------------------------------

    /**
     * Le gros impact. Un seul échantillon ne donne jamais une détonation : le tonnerre de
     * Minecraft est long et mou, il manque l'attaque. On empile donc quatre couches qui se
     * complètent — le claquement sec du marteau de guerre, la masse du tonnerre, le souffle de
     * l'explosion, et le sub du Warden qui met le poids en bas du spectre.
     *
     * @param power 0 → gros coup, 1 → coup dévastateur ; module volume et gravité
     */
    private static void thunderBoom(ServerLevel sl, double x, double y, double z, float power) {
        float p = Math.max(0.0f, Math.min(1.0f, power));
        sl.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS,
                0.95f + 0.45f * p, 0.90f - 0.18f * p);
        sl.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                1.15f + 0.55f * p, 0.72f - 0.14f * p);
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.70f + 0.35f * p, 0.62f - 0.10f * p);
        sl.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                0.45f + 0.40f * p, 0.58f - 0.12f * p);
    }

    /**
     * Le tonnerre qui roule et s'éteint. C'est le fondu de sortie de toutes les signatures de
     * foudre : quatre répliques de plus en plus faibles, de plus en plus graves et de plus en
     * plus espacées, décalées autour du point d'impact pour qu'elles ne sortent pas du même
     * haut-parleur. Sans ça, un orage s'arrête net, ce qu'aucun orage ne fait.
     */
    private static void rollingThunder(ServerLevel sl, int now, double x, double y, double z,
                                       float volume) {
        // int et non long : TestProcedure.schedule attend un tick de serveur, qui est un int.
        final int[] WHEN = {14, 31, 53, 82};
        final float[] GAIN = {0.70f, 0.46f, 0.28f, 0.15f};
        for (int i = 0; i < WHEN.length; i++) {
            final float v = volume * GAIN[i];
            final float pitch = 0.78f - 0.07f * i;
            final double ox = x + (i % 2 == 0 ? 7.0 : -6.0) * (i + 1) * 0.5;
            final double oz = z + (i % 2 == 0 ? -5.0 : 8.0) * (i + 1) * 0.5;
            TestProcedure.schedule(sl, now + WHEN[i], () ->
                    sl.playSound(null, ox, y + 6.0, oz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                            SoundSource.PLAYERS, v, pitch));
        }
    }

    /**
     * Le fondu d'entrée : l'air se charge. Volume croissant, intervalle décroissant — on entend
     * la tension monter avant de voir quoi que ce soit. C'est la moitié du travail d'un effet
     * de frappe : prévenir.
     */
    private static void chargeSwell(ServerLevel sl, int now, double x, double y, double z,
                                    int steps, float volume) {
        for (int i = 0; i < steps; i++) {
            final float v = volume * (0.28f + 0.72f * i / Math.max(1, steps - 1));
            final float pitch = 0.62f + 0.11f * i;
            final int when = now + (int) Math.round(i * i * 1.15 + i * 2.2);
            TestProcedure.schedule(sl, when, () -> {
                sl.playSound(null, x, y, z, SoundEvents.REDSTONE_TORCH_BURNOUT,
                        SoundSource.PLAYERS, v * 0.85f, pitch + 0.55f);
                sl.playSound(null, x, y, z, SoundEvents.CONDUIT_AMBIENT,
                        SoundSource.PLAYERS, v * 0.60f, pitch);
            });
        }
    }

    /** Crépitement court et sec, tiré au hasard autour d'un point : du statique, rien d'autre. */
    private static void staticCrackle(ServerLevel sl, double x, double y, double z,
                                      double spread, int count, float volume) {
        for (int i = 0; i < count; i++) {
            double ox = x + (sl.random.nextDouble() - 0.5) * spread * 2.0;
            double oz = z + (sl.random.nextDouble() - 0.5) * spread * 2.0;
            sl.playSound(null, ox, y, oz, SoundEvents.REDSTONE_TORCH_BURNOUT,
                    SoundSource.PLAYERS, volume, 1.35f + sl.random.nextFloat() * 0.55f);
        }
    }

    /**
     * APPEL DE L'ORAGE (170 ticks) — la signature <b>de zone</b> de la famille.
     *
     * <p>Elle remplace la « double entaille d'écho », qui frappait deux fois au même endroit et
     * ne demandait rien au joueur. Le principe est maintenant un <b>contrat de position</b> :
     * le nuage se noue à huit blocs au-dessus du point d'impact et <b>n'y bouge plus</b>. Tout
     * ce qui reste dessous est mitraillé puis pris dans la décharge ; tout ce qui en sort n'a
     * plus rien à craindre. Les deux camps peuvent jouer la zone, ce qui était impossible avec
     * une entaille instantanée.
     *
     * <p>Découpage des dégâts, pour une base de {@code max(1.2, arme × 0,55)} :
     * <ul>
     *   <li>cinq frappes de mitraille à <b>0,30</b> chacune, du tick 34 au tick 105, qui ne
     *       touchent que ce qui se trouve dans les 2,6 blocs sous le nuage ;</li>
     *   <li>la décharge au tick 140 : <b>2,60</b> au centre, puis <b>0,22</b> répercutés
     *       jusqu'à dix blocs — c'est volontairement peu, l'intérêt de l'onde est de finir
     *       les blessés et de poser des piles, pas de raser une zone ;</li>
     *   <li>aucune pile n'est consommée par l'effet lui-même : il en <b>pose</b>, et ce sont
     *       les autres signatures de foudre qui les encaissent.</li>
     * </ul>
     */
    private static void applyStormcallGameplay(LivingEntity attacker, LivingEntity target,
                                               ServerLevel sl, float weaponDamage) {
        final int attackerId = attacker == null ? -1 : attacker.getId();
        final int now = TestProcedure.currentTick(sl);
        final float base = Math.max(1.2f, weaponDamage * 0.55f);

        // Le nuage est ancré une fois pour toutes, sur la position de la cible à l'impact.
        final double ax = target.getX();
        final double ay = target.getY();
        final double az = target.getZ();

        // --- Le porteur dégage, pour la même raison que sous le marteau. ---
        // « L'animation du nuage qui fait éclair est visée sur le joueur, pas sur le mob. »
        // Elle ne l'est pas : le gameplay est centré ici, sur la cible, et aucun joueur n'est
        // jamais touché. Mais le nuage mesure 3,40 blocs de rayon et flotte à huit de haut,
        // et en mêlée on se tient à deux ou trois blocs de ce qu'on frappe : on est DEDANS.
        // La mitraille tombe jusqu'à 2,60 blocs du centre, donc littéralement à ses pieds.
        // De l'intérieur, un nuage centré sur le mob et un nuage centré sur soi sont la même
        // image. On recule le porteur et l'orage redevient ce qu'il est : quelque chose qui
        // tombe là-bas. Dix blocs suffisent — la jupe du nuage en fait trois et demi.
        clearTheBlastZone(sl, attacker, ax, az, STORM_CLEARANCE);

        // --- Formation : un grondement, puis rien pendant une seconde et demie. ---
        TestProcedure.schedule(sl, now + 6, () -> {
            sl.playSound(null, ax, ay + 8.0, az, SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.85f, 0.60f);
            sl.playSound(null, ax, ay + 8.0, az, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                    SoundSource.PLAYERS, 1.00f, 0.66f);
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, ax, ay + 8.0, az,
                    40, 2.40, 0.55, 2.40, 0.02);
        });

        // --- Le battement d'attente. ---
        // Entre la formation du nuage et la première frappe il y a une seconde et demie de
        // rien du tout : c'est long, et le silence y passe pour un bug. Six battements de
        // plus en plus rapprochés tiennent cet intervalle et annoncent que ça va tomber.
        final int[] BEAT = {10, 18, 25, 30, 34, 37};
        for (int b = 0; b < BEAT.length; b++) {
            final float v = 0.34f + 0.09f * b;
            final float pitch = 0.52f + 0.06f * b;
            TestProcedure.schedule(sl, now + BEAT[b], () -> {
                sl.playSound(null, ax, ay + 7.0, az, SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, v, pitch);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, ax, ay + 7.4, az,
                        10, 2.10, 0.40, 2.10, 0.02);
            });
        }

        // --- Mitraille : cinq frappes, chacune sur un point tiré sous le nuage. ---
        for (int s = 0; s < 5; s++) {
            final int index = s;
            TestProcedure.schedule(sl, now + 34 + index * 18, () -> {
                double rr = 0.55 + 2.05 * ((index * 7 + 3) % 5) / 4.0;
                double aa = index * 2.3998277;                  // angle d'or : jamais aligné
                double hx = ax + Math.cos(aa) * rr;
                double hz = az + Math.sin(aa) * rr;
                // Le trait part du nuage : la source est à huit blocs, pas au sol.
                sl.playSound(null, hx, ay + 8.0, hz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.PLAYERS, 0.55f, 1.35f + 0.05f * index);
                sl.playSound(null, hx, ay, hz, SoundEvents.MACE_SMASH_GROUND,
                        SoundSource.PLAYERS, 0.58f, 1.18f + 0.06f * index);
                staticCrackle(sl, hx, ay + 0.2, hz, 1.60, 2, 0.34f);
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, hx, ay + 0.20, hz,
                        22, 0.35, 0.45, 0.35, 0.16);
                PixelFx.send(sl, ParticleTypes.FLASH, hx, ay + 0.60, hz, 1, 0.0, 0.0, 0.0, 0.0);
                // 2,6 blocs : exactement la dispersion des points de chute à l'écran.
                for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(hx - 2.60, ay - 2.20, hz - 2.60, hx + 2.60, ay + 3.00, hz + 2.60))) {
                    if (!victim.isAlive() || victim instanceof Player) continue;
                    if (attackerId >= 0 && victim.getId() == attackerId) continue;
                    double dx = victim.getX() - hx;
                    double dz = victim.getZ() - hz;
                    if (dx * dx + dz * dz > 2.60 * 2.60) continue;
                    hurtWithCapturedWeapon(sl, attackerId, victim,
                            base * 0.30f * staticMultiplier(sl, victim));
                    addStaticCharge(sl, victim, 1, attackerId);
                    // Marqué par l'orage : il reste visible à travers les murs le temps que
                    // la décharge arrive. C'est une information, pas une punition — le nuage
                    // désigne ses cibles, et le joueur sait qui n'est pas sorti de la zone.
                    victim.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            120, 0, true, false));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            20, 0, true, false));
                }
            });
        }

        // --- Charge : le nuage se contracte, on l'entend. ---
        TestProcedure.schedule(sl, now + 112, () -> {
            sl.playSound(null, ax, ay + 6.0, az, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 1.0f, 0.70f);
            // L'aspiration : le nuage se contracte de 30 % à l'écran, et le son fait pareil.
            // Le chargement du Warden monte pendant presque une seconde et s'arrête pile
            // quand la décharge part — c'est l'inspiration avant le cri.
            sl.playSound(null, ax, ay + 6.0, az, SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 1.10f, 0.74f);
            sl.playSound(null, ax, ay + 5.0, az, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 0.70f, 0.62f);
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, ax, ay + 4.0, az,
                    60, 0.90, 2.20, 0.90, 0.28);
        });
        // Sept crépitements serrés pendant la contraction : l'air ne tient plus.
        for (int c = 0; c < 7; c++) {
            final int when = 116 + c * 3;
            TestProcedure.schedule(sl, now + when, () ->
                    staticCrackle(sl, ax, ay + 3.5, az, 2.40, 2, 0.46f));
        }

        // --- La décharge, et l'onde qui la répercute. ---
        TestProcedure.schedule(sl, now + 140, () -> {
            // Sept traits tombent ensemble : la détonation est pleine, mais plus claire que
            // celle du marteau — c'est de l'électricité qui frappe, pas du métal qui écrase.
            thunderBoom(sl, ax, ay, az, 0.72f);
            sl.playSound(null, ax, ay, az, SoundEvents.TRIDENT_THROW.value(),
                    SoundSource.PLAYERS, 0.90f, 0.62f);
            sl.playSound(null, ax, ay + 4.0, az, SoundEvents.ELDER_GUARDIAN_CURSE,
                    SoundSource.PLAYERS, 0.55f, 1.25f);
            rollingThunder(sl, TestProcedure.currentTick(sl), ax, ay, az, 0.90f);
            // La colonne centrale traverse vraiment ce qui se tient dessous, du sol au nuage.
            addThunderHit(sl, attackerId, sl.getGameTime(),
                    sl.getGameTime() + 4L, ax, az,
                    ay + 3.0, ay + 3.0, 2.60, 4.00, base * 0.55f, false, 40);
            PixelFx.send(sl, ParticleTypes.FLASH, ax, ay + 1.00, az, 1, 0.0, 0.0, 0.0, 0.0);
            PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK, ax, ay + 0.40, az,
                    90, 1.20, 0.90, 1.20, 0.42);

            // Un seul balayage : on classe chaque victime selon sa distance au centre, ce qui
            // évite de frapper deux fois quelqu'un qui serait dans les deux rayons.
            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ax - 10.0, ay - 4.0, az - 10.0, ax + 10.0, ay + 5.0, az + 10.0))) {
                if (!victim.isAlive() || victim instanceof Player) continue;
                if (attackerId >= 0 && victim.getId() == attackerId) continue;
                double dx = victim.getX() - ax;
                double dz = victim.getZ() - az;
                double d2 = dx * dx + dz * dz;
                if (d2 > 10.0 * 10.0) continue;
                boolean core = d2 <= 2.60 * 2.60;
                float power = base * (core ? 2.60f : 0.22f) * staticMultiplier(sl, victim);
                hurtWithCapturedWeapon(sl, attackerId, victim, power);
                addStaticCharge(sl, victim, core ? 2 : 1, attackerId);
                // Au centre on est assommé ; au bord on est seulement secoué. La nausée dit
                // la commotion, la faiblesse dit le bras qui ne répond plus.
                if (core) {
                    victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                            120, 0, true, false));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            70, 2, true, false));
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                            100, 1, true, false));
                    sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS,
                            0.80f, 0.95f);
                } else {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            40, 0, true, false));
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                            60, 0, true, false));
                }
                PixelFx.send(sl, ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                        core ? 30 : 10, 0.35, 0.45, 0.35, core ? 0.26 : 0.12);
            }
        });
    }

    private static void applyWindGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx, float weaponDamage) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        float damage = Math.max(0.0f, weaponDamage);
        if (fx == WeaponVisualEffectId.SANDSTORM_DUNE) {
            applySandstormGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.TEMPEST_EYE) {
            applyTempestGameplay(attacker, target, sl, damage);
        } else if (fx == WeaponVisualEffectId.ZEPHYR_WIND_LANCE) {
            applyZephyrGameplay(attacker, target, sl, damage);
        }
    }

    /**
     * ZÉPHYR (80 ticks) : la cible est soulevée de 0,55 bloc et clouée sur place par la colonne
     * d'air, piquée par trois lances (33 / 37 / 42), entretenue par quatre rafales, puis relâchée.
     */
    private static void applyZephyrGameplay(LivingEntity attacker, LivingEntity target,
                                            ServerLevel sl, float weaponDamage) {
        int scheduleNow = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        int attackerId = attacker == null ? -1 : attacker.getId();
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.5, z = target.getZ();

        applyMovementHold(target, gameNow + 80, false, target.getY() + 0.55);
        sl.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.40f, 0.75f);
        sl.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.35f, 1.35f);

        float impactDamage = weaponDamage * 0.38f;
        float pulseDamage = net.minecraft.util.Mth.clamp(weaponDamage * 0.09f, 0.05f, 0.60f);
        int[] launches = {24, 28, 33};
        int[] impacts = {33, 37, 42};
        for (int spear = 0; spear < launches.length; spear++) {
            final int launchDelay = launches[spear];
            final int impactDelay = impacts[spear];
            final float pitch = 1.22f + spear * 0.08f;
            final float chimePitch = 1.55f + spear * 0.10f;
            TestProcedure.schedule(sl, scheduleNow + launchDelay,
                    () -> sl.playSound(null, x, y, z, SoundEvents.TRIDENT_THROW,
                            SoundSource.PLAYERS, 0.46f, pitch));
            TestProcedure.schedule(sl, scheduleNow + impactDelay, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                if (signatureContact(target, x, y, z, 0.75)) {
                    hurtWithCapturedWeapon(sl, attackerId, target, impactDamage);
                }
                sl.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 0.52f, pitch + 0.08f);
                sl.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.30f, chimePitch);
            });
        }

        for (int delay = 12; delay <= 60; delay += 16) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                if (!target.isAlive() || target.level() != sl) return;
                hurtWithCapturedWeapon(sl, attackerId, target, pulseDamage);
                sl.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP,
                        SoundSource.PLAYERS, 0.28f, 1.30f);
            });
        }

        TestProcedure.schedule(sl, scheduleNow + 80, () -> {
            if (!target.isAlive() || target.level() != sl) return;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, true, true));
            sl.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.60f, 0.95f);
            sl.playSound(null, x, y, z, SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 0.34f, 1.20f);
        });
    }

    /**
     * TEMPÊTE DE SABLE (140 ticks) : <b>vraie tornade</b>, comme l'Œil du cyclone mais plus basse et
     * plus large au sol. Elle happe en continu toute créature du rayon 4,20, les fait tourner en
     * montant de 7,60 blocs sur des hélices toutes différentes (certaines filent vers l'œil), les
     * aveugle et les ralentit, leur inflige des dégâts toutes les douze ticks, puis les projette
     * haut et loin dès qu'elles atteignent le sommet — avec les dégâts de chute par-dessus.
     *
     * <p>T374-SAND-TORNADO : jusqu'ici cette signature ne faisait que poser cécité + lenteur sur une
     * liste figée de victimes dans un rayon de 2,40 ; « les tornades n'emportent toujours pas les
     * mobs » ne concernait donc que celle-ci, le cyclone utilisant déjà l'aspiration zonale.</p>
     */
    private static void applySandstormGameplay(LivingEntity attacker, LivingEntity target,
                                               ServerLevel sl, float weaponDamage) {
        int scheduleNow = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        int attackerId = attacker == null ? -1 : attacker.getId();
        double cx = target.getX(), cy = target.getY() + target.getBbHeight() * 0.5, cz = target.getZ();
        double baseY = target.getY();

        float pulse = net.minecraft.util.Mth.clamp(weaponDamage * 0.07f, 0.04f, 0.45f);
        WindZone zone = new WindZone(sl, gameNow, gameNow + 140, cx, cz, baseY,
                4.20, 7.60, 0.30, pulse, 12, attackerId, 55, 105, true, false, RELEASE_FLING);
        WIND_ZONES.add(zone);
        int grabbed = captureWindZone(zone, sl);
        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§6[Tempête de sable] §f" + grabbed + " créature(s) happée(s) §7— rayon 4,2 · "
                            + "montée 7,6 · cécité · éjection au sommet"), true);
        }

        sl.playSound(null, cx, cy, cz, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.70f, 0.62f);
        for (int delay = 12; delay < 140; delay += 14) {
            TestProcedure.schedule(sl, scheduleNow + delay, () -> sl.playSound(null, cx, cy, cz,
                    SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.22f, 0.55f));
        }
        TestProcedure.schedule(sl, scheduleNow + 40, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 0.30f, 0.80f));
        TestProcedure.schedule(sl, scheduleNow + 96, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.55f, 0.70f));
        TestProcedure.schedule(sl, scheduleNow + 140, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.60f, 0.55f));
    }

    /**
     * ŒIL DU CYCLONE (170 ticks) : la tornade naît sous la cible et happe <b>toute créature vivante
     * du rayon 4,60</b> — pas seulement celle qui vient d'être frappée — puis continue d'aspirer
     * celles qui entrent dans la zone en cours de route. Les victimes tournent autour de l'axe en
     * montant de <b>10,50 blocs</b>, prennent des dégâts toutes les dix ticks, puis sont lâchées tout
     * en haut : la chute libre qui suit (10 blocs) ajoute les dégâts de chute vanilla, et l'éjection
     * horizontale part dans une direction aléatoire.
     */
    private static void applyTempestGameplay(LivingEntity attacker, LivingEntity target,
                                             ServerLevel sl, float weaponDamage) {
        int scheduleNow = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        int attackerId = attacker == null ? -1 : attacker.getId();
        double cx = target.getX(), cy = target.getY() + target.getBbHeight() * 0.5, cz = target.getZ();
        double baseY = target.getY();
        float pulse = net.minecraft.util.Mth.clamp(weaponDamage * 0.08f, 0.05f, 0.60f);

        WindZone zone = new WindZone(sl, gameNow, gameNow + 170, cx, cz, baseY,
                4.60, 10.50, 0.34, pulse, 10, attackerId, 70, 130, false, false, RELEASE_FLING);
        WIND_ZONES.add(zone);
        int grabbed = captureWindZone(zone, sl);
        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b[Œil du cyclone] §f" + grabbed + " créature(s) happée(s) §7— rayon 4,6 · montée 10,5 · "
                            + "éjection aléatoire au sommet"), true);
        }
        if (grabbed <= 0) {
            sl.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.55f, 0.60f);
            return;
        }

        sl.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.90f, 0.55f);
        sl.playSound(null, cx, cy, cz, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.35f, 0.60f);
        for (int delay = 20; delay <= 160; delay += 20) {
            final float pitch = 0.78f + (delay / 20) * 0.05f;
            TestProcedure.schedule(sl, scheduleNow + delay, () -> sl.playSound(null, cx, cy, cz,
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.50f, pitch));
        }
        TestProcedure.schedule(sl, scheduleNow + 90, () -> sl.playSound(null, cx, cy, cz,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.22f, 1.45f));
        TestProcedure.schedule(sl, scheduleNow + 170, () -> {
            // Le lâcher se produit TOUT EN HAUT : souffle grave + choc lointain.
            sl.playSound(null, cx, cy + 10.5, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65f, 0.85f);
            sl.playSound(null, cx, cy + 10.5, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45f, 1.35f);
        });
    }

    /**
     * Aspiration zonale : toute créature vivante (hors joueurs) presente dans le cylindre rejoint
     * l'helice si elle n'y est pas deja, et monte jusqu'au sommet avec les autres. Les degats et
     * l'ejection sont traites par {@link #tickWindHold} et {@link #releaseWindHold}.
     *
     * @return le nombre de creatures happees par cet appel
     */
    private static int captureWindZone(WindZone zone, ServerLevel sl) {
        long now = sl.getGameTime();
        if (now >= zone.expires()) return 0;
        double r = zone.radius();
        double topY = zone.baseY() + zone.rise();
        AABB box = new AABB(zone.cx() - r, zone.baseY() - 2.0, zone.cz() - r,
                zone.cx() + r, topY + 2.5, zone.cz() + r);
        int grabbed = 0;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            // Toute creature vivante est concernee (la cible d'entrainement est un villageois en
            // mode Paisible, donc pas une « Enemy ») : seuls les joueurs sont exclus.
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            if (zone.attackerId() >= 0 && candidate.getId() == zone.attackerId()) continue;
            double dx = candidate.getX() - zone.cx(), dz = candidate.getZ() - zone.cz();
            double d2 = dx * dx + dz * dz;
            if (d2 > r * r) continue;
            if (candidate.getY() > topY + 1.5) continue;
            if (WIND_HOLDS.containsKey(candidate.getId())) continue;
            Long recapture = WIND_RECAPTURE.get(candidate.getId());
            if (recapture != null && now < recapture) continue;
            rememberSignatureAi(candidate);
            // T374-WIND-PRIORITY : le vent prime sur les emprises au sol (seisme, titan). Sans cela,
            // l'emprise rappelait le mob a sa position d'origine a chaque tick et la tornade semblait
            // n'avoir aucun effet.
            MOVEMENT_HOLDS.remove(candidate.getId());
            var rnd = candidate.getRandom();
            double startRadius = Math.max(0.60, Math.min(r, Math.sqrt(d2)));
            // T374-WIND-WEAVE : « bien faire des radius differents pour les mobs, qu'ils
            // s'entremellent en montant ». Un tiers des victimes est aspiré vers l'ŒIL (rayon
            // minuscule au sommet), les autres restent sur la paroi, à des distances variées.
            boolean towardEye = zone.converge() || rnd.nextFloat() < 0.32f;
            // T375-WATER : dans un tourbillon, TOUT finit à la gorge — mais chacun sur son rayon,
            // sinon les mobs se superposent exactement sur l'axe et la spirale ne se lit plus.
            double topRadius = zone.converge() ? 0.18 + rnd.nextDouble() * 0.95
                    : towardEye ? 0.25 + rnd.nextDouble() * 0.55
                    : 1.30 + rnd.nextDouble() * Math.max(0.60, r * 0.55);
            // Vitesse de rotation propre (même sens pour tout le monde), hauteur propre, durée propre.
            double spin = zone.spin() * (0.70 + rnd.nextDouble() * 0.85);
            double climb = zone.rise() * (0.80 + rnd.nextDouble() * 0.28);
            double rise = Math.max(1.20, zone.baseY() + climb - candidate.getY());
            int span = Math.max(1, zone.flightMax() - zone.flightMin() + 1);
            long flight = zone.flightMin() + rnd.nextInt(span);
            WIND_HOLDS.put(candidate.getId(), new WindHold(now + flight, now, zone.cx(), zone.cz(),
                    candidate.getY(), startRadius, topRadius, rise, Math.atan2(dz, dx),
                    spin, zone.pulse(), zone.period(), zone.attackerId(),
                    rnd.nextDouble() * Math.PI * 2.0, zone.release()));
            if (zone.blind()) {
                candidate.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true, true));
                candidate.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 1, false, true, true));
            }
            grabbed++;
        }
        return grabbed;
    }

    /** Une passe d'aspiration par tick de jeu : les mobs qui entrent dans la tornade sont pris. */
    private static void tickWindZones(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now == LAST_WIND_ZONE_TICK) return;
        LAST_WIND_ZONE_TICK = now;
        if (!WIND_RECAPTURE.isEmpty()) WIND_RECAPTURE.values().removeIf(until -> now >= until);
        if (!SURGE_RECAPTURE.isEmpty()) SURGE_RECAPTURE.values().removeIf(until -> now >= until);
        // T375-WATER-HOLE : rebouchage des blocs fantômes arrivés à échéance (voir GhostBlockMask).
        GhostBlockMask.tick(sl);
        if (WIND_ZONES.isEmpty()) return;
        WIND_ZONES.removeIf(zone -> now >= zone.expires() || zone.level() != sl);
        for (WindZone zone : WIND_ZONES) {
            captureWindZone(zone, sl);
        }
    }

    /**
     * Démo hors combat ({@code /dlbvfx}) : applique le vrai gameplay du vent à l'entité vivante la
     * plus proche du point visé, pour pouvoir tester dégâts, emprise et sons sans toucher un mob.
     */
    public static boolean debugWindGameplay(ServerPlayer player, WeaponVisualEffectId fx, Vec3 at) {
        if (player == null || fx == null || at == null) return false;
        if (!(player.level() instanceof ServerLevel sl)) return false;
        if (fx != WeaponVisualEffectId.ZEPHYR_WIND_LANCE
                && fx != WeaponVisualEffectId.SANDSTORM_DUNE
                && fx != WeaponVisualEffectId.TEMPEST_EYE) return false;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - 3.5, at.y - 3.5, at.z - 3.5, at.x + 3.5, at.y + 3.5, at.z + 3.5))) {
            if (candidate == player || !candidate.isAlive()) continue;
            double distance = candidate.distanceToSqr(at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) return false;
        applyWindGameplay(player, best, fx, signatureBaseDamage(player, best));
        return true;
    }

    /**
     * Rotation orbitale + montée + dégâts périodiques, tant que l'emprise dure.
     *
     * <p>T374-WIND-PULL : la position sur l'hélice est garantie par {@code teleportTo}, et la
     * <b>vitesse</b> est posée sur la tangente de l'hélice (delta entre le point de ce tick et celui
     * du tick suivant). C'est le principe des mods de tornade : la victime est à la fois aspirée vers
     * l'axe, entraînée en rotation et poussée vers le haut, avec un mouvement lissé côté client
     * ({@code hurtMarked} synchronise la vitesse) au lieu d'un à-coup par tick.</p>
     */
    private static void tickWindHold(LivingEntity target) {
        WindHold hold = WIND_HOLDS.get(target.getId());
        if (hold == null) return;
        long now = target.level().getGameTime();
        if (!target.isAlive() || now >= hold.expires()) {
            releaseWindHold(target, hold);
            return;
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        target.stopUsingItem();
        double elapsed = now - hold.started();
        double span = Math.max(1.0, (double) (hold.expires() - hold.started()));
        double u = Math.max(0.0, Math.min(1.0, elapsed / span));
        double uNext = Math.max(0.0, Math.min(1.0, (elapsed + 1.0) / span));
        // T374-WIND-WEAVE : le rayon et l'altitude ondulent autour de l'hélice, avec une phase
        // propre à chaque mob — sans cela tous montaient sur EXACTEMENT le même patron, ce qui se
        // remarquait immédiatement. Ici ils se croisent, se doublent et s'entremêlent.
        double rNow = Math.max(0.12, hold.r0() + (hold.r1() - hold.r0()) * u
                + 0.34 * Math.sin(elapsed * 0.17 + hold.wobble()));
        double rNext = Math.max(0.12, hold.r0() + (hold.r1() - hold.r0()) * uNext
                + 0.34 * Math.sin((elapsed + 1.0) * 0.17 + hold.wobble()));
        double aNow = hold.phase() + elapsed * hold.spin();
        double aNext = hold.phase() + (elapsed + 1.0) * hold.spin();
        double yNow = hold.baseY() + hold.rise() * Math.sin(u * Math.PI * 0.5)
                + 0.20 * Math.sin(elapsed * 0.23 + hold.wobble() * 1.7);
        double yNext = hold.baseY() + hold.rise() * Math.sin(uNext * Math.PI * 0.5)
                + 0.20 * Math.sin((elapsed + 1.0) * 0.23 + hold.wobble() * 1.7);
        double xNow = hold.cx() + Math.cos(aNow) * rNow;
        double zNow = hold.cz() + Math.sin(aNow) * rNow;
        double xNext = hold.cx() + Math.cos(aNext) * rNext;
        double zNext = hold.cz() + Math.sin(aNext) * rNext;
        target.teleportTo(xNow, yNow, zNow);
        target.setDeltaMovement(xNext - xNow, Math.max(0.08, yNext - yNow), zNext - zNow);
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
        if (hold.period() > 0 && elapsed > 0 && elapsed % hold.period() == 0
                && hold.pulse() > 0.0f && target.level() instanceof ServerLevel sl) {
            hurtWithCapturedWeapon(sl, hold.attackerId(), target, hold.pulse());
        }
    }

    /**
     * Fin de l'emprise : le mob est lâché au sommet de la tornade et projeté dans une direction
     * <b>aléatoire</b> — pas la tangente de l'orbite — puis retombe : la chute libre vanilla ajoute
     * ses propres dégâts de chute par-dessus ceux de la tornade (souffle final + confusion).
     */
    private static void releaseWindHold(LivingEntity target, WindHold hold) {
        WIND_HOLDS.remove(target.getId());
        refreshSignatureAi(target);
        if (!target.isAlive()) return;
        var random = target.getRandom();
        float flingAngle = random.nextFloat() * (float) (Math.PI * 2.0);
        // « a la fin ils se font projeter haut et loin » : poussée horizontale 2,05-3,40 et une
        // verticale franche, dans une direction tirée au hasard, dès que le mob atteint le sommet.
        // T375-WATER : le maelström, lui, RECRACHE à la verticale — c'est un geyser, pas un souffle.
        boolean geyser = hold.release() == RELEASE_GEYSER;
        float strength = geyser ? 0.30f + random.nextFloat() * 0.55f
                : 2.05f + random.nextFloat() * 1.35f;
        double lift = geyser ? 1.85 + random.nextFloat() * 0.55
                : 1.12 + random.nextFloat() * 0.48;
        target.setDeltaMovement(Math.cos(flingAngle) * strength, lift, Math.sin(flingAngle) * strength);
        WIND_RECAPTURE.put(target.getId(), target.level().getGameTime() + 70);
        // La chute repart de zéro À CET INSTANT : tout ce qui suit est compté par le jeu.
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, true, true));
        // Souffle final : derniers dégâts de tornade AVANT la chute (qui ajoutera les siens).
        if (hold.pulse() > 0.0f && target.level() instanceof ServerLevel sl) {
            hurtWithCapturedWeapon(sl, hold.attackerId(), target, Math.min(4.0f, hold.pulse() * 3.0f));
        }
    }

    private static void applyEarthGameplay(LivingEntity attacker, LivingEntity target,
                                           WeaponVisualEffectId fx, float weaponDamage) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        scheduleEarthSoundtrack(sl, target, fx);
        if (fx == WeaponVisualEffectId.TITAN_GRASP) {
            applyTitanGameplay(attacker, target, sl, weaponDamage);
            return;
        }
        if (fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) {
            applyRallyGameplay(target, sl);
            return;
        }
        if (fx == WeaponVisualEffectId.SEISMIC_TECTONIC) {
            applySeismicToNearbyEnemies(attacker, target, sl, weaponDamage);
            return;
        }
        // TEMPEST est déjà traité par la branche Bois (son élément) : pas de double gameplay.
        if (fx == WeaponVisualEffectId.SANDSTORM_DUNE) {
            applyWindGameplay(attacker, target, fx, weaponDamage);
        }
    }

    /** ZEPHYR/VERDANT = immobilisation sans bloquer les attaques ; THORN = stun complet 3 s. */
    private static void applyWoodGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx) {
        applyWoodGameplay(attacker, target, fx, sizeScaleOf(target), signatureBaseDamage(attacker, target));
    }

    private static void applyWoodGameplay(LivingEntity attacker, LivingEntity target,
                                          WeaponVisualEffectId fx, float visualScale,
                                          float weaponDamage) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        // Les tâches utilisent le tickCount du serveur, mais les emprises sont relâchées par le
        // gameTime du niveau. Mélanger ces deux horloges pouvait laisser Thorn actif très longtemps.
        int scheduleNow = TestProcedure.currentTick(sl);
        long gameNow = sl.getGameTime();
        int attackerId = attacker == null ? -1 : attacker.getId();
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.50f, z = target.getZ();
        if (fx == WeaponVisualEffectId.THORN_MAW_ROOT) {
            // Le centre visuel du nœud est cy + 0,48 * échelle : on y place le centre du corps.
            double liftedY = target.getY() + 0.48 * net.minecraft.util.Mth.clamp(visualScale, 0.30f, 4.0f);
            applyMovementHold(target, gameNow + 60, true, liftedY);
            for (int delay = 7; delay <= 56; delay += 7) {
                TestProcedure.schedule(sl, scheduleNow + delay, () -> {
                    MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
                    if (!target.isAlive() || target.level() != sl || hold == null || !hold.fullStun()) return;
                    float randomPart = 0.0010f + sl.random.nextFloat() * 0.0005f;
                    hurtWithCapturedWeapon(sl, attackerId, target, weaponDamage * randomPart);
                });
            }
            sl.playSound(null, x, y, z, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.72f, 0.72f);
            TestProcedure.schedule(sl, scheduleNow + 20, () -> sl.playSound(null, x, y, z,
                    SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.58f, 0.82f));
            return;
        }
        if (fx == WeaponVisualEffectId.ZEPHYR_WIND_LANCE
                || fx == WeaponVisualEffectId.TEMPEST_EYE) {
            applyWindGameplay(attacker, target, fx, weaponDamage);
            return;
        }
        if (fx == WeaponVisualEffectId.WILDWOOD_AWAKENING) {
            // Futaie : accent sonore seulement. La rotation du Bois gagne une animation,
            // pas une nouvelle emprise — aucun maintien, aucun dégât supplémentaire, donc
            // aucune interférence avec les priorités de MOVEMENT_HOLDS.
            sl.playSound(null, x, y, z, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.68f, 0.64f);
            TestProcedure.schedule(sl, scheduleNow + 26, () -> sl.playSound(null, x, y, z,
                    SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.60f, 0.98f));
            return;
        }
        if (fx != WeaponVisualEffectId.VERDANT_SAP_BLOOM) return;

        long holdEndGame = gameNow + 96;
        int holdEndSchedule = scheduleNow + 96;
        // Verdant dure 96 ticks : ne pas laisser le mob libre pendant la croissance (0..63).
        applyMovementHold(target, holdEndGame, false);
        sl.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE,
                SoundSource.PLAYERS, 0.40f, 0.75f);
        sl.playSound(null, x, y, z, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.72f, 0.82f);
        // Le maintien existe déjà depuis le tick 0 : on programme uniquement sa libération.
        TestProcedure.schedule(sl, scheduleNow + 74, () -> {
            if (!target.isAlive() || target.level() != sl) return;
            hurtWithCapturedWeapon(sl, attackerId, target, weaponDamage * 2.0f);
            sl.playSound(null, x, y, z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.70f, 1.35f);
        });
        TestProcedure.schedule(sl, holdEndSchedule, () -> releaseMovementHold(target, holdEndGame));
    }

    /**
     * Déplace le point d'ancrage d'une emprise déjà posée.
     *
     * <p>{@link #applyMovementHold} conserve délibérément les coordonnées de l'emprise
     * précédente — c'est ce qui empêche une seconde signature de téléporter une victime
     * déjà tenue. Il faut donc une porte séparée pour le seul cas où l'on veut vraiment
     * que le point bouge : une proie qu'on <b>emporte</b>.</p>
     *
     * <p>Le tick d'emprise téléporte la victime sur l'ancre à chaque image ; déplacer
     * l'ancre d'un peu chaque tick produit donc un vrai glissement, et non un saut.</p>
     */
    private static void dragMovementHold(LivingEntity target, double x, double z) {
        MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
        if (hold == null) return;
        MOVEMENT_HOLDS.put(target.getId(), new MovementHold(hold.expires(), x, hold.y(), z,
                hold.previousNoAi(), hold.fullStun(), hold.releaseY(),
                hold.started(), hold.burialDepth()));
    }

    private static void applyMovementHold(LivingEntity target, long expires, boolean fullStun) {
        applyMovementHold(target, expires, fullStun, Double.NaN);
    }

    private static void applyMovementHold(LivingEntity target, long expires, boolean fullStun,
                                          double requestedY) {
        applyMovementHold(target, expires, fullStun, requestedY, Double.NaN);
    }

    private static void applyMovementHold(LivingEntity target, long expires, boolean fullStun,
                                          double requestedY, double releaseY) {
        applyMovementHold(target, expires, fullStun, requestedY, releaseY, 0L, 0.0);
    }

    private static void applyMovementHold(LivingEntity target, long expires, boolean fullStun,
                                          double requestedY, double releaseY,
                                          long started, double burialDepth) {
        rememberSignatureAi(target);
        MovementHold previous = MOVEMENT_HOLDS.get(target.getId());
        boolean previousNoAi = previous != null ? previous.previousNoAi()
                : target instanceof Mob mob && mob.isNoAi();
        double x = previous != null ? previous.x() : target.getX();
        double y = Double.isNaN(requestedY)
                ? (previous != null ? previous.y() : target.getY()) : requestedY;
        double z = previous != null ? previous.z() : target.getZ();
        MOVEMENT_HOLDS.put(target.getId(), new MovementHold(expires, x, y, z, previousNoAi,
                fullStun, releaseY, started, burialDepth));
        if (fullStun) {
            if (target instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
            target.stopUsingItem();
        } else {
            // Si Thorn etait deja actif, une emprise non-stun ne doit pas conserver son NoAi.
            refreshSignatureAi(target);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.teleportTo(x, y, z);
        target.fallDistance = 0.0f;
    }

    private static void rememberSignatureAi(LivingEntity target) {
        if (target instanceof Mob mob) {
            SIGNATURE_BASELINE_NO_AI.computeIfAbsent(target.getId(), ignored -> mob.isNoAi());
        }
    }

    /** Une emprise qui se termine ne doit jamais réveiller un mob encore tenu par une autre. */
    private static void refreshSignatureAi(LivingEntity target) {
        if (!(target instanceof Mob mob)) return;
        int id = target.getId();
        MovementHold wood = MOVEMENT_HOLDS.get(id);
        boolean soul = SOUL_HOLDS.containsKey(id);
        boolean wind = WIND_HOLDS.containsKey(id);
        // T375-WATER-MOTION : le surf du raz-de-marée compte lui aussi comme une IA suspendue,
        // sinon un autre effet qui se termine rend le contrôle au mob en plein transport et on le
        // voit se retourner pour marcher contre la vague.
        boolean surf = SURGE_RIDES.containsKey(id);
        // T396-VOID-GRIP : l'emprise du Vide compte comme une IA suspendue, au meme titre
        // que l'ame et la tornade ; sans cela, la fin d'une autre prise rendrait le controle
        // a un mob encore tenu au fond du puits d'horizon.
        boolean voidHold = isVoidStunned(mob);
        if (wood == null && !soul && !wind && !surf && !voidHold) {
            Boolean baseline = SIGNATURE_BASELINE_NO_AI.remove(id);
            if (baseline != null) mob.setNoAi(baseline);
            return;
        }
        boolean baseline = SIGNATURE_BASELINE_NO_AI.getOrDefault(id, mob.isNoAi());
        boolean fullyDisabled = soul || wind || surf || voidHold
                || (wood != null && wood.fullStun());
        mob.setNoAi(baseline || fullyDisabled);
    }

    private static void releaseMovementHold(LivingEntity target, long expectedExpiry) {
        MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
        if (hold == null || hold.expires() != expectedExpiry) return;
        MOVEMENT_HOLDS.remove(target.getId());
        refreshSignatureAi(target);
        // Seismic restitue son point d'origine ; les autres emprises conservent leur comportement.
        if (!Double.isNaN(hold.releaseY())) target.teleportTo(hold.x(), hold.releaseY(), hold.z());
        // La gravité vanilla reprend immédiatement ; aucune vélocité résiduelle ne maintient le mob.
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0f;
        target.hurtMarked = true;
    }

    private static boolean isSignatureFullyStunned(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        long now = living.level().getGameTime();
        MovementHold wood = MOVEMENT_HOLDS.get(living.getId());
        SoulHold soul = SOUL_HOLDS.get(living.getId());
        return (wood != null && wood.fullStun() && now < wood.expires())
                || (soul != null && now < soul.expires())
                || isVoidStunned(living);
    }

    @SubscribeEvent
    public static void onSignatureHoldTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target.level().isClientSide()) return;
        tickSignaturePoison(target);
        tickSoulBurn(target);
        tickSoulHold(target);
        tickWindZones(target.level());
        tickShockZones(target.level());
        tickThunderHits(target.level());
        tickWindHold(target);
        tickSurgeRide(target);
        tickEclipseCage(target);
        tickBleed(target);
        tickBloodPools(target.level());
        tickVoidMark(target);
        tickVoidGrip(target);
        tickChaosDiscord(target);
        sweepVoidTables(target.level());
        sweepChaosTables(target.level());
        if (SURGE_RIDES.containsKey(target.getId())) return;
        MovementHold hold = MOVEMENT_HOLDS.get(target.getId());
        if (hold == null) return;
        if (WIND_HOLDS.containsKey(target.getId())) {
            // T374-WIND-PRIORITY : une tornade tient la cible ; aucune emprise au sol ne doit la
            // rappeler à sa position d'origine (c'était la cause du « rien ne se passe »).
            MOVEMENT_HOLDS.remove(target.getId());
            refreshSignatureAi(target);
            return;
        }
        long now = target.level().getGameTime();
        if (!target.isAlive() || now >= hold.expires()) {
            releaseMovementHold(target, hold.expires());
            return;
        }
        if (hold.fullStun()) {
            if (target instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
            target.stopUsingItem();
        }
        target.setDeltaMovement(Vec3.ZERO);
        double heldY = hold.y();
        if (hold.burialDepth() > 0.0) {
            // Même smoothstep que la main : prise jusqu'à 60, descente de trois blocs jusqu'à 112.
            heldY = hold.releaseY() - hold.burialDepth()
                    * smoothTick(now, hold.started() + 60, hold.started() + 112);
        } else if (hold.burialDepth() < 0.0) {
            // Rally : trente blocs ascendants en vingt ticks, puis chute naturelle après libération.
            heldY = hold.y() - hold.burialDepth()
                    * smoothTick(now, hold.started(), hold.started() + 20);
        }
        target.teleportTo(hold.x(), heldY, hold.z());
        target.fallDistance = 0.0f;
    }

    private static void tickSoulHold(LivingEntity target) {
        SoulHold hold = SOUL_HOLDS.get(target.getId());
        if (hold == null) return;
        long now = target.level().getGameTime();
        if (!target.isAlive() || now >= hold.expires()) {
            releaseSoulHold(target, hold.expires());
            return;
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        target.stopUsingItem();
        if (now >= hold.riseEnd() && target.level() instanceof ServerLevel sl) {
            SoulBurn current = SOUL_BURNS.get(target.getId());
            if (current == null || current.expires() < hold.expires() || current.period() != 6) {
                SOUL_BURNS.put(target.getId(), new SoulBurn(hold.expires(), now,
                        hold.attackerId(), hold.weaponDamage(), 6));
            }
        }
        double y;
        if (now < hold.riseStart()) {
            y = hold.baseY();
        } else if (now < hold.riseEnd()) {
            y = net.minecraft.util.Mth.lerp(smoothTick(now, hold.riseStart(), hold.riseEnd()),
                    hold.baseY(), hold.targetY());
        } else if (now < hold.descendStart()) {
            y = hold.targetY();
        } else {
            y = net.minecraft.util.Mth.lerp(smoothTick(now, hold.descendStart(), hold.expires()),
                    hold.targetY(), hold.baseY());
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.teleportTo(hold.x(), y, hold.z());
        target.fallDistance = 0.0f;
    }

    private static float smoothTick(long now, long start, long end) {
        float x = net.minecraft.util.Mth.clamp((now - start) / (float) Math.max(1L, end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWoodHeldAttack(AttackEntityEvent event) {
        if (isSignatureFullyStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWoodHeldDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isSignatureFullyStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    private static String animationFamily(ItemStack weapon) {
        var tag = deepluckyblock.util.DeepNbt.tag(weapon);
        if (tag == null) return null;
        // T374-ANIM-FAMILIES : noms anglais canoniques (fire / wind / earth / metal / light /
        // poison / nature) ; les anciennes valeurs (feu / vent / terre / gaia) sont normalisées.
        return deepluckyblock.util.AnimFamily.canonical(tag.getString("DlbAnimFamily"));
    }

    /**
     * Épées /anim : un seul jet par coup. 0..59 = slashes aléatoires ; 60..99 = grosse
     * signature réelle. Seule une grosse animation fait avancer le compteur NBT de l'épée :
     * les trois signatures tournent donc dans l'ordre, sans reroll et sans répétition aléatoire.
     */
    private static void handleAnimMelee(ServerLevel sl, LivingIncomingDamageEvent event,
                                        LivingEntity attacker, LivingEntity target,
                                        ItemStack weapon, String family) {
        float capturedWeaponDamage = Math.max(0.0f, event.getAmount());
        int roll = sl.random.nextInt(100);
        float ax = (float) attacker.getX();
        float ay = (float) (attacker.getY() + attacker.getBbHeight() * 0.62f);
        float az = (float) attacker.getZ();
        float tx = (float) target.getX();
        float ty = (float) (target.getY() + target.getBbHeight() * 0.50f);
        float tz = (float) target.getZ();
        WeaponVisualEffectId[] pool = deepluckyblock.util.AnimFamily.FIRE.equals(family) ? ANIM_FIRE
                : deepluckyblock.util.AnimFamily.POISON.equals(family) ? ANIM_POISON
                : deepluckyblock.util.AnimFamily.EARTH.equals(family) ? ANIM_EARTH
                : deepluckyblock.util.AnimFamily.METAL.equals(family) ? ANIM_METAL
                : deepluckyblock.util.AnimFamily.LIGHT.equals(family) ? ANIM_LIGHT
                : deepluckyblock.util.AnimFamily.WATER.equals(family) ? ANIM_WATER
                : deepluckyblock.util.AnimFamily.THUNDER.equals(family) ? ANIM_THUNDER
                : deepluckyblock.util.AnimFamily.SHADOW.equals(family) ? ANIM_SHADOW
                : deepluckyblock.util.AnimFamily.BLOOD.equals(family) ? ANIM_BLOOD
                : deepluckyblock.util.AnimFamily.VOID.equals(family) ? ANIM_VOID
                : deepluckyblock.util.AnimFamily.CHAOS.equals(family) ? ANIM_CHAOS
                : deepluckyblock.util.AnimFamily.WIND.equals(family) ? ANIM_WIND : ANIM_WOOD;

        if (roll >= 60) {
            var tag = deepluckyblock.util.DeepNbt.getOrCreateTag(weapon);
            int sequence = Math.floorMod(tag.getInt("DlbAnimNext"), pool.length);
            tag.putInt("DlbAnimNext", (sequence + 1) % pool.length);
            deepluckyblock.util.DeepNbt.commit(weapon, tag);
            WeaponVisualEffectId fx = pool[sequence];
            float measuredScale = sizeScaleOf(target);
            float bigScale = bigMeleeVisualScale(measuredScale);
            event.setAmount(event.getAmount() * (1.0f + bigMeleeBonusPercent(measuredScale) / 100.0f));
            broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, 6,
                    tx, ty, tz, ax, ay, az, 1 << fx.element.ordinal(), lightVisualScaleOf(fx, target, bigScale));
            applyWoodGameplay(attacker, target, fx, bigScale, capturedWeaponDamage);
            applyEarthGameplay(attacker, target, fx, capturedWeaponDamage);
            applyMetalGameplay(attacker, target, fx, capturedWeaponDamage);
            applyLightGameplay(attacker, target, fx, capturedWeaponDamage, bigScale);
            applyFireGameplay(attacker, target, fx, bigScale, capturedWeaponDamage);
            applyPoisonGameplay(attacker, target, fx);
            applyWaterGameplay(attacker, target, fx, capturedWeaponDamage);
            applyThunderGameplay(attacker, target, fx, capturedWeaponDamage);
            applyShadowGameplay(attacker, target, fx, capturedWeaponDamage);
            applyBloodGameplay(attacker, target, fx, capturedWeaponDamage);
            applyVoidGameplay(attacker, target, fx, capturedWeaponDamage);
            applyChaosGameplay(attacker, target, fx, capturedWeaponDamage);
            return;
        }

        // L'arc originel unique est produit au swing par le client, y compris dans le vide.
        // Ne pas en renvoyer ici au contact : cela créait une seconde copie dans l'espace.
        event.setAmount(event.getAmount() * MELEE_SLASH_DAMAGE_MULTIPLIER);
    }

    /**
     * Mêlée directe éligible : exactement UN jet serveur par événement de dégâts accepté.
     * 77 % normal / 20 % slash +5 % / 3 % grande animation +5..15 % selon la taille mesurée.
     * Aucun appel aux anciens tirages de couches, accents, critiques ou combos dans cette branche.
     */
    private static void handleEligibleMelee(ServerLevel sl, LivingIncomingDamageEvent event,
                                            LivingEntity attacker, LivingEntity target, ItemStack weapon,
                                            List<WeaponVisualEffectId.ResolvedLayer> layers) {
        float capturedWeaponDamage = Math.max(0.0f, event.getAmount());
        int roll = sl.random.nextInt(100);
        if (roll <= MELEE_NORMAL_MAX_ROLL) return;

        WeaponVisualEffectId.ResolvedLayer layer = layers.get(0);
        WeaponVisualEffectId fx = layer.effect();
        int palette = paletteOf(weapon, fx);
        float tx = (float) target.getX();
        float ty = (float) (target.getY() + target.getBbHeight() * 0.5f);
        float tz = (float) target.getZ();
        float ax = (float) attacker.getX();
        float ay = (float) (attacker.getY() + attacker.getBbHeight() * 0.62f);
        float az = (float) attacker.getZ();

        if (roll <= MELEE_SLASH_MAX_ROLL) {
            event.setAmount(event.getAmount() * MELEE_SLASH_DAMAGE_MULTIPLIER);
            // Le visuel est déclenché au swing par le geste originel du joueur, pas par ce
            // événement de dégâts : il reste donc identique sur un mob et dans le vide.
            return;
        }

        float measuredScale = sizeScaleOf(target);
        int bonusPercent = bigMeleeBonusPercent(measuredScale);
        event.setAmount(event.getAmount() * (1.0f + bonusPercent / 100.0f));
        float bigScale = bigMeleeVisualScale(measuredScale);
        if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
            // T379-BLOOD : le siphon passait par le rayon generique KIND_BEAM_SIPHON, qui ne
            // sait tracer qu'un trait entre deux points. Sa choregraphie propre — crochets,
            // conduit helicoidal, six bols, rupture — vit dans BloodSignatureVfx, et celle-ci
            // n'est atteinte que par KIND_IMPACT. On y bascule, et on applique le gameplay.
            broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, layer.level(),
                    tx, ty, tz, ax, ay, az, palette, lightVisualScaleOf(fx, target, bigScale));
            applyBloodGameplay(attacker, target, fx, capturedWeaponDamage);
        } else if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE && attacker.distanceToSqr(target) > 9.0) {
            broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                    ax, (float) attacker.getEyeY() - 0.12f, az, tx, ty, tz, palette, lightVisualScaleOf(fx, target, bigScale));
            applyLightGameplay(attacker, target, fx, capturedWeaponDamage, bigScale);
        } else {
            broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, layer.level(),
                    tx, ty, tz, ax, ay, az, palette, lightVisualScaleOf(fx, target, bigScale));
            applyWoodGameplay(attacker, target, fx, bigScale, capturedWeaponDamage);
            applyEarthGameplay(attacker, target, fx, capturedWeaponDamage);
            applyMetalGameplay(attacker, target, fx, capturedWeaponDamage);
            applyLightGameplay(attacker, target, fx, capturedWeaponDamage, bigScale);
            applyFireGameplay(attacker, target, fx, bigScale, capturedWeaponDamage);
            applyPoisonGameplay(attacker, target, fx);
            applyBloodGameplay(attacker, target, fx, capturedWeaponDamage);
            applyVoidGameplay(attacker, target, fx, capturedWeaponDamage);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtVisuals(LivingIncomingDamageEvent event) {
        // Note : on autorise event.getAmount() == 0.0f pour blood_transfusion (#11), curse_of_mercy et toy !
        if (event.isCanceled()) return;
        LivingEntity target = event.getEntity();
        if (INTERNAL_SIGNATURE_DAMAGE.contains(target.getId())) return;
        if (!(target.level() instanceof ServerLevel sl)) return;

        DamageSource src = event.getSource();
        Entity attackerEntity = src.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return;
        if (attacker != target) triggerMiasmaDefense(sl, target, attackerEntity);

        long now = sl.getGameTime();
        Entity direct = src.getDirectEntity();
        boolean isProjectileHit = direct instanceof Projectile;

        // En mêlée pure, évite le spam i-frame ; à distance, autorise les salves multi-flèches (jusqu'à 4 impacts/tick)
        if (!isProjectileHit) {
            Long prev = LAST_ATTACKER_HIT_TICK.put(attacker.getId(), now);
            if (prev != null && now - prev < 2L) return;
        } else {
            Long prevProj = LAST_PROJECTILE_HIT_TICK.put(direct.getId(), now);
            if (prevProj != null && now == prevProj) return;
        }

        if (LAST_ATTACKER_HIT_TICK.size() > 512) {
            LAST_ATTACKER_HIT_TICK.clear();
            LAST_PROJECTILE_HIT_TICK.clear();
            LAST_CRIT_TICK.clear();
            LAST_RALLY_TICK.clear();
            LAST_BIG_ANIM_TICK.clear();
            LAST_ELEMENT_COMBO_TICK.clear();
        }

        ItemStack weapon = resolveImpactWeapon(attacker, direct);
        String animFamily = animationFamily(weapon);
        if (animFamily != null && !isProjectileHit && direct == attacker) {
            handleAnimMelee(sl, event, attacker, target, weapon, animFamily);
            return;
        }
        List<WeaponVisualEffectId.ResolvedLayer> layers = resolveAttackerLayers(attacker, weapon, 2);
        if (layers.isEmpty()) return;

        // playerAttack/mobAttack direct uniquement : les projectiles et dégâts différés ne rerollent pas.
        if (!isProjectileHit && direct == attacker) {
            handleEligibleMelee(sl, event, attacker, target, weapon, layers);
            return;
        }

        float tx = (float) target.getX();
        float ty = (float) (target.getY() + target.getBbHeight() * 0.5);
        float tz = (float) target.getZ();
        float ax = (float) attacker.getX();
        float ay = (float) (attacker.getY() + attacker.getBbHeight() * 0.62);
        float az = (float) attacker.getZ();

        boolean meleeCritJustFired = (now - LAST_CRIT_TICK.getOrDefault(attacker.getId(), -100L)) <= 1L;
        boolean arrowCrit = (direct instanceof AbstractArrow arr) && arr.isCritArrow();
        // T351-ELEMENT-BLEND : palette élémentaire de l'arme (feu + eau -> les deux teintes ensemble).
        int palette = paletteOf(weapon, layers.get(0).effect());

        // Critique à l'arc / arbalète : étoile critique pixel 3D dédiée si pas déjà en couche 0
        if (arrowCrit && layers.get(0).effect().category != WeaponVisualEffectId.Category.CRIT
                && sl.random.nextInt(100) < CRIT_TRIGGER_PERCENT) {
            if (!playSubtleAccent(sl, weapon, WeaponVisualEffectId.GOLDEN_CRIT_STAR, tx, ty, tz, ax, ay, az)) {
                broadcastNear(sl, tx, ty, tz, WeaponVisualEffectId.GOLDEN_CRIT_STAR.id, KIND_IMPACT, 2,
                        tx, ty, tz, ax, ay, az, palette, sizeScaleOf(target));
            }
        }

        // T349-VFX-FREQUENCY : un seul effet par coup (rarement deux) au lieu de toute la pile
        int layerBudget = (sl.random.nextInt(100) < 22) ? 2 : 1;
        // Le critique a-t-il déjà lancé une grosse animation ? Alors le +5 % est appliqué tout de suite.
        boolean bigAnimBonusApplied = (now - LAST_BIG_ANIM_TICK.getOrDefault(attacker.getId(), -100L)) <= 2L;
        // T352-ELEMENT-COMBO : un seul combo élémentaire par coup, même si deux couches partent.
        boolean elementComboDone = false;
        boolean swingSlashPlayed = false;
        if (bigAnimBonusApplied) {
            event.setAmount(event.getAmount() * BIG_ANIM_DAMAGE_BONUS);
        }
        for (int i = 0; i < layers.size(); i++) {
            if (layerBudget <= 0) break;
            WeaponVisualEffectId.ResolvedLayer layer = layers.get(i);
            WeaponVisualEffectId fx = layer.effect();

            // Le slash standard accompagne le coup critique même si le tirage rare de VFX échoue.
            // Le petit/gros effet enchanté reste ensuite décidé une seule fois par shouldPlay.
            if (!isProjectileHit && meleeCritJustFired && !swingSlashPlayed) {
                broadcastNear(sl, ax, ay, az, fx.id, KIND_SWING_SLASH, layer.level(),
                        ax, ay, az, tx, ty, tz, palette);
                swingSlashPlayed = true;
            }
            boolean critHit = arrowCrit || meleeCritJustFired
                    || fx.category == WeaponVisualEffectId.Category.CRIT;
            if (!shouldPlay(attacker.getId(), fx, sl, critHit)) {
                continue;
            }
            // Slash de combat indépendant du tirage « petite ou grande animation » : les attaques
            // sélectionnées gardent leur entaille même lorsque le gros effet est remplacé par un
            // accent discret. Un seul slash par coup, jamais une superposition par couche.
            if (!isProjectileHit && !swingSlashPlayed) {
                broadcastNear(sl, ax, ay, az, fx.id, KIND_SWING_SLASH, layer.level(),
                        ax, ay, az, tx, ty, tz, palette);
                swingSlashPlayed = true;
            }

            layerBudget--;
            // La petite animation reste propre à l'enchantement et ne remplace plus le slash.
            if (playSubtleAccent(sl, weapon, fx, tx, ty, tz, ax, ay, az)) {
                continue;
            }

            // T352-ELEMENT-COMBO : arme multi-éléments -> parfois une animation de CHAQUE élément.
            if (!elementComboDone) {
                elementComboDone = tryElementCombo(sl, attacker.getId(), weapon, tx, ty, tz, ax, ay, az,
                        sizeScaleOf(target), target, Math.max(0.0f, event.getAmount()));
            }

            // T351-BIG-BONUS : les 10 % de grosses animations amplifient de 5 % les dégâts subis
            // (une seule fois par coup, même si deux couches partent).
            if (!bigAnimBonusApplied) {
                bigAnimBonusApplied = true;
                event.setAmount(event.getAmount() * BIG_ANIM_DAMAGE_BONUS);
            }

            if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        tx, ty, tz, ax, ay, az, palette, lightVisualScaleOf(fx, target, sizeScaleOf(target)));
            } else if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE && attacker.distanceToSqr(target) > 9.0) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        ax, (float) attacker.getEyeY() - 0.12f, az, tx, ty, tz, palette, lightVisualScaleOf(fx, target, sizeScaleOf(target)));
                applyLightGameplay(attacker, target, fx, Math.max(0.0f, event.getAmount()), sizeScaleOf(target));
            } else {
                int boost = (arrowCrit && fx.category == WeaponVisualEffectId.Category.CRIT) ? layer.level() + 1 : layer.level();
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, boost,
                        tx, ty, tz, ax, ay, az, palette, lightVisualScaleOf(fx, target, sizeScaleOf(target)));
                applyFireGameplay(attacker, target, fx, sizeScaleOf(target), Math.max(0.0f, event.getAmount()));
                applyWoodGameplay(attacker, target, fx, sizeScaleOf(target), Math.max(0.0f, event.getAmount()));
                applyEarthGameplay(attacker, target, fx, Math.max(0.0f, event.getAmount()));
                applyMetalGameplay(attacker, target, fx, Math.max(0.0f, event.getAmount()));
                applyLightGameplay(attacker, target, fx, Math.max(0.0f, event.getAmount()), sizeScaleOf(target));
                applyPoisonGameplay(attacker, target, fx);
            }
        }
    }

    /** Impact sur bloc (flèche ou trident planté dans le décor) : salve de mini-pixels 3D ! */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileBlockVisuals(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        if (!(proj.level() instanceof ServerLevel sl)) return;
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        if (!(proj.getOwner() instanceof LivingEntity shooter)) return;

        long now = sl.getGameTime();
        Long prev = LAST_PROJECTILE_HIT_TICK.put(proj.getId(), now);
        if (prev != null && now - prev < 2L) return;

        ItemStack weapon = resolveImpactWeapon(shooter, proj);
        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 1);
        if (layers.isEmpty()) return;

        if (!shouldPlay(shooter.getId(), layers.get(0).effect(), sl, false)) return;

        WeaponVisualEffectId.ResolvedLayer top = layers.get(0);
        int palette = paletteOf(weapon, top.effect());
        Vec3 hit = event.getRayTraceResult().getLocation();
        if (top.effect() == WeaponVisualEffectId.ASTRAL_STAR_LANCE) {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_BEAM_SIPHON, top.level(),
                    (float) shooter.getX(), (float) shooter.getEyeY() - 0.12f, (float) shooter.getZ(),
                    (float) hit.x, (float) hit.y, (float) hit.z, palette);
        } else {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_IMPACT, top.level(),
                    (float) hit.x, (float) hit.y, (float) hit.z,
                    (float) shooter.getX(), (float) shooter.getEyeY(), (float) shooter.getZ(), palette);
        }
    }

    /** Clic droit avec une arme portant {@code army_summon} / {@code summon_army} -> Sceau Voxel #29 ! */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickRallyVisuals(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (isSignatureFullyStunned(player)) {
            event.setCanceled(true);
            return;
        }
        if (!(player.level() instanceof ServerLevel sl)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack.getItem())) return;

        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        for (WeaponVisualEffectId.ResolvedLayer l : layers) {
            if (l.effect() == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) {
                long now = sl.getGameTime();
                Long prev = LAST_RALLY_TICK.put(player.getId(), now);
                if (prev != null && now - prev < 40L) return; // T349 : sceau moins spammable
                float px = (float) player.getX();
                float py = (float) (player.getY() + 0.45);
                float pz = (float) player.getZ();
                broadcastNear(sl, px, py, pz, WeaponVisualEffectId.WARLORD_RALLY_SIGIL.id, KIND_IMPACT, l.level(),
                        px, py, pz, px, py + 1.0f, pz);
                scheduleEarthSoundtrack(sl, player, WeaponVisualEffectId.WARLORD_RALLY_SIGIL);
                applyRallyGameplay(player, sl);
                break;
            }
        }
    }

    private static List<WeaponVisualEffectId.ResolvedLayer> resolveAttackerLayers(LivingEntity attacker, ItemStack weapon, int maxLayers) {
        if (!weapon.isEmpty()) {
            return WeaponVisualEffectId.resolveLayers(weapon, maxLayers);
        }
        if (attacker instanceof Player p) {
            return WeaponVisualEffectId.resolveBareHandGauntletLayers(p, maxLayers);
        }
        return List.of();
    }

    private static ItemStack resolveImpactWeapon(LivingEntity attacker, Entity direct) {
        if (direct instanceof ThrownTrident trident) {
            ItemStack origin = trident.getPickupItemStackOrigin();
            if (!origin.isEmpty()) return origin;
        }
        if (direct instanceof AbstractArrow arrow) {
            ItemStack snap = UniversalEnchantmentHandler.resolveArrowWeapon(arrow, attacker);
            if (!snap.isEmpty()) return snap;
        }
        return attacker.getMainHandItem();
    }

    /**
     * T346-VFX-TEST : déclenchement MANUEL d'un effet visuel (utilisé par {@code /dlbvfx}).
     * Emprunte exactement le même paquet S2C que les déclencheurs de jeu réels, donc ce que la
     * commande affiche est rigoureusement identique à ce que le joueur voit en combat.
     */
    public static void playDebug(ServerPlayer player, WeaponVisualEffectId fx, int kind, int intensity,
                                 Vec3 from, Vec3 to) {
        playDebug(player, fx, kind, intensity, from, to, 1.0f);
    }

    /** Intensité et échelle explicites pour les tests visuels, sans second passage artificiel. */
    public static void playDebug(ServerPlayer player, WeaponVisualEffectId fx, int kind, int intensity,
                                 Vec3 from, Vec3 to, float sizeScale) {
        if (player == null || fx == null) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        int lvl = Math.max(1, Math.min(12, intensity));
        float scale = Math.max(0.45f, Math.min(2.60f, sizeScale));
        if (kind == KIND_PROJECTILE_ATTACH) {
            // to.x porte l'identifiant d'entité du projectile à suivre (comme le vrai chemin réseau)
            broadcastNear(sl, (float) from.x, (float) from.y, (float) from.z, fx.id, kind, lvl,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) to.x, 0.0f, 0.0f, 0, scale);
            return;
        }
        // Mêmes conventions que le combat réel : un impact a son ORIGINE sur la cible et sa
        // target sur l'attaquant ; un slash part de l'attaquant et regarde la cible. L'ancien
        // ordre from/to plaçait tous les impacts de démonstration sur le joueur.
        boolean impact = kind == KIND_IMPACT;
        Vec3 origin = impact ? to : from;
        Vec3 target = impact ? from : to;
        Vec3 reference = kind == KIND_SWING_SLASH ? from : to;
        broadcastNear(sl, (float) reference.x, (float) reference.y, (float) reference.z, fx.id, kind, lvl,
                (float) origin.x, (float) origin.y, (float) origin.z,
                (float) target.x, (float) target.y, (float) target.z, 0, scale);
    }

    /** T351 : joue un accent discret précis (test via {@code /dlbvfx subtle}). */
    public static void playSubtleDebug(ServerPlayer player, WeaponVfxSubtle.Subtle accent,
                                       Vec3 from, Vec3 to) {
        if (accent == null) return;
        playSubtleDebug(player, accent.index(), from, to);
    }

    /**
     * T351-SUBTLE-ECHO : joue un accent par son index global ({@code 0..49} élémentaires,
     * {@code 50..99} échos de grosses animations).
     */
    public static void playSubtleDebug(ServerPlayer player, int accentIndex, Vec3 from, Vec3 to) {
        if (player == null) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        broadcastNear(sl, (float) to.x, (float) to.y, (float) to.z,
                0, WeaponVfxSubtle.NET_KIND_BASE + accentIndex, 1,
                (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z);
    }

    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz) {
        broadcastNear(sl, refX, refY, refZ, vfxId, kind, intensity, x, y, z, tx, ty, tz, 0);
    }

    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz, int paletteMask) {
        broadcastNear(sl, refX, refY, refZ, vfxId, kind, intensity, x, y, z, tx, ty, tz, paletteMask, 1.0f);
    }

    /**
     * T359-BIG-ANIM-SCALE : comme ci-dessus, en transmettant l'<b>échelle de taille</b> de la
     * grosse animation. Le client fait alors grossir/rétrécir l'animation ENTIÈRE autour de son
     * point d'impact : un poulet reçoit une animation minuscule, un ravager ou un dragon une
     * animation énorme.
     */
    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz, int paletteMask,
                                      float sizeScale) {
        DeepPayloads.WeaponVfxMessage payload = DeepPayloads.WeaponVfxMessage.create(
                vfxId, kind, intensity, x, y, z, tx, ty, tz, paletteMask, sizeScale);

        double maxDistSq = 52.0 * 52.0;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(refX, refY, refZ) <= maxDistSq) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }
}
