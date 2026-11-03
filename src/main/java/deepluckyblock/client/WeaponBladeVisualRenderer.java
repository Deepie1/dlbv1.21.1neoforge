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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * T343-VOXEL-BLADE-3D : Rendu sur l'arme tenue en main (1re personne, 3e personne, au sol, cadre)
 * 100 % en <b>vrais mini-pixels 3D cubiques</b> alignés sur la grille de texels 16x16 du modèle :
 * <ul>
 *   <li>Extrait les quads extrudés depuis {@code model.getQuads(null, null, ...)} ({@code unculledFaces})
 *       afin de détecter exactement le contour pixelisé réel de n'importe quelle épée, hache, masse,
 *       faux ({@link HoeItem}), arc ou arbalète.</li>
 *   <li>Gère nativement les coordonnées 3D exactes du {@code TridentModel} tenu en main (3 pointes + hampe).</li>
 *   <li>Chaque texel actif du tranchant et chaque goutte de sang, flammèche, cristal de givre, éclair
 *       ou étoile critique est rendu sous forme de mini-cube 3D à 6 faces ombrées, parfaitement lisible
 *       de profil en 1re personne comme de face en 3e personne (icônes GUI exclues).</li>
 * </ul>
 */
public final class WeaponBladeVisualRenderer {

    private record VoxelNode(float x, float y, float z, float nx, float ny, float progress) {}

    private static final Map<BakedModel, List<VoxelNode>> MODEL_VOXEL_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static List<VoxelNode> trident3dNodes;
    private static RenderType bladeAdditiveType;
    private static RenderType bladeTranslucentType;

    private static boolean renderingOverlay = false;

    private WeaponBladeVisualRenderer() {}

    public static RenderType getBladeAdditiveRenderType() {
        if (bladeAdditiveType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            bladeAdditiveType = RenderType.create(
                    "dlb_blade_voxel_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    4096,
                    false,
                    false,
                    state);
        }
        return bladeAdditiveType;
    }

    public static RenderType getBladeTranslucentRenderType() {
        if (bladeTranslucentType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            bladeTranslucentType = RenderType.create(
                    "dlb_blade_voxel_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    4096,
                    false,
                    true,
                    state);
        }
        return bladeTranslucentType;
    }

    public static void renderWeaponOverlays(ItemStack stack, ItemDisplayContext displayContext,
                                            boolean leftHand, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            BakedModel model) {
        if (renderingOverlay || stack == null || stack.isEmpty()) return;
        // Icônes d'inventaire (GUI) laissées propres comme demandé
        if (displayContext == ItemDisplayContext.GUI) return;

        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        if (layers.isEmpty()) return;

        boolean custom3dTrident = stack.is(Items.TRIDENT)
                && displayContext != ItemDisplayContext.GROUND
                && displayContext != ItemDisplayContext.FIXED;

        List<VoxelNode> nodes = custom3dTrident
                ? getTrident3dVoxelNodes()
                : getOrBuildModelVoxelNodes(stack, model);
        if (nodes.isEmpty()) return;

        // T349-IDLE-FILTER : les « pixels lumineux constants » collés sur l'item tenu sont supprimés.
        // Seuls les éclats d'ACTION (alpha élevé : reflet qui parcourt la lame, charge d'arc, goutte qui
        // tombe...) restent visibles. Hors action, on ne garde donc que les accents animés.
        boolean armActive = isHeldWeaponActive();
        WeaponWorldVfxManager.setAlphaFloor(armActive ? 0 : 172);
        WeaponWorldVfxManager.beginFrame(WeaponWorldVfxManager.smoothTimeSeconds());

        renderingOverlay = true;
        try {
            float time = WeaponWorldVfxManager.smoothTimeSeconds();
            Matrix4f mat = poseStack.last().pose();
            Vector3f tmp = new Vector3f();

            for (int i = 0; i < layers.size(); i++) {
                WeaponVisualEffectId.ResolvedLayer layer = layers.get(i);
                WeaponVisualEffectId fx = layer.effect();
                VertexConsumer vc = bufferSource.getBuffer(
                        fx.additive
                                ? getBladeAdditiveRenderType()
                                : getBladeTranslucentRenderType());

                float alphaMul = (i == 0) ? 1.0f : 0.78f;
                renderBladeVoxelEffect(vc, mat, tmp, stack, nodes, custom3dTrident,
                        fx, layer.level(), time + i * 1.35f, alphaMul);
            }
        } catch (Throwable ignored) {
            // Tolérance totale avec ImmediatelyFast / Sodium / Iris
        } finally {
            WeaponWorldVfxManager.setAlphaFloor(0);
            renderingOverlay = false;
        }
    }

