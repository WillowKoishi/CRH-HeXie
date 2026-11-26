
# 开发日志

### version 1.0.0-hoshino-alpha d.1542
##### 2026.11.26

添加方块（组）:

- 新增 CR200J 车头与面板方块，分别拆出车头方块、面板方块组、车头方块实体和专用渲染器（HachimiYang）

添加物品:

- 随心贴（`custom_sticker`），支持在任意未组装世界方块或物理结构表面放置参数化几何车窗与自定义图片贴花，并纳入主创造物品分类（HachimiYang）

添加功能:

- 首次进入标题界面时显示 v1.0 系列早期开发版本的稳定性提示，提供中、英、法、西、德、日六种语言文本及确认按钮（WillowKoishi）
- 随心贴支持「万象车窗」与「图片贴花」两种模式：右键空气或潜行右键方块可打开模式与参数设置界面（`StickerModeScreen`），按住 Ctrl 可开启 16×16 精细对齐网格与边距 HUD 提示，右键白刷子可移除对应表面上的随心贴（HachimiYang）
- 随心贴几何车窗模式支持调整边框颜色、玻璃颜色与透明度、边框粗细、圆角半径、宽高及平面偏移，可跨多方块或偏移至相邻方块放置；右键已放置车窗可进入自由视角编辑模式（`StickerEditorScreen`），支持 WASD／空格／Shift 移动镜头、右键切换视角锁定与光标解放、拖拽 3D 上下左右箭头手柄或通过可拖动参数面板实时调整（HachimiYang）
- 随心贴图片贴花模式支持通过系统文件对话框或输入路径载入 PNG／JPG／BMP 本地图片预览，支持滚轮缩放与 Ctrl + 滚轮旋转；右键方块确认后按 SHA-256 内容寻址上传并持久化至服务端 `<world>/data/hexie_stickers/`（单图上限 2 MiB），并按需同步至区块内的其他客户端（HachimiYang）

修改机制:

- 为随心贴加入基于区块附件的数据持久化、跨区块影响索引及客户端动态贴图缓存；通过 `MixinSectionCompilerCut`、`MixinBlockOcclusionCut` 与 `BlockModelCutter` 在区块网格编译阶段对普通方块及多方块／曲面 OBJ 车厢外壳执行共面单层与零厚度实时挖孔，自动生成孔洞内壁、三维圆角窗框与半透明玻璃并避免内部区块被遮挡剔除；已放置的图片贴花接入内置 `renderer/` 渲染域并按真实表面裁切贴合绘制（HachimiYang）
- 将车辆与转向架按车型归入独立车包源目录；构建配置统一登记 CRH1A（`crh1a`）、CRH2A（`crh2a`）、CR200J（`cr200j`）、25 系客车（`series25`）、韶山 7D（`ss7d`）、东风 11G（`df11g`）、和谐 3D（`hxd3d`）及前进型蒸汽机车（`qj`）八个内置车包，并分别纳入 Java 源码、资源和数据生成读取路径（HachimiYang）
- 本次提交实际迁移与调整的车包文件集中在 CRH1A 和 CR200J：CRH1A 车头、端面、地板、驾驶台、门及相关部件代码和资源归入 `packs/crh1a/`；CR200J 车头与面板代码、模型贴图、方块状态和配方等资源归入 `packs/cr200j/`。其余六个车包已纳入统一构建入口，但本次提交没有相应的 `packs/` 文件迁移（HachimiYang）
- 将 CRH1A 的车头几何、车门主体与成员方块、门几何和修复逻辑、端面、地板、驾驶台、头车转向架及车钩等实现按职责拆为独立类；另将车门交互、驾驶台控制与手柄、头车方块实体，以及车门、头车、端面、车钩、照明和静态车厢段渲染分别拆分，便于按功能维护（HachimiYang）
- 将渲染实现和资源集中到 `renderer/` 渲染域；车包内仍可保留车型专属渲染类，并通过构建源目录统一编译；注册 ID 和资源路径继续使用 `hexie` 命名空间，保持既有存档兼容（HachimiYang）
- 将通用实现按 `core`、`bridge`、`railway`、`vehicle`、`world`、`interaction`、`sync`、`ui`、`render`、`compat` 十个代码域组织：核心域不依赖其他模组代码，桥接域只依赖核心与自身，业务域通过桥接接口协作；`Main.java` 作为组合入口连接各域（HachimiYang）
- 新增 Gradle `checkDomainBoundaries` 校验任务，扫描源码及车包，限制跨域导入、全限定类名和对 `Main` 的引用，并将 Create、Flywheel、Catnip、Registrate 等可选库引用限定在兼容与渲染相关位置；该任务接入 `check` 和 `jar`（HachimiYang）

移除内容:

- 剥离 Create 兼容模块并将轨道、信号机及半成品迁移至 `create_*` 注册名后，移除旧 `standard_track`、`ballastless_track`、`tieless_track`、`hx_semaphore` 战利品表、旧无砟／无枕轨道半成品物品模型，以及 12 个依赖机械动力或旧路径的合成配方（涵盖 25B/G/K 原色端面、25 系车顶空调与水箱、轨道、耐候钢块搅拌／压板与 25Z 地板地毯搅拌配方）（HachimiYang）
- 随代码域与 Mixin 体系重组，移除约 20 个不再使用的旧 Mixin 与访问器（包括 `MixinBlockControlClip`、`MixinBlockControlPicking`、`MixinDoorInteractionPacket`、`MixinDriverSeat`、`MixinPhysicalCouplerRedstone`、`MixinCarriageCamera`、`MixinTrainHudDirection`、`MixinTrackGraphVisualizer`、`MixinCRH1DoorRenderContext` 等）（HachimiYang）

其他:

- 补充 209P、CW-2、SW160、SW220K 转向架模型与贴图及椅子 22 源模型；完成车包目录、功能域和构建入口重组，并未把这些美术源模型当作车型代码迁移（WillowKoishi、HachimiYang）
- 加入 JUnit 5 与 Gson 测试依赖及 JUnit Platform 运行配置；增加 Sodium 0.8.13 嵌套模组包提取和客户端运行目录暂存任务，供开发运行配置加载（HachimiYang）
- 增加按代码域筛选 Java 编译的 `compileOnlyZones` 参数，并将 NeoForge access transformer 接入构建（HachimiYang）
- 将开发版本号从 0.11.2-nonomi-bata 更新为 0.11.4-nonomi-bata（HachimiYang）
- 将开发版本号从 0.11.4-nonomi-bata 更新为 1.0.0-hoshino-alpha（WillowKoishi）

---
### version 0.11.2-nonomi-bata d.1542
##### 2026.11.26

新增 / 修改美术资源:

- 新增 25G 客车整车 Blockbench 源模型及车棚、厢板、地板贴图（WillowKoishi）
- 补充 209P、CW-2、SW160、SW220K 转向架的源模型、OBJ/MTL 游戏模型与车轮资源，并整理至 `normal_speed_bogies_refresh/`；另添加椅子 22 源模型（WillowKoishi）

其他:

- 更新 README 英文项目标题与介绍，补充铁路系统、MTCS-200、CarriageFlow 及模组兼容性说明与配图（WillowKoishi）

---
### version 0.11.1-nonomi-bata d.1541
##### 2026.11.25

添加功能:

- 驾驶台乘坐时显示驾驶 HUD，提供游戏时间、运行目标、当前与目标速度、牵引或制动状态，并可按 Tab 切换显示（WillowKoishi）
- 可调式色灯信号机设置新增区段／连锁信号模式选项，并同步信号类型及相应状态提示（WillowKoishi）

修改机制:

- 驾驶 HUD 按目标类型显示车站、信号停车点或限速点；统一读取原生列车与快照列车的速度、限速和牵引／制动数据（WillowKoishi）
- 驾驶 HUD 使用拉丁字母与简体中文专用字体，按需加载字形并在字体不可用时回退到游戏字体（WillowKoishi）

新增 / 修改数据资源:

- 新增 Patchouli《和谐图鉴》手册入口及机车、女仆、其他、总览分类框架（WillowKoishi）

其他:

- 为 Create、Aeronautics 及其客户端运行配置拆分 Gradle source set，并添加客户端 Patchouli 依赖（WillowKoishi）

---
### version 0.11.0-nonomi-bata d.1539
##### 2026.11.23

添加方块（组）:

- 韶山 7D 车头及配套地板结构（WillowKoishi）
- 韶山 7D 机车地板、上部和下部车厢板（WillowKoishi）
- 韶山 7D 侧车棚、中间车棚和车门（WillowKoishi）

添加功能:

