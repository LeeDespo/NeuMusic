# 音效页实现与验收

日期：2026-10-04。调研原稿：`.zcode/audio-fx-research/audio-fx-plan.md`，现已加入 R3 实施补充。独立审阅：[audio-fx-plan-review.md](../audio-fx-plan-review.md)。未安装额外插件或 skill；现有 Android 工程、设计规范与协作工具足够完成本次工作。未提交 Git。

## 完成内容

- 音效页改为「均衡 / 动态 / 其它」，保留滚动页头、新拟物控件和原返回动画，按各项能力显示状态。
- 系统 EQ 使用实际设备频段；精确 EQ 提供10段和原始参数滤波器、自动前级、量化前 linked limiter及短参数交叉淡入。曲线在绘制阶段读取，系统曲线标为估算。
- 预设 v2 保留源频点、前级、参数、稳定 id 与来源；兼容旧数据。文本导入/导出支持 EqualizerAPO、GraphicEQ；跨引擎用有界拟合，保留原始来源，显示误差。旧 selected/genreMap 仍用唯一名称键，未来可迁移为 id。
- AutoEq 离线218项，检索、来源展示、导入和删除；来源 commit、许可和校验保存在 assets/autoeq，提供更新脚本。此为 Score≥80 子集，非完整耳机库。
- DVC 五档、系统限幅参数、声道平衡、低音能力回读、独立响度增强；速度/音调、智能映射及系统空间音频只读状态。
- 播放器拥有音效生命周期；暂停只停止发布可视化并清零，不销毁播放器。显式关闭释放所有资源，再播放可重建。更新处理器通过播放线程消息，不在 UI 线程操作 PCM 链。
- 滑杆实时预览、结束提交；旧预设手势不会写入新预设。开关与滑杆补充触控尺寸及无障碍语义。可视化统一开关，权限拒绝走 PCM、授权可重试 FFT。

## 自动验证

命令：`JAVA_HOME=~/tools/jdk-21.0.2.jdk/Contents/Home ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`。最终构建通过，debug APK 已安装到 emulator-5554。

| JVM 测试类 | 数量 | 失败 | 错误 |
|---|---:|---:|---:|
| AudioEqTest | 8 | 0 | 0 |
| EqImportTest | 5 | 0 | 0 |
| EqProcessorTest | 6 | 0 | 0 |

19 项 JVM 测试通过：包括 44.1/48/96kHz、mono/stereo、PCM16/FLOAT 的实际缓冲处理、空缓冲与别名安全、输出边界、linked limiter 声道比例、实际渲染 RMS 频响、重置、预设迁移、拟合及全部218项 AutoEq 文本校验与往返。

API 36 模拟器真实本地48kHz WAV 播放测试1项通过。覆盖暂停保留会话与进度、恢复、实际换 session、播放参数预览不落盘直到提交、参数预设拖回原值仍移除原filters、旧 generation 编辑拒绝、显式 release 后重新播放。测试恢复原 SharedPreferences 并删除本地 WAV。[设备结果](device-tests.txt)。

设备测试运行：安装应用与 androidTest APK 后，`adb -s emulator-5554 shell am instrument -w -r com.neumusic.player.test/androidx.test.runner.AndroidJUnitRunner`。为保留用户数据未使用会卸载应用的测试流程。

## 手动界面验证

在1080×2400、API36模拟器检查三分区、真实设备五段与精确十段、AutoEq 搜索 AKG K371（区分标准/更换耳垫来源）、导入和长按删除。删除测试导入条目后回到系统引擎与 Rock。其它预设、账号和收藏保留。

权限拒绝显示 PCM 回退；临时授权并开关可视化后显示系统 FFT；验证后已恢复原拒绝权限。暂停清零由真实播放测试断言。截图：[动态](dynamics.png)、[AutoEq搜索](autoeq-search.png)、[PCM能力状态](capabilities-pcm.png)、[FFT能力状态](capabilities-fft.png)。

## 验证边界

- 系统 EQ 的 Q 与音效执行细节由厂商决定；拟合误差是估计模型结果，曲线使用48kHz参考，不代表设备测量。
- 仅验证 API36 模拟器，未覆盖API26/28及真实蓝牙、USB、多声道、耳机头部跟踪。DVC/响度的主观听感仍需实体设备试听。
- 文件导入/导出使用系统 SAF，编解码和全库数据已自动验证；未在本轮逐个操作所有文件提供者。
- 响度增强独立于 EQ，不能承诺系统限幅一定兜住厂商 LoudnessEnhancer 的输出；默认关闭。

## 2026-10-05 提交前复核

基础 JVM 测试及 debug/Android 测试 APK 构建通过。复核修复：显式关闭和新播放请求失效化旧链接/歌词请求；已排队的播放操作同样检查代次。手动精确 EQ 曲线导出保留当前引擎的 Q 参数。

emulator-5554 上 AudioFxLifecycleTest 通过（1 项），新增不响应取消的延迟 resolver 回归，确认关闭后没有播放器、当前曲目及播放态，并验证之后可主动重建播放。精确引擎无预设的导出 Q 校验通过。未执行截图或在线写操作，保留账号与设置。
