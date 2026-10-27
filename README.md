# VibeMusic Native（AI 版原生 App）

原生 Android 客户端，后端 `https://vibe.cyk666.top`（访客直进，登录接自家账号）。
包名 `com.cyk666.vibemusic`，展示名 `VibeMusic`。详细计划与验收门见 `PLAN.md`。

当前版本：`2.0.17-ai`（versionCode 95，正式通道）；上一个稳定版 `2.0.16-ai`。
发版走 CI 打 tag 直发 GitHub Release（LanShare 传包已退役）；CI 发版前会先跑 `testDebugUnitTest` 作为发布门。

## 技术栈（版本冻结，升级需批准）

Kotlin 2.0.20 + Compose Material3 + Media3 1.9.3 + Retrofit 2.11.0 / OkHttp 4.12.0 /
Coil 2.6.0 + DataStore 1.1.1，JSON 走 org.json（Moshi 已移除）；AGP 8.5.2 / Gradle 8.10.2 / JDK 21 实测可编；
compileSdk 35 / minSdk 26 / targetSdk 35。测试：JUnit 4.13.2 + Robolectric 4.14.1。

## 常用命令

```bash
./gradlew testDebugUnitTest --no-daemon   # 验收门：937 用例全绿才许交付（2026-10-19 实测）
./gradlew assembleDebug --no-daemon       # 调试包
VIBEMUSIC_KEYSTORE_PASSWORD=<见凭据库> ./gradlew assembleRelease --no-daemon
~/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk
~/Android/Sdk/build-tools/35.0.0/apksigner verify app/build/outputs/apk/release/app-release.apk
./gradlew --stop                           # 收尾清 daemon（机器内存小）
```

交付三件套（PLAN §5 第 6 条）：单测全绿 + 构建 + aapt/apksigner 校验，缺一不传包。
传包：CI 打 tag 直发 GitHub Release（stable/beta 双通道），包在 Release 取，不再走 LanShare。

## 签名

keystore：`~/.dsh/keystores/vibemusic-native-release.jks`（alias `vibemusic`，RSA 2048，09-13 已轮换新 key）。
密码只存 `~/.dsh/.credentials.yaml` 变量 `VIBEMUSIC_KEYSTORE_PASSWORD`，禁进仓库、禁明文记别处。
构建读环境变量，缺失则 fail-fast 报错。旧 key 文件保留为 `.LOCKED` 纪念，不再用于签名。

## 架构（data → business → presentation，禁跨层）

- `VibeApi.kt`：后端契约（search/stream/lyric/login/me/playlists），信封 `code==200` 才算成功；
  OkHttp 拦截器只在 token 非空时带 `Bearer` 头，访客链路零影响；401 统一转"密码错/登录过期"。
  覆盖 5 平台：网易/QQ/咪咕/酷狗/B站（B站单曲过滤，只进单曲）。
- `PlaybackService.kt`：唯一 ExoPlayer 属主，继承 `MediaLibraryService` 走系统级恢复通道
  （进程被杀只走官方 `onPlaybackResumption`）；坏歌自动跳下一首（连错 3 次熔断停手）；
  `shouldAutoSkip()` 为纯函数（单测钉住），行为变更先改真值表。
- `Song.kt`：`streamUrl()` 用 `Uri.Builder` 编码；`toMediaItem()` 把 platform 塞 extras，
  供 Activity 重建时 round-trip。
- `AuthStore.kt` / `PlaybackStateStore.kt`：DataStore `"auth"`（token 三件套，密码永不存）
  与 `"playback"`（上次队列快照）分离；读写永不抛异常（三处都带 corruptionHandler）。
- `MainActivity.kt`：单 Activity，四屏（首页/我的/播放器/歌词，QQ 风格深色）+ 登录页，全经 MediaController
  驱动同一 player；报错一律 Snackbar 带歌名和原因，禁静默失败；切后台重建时从
  controller 时间线或快照恢复队列（不自动播）。
