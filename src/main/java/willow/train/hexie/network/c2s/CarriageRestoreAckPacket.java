package willow.train.hexie.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Completes a seat/login placement handoff before the client's attached or world-exit report. */
public record CarriageRestoreAckPacket(long token) implements CustomPacketPayload {
    public static final Type<CarriageRestoreAckPacket> TYPE = new Type<>(Main.asResource("carriage_restore_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriageRestoreAckPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeVarLong(packet.token),
            buffer -> new CarriageRestoreAckPacket(buffer.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.connection instanceof CarriedPlayerConnection connection)
                connection.hexie$acknowledgeCarriageRestore(token);
        });
    }
}
