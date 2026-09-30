# Baseline Profile（Compose 冷启动优化）

> 状态：脚手架已就绪（`:baselineprofile` 模块＋生成器＋AGP 接线），待接真机跑一次生成。
> 预期收益：冷启动与首屏 jank 改善（官方中位数约 20–30%，以实测为准）；无副作用，不改任何业务代码。

## 一次性生成（USB 连 Vivo 测试机）

```bash
adb devices  # 确认看到设备
./gradlew :baselineprofile:generateBaselineProfile --no-daemon
```

任务日志会打印产物目录（形如
`baselineprofile/build/outputs/...additional_output/.../`），把里面的
`baseline-prof.txt`（如有 `startup-prof.txt` 一并）拷到：

```
app/src/main/baselineProfiles/
```

AGP 会自动把该目录的 profile 打进此后所有 release 包，无需改任何配置。
拷入后照常打 tag 走 CI 发版即可。

## 验证

- 生成后看任务输出的 `baseline-prof.txt` 行数（几百行起步为正常；几十行说明覆盖不足，重跑前多点几个页面）。
- 发版后冷启动对比：`adb shell am start -W` 计时，或看 PlaybackService 既有启动日志。
- 回滚：删掉 `app/src/main/baselineProfiles/` 下文件即回到现状（零风险）。

## 版本钉死（2026-09-30 实测可配）

- `androidx.baselineprofile` Gradle 插件 1.4.1（Google Maven marker 实测在位）
- `benchmark-macro-junit4` 1.4.1、`test-ext:junit` 1.3.0、`uiautomator` 2.3.0
- 生成器 API 按 1.4.1 源码对齐：`collect(packageName, …, includeInStartupProfile, …)`，
  注意 `includeInStartupProfile` 默认为 false，启动规则必须显传 true。

## 无真机时的备选

`:baselineprofile` 已配 Pixel6 API34 模拟器（managedDevices）：
`./gradlew :baselineprofile:pixel6Api34GenerateBaselineProfile --no-daemon`
（需本机装 emulator＋system-image，WSL 下不如真机顺，仅备选）。
