package willow.train.hexie.compat.mixins.client.render;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntityTilt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.HexieBridges;

/** Create can restore the geometry of an existing connection without changing its identity. */
@Mixin(value = TrackBlockEntityTilt.class, remap = false)
public abstract class MixinBallastCurveSmoothing {
    @Inject(method = "restoreToOriginalCurve", at = @At("RETURN"), require = 1)
    private void hexie$curveRestored(BezierConnection curve, CallbackInfoReturnable<BezierConnection> cir) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().changed(cir.getReturnValue());
    }
}
