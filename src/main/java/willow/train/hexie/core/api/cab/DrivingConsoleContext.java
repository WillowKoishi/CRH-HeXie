package willow.train.hexie.core.api.cab;

import java.util.function.Function;
import net.minecraft.core.Direction;

// split from willow/train/hexie/api/cab/DrivingConsoleContext.java

import willow.train.hexie.core.api.interaction.ControlSurface;

import willow.train.hexie.core.api.interaction.ControlState;

import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Backend-neutral seat discovery and console-local input coordinates. */
public record DrivingConsoleContext(SeatAccess seatAccess, BlockPos console) {
    /** Providers should return value-stable access objects so the cab does not reset every tick. */
    public interface SeatAccess {
        Entity vehicle();
        BlockPos seat();
        BlockState blockState(BlockPos position);
        /** The block container may differ from the entity carrying a stationary seat. */
        default BlockControlContext controlTarget(BlockPos position, float partialTick) {
            return new BlockControlContext(vehicle().level(), position, null, partialTick);
        }
        /** Let another provider take this seat when its surface needs a different backend. */
        default boolean supportsSurface(BlockPos position) { return true; }
        default ControlState surfaceState(BlockPos position) { return ControlState.EMPTY; }
        /** Client-side interpolation source for the same physical surface that is being rendered. */
        default ControlSurface.AnimationSource animationSource(BlockPos position) { return null; }
        default boolean simple(BlockPos position) { return false; }
        /** The server has checked occupancy, control membership and the requested detent. */
        default void operateControl(DrivingConsoleContext cab, Player player, BlockControl control, int value) {}
        /** Submit the desk's interpreted keyboard state to its existing control owner. */
        default void updateKeyboard(DrivingConsoleContext cab, Player player, boolean toggleSimple, int mask) {}
        /** World coordinates to the coordinate system containing seat and console. */
        Vec3 toLocal(Vec3 world, float partialTick);
        /** Transform both endpoints with the same sampled carrier pose. */
        default Ray toLocalRay(Vec3 from, Vec3 to, float partialTick) {
            return new Ray(toLocal(from, partialTick), toLocal(to, partialTick));
        }
    }

    public record Ray(Vec3 from, Vec3 to) {}

    public BlockPos seat() { return seatAccess.seat(); }
    public boolean simple() { return seatAccess.simple(console); }
    public ControlState surfaceState() { return seatAccess.surfaceState(console); }

    public boolean matchesTarget(BlockControlContext target) {
        if (target == null || !console.equals(target.position())) return false;
        var own = seatAccess.controlTarget(console, target.partialTick());
        return own != null && own.blocks() == target.blocks() && own.carrier() == target.carrier();
    }

    public Vec3 toConsoleLocal(Vec3 world, float partialTick) {
        return seatAccess.toLocal(world, partialTick).subtract(Vec3.atLowerCornerOf(console));
    }

    public Ray toConsoleLocalRay(Vec3 from, Vec3 to, float partialTick) {
        var ray = seatAccess.toLocalRay(from, to, partialTick);
        var offset = Vec3.atLowerCornerOf(console);
        return new Ray(ray.from().subtract(offset), ray.to().subtract(offset));
    }

    public static void registerSeatProvider(Function<Player, SeatAccess> provider) {
        DrivingConsoleSeats.registerSeatProvider(provider);
    }

    public static Direction consoleFacing(BlockPos seat, Function<BlockPos, BlockState> states) {
        return DrivingConsoleSeats.consoleFacing(seat, states);
    }

    public static DrivingConsoleContext find(Player player) { return DrivingConsoleSeats.find(player); }
}
