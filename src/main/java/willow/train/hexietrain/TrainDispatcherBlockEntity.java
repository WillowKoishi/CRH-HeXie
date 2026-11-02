package willow.train.hexietrain;

import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列车调度台 —— 方块实体。
 *
 * <p>两种工作模式：
 * <ul>
 *   <li><b>手动模式</b>（未绑定列车）：左/右红石 → 正/反向转速，0~15 线性映射。</li>
 *   <li><b>自动模式</b>（绑定了列车）：读列车状态 → 制动曲线 → 三个通道输出，
 *       同时用反向传动逼停列车。</li>
 * </ul>
 *
 * <p>三个输出通道（每条都是一对 Create 无线红石频率）：
 * <ol>
 *   <li>刹车 —— 0~15，距离目标越近越强（已做平方根补偿）</li>
 *   <li>左转 —— 0~15</li>
 *   <li>右转 —— 0~15</li>
 * </ol>
 */
public class TrainDispatcherBlockEntity extends SplitShaftBlockEntity implements MenuProvider, net.minecraft.world.Container {

    /** 输出转速上限（RPM）。 */
    public static final float MAX_RPM = 256f;

    /** 应力容量（SU）。 */
    public static final float STRESS_CAPACITY = 512f;

    /** 每个通道两个频率槽（频率1 / 频率2），共 6 个。 */
    public static final int FREQ_SLOTS = SignalChannel.VALUES.length * 2;

    private final ItemStack[] frequencies = new ItemStack[FREQ_SLOTS];

    /** 三个无线发送通道，一一对应 SignalChannel。 */
    private final WirelessChannel[] wireless = new WirelessChannel[SignalChannel.VALUES.length];

    /** 各通道当前输出强度 0~15，给 UI 显示和无线发送用。 */
    private final int[] outputs = new int[SignalChannel.VALUES.length];




    /** 时刻表里的站点名列表（顺序 = 条目顺序）。 */
    private final java.util.List<String> stopNames = new java.util.ArrayList<>();

    /** 当前在长栏里选中的站点下标。 */

    /** 加减速灵敏度（格/秒²），直接作为制动曲线的最大减速度。 */

    /** 下一个目标（车站名或信号）名字，给 UI 的长栏用。 */
    private String nextTargetName = "";

    /**
     * 是否允许输出动力。
     *
     * <p>规则：必须已绑定时刻表，并且左右任一面有红石输入。
     * 其余情况一律不输出（也不会把传动杆连进来）。
     */
    public boolean isDriving() {
        // 有红石就接传动轴：手动模式（无时刻表）也要输出转速
        return leftSignal() > 0 || rightSignal() > 0;
    }

    /** 最近一次读到的到目标距离，给 UI 用。 */
    private double lastDistance = 0;

    /** 诊断日志计时。 */

    /** 简化自动驾驶的巡航速度（格/tick）。 */
    private static final double AUTO_SPEED = 0.15;

    /** 制动区内的最大接近速度（格/tick）。 */
    private static final double MAX_APPROACH_SPEED = 0.35;

    /** 进站：5 格内限速 3 m/s。 */
    private static final double SLOW_ZONE_BLOCKS = 5.0;
    private static final double SLOW_ZONE_SPEED_MPS = 3.0;

    /** 蠕行速度：保证车头点能跨过站点，否则永远触发不了到站。 */
    private static final double CREEP_SPEED_MPS = 0.3;

    /** 期望制动减速度（m/s²），用于估算"还要滑多远"。先给常量，实测后调。 */
    private static final double EXPECTED_BRAKE_DECEL = 1.0;

    /** 距车站多少格内允许用反向传动提供反向推力。 */
    private static final double REVERSE_ZONE_BLOCKS = 20.0;
    /** 判定"刹不住"的安全余量（格）。 */
    private static final double STOP_MARGIN_BLOCKS = 2.0;

    /** 本次行程是否还想到站：到站后置 false，离站足够远再置回 true。 */
    private int arrivedEntry = -1;

    /** 到站后等待时刻表发号施令：记下停站时的条目号。 */
    private int stoppedEntry = -1;
    private boolean waitingForSchedule = false;
    private int targetLogTicks = 0;
    private int lastEntrySeen = -1;

    /** 速度传感器：上一 tick 测得的世界速度矢量（m/s，含旋转贡献）。 */
    private final org.joml.Vector3d measuredVelocity = new org.joml.Vector3d();

    /** 速度传感器：速度大小（m/s），低于 0.05 归零（与航空学一致）。 */
    private double measuredSpeedMps = 0;

    /** 自动模式下传动要输出的方向：+1 正传、-1 反传、0 断开。 */
    private float autoDirection = 0f;

    /** 输出传动比 0~1：视距内按加速度渐变到 1，视距外直接用二进制。 */
    private float outputRatio = 0f;

    /** 调度器自身的工作状态。 */
    private DispatcherState dispatcherState = DispatcherState.IDLE;
    private int lastRatioQuantum = -1;

    /** 沿轨道到车站的剩余距离缓存。 */
    private double routeDistance = -1;
    private int routeTicks = 0;
    private int brakeLogTicks = 0;
    private int derailLogTicks = 0;

    /** 所有已加载的调度台（由全局 ticker 驱动，不依赖方块实体自身 tick）。 */
    private static final Set<TrainDispatcherBlockEntity> ACTIVE = ConcurrentHashMap.newKeySet();

    public static Set<TrainDispatcherBlockEntity> activeDispatchers() {
        return ACTIVE;
    }

    /** tick 存活探针计时。 */

