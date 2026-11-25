package willow.train.hexie.compat.mixins.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.bridge.HexieBridges;

/**
 * Intercepts chunk section compilation for blocks cut by custom windows:
 * 1. Prevents VisGraph from marking cut blocks opaque so interior chunks behind windows are never culled.
 * 2. Replaces vanilla block tessellation with real coplanar single-layer / zero-thickness cut geometry,
 *    hollow cavity walls, 3D parametric frame, and translucent glass directly inside the chunk VBOs.
 */
@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompilerCut {
    @Shadow
    protected abstract BufferBuilder getOrBeginLayer(
            Map<RenderType, BufferBuilder> bufferLayers,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            RenderType renderType);

    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"))
    private void hexie$skipOpaqueForCutBlocks(VisGraph visGraph, BlockPos pos, Operation<Void> original) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.hasWindowCutAt(pos)) return;
        original.call(visGraph, pos);
    }

    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void hexie$tesselateCutBlock(
            BlockRenderDispatcher dispatcher,
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter region,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            ModelData modelData,
            RenderType renderType,
            Operation<Void> original,
            @Local Map<RenderType, BufferBuilder> bufferLayers,
            @Local(argsOnly = true) SectionBufferBuilderPack bufferPack) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.hasCandidateCutAt(pos)) {
            boolean handled = cutter.tesselateCutBlock(
                    dispatcher, state, pos, region, poseStack, consumer,
                    layer -> getOrBeginLayer(bufferLayers, bufferPack, layer),
                    random, modelData, renderType);
            if (handled) return;
        }
        original.call(dispatcher, state, pos, region, poseStack, consumer, checkSides, random, modelData, renderType);
    }
}
