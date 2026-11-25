package willow.train.hexie.ui.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.ui.MenuBridge.TrainSpeedSignEditMenuView;
import willow.train.hexie.bridge.world.BlockEntityViews.TrainSpeedSignEntityView;

public class TrainSpeedSignEditMenu extends BlockEntityEditMenu<TrainSpeedSignEntityView> implements TrainSpeedSignEditMenuView {
    public TrainSpeedSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), id, inventory, data, TrainSpeedSignEntityView.class);
    }

    public TrainSpeedSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), id, entity, TrainSpeedSignEntityView.class);
    }

    public TrainSpeedSignEntityView getTsse() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, BlockEntityEditMenuTargetView target) {
        return openMoving(player, target, (id, inv) -> new TrainSpeedSignEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public void setTsse(TrainSpeedSignEntityView entity) { target = entity; }
}
