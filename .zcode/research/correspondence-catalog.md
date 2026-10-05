# 目录与搜索域：工程能力 ↔ 组件方法 对应核对
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

> 只读核对。未改动任何工程文件（本文件除外）、未运行 gradle、未连 adb、未发起任何网络请求。
> **组件侧路径**均相对 `/tmp/helpernext_probe`（git 2ff7e71），**工程侧路径**均相对
> `/Users/mac/Documents/Music_app/app/src/main/java/com/neumusic/player/`。
>
> 本域范围：搜索（歌曲/歌手/专辑/歌单，含分页）、收藏的歌单、收藏的专辑、歌单/专辑内曲目、
> 歌手（解析/歌曲/专辑/资料/计数）、关注歌手、电台（分组与曲目）、猜你喜欢/推荐流、昵称。
> 写操作（`set_liked`）、歌词、取流、下载引擎不在本域，只在"额外能力"里点到。
>
> 本次实际跑过的命令（全部只读）：
> - `wc -l /tmp/helpernext_probe/src/*.rs`
> - `python3 -c "json.load(open('/tmp/helpernext-workflow-cred/probe-output.json'))"`（逐条打印 17 行实测）
> - `diff <(python3 -m json.tool /tmp/helpernext-research/probe-output.json) <(python3 -m json.tool /tmp/helpernext-workflow-cred/probe-output.json)`
>   → 唯一差异是 `fetch_new_songs.count`（69 vs 52），其余逐字相同
> - 工程侧多轮 `grep -n` / `sed -n` 读取（行号见下表）
> - `md5` 两个 probe-output.json（不同），`cat` 两份探针脚本以确认给定的那份由哪个脚本产生
>
> 下面每条 verdict 都注明"证据来自源码阅读"还是"另有实测"。

---

## 0. 先看三条全局结论（它们决定很多单条的 verdict）

1. **组件的"曲目"形状比工程需要的窄。**
   组件 `decode_track`（`src/methods.rs:889-933`）产出的字段是
   `songId/songMid/mediaMid/title/artist/album/albumMid/albumId/imageURL/duration/payPlay/singerMid/singers`
   （模型见 `src/models.rs:131-155`）。**没有 `file.size_*`、没有 `genre`**，
   而这两样在工程里是硬需求：
   - `fileSizes` → 音质档位过滤（`data/api/SongApi.kt:57-58`、`:87-94`）与「查看格式」弹窗
     （`ui/common/TrackDialogs.kt:208-210`、`:231-265`）；
   - `genre` → 智能调音（`data/api/QqMapper.kt:61`、`player/SmartEq.kt:50-63`）。
   这两项在组件里是**结构性拿不到**（不是参数问题），所以凡涉及"完整曲目"的行都是 partial 或 gap。

2. **`mediaMid` 在组件里几乎恒为 null（源码级 bug）。**
   `src/methods.rs:921`：
   `"mediaMid": first_int(track, &["media_mid"]).map(|_| ()).and(first_text(track, &["media_mid", "mediaMid"]))`
   —— `Option::and` 要求 `first_int(media_mid)` 为 `Some` 才会返回文本；
   真实 `media_mid` 是 `"0039MnYb0qxYhV"` 这类字符串，`first_int` 解析失败 → 结果恒为 `None`。
   **实测印证**：给定探针里 `fetch_liked_songs.detail.firstHasMediaMid = false`
   （`/tmp/helpernext-workflow-cred/probe-output.json`）。组件自己的取流会兜底
   （`src/catalog.rs:1066-1069`：无 mediaMid 时用 `{prefix}{songMid}{songMid}{ext}`），
   但宿主若沿用工程 `Track.mediaMid`（`data/api/QqMapper.kt:35` 兜底为 mid）需要自己补同一条兜底。

3. **FFI（typed）面在本域有一半方法不可用；stdio 面完整。**
   `src/api.rs:41-53` 只解包 7 个键（`likedSongs`/`login`/`helper`/`playlists`/`albums`/`artists`/`tracks`）。
   我按 `parse()` + `models.rs` 自行推演（与 `helpernext-inventory.md` §4.7 的实测清单一致）：
   - **typed 可用**：`liked_songs`、`playlist_tracks`、`user_playlists`、`liked_albums`、
     `followed_artists`、`album_tracks`（载荷 `{tracks,total}` 恰好等于 `TrackPage`）、
     `search_songs/artists/albums/playlists`（载荷 `{结果,total}` 恰好等于四个 `*Search` 模型）；
   - **typed 不可用（本域内）**：`artist_songs`、`artist_albums`、`artist_detail`、
     `radio_stations`、`radio_tracks`、`recommend_feed`、`song_detail`、`album_detail`（静默全 None）、
     `fetch_artist_biography`（静默全 None）、`search_*_artwork`。
   宿主若走 FFI，这 8+ 条必须改用 stdio 子进程或 `call_with_platform`
   （`src/api.rs:378-394`，实测可用）。

---

## 1. 逐条对应

格式：**工程能力** ← `组件方法`（组件证据）｜**verdict**
工程证据 / 差异与宿主需要补什么。

### 1.1 搜索域

| # | 工程能力 | 组件方法 | verdict |
|---|---|---|---|
| 1 | 搜索·歌曲（含分页） | `search_songs` | **partial** |
| 2 | 搜索·歌手 | `search_artists` | **covered** |
| 3 | 搜索·专辑 | `search_albums` | **partial** |
| 4 | 搜索·歌单 | `search_playlists` | **covered** |
| 5 | 曲目字段合集（fileSizes / genre / mediaMid） | `decode_track`（全方法共用） | **gap** |

