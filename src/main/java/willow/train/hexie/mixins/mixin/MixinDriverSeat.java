package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.locos.CRH2DriverSeatBlock;

/** Keep the native seat entity and lifecycle; only this cab has a different riding position. */
@Mixin(SeatEntity.class)
public abstract class MixinDriverSeat {
    @Inject(method = "positionRider", at = @At("HEAD"), cancellable = true)
    private void hexie$driverPosition(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        var seat = (SeatEntity) (Object) this;
        var pos = seat.blockPosition();
        var state = seat.level().getBlockState(pos);
        if (!(state.getBlock() instanceof CRH2DriverSeatBlock) || !seat.hasPassenger(passenger)) return;
        var eye = CRH2DriverSeatBlock.driverEyePosition(state);
        move.accept(passenger, pos.getX() + eye.x, pos.getY() + eye.y - passenger.getEyeHeight(), pos.getZ() + eye.z);
        ci.cancel();
    }
}
