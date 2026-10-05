# 在线层需求盘点（消费侧视角）
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

> 目的：为「接入调研」回答——**现有用户可见功能，究竟向在线层要什么**。
> 只读工程源码得出；不重复接口清单，按「消费方怎么用」写。
> 盘点范围：`app/src/main/java/com/neumusic/player/` 全部消费点 + 根 `AGENTS.md` 的「界面结构」「数据架构」两节。
> 本工程当前是原生直连 QQ 音乐（见 AGENTS.md:108-138），所有「在线层」字样在本文里都指**给 App 供数的服务端能力**。
>
> 图例：`✅` 有在线依赖；`⛔ 无` 纯本地功能（列出以便接入时确认不必迁）。

---

## 0. 总览

| # | 用户可见功能 | 在线层依赖 |
|---|---|---|
| 1 | 主页问候语 | ⛔ 无（昵称接口有取数但界面未消费） |
| 2 | 主页顶部推荐歌曲卡 | ✅ 电台曲目 + 歌曲介绍 + 电台分组 |
| 3 | 推荐卡的喜欢 / 播放态 | ✅ 数字 songId + 我喜欢集合 |
| 4 | 主页三栏：收藏歌单 / 收藏专辑 / 电台 | ✅ 三类列表接口 |
| 5 | 我喜欢的计数卡片 | ✅ 我喜欢总数 |
| 6 | 我喜欢列表页 | ✅ 分页曲目 + 总数 |
| 7 | 关注的歌手栏 | ✅ 关注列表 |
| 8 | 电台分组 → 电台曲目列表（无限流） | ✅ 电台曲目「每次不同」的行为 |
| 9 | 歌单详情页 | ✅ 歌单内歌曲分页 |
| 10 | 专辑详情页 | ✅ 专辑内歌曲分页 |
| 11 | 搜索（歌曲/歌手/专辑/歌单，含分页） | ✅ 四类搜索 |
| 12 | 歌手页（头计数 / 歌曲 / 专辑 / 排序） | ✅ 搜索解析 + 歌手歌曲/专辑 + 专辑歌数 |
| 13 | 曲目行 TrackRow | ✅ 曲目字段合集（songId / VIP / 时长 / 封面） |
| 14 | 通用卡片 MediaCard / SingerCard | ✅ 计数与封面 |
| 15 | 选择下载模式 | ✅ 直链可下载 + 逐首失败语义 |
| 16 | 播放栏 | ✅ 歌手名可解析、封面、喜欢态 |
| 17 | 播放页（封面/进度/时长） | ✅ 直链 + 时长 + 封面 |
| 18 | 歌词（逐字 QRC / 翻译 / 音译 / 注音） | ✅ 四路歌词数据 + 显式空语义 |
| 19 | 播放队列 / 下一首播 / 随机上一首 | ✅ 仅需稳定 mid + 曲目对象 |
| 20 | 音质 6 档 + 自动降级 | ✅ file.* 档位存在性 + purl 错误码语义 |
| 21 | 查看格式弹窗 | ✅ file.* 大小表 |
| 22 | 歌曲信息弹窗 | ✅ mid / 数字 id / 付费标记 |
| 23 | 下载（目录 / 音质 / 已下载台账） | ✅ 可流式下载的直链 + 扩展名 |
| 24 | 智能调音（曲风→预设） | ✅ `genre` 风格码 |
| 25 | 登录 / 凭据 | ✅ cookie 三件套 + 登录档 comm |
| 26 | 诊断日志 | ✅ 失败码/文案（1000、104003…） |
| 27 | 后台播放通知 / 锁屏 | ✅ 封面 URL 可被服务线程直接 GET |
| 28 | 音效页 / 光影 / 新拟物 UI / 转场 | ⛔ 无 |

---

## 1. 逐条需求

