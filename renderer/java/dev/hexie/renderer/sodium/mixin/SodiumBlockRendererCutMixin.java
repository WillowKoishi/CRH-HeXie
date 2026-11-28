package dev.hexie.renderer.sodium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.function.Function;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

/**
 * Intercepts Sodium 0.8 block meshing for blocks affected by custom window stickers so ordinary
 * non-OBJ blocks (and non-StaticShell models) cut and emit frames + glass identically to vanilla SectionCompiler.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererCutMixin {
    @Shadow
    private ChunkBuildBuffers buffers;

    @Shadow
    private TranslucentGeometryCollector collector;

    @Unique
    private BlockAndTintGetter hexie$level;

    @Inject(method = "prepare", at = @At("TAIL"))
    private void hexie$captureLevel(ChunkBuildBuffers buffers, @Coerce Object slice,
                                    TranslucentGeometryCollector collector, CallbackInfo ci) {
        this.hexie$level = slice instanceof BlockAndTintGetter getter ? getter : null;
    }

    @Inject(method = "release", at = @At("TAIL"))
    private void hexie$clearLevel(CallbackInfo ci) {
        this.hexie$level = null;
    }

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void hexie$tesselateWindowCutBlock(BakedModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.isExternallyRenderedCut(pos)) {
            ci.cancel();
            return;
        }
        BlockAndTintGetter region = this.hexie$level;
        if (cutter == null || buffers == null || region == null || !cutter.hasCandidateCutAt(pos)) return;

        BlockPos immPos = pos.immutable();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource rand = RandomSource.create(state.getSeed(immPos));
        ModelData modelData = model.getModelData(region, immPos, state, region.getModelData(immPos));

        var renderTypes = new ArrayList<RenderType>(4);
        for (RenderType rt : model.getRenderTypes(state, rand, modelData)) {
            renderTypes.add(rt);
        }
        if (renderTypes.isEmpty()) {
            renderTypes.add(ItemBlockRenderTypes.getChunkRenderType(state));
        }

        PoseStack poseStack = new PoseStack();
        poseStack.translate(origin.getX(), origin.getY(), origin.getZ());
        Function<RenderType, VertexConsumer> layerSupplier = rt -> {
            Material mat = DefaultMaterials.forRenderLayer(rt);
            return buffers.get(mat).asFallbackVertexConsumer(mat, collector);
        };

        boolean anyHandled = false;
        var uncutLayers = new ArrayList<RenderType>(2);
        for (RenderType rt : renderTypes) {
            rand.setSeed(state.getSeed(immPos));
            boolean handled = cutter.tesselateCutBlock(
                    dispatcher, state, immPos, region, poseStack,
                    layerSupplier.apply(rt), layerSupplier, rand, modelData, rt);
            if (handled) anyHandled = true;
            else uncutLayers.add(rt);
        }
        if (!anyHandled) return;

        for (RenderType rt : uncutLayers) {
            rand.setSeed(state.getSeed(immPos));
            dispatcher.renderBatched(state, immPos, region, poseStack, layerSupplier.apply(rt), true, rand, modelData, rt);
        }
        ci.cancel();
    }
}
