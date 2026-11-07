package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.menu.CarriageMarkingMenu;
import willow.train.hexie.network.c2s.HexiePacket;

public record CarriageMarkingUpdatePacket(BlockPos pos, String number, String chinese, String english, boolean left, int menuId)
        implements HexiePacket {
    public CarriageMarkingUpdatePacket(BlockPos pos, String number, String chinese, String english, boolean left) {
        this(pos, number, chinese, english, left, -1);
    }
    public CarriageMarkingUpdatePacket withMenuId(int id) {
        return new CarriageMarkingUpdatePacket(pos, number, chinese, english, left, id);
    }
    public CarriageMarkingUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(),buf.readUtf(128),buf.readUtf(128),buf.readUtf(128),buf.readBoolean(),buf.isReadable()?buf.readVarInt():-1);
    }
    @Override public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos); buf.writeUtf(number,128);buf.writeUtf(chinese,128);buf.writeUtf(english,128);buf.writeBoolean(left);
        buf.writeVarInt(menuId);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof CarriageMarkingMenu)) return;
            SignEditSubmission.commit(context.player(), pos, menuId, willow.train.hexie.blocks.parts.VehicleMarkings.class,
                panel -> panel.update(new String[]{number,chinese,english},left));
        });
        return true;
    }
}
