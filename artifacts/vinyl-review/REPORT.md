# NeuMusic 黑胶唱片机接续验收 · 2026-10-04

本轮主工程修复与独立审查完成。原实验工程 VinylLab 编译通过，保持历史版本；本轮按最新要求修改 NeuMusic，不再要求两工程机械件/胶囊外观完全相同。

## 修复

- **胶囊不动**：旧组件只读取 `VizHost.levels`（FFT），漏接 `PlayerHost.vizProcessor.levels`（PCM）。模拟器日志明确报告系统 Visualizer 创建失败 error -3，走 PCM，正好覆盖旧缺陷路径。现在与普通封面可视化一样按 `usingFft` 二选一。
- **胶囊外观**：16 根完整圆头胶囊，纯主题角色色。删除凹陷槽、模糊发光、双色高光；真实振幅经平方根压缩控制高度，静音仍为零；暂停为短点。电平 State 仅在 Canvas 绘制阶段读取，不带动整个播放页重组。可视区域 48dp，宽度等于盘径。
- **机械件**：底座、唱臂、唱头各为完整色块；删除同心装饰、金属高光、唱头内嵌面板与针尖点。臂宽 0.050D→0.034D，唱头宽 0.105D→0.090D。
- **内折连接**：唱头从连接点伸出，绕连接点向内折 20°，不再绕矩形中心；按有效针尖长度反解连接点，保留原半径轨迹。Finger Lift 位于外侧，并与唱头共用轮廓/颜色。四个代表半径的几何计算误差均小于 1e-6D。
- **换片遗漏**：按歌曲 mid 区分身份，修复同专辑同封面及无封面歌曲漏切换；revision 保留换片后半程到达的新请求，等待最新请求稳定后继续换片，不对过时封面重新加速/落臂。暂停态同样消费新曲目，统一三秒视觉静默窗口。音频切换仍由 PlayerHost 立即负责。
- **片尾重入**：移除旧版提前抬臂逻辑。该逻辑在0.5倍速下，视觉停盘完成时实际歌曲仍未结束，idle 会再次加速。现在直接跟随真实结束/曲目变化；符合旧方案 §3.4 的进度绑定简化。
- **光影读取**：`DayLightHost.current` 下沉到绘制 lambda，设置/时刻改变只触发对应重绘。

## 验证

- NeuMusic `assembleDebug` / `assembleDebugAndroidTest` 编译成功；VinylLab `assembleDebug` 编译成功。
- `git diff --check` 通过。
- 专用 Instrumentation 在 **emulator-5554** 上验证真实状态机：进度绑定、片尾不重复起播、同封面不同曲目、换片后半程新请求、暂停快速切歌窗口。新请求待处理时还检查不得对旧封面加速。
- 真音乐播放：FFT失败日志确认PCM回退；截图01/02/03逐根胶囊测量高度分别约45–47px、45–49px、51–57px，证明来自实际音频的变化。04是提高可见高度/角色色后的最终浅色界面；05是最终深色暂停界面。无模拟波形。
- 后半进度实播日志：`ARM r≈0.3446 deg≈109.39`，较外圈0.48D向内移动。
- 截图浏览 **5/5**，子代理未截图。01–03是中间验证，04–05是最终外观。
- 独立审查确认无重要遗留，提出的四项问题均已修复并复核。
- 模拟器稳态 gfxinfo：769帧、3帧未达系统deadline（0.39%）、P50 22ms、P90 26ms、P95 27ms、P99 32ms。旧版legacy统计仍显示68.27%；不能据此宣称稳定60fps或真机性能通过，也未做严格同条件旧版对照。

## 初始化与边界

- 已更新根目录 `AGENTS.md`：黑胶新规范、M3 Expressive明确例外、工具选择、代理和测试入口。该文件原本被仓库忽略，保留本地规范用途。
- 已检查现有项目Android skills，使用 team-mode、neumorphism、ui-ux-pro-max；检查官方skill目录及插件目录后没有发现此次本地修复所需的新工具缺口，没有重复安装或连接无关账号。
- 17890代理连通正常。Python工具的证书问题通过本次命令清除代理并使用Homebrew CA证书解决，未关闭TLS校验。
- 未操作真机，未调用喜欢等写接口，未更改 `.zcode` 历史材料，未提交代码。
- 真机FFT分支、其他尺寸/横屏、大字号与减少动画的完整回归未执行；本轮针对指定黑胶缺陷验收。

M3 Expressive使用颜色、形状与运动表达层次的背景依据：[Google 官方介绍](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/)。本轮自绘胶囊/机械件遵循用户指定设计语言，并非声称官方提供了唱片机或音频频谱组件。

## 复跑

```sh
JAVA_HOME=/Users/mac/tools/jdk-21.0.2.jdk/Contents/Home ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.neumusic.player.test/com.neumusic.player.VinylReviewInstrumentation
```