### 1. 主页问候语 ⛔ 无在线依赖
- **需要在线层给什么**：不需要。问候语按时段本地随机（`GREETINGS` + `greetingForHour`）。
- **注意（文档与代码不一致，如实记录）**：AGENTS.md:21 写的是「未登录 `请登录`，已登录随机带昵称」，但当前代码**完全不读昵称**——`HomeScreen.kt:215-217` 只做本地时段随机，注释明确「不带用户名（用户要求）」（`HomeScreen.kt:103`）；全工程 grep `NicknameCache.get` 无调用点，昵称仅在登录成功时拉取并落入内存缓存（`SettingsScreen.kt:852`、`Prefs.kt:377-383`）。若接入后的产品要恢复昵称展示，在线层需要 `GetLoginUserInfo → data.info.nick`（`UserApi.kt:46-50`）。
- **证据**：`ui/home/HomeScreen.kt:103,114-124,215-217`；`data/api/UserApi.kt:46-50`；`data/Prefs.kt:377-383`；`ui/settings/SettingsScreen.kt:852`。

### 2. 主页顶部推荐歌曲卡 ✅
- **需要在线层给什么**：
  1. **「猜你喜欢」电台的可识别标识**：App 靠**电台标题字符串** `"猜你喜欢"` 从电台分组里找 station，取其 `id`；分组标题与 station 标题必须稳定可匹配（`HomeScreen.kt:220-222,246-253`）。
  2. **电台逐批取歌**：每批约 5 首、连续批**不重复**（`RadioApi.kt:36-63`），字段齐全到能播放 + 显示（`Track` 全字段）。取歌必须用**真实电台 id**，且带登录 comm（`RadioApi.kt:22-33`）。
  3. **歌曲介绍**：按 `song_mid` 取一段多段文案（`data.info.intro.content[*].value`），可空（空则 UI 显示「该歌曲暂无歌曲详情」）。`SongApi.intro` 匿名 comm 即可（`SongApi.kt:114-133`；UI 占位 `HomeScreen.kt:696`）。
  4. **预缓冲持久化所需字段**：`mid/name/mediaMid/singer/albumName/albumMid/intervalSec/isVip/songId` 要能从列表接口拿到并落盘（`RecommendStore.kt:42-69,121-143`）。
- **行为需求**：刷新是**本地弹出队首 + 后台补货**（`RecommendStore.kt:71-108,110-119`），所以在线层只需支持「按调用给新歌 + 按 mid 取介绍」，不需要「刷新」这类有状态接口。
- **证据**：`ui/home/HomeScreen.kt:150-151,157-175,219-222,246-253,267-295,696`；`data/RecommendStore.kt:71-119`；`data/api/RadioApi.kt:22-63`；`data/api/SongApi.kt:114-133`；`data/Models.kt:4-27`。

### 3. 推荐卡的喜欢 / 播放态 ✅
- **需要在线层给什么**：曲目带**数字 `songId`**（`>0` 才能显示/写入喜欢）；喜欢状态来自全局我喜欢集合（`LikedStore.liked` 是 `Set<Long> songId`）。
- **证据**：`ui/home/HomeScreen.kt:275-285`；`data/LikedStore.kt:15-17,56-62`；`data/Models.kt:13-14`。

### 4. 主页三栏（收藏的歌单 / 收藏的专辑 / 电台） ✅
- **需要在线层给什么**：
  1. **收藏歌单**：`tid/name/logo/songnum`，结果在 `v_list[]`；需 `euin`（缺它静默失败）。`logo` 转 HTTPS 后才能显示（`QqCore.kt:128-131`）。
  2. **收藏专辑**：`mid/name/logo/songnum`，同样 `v_list[]`。
  3. **电台分组列表**：外层 `radio_list[]` 是**分组**、内层 `list[]` 才是电台；卡片用 `id/title/pic_url`（`QqMapper.kt:109-124`）。**分组 id 不能当电台 id**。
  4. **「我喜欢」卡片的特判**：`tid==201L || name=="我喜欢"` 时用爱心代替封面（`HomeScreen.kt:527`）——即收藏歌单列表里必须能识别出「我喜欢」这一项（当前实现依赖 tid=201 或名字）。
  5. **快照恢复**：以上字段都要能 JSON 落盘/读回（`HomeCache.kt:50-152`），字段一旦改名旧快照即失效（可接受但会白屏一轮）。
- **证据**：`ui/home/HomeScreen.kt:181-213,298-379,525-594`；`data/api/PlaylistApi.kt:62-80`；`data/api/QqMapper.kt:85-124`；`data/api/QqCore.kt:128-131`；`data/HomeCache.kt:50-152`。

