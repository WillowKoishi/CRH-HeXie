package willow.train.hexietrain;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 物理火车调度器 UI。
 *
 * <p>刻意<b>不含玩家背包</b>：它是一个"控制面板"，不是储物箱。
 * 槽位只有 1 个时刻表 + 6 个频率。
 */
public class TrainDispatcherMenu extends AbstractContainerMenu {

    public static final int DATA_BRAKE = 0;
    public static final int DATA_LEFT = 1;
    public static final int DATA_RIGHT = 2;
    public static final int DATA_BOUND = 4;
    public static final int DATA_SUSPENDED = 5;
    public static final int DATA_STAGE = 6;
    public static final int DATA_DISTANCE = 7;
    public static final int DATA_STOP_COUNT = 9;
    public static final int DATA_MANUAL_MODE = 11;
    public static final int DATA_CURRENT_ENTRY = 12;
    public static final int DATA_SPEED = 13;
    public static final int DATA_DISPATCHER_STATE = 14;
    public static final int DATA_SENSOR_SPEED = 15;   // 速度传感器读数（×100 m/s）
    public static final int DATA_COUNT = 16;

    private final Container container;
    private final TrainDispatcherBlockEntity blockEntity;
    private final int[] cached = new int[DATA_COUNT];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index >= 0 && index < DATA_COUNT ? cached[index] : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < DATA_COUNT) cached[index] = value;
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /** 客户端构造：从同步包里的坐标找回客户端方块实体。 */
    public TrainDispatcherMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, clientEntity(playerInventory, buf.readBlockPos()));
    }

    private static TrainDispatcherBlockEntity clientEntity(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be ? be : null;
    }

    /** 服务端构造。 */
    public TrainDispatcherMenu(int containerId, Inventory playerInventory, TrainDispatcherBlockEntity be) {
        this(containerId, playerInventory, be,
                be != null ? new TrainDispatcherContainer(be) : new SimpleContainer(TrainDispatcherContainer.SIZE));
    }

    private TrainDispatcherMenu(int containerId, Inventory playerInventory,
                                TrainDispatcherBlockEntity be, Container container) {
        super(HexieRegistries.TRAIN_DISPATCHER_MENU.get(), containerId);
        this.blockEntity = be;
        this.container = container;

        addDataSlots(data);

        // 打开界面时初始化一次：刷新站名并把选中项落到当前条目（之后不再重置）
        if (be != null) {
            be.refreshStopNames();
        }

        // 时刻表槽
        addSlot(new Slot(container, TrainDispatcherContainer.SLOT_SCHEDULE, 14, 22) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // 三通道 × 两频率槽
        int[] channelY = {54, 74, 94};
        for (int ch = 0; ch < 3; ch++) {
            for (int f = 0; f < 2; f++) {
                int slotIndex = TrainDispatcherContainer.SLOT_FREQ_BASE + ch * 2 + f;
                addSlot(new Slot(container, slotIndex, 40 + f * 20, channelY[ch]) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }

        // 玩家背包（3 行 + 快捷栏）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 146 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 204));
        }
        refresh();
    }

    /** 从方块实体刷新缓存。 */
    public void refresh() {
        if (blockEntity == null) return;
        cached[DATA_BRAKE] = blockEntity.output(SignalChannel.BRAKE);
        cached[DATA_LEFT] = blockEntity.output(SignalChannel.LEFT);
        cached[DATA_RIGHT] = blockEntity.output(SignalChannel.RIGHT);
        cached[DATA_BOUND] = blockEntity.boundTrain() != null ? 1 : 0;
        cached[DATA_SUSPENDED] = blockEntity.suspended() ? 1 : 0;
        cached[DATA_STAGE] = blockEntity.stageOrdinal();
        cached[DATA_DISTANCE] = (int) Math.min(Integer.MAX_VALUE, blockEntity.distanceToTarget());
        blockEntity.refreshStopNames();
        cached[DATA_STOP_COUNT] = blockEntity.stopNames().size();
        HexieTrain.LOGGER.info("[HexieTrain][ui] 打开界面：站点数={} 当前条目={}",
                blockEntity.stopNames().size(), blockEntity.currentEntry());
        cached[DATA_MANUAL_MODE] = blockEntity.manualMode() ? 1 : 0;
        cached[DATA_CURRENT_ENTRY] = blockEntity.currentEntry();
        cached[DATA_SPEED] = blockEntity.speedMillis();
        cached[DATA_DISPATCHER_STATE] = blockEntity.dispatcherState().ordinal();
        cached[DATA_SENSOR_SPEED] = blockEntity.measuredSpeedCenti();
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) return false;
        switch (id) {
            // 零启动/非零启动改由时刻表按站设置，不再走 UI
            case 3 -> blockEntity.setManualMode(!blockEntity.manualMode());
            // 确认按钮走 id>=200 分支（带索引）
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }

    public TrainDispatcherBlockEntity blockEntity() { return blockEntity; }
    public int brakeOutput() { return data.get(DATA_BRAKE); }
    public int leftOutput()  { return data.get(DATA_LEFT); }
    public int rightOutput() { return data.get(DATA_RIGHT); }
    public boolean bound()     { return data.get(DATA_BOUND) != 0; }
    public boolean suspended() { return data.get(DATA_SUSPENDED) != 0; }
    public int stage()         { return data.get(DATA_STAGE); }
    public int distance()      { return data.get(DATA_DISTANCE); }
    public int stopCount()      { return data.get(DATA_STOP_COUNT); }
    public boolean manualMode() { return data.get(DATA_MANUAL_MODE) != 0; }
    public int currentEntry() { return data.get(DATA_CURRENT_ENTRY); }
    public int speedMillis() { return data.get(DATA_SPEED); }
    public int dispatcherState() { return data.get(DATA_DISPATCHER_STATE); }
    public int sensorSpeedCenti() { return data.get(DATA_SENSOR_SPEED); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int blockSlots = TrainDispatcherContainer.SIZE;
        int inventoryEnd = blockSlots + 36;
        if (index < blockSlots) {
            if (!moveItemStackTo(stack, blockSlots, inventoryEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, blockSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}