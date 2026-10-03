# 播放与写入域：工程能力 ↔ 组件方法 逐条对应

> **范围**：播放直链与音质档位降级、歌曲详情/简介、逐字歌词与翻译（译/音/注三开关）、喜欢（写入）、封面 URL、登录与凭据（网页 cookie 与扫码）、下载、播放条/播放页对在线层的依赖。
> **两侧源码都亲自读过**：组件 `/tmp/helpernext_probe/src/{methods,catalog,models,qrc,login,credential,upstream,guard,api,aria2,bin/stdio}.rs`（git `2ff7e71`）；工程 `app/src/main/java/com/neumusic/player/`（只读）。清单文件 `.zcode/research/{helpernext-inventory,project-api-inventory,project-requirements}.md` 只当作索引，结论均回到源码。
> **只读**：未改任何工程文件、未跑 gradle、未连 adb/模拟器、未发任何网络请求。唯一的写入是本文件。
> **未自行执行**的检查在 §D 逐条列出（含组件 FFI 面与写操作）。

---

## 0. 一句话结论

组件在这一域里能直接替代的是**读取类**：播放直链（同一 `UrlGetVkey` + 同一降级语义）、歌曲简介、整行歌词、翻译、封面归一化、昵称。
**拿不到的**有三样，且都落在用户可见能力上：**音译（roma）**、**fileSizes（格式弹窗 + 降级过滤）**、**曲目 genre 码（智能调音）**；
**形态差异最大**的是逐字歌词——组件交的是「一词一标签的 LRC 文本」，工程内部是「行 + 逐字对象」且渲染按**单字**裁剪；
**写操作**能替代但成功判定比工程弱（组件不看 `data.retCode`），且业务码被压成错误文案（`1000` 被译成「登录已过期」）。

---

## A. 逐条对应

### A1. 播放直链（含音质档位降级）— **partial**

| | |
|---|---|
| 工程 | `data/api/SongApi.kt:51-84`（playUrl 降级链）、`:98-112`（fetchPurl）、`:87-94`（fileSizes 过滤）、`:96`（PlayUrlException）；调用点 `ui/common/TrackRow.kt:345-349` → `ui/AppRoot.kt:174-176` → `player/PlayerHost.kt:175-197` |
| 组件 | `resolve_song_url`（`src/methods.rs:350-359` → `src/catalog.rs:1041-1123`；档位表 `src/catalog.rs:25-30`；`require_login` `:1050`；`result` 码 `:33-36`；失败分类 `:1126-1158`）；refusal 处理 `src/upstream.rs:524-531` |
| 实测 | `probe-output.json:141-150`：`resolve_song_url` → `playable:true, quality:"flac", filenamePrefix:"F000", urlHost:"isure.stream.qqmusic.qq.com"`（真实账号） |

**相同**：同一 `music.vkey.GetVkey/UrlGetVkey`；同一 CDN 前缀 `https://isure.stream.qqmusic.qq.com/`（`catalog.rs:17` / `SongApi.kt:110`）；同一「按档位从高到低探测、取到就返回」；`104003` 是**档位级**、见到不中断（组件按 `result==0 && purl 非空` 才授予，否则 `catalog.rs:1091-1108` 继续下一档——与工程 `SongApi.kt:69` 同义）；`104004`/`104013` 分别映射「取票失败/设备受限」（`catalog.rs:34-36`）。

**差异 / 宿主需要补**：
1. **档位只有 4 档且没有 OGG**：组件表是 `flac / 320(M800.mp3) / 128(M500.mp3) / aac(C400.m4a)`（`catalog.rs:25-30`）。工程的 `O600 (.ogg 192k)`、`O800 (.ogg 320k)`（`Prefs.kt:13-14`）在组件里没有对应标签；`quality` 参数只按 label 匹配，传 "ogg320" 之类会**静默退回整条链**（`catalog.rs:1054-1061` —— `find` 不到就用 `QUALITY_LADDER`）。
2. **组件不做 fileSizes 过滤**：它没有 `file.*` 概念，每档都真发一次请求（最多 4 次往返）；工程先按 `file.size_*` 过滤（`SongApi.kt:57-58`）。若宿主想省请求，得自己先过滤，再把「首选档位」或「整链」二选一喂给组件——组件不支持「只试这几档」。
3. **失败是返回值不是异常**：不可播时组件回 `playable:false` + `restriction`(`paid_required|device_restricted|ticket_required|unavailable`) + `reason`(逐档中文) + `tried[]`(`"flac:104003"`)（`catalog.rs:1112-1122`）。工程是 `PlayUrlException(message, result)`（`SongApi.kt:74-83`）。宿主需要把 `playable:false` 映射成自己的异常/文案；**「连最低档都 104003 才判无权限」这条判定组件不替你做**（它给的是整链分类 `classify_restriction`，`catalog.rs:1126-1149`）。
4. **`101404` 组件没有单独分支**：只落进 `describe_result` 的「错误码 101404」兜底（`catalog.rs:1157`），不像工程那样把它当「短时限流」特意标注（`SongApi.kt:70,79`）。`result=22`（未登录）同样不单独识别。
5. `filename` 构造：组件优先 `mediaMid`，缺失时退化成 `{songMid}{songMid}`（`catalog.rs:1066-1069`）；`guid` 是**每次请求随机生成**（`catalog.rs:1163-1170`），而工程用稳定的 `Prefs.guid`（`SongApi.kt:103`）。
6. `uin` 组件取凭据数字 id（`catalog.rs:1078`）；工程缺凭据时写 `"0"` 仍发请求（`SongApi.kt:101`）。
7. **FFI 面的坑**：typed `resolve_song_url()` 在盘点里属于「18 个载荷与模型不匹配」之列（`helpernext-inventory.md:216`，载荷信封键是 `stream`，`src/methods.rs:359`，而 `src/api.rs:41-53` 只解包 7 个键——我自己核过这段解包代码，与盘点结论一致）。实际可用面是 **stdio 子进程** 或 `call_with_platform`（`src/api.rs:378-394`），或按盘点建议拿原始 JSON 自己解析。
8. 组件多给：`expiration`（兜底 7200，`catalog.rs:1102`）、`tried[]`。

---

### A2. 音质档位定义（6 档、前缀+扩展名成对）— **partial**