**1）搜索·歌曲（含分页）** — `search_songs`（`src/methods.rs:305-321`、`src/catalog.rs:1361-1420`）｜**partial**

- 工程证据：`data/api/SearchApi.kt:36-42`（`songs(q, num=30, page=1)`，`body.item_song[]` 兜底
  `body.song.list[]`）；分页合同 `ui/search/SearchScreen.kt:138-171`（`pageByTab`/`appendMore`）、
  触发点 `:214-221`（滚到底部前 3 项）、空结果语义 `:185`（"没有找到相关歌曲"）。
- 组件证据：结果取 `body.item_song`（`src/catalog.rs:1395`）→ `decoded_tracks`
  （`src/methods.rs:871-887`，候选键含 `songlist/songList/songs/list/tracks/songInfoList`）；
  总数 `meta.sum`（`src/catalog.rs:1409-1411`）；标题 `<em>` 已剥（`src/catalog.rs:1468-1470`）。
- **可得**：`songMid/title/artist/album/albumMid/albumId/imageURL/duration/payPlay/singers`，
  以及 `total`（工程的 `exhausted` 目前只能靠"本页 0 条"推断，`SearchScreen.kt:164-166`；
  换成组件后可以改用 total，判断更准）。
- **差异**：
  a. **无 `fileSizes`/`genre`**（见全局结论 1）→ 搜索结果里的"查看格式"入口会消失
     （`TrackDialogs.kt:210` 以 `fileSizes.isNotEmpty()` 为门槛），智能调音对该曲失效。
  b. **`mediaMid` 缺**（见全局结论 2）。
  c. 档案被**强制 android**（`src/methods.rs:104-118`），需要设备身份 + 设备会话（`src/upstream.rs:333-347`、
     `:398-444`）；工程现用匿名 `commWeb`（`data/api/QqCore.kt:40`）+ 自报 QIMEI（`QqCore.kt:54-58`），
     同一查询在 web 档案下 `meta.sum=0`（`helpernext-inventory.md` §4.1.2 实测）——组件内部已处理，
     这是"换调用即可"的部分。
  d. 默认 `limit=20`、上限 50（`src/methods.rs:319`）；工程默认 30 → 必须显式传 `limit:30`。
  e. 空串关键词组件直接回空不发请求（`src/catalog.rs:1371-1373`），与工程一致。
- **宿主需要补**：`limit:30`；把 `fileSizes/genre/mediaMid` 从潮流中降级（或让组件在 `decode_track` 里补
  这三项——上游响应里本来就有 `file.size_*`/`genre`/`media_mid`，只是被丢弃）。

**2）搜索·歌手** — `search_artists`（`src/methods.rs:305-321` → `src/catalog.rs:1430-1444`）｜**covered**

- 工程证据：`data/api/SearchApi.kt:45-60`（`body.singer[]` → `SearchSinger(mid,id,name,pic,songNum,albumNum)`）；
  消费点 `ui/search/SearchScreen.kt:631-655`（卡第二行用 songNum/albumNum，`ui/common/Cards.kt:234-238`）。
- 组件证据：`singerMid/name/coverURL/songCount/albumCount/fanCount`，名字已剥 `<em>`，
  封面已 https 化（`src/methods.rs:987-996`）。
- 差异：工程的 `SearchSinger.id`（`SearchApi.kt:53`，来源 `singerID`）组件不返回；
  **全工程没有任何地方读这个字段**（grep `\.id` 在 search/singer/AppRoot 只命中电台 id），所以不构成缺口。
  `fanCount` 是白送的。另外 `SingerApi.resolve` 的"精确名优先"逻辑（`data/api/SingerApi.kt:29-32`）
  要留在宿主侧（组件不做名字匹配，只回搜索列表；组件内部的 name→mid 解析用 limit=1，
  见 `src/catalog.rs:590-614`）。

**3）搜索·专辑** — `search_albums`（`src/catalog.rs:1446-1463`）｜**partial**

- 工程证据：`data/api/SearchApi.kt:63-76`（`albummid/name/pic/song_num/singer` → `AlbumItem(mid,name,logo,songnum,singerName)`）；
  展示 `ui/search/SearchScreen.kt:660-680` → `MediaCard(count = a.songnum)`（`ui/common/Cards.kt:158-161`，`count<=0` 不显示）。
- 组件证据：`id/title/albumMid/coverURL/artist/releaseDate`（`src/catalog.rs:1449-1462`）。
- **差异**：**没有 `song_num`**（`map_album` 未映射）→ 专辑卡右侧「N 首」会消失（不是显示错值，是不显示）。
  封面优先用上游 `pic`、否则按 `T002R800x800M000{mid}.jpg` 拼（`:1455-1459`），都是 https，可用。

**4）搜索·歌单** — `search_playlists`（`src/catalog.rs:1472-1489`）｜**covered**

- 工程证据：`data/api/SearchApi.kt:79-91`（`dissid/dissname/logo/songnum` → `PlaylistItem(tid,name,logo,songnum)`）；
  展示 `ui/search/SearchScreen.kt:681-687`。
- 组件证据：`id`（源 `dissid`）/`title`/`coverURL`（源 `logo`，`:1482` 明确注明本路封面在 `logo` 键）/
  `creator`/`songCount`/`playCount` —— 一一对上，且 `id` 可直接当 `fetch_playlist_tracks` 的 `songlistId`。

**5）曲目字段合集（fileSizes / genre / mediaMid）** — `decode_track`｜**gap**

