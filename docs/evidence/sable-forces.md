# Sable 子结构如何计算受力（源码取证）

## 〇、先更正一条我之前的错误结论

上一轮我说"这个整合包没有物理引擎，physicsTick 是空实现"。
这是**错的**——我只扫了 mods 顶层 jar 的 `META-INF/services`，
漏掉了 Sable 自己 jarJar 里的嵌套 jar。

真实情况：

    sable-neoforge-1.21.1-2.0.5.jar
      └─ META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.5.jar   (7.4 MB)
           └─ META-INF/services/dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider
                → dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipelineProvider

**本整合包跑的是真物理：Rapier（Rust）引擎。**

`dev.ryanhcode.sable.physics.impl.none.StaticPhysicsPipeline` 只是
"没装 Rapier 时的空回退"，它的 `physicsTick` 确实只有 `return;`，
但运行时用的不是它。

---

## 一、物理体的构成

    求解器里的刚体（Rapier native）
      ├─ 体素碰撞体：RapierVoxelColliderBakery，从区块段里的方块烘焙而来
      ├─ 质量/惯性：MassTracker / MassData（getMass / getInertiaTensor / getCenterOfMass）
      └─ 位姿：Rapier 持有，每步由 readPose(...) 写回 SubLevel.logicalPose()

`ServerSubLevel implements PhysicsPipelineBody` —— 它就是刚体的 Java 侧句柄。

## 二、受力是怎么算的

### 1. 引擎内部（Java 不参与）

    RapierPhysicsPipeline.init(Vector3dc gravity, double dt):
        scene = new RapierPhysicsScene(
            Rapier3D.initialize(gravity.x, gravity.y, gravity.z, dt)
        );

    RapierPhysicsPipeline.physicsTick(double dt):
        updateContraptionPoses();                  // 先同步运动学体姿态
        Rapier3D.step(scene.handle(), dt);         // ← 整个求解在这里
        ...处理 queuedWakeUps

    Rapier3D:
        static native void step(long scene, double dt);
        static native void teleportObject(long, int id, x,y,z, qx,qy,qz,qw);
        static native void applyForce(...);
        static native void applyForceAndTorque(...);

**重力、碰撞、摩擦、约束求解全部在 native 层**，Java 侧只是把重力向量传进去。

### 2. 额外施加的力（Java 侧 API）

    ForceGroups 注册表（用于分类/显示）:
        GRAVITY, DRAG, LEVITATION, BALLOON_LIFT, PROPULSION, LIFT, MAGNETIC_FORCE

    ForceTotal:
        localForce / localTorque
        applyForceTotal(ForceTotal)
        applyLinearImpulse / applyAngularImpulse
        applyForces(RigidBodyHandle)

    RigidBodyHandle.applyForcesAndReset(ForceTotal):
        → ForceTotal.applyForces(this)
        → 逐项转成 applyForce / applyImpulse 交给 Rapier

也就是说：Sable 自己**只负责"额外力"的累加与提交**，
基础物理（重力/接触）由 Rapier 完成。

## 三、碰撞体与区块的绑定

    PhysicsPipeline.handleChunkSectionAddition(...)   // 区块段加载 → 烘焙碰撞体
    PhysicsPipeline.handleChunkSectionRemoval(...)    // 区块段卸载 → 移除碰撞体

`RapierVoxelColliderBakery` 需要读方块，
所以**没有已加载区块段 → 没有碰撞体 → 刚体无法存在**。

## 四、两种刚体模型（都在 Rapier 里）

| | 动态刚体 | 运动学刚体 |
|---|---|---|
| Java 类型 | `ServerSubLevel implements PhysicsPipelineBody` | `KinematicContraption` |
| 位姿来源 | Rapier 积分 | `sable$getPosition(partialTick)` / `sable$getOrientation(partialTick)` |
| 受重力 | 是 | 否 |
| 谁会用它 | 玩家用 Sable 工具造的子结构 | Create 的 contraption 实体（Sable 用 mixin 注入 `AbstractContraptionEntity`） |
| 卸载 | `onUnloaded` → HoldingSubLevel | 无该钩子，由持有者移除 |

`RapierPhysicsPipeline` 里对应 `activeSubLevels` 与 `activeContraptions`
（`TrackedKinematicContraption` 内部类）。

## 五、结论

1. **真物理在跑**，引擎是 Rapier，Java 侧只负责施加额外力与同步位姿
2. 物理体**必须**依赖已加载区块段，这是结构性的，不是策略
3. 出加载范围 → `onUnloaded` → `HoldingSubLevel`（NBT，含 pose，不含速度）
4. 运动学体（kinematic）可以"由代码指定位姿"，但只有 Create contraption 走这条路；
   玩家子结构是动态刚体，**自己会被重力拉、会被碰撞推**