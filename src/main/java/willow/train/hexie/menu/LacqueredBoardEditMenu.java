package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.blockentity.LacqueredBoardEntity;
import willow.train.hexie.init.client.MenuInit;

public class LacqueredBoardEditMenu extends BlockEntityEditMenu<LacqueredBoardEntity> {
    public LacqueredBoardEditMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getBlockEntity(data.readBlockPos()), new SimpleContainerData(2));
    }

    public LacqueredBoardEditMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(MenuInit.LAQUERED_BOARD_EDIT_MENU.get(), id, entity, LacqueredBoardEntity.class);
    }

    public LacqueredBoardEntity getLbe() { return target; }
    public void setLbe(LacqueredBoardEntity entity) { target = entity; }
}
