package willow.train.hexie.core.util.seat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class SeatPositions {
    private SeatPositions() {}

    public static int getPositionForHit(BlockState state, BlockPos pos, Vec3 hitLocation) {
        boolean rightHalf = hitLocation.x - pos.getX() >= .5;
        boolean farHalf = hitLocation.z - pos.getZ() >= .5;
        boolean northSouth = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.Z;

        if (northSouth) {
            if (!rightHalf && !farHalf) return 0;
            if (rightHalf && !farHalf) return 1;
            if (rightHalf) return 2;
            return 3;
        }

        if (rightHalf && farHalf) return 0;
        if (rightHalf) return 1;
        if (!farHalf) return 2;
        return 3;
    }

    public static Vec3 getSeatCoordinates(BlockState state, int index) {
        boolean northSouth = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.Z;
        if (northSouth) {
            return switch (index) {
                case 0 -> new Vec3(.25, 0, .1875);
                case 1 -> new Vec3(.75, 0, .1875);
                case 2 -> new Vec3(.75, 0, .8125);
                case 3 -> new Vec3(.25, 0, .8125);
                default -> new Vec3(.25, 0, .1875);
            };
        }
        return switch (index) {
            case 0 -> new Vec3(.8125, 0, .75);
            case 1 -> new Vec3(.8125, 0, .25);
            case 2 -> new Vec3(.1875, 0, .25);
            case 3 -> new Vec3(.1875, 0, .75);
            default -> new Vec3(.8125, 0, .75);
        };
    }

    public static int getNearestAvailablePosition(BlockState state, int preferred, boolean[] occupied) {
        if (preferred < 0 || preferred >= 4 || occupied.length < 4)
            return -1;
        if (!occupied[preferred])
            return preferred;

        int preferredX = getGridX(state, preferred);
        int preferredZ = getGridZ(state, preferred);
        int nearest = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < 4; candidate++) {
            if (occupied[candidate])
                continue;
            int distance = Math.abs(getGridX(state, candidate) - preferredX)
                    + Math.abs(getGridZ(state, candidate) - preferredZ);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static int getGridX(BlockState state, int position) {
        return getSeatCoordinates(state, position).x >= .5 ? 1 : 0;
    }

    public static int getGridZ(BlockState state, int position) {
        return getSeatCoordinates(state, position).z >= .5 ? 1 : 0;
    }

    public static boolean[] getOccupiedPositions(List<? extends Entity> seats,
            BlockState state,
            BlockPos pos,
            Player player,
            Entity rider) {
        boolean[] occupied = new boolean[4];
        for (int position = 0; position < occupied.length; position++) {
            Entity seat = findSeatAt(seats, pos, getSeatCoordinates(state, position));
            if (seat == null || seat.getPassengers().isEmpty())
                continue;
            Entity occupant = seat.getPassengers().get(0);
            occupied[position] = occupant != player && occupant != rider;
        }
        return occupied;
    }

    public static <E extends Entity> E findSeatAt(List<E> seats, BlockPos pos, Vec3 offset) {
        double x = pos.getX() + offset.x;
        double z = pos.getZ() + offset.z;
        for (E seat : seats) {
            if (Math.abs(seat.getX() - x) < .001 && Math.abs(seat.getZ() - z) < .001)
                return seat;
        }
        return null;
    }
}
