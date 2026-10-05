# 目录与搜索域 · 独立复核（find counterexamples, read-only）
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

被核结论：`/tmp/helpernext-workflow-cred/probe-output.json` 之外的「helpernext ↔ 本工程目录/搜索域对照表」20 条（id 0–19）。
本复核只读两侧源码，未发起任何网络请求，未运行 gradle / adb / 模拟器。

## 复核时点的两侧版本（复核基准，重要）

- 工程侧：`/Users/mac/Documents/Music_app` @ `b6c5c29`（2026-10-02 13:34），`app/src/main` 工作区无改动（`git status --porcelain -- app/src/main` 为空）。
- 组件侧：`/Users/mac/Documents/QQMusicApi_HelperNext` @ `5cb2e82`（2026-10-03 00:37）。
  - 工作区有一个**未提交**改动：`src/port/comment.rs`（`git status --porcelain` 显示 ` M src/port/comment.rs`），只涉及 comment 域，不影响本域任何行号。
- **关键发现（影响所有引用行号）**：被核结论引用的许多组件行号来自上一版提交 `2ff7e71`（2026-10-01），其后 `2448cec`/`5cb2e82` 两个 port 提交给 `src/api.rs` +8 行、`src/upstream.rs` +140 行、`src/methods.rs` 的 `unused import` 变化使 methods.rs 行号整体位移 **+13 行**（catalog.rs 未变）。因此结论里 `src/methods.rs:889-933`（实为 902-946）、`:921`（实为 934）、`:661-693`（实为 674-706）、`:690`（实为 703）、`:744-773`（实为 757-786）、`:776-801`（实为 789-814）、`:804-855`（实为 817-868）、`:625-657`（实为 638-670）、`src/api.rs:274`（实为 282）、`src/api.rs:41-53`（实为 49-61）、`src/upstream.rs:273-299`（实为 343-369）等均为**旧版行号**。内容本身在 2ff7e71 上都能对上（我逐条用 `git show 2ff7e71:` 验证过），但按当前 HEAD 读会指错位置。下面每条按「结论是否成立」判定，行号问题在 evidence 里如实写明。

判定口径：`confirmed` = 结论成立；`refuted` = 有反例 / 陈述有误；`unclear` = 证据不足。

---

## id 0 — 搜索·歌曲（含分页）| partial → **confirmed**（有一处行号/细节需修正）

**核到的证据**
- 组件：`src/methods.rs:305-321`（HEAD）确实是 `search_songs|search_artists|search_albums|search_playlists` 同一 arm，`int("page").unwrap_or(1)`、`round(int("limit"), 20, 1, 50)`；`src/catalog.rs:1361-1420` 同一 `music.search.SearchCgiService/DoSearchForQQMusicMobile`，`body.item_song`（`SearchKind::body_key`，catalog.rs:1339-1346），`meta.sum` 取总数（catalog.rs:1409-1411，缺失退化 `items.len()`）。
- `<em>` 这一句订正：组件请求固定 `"highlight": false`（catalog.rs:1387），且 `strip_highlight`（catalog.rs:1468-1470）**只对 artist/album/playlist 三个 mapper 调用**（:1434、:1451、:1476）；歌曲走 `decoded_tracks`（catalog.rs:1414 → methods.rs:902-946），标题**不剥**。工程侧搜索歌曲也没剥（`QqMapper.kt:52` 只读 `name/songname`），而且工程对歌曲反而**开着高亮**（`SearchApi.kt:27` `.put("highlight", type == 0)`，type=0 的歌曲为 true）——所以「两侧名字里都可能留 `<em>`」在工程侧是既有事实。结论把这句写成「`<em>` 已剥……都对得上」，措辞不准（对歌曲不成立、也不是「都对得上」），但不改变 ①–④ 四条的判定。
- 组件 `Track` 无 fileSizes/genre：`src/methods.rs:902-946`（`decode_track` 输出 JSON 只含 songId/songMid/mediaMid/title/artist/album/albumMid/albumId/imageURL/duration/payPlay/singerMid/singers）；`src/models.rs:131-155`（Track 结构体同字段）。工程侧门槛：`ui/common/TrackDialogs.kt:210`（`if (track.fileSizes.isNotEmpty()) ShownDialogRow("查看格式")`）、`ui/common/TrackDialogs.kt:236`（`fileSizes.forEach`）；智能调音 `player/SmartEq.kt:55`（`if (track.genre == 0) … 本曲无风格数据`）← 调用于 `player/PlayerHost.kt:195`。→ 差异 ① **成立**。
- mediaMid 恒 null：`src/methods.rs:934`（旧版 921）
  `"mediaMid": first_int(track, &["media_mid"]).map(|_| ()).and(first_text(track, &["media_mid", "mediaMid"]))`
  上游 `media_mid` 是字符串（工程侧 `QqMapper.kt:35` 读 `file.media_mid`），`first_int` 必然 `None`；`.and(...)` 为 `None` 时短路，整体恒 `None` → JSON `null`。probe 实测 `firstHasMediaMid:false`（probe-output.json 第 3 行 `fetch_liked_songs`）→ 差异 ② **成立**。工程侧影响：`SongApi.kt:102` 用 `track.mediaMid` 拼 `filename`，工程 `QqMapper.kt:35` 有 `?: mid` 回退，组件宿主需自补回退。
