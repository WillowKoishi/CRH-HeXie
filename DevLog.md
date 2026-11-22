
# 开发日志

### version 0.11.0-nonomi-bata
##### 2026.11.22

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

其他:

- 将运行日志目录 logs/ 加入 Git 忽略规则（WillowKoishi）

---
### version 0.10.6-Shiroko-bata
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

其他:

- 将 Simurail 依赖版本更新为 0.1.4+mc1.21.1，可选兼容范围调整为 0.1.4（含）至 0.2（不含）（WillowKoishi）
- 在车型编号牌的简体中文界面标明十六进制颜色以 0x 开头，移除部分接触网实体、接触网操作和闸机提示的简体中文条目（WillowKoishi）

---

### version 0.10.5-Shiroko-bata
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
### version 0.10.4-Shiroko-bata
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

其他:

- 添加 CEE 通用兼容代码的专用服务器侧引用检查，并接入 check 及 jar 任务，防止通用逻辑引用客户端渲染类（WillowKoishi）
- 清理未采用的车轮源模型、配套贴图及车顶设备源模型（AlpacaEgg）

---
### version 0.10.2-Shiroko-bata
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

其他:

- 将 CEE 可选依赖的版本范围由固定 1.21.1-1.1.4 调整为 1.21.1-1.1.2 及以上（HachimiYang）
- 补充转向架选择界面所用 Simurail 代码及资源的 MIT 许可与第三方声明（HachimiYang）

---
### version 0.10.1-Shiroko-bata
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
### version 0.9.6-Plana-bata
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
### version 0.9.5-Plana-bata
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
### version 0.9.4-Plana-bata
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

其他:

- 整理 Gradle 构建配置，补充 Simulated、Sable Companion 及 ComputerCraft 等兼容模块的编译依赖（HachimiYang）
- 迁移并更新车窗流（Carriage Flow）资源包配置文档（HachimiYang）


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
