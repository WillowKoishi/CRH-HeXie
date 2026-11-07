package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.render.track.BallastCurveBuffers;

@Mixin(value=TrackBlockEntity.class,remap=false)
public abstract class MixinBallastModelData {
    @Inject(method="read",at=@At("RETURN"),require=1)
    private void hexie$invalidate(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        TrackBlockEntity be=(TrackBlockEntity)(Object)this;
        if(packet&&be.getLevel()!=null) {
            be.requestModelDataUpdate();
            be.getLevel().sendBlockUpdated(be.getBlockPos(),be.getBlockState(),be.getBlockState(),8);
        }
        BallastCurveBuffers.changed(be);
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
