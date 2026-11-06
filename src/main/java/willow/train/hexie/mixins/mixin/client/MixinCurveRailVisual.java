package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.engine_room.flywheel.api.instance.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import willow.train.hexie.compat.create.client.CurveRailModel;

@Mixin(targets = "com.simibubi.create.content.trains.track.TrackVisual$BezierTrackVisual", remap = false)
abstract class MixinCurveRailVisual {
    @Shadow @Final @Mutable private TransformedInstance[] left;
    @Shadow @Final @Mutable private TransformedInstance[] right;
    @Unique private static final boolean[] hexie$noSeparateRails = new boolean[0];

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Ldev/engine_room/flywheel/api/instance/InstancerProvider;instancer(Ldev/engine_room/flywheel/api/instance/InstanceType;Ldev/engine_room/flywheel/api/model/Model;)Ldev/engine_room/flywheel/api/instance/Instancer;"))
    private Instancer<?> hexie$provider(InstancerProvider provider, InstanceType<?> type, Model model,
            Operation<Instancer<?>> original, @Share("provider") LocalRef<InstancerProvider> shared) {
        shared.set(provider);
        return original.call(provider, type, model);
    }

    @WrapOperation(method = "<init>", slice = @Slice(from = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/TrackMaterial$TrackModelHolder;leftSegment()Ldev/engine_room/flywheel/lib/model/baked/PartialModel;")),
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/instance/Instancer;createInstances([Ldev/engine_room/flywheel/api/instance/Instance;)V"), require = 2)
    private void hexie$rails(Instancer<?> originalInstancer, Instance[] instances, Operation<Void> original,
            @Local(argsOnly = true) BezierConnection curve, @Share("provider") LocalRef<InstancerProvider> provider,
            @Local PoseStack ownerPose) {
        if (!CurveRailModel.applies(curve) || instances.length == 0) {
            original.call(originalInstancer, instances);
        } else if (instances == left) {
            // Reuse Create's instance collection/deletion for our single curve mesh.
            var rails = provider.get().instancer(InstanceTypes.TRANSFORMED, CurveRailModel.wholeCurve(curve))
                    .createInstance();
            rails.setTransform(ownerPose).setChanged();
            left = new TransformedInstance[]{rails};
            right = new TransformedInstance[0];
        }
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/createmod/catnip/data/Iterate;trueAndFalse:[Z"))
    private boolean[] hexie$separateRails(boolean[] sides, @Local(argsOnly = true) BezierConnection curve) {
        return CurveRailModel.applies(curve) ? hexie$noSeparateRails : sides;
    }
}
