package willow.train.hexie.world.blocks.seats;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.util.seat.SeatPositions;

public final class PassengerSeatInteraction {
    private PassengerSeatInteraction() {}

    static List<Entity> seatsAt(Level level, BlockPos pos) {
        return level.getEntities((Entity) null, new AABB(pos),
                entity -> EntitySelector.NO_SPECTATORS.test(entity) && HexieBridges.playerCarriage().isHexieSeat(entity));
    }

    static Entity spawnSeat(Level world, BlockPos pos, Vec3 offset) {
        var seat = HexieBridges.playerCarriage().createHexieSeatEntity(world);
        seat.setPos(pos.getX() + offset.x, pos.getY(), pos.getZ() + offset.z);
        world.addFreshEntity(seat);
        return seat;
    }

    public static InteractionResult use(BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            BlockHitResult hit, Supplier<Entity> riderSupplier) {

        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        if (world.isClientSide) return InteractionResult.SUCCESS;

        int clickedPosition = SeatPositions.getPositionForHit(state, pos, hit.getLocation());
        int seatPosition = clickedPosition;
        Vec3 seatOffset = SeatPositions.getSeatCoordinates(state, seatPosition);
        List<Entity> seats = seatsAt(world, pos);
        Entity targetSeat = SeatPositions.findSeatAt(seats, pos, seatOffset);
        Entity rider = riderSupplier.get();

        if (targetSeat != null && !targetSeat.getPassengers().isEmpty()) {
            Entity currentRider = targetSeat.getPassengers().get(0);
            if (currentRider == rider || currentRider == player) {
                targetSeat.ejectPassengers();
                return InteractionResult.SUCCESS;
            }
            if (currentRider instanceof Player) {
                boolean[] occupied = SeatPositions.getOccupiedPositions(seats, state, pos, player, rider);
                seatPosition = SeatPositions.getNearestAvailablePosition(state, clickedPosition, occupied);
                if (seatPosition < 0)
                    return InteractionResult.PASS;
                seatOffset = SeatPositions.getSeatCoordinates(state, seatPosition);
                targetSeat = SeatPositions.findSeatAt(seats, pos, seatOffset);

                if (targetSeat != null && !targetSeat.getPassengers().isEmpty()) {
                    Entity alternateRider = targetSeat.getPassengers().get(0);
                    if (alternateRider == rider || alternateRider == player)
                        return InteractionResult.SUCCESS;
                    if (alternateRider instanceof Player)
                        return InteractionResult.PASS;
                    targetSeat.ejectPassengers();
                }
            } else {
                targetSeat.ejectPassengers();
            }
        }

        if (targetSeat == null) {
            targetSeat = spawnSeat(world, pos, seatOffset);
        }

        rider.startRiding(targetSeat, true);
        if (rider instanceof TamableAnimal animal)
            animal.setInSittingPose(true);
        return InteractionResult.SUCCESS;
    }
}
