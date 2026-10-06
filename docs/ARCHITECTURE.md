# ARCHITECTURE — NeuMusic 当前架构

> 本文描述仓库当前如何工作（现行事实），是架构/所有权改动与 UI 硬规则的第一事实来源。
> 历史调查与批次叙事在 `docs/history/`；组件供应与升级见 `docs/HELPERNEXT.md`；测试与设备授权见 `docs/TESTING.md`。
> QQ 协议端点/comm/参数细节不在本仓库维护（归 `QQMusicApi_HelperNext` 所有）；本文只保留宿主作为消费方的行为规则。

## 分层总览

```text
ui/        Compose 界面：AppRoot（页面栈 + 覆盖层）、home/ player/ singer/ search/ settings/ common/、Nav.kt
shade/     视觉与交互基元：Shade.kt（shadeSurface/shadeInset/shadePressable/ShadeFusedTabs）、DayLight.kt（随时间光影）
data/      数据层：api/（HelperNext 组件入口 + 按域薄适配 + ApiCache）、存储类（Prefs/HomeCache/RecommendStore/
           LikedStore/DownloadStore/SearchHistoryStore/NicknameCache）、Lyrics/（模型+LRC 解析+行查找）、Models、AppLog
player/    播放与音频：PlayerHost（ExoPlayer 单例）、EqualizerHost、DynamicsFxHost、VizHost/VizProcessor、SmartEq
app/helpernext/  HelperNext 组件产物（generated/vendor，见 docs/HELPERNEXT.md）
```

依赖方向永远是 `ui/ → data/api → HelperNext → QQ 音乐`，绝不反向。

## 工程结构（模块索引）

- `app/` — Kotlin + Jetpack Compose，包名 `com.neumusic.player`。
  - `shade/Shade.kt` — 视觉与交互基元（风格参考 ShadeCraft）：`shadeSurface`（凸起）、`shadeInset`（凹陷）、`shadePressable`（按压交互）、`ShadeFusedTabs`/`ShadeFusedTab`（基元保留，现仅搜索页使用）。
  - `data/`：
    - `api/HelperNext.kt` — BoltFFI/JNI 组件入口：在应用私有 `filesDir/HelperNext` 初始化；启动时把旧 SharedPreferences 凭据迁移进组件并在成功后删除旧副本；`call()` 薄封装 + `ApiCache`。
    - `api/` 按域薄适配：`SearchApi`、`PlaylistApi`、`RadioApi`、`SongApi`（取链接 + 喜欢写入）、`LyricApi`、`UserApi`、`SingerApi`、`QqMapper`（组件 JSON → UI 模型）、`ApiCache`。
    - `AppLog` — 结构化诊断日志（filesDir/diagnostics.log；设置里可开关/清空/限容 MB 超限砍前一半/SAF 导出；默认开）。
    - `RecommendStore` — 推荐歌曲预缓冲 5 首（曲目+介绍）落盘持久化。
    - `Prefs` — 凭据入口（实际存组件凭据文件）/音质/播放模式/外观/音效预设存储（eqStore、eqGenreMap）。
    - `Downloader`/`DownloadStore` — MediaStore 下载 + 台账；`LikedStore` — 本地喜欢态缓存；`SearchHistoryStore`、`HomeCache`、`NicknameCache`（内存缓存）。
  - `player/PlayerHost.kt` — ExoPlayer 单例（操作必须投递到主线程）；持有 `current`/`isPlaying`/`liked`/`lyrics` 等 StateFlow；音频会话 id 写入 `Prefs.sessionIdForFx` 供各音效挂载。
  - `player/EqualizerHost.kt`（平台 `Equalizer`/`BassBoost` + 自有预设存储）、`DynamicsFxHost.kt`（DVC，API 28+）、`VizProcessor.kt`/`VizHost.kt`（可视化电平）、`SmartEq.kt`（曲风→预设映射）。
  - `ui/` — `AppRoot`（无导航条，`Nav` 页面栈 + 播放页上滑覆盖层）、`Nav.kt`、`home/`、`player/`、`singer/`、`search/`、`settings/`、`common/`。
