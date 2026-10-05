# NeuMusic 在线数据获取/写入入口盘点
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

> 只读盘点，未改动任何工程文件、未运行 gradle、未连接模拟器/adb。
> 依据：AGENTS.md「数据架构」（L108–157）与「开源协议与第三方代码」（L77–87）两节 + `app/src/main/java/com/neumusic/player/data/api/` 全部 11 个 .kt + `data/Prefs.kt` + `data/LikedStore.kt` + 全部调用点 grep。
> 全工程**只有一个 HTTP 入口族**：`QqCore`（OkHttp 4.12.0），另有 `LyricApi` 自建 Request 复用 `QqCore.http`，`Downloader`/`PlaybackService` 直拉 CDN。没有边车、没有 Python（AGENTS.md L15）。

---

## 0. 文件与对象总览

| 文件 | 对象 | 暴露的对外函数 |
|---|---|---|
| `data/api/QqCore.kt` | `QqCore` | `commWeb` / `commRadio` / `commAuth()` / `Req` / `call` / `data` / `code` / `mapItems` / `http` |
| `data/api/QqCore.kt` | 顶层扩展 | `String.toHttps()` / `String.cleanHighlight()` |
| `data/api/SearchApi.kt` | `SearchApi` | `songs` / `singers` / `albums` / `playlists`（私有 `searchBody`） |
| `data/api/PlaylistApi.kt` | `PlaylistApi` | `likedPage` / `playlistPage` / `favPlaylists` / `favAlbums` / `albumPage` / `Page`（私有 `requireEuin`） |
| `data/api/RadioApi.kt` | `RadioApi` | `groups` / `tracks`（**未被调用**） / `nextTracks` |
| `data/api/SingerApi.kt` | `SingerApi` | `resolve` / `songs` / `albums` / `ORDER_HOT` / `ORDER_NEW` / `AlbumPage`（私有 `fillSongCounts`） |
| `data/api/SongApi.kt` | `SongApi` | `playUrl` / `intro` / `setLiked` / `LikeResult` / `PlayUrlException`（私有 `fetchPurl` / `hasFile`） |
| `data/api/UserApi.kt` | `UserApi` | `followSingers` / `nickname` / `FollowSinger` |
| `data/api/LyricApi.kt` | `LyricApi` | `lyricsFor` / `parseLrc`（其余全私有） |
| `data/api/QrcCodec.kt` | `QrcCodec` | `decryptHex`（其余全私有） |
| `data/api/QqMapper.kt` | `QqMapper`（internal） | `track` / `playlistItem` / `albumItem` / `radioGroups`（私有 `singerNames`） |
| `data/api/ApiCache.kt` | `ApiCache` | `getOrPut` / `invalidate`（**未被调用**） / `clear` |

---

## 1. 逐条清单（函数 ← 上游接口 ← 调用点）

### 1.1 QqCore —— 请求底座

| 函数 | 文件 | 上游 | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `QqCore.call(comm, vararg reqs)` | `data/api/QqCore.kt:82` | `https://u.y.qq.com/cgi-bin/musicu.fcg`（`FCG`, QqCore.kt:37） | 信封 `{comm:{…}, req_1:{module,method,param}, req_2:…}`；**多 Req 可合并一次往返** | 整个响应 JSONObject（调用方再取 `data()`） | 视上游而定 | 全部端点模块：SearchApi.kt:31、PlaylistApi.kt:33/49/65/75/87、RadioApi.kt:17/27/50、SingerApi.kt:36/53/94、SongApi.kt:107/120/152、UserApi.kt:22/47 |
| `QqCore.commWeb` | `data/api/QqCore.kt:40` | — | `{ct:19, cv:1873}` 匿名网页档 | — | — | SearchApi.kt:31、SongApi.kt:120（intro） |
| `QqCore.commRadio` | `data/api/QqCore.kt:43` | — | `{ct:24, cv:0}` 电台档 | — | — | RadioApi.kt:17 |
| `QqCore.commAuth()` | `data/api/QqCore.kt:49` | — | `{ct:11, cv:14090008, v, tmeAppID:qqmusic, chid, tmeLoginType:2, QIMEI/QIMEI36, OpenUDID/udid, os_ver:12, phonetype:Android}` + `qq`=uin、`authst`=musickey | — | — | PlaylistApi（全部）、RadioApi.kt:27/50、SingerApi.kt:36/53/94、SongApi.kt:107/152、UserApi.kt:22/47 |
| `QqCore.data(root, key="req_1")` | `data/api/QqCore.kt:110` | — | 取 `root[key].data` | JSONObject? | — | 各端点模块；`SongApi.kt:123` 取 `info.intro.content`、PlaylistApi.kt:40 取 `songlist` |
| `QqCore.code(root, key="req_1")` | `data/api/QqCore.kt:114` | — | 取 `root[key].code`，缺省 **-1** | Int | — | SongApi.kt:157（喜欢写成功判定）、UserApi.kt:29（诊断日志） |
| `QqCore.mapItems(arr, transform)` | `data/api/QqCore.kt:118` | — | JSONArray→List，跳过非对象项 | List<T> | — | SearchApi.kt:41/48/66/82、PlaylistApi.kt:40/56/69/79/92、RadioApi.kt:31/55、SingerApi.kt:43/61、QqMapper.kt:113 |
| `String.toHttps()` | `data/api/QqCore.kt:128` | — | `http://` → `https://`，其余原样 | String | — | QqMapper.kt:92/104/120、SearchApi.kt:55/71/87、UserApi.kt:38 |
| `String.cleanHighlight()` | `data/api/QqCore.kt:137` | — | 剥 `<em>`/`</em>` 与 4 个 HTML 实体再 trim | String | — | SearchApi.kt:54/70/86 |
| `QqCore.http` | `data/api/QqCore.kt:30` | — | OkHttp `connectTimeout 15s / readTimeout 25s` | — | — | QqCore.kt:104、**LyricApi.kt:144（绕过 call 与节奏闸）** |