- 韶山 7D 车头内置詹氏车钩。组装后可操作钩头，钩舌随连挂状态更新（WillowKoishi）
- 韶山 7D 车头添加随降雨启停的双雨刷。组装车厢按挡风玻璃的世界位置判断降雨，雨停后雨刷完成当前周期再回位（WillowKoishi）
- 使用带颜料的刷子右键韶山 7D 车号区域，可在世界方块和组装车厢上编辑四位车号、铁路局及机务段，并可隐藏局段标记（WillowKoishi）
- 韶山 7D 根据车号在侧面铭牌和车身字样之间切换，车号编辑界面支持选择 I 端或 II 端，并在车头两侧显示端位标记（WillowKoishi）
- 韶山 7D 车门可右键开关。放置时可选择镜像方向，也可用和谐扳手切换整扇门的左右方向，组装后仍可操作（WillowKoishi）
- 韶山 7D 侧车棚可用带颜料的刷子切换外观，双手空手右键可切换左右方向，组装后仍可操作（WillowKoishi）
- 韶山 7D 中间车棚可用和谐扳手切换空车顶、两种设备、车顶通风窗和车顶线路。双手空手右键可切换左右方向，组装后仍可操作（WillowKoishi）
- 韶山 7D 地板可用和谐扳手在空车底与四种车底设备朝向之间切换，世界方块与组装车厢均可操作（WillowKoishi）

修改机制:

- 适配 P&W 受电弓在和谐车顶上的安装高度，同步调整模型、选取形状以及组装车辆上的接触网探测位置（WillowKoishi）
- 为旧韶山 7D 车辆快照补齐中间车棚的外观、镜像状态和地板设备状态，未设置的地板设备按空车底读取（WillowKoishi）

修改名称:

- 简体中文中将组号编辑标题调整为「列车组号」，根进度标题调整为「CRH和谐」（WillowKoishi）

新增 / 修改美术资源:

- 补充韶山 7D 车头、车门、车厢板、地板和车棚的源模型、拆件模型与贴图，并加入司机室空调、车顶线路、车底设备、车号及局段字样资源（AlpacaEgg、WillowKoishi）
- 更新车厢部件源模型 art_assets/wow.bbmodel（WillowKoishi）

其他:

- 将运行日志目录 logs/ 加入 Git 忽略规则（WillowKoishi）
- 将车窗流资源包配置文档标题中的「车窗流」改为「车厢流」（WillowKoishi）

---
### version 0.10.6-Shiroko-bata d.1537
##### 2026.11.21

添加方块（组）:

- 韶山 7D 前转向架（WillowKoishi）

添加功能:

- 手持标准轨距铁轨时，蹲下对空气右键可打开道砟材质设置。按住 Shift 点击物品栏中碰撞箱完整的方块即可选用，空手点击材质槽可恢复圆石（WillowKoishi）
- 道砟预设随标准轨距铁轨的物品堆叠保存和同步，悬停可查看当前材质。铺轨时自动将预设应用到新铺设的标准轨道和曲线（WillowKoishi）
- 车窗流顶部车厢条支持按住左键拖动换车。拖动时同步更新主预览，松开时选定车厢，短距离往返拖动时减少焦点跳动（WillowKoishi）
- 车窗流主预览支持从车体上开始拖动，短按松开仍可打开部件选单。镜头松手后可继续惯性滑动，越过预览阈值时切换至相邻车厢（WillowKoishi）
- 韶山 7D 前转向架接入机车转向架样式及 Simurail 的机车转向架分类，添加双轴几何与对应的车辆运行参数（WillowKoishi）
- 韶山 7D 前转向架添加车轮转动和牵引杆随动。牵引杆在构架端转动，并沿车体导向件滑动，保持杆件长度（WillowKoishi）

修改机制:

- 车窗流切换车厢时从当前镜头位置平滑过渡，逐步改变车厢高亮与外壳显示，并按镜头偏移更新可见车厢范围（WillowKoishi）
- 物理车钩接入 Simurail 0.1.4 的可连挂判定和伙伴查询接口，校验双方的连挂关系，并同步更新 CRH1A 内风挡的物理连挂渲染（WillowKoishi）
- 明确创造物品栏中主分类、动车组、普速客车、机车与餐饮分类的排列关系，调整普速客车及餐饮分类的图标来源（WillowKoishi）

修复问题:

- 修正道砟材质同步后的客户端渲染刷新，在读取新材质后更新直轨模型和曲线道床缓存（WillowKoishi）

修改名称:

- 简体中文中将 CRH2A「头车」改称「车头」，将转向架分类「动车转向架」及「蒸汽转向架」分别改称「动车/拖车转向架」和「蒸汽机车转向架」（WillowKoishi）
- 简体中文中将「通用自动车钩」改称「通用詹氏车钩」（WillowKoishi）

新增 / 修改美术资源:

- 补充韶山 7D 整车、前转向架及中间转向架的源模型和配套贴图。中间转向架本阶段为美术资源（AlpacaEgg、WillowKoishi）
- 将 AlpacaEgg 早期制作的车轮源模型重新纳入韶山 7D 素材目录（WillowKoishi, AlpacaEgg）

移除内容:

- 移除已不存在的接触网实体（`catenary_base`、`small_catenary_base`）、接触网连接操作以及部分闸机提示等遗留简体中文翻译条目（WillowKoishi）

其他:

- 将 Simurail 依赖版本更新为 0.1.4+mc1.21.1，可选兼容范围调整为 0.1.4（含）至 0.2（不含）（WillowKoishi）
- 在车型编号牌的简体中文界面标明十六进制颜色以 0x 开头（WillowKoishi）

---

### version 0.10.5-Shiroko-bata d.1535
##### 2026.11.19

修改机制:

- CRH1A 客室门及 CRH2A 客室门（含宽体门）的开关动画统一为 100 游戏刻（正常刻速下为 5 秒），世界方块与组装车厢按相同步长更新（WillowKoishi）

修复问题:

- 修正 CRH1A 塞拉门的开启轨迹，门扇先向外移出，再沿车身滑动，同步调整模型渲染范围（WillowKoishi）
- 在区块加载或相邻方块变化时检查旧 CRH1A 客室门，自动补回缺失的成员方块或修复失效的归属，保留原门和占位的其他方块（WillowKoishi）
- 修正旋转 CRH1A 客室门结构时成员方块指向门主体的偏移（WillowKoishi）

新增 / 修改美术资源:

- 添加含信号箱、信号灯及控制器的信号设备源模型（hexieautusimuroute.bbmodel）（WillowKoishi）
- 添加含 25G 地板分组的车厢部件源模型（wow.bbmodel）（WillowKoishi）

其他:

- 将 macOS 的 .DS_Store 文件加入 Git 忽略规则（WillowKoishi）

---
### version 0.10.4-Shiroko-bata d.1532
##### 2026.11.16

添加方块（组）:

- CRH1A 客室门（WillowKoishi）
- CRH1A 端面，包含地板、半永久车钩及内风挡（WillowKoishi）

添加功能:

- CRH1A 客室门添加塞拉开关动画及左右侧变体，分别处理固定门框、移动门扇与开关状态下的选取和碰撞（WillowKoishi）
- CRH1A 客室门任一组成方块收到红石信号时，同步整组门的供电与开关状态，并播放对应声音（WillowKoishi）
- 组装后的 CRH1A 客室门按游戏刻保存及更新动画进度，重建显示后恢复当前姿态，完全开启时更新通行碰撞（WillowKoishi）
- CRH1A 端面接入半永久车钩连挂与内风挡变形，风挡随相邻车厢的相对位置和姿态调整（WillowKoishi）
- CRH1A 端面接入 Simurail / Sable 物理车钩与风挡渲染，使用两侧车体的插值姿态计算连接关系（WillowKoishi）

修改机制:

- 完善 CRH1A 客室门的多方块蓝图放置、成员方块交互与组装后选取，校验伸出门扇的交互距离及遮挡（WillowKoishi）
- 为 CRH1A 端面补充独立的玩家碰撞外壳及渲染范围，适配世界及组装车厢中的显示（WillowKoishi）

修复问题:

- 修正 CRH1A、CRH2A 无窗 / 小窗 / 宽窗面板及 CRH2A 加厚无窗面板的物品栏预览朝向与位置（WillowKoishi）
- 调整 CRH1A、CRH2A 头车和驾驶台，以及 CRH2A 驾驶座的物品栏图标缩放与偏移（WillowKoishi）
- 调整 CRH1A、CRH2A 端面与裙板、CRH2A 自动车钩及沙库车钩的物品栏显示位置与比例（WillowKoishi）

新增 / 修改美术资源:

- 补充 CRH1A 客室门、端面、半永久车钩与内风挡的源模型、拆件模型、材质及贴图（WillowKoishi）
- 为 CRH1A 客室门，以及 CRH2A 客室门、宽体客室门和司机室门补充物品栏专用显示配置，调整预览角度、缩放与偏移（WillowKoishi）
- 为 CRH1A、CRH2A 端面物品补充左右手的第一、第三人称显示配置，调整持握角度、缩放与偏移（WillowKoishi）
- 添加动车组停车位置标识牌及另一组铁路标识牌的整体源模型；本阶段为美术资源（Lacus-Mort1s）