| | |
|---|---|
| 工程 | `data/Prefs.kt:10-17`（`M500.mp3 / M800.mp3 / C400.m4a / O600.ogg / O800.ogg / F000.flac`）；`ui/settings/SettingsScreen.kt:222-239`（6 档选择）；`SongApi.kt:87-94`（hasFile 映射） |
| 组件 | 无独立方法；只有 `resolve_song_url` 的 4 个 label（`src/catalog.rs:25-30, 1054-1061`） |

差异：工程这套「6 档 + 该曲有哪些档位（size>0）」在组件上**没有等价物**。若宿主保留自己的档位表与 filename 构造，它需要的其实只是「发一次 vkey 原始请求」的能力——组件没有这种低层方法，只能 `call_with_platform("resolve_song_url", …, "android")` 拿到已选档位的结果，或退回原生直连。**OGG 两档 + fileSizes 过滤这两条，接组件后必然要放弃或自建。**

---

### A3. 播放失败的人话文案（1000/104003/101404/22）— **partial**

| | |
|---|---|
| 工程 | `SongApi.kt:60-83`、`ui/common/TrackRow.kt:338-343`、`SongApi.kt:156-159` |
| 组件 | `catalog.rs:1126-1158`（restriction/describe_result）、`src/upstream.rs:524-531`（refusal_reason） |
| 实测 | `probe-output.json:141-150`：可播路径（无失败样本） |

差异：组件有结构化 `restriction` 与逐档 `tried`，**比工程好**；但上游风控码被压成错误文本：`1000/104401/104400` → 「登录已过期，请重新登录」，`2001` → 「触发风控…」，`2000` → 「需要签名」（`upstream.rs:524-531`）。工程的 `1000`（写限流）与 `104003`（档位会员）能分别展示不同文案；组件侧这两类**拿不到原始码**（除非走 `call_with_platform` 自己解析原始 JSON）。宿主若要保留既有文案分级，必须在组件外再解析一层，或改文案。

---

### A4. 歌曲详情 / 简介（推荐卡介绍框）— **covered**

| | |
|---|---|
| 工程 | `data/api/SongApi.kt:119-133`（`intro(mid)`：commWeb + `data.info.intro.content[*].value` 以 `\n` 拼接，空→null）；消费点 `data/RecommendStore.kt:95` → `ui/home/HomeScreen.kt:696`（空显示「该歌曲暂无歌曲详情」） |
| 组件 | `fetch_song_detail`（`src/methods.rs:129-141` → `src/catalog.rs:161-275`；`description = content_group(&info,"intro").join("\n")` 在 `:264`；空简介是答案 `:249-251`；`content_group` `:123-135`） |
| 实测 | `probe-output.json:151-159`：`hasDescription:false, descriptionLen:0` —— 所测曲目确实没有简介，与「空即答案」语义一致 |

**可直接替代**：同端点 `music.pf_song_detail_svr/get_song_detail_yqq`、同字段路径、同 `\n` 拼接、同「空不是错误」。多给：`genreTags[] / language / labelOrCompany / releaseDate / duration / songId / albumMid / imageURL` 与四个溯源键（`with_provenance` `catalog.rs:84-93`）。

需要留意的：
- 组件支持「只给 title/artist/album 自动搜索解析 mid」（`catalog.rs:175-205`，子搜索**强制 android 档案** `SEARCH_PLATFORM` `catalog.rs:50`）——工程只按 mid 调，用不到但无冲突。
- 组件默认档案是 Web（`platform_for` → `Platform::default()` = `Web`，`methods.rs:401`、`upstream.rs:34-39`），与工程 `commWeb` 一致；**`configure(default_platform)` 在 dispatch 路径上是死配置**（`helpernext-inventory.md:152`，我核过 `methods.rs:401` 用的确实是 `Platform::default()`）。
- FFI 的 typed `song_detail()` 报 `missing field songMid`（`helpernext-inventory.md:216`）；能用的是 stdio / `call_with_platform`。
- **`genreTags` 是字符串标签（"Rock; Pop/Blues"，`split_tags` `catalog.rs:692-701`），不是工程的 int `genre` 码** —— 见 A20。

---

### A5. 逐字歌词（QRC 卡拉 OK）— **partial**（本域最大形态差异）

| | |
|---|---|
| 工程 | `data/api/LyricApi.kt:69-116`（取数+分流）、`:374-413`（parseQrcXml）、`:151-158`（looksLikeHex）；`data/api/QrcCodec.kt:202-227`（decryptHex）；模型 `data/Lyrics.kt:3-30`；渲染 `ui/player/LyricsView.kt:415-473`（FlowRow 单字双层裁剪）、`:397-403`（wordFraction） |
| 组件 | `fetch_lyric`（`src/methods.rs:227-266`）→ `encrypted_lyrics`（`src/catalog.rs:949-999`，`qrc:1, crypt:1`）+ `src/qrc.rs:374-410`（decrypt_hex）、`:440-489`（parse）、`:492-500`（lrc_timestamp）、`:507-525`（to_word_lrc）、`:528-536`（word_level_lrc） |
| 实测 | `probe-output.json:117-127`：日文曲 `hasWordLevel:true, wordTimestampCount:544, hasTranslation:true, hasKanaMeta:true`；`:129-139`：目标曲 596 个词时间戳；`:105-115`：另一首 `hasWordLevel:false`（`wordLyric:null`，不是错误） |

**数据同源**：`music.musichallSong.PlayLyricInfo/GetPlayLyricInfo` 加密路，同一 QQ 私有「类 DES」三重 `D(K3)→E(K2)→D(K1)` + zlib + BOM + XML 壳；组件算法来源与工程 `QrcCodec.kt:12-15` 相同（MIT 的 qrc-decoder，已知答案测试 `qrc.rs:547-565`）。逐字数据只在加密路（`docs/endpoints.md:61-71`，`catalog.rs:920-928`）。

