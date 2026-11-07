package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.CarriageChunkAccess;

/** Physics/environment reads for an attached player must not synchronously generate terrain. */
@Mixin(Level.class)
public abstract class MixinCarriageTerrainRead {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void hexie$loadedBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if ((Object) this instanceof ServerLevel server && CarriageChunkAccess.reading(server)) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            cir.setReturnValue(chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos));
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void hexie$loadedFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if ((Object) this instanceof ServerLevel server && CarriageChunkAccess.reading(server)) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            cir.setReturnValue(chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos));
        }
    }
}
