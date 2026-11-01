package willow.train.hexietrain;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyGroup;
import dev.ryanhcode.sable.Sable;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.createmod.catnip.data.Couple;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * "插入时刻表 → 物理结构变成列车" 的落地实现。
 *
 * <p>流程：
 * <ol>
 *   <li>在调度台附近找物理转向架（{@link PhysicsBogeyBlockEntity}）</li>
 *   <li>让 Simurail 把连挂的转向架组成一个 {@link PhysicsBogeyGroup}</li>
 *   <li>取组上的轨道图与首尾 {@code TravellingPoint}（Simurail 已经算好的）</li>
 *   <li>拼一辆车（v1：一个物理结构 = 一节车厢）</li>
 *   <li>{@code new Train(...)} 并注册进 {@code RAILWAYS}</li>
 *   <li>把时刻表塞进 {@code Train.runtime}</li>
 * </ol>
 *
 * <p>失败时返回带原因的结果，方块据此显示"未接触轨道 / 没有转向架"。
 */
public final class TrainBinder {

    /** 扫描半径（水平 / 垂直），找转向架用。 */
    private static final int SCAN_H = 12;
    private static final int SCAN_V = 8;

    public record Result(boolean ok, UUID trainId, String message) {
        static Result fail(String why) {
            return new Result(false, null, why);
        }
        static Result success(UUID id, String msg) {
            return new Result(true, id, msg);
        }
    }

    // ── 诊断用：最近一次建的车，逐 tick 观察它在 RAILWAYS 里的存活情况 ──
    public static Train debugTrain;
    public static UUID debugId;
    public static int debugMapHash;
    public static int debugTicks;

    /** 属于本模组的列车 id：混入用它识别"这节车厢是我们的"。 */
    public static final Set<UUID> OUR_TRAINS = ConcurrentHashMap.newKeySet();

    /** 列车 id → 主调度器位置：一列车只允许一个调度器输出传动。 */
    public static final Map<UUID, net.minecraft.core.BlockPos> TRAIN_DISPATCHERS = new ConcurrentHashMap<>();

    /** 列车 id 到动力学模型（质量在绑定时定格）。 */

    /** 列车 id 到物理编组的映射（驱动时要用接触点与切线）。 */

    /** 持有永不被视距卸载票据的列车。 */
    public static final Set<UUID> PERSISTENT_LOADS = ConcurrentHashMap.newKeySet();

    /** 子结构 UUID → 列车 id：保证一个物理结构只对应一辆列车。 */
    public static final java.util.Map<UUID, UUID> SUBLEVEL_TRAINS = new ConcurrentHashMap<>();

    /** 注销一辆车占用的所有登记（RAILWAYS / OUR_TRAINS / 子结构映射）。 */
    public static void unregister(UUID trainId) {
        if (trainId == null) return;
        OUR_TRAINS.remove(trainId);
        PERSISTENT_LOADS.remove(trainId);
        TRAIN_DISPATCHERS.remove(trainId);
        SUBLEVEL_TRAINS.values().removeIf(trainId::equals);
        com.simibubi.create.Create.RAILWAYS.removeTrain(trainId);
    }

    /** 这节车厢是否属于物理列车。 */
    public static boolean isOurCarriage(Carriage carriage) {
        return carriage != null && carriage.train != null
                && OUR_TRAINS.contains(carriage.train.id);
    }

