package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.api.interaction.BlockControlPicking;

/** Called by both vanilla and Sable's plot-local clip loop; no plot enumeration or extra ray cast. */
@Mixin(BlockGetter.class)
public interface MixinBlockControlClip {
    @Inject(method = "clipWithInteractionOverride", at = @At("RETURN"), cancellable = true)
    private void hexie$controlCell(Vec3 from, Vec3 to, BlockPos cell, VoxelShape shape, BlockState state,
                                   CallbackInfoReturnable<BlockHitResult> callback) {
        callback.setReturnValue(BlockControlPicking.clipCell((BlockGetter) this, cell, from, to, callback.getReturnValue()));
    }
}
