# 车万女仆：多功能队伍指令 9.3

作者：**cmdyy**

Minecraft **1.20.1 / Forge 47.x** 的车万女仆（Touhou Little Maid）附属模组。

包含三大板块：

- **女仆队伍管理（F9）**：动态队伍、独立据点、召唤/接替、小队返回、死亡坐标与传送。
- **战术指令轮盘（F8）**：跟随、撤退、警戒、锁定集火、标记点，仅作用于当前激活队伍。
- **指挥棒参数与阵型（F10）**：FollowRange、阵型跟随、扩圈和移动参数。
- **空中支援（F9 部署 / 小队撤离）**：卓越前线黑鹰自动空投、索降、索升回收（未安装卓越前线时自动不可用）。

## 环境

| 项目 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.0+（建议 47.3.0+，实测 47.4.28） |
| 车万女仆（强制） | 1.5.3-forge+mc1.20.1（不支持 1.6+） |
| GeckoLib（强制） | 4.8.4（不支持 4.9+） |
| 卓越前线 SuperbWarfare（软依赖） | 0.8.9.2（0.8.9 ≤ 版本 < 0.9） |
| SimpleBedrockModel（装卓越前线时才需要） | ≥ 2.5.1 |
| Java | 17 |

> **联机**：客户端与服务端必须安装**相同版本**的 tlmformation（否则会拒绝连接）。
> 不装卓越前线也能玩：队伍、阵型、HUD、战术指令全部正常，只是没有直升机空投/索降。

## 安装

1. 安装 Forge 1.20.1 与车万女仆 1.5.3、GeckoLib 4.8.4。
2. （可选，启用空投功能）安装卓越前线 0.8.9.2 及其依赖 SimpleBedrockModel ≥2.5.1。
3. 把 `tlmformation-9.3-rappel.jar` 放进 `mods/`。
4. 启动游戏。联机时客户端和服务端都需要安装；AI、战斗、区块逻辑在服务端执行，菜单在客户端显示。

## 使用

### 阵型指挥棒

合成：

```text
 G
SWS
 G
```

`G` = 金锭，`S` = 木棍，`W` = 白色羊毛。

也可以直接：

```text
/give @s tlmformation:formation_commander
```

- **右击空气**：打开管理菜单
- **潜行 + 右击空气**：循环 FollowRange（16 / 24 / 32 / 48 / 64 / 96 / 128）
- **右击自己的女仆**：登记/取消该女仆的区块常加载

### 女仆战术指令轮盘

默认按键 **F8**，可在“控制 → 按键绑定 → TLM Formation”中修改为任意键。

- **长按**按键：打开轮盘，隐藏系统鼠标并锁定原始鼠标输入；
- 按住期间拖动鼠标：按硬件位移方向高亮扇区，视角不会旋转；
- **鼠标左键**：执行当前高亮指令并关闭；不移动鼠标时默认高亮“跟随我”；
- **松开按键**：只关闭轮盘，不执行指令；
- 轮盘不暂停游戏，背景轻微调暗，扇区图标按极坐标居中绘制。

六个扇区：

| 扇区 | 指令 | 行为 |
|---|---|---|
| 上 | 行动 | 前往准星标记点；无效时使用视线前方地面 |
| 右上 | 锁定 | 射线/锥形吸附敌人，全队集火 |
| 右下 | 警戒 | 在 FollowRange 内随机巡逻，限制追击范围 |
| 下 | 跟随我 | 清除所有临时状态，恢复阵型跟随 |
| 左下 | 原地待命 | 留在原地，停止跟随和攻击 |
| 左上 | 全体撤退 | 放弃战斗，强制回防，到玩家附近恢复跟随 |

指令只作用于 **当前已召唤（激活）队伍**中、位于玩家 32 格内且属于该玩家的女仆。服务端筛选并执行，客户端只负责选指令和发送网络包；未召唤队伍的女仆即使就在附近也不会响应轮盘。

临时状态：

