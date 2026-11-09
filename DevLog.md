
# 开发日志

---
### version 0.9.3-Plana-bata
##### 2026.11.9

修改名称:

- “和谐号标识”更名为“CRH 标识”，同步动车组分类图标、创造物品栏及相关工具引用（WillowKoishi）

新增 / 修改美术资源:

- 添加 CRH 标识的物品图标及配套贴图（WillowKoishi）

其他:

- 添加文言文语言文件（lzh）（WillowKoishi）

---
### version 0.9.0-Plana-bata
##### 2026.11.8

添加方块（组）:

- 通用自动车钩（詹氏车钩）（HachimiYang, WillowKoishi）
- CRH2A 电子水牌（HachimiYang, WillowKoishi）
- CRH2A 车厢号显示屏（HachimiYang, WillowKoishi）

添加物品:

- CRH2 救援车钩转接器，可用于 CRH2 自动车钩与标准詹氏车钩之间的转换（HachimiYang, WillowKoishi）

添加功能:

- CRH2A 端面支持原版、倾斜式连接器、水平带线及水平不带线四种样式，使用和谐扳手切换（HachimiYang, WillowKoishi）
- 相容端面连挂后显示车间电缆，并随两节车厢的相对位置与姿态变形（HachimiYang, WillowKoishi）
- 补充 CRH2A 中部车顶的平顶、天线及带线缆变体（HachimiYang, WillowKoishi）
- 电子水牌与车厢号屏可作为附件安装到车厢板上，并调整显示位置（HachimiYang）
- 电子水牌读取当前列车名称 / 车次，以及启用驾驶台运行图中的始发、终到信息（HachimiYang）
- 使用和谐扳手编辑车厢号屏，支持 1–2 位数字、保留前导零及留空清屏（HachimiYang）
- 通用自动车钩及救援转接器添加钩舌状态、开合动画与连挂状态联动（HachimiYang）
- 支持在车窗流中取用车型配置提供的预制蓝图，蓝图随物品携带结构数据（HachimiYang）
- 接入机械动力剪贴板，按选中的文字、车身标记及标志复制粘贴（HachimiYang）
- 白刷子改为只移除点击到的附件，分别处理文本、标志、电子屏及车身标记（HachimiYang）
- 完善运行中车厢上文字贴纸、车号及标牌的编辑与数据提交（HachimiYang）

修改机制:

- 为 CW-2、209P、SW160、SW220K、DT206、SKTB-200 补充摇枕、空气弹簧或连杆等部件的随动渲染（HachimiYang）
- 适配 Create 与 Simurail 的转向架悬挂姿态，并处理反转及地板高度调整后的模型关系（HachimiYang）
- 统一驾驶台、车门、车钩等操作部件在世界、组装载具及 Sable 子世界中的表面选取与交互（HachimiYang）
- 地板调节工具注册名由 floor_switcher 改为 harmony_wrench，保留旧 ID 别名以兼容已有物品（HachimiYang）
- 整理乘客上下车、跨维度、区块加载及远端玩家位置插值，调整车厢运动中的地形碰撞与同步（HachimiYang）

修复问题:

- 修正道岔前方已缓存路线的重新选择，使手动转向与导航能在进入分岔前更新路线（HachimiYang）
- 修正列车脱轨或脱离轨道后的导航、信号预留及驾驶台运行图执行状态清理（HachimiYang）
- 修正标牌编辑界面的提交时序，以及组装车厢上编辑目标的定位与同步（HachimiYang）

新增 / 修改美术资源:

- 补充通用自动车钩、救援转接器、电子水牌、车厢号屏及端面变体的游戏模型与材质（HachimiYang, WillowKoishi）
- 更新 25B、25G、25K、25T、25Z 车厢的内外预览车模，并调整车厢前后间距（WillowKoishi, AlpacaEgg）
- 提亮老式站牌表面，修复模型中的纹理冲突；本次提交为美术源模型调整（AlpacaEgg）

---
### version 0.4.14-A.R.O.N.A.
##### 2026.11.7

添加方块（组）:

- 实装 SKTB-200 转向架，接入转向架样式切换、渲染与 Simurail 兼容（HachimiYang, WillowKoishi）

添加物品:

- 车厢板文本贴纸（HachimiYang）

添加功能:

