package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.enchantments.WeaponVisualEffectId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * T342-WEAPON-VFX-30 : Rendu d'effets lumineux et micro-animations 3D directement sur la géométrie
 * de l'arme tenue en main / posée au sol / en cadre (JAMAIS dans l'inventaire GUI) :
 * <ul>
 *   <li>Extrait et met en cache les vraies facettes d'arête 3D ({@code UP/DOWN/EAST/WEST}) du
 *       {@link BakedModel} de n'importe quelle épée, hache, arc, arbalète ou arme moddée (ainsi que
 *       les 3 pointes 3D du Trident tenu en main).</li>
 *   <li>Fait glisser des ondes spéculaires de tranchant, gouttes de sang animées, arcs électriques,
 *       flammes/flammes d'âme, cristaux de givre, gouttes de venin, allonge fantôme et anneaux
 *       sacrés/temporels directement le long du fil métallique de la lame (en épargnant le manche).</li>
 * </ul>
 */
public final class WeaponBladeVisualRenderer {

    private static final Direction[] EDGE_DIRS = {
            Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST
    };

    private static final Map<BakedModel, BladeGeometry> MODEL_GEOMETRY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>(128));
    private static final Map<ItemStack, CachedStackLayers> STACK_LAYER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>(128));

    private static RenderType bladeAdditiveType;
    private static RenderType bladeTranslucentType;

    private record EdgeQuad(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float progress,
            boolean lowerEdge
    ) {}

    private static final class BladeGeometry {
        final EdgeQuad[] edgeQuads;
        final float hiltX, hiltY, hiltZ;
        final float tipX, tipY, tipZ;
        final float dirX, dirY;

        BladeGeometry(EdgeQuad[] edgeQuads,
                      float hiltX, float hiltY, float hiltZ,
                      float tipX, float tipY, float tipZ,
                      float dirX, float dirY) {
            this.edgeQuads = edgeQuads;
            this.hiltX = hiltX;
            this.hiltY = hiltY;
            this.hiltZ = hiltZ;
            this.tipX = tipX;
            this.tipY = tipY;
            this.tipZ = tipZ;
            this.dirX = dirX;
            this.dirY = dirY;
        }
    }

    private record CachedStackLayers(long expiresGameTime, List<WeaponVisualEffectId.ResolvedLayer> layers) {}

    private WeaponBladeVisualRenderer() {}

    private static RenderType getBladeAdditiveType() {
        if (bladeAdditiveType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            bladeAdditiveType = RenderType.create(
                    "dlb_blade_edge_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    2048,
                    false,
                    false,
                    state);
        }
        return bladeAdditiveType;
    }

    private static RenderType getBladeTranslucentType() {
        if (bladeTranslucentType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            bladeTranslucentType = RenderType.create(
                    "dlb_blade_edge_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    2048,
                    false,
                    true,
                    state);
        }
        return bladeTranslucentType;
    }

    public static void renderWeaponOverlays(ItemStack stack, ItemDisplayContext ctx,
                                            boolean leftHand, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            BakedModel model) {
        if (stack == null || stack.isEmpty() || ctx == null || poseStack == null || bufferSource == null) {
            return;
        }
        // Jamais dans les slots d'inventaire (GUI) pour garder l'interface 100 % lisible
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.HEAD || ctx == ItemDisplayContext.NONE) {
            return;
        }

        List<WeaponVisualEffectId.ResolvedLayer> layers = getCachedLayers(stack);
        if (layers.isEmpty()) {
            return;
        }

        boolean firstPerson = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        float fpScale = firstPerson ? 0.68f : 1.0f;
        float time = WeaponWorldVfxManager.smoothTimeSeconds();

        BladeGeometry geom;
        if (stack.getItem() instanceof TridentItem
                && ctx != ItemDisplayContext.GROUND
                && ctx != ItemDisplayContext.FIXED) {
            geom = TRIDENT_3D_GEOMETRY;
        } else {
            if (model == null) return;
            geom = getOrBuildGeometry(model);
        }
        if (geom == null || geom.edgeQuads.length == 0) {
            return;
        }

        Matrix4f mat = poseStack.last().pose();
        Vector3f tmp = new Vector3f();

        for (int i = 0, n = layers.size(); i < n; i++) {
            WeaponVisualEffectId.ResolvedLayer layer = layers.get(i);
            WeaponVisualEffectId fx = layer.effect();
            int lvl = layer.level();
            float layerWeight = (i == 0) ? 1.0f : (i == 1 ? 0.78f : 0.60f);

            VertexConsumer vc = bufferSource.getBuffer(
                    fx.additive ? getBladeAdditiveType() : getBladeTranslucentType());

            // 1) Onde lumineuse animée le long du tranchant 3D de la lame
            renderAnimatedEdgeQuads(vc, mat, tmp, geom, fx, lvl, time + i * 0.37f, layerWeight * fpScale);

            // 2) Micro-animations 3D dédiées sur la pointe / garde / gouttière
            renderBladeOrnament(vc, mat, tmp, geom, fx, lvl, time + i * 0.51f, layerWeight * fpScale);
        }
    }

    private static List<WeaponVisualEffectId.ResolvedLayer> getCachedLayers(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        CachedStackLayers cached = STACK_LAYER_CACHE.get(stack);
        if (cached != null && now <= cached.expiresGameTime()) {
            return cached.layers();
        }
        List<WeaponVisualEffectId.ResolvedLayer> resolved = WeaponVisualEffectId.resolveLayers(stack, 3);
        STACK_LAYER_CACHE.put(stack, new CachedStackLayers(now + 10L, resolved));
        return resolved;
    }

    // ==================== 1. LUEUR ANIMÉE SUR LE FIL DE LA LAME ====================

    private static void renderAnimatedEdgeQuads(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                                BladeGeometry geom, WeaponVisualEffectId fx,
                                                int level, float time, float weight) {
        EdgeQuad[] quads = geom.edgeQuads;
        int pRgb = fx.primaryRgb;
        int sRgb = fx.secondaryRgb;

        for (int i = 0, n = quads.length; i < n; i++) {
            EdgeQuad q = quads[i];
            float s = q.progress;
            float intensity = evaluateEdgeWave(fx, s, q.lowerEdge, time, level);
            if (intensity <= 0.04f) continue;

            int alpha = Math.round(Mth.clamp(intensity * 215.0f * weight, 0.0f, 235.0f));
            if (alpha <= 5) continue;

            int rgb;
            if (fx == WeaponVisualEffectId.DRAGON_CHAOS_PRISM) {
                rgb = Mth.hsvToRgb((s * 0.7f + time * 0.65f) % 1.0f, 0.78f, 1.0f);
            } else {
                rgb = intensity > 0.72f ? blendRgb(pRgb, 0xFFFFFF, (intensity - 0.72f) * 2.2f)
                                        : blendRgb(sRgb, pRgb, intensity);
            }

            WeaponWorldVfxManager.putQuad(vc, mat, tmp,
                    q.x0, q.y0, q.z0, rgb, alpha,
                    q.x1, q.y1, q.z1, rgb, alpha,
                    q.x2, q.y2, q.z2, rgb, alpha,
                    q.x3, q.y3, q.z3, rgb, alpha);
        }
    }

    /** Équation d'onde propre à chacun des 30 effets le long de l'axe [0.22 .. 1.0] de la lame. */
    private static float evaluateEdgeWave(WeaponVisualEffectId fx, float s, boolean lowerEdge, float time, int level) {
        return switch (fx) {
            case RAZOR_GLEAM -> {
                // Reflet spéculaire net balayant la lame toutes les ~1.6 s avec repos métallique subtil
                float cycle = (time * (0.65f + 0.04f * Math.min(8, level))) % 1.45f;
                float dist = Math.abs(s - cycle);
                float sweep = Math.max(0.0f, 1.0f - dist * 7.5f);
                yield 0.10f + 0.90f * sweep * sweep;
            }
            case SUPREME_PRISM_EDGE -> {
                // Double reflet diamant haute vitesse + halo permanent sur le tranchant
                float spd = 1.05f + 0.06f * Math.min(12, level);
                float w1 = Math.max(0.0f, 1.0f - Math.abs(s - ((time * spd) % 1.2f)) * 6.2f);
                float w2 = Math.max(0.0f, 1.0f - Math.abs(s - (((time * spd) + 0.6f) % 1.2f)) * 7.0f);
                yield Math.min(1.0f, 0.24f + 0.85f * Math.max(w1, w2));
            }
            case TWIN_ECHO_SLASH -> {
                float w1 = Math.max(0.0f, 1.0f - Math.abs(s - ((time * 1.15f) % 1.3f)) * 8.0f);
                float w2 = Math.max(0.0f, 1.0f - Math.abs(s - ((time * 1.15f - 0.22f + 1.3f) % 1.3f)) * 8.5f);
                yield 0.14f + 0.86f * Math.max(w1, w2 * 0.8f);
            }
            case GUILLOTINE_CLEAVE -> {
                // Concentré sur la tête d'exécution (tiers supérieur de la lame)
                if (s < 0.50f) yield 0.08f;
                float pulse = 0.55f + 0.45f * Mth.sin(time * 4.2f - s * 4.0f);
                yield (s - 0.35f) * 1.25f * pulse;
            }
            case DRIPPING_BLOOD_EDGE -> {
                float base = lowerEdge ? 0.68f : 0.28f;
                yield base * (0.72f + 0.28f * Mth.sin(time * 2.6f + s * 6.0f));
            }
            case CRIMSON_SIPHON -> {
                // Double battement cardiaque (lub-dub) descendant de la pointe vers la garde !
                float beatPhase = (time * 1.35f) % 1.0f;
                float lub = Math.max(0.0f, 1.0f - Math.abs(beatPhase - 0.12f) * 11.0f);
                float dub = Math.max(0.0f, 1.0f - Math.abs(beatPhase - 0.30f) * 13.0f);
                float flow = Math.max(0.0f, 1.0f - Math.abs((1.0f - s) - beatPhase) * 4.8f);
                yield Math.min(1.0f, 0.25f + 0.55f * Math.max(lub, dub) + 0.45f * flow);
            }
            case CARNAGE_BERSERK -> {
                float rage = 0.55f + 0.45f * Mth.sin(time * 8.5f + s * 11.0f);
                yield 0.35f + 0.65f * rage;
            }
            case OVERCLOCK_CYBER_X, CHAIN_VOLT_ARC -> {
                float spark = Mth.sin(time * 16.0f + s * 19.0f) * Mth.cos(time * 9.0f - s * 13.0f);
                yield 0.22f + 0.78f * Math.max(0.0f, spark);
            }
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, BRIMSTONE_METEOR -> {
                float lick = 0.5f + 0.5f * Mth.sin(time * 6.5f - s * 9.5f);
                yield 0.26f + 0.72f * lick;
            }
            case VENOM_BRIAR_FANG -> {
                // Vernis toxique concentré sur le croc / la pointe
                yield (s < 0.48f) ? 0.10f : (0.35f + 0.60f * Mth.sin(time * 3.4f + s * 7.0f) * 0.5f + 0.3f);
            }
            default -> {
                float wave = 0.5f + 0.5f * Mth.sin(time * 3.6f - s * 6.5f);
                yield 0.18f + 0.68f * wave;
            }
        };
    }

    // ==================== 2. MICRO-ANIMATIONS 3D SUR POINTE / GARDE / FIL ====================

    private static void renderBladeOrnament(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                            BladeGeometry g, WeaponVisualEffectId fx,
                                            int level, float time, float weight) {
        int pRgb = fx.primaryRgb;
        int sRgb = fx.secondaryRgb;
        int baseA = Math.round(215.0f * weight);
        if (baseA <= 8) return;

        switch (fx) {
            case RAZOR_GLEAM -> {
                // Étoile spéculaire fine à la pointe exactement quand le reflet atteint le bout de la lame
                float cycle = (time * (0.65f + 0.04f * Math.min(8, level))) % 1.45f;
                float tipFlash = Math.max(0.0f, 1.0f - Math.abs(cycle - 0.96f) * 6.5f);
                if (tipFlash > 0.05f) {
                    emitLocalStar(vc, mat, tmp, g.tipX, g.tipY, g.tipZ, 0.085f * tipFlash * weight,
                            time * 2.0f, 0xFFFFFF, pRgb, Math.round(235 * tipFlash * weight));
                }
            }
            case SUPREME_PRISM_EDGE, GOLDEN_CRIT_STAR, ASTRAL_STAR_LANCE -> {
                // Étoile diamant / critique / astrale tournant en continu sur la pointe
                float pulse = 0.72f + 0.28f * Mth.sin(time * 5.2f);
                emitLocalStar(vc, mat, tmp, g.tipX, g.tipY, g.tipZ, 0.095f * pulse * weight,
                        time * 2.6f, 0xFFFFFF, pRgb, baseA);
            }
            case SPECTRAL_REACH -> {
                // Pointe spectrale fantôme prolongée au-delà de l'arme (+18 % à +28 %)
                float ext = (0.16f + 0.03f * Math.min(5, level)) * weight;
                float ex = g.tipX + g.dirX * ext;
                float ey = g.tipY + g.dirY * ext;
                WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp,
                        g.tipX - g.dirX * 0.06f, g.tipY - g.dirY * 0.06f, g.tipZ,
                        ex, ey, g.tipZ, 0.026f * weight, 0xFFFFFF, pRgb, Math.round(baseA * 0.85f));
            }
            case FOCUSED_BULLSEYE -> {
                // Réticule losange tournant sur la pointe
                emitLocalRingXY(vc, mat, tmp, g.tipX, g.tipY, g.tipZ, 0.065f * weight, 0.010f, time * 1.8f, sRgb, pRgb, baseA, 4);
            }
            case OVERCLOCK_CYBER_X -> {
                // Flash X cybernétique sur la pointe + arc rapide
                float r = 0.075f * weight;
                WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp,
                        g.tipX - r, g.tipY - r, g.tipZ, g.tipX + r, g.tipY + r, g.tipZ, 0.012f, 0xFFFFFF, pRgb, baseA);
                WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp,
                        g.tipX - r, g.tipY + r, g.tipZ, g.tipX + r, g.tipY - r, g.tipZ, 0.012f, 0xFFFFFF, sRgb, baseA);
            }
            case DRIPPING_BLOOD_EDGE, VENOM_BRIAR_FANG -> {
                // Vraies gouttes 3D (sang écarlate ou venin acide) qui se forment sous le tranchant et tombent !
                for (int d = 0; d < 3; d++) {
                    float cycle = (time * 0.85f + d * 0.33f) % 1.0f;
                    float along = 0.42f + d * 0.23f;
                    float bx = Mth.lerp(along, g.hiltX, g.tipX);
                    float by = Mth.lerp(along, g.hiltY, g.tipY);
                    float dropFall = (cycle < 0.35f) ? 0.0f : (cycle - 0.35f) * (cycle - 0.35f) * 0.42f;
                    float size = ((cycle < 0.35f) ? (cycle / 0.35f) : (1.0f - (cycle - 0.35f) / 0.65f)) * 0.024f * weight;
                    if (size > 0.003f) {
                        emitLocalDiamond(vc, mat, tmp, bx + 0.02f, by - dropFall - 0.02f, g.tipZ,
                                size, sRgb, pRgb, Math.round(baseA * 0.95f));
                    }
                }
            }
            case CRIMSON_SIPHON, VITAL_TRANSFUSION -> {
                // Perles de vie / sang remontant en spirale de la pointe vers la garde
                for (int p = 0; p < 3; p++) {
                    float u = 1.0f - ((time * 0.65f + p * 0.33f) % 1.0f);
                    if (u < 0.24f) continue;
                    float ang = time * 4.5f + p * 2.1f;
                    float px = Mth.lerp(u, g.hiltX, g.tipX) + Mth.cos(ang) * 0.035f * weight;
                    float py = Mth.lerp(u, g.hiltY, g.tipY) + Mth.sin(ang) * 0.035f * weight;
                    float pz = g.tipZ + Mth.cos(ang + 1.1f) * 0.035f * weight;
                    emitLocalDiamond(vc, mat, tmp, px, py, pz, 0.019f * weight, sRgb, pRgb, baseA);
                }
            }
            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, BRIMSTONE_METEOR -> {
                // Flammèches montantes le long du fil de la lame
                for (int f = 0; f < 4; f++) {
                    float cyc = (time * 1.25f + f * 0.25f) % 1.0f;
                    float u = 0.34f + f * 0.19f;
                    float fxPos = Mth.lerp(u, g.hiltX, g.tipX) + Mth.sin(time * 7.0f + f) * 0.015f;
                    float fyPos = Mth.lerp(u, g.hiltY, g.tipY) + cyc * 0.11f * weight;
                    float sz = (1.0f - cyc) * 0.026f * weight;
                    emitLocalDiamond(vc, mat, tmp, fxPos, fyPos, g.tipZ, sz, sRgb, pRgb, Math.round(baseA * (1.0f - cyc)));
                }
            }
            case CHAIN_VOLT_ARC -> {
                // Petit éclair 3D brisé courant de la garde jusqu'à la pointe de l'arme !
                float sx = Mth.lerp(0.26f, g.hiltX, g.tipX);
                float sy = Mth.lerp(0.26f, g.hiltY, g.tipY);
                long stepSeed = (long) (time * 14.0f) * 31L + level;
                emitMiniBladeLightning(vc, mat, tmp, sx, sy, g.hiltZ, g.tipX, g.tipY, g.tipZ,
                        6, 0.042f * weight, stepSeed, 0xFFFFFF, pRgb, baseA);
            }
            case HYDRO_TIDAL_SURGE, ABYSSAL_MAELSTROM, ZEPHYR_WIND_LANCE -> {
                // Ruban hélicoïdal d'eau ou de vent enroulé autour de la lame
                int steps = 10;
                float prevX = 0, prevY = 0, prevZ = 0;
                for (int k = 0; k <= steps; k++) {
                    float u = 0.28f + 0.72f * (k / (float) steps);
                    float ang = u * 8.5f - time * 5.0f;
                    float rad = 0.036f * weight * Mth.sin(((u - 0.28f) / 0.72f) * (float) Math.PI);
                    float nx = Mth.lerp(u, g.hiltX, g.tipX) - g.dirY * Mth.cos(ang) * rad;
                    float ny = Mth.lerp(u, g.hiltY, g.tipY) + g.dirX * Mth.cos(ang) * rad;
                    float nz = g.tipZ + Mth.sin(ang) * rad;
                    if (k > 0) {
                        WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp, prevX, prevY, prevZ, nx, ny, nz,
                                0.009f * weight, sRgb, pRgb, Math.round(baseA * 0.85f));
                    }
                    prevX = nx; prevY = ny; prevZ = nz;
                }
            }
            case PERMAFROST_RIME -> {
                // Cristaux de givre géométriques sur 3 nœuds du tranchant
                for (int c = 0; c < 3; c++) {
                    float u = 0.42f + c * 0.24f;
                    float cx = Mth.lerp(u, g.hiltX, g.tipX);
                    float cy = Mth.lerp(u, g.hiltY, g.tipY);
                    emitLocalDiamond(vc, mat, tmp, cx, cy, g.tipZ, 0.022f * weight, 0xFFFFFF, pRgb, baseA);
                }
            }
            case SERAPH_SKY_PILLAR, CHRONO_ETERNAL_HALO, VOID_WARP_SPHERE, WARLORD_RALLY_SIGIL -> {
                // Anneau sacré / horloger / dimensionnel / impérial flottant près de la garde ou de la pointe
                float anchorU = (fx == WeaponVisualEffectId.VOID_WARP_SPHERE) ? 0.92f : 0.34f;
                float ax = Mth.lerp(anchorU, g.hiltX, g.tipX);
                float ay = Mth.lerp(anchorU, g.hiltY, g.tipY);
                int segs = (fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) ? 8 : 14;
                emitLocalRingXY(vc, mat, tmp, ax, ay, g.tipZ, 0.072f * weight, 0.009f * weight,
                        time * 1.6f, 0xFFFFFF, pRgb, Math.round(baseA * 0.88f), segs);
            }
            default -> {
                // Scintillement doux sur la pointe pour tous les autres effets (#04, #05, #12, #21, #24, #27, #30)
                float pulse = 0.6f + 0.4f * Mth.sin(time * 4.5f);
                emitLocalDiamond(vc, mat, tmp, g.tipX, g.tipY, g.tipZ, 0.026f * pulse * weight, sRgb, pRgb, Math.round(baseA * 0.8f));
            }
        }
    }

    private static void emitLocalStar(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float r, float rotRad,
                                      int coreRgb, int tipRgb, int alpha) {
        if (alpha <= 5 || r <= 0.004f) return;
        float c = Mth.cos(rotRad) * r;
        float s = Mth.sin(rotRad) * r;
        float w = r * 0.18f;
        WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp, cx - c, cy - s, cz, cx + c, cy + s, cz, w, coreRgb, tipRgb, alpha);
        WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp, cx + s, cy - c, cz, cx - s, cy + c, cz, w, coreRgb, tipRgb, alpha);
    }

    private static void emitLocalDiamond(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float s,
                                         int coreRgb, int edgeRgb, int alpha) {
        if (alpha <= 5 || s <= 0.002f) return;
        WeaponWorldVfxManager.putQuad(vc, mat, tmp,
                cx, cy + s * 1.35f, cz, coreRgb, alpha,
                cx + s, cy, cz, edgeRgb, alpha,
                cx, cy - s * 1.35f, cz, edgeRgb, Math.round(alpha * 0.7f),
                cx - s, cy, cz, coreRgb, alpha);
    }

    private static void emitLocalRingXY(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float radius, float halfW,
                                        float rot, int innerRgb, int outerRgb, int alpha, int segs) {
        if (alpha <= 5) return;
        float rIn = Math.max(0.005f, radius - halfW);
        float rOut = radius + halfW;
        for (int i = 0; i < segs; i++) {
            float a0 = rot + (float) (i * Math.PI * 2.0 / segs);
            float a1 = rot + (float) ((i + 1) * Math.PI * 2.0 / segs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            WeaponWorldVfxManager.putQuad(vc, mat, tmp,
                    cx + c0 * rIn,  cy + s0 * rIn,  cz, innerRgb, alpha,
                    cx + c0 * rOut, cy + s0 * rOut, cz, outerRgb, Math.round(alpha * 0.35f),
                    cx + c1 * rOut, cy + s1 * rOut, cz, outerRgb, Math.round(alpha * 0.35f),
                    cx + c1 * rIn,  cy + s1 * rIn,  cz, innerRgb, alpha);
        }
    }

    private static void emitMiniBladeLightning(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                               float x0, float y0, float z0,
                                               float x1, float y1, float z1,
                                               int segments, float amp, long seed,
                                               int coreRgb, int glowRgb, int alpha) {
        float px = x0, py = y0, pz = z0;
        long h = seed * 0x9E3779B97F4A7C15L;
        for (int i = 1; i <= segments; i++) {
            float f = (float) i / segments;
            float nx = Mth.lerp(f, x0, x1);
            float ny = Mth.lerp(f, y0, y1);
            float nz = Mth.lerp(f, z0, z1);
            if (i < segments) {
                float env = Mth.sin(f * (float) Math.PI) * amp;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nx += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
                h = h * 6364136223846793005L + 1442695040888963407L;
                ny += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env;
                h = h * 6364136223846793005L + 1442695040888963407L;
                nz += (((h >>> 16) & 0xFF) / 127.5f - 1.0f) * env * 0.6f;
            }
            WeaponWorldVfxManager.emitBoltRibbon(vc, mat, tmp, px, py, pz, nx, ny, nz, 0.0085f, coreRgb, glowRgb, alpha);
            px = nx; py = ny; pz = nz;
        }
    }

    // ==================== EXTRACTION & CACHE DES ARÊTES 3D DU MODÈLE ====================

    private static BladeGeometry getOrBuildGeometry(BakedModel model) {
        BladeGeometry cached = MODEL_GEOMETRY_CACHE.get(model);
        if (cached != null) return cached;
        BladeGeometry built = buildGeometry(model);
        MODEL_GEOMETRY_CACHE.put(model, built);
        return built;
    }

    private static BladeGeometry buildGeometry(BakedModel model) {
        RandomSource rand = RandomSource.create(42L);
        List<BakedQuad> rawEdgeQuads = new ArrayList<>(64);
        for (Direction d : EDGE_DIRS) {
            rand.setSeed(42L);
            List<BakedQuad> qlist = model.getQuads(null, d, rand, ModelData.EMPTY, null);
            if (qlist != null && !qlist.isEmpty()) {
                rawEdgeQuads.addAll(qlist);
            }
        }
        // Fallback pour modèles d'armes 3D personnalisés stockant toutes leurs facettes dans null
        if (rawEdgeQuads.isEmpty()) {
            rand.setSeed(42L);
            List<BakedQuad> all = model.getQuads(null, null, rand, ModelData.EMPTY, null);
            if (all != null) {
                for (BakedQuad q : all) {
                    Direction d = q.getDirection();
                    if (d != Direction.NORTH && d != Direction.SOUTH) {
                        rawEdgeQuads.add(q);
                    }
                }
            }
        }

        if (rawEdgeQuads.isEmpty()) {
            return new BladeGeometry(new EdgeQuad[0], 0.15f, 0.15f, 0.5f, 0.85f, 0.85f, 0.5f, 0.707f, 0.707f);
        }

        // Détecte automatiquement le manche (proche du coin inférieur de prise en main) et la pointe opposée
        float minX = 1.0f, minY = 1.0f, maxX = 0.0f, maxY = 0.0f;
        for (BakedQuad q : rawEdgeQuads) {
            int[] v = q.getVertices();
            if (v.length < 32) continue;
            for (int k = 0; k < 4; k++) {
                float x = Float.intBitsToFloat(v[k * 8]);
                float y = Float.intBitsToFloat(v[k * 8 + 1]);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        // Recherche du sommet le plus haut/éloigné comme pointe réelle de l'arme
        float hiltX = minX + 0.06f;
        float hiltY = minY + 0.06f;
        float tipX = maxX, tipY = maxY, tipZ = 0.5f;
        float bestDistSq = -1.0f;
        for (BakedQuad q : rawEdgeQuads) {
            int[] v = q.getVertices();
            if (v.length < 32) continue;
            for (int k = 0; k < 4; k++) {
                float x = Float.intBitsToFloat(v[k * 8]);
                float y = Float.intBitsToFloat(v[k * 8 + 1]);
                float z = Float.intBitsToFloat(v[k * 8 + 2]);
                if (y < minY + (maxY - minY) * 0.45f) continue;
                float dSq = (x - hiltX) * (x - hiltX) + (y - hiltY) * (y - hiltY) * 1.15f;
                if (dSq > bestDistSq) {
                    bestDistSq = dSq;
                    tipX = x;
                    tipY = y;
                    tipZ = z;
                }
            }
        }

        float ax = tipX - hiltX;
        float ay = tipY - hiltY;
        float axisLenSq = Math.max(0.05f, ax * ax + ay * ay);
        float axisLen = Mth.sqrt(axisLenSq);
        float dirX = ax / axisLen;
        float dirY = ay / axisLen;

        List<EdgeQuad> out = new ArrayList<>(rawEdgeQuads.size());
        for (BakedQuad q : rawEdgeQuads) {
            int[] v = q.getVertices();
            if (v.length < 32) continue;
            Direction dir = q.getDirection();
            float nx = dir != null ? dir.getStepX() * 0.0034f : 0.0f;
            float ny = dir != null ? dir.getStepY() * 0.0034f : 0.0f;

            float cx = 0.0f, cy = 0.0f;
            float[] xs = new float[4];
            float[] ys = new float[4];
            float[] zs = new float[4];
            for (int k = 0; k < 4; k++) {
                xs[k] = Float.intBitsToFloat(v[k * 8]) + nx;
                ys[k] = Float.intBitsToFloat(v[k * 8 + 1]) + ny;
                float rawZ = Float.intBitsToFloat(v[k * 8 + 2]);
                zs[k] = rawZ + (rawZ >= 0.5f ? 0.0028f : -0.0028f);
                cx += xs[k];
                cy += ys[k];
            }
            cx *= 0.25f;
            cy *= 0.25f;

            float proj = ((cx - hiltX) * ax + (cy - hiltY) * ay) / axisLenSq;
            float s = Mth.clamp(proj, 0.0f, 1.0f);
            // Exclut la poignée/garde inférieure (s < 0.24) pour n'illuminer QUE la lame / tête d'arme !
            if (s < 0.24f) continue;

            boolean lower = (dir == Direction.DOWN || dir == Direction.EAST);
            out.add(new EdgeQuad(
                    xs[0], ys[0], zs[0],
                    xs[1], ys[1], zs[1],
                    xs[2], ys[2], zs[2],
                    xs[3], ys[3], zs[3],
                    s, lower));
        }

        return new BladeGeometry(out.toArray(new EdgeQuad[0]),
                hiltX, hiltY, 0.5f, tipX, tipY, tipZ, dirX, dirY);
    }

    // Géométrie 3D dédiée aux 3 dents du Trident lorsqu'il est tenu en main
    private static final BladeGeometry TRIDENT_3D_GEOMETRY = buildTrident3DGeometry();

    private static BladeGeometry buildTrident3DGeometry() {
        List<EdgeQuad> prongs = new ArrayList<>(24);
        float[] prongX = { 0.50f, 0.36f, 0.64f };
        for (int p = 0; p < prongX.length; p++) {
            float px = prongX[p];
            float yStart = (p == 0) ? 0.55f : 0.72f;
            float yEnd = (p == 0) ? 1.55f : 1.42f;
            int segs = 6;
            for (int i = 0; i < segs; i++) {
                float u0 = (float) i / segs;
                float u1 = (float) (i + 1) / segs;
                float y0 = Mth.lerp(u0, yStart, yEnd);
                float y1 = Mth.lerp(u1, yStart, yEnd);
                float w = 0.022f;
                float s = Mth.clamp(0.30f + 0.70f * u1, 0.30f, 1.0f);
                prongs.add(new EdgeQuad(
                        px - w, y0, 0.50f,
                        px + w, y0, 0.50f,
                        px + w, y1, 0.50f,
                        px - w, y1, 0.50f,
                        s, p > 0));
            }
        }
        return new BladeGeometry(prongs.toArray(new EdgeQuad[0]),
                0.50f, 0.35f, 0.50f, 0.50f, 1.56f, 0.50f, 0.0f, 1.0f);
    }

    private static int blendRgb(int rgbA, int rgbB, float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        int rA = (rgbA >>> 16) & 0xFF, gA = (rgbA >>> 8) & 0xFF, bA = rgbA & 0xFF;
        int rB = (rgbB >>> 16) & 0xFF, gB = (rgbB >>> 8) & 0xFF, bB = rgbB & 0xFF;
        int r = Math.round(Mth.lerp(k, rA, rB));
        int g = Math.round(Mth.lerp(k, gA, gB));
        int b = Math.round(Mth.lerp(k, bA, bB));
        return (r << 16) | (g << 8) | b;
    }
}