- `.agents/skills/` — 本地 agent 技能（`neumorphism` 新拟物规范、`qqmusic-web-api` 端点字典、官方 Android skills）。**本地未入库**（被 .gitignore 忽略），不是仓库现行事实。

没有边车、没有 Python 运行时：QQ 请求/签名/设备档案/凭据/QRC 解密全部由 HelperNext Rust 组件内嵌执行；历史上的 `qqmusic-service/`、Chaquopy 方案已删除。

## HelperNext 边界与数据流

- 网络请求经 BoltFFI/JNI 同步调用，**一律包在 `Dispatchers.IO`**，不阻塞主线程。
- 组件初始化在 `data/api/HelperNext.kt`，使用应用私有 `filesDir/HelperNext`。
- 各域 Api 只做「组件响应 → UI 模型」的薄适配（`QqMapper`）；不得在宿主出现 QQ 端点名、请求信封、签名/设备逻辑或 raw 上游 JSON 解析的新增代码（规则见 `docs/HELPERNEXT.md`）。
- typed BoltFFI Kotlin API 是消费目标；typed 迁移落地前维持现有 raw 域适配（见迁移期注意）。

## 凭据流

- **唯一持久来源是组件管理的 `files/HelperNext/Credential/qqmusic-credential.json`**（内容为 uin + musickey + euin；值绝不打印、提交或写进任何报告）。
- 宿主 `Prefs.credential` 只是转发入口：读组件文件、保存时交给组件导入并删除旧 SharedPreferences 副本（迁移在 `HelperNext.kt` 初始化自动完成，失败则下次启动重试）。
- 登录唯一入口 = 网页登录：WebView 打开 y.qq.com → 读 cookie 的 uin/qm_keyst（+euin）→ 交给组件导入。缺 euin 时收藏类列表不可用，界面提示重新登录。
- **账号切换/退出必须清理宿主缓存与在途结果**：`ApiCache` 的每个条目按当时的 `credential` 键控（`ApiCache.kt:20,33`），账号变化即在途结果作废（`ApiCache.kt:42`）；`LikedStore` 用 generation + credential 双守卫（`LikedStore.kt:24,45`）。同步清理歌词等派生缓存。
- 迁移目标：宿主不得读组件私有凭据文件路径/schema、不得用会话秘密判断登录态；现状 `HelperNext.kt` 的直接读取按 Phase C/D 退役。

## 分页与数据消费（nextOffset 语义）

歌曲页分页（我喜欢/歌单/专辑/歌手歌曲与专辑）使用组件返回的 `nextOffset`：

- **按原始行偏移推进**，不能按过滤/去重后的曲目数或请求 num 推进。
- 无 MID 的不可用记录仍占上游位置；服务器 total 与可播放 ID 数可以不同——不可用条目影响游标，不影响结束判断。
- 歌手服务可能至少回 30 行：组件先按请求 limit 限制原始窗口，再解码。
- 歌手页的歌曲与专辑加载状态**分别缓存**（避免切换标签时互相挡加载）。
- 分页读取要处理不可用条目、已知/未知总数、停滞重试；部分页不入完整缓存；账号切换使在途分页结果失效。

typed 迁移后此语义由 `TrackPage.nextOffset` 等模型继续承载，仍是消费侧行为依据。

## Compose 取数模式（硬经验）