### 5. 我喜欢计数卡片 ✅
- **需要在线层给什么**：我喜欢（`dirid=201`）的**总数** `dirinfo.songnum`，UI 只取 1 首拿 total（`PlaylistApi.likedPage(0,1).total`）。total 可空（UI 显示「—」）。
- **证据**：`ui/home/HomeScreen.kt:236-241,490-521`；`data/api/PlaylistApi.kt:31-43`。

### 6. 我喜欢列表页 ✅
- **需要在线层给什么**：按 `song_begin/song_num` 分页的曲目 + 总数；歌曲在 `songlist[]`；每首带 `mid/songId` 等全套字段；需 `euin`。
- **行为需求**：App 会**一直翻页到总数**（`guard<50`），并且 `LikedStore` 也以 300/页翻全表（`LikedStore.kt:36-48`）——所以在线层必须支持大 offset 分页，且 total 要准（total 缺失时靠「空页/短页」判断结束）。
- **证据**：`ui/AppRoot.kt:624-629`；`data/api/PlaylistApi.kt:31-43`；`data/LikedStore.kt:36-48`；`ui/home/Screens.kt:205-247`。

### 7. 关注的歌手栏 ✅
- **需要在线层给什么**：`List[*]` 的 `MID/Name/AvatarUrl/Desc` + 总数 `Total`；需登录 + `euin`；无 euin 时 UI 返回空列表并显示「登录后显示」。
- **注意**：卡片第二行用服务端 `Desc`（`SmallSingerCard` 只在非空时显示）；`AvatarUrl` 需 HTTPS；进歌手页后要靠 `name` 反查补齐计数（见 #12）。
- **证据**：`ui/home/HomeScreen.kt:224-231,337-357,774-781`；`data/api/UserApi.kt:19-43,53-59`。

### 8. 电台分组 → 电台曲目列表（无限流） ✅
- **需要在线层给什么**：
  1. 每个电台的曲目接口，**每次调用返回不同的歌**（实测每批 5 首，`num` 被忽略；连续三批 0 重叠）——这是「无限加载」成立的前提（`RadioApi.kt:36-44`）。
  2. 曲目字段能解析出 `mid`（去重的唯一键），并带 `singer`（可能是字符串或数组，`QqMapper.kt:65-83`）。
  3. **失败语义**：全部批次失败必须**抛错**而不是空表（空表会被当作「到底了」）。即在线层需可区分「限流/失败」与「真的没有更多」。
  4. 首屏与续批要能靠 `firstplay=1/0` 区分。
- **证据**：`ui/AppRoot.kt:645-666`；`data/api/RadioApi.kt:22-63`；`ui/home/Screens.kt:130-147,272-290,557`。

### 9. 歌单详情页 ✅
- **需要在线层给什么**：`tid` + `song_begin/song_num` 分页 → 曲目 + `dirinfo.songnum` 总数；需 `euin`。`knownTotal` 只作占位（`Nav.PlaylistDetail.songnum`）。
- **证据**：`data/api/PlaylistApi.kt:46-59`；`ui/AppRoot.kt:631-637`；`ui/Nav.kt:38`。

### 10. 专辑详情页 ✅
- **需要在线层给什么**：参数名**驼峰** `albumMid/begin/num`；曲目在 `songList[].songInfo`（字段与搜索/歌单同构）；总数 `totalNum`。**播客类专辑 `totalNum=0` 属正常**，UI 会显示「已加载 N 首」而不是进度（`Screens.kt:559-563`）。
- **证据**：`data/api/PlaylistApi.kt:82-97`；`ui/AppRoot.kt:638-644`；`AGENTS.md:125`。