- 搜索 UX：联想词点选即收起键盘；点歌只把该单曲塞进当前队列即播，不整单替换。
- 双更新通道：稳定版/测试版开关，prerelease 走 beta 通道。

### 近期能力（一句话索引，细节见 PLAN.md 与 git log）

- 封面兜底 `coverModelOrDefault` + CI 门禁，裸调封面源是红线。
- 播放进度 7 天过期（`POSITION_EXPIRE_MS`），过期丢进度不丢队列。
- 更新包断点续传（HTTP `Range`，416 才整体重来）。
- 去 Cookie 输入：App 零输入，服务端主备源兜底。
- 搜索结果内存缓存（`SearchResultCache`，5 分钟 TTL，重复搜秒出）。
- 屏幕方向默认锁竖屏，设置页可切"跟随系统"。
- 歌单六态分支收口，重进不再闪空。
- 搜索历史云同步（与本地 DataStore 合并去重）。
- Bugly 4.1.9 崩溃上报 + `PlayDiag` 操作面包屑，排障先看面包屑再动手。
- 焦点补试阶梯 + 用户意愿门 `shouldFireRetryProbe`：未点按绝不自动续播，暂停即取消补试。
- 卡死自愈交给 Media3 1.9.0 原生 `StuckPlayerException`（想播 + READY 10 秒无进度即抛错）→ 走既有 `onPlayerError` 重试/换源；自建看门狗已于 2026-10-26 删除。
- 保活引导 `shouldShowKilledHint`：播放键失灵提示直指「我的→设置→后台保活」。
- 可测性：上述门与纯函数都有单测钉住（939 例），行为变更先改测试再改实现。

## 血泪教训（新需求先读）

1. 一首坏源曾毒死整个队列只能重启 → 自动跳过 + transport 空闲态先 prepare。
2. Activity 重建丢队列按钮全灰 → 时间线重建 + 快照恢复双保险。
3. "播测试音（对照实验）"按钮是播歌对照组：响=链路问题，不响=模块问题，先对照再动手。
4. **通知/媒体按钮路径不要用"拦截式 session 回调"做带声意图物化**：1.0.10-ai/1.0.11-ai（拦截 9 类命令 → 通知栏串歌）与 1.0.27-ai/1.0.28-ai（窄口径仅 PLAY_PAUSE → 播放全死）两次实装两次回滚，**第三次不做**。进程被杀后的恢复只走官方 `MediaSession.Callback.onPlaybackResumption`（见 `PlaybackService.onCreate`），Activity 侧 `ensureTimeline` 是唯一兜底物化路径。
5. 焦点被拒≠卡死：Media3 会把 playWhenReady 置回 false 且**平台不再回调**，所以"点了没反应"必须由 App 侧有界补试兜（`shouldRetryDeniedPlay`）；补试还要过意愿门 `shouldFireRetryProbe`（未点按不续播、暂停即取消）。瞬时丢失本身是框架自动续播，不要自己接管焦点。
6. DataStore 三处都必须带 `corruptionHandler`（round6 起已加）：文件损坏时 DataStore 连写都会失败，表现为"进度/队列/搜索历史静默永久失效"，很难从现象反推。
7. **"点了播放没反应"先查库版本，别急着自建看门狗**（2026-10-26 审计）：2026-10-10/11 按"焦点等待卡死"假设手搓了 `stop→prepare→play` 看门狗，真机从未验证成功，`44de1c0` 默认关闭后成了 101 行永不执行的死代码，2026-10-26 整体删除。真正的修复来自 Media3 1.5.1→1.9.0：① 新增 `StuckPlayerException` 卡死检测（想播 + `STATE_READY` **10 秒无进度**即抛错，默认开启）→ 走既有 `onPlayerError` 重试/换源；② 前台服务态在暂停/失败后多留 10 分钟；③ wake lock 默认开。教训：**库内已有"检测 + 抛错"机制时，App 侧不要自建状态机**；且判断"某次升级修了什么"要读官方 release notes + 反编译核对，不能靠排除法猜。