- **取数必须由 `LaunchedEffect(键)` 驱动**：切换键自动取消上一条取数协程并在 `finally` 复位 loading。共享 loading 互斥 + `scope.launch` 手动取数的组合已被实测否决——新键的加载被在途请求挡掉（「切过去是空的，再切一次才恢复」）、加载被取消后 loading 永久卡死。
- **列表/加载字段用 `mutableStateOf` 装**：普通 `HashMap` 写入不触发重组。
- 推荐歌曲的取歌/补货必须挂**页面级 scope**：挂 `LaunchedEffect(radioGroups)` 会被分组刷新取消（实测 LeftCompositionCancellationException）。
- 搜索分页的触底检测放在**组合上下文**（页面根 Box 之后）；放进 `LazyListScope` 编译不过。
- 搜索分页：滚到底部前 3 项触发 `appendMore()`；`pageByTab`/`exhausted` 按标签各记一份，`doSearch` 时清空。

## 音质与播放链接降级（消费侧语义）

- 六档音质，filename 前缀与扩展名成对：`M500`=.mp3 标准128k、`M800`=.mp3 高品质320k、`C400`=.m4a 流畅96k、`O600`=.ogg 192k、`O800`=.ogg 320k、`F000`=.flac 无损。
- `playUrl(track, quality)` 按档位顺序**逐级降级**，取到即返回；网络层无需额外重试封装。
- **`104003` 是「档位级」结果**：免费歌的无损/高品质档同样返回（该档位要会员权益），只有标准档失败才是真没权限。**降级链中见到 104003 绝不能中断**（否则音质设为无损时所有歌都误报「没有会员」）。重新登录可修复 104003（票据失效时 VIP/无损全挂，旧票据读接口仍正常，别误判成 cookie 失效）。
- 先用 `Track.fileSizes` 过滤该曲目不存在的档位再请求。
- `101404`（purl 空）是**短时限流**，不是权限不足，隔几秒即可恢复；`22` 才是未登录。
- 请求档位与组件音质标识的映射只放在一个适配位置（`SongApi`）。

## 喜欢写入（live 写，消费侧行为）

- 必须用**数字 `songId`**（`songId<=0` 无法操作），不是 mid。
- **业务码 `1000` = 风控限流**：短时间多次写后持续返回，读接口同时完全正常，继续重试只会延长限流。**写失败不自动重试**；`LikeResult.Rejected(1000)` 呈现为「操作太频繁，已被限流，请稍后再试」（`TrackRow.kt:357`）。播客/白噪音类曲目同样可能返回 1000，无法与限流区分。
- 不回退到 `PlaylistFavWrite/CgiAddSonglist` + songMid 路径（返回 40000）。
- 写测试纪律见 `docs/TESTING.md`。

## 歌词（消费侧规则）

分工：HelperNext 拥有 QQ 歌词取回与 QRC 解密（hex 密文→组件内解密→QRC 明文）；宿主拥有缓存、对齐、注音映射、优化与渲染。

- **QRC 字时间是「绝对毫秒」，不是相对行首**：判别基准是「字时间最大值是否超过行时长」（相对时间的行不可能超出自身时长），再决定要不要加 `lineStart`。按 `lineStart + 字偏移` 换算会使除第一行外全部扫色失效。
- **翻译 `trans`**：base64 明文 LRC，无翻译行是 `//` 占位，与原文同时间轴；日文曲带 `[kana:<n><读音>…]` 注音元数据——token 流按行时间序贯穿整首歌、**只有汉字消费 token**（假名/拉丁/标点保留原字），逐字行渲染成字上注音、行级 LRC 渲染成读音行。
- **音译 `roma`**：hex QRC 密文同一套解密，解出逐字罗马音，按行起点对齐回主歌词行（±150ms）；部分曲目该字段为空，使用前先探测。开关：`Prefs.showLyricTranslation/showLyricRoman/showLyricKana`。
- **歌词优化策略**（自研）：空格规范化；清洗非刻意重叠（与下一行重叠 <500ms 且 ≤100ms、或 ≤下一行时长 10% → 截断上一行末字）；行起点提前（间隔 ≥600ms 提前 600、≥400 提前 400、否则提前间隔的 70%；只提前行起点，字时间不动）。`LyricLine.endMs` 只在逐字行有意义。
- **渲染硬经验**（`ui/player/LyricsView.kt`）：
  - 填充下标必须是播放位置的**纯函数**，不要用 `LaunchedEffect(positionMs, …)` 写状态（positionMs 33ms 变一次键，被取消的 job 可能一次都没执行，填充恒为 0）。
  - 逐字渲染按**字单元**（FlowRow 每字双层 Text、按已唱比例横向裁剪）；填充层在尺寸未量出的第一帧什么都别画。
  - 浏览态（拖动开始/双击后 3 秒内）聚焦行 = **视口 35% 锚点行**（`layoutInfo` 里挑覆盖锚点 y 的项），之后回归正在播放行；双击任意行 = seek 到该行。
  - 纯歌词→封面+歌词模式的收起：列表到顶后继续下拉的剩余量经 `onPullDownCollapse` + nestedScroll 回传（该模式 `LocalOverscrollConfiguration=null`，否则 stretch 吃光剩余量）。

