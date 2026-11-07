package willow.train.hexie.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A world editor retains the exact target opened by the server. */
public abstract class BlockEntityEditMenu<T extends BlockEntity> extends AbstractContainerMenu {
    public interface Target {
        BlockEntity entity();
        boolean stillValid(Player player);
        boolean commit(Player player);
    }
    protected T target;
    private Target movingTarget;
    protected boolean contentOnly;

    protected BlockEntityEditMenu(MenuType<?> type, int id, BlockEntity target, Class<T> targetType) {
        super(type, id);
        this.target = targetType.cast(target);
    }

    protected BlockEntityEditMenu(MenuType<?> type, int id, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.network.FriendlyByteBuf buf, Class<T> targetType) {
        super(type, id);
        var pos = buf.readBlockPos();
        contentOnly = buf.isReadable() && buf.readBoolean();
        if (contentOnly) {
            var state = net.minecraft.world.level.block.Block.stateById(buf.readVarInt());
            var tag = buf.readNbt();
            target = targetType.cast(BlockEntity.loadStatic(pos, state, java.util.Objects.requireNonNull(tag), inventory.player.registryAccess()));
        } else target = targetType.cast(inventory.player.level().getBlockEntity(pos));
    }

    public final T editTarget() { return target; }
    public final boolean contentOnly() { return contentOnly; }
    public final void attachTarget(Target moving) {
        if (moving.entity() != target) throw new IllegalArgumentException("Editor target must be its captured entity");
        movingTarget = moving;
        contentOnly = true;
    }
    public final boolean commitEdit(Player player) { return movingTarget != null && movingTarget.commit(player); }

    protected static boolean openMoving(net.minecraft.server.level.ServerPlayer player, Target target,
            java.util.function.BiFunction<Integer, net.minecraft.world.entity.player.Inventory, ? extends BlockEntityEditMenu<?>> factory,
            net.minecraft.network.chat.Component title) {
        if (!target.stillValid(player)) return false;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> {
            var menu = factory.apply(id, inventory);
            menu.attachTarget(target);
            return menu;
        }, title), buf -> writeMovingSnapshot(buf, target.entity(), player.registryAccess()));
        return true;
    }

    public static void writeMovingSnapshot(net.minecraft.network.FriendlyByteBuf buf, BlockEntity entity,
            net.minecraft.core.HolderLookup.Provider registries) {
        buf.writeBlockPos(entity.getBlockPos());
        buf.writeBoolean(true);
        buf.writeVarInt(net.minecraft.world.level.block.Block.getId(entity.getBlockState()));
        buf.writeNbt(entity.saveWithFullMetadata(registries));
    }

    @Override public boolean stillValid(Player player) {
        if (contentOnly) return movingTarget != null && movingTarget.stillValid(player);
        return target != null && !target.isRemoved() && player.mayBuild() && !player.isSpectator()
                && target.getLevel() == player.level() && player.level().hasChunkAt(target.getBlockPos())
                && player.level().getBlockEntity(target.getBlockPos()) == target
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockPos())) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
