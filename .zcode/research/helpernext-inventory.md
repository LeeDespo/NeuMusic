# QQMusicApi_HelperNext 组件接口盘点
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

- 源码：`/tmp/helpernext_probe`（工作区外，只读），git 提交 `2ff7e71`（`git log --oneline -1` → `2ff7e71 feat: stop when the host stops, however it stopped`）。
- 版本：`Cargo.toml` `version = "0.1.0"`，`license = "GPL-3.0-or-later"`；组件自报 `helperVersion=0.1.0`、`protocolVersion=2`（`src/methods.rs:20-23`，实测 `get_helper_info` 输出）。
- 盘点方式：通读 README.md、docs/{endpoints,parsing,ffi}.md、src/{api,models,methods,guard,login,credential,upstream,catalog,lib}.rs、src/bin/stdio.rs、src/qrc.rs、src/device.rs、src/aria2.rs；并用仓库已编译的 `target/release/qqmusic-helper-next` 与自建 out-of-repo 探针（`/tmp/hn_probe`，链接 `/tmp/helpernext_probe`，target 在 `/tmp/hn_probe_target`）实测了本地方法、匿名读取与全部 47 个 typed FFI 包装的载荷形状。**未修改 /tmp/helpernext_probe 任何文件**（`git status --porcelain` 为空）。

---

## 0. 两种调用面（先看这一节，它决定集成方式）

组件同时提供两个面，**两者行为不一致**：

| 调用面 | 入口 | 状态 |
|---|---|---|
| 子进程（stdio） | `qqmusic-helper-next` 二进制，一行一个 JSON `{"id","method","params"}` | **完整可用**。命令面方法（set_rate_limit / set_breaker / aria2_* / import_cookies / logout）在 `src/bin/stdio.rs:135-197` 被拦截后自己处理 |
| BoltFFI（Swift/Kotlin/…） | `src/api.rs` 的 `#[export]` 函数（47 个） | **47 个里有 31 个不可用**：11 个返回「不支持的方法」，18 个返回的 JSON 与模型/载荷键不匹配，2 个反序列化「成功」但字段全 `None`（静默丢数据）。可用 16 个。证据见 §4.7 |

`src/bin/stdio.rs:209` 与 `src/api.rs:31` 都调用同一个 `methods::dispatch`，但 **11 个命令面方法没有库内入口**：`dispatch` 的 match（`src/methods.rs:405-459`）里没有 `set_rate_limit` / `set_breaker` / `aria2_*` / `import_cookies` 以外的分支，这些只在 stdio 的 `serve()` 里被拦截（`src/bin/stdio.rs:135-197`）。FFI 路径直接调 `methods::dispatch`（`src/api.rs:31`），所以从 FFI 调这 11 个方法一律得到 `Upstream("不支持的方法：…")`（`import_cookies` 在 `dispatch` 里有一支明确返回同样错误，靠 FFI 的 `import_credential` 走本地存储绕过，`src/methods.rs:448`、`src/api.rs:69-78`）。

还有 **18 个读取方法的载荷键与模型不匹配**：`dispatch` 把结果包在 `detail`/`toplistGroups`/`radioGroups`/`candidates`/`stream`/`qrcode`/`status`/`artistDetail` 等键里，而 `api.rs::parse` 只解包 7 个键（`src/api.rs:41-53`），于是模型拿到信封对象本身、反序列化失败（§4.7 有逐条实测）。

---

## 1. 对外方法总表（46 个，以 `src/methods.rs:29-83` 的 `METHODS` 与实测 `get_helper_info` 输出为准）

`METHODS` 数组长度 = 46（脚本数得；实测 `get_helper_info` 返回同一份 46 项清单）。下表的「方法」列就是协议名（stdio 用），括号里是 FFI 包装名（`src/api.rs` 的 `#[export]`，见 `src/api.rs:156-201` 的 alias 表）；无括号表示同名。

约定：
- **平台档案**列写的是**实际会用的档案**，不是「可以传什么」。`params.platform` 可覆盖为 `web`/`android`/`yqq`/`yqq.json`（`src/methods.rs:387-391`、`src/upstream.rs:49-55`）。
- **需登录** = 组件自己会因凭据不可用而报 `需要登录后才能读取`（`require_login`：`src/methods.rs:857-863`、`src/catalog.rs:113-119`）。
- 「—（本地）」= 不访问 QQ 上游。
- 返回字段列注意大小写。

