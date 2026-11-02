# 违反架构铁律的代码清单（逐条复查）

复查时间：2025-11-02
依据：`RULES.md` 〇之二（物理体权威）、〇之三（中转站非输出者）
复查方法：全项目 grep + 逐处阅读源码上下文

---

## 〇之三（中转站 / 非输出者）—— **无违规**

| 检查项 | 结果 |
|---|---|
| `setSpeed` / `generateRotation` / `setSource` / `setCapacity` / `setStress` | 全项目 0 处 |
| `getRotationSpeedModifier`（BE:232-243） | 与 `simulated` 的 `DirectionalGearshiftBlockEntity` 逐行一致：源侧 1、左 +1、右 −1、双亮 0、无源 0 |
| 红石强度 → 输出转速 | 无。红石只写 `LEFT_POWERED` / `RIGHT_POWERED` 两个布尔，传动比只有 ±1/0 |
| 输出转速 ≠ 输入转速 | 不可能，`getRotationSpeedModifier` 只返回倍数 1/−1/0 |
| 无输入时输出 | `hasSource()` 为假 ⇒ 返回 0 |

结论：**方块本体符合〇之三。**

---

## 〇之二（物理体权威）—— 共 9 处违规

### V1 · BE:340-347 手动接管给影子车设速度

    train.manualTick = true;
    train.targetSpeed = dir * mag * MANUAL_MAX_SPEED;
    train.approachTargetSpeed(1.0f);

- 违规点：速度由红石强度直接决定，方向 Create → 物理体
- 违反：〇之二「禁止让 Create 决定走多远」；〇之三的延伸（红石决定快慢）

### V2 · BE:353 手动模式写 speed（符合原则）

    train.speed = bodySpeedAlongTrackBps() / 20.0;

- 判定：**这一行是对的**（物理体 → Create）
- 但被 V1/V3 夹在中间，方向相冲，实际生效顺序是 V3 覆盖它

### V3 · BE:360 直接赋值目标速度

    train.speed = train.targetSpeed;

- 违规点：绕过物理，直接设定列车速度
- 违反：〇之二「禁止直接赋值 train.speed 作为驱动手段」
- 后果：物理体与影子车脱节，出现过"凭空飞出去"

### V4 · BE:377-400 手写到站检测 + arriveAt

    if (before >= 0 && after <= 0) { train.arriveAt(arriveTarget); }

- 违规点：绕过 `TravellingPoint.travel` 的 `frontSignalListener`
- 违反：〇之二「到站判定必须交给 Create 原生边点监听」
- 后果：`navigation.destination` 不被清空 → `runtime.tick` 首句 `if (destination != null) return;` → POST_TRANSIT 条件永不 tick → 时刻表卡死

### V5 · BE:904 waitingForSchedule 时 `train.speed = 0`

- 违规点：直接抹掉速度，而不是让物理体停下
- 违反：〇之二

### V6 · BE:931 用 `bodySpeedAlongTrackBps()` 做制动曲线

- 现状：该方法已改用 `measuredVelocity()` 投影（符合"唯一速度来源"）
- 问题：用它算出 `targetV` 后，再通过**红石三通道**去驱动，仍属"Create 侧决策"
- 严重度：中。测量来源正确，但决策链方向仍待重构

### V7 · BE:938-955 `d <= 0.4` 判定到站 + `arriveAt`

- 违规点：`distanceToStation` 是**直线距离**，不是沿轨道距离；且再次绕过边点监听
- 违反：〇之二两条（到站判定方式 + 距离定义）
- 后果：与 V4 同源，双重触发、重复推进

### V8 · BE:942 到站时 `train.speed = 0`

- 违规点：同 V5

### V9 · SubLevelSuspend:97-125 `followTrain(...)` 速度伺服

    Vector3d target = (cur - last) * 20;                  // 由影子车位置算目标速度
    handle.addLinearAndAngularVelocity(target.sub(current), new Vector3d());

- 违规点：**反向**——把影子车的位置差灌进物理体速度。这是"Create 权威"的典型实现
- 违反：〇之二（方向必须是物理体 → Create）
- 现状：当前**无任何调用点**（死代码）
- 备注：`handle.getLinearVelocity` 也在这里出现，是第二个速度来源，违反"唯一速度来源"

---

## 附带发现（非铁律，但有害）

| 位置 | 问题 |
|---|---|
| BE:334-335、350-365 | 两段手动逻辑重复且互相覆盖（V1 之后又 V3） |
| BE:367-371 | 注释仍写着"影子车的轨道点是复制来的，监听不一定触发"——这是对 0.1.0 假设的残留，需要随 V4/V7 一起删除 |
| SubLevelSuspend:97 | 死代码 `followTrain` |
| TrainStateReader:33 | `train.speed * 20.0` 作为"速度"对外暴露——若将来用于控制，会变成第二个权威 |

---

## 修复顺序建议

1. **V9**（删死代码）→ 立刻可做，零风险
2. **V4 / V7 / V8 / V5**（删手写到站与清零）→ 让到站回到原生流程
3. **V1 / V3**（删速度直驱）→ 与物理体权威对齐
4. **V6**（制动决策链）→ 需要与"物理体权威"配套的控制设计，最后做