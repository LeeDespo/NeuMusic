# 播放与写入域 · 独立复核（反证）
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

复核人：播放域复核员（与出结论者非同一双眼）。**只读**，未修改任何工程文件、组件源码；未跑 gradle / adb / 模拟器；**未发起任何网络请求**。

## 复核边界与方法

| 项 | 内容 |
|---|---|
| 被核材料 | 提问里的 22 条 JSON 结论（id 0–21），原始 verdict 为 partial/gap/covered |
| 组件侧源码 | `/private/tmp/helpernext_probe`（git `2ff7e71`，`git status --porcelain` 干净；catalog.rs md5 `2ccdc2d2…`、methods.rs `a70a11b6…`、qrc.rs `426cbe4f…`、upstream.rs `c8de80cd…`、api.rs `c3a22cff…`、credential.rs `62b742be…`、login.rs `31717898…`、aria2.rs `ee2662b2…`、bin/stdio.rs `74accdda…`） |
| 工程侧源码 | `/Users/mac/Documents/Music_app/app/src/main/java/...`（只读；复核期间 `git status --short` 仅显示别的 agent 的未跟踪文件，无我造成的改动） |
| 组件实测输出 | `/tmp/helpernext-workflow-cred/probe-output.json`（17 行全 ok；凭据文件键 `musicid,musickey,str_musicid`，由我实跑 `jq -r 'keys\|join(",")'` 确认） |
| 跑过的命令 | `grep/find/md5/cat -n/wc`、`jq`、`python3`（算 479÷300 与 479÷100 的分页次数、UTF-8 字节检查）、以及**为验证一个子命题**在 `/tmp/b64check` 编译了 Android SDK `sources/android-37.0/android/util/Base64.java`（剥掉 `UnsupportedAppUsage`/`Ravenwood` 注解后 `javac`）并调用 `Base64.decode(s, Base64.DEFAULT)` —— 见 id 7。这是唯一在工作区外产生的临时文件，未触碰工程与组件目录 |
| 未做的 | 未跑 cargo/rust 测试；未实测任何写操作（与 AGENTS.md 的限流教训一致）；未解真实上游的 `trans` 密文（无网络） |

## 结论总表

| id | 能力 | 我的 verdict | 一句话 |
|---|---|---|---|
| 0 | 播放直链 + 音质降级 | confirmed | 逐条属实；4 档 vs 6 档、无 file 过滤、返回值而非异常、101404/22 无分支均核对 |
| 1 | 音质档位定义 | confirmed | 组件确无等价物；但「唯一入口 call_with_platform」也发不出 OGG（见下） |
| 2 | 失败人话文案 | confirmed | 结构化 restriction 属实；「拿不到原始数字码」需更正：数字在错误文本里 |
| 3 | 歌曲详情/简介 | confirmed | 端点/字段/`\n` 拼接/空即答案全对；「默认档案与工程 commWeb 一致」需更正（ct/cv 不同） |
| 4 | 逐字歌词 | confirmed | 形态与宿主代价属实；两处需收紧（扫色粒度、10ms 截断） |
| 5 | 翻译 | confirmed | 「关不掉」实锤；`//` 过滤、宿主自算门控属实 |
| 6 | 音译 roma | confirmed | roma 写死 0、romanization 恒 None、注释与实现不符——全对 |
| 7 | 注音 kana | confirmed（含一处被推翻的子句） | 数据在 translation 里属实；**「靠 decodeLrc 的 getOrElse 兜底可容纳」被实测反例推翻** |
| 8 | 喜欢写入 | confirmed | retCode 不查、按成功返回、1000 被译文本——全对；另发现 tmeLoginType 与工程不同 |
| 9 | 我喜欢列表 | confirmed | limit≤100、不需 euin（实测 479 条）、total 兜底——全对 |
| 10 | 封面 URL | confirmed（含一处更正） | 归一化更全、Option vs 空串、通知 https 属实；「曲目用上游原始 imageURL」错 |
| 11 | 网页 cookie 登录 | confirmed | uin+qm_keyst 足够、euin 降为可选、实测三键凭据——全对 |
| 12 | 扫码登录 | confirmed | 事件码/PNG/qrsig/android+tmeLoginType:2、无微信——全对 |
| 13 | 退出登录 | confirmed | dispatch 分支不删文件属实（stdio 拦截行号需更正为 192-197） |
| 14 | 昵称 | confirmed | 同端点同字段源 nick；NicknameCache.get 确无消费点 |
| 15 | 下载 | confirmed（计数更正） | 引擎缺席报错、目录钉死、二进制不在——对；aria2_* 是 9 个不是 11 个 |
| 16 | 已下载台账 | confirmed | 组件确无 mid→已下载 概念 |
| 17 | 曲目字段 | confirmed | ", " 分隔坑、mediaMid 可空+兜底、payPlay Option——全对（引用行 35→33） |
| 18 | fileSizes | confirmed | 组件确无此数据 |
| 19 | genre 风格码 | confirmed | 只有字符串 genreTags，与 int 码表不可换算 |
| 20 | 搜歌手 → 歌手页 | confirmed | 无数字 id 且工程无消费点、强制 android——全对 |
| 21 | 通知封面 | confirmed | 全 URL 强制 https，实测 coverIsHttps:true |

