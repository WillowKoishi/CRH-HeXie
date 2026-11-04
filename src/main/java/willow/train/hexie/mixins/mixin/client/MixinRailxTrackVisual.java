package willow.train.hexie.mixins.mixin.client;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlockEntity;
import com.lhwdev.minecraft.railx.flexiTrack.FlexiState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.railx.client.RailxBallastModels;

@Pseudo
@Mixin(targets = "com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackVisual$BlockVisual", remap = false)
public abstract class MixinRailxTrackVisual {
    // RailX obtains the owner and provider once before the per-axis instance loop.
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackVisual;getBe()Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackBlockEntity;",
            ordinal = 0), require = 1)
    private FlexiTrackBlockEntity hexie$captureTrack(FlexiTrackBlockEntity track,
            @Share("track") LocalRef<FlexiTrackBlockEntity> owner) {
        owner.set(track);
        return track;
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackVisual;access$instancerProvider(Lcom/lhwdev/minecraft/railx/flexiTrack/FlexiTrackVisual;)Ldev/engine_room/flywheel/api/instance/InstancerProvider;"), require = 1)
    private InstancerProvider hexie$captureProvider(InstancerProvider provider,
            @Share("provider") LocalRef<InstancerProvider> shared) {
        shared.set(provider);
        return provider;
    }

    // Kotlin's inlined map stores the loop element as Object in the constructor's stack frames.
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/Iterator;next()Ljava/lang/Object;"), require = 1)
    private Object hexie$captureAxis(Object axis, @Share("axis") LocalRef<FlexiState.AxisCache> current) {
        current.set((FlexiState.AxisCache) axis);
        return axis;
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/api/instance/Instancer;createInstance()Ldev/engine_room/flywheel/api/instance/Instance;"), require = 1)
    private Instance hexie$paintedAxis(Instancer<?> originalInstancer, Operation<Instance> original,
            @Local BlockState state, @Share("axis") LocalRef<FlexiState.AxisCache> axis,
            @Share("track") LocalRef<FlexiTrackBlockEntity> owner,
            @Share("provider") LocalRef<InstancerProvider> provider) {
        if (!RailxBallastModels.applies(state)) return original.call(originalInstancer);
        var model = RailxBallastModels.model(state, owner.get(), axis.get().getRotationValueDouble());
        return original.call(provider.get().instancer(InstanceTypes.TRANSFORMED, model));
    }
}
