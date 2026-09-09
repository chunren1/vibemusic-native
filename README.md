# VibeMusic Native（AI 版原生 App）

原生 Android 客户端，后端 `https://vibe.cyk666.top`（访客直进，登录接自家账号）。
包名 `com.cyk666.vibemusic`，展示名 `VibeMusic`。详细计划与验收门见 `PLAN.md`。

## 技术栈（版本冻结，升级需批准）

Kotlin 2.0.20 + Compose Material3 + Media3 1.5.1 + Retrofit 2.11.0 / OkHttp 4.12.0 /
Moshi 1.15.2 + Coil 2.6.0 + DataStore 1.1.1；AGP 8.5.2 / Gradle 8.10.2 / JDK 21 实测可编；
compileSdk 35 / minSdk 26 / targetSdk 35。测试：JUnit 4.13.2 + Robolectric 4.14.1。

## 常用命令

```bash
./gradlew testDebugUnitTest --no-daemon   # 验收门：24 用例全绿才许交付
./gradlew assembleDebug --no-daemon       # 调试包
VIBEMUSIC_KEYSTORE_PASSWORD=<见凭据库> ./gradlew assembleRelease --no-daemon
~/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk
~/Android/Sdk/build-tools/35.0.0/apksigner verify app/build/outputs/apk/release/app-release.apk
./gradlew --stop                           # 收尾清 daemon（机器内存小）
```

交付三件套（PLAN §5 第 6 条）：单测全绿 + 构建 + aapt/apksigner 校验，缺一不传包。
传包：LanShare（`http://192.168.24.107:8000`）根目录分片上传，下载验大小一致。

## 签名

keystore：`~/.dsh/keystores/vibemusic-native-release.jks`（alias `vibemusic`，RSA 2048）。
密码只存 `~/.dsh/.credentials.yaml` 变量 `VIBEMUSIC_KEYSTORE_PASSWORD`，禁进仓库、禁明文记别处。
构建读环境变量，缺失则 fail-fast 报错。

## 架构（data → business → presentation，禁跨层）

- `VibeApi.kt`：后端契约（search/stream/lyric/login/me/playlists），信封 `code==200` 才算成功；
  OkHttp 拦截器只在 token 非空时带 `Bearer` 头，访客链路零影响；401 统一转"密码错/登录过期"。
- `PlaybackService.kt`：唯一 ExoPlayer 属主。坏歌自动跳下一首（连错 3 次熔断停手）；
  `shouldAutoSkip()` 为纯函数（单测钉住），行为变更先改真值表。
- `Song.kt`：`streamUrl()` 用 `Uri.Builder` 编码；`toMediaItem()` 把 platform 塞 extras，
  供 Activity 重建时 round-trip。
- `AuthStore.kt` / `PlaybackStateStore.kt`：DataStore `"auth"`（token 三件套，密码永不存）
  与 `"playback"`（上次队列快照）分离；讀寫永不抛异常。
- `MainActivity.kt`：单 Activity，三 tab（搜索/播放/我的）+ 登录页，全经 MediaController
  驱动同一 player；报错一律 Snackbar 带歌名和原因，禁静默失败；切后台重建时从
  controller 时间线或快照恢复队列（不自动播）。

## 血泪教训（新需求先读）

1. 一首坏源曾毒死整个队列只能重启 → 自动跳过 + transport 空闲态先 prepare。
2. Activity 重建丢队列按钮全灰 → 时间线重建 + 快照恢复双保险。
3. "播测试音（对照实验）"按钮是播歌对照组：响=链路问题，不响=模块问题，先对照再动手。