- 驻守/警戒/锁定/待命会写入女仆持久化 NBT，并显示头顶战术图标；
- 驻守点和锁定目标会显示准星/集合图标；
- 女仆再次收到“跟随我”时清除临时状态；
- “跟随我”不会强制清除战斗目标，攻击 AI 可继续直到目标死亡/脱离；
- “全体撤退”会强制清除攻击目标；
- “警戒”保留战斗 AI，但目标离开警戒半径后放弃追击。

- 临时指令使用原版 Navigation，速度倍率硬上限 `tacticalSpeedCap = 1.2`，不调用 teleportTo；警戒固定 `0.4`，标记点/撤退固定 `0.65`。
- 临时指令的寻路目标只有变化超过 `tacticalTargetChangeThreshold` 才重设，避免每 tick 重算导致“鬼畜冲刺”；该逻辑只属于 `TacticalManager`，不影响常规跟随。
- “锁定”从视线 96 格内直接命中的生物，或准星 5 格锥形范围内最近的敌对/中立生物中选目标；全队攻击该目标，不给导航加速，目标死亡后自动恢复。
- “前往标记点”使用玩家视线 RayTrace；未命中方块时沿视线向下扫描，直到找到真实地面，不再使用“玩家位置 + 固定距离”的坐标。
- 警戒巡逻使用普通步速，不套用任何阵型速度同步。

### 女仆队伍配置（F9）

默认按键 **F9**，打开“女仆队伍配置”界面：

- 左侧显示未入队女仆；已加入队伍的女仆自动隐藏，移除后实时回到左侧；
- 右侧最多 10 个队伍，可点“添加队伍”动态增加，每队最多 5 人；
- 队伍默认折叠显示名称/人数；点击展开后以网格卡片显示成员 3D 模型、死亡坐标、TP/移除按钮，以及设据点/召唤/传送据点/折叠按钮；
- 点击“+”把左侧选中的女仆加入队伍；展开后点成员右侧“移除”移出队伍；
- 每个队伍独立“设据点”，保存玩家当前 XYZ 和维度；
- 每个队伍独立“召唤”：
  - 无激活队伍：队伍传送到玩家附近并恢复跟随；
  - 已有激活队伍：旧队伍在据点 3~5 格内分散传送到安全落点，随后取消跟随、绑定 Home 并切换 IdleTask，在据点自然活动；聊天栏播报“队伍X已撤退”，新队伍接替并播报“队伍Y已接替”；
  - 重复召唤当前队伍：提示“该队伍已在场”。

队伍列表、据点和激活队伍保存在玩家持久化 NBT（`TLMTeams`）中，据点互不覆盖。

### 死亡坐标提示

女仆死亡完全保留车万女仆原版逻辑和墓碑，不拦截、不取消、不自动复活。

死亡时仅记录坐标：

- 写入女仆自身 NBT：`LastDeathX/Y/Z`、`LastDeathDim`；
- 同时缓存到玩家持久化数据，供女仆实体已被移除后 UI 读取；
- 队伍展开面板中显示：`名字 (死亡坐标: X, Y, Z)`；没有记录则只显示名字。

### 指挥棒参数设置（F10）

默认按键 **F10**，可在按键绑定中修改。按下后通过服务端打开原有的“女仆阵型管理”参数界面（`FormationMenu` / `FormationScreen`），用于调节跟随距离、扩圈机制、FollowRange 等参数。

状态采用“女仆持久化 NBT + 服务端 `TacticalManager` 缓存”，而不是自定义 Task：轮盘状态需要跨存档保留、同时压制阵型/默认跟随，又不能永久占用 TLM 的任务槽；由服务端统一 tick 更适合这种跨模块临时状态。车万女仆侧主要查找/使用 `EntityMaid`、`getBrain()`、`setTarget()`、`MemoryModuleType.WALK_TARGET/ATTACK_TARGET/PATH`、`MaidFollowOwnerTask`、`TaskManager` 等类和方法。

