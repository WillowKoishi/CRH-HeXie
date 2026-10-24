package willow.train.hexie.network.s2c;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;


public record ContraptionSeatPositionPacket(
        UUID contraptionId, UUID passengerId, int seatIndex, int position)
        implements CustomPacketPayload {

    public static final Type<ContraptionSeatPositionPacket> TYPE =
            new Type<>(Main.asResource("contraption_seat_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionSeatPositionPacket> CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeUUID(packet.contraptionId());
                        buffer.writeUUID(packet.passengerId());
                        buffer.writeVarInt(packet.seatIndex());
                        buffer.writeVarInt(packet.position());
                    },
                    buffer -> new ContraptionSeatPositionPacket(
                            buffer.readUUID(),
                            buffer.readUUID(),
                            buffer.readVarInt(),
                            buffer.readVarInt()));

    public void handle(IPayloadContext context) {
        context.enqueueWork(
                () -> ContraptionSeatPositionCache.update(
                        contraptionId,
                        passengerId,
                        seatIndex,
                        position));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
