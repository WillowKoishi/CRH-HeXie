# 车窗流（Carriage Flow）资源包配置

```text
// 客户端资源包：使用 assets/。
// JSONC 注释仅用于文档；保存为 .json 时去掉注释。
// 独立资源包放入 resourcepacks/ 。
// 随模组提供时，assets/ 放在 src/main/resources/ 下。

<资源包根目录>/
├─ pack.mcmeta
└─ assets/
   └─ yourmod/                         // 自定义命名空间
      ├─ carriage_ui/
      │  ├─ pages/
      │  │  └─ example.json            // 目标界面配置
      │  ├─ types/
      │  │  └─ example.json            // 车型 ID：yourmod:example
      │  └─ models/
      │     └─ example/
      │        ├─ carriage.json        // 车型参数、轨道、方块分组、可取用蓝图
      │        ├─ exterior.nbt         // 外部结构(机械动力蓝图，北方为前)
      │        ├─ interior.nbt         // 内部结构(机械动力蓝图，北方为前)
      │        └─ example.nbt          // 可选：右下角直接取用的完整机械动力蓝图
      └─ lang/
         └─ zh_cn.json                // 使用 title_key 时提供
```

`pack.mcmeta`

```jsonc
{
  "pack": {
    "pack_format": 34,               
    "description": "车厢 UI 资源包"
  }
}
```

`assets/yourmod/carriage_ui/pages/example.json`

```jsonc
{
  // 必填：普通创造标签页的注册 ID。
  // HeXie 车厢页：hexie:hexie_normalspeedpassagecarriagetab
  // 不支持搜索页、背包页、箱子等容器。
  // 同一目标标签页只配置一次；未配置的标签页不覆盖。
  // 同一路径由资源包优先级决定覆盖。
  // 不同文件重复绑定同一标签页：按资源 ID 排序保留首个，其余记录错误。
  "tab": "hexie:hexie_normalspeedpassagecarriagetab",

  // 必填、非空：车型 ID，按数组顺序排列，重复 ID 去重。
  // 可引用其他命名空间；每个 ID 必须有车型声明或 Java 注册。
  "carriages": [
    "yourmod:example",
    "hexie:25b"
  ],

  // 可选，默认 preview；可选值：preview、original。
  // preview：车厢预览；original：原物品菜单。
  // 当前客户端会话中已经保存的模式优先于此默认值。
  "default_mode": "preview",

  // 可选，默认 true：是否显示并允许使用模式切换按钮。
  // 不同标签页分别保存模式、车型位置和视角。
  "allow_mode_switch": true
}
```

`assets/yourmod/carriage_ui/types/example.json`

```jsonc
{
  // 必填：命名空间 + 相对 assets/<命名空间>/ 的目录。
  // 指向包含 carriage.json、内外 NBT 和可选预制蓝图的目录。
  // 当前文件声明车型 yourmod:example；同 ID 的 JSON 优先于 Java 注册。
  "assets": "yourmod:carriage_ui/models/example"
}
```

`assets/yourmod/carriage_ui/models/example/carriage.json`

```jsonc
{
  "format": 1,                       // 必填，目前仅支持 1

  "title": "示例车厢",               // 未提供 title_key 时必填
  // "title_key": "carriage.yourmod.example", // 可选，优先于 title

  // 可选，分别默认 0.5；单位：方块；支持小数与有限负值。
  // 各间距绝对值 <= 256，且 length / 2 + 各间距 >= 0.125。
  // 前车 A、后车 B 的包围盒间距 = A.rear_gap + B.front_gap。
  // 中心距离 = A.length / 2 + A.rear_gap + B.front_gap + B.length / 2。
  // 首尾循环、上方横条和下方预览使用相同间距。
  "front_gap": 0.5,
  "rear_gap": 1.5,

  // 必填：车下轨道；这里使用 HeXie 轨道。
  "track": {
    "block": "hexie:standard_track", // 必填：已注册轨道方块 ID
    "properties": {                  // 可选：属性值使用字符串
      "shape": "zo",                 // 支持 zo 的轨道默认使用 zo；显式值覆盖默认值
      "waterlogged": "false"
    }
  },

  // 必填：相对当前车型资产目录的结构文件路径。
  // 不允许绝对路径、冒号或 ..。
  "exterior": "exterior.nbt",
  "interior": "interior.nbt",

  // 可选：右下角物品框中预制蓝图的文件名，相对当前车型资产目录。
  // 使用机械动力导出的完整压缩 .nbt，不会自动合并 exterior/interior。
  // 文件与 carriage.json 放在一起；路径规则同上。省略或留空时物品框为空。
  // 文件缺失/无效时记录加载错误并留空，不影响车厢预览。
  "schematic": "example.nbt",

  // 可选：车窗流专用分组，不是 Minecraft tag。
  // 分组仅在当前车型内生效，同名分组不会跨车型合并。
  // 点击某方块时，展示当前车型内该方块所属各组的成员，并去重。
  // 未分组的方块仅展示自身；成员填写已注册的方块 ID。
  // 分组控制取物弹窗；内外分类由两个 NBT 文件决定。
  "groups": {
    "yourmod:panels": [
      "hexie:panel_25b_original_bottom",
      "hexie:panel_25b_original_mid",
      "hexie:panel_25b_original_top"
    ],
    "yourmod:windows": [
      "hexie:original_color_window_25b_small",
      "hexie:original_color_window_25b_wide"
    ],
    "yourmod:seats": [
      "hexie:seat_yz",
      "hexie:seat_rz"
    ]
  }
}
```

右下角蓝图随当前选中的车型切换，使用原版创造物品槽拿取。完整压缩结构随蓝图物品保存和同步，
服务端无需另装相同的客户端资源包，也无需先在蓝图桌上传；拿出的物品可继续使用机械动力的预览、放置和蓝图炮逻辑。
压缩文件最大 512 KiB。这里仍是 `assets/` 下的客户端资源包配置，不是 `data/` 下的服务端数据包。