**差异（宿主必须自己补解析器）**：
1. **交付形态是文本**：组件给的是 **词级 LRC** —— 每个词前一个时间戳（`to_word_lrc` `qrc.rs:507-525`；测试断言 `[00:01.00]你[00:01.20]好` `qrc.rs:603-609`），不是结构化的行/字对象。
2. **工程的 `parseLrc` 会吃掉它**：`LyricApi.kt:337` 只取**最后一个时间标签之后**的文本（`line.substring(tags.last().range.last+1)`），把「多标签行」当重复副歌展开。直接把 `wordLyric` 喂进去 → 每行只剩最后一个词。宿主**必须**为「一词一标签」写新解析（按 `[t]text` 逐段切），并且注意工程渲染按**单个字符**裁剪（`KaraokeWords` `LyricsView.kt:415-473`），若把整词当一个 `LyricWord`，扫色粒度就从「字」退化成「词」。
3. **时间精度 10ms**：`lrc_timestamp` 取 `ms/10` 再格式化两位小数（`qrc.rs:492-500`）→ 量化误差 ≤5ms。工程的 `parseLrc` 两位小数按 ×10（`LyricApi.kt:343-349`）能无损读回，`wordFraction` 不受影响。
4. **没有行时长/字时长**：QRC 原文里每行/每字都带 duration，但词级 LRC 只有**起点**；工程模型需要 `endMs`（`Lyrics.kt:6-9, 29`：`endMs = 末字 endMs`，用于重叠清洗 `LyricApi.kt:288-291` 与扫色插值）。宿主只能「用下一个词的起点当上一个词的终点」，末词需要截到下一行起点或行时长——**组件把行时长丢了**。这是卡拉 OK 观感的关键差异（工程自己解密时行时长在 `[行起始,行时长]` 里，`LyricApi.kt:385-386`）。
5. **绝对毫秒坑不存在**：组件在 `parse` 里直接用原文数值（`qrc.rs:453-456`），本来就是绝对时间；工程那条「字时间超过行时长 → 绝对基准」的判别（`LyricApi.kt:405`）在接组件后不再需要。
6. **没有逐字不是错误**：组件 `wordLyric:null`（`methods.rs:262`），同时给 `lyric.lyric` 整行明文（明文 fcgi 路，`catalog.rs:1012-1017`）——可直接替代工程「hex 判别失败 → base64 兜底」的分流（`LyricApi.kt:88-95`）。
7. **取数路径不同**：组件整行走 `c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?nobase64=1&platform=yqq.json`（带 `g_tk`，`catalog.rs:1012-1017`），逐字走 `PlayLyricInfo`；工程两条都走 `PlayLyricInfo`（`LyricApi.kt:119-148`，且**绕过**工程的 120ms 节奏闸 —— `project-api-inventory.md:43`）。默认 `wordTiming:true, translation:true`（`methods.rs:233-234`）＝最多 2 次上游；工程的第二趟只在 `trans` 为空且有逐字行时才发（`LyricApi.kt:99-103`）。
8. FFI typed `lyric()` 属载荷不匹配之列（信封 `lyric`+`wordLyric`，`methods.rs:260-263`；盘点 `helpernext-inventory.md:216` 实测报错）→ stdio / `call_with_platform`。

---

### A6. 翻译（「译」开关）— **partial**

| | |
|---|---|
| 工程 | `LyricApi.kt:99-105`（qrc=1 的 trans 常空 → 补一趟 qrc=0）、`:169-180`（`mergeTranslation`，±300ms、丢 `//`）；`Lyrics.kt:37`（`hasTranslation`）；开关 `ui/player/PlayerScreen.kt:246-267`、`ui/player/LyricsView.kt:352-371` |
| 组件 | `fetch_lyric.translation`（`methods.rs:252-259` 用加密路覆盖明文路；`catalog.rs:997` decrypt(trans)；明文路兜底 `catalog.rs:1027`）；`Lyric.translation`（`models.rs:388-395`） |
| 实测 | `probe-output.json:117-127`：日文曲 `hasTranslation:true` —— 加密路确实带出翻译 |

差异与宿主补法：
- 组件给的是**整行 LRC 文本**（不是逐行对象），宿主需 parse + 按时间合并——工程的 `mergeTranslation`（`LyricApi.kt:169-180`）可直接复用。
- **`//` 占位**：组件不丢（`decrypt` 后原样给），工程是自己丢的（`LyricApi.kt:62,177`）——**保留工程这段逻辑即可**，不能指望组件。
- **关不掉**：`translation:false` 只跳过明文路那一半的读取，加密路只要取逐字（`want_words`）就会连 `trans` 一起取（`methods.rs:236-240`，`catalog.rs:958-978` 的 param 写死 `trans:1`）——省不了往返。
- 可用性门控（`hasTranslation`）由宿主从合并结果自己算，组件没有这个标志。

---

### A7. 音译 / 罗马音（「音」开关）— **gap**

| | |
|---|---|
| 工程 | `LyricApi.kt:108-110`（`roma:1` 的 hex QRC → `parseQrcXml`）、`:183-191`（`attachRoman`，±150ms 对齐）；`Lyrics.kt:39`；开关 `PlayerScreen.kt:258-262` |
| 组件 | **不存在**：param 里 `"roma": 0, "roma_t": 0` 写死（`src/catalog.rs:970`）；`EncryptedLyrics` 只有 `word`/`translation`（`catalog.rs:933-939`）；`Lyric.romanization` 恒 `None`（`models.rs:393`，`methods.rs:252-259` 只合并 translation） |
| 实测 | `probe-output.json:113,125,137`：三首歌 `hasRomanization` 全 `false`。**但这不能证明上游没有数据**——是组件 `roma:0` 写死的必然结果 |

**结论**：这条能力在组件上拿不到。宿主若要保留「音」开关，必须自己按 `PlayLyricInfo{roma:1}` 再取一次并用 `QrcCodec` 解 hex（工程已有全套实现，`LyricApi.kt:108-110`）。
⚠️ 组件文档/接口注释有误导：`src/api.rs:326-327` 写「translation asks for the translation **and romanisation**」，`docs/endpoints.md:83` 也提「翻译优先取加密路」，但实现里 roma 是 0 —— **注释与实现不符，别按注释期待**。

---

### A8. 注音 / kana（「注」开关）— **partial**

| | |
|---|---|
| 工程 | `LyricApi.kt:196-209`（`parseKanaTokens` 从 trans 原文正则找 `[kana:…]`）、`:224-266`（`attachKana`：只有汉字消费 token，产出字级 `kana` 与行级 `kana`）；`Lyrics.kt:41`；开关 `PlayerScreen.kt:263-267` |
| 组件 | 不解析，但**数据在 `translation` 里原样带出**（`catalog.rs:997` `decrypt_hex(trans)`）；全组件 grep 无 `kana` |
| 实测 | `probe-output.json:125`：日文曲 `hasKanaMeta:true` —— 组件返回的 translation 文本里确实含 `[kana:` 元数据 |