- 工程证据：`data/Models.kt:16-18`（`fileSizes`/`genre` 是 `Track` 的字段）、`QqMapper.kt:39-48`（`size_*`）、
  `:61`（`genre`）、`:35`（mediaMid 兜底）、`SongApi.kt:87-94`、`TrackDialogs.kt:231-265`、`SmartEq.kt:50-63`。
- 组件证据：`src/methods.rs:917-932`（映射里根本没有 `file`/`genre`）；模型 `src/models.rs:131-155` 也没有。
- 结论：**这三项拿不到**。上游响应里有，组件丢了。宿主若接组件，要么放弃「查看格式」/智能调音/按档位过滤，
  要么改组件 `decode_track`（改动很小：`file.size_*` → 大小表、`genre` 原样、修 `mediaMid` 的 `first_int` bug）。
  注意这不影响播放本身（组件的 `resolve_song_url` 自带降级链，见其 `src/catalog.rs:1041-1122`），
  但工程的 `SongApi.playUrl` 是以 `fileSizes` 为前置过滤的。

### 1.2 我喜欢 / 收藏列表

| # | 工程能力 | 组件方法 | verdict |
|---|---|---|---|
| 6 | 我喜欢列表（分页 + 总数） | `fetch_liked_songs` | **partial** |
| 7 | 我喜欢总数（主页卡片） | `fetch_liked_songs` | **covered** |
| 8 | 收藏的歌单 | `fetch_user_playlists` | **partial** |
| 9 | 收藏的专辑 | `fetch_liked_albums` | **partial** |
| 10 | 歌单内曲目 | `fetch_playlist_tracks` | **partial** |
| 11 | 专辑内曲目 | `fetch_album_tracks` | **partial** |

**6）我喜欢列表** — `fetch_liked_songs`（`src/methods.rs:661-693`）｜**partial**

- 工程证据：`data/api/PlaylistApi.kt:31-43`（`likedPage(offset,num)`，`dirid=201` + `enc_host_uin=euin`，
  曲目 `songlist[]`、总数 `dirinfo.songnum`）；分页消费者 `ui/home/Screens.kt:217-247`（`pageSize=100`、`guard<50`）、
  `data/LikedStore.kt:36-48`（**每页 300** 翻全表）。
- 组件证据：`page`（≥1）+ `limit`（clamp 1–100，默认 50），内部 `dirid:201, disstid:0, song_begin, song_num`
  （`:675-683`），总数 `dirinfo.songnum`（`:690`）；需登录（`:666`）。
- **差异**：① 组件是**页号**语义（`page`），工程是 **offset** 语义，且工程单页要 300（`LikedStore.kt:41`）
  而组件上限 100 → 宿主必须改成"page 循环、每页 100"。② 组件**不需要 euin**（用登录档自己的账号），
  工程 `requireEuin()`（`PlaylistApi.kt:24-28`）在缺 euin 时直接抛/静默空（`LikedStore.kt:32`）——
  这是**减依赖**，属收益。③ 曲目字段缺口同 §1.1 行 5。
- 实测：`/tmp/helpernext-workflow-cred/probe-output.json` → `fetch_liked_songs: {total:479, got:3, firstHasMid:true}`。

**7）我喜欢总数（主页卡片）** — `fetch_liked_songs`｜**covered**

- 工程证据：`ui/home/HomeScreen.kt:236-241`（`PlaylistApi.likedPage(0, 1).total`）、卡片 `:490-521`/`:508`（`—` 兜底）。
- 组件证据：`total`（`src/methods.rs:690`）。用 `page:1, limit:1` 即可（limit clamp 下限 1）。
- 差异：无。`total` 是 `i64` 且总有值（上游不给时退化为本页条数，`:690` —— 与工程的
  "`total=null` 表示没给" 语义不同，宿主若依赖"null 显示 —"需要自己判断）。

**8）收藏的歌单** — `fetch_user_playlists`（`src/methods.rs:744-773`）｜**partial**

- 工程证据：`data/api/PlaylistApi.kt:63-70`（`PlaylistFavRead/CgiGetPlaylistFavInfo`，`uin=euin`，结果 `v_list[]`）；
  消费 `ui/home/HomeScreen.kt:189`、`ui/home/Screens.kt:614`；卡片特判 `HomeScreen.kt:527`（`tid==201L || name=="我喜欢"`）。
- 组件证据：走**老 fcgi** `c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg`，`reqtype=3`
  （`src/upstream.rs:273-299`）；字段 `source/id(源 dissid,tid,id)/title/coverURL(源 logo,picurl)/creator/songCount/playCount`
  （`src/methods.rs:762-770`）；`id<=0` 的保留目录被跳过（`:756-761`）。
- **差异**：
  a. **端点不同**：工程是 `musicasset.PlaylistFavRead`（`v_list`），组件是老 fcgi。两者在上游都是
     "我的收藏-歌单"这一页，但**我没有办法在不发网络请求的前提下证明两者对同一账号返回同一集合**
     （工程侧的收藏歌单条数没有留档可比）。→ 这条的"等价"**未验证**，是本次最大的不确定点。
  b. **没有 offset 分页**：组件只读 `limit`（1–100，默认 100），内部 `sin=0&ein=limit` 恒从头取
     （`src/upstream.rs:283-285`），**传 `offset`/`page` 无效**。工程 `favPlaylists(page,num)` 支持翻页，
     但目前 UI 只取第 1 页（`HomeScreen.kt:189`、`Screens.kt:614` 都用默认参数）→ 当前够用，
     收藏数 >100 时无法翻全。
  c. 保留目录（我喜欢）被组件跳过 —— 工程主页的「我喜欢」是独立卡片（`HomeScreen.kt:306-308`），
     不依赖这一项，所以不构成缺口；但 `PlaylistCard` 的特判（`:527`）会失去触发场景。
  d. 曲目字段缺口不涉及本行（歌单列表只给计数）。