### 11. 搜索（四类 + 分页） ✅
- **需要在线层给什么**：
  1. 同一端点按 `search_type` 分四类：歌曲 `item_song[]` / 歌手 `singer[]` / 专辑 `item_album[]` / 歌单 `item_songlist[]`；`num_per_page` + `page_num` 支持分页（UI 触底续页 3 项前触发，`SearchScreen.kt:214-221`）。
  2. **歌曲结果**：必须带全套 `Track` 字段（尤其 `songId` 供红心、`fileSizes` 供下载/音质、`albumMid` 供「查看专辑」）。
  3. **歌手结果**：`singerMID/singerName/singerPic/songNum/albumNum`——`songNum/albumNum` 直接决定歌手卡第二行文案（`SearchScreen.kt:650-654`、`Cards.kt:234-238`）。
  4. **专辑/歌单结果**：`singer/song_num`、`dissid/dissname/logo/songnum`；名字里的 `<em>` 高亮标签与 HTML 实体需 App 侧剥（`QqCore.kt:137-144`）。
  5. **清空语义**：搜不到时 UI 以「空列表」判定「没有找到相关歌曲」（`SearchScreen.kt:185`），所以在线层正常空结果要返回空数组（而非报错）。
- **证据**：`ui/search/SearchScreen.kt:96-98,137-171,214-221,578-687`；`data/api/SearchApi.kt:19-91`；`data/api/QqCore.kt:128-144`。

### 12. 歌手页 ✅
- **需要在线层给什么**：
  1. **歌手解析**：按名字精确匹配 → `mid/name/pic/songNum/albumNum`（复用搜索接口）。关注列表进来的计数为 0 时，UI 用名字**自动补齐一次**（`SingerScreen.kt:109-119`）——即要求搜索接口对歌手名可解析出计数。
  2. **歌手歌曲分页**：`singerMid/order/number/begin` → `songList[].songInfo` + `totalNum`；**`order=1 热门 / 2 最新`**（`SingerApi.kt:24-47`）。
  3. **歌手专辑分页**：`albumMid/albumName/pmid/publishDate/singerName` + 总数 `total`；**`totalNum`（专辑内歌曲数）恒 0**，App 用 `GetAlbumSongList` 批量（30 张/请求、最多 3 批）补出每张专辑的歌数，供专辑卡右侧「N 首」（`SingerApi.kt:69-102`、`SingerScreen.kt:284-296`）。
  4. **列表底部文案**：`已加载 N / 共 total 首|张`；total 缺省时退化（`SingerScreen.kt:328-344`）。
- **证据**：`ui/singer/SingerScreen.kt:109-119,130-188,247-259,275-344`；`data/api/SingerApi.kt:22-102`；`data/api/QqCore.kt:68-72`（多请求块）。

### 13. 曲目行 TrackRow（所有列表共用） ✅
- **需要在线层给什么**（每首必须具备）：
  - `mid`（队列/去重/缓存键）、`name`、`singer`（**可能是 `"A / B"` 形式，或数组**）、`albumName`（为空时退化成时长文案）、`intervalSec`（时长）、`isVip`（`pay.pay_play==1` → 行内 VIP 角标）、`albumMid`（封面 + 查看专辑）、`songId`（喜欢）、`fileSizes`（下载/格式弹窗）。
- **行为需求**：VIP 角标只在 `isVip` 为真时出现（`TrackRow.kt:198-208`）；已下载置灰依赖 `mid`（`DownloadStore`）。
- **证据**：`ui/common/TrackRow.kt:186-221,315-343`；`data/api/QqMapper.kt:27-63`；`data/Models.kt:4-27`；`ui/home/Screens.kt:378-409`。

### 14. 通用卡片 MediaCard / SingerCard ✅
- **需要在线层给什么**：专辑/歌单卡的「N 首」= `songnum`（`>0` 才显示）；第二行的歌手名（搜索专辑结果有、收藏列表没有，App 用 `ifEmpty{null}` 兜底）；歌手卡的「歌曲 N · 专辑 N」= `songNum/albumNum`。封面 `logo/pic` 为空时用占位符（所以空字符串必须是真的空，不能给坏 URL）。
- **证据**：`ui/common/Cards.kt:110-166,211-240`；`ui/search/SearchScreen.kt:658-685`；`ui/singer/SingerScreen.kt:284-296`。