**能拿到数据，解析要宿主自己做**：把工程的 `parseKanaTokens(transRaw)` 接到组件的 `translation` 上即可（注意工程现在传的是**未解密的 base64 原文** `LyricApi.kt:202-204` 内部先 `decodeLrc`；组件给的是已解密文本，直接正则即匹配，`decodeLrc` 那步要跳过/改成「已明文则原样」，其实 `decodeLrc` 的 `getOrElse { raw }` 兜底 `LyricApi.kt:165` 已经能容纳）。
行级 `kana`（读音行）与字级 `kana`（字上注音）都仍由宿主算。

---

### A9. 喜欢（写入 / AddSonglist·DelSonglist）— **partial**

| | |
|---|---|
| 工程 | `data/api/SongApi.kt:143-160`（数字 songId 前置 `:145`；param `:146-151`；成功判定 `:155-159`）；`ui/common/TrackRow.kt:315-343`（统一入口 + 1000 文案）；`ui/player/PlayerScreen.kt:398-412`；本地集合 `data/LikedStore.kt:59-62` |
| 组件 | `set_liked`（`src/catalog.rs:1262-1312`；param `:1300-1308`；mid→数字 id 解析 `:1276-1295`）；dispatch `src/methods.rs:338-349`；FFI `src/api.rs:398-404`；强制 android `src/methods.rs:104-121` |
| 实测 | **未实测写操作**：`probe-output.json` 的 17 行里没有 `set_liked`（`readOnly:true`），与盘点一致（`helpernext-inventory.md:236`）。以下全部来自源码 |

**相同的**：端点 `music.musicasset.PlaylistDetailWrite` + `AddSonglist`/`DelSonglist`；参数 `dirId:201, tid:0, bFmtUtf8:true, v_songInfo:[{songId, songType:0}]`；强制 android 档（组件 `methods.rs:104-121` / 工程 `QqCore.kt:49` commAuth）；都不自动重试。

**差异 / 宿主需要补**：
1. **成功判定更弱**：组件只看外层 `code`（`upstream.rs:245-259`：`code!=0` 或 `data` 为空即报错），**从不看 `data.retCode`**（全组件只有老 fcgi 的 `profile_assets` 读 `retcode`，`upstream.rs:288`）。工程必须 `code==0 && retCode==0`（`SongApi.kt:155-159`，注释明说「只看外层会误报」）。若上游出现「外层 0 / retCode≠0」，组件会**当成功返回**（`catalog.rs:1311` → `{"songId":…, "liked":…}`），宿主的红心会与服务端不一致。⇒ 接入时要么自己 `call_with_platform("set_liked", …)` 拿原始 JSON 复核 `retCode`，要么给组件上游提这个 bug。
2. **业务码被压成错误文本**：`1000` 归入「登录已过期，请重新登录」（`upstream.rs:528`），`2001` → 「触发风控…」，且 `Err(Upstream)` 只带一个字符串（`upstream.rs:100-106`）。工程能拿到 `LikeResult.Rejected(1000)` 并显示「操作太频繁，已被限流」（`TrackRow.kt:338-343`）。宿主接组件后**拿不到数字码**，除非解析 message 或走 `call_with_platform`。
3. **接受 mid**：组件允许只给 `songMid`，内部先 `song_detail` 解析数字 id（`catalog.rs:1276-1295`），比工程更宽容（工程 `songId<=0` 直接 `Unavailable`，`SongApi.kt:145`）；但 FFI 的 `set_liked(song_mid, song_type, liked)` 只暴露 mid 路径且返回 `Result<(),_>`（`api.rs:398-404`），拿不到回执里的 `songId`。
4. **`require_login`**（`catalog.rs:1271`）→ 无凭据报「需要登录后才能读取」；工程的 `Unavailable("未登录")` 是本地判定，不发请求（`SongApi.kt:144`）。
5. 本地乐观更新（`LikedStore.mark`）与「本地集合」完全属宿主，组件不提供。

---

### A10. 我喜欢列表（供红心状态 / 计数）— **partial**

| | |
|---|---|
| 工程 | `data/api/PlaylistApi.kt:31-43`（offset 分页，`dirinfo.songnum` 总数；缺 euin 抛 `:24-28`）；`data/LikedStore.kt:31-54`（每页 **300**，最多 50 页翻全）；`ui/home/HomeScreen.kt:236-241`（计数卡片） |
| 组件 | `fetch_liked_songs`（`src/methods.rs:661-693`；FFI `liked_songs(page, limit)` `api.rs:90`） |
| 实测 | `probe-output.json:26-38`：`total:479, got:3, firstHasMid:true, firstHasAlbumMid:true, firstHasMediaMid:false, firstDuration:133, coverIsHttps:true` |

**差异**：
- **分页是 page/limit 不是 offset**，`limit` clamp 1–100（`methods.rs:667-668`）。工程按 offset、每页 300（`LikedStore.kt:41`）→ 接组件后每页最多 100，翻全 479 首要 5 次往返（工程 2 次）。宿主需改分页算法（`page` 从 1 起）。
- **不需要 euin**（`methods.rs:666` 只 `require_login`）；工程 `requireEuin()` 缺 euin 直接抛（`PlaylistApi.kt:26`）。⇒ 组件这条路让「缺 euin → 收藏不可用」的约束消失（`encrypt_uin` 缺失时关注歌手用数字 uin 兜底，`methods.rs:816-821`）。工程 UI 里那条「缺 euin，收藏列表可能不可用」提示（`SettingsScreen.kt:855-857`）可删。
- `total` 必填但**可能被本页条数兜底**（`methods.rs:690`：`dirinfo.songnum` 取不到就 `tracks.len()`）——宿主不能拿它当「到底了」的判据，仍要按空页/短页判断。
- 返回的 Track **无 `fileSizes`、无 `genre`**（见 A19/A20）→ 这条数据源上的红心没问题，但格式弹窗/智能调音/音质过滤会失效。
- 组件不给 `enc_host_uin`（用凭据 cookie），工程显式传（`PlaylistApi.kt:38`）——协议细节，不影响结果。

---

### A11. 封面 URL（专辑 / 歌手 / 歌单 / 曲目 / 通知封面）— **partial**

