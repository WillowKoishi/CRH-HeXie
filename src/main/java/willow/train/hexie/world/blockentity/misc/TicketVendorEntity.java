package willow.train.hexie.world.blockentity.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class TicketVendorEntity extends BlockEntity implements MenuProvider, BlockEntityViews.TicketVendorEntityView {

    public TicketVendorEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.TICKET_VENDOR.get(), pPos, pBlockState);
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return HexieBridges.menu().createTicketVendorMenu(
                pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
    }
}