    /**
     * T349-IDLE-FILTER : vrai quand l'arme est réellement « en action » (coup en cours, arc tendu,
     * arbalète chargée, trident lancé). Dans ce cas seulement, l'épaisse couche de pixels du tranchant
     * est autorisée : sinon on se contente du reflet animé.
     */
    private static boolean isHeldWeaponActive() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return false;
        if (player.swinging || player.attackAnim > 0.02f) return true;
        if (player.isUsingItem()) return true;
        return player.getTicksUsingItem() > 0;
    }

    private static void renderBladeVoxelEffect(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                               ItemStack stack, List<VoxelNode> nodes, boolean is3dTrident,
                                               WeaponVisualEffectId fx, int level, float time, float layerMul) {
        int count = nodes.size();
        // T350-SMOOTH-COLOR : couleurs de famille élémentaire lissées dans le temps (dégradé
        // progressif, jamais de changement brutal d'un coup à l'autre).
        int pRgb = WeaponWorldVfxManager.smoothBladeColor(fx);
        int sRgb = WeaponWorldVfxManager.smoothBladeColor2(fx);
        int highlightRgb = WeaponWorldVfxManager.smoothColor(2000 + fx.id, fx.highlightRgb);

        // T374-PALETTE-LOCK : primaire, secondaire et éclat restent dans le même rôle de chaque famille.
        java.util.List<deepluckyblock.enchantments.DeepElement> elements =
                WeaponVisualEffectId.elementsOf(stack);
        if (elements.size() > 1) {
            int gradPrimary = WeaponWorldVfxManager.gradientColorFor(stack, time, 0);
            int gradSecondary = WeaponWorldVfxManager.gradientColorFor(stack, time, 1);
            int gradHighlight = WeaponWorldVfxManager.gradientColorFor(stack, time, 2);
            pRgb = gradPrimary;
            sRgb = gradSecondary;
            highlightRgb = gradHighlight;
        }
        final int white = highlightRgb;
        float lvlBoost = 1.0f + Math.min(4, Math.max(0, level - 1)) * 0.08f;

        // Demi-épaisseur Z chevauchant les deux faces de la lame 2D (0.46875..0.53125 -> 0.461..0.539)
        // T358-PIXEL-GRID-HALF : la GRILLE des pixels animés est divisée par 2 sur les armes
        // (0.03125 -> 0.015625, soit des cubes deux fois plus fins), la densité de texels est
        // doublée plus bas pour que la lame reste couverte. L'épaisseur Z est conservée pour
        // que les mini-cubes continuent de chevaucher les DEUX faces de la lame 2D.
        float hz = is3dTrident ? 0.024f : 0.039f;
        float hxy = is3dTrident ? 0.011f : 0.015625f; // 1/64 de largeur : un demi mini-pixel d'origine

        // Détection tir à l'arc tendu ou arbalète chargée -> concentration de pixels 3D sur la pointe !
        float rangedCharge = computeRangedCharge(stack);

        // T358-PIXEL-MIX : couche MÉLANGÉE de pixels encore plus petits, à taille aléatoire
        // (nano 0.16 -> petit 0.90 de la grille), par-dessus les pixels réguliers : la lame
        // fourmille de grains fins au lieu d'une grille uniforme. Taille tirée par un hash
        // stable : aucun scintillement d'une frame à l'autre.
        emitBladeMicroPixels(vc, mat, tmp, nodes, hxy, hz, pRgb, sRgb, white, time, layerMul);

        switch (fx) {
            case RAZOR_GLEAM -> {
                // #01 Éclat d'affûtage pixel-art parcourant chaque texel du tranchant + étoile 3D en pointe
                float head = (time * 1.45f) % 1.35f;
                for (int i = 0; i < count; i++) {
                    VoxelNode n = nodes.get(i);
                    float d = Math.abs(n.progress - head);
                    float shine = Math.max(0.0f, 1.0f - d * 5.2f);
                    int a = Math.round((75.0f + 175.0f * shine * shine) * layerMul);
                    int rgb = shine > 0.45f ? white : sRgb;
                    float s = hxy * (0.82f + 0.35f * shine) * pixelVariance(i, time);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * 0.014f, n.y + n.ny * 0.014f, n.z, s, s, hz, rgb, a);
                }
                VoxelNode tip = nodes.get(count - 1);
                float pulse = 0.55f + 0.45f * Mth.sin(time * 6.5f);
                WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp,
                        tip.x + tip.nx * 0.03f, tip.y + tip.ny * 0.03f, tip.z,
                        0.035f, 2, white, sRgb, Math.round(220 * pulse * layerMul));
            }

            case SUPREME_PRISM_EDGE -> {
                // #02 Double vague de mini-cubes diamant & or + prisme 3D au bout
                float w1 = (time * 1.75f) % 1.0f;
                float w2 = (w1 + 0.5f) % 1.0f;
                for (int i = 0; i < count; i++) {
                    VoxelNode n = nodes.get(i);
                    float d1 = Math.min(Math.abs(n.progress - w1), 1.0f - Math.abs(n.progress - w1));
                    float d2 = Math.min(Math.abs(n.progress - w2), 1.0f - Math.abs(n.progress - w2));
                    boolean first = d1 <= d2;
                    float glow = Math.max(0.0f, 1.0f - Math.min(d1, d2) * 5.0f);
                    int rgb = glow > 0.65f ? white : (first ? pRgb : sRgb);
                    int a = Math.round((115.0f + 135.0f * glow) * layerMul);
                    float s = hxy * (0.90f + 0.32f * glow) * pixelVariance(i, time);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * 0.018f, n.y + n.ny * 0.018f, n.z, s, s, hz, rgb, a);
                }
                VoxelNode tip = nodes.get(count - 1);
                WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp,
                        tip.x + 0.03f, tip.y + 0.03f, tip.z, 0.038f, 2, white, sRgb, Math.round(235 * layerMul));
            }

            case SPECTRAL_REACH -> {
                // #03 Pointe fantôme en escalier de mini-pixels 3D prolongeant l'allonge de l'arme
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.88f, hz, pRgb, sRgb, time * 2.8f, time, 145, layerMul);
                VoxelNode tip = nodes.get(count - 1);
                float ext = 0.05f + 0.04f * (0.5f + 0.5f * Mth.sin(time * 4.5f));
                for (int k = 1; k <= 4; k++) {
                    float f = k * ext * 0.65f;
                    int a = Math.round((220 - k * 42) * layerMul);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            tip.x + f, tip.y + f, tip.z, hxy * (1.05f - k * 0.14f), hxy * (1.05f - k * 0.14f), hz * 0.85f,
                            (k == 1) ? white : pRgb, a);
                }
            }

            case TWIN_ECHO_SLASH -> {
                // #04 Seconde rangée fantôme de mini-cubes décalée d'un pixel à côté du tranchant !
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.85f, hz, pRgb, sRgb, time * 4.0f, time, 155, layerMul);
                float off = 0.0625f * (0.8f + 0.4f * Mth.sin(time * 6.0f));
                for (int i = 0; i < count; i += 2) {
                    VoxelNode n = nodes.get(i);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * off, n.y + n.ny * off, n.z,
                            hxy * 0.72f, hxy * 0.72f, hz * 0.72f, sRgb, Math.round(150 * layerMul));
                }
            }

            case DRIPPING_BLOOD_EDGE, CARNAGE_BERSERK -> {
                // #09 / #12 Tranchant taché de pixels écarlates + gouttes de sang cubiques 3D tombant sous la gravité !
                boolean berserk = (fx == WeaponVisualEffectId.CARNAGE_BERSERK);
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * (berserk ? 1.08f : 0.92f), hz, pRgb, sRgb, time * 2.4f, time, berserk ? 205 : 175, layerMul);

                int drops = berserk ? 6 : 4;
                for (int d = 0; d < drops; d++) {
                    int idx = Math.min(count - 1, Math.max(1, ((d * 5 + 2) % count)));
                    VoxelNode anchor = nodes.get(idx);
                    float cycle = (time * (berserk ? 1.55f : 1.05f) + d * 0.27f) % 1.0f;
                    // Phase 0..0.35 : le pixel de sang gonfle sur le tranchant ; 0.35..1.0 : tombe par paliers !
                    float dropFall = (cycle < 0.35f) ? 0.0f : (cycle - 0.35f) / 0.65f;
                    float dy = -WeaponWorldVfxManager.quantize(dropFall * dropFall * 0.32f, 0.03125f);
                    float sz = hxy * (cycle < 0.35f ? (0.55f + cycle * 1.4f) : (1.04f - dropFall * 0.45f)) * pixelVariance(idx, time);
                    int alpha = Math.round(235.0f * (1.0f - dropFall * 0.75f) * layerMul);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            anchor.x + anchor.nx * 0.025f, anchor.y + dy, anchor.z,
                            sz, sz, sz, (d & 1) == 0 ? sRgb : pRgb, alpha);
                }
            }

            case CRIMSON_SIPHON -> {
                // #10 Mini-cubes de sang aspirés de la pointe vers la garde !
                float flow = 1.0f - ((time * 1.6f) % 1.0f);
                for (int i = 0; i < count; i++) {
                    VoxelNode n = nodes.get(i);
                    float d = Math.min(Math.abs(n.progress - flow), 1.0f - Math.abs(n.progress - flow));
                    float pulse = Math.max(0.0f, 1.0f - d * 4.5f);
                    float s = hxy * (0.78f + 0.45f * pulse) * pixelVariance(i, time);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * 0.018f, n.y + n.ny * 0.018f, n.z,
                            s, s, hz, pulse > 0.4f ? sRgb : pRgb, Math.round((130 + 115 * pulse) * layerMul));
                }
            }

            case VITAL_TRANSFUSION -> {
                // #11 Mini-croix médicales 3D rubis & or s'élevant le long de l'arme
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.85f, hz, pRgb, sRgb, time * 2.2f, time, 155, layerMul);
                for (int c = 0; c < 3; c++) {
                    float cyc = (time * 0.95f + c * 0.33f) % 1.0f;
                    VoxelNode n = nodes.get(Math.min(count - 1, Math.round(cyc * (count - 1))));
                    WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp,
                            n.x + n.nx * 0.045f, n.y + 0.04f, n.z,
                            0.028f, 1, sRgb, pRgb, Math.round(220 * (1.0f - cyc * 0.5f) * layerMul));
                }
            }

            case MOLTEN_FIRE_WAVE, SOUL_FIRE_CRESCENT, BRIMSTONE_METEOR -> {
                // #13 / #14 / #15 Braises et flammèches cubiques 3D s'élevant depuis chaque texel !
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.98f, hz, pRgb, sRgb, time * 4.2f, time, 195, layerMul);
                for (int e = 0; e < 6; e++) {
                    VoxelNode base = nodes.get((e * 3 + 1) % count);
                    float cyc = (time * 1.65f + e * 0.17f) % 1.0f;
                    float rise = WeaponWorldVfxManager.quantize(cyc * 0.22f, 0.03125f);
                    float sway = ((e & 1) == 0 ? 1 : -1) * 0.03125f * (cyc > 0.5f ? 1 : 0);
                    float sz = hxy * (1.05f - cyc * 0.55f) * lvlBoost * pixelVariance(e, time);
                    int rgb = cyc < 0.3f ? white : (cyc < 0.65f ? sRgb : pRgb);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            base.x + sway, base.y + rise, base.z,
                            sz, sz, sz, rgb, Math.round(235 * (1.0f - cyc) * layerMul));
                }
            }

            case CHAIN_VOLT_ARC, OVERCLOCK_CYBER_X -> {
                // #16 / #08 Éclairs crénelés en mini-cubes 3D sautant de la garde à la pointe !
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.85f, hz, pRgb, sRgb, time * 6.5f, time, 165, layerMul);
                VoxelNode start = nodes.get(0);
                VoxelNode end = nodes.get(count - 1);
                long boltSeed = (long) (time * 14.0f);
                WeaponWorldVfxManager.emitVoxelLightning(vc, mat, tmp,
                        start.x, start.y, start.z, end.x, end.y, end.z,
                        5, 0.065f, boltSeed, white, pRgb, Math.round(235 * layerMul), 0.022f);
            }

            case PERMAFROST_RIME -> {
                // #19 Dents de glace cubiques 3D hérissant le tranchant + flocons pixelisés
                for (int i = 0; i < count; i++) {
                    VoxelNode n = nodes.get(i);
                    float tooth = ((i & 1) == 0) ? 1.28f : 0.82f;
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * 0.024f * tooth, n.y + n.ny * 0.024f * tooth, n.z,
                            hxy * tooth, hxy * tooth, hz, (i & 1) == 0 ? sRgb : pRgb, Math.round(205 * layerMul));
                }
            }

            case VENOM_BRIAR_FANG -> {
                // #20 Gouttes cubiques de venin acide vert néon suintant de la lame
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.90f, hz, pRgb, sRgb, time * 2.6f, time, 175, layerMul);
                for (int v = 0; v < 4; v++) {
                    VoxelNode anchor = nodes.get((v * 4 + 2) % count);
                    float cyc = (time * 1.15f + v * 0.25f) % 1.0f;
                    float dy = -WeaponWorldVfxManager.quantize(cyc * cyc * 0.26f, 0.03125f);
                    float sz = hxy * (0.95f - cyc * 0.4f) * pixelVariance(v, time);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            anchor.x + anchor.nx * 0.02f, anchor.y + dy, anchor.z,
                            sz, sz, sz, (v & 1) == 0 ? pRgb : sRgb, Math.round(225 * (1.0f - cyc * 0.7f) * layerMul));
                }
            }

            case GOLDEN_CRIT_STAR, FOCUSED_BULLSEYE, SERAPH_SKY_PILLAR, ASTRAL_STAR_LANCE -> {
                // #06 / #07 / #23 / #25 Étoiles critiques pixel-art 3D scintillant le long du tranchant
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.90f, hz, pRgb, sRgb, time * 3.2f, time, 175, layerMul);
                for (int s = 0; s < 3; s++) {
                    int idx = Math.min(count - 1, ((s + 1) * count) / 3 - 1);
                    VoxelNode n = nodes.get(Math.max(0, idx));
                    float tw = 0.5f + 0.5f * Mth.sin(time * 5.5f + s * 2.1f);
                    if (tw > 0.25f) {
                        WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp,
                                n.x + n.nx * 0.035f, n.y + n.ny * 0.035f, n.z,
                                0.028f * tw, 2, white, pRgb, Math.round(235 * tw * layerMul));
                    }
                }
            }

            case DRAGON_CHAOS_PRISM -> {
                // #27 Prisme du Vide : le dégradé suit uniquement les rôles validés de la famille.
                for (int i = 0; i < count; i++) {
                    VoxelNode n = nodes.get(i);
                    float phase = (n.progress * 0.7f + time * 0.12f) % 1.0f;
                    int rgb = WeaponWorldVfxManager.rampRgb(phase, sRgb, pRgb, white, pRgb);
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            n.x + n.nx * 0.02f, n.y + n.ny * 0.02f, n.z,
                            hxy * 1.02f, hxy * 1.02f, hz, rgb, Math.round(210 * layerMul));
                }
            }

            case ANNIHILATING_ARC -> {
                // #30 Couronne de plasma solaire en gros mini-cubes incandescents + étoile 3D en pointe
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 1.15f, hz * 1.12f, white, pRgb, time * 5.2f, time, 225, layerMul);
                VoxelNode tip = nodes.get(count - 1);
                WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp,
                        tip.x + 0.03f, tip.y + 0.03f, tip.z, 0.042f, 3, white, pRgb, Math.round(245 * layerMul));
            }

            default -> {
                // Tous les autres effets (#05, #17, #18, #21, #22, #24, #26, #28, #29) :
                // Couronne crénelée de mini-cubes 3D + satellites cubiques en orbite autour de la pointe !
                renderSteppedRimCubes(vc, mat, tmp, nodes, hxy * 0.95f, hz, pRgb, sRgb, time * 3.4f, time, 185, layerMul);
                VoxelNode mid = nodes.get(count / 2);
                for (int o = 0; o < 3; o++) {
                    float a = time * 3.8f + o * 2.094f;
                    float ox = WeaponWorldVfxManager.quantize(Mth.cos(a) * 0.08f, 0.03125f);
                    float oy = WeaponWorldVfxManager.quantize(Mth.sin(a) * 0.08f, 0.03125f);
                    float oz = Mth.sin(a * 1.3f) * 0.07f;
                    WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                            mid.x + ox, mid.y + oy, mid.z + oz,
                            0.026f, 0.026f, 0.026f, (o == 0) ? white : sRgb, Math.round(220 * layerMul));
                }
            }
        }

        // Si arc bandé ou arbalète chargée : convergence de mini-pixels 3D sur la pointe de la flèche !
        if (rangedCharge > 0.05f) {
            float cx = 0.68f, cy = 0.68f, cz = 0.50f;
            WeaponWorldVfxManager.emitPixelCross3D(vc, mat, tmp, cx, cy, cz,
                    0.034f * (0.6f + 0.6f * rangedCharge), 2,
                    white, pRgb, Math.round(240 * rangedCharge * layerMul));
        }
    }

    private static float computeRangedCharge(ItemStack stack) {
        if (!(stack.getItem() instanceof ProjectileWeaponItem)) return 0.0f;
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return 1.0f;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem() == stack) {
            return Mth.clamp(mc.player.getTicksUsingItem() / 20.0f, 0.15f, 1.0f);
        }
        return 0.0f;
    }

    private static void renderSteppedRimCubes(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                              List<VoxelNode> nodes, float hxy, float hz,
                                              int pRgb, int sRgb, float wavePhase, float time,
                                              int baseAlpha, float layerMul) {
        int count = nodes.size();
        for (int i = 0; i < count; i++) {
            VoxelNode n = nodes.get(i);
            float w = 0.5f + 0.5f * Mth.sin(n.progress * 9.0f - wavePhase);
            int rgb = w > 0.72f ? WeaponWorldVfxManager.mixRgb(pRgb, sRgb, 0.35f)
                    : (w > 0.35f ? pRgb : sRgb);
            int a = Math.round((baseAlpha + 55.0f * w) * layerMul);
            float s = hxy * (0.82f + 0.30f * w) * pixelVariance(i, time);
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                    n.x + n.nx * 0.016f, n.y + n.ny * 0.016f, n.z, s, s, hz, rgb, a);
        }
    }

    // ==================== EXTRACTION EXACTE DE LA GRILLE 16x16 DE TEXELS DU MODÈLE ====================

    private static List<VoxelNode> getOrBuildModelVoxelNodes(ItemStack stack, BakedModel model) {
        if (model == null) return getFallbackBladeVoxels(stack);
        List<VoxelNode> cached = MODEL_VOXEL_CACHE.get(model);
        if (cached != null) return cached;

        List<VoxelNode> built = extractModelBoundaryTexels(stack, model);
        if (built.isEmpty()) {
            built = getFallbackBladeVoxels(stack);
        }
        MODEL_VOXEL_CACHE.put(model, built);
        return built;
    }

    private static List<VoxelNode> extractModelBoundaryTexels(ItemStack stack, BakedModel model) {
        RandomSource rand = RandomSource.create(42L);
        List<BakedQuad> allQuads = new ArrayList<>();
        try {
            allQuads.addAll(model.getQuads(null, null, rand, ModelData.EMPTY, null));
            for (Direction d : Direction.values()) {
                rand.setSeed(42L);
                allQuads.addAll(model.getQuads(null, d, rand, ModelData.EMPTY, null));
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        if (allQuads.isEmpty()) return Collections.emptyList();

        boolean isHeadWeapon = stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof HoeItem;
        boolean isRanged = stack.getItem() instanceof ProjectileWeaponItem;

        boolean[][] seenTexel = new boolean[16][16];
        float[][] normX = new float[16][16];
        float[][] normY = new float[16][16];

        for (BakedQuad q : allQuads) {
            Direction dir = q.getDirection();
            if (dir == Direction.NORTH || dir == Direction.SOUTH || dir == null) continue;

            int[] v = q.getVertices();
            if (v.length < 32) continue;

            float sx = 0, sy = 0, sz = 0;
            for (int k = 0; k < 4; k++) {
                int base = k * 8;
                sx += Float.intBitsToFloat(v[base]);
                sy += Float.intBitsToFloat(v[base + 1]);
                sz += Float.intBitsToFloat(v[base + 2]);
            }
            float cx = sx * 0.25f;
            float cy = sy * 0.25f;
            float cz = sz * 0.25f;

            // Ne garde que les tranches d'extrusion de texels autour du plan 0.50
            if (cz < 0.35f || cz > 0.65f) continue;

            // Recule d'un demi-texel vers l'intérieur du pixel plein
            float tx = cx - dir.getStepX() * 0.015f;
            float ty = cy - dir.getStepY() * 0.015f;
            int px = Mth.clamp((int) Math.floor(tx * 16.0f), 0, 15);
            int py = Mth.clamp((int) Math.floor(ty * 16.0f), 0, 15);

            // Filtre le manche en bois pour ne garder que la lame / tête métallique / branches d'arc
            if (isHeadWeapon) {
                if (py < 7 && px < 8) continue;
            } else if (isRanged) {
                if (px + py < 5) continue;
            } else {
                // Épée / dague : exclut le pommeau et la poignée basse-gauche
                if (px + py < 8 || (px < 4 && py < 6)) continue;
            }

            seenTexel[px][py] = true;
            normX[px][py] += dir.getStepX();
            normY[px][py] += dir.getStepY();
        }

        record RawTexel(int px, int py, float nx, float ny, float sortKey) {}
        List<RawTexel> raw = new ArrayList<>(32);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                if (!seenTexel[x][y]) continue;
                float nx = normX[x][y];
                float ny = normY[x][y];
                float len = Mth.sqrt(nx * nx + ny * ny);
                if (len > 0.001f) {
                    nx /= len;
                    ny /= len;
                }
                float key = isHeadWeapon ? (y * 1.2f + x * 0.8f) : (x + y);
                raw.add(new RawTexel(x, y, nx, ny, key));
            }
        }
        if (raw.size() < 3) return Collections.emptyList();

        raw.sort(Comparator.comparingDouble(RawTexel::sortKey));

        // T358-PIXEL-GRID-HALF : grille divisée par 2 -> DEUX FOIS plus de mini-cubes (44),
        // chacun deux fois plus petit : même couverture de lame, rendu bien plus fin.
        int stride = Math.max(1, raw.size() / 44);
        List<VoxelNode> result = new ArrayList<>(48);
        for (int i = 0; i < raw.size(); i += stride) {
            RawTexel rt = raw.get(i);
            float cx = (rt.px + 0.5f) / 16.0f;
            float cy = (rt.py + 0.5f) / 16.0f;
            float prog = (raw.size() <= 1) ? 1.0f : (float) i / (raw.size() - 1);
            result.add(new VoxelNode(cx, cy, 0.50f, rt.nx, rt.ny, prog));
        }
        // S'assure que le tout dernier texel de la pointe est toujours inclus !
        RawTexel last = raw.get(raw.size() - 1);
        float lx = (last.px + 0.5f) / 16.0f;
        float ly = (last.py + 0.5f) / 16.0f;
        VoxelNode tail = result.get(result.size() - 1);
        if (Math.abs(tail.x - lx) > 0.01f || Math.abs(tail.y - ly) > 0.01f) {
            result.add(new VoxelNode(lx, ly, 0.50f, last.nx, last.ny, 1.0f));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Coordonnées exactes vérifiées du {@code TridentModel} vanilla rendu via
     * {@code BlockEntityWithoutLevelRenderer} (juste avant {@code ItemRenderer.render} {@code popPose()}):
     * 3 pointes en {@code x = 0.0f, -0.125f, +0.125f}, {@code y = -0.1875f..+0.20f}, {@code z = 0.0f}.
     */
    private static List<VoxelNode> getTrident3dVoxelNodes() {
        if (trident3dNodes != null) return trident3dNodes;
        List<VoxelNode> list = new ArrayList<>(48);
        // T358-PIXEL-GRID-HALF : 12 points par fourche au lieu de 6 (grille divisée par 2).
        // Fourche gauche (-0.125) et fourche droite (+0.125) : y = -0.0625 .. +0.1875
        for (int i = 0; i <= 11; i++) {
            float u = i / 11.0f;
            float y = Mth.lerp(u, -0.0625f, 0.1875f);
            list.add(new VoxelNode(-0.125f, y, 0.0f, -1.0f, 0.3f, 0.30f + 0.58f * u));
            list.add(new VoxelNode(0.125f, y, 0.0f, 1.0f, 0.3f, 0.30f + 0.58f * u));
        }
        // Collet supérieur + pointe centrale (dernier noeud = 1.0f au sommet exact y = +0.25f !)
        // T358-PIXEL-GRID-HALF : 20 points au lieu de 10 sur l'axe central.
        for (int i = 0; i <= 19; i++) {
            float u = i / 19.0f;
            float y = Mth.lerp(u, -0.30f, 0.25f);
            list.add(new VoxelNode(0.0f, y, 0.0f, 0.0f, 1.0f, u));
        }
        list.sort(Comparator.comparingDouble(n -> n.progress));
        trident3dNodes = Collections.unmodifiableList(list);
        return trident3dNodes;
    }

    private static List<VoxelNode> getFallbackBladeVoxels(ItemStack stack) {
        // T358-PIXEL-GRID-HALF : un point tous les DEMI-texel (21 points au lieu de 11).
        List<VoxelNode> list = new ArrayList<>(24);
        for (float p = 5.0f; p <= 15.0f; p += 0.5f) {
            float c = (p + 0.25f) / 16.0f;
            float u = (p - 5.0f) / 10.0f;
            list.add(new VoxelNode(c, c, 0.50f, -0.707f, 0.707f, u));
        }
        return Collections.unmodifiableList(list);
    }

    // ==================== T358-PIXEL-GRID : GRILLE / 2 ET VARIATIONS ====================

    /**
     * T358-PIXEL-GRID-VARIATION : facteur de taille (stable) d'un mini-cube de lame.
     *
     * <p>La grille d'origine avait des cubes tous identiques, ce qui faisait « nappe » uniforme.
     * Ici chaque texel reçoit sa propre taille : ~30 % de pixels <b>bien plus petits</b>
     * (0.42–0.69), ~50 % de <b>normaux</b> (0.80–1.15), ~20 % de <b>plus gros</b> (1.10–1.42).
     * Le facteur est dérivé d'un hash de l'index : il ne change donc JAMAIS d'une frame à
     * l'autre (aucun scintillement, règle T351), seulement d'un texel à l'autre, et il respire
     * très lentement (période ~4 s).</p>
     */
    private static float pixelVariance(int index, float time) {
        int h = index * 0x9E3779B1;
        h ^= (h >>> 15);
        h *= 0x85EBCA6B;
        h ^= (h >>> 13);
        float r = (h & 0xFFFF) / 65535.0f;
        float size;
        if (r < 0.30f) {
            size = 0.42f + r * 0.9f;                 // pixels nettement plus petits
        } else if (r < 0.80f) {
            size = 0.80f + (r - 0.30f) * 0.7f;       // taille normale
        } else {
            size = 1.10f + (r - 0.80f) * 1.6f;       // quelques gros pixels
        }
        return size * (0.94f + 0.06f * Mth.sin(time * 1.5f + index * 0.9f));
    }

    /**
     * T358-PIXEL-MIX : couche de <b>micro-pixels</b> à taille aléatoire, mélangée aux pixels
     * réguliers de la lame.
     *
     * <p>Trois familles : nano (0.16–0.39 de la grille), micro (0.40–0.60) et petit (0.60–0.90).
     * Les positions et les tailles viennent d'un hash stable : le mélange reste le même image
     * après image (pas de fourmillement nerveux), seule l'opacité ondule très lentement.</p>
     */
    private static void emitBladeMicroPixels(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                             List<VoxelNode> nodes, float hxy, float hz,
                                             int pRgb, int sRgb, int white, float time, float layerMul) {
        int count = nodes.size();
        if (count < 3) return;
        int passes = Math.min(18, 8 + count / 5);
        for (int k = 0; k < passes; k++) {
            int h = (k + 1) * 0x27D4EB2F;
            int idx = Math.floorMod((h >>> 9) ^ (k * 31), count);
            VoxelNode n = nodes.get(idx);
            float r1 = ((h >>> 3) & 0xFF) / 255.0f;
            float r2 = ((h >>> 11) & 0xFF) / 255.0f;
            float r3 = ((h >>> 19) & 0xFF) / 255.0f;
            float sizeFactor;
            if (r1 < 0.55f) {
                sizeFactor = 0.16f + r1 * 0.42f;                  // nano-pixel
            } else if (r1 < 0.85f) {
                sizeFactor = 0.40f + (r1 - 0.55f) * 0.67f;        // micro-pixel
            } else {
                sizeFactor = 0.60f + (r1 - 0.85f) * 2.0f;         // petit pixel
            }
            float s = hxy * sizeFactor;
            float ox = (r2 - 0.5f) * 0.045f;
            float oy = (r3 - 0.5f) * 0.045f;
            int rgb = ((k & 3) == 0) ? white : (((k & 1) == 0) ? pRgb : sRgb);
            int a = Math.round((55.0f + 120.0f * r1) * layerMul);
            WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp,
                    n.x + ox, n.y + oy, n.z, s, s, hz * 0.80f, rgb, a);
        }
    }

}
