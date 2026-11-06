package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.blocks.parts.PanelText;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.init.item.LogoItemInit;

public final class PanelTextMenu extends BlockEntityEditMenu<CRH2PartEntity> {
    public final PanelText initial;
    public final net.minecraft.world.level.block.state.BlockState panelState;
    public final int slot;
    private final InteractionHand hand;

    public PanelTextMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.PANEL_TEXT_MENU.get(), id, null, CRH2PartEntity.class);
        initial = new PanelText(buf);
        panelState = net.minecraft.world.level.block.Block.stateById(buf.readVarInt());
        slot = buf.readVarInt();
        hand = InteractionHand.MAIN_HAND;
    }
    private PanelTextMenu(int id, CRH2PartEntity panel, int slot, InteractionHand hand) {
        super(MenuInit.PANEL_TEXT_MENU.get(), id, panel, CRH2PartEntity.class);
        panelState = panel.getBlockState();
        this.slot = slot;
        this.hand = hand;
        initial = panel.panelText(slot);
    }
    public static void open(ServerPlayer player, CRH2PartEntity panel, int slot, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PanelTextMenu(id, panel, slot, hand),
                Component.translatable("item.hexie.panel_text")), buf -> {
                    panel.panelText(slot).write(buf);
                    buf.writeVarInt(net.minecraft.world.level.block.Block.getId(panel.getBlockState()));
                    buf.writeVarInt(slot);
                });
    }
    @Override public boolean stillValid(Player player) {
        return super.stillValid(player) && initial.equals(target.panelText(slot))
                && (!initial.text().isEmpty() || player.getItemInHand(hand).is(LogoItemInit.PANEL_TEXT.get()));
    }
    public void submit(Player player, PanelText value) {
        if (!stillValid(player)) return;
        if (initial.text().isEmpty() && !value.text().isBlank() && !player.getAbilities().instabuild)
            player.getItemInHand(hand).shrink(1);
        target.setPanelText(slot, value.text().isBlank() ? PanelText.EMPTY : value);
    }
}
