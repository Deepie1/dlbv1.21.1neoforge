package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import deepluckyblock.DeepLuckyBlockMod;

import java.util.ArrayList;
import java.util.List;

/**
 * T349-COSMETIC-PIXEL-AURA : moteur cosmétique maison, 100 % pixels 3D, sans AUCUNE particule vanilla.
 *
 * <p><b>Problème résolu.</b> Les ailes, couronnes et auras étaient rendues par 2 à 6 particules
 * ponctuelles par tick : de loin ça ne ressemblait qu'à « quelques points dans le dos », et chaque
 * particule vanilla coûte un tick + un billboard à rendre (donc du lag dès qu'il y a plusieurs joueurs
 * ou que le budget grimpe).</p>
 *
 * <p><b>Solution.</b> On ne stocke plus RIEN par tick : à chaque frame on dessine directement les
 * formes complètes (membrane d'aile, couronne en anneau, aura au sol) en mini-cubes 3D, dans un seul
 * batch dédié à ce système. Coût : une centaine de cubes par joueur, zéro particule, zéro paquet
 * réseau, zéro allocation par frame (aucune liste temporaire).</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CosmeticPixelAura {

    private CosmeticPixelAura() {}

    /** Budget total de cubes par frame (tous joueurs confondus). */
    private static final int MAX_CUBES_PER_FRAME = 1400;
    /** Distance maximale de rendu d'une aura cosmétique. */
    private static final double MAX_DIST_SQR = 40.0 * 40.0;

    private static RenderType auraAdditiveType;
    private static RenderType auraTranslucentType;

    private static int budget;

    private static final List<Player> TARGETS = new ArrayList<>(8);

    // =====================================================================
    // RENDER TYPES DÉDIÉS (aucune interférence avec les VFX d'armes)
    // =====================================================================

    public static RenderType getAdditiveType() {
        if (auraAdditiveType == null) {
            auraAdditiveType = RenderType.create(
                    "dlb_cosmetic_aura_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.PARTICLES_TARGET)
                            .createCompositeState(false));
        }
        return auraAdditiveType;
    }

    public static RenderType getTranslucentType() {
        if (auraTranslucentType == null) {
            auraTranslucentType = RenderType.create(
                    "dlb_cosmetic_aura_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.PARTICLES_TARGET)
                            .createCompositeState(false));
        }
        return auraTranslucentType;
    }

    // =====================================================================
    // COLLECTE DES CIBLES (une fois par tick)
    // =====================================================================

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            Player viewer = mc.player;
            if (level == null || viewer == null) {
                TARGETS.clear();
                return;
            }
            TARGETS.clear();
            TARGETS.add(viewer);
            List<? extends Player> others = level.players();
            for (int i = 0, n = others.size(); i < n; i++) {
                Player other = others.get(i);
                if (other == viewer || !other.isAlive() || other.isSpectator()) continue;
                if (other.distanceToSqr(viewer) > MAX_DIST_SQR) continue;
                TARGETS.add(other);
            }
        } catch (Throwable ignored) {
            TARGETS.clear();
        }
    }

    // =====================================================================
    // RENDU
    // =====================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (TARGETS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mat = event.getPoseStack().last().pose();
        Vector3f tmp = new Vector3f();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        float time = WeaponWorldVfxManager.smoothTimeSeconds();
        WeaponWorldVfxManager.beginFrame(time);

        WeaponWorldVfxManager.resetCubeBudget();
        budget = MAX_CUBES_PER_FRAME;
        VertexConsumer add = null;
        VertexConsumer alpha = null;

        try {
            for (int i = 0, n = TARGETS.size(); i < n && budget > 0; i++) {
                Player p = TARGETS.get(i);
                if (!p.isAlive() || p.isInvisible()) continue;
                if (p.distanceToSqr(cam.x, cam.y, cam.z) > MAX_DIST_SQR) continue;

                int flags = CosmeticAuraClientTicker.getCosmeticFlags(p, level.getGameTime());
                if (flags == 0) continue;

                if (add == null) add = buffers.getBuffer(getAdditiveType());
                if (alpha == null) alpha = buffers.getBuffer(getTranslucentType());

                renderPlayerAura(p, flags, cam, mat, tmp, add, alpha, time);
            }
        } catch (RuntimeException ignored) {
            // Jamais de crash pour du cosmétique
        } finally {
            try {
                buffers.endBatch(getTranslucentType());
            } catch (RuntimeException ignored) {
            }
            try {
                buffers.endBatch(getAdditiveType());
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void renderPlayerAura(Player p, int flags, Vec3 cam, Matrix4f mat, Vector3f tmp,
                                         VertexConsumer add, VertexConsumer alpha, float time) {
        float px = (float) (p.getX() - cam.x);
        float py = (float) (p.getY() - cam.y);
        float pz = (float) (p.getZ() - cam.z);
        float height = p.getBbHeight();

        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        float fwdX = -Mth.sin(yaw);
        float fwdZ = Mth.cos(yaw);
        float rightX = -fwdZ;
        float rightZ = fwdX;

        // --- 1. COURONNES / HALOS (anneaux de pixels au-dessus de la tête) ---
        if ((flags & CosmeticAuraClientTicker.F_COMBO_110_AVATAR) != 0) {
            haloRing(add, mat, tmp, px, py + height + 0.30f, pz, 0.42f, time * 0.55f,
                    14, 0xFFF6D0, 0xFFC63D, 240, 1.6f);
        } else if ((flags & CosmeticAuraClientTicker.F_COMBO_097_ETERNAL) != 0
                || (flags & CosmeticAuraClientTicker.F_COMBO_098_SWORD_GOD) != 0) {
            haloRing(add, mat, tmp, px, py + height + 0.28f, pz, 0.40f, time * 0.5f,
                    12, 0xFFE9A0, 0xFFB020, 235, 1.5f);
        } else if ((flags & CosmeticAuraClientTicker.F_ENCH_KING) != 0) {
            haloRing(add, mat, tmp, px, py + height + 0.26f, pz, 0.34f, time * 0.42f,
                    10, 0xFFD34D, 0xFF9500, 225, 1.4f);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_MYSTIC_MIND) != 0) {
            haloRing(add, mat, tmp, px, py + height + 0.34f, pz, 0.32f, -time * 0.40f,
                    10, 0xC08BFF, 0x7B3BE8, 215, 1.3f);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_RADIANT_AEGIS) != 0) {
            haloRing(add, mat, tmp, px, py + height + 0.20f, pz, 0.36f, time * 0.36f,
                    10, 0xFFF7D9, 0xBFE8FF, 210, 1.3f);
        }

        // --- 2. AILES EN VRAIE MEMBRANE DE PIXELS (5 x 3 cubes par aile) ---
        boolean archangel = (flags & CosmeticAuraClientTicker.F_COMBO_101_ARCHANGEL) != 0;
        boolean winged = (flags & CosmeticAuraClientTicker.F_ENCH_WINGED_ENDURANCE) != 0
                && (!p.onGround() || p.isFallFlying());
        if (archangel || winged) {
            int outer;
            int inner;
            if (archangel) {
                outer = 0xFFE9A0;
                inner = 0xFFFBEA;
            } else {
                outer = 0xCFE8FF;
                inner = 0xFFFFFF;
            }
            for (int side = -1; side <= 1; side += 2) {
                wing(add, alpha, mat, tmp, px, py, pz, height,
                        fwdX, fwdZ, rightX, rightZ, side, time, outer, inner,
                        archangel ? 1.0f : 0.82f);
            }
        }

        // --- 3. AURA BASSE (anneau au sol) ---
        if ((flags & CosmeticAuraClientTicker.F_COMBO_106_WARLORD) != 0) {
            haloRing(add, mat, tmp, px, py + 0.10f, pz, 0.85f, -time * 0.45f,
                    12, 0xFF7A2E, 0xFFC46B, 195, 1.2f);
        }
        if ((flags & CosmeticAuraClientTicker.F_COMBO_062_PLAGUE) != 0) {
            haloRing(alpha, mat, tmp, px, py + 0.14f, pz, 0.95f, time * 0.30f,
                    10, 0x6BAF1E, 0x3E7A0C, 150, 1.1f);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_DREAD_AURA) != 0) {
            haloRing(alpha, mat, tmp, px, py + 0.32f, pz, 0.66f, -time * 0.38f,
                    8, 0x3A0A66, 0x7B2BD6, 165, 1.0f);
        }
    }

    /** Anneau de pixels (couronne / aura au sol), avec léger décalage vertical en dents de scie. */
    private static void haloRing(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float radius, float phase,
                                 int points, int rgbA, int rgbB, int alpha, float size) {
        float step = 0.035f * size;
        for (int i = 0; i < points && budget > 0; i++) {
            float ang = phase + (float) (Math.PI * 2.0 * i / points);
            float x = cx + Mth.cos(ang) * radius;
            float z = cz + Mth.sin(ang) * radius;
            float y = cy + Mth.sin(ang * 2.0f + phase) * 0.035f;
            int rgb = ((i & 1) == 0) ? rgbA : rgbB;
            int a = alpha - (i % 3) * 12;
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, y, z, step, step * 0.80f, step, rgb, a);
            budget--;
            // petit pixel intérieur pour l'épaisseur (un anneau doit être fait de pixels, pas d'un trait)
            if (radius > 0.5f && (i & 1) == 0) {
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                        cx + Mth.cos(ang) * (radius - step * 2.2f), y + step * 0.6f,
                        cz + Mth.sin(ang) * (radius - step * 2.2f),
                        step * 0.55f, step * 0.45f, step * 0.55f, rgbB, Math.round(a * 0.7f));
                budget--;
            }
        }
    }

    /**
     * Une aile complète : membrane en escalier (5 colonnes x 3 rangées), plumes de bord plus fines,
     * battement fluide (le bout de l'aile monte/descend avec un déphasage par colonne).
     */
    private static void wing(VertexConsumer add, VertexConsumer alpha, Matrix4f mat, Vector3f tmp,
                             float px, float py, float pz, float height,
                             float fwdX, float fwdZ, float rightX, float rightZ,
                             int side, float time, int outerRgb, int innerRgb, float scale) {
        float shoulderY = py + height * 0.66f;
        float backOffset = 0.16f;
        float flap = Mth.sin(time * 2.6f);

        int cols = 5;
        int rows = 3;
        for (int c = 0; c < cols; c++) {
            float cu = c / (float) (cols - 1);
            float span = (0.20f + cu * 0.72f) * scale;
            float sweepBack = backOffset + cu * 0.30f;
            float colFlap = flap * (0.06f + cu * 0.16f);
            for (int r = 0; r < rows; r++) {
                float ru = r / (float) (rows - 1);
                float lift = 0.34f - ru * 0.30f + colFlap;
                float bx = px + rightX * span * side - fwdX * sweepBack;
                float bz = pz + rightZ * span * side - fwdZ * sweepBack;
                float by = shoulderY + lift;

                float size = (0.058f + 0.030f * (1.0f - cu)) * (0.85f + 0.30f * ru) * scale;
                // Membrane intérieure plus douce, bord extérieur plus vif : l'aile se lit comme des plumes
                boolean outerEdge = (r == 0) || (c == cols - 1);
                VertexConsumer vc = outerEdge ? add : alpha;
                int rgb = outerEdge ? innerRgb : outerRgb;
                int a = outerEdge ? 235 : 150;
                if (budget <= 0) return;
                WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, bx, by, bz,
                        size, size * 0.55f, size * 0.85f, rgb, a);
                budget--;
            }
            // Plume de pointe plus fine, décalée vers le haut : silhouette d'aile, pas un rectangle
            if (budget > 0) {
                float tipX = px + rightX * (0.20f + cu * 0.72f) * scale * side - fwdX * (backOffset + cu * 0.30f);
                float tipZ = pz + rightZ * (0.20f + cu * 0.72f) * scale * side - fwdZ * (backOffset + cu * 0.30f);
                float tipY = shoulderY + 0.40f + colFlap + 0.05f * c;
                WeaponWorldVfxManager.emitVoxelCube(add, mat, tmp, tipX, tipY, tipZ,
                        0.034f * scale, 0.026f * scale, 0.050f * scale, innerRgb, 200);
                budget--;
            }
        }
    }
}