---

## 逐条证据

### id 0 播放直链 + 音质档位降级 — confirmed
- 同端点 `music.vkey.GetVkey/UrlGetVkey`、同 CDN：组件 `catalog.rs:1070-1086`（module/method）与 `catalog.rs:17`（`STREAM_CDN=https://isure.stream.qqmusic.qq.com/`）；工程 `SongApi.kt:107`、`SongApi.kt:110`。档位探测、遇到非 0 结果继续下一档：`catalog.rs:1065-1109`（循环内只 `push` 不 `break`）。
- 4 档：`catalog.rs:25-30`（`flac/M800/M500/C400`）；工程 6 档 `Prefs.kt:11-17`（多 `O600/O800`）。传未知 label 静默回退整链：`catalog.rs:1054-1061`（`find(...).map(from_ref).unwrap_or(QUALITY_LADDER)`）——实跑 `grep '"O600"\|"O800"'` 组件源码零命中。
- 无 `file.*` 概念、不做档位过滤：`catalog.rs:1065-1069` 每档直接拼 filename 发一次请求；`grep -rn 'size_' src/` 只命中 `models.rs:63`/`aria2.rs` 的 `min_split_size_mib`。工程侧过滤见 `SongApi.kt:56-58`、`SongApi.kt:87-93`。
- 不可播 = 返回值不是异常：`catalog.rs:1110-1122`（`playable:false` + `restriction` + `tried[]` + `reason`），`classify_restriction`（`catalog.rs:1126-1149`）的判据是「全部 tried 都是 104003」，不是工程的「最便宜档是 104003」（`SongApi.kt:74-79`）——即「该判定不替宿主做」成立。
- 101404/22 无单独分支：`catalog.rs:1151-1158` 的 `describe_result` 只有 0/104003/104004/104013，其余 `错误码 {other}`（`:1157`）；实跑 `grep -rn "101404" src/` 零命中。
- FFI typed `resolve_song_url` 属载荷不匹配：dispatch 包 `{"stream": …}`（`methods.rs:350-359`），`api.rs:41-53` 的 `parse` 只解包 7 个键（无 `stream`）→ `StreamResolution.song_mid` 必填（`models.rs:401-410`）导致 `missing field`；`call_with_platform`（`api.rs:378-394`）返回原始 JSON 可用。实测呼应在 `probe-output.json:141-149`（playable/flac/F000/isure 主机）。

### id 1 音质档位定义 — confirmed
- 组件只有 4 个 label（`catalog.rs:25-30`），无 O600/O800，无「该曲有哪些档位」数据（同上 grep）。
- **更正一处**：最后一句「唯一能发原始 musicu.fcg 请求的入口是 call_with_platform」不准确——`call_with_platform` 也只进 `methods::dispatch`（`api.rs:392`），而 dispatch/stdio 都受 `METHODS` 名单约束（`methods.rs:85-87`），全组件没有任何方法接受 `filename`/任意 param 透传（`grep '"filename"'` 仅 `catalog.rs:1079/1100` 组件自建）。所以 OGG 两档**在组件上完全无法取得**（连 call_with_platform 也不行），只能保留工程原生请求。

