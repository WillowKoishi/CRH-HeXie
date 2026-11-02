# 加载范围外的模拟能力：航空学 vs 机械动力

取证来源：
  sable-neoforge-1.21.1-2.0.5.jar（javap）
  create-1.21.1-6.0.10.jar（源码提取版 + javap）

---

## 一、航空学（Sable）：**不允许**超加载范围计算，只做 20 格预测

### 1. 物理区块票据

    dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager
        public static final double MAX_PREDICTION_DISTANCE = 20.0d;
        public static final TicketType<UUID> SUB_LEVEL_LOADED_TICKET_TYPE;   // "sable_sub_level_loaded"
        public void    addTicketForSection(ServerLevel, SectionPos);
        public static boolean isChunkLoadedEnough(ServerLevel, int, int);
        public boolean wouldBeLoaded(Level, ArbitraryPhysicsObject);

结论：Sable 会为物理体**提前 20 格**加区块票据，仅此而已。

### 2. 区块没加载会发生什么

`PhysicsChunkTicketManager.update(...)` 里：

    if (!isChunkLoadedEnough(level, cx, cz) || !tracked.contains(pos)) {
        object.onUnloaded(holdingMap, new ChunkPos(cx, cz));   // ← 移出物理步进
        ...
    }

`ArbitraryPhysicsObject.onUnloaded(SubLevelHoldingChunkMap, ChunkPos)` 把子结构送进

    dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap
        Object2ObjectMap<UUID, HoldingSubLevel> allHoldingSubLevels;

即：**子结构变成 HoldingSubLevel（存档态），不再参与物理模拟。**

### 3. 物理步进是全局的还是分区块的

`SubLevelPhysicsSystem` 每层一个（`get(Level)`），但它持有的
`Collection<ArbitraryPhysicsObject> arbitraryObjects` 只包含**区块已加载**的对象。

结论：**Sable 没有任何"离屏物理"能力**。物理体不可能在未加载区块里继续运动。

---

## 二、机械动力：**允许**，因为列车是纯数据

### 1. 每 tick 全局驱动

    CommonEvents（服务器 tick 事件）
        Create.RAILWAYS.tick(level);
            ↓
    GlobalRailwayManager.tick(Level level):
        if (level.dimension() != Level.OVERWORLD) return;      // ← 只在主世界
        for (TrackGraph graph : trackNetworks.values()) graph.tickPoints(true);
        tickTrains(level);
            ↓
    tickTrains(Level):
        for (Train train : waitingTrains) train.earlyTick(level);
        for (Train train : movingTrains)  train.earlyTick(level);
        for (Train train : waitingTrains) train.tick(level);
        for (Train train : movingTrains)  train.tick(level);

**没有任何区块/实体存在性检查。** 列车存活在 `Map<UUID, Train> trains` 里，
由服务器 tick 事件直接驱动。

### 2. 位置是图数据，不是世界内容

    Train.tick(level):
        double distance = speed;
        carriage.travel(level, graph, distance + totalStress, ...)

`Carriage.travel` 需要的是 `TrackGraph` + `TravellingPoint`，
两者都是**纯数据结构**，与区块加载无关。

### 3. 唯一的区块相关节流：有乘客时

    Carriage.updateContraptionAnchors:
        for (Entity e : entity.getPassengers())
            if (!(e instanceof Player)) continue;
            ...
            if (CarriageEntityHandler.isActiveChunk(level, BlockPos.containing(lookahead))) break;
            train.carriageWaitingForChunks = id;
            return;

    Train.tick:
        if (carriageWaitingForChunks != -1) distance = 0;     // 该 tick 不推进

只有在**玩家坐在车上**且前方区块未激活时才暂停，与"离开加载范围"无关。

---

## 三、对比结论

| | 机械动力列车 | Sable 物理体 |
|---|---|---|
| 本质 | 纯数据（图上的点） | 世界内容（区块里的方块/实体） |
| 驱动 | 服务器 tick 事件，全局 | 物理步进，仅限已加载区块 |
| 离开加载范围 | 照常运行 | 变成 HoldingSubLevel，冻结 |
| 提前加载 | 不需要 | 20 格预测票据 |
| 依赖 | `TrackGraph` | 区块必须加载 |

**这就是影子列车存在的根本原因**：
物理体不可能在未加载区域继续运动，而时刻表/信号/车站系统需要"列车一直在跑"。
影子列车不是物理体的竞争对手，它是**未加载区域的降级模型**。

---

## 四、三种可选架构