移除内容:

- 清理未采用的车轮源模型、配套贴图及车顶设备源模型（`art_assets/train_collection/` 下的 `wheel.png`、`！？wheelwheel？！.bbmodel` 及 `车顶设备.bbmodel`）（AlpacaEgg）

其他:

- 添加 CEE 通用兼容代码的专用服务器侧引用检查，并接入 check 及 jar 任务，防止通用逻辑引用客户端渲染类（WillowKoishi）

---
### version 0.10.2-Shiroko-bata d.1531
##### 2026.11.15

添加方块（组）:

- CRH1A 宽窗面板（WillowKoishi）
- CRH1A 无窗面板、小窗面板（FlycatFeimao）
- CRH1A 地板、裙板（WillowKoishi）
- CRH1A 中部车顶、侧车顶（WillowKoishi）
- CRH1A 驾驶控制台（WillowKoishi）
- CRH1A 头车，包含整体外壳及配套承重地板（WillowKoishi）
- 沙库车钩（Scharfenberg）（WillowKoishi）

添加功能:

- 使用机械动力扳手右键普通和谐转向架顶部，打开转向架选择界面，支持分类、分页、模型预览及有效轴距、轨道类型显示（HachimiYang）
- CRH1A 地板支持使用和谐扳手分别切换左右两侧的转向架裙板，支持世界方块及组装车厢上的操作（WillowKoishi）
- CRH1A 宽窗面板接入车身文字、CRH 标识及电子显示附件交互，并调整标识的贴合深度与高度（WillowKoishi）
- CRH1A 驾驶台添加单手柄控制，支持目标速度递增 / 递减、前进驱动位、常用制动及紧急制动，速度调整位松开后回到驱动位（WillowKoishi）
- CRH1A 详细驾驶模式支持 W / S 操作手柄、R 切换行车方向，也可直接操作控制台；换向须停车且手柄处于空挡或制动位（WillowKoishi）
- CRH1A 头车添加整流罩开合与沙库车钩伸缩动画，关闭整流罩时先解除连挂并收回车钩（WillowKoishi）
- 既有驾驶台物品放置时同时安装配套驾驶座，放置前检查两处空间及权限，安装后保留为独立方块（HachimiYang）
- 同一编组内的驾驶台同步牵引、制动及换向请求，按驾驶台朝向换算行车方向，仅同步本次操作改变的控制项（HachimiYang）
- 同一编组的驾驶台共享时刻表及执行进度，支持同步暂停、恢复和更换，并区分共享副本与实际时刻表物品（HachimiYang）
- 连挂后存在多份不同的时刻表时保留各表、暂停自动执行并显示提示，解决冲突后恢复共享执行（HachimiYang）

修改机制:

- 转向架选择由服务端校验操作权限及轨道适配性，切换样式时保留原有转向架数据与调节设置；选择界面可在未安装 Simurail 时使用（HachimiYang）
- 调整多驾驶台控制仲裁，由最近操作的已启用驾驶台提供运行模式及牵引输入，各驾驶台的制动请求取最大值（HachimiYang）
- 完成机械连挂后统一将相连编组的牵引归零、换向回中并保留制动状态，避免沿用连挂前的牵引请求（HachimiYang）
- 为 CRH1A 中部车顶适配沿车辆朝向连续放置的规则（WillowKoishi）
- 优化 CRH2A 头车及端面外壳碰撞查询，缓存地板形状并跳过不含车体部件的区块分段，避免碰撞查询加载缺失区块（FlycatFeimao）
- 普通世界中的车体面板改用地形分段网格渲染，减少首次放置时在渲染线程创建 Flywheel 平滑光照及透明渲染程序的开销（FlycatFeimao）
- 分离 CEE 受电弓的通用移动逻辑与客户端渲染适配，修正组装状态下底座渲染使用的空间变换（HachimiYang）

修复问题:

- 修正 Sable 子世界中驾驶位的世界坐标锚点及第三人称视角插值，保留座垫偏移并减少远端存储坐标带来的精度误差（HachimiYang）
- 修正曲线道床与 RailX 柔性轨道、坡道端点的衔接，并在远端区块未加载时保留上次识别的端点类型（HachimiYang）
- 完善车辆快照归属恢复及列车删除时的数据交接，快照暂不可用时保留原生车厢载荷，避免恢复错误车厢或残留已删除的编组成员（HachimiYang）

新增 / 修改美术资源:

- 补充 CRH1A 面板、地板、裙板、车顶、驾驶台、头车及沙库车钩的源模型、游戏模型、材质与贴图（WillowKoishi, FlycatFeimao）
- 补充 DF11G 重制头车模型与贴图、HXD3D 重制源模型，并调整 CRH2A 适配版头车及 CW-2 转向架模板源模型；本阶段为美术资源（WillowKoishi）

移除内容:

- 清理 `art_assets/CRH1/` 目录下临时遗留的说明文档 `README.md` 与面板导出脚本 `export_panels.py`（FlycatFeimao、WillowKoishi）

其他:

- 将 CEE 可选依赖的版本范围由固定 1.21.1-1.1.4 调整为 1.21.1-1.1.2 及以上（HachimiYang）
- 补充转向架选择界面所用 Simurail 代码及资源的 MIT 许可与第三方声明（HachimiYang）

---
### version 0.10.1-Shiroko-bata d.1531
##### 2026.11.15

添加方块（组）:

- CRH1A 动车转向架（WillowKoishi）
- CRH1A 拖车转向架（WillowKoishi）

添加功能:

- 新转向架加入现有动车组样式选择及 Simurail 动车组分类，并接入对应几何与车辆运行参数（WillowKoishi）
- 为 CRH1A 转向架添加空气弹簧、牵引杆、抗蛇行及垂向减振器、抗侧滚连杆等部件的随动渲染（WillowKoishi）
- CRH1A 转向架支持普通渲染、Flywheel 实例化及反向设置（WillowKoishi）

修改机制:

- 调整空气弹簧的随动方式，按车体连接点位移平移，保持相对架体的朝向和尺寸（WillowKoishi）
- 扩展减振器活塞与套筒的轴向伸缩计算，保持连接位置及原有重叠关系（WillowKoishi）

修复问题:

- 修复 Sodium 丢弃透明四边形后顶点缓冲索引未重置的问题（WillowKoishi）

新增 / 修改美术资源:

- 补充 CRH1A 动车、拖车转向架源模型、游戏拆件模型、材质与贴图（WillowKoishi）
- 补充 CRH1 头车制作过程的延时图像资源；本阶段为制作素材（WillowKoishi）
- 添加车顶设备源模型；本阶段为美术资源（AlpacaEgg）

- 添加 CRH1A 头车总成源模型；本阶段为美术资源（WillowKoishi）
- 补充 CRH1A 裙板、地板、宽窗 / 小窗 / 无窗面板、车顶及驾驶台、头车贴图（WillowKoishi）

其他:

- 添加 CRH1A 转向架拆件导出、校验脚本及接入说明文档（WillowKoishi）
- 将界面切换提示中的“车窗流”调整为“车厢流”（WillowKoishi）

---
### version 0.9.6-Plana-bata d.1529
##### 2026.11.13

添加功能:

- 接入 CEE（Create: Electro Energetics）可选兼容，使本模组受电弓参与接触网检测并同步升降状态（misaka10843）
- 快照车厢同步 CEE 受电弓、电机标记与电力列车音效配置（misaka10843）
- CEE 电机车厢在接触网电压满足要求时获得外部供电功率倍率，可通过 linePowerMultiplier 配置，默认 1.5（misaka10843）
- CEE 受电弓按和谐车顶的实际高度贴合安装，同步调整静态及组装状态下的模型、选取框与电气接触点（misaka10843）

修改机制:

- 添加可供其他兼容集成注册的外部牵引供电来源接口（misaka10843）
- 将外部供电运行状态统一为供电车厢集合与功率倍率，替换原有运行回调（misaka10843）

修复问题:

- 统一滑动门、折叠门在组装及拆解时的模型可见性；拆解时按开关状态恢复可见性并清除供电标记（WillowKoishi）
- 修正车厢运动部件无方块实体时的空 NBT 处理，兼容旧运行数据中的空方块实体标签（WillowKoishi）
- 修复 CEE 受电弓在车厢尚未绑定完成时执行移动逻辑导致的报错（misaka10843）
- 车辆重定位恢复补充越界异常处理，在目标轨道暂不可用时保留原保存状态（misaka10843）

新增 / 修改美术资源:

