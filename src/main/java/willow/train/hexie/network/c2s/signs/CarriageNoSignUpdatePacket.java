package willow.train.hexie.network.c2s.signs;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import willow.train.hexie.network.c2s.HexiePacket;
import willow.train.hexie.blockentity.CarriageNoSignEntity;

public class CarriageNoSignUpdatePacket implements HexiePacket {

    private final BlockPos pos;
    private final Component message;
    private final int color;

    private final boolean isLeftSide;

    public CarriageNoSignUpdatePacket(
            BlockPos pos, Component message, int color, boolean isLeftSide) {
        this.pos = pos;
        this.message = message;
        this.color = color;
        this.isLeftSide = isLeftSide;
    }

    public CarriageNoSignUpdatePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.color = buffer.readInt();
        this.message = Component.literal(buffer.readUtf());
        this.isLeftSide = buffer.readBoolean();
    }

    @Override
    public boolean handle(IPayloadContext var1) {
        var1.enqueueWork(
                () -> {
                    ServerLevel level = ((net.minecraft.server.level.ServerPlayer) var1.player()).serverLevel();
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity == null) {
                        return;
                    }

                    if (entity instanceof CarriageNoSignEntity) {
                        ((CarriageNoSignEntity) entity).setMessage(message);
                        ((CarriageNoSignEntity) entity).setColor(color);
                        ((CarriageNoSignEntity) entity).setLeftSide(isLeftSide);
                        entity.setChanged();
                        ((CarriageNoSignEntity) entity).sendData();
                    }
                });
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf var1) {
        var1.writeBlockPos(pos);
        var1.writeInt(color);
        var1.writeUtf(message.getString());
        var1.writeBoolean(isLeftSide);
    }
}
