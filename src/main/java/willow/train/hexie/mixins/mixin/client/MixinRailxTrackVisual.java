package willow.train.hexie.mixins.mixin.client;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.railx.client.RailxBallastModels;

@Pseudo
@Mixin(targets = "com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackVisual$BlockVisual", remap = false)
public abstract class MixinRailxTrackVisual {
    @Unique
    private FlexiTrackBlockEntity hexie$track;

    // The inner visual obtains its owner before requesting the cached normal-track model.
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackVisual;getBe()Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackBlockEntity;",
            ordinal = 0), require = 1)
    private FlexiTrackBlockEntity hexie$captureTrack(FlexiTrackBlockEntity track) {
        hexie$track = track;
        return track;
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/model/Models;block(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/engine_room/flywheel/api/model/Model;"),
            require = 1)
    private Model hexie$paintedModel(BlockState state, Operation<Model> original) {
        return RailxBallastModels.applies(state) ? RailxBallastModels.model(state, hexie$track) : original.call(state);
    }
}
