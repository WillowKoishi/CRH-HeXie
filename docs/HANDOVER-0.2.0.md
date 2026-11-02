# 0.2.0 状态卡（极简）

## 环境
- 工程：`H:\蓝色肥鱼小家\hexie-physics`
- 实例：`H:\柳树服beta2\.minecraft\versions\Willow11TH_Aero_beta2(无地图)`
- 构建：`gradle build --offline` → `历史版本/[和谐：模拟自动铁路]Hexie_AutoSimulateTrain<ver>.jar`
- 安装：复制到 `<实例>\mods\`。**游戏运行时禁止覆盖**（会 NoClassDefFoundError）
- 版本：`mod_version=0.2.0`

## 代码现状（19 个类，约 3000 行）
- 方块 `TrainDispatcherBlock`：六向放置、可控传动、扳手、掉落
- BE `TrainDispatcherBlockEntity`：绑定列车、驾驶、时刻表、无线通道、UI 数据
- `TrainBinder`：Sable 转向架编组 → Create `Train`
- 3 个 mixin：阻止 contraption 锚点 / 跳过储物序列化 / 跳过烧燃料
- UI：时刻表槽 + 站名栏（滚轮选站 + `>>` 确认）、三通道 15 格灯、状态与 m/s 速度

## 关键 API 事实（已实测）
- 传动比照抄 `simulated` 的 `DirectionalGearshiftBlockEntity`：源侧恒 1，左 +1，右 −1，其余 0
- 轴垂直于 `facing`，由 `axis_along_first` 选对：`X→(Y:Z)`、`Y→(X:Z)`、`Z→(X:Y)`
- Create 动力网络**缓存**传动比，只有网络重建才重算

## 规则
必须先读 [RULES.md](../RULES.md)：能读的不猜、不能读的问、参考模组必须反编译取证（证据放 `docs/evidence/`）。

## 待办
**无。等待重新定义方向。**