- 强制 android：`src/methods.rs:104-121`（`search_*` 在 `matches!` 名单内，默认 `Platform::Android`），文档 `docs/endpoints.md`「android 档案 —— 搜索…」→ 差异 ③ **成立**。
- limit 20/50：`src/methods.rs:319`（`round(int("limit"), 20, 1, 50)`）；工程 `SearchApi.kt:36/45/63/79` 默认 `num=30`，`SearchScreen.kt:143-157` 每页 30、触底 `appendMore()`（`SearchScreen.kt:219`）。→ 差异 ④ **成立**。
- 关于「total 可取代工程的『本页 0 条=到底』推断」：工程**已经是 page 循环 + 本页条数判断**（`SearchScreen.kt:158-170`），没有 total。结论这半句方向正确。

**判定**：confirmed（差异 ①–④ 属实；唯「`<em>` 已剥」在歌曲上用词不准，行号需从 2ff7e71 平移到 HEAD）。

---

## id 1 — 搜索·歌手 | covered → **confirmed**

**核到的证据（抽查类：搜索）**
- 组件 `src/catalog.rs:1430-1444`（`map_artist`）：`singerMid / name(剥 <em>) / coverURL(https 化) / songCount / albumCount / fanCount`。名字剥离 :1434-1436，封面 https :1437-1439。
- 工程 `SearchApi.kt:45-60`：`mid = singerMID`、`id = singerID`、`name(cleanHighlight)`、`pic.toHttps()`、`songNum`、`albumNum`。逐字段对上（`fanCount` 组件多给，工程 `SearchSinger` 未收，不构成缺口）。
- 「`SearchSinger.id` 组件不给但全工程无人读」——反证检查：
  - `grep -rn "SearchSinger(" app/src/main` 只命中 `Models.kt:52` 定义与 `SearchApi.kt:51` 构造，无第三处；
  - `grep -rn "\.id\b"` 命中项逐条看过：`HomeScreen.kt:165/222/250/374`、`HomeCache.kt:124/140`、`AppRoot.kt:645/649/651/656/660` 全是 `RadioStation.id` / `Nav.RadioDetail.id`；`SearchScreen.kt:651` 歌手卡只传 `s.name/pic/songNum/albumNum`；`AppRoot.kt:421/613` 也只取这四项。→ **组件不给 `id` 无影响，结论成立**。
- 精确名优先在 `SingerApi.kt:29-32`（`list.firstOrNull { it.name == name } ?: list.firstOrNull()`）→ 成立。

**判定**：confirmed。

---

## id 2 — 搜索·专辑 | partial → **confirmed**

**核到的证据（抽查类：搜索）**
- 组件 `src/catalog.rs:1446-1463`（`map_album`）：`id / title(剥 em) / albumMid / coverURL / artist / releaseDate`。**没有 `song_num`**（可以从上游 `item_album` 读，那里确实有；组件没带出来）。
- 工程 `SearchApi.kt:68-74`：`songnum = o.optInt("song_num", 0)`；`Cards.kt:152-155`（`if (count > 0) … "$count 首"`）→ count 为 0 时不渲染，卡片右侧「N 首」消失 → 结论成立。
- 封面回退：`catalog.rs:1455-1459`（`first_text(pic, coverURL)` 缺失时 `album_cover_url(mid)`，`methods.rs:992-994` 为 `T002R800x800M000{mid}.jpg`，:1000-1009 https 化）→ 成立。
- 附注：`Cards.kt:158` 这个行号在任何一版都对不上「N 首」门槛所在的语句——该语句在 `e7e88c9` 是 `Cards.kt:87`、自 `43d8213` 起至当前 HEAD 都是 `Cards.kt:152`（`git show 43d8213:…/Cards.kt | grep -n "count > 0"` 与 `grep -n "count > 0" <工作区>` 均得 152；HEAD 的 `:157` 是 `line2` 判空、`:158` 落在 line2 的 Text 里）。功能判断（count 为 0 不渲染）不受影响，只是引用行号需改为 **Cards.kt:152**。

