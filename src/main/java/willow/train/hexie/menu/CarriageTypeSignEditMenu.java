package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;


import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;

public class CarriageTypeSignEditMenu extends AbstractContainerMenu {

    CarriageTypeSignEntity ctse;

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return null;
    }

    public CarriageTypeSignEditMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(
                pContainerId,
                inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    public CarriageTypeSignEditMenu(
            int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get(), pContainerId);
        this.ctse = ((CarriageTypeSignEntity) entity);
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true;
    }

    public CarriageTypeSignEditMenu setCtse(CarriageTypeSignEntity ctse) {
        this.ctse = ctse;
        return this;
    }

    /**
     * 这个方法将负责将方块实体向Screen传递
     *
     * @return 对应的方块实体
     */
    public CarriageTypeSignEntity getCtse() {
        return ctse;
    }
}
