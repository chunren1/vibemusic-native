# MainActivity 拆分方案（C 项）

> 立档：2026-10-21 · 状态：**C1 已执行，C2-C6 待办** · 验收门：`./gradlew testDebugUnitTest` 937 用例全绿 + `assembleDebug` 通过

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
| **C3** | 建 `MainState` 骨架，先迁**低耦合组**：自更新（`updateRelease` 6 引用 / `updateBusy` 7 / `updateChannel` 6，全部落在 371-3948 的同一条链路上） | 中 | 937 全绿 + 手工 diff 审查 |
| **C4** | 自更新整段（状态+检查+下载+安装回调）搬到 `UpdateCoordinator`，验证 C3 的骨架可用 | 中 | 同上 |
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
