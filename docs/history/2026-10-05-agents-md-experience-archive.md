# 旧版 AGENTS.md 经验存档（归档于 2026-10-07）

> 本文件是仓库根 `AGENTS.md` 在 2026-10-07「文档体系落地」批次改写前的完整快照（末次维护至 2026-10-05）。
> 原 AGENTS.md 当时未被 git 跟踪（被 .gitignore 忽略），覆盖后无版本可回退，故整篇存档于此。
> 属历史证据：其中部分条目（测试入口、组件产物供应方式、架构描述）已被 docs/TESTING.md、docs/HELPERNEXT.md、docs/ARCHITECTURE.md 的现行规则取代，以现行文档为准；日期化批次叙事按时间线保留原样。

# AGENTS.md

## 2026-10-05 当前 QQ 数据架构（覆盖下文旧版 Kotlin 直连描述）

QQ 请求、签名、设备档案、凭据存储、QRC 解密已改由 HelperNext Rust 0.2.0 通过 BoltFFI/JNI 内嵌执行。没有边车或 Python 运行时。`data/api/HelperNext.kt` 在应用私有 `filesDir/HelperNext` 初始化；所有网络 FFI 调用放在 IO。`QqCore.kt` 和 `QrcCodec.kt` 已移除；各域 Api 只做组件 JSON 到 UI 数据的薄适配。歌词显示优化、Media3、音效、MediaStore 下载及下载台账仍由 Android 宿主负责。

`app/helpernext` 的 Kotlin/JNI 四 ABI 必须从同一源码同时生成；查看 `manifest.json` 和 LICENSE，运行 `scripts/update-helpernext.sh` 更新，不要单独替换一份库或手改生成绑定。组件目标 minSdk 24，应用仍 minSdk 26，所有 ELF LOAD 对齐 16KB。没有相邻源码仓库时，脚本从 manifest 的远程 revision + source.patch 校验重建源码。

凭据唯一持久来源是 `files/HelperNext/Credential/qqmusic-credential.json`；旧 SharedPreferences 副本只在组件文件有效后删除。不要打印、提交或写进报告任何票据。账号切换须清理宿主缓存及其在途结果。

歌曲页使用 `nextOffset` 的原始行偏移，不能按过滤/去重后的曲目数或请求 num 推进；无 MID 的不可用记录仍占上游位置，服务器 total 与可播放 ID 数可以不同。歌手服务可能至少回 30 行，组件先按请求 limit 限原始窗口，再解码。歌手歌曲和专辑的加载状态分别缓存。

设备唱片回归使用 `VinylStateTest` 通过 AndroidJUnitRunner 运行；旧自定义 `VinylReviewInstrumentation` 未被当前测试 APK 注册，直接运行旧 runner 名称会失败。HelperNextPlaybackTest 检查真实直链静音播放，AudioFxLifecycleTest 检查音效生命周期；均恢复本地设置。

本次授权只允许 `emulator-5554`，不访问真实设备；不查看截图；登录/微信/扫码流程不测。现有账号真实读测试与喜欢/取消喜欢的可逆测试入口在 androidTest 的 HelperNext*Test；写测试必须显式 executeWrites=true 并核对复原。


新拟物风安卓音乐播放器（NeuMusic）：在线播放（QQ 音乐）+ 本地音乐。包名 `com.neumusic.player`。

## 工程结构