| # | 方法（FFI 名） | 回答什么 | 上游 module / method | 关键参数（字段名照抄） | 关键返回字段 | 需登录 | 平台档案 | 写 |
|---|---|---|---|---|---|---|---|---|
| 1 | `get_helper_info`（`component_info`） | 组件是什么、服务哪些方法 | —（本地） | — | `helper.helperVersion` / `helper.protocolVersion` / `helper.libraryVersion` / `helper.credentialDir` / `helper.methods[]` | no | 不适用 | no |
| 2 | `get_login_status`（`login_status`） | 谁登录着（问上游，过期也如实报 false） | `music.UserInfo.userInfoServer` / `GetLoginUserInfo` | `{}` | `login.loggedIn` / `login.musicId` / `login.nickname`（源 `info.nick`）/ `login.vipType` / `login.expired` / `login.hasPlaybackKey` | no（无凭据直接回 `loggedIn:false`） | web（上游无档案依赖） | no |
| 3 | `import_cookies`（`import_credential`） | 用一个网页登录的 cookie 存下凭据 | —（本地；FFI 签名 `import_credential(uin, qm_keyst)`） | `params.cookies.uin` + `params.cookies.qm_keyst`（`qqmusic_uin`/`musicid` 可替代 `uin`；`encrypt_uin` 选填） | `login.loggedIn: true` | no | 不适用 | no（本地文件写入） |
| 4 | `logout`（`logout`） | 忘掉存下的登录 | —（本地） | — | `login.loggedIn:false` | no | 不适用 | no（本地文件删除） |
| 5 | `fetch_liked_songs`（`liked_songs`） | 我喜欢，一页一首 | `music.srfDissInfo.DissInfo` / `CgiGetDiss` | `page`（≥1，默认 1）、`limit`（clamp 1–100，默认 50）；内部 `disstid:0, dirid:201, tag:true, song_begin, song_num, userinfo:true, orderlist:true` | `likedSongs.title` / `likedSongs.total`（源 `dirinfo.songnum`）/ `likedSongs.tracks[]` | **yes** | web | no |
| 6 | `fetch_liked_albums`（`liked_albums`） | 收藏的专辑 | 老 fcgi `GET c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg` | `limit`（1–100，默认 30）；内部 `ct=20&cid=205360956&userid=<数字uin>&reqtype=2&sin=0&ein=<limit>&format=json` | `albums[]`：`id` / `title` / `albumMid` / `coverURL` / `artist` / `releaseDate`（`pubtime` 已按 +08:00 转日期）/ `source` | **yes** | web | no |
| 7 | `fetch_user_playlists`（`user_playlists`） | 账号自己的歌单 | 同上老 fcgi | `limit`（1–100，默认 100）；内部 `reqtype=3` | `playlists[]`：`id` / `title` / `coverURL` / `creator` / `songCount` / `playCount` / `source`；无 `dissid` 的保留目录被跳过 | **yes** | web | no |
| 8 | `fetch_followed_artists`（`followed_artists`） | 关注的歌手 | `music.concern.RelationList` / `GetFollowSingerList` | `page`（≥1，默认 1）、`limit`（1–100，默认 30）；内部 `HostUin`（encrypt_uin 优先，否则数字 uin）、`From=(page-1)*limit`、`Size=limit` | `artists[]`：`singerMid` / `name` / `coverURL` / `fanCount` / `source`；**列表键是 `List`（大写 L）** | **yes** | web | no |
| 9 | `fetch_playlist_tracks`（`playlist_tracks`） | 歌单/排行榜里的曲目 | `music.srfDissInfo.DissInfo` / `CgiGetDiss` | `songlistId`（别名 `disstid`/`id`/`topId`）、`offset`（别名 `song_begin`；缺省时按 `page` 换算）、`page`、`limit`（1–200，默认 100）；内部 `disstid=<id>, dirid:0` | `tracks[]` / `total`（源 `dirinfo.songnum`，取不到时为 `null`） | **yes** | web | no |
| 10 | `get_status`（`guard_status`） | 限流用量与熔断状态 | —（本地） | — | `status.breaker`（`closed`/`half-open`/`open`）/ `status.breakerConfig.{enabled,failureThreshold,failureWindowSeconds,openSeconds}` / `status.rateLimit.config.{enabled,windowSeconds,maxRequests}` / `status.rateLimit.{read,interactive,playback,account,write}` | no | 不适用 | no |
| 11 | `set_rate_limit`（`set_rate_limit`） | 下推全局请求上限（组件重启即忘） | —（本地配置） | `enabled`、`windowSeconds`（别名 `window`；clamp 1–3600，默认 10）、`maxRequests`（别名 `max`；clamp 1–100000，默认 100） | `rateLimit.{enabled,windowSeconds,maxRequests}`（回显 clamp 后的值） | no | 不适用 | no |
| 12 | `set_breaker`（`set_breaker`） | 下推熔断参数（同时清空熔断状态） | —（本地配置） | `enabled`、`failureThreshold`（别名 `threshold`；1–100，默认 5）、`failureWindowSeconds`（别名 `failureWindow`；1–3600，默认 60）、`openSeconds`（别名 `openFor`；1–3600，默认 30） | `breaker.{enabled,failureThreshold,failureWindowSeconds,openSeconds}` | no | 不适用 | no |
| 13 | `aria2_status`（`aria2_status`） | 下载引擎装没装/跑没跑/什么参数 | 本地 aria2 JSON-RPC（引擎缺席时报错） | `ensure`（bool，默认 false；true 会顺手拉起引擎） | `aria2.{installed,running,binary,port,version,active,downloads,waiting,stopped,downloadSpeed,options}` | no | 不适用 | no（本地守护进程状态） |
| 14 | `aria2_restart`（`aria2_restart`） | 重启引擎（换端口、清卡死任务） | 本地 aria2 | — | `aria2.{…同上}` | no | 不适用 | no（本地） |
| 15 | `aria2_configure`（`aria2_configure`） | 改引擎参数（除端口外立即生效） | 本地 aria2 | `split`、`maxConnectionPerServer`、`maxConcurrentDownloads`、`minSplitSizeMiB`、`maxOverallDownloadLimitKiB`、`port` | `aria2.{…}`（`options` 回显） | no | 不适用 | no（本地） |
| 16 | `aria2_add`（`aria2_add`） | 排队一个文件 | 本地 aria2 | `url`、`out`（引擎目录内的文件名，不能含 `/`） | `download.gid` | no | 不适用 | no（本地队列） |
| 17 | `aria2_tell`（`aria2_tell`） | 单个任务进度 | 本地 aria2 | `gid` | `download.{gid,status,completed,total,speed,name,path,error}` | no | 不适用 | no |
| 18 | `aria2_list`（`aria2_list`） | 全部任务（active+waiting+stopped） | 本地 aria2 | — | `downloads[]`：`{gid,status,completed,total,speed,name,path,error}` | no | 不适用 | no |
| 19 | `aria2_pause`（`aria2_pause`） | 暂停一个（给 `gid`）或全部（不给） | 本地 aria2 | `gid`（可省） | `downloads[]` | no | 不适用 | no（本地） |
| 20 | `aria2_unpause`（`aria2_unpause`） | 恢复一个或全部 | 本地 aria2 | `gid`（可省） | `downloads[]` | no | 不适用 | no（本地） |
| 21 | `aria2_cancel`（`aria2_cancel`） | 取消一个或全部，**并删临时文件** | 本地 aria2 | `gid`（可省） | `removed[]` + `downloads[]` | no | 不适用 | no（本地；会删临时文件） |
| 22 | `fetch_song_detail`（`song_detail`） | 歌曲详情/简介 | `music.pf_song_detail_svr` / `get_song_detail_yqq`（只给名字时先走搜索解析 mid） | `songMid`，或 `title`/`artist`/`album`（`duration` 收下但忽略） | `detail.{title,artist,album,songMid,albumMid,imageURL,description,genreTags[],language,labelOrCompany,releaseDate,duration,songId}` + `source/metadataSource/metadataFetchedAt/metadataConfidence/confidence` | no | 默认档案（web）；**名字→mid 的子搜索强制 android** | no |
| 23 | `fetch_album_detail`（`album_detail`） | 专辑详情 | `music.musichallAlbum.AlbumInfoServer` / `GetAlbumDetail`（只给名字时先搜索） | `albumMid` 或 `albumId`，或 `album`+`artist` | `detail.{album,artist,albumMid,imageURL,description,releaseDate,releaseYear,albumType,genreTags[],language,labelOrCompany,id,coverURL,title,songCount}` + 溯源键 | no | 默认档案；名字→mid 的子搜索强制 android | no |
| 24 | `fetch_album_tracks`（`album_tracks`） | 专辑曲目 | `music.musichallAlbum.AlbumSongList` / `GetAlbumSongList` | `albumMid` 或 `albumId`、`offset`、`limit`（1–200，默认 200）；内部 `begin/num/order:0` | `tracks[]` / `total`（源 `totalNum`） | no | 默认档案 | no |
| 25 | `fetch_artist_songs`（`artist_songs`） | 歌手歌曲 | `musichall.song_list_server` / `GetSingerSongList` | `singerMid`、`sort`（`hot`/`latest`，**latest 由组件本地按 `releaseDate` 排序**）、`page`、`limit`（1–100，默认 50）；内部 `order:1, number, begin` | `tracks[]`（latest 时每首带 `releaseDate`） | no | 默认档案 | no |
| 26 | `fetch_artist_albums`（`artist_albums`） | 歌手专辑 | `music.musichallAlbum.AlbumListServer` / `GetAlbumList` | 同上 | `albums[]`：`id`（源 **`albumID`**）/ `title` / `albumMid` / `coverURL` / `artist` / `releaseDate` | no | 默认档案 | no |
| 27 | `fetch_artist_detail`（`artist_detail`） | 歌手资料 | `music.UnifiedHomepage.UnifiedHomepageSrv` / `GetHomepageHeader` | `singerMid`（别名 `mid`）或 `name`（别名 `artist`）；内部 `SingerMid` | `detail.{singerMid,name,artistName,imageURL,coverURL,description,genreTags[]/genre[],region,foreignName,songCount,albumCount,fanCount,followCount}` + 溯源键；**空壳资料报错而不是回「未知歌手」** | no | **强制 android**（可被 `params.platform` 覆盖） | no |
| 28 | `fetch_toplist_categories`（`toplist_categories`） | 排行榜分组 | `music.musicToplist.Toplist` / `GetAll` | — | `toplistGroups[]`：`{id,name,title,source,toplists[]}`，榜内 `{id,name,title,coverURL,updateTime}`；**组内键名是 `toplist`（小写 l）** | no | 默认档案 | no |
| 29 | `fetch_toplist_tracks`（`toplist_tracks`） | 单个排行榜曲目 | `music.musicToplist.Toplist` / `GetDetail` | `topId`、`offset`、`limit`（1–300，默认 100）；内部 `num` | `tracks[]`（源 **`songInfoList`**）/ `total`（源 `totalNum`） | no | 默认档案 | no |
| 30 | `fetch_radio_stations`（`radio_stations`） | 电台分组 | `pf.radiosvr` / `GetRadiolist` | 无（内部 `uin` 用凭据数字 id，无凭据用 `"0"`） | `radioGroups[]`：`{id,name,title,source,stations[]}`，电台 `{id,title,coverURL,listenerCount,source}`；**分组 id 不是电台 id** | no | 默认档案（web） | no |
| 31 | `fetch_radio_tracks`（`radio_tracks`） | 电台曲目（每次都是新一批） | `pf.radiosvr` / `GetRadiosonglist` | `stationId`、`limit`（1–50，默认 20）、`firstPlay`（bool，默认 true）；内部 `id/firstplay/num` | `tracks[]` | no | 默认档案（web） | no |
| 32 | `fetch_new_songs`（`new_songs`） | 新歌（按地区） | `newsong.NewSongServer` / `get_new_song_info` | `regionType`（0 最新/1 内地/2 港台/3 欧美/4 日本/5 韩国），或 `region` 名字符串 | `tracks[]` | no | 默认档案 | no |
| 33 | `fetch_recommend_feed`（`recommend_feed`） | 猜你喜欢（每次 5 首） | `music.radioProxy.MbTrackRadioSvr` / `get_radio_track` | 无（内部 `id:99, num:5, from:0, scene:0, song_ids:[]`） | `tracks[]` | **yes（实测无凭据回 `1000` 登录已过期）** | **强制 android** | no |
| 34 | `fetch_lyric`（`lyric`） | 歌词（整行 + 逐字 + 翻译） | 整行：`GET c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg`（`nobase64=1`）；逐字/翻译：`music.musichallSong.PlayLyricInfo` / `GetPlayLyricInfo` | `songMid`、`songId`（收下）、`wordTiming`（bool，默认 **true**）、`translation`（bool，默认 **true**）；加密路内部 `crypt:1, qrc:1, trans:1, roma:0, type:1, needSingingAnnotations:false, lrc_t:0, qrc_t:0, roma_t:0, trans_t:0` | `lyric.{lyric, translation, romanization}`（整行明文 LRC；`romanization` 恒为 null）+ `wordLyric`（逐字 LRC，无逐字时为 `null`，**不是错误**） | no（匿名可读；VIP 曲目的取流才需登录） | 默认档案（fcgi 路与档案无关） | no |
| 35 | `resolve_song_url`（`resolve_song_url`） | 取可播流地址（按音质阶梯探测） | `music.vkey.GetVkey` / `UrlGetVkey` | `songMid`、`mediaMid`、`songType`、`quality`（`flac`/`320`/`128`/`aac`，给了就只探这一档，否则 flac→320→128→aac）；内部 `uin, filename[], guid, songmid[], songtype[], ctx:0` | `stream.{source,songMid,mediaMid,quality,extension,filename,url,expiration,playable,tried[]}`；不可播时 `playable:false` + `restriction`（`paid_required`/`device_restricted`/`ticket_required`/`unavailable`）+ `reason`，**不是错误** | **yes**（`require_login`） | **强制 android**（web 档案同曲只给 128） | no |
| 36 | `set_liked`（`set_liked`） | 加/取消「我喜欢」——**唯一的写操作** | `music.musicasset.PlaylistDetailWrite` / `AddSonglist`·`DelSonglist` | `songId`（数字 id，优先）或 `songMid`（别名 `mid`，组件先解析数字 id）、`songType`、`liked`（bool，默认 true）；内部 `dirId:201, tid:0, bFmtUtf8:true, v_songInfo:[{songId, songType}]` | `like.songId` / `like.liked` | **yes** | **强制 android**（换 comm 是这类写操作成败的关键） | **yes** |
| 37 | `search_track_artwork`（`search_track_artwork`） | 本地曲目的封面候选 | 内部走搜索 | `title`、`artist`、`album`、`limit`（1–10，默认 5）；三字段拼成一个查询串 | `candidates[]`：`{source,title,artist,album,songMid,albumMid,imageURL,duration,confidence}`（confidence 按名次 0.86 起、每名 −0.04、下限 0.50） | no | **强制 android**（搜索本身要设备会话） | no |
| 38 | `search_artist_artwork`（`search_artist_artwork`） | 本地歌手的封面候选 | 内部走搜索（`search_type=1`） | `name`、`limit`（1–10） | `candidates[]`：`{source,artistName,singerMid,imageURL,genreTags:[],region,confidence}` | no | **强制 android** | no |
| 39 | `search_album_artwork`（`search_album_artwork`） | 本地专辑的封面候选 | 内部走搜索（`search_type=2`） | `album`、`artist`、`limit`（1–10） | `candidates[]`：`{source,album,artist,albumMid,imageURL,releaseDate,confidence}` | no | **强制 android** | no |
| 40 | `fetch_artist_biography`（`fetch_artist_biography`） | 歌手简介（给本地曲库用） | 同 `fetch_artist_detail`（`GetHomepageHeader`） | `name`（别名 `artist`）或 `singerMid` | `artistDetail.{source,artistName,singerMid,description,imageURL,genreTags,region,foreignName,metadataSource,metadataFetchedAt,metadataConfidence,confidence}` | no | **强制 android** | no |
| 41 | `search_songs`（`search_songs`） | 搜歌 | `music.search.SearchCgiService` / `DoSearchForQQMusicMobile` | `keyword`（空串直接回空，不发请求）、`page`（≥1）、`limit`（1–50，默认 20）；内部 `search_type:0, num_per_page, page_num, searchid, highlight:false, grp:true, selectors:{}, vec_selectors:[]` | `tracks[]` / `total`（源 `meta.sum`；结果取 `body.item_song`） | no | **强制 android**（web 档案 `meta.sum=0`，实测） | no |
| 42 | `search_artists`（`search_artists`） | 搜歌手 | 同上 `search_type:1` | 同上（结果取 `body.singer`） | `artists[]`：`{singerMid,name,coverURL,songCount,albumCount,fanCount}` / `total` | no | **强制 android** | no |
| 43 | `search_albums`（`search_albums`） | 搜专辑 | 同上 `search_type:2` | 同上（结果取 `body.item_album`） | `albums[]`：`{id,title,albumMid,coverURL,artist,releaseDate}` / `total` | no | **强制 android** | no |
| 44 | `search_playlists`（`search_playlists`） | 搜歌单 | 同上 `search_type:3` | 同上（结果取 `body.item_songlist`） | `playlists[]`：`{id,title,coverURL,creator,songCount,playCount}` / `total` | no | **强制 android**（封面在 `logo` 键上） | no |
| 45 | `start_login`（`start_login`） | 开始 QQ 扫码登录 | `ssl.ptlogin2.qq.com/ptqrshow`（五步握手第一步） | — | `qrcode.{identifier,loginType:"qq",mimetype:"image/png",imageBase64}` | no | 不适用（浏览器 UA 直连） | no |
| 46 | `poll_login`（`poll_login`） | 问扫码进行到哪一步，最后一步落凭据 | `ptqrlogin` → `check_sig` → `graph.qq.com/oauth2.0/authorize` → `QQConnectLogin.LoginServer` / `QQLogin` | `identifier`（即 `qrsig`） | `login.{event,loggedIn,login?}`；`event` = `SCAN`/`CONF`/`DONE`/`TIMEOUT`/`REFUSE` | no | 最后一步 `QQLogin` 用 **android 档案 + `tmeLoginType:2`** | no（本地凭据落盘） |

