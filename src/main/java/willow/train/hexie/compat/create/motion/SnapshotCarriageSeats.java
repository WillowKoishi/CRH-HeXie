package willow.train.hexie.compat.create.motion;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge.CarriedPlayerConnectionView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DriverSeatBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.RZSeatBlockOperations;
import willow.train.hexie.bridge.world.WorldBlockBridge.RZSeatBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.SeatYZBlockOperations;
import willow.train.hexie.bridge.world.WorldBlockBridge.SeatYZBlockView;
import willow.train.hexie.compat.create.player.CarriagePlayerSupport;
import willow.train.hexie.compat.create.seat.SeatYZContraptionInteractionPacket;

final class SnapshotCarriageSeats {
    private final SnapshotCarriageEntity car;

    SnapshotCarriageSeats(SnapshotCarriageEntity car) {
        this.car = car;
    }

    boolean isHardSeat(int index) {
        var contraption = car.getContraption();
        if (contraption == null || index < 0 || index >= contraption.getSeats().size()) return false;
        var info = contraption.getBlocks().get(contraption.getSeats().get(index));
        return info != null && info.state().getBlock() instanceof SeatYZBlockView;
    }

    void addPassenger(Entity passenger, int index) {
        boolean[] occupied = occupancy(index, null);
        int position = HexieBridges.worldBlock().hardSeatPositionRequest().get(passenger, index);
        if (position >= 0 && position < occupied.length) {
            if (occupied[position]) return;
        } else {
            position = availablePosition(passenger, index, occupied);
            if (position < 0) return;
        }
        mount(passenger, index, position);
    }

    private int availablePosition(Entity passenger, int index, boolean[] occupied) {
        for (int i = 0; i < occupied.length; i++)
            if (!occupied[i]) return i;

        // A full bench can yield a mob's quadrant, but never another player's.
        var mapping = car.getContraption().getSeatMapping();
        for (var current : car.getPassengers()) {
            if (current == passenger || current instanceof Player || !Integer.valueOf(index).equals(mapping.get(current.getUUID())))
                continue;
            int quadrant = HexieBridges.worldBlock().hardSeatPosition().read(current.getTags(), index);
            if (quadrant >= 0) {
                current.stopRiding();
                return quadrant;
            }
        }
        return -1;
    }

    int interactiveHardSeat(Player player, BlockPos pos, InteractionHand hand) {
        var contraption = car.getContraption();
        if (contraption == null || AllItems.WRENCH.isIn(player.getItemInHand(hand))) return -1;
        int index = contraption.getSeats().indexOf(pos);
        if (!isHardSeat(index)) return -1;
        if (!car.level().isClientSide && player.isPassenger() && player.getVehicle() != car) return -1;
        return index;
    }

    boolean interact(Player player, BlockPos pos, Direction side, InteractionHand hand, int index) {
        BlockState state = car.getContraption().getBlocks().get(pos).state();
        if (car.level().isClientSide) {
            var hit = HexieBridges.Client.render().carriageInteractionCoordinates().pick(player, car);
            if (hit != null && hit.getBlockPos().equals(pos)) {
                int clicked = HexieBridges.worldBlock().seatYZBlock().getPositionForHit(state, pos, hit.getLocation());
                if (clicked >= 0)
                    PacketDistributor.sendToServer(new SeatYZContraptionInteractionPacket(car.getId(), hand, pos, side, clicked));
            }
            // The custom packet includes the quadrant. Suppress Create's position-less packet.
            return false;
        }

        int requested = HexieBridges.worldBlock().hardSeatPositionRequest().get(player, index);
        if (requested < 0) requested = clickedPosition(player, pos, state);
        Entity rider = SeatBlock.getLeashed(car.level(), player).or(player);
        int position = HexieBridges.worldBlock().seatYZBlock().getNearestAvailablePosition(
                state, requested, occupancy(index, rider));
        if (position < 0) return true;
        if (Integer.valueOf(index).equals(car.getContraption().getSeatMapping().get(rider.getUUID()))
                && HexieBridges.worldBlock().hardSeatPosition().read(rider.getTags(), index) == position) return true;

        if (rider.getVehicle() == car) rider.stopRiding();
        mount(rider, index, position);
        return true;
    }