## 管理菜单

- 阵型开关
- 扩圈范围 ±10
- FollowRange ±8
- 登记最近女仆
- 取消登记最近女仆
- 召回女仆

### 命令

```text
/tlmformation status
/tlmformation register
/tlmformation unregister
/tlmformation recall
/tlmformation menu
```

`register` / `unregister` 操作 16 格内最近的一只自己的女仆。

## FollowRange 语义

`FollowRange` 是**强制跟随激活半径**，不是传送距离：

- **未进入跟随状态**且距离 ≤ FollowRange：不强制跟随，可执行工作/随机走动；
- 距离 > FollowRange：进入跟随状态，开始阵型/预判；
- 已激活的跟随状态使用 `0.75 × FollowRange` 的释放半径和静止时间做迟滞；
- 工作女仆在释放半径内且玩家静止足够久时，可以退出跟随继续工作；
- 传送由独立的 `emergencyTeleportDistance`（默认 64）兜底，FollowRange 内绝不传送。

## 配置

```text
world/serverconfig/tlmformation-server.toml
```

关键项：

```toml
[formation]
enabled = true
maxMaids = 12
followRange = 32
followReleaseTicks = 100
stillSpeed = 0.05
stillTicks = 40
predictionTicks = 20
predictionStrength = 1.0
maxPrediction = 5
highSpeedPredictionCap = 3.0
predictionSpeedCap = 0.25
lowSpeedPredictionThreshold = 0.11
lowSpeedPredictionFactor = 0.25
targetSmoothingSpeed = 1.0
targetSmoothingMin = 0.15
targetSmoothingFactor = 1.25
targetChangeThreshold = 1.5
pathCommitTicks = 12
frontFallbackCooldown = 10
frontTargetLead = 1.25
lowSpeedFrontLead = 2.5
frontBoostDistance = 6.0
rearSpeedBoost = 0.2
rearChaseDistance = 3.0
frontPrioritySpeed = 0.12
frontSpeedBoost = 0.25
emergencyTeleportDistance = 64
tacticalRadius = 32
tacticalGuardPatrolInterval = 40
tacticalMarkerRecallDistance = 64
tacticalSpeedCap = 1.2
tacticalTargetChangeThreshold = 1.5
tacticalGuardSpeed = 0.4
tacticalMoveSpeed = 0.65
tacticalLockRange = 96
tacticalLockCone = 5.0

[combat]
expandRange = 100

[chunkLoading]
enabled = true
maxRegisteredMaids = 8

[friendlyFire]
protectOwner = true
protectSameOwnerMaids = true
ownerDamageMode = "VANILLA"
```

> `expandRange = 100` 会带来较大的实体扫描开销，卡顿时建议降到 32/48。
>
> 如果同时安装 **Maid Always Follow**，请把 `chunkLoading.enabled` 设为 `false`，避免双份区块 ticket。

## 行为说明