| | |
|---|---|
| 工程 | 模板拼装 `data/Models.kt:20-23`（`T002R300x300M000{albumMid}`）、`data/api/SingerApi.kt:75`；接口直给 + `toHttps()` `data/api/QqCore.kt:128-131`，用在 `QqMapper.kt:92,104,120`、`SearchApi.kt:55,71,87`、`UserApi.kt:38`；通知封面直拉 `player/PlaybackService.kt:125-137` |
| 组件 | `normalized_artwork_url`（`src/methods.rs:987-996`，http:// 与 `//` 都升 https）；`album_cover_url`（`:979-981`，`T002R800x800M000`）、`singer_cover_url`（`:975-977`，`T001R300x300M000`）；Track 的 `imageURL` 直接用上游 URL（`methods.rs:927`）；各处归一：`map_album`（`catalog.rs:1450-1464`）、`map_playlist`（`catalog.rs:1481-1484`，键在 **`logo`**）、`map_artist`（`catalog.rs:1436-1440`）、电台（`catalog.rs:822-824`）、关注歌手（`methods.rs:850`） |
| 实测 | `probe-output.json:36`：`coverIsHttps:true`（真实账号回读） |

差异：
- **https 强制组件更全**（多处理协议相对 `//`），工程 `toHttps` 只处理 `http://`（`QqCore.kt:128-131`）。
- **曲目封面尺寸档不同**：工程统一 `R300x300`；组件曲目用上游原始 `imageURL`（`methods.rs:927`，可能是 800 或其他），专辑模板是 `R800x800`。表现层观感差异，宿主可忽略或自己换模板。
- **空值形态**：组件全是 `Option`（None），工程约定「空字符串」。宿主需要 `orEmpty()` 映射，否则占位逻辑（`Cards.kt` 首字母占位）判断不到。
- 通知封面（`PlaybackService.kt:129-132` 用 `java.net.URL(...).openStream()`）依赖的是**纯 https 直链**，组件满足（实测 coverIsHttps:true）。
- 组件没有独立的「mid → 封面 URL」方法；`singer_cover_url`/`album_cover_url` 是内部函数（`pub` 但不在 METHODS 表里，stdio 面拿不到）。要封面就取轨迹/专辑/歌手对象里的字段。

---

### A12. 网页 cookie 登录（uin + qm_keyst + euin）— **partial**

| | |
|---|---|
| 工程 | `ui/settings/SettingsScreen.kt:839-860`（WebView cookie 读三件 → `Prefs.saveCredential`）、`:875-895`（桌面 UA + 三方 cookie）、`:911-940`（只放行 http/https）;`data/Prefs.kt:352-374`（`CredentialInfo(uin,musickey,euin)`）；`data/api/QqCore.kt:49-61`（commAuth 用 uin/musickey）；`PlaylistApi.kt:24-28`（euin 硬前提） |
| 组件 | `import_cookies`（`src/methods.rs:615-621`；stdio 拦截 `src/bin/stdio.rs:135-150`）/ FFI `import_credential(uin, qm_keyst)`（`src/api.rs:69-78`）；`credential_from_cookies`（`src/credential.rs:180-198`，`qm_keyst`+`uin`，`encrypt_uin` 选填）；凭据文件 `credential.rs:117-164`；`encrypt_uin` 缺失回退数字 uin `src/upstream.rs:146-172`、`methods.rs:816-821` |
| 实测 | `/tmp/helpernext-workflow-cred/Credential/qqmusic-credential.json` 只有 `musicid`/`musickey`/`str_musicid` 三键（`jq keys` 实跑），**没有 `encrypt_uin`**，而 `get_login_status` 实测 `loggedIn:true, hasPlaybackKey:true`（`probe-output.json:16-25`）→ 印证「缺 euin 也能用」 |

**可直接替代的部分**：cookie 的 `uin` + `qm_keyst` 就是完整登录（`credential.rs:175-179` 注释），`qm_keyst` 同时是会话票据与 CDN 播放票据。工程 WebView 登录读到的三件里，组件只需要两件（`euin` 可选），并且**工程侧的 euin 硬前提在组件里不存在**（关注歌手用数字 uin 兜底）。

**宿主需要补/改的**：
1. 凭据存储换成组件的文件（`<data_dir>/Credential/qqmusic-credential.json`，原子写 + 保留未知键，`credential.rs:145-164`）；工程 `Prefs.credential` 的 JSON 要改成「喂给组件」而不是自己用。可走 FFI `import_credential`（只有两参数）或 stdio 的 `import_cookies`（可带 `encrypt_uin`）。
2. **组件不提供「本地已存谁」的读**：`get_login_status` 是真问上游（`methods.rs:625-645`），凭据过期时如实回 `loggedIn:false`（`:639-643`）。设置页要显示账号（`SettingsScreen.kt:188`）需宿主自己读那个 JSON。
3. `g_tk` 组件自动算（`hash33(qm_keyst, 5381)`，`credential.rs:19-31`），工程没有这个概念——组件内部补齐，宿主不用管。
4. 工程 UI 里的「缺 euin」分级提示与「凭据完整，可读取全部收藏」（`SettingsScreen.kt:192,855-857`）需要重写。

---

### A13. 扫码登录（QQ）— **covered**（工程没有，组件补上）

| | |
|---|---|
| 工程 | 没有独立扫码：二维码是 `y.qq.com` 页面里的 iframe，靠 WebView 三方 cookie 显示（`SettingsScreen.kt:879-880` 注释） |
| 组件 | `start_login`（`src/login.rs:92-121`，返回 PNG base64 + `qrsig`）、`poll_login`（`:124-169`，事件码 `:58-79`）、换凭据 `:173-257`（`check_sig`→`authorize`→`QQLogin`，最后一步 android 档案 + `tmeLoginType:2`）；dispatch `methods.rs:322-337`；FFI `api.rs:411-420` |
| 实测 | 未实测（`probe-output.json` 无这两行；盘点记 `poll_login` 的 DONE 路径需真人扫码，`helpernext-inventory.md:238`） |

限制如实记录：**只有 QQ 扫码，没有微信**（`docs/endpoints.md:104`，源码无微信分支）；HTTPS 403 的一个常见原因是 `ptqrtoken` 种子算错（`login.rs:130-132` 注释，`credential.rs:14-31`）——组件已用 seed 0 处理。

对工程：可保留现有 WebView 登录（cookie 仍能 import），也可换成原生扫码；两条可以并存。

---

### A14. 退出登录 — **partial**

| | |
|---|---|
| 工程 | `ui/settings/SettingsScreen.kt:199-208`：`Prefs.clearCredential()` + `ApiCache.clear()` + `LikedStore.clear()` + `HomeCache.clear()` + `TrackListCache.clear()` |
| 组件 | `logout`：stdio 真删文件（`src/bin/stdio.rs:180-184`）、FFI 真删（`src/api.rs:82-86`）、`CredentialStore::clear`（`credential.rs:166-172`） |

