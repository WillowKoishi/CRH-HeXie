package willow.train.hexie.ui.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.ui.MenuBridge.LacqueredBoardEditMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.LacqueredBoardEntityView;

public class LacqueredBoardEditMenu extends BlockEntityEditMenu<LacqueredBoardEntityView> implements LacqueredBoardEditMenuView {
    public LacqueredBoardEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), id, inventory, data, LacqueredBoardEntityView.class);
    }

    public LacqueredBoardEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), id, entity, LacqueredBoardEntityView.class);
    }

    public LacqueredBoardEntityView getLbe() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, BlockEntityEditMenuTargetView target) {
        return openMoving(player, target, (id, inv) -> new LacqueredBoardEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public void setLbe(LacqueredBoardEntityView entity) { target = entity; }
}
