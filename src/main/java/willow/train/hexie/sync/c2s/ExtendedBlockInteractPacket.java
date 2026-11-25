package willow.train.hexie.sync.c2s;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.core.HexieConstants;

/** A client-selected face in the owner's storage coordinate system; server trusts the client's geometric pick. */
public record ExtendedBlockInteractPacket(BlockPos pos, Direction face, Vec3 location, InteractionHand hand,
                                          boolean inside) implements CustomPacketPayload {
    public static final Type<ExtendedBlockInteractPacket> TYPE =
            new Type<>(HexieConstants.asResource("extended_block_interact"));
    public static final StreamCodec<FriendlyByteBuf, ExtendedBlockInteractPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeEnum(packet.face);
                buf.writeVec3(packet.location);
                buf.writeEnum(packet.hand);
                buf.writeBoolean(packet.inside);
            }, buf -> new ExtendedBlockInteractPacket(buf.readBlockPos(), buf.readEnum(Direction.class),
                    buf.readVec3(), buf.readEnum(InteractionHand.class), buf.readBoolean()));

    public void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        if (!level.isLoaded(pos)) return;
        var stack = player.getItemInHand(hand);
        if (stack.isItemEnabled(level.enabledFeatures())) {
            player.gameMode.useItemOn(player, level, stack, hand, new BlockHitResult(location, face, pos, inside));
        }
    }

    @Override public Type<ExtendedBlockInteractPacket> type() { return TYPE; }
}
