package willow.train.hexie.compat.mixins.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.HexieBridges;

/**
 * Ensures solid blocks sitting directly behind a window-cut block still render their surface
 * visible through the hollow cavity (while keeping coplanar side-by-side cut seams culled).
 */
@Mixin(Block.class)
public abstract class MixinBlockOcclusionCut {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void hexie$exposeFaceBehindCutBlock(
            BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir) {
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null && cutter.shouldExposeFaceIntoCut(pos, face, neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}