- 只有跟随状态的女仆参与阵型：居家、坐下、骑乘、睡觉、被拴的女仆不参与。
- 女仆有攻击目标时阵型暂停，目标死亡/逃出扩圈后回到阵型或分散。
- 玩家静止 40 tick：跟随中的女仆以玩家为圆心分散；玩家恢复移动：切回阵型。
- 玩家高速移动时，前槽位（4 人以上 FL/FR）优先走向预判点，并获得临时移动速度加成；预判点被阻挡时会尝试预测点两侧，而不是立刻退回玩家身边。
- 平滑目标每 tick 插值，插值速度约等于女仆当前移动速度的 1.25 倍，并限制在 `targetSmoothingMin`~`targetSmoothingSpeed`，让目标点稳定领先而不是频繁落到身后。
- 只有平滑目标相对当前寻路点移动超过 `targetChangeThreshold`（默认 1.5 格），且当前路径已存活 `pathCommitTicks`（默认 12 tick）时，才提交新的 `WalkTarget`；小调整不会重置路径。
- 目标点严重失效（进入方块/悬空）时才会立即打断路径；正常微调不会调用 `navigation.stop()` 后重新 `moveTo`。
- 玩家低速走路时预判量乘以 `lowSpeedPredictionFactor`（默认 0.25），同时前槽目标额外前移 `lowSpeedFrontLead`（默认 2.5 格），避免女仆到达后停下等待。
- 玩家速度越快，预判距离越收紧：高速时上限约 3 格，避免目标点超出女仆寻路可达范围。
- 4 人以上前槽位的目标额外前移 `frontTargetLead`（默认 1.25 格）；低速时再叠加 `lowSpeedFrontLead`。
- 本版本不通过降低女仆移动速度解决超前问题；前槽加速只在追赶且距离目标超过 `frontBoostDistance`（默认 6 格）时启用。
- 后槽位不套用前槽前移、到达减速或速度同步；明显落后时保持全速，并在距离后槽目标超过 `rearChaseDistance` 时临时获得 `rearSpeedBoost` 加速，追上后立即移除。
- 前槽位因预判点不合法而回退后，`frontFallbackCooldown` tick 内保持最后安全点，不再立即重试同一预判点。
- 前槽位加速有固定 UUID 和配置上限（`frontSpeedBoost` 最大 0.6），且带 20 tick 保持窗口，目标点微调不会反复开关加速。
- 女仆移动时头部朝向自身移动方向；只有女仆待机/速度很低时才自然看向玩家附近。
- 跟随移动时会压制车万女仆原生的 `SetEntityLookTarget`/`LookAtTargetSink` 与 `BehaviorUtils.lookAtEntity` 注视玩家逻辑；短时间被阻挡但仍持有寻路目标时，继续看向路径目标，不会回头看一眼主人。
- 预判点会做脚/头碰撞、支撑方块、`noCollision` 检查；不合法时依次回退到玩家位置/后方槽位。
- 主人传送后，战斗中的目标会被保留（现有传送 Mixin）。
- 主人重生后同维度女仆会被召回；跨维度召回不在本模组范围内。
- 区块常加载默认每位玩家最多 8 只，只加载女仆所在中心区块。

## 从源码构建

1. 安装 JDK 17。
2. 把三个依赖 jar 放进 `libs/`（仓库不提交它们，文件名与校验值见 [libs/README.md](libs/README.md)）：

```text
libs/superbwarfare-0.8.9.2.jar          # 卓越前线
libs/touhoulittlemaid-1.5.3.jar         # 车万女仆
libs/geckolib-4.8.4.jar                 # GeckoLib
```

3. 构建：

```bash
./gradlew build
```

Windows：

```bat
gradlew.bat build
```

产物：

```text
build/libs/tlmformation-9.3.jar
```

## 说明

- 与 **Combat Plus** 不共存：它是 NeoForge 1.21.1，且会修改同一批战斗方法。
- 与 **Maid Always Follow** 可以共存，但请关闭本模组的 `chunkLoading`，避免双份区块 ticket。
- 源码包不附带任何第三方模组 jar。

## 许可证

本项目以 **GNU General Public License v3.0** 发布（全文见 [LICENSE](LICENSE)）。

原因：本模组**硬依赖卓越前线（SuperbWarfare，GPL-3.0）**，在代码上继承其 `VehicleEntity`
并使用其载具/座位/渲染 API，同时分发了它的少量音效与载具数据资源，因此整体必须按 GPL-3.0 发布、
并在发布二进制时提供对应源码。

- 第三方声明（依赖、素材来源与许可）见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 车万女仆（Touhou Little Maid）由 TartaricAcid 等作者开发：代码 MIT、美术资源 CC BY-NC-SA 4.0；
  本模组只调用其公开 API 与 mixin 目标，**不再包含其任何美术/音频素材**。
- GeckoLib 为 MIT。
- 若你希望改用宽松许可（如 MIT），必须先移除对卓越前线的代码依赖与资源分发。

