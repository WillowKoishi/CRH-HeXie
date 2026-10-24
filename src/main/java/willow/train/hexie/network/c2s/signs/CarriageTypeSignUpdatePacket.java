package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import willow.train.hexie.network.c2s.HexiePacket;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;

public class CarriageTypeSignUpdatePacket implements HexiePacket {
    private final BlockPos pos;

    private int color;
    private final String[] lines;


    public CarriageTypeSignUpdatePacket(
            BlockPos pPos,
            String pLine0,
            String pLine1,
            String pLine2,
            String pLine3,
            String pLine4,
            int pColor) {
        this.pos = pPos;
        this.lines = new String[] {pLine0, pLine1, pLine2, pLine3, pLine4};
        this.color = pColor;
    }


    public CarriageTypeSignUpdatePacket(FriendlyByteBuf pBuffer) {
        this.pos = pBuffer.readBlockPos();
        this.color = pBuffer.readInt();
        this.lines = new String[5];

        for (int i = 0; i < 5; ++i) {
            this.lines[i] = pBuffer.readUtf(384);
        }
    }

    public BlockPos getPos() {
        return this.pos;
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
                    if (entity instanceof CarriageTypeSignEntity) {
                        ((CarriageTypeSignEntity) entity).setMessages(this.lines);
                        ((CarriageTypeSignEntity) entity).setColor(color);
                        entity.setChanged();
                        ((CarriageTypeSignEntity) entity).sendData();
                    }
                });
        return true;
    }


    @Override
    public void encode(FriendlyByteBuf pBuffer) {
        pBuffer.writeBlockPos(this.pos);
        pBuffer.writeInt(color);

        for (int i = 0; i < 5; ++i) {
            pBuffer.writeUtf(this.lines[i]);
        }
    }
}
