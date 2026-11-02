# 0.3 实施清单 + 遗漏盘点

> 依据：`RULES.md`（物理体权威 / 中转站非输出者）、`docs/NAV-conditions.md`、Create 源码取证。

---

# 第一部分：必须执行的清单

## A. 物理体 → Create 的位移同步（核心）

| # | 要做的事 | 具体 API / 方法 | 备注 |
|---|---|---|---|
| A1 | 每 tick 用物理体真实位移写回列车速度 | `train.speed = measuredVelocity().dot(tangent) / 20.0` | `measuredVelocity()` 已在 BE 里常驻 |
| A2 | 解决 `Navigation.tick` 覆盖 speed | 写回时机必须在 `Navigation.tick()` **之后**（`ServerTickEvent.Post` 或 `Train.tick` 尾部 mixin） | 否则被 `approachTargetSpeed` 覆盖 |
| A3 | 处理 `tickPassiveSlowdown` 的衰减 | 同上，位置放在衰减之后 | 否则每 tick 被拉回 0 |
| A4 | 倒车方向 | `speed` 为负时 `Carriage.travel` 内部 `iterateFromBack = distance < 0` 自动处理 | 无需额外代码 |

## B. 到站流程接管（四步，顺序不能错）

| # | 要做的事 | 具体 API | 漏了会怎样 |
|---|---|---|---|
| B1 | 车站登记 | `station.reserveFor(train)` | `getPresentTrain()` 为 null → 邮件/乘客/界面全失效 |
| B2 | 状态推到 IN_TRANSIT | `train.runtime.state = ScheduleRuntime.State.IN_TRANSIT` | `destinationReached()` 直接 return |
| B3 | 触发到达 | `train.arriveAt(station)` | `setCurrentStation` + `destinationReached` + `runMailTransfer` |
| B4 | 清理导航残留 | `navigation.destination = null; currentPath.clear()` | `Navigation.tick` 按空路径把速度归零 |

**B1~B4 必须在同一 tick 内完成**，否则 `runtime.tick` 看到 `IN_TRANSIT && destination == null` 会卡死。

## C. 岔路口选道（此前完全没考虑）

| # | 事实 | 影响 |
|---|---|---|
| C1 | `Carriage.travel` 的选道器来自 `train.navigation::control` | 我们的列车也走这条路 |
| C2 | `control(mp)`：`destination == null` 时返回 `mp.steer(train.manualSteer, up)` | 无导航时按 `manualSteer` 手动转向 |
| C3 | `destination != null` 时按 `currentPath` 逐段匹配 | 有导航才走正确进路 |
| C4 | `navigateOptions` 里 `path.isEmpty()` → **`options.get(0)`**（随便取第一条） | 路径耗尽后会乱走 |

**结论：要么让 `destination` 非空并维护 `currentPath`，要么自己接管选道。**

## D. 货物桥接

| # | 要做的事 | 具体 API |
|---|---|---|
| D1 | 取物理体世界视图 | `subLevel.getPlot().getEmbeddedLevelAccessor()` |
| D2 | 取遍历范围 | `plot.getBoundingBox()`（`BoundingBox3ic`） |
| D3 | 遍历方块实体 | `accessor.getBlockEntity(pos)` |
| D4 | 过滤物品容器 | `be.getCapability(Capabilities.ItemHandler.BLOCK, null) instanceof IItemHandlerModifiable` |
| D5 | 注入货舱 | `carriage.storage.initialize()` → 逐个 `attachExternal(h)` |
| D6 | 箱子变化时重建 | `carriage.storage = new TrainCargoManager()` 后重跑 D5 |

**注意**：`initialize()` 会把 `itemsBuilder` 置 null，之后不能再 `addBlock`；`externalHandlers` 不进存档，必须每次加载时重扫。

## E. 时刻表下发

| # | 做法 | 依据 |
|---|---|---|
| E1 | "改去哪" = 造新时刻表 + `setSchedule(schedule, true)` | CTS 项目做法，比改 entries 干净 |
| E2 | "恢复运行" = `runtime.paused = false` | Create 自己会重新调 `startCurrentInstruction` |
| E3 | "暂停" = `navigation.cancelNavigation(); runtime.paused = true` | 保留时刻表 |

---

# 第二部分：遗漏盘点（还没解决/没验证的）

## 一、硬门槛类

| # | 项 | 状态 |
|---|---|---|
| M1 | 列车员标志 `presentConductors` | 已确认是硬门槛（`DestinationInstruction.start` 第一句），**但还没修** |
| M2 | `Navigation.search` 的 `validTypes` 是否为空 | 未验证；来自 `leadingBogey().type.getValidPathfindingTypes(style)` |
| M3 | 轨道材质 `TrackType` 与转向架是否兼容 | 未验证（C7） |

## 二、机制类

| # | 项 | 影响 |
|---|---|---|
| M4 | **信号占用**：`reservedSignalBlocks` 由 Navigation 维护 | 绕过导航 → 不占信号 → 可能撞车/闯红灯 |
| M5 | **道岔选路**（见 C 节） | 无 `currentPath` 时随机选第一条 |
| M6 | `train.status`（HUD 提示） | 我们绕过后不会有 `missingConductor` 之类提示 |
| M7 | `GlobalTrainDisplayData`（站牌离站预告） | 依赖 `navigation.distanceToDestination`，不自更新 |
| M8 | `Navigation.ticksInTransit` / 到达预测 | 依赖导航，绕过后不更新 |

## 三、数据/存档类

| # | 项 | 影响 |
|---|---|---|
| M9 | `attachExternal` 的箱子不随 Create 存档 | 重载后要重扫 |
| M10 | `presentConductors` 存 NBT（`FrontConductor`/`BackConductor`） | 设一次即可，重启保留 |
| M11 | 我们自己的 `TrainCargoManager` 重建时机 | 绑定时 + 箱子变化时 |

