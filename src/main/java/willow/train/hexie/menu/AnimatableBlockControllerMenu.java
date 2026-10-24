package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.init.client.MenuInit;

public class AnimatableBlockControllerMenu extends AbstractContainerMenu {

    ItemStack stack;
    Inventory inventory;

    protected AnimatableBlockControllerMenu(@Nullable MenuType<?> pMenuType, int pContainerId) {
        super(pMenuType, pContainerId);
    }

    public AnimatableBlockControllerMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(MenuInit.ANIMATABLE_BLOCK_CONTROLLER_MENU.get(), pContainerId);
        this.inventory = inv;
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return null;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true;
    }
}
