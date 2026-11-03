package willow.train.hexie.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A world editor retains the exact target opened by the server. */
public abstract class BlockEntityEditMenu<T extends BlockEntity> extends AbstractContainerMenu {
    protected T target;

    protected BlockEntityEditMenu(MenuType<?> type, int id, BlockEntity target, Class<T> targetType) {
        super(type, id);
        this.target = targetType.cast(target);
    }

    public final T editTarget() { return target; }

    @Override public boolean stillValid(Player player) {
        return target != null && !target.isRemoved() && player.mayBuild() && !player.isSpectator()
                && target.getLevel() == player.level() && player.level().hasChunkAt(target.getBlockPos())
                && player.level().getBlockEntity(target.getBlockPos()) == target
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockPos())) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