**判定**：confirmed（行号 `Cards.kt:158` 应改 :152）。

---

## id 3 — 搜索·歌单 | covered → **confirmed**

**核到的证据（抽查类：搜索）**
- 组件 `src/catalog.rs:1472-1489`（`map_playlist`）：`id = first_int(dissid..)`、`title(剥 em)`、`coverURL`（`logo` 优先，:1480-1484 注释明确 logo 是本路封面键）、`creator`、`songCount`、`playCount`。
- 工程 `SearchApi.kt:82-89`：`tid = dissid`、`name`、`logo`、`songnum`。`PlaylistItem`（Models.kt:40）只有 `tid/name/logo/songnum`，组件多给 creator/playCount 不构成缺口；`tid` 与 `fetch_playlist_tracks` 的 `songlistId` 同型（methods.rs:720）。→ 成立。
- 封面 https 化：`catalog.rs:1482` + `methods.rs:1000-1009` → 成立。

**判定**：confirmed。

---

## id 4 — 曲目字段合集（fileSizes / genre / mediaMid）| gap → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:884-946`（`decoded_tracks` + `decode_track`，结论写的 889-933 是旧版）与 `src/models.rs:131-155`（`Track`）里没有任何 `file` / `size_*` / `genre` 字段；`grep -rn "size_\|fileSizes\|genre" src/` 结果：`size_` 只出现在 aria2 的 `min_split_size_mib`，`genre` 只出现在 SongDetail/AlbumDetail/ArtistDetail 三个**详情**模型（models.rs:303/323/341）与 `catalog.rs` 的 `content_group(info,"genre")`（:251），**曲目列表路径完全没有**。→ 「上游有、组件丢弃」属实：上游 `file.size_*` / `genre` / `media_mid` 在工程侧确实从同一响应里取到（`QqMapper.kt:34-48`、:61；`QqMapper.kt:35`）。
- mediaMid 恒 null：方法同 id 0（methods.rs:934）。
- 工程侧三项消费已在上文定位：`SongApi.kt:56-58/87-94`（fileSizes 过滤降级链）、`TrackDialogs.kt:210/236`（查看格式）、`SmartEq.kt:55-59`（智能调音）。→ 结构性缺失，结论成立。

**判定**：confirmed。

---

## id 5 — 我喜欢列表（分页 + 总数）| partial → **confirmed**

**核到的证据（抽查类：我喜欢）**
- 组件 `src/methods.rs:674-706`（结论写 661-693 是旧版）：同 `music.srfDissInfo.DissInfo/CgiGetDiss`、`dirid = LIKED_SONGS_DIRID = 201`（:27, :690）、总数 `dirinfo.songnum`（:703）。**page 语义**：`song_begin = limit*(page-1)`（:692）、`limit` 默认 50、clamp 1–100（:681）。
- 工程 `PlaylistApi.kt:31-43`（`likedPage`）用 **offset**，且 `LikedStore.kt:41` 每页要 **300**（`PlaylistApi.likedPage(offset, 300)`；页大小 300 超出组件上限 100）→ 宿主必须改成 page 循环，结论成立。`LikedStore.kt:40-47` 的循环确实是 offset 累加 300。
- 不需 euin：组件 `liked_songs` 只调 `require_login`（:679），param 无 `enc_host_uin`（:685-697）；工程 `PlaylistApi.kt:24-28`（`requireEuin()`，`cred.euin.isEmpty() → throw`）与 `LikedStore.kt:32`（`if (Prefs.credential?.euin.isNullOrEmpty()) return`）确实硬依赖 euin → 「收益」成立。
- 曲目字段缺口 → 见 id 4。
- 实测 `total:479 got:3 firstHasMid:true`：probe-output.json `fetch_liked_songs` 行完全一致 → 成立。

**判定**：confirmed。

---

## id 6 — 我喜欢总数（主页卡片）| covered → **confirmed**