### 1.2 SearchApi —— 搜索（匿名网页档）

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `SearchApi.searchBody(q,type,num,page)`（私有） | `data/api/SearchApi.kt:19` | `music.search.SearchCgiService.DoSearchForQQMusicMobile` | `{searchid(时间戳后10位), query, search_type, num_per_page, page_num, highlight=(type==0), grp:true, selectors:{}, vec_selectors:[]}` | `data.body` | 读 | SearchApi.kt:37/46/64/80 |
| `SearchApi.songs(q,num=30,page=1)` | `data/api/SearchApi.kt:36` | 同上 `search_type=0` | 综合模式 | `body.item_song[]`，兜底 `body.song.list[]` → **`Track`** | 读 | `ui/search/SearchScreen.kt:144` |
| `SearchApi.singers(q,num=30,page=1)` | `data/api/SearchApi.kt:45` | 同上 `search_type=1` | — | `body.singer[]`（`singerMID/singerID/singerName/singerPic/songNum/albumNum`）→ **`SearchSinger`** | 读 | `ui/search/SearchScreen.kt:147`；**`SingerApi.resolve:30`（歌手页/播放栏的歌手数据源）** |
| `SearchApi.albums(q,num=30,page=1)` | `data/api/SearchApi.kt:63` | 同上 `search_type=2` | — | `body.item_album[]`（`albummid/name/pic/song_num/singer`）→ **`AlbumItem`** | 读 | `ui/search/SearchScreen.kt:150` |
| `SearchApi.playlists(q,num=30,page=1)` | `data/api/SearchApi.kt:79` | 同上 `search_type=3` | — | `body.item_songlist[]`（`dissid/dissname/logo/songnum`）→ **`PlaylistItem`** | 读 | `ui/search/SearchScreen.kt:153` |

分页契约：`page_num` 由 UI 在触底时 +1 续页（`SearchScreen.kt:144-154` 的 `loadPage` + `appendMore`，AGENTS.md 第五批）。

### 1.3 PlaylistApi —— 歌单/专辑/我喜欢读取（登录档）

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `PlaylistApi.requireEuin()`（私有） | `data/api/PlaylistApi.kt:24` | — | 无凭据抛 `未登录`；`euin` 空抛 `缺少 euin，请重新登录一次` | `CredentialInfo` | — | likedPage / playlistPage / favPlaylists / favAlbums |
| `PlaylistApi.likedPage(offset=0,num=100)` | `data/api/PlaylistApi.kt:31` | `music.srfDissInfo.DissInfo.CgiGetDiss` | `{disstid:0, dirid:201, tag:true, song_begin, song_num, userinfo:true, orderlist:true, enc_host_uin:euin}` | `data.songlist[]`→`Track`；总数 `dirinfo.songnum` → `Page(songs,total)` | 读 | `ui/AppRoot.kt:627`（Nav.Liked 列表）、`data/LikedStore.kt:41`（翻页取全，每页 300）、`ui/home/HomeScreen.kt:236`（经 ApiCache 取总数） |
| `PlaylistApi.playlistPage(tid,offset,num=100)` | `data/api/PlaylistApi.kt:47` | 同上，`disstid=tid, dirid=0` | 同参数结构 | 同上 | 读 | `ui/AppRoot.kt:635`（Nav.PlaylistDetail） |
| `PlaylistApi.favPlaylists(page=1,num=50)` | `data/api/PlaylistApi.kt:63` | `music.musicasset.PlaylistFavRead.CgiGetPlaylistFavInfo` | `{uin:euin, offset:(page-1)*num, size:num}` | **`data.v_list[]`** → `PlaylistItem` | 读 | `ui/home/HomeScreen.kt:189`（ApiCache `favPlaylists`）、`ui/home/Screens.kt:614`（PlaylistsScreen） |
| `PlaylistApi.favAlbums(page=1,num=50)` | `data/api/PlaylistApi.kt:73` | `music.musicasset.AlbumFavRead.CgiGetAlbumFavInfo` | `{euin, offset:(page-1)*num, size:num}` | **`data.v_list[]`** → `AlbumItem` | 读 | `ui/home/HomeScreen.kt:197`（ApiCache `favAlbums`）、`ui/home/Screens.kt:651`（AlbumsScreen） |
| `PlaylistApi.albumPage(albumMid,offset=0,num=100)` | `data/api/PlaylistApi.kt:86` | `music.musichallAlbum.AlbumSongList.GetAlbumSongList` | **驼峰 `albumMid`** + `begin`/`num` | `data.songList[].songInfo` → `Track`；总数 `totalNum`（播客类可为 0）→ `Page` | 读 | `ui/AppRoot.kt:642`（Nav.AlbumDetail） |
| `PlaylistApi.Page` | `data/api/PlaylistApi.kt:21` | — | `(songs, total:Int?)`，`total=null` 表示服务端没给 | — | — | AppRoot.kt:197/662、Screens.kt:136/191、SingerApi.kt:35/46 |

### 1.4 RadioApi —— 电台

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `RadioApi.groups()` | `data/api/RadioApi.kt:16` | `pf.radiosvr.GetRadiolist`（**commRadio**，块名 `radiolist`） | `{ct:"24"}` | `data("radiolist").radio_list[].list[]` → **`RadioGroup`/`RadioStation`** | 读 | `ui/home/HomeScreen.kt:206`（ApiCache `radioGroups`）→ HomeCache 快照；`HomeScreen.kt:222/250` 从中挑「猜你喜欢」的 id 喂 RecommendStore |
| `RadioApi.tracks(radioId,num=50)` | `data/api/RadioApi.kt:26` | `mb_track_radio_svr.get_radio_track`（commAuth，块名 `songlist`） | `{id, firstplay:1, num}` | `data("songlist").tracks[]` → `Track?`（空返回 null） | 读 | **无调用点**（全工程 grep `\.tracks(` 仅命中定义本身）—— 已被 `nextTracks` 取代的残留 |
| `RadioApi.nextTracks(radioId,firstplay,exclude,batches=4)` | `data/api/RadioApi.kt:45` | 同上（commAuth，块名 `songlist`） | 每批只带 `{id, firstplay:(首批且请求1？1:0)}`；**每次只回 5 首**（num 被忽略）但每批不同 → 反复调用按 mid 去重；批间 `delay(300)` | `tracks[]` → `Track`（过滤 `exclude`） | 读 | `ui/AppRoot.kt:660`（Nav.RadioDetail 无限流）、`ui/home/HomeScreen.kt:165`（推荐歌接入猜你喜欢队列）、`data/RecommendStore.kt:81`（预缓冲补货，batches=2） |

