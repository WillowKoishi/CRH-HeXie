package willow.train.hexie.compat.create.player;

import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageStructure;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageBoarding {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageBoarding(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public final CarriageBoardingHistory boardingHistory = new CarriageBoardingHistory();

    public boolean retainTrackingForBoarding(net.minecraft.server.level.ServerPlayer player, double range) {
        return !carriageEntity.isRemoved() && carriageEntity.level() == player.level()
                && (CarriagePlayerSupport.attachedCarriage(player) == carriageEntity
                    || boardingHistory.wasInTrackingRange(player.position(), range, carriageEntity.level().getGameTime()));
    }

    /** Call only after the world restore transaction commits; a failed unmount must not move riders. */
    public void restorePassengers() {
        if (carriageEntity.snapshot == null) return;
        restorePassengers(new StructureTransform(carriageEntity.snapshot.origin(), 0, 0, 0));
    }

    public void restorePassengers(StructureTransform transform) {
        if (carriageEntity.getPassengers().isEmpty()) return;
        carriageEntity.getContraption().addPassengersToWorld(carriageEntity.level(), transform, carriageEntity.getPassengers());
        carriageEntity.ejectPassengers();
    }

    public void registerColliding(Entity entity) {
        carriageEntity.baseRegisterColliding(entity);
        CarriagePlayerSupport.contact(carriageEntity, entity);
    }

    public void contraptionInitialize() {
        if (carriageEntity.getContraption() instanceof SnapshotCarriageStructure.SnapshotView view) {
            view.initialSeatPassengers.forEach((seat, passenger) -> {
                if (!passenger.isAlive()) return;
                Entity oldVehicle = passenger.getVehicle();
                carriageEntity.addSittingPassenger(passenger, view.getSeats().indexOf(seat));
                if (passenger.getVehicle() == carriageEntity && oldVehicle instanceof SeatEntity
                        && oldVehicle.getPassengers().isEmpty()) oldVehicle.discard();
            });
            view.initialSeatPassengers.clear();
        }
        // No Create stabilized subcontraptions exist here, so no generic nearby-entity scan is needed.
        carriageEntity.setStructureInitialized(true);
    }
}