### id 2 失败人话文案 — confirmed
- 结构化 `restriction`（paid_required/device_restricted/ticket_required/unavailable）+ 逐档 `tried[]`：`catalog.rs:1119-1121`、`catalog.rs:1126-1149`；测试 `catalog.rs:1679-1688`。
- 风控码压文本：`upstream.rs:524-531` —— `2001 → 触发风控…`（`:527`）、`1000|104401|104400 → 登录已过期，请重新登录`（`:528`）；`call_with` 把它们变成 `UpstreamError::Upstream("上游返回错误（{code}）：{reason}")`（`upstream.rs:250-251`）。
- **更正一处**：说「宿主拿不到原始数字码」不准确——数字码就嵌在错误文本 `上游返回错误（1000）：…` 里，可以再解析（这也正是该条自己说的「需自己再解析」）；准确说法是「拿不到结构化码，只有兜底文案里的数字」。工程侧分级文案见 `TrackRow.kt:337-343`（1000→「操作太频繁…」）。

### id 3 歌曲详情/简介 — confirmed（四类抽查之「歌曲简介」）
- 端点/路径/拼接全对：`methods.rs:129-141` 派发 → `catalog.rs:219-228`（`music.pf_song_detail_svr/get_song_detail_yqq`，param `song_mid`）→ `content_group(&info,"intro")`（`catalog.rs:123-135` 取 `content[].value`）→ `.join("\n")`（`catalog.rs:264`）；空即答案（无 error，description 为空串）。工程同路径：`SongApi.kt:119-133`。
- 多给字段与溯源键：`catalog.rs:256-270` + `with_provenance`（`catalog.rs:84-93`）；`models.rs:286-311`。
- `configure(default_platform)` 在 dispatch 是死配置：`methods.rs:401` 用 `crate::upstream::Platform::default()`（=Web，`upstream.rs:35-39`）；实跑 grep 显示 `default_platform()` 只在 `lib.rs` 定义/测试与 `stdio.rs:52` 赋值处出现，无调用点。
- FFI typed `song_detail` 报 `missing field songMid`：`api.rs:222-224` → dispatch 包 `{"detail":…}`（`methods.rs:141`）→ `api.rs:41-53` 无 `fetch_song_detail` 解包 → `models.rs:294-295` 必填 `song_mid`。结构性成立。
- **更正一处**：`默认档案 Web 与工程 commWeb 一致` 不准确。组件 Web 档案 = `ct:24/cv:4747474/platform:yqq.json`（`upstream.rs:60-65`）；工程 `commWeb` = `ct:19/cv:1873`（`QqCore.kt:40`）。两者不是同一个 comm（端点两边都能答，`probe-output.json:151-158` ok:true 且返回 detail 对象），但这句不能当「同 comm」用。

### id 4 逐字歌词 — confirmed（含两处收紧）
- 「词级 LRC」形态：`qrc.rs:507-525`（每词前一个 `[mm:ss.cc]`），测试 `qrc.rs:603-609` 断言 `[00:01.00]你[00:01.20]好`；解析代价实锤：`LyricApi.kt:330-350`，`val text = line.substring(tags.last().range.last + 1)`（`:337`）——直接喂 `[00:01.00]你[00:01.20]好` 会得到两条 (1000,"好")、(1200,"好")，前面词全丢，与结论一致。
- 丢弃行时长/字时长、宿主需合成 `endMs`：`qrc.rs:507-525` 只写 `word.start_ms`；工程依赖 `Lyrics.kt:29`、重叠清洗 `LyricApi.kt:288-291`、扫色插值 `LyricsView.kt:398-403`。
  - **收紧一处**：说「扫色粒度从字退化成词」对 CJK 不成立——QRC 把中文按**单字**切（`qrc.rs:589-601` 的测试即是 `你(1000,200)好(1200,300)`），用「下一词起点」合成 end 后中文仍是逐字扫色；只有拉丁文才是词级。真正的损失是**词时长被量化**、且每行/全曲最后一个词没有可靠的 end。
  - **收紧一处**：「工程 parseLrc 两位小数可无损读回」——组件 `lrc_timestamp` 是 `milliseconds/10` 截断（`qrc.rs:492-500`，最多丢 9ms），对「组件已输出的值」读回无损，对**原始 QRC 毫秒**是有损的（10ms 量化）。
