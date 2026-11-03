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
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()), new SimpleContainerData(2));
    }

    public CarriageNoSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), id, entity, CarriageNoSignEntity.class);
    }

    public CarriageNoSignEntity getCtse() { return target; }
    public void setCtse(CarriageNoSignEntity entity) { target = entity; }
}