**核到的证据（抽查类：我喜欢）**
- 组件 `src/methods.rs:703`：`total: first_int(&info, &["songnum","song_num","total"]).unwrap_or(tracks.len() as i64)` —— **total 恒有值**（缺失退化为本页条数）。`page:1, limit:1` 合法（`clamp(1,100)`，:681）。
- 工程 `HomeScreen.kt:236`（`PlaylistApi.likedPage(0,1).total`）、`HomeScreen.kt:516`（`if (count != null) "$count 首" else "—"`）→ 语义差异（"—" vs 组件必给）成立；宿主若要保留「—」需自己判断，结论成立。
- 附注：组件退化值是**本页条数（1）**而不是 0，所以「恒有值」正确。

**判定**：confirmed。

---

## id 7 — 收藏的歌单 | partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:757-786`（结论写 744-773 是旧版）走 `upstream.profile_assets(credential, 3, limit)`（:764）→ 老 fcgi `https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg`，`reqtype=3`（upstream.rs:343-369 现 HEAD；结论写 273-299 是旧版）。字段 `id/title/coverURL/creator/songCount/playCount`（:775-783）。
- 工程 `PlaylistApi.kt:63-70` 走 `music.musicasset.PlaylistFavRead/CgiGetPlaylistFavInfo`（`v_list[]`）——**确实是不同端点**，同一账号是否同一集合本次未验证 → 结论如实，成立。
- **没有 offset/page 分页**：`profile_assets` URL 固定 `sin=0&ein={limit}`（upstream.rs:354/283），`user_playlists` 只读 `limit`（methods.rs:763，clamp 1–100）→ 成立（工程 `PlaylistApi.kt:63` 有 page 参数但 `PlaylistsScreen`（Screens.kt:613）只取第一页，影响相同）。
- 「我喜欢」保留目录被跳过：:771-774（`let id = …?; if id <= 0 { return None }`）→ 成立。
- 实测 `count:4`：probe-output.json `fetch_user_playlists` 行一致。

**判定**：confirmed。

---

## id 8 — 收藏的专辑 | partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:789-814`（结论写 776-801 是旧版）：`profile_assets(credential, 2, limit)`；字段 `id/title/albumMid/coverURL/artist/releaseDate`（:803-811），**没有 songnum** → 成立。
- 工程 `HomeScreen.kt:590`（`"${item.songnum} 首"`，不做 0 判断）、`Screens.kt:667`（专辑列表页 `subtitle = "${a.songnum} 首"`）→ 组件宿主不补就是「0 首」，成立。
- `pubtime` 北京时间转 `YYYY-MM-DD`：`album_release_date`（methods.rs:958-967，+08:00 见 :956）+ `civil_date_from_unix`（:971-984）→ 成立。
- 「artist 是白送的」：`methods.rs:809` 读 `singername/…`；工程侧 `Models.kt:42` 注释「[singerName] 仅搜索结果填充（收藏列表接口不给）」，`QqMapper.albumItem`（:98-107）确实不填 → 成立。
- 实测 `count:4 numericId:true`：probe-output.json `fetch_liked_albums` 一致。

**判定**：confirmed。

---

## id 9 — 歌单内曲目 | partial → **confirmed**（结论自述未实测，如实）

**核到的证据**
- 组件 `src/methods.rs:714-754`（结论写 701-741 是旧版）：`disstid = first_int(songlistId/disstid/id) or topId`（:720-722），`limit` clamp **1–200**（:723），`offset` 显式优先、否则由 page 换算（:727-730），total **可为 null**（:750-752 只取 `dirinfo.songnum`，无 `.unwrap_or`）。与工程 `PlaylistApi.Page(offset,num)`（PlaylistApi.kt:21/47-59）语义可直接替代，成立；曲目字段缺口见 id 4。
- 升级收益：工程 `PlaylistApi.kt:41/57` 同样可拿到 total；工程 `TrackListScreen`（Screens.kt:213-238）本来就是 offset 循环 + `total` / 空页判断，组件的 page 换算对宿主是等价物 → 成立。
- 本行未实测：probe-output.json 17 行里**没有** `fetch_playlist_tracks` → 结论自述「本行未实测」属实，不是遮掩。

**判定**：confirmed（未实测部分如实标注）。

---

## id 10 — 专辑内曲目 | partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:154-163` → `catalog.rs:415-451`：驼峰 `albumMid` / `offset`→`begin` / `limit`→`num` / `order:0`，列表 `songList[].songInfo`（`decoded_tracks` 的候选键含 `songList`，methods.rs:891-898；包层解在 `decode_track`，:903）。总数 `totalNum`（catalog.rs:449）。与工程 `PlaylistApi.kt:86-97` 逐字一致 → 成立。
- limit 默认 200（`round(int("limit"), 200, 1, 200)`，methods.rs:161）vs 工程 100（`PlaylistApi.kt:86` 默认 `num=100`）→ 传参即可，成立。
- 实测 `total:1 got:1`：probe-output.json `fetch_album_tracks` 一致。