### 15. 选择下载模式（列表页/搜索页共用） ✅
- **需要在线层给什么**：对**选中的每一首**都能取到可写盘的音频字节流（App 用 OkHttp 直接 GET 直链，`Downloader.kt:26-67`）。逐首失败不能中断整批——UI 只 `runCatching` 继续下一首（`SearchScreen.kt:524-535`、`Screens.kt:449-461`），失败文案透传给人（会员/限流/网络，`Downloader.kt:29-33`）。
- **行为需求**：全选/反选会跳过已下载项（按 `mid` 判），所以 `mid` 要在两次会话间稳定。
- **证据**：`ui/search/SearchScreen.kt:502-537`；`ui/home/Screens.kt:427-462,438-448`；`data/Downloader.kt:26-79`；`data/DownloadStore.kt:56-63`。

### 16. 播放栏 ✅
- **需要在线层给什么**：
  1. 封面 URL 可直接被 Coil 加载（HTTPS）。
  2. `singer` 字段可被 App 按 `/`、`、`、`,` 切分；点歌手名时要能用**歌手名**反查 `mid/pic/songNum/albumNum` 进歌手页——即搜索接口对精确名字可解析。
  3. 歌名/歌手要单行跑马灯（长文本原样给即可）。
  4. 喜欢态靠 `songId>0 && songId∈LikedStore`。
- **证据**：`ui/common/PlayerBar.kt:130-167,217-287`；`data/api/SingerApi.kt:28-32`；`ui/AppRoot.kt:417-446`。

### 17. 播放页（封面 / 时长 / 进度） ✅
- **需要在线层给什么**：
  1. **可直接播放的直链**（`https://isure.stream.qqmusic.qq.com/` + purl），登录后 VIP 曲目也要能取到（`SongApi.kt:98-112`）。**取链接失败时 UI 必须能收到人话错误**（PlayerHost 把异常 message 直接 toast）。
  2. **时长**：优先 ExoPlayer 的 duration，未就绪时回退 `intervalSec`（秒）。
  3. 封面（`albumMid` 派生，见 #33）。
  4. 取链失败后的重试语义：IDLE/ENDED 会重新解析（`PlayerHost.toggle`），所以直链可重复获取。
- **证据**：`ui/player/PlayerScreen.kt:137-143,160,298-343`；`player/PlayerHost.kt:175-197,231-246`；`data/api/SongApi.kt:51-112`。

### 18. 歌词（逐字 QRC / 翻译 / 音译 / 注音） ✅（最挑在线层的一项）
- **需要在线层给什么**：
  1. **按 `songMid` 返回歌词**；`qrc:1` 时 `lyric` 是 **hex 密文**（同一字段在无逐字数据时才是 base64 LRC 明文）——App 按「全 hex 且偶数长度」判别后解密（`LyricApi.kt:82-115,150-158`）。
  2. **逐字时间**：QRC 里**字时间是绝对毫秒**（首字时间=行起点），解析时按「字时间是否超出该行时长」判别绝对/相对基准（`LyricApi.kt:355-413`）。若要维持卡拉 OK 扫色，必须给字级 `(起,时长)`。
  3. **翻译**：`trans` 为 base64 明文 LRC，与原文同时间轴；**无翻译的行用 `//` 占位**（必须识别该占位，否则会显示「//」，`LyricApi.kt:62,169-180`）；`qrc=1` 时 trans 常为空，App 会用 `qrc=0` 再请求一次补。
  4. **音译**：`roma:1` 时 `roma` 是 hex QRC 密文，解出逐字罗马音；**英文曲该字段为空**，空即视为无音译（`LyricApi.kt:108-110`）。
  5. **注音**：`trans` 里可带 `[kana:<字符数><读音>…]` 元数据，App 按「只有汉字消费 token」的规则铺到字上；没有该标签 = 无注音（`LyricApi.kt:196-266`）。
  6. **可用性开关**：三个开关（译/音/注）只在**该曲真有对应数据**时给（`Lyrics.hasTranslation/hasRoman/hasKana`），所以「为空」与「有数据」必须可区分。
- **证据**：`data/api/LyricApi.kt:60-158,169-191,196-209,355-413`；`data/Lyrics.kt:33-41`；`ui/player/PlayerScreen.kt:240-267`；`ui/player/LyricsView.kt:258-313,397-473`；`player/PlayerHost.kt:190-192`。

### 19. 播放队列 / 下一首播放 / 随机上一首 ✅（低要求）
- **需要在线层给什么**：只需要曲目对象具备**稳定的 `mid`**（队列去重、`playNext` 过滤、`indexOfFirst` 都靠它）与 `name`（队列弹窗只显示序号+歌名）。
- **证据**：`player/PlayerHost.kt:166-173,248-342`；`ui/common/TrackDialogs.kt:129-164`。