    /** 上一 tick 的车头世界坐标，用于"是否跨过车站"的精确判定。 */
    private net.minecraft.world.phys.Vec3 lastLeadPos = null;

    /** 时刻表卡在 PRE_TRANSIT 的计时。 */
    private int stallTicks = 0;

    /** true = 玩家操控中（暂停自动寻路）；false = 自动驾驶中。 */
    private boolean manualMode = false;

    /** 手动直驱的最大速度（格/tick）。1.0 = 20 格/秒。 */
    private static final double MANUAL_MAX_SPEED = 0.25;   // 格/tick → 5 格/秒

    /** 时刻表槽。非空且绑定成功 = 自动模式。 */
    private ItemStack scheduleSlot = ItemStack.EMPTY;

    /** 最近一次绑定结果，给 UI 显示。 */
    private String lastBindMessage = "";

    /** 绑定的列车；null = 手动模式。 */
    private UUID boundTrain;

    /** 放置这个方块的玩家 —— 列车拥有者。 */
    private UUID owner;

    /** 上一次的目的地对象，用来判断"目的地是否刚变"。 */
    private Object lastDestination;


    /** true = 对刹车通道做平方根补偿（抵消转向架的 Mth.square）。 */
    private boolean linearizeBrake = true;


    public TrainDispatcherBlockEntity(BlockPos pos, BlockState state) {
        this(HexieRegistries.TRAIN_DISPATCHER_BE.get(), pos, state);
    }

