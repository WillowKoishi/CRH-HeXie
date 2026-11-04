package willow.train.hexie.mixins.mixin.client;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlockEntity;
import com.lhwdev.minecraft.railx.flexiTrack.FlexiState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.railx.client.RailxBallastModels;

@Pseudo
@Mixin(targets = "com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackRenderer", remap = false)
public abstract class MixinRailxTrackRenderer {
    @WrapOperation(method = "renderFlexiBlock", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/render/CachedBuffers;block(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/render/SuperByteBuffer;"),
            require = 1)
    private SuperByteBuffer hexie$paintedModel(BlockState state, Operation<SuperByteBuffer> original,
                                              @Local(argsOnly = true) FlexiTrackBlockEntity track,
                                              @Local FlexiState.AxisCache axis) {
        return RailxBallastModels.applies(state)
                ? RailxBallastModels.buffer(state, track, axis.getRotationValueDouble()) : original.call(state);
    }
}