### 20. 音质 6 档 + 自动降级 ✅
- **需要在线层给什么**：
  1. 请求 `filename = {前缀}{mediaMid}{扩展名}` 时必须**前缀与扩展名成对**：`M500.mp3 / M800.mp3 / C400.m4a / O600.ogg / O800.ogg / F000.flac`（`Prefs.kt:11-17`）。
  2. 曲目要带**该曲真实存在的档位**（`file.size_*` > 0），App 先用它过滤请求链（`SongApi.kt:87-94`、`QqMapper.kt:39-48`）。
  3. **错误码语义必须保留**：`104003` = 该**档位**需要会员（**不能中断降级链**，继续试低档）；`101404` = 短时限流（隔几秒可重试）；`22` = 未登录。降级链走到最低档仍失败才报「无播放权限」。
- **证据**：`data/Prefs.kt:10-22`；`data/api/SongApi.kt:51-96,98-112`；`ui/common/TrackRow.kt:345-349`；`AGENTS.md:127,152-158`。

### 21. 查看格式弹窗 ✅
- **需要在线层给什么**：`file.*` 的 `size_<格式>` 大于 0 的项与字节数（`128mp3/320mp3/96aac/192ogg/320ogg/flac/ape…`），App 只列非零项并翻成人话（`QqMapper.kt:39-48`、`TrackDialogs.kt:232-265`）。没有格式数据时「查看格式」入口不出现（`TrackDialogs.kt:210`）。
- **证据**：`ui/common/TrackDialogs.kt:208-210,231-265`；`data/api/QqMapper.kt:39-48`。

### 22. 歌曲信息弹窗 ✅
- **需要在线层给什么**：`name/singer/albumName/intervalSec/mid/songId/isVip`；`songId>0` 才显示「数字 ID」行。
- **证据**：`ui/common/TrackDialogs.kt:215-229`。

### 23. 下载（目录 / 音质 / 已下载台账） ✅
- **需要在线层给什么**：
  1. **可直接流式 GET 的直链**（登录后含 VIP 曲目）；App 用独立 OkHttp（读超时 120s）拉流写 MediaStore。
  2. **扩展名与 MIME 的成对关系**（决定落盘文件名与 MIME）：`.mp3/.m4a/.ogg/.flac`（`Downloader.kt:34,81-87`）。
  3. 文件名用 `singer - name`，非法字符 App 自己替换。
  4. 下载失败要给「人话」原因（会员/限流/网络），由 `playUrl` 抛出的 message 直接 toast。
- **证据**：`data/Downloader.kt:20-88`；`data/DownloadStore.kt:9-63`；`ui/settings/SettingsScreen.kt:497-535`。

### 24. 智能调音（曲风 → 预设） ✅
- **需要在线层给什么**：曲目带 **`genre` 风格码（int）**。App 的码表是**实测样本校准**（1=流行 2=古典 22/50=摇滚 23=民谣 27=爵士 28=金属 33/20=电子 34=说唱），**0 或未知码 = 不动当前预设**（`SmartEq.kt:10-35,53-68`）。设置页允许用户把任意码改映射到任意预设（`EqualizerScreen.kt:316-334`）。
- **证据**：`player/SmartEq.kt:25-35,53-68`；`data/api/QqMapper.kt:61`；`player/PlayerHost.kt:193-196`；`ui/player/EqualizerScreen.kt:303-340`。

### 25. 登录 / 凭据 ✅
- **需要在线层给什么**：
  1. 网页登录后能从 cookie 读到 `uin`（去 `o` 前缀）+ `qm_keyst`（musickey）；`euin` 有则一并读。
  2. **`euin` 是收藏类读接口的硬前提**（缺它 `CgiGetDiss` 静默失败——表现为空列表而不是报错）；UI 会提示「缺 euin…建议重新登录」。
  3. 登录档 comm 才能做写操作；匿名档只能搜索/歌词/介绍。
  4. 播放直链、我喜欢写入、收藏读、关注歌手、昵称等都依赖登录态；QIMEI/guid 可自报随机值（无需真实置备）。