失败语义：`nextTracks` 全部批次都失败（风控/网络）**抛 `IllegalStateException("电台取歌失败，请稍后重试")`**，不当空表（否则首屏被误标「没有更多」）——RadioApi.kt:59-61。

### 1.5 SingerApi —— 歌手页

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `SingerApi.ORDER_HOT / ORDER_NEW` | `data/api/SingerApi.kt:26/27` | — | `1`=热门、`2`=最新（实测） | — | — | `ui/singer/SingerScreen.kt:130` |
| `SingerApi.resolve(name)` | `data/api/SingerApi.kt:29` | **复用 `SearchApi.singers(name, num=10)`**（匿名网页档） | 精确名优先，找不到取第一条 | `SearchSinger?`（含 mid/pic/songNum/albumNum） | 读 | `ui/common/PlayerBar.kt:152`（点播放栏歌手名）、`ui/singer/SingerScreen.kt:113`（关注列表来源计数为 0 时按名字补齐） |
| `SingerApi.songs(mid,order,offset,num=100)` | `data/api/SingerApi.kt:35` | `musichall.song_list_server.GetSingerSongList`（commAuth） | `{singerMid, order, number:num, begin:offset}` | `data.songList[].songInfo` → `Track`；总数 `totalNum` → `PlaylistApi.Page` | 读 | `ui/singer/SingerScreen.kt:141`（首屏）、`:167`（loadMore） |
| `SingerApi.albums(mid,order,offset,num=30)` | `data/api/SingerApi.kt:52` | `music.musichallAlbum.AlbumListServer.GetAlbumList`（commAuth） | 同上 | `data.albumList[]`（`albumMid/albumName/singerName`）→ `AlbumItem`；logo 本地拼 `y.gtimg.cn/music/photo_new/T002R300x300M000{mid}.jpg`（SingerApi.kt:75）；总数 `total` → `AlbumPage` | 读 | `ui/singer/SingerScreen.kt:147`（首屏）、`:176`（loadMore） |
| `SingerApi.fillSongCounts(mids)`（私有） | `data/api/SingerApi.kt:84` | `GetAlbumSongList` × N 合并进**一次** `QqCore.call` | 每 30 张一批 `req_1..req_N`，`runCatching` 静默失败；最多 3 批（`chunked(30).take(3)`），超出放弃计数 | `Map<albumMid, totalNum>` | 读 | SingerApi.kt:70 |

### 1.6 SongApi —— 播放直链 / 歌曲介绍 / 喜欢写入

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `SongApi.playUrl(track,quality=STANDARD)` | `data/api/SongApi.kt:51` | `music.vkey.GetVkey.UrlGetVkey`（commAuth，见 `fetchPurl:98`） | 首选档位 + 其余档位按 ordinal 降级；先用 `track.fileSizes` 过滤不存在档位（`hasFile:87`），元数据缺失则全链试；**104003 只记 `sawVipOnly` 不中断**，101404 记限流 | `String` 完整直链 `https://isure.stream.qqmusic.qq.com/{purl}`；失败抛 `PlayUrlException(message,result)` | 读 | `ui/common/TrackRow.kt:349`（`loadUrl`）→ `ui/AppRoot.kt:175`（注入 `PlayerHost.resolveUrl`）→ `player/PlayerHost.kt:180`；`data/Downloader.kt:28`（下载） |
| `SongApi.fetchPurl(track,q)`（私有） | `data/api/SongApi.kt:98` | 同上 | `{uin:(cred?.uin?:"0"), filename:["{前缀}{mediaMid}{扩展名}"], guid, songmid:[mid], songtype:[0], ctx:0}` | `midurlinfo[0].purl`；空则 `PlayUrlException("purl empty", result)` | 读 | SongApi.kt:64 |
| `SongApi.hasFile(track,q)`（私有） | `data/api/SongApi.kt:87` | — | 映射表 `128mp3/320mp3/96aac/192ogg/320ogg/flac` ↔ `Quality`（`Prefs.kt:11-17`） | Boolean | — | SongApi.kt:58 |
| `SongApi.intro(mid)` | `data/api/SongApi.kt:119` | `music.pf_song_detail_svr.get_song_detail_yqq`（**commWeb**） | `{song_mid}` | `data.info.intro.content[*].value` 以 `\n` 拼接；空/异常 → null | 读 | `data/RecommendStore.kt:95`（推荐卡介绍）→ 展示于 `ui/home/HomeScreen.kt:696`（空则「该歌曲暂无歌曲详情」） |
| `SongApi.setLiked(track,liked)` | `data/api/SongApi.kt:143` | `music.musicasset.PlaylistDetailWrite` + **`AddSonglist`/`DelSonglist`**（commAuth） | `{dirId:201, tid:0, bFmtUtf8:true, v_songInfo:[{songId:<数字>, songType:0}]}`；**必须数字 songId**；本地前置：未登录→`Unavailable("未登录")`、`songId<=0`→`Unavailable("这首没有可用的歌曲 id")` | `LikeResult`（`Success` / `Rejected(code)` / `Unavailable(reason)`）；成功要 `code==0 && data.retCode==0` | **写** | `ui/common/TrackRow.kt:325`（`toggleLike`）→ 被 `ui/common/PlayerBar.kt:282`、`ui/home/HomeScreen.kt:285`、`ui/home/Screens.kt:396`、`ui/search/SearchScreen.kt:390`、`ui/singer/SingerScreen.kt:319` 调用；另 `ui/player/PlayerScreen.kt:401`（播放页红心）直调 |
| `SongApi.PlayUrlException` | `data/api/SongApi.kt:96` | — | `(message, result:Int)`，message 是可直接展示的人话 | — | — | PlayerHost 透传给 `onError` → AppRoot toast（AppRoot.kt:174-176） |
| `LikeResult` | `data/api/SongApi.kt:24` | — | `Rejected(1000)` = 风控限流，**不自动重试** | — | — | `ui/common/TrackRow.kt:337`（`likeFailMessage`）、`ui/player/PlayerScreen.kt:401` |

