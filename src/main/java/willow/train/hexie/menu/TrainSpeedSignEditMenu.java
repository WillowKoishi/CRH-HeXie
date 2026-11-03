package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;
import willow.train.hexie.init.client.MenuInit;

public class TrainSpeedSignEditMenu extends BlockEntityEditMenu<TrainSpeedSignEntity> {
    public TrainSpeedSignEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()), new SimpleContainerData(2));
    }

    public TrainSpeedSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), id, entity, TrainSpeedSignEntity.class);
    }

    public TrainSpeedSignEntity getTsse() { return target; }
    public void setTsse(TrainSpeedSignEntity entity) { target = entity; }
}
