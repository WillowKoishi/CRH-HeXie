package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.CreateSignalAspect;
import willow.train.hexie.compat.create.CreateSignalAspects;
import willow.train.hexie.vehicle.control.CtcsControl.Code;

@Mixin(value = SignalBlockEntity.class, remap = false)
public abstract class MixinCtcsSignalDisplay implements CreateSignalAspect {
    @Unique private Code hexie$code = Code.UNKNOWN;
    @Override public Code hexie$ctcsCode() { return hexie$code; }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexie$sample(CallbackInfo ci) {
        var be = (SignalBlockEntity) (Object) this;
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        var code = CreateSignalAspects.read(be);
        if (hexie$code != code) { hexie$code = code; be.notifyUpdate(); }
    }
    @Inject(method = "write", at = @At("TAIL"))
    private void hexie$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) tag.putString("HeXieCtcsAspect", hexie$code.name());
    }
    @Inject(method = "read", at = @At("TAIL"))
    private void hexie$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        hexie$code = Code.UNKNOWN;
        if (clientPacket) try { hexie$code = Code.valueOf(tag.getString("HeXieCtcsAspect")); }
        catch (IllegalArgumentException ignored) { }
    }
    @Inject(method = "getState", at = @At("RETURN"), cancellable = true)
    private void hexie$display(CallbackInfoReturnable<SignalBlockEntity.SignalState> cir) {
        cir.setReturnValue(CreateSignalAspects.visibleState((SignalBlockEntity) (Object) this, cir.getReturnValue()));
    }
}
