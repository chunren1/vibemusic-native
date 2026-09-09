# vibemusic-native（Sisyphus 版）——原生 App 开发计划

> 并行开发区分：本目录 `vibemusic-native/` 是 AI 版；用户自研版在另一目录（待确认）。
> 两 App 必须包名不同、展示名不同，才能装在同一台手机上对照测试。

## 0. 身份与冻结区

| 项 | 本 App（AI 版） |
|---|---|
| 目录 | `/home/user/projects/vibemusic-native/` |
| 包名（已定 2026-09-08） | `com.cyk666.vibemusic`；自研版预留 `com.cyk666.vibemusic.dev` |
| 展示名（已定 2026-09-08） | `VibeMusic`；自研版预留 `VibeMusic Dev` |
| 自研版目录 | 用户尚未创建（创建后告知，AI 版一律不碰） |
| 技术栈 | Kotlin + Compose Material3 + Media3 + Retrofit/OkHttp/Moshi + Coil + DataStore |
| minSdk 26，targetSdk 35 | 全程冻结，升级需用户点头 |
| 构建 | Gradle 8.10.2（已有缓存）+ AGP 8.5.x + JDK（优先 17，没有则先验证 21，不行就装 17） |
| 后端 | `https://vibe.cyk666.top`（访客态 `guest:true` 直进） |

## 1. 阶段 0：链路基线（目标 1 天）

**做**：空工程 + 1 个播放按钮 + 硬编码流地址
`GET /api/songs/stream?sourceId=1895330088&name=予以&artist=队长&platform=netease`
Media3 `MediaSessionService` + 通知栏。

**验收测试（A0，全部通过才算过门）：**
- [ ] A0-1：`./gradlew assembleDebug` 一次通过（exit 0）
- [ ] A0-2：`adb install` 到真机成功，图标/展示名与自研版不冲突
- [ ] A0-3：点播放 3 秒内出声（对照：之前 Expo 包同地址无声）
- [ ] A0-4：通知栏出现播放控制，可暂停/恢复
- [ ] A0-5：`adb logcat` 无 FATAL/未捕获异常
- 不过门 → 停，问题在手机系统层（电池优化/音频焦点）或环境，先排环境，不写新代码。

## 2. 阶段 1：对接后端（目标 3~5 天）

**做**：搜索页（`/api/search`）、播放页（封面 Coil + 歌名 + 进度条 + 切歌）、访客直进。

**验收测试（A1）：**
- [ ] A1-1：搜"予以"有结果，点播 3 秒内出声
- [ ] A1-2：上一曲/下一曲/暂停/ seek 都生效，进度条与实际播放误差 < 1s
- [ ] A1-3：断网点播 → 明确错误提示（不许静默 `playing=false`，这是 Expo 教训）
- [ ] A1-4：切后台 5 分钟回来还在播（MIUI 电池优化对照；若被杀，引导用户开"忽略电池优化"，不算 bug）

## 3. 阶段 2：账号与歌单（目标 1 周）

**做**：登录页（后端登录接口，token 存 DataStore）、我的歌单（后端歌单接口对齐）、收藏/历史、"播测试音"对照按钮（产品化教训）。

**验收测试（A2）：**
- [ ] A2-1：登录→杀进程→重进，仍是登录态（token 持久化）
- [ ] A2-2：播不出声时点"测试音"：响 → 链路问题查 URL；不响 → 模块问题查音频（先对照再动手，铁律）
- [ ] A2-3：歌单增删与 Web 端一致（同一账号两边对照）

## 4. 阶段 3：体验与发布（目标 1 周）

**做**：Obsidian Bloom 深色主题、歌词（后端有就接）、睡眠定时、release 签名、GitHub 发包。

**验收测试（A3）：**
- [ ] A3-1：release 包体积 < 50MB，冷启动 < 2s
- [ ] A3-2：与自研版同装一机，图标/名字可区分，互不覆盖
- [ ] A3-3：README 含构建/签名/架构说明，可复现出包

## 5. 端点全覆盖优化（2026-09-09，28 端点已盘点：已实现 7，待实现 21）

后端契约总表（`vibeMusic-backend/.../controller`，信封 `{code,message,data}`，`code==200` 成功；
登录态一律 `Authorization: Bearer <token>`，访客只认本地）：

| 域 | 已实现 | 待实现（本计划覆盖） |
|---|---|---|
| 认证 `/api/auth` | login/me | register、change-password、profile(PUT)、logout、refresh（静默续签）、avatar/bg-image 上传 |
| 歌曲 `/api/songs` | search/stream/lyric | banner、random、play（播控）、image-proxy（封面代理，防混合内容） |
| 歌单 `/api/playlists` | list/songs | create/add-song/remove-song/update/reorder/delete/delete-batch/detail/recommend/export/import |
| 收藏 `/api/favorites` | — | toggle/list/ids/remove-batch（幂等 X-Request-Id 照抄 web 端） |
| 历史 `/api/songs/history` | — | GET 列表/export/remove（上报：播超 30s 或播完记一条，去重） |
| 推荐 `/api/recommend` | — | personalized（deviceId 优先用登录 userId，访客用 X-Device-Id 透传） |
| 下载 `/api/download` | — | POST /{sourceId} 触发服务端缓存 → check 轮询 → file 落盘 DownloadManager（离线播放） |
| 二期（明确不做） | — | assistant 聊天/SSE、monitor 缓存统计、es-health（hide 进调试面板都不给） |

