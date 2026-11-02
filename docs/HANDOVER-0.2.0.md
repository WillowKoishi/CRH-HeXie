# 0.2.0 开发交接（上下文压缩版）

> 本文件用于压缩历史上下文。新阶段开工前只读这一份即可。
> 强制工作规则见 [RULES.md](../RULES.md)，本文不重复。

---

## 一、环境与位置

| 项 | 值 |
|---|---|
| 工程目录 | `H:\蓝色肥鱼小家\hexie-physics` |
| 实例目录 | `H:\柳树服beta2\.minecraft\versions\Willow11TH_Aero_beta2(无地图)` |
| 安装目标 | `<实例>\mods\[和谐：模拟自动铁路]Hexie_AutoSimulateTrain<版本>.jar` |
| 构建 | `gradle build --offline`（`build.bat` 为纯 ASCII 包装） |
| 产物 | `历史版本/[和谐：模拟自动铁路]Hexie_AutoSimulateTrain<版本>.jar` |
| JDK | `C:\Users\PC\AppData\Roaming\.minecraft\runtime\java-runtime-delta`（21） |
| git 身份 | `Dasvidaniya <1780566261@qq.com>`（已写入仓库 config） |

**铁律**：游戏运行中**不得**覆盖 mods 里的 jar（会导致 `NoClassDefFoundError`）。
安装前先查 java 进程，或确认游戏已退出。

---

## 二、平台与依赖

MC 1.21.1 / NeoForge 21.1.248 / Create 6.0.10-281 / Sable 2.0.5 / Simurail 0.1.0 /
create-aeronautics-bundled 1.3.2（内含 `simulated`）/ 可选 electroenergetics、powergrid。

编译采用**离线 classpath**：直接指向整合包 `libraries/`、补丁客户端 jar、`mods/` 里的目标模组，
以及 `libs/` 中从 Create 的 `META-INF/jarjar/` 解出的 4 个依赖（flywheel / ponder / Registrate / sable-companion）。
详见 `BUILD-NOTES.md`。

---

## 三、架构结论（已实机验证）

| 结论 | 内容 |
|---|---|
| 调度大脑 | 复用 Create `Train`：时刻表、寻路、信号、车站全部原生 |
| 物理身体 | Sable 子结构（Simurail 转向架编组），**只作表现** |
| 位置权威 | Create 的 `TravellingPoint`（全局 tick，跨视距可用） |
| 速度权威 | 物理体在场时以物理体为准；不在场时由模型推算 |
| 车厢划分 | 按 `PhysicsBogeyGroup` 的 sub-level 分组，一节结构 = 一节车厢；多节自动展开 |
| 轨道点 | **必须深拷贝** `TravellingPoint`，与 Simurail 物理体完全解耦 |
| 影子车厢 | 不创建 `CarriageContraptionEntity`，靠 mixin 阻止 `updateContraptionAnchors` |
| 燃料 | 跳过 Create 烧燃料（`TrainFuelMixin`），否则无 contraption 会崩 |
| 存档 | `DimensionalCarriageWriteMixin` 跳过储物序列化，否则自动存档崩 |
| 加载 | 每辆物理列车持独立持久加载票据，不随视距卸载 |

---

## 四、方块：物理火车调度器

**传动语义 = 照抄 `simulated` 的定向齿轮换向器**

```
源侧             → 1（永远放行，否则方块自认动力源 → 凭空转速）
左侧红石         → +1
右侧红石         → -1
无红石 / 两侧同亮 → 0
没有源           → 0
```

- 基类 `DirectionalAxisKineticBlock`
- 状态：`facing` + `AXIS_ALONG_FIRST_COORDINATE` + `LEFT_POWERED` + `RIGHT_POWERED`
- **轴垂直于 FACING**：`X→(alongFirst?Y:Z)`、`Y→(alongFirst?X:Z)`、`Z→(alongFirst?X:Y)`
- 放置：`RotatedPillarKineticBlock.getPreferredAxis` 吸轴 + `getNearestLookingDirection`，
  垂直分支判 `horizontal.getAxis()==Axis.Z`，最后 `if (shift) facing = facing.getOpposite()`
- 红石变化：`getPoweredState → setBlock → detachKinetics → scheduleTick → tick → handleAdded`
- 扳手：`super.onWrenched` + `detachKinetics`；Shift+扳手拆除
- 模型：blockstate 必须按 `facing` **和** `axis_along_first` 双属性映射（144 条 when）

证据：`docs/evidence/simulated-DirectionalGearshiftBlockEntity-getRotationSpeedModifier.txt`、
`docs/evidence/create-DirectionalAxisKineticBlock-getRotationAxis.txt`

---

## 五、界面

- 时刻表槽 + 站名栏（滚轮遍历站点，右侧 `>>` 确认，点击后短暂显示 `✓`）
- 三行无线频率（每行 2 个频率槽）
- 三通道 15 格信号灯：刹车 / 左岔道 / 右岔道
- 调度状态（待命/行驶中/滑车/急刹）+ 当前速度（`m/s`，死区 0.05，与航空学速度传感器一致）
- 右上角徽标：未绑定 / 自动驾驶中 / 玩家操控中（可点击）
- 背包 + 快捷栏

**已删除**：零速起步、制动灵敏度、站名跑马灯长条、`RailPathfinder`、`TrainMessages`

---

## 六、模式语义（重要，别再混淆）

| 模式 | 含义 | 谁改它 |
|---|---|---|
| 玩家操控中 | 自动寻路暂停（`navigation.cancelNavigation()`），`driveToStation` 不运行 | 红石介入、右上角按钮 |
| 自动驾驶中 | 时刻表驱动 | 同上 |

**选站与模式无关**：选站只改"下一站是谁"（`runtime.currentEntry`），
条目顺序 A→B→C 永不改变；到站后 Create 正常推进游标（循环时刻表会回绕）。

---

## 七、0.1.0 遗留问题（0.2.0 候选）

| 优先 | 问题 | 线索 |
|---|---|---|
| 高 | 选站后列车到站会立刻推进游标导致看起来"改不回去" | `[arrive]` / `[entry]` 日志已加，需确认 `cyclic` 与到站时机 |
| 高 | 自动驾驶距离用**直线距离**近似，不是沿轨道测距 | `distanceToStation` / `isStationAhead` |
| 中 | 时刻表**内容**编辑无 UI 入口（写回链路 `applySchedule` 已具备，可写物品+列车） | `readSchedule()` / `applySchedule()` |
| 中 | 坡度、视距外动力学模型（`SpeedModel` / `TrainDynamics`）未接入主循环 | 纯数学模型已存在 |
| 中 | 多节编组未实机验证 | 车厢划分已支持 |
| 低 | 诊断日志（`[schedule]` `[drive]` `[arrive]` `[entry]`）需在稳定后清理 | — |
| 低 | 电力学仅登记电机，未接受电弓/功率 | `ElectricCompat` |

---

## 八、GitHub 状态

- 0.1.0 已推送：`https://github.com/WillowKoishi/CRH-HeXie` 分支 `Automation`，标签 `v0.1.0`
- **0.2.0 阶段暂停使用 GitHub**，只做本地提交
- 历史已重写为 `Dasvidaniya <1780566261@qq.com>`，GitHub 关联账号 `Van12345-Boy`

---

## 九、开工前自检

- [ ] 读 `RULES.md`
- [ ] 确认游戏已退出再安装 jar
- [ ] 任何照抄其它模组的地方，先在 `docs/evidence/` 留证
- [ ] 提交前确认 `git config user.email` 正确