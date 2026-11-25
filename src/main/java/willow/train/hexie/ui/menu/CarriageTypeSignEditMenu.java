package willow.train.hexie.ui.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.ui.MenuBridge.CarriageTypeSignEditMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageTypeSignEntityView;

public class CarriageTypeSignEditMenu extends BlockEntityEditMenu<CarriageTypeSignEntityView> implements CarriageTypeSignEditMenuView {
    public CarriageTypeSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), id, inventory, data, CarriageTypeSignEntityView.class);
    }

    public CarriageTypeSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), id, entity, CarriageTypeSignEntityView.class);
    }

    public CarriageTypeSignEntityView getCtse() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, BlockEntityEditMenuTargetView target) {
        return openMoving(player, target, (id, inv) -> new CarriageTypeSignEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public CarriageTypeSignEditMenu setCtse(CarriageTypeSignEntityView entity) { target = entity; return this; }
}
