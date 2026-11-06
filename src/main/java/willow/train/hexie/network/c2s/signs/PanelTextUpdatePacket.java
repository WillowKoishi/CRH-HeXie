package willow.train.hexie.network.c2s.signs;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blocks.parts.PanelText;
import willow.train.hexie.menu.PanelTextMenu;
import willow.train.hexie.network.c2s.HexiePacket;

public record PanelTextUpdatePacket(int menuId, PanelText value) implements HexiePacket {
    public PanelTextUpdatePacket(FriendlyByteBuf buf) { this(buf.readVarInt(), new PanelText(buf)); }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeVarInt(menuId); value.write(buf); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof PanelTextMenu menu && menu.containerId == menuId)
                menu.submit(player, value);
        });
        return true;
    }
}