- 文本贴纸支持编辑内容、字号、水平 / 垂直偏移、字体及字距，并提供编辑预览（HachimiYang）
- 和谐扳手支持调整 CRH2A 客室门的移动方向（HachimiYang）
- 和谐扳手添加转向架反转模式（HachimiYang）
- 完善行车 / 调车模式切换，切换前检查功率手柄零位及换向器切出位（HachimiYang）
- 简易驾驶模式显示速度、牵引及转向提示 HUD（HachimiYang）
- 补充 CTCS2 DMI 软键标签，整理驾驶台手柄、按钮及键盘操作的映射（HachimiYang）

修改机制:

- 拆分驾驶台控制面板、操作状态与列车控制请求，统一驾驶台接口及手柄档位恢复（HachimiYang）
- 调整多驾驶台控制仲裁，汇总各驾驶台的制动请求并限制冲突的牵引输入（HachimiYang）
- 调整曲线道床纹理与渲染路径，接入 Iris 的 PBR 法线、高光贴图（HachimiYang）

修复问题:

- 修正轨面采样、曲线轨道模型及转向架对齐关系（HachimiYang）
- 修正驾驶位视角、上下车位置及车内行走同步（HachimiYang）
- 调整组装车厢的门体、透明部件及旧式车厢部件的渲染与选取（HachimiYang）

新增 / 修改美术资源:

- 添加 CRH2A 带线缆车顶及 7 号车厢 FM 天线模型与贴图（WillowKoishi）
- 补充 CRH2A 端面连接器、电缆及电子显示牌素材，修正连接器贴图（WillowKoishi）
- 添加并调整通用詹氏车钩、CRH2 救援车钩模型及贴图；本阶段为美术资源（WillowKoishi）

----

### version 0.4.13-A.R.O.N.A.
##### 2026.11.5

添加方块（组）:
- CRH2A 宽体客室门（HachimiYang, WillowKoishi）
- CRH2A 自动车钩（HachimiYang, WillowKoishi）
- CRH2A 非统型驾驶台（未完成）（HachimiYang, WillowKoishi, AlpacaEgg）
- CRH2A 驾驶室座椅（HachimiYang, WillowKoishi）

添加功能:
- 非统型驾驶台的牵引、制动、换向手柄交互，添加档位提示及手柄动画（开发中）（HachimiYang）
- 添加动车组创造物品分类（HachimiYang）
- 自动车钩的防护罩开合及模型表面交互（HachimiYang）
- 完善带天线车顶的变体切换（HachimiYang）

修改机制:
- 统一车辆部件的多方块放置、选取与拆除，添加旧部件布局迁移（HachimiYang）
- 完善 RailX 轨道适配，处理轨道网络、轨面高度、选取框及道床渲染（HachimiYang）
- 调整自动驾驶、进站停车与发车逻辑，完善运行图进度保存及恢复（HachimiYang）
- 整理驾驶台控制接口，调整简易键盘控制、制动限制与 DMI 显示的数据衔接（HachimiYang）
- 优化道砟及无砟轨道曲线道床的批量渲染、实例化渲染与光照采样（HachimiYang）

修复问题:
- 修复 CRH2A 头车无法放置的问题（WillowKoishi）
- 修正重构后的驾驶台控制、座位同步及车门、车钩交互行为（HachimiYang）

新增 / 修改美术资源:
- 高柱三显信号机的低模、高模及配套贴图，本阶段为美术资源（AlpacaEgg）
- CRH2A 驾驶台、操纵手柄、司机座椅模型及贴图（WillowKoishi, AlpacaEgg）
- 优化 CRH2A 头车模型，补充带天线车顶变种（WillowKoishi）
- SKTB-200 拖车转向架及拖车轮模型，本阶段为美术资源（WillowKoishi）
- 修正 DT206 转向架、CRH2A 头车侧面及前窗贴图（WillowKoishi）

其他:
- 完善构建产物中的项目、字体、第三方及美术许可声明（HachimiYang）
- 构建时同时生成代码源码包和对应的美术源码包（HachimiYang）

---
### version 0.4.12-A.R.O.N.A.
##### 2026.11.4

添加方块（组）:
- CRH2A 客室门（HachimiYang, WillowKoishi）
- CRH2A 司机室门（HachimiYang, WillowKoishi）
- CRH2A 端面（HachimiYang, WillowKoishi）