- 添加三角形、圆形、菱形及长方形铁路标识牌源模型；本阶段为美术资源（Lacus-Mort1s）
- 添加车轮源模型及配套 wheel.png 贴图；本阶段为美术资源（AlpacaEgg）
- 添加“椎名锘”角色图片素材（AlpacaEgg）
- 添加“桐铁”Logo 图片素材（AlpacaEgg）

其他:

- 在航空学测试运行配置中加入 Copycats+ 3.0.11 依赖（WillowKoishi）
- 补充 CEE 可选依赖声明与编译依赖，并支持通过 withCee 开发配置加载运行依赖（misaka10843）

---
### version 0.9.5-Plana-bata d.1527
##### 2026.11.11

添加功能:

- 为和谐扳手添加操作说明，并在可编辑的客室门、端面、车顶及车厢号屏上提示使用和谐扳手编辑（HachimiYang, WillowKoishi）
- 为 CRH2A 头车、驾驶台、座椅、电子水牌、车厢号屏、CRH 标识及调车信号机补充本地化说明与操作指引（WillowKoishi）
- 物品说明统一在悬停时展示，并使用机械动力的自动换行、配色与重点高亮（WillowKoishi）

修改机制:

- 和谐扳手添加主手攻击伤害与攻击速度属性（WillowKoishi）

修复问题:

- 修正车辆快照对方块实体的校验，允许门上半部等实际没有方块实体的状态被正常捕获（WillowKoishi）
- 修正 25 系列及刷绿车厢速度标文字在上沿半砖上的垂直对齐（WillowKoishi）

新增 / 修改美术资源:

- 更新和谐扳手的源模型、OBJ 游戏模型、材质及物品贴图（WillowKoishi）
- 修正 25T 裙板及左右侧裙板模型的垂直位置（WillowKoishi）
- 调整 25G、CR200J 直排厕所模型，并更新 CR200J 对应贴图（WillowKoishi）
- 调整车厢半砖模型（WillowKoishi）
- 添加“奋进”铁道少女角色图片素材（AlpacaEgg）


其他:

- 更新开发版本号至 0.9.5-Plana-bata；本次提交仅修改版本配置（HachimiYang）

---
### version 0.9.4-Plana-bata d.1526
##### 2026.11.10

添加方块（组）:

- CRH2A 物理化鱼头，用于 Simurail / Sable 物理化车辆（HachimiYang）

添加功能:

- 非统型 CRH2A 驾驶台详细驾驶模式添加键盘操作：W / S 调整换向，A / D 增减牵引功率，分号 / 单引号减小或增加制动力（HachimiYang）
- 驾驶台手柄支持长按按键逐档调整，同时按下相反方向按键时停止步进，并显示按键与当前档位提示（HachimiYang）
- Simulated 及 Sable 普通组装自动包含完整的物理化头车；结构损坏或未完全加载时阻止组装（HachimiYang）
- 既有车钩接入 Simurail 物理连挂与约束，支持红石信号及手动解钩控制（HachimiYang）
- 物理化头车的转向架控制块接入 ComputerCraft 外围设备接口，安装对应模组时启用（HachimiYang）
- 车窗流预览区域支持在空白处按住鼠标左键拖拽平移，并按当前视角沿可见轨道方向移动（WillowKoishi）
- 预制蓝图支持配置独立图标及车型名称，取出到物品栏后保留图标数据（WillowKoishi）

修改机制:

- 将普通 CRH2A 头车的内置转向架设置归入头车数据，并迁移旧头车挂载 DT206 的蓝图及车辆快照（HachimiYang）
- 拆分机车与蒸汽转向架样式分类，兼容旧 DF11G、前进型及反向样式的存档数据（HachimiYang）
- 整理转向架、轨道的公共注册与渲染接口，调整 Create、RailX 及 Simurail 的适配实现（HachimiYang）
- 统一操作部件声明的键盘、鼠标与滚轮输入，完善世界、Create 载具及 Sable 子世界中的驾驶台接管与退出（HachimiYang）
- 调整物理化头车侧面放置及朝向变化时的结构定位，保留控制块设置（HachimiYang）
- 补充 Sable 子世界中渲染特效的空间变换与实例共享（HachimiYang）

修复问题:

- 修复物理车钩清理时触发未加载或正在卸载区块的重新加载，导致保存世界卡住的问题（HachimiYang）
- 修正 25B、25G、25K、25T、25Z 预制蓝图内容，以及车窗流中转向架、车门、地板、座椅等部件分组（WillowKoishi）
- 修正文本编辑界面的输入焦点处理，避免界面输入触发游戏或容器快捷操作（HachimiYang）
- 修正 Iris 下电子水牌文字与玻璃的绘制顺序（HachimiYang）

新增 / 修改美术资源:

- 添加 25B、25G、25K、25T、25Z 蓝图图标，并调整 25K 车厢内外预览结构（WillowKoishi）

移除内容:

- 整理转向架与轨道公共接口后，移除旧版 SPI 后端加载器及服务配置文件（`BogeyBackend`、`BogeyBackendLoader`、`BogeyAnimationBackend`、`RailwayBackend`、`RailwayBackendLoader` 及 `META-INF/services/` 对应配置）（HachimiYang）
- 迁移车窗流配置文档时移除旧路径文档 `docs/carriage-browser-example.md`（HachimiYang）

其他:

- 整理 Gradle 构建配置，补充 Simulated、Sable Companion 及 ComputerCraft 等兼容模块的编译依赖（HachimiYang）
- 迁移并更新车窗流（Carriage Flow）资源包配置文档（HachimiYang）


---
### version 0.9.3-Plana-bata d.1525
##### 2026.11.9

修改名称:

- “和谐号标识”更名为“CRH 标识”，同步动车组分类图标、创造物品栏及相关工具引用（WillowKoishi）

新增 / 修改美术资源:

- 添加 CRH 标识的物品图标及配套贴图（WillowKoishi）

移除内容:

- 随「和谐号标识」更名为「CRH 标识」，移除旧版 `HarmonyLogoItem` 类及 `item.hexie.harmony_logo` 语言键（WillowKoishi）

其他:

- 添加文言文语言文件（lzh）（WillowKoishi）

---
### version 0.9.0-Plana-bata d.1524
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

移除内容:

- 移除旧版客户端覆层框架及转向架检查覆层（`BaseOverlay`、`BogeyCheckOverlay`、`HexieOverlay`、`OverlayManager`）（HachimiYang）
- 统一表面选取与交互后，移除旧版车门／车钩独立选取类与 Mixin（`CouplerInteractionPicking`、`DoorInteractionPicking`、`ShiftedCouplerInteraction`、`MixinDoorPicking`）及旧车厢位置校正数据包 `CarriagePositionCorrectionPacket`（HachimiYang）

---
### version 0.4.14-A.R.O.N.A. d.1523
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

移除内容:

- 移除旧版曲线道床 Flywheel 实例化渲染与自定义着色器（`CurveBedInstance`、`CurveBedShader` 及 `curve_bed` 系列着色器文件）（HachimiYang）
- 重构驾驶台控制接口时，移除旧版 CRH2 驾驶台拖拽与测试驾驶台客户端实现（`Crh2CabPresentation`、`Crh2CabSliderTrack`、`Crh2CabDrag`、`TestCabClient`、`DrivingConsoleCamera`）及对应数据包（`Crh2CabHandlePacket`、`TestCabActionPacket`）（HachimiYang）

---
### version 0.4.13-A.R.O.N.A. d.1521
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

移除内容:

- 移除 `art_assets/CRH2A/bogey/dt206/` 目录下的冗余贴图 `dt206_frame.png`（WillowKoishi）

其他:
- 完善构建产物中的项目、字体、第三方及美术许可声明（HachimiYang）
- 构建时同时生成代码源码包和对应的美术源码包（HachimiYang）

---
### version 0.4.12-A.R.O.N.A. d.1520
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
### version 0.4.11-A.R.O.N.A. d.1518
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

移除内容:

- 移除 8 个既有方块及其代码、模型、贴图、战利品表与相关配方：单臂受电弓（`ss8_pantograph`）、月台（`platform_line_block`）、禁止双弓标志牌（`no_double_pantograph`）、断标志牌（`switch_off_sign`）、合标志牌（`switch_on_sign`）、直盲道（`tactile_paving_straight`）、点盲道（`tactile_paving_pin`）与日光灯（`flourescent_light`），同步移除混凝土及盲道、断合标转换配方（WillowKoishi）
- 移除 3 个既有物品及其完整交互与界面系统：一些钉子（`pins`）、可动方块控制器（`animatable_block_controller`）与自动控制联轴器（`automatic_control_coupling`），一并删除对应的遥控交互框架、GUI 界面、容器菜单、物品栏同步包及配方（WillowKoishi）
- 清理开发过渡期的验证车钩（`test_coupler`）与旧车钩转换器（`coupler_adapter`）模型与战利品表、旧前进型转向架顶盖模型 `qj_top.json`、旧转向架几何配置 `bogey_models.json` 与 `HexieBogeyGeometries`、废弃的 `MixinCurvedBallastInteraction` 及旧版 `CRH2HeadRenderer`（HachimiYang）
- 移除 `sign_fonts/` 目录下的重复字体许可文本文件及误置于转向架目录的 `crh2a_head.bbmodel`，并清理迁移前旧路径的国铁地面站牌与道口信号源模型（WillowKoishi、AlpacaEgg）