### 1.7 UserApi

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `UserApi.followSingers(offset=0,num=30)` | `data/api/UserApi.kt:19` | `music.concern.RelationList.GetFollowSingerList`（commAuth） | `{HostUin:euin, From:offset, Size:num}`；**euin 为空直接返回 `emptyList to 0`，不发请求** | `data.List[]`（`MID/Name/AvatarUrl/Desc`）→ **`FollowSinger`**；总数 `data.Total` | 读 | `ui/home/HomeScreen.kt:227`（主页「关注的歌手」栏，前置 `euin` 非空判断在 `:226`） |
| `UserApi.nickname()` | `data/api/UserApi.kt:46` | `music.UserInfo.userInfoServer.GetLoginUserInfo`（commAuth） | 空 param | `data.info.nick`；失败/空 → null（`runCatching`） | 读 | `ui/settings/SettingsScreen.kt:852`（登录成功后拉一次）→ `NicknameCache`（仅内存，`Prefs.kt:376-383`） |
| `FollowSinger` | `data/api/UserApi.kt:54` | — | `mid/name/pic/desc` | — | — | `ui/home/HomeScreen.kt:150`（`followedSingers` 状态） |

### 1.8 LyricApi / QrcCodec —— 逐字歌词链路

| 函数 | 文件 | 上游 module.method | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `LyricApi.lyricsFor(mid)` | `data/api/LyricApi.kt:69` | `music.musichallSong.PlayLyricInfo.GetPlayLyricInfo`（见 `call:119`） | 进程内 LRU 式 `HashMap` 缓存（`CACHE_MAX=24`，满则整表 `clear()`）；`runCatching` 失败 → null | `Lyrics?` | 读 | `player/PlayerHost.kt:191`（与取链并行 `_lyrics.value = …`） |
| `LyricApi.call(mid,qrc)`（私有） | `data/api/LyricApi.kt:119` | 同上 | `{songMid, crypt:0, lrc_t:0, qrc:(1/0), qrc_t:0, roma:1, roma_t:0, trans:1, trans_t:0, needSingingAnnotations:false, type:1}`；**comm 硬编码匿名 `{ct:19,cv:1873}`** | `root.req_1.data` | 读 | LyricApi.kt:83（qrc=true）、`:101`（qrc=false 补翻译） |
| `LyricApi.fetch(mid)`（私有） | `data/api/LyricApi.kt:82` | — | 分流：`looksLikeHex` → `QrcCodec.decryptHex` + `parseQrcXml`；否则 base64 `decodeLrc` + `parseLrc` | `Lyrics` | 读 | LyricApi.kt:72 |
| `LyricApi.looksLikeHex` | `data/api/LyricApi.kt:151` | — | 长度 ≥32、偶数、全 hex 字符 | Boolean | — | LyricApi.kt:88/108 |
| `LyricApi.decodeLrc` / `mergeTranslation` / `attachRoman` | `data/api/LyricApi.kt:161/169/183` | — | base64 解码；翻译按 ±300ms 对齐（`//` 占位丢弃）；roma 按 ±150ms 对齐 | `List<LyricLine>` | — | LyricApi.kt:94/104/115 |
| `LyricApi.parseKanaTokens` / `isKanji` / `attachKana` | `data/api/LyricApi.kt:202/212/224` | — | `[kana:<n><读音>…]` token 流按行时间序贯穿全曲，**只有汉字消费 token**（假名/拉丁/标点保留原字）；`chars>1` 用 skip 覆盖后续字符 | 逐字 `LyricWord.kana` + 行级 `LyricLine.kana` | — | LyricApi.kt:113/115 |
| `LyricApi.optimize` / `truncateAt` | `data/api/LyricApi.kt:277/310` | — | ①空格规范化 ②清洗非刻意重叠（重叠 <500ms 且 ≤100ms 或 ≤下一行时长 10% → 截上一行末字到下一行起点）③行起点提前（≥600 提前 600；≥400 提前 400；否则提前间隔 70%），**只动行起点与末字，字时间轴不动** | `List<LyricLine>` | — | LyricApi.kt:115 |
| `LyricApi.parseLrc`（public） | `data/api/LyricApi.kt:330` | — | 多时间标签展开；`[ti:]/[ar:]/[kana:]` 元信息行丢弃 | `List<LyricLine>` | — | LyricApi.kt:94/104（工程内无其它调用点） |
| `LyricApi.parseQrcXml`（私有） | `data/api/LyricApi.kt:374` | — | 取 `LyricContent`（CDATA 或属性，属性形式反解 XML 实体）；`[行起始,行时长]字(起,时长)…` | `List<LyricLine>`（带 `words`） | — | LyricApi.kt:89/109 |
| `QrcCodec.decryptHex(hex)` | `data/api/QrcCodec.kt:202` | — | hex→bytes（长度偶数、8 字节对齐，否则 `IllegalArgumentException`）→ **三重 D(K3)→E(K2)→D(K1)**（24 字节连续密钥 `!@#)(*$%123ZXC!@!@#)(NHL`，QQ 私有 S/P/E 盒）→ zlib（带 BOM 剥离） | QRC XML 明文 String | — | `LyricApi.kt:89`（主歌词）、`LyricApi.kt:109`（roma 音译） |

### 1.9 QqMapper —— 响应→模型映射（无网络）

