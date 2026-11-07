package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.render.track.BallastCurveBuffers;

/** Material changes can make a previously unrendered connection eligible. */
@Mixin(value = BezierConnection.class, remap = false)
public abstract class MixinBallastCurveMaterial {
    @Inject(method = "setMaterial", at = @At("RETURN"), require = 1)
    private void hexie$materialChanged(TrackMaterial material, CallbackInfo ci) {
        BallastCurveBuffers.changed((BezierConnection)(Object)this);
    }
}
