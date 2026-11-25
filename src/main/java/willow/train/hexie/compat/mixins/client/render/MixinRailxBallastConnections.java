package willow.train.hexie.compat.mixins.client.render;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

/** RailX finishes its in-place endpoint/normal edits here, including the remote endpoint. */
@Pseudo
@Mixin(targets = "com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlockEntity", remap = false)
public abstract class MixinRailxBallastConnections {
    @Inject(method = "updateEachConnections", at = @At("RETURN"), require = 1)
    private void hexie$flexibleConnectionsChanged(CallbackInfo ci) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().changed((TrackBlockEntity)(Object)this);
    }
}