- 实测：`probe-output.json` → `fetch_user_playlists: {count:4, numericId:true, firstSongCount:241}`。

**9）收藏的专辑** — `fetch_liked_albums`（`src/methods.rs:776-801`）｜**partial**

- 工程证据：`data/api/PlaylistApi.kt:73-80`（`AlbumFavRead/CgiGetAlbumFavInfo`，`euin`，`v_list[]` → `AlbumItem(mid,name,logo,songnum)`）；
  消费 `HomeScreen.kt:197`、`Screens.kt:651`、卡片 `HomeScreen.kt:590`（`"${item.songnum} 首"`）、
  列表页副标题 `Screens.kt:668`。
- 组件证据：老 fcgi `reqtype=2`（`src/methods.rs:783`）；字段 `id/title/albumMid/coverURL/artist/releaseDate`
  （`:790-798`）；`releaseDate` 把 `pubtime`（北京时间零点）按 +08:00 转成 `YYYY-MM-DD`（`:935-954`）。
- **差异**：**没有 `songnum`** → 主页卡片与列表页会显示「0 首」（`HomeScreen.kt:590` 不做 0 判断；
  `MediaCard` 才判断且这里不用）——比搜索结果那行更难看，需要宿主补或组件补。
  `artist` 组件给了（工程的 `AlbumItem.singerName` 注释说"收藏列表接口不给"，`Models.kt:42`）→ 是白送的。
- 实测：`{count:4, firstHasMid:true, numericId:true}`。

**10）歌单内曲目** — `fetch_playlist_tracks`（`src/methods.rs:701-741`）｜**partial**

- 工程证据：`data/api/PlaylistApi.kt:47-59`（`disstid=tid, dirid=0`，offset/num，`songlist[]`，`dirinfo.songnum`）；
  消费 `ui/AppRoot.kt:635`（`Nav.PlaylistDetail`）。
- 组件证据：`songlistId`（别名 `disstid/id/topId`），`offset`（别名 `song_begin`）优先、否则由 `page` 换算
  （`:714-717`，注释明确"只读 offset 是每一页都回第一页的原因"），`limit` clamp 1–200 默认 100；
  总数 `total` 可为 `null`（`:737-740`）。
- **差异**：语义上是**直接替代**（工程用 offset，组件正好支持 offset），只剩曲目字段缺口（§1.1 行 5）。
  另：组件把 `total` 做成 `Option`，与工程 `Page.total: Int?` 一致。
- **未实测**：探针没有调用 `fetch_playlist_tracks`（见 §3），结论来自源码。

**11）专辑内曲目** — `fetch_album_tracks`（`src/methods.rs:154-163` → `src/catalog.rs:415-451`）｜**partial**

- 工程证据：`data/api/PlaylistApi.kt:86-97`（驼峰 `albumMid`/`begin`/`num`，`songList[].songInfo`，`totalNum`，
  播客类 `totalNum=0` 属正常）；消费 `ui/AppRoot.kt:642`。
- 组件证据：参数 `albumMid` 或 `albumId` + `begin`/`num`/`order:0`（`src/catalog.rs:424-436`），
  列表靠 `decoded_tracks` 吃 `songList`/`songInfo`（`src/methods.rs:881`、`:890`），总数 `totalNum`（`:449`）。
- **差异**：端点与参数**逐字一致**（`albumMid` 驼峰、`songInfo` 包层、`totalNum`），只剩曲目字段缺口。
  组件的 `limit` 默认 200，工程默认 100 → 传参即可。
- 实测：`probe-output.json` → `fetch_album_tracks: {total:1, got:1}`。

### 1.3 歌手

| # | 工程能力 | 组件方法 | verdict |
|---|---|---|---|
| 12 | 歌手解析（名字 → mid/pic/计数） | `search_artists` | **covered** |
| 13 | 歌手歌曲（分页/排序/总数） | `fetch_artist_songs` | **partial** |
| 14 | 歌手专辑（分页/总数/每张歌数） | `fetch_artist_albums` | **partial** |
| 15 | 歌手资料与计数 | `fetch_artist_detail` | **partial** |

**12）歌手解析** — `search_artists`（`src/catalog.rs:1430-1444`）｜**covered**

- 工程证据：`data/api/SingerApi.kt:29-32`（复用 `SearchApi.singers(name, num=10)`，精确名优先）；
  调用点 `ui/common/PlayerBar.kt:152`（点播放栏歌手名）、`ui/singer/SingerScreen.kt:110-120`（计数为 0 时按名字补齐）。
- 组件证据：同 §1.1 行 2；组件内部的 name→mid 解析也用同一个搜索（`src/catalog.rs:590-614`）。
- 差异：无（宿主保留"精确名优先"的两行逻辑即可）。计数来自 `songCount/albumCount`。

**13）歌手歌曲** — `fetch_artist_songs`（`src/methods.rs:164-173` → `src/catalog.rs:459-504`）｜**partial**

