package willow.train.hexie.sync.sticker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

/** C2S: uploads locally selected PNG bytes ONLY when the player right-click confirms decal placement. */
public record StickerImageUploadPacket(byte[] pngBytes) implements CustomPacketPayload {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final Type<StickerImageUploadPacket> TYPE = new Type<>(HexieConstants.asResource("sticker_image_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerImageUploadPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeByteArray(packet.pngBytes),
            buf -> new StickerImageUploadPacket(buf.readByteArray(MAX_BYTES)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.mayBuild() || player.isSpectator()) return;
            if (pngBytes == null || pngBytes.length == 0 || pngBytes.length > MAX_BYTES) return;
            if (player.level() instanceof ServerLevel serverLevel) {
                String hash = HexieBridges.sticker().saveImageServer(serverLevel, pngBytes);
                PacketDistributor.sendToPlayersInDimension(serverLevel, new StickerImageSyncPacket(hash, pngBytes));
            }
        });
    }
}
