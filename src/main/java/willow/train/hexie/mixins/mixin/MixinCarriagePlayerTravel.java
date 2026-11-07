package willow.train.hexie.mixins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

/** The client has already resolved travel for standing players on our carriages. */
@Mixin(ServerPlayer.class)
public abstract class MixinCarriagePlayerTravel {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void hexie$clientResolvedTravel(Vec3 input, CallbackInfo ci) {
        if (CarriagePlayerSupport.carriage((ServerPlayer) (Object) this) != null) ci.cancel();
    }
}
