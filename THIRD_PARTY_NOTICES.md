# Third-Party Notices / 第三方声明

本模组（TLM Formation Tactics / `tlmformation`）整体以 **GNU General Public License v3.0** 发布，
因为它在运行时依赖并以类继承方式链接「卓越前线（SuperbWarfare）」，且分发了该模组的若干资源文件。

This mod is distributed under **GPL-3.0** because it links against SuperbWarfare (GPL-3.0)
and redistributes some of its assets. See `LICENSE` for the full license text.

## 依赖 / Dependencies

| 组件 | 版本 | 许可证 | 说明 |
|---|---|---|---|
| 卓越前线 SuperbWarfare | 0.8.9.2（要求 `[0.8.9,0.9)`） | **GPL-3.0** | 硬前置。`EntityHelicopter extends VehicleEntity`，使用载具/座位/渲染 API；分发了其音效与部分载具数据/资源 |
| 车万女仆 Touhou Little Maid | 1.5.3（要求 `[1.5.3,1.6)`） | **MIT（代码）/ CC BY-NC-SA 4.0（美术资源）** | 硬前置。仅通过其公开 API 与 mixin 目标使用，**不再分发其美术/音频资源** |
| GeckoLib | 4.8.4（要求 `[4.8,4.9)`） | MIT | 硬前置，动画/模型渲染 |
| SimpleBedrockModel | ≥2.5.1 | 见其自身许可 | 卓越前线的依赖，安装卓越前线时需要 |
| Minecraft / Forge | 1.20.1 / 47.x | Minecraft EULA / LGPL | 游戏与模组加载器 |

## 分发的第三方素材 / Bundled third-party assets

下列文件来自 **SuperbWarfare 0.8.9.2**（GPL-3.0），随本模组按 GPL-3.0 一起分发：

- `assets/tlmformation/sounds/heli/engine_loop.ogg`（原 `mi_28_engine.ogg`）
- `assets/tlmformation/sounds/heli/engine_start.ogg`（原 `mi_28_engine_start.ogg`）
- `assets/tlmformation/sounds/radio/click.ogg`（原 `trigger_click.ogg`）
- `assets/tlmformation/sounds/radio/beep.ogg`（原 `c4_beep.ogg`）

若这些素材的作者要求移除或改以其它方式分发，请在本仓库提 issue，我们会替换为自制素材。

本模组自有或已获授权的内容（含 UH-60 模型转换、贴图、语言文件、代码）同样按 GPL-3.0 授权。

## 源码获取 / Source

GPL-3.0 要求分发二进制时提供对应源码。发布 jar 时请同时提供：

1. 本仓库对应 tag 的源码（GitHub Releases 的 Source code 压缩包即可）；或
2. `dist/../*-sources.zip`（构建时可通过 `gradle sourcesJar` 生成）。

## 商标 / Trademarks

「SuperbWarfare / 卓越前线」「Touhou Little Maid / 车万女仆」等名称与商标归各自作者所有。
本项目为社区附属模组，与上述项目作者无隶属关系。
