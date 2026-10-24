package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;

public class TrainSpeedSignEditMenu extends AbstractContainerMenu {
    TrainSpeedSignEntity tsse;

    public TrainSpeedSignEditMenu(@Nullable MenuType<?> pMenuType, int pContainerId) {
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

    public TrainSpeedSignEditMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(
                pContainerId,
                inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    public TrainSpeedSignEditMenu(
            int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get(), pContainerId);
        this.tsse = ((TrainSpeedSignEntity) entity);
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true;
    }

    public void setTsse(TrainSpeedSignEntity tsse) {
        this.tsse = tsse;
    }

    /**
     * 这个方法将负责将方块实体向Screen传递
     *
     * @return 对应的方块实体
     */
    public TrainSpeedSignEntity getTsse() {
        return tsse;
    }
}
