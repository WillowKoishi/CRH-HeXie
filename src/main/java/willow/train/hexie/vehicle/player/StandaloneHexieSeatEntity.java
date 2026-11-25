package willow.train.hexie.vehicle.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public final class StandaloneHexieSeatEntity extends Entity {
    public StandaloneHexieSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        var pos = blockPosition();
        var state = level().getBlockState(pos);
        if (state.getBlock() instanceof WorldBlockBridge.DriverSeatEyePosition seat) {
            var eye = seat.driverEyePosition(state);
            move.accept(passenger, pos.getX() + eye.x, pos.getY() + eye.y - passenger.getEyeHeight(), pos.getZ() + eye.z);
        } else if (state.getBlock() instanceof WorldBlockBridge.RZSeatBlockView) {
            move.accept(passenger, pos.getX() + .5, pos.getY() + WorldBlockBridge.RZSeatBlockOperations.EYE_HEIGHT
                    - passenger.getEyeHeight(), pos.getZ() + .5);
        } else {
            move.accept(passenger, getX(), getY() + WorldBlockBridge.SeatYZBlockOperations.EYE_HEIGHT
                    - passenger.getEyeHeight(), getZ());
        }
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (!isVehicle() || level().isEmptyBlock(blockPosition()))) discard();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