**FFI 多出的一个导出**：`call_with_platform(method, params_json, platform)`（`src/api.rs:378-394`），不在 `METHODS` 里，返回原始 JSON 字符串——**这是绕过 §4 全部载荷不匹配问题的逃生口**，实测可用。

---

## 2. 数据模型清单（35 个 `#[data]` 结构，`src/models.rs`）

`#[serde(rename_all = "camelCase")]` 是全局规则；`imageURL` / `coverURL` 因为全大写缩写需要显式 rename。**Rust 字段 → JSON 键**：

| 模型 | 关键字段（Rust → JSON） |
|---|---|
| `RateLimitConfigModel` | `enabled→enabled`, `window_seconds→windowSeconds`, `max_requests→maxRequests` |
| `BreakerConfigModel` | `enabled→enabled`, `failure_threshold→failureThreshold`, `failure_window_seconds→failureWindowSeconds`, `open_seconds→openSeconds` |
| `Aria2Status` | `installed→installed`, `running→running`, `binary→binary`, `port→port`, `version→version`, `active→active`, `downloads→downloads`, `waiting→waiting`, `stopped→stopped`, `download_speed→downloadSpeed`, `options→options: Aria2OptionsModel` |
| `Aria2OptionsModel` | `split→split`, `max_connection_per_server→maxConnectionPerServer`, `max_concurrent_downloads→maxConcurrentDownloads`, `min_split_size_mib→`**`minSplitSizeMiB`**, `max_overall_download_limit_kib→maxOverallDownloadLimitKiB`, `port→port` |
| `Aria2Download` | `gid→gid`, `status→status`, `completed→completed`, `total→total`, `speed→speed`, `path→path`, `error→error`, `error_code→errorCode` |
| `Aria2TaskList` | `downloads→downloads`, `removed→removed` |
| `Aria2Task` | `gid→gid`, `status→status`（aria2 自己的词：active/waiting/paused/complete/error/removed）, `completed→completed`, `total→total`, `speed→speed`, `name→name`, `path→path`, `error→error` |
| `TrackPage` | `tracks→tracks`, `total→total: Option<i64>`（上游不报总数时为 `None`，与 0 不同） |
| `Singer` | `mid→mid: Option`, `name→name: Option` |
| `Track` | `song_id→songId`, `song_mid→songMid`（**必填 String**）, `media_mid→mediaMid`, `title→title`, `artist→artist`（多歌手用 `", "` 连接）, `album→album`, `album_mid→albumMid`, `album_id→albumId`, `image_url→`**`imageURL`**（显式 rename）, `duration→duration`（秒）, `pay_play→payPlay`（1=可能需要会员）, `singer_mid→singerMid`, `singers→singers: Option<Vec<Singer>>`, `release_date→releaseDate`（来源带 `time_public` 时才有） |
| `LikedSongs` | `title→title`, `total→total: i64`, `tracks→tracks` |
| `Album` | `id→id`, `title→title`, `album_mid→albumMid`, `cover_url→`**`coverURL`**, `artist→artist`, `release_date→releaseDate` |
| `Playlist` | `id→id`, `title→title`, `cover_url→`**`coverURL`**, `creator→creator`, `song_count→songCount`, `play_count→playCount` |
| `Artist` | `singer_mid→singerMid`, `name→name`, `cover_url→`**`coverURL`**, `song_count→songCount`, `album_count→albumCount`, `fan_count→fanCount` |
| `LoginStatus` | `logged_in→loggedIn`, `music_id→musicId`, `nickname→nickname`, `vip_type→vipType`, `expired→expired`, `has_playback_key→hasPlaybackKey` |
| `ComponentInfo` | `helper_version→helperVersion`, `protocol_version→protocolVersion`, `library_version→libraryVersion`, `methods→methods[]` |
| `GuardStatus` | `breaker→breaker: String`, `rate_limit→rateLimit: RateLimitUsage` |
| `RateLimitUsage` | `read→read`, `interactive→interactive`, `playback→playback`, `account→account`, `write→write` |
| `SongDetail` | `song_mid→songMid`（**必填**）, `song_id→songId`, `title→title`, `artist→artist`, `album→album`, `album_mid→albumMid`, `description→description`（简介，空即答案）, `genre→genre[]`, `language→language`, `company→company`, `release_date→releaseDate`, `duration→duration` |
| `AlbumDetail` | `id→id`, `album_mid→albumMid`, `title→title`, `artist→artist`, `cover_url→`**`coverURL`**, `description→description`, `release_date→releaseDate`, `genre→genre`, `language→language`, `company→company`, `song_count→songCount` |
| `ArtistDetail` | `singer_mid→singerMid`（**必填**）, `name→name`（**必填**）, `description→description`, `cover_url→`**`coverURL`**, `foreign_name→foreignName`, `region→region`, `genre→genre[]`, `song_count→songCount`, `album_count→albumCount`, `fan_count→fanCount` |
| `Toplist` | `id→id`, `title→title`, `cover_url→`**`coverURL`**, `update_time→updateTime` |
| `ToplistGroup` | `title→title`, `toplists→toplists: Vec<Toplist>` |
| `RadioStation` | `id→id`, `title→title`, `cover_url→`**`coverURL`** |
| `RadioGroup` | `title→title`, `stations→stations: Vec<RadioStation>` |
| `Lyric` | `lyric→lyric: Option`, `translation→translation: Option`, `romanization→romanization: Option`, `word_lyric→wordLyric: Option`（逐字 LRC） |
| `StreamResolution` | `song_mid→songMid`（**必填**）, `quality→quality`（`flac`/`320`/`128`/`aac`）, `filename→filename`, `url→url`, `playable→playable`, `reason→reason`（不可播时说明各档位答了什么） |
| `LoginQrCode` | `identifier→identifier`（**必填**，即 `qrsig`）, `login_type→loginType`, `mimetype→mimetype`, `image_base64→imageBase64`（PNG 的 base64） |
| `LoginPoll` | `event→event`（**必填**）, `logged_in→loggedIn`, `login→login: Option<LoginStatus>` |
| `TrackSearch` | `total→total: i64`, `tracks→tracks` |
| `ArtistSearch` | `total→total`, `artists→artists` |
| `AlbumSearch` | `total→total`, `albums→albums` |
| `PlaylistSearch` | `total→total`, `playlists→playlists` |
| `ArtworkCandidate` | `title→title`, `artist→artist`, `album→album`, `artist_name→artistName`, `singer_mid→singerMid`, `song_mid→songMid`, `album_mid→albumMid`, `image_url→`**`imageURL`**, `duration→duration`, `release_date→releaseDate`, `confidence→confidence: Option<f64>`（名次提示，判定由宿主自己做） |
| `ArtistBiography` | `artist_name→artistName`, `singer_mid→singerMid`, `description→description`, `image_url→`**`imageURL`**, `region→region`, `foreign_name→foreignName`, `confidence→confidence: Option<f64>` |

