package dev.hexie.renderer.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.HexieBridges;

/** Ensures solid blocks behind a window-cut block expose their inner face through the cut cavity under Sodium. */
@Mixin(value = BlockOcclusionCache.class, remap = false)
public abstract class SodiumBlockOcclusionCutMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void hexie$exposeFaceBehindCutBlock(
            BlockState selfState, BlockGetter view, BlockPos pos, Direction facing,
            CallbackInfoReturnable<Boolean> cir) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.shouldExposeFaceIntoCut(pos, facing, pos.relative(facing))) {
            cir.setReturnValue(true);
        }
    }
}
