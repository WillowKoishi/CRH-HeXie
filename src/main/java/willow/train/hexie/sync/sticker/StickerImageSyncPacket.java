package willow.train.hexie.sync.sticker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

/** S2C: delivers uploaded decal PNG bytes to clients. */
public record StickerImageSyncPacket(String hash, byte[] pngBytes) implements CustomPacketPayload {
    public static final Type<StickerImageSyncPacket> TYPE = new Type<>(HexieConstants.asResource("sticker_image_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerImageSyncPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.hash, 128);
                buf.writeByteArray(packet.pngBytes);
            },
            buf -> new StickerImageSyncPacket(buf.readUtf(128), buf.readByteArray(StickerImageUploadPacket.MAX_BYTES)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> HexieBridges.sticker().putClientImage(hash, pngBytes));
    }
}
