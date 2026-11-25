package willow.train.hexie.ui.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.ui.MenuBridge.CarriageNoSignEditMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageNoSignEntityView;

public class CarriageNoSignEditMenu extends BlockEntityEditMenu<CarriageNoSignEntityView> implements CarriageNoSignEditMenuView {
    public CarriageNoSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), id, inventory, data, CarriageNoSignEntityView.class);
    }

    public CarriageNoSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), id, entity, CarriageNoSignEntityView.class);
    }

    public CarriageNoSignEntityView getCtse() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, BlockEntityEditMenuTargetView target) {
        return openMoving(player, target, (id, inv) -> new CarriageNoSignEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public void setCtse(CarriageNoSignEntityView entity) { target = entity; }
}