    public TrainDispatcherBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < FREQ_SLOTS; i++) {
            frequencies[i] = ItemStack.EMPTY;
        }
        for (SignalChannel ch : SignalChannel.VALUES) {
            wireless[ch.index()] = new WirelessChannel(this, ch);
        }
    }

    // ══════════════════════ 红石 → 转速（手动模式） ══════════════════════

    public int leftSignal() {
        return sideSignal(true);
    }

    public int rightSignal() {
        return sideSignal(false);
    }

    /**
     * 读取某一侧的红石强度。
     *
     * <p>两种来源都算：
     * <ol>
     *   <li>邻方块直接朝我们发射信号（getSignal）</li>
     *   <li>邻方块自己被充能（例如被拉杆从背面供电）——取它周围最强信号</li>
     * </ol>
     */
    private int sideSignal(boolean left) {
        if (level == null) return 0;
        // 垂直朝向也不能崩：统一走 Block 的安全方向表
        Direction facing = getBlockState().getValue(TrainDispatcherBlock.FACING);
        Direction dir = left ? getBlockState().getValue(TrainDispatcherBlock.FACING)
                            : getBlockState().getValue(TrainDispatcherBlock.FACING).getOpposite();
        int direct = level.getSignal(worldPosition, dir);
        int indirect = level.getBestNeighborSignal(worldPosition.relative(dir));
        return Math.max(direct, indirect);
    }

    private Direction horizontalFacing() {
        Direction facing = getBlockState().getValue(TrainDispatcherBlock.FACING);
        return facing.getAxis().isHorizontal() ? facing : null;
    }

    /** 传动比（照抄参考实现）。 */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource()) {
            if (face == getSourceFacing()) return 1.0f;

            boolean left = getBlockState().getValue(TrainDispatcherBlock.LEFT_POWERED);
            boolean right = getBlockState().getValue(TrainDispatcherBlock.RIGHT_POWERED);
            if (left && right) return 0.0f;
            if (left) return 1.0f;
            if (right) return -1.0f;
        }
        return 0.0f;
    }

    // ══════════════════════ tick ══════════════════════

    @Override
    public void tick() {
        super.tick();
        // 逻辑不在这里跑：由 DispatcherTicker 全局驱动（方块不能当大脑）
    }

    /** 由全局 ticker 每 tick 调用。 */
    public void serverTick() {
        updateMeasuredSpeed();



        // 把红石状态写进 blockstate —— 只有 blockstate 变化，
        // Create 的动力学网络才会重新计算传动比。
        // 红石状态由 Block.neighborChanged 写入 blockstate（照抄参考实现）

        // 存活探针：证明方块实体确实在 tick（每 5 秒一次）

        if (boundTrain != null) {
            tickAutomated();
        } else {
            tickManual();
        }
    }

    /**
     * 玩家操控中：本方块就是一个定向齿轮换向器，仅此而已。
     *
     * <p>不做任何额外的事 —— 不推物理体、不设速度、不输出通道。
     * 传动比由 getRotationSpeedModifier 按左右红石直接决定，
     * 转速则由上游传动轴提供。
     */
    private void tickManual() {
        boolean manual = leftSignal() > 0 || rightSignal() > 0;
        if (manual != manualMode) {
            manualMode = manual;
            setChanged();
            sendData();
        }
    }

    /** 自动：读列车状态 → 制动曲线 → 三通道输出 + 反向传动。 */
    private void tickAutomated() {
        Train train = Create.RAILWAYS.trains.get(boundTrain);
        if (train == null) {
            HexieTrain.LOGGER.warn("[HexieTrain][run] RAILWAYS 里找不到列车 {}，解除绑定", boundTrain);
            boundTrain = null;
            return;
        }

        // 目的地是否刚变（用对象身份比较，避免依赖字段名）
        Object destination = train.navigation == null ? null : train.navigation.destination;
        boolean destinationNew = destination != lastDestination;
        lastDestination = destination;

        TrainStateReader.State st = TrainStateReader.read(train);
        lastDistance = st.hasTarget() ? st.distance() : 0;
        String targetName = "";
        if (train.navigation != null && train.navigation.destination != null) {
            try {
                targetName = String.valueOf(train.navigation.destination.name);
            } catch (Throwable ignored) {
                targetName = "";
            }
        } else if (st.targetIsSignal()) {
            targetName = "前方信号";
        }
        if (!targetName.equals(nextTargetName)) {
            nextTargetName = targetName;
            sendData();
        }

        // ① 刹车通道（做了平方根补偿）
        // ① 刹车通道：由 driveToStation 计算并写入，这里不再覆盖
        // （旧 BrakeController 已删除）
        setOutput(SignalChannel.LEFT, 0);
        setOutput(SignalChannel.RIGHT, 0);

        // ③ 反向传动逼停：反推强度 → 负转速；但没红石就不输出
        int left = leftSignal();
        int right = rightSignal();
        boolean redstonePresent = left > 0 || right > 0;

        // 红石一介入 → 自动切到"玩家操控"，并暂停自动寻路
        if (redstonePresent && !manualMode) {
            setManualMode(true);
        }

        // ③b 手动接管：左右红石直接给影子车速度（位置由 TravellingPoint 推进，
        if (redstonePresent) {
            double dir = left > 0 ? 1.0 : -1.0;
            double mag = Math.max(left, right) / 15.0;
            // 用 Create 自己的手动驾驶通道，别硬写 speed（会被 tickPassiveSlowdown 吃掉）
            train.manualTick = true;
            train.targetSpeed = dir * mag * MANUAL_MAX_SPEED;
            // 关键：approachTargetSpeed 平时由车厢实体调用，我们没有实体，必须自己调
            train.approachTargetSpeed(1.0f);
        } else if (train.manualTick) {
            train.manualTick = false;
            train.targetSpeed = 0;
            train.approachTargetSpeed(1.0f);
        }

        // 玩家操控模式：只按红石直驱，不跑自动制动曲线
        if (manualMode && redstonePresent) {
            // 手动：物理体由转速驱动，影子车跟着它的真实速度走（速度以物理体为准）
            train.speed = bodySpeedAlongTrackBps() / 20.0;
            double dir = left > 0 ? 1.0 : -1.0;
            double mag = Math.max(left, right) / 15.0;
            train.manualTick = true;
            train.targetSpeed = dir * mag * MANUAL_MAX_SPEED;
            // 直接赋速度：Create 的 acceleration() 只有 ~0.0013 格/tick²，
            // 靠 approachTargetSpeed 爬升要 20 秒才看得出动，手动操控必须立刻响应
            train.speed = train.targetSpeed;
            // 左转/右转是"给转向架的输出通道"，不是输入的回显
            setOutput(SignalChannel.BRAKE, 0);
            // ★ 手动模式也要输出动力：方向+强度直接换算成转速

        }

        // ── 到站补齐 ──
        // Create 自己的到站是靠 TravellingPoint 的边点监听触发的。
        // 影子车的轨道点是复制来的，压过车站边点时监听不一定被触发，
        // 结果就是"滑进站了但站牌不立、时刻表也以为没到"。
        // 这里在距离足够近时主动补一次 arriveAt。
        // ── 到站判定：用车站的精确坐标做"穿越检测"，不猜距离 ──
        //
        // GlobalStation 继承 SingleBlockEntityEdgePoint，暴露了 blockEntityPos，
        // 所以我们能拿到车站的确切坐标；再用"车头这一 tick 是否跨过该点"来判定到站，
        // 而不是设一个"距离足够近"的阈值。
        if (train.navigation.destination != null) {
            GlobalStation arriveTarget = train.navigation.destination;
            try {
                net.minecraft.world.phys.Vec3 cur =
                        train.carriages.get(0).getLeadingPoint().getPosition(train.graph);
                if (cur != null) {
                    net.minecraft.world.phys.Vec3 stationPos =
                            net.minecraft.world.phys.Vec3.atCenterOf(arriveTarget.getBlockEntityPos());

                    if (lastLeadPos != null && train.getCurrentStation() != arriveTarget) {
                        net.minecraft.world.phys.Vec3 move = cur.subtract(lastLeadPos);
                        if (move.lengthSqr() > 1.0E-9) {
                            double before = stationPos.subtract(lastLeadPos).dot(move);
                            double after = stationPos.subtract(cur).dot(move);
                            // 车站点在"上一位置→当前位置"这段位移的两侧 = 本 tick 跨过了它
                            if (before >= 0 && after <= 0) {
                                train.arriveAt(arriveTarget);
                                HexieTrain.LOGGER.info(
                                        arriveTarget.name, arriveTarget.getBlockEntityPos(),
                                        String.format("%.2f,%.2f,%.2f", cur.x, cur.y, cur.z));
                            }
                        }
                    }
                    lastLeadPos = cur;

                }
            } catch (Throwable t9) {
            }
        } else {
            lastLeadPos = null;
        }

        // ③c 诊断：每 2 秒打一次列车状态
        // 卡滞检测：时刻表在 PRE_TRANSIT 且没有目的地 = 目标车站不存在/无可行路径
        if (train.navigation.destination == null && !redstonePresent
                && train.runtime.state == com.simibubi.create.content.trains.schedule.ScheduleRuntime.State.PRE_TRANSIT) {
            if (++stallTicks % 200 == 1) {
                String wanted = stopNames.isEmpty() ? "（空）"
                        : stopNames.get(Math.min(train.runtime.currentEntry, stopNames.size() - 1));
                HexieTrain.LOGGER.warn("[HexieTrain] 时刻表卡住：目标 \"{}\" 找不到（已 {} tick）",
                        wanted, stallTicks);
            }
        } else {
            stallTicks = 0;
        }


        // 简化自动驾驶：有时刻表就直接朝车站开
        // 兜底：只要不是"行驶中/滑车/急刹"，一律回到待命
        setDispatcherState(DispatcherState.IDLE);

        if (train.runtime.schedule != null && !manualMode) {
            tickOutputRamp();
            driveToStation(train);
        }

    }

    /** 红石状态变化回调：手动模式下立刻重算转速，不用等下一 tick 的轮询。 */
    public void onRedstoneChanged() {
        if (boundTrain == null) {

        }
        setChanged();
    }

    // ══════════════════════ UI 数据 ══════════════════════


    // ══════════════════════ 站点选择 ══════════════════════

    /** 时刻表里的站点名（给界面长栏用）。 */
    public java.util.List<String> stopNames() {
        return stopNames;
    }

    /** 重新从时刻表里抽取站点名。 */
    public void refreshStopNames() {
        stopNames.clear();
        if (level != null && !scheduleSlot.isEmpty() && scheduleSlot.getItem() instanceof ScheduleItem) {
            try {
                Schedule schedule = ScheduleItem.getSchedule(level.registryAccess(), scheduleSlot);
                if (schedule != null && schedule.entries != null) {
                    for (ScheduleEntry entry : schedule.entries) {
                        if (entry != null && entry.instruction instanceof DestinationInstruction dest) {
                            String name = dest.getFilter();
                            stopNames.add(name == null || name.isBlank() ? "（未命名）" : name);
                        } else {
                            stopNames.add("（非车站指令）");
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        sendData();
    }


    // ══════════════════════ 驾驶模式 ══════════════════════

    public boolean manualMode() {
        return manualMode;
    }

    /** 切换自动驾驶 / 玩家操控。切回自动时会恢复寻路。 */
    public void setManualMode(boolean value) {
        manualMode = value;
        if (level != null && !level.isClientSide && boundTrain != null) {
            Train train = Create.RAILWAYS.trains.get(boundTrain);
            if (train != null) {
                try {
                    if (value) {
                        train.navigation.cancelNavigation();   // 只取消寻路目标，不冻结列车
                        train.runtime.paused = false;           // 关键：不能暂停，否则列车的点不再推进
                        // 播报：玩家接管
                    } else {
                        train.runtime.paused = false;          // 恢复自动巡航
                        // 切回自动驾驶：立刻试一次寻路，并把结果播报给车主
                        var stations9 = new java.util.ArrayList<com.simibubi.create.content.trains.station.GlobalStation>(
                                train.graph.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION));
                        var path9 = train.navigation.findPathTo(stations9, Double.MAX_VALUE);
                        if (path9 != null) {
                            try { train.status.successfulNavigation(); } catch (Throwable ignored) { }
                            HexieTrain.LOGGER.info("[HexieTrain][path] 导航成功");
                        } else {
                            String target9 = stations9.isEmpty() ? "" : stations9.get(0).name;
                            try { train.status.failedNavigationNoTarget(target9); } catch (Throwable ignored) { }
                            HexieTrain.LOGGER.info("[HexieTrain][path] 找不到可用路径（目标 {}）", target9);
                        }
                        train.manualTick = false;
                        train.targetSpeed = 0;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        setChanged();
        // 模式变了，传动比也变了：必须强制重建网络，否则 Create 仍用旧缓存
        refreshKinetics();
        sendData();
    }


    /** 下一个目标的显示名（车站名 / 前方信号）。 */
    public String nextTargetName() {
        return nextTargetName;
    }

    /** 到目标距离（格）；没有目标时为 0。 */
    public double distanceToTarget() {
        return lastDistance;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hexietrain.train_dispatcher");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TrainDispatcherMenu(containerId, playerInventory, this);
    }

    /** 最近玩家的距离（格）；没有玩家在线时返回极大值。 */
    private double nearestPlayerDistance() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        // 注意：方块在 Sable 绘图区里，必须换算成世界坐标
        Vec3 center = SubLevelSuspend.worldCenter(serverLevel, worldPosition);
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : serverLevel.players()) {
            double d = player.position().distanceTo(center);
            if (d < best) best = d;
        }
        return best;
    }


    private void setOutput(SignalChannel channel, int value) {
        int v = Math.max(0, Math.min(15, value));
        if (outputs[channel.index()] == v) return;
        outputs[channel.index()] = v;
        setChanged();
        sendData();
    }

    // ══════════════════════ 无线红石 ══════════════════════

    /** 某通道的网络键：一对频率（频率1 / 频率2）。 */
    public Couple<Frequency> networkKey(SignalChannel channel) {
        int base = channel.index() * 2;
        return Couple.create(frequencyOf(frequencies[base]), frequencyOf(frequencies[base + 1]));
    }

    private static Frequency frequencyOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Frequency.EMPTY : Frequency.of(stack);
    }

    @Override
    public void initialize() {
        super.initialize();
        ACTIVE.add(this);
        if (level != null && !level.isClientSide) {
            for (WirelessChannel ch : wireless) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, ch);
            }
        }
    }

    /** 频率变了：让网络重新挂一次。 */
    private void refreshNetwork() {
        if (level == null || level.isClientSide) return;
        for (WirelessChannel ch : wireless) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, ch);
        }
    }

    @Override
    public void remove() {
        ACTIVE.remove(this);
        if (level != null && !level.isClientSide) {
            for (WirelessChannel ch : wireless) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, ch);
            }
        }
        super.remove();
    }

    /** 某个通道是否配置了完整的频率对（可以真正发信号）。 */
    public boolean channelConfigured(SignalChannel channel) {
        int base = channel.index() * 2;
        return !frequencies[base].isEmpty() && !frequencies[base + 1].isEmpty();
    }

    // ══════════════════════ 时刻表 → 列车身份 ══════════════════════

    /** 统一的槽位读取：0 = 时刻表，1~6 = 频率槽。 */
    public ItemStack slot(int index) {
        if (index == 0) return scheduleSlot;
        return frequency(index - 1);
    }

    /** 统一的槽位写入。 */
    public void setSlot(int index, ItemStack stack) {
        if (index == 0) {
        refreshWireless();
            scheduleSlot = stack == null ? ItemStack.EMPTY : stack;
            setChanged();
        } else {
            setFrequency(index - 1, stack);
        }
    }

    public ItemStack scheduleSlot() {
        return scheduleSlot;
    }

    public String lastBindMessage() {
        return lastBindMessage;
    }

    /**
     * 放入 / 取出时刻表。
     *
     * <p>放入：检查附近转向架 + 物理结构是否在轨道上 → 建 Train → 绑定。
     * <p>取出：注销 Train（解除寻路、离站、从 RAILWAYS 移除）。
     */
    public void setScheduleSlot(ItemStack stack) {
        this.scheduleSlot = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);

        if (level instanceof ServerLevel serverLevel) {
            if (!scheduleSlot.isEmpty() && scheduleSlot.getItem() instanceof ScheduleItem) {
                // ★ 先注销旧车，避免重复加载造成列车冗余
                if (boundTrain != null) {
                    HexieTrain.LOGGER.info("[HexieTrain] 重新加载时刻表：先注销旧列车 {}", boundTrain);
                    dissolve();
                }
                TrainBinder.Result result;
                try {
                result = TrainBinder.bind(serverLevel, worldPosition, scheduleSlot, owner);
                } catch (Throwable t) {
                    // 崩溃兜底：绝不让建车的异常冒到 tick 里把游戏带崩
                    HexieTrain.LOGGER.error("[HexieTrain] 建车异常 @{}", worldPosition, t);
                    result = new TrainBinder.Result(false, null,
                            "建车异常：" + t.getClass().getSimpleName());
                }
                lastBindMessage = result.message();
                HexieTrain.LOGGER.info("[HexieTrain][ui] bind 返回 ok={} msg={}", result.ok(), result.message());
                if (result.ok()) {
                    bindTrain(result.trainId());
                    // ★ 每辆物理列车单独持有一个"永不被视距卸载"的力加载票据
                    boolean loaded = SubLevelSuspend.resume(serverLevel, worldPosition);
                    if (loaded) {
                        TrainBinder.PERSISTENT_LOADS.add(result.trainId());
                    }
                    HexieTrain.LOGGER.info("[HexieTrain][ui] 列车已挂持久加载票据={}（共 {} 辆）",
                            loaded, TrainBinder.PERSISTENT_LOADS.size());
                }
            } else if (boundTrain != null) {
                dissolve();
                SubLevelSuspend.suspend(serverLevel, worldPosition);
                lastBindMessage = "已取消列车身份";
            }
        }
        refreshStopNames();
        setChanged();
        sendData();
    }

    /** 拆除列车身份：解除寻路 → 离站 → 从 RAILWAYS 移除。 */
    public void dissolve() {
        if (boundTrain == null) return;
        if (level != null && !level.isClientSide) {
            Train train = Create.RAILWAYS.trains.get(boundTrain);
            if (train != null) {
                try {
                    train.navigation.cancelNavigation();
                } catch (Throwable ignored) {
                }
                try {
                    train.leaveStation();
                } catch (Throwable ignored) {
                }
                Create.RAILWAYS.removeTrain(boundTrain);
                TrainBinder.OUR_TRAINS.remove(boundTrain);
            }
        }
        if (level != null) {
        }
        boundTrain = null;
        lastDestination = null;
    }

    // ══════════════════════ 对外接口 ══════════════════════

    public int output(SignalChannel channel) {
        return outputs[channel.index()];
    }

    public ItemStack frequency(int slot) {
        return slot >= 0 && slot < FREQ_SLOTS ? frequencies[slot] : ItemStack.EMPTY;
    }

    public void setFrequency(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FREQ_SLOTS) return;
        frequencies[slot] = stack == null ? ItemStack.EMPTY : stack;
        refreshNetwork();
        setChanged();
        sendData();
    }

    public UUID boundTrain() {
        return boundTrain;
    }

    public void bindTrain(UUID id) {
        boundTrain = id;
        lastDestination = null;
        setChanged();
        sendData();
    }


    public boolean linearizeBrake() {
        return linearizeBrake;
    }

    public void setLinearizeBrake(boolean value) {
        linearizeBrake = value;
        setChanged();
    }

    // ══════════════════════ 存档 ══════════════════════

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boundTrain != null) tag.putUUID("BoundTrain", boundTrain);
        tag.putBoolean("ManualMode", manualMode);
        if (owner != null) tag.putUUID("Owner", owner);
        // 方块特性：玩家在 UI 里调的制动灵敏度
        if (!scheduleSlot.isEmpty()) tag.put("Schedule", sanitizedSchedule().save(registries));
        net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (String s : stopNames) names.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("StopNames", names);
        tag.putBoolean("LinearizeBrake", linearizeBrake);
        for (int i = 0; i < FREQ_SLOTS; i++) {
            if (!frequencies[i].isEmpty()) {
                tag.put("Freq" + i, frequencies[i].save(registries));
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boundTrain = tag.hasUUID("BoundTrain") ? tag.getUUID("BoundTrain") : null;
        if (boundTrain != null) {
            // 重启后 OUR_TRAINS 是空的，必须重新登记，否则车厢会失去 mixin 保护
            TrainBinder.OUR_TRAINS.add(boundTrain);
            HexieTrain.LOGGER.info("[HexieTrain] 从存档恢复列车绑定 {}", boundTrain);
        }
        manualMode = tag.getBoolean("ManualMode");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // 方块特性：制动灵敏度（缺省 3.0 格/秒²）

        // 瞬态状态：读档后一律复位，等本 tick 重新判定
        stallTicks = 0;
        lastLeadPos = null;
        lastDestination = null;
        linearizeBrake = !tag.contains("LinearizeBrake") || tag.getBoolean("LinearizeBrake");

        scheduleSlot = tag.contains("Schedule")
                ? ItemStack.parseOptional(registries, tag.getCompound("Schedule"))
                : ItemStack.EMPTY;

        stopNames.clear();
        net.minecraft.nbt.ListTag names = tag.getList("StopNames", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) {
            stopNames.add(names.getString(i));
        }

        for (int i = 0; i < FREQ_SLOTS; i++) {
            String key = "Freq" + i;
            frequencies[i] = tag.contains(key)
                    ? ItemStack.parseOptional(registries, tag.getCompound(key))
                    : ItemStack.EMPTY;
        }
    }


    /**
     * 物理体沿轨道的真实速度（格/秒）。
     *
     * <p>手动模式下物理体是被转速驱动的，影子车要跟着它走，
     * 否则两边的位置会越差越远。
     */
    /**
     * 更新速度传感器读数。
     *
     * <p>数据来源照抄 simulated 的速度传感器：同一局部点在 current/last 姿态下的世界位移 × 20，
     * 因此包含旋转贡献，等价于"结构上这一点实际怎么动"。
     *
     * <p>每 tick 调用一次，结果常驻在 {@link #measuredVelocity()} / {@link #measuredSpeedMps()}，
     * 供 UI 显示与后续控制运算使用。
     */
    private void updateMeasuredSpeed() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        org.joml.Vector3d v = SubLevelSuspend.velocityAt(serverLevel, worldPosition);
        measuredVelocity.set(v);
        double mag = v.length();
        measuredSpeedMps = mag > 0.05 ? mag : 0.0;      // 与航空学一致的死区
    }

    /** 速度传感器读数：速度大小（m/s）。 */
    public double measuredSpeedMps() {
        return measuredSpeedMps;
    }

    /** 速度传感器读数：世界速度矢量（m/s），调用方不要修改。 */
    public org.joml.Vector3dc measuredVelocity() {
        return measuredVelocity;
    }

    /** 速度传感器读数，放大 100 倍的整数（用于 ContainerData 同步）。 */
    public int measuredSpeedCenti() {
        return (int) Math.round(measuredSpeedMps * 100.0);
    }
    private double bodySpeedAlongTrackBps() {
        try {
            Train tr = Create.RAILWAYS.trains.get(boundTrain);
            if (tr == null || tr.carriages.isEmpty()) return 0;
            var lp = tr.carriages.get(0).getLeadingPoint();
            if (lp == null || lp.edge == null) return 0;

            double len = Math.max(1.0E-6, lp.edge.getLength());
            net.minecraft.world.phys.Vec3 dir = lp.edge.getDirectionAt(Math.min(1.0, lp.position / len));

            // 用速度传感器读数（姿态差分），而不是刚体质心线速度：
            // 后者不含旋转贡献，过弯时会偏小。
            var v = measuredVelocity();
            return v.x() * dir.x + v.y() * dir.y + v.z() * dir.z;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 记录操作者。 */
    public void setOwner(UUID playerId) {
        if (playerId == null || playerId.equals(owner)) return;
        owner = playerId;
        setChanged();
    }

    public UUID owner() {
        return owner;
    }

    /**
     * 自动驾驶：只控制"给不给传动"和"刹不刹车"，速度由转向架的推力决定。
     *
     * <p>进制动区后，目标速度由剩余距离决定；实际速度超标就持续刹车，
     * 直到降到目标速度以下（与 Create 的 Navigation 行为一致）。
     */
    /**
     * 自动行驶：只控制传动方向与刹车，速度由转向架推力决定。
     */
    private void driveToStation(Train train) {
        try {
            if (train.derailed || train.invalid) { setAutoDirection(0f); return; }
            if (!isPrimaryDispatcher()) { setAutoDirection(0f); return; }

            if (waitingForSchedule) {
                if (train.runtime != null && train.runtime.currentEntry != stoppedEntry) {
                    waitingForSchedule = false;
                    SetOutputBrake(0);
                } else {
                    setAutoDirection(0f);
                    SetOutputBrake(15);
                    return;
                }
            }

            if (train.carriages.isEmpty()) return;
            var lp = train.carriages.get(0).getLeadingPoint();
            if (lp == null || lp.edge == null) return;

            if (lastEntrySeen != train.runtime.currentEntry) {
                HexieTrain.LOGGER.info("[HexieTrain][entry] currentEntry {} → {}",
                        lastEntrySeen, train.runtime.currentEntry);
                lastEntrySeen = train.runtime.currentEntry;
            }

            GlobalStation target = stationForCurrentEntry(train);
            if (target == null) { setAutoDirection(0f); return; }

            // 剩余距离：优先用 Create 的轨道距离（有导航目标时才有效），否则退回直线距离
            double d = remainingDistance(train, target);
            if (d < 0) { setAutoDirection(0f); return; }

            double v = Math.abs(measuredSpeedMps());            // m/s
            boolean forward = isStationAhead(train, target, lp);
            double sign = forward ? 1.0 : -1.0;

            if (++targetLogTicks >= 20) {
                targetLogTicks = 0;
                HexieTrain.LOGGER.info("[HexieTrain][drive] entry={} 目标={} d={} v={}",
                        train.runtime.currentEntry, target.name, String.format("%.2f", d), String.format("%.2f", v));
            }

            // ① 已经到位：刹死
            if (d <= 0.05) {
                setAutoDirection(0f);
                SetOutputBrake(15);
                completeArrival(train, target);
                return;
            }

            // ② 目标速度曲线：>5 格不限速；<=5 格线性降到 3 m/s；下限蠕行
            double targetV;
            if (d > SLOW_ZONE_BLOCKS) {
                targetV = Double.MAX_VALUE;
            } else {
                targetV = Math.max(CREEP_SPEED_MPS, SLOW_ZONE_SPEED_MPS * (d / SLOW_ZONE_BLOCKS));
            }

            // ③ 期望滑行距离：v² / (2a)
            double dStop = (v * v) / (2.0 * EXPECTED_BRAKE_DECEL);
            boolean cantStop = d < REVERSE_ZONE_BLOCKS && (dStop + STOP_MARGIN_BLOCKS > d);

            // ④ 给红石强度（纯距离映射）
            int brake = SignalChannel.brakeStrength(d, v / 20.0, linearizeBrake);

            if (cantStop) {
                // 刹不住：反向传动提供反向推力 + 距离强度刹车
                setAutoDirection((float) -sign);
                SetOutputBrake(brake);
            } else if (v > targetV + 0.25) {
                // 超速：顺向传动 + 距离强度刹车
                setAutoDirection((float) sign);
                SetOutputBrake(brake);
            } else if (v < targetV - 0.25) {
                // 需要加速
                setAutoDirection((float) sign);
                SetOutputBrake(0);
            } else {
                // 速度合适：松刹车滑行
                setAutoDirection((float) sign);
                SetOutputBrake(0);
            }

            // ⑤ 蠕行兜底：很近但还没跨过站点 → 松刹车 + 前进推力，确保压过站点
            if (d < 1.0 && v < 0.2) {
                SetOutputBrake(0);
                setAutoDirection((float) sign);
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][auto] 驱动异常: {}", t.toString());
        }
    }

    /** 剩余距离：优先 Create 的轨道距离，拿不到时退回直线距离。 */
    private double remainingDistance(Train train, GlobalStation target) {
        try {
            var nav = train.navigation;
            if (nav != null && nav.destination == target) {
                return Math.abs(nav.distanceToDestination);
            }
        } catch (Throwable ignored) {
        }
        return distanceToStation(train, target);
    }

    /**
     * 完成一次到站：车站登记 + 状态前置 + arriveAt + 清理导航目标。
     *
     * <p>顺序不能变：reserveFor 让车站"认得"这辆车（getPresentTrain），
     * state 必须是 IN_TRANSIT 否则 destinationReached 直接 return。
     */
    private void completeArrival(Train train, GlobalStation target) {
        if (arrivedEntry == train.runtime.currentEntry) return;
        try {
            int entryBefore = train.runtime.currentEntry;
            target.reserveFor(train);                                             // ① 车站登记
            train.runtime.state = com.simibubi.create.content.trains.schedule.ScheduleRuntime.State.IN_TRANSIT;  // ② 前置
            train.arriveAt(target);                                               // ③ 到站
            if (train.navigation != null) train.navigation.destination = null;    // ④ 清理
            arrivedEntry = entryBefore;
            stoppedEntry = entryBefore;
            waitingForSchedule = true;
            HexieTrain.LOGGER.info("[HexieTrain][arrive] 到达 {}：currentEntry {} → {}（cyclic={}）",
                    target.name, entryBefore, train.runtime.currentEntry,
                    train.runtime.schedule != null && train.runtime.schedule.cyclic);
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][arrive] 到站处理异常: {}", t.toString());
        }
    }

    private void SetOutputBrake(int value) {
        setOutput(SignalChannel.BRAKE, Math.max(0, Math.min(15, value)));
    }

    /** 设置自动模式下的传动方向（会重建动力网络，让新传动比立刻生效）。 */
    private void setAutoDirection(float dir) {
        if (Math.abs(dir - autoDirection) < 1.0E-4f) return;
        autoDirection = dir;
        if (level != null && !level.isClientSide
                && getBlockState().getBlock() instanceof TrainDispatcherBlock block) {
            level.blockUpdated(worldPosition, block);   // 安全刷新，不再摘网络
        }
    }

    /** 强制让 Create 重新评估本方的传动比。 */
    private void refreshKinetics() {
        if (level == null || level.isClientSide) return;
        if (getBlockState().getBlock() instanceof TrainDispatcherBlock block) {
            level.blockUpdated(worldPosition, block);   // 安全刷新，不再摘网络
        }
    }

    /** 当前正在执行的第几条站点。 */
    public int currentEntry() {
        try {
            Train tr = Create.RAILWAYS.trains.get(boundTrain);
            if (tr != null && tr.runtime != null) {
                return tr.runtime.currentEntry;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }


    /**
     * 输出转速渐变：0→满值按加速度决定，a=1 时 1 秒（20 tick）走完 0~100%。
     *
     * <p>Create 的动力网络是缓存的，所以只在比例跨过 5% 台阶时才重建一次，
     * 避免每 tick detachKinetics 造成网络抖动。</p>
     */
    private void tickOutputRamp() {
        float targetRatio = (!manualMode && boundTrain != null && autoDirection != 0f) ? 1f : 0f;
        // 起步顺序固定：先松刹车 → 再给传动
        if (targetRatio > 0f && output(SignalChannel.BRAKE) != 0) {
            setOutput(SignalChannel.BRAKE, 0);
        }
        float step = 1.0f;    // 不再做转速渐变：一步到位

        float next = outputRatio;
        if (Math.abs(targetRatio - outputRatio) <= step) {
            next = targetRatio;
        } else {
            next = outputRatio + Math.signum(targetRatio - outputRatio) * step;
        }
        if (Math.abs(next - outputRatio) < 1.0E-4f) return;
        outputRatio = next;

        int quantum = (int) (outputRatio * 20f);
        if (quantum != lastRatioQuantum) {
            lastRatioQuantum = quantum;
            refreshKinetics();
        }
    }

    /** 本方块是否是这列车的主调度器（一列车只允许一个输出传动）。 */
    private boolean isPrimaryDispatcher() {
        if (boundTrain == null) return false;
        net.minecraft.core.BlockPos primary = TrainBinder.TRAIN_DISPATCHERS.get(boundTrain);
        return primary != null && primary.equals(worldPosition);
    }


    /** 当前实际速度（格/tick × 100），给 UI 显示用。 */

    /** 当前调度器工作状态。 */
    public DispatcherState dispatcherState() {
        return dispatcherState;
    }

    private void setDispatcherState(DispatcherState state) {
        if (dispatcherState == state) return;
        dispatcherState = state;
        sendData();
    }

    /**
     * 时刻表当前条目指定的车站。
     *
     * <p>原版就是这样：目标由条目里的 DestinationInstruction 决定，
     * 而不是"图里第一个车站"。</p>
     */
    private GlobalStation stationForCurrentEntry(Train train) {
        try {
            if (train.runtime == null || train.runtime.schedule == null) return null;
            var entries = train.runtime.schedule.entries;
            int idx = Math.max(0, Math.min(entries.size() - 1, train.runtime.currentEntry));
            if (entries.isEmpty()) return null;

            var instruction = entries.get(idx).instruction;
            if (!(instruction instanceof com.simibubi.create.content.trains.schedule.destination.DestinationInstruction dest)) {
                return null;
            }
            String filter = dest.getFilter();
            if (filter == null || filter.isBlank()) return null;

            for (GlobalStation st : train.graph.getPoints(
                    com.simibubi.create.content.trains.graph.EdgePointType.STATION)) {
                if (st.name != null && st.name.matches(filter)) return st;
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][auto] 解析目标站失败: {}", t.toString());
        }
        return null;
    }

    /** 频率变了：必须从网络摘掉再挂回去，否则仍用旧的网络键。 */
    private void refreshWireless() {
        if (level == null || level.isClientSide) return;
        for (WirelessChannel ch : wireless) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, ch);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, ch);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 净化时刻表物品：旧版本曾把 Schedule 对象直接写进物品组件（应为 CompoundTag），
     * 那会导致存档序列化时 ClassCastException，进而整个存档崩掉。
     * 这里在保存前把非法组件剥掉，让存档自愈。
     */
    private ItemStack sanitizedSchedule() {
        ItemStack stack = scheduleSlot.copy();
        if (stack.isEmpty()) return stack;
        try {
            if (stack.getItem() instanceof ScheduleItem scheduleItem) {
                @SuppressWarnings("unchecked")
                net.minecraft.core.component.DataComponentType<Object> type =
                        (net.minecraft.core.component.DataComponentType<Object>) scheduleItem.getComponentType();
                Object value = stack.get(type);
                if (value != null && !(value instanceof CompoundTag)) {
                    stack.remove(type);
                    HexieTrain.LOGGER.warn("[HexieTrain] 已清除损坏的时刻表组件（类型 {}），存档可继续",
                            value.getClass().getName());
                }
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain] 净化时刻表失败: {}", t.toString());
        }
        return stack;
    }

    // ══════════════════ Container（让方块成为"能存一个时刻表的容器"） ══════════════════

    @Override
    public int getContainerSize() {
        return TrainDispatcherContainer.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!slot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return slot(index);
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        ItemStack current = slot(index);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        setItem(index, current);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = slot(index);
        setItem(index, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index == 0) {
            setScheduleSlot(stack);
        } else {
            setFrequency(index - 1, stack);
        }
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return hasLevel() && player.distanceToSqr(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        setScheduleSlot(ItemStack.EMPTY);
        for (int i = 0; i < FREQ_SLOTS; i++) {
            setFrequency(i, ItemStack.EMPTY);
        }
    }

    /** 车头到车站方块中心的直线距离（替代原来的图搜索距离）。 */
    private double distanceToStation(Train train, GlobalStation target) {
        try {
            var head = train.carriages.get(0).getLeadingPoint().getPosition(train.graph);
            var sp = net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockEntityPos());
            if (head == null) return -1;
            return head.distanceTo(sp);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 车站是否在车头前进方向上（用轨道切线与指向车站的向量点积判断）。 */
    private boolean isStationAhead(Train train, GlobalStation target,
                                   com.simibubi.create.content.trains.entity.TravellingPoint lp) {
        try {
            var head = lp.getPosition(train.graph);
            var sp = net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockEntityPos());
            if (head == null || lp.edge == null) return true;
            var tangent = lp.edge.getDirection(true);
            return tangent.dot(sp.subtract(head)) >= 0;
        } catch (Throwable t) {
            return true;
        }
    }

}