| 函数 | 文件 | 上游 | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `QqMapper.track(o)` | `data/api/QqMapper.kt:27` | — | 兼容三种形状：①`{mid,name,singer[],album{},file{},interval,pay,id}` ②专辑 `songInfo` ③电台扁平（`singer` 可为字符串）；`mediaMid` 兜底 `file.media_mid`→`mid`；`fileSizes` 收 `size_*`>0；`isVip`=`pay.pay_play==1`；`songId`=`id` 兜底 `songId` | **`Track`** | — | SearchApi.kt:41、PlaylistApi.kt:40/56/92、RadioApi.kt:31/55、SingerApi.kt:43 |
| `QqMapper.singerNames(o)`（私有） | `data/api/QqMapper.kt:71` | — | `singer[]` / `singer:"名字"` / `singername:"A/B"`（`/` 分隔再 ` / ` 拼接）；全空 → `"未知歌手"` | String | — | QqMapper.kt:54 |
| `QqMapper.playlistItem(o)` | `data/api/QqMapper.kt:86` | — | `tid` 兜底 `dissid`，≤0 返回 null；`logo.toHttps()` | `PlaylistItem?` | — | PlaylistApi.kt:69 |
| `QqMapper.albumItem(o)` | `data/api/QqMapper.kt:98` | — | `mid` 空返回 null；`logo.toHttps()` | `AlbumItem?` | — | PlaylistApi.kt:79 |
| `QqMapper.radioGroups(arr)` | `data/api/QqMapper.kt:113` | — | 外层 `radio_list[]`=分组、内层 `list[]`=电台；**分组 id 不能当电台 id**；`pic_url.toHttps()` | `List<RadioGroup>` | — | RadioApi.kt:19 |

### 1.10 ApiCache / LikedStore / 其它本地状态

| 函数 | 文件 | 上游 | 参数要点 | 返回映射 | 读/写 | 主要调用点 |
|---|---|---|---|---|---|---|
| `ApiCache.getOrPut(key,ttlMs=5min,loader)` | `data/api/ApiCache.kt:26` | — | 命中未过期直接返回；加载在锁外；`MAX_ENTRIES=32` LRU 淘汰；**只缓存幂等读** | T | 本地 | `ui/home/HomeScreen.kt:189`（`favPlaylists`）、`:197`（`favAlbums`）、`:206`（`radioGroups`）、`:236`（`likedCount`） |
| `ApiCache.invalidate(prefix)` | `data/api/ApiCache.kt:47` | — | 前缀失效 | — | 本地 | **无调用点**（退出登录走的是 `clear()`） |
| `ApiCache.clear()` | `data/api/ApiCache.kt:53` | — | 清空 | — | 本地 | `ui/settings/SettingsScreen.kt:202`（退出登录） |
| `LikedStore.refresh(force)` | `data/LikedStore.kt:31` | 经 `PlaylistApi.likedPage`（每页 300，最多 50 页） | `euin` 空则静默返回；进行中/已加载非 force 直接返回 | `StateFlow<Set<Long>>`（songId 集合） | 读 | `ui/home/HomeScreen.kt:234`、`ui/home/Screens.kt:245`（列表页加载完）、`ui/player/PlayerScreen.kt:154` |
| `LikedStore.mark(songId,liked)` | `data/LikedStore.kt:59` | — | 本地翻转，写成功后调用，避免重拉整表 | — | 本地 | `ui/common/TrackRow.kt:328`、`ui/player/PlayerScreen.kt:405` |
| `LikedStore.clear()` | `data/LikedStore.kt:65` | — | 退出登录 | — | 本地 | `ui/settings/SettingsScreen.kt:203` |
| `LikedStore.isLiked(songId)` | `data/LikedStore.kt:56` | — | — | Boolean | 本地 | **无调用点**（各处直接订阅 `liked` 流后 `in` 判断） |
| `HomeCache`（快照） | `data/HomeCache.kt:50/79/…` | 承接上面 4 个读接口 | 7 天过期（`MAX_AGE_MS`），stale-while-revalidate | JSON 精简字段 | 本地 | 读 `ui/home/HomeScreen.kt:181-184`；写 `:192/201/210/239`；清 `ui/settings/SettingsScreen.kt:204` |
| `TrackListCache` | `ui/home/Screens.kt:491` | — | 曲目列表进程内缓存 + 滚动位置 | — | 本地 | `ui/home/Screens.kt:153/172/244/289`、`ui/AppRoot.kt:651` |
| `RecommendStore` | `data/RecommendStore.kt`（`refill:75`/`advance:114`） | 经 `RadioApi.nextTracks` + `SongApi.intro` | CAP=5；已展示 mid 落盘 `recommend_buffer.json`（`EXCLUDED_CAP=60`） | `StateFlow<List<Entry>>` | 本地（落盘） | `ui/home/HomeScreen.kt:251/287/290` |

### 1.11 周边网络（非 QqCore 信封，但属于「在线数据获取」）

| 位置 | 用途 | 上游 | 读/写 | 调用点 |
|---|---|---|---|---|
| `data/Downloader.kt:28` + `:37` | 下载：先 `SongApi.playUrl` 拿直链，再用**独立 OkHttp**（readTimeout 120s）拉流写 MediaStore | `isure.stream.qqmusic.qq.com` | 读 | `ui/home/Screens.kt:454`、`ui/search/SearchScreen.kt:529` |
| `player/PlaybackService.kt:126-141` | 媒体通知封面：`java.net.URL(url).openStream()` → Bitmap | `y.gtimg.cn` | 读 | PlaybackService 内部（`buildNotification` 时 `artwork==null && art.isNotEmpty()`） |
| `ui/settings/SettingsScreen.kt:875-895` | 网页登录 WebView：`loadUrl("https://y.qq.com/")`，桌面 UA + `setAcceptThirdPartyCookies(true)`（二维码是 graph.qq.com iframe）；`Adapter` 只放行 http/https（`:931`） | y.qq.com | 读（写 cookie） | 设置页「网页登录」 |
| `ui/settings/SettingsScreen.kt:839-846` | 完成后读 cookie：`uin`（去 `o` 前缀）+ `qm_keyst` + `euin` → `Prefs.saveCredential` | — | 写本地 | `:850` |
| Coil `AsyncImage`（`coil-compose:2.5.0`） | 封面/头像加载，全部走 `toHttps()` 后的 URL | `y.gtimg.cn` | 读 | Cards.kt:87/135、PlayerBar.kt:382、TrackRow.kt:288、HomeScreen.kt:545/579、PlayerScreen.kt:649、SingerScreen 相关 |