## 四、代码卫生（该清理的）

| # | 项 | 说明 |
|---|---|---|
| M12 | `UnloadExperiment` | 实验代码，结论已出（不会卸载），**可以删** |
| M13 | `suspended` 字段 | 永远 false，UI 还显示，属于 `OffscreenProxy` 遗骸 |
| M14 | 第 72 行孤立注释「出加载区代理…虚空列车」 | 遗骸，删 |
| M15 | `SubLevelSuspend.followTrain()` | 死代码（无调用点） |
| M16 | `SubLevelSuspend.resume/suspend`（票据） | 已证明不防卸载，只防删除；留着或删掉都行 |

## 五、尚未验证的假设

| # | 假设 | 怎么验证 |
|---|---|---|
| M17 | 写 `train.speed` 后虚拟点真的按物理位移前进 | 打 `leadingPoint.position` 对比 `measuredVelocity` |
| M18 | `frontSignalListener` 在 `destination` 为 null 时**不会**触发 | 源码已确认（`navigation.destination != station → return false`），所以**必须让 destination 非空**，否则到站永远不触发 |
| M19 | 多节车厢 `totalStress` 在物理体存在时是否仍需要 | 未验证 |
| M20 | 物理体在弯道/坡道上测速是否仍准确 | 已在直线验证 3.34 m/s，弯道未测 |

---

# 第三部分：建议执行顺序

1. **M12~M16 清理**（零风险，去掉噪声）
2. **M1 列车员标志**（硬门槛，必须先过）
3. **A1~A3 位移同步**（核心闭环）
4. **M17 验证**（虚拟点是否跟着走）
5. **B1~B4 到站接管**
6. **C 选道**（可能需要自己维护 `currentPath`）
7. **D 货物桥接**
8. **M4/M5 信号与道岔**
---

# 第四部分：第二轮排查结果（空壳与死代码）

## 一、UI 上的"死显示"

| # | 位置 | 现状 | 后果 |
|---|---|---|---|
| S1 | `stageOrdinal()`（BE:451） | `return 0;  // 旧的五段制动曲线已删除` | UI 阶段永远 0 |
| S2 | `setDispatcherState(...)`（BE:431 唯一调用） | 只写死 `IDLE` | UI 状态永远"待命" |
| S3 | `distanceToTarget()` → `TrainStateReader.read()` | 只有 `nav.destination != null` 才有距离；无导航 → 0 | UI 距离永远 0 |
| S4 | `suspended` 字段 | 只在 `read()` 里写 false | UI 的 SUSPENDED 状态是死的 |

## 二、通道类的空壳

| # | 东西 | 状态 |
|---|---|---|
| S5 | `SignalChannel.encodeSteer(double)` | **从未被调用** |
| S6 | `setOutput(SignalChannel.LEFT/RIGHT, 0)` | 唯一写入点是常量 0 |
| S7 | `train.manualSteer` | **从未赋值**（Create 里无导航时靠它选道） |
| S8 | `SignalChannel.reverseStage(...)` | 需确认是否被调用（制动多段逻辑） |

**→ 岔道选道这条链：通道完整、编码器完整、无线发送完整，但"值"永远是 0。**

## 三、遗漏的功能闭环（结构性）

| # | 缺口 | 依据 |
|---|---|---|
| G1 | `train.speed` 在自动模式下无回写 | 全项目只有手动模式写 |
| G2 | `station.reserveFor(train)` 未调用 | `getPresentTrain()` 需要它 |
| G3 | `runtime.state = IN_TRANSIT` 未管理 | `destinationReached()` 的前置 |
| G4 | `carriage.storage.attachExternal(...)` 未接 | 货舱是空的 |
| G5 | `presentConductors` 未设 true（按指示已撤销） | `DestinationInstruction.start` 第一句 |
| G6 | `reservedSignalBlocks` 我们不维护 | 绕过导航 → 不占信号 |
| G7 | `currentPath` 我们不维护 | 岔路口 `navigateOptions` 会 `options.get(0)` 乱选 |

## 四、生命周期与资源

| # | 项 | 说明 |
|---|---|---|
| L1 | `ACTIVE` 静态集合 | `initialize()` 加入、`remove()` 移除；**若子结构卸载时 `remove()` 不被调用 → 静态引用泄漏 + 继续 tick** |
| L2 | `UnloadExperiment.OBS` 静态 Map | 无清理，随调度器数量增长 |
| L3 | `[EXP-UNLOAD]` 日志 | 每 20 tick 一条，长期运行会刷屏 |
| L4 | `SubLevelSuspend.LAST_ANCHOR` | `followTrain` 的遗留缓存，死代码但静态 Map 仍在 |

## 五、待清理清单（零风险）

1. `SubLevelSuspend.followTrain()` + `LAST_ANCHOR` + `clearAnchorCache()`
2. 孤立注释「出加载区代理…虚空列车」（BE:72）
3. `suspended` 字段 + `DATA_SUSPENDED` + UI 引用
4. `stageOrdinal()` + `DATA_STAGE`
5. `UnloadExperiment`（结论已出：不卸载）
6. `encodeSteer`（若确定不用手动转向则删；若要用则接上 `manualSteer`）

## 六、排查中确认的两个"看着像 bug 其实不是"

| 项 | 结论 |
|---|---|
| `SignalChannel.brakeStrength` 的平方根补偿 | 正确：Simurail 内部会对刹车强度平方 |
| `Carriage.travel` 里 `distance < 0` 自动从车尾开始迭代 | 正确：倒车无需特殊处理 |