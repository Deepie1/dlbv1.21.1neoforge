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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
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
        INTERNAL_SIGNATURE_DAMAGE.add(target.getId());
        try {
            target.invulnerableTime = 0;
            return target.hurt(source, amount);
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
                    sizeScaleOf(target), livingTarget, estimatedWeaponDamage(attacker));
        }

        // Grosse animation jouée -> le dégât de ce coup sera amplifié de 5 % (voir onLivingHurtVisuals).
        LAST_BIG_ANIM_TICK.put(attacker.getId(), now);
        broadcastNear(sl, cx, cy, cz, fx.id, KIND_IMPACT, Math.max(2, intensity),
                cx, cy, cz, (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ(),
                paletteOf(attacker.getMainHandItem(), fx), lightVisualScaleOf(fx, target, sizeScaleOf(target)));
        if (livingTarget != null) {
            applyFireGameplay(attacker, livingTarget, fx, sizeScaleOf(target));
            applyWoodGameplay(attacker, livingTarget, fx, sizeScaleOf(target), estimatedWeaponDamage(attacker));
            applyEarthGameplay(attacker, livingTarget, fx, estimatedWeaponDamage(attacker));
            applyMetalGameplay(attacker, livingTarget, fx, estimatedWeaponDamage(attacker));
            applyLightGameplay(attacker, livingTarget, fx, estimatedWeaponDamage(attacker), sizeScaleOf(target));
            applyPoisonGameplay(attacker, livingTarget, fx);
            applyWaterGameplay(attacker, livingTarget, fx, estimatedWeaponDamage(attacker));
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
                estimatedWeaponDamage(attacker));
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
        applyFireGameplay(attacker, target, fx, visualScale, estimatedWeaponDamage(attacker));
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
        float plaguePulseDamage = Math.max(0.25f, estimatedWeaponDamage(attacker) * 0.16f);

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
            float pulseDamage = Math.max(0.25f, estimatedWeaponDamage(attacker) * 0.12f);
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
                gameNow + 6, gameNow + 118, 3.4, 2.6);

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
        applyWindGameplay(player, best, fx, estimatedWeaponDamage(player));
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
        applyWoodGameplay(attacker, target, fx, sizeScaleOf(target), estimatedWeaponDamage(attacker));
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
        if (wood == null && !soul && !wind && !surf) {
            Boolean baseline = SIGNATURE_BASELINE_NO_AI.remove(id);
            if (baseline != null) mob.setNoAi(baseline);
            return;
        }
        boolean baseline = SIGNATURE_BASELINE_NO_AI.getOrDefault(id, mob.isNoAi());
        boolean fullyDisabled = soul || wind || surf || (wood != null && wood.fullStun());
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
                || (soul != null && now < soul.expires());
    }

    @SubscribeEvent
    public static void onSignatureHoldTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target.level().isClientSide()) return;
        tickSignaturePoison(target);
        tickSoulBurn(target);
        tickSoulHold(target);
        tickWindZones(target.level());
        tickWindHold(target);
        tickSurgeRide(target);
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
            broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                    tx, ty, tz, ax, ay, az, palette, lightVisualScaleOf(fx, target, bigScale));
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
