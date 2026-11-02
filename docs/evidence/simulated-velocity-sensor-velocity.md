# 航空学「速度传感器」如何取物理结构速度（源码取证）

来源：
  simulated-neoforge-1.21.1-1.3.2.jar
  （位于 create-aeronautics-bundled-1.21.1-1.3.2.jar 的 META-INF/jarjar/ 内）

类：dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity
命令：javap -p -c -cp "<simulated>;<sable>;<mc>" dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity

## 1. 取速度：同一局部点在两帧姿态下的世界位移 × 20

    private Vector3d getGlobalVelocity() {
        SubLevel sub = subLevelReference.get();
        if (sub == null) return new Vector3d();

        // 本方块中心，作为"局部点"
        Vector3d local = JOMLConversion.toJOML(worldPosition.getCenter());

        return sub.logicalPose().transformPosition(local, new Vector3d())     // 当前姿态下的世界坐标
                .sub(sub.lastPose().transformPosition(local, new Vector3d())) // 上一 tick 姿态下的世界坐标
                .mul(20.0);                                                   // 每 tick 位移 ×20 = 每秒位移 (m/s)
    }

要点：
  - 不是读刚体的线速度，而是**把同一个局部点分别用当前姿态和上一 tick 姿态变换到世界**，两者相减
  - 因此**旋转分量天然包含在内**（结构转动时，该点的世界位置也会变）
  - `lastPose()` 由 `SubLevel.updateLastPose()` 每 tick 更新

## 2. 取哪个方向的分量：投影到传感器法线

    Vector3d normalWorld = sub.logicalPose().transformNormal(currentNormal, new Vector3d());
    float v = (float) velocity.dot(normalWorld);
    adjustedVelocity = Math.abs(v) > 0.05 ? v : 0;      // 死区 0.05

## 3. 对外暴露

提示框（只读展示，单位 m/s）：

    SimLang.number(Math.abs(getAdjustedVelocity())).text(" m/s").forGoggles(list);

红石输出（归一化到 0~15）：

    getAdjustedVelocity() / maxSpeed.getValue() * 15

`maxSpeed` 是一个 ScrollValueBehaviour（可配置最大速度），
即：**红石强度 = 当前速度 / 设定最大速度**。

## 4. 相关 Sable API（javap 实测）

dev.ryanhcode.sable.sublevel.SubLevel
    public Pose3d  logicalPose();        // 当前姿态（可写）
    public Pose3dc lastPose();           // 上一 tick 姿态（只读）
    public void    updateLastPose();     // 每 tick 由物理系统调用

dev.ryanhcode.sable.sublevel.ServerSubLevel
    public final Vector3d latestLinearVelocity;    // 刚体质心线速度（不含旋转贡献）
    public final Vector3d latestAngularVelocity;

## 5. 结论

两种取速度的方式：

  A. 刚体线速度：`ServerSubLevel.latestLinearVelocity`（或 RigidBodyHandle.getLinearVelocity()）
     —— 质心线速度，**不含旋转带来的点位运动**，也不是沿轨道的分量

  B. 姿态差分（航空学采用）：同一局部点 current/last 姿态变换后相减 × 20
     —— 任意点、任意方向，**包含旋转**，与"结构上这个点实际怎么动"完全一致

对物理列车来说 B 才是正确做法：我们要的是"车头这个点沿轨道走了多远"，
而不是"质心平移了多少"。