### Phase 4：数据链修复 P0（约 3 天，先决条件）
- 后端：Flyway V6 给 `playlist_song` 加 `platform` 列；`add-song` 收 platform；
  `getSongs` 返回 `name`（兼容老 `songName`）+ `platform` + `album`；`mvn verify` 全绿才许发云。
- App：映射对齐（songName→name、platform 透传到 streamUrl）；收藏夹复测。
- 验收 A4：收藏夹歌名/歌手/封面/时长全对，点播出声有词；Web 端对照一致。

### Phase 5：搜索专项 P0.5（约 1 周，含"时好时坏"稳定性）
- App：结果页歌手过滤 chip + 排序切换；请求防抖 + 取消在途（抄 web 端 abort 语义）；
  骨架屏；搜索历史 + 热词（DataStore，点历史零等待）。
- 后端：打分调权（歌名全匹配/歌手匹配加权）；查搜索缓存 TTL、热词预热；
  云日志分段计时（Redis/ES/musicapi 各段耗时，对症下药）。
- 验收 A5：搜"来不及爱你"前 5 见筷子兄弟版；同一词二搜 <0.8s（含网络）；
  连输不堆请求；**同一词早晚各测 3 次全中（治"时好时坏"）**。

### Phase 6：歌单管理 P1（约 1 周，后端全现成，纯 UI）
- 新建/删除（含批量）/重命名/改描述；歌曲 ⋯ 菜单：加入歌单（多选页）、从歌单移除；
  歌单内长按排序（调 reorder）；导入网易/QQ 歌单（detail 拉取 → add-song 批量入自建歌单）。
- 验收 A6：Web/App 双向对照一致；删歌单二次确认；空歌单有空态。

### Phase 7：播放核心 P2（约 1 周，对标三家标配）
- 播放队列页：当前列表查看/切歌/删单首/清空（ExoPlayer timeline 现成）。
- 播放模式：顺序→列表循环→单曲循环→随机（RepeatMode + shuffle，状态持久化 DataStore）。
- 离线：下载按钮 → check 轮询 → file 落盘 → 离线标识；无网播下载过的歌。
- 验收 A7：四种模式行为正确且重启保持；飞行模式播离线歌；队列与通知栏双向同步。

### Phase 8：账号+收藏+历史 P3（约 1 周）
- 注册/改密/资料编辑/头像背景上传（coil + multipart）；退出登录清 token；
  refresh 静默续签（401 时先 refresh 重试一次，再不行才踢回登录——抄 web 端拦截器语义）。
- ❤ toggle（X-Request-Id 幂等照抄）+ `ids` 批量回显，搜索/播放/歌单三处红心同步。
- 历史：播超 30s/播完上报；最近播放横滑；清空；访客只存本地。
- 验收 A8：注册→登录→改资料→上传头像全通；红心三处一致；历史与 Web 对得上。

### Phase 9：发现页 P4（约 1 周， competitors 标配入口）
- 发现 tab：banner 轮播 → 点进歌单详情（detail）；personalized 每日推荐（refresh 下拉换一批）；
  random 猜你喜欢；recommend 热门歌单 → 一键导入自建。
- 封面一律走 image-proxy（防 https 混合内容，抄后端注释做法）。
- 验收 A9：弱网 banner 占位不崩；推荐可播可导入；tab 切换不重查（缓存）。

### Phase 10：美观 overhaul P5（约 1~2 周，最后整容）
- 沉浸播放页（封面大圆角 + 模糊渐变，Obsidian 底）、mini 条全局常驻、歌词/封面点按切换。
- 手势：下滑关闭、左右滑切歌（Expo 验证过的交互移植）。
- 三态补齐：空态文案、加载骨架、错误重试（现在裸奔的是"不美观"的一半）。
- 设置页：主题、音质选择（标准/高，透传已有 标准 位）、缓存清理、关于/版本。
- 验收 A10：与网易云/QQ 并排对比，核心三屏（发现/播放/我的）不丢面；无障碍触摸 ≥44px 回归。

二期（本次不排）：assistant AI 聊天、歌单文件导出、桌面歌词、Android Auto。

## 6. AI 军规（本计划最高优先级）

1. 一次一改，一改一装机，真机验证；不攒改动。
2. 报错贴 `adb logcat` 全栈文本；静默失败（无声无错）先加日志定位，不许猜。
3. `gradle-wrapper.properties` / AGP / Kotlin / minSdk 冻结；加依赖必须说理由+锁版本+用户同意。
4. 验收门没过，不进下一阶段；3 次失败停手 revert + 请示。
5. 自研版目录一律不碰；两边对照测试时注明版本（AI版 `v0.x-ai`）。
6. 每次交付前必须 `./gradlew testDebugUnitTest` 全绿 + assemble + aapt/apksigner 校验，三者缺一即不许传 LanShare；测试挂了先修测试或代码，禁删失败测试冒充通过。

## 7. 需要用户帮忙的点（提前列好）

- H1：自研版包名/目录/展示名（并行不打架，见下）
- H2：真机验证（装包、点播、报结果：响/不响+logcat）
- H3：release keystore 密码（发布阶段，现阶段不需要）
- H4：MIUI "忽略电池优化"开关（若 A1-4 被杀后台，用户亲手开一次）