### A. 物理只在附近，远处由数据模型接替（推荐）

- 近处：物理体权威（符合〇之二），测出位移喂给 Create
- 远处：物理体冻结进 HoldingSubLevel，改由数学模型按 `train.speed` 推进
- 交接：进出加载范围时对齐位置与速度

### B. 强行让物理体一直在场

- 沿途持续加区块票据（票价：内存 + tick 开销随距离线性增长）
- Sable 自身票据只管 20 格，需要自己写常加载逻辑
- 长线路上不可行

### C. 接受物理体冻结

- 离开加载范围列车直接停住
- 与"时刻表自动运行"目标冲突
---

## 五、卸载态的形态，以及能否 TP（补充取证）

### 1. 出了加载范围之后，物理体去了哪里

    PhysicsChunkTicketManager.update(level, container, physicsSystem, pipeline, dt):
        if (!isChunkLoadedEnough(level, cx, cz) || !tracked.contains(pos)) {
            object.onUnloaded(holdingMap, new ChunkPos(cx, cz));   // 移出物理步进
            ...
        }

    ArbitraryPhysicsObject.onUnloaded(SubLevelHoldingChunkMap, ChunkPos)
        → SubLevelHoldingChunkMap.moveToUnloaded(ServerSubLevel, ChunkPos)
            → 生成 HoldingSubLevel
            → allHoldingSubLevels.put(uuid, holdingSubLevel)
            → SubLevelPhysicsSystem.arbitraryObjects 里移除

`HoldingSubLevel` 的内容：

    public final class HoldingSubLevel {
        private final SubLevelData data;
        private GlobalSavedSubLevelPointer pointer;
        public SubLevelData data();
    }

    public final class SubLevelData {
        private final UUID uuid;
        private final BoundingBox3d bounds;
        private final Pose3d pose;
        private final List<UUID> relations;
        private final CompoundTag fullTag;
        private ChunkPos originLoadedChunk;
        public Pose3d pose();
        public CompoundTag fullTag();          // ← 可写！
        public void setOriginLoadedChunk(ChunkPos);
    }

**结论：物理体变成了「存档数据」，没有刚体、没有 handle、不在物理系统里。**

### 2. 能不能对它 TP

**直接 TP：不行。** `RigidBodyHandle.of(subLevel)` 需要活的 `ServerSubLevel`，
`teleport(Vector3dc, Quaterniondc)` 无从调用。

**间接 TP：可以。** 恢复路径是公开的：

    SubLevelHoldingChunkMap
        public HoldingSubLevel getHoldingSubLevel(UUID);
        public void loadHoldingSubLevel(HoldingSubLevel);

    loadHoldingSubLevel(holding) →
        SubLevelSerializer.fullyLoad(level, holding.data())
            → SableNBTUtils.readPose3d(tag)      // 从 NBT 的 "pose" 键读姿态
            → container.allocateSubLevel(uuid, x, z, pose)
            → loaded.logicalPose().set(pose)
        → allHoldingSubLevels.remove(uuid)

也就是说：**姿态是从 `SubLevelData.fullTag()` 里的 `"pose"` 标签读出来的，
而 `fullTag()` 返回的是可写的 `CompoundTag`**，并且有现成的写入口：

    SableNBTUtils.writePose3d(Pose3d) → CompoundTag

于是"卸载态传送"等价于：

    HoldingSubLevel h = map.getHoldingSubLevel(uuid);
    SubLevelData d = h.data();
    d.fullTag().put("pose", SableNBTUtils.writePose3d(目标姿态));
    d.pose().set(目标姿态);                              // 同步字段
    d.setOriginLoadedChunk(new ChunkPos(目标x >> 4, 目标z >> 4));
    map.loadHoldingSubLevel(h);

### 3. 三个风险

| 风险 | 说明 |
|---|---|
| 目标区块必须已加载 | 否则刚复活就被 `isChunkLoadedEnough` 再踢回 HoldingSubLevel |
| 速度不会被保留 | `SubLevelData` 里**没有任何速度字段**，复活后是静止的，速度必须由外部注入 |
| 失败会静默 | 恢复失败时只打一行 `Failed to load holding sub-level ... This is a problem.` |

### 4. 对方案的结论

- "进场把物理体传送过来"**技术上成立**，但路径是 **改 NBT pose → loadHoldingSubLevel**，
  不是 `RigidBodyHandle.teleport`
- 因为卸载态根本没有速度，**速度连续性的责任完全落在我们自己的「点」上**
- 这与「点是本体、物理体是化身」的设计完全吻合