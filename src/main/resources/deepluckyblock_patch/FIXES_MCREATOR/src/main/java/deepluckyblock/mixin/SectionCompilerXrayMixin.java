package deepluckyblock.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import deepluckyblock.client.MinersEyeClientXray;
import deepluckyblock.client.MinersEyeVertexConsumers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * T341-MINERS-EYE-XRAY : Compilation semi-transparente locale dans {@link SectionCompiler} :
 * <ul>
 *   <li>Désactive l'occultation {@link VisGraph#setOpaque(BlockPos)} dans la sphère de {@code miners_eye}
 *       afin de laisser voir à travers jusqu'à 30 blocs de roche pleine.</li>
 *   <li>Ignore les blocs rocheux totalement enterrés (~95 % des blocs -> gain massif de FPS), et redirige
 *       la paroi de grotte/sol vers {@link RenderType#translucent()} à ~24 % d'opacité tout en conservant
 *       le {@code renderType} d'origine pour {@code BakedModel#getQuads}.</li>
 *   <li>Rehausse l'éclairage minimal des minerais et liquides enfouis pour qu'ils restent nets et lisibles
 *       même à Y = -54 sans torche.</li>
 * </ul>
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerXrayMixin {

    @Unique
    private static final String DLB_COMPILE_DESC =
            "compile(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
            + "Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
            + "Ljava/util/List;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Unique
    private static final ThreadLocal<Map<RenderType, BufferBuilder>> DLB_TL_MAP = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<SectionBufferBuilderPack> DLB_TL_PACK = new ThreadLocal<>();

    @Shadow
    private BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> map,
                                          SectionBufferBuilderPack pack,
                                          RenderType renderType) {
        throw new AssertionError();
    }

    @Inject(method = DLB_COMPILE_DESC, at = @At("HEAD"))
    private void dlb$onCompileStart(SectionPos sectionPos, RenderChunkRegion region, VertexSorting sorting,
                                    SectionBufferBuilderPack pack,
                                    List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
                                    CallbackInfoReturnable<SectionCompiler.Results> cir) {
        MinersEyeClientXray.markVanillaSectionCompilerSeen();
        DLB_TL_PACK.set(pack);
    }

    @Inject(method = DLB_COMPILE_DESC, at = @At("RETURN"))
    private void dlb$onCompileEnd(SectionPos sectionPos, RenderChunkRegion region, VertexSorting sorting,
                                  SectionBufferBuilderPack pack,
                                  List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
                                  CallbackInfoReturnable<SectionCompiler.Results> cir) {
        DLB_TL_MAP.remove();
        DLB_TL_PACK.remove();
    }

    @Inject(method = "getOrBeginLayer", at = @At("HEAD"))
    private void dlb$captureLayerMap(Map<RenderType, BufferBuilder> map,
                                     SectionBufferBuilderPack pack,
                                     RenderType renderType,
                                     CallbackInfoReturnable<BufferBuilder> cir) {
        DLB_TL_MAP.set(map);
        DLB_TL_PACK.set(pack);
    }

    @Redirect(
            method = DLB_COMPILE_DESC,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void dlb$filterVisGraphOpaque(VisGraph visGraph, BlockPos pos) {
        if (MinersEyeClientXray.isVanillaMeshXrayActive() && MinersEyeClientXray.isPosInSphere(pos)) {
            return;
        }
        visGraph.setOpaque(pos);
    }

    @Redirect(
            method = DLB_COMPILE_DESC,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid("
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/material/FluidState;)V"
            )
    )
    private void dlb$renderXrayLiquid(BlockRenderDispatcher dispatcher, BlockPos pos,
                                      BlockAndTintGetter region, VertexConsumer consumer,
                                      BlockState state, FluidState fluidState) {
        if (MinersEyeClientXray.isVanillaMeshXrayActive() && MinersEyeClientXray.isPosInSphere(pos)) {
            VertexConsumer lit = MinersEyeVertexConsumers.brighten(consumer, 13);
            dispatcher.renderLiquid(pos, region, lit, state, fluidState);
            return;
        }
        dispatcher.renderLiquid(pos, region, consumer, state, fluidState);
    }

    @Redirect(
            method = DLB_COMPILE_DESC,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched("
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                            + "Z"
                            + "Lnet/minecraft/util/RandomSource;"
                            + "Lnet/neoforged/neoforge/client/model/data/ModelData;"
                            + "Lnet/minecraft/client/renderer/RenderType;)V"
            )
    )
    private void dlb$renderXrayBlock(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos,
                                     BlockAndTintGetter region, PoseStack poseStack, VertexConsumer consumer,
                                     boolean checkSides, RandomSource random, ModelData modelData,
                                     RenderType renderType) {
        if (!MinersEyeClientXray.isVanillaMeshXrayActive() || !MinersEyeClientXray.isPosInSphere(pos)) {
            dispatcher.renderBatched(state, pos, region, poseStack, consumer, checkSides, random, modelData, renderType);
            return;
        }

        // Minerais et blocs porteurs de fluide : 100 % visibles + éclairage minimal net sous terre
        if (MinersEyeClientXray.isPreservedBlock(state)) {
            VertexConsumer lit = MinersEyeVertexConsumers.brighten(consumer, 14);
            dispatcher.renderBatched(state, pos, region, poseStack, lit, true, random, modelData, renderType);
            return;
        }

        // Roche interne sans contact air : 0 quad généré
        if (!MinersEyeClientXray.hasExposedCaveSurface(region, pos)) {
            return;
        }

        // Coquille rocheuse en contact avec l'air : redirigée vers la passe translucide à ~24 % d'alpha
        Map<RenderType, BufferBuilder> map = DLB_TL_MAP.get();
        SectionBufferBuilderPack pack = DLB_TL_PACK.get();
        VertexConsumer target = consumer;
        if (map != null && pack != null) {
            target = this.getOrBeginLayer(map, pack, RenderType.translucent());
        }
        VertexConsumer faded = MinersEyeVertexConsumers.faded(target, MinersEyeClientXray.FADED_SHELL_ALPHA, 9);
        dispatcher.renderBatched(state, pos, region, poseStack, faded, true, random, modelData, renderType);
    }
}
