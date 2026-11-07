package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.blockentity.CarriageNoSignEntity;
import willow.train.hexie.init.client.MenuInit;

public class CarriageNoSignEditMenu extends BlockEntityEditMenu<CarriageNoSignEntity> {
    public CarriageNoSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), id, inventory, data, CarriageNoSignEntity.class);
    }

    public CarriageNoSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), id, entity, CarriageNoSignEntity.class);
    }

    public CarriageNoSignEntity getCtse() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, Target target) {
        return openMoving(player, target, (id, inv) -> new CarriageNoSignEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public void setCtse(CarriageNoSignEntity entity) { target = entity; }
}