---

## 2. 请求底座

### 2.1 三套 comm（`data/api/QqCore.kt`）

| comm | 定义 | 用途 | 谁在用 |
|---|---|---|---|
| `commWeb` | `:40` `{ct:19, cv:1873}` | **匿名网页档，唯一能用综合模式搜到歌的组合** | SearchApi 全部（`SearchApi.kt:31`）、`SongApi.intro`（`:120`） |
| `commRadio` | `:43` `{ct:24, cv:0}` | 电台档，`pf.radiosvr` 要求 | `RadioApi.groups`（`:17`） |
| `commAuth()` | `:49` | **登录档（Android 档位）**，其余全部（含写操作） | PlaylistApi / RadioApi.tracks+nextTracks / SingerApi / SongApi.playUrl+setLiked / UserApi |

`commAuth()` 固定字段：`ct:11, cv:14090008, v:14090008, tmeAppID:qqmusic, chid:10003505, tmeLoginType:2, QIMEI=QIMEI36=Prefs.qimei, OpenUDID=udid=Prefs.guid, os_ver:12, phonetype:Android`（`:50-58`），凭据非空时补 `qq`=uin、`authst`=musickey（`:59-61`）。**QIMEI 自报随机值即可（无需真实置备）**——AGENTS.md L116 与 QqCore.kt:46-48 注释一致；`Prefs.qimei`/`Prefs.guid` 都是首次调用时生成的 UUID 去横杠并写盘（`Prefs.kt:97-116`）。

⚠️ **AGENTS.md 表格里「`{ct:24,cv:0}` 电台」与 QqCore 一致；`commAuth` 的 `cv` 是 14090008**——写操作（Add/DelSonglist）换 comm 是成败关键：网页 comm 下 80105、Android 档 0（AGENTS.md L130、SongApi.kt:140-142）。

### 2.2 120ms 节奏闸（`QqCore.kt:78-90`）

- 实现：`paceMutex` + `lastCallAt`，`MIN_INTERVAL_MS = 120L`，按 `SystemClock.elapsedRealtime()` 计算。
- 语义：**只闸「起跑」，不串行「跑步」**——出闸后的请求各自并发（QqCore.kt:72-76 注释）。目的是削掉「主页并发拉三栏 / 音质降级链 / 电台连续批 / 自动切歌」造成的瞬时请求尖峰（AGENTS.md 认定这是触发风控的主因）。
- 作用范围：**只有 `QqCore.call` 在闸内**。两处例外：
  1. `LyricApi.call`（`LyricApi.kt:119-148`）自建 Request 走 `QqCore.http` 直发，**不过闸**；
  2. `Downloader`（`:37`）与 `PlaybackService`（`:130`）拉 CDN 直链，**不过闸**（不是 musicu.fcg，风控模型不同）。
- 另有两处模块级小歇，属于业务自保：`RadioApi.nextTracks` 批间 `delay(300)`（`:49`）、`RecommendStore.refill` 空手时 `delay(1200)`（`RecommendStore.kt:88`）。
- `TrackListScreen.fetchPage` 对失败的页做 0.6s/1.2s 退避重试，3 次放弃（`ui/home/Screens.kt:191-204`）。

### 2.3 凭据字段（uin / musickey / euin）

- 存储：`Prefs.credential` 是 `neumusic.xml` 里的一段 JSON，`CredentialInfo(uin, musickey, euin="")`（`Prefs.kt:352-374`）。
- 唯一入口是网页登录 WebView（`SettingsScreen.kt:807-908`）：从 `https://y.qq.com/` 的 cookie 读 `uin`（`removePrefix("o")`）、`qm_keyst`→musickey、`euin`（可能缺）；登录成功即 `saveCredential` + 拉一次昵称（`:850-852`）。`euin` 也可 `run-as` 直改 XML 注入（AGENTS.md L164）。
- **euin 缺失的影响（分层，逐条落到代码）**：
  - `PlaylistApi.requireEuin()`（`:24-28`）→ **抛** `IllegalStateException("缺少 euin，请重新登录一次")`；于是 `likedPage`/`playlistPage`/`favPlaylists`/`favAlbums` **一个都取不到**（`CgiGetDiss` 缺 `enc_host_uin` 会静默返回空列表，所以宁可显式抛）。
  - `LikedStore.refresh`（`LikedStore.kt:32`）在 `euin` 空时**静默 return**（不抛、不加载）——UI 红心全为未喜欢。
  - `UserApi.followSingers`（`:20-21`）`euin` 空时**不发请求**，返回 `emptyList to 0`。
  - `SongApi.playUrl`/`albumPage`/`SingerApi.*` **不依赖 euin**（只用 commAuth 的 qq/authst）——缺 euin 仍可搜索、播放、看专辑。
  - UI 侧：设置页账号区文案按 `cred?.euin.isNullOrEmpty()` 切换（`SettingsScreen.kt:192`）；主页「关注的歌手」前置判断（`HomeScreen.kt:226`）；登录 toast 会提示「已登录（缺 euin，收藏列表可能不可用；建议重新登录一次）」（`SettingsScreen.kt:855-857`）。
- 退出登录：`Prefs.clearCredential()` + `ApiCache.clear()` + `LikedStore.clear()` + `HomeCache.clear()` + `TrackListCache.clear()`（`SettingsScreen.kt:199-206`）。

### 2.4 封面 URL 规整

- **统一入口 `String.toHttps()`**（`QqCore.kt:128-131`）：接口给的多是 `http://`，Android 默认禁明文流量 → 不升级会**静默不显示封面**。凡是接口直给的 URL 都过它：`QqMapper.playlistItem:92`、`albumItem:104`、`radioGroups:120`、`SearchApi:55/71/87`、`UserApi:38`。
- **本地拼装的封面不走 toHttps（本来就是 https）**：
  - `Track.coverUrl`（`Models.kt:20-23`）：`https://y.gtimg.cn/music/photo_new/T002R300x300M000{albumMid}.jpg`，albumMid 空时退化为 `…M000.jpg`。
  - `SingerApi.albums` 的 logo（`:75`）：同一个 T002R300x300M000 模板按专辑 mid 拼。
