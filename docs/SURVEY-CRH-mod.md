# CRH（Hexie 0.4.10）项目调研报告

## 〇、它是什么

    modId       = hexie
    displayName = CRH
    version     = 0.4.10-A.R.O.N.A.
    authors     = 柳树怪(WillowKoishi), flycatfeimao, 羊驼猫猫头, radekemia, shazimingjiang
    类数量      = 354

包结构（前几名）：

    blocks/carriage              35   车厢方块
    client/render/bogey          20   转向架渲染
    blocks/structure             18   结构方块
    blockentity                  13
    blocks/semaphore/through     11   通过式信号机
    client/render/blockentity    10
    blocks/locos/df11g           10   机车方块
    client/screens                9
    menu                          7
    init/*                        7

**定位：内容/装饰模组**（车厢、转向架外观、信号机、座椅、车票、线路图、渲染）。
**没有自己的列车物理、导航或调度实现。**

---

## 一、真正有价值的第一样：Simurail 转向架的物理参数桥

`willow.train.hexie.compat.HeXieSimurailCompat`

    public static final BogeyEntry CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD,
                                    SW220K, SW220K_BACKWARD, SW160, DF11G, DF11G_BACKWARD, QJ, QJ_BACKWARD;
    private static void configureParameters();
    private static void configureBogey(BogeyEntry entry, double wheelRadius, double... axlePositions);

`configureBogey` 的实现（字节码还原）：

    BogeyType type = entry.type();
    double axleSpacing = axlePositions[axlePositions.length - 1] - axlePositions[0];

    BogeyPropertyOverrides.setAxleSpacingOverride(type, axleSpacing);
    BogeyPropertyOverrides.setAxleCountOverride(type, axlePositions.length);
    BogeyPropertyOverrides.setAxlePositionsOverride(type, axlePositions);
    BogeyPropertyOverrides.setWheelRadiusOverride(type, wheelRadius);

实际取值：**wheelRadius = 0.4575**，**axlePositions = {-1.2, +1.2}**（轴距 2.4 m）。

### BogeyPropertyOverrides 能覆盖的全部属性

    setLogicalAxleSpacingOverride   int / ToIntFunction
    setVisualAxleSpacingOverride    double / ToDoubleFunction
    setAxleSpacingOverride          double
    setAxleCountOverride            int
    setAxlePositionsOverride        double...
    setWheelRadiusOverride          double          ← 关键
    setConnectorAnchorOffsetOverride
    setTrackTypesOverride
    setTrackWidthOverride / setTrackHeightOverride
    setGroundDrivableOverride

---

## 二、第二样：BogeyType —— 转向架的几何/物理描述

    public final class BogeyType {
        BogeyStyle style();   BogeySizes.BogeySize size();   AbstractBogeyBlock<?> block();
        int      logicalAxleSpacing(CompoundTag);
        double   visualAxleSpacing(CompoundTag);
        int      axleCount(CompoundTag);
        double[] axlePositions(CompoundTag);
        double   wheelRadius(CompoundTag);          // ← 关键
        Vector3f connectorAnchorOffset(boolean, CompoundTag, Vector3f);
        Set<TrackMaterial.TrackType> trackTypes();
        double   trackWidth();   double trackHeight();
        boolean  invertible();   boolean groundDrivable(CompoundTag);
    }

---

## 三、第三样：BogeyCheckOverlay —— 转向架状态 HUD

    public class BogeyCheckOverlay extends BaseOverlay {
        BogeyStyle style;  BogeySizes.BogeySize size;
        BlockPos position; Level level;
        Component nameTag, speedLabel, speedValue;
    }

说明他们做了一套"看转向架状态"的客户端浮层，
可以直接参考它的渲染组织方式。

---

## 四、其余值得看一眼的

| 类 | 内容 |
|---|---|
| `item.AutomaticControlCouplingItem` + Menu + Screen | 自动车钩控制：把方块记入车钩清单（`addBlock(stack, contraptionEntity, pos)`） |
| `util.station.LineMap / LineTag / StationInfo / Connections` | 车站线路图数据结构，可给调度器 UI 复用 |
| `blocks.semaphore.through.*`（11 个类） | 通过式信号机 |
| `blocks.track.standard.StandardTrackBlock` | 自定义轨道方块 |
| `blocks.locos.PantographBlock` / `SS8PantographBlock` | 受电弓 |
| `mixins.mixin.client.MixinPhysicsBogeyBlockEntity` | 给 `PhysicsBogeyBlockEntity` 加了 `visualSpeed` 字段（用于 QJ 动画） |

---

## 五、对我们最重要的结论

**`wheelRadius` 一直存在于 Simurail 的转向架定义里，而且可以被读取。**

于是：

    v_linear = ω × r
    ω = (RPM / 60) × 2π

**转速驱动的车，理论速度是可以精确算出来的，不需要标定。**

这就把"驱动千差万别"拆成了两类：

| 驱动类型 | 速度来源 | 需要标定吗 |
|---|---|---|
| Create 传动轴 → 转向架 | 读轴转速 + `bogeyType.wheelRadius()` → 精确计算 | 不需要 |
| 推进器 / 帆 / 气球 / 其它 | 实测曲线回放 | 需要 |

所以之前"全部靠标定"的方案可以升级为**双路模型**：
有转速就用转速算，没有转速就回放标定曲线。

## 六、下一步要查的

1. `PhysicsBogeyBlockEntity`（Simurail）能否在服务端读到当前转速
2. `BogeyType` 是从哪里取得的（转向架方块 → type 的映射）
3. 我们的调度器能否拿到同位置转向架的 `BogeyType`