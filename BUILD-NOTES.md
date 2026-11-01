# 构建说明（BUILD-NOTES）· 离线编译方案

> 结论：**不需要联网，也不需要 maven.neoforged.net。**
> 编译期 classpath 直接指向本机整合包 `H:\柳树服beta2\.minecraft`。

## 一、已就绪

| 项 | 来源 | 状态 |
|---|---|---|
| **JDK 21.0.7** | MC 启动器运行时 `%APPDATA%\.minecraft\runtime\java-runtime-delta` | ✅ 免下载 |
| **Gradle 8.10.2** | 腾讯镜像（130 MB，22 MB/s） | ✅ `.tools\gradle-8.10.2` |
| **NeoForge 21.1.248** | 你的整合包 `libraries\net\neoforged\neoforge\21.1.248` | ✅ universal + client |
| **Minecraft 1.21.1（已打补丁）** | `versions\Willow11TH_Aero_beta2(无地图)\...jar`（25.6 MB） | ✅ |
| **137 个 MC 库（151 MB）** | `libraries\**` | ✅ |
| **Create 6.0.10** | `mods\[机械动力] create-1.21.1-6.0.10.jar`（18.2 MB） | ✅ |
| **Sable 2.0.5** | `mods\sable-neoforge-1.21.1-2.0.5.jar`（12.4 MB） | ✅ |
| **Simurail 0.1.0** | `mods\simurail-0.1.0+mc1.21.1+9b9ddc3.jar` | ✅ |

**实测：`build.bat build --offline` → BUILD SUCCESSFUL，产出 `build\libs\hexietrain-0.1.0+mc1.21.1.jar`。**

jar 内含：`HexieTrain.class` + `META-INF/neoforge.mods.toml` + `pack.mcmeta` ✅

## 二、这套方案的原理与代价

**原理**：NeoForge 的模组在运行时看到的类名，和整合包里 jar 的类名完全一致。
所以直接把这些 jar 当 `compileOnly` classpath，编译出的 class 天然与运行时匹配 —— 无需 NeoForm 反混淆流水线。

**代价**：

| 能做 | 不能做 |
|---|---|
| ✅ `gradle build` 出 jar | ❌ `runClient`（没有 dev 环境） |
| ✅ 完整访问 Create / Sable / Simurail API | ❌ datagen（数据生成） |
| ✅ IDE 里补全、跳转（导入 gradle 工程即可） | ❌ 热重载 |
| ✅ 丢进 `mods` 目录直接测 | ❌ 断点调试（要另外配） |

**测试流程**：`build.bat build --offline` → 把 `build\libs\*.jar` 复制到
`H:\柳树服beta2\.minecraft\versions\Willow11TH_Aero_beta2(无地图)\mods\` → 启动游戏。

## 三、常用命令

```bat
build.bat build --offline          :: 编译打包
build.bat clean build --offline    :: 全量重编
build.bat compileJava --offline    :: 只编译，快
```

## 四、如果要升级/切换整合包

改 `build.gradle` 顶部三行即可：

```groovy
mcDir    = "H:/柳树服beta2/.minecraft"
instName = "Willow11TH_Aero_beta2(无地图)"
modsDir  = "${instDir}/mods"
```

（另一个实例 `H:\羊驼服4.0\.minecraft` 用的是 NeoForge 21.1.233，也能这样切）

## 五、如果以后需要 Registrate / Ponder / Flywheel 的类

Create 的 jar 里用 jarJar 打包了它们（在 `META-INF/jarjar/*.jar`），**嵌套 jar 不在 classpath 上**。
需要时从 Create 的 jar 里把它们解出来放到 `libs/`，然后在 `build.gradle` 里加一行：

```groovy
compileOnly fileTree(dir: 'libs', include: ['*.jar'])
```

## 六、后续开发顺序

| 里程碑 | 内容 | 需要的 API |
|---|---|---|
| **M0** | `RailwayIndex`：全图铁路快照（图/节点/边/车站/信号） | Create |
| **M1** | 影子列车 + 到站升旗 | Create |
| **M2** | `SpeedSimulator` + 能量账本（纯数学） | 无 |
| **M3** | 转向架方块 + 左右红石选道 | Create |
| **M4** | 物理体位姿注入 / 跟随 | Sable |
| **M5** | 速度仪表（DisplaySource + 指针表盘） | Create |

> 设计文档在上一级目录：`预期成果与可行性.md` / `技术细节-备查.md` / `代码\`
---

## 迭代模式（暂不打包）

当前阶段只做**类型检查**，不产出 jar：

```bat
check.bat            :: 只编译（快），不生成 jar
build.bat build --offline   :: 需要出 jar 时再用
```

`check.bat` = `gradle compileJava --offline --no-daemon`。