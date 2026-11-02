# 设计原则：物理体权威

## 原则

    位置与运动的权威 = Sable 物理体自身
    Create 的 Train / TravellingPoint = 被喂食的消费者
    方向永远是：物理体 → Create

也就是说：**先有物理体的真实运动，再把结果写进 Create**；
不是先让 Create 算出该走多远、再把物理体摆过去。

## 与现有实现的差别

现有（0.1.0 遗留）：Create 算 speed → Carriage.travel 推进轨道点 → 物理体跟随
目标（0.2.0）：物理体自己动 → 测出实际位移 → 写回 Train.speed / travel 距离

## 必须改动的三处

1. **速度来源**
   `Train.speed` 不再是权威，而是由传感器读数反推：

       train.speed = measuredVelocity().dot(trackTangent) / 20.0    // 格/tick

2. **推进距离**
   `Train.tick` 里 `double distance = speed;` → 再交给 `Carriage.travel(..., distance, ...)`
   要让实际推进量 = 物理体本 tick 的真实位移。

3. **到站判定**
   一旦轨道点由物理位移推进，`frontSignalListener` 会自然触发，
   `arriveAt → destinationReached` 全部回到 Create 原生流程。
   **我们手写的 `distanceToStation <= 0.4 → train.arriveAt()` 必须删除**
   （它绕过了 listener，不清 `navigation.destination`，会让状态机卡死）。

## 需要压制的东西

- `Navigation.tick()` 里的速度权威：
  `train.targetSpeed = ...; train.approachTargetSpeed(1);`
  路径/信号/道岔记账要保留，但**速度指令必须失效**，否则和物理体互相拉扯。
- 手动模式里 `train.speed = train.targetSpeed` 的直接赋值。

## 待定（需拍板）

- 注入点：`Carriage.travel` 的 distance 参数（mixin 改参数）
- 物理体不在场 / 未加载时的退化策略
- 多节编组时非首节车厢的距离如何处理（跟随 or 各自测量）