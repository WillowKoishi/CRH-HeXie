package willow.train.hexie.ui.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.ui.MenuBridge.PanelTextMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2PartEntityView;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelTextView;

public final class PanelTextMenu extends BlockEntityEditMenu<CRH2PartEntityView> implements PanelTextMenuView {
    public final PanelTextView initial;
    public final net.minecraft.world.level.block.state.BlockState panelState;
    public final int slot;
    private final InteractionHand hand;

    public PanelTextMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.PANEL_TEXT_MENU.get(), id, null, CRH2PartEntityView.class);
        initial = HexieBridges.partBlock().panelText().create(buf);
        panelState = net.minecraft.world.level.block.Block.stateById(buf.readVarInt());
        slot = buf.readVarInt();
        contentOnly = buf.isReadable() && buf.readBoolean();
        hand = InteractionHand.MAIN_HAND;
    }
    public static boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving, int slot) {
        if (!(moving.entity() instanceof CRH2PartEntityView panel) || slot < 0 || slot > 1
                || panel.panelText(slot).text().isBlank() || !moving.stillValid(player)) return false;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
            var menu = new PanelTextMenu(id, panel, slot, InteractionHand.MAIN_HAND);
            menu.attachTarget(moving);
            return menu;
        }, Component.translatable("item.hexie.panel_text")), buf -> {
            panel.panelText(slot).write(buf);
            buf.writeVarInt(net.minecraft.world.level.block.Block.getId(panel.asBlockEntity().getBlockState()));
            buf.writeVarInt(slot);
            buf.writeBoolean(true);
        });
        return true;
    }
    private PanelTextMenu(int id, CRH2PartEntityView panel, int slot, InteractionHand hand) {
        super(MenuInit.PANEL_TEXT_MENU.get(), id, panel.asBlockEntity(), CRH2PartEntityView.class);
        panelState = panel.asBlockEntity().getBlockState();
        this.slot = slot;
        this.hand = hand;
        initial = panel.panelText(slot);
    }
    public static void open(ServerPlayer player, CRH2PartEntityView panel, int slot, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PanelTextMenu(id, panel, slot, hand),
                Component.translatable("item.hexie.panel_text")), buf -> {
                    panel.panelText(slot).write(buf);
                    buf.writeVarInt(net.minecraft.world.level.block.Block.getId(panel.asBlockEntity().getBlockState()));
                    buf.writeVarInt(slot);
                });
    }
    @Override public boolean stillValid(Player player) {
        return super.stillValid(player) && initial.equals(target.panelText(slot))
                && (!initial.text().isEmpty() || player.getItemInHand(hand).is(HexieBridges.worldRegistry().logoItemInit().panelText()));
    }
    public void submit(Player player, PanelTextView value) {
        if (!stillValid(player)) return;
        if (contentOnly()) {
            if (value.text().isBlank()) return;
            target.setPanelText(slot, HexieBridges.partBlock().panelText().create(value.text(), value.size(), initial.x(), initial.y(), value.font(), value.spacing()));
            commitEdit(player);
            return;
        }
        if (initial.text().isEmpty() && !value.text().isBlank() && !player.getAbilities().instabuild)
            player.getItemInHand(hand).shrink(1);
        target.setPanelText(slot, value.text().isBlank() ? HexieBridges.partBlock().panelText().empty() : value);
    }
}
