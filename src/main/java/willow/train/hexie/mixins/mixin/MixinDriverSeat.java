package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.emu.CRH2DriverSeatBlock;
import willow.train.hexie.blocks.seats.RZSeatBlock;

/** Keep the native seat lifecycle while positioning cab and soft-seat passengers by eye height. */
@Mixin(SeatEntity.class)
public abstract class MixinDriverSeat {
    @Inject(method = "positionRider", at = @At("HEAD"), cancellable = true)
    private void hexie$seatPosition(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        var seat = (SeatEntity) (Object) this;
        if (!seat.hasPassenger(passenger)) return;
        var pos = seat.blockPosition();
        var state = seat.level().getBlockState(pos);
        Vec3 eye;
        if (state.getBlock() instanceof CRH2DriverSeatBlock) {
            eye = CRH2DriverSeatBlock.driverEyePosition(state);
        } else if (state.getBlock() instanceof RZSeatBlock) {
            eye = new Vec3(.5, RZSeatBlock.EYE_HEIGHT, .5);
        } else {
            return;
        }
        move.accept(passenger, pos.getX() + eye.x, pos.getY() + eye.y - passenger.getEyeHeight(), pos.getZ() + eye.z);
        ci.cancel();
    }
}
