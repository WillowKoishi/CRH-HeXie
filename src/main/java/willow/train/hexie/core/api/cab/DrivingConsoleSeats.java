package willow.train.hexie.core.api.cab;

import willow.train.hexie.core.api.cab.DrivingConsoleContext.SeatAccess;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

// split from willow/train/hexie/api/cab/DrivingConsoleContext.java
final class DrivingConsoleSeats {
    private static final List<Function<Player, SeatAccess>> SEAT_PROVIDERS = new CopyOnWriteArrayList<>();

    /** Register during common setup; providers run on the querying player's game thread. */
    public static void registerSeatProvider(Function<Player, SeatAccess> provider) {
        SEAT_PROVIDERS.add(Objects.requireNonNull(provider));
    }

    /** Shared layout lookup for seat placement, occupied input and dismount; it imposes no occupancy rule. */
    public static Direction consoleFacing(BlockPos seat, Function<BlockPos, BlockState> states) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = states.apply(seat.relative(facing));
            if (state != null && state.getBlock() instanceof CabSurfaceHost host
                    && host.cabFacing(state) == facing) return facing;
        }
        return null;
    }

    public static DrivingConsoleContext find(Player player) {
        if (player == null || !player.isAlive()) return null;
        for (var provider : SEAT_PROVIDERS) {
            var access = provider.apply(player);
            if (access == null) continue;
            var facing = consoleFacing(access.seat(), access::blockState);
            if (facing == null) continue;
            var console = access.seat().relative(facing);
            if (access.supportsSurface(console)) return new DrivingConsoleContext(access, console);
        }
        return null;
    }
}
