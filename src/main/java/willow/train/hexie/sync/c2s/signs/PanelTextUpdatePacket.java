package willow.train.hexie.sync.c2s.signs;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge.PanelTextMenuView;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelTextView;
import willow.train.hexie.sync.c2s.HexiePacket;

public record PanelTextUpdatePacket(int menuId, PanelTextView value) implements HexiePacket {
    public PanelTextUpdatePacket(FriendlyByteBuf buf) { this(buf.readVarInt(), HexieBridges.partBlock().panelText().create(buf)); }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeVarInt(menuId); value.write(buf); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof PanelTextMenuView menu && menu.asMenu().containerId == menuId)
                menu.submit(player, value);
        });
        return true;
    }
}
