package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.api.display.PassengerDisplayState;
import willow.train.hexie.client.sign.PassengerDisplayCache;

/** One small projection per carriage; clients never receive or execute its timetable. */
public record PassengerDisplayPacket(UUID carriage, PassengerDisplayState state) implements CustomPacketPayload {
    private static final int MAX_STRING_LENGTH = 512;
    public static final Type<PassengerDisplayPacket> TYPE = new Type<>(Main.asResource("passenger_display"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PassengerDisplayPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.carriage());
                buffer.writeUtf(packet.state().trainNumber(), MAX_STRING_LENGTH);
                buffer.writeUtf(packet.state().origin(), MAX_STRING_LENGTH);
                buffer.writeUtf(packet.state().destination(), MAX_STRING_LENGTH);
                buffer.writeUtf(packet.state().nextStop(), MAX_STRING_LENGTH);
            }, buffer -> new PassengerDisplayPacket(buffer.readUUID(), new PassengerDisplayState(
                    buffer.readUtf(MAX_STRING_LENGTH), buffer.readUtf(MAX_STRING_LENGTH),
                    buffer.readUtf(MAX_STRING_LENGTH), buffer.readUtf(MAX_STRING_LENGTH))));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> PassengerDisplayCache.accept(context.player().level(), carriage, state));
    }
}