**坑**：`methods::dispatch` 里的 `"logout"` 分支**不删文件**，只回一个 `{"login":{"loggedIn":false}}`（`src/methods.rs:449`）。宿主若把 `logout` 当普通 method 派发（例如自己实现 stdio 客户端但漏了拦截，或用 `call_with_platform("logout")`），会出现「退出后重启仍登录」。而且组件只清自己的凭据文件——工程那四份内存/快照缓存（ApiCache/LikedStore/HomeCache/TrackListCache）仍要宿主自己清。

---

### A15. 昵称 — **covered**

| | |
|---|---|
| 工程 | `data/api/UserApi.kt:46-50`（`GetLoginUserInfo` → `data.info.nick`）；`Prefs.kt:377-383`（内存缓存）；调用点 `SettingsScreen.kt:852` |
| 组件 | `get_login_status.nickname`（`methods.rs:648-656`，源 `nick`）；`poll_login` 的 `login` 里也带（`login.rs:166-168`） |
| 实测 | `probe-output.json:23`：`hasNickname:true` |

同端点同字段，一次调用同时给 `musicId/vipType/expired/hasPlaybackKey`（`models.rs:209-222`）。注意工程侧 `NicknameCache.get()` 目前**没有消费点**（`project-requirements.md` §4.1），所以这条替代影响很小。

---

### A16. 下载（取直链 → 拉流 → 写公共目录）— **partial**（建议保留工程实现）

| | |
|---|---|
| 工程 | `data/Downloader.kt:21-24`（独立 OkHttp，读超时 120s）、`:26-35`（`SongApi.playUrl` + 人话 toast）、`:34`（`singer - name` 命名）、`:37-62`（GET → MediaStore `Audio`/`Downloads` + IS_PENDING）、`:81-87`（ext→MIME）；台账 `data/DownloadStore.kt`；设置 `SettingsScreen.kt:497-535`（三目录 + 6 档音质） |
| 组件 | `aria2_*` 系列：`aria2_status/restart/configure/add/tell/list/pause/unpause/cancel`（`src/methods.rs:541-612`；stdio 拦截 `src/bin/stdio.rs:156-178`）；引擎目录 `--dir=<组件目录>/Downloads` 且**带 `--referer=https://y.qq.com/`、`--user-agent=QQMusic/1.0 (macOS)`**（`src/aria2.rs:212-223`）；二进制位置 `<组件目录>/aria2-next`（`aria2.rs:153-159`） |
| 实测 | 仓库里**找不到 `aria2-next` 这个文件**（`find /tmp/helpernext_probe -maxdepth 3 -name "aria2*"` → 只有 `src/aria2.rs`）；`dist/` 只有 android jniLibs/头/Kotlin（`ls -R dist`）。盘点也记 `aria2.{installed:false,running:false}`（`helpernext-inventory.md:239`）。⇒ **引擎缺席时这些方法报错，宿主应退回自己的下载方式**（`docs/endpoints.md` §四末尾原文） |

**为什么是 partial 而不是 covered**：
1. **下载目录被钉死在私有目录下的 `Downloads`**（`aria2.rs:212-223`），没有 `Music/NeuMusic`、`Music/`、`Downloads/NeuMusic` 三选（`Prefs.kt:47-51`）——工程的多目录设置无法保留；在 Android 上 `<组件目录>` 只能是应用私有目录，写进去再导入 MediaStore 是全新工作量（工程现在是**直接写公共目录** `Downloader.kt:42-62`）。
2. **引擎二进制不在仓库**：Android 要「随组件带一个可执行文件并 exec」需额外打包与权限；本机/仓库都没有该文件（上述 `find` 实跑）。
3. **FFI 面 11 个 `aria2_*` 全部不可用**（盘点 `helpernext-inventory.md:214` 实测「不支持的方法」，我核过源码：这些只在 `src/bin/stdio.rs:156-178` 被拦截，`methods::dispatch` 里没有它们的分支，`src/methods.rs:405-459`——与盘点一致）。
4. **命名/MIME 仍是宿主的**：`aria2_add` 只收 `url` + `out`（文件名，不能含 `/`，`methods.rs:569-580`），不校验可播、不管 MIME；工程的 `.mp3/.m4a/.ogg/.flac` MIME 映射（`Downloader.kt:81-87`）与非法字符替换（`:35`）要自己保留。
5. 组件能补的是**进度与取消**：`aria2_tell/list` 给 `completed/total/speed/status`（`models.rs:69-108`），`aria2_cancel` 会删临时文件（`methods.rs:600-612`）——工程目前只有一句 toast，没有进度 UI。

---

### A17. 已下载台账 / 列表置灰 — **gap**（纯宿主状态，组件不涉及）

| | |
|---|---|
| 工程 | `data/DownloadStore.kt:9-17`（mid→记录）、`:56-63`（mark/isDownloaded，SharedPreferences 持久化）；UI 置灰 `ui/home/Screens.kt:438-448` |
| 组件 | 无。`aria2_list` 只是引擎任务列表，不带「这个 mid 是否已下载」 |

接组件后宿主仍要自己按 `mid` 维护台账（`aria2` 的 `out` 名与工程 `displayName` 规则不同，不能互认），并自己把任务完成事件回填。

---

### A18. 曲目字段（songId / mediaMid / singers / isVip / interval）— **partial**

| | |
|---|---|
| 工程 | `data/Models.kt:4-27`；`data/api/QqMapper.kt:27-63`（`:35` mediaMid 兜底、`:50-61` 字段、`:59` songId、`:71-83` 歌手名拼接） |
| 组件 | `decode_track` `src/methods.rs:889-933`（`:921` mediaMid、`:922` title、`:923` artist 用 `", "` join、`:927` imageURL、`:928` duration、`:929` payPlay、`:930-931` singerMid/singers） |
| 实测 | `probe-output.json:33-36`：`firstHasMid:true, firstHasMediaMid:**false**, firstHasAlbumMid:true, firstDuration:133` |

差异：
- **`mediaMid` 可能没有**（实测），组件在解析 song_detail 之外不补；`resolve_song_url` 会用 `{songMid}{songMid}` 兜底（`catalog.rs:1066-1069`）。宿主不要假设 `mediaMid` 非空。
- **多歌手分隔符是 `", "`**（`methods.rs:907-911`），工程的切分集合是 `/`、`、`、`,`（`PlayerBar.kt:161`）——`, `（逗号+空格）**不在**工程切分集合里，会被当成一个名字去 `SingerApi.resolve`，多半解析失败。宿主必须补切分，或改用组件多给的 `singers[{mid,name}]` 数组（`models.rs:120-127`）——后者更稳，工程目前只有拼接字符串。
- 组件 Track **没有 `fileSizes`、没有 `genre`**（见 A19/A20）；`payPlay` 是 `Option<i64>`（1 = 可能要会员），工程是 `Boolean`（`QqMapper.kt:58`）。
- 组件 Track 的必填字段只有 `songMid`（`models.rs:135`），其余全 `Option`/默认——宿主需要默认值（工程模型基本非空）。