**注意**：`SongDetail.song_mid`、`ArtistDetail.singer_mid`/`name`、`StreamResolution.song_mid`、`LoginQrCode.identifier`、`LoginPoll.event` 是**必填**（非 Option、无 serde default），JSON 里缺这个键会直接反序列化失败——这正是 §4 里一批 FFI 包装报 `missing field` 的原因。

模型层没有 `#[serde(default)]`（`grep` 全文件无 default 属性）；`Option<T>` 字段缺失时按 serde 语义得到 `None`（这是 `album_detail` / `fetch_artist_biography` 能「成功」返回全 `None` 的结构性原因，见 §4）。

---

## 3. 其他可交付物（不占 46 方法表，但宿主会用到）

- **凭据文件**：`<data_dir>/Credential/qqmusic-credential.json`，键 `musicid`/`str_musicid`/`musickey`/`encrypt_uin`（`src/credential.rs:117-164`）。`store()` 先写 `.json.tmp` 再 rename，且保留不认识的键（refresh token 等）。
- **设备文件**：`<data_dir>/device.json`，存 QIMEI（q16/q36，24h TTL，`src/device.rs:41`）与设备会话 `{uid,sid,vkey}`（同样 24h，`src/device.rs:135-148`）。
- **目录配置**：宿主传 `configure(Configuration{data_dir, default_platform})`；stdio 用环境变量 `QQMUSIC_HELPER_NEXT_DIR`（覆盖）或 `QQMUSIC_HELPER_DIR`，默认 `~/Library/Application Support/kmgccc.player/QQMusicHelperNext`（`src/bin/stdio.rs:33-44`）。
- **子进程协议**：请求 `{"id","method","params"}` → 响应必带同一 `id` 与 `ok`；失败为 `{"id":…,"ok":false,"error":"…"}`（`src/bin/stdio.rs:226-234`）。stdout 只放协议 JSON，诊断走 stderr（`src/bin/stdio.rs:15-16`）。
- **进程生命周期**：stdio 版监听父进程 ppid 变化，宿主消失即退出并关掉它拉起的 aria2（`src/bin/stdio.rs:105-126`）。

