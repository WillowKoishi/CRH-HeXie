package willow.train.hexie.sync.sticker;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.StickerEntry;

/** S2C: synchronizes all custom stickers belonging to a chunk. */
public record StickerChunkSyncPacket(int chunkX, int chunkZ, List<StickerEntry> entries) implements CustomPacketPayload {
    public static final Type<StickerChunkSyncPacket> TYPE = new Type<>(HexieConstants.asResource("sticker_chunk_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerChunkSyncPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.chunkX);
                buf.writeInt(packet.chunkZ);
                buf.writeVarInt(packet.entries.size());
                for (var e : packet.entries) e.writeBuf(buf);
            },
            buf -> {
                int cx = buf.readInt();
                int cz = buf.readInt();
                int count = buf.readVarInt();
                var list = new ArrayList<StickerEntry>(Math.min(count, 4096));
                for (int i = 0; i < count; i++) list.add(StickerEntry.readBuf(buf));
                return new StickerChunkSyncPacket(cx, cz, List.copyOf(list));
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) return;
            HexieBridges.sticker().applyChunkSyncClient(context.player().level(), chunkX, chunkZ, entries);
        });
    }
}
