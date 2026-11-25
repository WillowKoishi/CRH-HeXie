package willow.train.hexie.sync.sticker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.StickerEntry;

/** C2S: place or update a custom window/decal on an unassembled world or physical-structure block. */
public record StickerUpsertPacket(StickerEntry entry) implements CustomPacketPayload {
    public static final Type<StickerUpsertPacket> TYPE = new Type<>(HexieConstants.asResource("sticker_upsert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerUpsertPacket> CODEC = StreamCodec.of(
            (buf, packet) -> packet.entry.writeBuf(buf),
            buf -> new StickerUpsertPacket(StickerEntry.readBuf(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.mayBuild() || player.isSpectator()) return;
            if (player.level() instanceof ServerLevel serverLevel) {
                HexieBridges.sticker().upsertServer(serverLevel, entry);
            }
        });
    }
}