- **证据**：`ui/settings/SettingsScreen.kt:186-208,839-860`；`data/Prefs.kt:352-374,107-116`；`data/api/QqCore.kt:39-63`；`data/api/PlaylistApi.kt:24-28`；`AGENTS.md:159-165`。

### 26. 诊断日志 ✅（对在线层的间接要求）
- **需要在线层给什么**：失败时能拿到**可区分的业务码 + 文本**，供日志与 UI 分别处理（`1000` 写限流、`104003` 档位会员、`101404` 限流、`80105` 网页 comm 拒绝、`-1` 网络异常）。写操作失败**必须显式区分限流**，因为对策是「不自动重试、如实告知用户」（`TrackRow.kt:337-343`）。
- **证据**：`data/AppLog.kt:13-53`；`data/api/SongApi.kt:24-34,73,158`；`ui/common/TrackRow.kt:337-343`；`ui/settings/SettingsScreen.kt:591-659`；`AGENTS.md:129-134`。

### 27. 后台播放通知 / 锁屏 ✅
- **需要在线层给什么**：封面 URL（`track.coverUrl`）要能被**另一个线程直接 `java.net.URL(url).openStream()`** 取到（用于通知大图标）；取不到就只是没图，不阻塞播放。
- **证据**：`player/PlaybackService.kt:125-137`；`player/PlayerHost.kt:70-87,199-216`。

### 28. 音效页 / 光影 / 新拟物 UI / 全部转场 ⛔ 无在线依赖
- 均衡器（平台 `Equalizer`/`BassBoost`/`DynamicsProcessing`）、速度与音调、曲风映射的**存储**、光影时刻、页面栈/推拉/飞位转场均为本地能力；唯一与在线层相关的是曲风码本身（#24）。
- **证据**：`ui/player/EqualizerScreen.kt:70-400`；`player/EqualizerHost.kt:191-210`；`shade/DayLight.kt`（光影）；`ui/AppRoot.kt:108-595`。

---

## 2. 跨功能的共性约束（接入在线层时必须满足）

| 约束 | 说明 | 证据 |
|---|---|---|
| **图片一律 HTTPS** | 接口原始字段多为 `http://`，Android 默认禁明文 → 封面静默不显示。App 统一升级为 https（两个 CDN 都支持） | `data/api/QqCore.kt:124-131` |
| **封面可由 albumMid 派生** | `https://y.gtimg.cn/music/photo_new/T002R300x300M000{albumMid}.jpg`，所以「专辑 mid」是关键字段；歌单/专辑列表另有 `logo` | `data/Models.kt:20-23`；`data/api/SingerApi.kt:75` |
| **`mid` 是全局身份键** | 队列去重、列表缓存键、下载台账、电台去重、播放器 mediaId 全用它；同一 mid 需在多次请求间稳定 | `ui/home/Screens.kt:219,236,378`；`data/DownloadStore.kt:56`；`player/PlayerHost.kt:207-212` |
| **`songId`（数字 id）是喜欢能力的门票** | `songId<=0` 直接判「无法收藏」，连请求都不发；喜欢集合也是 songId 集合 | `data/api/SongApi.kt:143-145`；`ui/common/TrackRow.kt:319-323`；`data/LikedStore.kt:42,56-62` |
| **`euin` 缺失 = 收藏类静默空** | 需要在 UI 能区分「空」与「缺 euin」；App 在设置页显式提示重新登录 | `data/api/PlaylistApi.kt:24-28`；`ui/settings/SettingsScreen.kt:191-194` |
| **写操作限流语义** | `1000` 出现时读接口正常；对策是**不自动重试**并如实告知。返回体需同时给外层 `code` 与 `data.retCode`（只看一个会误判） | `data/api/SongApi.kt:152-159`；`ui/common/TrackRow.kt:337-343`；`AGENTS.md:129-134` |
| **请求节奏** | App 有全局 120ms 最小间隔闸与批间 300ms 停顿（对所有在线层调用生效）；电台取歌连续批、音质降级链、自动切歌都会连续发请求 | `data/api/QqCore.kt:72-90`；`data/api/RadioApi.kt:47-52` |
| **可合并的多请求** | 一次 `musicu.fcg` 可带多个 `req_N` 块（歌手专辑歌数批量补齐就靠它）；接入的新在线层若不支持，请求数会线性膨胀 | `data/api/QqCore.kt:65-107`；`data/api/SingerApi.kt:83-102` |
| **空 vs 失败的区分** | 多个列表页把「空数组」当「到底了/没有结果」，把「异常」当可重试失败。在线层对限流/风控**不能回空列表** | `data/api/RadioApi.kt:58-62`；`ui/home/Screens.kt:527-540`；`ui/search/SearchScreen.kt:185` |
| **分页要支持大 offset** | 我喜欢/歌单/专辑都是「翻页取全」（100/页，最多 50 页），total 要准或至少给「短页=结束」的信号 | `ui/home/Screens.kt:205-247`；`data/LikedStore.kt:36-48` |
| **HTTPS 直链可被播放器/下载器/系统通知线程直接 GET** | 无额外鉴权头、无签名时效过短问题（重试/下载/通知解码都要复取） | `data/api/SongApi.kt:98-112`；`data/Downloader.kt:37`；`player/PlaybackService.kt:129-132` |