- `wordLyric:null` 不是错误 + 明文整行兜底：`methods.rs:262`、`methods.rs:247-251`、`catalog.rs:1001-1029`（fcgi `nobase64=1` 明文）。
- 实测数字对得上：`probe-output.json:122`（544）、`:134`（596）、`:108-110`（有的曲没有逐字）。
- 可替掉 hex/base64 分流：成立（组件返回解密后的文本；工程分流在 `LyricApi.kt:88-96`）。

### id 5 翻译 — confirmed
- 能拿到且实测：`probe-output.json:123`（`hasTranslation:true`）。
- 组件不分行、宿主自 parse+merge：`catalog.rs:997`（decrypt 后整段）→ `methods.rs:252-259`（覆盖 `lyric.translation`）；工程 mergeTranslation 可复用（`LyricApi.kt:168-180`）。
- `//` 占位组件不丢：组件侧无任何 `//` 过滤（实跑 grep 无命中）；工程过滤在 `LyricApi.kt:177`（`NO_TRANS_PLACEHOLDER`，`:62` 定义）。成立。
- 门控 `hasTranslation` 由宿主自算：组件无该字段（`models.rs:390-395` 只有 translation 原值）。
- 「关不掉」实锤：`methods.rs:233-240`（`want_words || want_translation` 才取加密路；加密路 param 写死 `trans:1`，`catalog.rs:972`），且 `methods.rs:255-259` **无条件**把 `encrypted.translation` 覆盖进去——即使 `translation:false`，只要 `wordTiming=true` 就会带回翻译。
- 一处附注（未能独立证实、也没有反例）：`trans` 解密后到底是「裸 LRC」还是与 `lyric` 同款 XML 壳，组件只有注释与 `docs/parsing.md:124` 的间接说法（`catalog.rs:995-997` 只做 `decrypt_hex`）；`probe-output.json` 只报 `hasTranslation/hasKanaMeta` 布尔值，未落原文。这不影响本行的操作性结论。

### id 6 音译 roma — confirmed
- 写死 0：`catalog.rs:970`（`"roma": 0`）；实跑 `grep -rn 'roma\b' src/` 只有这一处。
- `EncryptedLyrics` 只有 word/translation：`catalog.rs:933-939`；`Lyric.romanization` 恒 None：`catalog.rs:1028` + `models.rs:393`，`methods.rs:252-259` 只合并 translation。
- 实测三首全 false：`probe-output.json:113/125/137`——是写死 roma:0 的必然结果，**不能**证明上游无数据（工程 `LyricApi.kt:107-110` 用 `roma:1` 拿到过逐字罗马音，AGENTS.md 也有实测记录）。
- 「注释与实现不符」：`api.rs:326-327` 明说 “asks for the translation and romanisation”，实现（`catalog.rs:1028`）恒 null。属实。
- 宿主只能自己再取：成立；且**组件没有任何路径能发 `roma:1`**（param 硬编码 + 无 raw 透传），必须保留工程原生 `LyricApi`。

### id 7 注音 kana — confirmed（一处子句被实测推翻）
- 数据在翻译里：`probe-output.json:124`（`hasKanaMeta:true`）；组件不解析：实跑 `grep -rin kana`（排除 target）**零命中**。
- 工程解析器可接上：`LyricApi.kt:202-209`（parseKanaTokens）读 text 找 `[kana:…]`，与壳无关。
- **被推翻的子句**：「靠 `LyricApi.kt:165` 的 `getOrElse { raw }` 兜底可容纳」。我编译了 Android SDK（android-37.0）的 `android/util/Base64.java`（仅剥注解）实测 `Base64.decode(s, Base64.DEFAULT)`：
  - `[00:12.34]君の名は\n[kana:1き1み2のな1まえ]` → **OK**，返回 10 字节乱码（不抛异常 → `getOrElse` 永远不触发，`[kana:` 被乱码吃掉）；
  - `[00:01.00]君の名は\n[00:05.00]夢を追いかけて\n[kana:…]` → **OK**（24 字节乱码）；
  - 也有抛异常的例子（如含 `"`、ASCII 长度 ≡3 mod 4 的样本，`mixed-3char → THROW`）。
  命令：`$JAVA_HOME/bin/javac -d . android/util/Base64.java T2.java && $JAVA_HOME/bin/java -cp . T2`。结论：宿主**必须显式跳过/绕过 `decodeLrc`**（例如组件来源的 translation 直接当明文用），不能依赖 getOrElse 兜底——否则注音在多数日文曲上会静默消失。这是本行唯一的实质性纠错，其余成立。