---

## 4. 硬结论（每条注明出处）

### 4.1 上游与请求信封

1. **三个上游**：`POST https://u.y.qq.com/cgi-bin/musicu.fcg`（`{comm:{…}, req_0:{module,method,param}}`）、`GET c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg`（账号自己的歌单/收藏专辑**只有它**能取，`userid` 用数字 uin，`format=json` 不可省）、`GET c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg`（明文歌词，`nobase64=1`）——`src/upstream.rs:1-11,76,282-286`，`docs/endpoints.md:8-13`。
2. **`comm` 平台档案按接口分类选，选错不报错、只回空数据**：web（`cv 4747474 / ct 24 / platform yqq.json / needNewCode 1`）给账号列表、曲库详情、榜单、电台、新歌；android（`ct 11 / cv 14090008` + 设备身份、设备会话）给搜索、取流、收藏写、推荐流、歌手资料、封面匹配——`src/upstream.rs:57-75`，`docs/parsing.md:6-15`。**实测**：`search_songs` 在 web 档案回 `{"total":0,"tracks":[]}`、在 android 档案回 `total:999`（同一查询、同一凭据）。
3. **档案是在代码里按方法名硬编码的**，不是宿主默认值：`fetch_artist_detail`、`fetch_recommend_feed`、`set_liked`、`search_songs/artists/albums/playlists`、`search_*_artwork`、`fetch_artist_biography`、`resolve_song_url` 共 12 个方法强制 `Platform::Android`（`src/methods.rs:104-121`）；其余走高斯的「默认档案」。`params.platform` 可逐个覆盖。
4. **`configure(default_platform)` 在 dispatch 路径上目前是死配置**：`dispatch` 用的是 `crate::upstream::Platform::default()` 即 `Web`（`src/methods.rs:401`、`src/upstream.rs:34-39`），`default_platform()`（`src/lib.rs:79-84`）**除了测试没有任何调用点**（`grep` 全库只有定义与测试）。**实测**：把宿主的 default_platform 配成 Android 后调 `toplist_categories`，仍按 web 发出（配置没被读取）。宿主若要 android 档案只能用 `params.platform` 或 `call_with_platform`。
5. **android 档案要「设备身份 + 设备会话」两件事**：QIMEI 握手（RSA 包 AES 密钥 → AES-CBC 加密 → 双 MD5 签名 → POST `api.tencentmusic.com/tme/trpc/proxy`，缓存 24h，`src/device.rs:1-30,41,205-222`）与 `music.getSession.session / GetSession`（`param {"uid":"","vkey":0,"caller":0}`，得 `session.{uid,sid,vkey}`，`src/upstream.rs:392-444`）。搜索/推荐/歌手资料要的是**登录过的设备会话**，只有指纹不够——`docs/parsing.md:98-109`。
6. **所有封面 URL 一律被强制转 https**（`http://` 与协议相对 `//` 都改写），因为 macOS ATS 与 Android 9+ 会直接拒 http 图，表现为「封面静默空白」——`src/methods.rs:983-996`，`docs/parsing.md:78-83`。
7. **搜索标题/歌手名的 `<em>` 高亮标记由组件剥掉**（`highlight:false` 也挡不住某些路）——`src/catalog.rs:1429-1470`。
8. **失败语义**：`req_0.code` 非 0 或 `data` 为空都报错（`src/upstream.rs:245-259`）；老 fcgi 的 `data` 为空报「登录可能已过期」而不是空列表（`src/upstream.rs:293-298`）。
9. **鉴权码**：`g_tk = hash33(qm_keyst, seed=5381)`；扫码的 `ptqrtoken = hash33(qrsig, seed=0)`——种子不对是 **HTTP 403**，不是「算错一点点」（`src/credential.rs:14-31`，`src/login.rs:130-132`，`docs/parsing.md:111-120`）。