**判定**：confirmed。

---

## id 11 — 歌手解析（名字 → mid/pic/计数）| covered → **confirmed**

**核到的证据（抽查类：歌手计数）**
- 组件 `catalog.rs:1430-1444` 已核（见 id 1）；内部 name→mid 解析确实走同一搜索：`catalog.rs:590-614`（`search_artist_artwork(…, 1)`，limit=1；`search_artist_artwork` 在 :1544-1571 用 `SearchKind::Artists`）。
- 计数来自 `songCount/albumCount`：catalog.rs:1440-1441（`songNum/albumNum` → `songCount/albumCount`），`Artist` 模型（models.rs:199-207）同名 → 成立。
- 宿主「精确名优先」在 `SingerApi.kt:29-32`，工程侧消费点 `SingerScreen.kt:112-116`（`s.songNum/s.albumNum` 补齐页头）、`AppRoot.kt:421/613` → 成立。
- 一个精确定位：组件 `map_artist` 的 `songCount/albumCount` 用 `first_int` 且**不做 0 到 null 的转换**——`Optio
n<i64>` 在 JSON 里就是数字或 null，工程侧 `SearchApi.kt:57-58` 的 `optInt("songNum", 0)` 读到 null 得 0，行为一致。

**判定**：confirmed。

---

## id 12 — 歌手歌曲（分页/排序/总数）| partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:164-173`（HEAD）→ `catalog.rs:459-504`：`order` 硬编码 **1**（catalog.rs:477；`sort` 参数只用于本地排序），`begin = (page-1)*limit`（:479），响应**只回 `{tracks}`**（methods.rs:173），`totalNum` 未被读取（`decoded_tracks` 返回 `Vec<Value>`，无 total）→ 差异 ① 成立。
- 只认 page（methods.rs:170 `int("page").unwrap_or(1)`，limit clamp 1–100 :171）而非 offset → 差异 ② 成立。
- latest 只是页内排序：catalog.rs:487-502（附 `releaseDate` 后 `sort_by`），上游 order 被忽略（:471-481 只发 `order:1`）→ 差异 ③ 成立；工程 `SingerApi.kt:35-47` 把 `order` 透传（`SingerScreen.kt:130` `ORDER_NEW=2 / ORDER_HOT=1`，SingerApi.kt:24-26），注释（SingerApi.kt:15-16）称实测 order=2 是服务端全序 → 成立。
- 曲目字段缺口 → id 4。
- **工程侧反例检查**：「跨页不是全序」在当前 UI 上有一个被削弱的地方：`SingerScreen.kt:141-149` 首次只取一页（100/30），`:166-181` 触底追加时把新旧页**拼接**，不做跨页重排。所以在**当前的翻页结构**下，组件「页内排序」与工程「服务端全序」在已加载部分的表现不同（工程每页都是服务端序，组件每页页内重排）——差异 ③ 成立且后果如结论所述。
- 本行未实测：probe-output.json 无 `fetch_artist_songs` → 结论自述属实。

**判定**：confirmed。

---

## id 13 — 歌手专辑（分页/总数/每张歌数）| partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:174-183` → `catalog.rs:507-559`：只回 `{albums}`（methods.rs:183），无 total；`order:1` 硬编码（:525）、`begin` 同 page 换算（:527）；latest 页内排序（:552-557）；输出字段 `id/title/albumMid/coverURL/artist/releaseDate`（:540-549），**没有每张专辑的歌数** → 成立。
- **多请求块合并**：组件 `envelope` 支持 `Vec<Call>`（upstream.rs:388-426，循环 `body["req_{index}"]`），但**公开路径只传单元素**：`call_with`（:303-312 `vec![call]`）、`call`（:226-233）、`call_with_tme_login_type`（:193-201）都是 `vec![call]`；typed 包装 `call_with_platform`（api.rs:386-407）只注入 platform 后走 `call`；`grep` 确认 `src/` 内无第二处多 call 构造（除 port 的 `call_signed` 固定 `req_0`，upstream.rs:266-271）→ 结论「不支持多请求块合并」成立。
- 工程对应实现：`SingerApi.kt:84-102`（`fillSongCounts`：`mids.chunked(30).take(3)` → `call(commAuth(), *reqs.toTypedArray())`），并注释 :19-20 明确「用 musicu.fcg 的多请求块批量合并（一次 HTTP 带多个 req_N），不增加请求数」；`QqCore.kt:78-83` 注释同口径。→ 「30 张专辑合成 1 次 HTTP 在组件上会膨胀成 30 次请求」成立。
- 本行未实测：probe-output.json 无 `fetch_artist_albums` → 结论自述属实。