## 播放器行为（player/PlayerHost）

- ExoPlayer 单例，**操作必须投递到主线程**；音频会话 id 写入 `Prefs.sessionIdForFx`。
- 取链接失败（限流）后播放器停在 IDLE：`toggle()` 必须对「IDLE 或 ENDED 且 current 仍在」走**重新解析（playCurrent）**——IDLE 下 `play()` 无效，ENDED 下 `play()` 会重播刚放完的歌。
- 自动切换失败不能静默：`PlayerHost.onError` 由 AppRoot 注入 toast。
- 「下一首播放」用 **`pendingNext` 待播队列**实现（`PlayerHost.kt:85`；随机模式下也保证先播），不能只改 index——ENDED 时 advance 会把「下一首」整个跳过。
- 随机「上一首」按 `playedHistory` 回跳（`PlayerHost.kt:74`），历史为空按顺序回退（不能随机）。
- 变速/变调用 media3 `PlaybackParameters`（0.5..2.0，变速不变调可分开）。
- 可视化：`VizHost.BARS = 16` 段电平（`VizHost.kt:12`），播放栏/播放页频谱环 `VIZ_BARS = 40` 根（`PlayerBar.kt:394`）从段电平降采样；优先系统 `Visualizer` FFT，不可用回退 `VizProcessor` PCM 电平（Visualizer 需 manifest 声明 RECORD_AUDIO）。动态 State 只在绘制阶段读取。
- 黑胶模式（`Prefs.vinylMode`，规格见 `SPEC_VINYL.md`）：播放页喂真实播放状态/曲目 id/每帧进度，**视觉状态机不接管音频切歌**；**曲目身份按 mid 判断**，不能只看封面 URL（同专辑/无封面歌曲仍要换片）；唱头半径随进度 0.48D→0.32D；不做片尾提前抬臂（慢速下会重复起播旧片）；快速切歌的 3 秒窗口仅作用于视觉；换片中到达的新请求必须保留，暂停时也能换封面。

## 音效（audiofx）

- `EqualizerHost` — 平台 `Equalizer`/`BassBoost` + **自有预设存储**（`Prefs.eqStore` JSON：首次挂载快照设备内置预设增益，此后运行时不碰 `usePreset`；所有预设可改，选中后拖频段直接写回该预设；可新增/删除非内置预设）。
- `DynamicsFxHost` — `DynamicsProcessing`（DVC 动态范围压缩 + 声道平衡，API 28+）。注意 SDK 真实 API 是 `Config.Builder(9参)` 与 `setInputGainbyChannel`，**没有顶层 Builder**。
- `SmartEq` — 智能调音：`Track.genre`→预设名映射，码表实测校准：1=流行 2=古典 22/50=摇滚 23=民谣 27=爵士 28=金属 33/20=电子 34=说唱；未知码不动。映射存 `Prefs.eqGenreMap`，音效页可改（每个曲风挑任意预设，含自建）。

## 存储与后台