    private int clickedPosition(Player player, BlockPos pos, BlockState state) {
        Vec3 origin = player.getEyePosition();
        Vec3 target = origin.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var shape = state.getShape(car.getContraption().getContraptionWorld(), BlockPos.ZERO.below());
        var hit = shape.clip(car.toLocalVector(origin, 1), car.toLocalVector(target, 1), pos);
        return hit == null ? -1 : HexieBridges.worldBlock().seatYZBlock().getPositionForHit(state, pos, hit.getLocation());
    }

    private boolean[] occupancy(int index, Entity except) {
        boolean[] occupied = new boolean[4];
        for (Entity passenger : car.getPassengers()) {
            if (passenger == except) continue;
            int position = HexieBridges.worldBlock().hardSeatPosition().read(passenger.getTags(), index);
            if (position >= 0) occupied[position] = true;
        }
        return occupied;
    }

    private void mount(Entity passenger, int index, int position) {
        if (!passenger.startRiding(car, true)) return;
        var positions = HexieBridges.worldBlock().hardSeatPosition();
        positions.clear(passenger);
        passenger.addTag(positions.tag(index, position));
        if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(true);
        if (car.level().isClientSide) return;
        var mapping = car.getContraption().getSeatMapping();
        mapping.put(passenger.getUUID(), index);
        PacketDistributor.sendToPlayersTrackingEntity(car, new ContraptionSeatMappingPacket(car.getId(), mapping));
        HexieBridges.sync().sendContraptionSeatPositionToTracking(car, car.getUUID(), passenger.getUUID(), index, position);
    }

    Vec3 passengerPosition(Entity passenger, float partialTicks) {
        var contraption = car.getContraption();
        var id = passenger.getUUID();
        if (contraption == null) return null;
        BlockPos pos = contraption.getSeatOf(id);
        var info = pos == null ? null : contraption.getBlocks().get(pos);
        if (info == null) return null;
        BlockState state = info.state();
        Vec3 offset;
        if (state.getBlock() instanceof CRH2DriverSeatBlockView) {
            offset = HexieBridges.carriageBlock().crh2DriverSeatBlock().driverEyePosition(state);
        } else if (state.getBlock() instanceof RZSeatBlockView) {
            offset = new Vec3(.5, RZSeatBlockOperations.EYE_HEIGHT, .5);
        } else if (state.getBlock() instanceof SeatYZBlockView) {
            int index = contraption.getSeatMapping().get(id);
            int position = car.level().isClientSide ? HexieBridges.Client.clientSync().get(car.getUUID(), id, index) : -1;
            if (position < 0) position = HexieBridges.worldBlock().hardSeatPosition().read(passenger.getTags(), index);
            if (position < 0) return null;
            var quadrant = HexieBridges.worldBlock().seatYZBlock().getSeatCoordinates(state, position);
            offset = new Vec3(quadrant.x, SeatYZBlockOperations.EYE_HEIGHT, quadrant.z);
        } else {
            return null;
        }
        // Native positionRider adds the custom entity seat offset minus one eighth of a block.
        return car.toGlobalVector(Vec3.atLowerCornerOf(pos).add(offset), partialTicks)
                .add(0, .125 - SeatEntity.getCustomEntitySeatOffset(passenger) - passenger.getEyeHeight(), 0);
    }

    void dismounted(Entity passenger, Integer index) {
        if (!car.level().isClientSide && index != null) {
            var contraption = car.getContraption();
            if (contraption instanceof CarriageContraption && index >= 0 && index < contraption.getSeats().size()
                    && passenger instanceof ServerPlayer player && player.isAlive() && !player.isRemoved()
                    && !car.isRemoved() && player.connection.isAcceptingMessages()
                    && player.connection instanceof CarriedPlayerConnectionView connection) {
                CarriagePlayerSupport.beginDismount(player, car);
                connection.hexie$beginCarriageDismount(car.getId(), car.getUUID());
            }
            // Clients also eject riders while applying passenger lists. Only a server delta clears the cache.
            if (isHardSeat(index)) HexieBridges.sync().sendContraptionSeatPositionToTracking(
                    car, car.getUUID(), passenger.getUUID(), index, -1);
        }
        HexieBridges.worldBlock().hardSeatPosition().clear(passenger);
    }
}