**判定**：confirmed。

---

## id 14 — 歌手资料与计数 | partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:184-193` → `catalog.rs:574-690`：`GetHomepageHeader`，`force android`（methods.rs:104-115 名单含 `fetch_artist_detail`）；返回 `songCount/albumCount/fanCount/followCount/description/region/foreignName`（catalog.rs:666-687）。
- **工程侧没有歌手简介**：`grep -rn "artist_detail\|artistDetail\|歌手简介"` 在 `app/src/main` 只命中 `SingerScreen.kt:242` 的 `desc = "歌手头像"`（无关）；`SingerApi.kt` 全文件无详情端点 → 「工程目前完全没有歌手简介/详情」成立。
- 空壳报错：catalog.rs:636-641（`header_name.is_none() && singerMid.is_none()` → `Err("上游返回了空的歌手资料…")`）→ 成立。
- typed FFI 不可用：
  - `api.rs:282-284`（结论写 274，旧版）`pub fn artist_detail(singer_mid) { call("fetch_artist_detail", …) }`；
  - `parse`（api.rs:49-61）的 match 只有 **7 个方法键**（`fetch_liked_songs` / `fetch_login_status|get_login_status` / `get_helper_info` / `fetch_user_playlists` / `fetch_liked_albums` / `fetch_followed_artists` / `fetch_playlist_tracks`）——结论「只解 7 个键」**准确**，`fetch_artist_detail` 不在其中 → 返回的 `{ "detail": {...} }` 不会被拆；
  - 生成绑定 `dist/android/kotlin/.../QqmusicApiHelperNext.kt:3846` 的 `artistDetail` 直接 `ArtistDetail.fromReader(...)`，读的是顶层字段；`ArtistDetail` 的必填 `singerMid/name/description` 定义在 `models.rs:333-345`，而协议层返回的顶层是 `{"detail": …}` → 必然反序列化失败。→ 成立。
  - 我未能运行 `boltffi generate` / `cargo test`（约束：只读、不跑构建），这一条按静态阅读判定，但两条独立证据（api.rs 的 parse 表 + 生成文件的 fromReader）足够支撑。
- `description` 值（catalog.rs:656 `first_text(&singer, &["Desc","desc","Description"])`）与 `docs/endpoints.md`「歌手简介多数为空」一致。
- 本行未实测：probe-output.json 无 `fetch_artist_detail` → 结论自述属实。

**判定**：confirmed。

---

## id 15 — 关注的歌手 | partial → **confirmed**

**核到的证据**
- 组件 `src/methods.rs:817-868`（结论写 804-855 是旧版）：`List`（大写 L）已处理（:853），`MID/Name/AvatarUrl/FanNum` → `singerMid/name/coverURL/fanCount`（:857-865）。
- **不返回 Desc、只给 fanCount** → 成立；工程侧 `UserApi.kt:53-59`（`FollowSinger.desc` 注释「服务端文案（如粉丝数）；歌手卡第二行用它」）、`HomeScreen.kt:774-781`（`if (s.desc.isNotEmpty())` 才显示第二行）→ 宿主不拼就第二行留空，成立。
- **无 Total**：:854-867 只 `.collect()` 一个 `Vec`，不读 `data.Total`（工程 `UserApi.kt:42` 读 `d?.optInt("Total", -1)`）。工程当前只消费 `.first`（`HomeScreen.kt:227` `UserApi.followSingers(0, 30).first`）→ 「当前影响为零」成立。
- `HostUin` 数字 uin 兜底：:823-831（`encrypted_uin().unwrap_or_else(|_| credential.music_id.clone())`，注释明确「endpoint 对数字 id 一样接受，实测同账号同行」）→ 去掉工程「缺 euin 就返回空」的硬依赖（`UserApi.kt:20-21` `if (euin.isEmpty()) return emptyList to 0`）→ 收益成立。
- 实测 `count:3 firstHasSingerMid:true firstHasName:true`：probe-output.json `fetch_followed_artists` 一致。

**判定**：confirmed。

---

## id 16 — 电台分组 | covered → **confirmed**

