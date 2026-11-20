package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.client.render.track.BallastCurveBuffers;
import willow.train.hexie.client.render.track.BallastTrackModel;

@Mixin(value=TrackBlockEntity.class,remap=false)
public abstract class MixinBallastModelData {
    @Inject(method="getModelData",at=@At("RETURN"),cancellable=true,require=1)
    private void hexie$includeBallast(CallbackInfoReturnable<ModelData> callback) {
        TrackBlockEntity track=(TrackBlockEntity)(Object)this;
        if (TrackBallast.enabled(track.getBlockState()))
            callback.setReturnValue(callback.getReturnValue().derive()
                    .with(BallastTrackModel.TEXTURE,((BallastData)track).hexie$getBallast(null)).build());
    }

    // initialize covers the initial NBT read performed before a level is assigned.
    @Inject(method={"initialize", "addConnection", "accept", "transform"},at=@At("RETURN"),require=1)
    private void hexie$connectionsChanged(CallbackInfo ci) {
        BallastCurveBuffers.changed((TrackBlockEntity)(Object)this);
    }

    // The painted-track mixin can cancel removeConnection later, after this removal and notification.
    @Inject(method="removeConnection",at=@At(value="INVOKE",
            target="Lcom/simibubi/create/content/trains/track/TrackBlockEntity;notifyUpdate()V"),require=1)
    private void hexie$connectionRemoved(CallbackInfo ci) {
        BallastCurveBuffers.changed((TrackBlockEntity)(Object)this);
    }

    @Inject(method="invalidate",at=@At("HEAD"),require=1)
    private void hexie$unloaded(CallbackInfo ci) {
        BallastCurveBuffers.removed((TrackBlockEntity)(Object)this);
    }
}
