package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.WeaponVisualEffectId;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
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
    private static final int MAX_VOXEL_TRAIL_PIXELS = 320;

    public static final int KIND_SWING_SLASH       = 0;
    public static final int KIND_IMPACT            = 1;
    public static final int KIND_BEAM_SIPHON       = 2;
    public static final int KIND_PROJECTILE_ATTACH = 3;

    private static final List<VfxInstance> ACTIVE = new ArrayList<>(144);
    private static final List<FloatingPixel> PIXEL_PARTICLES = new ArrayList<>(340);
    private static final List<PendingEcho> PENDING_ECHOES = new ArrayList<>(16);
    private static final Map<Integer, TrackedProjectile> TRACKED_PROJECTILES = new HashMap<>(32);

    private static final Map<Integer, Boolean> PLAYER_WAS_SWINGING = new HashMap<>(16);
    private static final Map<Integer, Integer> PLAYER_SWING_PARITY = new HashMap<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_DRAWING = new HashMap<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_CB_CHARGED = new HashMap<>(16);

    private static ClientLevel trackedLevel;
    private static long lastTickNanos = System.nanoTime();

    private static RenderType additiveType;
    private static RenderType translucentType;

    private record TrackedProjectile(WeaponVisualEffectId effect, int intensity, long startTick, long expiresTick) {}

    private static final class FloatingPixel {
        final boolean additive;
        double x, y, z;
        double vx, vy, vz;
        float gravity;
        float halfSize;
        int rgb;
        int age;
        int maxAge;

        FloatingPixel(boolean additive, double x, double y, double z,
                      double vx, double vy, double vz, float gravity,
                      float halfSize, int rgb, int maxAge) {
            this.additive = additive;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.gravity = gravity;
            this.halfSize = halfSize;
            this.rgb = rgb;
            this.maxAge = Math.max(4, maxAge);
        }
    }

    private static final class PendingEcho {
        int ticksLeft;
        WeaponVisualEffectId effect;
        int intensity;
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
        int age;

        VfxInstance(WeaponVisualEffectId effect, int kind, int intensity,
                    double x, double y, double z,
                    double tx, double ty, double tz,
                    float yawDeg, float pitchDeg, float tiltRad,
                    int maxAge, long seed) {
            this.effect = effect;
            this.kind = kind;
            this.intensity = Math.max(1, Math.min(12, intensity));
            this.x = x; this.y = y; this.z = z;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.tiltRad = tiltRad;
            this.maxAge = Math.max(4, maxAge);
            this.seed = seed;
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
                    8192,
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
                    8192,
                    false,
                    true,
                    state);
        }
        return translucentType;
    }

    public static void enqueueNetworkVfx(int vfxId, int kind, int intensity,
                                         float x, float y, float z,
                                         float tx, float ty, float tz) {
        WeaponVisualEffectId fx = WeaponVisualEffectId.byId(vfxId);
        if (fx == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // Attachement en direct d'un sillage de mini-pixels 3D à un projectile en vol !
        if (kind == KIND_PROJECTILE_ATTACH) {
            int projEntityId = Math.round(tx);
            if (projEntityId > 0) {
                long gt = level.getGameTime();
                TRACKED_PROJECTILES.put(projEntityId, new TrackedProjectile(fx, intensity, gt, gt + 140L));
            }
            spawnMiniPixelBurst(fx, x, y, z, 7, 0.032f, 0.14);
            return;
        }

        int duration = switch (fx) {
            case SERAPH_SKY_PILLAR, ABYSSAL_MAELSTROM, WARLORD_RALLY_SIGIL,
                 DRAGON_CHAOS_PRISM, BRIMSTONE_METEOR -> 22;
            case ASTRAL_STAR_LANCE, VOID_WARP_SPHERE, SEISMIC_TECTONIC,
                 CRIMSON_SIPHON, CHRONO_ETERNAL_HALO, ANNIHILATING_ARC -> 16;
            case CHAIN_VOLT_ARC -> 11;
            default -> 14;
        };

        float dx = tx - x;
        float dz = tz - z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        long seed = Double.doubleToLongBits(x * 31.0 + y * 17.0 + z * 13.0) ^ System.nanoTime();
        pushInstance(new VfxInstance(fx, kind, intensity, x, y, z, tx, ty, tz, yaw, 0.0f, 0.25f, duration, seed));

        // Éclaboussure de vrais mini-pixels 3D cubiques animés sous la physique !
        int burstCount = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 14 : 10;
        float cubeHalf = (fx.category == WeaponVisualEffectId.Category.BLOOD) ? 0.042f : 0.036f;
        spawnMiniPixelBurst(fx, x, y, z, burstCount, cubeHalf, 0.18);
    }

    public static void spawnMiniPixelBurst(WeaponVisualEffectId fx, double x, double y, double z,
                                           int count, float halfSize, double speed) {
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
            int rgb = ((i & 1) == 0) ? fx.primaryRgb : fx.secondaryRgb;
            float sz = halfSize * (0.75f + rand.nextFloat() * 0.5f);
            PIXEL_PARTICLES.add(new FloatingPixel(fx.additive, x, y, z, vx, vy, vz, grav, sz, rgb, 10 + rand.nextInt(10)));
        }
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
                        pe.yaw, pe.pitch, -pe.tiltRad, 9, System.nanoTime()));
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
                int rgb = (step == 0) ? tp.effect().primaryRgb : tp.effect().secondaryRgb;
                PIXEL_PARTICLES.add(new FloatingPixel(
                        tp.effect().additive, px, py, pz,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        0.0f, 0.032f, rgb, 9));
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
        spawnMiniPixelBurst(fx, mx, my, mz, 10, 0.030f, 0.12);
    }

    private static void onPlayerWeaponSwing(Player p) {
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

        pushInstance(new VfxInstance(
                fx, KIND_SWING_SLASH, primary.level(),
                ox, oy, oz, ox + look.x * 3.4, oy + look.y * 3.4, oz + look.z * 3.4,
                p.getYRot(), p.getXRot(), tilt, duration, System.nanoTime()));

        // Quelques mini-pixels 3D détachés le long du coup
        spawnMiniPixelBurst(fx, ox + look.x * 1.1, oy + look.y * 1.1, oz + look.z * 1.1, 5, 0.026f, 0.09);

        boolean hasEcho = fx == WeaponVisualEffectId.TWIN_ECHO_SLASH
                || (layers.size() > 1 && layers.get(1).effect() == WeaponVisualEffectId.TWIN_ECHO_SLASH);
        if (hasEcho && PENDING_ECHOES.size() < 16) {
            PendingEcho pe = new PendingEcho();
            pe.ticksLeft = 4;
            pe.effect = WeaponVisualEffectId.TWIN_ECHO_SLASH;
            pe.intensity = primary.level();
            pe.x = ox; pe.y = oy; pe.z = oz;
            pe.yaw = p.getYRot(); pe.pitch = p.getXRot(); pe.tiltRad = baseTilt;
            PENDING_ECHOES.add(pe);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty() && PIXEL_PARTICLES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        float subTick = mc.isPaused()
                ? 0.0f
                : Mth.clamp((System.nanoTime() - lastTickNanos) / 50_000_000.0f, 0.0f, 1.0f);

        PoseStack poseStack = event.getPoseStack();
        Matrix4f mat = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f tmp = new Vector3f();

        VertexConsumer addVc = null;
        VertexConsumer alphaVc = null;

        // 1) Rendu des mini-pixels 3D flottants (traînées de flèches, gouttes de sang, flammèches, étincelles)
        for (int i = 0, n = PIXEL_PARTICLES.size(); i < n; i++) {
            FloatingPixel fp = PIXEL_PARTICLES.get(i);
            float t = Mth.clamp((fp.age + subTick) / (float) fp.maxAge, 0.0f, 1.0f);
            if (t >= 1.0f) continue;

            float rx = (float) (fp.x + fp.vx * subTick - camPos.x);
            float ry = (float) (fp.y + fp.vy * subTick - camPos.y);
            float rz = (float) (fp.z + fp.vz * subTick - camPos.z);
            float sz = fp.halfSize * (1.0f - t * 0.45f);
            int alpha = Math.round(235.0f * (1.0f - t));

            if (fp.additive) {
                if (addVc == null) addVc = buffers.getBuffer(getAdditiveRenderType());
                emitVoxelCube(addVc, mat, tmp, rx, ry, rz, sz, sz, sz, fp.rgb, alpha);
            } else {
                if (alphaVc == null) alphaVc = buffers.getBuffer(getTranslucentRenderType());
                emitVoxelCube(alphaVc, mat, tmp, rx, ry, rz, sz, sz, sz, fp.rgb, alpha);
            }
        }

        // 2) Rendu des 30 animations d'armes en grappes de mini-cubes 3D blocky
        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            VfxInstance inst = ACTIVE.get(i);
            float t = Mth.clamp((inst.age + subTick) / (float) inst.maxAge, 0.0f, 1.0f);
            if (t >= 1.0f) continue;

            VertexConsumer vc;
            if (inst.effect.additive) {
                if (addVc == null) addVc = buffers.getBuffer(getAdditiveRenderType());
                vc = addVc;
            } else {
                if (alphaVc == null) alphaVc = buffers.getBuffer(getTranslucentRenderType());
                vc = alphaVc;
            }

            if (inst.kind == KIND_SWING_SLASH) {
                renderVoxelSwingSlash(vc, mat, tmp, camPos, inst, t);
            } else if (inst.kind == KIND_BEAM_SIPHON) {
                renderVoxelBeamOrSiphon(vc, mat, tmp, camPos, inst, t);
            } else {
                renderVoxelImpact(vc, mat, tmp, camPos, inst, t);
            }
        }

        if (alphaVc != null) {
            buffers.endBatch(getTranslucentRenderType());
        }
        if (addVc != null) {
            buffers.endBatch(getAdditiveRenderType());
        }
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

        switch (inst.effect) {
            case CRIMSON_SIPHON -> {
                // #10 : 3 courants courbes de mini-cubes de sang aspirés vers le porteur !
                for (int s = 0; s < 3; s++) {
                    float phase = s * 2.094f + inst.age * 0.32f;
                    emitVoxelCurvedSiphon(vc, mat, tmp, x0, y0, z0, x1, y1, z1, phase, t,
                            inst.effect.primaryRgb, inst.effect.secondaryRgb, alpha);
                }
            }
            case CHAIN_VOLT_ARC -> {
                // #16 : Éclair en escalier de mini-cubes 3D !
                emitVoxelLightning(vc, mat, tmp, x0, y0, z0, x1, y1, z1, 9, 0.25f,
                        inst.seed + inst.age / 2, 0xFFFFFF, inst.effect.primaryRgb, alpha, 0.036f);
            }
            default -> {
                // #25 Lance Astrale / Sniper : Rayon crénelé en mini-cubes stellaires + croix pixel 3D à l'impact
                emitSteppedVoxelLine(vc, mat, tmp, x0, y0, z0, x1, y1, z1,
                        0.040f * (1.0f - t * 0.45f), 0xFFFFFF, inst.effect.primaryRgb, alpha);
                emitPixelCross3D(vc, mat, tmp, x1, y1, z1, 0.0625f, 3, 0xFFFFFF, inst.effect.secondaryRgb, alpha);
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
        int pRgb = inst.effect.primaryRgb;
        int sRgb = inst.effect.secondaryRgb;

        switch (inst.effect) {
            case GOLDEN_CRIT_STAR -> {
                // #06 Étoile Critique Pixel-Art 3D (+ en cubes + anneau carré crénelé)
                int arms = (inst.intensity >= 3) ? 4 : 3;
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, arms, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy, cz, 0.30f + 1.35f * t, 0.040f, pRgb, sRgb, fadeA, 16);
            }
            case FOCUSED_BULLSEYE -> {
                // #07 Mire carrée pixelisée qui se verrouille sur la cible
                float half = (t < 0.4f) ? (1.15f - 1.8f * t) : (0.43f + (t - 0.4f) * 1.6f);
                emitBlockySquareFrame(vc, mat, tmp, cx, cy, cz, half, 0.042f, sRgb, pRgb, fadeA);
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.055f, 2, 0xFFFFFF, sRgb, fadeA);
            }
            case OVERCLOCK_CYBER_X -> {
                // #08 Immense X en escalier de mini-cubes néon cyan & magenta !
                float span = 0.85f + 0.55f * t;
                emitSteppedVoxelLine(vc, mat, tmp, cx - span, cy - span, cz, cx + span, cy + span, cz, 0.048f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx - span, cy + span, cz, cx + span, cy - span, cz, 0.048f, 0xFFFFFF, sRgb, fadeA);
                emitBlockySquareFrame(vc, mat, tmp, cx, cy, cz, 0.35f + 1.45f * t, 0.040f, pRgb, sRgb, fadeA);
            }
            case GUILLOTINE_CLEAVE -> {
                // #05 Colonne de cubes d'exécution tranchant de haut en bas
                float topY = cy + 2.0f * (1.0f - t * 0.35f);
                emitSteppedVoxelLine(vc, mat, tmp, cx, topY, cz, cx, cy - 0.8f, cz, 0.058f, sRgb, pRgb, fadeA);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy - 0.65f, cz, 0.3f + 1.3f * t, 0.045f, pRgb, sRgb, fadeA, 12);
            }
            case DRIPPING_BLOOD_EDGE, CARNAGE_BERSERK -> {
                // #09 / #12 Arcs de gros pixels cubiques de sang retombant en fontaine
                int count = (inst.effect == WeaponVisualEffectId.CARNAGE_BERSERK) ? 12 : 8;
                for (int i = 0; i < count; i++) {
                    float ang = (float) (i * (Math.PI * 2.0 / count) + (inst.seed & 7));
                    float rad = (0.20f + 1.35f * t) * (0.75f + 0.25f * (i & 1));
                    float dy = 0.55f * t - 1.40f * t * t;
                    float px = quantize(cx + Mth.cos(ang) * rad, 0.0625f);
                    float py = quantize(cy + dy, 0.0625f);
                    float pz = quantize(cz + Mth.sin(ang) * rad, 0.0625f);
                    emitVoxelCube(vc, mat, tmp, px, py, pz, 0.052f, 0.052f, 0.052f, (i & 1) == 0 ? pRgb : sRgb, fadeA);
                }
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy - 0.3f, cz, 0.35f + 1.5f * t, 0.044f, pRgb, sRgb, fadeA, 16);
            }
            case VITAL_TRANSFUSION -> {
                // #11 Croix médicales 3D rubis-or montantes en spirale voxel
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.6f, cz, 0.65f, 1.8f, t * 4.0f, pRgb, sRgb, fadeA);
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.6f, cz, 0.65f, 1.8f, t * 4.0f + (float) Math.PI, sRgb, pRgb, fadeA);
            }
            case BRIMSTONE_METEOR -> {
                // #15 Bloc-météore cubique plongeant du ciel + anneau crénelé de magma
                if (t < 0.48f) {
                    float mt = t / 0.48f;
                    float mx = quantize(cx + 3.2f * (1.0f - mt), 0.0625f);
                    float my = quantize(cy + 11.0f * (1.0f - mt), 0.0625f);
                    float mz = quantize(cz + 2.0f * (1.0f - mt), 0.0625f);
                    emitVoxelCube(vc, mat, tmp, mx, my, mz, 0.22f, 0.22f, 0.22f, 0xFFFFFF, 245);
                    emitVoxelCube(vc, mat, tmp, mx, my, mz, 0.32f, 0.32f, 0.32f, pRgb, 190);
                    emitSteppedVoxelLine(vc, mat, tmp, mx + 1.0f, my + 3.2f, mz + 0.6f, mx, my, mz, 0.075f, sRgb, pRgb, 220);
                }
                float st = Math.max(0.0f, (t - 0.2f) / 0.8f);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy - 0.35f, cz, 0.4f + 3.1f * st, 0.075f, sRgb, pRgb, fadeA, 24);
            }
            case CHAIN_VOLT_ARC -> {
                // #16 Éclair céleste en escalier de cubes 3D + 3 arcs au sol
                emitVoxelLightning(vc, mat, tmp, cx + 0.3f, cy + 8.0f, cz - 0.3f, cx, cy, cz, 11, 0.35f,
                        inst.seed + inst.age / 2, 0xFFFFFF, pRgb, fadeA, 0.045f);
                for (int b = 0; b < 3; b++) {
                    float a = b * 2.094f + (inst.seed & 3);
                    emitVoxelLightning(vc, mat, tmp, cx, cy, cz,
                            cx + Mth.cos(a) * 1.8f, cy - 0.3f, cz + Mth.sin(a) * 1.8f,
                            5, 0.18f, inst.seed + b * 29L + inst.age, sRgb, pRgb, fadeA, 0.035f);
                }
            }
            case HYDRO_TIDAL_SURGE -> {
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.5f, cz, 0.55f + 0.8f * t, 1.6f, t * 4.8f, pRgb, sRgb, fadeA);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy - 0.3f, cz, 0.3f + 2.0f * t, 0.048f, sRgb, pRgb, fadeA, 18);
            }
            case ABYSSAL_MAELSTROM -> {
                // #18 Vortex océanique de mini-cubes 3D tournoyants à 4 bras
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
            }
            case PERMAFROST_RIME -> {
                // #19 Piliers de cristaux cubiques de glace émergeant du sol
                for (int k = 0; k < 6; k++) {
                    float a = k * ((float) Math.PI / 3.0f);
                    float r = 0.40f + 1.45f * t;
                    float px = quantize(cx + Mth.cos(a) * r, 0.0625f);
                    float pz = quantize(cz + Mth.sin(a) * r, 0.0625f);
                    float h = 0.38f * (1.0f - t * 0.5f);
                    emitVoxelCube(vc, mat, tmp, px, cy - 0.3f + h * 0.5f, pz, 0.0625f, h, 0.0625f, (k & 1) == 0 ? sRgb : pRgb, fadeA);
                }
            }
            case VENOM_BRIAR_FANG -> {
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.5f, cz, 0.70f, 1.5f, t * 3.0f, pRgb, sRgb, fadeA);
                emitVoxelHelix(vc, mat, tmp, cx, cy - 0.5f, cz, 0.70f, 1.5f, -t * 3.0f + 1.4f, sRgb, pRgb, fadeA);
            }
            case SEISMIC_TECTONIC -> {
                // #21 Failles de cubes rocheux soulevés sur le sol
                float reach = 0.5f + 2.9f * Mth.sqrt(t);
                for (int f = 0; f < 6; f++) {
                    float a = f * ((float) Math.PI / 3.0f) + 0.2f;
                    emitSteppedVoxelLine(vc, mat, tmp, cx, cy - 0.42f, cz,
                            cx + Mth.cos(a) * reach, cy - 0.42f, cz + Mth.sin(a) * reach,
                            0.058f, sRgb, pRgb, fadeA);
                }
                emitBlockySquareFrame(vc, mat, tmp, cx, cy - 0.40f, cz, reach * 0.72f, 0.055f, pRgb, sRgb, fadeA);
            }
            case ZEPHYR_WIND_LANCE -> {
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy, cz, 0.35f + 1.9f * t, 0.042f, pRgb, sRgb, fadeA, 16);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy + 0.45f * t, cz, 0.25f + 1.25f * t, 0.036f, 0xFFFFFF, pRgb, fadeA, 12);
            }
            case SERAPH_SKY_PILLAR -> {
                // #23 Colonne carrée blocky de lumière dorée tombant du ciel + cadres sacrés pixelisés !
                float hw = 0.22f * Mth.sin(Math.min(1.0f, t * 1.3f) * (float) Math.PI);
                if (hw > 0.02f) {
                    emitVoxelCube(vc, mat, tmp, cx, cy + 5.0f, cz, hw, 5.6f, hw, 0xFFFFFF, fadeA);
                    emitVoxelCube(vc, mat, tmp, cx, cy + 5.0f, cz, hw * 1.75f, 5.6f, hw * 1.75f, sRgb, Math.round(fadeA * 0.45f));
                }
                emitBlockySquareFrame(vc, mat, tmp, cx, cy - 0.35f + 2.4f * t, cz, 0.55f + 1.4f * t, 0.050f, 0xFFFFFF, sRgb, fadeA);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy + 0.25f, cz, 0.45f + 2.2f * t, 0.048f, pRgb, sRgb, fadeA, 20);
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
            case VOID_WARP_SPHERE -> {
                float r = 0.35f + 1.45f * Mth.sin(t * (float) Math.PI);
                emitBlockySquareFrame(vc, mat, tmp, cx, cy, cz, r, 0.048f, pRgb, sRgb, fadeA);
                emitPixelCross3D(vc, mat, tmp, cx, cy, cz, 0.0625f, 4, sRgb, pRgb, fadeA);
            }
            case DRAGON_CHAOS_PRISM -> {
                int c1 = Mth.hsvToRgb((t * 1.5f) % 1.0f, 0.8f, 1.0f);
                int c2 = Mth.hsvToRgb((t * 1.5f + 0.35f) % 1.0f, 0.85f, 1.0f);
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy - 0.35f, cz, 0.45f + 2.4f * t, 0.058f, c1, c2, fadeA, 20);
                emitPixelCross3D(vc, mat, tmp, cx, cy + 0.2f, cz, 0.0625f, 4, 0xFFFFFF, c1, fadeA);
            }
            case CHRONO_ETERNAL_HALO -> {
                // #28 Cadran carré pixelisé + aiguilles en mini-cubes 3D
                float r = 0.85f;
                emitBlockySquareFrame(vc, mat, tmp, cx, cy + 0.7f, cz, r, 0.038f, pRgb, sRgb, fadeA);
                float aFast = t * 8.5f;
                float aSlow = t * 2.2f;
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy + 0.7f, cz,
                        cx + Mth.cos(aFast) * r * 0.85f, cy + 0.7f, cz + Mth.sin(aFast) * r * 0.85f,
                        0.032f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy + 0.7f, cz,
                        cx + Mth.cos(aSlow) * r * 0.55f, cy + 0.7f, cz + Mth.sin(aSlow) * r * 0.55f,
                        0.038f, sRgb, pRgb, fadeA);
            }
            case WARLORD_RALLY_SIGIL -> {
                // #29 Sceau carré double en mini-cubes dorés au sol
                float r = 1.95f * Math.min(1.0f, t * 2.5f);
                emitBlockySquareFrame(vc, mat, tmp, cx, cy - 0.40f, cz, r, 0.052f, pRgb, sRgb, fadeA);
                emitBlockySquareFrame(vc, mat, tmp, cx, cy - 0.40f, cz, r * 0.58f, 0.042f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx - r, cy - 0.40f, cz, cx + r, cy - 0.40f, cz, 0.036f, 0xFFFFFF, pRgb, fadeA);
                emitSteppedVoxelLine(vc, mat, tmp, cx, cy - 0.40f, cz - r, cx, cy - 0.40f, cz + r, 0.036f, 0xFFFFFF, pRgb, fadeA);
            }
            default -> {
                float maxR = (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) ? 3.2f : 1.65f;
                emitSteppedVoxelRing(vc, mat, tmp, cx, cy, cz, 0.25f + maxR * t, 0.048f, pRgb, sRgb, fadeA, 18);
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

    /** Croix pixel-art 3D composée d'un cube central et de bras crénelés sur X/Y/Z. */
    public static void emitPixelCross3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz,
                                        float stepSize, int armPixels,
                                        int coreRgb, int tipRgb, int alpha) {
        if (alpha <= 6) return;
        float hs = stepSize * 0.48f;
        emitVoxelCube(vc, mat, tmp, cx, cy, cz, hs * 1.15f, hs * 1.15f, hs * 1.15f, coreRgb, alpha);
        for (int i = 1; i <= armPixels; i++) {
            float off = i * stepSize;
            float s = hs * (1.0f - (i - 1) * 0.16f);
            int rgb = (i == 1) ? coreRgb : tipRgb;
            int a = Math.round(alpha * (1.0f - (i - 1) * 0.18f));
            emitVoxelCube(vc, mat, tmp, cx + off, cy, cz, s, s, s, rgb, a);
            emitVoxelCube(vc, mat, tmp, cx - off, cy, cz, s, s, s, rgb, a);
            emitVoxelCube(vc, mat, tmp, cx, cy + off, cz, s, s, s, rgb, a);
            emitVoxelCube(vc, mat, tmp, cx, cy - off, cz, s, s, s, rgb, a);
            if (i <= 2) {
                emitVoxelCube(vc, mat, tmp, cx, cy, cz + off, s, s, s, rgb, a);
                emitVoxelCube(vc, mat, tmp, cx, cy, cz - off, s, s, s, rgb, a);
            }
        }
    }

    /** Ligne 3D crénelée en mini-cubes alignés sur une grille de pixels (style Bresenham 3D). */
    public static void emitSteppedVoxelLine(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1,
                                            float halfSize, int coreRgb, int outerRgb, int alpha) {
        if (alpha <= 6 || halfSize <= 0.002f) return;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float step = Math.max(halfSize * 2.1f, 0.0625f);
        int count = Math.min(48, Math.max(2, Math.round(dist / step)));
        float grid = halfSize * 1.8f;

        for (int i = 0; i <= count; i++) {
            float u = (float) i / count;
            float px = quantize(Mth.lerp(u, x0, x1), grid);
            float py = quantize(Mth.lerp(u, y0, y1), grid);
            float pz = quantize(Mth.lerp(u, z0, z1), grid);
            int rgb = ((i & 1) == 0) ? coreRgb : outerRgb;
            emitVoxelCube(vc, mat, tmp, px, py, pz, halfSize, halfSize, halfSize, rgb, alpha);
        }
    }

    /** Anneau de mini-cubes 3D quantifiés sur la grille pixel. */
    private static void emitSteppedVoxelRing(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                             float cx, float cy, float cz,
                                             float radius, float halfSize,
                                             int rgbA, int rgbB, int alpha, int cubeCount) {
        if (alpha <= 6 || radius <= 0.04f) return;
        for (int i = 0; i < cubeCount; i++) {
            float a = (float) (i * Math.PI * 2.0 / cubeCount);
            float px = quantize(cx + Mth.cos(a) * radius, 0.0625f);
            float pz = quantize(cz + Mth.sin(a) * radius, 0.0625f);
            emitVoxelCube(vc, mat, tmp, px, cy, pz, halfSize, halfSize, halfSize, (i & 1) == 0 ? rgbA : rgbB, alpha);
        }
    }

    /** Cadre carré blocky composé de mini-cubes 3D le long des 4 côtés. */
    private static void emitBlockySquareFrame(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              float cx, float cy, float cz,
                                              float halfSpan, float cubeHalf,
                                              int cornerRgb, int edgeRgb, int alpha) {
        if (alpha <= 6 || halfSpan <= 0.04f) return;
        emitSteppedVoxelLine(vc, mat, tmp, cx - halfSpan, cy, cz - halfSpan, cx + halfSpan, cy, cz - halfSpan, cubeHalf, cornerRgb, edgeRgb, alpha);
        emitSteppedVoxelLine(vc, mat, tmp, cx + halfSpan, cy, cz - halfSpan, cx + halfSpan, cy, cz + halfSpan, cubeHalf, cornerRgb, edgeRgb, alpha);
        emitSteppedVoxelLine(vc, mat, tmp, cx + halfSpan, cy, cz + halfSpan, cx - halfSpan, cy, cz + halfSpan, cubeHalf, cornerRgb, edgeRgb, alpha);
        emitSteppedVoxelLine(vc, mat, tmp, cx - halfSpan, cy, cz + halfSpan, cx - halfSpan, cy, cz - halfSpan, cubeHalf, cornerRgb, edgeRgb, alpha);
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

        int cubes = 14;
        for (int i = 0; i <= cubes; i++) {
            float u = (float) i / cubes;
            float inv = 1.0f - u;
            float bx = quantize(inv * inv * x0 + 2.0f * inv * u * mx + u * u * x1, 0.0625f);
            float by = quantize(inv * inv * y0 + 2.0f * inv * u * my + u * u * y1, 0.0625f);
            float bz = quantize(inv * inv * z0 + 2.0f * inv * u * mz + u * u * z1, 0.0625f);
            float pulse = 1.0f - Math.min(1.0f, Math.abs(u - progress) * 2.8f);
            float hs = 0.038f + 0.024f * pulse;
            emitVoxelCube(vc, mat, tmp, bx, by, bz, hs, hs, hs, pulse > 0.35f ? brightRgb : darkRgb, alpha);
        }
    }

    private static void emitVoxelHelix(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float baseY, float cz,
                                       float radius, float height, float phase,
                                       int rgbA, int rgbB, int alpha) {
        if (alpha <= 6) return;
        int cubes = 14;
        for (int i = 0; i <= cubes; i++) {
            float u = (float) i / cubes;
            float a = phase + u * (float) Math.PI * 2.4f;
            float nx = quantize(cx + Mth.cos(a) * radius * (1.0f - 0.2f * u), 0.0625f);
            float ny = quantize(baseY + u * height, 0.0625f);
            float nz = quantize(cz + Mth.sin(a) * radius * (1.0f - 0.2f * u), 0.0625f);
            float hs = 0.042f * (1.0f - u * 0.35f);
            emitVoxelCube(vc, mat, tmp, nx, ny, nz, hs, hs, hs, (i & 1) == 0 ? rgbA : rgbB, alpha);
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
        mat.transformPosition(x, y, z, tmp);
        int ca = Mth.clamp(a, 0, 255);
        vc.addVertex(tmp.x(), tmp.y(), tmp.z())
                .setColor((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, ca);
    }
}