添加功能:
- 编辑 CRH2A 头车车号及车身标记（HachimiYang）
- 添加车号、车身标记编辑界面，适配刷子工具（HachimiYang）
- 在 CRH2A 车身厢板上粘贴“和谐号”标志（HachimiYang）
- 添加车门的模型表面选取及组装后交互（HachimiYang）

修改机制:
- 调整 CRH2A 端面、车门的模型拆分与渲染（HachimiYang, WillowKoishi）
- 继续调整组装车厢的静态模型、动态部件及透明渲染（HachimiYang）

新增 / 修改美术资源:
- CRH2A 端面、车钩、客室门、宽体门及门框模型（WillowKoishi）
- 调整 CRH2A 车身通风部分、车门及端面贴图，补充路徽资源（WillowKoishi）
- 蒸汽机车五室汽笛模型（AlpacaEgg）
- 前进型、KD6487 号、8620 型 58654 号、D51200 号及 HXD3C 型机车汽笛 / 风笛资源，本阶段为美术资源（AlpacaEgg）
- 整理部分装饰类美术资源的名称与目录（AlpacaEgg）

---
### version 0.4.11-A.R.O.N.A.
##### 2026.11.2

添加方块（组）:
- CRH2A 非统型头车（HachimiYang, WillowKoishi）
- CRH2A 无窗厢板、加厚无窗厢板、小窗及宽窗厢板（HachimiYang, WillowKoishi）
- CRH2A 车顶、侧车顶、裙板及地板部件（HachimiYang, WillowKoishi）
- CRH2A 驾驶座、驾驶台及测试驾驶台（HachimiYang, WillowKoishi）
- DT206 转向架（HachimiYang, WillowKoishi）
- 可编辑的老式站牌（HachimiYang, AlpacaEgg）

添加物品:
- 地板高度 / 转向架位置调节工具（HachimiYang）
- 车钩转换装置（HachimiYang）

添加功能:
- 添加 CTCS2-200H 样式的 DMI 显示，接入速度、目标距离、限速及线路规划信息（HachimiYang）
- 添加驾驶台手动 / 自动控制、紧急制动、停放制动及缓解逻辑（HachimiYang）
- 调整列控目标、信号显示与运行图衔接（HachimiYang）
- 支持调整车辆地板高度及转向架纵向位置（HachimiYang）
- 支持用方块材质更换标准轨道道砟，适配直线及曲线轨道（HachimiYang）
- 站牌可编辑站名、拼音、前后站信息，并切换字体（HachimiYang）
- 更新车厢编号、区间牌及速度标的字体渲染，支持通过资源包配置字体（HachimiYang）

修改机制:
- 重构车辆组装、车体运动、转向架、车钩及车辆间约束计算（HachimiYang）
- 进一步分离车辆逻辑与渲染，优化轨道几何及运动计算（HachimiYang）
- 适配“汽鸣”车钩（HachimiYang）
- 抬高轨道并调整转向架、轨道覆盖层及预览的高度（HachimiYang）
- 调整乘客在组装车厢上的移动、座位乘坐与下车定位（HachimiYang）
- 优化 CRH2A 静态车体和动态部件渲染（HachimiYang）

修复问题:
- 修复可动方块控制器界面崩溃及物品栏同步问题（WillowKoishi）
- 修复组装车厢透明渲染，以及车门透视后方方块的问题（WillowKoishi, HachimiYang）
- 修正信号机右键长按行为（WillowKoishi）
- 恢复 25Z 车厢预览比例（WillowKoishi）
- 调整车窗流的视图边界、模型加载与资源重载（HachimiYang）

新增 / 修改美术资源:
- CRH2A 非统型转向架，以及头车窗户、侧板、内饰、车顶、底盘、驾驶台模型及贴图（WillowKoishi）
- 拆分 CRH2A 头车模型，修正 UV 与排障器（WillowKoishi）
- 国铁样式道口信号机、老式站牌及 CRH 停车位置标识资源（AlpacaEgg）
- 国铁 G 型客车空调发电车车顶装饰、四类车顶通风口，以及 25G、25K 行李车上厢板资源（AlpacaEgg）

其他:
- 更新项目介绍，说明跨越 0.3.9n 资源来源及 Create / Simurail 兼容关系（WillowKoishi）
- 补充美术资源的 GPL-3.0-only 许可及适用范围说明（WillowKoishi）

