package willow.train.hexie.mixins.mixin;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import willow.train.hexie.blocks.emu.CRH2DriverSeatBlock;
import willow.train.hexie.blocks.seats.HardSeatPositionRequest;
import willow.train.hexie.blocks.seats.RZSeatBlock;
import willow.train.hexie.blocks.seats.SeatYZBlock;
import willow.train.hexie.network.c2s.SeatYZContraptionInteractionPacket;
import willow.train.hexie.network.s2c.ContraptionSeatPositionCache;
import willow.train.hexie.network.s2c.ContraptionSeatPositionPacket;

import java.util.UUID;

/**
 * Adds HeXie's four hard-seat positions and custom cab/soft-seat eye heights while
 * leaving Create's normal contraption seat implementation intact for other seats.
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity extends Entity {

    @Shadow(remap = false)
    protected Contraption contraption;

    protected MixinAbstractContraptionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapMethod(
            method = "addSittingPassenger(Lnet/minecraft/world/entity/Entity;I)V",
            remap = false)
    private void hexie$addHardSeatPassenger(
            Entity passenger, int seatIndex, Operation<Void> original) {
        if (contraption == null || seatIndex < 0 || seatIndex >= contraption.getSeats().size()) {
            original.call(passenger, seatIndex);
            return;
        }

        BlockPos seatPos = contraption.getSeats().get(seatIndex);
        if (!contraption.getBlocks().containsKey(seatPos)) {
            original.call(passenger, seatIndex);
            return;
        }

        BlockState seatState = contraption.getBlocks().get(seatPos).state();
        if (!(seatState.getBlock() instanceof SeatYZBlock)) {
            original.call(passenger, seatIndex);
            return;
        }

        boolean[] occupied = new boolean[4];
        String tagPrefix = "seats" + seatIndex + ",";
        for (Entity currentPassenger : getPassengers()) {
            for (String tag : currentPassenger.getTags()) {
                if (!tag.startsWith(tagPrefix))
                    continue;
                try {
                    int position = Integer.parseInt(tag.substring(tagPrefix.length()));
                    if (position >= 0 && position < occupied.length)
                        occupied[position] = true;
                } catch (NumberFormatException ignored) {
                    // Ignore stale or malformed seat tags.
                }
            }
        }

        int requestedPosition = getRequestedHardSeatPosition(passenger, seatIndex);
        boolean hasRequestedPosition = requestedPosition >= 0 && requestedPosition < occupied.length;
        if (hasRequestedPosition && occupied[requestedPosition])
            return;
        int position = hasRequestedPosition
                ? requestedPosition
                : findFreeHardSeatPosition(occupied, -1);

        if (position < 0) {
            for (Entity currentPassenger : getPassengers()) {
                if (currentPassenger == passenger
                        || !java.util.Objects.equals(
                                contraption.getSeatMapping().get(currentPassenger.getUUID()), seatIndex)
                        || currentPassenger instanceof Player)
                    continue;
                int currentPosition = getHardSeatPosition(
                        currentPassenger.getTags(), seatPos, contraption);
                if (currentPosition < 0 || currentPosition >= occupied.length)
                    continue;
                currentPassenger.stopRiding();
                position = currentPosition;
                break;
            }
        }

        // A hard seat has four positions; do not fall back to Create's single-seat path.
        if (position < 0) return;

        hexie$mountHardSeatPassenger(passenger, seatIndex, position);
    }

    @Unique
    private void hexie$mountHardSeatPassenger(Entity passenger, int seatIndex, int position) {
        if (!passenger.startRiding(this, true)) return;
        removeHardSeatTags(passenger);
        passenger.addTag(willow.train.hexie.blocks.seats.HardSeatPosition.tag(seatIndex, position));
        if (passenger instanceof TamableAnimal animal)
            animal.setInSittingPose(true);

        if (!level().isClientSide) {
            contraption.getSeatMapping().put(passenger.getUUID(), seatIndex);
            PacketDistributor.sendToPlayersTrackingEntity(
                    this, new ContraptionSeatMappingPacket(getId(), contraption.getSeatMapping()));
            PacketDistributor.sendToPlayersTrackingEntity(
                    this,
                    new ContraptionSeatPositionPacket(
                            getUUID(), passenger.getUUID(), seatIndex, position));
        }
    }

    @WrapMethod(
            method = "handlePlayerInteraction(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/InteractionHand;)Z",
            remap = false)
    private boolean hexie$handleHardSeatInteraction(
            Player player,
            BlockPos localPos,
            Direction side,
            InteractionHand hand,
            Operation<Boolean> original) {
        if (contraption == null || AllItems.WRENCH.isIn(player.getItemInHand(hand)))
            return original.call(player, localPos, side, hand);

        int targetSeatIndex = contraption.getSeats().indexOf(localPos);
        if (targetSeatIndex < 0
                || !contraption.getBlocks().containsKey(localPos)
                || !(contraption.getBlocks().get(localPos).state().getBlock() instanceof SeatYZBlock))
            return original.call(player, localPos, side, hand);

        if (level().isClientSide) {
            int requestedPosition = getClickedHardSeatPosition(player, localPos);
            if (requestedPosition < 0)
                return false;
            PacketDistributor.sendToServer(new SeatYZContraptionInteractionPacket(
                    getId(), hand, localPos, side, requestedPosition));
            // The custom payload carries the exact seat quadrant; suppress Create's
            // position-less interaction payload for this block.
            return false;
        }

        if (player.isPassenger() && player.getVehicle() != this)
            return original.call(player, localPos, side, hand);

        BlockState targetSeatState = contraption.getBlocks().get(localPos).state();
        int requestedPosition = getRequestedHardSeatPosition(player, targetSeatIndex);
        if (requestedPosition < 0)
            requestedPosition = getClickedHardSeatPosition(player, localPos);
        if (requestedPosition < 0 || requestedPosition >= 4)
            return true;

        Entity passenger = SeatBlock.getLeashed(level(), player).or(player);
        boolean[] occupied = getHardSeatOccupancy(targetSeatIndex, passenger);
        int seatPosition = SeatYZBlock.getNearestAvailablePosition(
                targetSeatState, requestedPosition, occupied);
        if (seatPosition < 0)
            return true;

        Integer currentSeatIndex = contraption.getSeatMapping().get(passenger.getUUID());
        if (currentSeatIndex != null
                && currentSeatIndex == targetSeatIndex
                && getHardSeatPosition(
                        passenger.getTags(), contraption.getSeats().get(currentSeatIndex), contraption)
                        == seatPosition)
            return true;

        if (passenger.getVehicle() == this)
            passenger.stopRiding();
        // Native interaction rejects any second player on the same block before
        // addSittingPassenger runs. Mount the validated free quadrant directly.
        hexie$mountHardSeatPassenger(passenger, targetSeatIndex, seatPosition);
        return true;
    }

    @Unique
    private int getClickedHardSeatPosition(Player player, BlockPos localPos) {
        if (contraption == null || !contraption.getBlocks().containsKey(localPos))
            return -1;

        BlockState state = contraption.getBlocks().get(localPos).state();
        if (level().isClientSide && (Object) this instanceof willow.train.hexie.compat.create.SnapshotCarriageEntity car) {
            var hit = willow.train.hexie.client.CarriageInteractionCoordinates.pick(player, car);
            return hit != null && hit.getBlockPos().equals(localPos)
                    ? SeatYZBlock.getPositionForHit(state, localPos, hit.getLocation()) : -1;
        }
        Vec3 origin = player.getEyePosition();
        Vec3 target = origin.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        var entity = (AbstractContraptionEntity) (Object) this;
        Vec3 localOrigin = entity.toLocalVector(origin, 1.0F);
        Vec3 localTarget = entity.toLocalVector(target, 1.0F);
        VoxelShape shape = state.getShape(contraption.getContraptionWorld(), BlockPos.ZERO.below());
        BlockHitResult hit = shape.clip(localOrigin, localTarget, localPos);
        if (hit == null)
            return -1;
        return SeatYZBlock.getPositionForHit(state, localPos, hit.getLocation());
    }

    @WrapMethod(
            method = "getPassengerPosition(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;",
            remap = false)
    private Vec3 hexie$positionHardSeatPassenger(
            Entity passenger, float partialTicks, Operation<Vec3> original) {
        if (contraption == null)
            return original.call(passenger, partialTicks);

        UUID passengerId = passenger.getUUID();
        BlockPos seatPos = contraption.getSeatOf(passengerId);
        if (seatPos == null || !contraption.getBlocks().containsKey(seatPos))
            return original.call(passenger, partialTicks);

        BlockState state = contraption.getBlocks().get(seatPos).state();
        if (state.getBlock() instanceof CRH2DriverSeatBlock || state.getBlock() instanceof RZSeatBlock) {
            Vec3 eyeOffset = state.getBlock() instanceof RZSeatBlock
                    ? new Vec3(.5, RZSeatBlock.EYE_HEIGHT, .5)
                    : CRH2DriverSeatBlock.driverEyePosition(state);
            Vec3 eye = Vec3.atLowerCornerOf(seatPos).add(eyeOffset);
            // Native positionRider adds the custom seat offset minus 1/8 block.
            return ((AbstractContraptionEntity) (Object) this).toGlobalVector(eye, partialTicks)
                    .add(0, .125 - SeatEntity.getCustomEntitySeatOffset(passenger) - passenger.getEyeHeight(), 0);
        }
        if (!(state.getBlock() instanceof SeatYZBlock))
            return original.call(passenger, partialTicks);

        int seatIndex = contraption.getSeats().indexOf(seatPos);
        int position = ContraptionSeatPositionCache.get(getUUID(), passengerId, seatIndex);
        if (position < 0)
            position = getHardSeatPosition(passenger.getTags(), seatPos, contraption);
        if (position < 0)
            return original.call(passenger, partialTicks);

        double passengerHeight = passenger.getBoundingBox().getYsize();
        Vec3 seatOffset = SeatYZBlock.getSeatCoordinates(state, position);
        double customSeatOffset = SeatEntity.getCustomEntitySeatOffset(passenger);
        Vec3 localPosition = Vec3.atLowerCornerOf(seatPos)
                .add(
                        seatOffset.x,
                        -passenger.getVehicleAttachmentPoint(this).y
                                + passengerHeight + .125 - customSeatOffset,
                        seatOffset.z);
        return ((AbstractContraptionEntity) (Object) this).toGlobalVector(localPosition, partialTicks)
                .add(.5, .5, .5)
                .subtract(.5, passengerHeight, .5);
    }

    @WrapMethod(method = "removePassenger(Lnet/minecraft/world/entity/Entity;)V")
    private void hexie$clearHardSeatPosition(Entity passenger, Operation<Void> original) {
        int seatIndex = -1;
        BlockPos dismountSeat = contraption == null ? null : contraption.getSeatOf(passenger.getUUID());
        if (contraption != null) {
            Integer mappedIndex = contraption.getSeatMapping().get(passenger.getUUID());
            if (mappedIndex != null
                    && mappedIndex >= 0
                    && mappedIndex < contraption.getSeats().size()) {
                BlockPos seatPos = contraption.getSeats().get(mappedIndex);
                if (contraption.getBlocks().containsKey(seatPos)
                        && contraption.getBlocks().get(seatPos).state().getBlock() instanceof SeatYZBlock)
                    seatIndex = mappedIndex;
            }
        }
        original.call(passenger);
        if (!level().isClientSide && passenger instanceof Player && dismountSeat != null
                && contraption instanceof com.simibubi.create.content.trains.entity.CarriageContraption) {
            Vec3 feet = null;
            if ((Object) this instanceof willow.train.hexie.compat.create.SnapshotCarriageEntity car
                    && passenger instanceof ServerPlayer player && player.isAlive() && !player.isRemoved()
                    && !car.isRemoved() && player.connection.isAcceptingMessages()) {
                if (player.connection instanceof willow.train.hexie.vehicle.player.CarriedPlayerConnection connection) {
                    willow.train.hexie.compat.create.CarriagePlayerSupport.beginDismount(player, car);
                    connection.hexie$beginCarriageDismount(car.getId(), car.getUUID());
                }
            } else if (!((Object) this instanceof willow.train.hexie.compat.create.SnapshotCarriageEntity)) {
                feet = willow.train.hexie.compat.create.TrainSeatDismount.behind(
                        (AbstractContraptionEntity) (Object) this, dismountSeat);
            }
            if (feet != null) passenger.getPersistentData().put("ContraptionDismountLocation",
                    net.createmod.catnip.math.VecHelper.writeNBT(feet));
        }
        // The client temporarily ejects every passenger when applying a native passenger-list
        // packet. Only the server's explicit removal delta may discard the quadrant cache.
        if (seatIndex >= 0 && !level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(this,
                    new ContraptionSeatPositionPacket(getUUID(), passenger.getUUID(), seatIndex, -1));
        }
        removeHardSeatTags(passenger);
    }

    @Unique
    private boolean[] getHardSeatOccupancy(int seatIndex, Entity ignoredPassenger) {
        boolean[] occupied = new boolean[4];
        for (Entity passenger : getPassengers()) {
            if (passenger == ignoredPassenger) continue;
            int position = willow.train.hexie.blocks.seats.HardSeatPosition.read(passenger.getTags(), seatIndex);
            if (position >= 0) occupied[position] = true;
        }
        return occupied;
    }

    @Unique
    private static int findFreeHardSeatPosition(boolean[] occupied, int afterPosition) {
        int start = afterPosition < 0 ? 0 : afterPosition + 1;
        for (int i = start; i < occupied.length; i++)
            if (!occupied[i])
                return i;
        for (int i = 0; i < Math.min(start, occupied.length); i++)
            if (!occupied[i])
                return i;
        return -1;
    }

    @Unique
    private static int getRequestedHardSeatPosition(Entity passenger, int seatIndex) {
        return HardSeatPositionRequest.get(passenger, seatIndex);
    }

    @Unique
    private static int getHardSeatPosition(
            java.util.Set<String> tags, BlockPos seatPos, Contraption contraption) {
        return willow.train.hexie.blocks.seats.HardSeatPosition.read(tags, contraption.getSeats().indexOf(seatPos));
    }

    @Unique
    private static void removeHardSeatTags(Entity passenger) {
        willow.train.hexie.blocks.seats.HardSeatPosition.clear(passenger);
    }
}
