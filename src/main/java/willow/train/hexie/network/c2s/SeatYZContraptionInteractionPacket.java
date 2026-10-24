package willow.train.hexie.network.c2s;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.seats.HardSeatPositionRequest;
import willow.train.hexie.blocks.seats.SeatYZBlock;


public record SeatYZContraptionInteractionPacket(
        int contraptionEntityId,
        InteractionHand hand,
        BlockPos localPos,
        Direction side,
        int position)
        implements CustomPacketPayload {

    public static final Type<SeatYZContraptionInteractionPacket> TYPE =
            new Type<>(Main.asResource("seat_yz_contraption_interaction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeatYZContraptionInteractionPacket> CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeVarInt(packet.contraptionEntityId());
                        buffer.writeVarInt(packet.hand().ordinal());
                        buffer.writeBlockPos(packet.localPos());
                        buffer.writeVarInt(packet.side().get3DDataValue());
                        buffer.writeVarInt(packet.position());
                    },
                    buffer -> {
                        int entityId = buffer.readVarInt();
                        int handId = buffer.readVarInt();
                        InteractionHand[] hands = InteractionHand.values();
                        InteractionHand hand = hands[Math.floorMod(handId, hands.length)];
                        BlockPos localPos = buffer.readBlockPos();
                        Direction side = Direction.from3DDataValue(buffer.readVarInt());
                        return new SeatYZContraptionInteractionPacket(
                                entityId, hand, localPos, side, buffer.readVarInt());
                    });

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)
                    || sender.isSpectator()
                    || position < 0
                    || position >= 4
                    || AllItems.WRENCH.isIn(sender.getItemInHand(hand)))
                return;

            Entity entity = sender.level().getEntity(contraptionEntityId);
            if (!(entity instanceof AbstractContraptionEntity contraptionEntity))
                return;

            Contraption contraption = contraptionEntity.getContraption();
            if (contraption == null)
                return;
            int seatIndex = contraption.getSeats().indexOf(localPos);
            if (seatIndex < 0
                    || !contraption.getBlocks().containsKey(localPos)
                    || !(contraption.getBlocks().get(localPos).state().getBlock() instanceof SeatYZBlock))
                return;

            AABB bounds = contraptionEntity.getBoundingBox();
            double maxExtra = Math.max(bounds.getXsize(), bounds.getYsize());
            double allowedDistance = sender.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
                    + 10 + maxExtra;
            if (!sender.hasLineOfSight(entity))
                allowedDistance -= 3;
            if (sender.distanceToSqr(entity) > allowedDistance * allowedDistance)
                return;

            String requestTag = HardSeatPositionRequest.tag(seatIndex, position);
            sender.addTag(requestTag);
            try {
                if (contraptionEntity.handlePlayerInteraction(sender, localPos, side, hand))
                    sender.swing(hand, true);
            } finally {
                sender.removeTag(requestTag);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