---
### version 0.4.10-A.R.O.N.A.
##### 2026.10.28

添加功能:
- 添加创造物品栏中的车窗流（HachimiYang）
- 支持切换车型、浏览车厢内外部及选取对应部件（HachimiYang）
- 点击部件可打开分组物品弹窗，选取同组变体（HachimiYang）
- 支持在车厢预览与原物品菜单之间切换（HachimiYang）
- 支持通过资源包配置车型、预览结构、部件分组及目标创造标签页（HachimiYang）

修改机制:
- 完善车厢切换动画、视图平移、缩放与部件选取（HachimiYang）
- 调整取物弹窗分页及边界，保存不同标签页的浏览状态（HachimiYang）
- 优化车厢预览的资源加载及重载（HachimiYang）

其他:
- 添加车窗流资源包配置示例（HachimiYang）

---
### version 0.4.9-A.R.O.N.A.
##### 2026.10.27

添加功能:
- 添加可调整朝向的信号机（HachimiYang）
- 支持按世界方向或轨道方向对齐信号机，并通过扳手旋转（HachimiYang）
- 完善曲线轨道上的信号机绑定（HachimiYang）

修改机制:
- 微调 Simurail 物理化转向架参数（WillowKoishi）
- 整理转向架、轨道及信号机注册与模型生成（WillowKoishi）

修复问题:
- 修复修改车厢号码牌导致玩家被踢出的问题（FlycatFeimao）

其他:
- 添加航空学兼容测试使用的客户端运行配置（WillowKoishi）
- 添加持续集成构建配置（WillowKoishi）
- 补充第三方版权声明（HachimiYang）

---
### version 0.4.8-A.R.O.N.A.
##### 2026.10.26

添加功能:
- 接入 Simurail 转向架兼容（HachimiYang）

修改机制:
- 整理车厢、车门、座椅、标牌等方块的公共逻辑（WillowKoishi）
- 调整方块实体数据保存、交互及渲染实现（WillowKoishi）

修复问题:
- 修复前进型转向架连杆动画（HachimiYang）
- 修复信号机贴图透视问题（WillowKoishi）

其他:
- 添加并固定 Simurail、Aeronautics 及 Sable 的开发依赖（WillowKoishi）

---
### version 0.4.7-A.R.O.N.A.
##### 2026.10.25

修复问题:
- 修复国铁过票闸机的渲染及崩溃问题（WillowKoishi）
- 修复信号机朝西放置时的朝向问题（WillowKoishi）
- 修复轨道重复绑定及放置交互问题（WillowKoishi）

修改机制:
- 调整信号机、轨道物品的放置状态与客户端同步（WillowKoishi）

---
### version 0.4.6-A.R.O.N.A.
##### 2026.10.25

版本迁移:
- 将既有铁路装饰内容迁移至 Minecraft 1.21.1 / NeoForge（WillowKoishi, FlycatFeimao）
- 使用 Java 21，适配 Create 6.0.10 及相关渲染、注册接口（WillowKoishi, FlycatFeimao）
- 迁移车厢、转向架、接触网、信号机、售票及标牌等既有内容（FlycatFeimao, WillowKoishi）

添加方块（组）:
- 各锈蚀阶段的涂蜡耐候钢块（WillowKoishi）
- 前进型转向架反向模型（WillowKoishi）

修改机制:
- 调整网络包、菜单注册、物品数据及 Mixin，以适应新版本接口（WillowKoishi, FlycatFeimao）
- 补充信号机灯色选项及相关模型资源（WillowKoishi）
- 优化车门、端面、标牌与界面组件的渲染及缓存（WillowKoishi）

修复问题:
- 修复前进型转向架朝向（WillowKoishi）
- 修复转向架预览缩放（WillowKoishi）
- 修复轨道破坏时的粒子效果（WillowKoishi）
- 修正 OBJ 模型、资源路径及部分界面的兼容性问题（WillowKoishi, FlycatFeimao）

其他:
- 补全 Gradle Wrapper（WillowKoishi）

---
### 2024.2.20
##### version 0.3.8-izayoi_sakuya

修改机制:
- 减少 Mixin 使用 
- 重制资源读取方式, 期望减少资源读取的 bug 

---
### 2024.2.17
##### version 0.3.8-izayoi_sakuya