### id 8 喜欢写入 — confirmed（四类抽查之「喜欢写入」）
- 同端点同参数、同强制 android、都不自动重试：`catalog.rs:1296-1309`（`dirId:201/tid:0/bFmtUtf8/v_songInfo[{songId,songType}]`）、`methods.rs:104-118`（`set_liked` 在强制 android 名单）、工程 `SongApi.kt:146-154`；实跑 `grep -rn "retry\|重试" src/` 无写重试逻辑。
- ①从不看 `data.retCode`：`upstream.rs:242-259` 只看 `req_0.code`（`refusal_reason`）与 data 是否为空对象；`catalog.rs:1296-1311` 拿到 data 后直接 `Ok(json!({"songId":…,"liked":…}))`（`:1311`）。外层 0、内层 retCode≠0 时**组件当成功返回**——成立。工程必须两者都查：`SongApi.kt:155-159`（注释即「只看外层会误报」）。
- ②业务码压文本：同 id 2（`upstream.rs:528`）。工程 `LikeResult.Rejected(1000)` 的文案（`TrackRow.kt:338-343`）在组件侧拿不到结构化码——成立（数字仍在文本里，同 id 2 的更正）。
- 组件接受 mid 并自解析数字 id：`catalog.rs:1276-1295`（songId≤0 时走 `song_detail` 取 `songId`）；FFI 只暴露 mid 且 `Result<(),_>`：`api.rs:398-404`。
- 未实测：`probe-output.json` 17 行里确无 `set_liked`（我列过 method 列表）。
- **额外发现（风险，未实测）**：组件的 android comm 写 `tmeLoginType:1`（`upstream.rs:371`），而工程 `commAuth()` 写 `tmeLoginType:2`（`QqCore.kt:53`）——AGENTS.md 明说「换 comm 是这类写操作成败的关键」。其余 android 字段（ct:11/cv:14090008）一致，但这一处差异是否影响写成功**无法在本轮验证**（未实测写）。集成前应先做一次受控实测。

### id 9 我喜欢列表 — confirmed
- 分页 page/limit、limit clamp 1–100：`methods.rs:667-668`。工程每页 300（`LikedStore.kt:41`）→ 479 首 2 次；组件 100/页 → 5 次（479=100×4+79）。算术成立。
- 不要求 euin：`methods.rs:666` 只 `require_login`（`methods.rs:857-863` + `credential.rs:47-49` 只看 music_id/music_key）；且组件请求体**不含** `enc_host_uin`（`methods.rs:675-683`），实测凭据文件只有三键（jq 实跑）仍 `total:479`（`probe-output.json:30`）——工程「缺 euin 收藏不可用」的约束在组件上确不存在（工程侧约束：`PlaylistApi.kt:24-28`、UI 提示 `SettingsScreen.kt:855-857`）。
- total 可能被本页条数兜底：`methods.rs:690`（`.unwrap_or(tracks.len() as i64)`）——不能当「到底了」判据。成立。
- `coverIsHttps:true`：`probe-output.json:36`。

### id 10 封面 URL — confirmed（一处更正）
- https 归一化更全：`methods.rs:987-996` 同时处理 `http://` 与协议相对 `//`；工程 `QqCore.kt:128-131` 只处理 `http://`。成立。
- 空值形态 Option(None)：组件 `models.rs:146-147`（image_url Option）、`models.rs:174-176`（cover_url Option）；工程占位判断按空串 `Cards.kt:84`（`if (pic.isEmpty())`）、`Cards.kt:128`（`if (logo.isEmpty())`）——宿主必须 None→orEmpty()，否则首字母占位/爱心不触发。成立。
- **更正一处**：说「组件曲目用上游原始 imageURL（methods.rs:927，可能非 300）」**不对**。`methods.rs:927` 是 `normalized_artwork_url(album_mid.map(album_cover_url))`，即**专辑模板** `T002R800x800M000{mid}.jpg`（`methods.rs:979-981`；组件自己的单测 `methods.rs:1035-1038` 断言的就是这个 R800x800 串）。结论里「尺寸档与工程 R300x300 不同」依然成立（800 vs 300，工程见 `Models.kt:20-23`），但原因是模板档位不同，不是「原始 URL 可能非 300」。
- 通知封面依赖纯 https 直链：工程用 `java.net.URL(url).openStream()`（`PlaybackService.kt:126-136`）；实测 `coverIsHttps:true`（`probe-output.json:36`）。