- 工程证据：`data/api/SingerApi.kt:35-47`（`order=1 热门 / 2 最新`、`number/begin`、`songList[].songInfo`、
  `totalNum`）；消费 `ui/singer/SingerScreen.kt:141`（首屏）、`:167`（loadMore，用 `st.songs.size` 当 offset）、
  底部文案 `:334-338`（`已加载 N / 共 T`）。
- 组件证据：`singerMid` + `sort`（`hot`/`latest`）+ `page` + `limit`（1–100 默认 50），
  内部 `order:1`（**恒为热门**，`:477`）；`latest` 由组件**本地按 `releaseDate` 排序**（`:487-502`）。
- **差异（三条，都要宿主处理）**：
  a. **没有 total**：`fetch_artist_songs` 只回 `{tracks}`（`src/methods.rs:173`），`catalog::artist_songs`
     返回 `Vec<Value>`（`src/catalog.rs:467`）。工程的 `totalNum` 丢失 → 底部只能显示「已加载 N 首」，
     分页仍能继续（`SingerScreen.kt:166` 的 `songsTotal<=0` 分支会一直允许 loadMore），但"共 T 首"消失。
  b. **页号 vs offset**：组件只认 `page`（`:479` `begin=(page-1)*limit`），工程按 `st.songs.size` 递 offset；
     limit 上限 100 与工程每页 100 一致 → offset→page 换算可得，但要小心 offset 不是 100 的整数倍的情形。
  c. **`latest` 是"页内排序"**：上游忽略排序参数（`:455-457` 注释），组件只把**当页**按 releaseDate
     降序排（`:498-501`）。跨页顺序因此不是全序 —— 工程现在的 `order=2` 是服务端全序。
     想要真"最新"，宿主得把所有页取回来自己排，或接受页内有序。
  d. 曲目字段缺口（§1.1 行 5）。`releaseDate` 每首会附带（`:494`）。
- **未实测**：探针未调用本方法。

**14）歌手专辑** — `fetch_artist_albums`（`src/methods.rs:174-183` → `src/catalog.rs:507-559`）｜**partial**

- 工程证据：`data/api/SingerApi.kt:52-81`（`GetAlbumList`，`singerMid/order/number/begin`，`albumList[]`，
  总数 `total`，logo 本地拼 `T002R300x300M000{mid}`）+ **`fillSongCounts`**（`:84-102`：每 30 张合并成
  一次 `musicu.fcg` 多请求块查 `totalNum`，最多 3 批）；消费 `SingerScreen.kt:147`、`:176`、
  专辑卡右侧「N 首」（`:284-296`）。
- 组件证据：`singerMid/sort/page/limit`（1–100 默认 50），内部 `order:1`；字段
  `id(源 albumID)/title/albumMid/coverURL(拼 T002R800x800M000)/artist/releaseDate`（`:536-549`），
  `latest` 同样**只对当页排序**（`:552-557`）。
- **差异**：
  a. **没有 total**（返回的是 `Vec<Album>`，`src/catalog.rs:507-515`，`fetch_artist_albums` 载荷
     只有 `{albums}`，`src/methods.rs:183`）→ 底部"共 T 张"消失。
  b. **没有每张专辑的歌数**，而且**组件不提供多请求块合并**：`Upstream::envelope` 支持 `Vec<Call>`
     但公开的 `call_with` 只传 `vec![call]`（`src/upstream.rs:240`、`:348-355`），
     所以工程那条"30 张专辑一次 HTTP 补齐歌数"（`SingerApi.kt:84-102`）在组件上没有等价物 ——
     要补就得发 N 次 `fetch_album_tracks`（请求数 ×30，与工程"降低请求频次"的纪律相反）。
     **这是本域最实质的能力退化之一。**
  c. 封面尺寸不同（组件 800×800、工程 300×300），都 https，无影响。
- **未实测**：探针未调用本方法。

**15）歌手资料与计数** — `fetch_artist_detail`（`src/methods.rs:184-193` → `src/catalog.rs:574-690`）｜**partial**

- 工程证据：歌手页头两行「N 首歌 · N 张专辑」（`ui/singer/SingerScreen.kt:247-259`），计数来自搜索解析
  （`SingerApi.resolve`）。工程**没有**歌手简介/详情展示。
- 组件证据：`GetHomepageHeader`（`SingerMid`），产出
  `artistName/singerMid/imageURL/coverURL/description/genreTags/region/foreignName/songCount/albumCount/fanCount/followCount`
  （`:666-689`），封面可回退到 `T001R300x300M000{mid}`（`:642-652`）。
- **差异**：
  a. **强制 android 档案**（`src/methods.rs:104-118`）+ 设备会话；并**依赖上游 header 不返回空壳**：
     空壳时报「与搜索、推荐同因：可能缺设备标识」（`src/catalog.rs:636-641`）而不是回"未知歌手"。
  b. **typed FFI 的 `artist_detail` 不可用**（`src/api.rs:274-276` 只有 `singerMid`；`parse` 不解 `detail`
     → 反序列化失败，`src/api.rs:41-53`）；stdio 面可用。`fetch_artist_biography` 在 FFI 上更糟：
     反序列化"成功"但字段全 `None`（`helpernext-inventory.md` §4.7 实测）。
  c. 上游对多数歌手没有简介文字（`docs/endpoints.md` 第五节），`description` 空是正常答案。
  d. 计数字段与搜索同源，可作为「关注列表进来后补齐计数」的第二条路（工程现在用搜索，`:110-120`）。
- **未实测**：探针未调用本方法。

### 1.4 关注歌手 / 电台 / 推荐 / 昵称