### 4.2 登录与凭据

10. **凭据只需要 `uin` + `qm_keyst` 两件**，不需要别的 cookie：`qm_keyst` 同时是会话票据与 CDN 播放票据，`g_tk` 由它推导——`src/credential.rs:175-198`，`src/api.rs:62-78`，`docs/parsing.md:90-96`。`import_cookies` 收 `cookies.uin` + `cookies.qm_keyst`（`qqmusic_uin`/`musicid` 可替代 uin）。
11. **凭据被拒时 `get_login_status` 报 `loggedIn:false` 而不是抛错**（「那不是失败，是一种答案」）——`src/methods.rs:626-657`，`docs/parsing.md:96`。
12. **扫码登录五步**：`ptqrshow`（拿图 + `qrsig`）→ `ptqrlogin`（状态，带 `ptqrtoken`）→ `check_sig`（拿 `p_skey`）→ `graph.qq.com/oauth2.0/authorize`（拿 `code`）→ `QQConnectLogin.LoginServer/QQLogin`（android 档案 + `tmeLoginType:2` 换凭据）；`check_sig`/`authorize` 的响应**不能跟重定向**（cookie 与 Location 本身就是结果）——`src/login.rs:1-18,92-121,173-257`，`src/upstream.rs:177-183`。事件码映射 `0/405=DONE, 66/408=SCAN, 67/404=CONF, 65/402=TIMEOUT, 68/403=REFUSE`（`src/login.rs:58-79`）。**微信扫码未实现**（`docs/endpoints.md:104`）。
13. **`GetFollowSingerList` 的 `HostUin` 收数字 uin 也可以**（与 encrypt_uin 同结果），所以关注歌手不依赖登录流程并不总产出的 `encrypt_uin`——`src/methods.rs:809-821`，`docs/parsing.md:45`。

### 4.3 解析与大小写（踩过的坑，必须照做）

14. **关注歌手的列表键是 `List`（大写 L）**——`src/methods.rs:838-840`；**歌手专辑的 id 键是 `albumID`（大写 ID）**——`src/catalog.rs:538-539`；**专辑曲目的列表键是 `songList`（大写 L）**——`src/methods.rs:878-885`；**排行榜分组键是 `toplist`（小写 l）、曲目在 `songInfoList`**（`data.data.song` 是展示行，没有 song mid，拿它解码会得到空列表）——`src/catalog.rs:718-790`；**我喜欢的曲目在 `songlist`、总数在 `dirinfo.songnum`**——`src/methods.rs:686-692`，`docs/parsing.md:17-49`。
15. **字段名在不同接口拼写不同**（`mid`/`songMid`/`songmid`、`interval`/`duration`），取值一律用候选键工具 `first_text/first_int/first_object/first_array`，**不要写死单键**（少一个拼写就是一张空表）——`src/upstream.rs:533-578`，`docs/parsing.md:47-48`。曲目多歌手是多个 `singer` 元素、分隔符统一 `", "`（`src/methods.rs:894-911`）。
16. **总数键每个接口都不一样**：我喜欢/歌单 `dirinfo.songnum`；排行榜/专辑曲目 `totalNum`；搜索 `meta.sum`——`docs/parsing.md:50-63`。没有总数的接口按「取到空页为止」，不要把本页条数当总数。
17. **收藏专辑的 `pubtime` 是北京时间零点**（抽查八张全部恰为 16:00Z，如流浪地球 → `2019-02-04T16:00Z`），按 UTC 渲染每张都早一天；组件在解析处 +08:00 转成 `YYYY-MM-DD`——`src/methods.rs:935-954`，`docs/parsing.md:31-35`。
18. **专辑曲目地址参数是驼峰 `albumMid`/`albumId`/`begin`/`num`**（`src/catalog.rs:424-436`）；**专辑详情按 mid 时参数名是 `albumMId`（大写 I）**（`src/catalog.rs:324-331`）——两者不是同一个拼写。
19. **`decoded_tracks` 的候选列表键**：`songlist`/`songList`/`songs`/`list`/`tracks`/`track_list`/`songInfoList`，条目内可取 `track`/`song`/`songInfo` 包一层——`src/methods.rs:871-887`。缺 `mid` 的条目被整条丢弃（`filter_map`）。
20. **封面 URL 的拼接规则**：专辑 `T002R800x800M000<albumMid>.jpg`、歌手 `T001R300x300M000<singerMid>.jpg`（`src/methods.rs:974-981`）；歌单搜索结果的封面在 **`logo`** 键上，不在 `imgurl`/`picurl`（`src/catalog.rs:1479-1484`）。

### 4.4 逐字歌词的形态

21. **逐字数据只在加密路**：`GetPlayLyricInfo` 带 `crypt:1, qrc:1` 时 `lyric` 字段不再是 base64 LRC 而是 **hex 编码密文**（`trans` 同样是密文）；明文 fcgi 路只有整行歌词，拿不到逐字——`src/catalog.rs:920-999`，`docs/endpoints.md:61-85`。**判断 hex 还是 base64 要靠内容**，把密文当 base64 解会渲染出垃圾而不是抛异常（`docs/parsing.md:122-127`）。
22. **密文是 QQ 私有的「类 DES」**（S/P/E 盒与密钥位序都是私有，不是标准 DES），三重 `D(K3)→E(K2)→D(K1)`，解出带 BOM 的 zlib，再解压得到 XML 壳，正文在一个属性或 CDATA 里——`src/qrc.rs`（文件头与 `the_first_block_matches_the_reference_vector` 用 AMLL 官方向量做 known-answer 测试，`src/lib.rs:172-188`）。
23. **组件交付的逐字形态是「词级 LRC」：每个词前都带一个时间戳**，例如 `[00:01.00]你[00:01.20]好`（`src/qrc.rs:507-541`，测试断言在 `src/qrc.rs:603-616`）。选这个形状的理由是宿主歌词读取器把「一行多个时间戳」当作多个词并生成逐字 TTML，**宿主不必为逐字做任何特殊处理**——`docs/endpoints.md:73-85`。**实测**：`fetch_lyric` 对《晴天》返回的 `wordLyric` 为 `[00:00.00]晴[00:00.16]天[00:00.48]-[00:00.80]周…`。
24. **`fetch_lyric` 一次答两个字段**：`lyric`（整行，明文路，兜底）与 `wordLyric`（逐字，有则给）；**没有逐字不是错误**（老歌往往没有），那时 `wordLyric` 为 `null`。翻译优先取加密路解出来的那份（明文路的 `trans` 常常是空的）——`src/methods.rs:227-266`，`docs/endpoints.md:83-85`。`wordTiming`/`translation` 默认都是 true（各多花一次加密路往返）。
25. **组件把明文路的 `trans` 与加密路的翻译合并**：加密路有值时覆盖 `lyric.translation`（`src/methods.rs:252-259`）；`romanization` 字段存在但恒为 `null`（`roma` 请求参数被写死 0，`src/catalog.rs:966-977`，`src/catalog.rs:1025-1029`）。