### id 11 网页 cookie 登录 — confirmed
- 只需 uin+qm_keyst：`credential.rs:180-198`（`qm_keyst` 必需，`uin`/`qqmusic_uin`/`musicid` 任一），`encrypt_uin` 选填；FFI `api.rs:69-78`。
- euin 降为可选：`credential.rs:47-49`（`is_usable` 只看 id+key）；缺 encrypt_uin 时关注歌手回退数字 uin：`upstream.rs:146-172`、`methods.rs:816-821`。
- 实测：凭据文件三键（我实跑 jq）、`get_login_status` 仍 `loggedIn/hasPlaybackKey`（`probe-output.json:20-23`）。
- 宿主需改的三点与工程现状对得上：凭据存 SharedPreferences（`Prefs.kt:361-366`）、设置页账号显示（`SettingsScreen.kt:188`）、euin 分级提示（`SettingsScreen.kt:855-857`）；`get_login_status` 真问上游（`methods.rs:625-655`，成功取 `info.nick`）。

### id 12 扫码登录 — confirmed
- PNG base64 + qrsig：`login.rs:92-121`（`identifier=qrsig`、`mimetype=image/png`、STANDARD base64）。
- 事件码：`login.rs:44-56`（枚举）+ `:58-79`（`0|405→DONE, 66|408→SCAN, 67|404→CONF, 65|402→TIMEOUT, 68|403→REFUSE`，字符串 `SCAN/CONF/DONE/TIMEOUT/REFUSE`）。
- 最后一步 android+tmeLoginType:2：`login.rs:242-246`。
- 只有 QQ：`docs/endpoints.md:104`；实跑 `grep -rin "wechat\|微信"`（源码）零命中。
- 未实测 DONE 路径：`probe-output.json` 17 行无 start_login/poll_login。工程 WebView 登录仍在（`SettingsScreen.kt:839-857`），可并存。

### id 13 退出登录 — confirmed
- dispatch 的 logout 不删文件：`methods.rs:449`（只回 `loggedIn:false`）；stdio 才真删：**实际行号 `bin/stdio.rs:192-197`**（原结论写 180-184，是 aria2 段的行号，需更正引用）；FFI typed `logout()` 也真删（`api.rs:82-86` + `credential.rs:145-164/166-172`）。「按普通 method 派发或 call_with_platform("logout") 会退出后重启仍登录」成立（`api.rs:392` 进 dispatch）。
- 四份缓存：`SettingsScreen.kt:199-207` 清 ApiCache/LikedStore/HomeCache/TrackListCache。

### id 14 昵称 — confirmed
- 同端点同字段：组件 `methods.rs:629-635` + `:652`（`nick`）；工程 `UserApi.kt:46-50`（同 module/method + `info.nick`）；实测 `hasNickname:true`（`probe-output.json:23`）。
- 一次调用另带 musicId/vipType/expired/hasPlaybackKey：`models.rs:209-222`。
- `NicknameCache.get()` 无消费点：实跑 `grep -rn NicknameCache` → 只有 `SettingsScreen.kt:852`（set）、`Prefs.kt:365/370`（set null）、定义处；`get()` 零调用。主页问候语明确不带用户名（`HomeScreen.kt:103-104,215-217`）。成立。

