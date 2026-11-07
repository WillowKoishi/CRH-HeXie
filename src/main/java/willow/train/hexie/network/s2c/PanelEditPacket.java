package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.client.sign.PanelEditUpdates;
import willow.train.hexie.compat.create.CreatePanelEditTarget.ContentType;

/** Only the selected editor's whitelisted content is sent; captured metadata and layout remain local. */
public record PanelEditPacket(int entityId, UUID entityUuid, BlockPos rootPos, ContentType contentType,
        int slot, CompoundTag content) implements CustomPacketPayload {
    public static final Type<PanelEditPacket> TYPE = new Type<>(Main.asResource("panel_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelEditPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.entityId()); buffer.writeUUID(packet.entityUuid());
                buffer.writeBlockPos(packet.rootPos()); buffer.writeEnum(packet.contentType());
                buffer.writeVarInt(packet.slot()); buffer.writeNbt(packet.content());
            }, buffer -> new PanelEditPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readEnum(ContentType.class), buffer.readVarInt(), buffer.readNbt()));
    public PanelEditPacket { content = content == null ? new CompoundTag() : content.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> PanelEditUpdates.apply(context.player().level(), this));
    }
}