| # | 工程能力 | 组件方法 | verdict |
|---|---|---|---|
| 16 | 关注的歌手 | `fetch_followed_artists` | **partial** |
| 17 | 电台分组 | `fetch_radio_stations` | **covered** |
| 18 | 电台曲目（无限流） | `fetch_radio_tracks` | **partial** |
| 19 | 猜你喜欢 / 推荐流 | `fetch_recommend_feed` | **partial** |
| 20 | 昵称 | `get_login_status` | **covered** |

**16）关注的歌手** — `fetch_followed_artists`（`src/methods.rs:804-855`）｜**partial**

- 工程证据：`data/api/UserApi.kt:19-43`（`GetFollowSingerList`，`HostUin=euin`，`List[*]` 的
  `MID/Name/AvatarUrl/Desc` + `Total`；**euin 空则不发请求**，`:20-21`）；消费 `HomeScreen.kt:224-231`、
  卡片第二行用 `Desc`（`HomeScreen.kt:774-780`）。
- 组件证据：`page`/`limit`（1–100 默认 30）；`HostUin` **encrypt_uin 优先、没有就用数字 uin**
  （`:816-818`，注释说实测同结果）；`List`（大写 L）键已处理（`:838-840`）；
  字段 `singerMid/name/coverURL(源 AvatarUrl)/fanCount`（`:846-852`）；需登录（`:809`）。
- **差异**：
  a. **`Desc` 不返回**（只给 `fanCount`）→ 工程卡片第二行的服务端文案（`HomeScreen.kt:774-780`）需要
     宿主用 `fanCount` 自己拼，或该行留空。属"要宿主补东西"。
  b. **没有 `Total`**（返回 `Vec`，`src/methods.rs:453`）→ 工程 `followSingers` 返回的 total 只在
     `HomeScreen.kt:227` 用了 `.first`，**当前无人消费**，影响为零；将来要"共 N 位"就没有。
  c. **euin 依赖解除**（组件用数字 uin 兜底）→ 工程那条"缺 euin 就返回空列表"（`UserApi.kt:21`）可以去掉，
     属收益。
  d. 曲目字段无关。
- 实测：`{count:3, firstHasSingerMid:true, firstHasName:true}`。

**17）电台分组** — `fetch_radio_stations`（`src/methods.rs:205-206` → `src/catalog.rs:794-846`）｜**covered**

- 工程证据：`data/api/RadioApi.kt:16-20`（`commRadio` = `{ct:24,cv:0}`，`radio_list[].list[]`）；
  映射 `data/api/QqMapper.kt:113-124`（外层分组、内层电台，`id/title/listenDesc/pic_url`）；
  消费 `HomeScreen.kt:206`、`:370-378`；**分组 id 不能当电台 id**（`QqMapper.kt:109-112`）。
- 组件证据：`pf.radiosvr/GetRadiolist`，param `{uin}`（无凭据用 `"0"`，`:799`），web 默认档案；
  `radioGroups[]` → `{id,name,title,source,stations[{id,title,coverURL,listenerCount,source}]}`（`:813-845`）；
  分组与电台的层级保持正确（`:838` 取的是 item 的 `id`）。
- **差异**：组件**不返回 `listenDesc`**；工程读它但**从不显示**（grep：只出现在 Models/QqMapper/HomeCache），
  无影响。组件多给 `listenerCount`。另外工程用 `commRadio` 而组件走默认 web 档案 + `uin` 参数，
  实测同账号能回 11 组 134 个电台（`probe-output.json`），所以档案差异已由组件内部消化。
- **注意**：工程靠**标题字符串 `"猜你喜欢"`** 在分组里找 station（`HomeScreen.kt:163-165`、`:220-222`、`:248-251`）；
  组件的 `title` 原样来自上游（只走 `first_text`，不加工），字符串可继续匹配。

**18）电台曲目（无限流）** — `fetch_radio_tracks`（`src/methods.rs:207-215` → `src/catalog.rs:850-873`）｜**partial**

- 工程证据：`data/api/RadioApi.kt:45-63`（`nextTracks`：`mb_track_radio_svr/get_radio_track`，
  每批只带 `{id, firstplay}`、批间 `delay(300)`、按 mid 去重、全批失败**抛异常**——`:59-61` 明确
  "不能当空表"）；消费 `ui/AppRoot.kt:645-666`（无限流列表）、`HomeScreen.kt:165`、
  `data/RecommendStore.kt:81`。
- 组件证据：**上游方法不同** —— `pf.radiosvr/GetRadiosonglist`，param `{id, firstplay, num}`
  （`:864-869`），`limit` clamp 1–50 默认 20，返回 `tracks`（无总数）；
  注释宣称"同一请求每次回一批新轮换"（`:848-849`）且 `docs/endpoints.md` 写"电台是无穷列表"。
- **差异**：
  a. **换了上游方法**（`GetRadiosonglist` vs `get_radio_track`），是否同样"每次不同、每批约 5 首"
     **本次未验证**（探针没跑这条，见 §3）。工程的无限流（`AppRoot.kt:645-666`）与去重完全依赖这个行为。
  b. 组件**没有排除参数**（不能传 `exclude`/`from`），去重与分批仍由宿主做（工程已有，`RadioApi.kt:45-58`）。
  c. 组件 `firstPlay` 有（默认 true）→ 首屏/续批区分可保留。
  d. "全批失败必须可区分"：组件对上游拒答码（2000/2001/1000/104401/104400）**一律报错**
     （`src/upstream.rs:245-252`）→ 宿主仍能按"异常=可重试、空表=到底"处理。
  e. 曲目字段缺口（§1.1 行 5），但电台曲目在工程里也不过 `fileSizes`（`QqMapper` 对扁平电台结构同样收 `file`）。

