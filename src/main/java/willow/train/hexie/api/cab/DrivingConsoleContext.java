package willow.train.hexie.api.cab;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Backend-neutral seat discovery and console-local input coordinates. */
public record DrivingConsoleContext(SeatAccess seatAccess, BlockPos console) {
    private static final List<Function<Player, SeatAccess>> SEAT_PROVIDERS = new CopyOnWriteArrayList<>();

    /** Providers should return value-stable access objects so the cab does not reset every tick. */
    public interface SeatAccess {
        Entity vehicle();
        BlockPos seat();
        BlockState blockState(BlockPos position);
        /** Let another provider take this seat when its surface needs a different backend. */
        default boolean supportsSurface(BlockPos position) { return true; }
        default CabSurfaceState surfaceState(BlockPos position) { return CabSurfaceState.EMPTY; }
        /** Client-side interpolation source for the same physical surface that is being rendered. */
        default CabSurfaceGeometry.AnimationSource animationSource(BlockPos position) { return null; }
        default boolean simple(BlockPos position) { return false; }
        /** The server has checked occupancy, control membership and the requested detent. */
        default void operateControl(DrivingConsoleContext cab, Player player, CabControl control, int value) {}
        /** World coordinates to the coordinate system containing seat and console. */
        Vec3 toLocal(Vec3 world, float partialTick);
    }

    /** Register during common setup; providers run on the querying player's game thread. */
    public static void registerSeatProvider(Function<Player, SeatAccess> provider) {
        SEAT_PROVIDERS.add(Objects.requireNonNull(provider));
    }

    public BlockPos seat() { return seatAccess.seat(); }
    public boolean simple() { return seatAccess.simple(console); }
    public CabSurfaceState surfaceState() { return seatAccess.surfaceState(console); }

    public Vec3 toConsoleLocal(Vec3 world, float partialTick) {
        return seatAccess.toLocal(world, partialTick).subtract(Vec3.atLowerCornerOf(console));
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
