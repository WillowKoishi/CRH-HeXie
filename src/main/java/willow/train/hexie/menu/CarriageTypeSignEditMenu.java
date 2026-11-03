package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;
import willow.train.hexie.init.client.MenuInit;

public class CarriageTypeSignEditMenu extends BlockEntityEditMenu<CarriageTypeSignEntity> {
    public CarriageTypeSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()), new SimpleContainerData(2));
    }

    public CarriageTypeSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), id, entity, CarriageTypeSignEntity.class);
    }

    public CarriageTypeSignEntity getCtse() { return target; }
    public CarriageTypeSignEditMenu setCtse(CarriageTypeSignEntity entity) { target = entity; return this; }
}