**19）猜你喜欢 / 推荐流** — `fetch_recommend_feed`（`src/catalog.rs:900-916`）｜**partial**

- 工程证据：`ui/home/HomeScreen.kt:150-175`（用推荐歌 + **「猜你喜欢」电台** 的 `nextTracks` 拼队列）、
  `data/RecommendStore.kt:71-119`（预缓冲 5 首 + `excluded` 去重 + `SongApi.intro` 取介绍，`:95`）；
  介绍端点 `data/api/SongApi.kt:119-133`。
- 组件证据：`music.radioProxy.MbTrackRadioSvr/get_radio_track`，param **写死** `{id:99, num:5, from:0, scene:0, song_ids:[]}`
  （`:912`）；需登录 + 强制 android（`src/methods.rs:104-118`）。
- **差异**：
  a. **组件直连"猜你喜欢"（id=99）**，工程要先去电台分组里按标题找 station 再取 id —— 换成组件后
     那套字符串匹配（`HomeScreen.kt:220-222`）可以删掉，属收益。
  b. **参数写死、无可调**：没有 `exclude`/`from`/`num` 参数（调用方传什么都不看），
     所以"每次给不重复的新歌"这个工程预缓冲的核心前提（`RecommendStore.kt:86-91`）
     **在组件上无法表达**，只能靠宿主继续按 mid 去重；如果 id=99 每次回同一批，
     预缓冲会陷入"全是旧的"分支（`RecommendStore.kt:87-90`，会 delay 1200 重试）。
     **组件文档没有声称 id=99 会轮换**，本次也未实测 → 风险点。
  c. **介绍文案**：组件没有 `SongApi.intro` 的等价单点方法；但 `fetch_song_detail` 的
     `description`（`src/catalog.rs:264`）就是同一份 `info.intro.content[].value` 拼接，
     只是它同时要求 `song_mid` 且走 `song_detail` 的整套字段。实测（第一首收藏曲）
     `hasDescription:false, descriptionLen:0` —— **空即答案**（`src/catalog.rs:250-254` 注释），
     与工程 `SongApi.intro` 返回 null 的语义一致。
  d. 曲目字段缺口同上。
- 实测：`{count:5}`（真实凭据下能取到）。

**20）昵称** — `get_login_status`（`src/methods.rs:625-657`）｜**covered**

- 工程证据：`data/api/UserApi.kt:46-50`（`GetLoginUserInfo` → `data.info.nick`），
  调用点 `ui/settings/SettingsScreen.kt:852` → `NicknameCache`（`data/Prefs.kt:377-383`）；
  **主页问候语当前不消费昵称**（`ui/home/HomeScreen.kt:103`、`:215-217`，与 AGENTS.md L21 不一致，见
  `project-requirements.md` §1 的记录）。
- 组件证据：`login.nickname`（源 `info.nick`，`:648-656`），同时给 `loggedIn/musicId/vipType/expired/hasPlaybackKey`；
  凭据被拒时返回 `loggedIn:false` 而不是抛错（`:639-643`）。
- **差异**：组件是一次调用把"谁登录着 + 昵称 + 是否 VIP + 播放票据在不在"一起答（工程目前要单独调
  `GetLoginUserInfo`）。昵称不落盘这一点两边一致（工程的 `NicknameCache` 也只在内存）。
- 实测：`{loggedIn:true, expired:false, hasPlaybackKey:true, hasNickname:true}`。

---

## 2. 额外能力（组件有、工程没用）

按对本域的可用价值排序。每条一句话。

| 组件方法 | 它能给工程带来什么 |
|---|---|
| `fetch_toplist_categories` + `fetch_toplist_tracks` | 排行榜分组与曲目（`GetAll`/`GetDetail`，曲目在 `songInfoList`、总数 `totalNum`）——工程**完全没有榜单**，这是最大的净新增内容面。实测：4 个分组。 |
| `fetch_new_songs` | 按地区（0 最新/1 内地/2 港台/3 欧美/4 日本/5 韩国）的新歌列表，可直接做"新歌"栏。实测：52 首。 |
| `fetch_album_detail` | 专辑文案（`description/releaseDate/releaseYear/albumType/genreTags/language/labelOrCompany/songCount`）——工程的专辑页只有曲目列表（`ui/AppRoot.kt:638-644`），标题只带一个名字。**typed FFI 上会静默返回全 None**（见本文件 §0 结论 3），须走 stdio。 |
| `search_track_artwork` / `search_artist_artwork` / `search_album_artwork` | 本地曲库的封面候选匹配（带名次 `confidence` 0.86→0.50），给"本地音乐补封面"这类需求。 |
| `fetch_artist_biography` | 歌手简介（`artistDetail.description/region/foreignName`）——工程歌手页只有名字与两个计数（`SingerScreen.kt:247-259`）。 |
| `fetch_song_detail` 的附加字段 | `genreTags/language/labelOrCompany/releaseDate/duration` —— 工程的"歌曲信息"弹窗（`TrackDialogs.kt:215-229`）只有 7 行，这些字段可扩成完整信息页。 |
| `fetch_user_playlists` | "账号自己的歌单"（含自建）——工程只展示**收藏的**歌单；若产品要"我创建的歌单"，这是唯一的入口。 |
| 卡片副文案字段 | `playlist.playCount`/`creator`、`radioStation.listenerCount`、`artist.fanCount` —— 工程的卡片只有「N 首」一行（`Cards.kt:158-161`）。 |
| `start_login` + `poll_login` + `import_cookies` | 扫码登录（五步握手，产出 `uin`+`qm_keyst`）——**不需要 `euin`**，可替代工程现在"网页登录 WebView 读 cookie"的路径（`SettingsScreen.kt:807-908`），并顺带解掉收藏类接口对 euin 的依赖。 |
| `get_status` / `set_rate_limit` / `set_breaker` | 组件内的分桶限流（Read30/Interactive12/Playback20/Account12/Write6 每 10 秒）与熔断可观测、可下推 —— 工程目前只有自研的 120ms 节奏闸（`QqCore.kt:78-90`）。**注意：这三个在 FFI 上不可用**（`helpernext-inventory.md` §4.7）。 |
| `call_with_platform` | 逐调用指定平台档案的逃生口（`src/api.rs:378-394`），也是绕过 FFI 载荷不匹配（§0 结论 3）的唯一 typed 出路。 |
| `aria2_*` 下载引擎族 | 组件自带 aria2 JSON-RPC（安装/启动/并发/限速/断点/取消删临时文件）——可替代或并列于工程的 `Downloader`（**不属于本域**，列出仅供决策）。 |

