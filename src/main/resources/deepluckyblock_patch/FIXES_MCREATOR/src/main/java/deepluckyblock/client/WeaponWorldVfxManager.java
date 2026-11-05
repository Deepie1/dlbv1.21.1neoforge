package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.DeepElement;
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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
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
    public static final int KIND_METEOR_TRACK = 4;

    private static final List<VfxInstance> ACTIVE = new ArrayList<>(144);
    private static final List<FloatingPixel> PIXEL_PARTICLES = new ArrayList<>(1400);
    private static final Map<Integer, TrackedProjectile> TRACKED_PROJECTILES = new HashMap<>(32);

    // Déclenchement originel des entailles (T342, commit 4d4182e) : au swing, pas au contact.
    private static final List<PendingEcho> PENDING_ECHOES = new ArrayList<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_SWINGING = new HashMap<>(16);
    private static final Map<Integer, Integer> PLAYER_SWING_PARITY = new HashMap<>(16);
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
    /** Les signatures Gaia utilisent des dimensions stables; seules leurs trajectoires bougent. */
    private static boolean voxelPulseEnabled = true;

    // ============================================================================================
    // T367-VOXEL-MATERIAL — matière des cubes : voile (transparence), reflet (gloss), grain (texture)
    // --------------------------------------------------------------------------------------------
    // Chaque voxel est TOUJOURS un cube à 6 faces carrées (aucun triangle, aucun cercle) : la
    // matière est donc obtenue uniquement par la couleur de ses 8 coins (texture + occlusion),
    // par son opacité (l'eau et l'air laissent passer la lumière) et par sa taille.
    // ============================================================================================
    /** Opacité de base du matériau : seule une vraie matière fluide/vaporeuse descend sous 1. */
    private static float cubeVeil = 1.0f;
    /** Reflet spéculaire réservé au métal ; aucun « shine » générique sur les autres éléments. */
    private static float cubeGloss = 0.0f;
    /** Grain de surface propre à l'élément (eau lisse, bois strié, terre granuleuse...). */
    private static float cubeTex = 0.10f;
    /** Matériau courant, utilisé pour choisir un motif cohérent au lieu d'un bruit aléatoire partout. */
    private static DeepElement voxelElement = DeepElement.EARTH;
    /** Nuances par coin du cube courant (indice = (x>0 ? 4 : 0) | (y>0 ? 2 : 0) | (z>0 ? 1 : 0)). */
    private static final float[] CORNER_SHADE = new float[8];

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
        /** Transparence réelle des particules secondaires (notamment les bleus d'Eau). */
        final float alphaScale;

        FloatingPixel(boolean additive, double x, double y, double z,
                      double vx, double vy, double vz, float gravity,
                      float halfSize, int rgb, int maxAge, int detailSalt) {
            this(additive, x, y, z, vx, vy, vz, gravity, halfSize, rgb, maxAge, detailSalt, 1.0f);
        }

        FloatingPixel(boolean additive, double x, double y, double z,
                      double vx, double vy, double vz, float gravity,
                      float halfSize, int rgb, int maxAge, int detailSalt, float alphaScale) {
            this.alphaScale = Mth.clamp(alphaScale, 0.05f, 1.0f);
            this.additive = additive && this.alphaScale >= 0.999f;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.gravity = gravity;
            this.halfSize = halfSize;
            this.rgb = rgb;
            this.maxAge = Math.max(4, maxAge);
            this.detailSalt = detailSalt;
        }
    }

    /** Seconde lame différée de TWIN_ECHO_SLASH, reprise de l'implémentation originelle. */
    private static final class PendingEcho {
        int ticksLeft;
        WeaponVisualEffectId effect;
        int intensity;
        double x, y, z;
        float yaw, pitch, tiltRad;
        int paletteMask;
    }

    private static final class VfxInstance {
        final WeaponVisualEffectId effect;
        final int kind;
        final int intensity;
        double x, y, z;
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
        int homingTargetId = -1;

        /** Couleur primaire de l'effet ou d'une famille sélectionnée dans la palette de l'arme. */
        int primary() {
            return (paletteMask == 0) ? effect.primaryRgb
                    : paletteColor(paletteMask, effect.primaryRgb, palettePhase(frameTimeSeconds), 0);
        }

        /** Couleur secondaire, issue du même rôle chromatique pour chaque famille portée. */
        int secondary() {
            return (paletteMask == 0) ? effect.secondaryRgb
                    : paletteColor(paletteMask, effect.secondaryRgb, palettePhase(frameTimeSeconds), 1);
        }

        int highlight() {
            return (paletteMask == 0) ? effect.highlightRgb
                    : paletteColor(paletteMask, effect.highlightRgb, palettePhase(frameTimeSeconds), 2);
        }

        /** T349-VARIATION : variante 0..7 tirée au déclenchement (forme, sens, luminosité). */
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

    /**
     * T367-VOXEL-MATERIAL : propriétés sobres et propres à chaque matière, jamais un post-traitement
     * uniforme. La pierre/terre/bois/métal restent opaques ; seuls les fluides et les voiles le sont.
     */
    private static void applyVoxelMaterial(WeaponVisualEffectId fx) {
        DeepElement el = fx != null ? fx.element() : null;
        voxelElement = el == null ? DeepElement.EARTH : el;
        cubeVeil = 1.0f;
        cubeGloss = 0.0f;
        switch (voxelElement) {
            case WATER -> {
                // L'eau est un volume translucide; le givre reste cristallin mais laisse aussi passer la lumière.
                cubeVeil = fx == WeaponVisualEffectId.PERMAFROST_RIME ? 0.78f : 0.62f;
                cubeTex = fx == WeaponVisualEffectId.PERMAFROST_RIME ? 0.075f : 0.035f;
            }
            case FIRE -> cubeTex = 0.075f;       // flamme = panaches lisses, non une pierre brillante
            case THUNDER -> cubeTex = 0.025f;    // énergie nette, aucun reflet spéculaire
            case LIGHT -> cubeTex = 0.045f;      // énergie pure, sans matière brillante
            case WOOD -> cubeTex = 0.19f;        // stries d'écorce / fibres
            case EARTH -> cubeTex = 0.22f;       // cassures et grains irréguliers
            case METAL -> { cubeTex = 0.08f; cubeGloss = 0.18f; } // seule matière réfléchissante
            case POISON -> {
                // La vapeur de miasme passe à travers; les épines (Venom Briar) sont solides.
                cubeVeil = fx == WeaponVisualEffectId.MIASMA_SHROUD ? 0.68f
                        : fx == WeaponVisualEffectId.PLAGUE_NOVA ? 0.88f : 1.0f;
                cubeTex = 0.12f;
            }
            case SHADOW -> { cubeVeil = 0.76f; cubeTex = 0.08f; }
            case VOID -> { cubeVeil = 0.82f; cubeTex = 0.10f; }
            case BLOOD -> { cubeVeil = 0.94f; cubeTex = 0.10f; }
            case CHAOS -> { cubeTex = 0.16f; }
        }
    }

    /** La lueur additive est réservée aux éléments qui émettent réellement de la lumière. */
    private static boolean useAdditivePass(WeaponVisualEffectId fx) {
        if (fx == null) return false;
        return switch (fx.element()) {
            case FIRE, THUNDER, LIGHT, CHAOS -> true;
            default -> false;
        };
    }

    /** Applique un motif matériel cohérent avec un seul hash par cube (aucune allocation). */
    private static void prepareCorners(int seed) {
        float noise = pixelHash(seed, 991) - 0.5f;
        for (int i = 0; i < 8; i++) {
            float x = ((i & 4) != 0) ? 1.0f : -1.0f;
            float y = ((i & 2) != 0) ? 1.0f : -1.0f;
            float z = ((i & 1) != 0) ? 1.0f : -1.0f;
            float pattern = switch (voxelElement) {
                case WOOD -> x * 0.36f + z * 0.14f + noise * 0.22f; // fibres longitudinales
                case EARTH -> (x * y * z) * 0.26f + noise * 0.40f; // grain cassé
                case METAL -> y * 0.12f + noise * 0.12f;           // stries d'usinage discrètes
                case WATER -> y * 0.08f + noise * 0.10f;            // surface presque lisse
                case FIRE, THUNDER, LIGHT -> noise * 0.22f;          // énergie peu texturée
                case POISON, SHADOW, VOID -> noise * 0.34f;          // voile diffus
                case BLOOD -> x * z * 0.12f + noise * 0.26f;
                case CHAOS -> ((i & 1) == 0 ? 0.22f : -0.22f) + noise * 0.30f;
            };
            CORNER_SHADE[i] = 1.0f + pattern * cubeTex * 2.0f;
        }
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
        if (kind == KIND_METEOR_TRACK) {
            updateMeteorTracking(Math.round(tx), x, y, z);
            return;
        }

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
            // Chorégraphies Wood complètes : croissance, impacts, stun et explosion de sève.
            case ZEPHYR_WIND_LANCE -> 80;
            case THORN_MAW_ROOT -> 96;
            case VERDANT_SAP_BLOOM -> 96;
            case MOLTEN_FIRE_WAVE -> 92;
            case SOUL_FIRE_CRESCENT -> 112;
            case BRIMSTONE_METEOR -> 128;
            case SERAPH_SKY_PILLAR, ABYSSAL_MAELSTROM, WARLORD_RALLY_SIGIL,
                 DRAGON_CHAOS_PRISM, BIG_BANG_SCAR -> 22;
            case SANDSTORM_DUNE, ECLIPSE_VEIL, NULL_RIFT_FISSURE,
                 CHAOS_KALEIDO_LOOP, ENTROPY_SPHERE -> 18;
            case VENOM_BRIAR_FANG -> 88;
            case MIASMA_SHROUD -> 112;
            case PLAGUE_NOVA -> 96;
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

        // Un paquet de slash ne programme aucun second slash différé : un coup = un arc principal.

        // Éclaboussure de vrais mini-pixels 3D cubiques animés sous la physique !
        int burstCount = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 14 : 10;
        float cubeHalf = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 0.042f : 0.036f;
        // Fire, Chaos et Poison possèdent leurs seules formes structurées : aucune salve générique.
        if (fx.element() != DeepElement.CHAOS && fx.element() != DeepElement.FIRE
                && fx.element() != DeepElement.POISON) {
            spawnMiniPixelBurst(fx, x, y, z, burstCount, cubeHalf, 0.18, paletteMask);
        }
    }

    private static void updateMeteorTracking(int targetId, float x, float y, float z) {
        VfxInstance tracked = null;
        double bestDistance = 64.0;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            VfxInstance candidate = ACTIVE.get(i);
            if (candidate.effect != WeaponVisualEffectId.BRIMSTONE_METEOR) continue;
            if (candidate.homingTargetId == targetId) {
                tracked = candidate;
                break;
            }
            if (candidate.homingTargetId < 0) {
                double dx = candidate.x - x, dy = candidate.y - y, dz = candidate.z - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    tracked = candidate;
                }
            }
        }
        if (tracked == null) return;
        tracked.homingTargetId = targetId;
        tracked.x = x;
        tracked.y = y;
        tracked.z = z;
    }

    public static void spawnMiniPixelBurst(WeaponVisualEffectId fx, double x, double y, double z,
                                           int count, float halfSize, double speed) {
        spawnMiniPixelBurst(fx, x, y, z, count, halfSize, speed, 0);
    }

    /**
     * T374-PALETTE-LOCK : chaque pixel choisit un rôle chromatique d'une famille portée par l'arme;
     * les teintes de familles différentes alternent, sans interpolation RGB entre elles.
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
            int role = i % 3;
            int rgb = switch (role) {
                case 0 -> fx.primaryRgb;
                case 1 -> fx.secondaryRgb;
                default -> fx.highlightRgb;
            };
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                // Répartition des pixels entre familles sans changer leur rôle chromatique.
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = paletteColor(paletteMask, rgb, phase, role);
            } else if (fx.element() == DeepElement.CHAOS && role == 2) {
                rgb = DeepElement.chaosEdge(frameTimeSeconds, (float) i / Math.max(1, count));
            }
            // Tailles multi-échelles : quelques gros pixels, beaucoup de micro-éclats (jamais uniforme)
            float sz = halfSize * (0.42f + rand.nextFloat() * 1.05f);
            PIXEL_PARTICLES.add(new FloatingPixel(fx.additive, x, y, z, vx, vy, vz, grav, sz, rgb,
                    10 + rand.nextInt(10), rand.nextInt(Integer.MAX_VALUE),
                    fx.element() == DeepElement.WATER ? 0.68f : 1.0f));
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

            int role = i % 3;
            int rgb = switch (role) {
                case 0 -> echo.element().primary;
                case 1 -> echo.element().highlight;
                default -> echo.element().secondary;
            };
            int paletteRole = role == 1 ? 2 : (role == 2 ? 1 : 0);
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                rgb = paletteColor(paletteMask, rgb, t, paletteRole);
            }
            // Les mouvements lumineux utilisent la surbrillance validée de l'élément, jamais du blanc neutre.
            if (echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_STAR
                    || echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_PRISM
                    || echo.motion() == deepluckyblock.enchantments.WeaponVfxSubtleEcho.MOTION_HALO) {
                rgb = (paletteMask != 0) ? paletteColor(paletteMask, echo.element().highlight, t, 2)
                            : echo.element().highlight;
            }
            if (echo.element() == DeepElement.CHAOS && paletteRole == 2
                    && Integer.bitCount(paletteMask & DeepElement.MASK_ALL) <= 1) {
                rgb = DeepElement.chaosEdge(frameTimeSeconds, t);
            }

            float sz = echo.size() * (0.7f + rand.nextFloat() * 0.6f);
            PIXEL_PARTICLES.add(new FloatingPixel(additive,
                    x + ox, y + oy, z + oz, vx, vy, vz, echo.gravity(), sz, rgb,
                    echo.life() + rand.nextInt(3), rand.nextInt(Integer.MAX_VALUE),
                    echo.element() == DeepElement.WATER ? 0.68f : 1.0f));
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

            int role = i % 3;
            int rgb = switch (role) {
                case 0 -> sub.element().primary;
                case 1 -> sub.element().highlight;
                default -> sub.element().secondary;
            };
            int paletteRole = role == 1 ? 2 : (role == 2 ? 1 : 0);
            if (paletteMask != 0 && Integer.bitCount(paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                // Mélange élémentaire dans le même rôle de couleur : aucune teinte étrangère ajoutée.
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = paletteColor(paletteMask, rgb, phase, paletteRole);
            }
            if (sub.shape() == deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_GLINT
                    || sub.shape() == deepluckyblock.enchantments.WeaponVfxSubtle.SHAPE_SHARD) {
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = (paletteMask != 0) ? paletteColor(paletteMask, sub.element().highlight, phase, 2)
                        : sub.element().highlight;
            }
            if (sub.element() == DeepElement.CHAOS && paletteRole == 2
                    && Integer.bitCount(paletteMask & DeepElement.MASK_ALL) <= 1) {
                float phase = (count <= 1) ? 0.5f : (float) i / (float) (count - 1);
                rgb = DeepElement.chaosEdge(frameTimeSeconds, phase);
            }
            // T370 : les accents étaient sub-pixel (diamètre < 1 px pour les tiers discrets).
            // Agrandissement modéré : ils restent petits, mais sont réellement visibles en jeu.
            float sz = sub.size() * (1.6f + rand.nextFloat() * 0.8f);
            PIXEL_PARTICLES.add(new FloatingPixel(additive,
                    x + ox, y + oy, z + oz, vx, vy, vz, sub.gravity(), sz, rgb,
                    sub.life() + rand.nextInt(4), rand.nextInt(Integer.MAX_VALUE),
                    sub.element() == DeepElement.WATER ? 0.68f : 1.0f));
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
     * T374-PALETTE-LOCK : sélectionne les couleurs exactes des familles présentes dans {@code mask}.
     * La phase parcourt les éléments sans interpoler leurs teintes : aucun mélange rouge-orange
     * ou autre couleur intermédiaire ne peut apparaître. Une famille seule renvoie son rôle exact.
     */
    public static int paletteColor(int mask, int fallback, float phase) {
        return paletteColor(mask, fallback, phase, 0);
    }

    /** Role 0/1/2 = primaire/secondaire/surbrillance; les familles ne sont jamais mélangées en RGB. */
    public static int paletteColor(int mask, int fallback, float phase, int role) {
        int total = Integer.bitCount(mask & deepluckyblock.enchantments.DeepElement.MASK_ALL);
        if (total == 0) return fallback;
        float tri = (phase < 0.5f) ? (phase * 2.0f) : (2.0f - phase * 2.0f);
        tri = Math.max(0.0f, Math.min(1.0f, tri));
        tri = tri * tri * (3.0f - 2.0f * tri);
        int index = Math.min(total - 1, Math.max(0, Math.round(tri * (total - 1))));
        return nthElementColor(mask, index, fallback, role);
    }

    private static int nthElementColor(int mask, int index, int fallback, int role) {
        int seen = 0;
        for (deepluckyblock.enchantments.DeepElement el : deepluckyblock.enchantments.DeepElement.values()) {
            if ((mask & (1 << el.ordinal())) == 0) continue;
            if (seen == index) return switch (role) {
                case 1 -> el.secondary;
                case 2 -> el.highlight;
                default -> el.primary;
            };
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
     * T374-PALETTE-LOCK : couleur d'un rôle quand l'arme porte PLUSIEURS éléments.
     * On parcourt les familles en aller-retour, en gardant chaque couleur exacte : aucune
     * interpolation RGB ne crée une teinte intermédiaire étrangère aux palettes.
     */
    public static int gradientColorFor(net.minecraft.world.item.ItemStack stack, float timeSeconds) {
        return gradientColorFor(stack, timeSeconds, 0);
    }

    /** Dégradé multi-élément pour un rôle chromatique donné : 0 primaire, 1 secondaire, 2 accent. */
    public static int gradientColorFor(net.minecraft.world.item.ItemStack stack, float timeSeconds, int role) {
        List<deepluckyblock.enchantments.DeepElement> els = WeaponVisualEffectId.elementsOf(stack);
        if (els.isEmpty()) return 0;
        float cycle = (timeSeconds * 0.16f) % 2.0f;
        float tri = cycle < 1.0f ? cycle : 2.0f - cycle;
        int index = Math.min(els.size() - 1, Math.max(0, Math.round(tri * (els.size() - 1))));
        deepluckyblock.enchantments.DeepElement selected = els.get(index);
        return elementRoleColor(selected, role);
    }

    /** Famille actuellement sélectionnée pour une arme multi-éléments (alternance sans interpolation). */
    public static deepluckyblock.enchantments.DeepElement gradientElementFor(
            net.minecraft.world.item.ItemStack stack, float timeSeconds) {
        List<deepluckyblock.enchantments.DeepElement> els = WeaponVisualEffectId.elementsOf(stack);
        if (els.isEmpty()) return null;
        float cycle = (timeSeconds * 0.16f) % 2.0f;
        float tri = cycle < 1.0f ? cycle : 2.0f - cycle;
        int index = Math.min(els.size() - 1, Math.max(0, Math.round(tri * (els.size() - 1))));
        return els.get(index);
    }

    private static int elementRoleColor(deepluckyblock.enchantments.DeepElement element, int role) {
        return switch (role) {
            case 1 -> element.secondary;
            case 2 -> element.highlight;
            default -> element.primary;
        };
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
            TRACKED_PROJECTILES.clear();
            PENDING_ECHOES.clear();
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
            TRACKED_PROJECTILES.clear();
            PENDING_ECHOES.clear();
            PLAYER_WAS_SWINGING.clear();
        }
        DeepEnchant.rememberClientAccess(level.registryAccess());
        FireSignatureVfx.beginTerrainTick(level);

        for (Iterator<VfxInstance> it = ACTIVE.iterator(); it.hasNext(); ) {
            VfxInstance inst = it.next();
            inst.age++;
            if (inst.age >= inst.maxAge) {
                it.remove();
                continue;
            }
            if (inst.effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE
                    || inst.effect == WeaponVisualEffectId.SOUL_FIRE_CRESCENT
                    || inst.effect == WeaponVisualEffectId.BRIMSTONE_METEOR) {
                float fireDx = Mth.sin(-inst.yawDeg * Mth.DEG_TO_RAD);
                float fireDz = Mth.cos(-inst.yawDeg * Mth.DEG_TO_RAD);
                if (inst.effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE
                        || inst.effect == WeaponVisualEffectId.SOUL_FIRE_CRESCENT) {
                    fireDx = -fireDx;
                    fireDz = -fireDz;
                }
                float waveShift = inst.effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE
                        ? fireWaveOriginShift(inst) : 0.0f;
                if (inst.effect == WeaponVisualEffectId.BRIMSTONE_METEOR) {
                    FireSignatureVfx.prepareMeteorTerrain(level, inst.x, inst.y, inst.z, inst.sizeScale);
                } else {
                    FireSignatureVfx.prepareTerrain(level, inst.x + fireDx * waveShift, inst.y,
                            inst.z + fireDz * waveShift, fireDx, fireDz,
                            inst.effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE ? 1.0f : inst.sizeScale);
                }
            }
        }

        // Déclenche les entailles d'écho différées (#04 TWIN_ECHO_SLASH), comme dans T342.
        for (Iterator<PendingEcho> it = PENDING_ECHOES.iterator(); it.hasNext(); ) {
            PendingEcho pe = it.next();
            pe.ticksLeft--;
            if (pe.ticksLeft <= 0) {
                pushInstance(new VfxInstance(
                        pe.effect, KIND_SWING_SLASH, pe.intensity,
                        pe.x, pe.y, pe.z, pe.x, pe.y, pe.z,
                        pe.yaw, pe.pitch, -pe.tiltRad, 8, System.nanoTime(), pe.paletteMask));
                it.remove();
            }
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
                // T374-PALETTE-LOCK : la traînée alterne primaire/surbrillance sans rotation de teinte.
                int role = step == 0 ? 0 : 2;
                int rgb = step == 0 ? tp.effect().primaryRgb : tp.effect().highlightRgb;
                if (tp.paletteMask() != 0 && Integer.bitCount(tp.paletteMask() & deepluckyblock.enchantments.DeepElement.MASK_ALL) > 1) {
                    // Le mélange de familles conserve le rôle chromatique de chaque pixel.
                    float phase = ((now & 15L) / 15.0f) * 0.5f + step * 0.5f;
                    rgb = paletteColor(tp.paletteMask(), rgb, phase, role);
                } else if (tp.effect().element() == DeepElement.CHAOS && role == 2) {
                    rgb = DeepElement.chaosEdge(frameTimeSeconds, (now & 15L) / 15.0f);
                }
                PIXEL_PARTICLES.add(new FloatingPixel(
                        tp.effect().additive, px, py, pz,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        0.0f, 0.032f * (0.55f + level.random.nextFloat() * 0.9f), rgb, 9,
                        level.random.nextInt(Integer.MAX_VALUE),
                        tp.effect().element() == DeepElement.WATER ? 0.68f : 1.0f));
            }
        }

        // Détecte les frappes mêlée, gantelets mains nues, et tirs d'arc / arbalète
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p != viewer && p.distanceToSqr(viewer) > 32.0 * 32.0) continue;

            // T342 originel : détecte le début du swing, y compris dans l'air, sans attendre un
            // LivingIncomingDamageEvent. Le rayon part donc de l'arme au moment du geste.
            boolean swinging = p.swinging && p.swingTime <= 2;
            boolean previousSwing = PLAYER_WAS_SWINGING.getOrDefault(p.getId(), false);
            PLAYER_WAS_SWINGING.put(p.getId(), p.swinging);
            if (swinging && !previousSwing) {
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

    /**
     * Déclencheur originel restauré depuis le commit GitHub 4d4182e (T342).
     * L'arc naît devant les yeux, orienté par le regard, et reste hors de la cible : il n'est
     * jamais téléporté vers le torse du mob. Les épées /anim réutilisent ce même swing visuel.
     */
    private static void onPlayerWeaponSwing(Player p) {
        InteractionHand hand = p.swingingArm == null ? InteractionHand.MAIN_HAND : p.swingingArm;
        ItemStack stack = p.getItemInHand(hand);
        if (!isMeleeSlashWeapon(stack)) return;

        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        String animFamily = null;
        var tag = deepluckyblock.util.DeepNbt.tag(stack);
        if (tag != null) {
            String candidate = tag.getString("DlbAnimFamily");
            if (candidate.equals("feu") || candidate.equals("gaia") || candidate.equals("poison")) animFamily = candidate;
        }
        if (layers.isEmpty() && animFamily == null) return;

        int parity = PLAYER_SWING_PARITY.merge(p.getId(), 1, Integer::sum);
        float baseTilt = ((parity & 1) == 0) ? 0.36f : -0.34f;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double ox = eye.x + look.x * 0.55;
        double oy = eye.y - 0.16 + look.y * 0.35;
        double oz = eye.z + look.z * 0.55;
        WeaponVisualEffectId.ResolvedLayer primary = layers.isEmpty() ? null : layers.get(0);
        WeaponVisualEffectId fx = primary != null ? primary.effect()
                : animationSlashEffect(animFamily, p.level().random.nextInt(3));
        int intensity = primary != null ? primary.level() : 6;
        int paletteMask = WeaponVisualEffectId.elementMaskOf(stack);
        if (paletteMask == 0) paletteMask = 1 << fx.element().ordinal();
        int duration = (fx == WeaponVisualEffectId.ANNIHILATING_ARC
                || fx == WeaponVisualEffectId.CARNAGE_BERSERK) ? 11 : 8;
        float tilt = (fx == WeaponVisualEffectId.GUILLOTINE_CLEAVE) ? 1.48f : baseTilt;

        // UN SEUL VfxInstance par swing, exactement comme l'ancien déclencheur T342.
        pushInstance(new VfxInstance(
                fx, KIND_SWING_SLASH, intensity,
                ox, oy, oz, ox + look.x * 3.2, oy + look.y * 3.2, oz + look.z * 3.2,
                p.getYRot(), p.getXRot(), tilt, duration, System.nanoTime(), paletteMask));

        if (layers.isEmpty()) return;
        WeaponVisualEffectId primaryEffect = layers.get(0).effect();
        boolean hasEcho = primaryEffect == WeaponVisualEffectId.TWIN_ECHO_SLASH
                || (layers.size() > 1 && layers.get(1).effect() == WeaponVisualEffectId.TWIN_ECHO_SLASH);
        if (hasEcho && PENDING_ECHOES.size() < 16) {
            PendingEcho pe = new PendingEcho();
            pe.ticksLeft = 4;
            pe.effect = WeaponVisualEffectId.TWIN_ECHO_SLASH;
            pe.intensity = layers.get(0).level();
            pe.x = ox;
            pe.y = oy;
            pe.z = oz;
            pe.yaw = p.getYRot();
            pe.pitch = p.getXRot();
            pe.tiltRad = baseTilt;
            pe.paletteMask = WeaponVisualEffectId.elementMaskOf(stack);
            PENDING_ECHOES.add(pe);
        }
    }

    private static WeaponVisualEffectId animationSlashEffect(String family, int index) {
        if (family.equals("feu")) {
            return switch (index) {
                case 0 -> WeaponVisualEffectId.MOLTEN_FIRE_WAVE;
                case 1 -> WeaponVisualEffectId.SOUL_FIRE_CRESCENT;
                default -> WeaponVisualEffectId.BRIMSTONE_METEOR;
            };
        }
        if (family.equals("poison")) {
            return switch (index) {
                case 0 -> WeaponVisualEffectId.VENOM_BRIAR_FANG;
                case 1 -> WeaponVisualEffectId.MIASMA_SHROUD;
                default -> WeaponVisualEffectId.PLAGUE_NOVA;
            };
        }
        return switch (index) {
            case 0 -> WeaponVisualEffectId.ZEPHYR_WIND_LANCE;
            case 1 -> WeaponVisualEffectId.THORN_MAW_ROOT;
            default -> WeaponVisualEffectId.VERDANT_SAP_BLOOM;
        };
    }

    private static boolean isMeleeSlashWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem
                || item instanceof TridentItem || item instanceof MaceItem) {
            return true;
        }
        return item instanceof TieredItem && WeaponVisualEffectId.isWeaponItem(stack);
    }

    /** Budget de cubes par batch : évite de saturer le ByteBufferBuilder partagé de renderBuffers(). */
    private static final int MAX_CUBES_PER_BATCH = 900;
    /** Plafond dur par frame : au-delà, on arrête simplement d'émettre (jamais de pic incontrôlé). */
    private static final int HARD_CUBE_CAP_PER_FRAME = 7000;
    /** Écume blanche réservée à quelques cubes sur la lèvre supérieure des vagues. */
    private static final int WATER_FOAM_RGB = 0xF4FDFF;
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
            int alpha = Math.round(235.0f * (1.0f - t) * fp.alphaScale);

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
                        detailMode == 2 ? shadeRgb(fp.rgb, 1.08f) : fp.rgb, Math.round(alpha * 0.72f));
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

            // T367-VOXEL-MATERIAL : la matière décide du rendu (les éléments fluides passent en
            // transparence réelle au lieu d'être additifs).
            applyVoxelMaterial(inst.effect);
            VertexConsumer vc;
            if (useAdditivePass(inst.effect)) {
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
            // Molten garde son corridor monde verrouillé : le scale des voxels est local à sa maille.
            mobScale = inst.effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE ? 1.0f : inst.sizeScale;
            mobScaleActive = Math.abs(mobScale - 1.0f) > 0.02f;
            // Les sommets déjà émis par les rendus sont en coordonnées RELATIVES à la caméra.
            // Le pivot doit être dans le même repère : utiliser ici la position monde de l'effet
            // décalait les petites animations vers le ciel et les grandes sous le point d'impact.
            mobPivotX = (float) (inst.x - camPos.x);
            mobPivotY = (float) (inst.y - camPos.y);
            mobPivotZ = (float) (inst.z - camPos.z);
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
                cubeVeil = 1.0f;
                cubeGloss = 0.0f;
                cubeTex = 0.10f;
                voxelElement = DeepElement.EARTH;
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

        if (inst.effect == WeaponVisualEffectId.SPECTRAL_REACH) {
            float tx = (float) (inst.tx - cam.x);
            float ty = (float) (inst.ty - cam.y);
            float tz = (float) (inst.tz - cam.z);
            float reach = Math.min(1.0f, t * 2.3f);
            emitSteppedVoxelLine(vc, mat, tmp, rx, ry, rz,
                    Mth.lerp(reach, rx, tx), Mth.lerp(reach, ry, ty), Mth.lerp(reach, rz, tz),
                    0.045f, 0xFFFFFF, inst.effect.primaryRgb, Math.round(230 * (1.0f - t)));
            return;
        }

        float sweepDeg = switch (inst.effect) {
            case CARNAGE_BERSERK -> 320.0f;
            case ANNIHILATING_ARC -> 175.0f;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT -> 150.0f;
            default -> 125.0f;
        };
        float radius = switch (inst.effect) {
            case ANNIHILATING_ARC -> 2.10f + 0.95f * t;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, CARNAGE_BERSERK -> 1.60f + 0.45f * t;
            default -> 1.35f + 0.22f * t;
        };
        float voxelHalf = switch (inst.effect) {
            case RAZOR_GLEAM -> 0.032f;
            case ANNIHILATING_ARC -> 0.058f;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, CARNAGE_BERSERK, SEISMIC_TECTONIC -> 0.048f;
            default -> 0.038f;
        };

        int alpha = Math.round(238.0f * (1.0f - t * t));
        emitVoxelSlashArc(vc, mat, tmp, rx, ry, rz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                radius, voxelHalf, sweepDeg, t, inst.effect.primaryRgb, inst.effect.secondaryRgb, alpha);

        if (inst.effect == WeaponVisualEffectId.SUPREME_PRISM_EDGE) {
            emitVoxelSlashArc(vc, mat, tmp, rx, ry, rz, inst.yawDeg, inst.pitchDeg, -inst.tiltRad * 0.85f,
                    radius * 0.92f, voxelHalf * 0.85f, sweepDeg * 0.88f, t,
                    inst.effect.secondaryRgb, 0xFFFFFF, Math.round(alpha * 0.85f));
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

        int pixelSteps = Math.max(14, Math.round(sweepDeg / 6.5f));
        float halfArc = (sweepDeg * 0.5f) * Mth.DEG_TO_RAD;
        float headU = Mth.clamp(progress * 1.35f, 0.0f, 1.0f);

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
            float hs = baseHalfSize * (0.55f + 0.75f * taper) * (isHead ? 1.25f : 1.0f);
            int rgb = isHead ? 0xFFFFFF : (distBehind < 0.28f ? coreRgb : trailRgb);
            int a = Math.round(peakAlpha * Math.max(0.2f, 1.0f - Math.max(0.0f, distBehind) * 1.35f));

            emitVoxelCube(vc, mat, tmp, px, py, pz, hs, hs, hs, rgb, a);
            // Épaisseur blocky doublée d'un mini-cube intérieur décalé d'un pixel 1/16
            if (taper > 0.45f) {
                float pxIn = quantize(cx + dirX * (radius - baseHalfSize * 2.2f), 0.0625f);
                float pyIn = quantize(cy + dirY * (radius - baseHalfSize * 2.2f), 0.0625f);
                float pzIn = quantize(cz + dirZ * (radius - baseHalfSize * 2.2f), 0.0625f);
                emitVoxelCube(vc, mat, tmp, pxIn, pyIn, pzIn, hs * 0.78f, hs * 0.78f, hs * 0.78f, trailRgb, Math.round(a * 0.8f));
            }
        }
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
                            prof.spin() * progress * 2.2f + r * 1.1f, c1, c2, accentRgb,
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
                                    (i & 1) == 0 ? c2 : accentRgb,
                                    Math.round(peakAlpha * 0.8f));
                        }
                    }
                }
                if (prof.arms() >= 2) {
                    emitPixelCross3D(vc, mat, tmp, cx, cy + rise * progress, cz,
                            0.0625f, Math.min(4, prof.arms()), accentRgb, coreRgb, peakAlpha);
                }
            }

            case WeaponVfxMotion.SHAPE_LANCE -> {
                // Estoc rectiligne : l'arme "pousse" droit devant, la pointe s'allume.
                float len = radius * (0.85f + 0.9f * Mth.clamp(progress * 1.5f, 0.0f, 1.0f));
                float tx = cx + fx * len;
                float ty = cy + fy * len + rise * progress;
                float tz2 = cz + fz * len;
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz, tx, ty, tz2,
                        baseHalf * (1.0f - 0.35f * progress), accentRgb, coreRgb, peakAlpha);
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy, cz, tx * 0.94f, ty * 0.94f, tz2 * 0.94f,
                        baseHalf * 0.5f, trailRgb, accentRgb, Math.round(peakAlpha * 0.55f));
                if (prof.arms() >= 2) {
                    emitPixelCross3D(vc, mat, tmp, tx, ty, tz2, 0.0625f, 4, accentRgb, trailRgb, peakAlpha);
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
                            (li % 4 == 0) ? accentRgb : ((li & 1) == 0 ? coreRgb : trailRgb),
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
                            accentRgb, Math.round(peakAlpha * 0.85f));
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
                            baseHalf * (1.0f - 0.45f * progress), accentRgb, c, peakAlpha);
                }
                emitPixelCross3D(vc, mat, tmp, cx, cy + rise * progress, cz, 0.0625f,
                        Math.min(5, prof.arms()), accentRgb, accentRgb, peakAlpha);
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
                // T365-HOLOGRAM : le disque de l'éclipse a maintenant une ÉPAISSEUR (3 anneaux
                // inclinés + cœur suspendu) au lieu d'être un plan face caméra.
                emitPixelHoloDisc3D(vc, mat, tmp, cx, cy + 0.32f + rise * progress, cz, disc,
                        yawRad, pitchRad + 1.35f, trailRgb, trailRgb, peakAlpha, salt);
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
                            baseHalf * 0.55f, baseHalf * 0.55f, baseHalf * 0.55f, accentRgb,
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
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 2, accentRgb, accentRgb,
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
                    // T364-GRADIENTS : l'entaille n'a plus deux bandes de couleur franches mais un
                    // DÉGRADÉ continu le long de la lame : blanc incandescent à la tête, cœur de
                    // l'élément au milieu, accent sombre dans la traînée.
                    int rgb = isHead ? accentRgb
                            : jitterRgb(rampRgb(Mth.clamp(1.0f - Math.max(0.0f, distBehind), 0.0f, 1.0f),
                                    accentRgb, trailRgb, coreRgb, accentRgb), salt + i * 41, 0.045f);
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
                                        jitterRgb(rampRgb(sh / 2.0f, accentRgb, coreRgb, trailRgb, accentRgb),
                                                salt + sh * 13 + i, 0.04f),
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
                                    baseHalf * 0.6f, (i & 1) == 0 ? accentRgb : coreRgb, accentRgb, a);
                        }
                        case WeaponVfxMotion.SHAPE_DRIP -> {
                            // goutte + traînée qui tombe (le sang, le venin)
                            emitPixelDroplet3D(vc, mat, tmp, px, py, pz, hs,
                                    (i & 1) == 0 ? coreRgb : trailRgb, accentRgb, a, salt + i);
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
                                        (c2 == 0) ? accentRgb : ((c2 & 1) == 0 ? coreRgb : accentRgb),
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
                                    (Math.abs(distBehind) < 0.05f) ? accentRgb : ((i & 1) == 0 ? coreRgb : trailRgb), a);
                            if ((i & 3) == 0) {
                                emitVoxelCube(vc, mat, tmp,
                                        quantize(px + sx * jag * 1.8f, 0.0625f),
                                        quantize(py + sy * jag * 1.8f, 0.0625f),
                                        quantize(pz + sz * jag * 1.8f, 0.0625f),
                                        hs * 0.3f, hs * 0.3f, hs * 0.3f, accentRgb, Math.round(a * 0.6f));
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
                                        (petal == 0) ? accentRgb : ((petal & 1) == 0 ? coreRgb : accentRgb),
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
                                    hs * 0.45f, hs * 0.45f, hs * 0.45f, accentRgb, Math.round(a * 0.8f));
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
                                            hs * 0.32f, hs * 0.26f, hs * 0.32f, accentRgb, Math.round(a * 0.55f));
                                }
                            }
                            // Une seule ligne de lame : les bras supplémentaires du profil ne
                            // déclenchent plus un deuxième arc générique superposé.
                        }
                    }
                }

                // DOUBLE_ARC et PRISM utilisent eux aussi leur seul arc principal : aucune
                // récursion ne crée de lignes de lame secondaires ou successives.
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
        int aRgb = inst.highlight();
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
                        inst.seed + inst.age / 2, aRgb, pRgb, alpha, 0.036f);
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
                            0.024f, 0.024f, 0.024f, aRgb, Math.round(alpha * 0.5f));
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
                                0.024f, aRgb, sRgb, Math.round(alpha * 0.7f));
                    }
                }
            }
            case WeaponVfxMotion.SHAPE_PILLAR -> {
                // colonne de lumière : le rayon est épais, crénelé, avec halo large
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1, 0.075f, aRgb, pRgb, alpha);
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
                                0.026f, sRgb, aRgb, Math.round(alpha * 0.7f), salt + i);
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
                                0.030f, (jaw & 1) == 0 ? pRgb : sRgb, aRgb, alpha);
                    }
                }
            }
            default -> {
                // estoc fin et net + étoile à l'impact
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1,
                        0.036f * (1.0f - t * 0.45f), aRgb, pRgb, alpha);
                emitPixelCross3D(vc, mat, tmp, x1, y1, z1, 0.0625f,
                        Math.min(4, 2 + inst.intensity / 4), aRgb, sRgb, alpha);
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

        // T362-ELEMENT-IMPACT : une animation PAR ÉLÉMENT, avec son propre déroulé dans le temps.
        // Avant, presque tout finissait en « disque au sol + croix verticale » : de loin, le feu, la
        // glace et le vide se ressemblaient. Désormais chaque élément a sa silhouette ET son timing
        // (le feu monte, l'eau gicle puis retombe en rides, la glace pousse puis se brise, l'éclair
        // clignote par à-coups, la terre se soulève puis retombe lourdement, la mâchoire se referme,
        // le vide implose, l'ombre rampe puis se rétracte...).
        ImpactView iv = new ImpactView(vc, mat, tmp, cx, cy, cz, inst, t,
                pRgb, sRgb, aRgb, motion, mSalt, fadeA);
        switch (inst.effect.element()) {
            case FIRE -> impactFire(iv);
            case WATER -> impactWater(iv);
            case THUNDER -> impactThunder(iv);
            case EARTH -> impactEarth(iv);
            case METAL -> impactMetal(iv);
            case WOOD -> impactWood(iv);
            case POISON -> impactPoison(iv);
            case LIGHT -> impactLight(iv);
            case SHADOW -> impactShadow(iv);
            case BLOOD -> impactBlood(iv);
            case VOID -> impactVoid(iv);
            case CHAOS -> impactChaos(iv);
        }
    }

    /**
     * T362-ELEMENT-IMPACT / T363-ROTATED-VOXELS : tout ce qu'il faut à une animation d'impact pour
     * se dessiner, plus une boîte à outils de formes (cube incliné, dalle, éclat facetté, liane
     * courbe, vague déferlante, langue de flamme, anneau, goutte, gerbe balistique, éclair).
     *
     * <p>Chaque élément dispose donc de sa propre silhouette ET de son propre rythme, et joue de
     * tout : tailles différentes, inclinaisons sur trois axes, opacités dégradées.</p>
     */
    private record ImpactView(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz,
                              VfxInstance inst, float t,
                              int pRgb, int sRgb, int aRgb,
                              WeaponVfxMotion.Profile m, int salt, int fadeA) {

        /**
         * Géométrie de base non scalée. {@link #putPosColor} applique inst.sizeScale une seule
         * fois autour du centre du mob, aux positions ET aux dimensions des voxels. Remettre
         * inst.sizeScale ici produisait une échelle quadratique et déréglait toutes les animations.
         */
        float S() {
            return 1.0f;
        }

        int age() {
            return inst.age;
        }

        /** Progression qui démarre vite puis ralentit (jaillissement, ouverture). */
        float easeOut() {
            return 1.0f - (1.0f - t) * (1.0f - t);
        }

        /** Progression qui démarre lentement puis accélère (chute, implosion). */
        float easeIn() {
            return t * t;
        }

        float hash(int a) {
            return pixelHash(salt, a);
        }

        /**
         * Teinte la plus sombre du dégradé (creux, base, fond de la vague) : on mélange cœur et
         * halo de l'élément puis on assombrit franchement, pour que le bas d'une forme « rentre »
         * dans l'ombre et que le haut paraisse lumineux par contraste.
         */
        int deep() {
            // On part TOUJOURS de la teinte la plus sombre des deux (peu importe laquelle est le
            // "cœur" dans la palette de l'enchantement) : le bas des formes est donc réellement
            // dans l'ombre, et non plus aléatoirement plus clair que le haut.
            int darker = (luminanceRgb(pRgb) <= luminanceRgb(sRgb)) ? pRgb : sRgb;
            return shadeRgb(mixRgb(darker, pRgb, 0.30f), 0.52f);
        }

        /** Teinte lumineuse intermédiaire (crête, partie éclairée) : garantie plus claire que le corps. */
        int high() {
            int brighter = (luminanceRgb(pRgb) >= luminanceRgb(sRgb)) ? pRgb : sRgb;
            return shadeRgb(mixRgb(brighter, aRgb, 0.35f), 1.08f);
        }

        /** Pointe lumineuse : toujours la surbrillance exacte de la famille active. */
        int tip() {
            return aRgb;
        }

        /** Dégradé continu de l'élément, u = 0 (creux) .. 1 (pointe). */
        int grad(float u) {
            return rampRgb(u, deep(), pRgb, high(), tip());
        }

        /** Dégradé + nuance propre à ce pixel (aucun cube n'a exactement la même couleur). */
        int grad(float u, int saltOffset) {
            return jitterRgb(grad(u), salt + saltOffset, 0.045f);
        }

        /** Direction horizontale de l'attaque (vers la cible). */
        float dirX() {
            return Mth.sin(-inst.yawDeg * Mth.DEG_TO_RAD);
        }

        float dirZ() {
            return Mth.cos(-inst.yawDeg * Mth.DEG_TO_RAD);
        }

        float yawRad() {
            return -inst.yawDeg * Mth.DEG_TO_RAD;
        }

        /** Mini-cube 3D aligné sur la grille. */
        void cube(float x, float y, float z, float s, int rgb, int a) {
            box(x, y, z, s, s, s, rgb, a);
        }

        /** Cube à dimensions libres (dalles, lames, pétales, éclats). */
        void box(float x, float y, float z, float hx, float hy, float hz, int rgb, int a) {
            emitVoxelCube(vc, mat, tmp, quantize(x, 0.0625f), quantize(y, 0.0625f), quantize(z, 0.0625f),
                    hx, hy, hz, rgb, a);
        }

        /** Cube INCLINÉ sur les trois axes (la brique des formes organiques). */
        void rot(float x, float y, float z, float hx, float hy, float hz,
                 float yaw, float pitch, float roll, int rgb, int a) {
            emitVoxelBoxRotated(vc, mat, tmp, quantize(x, 0.0625f), quantize(y, 0.0625f), quantize(z, 0.0625f),
                    hx, hy, hz, yaw, pitch, roll, rgb, a);
        }

        void line(float x0, float y0, float z0, float x1, float y1, float z1,
                  float hs, int core, int outer, int a) {
            emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1, hs, core, outer, a);
        }

        void ring(float y, float radius, int rgbA, int rgbB, int a, int style) {
            emitPixelRing3D(vc, mat, tmp, cx, y, cz, radius, rgbA, rgbB, a, style);
        }

        /** Éclat facetté (glace, métal, pierre, croc). */
        void shard(float x, float y, float z, float length, float half,
                   float dx, float dy, float dz, int rgb, int tip, int a, int s) {
            emitPixelShard3D(vc, mat, tmp, x, y, z, length, half, dx, dy, dz, rgb, tip, a, s);
        }

        /** Liane / tentacule / veine courbe. */
        void tendril(float x0, float y0, float z0, float x1, float y1, float z1,
                     float bow, int segments, float half, int rgb, int tip, int a) {
            emitPixelTendril3D(vc, mat, tmp, x0, y0, z0, x1, y1, z1, bow, segments, half, rgb, tip, a, salt);
        }

        /** Vague déferlante (elle avance dans l'axe de l'attaque). */
        void wave(float x, float y, float z, float radius, float height, float curl,
                  int deep, int foam, int a) {
            emitPixelWave3D(vc, mat, tmp, x, y, z, radius, height, yawRad(), curl, t,
                    pRgb, deep, foam, a, salt);
        }

        /** Langue de flamme courbe. */
        void lick(float x, float y, float z, float height, float baseHalf, float sway,
                  float phase, int tip, int a) {
            emitPixelFlameLick3D(vc, mat, tmp, x, y, z, height, baseHalf, sway, phase,
                    pRgb, tip, a, salt);
        }

        /** Éclair 3D : le tracé est re-tiré à chaque image (comme un orage qui se re-ramifie). */
        void bolt(float x0, float y0, float z0, float x1, float y1, float z1,
                  int nodes, float jitter, int core, int glow, int a, float hs) {
            long sd = (long) salt * 2654435761L + (long) inst.age * 7919L;
            emitVoxelLightning(vc, mat, tmp, x0, y0, z0, x1, y1, z1, nodes, jitter, sd, core, glow, a, hs);
        }

        void drop(float x, float y, float z, float step, int body, int light, int a, int idx) {
            emitPixelDroplet3D(vc, mat, tmp, quantize(x, 0.0625f), quantize(y, 0.0625f), quantize(z, 0.0625f),
                    step, body, light, a, salt + idx);
        }

        void rocks(float x, float y, float z, float step, int count, int crust, int glow, int hot, int a) {
            emitPixelRockCluster3D(vc, mat, tmp, x, y, z, step, count, crust, glow, hot, a, salt);
        }

        void cross(int arms, int core, int tip, int a) {
            emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, arms, core, tip, a);
        }

        /**
         * T366-VOXEL-SCULPT : <b>vraie sculpture voxel dense</b> (masse pleine sculptée, comme les
         * modèles MagicaVoxel) centrée sur l'impact. La résolution s'adapte au budget de cubes.
         *
         * @param shape    l'une des constantes {@code SCULPT_*}
         * @param radius   rayon de la boîte englobante (avant facteur d'échelle du mob)
         * @param alphaMul multiplicateur d'opacité (fondu de l'effet)
         */
        int sculpt(int shape, float radius, float alphaMul) {
            // Seules les formes tournantes pivotent ; la vague, la dune et la colonne restent
            // orientées vers la cible (leur silhouette doit rester lisible).
            boolean spin = shape == SCULPT_ORB || shape == SCULPT_TWISTER || shape == SCULPT_BURST;
            return emitVoxelSculpt(vc, mat, tmp, shape, cx, cy, cz, radius * S(),
                    yawRad() + (spin ? inst.age * 0.045f : 0.0f), t,
                    deep(), pRgb, high(), tip(), salt, Math.round(fadeA() * alphaMul),
                    Math.min(1600, Math.max(280, HARD_CUBE_CAP_PER_FRAME - cubesSinceFlush)));
        }

        /** Cage filaire holographique (vrais cubes, volume + profondeur). */
        void cage(float half, float yaw, float pitch, float roll, int edge, int node, int a) {
            emitPixelHoloCage3D(vc, mat, tmp, cx, cy, cz, half, yaw, pitch, roll, edge, node, a, salt);
        }

        /** Globe holographique (anneaux de latitude + méridiens). */
        void holoSphere(float radius, float yaw, int ring, int accent, int a) {
            emitPixelHoloSphere3D(vc, mat, tmp, cx, cy, cz, radius, yaw, ring, accent, a, salt);
        }

        /** Disque holographique épais (remplace le disque face caméra). */
        void holoDisc(float radius, int rgbA, int rgbB, int a) {
            emitPixelHoloDisc3D(vc, mat, tmp, cx, cy, cz, radius, yawRad(), 1.35f, rgbA, rgbB, a, salt);
        }

        /** L'arc de lame propre à l'enchantement (mouvement T361 conservé). */
        void slash(float radius, float baseHalf, float sweepDeg, int core, int trail, int accent, int a) {
            emitMotionSlash(vc, mat, tmp, cx, cy, cz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                    radius * S(), baseHalf, sweepDeg, t, core, trail, accent, a, m, salt, inst.effect.ordinal());
        }

        /**
         * Projectile balistique : il part vers le haut puis RETOMBE (gravité) — c'est ce qui distingue
         * une gerbe d'eau ou une braise d'une étincelle qui fuse en ligne droite.
         */
        void lob(float vx, float vy, float vz, float s, int rgb, int a) {
            float time = t * 0.90f;
            float g = 4.6f;
            cube(cx + vx * time * S(),
                    cy + (vy * time - 0.5f * g * time * time) * S(),
                    cz + vz * time * S(), s, rgb, a);
        }
    }

    // ============================ ANIMATIONS D'IMPACT PAR ÉLÉMENT ============================

    /**
     * 火 FEU — des langues de flamme <b>courbes et inclinées</b> qui montent en vacillant, des
     * braises projetées qui retombent, et un sol qui rougeoie. Aucune pile de blocs droits.
     */
    /** Origine décalée pour que la vague naisse devant le joueur, jamais sur lui. */
    private static float fireWaveOriginShift(VfxInstance inst) {
        double dx = inst.tx - inst.x;
        double dz = inst.tz - inst.z;
        float attackerDistance = (float) Math.sqrt(dx * dx + dz * dz);
        return Math.max(0.0f, 3.0f - attackerDistance);
    }

    private static void impactFire(ImpactView v) {
        int signature = switch (v.inst().effect) {
            case MOLTEN_FIRE_WAVE -> FireSignatureVfx.MOLTEN;
            case SOUL_FIRE_CRESCENT -> FireSignatureVfx.SOUL;
            case BRIMSTONE_METEOR -> FireSignatureVfx.METEOR;
            default -> -1;
        };
        if (signature >= 0) {
            // Graine stable pendant toute la chorégraphie : la direction céleste du météore ne
            // doit pas changer à chaque tick, contrairement au sel animé des impacts génériques.
            int fireSalt = (int) (v.inst().seed & 0x7FFFFFFFL);
            float fireDirX = signature == FireSignatureVfx.MOLTEN || signature == FireSignatureVfx.SOUL
                    ? -v.dirX() : v.dirX();
            float fireDirZ = signature == FireSignatureVfx.MOLTEN || signature == FireSignatureVfx.SOUL
                    ? -v.dirZ() : v.dirZ();
            float waveShift = signature == FireSignatureVfx.MOLTEN ? fireWaveOriginShift(v.inst()) : 0.0f;
            boolean previousPulse = voxelPulseEnabled;
            // La vague doit rester un volume soudé : la pulsation globale ouvrait des trous entre
            // les mailles. Les trajectoires et la lave restent animées, les dimensions sont stables.
            voxelPulseEnabled = false;
            try {
                FireSignatureVfx.render(Minecraft.getInstance().level,
                        v.inst().x + fireDirX * waveShift, v.inst().y, v.inst().z + fireDirZ * waveShift,
                        v.vc(), v.mat(), v.tmp(), v.cx() + fireDirX * waveShift, v.cy(),
                        v.cz() + fireDirZ * waveShift, v.t(),
                        fireDirX, fireDirZ, signature, v.S(),
                        signature == FireSignatureVfx.MOLTEN ? 1.0f : v.inst().sizeScale,
                        v.fadeA(), fireSalt);
            } finally {
                voxelPulseEnabled = previousPulse;
            }
            return;
        }
        if (v.inst().effect == WeaponVisualEffectId.MOLTEN_FIRE_WAVE) {
            impactMoltenFireWave(v);
            return;
        }
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        int licks = Mth.clamp(v.m().arms() + 2, 4, 8);
        float grow = 0.45f + 1.70f * v.easeOut();

        // T366-VOXEL-SCULPT : colonne de flammes PLEINE, torsadée, à langues (masse, pas de fils).
        v.sculpt(SCULPT_COLUMN, 0.80f + 0.55f * v.easeOut(), 0.94f);

        for (int i = 0; i < licks; i++) {
            float ang = i * (float) (Math.PI * 2.0 / licks) + v.hash(i * 13) * 0.55f;
            float rad = (0.16f + 0.52f * v.hash(i * 7)) * S;
            float h = (0.55f + 0.95f * v.hash(i * 29)) * grow * S;
            float bx = v.cx() + Mth.cos(ang) * rad;
            float bz = v.cz() + Mth.sin(ang) * rad;
            v.lick(bx, v.cy() - 0.42f, bz, h, (0.052f + 0.014f * v.hash(i * 5)) * S,
                    (0.10f + 0.09f * v.hash(i * 11)) * S, v.age() * 0.50f + i * 1.7f,
                    v.aRgb(), Math.round(v.fadeA() * (0.90f - 0.20f * t)));
        }

        // Sol qui rougeoie : braises qui s'élargissent lentement puis s'éteignent.
        v.ring(-0.46f, (0.32f + 1.40f * v.easeOut()) * S, v.pRgb(), v.sRgb(),
                Math.round(v.fadeA() * 0.72f), PIXEL_RING_DASHED);

        // Braises projetées (tailles et opacités variées), qui montent puis retombent.
        for (int e = 0; e < 10; e++) {
            float ang = e * 0.628f + v.hash(e) * 0.6f;
            float sp = (0.55f + 1.05f * v.hash(e * 31)) * S;
            v.lob(Mth.cos(ang) * sp, 1.30f + 0.90f * v.hash(e * 17), Mth.sin(ang) * sp,
                    0.022f + 0.016f * v.hash(e * 5),
                    (e & 1) == 0 ? v.aRgb() : (e % 3 == 0 ? v.pRgb() : v.sRgb()),
                    Math.round(v.fadeA() * (0.55f + 0.40f * v.hash(e * 3))));
        }

        if (fx == WeaponVisualEffectId.BRIMSTONE_METEOR) {
            // MÉTÉORE : deux blocs de roche en fusion tombent du ciel et éclatent au sol.
            for (int r = 0; r < 2; r++) {
                float u = Mth.clamp(t * 1.45f - r * 0.20f, 0.0f, 1.0f);
                float hy = v.cy() + (1.0f - u) * 3.6f * S;
                v.rocks(v.cx() + (r - 0.5f) * 0.55f * S, hy, v.cz() + (r * 0.35f - 0.17f) * S,
                        0.055f * S, 5, v.sRgb(), v.pRgb(), v.aRgb(), v.fadeA());
            }
        } else if (fx == WeaponVisualEffectId.SOUL_FIRE_CRESCENT) {
            // FEU D'ÂME : trois âmes tourbillonnantes en cubes inclinés (feu froid).
            for (int w = 0; w < 3; w++) {
                float ang = v.age() * 0.30f + w * 2.10f;
                float rad = (0.55f + 0.35f * w) * S * (0.60f + 0.50f * t);
                for (int k = 0; k < 3; k++) {
                    float u = k / 2.0f;
                    v.rot(v.cx() + Mth.cos(ang + u) * rad, v.cy() + 0.15f + u * 0.45f * S,
                            v.cz() + Mth.sin(ang + u) * rad,
                            0.040f * S * (1.0f - 0.3f * u), 0.055f * S, 0.030f * S,
                            ang, u * 0.8f, ang * 0.5f, w == 0 ? v.aRgb() : v.aRgb(),
                            Math.round(v.fadeA() * (1.0f - 0.3f * u)));
                }
            }
        }
    }

    /**
     * MOLTEN FIRE WAVE — une véritable déferlante de lave, et non la colonne de feu générique.
     * La sculpture monte depuis le sol, avance vers la cible, enroule sa crête puis s'affaisse.
     * La nappe au sol et les quelques projections naissent de cette même masse : aucune pluie de
     * particules décorative ne vient brouiller la silhouette de la vague.
     */
    private static void impactMoltenFireWave(ImpactView v) {
        float t = v.t();
        float S = v.S();
        float riseU = Mth.clamp(t / 0.48f, 0.0f, 1.0f);
        float rise = riseU * riseU * (3.0f - 2.0f * riseU);
        float curlU = Mth.clamp((t - 0.24f) / 0.44f, 0.0f, 1.0f);
        float curl = curlU * curlU * (3.0f - 2.0f * curlU);
        float collapseU = Mth.clamp((t - 0.68f) / 0.32f, 0.0f, 1.0f);
        float collapse = collapseU * collapseU * (3.0f - 2.0f * collapseU);
        float birthU = Mth.clamp(t / 0.18f, 0.0f, 1.0f);
        float birth = birthU * birthU * (3.0f - 2.0f * birthU);
        float sinkU = Mth.clamp((t - 0.76f) / 0.24f, 0.0f, 1.0f);
        float sink = sinkU * sinkU * (3.0f - 2.0f * sinkU);
        float envelope = birth * (1.0f - sink);
        int effectA = Math.round(v.fadeA() * envelope);
        // Hors sol au début comme à la fin : même à alpha nul, aucune géométrie ne dépasse.
        float depthOffset = -((1.0f - birth) + sink) * 1.90f * S;

        // 1) MASSE PRINCIPALE : une large muraille creusée sous sa lèvre. Elle se déplace dans
        // l'axe de l'attaque pendant que son SDF fait monter, enrouler puis abaisser la crête.
        float travel = (-0.30f + 1.45f * v.easeOut()) * S;
        float wx = v.cx() + v.dirX() * travel;
        float wz = v.cz() + v.dirZ() * travel;
        float radius = (1.12f + 0.18f * rise) * S;
        emitVoxelSculpt(v.vc(), v.mat(), v.tmp(), SCULPT_MOLTEN_WAVE,
                wx, v.cy() + 0.20f * S + depthOffset, wz, radius, v.yawRad(), t,
                v.deep(), v.pRgb(), v.high(), v.tip(), v.salt(),
                Math.round(effectA * 0.98f), 1100);

        // 2) CONTACT SOL : des dalles de lave s'ouvrent derrière le front, jamais deux simples
        // lignes. Chaque dalle a une croûte sombre et un cœur chaud légèrement surélevé.
        float lateralX = v.dirZ();
        float lateralZ = -v.dirX();
        float groundY = v.cy() - 0.48f * S + depthOffset;
        for (int row = 0; row < 6; row++) {
            float reveal = Mth.clamp((t - row * 0.055f) / 0.20f, 0.0f, 1.0f);
            if (reveal <= 0.0f) continue;
            float along = (-0.42f + row * 0.30f + 0.20f * v.easeOut()) * S;
            for (int col = -3; col <= 3; col++) {
                float h = v.hash(row * 41 + col * 17 + 100);
                if (h < 0.16f && row > 1) continue;
                float side = col * 0.245f * S;
                float px = v.cx() + v.dirX() * along + lateralX * side;
                float pz = v.cz() + v.dirZ() * along + lateralZ * side;
                float hs = (0.105f + 0.025f * h) * S * reveal;
                int crust = shadeRgb(v.sRgb(), 0.62f + 0.16f * h);
                v.rot(px, groundY, pz, hs, 0.030f * S, hs * 0.90f,
                        v.yawRad(), 0.0f, 0.0f, crust,
                        Math.round(effectA * (0.72f + 0.20f * reveal)));
                if ((row + col & 1) == 0) {
                    v.box(px, groundY + 0.040f * S, pz,
                            hs * 0.48f, 0.018f * S, hs * 0.62f,
                            h > 0.72f ? v.aRgb() : v.pRgb(),
                            Math.round(effectA * 0.88f * reveal));
                }
            }
        }

        // 3) SPLASH LIQUIDE : 35 parcels, pas des tiges de flamme. Chaque parcel part de la
        // position historique de la crête, avec sa propre force. Les blobs soudés se séparent en
        // gouttes, subissent la gravité puis fusionnent en une flaque lors du contact avec le sol.
        float contactGround = v.cy() - 0.48f * S;
        for (int p = 0; p < 35; p++) {
            int lane = p % 7 - 3;
            int burst = p / 7;
            float h = v.hash(p * 37 + 300);
            float launch = 0.24f + burst * 0.045f + h * 0.025f;
            float age = t - launch;
            if (age < 0.0f) continue;

            float launchRiseU = Mth.clamp(launch / 0.48f, 0.0f, 1.0f);
            float launchRise = launchRiseU * launchRiseU * (3.0f - 2.0f * launchRiseU);
            float launchCurlU = Mth.clamp((launch - 0.24f) / 0.44f, 0.0f, 1.0f);
            float launchCurl = launchCurlU * launchCurlU * (3.0f - 2.0f * launchCurlU);
            float launchTravel = -0.30f + 1.45f * (1.0f - (1.0f - launch) * (1.0f - launch));
            float launchForward = launchTravel + 0.52f + 0.54f * launchCurl;
            float side = (lane * 0.24f + (v.hash(p * 17 + 8) - 0.5f) * 0.04f) * S;
            float ox = v.cx() + v.dirX() * launchForward * S + lateralX * side;
            float oy = v.cy() + (0.48f + 0.72f * launchRise) * S;
            float oz = v.cz() + v.dirZ() * launchForward * S + lateralZ * side;

            float force = 0.72f + 0.62f * h;
            float vf = (1.00f + 1.25f * v.hash(p * 41 + 11)) * force * S;
            float vy = (1.75f + 0.85f * v.hash(p * 43 + 19)) * force * S;
            float vs = (lane * 0.055f + (v.hash(p * 47 + 23) - 0.5f) * 0.28f) * S;
            float vx = v.dirX() * vf + lateralX * vs;
            float vz = v.dirZ() * vf + lateralZ * vs;
            float gravity = 6.60f * S;
            float time = age * 1.90f;
            float hitTime = (vy + Mth.sqrt(vy * vy + 2.0f * gravity * (oy - contactGround))) / gravity;

            if (time >= hitTime) {
                // Contact : expansion courte et aplatie, puis fusion visuelle avec les dalles.
                float since = time - hitTime;
                if (since > 0.16f) continue;
                float px = ox + vx * hitTime;
                float pz = oz + vz * hitTime;
                float spread = 1.0f + since * 10.0f;
                float hs = (0.055f + 0.025f * h) * S * spread;
                int col = mixRgb(v.sRgb(), v.pRgb(), 0.45f + 0.30f * h);
                v.box(px, contactGround + 0.018f * S, pz, hs, 0.015f * S, hs * 0.75f,
                        col, Math.round(effectA * 0.55f * (1.0f - since / 0.16f)));
                continue;
            }

            float breakup = Mth.clamp(age / 0.20f, 0.0f, 1.0f);
            float px = ox + vx * time;
            float py = oy + vy * time - 0.5f * gravity * time * time;
            float pz = oz + vz * time;
            float vyNow = vy - gravity * time;
            float speed = Mth.sqrt(vx * vx + vyNow * vyNow + vz * vz);
            float size = (0.040f + 0.022f * h + 0.030f * (1.0f - breakup)) * S;
            float heat = (1.0f - breakup) * (0.55f + 0.35f * h);
            int col = mixRgb(v.pRgb(), v.aRgb(), 0.15f + 0.65f * heat);
            if (breakup > 0.65f) col = mixRgb(col, v.sRgb(), (breakup - 0.65f) * 0.80f);
            float pulse = 0.94f + 0.06f * Mth.sin(frameTimeSeconds * 3.0f + px * 2.1f + py * 3.7f + pz * 2.9f);
            col = shadeRgb(col, pulse);
            int alpha = Math.round(effectA * (0.50f + 0.42f * h) * (1.0f - 0.25f * breakup));
            float yaw = (float) Math.atan2(vx, vz);
            float pitch = (float) Math.acos(Mth.clamp(vyNow / Math.max(0.001f, speed), -1.0f, 1.0f));
            v.rot(px, py, pz, size, size * (1.05f + 0.25f * Math.abs(vyNow) / Math.max(0.01f, vy)),
                    size * 0.95f, yaw, pitch, 0.0f, col, alpha);

            // Satellite lié au blob au départ, puis séparation et refroidissement indépendants.
            float invSpeed = 1.0f / Math.max(0.001f, speed);
            float separation = size * (0.55f + 1.45f * breakup);
            float satSize = size * (0.52f + 0.18f * h);
            int satCol = mixRgb(col, v.sRgb(), 0.20f + 0.25f * breakup);
            v.cube(px - vx * invSpeed * separation, py - vyNow * invSpeed * separation,
                    pz - vz * invSpeed * separation, satSize, satCol,
                    Math.round(alpha * (0.42f + 0.25f * (1.0f - breakup))));
            if (h > 0.58f && breakup > 0.30f) {
                v.cube(px + lateralX * size * breakup * 1.4f, py + size * 0.35f,
                        pz + lateralZ * size * breakup * 1.4f, size * 0.38f,
                        mixRgb(v.pRgb(), v.aRgb(), 0.30f), Math.round(alpha * 0.32f));
            }
        }
    }

    /**
     * 水 EAU — une <b>vague déferlante</b> : une paroi d'eau qui monte, se recourbe vers la cible et
     * l'écrase, avec écume au sommet, embruns qui retombent et rides concentriques au sol.
     */
    private static void impactWater(ImpactView v) {
        if (v.inst().effect == WeaponVisualEffectId.PERMAFROST_RIME) {
            impactIce(v);
            return;
        }
        float t = v.t();
        float S = v.S();
        float surge = v.easeOut();
        boolean maelstrom = v.inst().effect == WeaponVisualEffectId.ABYSSAL_MAELSTROM;

        // T366-VOXEL-SCULPT : LA VAGUE est désormais une SCULPTURE PLEINE (masse d'eau + arche
        // creusée + lèvre qui retombe), plus une nappe de cubes en fil de fer.
        v.sculpt(maelstrom ? SCULPT_TWISTER : SCULPT_WAVE,
                (1.30f + 0.30f * surge) * (maelstrom ? 0.80f : 1.0f), 0.96f);

        // 1) LA VAGUE : elle démarre en arrière de la cible et la TRAVERSE (avance + se recourbe).
        float advance = (1.35f - 2.10f * surge) * S;
        float waveH = (0.85f + 0.95f * surge) * (maelstrom ? 0.55f : 1.0f) * S;
        v.wave(v.cx() - v.dirX() * advance, v.cy() - 0.44f, v.cz() - v.dirZ() * advance,
                (0.85f + 0.90f * surge) * S, waveH, 0.90f, v.sRgb(), WATER_FOAM_RGB, v.fadeA());

        // ÉCUME : petites crêtes en cubes presque opaques, séparées du volume d'eau translucide.
        // Pas de reflet global : l'écume est blanche, discontinue, et ne vit que sur la lèvre.
        if (!maelstrom && t < 0.78f) {
            float oldVeil = cubeVeil, oldTex = cubeTex, oldGloss = cubeGloss;
            cubeVeil = 0.94f;
            cubeTex = 0.015f;
            cubeGloss = 0.0f;
            try {
                int count = 9;
                float fade = Mth.clamp(1.0f - t * 1.20f, 0.0f, 1.0f);
                for (int i = 0; i < count; i++) {
                    float u = (i / (float) (count - 1)) * 2.0f - 1.0f;
                    float jitter = v.hash(0xF04A + i) - 0.5f;
                    // Deux ou trois cubes seulement : jamais une couverture blanche permanente.
                    if ((i & 3) != 0 || v.hash(0xA11 + i) < 0.25f) continue;
                    float fx = v.cx() + u * 0.82f * S - v.dirX() * advance;
                    float fz = v.cz() + u * 0.82f * S - v.dirZ() * advance;
                    float fy = v.cy() - 0.44f + waveH * (0.58f + 0.18f * (1.0f - Math.abs(u)))
                            + jitter * 0.10f * S;
                    float hs = (0.040f + 0.020f * v.hash(i * 19)) * S;
                    int foamRgb = WATER_FOAM_RGB;
                    emitVoxelCube(v.vc(), v.mat(), v.tmp(), fx, fy, fz,
                            hs, hs * (0.72f + 0.28f * v.hash(i * 7)), hs,
                            foamRgb, Math.round(v.fadeA() * fade * 0.90f));
                }
            } finally {
                cubeVeil = oldVeil;
                cubeTex = oldTex;
                cubeGloss = oldGloss;
            }
        }

        // 2) La vague s'écrase : une seconde crête, plus basse, déjà passée de l'autre côté.
        if (t > 0.32f) {
            float u = (t - 0.32f) / 0.68f;
            v.wave(v.cx() + v.dirX() * u * 1.15f * S, v.cy() - 0.44f, v.cz() + v.dirZ() * u * 1.15f * S,
                    (1.15f * (1.0f - 0.35f * u)) * S, (0.75f * (1.0f - 0.55f * u)) * S,
                    0.55f, v.sRgb(), WATER_FOAM_RGB, Math.round(v.fadeA() * (1.0f - 0.75f * u)));
        }

        // 3) Rides concentriques : deux ondes plates qui s'élargissent (l'eau est une SURFACE).
        for (int w = 0; w < 2; w++) {
            float u = Mth.clamp(t * 1.15f - w * 0.22f, 0.0f, 1.0f);
            v.ring(-0.44f, (0.30f + 1.85f * u) * S, w == 0 ? v.pRgb() : v.aRgb(), v.sRgb(),
                    Math.round(v.fadeA() * (0.85f - 0.35f * w)), PIXEL_RING_SOLID);
        }

        // 4) Embruns / gouttes : tailles variées, opacité qui tombe avec la distance.
        for (int d = 0; d < 10; d++) {
            float ang = d * 0.628f + v.hash(d * 11) * 0.35f;
            float sp = (0.60f + 1.30f * v.hash(d * 23)) * S * (0.70f + 0.50f * surge);
            float vy = 0.95f + 1.10f * v.hash(d * 37);
            v.lob(Mth.cos(ang) * sp, vy, Mth.sin(ang) * sp,
                    0.020f + 0.016f * v.hash(d * 13),
                    (d & 1) == 0 ? v.pRgb() : v.aRgb(),
                    Math.round(v.fadeA() * (0.90f - 0.35f * t)));
        }

        if (maelstrom) {
            // MAELSTRÖM : entonnoir de cubes inclinés qui tourbillonnent et s'enfoncent.
            float spin = 0.35f + 0.10f * v.m().spin();
            for (int k = 0; k < 20; k++) {
                float u = k / 20.0f;
                float ang = u * 9.0f + v.age() * spin;
                float rad = (1.55f - 1.25f * u) * S;
                v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() + 0.85f - u * 1.35f * S,
                        v.cz() + Mth.sin(ang) * rad,
                        0.042f * (1.0f - 0.40f * u) * S, 0.030f * S, 0.042f * (1.0f - 0.40f * u) * S,
                        ang, 0.75f * u, 0.55f * u,
                        (k & 1) == 0 ? v.pRgb() : v.aRgb(),
                        Math.round(v.fadeA() * (0.45f + 0.55f * u)));
            }
        }
    }

    /**
     * 氷 GIVRE (branche EAU) — des <b>éclats facettés inclinés</b> qui POUSSENT depuis l'impact en
     * s'écartant, gèlent le sol, puis se brisent en éclats à la fin de leur vie.
     */
    private static void impactIce(ImpactView v) {
        float t = v.t();
        float S = v.S();
        float grow = v.easeOut();

        // T366-VOXEL-SCULPT : faisceau de cristaux PLEINS (facettes réelles) au centre de l'impact.
        v.sculpt(SCULPT_SHARD, 0.90f + 0.45f * grow, 0.94f);

        for (int arm = 0; arm < 7; arm++) {
            float ang = arm * 0.897f + v.hash(arm) * 0.20f;
            float len = (0.45f + 1.60f * grow) * S;
            float rise = 0.25f + 0.55f * v.hash(arm * 17);
            v.shard(v.cx(), v.cy() - 0.34f, v.cz(), len, 0.042f * S,
                    Mth.cos(ang), rise, Mth.sin(ang), v.pRgb(), v.aRgb(), v.fadeA(), v.salt() + arm);
            // barbules latérales : petits éclats greffés sur la hampe
            if ((arm & 1) == 0) {
                float bang = ang + 1.05f;
                v.shard(v.cx() + Mth.cos(ang) * len * 0.55f, v.cy() - 0.20f,
                        v.cz() + Mth.sin(ang) * len * 0.55f, len * 0.45f, 0.028f * S,
                        Mth.cos(bang), 0.55f, Mth.sin(bang), v.aRgb(), v.aRgb(),
                        Math.round(v.fadeA() * 0.80f), v.salt() + arm * 7);
            }
        }

        // Croûte de glace : anneau plat qui gèle le sol autour de la cible.
        v.ring(-0.45f, (0.30f + 1.30f * grow) * S, v.pRgb(), v.aRgb(),
                Math.round(v.fadeA() * 0.80f), PIXEL_RING_SOLID);

        // Éclatement : le cristal se brise et les fragments s'écartent en tournoyant.
        if (t > 0.55f) {
            float b = (t - 0.55f) / 0.45f;
            for (int e = 0; e < 12; e++) {
                float ang = e * 0.524f + v.hash(e * 7) * 0.5f;
                float rad = (0.45f + 1.35f * b) * S;
                v.rot(v.cx() + Mth.cos(ang) * rad,
                        v.cy() - 0.10f + (v.hash(e * 13) - 0.40f) * 1.20f * b,
                        v.cz() + Mth.sin(ang) * rad,
                        (0.034f - 0.020f * b) * S, (0.026f - 0.014f * b) * S, (0.030f - 0.016f * b) * S,
                        ang, b * 2.2f + e, b * 1.7f, (e & 1) == 0 ? v.aRgb() : v.aRgb(),
                        Math.round(v.fadeA() * (1.0f - b)));
            }
        }
    }

    /**
     * 雷 FOUDRE — instantané : des branches tombent du ciel et rampent au sol, l'intensité CLIGNOTE
     * au lieu de croître, et des éclats crénelés sont projetés en ligne droite.
     */
    private static void impactThunder(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        int flick = Math.round(v.fadeA() * (0.42f + 0.58f * Math.abs(Mth.sin(v.age() * 1.85f))));
        int forks = Mth.clamp(2 + v.m().arms(), 3, 5);

        // T366-VOXEL-SCULPT : cristal de foudre PLEIN, hérissé de pointes (le cœur de l'arc).
        v.sculpt(SCULPT_BURST, 0.92f + 0.10f * t, 0.86f);

        for (int f = 0; f < forks; f++) {
            float ang = f * (float) (Math.PI * 2.0 / forks) + v.hash(f * 3) * 0.70f;
            float spread = (0.35f + 0.75f * v.hash(f * 11)) * S;
            v.bolt(v.cx() + Mth.cos(ang) * spread, v.cy() + 3.40f * S, v.cz() + Mth.sin(ang) * spread,
                    v.cx(), v.cy(), v.cz(), 9, 0.28f, v.aRgb(), v.pRgb(), flick, 0.036f * S);
            float reach = (1.30f + 0.60f * v.hash(f * 5)) * S;
            v.line(v.cx(), v.cy() - 0.42f, v.cz(),
                    v.cx() + Mth.cos(ang) * reach, v.cy() - 0.42f, v.cz() + Mth.sin(ang) * reach,
                    0.030f * S, v.sRgb(), v.pRgb(), Math.round(flick * 0.80f));
        }

        // Onde de choc : deux anneaux plats qui partent très vite (le coup de tonnerre).
        for (int w = 0; w < 2; w++) {
            float u = Mth.clamp(t * 1.60f - w * 0.18f, 0.0f, 1.0f);
            v.ring(-0.42f, (0.35f + 2.10f * u) * S, v.aRgb(), v.pRgb(),
                    Math.round(flick * (0.70f - 0.30f * w)), w == 0 ? PIXEL_RING_SOLID : PIXEL_RING_DASHED);
        }

        // Éclats crénelés projetés en ligne droite (ils ne retombent pas : pas de gravité).
        for (int e = 0; e < 9; e++) {
            float ang = e * 0.698f + v.hash(e * 9) * 0.40f;
            float sp = (1.20f + 1.60f * v.hash(e * 15)) * S * v.easeOut();
            float yy = v.cy() + (v.hash(e * 21) - 0.50f) * 1.30f;
            v.shard(v.cx(), v.cy() + (v.hash(e * 21) - 0.50f) * 1.30f, v.cz(),
                    0.55f * S, 0.024f * S,
                    Mth.cos(ang) * (0.6f + sp * 0.4f), yy * 0.05f, Mth.sin(ang) * (0.6f + sp * 0.4f),
                    v.aRgb(), v.aRgb(), flick, v.salt() + e * 11);
        }

        if (fx == WeaponVisualEffectId.TWIN_ECHO_SLASH) {
            v.slash((1.10f + 0.35f * t) * v.m().scale(), 0.036f, 155.0f,
                    v.pRgb(), v.sRgb(), v.aRgb(), v.fadeA());
            // L'écho devient un anneau discret, pas une deuxième ligne de slash.
            v.ring(v.cy(), (0.85f + 0.30f * t) * S, v.aRgb(), v.pRgb(),
                    Math.round(v.fadeA() * 0.45f), PIXEL_RING_DASHED);
        } else if (fx == WeaponVisualEffectId.CHAIN_VOLT_ARC) {
            for (int c = 0; c < 3; c++) {
                float ang = c * 2.094f + v.age() * 0.10f;
                float rad = (0.85f + 0.55f * v.hash(c * 23)) * S;
                v.bolt(v.cx(), v.cy() + 0.35f, v.cz(),
                        v.cx() + Mth.cos(ang) * rad, v.cy() + 0.10f, v.cz() + Mth.sin(ang) * rad,
                        6, 0.22f, v.aRgb(), v.aRgb(), Math.round(flick * 0.9f), 0.030f * S);
            }
        } else if (fx == WeaponVisualEffectId.OVERCLOCK_CYBER_X) {
            float span = (0.75f + 0.55f * t) * S;
            v.line(v.cx() - span, v.cy() - span, v.cz(), v.cx() + span, v.cy() + span, v.cz(),
                    0.042f * S, v.aRgb(), v.pRgb(), flick);
            v.line(v.cx() - span, v.cy() + span, v.cz(), v.cx() + span, v.cy() - span, v.cz(),
                    0.042f * S, v.aRgb(), v.sRgb(), flick);
            // T365-HOLOGRAM : grille cubique de surcadence (volume néon).
            v.cage(span * 0.95f, v.age() * 0.07f, 0.50f, v.age() * 0.05f,
                    v.pRgb(), v.aRgb(), Math.round(flick * 0.75f));
        }
    }

    /**
     * 土 TERRE — des <b>dalles de pierre inclinées</b> se soulèvent puis retombent lourdement, le
     * sol se fend en étoile, et des blocs de roche sont arrachés (éclats facettés).
     */
    private static void impactEarth(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        float lift = Mth.sin(Mth.clamp(t * 1.15f, 0.0f, 1.0f) * (float) Math.PI);
        int plates = Mth.clamp(4 + v.m().arms(), 5, 9);

        // T366-VOXEL-SCULPT : empilement de DALLES de roche PLEINES, inclinées et bruitées.
        v.sculpt(SCULPT_DUNE, 1.05f + 0.30f * lift, 0.94f);

        for (int p = 0; p < plates; p++) {
            float ang = p * (float) (Math.PI * 2.0 / plates) + v.hash(p * 17) * 0.30f;
            float rad = (0.55f + 0.85f * v.hash(p * 23)) * S;
            float w = (0.11f + 0.08f * v.hash(p * 31)) * S;
            float h = (0.045f + 0.035f * v.hash(p * 37)) * S;
            float hy = v.cy() - 0.40f + lift * (0.55f + 0.80f * v.hash(p * 43)) * S;
            // dalle INCLINÉE : elle bascule en s'arrachant du sol
            v.rot(v.cx() + Mth.cos(ang) * rad, hy, v.cz() + Mth.sin(ang) * rad,
                    w, h, w * 0.80f,
                    ang, (v.hash(p * 53) - 0.5f) * 1.10f * lift - 0.35f * (1.0f - lift),
                    (v.hash(p * 61) - 0.5f) * 0.90f * lift,
                    v.grad(0.20f + 0.55f * lift, p * 11), v.fadeA());
            // caillou plus petit qui tourne autour de la dalle
            v.rot(v.cx() + Mth.cos(ang + 0.5f) * rad * 1.10f, hy + 0.10f,
                    v.cz() + Mth.sin(ang + 0.5f) * rad * 1.10f,
                    0.030f * S, 0.024f * S, 0.034f * S, ang * 2.0f, ang, 0.6f,
                    v.aRgb(), Math.round(v.fadeA() * 0.85f));
        }

        // Failles radiales : le sol se fend depuis l'impact.
        for (int c = 0; c < 8; c++) {
            float ang = c * 0.785f + v.hash(c * 11) * 0.35f;
            float len = (0.45f + 1.55f * v.easeOut()) * S;
            v.line(v.cx(), v.cy() - 0.46f, v.cz(),
                    v.cx() + Mth.cos(ang) * len, v.cy() - 0.46f, v.cz() + Mth.sin(ang) * len,
                    0.028f * S, v.sRgb(), v.pRgb(), Math.round(v.fadeA() * 0.85f));
        }

        if (fx == WeaponVisualEffectId.SANDSTORM_DUNE) {
            // TEMPÊTE DE SABLE : grains inclinés qui tourbillonnent en enveloppant la cible.
            for (int g = 0; g < 18; g++) {
                float u = g / 18.0f;
                float ang = u * 7.5f + v.age() * 0.55f;
                float rad = (0.55f + 1.20f * u) * S;
                v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() - 0.40f + u * 1.55f * S,
                        v.cz() + Mth.sin(ang) * rad,
                        0.026f * S + 0.014f * v.hash(g * 7), 0.014f * S,
                        0.026f * S + 0.010f * v.hash(g * 13),
                        ang, u * 1.6f, ang * 0.8f,
                        (g & 1) == 0 ? v.pRgb() : v.aRgb(),
                        Math.round(v.fadeA() * (0.55f + 0.45f * v.hash(g * 3))));
            }
        } else if (fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) {
            emitPixelRunicCircle3D(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy() - 0.44f, v.cz(),
                    (0.60f + 1.15f * v.easeOut()) * S, t * 0.80f, v.pRgb(), v.sRgb(), v.aRgb(),
                    v.fadeA(), true);
            for (int b = 0; b < 4; b++) {
                float ang = b * 1.5708f;
                float rad = 1.05f * S;
                v.line(v.cx() + Mth.cos(ang) * rad, v.cy() - 0.40f, v.cz() + Mth.sin(ang) * rad,
                        v.cx() + Mth.cos(ang) * rad, v.cy() + 1.05f * S, v.cz() + Mth.sin(ang) * rad,
                        0.034f * S, v.aRgb(), v.pRgb(), v.fadeA());
            }
        }
    }

    /**
     * 金 MÉTAL — des <b>lames inclinées</b> convergent vers la cible puis claquent, avec des
     * étincelles courtes et une entaille franche. Aucune gerbe molle, aucune fumée.
     */
    private static void impactMetal(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        boolean prism = fx == WeaponVisualEffectId.SUPREME_PRISM_EDGE;
        int blades = prism ? 4 : 7;
        float snap = (t < 0.55f) ? (1.0f - v.easeIn() * 0.85f) : (0.15f + (t - 0.55f) * 0.35f);

        // T366-VOXEL-SCULPT : cœur de métal PLEIN (hérisse de pointes, ou vrille prismatique).
        v.sculpt(prism ? SCULPT_TWISTER : SCULPT_BURST, 0.88f + 0.28f * snap, 0.92f);

        for (int b = 0; b < blades; b++) {
            float ang = b * (float) (Math.PI * 2.0 / blades) + v.m().spin() * 0.25f;
            float rad = (0.55f + 0.80f * snap) * S;
            int col = v.grad(0.30f + 0.45f * v.hash(b * 13), b * 19);
            // lame : longue, fine, INCLINÉE vers le centre (elle pointe la cible)
            v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() + 0.05f, v.cz() + Mth.sin(ang) * rad,
                    (prism ? 0.050f : 0.022f) * S, (0.34f + 0.20f * v.hash(b * 13)) * S, 0.012f * S,
                    ang + 1.5708f, 0.85f, (v.hash(b * 7) - 0.5f) * 0.5f,
                    col, v.fadeA());
            // contre-lame plus petite, croisée
            v.rot(v.cx() + Mth.cos(ang) * rad * 0.80f, v.cy() + 0.12f, v.cz() + Mth.sin(ang) * rad * 0.80f,
                    0.012f * S, (0.16f + 0.10f * v.hash(b * 19)) * S, 0.010f * S,
                    ang, -0.7f, 0.30f, v.aRgb(), Math.round(v.fadeA() * 0.85f));
        }

        // L'ENTAILLE elle-même : l'arc de lame de l'enchantement claque au point d'impact.
        v.slash((0.95f + 0.45f * t) * v.m().scale(), 0.030f, 150.0f,
                v.pRgb(), v.sRgb(), v.aRgb(), v.fadeA());

        // Étincelles de contact : éclats fins, très rapides, sans gravité.
        for (int e = 0; e < 8; e++) {
            float ang = e * 0.785f + v.hash(e * 29) * 0.80f;
            float sp = (0.35f + 0.85f * v.hash(e * 7)) * S;
            v.shard(v.cx(), v.cy() + 0.05f, v.cz(), sp, 0.014f * S,
                    Mth.cos(ang), (v.hash(e * 3) - 0.5f) * 0.8f, Mth.sin(ang),
                    v.aRgb(), v.aRgb(), Math.round(v.fadeA() * 0.95f), v.salt() + e * 5);
        }

        if (prism) {
            // T365-HOLOGRAM : cage prismatique en volume autour de l'impact (au lieu de la croix
            // seule, qui restait plate) + éclat central.
            v.cage((0.95f + 0.35f * t) * S, v.age() * 0.055f, 0.62f + t * 0.4f, v.age() * 0.03f,
                    v.pRgb(), v.aRgb(), Math.round(v.fadeA() * 0.85f));
            v.cross(4, v.aRgb(), v.pRgb(), v.fadeA());
        } else if (fx == WeaponVisualEffectId.FOCUSED_BULLSEYE) {
            float rad = (t < 0.50f) ? (1.05f - 1.45f * t) : (0.38f + (t - 0.50f) * 1.60f);
            // T365-HOLOGRAM : disque ÉPAIS (3 anneaux inclinés + cœur), plus un plan face caméra.
            v.holoDisc(rad * S, v.sRgb(), v.pRgb(), v.fadeA());
        } else if (fx == WeaponVisualEffectId.ANNIHILATING_ARC) {
            v.slash((1.35f + 0.70f * t) * v.m().scale(), 0.046f, 210.0f,
                    v.aRgb(), v.aRgb(), v.pRgb(), v.fadeA());
        }
    }

    /** 木 BOIS — GAIA : un arbre-monde complet, puis l'accent propre au titre de l'arme. */
    private static void impactWood(ImpactView v) {
        WeaponVisualEffectId fx = v.inst().effect;
        int variant = fx == WeaponVisualEffectId.ZEPHYR_WIND_LANCE ? GaiaWorldTreeVfx.ZEPHYR
                : fx == WeaponVisualEffectId.THORN_MAW_ROOT ? GaiaWorldTreeVfx.THORN
                : GaiaWorldTreeVfx.VERDANT;
        // Les impacts transportent cible -> attaquant ; les accents dirigés utilisent l'inverse.
        float dirX = -v.dirX(), dirZ = -v.dirZ();
        boolean previousPulse = voxelPulseEnabled;
        voxelPulseEnabled = false;
        try {
            GaiaWorldTreeVfx.render(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), v.t(),
                    dirX, dirZ, variant, v.fadeA(), v.salt());
        } finally {
            voxelPulseEnabled = previousPulse;
        }
    }

    /** POISON : trois signatures longues, dirigées par leur titre plutôt que par un socle commun. */
    private static void impactPoison(ImpactView v) {
        WeaponVisualEffectId fx = v.inst().effect;
        int variant = fx == WeaponVisualEffectId.VENOM_BRIAR_FANG ? PoisonSignatureVfx.VENOM
                : fx == WeaponVisualEffectId.MIASMA_SHROUD ? PoisonSignatureVfx.MIASMA
                : PoisonSignatureVfx.PLAGUE;
        PoisonSignatureVfx.render(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), v.t(),
                v.dirX(), v.dirZ(), variant, v.fadeA(), v.salt());
    }

    /**
     * 光 LUMIÈRE — la lumière DESCEND : des rayons en plaques inclinées tombent du ciel et
     * convergent, la couronne s'ouvre, un faisceau relie le ciel à la cible.
     */
    private static void impactLight(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;

        // T366-VOXEL-SCULPT : colonne de lumière PLEINE (masse de cristal, pas un faisceau de fils).
        v.sculpt(SCULPT_COLUMN, 0.78f + 0.42f * t, 0.88f);

        int rays = 9;
        for (int r = 0; r < rays; r++) {
            float ang = r * 0.698f + v.age() * 0.04f;
            float rad = (1.35f + 0.55f * v.hash(r * 7)) * S;
            float up = (2.55f - 0.85f * t) * S;
            float appear = Mth.clamp(t * 1.60f - r * 0.04f, 0.0f, 1.0f);
            float x0 = v.cx() + Mth.cos(ang) * rad;
            float z0 = v.cz() + Mth.sin(ang) * rad;
            float x1 = v.cx() + Mth.cos(ang) * 0.22f * S;
            float z1 = v.cz() + Mth.sin(ang) * 0.22f * S;
            // rayon = plaque fine inclinée le long de son axe (aspect « hologramme »)
            int segs = 4;
            for (int k = 0; k < segs; k++) {
                float u = (k + 0.5f) / segs;
                float px = Mth.lerp(u, x0, x1);
                float py = Mth.lerp(u, v.cy() + up, v.cy() + 0.10f);
                float pz = Mth.lerp(u, z0, z1);
                float half = (0.030f - 0.014f * u) * S;
                v.rot(px, py, pz, half * 2.2f, half * 0.30f, half,
                        ang, 0.30f, 0.35f,
                        v.grad(0.45f + 0.55f * u, k * 23),
                        Math.round(v.fadeA() * appear * (0.85f - 0.15f * u)));
            }
        }

        v.ring(0.30f, (0.30f + 1.55f * v.easeOut()) * S, v.aRgb(), v.pRgb(),
                Math.round(v.fadeA() * 0.85f), PIXEL_RING_SOLID);

        // Faisceau : une colonne fine de pixels qui DESCEND du ciel vers la cible.
        float beam = Mth.clamp(t * 1.60f, 0.0f, 1.0f);
        for (int k = 0; k < 8; k++) {
            float u = k / 8.0f;
            float y = v.cy() + 2.35f * S * (1.0f - u * beam);
            v.cube(v.cx() + (v.hash(k * 7) - 0.50f) * 0.10f * S, y,
                    v.cz() + (v.hash(k * 11) - 0.50f) * 0.10f * S,
                    (0.040f - 0.012f * u) * S, u < 0.40f ? v.aRgb() : v.pRgb(),
                    Math.round(v.fadeA() * (0.90f - 0.40f * u)));
        }

        if (fx == WeaponVisualEffectId.SERAPH_SKY_PILLAR) {
            // T365-HOLOGRAM : globe de lumière en volume au-dessus de la cible.
            v.holoSphere((0.80f + 0.45f * v.easeOut()) * S, v.age() * 0.06f,
                    v.pRgb(), v.aRgb(), Math.round(v.fadeA() * 0.85f));
            for (int k = 0; k < 12; k++) {
                float u = k / 12.0f;
                v.cube(v.cx() + (v.hash(k * 13) - 0.50f) * 0.22f * S,
                        v.cy() - 0.30f + u * 3.10f * S,
                        v.cz() + (v.hash(k * 29) - 0.50f) * 0.22f * S,
                        (0.048f - 0.018f * u) * S, u > 0.60f ? v.aRgb() : v.pRgb(),
                        Math.round(v.fadeA() * (1.0f - 0.35f * u)));
            }
        } else if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE) {
            // LANCE ASTRALE : un trait de lumière facetté qui transperce la cible en diagonale.
            v.shard(v.cx() - 1.30f * S, v.cy() + 1.85f * S, v.cz() - 1.30f * S,
                    3.4f * S, 0.060f * S, 1.0f, -1.15f, 1.0f,
                    v.aRgb(), v.aRgb(), v.fadeA(), v.salt());
            // T365-HOLOGRAM : cage stellaire qui tourne autour de l'impact.
            v.cage((0.85f + 0.50f * v.easeOut()) * S, v.age() * 0.045f, 0.55f, v.age() * 0.02f,
                    v.aRgb(), v.aRgb(), Math.round(v.fadeA() * 0.75f));
        } else if (fx == WeaponVisualEffectId.CHRONO_ETERNAL_HALO) {
            for (int o = 0; o < 2; o++) {
                float rad = (0.55f + 0.25f * o) * S;
                float ang0 = v.age() * (0.05f + 0.04f * o);
                for (int k = 0; k < 12; k++) {
                    float ang = ang0 + k * 0.524f;
                    v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() + 0.20f + (o == 0 ? 0.18f : -0.16f),
                            v.cz() + Mth.sin(ang) * rad,
                            (k % 3 == 0 ? 0.034f : 0.021f) * S, 0.014f * S,
                            (k % 3 == 0 ? 0.034f : 0.021f) * S,
                            ang, 0.0f, o == 0 ? 0.35f : -0.35f,
                            (k % 3 == 0) ? v.aRgb() : v.pRgb(), v.fadeA());
                }
            }
        } else if (fx == WeaponVisualEffectId.GOLDEN_CRIT_STAR) {
            for (int a = 0; a < 10; a++) {
                float ang = a * 0.628f + v.age() * 0.10f;
                float len = (0.40f + 1.30f * v.easeOut()) * S;
                v.shard(v.cx(), v.cy(), v.cz(), len, 0.026f * S,
                        Mth.cos(ang), 0.16f, Mth.sin(ang),
                        v.aRgb(), v.sRgb(), v.fadeA(), v.salt() + a * 13);
            }
            v.ring(0.18f, (0.55f + 0.75f * t) * S, v.pRgb(), v.aRgb(),
                    Math.round(v.fadeA() * 0.80f), PIXEL_RING_BEADS);
        }
    }

    /**
     * 影 OMBRE — des <b>tentacules courbes</b> rampent au sol depuis l'impact puis se RÉTRACTENT,
     * avec des griffures en éclats inclinés et une éclipse qui avale la lumière.
     */
    private static void impactShadow(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        float crawl = v.easeOut();
        float retract = (t < 0.45f) ? 1.0f : (1.0f - 0.65f * ((t - 0.45f) / 0.55f));
        int arms = Mth.clamp(4 + v.m().arms(), 5, 9);

        // T366-VOXEL-SCULPT : masse d'ombre PLEINE, vrillée, qui se rétracte.
        v.sculpt(SCULPT_TWISTER, (0.90f + 0.30f * crawl) * retract, 0.82f);

        for (int i = 0; i < arms; i++) {
            float ang = i * (float) (Math.PI * 2.0 / arms) + v.hash(i * 23) * 0.45f;
            float len = (0.35f + 1.80f * crawl) * S * retract;
            float ex = v.cx() + Mth.cos(ang) * len;
            float ez = v.cz() + Mth.sin(ang) * len;
            // tentacule au sol (courbe) puis pointe qui se dresse
            v.tendril(v.cx(), v.cy() - 0.45f, v.cz(), ex, v.cy() - 0.45f + 0.22f * (1.0f - retract), ez,
                    0.28f * S, 6, 0.030f * S, v.pRgb(), v.aRgb(),
                    Math.round(v.fadeA() * (0.55f + 0.45f * retract)));
            v.rot(ex, v.cy() - 0.32f + 0.18f * (1.0f - retract), ez,
                    0.024f * S, 0.060f * S, 0.024f * S,
                    ang, 0.55f, 0.30f,
                    v.grad(0.25f + 0.45f * retract, i * 31), v.fadeA());
        }

        if (fx == WeaponVisualEffectId.WITHER_SHADOW_RIFT) {
            // GRIFFURE : trois entailles parallèles en éclats inclinés (une patte qui lacère).
            for (int c = 0; c < 3; c++) {
                float off = (c - 1) * 0.35f * S;
                v.shard(v.cx() - 0.85f * S + off, v.cy() + 0.95f * S, v.cz() - 0.55f * S,
                        1.85f * S, 0.026f * S, 0.72f, -1.0f, 0.55f,
                        v.aRgb(), v.aRgb(), Math.round(v.fadeA() * 0.90f), v.salt() + c * 17);
            }
        } else if (fx == WeaponVisualEffectId.ECLIPSE_VEIL) {
            float rad = (0.25f + 1.15f * crawl) * S;
            // T365-HOLOGRAM : l'éclipse devient un volume (disque épais + cage qui l'enserre).
            v.holoDisc(rad, DeepElement.SHADOW.secondary, DeepElement.SHADOW.primary, Math.round(v.fadeA() * 0.85f));
            v.cage(rad * 1.15f, v.age() * 0.03f, 0.95f, v.age() * 0.02f,
                    v.aRgb(), v.aRgb(), Math.round(v.fadeA() * 0.70f));
            v.ring(0.05f, rad * 1.18f, v.aRgb(), v.aRgb(),
                    Math.round(v.fadeA() * 0.90f), PIXEL_RING_BEADS);
        } else if (fx == WeaponVisualEffectId.SPECTRAL_REACH) {
            float reach = (0.90f + 1.90f * v.easeOut()) * S;
            v.tendril(v.cx(), v.cy() + 0.05f, v.cz(),
                    v.cx() + v.dirX() * reach, v.cy() + 0.05f, v.cz() + v.dirZ() * reach,
                    0.10f * S, 7, 0.030f * S, v.aRgb(), v.pRgb(), v.fadeA());
        }
    }

    /**
     * 血 SANG — veines courbes qui rampent, gouttes qui tombent en accélérant, éclats de sang
     * inclinés ; chaque enchantement de prélèvement garde sa circulation propre.
     */
    private static void impactBlood(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;
        int veins = Mth.clamp(3 + v.m().arms(), 4, 7);

        // T366-VOXEL-SCULPT : caillot PLEIN hérissé de pointes (masse visqueuse sculptée).
        v.sculpt(SCULPT_BURST, 0.80f + 0.30f * v.easeOut(), 0.90f);

        for (int i = 0; i < veins; i++) {
            float ang = i * (float) (Math.PI * 2.0 / veins) + v.hash(i * 29) * 0.50f;
            float len = (0.30f + 1.50f * v.easeOut()) * S;
            v.tendril(v.cx(), v.cy() - 0.44f, v.cz(),
                    v.cx() + Mth.cos(ang) * len, v.cy() - 0.44f, v.cz() + Mth.sin(ang) * len,
                    0.24f * S, 6, 0.024f * S, v.pRgb(), v.sRgb(), Math.round(v.fadeA() * 0.90f));
            // bifurcation plus fine
            v.tendril(v.cx() + Mth.cos(ang) * len * 0.55f, v.cy() - 0.44f, v.cz() + Mth.sin(ang) * len * 0.55f,
                    v.cx() + Mth.cos(ang + 0.55f) * len, v.cy() - 0.44f,
                    v.cz() + Mth.sin(ang + 0.55f) * len,
                    0.14f * S, 4, 0.016f * S, v.sRgb(), v.aRgb(), Math.round(v.fadeA() * 0.7f));
        }

        // Gouttes : accélération gravitaire (t²) — le sang tombe, il ne monte pas.
        for (int d = 0; d < 7; d++) {
            float ang = d * 0.897f + v.hash(d * 13) * 0.60f;
            float rad = (0.20f + 1.05f * v.easeOut()) * S * (0.60f + 0.50f * v.hash(d * 7));
            v.drop(v.cx() + Mth.cos(ang) * rad, v.cy() + 0.45f - 1.55f * t * t,
                    v.cz() + Mth.sin(ang) * rad, (0.030f - 0.008f * t) * S,
                    (d & 1) == 0 ? v.pRgb() : v.sRgb(), v.aRgb(),
                    Math.round(v.fadeA() * (0.85f - 0.25f * t)), d);
        }

        if (fx == WeaponVisualEffectId.GUILLOTINE_CLEAVE) {
            // GUILLOTINE : une lame large qui DESCEND (colonne d'exécution), légèrement inclinée.
            float y = v.cy() + 2.30f * S - (2.90f * S) * v.easeIn();
            v.rot(v.cx(), y, v.cz(), 0.44f * S, 0.032f * S, 0.030f * S, 0.0f, 0.10f, 0.05f,
                    v.aRgb(), v.fadeA());
            v.line(v.cx() - 0.44f * S, y, v.cz(), v.cx() + 0.44f * S, y, v.cz(),
                    0.022f * S, v.sRgb(), v.pRgb(), v.fadeA());
        } else if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
            for (int k = 0; k < 18; k++) {
                float u = k / 18.0f;
                float ang = u * 8.5f + v.age() * 0.30f;
                float rad = (1.25f - 1.05f * u) * S;
                v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() - 0.35f + u * 1.90f * S,
                        v.cz() + Mth.sin(ang) * rad,
                        0.036f * S, 0.026f * S, 0.036f * S,
                        ang, 0.7f, 0.4f, (k & 1) == 0 ? v.pRgb() : v.aRgb(), v.fadeA());
            }
        } else if (fx == WeaponVisualEffectId.VITAL_TRANSFUSION) {
            for (int h = 0; h < 2; h++) {
                for (int k = 0; k < 11; k++) {
                    float u = k / 11.0f;
                    float ang = u * 6.4f + v.age() * 0.22f + h * (float) Math.PI;
                    float rad = (0.55f - 0.30f * u) * S;
                    v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() - 0.30f + u * 1.90f * S,
                            v.cz() + Mth.sin(ang) * rad,
                            0.032f * S, 0.026f * S, 0.032f * S,
                            ang, 0.8f, 0.5f, h == 0 ? v.pRgb() : v.aRgb(), v.fadeA());
                }
            }
        } else if (fx == WeaponVisualEffectId.CARNAGE_BERSERK) {
            v.slash((1.15f + 0.55f * t) * v.m().scale(), 0.042f, 220.0f,
                    v.aRgb(), v.pRgb(), v.aRgb(), v.fadeA());
        }
    }

    /**
     * 虚 VID — le vide IMPLOSE : une coquille de cubes <b>inclinés</b> s'effondre vers la cible,
     * l'espace se déforme, puis tout ce qui a été avalé ressort d'un coup.
     */
    private static void impactVoid(ImpactView v) {
        float t = v.t();
        float S = v.S();
        WeaponVisualEffectId fx = v.inst().effect;

        if (fx == WeaponVisualEffectId.NULL_RIFT_FISSURE) {
            float open = (0.25f + 0.85f * v.easeOut()) * S;
            for (int k = 0; k < 10; k++) {
                float u = k / 10.0f;
                float y = v.cy() - 1.10f * S + u * 2.30f * S;
                float wob = Mth.sin(u * 6.0f + v.age() * 0.20f) * 0.10f * S;
                v.rot(v.cx() - open + wob, y, v.cz(), 0.032f * S, 0.030f * S, 0.020f * S,
                        0.0f, 0.20f, 0.35f, v.pRgb(), v.fadeA());
                v.rot(v.cx() + open + wob, y, v.cz(), 0.032f * S, 0.030f * S, 0.020f * S,
                        0.0f, -0.20f, -0.35f, v.aRgb(), v.fadeA());
                if ((k & 1) == 0) {
                    v.rot(v.cx() + (v.hash(k * 13) - 0.50f) * 0.70f * S, y,
                            v.cz() + (v.hash(k * 7) - 0.50f) * 0.50f * S,
                            0.018f * S, 0.018f * S, 0.018f * S,
                            v.age() * 0.4f, 0.6f, 0.6f,
                            v.aRgb(), Math.round(v.fadeA() * 0.80f));
                }
            }
            return;
        }

        // IMPLOSION : les cubes s'effondrent vers la cible en tournoyant (coquille qui se ferme).
        float close = 1.0f - v.easeIn();
        // T366-VOXEL-SCULPT : orbe de vide PLEIN et cratérisé qui se replie sur la cible.
        v.sculpt(SCULPT_ORB, 0.55f + 0.95f * close, 0.92f);
        float radius = (1.80f * close + 0.08f) * S;
        int pts = 26;
        for (int i = 0; i < pts; i++) {
            float u = i / (float) pts;
            float ang = u * 12.4f + v.age() * 0.16f;
            float y = v.cy() + Mth.sin(u * 9.3f + v.age() * 0.10f) * radius * 0.85f;
            float rad = radius * (0.55f + 0.45f * Math.abs(Mth.cos(u * 7.1f)));
            float half = (0.034f + 0.014f * v.hash(i * 11)) * S;
            v.rot(v.cx() + Mth.cos(ang) * rad, y, v.cz() + Mth.sin(ang) * rad,
                    half, half * 1.35f, half * 0.75f,
                    ang, v.age() * 0.25f + u * 3.0f, u * 4.0f,
                    v.grad(0.15f + 0.65f * close + 0.20f * v.hash(i * 7), i * 29),
                    Math.round(v.fadeA() * (0.45f + 0.55f * close)));
        }

        // T365-HOLOGRAM : cage qui s'EFFONDRE avec la sphère (elle se replie sur la cible).
        v.cage((1.55f * close + 0.10f) * S, v.age() * 0.12f, 0.75f, v.age() * 0.07f,
                v.aRgb(), v.aRgb(), Math.round(v.fadeA() * 0.80f));

        float pulse = 0.75f + 0.25f * Mth.sin(v.age() * 0.35f);
        v.ring(0.02f, (0.95f + 0.25f * pulse) * S, v.aRgb(), v.pRgb(),
                Math.round(v.fadeA() * 0.65f), PIXEL_RING_DASHED);

        if (fx == WeaponVisualEffectId.DRAGON_CHAOS_PRISM) {
            for (int a = 0; a < 4; a++) {
                float rad = (1.05f + 0.35f * a) * S * (0.55f + 0.45f * close);
                float ang0 = v.age() * (0.08f + 0.02f * a) + a * 0.8f;
                for (int k = 0; k < 9; k++) {
                    float u = k / 9.0f;
                    float ang = ang0 + u * 2.6f;
                    v.rot(v.cx() + Mth.cos(ang) * rad, v.cy() + (-0.45f + 0.90f * u) * S,
                            v.cz() + Mth.sin(ang) * rad,
                            0.032f * S, 0.022f * S, 0.032f * S,
                            ang, 0.9f, 0.6f,
                            ((a + k) & 1) == 0 ? v.aRgb() : v.pRgb(), v.fadeA());
                }
            }
        }

        if (t > 0.82f) {
            float b = (t - 0.82f) / 0.18f;
            v.cross(4, v.aRgb(), v.aRgb(), Math.round(v.fadeA() * b));
            v.ring(0.02f, (1.05f + 0.85f * b) * S, v.aRgb(), v.pRgb(),
                    Math.round(v.fadeA() * b), PIXEL_RING_SOLID);
        }
    }

    /**
     * 混沌 CHAOS — corps sombre stable, cerné par de vrais blocs voxel lumineux.
     * Kaléidoscope = deux cadres rotatifs, entropie = trois plans orbitaux, big bang = expansion.
     * Aucun satellite multicolore isolé : chaque pixel coloré appartient à une arête synchronisée.
     */
    private static void impactChaos(ImpactView v) {
        float t = v.t();
        float S = v.S();
        float time = v.age() * 0.05f;
        WeaponVisualEffectId fx = v.inst().effect;
        int shape = fx == WeaponVisualEffectId.BIG_BANG_SCAR ? SCULPT_BURST : SCULPT_ORB;

        // La palette d'une arme multi-éléments ne doit jamais colorer la masse centrale de Chaos.
        emitVoxelSculpt(v.vc(), v.mat(), v.tmp(), shape, v.cx(), v.cy(), v.cz(),
                (0.92f + 0.32f * t) * S, v.yawRad() + v.age() * 0.045f, t,
                shadeRgb(DeepElement.CHAOS.secondary, 0.52f), DeepElement.CHAOS.primary,
                shadeRgb(DeepElement.CHAOS.highlight, 0.82f), DeepElement.CHAOS.highlight,
                v.salt(), Math.round(v.fadeA() * 0.88f),
                Math.min(1600, Math.max(280, HARD_CUBE_CAP_PER_FRAME - cubesSinceFlush)));

        if (fx == WeaponVisualEffectId.BIG_BANG_SCAR) {
            for (int i = 0; i < 14; i++) {
                float ang = i * 0.449f + t * 0.60f;
                float len = (0.55f + 3.00f * t) * S * ((i & 1) == 0 ? 1.0f : 0.70f);
                float elev = (i % 3 == 0) ? 0.55f : ((i % 3 == 1) ? -0.35f : 0.05f);
                v.shard(v.cx(), v.cy(), v.cz(), len, 0.030f * S,
                        Mth.cos(ang), elev * 0.55f, Mth.sin(ang),
                        DeepElement.CHAOS.primary, DeepElement.chaosEdge(time, i / 14.0f),
                        Math.round(v.fadeA() * 0.82f), v.salt() + i * 19);
            }
            v.cross(5, DeepElement.CHAOS.primary, DeepElement.CHAOS.secondary, v.fadeA());
            float half = (1.05f + 1.85f * t) * S;
            emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), half,
                    time * 0.32f, time * 0.18f, time * 0.24f, time,
                    Math.round(v.fadeA() * 0.92f));
            emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), half * 0.72f,
                    -time * 0.41f, 0.785f, -time * 0.28f, time,
                    Math.round(v.fadeA() * 0.82f));
            return;
        }

        if (fx == WeaponVisualEffectId.ENTROPY_SPHERE) {
            float rad = (1.02f + 0.55f * Mth.sin(t * (float) Math.PI)) * S;
            emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), rad,
                    time * 0.42f, 0.0f, time * 0.22f, time, Math.round(v.fadeA() * 0.90f));
            emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), rad,
                    time * 0.42f + 1.5708f, 1.5708f, -time * 0.18f, time,
                    Math.round(v.fadeA() * 0.82f));
            emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), rad,
                    -time * 0.36f, 1.5708f, 1.5708f + time * 0.20f, time,
                    Math.round(v.fadeA() * 0.82f));
            return;
        }

        float half = (0.95f + 0.75f * v.easeOut()) * S;
        emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), half,
                time * 0.45f, 0.48f + time * 0.25f, time * 0.15f, time,
                Math.round(v.fadeA() * 0.94f));
        emitChaosVoxelFrame(v.vc(), v.mat(), v.tmp(), v.cx(), v.cy(), v.cz(), half * 0.72f,
                -time * 0.38f, -0.62f, 0.785f + time * 0.32f, time,
                Math.round(v.fadeA() * 0.84f));
    }

    /**
     * Douze arêtes épaisses en mini-cubes, pas un nuage de particules. Une phase continue parcourt
     * chaque arête et tous les cadres partagent la même horloge : les teintes radiant ainsi de
     * manière synchronisée tout en restant simultanément multicolores.
     */
    private static void emitChaosVoxelFrame(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            float cx, float cy, float cz, float half,
                                            float yaw, float pitch, float roll,
                                            float time, int alpha) {
        if (alpha <= 6 || half <= 0.08f) return;
        float cr = Mth.cos(roll), sr = Mth.sin(roll);
        float cp = Mth.cos(pitch), sp = Mth.sin(pitch);
        float cyw = Mth.cos(yaw), syw = Mth.sin(yaw);
        float[] p = ROT_CORNERS;
        for (int i = 0; i < 8; i++) {
            float ox = ((i & 4) == 0) ? -half : half;
            float oy = ((i & 2) == 0) ? -half : half;
            float oz = ((i & 1) == 0) ? -half : half;
            rotWrite(ox, oy, oz, cr, sr, cp, sp, cyw, syw, p, i * 3);
            p[i * 3] += cx;
            p[i * 3 + 1] += cy;
            p[i * 3 + 2] += cz;
        }

        int steps = Mth.clamp(Math.round(half * 6.0f), 6, 12);
        float blockHalf = Mth.clamp(half * 0.044f, 0.026f, 0.070f);
        for (int axis = 0; axis < 3; axis++) {
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    int i0;
                    int i1;
                    if (axis == 0) {
                        i0 = (a << 1) | b;
                        i1 = (1 << 2) | (a << 1) | b;
                    } else if (axis == 1) {
                        i0 = (a << 2) | b;
                        i1 = (a << 2) | (1 << 1) | b;
                    } else {
                        i0 = (a << 2) | (b << 1);
                        i1 = (a << 2) | (b << 1) | 1;
                    }
                    int edge = axis * 4 + a * 2 + b;
                    for (int s = 1; s < steps; s++) {
                        float u = s / (float) steps;
                        float px = Mth.lerp(u, p[i0 * 3], p[i1 * 3]);
                        float py = Mth.lerp(u, p[i0 * 3 + 1], p[i1 * 3 + 1]);
                        float pz = Mth.lerp(u, p[i0 * 3 + 2], p[i1 * 3 + 2]);
                        float phase = edge / 12.0f + u * 0.28f;
                        float wave = 0.90f + 0.10f * Mth.sin(time * 2.4f + phase * 6.2831855f);
                        int rgb = DeepElement.chaosEdge(time, phase);
                        int edgeAlpha = depthAlpha(Math.round(alpha * wave), nearness(cx, cy, cz, px, py, pz));
                        emitVoxelCube(vc, mat, tmp, px, py, pz,
                                blockHalf, blockHalf, blockHalf, rgb, edgeAlpha);
                    }
                }
            }
        }
        // Les huit coins sont des blocs renforcés : la silhouette du cadre reste lisible de loin.
        for (int i = 0; i < 8; i++) {
            float px = p[i * 3], py = p[i * 3 + 1], pz = p[i * 3 + 2];
            int rgb = DeepElement.chaosEdge(time, i / 8.0f + 0.08f);
            int cornerAlpha = depthAlpha(alpha, nearness(cx, cy, cz, px, py, pz));
            emitVoxelCube(vc, mat, tmp, px, py, pz,
                    blockHalf * 1.45f, blockHalf * 1.45f, blockHalf * 1.45f, rgb, cornerAlpha);
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

        // T349-PIXEL-VARIATION : la taille respire doucement dans le temps…
        float scale = (voxelPulseEnabled ? framePixelScale * smoothPulse(cx, cy, cz) : 1.0f) * variantScale;
        // …T367 : …et chaque cube a en plus sa PROPRE taille (aucun n'est identique à son voisin).
        int sizeSeed = (int) (cx * 911.0f + cy * 577.0f + cz * 331.0f);
        scale *= 0.82f + 0.34f * pixelHash(0x51C3, sizeSeed);
        hx *= scale;
        hy *= scale;
        hz *= scale;

        // T350-VFX-VARIATIONS : allègement contrôlé (les contours lumineux restent toujours dessinés)
        if (pixelDensity < 0.999f && alpha < 200) {
            float keep = pixelHash(0x5EED, (int) (cx * 977.0f + cy * 613.0f + cz * 389.0f));
            if (keep > pixelDensity) return;
        }

        // T367-VOXEL-MATERIAL : transparence de l'élément (l'eau laisse voir au travers).
        if (cubeVeil < 0.999f) {
            alpha = Math.round(alpha * cubeVeil);
            if (alpha <= 5) return;
        }

        // T367-VOXEL-TEXTURE : les 8 coins du cube ont chacun leur nuance -> la face n'est plus un
        // aplat, elle a un dégradé interne et des arêtes légèrement assombries (occlusion).
        int texSeed = sizeSeed ^ (alpha * 31);
        prepareCorners(texSeed);

        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        int rTop = rgb;
        int rNS = shadeRgb(rgb, 0.85f);
        int rEW = shadeRgb(rgb, 0.73f);
        int rBot = shadeRgb(rgb, 0.62f);
        // Reflet : les faces du dessus attrapent la lumière (métal, glace, lumière).
        int rSpec = cubeGloss > 0.02f
                ? shadeRgb(rTop, 1.0f + 0.30f * cubeGloss)
                : rTop;

        // UP
        putQuadCorners(vc, mat, tmp, x0, y1, z0, 2, x0, y1, z1, 3, x1, y1, z1, 7, x1, y1, z0, 6,
                rSpec, alpha);
        // DOWN
        putQuadCorners(vc, mat, tmp, x0, y0, z1, 1, x0, y0, z0, 0, x1, y0, z0, 4, x1, y0, z1, 5,
                rBot, alpha);
        // NORTH
        putQuadCorners(vc, mat, tmp, x1, y1, z0, 6, x1, y0, z0, 4, x0, y0, z0, 0, x0, y1, z0, 2,
                rNS, alpha);
        // SOUTH
        putQuadCorners(vc, mat, tmp, x0, y1, z1, 3, x0, y0, z1, 1, x1, y0, z1, 5, x1, y1, z1, 7,
                rNS, alpha);
        // WEST
        putQuadCorners(vc, mat, tmp, x0, y1, z0, 2, x0, y0, z0, 0, x0, y0, z1, 1, x0, y1, z1, 3,
                rEW, alpha);
        // EAST
        putQuadCorners(vc, mat, tmp, x1, y1, z1, 7, x1, y0, z1, 5, x1, y0, z0, 4, x1, y1, z0, 6,
                rEW, alpha);
    }

    /**
     * T367-VOXEL-TEXTURE : une face de cube dont chaque coin porte sa nuance (issue de
     * {@link #CORNER_SHADE}). C'est ce qui donne l'aspect « texturé » sans texture : dégradé
     * interne, arêtes assombries, reflet en haut. Toujours exactement 4 sommets = 1 quad carré.
     */
    private static void putQuadCorners(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float x0, float y0, float z0, int c0,
                                       float x1, float y1, float z1, int c1,
                                       float x2, float y2, float z2, int c2,
                                       float x3, float y3, float z3, int c3,
                                       int rgb, int alpha) {
        putQuad(vc, mat, tmp,
                x0, y0, z0, shadeRgb(rgb, CORNER_SHADE[c0]), alpha,
                x1, y1, z1, shadeRgb(rgb, CORNER_SHADE[c1]), alpha,
                x2, y2, z2, shadeRgb(rgb, CORNER_SHADE[c2]), alpha,
                x3, y3, z3, shadeRgb(rgb, CORNER_SHADE[c3]), alpha);
    }


    /**
     * T363-ROTATED-VOXELS : mini-cube 3D <b>incliné sur les trois axes</b> (lacet, tangage, roulis).
     *
     * <p>C'est la brique qui manquait à tout le système : jusqu'ici chaque cube était aligné sur la
     * grille, donc toutes les formes se ressemblaient à des piles de LEGO verticales. Avec
     * l'inclinaison on peut enfin dessiner une vague qui se recourbe, un éclat de glace facetté,
     * une lame qui pivote, une écaille de sable, une volute d'ombre.</p>
     *
     * <p>L'ombrage reste celui de Minecraft mais il est calculé à partir de la normale TOURNÉE de
     * chaque face : un cube penché n'a donc plus des faces artificiellement claires.</p>
     */
    public static void emitVoxelBoxRotated(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           float cx, float cy, float cz,
                                           float hx, float hy, float hz,
                                           float yawRad, float pitchRad, float rollRad,
                                           int rgb, int alpha) {
        if (alpha <= 5 || hx <= 0.0005f || hy <= 0.0005f || hz <= 0.0005f) return;
        if (alphaFloor > 0 && alpha < alphaFloor) return;
        if (cubesSinceFlush >= HARD_CUBE_CAP_PER_FRAME) return;
        cubesSinceFlush++;

        float scale = (voxelPulseEnabled ? framePixelScale * smoothPulse(cx, cy, cz) : 1.0f) * variantScale;
        int sizeSeed = (int) (cx * 911.0f + cy * 577.0f + cz * 331.0f);
        scale *= 0.82f + 0.34f * pixelHash(0x51C3, sizeSeed);
        hx *= scale;
        hy *= scale;
        hz *= scale;

        if (pixelDensity < 0.999f && alpha < 200) {
            float keep = pixelHash(0x5EED, (int) (cx * 977.0f + cy * 613.0f + cz * 389.0f));
            if (keep > pixelDensity) return;
        }

        // T367-VOXEL-MATERIAL : même transparence et même grain que les cubes droits.
        if (cubeVeil < 0.999f) {
            alpha = Math.round(alpha * cubeVeil);
            if (alpha <= 5) return;
        }
        prepareCorners(sizeSeed ^ (alpha * 31));

        float cr = Mth.cos(rollRad), sr = Mth.sin(rollRad);
        float cp = Mth.cos(pitchRad), sp = Mth.sin(pitchRad);
        float cyw = Mth.cos(yawRad), syw = Mth.sin(yawRad);

        // 8 coins tournés, indexés par (xi << 2) | (yi << 1) | zi (0 = côté négatif)
        float[] p = ROT_CORNERS;
        for (int i = 0; i < 8; i++) {
            float ox = ((i & 4) == 0) ? -hx : hx;
            float oy = ((i & 2) == 0) ? -hy : hy;
            float oz = ((i & 1) == 0) ? -hz : hz;
            rotWrite(ox, oy, oz, cr, sr, cp, sp, cyw, syw, p, i * 3);
            p[i * 3] += cx;
            p[i * 3 + 1] += cy;
            p[i * 3 + 2] += cz;
        }

        // UP / DOWN / NORTH / SOUTH / WEST / EAST — même sens d'enroulement que le cube droit
        emitRotatedFace(vc, mat, tmp, p, 2, 3, 7, 6, rgb, alpha, 0.0f, 1.0f, 0.0f, cr, sr, cp, sp, cyw, syw);
        emitRotatedFace(vc, mat, tmp, p, 1, 0, 4, 5, rgb, alpha, 0.0f, -1.0f, 0.0f, cr, sr, cp, sp, cyw, syw);
        emitRotatedFace(vc, mat, tmp, p, 6, 4, 0, 2, rgb, alpha, 0.0f, 0.0f, -1.0f, cr, sr, cp, sp, cyw, syw);
        emitRotatedFace(vc, mat, tmp, p, 3, 1, 5, 7, rgb, alpha, 0.0f, 0.0f, 1.0f, cr, sr, cp, sp, cyw, syw);
        emitRotatedFace(vc, mat, tmp, p, 2, 0, 1, 3, rgb, alpha, -1.0f, 0.0f, 0.0f, cr, sr, cp, sp, cyw, syw);
        emitRotatedFace(vc, mat, tmp, p, 7, 5, 4, 6, rgb, alpha, 1.0f, 0.0f, 0.0f, cr, sr, cp, sp, cyw, syw);
    }

    /** Tampons réutilisés : le rendu ne doit allouer AUCUN tableau par cube (sinon le GC tousse). */
    private static final float[] ROT_CORNERS = new float[24];
    private static final float[] ROT_NORMAL = new float[3];

    /**
     * Applique roulis (Z), puis tangage (X), puis lacet (Y) à un vecteur et écrit le résultat dans
     * {@code out} à partir de {@code off} (aucune allocation).
     */
    private static void rotWrite(float x, float y, float z,
                                 float cr, float sr, float cp, float sp, float cyw, float syw,
                                 float[] out, int off) {
        float x1 = x * cr - y * sr;
        float y1 = x * sr + y * cr;
        float y2 = y1 * cp - z * sp;
        float z2 = y1 * sp + z * cp;
        out[off] = x1 * cyw + z2 * syw;
        out[off + 1] = y2;
        out[off + 2] = -x1 * syw + z2 * cyw;
    }

    /** Dessine une face d'un cube incliné, ombrée par sa normale tournée. */
    private static void emitRotatedFace(VertexConsumer vc, Matrix4f mat, Vector3f tmp, float[] p,
                                        int i0, int i1, int i2, int i3, int rgb, int alpha,
                                        float nx, float ny, float nz,
                                        float cr, float sr, float cp, float sp, float cyw, float syw) {
        rotWrite(nx, ny, nz, cr, sr, cp, sp, cyw, syw, ROT_NORMAL, 0);
        float lit = Mth.clamp(ROT_NORMAL[1] * 0.80f + ROT_NORMAL[0] * 0.30f + ROT_NORMAL[2] * 0.36f,
                0.0f, 1.0f);
        int col = shadeRgb(rgb, 0.58f + 0.42f * lit);
        // Reflet : les faces tournées vers le ciel s'éclaircissent (métal, glace, lumière).
        if (cubeGloss > 0.02f && ROT_NORMAL[1] > 0.25f) {
            col = shadeRgb(col, 1.0f + 0.26f * cubeGloss * ROT_NORMAL[1]);
        }
        // T367-VOXEL-TEXTURE : chaque coin garde sa nuance, même sur un cube incliné.
        putQuad(vc, mat, tmp,
                p[i0 * 3], p[i0 * 3 + 1], p[i0 * 3 + 2], shadeRgb(col, CORNER_SHADE[i0]), alpha,
                p[i1 * 3], p[i1 * 3 + 1], p[i1 * 3 + 2], shadeRgb(col, CORNER_SHADE[i1]), alpha,
                p[i2 * 3], p[i2 * 3 + 1], p[i2 * 3 + 2], shadeRgb(col, CORNER_SHADE[i2]), alpha,
                p[i3 * 3], p[i3 * 3 + 1], p[i3 * 3 + 2], shadeRgb(col, CORNER_SHADE[i3]), alpha);
    }

    /**
     * T365-HOLOGRAM : « à quel point ce cube est-il devant l'objet ? » (-1 derrière .. +1 devant).
     *
     * <p>Les positions sont déjà relatives à la caméra : la caméra est donc à l'origine et la
     * direction de vue est simplement l'opposé du centre de l'effet. On peut ainsi éclaircir les
     * cubes du devant et assombrir ceux du fond — c'est ce qui fait lire un VOLUME au lieu d'un
     * plan, et c'est exactement ce que fait un hologramme.</p>
     */
    // ============================================================================================
    // T366-VOXEL-SCULPT — VRAIES sculptures voxel denses (style MagicaVoxel)
    // --------------------------------------------------------------------------------------------
    // Jusqu'ici les effets étaient des « fils de cubes » : quelques cubes posés le long de courbes.
    // Une vraie sculpture voxel, c'est un VOLUME REMPLI puis creusé : un champ de distance signée
    // échantillonné sur une grille N³, bruité pour donner des bords organiques, dont on n'émet que
    // la PEAU (les voxels qui touchent du vide). C'est exactement la méthode des images de
    // référence : masses pleines, gradins de voxels, silhouette sculptée.
    // ============================================================================================

    /** Sculpture : masse d'eau qui se recourbe (vague déferlante pleine). */
    private static final int SCULPT_WAVE = 0;
    /** Sculpture : orbe massif, cratères et bulles (eau, vide, entropie). */
    private static final int SCULPT_ORB = 1;
    /** Sculpture : faisceau d'éclats pointus (glace, éclats, poison). */
    private static final int SCULPT_SHARD = 2;
    /** Sculpture : dalles de roche empilées et inclinées (terre, sable). */
    private static final int SCULPT_DUNE = 3;
    /** Sculpture : colonne torsadée à langues (feu, lumière, racines). */
    private static final int SCULPT_COLUMN = 4;
    /** Sculpture : cœur + pointes rayonnantes (métal, foudre, chaos, sang). */
    private static final int SCULPT_BURST = 5;
    /** Sculpture : vrilles hélicoïdales enroulées (vent, maelström, ombre). */
    private static final int SCULPT_TWISTER = 6;
    /** Sculpture : muraille de lave animée, large crête enroulée puis affaissée. */
    private static final int SCULPT_MOLTEN_WAVE = 7;

    /** Grille maximale d'une sculpture (24³ = 13 824 échantillons, calculés une seule fois). */
    private static final int SCULPT_MAX_RES = 24;
    /** Cases remplies de la dernière sculpture (1 = plein). */
    private static final byte[] SCULPT_OCC = new byte[SCULPT_MAX_RES * SCULPT_MAX_RES * SCULPT_MAX_RES];

    /** Bruit de valeur 3D déterministe (aucune allocation) : sert à éroder les formes. */
    private static float voxNoise(int seed, float x, float y, float z) {
        int xi = Mth.floor(x), yi = Mth.floor(y), zi = Mth.floor(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3.0f - 2.0f * fx);
        fy = fy * fy * (3.0f - 2.0f * fy);
        fz = fz * fz * (3.0f - 2.0f * fz);
        float c000 = pixelHash(seed, xi * 73856093 ^ yi * 19349663 ^ zi * 83492791);
        float c100 = pixelHash(seed, (xi + 1) * 73856093 ^ yi * 19349663 ^ zi * 83492791);
        float c010 = pixelHash(seed, xi * 73856093 ^ (yi + 1) * 19349663 ^ zi * 83492791);
        float c110 = pixelHash(seed, (xi + 1) * 73856093 ^ (yi + 1) * 19349663 ^ zi * 83492791);
        float c001 = pixelHash(seed, xi * 73856093 ^ yi * 19349663 ^ (zi + 1) * 83492791);
        float c101 = pixelHash(seed, (xi + 1) * 73856093 ^ yi * 19349663 ^ (zi + 1) * 83492791);
        float c011 = pixelHash(seed, xi * 73856093 ^ (yi + 1) * 19349663 ^ (zi + 1) * 83492791);
        float c111 = pixelHash(seed, (xi + 1) * 73856093 ^ (yi + 1) * 19349663 ^ (zi + 1) * 83492791);
        float x00 = c000 + (c100 - c000) * fx;
        float x10 = c010 + (c110 - c010) * fx;
        float x01 = c001 + (c101 - c001) * fx;
        float x11 = c011 + (c111 - c011) * fx;
        float y0 = x00 + (x10 - x00) * fy;
        float y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    /** Distance (normalisée par le rayon) d'un point 2D à un segment : sert aux « épines » des formes. */
    private static float segDist2(float pz, float py, float az, float ay, float bz, float by) {
        float vz = bz - az, vy = by - ay;
        float wz = pz - az, wy = py - ay;
        float len = vz * vz + vy * vy;
        float t = len > 1.0e-9f ? Mth.clamp((wz * vz + wy * vy) / len, 0.0f, 1.0f) : 0.0f;
        float cz = az + vz * t, cy2 = ay + vy * t;
        return Mth.sqrt((pz - cz) * (pz - cz) + (py - cy2) * (py - cy2));
    }

    /**
     * Champ de distance signée de chaque sculpture, dans une boîte [-1..1]³ :
     * négatif = à l'intérieur de la matière, positif = dans le vide.
     */
    private static float sculptSdf(int shape, float x, float y, float z, float t, int salt) {
        switch (shape) {
            case SCULPT_WAVE -> {
                // DÉFERLANTE : une colonne d'eau monte, se recourbe au-dessus de la cible et
                // retombe en lèvre. L'épine dorsale est un chemin de segments dans le plan (z,y) ;
                // chaque segment est épaisi, puis on limite en largeur (x) : masse pleine et
                // silhouette de vague parfaitement lisible de profil.
                float dmin = 1.0e9f;
                dmin = Math.min(dmin, segDist2(z, y, -0.34f, -1.05f, -0.30f, 0.20f) - 0.27f);
                dmin = Math.min(dmin, segDist2(z, y, -0.30f, 0.20f, 0.02f, 0.80f) - 0.25f);
                dmin = Math.min(dmin, segDist2(z, y, 0.02f, 0.80f, 0.50f, 0.86f) - 0.21f);
                dmin = Math.min(dmin, segDist2(z, y, 0.50f, 0.86f, 0.70f, 0.34f) - 0.17f);
                dmin = Math.min(dmin, segDist2(z, y, 0.70f, 0.34f, 0.60f, -0.34f) - 0.12f);
                float w = 0.56f - 0.20f * Mth.clamp((y + 1.05f) / 1.95f, 0.0f, 1.0f);
                float d = Math.max(dmin, Math.abs(x) - w);
                d = Math.max(d, Math.max(y - 1.02f, -y - 1.06f));
                return d + (voxNoise(salt, x * 3.6f, y * 3.6f, z * 3.6f) - 0.5f) * 0.15f;
            }
            case SCULPT_MOLTEN_WAVE -> {
                // La lave est large et lourde : une base épaisse porte une crête qui avance et
                // retombe devant elle. Le vide sous la lèvre reste ouvert pour donner une silhouette
                // de déferlante, y compris en miniature et sans lire le nom de l'effet.
                float riseU = Mth.clamp(t / 0.48f, 0.0f, 1.0f);
                float rise = riseU * riseU * (3.0f - 2.0f * riseU);
                float curlU = Mth.clamp((t - 0.24f) / 0.44f, 0.0f, 1.0f);
                float curl = curlU * curlU * (3.0f - 2.0f * curlU);
                float collapseU = Mth.clamp((t - 0.68f) / 0.32f, 0.0f, 1.0f);
                float collapse = collapseU * collapseU * (3.0f - 2.0f * collapseU);
                float height = 0.16f + 0.84f * rise * (1.0f - 0.58f * collapse);
                float crestY = -0.78f + 1.62f * height;
                float backZ = 0.46f - 0.18f * rise;
                float shoulderZ = 0.20f - 0.20f * curl;
                float crestZ = -0.12f - 0.42f * curl;
                float lipZ = crestZ - 0.32f - 0.28f * curl;
                float lipY = crestY - (0.22f + 0.60f * curl + 0.35f * collapse);

                float dmin = 1.0e9f;
                dmin = Math.min(dmin, segDist2(z, y, backZ, -0.98f, backZ, -0.28f + 0.20f * rise) - 0.29f);
                dmin = Math.min(dmin, segDist2(z, y, backZ, -0.28f + 0.20f * rise,
                        shoulderZ, crestY - 0.36f) - 0.25f);
                dmin = Math.min(dmin, segDist2(z, y, shoulderZ, crestY - 0.36f,
                        crestZ, crestY) - 0.22f);
                dmin = Math.min(dmin, segDist2(z, y, crestZ, crestY, lipZ, lipY) - 0.17f);
                dmin = Math.min(dmin, segDist2(z, y, lipZ, lipY,
                        lipZ + 0.08f, Math.max(-0.92f, lipY - 0.28f)) - 0.11f);
                // Semelle large qui relie visuellement la vague aux dalles de lave au sol.
                float base = Math.max(Math.abs(y + 0.88f) - 0.15f, Math.abs(z - 0.02f) - 0.64f);
                dmin = Math.min(dmin, base);
                float width = 0.84f - 0.16f * Mth.clamp((y + 1.0f) * 0.5f, 0.0f, 1.0f);
                float d = Math.max(dmin, Math.abs(x) - width);
                d = Math.max(d, Math.max(y - 1.04f, -y - 1.06f));
                return d + (voxNoise(salt, x * 4.4f, y * 4.4f, z * 4.4f) - 0.5f) * 0.12f;
            }
            case SCULPT_ORB -> {
                float rr = Mth.sqrt(x * x * 0.97f + y * y + z * z * 0.97f);
                float d = rr - (0.86f + 0.10f * Mth.sin(t * 3.1f));
                d += (voxNoise(salt, x * 2.5f, y * 2.5f, z * 2.5f) - 0.5f) * 0.36f;
                float n2 = voxNoise(salt + 91, x * 5.0f + 3.0f, y * 5.0f, z * 5.0f + 7.0f);
                d += Math.max(0.0f, n2 - 0.60f) * 1.10f;
                return d;
            }
            case SCULPT_SHARD -> {
                // Grappe de cristaux : cinq aiguilles facettées de hauteurs différentes.
                float best = 1.0e9f;
                best = Math.min(best, shardNeedle(x, y, z, 0.00f, 0.00f, 1.00f, 0.17f));
                best = Math.min(best, shardNeedle(x, y, z, 0.36f, 0.22f, 0.78f, 0.14f));
                best = Math.min(best, shardNeedle(x, y, z, -0.32f, 0.26f, 0.68f, 0.13f));
                best = Math.min(best, shardNeedle(x, y, z, 0.18f, -0.36f, 0.58f, 0.12f));
                best = Math.min(best, shardNeedle(x, y, z, -0.24f, -0.30f, 0.48f, 0.11f));
                return best + (voxNoise(salt, x * 4.0f, y * 4.0f, z * 4.0f) - 0.5f) * 0.11f;
            }
            case SCULPT_DUNE -> {
                // Dalles de roche empilées : trois strates inclinées, décalées, à bords cassés.
                float best = 1.0e9f;
                for (int s = 0; s < 3; s++) {
                    float ang = s * 1.10f + 0.35f;
                    float ca = Mth.cos(ang), sa = Mth.sin(ang);
                    float ux = x * ca + z * sa;
                    float uz = -x * sa + z * ca;
                    float tilt = 0.34f + 0.16f * s;
                    float yy = y - (s - 1) * 0.46f - ux * tilt;
                    float d = Math.max(Math.abs(uz) - (0.46f + 0.10f * s), Math.abs(yy) - 0.17f);
                    d = Math.max(d, Math.abs(ux) - (0.66f + 0.16f * s));
                    best = Math.min(best, d);
                }
                return best + (voxNoise(salt, x * 4.2f, y * 4.2f, z * 4.2f) - 0.5f) * 0.16f;
            }
            case SCULPT_COLUMN -> {
                float ty = Mth.clamp((y + 1.0f) * 0.5f, 0.0f, 1.0f);
                float twist = ty * 2.7f;
                float ca = Mth.cos(twist), sa = Mth.sin(twist);
                float ux = x * ca + z * sa;
                float uz = -x * sa + z * ca;
                float rad = (0.60f - 0.50f * ty) * (1.0f + 0.14f * Mth.sin(ty * 8.5f));
                float d = Mth.sqrt(ux * ux + uz * uz * 1.15f) - rad;
                d = Math.max(d, Math.max(y - 0.98f, -y - 0.98f));
                float lick = 1.0e9f;
                for (int s = 0; s < 4; s++) {
                    float a = s * 1.5708f + 0.55f;
                    float px = Mth.cos(a) * 0.26f, pz = Mth.sin(a) * 0.26f;
                    float lx = x - px, lz = z - pz;
                    float high = 1.0f + 0.30f * pixelHash(salt, s * 31);
                    float w = 0.17f * (1.0f - Mth.clamp((y - 0.10f) / high, 0.0f, 1.0f)) + 0.01f;
                    float dd = Mth.sqrt(lx * lx + lz * lz) - w;
                    dd = Math.max(dd, Math.max(y - high, -y - 0.98f));
                    lick = Math.min(lick, dd);
                }
                return Math.min(d, lick) + (voxNoise(salt, x * 3.4f, y * 3.4f, z * 3.4f) - 0.5f) * 0.20f;
            }
            case SCULPT_BURST -> {
                // Cœur massif + 14 pointes réparties sur la sphère : la lecture « explosion ».
                float core = Mth.sqrt(x * x + y * y + z * z) - (0.46f + 0.10f * t);
                float spikes = 1.0e9f;
                float norm2 = x * x + y * y + z * z;
                for (int s = 0; s < 14; s++) {
                    float a = s * 2.399f;
                    float by = 1.0f - 2.0f * ((s + 0.5f) / 14.0f);
                    float br = Mth.sqrt(Math.max(0.0f, 1.0f - by * by));
                    float dx = Mth.cos(a) * br, dz = Mth.sin(a) * br;
                    float proj = x * dx + y * by + z * dz;
                    float perp = Mth.sqrt(Math.max(0.0f, norm2 - proj * proj));
                    float len = (0.80f + 0.30f * pixelHash(salt, s * 17)) * (0.55f + 0.60f * t);
                    float w = 0.135f * (1.0f - 0.80f * Mth.clamp(proj / Math.max(0.2f, len), 0.0f, 1.0f))
                            + 0.008f;
                    float dd = Math.max(perp - w, Math.max(-proj - 0.05f, proj - len));
                    spikes = Math.min(spikes, dd);
                }
                return Math.min(core, spikes)
                        + (voxNoise(salt, x * 4.4f, y * 4.4f, z * 4.4f) - 0.5f) * 0.10f;
            }
            case SCULPT_TWISTER -> {
                // TORNADE : entonnoir creux + trois crêtes hélicoïdales qui débordent.
                float ty = Mth.clamp((y + 1.0f) * 0.5f, 0.0f, 1.0f);
                float cone = (0.30f + 0.52f * (float) Math.pow(ty, 0.85)) * (1.0f + 0.05f * Mth.sin(ty * 9.0f));
                float r = Mth.sqrt(x * x + z * z);
                float d = r - cone;
                d = Math.max(d, -(r - cone * 0.45f));
                float ang = (float) Math.atan2(z, x);
                float ridge = 1.0e9f;
                for (int s = 0; s < 3; s++) {
                    float ph = ang + ty * 7.4f + s * 2.094f;
                    float rr = Mth.sqrt((r - cone - 0.02f) * (r - cone - 0.02f)
                            + (Mth.sin(ph) * 0.10f) * (Mth.sin(ph) * 0.10f));
                    ridge = Math.min(ridge, rr - 0.13f);
                }
                d = Math.min(d, ridge);
                d = Math.max(d, Math.max(y - 1.00f, -y - 1.02f));
                return d + (voxNoise(salt, x * 3.4f, y * 3.4f, z * 3.4f) - 0.5f) * 0.14f;
            }
            default -> {
                return Mth.sqrt(x * x + y * y + z * z) - 0.80f;
            }
        }
    }

    /** Une aiguille de cristal : pyramide à base carrée tournée (facettes nettes, pas un cylindre). */
    private static float shardNeedle(float x, float y, float z,
                                     float ox, float oz, float hgt, float rad0) {
        float ty = Mth.clamp((y + 0.55f) / (hgt + 0.55f), 0.0f, 1.0f);
        float rad = rad0 * (1.0f - (float) Math.pow(ty, 1.35)) + 0.012f;
        float facet = (Math.abs(x - ox) + Math.abs(z - oz)) * 0.80f - rad;
        return Math.max(facet, Math.max(y - hgt, -y - 0.60f));
    }

    /** Remplit la grille {@code res³} avec la sculpture (booléen de matière). */
    private static void fillSculpt(int shape, int res, float t, int salt, float cyaw, float syaw) {
        float step = 2.0f / res;
        for (int i = 0; i < res; i++) {
            float nx = -1.0f + (i + 0.5f) * step;
            for (int k = 0; k < res; k++) {
                float nz = -1.0f + (k + 0.5f) * step;
                // rotation autour de l'axe vertical : la sculpture tourne sur elle-même
                float sx = nx * cyaw - nz * syaw;
                float sz = nx * syaw + nz * cyaw;
                for (int j = 0; j < res; j++) {
                    float ny = -1.0f + (j + 0.5f) * step;
                    byte in = (byte) (sculptSdf(shape, sx, ny, sz, t, salt) < 0.0f ? 1 : 0);
                    SCULPT_OCC[(i * res + j) * res + k] = in;
                }
            }
        }
    }

    /** Vrai si ce voxel plein touche du vide sur l'une de ses 6 faces (donc s'il est visible). */
    private static boolean sculptSkin(int res, int i, int j, int k) {
        int idx = (i * res + j) * res + k;
        if (SCULPT_OCC[idx] == 0) return false;
        if (i == 0 || i == res - 1 || j == 0 || j == res - 1 || k == 0 || k == res - 1) return true;
        int di = res * res;
        int dj = res;
        return SCULPT_OCC[idx - di] == 0 || SCULPT_OCC[idx + di] == 0
                || SCULPT_OCC[idx - dj] == 0 || SCULPT_OCC[idx + dj] == 0
                || SCULPT_OCC[idx - 1] == 0 || SCULPT_OCC[idx + 1] == 0;
    }

    private static int countSculptSkin(int res) {
        int n = 0;
        for (int i = 0; i < res; i++) {
            for (int j = 0; j < res; j++) {
                for (int k = 0; k < res; k++) {
                    if (sculptSkin(res, i, j, k)) n++;
                }
            }
        }
        return n;
    }

    /** Matière propre à MOLTEN_FIRE_WAVE : croûte sombre, plaques orange et fissures très chaudes. */
    private static int moltenLavaRgb(float nx, float ny, float nz, float u, int salt,
                                     int deep, int body, int high, int tip) {
        float warp = voxNoise(salt + 0x1A7A, nx * 2.3f, ny * 2.3f, nz * 2.3f) - 0.5f;
        float plates = voxNoise(salt + 0x1A73,
                nx * 6.4f + warp * 1.8f, ny * 6.4f + nx * 0.6f, nz * 6.4f - warp * 1.8f);
        float grain = voxNoise(salt + 0x51A9, nx * 15.0f, ny * 15.0f, nz * 15.0f);
        float crack = Math.abs(plates - 0.5f);
        int rgb;
        if (crack < 0.075f) {
            float heat = 1.0f - crack / 0.075f;
            rgb = mixRgb(body, tip, 0.35f + 0.55f * heat);
        } else if (plates > 0.61f) {
            rgb = mixRgb(deep, body, 0.25f + 0.20f * grain);
        } else if (plates < 0.39f) {
            rgb = mixRgb(body, high, 0.42f + 0.30f * grain);
        } else {
            rgb = mixRgb(body, high, 0.20f + 0.40f * grain);
        }
        float crustMask = voxNoise(salt + 0xC057, nx * 3.8f, ny * 3.8f, nz * 3.8f);
        if (crustMask > 0.64f && crack > 0.08f) {
            rgb = mixRgb(rgb, mixRgb(deep, body, 0.18f), 0.55f);
        }
        // Jeu de lumière local : seule l'intensité respire, sans rotation de teinte ni scintillement.
        float pulse = 0.94f + 0.06f * Mth.sin(frameTimeSeconds * 3.0f + nx * 5.1f + ny * 3.7f + nz * 4.3f);
        return shadeRgb(rgb, pulse);
    }

    /**
     * T366-VOXEL-SCULPT : émet une <b>vraie sculpture voxel</b> dense à cet endroit.
     *
     * <p>La résolution est choisie automatiquement pour tenir dans le budget de cubes : la forme
     * reste donc toujours bien plus dense que les anciens « fils de cubes », sans jamais faire
     * exploser le nombre de faces envoyées au GPU.</p>
     *
     * @return le nombre de cubes émis (utile au mode test)
     */
    public static int emitVoxelSculpt(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      int shape, float cx, float cy, float cz,
                                      float radius, float yawRad, float t,
                                      int deep, int body, int high, int tip,
                                      int salt, int alpha, int budget) {
        if (alpha <= 6 || radius <= 0.05f) return 0;
        int room = HARD_CUBE_CAP_PER_FRAME - cubesSinceFlush;
        if (room < 48) return 0;
        int maxCubes = Math.min(budget, room);

        float cyaw = Mth.cos(yawRad), syaw = Mth.sin(yawRad);
        // La peau croît comme res² : on sonde une grille moyenne puis on ajuste en UNE passe, pour
        // ne jamais remplir la grille plus de trois fois par frame (coût CPU borné).
        int res = 16;
        fillSculpt(shape, res, t, salt, cyaw, syaw);
        int skin = countSculptSkin(res);
        if (skin > maxCubes) {
            res = Mth.clamp((int) (res * Mth.sqrt(maxCubes / (float) skin)), 8, SCULPT_MAX_RES);
            fillSculpt(shape, res, t, salt, cyaw, syaw);
            skin = countSculptSkin(res);
            while (skin > maxCubes && res > 8) {
                res--;
                fillSculpt(shape, res, t, salt, cyaw, syaw);
                skin = countSculptSkin(res);
            }
        } else if (res < SCULPT_MAX_RES) {
            res = Mth.clamp((int) (res * Mth.sqrt(maxCubes / (float) Math.max(1, skin))), res, SCULPT_MAX_RES);
            fillSculpt(shape, res, t, salt, cyaw, syaw);
            skin = countSculptSkin(res);
        }
        // La nouvelle vague de lave garantit aussi son budget APRÈS l'agrandissement automatique.
        // Ce second contrôle est volontairement local à cette sculpture : aucune autre animation
        // ne change de résolution ou de densité.
        if (shape == SCULPT_MOLTEN_WAVE) {
            while (skin > maxCubes && res > 8) {
                res--;
                fillSculpt(shape, res, t, salt, cyaw, syaw);
                skin = countSculptSkin(res);
            }
        }
        if (skin <= 0) return 0;

        float step = 2.0f / res;
        float hsz = radius * step * 0.54f;   // léger recouvrement : aucune fente entre voxels
        int emitted = 0;
        for (int i = 0; i < res; i++) {
            float nx = -1.0f + (i + 0.5f) * step;
            for (int k = 0; k < res; k++) {
                float nz = -1.0f + (k + 0.5f) * step;
                float sx = nx * cyaw - nz * syaw;
                float sz = nx * syaw + nz * cyaw;
                for (int j = 0; j < res; j++) {
                    if (!sculptSkin(res, i, j, k)) continue;
                    float ny = -1.0f + (j + 0.5f) * step;
                    int voxSalt = salt + i * 73 + j * 131 + k * 17;
                    // Dégradé du matériau : creux sombre en bas, pointe presque blanche en haut,
                    // avec une nuance par voxel (la sculpture n'est jamais monochrome).
                    float u = Mth.clamp(0.42f + 0.62f * (ny * 0.5f + 0.5f) + 0.20f * sz, 0.0f, 1.0f);
                    int rgb = rampRgb(u, deep, body, high, tip);
                    if (shape == SCULPT_MOLTEN_WAVE) {
                        rgb = moltenLavaRgb(nx, ny, nz, u, salt, deep, body, high, tip);
                    } else {
                        rgb = jitterRgb(rgb, voxSalt, 0.050f);
                    }


                    if (pixelDensity < 0.999f && alpha < 200) {
                        float keep = pixelHash(0x5EED ^ shape, (i * 977 + j * 613 + k * 389));
                        if (keep > pixelDensity) continue;
                    }

                    // Tailles variées, sans omettre de voisins (les sculptures restent pleines).
                    // L'opacité vient uniquement de la matière, jamais de l'épaisseur par hasard.
                    float grain = pixelHash(voxSalt, 13);
                    float sizeMul = 0.82f + 0.30f * grain;
                    if (grain > 0.97f) sizeMul *= 1.12f;
                    else if (grain < 0.08f) sizeMul *= 0.88f;
                    int voxAlpha = alpha;

                    float px = cx + sx * radius, py = cy + ny * radius, pz = cz + sz * radius;
                    emitVoxelCube(vc, mat, tmp, px, py, pz,
                            hsz * sizeMul, hsz * sizeMul, hsz * sizeMul, rgb, voxAlpha);
                    emitted++;

                }
            }
        }
        return emitted;
    }

    private static float nearness(float cx, float cy, float cz, float px, float py, float pz) {
        float l = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (l < 0.01f) return 0.0f;
        float vx = -cx / l, vy = -cy / l, vz = -cz / l;
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 0.001f) return 0.0f;
        return (dx * vx + dy * vy + dz * vz) / dl;
    }

    /** Opacité d'un cube selon sa profondeur : devant = franc, derrière = translucide. */
    private static int depthAlpha(int alpha, float nearness) {
        return Math.round(alpha * (0.34f + 0.66f * (0.5f + 0.5f * nearness)));
    }

    /**
     * T365-HOLOGRAM : <b>cage filaire 3D</b> — les 12 arêtes d'un cube, en vrais mini-cubes à
     * 6 faces, qui tourne sur deux axes.
     *
     * <p>Ce n'est PAS un plan : chaque arête a de la profondeur, les cubes du fond sont plus
     * translucides que ceux du devant (parallaxe réelle), et les huit sommets portent un nœud plus
     * gros et plus clair. C'est la figure « hologramme » classique.</p>
     */
    public static void emitPixelHoloCage3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           float cx, float cy, float cz, float half,
                                           float yaw, float pitch, float roll,
                                           int edgeRgb, int nodeRgb, int alpha, int salt) {
        if (alpha <= 8 || half <= 0.02f) return;
        float cr = Mth.cos(roll), sr = Mth.sin(roll);
        float cp = Mth.cos(pitch), sp = Mth.sin(pitch);
        float cyw = Mth.cos(yaw), syw = Mth.sin(yaw);

        float[] p = ROT_CORNERS;
        for (int i = 0; i < 8; i++) {
            float ox = ((i & 4) == 0) ? -half : half;
            float oy = ((i & 2) == 0) ? -half : half;
            float oz = ((i & 1) == 0) ? -half : half;
            rotWrite(ox, oy, oz, cr, sr, cp, sp, cyw, syw, p, i * 3);
            p[i * 3] += cx;
            p[i * 3 + 1] += cy;
            p[i * 3 + 2] += cz;
        }

        int steps = 5;
        float nodeHalf = half * 0.085f;
        for (int axis = 0; axis < 3; axis++) {
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    int i0;
                    int i1;
                    if (axis == 0) {
                        i0 = (a << 1) | b;
                        i1 = (1 << 2) | (a << 1) | b;
                    } else if (axis == 1) {
                        i0 = (a << 2) | b;
                        i1 = (a << 2) | (1 << 1) | b;
                    } else {
                        i0 = (a << 2) | (b << 1);
                        i1 = (a << 2) | (b << 1) | 1;
                    }
                    for (int s = 1; s < steps; s++) {
                        float u = s / (float) steps;
                        float px = Mth.lerp(u, p[i0 * 3], p[i1 * 3]);
                        float py = Mth.lerp(u, p[i0 * 3 + 1], p[i1 * 3 + 1]);
                        float pz = Mth.lerp(u, p[i0 * 3 + 2], p[i1 * 3 + 2]);
                        float nr = nearness(cx, cy, cz, px, py, pz);
                        float hsz = nodeHalf * (0.62f + 0.42f * pixelHash(salt, axis * 41 + s * 7 + a * 3 + b));
                        emitVoxelCube(vc, mat, tmp, px, py, pz, hsz, hsz, hsz, edgeRgb,
                                depthAlpha(alpha, nr));
                    }
                }
            }
        }
        // Nœuds des 8 sommets
        for (int i = 0; i < 8; i++) {
            float px = p[i * 3], py = p[i * 3 + 1], pz = p[i * 3 + 2];
            float nr = nearness(cx, cy, cz, px, py, pz);
            emitVoxelCube(vc, mat, tmp, px, py, pz, nodeHalf * 1.7f, nodeHalf * 1.7f, nodeHalf * 1.7f,
                    nodeRgb, depthAlpha(alpha, nr));
        }
    }

    /**
     * T365-HOLOGRAM : <b>globe holographique</b> — anneaux de latitude en cubes, tournant autour de
     * l'axe vertical, avec dégradé de profondeur (le fond de la sphère est translucide).
     */
    public static void emitPixelHoloSphere3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                             float cx, float cy, float cz, float radius,
                                             float yaw, int ringRgb, int accentRgb,
                                             int alpha, int salt) {
        if (alpha <= 8 || radius <= 0.05f) return;
        int lat = 6;
        float cyw = Mth.cos(yaw), syw = Mth.sin(yaw);
        for (int i = 0; i < lat; i++) {
            float theta = -1.5707964f + (i + 0.5f) * ((float) Math.PI / lat);
            float yy = radius * Mth.sin(theta);
            float rr = radius * Mth.cos(theta);
            int n = Math.max(6, Math.round(rr / 0.072f));
            for (int k = 0; k < n; k++) {
                float ang = k * (float) (Math.PI * 2.0 / n) + yaw;
                float lx = Mth.cos(ang) * rr;
                float lz = Mth.sin(ang) * rr;
                float px = cx + lx * cyw + lz * syw;
                float pz = cz - lx * syw + lz * cyw;
                float py = cy + yy;
                float nr = nearness(cx, cy, cz, px, py, pz);
                boolean pole = (i == 0 || i == lat - 1);
                float hsz = radius * (pole ? 0.040f : 0.052f)
                        * (0.80f + 0.40f * pixelHash(salt, i * 37 + k));
                emitVoxelCube(vc, mat, tmp, px, py, pz, hsz, hsz, hsz,
                        (k % 4 == 0) ? accentRgb : ringRgb, depthAlpha(alpha, nr));
            }
        }
        // Méridien accentué : deux arcs verticaux opposés donnent la lecture « globe »
        for (int m = 0; m < 2; m++) {
            float phi = m * (float) Math.PI;
            for (int k = 1; k < 12; k++) {
                float th = -1.5707964f + k * ((float) Math.PI / 12.0f);
                float lx = Mth.cos(th) * Mth.cos(phi) * radius;
                float lz = Mth.cos(th) * Mth.sin(phi) * radius;
                float px = cx + lx * cyw + lz * syw;
                float pz = cz - lx * syw + lz * cyw;
                float py = cy + Mth.sin(th) * radius;
                float nr = nearness(cx, cy, cz, px, py, pz);
                emitVoxelCube(vc, mat, tmp, px, py, pz, radius * 0.032f, radius * 0.032f, radius * 0.032f,
                        accentRgb, depthAlpha(alpha, nr));
            }
        }
    }

    /**
     * T365-HOLOGRAM : <b>disque holographique à épaisseur</b> — trois anneaux légèrement inclinés
     * les uns par rapport aux autres, plus un cœur de pixels.
     *
     * <p>Remplace le disque « face caméra » (un plan) : ici l'objet a une vraie épaisseur et une
     * parallaxe, donc il tourne avec la caméra au lieu de la suivre.</p>
     */
    public static void emitPixelHoloDisc3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           float cx, float cy, float cz, float radius,
                                           float yawRad, float pitchRad,
                                           int rgbA, int rgbB, int alpha, int salt) {
        if (alpha <= 8 || radius <= 0.04f) return;
        float dx = Mth.sin(yawRad), dz = Mth.cos(yawRad);
        float sx = Mth.cos(yawRad), sz = -Mth.sin(yawRad);
        float hsz = radius * 0.038f;

        for (int ringI = 0; ringI < 3; ringI++) {
            float rr = radius * (1.0f - 0.13f * ringI);
            float tilt = pitchRad + (ringI - 1) * 0.10f;
            float ct = Mth.cos(tilt), st = Mth.sin(tilt);
            int n = Math.max(10, Math.round(rr / 0.068f));
            for (int k = 0; k < n; k++) {
                float ang = k * (float) (Math.PI * 2.0 / n) + ringI * 0.22f;
                float ox = Mth.cos(ang) * rr;
                float oz = Mth.sin(ang) * rr;
                float px = cx + sx * ox + dx * (oz * st);
                float py = cy + oz * ct;
                float pz = cz + sz * ox + dz * (oz * st);
                float nr = nearness(cx, cy, cz, px, py, pz);
                float s2 = hsz * (0.78f + 0.42f * pixelHash(salt, ringI * 53 + k));
                emitVoxelCube(vc, mat, tmp, px, py, pz, s2, s2, s2,
                        ((k + ringI) % 5 == 0) ? rgbB : rgbA, depthAlpha(alpha, nr));
            }
        }
        // Cœur : quelques pixels suspendus entre les anneaux (profondeur supplémentaire)
        for (int k = 0; k < 7; k++) {
            float ang = k * 0.897f + salt * 0.01f;
            float rr = radius * (0.18f + 0.22f * pixelHash(salt, k * 19));
            float px = cx + sx * Mth.cos(ang) * rr + dx * Mth.sin(ang) * rr * 0.35f;
            float py = cy + Mth.sin(ang) * rr * 0.55f;
            float pz = cz + sz * Mth.cos(ang) * rr + dz * Mth.sin(ang) * rr * 0.35f;
            float nr = nearness(cx, cy, cz, px, py, pz);
            emitVoxelCube(vc, mat, tmp, px, py, pz, hsz * 0.7f, hsz * 0.7f, hsz * 0.7f, rgbB,
                    depthAlpha(Math.round(alpha * 0.85f), nr));
        }
    }

    /**
     * T363-VAGUE : une <b>vraie vague déferlante</b> en cubes inclinés.
     *
     * <p>Une paroi d'eau qui monte en se recourbant vers la cible (la crête bascule en avant,
     * le pied reste planté), avec une crête d'écume blanche, des cubes de plus en plus petits et
     * de plus en plus transparents en montant, et des embruns qui retombent.</p>
     */
    public static void emitPixelWave3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float cy, float cz,
                                       float radius, float height, float yawRad,
                                       float curl, float progress,
                                       int bodyRgb, int deepRgb, int foamRgb,
                                       int alpha, int salt) {
        if (alpha <= 6 || radius <= 0.05f || height <= 0.01f) return;
        float dirX = Mth.sin(yawRad);
        float dirZ = Mth.cos(yawRad);
        float sideX = Mth.cos(yawRad);
        float sideZ = -Mth.sin(yawRad);
        // Base de la vague : toujours nettement plus sombre que le corps (effet de profondeur).
        int darkerTone = (luminanceRgb(deepRgb) <= luminanceRgb(bodyRgb)) ? deepRgb : bodyRgb;
        int deepTone = shadeRgb(mixRgb(darkerTone, bodyRgb, 0.25f), 0.55f);
        int midTone = mixRgb(bodyRgb, foamRgb, 0.12f);
        int crestTone = mixRgb(bodyRgb, foamRgb, 0.28f);
        int steps = 15;
        int levels = 4;

        for (int i = 0; i < steps; i++) {
            float u = i / (float) (steps - 1);
            float env = Mth.sin(u * (float) Math.PI);
            env = Mth.sqrt(Mth.clamp(env, 0.0f, 1.0f));      // crête large, pieds fins
            float lateral = (u - 0.5f) * 2.0f * radius;
            float h = height * env * (0.55f + 0.45f * progress);
            float wob = Mth.sin(u * 7.0f + frameTimeSeconds * 2.2f) * 0.035f * env;

            for (int l = 0; l < levels; l++) {
                float v = l / (float) (levels - 1);
                float lean = curl * v * v * 0.70f * radius;   // la vague se recourbe vers la cible
                float px = cx + sideX * (lateral + wob) + dirX * lean;
                float pz = cz + sideZ * (lateral + wob) + dirZ * lean;
                float py = cy + v * h;
                float half = (0.055f - 0.018f * v) * (0.70f + 0.45f * env);
                // Dégradé de bleus clairs; l'écume réelle est un accent séparé et discontinu.
                int col = rampRgb(v, deepTone, bodyRgb, midTone, crestTone);
                // éclairage directionnel : le flanc tourné vers la lumière est plus clair -> volume
                float lit = 0.84f + 0.26f * (0.5f + 0.5f * Mth.cos(u * 6.2831855f - 0.6f));
                col = shadeRgb(jitterRgb(col, salt + i * 31 + l, 0.055f), lit);
                int a = Math.round(alpha * (0.72f + 0.28f * v) * (0.40f + 0.60f * env));
                float pitch = curl * v * 1.15f;               // la crête bascule vers l'avant
                float roll = (u - 0.5f) * 0.55f;              // la paroi ondule latéralement
                emitVoxelBoxRotated(vc, mat, tmp, px, py, pz,
                        half, half * 0.85f, half * 0.55f,
                        yawRad, pitch, roll, col, a);
            }

            // Écume : rare, uniquement sur la lèvre supérieure, jamais sur toute la vague.
            if (env > 0.62f && ((i + salt) & 3) == 0) {
                float lean = curl * 0.70f * radius;
                float px = cx + sideX * lateral + dirX * lean;
                float pz = cz + sideZ * lateral + dirZ * lean;
                emitVoxelBoxRotated(vc, mat, tmp, px, cy + h + 0.035f, pz,
                        0.032f, 0.022f, 0.026f, yawRad + 0.4f, curl * 1.35f, 0.30f,
                        jitterRgb(foamRgb, salt + i * 7, 0.04f), Math.round(alpha * 0.95f * env));
            }
        }

        // Embruns : gouttes projetées depuis la crête qui retombent (chute libre).
        for (int s = 0; s < 6; s++) {
            float u = (s + 0.5f) / 6.0f;
            float lateral = (u - 0.5f) * 2.0f * radius;
            float tt = progress;
            float up = 0.55f * tt - 1.30f * tt * tt;
            float px = cx + sideX * lateral + dirX * (curl * 0.75f * radius);
            float pz = cz + sideZ * lateral + dirZ * (curl * 0.75f * radius);
            emitVoxelBoxRotated(vc, mat, tmp,
                    quantize(px, 0.0625f), quantize(cy + height * 0.80f + up, 0.0625f),
                    quantize(pz, 0.0625f),
                    0.024f, 0.034f, 0.024f, yawRad, 0.6f * tt, 0.4f,
                    s == 2 ? foamRgb : bodyRgb, Math.round(alpha * 0.85f));
        }
    }

    /**
     * T363-FLAMME : une <b>langue de flamme</b> — chaîne de cubes inclinés qui se courbe en
     * s'affinant vers le haut, avec un dégradé de couleur et d'opacité (le pied opaque, la pointe
     * translucide). C'est ce qui donne l'aspect « feu » plutôt qu'une pile de blocs.
     */
    public static void emitPixelFlameLick3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            float x, float y, float z,
                                            float height, float baseHalf, float sway,
                                            float phase, int coreRgb, int tipRgb,
                                            int alpha, int salt) {
        if (alpha <= 6 || height <= 0.02f) return;
        int segs = 6;
        for (int k = 0; k < segs; k++) {
            float u = k / (float) (segs - 1);
            float bend = Mth.sin(phase + u * 3.1f) * sway * u;
            float bendZ = Mth.cos(phase * 0.7f + u * 2.3f) * sway * 0.55f * u;
            float half = baseHalf * (1.0f - 0.68f * u);
            // FEU : dégradé continu du rouge sombre (pied) au blanc-jaune (pointe)
            int col = rampRgb(u, shadeRgb(coreRgb, 0.52f), coreRgb,
                    mixRgb(coreRgb, tipRgb, 0.55f), tipRgb);
            col = jitterRgb(col, salt + k * 17, 0.06f);
            int a = Math.round(alpha * (1.0f - 0.30f * u));
            emitVoxelBoxRotated(vc, mat, tmp, x + bend, y + u * height, z + bendZ,
                    half, half * 1.30f, half * 0.85f,
                    Mth.sin(phase) * 0.25f,
                    Mth.sin(phase + u * 3.1f + 1.57f) * sway * 2.4f,
                    bendZ * 3.0f, col, a);
        }
    }

    /**
     * T363-ÉCLAT : un <b>éclat facetté</b> (glace, métal, pierre, croc) — trois segments qui
     * s'affinent jusqu'à une pointe, alignés sur une direction 3D libre, avec une facette
     * latérale pour l'aspect cristal.
     */
    public static void emitPixelShard3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float x, float y, float z,
                                        float length, float half,
                                        float dirX, float dirY, float dirZ,
                                        int baseRgb, int tipRgb, int alpha, int salt) {
        if (alpha <= 6 || length <= 0.01f) return;
        float len = Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len < 0.001f) return;
        float nx = dirX / len, ny = dirY / len, nz = dirZ / len;
        float yaw = (float) Math.atan2(nx, nz);
        float pitch = (float) Math.acos(Mth.clamp(ny, -1.0f, 1.0f));

        for (int k = 0; k < 3; k++) {
            float u = (k + 0.5f) / 3.0f;
            float h = half * (1.0f - 0.64f * u);
            emitVoxelBoxRotated(vc, mat, tmp,
                    x + nx * length * u, y + ny * length * u, z + nz * length * u,
                    h * 0.60f, length * 0.20f, h,
                    yaw, pitch, 0.28f * ((salt & 1) == 0 ? 1.0f : -1.0f),
                    jitterRgb(rampRgb(u, shadeRgb(baseRgb, 0.55f), baseRgb,
                            mixRgb(baseRgb, tipRgb, 0.60f), tipRgb), salt + k * 23, 0.05f),
                    Math.round(alpha * (1.0f - 0.22f * u)));
        }
        // petite facette latérale : cassure du cristal
        emitVoxelBoxRotated(vc, mat, tmp,
                x + nx * length * 0.42f + nz * half * 0.8f, y + ny * length * 0.42f, z + nz * length * 0.42f - nx * half * 0.8f,
                half * 0.34f, length * 0.10f, half * 0.34f,
                yaw, pitch + 0.6f, 0.5f, tipRgb, Math.round(alpha * 0.75f));
    }

    /**
     * T363-LIANE : une <b>liane / tentacule courbe</b> — chaîne de cubes inclinés suivant une
     * courbe bombée, qui s'affine et change de teinte vers la pointe. Sert au bois (racines), à
     * l'ombre (tentacules), au poison (fumée) et au sang (veines).
     */
    public static void emitPixelTendril3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float bow, int segments, float half,
                                          int baseRgb, int tipRgb, int alpha, int salt) {
        if (alpha <= 6 || segments < 2) return;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01f) return;

        float px = -dz / dist;
        float pz = dx / dist;
        float pl = Mth.sqrt(px * px + pz * pz);
        if (pl < 0.001f) { px = 1.0f; pz = 0.0f; pl = 1.0f; }
        px /= pl;
        pz /= pl;

        float prevX = x0, prevY = y0, prevZ = z0;
        for (int k = 1; k <= segments; k++) {
            float u = k / (float) segments;
            float bend = Mth.sin(u * (float) Math.PI) * bow;
            float cxx = Mth.lerp(u, x0, x1) + px * bend;
            float cyy = Mth.lerp(u, y0, y1) + bend * 0.35f;
            float czz = Mth.lerp(u, z0, z1) + pz * bend;
            float sx = cxx - prevX, sy = cyy - prevY, sz = czz - prevZ;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl > 0.0005f) {
                float yaw = (float) Math.atan2(sx / sl, sz / sl);
                float pitch = (float) Math.acos(Mth.clamp(sy / sl, -1.0f, 1.0f));
                float h = half * (1.0f - 0.58f * u);
                emitVoxelBoxRotated(vc, mat, tmp,
                        (prevX + cxx) * 0.5f, (prevY + cyy) * 0.5f, (prevZ + czz) * 0.5f,
                        h, sl * 0.62f, h * 0.85f, yaw, pitch, 0.0f,
                        jitterRgb(rampRgb(u, shadeRgb(baseRgb, 0.50f), baseRgb,
                                mixRgb(baseRgb, tipRgb, 0.55f), tipRgb), salt + k * 29, 0.05f),
                        Math.round(alpha * (1.0f - 0.28f * u)));
            }
            prevX = cxx;
            prevY = cyy;
            prevZ = czz;
        }
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
        // T374-PALETTE-LOCK : primaire, secondaire et accent suivent le même rôle dans chaque famille.
        // Les variantes changent uniquement la luminosité; aucune rotation HSV ne peut créer une teinte étrangère.
        int primaryTarget = (paletteMask == 0) ? fx.primaryRgb
                : paletteColor(paletteMask, fx.primaryRgb, palettePhase(frameTimeSeconds), 0);
        int secondaryTarget = (paletteMask == 0) ? fx.secondaryRgb
                : paletteColor(paletteMask, fx.secondaryRgb, palettePhase(frameTimeSeconds), 1);
        int highlightTarget = (paletteMask == 0) ? fx.highlightRgb
                : paletteColor(paletteMask, fx.highlightRgb, palettePhase(frameTimeSeconds), 2);
        int core = (paletteMask == 0) ? smoothColor(fx.id, primaryTarget) : primaryTarget;
        int glow = (paletteMask == 0) ? smoothColor(1000 + fx.id, secondaryTarget) : secondaryTarget;
        int highlight = (paletteMask == 0) ? smoothColor(2000 + fx.id, highlightTarget) : highlightTarget;
        float accentBrightness = 0.94f + (variant & 7) * 0.015f;
        int accent = shadeRgb(highlight, accentBrightness);
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

        emitVoxelCube(vc, mat, tmp, cx, cy, cz, hs * 1.24f, hs * 1.30f, hs * 1.24f, coreRgb, alpha);
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
            // T365-HOLOGRAM : les bras de la croix existent aussi en PROFONDEUR (avant/arrière),
            // donc l'éclat est une étoile à six branches en volume, plus un dessin sur un plan.
            crossArmPixel(vc, mat, tmp, cx, cy, cz + off, s * 0.92f, rgb, Math.round(a * 0.88f), salt + i, 4);
            crossArmPixel(vc, mat, tmp, cx, cy, cz - off, s * 0.92f, rgb, Math.round(a * 0.80f), salt + i, 5);
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
                    shadeRgb(rgb, 1.10f), Math.round(alpha * 0.68f));
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
                        halfSize * 0.33f, halfSize * 0.27f, halfSize * 0.33f, shadeRgb(coreRgb, 1.10f),
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
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, p, 0, grid, s, rgbA, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, -p, 0, grid, s, rgbA, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, 0, p, grid, s, rgbA, a2);
            billboardRaw(vc, mat, tmp, cx, cy, cz, rx, rz, 0, -p, grid, s, rgbA, a2);
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
                    grid * 0.14f, grid * 0.11f, grid * 0.14f, rgbAlt, Math.round(alpha * 0.75f));
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
                        -ang + (float) (Math.PI * 0.5), runeRgb, (r & 1) == 0 ? runeRgb : innerRgb, alpha);
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
                    lineHalf * 1.5f, lineHalf * 1.2f, lineHalf * 1.5f, runeRgb, Math.round(alpha * 0.9f));
        }

        // 6) Accents cardinaux : micro-pixels blancs pour le piqué pixel-art
        if (outerRadius >= 0.60f) {
            for (int k = 0; k < 4; k++) {
                float a = rotPhase * 0.35f + k * ((float) Math.PI * 0.5f);
                float px = quantize(cx + Mth.cos(a) * outerRadius, 0.03125f);
                float pz = quantize(cz + Mth.sin(a) * outerRadius, 0.03125f);
                float s = Mth.clamp(outerRadius * 0.028f, 0.010f, 0.030f);
                emitVoxelCube(vc, mat, tmp, px, cy + s * 1.1f, pz, s, s * 0.85f, s, runeRgb,
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
                pixelStep * 0.13f, pixelStep * 0.13f, pixelStep * 0.13f, lightRgb, Math.round(alpha * 0.85f));
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

    /** Luminance perçue (0..1) : sert à ordonner les arrêts d'un dégradé du sombre au clair. */
    public static float luminanceRgb(int rgb) {
        return (0.2126f * ((rgb >>> 16) & 0xFF) + 0.7152f * ((rgb >>> 8) & 0xFF)
                + 0.0722f * (rgb & 0xFF)) / 255.0f;
    }

    /**
     * T364-GRADIENTS : dégradé à quatre arrêts (creux → corps → lumière → pointe).
     *
     * <p>Avant, une vague ou une flamme avait trois teintes <b>plaquées</b> (une par étage de cube) :
     * ça se voyait tout de suite, l'œil lit des bandes franches. Ici la couleur est <b>interpolée en
     * continu</b> le long de la forme, du plus sombre au plus lumineux.</p>
     */
    public static int rampRgb(float u, int deep, int body, int high, int tip) {
        float k = Mth.clamp(u, 0.0f, 1.0f);
        if (k < 0.34f) return mixRgb(deep, body, k / 0.34f);
        if (k < 0.68f) return mixRgb(body, high, (k - 0.34f) / 0.34f);
        return mixRgb(high, tip, (k - 0.68f) / 0.32f);
    }

    /** Micro-variation déterministe de luminosité : nuance de la couleur d'origine, jamais dérive de teinte. */
    public static int jitterRgb(int rgb, int salt, float amount) {
        int hash = salt * 0x9E3779B1;
        float variation = (((hash >>> 3) & 0xFFFF) / 65535.0f - 0.5f) * 2.0f * amount;
        return shadeRgb(rgb, 1.0f + variation);
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
