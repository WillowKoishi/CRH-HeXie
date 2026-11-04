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
    /** 方块的"左"是否就是虚拟列车定义的前进方向（绑定/放置时一次判定，之后不变）。 */
    private Boolean leftIsForward = null;

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
    private int rebindCooldown = 0;
    private boolean leakSwept = false;

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


    /**
     * 玩家操控中：本方块就是一个定向齿轮换向器，仅此而已。
     *
     * <p>不做任何额外的事 —— 不推物理体、不设速度、不输出通道。
     * 传动比由 getRotationSpeedModifier 按左右红石直接决定，
     * 转速则由上游传动轴提供。
     */
    // ══════════ 【重写】调度器运行逻辑 ══════════

    /** 自动模式：一切由 Create 的导航决定方向与距离，我们只负责输出传动与刹车。 */
    /** 保证列车有"列车员"标志：DestinationInstruction.start 的第一道门槛。 */
    private void ensureConductors(Train train) {
        try {
            if (train == null) return;
            for (var c : train.carriages) {
                if (c.presentConductors == null) continue;
                c.presentConductors.replace(v -> Boolean.TRUE);
            }
        } catch (Throwable ignored) {}
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


    /** 由 DispatcherTicker 每 tick 调用。 */
    public void serverTick() {
        if (!leakSwept) {
            leakSwept = true;
            try {
                java.util.List<UUID> stale = new java.util.ArrayList<>(TrainBinder.OUR_TRAINS);
                stale.removeAll(TrainBinder.SUBLEVEL_TRAINS.values());
                stale.remove(boundTrain);
                for (UUID id : stale) TrainBinder.unregister(id);
                if (!stale.isEmpty()) HexieTrain.LOGGER.info("[HexieTrain][clean] 清理泄露列车 {} 辆", stale.size());
            } catch (Throwable ignored) {}
        }
        updateMeasuredSpeed();
        try {
            if (boundTrain != null && !Create.RAILWAYS.trains.containsKey(boundTrain)) {
                HexieTrain.LOGGER.warn("[HexieTrain][run] RAILWAYS 找不到列车 {}，保留绑定等待", boundTrain);
                return;
            }
            if (boundTrain == null && !scheduleSlot.isEmpty() && rebindCooldown-- <= 0 && level instanceof ServerLevel
                    && (rebindCooldown = 200) > 0
                    && scheduleSlot.getItem() instanceof com.simibubi.create.content.trains.schedule.ScheduleItem) {
                HexieTrain.LOGGER.info("[HexieTrain][rebind] 槽内有时刻表但未绑定，重建中");
                setScheduleSlot(scheduleSlot);
            }
        } catch (Throwable t) { HexieTrain.LOGGER.warn("[HexieTrain][run] 自检异常 {}", t.toString()); }
        if (boundTrain != null) tickAutomated(); else tickManual();
    }

    /** 自动模式：方向与距离都来自 Create 的导航，我们只输出传动与刹车。 */
    private void tickAutomated() {
        try {
            Train train = Create.RAILWAYS.trains.get(boundTrain);
            if (train == null) return;
            ensureConductors(train);

            int left = leftSignal(), right = rightSignal();
            boolean redstonePresent = left > 0 || right > 0;

            // 玩家给红石 → 单向切手动（要恢复请点 UI 或重设列车）
            if (redstonePresent) {
                if (!manualMode) setManualMode(true);
                double dir = left > 0 ? 1.0 : -1.0;
                double mag = Math.max(left, right) / 15.0;
                train.manualTick = true;
                train.targetSpeed = dir * mag * MANUAL_MAX_SPEED;
                train.approachTargetSpeed(1.0f);
                return;
            }
            if (manualMode) { setAutoDirection(0f); SetOutputBrake(15); return; }

            driveToStation(train);
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][auto] 异常: {}", t.toString());
        }
    }

    /** 未绑定：只做红石模式同步，不驱动任何东西。 */
    private void tickManual() {
        boolean manual = leftSignal() > 0 || rightSignal() > 0;
        if (manual != manualMode) { manualMode = manual; setChanged(); sendData(); }
    }

    /**
     * 一次判定：方块的"左"是否等于虚拟列车的前进方向。
     * 依据 Simurail 转向架：getMovementSpeed() 已是整条传动链累乘后的行驶速度，
     * 乘上 getDirection() 再投影到轨道切线，就是"这组输出让车沿轨道往哪走"。
     */
    private boolean leftPolarity() {
        if (leftIsForward != null) return leftIsForward;
        leftIsForward = true;
        try {
            Train train = Create.RAILWAYS.trains.get(boundTrain);
            if (train == null || train.carriages.isEmpty() || !(level instanceof ServerLevel sl)) return true;
            var lp = train.carriages.get(0).getLeadingPoint();
            if (lp == null || lp.edge == null) return true;
            var tan = lp.edge.getDirection(true);
            double best = 0;
            for (net.minecraft.core.BlockPos bp : net.minecraft.core.BlockPos.betweenClosed(
                    worldPosition.offset(-6, -4, -6), worldPosition.offset(6, 4, 6))) {
                var be = sl.getBlockEntity(bp);
                if (be == null || !be.getClass().getName().contains("PhysicsBogeyBlockEntity")) continue;
                try {
                    double ms = (double) be.getClass().getMethod("getMovementSpeed").invoke(be);
                    var dir = (org.joml.Vector3dc) be.getClass().getMethod("getDirection").invoke(be);
                    double dot = ms * (dir.x() * tan.x + dir.y() * tan.y + dir.z() * tan.z);
                    if (Math.abs(dot) > Math.abs(best)) best = dot;
                } catch (Throwable ignored) {}
            }
            if (Math.abs(best) > 1.0E-4) {
                leftIsForward = best > 0;
                HexieTrain.LOGGER.info("[HexieTrain][dir] dot={} 左是前进={}", String.format("%.3f", best), leftIsForward);
            } else {
                HexieTrain.LOGGER.info("[HexieTrain][dir] 转向架速度为 0，沿用默认 左是前进=true");
            }
        } catch (Throwable t) { HexieTrain.LOGGER.warn("[HexieTrain][dir] {}", t.toString()); }
        return leftIsForward;
    }

    /** 输出传动方向：写 blockstate 的 LEFT/RIGHT_POWERED 并重建动力学网络。 */
    private void setAutoDirection(float dir) {
        if (Math.abs(dir - autoDirection) < 1.0E-4f) return;
        autoDirection = dir;
        if (!(level instanceof ServerLevel sl)) return;
        try {
            BlockState st = getBlockState();
            if (!(st.getBlock() instanceof TrainDispatcherBlock block)) return;
            boolean forward = dir > 0.001f, reverse = dir < -0.001f;
            boolean pol = leftPolarity();
            boolean left  = forward ? pol : (reverse ? !pol : false);
            boolean right = forward ? !pol : (reverse ? pol : false);
            if (st.getValue(TrainDispatcherBlock.LEFT_POWERED) == left
                    && st.getValue(TrainDispatcherBlock.RIGHT_POWERED) == right) return;
            sl.setBlock(worldPosition, st.setValue(TrainDispatcherBlock.LEFT_POWERED, left)
                    .setValue(TrainDispatcherBlock.RIGHT_POWERED, right), 3);
            block.detachKinetics(sl, worldPosition, true);
        } catch (Throwable t) { HexieTrain.LOGGER.warn("[HexieTrain][kinetics] {}", t.toString()); }
    }

    /** 沿轨距离核减 + 物理到站。 */
    private double legRemaining = -1;
    private Object lastLegDest = null;
    private int lastLegEntry = -1;

    private void driveToStation(Train train) {
        try {
            if (train.derailed || train.invalid || !isPrimaryDispatcher()) { setAutoDirection(0f); return; }
            var nav = train.navigation;
            if (nav == null || nav.destination == null) { setAutoDirection(0f); SetOutputBrake(0); return; }

            if (nav.destination != lastLegDest || train.runtime.currentEntry != lastLegEntry || legRemaining < 0) {
                legRemaining = Math.max(0.0, nav.distanceToDestination);
                lastLegDest = nav.destination;
                lastLegEntry = train.runtime.currentEntry;
                leftIsForward = null;
                HexieTrain.LOGGER.info("[HexieTrain][leg] → {} 沿轨长度={}", nav.destination.name, String.format("%.1f", legRemaining));
            }

            double moved = bodySpeedAlongTrackBps() / 20.0;
            legRemaining = Math.max(0.0, legRemaining - moved);
            nav.distanceToDestination = legRemaining;

            double v = Math.abs(measuredSpeedMps());
            double sign = nav.destinationBehindTrain ? -1.0 : 1.0;
            double targetV = legRemaining > SLOW_ZONE_BLOCKS ? Double.MAX_VALUE
                    : Math.max(CREEP_SPEED_MPS, SLOW_ZONE_SPEED_MPS * (legRemaining / SLOW_ZONE_BLOCKS));
            int brake = SignalChannel.brakeStrength(legRemaining, v / 20.0, linearizeBrake);

            setAutoDirection((float) sign);
            SetOutputBrake(v > targetV + 0.25 ? brake : 0);
            if (legRemaining < 1.0 && v < 0.2) SetOutputBrake(0);

            if (legRemaining <= 0.05 && v < 0.5) {
                GlobalStation st = nav.destination;
                snapVirtualTo(train, st);
                try { train.arriveAt(st); } catch (Throwable ignored) {}
                HexieTrain.LOGGER.info("[HexieTrain][arrive] {} → arriveAt()", st.name);
                legRemaining = -1; lastLegDest = null;
            }
        } catch (Throwable t) { HexieTrain.LOGGER.warn("[HexieTrain][auto] {}", t.toString()); }
    }

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
        leftIsForward = null;   // 重新判定朝向
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
        if (tag.hasUUID("BoundTrain")) boundTrain = tag.getUUID("BoundTrain");   // 缺失时保留已有绑定
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

    // 【实验性代码】开始：沿轨距离核减 + 物理到站


    /** 把虚拟车头点对齐到车站（一次性，仅到站时）。 */
    private void snapVirtualTo(Train train, GlobalStation st) {
        try {
            var graph = train.graph;
            if (graph == null || train.carriages.isEmpty()) return;
            var loc = st.edgeLocation;
            var n1 = graph.locateNode(loc.getFirst());
            var n2 = graph.locateNode(loc.getSecond());
            var e = graph.getConnectionsFrom(n1).get(n2);
            if (e == null) return;
            var lp = train.carriages.get(0).getLeadingPoint();
            lp.node1 = n1; lp.node2 = n2; lp.edge = e;
            lp.position = st.getLocationOn(e);
        } catch (Throwable ignored) {}
    }
    // 【实验性代码】结束



    private void SetOutputBrake(int value) {
        setOutput(SignalChannel.BRAKE, Math.max(0, Math.min(15, value)));
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



}