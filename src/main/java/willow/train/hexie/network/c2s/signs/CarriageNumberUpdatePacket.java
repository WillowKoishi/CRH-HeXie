package willow.train.hexie.network.c2s.signs;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.api.display.PassengerCarriageNumber;
import willow.train.hexie.menu.CarriageNumberMenu;
import willow.train.hexie.network.c2s.HexiePacket;

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
            if (player.containerMenu instanceof CarriageNumberMenu menu && menu.containerId == menuId)
                menu.submit(player, number);
        });
        return true;
    }
}