- **明文兜底配置**：`res/xml/network_security_config.xml` 只对 `127.0.0.1`/`10.0.2.2`/`localhost` 放行 cleartext，注释写明「App 走 https 直连 QQ 音乐，不依赖这些」。这解释了 toHttps 的必要性——没有全局 `usesCleartextTraffic`。

### 2.5 异常语义

| 层 | 行为 |
|---|---|
| `QqCore.call` | 不吞异常：网络失败/非 JSON 响应会 `JSONObject(...)` 抛 `JSONException`，超时抛 OkHttp 异常，由调用方 `runCatching` 处理 |
| `QqCore.data/code` | **不抛**：缺块返回 `null`/`-1`（`code` 默认 -1，`data` 返回 null） |
| SearchApi / RadioApi.groups / PlaylistApi.fav* / SingerApi.songs+albums / UserApi.nickname / SongApi.intro | 用 `opt*` + 空判断，**多数把「空」当正常空表返回**（`songs`/`singers`/`albums`/`playlists` 返回 `emptyList`） |
| PlaylistApi.likedPage / playlistPage / fav* / albumPage | `likedPage` 对 `data==null` **抛** `IllegalStateException("我喜欢数据为空")`；`fav*`/`albumPage`/`playlistPage` 不抛（空表 / total=null） |
| SongApi.playUrl | **唯一有类型化异常的读接口**：`PlayUrlException(message, result)`，message 人话分级（会员/限流/其它），`result` 保留原始码；`104003` 与 `101404` 的语义见 §2.6 |
| SongApi.setLiked | 不抛：`LikeResult.Success/Rejected(code)/Unavailable(reason)`；**Rejected(1000) 是风控限流，不自动重试**（AGENTS.md L131-133、TrackRow.kt:337） |
| RadioApi.nextTracks | 全批失败**抛** `IllegalStateException("电台取歌失败，请稍后重试")`（刻意不当空表，`:59-61`） |
| 上层统一姿势 | 调用点几乎一律 `runCatching { … }.getOrNull()/.getOrDefault(…)`（例：`Screens.kt:191-204`、`SingerScreen.kt:141/147/167/176`、`HomeScreen.kt:189-236`）；`PlayerHost` 的 `onError` 由 AppRoot 注入 toast，自动切歌失败不静默（`AppRoot.kt:174-176`、AGENTS.md L10） |

### 2.6 关键业务码（实测结论，代码里都有对应分支）

| 码 | 含义 | 代码位置 |
|---|---|---|
| `104003` | **档位级**：该档要会员权益（免费歌的无损/高品质同样返回）；降级链**绝不能中断** | `SongApi.kt:69`（只记 `sawVipOnly`）、`:77-78`（只有连最便宜档都 104003 才判无权限） |
| `101404` | 短时限流（purl 为空），降级重试即可 | `SongApi.kt:70`、`:79` |
| `result=22` | 未登录 | AGENTS.md L157（代码未单独分支） |
| `1000` | 写操作风控限流，`retCode` 缺失；读接口同时正常 | `SongApi.kt:158-159`、`TrackRow.kt:338`、`PlayerScreen.kt:407-409` |
| `80105` | **网页 comm 下**的写拒绝；Android 档无此问题 | `TrackRow.kt:339`（文案保留） |
| `40000` | `PlaylistFavWrite/CgiAddSonglist + songMid` 路径，不要回退到它 | AGENTS.md L134（代码无此路径） |

---

## 3. 逐字歌词链路

### 3.1 取数（LyricApi.kt:119-148）

- 端点：`musicu.fcg` → `music.musichallSong.PlayLyricInfo.GetPlayLyricInfo`；
- comm：**硬编码匿名 `{ct:19, cv:1873}`**（同 `QqCore.commWeb` 的值，但独立写字面量，未复用 `QqCore.commWeb`）；响应取 `root.req_1.data`；
- param 必带布尔开关：`qrc:1`（要 1 才给 QRC）、`roma:1`、`trans:1`、`type:1`、`crypt:0`、`lrc_t/qrc_t/roma_t/trans_t:0`、`needSingingAnnotations:false`（`LyricApi.kt:120-131`）；
- **两趟请求**：主请求 `qrc=true`；若 `trans` 为空且主歌词有逐字行，再补一次 `qrc=false` 取翻译（`LyricApi.kt:99-103`）。
- 缓存：`mid → Lyrics` 进程内 `HashMap`，`CACHE_MAX = 24`，满则 `clear()`（`:66-67`、`:75-79`）——注意是**整表清空**而非 LRU。

### 3.2 分流与解密（`fetch:82-116` + `looksLikeHex:151`）

1. `looksLikeHex(rawLyric)`（长度 ≥32、偶数、全 hex）→ `QrcCodec.decryptHex` → `parseQrcXml`；
2. 否则按 base64（`decodeLrc:161`）→ `parseLrc`；
3. 两条都空 → 返回 null（UI 显示「暂无歌词」）。**必须先判别 hex**：`qrc=1` 后 lyric 变 hex 密文，按 base64 解会得到乱码、`parseLrc` 找不到时间标签 → 整首「暂无歌词」（`LyricApi.kt:33-34`）。

### 3.3 QrcCodec.decryptHex（QrcCodec.kt:202-227）