**核到的证据（抽查类补充，非必需四类之一）**
- 组件 `src/methods.rs:205-206` → `catalog.rs:794-846`：分组在 `radio_list[]`（:810），电台在其 `list[]`（:816）；**电台 id 取 item 自己的 id**（:821 `let id = first_int(item, &["id"])?`），**分组 id 单独取**（:838 `first_int(group, &["id","groupId","radioId"])`）→ 「分组 id 不会被当电台 id」成立。
- 「不返回 listenDesc」：电台输出字段只有 id/title/coverURL/listenerCount/source（:822-830）→ 成立；工程侧 `QqMapper.kt:119` 读 listenDesc，`Models.kt:35` 定义，`HomeCache.kt:121/141` 序列化，但 `grep -rn "listenDesc"` 在 UI 层无命中（只有上述三处）→ 「工程读它但从不显示」成立。多给 `listenerCount`（:828）成立。
- 工程按标题「猜你喜欢」定位 station：`HomeScreen.kt:163-165`、:220-222、:248-251（三处 `firstOrNull { st -> st.title == "猜你喜欢" }`）；组件 `title` 原样透传（:824）→ 可继续匹配，成立。
- 实测 `groups:11 stations:134`：probe-output.json `fetch_radio_stations` 一致。

**判定**：confirmed。

---

## id 17 — 电台曲目（无限流）| partial → **confirmed**（未实测点如实）

**核到的证据**
- 组件 `src/methods.rs:207-215` → `catalog.rs:850-873`：**上游方法确实换了**——`pf.radiosvr/GetRadiosonglist`（:864-865），工程 `RadioApi.kt:27-29/50-52` 是 `mb_track_radio_svr/get_radio_track` → 成立。
- 「每次调用回不同的一批」这个无限流前提：组件 `docs/endpoints.md` 电台曲目行写「电台是无穷列表」，`catalog.rs:848-849` 注释「A station is endless: the same request answers with a fresh rotation each time」——但这是**组件自己的文档断言，不是已跑过的活体证据**；本次未做网络请求，probe-output.json 17 行里**没有** `fetch_radio_tracks` → 按结论自己「本次未验证」，如实。此处补一句准确性：组件源码**行为上确实具备**「同一请求可无限取」的形状（无 offset/cursor、只发 `id/firstplay/num`，catalog.rs:866-870），所以工程若换成该端点，去重与分批逻辑**形式上**可平移；但「每次确实回不同内容」这次未验证，不做通过判定。
- 无 exclude/from 参数：param 只有 `id/firstplay/num`（:866-870）→ 去重与分批归宿主，工程 `RadioApi.kt:45-58` 已有（`exclude`、批间 300ms、mid 去重）→ 成立。
- `firstPlay` 默认 true（methods.rs:213 `unwrap_or(true)`）→ 首屏/续批可区分，成立。
- 拒答码统一报错：`upstream.call_with`（upstream.rs:303-330）在 `refusal_reason` 命中 2000/2001/1000/104401/104400 时 `Err`，即便有 data（:320-322）；空 data 也 `Err`（:323-329）→ 保留「异常 vs 空表」区分。工程侧 `RadioApi.kt:59-61`（`if (ok == 0) throw`，注释「全部批次失败必须按失败处理，不能当成空表」）原则可保留 → 成立。

**判定**：confirmed（「无限流前提未验证」如实标注；我没有网络请求可补证）。

---

## id 18 — 猜你喜欢 / 推荐流 | partial → **confirmed**

**核到的证据**
- 组件 `catalog.rs:900-916`（HEAD；旧版 :900 一致）：`music.radioProxy.MbTrackRadioSvr/get_radio_track`，param **写死** `{"id":99, "num":5, "from":0, "scene":0, "song_ids":[]}`（:912）→ 「直连 id=99 猜你喜欢、省掉按标题找电台」成立；「没有 exclude」成立。
- 需要登录 + 强制 android：methods.rs:104-115 名单含 `fetch_recommend_feed`（`class = Interactive`，:908）。
- 工程侧前提：`RecommendStore.kt:86-91`（`fresh.isEmpty() → delay(1200) → continue`）与 `:81-83`（`nextTracks(..., exclude = excluded.toSet())`）——组件的 `exclude` 表达不了，宿主继续按 mid 去重；上游回同一批时会落进 repeat 分支（`guard < 8` 次后退出，队列仍是旧的）→ 成立。
- 介绍文案：`fetch_song_detail` 的 `description = content_group(&info, "intro").join("\n")`（catalog.rs:264），同一 `info.intro.content[].value`；工程 `SongApi.kt:119-133`，空返回 null（:123 `?: return@runCatching null`、:132 `takeIf { isNotBlank }`）→ 「空即答案，一致」成立。
- 实测 `count:5`：probe-output.json `fetch_recommend_feed` 一致。

