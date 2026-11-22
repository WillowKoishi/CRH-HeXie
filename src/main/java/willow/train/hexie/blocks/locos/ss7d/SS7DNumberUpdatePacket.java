package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.network.c2s.HexiePacket;

public record SS7DNumberUpdatePacket(int menuId, String digits, String bureau, String depot,
                                     boolean depotVisible, boolean secondEnd) implements HexiePacket {
    public SS7DNumberUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(4), buf.readUtf(SS7DHeadEntity.MAX_UNIT_LENGTH),
                buf.readUtf(SS7DHeadEntity.MAX_UNIT_LENGTH), buf.readBoolean(), buf.readBoolean());
    }
    @Override public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(menuId);
        buf.writeUtf(digits, 4);
        buf.writeUtf(bureau, SS7DHeadEntity.MAX_UNIT_LENGTH);
        buf.writeUtf(depot, SS7DHeadEntity.MAX_UNIT_LENGTH);
        buf.writeBoolean(depotVisible);
        buf.writeBoolean(secondEnd);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)
                    || !(player.containerMenu instanceof SS7DNumberMenu menu) || menu.containerId != menuId
                    || !menu.stillValid(player) || !SS7DHeadEntity.validDigits(digits)
                    || !SS7DHeadEntity.validUnit(bureau) || !SS7DHeadEntity.validUnit(depot)) return;
            var head = menu.editTarget();
            head.updateDigits(digits);
            head.updateDepot(bureau, depot, depotVisible);
            head.updateSecondEnd(secondEnd);
            if (menu.contentOnly()) menu.commitEdit(player);
            else {
                head.setChanged();
                head.sendData();
            }
        });
        return true;
    }
}
