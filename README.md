# CRH-HeXie · 物理火车调度器

> ⚠️ **开发前必读：[RULES.md](RULES.md)**
> 能读的不猜、不能读的问；参考任何模组必须先反编译取证（证据放 `docs/evidence/`）。


Minecraft **NeoForge** 附属模组：让 **Sable 物理子结构**当作真正的列车，
复用 **Create** 原生的时刻表、寻路、信号与车站系统。

> 设计原则：**原版的调度大脑 + 自己的物理身体**。
> 位置权威在 Create 的列车，速度权威在物理体，两者通过调度器方块联动。

当前版本：**0.1.0**（分支 `Automation`）

---

## 一、运行环境

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Create | 6.0.10-281 |
| Sable | 2.0.5 |
| Simurail | 0.1.0+mc1.21.1 |
| Create Aeronautics（含 bundled `simulated`） | 1.3.2 |
| Electro Energetics（可选，电力兼容） | 1.1.1 |
| PowerGrid（可选） | 0.6.2 |

---

## 二、已实现

### 方块：物理火车调度器

- **六向放置**：轴正对点击面；`facing` + `axis_along_first` 两个状态决定轴位，
  blockstate 按两属性全组合映射模型（照抄 `simulated` 的定向齿轮换向器）
- **可控传动**：本方块不是发电机，只是传动链上的可控分轴
  - 源侧永远放行（`getRotationSpeedModifier` 对 `getSourceFacing()` 返回 1）
  - 左侧红石 → `+1`（正传）
  - 右侧红石 → `-1`（反传）
  - 无红石 / 两侧同时通电 → `0`（断开）
  - 输出转速恒等于输入转速，无输入则无输出
- **扳手**：右键切换收红石的那一对垂直面；**Shift + 扳手**直接拆除
- **空手右键**打开界面（手持扳手时不弹界面）
- 破坏时逐槽掉落内容物

### 界面

- 时刻表槽 + 站名栏（**滚轮遍历站点**，右侧 `>>` 确认）
- 三行无线红石频率（每行两个频率槽）
- 三通道 15 格信号灯：刹车 / 左岔道 / 右岔道
- 调度状态（待命 / 行驶中 / 滑车 / 急刹）+ 当前速度（`m/s`，死区 0.05，与航空学速度传感器一致）
- 右上角徽标：未绑定 / 自动驾驶中 / 玩家操控中（可点击切换）
- 玩家背包 + 快捷栏

### 列车

- 绑定时按 `PhysicsBogeyGroup` 划分车厢（一节物理结构 = 一节车厢，多节自动展开）
- `TravellingPoint` 深拷贝，与 Simurail 物理体完全解耦
- 双转向架车厢构造，`bogeySpacing` / `carriageSpacing` 按物理锚点计算
- 跳过 Create 的烧燃料逻辑（`TrainFuelMixin`）
- 车厢无 contraption 时的存档安全处理（`DimensionalCarriageWriteMixin`）
- 每辆物理列车持有独立的持久加载票据，不会因视距被卸载

### 模式

| 模式 | 含义 |
|---|---|
| **自动驾驶中** | 时刻表驱动，调度器按当前条目开车 |
| **玩家操控中** | 自动寻路暂停（`navigation.cancelNavigation()`），红石控制传动方向 |

两种模式**只由红石介入和右上角按钮改变**；选站只调整"下一站是谁"，与模式无关。

---

## 三、构建

### 1. 需要 JDK 21

推荐直接用 Minecraft 启动器自带的运行时：

```
%APPDATA%\.minecraft\runtime\java-runtime-delta
```

### 2. 准备编译依赖

本项目采用**离线编译**：不打 NeoForm 反混淆流水线，直接把整合包里的 jar
当作 `compileOnly` classpath（运行时类名与编译期完全一致）。

需要准备：

| 位置 | 内容 |
|---|---|
| `libraries/` | 整合包的 `libraries/**`（MC 库 + NeoForge） |
| 客户端 jar | 整合包 `versions/<实例>/<实例>.jar`（已打补丁） |
| `mods/` | Create、Sable、Simurail、create-aeronautics-bundled、electroenergetics、powergrid |
| `libs/` | 见下方「必须自备的 libs」 |

### 3. 必须自备的 `libs/`

Create 把部分依赖打在 `META-INF/jarjar/` 里，**嵌套 jar 不在 classpath 上**，需要手动解出来：

