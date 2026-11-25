package willow.train.hexie.sync.s2c;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge.CreatePanelEditTargetContentType;
import willow.train.hexie.bridge.sync.SyncBridge.PanelEditPacketView;
import willow.train.hexie.core.HexieConstants;

/** Only the selected editor's whitelisted content is sent; captured metadata and layout remain local. */
public record PanelEditPacket(int entityId, UUID entityUuid, BlockPos rootPos, CreatePanelEditTargetContentType contentType,
        int slot, CompoundTag content) implements CustomPacketPayload, PanelEditPacketView {
    public static final Type<PanelEditPacket> TYPE = new Type<>(HexieConstants.asResource("panel_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelEditPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.entityId()); buffer.writeUUID(packet.entityUuid());
                buffer.writeBlockPos(packet.rootPos()); buffer.writeEnum(packet.contentType());
                buffer.writeVarInt(packet.slot()); buffer.writeNbt(packet.content());
            }, buffer -> new PanelEditPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readEnum(CreatePanelEditTargetContentType.class), buffer.readVarInt(), buffer.readNbt()));
    public PanelEditPacket { content = content == null ? new CompoundTag() : content.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> HexieBridges.Client.displayRender().panelEditUpdates().apply(context.player().level(), this));
    }
}
