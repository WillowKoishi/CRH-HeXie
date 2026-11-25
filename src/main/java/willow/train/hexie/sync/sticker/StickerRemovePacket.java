package willow.train.hexie.sync.sticker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

/** C2S: white-brush removal of custom windows/decals touching (pos, facing). */
public record StickerRemovePacket(BlockPos pos, Direction facing) implements CustomPacketPayload {
    public static final Type<StickerRemovePacket> TYPE = new Type<>(HexieConstants.asResource("sticker_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickerRemovePacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeEnum(packet.facing);
            },
            buf -> new StickerRemovePacket(buf.readBlockPos(), buf.readEnum(Direction.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.mayBuild() || player.isSpectator()) return;
            if (player.level() instanceof ServerLevel serverLevel) {
                HexieBridges.sticker().removeAtServer(serverLevel, pos, facing);
            }
        });
    }
}
