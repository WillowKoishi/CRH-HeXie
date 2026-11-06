package willow.train.hexie.network.c2s;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

public record ReleaseNativeControlsPacket(int carriageId, BlockPos controls) implements HexiePacket {
    public ReleaseNativeControlsPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBlockPos());
    }

    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(carriageId);
        buffer.writeBlockPos(controls);
    }

    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().getEntity(carriageId) instanceof SnapshotCarriageEntity carriage)
                carriage.releaseControls(player, controls);
        });
        return true;
    }
}
