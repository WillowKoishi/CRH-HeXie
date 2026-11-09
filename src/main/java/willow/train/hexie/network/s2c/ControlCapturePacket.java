package willow.train.hexie.network.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.network.c2s.ControlInputPacket.Target;

/** Confirms or revokes only the matching client capture attempt, never a newer attempt on the same block. */
public record ControlCapturePacket(Target target, int vehicleId, BlockPos position, long requestId, boolean accepted)
        implements CustomPacketPayload {
    public static final Type<ControlCapturePacket> TYPE = new Type<>(Main.asResource("control_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlCapturePacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeEnum(packet.target); buffer.writeVarInt(packet.vehicleId); buffer.writeBlockPos(packet.position);
                buffer.writeVarLong(packet.requestId); buffer.writeBoolean(packet.accepted);
            }, buffer -> new ControlCapturePacket(buffer.readEnum(Target.class), buffer.readVarInt(), buffer.readBlockPos(),
                    buffer.readVarLong(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> willow.train.hexie.client.interaction.SurfaceInputClient.captureResult(
                target, vehicleId, position, requestId, accepted));
    }
}