---

## 3. 本次未验证 / 存疑（如实标注）

1. **未发起任何网络请求**（任务约束），所有"行为"结论都来自源码；下面这些尤其没有实测：
   - `fetch_radio_tracks` 的"每次新轮换"（工程无限流的前提）；
   - `fetch_artist_songs` 的 `latest` 跨页语义、`fetch_artist_albums` 的排序；
   - `search_artists/albums/playlists` 与 `fetch_playlist_tracks` 的返回；
   - `fetch_album_detail` / `fetch_artist_detail` 的成功路径（需设备会话）。
2. **给定的探针输出只覆盖 17 行**（`/tmp/helpernext-workflow-cred/probe-output.json`，`readOnly:true,
   calls:17, ok:17, failed:0`），其中属于本域的是：`get_helper_info`、`get_login_status`、
   `fetch_liked_songs`、`fetch_liked_albums`、`fetch_user_playlists`、`fetch_followed_artists`、
   `search_songs`、`fetch_radio_stations`、`fetch_recommend_feed`、`fetch_toplist_categories`、
   `fetch_new_songs`、`fetch_song_detail`、`fetch_album_tracks`。
   **本域内未被探针覆盖的**：`search_artists`、`search_albums`、`search_playlists`、
   `fetch_playlist_tracks`、`fetch_album_detail`、`fetch_artist_songs`、`fetch_artist_albums`、
   `fetch_artist_detail`、`fetch_radio_tracks` —— 这些行的 verdict 只有源码依据。
   （该文件与 `/tmp/helpernext-research/probe-output.json` 的 `diff` 只差 `fetch_new_songs.count`：
   69 vs 52，其余逐字相同；两份的 schema 与 `/tmp/helpernext-research/probe2.mjs` 完全吻合，
   不是 `probe.mjs` 的 schema。）
3. **"收藏的歌单"两条路径是否同一集合未验证**：工程走 `musicasset.PlaylistFavRead`（`v_list`），
   组件走老 fcgi `reqtype=3`。工程侧收藏歌单的条数没有留档，无法与组件实测的 4 条比对。
4. **FFI typed 面的可用性**：`helpernext-inventory.md` §4.7 记录的是"另一 agent 在某次运行中的实测
   （47 个导出，31 个不可用）"；我**只**依据 `src/api.rs:41-53` 的 `parse()` 名单 + `src/models.rs` 的模型
   自行推演出本域哪些 typed 方法会失败，没有重新运行那 47 个导出。
5. **组件 `pp` 的档案死配置**：`configure(default_platform)` 在 dispatch 路径上不被读取
   （`src/methods.rs:401` 用 `Platform::default()`=Web；`src/lib.rs:79-84` 的 `default_platform()`
   除测试外无调用点）——这是 `helpernext-inventory.md` §4.1.4 的结论，我核对了这两处源码属实，
   但**没有实跑**跨档案对比。

---

## 4. 一句话结论

本域 20 条工程能力里：**搜索四类、收藏三列表、歌单/专辑内曲目、歌手解析/歌曲/专辑、关注歌手、电台分组/曲目、
推荐流、昵称**在端点上**几乎逐字对得上**（同一个上游模块/方法/字段键），真正挡住"直接替换"的只有四件事：
① 组件 `Track` 缺 `fileSizes/genre/mediaMid`（影响音质过滤、查看格式、智能调音）；
② 组件把若干"总数"丢了（歌手歌曲/专辑、关注歌手、收藏专辑的歌数）且**不再支持多请求块合并**
（歌手专辑歌数补齐会从 1 次请求膨胀成 30 次）；
③ 组件 `latest` 只做页内排序、分页是 page 而不是 offset；
④ FFI typed 面在本域有 8+ 个方法不可用（含电台、歌手全套、推荐流），必须走 stdio 或 `call_with_platform`。
反过来，组件净送的增量是：排行榜、新歌、专辑详情文案、封面匹配、歌手简介、
以及**去掉 euin 依赖**（关注歌手/我喜欢/收藏列表都能用数字 uin 或直接读）——
其中"收藏/关注不再依赖 euin"直接消解了工程里一条贯穿多处的脆弱前置条件
（`PlaylistApi.kt:24-28`、`LikedStore.kt:32`、`UserApi.kt:20-21`、`SettingsScreen.kt:855-857`）。
