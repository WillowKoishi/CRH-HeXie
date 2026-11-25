package willow.train.hexie.sync.c2s.signs;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge.SS7DNumberMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.bridge.world.WorldBlockBridge.SS7DHeadEntityOperations;
import willow.train.hexie.sync.c2s.HexiePacket;

public record SS7DNumberUpdatePacket(int menuId, String digits, String bureau, String depot,
                                     boolean depotVisible, boolean secondEnd) implements HexiePacket {
    public SS7DNumberUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(4), buf.readUtf(SS7DHeadEntityOperations.MAX_UNIT_LENGTH),
                buf.readUtf(SS7DHeadEntityOperations.MAX_UNIT_LENGTH), buf.readBoolean(), buf.readBoolean());
    }
    @Override public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(menuId);
        buf.writeUtf(digits, 4);
        buf.writeUtf(bureau, SS7DHeadEntityOperations.MAX_UNIT_LENGTH);
        buf.writeUtf(depot, SS7DHeadEntityOperations.MAX_UNIT_LENGTH);
        buf.writeBoolean(depotVisible);
        buf.writeBoolean(secondEnd);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)
                    || !(player.containerMenu instanceof SS7DNumberMenuView menu) || menu.asMenu().containerId != menuId
                    || !menu.stillValid(player) || !HexieBridges.worldBlock().ss7DHeadEntity().validDigits(digits)
                    || !HexieBridges.worldBlock().ss7DHeadEntity().validUnit(bureau) || !HexieBridges.worldBlock().ss7DHeadEntity().validUnit(depot)) return;
            if (!(menu.editTarget() instanceof SS7DHeadEntityView head)) return;
            head.updateDigits(digits);
            head.updateDepot(bureau, depot, depotVisible);
            head.updateSecondEnd(secondEnd);
            if (menu.contentOnly()) menu.commitEdit(player);
            else SignEditSubmission.sync(head.asBlockEntity());
        });
        return true;
    }
}
