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
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), id, inventory, data, TrainSpeedSignEntity.class);
    }

    public TrainSpeedSignEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), id, entity, TrainSpeedSignEntity.class);
    }

    public TrainSpeedSignEntity getTsse() { return target; }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, Target target) {
        return openMoving(player, target, (id, inv) -> new TrainSpeedSignEditMenu(id, inv, target.entity(), new SimpleContainerData(2)), net.minecraft.network.chat.Component.empty());
    }
    public void setTsse(TrainSpeedSignEntity entity) { target = entity; }
}
