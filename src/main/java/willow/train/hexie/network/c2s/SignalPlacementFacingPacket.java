package willow.train.hexie.network.c2s;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.SignalPlacementContext;
import willow.train.hexie.init.semaphore.SemaphoreBlockInit;

public record SignalPlacementFacingPacket(BlockPos pos, InteractionHand hand, Direction facing)
        implements CustomPacketPayload {
    public static final Type<SignalPlacementFacingPacket> TYPE =
            new Type<>(Main.asResource("signal_placement_facing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SignalPlacementFacingPacket> CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeBlockPos(packet.pos());
                buffer.writeBoolean(packet.hand() == InteractionHand.MAIN_HAND);
                buffer.writeEnum(packet.facing());
            }, buffer -> new SignalPlacementFacingPacket(buffer.readBlockPos(),
                    buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    buffer.readEnum(Direction.class)));

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.isSpectator() || !facing.getAxis().isHorizontal()
                    || !(SemaphoreBlockInit.HX_SEMAPHORE.isIn(player.getItemInHand(hand))
                            || SemaphoreBlockInit.ADJUSTABLE_SEMAPHORE.isIn(player.getItemInHand(hand)))
                    || !player.canInteractWithBlock(pos, 1.0))
                return;
            SignalPlacementContext.remember(player, pos, hand, facing);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
