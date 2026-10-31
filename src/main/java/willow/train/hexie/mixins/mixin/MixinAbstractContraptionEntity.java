package willow.train.hexie.mixins.mixin;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.spongepowered.asm.mixin.gen.Invoker;
import willow.train.hexie.blocks.seats.HardSeatPositionRequest;
import willow.train.hexie.blocks.seats.SeatYZBlock;
import willow.train.hexie.network.c2s.SeatYZContraptionInteractionPacket;
import willow.train.hexie.network.s2c.ContraptionSeatPositionCache;
import willow.train.hexie.network.s2c.ContraptionSeatPositionPacket;

import java.util.UUID;

/**
 * Adds the four passenger positions for HeXie's hard seat while leaving Create's
 * normal contraption seat implementation intact for other seat blocks and addons.
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity extends Entity {

    @Shadow(remap = false)
    protected Contraption contraption;

    protected MixinAbstractContraptionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Invoker(value = "toGlobalVector", remap = false)
    protected abstract Vec3 hexie$toGlobalVector(Vec3 localVec, float partialTicks);

    @Invoker(value = "toLocalVector", remap = false)
    protected abstract Vec3 hexie$toLocalVector(Vec3 globalVec, float partialTicks);

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

        removeHardSeatTags(passenger);
        passenger.addTag(tagPrefix + position);
        passenger.startRiding(this, true);
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

        boolean[] occupied = getHardSeatOccupancy(targetSeatIndex, player);
        int seatPosition = SeatYZBlock.getNearestAvailablePosition(
                targetSeatState, requestedPosition, occupied);
        if (seatPosition < 0)
            return true;

        Integer currentSeatIndex = contraption.getSeatMapping().get(player.getUUID());
        if (currentSeatIndex != null
                && currentSeatIndex == targetSeatIndex
                && getHardSeatPosition(
                        player.getTags(), contraption.getSeats().get(currentSeatIndex), contraption)
                        == seatPosition)
            return true;

        if (player.getVehicle() == this)
            player.stopRiding();

        String requestedPositionTag = hardSeatTargetTag(targetSeatIndex, seatPosition);
        player.addTag(requestedPositionTag);
        try {
            return original.call(player, localPos, side, hand);
        } finally {
            player.removeTag(requestedPositionTag);
        }
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
        Vec3 localOrigin = hexie$toLocalVector(origin, 1.0F);
        Vec3 localTarget = hexie$toLocalVector(target, 1.0F);
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
        if (state.getBlock() instanceof willow.train.hexie.blocks.locos.CRH2DriverSeatBlock) {
            Vec3 eye = Vec3.atLowerCornerOf(seatPos).add(
                    willow.train.hexie.blocks.locos.CRH2DriverSeatBlock.driverEyePosition(state));
            return hexie$toGlobalVector(eye, partialTicks).subtract(0, passenger.getEyeHeight(), 0);
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
        return hexie$toGlobalVector(localPosition, partialTicks)
                .add(.5, .5, .5)
                .subtract(.5, passengerHeight, .5);
    }

    @WrapMethod(method = "removePassenger(Lnet/minecraft/world/entity/Entity;)V")
    private void hexie$clearHardSeatPosition(Entity passenger, Operation<Void> original) {
        int seatIndex = -1;
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
        if (seatIndex >= 0) {
            if (level().isClientSide) {
                ContraptionSeatPositionCache.update(getUUID(), passenger.getUUID(), seatIndex, -1);
            } else {
                PacketDistributor.sendToPlayersTrackingEntity(
                        this,
                        new ContraptionSeatPositionPacket(
                                getUUID(), passenger.getUUID(), seatIndex, -1));
            }
        }
        removeHardSeatTags(passenger);
    }

    @Unique
    private boolean[] getHardSeatOccupancy(int seatIndex, Entity ignoredPassenger) {
        boolean[] occupied = new boolean[4];
        String tagPrefix = "seats" + seatIndex + ",";
        for (Entity currentPassenger : getPassengers()) {
            if (currentPassenger == ignoredPassenger)
                continue;
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
    private static String hardSeatTargetTag(int seatIndex, int position) {
        return HardSeatPositionRequest.tag(seatIndex, position);
    }

    @Unique
    private static int getRequestedHardSeatPosition(Entity passenger, int seatIndex) {
        return HardSeatPositionRequest.get(passenger, seatIndex);
    }

    @Unique
    private static int getHardSeatPosition(
            java.util.Set<String> tags, BlockPos seatPos, Contraption contraption) {
        for (String tag : tags) {
            if (!tag.startsWith("seats"))
                continue;
            String[] values = tag.substring("seats".length()).split(",", -1);
            if (values.length != 2)
                continue;
            try {
                int seatIndex = Integer.parseInt(values[0]);
                int position = Integer.parseInt(values[1]);
                if (seatIndex >= 0 && seatIndex < contraption.getSeats().size()
                        && contraption.getSeats().get(seatIndex).equals(seatPos)
                        && position >= 0 && position < 4)
                    return position;
            } catch (NumberFormatException ignored) {
                // Ignore stale or malformed seat tags.
            }
        }
        return -1;
    }

    @Unique
    private static void removeHardSeatTags(Entity passenger) {
        for (String tag : java.util.Set.copyOf(passenger.getTags())) {
            if (tag.startsWith("seats"))
                passenger.removeTag(tag);
        }
    }
}