### 4.5 限流与熔断（在组件内）

26. **限流与熔断都在组件里**，因为组件是唯一看得见全部流量的地方——`src/guard.rs:1-18`，`docs/parsing.md:143-147`。
27. **限流是按内容类别分桶的固定窗口**：`Read=30 / Interactive=12 / Playback=20 / Account=12 / Write=6` 次每 10 秒（`src/guard.rs:40-51,98-109`）；**超限是等待而不是丢弃**（「每次调用都是用户看得见的读取，延迟比失败好」）——`src/guard.rs:146-190`。全局上限（`set_rate_limit`）是**额外的**一道，关掉它只剩分桶（`src/guard.rs:62-66`）。
28. **熔断**：`failure_window` 内连续失败达 `failure_threshold`（默认 5 次/60 秒）开路 `open_for`（默认 30 秒），期间立即失败并带剩余秒数；半开只放一个探测（`src/guard.rs:212-328`）。**改配置会同时清空熔断状态**（否则「设置像没生效」）——`src/guard.rs:264-274`，`src/methods.rs:493-533`。熔断开启时调用返回 `HelperError::Throttled`（`src/lib.rs:144`）。
29. **配置是「推送」不是「持久化」**：`set_rate_limit`/`set_breaker` 由宿主在启动与设置变化时推，**组件退出即忘**（`src/methods.rs:462-533`，`docs/parsing.md:146-147`）。`get_status` 回显生效值与当前用量（`src/methods.rs:419-445`）。
30. **限流/熔断在 FFI 面上完全没有入口**：`api.rs::set_rate_limit`/`set_breaker`/`guard_status` 走 `methods::dispatch`，而 `dispatch` 不实现这三个方法（见 §0、§4.7）。

### 4.6 写操作与空值语义

31. **唯一的写操作是 `set_liked`**（加/取消「我喜欢」）——`src/methods.rs:68-69`、`src/catalog.rs:1257-1312`、`README.md:53`。它要求**数字 `songId`**；宿主只有 mid 时组件先用 `song_detail` 解析（`src/catalog.rs:1272-1295`）。`dirId:201, tid:0, bFmtUtf8:true, v_songInfo:[{songId,songType}]`（`src/catalog.rs:1303-1308`）。`aria2_*` 只改本地下载引擎与临时文件（`aria2_cancel` 会删临时文件），不动 QQ 服务端数据。
32. **空值有两种相反含义，判据是「空是不是一种合法的正常状态」**：整表读取（账号列表、收藏）里空列表＝端点形状变了＝**不是答案、要报错**（否则会用空列表覆盖宿主缓存里的真数据）；歌曲简介里的空＝这首歌本来就没有简介＝**就是答案**、照常返回——`docs/parsing.md:130-137`，`src/upstream.rs:293-298`，`src/catalog.rs:249-264`。
33. **风控码要被当成错误**：上游被限流时回的是一份「成功但空」的结果集；组件对 `2000/2001/1000/104401/104400` 一律报错（`2001` = 触发风控，`2000` = 需要签名，其余 = 登录已过期），**即使同时带回了 `data` 也报错**——`src/upstream.rs:245-252,521-531`，`docs/parsing.md:139-141`。**实测**：无凭据调 `fetch_recommend_feed` 得到 `上游返回错误（1000）：登录已过期，请重新登录`。
34. **取流的「不可播」是答案不是错误**：按 flac→320→128→aac 逐级探测，取到就返回；全不给时 `playable:false` + `reason` + `restriction`（`paid_required`/`device_restricted`/`ticket_required`/`unavailable`），按每档 `result` 分类：`0` 授予、`104003` 无权限、`104004` 取票失败、`104013` 设备受限——`src/catalog.rs:25-40,1041-1159`。
35. **VIP 取流必须登录**：`resolve_song_url` 先 `require_login`，无凭据报「需要登录后才能读取」——实测确实如此（`src/catalog.rs:1050`）。
36. **多前缀请求时 `result=101404` 且 purl 为空是短时限流**，不是权限不足（`result=22` 才是未登录）——`docs/parsing.md` 之外的结论在 `src/catalog.rs` 的注释与 ladder 里；本项目 AGENTS.md 记录的同一现象与组件实现一致。
37. **电台是无穷列表**：同一请求每次回一批新轮换（`firstPlay` 控制是否首播），没有总数——`src/catalog.rs:848-873`，`docs/endpoints.md:51`。

### 4.7 实测发现的接口契约问题（**集成前必须知道**）

38. **stdio 子进程面是完整可用的面**。实测（用仓库自带 `target/release/qqmusic-helper-next`）：
    ```
    {"id":"1","method":"set_rate_limit","params":{"enabled":true,"windowSeconds":10,"maxRequests":100}}
    → {"id":"1","ok":true,"rateLimit":{"enabled":true,"maxRequests":100,"windowSeconds":10}}
    {"id":"3","method":"get_status","params":{}} → status.breaker="closed" + rateLimit 五项用量
    {"id":"4","method":"aria2_status","params":{"ensure":false}} → aria2.{installed:false,running:false,options:{…}}
    {"id":"6","method":"set_rate_limit","params":{"enabled":true,"windowSeconds":0,"maxRequests":99999999}}
    → {"rateLimit":{"enabled":true,"maxRequests":100000,"windowSeconds":1}}   # clamp 生效
    ```
