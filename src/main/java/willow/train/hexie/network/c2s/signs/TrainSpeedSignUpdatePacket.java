package willow.train.hexie.network.c2s.signs;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import willow.train.hexie.network.c2s.HexiePacket;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;

public class TrainSpeedSignUpdatePacket implements HexiePacket {

    private final BlockPos pos;
    private final String content;
    private final int color;
    private final float x_offset, y_offset;
    private final boolean x_revert, y_revert;

    public TrainSpeedSignUpdatePacket(
            BlockPos pos,
            Component content,
            int color,
            float x_offset,
            float y_offset,
            boolean x_revert,
            boolean y_revert) {
        this.pos = pos;
        this.content = content.getString();
        this.color = color;
        this.x_offset = x_offset;
        this.y_offset = y_offset;
        this.x_revert = x_revert;
        this.y_revert = y_revert;
    }

    public TrainSpeedSignUpdatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.content = buf.readUtf();
        this.color = buf.readInt();
        this.x_offset = buf.readFloat();
        this.y_offset = buf.readFloat();
        this.x_revert = buf.readBoolean();
        this.y_revert = buf.readBoolean();
    }

    @Override
    public boolean handle(IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    ServerLevel level = ((net.minecraft.server.level.ServerPlayer) context.player()).serverLevel();
                    BlockEntity entity = level.getBlockEntity(pos);

                    if (entity instanceof TrainSpeedSignEntity pEntity) {
                        pEntity.setContent(
                                Component.literal(content));
                        pEntity.setColor(color);
                        pEntity.setX_offset(x_offset);
                        pEntity.setY_offset(y_offset);
                        pEntity.setX_revert(x_revert);
                        pEntity.setY_revert(y_revert);
                        pEntity.setChanged();
                        pEntity.sendData();
                    } else {
                        return;
                    }
                });
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf var1) {
        var1.writeBlockPos(pos);
        var1.writeUtf(content);
        var1.writeInt(color);
        var1.writeFloat(x_offset);
        var1.writeFloat(y_offset);
        var1.writeBoolean(x_revert);
        var1.writeBoolean(y_revert);
    }
}
