package willow.train.hexie.blocks.locos;

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
        /** World coordinates to the coordinate system containing seat and console. */
        Vec3 toLocal(Vec3 world, float partialTick);
    }

    /** Register during common setup; providers run on the querying player's game thread. */
    public static void registerSeatProvider(Function<Player, SeatAccess> provider) {
        SEAT_PROVIDERS.add(Objects.requireNonNull(provider));
    }

    public BlockPos seat() { return seatAccess.seat(); }

    public Vec3 toConsoleLocal(Vec3 world, float partialTick) {
        return seatAccess.toLocal(world, partialTick).subtract(Vec3.atLowerCornerOf(console));
    }

    public static DrivingConsoleContext find(Player player) {
        if (player == null || !player.isAlive()) return null;
        for (var provider : SEAT_PROVIDERS) {
            var access = provider.apply(player);
            if (access == null) continue;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos console = access.seat().relative(facing);
                var state = access.blockState(console);
                if (state != null && state.getBlock() instanceof CRH2DrivingConsoleBlock
                        && state.getValue(CRH2DrivingConsoleBlock.FACING) == facing)
                    return new DrivingConsoleContext(access, console);
            }
        }
        return null;
    }
}