其他:
- 更新项目介绍，说明跨越 0.3.9n 资源来源及 Create / Simurail 兼容关系（WillowKoishi）
- 补充美术资源的 GPL-3.0-only 许可及适用范围说明（WillowKoishi）

---
### version 0.4.10-A.R.O.N.A. d.1513
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
### version 0.4.9-A.R.O.N.A. d.1512
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

移除内容:

- 清理旧版轨道组装覆盖贴图（`track_assembly_overlay.png`／`.xcf`）、标准轨方块状态生成器 `StandardTrackBlockStateGenerator`、旧构建转换器 `BuilderTransformers` 及旧标签注册类 `TagsInit`（WillowKoishi）

其他:
- 添加航空学兼容测试使用的客户端运行配置（WillowKoishi）
- 添加持续集成构建配置（WillowKoishi）
- 补充第三方版权声明（HachimiYang）

---
### version 0.4.8-A.R.O.N.A. d.1511
##### 2026.10.26

添加功能:
- 接入 Simurail 转向架兼容（HachimiYang）

修改机制:
- 整理车厢、车门、座椅、标牌等方块的公共逻辑（WillowKoishi）
- 调整方块实体数据保存、交互及渲染实现（WillowKoishi）

修复问题:
- 修复前进型转向架连杆动画（HachimiYang）
- 修复信号机贴图透视问题（WillowKoishi）

移除内容:

- 清理未使用的字体样式工具 `HexieFontStyle`、蓝图物品工具 `SchematicItemUtil` 及配置事件类 `ConfigEvents`（WillowKoishi）

其他:
- 添加并固定 Simurail、Aeronautics 及 Sable 的开发依赖（WillowKoishi）

---
### version 0.4.7-A.R.O.N.A. d.1510
##### 2026.10.25

修复问题:
- 修复国铁过票闸机的渲染及崩溃问题（WillowKoishi）
- 修复信号机朝西放置时的朝向问题（WillowKoishi）
- 修复轨道重复绑定及放置交互问题（WillowKoishi）

修改机制:
- 调整信号机、轨道物品的放置状态与客户端同步（WillowKoishi）

移除内容:

- 移除 4 个旧版地面信号机方块及其代码、模型、贴图、战利品表与语言条目：进站信号机（`station_entrance_signal`）、旧版调车信号机（`shunting_signal`）、信号机柱（`signal_pole`）与柱上信号机（`signal_pole_light`）（WillowKoishi）

---
### version 0.4.6-A.R.O.N.A. d.1510
##### 2026.10.25

版本迁移:
- 将既有铁路装饰内容迁移至 Minecraft 1.21.1 / NeoForge（WillowKoishi, FlycatFeimao）
- 使用 Java 21，适配 Create 6.0.10 及相关渲染、注册接口（WillowKoishi, FlycatFeimao）
- 迁移车厢、转向架、信号机、售票及标牌等既有内容（FlycatFeimao, WillowKoishi）

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

移除内容:

- 迁移至 1.21.1 / NeoForge 时移除原有接触网系统全部方块、物品与实体逻辑，包括新旧水泥／工字钢接触网柱、A／B／C／D 型接触网支架与转接器、龙门架及挂架、绝缘子与悬架、坠砣与配重、非接触网线缆挂点、创造发电机，以及接触网挂线线轴、电缆线轴、钢缆线轴与剪线钳（FlycatFeimao、WillowKoishi）
- 将 DF11G「跨越」涂装车棚与下厢板（`df11g_carport_kuayue`、`df11g_panel_kuayue`）替换为「和谐」版本，并移除旧重复硬座注册项 `yz_seat2`（WillowKoishi、FlycatFeimao）

其他:
- 补全 Gradle Wrapper（WillowKoishi）

---
### version 0.3.9-izayoi_sakuya-rc1 d.554
##### 2024.3.13

修改机制:

- 修改通过信号机信号灯的渲染类型，并扩大信号灯的渲染范围（Zlw380）

移除内容:

- 彻底删除此前停用的旧版色灯信号机相关源码与注册项，包括 `KYSemaphoreBlock`、`KYSemaphoreBlockEntity`、`KYSemaphoreItem`、`KYSemaphoreRenderer` 及 `KYEdgePointTypes`（Zlw380）
- 移除转向架径向快捷选择菜单及其服务端／客户端处理与网络包（`RadialBogeyCategoryMenu`、`BogeyCategoryHandlerClient`、`BogeyCategoryHandlerServer`、`BogeyStyleSelectionPacket`）和压路机替换轨道扩展 `TrackReplacePaver`（Zlw380）
- 清理不再使用的轨道／转向架／网络 Mixin 与底层工具类，包括 `MixinBogeyInstance`、`MixinTrackRenderer`、`MixinTrackTargetingClient`、`MixinAbstractBogeyBlock`、`ServerGamePacketListenerMixin`、`ClientPacketListenerMixin`、`PacketsInit`、`PacketSet`、`C2SPacket`、`OverridableSignalPacket`、`RegistrationListening` 及 `TextUtils`（Zlw380）

新增 / 修改美术资源:

- 调整耐候钢锭与耐候钢板的物品贴图（WillowKoishi）

其他:

- 更新 `mods.toml` 中的模组主页地址，将 Create 依赖更新至 0.5.1.f-332，并将开发版本号更新为 `0.3.9-izayoi_sakuya-rc1`（Zlw380）

---
### version 0.3.8-izayoi_sakuya d.532
##### 2024.2.20

修改机制:

- 规范化并重制资源读取方式，降低资源加载异常的发生概率，并进一步减少 Mixin 使用（MegumiKasuga）

移除内容:

- 移除客户端覆层 Mixin（`MixinForgeGui`）并删除冗余工具类 `PlayerFocusingBlockPredicateUtil`（MegumiKasuga）

---
### version 0.3.8-izayoi_sakuya d.529
##### 2024.2.17

修改机制:

- 获取转向架信息暂时不再需要佩戴工程师护目镜（MegumiKasuga）
- 为新版接触网支架补充操作工具提示（MegumiKasuga）
- 调整可动方块交互相关的 Mixin 实现（MegumiKasuga）

修复问题:

- 修复 SW160 转向架与前进型转向架的模型及贴图显示问题（MegumiKasuga）

移除内容:

- 移除 `MixinSimpleBlockMovingInteraction`，减少对机械动力可动方块交互的 Mixin 侵入（MegumiKasuga）

---
### version 0.3.8-izayoi_sakuya d.528
##### 2024.2.16

添加方块（组）:

- 统型 CR200J 车头、动力车门、拖车车门与滑动门、车梯、滑动门／转轴门端面、直排便池、宽窗／密封宽窗／密封小窗／厕所窗、车头弧线／复兴号／CR 标志／无黑带／弧线过渡段等上中下厢板、裙板、侧裙板、车棚与中央车棚（Desert_Walker、WillowKoishi、MegumiKasuga）
- SW160 转向架（Zlw380、WillowKoishi）
- 无砟轨道、无枕轨道及配套传送门轨道适配（Zlw380、WillowKoishi）
- 通过信号机（Zlw380）
- 创造发电机（MegumiKasuga）
- 耐候钢块及其 6 种锈蚀阶段与对应涂蜡变体（MegumiKasuga、WillowKoishi）
- 原色 25Z 车厢地板样式地毯（MegumiKasuga）

添加物品:

- 可动方块控制器、自动控制联轴器（MegumiKasuga）
- 耐候钢锭、耐候钢粒（MegumiKasuga）
- 标准轨、无砟轨道与无枕轨道半成品（HTony03）

添加功能:

- 实现可动方块自动遥控与联轴器绑定系统，支持对车门与 CR200J 车头整流罩／雨刮器进行实体态遥控（MegumiKasuga）
- 实现接触网供电电网底层与多线程任务队列调度，并补充部分 GUI 覆层显示（MegumiKasuga）
- 补充生存模式基础支持、蓝图桌合成表底层及 XML 解释器，并调整车厢分类侧栏与自动化 RenderType 注册（MegumiKasuga）
- 为矮型色灯信号机补充使用提示，使压路机不会破坏新增轨道类型（Zlw380）

修改机制:

