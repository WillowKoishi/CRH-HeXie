package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;

/** Seat discovery and coordinate adaptation only; owns no cab display or control state. */
public final class CreateDrivingConsoleSeats {
    private CreateDrivingConsoleSeats() {}

    public static void register() {
        DrivingConsoleContext.registerSeatProvider(CreateDrivingConsoleSeats::find);
    }

    private static DrivingConsoleContext.SeatAccess find(Player player) {
        if (player.getVehicle() instanceof SeatEntity seat
                && player.level().getBlockState(seat.blockPosition()).getBlock() instanceof SeatBlock)
            return new StationarySeat(seat, seat.blockPosition());
        if (player.getVehicle() instanceof AbstractContraptionEntity car && car.getContraption() != null) {
            var seat = car.getContraption().getSeatOf(player.getUUID());
            if (seat != null) {
                var info = car.getContraption().getBlocks().get(seat);
                if (info != null && info.state().getBlock() instanceof SeatBlock)
                    return new MovingSeat(car, seat);
            }
        }
        return null;
    }

    private record StationarySeat(SeatEntity vehicle, BlockPos seat) implements DrivingConsoleContext.SeatAccess {
        @Override public BlockState blockState(BlockPos position) { return vehicle.level().getBlockState(position); }
        @Override public willow.train.hexie.vehicle.control.CabModule.State cabControls(BlockPos position) {
            return vehicle.level().getBlockEntity(position) instanceof willow.train.hexie.api.cab.CabControlsHost host
                    ? host.controls() : willow.train.hexie.vehicle.control.CabModule.State.NEUTRAL;
        }
        @Override public Vec3 toLocal(Vec3 world, float partialTick) { return world; }
    }

    private record MovingSeat(AbstractContraptionEntity vehicle, BlockPos seat) implements DrivingConsoleContext.SeatAccess {
        @Override public BlockState blockState(BlockPos position) {
            var info = vehicle.getContraption().getBlocks().get(position);
            return info == null ? null : info.state();
        }
        @Override public willow.train.hexie.vehicle.control.CabModule.State cabControls(BlockPos position) {
            if (vehicle instanceof SnapshotCarriageEntity car) return car.cabControls(position);
            var info = vehicle.getContraption().getBlocks().get(position);
            if (info == null || info.nbt() == null) return willow.train.hexie.vehicle.control.CabModule.State.NEUTRAL;
            return willow.train.hexie.vehicle.control.CabModule.read(info.nbt().getCompound(
                    info.nbt().contains("CabControls") ? "CabControls" : "TestControls")).state();
        }
        @Override public Vec3 toLocal(Vec3 world, float partialTick) {
            if (vehicle instanceof SnapshotCarriageEntity car)
                return CarriagePlayerSupport.frame(car, partialTick).local(world);
            return vehicle.toLocalVector(world, partialTick);
        }
    }
}
