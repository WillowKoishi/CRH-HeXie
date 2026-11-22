package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;
import willow.train.hexie.menu.BlockEntityEditMenu;

public final class SS7DNumberMenu extends BlockEntityEditMenu<SS7DHeadEntity> {
    private static final Component TITLE = Component.translatable("gui.hexie.ss7d_head_number");
    public final String initialDigits;
    public final String initialBureau;
    public final String initialDepot;
    public final boolean initialDepotVisible;
    public final boolean initialSecondEnd;

    public SS7DNumberMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.SS7D_NUMBER_MENU.get(), id, null, SS7DHeadEntity.class);
        initialDigits = buf.readUtf(4);
        initialBureau = buf.readUtf(SS7DHeadEntity.MAX_UNIT_LENGTH);
        initialDepot = buf.readUtf(SS7DHeadEntity.MAX_UNIT_LENGTH);
        initialDepotVisible = buf.readBoolean();
        initialSecondEnd = buf.readBoolean();
    }
    private SS7DNumberMenu(int id, SS7DHeadEntity head) {
        super(MenuInit.SS7D_NUMBER_MENU.get(), id, head, SS7DHeadEntity.class);
        initialDigits = head.digits();
        initialBureau = head.bureau();
        initialDepot = head.depot();
        initialDepotVisible = head.depotVisible();
        initialSecondEnd = head.secondEnd();
    }
    public static void openWorld(ServerPlayer player, SS7DHeadEntity head) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SS7DNumberMenu(id, head), TITLE),
                buf -> writeInitial(buf, head));
    }
    public static boolean openMoving(ServerPlayer player, Target target) {
        if (!(target.entity() instanceof SS7DHeadEntity head) || !target.stillValid(player)) return false;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
            var menu = new SS7DNumberMenu(id, head);
            menu.attachTarget(target);
            return menu;
        }, TITLE), buf -> writeInitial(buf, head));
        return true;
    }
    private static void writeInitial(FriendlyByteBuf buf, SS7DHeadEntity head) {
        buf.writeUtf(head.digits(), 4);
        buf.writeUtf(head.bureau(), SS7DHeadEntity.MAX_UNIT_LENGTH);
        buf.writeUtf(head.depot(), SS7DHeadEntity.MAX_UNIT_LENGTH);
        buf.writeBoolean(head.depotVisible());
        buf.writeBoolean(head.secondEnd());
    }
    @Override public boolean stillValid(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof ColoredBrush)
                && !(player.getOffhandItem().getItem() instanceof ColoredBrush)) return false;
        return super.stillValid(player);
    }
}