39. **FFI 面 47 个导出里 31 个不可用**（实测全部 47 个，见下）：
    - **11 个返回 `不支持的方法`**（命令面方法没有库内入口，只在 stdio 的 `serve()` 被拦截）：`set_rate_limit`、`set_breaker`、`aria2_status`、`aria2_restart`、`aria2_configure`、`aria2_add`、`aria2_tell`、`aria2_list`、`aria2_pause`、`aria2_unpause`、`aria2_cancel`。实测：`rate_limit: ERR 不支持的方法：set_rate_limit`、`aria2_list: ERR 不支持的方法：aria2_list` 等。
    - **18 个返回的 JSON 与模型不符**（`api.rs::parse` 只解包 7 个方法的信封键：`likedSongs`/`login`/`helper`/`playlists`/`albums`/`artists`/`tracks`，`src/api.rs:41-53`；其余方法返回的 `detail`/`toplistGroups`/`radioGroups`/`candidates`/`stream`/`qrcode`/`status`/`artistDetail` 信封被原样塞给模型，于是报 `missing field` / `invalid type`）：
      `song_detail`（`missing field songMid`，**实测于真实上游**）、`artist_songs`、`artist_albums`、`artist_detail`、`toplist_categories`（**实测**）、`toplist_tracks`、`radio_stations`、`radio_tracks`、`new_songs`、`recommend_feed`、`lyric`（**实测**）、`resolve_song_url`、`start_login`（**实测**）、`poll_login`、`search_track_artwork`、`search_artist_artwork`、`search_album_artwork`、`guard_status`（**实测** `missing field breaker`）。
    - **2 个静默返回全 `None` / 空值**（字段全 Option，缺键不报错——比报错更危险）：`album_detail`（实测真实上游返回 `AlbumDetail{id:None,…,song_count:None}`，而同一请求的原始 `detail` 里 title/description 都有）、`fetch_artist_biography`（同样全 `None`）。
    - **可用的 16 个**（实测反序列化成功、字段非全空）：`get_helper_info`(component_info)、`get_login_status`(login_status)、`import_credential`、`logout`、`liked_songs`、`playlist_tracks`（`total` 被 FFI 丢弃，模型是 `Vec<Track>`）、`user_playlists`、`liked_albums`、`followed_artists`、`album_tracks`、`search_songs`、`search_artists`、`search_albums`、`search_playlists`、`set_liked`、`call_with_platform`。**注意 `album_detail`/`fetch_artist_biography` 不在此列**——它们反序列化「成功」但字段全 `None`（归入上面 31 个不可用之列）。
    - **绕过办法**：FFI 用 `call_with_platform(method, params_json, platform)` 拿原始 JSON 自己解析（实测可用）；或直接把组件当子进程驱动（`docs/ffi.md:90-102` 明确说两条路走同一份 `methods::dispatch`，但实测**命令面方法并不在 `dispatch` 里**，`api_surface_matches` 测试只检查「每个 METHODS 项在 api.rs 里有同名 `pub fn`」，不检查载荷能否解析——`src/api.rs:149-215`）。
40. **`api_surface_matches` 测试会漏掉这些不一致**：它只断言方法名在 `api.rs` 源码里出现过（`source.contains("pub fn {wrapper}")`），不断言模型能从 dispatch 载荷反序列化出来（`src/api.rs:150-215`）。所以「测试通过」不代表 typed API 可用。
41. **`album_detail` 的「成功但全 None」是 FFI 层丢的，不是上游没有**：组件自己的注释承认 `GetAlbumDetail` 会按 mid/id 以不同名字返回专辑（`basicInfo`/`albumInfo`/`album`/`data`，`src/catalog.rs:354-358`）；实测按 mid 请求的原始载荷里 `detail.album`/`detail.albumMid`/`detail.description` 都有值，而 typed `album_detail()` 返回的对象每个字段都是 `None`。
42. **`fetch_artist_detail` 空壳资料报错**（不伪装成「未知歌手」）：当 header 返回 shaped-but-empty 的 singer 时报「与搜索、推荐同因：可能缺设备标识」——`src/catalog.rs:632-641`。**实测**：无设备会话时 `fetch_artist_detail`/`fetch_artist_biography` 都得到此错误（`fetch_artist_biography` 在 FFI 上还先被 §39 的信封问题挡住）。
43. **超时与并发**：导出的 FFI 函数是**同步阻塞**的（最长 20 秒全局超时，`src/upstream.rs:125-131`；`docs/ffi.md:62` 写的是 12 秒，与源码不符——以源码 20s 为准）。stdio 适配器每请求起一个线程，宿主可并发（`src/bin/stdio.rs:86-93`）；线程安全靠共享 agent + 锁（`src/lib.rs:32-36`）。
44. **加载面**：`configure()` 第一次调用生效、之后忽略（`src/lib.rs:86-90`，测试 `the_first_configuration_wins`）；`QQMUSIC_HELPER_NEXT_PLATFORM` 环境变量只在 stdio 里读（`src/bin/stdio.rs:52-55`）。

### 4.8 与本工程（NeuMusic）的对照要点

45. 本工程现有 `data/api/` 已原生直连同类端点（`music.srfDissInfo.DissInfo`/`CgiGetDiss`、`music.musichallAlbum.*`、`music.vkey.GetVkey`、`music.musicasset.PlaylistDetailWrite`、搜索接口等，见 AGENTS.md「数据架构」节），因此本组件**不是能力缺口**；它值得参考的是：QIMEI/设备会话的完整实现（`src/device.rs`，本工程目前未做）、qrc 解密与逐字 LRC 的独立实现（本工程 `QrcCodec` 已有）、以及限流/熔断分桶策略。
46. **协议一致的部分**：`v_songInfo:[{songId, songType}]` + `dirId:201, tid:0, bFmtUtf8:true`（`src/catalog.rs:1303-1308`）与本工程 AGENTS.md 记录的写接口参数一致；`result=104003` 的档位级语义与降级链（`src/catalog.rs:25-40,1091-1109`）也与本工程实测一致。
47. **不一致/需注意的部分**：组件把「无权限/受限」做成 `playable:false` 的**正常返回**（本工程是错误路径）；组件的 `album_detail`/`artist_detail` 参数名 `albumMId`/`SingerMid`（大小写）与本工程记录一致；组件的歌词走 `c.y.qq.com` 明文 + `PlayLyricInfo` 加密双路（本工程只用 `PlayLyricInfo`）。

---

## 5. 未验证 / 存疑（如实标注）

- **未跑任何 QQ 上游的写操作**（`set_liked`）——按 AGENTS.md 的限流风控教训，本次只读，不触发写。`set_liked` 的行为结论全部来自源码（`src/catalog.rs:1257-1312`）与文档（`docs/endpoints.md:57`），未实测。
- **未验证需要真实登录的读取**（`fetch_liked_songs`/`fetch_liked_albums`/`fetch_user_playlists`/`fetch_followed_artists`/`fetch_playlist_tracks`/`resolve_song_url` 的成功路径、登录后的 `fetch_recommend_feed`）。本次只跑到「无凭据时报需要登录」（实测 `playlist_tracks`/`user_playlists`/`liked_albums`/`followed_artists`/`set_liked` 均返回 `需要登录后才能读取`），确认了鉴权门存在，但不构成成功路径的证据。
- **未验证 `poll_login` 的 DONE 路径**（需要真人扫码）；实测只到「bogus qrsig 被 403 拒绝」。
- **未验证 aria2 真正拉起后的状态/任务字段**（本机 `/tmp/hn_inventory_run/aria2-next` 不存在，引擎缺席），只验证了引擎缺席时的 `aria2.{installed:false,running:false}` 与 dispatch/stdio 分支。
- **`docs/endpoints.md` 与源码有小的文字出入**：文档写 `fetch_artist_songs` 的「最新由组件按 `time_public` 排序」，源码实际用 `releaseDate`（由 `time_public`/`publishDate` 取值后排序，`src/catalog.rs:487-502`）——不矛盾，但措辞不同。
- **`docs/ffi.md:62` 的「最长 12 秒超时」与源码的 20 秒全局超时不符**（`src/upstream.rs:129`）——以源码为准。
- **`docs/endpoints.md:100` 说 `aria2_pause/unpause/cancel` 「单个给 `gid`、全部不给」**，与源码一致（`src/methods.rs:600-612`）；但 stdio 与 FFI 的 `aria2_*` 可用性不同（§39），文档没有区分。