- 以新版接触网替换旧版接触网并废弃旧版接触网系统（Desert_Walker、MegumiKasuga）
- 调整车门与塞拉门开关动画实现方式，统一车厢板采集、镶字与转向架组装前后朝向逻辑（MegumiKasuga、Zlw380）

修复问题:

- 修复方块标签失效、挖掘掉落缺失、服务端物品栏同步及资源读取异常，修正 25 系车厢涂蜡右键音效（MegumiKasuga、WillowKoishi、HTony03）

修改名称:

- 将原「耐候钢（`weathering_resistant_steel`）」与「一小块耐候钢（`weathering_resistant_steel_piece`）」调整为「耐候钢锭（`weathering_resistant_steel_ingot`）」与「耐候钢粒（`weathering_resistant_steel_nugget`）」（MegumiKasuga）
- 将「未完成的标准轨（`uncompleted_standard_track`）」更名为「标准轨（半成品）（`incomplete_standard_track`）」（HTony03）

移除内容:

- 停用并移除依赖于机械动力列车信号机的色灯信号机（`ky_semaphore`）方块与方块实体注册，并清理 `ThroughSignalBlockEntity` 中的冗余信号状态枚举（Zlw380）
- 移除早期旧版 `widepanel_cr200j_marshalled_mid` 方块、方块状态与模型，拆分为多类独立的统型 CR200J 车窗方块（MegumiKasuga）
- 清理 `develop_art_resource/cr200j/垃圾桶/` 目录下的 12 个废案源模型与贴图文件（WillowKoishi）
- 更改车门动画实现后，移除 4 个旧版 CR200J 车门开启状态静态模型（`door_bottom_open`、`door_top_open` 及镜像版本）（MegumiKasuga）
- 移除废弃的 `MixinModelBakery`、旧版战利品表数据生成器（`KuayueLootTableProvider`、`KuayueBlockLoots`）、旧界面按钮控件 `ImageButton`，以及电网多线程重构中废弃的 `ChargedGraphTask`、`ChargedGraphSavedData`、`MergeGraphTask`、`SplitGraphTask`（MegumiKasuga）
- 移除过时的耐候钢压制／合成配方、旧标准轨压制配方及重复的 `ventiduct_25b` 配方，并清理标准轨旧物品贴图 `track_standard.png` 与冗余轨枕贴图（HTony03、MegumiKasuga、Zlw380）

新增 / 修改美术资源:

- 更换调车信号机、标准轨、无砟轨、无枕轨及传送门轨道的模型与贴图（Zlw380、WillowKoishi、Desert_Walker）
- 调整耐候钢锭、耐候钢粒与螺丝钉贴图（MegumiKasuga）

新增 / 修改数据资源:

- 补充并重构大批方块与物品合成表，新增并调整进度（成就）及教程内容（HTony03、WillowKoishi）

---
### version 0.3.6-izayoi_sakuya d.411
##### 2023.10.22

添加方块（组）:

- 前进型蒸汽机车锅胴（WillowKoishi）
- 前进型动轮转向架，包含曲柄、月牙板、连杆、偏心杆、十字头、鞲鞴杆、结合杆、合并杆、汽阀杆、半径杆、压油机、回动机及台架架体联动动画（WillowKoishi、Zlw380）
- 新版接触网 A 型内／外侧支架、C 型 1／2 号支架、D 型内／外侧挂架、绝缘子、绝缘子悬架与新版接触网路端配重（MegumiKasuga）

添加物品:

- 原色 25B、25G、25K、25T、25Z 车厢内置蓝图（MegumiKasuga）

添加功能:

- 完善内置蓝图系统与新版接触网外围接口、悬链线算法、最大斜率／最大连接数限制及剪线钳预览功能（MegumiKasuga）
- 新增客户端 Overlay 覆层渲染框架（MegumiKasuga）
- 支持在转向架渲染器中读取列车速度与运行方向（Zlw380）

修复问题:

- 修复车厢号码牌无法更新、接触网客户端同步异常及椅子无法随组装车厢旋转的问题（MegumiKasuga）

移除内容:

- 删除仅用于信号机测试的 21 个临时 `ky_semaphore` 臂板／灯具模型与贴图资源（Zlw380）
- 删除不再使用的 `data_fixer` 接口体系及旧注册类 `KYDataFixers`（Zlw380）
- 替换为分体模型与新贴图后，移除前进型动轮旧版整体模型 `wheel_group`、`wheel_group2` 及旧贴图 `wheel_group.png`（Zlw380）
- 移除旧版接触网类与网络包（`GeneralHyperbolic`、`GeneralStraight`、`CatenaryGridConnectPacket`、`CatenaryGridInitPacket`、`CatenarySaveDataUpdatePacket`、`IGridModel`）、测试贴图 `test2.png`、旧椅子旋转 Mixin（`AccessorContraptionRotationState`）以及内置思源黑体字体文件 `source_han_sans_cn-medium.ttf` 与配置（MegumiKasuga）

新增 / 修改美术资源:

- 新增 5 种接触网路端配重模型并完善配重三种状态显示，修改三种接触网线轴的物品贴图与中文翻译（MegumiKasuga）

其他:

- 新增英文版开发日志 `DevLog_EN.md`（HTony03）

---
### version 0.3.5-izayoi_sakuya d.369
##### 2023.9.10

添加方块（组）:

- 原色 25Z 车厢完整组件，包括粗腰线／细腰线／厕所外墙下厢板、车厢一侧中厢板、席位等级标志窗、宽窗／小窗／车厢一侧小窗／厕所窗、车厢门、直排式便池、上端面（普通／空调端）、车棚、中央车棚及车厢空调（Desert_Walker、MegumiKasuga、WillowKoishi）
- 色灯信号机（`ky_semaphore`）原型（Zlw380）

添加物品:

- 一些钉子、一小块耐候钢、耐候钢棒、钢线与未完成的标准轨等合成材料（HTony03）

添加功能:

- 完成内嵌蓝图系统基础功能，并使蓝图可在创造物品栏中取用（MegumiKasuga）
- 实现全部车窗与车门开启交互及开关音效，支持车梯开关及使用带颜色的刷子切换 25Z 席位等级与中厢板数字（MegumiKasuga、WillowKoishi）

修复问题:

- 修复 25Z 车门渲染与开启异常、行李架渲染问题、车厢类型牌渲染问题以及斧头挖掘标签丢失的问题（MegumiKasuga）

移除内容:

- 隐藏 `ky_standard_bogey` 转向架方块的独立物品注册，改为通过转向架样式切换选用（MegumiKasuga）
- 将 25Z 下厢板拆分为多种变体后，移除旧版 `panel_25z_original_bottom` 方块及模型（MegumiKasuga）
- 精简合成材料物品注册，删除冗余类 `mixture.java` 与 8 个独立合成材料物品类，并移除旧 `ItemInit.java`、`TrainPanelItem.java`、`Env.java` 及临时 `Config.java`（MegumiKasuga、HTony03、Zlw380）
- 清理未使用的音效 `beep_ticket_vendor.ogg`、旧物品贴图 `train_panel_sign_item.png` 以及错误的原版 `minecraft:signs` 方块标签文件（MegumiKasuga）

新增 / 修改美术资源:

- 更新 25Z 车梯贴图并补充 25T 动态图标，修正 25Z 席位等级模型与中厢板贴图位置（MegumiKasuga、WillowKoishi）

新增 / 修改数据资源:

- 补充多项方块与物品合成表、中文翻译与进度（成就）内容，整理合成材料目录结构（HTony03）

其他:

- 引入 GitHub Actions 构建与 Google Java Format 自动化工作流（Yjn024）

---
### version 0.3.2-izayoi_sakuya d.358
##### 2023.8.30

添加方块（组）:

- 原色 25Z 车厢下／中／上厢板、端面、车梯与地板（WillowKoishi、MegumiKasuga）
- 电动围栏、电动围栏门（MegumiKasuga）
- 合并 Lead Beyond（引向超越）内容：软座席、硬座、硬座车小桌板、行李架、闸机、国铁闸机、售票机（WillowKoishi、MegumiKasuga、Radekemia Haruka）
- 车种标签、车厢号码贴纸、水牌贴纸与车厢速度等级贴纸方块（MegumiKasuga）
- 浦镇 209P 转向架（含无轴发及反向版本）、SW220K 转向架（含反向版本）、DF11G 转向架完善版本（Zlw380、WillowKoishi）

添加物品:

- 耐候钢、耐候钢板、绿色刷子（HTony03）
- 刷子、带颜色的刷子、列车运行区段指示牌（水牌）、贴纸（MegumiKasuga）
- 合并 Lead Beyond 内容：单程票（后更名为乘车报销凭证）、红票、储值卡（MegumiKasuga）

添加功能:

