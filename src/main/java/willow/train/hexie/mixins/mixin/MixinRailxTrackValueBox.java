package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.railx.RailxTrackBlock;

/** Keep RailX's rotation control on the same raised surface as our track selection. */
@Pseudo
@Mixin(targets = "com.lhwdev.minecraft.railx.flexiTrack.rotate.FlexiRotationValueBox", remap = false)
public abstract class MixinRailxTrackValueBox {
    @Inject(method = "getLocalOffset", at = @At("RETURN"), cancellable = true, require = 1)
    private void hexie$raiseSettingsBox(LevelAccessor world, BlockPos pos, BlockState state,
                                       CallbackInfoReturnable<Vec3> callback) {
        Vec3 offset = callback.getReturnValue();
        if (offset != null && state.getBlock() instanceof RailxTrackBlock track)
            callback.setReturnValue(offset.add(track.heightOffset(world, pos, state)));
    }
}