**判定**：confirmed。

---

## id 19 — 昵称 | covered → **confirmed**

**核到的证据（抽查类补充）**
- 组件 `src/methods.rs:638-670`（结论写 625-657 是旧版）：`login.nickname = first_text(profile, ["nick","nickname","name"])`（:665），`loggedIn/musicId/vipType/expired/hasPlaybackKey` 一次给全；凭据被拒时 `Err(UpstreamError::Upstream(_))` → 返回 `loggedIn:false`（:652-655，结论写 639-643 是旧版）。
- 工程 `UserApi.kt:46-50` 同端点 `info.nick`；`NicknameCache` 默认值语义见下。
- 实测 `hasNickname:true hasPlaybackKey:true`：probe-output.json `get_login_status` 一致。
- 附注：结论说「主页问候语当前不消费昵称（HomeScreen.kt:103,215-217）」——复核发现 `HomeScreen.kt:106-111` 的 `GREETINGS` 与 :216-217 的 `greetingForHour` 确实不带昵称，且 `NicknameCache` 只在 `SettingsScreen.kt:852` 写入、`Prefs.kt:365/370` 清空，**没有任何读取方**（`grep -rn "NicknameCache"` 无 `.get()` 调用）→ 工程侧「文档与代码不一致」成立，与组件无关。

**判定**：confirmed。

---

## 复核方法与未做到的事（如实）

- 两侧引用我用 `sed -n` / `grep -n` 逐行打开核对；组件旧行号用 `git -C … show <rev>:<file>` 取出后**逐条在对应 revision 上验证过**（`2ff7e71` 是被核结论的实际基准）。
- 未运行：`cargo test` / `cargo build` / `boltffi generate`（约束只读，不跑构建）；未发任何网络请求（probe-output.json 只读参考，未新增调用）；未改任何工程文件。
- 组件工作区 `src/port/comment.rs` 有未提交改动，与本域无关，但提醒后续复核以 `5cb2e82` 为基准读行号时注意 methods.rs **+13** 的位移。
- 结论里「本行未实测」的三条（id 9/12/13）与 id 17 的前提未验证，我**没有**替代性网络证据可补，一律按未验证处理，不代为判定通过。

## 汇总

| id | capability | 结论 | 复核判定 |
|---|---|---|---|
| 0 | 搜索·歌曲 | partial | confirmed（`<em>` 已剥 与 行号需订正） |
| 1 | 搜索·歌手 | covered | confirmed |
| 2 | 搜索·专辑 | partial | confirmed（`Cards.kt:158`→:152） |
| 3 | 搜索·歌单 | covered | confirmed |
| 4 | 曲目字段合集 | gap | confirmed |
| 5 | 我喜欢列表 | partial | confirmed |
| 6 | 我喜欢总数 | covered | confirmed |
| 7 | 收藏的歌单 | partial | confirmed |
| 8 | 收藏的专辑 | partial | confirmed |
| 9 | 歌单内曲目 | partial | confirmed（未实测，如实） |
| 10 | 专辑内曲目 | partial | confirmed |
| 11 | 歌手解析 | covered | confirmed |
| 12 | 歌手歌曲 | partial | confirmed（未实测，如实） |
| 13 | 歌手专辑 | partial | confirmed（未实测，如实） |
| 14 | 歌手资料与计数 | partial | confirmed（未实测，如实；typed FFI 判断成立） |
| 15 | 关注的歌手 | partial | confirmed |
| 16 | 电台分组 | covered | confirmed |
| 17 | 电台曲目 | partial | confirmed（无限流前提未验证，如实） |
| 18 | 猜你喜欢 | partial | confirmed |
| 19 | 昵称 | covered | confirmed |

无一条 refuted；无一条 unclear（对被核结论的判定而言）；所有 partial/gap 均在两侧源码中找到了对应证据。真正需要别人知道的两件事：**（a）被核结论的组件行号整体落后 ~13 行**（methods.rs / api.rs / upstream.rs 因 port 提交位移，catalog.rs 未变）；**（b）id 0 的「`<em>` 已剥」对搜索歌曲不成立**——`strip_highlight` 不作用于 `decoded_tracks` 路径。
