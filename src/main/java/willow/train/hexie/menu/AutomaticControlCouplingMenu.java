package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.init.client.MenuInit;

public class AutomaticControlCouplingMenu extends AbstractContainerMenu {

    public ItemStack stack;
    Inventory inventory;

    protected AutomaticControlCouplingMenu(@Nullable MenuType<?> pMenuType, int pContainerId) {
        super(pMenuType, pContainerId);
    }

    public AutomaticControlCouplingMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(MenuInit.AUTOMATIC_CONTROL_COUPLING_MENU.get(), pContainerId);
        this.stack = ItemStack.STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf) extraData);
        this.inventory = inv;
    }

    public AutomaticControlCouplingMenu(int pContainerId, Inventory inv) {
        this(MenuInit.AUTOMATIC_CONTROL_COUPLING_MENU.get(), pContainerId);
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