修改机制:
- 获取转向架信息暂时不再需要佩戴工程师护目镜 
- 新版接触网支架补全 tooltips 
- 修改 Interaction Mixin 使用 

---
### 2024.2.16
##### version 0.3.8-izayoi_sakuya

添加物品(组):
- cr200j 上中下厢板 (Desert_Walker, WillowKoishi)
- cr200j 端面(两种) (Desert_Walker)
- cr200j 车头 (Desert_Walker)
- cr200j 车窗 (Desert_Walker)
- 可动方块控制器 
- 自动控制联轴器 
- 25z样式地毯 
- 通过信号机 (Zlw380)
- 无砟轨道 (Zlw380, WillowKoishi)
- 无枕轨道 (Zlw310, WillowKoishi)
- 耐候钢块(WillowKoishi)

修改机制:
- 添加可动方块自动控制与联轴 
- 旧版本接触网替换为新版本接触网 (Desert_Walker)
- 旧版本接触网废弃 
- 塞拉门动画修改 

其他:
- 添加合成表 (WillowKoishi, HTony03)
- tag 修正 (Willowkoishi)
- 挖掘掉落修复 
- 添加修改成就系统 (HTony03)

----
### 2023.10.21
##### version 0.3.5-izayoi_sakuya

添加方块：
- 前进型锅胴 (WillowKoishi)

添加转向架：
- 前进型动轮 (Willikoishi,Zlw380)

添加功能：
- 内置蓝图系统 


----
### 2023.7.31
##### version 0.3.2-izayoi_sakuya

添加物品：
- 耐候钢（HTony03）
- 耐候钢版（HTony03）
- 刷子
- 带颜色的刷子
- 绿色刷子（HTony03）
- 列车运行区段指示牌
- 贴纸
- 标准轨距铁轨（Zlw380）

添加方块：
- 原色25Z车厢下厢版（柳树怪）
- 原色25Z车厢中厢版（柳树怪）
- 原色25Z车厢上厢版（柳树怪）
- 原色25Z端面（柳树怪）
- 原色25Z车梯（柳树怪）
- 原色25Z列车地板（柳树怪）
- 单臂受电弓
- 标准轨距铁轨（Zlw380）
- 电动围栏门
- 电动围栏

添加转向架：（Zlw380，柳树怪）
- 长客CW-2转向架
- 铺镇209P转向架
- 铺镇209P转向架（轴发）
- SW220K转向架
- df11g转向架

添加修改合成表（HTony03）

添加功能：
- 创建，编写车厢号，车厢编号，列车运行区段指示牌，车厢速度标
- 复制黏贴车厢号，车厢编号，列车运行区段指示牌
- 随机生成车厢编号
- 添加创造物品分类
- 修改车厢编号颜色以及车厢编号颜色预设

合并 Lead Beyond 内容

新增物品：
- 红票

新增方块：
- 行李架
- 国铁闸机

修改名称：
- 单程票->乘车报销凭证

修改贴图：（Desert_Walker）
- 软座席
- 硬座

添加功能：
- 在售票机上绘制线路图
- 购买指定区间车票


----
### 2023.5.12
##### version 0.2.1-Patchouli

接触网下垂系数修正为0.0007
悬链线下垂系数修正为120


----
### 2023.5.12
##### version 0.2.0-Patchouli

更新部分的合成表（HTony03）

修改模型：
- 调车信号机（HTony03）

增加方块：
- 水泥接触网柱
- 水泥接触网柱45度转角
- 接触网柱侧边挂点转接器
- 接触网柱侧边挂点转接器45度转角
- 工字钢接触网柱
- 工字钢接触网柱45度转角
- 接触网支架A型内侧支架
- 接触网支架A型内侧支架45度转角
- 接触网支架A型外侧支架
- 接触网支架A型外侧支架45度转角
- 接触网支架C型1号支架
- 接触网支架C型2号支架
- 接触网支架C型1号支架45度转角
- 接触网支架C型2号支架45度转角
- 接触网支架B型（悬挂式）
- 接触网路端配重
- 坠砣吊线
- 线缆挂点（非接触网用）
- 适配支柱的线缆挂点（非接触网用）
- 悬空线缆挂点
- 坠砣
- 龙门架水泥杆
- 龙门架金属杆
- 龙门架连接器
- 龙门架
- 龙门架接触网挂架
- 龙门架接触网挂架（双侧）
- 龙门架接触网挂架延长杆
- 龙门架接触网挂架延长杆（双侧)
- a类接触网转接器
- c类接触网转接器
- 微型悬空线缆挂点
- 月台

