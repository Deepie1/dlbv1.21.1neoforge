package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.WeaponVisualEffectId;
import deepluckyblock.enchantments.WeaponVfxMotion;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * T343-VOXEL-3D-ENGINE : Moteur client 100 % "Blocky / Mini-Pixels 3D" pour les 30 effets d'armes :
 * <ul>
 *   <li>Chaque particule, entaille, éclair, goutte de sang, étoile critique, colonne céleste, vortex
 *       et rayon à distance est intégralement composé de <b>vrais mini-cubes voxels 3D à 6 faces</b>
 *       ombrées selon les normales Minecraft (UP 100 %, N/S 84 %, E/W 72 %, DOWN 60 %).</li>
 *   <li>Suit également en temps réel les flèches, carreaux et tridents enchantés pendant tout leur vol
 *       ({@link #KIND_PROJECTILE_ATTACH}) avec un sillage continu de mini-pixels 3D, ainsi que le tir
 *       d'arc/arbalète et les coups de gantelets à mains nues.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class WeaponWorldVfxManager {

    private static final int MAX_ACTIVE_INSTANCES = 140;
    /**
     * T360-NO-VANILLA-PARTICLES : les salves de pixels remplacent désormais les particules
     * vanilla du mod (halos, slashes, traînées d'équipement) : le réservoir doit donc être plus
     * grand, sinon les effets anciens étaient évincés avant même d'avoir été vus.
     */
    private static final int MAX_VOXEL_TRAIL_PIXELS = 1400;

    public static final int KIND_SWING_SLASH       = 0;
    public static final int KIND_IMPACT            = 1;
    public static final int KIND_BEAM_SIPHON       = 2;
    public static final int KIND_PROJECTILE_ATTACH = 3;

    private static final List<VfxInstance> ACTIVE = new ArrayList<>(144);
    private static final List<FloatingPixel> PIXEL_PARTICLES = new ArrayList<>(1400);
    private static final List<PendingEcho> PENDING_ECHOES = new ArrayList<>(16);
    private static final Map<Integer, TrackedProjectile> TRACKED_PROJECTILES = new HashMap<>(32);

    private static final Map<Integer, Boolean> PLAYER_WAS_SWINGING = new HashMap<>(16);
    private static final Map<Integer, Integer> PLAYER_SWING_PARITY = new HashMap<>(16);
    /** T349-VFX-FREQUENCY : cooldown aléatoire de la traînée de coup (par joueur). */
    private static final Map<Integer, Long> PLAYER_SWING_READY = new HashMap<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_DRAWING = new HashMap<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_CB_CHARGED = new HashMap<>(16);

    private static ClientLevel trackedLevel;
    private static long lastTickNanos = System.nanoTime();

    private static RenderType additiveType;
    private static RenderType translucentType;

    /** T349 : temps de frame (secondes) partagé, mis à jour une seule fois par frame. */
    private static float frameTimeSeconds;
    /** T349-PIXEL-VARIATION : échelle douce globale de tous les pixels. */
    private static float framePixelScale = 1.0f;

    /**
     * T350-VFX-VARIATIONS : densité de pixels de la déclinaison en cours de rendu (1.0 = tout).
     * Les pixels de silhouette (alpha élevé) ne sont jamais retirés, seuls les pixels secondaires
     * s'espacent : l'animation reste lisible mais devient beaucoup moins chargée.
     */
    private static float pixelDensity = 1.0f;
    /** T350 : échelle de taille de la déclinaison en cours (0.80 .. 1.0). */
    private static float variantScale = 1.0f;

    // ── T359-BIG-ANIM-SCALE : échelle des grosses animations, pilotée par la taille du mob touché ──
    /** Échelle courante (1 = normale) ; appliquée à TOUTE la géométrie d'une instance. */
    private static float mobScale = 1.0f;
    /** Pivot de mise à l'échelle = origine de l'instance (le point d'impact). */
    private static float mobPivotX, mobPivotY, mobPivotZ;
    /** Vrai seulement pendant le rendu d'une instance à échelle ≠ 1 (aucun coût sinon). */
    private static boolean mobScaleActive = false;

    /**
     * T359-BIG-ANIM-SCALE : applique l'échelle au pivot courant.
     *
     * <p>Le point (x, y, z) est ramené dans le repère du pivot, multiplié par {@link #mobScale},
     * puis replacé dans le monde : l'animation ENTIÈRE grossit ou rétrécit autour de son point
     * d'impact (positions ET tailles), au lieu de simplement changer de taille de pixel.</p>
     */
    private static float scaleAxis(float v, float pivot) {
        return mobScaleActive ? pivot + (v - pivot) * mobScale : v;
    }

    /** T350-SMOOTH-COLOR : index de frame pour ne faire avancer les couleurs qu'une fois par frame. */
    private static int frameIndex;
    /** Couleur courante (r,g,b,dernièreFrame) par effet : transition douce, jamais de saut brutal. */
    private static final java.util.Map<Integer, float[]> SMOOTH_COLORS = new java.util.HashMap<>(40);

    /**
     * T349-IDLE-FILTER : plancher d'alpha. Quand il vaut &gt; 0, tout cube dont l'alpha est inférieur est
     * ignoré. Utilisé par le rendu d'arme tenue pour supprimer les « pixels lumineux constants » qui
     * restaient collés sur l'item, sans toucher aux éclats d'action (alpha élevé).
     */
    private static int alphaFloor = 0;

    public static void setAlphaFloor(int floor) {
        alphaFloor = Math.max(0, Math.min(255, floor));
    }

    public static int getAlphaFloor() {
        return alphaFloor;
    }

    /** T349 : remise à zéro du compteur de cubes (utilisée par CosmeticPixelAura, autre passe de rendu). */
    public static void resetCubeBudget() {
        cubesSinceFlush = 0;
    }

    /**
     * T360-HALO-CONTINU : isole complètement la passe cosmétique du style des VFX d'armes.
     *
     * <p>Le seuil d'alpha ({@code alphaFloor}, mis à 172 par le reflet d'arme) et la densité de
     * pixels (mise à 0.35 par certaines déclinaisons) sont des états <b>globaux</b> du moteur. Si
     * une frame se terminait mal, ils restaient « fermés » et {@code emitVoxelCube} jetait
     * silencieusement TOUS les cubes du halo (alpha 150-250 &lt; 172, ou culling de densité) :
     * le halo disparaissait définitivement. Cette méthode garantit que la couronne est toujours
     * dessinée en pleine densité, quelle que soit la frame précédente.</p>
     */
    public static void beginCosmeticPass() {
        pixelDensity = 1.0f;
        variantScale = 1.0f;
        alphaFloor = 0;
        cubesSinceFlush = 0;
    }

    /** Appelé une fois par frame pour partager le même temps / la même échelle de pixels. */
    public static void beginFrame(float timeSeconds) {
        frameTimeSeconds = timeSeconds;
        frameIndex++;
        framePixelScale = 0.80f + 0.10f * Mth.sin(timeSeconds * 0.53f) + 0.10f * Mth.sin(timeSeconds * 1.27f + 1.7f);
    }

    private record TrackedProjectile(WeaponVisualEffectId effect, int intensity, int paletteMask,
                                     long startTick, long expiresTick) {}

    private static final class FloatingPixel {
        final boolean additive;
        double x, y, z;
        double vx, vy, vz;
        float gravity;
        float halfSize;
        int rgb;
        int age;
        int maxAge;
        int detailSalt;

        FloatingPixel(boolean additive, double x, double y, double z,
                      double vx, double vy, double vz, float gravity,
                      float halfSize, int rgb, int maxAge, int detailSalt) {
            this.additive = additive;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.gravity = gravity;
            this.halfSize = halfSize;
            this.rgb = rgb;
            this.maxAge = Math.max(4, maxAge);
            this.detailSalt = detailSalt;
        }
    }

    private static final class PendingEcho {
        int ticksLeft;
        WeaponVisualEffectId effect;
        int intensity;
        /** T351-ELEMENT-BLEND : palette élémentaire de l'arme au moment du coup. */
        int paletteMask;
        double x, y, z;
        float yaw, pitch, tiltRad;
    }

    private static final class VfxInstance {
        final WeaponVisualEffectId effect;
        final int kind;
        final int intensity;
        final double x, y, z;
        final double tx, ty, tz;
        final float yawDeg, pitchDeg, tiltRad;
        final int maxAge;
        final long seed;
        /** T350-VFX-VARIATIONS : déclinaison tirée au déclenchement (pleine ou allégée). */
        final deepluckyblock.enchantments.WeaponVfxVariant.Entry variantEntry;
        /** T351-ELEMENT-BLEND : masque des familles élémentaires portées par l'arme (0 = inconnu). */
        final int paletteMask;
        /** T359-BIG-ANIM-SCALE : échelle de la grosse animation (taille du mob touché). */
        final float sizeScale;
        int age;

        /** Couleur principale, mélangée sur la palette élémentaire de l'arme si elle en porte plusieurs. */
        int primary() {
            return (paletteMask == 0) ? effect.primaryRgb
                    : paletteColor(paletteMask, effect.primaryRgb, palettePhase(frameTimeSeconds));
        }

        /** Couleur secondaire, décalée d'un demi-cycle pour montrer l'autre bout du mélange. */
        int secondary() {
            return (paletteMask == 0) ? effect.secondaryRgb
                    : paletteColor(paletteMask, effect.secondaryRgb, palettePhase(frameTimeSeconds + 3.1f));
        }

        /** T349-VARIATION : variante 0..7 tirée au déclenchement (forme, sens de rotation, teinte). */
        int variant() {
            return (int) ((seed >>> 17) & 7);
        }

        VfxInstance(WeaponVisualEffectId effect, int kind, int intensity,
                    double x, double y, double z,
                    double tx, double ty, double tz,
                    float yawDeg, float pitchDeg, float tiltRad,
                    int maxAge, long seed) {
            this(effect, kind, intensity, x, y, z, tx, ty, tz, yawDeg, pitchDeg, tiltRad, maxAge, seed, 0);
        }

        VfxInstance(WeaponVisualEffectId effect, int kind, int intensity,
                    double x, double y, double z,
                    double tx, double ty, double tz,
                    float yawDeg, float pitchDeg, float tiltRad,
                    int maxAge, long seed, int paletteMask) {
            this(effect, kind, intensity, x, y, z, tx, ty, tz, yawDeg, pitchDeg, tiltRad,
                    maxAge, seed, paletteMask, 1.0f);
        }

        VfxInstance(WeaponVisualEffectId effect, int kind, int intensity,
                    double x, double y, double z,
                    double tx, double ty, double tz,
                    float yawDeg, float pitchDeg, float tiltRad,
                    int maxAge, long seed, int paletteMask, float sizeScale) {
            this.sizeScale = (sizeScale > 0.0f) ? Math.min(6.0f, Math.max(0.20f, sizeScale)) : 1.0f;
            this.paletteMask = paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL;
            this.effect = effect;
            this.kind = kind;
            this.intensity = Math.max(1, Math.min(12, intensity));
            this.x = x; this.y = y; this.z = z;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.tiltRad = tiltRad;
            this.maxAge = Math.max(4, maxAge);
            this.seed = seed;
            this.variantEntry = deepluckyblock.enchantments.WeaponVfxVariant.pick(effect, seed);
        }
    }

    private WeaponWorldVfxManager() {}

    public static float smoothTimeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null) {
            return (float) ((System.nanoTime() & 0x3FFFFFFFL) * 1.0e-9);
        }
        float subTick = mc.isPaused()
                ? 0.0f
                : Mth.clamp((System.nanoTime() - lastTickNanos) / 50_000_000.0f, 0.0f, 1.0f);
        return ((lvl.getGameTime() & 0xFFFFFL) + subTick) * 0.05f;
    }

    public static RenderType getAdditiveRenderType() {
        if (additiveType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false);
            additiveType = RenderType.create(
                    "dlb_voxel_vfx_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    32768,
                    false,
                    false,
                    state);
        }
        return additiveType;
    }

    public static RenderType getTranslucentRenderType() {
        if (translucentType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false);
            translucentType = RenderType.create(
                    "dlb_voxel_vfx_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    32768,
                    false,
                    true,
                    state);
        }
        return translucentType;
    }

    public static void enqueueNetworkVfx(int vfxId, int kind, int intensity,
                                         float x, float y, float z,
                                         float tx, float ty, float tz) {
        enqueueNetworkVfx(vfxId, kind, intensity, x, y, z, tx, ty, tz, 0);
    }

    public static void enqueueNetworkVfx(int vfxId, int kind, int intensity,
                                         float x, float y, float z,
                                         float tx, float ty, float tz, int paletteMask) {
        enqueueNetworkVfx(vfxId, kind, intensity, x, y, z, tx, ty, tz, paletteMask, 1.0f);
    }

    /**
     * T359-BIG-ANIM-SCALE : variante avec <b>échelle de taille</b> (taille du mob touché).
     * 0.30 = animation minuscule (poulet, poisson), 1.0 = normale (joueur), jusqu'à 4.0 = énorme
     * (ravager, wither, dragon) : la grosse animation s'adapte vraiment à sa cible.
     */
    public static void enqueueNetworkVfx(int vfxId, int kind, int intensity,
                                         float x, float y, float z,
                                         float tx, float ty, float tz, int paletteMask,
                                         float sizeScale) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // T351-SUBTLE : un « kind » >= 100 transporte un accent discret (2..9 mini-pixels).
        if (kind >= deepluckyblock.enchantments.WeaponVfxSubtle.NET_KIND_BASE) {
            spawnSubtle(kind - deepluckyblock.enchantments.WeaponVfxSubtle.NET_KIND_BASE,
                    x, y, z, tx, ty, tz, paletteMask);
            return;
        }

        WeaponVisualEffectId fx = WeaponVisualEffectId.byId(vfxId);
        if (fx == null) return;

        // Attachement en direct d'un sillage de mini-pixels 3D à un projectile en vol !
        if (kind == KIND_PROJECTILE_ATTACH) {
            int projEntityId = Math.round(tx);
            if (projEntityId > 0) {
                long gt = level.getGameTime();
                TRACKED_PROJECTILES.put(projEntityId, new TrackedProjectile(fx, intensity, paletteMask, gt, gt + 140L));
            }
            spawnMiniPixelBurst(fx, x, y, z, 7, 0.032f, 0.14, paletteMask);
            return;
        }

        int duration = switch (fx) {
            case SERAPH_SKY_PILLAR, ABYSSAL_MAELSTROM, WARLORD_RALLY_SIGIL,
                 DRAGON_CHAOS_PRISM, BRIMSTONE_METEOR, BIG_BANG_SCAR -> 22;
            case THORN_MAW_ROOT, SANDSTORM_DUNE, ECLIPSE_VEIL, NULL_RIFT_FISSURE,
                 CHAOS_KALEIDO_LOOP, ENTROPY_SPHERE, VERDANT_SAP_BLOOM, MIASMA_SHROUD,
                 PLAGUE_NOVA -> 18;
            case ASTRAL_STAR_LANCE, VOID_WARP_SPHERE, SEISMIC_TECTONIC,
                 CRIMSON_SIPHON, CHRONO_ETERNAL_HALO, ANNIHILATING_ARC -> 16;
            case CHAIN_VOLT_ARC -> 11;
            default -> 14;
        };

        float dx = tx - x;
        float dz = tz - z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        long seed = Double.doubleToLongBits(x * 31.0 + y * 17.0 + z * 13.0) ^ System.nanoTime();
        pushInstance(new VfxInstance(fx, kind, intensity, x, y, z, tx, ty, tz, yaw, 0.0f, 0.25f,
                duration, seed, paletteMask, sizeScale));

        // T351 : l'entaille est pilotée par le serveur ; l'écho du TWIN_ECHO est donc reproduit ici.
        if (kind == KIND_SWING_SLASH && fx == WeaponVisualEffectId.TWIN_ECHO_SLASH && PENDING_ECHOES.size() < 16) {
            PendingEcho pe = new PendingEcho();
            pe.ticksLeft = 4;
            pe.effect = WeaponVisualEffectId.TWIN_ECHO_SLASH;
            pe.intensity = intensity;
            pe.x = x; pe.y = y; pe.z = z;
            pe.yaw = yaw; pe.pitch = 0.0f; pe.tiltRad = -0.34f;
            pe.paletteMask = paletteMask;
            PENDING_ECHOES.add(pe);
        }

        // Éclaboussure de vrais mini-pixels 3D cubiques animés sous la physique !
        int burstCount = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 14 : 10;
        float cubeHalf = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 0.042f : 0.036f;
        spawnMiniPixelBurst(fx, x, y, z, burstCount, cubeHalf, 0.18, paletteMask);
    }

    public static void spawnMiniPixelBurst(WeaponVisualEffectId fx, double x, double y, double z,
                                           int count, float halfSize, double speed) {
        spawnMiniPixelBurst(fx, x, y, z, count, halfSize, speed, 0);
    }

    /**
     * T351-ELEMENT-BLEND : même salve, mais chaque pixel prend une teinte piochée le long de la
     * palette élémentaire de l'arme (feu + eau = braises chaudes ET givrantes dans le même éclat).
     */
    public static void spawnMiniPixelBurst(WeaponVisualEffectId fx, double x, double y, double z,
                                           int count, float halfSize, double speed, int paletteMask) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var rand = mc.level.random;
        float grav = (fx.category == WeaponVisualEffectId.Category.BLOOD
                || fx == WeaponVisualEffectId.VENOM_BRIAR_FANG
                || fx == WeaponVisualEffectId.SEISMIC_TECTONIC) ? 0.016f
                : (fx == WeaponVisualEffectId.MOLTEN_FIRE_WAVE || fx == WeaponVisualEffectId.SOUL_FIRE_CRESCENT ? -0.005f : 0.004f);

        for (int i = 0; i < count; i++) {
            if (PIXEL_PARTICLES.size() >= MAX_VOXEL_TRAIL_PIXELS) {
                PIXEL_PARTICLES.remove(0);
            }
            double vx = (rand.nextDouble() - 0.5) * speed;
            double vy = (rand.nextDouble() - 0.2) * speed;
            double vz = (rand.nextDouble() - 0.5) * speed;
            int rgb = switch (i % 3) {
                case 0 -> fx.primaryRgb;
                case 1 -> fx.secondaryRgb;
                default -> rotateHue(fx.secondaryRgb, 0.16f);
            };
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                // Répartition des pixels le long de la palette : le mélange est visible d'un seul coup d'œil.
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = mixRgb(rgb, paletteColor(paletteMask, rgb, phase), 0.85f);
            }
            // Tailles multi-échelles : quelques gros pixels, beaucoup de micro-éclats (jamais uniforme)
            float sz = halfSize * (0.42f + rand.nextFloat() * 1.05f);
            PIXEL_PARTICLES.add(new FloatingPixel(fx.additive, x, y, z, vx, vy, vz, grav, sz, rgb,
                    10 + rand.nextInt(10), rand.nextInt(Integer.MAX_VALUE)));
        }
    }

    // ==================================================================================
    // T360-NO-VANILLA-PARTICLES : salve de mini-cubes 3D génériques
    // ==================================================================================

    /** Plafond de cubes créés par un seul paquet (anti-spam réseau + anti-lag). */
    private static final int MAX_PUFF_CUBES_PER_PACKET = 12;

    /**
     * T360-NO-VANILLA-PARTICLES : remplace <b>toute</b> particule Minecraft du mod par de vrais
     * mini-cubes 3D multi-tailles.
     *
     * <p>Appelé soit par {@code PixelPuffClientBridge} (effets serveur : halos, slashes, traînées
     * d'enchantements), soit directement par le code client (aura ETERNAL, nuages du Cloud Walker).
     * Chaque cube reçoit une taille aléatoire (multi-size), une trajectoire propre, une gravité
     * douce et une durée de vie propre : la salve respire au lieu de ressembler à une boule unique.</p>
     *
     * @param rgb    couleur 0xRRGGBB du pixel
     * @param count  nombre de cubes demandés (borné à {@value #MAX_PUFF_CUBES_PER_PACKET})
     * @param spread dispersion initiale (rayon, en blocs)
     * @param speed  vitesse d'éclatement
     * @param maxAge durée de vie maximale (en ticks)
     */
    public static void spawnPixelPuff(int rgb, double x, double y, double z,
                                      int count, float spread, double speed, int maxAge) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RandomSource rand = mc.level.random;
        int n = Mth.clamp(count, 1, MAX_PUFF_CUBES_PER_PACKET);
        float sp = (float) Mth.clamp(spread, 0.0, 4.0);
        double vel = Mth.clamp(speed, 0.0, 2.0);
        int life = Mth.clamp(maxAge, 6, 60);

        for (int i = 0; i < n; i++) {
            if (PIXEL_PARTICLES.size() >= MAX_VOXEL_TRAIL_PIXELS) PIXEL_PARTICLES.remove(0);

            double ox = (rand.nextDouble() - 0.5) * 2.0 * sp;
            double oy = (rand.nextDouble() - 0.5) * 2.0 * sp * 0.8;
            double oz = (rand.nextDouble() - 0.5) * 2.0 * sp;

            double vx = (rand.nextDouble() - 0.5) * 2.0 * vel;
            double vy = (rand.nextDouble() - 0.25) * 1.6 * vel;
            double vz = (rand.nextDouble() - 0.5) * 2.0 * vel;

            // MIROIR MULTI-SIZE : micro-éclats majoritaires, quelques gros pixels bien lisibles.
            float roll = rand.nextFloat();
            float halfSize = (roll < 0.62f) ? (0.022f + rand.nextFloat() * 0.016f)
                    : (roll < 0.92f) ? (0.040f + rand.nextFloat() * 0.020f)
                    : (0.062f + rand.nextFloat() * 0.024f);

            // Teinte légèrement variée autour de la couleur demandée (jamais monochrome).
            int shade = switch (i % 3) {
                case 0 -> rgb;
                case 1 -> shadeRgb(rgb, 1.18f);
                default -> shadeRgb(rgb, 0.82f);
            };

            PIXEL_PARTICLES.add(new FloatingPixel(true,
                    x + ox, y + oy, z + oz,
                    vx, vy, vz, 0.006f,
                    halfSize, shade,
                    life / 2 + rand.nextInt(Math.max(3, life / 2)),
                    rand.nextInt(Integer.MAX_VALUE)));
        }
    }

    /** Variante flottante (utilisée par les paquets : positions déjà en float). */
    public static void spawnPixelPuff(int rgb, float x, float y, float z,
                                      int count, float spread, double speed, int maxAge) {
        spawnPixelPuff(rgb, (double) x, (double) y, (double) z, count, spread, speed, maxAge);
    }

    // ==================================================================================
    // T351-SUBTLE : accents discrets (2..9 mini-pixels, jamais de forme englobante)
    // ==================================================================================

    /**
     * T351-SUBTLE-ECHO : joue le <b>souvenir</b> d'une grosse animation
     * ({@link deepluckyblock.enchantments.WeaponVfxSubtleEcho}) : même géométrie (arc, colonne,
     * orbite, éclair, couronne...), même élément, mais seulement 2 à 6 mini-pixels.
     */
    public static void spawnEcho(int echoIndex, float x, float y, float z,
                                 float towardX, float towardY, float towardZ, int paletteMask) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var rand = mc.level.random;
        var echo = deepluckyblock.enchantments.WeaponVfxSubtleEcho.get(echoIndex);

        float dx = towardX - x, dy = towardY - y, dz = towardZ - z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001f) { dx = 0; dy = 0; dz = 1; len = 1; }
        dx /= len; dy /= len; dz /= len;
        float px = -dz, pz = dx;                     // perpendiculaire horizontale
        float spread = echo.spread();
        int count = Math.max(2, Math.min(6, echo.count()));
        boolean additive = true;

        for (int i = 0; i < count; i++) {
            if (PIXEL_PARTICLES.size() >= MAX_VOXEL_TRAIL_PIXELS) PIXEL_PARTICLES.remove(0);
            float t = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
            float ang = (float) (Math.PI * 2.0 * i / count) + rand.nextFloat() * 0.4f;
            float radial = spread * (0.35f + rand.nextFloat() * 0.8f);
            double ox = 0, oy = 0, oz = 0, vx = 0, vy = 0, vz = 0;
            float speed = echo.speed();

            switch (echo.motion()) {
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_TRAIL -> {
                    // Traînée : les pixels s'égrènent DANS l'axe de la frappe, puis ralentissent.
                    ox = px * (t - 0.5f) * spread;
                    oy = (t - 0.5f) * spread * 0.5f;
                    oz = pz * (t - 0.5f) * spread;
                    vx = dx * speed * (0.6 + t);
                    vy = dy * speed * (0.6 + t) + 0.010;
                    vz = dz * speed * (0.6 + t);
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_ARC -> {
                    // Arc : balayage perpendiculaire, comme une entaille minuscule.
                    ox = px * (t - 0.5f) * spread * 2.2f;
                    oy = (t - 0.5f) * spread * 0.35f;
                    oz = pz * (t - 0.5f) * spread * 2.2f;
                    vx = px * speed * 1.3;
                    vy = 0.016 + rand.nextDouble() * 0.012;
                    vz = pz * speed * 1.3;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_SPIRE -> {
                    // Colonne : montée verticale très serrée.
                    ox = px * radial * 0.35f;
                    oy = t * 0.16f;
                    oz = pz * radial * 0.35f;
                    vx = px * 0.010;
                    vy = 0.030 + t * 0.014;
                    vz = pz * 0.010;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_ORBIT -> {
                    // Orbite : petit cercle horizontal autour du point.
                    ox = Math.cos(ang) * radial;
                    oy = 0.02 + (rand.nextFloat() - 0.5f) * 0.05f;
                    oz = Math.sin(ang) * radial;
                    vx = -Math.sin(ang) * speed * 0.9;
                    vy = 0.009 + rand.nextDouble() * 0.008;
                    vz = Math.cos(ang) * speed * 0.9;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_RING -> {
                    // Anneau : couronne plate qui tourne à peine.
                    ox = Math.cos(ang) * spread;
                    oy = (rand.nextFloat() - 0.5f) * 0.02f;
                    oz = Math.sin(ang) * spread;
                    vx = -Math.sin(ang) * speed * 0.45;
                    vy = 0.006;
                    vz = Math.cos(ang) * speed * 0.45;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_FALL -> {
                    // Chute : arrive d'en haut et tombe (météore / gouttes).
                    ox = (rand.nextFloat() - 0.5f) * spread * 1.4f;
                    oy = 0.16f + rand.nextFloat() * 0.10f;
                    oz = (rand.nextFloat() - 0.5f) * spread * 1.4f;
                    vx = dx * 0.020;
                    vy = -0.024 - rand.nextDouble() * 0.022;
                    vz = dz * 0.020;
                    additive = false;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_SIPHON -> {
                    // Tourbillon : part d'un cercle et revient vers le point d'impact.
                    ox = Math.cos(ang) * radial * 1.5f;
                    oy = 0.03 + Math.abs(Math.sin(ang)) * 0.06f;
                    oz = Math.sin(ang) * radial * 1.5f;
                    vx = -Math.cos(ang) * speed * 1.4;
                    vy = 0.014;
                    vz = -Math.sin(ang) * speed * 1.4;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_BOLT -> {
                    // Éclair : 2-3 pixels en zigzag, très brefs.
                    float zig = ((i & 1) == 0) ? 1.0f : -1.0f;
                    ox = px * zig * spread * (1.0f - t);
                    oy = dy * 0.04f + zig * 0.010f;
                    oz = pz * zig * spread * (1.0f - t);
                    vx = dx * speed * 0.9;
                    vy = dy * speed * 0.9 + 0.008;
                    vz = dz * speed * 0.9;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_HALO -> {
                    // Couronne : anneau au-dessus du point, léger flottement.
                    ox = Math.cos(ang) * spread * 0.9f;
                    oy = 0.13f + Math.sin(ang * 2.0f) * 0.012f;
                    oz = Math.sin(ang) * spread * 0.9f;
                    vx = -Math.sin(ang) * speed * 0.35;
                    vy = 0.012;
                    vz = Math.cos(ang) * speed * 0.35;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_PRISM -> {
                    // Prisme : 3 facettes qui s'écartent en gardant un axe net.
                    ox = px * (t - 0.5f) * spread * 1.6f;
                    oy = (rand.nextFloat() - 0.5f) * spread;
                    oz = pz * (t - 0.5f) * spread * 1.6f;
                    vx = px * speed * (0.5 + t * 0.8);
                    vy = (rand.nextDouble() - 0.5) * speed * 0.5 + 0.008;
                    vz = pz * speed * (0.5 + t * 0.8);
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_WAVE -> {
                    // Vague : front courbe qui déferle vers l'avant.
                    ox = px * (t - 0.5f) * spread * 1.8f;
                    oy = 0.02 + Math.sin(t * Math.PI) * 0.05f;
                    oz = pz * (t - 0.5f) * spread * 1.8f;
                    vx = dx * speed * (0.7 + t * 0.6);
                    vy = 0.020 + Math.sin(t * Math.PI) * 0.012;
                    vz = dz * speed * (0.7 + t * 0.6);
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_CROSS -> {
                    // Croix pixel 3D : 4 directions franches (haut, bas, gauche, droite).
                    float sx = ((i & 1) == 0) ? 1.0f : -1.0f;
                    float sy = ((i & 2) == 0) ? 1.0f : -1.0f;
                    ox = px * sx * spread;
                    oy = sy * spread * 0.55f;
                    oz = pz * sx * spread;
                    vx = px * sx * speed * 0.8;
                    vy = sy * speed * 0.45 + 0.010;
                    vz = pz * sx * speed * 0.8;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_STAR -> {
                    // Étoile : rayonnement 3D court et vif (signature des critiques).
                    float elev = (i % 2 == 0) ? 1.0f : -0.5f;
                    ox = Math.cos(ang) * radial * 0.7f;
                    oy = elev * 0.035f;
                    oz = Math.sin(ang) * radial * 0.7f;
                    vx = Math.cos(ang) * speed * 1.2;
                    vy = elev * speed * 0.7 + 0.012;
                    vz = Math.sin(ang) * speed * 1.2;
                }
                default -> { // MOTION_BLOOM
                    // Éclosion : petit dôme qui s'ouvre au point d'impact.
                    ox = Math.cos(ang) * radial;
                    oy = -0.02f - Math.abs(Math.sin(ang)) * 0.02f;
                    oz = Math.sin(ang) * radial;
                    vx = Math.cos(ang) * speed * 1.1;
                    vy = 0.016 + Math.abs(Math.sin(ang)) * 0.010;
                    vz = Math.sin(ang) * speed * 1.1;
                    additive = false;
                }
            }

            int rgb = switch (i % 3) {
                case 0 -> echo.element().primary;
                case 1 -> echo.element().highlight;
                default -> rotateHue(echo.element().secondary, 0.05f);
            };
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                rgb = mixRgb(rgb, paletteColor(paletteMask, rgb, t), 0.85f);
            }
            // Les mouvements « lumineux » restent bien lisibles même minuscules.
            if (echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_STAR
                    || echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_PRISM
                    || echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_HALO) {
                rgb = mixRgb(rgb, 0xFFFFFF, 0.35f);
            }

            float sz = echo.size() * (0.7f + rand.nextFloat() * 0.6f);
            PIXEL_PARTICLES.add(new FloatingPixel(additive,
                    x + ox, y + oy, z + oz, vx, vy, vz, echo.gravity(), sz, rgb,
                    echo.life() + rand.nextInt(3), rand.nextInt(Integer.MAX_VALUE)));
        }
    }

    /**
     * Joue un accent subtil de {@link deepluckyblock.enchantments.WeaponVfxSubtle}.
     * Aucune forme qui entoure la cible : quelques pixels seulement, avec la physique adaptée
     * à la forme (braise qui monte, goutte qui tombe, poussière qui s'étale...).
     */
    public static void spawnSubtle(int subtleIndex, float x, float y, float z,
                                   float towardX, float towardY, float towardZ) {
        spawnSubtle(subtleIndex, x, y, z, towardX, towardY, towardZ, 0);
    }

    public static void spawnSubtle(int subtleIndex, float x, float y, float z,
                                   float towardX, float towardY, float towardZ, int paletteMask) {
        // T351-SUBTLE-ECHO : les index 50..99 sont les échos dérivés des grosses animations.
        int base = deepluckyblock.enchantments.WeaponVfxSubtle.COUNT;
        if (subtleIndex >= base) {
            spawnEcho(subtleIndex - base, x, y, z, towardX, towardY, towardZ, paletteMask);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var rand = mc.level.random;
        var sub = deepluckyblock.enchantments.WeaponVfxSubtle.get(subtleIndex);

        float dx = towardX - x, dy = towardY - y, dz = towardZ - z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001f) { dx = 0; dy = 0; dz = 1; len = 1; }
        dx /= len; dy /= len; dz /= len;
        // Perpendiculaire horizontale pour les effets latéraux
        float px = -dz, pz = dx;

        boolean additive = true;
        int count = Math.min(9, Math.max(2, sub.count()));

        for (int i = 0; i < count; i++) {
            if (PIXEL_PARTICLES.size() >= MAX_VOXEL_TRAIL_PIXELS) PIXEL_PARTICLES.remove(0);
            double vx, vy, vz;
            float ox = 0f, oy = 0f, oz = 0f;
            float speed = sub.speed();
            switch (sub.shape()) {
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_SPARK -> {
                    vx = dx * speed * (1.2 + rand.nextDouble());
                    vy = dy * speed * (0.6 + rand.nextDouble() * 0.8) + 0.02;
                    vz = dz * speed * (1.2 + rand.nextDouble());
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_EMBER -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.20f;
                    oz = (rand.nextFloat() - 0.5f) * 0.20f;
                    vx = (rand.nextDouble() - 0.5) * 0.015;
                    vy = 0.028 + rand.nextDouble() * 0.030;
                    vz = (rand.nextDouble() - 0.5) * 0.015;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_DROPLET -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.16f;
                    oy = 0.05f + rand.nextFloat() * 0.10f;
                    vx = dx * 0.02 + (rand.nextDouble() - 0.5) * 0.02;
                    vy = -0.02 - rand.nextDouble() * 0.03;
                    vz = dz * 0.02 + (rand.nextDouble() - 0.5) * 0.02;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_MIST -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.26f;
                    oy = (rand.nextFloat() - 0.5f) * 0.10f;
                    oz = (rand.nextFloat() - 0.5f) * 0.26f;
                    vx = ox * 0.06;
                    vy = 0.008 + rand.nextDouble() * 0.010;
                    vz = oz * 0.06;
                    additive = false;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_LEAF -> {
                    ox = px * (rand.nextFloat() - 0.5f) * 0.24f;
                    oz = pz * (rand.nextFloat() - 0.5f) * 0.24f;
                    vx = px * 0.030 + (rand.nextDouble() - 0.5) * 0.01;
                    vy = 0.020 + rand.nextDouble() * 0.02;
                    vz = pz * 0.030 + (rand.nextDouble() - 0.5) * 0.01;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_SHARD -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.18f;
                    oy = (rand.nextFloat() - 0.5f) * 0.18f;
                    oz = (rand.nextFloat() - 0.5f) * 0.18f;
                    vx = (rand.nextDouble() - 0.5) * 0.05;
                    vy = (rand.nextDouble() - 0.5) * 0.05;
                    vz = (rand.nextDouble() - 0.5) * 0.05;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_DUST -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.22f;
                    oy = -0.10f + rand.nextFloat() * 0.06f;
                    oz = (rand.nextFloat() - 0.5f) * 0.22f;
                    vx = (rand.nextDouble() - 0.5) * 0.04;
                    vy = 0.010 + rand.nextDouble() * 0.012;
                    vz = (rand.nextDouble() - 0.5) * 0.04;
                    additive = false;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_WISP -> {
                    ox = px * (rand.nextFloat() - 0.5f) * 0.18f;
                    oz = pz * (rand.nextFloat() - 0.5f) * 0.18f;
                    vx = px * 0.018;
                    vy = 0.026 + rand.nextDouble() * 0.018;
                    vz = pz * 0.018;
                    additive = false;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_GLINT -> {
                    // Reflet unique qui balaie l'arc : un seul pixel très court, très clair
                    ox = dx * (0.02f + i * 0.05f);
                    oy = dy * (0.02f + i * 0.05f);
                    oz = dz * (0.02f + i * 0.05f);
                    vx = dx * 0.06;
                    vy = dy * 0.06;
                    vz = dz * 0.06;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_BOLT -> {
                    // Micro-éclair : 2-3 pixels en zigzag entre deux points
                    float t = (i + 1) / (float) (count + 1);
                    ox = (px * ((i & 1) == 0 ? 0.05f : -0.05f)) * (1.0f - t);
                    oz = (pz * ((i & 1) == 0 ? 0.05f : -0.05f)) * (1.0f - t);
                    vx = dx * 0.05;
                    vy = dy * 0.05 + 0.01;
                    vz = dz * 0.05;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_BUBBLE -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.20f;
                    oz = (rand.nextFloat() - 0.5f) * 0.20f;
                    vx = (rand.nextDouble() - 0.5) * 0.012;
                    vy = 0.030 + rand.nextDouble() * 0.020;
                    vz = (rand.nextDouble() - 0.5) * 0.012;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_ASH -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.24f;
                    oy = 0.04f + rand.nextFloat() * 0.10f;
                    oz = (rand.nextFloat() - 0.5f) * 0.24f;
                    vx = (rand.nextDouble() - 0.5) * 0.02;
                    vy = -0.010 - rand.nextDouble() * 0.012;
                    vz = (rand.nextDouble() - 0.5) * 0.02;
                    additive = false;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_MOTES -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.30f;
                    oy = (rand.nextFloat() - 0.5f) * 0.24f;
                    oz = (rand.nextFloat() - 0.5f) * 0.30f;
                    vx = (rand.nextDouble() - 0.5) * 0.006;
                    vy = (rand.nextDouble() - 0.5) * 0.004;
                    vz = (rand.nextDouble() - 0.5) * 0.006;
                }
                case deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_CRUMBLE -> {
                    ox = (rand.nextFloat() - 0.5f) * 0.18f;
                    oy = -0.06f + rand.nextFloat() * 0.10f;
                    oz = (rand.nextFloat() - 0.5f) * 0.18f;
                    vx = (rand.nextDouble() - 0.5) * 0.035;
                    vy = -0.015 - rand.nextDouble() * 0.02;
                    vz = (rand.nextDouble() - 0.5) * 0.035;
                    additive = false;
                }
                default -> { // SHAPE_DRIFT
                    ox = (rand.nextFloat() - 0.5f) * 0.16f;
                    oy = (rand.nextFloat() - 0.5f) * 0.16f;
                    oz = (rand.nextFloat() - 0.5f) * 0.16f;
                    vx = (rand.nextDouble() - 0.5) * 0.010;
                    vy = 0.008 + rand.nextDouble() * 0.008;
                    vz = (rand.nextDouble() - 0.5) * 0.010;
                }
            }

            int rgb = switch (i % 3) {
                case 0 -> sub.element().primary;
                case 1 -> sub.element().highlight;
                default -> rotateHue(sub.element().secondary, 0.06f);
            };
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                // Mélange élémentaire : chaque mini-pixel tombe à un autre point de la palette.
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = mixRgb(rgb, paletteColor(paletteMask, rgb, phase), 0.82f);
            }
            if (sub.shape() == deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_GLINT
                    || sub.shape() == deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_SHARD) {
                rgb = mixRgb(rgb, 0xFFFFFF, 0.45f);
            }
            float sz = sub.size() * (0.7f + rand.nextFloat() * 0.7f);
            PIXEL_PARTICLES.add(new FloatingPixel(additive,
                    x + ox, y + oy, z + oz, vx, vy, vz, sub.gravity(), sz, rgb,
                    sub.life() + rand.nextInt(4), rand.nextInt(Integer.MAX_VALUE)));
        }
    }

    /**
     * T351-ELEMENT-BLEND : phase continue 0..1 pour parcourir la palette élémentaire (~3 s par
     * aller-retour). Strictement continue dans le temps → aucun clignotement.
     */
    public static float palettePhase(float seconds) {
        float p = (seconds * 0.32f) % 1.0f;
        return (p < 0.0f) ? p + 1.0f : p;
    }

    /**
     * T351-ELEMENT-BLEND : mélange les couleurs primaires des familles élémentaires contenues dans
     * {@code mask} (bit {@code ordinal()} de {@link deepluckyblock.enchantments.DeepElement}).
     * {@code phase} 0..1 décrit un aller-retour feu → eau → feu, interpolé en smoothstep.
     * Une seule famille (ou aucune) renvoie {@code fallback} : le rendu d'origine est conservé.
     */
    public static int paletteColor(int mask, int fallback, float phase) {
        int total = Integer.bitCount(mask & deepluckyblock.enchantments.DeepElement.MASK_ALL);
        if (total <= 1) return fallback;
        float tri = (phase < 0.5f) ? (phase * 2.0f) : (2.0f - phase * 2.0f);
        tri = Math.max(0.0f, Math.min(1.0f, tri));
        tri = tri * tri * (3.0f - 2.0f * tri);
        float pos = tri * (total - 1);
        int i0 = (int) Math.floor(pos);
        if (i0 < 0) i0 = 0;
        if (i0 > total - 1) i0 = total - 1;
        int i1 = Math.min(i0 + 1, total - 1);
        float f = pos - i0;
        int a = nthElementPrimary(mask, i0, fallback);
        int b = nthElementPrimary(mask, i1, fallback);
        return mixRgb(a, b, f);
    }

    private static int nthElementPrimary(int mask, int index, int fallback) {
        int seen = 0;
        for (deepluckyblock.enchantments.DeepElement el : deepluckyblock.enchantments.DeepElement.values()) {
            if ((mask & (1 << el.ordinal())) == 0) continue;
            if (seen == index) return el.primary;
            seen++;
        }
        return fallback;
    }

    /** Mélange linéaire de deux couleurs 0xRRGGBB. */
    public static int mixRgb(int a, int b, float t) {
        float k = Math.max(0.0f, Math.min(1.0f, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * T351-MULTI-ELEMENT-GRADIENT : couleur de l'arme tenue quand elle porte PLUSIEURS éléments.
     * On parcourt les couleurs de famille en aller-retour (bleu → rouge → bleu...) avec une
     * interpolation lissée aux extrémités, puis on repasse par {@link #smoothColor} pour que la
     * transition reste douce même si l'ensemble d'éléments change (changement d'arme).
     */
    public static int gradientColorFor(net.minecraft.world.item.ItemStack stack, float timeSeconds) {
        List<deepluckyblock.enchantments.DeepElement> els =
                WeaponVisualEffectId.elementsOf(stack);
        if (els.isEmpty()) return 0xFFFFFF;
        if (els.size() == 1) {
            deepluckyblock.enchantments.DeepElement only = els.get(0);
            if (only == deepluckyblock.enchantments.DeepElement.CHAOS) {
                return smoothColor(0x5A5A0001, deepluckyblock.enchantments.DeepElement.chaosHue(
                        timeSeconds, 0.0f, only.primary));
            }
            return only.primary;
        }

        float cycle = (timeSeconds * 0.16f) % 2.0f;              // ~6,2 s par aller-retour
        float tri = cycle < 1.0f ? cycle : 2.0f - cycle;          // 0..1..0
        float pos = tri * (els.size() - 1);
        int i0 = (int) Math.floor(pos);
        if (i0 < 0) i0 = 0;
        if (i0 > els.size() - 1) i0 = els.size() - 1;
        int i1 = Math.min(i0 + 1, els.size() - 1);
        float f = pos - i0;
        f = f * f * (3.0f - 2.0f * f);                            // smoothstep : départs/arrivées doux
        int target = mixRgb(els.get(i0).primary, els.get(i1).primary, f);
        // T352-CHAOS-ELEMENT : une arme chaotique garde une base très sombre mais laisse passer un
        // reflet qui tourne sur toute la roue chromatique (sombre + multicolore, jamais clignotant).
        if (els.contains(deepluckyblock.enchantments.DeepElement.CHAOS)) {
            target = deepluckyblock.enchantments.DeepElement.chaosHue(timeSeconds, 0.0f, target);
        }
        int key = 0x5A5A0000 | (System.identityHashCode(stack) & 0xFFFF);
        return smoothColor(key, target);
    }

    private static void pushInstance(VfxInstance inst) {
        if (ACTIVE.size() >= MAX_ACTIVE_INSTANCES) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(inst);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer viewer = mc.player;
        if (level == null || viewer == null) {
            ACTIVE.clear();
            PIXEL_PARTICLES.clear();
            PENDING_ECHOES.clear();
            TRACKED_PROJECTILES.clear();
            PLAYER_WAS_SWINGING.clear();
            trackedLevel = null;
            return;
        }
        if (mc.isPaused()) return;

        lastTickNanos = System.nanoTime();
        if (trackedLevel != level) {
            trackedLevel = level;
            ACTIVE.clear();
            PIXEL_PARTICLES.clear();
            PENDING_ECHOES.clear();
            TRACKED_PROJECTILES.clear();
            PLAYER_WAS_SWINGING.clear();
        }
        DeepEnchant.rememberClientAccess(level.registryAccess());

        for (Iterator<VfxInstance> it = ACTIVE.iterator(); it.hasNext(); ) {
            VfxInstance inst = it.next();
            inst.age++;
            if (inst.age >= inst.maxAge) it.remove();
        }

        for (Iterator<FloatingPixel> it = PIXEL_PARTICLES.iterator(); it.hasNext(); ) {
            FloatingPixel fp = it.next();
            fp.age++;
            if (fp.age >= fp.maxAge) {
                it.remove();
                continue;
            }
            fp.x += fp.vx;
            fp.y += fp.vy;
            fp.z += fp.vz;
            fp.vy -= fp.gravity;
            fp.vx *= 0.90;
            fp.vz *= 0.90;
        }

        for (Iterator<PendingEcho> it = PENDING_ECHOES.iterator(); it.hasNext(); ) {
            PendingEcho pe = it.next();
            pe.ticksLeft--;
            if (pe.ticksLeft <= 0) {
                pushInstance(new VfxInstance(
                        pe.effect, KIND_SWING_SLASH, pe.intensity,
                        pe.x, pe.y, pe.z, pe.x, pe.y, pe.z,
                        pe.yaw, pe.pitch, -pe.tiltRad, 9, System.nanoTime(), pe.paletteMask));
                it.remove();
            }
        }

        // Sillage continu de mini-cubes 3D derrière chaque flèche / carreau / trident enchanté en vol !
        long now = level.getGameTime();
        for (Iterator<Map.Entry<Integer, TrackedProjectile>> it = TRACKED_PROJECTILES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, TrackedProjectile> e = it.next();
            TrackedProjectile tp = e.getValue();
            if (now > tp.expiresTick()) {
                it.remove();
                continue;
            }
            Entity ent = level.getEntity(e.getKey());
            if (ent == null) {
                if (now - tp.startTick() > 8L) it.remove();
                continue;
            }
            if (!ent.isAlive() || ent.onGround()) {
                it.remove();
                continue;
            }
            Vec3 vel = ent.getDeltaMovement();
            if (vel.lengthSqr() < 0.008) continue;

            for (int step = 0; step < 2; step++) {
                if (PIXEL_PARTICLES.size() >= MAX_VOXEL_TRAIL_PIXELS) PIXEL_PARTICLES.remove(0);
                double f = step * 0.5;
                double px = ent.getX() - vel.x * f + (level.random.nextDouble() - 0.5) * 0.08;
                double py = ent.getY() + 0.1 - vel.y * f + (level.random.nextDouble() - 0.5) * 0.08;
                double pz = ent.getZ() - vel.z * f + (level.random.nextDouble() - 0.5) * 0.08;
                // T349-PALETTE : traînée de flèche tricolore qui dérive lentement en teinte
                int rgb = (step == 0)
                        ? tp.effect().primaryRgb
                        : rotateHue(tp.effect().secondaryRgb, 0.10f + (float) ((now & 31L) / 31.0) * 0.14f);
                if (tp.paletteMask() != 0 && Integer.bitCount(tp.paletteMask() & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                    // T351-ELEMENT-BLEND : la traînée des flèches mélange aussi les familles de l'arc.
                    float phase = ((now & 15L) / 15.0f) * 0.5f + step * 0.5f;
                    rgb = mixRgb(rgb, paletteColor(tp.paletteMask(), rgb, phase), 0.85f);
                }
                PIXEL_PARTICLES.add(new FloatingPixel(
                        tp.effect().additive, px, py, pz,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        0.0f, 0.032f * (0.55f + level.random.nextFloat() * 0.9f), rgb, 9,
                        level.random.nextInt(Integer.MAX_VALUE)));
            }
        }

        // Détecte les frappes mêlée, gantelets mains nues, et tirs d'arc / arbalète
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p != viewer && p.distanceToSqr(viewer) > 32.0 * 32.0) continue;

            boolean swinging = p.swinging && p.swingTime <= 2;
            boolean prevSwing = PLAYER_WAS_SWINGING.getOrDefault(p.getId(), false);
            PLAYER_WAS_SWINGING.put(p.getId(), p.swinging);
            if (swinging && !prevSwing) {
                onPlayerWeaponSwing(p);
            }

            // Détection relâchement d'arc ou tir d'arbalète -> salve de mini-pixels 3D devant l'arme !
            ItemStack main = p.getMainHandItem();
            ItemStack off = p.getOffhandItem();
            boolean drawingNow = p.isUsingItem() && p.getUseItem().getItem() instanceof ProjectileWeaponItem && p.getTicksUsingItem() >= 4;
            boolean wasDrawing = PLAYER_WAS_DRAWING.getOrDefault(p.getId(), false);
            PLAYER_WAS_DRAWING.put(p.getId(), drawingNow);
            if (wasDrawing && !drawingNow) {
                ItemStack ranged = main.getItem() instanceof ProjectileWeaponItem ? main : off;
                onPlayerRangedShot(p, ranged);
            }

            boolean cbCharged = (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main))
                    || (off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off));
            boolean wasCbCharged = PLAYER_WAS_CB_CHARGED.getOrDefault(p.getId(), false);
            PLAYER_WAS_CB_CHARGED.put(p.getId(), cbCharged);
            if (wasCbCharged && !cbCharged) {
                ItemStack cb = main.getItem() instanceof CrossbowItem ? main : off;
                onPlayerRangedShot(p, cb);
            }
        }
    }

    private static void onPlayerRangedShot(Player p, ItemStack ranged) {
        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(ranged, 2);
        if (layers.isEmpty()) return;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double mx = eye.x + look.x * 0.85;
        double my = eye.y - 0.12 + look.y * 0.85;
        double mz = eye.z + look.z * 0.85;
        WeaponVisualEffectId fx = layers.get(0).effect();
        spawnMiniPixelBurst(fx, mx, my, mz, 10, 0.030f, 0.12,
                WeaponVisualEffectId.elementMaskOf(ranged));
    }

    private static void onPlayerWeaponSwing(Player p) {
        // T351/T359-TRIGGER-LAW : le serveur décide du vrai déclenchement (5 % coup normal / 76 % critique).
        // Côté client on ne garde qu'une « respiration » très rare (5 % + long cooldown) pour donner un
        // retour visuel même dans le vide (coup dans l'air), sans jamais doubler l'animation d'impact.
        long nowTick = p.level().getGameTime();
        Long swingReady = PLAYER_SWING_READY.get(p.getId());
        if (swingReady != null && nowTick < swingReady) return;
        if (p.level().random.nextInt(100) >= 5) {
            PLAYER_SWING_READY.put(p.getId(), nowTick + 4L + p.level().random.nextInt(5));
            return;
        }
        PLAYER_SWING_READY.put(p.getId(), nowTick + 24L + p.level().random.nextInt(24));
        if (PLAYER_SWING_READY.size() > 256) {
            PLAYER_SWING_READY.clear();
        }

        InteractionHand hand = p.swingingArm == null ? InteractionHand.MAIN_HAND : p.swingingArm;
        ItemStack stack = p.getItemInHand(hand);

        List<WeaponVisualEffectId.ResolvedLayer> layers;
        if (stack.isEmpty()) {
            layers = WeaponVisualEffectId.resolveBareHandGauntletLayers(p, 2);
        } else if (stack.getItem() instanceof ProjectileWeaponItem) {
            return;
        } else {
            layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        }
        if (layers.isEmpty()) return;

        int parity = PLAYER_SWING_PARITY.merge(p.getId(), 1, Integer::sum);
        float baseTilt = ((parity & 1) == 0) ? 0.36f : -0.34f;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double ox = eye.x + look.x * 0.55;
        double oy = eye.y - 0.16 + look.y * 0.35;
        double oz = eye.z + look.z * 0.55;

        WeaponVisualEffectId.ResolvedLayer primary = layers.get(0);
        WeaponVisualEffectId fx = primary.effect();
        int duration = (fx == WeaponVisualEffectId.ANNIHILATING_ARC || fx == WeaponVisualEffectId.CARNAGE_BERSERK) ? 11 : 8;
        float tilt = (fx == WeaponVisualEffectId.GUILLOTINE_CLEAVE) ? 1.48f : baseTilt;

        // T351-ELEMENT-BLEND : palette de l'arme tenue (feu + eau = entaille bicolore).
        int localPalette = WeaponVisualEffectId.elementMaskOf(stack);
        pushInstance(new VfxInstance(
                fx, KIND_SWING_SLASH, primary.level(),
                ox, oy, oz, ox + look.x * 3.4, oy + look.y * 3.4, oz + look.z * 3.4,
                p.getYRot(), p.getXRot(), tilt, duration, System.nanoTime(), localPalette));

        // Quelques mini-pixels 3D détachés le long du coup
        spawnMiniPixelBurst(fx, ox + look.x * 1.1, oy + look.y * 1.1, oz + look.z * 1.1,
                5, 0.026f, 0.09, localPalette);

        boolean hasEcho = fx == WeaponVisualEffectId.TWIN_ECHO_SLASH
                || (layers.size() > 1 && layers.get(1).effect() == WeaponVisualEffectId.TWIN_ECHO_SLASH);
        if (hasEcho && PENDING_ECHOES.size() < 16) {
            PendingEcho pe = new PendingEcho();
            pe.ticksLeft = 4;
            pe.effect = WeaponVisualEffectId.TWIN_ECHO_SLASH;
            pe.intensity = primary.level();
            pe.x = ox; pe.y = oy; pe.z = oz;
            pe.yaw = p.getYRot(); pe.pitch = p.getXRot(); pe.tiltRad = baseTilt;
            pe.paletteMask = localPalette;
            PENDING_ECHOES.add(pe);
        }
    }

    /** Budget de cubes par batch : évite de saturer le ByteBufferBuilder partagé de renderBuffers(). */
    private static final int MAX_CUBES_PER_BATCH = 900;
    /** Plafond dur par frame : au-delà, on arrête simplement d'émettre (jamais de pic incontrôlé). */
    private static final int HARD_CUBE_CAP_PER_FRAME = 7000;
    /** Distance au-delà de laquelle un effet n'est plus dessiné (FPS + taille des batches). */
    private static final double MAX_VFX_RENDER_DISTANCE_SQR = 96.0 * 96.0;

    private static int cubesSinceFlush;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty() && PIXEL_PARTICLES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();

        PoseStack poseStack = event.getPoseStack();
        Matrix4f mat = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f tmp = new Vector3f();

        cubesSinceFlush = 0;
        beginFrame((mc.level.getGameTime() & 0xFFFFFL) * 0.05f
                + (mc.isPaused() ? 0.0f : Mth.clamp((System.nanoTime() - lastTickNanos) / 1.0e9f, 0.0f, 1.0f)));

        // T348-BUFFER-SAFETY : tout le rendu est encapsulé. Un BufferBuilder partagé qui aurait été
        // vidé/recyclé par un autre listener ne doit JAMAIS faire crasher le jeu ("Not building!").
        try {
            renderVfxFrame(mc, buffers, mat, tmp, camPos);
        } catch (RuntimeException ex) {
            releaseBatches(buffers);
            PIXEL_PARTICLES.clear();
            if (ACTIVE.size() > 8) {
                ACTIVE.subList(0, ACTIVE.size() - 8).clear();
            }
        }
    }

    private static void renderVfxFrame(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                       Matrix4f mat, Vector3f tmp, Vec3 camPos) {
        float subTick = mc.isPaused()
                ? 0.0f
                : Mth.clamp((System.nanoTime() - lastTickNanos) / 50_000_000.0f, 0.0f, 1.0f);

        VertexConsumer addVc = null;
        VertexConsumer alphaVc = null;
        cubesSinceFlush = 0;

        // 1) Rendu des mini-pixels 3D flottants (traînées de flèches, gouttes de sang, flammèches, étincelles)
        for (int i = 0, n = PIXEL_PARTICLES.size(); i < n; i++) {
            FloatingPixel fp = PIXEL_PARTICLES.get(i);
            float t = Mth.clamp((fp.age + subTick) / (float) fp.maxAge, 0.0f, 1.0f);
            if (t >= 1.0f) continue;

            double dx = fp.x + fp.vx * subTick - camPos.x;
            double dy = fp.y + fp.vy * subTick - camPos.y;
            double dz = fp.z + fp.vz * subTick - camPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_VFX_RENDER_DISTANCE_SQR) continue;

            if (cubesSinceFlush >= MAX_CUBES_PER_BATCH) {
                flushBatches(buffers, addVc != null, alphaVc != null);
                addVc = null;
                alphaVc = null;
            }

            float rx = (float) dx;
            float ry = (float) dy;
            float rz = (float) dz;
            float sz = fp.halfSize * (1.0f - t * 0.45f);
            int alpha = Math.round(235.0f * (1.0f - t));

            VertexConsumer pvc = fp.additive ? addVc : alphaVc;
            if (pvc == null) {
                if (fp.additive) {
                    addVc = buffers.getBuffer(getAdditiveRenderType());
                    pvc = addVc;
                } else {
                    alphaVc = buffers.getBuffer(getTranslucentRenderType());
                    pvc = alphaVc;
                }
            }
            // Pixel principal de taille et de hauteur variables (aspect "vrai bloc de pixel")
            float hy = sz * (0.72f + 0.40f * pixelHash(fp.detailSalt, 3));
            emitVoxelCube(pvc, mat, tmp, rx, ry, rz, sz, hy, sz, fp.rgb, alpha);
            // 1 ou 2 micro-éclats accolés : le débris se lit comme une grappe de pixels, pas un cube lisse
            int detailMode = fp.detailSalt & 3;
            if (detailMode != 0) {
                float m = sz * 0.42f;
                emitVoxelCube(pvc, mat, tmp,
                        rx + (detailMode == 1 ? m * 1.1f : -m * 1.1f), ry + m * 0.5f, rz + m * 0.7f,
                        m * 0.62f, m * 0.52f, m * 0.62f,
                        detailMode == 2 ? 0xFFFFFF : fp.rgb, Math.round(alpha * 0.72f));
            }
            if (detailMode == 3) {
                float m = sz * 0.30f;
                emitVoxelCube(pvc, mat, tmp, rx - m, ry - m * 1.2f, rz - m,
                        m * 0.55f, m * 0.45f, m * 0.55f, fp.rgb, Math.round(alpha * 0.6f));
            }
        }

        // 2) Rendu des 30 animations d'armes en grappes de mini-cubes 3D blocky
        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            VfxInstance inst = ACTIVE.get(i);
            float t = Mth.clamp((inst.age + subTick) / (float) inst.maxAge, 0.0f, 1.0f);
            if (t >= 1.0f) continue;

            double ddx = inst.x - camPos.x;
            double ddy = inst.y - camPos.y;
            double ddz = inst.z - camPos.z;
            if (ddx * ddx + ddy * ddy + ddz * ddz > MAX_VFX_RENDER_DISTANCE_SQR) continue;

            // Flush progressif : chaque batch reste petit, le buffer partagé ne peut plus déborder
            if (cubesSinceFlush >= MAX_CUBES_PER_BATCH) {
                flushBatches(buffers, addVc != null, alphaVc != null);
                addVc = null;
                alphaVc = null;
            }

            VertexConsumer vc;
            if (inst.effect.additive) {
                if (addVc == null) addVc = buffers.getBuffer(getAdditiveRenderType());
                vc = addVc;
            } else {
                if (alphaVc == null) alphaVc = buffers.getBuffer(getTranslucentRenderType());
                vc = alphaVc;
            }

            // T350-VFX-VARIATIONS : chaque déclinaison a sa densité de pixels et sa taille.
            pixelDensity = inst.variantEntry.density();
            variantScale = inst.variantEntry.scale();
            // T359-BIG-ANIM-SCALE : échelle propre à l'instance (taille du mob touché).
            mobScale = inst.sizeScale;
            mobScaleActive = Math.abs(mobScale - 1.0f) > 0.02f;
            mobPivotX = (float) inst.x;
            mobPivotY = (float) inst.y;
            mobPivotZ = (float) inst.z;
            try {
                if (inst.kind == KIND_SWING_SLASH) {
                    renderVoxelSwingSlash(vc, mat, tmp, camPos, inst, t);
                } else if (inst.kind == KIND_BEAM_SIPHON) {
                    renderVoxelBeamOrSiphon(vc, mat, tmp, camPos, inst, t);
                } else {
                    renderVoxelImpact(vc, mat, tmp, camPos, inst, t);
                }
            } finally {
                pixelDensity = 1.0f;
                variantScale = 1.0f;
                mobScale = 1.0f;
                mobScaleActive = false;
            }
        }

        flushBatches(buffers, addVc != null, alphaVc != null);
    }

    /** Envoie les geometry accumulées au GPU et libère les builders (ordre : alpha puis additif). */
    private static void flushBatches(MultiBufferSource.BufferSource buffers, boolean hasAlpha, boolean hasAdditive) {
        if (hasAlpha) {
            buffers.endBatch(getTranslucentRenderType());
        }
        if (hasAdditive) {
            buffers.endBatch(getAdditiveRenderType());
        }
        cubesSinceFlush = 0;
    }

    /** Nettoyage défensif après une exception de rendu : libère les deux RenderTypes sans propager. */
    private static void releaseBatches(MultiBufferSource.BufferSource buffers) {
        try {
            buffers.endBatch(getTranslucentRenderType());
        } catch (RuntimeException ignored) {
        }
        try {
            buffers.endBatch(getAdditiveRenderType());
        } catch (RuntimeException ignored) {
        }
        cubesSinceFlush = 0;
    }

    // ==================== 1. ENTAILLES EN MINI-CUBES VOXELS 3D ====================

    private static void renderVoxelSwingSlash(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              Vec3 cam, VfxInstance inst, float t) {
        float rx = (float) (inst.x - cam.x);
        float ry = (float) (inst.y - cam.y);
        float rz = (float) (inst.z - cam.z);

        // T361-MOTIONS-UNIQUES : chaque enchantement a SA trajectoire (22 familles de mouvement).
        // Deux attaques ne se ressemblent plus, même à couleur identique : l'une balaie, l'autre
        // monte, l'autre aspire, l'autre se referme, l'autre tombe...
        WeaponVfxMotion.Profile prof = WeaponVfxMotion.of(inst.effect);

        float sweepDeg = switch (prof.shape()) {
            case WeaponVfxMotion.SHAPE_CRESCENT -> 118.0f;
            case WeaponVfxMotion.SHAPE_DOUBLE_ARC, WeaponVfxMotion.SHAPE_PRISM -> 150.0f;
            case WeaponVfxMotion.SHAPE_WAVE -> 165.0f;
            case WeaponVfxMotion.SHAPE_ASH -> 190.0f;
            case WeaponVfxMotion.SHAPE_MAW -> 130.0f;
            case WeaponVfxMotion.SHAPE_SHARDS -> 140.0f;
            default -> 130.0f;
        };
        if (inst.effect == WeaponVisualEffectId.CARNAGE_BERSERK) sweepDeg = 318.0f;
        if (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) sweepDeg = 205.0f;
        if (inst.effect == WeaponVisualEffectId.FOCUSED_BULLSEYE) sweepDeg = 62.0f;

        float radius = 1.32f + 0.22f * t;
        float voxelHalf = 0.038f;
        if (inst.effect == WeaponVisualEffectId.RAZOR_GLEAM) voxelHalf = 0.030f;
        if (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) voxelHalf = 0.056f;
        radius *= prof.scale();
        voxelHalf *= (0.90f + 0.10f * (prof.scale() - 1.0f));

        // T349-VARIATION : chaque déclenchement diffère un peu — amplitude, ouverture et grain.
        int v = inst.variant();
        float varSpan = 0.90f + (v & 3) * 0.06f;          // 0.90 .. 1.08
        float varSweep = 0.92f + ((v >> 1) & 3) * 0.05f;  // 0.92 .. 1.07
        radius *= varSpan;
        sweepDeg *= varSweep;
        voxelHalf *= 0.88f + (v & 1) * 0.10f;

        int alpha = Math.round(238.0f * (1.0f - t * t));
        int[] pal = palette(inst.effect, inst.variant(), inst.paletteMask);
        int arcCore = pal[0];
        int arcTrail = ((inst.variant() & 1) == 0) ? pal[1] : pal[2];
        int accent = pal[2];

        int motionSalt = (int) (inst.seed & 0x7FFFFFFFL) + inst.age * 31 + inst.effect.ordinal() * 977;
        emitMotionSlash(vc, mat, tmp, rx, ry, rz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                radius, voxelHalf, sweepDeg, t, arcCore, arcTrail, accent, alpha,
                prof, motionSalt, inst.effect.ordinal());
    }

    /**
     * T361-MOTIONS-UNIQUES : dessine UNE trajectoire de mouvement (une des 22 familles).
     *
     * <p>C'est le cœur de la différenciation : la même fonction sert à toutes les attaques, mais
     * chaque famille emprunte un trajet différent — et les paramètres du profil (bras, rotation,
     * montée, oscillation) font que deux enchantements d'une même famille restent distincts.</p>
     */
    private static void emitMotionSlash(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz,
                                        float yawDeg, float pitchDeg, float tiltRad,
                                        float radius, float baseHalf, float sweepDeg, float progress,
                                        int coreRgb, int trailRgb, int accentRgb, int peakAlpha,
                                        WeaponVfxMotion.Profile prof, int salt, int effectIdx) {
        if (peakAlpha <= 6) return;

        float yawRad = -yawDeg * Mth.DEG_TO_RAD;
        float pitchRad = pitchDeg * Mth.DEG_TO_RAD;

        // Repère de l'attaque (identique à l'ancien arc, pour ne rien casser visuellement).
        float fx = Mth.sin(yawRad) * Mth.cos(pitchRad);
        float fy = -Mth.sin(pitchRad);
        float fz = Mth.cos(yawRad) * Mth.cos(pitchRad);
        float rx0 = Mth.cos(yawRad);
        float rz0 = -Mth.sin(yawRad);
        float ux0 = -Mth.sin(yawRad) * Mth.sin(pitchRad);
        float uy0 = Mth.cos(pitchRad);
        float uz0 = -Mth.cos(yawRad) * Mth.sin(pitchRad);
        float cosT = Mth.cos(tiltRad);
        float sinT = Mth.sin(tiltRad);
        float sx = rx0 * cosT + ux0 * sinT;
        float sy = uy0 * sinT;
        float sz = rz0 * cosT + uz0 * sinT;

        float halfArc = (sweepDeg * 0.5f) * Mth.DEG_TO_RAD;
        int steps = Mth.clamp(Math.round(sweepDeg / 8.0f), 10, 42);
        float headU = Mth.clamp(progress * 1.40f, 0.0f, 1.0f);
        float rise = prof.rise();
        float wobble = prof.wobble();

        switch (prof.shape()) {

            // ------------------------------------------------------------------
            // Formes centrées (pas le long de l'arc) : orbite, estoc, colonnes, etc.
            // ------------------------------------------------------------------
            case WeaponVfxMotion.SHAPE_ORBIT -> {
                int rings = Math.max(1, Math.min(3, prof.arms()));
                for (int r = 0; r < rings; r++) {
                    float tilt = (r - (rings - 1) * 0.5f) * 0.6f + prof.spin() * progress * 0.9f;
                    float rr = radius * (0.85f + 0.16f * r) * (0.55f + 0.55f * progress);
                    float cy2 = cy + rise * progress + (r - (rings - 1) * 0.5f) * 0.22f;
                    int c1 = (r == 0) ? coreRgb : ((r == 1) ? trailRgb : accentRgb);
                    int c2 = (r == 0) ? trailRgb : ((r == 1) ? accentRgb : coreRgb);
                    emitPixelRunicCircle3D(vc, mat, tmp, cx, cy2, cz, rr,
                            prof.spin() * progress * 2.2f + r * 1.1f, c1, c2, 0xFFFFFF,
                            Math.round(peakAlpha * (1.0f - 0.18f * r)), r == 0);
                    if (Math.abs(Mth.cos(tilt)) > 0.35f) {
                        // second anneau incliné : donne un vrai volume d'orbite
                        float ir = rr * 0.62f;
                        for (int i = 0; i < 14; i++) {
                            float ang = (float) (Math.PI * 2.0 * i / 14) + prof.spin() * progress * 3.0f;
                            emitVoxelCube(vc, mat, tmp,
                                    quantize(cx + Mth.cos(ang) * ir, 0.0625f),
                                    quantize(cy2 + Mth.sin(ang) * ir * tilt, 0.0625f),
                                    quantize(cz + Mth.sin(ang) * ir * Mth.cos(tilt), 0.0625f),
                                    baseHalf * 0.72f, baseHalf * 0.72f, baseHalf * 0.72f,
                                    (i & 1) == 0 ? c2 : 0xFFFFFF,
                                    Math.round(peakAlpha * 0.8f));
                        }
                    }
                }
                if (prof.arms() >= 2) {
                    emitPixelCross3D(vc, mat, tmp, cx, cy + rise * progress, cz,
                            0.0625f, Math.min(4, prof.arms()), 0xFFFFFF, coreRgb, peakAlpha);
                }
            }

            case WeaponVfxMotion.SHAPE_LANCE -> {
                // Estoc rectiligne : l'arme "pousse" droit devant, la pointe s'allume.
                float len = radius * (0.85f + 0.9f * Mth.clamp(progress * 1.5f, 0.0f, 1.0f));
                float tx = cx + fx * len;
                float ty = cy + fy * len + rise * progress;
                float tz2 = cz + fz * len;
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz, tx, ty, tz2,
                        baseHalf * (1.0f - 0.35f * progress), 0xFFFFFF, coreRgb, peakAlpha);
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz, tx * 0.94f, ty * 0.94f, tz2 * 0.94f,
                        baseHalf * 0.5f, trailRgb, accentRgb, Math.round(peakAlpha * 0.55f));
                if (prof.arms() >= 2) {
                    emitPixelCross3D(vc, mat, tmp, tx, ty, tz2, 0.0625f, 4, 0xFFFFFF, trailRgb, peakAlpha);
                }
                // éclats arrachés le long de l'estoc
                for (int i = 1; i <= 5; i++) {
                    float u = i / 6.0f;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + fx * len * u + (pixelHash(salt, i) - 0.5f) * wobble, 0.0625f),
                            quantize(cy + fy * len * u + (pixelHash(salt + 3, i) - 0.5f) * 0.16f, 0.0625f),
                            quantize(cz + fz * len * u + (pixelHash(salt + 7, i) - 0.5f) * wobble, 0.0625f),
                            baseHalf * 0.34f, baseHalf * 0.34f, baseHalf * 0.34f, trailRgb,
                            Math.round(peakAlpha * 0.6f));
                }
            }

            case WeaponVfxMotion.SHAPE_PILLAR -> {
                // Pilier de lumière : monte du sol vers le ciel, s'amincit en hauteur.
                int layers = 26;
                float h = 7.5f * prof.scale();
                for (int li = 0; li < layers; li++) {
                    float lu = li / (float) (layers - 1);
                    float y = cy - 0.4f + lu * h * Mth.clamp(progress * 1.3f, 0.0f, 1.0f);
                    float w = baseHalf * (2.6f - 1.5f * lu) * (0.8f + 0.4f * Mth.sin(lu * 3.1f + progress * 4.0f));
                    float jx = (pixelHash(salt + li, li * 7) - 0.5f) * w;
                    float jz = (pixelHash(salt + li * 3, li * 11) - 0.5f) * w;
                    emitVoxelCube(vc, mat, tmp, quantize(cx + jx, 0.0625f), quantize(y, 0.0625f),
                            quantize(cz + jz, 0.0625f), w * 0.55f, w * 0.42f, w * 0.55f,
                            (li % 4 == 0) ? 0xFFFFFF : ((li & 1) == 0 ? coreRgb : trailRgb),
                            Math.round(peakAlpha * (1.0f - 0.5f * lu)));
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.42f, cz, 0.45f + 1.9f * progress,
                        coreRgb, trailRgb, Math.round(peakAlpha * 0.8f), PIXEL_RING_DASHED);
            }

            case WeaponVfxMotion.SHAPE_RUNE_CIRCLE -> {
                // Grand sceau runique au sol : anneau + runes + rayons, il tourne et s'ouvre.
                float rr = radius * Math.min(1.0f, progress * 2.2f);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.42f + rise * progress, cz, rr,
                        prof.spin() * progress * 3.0f, coreRgb, trailRgb, accentRgb, peakAlpha, true);
                int runes = Math.max(4, Math.min(8, prof.arms() * 2));
                for (int i = 0; i < runes; i++) {
                    float ang = (float) (Math.PI * 2.0 * i / runes) - prof.spin() * progress * 2.0f;
                    float ux = cx + Mth.cos(ang) * rr * 0.74f;
                    float uz = cz + Mth.sin(ang) * rr * 0.74f;
                    float uy = cy - 0.42f + rise * progress;
                    emitVoxelCube(vc, mat, tmp, quantize(ux, 0.0625f), quantize(uy + 0.05f, 0.0625f),
                            quantize(uz, 0.0625f), baseHalf * 0.9f, baseHalf * 1.5f, baseHalf * 0.9f,
                            0xFFFFFF, Math.round(peakAlpha * 0.85f));
                    emitVoxelCube(vc, mat, tmp, quantize(ux, 0.0625f), quantize(uy + 0.22f, 0.0625f),
                            quantize(uz, 0.0625f), baseHalf * 0.5f, baseHalf * 0.9f, baseHalf * 0.5f,
                            accentRgb, Math.round(peakAlpha * 0.6f));
                }
                for (int i = 0; i < 6; i++) {
                    float ang = (float) (Math.PI * 2.0 * i / 6) + prof.spin() * progress;
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx + Mth.cos(ang) * rr * 0.20f, cy - 0.40f, cz + Mth.sin(ang) * rr * 0.20f,
                            cx + Mth.cos(ang) * rr * 0.62f, cy - 0.40f, cz + Mth.sin(ang) * rr * 0.62f,
                            baseHalf * 0.7f, trailRgb, coreRgb, Math.round(peakAlpha * 0.7f));
                }
            }

            case WeaponVfxMotion.SHAPE_CROSS_STAR -> {
                // Étoile rayonnante : rayons de longueurs inégales partant d'un cœur blanc.
                int rays = Math.max(8, prof.arms() * 3);
                for (int i = 0; i < rays; i++) {
                    float ang = (float) (Math.PI * 2.0 * i / rays) + prof.spin() * progress * 0.8f;
                    float len = radius * (0.6f + 1.7f * progress) * ((i & 1) == 0 ? 1.0f : 0.66f);
                    float elev = ((i % 3 == 0) ? 0.55f : ((i % 3 == 1) ? -0.35f : 0.05f)) + rise * 0.4f;
                    int c = (i % 3 == 0) ? coreRgb : ((i % 3 == 1) ? trailRgb : accentRgb);
                    emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz,
                            cx + Mth.cos(ang) * len, cy + elev * len * 0.55f, cz + Mth.sin(ang) * len,
                            baseHalf * (1.0f - 0.45f * progress), 0xFFFFFF, c, peakAlpha);
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy + rise * progress, cz, 0.0625f,
                        Math.min(5, prof.arms()), 0xFFFFFF, 0xFFFFFF, peakAlpha);
                for (int k = 0; k < 8; k++) {
                    float ang = k * 0.785f - progress * 2.5f;
                    float rr = radius * 2.2f * progress;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rr, 0.0625f),
                            quantize(cy + (pixelHash(k, k * 7) - 0.5f) * 1.4f + rise * progress, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rr, 0.0625f),
                            baseHalf * 0.7f, baseHalf * 0.7f, baseHalf * 0.7f, accentRgb,
                            Math.round(peakAlpha * 0.8f));
                }
            }

            case WeaponVfxMotion.SHAPE_ECLIPSE -> {
                // Éclipse : disque sombre qui grossit, liseré lumineux, puis triple griffure.
                float disc = radius * (0.55f + 0.85f * progress);
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy + 0.32f + rise * progress, cz,
                        disc, trailRgb, 0x000000, peakAlpha);
                emitPixelRing3D(vc, mat, tmp, cx, cy + 0.32f + rise * progress, cz, disc,
                        coreRgb, accentRgb, peakAlpha, PIXEL_RING_SOLID);
                for (int claw = -1; claw <= 1; claw++) {
                    float off = claw * 0.26f + 0.10f * progress;
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx - 0.70f + off, cy + 0.80f, cz - 0.35f,
                            cx + 0.70f + off, cy - 0.55f, cz + 0.35f,
                            baseHalf * 1.05f, coreRgb, accentRgb, Math.round(peakAlpha * 0.9f));
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.50f, cz, 0.40f + radius * progress,
                        accentRgb, trailRgb, Math.round(peakAlpha * 0.7f), PIXEL_RING_DASHED);
            }

            case WeaponVfxMotion.SHAPE_RIFT -> {
                // Faille verticale : deux lèvres qui s'écartent puis se referment, éclats aspirés.
                float open = Mth.sin(Math.min(1.0f, progress * 1.2f) * (float) Math.PI);
                float half = 0.04f + radius * 0.55f * open;
                for (int side = -1; side <= 1; side += 2) {
                    for (int segI = 0; segI < 7; segI++) {
                        float u = segI / 6.0f;
                        float w = half * (0.35f + 0.65f * Mth.sin(u * (float) Math.PI));
                        float jx = (pixelHash(effectIdx + segI, side * 13) - 0.5f) * wobble * 5.0f;
                        emitVoxelCube(vc, mat, tmp,
                                quantize(cx + side * w + jx, 0.0625f),
                                quantize(cy - 1.10f + u * 2.35f + rise * progress, 0.0625f),
                                quantize(cz + side * 0.05f, 0.0625f),
                                baseHalf * 0.85f, baseHalf * 1.2f, baseHalf * 0.85f,
                                (segI & 1) == 0 ? coreRgb : accentRgb, peakAlpha);
                    }
                }
                for (int k = 0; k < 6; k++) {
                    float ang = k * 1.047f + progress * 3.0f;
                    float rr = radius * 1.2f * (1.0f - progress);
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rr, 0.0625f),
                            quantize(cy + 0.25f + Mth.sin(ang * 2.0f) * 0.45f, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rr, 0.0625f),
                            baseHalf * 0.55f, baseHalf * 0.55f, baseHalf * 0.55f, 0xFFFFFF,
                            Math.round(peakAlpha * 0.9f));
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.45f, cz, 0.35f + radius * progress,
                        accentRgb, coreRgb, Math.round(peakAlpha * 0.6f), PIXEL_RING_DASHED);
            }

            case WeaponVfxMotion.SHAPE_CLAW -> {
                // Triple griffure : trois raies diagonales parallèles qui "rayent" l'espace.
                int claws = Math.max(3, Math.min(5, prof.arms() + 2));
                for (int claw = 0; claw < claws; claw++) {
                    float off = (claw - (claws - 1) * 0.5f) * 0.28f;
                    float grow = Mth.clamp(progress * 1.6f, 0.0f, 1.0f);
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx - 0.65f + off, cy + 0.75f + rise * progress, cz - 0.30f,
                            Mth.lerp(grow, cx - 0.65f + off, cx + 0.65f + off),
                            Mth.lerp(grow, cy + 0.75f, cy - 0.65f + rise * progress),
                            Mth.lerp(grow, cz - 0.30f, cz + 0.30f),
                            baseHalf * 1.1f, accentRgb, coreRgb, peakAlpha);
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 2, 0xFFFFFF, accentRgb,
                        Math.round(peakAlpha * 0.7f));
            }

            // ------------------------------------------------------------------
            // Formes qui suivent l'arc de l'arme
            // ------------------------------------------------------------------
            default -> {
                for (int i = 0; i <= steps; i++) {
                    float u = (float) i / steps;
                    if (u > headU + 0.15f) break;
                    float distBehind = headU - u;
                    if (distBehind > 0.70f) continue;

                    float ang = (u - 0.5f) * 2.0f * halfArc;
                    // Les formes "tournantes" enroulent l'angle autour de l'arc.
                    float twist = 0.0f;
                    switch (prof.shape()) {
                        case WeaponVfxMotion.SHAPE_VORTEX -> twist = -u * prof.spin() * 2.6f;
                        case WeaponVfxMotion.SHAPE_SPIRAL -> twist = u * prof.spin() * 1.9f;
                        case WeaponVfxMotion.SHAPE_WAVE -> twist = Mth.sin(u * 8.0f + progress * 6.0f) * 0.16f;
                        case WeaponVfxMotion.SHAPE_ASH -> twist = Mth.sin(u * 5.0f + progress * 9.0f) * 0.10f;
                        default -> { }
                    }
                    float ca = Mth.cos(ang + twist);
                    float sa = Mth.sin(ang + twist);
                    float dirX = fx * ca + sx * sa;
                    float dirY = fy * ca + sy * sa;
                    float dirZ = fz * ca + sz * sa;

                    float localR = radius;
                    if (prof.shape() == WeaponVfxMotion.SHAPE_VORTEX) {
                        // la spirale se resserre à mesure qu'elle tourne (aspiration)
                        localR = radius * (1.15f - 0.55f * u) * (1.0f - 0.25f * progress);
                    } else if (prof.shape() == WeaponVfxMotion.SHAPE_WAVE) {
                        localR = radius * (1.0f + 0.12f * Mth.sin(u * 6.0f + progress * 5.0f));
                    }

                    float taper = Mth.sin(u * (float) Math.PI);
                    float jitterX = (wobble > 0.0f) ? (pixelHash(salt + i, i * 13) - 0.5f) * wobble * 2.0f : 0.0f;
                    float jitterY = (wobble > 0.0f) ? (pixelHash(salt + i * 3, i * 7) - 0.5f) * wobble * 2.0f : 0.0f;
                    float px = quantize(cx + dirX * localR + jitterX, 0.0625f);
                    float py = quantize(cy + dirY * localR + jitterY + rise * progress * u, 0.0625f);
                    float pz = quantize(cz + dirZ * localR + jitterX * 0.6f, 0.0625f);

                    boolean isHead = Math.abs(distBehind) < 0.12f;
                    float hs = baseHalf * (0.55f + 0.75f * taper) * (isHead ? 1.25f : 1.0f)
                            * (0.82f + 0.34f * pixelHash(salt, i * 13));
                    int rgb = isHead ? 0xFFFFFF : (distBehind < 0.28f ? coreRgb : trailRgb);
                    int a = Math.round(peakAlpha * Math.max(0.18f, 1.0f - Math.max(0.0f, distBehind) * 1.35f));

                    switch (prof.shape()) {
                        case WeaponVfxMotion.SHAPE_SHARDS -> {
                            // éclats projetés vers l'extérieur : pointe de 3 cubes qui s'affine
                            for (int sh = 0; sh < 3; sh++) {
                                float k = 0.35f + sh * 0.55f + 0.35f * progress;
                                float sz2 = hs * (1.0f - 0.24f * sh);
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(cx + dirX * localR * k, 0.0625f),
                                        quantize(cy + dirY * localR * k + rise * progress * u + sh * 0.03f, 0.0625f),
                                        quantize(cz + dirZ * localR * k, 0.0625f),
                                        sz2 * 0.62f, sz2 * (1.5f - 0.25f * sh), sz2 * 0.62f,
                                        (sh == 0) ? 0xFFFFFF : ((sh == 1) ? coreRgb : accentRgb),
                                        Math.round(a * (1.0f - 0.22f * sh)));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_MAW -> {
                            // dents qui pointent vers le centre et se referment
                            float close = 1.0f - 0.62f * progress;
                            float inX = cx + dirX * localR * close;
                            float inY = cy + dirY * localR * close + rise * progress;
                            float inZ = cz + dirZ * localR * close;
                            emitSteppedVoxelLine(vc, mat, tmp, px, py, pz, inX, inY, inZ,
                                    baseHalf * 0.6f, (i & 1) == 0 ? 0xFFFFFF : coreRgb, accentRgb, a);
                        }
                        case WeaponVfxMotion.SHAPE_DRIP -> {
                            // goutte + traînée qui tombe (le sang, le venin)
                            emitPixelDroplet3D(vc, mat, tmp, px, py, pz, hs,
                                    (i & 1) == 0 ? coreRgb : trailRgb, 0xFF8A8A, a, salt + i);
                            float fall = 0.35f + 0.55f * progress * (1.0f + taper);
                            emitVoxelCube(vc, mat, tmp, px, quantize(py - fall, 0.0625f), pz,
                                    hs * 0.42f, hs * 0.30f, hs * 0.42f, accentRgb,
                                    Math.round(a * 0.7f));
                        }
                        case WeaponVfxMotion.SHAPE_COLUMN -> {
                            // colonne verticale au niveau du tranchant : elle balaie avec l'arc
                            float h = 1.1f + 1.5f * prof.scale() * (isHead ? 1.0f : 0.55f);
                            int col = Math.max(3, Math.round(h / 0.16f));
                            for (int c2 = 0; c2 < col; c2++) {
                                float cy2 = py + (rise >= 0.0f ? 1.0f : -1.0f) * (c2 * 0.16f);
                                emitVoxelCube(vc, mat, tmp, px, quantize(cy2, 0.0625f), pz,
                                        hs * (1.0f - 0.10f * c2), hs * 0.85f, hs * (1.0f - 0.10f * c2),
                                        (c2 == 0) ? 0xFFFFFF : ((c2 & 1) == 0 ? coreRgb : accentRgb),
                                        Math.round(a * (1.0f - 0.10f * c2)));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_WAVE -> {
                            // nappe : deux rangées qui ondulent (eau)
                            emitVoxelCube(vc, mat, tmp, px, py, pz, hs, hs * 0.6f, hs, rgb, a);
                            float waveY = py + 0.10f * Mth.sin(u * 9.0f - progress * 7.0f);
                            emitVoxelCube(vc, mat, tmp, px, quantize(waveY - 0.14f, 0.0625f), pz,
                                    hs * 0.7f, hs * 0.5f, hs * 0.7f, accentRgb, Math.round(a * 0.75f));
                        }
                        case WeaponVfxMotion.SHAPE_SPIRAL -> {
                            // hélice enroulée autour du tranchant
                            for (int arm = 0; arm < Math.max(1, Math.min(3, prof.arms())); arm++) {
                                float ph = u * 9.0f + progress * 6.0f + arm * 2.094f;
                                float off = 0.10f + 0.05f * prof.scale();
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + Mth.cos(ph) * off, 0.0625f),
                                        quantize(py + Mth.sin(ph) * off, 0.0625f),
                                        quantize(pz + Mth.sin(ph * 1.3f) * off, 0.0625f),
                                        hs * 0.7f, hs * 0.7f, hs * 0.7f,
                                        (arm == 0) ? rgb : accentRgb, Math.round(a * 0.85f));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_BOLT -> {
                            // éclair : les points sautent franchement de part et d'autre de l'arc
                            float jump = (i & 1) == 0 ? 1.0f : -1.0f;
                            float jag = wobble * jump * (0.6f + 0.6f * pixelHash(salt, i));
                            emitVoxelCube(vc, mat, tmp,
                                    quantize(px + sx * jag, 0.0625f),
                                    quantize(py + sy * jag + uy0 * jag * 0.6f, 0.0625f),
                                    quantize(pz + sz * jag, 0.0625f),
                                    hs * (0.7f + 0.5f * pixelHash(i, salt)), hs * 0.75f, hs * 0.85f,
                                    (Math.abs(distBehind) < 0.05f) ? 0xFFFFFF : ((i & 1) == 0 ? coreRgb : trailRgb), a);
                            if ((i & 3) == 0) {
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + sx * jag * 1.8f, 0.0625f),
                                        quantize(py + sy * jag * 1.8f, 0.0625f),
                                        quantize(pz + sz * jag * 1.8f, 0.0625f),
                                        hs * 0.3f, hs * 0.3f, hs * 0.3f, 0xFFFFFF, Math.round(a * 0.6f));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_DUNE -> {
                            // dune : blocs plus gros qui montent, grains qui retombent
                            float bulk = prof.scale() * 1.15f;
                            emitVoxelCube(vc, mat, tmp, px, quantize(py + 0.05f, 0.0625f), pz,
                                    hs * bulk, hs * bulk * 0.8f, hs * bulk,
                                    (i & 1) == 0 ? coreRgb : trailRgb, a);
                            for (int g2 = 0; g2 < 2; g2++) {
                                float drop = 0.30f + 0.55f * progress * (0.5f + pixelHash(i, g2 * 7));
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + (pixelHash(i + g2, 3) - 0.5f) * 0.35f, 0.0625f),
                                        quantize(py - drop, 0.0625f),
                                        quantize(pz + (pixelHash(i + g2, 9) - 0.5f) * 0.35f, 0.0625f),
                                        hs * 0.26f, hs * 0.26f, hs * 0.26f, accentRgb,
                                        Math.round(a * 0.65f));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_ASH -> {
                            // braises qui montent et dérivent
                            float drift = prof.rise() * progress * (0.6f + 0.8f * pixelHash(i, 5));
                            emitVoxelCube(vc, mat, tmp,
                                    quantize(px + 0.12f * Mth.sin(progress * 5.0f + i), 0.0625f),
                                    quantize(py + drift, 0.0625f),
                                    quantize(pz + 0.12f * Mth.cos(progress * 4.0f + i), 0.0625f),
                                    hs * 0.55f, hs * 0.55f, hs * 0.55f,
                                    (i & 1) == 0 ? coreRgb : accentRgb,
                                    Math.round(a * (1.0f - 0.35f * progress)));
                        }
                        case WeaponVfxMotion.SHAPE_BLOOM -> {
                            // pétales qui s'ouvrent vers le haut
                            float open = 0.4f + 0.9f * progress;
                            for (int petal = 0; petal < Math.max(3, prof.arms()); petal++) {
                                float pa = petal * 2.094f + u * 2.0f;
                                float pr2 = 0.10f + 0.16f * open;
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + Mth.cos(pa) * pr2, 0.0625f),
                                        quantize(py + 0.10f * open + rise * progress * u, 0.0625f),
                                        quantize(pz + Mth.sin(pa) * pr2, 0.0625f),
                                        hs * 0.62f, hs * 0.9f, hs * 0.62f,
                                        (petal == 0) ? 0xFFFFFF : ((petal & 1) == 0 ? coreRgb : accentRgb),
                                        Math.round(a * 0.9f));
                            }
                        }
                        case WeaponVfxMotion.SHAPE_VORTEX -> {
                            // spirale aspirante : cubes + éclats qui filent vers l'intérieur
                            emitVoxelCube(vc, mat, tmp, px, py, pz, hs, hs * 0.8f, hs, rgb, a);
                            float suck = 0.30f + 0.45f * progress;
                            emitVoxelCube(vc, mat, tmp,
                                    quantize(Mth.lerp(suck, px, cx), 0.0625f),
                                    quantize(Mth.lerp(suck, py, cy), 0.0625f),
                                    quantize(Mth.lerp(suck, pz, cz), 0.0625f),
                                    hs * 0.45f, hs * 0.45f, hs * 0.45f, 0xFFFFFF, Math.round(a * 0.8f));
                        }
                        case WeaponVfxMotion.SHAPE_DOUBLE_ARC, WeaponVfxMotion.SHAPE_PRISM -> {
                            // géré hors boucle (arcs multiples) — rien ici
                        }
                        default -> {
                            // CRESCENT (et repli) : arc plein + épaisseur + éclat de tranche
                            emitVoxelCube(vc, mat, tmp, px, py, pz, hs,
                                    hs * (0.78f + 0.3f * pixelHash(salt + 3, i)), hs, rgb, a);
                            if (taper > 0.45f) {
                                float pxIn = quantize(cx + dirX * (localR - baseHalf * 2.2f), 0.0625f);
                                float pyIn = quantize(cy + dirY * (localR - baseHalf * 2.2f) + rise * progress * u, 0.0625f);
                                float pzIn = quantize(cz + dirZ * (localR - baseHalf * 2.2f), 0.0625f);
                                emitVoxelCube(vc, mat, tmp, pxIn, pyIn, pzIn,
                                        hs * 0.72f, hs * 0.72f, hs * 0.72f, trailRgb, Math.round(a * 0.8f));
                                if ((i & 1) == 0) {
                                    emitVoxelCube(vc, mat, tmp,
                                            quantize(cx + dirX * (localR + baseHalf * 1.4f), 0.03125f),
                                            quantize(cy + dirY * (localR + baseHalf * 1.4f) + rise * progress * u, 0.03125f),
                                            quantize(cz + dirZ * (localR + baseHalf * 1.4f), 0.03125f),
                                            hs * 0.32f, hs * 0.26f, hs * 0.32f, 0xFFFFFF, Math.round(a * 0.55f));
                                }
                            }
                            // couche supplémentaire (2e bras : flamme d'âme, furie...)
                            if (prof.arms() >= 2) {
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + sx * hs * 2.0f, 0.0625f),
                                        quantize(py + sy * hs * 2.0f + rise * progress * 0.6f, 0.0625f),
                                        quantize(pz + sz * hs * 2.0f, 0.0625f),
                                        hs * 0.55f, hs * 0.8f, hs * 0.55f, accentRgb, Math.round(a * 0.7f));
                            }
                        }
                    }
                }

                // Les familles à arcs multiples dessinent leur arc principal + les secondaires.
                if (prof.shape() == WeaponVfxMotion.SHAPE_DOUBLE_ARC
                        || prof.shape() == WeaponVfxMotion.SHAPE_PRISM) {
                    emitMotionSlash(vc, mat, tmp, cx, cy, cz, yawDeg, pitchDeg, -tiltRad * 0.85f,
                            radius * 0.80f, baseHalf * 0.82f, sweepDeg * 0.90f, progress,
                            trailRgb, coreRgb, accentRgb, Math.round(peakAlpha * 0.85f),
                            WeaponVfxMotion.crescent(1, wobble * 0.6f), salt + 17, effectIdx);
                    if (prof.shape() == WeaponVfxMotion.SHAPE_PRISM) {
                        int extra = Math.max(1, Math.min(3, prof.arms() - 1));
                        for (int layer = 1; layer <= extra; layer++) {
                            float hue = (progress * prof.spin() + layer * 0.27f) % 1.0f;
                            if (hue < 0.0f) hue += 1.0f;
                            int c = Mth.hsvToRgb(hue, 0.70f, 1.0f);
                            emitMotionSlash(vc, mat, tmp, cx, cy, cz, yawDeg, pitchDeg, tiltRad * (0.6f * layer),
                                    radius * (1.0f - 0.14f * layer), baseHalf * (0.8f - 0.12f * layer),
                                    sweepDeg * (0.94f - 0.06f * layer), progress,
                                    c, coreRgb, 0xFFFFFF, Math.round(peakAlpha * (0.7f - 0.12f * layer)),
                                    WeaponVfxMotion.crescent(1, 0.0f), salt + 31 * layer, effectIdx);
                        }
                    }
                }
            }
        }
    }

    private static void emitVoxelSlashArc(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float cx, float cy, float cz,
                                          float yawDeg, float pitchDeg, float tiltRad,
                                          float radius, float baseHalfSize, float sweepDeg, float progress,
                                          int coreRgb, int trailRgb, int peakAlpha) {
        if (peakAlpha <= 6) return;
        float yawRad = -yawDeg * Mth.DEG_TO_RAD;
        float pitchRad = pitchDeg * Mth.DEG_TO_RAD;

        float fx = Mth.sin(yawRad) * Mth.cos(pitchRad);
        float fy = -Mth.sin(pitchRad);
        float fz = Mth.cos(yawRad) * Mth.cos(pitchRad);

        float rx0 = Mth.cos(yawRad);
        float rz0 = -Mth.sin(yawRad);
        float ux0 = -Mth.sin(yawRad) * Mth.sin(pitchRad);
        float uy0 = Mth.cos(pitchRad);
        float uz0 = -Mth.cos(yawRad) * Mth.sin(pitchRad);

        float cosT = Mth.cos(tiltRad);
        float sinT = Mth.sin(tiltRad);
        float sx = rx0 * cosT + ux0 * sinT;
        float sy = uy0 * sinT;
        float sz = rz0 * cosT + uz0 * sinT;

        // Pas volontairement gros (≈1 pixel de grille) : l'entaille doit se lire comme un escalier de
        // pixels 3D bien visible, jamais comme un arc lissé.
        int pixelSteps = Math.max(9, Math.round(sweepDeg / 10.0f));
        float halfArc = (sweepDeg * 0.5f) * Mth.DEG_TO_RAD;
        float headU = Mth.clamp(progress * 1.35f, 0.0f, 1.0f);
        int salt = Math.round(cx * 577.0f + cz * 431.0f + radius * 97.0f);

        for (int i = 0; i <= pixelSteps; i++) {
            float u = (float) i / pixelSteps;
            if (u > headU + 0.15f) break;

            float distBehind = headU - u;
            if (distBehind > 0.68f) continue;

            float ang = (u - 0.5f) * 2.0f * halfArc;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            float dirX = fx * ca + sx * sa;
            float dirY = fy * ca + sy * sa;
            float dirZ = fz * ca + sz * sa;

            float taper = Mth.sin(u * (float) Math.PI);
            float px = quantize(cx + dirX * radius, 0.0625f);
            float py = quantize(cy + dirY * radius, 0.0625f);
            float pz = quantize(cz + dirZ * radius, 0.0625f);

            boolean isHead = Math.abs(distBehind) < 0.12f;
            float hs = baseHalfSize * (0.55f + 0.75f * taper) * (isHead ? 1.25f : 1.0f)
                    * (0.82f + 0.34f * pixelHash(salt, i * 13));
            int rgb = isHead ? 0xFFFFFF : (distBehind < 0.28f ? coreRgb : trailRgb);
            int a = Math.round(peakAlpha * Math.max(0.2f, 1.0f - Math.max(0.0f, distBehind) * 1.35f));

            emitVoxelCube(vc, mat, tmp, px, py, pz, hs,
                    hs * (0.78f + 0.3f * pixelHash(salt + 3, i)), hs, rgb, a);
            // Épaisseur blocky doublée d'un mini-cube intérieur décalé d'un pixel 1/16
            if (taper > 0.45f) {
                float pxIn = quantize(cx + dirX * (radius - baseHalfSize * 2.2f), 0.0625f);
                float pyIn = quantize(cy + dirY * (radius - baseHalfSize * 2.2f), 0.0625f);
                float pzIn = quantize(cz + dirZ * (radius - baseHalfSize * 2.2f), 0.0625f);
                emitVoxelCube(vc, mat, tmp, pxIn, pyIn, pzIn, hs * 0.72f, hs * 0.72f, hs * 0.72f, trailRgb, Math.round(a * 0.8f));
                // Éclat de bord minuscule sur la tranche extérieure : cassure pixel-art
                if ((i & 1) == 0) {
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + dirX * (radius + baseHalfSize * 1.4f), 0.03125f),
                            quantize(cy + dirY * (radius + baseHalfSize * 1.4f), 0.03125f),
                            quantize(cz + dirZ * (radius + baseHalfSize * 1.4f), 0.03125f),
                            hs * 0.32f, hs * 0.26f, hs * 0.32f, 0xFFFFFF, Math.round(a * 0.55f));
                }
            }
        }
    }

    // ==================== 2. RAYONS, ÉCLAIRS CRÉNELÉS & SIPHONS VOXELS ====================

    private static void renderVoxelBeamOrSiphon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                                Vec3 cam, VfxInstance inst, float t) {
        float x0 = (float) (inst.x - cam.x);
        float y0 = (float) (inst.y - cam.y);
        float z0 = (float) (inst.z - cam.z);
        float x1 = (float) (inst.tx - cam.x);
        float y1 = (float) (inst.ty - cam.y);
        float z1 = (float) (inst.tz - cam.z);
        int alpha = Math.round(240.0f * (1.0f - t));
        int pRgb = inst.primary();
        int sRgb = inst.secondary();
        int salt = (int) (inst.seed & 0x7FFFFFFFL) + inst.age * 17;

        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        // perpendiculaire horizontale
        float px = -nz, pz = nx;
        float pl = Mth.sqrt(px * px + pz * pz);
        if (pl < 0.001f) { px = 1.0f; pz = 0.0f; pl = 1.0f; }
        px /= pl; pz /= pl;

        // T361-MOTIONS-UNIQUES : le RAYON suit lui aussi le mouvement de son enchantement.
        int shape = WeaponVfxMotion.of(inst.effect).shape();
        switch (shape) {
            case WeaponVfxMotion.SHAPE_VORTEX -> {
                // spirale aspirante : les cubes tournent autour du rayon en se resserrant vers la cible
                int nodes = 26;
                for (int i = 0; i <= nodes; i++) {
                    float u = (float) i / nodes;
                    float ang = u * 10.0f + inst.age * 0.55f;
                    float rad = 0.55f * (1.0f - 0.65f * u) * (1.0f - 0.3f * t);
                    emitVoxelCube(vc, mat, tmp,
                            quantize(Mth.lerp(u, x0, x1) + px * Mth.cos(ang) * rad, 0.0625f),
                            quantize(Mth.lerp(u, y0, y1) + Mth.sin(ang) * rad, 0.0625f),
                            quantize(Mth.lerp(u, z0, z1) + pz * Mth.cos(ang) * rad, 0.0625f),
                            0.038f * (1.2f - 0.7f * u), 0.038f, 0.038f,
                            (i & 1) == 0 ? pRgb : sRgb, Math.round(alpha * (0.45f + 0.55f * u)));
                }
            }
            case WeaponVfxMotion.SHAPE_SPIRAL -> {
                // hélice enroulée : le vent s'enroule autour du trajet
                int nodes = 30;
                for (int i = 0; i <= nodes; i++) {
                    float u = (float) i / nodes;
                    float ang = u * 14.0f + inst.age * 0.7f;
                    for (int arm = 0; arm < 2; arm++) {
                        float a2 = ang + arm * (float) Math.PI;
                        float rad = 0.30f + 0.12f * Mth.sin(u * 6.0f);
                        emitVoxelCube(vc, mat, tmp,
                                quantize(Mth.lerp(u, x0, x1) + px * Mth.cos(a2) * rad, 0.03125f),
                                quantize(Mth.lerp(u, y0, y1) + Mth.sin(a2) * rad, 0.03125f),
                                quantize(Mth.lerp(u, z0, z1) + pz * Mth.cos(a2) * rad, 0.03125f),
                                0.030f, 0.030f, 0.030f,
                                (arm == 0) ? pRgb : sRgb, Math.round(alpha * (0.9f - 0.35f * u)));
                    }
                }
            }
            case WeaponVfxMotion.SHAPE_BOLT -> {
                emitVoxelLightning(vc, mat, tmp, x0, y0, z0, x1, y1, z1, 11, 0.30f,
                        inst.seed + inst.age / 2, 0xFFFFFF, pRgb, alpha, 0.036f);
                for (int b = 0; b < 2; b++) {
                    float off = (b == 0 ? 1.0f : -1.0f) * 0.28f;
                    emitVoxelLightning(vc, mat, tmp, x0 + px * off, y0, z0 + pz * off,
                            x1 + px * off, y1, z1 + pz * off, 7, 0.22f,
                            inst.seed + b * 41L + inst.age, sRgb, pRgb,
                            Math.round(alpha * 0.7f), 0.026f);
                }
            }
            case WeaponVfxMotion.SHAPE_WAVE -> {
                // ruban ondulant : deux nappes qui se croisent
                int nodes = 30;
                for (int i = 0; i <= nodes; i++) {
                    float u = (float) i / nodes;
                    float wob = Mth.sin(u * 9.0f - inst.age * 0.35f) * 0.16f;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(Mth.lerp(u, x0, x1) + px * wob, 0.0625f),
                            quantize(Mth.lerp(u, y0, y1) + wob * 0.7f, 0.0625f),
                            quantize(Mth.lerp(u, z0, z1) + pz * wob, 0.0625f),
                            0.034f, 0.030f, 0.034f, (i & 1) == 0 ? pRgb : sRgb,
                            Math.round(alpha * (0.95f - 0.4f * u)));
                    emitVoxelCube(vc, mat, tmp,
                            quantize(Mth.lerp(u, x0, x1) - px * wob * 0.8f, 0.0625f),
                            quantize(Mth.lerp(u, y0, y1) - wob * 0.5f + 0.05f, 0.0625f),
                            quantize(Mth.lerp(u, z0, z1) - pz * wob * 0.8f, 0.0625f),
                            0.024f, 0.024f, 0.024f, 0xFFFFFF, Math.round(alpha * 0.5f));
                }
            }
            case WeaponVfxMotion.SHAPE_SHARDS -> {
                // chapelet d'éclats : chaque segment projette une pointe perpendiculaire
                int nodes = 16;
                for (int i = 0; i <= nodes; i++) {
                    float u = (float) i / nodes;
                    float bx = Mth.lerp(u, x0, x1);
                    float by = Mth.lerp(u, y0, y1);
                    float bz = Mth.lerp(u, z0, z1);
                    emitVoxelCube(vc, mat, tmp, quantize(bx, 0.0625f), quantize(by, 0.0625f), quantize(bz, 0.0625f),
                            0.036f, 0.036f, 0.036f, (i & 1) == 0 ? pRgb : sRgb, Math.round(alpha * 0.9f));
                    if ((i & 1) == 0) {
                        float spike = 0.22f;
                        emitSteppedVoxelLine(vc, mat, tmp, bx, by, bz,
                                bx + px * spike, by + 0.16f, bz + pz * spike,
                                0.024f, 0xFFFFFF, sRgb, Math.round(alpha * 0.7f));
                    }
                }
            }
            case WeaponVfxMotion.SHAPE_PILLAR -> {
                // colonne de lumière : le rayon est épais, crénelé, avec halo large
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1, 0.075f, 0xFFFFFF, pRgb, alpha);
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0 + 0.12f, z0, x1, y1 + 0.12f, z1,
                        0.040f, pRgb, sRgb, Math.round(alpha * 0.6f));
            }
            case WeaponVfxMotion.SHAPE_DRIP -> {
                // gouttes qui perlent le long du rayon puis tombent
                int nodes = 18;
                for (int i = 0; i <= nodes; i++) {
                    float u = (float) i / nodes;
                    float bx = Mth.lerp(u, x0, x1);
                    float by = Mth.lerp(u, y0, y1);
                    float bz = Mth.lerp(u, z0, z1);
                    emitVoxelCube(vc, mat, tmp, quantize(bx, 0.0625f), quantize(by, 0.0625f), quantize(bz, 0.0625f),
                            0.028f, 0.028f, 0.028f, pRgb, Math.round(alpha * 0.85f));
                    if ((i & 1) == 0) {
                        float fall = 0.18f + 0.5f * u * (0.6f + pixelHash(salt, i));
                        emitPixelDroplet3D(vc, mat, tmp,
                                quantize(bx, 0.0625f), quantize(by - fall, 0.0625f), quantize(bz, 0.0625f),
                                0.026f, sRgb, 0xFF8A8A, Math.round(alpha * 0.7f), salt + i);
                    }
                }
            }
            case WeaponVfxMotion.SHAPE_MAW -> {
                // crocs : trois mâchoires qui se referment le long du rayon
                for (int jaw = 0; jaw < 3; jaw++) {
                    float u = 0.25f + jaw * 0.25f;
                    float bx = Mth.lerp(u, x0, x1);
                    float by = Mth.lerp(u, y0, y1);
                    float bz = Mth.lerp(u, z0, z1);
                    for (int side = -1; side <= 1; side += 2) {
                        emitSteppedVoxelLine(vc, mat, tmp,
                                bx + px * 0.22f * side, by + 0.20f, bz + pz * 0.22f * side,
                                bx + px * 0.05f * side, by - 0.10f, bz + pz * 0.05f * side,
                                0.030f, (jaw & 1) == 0 ? pRgb : sRgb, 0xFFFFFF, alpha);
                    }
                }
            }
            default -> {
                // estoc fin et net + étoile à l'impact
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1,
                        0.036f * (1.0f - t * 0.45f), 0xFFFFFF, pRgb, alpha);
                emitPixelCross3D(vc, mat, tmp, x1, y1, z1, 0.0625f,
                        Math.min(4, 2 + inst.intensity / 4), 0xFFFFFF, sRgb, alpha);
            }
        }
    }

    // ==================== 3. IMPACTS, CRITIQUES & EFFETS GRANDIOSES EN MINI-PIXELS 3D ====================

    private static void renderVoxelImpact(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          Vec3 cam, VfxInstance inst, float t) {
        float cx = quantize((float) (inst.x - cam.x), 0.0625f);
        float cy = quantize((float) (inst.y - cam.y), 0.0625f);
        float cz = quantize((float) (inst.z - cam.z), 0.0625f);
        int fadeA = Math.round(240.0f * (1.0f - t));
        // T349-PALETTE : trois teintes par enchantement (cœur / halo / accent) au lieu de deux
        int[] pal = palette(inst.effect, inst.variant(), inst.paletteMask);
        int pRgb = pal[0];
        int sRgb = pal[1];
        int aRgb = pal[2];
        // T361-MOTIONS-UNIQUES : la trajectoire propre de cet enchantement est réutilisée ici,
        // pour que l'impact ressemble à SA famille de mouvement (et pas à un anneau générique).
        WeaponVfxMotion.Profile motion = WeaponVfxMotion.of(inst.effect);
        int mSalt = (int) (inst.seed & 0x7FFFFFFFL) + inst.age * 31 + inst.effect.ordinal() * 613;

        switch (inst.effect) {
            case RAZOR_GLEAM -> {
                // #01 T361 : ÉCLAT D'ACIER — une entaille fine qui claque au point d'impact puis
                // s'efface, avec un seul éclat blanc au centre (le "ting" du tranchant).
                emitMotionSlash(vc, mat, tmp, cx, cy, cz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                        (1.05f + 0.30f * t) * motion.scale(), 0.030f, 165.0f, t,
                        pRgb, 0xFFFFFF, aRgb, fadeA, motion, mSalt, inst.effect.ordinal());
                emitVoxelCube(vc, mat, tmp, cx, cy, cz, 0.048f, 0.048f, 0.048f, 0xFFFFFF, fadeA);
            }
            case SUPREME_PRISM_EDGE -> {
                // #02 T361 : PRISME — trois arcs concentriques dont la teinte tourne, cœur aveuglant.
                int hue = Mth.hsvToRgb((inst.age * 0.06f) % 1.0f, 0.55f, 1.0f);
                emitMotionSlash(vc, mat, tmp, cx, cy, cz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                        (1.20f + 0.40f * t) * motion.scale(), 0.034f, 190.0f, t,
                        hue, pRgb, 0xFFFFFF, fadeA, motion, mSalt, inst.effect.ordinal());
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 4, 0xFFFFFF, hue, fadeA);
            }
            case SPECTRAL_REACH -> {
                // #03 T361 : ALLONGE SPECTRALE — la lame s'étire vers la cible en pixels fantômes
                // qui s'éloignent (aucun cercle au sol : la direction EST l'animation).
                float yawR = -inst.yawDeg * Mth.DEG_TO_RAD;
                float dirX = Mth.sin(yawR);
                float dirZ = Mth.cos(yawR);
                float ghost = 1.0f + 1.7f * Mth.clamp(t * 1.6f, 0.0f, 1.0f);
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz,
                        cx + dirX * ghost, cy + 0.05f, cz + dirZ * ghost,
                        0.032f, 0xFFFFFF, pRgb, Math.round(fadeA * 0.85f));
                for (int g2 = 1; g2 <= 3; g2++) {
                    float k = g2 / 3.0f;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + dirX * ghost * k, 0.0625f),
                            quantize(cy + 0.05f + 0.10f * k, 0.0625f),
                            quantize(cz + dirZ * ghost * k, 0.0625f),
                            0.022f, 0.022f, 0.022f, 0xFFFFFF, Math.round(fadeA * (0.9f - 0.2f * k)));
                }
            }
            case TWIN_ECHO_SLASH -> {
                // #04 T361 : DOUBLE RÉMANENCE — l'entaille est rejouée une seconde fois, décalée,
                // avec un écho plus pâle (deux arcs, jamais un anneau).
                emitMotionSlash(vc, mat, tmp, cx, cy, cz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                        (1.15f + 0.35f * t) * motion.scale(), 0.036f, 155.0f, t,
                        pRgb, sRgb, aRgb, fadeA, motion, mSalt, inst.effect.ordinal());
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 2, 0xFFFFFF, sRgb,
                        Math.round(fadeA * 0.6f));
            }
            case MOLTEN_FIRE_WAVE -> {
                // #13 T361 : NAPPE DE BRAISES — 14 flammèches tourbillonnantes qui montent, plus un
                // anneau de cendres : le feu MONTE, il ne tourne pas autour de l'impact.
                for (int e2 = 0; e2 < 14; e2++) {
                    float ang = e2 * 0.4488f + inst.age * 0.10f;
                    float rad = 0.25f + 1.30f * t * (0.6f + 0.5f * pixelHash(e2, e2 * 3));
                    float up = (0.35f + 1.25f * t) * (0.55f + 0.7f * pixelHash(e2, 7));
                    float sway = Mth.sin(inst.age * 0.35f + e2) * 0.12f;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rad + sway, 0.0625f),
                            quantize(cy - 0.35f + up, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rad + sway * 0.7f, 0.0625f),
                            0.030f + 0.014f * pixelHash(e2, 11), 0.034f, 0.030f,
                            (e2 & 2) == 0 ? 0xFFFFFF : ((e2 & 1) == 0 ? pRgb : sRgb),
                            Math.round(fadeA * (1.0f - 0.45f * t)));
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.44f, cz, 0.40f + 1.75f * t,
                        pRgb, sRgb, Math.round(fadeA * 0.75f), PIXEL_RING_DASHED);
            }
            case GOLDEN_CRIT_STAR -> {
                // #06 Étoile Critique Pixel-Art 3D (+ en cubes + vrai cercle pixelisé de Bresenham)
                int arms = (inst.intensity >= 3) ? 4 : 3;
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, arms, 0xFFFFFF, pRgb, fadeA);
                emitPixelCircle3D(vc, mat, tmp, cx, cy, cz, 0.30f + 1.35f * t, pRgb, sRgb, fadeA);
            }
            case FOCUSED_BULLSEYE -> {
                // #07 Mire circulaire pixel-art (Bresenham) faisant face au tireur + 4 crans de visée pixels
                float rad = (t < 0.4f) ? (1.10f - 1.55f * t) : (0.48f + (t - 0.4f) * 1.45f);
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy, cz, rad, sRgb, pRgb, fadeA);
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 2, 0xFFFFFF, sRgb, fadeA);
            }
            case OVERCLOCK_CYBER_X -> {
                // #08 Immense X en escalier de mini-cubes néon cyan & magenta + cercle runique cyber pixelisé !
                float span = 0.85f + 0.55f * t;
                emitSteppedVoxelLine(vc, mat, tmp, cx - span, cy - span, cz, cx + span, cy + span, cz, 0.048f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx - span, cy + span, cz, cx + span, cy - span, cz, 0.048f, 0xFFFFFF, sRgb, fadeA);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.35f, cz, 0.55f + 1.35f * t, -t * 3.0f, pRgb, sRgb, aRgb, fadeA, false);
            }
            case GUILLOTINE_CLEAVE -> {
                // #05 Colonne de cubes d'exécution tranchant de haut en bas + cercle pixel au sol
                float topY = cy + 2.0f * (1.0f - t * 0.35f);
                emitSteppedVoxelLine(vc, mat, tmp, cx, topY, cz, cx, cy - 0.8f, cz, 0.058f, sRgb, pRgb, fadeA);
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.65f, cz, 0.30f + 1.30f * t, pRgb, sRgb, fadeA);
            }
            case DRIPPING_BLOOD_EDGE, CARNAGE_BERSERK -> {
                // #09 / #12 Gouttes reconstruites en PIXELS 3D de tailles différentes (pointe fine, corps
                // large, queue, éclaboussures satellites) — plus aucune goutte lisse ni gros cube unique !
                int count = (inst.effect == WeaponVisualEffectId.CARNAGE_BERSERK) ? 10 : 7;
                for (int i = 0; i < count; i++) {
                    float ang = (float) (i * (Math.PI * 2.0 / count) + (inst.seed & 7));
                    float rad = (0.20f + 1.35f * t) * (0.72f + 0.28f * pixelHash(inst.effect.ordinal(), i * 19));
                    float dy = 0.55f * t - 1.40f * t * t;
                    float px = quantize(cx + Mth.cos(ang) * rad, 0.0625f);
                    float py = quantize(cy + dy, 0.0625f);
                    float pz = quantize(cz + Mth.sin(ang) * rad, 0.0625f);
                    float dropStep = (i % 3 == 0) ? 0.034f : ((i % 3 == 1) ? 0.024f : 0.040f);
                    emitPixelDroplet3D(vc, mat, tmp, px, py, pz, dropStep,
                            (i & 1) == 0 ? pRgb : sRgb, 0xFF8A8A, fadeA, inst.effect.ordinal() * 7 + i);
                    // Gouttelettes plus petites qui se détachent au-dessus de la goutte
                    if ((i & 1) == 0) {
                        emitVoxelCube(vc, mat, tmp, px, py + dropStep * 4.2f, pz,
                                dropStep * 0.30f, dropStep * 0.30f, dropStep * 0.30f, pRgb,
                                Math.round(fadeA * 0.8f));
                        emitVoxelCube(vc, mat, tmp, px + dropStep * 0.9f, py + dropStep * 6.0f, pz,
                                dropStep * 0.20f, dropStep * 0.20f, dropStep * 0.20f, sRgb,
                                Math.round(fadeA * 0.6f));
                    }
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.35f, cz, 0.35f + 1.50f * t, pRgb, sRgb, fadeA);
            }
            case VITAL_TRANSFUSION -> {
                // #11 Double hélice voxel rubis-or + petit cercle runique médical en pixels au sol
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.6f, cz, 0.65f, 1.8f, t * 4.0f, pRgb, sRgb, fadeA);
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.6f, cz, 0.65f, 1.8f, t * 4.0f + (float) Math.PI, sRgb, pRgb, fadeA);
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.45f, cz, 0.75f, sRgb, pRgb, fadeA);
            }
            case SOUL_FIRE_CRESCENT -> {
                // #14 T361 : LANGUES DE FEU D'ÂME — neuf flammes turquoise qui montent en escalier et
                // vacillent (le feu monte), avec le cercle runique d'âme conservé au sol.
                for (int fl = 0; fl < 9; fl++) {
                    float ang = fl * 0.698f + inst.age * 0.05f;
                    float rad = 0.25f + 0.85f * (0.4f + 0.6f * pixelHash(fl, fl * 5));
                    float h = (0.55f + 0.75f * pixelHash(fl, 11)) * (0.55f + 0.75f * t);
                    float bx = cx + Mth.cos(ang) * rad;
                    float bz = cz + Mth.sin(ang) * rad;
                    for (int sg = 0; sg < 5; sg++) {
                        float u = sg / 5.0f;
                        float sway = Mth.sin(u * 5.0f + inst.age * 0.5f + fl) * 0.10f * u;
                        emitVoxelCube(vc, mat, tmp,
                                quantize(bx + sway, 0.0625f),
                                quantize(cy - 0.35f + u * h, 0.0625f),
                                quantize(bz + sway * 0.7f, 0.0625f),
                                0.040f * (1.0f - 0.55f * u), 0.052f * (1.0f - 0.45f * u),
                                0.040f * (1.0f - 0.55f * u),
                                u > 0.55f ? 0xFFFFFF : ((sg & 1) == 0 ? pRgb : sRgb),
                                Math.round(fadeA * (1.0f - 0.25f * u)));
                    }
                }
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.42f, cz, 0.55f + 1.30f * t,
                        t * 2.2f, pRgb, sRgb, aRgb, fadeA, true);
            }
            case BRIMSTONE_METEOR -> {
                // #15 Bloc-météore cubique plongeant du ciel + grand cercle runique infernal en pixels !
                if (t < 0.48f) {
                    float mt = t / 0.48f;
                    float mx = quantize(cx + 3.2f * (1.0f - mt), 0.0625f);
                    float my = quantize(cy + 11.0f * (1.0f - mt), 0.0625f);
                    float mz = quantize(cz + 2.0f * (1.0f - mt), 0.0625f);
                    // Météore reconstruit en amas de pixels 3D (croûte irrégulière + cœur incandescent)
                    emitPixelRockCluster3D(vc, mat, tmp, mx, my, mz, 0.0625f, 10, pRgb, sRgb, 0xFFFFFF, 235,
                            inst.effect.ordinal() * 13 + inst.age);
                    emitSteppedVoxelLine(vc, mat, tmp, mx + 1.0f, my + 3.2f, mz + 0.6f, mx, my, mz,
                            0.070f, sRgb, pRgb, 220);
                }
                float st = Math.max(0.0f, (t - 0.2f) / 0.8f);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.38f, cz, 0.55f + 2.65f * st, t * 1.5f, sRgb, pRgb, 0xFFFFFF, fadeA, true);
            }
            case CHAIN_VOLT_ARC -> {
                // #16 Éclair céleste en escalier de cubes 3D + 3 arcs au sol + onde circulaire pixelisée
                emitVoxelLightning(vc, mat, tmp, cx + 0.3f, cy + 8.0f, cz - 0.3f, cx, cy, cz, 11, 0.35f,
                        inst.seed + inst.age / 2, 0xFFFFFF, pRgb, fadeA, 0.045f);
                for (int b = 0; b < 3; b++) {
                    float a = b * 2.094f + (inst.seed & 3);
                    emitVoxelLightning(vc, mat, tmp, cx, cy, cz,
                            cx + Mth.cos(a) * 1.8f, cy - 0.3f, cz + Mth.sin(a) * 1.8f,
                            5, 0.18f, inst.seed + b * 29L + inst.age, sRgb, pRgb, fadeA, 0.035f);
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.35f, cz, 0.35f + 1.45f * t, 0xFFFFFF, pRgb, fadeA);
            }
            case HYDRO_TIDAL_SURGE -> {
                // #17 T361 : COURONNE DE VAGUES — trois nappes qui montent en ondulant + gerbes de
                // gouttes projetées vers le haut puis retombées (l'eau ondule, elle ne s'enroule pas).
                for (int wv = 0; wv < 3; wv++) {
                    float wt = Math.min(1.0f, t + wv * 0.16f);
                    float rr = 0.35f + (1.35f + 0.25f * wv) * wt;
                    float yy = cy - 0.40f + wt * (0.32f + 0.16f * wv);
                    emitPixelCircle3D(vc, mat, tmp, cx, yy, cz, rr,
                            (wv & 1) == 0 ? pRgb : sRgb, 0xE8F8FF,
                            Math.round(fadeA * (0.95f - 0.20f * wv)));
                }
                for (int d = 0; d < 12; d++) {
                    float ang = d * 0.5236f + inst.age * 0.08f;
                    float rad = 0.35f + 1.15f * t;
                    float up = 0.55f * Mth.sin(Math.min(1.0f, t * 1.3f) * (float) Math.PI)
                            * (0.6f + 0.4f * pixelHash(d, d * 7));
                    emitPixelDroplet3D(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rad, 0.0625f),
                            quantize(cy - 0.30f + up, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rad, 0.0625f),
                            0.030f + 0.012f * pixelHash(d, 3), pRgb, 0xE8F8FF, fadeA, d * 13);
                }
            }
            case ABYSSAL_MAELSTROM -> {
                // #18 Vortex océanique de mini-cubes 3D tournoyants à 4 bras + cercle pixel abyssal
                for (int arm = 0; arm < 4; arm++) {
                    float baseAng = arm * ((float) Math.PI * 0.5f) + t * 6.0f;
                    for (int s = 1; s <= 9; s++) {
                        float u = s / 9.0f;
                        float r = Mth.lerp(u, 0.25f, 2.6f * (1.0f - 0.2f * t));
                        float a = baseAng + u * 2.6f;
                        float vx = quantize(cx + Mth.cos(a) * r, 0.0625f);
                        float vy = quantize(cy - 0.35f + (1.0f - u) * 0.55f, 0.0625f);
                        float vz = quantize(cz + Mth.sin(a) * r, 0.0625f);
                        emitVoxelCube(vc, mat, tmp, vx, vy, vz, 0.052f * (1.15f - 0.4f * u), 0.052f, 0.052f,
                                (s & 1) == 0 ? sRgb : pRgb, fadeA);
                    }
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.38f, cz, 0.45f + 2.05f * t, pRgb, sRgb, fadeA);
            }
            case PERMAFROST_RIME -> {
                // #19 Piliers de cristaux cubiques de glace + cercle runique givré en pixels !
                for (int k = 0; k < 6; k++) {
                    float a = k * ((float) Math.PI / 3.0f);
                    float r = 0.40f + 1.45f * t;
                    float px = quantize(cx + Mth.cos(a) * r, 0.0625f);
                    float pz = quantize(cz + Mth.sin(a) * r, 0.0625f);
                    float h = 0.38f * (1.0f - t * 0.5f);
                    // Cristal en pixels 3D empilés qui s'affinent vers la pointe (jamais un bloc unique)
                    int segs = 4;
                    for (int seg = 0; seg < segs; seg++) {
                        float su = seg / (float) segs;
                        float segS = 0.0625f * (1.0f - 0.62f * su);
                        float sy = cy - 0.3f + h * (su + 0.5f / segs);
                        emitVoxelCube(vc, mat, tmp, px, sy, pz,
                                segS, h / segs * 0.52f, segS,
                                (k & 1) == 0 ? sRgb : pRgb, fadeA);
                        emitVoxelCube(vc, mat, tmp, px + segS * 0.85f, sy + h / segs * 0.16f, pz - segS * 0.7f,
                                segS * 0.48f, h / segs * 0.3f, segS * 0.48f, 0xFFFFFF,
                                Math.round(fadeA * 0.55f));
                    }
                    emitVoxelCube(vc, mat, tmp, px, cy - 0.3f + h + 0.02f, pz,
                            0.0125f, 0.020f, 0.0125f, 0xFFFFFF, Math.round(fadeA * 0.9f));
                }
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.38f, cz, 0.45f + 1.45f * t, 0.0f, sRgb, pRgb, 0xFFFFFF, fadeA, false);
            }
            case VENOM_BRIAR_FANG -> {
                // #20 T361 : MÂCHOIRE VENIMEUSE — trois paires de crocs qui jaillissent du sol et se
                // referment sur la cible, avec un suintement de venin aux pointes (plus d'hélice).
                for (int jaw = 0; jaw < 3; jaw++) {
                    float ang = jaw * 2.094f + 0.40f;
                    float baseR = 0.55f + 0.35f * (1.0f - t);
                    float closeK = 1.0f - 0.55f * t;
                    for (int side = -1; side <= 1; side += 2) {
                        float ca2 = Mth.cos(ang) * side;
                        float sa2 = Mth.sin(ang) * side;
                        emitSteppedVoxelLine(vc, mat, tmp,
                                cx + ca2 * baseR, cy - 0.55f, cz + sa2 * baseR,
                                cx + ca2 * 0.12f * closeK, cy + 0.62f, cz + sa2 * 0.12f * closeK,
                                0.036f, sRgb, pRgb, fadeA);
                        emitPixelDroplet3D(vc, mat, tmp,
                                quantize(cx + ca2 * 0.16f, 0.0625f),
                                quantize(cy + 0.55f - 0.25f * t, 0.0625f),
                                quantize(cz + sa2 * 0.16f, 0.0625f),
                                0.026f, pRgb, 0xB6FF7A, Math.round(fadeA * 0.8f), jaw * 31 + side);
                    }
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.48f, cz, 0.40f + 1.25f * t,
                        sRgb, pRgb, Math.round(fadeA * 0.7f), PIXEL_RING_DASHED);
            }
            case SEISMIC_TECTONIC -> {
                // #21 Failles de cubes rocheux soulevés sur le sol + onde sismique circulaire en pixels
                float reach = 0.5f + 2.9f * Mth.sqrt(t);
                for (int f = 0; f < 6; f++) {
                    float a = f * ((float) Math.PI / 3.0f) + 0.2f;
                    emitSteppedVoxelLine(vc, mat, tmp, cx, cy - 0.42f, cz,
                            cx + Mth.cos(a) * reach, cy - 0.42f, cz + Mth.sin(a) * reach,
                            0.058f, sRgb, pRgb, fadeA);
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.40f, cz, reach * 0.78f, pRgb, sRgb, fadeA);
            }
            case ZEPHYR_WIND_LANCE -> {
                // #22 T361 : ENROULEMENT DE VENT — trois bras spiraux qui tournent en s'élevant
                // (le vent s'enroule) puis un cœur clair au centre ; plus deux anneaux plats.
                for (int arm = 0; arm < 3; arm++) {
                    float baseAng = arm * 2.094f + inst.age * 0.35f;
                    for (int sg = 1; sg <= 10; sg++) {
                        float u = sg / 10.0f;
                        float ang = baseAng + u * 3.4f;
                        float rad = (0.20f + 1.35f * u) * (0.45f + 0.75f * t);
                        emitVoxelCube(vc, mat, tmp,
                                quantize(cx + Mth.cos(ang) * rad, 0.0625f),
                                quantize(cy - 0.35f + u * 1.45f * (0.5f + 0.7f * t), 0.0625f),
                                quantize(cz + Mth.sin(ang) * rad, 0.0625f),
                                0.032f * (1.2f - 0.5f * u), 0.028f, 0.032f * (1.2f - 0.5f * u),
                                (sg & 1) == 0 ? pRgb : 0xFFFFFF,
                                Math.round(fadeA * (1.0f - 0.35f * u)));
                    }
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 2, 0xFFFFFF, pRgb,
                        Math.round(fadeA * 0.8f));
            }
            case SERAPH_SKY_PILLAR -> {
                // #23 Colonne céleste blocky + VRAI CERCLE RUNIQUE SACRÉ EN PIXELS 3D au sol et en élévation !
                float hw = 0.26f * Mth.sin(Math.min(1.0f, t * 1.3f) * (float) Math.PI);
                if (hw > 0.02f) {
                    // Colonne céleste reconstruite en pixels 3D empilés de tailles différentes : cœur blanc
                    // crénelé, halo de micro-pixels colorés et étincelles — aucun gros bloc rectangulaire.
                    int layers = 30;
                    for (int li = 0; li < layers; li++) {
                        float lu = li / (float) (layers - 1);
                        float ly = cy + 0.15f + lu * 10.8f;
                        float bulge = 0.35f + 0.85f * Mth.sin(lu * (float) Math.PI);
                        float spread = hw * bulge;
                        float jx = (pixelHash(inst.effect.ordinal() + li, li * 31) - 0.5f) * spread * 1.35f;
                        float jz = (pixelHash(li * 7 + 3, li * 17) - 0.5f) * spread * 1.35f;
                        float px = quantize(cx + jx, 0.0625f);
                        float pz = quantize(cz + jz, 0.0625f);
                        float coreS = 0.028f + 0.030f * bulge;
                        emitVoxelCube(vc, mat, tmp, px, ly, pz, coreS,
                                coreS * (1.05f + 0.35f * pixelHash(li, li * 5)), coreS, 0xFFFFFF, fadeA);
                        int haloA = Math.round(fadeA * 0.42f);
                        emitVoxelCube(vc, mat, tmp, quantize(px + spread, 0.0625f), ly + 0.02f, pz,
                                coreS * 0.72f, coreS * 0.9f, coreS * 0.72f, sRgb, haloA);
                        emitVoxelCube(vc, mat, tmp, px, ly + 0.01f, quantize(pz - spread, 0.0625f),
                                coreS * 0.60f, coreS * 0.78f, coreS * 0.60f, pRgb, Math.round(haloA * 0.9f));
                        if ((li & 3) == 0) {
                            emitVoxelCube(vc, mat, tmp, quantize(px, 0.03125f), ly + coreS * 2.2f, pz,
                                    coreS * 0.26f, coreS * 0.26f, coreS * 0.26f, 0xFFFFFF,
                                    Math.round(fadeA * 0.6f));
                        }
                    }
                }
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.38f, cz, 0.65f + 1.75f * Math.min(1.0f, t * 1.6f),
                        t * 1.8f, pRgb, sRgb, aRgb, fadeA, true);
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.20f + 2.4f * t, cz, 0.55f + 1.15f * t, 0xFFFFFF, sRgb, fadeA);
            }
            case WITHER_SHADOW_RIFT -> {
                // #24 Triple griffure d'ombre en escalier de mini-pixels violets-noirs
                for (int claw = -1; claw <= 1; claw++) {
                    float off = claw * 0.28f;
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx - 0.65f + off, cy + 0.75f, cz - 0.30f,
                            cx + 0.65f + off, cy - 0.65f, cz + 0.30f,
                            0.044f, sRgb, pRgb, fadeA);
                }
            }
            case ASTRAL_STAR_LANCE -> {
                // #25 Impact stellaire : étoile 3D + cercle runique astral en pixels !
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.35f, cz, 0.50f + 1.35f * t, -t * 2.4f, pRgb, sRgb, aRgb, fadeA, true);
                emitPixelCross3D(vc, mat, tmp, cx, cy + 0.1f, cz, 0.0625f, 4, 0xFFFFFF, pRgb, fadeA);
            }
            case VOID_WARP_SPHERE -> {
                // #26 Cercle runique du Vide en pixels 3D (implosion/explosion) + croix abyssale
                float r = 0.40f + 1.45f * Mth.sin(t * (float) Math.PI);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.20f, cz, r, -t * 3.5f, pRgb, sRgb, aRgb, fadeA, true);
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy + 0.1f, cz, r * 0.65f, sRgb, pRgb, fadeA);
                emitPixelCross3D(vc, mat, tmp, cx, cy + 0.1f, cz, 0.0625f, 3, sRgb, pRgb, fadeA);
            }
            case DRAGON_CHAOS_PRISM -> {
                // #27 Cercle runique draconique arc-en-ciel en pixels 3D !
                int c1 = Mth.hsvToRgb((t * 1.5f) % 1.0f, 0.8f, 1.0f);
                int c2 = Mth.hsvToRgb((t * 1.5f + 0.35f) % 1.0f, 0.85f, 1.0f);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.38f, cz, 0.55f + 2.15f * t, t * 2.6f, c1, c2, 0xFFFFFF, fadeA, true);
                emitPixelCross3D(vc, mat, tmp, cx, cy + 0.2f, cz, 0.0625f, 4, 0xFFFFFF, c1, fadeA);
            }
            case CHRONO_ETERNAL_HALO -> {
                // #28 Cadran d'horloge runique en pixels 3D (double cercle de Bresenham + 8 runes + aiguilles pixels) !
                float r = 1.05f;
                float hy = cy + 0.65f;
                emitPixelRunicCircle3D(vc, mat, tmp, cx, hy, cz, r, t * 0.8f, pRgb, sRgb, aRgb, fadeA, false);
                float aFast = t * 8.5f;
                float aSlow = t * 2.2f;
                emitSteppedVoxelLine(vc, mat, tmp, cx, hy, cz,
                        cx + Mth.cos(aFast) * r * 0.58f, hy, cz + Mth.sin(aFast) * r * 0.58f,
                        0.032f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx, hy, cz,
                        cx + Mth.cos(aSlow) * r * 0.40f, hy, cz + Mth.sin(aSlow) * r * 0.40f,
                        0.038f, sRgb, pRgb, fadeA);
            }
            case WARLORD_RALLY_SIGIL -> {
                // #29 Grand Cercle Runique du Chef de Guerre en pixels 3D (double anneau Bresenham + 8 runes 3x3 + étoile) !
                float r = 2.05f * Math.min(1.0f, t * 2.4f);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.40f, cz, r, t * 1.6f, pRgb, sRgb, aRgb, fadeA, true);
            }
            case VERDANT_SAP_BLOOM -> {
                // #38 T352b Bois (BRUN) : éclats d'écorce projetés vers le haut + pousses qui
                // s'ouvrent, gouttes de sève ambrée — aucune teinte verte (le vert = poison).
                for (int i = 0; i < 7; i++) {
                    float ang = i * 0.897f + (inst.seed & 7);
                    float rad = 0.25f + 1.55f * Mth.sqrt(t);
                    float px = quantize(cx + Mth.cos(ang) * rad, 0.0625f);
                    float pz = quantize(cz + Mth.sin(ang) * rad, 0.0625f);
                    float py = quantize(cy - 0.30f + 0.55f * t + (i % 3) * 0.06f, 0.0625f);
                    // éclat d'écorce (bois sombre) surmonté d'une goutte de sève claire
                    emitVoxelCube(vc, mat, tmp, px, py, pz, 0.040f, 0.030f, 0.040f,
                            (i & 1) == 0 ? sRgb : pRgb, fadeA);
                    emitVoxelCube(vc, mat, tmp, px, py + 0.055f, pz, 0.022f, 0.026f, 0.022f,
                            0xE8C089, Math.round(fadeA * 0.85f));
                }
                // jeunes pousses : deux tiges en escalier de part et d'autre de l'impact
                for (int side = -1; side <= 1; side += 2) {
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx + side * 0.35f, cy - 0.45f, cz,
                            cx + side * (0.55f + 0.35f * t), cy + 0.55f, cz + side * 0.10f,
                            0.030f, pRgb, sRgb, fadeA);
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.42f, cz, 0.35f + 1.25f * t,
                        pRgb, sRgb, Math.round(fadeA * 0.75f));
            }
            case MIASMA_SHROUD -> {
                // #39 T352b Poison (VERT FONCÉ) : voile de miasme — trois anneaux qui montent et
                // s'élargissent, plus des spores qui flottent (mode alpha, pas de flash additif).
                for (int ringI = 0; ringI < 3; ringI++) {
                    float rt = Math.min(1.0f, t + ringI * 0.18f);
                    emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.35f + rt * 0.85f, cz,
                            0.35f + 1.45f * rt, pRgb, sRgb, Math.round(fadeA * (0.8f - 0.18f * ringI)));
                }
                for (int sp = 0; sp < 10; sp++) {
                    float ang = sp * 0.628f + (inst.seed & 15) * 0.1f;
                    float rad = (0.30f + 0.95f * t) * (0.6f + 0.4f * pixelHash(sp, sp * 11));
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rad, 0.0625f),
                            quantize(cy - 0.10f + t * (0.55f + 0.3f * pixelHash(sp * 3, sp)), 0.0625f),
                            quantize(cz + Mth.sin(ang) * rad, 0.0625f),
                            0.028f, 0.028f, 0.028f,
                            (sp & 1) == 0 ? pRgb : sRgb, Math.round(fadeA * 0.8f));
                }
            }
            case PLAGUE_NOVA -> {
                // #40 T352b Poison : nova venimeuse — 10 aiguillons jaillissent du sol puis retombent,
                // anneau de contamination au sol et cœur sombre.
                for (int i = 0; i < 10; i++) {
                    float ang = i * 0.628f;
                    float out = 0.25f + 1.85f * Mth.sqrt(t);
                    float h = 0.75f * Mth.sin(Math.min(1.0f, t * 1.4f) * (float) Math.PI);
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx + Mth.cos(ang) * out * 0.55f, cy - 0.45f, cz + Mth.sin(ang) * out * 0.55f,
                            cx + Mth.cos(ang) * out, cy - 0.45f + h, cz + Mth.sin(ang) * out,
                            0.034f, sRgb, pRgb, fadeA);
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.44f, cz, 0.40f + 1.70f * t,
                        pRgb, sRgb, Math.round(fadeA * 0.85f), PIXEL_RING_DASHED);
                emitPixelCross3D(vc, mat, tmp, cx, cy - 0.05f, cz, 0.0625f, 3, sRgb, pRgb, fadeA);
            }
            case THORN_MAW_ROOT -> {
                // #31 T352 Bois : 8 ronces en escalier qui montent du sol puis se referment vers le haut,
                // + anneau de spores vert sombre (aucune forme pleine, uniquement des pixels 3D).
                int roots = 8;
                for (int i = 0; i < roots; i++) {
                    float ang = i * (float) (Math.PI * 2.0 / roots) + 0.25f;
                    float baseR = 0.55f + 0.55f * (1.0f - t);
                    float topR = 0.12f + 0.40f * t;
                    float bx = cx + Mth.cos(ang) * baseR;
                    float bz = cz + Mth.sin(ang) * baseR;
                    float tx2 = cx + Mth.cos(ang + 0.5f) * topR;
                    float tz2 = cz + Mth.sin(ang + 0.5f) * topR;
                    emitSteppedVoxelLine(vc, mat, tmp, bx, cy - 0.55f, bz, tx2, cy + 0.85f, tz2,
                            0.040f, sRgb, pRgb, fadeA);
                    // épines : petits pixels qui dépassent de la ronce
                    if ((i & 1) == 0) {
                        emitVoxelCube(vc, mat, tmp, Mth.lerp(0.55f, bx, tx2), cy + 0.10f, Mth.lerp(0.55f, bz, tz2),
                                0.026f, 0.032f, 0.026f, 0xFFFFFF, Math.round(fadeA * 0.75f));
                    }
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.52f, cz, 0.45f + 1.35f * t, pRgb, sRgb, Math.round(fadeA * 0.85f));
            }
            case SANDSTORM_DUNE -> {
                // #32 T352 Terre : tempête de sable — 3 bras en spirale qui tournent en s'élargissant,
                // grains épars au sol et dune soulevée en pixels (ocre, non additif = poussière lisible).
                for (int armI = 0; armI < 3; armI++) {
                    float baseAng = armI * 2.094f + t * 7.5f;
                    for (int seg = 1; seg <= 8; seg++) {
                        float u = seg / 8.0f;
                        float rad = Mth.lerp(u, 0.30f, 2.35f * (0.35f + t));
                        float ang = baseAng + u * 2.8f;
                        float px = quantize(cx + Mth.cos(ang) * rad, 0.0625f);
                        float pz = quantize(cz + Mth.sin(ang) * rad, 0.0625f);
                        float py = quantize(cy - 0.25f + u * (1.15f * t), 0.0625f);
                        emitVoxelCube(vc, mat, tmp, px, py, pz, 0.038f * (1.2f - 0.5f * u),
                                0.030f, 0.038f * (1.2f - 0.5f * u),
                                (seg & 1) == 0 ? pRgb : sRgb, fadeA);
                    }
                }
                emitPixelCircle3D(vc, mat, tmp, cx, cy - 0.48f, cz, 0.55f + 1.85f * t, sRgb, pRgb, Math.round(fadeA * 0.8f));
            }
            case ECLIPSE_VEIL -> {
                // #33 T352 Ombre : éclipse — disque noir pixelisé (cercle de Bresenham) qui grossit,
                // voile en spirale et triple griffure violette.
                float disc = 0.55f + 1.25f * t;
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy + 0.35f, cz, disc, sRgb, 0x000000, fadeA);
                emitPixelCircle3D(vc, mat, tmp, cx, cy + 0.35f, cz, disc * 0.72f, 0x000000, sRgb,
                        Math.round(fadeA * 0.85f));
                for (int claw = -1; claw <= 1; claw++) {
                    float off = claw * 0.26f + 0.10f * t;
                    emitSteppedVoxelLine(vc, mat, tmp,
                            cx - 0.70f + off, cy + 0.85f, cz - 0.35f,
                            cx + 0.70f + off, cy - 0.55f, cz + 0.35f,
                            0.040f, pRgb, sRgb, fadeA);
                }
                emitPixelRing3D(vc, mat, tmp, cx, cy - 0.50f, cz, 0.40f + 1.55f * t, pRgb, sRgb, fadeA, PIXEL_RING_DASHED);
            }
            case NULL_RIFT_FISSURE -> {
                // #34 T352 Vide : faille verticale crénelée qui s'ouvre puis se referme, avec
                // aspiration circulaire et éclats de vide qui filent vers l'intérieur.
                float open = Mth.sin(Math.min(1.0f, t * 1.15f) * (float) Math.PI);
                float half = 0.05f + 0.70f * open;
                for (int side = -1; side <= 1; side += 2) {
                    for (int segI = 0; segI < 6; segI++) {
                        float u = segI / 5.0f;
                        float w = half * (0.35f + 0.65f * Mth.sin(u * (float) Math.PI));
                        float jx = (pixelHash(inst.effect.ordinal() + segI, side * 13) - 0.5f) * 0.22f;
                        emitVoxelCube(vc, mat, tmp,
                                quantize(cx + side * w + jx, 0.0625f),
                                quantize(cy - 1.10f + u * 2.35f, 0.0625f),
                                quantize(cz + side * 0.05f, 0.0625f),
                                0.032f, 0.045f, 0.032f,
                                (segI & 1) == 0 ? pRgb : sRgb, fadeA);
                    }
                }
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.45f, cz, 0.35f + 1.95f * t,
                        -t * 4.2f, aRgb, pRgb, 0xFFFFFF, fadeA, true);
                for (int k = 0; k < 6; k++) {
                    float ang = k * 1.047f + t * 3.0f;
                    float rad = 1.45f * (1.0f - t);
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rad, 0.0625f),
                            quantize(cy + 0.25f + Mth.sin(ang * 2.0f) * 0.45f, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rad, 0.0625f),
                            0.026f, 0.026f, 0.026f, 0xFFFFFF, fadeA);
                }
            }
            case CHAOS_KALEIDO_LOOP -> {
                // #35 T352 Chaos : boucle kaléidoscopique — trois anneaux runiques qui tournent
                // dans des teintes différentes sur un fond sombre, plus éclats multicolores.
                int hueBase = Mth.hsvToRgb((t * 1.35f) % 1.0f, 0.9f, 1.0f);
                int hueAlt = Mth.hsvToRgb((t * 1.35f + 0.33f) % 1.0f, 0.9f, 1.0f);
                int hueAlt2 = Mth.hsvToRgb((t * 1.35f + 0.66f) % 1.0f, 0.9f, 1.0f);
                emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.40f, cz, 0.55f + 2.10f * t,
                        t * 2.4f, hueBase, sRgb, hueAlt, fadeA, true);
                emitPixelRing3D(vc, mat, tmp, cx, cy + 0.35f, cz, 0.45f + 1.45f * t,
                        hueAlt, hueAlt2, Math.round(fadeA * 0.85f), PIXEL_RING_DASHED);
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy + 0.35f, cz, 0.30f + 1.05f * t,
                        hueAlt2, hueBase, Math.round(fadeA * 0.70f));
                emitPixelCross3D(vc, mat, tmp, cx, cy + 0.20f, cz, 0.0625f, 4, 0xFFFFFF, hueBase, fadeA);
            }
            case ENTROPY_SPHERE -> {
                // #36 T352 Chaos : sphère d'entropie — 3 cercles orthogonaux (équateur, méridiens)
                // aux teintes différentes + hélice intérieure : volume bariolé, base sombre.
                int hueA = Mth.hsvToRgb((t * 1.1f) % 1.0f, 0.85f, 1.0f);
                int hueB = Mth.hsvToRgb((t * 1.1f + 0.33f) % 1.0f, 0.85f, 1.0f);
                int hueC = Mth.hsvToRgb((t * 1.1f + 0.66f) % 1.0f, 0.85f, 1.0f);
                float rad = 0.45f + 1.25f * Mth.sin(t * (float) Math.PI);
                emitPixelRing3D(vc, mat, tmp, cx, cy, cz, rad, hueA, hueB, fadeA, PIXEL_RING_SOLID);
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy, cz, rad, hueB, hueC, Math.round(fadeA * 0.8f));
                emitPixelCircleBillboard3D(vc, mat, tmp, cx, cy, cz, rad * 0.92f, hueC, hueA,
                        Math.round(fadeA * 0.55f));
                emitVoxelHelix(vc, mat, tmp, cx, cy - rad, cz, rad * 0.55f, rad * 2.0f, t * 5.5f,
                        hueA, 0xFFFFFF, fadeA);
            }
            case BIG_BANG_SCAR -> {
                // #37 T352 Chaos : déchirure du big bang — 12 rayons bariolés qui jaillissent d'un
                // cœur blanc, anneau noir d'effondrement et éclats projetés très loin.
                int rays = 12;
                for (int i = 0; i < rays; i++) {
                    float ang = i * (float) (Math.PI * 2.0 / rays) + t * 0.6f;
                    float len = (0.55f + 2.9f * t) * ((i & 1) == 0 ? 1.0f : 0.72f);
                    int hue = Mth.hsvToRgb((t * 1.9f + i / (float) rays) % 1.0f, 0.9f, 1.0f);
                    float elev = (i % 3 == 0) ? 0.55f : ((i % 3 == 1) ? -0.35f : 0.05f);
                    emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz,
                            cx + Mth.cos(ang) * len, cy + elev * len * 0.55f, cz + Mth.sin(ang) * len,
                            0.045f * (1.0f - 0.45f * t), 0xFFFFFF, hue, fadeA);
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 5, 0xFFFFFF, 0xFFFFFF, fadeA);
                emitPixelRing3D(vc, mat, tmp, cx, cy, cz, 0.45f + 2.55f * t,
                        0x000000, sRgb, Math.round(fadeA * 0.75f), PIXEL_RING_DASHED);
                for (int k = 0; k < 8; k++) {
                    float ang = k * 0.785f - t * 2.5f;
                    float rad = 3.2f * t;
                    emitVoxelCube(vc, mat, tmp,
                            quantize(cx + Mth.cos(ang) * rad, 0.0625f),
                            quantize(cy + (pixelHash(k, k * 7) - 0.5f) * 1.4f, 0.0625f),
                            quantize(cz + Mth.sin(ang) * rad, 0.0625f),
                            0.028f, 0.028f, 0.028f,
                            Mth.hsvToRgb((t * 2.4f + k * 0.12f) % 1.0f, 0.85f, 1.0f), fadeA);
                }
            }
            default -> {
                float maxR = (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) ? 3.1f : 1.65f;
                if (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) {
                    emitPixelRunicCircle3D(vc, mat, tmp, cx, cy - 0.35f, cz, 0.45f + maxR * t, t * 3.0f, aRgb, pRgb, sRgb, fadeA, true);
                } else {
                    emitPixelCircle3D(vc, mat, tmp, cx, cy, cz, 0.25f + maxR * t, pRgb, sRgb, fadeA);
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 3, 0xFFFFFF, pRgb, fadeA);
            }
        }
    }

    // ==================== PRIMITIVES VOXELS 3D (MINI-CUBES 6 FACES MINECRAFT) ====================

    /**
     * Dessine un vrai mini-cube 3D à 6 faces alignées sur la grille avec l'ombrage directionnel
     * exact de Minecraft (UP 100 %, N/S 84 %, E/W 72 %, DOWN 60 %).
     */
    public static void emitVoxelCube(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz,
                                     float hx, float hy, float hz,
                                     int rgb, int alpha) {
        if (alpha <= 5 || hx <= 0.0005f || hy <= 0.0005f || hz <= 0.0005f) return;
        if (alphaFloor > 0 && alpha < alphaFloor) return;
        if (cubesSinceFlush >= HARD_CUBE_CAP_PER_FRAME) return;
        cubesSinceFlush++;

        // T349-PIXEL-VARIATION : chaque pixel est rapetissé/agrandi aléatoirement MAIS de façon lisse
        // dans le temps (bruit sinusoïdal déterministe par position) : la taille respire doucement au
        // lieu de rester un gros bloc figé.
        float scale = framePixelScale * smoothPulse(cx, cy, cz) * variantScale;
        hx *= scale;
        hy *= scale;
        hz *= scale;

        // T350-VFX-VARIATIONS : allègement contrôlé (les contours lumineux restent toujours dessinés)
        if (pixelDensity < 0.999f && alpha < 200) {
            float keep = pixelHash(0x5EED, (int) (cx * 977.0f + cy * 613.0f + cz * 389.0f));
            if (keep > pixelDensity) return;
        }

        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        int rTop = rgb;
        int rNS = shadeRgb(rgb, 0.85f);
        int rEW = shadeRgb(rgb, 0.73f);
        int rBot = shadeRgb(rgb, 0.62f);

        // UP
        putQuad(vc, mat, tmp, x0, y1, z0, rTop, alpha, x0, y1, z1, rTop, alpha, x1, y1, z1, rTop, alpha, x1, y1, z0, rTop, alpha);
        // DOWN
        putQuad(vc, mat, tmp, x0, y0, z1, rBot, alpha, x0, y0, z0, rBot, alpha, x1, y0, z0, rBot, alpha, x1, y0, z1, rBot, alpha);
        // NORTH
        putQuad(vc, mat, tmp, x1, y1, z0, rNS, alpha, x1, y0, z0, rNS, alpha, x0, y0, z0, rNS, alpha, x0, y1, z0, rNS, alpha);
        // SOUTH
        putQuad(vc, mat, tmp, x0, y1, z1, rNS, alpha, x0, y0, z1, rNS, alpha, x1, y0, z1, rNS, alpha, x1, y1, z1, rNS, alpha);
        // WEST
        putQuad(vc, mat, tmp, x0, y1, z0, rEW, alpha, x0, y0, z0, rEW, alpha, x0, y0, z1, rEW, alpha, x0, y1, z1, rEW, alpha);
        // EAST
        putQuad(vc, mat, tmp, x1, y1, z1, rEW, alpha, x1, y0, z1, rEW, alpha, x1, y0, z0, rEW, alpha, x1, y1, z0, rEW, alpha);
    }

    /**
     * T349-PIXEL-VARIATION : pulsation lente et déterministe (0.72 .. 1.12) propre à chaque position.
     * Comme elle dérive du temps de frame, la variation est parfaitement lisse (aucun scintillement).
     */
    private static float smoothPulse(float x, float y, float z) {
        float h = x * 12.9898f + y * 78.233f + z * 37.719f;
        float n = h - (float) Math.floor(h * 0.15915494f) * 6.2831855f;
        float t = frameTimeSeconds;
        return 0.92f + 0.11f * Mth.sin(t * 1.15f + n) + 0.09f * Mth.sin(t * 0.41f + n * 2.3f);
    }

    /**
     * T349-PALETTE : palette vivante (cœur, halo, accent saturé) dérivée de l'enchantement et tournée
     * par variante. Les entailles et traînées de flèches ne sont donc plus monochromes : chaque
     * enchantement a son dégradé propre et chaque déclenchement décale légèrement la teinte.
     */
    public static int[] palette(WeaponVisualEffectId fx, int variant) {
        return palette(fx, variant, 0);
    }

    public static int[] palette(WeaponVisualEffectId fx, int variant, int paletteMask) {
        // T350-ELEMENT-COLORS : couleur de la FAMILLE ÉLÉMENTAIRE (feu = rouge, poison = vert,
        // eau = bleu, foudre = jaune, métal = argent, ombre = violet...), mélangée à l'identité
        // propre de l'effet, puis lissée dans le temps.
        // T351-ELEMENT-BLEND : si l'arme porte plusieurs éléments, cœur et halo parcourent la
        // palette en OPPOSITION DE PHASE -> les deux teintes (feu + eau) sont visibles ensemble.
        int target = (paletteMask == 0) ? fx.primaryRgb
                : paletteColor(paletteMask, fx.primaryRgb, palettePhase(frameTimeSeconds));
        int glowTarget = (paletteMask == 0) ? fx.secondaryRgb
                : paletteColor(paletteMask, fx.secondaryRgb, palettePhase(frameTimeSeconds + 3.1f));
        int core = smoothColor(fx.id, target);
        int glow = smoothColor(1000 + fx.id, glowTarget);
        float shift = ((variant & 7) - 3.5f) * 0.010f;
        int accent = rotateHue(glow, 0.10f + shift);
        return new int[]{core, glow, accent};
    }

    /**
     * T350-SMOOTH-COLOR : renvoie la couleur courante d'une clé, en la faisant glisser doucement vers
     * sa cible (~0,5 s de transition). Plus aucun changement de teinte brutal d'un coup à l'autre.
     */
    public static int smoothColor(int key, int target) {
        int tr = (target >> 16) & 0xFF, tg = (target >> 8) & 0xFF, tb = target & 0xFF;
        float[] cur = SMOOTH_COLORS.get(key);
        if (cur == null) {
            cur = new float[]{(float) tr, (float) tg, (float) tb, -1.0f};
            SMOOTH_COLORS.put(key, cur);
            if (SMOOTH_COLORS.size() > 256) SMOOTH_COLORS.clear();
            return target;
        }
        if ((int) cur[3] == frameIndex) {
            return (Math.round(cur[0]) << 16) | (Math.round(cur[1]) << 8) | Math.round(cur[2]);
        }
        float k = 0.055f; // par frame (~0,5 s à 60 fps)
        cur[0] += (tr - cur[0]) * k;
        cur[1] += (tg - cur[1]) * k;
        cur[2] += (tb - cur[2]) * k;
        cur[3] = frameIndex;
        return (Math.round(cur[0]) << 16) | (Math.round(cur[1]) << 8) | Math.round(cur[2]);
    }

    /** Couleur lissée utilisée par le rendu de l'arme tenue (dégradé progressif, jamais de saut). */
    public static int smoothBladeColor(WeaponVisualEffectId fx) {
        return smoothColor(fx.id, fx.primaryRgb);
    }

    public static int smoothBladeColor2(WeaponVisualEffectId fx) {
        return smoothColor(1000 + fx.id, fx.secondaryRgb);
    }

    /** Rotation de teinte HSV légère d'une couleur RGB 0xRRGGBB. */
    public static int rotateHue(int rgb, float deltaHue) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float max = Math.max(r, Math.max(g, b)) / 255.0f;
        float min = Math.min(r, Math.min(g, b)) / 255.0f;
        float v = max;
        float sat = max <= 0.0f ? 0.0f : (max - min) / max;
        float h;
        float d = max - min;
        if (d <= 0.00001f) {
            h = 0.0f;
        } else if (max == r / 255.0f) {
            h = ((g - b) / 255.0f) / d;
            if (h < 0.0f) h += 6.0f;
            h /= 6.0f;
        } else if (max == g / 255.0f) {
            h = (((b - r) / 255.0f) / d + 2.0f) / 6.0f;
        } else {
            h = (((r - g) / 255.0f) / d + 4.0f) / 6.0f;
        }
        h = (h + deltaHue) % 1.0f;
        if (h < 0.0f) h += 1.0f;
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float pp = v * (1.0f - sat);
        float q = v * (1.0f - sat * f);
        float t = v * (1.0f - sat * (1.0f - f));
        float rr, gg, bb;
        switch (i % 6) {
            case 0 -> { rr = v; gg = t; bb = pp; }
            case 1 -> { rr = q; gg = v; bb = pp; }
            case 2 -> { rr = pp; gg = v; bb = t; }
            case 3 -> { rr = pp; gg = q; bb = v; }
            case 4 -> { rr = t; gg = pp; bb = v; }
            default -> { rr = v; gg = pp; bb = q; }
        }
        return ((int) (rr * 255) << 16) | ((int) (gg * 255) << 8) | (int) (bb * 255);
    }

    /** T345-PIXEL-DETAIL : micro-aléa déterministe (0..1) — toutes les tailles de pixels restent stables d'une frame à l'autre. */
    private static float pixelHash(int salt, int a) {
        int h = salt * 0x9E3779B1 ^ (a * 0x85EBCA77);
        h ^= (h >>> 15);
        h *= 0x2545F491;
        h ^= (h >>> 13);
        return (h & 0xFFFF) / 65535.0f;
    }

    private static final int PIXEL_RING_SOLID  = 0;
    private static final int PIXEL_RING_DASHED = 1;
    private static final int PIXEL_RING_BEADS  = 2;

    /**
     * Croix pixel-art 3D détaillée : cœur à double taille, bras crénelés de cubes dégressifs,
     * éclats diagonaux minuscules et micro-pixels de pointe (aucun gros bloc uniforme).
     */
    public static void emitPixelCross3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz,
                                        float stepSize, int armPixels,
                                        int coreRgb, int tipRgb, int alpha) {
        if (alpha <= 6 || stepSize <= 0.004f) return;
        float hs = stepSize * 0.46f;
        int salt = Math.round(cx * 811.0f + cy * 457.0f + cz * 293.0f);

        emitVoxelCube(vc, mat, tmp, cx, cy, cz, hs * 1.24f, hs * 1.30f, hs * 1.24f, 0xFFFFFF, alpha);
        emitVoxelCube(vc, mat, tmp, cx, cy - hs * 0.9f, cz, hs * 0.72f, hs * 0.42f, hs * 0.72f, coreRgb,
                Math.round(alpha * 0.85f));
        for (int d = 0; d < 4; d++) {
            float sx = ((d & 1) == 0) ? 1.0f : -1.0f;
            float sy = ((d & 2) == 0) ? 1.0f : -1.0f;
            emitVoxelCube(vc, mat, tmp, cx + sx * stepSize * 0.92f, cy + sy * stepSize * 0.92f, cz,
                    hs * 0.38f, hs * 0.38f, hs * 0.38f, coreRgb, Math.round(alpha * 0.8f));
        }

        for (int i = 1; i <= armPixels; i++) {
            float off = i * stepSize;
            float t = (armPixels > 1) ? (float) (i - 1) / (armPixels - 1) : 0.0f;
            float s = hs * (1.02f - 0.40f * t) * (0.86f + 0.28f * pixelHash(salt + i * 17, i));
            int rgb = (i == 1) ? coreRgb : tipRgb;
            int a = Math.round(alpha * (1.0f - 0.13f * (i - 1)));
            crossArmPixel(vc, mat, tmp, cx + off, cy, cz, s, rgb, a, salt + i, 0);
            crossArmPixel(vc, mat, tmp, cx - off, cy, cz, s, rgb, a, salt + i, 1);
            crossArmPixel(vc, mat, tmp, cx, cy + off, cz, s, rgb, a, salt + i, 2);
            crossArmPixel(vc, mat, tmp, cx, cy - off, cz, s, rgb, a, salt + i, 3);
            if (i <= 2) {
                crossArmPixel(vc, mat, tmp, cx, cy, cz + off, s, rgb, a, salt + i, 4);
                crossArmPixel(vc, mat, tmp, cx, cy, cz - off, s, rgb, a, salt + i, 5);
            }
        }
    }

    /** Pixel de bras de croix : tailles X/Y/Z différentes + micro-éclat de bord. */
    private static void crossArmPixel(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float px, float py, float pz, float s,
                                      int rgb, int alpha, int salt, int face) {
        float jy = s * (0.76f + 0.44f * pixelHash(salt, face * 29 + 7));
        float jz = s * (0.84f + 0.28f * pixelHash(salt + 3, face * 13 + 1));
        emitVoxelCube(vc, mat, tmp, px, py, pz, s, jy, jz, rgb, alpha);
        if ((face & 1) == 0) {
            emitVoxelCube(vc, mat, tmp, px, py + jy * 0.94f, pz, s * 0.34f, s * 0.26f, s * 0.34f,
                    0xFFFFFF, Math.round(alpha * 0.68f));
        }
    }

    /**
     * Ligne pixel-art 3D : chaque pas est un mini-cube de taille variable, avec coudes de remplissage
     * (aucun trou en escalier) et éclats périodiques plus petits — l'effet "trait droit 3D" reste
     * volontairement crénelé, jamais une forme lisse.
     */
    public static void emitSteppedVoxelLine(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1,
                                            float halfSize, int coreRgb, int outerRgb, int alpha) {
        if (alpha <= 6 || halfSize <= 0.002f) return;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= 0.0005f) return;

        float grid = Math.max(0.03125f, halfSize * 1.95f);
        int count = Math.min(64, Math.max(2, Math.round(dist / grid)));
        int salt = Math.round(x0 * 911.0f + z0 * 647.0f + y0 * 383.0f + dist * 97.0f);

        float prevX = 0.0f, prevY = 0.0f, prevZ = 0.0f;
        boolean hasPrev = false;
        for (int i = 0; i <= count; i++) {
            float u = (float) i / count;
            float px = quantize(Mth.lerp(u, x0, x1), grid);
            float py = quantize(Mth.lerp(u, y0, y1), grid);
            float pz = quantize(Mth.lerp(u, z0, z1), grid);
            int rgb = ((i & 1) == 0) ? coreRgb : outerRgb;

            if (hasPrev) {
                int changed = 0;
                if (Math.abs(px - prevX) > grid * 0.45f) changed++;
                if (Math.abs(py - prevY) > grid * 0.45f) changed++;
                if (Math.abs(pz - prevZ) > grid * 0.45f) changed++;
                if (changed >= 2) {
                    // Coude : petit cube intercalé pour garder la ligne continue en escalier
                    emitVoxelCube(vc, mat, tmp,
                            (prevX + px) * 0.5f, (prevY + py) * 0.5f, (prevZ + pz) * 0.5f,
                            halfSize * 0.60f, halfSize * 0.60f, halfSize * 0.60f,
                            ((i & 1) == 0) ? outerRgb : coreRgb, Math.round(alpha * 0.92f));
                }
            }

            float jit = 0.84f + 0.30f * pixelHash(salt, i * 13);
            emitVoxelCube(vc, mat, tmp, px, py, pz,
                    halfSize * jit,
                    halfSize * (0.74f + 0.32f * pixelHash(salt + 5, i * 7)),
                    halfSize * jit, rgb, alpha);
            if ((i & 3) == 1) {
                emitVoxelCube(vc, mat, tmp, px, py + halfSize * 1.12f, pz,
                        halfSize * 0.33f, halfSize * 0.27f, halfSize * 0.33f, 0xFFFFFF,
                        Math.round(alpha * 0.7f));
            }
            prevX = px; prevY = py; prevZ = pz;
            hasPrev = true;
        }
    }

    /**
     * 12 vraies runes pixel-art 5x5 (bitmaps 25 bits, ligne par ligne) : Fehu, Algiz, Othala, Tiwaz,
     * Dagaz, Sowilo, Ingwaz, Gebo, Ehwaz, Ansuz, Raido, Thurisaz.
     */
    private static final int[] RUNE_BITMAPS_5X5 = {
            0b10000_11110_11111_10100_10000, // Fehu
            0b10001_01010_00100_00100_00100, // Algiz
            0b01110_10001_10001_01010_00100, // Othala
            0b01110_00100_00100_00100_00100, // Tiwaz
            0b11100_11110_01111_00111_00011, // Dagaz
            0b01110_11000_01100_00110_01110, // Sowilo
            0b00100_01010_10001_01010_00100, // Ingwaz
            0b10001_01010_00100_01010_10001, // Gebo
            0b00100_01110_11111_01110_00100, // Ehwaz
            0b10000_11100_10110_10000_11000, // Ansuz
            0b11100_10010_11100_10100_10010, // Raido
            0b01100_10010_01100_01000_01000  // Thurisaz
    };

    private static boolean runeBit(int mask, int row, int col) {
        int bit = 24 - (row * 5 + col);
        return ((mask >>> bit) & 1) != 0;
    }

    /**
     * T345-PIXEL-RING : vrai cercle pixel-art (rasterisation colonne par colonne, comme un sprite de
     * cercle Minecraft) — chaque pixel est un mini-cube 3D de taille variable, avec pixels internes
     * plus petits et biseaux de marche pour que la courbe reste visiblement crénelée.
     */
    public static void emitPixelCircle3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz,
                                         float radius, int rgbA, int rgbB, int alpha) {
        emitPixelRing3D(vc, mat, tmp, cx, cy, cz, radius, rgbA, rgbB, alpha, PIXEL_RING_SOLID);
    }

    private static void emitPixelRing3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float radius,
                                        int rgbA, int rgbB, int alpha, int style) {
        if (alpha <= 6 || radius <= 0.06f) return;
        float grid = (radius <= 0.70f) ? 0.0625f : ((radius <= 1.55f) ? 0.078125f : 0.109375f);
        int rPix = Math.min(30, Math.max(2, Math.round(radius / grid)));
        // Le niveau de détail (micro-pixel intérieur + biseau) n'est activé que sur les petits cercles :
        // sur les grands rayons, le contour pixel-art suffit et le nombre de cubes reste raisonnable.
        boolean fine = rPix <= 16;
        float hs = grid * 0.47f;
        float gx = quantize(cx, grid);
        float gz = quantize(cz, grid);
        int salt = Math.round(cx * 977.0f + cz * 613.0f + cy * 331.0f) ^ (rPix * 31);

        if (style == PIXEL_RING_BEADS) {
            // Anneau de perles : pixels isolés tous les 3 pas, tailles alternées
            for (int u = -rPix; u <= rPix; u += 3) {
                int v = (int) Math.round(Math.sqrt((double) Math.max(0, rPix * rPix - u * u)));
                ringPixel(vc, mat, tmp, gx, cy, gz, u, v, grid, hs, alpha, rgbA, rgbB, false, fine, salt);
                if (v != 0) {
                    ringPixel(vc, mat, tmp, gx, cy, gz, u, -v, grid, hs, alpha, rgbB, rgbA, false, fine, salt + 1);
                }
            }
            return;
        }

        int lastV = -1;
        for (int u = -rPix; u <= rPix; u++) {
            int v = (int) Math.round(Math.sqrt((double) Math.max(0, rPix * rPix - u * u)));
            boolean edgeStep = (u == -rPix || u == rPix || v == 0 || v == rPix);
            if (style == PIXEL_RING_DASHED) {
                if (((u >> 1) & 1) != 0) {
                    lastV = v;
                    continue;
                }
                ringPixel(vc, mat, tmp, gx, cy, gz, u, v, grid, hs, alpha, rgbA, rgbB, edgeStep, fine, salt);
                if (v != 0) {
                    ringPixel(vc, mat, tmp, gx, cy, gz, u, -v, grid, hs, alpha, rgbB, rgbA, edgeStep, fine, salt + 1);
                }
                lastV = v;
                continue;
            }

            // Remplissage vertical : le contour reste jointif là où la courbe "saute" d'un cran.
            // Grand rayon : seuls les deux bords de la marche sont posés (4x moins de cubes).
            int from = (lastV >= 0) ? Math.min(lastV, v) : v;
            int to = (lastV >= 0) ? Math.max(lastV, v) : v;
            for (int k = from; k <= to; k++) {
                if (!fine && k != from && k != to) continue;
                boolean edge = edgeStep || k == from || k == to;
                ringPixel(vc, mat, tmp, gx, cy, gz, u, k, grid, hs, alpha, rgbA, rgbB, edge, fine, salt);
                if (k != 0) {
                    ringPixel(vc, mat, tmp, gx, cy, gz, u, -k, grid, hs, alpha, rgbB, rgbA, edge, fine, salt + 1);
                }
            }
            lastV = v;
        }
    }

    /** Un pixel de cercle : cube principal de taille variable, micro-pixel intérieur et biseau de marche. */
    private static void ringPixel(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float gx, float gy, float gz, int u, int v,
                                  float grid, float hs, int alpha,
                                  int rgb, int rgbAlt, boolean edge, boolean detail, int salt) {
        float px = gx + u * grid;
        float pz = gz + v * grid;
        float jx = 0.80f + 0.30f * pixelHash(salt, u * 37 + v * 11);
        float hy = hs * (0.66f + 0.34f * pixelHash(salt ^ 0x5F, u * 13 + v * 41));

        emitVoxelCube(vc, mat, tmp, px, gy, pz, hs * jx, hy, hs * (0.82f + 0.26f * pixelHash(salt + 9, u * 7 + v * 29)),
                rgb, alpha);
        if (!detail) return;

        // Pixel intérieur plus petit : donne l'épaisseur "2 blocs de tailles différentes"
        if (((u * 3 + v * 5) & 1) == 0) {
            float inX = px + (u >= 0 ? -grid * 0.29f : grid * 0.29f);
            float inZ = pz + (v >= 0 ? -grid * 0.29f : grid * 0.29f);
            emitVoxelCube(vc, mat, tmp, inX, gy + hy * 0.62f, inZ,
                    grid * 0.20f, grid * 0.15f, grid * 0.20f, rgbAlt, Math.round(alpha * 0.88f));
        }
        // Biseau de marche : marque le crénelage pixel-art (jamais de courbe lissée)
        if (edge) {
            emitVoxelCube(vc, mat, tmp,
                    px + (u >= 0 ? -grid * 0.42f : grid * 0.42f), gy - hy * 0.52f,
                    pz + (v >= 0 ? grid * 0.20f : -grid * 0.20f),
                    grid * 0.16f, grid * 0.10f, grid * 0.16f, rgbAlt, Math.round(alpha * 0.78f));
        }
    }

    /**
     * T345-PIXEL-RETICLE : réticule pixel-art face caméra — anneau rasterisé en pixels 3D de tailles
     * variées, second anneau intérieur pointillé, et crans de mire dégressifs.
     */
    private static void emitPixelCircleBillboard3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                                   float cx, float cy, float cz,
                                                   float radius, int rgbA, int rgbB, int alpha) {
        if (alpha <= 6 || radius <= 0.06f) return;
        float horizDist = Mth.sqrt(cx * cx + cz * cz);
        float rx = (horizDist > 0.01f) ? (-cz / horizDist) : 1.0f;
        float rz = (horizDist > 0.01f) ? (cx / horizDist) : 0.0f;

        float grid = (radius <= 1.20f) ? 0.0625f : 0.09375f;
        int rPix = Math.min(24, Math.max(3, Math.round(radius / grid)));
        boolean fine = rPix <= 14;
        float hs = grid * 0.47f;
        int salt = Math.round(cx * 733.0f + cy * 419.0f + cz * 617.0f + radius * 89.0f);

        int lastV = -1;
        for (int u = -rPix; u <= rPix; u++) {
            int v = (int) Math.round(Math.sqrt((double) Math.max(0, rPix * rPix - u * u)));
            int from = (lastV >= 0) ? Math.min(lastV, v) : v;
            int to = (lastV >= 0) ? Math.max(lastV, v) : v;
            for (int k = from; k <= to; k++) {
                if (!fine && k != from && k != to) continue;
                boolean edge = (u == -rPix || u == rPix || k == 0 || k == rPix || k == from || k == to);
                int rgb = (((u + k) >> 1) & 1) == 0 ? rgbA : rgbB;
                billboardPixel(vc, mat, tmp, cx, cy, cz, rx, rz, u, k, grid, hs, rgb, rgbB, alpha, edge, fine, salt);
                if (k != 0) {
                    billboardPixel(vc, mat, tmp, cx, cy, cz, rx, rz, u, -k, grid, hs, rgb, rgbB, alpha, edge, fine, salt + 1);
                }
            }
            lastV = v;
        }

        // Second anneau intérieur pointillé (profondeur pixel-art du réticule)
        int innerPix = Math.max(2, Math.round(rPix * 0.56f));
        for (int u = -innerPix; u <= innerPix; u += 2) {
            int v = (int) Math.round(Math.sqrt((double) Math.max(0, innerPix * innerPix - u * u)));
            billboardPixel(vc, mat, tmp, cx, cy, cz, rx, rz, u, v, grid, hs * 0.8f, rgbB, rgbA,
                    Math.round(alpha * 0.72f), false, fine, salt + 5);
            if (v != 0) {
                billboardPixel(vc, mat, tmp, cx, cy, cz, rx, rz, u, -v, grid, hs * 0.8f, rgbB, rgbA,
                        Math.round(alpha * 0.72f), false, fine, salt + 6);
            }
        }

        // Crans de mire dégressifs sur les 4 axes + marques diagonales minuscules
        for (int tk = 1; tk <= 3; tk++) {
            int p = rPix - tk;
            if (p <= 1) break;
            int a2 = Math.round(alpha * (tk == 1 ? 1.0f : 0.62f));
            float s = hs * (tk == 1 ? 1.05f : 0.56f);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, p, 0, grid, s, 0xFFFFFF, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, -p, 0, grid, s, 0xFFFFFF, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, 0, p, grid, s, 0xFFFFFF, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, 0, -p, grid, s, 0xFFFFFF, a2);
        }
        int diag = Math.max(2, Math.round(rPix * 0.70f));
        for (int d = 0; d < 4; d++) {
            int ux = ((d & 1) == 0) ? diag : -diag;
            int uy = ((d & 2) == 0) ? diag : -diag;
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, ux, uy, grid, hs * 0.5f, rgbA, Math.round(alpha * 0.8f));
        }
    }

    private static void billboardPixel(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float cy, float cz, float rx, float rz,
                                       int uPix, int vPix, float grid, float hs, int rgb, int rgbAlt,
                                       int alpha, boolean edge, boolean detail, int salt) {
        float px = cx + rx * (uPix * grid);
        float py = cy + vPix * grid;
        float pz = cz + rz * (uPix * grid);
        float jx = 0.80f + 0.30f * pixelHash(salt, uPix * 31 + vPix * 17);
        float hy = hs * (0.68f + 0.32f * pixelHash(salt + 2, uPix * 11 + vPix * 43));
        emitVoxelCube(vc, mat, tmp, px, py, pz, hs * jx, hy, hs * jx, rgb, alpha);
        if (!detail) return;
        if (((uPix + vPix) & 1) == 0) {
            emitVoxelCube(vc, mat, tmp, px - rx * grid * 0.26f, py - grid * 0.22f, pz - rz * grid * 0.26f,
                    grid * 0.19f, grid * 0.15f, grid * 0.19f, rgbAlt, Math.round(alpha * 0.85f));
        }
        if (edge) {
            emitVoxelCube(vc, mat, tmp, px + rx * grid * 0.30f, py + grid * 0.24f, pz + rz * grid * 0.30f,
                    grid * 0.14f, grid * 0.11f, grid * 0.14f, 0xFFFFFF, Math.round(alpha * 0.75f));
        }
    }

    private static void billboardRaw(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float cx, float cy, float cz, float rx, float rz,
                                     int uPix, int vPix, float grid, float hs,
                                     int rgb, int alpha) {
        emitVoxelCube(vc, mat, tmp,
                cx + rx * (uPix * grid), cy + vPix * grid, cz + rz * (uPix * grid),
                hs, hs * 0.9f, hs, rgb, alpha);
    }

    /**
     * T345-PIXEL-RUNIC-CIRCLE : cercle runique fait d'ANNEAUX DE PIXELS :
     * anneau extérieur plein, anneau intérieur plein, anneau de perles ornemental, runes 5x5
     * en mini-cubes orientés vers l'extérieur, étoile inscrite crénelée et accents cardinaux.
     */
    public static void emitPixelRunicCircle3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              float cx, float cy, float cz,
                                              float outerRadius, float rotPhase,
                                              int outerRgb, int innerRgb, int runeRgb,
                                              int alpha, boolean withInscribedStar) {
        if (alpha <= 6 || outerRadius <= 0.15f) return;

        // 1) Anneau extérieur : cercle de pixels 3D jointifs, tailles variées
        emitPixelRing3D(vc, mat, tmp, cx, cy, cz, outerRadius, outerRgb, innerRgb, alpha, PIXEL_RING_SOLID);

        // 2) Anneau intérieur plein, légèrement plus petit
        float innerR = outerRadius * 0.60f;
        if (innerR >= 0.16f) {
            emitPixelRing3D(vc, mat, tmp, cx, cy, cz, innerR, innerRgb, runeRgb,
                    Math.round(alpha * 0.94f), PIXEL_RING_SOLID);
        }

        // 3) Anneau de perles pixel-art entre les deux anneaux
        float beadR = outerRadius * 0.78f;
        if (beadR >= 0.30f) {
            emitPixelRing3D(vc, mat, tmp, cx, cy, cz, beadR, runeRgb, outerRgb,
                    Math.round(alpha * 0.72f), PIXEL_RING_BEADS);
        }

        // 4) Runes 5x5 en mini-pixels 3D, orientées vers l'extérieur du cercle
        if (outerRadius >= 0.42f) {
            float step = Mth.clamp(outerRadius * 0.040f, 0.024f, 0.050f);
            int runeCount = (outerRadius >= 1.75f) ? 12 : ((outerRadius >= 0.95f) ? 8 : 4);
            int phaseIdx = Math.round(rotPhase * 3.0f);
            for (int r = 0; r < runeCount; r++) {
                float ang = rotPhase + r * ((float) (Math.PI * 2.0) / runeCount);
                float rx = quantize(cx + Mth.cos(ang) * beadR, 0.03125f);
                float rz = quantize(cz + Mth.sin(ang) * beadR, 0.03125f);
                emitPixelRune3D(vc, mat, tmp, rx, cy + 0.022f, rz, r + phaseIdx, step,
                        -ang + (float) (Math.PI * 0.5), runeRgb, (r & 1) == 0 ? 0xFFFFFF : innerRgb, alpha);
            }
        }

        // 5) Étoile runique inscrite en lignes de pixels crénelées
        if (withInscribedStar && innerR >= 0.22f) {
            float sr = innerR * 0.92f;
            float lineHalf = Mth.clamp(outerRadius * 0.024f, 0.014f, 0.032f);
            for (int k = 0; k < 4; k++) {
                float a = rotPhase * 0.5f + k * ((float) Math.PI * 0.25f);
                float dx = Mth.cos(a) * sr;
                float dz = Mth.sin(a) * sr;
                emitSteppedVoxelLine(vc, mat, tmp, cx - dx, cy, cz - dz, cx + dx, cy, cz + dz,
                        lineHalf, (k & 1) == 0 ? runeRgb : innerRgb, outerRgb, Math.round(alpha * 0.86f));
            }
            emitVoxelCube(vc, mat, tmp, cx, cy + lineHalf * 0.4f, cz,
                    lineHalf * 1.5f, lineHalf * 1.2f, lineHalf * 1.5f, 0xFFFFFF, Math.round(alpha * 0.9f));
        }

        // 6) Accents cardinaux : micro-pixels blancs pour le piqué pixel-art
        if (outerRadius >= 0.60f) {
            for (int k = 0; k < 4; k++) {
                float a = rotPhase * 0.35f + k * ((float) Math.PI * 0.5f);
                float px = quantize(cx + Mth.cos(a) * outerRadius, 0.03125f);
                float pz = quantize(cz + Mth.sin(a) * outerRadius, 0.03125f);
                float s = Mth.clamp(outerRadius * 0.028f, 0.010f, 0.030f);
                emitVoxelCube(vc, mat, tmp, px, cy + s * 1.1f, pz, s, s * 0.85f, s, 0xFFFFFF,
                        Math.round(alpha * 0.85f));
            }
        }
    }

    /** Rune pixel-art 5x5 : pixels "colonne" plus gros et pixels de liaison plus petits + orientation radiale. */
    private static void emitPixelRune3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float rx, float ry, float rz,
                                        int glyphIdx, float pixelStep, float angleRad,
                                        int stemRgb, int accentRgb, int alpha) {
        int mask = RUNE_BITMAPS_5X5[Math.floorMod(glyphIdx, RUNE_BITMAPS_5X5.length)];
        float ca = Mth.cos(angleRad);
        float sa = Mth.sin(angleRad);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (!runeBit(mask, row, col)) continue;
                int neighbours = 0;
                if (col > 0 && runeBit(mask, row, col - 1)) neighbours++;
                if (col < 4 && runeBit(mask, row, col + 1)) neighbours++;
                if (row > 0 && runeBit(mask, row - 1, col)) neighbours++;
                if (row < 4 && runeBit(mask, row + 1, col)) neighbours++;

                boolean stem = neighbours >= 2;
                float dx = (col - 2) * pixelStep;
                float dz = (row - 2) * pixelStep;
                float px = rx + dx * ca - dz * sa;
                float pz = rz + dx * sa + dz * ca;
                float hx = pixelStep * (stem ? 0.47f : 0.34f);
                float hy = pixelStep * (stem ? 0.42f : 0.28f);
                emitVoxelCube(vc, mat, tmp, px, ry + (stem ? 0.0f : pixelStep * 0.07f), pz,
                        hx, hy, hx, stem ? stemRgb : accentRgb, alpha);
            }
        }
    }

    /**
     * Goutte pixel-art 3D : aucune forme lisse, uniquement un empilement de cubes de tailles
     * différentes (pointe fine, corps large, queue, éclaboussures satellites, micro-reflet).
     */
    public static void emitPixelDroplet3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float cx, float cy, float cz, float pixelStep,
                                          int bodyRgb, int lightRgb, int alpha, int salt) {
        if (alpha <= 6 || pixelStep <= 0.004f) return;
        float[][] pixels = {
                {0.00f, 3.10f, 0.16f},
                {0.00f, 2.25f, 0.28f},
                {0.00f, 1.35f, 0.40f},
                {-0.42f, 0.55f, 0.34f}, {0.42f, 0.55f, 0.34f},
                {0.00f, 0.50f, 0.52f},
                {-0.52f, -0.35f, 0.34f}, {0.52f, -0.35f, 0.34f},
                {0.00f, -0.42f, 0.50f},
                {0.00f, -1.20f, 0.34f},
                {0.00f, -1.75f, 0.18f}
        };
        for (int i = 0; i < pixels.length; i++) {
            float jx = (pixelHash(salt, i * 11) - 0.5f) * pixelStep * 0.22f;
            float jz = (pixelHash(salt + 13, i * 7) - 0.5f) * pixelStep * 0.22f;
            float s = pixelStep * pixels[i][2];
            emitVoxelCube(vc, mat, tmp,
                    cx + pixelStep * pixels[i][0] + jx,
                    cy + pixelStep * pixels[i][1],
                    cz + jz,
                    s, s * 0.86f, s,
                    i <= 2 ? lightRgb : bodyRgb,
                    Math.round(alpha * (i <= 2 ? 0.92f : 1.0f)));
        }
        emitVoxelCube(vc, mat, tmp, cx - pixelStep * 0.55f, cy + pixelStep * 0.9f, cz - pixelStep * 0.1f,
                pixelStep * 0.13f, pixelStep * 0.13f, pixelStep * 0.13f, 0xFFE0E0, Math.round(alpha * 0.85f));
        emitVoxelCube(vc, mat, tmp, cx + pixelStep * 1.15f, cy + pixelStep * 2.0f, cz + pixelStep * 0.35f,
                pixelStep * 0.11f, pixelStep * 0.11f, pixelStep * 0.11f, bodyRgb, Math.round(alpha * 0.8f));
        emitVoxelCube(vc, mat, tmp, cx - pixelStep * 1.05f, cy - pixelStep * 0.9f, cz + pixelStep * 0.5f,
                pixelStep * 0.14f, pixelStep * 0.12f, pixelStep * 0.14f, lightRgb, Math.round(alpha * 0.7f));
    }

    /** Amas de pixels 3D (météore, éclat de roche, cœur) : croûte irrégulière de cubes de tailles différentes. */
    public static void emitPixelRockCluster3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              float cx, float cy, float cz, float pixelStep, int count,
                                              int crustRgb, int glowRgb, int hotRgb, int alpha, int salt) {
        if (alpha <= 6 || pixelStep <= 0.004f) return;
        emitVoxelCube(vc, mat, tmp, cx, cy, cz, pixelStep * 1.10f, pixelStep * 1.00f, pixelStep * 1.10f, hotRgb, alpha);
        long h = salt * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < count; i++) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            float a = (((h >>> 11) & 0x3FF) / 1023.0f) * (float) (Math.PI * 2.0);
            h = h * 6364136223846793005L + 1442695040888963407L;
            float elev = ((((h >>> 13) & 0x1FF) / 511.0f) - 0.5f) * 1.6f;
            h = h * 6364136223846793005L + 1442695040888963407L;
            float dist = 0.75f + (((h >>> 17) & 0xFF) / 255.0f) * 1.35f;
            float px = quantize(cx + Mth.cos(a) * pixelStep * dist, pixelStep * 0.5f);
            float py = quantize(cy + elev * pixelStep * dist, pixelStep * 0.5f);
            float pz = quantize(cz + Mth.sin(a) * pixelStep * dist, pixelStep * 0.5f);
            float s = pixelStep * (0.30f + 0.30f * ((i & 1) == 0 ? 1.0f : 0.5f));
            emitVoxelCube(vc, mat, tmp, px, py, pz, s, s * 0.88f, s,
                    (i % 3 == 0) ? glowRgb : crustRgb, Math.round(alpha * Math.max(0.45f, 0.95f - 0.03f * i)));
        }
    }

    /** Éclair 3D en escalier de mini-cubes voxels. */
    public static void emitVoxelLightning(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          int nodes, float jitterAmp, long seed,
                                          int coreRgb, int glowRgb, int alpha, float cubeHalf) {
        if (alpha <= 6) return;
        float px = x0, py = y0, pz = z0;
        long h = seed * 0x9E3779B97F4A7C15L;
        for (int i = 1; i <= nodes; i++) {
            float f = (float) i / nodes;
            float nx = Mth.lerp(f, x0, x1);
            float ny = Mth.lerp(f, y0, y1);
            float nz = Mth.lerp(f, z0, z1);
            if (i < nodes) {
                float env = Mth.sin(f * (float) Math.PI) * jitterAmp;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nx += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
                h = h * 6364136223846793005L + 1442695040888963407L;
                ny += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env * 0.6f;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nz += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
            }
            emitSteppedVoxelLine(vc, mat, tmp, px, py, pz, nx, ny, nz, cubeHalf, coreRgb, glowRgb, alpha);
            px = nx; py = ny; pz = nz;
        }
    }

    private static void emitVoxelCurvedSiphon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              float x0, float y0, float z0,
                                              float x1, float y1, float z1,
                                              float phase, float progress,
                                              int darkRgb, int brightRgb, int alpha) {
        if (alpha <= 6) return;
        float mx = (x0 + x1) * 0.5f + Mth.cos(phase) * 0.75f;
        float my = Math.max(y0, y1) + 0.85f + Mth.sin(phase) * 0.35f;
        float mz = (z0 + z1) * 0.5f + Mth.sin(phase) * 0.75f;

        int cubes = 16;
        int salt = Math.round(phase * 137.0f + progress * 53.0f);
        float prevX = x0, prevY = y0, prevZ = z0;
        for (int i = 0; i <= cubes; i++) {
            float u = (float) i / cubes;
            float inv = 1.0f - u;
            float bx = quantize(inv * inv * x0 + 2.0f * inv * u * mx + u * u * x1, 0.0625f);
            float by = quantize(inv * inv * y0 + 2.0f * inv * u * my + u * u * y1, 0.0625f);
            float bz = quantize(inv * inv * z0 + 2.0f * inv * u * mz + u * u * z1, 0.0625f);
            float pulse = 1.0f - Math.min(1.0f, Math.abs(u - progress) * 2.8f);
            float hs = (0.034f + 0.026f * pulse) * (0.82f + 0.36f * pixelHash(salt, i * 13));
            // Coude de remplissage : le siphon reste un chapelet de pixels jointifs, jamais lissé
            if (i > 0 && (Math.abs(bx - prevX) > 0.03f && Math.abs(by - prevY) > 0.03f)) {
                emitVoxelCube(vc, mat, tmp, (bx + prevX) * 0.5f, (by + prevY) * 0.5f, (bz + prevZ) * 0.5f,
                        hs * 0.58f, hs * 0.58f, hs * 0.58f, darkRgb, Math.round(alpha * 0.9f));
            }
            emitVoxelCube(vc, mat, tmp, bx, by, bz, hs,
                    hs * (0.78f + 0.3f * pixelHash(salt + 4, i)), hs,
                    pulse > 0.35f ? brightRgb : darkRgb, alpha);
            if ((i & 2) == 0) {
                emitVoxelCube(vc, mat, tmp, bx + hs * 1.1f, by + hs * 0.35f, bz,
                        hs * 0.32f, hs * 0.32f, hs * 0.32f, brightRgb, Math.round(alpha * 0.62f));
            }
            prevX = bx; prevY = by; prevZ = bz;
        }
    }

    private static void emitVoxelHelix(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float baseY, float cz,
                                       float radius, float height, float phase,
                                       int rgbA, int rgbB, int alpha) {
        if (alpha <= 6) return;
        int cubes = 18;
        int salt = Math.round(phase * 211.0f + radius * 379.0f);
        float pxP = cx, pyP = baseY, pzP = cz;
        for (int i = 0; i <= cubes; i++) {
            float u = (float) i / cubes;
            float a = phase + u * (float) Math.PI * 2.4f;
            float nx = quantize(cx + Mth.cos(a) * radius * (1.0f - 0.2f * u), 0.0625f);
            float ny = quantize(baseY + u * height, 0.0625f);
            float nz = quantize(cz + Mth.sin(a) * radius * (1.0f - 0.2f * u), 0.0625f);
            float hs = 0.040f * (1.0f - u * 0.32f) * (0.80f + 0.38f * pixelHash(salt, i * 11));
            // Remplissage des marches : l'hélice reste un escalier de pixels continus
            if (i > 0) {
                emitVoxelCube(vc, mat, tmp, (nx + pxP) * 0.5f, (ny + pyP) * 0.5f, (nz + pzP) * 0.5f,
                        hs * 0.56f, hs * 0.56f, hs * 0.56f, rgbB, Math.round(alpha * 0.85f));
            }
            emitVoxelCube(vc, mat, tmp, nx, ny, nz, hs,
                    hs * (0.80f + 0.26f * pixelHash(salt + 3, i)), hs,
                    (i & 1) == 0 ? rgbA : rgbB, alpha);
            // Micro-pixel décalé : casse l'effet "gros bloc uniforme"
            if ((i & 1) == 0) {
                emitVoxelCube(vc, mat, tmp, quantize(nx + hs * 1.25f, 0.03125f), ny, quantize(nz - hs * 1.1f, 0.03125f),
                        hs * 0.34f, hs * 0.30f, hs * 0.34f, rgbA, Math.round(alpha * 0.66f));
            }
            pxP = nx; pyP = ny; pzP = nz;
        }
    }

    public static float quantize(float v, float step) {
        return Math.round(v / step) * step;
    }

    private static int shadeRgb(int rgb, float mul) {
        int r = Math.min(255, Math.round(((rgb >>> 16) & 0xFF) * mul));
        int g = Math.min(255, Math.round(((rgb >>> 8) & 0xFF) * mul));
        int b = Math.min(255, Math.round((rgb & 0xFF) * mul));
        return (r << 16) | (g << 8) | b;
    }

    public static void putQuad(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float x0, float y0, float z0, int rgb0, int a0,
                               float x1, float y1, float z1, int rgb1, int a1,
                               float x2, float y2, float z2, int rgb2, int a2,
                               float x3, float y3, float z3, int rgb3, int a3) {
        putPosColor(vc, mat, tmp, x0, y0, z0, rgb0, a0);
        putPosColor(vc, mat, tmp, x1, y1, z1, rgb1, a1);
        putPosColor(vc, mat, tmp, x2, y2, z2, rgb2, a2);
        putPosColor(vc, mat, tmp, x3, y3, z3, rgb3, a3);
    }

    public static void putPosColor(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float x, float y, float z, int rgb, int a) {
        // T359-BIG-ANIM-SCALE : unique point de sortie de TOUTE la géométrie (cubes, anneaux,
        // cercles, lignes, hélices, réticules) -> une seule transformation suffit à faire
        // grossir/rétrécir l'animation entière autour de son point d'impact.
        if (mobScaleActive) {
            x = scaleAxis(x, mobPivotX);
            y = scaleAxis(y, mobPivotY);
            z = scaleAxis(z, mobPivotZ);
        }
        mat.transformPosition(x, y, z, tmp);
        int ca = Mth.clamp(a, 0, 255);
        vc.addVertex(tmp.x(), tmp.y(), tmp.z())
                .setColor((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, ca);
    }
}