    private TrainBinder() {}

public static Result bind(ServerLevel level, BlockPos dispatcherPos, ItemStack scheduleStack, UUID ownerId) {
        // ⓪ 同一个物理结构只允许一辆车：先把旧的注销掉
        UUID subLevelId = null;
        try {
            var subLevel = SubLevelSuspend.containing(level, dispatcherPos);
            if (subLevel != null) {
                subLevelId = subLevel.getUniqueId();
                UUID existing = SUBLEVEL_TRAINS.get(subLevelId);
                if (existing != null) {
                    HexieTrain.LOGGER.warn("[HexieTrain][bind] 该子结构已有列车 {}，先注销", existing);
                    unregister(existing);
                }
            }
        } catch (Throwable ignored) {
        }

        // ① 找转向架
        HexieTrain.LOGGER.info("[HexieTrain][bind] 1/6 开始，锚点 {}", dispatcherPos);
        PhysicsBogeyBlockEntity bogey = findBogey(level, dispatcherPos);
        if (bogey == null) {
            return Result.fail("附近没有物理转向架");
        }

        // ② 组编组
        HexieTrain.LOGGER.info("[HexieTrain][bind] 2/6 找到转向架 {}", bogey.getBlockPos());
        PhysicsBogeyGroup group;
        try {
            group = PhysicsBogeyGroup.createAndAssign(bogey);
        } catch (Throwable t) {
            return Result.fail("编组失败：" + t.getClass().getSimpleName());
        }
        if (group == null || group.bogeys.isEmpty()) {
            return Result.fail("编组为空");
        }

        // ③ 轨道图（前后轴不在同一张图时为 null）
        HexieTrain.LOGGER.info("[HexieTrain][bind] 3/6 编组 {} 个转向架", group.bogeys.size());
        TrackGraph graph = group.getTrackGraph();
        if (graph == null) {
            return Result.fail("物理结构不在轨道上（或跨图）");
        }

        // ④ 车厢划分 —— 直接继承 Simurail 的 PhysicsBogeyGroup
        //
        // 不再自己拍"一个结构 = 一节车厢"：
        //   · 按 sub-level 分节（一节物理结构 = 一节车厢）
        //   · 每节里第一个/最后一个转向架，就是这节车厢的前/后转向架
        //   · 每个转向架用自己的两根轴点，完全来自 Simurail 的物理数据
        CompoundTag bogeyData = new CompoundTag();
        bogeyData.putString(AbstractBogeyBlockEntity.BOGEY_STYLE_KEY,
                AllBogeyStyles.STANDARD.id.toString());
        bogeyData.put(AbstractBogeyBlockEntity.BOGEY_DATA_KEY,
                AllBogeyStyles.STANDARD.defaultData.copy());

        Map<UUID, List<PhysicsBogeyBlockEntity>> sections = new LinkedHashMap<>();
        for (PhysicsBogeyBlockEntity b : group.bogeys) {
            UUID key = null;
            try {
                var sl = Sable.HELPER.getContaining(level, b.getBlockPos());
                if (sl != null) key = sl.getUniqueId();
            } catch (Throwable ignored) {
            }
            if (key == null) {
                key = UUID.nameUUIDFromBytes(("bogey:" + b.getBlockPos().asLong()).getBytes());
            }
            sections.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }

        List<Carriage> carriages = new ArrayList<>();
        List<Integer> spacing = new ArrayList<>();
        Vec3 prevTail = null;

        for (List<PhysicsBogeyBlockEntity> section : sections.values()) {
            PhysicsBogeyBlockEntity firstBogey = section.get(0);
            PhysicsBogeyBlockEntity lastBogey = section.get(section.size() - 1);

            TravellingPoint f1 = copyPoint(firstBogey.getAxle(true).getTrackPoint());
            TravellingPoint f2 = copyPoint(firstBogey.getAxle(false).getTrackPoint());
            TravellingPoint b1 = copyPoint(lastBogey.getAxle(true).getTrackPoint());
            TravellingPoint b2 = copyPoint(lastBogey.getAxle(false).getTrackPoint());

            if (f1 == null || f2 == null || b1 == null || b2 == null) {
                HexieTrain.LOGGER.warn("[HexieTrain][bind] 跳过一节：轨道点缺失");
                continue;
            }

            CarriageBogey frontBogey = new CarriageBogey(
                    AllBlocks.SMALL_BOGEY.get(), false, bogeyData, f1, f2);
            CarriageBogey backBogey = new CarriageBogey(
                    AllBlocks.SMALL_BOGEY.get(), false, bogeyData, b1, b2);
            // 车厢的 bogeySpacing 必须是正数：Create 的 Navigation.search 用它算车长，
            // 为 0 会让距离计算退化，findPathTo 直接返回 null（实测）。
            // 单转向架结构下取该转向架两根轴之间的真实间距。
            int bogeyGap = 2;
            Vec3 axleA = f1.getPosition(graph);
            Vec3 axleB = f2.getPosition(graph);
            if (axleA != null && axleB != null) {
                int measured = (int) Math.round(axleA.distanceTo(axleB));
                bogeyGap = Math.max(1, Math.min(32, measured));
            }
            Carriage carriage = new Carriage(frontBogey, backBogey, bogeyGap);

            // 列车长：影子车没有实体，标志设一次即可（updateConductors 会提前返回）
            carriage.presentConductors.replace(v -> Boolean.TRUE);

            // 维度 + 真实锚点（电力学等模组要读）
            try {
                var dce = carriage.getDimensional(level);
                Vec3 head = f1.getPosition(graph);
                Vec3 tail = b2.getPosition(graph);
                if (head != null && tail != null) {
                    dce.rotationAnchors = Couple.create(head, tail);
                }
            } catch (Throwable ignored) {
            }

            if (net.neoforged.fml.ModList.get().isLoaded("electroenergetics")) {
                ElectricCompat.attach(level, dispatcherPos, carriage);
            }

            Vec3 headPos = f1.getPosition(graph);
            Vec3 tailPos = b2.getPosition(graph);
            if (prevTail != null && headPos != null) {
                // 夹到 0~32 格：异常大的间距会让 Create 把车厢甩飞
                int gap = (int) Math.round(prevTail.distanceTo(headPos));
                spacing.add(Math.max(0, Math.min(32, gap)));
            }
            prevTail = tailPos;

            carriages.add(carriage);
        }

        if (carriages.isEmpty()) {
            return Result.fail("无法从物理结构构造车厢");
        }
        HexieTrain.LOGGER.info("[HexieTrain][bind] 5/6 构造了 {} 节车厢（来自 {} 个转向架）",
                carriages.size(), group.bogeys.size());
        // ⑤ 建车
        UUID trainId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Train train;
        try {
            train = new Train(trainId, owner, graph,
                    carriages, spacing, true, 0);
        } catch (Throwable t) {
            return Result.fail("建车失败：" + t.getClass().getSimpleName());
        }

        // ⑥ 时刻表
        if (scheduleStack != null && !scheduleStack.isEmpty()) {
            try {
                Schedule schedule = ScheduleItem.getSchedule(level.registryAccess(), scheduleStack);
                if (schedule != null) {
                    train.runtime.setSchedule(schedule, false);
                    train.runtime.paused = false;
                    HexieTrain.LOGGER.info("[HexieTrain][bind] 时刻表已装入（已放行）");
                }
            } catch (Throwable t) {
                return Result.fail("读取时刻表失败：" + t.getClass().getSimpleName());
            }
        }

        // 诊断开关：true = 不注册进 RAILWAYS（用于二分定位崩溃源）
        final boolean skipRegister = false;
        if (skipRegister) {
            HexieTrain.LOGGER.warn("[HexieTrain][bind] 诊断模式：Train {} 已构造但【不注册】进 RAILWAYS", trainId);
        } else {
            Create.RAILWAYS.addTrain(train);
            // 拥有者 = 放置方块的玩家
            if (ownerId != null) {
                train.owner = ownerId;
            }

            OUR_TRAINS.add(trainId);
            if (subLevelId != null) {
                SUBLEVEL_TRAINS.put(subLevelId, trainId);
            }
            // 一列车只允许一个主调度器输出传动
            TRAIN_DISPATCHERS.put(trainId, dispatcherPos.immutable());
            HexieTrain.LOGGER.info("[HexieTrain][bind] 6/6 Train 建好 {}, 注册进 RAILWAYS", trainId);

            // ── 寻路自检：把 findPathTo 的输入和输出一次性打出来 ──
            try {
                Carriage c0 = train.carriages.get(0);
                HexieTrain.LOGGER.info(
                        "[HexieTrain][path] 车厢数={} bogeySpacing={} 车厢间距数组={} 双转向架={}",
                        train.carriages.size(), c0.bogeySpacing, train.carriageSpacing.size(),
                        c0.isOnTwoBogeys());

                TravellingPoint lp0 = c0.getLeadingPoint();
                HexieTrain.LOGGER.info(
                        "[HexieTrain][path] 车头点 edge={} pos={} node1={} node2={}",
                        lp0.edge != null, String.format("%.2f", lp0.position),
                        lp0.node1 == null ? "null" : lp0.node1.getLocation(),
                        lp0.node2 == null ? "null" : lp0.node2.getLocation());

                var stations0 = train.graph.getPoints(
                        com.simibubi.create.content.trains.graph.EdgePointType.STATION);
                for (GlobalStation st : stations0) {
                    HexieTrain.LOGGER.info(
                            "[HexieTrain][path] 车站 {} 边={}->{} 边上位置={}",
                            st.name, st.edgeLocation.getFirst(), st.edgeLocation.getSecond(),
                            String.format("%.2f", st.position));
                    // 车站记录的两个节点是否还存在于当前轨道图里
                    boolean hasA = train.graph.getNodes().contains(st.edgeLocation.getFirst());
                    boolean hasB = train.graph.getNodes().contains(st.edgeLocation.getSecond());
                    HexieTrain.LOGGER.info(
                            "[HexieTrain][path] 车站边节点存在? first={} second={}  全图节点数={}",
                            hasA, hasB, train.graph.getNodes().size());

                    // 车站点是否真的挂在这条边的 EdgeData 上（搜索靠它识别目的地）
                    var nA = train.graph.locateNode(st.edgeLocation.getFirst());
                    var nB = train.graph.locateNode(st.edgeLocation.getSecond());
                    var edgeAB = (nA == null || nB == null) ? null
                            : train.graph.getConnectionsFrom(nA).get(nB);
                    boolean attached = edgeAB != null
                            && edgeAB.getEdgeData().getPoints().contains(st);
                    HexieTrain.LOGGER.info(
                            "[HexieTrain][path] 车站边存在={} 点已挂载={} 边上点数={}",
                            edgeAB != null, attached,
                            edgeAB == null ? -1 : edgeAB.getEdgeData().getPoints().size());

                    if (edgeAB != null && !attached) {
                        // 用 Create 自己的 API 补挂，和放置车站方块时的行为一致
                        edgeAB.getEdgeData().addPoint(train.graph, st);
                        HexieTrain.LOGGER.info("[HexieTrain][path] 车站点未挂载，已补挂到边上");
                    }
                }

                var list = new java.util.ArrayList<GlobalStation>(stations0);
                var found = train.navigation.findPathTo(list, Double.MAX_VALUE);

                HexieTrain.LOGGER.info("[HexieTrain][path] findPathTo 结果={}",
                        found == null ? "null（找不到路）" : "找到，终点=" + found.destination.name);

                // 用 Create 原生的列车状态机制播报给车主（橙色）
            } catch (Throwable t) {
                HexieTrain.LOGGER.warn("[HexieTrain][path] 自检异常: {}", t.toString());
            }

            // 诊断快照
            debugTrain = train;
            debugId = trainId;
            debugMapHash = System.identityHashCode(Create.RAILWAYS.trains);
            debugTicks = 0;
            HexieTrain.LOGGER.info("[HexieTrain][dbg] 注册后 containsKey={} invalid={} mapHash={}",
                    Create.RAILWAYS.trains.containsKey(trainId), train.invalid, debugMapHash);
        }
        HexieTrain.LOGGER.info("[HexieTrain][bind] 完成");
        return Result.success(trainId, "已注册为列车（" + group.bogeys.size() + " 个转向架）");
    }

    /** 复制一个轨道点（含所在的边与位置），与源对象完全解耦。 */
    private static TravellingPoint copyPoint(TravellingPoint src) {
        TrackNode n1 = src.node1;
        TrackNode n2 = src.node2;
        return new TravellingPoint(n1, n2, src.edge, src.position, src.upsideDown);
    }

    /** 按位置取物理编组（手动模式驱动用）。 */
    public static PhysicsBogeyGroup groupAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            PhysicsBogeyBlockEntity bogey = findBogey(level, pos);
            if (bogey == null) return null;
            return PhysicsBogeyGroup.createAndAssign(bogey);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 在调度台周围找第一个物理转向架。 */
    private static PhysicsBogeyBlockEntity findBogey(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_H; dx <= SCAN_H; dx++) {
            for (int dy = -SCAN_V; dy <= SCAN_V; dy++) {
                for (int dz = -SCAN_H; dz <= SCAN_H; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof PhysicsBogeyBlockEntity bogey) {
                        return bogey;
                    }
                }
            }
        }
        return null;
    }
}