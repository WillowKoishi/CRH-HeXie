package willow.train.hexie.network.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.client.SnapshotControlsClient;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

public record NativeControlsStartedPacket(int carriageId, BlockPos controls) implements CustomPacketPayload {
    public static final Type<NativeControlsStartedPacket> TYPE = new Type<>(Main.asResource("native_controls_started"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeControlsStartedPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NativeControlsStartedPacket::carriageId,
            BlockPos.STREAM_CODEC, NativeControlsStartedPacket::controls, NativeControlsStartedPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getEntity(carriageId) instanceof SnapshotCarriageEntity carriage)
                SnapshotControlsClient.startConfirmed(carriage, controls);
        });
    }
}