- 下载：`Downloader`/`DownloadStore` 负责直链拉流 → MediaStore 落位 → 命名 → 用户可见台账（列表置灰）。MediaStore/台账语义归宿主，不进组件。
- 后台播放必须有 `foregroundServiceType=mediaPlayback` 的前台服务：**自己显式 `startForeground(id, n, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`**（media3 的 `MediaSessionService` 在部分设备上不提升前台）；`MediaSession` 要设 `setSessionActivity`，`MediaItem` 要带 `MediaMetadata` 否则通知空白；Android 13+ 需运行时申请 `POST_NOTIFICATIONS`；media3-session 拉进的 kotlin-stdlib 2.2.10 与工程 Kotlin 2.0.21 不兼容，已在 `app/build.gradle.kts` 用 `resolutionStrategy.force` 钉回。
- `targetSdk = 35`：Android 15+ 只有 targetSdk≥35 才真正边到边。compileSdk 36、minSdk 26（组件 24）。

## 视觉与交互硬规则（UI）

新拟物规范全文见 `.agents/skills/neumorphism`（本地未入库）与根 AGENTS.md「产品规范与工作环境」；以下是与代码强耦合的实现规则：

- **新拟物三原则**：不在凸起上再做凸起（例外：播放栏/选择底栏这类容器级凸起面板上的独立控件可再凸起；普通内容面板内部保持平）；不在凹陷中再做凹陷；凸起母体中以凹陷表示选中、以平表示未选中。
- **新拟物基元只用 `shadeSurface`/`shadeInset`/`shadePressable`**（`ShadeFusedTabs` 基元保留，现仅搜索页使用）；黑胶机械件与音频胶囊使用明确授权的 M3 Expressive 例外；其余页面不引入 Material elevation/ripple。
- **风格分工**：新拟物为主、M3 为辅——只有硬做新拟物会变成「全是凸起或光影糊成一团」的元素（细长杆件、机械零件、悬浮小构件）才换 M3；M3 色板取 `MaterialTheme.colorScheme` 角色色，由 `ShadeTheme` 依 `ShadeColors`+强调色推导（跟随深浅色与莫奈取色），不用 M3 基线默认值；M3 语汇：胶囊/倒角矩形/同心圆、靠色块区分不靠描边、elevation 式柔和阴影。
- **`shadePressable` 三点规范**：① 凸→凹阴影渐变形变——外阴影透明度 1→0 与内阴影 0→1 在 160ms 内交叉插值，绝不瞬间翻转；② 已凹陷再按，内阴影强度 ×1.45（`innerStrength`）；③ 按下背景向黑混 6%。新控件直接复用。
- **卡片点击必须用 `cardTap`（pointerInput）**——`flatPressable` 的 clip 会把卡片内画框的左右阴影直接截断。
- **歌手头像画框必须用 `ui/common/SingerAvatarFrame`**（歌手页头像/搜索页歌手卡/主页关注歌手卡/飞位克隆卡共用）：内缩/圆角/画框 shade 按 216dp 基准等比换算，任何宽度下归一化规格一致；凡与歌手页头像交接（飞位）的渲染只能有这一个来源。
- **飞位转场（搜索页/主页歌手卡 → 歌手页）**：
  - 两段式进入：页面其余元素随遮罩淡出 → 克隆卡缩放+平移飞向头像落点（tween 440ms FastOutSlowIn，文字按 1-2.4t 淡出）→ **飞行结束后**页面整层纯淡入 200ms（无位移——上浮会让落点测量变成瞬态位、落位不重合）。
  - 落点由 `onGloballyPositioned → boundsInRoot` 上报；飞位起点要用**未裁剪边界**：该 Compose 版本的 `boundsInRoot()` 按父级裁剪，边缘半露卡片会被裁小——用 `Rect(coordinator.localToRoot(Offset.Zero), Size(w,h))` 手工构造（凡用 boundsInRoot 做转场起点的都适用）。
  - 透明触摸拦截层显隐用 `singerLanding = t < 0.999` 门控（用 singerFlying 会让拦截层落位后永久存在、全屏吃点击）；`open/back` 与回飞窗口期（`singerBackPending`）均防重入，动画走完才弹栈。
  - 压专辑详情=整层淡出而非回飞；飞位路径播放栏不上升、随转场淡出。
