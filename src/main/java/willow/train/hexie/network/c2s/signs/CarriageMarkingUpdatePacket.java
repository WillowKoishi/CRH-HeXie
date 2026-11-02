package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.menu.CarriageMarkingMenu;
import willow.train.hexie.network.c2s.HexiePacket;

public record CarriageMarkingUpdatePacket(BlockPos pos, String number, String chinese, String english, boolean left)
        implements HexiePacket {
    public CarriageMarkingUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(),buf.readUtf(128),buf.readUtf(128),buf.readUtf(128),buf.readBoolean());
    }
    @Override public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos); buf.writeUtf(number,128);buf.writeUtf(chinese,128);buf.writeUtf(english,128);buf.writeBoolean(left);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CarriageMarkingMenu menu)
                    || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (player.level().getBlockEntity(pos) instanceof CRH2PartEntity panel)
                panel.update(new String[]{number,chinese,english},left);
        });
        return true;
    }
}
