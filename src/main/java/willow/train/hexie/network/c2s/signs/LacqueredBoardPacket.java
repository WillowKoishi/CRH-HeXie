package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import willow.train.hexie.network.c2s.HexiePacket;
import willow.train.hexie.blockentity.LacqueredBoardEntity;

public class LacqueredBoardPacket implements HexiePacket {

    int[] colors;
    String[] contents;

    double x_offset;

    BlockPos pos;

    public LacqueredBoardPacket(
            BlockPos pPos, Component[] contents, double x_offset, int... colors) {
        this.pos = pPos;
        this.contents = new String[contents.length];
        this.x_offset = x_offset;
        for (int i = 0; i < contents.length; i++) {
            this.contents[i] = contents[i].getString();
        }
        this.colors = colors;
    }

    public LacqueredBoardPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        int sLength = buf.readInt();
        int cLength = buf.readInt();
        this.x_offset = buf.readDouble();
        this.contents = new String[sLength];
        this.colors = new int[cLength];

        for (int i = 0; i < sLength; i++) {
            this.contents[i] = buf.readUtf();
        }
        for (int i = 0; i < cLength; i++) {
            this.colors[i] = buf.readInt();
        }
    }

    @Override
    public boolean handle(IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    ServerLevel level = ((net.minecraft.server.level.ServerPlayer) context.player()).serverLevel();
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity == null) {
                        return;
                    }
                    if (entity instanceof LacqueredBoardEntity) {
                        ((LacqueredBoardEntity) entity).setMessages(this.contents);
                        ((LacqueredBoardEntity) entity).setColors(this.colors);
                        ((LacqueredBoardEntity) entity).setXOffset(this.x_offset);
                        entity.setChanged();
                        ((LacqueredBoardEntity) entity).sendData();
                    }
                });
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf var1) {
        var1.writeBlockPos(this.pos);
        var1.writeInt(this.contents.length); // String[] 长度
        var1.writeInt(this.colors.length); // int[] 长度
        var1.writeDouble(this.x_offset);
        for (String s : contents) {
            var1.writeUtf(s);
        }
        for (int c : colors) {
            var1.writeInt(c);
        }
    }
}