- 支持创建、编辑、复制粘贴及随机生成车厢号、车厢编号、水牌与车厢速度标，提供颜色修改界面与预设，并补全生存／冒险模式适配与粒子音效（MegumiKasuga）
- 支持使用扳手右键转向架底部调整转向架朝向，适配 Create: Steam 'n' Rails（汽鸣铁道）轨道渲染（Zlw380）
- 支持在售票机上绘制线路图并购买指定区间车票（MegumiKasuga）
- 整理创造物品分类与车厢板排列顺序（MegumiKasuga、WillowKoishi、HTony03）

修复问题:

- 修复转向架在组装列车时反方向渲染、剪贴板失效及创造物品栏异常等问题（Zlw380、MegumiKasuga）

修改名称:

- 将「单程票」更名为「乘车报销凭证」（MegumiKasuga）

移除内容:

- 废弃并在创造物品栏中隐藏旧版刷绿 25B 窗户（`panel_25b_marshalled_window`）与刷绿 25 系列窗户（`panel_25_marshalled_window`）（MegumiKasuga）
- 移除早期未采用的矿物混合物翻译键 `item.kuayue.mineral_mixture`、冗余键 `block.kuayue.Carrier` 及旧工具提示键 `item.kuayue.colored_brush.tooltip`（HTony03、MegumiKasuga）
- 执行代码整理与架构合并时，删除旧版 `TrainPanelSignBlock`、`TrainPanelSignBlock2`、`TrainPanelSignBlockEntities`、`OrdinaryTrainSkirtPanelBlock`、旧版 `TrainSpeedSign*`、`WoodTypeInit`、`ModSetup`、`PlayerSelection`、旧大写 `Catenary/` 目录类、错误放置于 `c2s` 的 3 个 S2C 数据包、旧 `ElectricFenceDoorLeftModel` 及失效的 `MixinCarriageParticles`（MegumiKasuga）
- 删除测试图片 `genshin.jpg`、旧版模组图标 `kuayue.png`、旧 `cr_tunst_0_L_open.json` 闸机模型及多余的 `sw220k_top_lh.json` 模型文件（MegumiKasuga、WillowKoishi、Zlw380）

新增 / 修改美术资源:

- 修改软座席与硬座贴图，新增座椅模型资源（Desert_Walker）
- 补充 209P、SW220K 与 DF11G 转向架模型，更新模组横幅与水牌物品贴图（WillowKoishi、MegumiKasuga）

新增 / 修改数据资源:

- 补充与修正多批方块、物品及绿色刷子合成表，初步引入进度（成就）系统并校对英文翻译（HTony03）

---
### version 0.3.0-izayoi_sakuya d.336
##### 2023.8.8

添加方块（组）:

- 标准轨距铁轨（Zlw380）
- 长客 CW-2 转向架与 DF11G 转向架初版（Zlw380、WillowKoishi）

修改机制:

- 推进 Minecraft 1.19.2 迁移与轨道／转向架底层构建，使机械动力压路机不会破坏标准轨距铁轨，并禁用早期转向架快捷切换栏（MegumiKasuga、Zlw380）

移除内容:

- 清理轨道与转向架研发初期遗留的重复架构源码（`Bogeyss/`、`Tracks/standard_track/`、`KYStandardBogey*`、`RadialBogeyCategoryMenu`、`MultiLoader/` 旧接口与一批旧轨道 Mixin 等 76 个文件）及 `CustomTrackBlockStateGenerator`（Zlw380）
- 删除测试阶段的白桦木与金合欢木轨道模型、方块状态及贴图（`birch_track` 等 29 个文件）、未使用的道岔／车钩轨道覆盖层模型与贴图（29 个文件）、旧轨枕贴图与无用方块标签（Zlw380）
- 清理 `Blocks/Catenary/` 大写目录遗留的 45 个重复文件，以及旧 `TrainPanelBlock`、`TrackPlacementMixin`、未正常工作的车厢粒子 Mixin（`MixinCarriageContraptionEntity`、`AccessorCarriageParticles`）和旧原版路径贴图 `minecraft/textures/entity/signs/train_panel.png`（MegumiKasuga）

新增 / 修改美术资源:

- 补充 Patchouli 手册贴图与 25Z 转向架白模，调整转向架与旁承座模型（HTony03、WillowKoishi、MegumiKasuga）

新增 / 修改数据资源:

- 补充耐候钢测试代码与部分合成表，修正简体中文缺失翻译（HTony03）

---
### version 0.2.4-Patchouli d.263
##### 2023.5.27

添加方块（组）:

- 单臂受电弓（SS8 受电弓）（MegumiKasuga）

添加功能:

- 引入 Patchouli（帕秋莉手册）辞典框架（WillowKoishi）

修改机制:

- 适配 Create 0.5.1.x 与 Forge 40.2.5，补充单臂受电弓方块标签及跨版本兼容工具（WillowKoishi）

移除内容:

- 删除早期版本中实验失败的 DF11G 假转向架与旧车头方块实体／渲染源码，包括 `DF11GBogeyBlock`、`FakeDF11GBogeyBlock`、`DF11GBogeyTileEntity`、`DF11GFrontTileEntity`、`DF11GFrontInstance`、`FakeDF11GBogeyBehavior`、`FakeDF11GBogeyInstance`、`DF11GTileEntityRenderer`、`FakeDF11GBogeyRenderer` 及 `KYBuilderTransformers`（WillowKoishi）

其他:

- 补全接触网实体中文译名与英文翻译，更新版本 Logo（HTony03、ACS-resources、WillowKoishi）

---
### version 0.2.2-Patchouli d.249
##### 2023.5.13

修改机制:

- 将接触网下垂系数修正为 0.0007，将悬链线下垂系数先后修正为 120（`0.2.1-Patchouli`）与 160（MegumiKasuga）

修复问题:

- 修正入站信号机无法正常工作的问题（MegumiKasuga）

移除内容:

- 清理大小写目录重复引发的 `Blocks/Catenary/` 与旧 `catenary/` 包下 53 个冗余接触网源码文件（MegumiKasuga、WillowKoishi）

其他:

- 更新模组 Logo，并将 0.2.2 特性同步移植至 Minecraft 1.19.2（WillowKoishi）

---
### version 0.2.0-Patchouli d.248
##### 2023.5.12

添加方块（组）:

- 接触网系统方块：水泥接触网柱、工字钢接触网柱及其 45 度转角，接触网柱侧边挂点转接器及其 45 度转角，A 型内／外侧支架及 45 度转角，C 型 1／2 号支架及 45 度转角，A／C 类连接器与转接器，B 型悬挂式支架，1125／1600 规格接触网（含带承力索与挂点变体），接触网路端配重，坠砣与坠砣吊线，四类非接触网线缆挂点，龙门架及其水泥杆、金属杆、连接器与单／双侧接触网挂架及延长杆（MegumiKasuga）
- 月台方块（MegumiKasuga）

添加物品:

- 通用接触网、通用直线、通用悬链线、剪线钳（MegumiKasuga）

移除内容:

- 以新版多规格接触网支架体系替换旧版接触网支架后，移除旧 `catenary_grid`（旧接触网支架）与 `hang_catenary_grid`（悬吊接触网支架）的方块状态、模型及 17 个相关贴图文件，并移除旧物品贴图 `general_catenary.png`（MegumiKasuga）

新增 / 修改美术资源:

- 修改调车信号机模型与贴图（HTony03）

新增 / 修改数据资源:

- 更新部分方块与物品的合成表（HTony03）

---
### version 0.1.2-HoanMeirin d.155
##### 2023.2.8

新增 / 修改数据资源:

- 新增平滑石英竖半砖／1/4 砖／1/8 砖（含切石机配方）、黏土台阶与楼梯、绿色隔离栅栏、盲道、断合标、混凝土、火车餐 A 及 25B/G/K 车厢窗户等多组合成表，并整理配方目录与修正格式错误（HTony03）

其他:

- 更新机械动力 API 依赖与版本号，修正英文翻译并更新 README 进度清单（WillowKoishi、ACS-resources、Steve108）

---
### version 0.1.1-Cirno d.113
##### 2022.12.28

添加方块（组）:

- HXD3D 车头、通用厢板、「和谐」厢板、车棚、地板与驾驶室门（WillowKoishi、Radekemia Haruka）
- DF11G 设备间门、DF11G 机车端面（WillowKoishi）
- 原色 25B、25G、25K、25T 车厢端面与上端面（普通／空调端）、25K／25T 橡胶风挡端面与上端面，以及 25B/G 车厢隔离门（WillowKoishi）
- 原色 25B 登车梯，原色 25K／25T 塞拉门及配套塞拉门登车梯（WillowKoishi）
- 25 系列多类车窗：25B/G/K/T 宽窗、密封窗、错位窗、小窗、密封小窗、厕所窗、25G 砍头窗，25T 金属框／蓝色框车窗及 BSP25T 车窗（WillowKoishi）
- 25B/G/Z/K 车厢空调 1／2、中央车棚、上水箱、25B 车厢通气孔，BSP25T 中央车棚与空调、25T 卫生间排气扇，以及茶炉与原色 25B/G/K/T 直排式便池（WillowKoishi）
- 绿色隔离栅栏及其 45 度转角变体（WillowKoishi）

