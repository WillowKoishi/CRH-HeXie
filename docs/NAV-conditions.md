# 影响 Create 列车导航的全部条件（源码取证）

来源：`create-1.21.1-6.0.10.jar` 源码提取版
- `Navigation.findPathTo`（442-517）
- `Navigation.search`（551-~800）
- `Navigation.startNavigation`（386-439）
- `DestinationInstruction.start`（113 行）
- `Carriage.updateConductors`（247-256）

---

## A. 时刻表层（DestinationInstruction.start）

| # | 条件 | 不满足的后果 |
|---|---|---|
| A1 | `train.hasForwardConductor() \|\| train.hasBackwardConductor()` | `missingConductor()` + 冷却，**直接 return null，findPathTo 根本不调用** |
| A2 | `getFilter()` 非空 | 空过滤器 → 返回 null |
| A3 | 至少一个 `GlobalStation.name.matches(regex)` | `failedNavigationNoTarget()` |
| A4 | `findPathTo(...) != null` | `failedNavigation()` |

---

## B. 寻路入口（Navigation.findPathTo）

| # | 条件 | 后果 |
|---|---|---|
| B1 | `train.graph != null` | 返回 null |
| B2 | 已导航时不反向：`destination != null && destinationBehindTrain == forward` 时跳过该方向 | 该方向不出结果 |
| B3 | `initialPoint.edge != null` | search 内 initialEdge 为 null → return |
| B4 | `canDriveForward = hasForwardConductor() \|\| runtime.paused` | 前向结果被丢弃 |
| B5 | `canDriveBackward = (doubleEnded && hasBackwardConductor()) \|\| runtime.paused` | 后向结果被丢弃 |
| B6 | 两个方向都不可用 | 返回 null |

---

## C. 搜索核心（Navigation.search）

| # | 条件 | 后果 |
|---|---|---|
| **C1** | **`validTypes` 非空**<br>`carriage.leadingBogey().type.getValidPathfindingTypes(style)`<br>与 `trailingBogey()` 取交集 | **直接 return，一个都不搜** ← 最隐蔽的坑 |
| C2 | `initialEdge != null`（`graph.getConnectionsFrom(node1).get(node2)`） | 返回 |
| C3 | 初始边上的 `TrackEdgePoint.canNavigateVia(node2)` | 返回 |
| C4 | `distance <= maxDistance`（本调用传 `Double.MAX_VALUE`） | 该分支被丢弃 |
| C5 | 每条边只访问一次（`visited`） | 环线只走一遍 |
| C6 | `edge.canTravelTo(nextEdge)`（道岔/连通性） | 该分支不扩展 |
| **C7** | **`validTypes.contains(edge.getTrackMaterial().trackType)`** | **该边被跳过** ← 车型与轨道材质不兼容则永远走不到 |
| C8 | 车站 `canApproachFrom(node)` | 不能作为终点 |
| C9 | `newDistance + newPenalty <= maxCost`（本次为 `Double.MAX_VALUE`，不限制） | 该分支被剪 |
| C10 | 信号强制红 / 占用 → **只加罚分**，不阻断 | 仍可规划 |

---

## D. 启动导航（Navigation.startNavigation）

| # | 条件 | 后果 |
|---|---|---|
| D1 | `path != null` | 返回 -1（`TBD`） |
| D2 | `runtime.paused == true` 时跳过列车员检查 | — |
| D3 | 目标在后方 → 必须有 `hasBackwardConductor()` | `missingCorrectConductor()` → -1 |
| D4 | 目标在前方 → 必须有 `hasForwardConductor()` | 同上 → -1 |

---

## E. 运行中（Navigation.tick）

| # | 条件 | 后果 |
|---|---|---|
| E1 | `currentPath` 非空 | 无动作 |
| E2 | 信号未放行（`waitingForSignal != null`） | 停车等待 |
| E3 | `targetDistance < -10`（跑过头） | `cancelNavigation()` |
| E4 | 图更新导致路径失效 | `removeBrokenPathEntries()` 截断路径 |
| E5 | `destinationBehindTrain` 决定行驶方向 | 方向错则反向开 |

---

## 按"最容易出问题"排序（针对本项目）

1. **C1 `validTypes` 为空** —— 转向架类型没配好，一个都搜不到
2. **C7 轨道材质不匹配** —— 船用转向架走不了标准轨
3. **A1 缺列车员** —— 已确认是我们之前的根因，已修
4. **B3/B2 车头所在边为空** —— 影子车刚绑定时 `TravellingPoint.edge` 可能为 null
5. **A3 站名不匹配** —— glob 语法/名字不一致