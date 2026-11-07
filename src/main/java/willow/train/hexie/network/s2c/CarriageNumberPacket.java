package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.api.display.PassengerCarriageNumber;
import willow.train.hexie.client.sign.CarriageNumberUpdates;

/** A numeric content edit carries no arbitrary block-entity data or attachment changes. */
public record CarriageNumberPacket(int entityId, UUID entityUuid, BlockPos rootPos, String number)
        implements CustomPacketPayload {
    public static final Type<CarriageNumberPacket> TYPE = new Type<>(Main.asResource("carriage_number"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriageNumberPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.entityId());
                buffer.writeUUID(packet.entityUuid());
                buffer.writeBlockPos(packet.rootPos());
                buffer.writeUtf(packet.number(), PassengerCarriageNumber.MAX_DIGITS);
            }, buffer -> new CarriageNumberPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readUtf(PassengerCarriageNumber.MAX_DIGITS)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> CarriageNumberUpdates.apply(context.player().level(), this));
    }
}