---

### A19. `fileSizes`（格式弹窗 + 档位过滤）— **gap**

| | |
|---|---|
| 工程 | `QqMapper.kt:39-48`（收集 `file.size_*` > 0）、`ui/common/TrackDialogs.kt:210`（有数据才显示入口）、`:236-256`（逐项翻人话）；`SongApi.kt:87-94`（用它过滤降级链） |
| 组件 | **没有**。全组件 grep `size_` 只命中 aria2 的 `min_split_size_mib`；`decode_track`（`methods.rs:917-932`）不产 `file` 字段；`models.rs:133-155` 的 Track 无对应字段 |

组件只回答「哪一档被授予」，不回答「这首歌有哪些档位、各档多大」。⇒ 工程的「查看格式」弹窗与「先按已有档位过滤再请求」两条都失去数据源。宿主二选一：保留原生这条字段映射，或改成不做过滤、让 `resolve_song_url` 逐档探测（最多 4 次请求，且 OGG 两档彻底没有——见 A1/A2）。

---

### A20. `genre` 曲风码（智能调音）— **gap**

| | |
|---|---|
| 工程 | `QqMapper.kt:61`（`genre` int）、`player/SmartEq.kt:25-35`（实测码表 1=流行 2=古典 22/50=摇滚 23=民谣 27=爵士 28=金属 33/20=电子 34=说唱）、`:53-59`（`genre==0` 或未映射 → 不动）、`PlayerHost.kt:193-196`（换歌时应用）；设置页可改映射（`ui/player/EqualizerScreen.kt:303-340`） |
| 组件 | Track 无 `genre`；只有**字符串标签**：`SongDetail.genreTags[]`（`catalog.rs:251-254`，`split_tags` 按 `; / ,` 等拆，`:692-701`）、`ArtistDetail.genreTags/genre`（`catalog.rs:673-679`）、`AlbumDetail.genreTags`（`catalog.rs:386-388`） |
| 实测 | `probe-output.json:157`：`genreIsArray:false`——载荷里没有数组形态的 `genre` 键（组件字段名叫 `genreTags`，`catalog.rs:265`）；这条不能当「genre 可用」的证据 |

语义不同（int 码 vs 标签字符串），不能直接喂 `track.genre`。另外组件的标签要走 `fetch_song_detail`（**每首一次请求**），而工程的 `genre` 是列表接口里免费带的（`QqMapper.kt:61` 从同一份对象取）。⇒ 保留智能调音就得保留原生字段，或自建「标签→预设」映射并接受额外请求。

---

### A21. 播放条/播放页的歌手名 → 歌手页 — **partial**

| | |
|---|---|
| 工程 | `data/api/SingerApi.kt:29-32`（`resolve(name)` 复用 `SearchApi.singers(name,10)`，精确名优先）；`ui/common/PlayerBar.kt:150-167`（按 `/`、`、`、`,` 切分，多歌手弹窗）；`ui/AppRoot.kt:417-446`（打开歌手页） |
| 组件 | `search_artists`（`methods.rs:305-321` → `catalog.rs:1361-1421`；`map_artist` `:1430-1444` 给 `singerMid/name/coverURL/songCount/albumCount/fanCount`；**强制 android** `methods.rs:104-121`） |
| 实测 | `probe-output.json:67-74`：`search_songs` total 1004（同档案下搜索可用，说明设备会话就绪；device.json 里有 `session_uid/sid/vkey`） |

差异：①语义一致（名字→mid/封面/计数），组件还多给 `fanCount`；②**组件不给歌手数字 id**（工程的 `SearchSinger.id` 来自 `singerID`，`SearchApi.kt:53`）——我核过全工程 `SearchSinger.id` **没有消费点**（`Nav.Singer` 只用 mid/name/pic/songNum/albumNum，`ui/Nav.kt:29-35`），所以影响为零；③档案不同：工程走匿名 web（`QqCore.kt:40,SearchApi.kt:31`），组件强制 android + 设备会话（`catalog.rs:1361-1373` 注释：web 档案 `meta.sum=0`）；④`<em>` 两边都剥（`catalog.rs:1468-1470` / `QqCore.kt:137-144`）✓；⑤多歌手：见 A18 的分隔符坑，组件 Track 自带 `singers[]` 更稳。

---

### A22. 后台播放通知封面（另一线程直拉 URL）— **covered**

| | |
|---|---|
| 工程 | `player/PlaybackService.kt:125-137`（`java.net.URL(url).openStream()` → Bitmap → 通知大图）；`PlayerHost.kt:206-215`（MediaMetadata 带 artworkUri） |
| 组件 | 所有封面 URL 强制 https（`methods.rs:987-996`），曲目 `imageURL` 直接可用（`:927`） |
| 实测 | `probe-output.json:36`：`coverIsHttps:true` |

只要宿主把组件的 `imageURL` 写进 `Track.coverUrl`，这条依赖就满足（工程 URL 模板本身也是 https，`Models.kt:20-23`）。

---

## B. 专题小结（接入前必看）

### B1. 逐字歌词的形态差（最容易踩）
组件：**词级 LRC 文本** `[t]词[t]词…`（10ms 精度，`qrc.rs:492-525`），行时长与字时长**丢弃**。
工程：`LyricLine{timeMs,text,words[{startMs,endMs}]}`（`Lyrics.kt:3-30`），渲染按**单字**裁剪（`LyricsView.kt:415-473`），且依赖 `endMs` 做重叠清洗（`LyricApi.kt:288-291`）。
⇒ 宿主需要：①新的「一词一标签 LRC」解析器；②自己合成 `endMs`（下一个词的起点 / 行末）；③决定扫色粒度（词 or 字）。

### B2. 三个被组件丢掉的数据
| 数据 | 工程用途 | 组件 |
|---|---|---|
| `roma`（逐字音译） | 「音」开关 | 写死 `roma:0`，`romanization` 恒 null（`catalog.rs:970`、`models.rs:393`）→ **gap** |
| `file.*`/`size_*` | 格式弹窗、档位过滤 | 完全没有 → **gap** |
| `genre` int 码 | 智能调音 | 只有字符串 `genreTags`（还是要另发 `fetch_song_detail`）→ **gap** |

