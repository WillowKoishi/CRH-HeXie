# 架构定案：近处物理体 / 远处质点，交接靠 TP

## 决策

    近（区块加载）  → Sable 物理体自由运动，物理权威
    远（区块卸载）  → 物理体进 HoldingSubLevel 冻结，改由「质点」按 1D 模型积分
    回场           → 把物理体 TP 到质点当前位置与朝向，并注入速度

质点是**数据对象**（轨道位置 s、速度 v、质量 m、受力 F），
不是刚体，不依赖区块，永远在跑。

---

## 交接：回场（点 → 物理体）

    1. 目标区块先加载
       container.addForceLoadTicket(sub, TEMP_TICKET, 位置)   // 否则刚复活又被踢

    2. 由质点的 s 算出位姿
       Pose3d pose = trackPoseAt(s);        // 见下节「s → Pose」

    3. 改 NBT 姿态（此时还没有刚体，不能 teleport）
       HoldingSubLevel h = map.getHoldingSubLevel(uuid);
       SubLevelData d = h.data();
       d.fullTag().put("pose", SableNBTUtils.writePose3d(pose));
       d.pose().set(pose);
       d.setOriginLoadedChunk(new ChunkPos(pose.position().x >> 4, pose.position().z >> 4));

    4. 复活
       map.loadHoldingSubLevel(h);

    5. 注入速度（卸载态没有速度字段，复活后默认静止）
       ServerSubLevel sub = 按 uuid 从 container.getAllSubLevels() 里找回来
       RigidBodyHandle handle = RigidBodyHandle.of(sub);
       handle.addLinearAndAngularVelocity(质点速度向量, 质点角速度向量);

    6. 撤掉临时票据（或保留到物理体自己申请票据）

## 交接：出场（物理体 → 点）

    1. handle.getLinearVelocity() / getAngularVelocity()
    2. 投影到轨道切线 → v
    3. 沿轨道投影姿态 → s
    4. 写入质点
    5. 撤票据，让 Sable 自然卸载

---

## s → Pose：精度的核心

质点只有一维 s，但物理体需要完整 6 自由度姿态。换算方式：

    前端点 = 质点位置沿轨道前进 bogeyGap/2
    后端点 = 质点位置沿轨道后退 bogeyGap/2
    中心   = (前端点 + 后端点) / 2
    yaw    = atan2(dz, dx)
    pitch  = -atan2(dy, sqrt(dx²+dz²))
    roll   = 轨道倾斜（TrackEdge 的 tilt）

这套算法 Create 自己在 `Carriage.updateContraptionAnchors` 里做过，可直接照抄。

## 精度前提（四条，缺一不可）

| # | 前提 | 不做的后果 |
|---|---|---|
| 1 | 记录**子结构原点与轨道接触点**的局部偏移 | 复活后车身陷进轨道或悬空 |
| 2 | 记录**车体朝向与模型 forward 轴**的对应关系 | 复活后车头朝后/侧躺 |
| 3 | 复活后立即注入速度 | 车会从静止开始，被物理瞬间"拉停" |
| 4 | 复活前目标区块必须已加载 | TP 无效，立刻被踢回 HoldingSubLevel |

## 代码量（新增部分）

| 模块 | 行数 |
|---|---|
| 质点：状态 + 1D 积分（牵引/阻力/坡度/制动） | 250～350 |
| `trackPoseAt(s)`：s → Pose3d | 150～200 |
| 回场/出场交接 | 150～200 |
| 偏移量标定与持久化（NBT） | 50～80 |
| **合计** | **600～830** |

不包含：把现有 `TrainDispatcherBlockEntity` 的驾驶逻辑改到质点模型上（另算）。

## 剩余风险

1. 目标区块加载时机（同步生成可能卡顿）
2. 复活瞬间物理引擎是否稳定（可能有轻微下坠/抖一下）
3. 质点模型与物理体在场时的速度连续性（交接瞬间允许一次小突变，但要可接受）