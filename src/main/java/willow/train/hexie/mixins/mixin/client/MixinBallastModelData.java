package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TrackBlockEntity.class,remap=false)
public abstract class MixinBallastModelData {
    @Inject(method="read",at=@At("RETURN"),require=1)
    private void hexie$invalidate(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        TrackBlockEntity be=(TrackBlockEntity)(Object)this;
        if(packet&&be.getLevel()!=null) {
            be.requestModelDataUpdate();
            be.getLevel().sendBlockUpdated(be.getBlockPos(),be.getBlockState(),be.getBlockState(),8);
        }
    }
}
