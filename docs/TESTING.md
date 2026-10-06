# TESTING — 测试分层、设备授权与真实账号安全

> 验证强度与任务相称；本文定义各层测试的入口、门控与红线。
> 构建前置：`export JAVA_HOME=~/tools/jdk-21.0.2.jdk/Contents/Home`（本机唯一可用 JDK，Gradle 8.13 跑不了 Java 25 的 JBR）。

## 测试分层

| 层 | 入口 | 内容 | 网络账号 |
|---|---|---|---|
| JVM unit | `./gradlew :app:testDebugUnitTest` | `HelperNextMappingTest`（元数据/歌词映射）、`ApiCacheTest`（账号缓存失效/分页偏移）、`LikedPageAccumulatorTest`（分页累积）、`eq/*`（音效处理器）、播放器回归 | 无 |
| 构建 | `./gradlew :app:assembleDebug`（instrumentation 改动另跑 `:app:assembleDebugAndroidTest`） | 编译/打包 | 无 |
| instrumentation（设备，离线/自还原） | `:app:assembleDebugAndroidTest` + `am instrument` | `VinylStateTest`（黑胶状态机）、`AudioFxLifecycleTest`（音效生命周期）、`HelperNextPlaybackTest`（真实直链静音播放） | 仅 playback 用直链 |
| live read（真实账号只读） | androidTest `HelperNextReadTest` | 搜索、收藏/关注/昵称、喜欢分页、歌手排序与专辑数、电台、详情、六档音质、QRC/roma/kana | 真实账号 |
| live write（真实账号写） | androidTest `HelperNextLikeWriteTest` | 喜欢/取消喜欢的**可逆**写测试 | 真实账号，默认不跑 |

Kotlin/应用行为改动的本地基线 = `:app:testDebugUnitTest` + `:app:assembleDebug`；只有改动需要 Android 运行时证据时才跑 instrumentation。

## 设备 instrumentation 的跑法

```bash
./gradlew :app:assembleDebugAndroidTest
adb -s <设备名> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <设备名> install -r app/build/outputs/apk/debug-androidTest.apk
adb -s <设备名> shell am instrument -w com.neumusic.player.test/androidx.test.runner.AndroidJUnitRunner
```

- 当前测试 APK 的 runner 是 `androidx.test.runner.AndroidJUnitRunner`（`app/build.gradle.kts` 的 `testInstrumentationRunner`）。
- **黑胶状态机回归 = `VinylStateTest`，经 AndroidJUnitRunner 运行。** 不要用旧的自定义 runner 名直接跑黑胶回归——2026-10-05 实测旧入口不被当前测试 APK 接受（历史文件 `VinylReviewInstrumentation.kt` 不作为当前回归入口；androidTest/AndroidManifest.xml 里虽仍有声明，但以实测为准；若未来要重新启用，先在设备上验证）。
- `HelperNextPlaybackTest` 检查组件解析的真实直链在 Media3 静音播放下推进；`AudioFxLifecycleTest` 检查音效随音频会话的生命周期。**两者结束后恢复本地设置。**

## live read（真实账号只读）

入口：`app/src/androidTest/.../data/api/HelperNextReadTest.kt`（配套 `HelperNextTestSupport.kt`）。

- 只做读操作：搜索、收藏歌单/专辑、关注歌手、昵称、我喜欢完整分页、歌手热度/最新排序与专辑歌数、电台、歌曲详情、六档音质、日文 QRC/roma/kana。
- 不测登录/微信/扫码流程（现有账号凭据由测试支持代码从组件凭据文件读取）。

## live write（真实账号写，默认不跑）

入口：`app/src/androidTest/.../data/api/HelperNextLikeWriteTest.kt`。**默认不执行**，必须显式开门：

```bash
adb -s <设备名> shell am instrument -w \
  -e executeWrites true \
  com.neumusic.player.test/androidx.test.runner.AndroidJUnitRunner
```

- 门控实现：`assumeTrue(getArguments().getString("executeWrites") == "true")`（`HelperNextLikeWriteTest.kt:15`），不开门即跳过。
- **写测试必须可逆并核对复原**：操作前后对比喜欢列表 ID 集合、服务器总数与原始分页行数，确认复原才算通过。
- 不得为证明端点存在而扩大写测试范围；一次改动只验证其涉及的写路径。
- **写失败不要自动重试**：业务码 `1000` 是风控限流，继续重试只会延长限流（读接口同时正常）。调试时尤其克制——反复验证写操作很容易把账号打到限流状态。
- 涉及账号切换的测试必须清理宿主缓存及其在途结果。

## 真实账号安全（所有 live 测试适用）

- 绝不提交、打印或写进报告/日志/commit 任何凭据票据（uin/musickey/euin/cookie 的值）。
- 优先可逆操作；写操作前想清楚恢复路径；核对复原。
- live 测试与常规 Gradle unit/构建验证是两回事，live 结果不能替代离线测试。

## 设备与授权

- **授权设备只有 `emulator-5554`（NeuMusic 默认验收设备），不访问真实设备。** 设备名会随启动顺序变（emulator-5554/5555），先 `adb devices` 看实际名字再 `-s`。
- 模拟器会自己掉线/卡死：`adb devices` 显示 offline 或 `adb shell` 挂住就是它——kill qemu 进程后重启 AVD（AVD 名 `neu`）。
- **禁止 `screencap`、禁止 Read 任何图片/视频文件**；截图与视觉测试由用户自己做。
- 允许的非视觉验证手段：`uiautomator dump`（读 text/content-desc/bounds；注意 `content-desc="返回"` 是按钮内部图标而非按钮本身）、服务端接口回读、`shared_prefs` 回读、logcat 查崩溃（AndroidRuntime）。
- 系统深色模式切换：`adb shell cmd uimode night yes|no`（验证 ShadeTheme 随系统）。
- 登录/微信/扫码流程不测。

## 用户验收交付

需要用户验收的应用改动，每次完成后必须：

1. 构建最新代码（`:app:assembleDebug`）；
2. `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`（保留现有应用数据，不清空账号或设置）;
3. 重新启动 `com.neumusic.player/.MainActivity`，确认应用处于前台。

**仅构建成功或提交代码不算完成交付。** 最终回复明确说明构建、安装、打开是否成功；设备不可用或安装/启动失败时如实报告原因，不得声称已可验收。
