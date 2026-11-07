package willow.train.hexie.network.c2s.signs;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import willow.train.hexie.network.c2s.HexiePacket;
import willow.train.hexie.blockentity.CarriageNoSignEntity;

public class CarriageNoSignUpdatePacket implements HexiePacket {
    private int menuId = -1;
    public CarriageNoSignUpdatePacket withMenuId(int id) { menuId = id; return this; }

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
        this.message = Component.literal(buffer.readUtf(SignEditSubmission.MAX_TEXT_LENGTH));
        this.isLeftSide = buffer.readBoolean();
        if (buffer.isReadable()) menuId = buffer.readVarInt();
    }

    @Override
    public boolean handle(IPayloadContext var1) {
        var1.enqueueWork(() -> SignEditSubmission.commit(var1.player(), pos, menuId, CarriageNoSignEntity.class, entity -> {
            entity.setMessage(message);
            entity.setColor(color);
            entity.setLeftSide(isLeftSide);
        }));
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf var1) {
        var1.writeBlockPos(pos);
        var1.writeInt(color);
        var1.writeUtf(message.getString());
        var1.writeBoolean(isLeftSide);
        var1.writeVarInt(menuId);
    }
}