- **播放栏 `singerSlide` 只能由「不带 origin 的歌手页」驱动**：带 origin 走飞位路径、底栏只淡出；否则弹栈那一帧 `ride` 会从 0 跳回 1。
- **`basicMarquee` 必须直接挂在 Text 上**、由父级 Box 限制宽度（挂在被 `fillMaxWidth` 收紧的链上不会滚）。
- **`ui/common/ShadeSlider.kt` 用 `detectHorizontalDragGestures`**（`ShadeSlider.kt:60`）——无方向 `detectDragGestures` 会在滚动容器里吃掉竖向滚动。
- **`TopEdgeFade`**：彻底消失线=状态栏下缘 +8dp（`TOP_FADE_BELOW_STATUS_BAR`），渐隐带高 32dp；由 `canScrollBackward` 门控；**状态栏内缩由各页自己的顶栏负责**（`ListTopBarRow`/`DetailTopBar` 自带 statusBarsPadding；搜索/歌手页给表头加），**二级页面覆盖层 `SecondPageOverlay` 禁止加 `statusBarsPadding`**（否则整层含渐隐线下推一整个状态栏）。页面底部留白统一 `rememberPlayerBarSpace()`（`BottomBarMetrics`，播放栏高度由 AppRoot 写入；无曲目返回 0）。
- **`blockSlice`**：相邻行填充纵向各让 **1px 重叠**（严丝合缝时交界像素覆盖率 <1，漏出阴影色=行间断痕）；**受光切片圆角只能出现在块的真实端点**（中间行用两端外延的纯矩形，Head/Tail/Single 才圆角）。
- **底栏（播放栏/选择底栏）**：`barTint()` 即背景色（要区隔只改这一处）；面板不画阴影、不做圆角（外围光影会与 `BottomBarFade` 糊在一起）；`BottomBarFade` 与面板同色。
- **弹窗=整页 `ShadeDialog`**：纯色底（不压暗不模糊）+淡入浮现+顶栏收起钮+内容坐凸起卡；关闭也要动画（Animatable 倒放完再 onDismiss）。
- **详情槽吃掉用不完的滑动**：`Modifier.nestedScroll { onPostScroll = { _, available, _ -> available } }`，否则槽内滑到头把滚动传给宿主列表。
- 设置页分 6 区（SETTING_TABS：账号/外观/光影/播放/下载/其它），分区切换用纯淡入淡出（170ms 进/120ms 出）；`ShadeSwitch` 打开态轨道直接用强调色。
- 搜索页所有元素随内容上滑（表头都是列表 item）；歌手页歌曲|专辑、最新|热门分段控制，同一 LazyColumn，切换时滚动位置统一。

## 转场架构

