# NeuMusic

一个 **新拟物风（Neumorphism）安卓音乐播放器**，用 Kotlin + Jetpack Compose 从零手写 UI 与播放内核，是一个以 UI/交互工程为主的**学习、研究项目**。

> 本项目仅供个人学习与研究使用，不提供任何内容服务；在线功能需要使用者自备并自行配置账号凭据，请遵守服务条款、控制请求频率、勿用于商业用途或批量抓取。

## 这是什么

NeuMusic 的核心目标是探索一件事：**在纯 Compose 里手写一套完整、自洽的新拟物设计系统，并把它落到一个真实可用的播放器上**。

视觉上，它坚持三条自定的光影法则（贯穿全部控件）：

1. **不在凸起上再做凸起**；
2. **不在凹陷中再做凹陷**；
3. **在凸起中，以「凹陷」表示选中，以「平」表示未选中**。

所有表面（按钮、卡片、轨道、弹层）都由一个自绘的 `Shade` 基元绘制：单次 Canvas 内依次画外阴影 → 底色 → 内阴影，光源固定左上，凸↔凹以 160ms 阴影交叉插值过渡，并支持全局「立体感强度」调节（阴影位移与模糊整体缩放）。强调色支持 Material You 壁纸取色（Monet）——只替换 accent，底色与阴影保持中性，以维持明度关系不被破坏。

## 功能

- **播放**：在线流媒体播放（凭据由使用者在应用内自行登录获取）、多音质档位自动降级、下载（可选目录与音质，MediaStore 落盘）
- **播放队列**：顺序/随机/单曲循环，随机的"上一首"按真实播放历史回跳；队列弹窗点选跳播
- **均衡器与音效**：系统 `Equalizer`/`BassBoost` 预设与自定义频段、动态范围压缩（DVC）、声道平衡、变速不变调/变调（0.5–2.0）
- **智能调音**：按曲目自带的风格码自动匹配均衡器预设（码表按实测样本校准，未知风格保持现状）
- **歌词**：行级 LRC 与 QRC 逐字（卡拉 OK 扫色）双模、翻译对照、字号设置、拖动浏览/双击跳转/3 秒自动回归
- **音频可视化**：播放栏电平条与封面圆盘周围的径向频谱环（优先系统 Visualizer FFT，无实现时回退管线 PCM 电平）
- **发现页**：收藏歌单/专辑、电台分组、搜索（歌曲/歌手/专辑/歌单四类 + 搜索历史）
- **外观**：深浅色（随系统/强制）、莫奈取色、立体感强度、歌词字号与翻译开关
- **工程细节**：无导航条的单栈+覆盖层页面体系、首页内容磁盘快照（冷启动即显）、歌单翻页取全与失败重试、双击列表回顶

## 怎么做的

- **UI**：Jetpack Compose 手写新拟物系统——`shadeSurface`（凸）、`shadeInset`（凹）、`shadePressable`（按压形变）、融合式标签栏等，全部走同一个阴影绘制核心；不使用 Material elevation/ripple
- **强调色**：Material You（`dynamicColorScheme`，Android 12+）只取 primary 作为 accent
- **播放**：Media3 / ExoPlayer，`PlaybackParameters` 实现变速变调；音频管线插入透传 `AudioProcessor` 采集 PCM 做可视化（免录音权限）
- **音效**：Android 平台 audiofx（`Equalizer` / `BassBoost` / `DynamicsProcessing`），全部挂到播放器音频会话
- **逐字歌词**：QRC 密文解码由内嵌 HelperNext 组件完成（非标准类 DES 三重解密 → zlib，算法与 qrc-decoder 同源），渲染层用双层文本 + `clipRect` 扫色（AMLL 式）
- **数据**：内嵌 [HelperNext](https://github.com/LeeDespo/QQMusicApi_HelperNext) Rust/BoltFFI 组件直连 QQ 音乐接口（凭据、设备档案、限流、QRC 解码均在组件内），宿主各域 Api 只做 HelperNext typed models → NeuMusic models 的薄映射，StateFlow 响应式；接口字段与调用细节见源码注释（均经实测校准）

## 构建

```bash
# 需要 JDK 17+（本机开发用 JDK 21）与 Android SDK（compileSdk 36）
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio（Ladybug 及以上）打开工程运行。最低支持 Android 8.0（API 26），包含 arm64-v8a、armeabi-v7a、x86、x86_64。

## 致谢与参考

本项目在实现过程中学习、参考了以下优秀开源项目（按用途）：

- [qqmusic-api-python](https://github.com/luren-dc/qqmusic-api)（LGPL-3.0）与 [simple-music 的 QQ 音乐接口梳理](https://github.com/Yyyangshenghao/simple-music/blob/master/docs/qq-music-api.md) —— 接口形态研究
- [ShadeCraft](https://github.com/DingMouRen/ShadeCraft) —— 新拟物光影基元的思路起点
- [applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)（AGPL-3.0，仅研究其渲染思路与 QRC 格式，未引入代码）与 [qrc-decoder](https://github.com/apoint123/qrc-decoder)（MIT，QRC 解密算法）
- [Moriafly/SaltPlayerSource](https://github.com/Moriafly/SaltPlayerSource)（MIT）—— 音效功能形态参考

## 许可

本项目以 GPL-3.0 开源，完整文本见 LICENSE。QQ 音乐访问使用嵌入式 [HelperNext](https://github.com/LeeDespo/QQMusicApi_HelperNext) Rust/BoltFFI 组件（GPL-3.0-or-later）；Kotlin/JNI 产物及来源校验记录位于 `app/helpernext/`。构建无需 Python 边车或常驻服务。组件版本锁定在 `app/helpernext.lock.json`，更新脚本下载并校验官方 Android Release 资产后整体替换 `app/helpernext/`，常规 Android 构建直接使用仓库中的配套产物。

Android 继续负责 Media3 播放、MediaStore 下载、文件命名、已下载台账与歌词渲染；组件负责在线接口、设备身份、凭据、限流和 QRC 解码。原 SharedPreferences 登录凭据（legacy credentials）首次启动会一次性导入 HelperNext 托管的私有存储，导入成功后删除宿主侧旧副本。
