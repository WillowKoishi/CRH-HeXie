# CRH1A 转向架拆件与接入

本目录的原始 `.bbmodel` 保持不变。运行时资源使用与 DT206 相同的 NeoForge OBJ、PartialModel、SimpleBogeyRenderer 和 BogeySuspensionModels 管线，支持普通渲染、Flywheel 实例化及转向架反向设置。

从工程根目录重新导出或校验（仅需 Python 3 标准库）：

```powershell
python art_assets/CRH1/crh1a_bogey/export_bogeys.py
python art_assets/CRH1/crh1a_bogey/export_bogeys.py --check
```

动车来源为 `crh1_bogie_frame_motorl.bbmodel`、`crh1_bogie_wheel.bbmodel`；拖车来源为 `crh1_bogie_frame_trailer.bbmodel`、`crh1_bogie_wheel_trailer.bbmodel`。`motor/` 内的拖车导出副本用于核对 Blockbench 的分组与网格旋转，运行时不重复导入。动车、拖车各自保留其内嵌贴图，UV 按每张贴图的 UV 尺寸归一化。

| 部件 | 运动关系 |
| --- | --- |
| frame | 架体、横梁、空气弹簧下支座及架体端安装座 |
| primary_suspension | 一系悬挂，随架体 |
| motors（仅动车） | 两台电机及传动箱，随架体 |
| brakes | 制动夹钳及支架，随架体 |
| wheel | 轮轴、踏面、轮缘；拖车另含三个轴装制动盘，随轮对旋转 |
| bolster | 两半枕梁及牵引杆车体端安装座，随车体 |
| air_spring_left/right | 分别按左右枕梁连接点的位移平移，保持相对架体的原始方向和尺寸，不继承旋转、不缩放 |
| traction_rod | 架体与枕梁之间的牵引杆 |
| yaw_damper_left/right_shaft | 绕架体端对准另一端锚点，按两锚点距离的变化量轴向伸缩，保持直径及与套筒的重叠 |
| yaw_damper_left/right_sleeve | 外套筒，绕移动车体端旋转，保持长度 |
| yaw_damper_left/right_upper_mount | 减震器枕梁端安装座，随车体 |
| vertical_damper_left/right | 垂向减震器，连接架体与车体 |
| anti_roll_bar | 抗侧滚扭杆及下摇臂，随架体 |
| anti_roll_link_left/right | 抗侧滚连杆，连接下摇臂与车体 |

动车共 20 个部件，拖车共 19 个部件。网格均保留原始坐标、面顺序和 UV，将自身旋转、父分组旋转烘焙后除以 16；原始轨道 `rails` 与重复轮对 `wheel/wheel2` 分组全部排除。拖车独立轮对的三个可见制动盘原本被标记为 `export=false`，导出器按 UUID 单独恢复；架体里的参考轮对仍排除。拆件清单及面数保存在 `crh1a_motor_parts.json` 和 `crh1a_trailer_parts.json`。

轴心为 Z = ±21.6/16 = ±1.35，轴距 2.7；踏面最低点为 -7.328/16，注册半径 0.458。渲染轴高为 0.8006，叠加公共轨道增高 0.4074 后，零转角踏面下沿为 0.75，与当前轨面一致；轮缘低于踏面，不参与此对齐计算。架体原点就在轴心，无 DT206 的额外 5/16 偏移。减震器的 5°、牵引拉杆的 10° 初始旋转均参与连接点计算。

抗蛇行减震器的静止锚距为 12/16，内活塞从架体锚点到内端的长度为 6/16，套筒口到车体锚点的距离为 6.6/16，初始重叠为 0.6/16。内活塞内端长度按 `6/16 + 当前锚距 - 12/16` 更新，只沿轴向缩放；套筒按车体端锚点旋转和平移，保持原长。这样伸长、压缩时保持原有重叠，完全压缩时将活塞长度限制为正值，避免翻转或无效变换。

方块 ID：`hexie:crh1a_motor_bogey`、`hexie:crh1a_trailer_bogey`。两者加入 `hexie:crhbogey` 样式，尺寸 ID 分别为 `hexie:crh1a_motor`、`hexie:crh1a_trailer`；在现有转向架选择流程和 Simurail 动车组分类中可选。新转向架共用现有动车组地板资源，枕梁由拆出的 CRH1A 模型渲染。

车辆装配目录与 Simurail 共用 BuiltinBogeyModel/Profile 的几何参数。动车具有牵引力和功率，拖车均为零；运行参数沿用已有动车组的游戏平衡默认值，并非新增的实车测量数据。

模型和贴图遵循 `art_assets/README.md` 中的 GPL-3.0-only；保留原始素材及其署名。导出脚本和 Java 接入代码遵循工程代码许可。
