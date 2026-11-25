package willow.train.hexie.compat.mixins.client.render;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

@Mixin(value=TrackBlockEntity.class,remap=false)
public abstract class MixinBallastModelData {
    // initialize covers the initial NBT read performed before a level is assigned.
    @Inject(method={"initialize", "addConnection", "accept", "transform"},at=@At("RETURN"),require=1)
    private void hexie$connectionsChanged(CallbackInfo ci) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().changed((TrackBlockEntity)(Object)this);
    }

    // The painted-track mixin can cancel removeConnection later, after this removal and notification.
    @Inject(method="removeConnection",at=@At(value="INVOKE",
            target="Lcom/simibubi/create/content/trains/track/TrackBlockEntity;notifyUpdate()V"),require=1)
    private void hexie$connectionRemoved(CallbackInfo ci) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().changed((TrackBlockEntity)(Object)this);
    }

    @Inject(method="invalidate",at=@At("HEAD"),require=1)
    private void hexie$unloaded(CallbackInfo ci) {
        HexieBridges.Client.trackRender().ballastCurveBuffers().removed((TrackBlockEntity)(Object)this);
    }
}
