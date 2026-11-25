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

/** C2S: requests a missing decal image by its SHA-256 hash. */
public record StickerImageRequestPacket(String hash) implements CustomPacketPayload {
    public static final Type<StickerImageRequestPacket> TYPE = new Type<>(HexieConstants.asResource("sticker_image_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerImageRequestPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.hash, 128),
            buf -> new StickerImageRequestPacket(buf.readUtf(128)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level() instanceof ServerLevel serverLevel) {
                HexieBridges.sticker().loadImageServer(serverLevel, hash).ifPresent(
                        bytes -> PacketDistributor.sendToPlayer(player, new StickerImageSyncPacket(hash, bytes)));
            }
        });
    }
}
