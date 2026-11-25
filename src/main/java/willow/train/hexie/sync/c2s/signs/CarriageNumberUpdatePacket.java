package willow.train.hexie.sync.c2s.signs;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.ui.MenuBridge.CarriageNumberMenuView;
import willow.train.hexie.core.api.display.PassengerCarriageNumber;
import willow.train.hexie.sync.c2s.HexiePacket;

public record CarriageNumberUpdatePacket(int menuId, String number) implements HexiePacket {
    public CarriageNumberUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(PassengerCarriageNumber.MAX_DIGITS));
    }
    @Override public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(menuId);
        buf.writeUtf(number, PassengerCarriageNumber.MAX_DIGITS);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof CarriageNumberMenuView menu && menu.asMenu().containerId == menuId)
                menu.submit(player, number);
        });
        return true;
    }
}