添加功能:

- 为受电弓添加右键升降弓交互（WillowKoishi）

修改机制:

- 调整车厢板厚度并修改 HXD3D 车头及驾驶室门模型（WillowKoishi）

修复问题:

- 修复新加入车窗的放置与朝向错误（WillowKoishi）

移除内容:

- 将 0.1.1 版本移植至 1.19.2 时移除重复的旧版 `Train25GLadderBlock.java` 源码文件（WillowKoishi）

---
### version 0.0.9-Rumia d.88
##### 2022.12.3

添加方块（组）:

- DF11G 排障器（含碰撞箱）与 DF11G 驾驶室门（WillowKoishi）

修改机制:

- 通过 Mixin 放宽机械动力轨道曲线长度限制，支持铺设更长的曲线轨道（WillowKoishi）
- 重构方块状态与模型 JSON 目录结构（WillowKoishi）

修复问题:

- 解决登车梯引发的闪退及导致上方车门错位的问题，修复 DF11G 驾驶室门无法被正确识别的问题（WillowKoishi）

移除内容:

- 回滚实验性 Fabric 移植尝试，移除临时引入的 `fabric.mod.json`、`kueyue.mixins.json`、`FreeWoodType` 及 Fabric 侧 Mixin 文件（Yjn024）

其他:

- 将 0.0.9 版本同步移植至 Minecraft 1.19.2（WillowKoishi）

---
### version 0.0.8-VIVIT d.79
##### 2022.11.24

添加方块（组）:

- DF11G 机车车头（使用 Create 渲染器渲染）、车棚 1／2／通用／「跨越」变体、散热风机／机械室通风／辅助风滤器三类车棚百叶扇、通用／「跨越」下厢板、通用／1／2 号／路徽中厢板、下／中百叶扇与设备间窗户（WillowKoishi）
- 实验性列车标识牌（WillowKoishi）

添加功能:

- 完善道口扩音器与 25G 登车梯功能（WillowKoishi）
- 新增「普速旅客车厢」与「机车组件」创造物品分类（WillowKoishi）

修复问题:

- 修复登车梯方块刷新异常、DF11G 车头车窗碰撞箱问题及行李 25T 透光窗贴图问题（WillowKoishi）

移除内容:

- 重做登车梯多状态模型时移除旧版 `ladder_25g_original.json` 单态方块模型（WillowKoishi）
- 调整 DF11G 转向架原型方案时移除实验性 `DF11GBogeyInstance` 类（WillowKoishi）
- 整理方块状态与物品模型时移除多余的 `carport_df11g_1.json` 方块状态及旧版 `df11g_front.json` 物品模型（WillowKoishi）

新增 / 修改美术资源:

- 添加 HXD3D 机车源模型与基础贴图资源（Radekemia Haruka）
- 调整 DF11G 部分贴图（WillowKoishi）

其他:

- 调整方块工具提示与中英文翻译，并依次推进 `0.0.7-Elly`、`0.0.7-Mugetsu`、`0.0.8-Alice`、`0.0.8-Yumeko`、`0.0.8-Shinki`、`0.0.8-VIVIT` 等开发版本迭代（WillowKoishi）

---
### version 0.0.7-Kurumi d.37
##### 2022.10.14

添加方块（组）:

- 道口扩音器（道口铃）（WillowKoishi）
- 原色 25G 登车梯（WillowKoishi）
- 东福绿 CR200J 拖车上厢板、车窗、下厢板与地板（WillowKoishi）

其他:

- 补全道口扩音器及 CR200J 拖车方块的中文翻译（WillowKoishi）

呕

---
### version 0.0.6-YumemiOkazaki d.32
##### 2022.10.9

添加方块（组）:

- 高阻绿 25G 标识线、高阻绿 25K 标识线、高阻绿 25 车厢窗户、高阻绿 25T 车厢裙板与侧裙板（WillowKoishi）

新增 / 修改美术资源:

- 调整原色 25T 与原色 25K 车厢窗户贴图（WillowKoishi）

囍报，全刷绿了

---
### version 0.0.6-YumemiOkazaki d.31
##### 2022.10.8

添加方块（组）:

- 日光灯、直盲道、点盲道（MegumiKasuga）
- 刷绿 25 系列车厢门、下厢板、下厢板 b、中厢板、上厢板、上厢板 b、地板，以及刷绿 25B 车厢标识线与窗户（WillowKoishi）

修改机制:

- 为日光灯补充发光效果（WillowKoishi）

新增 / 修改美术资源:

- 调整原色 25B 车厢下厢板、中厢板、上厢板、上厢板 b、窗户与车厢门贴图，并更新模组图标（WillowKoishi）

其他:

- 修复机械动力依赖配置（WillowKoishi）

蛤蛤，开始刷绿咯

---
### version 0.0.5-ChiyuriKitashirakawa d.20
##### 2022.9.24

添加方块（组）:

- 接触网系统：钢制接触网柱、混凝土接触网柱、接触网支架、悬吊接触网支架（MegumiKasuga）
- 地面系统：进站信号机、调车信号机、信号机柱、柱上信号机、禁止双弓标志牌、断标志牌、合标志牌（MegumiKasuga、WillowKoishi）

添加功能:

- 新增「接触网」与「地面系统」创造物品分类（MegumiKasuga）

修改名称:

- 规范部分方块译名并整理代码风格（WillowKoishi）

新增 / 修改美术资源:

- 调整部分方块的模型显示效果（WillowKoishi）

其他:

- 升级 Forge 至 40.1.60，引入 Create（机械动力）与 JEI 依赖，并开始探索 Minecraft 1.19 移植（WillowKoishi）

---
### version 0.0.4-RikakoAsakura d.18
##### 2022.9.23

添加方块（组）:

- 原色 BSP25T 车厢窗户、原色行李 25T 车厢上窗板（WillowKoishi）

新增 / 修改美术资源:

- 调整原色 25T 车厢裙板与侧裙板模型（WillowKoishi）

---
### version 0.0.3-KanaAnaberal d.16
##### 2022.9.21

添加方块（组）:

- 原色 25B 车厢上厢板 b、原色 25T 车厢上厢板 b、原色 25T 车厢下厢板 b（WillowKoishi）
- 原色 25B、25G、25K、25T 车厢地板（WillowKoishi）

新增 / 修改美术资源:

- 配合新增车厢地板，同步调整 25B、25G、25K、25T 四种车厢的下厢板模型与贴图（WillowKoishi）

为此调整了以上4种车厢的下厢板。

准备刷绿了（悲）

---
### version 0.0.3-KanaAnaberal d.10
##### 2022.9.16

添加方块（组）:

- 原色 25B 车厢下／中／上厢板、窗户与车厢门（WillowKoishi）
- 原色 25K 车厢下／中／上厢板、窗户、车厢门、腰线板与标识板（WillowKoishi）
- 原色 25T 车厢下／中／中侧／上厢板、窗户、车厢门、裙板与侧裙板（WillowKoishi）
- 25B/G/Z/K 车厢车棚、25T 车厢车棚（WillowKoishi）

移除内容:

- 清理 Forge 模板遗留文件 `CREDITS.txt`、`changelog.txt` 与旧图标 `kuayue.png`，并移除重复的车棚语言键 `block.kuayue_carport.25bgzk`、`block.kuayue_carport.25t`（WillowKoishi）

~~五提好时代要来力（喜）~~

另外，批量加板子真的累死人了啊

---
### version 0.0.2-Kotohime d.6
##### 2022.9.12

开始实装自己的服务器，大受好评（大嘘）

添加方块（组）:

- 原色 25G 车厢中厢板、上厢板、窗户与车厢门（WillowKoishi）
- 中国铁路路徽、黏土楼梯、平滑石英竖半砖、平滑石英竖 1/4 砖、平滑石英竖 1/8 砖与平滑石英半坡（WillowKoishi）

添加物品:

- 餐车 25T 物品图标（WillowKoishi）

移除内容:

- 删除初始 `README.txt` 文件并改用 `README.md`（WillowKoishi）

其他:

- 初始化项目仓库与结构体、餐饮两个创造物品分类（WillowKoishi）

---
### version 0.0.1-Ellen d.1
##### 2022.9.7

作为试水性的写了一些hello world

添加方块（组）:

- 原色 25G 车厢下厢板、受电弓与黏土台阶（WillowKoishi）

添加物品:

- 火车餐 A（WillowKoishi）

其他:

- 完成早期模组工程搭建与基础自定义方块、物品试水（WillowKoishi）
