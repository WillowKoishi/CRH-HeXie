package dev.hexie.renderer.sodium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.bridge.HexieBridges;

/** Prevents Sodium's VisGraph from marking window-cut blocks opaque so rooms behind custom windows are never culled. */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class SodiumMeshingTaskCutMixin {
    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"))
    private void hexie$skipOpaqueForCutBlocks(VisGraph visGraph, BlockPos pos, Operation<Void> original) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.hasWindowCutAt(pos)) return;
        original.call(visGraph, pos);
    }
}
