package willow.train.hexie.compat.mixins.client.render;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

/** Material changes can make a previously unrendered connection eligible. */
@Mixin(value = BezierConnection.class, remap = false)
public abstract class MixinBallastCurveMaterial {
    @Inject(method = "setMaterial", at = @At("RETURN"), require = 1)
    private void hexie$materialChanged(TrackMaterial material, CallbackInfo ci) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().changed((BezierConnection)(Object)this);
    }
}
