# MainActivity 拆分方案（C 项）

> 立档：2026-10-21 · 状态：**C1、C4 已执行；C2/C3/C5/C6 待办** · 验收门：`./gradlew testDebugUnitTest` 956 用例全绿 + `assembleDebug` 通过

## 1. 现状（实测，非印象）

| 指标 | 数值 | 取得方式 |
|---|---|---|
| `MainActivity.kt` | **3940 行** | `wc -l` |
| `onCreate` 跨度 | **3736 行**（含单个 `setContent {}`） | 顶层声明扫描：类内只有这一个函数 |
| `@Composable` 声明 | **0 个**（UI 组件已全在 `PlayerScreen.kt` / `SearchScreen.kt` 等） | `grep -c` |
| `remember` 状态 | **108 个** | 正则统计 |
| 状态跨区分布（按 400 行分桶） | 跨 1 桶 **1 个**、2 桶 19、3 桶 42、4 桶 21、5 桶 17、≥6 桶 8 | 同上 |

**结论：这不是"文件太长"，而是"一个函数持有整个应用的状态"。**
108 个状态里只有 1 个能在单区域内自洽，其余全部跨区；最分散的是 `queue`(跨 9 区/62 引用)、`controller`(8/36)、`screen`(8/100)、`currentIndex`(8/40)。
→ 任何"按行切文件"的做法都要么给子组件传 20+ 参数，要么先做状态提升。**机械切分不可行**。

## 2. 目标结构

```
MainActivity.kt          // ~150 行：Activity 生命周期 + 装配
MainState.kt             // 状态持有者：108 个 mutableStateOf 归位（可单测）
MainScaffold.kt          // 纯 UI 装配：每个 screen 一个 when 分支，只读 state + 回传事件
handlers/
  PlaybackCoordinator.kt // MediaController 绑定、切歌、恢复、看门狗
  SearchCoordinator.kt   // 搜索/分页/历史/热词
  AuthCoordinator.kt     // 登录/注册/续签/登出
  UpdateCoordinator.kt   // 自更新检查/下载/安装
  OfflineCoordinator.kt  // 下载/缓存/审计
```

## 3. 切片顺序（严格串行，一片一提交）

| 片 | 内容 | 风险 | 验收 |
|---|---|---|---|
| **C1** | `Screen` 抽到 `Screen.kt` | 极低 | ✅ 已执行，编译通过 |
| **C2** | 找出 `onCreate` 内可提取的纯决策，抽成顶层纯函数 + 单测 | 低 | 新增用例全绿，且不改变任何调用点行为 |
| ~~C3~~ | ~~建通用 `MainState` 骨架~~ → **跳过**（见 §7：直接让 coordinator 自持状态，切得更小） | — | — |
| **C4** | 自更新整段（状态+检查+下载+安装回调）搬到 `UpdateCoordinator` | 中 | ✅ 已执行（当时 943 全绿 + assembleRelease 绿；当前门禁 956） |
| **C5** | 播放组（`queue`/`controller`/`currentIndex`/`playMode`）——最大的一刀，必须单独一轮，不与其他片并行 | 高 | 同上 + 真机回归（播放/通知/切歌/续播） |
| **C6** | 搜索组、Auth 组、Offline 组依次；最后删空壳 | 中 | 同上 |

## 4. 停手判据（硬规则）

任一条命中即**回滚该片**、退回上一绿点、重新设计，不许连续硬顶：

1. 单测红 **> 2 次**修不绿；
2. 单片 diff **> 800 行**（超出即说明这一刀切大了，拆小）；
3. 需要同时改 **> 5 个 screen 的调用点**；
4. 出现任何**行为变化**（不只是搬家）——重构片只许搬代码，不许顺手改逻辑。

## 5. 明确不做

- 不做一次性大爆炸提交（一个 commit 动 3000 行 = 无法 review、无法二分定位）。
- 不在重构片里顺手加功能/改样式。
- 不并行动两个片（状态提升的合并冲突代价远高于串行等待）。

## 6. 与既有裁决的关系

`README.md:74` 的红线（通知/媒体按钮不用拦截式 session 回调）与本次拆分**正交**：拆分只搬状态与回调的归属，不改 `PlaybackService` 的媒体会话语义。
vivo 媒体键问题仍**冻结**，不因本次拆分解冻。

## 7. C4 执行记录（2026-10-21）

**偏离**：原计划 C3 先建一个通用 `MainState` 再迁状态。实际执行时判断"先通用后专用"是空转——
自更新这组的 4 个状态（`release`/`busy`/`manualChecking`/`channel`）从不需要跨屏共享，直接让
`UpdateCoordinator` 自持 state 即可，反而少一层抽象。通用 `MainState` 等真正出现跨组共享时再建。

**结果**：`MainActivity.kt` **3939 → 3781 行**（-158）；新增 `UpdateCoordinator.kt`（218 行）。

**纯搬迁证据**：

- 状态字段名、初值、可见性一一对应（`release`/`busy`/`manualChecking`/`channel`）。
- 检查逻辑：24h 节流谓词 `shouldRunUpdateCheck(now, last, force=false)`、先写时间戳再拉取、版本号取
  `PackageManager` 的顺序均未改；手动检查仍以 `appVersionName.ifBlank { PackageManager }` 取当前版本。
- 下载逻辑：`updates/` 目录、`safeTag` 正则、`isDownloadComplete` 短路、跨源 fallback、失败文案
  （`更新失败: ` 前缀）均逐字保留。
- 安装逻辑：未知来源权限 → 跳设置页 → 签名校验 → FileProvider → ACTION_VIEW，顺序与文案未改。
- 唯一改名：`setChannel` → `selectChannel`（JVM 签名与 `channel` 的私有 setter 冲突，属编译期硬约束）。

**行为等价性论证（为什么不靠单测）**：这段是 IO+Intent 驱动，单测只能覆盖已被 `UpdateCheckTest`
钉死的纯函数；本轮等价性靠"逐行对照搬迁 + 编译 + 当时 943 例回归 + release R8 通过"。真机行为（下载/安装
/跳转设置页）仍需发版前手动过一遍——列在发版检查项里。

**遗留风险**：`UpdateCoordinator` 持有 `Context`，必须以 Activity 为生命周期（当前由 `remember`
保证）。若后续搬进 ViewModel，需换成 `applicationContext` 或注入，不许把 Activity 塞进长生命周期对象。