```
libs/flywheel-neoforge-1.21.1-1.0.6.jar
libs/ponder-neoforge-1.0.82+mc1.21.1.jar
libs/Registrate-MC1.21-1.3.0+67.jar
libs/sable-companion-common-1.21.1-1.6.0.jar
```

> 前三个来自 Create 的 `META-INF/jarjar/*.jar`；最后一个来自 Sable。

### 4. 改 `build.gradle` 顶部的路径

```groovy
ext {
    mcDir    = "路径/.minecraft"
    instName = "你的实例名"
    instDir  = "${mcDir}/versions/${instName}"
    modsDir  = "${instDir}/mods"
}
```

### 5. 构建

```bat
gradle build --offline
```

产物：

```
build/libs/hexietrain-0.1.0+mc1.21.1.jar
历史版本/[和谐：模拟自动铁路]Hexie_AutoSimulateTrain0.1.0.jar   ← releaseJar 任务自动复制
```

把 `历史版本/` 里的 jar 丢进 `mods/` 即可测试。

> ⚠️ **不要在游戏运行时覆盖 mods 里的 jar**。NeoForge 的类加载器会缓存 jar 索引，
> 文件被替换后加载新类会抛 `NoClassDefFoundError`。必须关掉游戏再替换。

---

## 四、目录结构

```
src/main/java/willow/train/hexietrain/
├─ HexieTrain.java                    模组入口
├─ HexieRegistries.java               方块/物品/BE/菜单注册
├─ TrainDispatcherBlock.java          方块：放置、红石、扳手、动力学重算
├─ TrainDispatcherBlockEntity.java    主逻辑：绑定、驾驶、时刻表、无线通道
├─ TrainDispatcherContainer.java      槽位容器
├─ TrainDispatcherMenu.java           菜单与 ContainerData
├─ DispatcherTicker.java              服务器全局 tick 驱动（方块不能当大脑）
├─ DispatcherState.java               IDLE / RUNNING / COASTING / BRAKING
├─ TrainBinder.java                   Sable 转向架 → Create 列车
├─ SignalChannel.java                 三通道编码
├─ WirelessChannel.java               无线红石发射端
├─ ElectricCompat.java                电力学兼容
├─ SubLevelSuspend.java               子结构定位与持久加载票据
├─ TrainStateReader.java              列车状态读取
└─ mixin/
   ├─ CarriageAnchorMixin.java        阻止 Create 为影子车厢创建 contraption
   ├─ DimensionalCarriageWriteMixin.java  跳过储物序列化（无 contraption 时会崩）
   └─ TrainFuelMixin.java             跳过烧燃料逻辑

src/main/resources/
├─ META-INF/neoforge.mods.toml
├─ hexietrain.mixins.json
└─ assets/hexietrain/                 blockstates / models / lang
```

---

## 五、已知限制

| 项目 | 状态 |
|---|---|
| 时刻表**内容**修改（增删条目/改条件） | 写回链路已具备（`applySchedule`：同时写物品组件与列车运行时），但 UI 尚未提供编辑入口 |
| 选站 | 只改「下一站是谁」（`runtime.currentEntry`），条目顺序不变；列车到站后 Create 会推进游标，循环时刻表会回绕 |
| 自动驾驶距离判定 | 用直线距离近似（`distanceToStation`），不是沿轨道测距 |
| 寻路 | 使用 Create 原生 `Navigation.findPathTo`；绕行逻辑（曾有的 `RailPathfinder`）已移除 |
| 坡度 / 视距外物理 | 预留了 `SpeedModel` / `TrainDynamics` 的纯数学模型，尚未接入主循环 |
| 多节编组 | 车厢划分支持多结构，但尚未在实机验证连挂运行 |

---

## 六、版本历史

| 版本 | 变更 |
|---|---|
| **0.1.0** | 传动链路打通（照抄定向齿轮换向器）；六向放置与模型对齐；玩家操控/自动驾驶分离；站名栏滚轮选站 + 确认；三通道信号灯；速度显示（m/s）；删除「零速起步」「制动灵敏度」等无效开关；清理全部诊断日志与调试开关 |

---

## 七、致谢

- **Create** — 时刻表、寻路、信号、车站的全部调度逻辑
- **Sable** — 物理子结构、刚体与力组 API
- **Simurail** — 物理转向架与编组
- **simulated**（Create Aeronautics bundled）— 定向齿轮换向器的实现与模型资源，本模组的传动部分是照它实机反编译实现的
- **Electro Energetics** — 电力兼容接口