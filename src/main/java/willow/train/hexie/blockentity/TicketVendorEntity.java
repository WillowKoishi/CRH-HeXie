package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.menu.TicketVendorMenu;
import willow.train.hexie.init.BlockEntitiesInit;

public class TicketVendorEntity extends BlockEntity implements MenuProvider {
    public TicketVendorEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

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
        return new TicketVendorMenu(
                pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
    }
}
