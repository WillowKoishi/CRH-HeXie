# HeXie

### 简介

这是一个为机械动力(Create mod)，适配高版本（1.21.1+）而制作的中国铁路装饰类方块mod


### 可调式信号灯

新方块 ID：`hexie:adjustable_semaphore`，可在创造模式物品栏中获取，也可使用 `/give @s hexie:adjustable_semaphore`。原有 `hexie:hx_semaphore` 保持可用。

- 先选择轨道及信号方向，再放置信号灯。默认沿所选信号方向对齐轨道，支持直线、斜向及曲线轨道；轨道暂未加载时保留已保存的朝向。
- 空手长按右键打开机械动力风格设置条：第一行选择灯色，第二行向左选“世界”、向右选“轨道”，松开确认所在行的设置。
- “世界”对齐沿用原信号灯的放置朝向；“轨道”对齐以所选轨道的局部方向为基准。
- 扳手短按右键顺时针旋转 90°；长按打开独立的角度微调条，范围为 −45° 至 +45°，每步 1°，松开确认。微调不会改变信号绑定方向或闭塞逻辑。
- 灯色、对齐方式、90° 旋转及微调角度保存在方块实体中，并同步给客户端。

### 第三方版权声明

本项目包含基于机械动力（[Create](https://github.com/Creators-of-Create/Create)）修改的代码，保留其原作者 simibubi 的版权及 MIT 许可声明。涉及的文件和完整许可文本见 [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)。项目许可证及第三方声明随发布 JAR 和源码 JAR 一并提供，位于 `META-INF/` 目录。

### 联系开发者
[服务器测试QQ群](https://qun.qq.com/universal-share/share?ac=1&authKey=bZx%2BAmZpVQgYc%2FNm1iH%2FGO%2Bx6SKyFGpPVUNJ9IgJOp7T8vmayszFTnJmTi2gHQMs&busi_data=eyJncm91cENvZGUiOiI1ODk5NDM2NzgiLCJ0b2tlbiI6Iks1ZnRzcUd5ZmRuK1hUcmlPMUMrOHkwV3VXM3diV1BNaWxXUXF0Q2w5MjhYdmZBYlJ6djRUUTJVUytnWlNhanciLCJ1aW4iOiIxMDMzODU1MDA3In0%3D&data=PT58Z64vJ_1SGu_wOP9ri0kguGFsJGKL4oSEpvhg2gfGUVreBgicxzWbDpcUafwMmG57BLye9eLtlpH2rdhRSA&svctype=4&tempid=h5_group_info)