- `app/` — Kotlin + Jetpack Compose。
  - `shade/Shade.kt` — 视觉与交互基元（风格参考 [ShadeCraft](https://github.com/DingMouRen/ShadeCraft)）：`shadeSurface`（凸起）、`shadeInset`（凹陷）、`shadePressable`（按压交互）、`ShadeFusedTabs`/`ShadeFusedTab`（选中标签与列表融为一体的标签栏）。**新拟物控件只用这几个**；黑胶机械件与音频胶囊使用下文明确授权的 M3 Expressive 例外，其余页面不引入 Material elevation/ripple。
  - `data/` — `AppLog`（结构化诊断日志：filesDir/diagnostics.log，设置里可开关/清空/限容(MB，超限砍前一半)/导出；默认开）、`RecommendStore`（推荐歌曲预缓冲 5 首落盘）、`api/`（`HelperNext`（BoltFFI/JNI 组件入口，见顶部架构段）+ 按域薄适配：`SearchApi`、`PlaylistApi`、`RadioApi`、`SongApi`（取链接 + 喜欢写入）、`LyricApi`、`UserApi`、`QqMapper`、`ApiCache`）、`Lyrics`（歌词数据模型 + LRC 解析 + 行查找）、`Prefs`（凭据入口（实际存组件凭据文件）/guid/QIMEI/音质/播放模式/外观）、`HomeCache`、`Downloader`/`DownloadStore`、`LikedStore`、`SearchHistoryStore`、`Models`、`NicknameCache`。
  - `player/PlayerHost.kt` — ExoPlayer 单例（操作必须投递到主线程）；持有 `current`/`isPlaying`/`liked`/`lyrics` 等 StateFlow；音频会话 id 写入 `Prefs.sessionIdForFx` 供各音效挂载。**注意**：取链接失败（限流）后播放器停在 IDLE，`toggle()` 必须对「IDLE 或 ENDED 且 current 仍在」走重新解析（playCurrent）——IDLE 下 `play()` 无效（按钮点不动），ENDED 下 `play()` 会把刚放完的歌重放一遍（自动切换变成重播的观感）（2026-09-27/28 实测）。自动切换失败不能静默：`PlayerHost.onError` 由 AppRoot 注入 toast。`playNext`（下一首播放）用 `pendingNext` 待播队列实现（随机模式下也保证先播），不能只改 index——ENDED 时 advance 会从那个下标再前进一格把「下一首」整个跳过。随机「上一首」按 `playedHistory` 回跳，历史为空按顺序回退（不能随机）。变速/变调用 media3 `PlaybackParameters`（0.5..2.0，变速不变调可分开）。
  - `player/EqualizerHost.kt` — 平台 `Equalizer`/`BassBoost` + **自己的预设存储**（`Prefs.eqStore` JSON：首次挂载把设备内置预设增益快照进来，此后运行时不碰 `usePreset`；所有预设可改——选中后拖频段直接写回该预设；可新增（用户命名）/删除非内置预设）；`player/DynamicsFxHost.kt` — `DynamicsProcessing`（DVC 动态范围压缩 + 声道平衡，API 28+，SDK 真实 API 是 `Config.Builder(9参)`、`setInputGainbyChannel`，无顶层 Builder）；`player/VizProcessor.kt`（透传 AudioProcessor 取 PCM 分段电平，免 RECORD_AUDIO）+ `player/VizHost.kt`（优先系统 `Visualizer` FFT，不可用回退 PCM；Visualizer 需 manifest 声明 RECORD_AUDIO）；`player/SmartEq.kt` — 智能调音（`Track.genre`→预设名映射，码表实测校准：1=流行 2=古典 22/50=摇滚 23=民谣 27=爵士 28=金属 33/20=电子 34=说唱；未知码不动）。**映射存 `Prefs.eqGenreMap`，音效页可改**（每个曲风挑任意预设，含自建）。
  - `ui/` — `AppRoot`（无导航条，`Nav` 页面栈 + 播放页上滑覆盖层）、`Nav.kt`（页面目的地）、`home/`、`player/`、`search/`、`settings/`、`common/`。
- `.agents/skills/` — `neumorphism`（新拟物规范）、`qqmusic-web-api`（端点字典）、官方 Android skills。做对应工作前先读。

**没有边车，也没有 Python**（`qqmusic-service/`、Chaquopy 插件、`QqPythonBridge.kt`、`src/main/python/`、`python-wheels/` 均已删除）。Chaquopy 接入 qqmusic-api-python 未采用：`chaquo.com/pypi-2.1/` 缺 pydantic(-core)/orjson/niquests/jsonpath-ng/anyio/paho-mqtt（全 404），这些含 Rust/C 扩展的包需要 Docker 交叉编译，本机无 Docker。**且没有必要**——原本以为必须靠 Python 的「喜欢写入」与「歌词」都已实测跑通（现由 HelperNext 组件执行，见顶部架构段）。

## 界面结构（无导航条）

主页不再是「底栏三 Tab」，而是单页三栏 + 二级页：

1. **主页**：右上角只有两个图标按钮（搜索、设置，**不带文字**）；左侧问候语（未登录 `请登录`，已登录随机带昵称）；下面三栏 —— 收藏的歌单、收藏的专辑、电台，每栏标题最右侧有「更多」进入对应二级页。
1.5. **主页顶部推荐歌曲**（2026-09-30 规格，无标题；09-30 晚二次改版）：**`RecommendStore` 预缓冲 5 首**（曲目+介绍，落盘持久化）——打开应用推荐即显、点刷新即时换歌，缓冲不足后台补货（**取歌/补货必须挂页面级 scope——挂 `LaunchedEffect(radioGroups)` 会被分组刷新取消，实测 LeftCompositionCancellationException**）。布局：**左=画框凸起封面（132dp，点封面即播放/暂停）**；右=歌名凸起块（跑马灯）+ 介绍框（凸起+边框修饰、中间凹陷、固定 150dp，**文字裁剪界线在边框处**）+ 竖排三凸起钮（播放/暂停、喜欢、刷新）。无详情显示「该歌曲暂无歌曲详情」。歌曲介绍接口 `music.pf_song_detail_svr/get_song_detail_yqq`（`SongApi.intro`，文案在 `data.info.intro.content[*].value`）。**点播放 = 接入猜你喜欢电台**（推荐歌为首曲 + `nextTracks` 一批作队列），不能把单曲丢进一首歌的队列（播完循环，用户实测指出）。
1.6. **关注的歌手**（收藏的专辑下面）：`music.concern.RelationList/GetFollowSingerList`，param `{HostUin: euin, From, Size}`（需登录 comm），响应 `data.List[*]`（MID/Name/AvatarUrl/Desc）。卡片=搜索页歌手卡同款缩小版（156dp 宽，画框规格与歌手页头像完全一致），点击带**飞位转场**进歌手页（坐标随卡片上报）。
2. **歌单/专辑封面**：封面坐在新拟物凸起底盘上；「我喜欢」（`tid=201` 或名为「我喜欢」）用**无背景爱心图标**替代封面；无封面/未加载到封面用**首字母无背景占位**。
3. **电台**：**没有独立页面** —— 主页里每个电台分组就是一栏（标题右侧**不带**「更多」），点卡片直接进该电台的曲目列表。早前那个「标签栏按分组分页、与列表融合成一块」的电台页（`RadioScreen` + `StationRow`）**已删除**；`ShadeFusedTabs` 这个视觉基元保留，现在只由搜索页的四个结果标签使用。
4. **播放栏与播放页**（播放栏 2026-09-30 二次改版）：
   - **播放栏 = 覆盖底部的底栏**（`common/PlayerBar.kt`）：凸起面板 `shadeSurfaceTop`（顶角圆、底边贴屏幕下缘、导航栏区域由面板延伸盖住），面板顶与页面内容交界处放同款背景色渐隐条 `BottomBarFade`。无音乐时**下沉消失**、有音乐时**上升出现**（记住 lastTrack 才能沉下去）；选择模式激活（`SelectionBus`）时**沉降让位**。
   - **布局**：左封面**跨两行、占位更大**（84dp 凸起画框；设置开「播放条音频可视化」则封面/画框都变圆形+四周围径向电平柱）；右侧**第一行只放歌名**（15sp 凸起块，`basicMarquee` 跑马灯），**第二行从左到右 = 歌手（可点进歌手页，跑马灯）/ 暂停 / 喜欢 / 播放列表**（喜欢只用爱心实心/空心表达状态）；**上一首/下一首按钮已取消**，改为**整条面板左右滑手势**（左滑=下一首、右滑=上一首，阈值 80dp，按钮 tap 不受影响），触发后面板内容向滑动方向滑出、新曲目从对侧滑入（`Animatable swap`，切歌动作立即发出）。
   - **多歌手**：`Track.singer` 按 `/`、`、` 切分；多个时先弹 `ShadeDialog` 问访问哪个，单个直接 `SingerApi.resolve(name)`（搜索接口精确名匹配）→ `open(Nav.Singer(mid,name,pic,songNum,albumNum))`。
   - **打开播放页/歌手页 = 整条底栏一路升到屏幕顶外、「带出」挂在它下方的覆盖层**：页面顶边始终贴着底栏底边（页面 `translationY=(1-t)*sceneH`，底栏 `translationY=-max(播放t,歌手t)*sceneH`，同一条时间轴锁步，底栏 zIndex 3.5、覆盖层 4）；返回精确倒放。飞行中页面不做透明度渐变（实心页面被拖上来）。
   - 播放队列弹窗 `QueueDialog` 在 common，播放栏与播放页共用；点播放栏「播放列表」直接弹它。
   - **播放页三模式**（2026-09-30 规格，替代左右 pager）：**纯封面(0) / 封面+歌词(1) / 纯歌词(2)**，上下滑切换、一次手势最多走一级（0↔2 不直连）。连续进度 `modeT`（Animatable）：封面随它上移/缩小/1→2 段淡出；歌词面板在 layout lambda 里按 modeT 定高（0→1 升起到 180dp 三行迷你（userScrollEnabled=false，拖动让给模式切换），1→2 长到全屏），`fullLyrics = derivedStateOf{ modeT>=1.5 }` 门控重组。**纯歌词→封面+歌词**靠 LyricsView 的 `onPullDownCollapse`：列表到顶后继续下拉的剩余量经 nestedScroll 连接回传（该模式下 `LocalOverscrollConfiguration=null`，否则 stretch 把剩余量吃光）。进度条直线形、控制区两模式共用不变。
   - **黑胶唱片机模式**（`Prefs.vinylMode`）：`VinylTurntable.kt` 已从 `~/Documents/VinylLab` 移植；唱片水平居中，底座位于盘右缘上方，双层静态纹路，只有片心封面旋转。播放页喂真实播放状态/曲目 id/每帧进度，视觉状态机不能接管音频切歌。唱头半径随进度从 0.48D 走到 0.32D；不做片尾提前抬臂（慢速下会重复起播旧片），直接跟随真实结束/切歌状态。快速切歌的 3 秒窗口仅作用于视觉；换片中到达的新请求必须保留，暂停时也能换封面。曲目身份按 mid 判断，不能只看封面 URL（同专辑和无封面歌曲仍要换片）。
   - **M3 Expressive 例外（2026-10-04 用户明确）**：唱臂、唱头、底座各为单一完整角色色色块，不做内嵌面板、金属高光条、装饰针尖或同心色块。唱臂宽 0.034D，唱头绕唱臂连接点向内折 20°，外侧有与唱头同色的小型 Finger Lift。底盘保留新拟物。`VinylLedBar.kt` 只用 M3 Expressive 圆头胶囊，无新拟物槽、霓虹发光或双色高光；必须与普通可视化一样按 `usingFft` 选择 FFT / `PlayerHost.vizProcessor.levels` PCM 回退，动态 State 只在绘制阶段读取。
   - 封面圆盘外圈的**频谱环与播放栏封面的可视化同频段数**（`VIZ_BARS=40` 根，从 `VizHost.BARS=16` 段电平降采样映射）。播放页左上是收起按钮、右上是均衡器，都凸起；播放模式是一个大凸起胶囊、内部不再放凸起；胶囊右侧喜欢按钮：未喜欢=凸起+空心爱心，已喜欢=凹陷+实心爱心。
4.5. **歌手页**（`ui/singer/SingerScreen.kt`，全屏覆盖层 `Nav.Singer`，升起动画与播放页同款）：最上**居中方形头像 + 凸起画框**；下面是**平的**两行居中文字（2026-09-30 改版，不再凹陷标签）：第一行歌手名、第二行小字「N 首歌 · N 张专辑」（计数为 0 的来源——如关注列表——进来后按名字自动解析补齐）；再下两个**平的**分段控制器（选中项=凹陷圆角矩形，中间「|」分隔）：左 = 歌曲|专辑、右 = 最新|热门。内容 = 歌曲列表或专辑卡片网格，**同一个 LazyColumn**（专辑按列数分块成行，`common/Cards.kt` 的 `cardGridItems`），切换种类/排序时滚动位置统一（2026-09-30 用户要求）。页头随内容滚走（与列表页一致）；边缘渐隐：顶部盖状态栏（`TopEdgeFade`，状态栏高度+48dp）、底部与主页同款。
   - **歌手页取数坑（2026-09-30 实测）**：按排序键缓存数据时，**取数必须由 LaunchedEffect 驱动**（切换键会取消上一条取数协程 + finally 复位 fetching）。早前用共享 loading 互斥 + scope.launch 手动取数：切换时新键的加载被在途请求挡掉（"切过去是空的，再切一次才恢复"）、加载被取消后 loading 卡死（底部永远不出"加载中"）。列表字段用 mutableStateOf 装（普通 HashMap 写入不触发重组）。
4.6. **通用卡片**（`common/Cards.kt`，歌手页与搜索结果共用）：`MediaCard`（凸起画框封面**画框细**=封面内缩 8dp；第一行=标题+右侧「N 首」同字号；第二行=歌手名**最多两行自动换行**）/ `SingerCard`（更大：方形画框头像+名字+歌曲·专辑数）。**卡片点击必须用 `cardTap`（pointerInput）**——`flatPressable` 的 clip 会把卡片内画框的左右阴影直接截断（用户实测指出）。
4.7. **搜索页**（2026-09-30 改版）：**所有元素都随内容上滑**——返回/标题/下载钮、搜索框、标签栏全部是列表的表头 item（CollapsingTopBar/rememberTopBarVisible 由此退役），内容从状态栏底下滚过；顶部渐隐盖状态栏（TopEdgeFade）、底部同主页。标签栏=**平的分段控制**（选中=凹陷圆角矩形，同歌手页分段控制器；ShadeFusedTabs 退役，基元保留）。结果与页头同在一个 LazyColumn（`searchResultItems` 扩展）：歌手/专辑/歌单都是卡片网格（歌手卡 minCard 300dp、卡宽上限 300dp 居中、名字两行换行；专辑/歌单卡 minCard 170dp）。
4.8. **搜索页歌手卡 → 歌手页 = 「飞位」共享元素转场**（2026-09-30 规格，与播放栏进入的「整页升起」并存，按 `Nav.Singer` 是否带 `origin` 区分）：
   - **进入（两段式，2026-09-30 用户规格：页面必须在动画完全落位后才出现，绝不重叠）**：① 页面其余元素随背景色遮罩淡出（前半程加浓，被点卡片由克隆卡原样盖住保持不动）；② 克隆卡从卡片原位**缩放+平移**飞向歌手页头像落点（`tween(440, FastOutSlowInEasing)` 缓动曲线，卡片文字飞行途中按 `1-2.4t` 淡出）；③ **飞行结束之后**歌手页整层纯淡入 200ms（`singerPageT` 第二个 Animatable，无位移——上浮会让落点测量变成瞬态位、落位不重合）。克隆卡陪到页面完全显形才隐去。克隆卡按**卡片原尺寸**布局、`TransformOrigin(0,0)` 逐帧 `translation+scale`（在 graphicsLayer lambda 里读进度，零重组）。
   - **落点**：歌手页头像画框经 `onGloballyPositioned → boundsInRoot` 上报（`SingerScreen(onAvatarBounds)`）；**头像规格必须与 SingerCard 一致**（内缩 10dp、圆角 14），否则落位交接会跳。
   - **落位精度（2026-09-30 实测补丁）**：页面淡入**不能带上浮位移**——上浮期间头像的 boundsInRoot 是瞬态偏移位置，克隆卡按它落位、页面归位后即出现"最后一段不重合"；飞位路径为纯淡入。透明触摸拦截层的显隐用 `singerLanding = t < 0.999` 门控——用 `singerFlying`（t>0.001）会让拦截层在落位后永久存在、全屏吃点击（"应用卡死"的实测根因）。`back()` 在回飞窗口期（singerBackPending）必须防重入，否则双弹栈。
   - **返回**：页面先淡出、克隆卡飞回卡片原位（480ms），动画走完才弹栈（`singerBackPending` + delay(720)，同均衡器返回套路）；`open/back` 均有防重入。
   - **压专辑详情**：整层淡出（`singerCoverAlpha`）而非回飞；返回时淡入复原。播放栏在飞位路径**不上升**，随转场淡出即可（图层 lambda 按 cardFlight 分支）。
   - 飞行中放**透明触摸拦截层**（页面 alpha=0 仍会吃事件）。
5. **设置页**（顶栏随页面滚动，2026-10-01 审计统一）：账号（网页登录/退出）、播放音质（6 档）、下载（目录/音质）、外观（深色模式随系统或强制、强调色：经典/莫奈壁纸取色、立体感强度、歌词字号、歌词翻译开关）、播放栏/播放页（可视化、黑胶）、**诊断日志**（开关；清空=圆形凸起垃圾桶钮；导出=SAF CreateDocument；存储上限=凹陷圆角数字框，单位 MB，超限自动裁掉前一半）。
5.5. **音效页**（顶栏随页面滚动，2026-10-01 审计统一）：开关 → 预设（chips，选中=凹陷；长按自建预设删除；「＋ 新预设」以当前滑杆值入库、用户命名）→ 五段频滑杆（拖动即写回选中预设）→ 低音增强 → DVC/声道平衡 → 速度与音调 → 智能调音（曲风 chips `流行 · Pop`，点选任意预设改映射）。
6.5. **一二级页面切换 = 场景推拉 + 渐进模糊 → 页面淡入上浮**（2026-09-29 最终规格，用户否掉过四版：Hero 卡片形变、纯 crossfade、两段接力、二级页矩形生长/复刻层——**封面/卡片不做任何仿射变形**，不等比 rect 插值必然压扁封面）：
   - **进入**：只有一级场景的根容器做 uniform scale + translation（相机中心从屏幕中心滑向卡片中心，卡片只是焦点，`S = max(W/cardW, H/cardH)`），同时 `RenderEffect.createBlurEffect` 模糊随进度增强（0→26dp，图层 lambda 里逐帧设 renderEffect），推满时完全模糊 → 停 80ms → 二级页面整屏淡入 + 上浮 12dp（LinearOutSlowIn 220ms）→ 落定。
   - **返回**：二级页面淡出下沉 200ms → 场景回缩 + 模糊渐消 400ms → 弹栈。
   - **性能红线：推拉进度只能在 graphicsLayer 的 lambda 里读**（组合期读 = AppRoot 每帧整树重组卡死）；落定与否用 `StackEntry.settled` 判断；二级页内容 t>0.05 就组合；转场期阴影淡出（`LocalShadeShadowAlpha`）；主页稳定 2.5s 后幕后预热一遍列表页。
   - **层级**：推拉层 zIndex(3)；迷你播放栏 zIndex(3.5)；播放页覆盖层 zIndex(4)。
   - **覆盖层**：background 盖到状态栏区域 + statusBarsPadding 在容器内；单一实例贯穿飞行与落定（按 steady 切子树会取消加载协程→列表空白）。
   - **顶部渐隐（`TopEdgeFade`，搜索/歌手页）**：**「彻底消失线」在状态栏上缘**——状态栏以上完全不透明、状态栏偏下仍可隐约看到渐隐中的内容、往下 48dp 渐到全透明（2026-09-30 用户规格）；由 `canScrollBackward` 门控显隐。
6.6. **选择下载模式**（2026-09-29 规格，曲目列表页与搜索结果页共用）：顶栏右侧下载钮进入；进入后该钮 Crossfade 变 **✕（退出选择）**（搜索页在选择态下任何标签都显示 ✕，防止切走标签后无法退出）；同时**播放栏经 `SelectionBus` 沉降让位**，带 全选/反选/下载 三个无文字图标钮的凸起底栏从底部升起（`SelectionBar` 在 common，样式与播放栏同款：BottomBarFade + shadeSurfaceTop + navigationBarsPadding，AnimatedVisibility 进出场）。页面用 `rememberSelectionBusSync(selecting)` 接线进出；下载中状态在底栏中间显示「已选 N / 下载中 i/n」。
6. **返回 = 弹出页面栈（从哪来就回哪去）**：6. **返回 = 弹出页面栈（从哪来就回哪去）**：`ui/Nav.kt` 的 `Nav` + `AppRoot` 里的 `mutableStateListOf<Nav>`；界面上的返回按钮与系统返回键**都只做「弹出栈顶」**，栈只剩主页时交还系统（退出 App）。**同一个页面可能有多个来源**（专辑详情可从主页卡片 / 歌单详情里的「查看专辑」/ 搜索结果进入），压栈后各自返回各自来源 —— 所以新页面**必须**走 `open(Nav.X)`，不要再写「一律回主页」。
   - `Nav.Player` 是特殊栈顶：它是全屏覆盖层（zIndex 4），`page` 取「栈里最后一个非 Player」的那层。均衡器 `Nav.Equalizer` 压在 `Nav.Player` 之上：**打开时播放页下滑露出音效页；返回时播放页重新从底部升起盖住音效页，动画结束后才真正弹栈**（`playerRising`+延时 360ms；立即弹栈会让底下的页面闪现再重放升起动画——实测踩过）。

## 动态光源（2026-10-01 已回退，未采纳）

「动态日光/月光光源 + 彩色衰减长投影」方案实现过一版后**按用户决定整体回退**，代码已删除（LightMath/SunLightSource/测试/设置项），Shade.kt 恢复静态左上光源。回退时踩的坑仍值得记录：Shade.kt 不能整文件 git checkout——未提交批次（shadeSurfaceTop 等）会一起丢，只能精准回退；OKLab 实现的 cbrt 不能用 sqrt 冒充；「多层模糊矩形外推」式长投影会有光斑且帧时间爆炸。若未来重提，参考记忆文件里的教训再设计。

## 随时间变化的光影（2026-10-01 采纳，`shade/DayLight.kt`）

在独立实验工程 ShadeLab（`~/Documents/ShadeLab`）里逐轮验证后移植进主工程。核心：**光照随「时刻」变化**，全部修饰符共用一套。

- **模型**：`Lighting(sx, sy, blurDark, blurLight, alphaDark, alphaLight, warmth, k)`。全部是**倍数**——修饰符自己传的 `offset`/`blur` 是它的标称尺寸，光照只做按轴缩放，所以小按钮与大卡片之间的比例关系不变（这是移植时对本工程唯一的结构性改动；实验里是直接给绝对 dp）。倍率基准取本工程最常用的 6dp / 10dp：默认参数下「offset=6dp、blur=10dp 的组件」观感与实验一致。
- **两套完整 0–24h 曲线**：白天（`DAY_KEYS`，真实色温 2000→5500→2000K，有效变化区间 6:00–18:00、**区间外沿用端点值**）与黑夜（`NIGHT_ATTR` 恒定：8000K、光弱而弥散、几乎没有明确高光点）。
- **选哪一套由深色模式决定，不由时刻决定**（用户规格）：浅色主题→白天那套（`ShadeColors.isDark=false`）、深色→黑夜那套、「随系统」→跟着系统深浅色。所以深色模式选浅色时变化一直是白天的，选深色时一直是黑夜的。
- **偏移（`OFFSET_KEYS`）日夜共用一条连续扫描**：6:00 最左 → 12:00 归零 → 18:00 最右 → 24:00 归零 → 回到 6:00。因为共用，昼夜交界处偏移天然接上，不需要额外对齐。夜间的差异只体现在色温/强度/模糊上。
- **时刻**：`LightingMode.TIME` 跟真实时钟（`runClock()` 每 30 秒对一次，`MainActivity` 里 LaunchedEffect 启动）；`FIXED` 停在设置里选的时刻（0.5h 对齐）。默认 `FIXED` + 12:00（正午：纯顶光、方向不偏左右，是最中性的默认）。
- **色温**：`warmthOfK` 分段线性，三锚点可在设置里改（2000K 白天最暖 / 5500K 白天最冷 / 8000K 黑夜），改任一个整条曲线重塑。**只给两影着色（`tempTint`：暖=R↑B↓、冷=B↑R↓），底色不碰**——立体感依赖底色与两影的明度关系。另有「最大偏移」滑杆（基准 10dp）整体缩放扫描幅度。
- **性能**：`DayLightHost` 的字段都是快照状态，只在 `drawBehind` lambda 里读 `current(colors.isDark)` → 设置一改就重绘、不引起重组（与 `LocalShadeShadowAlpha` 同一套路）。设置页读数用 `preview()`（组合期读）。
- 设置页新增「光影」区：**平的分段控制器**（`ui/common/Segmented.kt`，选中=凹陷圆角矩形，与歌手页同款）+ 时刻滑杆（凹陷轨道 + accent 填充）+ 折叠的标定滑杆。顺带把原「下载」区里混着的播放栏/播放页项拆成了独立区。
- **踩坑**：`ui/common/ShadeSlider.kt` 原来用无方向的 `detectDragGestures`，在设置页这种滚动容器里会把**竖向滚动吃掉**（表现为"想滚页面却把滑杆拖了"，实测把固定时刻从 12:00 拖成 17:00）——已改为 `detectHorizontalDragGestures`。

## 开源协议与第三方代码（2026-10-02 起：本项目 GPL-3.0）

**本项目以 GPL-3.0 开源**（用户 2026-10-02 定，取代此前的「License TBD / All Rights Reserved」；`README.md` 的协议段落已同步为 GPL-3.0 并写明 HelperNext 组件架构）。引第三方代码的红线据此重划：

- **GPL-3 兼容许可下的代码可以参阅、乃至照搬**：GPL-3.0 / LGPL-3.0 / MIT / Apache-2.0，以及 AGPL-3.0（AGPLv3 §13 明确允许与 GPLv3 组合成单一作品，但 AGPL 部分继续带 AGPL 自身义务——组合时保留声明并在文件头记清）。照搬时**必须**：保留原版权声明与许可文本、注明来源（仓库 + 文件 + commit/日期）、在文件头写清改动。
  - 于是旧红线的「几乎所有开源安卓播放器（Retro Music、Auxio、Vinyl、OuterTune、Music-Player-GO、RootlessJamesDSP）一律不搬」**已失效**——这些仓库（多为 GPL-3.0）可以从里面挑成熟实现移植；Retro/Auxio 以 Kotlin 为主，移植成本仍要单独评估。
- **不与 GPL-3 兼容的许可仍然不搬**（专有/All-Rights-Reserved、CC-NC 等）。拿不准先查 `api.github.com/repos/<owner>/<repo>` 的 `license.spdx_id`。
- [Moriafly/SaltPlayerSource](https://github.com/Moriafly/SaltPlayerSource)（椒盐音乐）虽是 MIT，但**仓库里没有应用源码**（只有 issue 模板/翻译/发布物，App 本体闭源），无功能性代码可拿；其均衡器等音效功能只能当产品形态参考。
- [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)（AMLL）—— AGPL-3.0-only（`packages/{lyric,core,ttml,react,vue}` 实测都是）且是 JS/DOM/WebGL 技术栈：协议上现在可组合（见上），但技术栈不通，仍以**自研 Kotlin 版**为主——QRC 逐字扫色、模糊/透明度梯度、`alignPosition` 35% 锚点、拖动浏览都是照它的观感自己写的。可继续借鉴的点（纯算法，自研）：音译/注音行、歌词优化策略（空格规范化、清理 <500ms 的非刻意重叠、让歌词最多提前 600ms 开始）、逐行弹簧动画与按距离的 scale 梯度、间奏点、左对齐/居中切换。其歌词格式支持面（LRC/QRC/TTML/YRC/LYS/LQE/ASS）只在「本地导入歌词文件」时才需要。
- 均衡器/音效优先用 **Android 平台 audiofx**（`Equalizer`/`BassBoost`/`DynamicsProcessing`，AOSP = Apache-2.0）+ **media3 `AudioProcessor` 链**（可自研 biquad EQ / 限幅 / crossfeed 等，见 `player/EqualizerHost.kt`、`player/DynamicsFxHost.kt`）。
- 强调色用 **Material You 莫奈取色**（`dynamicLight/DarkColorScheme`，API 31+）只替换 `ShadeColors.accent`，**底色与阴影保持中性**——新拟物立体感依赖底色与亮/暗影的明度关系，背景被壁纸色带偏就得整套重调阴影。

## 新拟物 UI 原则（用户明确要求，**不可违反**）

1. **不在凸起上再做凸起；必要时可以（2026-09-29 用户放开）**。例外是**容器级凸起**：播放栏、选择模式底栏这类整块凸起面板，其上的按钮、画框、信息块**可以再凸起**——它们是面板上的独立控件（`PlayerBar`/`SelectionBar` 即此形态）。普通内容面板（列表块、卡片）内部仍保持「平」。
2. **不在凹陷中再做凹陷**。
3. **在凸起中，以「凹陷」表示选中，以「平」（无阴影）表示未选中**。

对照实现：播放模式胶囊是凸起、内部只放图标与文字（无嵌套凸起）；进度条是凹陷轨道、轨内只放 accent 填充色块（无嵌套凹陷）；音质选项是凸起行、选中时整行变凹陷；播放栏面板（凸起）上的封面画框/标题块/按钮再凸起（容器级凸起的放开例外）。写新控件前先问「母体是凸起还是凹陷」——内容面板的凸起母体里只能出现凹陷或平面；底栏这类容器级凸起例外如上。

## 构建与安装

```bash
export JAVA_HOME=~/tools/jdk-21.0.2.jdk/Contents/Home   # 本机唯一可用 JDK（Gradle 8.13 跑不了 Java 25 的 JBR）
./gradlew :app:assembleDebug
adb connect 127.0.0.1:5555          # 设备会掉线，需重连
adb -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

`compileSdk = 36`（本机只有 android-37.0 扩展平台，AGP 8.12 找的是 android-36/自动下载）。依赖走阿里云镜像（`settings.gradle.kts`）。Gradle wrapper 指向腾讯镜像。

## 数据架构（全部实测验证，勿凭记忆改）

**QQ 接口请求由 HelperNext Rust 组件内嵌执行**（宿主经 BoltFFI/JNI 调用，无外部服务；组件按用途使用三套 comm，语义实测如下）：

| comm | 用途 | 关键点 |
|---|---|---|
| `{ct:19,cv:1873}` 匿名网页 | 搜索 | 综合模式解析 `body.item_song`；`body.song.list` 是另一模式 |
| `{ct:24,cv:0}` 电台 | 电台列表 | `pf.radiosvr/GetRadiolist`，分组在 `radio_list[].list[]`（**分组 id 不是电台 id**） |
| `{ct:11,cv:14090008,...}` 登录档 | 其余全部 | `qq`=uin、`authst`=musickey、`tmeLoginType:2`，QIMEI 可自报随机值（无需真实置备） |

登录档端点（全部需 `euin`=encrypt_uin，缺它则 `CgiGetDiss` 静默失败）：

- 我喜欢：`music.srfDissInfo.DissInfo/CgiGetDiss`，`dirid=201`、`disstid=0`、`enc_host_uin=euin`；歌曲在 `songlist[]`，总数在 `dirinfo.songnum`。
- 歌单内歌曲：同端点，`disstid=` 歌单 tid、`dirid=0`。
- 电台歌曲：`mb_track_radio_svr/get_radio_track`（`id`=真实电台 id，`firstplay:1`；网页裸调会被风控，必须带登录 comm）。
- 收藏歌单：`music.musicasset.PlaylistFavRead/CgiGetPlaylistFavInfo`，`uin=euin`，结果在 **`v_list[]`**（字段 `tid/name/logo/songnum`）。
- 收藏专辑：`music.musicasset.AlbumFavRead/CgiGetAlbumFavInfo`，`euin=euin`，同样 `v_list[]`（`mid/name/logo/songnum`）。
- 专辑内歌曲：`music.musichallAlbum.AlbumSongList/GetAlbumSongList`，参数是**驼峰** `albumMid`/`begin`/`num`，响应 `songList[].songInfo`。注意部分专辑（如播客类）`totalNum=0` 属正常。
- 歌手页（2026-09-30 curl 实测，`data/api/SingerApi.kt`）：歌手解析/头像/歌数/专辑数复用**搜索接口**（`body.singer[]` 的 `singerMID/singerName/singerPic/songNum/albumNum`）；歌曲列表 `musichall.song_list_server/GetSingerSongList`（param `{singerMid, order, number, begin}`，**order=1 热门、order=2 最新**，总数 `totalNum`，歌在 `songList[].songInfo`）；专辑列表 `music.musichallAlbum.AlbumListServer/GetAlbumList`（同 param，总数 `total`，字段 `albumMid/albumName/pmid/publishDate/singerName`）——**其 `totalNum`（专辑内歌曲数）恒为 0**，要歌数就把 `GetAlbumSongList` 按 30 张一批合并进一个 musicu.fcg 请求（req_1..req_N 多请求块，一次 HTTP 查各自的 totalNum）。
- 播放链接：`music.vkey.GetVkey/UrlGetVkey`，`filename=["{前缀}{media_mid}{扩展名}"]`，purl 为相对路径拼 `https://isure.stream.qqmusic.qq.com/`。**登录后 VIP 曲目也能取到 purl**。
  - ⚠️ **`result=104003` 是「档位级」的**：免费歌的无损/高品质档同样返回 104003（该档位要会员权益），只有标准档（M500）失败才是真没权限。**降级链里见到 104003 绝不能中断**（2026-09-25 的 bug：播放音质设为无损时所有歌都误报"没有会员"）。另外先用 `Track.fileSizes` 过滤掉这首歌不存在的档位再请求。**重新登录可修复 104003**（2026-09-27 实测：VIP6 账号票据失效导致 VIP/无损全挂 104003，网页重新登录后全部恢复）——旧票据读接口仍正常，别误判成 cookie 失效。
- 加/取消喜欢（写操作）：`music.musicasset.PlaylistDetailWrite` + `AddSonglist`/`DelSonglist`，参数 `{dirId:201, tid:0, bFmtUtf8:true, v_songInfo:[{songId:<数字 id>, songType:0}]}` —— 必须用**数字 `songId`**（`songId<=0` 时无法操作），不是 mid；`req_1.code==0` 且 `data.retCode==0` 为成功。**已在 App 内实测双向成功**（我喜欢总数 471→472→471）。
  - ⚠️ 有公开文档（simple-music）记录此接口返回业务码 `80105` 并被判为「不可用」，那是**网页 comm（`ct:24`/`cv:4747474`）**下的结果。本项目用的 **Android 登录 comm（`ct:11`/`cv:14090008`）没问题** —— 换 comm 是这类写操作成败的关键。
  - ⚠️⚠️ **写接口有风控限流：业务码 `1000`**。短时间内多次调用 Add/DelSonglist 后，接口会**持续**返回业务码 `1000`（`retCode` 缺失），此时**读接口完全正常**，等 3 分钟以上仍未恢复（实测）。这不是参数错、不是没权限、不是登录失效，**继续重试只会延长限流**。
    - 对策：写操作失败时**不要自动重试**；把 `1000` 如实告诉用户（App 已把 `LikeResult.Rejected(1000)` 显示为「操作太频繁，已被限流，请稍后再试」）。调试时尤其要克制 —— 反复验证写操作把一个账号打到限流状态是很容易的事。
    - 另注：播客/白噪音类曲目同样可能返回 `1000`，与被限流的表现一致，无法单独区分。
  - 另注意：`PlaylistFavWrite/CgiAddSonglist` + `songMid` 会返回 40000，不要回退到那条路径。
- 昵称：`music.UserInfo.userInfoServer/GetLoginUserInfo` → `data.info.nick`，只在内存缓存（`NicknameCache`），不持久化。
- **歌词**：端点用 `musicu.fcg` 的 `music.musichallSong.PlayLyricInfo/GetPlayLyricInfo`（匿名 comm 即可），param 必须**显式带布尔开关**：`crypt:0`、`qrc:1`、`trans:1`（翻译）、`roma:1`（音译）、`type:1`。详见下面「歌词（LRC / QRC 逐字）」。
  - 另有 `https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=<mid>&format=json&nobase64=1` 可取**明文 LRC**（必须带 `Referer: https://y.qq.com/portal/player.html`；`format=json` 可能带 jsonp 包装，解析前用 `substringAfter('{')` 兜底）。App 手里已有 `GetPlayLyricInfo`，这条只当兜底知识。

### 歌词（LRC / QRC 逐字）

- **`qrc:1` 时 `lyric` 字段是 hex 密文，不是 base64**（`crypt:0` 也不影响这一条）。解密由 HelperNext 组件执行：hex → 非标准类 DES 三重 D(K3)→E(K2)→D(K1)（24 字节连续密钥 `!@#)(*$%123ZXC!@!@#)(NHL`，S/P/E 盒是 QQ 私有的）→ zlib（带 BOM）→ 带 XML 壳的 QRC 明文。算法与 [qrc-decoder](https://github.com/apoint123/qrc-decoder)（MIT）同源，已用 AMLL 官方测试向量核过。
- ⚠️ **QRC 里的字时间是「绝对毫秒」，不是相对行首**：第 2 行是 `[5670,5670]Lyrics(5670,378) (6048,378)by(6426,378)…` —— 首字时间就等于行起点。早期解析器按 `lineStart + 字偏移` 换算，于是**除第一行（lineStart=0）外**每行的字时间都被推后一整个行起点，扫色计算判定成「还没唱」而恒为 0 —— 这正是用户报的「只有第一句歌词有扫色」。现在按「字时间最大值是否超过行时长」判别基准（相对时间的行不可能超出自身时长），再决定要不要加 `lineStart`。
- **翻译**：`trans` 是 base64 明文 LRC，无翻译的行是 `//` 占位，与原文同时间轴。日文曲的 `trans` 还带 **`[kana:1よね1づ1けん1し…]`** 注音元数据（数字=主歌词里对应词的字符数，后面跟读音）—— **尚未使用**，是「注音/ruby」这类能力的现成数据源。
- **音译（罗马音）**：`roma:1` 时 `roma` 字段同样是 **hex QRC 密文**，用同一套 QRC 解密（HelperNext 组件执行）就能解出**逐字罗马音**（实测 `[1547,1151]yu (1547,223)me (1771,152)na (1924,223)ra …`）。实测 Lemon / 残酷な天使のテーゼ 有数据，英文曲（Five Hundred Miles）为空 —— 想做「音译行」时先探测该字段是否为空。**尚未实现**。
- **音译（roma）与注音（kana）已实现**（2026-09-28）：`roma:1` 的 hex 密文用同一套解密解出逐字罗马音，按行起点对齐回主歌词行（±150ms），显示为主行下方的音译行；注音来自 trans 里的 `[kana:<n><读音>…]` 元数据——**token 流按行时间序贯穿整首歌、只有汉字消费 token**（假名/拉丁/标点保留原字，实测 Lemon 全曲对齐含 `词→し`、`曲→きょく`），逐字行渲染成字上注音、行级 LRC 渲染成读音行。歌词页右上角「译/音/注」三开关同行（`Prefs.showLyricTranslation/showLyricRoman/showLyricKana`）。
- **歌词优化策略**（AMLL 同款，自研）：空格规范化；清洗非刻意重叠（与下一行重叠 <500ms 且 ≤100ms 或 ≤下一行时长 10% → 截断上一行末字）；行起点提前（间隔 ≥600ms 提前 600，≥400 提前 400，否则提前间隔的 70%；只提前行起点，字时间不动）。注意 `LyricLine.endMs` 只在逐字行有意义。
- 渲染（`ui/player/LyricsView.kt`）硬经验：
  - **填充下标要算成播放位置的纯函数**，不要用 `LaunchedEffect(positionMs, …)` 写状态：`positionMs` 33ms 变一次键，每帧都在「取消旧协程、启动新协程」，被取消的 job 可能一次都没执行过，填充就恒为 0（第一句之后的扫色全失效的另一半原因）。
  - **逐字渲染改成了按字单元**（FlowRow 每字一个双层 Text、按该字已唱比例横向裁剪）：不需要 `getHorizontalPosition` 换算 x，长句折行天然正确（旧方案「整行以上整行画 + 当前行按 x 裁」已废弃）。填充层在尺寸未量出的第一帧**什么都别画**，否则闪成整字填充。
  - 浏览态（拖动开始 / 双击后 3 秒内）聚焦行 = **视口 35% 锚点行**（`layoutInfo` 里挑覆盖锚点 y 的那一项），之后回归正在播放行；双击任意行 = seek 到该行。

### 音质（`Prefs.Quality`）

`filename` 前缀与扩展名必须成对：`M500`=.mp3 标准128k、`M800`=.mp3 高品质320k、`C400`=.m4a 流畅96k、`O600`=.ogg 192k、`O800`=.ogg 320k、`F000`=.flac 无损。
`playUrl(track, quality)` 会按档位顺序**逐级降级**，取到就返回。

**关键实测结论**：多前缀请求时 `result=101404` 且 purl 为空是**短时限流**，不是权限不足（`result=22` 才是未登录）；隔几秒重试所有档位都能拿到 purl。网络层因此无需额外的重试封装，降级链已足够。

## 登录与凭据

凭据唯一持久来源是 `files/HelperNext/Credential/qqmusic-credential.json`（组件读写）；`Prefs.credential` 只是转发入口——读组件文件、保存时交给组件并删掉旧 SharedPreferences 副本（迁移在 `data/api/HelperNext.kt` 初始化时自动完成）。凭据内容仍是 `uin + musickey + euin`：

- **网页登录**（唯一入口，纯原生）：WebView 打开 y.qq.com → 读 cookie 的 `uin`（去 `o` 前缀）+ `qm_keyst`（+ 若有 `euin` 一并读）→ `Prefs.saveCredential` 交给组件导入。缺 `euin` 时收藏列表不可用，界面会提示重新登录。

## 交互规范（用户明确要求，务必遵守）

`shadePressable` 已实现三点，新控件直接复用它：

1. **凸→凹阴影渐变形变**：外阴影透明度 1→0 与内阴影 0→1 在 160ms 内交叉插值（`animateFloatAsState`），绝不允许瞬间翻转。
2. **已是凹陷时再按**：内阴影强度 ×1.45（`innerStrength`），强化「按进去」。
3. **背景色微调**：按下时背景向黑混入 6%（`animateColorAsState`），模拟受压。

## 环境事实（本机特有）

- **拉取依赖的原则（用户明确要求）：能用国内镜像源的优先用镜像源；镜像源拿不到时，才走代理兜底；GitHub 则一律直接走代理。**
- 直连外网不通：pip/git/GitHub 走代理 `http://127.0.0.1:12450`（**端口已从 7897 改为 12450**）；Maven/Gradle 走国内镜像（`settings.gradle.kts` 的阿里云 + wrapper 腾讯镜像）无需代理。Docker Desktop 已装并配好国内镜像源。
- **Python 直连 \*.qq.com 的 TLS 会被掐断**（证书/EOF 错误），`curl` 正常。写探针一律用 `curl`（可带 `-x http://127.0.0.1:12450`），别用 urllib。
- 模拟器 AVD 名 `neu`，`~/.android/avd/neu.avd/config.ini` 里 `target=android-36`（arm64-v8a google_apis）—— 早前写的「API 32」已过期，以 `adb shell getprop ro.build.version.sdk` 实测为准。启动：`~/Library/Android/sdk/emulator/emulator -avd neu`。**设备名会随启动顺序变**（`emulator-5554` / `emulator-5555`），别写死：先 `adb devices` 看实际名字再用 `-s`。`adb connect 127.0.0.1:5555` 常显示 offline，不代表可用；模拟器还会自己退出/卡死（`adb devices` 变 `offline`、`adb shell` 挂住就是它），掉了就 `kill -9` qemu 进程后重启 AVD。
- **禁止截图/读图（2026-09-28 用户恢复禁令）**：模型**一律不执行 `screencap`、不 Read 任何图片/视频**（此前短暂放开过，现撤销）。截图与视觉测试**由用户自己做**。
  - 模型该做的验证仍是**非视觉、可判定**的：`uiautomator dump` 读 `text`/`content-desc` 与 `bounds`、服务端接口回读、`shared_prefs` 回读、logcat 查崩溃。
  - 需要看效果（配色/阴影/动画/布局）时，**把要看什么交付给用户**，等用户反馈。
- 项目根 `.venv`（Python 3.14）装的是分析用 `qqmusic-api-python`（全异步），仅用于查阅其请求构造，App 不依赖它。
- 系统深色模式用 `adb shell cmd uimode night yes|no` 切换，用于验证 `ShadeTheme` 随系统。

## 工程纪律（用户明确要求）

- **大改动（新功能批次/方案替换/回退）开始前，必须先 `git add + commit` 当前状态**——把上一个已验收的稳定版本存进仓库，之后的一切回退/对比才干净。批内小步提交亦可，但批次边界必须有提交。
- 回退某项功能时不要整树 `git checkout`：先确认未提交区里有哪些其他批次的功能，精准回退目标文件的相关改动。

## 合规

仅供个人学习/自用播放；VIP 内容需用户自己的登录凭据；不批量抓取、不分发；控制请求频率。

## 2026-10-01 交互批次（播放栏 / 歌手画框 / 弹窗 / 设置页）

- **歌手头像画框必须用 `ui/common/SingerAvatarFrame`**（歌手页头像 / 搜索页歌手卡 / 主页关注歌手卡三处共用）。三者卡片宽度不同（216/300/156dp），**固定 dp 内缩会让飞位克隆卡缩放后与目标头像对不上**（用户报「动画有漏洞」）——该组件把内缩/圆角/画框 shade 全部按 216dp 基准**等比换算**，任何宽度下归一化规格都等于歌手页头像。新增会飞位交接的歌手卡一律用它，不要再各写一份。
- **播放栏 `singerSlide` 只能由「不带 origin 的歌手页」驱动**：带 origin（搜索页/主页歌手卡）走飞位路径、页面是原地淡入，底栏只做淡出；若让它也升到 1，弹栈那一帧底栏的 `ride` 会从 0 跳回 1（用户报的「返回主页时播放栏一瞬移动到底部再落下来」）。
- **播放栏/选择底栏面板不画阴影、不做圆角**（用户规格）：最外围那圈光影会与上方 `BottomBarFade` 糊在一起。现在是一块纯底色面板。
- **跑马灯挂在被 `fillMaxWidth` 收紧的链上不会滚**（播放栏歌手名实测）：`basicMarquee` 必须直接挂在 Text 上、由父级 Box 限制宽度。
- **弹窗 = 整页**（`ShadeDialog`）：纯色底（不压暗不模糊）+ 淡入浮现 + 顶栏收起钮 + 内容坐在凸起卡里（凸起卡上的光影才是全 App 那套）。早前「半透明压暗 + 居中浮层」时只有亮影看得见，观感像「两侧都是高光」。
- **设置页分 6 区**（`SETTING_TABS`：账号/外观/光影/播放/下载/其它），用 `if (tab == N)` 包住原分区、**不搬运代码**；短选项组（深色模式/强调色/立体感/歌词字号）改 `SegmentedControl`，开关改 `ShadeSwitch`（凹陷轨道+凸起滑块）。
- **高光偏移倍数**：`DayLightHost.lightRatio`（设置里可调，1=与暗影等长对称），只缩放高光那侧的位移。

## 2026-10-01 第二批（过渡克隆卡 / 底部留白 / 渐隐线 / 推荐卡 / 偏移曲线）

- **飞位克隆卡（`SingerFlightCard`）必须与歌手卡用同一个 `SingerAvatarFrame`**：它原来自己写死了一套固定 dp 画框，而源卡片已改成等比换算 → 过渡途中画框粗细可见地变了（用户实测「过渡时能看到画框不统一」）。凡是会与歌手页头像交接的渲染（卡片、克隆卡）都只能有一个来源。
- **底部留白统一走 `ui/common/BottomBarMetrics` + `rememberPlayerBarSpace()`**：播放栏（覆盖式底栏）的高度由 AppRoot 在 `onSizeChanged` 里写入，各滚动页面据此留白；此前各页硬编码 `150.dp`（设置页/音效页根本没有）。没有曲目时返回 0（底栏此时是沉下去的）。
- **`TopEdgeFade` 的「彻底消失线」在系统状态栏下缘再低一点点**（`belowStatusBar = 8.dp`）：状态栏那一条必须完全干净（沉浸），再往下才开始渐隐。
- **推荐卡（`RecommendCard`）**：整块大圆角凸起承载卡，四元素自上而下——封面画框（居中）→ 歌名+歌手（居中跑马灯）→ 详情凹陷槽（150dp、可滚）→ 三个动作钮（居中一行）+「来自猜你喜欢」。**喜欢/播放态必须 `collectAsState()`**：原来写 `LikedStore.liked.value` / `PlayerHost.current.value`，不订阅，按了喜欢按钮不变（用户实测）。
- **光影偏移改成两条独立的 min/max 曲线**：`Lighting(ux, uy, lenDark, lenLight, ...)`；相位取自同一条 24h 扫描（正午/午夜 = 0 最短、日出/日落 = 1 最长，用 `OFFSET_MAG_NEUTRAL/EXTREME` 归一化），两条阴影共用这条相位、只是取值区间不同。标定项：**暗色阴影最小/最大偏移**（默认 3.5 / 10 dp）、**高光阴影最小/最大偏移**（默认 3.5 / 5 dp）；组件自己的 `offset` 相对基准 `REF_OFFSET_DP = 6dp` 等比缩放。术语统一用「暗色阴影 / 高光阴影」。

## 2026-10-01 第三批（沉浸式边缘 / 行间光影接缝 / 推荐卡 2×2 / 弹窗关闭动画 / 后台播放前台服务）

- **页面层不能统一加 `statusBarsPadding`**：那会让所有页面内容进不到状态栏底下，每页的渐隐线整体低一整个状态栏（用户报「搜索页渐隐线明显比别的低」——歌手页是覆盖层不受影响，所以只有它是对的）。现在由各页自己把 `rememberTopContentInset()`（= 状态栏高 + `TOP_FADE_BELOW_STATUS_BAR` 8dp）加进 `contentPadding` / 首项内缩（搜索页/歌手页是给表头 `statusBarsPadding`，设置页/音效页给 `DetailTopBar(withStatusBarInset=true)`），`DetailTopBar` 因此多了一个参数。
- **`targetSdk = 35`**：Android 15+ 只对 targetSdk≥35 的应用真正放开边到边，否则系统强加一个不透明状态栏色块（用户真机 Android 17 看到的就是这个）。
- **`blockSlice` 的相邻行填充要**纵向各让 1px 重叠**：两块抗锯齿矩形严丝合缝相接时交界像素覆盖率之和 <1，会漏出底下的阴影色——就是用户报的「歌曲列表光影在每一行间断裂」。搜索页没有这个问题正因为它整块渲染、没有行缝。
- **弹窗关闭也要动画**：`ShadeDialog` 用 `Animatable` 播倒放（`animateFloatAsState` 没法在播完后才回调），收起钮走 `close()`（先倒放再 `onDismiss`）。
- **设置页分区标题坐在自己的凸起小块上**（`SectionTitle`），不再平铺。
- **推荐卡是 2×2**：左上封面（占地最大）、左下歌名+歌手（跑马灯）、右上详情（凹陷槽、**大小固定**=封面高、超出可滚）、右下三个动作钮。
- **后台播放必须有 `foregroundServiceType=mediaPlayback` 的前台服务**（用户真机 Android 17 实测「后台一两分钟自动停」）。踩坑：① 先试 media3 的 `MediaSessionService`，**它在这台设备上不提升前台**（`startForegroundCount=0`、通知不挂出）→ 改成自己显式 `startForeground(id, n, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`；② 没有 `MediaSession.setSessionActivity` 时 media3 会跳过通知；③ `MediaItem` 要带 `MediaMetadata`（歌名/歌手/封面）否则通知空白；④ Android 13+ 需运行时申请 `POST_NOTIFICATIONS`；⑤ media3-session 会拉进 kotlin-stdlib 2.2.10，与本工程 Kotlin 2.0.21 编译器不兼容 → 在 `app/build.gradle.kts` 里 `resolutionStrategy.force` 钉回 2.0.21。

## 2026-10-01 第四批（高光断痕 / 顶栏高度统一 / 底栏色调）

- **`blockSlice` 的受光切片：圆角只能出现在块的真实端点**。中间行原先也在 `h` 处收口并带 22dp 圆角，圆弧落进行内 → 每行一条高光断痕（用户实测「断裂的是高光阴影」）；暗影切片的中间行本来就是无圆角矩形，所以只有高光看得出。中间行改成两端外延的纯矩形，Head/Tail/Single 才用圆角。
- **顶栏高度必须由顶栏自己带状态栏内缩**：`ListTopBarRow`（歌单/我喜欢/专辑/电台）与 `DetailTopBar`（设置/音效）都加 `statusBarsPadding()` + 上下 12dp，列表的 `contentPadding` 顶部**不再叠加**内缩（否则返回键比别的页低一截，用户按返回键位置比过）。实测三处返回键 y 一致（215/215/215）。
- **底栏（播放栏/选择底栏）用 `barTint(colors)` 上色**，不再直接用背景色（那样面板与页面糊成一片、看不出是一条底栏）：浅色 = 背景向黑混 7%、深色 = 向白混 9%；`BottomBarFade` 与面板共用同一色，交界才不露痕。
- 播放栏的歌名块与歌手块用 `shadePressable`（凸→凹形变 + 背景微暗 + 触感），与其它按钮同一种反馈；`shadeSurface + flatPressable` 只有背景变暗。

## 2026-10-01 第五批（搜索分页 / Switch 强调色 / 底栏回背景色 / 切换过渡 / 渐隐带）

- **搜索结果是分页的**（`SearchApi.songs/singers/albums/playlists(q, num=30, page)`，接口本来就收 `page_num`，UI 原来只取第 1 页）。现在滚到底部前 3 项触发 `appendMore()` 续页，`pageByTab`/`exhausted` 按标签各记一份，`doSearch` 时清空；续页中的转圈是列表末尾一个 item。触底检测必须放在**组合上下文**（页面根 Box 之后），放进 `LazyListScope` 里会编译不过。
- **`ShadeSwitch` 打开态的轨道直接用强调色**（原来 `lerp(背景, 强调色, 0.55)`，看着比真正的强调色发白发灰）。
- **底栏（播放栏/选择底栏）用背景色**（`barTint()` 就是 `colors.background`）：试过浅色偏黑灰/深色偏灰白，观感更碎，已按用户要求回退。要再区隔就改 `barTint()` 一处。
- **分段控制器切换要有短过渡**：设置页各分区用 `AnimatedVisibility`（上升淡入 160ms / 下降淡出 120ms），搜索结果与歌手页的卡片行 / 歌曲块用 `Modifier.animateItem()`（同样的淡入淡出）。
- **`TopEdgeFade` 的渐隐带高度是 32dp**（原 48dp）：带子越长越像"整体变淡"、看不出有一条线；32dp 才看得出界线。
- 页面底部留白一律 `rememberPlayerBarSpace()`（`TrackListScreen` 原来还硬编码 150dp）。

## 2026-10-01 第六批（顶栏/渐隐线彻底统一）

- **二级页面覆盖层 `SecondPageOverlay` 不能加 `statusBarsPadding`**：它会把整层（含该页的 `TopEdgeFade`）下推一整个状态栏，于是「只有主页的渐隐线是对的、其它页都低一截」——用户反复反馈的就是这个。状态栏内缩一律由各页自己的顶栏组件负责。
- **首个内容的统一高度 = 状态栏 + `TOP_FADE_BELOW_STATUS_BAR`(8dp) + 12dp**：主页用 `contentPadding = topInset + 12.dp`；其他页用 `rememberTopContentInset(extra = 12.dp)`（`ListTopBarRow` / `DetailTopBar` / 搜索页表头 / 歌手页表头）。实测各页首个内容起点一致（180.5px）。
- 排查这类问题时注意：`uiautomator` 报的 `content-desc="返回"` 是按钮**内部的图标**（50px），不是 42dp 的按钮本身——按它判断"顶栏偏低"会误判 30px。

## 2026-10-01 第七批（过渡统一 / 飞位边缘卡片 / 详情槽吃滑动）

- **分段控制器切换统一用「纯淡入淡出」**（170ms 进 / 120ms 出）：设置页分区早前带 `slideInVertically { it / 10 }`，分区越高位移越大（几百 px），看着很怪；搜索页「歌曲」块原来没有 key（新旧被当成同一个 item，既不淡出也不淡入），补 `item(key="songs") + Modifier.animateItem`；歌手页歌曲行同款。
- **飞位起点要用「未裁剪」的边界**：该 Compose 版本的 `boundsInRoot()` 会按父级裁剪 —— 卡片在屏幕边缘只露一半时 `width/height` 被裁小，克隆卡于是缩成小框（用户实测）。改用 `Rect(coordinator.localToRoot(Offset.Zero), Size(size.width.toFloat(), size.height.toFloat()))` 手工构造。**凡是用 `boundsInRoot()` 做转场起点的都适用。**
- **详情槽要吃掉用不完的滑动**：`Modifier.nestedScroll { onPostScroll = { _, available, _ -> available } }`，否则槽内滑到头会把滚动传给宿主列表（在主页表现为"在详情里划一下整页跟着动"）。

## 风格分工（2026-10-03 用户规定）

**新拟物为主，M3 为辅。** 当一个元素**不好用新拟物风表达**时（细长杆件、机械零件、需要"悬浮/叠层"层次的小构件），就改用 **Material 3 风格**——M3 在这里是**点缀**，用来让整体 UI 活跃起来，不是第二套主题。

- 判断标准：如果硬做新拟物会变成"要么全是凸起、要么光影糊成一团"（典型：黑胶唱臂唱头这种像铅笔的细杆件），就换 M3。能用新拟物表达的照旧用。
- M3 元素的色板取 `MaterialTheme.colorScheme` 的角色色（`surfaceContainerHighest` / `secondaryContainer` / `surfaceVariant` / `outlineVariant` 等）——这些角色由 `ShadeTheme` 依据 `ShadeColors` + 强调色推导，所以跟随深浅色与莫奈取色。`ShadeTheme` 已补齐黑胶使用的角色；新增角色时仍需明确映射，别用 M3 基线紫色的默认值。
- M3 的形态语汇：胶囊/大圆角长条、倒角矩形、同心圆；**不用描边区分，靠色块区分**；阴影用 elevation 语汇（柔和、大模糊、低透明、可两层 key+ambient），而不是新拟物的"一亮一暗两影"。
- 首个使用者：`ui/player/VinylTurntable.kt` 的唱臂 / 唱头 / 唱臂底座（原为高对比黑白灰的"铅笔"造型）。

## Codex 接续与工具（2026-10-04）

- 黑胶旧方案与工作流位于 `.zcode/vinyl-v2-plan.md`、`.zcode/workflow-runs/`；仅作历史证据，最新用户要求优先，保留中间文件。
- 已有项目 skills：`neumorphism` 用于主界面规范；`qqmusic-web-api` 仅涉及在线接口时使用；`testing-setup` 仅需要新增 Android 测试设施时使用；`edge-to-edge` 用于系统栏问题。全局 `team-mode` 与 `ui-ux-pro-max` 可用于分工审查及局部视觉检查。已有工具足以完成本地 Android 修复，无需为本任务连接无关账号插件。
- 本轮只操作 `emulator-5554`，不操作真机。整个任务截图浏览总预算 5 张（含子代理），由主代理统一分配。
- 用户提供本地代理 `http://127.0.0.1:17890`；GitHub 可直连。Python GitHub 工具若证书失败，可清除该次命令的代理变量并使用 `/opt/homebrew/etc/openssl@3/cert.pem`，不得关闭 TLS 校验。

- 黑胶状态机回归：`app/src/androidTest/java/com/neumusic/player/VinylReviewInstrumentation.kt` 是无外部测试依赖的专用设备回归入口；构建 `:app:assembleDebugAndroidTest` 并安装测试 APK 后运行 `adb -s emulator-5554 shell am instrument -w com.neumusic.player.test/com.neumusic.player.VinylReviewInstrumentation`。它不访问在线接口、不截屏，覆盖进度、片尾、同封面曲目身份、换片后半新请求和暂停防抖。

## 用户验收交付（2026-10-04 用户明确要求，后续每次适用）

- 需要用户验收的应用，每次修改完成后必须：构建最新代码 → 将最新安装包安装到指定验收设备 → 打开应用供用户验收。仅构建成功或提交代码不算完成交付。
- NeuMusic 默认验收设备为 `emulator-5554`，不操作真机。执行 `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`，成功后重新启动 `com.neumusic.player/.MainActivity`，确认应用处于前台。保留现有应用数据，不清空账号或设置。
- 最终回复明确说明构建、安装、打开是否成功。若设备不可用或安装/启动失败，应如实报告原因，不能声称已可验收。
