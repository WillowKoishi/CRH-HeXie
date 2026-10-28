package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.AnimatableBlockControllerItem;

public class AnimatableBlockControllerMenu extends AbstractContainerMenu {

    private final Inventory inventory;
    private final int controllerSlot;

    public AnimatableBlockControllerMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, extraData != null && extraData.isReadable() ? extraData.readVarInt() : inv.selected);
    }

    public AnimatableBlockControllerMenu(int pContainerId, Inventory inv, int controllerSlot) {
        super(MenuInit.ANIMATABLE_BLOCK_CONTROLLER_MENU.get(), pContainerId);
        this.inventory = inv;
        this.controllerSlot = controllerSlot >= 0 && controllerSlot < inv.getContainerSize()
                ? controllerSlot : inv.selected;
    }

    public int getControllerSlot() {
        return controllerSlot;
    }

    public ItemStack getControllerStack() {
        return inventory.getItem(controllerSlot);
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return getControllerStack().getItem() instanceof AnimatableBlockControllerItem;
    }
}