### B3. 写操作的两个真实风险
1. 组件**不校验 `data.retCode`**（`upstream.rs:245-259`）——可能把上游的业务失败当成功。
2. 业务码被压成错误文本，`1000` 被译成「登录已过期」（`upstream.rs:524-531`）——工程的分级文案与「不自动重试」提示拿不到码。
⇒ 建议写操作走 `call_with_platform("set_liked", …)` 拿原始 JSON，或保留原生写路径。

### B4. 登录侧的最大简化
`euin` 从「硬前提」降级为「可选」：组件 `require_login` 只看 `music_id + music_key`（`credential.rs:46-49`），`encrypt_uin` 缺失时自动回退数字 uin（`upstream.rs:146-172`），收藏读/关注歌手都能跑——实测凭据文件里就没有 `encrypt_uin`（`jq keys` 实跑），`get_login_status` 仍 `loggedIn:true`。工程侧的 `requireEuin()` 与相关提示可以去掉。

---

## C. extras：组件有、工程没用

| # | 组件方法 / 能力 | 能给工程带来什么 |
|---|---|---|
| 1 | `get_status` / `set_rate_limit` / `set_breaker`（`methods.rs:419-445, 468-533`；分桶预算 `guard.rs:36-51`） | 组件自带**分桶限流 + 熔断**（超限是**等待**不是失败，`helpernext-inventory.md:187`），并有 `breaker` 状态与五类用量回显。工程现在只有 120ms 本地节奏闸（`QqCore.kt:73-90`）——可给「诊断日志」页加限流/熔断面板，风控期自动退避。 |
| 2 | `start_login` / `poll_login`（`login.rs:92-169`） | 原生 QQ 扫码（PNG base64 + SCAN/CONF/DONE/TIMEOUT/REFUSE 事件码），不依赖 WebView 里的网页二维码；工程可给设置页加「扫码登录」入口（微信仍未实现）。 |
| 3 | `fetch_recommend_feed`（`catalog.rs:900-916`，`music.radioProxy.MbTrackRadioSvr {id:99,num:5}`） | **专用猜你喜欢端点**，每次 5 首。工程现在靠「电台列表里找标题等于『猜你喜欢』的 station 再取歌」（`HomeScreen.kt:162,221,249`）——组件这条路不依赖标题字符串匹配。 |
| 4 | `search_track_artwork` / `search_artist_artwork` / `search_album_artwork`（`catalog.rs:1504-1620`） | 名字 → 封面候选 + `confidence`（0.86 起每名 −0.04，`:1495-1502`）。工程目前无本地曲库，暂无消费者；一旦做本地文件导入，这是现成的封面匹配。 |
| 5 | `fetch_artist_biography` / `fetch_artist_detail`（`catalog.rs:574-690, 1616-...`） | 歌手页可加简介/外文名/地区/风格标签。⚠️ 组件的 `description` **实测拿不到**（`catalog.rs:570-573` 自己写明 biography 不在 header 响应里，`description` 一直空）——价值主要在外文名/地区。 |
| 6 | `Track.singers[]`（`models.rs:120-127`） | 结构化的多歌手数组（mid+name），替代工程「拼接字符串再按分隔符切」（`PlayerBar.kt:161` 踩过 `, ` 不在切分集合）；播放条的多歌手弹窗可以更稳。 |
| 7 | `resolve_song_url` 的 `expiration` / `tried[]` / `restriction`（`catalog.rs:1102,1107,1119`） | 可解释的取流失败（逐档结果）与票据过期时间——工程只有错误码 + 人话 message。 |
| 8 | `aria2_add/tell/list/pause/unpause/cancel`（`methods.rs:565-612`） | 下载进度（`completed/total/speed`）、暂停/恢复/取消（取消会删临时文件）。工程下载目前只有一句 toast，无进度 UI。⚠️ 引擎不在仓库、FFI 不可用、目录钉死（见 A16）。 |
| 9 | `call_with_platform(method, params_json, platform)`（`api.rs:378-394`） | **绕过 FFI 载荷不匹配的逃生口**（盘点 `helpernext-inventory.md:83` 实测可用），也是工程想保留自建 filename（OGG 档）时唯一能发原始 `musicu.fcg` 请求的入口。 |
| 10 | `fetch_toplist_categories` / `fetch_toplist_tracks` / `fetch_new_songs`（`catalog.rs:703-790, 879-898`） | 排行榜分组/曲目、按地区新歌——工程没有榜单/新歌页，纯增量（属别的域，列出备查）。 |

---

## D. 未验证 / 未执行（如实）

- **未跑任何网络请求、未跑 gradle、未连 adb/模拟器**（按要求）。
- **未实测写操作**：`set_liked` 不在 `probe-output.json` 的 17 行里（`readOnly:true`）；A9 的全部结论来自源码（`catalog.rs:1262-1312`、`upstream.rs:245-259`）。
- **未实测 `start_login`/`poll_login` 的 DONE 路径**（`probe-output.json` 无这两行；与盘点 `helpernext-inventory.md:238` 一致）。
- **未实测 FFI 面**：A1/A4/A5/A9 里「typed wrapper 载荷不匹配、只能 stdio 或 call_with_platform」引自盘点 §4.7（`helpernext-inventory.md:213-219`）；**我自己只核到** `src/api.rs:41-53` 的 `parse` 只解包 7 个键、`methods.rs` 的载荷信封键（`stream`/`detail`/`lyric`+`wordLyric`/`like`）——与盘点结论结构一致，但没有跑过 FFI 调用。
- **未实测 aria2 真正拉起后的字段**（仓库/本机无 `aria2-next`，`find` 实跑确认；盘点同样记录 `installed:false`）。
- **未验证「组件返回的 translation 是否含 `//` 占位」**：源码上 `decrypt(trans)` 是原样文本、无占位过滤（`catalog.rs:997`），但我没有真实样本；工程侧「丢 `//`」的逻辑（`LyricApi.kt:177`）建议保留。
- `lyric.line` 的「行时长丢失」来自源码（`to_word_lrc` 只写起点，`qrc.rs:507-525`），未用真实 `wordLyric` 样本逐行核对。

---

*本文件只写入 `.zcode/research/`；未改动任何工程源码；未运行 gradle；未使用 adb/模拟器；未发起网络请求。*
