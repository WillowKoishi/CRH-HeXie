package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.blockentity.CarriageNoSignEntity;

public class CarriageNoSignEditMenu extends AbstractContainerMenu {
    CarriageNoSignEntity ctse;

    public CarriageNoSignEditMenu(@Nullable MenuType<?> pMenuType, int pContainerId) {
        super(pMenuType, pContainerId);

        this.addDataSlot(
                new DataSlot() {
                    @Override
                    public int get() {
                        return 0;
                    }

                    @Override
                    public void set(int pValue) {}
                });
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return null;
    }

    public CarriageNoSignEditMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(
                pContainerId,
                inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    public CarriageNoSignEditMenu(
            int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get(), pContainerId);
        this.ctse = ((CarriageNoSignEntity) entity);
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true;
    }

    public void setCtse(CarriageNoSignEntity ctse) {
        this.ctse = ctse;
    }

    /**
     * 这个方法将负责将方块实体向Screen传递
     *
     * @return 对应的方块实体
     */
    public CarriageNoSignEntity getCtse() {
        return ctse;
    }
}
