package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.api.cab.CabControlsHost;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.cab.CabSurfaceInstance;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;
import willow.train.hexie.vehicle.control.CabModule;

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
        @Override public boolean supportsSurface(BlockPos position) {
            return vehicle.level().getBlockEntity(position) instanceof CabSurfaceInstance;
        }
        @Override public ControlState surfaceState(BlockPos position) {
            if (vehicle.level().getBlockEntity(position) instanceof CabSurfaceInstance instance)
                return instance.surfaceState();
            return ControlState.EMPTY;
        }
        @Override public boolean simple(BlockPos position) {
            return vehicle.level().getBlockEntity(position) instanceof CabControlsHost host && host.controls().simple();
        }
        @Override public void operateControl(DrivingConsoleContext cab, Player player, BlockControl control, int value) {
            if (vehicle.level().getBlockEntity(cab.console()) instanceof CabSurfaceInstance instance)
                instance.operateControl(player, control, value);
        }
        @Override public ControlSurface.AnimationSource animationSource(BlockPos position) {
            return vehicle.level().getBlockEntity(position) instanceof ControlSurface.AnimationSource source ? source : null;
        }
        @Override public Vec3 toLocal(Vec3 world, float partialTick) {
            return SableInteractionCoordinates.render(vehicle.level(), seat, partialTick).toStorage(world);
        }
        @Override public DrivingConsoleContext.Ray toLocalRay(Vec3 from, Vec3 to, float partialTick) {
            var ray = SableInteractionCoordinates.render(vehicle.level(), seat, partialTick).ray(from, to);
            return new DrivingConsoleContext.Ray(ray.from(), ray.to());
        }
    }

    private record MovingSeat(AbstractContraptionEntity vehicle, BlockPos seat) implements DrivingConsoleContext.SeatAccess {
        @Override public BlockState blockState(BlockPos position) {
            var info = vehicle.getContraption().getBlocks().get(position);
            return info == null ? null : info.state();
        }
        @Override public boolean supportsSurface(BlockPos position) {
            var block = blockState(position);
            return block != null && block.getBlock() instanceof CabControlHost;
        }
        @Override public ControlState surfaceState(BlockPos position) {
            var block = blockState(position);
            return block != null && block.getBlock() instanceof CabControlHost host
                    ? host.surfaceState(block, cabControls(position)) : ControlState.EMPTY;
        }
        @Override public boolean simple(BlockPos position) { return cabControls(position).simple(); }
        private CabModule.State cabControls(BlockPos position) {
            if (vehicle instanceof SnapshotCarriageEntity car) return car.cabControls(position);
            var info = vehicle.getContraption().getBlocks().get(position);
            if (info == null || info.nbt() == null) return CabModule.State.NEUTRAL;
            return CabModule.read(info.nbt().getCompound(
                    info.nbt().contains("CabControls") ? "CabControls" : "TestControls")).state();
        }
        @Override public void operateControl(DrivingConsoleContext cab, Player player, BlockControl control, int value) {
            if (vehicle instanceof SnapshotCarriageEntity car && car.motion() != null)
                car.motion().operateCabControl(car, cab, player, control.id(), value);
        }
        @Override public ControlSurface.AnimationSource animationSource(BlockPos position) {
            return vehicle.getContraption().getBlockEntityClientSide(position) instanceof ControlSurface.AnimationSource source
                    ? source : null;
        }
        @Override public Vec3 toLocal(Vec3 world, float partialTick) {
            Vec3 storage = SableInteractionCoordinates.render(vehicle.level(), vehicle.blockPosition(), partialTick)
                    .toStorage(world);
            if (vehicle instanceof SnapshotCarriageEntity car)
                return CarriagePlayerSupport.frame(car, partialTick).local(storage);
            return vehicle.toLocalVector(storage, partialTick);
        }
        @Override public DrivingConsoleContext.Ray toLocalRay(Vec3 from, Vec3 to, float partialTick) {
            var ray = SableInteractionCoordinates.render(vehicle.level(), vehicle.blockPosition(), partialTick)
                    .ray(from, to);
            if (vehicle instanceof SnapshotCarriageEntity car) {
                var body = CarriagePlayerSupport.frame(car, partialTick);
                return new DrivingConsoleContext.Ray(body.local(ray.from()), body.local(ray.to()));
            }
            return new DrivingConsoleContext.Ray(vehicle.toLocalVector(ray.from(), partialTick),
                    vehicle.toLocalVector(ray.to(), partialTick));
        }
    }
}