- **一二级页面切换 = 场景推拉 + 渐进模糊 → 页面淡入上浮**：进入只有一级场景根容器做 uniform scale + translation（S = max(W/cardW, H/cardH)），模糊 0→26dp 随进度增强；推满停 80ms → 二级页整屏淡入 + 上浮 12dp（220ms）。返回：淡出下沉 200ms → 回缩+模糊渐消 400ms → 弹栈。封面/卡片不做任何仿射变形。
- **性能红线：推拉进度只能在 graphicsLayer 的 lambda 里读**（组合期读 = AppRoot 每帧整树重组卡死）；落定用 `StackEntry.settled`；二级页内容 t>0.05 就组合；转场期阴影淡出（`LocalShadeShadowAlpha`）；主页稳定 2.5s 后幕后预热列表页。
- **层级**：推拉层 zIndex(3)；迷你播放栏 zIndex(3.5)；播放页覆盖层 zIndex(4)。
- **返回 = 弹出页面栈**：`ui/Nav.kt` 的 `Nav` + AppRoot 的 `mutableStateListOf<Nav>`；返回按钮与系统返回键都只做「弹出栈顶」，栈只剩主页交还系统；同页面多来源，压栈后各自返回各自来源（新页面必须走 `open(Nav.X)`）。
- `Nav.Player` 是特殊栈顶（全屏覆盖层，`page` 取栈里最后一个非 Player）；`Nav.Equalizer` 压在 Player 之上：打开时播放页下滑露出音效页，返回时播放页重新升起、**动画结束后才真正弹栈**（立即弹栈会让底下页面闪现再重放升起动画）。
- 打开播放页/歌手页=整条底栏升到屏幕顶外「带出」覆盖层：同一条时间轴锁步（页面 translationY 与底栏 translationY 用同一进度），返回精确倒放；飞行中页面不做透明度渐变；覆盖层单一实例贯穿飞行与落定（按 steady 切子树会取消加载协程→列表空白）。

## 随时间变化的光影（shade/DayLight.kt）

- **模型**：`Lighting(sx, sy, blurDark, blurLight, alphaDark, alphaLight, warmth, k)`，全部是**倍数**——修饰符自己的 offset/blur 是标称尺寸，光照只按轴缩放；倍率基准 6dp/10dp（本工程最常用值），组件 offset 相对 `REF_OFFSET_DP = 6dp` 等比缩放。
- **两套 0–24h 曲线**：白天（`DAY_KEYS`，色温 2000→5500→2000K，有效区间 6:00–18:00，区间外沿用端点值）与黑夜（`NIGHT_ATTR` 恒定 8000K、光弱而弥散）。**选哪套由深色模式决定，不由时刻决定**：浅色主题→白天、深色→黑夜、「随系统」→跟系统。
- **偏移日夜共用一条连续扫描**（6:00 最左 → 12:00 归零 → 18:00 最右 → 24:00 归零），昼夜交界天然接上；偏移幅度用两条独立 min/max 曲线（暗色阴影默认 3.5/10dp、高光阴影默认 3.5/5dp；正午/午夜最短、日出/日落最长），相位同源。术语统一「暗色阴影/高光阴影」；`DayLightHost.lightRatio`（设置可调）只缩放高光侧位移。
- **时刻**：`LightingMode.TIME` 跟真实时钟（`runClock()` 每 30 秒对时，MainActivity LaunchedEffect 启动）；`FIXED` 停在设置时刻（0.5h 对齐）。默认 `FIXED` + 12:00。
- **色温**：`warmthOfK` 分段线性，三锚点（2000/5500/8000K）设置可改，改任一个整条曲线重塑；**只给两影着色（tempTint），底色不碰**——立体感依赖底色与两影的明度关系。
- **性能红线**：`DayLightHost` 字段都是快照状态，只在 `drawBehind` lambda 里读 `current(colors.isDark)`——设置一改就重绘、不引起重组；设置页读数才用 `preview()`（组合期读）。
- 教训：动态光源（日光/月光+彩色长投影）方案整体回退过——「多层模糊矩形外推」式长投影有光斑且帧时间爆炸，OKLab 的 cbrt 不能用 sqrt 冒充；Shade.kt 回退不能整文件 git checkout（会丢未提交批次），只能精准回退。

## 历史证据

- `docs/history/2026-10-05-helpernext-integration.md` — HelperNext 0.2.0 接入的对照表、分工与验证记录。
- `docs/history/2026-10-05-agents-md-experience-archive.md` — 文档体系落地前的旧版 AGENTS.md 全文快照（含 2026-09-25 起的逐批次交互/视觉经验与协议实测记录）。
- `.zcode/` 下的研究文件与工作流记录仅为历史证据，不是现行需求。