增加物品：
- 通用接触网
- 通用直线
- 通用悬链线
- 剪线钳

----

### 2022.10.14

##### version 0.0.7-Kurumi d.37

增加方块：
- 道口铃
- 原色25G登车梯
- CR200J上厢板
- CR200J车窗
- CR200J下厢板
- CR200J地板

呕

----

### 2022.10.8

##### version 0.0.6-YumemiOkazaki d.32

增加方块：
- 刷绿25B车厢标识线
- 高阻绿25G标识线
- 高阻绿25K标识线
- 高阻绿25车厢窗户
- 高阻绿25T车厢裙板
- 高阻绿25T车厢侧裙板

修改贴图：
- 原色25T窗户
- 原色25K窗户

囍报，全刷绿了

----

### 2022.10.8

##### version 0.0.6-YumemiOkazaki d.31


增加方块：
- 日光灯
- 刷绿25系列车厢门
- 刷绿25系列厢下厢板
- 刷绿25系列厢下厢板b
- 刷绿25系列车厢中厢板
- 刷绿25系列厢上厢板
- 刷绿25系列厢上厢板b
- 刷绿25B车厢标识线
- 刷绿25B车厢窗户
- 刷绿25系列车厢地板

修改贴图：
- 原色25B窗户
- 原色25B车厢上厢板b
- 原色25B车厢下厢板
- 原色25B车厢中厢板
- 原色25B车厢上厢板
- 原色25B车厢窗户
- 原色25B车厢车门

蛤蛤，开始刷绿咯

----

### 2022.09.24


##### version 0.0.5-ChiyuriKitashirakawa d.20
增加方块：

接触网:
- 钢制接触网柱
- 混凝土接触网柱
- 接触网支架

地面系统:
- 入站信号机
- 禁止双弓标志牌
- 断标志牌
- 合标志牌
- 信号机柱
- 信号机(柱上)
- 盲道


----

### 2022.09.23

##### version 0.0.4-RikakoAsakura d.18
增加方块：

- 原色BSP25T车厢窗户
- 原色行李25T车厢上窗板

调整模型：
- 原色25T车厢裙板
- 原色25T车厢侧裙板

----

### 2022.09.21

##### version 0.0.3-KanaAnaberal d.16

增加方块：

- 原色25B车厢上厢板b
- 原色25T车厢上厢板b
- 原色25T车厢下厢板b
- 原色25B车厢地板
- 原色25G车厢地板
- 原色25K车厢地板
- 原色25T车厢地板

为此调整了以上4种车厢的下厢板。

准备刷绿了（悲）

----

### 2022.09.16

##### version 0.0.3-KanaAnaberal d.10

增加方块：

- 原色25B车厢下厢板
- 原色25B车厢中厢板
- 原色25B车厢上厢板
- 原色25B车厢窗户
- 原色25B车厢车门
- 原色25K车厢下厢板
- 原色25K车厢中厢板
- 原色25K车厢上厢板
- 原色25K车厢窗户
- 原色25K车厢车门
- 原色25k车厢标志板
- 原色25k车厢腰线板
- 原色25T车厢下厢板
- 原色25T车厢中厢板
- 原色25T车厢中侧厢板
- 原色25T车厢上厢板
- 原色25T车厢窗户
- 原色25T车厢车门
- 原色25T车厢裙板
- 原色25T车厢侧裙板
- 25B/G/K车棚
- 25T车棚

~~五提好时代要来力（喜）~~

另外，批量加板子真的累死人了啊

----

### 2022.09.12

##### version 0.0.2-Kotohime d.6

开始实装自己的服务器，大受好评（大嘘）

增加方块:

- 原色25G车厢中厢板
- 原色25G车厢上厢板
- 原色25G车厢窗户
- 原色25G车厢车门
- 黏土楼梯
- 平滑石英竖半砖
- 平滑石英竖1/4砖
- 平滑石英竖1/8砖
- 平滑石英竖坡

----

### 2022.09.07

##### version 0.0.1-Ellen d.1

作为试水性的写了一些hello world

增加方块:

- 原色25G车厢下厢板
- 受电弓
- 黏土台阶

增加物品:

- 火车餐A
