package willow.train.hexie.sync.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketTarget;
import willow.train.hexie.core.HexieConstants;

/** Confirms or revokes only the matching client capture attempt, never a newer attempt on the same block. */
public record ControlCapturePacket(ControlInputPacketTarget target, int vehicleId, BlockPos position, long requestId, boolean accepted)
        implements CustomPacketPayload {
    public static final Type<ControlCapturePacket> TYPE = new Type<>(HexieConstants.asResource("control_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlCapturePacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeEnum(packet.target); buffer.writeVarInt(packet.vehicleId); buffer.writeBlockPos(packet.position);
                buffer.writeVarLong(packet.requestId); buffer.writeBoolean(packet.accepted);
            }, buffer -> new ControlCapturePacket(buffer.readEnum(ControlInputPacketTarget.class), buffer.readVarInt(), buffer.readBlockPos(),
                    buffer.readVarLong(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> HexieBridges.Client.surfaceInputClient().surfaceInputClient().captureResult(
                target, vehicleId, position, requestId, accepted));
    }
}
