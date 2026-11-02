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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
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
 * T342-WEAPON-VFX-30 : Moteur client 3D procédural pour les 30 effets visuels d'armes en monde
 * (rubans d'entaille courbés, double écho retardé, couperet guillotine, éclats critiques en étoile
 * et cyber-X, gouttes & siphons de sang 3D, chaînes d'éclairs ramifiés, colonne céleste du Séraphin,
 * météore soufré, maelström abyssal, failles sismiques, lance astrale longue portée, sceau runique...).
 *
 * Rendu groupé en 2 appels GPU maximum par image (1 passe additive luminescente + 1 passe alpha
 * saturée pour le sang sombre, l'ombre et la terre) sans aucune texture externe requise.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class WeaponWorldVfxManager {

    private static final int MAX_ACTIVE_INSTANCES = 110;
    private static final List<VfxInstance> ACTIVE = new ArrayList<>(128);
    private static final List<PendingEcho> PENDING_ECHOES = new ArrayList<>(16);
    private static final Map<Integer, Boolean> PLAYER_WAS_SWINGING = new HashMap<>(16);
    private static final Map<Integer, Integer> PLAYER_SWING_PARITY = new HashMap<>(16);

    private static ClientLevel trackedLevel;
    private static long lastTickNanos = System.nanoTime();

    private static RenderType additiveType;
    private static RenderType translucentType;

    // Modes géométriques 3D
    public static final int KIND_SWING_SLASH = 0;
    public static final int KIND_IMPACT      = 1;
    public static final int KIND_BEAM_SIPHON = 2;

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
            this.x = x;
            this.y = y;
            this.z = z;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.tiltRad = tiltRad;
            this.maxAge = Math.max(4, maxAge);
            this.seed = seed;
        }
    }

    private WeaponWorldVfxManager() {}

    /** Temps continu haute fréquence en secondes (figé proprement si le jeu solo est en pause). */
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
                    "dlb_weapon_vfx_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    4096,
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
                    "dlb_weapon_vfx_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    4096,
                    false,
                    true,
                    state);
        }
        return translucentType;
    }

    /** Réception d'un événement visuel déclenché par le serveur (impact, critique, siphon, tir longue portée...). */
    public static void enqueueNetworkVfx(int vfxId, int kind, int intensity,
                                         float x, float y, float z,
                                         float tx, float ty, float tz) {
        WeaponVisualEffectId fx = WeaponVisualEffectId.byId(vfxId);
        if (fx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int duration = switch (fx) {
            case SERAPH_SKY_PILLAR, ABYSSAL_MAELSTROM, WARLORD_RALLY_SIGIL,
                 DRAGON_CHAOS_PRISM, BRIMSTONE_METEOR -> 22;
            case ASTRAL_STAR_LANCE, VOID_WARP_SPHERE, SEISMIC_TECTONIC,
                 CRIMSON_SIPHON, CHRONO_ETERNAL_HALO, ANNIHILATING_ARC -> 16;
            case CHAIN_VOLT_ARC -> 10;
            default -> 13;
        };

        float dx = tx - x;
        float dz = tz - z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        long seed = Double.doubleToLongBits(x * 31.0 + y * 17.0 + z * 13.0) ^ System.nanoTime();
        pushInstance(new VfxInstance(fx, kind, intensity, x, y, z, tx, ty, tz, yaw, 0.0f, 0.25f, duration, seed));

        // Quelques particules vanilla subtiles d'accompagnement
        spawnAccentParticles(mc.level, fx, x, y, z, tx, ty, tz);
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
            PENDING_ECHOES.clear();
            PLAYER_WAS_SWINGING.clear();
        }
        DeepEnchant.rememberClientAccess(level.registryAccess());

        // Avance et nettoie les instances 3D actives
        for (Iterator<VfxInstance> it = ACTIVE.iterator(); it.hasNext(); ) {
            VfxInstance inst = it.next();
            inst.age++;
            if (inst.age >= inst.maxAge) {
                it.remove();
            }
        }

        // Déclenche les entailles d'écho différées (#04 TWIN_ECHO_SLASH)
        for (Iterator<PendingEcho> it = PENDING_ECHOES.iterator(); it.hasNext(); ) {
            PendingEcho pe = it.next();
            pe.ticksLeft--;
            if (pe.ticksLeft <= 0) {
                pushInstance(new VfxInstance(
                        pe.effect, KIND_SWING_SLASH, pe.intensity,
                        pe.x, pe.y, pe.z, pe.x, pe.y, pe.z,
                        pe.yaw, pe.pitch, -pe.tiltRad, 8, System.nanoTime()));
                it.remove();
            }
        }

        // Détecte instantanément les coups de mêlée des joueurs visibles (0 latence réseau !)
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p != viewer && p.distanceToSqr(viewer) > 32.0 * 32.0) continue;

            boolean swinging = p.swinging && p.swingTime <= 2;
            boolean prev = PLAYER_WAS_SWINGING.getOrDefault(p.getId(), false);
            PLAYER_WAS_SWINGING.put(p.getId(), p.swinging);

            if (swinging && !prev) {
                onPlayerWeaponSwing(p);
            }
        }
    }

    private static void onPlayerWeaponSwing(Player p) {
        InteractionHand hand = p.swingingArm == null ? InteractionHand.MAIN_HAND : p.swingingArm;
        ItemStack stack = p.getItemInHand(hand);
        if (!isMeleeSlashWeapon(stack)) return;

        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
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
                ox, oy, oz, ox + look.x * 3.2, oy + look.y * 3.2, oz + look.z * 3.2,
                p.getYRot(), p.getXRot(), tilt, duration, System.nanoTime()));

        // Si #04 TWIN_ECHO_SLASH est présent (en primaire ou secondaire), programme la 2e lame fantôme !
        boolean hasEcho = fx == WeaponVisualEffectId.TWIN_ECHO_SLASH
                || (layers.size() > 1 && layers.get(1).effect() == WeaponVisualEffectId.TWIN_ECHO_SLASH);
        if (hasEcho && PENDING_ECHOES.size() < 16) {
            PendingEcho pe = new PendingEcho();
            pe.ticksLeft = 4;
            pe.effect = WeaponVisualEffectId.TWIN_ECHO_SLASH;
            pe.intensity = primary.level();
            pe.x = ox;
            pe.y = oy;
            pe.z = oz;
            pe.yaw = p.getYRot();
            pe.pitch = p.getXRot();
            pe.tiltRad = baseTilt;
            PENDING_ECHOES.add(pe);
        }
    }

    private static boolean isMeleeSlashWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem) {
            return true;
        }
        return item instanceof TieredItem && WeaponVisualEffectId.isWeaponItem(stack);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty()) return;

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

        boolean usedAdd = false;
        boolean usedAlpha = false;
        VertexConsumer addVc = null;
        VertexConsumer alphaVc = null;

        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            VfxInstance inst = ACTIVE.get(i);
            float t = Mth.clamp((inst.age + subTick) / (float) inst.maxAge, 0.0f, 1.0f);
            if (t >= 1.0f) continue;

            VertexConsumer vc;
            if (inst.effect.additive) {
                if (addVc == null) addVc = buffers.getBuffer(getAdditiveRenderType());
                vc = addVc;
                usedAdd = true;
            } else {
                if (alphaVc == null) alphaVc = buffers.getBuffer(getTranslucentRenderType());
                vc = alphaVc;
                usedAlpha = true;
            }

            if (inst.kind == KIND_SWING_SLASH) {
                renderSwingSlash(vc, mat, tmp, camPos, inst, t);
            } else if (inst.kind == KIND_BEAM_SIPHON) {
                renderBeamOrSiphon(vc, mat, tmp, camPos, inst, t);
            } else {
                renderImpactEffect(vc, mat, tmp, camPos, inst, t);
            }
        }

        if (usedAlpha) {
            buffers.endBatch(getTranslucentRenderType());
        }
        if (usedAdd) {
            buffers.endBatch(getAdditiveRenderType());
        }
    }

    // ==================== 1. RUBANS D'ENTAILLE 3D EN FRAPPE ====================

    private static void renderSwingSlash(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         Vec3 cam, VfxInstance inst, float t) {
        float rx = (float) (inst.x - cam.x);
        float ry = (float) (inst.y - cam.y);
        float rz = (float) (inst.z - cam.z);

        // Allonge Fantôme (#03) : lance spectrale projetée vers l'avant
        if (inst.effect == WeaponVisualEffectId.SPECTRAL_REACH) {
            float tx = (float) (inst.tx - cam.x);
            float ty = (float) (inst.ty - cam.y);
            float tz = (float) (inst.tz - cam.z);
            float reachProg = Mth.sin(t * (float) Math.PI);
            float ex = Mth.lerp(Math.min(1.0f, t * 2.2f), rx, tx);
            float ey = Mth.lerp(Math.min(1.0f, t * 2.2f), ry, ty);
            float ez = Mth.lerp(Math.min(1.0f, t * 2.2f), rz, tz);
            emitBoltRibbon(vc, mat, tmp, rx, ry, rz, ex, ey, ez, 0.11f * reachProg,
                    inst.effect.primaryRgb, inst.effect.secondaryRgb, Math.round(215 * (1.0f - t)));
            return;
        }

        // Bain de Sang / Berserk (#12) : tourbillon 360° écarlate
        float sweepDeg = switch (inst.effect) {
            case CARNAGE_BERSERK -> 330.0f;
            case ANNIHILATING_ARC -> 175.0f;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT -> 150.0f;
            case GUILLOTINE_CLEAVE -> 125.0f;
            default -> 128.0f;
        };
        float radius = switch (inst.effect) {
            case ANNIHILATING_ARC -> 2.15f + 0.95f * t;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT -> 1.65f + 0.55f * t;
            case CARNAGE_BERSERK -> 1.75f + 0.35f * t;
            case SUPREME_PRISM_EDGE -> 1.55f + 0.25f * t;
            default -> 1.38f + 0.20f * t;
        };
        float width = switch (inst.effect) {
            case RAZOR_GLEAM -> 0.16f;
            case SUPREME_PRISM_EDGE -> 0.24f;
            case ANNIHILATING_ARC -> 0.44f;
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, CARNAGE_BERSERK -> 0.34f;
            default -> 0.23f;
        };

        int alpha = Math.round(230.0f * (1.0f - t * t));
        emitSlashCrescent(vc, mat, tmp, rx, ry, rz, inst.yawDeg, inst.pitchDeg, inst.tiltRad,
                radius, width, sweepDeg, t, inst.effect.primaryRgb, inst.effect.secondaryRgb, alpha);

        // #02 Prisme Diamant Suprême : second croissant croisé iridescent !
        if (inst.effect == WeaponVisualEffectId.SUPREME_PRISM_EDGE) {
            emitSlashCrescent(vc, mat, tmp, rx, ry, rz, inst.yawDeg, inst.pitchDeg, -inst.tiltRad * 0.85f,
                    radius * 0.92f, width * 0.65f, sweepDeg * 0.88f, t,
                    inst.effect.secondaryRgb, 0xFFFFFF, Math.round(alpha * 0.8f));
        }
    }

    private static void emitSlashCrescent(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float cx, float cy, float cz,
                                          float yawDeg, float pitchDeg, float tiltRad,
                                          float radius, float maxWidth, float sweepDeg, float progress,
                                          int innerRgb, int outerRgb, int peakAlpha) {
        if (peakAlpha <= 4) return;
        float yawRad = -yawDeg * Mth.DEG_TO_RAD;
        float pitchRad = pitchDeg * Mth.DEG_TO_RAD;

        // Repère 3D orienté selon le regard du joueur + inclinaison de coupe (tiltRad)
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

        int segs = 16;
        float halfArc = (sweepDeg * 0.5f) * Mth.DEG_TO_RAD;
        float headShift = (progress - 0.35f) * 0.45f;

        float prevOx = 0, prevOy = 0, prevOz = 0;
        float prevIx = 0, prevIy = 0, prevIz = 0;
        int prevA = 0;

        for (int i = 0; i <= segs; i++) {
            float u = (float) i / segs; // [0..1]
            float ang = (u - 0.5f + headShift) * 2.0f * halfArc;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);

            float dirX = fx * ca + sx * sa;
            float dirY = fy * ca + sy * sa;
            float dirZ = fz * ca + sz * sa;

            float taper = Mth.sin(u * (float) Math.PI);
            float rOut = radius;
            float rIn = Math.max(0.15f, radius - maxWidth * taper);

            float ox = cx + dirX * rOut;
            float oy = cy + dirY * rOut;
            float oz = cz + dirZ * rOut;
            float ix = cx + dirX * rIn;
            float iy = cy + dirY * rIn;
            float iz = cz + dirZ * rIn;
            int a = Math.round(peakAlpha * taper);

            if (i > 0) {
                putQuad(vc, mat, tmp,
                        prevIx, prevIy, prevIz, innerRgb, Math.round(prevA * 0.55f),
                        prevOx, prevOy, prevOz, outerRgb, prevA,
                        ox, oy, oz, outerRgb, a,
                        ix, iy, iz, innerRgb, Math.round(a * 0.55f));
            }
            prevOx = ox; prevOy = oy; prevOz = oz;
            prevIx = ix; prevIy = iy; prevIz = iz;
            prevA = a;
        }
    }

    // ==================== 2. RAYONS LONGUE PORTÉE, ÉCLAIRS & SIPHONS DE SANG ====================

    private static void renderBeamOrSiphon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           Vec3 cam, VfxInstance inst, float t) {
        float x0 = (float) (inst.x - cam.x);
        float y0 = (float) (inst.y - cam.y);
        float z0 = (float) (inst.z - cam.z);
        float x1 = (float) (inst.tx - cam.x);
        float y1 = (float) (inst.ty - cam.y);
        float z1 = (float) (inst.tz - cam.z);
        int alpha = Math.round(235.0f * (1.0f - t));

        switch (inst.effect) {
            case CRIMSON_SIPHON -> {
                // #10 : 3 filaments courbes de sang vivant revenant de la cible (x0) vers le porteur (x1)
                for (int s = 0; s < 3; s++) {
                    float phase = s * 2.094f + inst.age * 0.35f;
                    emitCurvedTendril(vc, mat, tmp, x0, y0, z0, x1, y1, z1, phase, t,
                            inst.effect.primaryRgb, inst.effect.secondaryRgb, alpha);
                }
            }
            case CHAIN_VOLT_ARC -> {
                // #16 : Éclair 3D brisé reliant deux cibles
                emitJaggedLightning(vc, mat, tmp, x0, y0, z0, x1, y1, z1, 8, 0.24f,
                        inst.seed + inst.age / 2, inst.effect.secondaryRgb, inst.effect.primaryRgb, alpha);
            }
            default -> {
                // #25 Percée Astrale / Sniper / Allonge : Lance stellaire longue portée + étoiles le long du rayon
                float width = 0.09f * (1.0f - t * 0.65f);
                emitBoltRibbon(vc, mat, tmp, x0, y0, z0, x1, y1, z1, width,
                        0xFFFFFF, inst.effect.primaryRgb, alpha);
                emitBoltRibbon(vc, mat, tmp, x0, y0, z0, x1, y1, z1, width * 2.2f,
                        inst.effect.primaryRgb, inst.effect.secondaryRgb, Math.round(alpha * 0.45f));
                emitStarFlare3D(vc, mat, tmp, x1, y1, z1, 0.55f * (1.0f - t * 0.4f),
                        0xFFFFFF, inst.effect.secondaryRgb, alpha);
            }
        }
    }

    // ==================== 3. IMPACTS, CRITIQUES AMÉLIORÉS & EFFETS GRANDIOSES ====================

    private static void renderImpactEffect(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           Vec3 cam, VfxInstance inst, float t) {
        float cx = (float) (inst.x - cam.x);
        float cy = (float) (inst.y - cam.y);
        float cz = (float) (inst.z - cam.z);
        int fadeA = Math.round(235.0f * (1.0f - t));
        int pRgb = inst.effect.primaryRgb;
        int sRgb = inst.effect.secondaryRgb;

        switch (inst.effect) {
            case GOLDEN_CRIT_STAR -> {
                // #06 Étoile Critique Dorée : flash 4-branches asymétrique + anneau d'onde de choc
                float s = 0.45f + 0.85f * Mth.sin(t * (float) Math.PI * 0.85f);
                emitStarFlare3D(vc, mat, tmp, cx, cy, cz, s, 0xFFFFFF, pRgb, fadeA);
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, 0.25f + 1.45f * t, 0.09f * (1.0f - t), pRgb, sRgb, fadeA, 16);
            }
            case FOCUSED_BULLSEYE -> {
                // #07 Réticule Focalisé : anneau de visée qui se contracte puis explose
                float r = (t < 0.4f) ? (1.35f - 2.2f * t) : (0.47f + (t - 0.4f) * 2.1f);
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, r, 0.08f, pRgb, sRgb, fadeA, 8);
                emitStarFlare3D(vc, mat, tmp, cx, cy, cz, 0.55f * (1.0f - t), sRgb, pRgb, fadeA);
            }
            case OVERCLOCK_CYBER_X -> {
                // #08 Surcharge Cyber-Critique : immense X néon cyan-magenta + anneau électrique
                float len = 0.95f + 0.65f * t;
                float w = 0.14f * (1.0f - t * 0.7f);
                emitBoltRibbon(vc, mat, tmp, cx - len, cy - len, cz, cx + len, cy + len, cz, w, 0xFFFFFF, pRgb, fadeA);
                emitBoltRibbon(vc, mat, tmp, cx - len, cy + len, cz, cx + len, cy - len, cz, w, 0xFFFFFF, sRgb, fadeA);
                emitBoltRibbon(vc, mat, tmp, cx, cy - len, cz - len, cx, cy + len, cz + len, w * 0.8f, pRgb, sRgb, fadeA);
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, 0.35f + 1.9f * t, 0.10f * (1.0f - t), pRgb, sRgb, fadeA, 16);
            }
            case GUILLOTINE_CLEAVE -> {
                // #05 Couperet vertical descendant rouge-acier
                float topY = cy + 2.1f * (1.0f - t * 0.35f);
                float botY = cy - 0.9f;
                emitBoltRibbon(vc, mat, tmp, cx, topY, cz, cx, botY, cz, 0.22f * (1.0f - t * 0.6f), sRgb, pRgb, fadeA);
                emitHorizontalRing(vc, mat, tmp, cx, botY + 0.15f, cz, 0.3f + 1.4f * t, 0.12f * (1.0f - t), pRgb, sRgb, fadeA, 14);
            }
            case DRIPPING_BLOOD_EDGE, CARNAGE_BERSERK -> {
                // #09 / #12 Gerbe d'arcs artériels sanglants retombant sous la gravité
                int drops = (inst.effect == WeaponVisualEffectId.CARNAGE_BERSERK) ? 10 : 6;
                for (int i = 0; i < drops; i++) {
                    float ang = (float) (i * (Math.PI * 2.0 / drops) + (inst.seed & 7));
                    float rad = (0.25f + 1.35f * t) * (0.75f + 0.25f * (i & 1));
                    float dy = 0.55f * t - 1.35f * t * t;
                    float px = cx + Mth.cos(ang) * rad;
                    float py = cy + dy;
                    float pz = cz + Mth.sin(ang) * rad;
                    emitDiamondShard(vc, mat, tmp, px, py, pz, 0.11f * (1.0f - t * 0.4f), sRgb, pRgb, fadeA);
                }
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.2f, cz, 0.35f + 1.6f * t, 0.14f * (1.0f - t), pRgb, sRgb, fadeA, 16);
            }
            case VITAL_TRANSFUSION -> {
                // #11 Double hélice rubis-or de soin autour de la cible
                for (int s = 0; s < 2; s++) {
                    emitHelixRibbon(vc, mat, tmp, cx, cy - 0.6f, cz, 0.65f, 1.9f, t * 4.0f + s * (float) Math.PI, pRgb, sRgb, fadeA);
                }
            }
            case BRIMSTONE_METEOR -> {
                // #15 Traînée de météore oblique tombant du ciel + dôme de lave incandescent
                if (t < 0.45f) {
                    float mt = t / 0.45f;
                    float skyX = cx + 3.5f * (1.0f - mt);
                    float skyY = cy + 12.0f * (1.0f - mt);
                    float skyZ = cz + 2.2f * (1.0f - mt);
                    emitBoltRibbon(vc, mat, tmp, skyX + 1.2f, skyY + 4.0f, skyZ + 0.8f, skyX, skyY, skyZ,
                            0.28f, 0xFFFFFF, pRgb, 240);
                }
                float shockT = Math.max(0.0f, (t - 0.2f) / 0.8f);
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.3f, cz, 0.4f + 3.4f * shockT, 0.26f * (1.0f - shockT), sRgb, pRgb, fadeA, 20);
                emitHorizontalRing(vc, mat, tmp, cx, cy + 0.35f * shockT, cz, 0.2f + 2.1f * shockT, 0.18f * (1.0f - shockT), 0xFFFFFF, sRgb, fadeA, 16);
            }
            case CHAIN_VOLT_ARC -> {
                // #16 Éclair céleste brisé + arcs radiaux au sol
                emitJaggedLightning(vc, mat, tmp, cx + 0.4f, cy + 8.5f, cz - 0.3f, cx, cy, cz, 10, 0.38f,
                        inst.seed + inst.age / 2, 0xFFFFFF, pRgb, fadeA);
                for (int b = 0; b < 3; b++) {
                    float a = b * 2.094f + (inst.seed & 3);
                    emitJaggedLightning(vc, mat, tmp, cx, cy, cz,
                            cx + Mth.cos(a) * 1.9f, cy - 0.3f, cz + Mth.sin(a) * 1.9f,
                            5, 0.18f, inst.seed + b * 31L + inst.age, sRgb, pRgb, fadeA);
                }
            }
            case HYDRO_TIDAL_SURGE -> {
                // #17 Couronne aquatique montante + anneau d'écume
                emitHelixRibbon(vc, mat, tmp, cx, cy - 0.5f, cz, 0.55f + 0.85f * t, 1.6f, t * 5.0f, pRgb, sRgb, fadeA);
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.3f, cz, 0.3f + 2.2f * t, 0.15f * (1.0f - t), sRgb, pRgb, fadeA, 18);
            }
            case ABYSSAL_MAELSTROM -> {
                // #18 Maelström des Abysses : 4 bras de vortex océanique spiralant vers le centre !
                for (int arm = 0; arm < 4; arm++) {
                    float baseAng = arm * ((float) Math.PI * 0.5f) + t * 6.5f;
                    emitSpiralArm(vc, mat, tmp, cx, cy - 0.35f, cz, 2.8f * (1.0f - 0.25f * t), baseAng, pRgb, sRgb, fadeA);
                }
            }
            case PERMAFROST_RIME -> {
                // #19 Éclats cristallins de givre rayonnants
                for (int k = 0; k < 6; k++) {
                    float a = k * ((float) Math.PI / 3.0f);
                    float r = 0.35f + 1.55f * t;
                    emitBoltRibbon(vc, mat, tmp, cx, cy - 0.35f, cz,
                            cx + Mth.cos(a) * r, cy + 0.45f * (1.0f - t), cz + Mth.sin(a) * r,
                            0.10f * (1.0f - t * 0.5f), sRgb, pRgb, fadeA);
                }
            }
            case VENOM_BRIAR_FANG -> {
                // #20 Sarments épineux toxiques entrelacés
                emitHelixRibbon(vc, mat, tmp, cx, cy - 0.5f, cz, 0.72f, 1.5f, t * 3.2f, pRgb, sRgb, fadeA);
                emitHelixRibbon(vc, mat, tmp, cx, cy - 0.5f, cz, 0.72f, 1.5f, -t * 3.2f + 1.5f, sRgb, pRgb, fadeA);
            }
            case SEISMIC_TECTONIC -> {
                // #21 6 lignes de faille tectoniques au sol + anneau d'onde lourde
                float reach = 0.5f + 3.0f * Mth.sqrt(t);
                for (int f = 0; f < 6; f++) {
                    float a = f * ((float) Math.PI / 3.0f) + 0.2f;
                    emitJaggedLightning(vc, mat, tmp, cx, cy - 0.45f, cz,
                            cx + Mth.cos(a) * reach, cy - 0.45f, cz + Mth.sin(a) * reach,
                            4, 0.16f, inst.seed + f * 17L, sRgb, pRgb, fadeA);
                }
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.42f, cz, reach * 0.85f, 0.18f * (1.0f - t), pRgb, sRgb, fadeA, 18);
            }
            case ZEPHYR_WIND_LANCE -> {
                // #22 Double anneau de bourrasque aérodynamique
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, 0.3f + 2.0f * t, 0.11f * (1.0f - t), pRgb, sRgb, fadeA, 16);
                emitHorizontalRing(vc, mat, tmp, cx, cy + 0.5f * t, cz, 0.2f + 1.3f * t, 0.08f * (1.0f - t), sRgb, pRgb, fadeA, 16);
            }
            case SERAPH_SKY_PILLAR -> {
                // #23 Jugement du Séraphin : colonne céleste dorée de 11 blocs + 2 halos angéliques !
                float colW = 0.42f * Mth.sin(Math.min(1.0f, t * 1.3f) * (float) Math.PI);
                if (colW > 0.02f) {
                    emitBoltRibbon(vc, mat, tmp, cx, cy - 0.6f, cz, cx, cy + 11.0f, cz, colW, 0xFFFFFF, pRgb, fadeA);
                    emitBoltRibbon(vc, mat, tmp, cx, cy - 0.6f, cz, cx, cy + 11.0f, cz, colW * 1.9f, sRgb, pRgb, Math.round(fadeA * 0.45f));
                }
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.35f + 2.4f * t, cz, 0.55f + 1.8f * t, 0.14f * (1.0f - t), 0xFFFFFF, sRgb, fadeA, 20);
                emitHorizontalRing(vc, mat, tmp, cx, cy + 0.3f, cz, 0.35f + 2.5f * t, 0.12f * (1.0f - t), pRgb, sRgb, fadeA, 20);
            }
            case WITHER_SHADOW_RIFT -> {
                // #24 3 griffures d'ombre violettes-noires parallèles
                for (int claw = -1; claw <= 1; claw++) {
                    float off = claw * 0.28f;
                    emitBoltRibbon(vc, mat, tmp,
                            cx - 0.65f + off, cy + 0.75f, cz - 0.35f,
                            cx + 0.65f + off, cy - 0.65f, cz + 0.35f,
                            0.09f * (1.0f - t * 0.5f), sRgb, pRgb, fadeA);
                }
            }
            case VOID_WARP_SPHERE -> {
                // #26 Anneaux dimensionnels croisés (portail d'espace-temps)
                float r = 0.35f + 1.65f * Mth.sin(t * (float) Math.PI);
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, r, 0.12f, pRgb, sRgb, fadeA, 18);
                emitStarFlare3D(vc, mat, tmp, cx, cy, cz, r * 0.75f, sRgb, pRgb, fadeA);
            }
            case DRAGON_CHAOS_PRISM -> {
                // #27 Corolle draconique / prisme chromatique
                int c1 = Mth.hsvToRgb((t * 1.5f) % 1.0f, 0.8f, 1.0f);
                int c2 = Mth.hsvToRgb((t * 1.5f + 0.35f) % 1.0f, 0.85f, 1.0f);
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.35f, cz, 0.45f + 2.6f * t, 0.22f * (1.0f - t), c1, c2, fadeA, 20);
                emitStarFlare3D(vc, mat, tmp, cx, cy + 0.2f, cz, 0.95f * (1.0f - t * 0.5f), 0xFFFFFF, c1, fadeA);
            }
            case CHRONO_ETERNAL_HALO -> {
                // #28 Cadran temporel doré avec 2 aiguilles en rotation
                float r = 0.85f;
                emitHorizontalRing(vc, mat, tmp, cx, cy + 0.7f, cz, r, 0.07f, pRgb, sRgb, fadeA, 16);
                float aFast = t * 9.0f;
                float aSlow = t * 2.2f;
                emitBoltRibbon(vc, mat, tmp, cx, cy + 0.7f, cz,
                        cx + Mth.cos(aFast) * r * 0.85f, cy + 0.7f, cz + Mth.sin(aFast) * r * 0.85f,
                        0.05f, 0xFFFFFF, pRgb, fadeA);
                emitBoltRibbon(vc, mat, tmp, cx, cy + 0.7f, cz,
                        cx + Mth.cos(aSlow) * r * 0.55f, cy + 0.7f, cz + Mth.sin(aSlow) * r * 0.55f,
                        0.06f, sRgb, pRgb, fadeA);
            }
            case WARLORD_RALLY_SIGIL -> {
                // #29 Grand cercle runique octogonal au sol + rayons rotatifs de commandement
                float r = 2.15f * Math.min(1.0f, t * 2.5f);
                float rot = t * 2.4f;
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.42f, cz, r, 0.12f, pRgb, sRgb, fadeA, 8);
                emitHorizontalRing(vc, mat, tmp, cx, cy - 0.42f, cz, r * 0.62f, 0.08f, 0xFFFFFF, pRgb, fadeA, 8);
                for (int spoke = 0; spoke < 4; spoke++) {
                    float a = rot + spoke * ((float) Math.PI * 0.5f);
                    emitBoltRibbon(vc, mat, tmp,
                            cx + Mth.cos(a) * r, cy - 0.41f, cz + Mth.sin(a) * r,
                            cx - Mth.cos(a) * r, cy - 0.41f, cz - Mth.sin(a) * r,
                            0.06f, 0xFFFFFF, pRgb, fadeA);
                }
            }
            default -> {
                // Onde circulaire + éclat central élémentaire générique (#01, #02, #04, #13, #14, #30...)
                float maxR = (inst.effect == WeaponVisualEffectId.ANNIHILATING_ARC) ? 3.4f : 1.75f;
                emitHorizontalRing(vc, mat, tmp, cx, cy, cz, 0.25f + maxR * t, 0.16f * (1.0f - t), pRgb, sRgb, fadeA, 18);
                emitStarFlare3D(vc, mat, tmp, cx, cy, cz, 0.65f * (1.0f - t), 0xFFFFFF, pRgb, fadeA);
            }
        }
    }

    // ==================== PRIMITIVES GÉOMÉTRIQUES 3D ====================

    private static void emitHorizontalRing(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                           float cx, float cy, float cz,
                                           float radius, float thickness,
                                           int innerRgb, int outerRgb, int alpha, int segments) {
        if (alpha <= 4 || radius <= 0.02f || thickness <= 0.005f) return;
        float rIn = Math.max(0.01f, radius - thickness);
        float rOut = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            putQuad(vc, mat, tmp,
                    cx + c0 * rIn,  cy, cz + s0 * rIn,  innerRgb, alpha,
                    cx + c0 * rOut, cy, cz + s0 * rOut, outerRgb, Math.round(alpha * 0.25f),
                    cx + c1 * rOut, cy, cz + s1 * rOut, outerRgb, Math.round(alpha * 0.25f),
                    cx + c1 * rIn,  cy, cz + s1 * rIn,  innerRgb, alpha);
        }
    }

    private static void emitStarFlare3D(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float size,
                                        int coreRgb, int tipRgb, int alpha) {
        if (alpha <= 4 || size <= 0.02f) return;
        float w = size * 0.16f;
        // 3 axes orthogonaux croisés -> visible sous tous les angles caméra
        emitBoltRibbon(vc, mat, tmp, cx - size, cy, cz, cx + size, cy, cz, w, coreRgb, tipRgb, alpha);
        emitBoltRibbon(vc, mat, tmp, cx, cy - size * 1.25f, cz, cx, cy + size * 1.25f, cz, w, coreRgb, tipRgb, alpha);
        emitBoltRibbon(vc, mat, tmp, cx, cy, cz - size, cx, cy, cz + size, w, coreRgb, tipRgb, alpha);
    }

    private static void emitDiamondShard(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float s,
                                         int coreRgb, int edgeRgb, int alpha) {
        if (alpha <= 4) return;
        putQuad(vc, mat, tmp,
                cx, cy + s * 1.4f, cz, coreRgb, alpha,
                cx + s, cy, cz, edgeRgb, alpha,
                cx, cy - s * 1.4f, cz, edgeRgb, Math.round(alpha * 0.6f),
                cx - s, cy, cz, coreRgb, alpha);
        putQuad(vc, mat, tmp,
                cx, cy + s * 1.4f, cz, coreRgb, alpha,
                cx, cy, cz + s, edgeRgb, alpha,
                cx, cy - s * 1.4f, cz, edgeRgb, Math.round(alpha * 0.6f),
                cx, cy, cz - s, coreRgb, alpha);
    }

    public static void emitBoltRibbon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float halfWidth, int coreRgb, int outerRgb, int alpha) {
        if (alpha <= 4 || halfWidth <= 0.002f) return;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1.0e-5f) return;
        float invLen = Mth.invSqrt(lenSq);
        dx *= invLen; dy *= invLen; dz *= invLen;

        // Deux plans perpendiculaires croisés pour un volume 3D visible sous n'importe quel angle
        float px = -dz, py = 0.0f, pz = dx;
        float pLenSq = px * px + pz * pz;
        if (pLenSq < 1.0e-4f) {
            px = 1.0f; py = 0.0f; pz = 0.0f;
        } else {
            float invP = Mth.invSqrt(pLenSq);
            px *= invP; pz *= invP;
        }
        float qx = dy * pz - dz * py;
        float qy = dz * px - dx * pz;
        float qz = dx * py - dy * px;

        float pWx = px * halfWidth, pWy = py * halfWidth, pWz = pz * halfWidth;
        float qWx = qx * halfWidth, qWy = qy * halfWidth, qWz = qz * halfWidth;

        putQuad(vc, mat, tmp,
                x0 - pWx, y0 - pWy, z0 - pWz, outerRgb, alpha,
                x0 + pWx, y0 + pWy, z0 + pWz, coreRgb,  alpha,
                x1 + pWx, y1 + pWy, z1 + pWz, coreRgb,  alpha,
                x1 - pWx, y1 - pWy, z1 - pWz, outerRgb, alpha);
        putQuad(vc, mat, tmp,
                x0 - qWx, y0 - qWy, z0 - qWz, outerRgb, alpha,
                x0 + qWx, y0 + qWy, z0 + qWz, coreRgb,  alpha,
                x1 + qWx, y1 + qWy, z1 + qWz, coreRgb,  alpha,
                x1 - qWx, y1 - qWy, z1 - qWz, outerRgb, alpha);
    }

    private static void emitJaggedLightning(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1,
                                            int segments, float jitterAmp, long seed,
                                            int coreRgb, int glowRgb, int alpha) {
        if (alpha <= 4) return;
        float prevX = x0, prevY = y0, prevZ = z0;
        long h = seed * 0x9E3779B97F4A7C15L;
        for (int i = 1; i <= segments; i++) {
            float f = (float) i / segments;
            float nx = Mth.lerp(f, x0, x1);
            float ny = Mth.lerp(f, y0, y1);
            float nz = Mth.lerp(f, z0, z1);
            if (i < segments) {
                float env = Mth.sin(f * (float) Math.PI) * jitterAmp;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nx += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
                h = h * 6364136223846793005L + 1442695040888963407L;
                ny += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env * 0.65f;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nz += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
            }
            emitBoltRibbon(vc, mat, tmp, prevX, prevY, prevZ, nx, ny, nz, 0.045f, coreRgb, glowRgb, alpha);
            emitBoltRibbon(vc, mat, tmp, prevX, prevY, prevZ, nx, ny, nz, 0.11f, glowRgb, glowRgb, Math.round(alpha * 0.38f));
            prevX = nx; prevY = ny; prevZ = nz;
        }
    }

    private static void emitCurvedTendril(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float phase, float progress,
                                          int darkRgb, int brightRgb, int alpha) {
        if (alpha <= 4) return;
        float mx = (x0 + x1) * 0.5f + Mth.cos(phase) * 0.75f;
        float my = Math.max(y0, y1) + 0.85f + Mth.sin(phase) * 0.35f;
        float mz = (z0 + z1) * 0.5f + Mth.sin(phase) * 0.75f;

        int steps = 10;
        float px = x0, py = y0, pz = z0;
        for (int i = 1; i <= steps; i++) {
            float u = (float) i / steps;
            float inv = 1.0f - u;
            float bx = inv * inv * x0 + 2.0f * inv * u * mx + u * u * x1;
            float by = inv * inv * y0 + 2.0f * inv * u * my + u * u * y1;
            float bz = inv * inv * z0 + 2.0f * inv * u * mz + u * u * z1;
            float pulse = 1.0f - Math.min(1.0f, Math.abs(u - progress) * 2.6f);
            int rgb = pulse > 0.35f ? brightRgb : darkRgb;
            emitBoltRibbon(vc, mat, tmp, px, py, pz, bx, by, bz, 0.055f + 0.035f * pulse, rgb, darkRgb, alpha);
            px = bx; py = by; pz = bz;
        }
    }

    private static void emitHelixRibbon(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float baseY, float cz,
                                        float radius, float height, float phase,
                                        int rgbA, int rgbB, int alpha) {
        if (alpha <= 4) return;
        int steps = 12;
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= steps; i++) {
            float u = (float) i / steps;
            float a = phase + u * (float) Math.PI * 2.4f;
            float nx = cx + Mth.cos(a) * radius * (1.0f - 0.25f * u);
            float ny = baseY + u * height;
            float nz = cz + Mth.sin(a) * radius * (1.0f - 0.25f * u);
            if (i > 0) {
                emitBoltRibbon(vc, mat, tmp, px, py, pz, nx, ny, nz, 0.06f * (1.0f - u * 0.5f), rgbA, rgbB, alpha);
            }
            px = nx; py = ny; pz = nz;
        }
    }

    private static void emitSpiralArm(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz,
                                      float outerR, float startAng,
                                      int innerRgb, int outerRgb, int alpha) {
        if (alpha <= 4) return;
        int steps = 10;
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= steps; i++) {
            float u = (float) i / steps;
            float r = Mth.lerp(u, 0.2f, outerR);
            float a = startAng + u * 2.8f;
            float nx = cx + Mth.cos(a) * r;
            float ny = cy + (1.0f - u) * 0.45f;
            float nz = cz + Mth.sin(a) * r;
            if (i > 0) {
                emitBoltRibbon(vc, mat, tmp, px, py, pz, nx, ny, nz, 0.09f * (1.0f - u * 0.6f), outerRgb, innerRgb, alpha);
            }
            px = nx; py = ny; pz = nz;
        }
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

    private static void spawnAccentParticles(ClientLevel level, WeaponVisualEffectId fx,
                                             float x, float y, float z, float tx, float ty, float tz) {
        var pt = switch (fx) {
            case MOLTEN_FIRE_WAVE, BRIMSTONE_METEOR -> ParticleTypes.FLAME;
            case SOUL_FIRE_CRESCENT -> ParticleTypes.SOUL_FIRE_FLAME;
            case CHAIN_VOLT_ARC, OVERCLOCK_CYBER_X -> ParticleTypes.ELECTRIC_SPARK;
            case HYDRO_TIDAL_SURGE, ABYSSAL_MAELSTROM -> ParticleTypes.SPLASH;
            case PERMAFROST_RIME -> ParticleTypes.SNOWFLAKE;
            case SERAPH_SKY_PILLAR, GOLDEN_CRIT_STAR, CHRONO_ETERNAL_HALO -> ParticleTypes.END_ROD;
            case ASTRAL_STAR_LANCE, VOID_WARP_SPHERE, DRAGON_CHAOS_PRISM -> ParticleTypes.PORTAL;
            case WITHER_SHADOW_RIFT -> ParticleTypes.SCULK_SOUL;
            default -> null;
        };
        if (pt == null) return;
        for (int i = 0; i < 6; i++) {
            double vx = (level.random.nextDouble() - 0.5) * 0.16;
            double vy = level.random.nextDouble() * 0.12;
            double vz = (level.random.nextDouble() - 0.5) * 0.16;
            level.addParticle(pt, x, y, z, vx, vy, vz);
        }
    }
}