### id 15 下载 — confirmed（计数更正）
- 引擎缺席报错、退回自有方式：`aria2.rs:174-185`（`ensure_running` 找不到二进制报错）、`docs/endpoints.md:90`；实跑 `find /tmp/helpernext_probe -maxdepth 3 -name 'aria2*'` → **只输出 `/tmp/helpernext_probe/src/aria2.rs`**；`dist/` 只有 `android/{include,kotlin,jniLibs}`，三个 checkout 的 repo 里都无 `aria2-next` 二进制。
- 目录钉死：`aria2.rs:212-213` + `:223`（`--dir=<组件目录>/Downloads`）；工程三选目录 `Prefs.kt:47-51`。
- **计数更正**：结论写「FFI 面 11 个 aria2_* 全不可用」——aria2_* 实际是 **9 个**（`methods.rs:42-50` 与 `api.rs:520-586` 都是 9 个）。「11」来自盘点把 `set_rate_limit`/`set_breaker` 一起算的「11 个命令面方法」。核心事实成立：dispatch 的 match（`methods.rs:405-458`）完全没有 aria2 分支，`is_known`（`methods.rs:85-87`）只认 METHODS，所以 FFI/`call_with_platform` 调 `aria2_*` 一律 `不支持的方法`；只有 stdio 在 `bin/stdio.rs:154-191` 拦截。
- add 只收 url+out、命名/MIME/非法字符归宿主：`methods.rs:565-580`；工程 `Downloader.kt:34`（displayName+非法字符替换）、`:51`（MIME）。
- 能补进度/取消（取消删临时文件）：`aria2.rs:355-369`（completed/total/speed）、`aria2.rs:422-447`（forceRemove + 删 `.aria2` 临时文件）；`models.rs:73-99`。

### id 16 已下载台账 — confirmed
- 组件无「该 mid 是否已下载」概念：`Aria2Task`（`models.rs:93-109`）只有 gid/status/completed/total/speed/name/path/error；`aria2_list`（`aria2.rs:376-395`）是引擎任务合并。工程台账以 mid 为键（`DownloadStore.kt:22-26`），命名规则 `singer - name.ext`（`Downloader.kt:34`）与 `out` 不互认（out 是宿主自定义文件名，组件不落 mid）。「接组件后仍要自己维护」成立。

### id 17 曲目字段 — confirmed
- 实测 `firstHasMediaMid:false` 在 **`probe-output.json:33`**（原结论引用 :35，`:35` 是 `firstDuration`；行号更正）；`resolve_song_url` 的 `{songMid}{songMid}` 兜底 `catalog.rs:1066-1069`——且 `probe-output.json:141-149` 的 playable:true 正是这条兜底路径（probe2 传的 mediaMid 为 null）跑出来的。
- 多歌手 `", "` 连接：`methods.rs:907-911`；工程切分集合 `"/","、",","`（`PlayerBar.kt:161`，UTF-8 实查：`/`、`、`(U+3001)、`,`(U+002C)）——两个中文名会被当成一个名字，`SingerApi.resolve` 精确匹配失败（`SingerApi.kt:29-32`）。成立。组件另有 `singers[{mid,name}]`（`models.rs:120-127`、`methods.rs:896-906`）。
- 无 fileSizes/genre；`payPlay` Option<i64>（`models.rs:150`）vs 工程 Boolean（`QqMapper.kt:58`）；必填仅 songMid（`models.rs:136`）。成立。

### id 18 fileSizes — confirmed
- 组件无此数据：实跑 `grep -rn 'size_'`（src）只有 `min_split_size_mib`；`decode_track`（`methods.rs:889-933`）不产 file 字段，`Track`（`models.rs:133-155`）无对应字段。工程用途：格式弹窗 `TrackDialogs.kt:210/236`、降级过滤 `SongApi.kt:57/87-93`、`Models.kt:16`。两条用途都失去数据源。

### id 19 genre 风格码 — confirmed
- `Track` 无 genre（`models.rs:133-155`）；组件的 `genreTags` 是字符串数组：`catalog.rs:251-254` + `split_tags`（`catalog.rs:692-698`，按 `; , / |` 拆），单测 `catalog.rs:1244-1246`（`"Rock; Pop/Blues"`→三个标签）。
- 与工程 int 码表不可换算：`Models.kt:17-18`（int 码）、`SmartEq.kt:20-33`（实测校准表）、`EqualizerHost.kt:194-209`（映射可改）。
- 取数代价：`genreTags` 只在 `fetch_song_detail`（`catalog.rs:265`）/专辑（`:387-389`）/歌手（`:673`），没有列表接口免费带；工程是列表项直接取（`QqMapper.kt:61`）。
- 实测 `genreIsArray:false`（`probe-output.json:157`）——载荷键叫 `genreTags`，不能当「genre 可用」。成立。

