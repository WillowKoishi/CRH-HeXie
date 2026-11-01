package willow.train.hexietrain;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 把方块实体的 7 个槽（1 时刻表 + 6 频率）包装成标准 {@link Container}。
 *
 * <p>槽位编号：0 = 时刻表；1~6 = 三个通道各自的"频率1/频率2"。
 */
public class TrainDispatcherContainer implements Container {

    public static final int SLOT_SCHEDULE = 0;
    public static final int SLOT_FREQ_BASE = 1;
    public static final int SIZE = 1 + TrainDispatcherBlockEntity.FREQ_SLOTS;

    private final TrainDispatcherBlockEntity be;

    public TrainDispatcherContainer(TrainDispatcherBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        if (!be.scheduleSlot().isEmpty()) return false;
        for (int i = 0; i < TrainDispatcherBlockEntity.FREQ_SLOTS; i++) {
            if (!be.frequency(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == SLOT_SCHEDULE) return be.scheduleSlot();
        return be.frequency(slot - SLOT_FREQ_BASE);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        setItem(slot, current);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == SLOT_SCHEDULE) {
            be.setScheduleSlot(stack);
        } else {
            be.setFrequency(slot - SLOT_FREQ_BASE, stack);
        }
    }

    @Override
    public void setChanged() {
        be.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return be.hasLevel() && player.distanceToSqr(
                be.getBlockPos().getX() + 0.5,
                be.getBlockPos().getY() + 0.5,
                be.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        be.setScheduleSlot(ItemStack.EMPTY);
        for (int i = 0; i < TrainDispatcherBlockEntity.FREQ_SLOTS; i++) {
            be.setFrequency(i, ItemStack.EMPTY);
        }
    }
}