# AGENTS.md — vibemusic-native

> 本文件**由人手编写并审校**，保持简短。**禁止让 agent 自动改写本文件**（ETH Zurich 2026 研究：LLM 生成的 AGENTS.md 无收益、平均降低成功率约 3% 且推理成本 +20%）。改动请走用户确认。
> 项目全景见 `README.md`（概览）与 `PLAN.md`（阶段计划 + 验收门 + AI 军规）。

## 概述
VibeMusic 原生 Android 客户端：**纯 Kotlin + Jetpack Compose (Material3)**，单 Activity / 单模块（`:app`），后端 `https://vibe.cyk666.top`（游客模式 + 自建账号登录）。当前线上 `2.0.18-ai`（versionCode 96，2026-10-28 发布），发版由 CI tag 触发。

## 技术栈（冻结，改动需用户批准）
- Kotlin 2.0.20 · AGP 8.5.2 · Gradle 8.10.2 · JDK 21（`jvmTarget 17`）
- compileSdk 35 / minSdk 26 / targetSdk 35
- Compose BOM 2024.10.00 + Material3 + material-icons-extended；Media3 1.9.3；Retrofit 2.11.0 / OkHttp 4.12.0；Coil 2.6.0；DataStore 1.1.1；Bugly 4.1.9
- JSON 走 **`org.json`（不用 Moshi）**
- 测试：JUnit 4.13.2 + Robolectric 4.14.1（83 个测试文件 / 964 用例）
- 版本单一来源：`gradle/libs.versions.toml`

## 常用命令（工具链 `~/Android/Sdk/build-tools/35.0.0/`）
```bash
./gradlew testDebugUnitTest --no-daemon    # ★ 验收门：964 用例必须全绿
./gradlew assembleDebug --no-daemon        # debug 构建
VIBEMUSIC_KEYSTORE_PASSWORD=<~/.dsh/.credentials.yaml> ./gradlew assembleRelease --no-daemon
aapt dump badging app/build/outputs/apk/release/app-release.apk   # 校 versionName/versionCode
apksigner verify app/build/outputs/apk/release/app-release.apk    # 校签名
./gradlew --stop                           # 小内存机器：用完杀 daemon
```

## 目录与分层
- 代码平铺于 `app/src/main/java/com/cyk666/vibemusic/`（57 个 `.kt`）；测试在 `app/src/test/java/.../`（83 个）
- 分层 `data → business → presentation`，**禁止跨层**（UI 不得直连网络/存储）
- 单一属主：`PlaybackService.kt` = 唯一 ExoPlayer 持有者；`MainActivity.kt` = 单 Activity 4 屏；`VibeApi.kt` = 后端契约
- 命名：文件/类 `PascalCase`，函数/变量 `camelCase`，常量 `UPPER_SNAKE`；`*Screen.kt`=界面，`*Store.kt`=DataStore，`*Ui`/`UiAtoms.kt`=设计系统，主题在 `UiTheme.kt`
- 状态：DataStore Preferences（`AuthStore`/`PlaybackStateStore`/`SearchStore`），媒体控制走 `MediaController`

## 规范 / 军规
- **一次只改一件事**；改行为先改测试真值表（`shouldAutoSkip` / `shouldFireRetryProbe` 等纯函数抽取出来专门测）
- **交付三验**：单测全绿 + 构建 + aapt/apksigner 校验，**三者缺一不可**
- **发版**：先改 `app/build.gradle.kts` 的 `versionCode`/`versionName`（`X.Y.Z-ai`），再 `git tag vX.Y.Z-ai && git push origin <tag>` 触发 CI（CI 校验 tag ↔ versionName 一致；tag 含 `beta` → 预发布通道）
- 提交信息沿用 `feat(app):` / `fix(app):` 中文风格
- 三个 DataStore 必须带 `corruptionHandler`（否则写失败会静默永久）

## 禁止
- ❌ 禁止删除失败测试冒充通过，禁止跳过 `testDebugUnitTest`
- ❌ 禁止改动冻结工具链（Gradle / AGP / Kotlin / minSdk）——需用户批准
- ❌ 禁止新增依赖未说明理由 / 未锁版本 / 未经用户同意
- ❌ 禁止调用裸 `coverModel()`——必须 `coverModelOrDefault` + 错误兜底（CI grep 门拦截）
- ❌ 禁止用"拦截 session 回调"处理带声媒体键（已两次回滚，见 `README.md`）
- ❌ 禁止碰 `com.cyk666.vibemusic.dev`（用户自写版，另立项目，勿动）
- ❌ 禁止提交密钥 / `local.properties` / `*.jks` / 凭据（签名密码只在 `~/.dsh/.credentials.yaml`）
- ❌ 禁止提交构建产物（`build/`、`*.apk`）、`generated-images/`、`*.log`

## 关键路径
- Release keystore：`~/.dsh/keystores/vibemusic-native-release.jks`（alias `vibemusic`；密码 → `~/.dsh/.credentials.yaml: VIBEMUSIC_KEYSTORE_PASSWORD`）
- CI：`.github/workflows/release.yml`（tag 触发；Gitee 镜像是 App 的**首选更新源**，失败即 CI 变红 + 匿名自证 .apk 已挂上，勿再当 best-effort）
- 详细计划 / 验收门：`PLAN.md`
