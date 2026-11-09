# libs/ —— 构建依赖（不提交到仓库）

本项目通过 `flatDir libs` 引用三个外部模组 jar。**仓库不包含这些 jar**（第三方作品，
各自有独立许可，不适合直接提交）。构建前请把它们放进本目录，文件名必须一致。

| 文件名 | 版本 | 大小 | SHA256 |
|---|---|---|---|
| `superbwarfare-0.8.9.2.jar` | 卓越前线 0.8.9.2 | 42,118,965 | `559930003F638080DE648A8621821D785158768C3F69A762094EF4C8D3C4CC12` |
| `touhoulittlemaid-1.5.3.jar` | 车万女仆 1.5.3 (forge+mc1.20.1) | 24,413,134 | `8C341567E57AA3C006F9FA85C7A5E3BC36A53B1CCA4E3538C3DB705EEEEA1BBC` |
| `geckolib-4.8.4.jar` | GeckoLib 4.8.4 | 1,038,209 | `C04ABBA4FD354DD8C0E8A732F1F05F4DCA893D2E1D962B2AB128BBD411478747` |

> `touhoulittlemaid-1.5.3-forge+mc1.20.1.jar` 与本表 `touhoulittlemaid-1.5.3.jar` 内容相同，
> 放任意一个、或两个都放均可。

下载建议：
- 卓越前线：Modrinth / CurseForge 搜索 **SuperbWarfare**，选 1.20.1、0.8.9.x
- 车万女仆：Modrinth / CurseForge 搜索 **Touhou Little Maid**，选 1.20.1、1.5.3
- GeckoLib：Modrinth / CurseForge 搜索 **GeckoLib**，选 1.20.1、4.8.4

校验：

```powershell
Get-FileHash .\libs\superbwarfare-0.8.9.2.jar -Algorithm SHA256
```

> 注意：卓越前线是 **GPL-3.0**，车万女仆是 MIT + CC BY-NC-SA 4.0。请只把它们当作
> 构建依赖使用，不要复制其代码/素材进本仓库（本项目的 GPL-3.0 义务见
> `THIRD_PARTY_NOTICES.md`）。