---

## 3. 明确**不**依赖在线层的能力（接入时可原样保留）

- 播放模式（顺序/随机/单曲循环）、队列与「下一首播放」、随机上一首历史（`player/PlayerHost.kt:248-342`）。
- 均衡器/DVC/声道平衡/速度与音调/自建预设（`player/EqualizerHost.kt`、`DynamicsFxHost.kt`、`ui/player/EqualizerScreen.kt`）。
- 播放条可视化（系统 Visualizer / 自研 PCM 透传，`player/VizHost.kt`、`VizProcessor.kt`）。
- 黑胶模式、光影随时间变化、深色模式/强调色/立体感、全部新拟物控件与页面转场（`shade/`、`ui/AppRoot.kt`）。
- 搜索历史、诊断日志开关/导出、下载目录选择（本地存储侧）。
- 本地「已下载」台账与置灰（`data/DownloadStore.kt`）。

---

## 4. 盘点中发现的不一致 / 待确认（如实记录，未擅自改）

1. **昵称未被消费**：AGENTS.md:21 的「已登录随机带昵称 / 未登录请登录」与现有代码不符；`HomeScreen.kt:103,215-217` 明确不带用户名，且 `NicknameCache.get()` 全工程无调用点。昵称接口（`UserApi.nickname`）目前是「拉了但没人用」。
2. **AGENTS.md 1.5 与代码的视觉参数有出入**：文档写推荐卡封面 132dp，代码是 `cover = 158.dp`（`HomeScreen.kt:638`）。这属于 UI 参数、与在线层无关，仅记录。
3. **AGENTS.md 1.5 描述推荐卡为「左封面 + 右歌名/介绍 + 竖排三凸起钮」，代码是两列两行（左列封面+歌名/歌手，右列介绍槽+三钮横排）**（`HomeScreen.kt:628-724`）。同上，非在线层需求。
4. **`ShadeFusedTabs` 退役但仍被文档提及**（AGENTS.md:25,38）——代码里搜索页已改平的分段控制（`SearchScreen.kt:327-357`），电台页早已删除。属文档滞后，不影响在线层盘点。

---

## 5. 一句话结论

消费侧真正在线层要的就是七类东西：**①曲目全套字段（mid/songId/mediaMid/albumMid/singer/intervalSec/isVip/fileSizes/genre）**、**②四类可分页列表（收藏歌单/专辑、歌单/专辑内歌曲、搜索四类、歌手歌曲/专辑）**、**③电台「每次给新歌」的无限流行为与分组标识**、**④按 songMid 的歌词四路数据（逐字绝对时间 / 翻译 `//` 占位 / 音译 / `[kana:]`）与显式空语义**、**⑤按档位的播放直链与错误码语义（104003 档位级、101404 限流）**、**⑥写喜欢的成败语义（含 1000 限流、不自动重试）**、**⑦登录凭据三件套（uin/musickey/euin，euin 决定收藏可用性）**；外加三条工程约束：图片强制 HTTPS、失败与空结果必须可区分、请求要能被节流/合并。