hex 文本 → bytes（**偶数长度 + 8 字节对齐**，否则 `require` 抛 `IllegalArgumentException`）→ 按 8 字节块三重 `D(K3)→E(K2)→D(K1)`（`KD` 预生成于 `:192-196`，密钥 24 字节连续 `!@#)(*$%123ZXC!@!@#)(NHL`，S/P/E 盒为 QQ 私有：`S_BOXES:28`、`P_BOX:26`、`E_BOX:27`、`KEY_COMPRESSION:20`）→ `java.util.zip.Inflater` 解压（256KiB 输出缓冲，`:214-221`）→ 剥 UTF-8 BOM（`:223-225`）→ 返回带 XML 壳的 QRC 明文。
来源：算法移植自 [qrc-decoder](https://github.com/apoint123/qrc-decoder)（MIT，Copyright (c) apoint123），文件头 `QrcCodec.kt:12-15` 保留了原版权与 MIT 声明——符合 AGENTS.md「照搬时必须保留原版权声明与许可文本、注明来源」的要求，且 MIT 与项目 GPL-3.0 兼容。

### 3.4 解析与「绝对毫秒」坑（`parseQrcXml:374-413`）

- 取 `LyricContent`：CDATA 优先，否则属性形式（并反解 `&lt;/&gt;/&quot;/&apos;/&amp;`）；
- 行格式 `[行起始,行时长]字(起,时长)字(起,时长)…`；元信息行（无 `[\d+,\d+]`）被 `LINE_RE` 自然过滤；
- **字时间基准判别**（`:405`）：`raw.maxOf { at + dur } > lineDur + 500` → 说明本来就是**绝对毫秒**（不是相对行首），直接用；否则加 `lineStart`。这条判别就是「只有第一句歌词有扫色」bug 的修复（AGENTS.md L142）。

### 3.5 优化策略（`optimize:277-307`）

1. 空格规范化：`Regex("""\s+""")` → 单空格（只动行文本，字单元自带空白不受影响）；
2. 清洗非刻意重叠：`prev.endMs > 0` 且 `overlap ∈ (0, 500)` 且（`overlap ≤ 100` 或 `overlap*10 ≤ 本行时长`）→ `truncateAt(prev, l.timeMs)` 把上一行末字截到本行起点；
3. 行起点提前（**只提前行起点，字时间不动**）：`gap ≥ 600 → 600`；`gap ≥ 400 → 400`；否则 `gap*7/10`；`coerceAtLeast(0)`。
注意 `LyricLine.endMs` 只在逐字行有意义（非逐字返回 -1，`Lyrics.kt:27-28`），所以第 2 步对行级 LRC 不生效。

### 3.6 译 / 音（roma）/ 注（kana）

| 数据 | 来源字段 | 处理函数 | 对齐规则 | 承载字段 |
|---|---|---|---|---|
| 翻译 `译` | `trans`（base64 明文 LRC；`//` 为占位） | `mergeTranslation:169` | 时间戳精确匹配，否则最近邻 **±300ms**；`//`/空丢弃 | `LyricLine.translation` |
| 音译 `音` | `roma`（hex QRC 密文，同一 `QrcCodec`） | `attachRoman:183` | 行起点 **±150ms** 对齐回主行；空/英文曲跳过 | `LyricLine.roman`（行级文本） |
| 注音 `注` | `trans` 里的 `[kana:<n><读音>…]` 元数据 | `parseKanaTokens:202` + `attachKana:224` | token 流**按行时间序贯穿整首歌**，**只有汉字消费 token**（`isKanji:212`：`0x4E00..0x9FFF`、`0x3400..0x4DBF`、`々 0x3005`）；`chars>1` 用 `skip` 覆盖后续字符 | 逐字 `LyricWord.kana`（字上注音）+ 行级 `LyricLine.kana`（读音行） |

三个开关（`Prefs.kt:151-167`）：
- `showLyricTranslation`（默认 **true**，带 `lyricTransFlow` 响应式通道）、`showLyricRoman`（默认 false）、`showLyricKana`（默认 false）；
- 播放页右上角「译/音/注」三开关同行（`ui/player/LyricsView.kt:367-369`），点击写回 Prefs（`PlayerScreen.kt:253-267`）；
- **可用性门控**：只有当前 `Lyrics` 真带对应数据时才给按钮/才显示——`canToggleTranslation/Roman/Kana = lyrics?.hasTranslation/hasRoman/hasKana`（`PlayerScreen.kt:246-252`，`Lyrics.kt:37-41`）；显示时再与开关做 `&&`；
- 设置页还有一个「显示翻译」开关（`SettingsScreen.kt:360-362`）；`showRoman`/`showKana` **只在播放页可切**，设置页没有对应 UI。

---

## 4. 盘点中发现的问题（供接入调研参考）

1. **`RadioApi.tracks`（RadioApi.kt:26）无任何调用点** —— 已被 `nextTracks` 完全取代的残留（同端点、但 `num` 被服务端忽略且不分批去重），接入时可考虑删或标注。
2. **`ApiCache.invalidate`（ApiCache.kt:47）无调用点**；退出登录只走 `clear()`。若新增「登录切换」场景需注意前缀失效能力目前是死代码。
3. **`LikedStore.isLiked`（LikedStore.kt:56）无调用点**（各处直接订阅 `liked` 流后 `in`）。
4. **`LyricApi` 不走 `QqCore.call`**：自建 Request（`LyricApi.kt:138-147`）+ 硬编码 comm 字面量，因此 (a) **绕过 120ms 节奏闸**，(b) comm 定义与 `QqCore.commWeb` 双份维护，(c) Referer/UA 头在 `QqCore.kt:100-101` 与 `LyricApi.kt:140-141` 各写一份。
5. **`SongApi.playUrl` 的档位过滤依赖 `Track.fileSizes`**：`RecommendStore` 落盘恢复 Track 时**没有恢复 `fileSizes`**（`RecommendStore.kt:52-63` 只存了 mid/name/mediaMid/singer/albumName/albumMid/intervalSec/isVip/songId），所以冷启动推荐的歌走「fileSizes 为空 → 全链都试」分支（多打几次请求，但功能正确）。
6. **`QqCore.call` 无 HTTP 状态码检查**：直接 `JSONObject(resp.body?.string() ?: "{}")`（`:104-106`），非 200 但 body 合法的响应会被当成功解析；`data()/code()` 的 null/-1 是唯一防线。

---

*本文件仅写入 `.zcode/research/`，未改动任何工程源码；未运行 gradle，未使用 adb/模拟器。*
