# Create 时刻表自动化机制（源码取证）

来源：create-1.21.1-6.0.10.jar（源码提取版，位于 .tmp/create-trains）
涉及类：
  com.simibubi.create.content.trains.schedule.ScheduleRuntime
  com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
  com.simibubi.create.content.trains.entity.Train
  com.simibubi.create.content.trains.entity.Navigation
  com.simibubi.create.content.trains.entity.Carriage

## 1. 主循环入口

Train.tick(Level)（Train.java:307-309, 315, 423）

    updateConductors();
    runtime.tick(level);        // 时刻表状态机
    navigation.tick(level);     // 速度控制
    tickPassiveSlowdown();
    double distance = speed;    // 本 tick 位移 = speed
    ...
    carriage.travel(level, graph, distance + totalStress, ...)

## 2. ScheduleRuntime.tick(Level) —— 三态机

    if (schedule == null) return;
    if (paused) return;
    if (train.derailed) return;
    if (train.navigation.destination != null) { ticksInTransit++; return; }   // 有目标就不动
    if (checkEndOfScheduleReached()) return;
    if (cooldown-- > 0) return;
    if (state == State.IN_TRANSIT) return;
    if (state == State.POST_TRANSIT) { tickConditions(level); return; }

    DiscoveredPath nextPath = startCurrentInstruction(level);
    if (nextPath == null) return;

    train.status.successfulNavigation();
    if (nextPath.destination == train.getCurrentStation()) {
        state = State.IN_TRANSIT;
        destinationReached();
        return;
    }
    if (train.navigation.startNavigation(nextPath) != TBD) {
        state = State.IN_TRANSIT;
        ticksInTransit = 0;
    }

checkEndOfScheduleReached()：

    if (currentEntry < schedule.entries.size()) return false;
    currentEntry = 0;                       // ← 循环回绕
    if (!schedule.cyclic) { paused = true; completed = true; }
    return true;

setSchedule(schedule, auto)：

    reset();
    this.schedule = schedule;
    currentEntry = Mth.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
    ...

reset()：paused=true, currentEntry=0, schedule=null, state=PRE_TRANSIT

destinationReached()：

    if (state != State.IN_TRANSIT) return;   // ← 只有 IN_TRANSIT 才生效
    state = State.POST_TRANSIT;
    ...按当前条目重建 conditionProgress / conditionContext

tickConditions(level)：

    条件全部满足 → state = PRE_TRANSIT; currentEntry++;

## 3. DestinationInstruction.start() —— 谁给出路径

    if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
        train.status.missingConductor(); runtime.startCooldown(); return null;
    }
    for (GlobalStation globalStation : train.graph.getPoints(EdgePointType.STATION)) {
        if (!globalStation.name.matches(regex)) continue;   // 站名 glob→regex
        anyMatch = true; validStations.add(globalStation);
    }
    DiscoveredPath best = train.navigation.findPathTo(validStations, Double.MAX_VALUE);
    if (best == null) { failedNavigation / failedNavigationNoTarget; startCooldown(); return null; }
    return best;

## 4. Navigation.tick —— 速度怎么算

    double targetDistance = waitingForSignal != null ? distanceToSignal : distanceToDestination;
    targetDistance += 0.25;                                  // 保证压过目标点
    if (targetDistance > 1/32f && train.getCurrentStation() != null) train.leaveStation();

    if (targetDistance < -10) { cancelNavigation(); return; }
    if (targetDistance - Math.abs(train.speed) < 1/32f) {
        train.speed = Math.max(targetDistance, 1/32f) * speedMod;   // 精确停在目标上
        return;
    }
    train.burnFuel();
    double topSpeed = train.maxSpeed();
    if (targetDistance < 10) {                               // 进站减速
        double maxApproachSpeed = topSpeed * (targetDistance / 10);
        ...
    }
    topSpeed *= train.throttle;
    double targetSpeed = targetDistance > brakingDistance ? topSpeed * speedMod : 0;
    train.targetSpeed = targetSpeed;
    train.approachTargetSpeed(1);

## 5. 到站判定 —— 靠 TravellingPoint 的边点监听

Carriage.travel(...)（Carriage.java:192-203）：

    IEdgePointListener frontListener = train.frontSignalListener();
    IEdgePointListener backListener  = train.backSignalListener();
    ...
    double moved = point.travel(graph, toMove, trackSelector, signalListener, ...);

Train.frontSignalListener()（Train.java:462-475）：

    if (couple.getFirst() instanceof GlobalStation station) {
        if (!station.canApproachFrom(...) || navigation.destination != station) return false;
        speed = 0;
        navigation.distanceToDestination = 0;
        navigation.currentPath.clear();
        arriveAt(navigation.destination);
        navigation.destination = null;      // ← 只有这里会清空目标
        return true;
    }

Train.arriveAt(station)：

    setCurrentStation(station);
    reservedSignalBlocks.clear();
    runtime.destinationReached();
    station.runMailTransfer();

## 6. 结论

自动化 = 三态循环 + 两个触发点：

  触发点 A：navigation.destination == null  → 向当前条目要路径 → startNavigation
  触发点 B：车头点压过车站边点（listener） → arriveAt → destinationReached → POST_TRANSIT
           → tickConditions 全部满足 → currentEntry++ → 回到 PRE_TRANSIT

两个关键约束：
  1. destinationReached() 只在 state == IN_TRANSIT 时生效
  2. 只有 frontSignalListener 会清空 navigation.destination；
     若绕过它直接调 arriveAt，destination 保持非空，
     而 runtime.tick 首句 `if (navigation.destination != null) return;`
     会让状态机从此卡死（POST_TRANSIT 的条件永不 tick）