### id 20 播放条/播放页歌手名 → 歌手页 — confirmed
- 语义一致、多 fanCount：`catalog.rs:1430-1444`（`singerMID/singerName/singerPic/songNum/albumNum/fansNum`）。
- 无数字 id 且工程无消费点：`map_artist` 无 id；`SearchSinger.id`（`Models.kt:52-59`）只由 `SearchApi.kt:53` 赋值，实跑 grep 全工程无 `.id` 读取；`Nav.Singer` 只要 mid/name/pic/songNum/albumNum（`Nav.kt:29-35`），消费点 `SearchScreen.kt:650-652`、`AppRoot.kt:423/613`、`PlayerBar.kt:156`。影响为零成立。
- 档案不同：工程匿名 web（`SearchApi.kt:31` 用 `commWeb`=`QqCore.kt:40`）；组件强制 android（`methods.rs:104-118`）+ 设备会话（`catalog.rs:1359-1373`、`upstream.rs:398-437`）。实测 total:1004（`probe-output.json:70`）。
- em 高亮两边都剥：组件 `strip_highlight`（`catalog.rs:1468-1469`）、工程 `cleanHighlight`（`QqCore.kt:137-140`）。

### id 21 后台播放通知封面 — confirmed
- 组件所有封面出口都过 `normalized_artwork_url`：`catalog.rs:246/368/544/646/741/825/1437/1455/1482`、`methods.rs:766/795/850/927`（归一化只在 `methods.rs:987-996`，`http://` 与 `//` 都改写）。
- 实测 `coverIsHttps:true`（`probe-output.json:36`）。
- 工程「另一线程直接 GET」的要求：`PlaybackService.kt:126-136` 用 `java.net.URL(url).openStream()`，无鉴权头；https 直链满足。

---

## 抽查覆盖（提问要求的四类）

| 抽查类 | 对应结论 | 我的核对 |
|---|---|---|
| 取流与音质降级 | id 0 | 端点/CDN/阶梯/104003 不中断/无 file 过滤/返回值语义/FFI 载荷不匹配 + 实测 `probe-output.json:141-149` |
| 整行歌词 | id 4（部分）、id 5 | 明文 fcgi 路 `catalog.rs:1001-1029`（nobase64=1、直接文本）；`wordLyric` 与 `//` 过滤；工程 parseLrc 兼容明文 |
| 喜欢写入 | id 8 | 端点/参数/android 强制/不重试/retCode 不查/1000 文本化/FFI 签名；未实测写 |
| 歌曲简介 | id 3 | 端点/`info.intro.content[].value`/`\n` 拼接/空即答案/多给字段/FFI 载荷不匹配 |

## 我未能证实 / 未做的（不填猜测）

1. **未实测任何写操作**（`set_liked`）：结论 id 8 全部来自源码；`probe-output.json` 无该行。组件 android comm 的 `tmeLoginType:1` 与工程 `:2` 的差异对写成败的影响未验证。
2. **未解真实上游的 `trans` 密文**：组件返回的 translation 是「裸 LRC」还是带 XML 壳，只有组件注释/文档的说法，`probe-output.json` 未落原文；不影响 id 5 的操作性结论，但宿主解析前应先打印一次确认。
3. **未跑组件自身的 cargo 测试**（qrc known-answer 等只在源码里读过）：引用测试均标注为「源码内测试」而非我跑过。
4. **未做逐字的端到端还原**：未把组件 `wordLyric` 实际喂给工程解析器（无网络、无设备），「解析代价」结论来自两侧源码对照 + 对 `parseLrc` 行为的静态推演。
5. 工作区外的临时文件：`/tmp/b64check`（编译 Android SDK Base64 副本 + 小测试类）。工程目录与组件目录均未被修改。

## 复核结论

22 条中 **21 条完全成立（confirmed）**，1 条主体成立但含一处被实测反例推翻的子句（id 7，「靠 decodeLrc 的 getOrElse 兜底可容纳」）。另有 4 处需更正的细节：id 3 的「comm 一致」（ct/cv 不同）、id 10 的「曲目用上游原始 imageURL」（实为 R800x800 专辑模板）、id 15 的「11 个 aria2_*」（实为 9 个）、id 13/17 的行号引用（stdio logout 在 192-197；`firstHasMediaMid` 在 probe 第 33 行）。没有发现使任何一条 capability 判定反转的反例；被核结论的 partial/gap/covered 定性在我的独立核对下均维持。
