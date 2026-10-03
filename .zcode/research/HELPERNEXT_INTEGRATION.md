# HelperNext 接入调研报告（QQMusicApi_HelperNext → NeuMusic）

> 调研日期 **2026-10-02**（**2026-10-03 定稿轮**：按独立通读反馈补齐口径与交叉引用；结论与「复核 confirmed／经独立复核修正」的判定未改）；组件提交 **2ff7e71**；工程提交 **b6c5c29**（工程同期正被另一个 agent 修改；该工作其后已提交为 **f6b2255**，工程侧的平移规则见下表）。
> 本报告为**只读调研**：未改动工程任何文件（只新建本文件）、未跑 gradle、未连 adb/模拟器、未改组件源码。
>
> **行号基准与平移规则（读前必看）**
>
> | 侧 | 基准 | 漂移情况 | 怎么用本文的行号 |
> |---|---|---|---|
> | 组件 | `2ff7e71`（工作副本 `/tmp/helpernext_probe`） | 本地仓库 `/Users/mac/Documents/QQMusicApi_HelperNext` 已到 `5cb2e82`（另有未提交改动 `src/port/{comment,mv,singer_extra}.rs`），本次实跑 `wc -l` 逐文件测得：`src/api.rs` +8（596→604）、`src/upstream.rs` **+145**（578→723）、`src/methods.rs` +13（1092→1105）、`src/catalog.rs` **0**（1699，md5 两处同为 `2ccdc2d2…`） | **不是统一的 +13**：`api.rs` 的 `parse` 在 41→49、`upstream.rs` 的 `profile_assets` 在 273→343（实跑 `grep -n`）。引用行号前先按文件查漂移；`catalog.rs` 可直接用 |
> | 工程 | `b6c5c29` | 本报告撰写时工作区差异为 `PlayerScreen.kt` 1 文件（77+/43−）；该工作已提交为 **f6b2255**（2026-10-03 实跑 `git diff --stat b6c5c29 HEAD -- app/src/main` → 2 files changed, 840 insertions(+), 63 deletions(-)：`PlayerScreen.kt` 80+/63−、新增 `VinylTurntable.kt` 760+/0），工作区相对 HEAD 无改动（`git status --short -- app/src/main` 空） | 本文引用的工程行号**一律是 b6c5c29 行号**。`PlayerScreen.kt` 两版本行数 763→780，位移**不统一**（非单调、区间各异）：**上文实际引用的两处整段下移 +11**——`:246-267`（译/音/注开关）→ **`257-278`**、`:398-412`（红心）→ **`409-423`**（2026-10-03 以 `git show <rev>:<path> | sed -n` 取两段做 `md5` 比对，各自相同：`b8e203a5231bb847db25f054116ff443`、`d359b33a06a7a85759406122cb2b893e`）。**除这两处外不要套用 +11**：`1-144` 行区间位移在 −1..+2 之间，`145` 以后开始变化，`483` 以后出现 +13/+25/+26 与行块移位混杂（如 `512` → `571`）。其余工程文件两版本一致，b6c5c29 行号可直接用；`PlayerScreen.kt` 的其它行号请用 `git show b6c5c29:<path>` 核对 |

---

## 你要的结论

**能接，而且工程现有在线能力大多在组件里有对应方法——但不能"整体替换"，当前只能"部分替换 + 保留原生兜底"。** 逐条核对 42 行里 covered 10、partial 27、gap 5（§4.3）。读取侧大部分能力与工程同一上游端点/字段；**但"逐字对得上"只对其中一部分成立**（口径：指端点 + 参数 + 字段名逐项相同）——§4.1「搜索·歌手」「搜索·歌单」「专辑内曲目」「歌手解析」、§4.2「歌曲详情/简介」「后台播放通知封面」是逐字对得上（另有「我喜欢总数」仅总数语义差）；同端点但有字段/参数差的（如「搜索·歌曲」的 highlight、「我喜欢列表」的 page/limit）见各行的 partial 备注；而**收藏的歌单/专辑换了端点（老 fcgi）、电台曲目换了上游方法（`GetRadiosonglist` vs `get_radio_track`）**（§4.1 对应行），这三条需要额外验证或接受差异。**缺口分两组，不要混用**：① **数据字段类 3 项**（`fileSizes`、`genre` int 码、`roma` 逐字音译）——组件 `Track`/`EncryptedLyrics` 里根本没有，改参数无解；② **实现缺陷/覆盖面类 2 项**（`mediaMid` 恒 `null` 的源码 bug、OGG 两档在组件音质表里没有）——前者改组件一行可修，后者只能保留原生请求或放弃（两组与 §4.3 的 5 行 gap 是不同切法，对齐表见 §4.3）。**逐字歌词交付形态**从「行+字对象」变成「词级 LRC 文本」，宿主必须写新解析器并自己合成 `endMs`。**必须先决定四件事**：① 许可与链接形态（组件 GPL-3.0-or-later、以 `--whole-archive` 静态链进同一个 `.so` 随 APK 分发，构成组合作品还是聚合使用——本报告并列两种解读，不下法律定论，且工程 `README.md:61` 仍写 "License TBD" 与 `AGENTS.md:79` 的 GPL-3.0 声明不一致）；② 是否推动上游修组件（FFI 面 47 个导出里 31 个不可用、11 个命令面方法在 FFI 路径是死的、`configure` 无 FFI 入口）；③ Android 上数据目录在**调研范围内**只能靠 `Os.setenv`（在**首次调用组件之前**设好；本报告未穷举其它手段，见 §2.2）；④ 音译/OGG 档/写操作回执这些组件给不了或给得不完整的能力，是保留原生直连还是放弃。

---

## 一、结论摘要

1. **接入路径唯一**：Android 上只能走 **FFI/JNI**。子进程路径不可行不是因为没实现，而是因为交付物里没有可执行文件（`pack android` 只产 4 个 `.so` + 头 + Kotlin + JNI 胶水，8 个文件、0 个可执行；boltffi 对每个 target 走 `--lib`），且 Android 10 起应用主目录禁止 `execve`（官方原文，工程 `targetSdk = 35` 适用）。（`verify-mechanics.md` §3，复核 verdict = confirmed；例外路径"解到 `nativeLibraryDir` 再 exec" **复核未能证实**。）
2. **能力覆盖面上"高但不满"**：逐条核对 42 行工程能力中，**可直接取代（covered）10 行**、**部分覆盖（partial）27 行**、**缺失（gap）5 行**（明细与计数见 §4.3）。covered 集中在搜索歌手/歌单、我喜欢总数、歌手解析、电台分组、昵称、歌曲简介、扫码登录、通知封面；partial 的主因高度集中——曲目字段窄、若干总数丢失、分页语义从 offset 变 page、`latest` 只页内排序、FFI 载荷不匹配。
3. **缺口分两组（避免与 §4.3 的 gap 行混用）**：**数据字段类 3 项**——① `Track.fileSizes`（`decode_track` 不产 `file.*`）；② `Track.genre` int 码（只有 `genreTags` 字符串标签，且要另发 `fetch_song_detail`）；③ `roma` 逐字音译（`catalog.rs:970` 写死 `"roma": 0`，本轮实读复核）。**实现缺陷/覆盖面类 2 项**——④ `mediaMid` 恒 `null`（`methods.rs:921` 的 `first_int(...).and(...)` 写法，源码级 bug，改一行可修）；⑤ OGG 两档音质（组件档位表只有 flac/320/128/aac 四档，见 §4.2「音质档位定义」行——该行内的 OGG 两档子项无等价物、按 §4.1 图例即 **gap**；但该行作为一条"工程能力"在 §4.2 表内与 §4.3 计数里都计 **partial**（口径见 §4.3 对齐表），本文不因 OGG 子项给该行改判）。
4. **FFI 面当前不可全信**：47 个导出里 **31 个不可用**（11 个返回「不支持的方法」——**完整名单见 §2.2 注**，18 个载荷与模型键不匹配，2 个静默返回全 `None`）；可用 16 个。逃生口是 `call_with_platform` 或子进程——但子进程在 Android 不可行，所以**要么接受 16 个可用导出 + `call_with_platform`，要么推动上游修 `dispatch`/`parse`**。两份可用性清单见 §4.4。
5. **写操作有两个实打实的风险**：组件**从不校验 `data.retCode`**（外层 0 而内层非 0 时会当成功返回），且风控码 `1000` 被压成文本「登录已过期，请重新登录」——工程的「操作太频繁，不自动重试」分级文案拿不到结构化码。另发现组件 android comm 写 `tmeLoginType:1`、工程写 `2`（AGENTS.md 明说换 comm 是写成败关键），**未实测**。
6. **登录侧是净收益**：`euin` 从工程里的硬前提降为可选（组件 `require_login` 只看 musicid+musickey，缺 `encrypt_uin` 时关注歌手回退数字 uin；实测凭据文件只有三键仍 `loggedIn:true`），工程里 `requireEuin()`/`LikedStore` 的 euin 前置与设置页提示可删。

---

## 二、HelperNext 是什么

### 2.1 定位与架构

QQMusicApi_HelperNext 是 [QQMusicApi](https://github.com/LeeDespo/QQMusicApi) 的 **Rust 重写组件**，对外提供 46 个协议方法（`src/methods.rs:29-83` 的 `METHODS`，实测 `get_helper_info` 返回 `methodCount: 46`）。它不是边车服务，而是一个可被两种方式驱动的库/可执行文件：

| 层 | 内容 | 证据 |
|---|---|---|
| 上游 | 3 个出口：`POST u.y.qq.com/cgi-bin/musicu.fcg`（信封 `{comm, req_N:{module,method,param}}`）、老 fcgi `c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg`（账号歌单/收藏专辑）、明文歌词 fcgi `c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg` | `src/upstream.rs:1-11,76,282-286`，`docs/endpoints.md:8-13` |
| 平台档案 | web（`cv 4747474 / ct 24 / platform yqq.json`）与 android（`cv/ct 14090008/11` + QIMEI 设备身份 + 设备会话）；12 个方法在代码里**强制 android**（搜索四类、取流、写喜欢、推荐流、歌手资料、封面匹配、`resolve_song_url`） | `src/upstream.rs:57-75`；`src/methods.rs:104-121`（本轮实读复核） |
| 内部 | 凭据存储（`<dir>/Credential/qqmusic-credential.json`，原子写 + 保留未知键）、设备身份（`<dir>/device.json`，QIMEI 24h TTL + 设备会话）、分桶限流 + 熔断（纯内存态）、QRC 解密（QQ 私有类 DES 三重量 + zlib）、aria2 JSON-RPC 下载引擎适配 | `src/credential.rs:117-164`、`src/device.rs:41,135-148`、`src/guard.rs`、`src/qrc.rs`、`src/aria2.rs` |

### 2.2 两条接入路径（本报告的核心机制事实）

| 路径 | 入口 | 状态 | 证据 |
|---|---|---|---|
| **子进程（stdio）** | `qqmusic-helper-next` 二进制，stdin/stdout 一行一个 JSON `{"id","method","params"}` | **仅限桌面/可执行文件能跑的环境；Android 不可用**（`pack android` 产物不含可执行文件 + Android 10 起应用主目录禁止 `execve`）＝ **桌面完整可用、Android 不可行**（两处口径不冲突：横线上是功能完整度，横线下是平台可用性）。它包含 `set_rate_limit`/`set_breaker`/`aria2_*`/`import_cookies`/`logout` 这些只在 stdio 被拦截的方法 | `src/bin/stdio.rs:135-197`；机制复核实测（macOS 二进制）返回 `rateLimit`/`breaker`/`aria2` 正常；Android 侧否定结论见本表下一行与 §5.2 A1 |
| **FFI（BoltFFI → Kotlin/JNI）** | `src/api.rs` 的 47 个 `#[export]` | **Android 上唯一可行路径**；但 **47 个里 31 个不可用**：11 个返回「不支持的方法」（`methods::dispatch` 里没有这些分支，`src/methods.rs:394-459`，本轮实读复核；**这 11 个的完整名单见下注——`import_cookies`/`logout` 不在其中**）、18 个载荷键不匹配（`api.rs::parse` 只解包 7 个键，`src/api.rs:41-53`，本轮实读复核）、2 个静默返回全 `None` | `helpernext-inventory.md` §0/§4.7；`verify-mechanics.md` §1.4/§1.5；可用性清单见 §4.4 |

**Android 侧落地的硬结论**：`pack android` 的产物里没有可执行文件（本报告 §三 实测 8 个文件、0 个可执行），且 Android 10 行为变更原文 "Untrusted apps that target Android 10 cannot invoke `execve()` directly on files within the app's home directory"，本工程 `targetSdk = 35`（`app/build.gradle.kts:14`）适用 ⇒ **必须走 FFI/JNI**。（`verify-mechanics.md` §3，confirmed。）

> **「11 个命令面方法」是哪 11 个**（本节与 §2.2、§5.2 A1/A4、§6.2 P2 均指这一组）：`set_rate_limit`、`set_breaker`、`aria2_status`、`aria2_restart`、`aria2_configure`、`aria2_add`、`aria2_tell`、`aria2_list`、`aria2_pause`、`aria2_unpause`、`aria2_cancel`。
> 判据（本轮实读复核）：这 11 个名字在 `METHODS`（`src/methods.rs:29-83`，实跑解析得 **46** 项）里，但不出现在 `catalog_dispatch`（92..363）与 `dispatch`（394..460）两个 match 的臂里（本轮用重花括号匹配两函数体、逐名比对，未命中者正是这 11 个）；它们只被 `src/bin/stdio.rs:146-190` 按方法名拦截。`import_cookies`/`logout` **不在**这 11 个里——它们在 `dispatch` 里有分支（`methods.rs:448`/`:449`），只是行为与 stdio 不同（前者明确报错、后者不删文件），见 §4.2「退出登录」行。

**FFI 面没有 `configure` 入口**——生成的 Kotlin 里 3 处 `configure` 全是 aria2 参数（`verify-mechanics.md` §1 三条独立证据：Kotlin grep / 头文件 / `llvm-readelf --dyn-syms`），`src/lib.rs:88` 的 `configure()` 没有 `#[export]`，`grep -c "#\[export\]" src/lib.rs` → 0。数据目录回退链是 `configure()` → 环境变量 `QQMUSIC_HELPER_NEXT_DIR` → `"."`（`src/lib.rs:95-107`）。

**Android 宿主当前可行的路径是 `android.system.Os.setenv`**（API 21+ 公开 API，工程 `minSdk = 26` 可用；`javap` 只证明该 API 存在，不证明它是唯一解——本轮**没有穷举**其他手段，例如改上游给 `configure` 加 `#[export]`、或在 JNI 侧包一层 `chdir`，所以这里说的是「调研范围内可行」而不是「理论上唯一」）。前提是**在第一次调用组件之前**设好——凭据目录每次调用重读环境变量，而 `Upstream`/`DeviceStore`/`Aria2` 是 `OnceLock` 首次构造冻结（`src/api.rs:19-26`、`src/upstream.rs:120,135`），设晚了会出现 "凭据落新目录、device.json 留在旧目录" 的劈叉。该环境变量在 **Android 产物**里确实存在：本轮对 4 个 ABI 的 `dist/android/jniLibs/*/libqqmusic_api_helper_next.so` 实跑 `strings -a <so> | grep -c QQMUSIC_HELPER_NEXT_DIR` → 全为 1，且 `llvm-nm -D --undefined-only` → `U getenv@LIBC`（arm64-v8a 实测，其余 ABI 未查符号只查了字符串）。

### 2.3 许可（本报告必须并列的两种解读）

**可核实事实**（本轮逐项实跑复核）：

| 事实 | 证据 |
|---|---|
| 组件 `license = "GPL-3.0-or-later"` | `/tmp/helpernext_probe/Cargo.toml:6`（本轮实读） |
| `LICENSE` 是纯 GPLv3 全文（674 行含空行 / `wc -l` 673 行，两份拷贝 sha256 一致 `c53a65c2fd561c87eaabf1072ef5dcab8653042bc15308465f52413585eb6271`），**无 linking exception、无附加条款** | 本轮 `shasum -a 256` 两份 LICENSE、`tail -25` 复核 |
| 组件 README 自称 GPL-3.0-or-later，并说明是 QQMusicApi 的 Rust 移植 | `README.md:6`（徽章）、`:131-134`（「## 📄 许可证」段） |
| 分发形态 = 预编译 `.so` + 生成的 Kotlin/JNI 胶水；Rust 源码不在分发物里；Rust 静态库被 `-Wl,--whole-archive` 整体吃进 JNI 共享库 | `boltffi_cli-0.31.0/src/pack/android/link.rs:322-349` |
| 工程 `AGENTS.md` 已声明本项目 GPL-3.0，且**反向放行** GPL-3 兼容代码（「可以参阅、乃至照搬」；旧红线「一律不搬」已失效；只对不与 GPL-3 兼容的许可仍不搬） | `AGENTS.md:79,81,82,83`（本轮实读） |
| 工程 `README.md:61` 仍写 "License TBD / All Rights Reserved"，仓库根**没有** LICENSE 文件，`AGENTS.md:79` 自己承认 README 未同步 | 本轮 `sed -n '58,63p' README.md`、`ls LICENSE* COPYING*` → no matches |

**两种常见解读（不下法律定论，决定权在用户）**：

- **解读 A：构成组合作品，整体按 GPL-3.0 分发。** 依据：GPLv3 §5 定义「基于该程序的作品」需整体按 GPL 授权；`--whole-archive` 把 Rust 目标码真正焊进同一个 `.so`、与宿主同进程运行、且工程所有 QQ 音乐访问都经它（功能深度耦合），分发的是二进制而非源码，GPLv3 §6 要求提供完整对应源码（含组件 Rust 源码）。工程 `AGENTS.md:79` 已宣布 GPL-3.0，与此解读相容。
- **解读 B：不构成组合作品，属聚合/独立程序，各自许可。** 依据：GPLv3 §5 末段与 §2 允许独立程序的聚合；宿主不含组件源码或派生代码，JNI 调用的是一组稳定 ABI 的导出符号（`boltffi_function_*`）。但在「同进程 + 静态链接 + 单一 .so」形态下这条论证比「两个独立可执行文件互相 exec」弱得多——`--whole-archive` 是解读 A 最有力的事实。
- **未能在仓库内核实的点**：作者对链接例外/商业授权**无任何书面表态**（README/LICENSE 均无补充条款），「作者是否默认接受专有宿主静态链接」在仓库内无答案，需向作者确认。（`verify-mechanics.md` §0；该节同时**修正**了提问中「AGENTS.md 写明 GPL/AGPL 一律不引入」的措辞：原文方向相反，`grep` 全仓库无「不引入」一词。）

> **经独立复核修正**：原始调研记录中「组件 GPL-3.0-or-later；本工程 AGENTS.md 写明 GPL/AGPL 代码一律不引入」的**后半句被复核推翻**——`AGENTS.md:81` 是「可以参阅、乃至照搬」，`:82` 明说旧红线已失效。本报告按复核证据改写：现行 AGENTS.md **不禁止**引入 GPL-3 兼容组件，真正待决的是上文的链接形态与分发义务。

---

## 三、本机实测结果

本节按「本次调研实际跑过的检查」记录。标注**（本轮复核）/（定稿轮实跑）**的条目是撰写本报告时在磁盘上重新验证过的（两种写法同义）；标注**（工作流执行记录）**的来自本次调研工作流的执行记录（组件/工程两侧均为只读），**其原始命令与输出见附录 E 的对应行，本报告作者未在本轮重跑**。**没有跑任何构建**（不跑 cargo/gradle/boltffi），因此"打包产物"类结论均为**读已存在的产物**，不是本轮新跑的。

### 3.1 双方基线

```bash
$ git -C /tmp/helpernext_probe log --oneline -1
2ff7e71 feat: stop when the host stops, however it stopped
$ git -C /Users/mac/Documents/Music_app rev-parse HEAD
b6c5c294a5e79c085419b8dc2d9051e8bbc22db8
```
（两条均本轮实跑复核。组件本地仓库 `~/Documents/QQMusicApi_HelperNext` 已到 `5cb2e82`，其 `src/port/comment.rs`、`src/port/mv.rs`、`src/port/singer_extra.rs` 有未提交改动——与本报告引用的域无关。）

### 3.2 组件自测（cargo test）

- 命令（工作流执行记录，`.zcode/workflow-drafts/helpernext-integration-research.ts:320-337`）：`cargo test --manifest-path /tmp/helpernext_probe/Cargo.toml`（`HOME=/Users/mac`）。
- 结果：**exit=0，通过 49 项、失败 0 项**（按 `test result: N passed; M failed` 逐行累计，`testCounts()` `:149-160`）。
- 本报告未重跑 `cargo test`（约束：不跑构建）；该结果来自工作流记录与提问给定的实测事实。

### 3.3 Kotlin 绑定编译（工程同款 Kotlin 2.0.21）

- 命令（工作流执行记录，`:360-395`）：`java -cp <kotlin-compiler-embeddable-2.0.21 等 jar> org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp <stdlib/coroutines/annotations> -d /tmp/helpernext-research/kotlin-out <QqmusicApiHelperNext.kt>`，编译对象是 `/tmp/helpernext_pack_rehearsal/repo/dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt`（3557 行）。
- 结果：**K2JVMCompiler exit=0，产出 class 文件 88 个**。
- **（本轮复核）** `find /tmp/helpernext-research/kotlin-out -name '*.class' | wc -l` → **88**；编译产物仍在磁盘（`com/example/qqmusic_api_helper_next/…class`）。
- 意义：生成的绑定语法上能被工程同款 Kotlin 编译器吃下——**不等于**能在 Android 上跑通（未接 jniLibs、未上设备，见 §七）。

### 3.4 真实账号只读探针

- 命令（工作流执行记录）：`node .zcode/workflow-drafts/helpernext-probe.mjs`（凭据从现场 macOS 宿主的组件目录复制，只调用读接口；输出落盘 `/tmp/helpernext-workflow-cred/probe-output.json`）。
- 结果：**只读探针 exit=0（凭据复制 exit=0），17/17 项读取成功、失败 0**。
- **（本轮复核）** 直接解析该 JSON：

```
readOnly: True calls: 17 ok: 17 failed: 0
```

| # | 探针方法 | 关键输出 |
|---|---|---|
| 1 | get_helper_info | helperVersion 0.1.0 / protocolVersion 2 / methodCount 46 |
| 2 | get_login_status | loggedIn:true, expired:false, hasPlaybackKey:true, hasNickname:true |
| 3 | fetch_liked_songs | total:479, got:3, firstHasMid:true, **firstHasMediaMid:false**, firstHasAlbumMid:true, firstDuration:133, coverIsHttps:true |
| 4 | fetch_liked_albums | count:4, firstHasMid:true, numericId:true |
| 5 | fetch_user_playlists | count:4, numericId:true, firstSongCount:241 |
| 6 | fetch_followed_artists | count:3, firstHasSingerMid:true, firstHasName:true |
| 7 | search_songs | total:1004, got:3, firstHasMid:true |
| 8 | fetch_radio_stations | groups:11, stations:134 |
| 9 | fetch_recommend_feed | count:5 |
| 10 | fetch_toplist_categories | groups:4（工程没有的净新增能力） |
| 11 | fetch_new_songs | count:52 |
| 12 | fetch_lyric（在听的第一首） | hasWholeLine:true, hasWordLevel:false, hasTranslation:false, hasKanaMeta:false, hasRomanization:false |
| 13 | fetch_lyric（搜索命中的日文曲） | hasWordLevel:true, wordTimestampCount:**544**, hasTranslation:true, hasKanaMeta:true, **hasRomanization:false** |
| 14 | fetch_lyric（已知有逐字数据的目标曲） | hasWordLevel:true, wordTimestampCount:**596** |
| 15 | resolve_song_url | playable:true, quality:"flac", filenamePrefix:"F000", urlHost:"isure.stream.qqmusic.qq.com" |
| 16 | fetch_song_detail | hasDescription:false, descriptionLen:0, genreIsArray:false |
| 17 | fetch_album_tracks | total:1, got:1 |

（上表由本轮 `python3` 逐行读取 `probe-output.json` 复核；探针只调读接口，输出不含凭据、完整直链与账号数字 id。）

**该探针未覆盖的域内方法**（其结论只有源码依据）：`search_artists`、`search_albums`、`search_playlists`、`fetch_playlist_tracks`、`fetch_album_detail`、`fetch_artist_songs`、`fetch_artist_albums`、`fetch_artist_detail`、`fetch_radio_tracks`、`set_liked`、`start_login`、`poll_login`（共 12 个）；另有工程不会用到的 `fetch_toplist_tracks`、`search_*_artwork`、`fetch_artist_biography` 也未覆盖。**其中可自动补测的方法与参数见 §4.5；写操作与扫码登录不在自动补测范围内。**

### 3.5 Android 打包（boltffi pack android）

- 命令（工作流执行记录，`:470-500`）：把组件源码 rsync 到干净副本 `/tmp/helpernext-workflow-pack/repo`（排除 `target`/`dist`），然后 `boltffi pack android --release`（`ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/27.3.13750724`）。
- 结果：**exit=0，产出 4 个 `.so`，合计约 38MB**。
- **（本轮复核）** 对磁盘上该干净副本实测：

```bash
$ find /tmp/helpernext-workflow-pack/repo/dist/android/jniLibs -name '*.so' -exec stat -f '%z' {} \;
8662608 9912900 10720072 10597808        # 合计 39,893,388 B = 38.05 MiB
$ <NDK>/llvm-readelf -lW .../arm64-v8a/libqqmusic_api_helper_next.so | awk '/^  LOAD/{print $NF}'
0x1000 0x1000 0x1000 0x1000              # 未加链接参数 → 4KB 对齐
$ 对照 4/4 ABI（/tmp/helpernext_pack_rehearsal/repo，加了 -Wl,-z,max-page-size=16384）
arm64-v8a / armeabi-v7a / x86 / x86_64 全部 0x4000 0x4000 0x4000 0x4000
```

- 产物清单：`dist/android` 只有 8 个文件——4×`.so` + 头文件 + Kotlin 绑定 + `jni_glue.c` + JNI 用头文件；**0 个可执行文件**（`find dist -type f` 全列）。`grep -n "extra_args" /tmp/helpernext-workflow-pack/repo/boltffi.toml` → `:59 extra_args = []`。
- 补充事实（**本轮对 4/4 个 Android ABI 逐个实跑 `llvm-nm -D --defined-only \| grep -c boltffi_function` → 各 47**，与源码 `grep -c '#\[export\]' src/api.rs` = 47 一致）：`/tmp/helpernext_probe` 那份 201.7 MiB 的产物是 **debug profile** 的打包结果（boltffi scratch 目录名 `debug/` + 编译时间戳 + 两棵树 release 静态库 md5 相同三重证据），38 MiB 才是 `--release` 的结果。**调试信息占比（本轮对 release 产物 4 个 ABI 逐个实跑 `llvm-readelf -SW` 累加 `.debug_*`+`.symtab`+`.strtab`+`.shstrtab`）**：arm64-v8a 7,281,021/10,720,072 = **67.9%**、armeabi-v7a 6,483,079/8,662,608 = **74.8%**、x86 5,869,057/9,912,900 = **59.2%**、x86_64 6,703,881/10,597,808 = **63.3%**；四者合计 26,337,038/39,893,388 = **66.0%**。所以「约六成半是调试信息」是对整个 release 包成立的实测值，不是单文件外推（单臂例 `file` 也确认 `with debug_info, not stripped`）。**但本报告不给出「加 `-Wl,-s` 能砍多少」的定量承诺**——该参数未实跑，只能说明二次链接参数里没有 strip（`boltffi_cli-0.31.0/src/pack/android/link.rs:322-349`；`[targets.android.debug_symbols].enabled=false` 只控制是否额外汇出符号归档，`link.rs:233-241`）。作为量级参照，同 target 下 cargo 自己 `strip = true` 的产物是 3,408,120 B（`stat` 实跑，`/tmp/helpernext_probe/target/aarch64-linux-android/release/libqqmusic_api_helper_next.so`）。
- `boltffi.toml` 现状（本轮实读）：`min_sdk = 24`（低于工程 `minSdk = 26`）、`package = "com.example.qqmusic_api_helper_next"`（示例包名，需改；改包名会进 `jni_glue.c:44` 的 `FindClass`，必须重新链接）。

### 3.6 许可与 Android 机制的复核实测

- LICENSE 两份拷贝 sha256 一致、为纯 GPLv3 全文（本轮实跑，见 §2.3）。
- `Os.setenv` 是公开 API：`javap -classpath .../android-37.0/android.jar android.system.Os` → `public static void setenv(String,String,boolean)`；`api-versions.xml` 中 `Os` since=21（复核材料 `verify-mechanics.md` §1）。
- `.so` 真的会读环境变量（**2026-10-03 定稿轮重跑，明确是 Android 产物**）：对 `/tmp/helpernext-workflow-pack/repo/dist/android/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libqqmusic_api_helper_next.so` 逐个实跑 `strings -a <so> | grep -c QQMUSIC_HELPER_NEXT_DIR` → **4/4 ABI 各为 1**；`llvm-nm -D --undefined-only <arm64-v8a so> | grep getenv` → `U getenv@LIBC`（符号仅 arm64-v8a 查过，其余 ABI 只查字符串）。即结论适用于 **Android release 产物**，不是 macOS dylib。
- FFI 分发逻辑实测（macOS dylib，ctypes 调全部 47 个 `boltffi_function_*`）：11 个命令面方法全部返回 `Upstream("不支持的方法：…")`；`song_detail` 等报 `missing field`/`invalid type`；`album_detail`/`fetch_artist_biography` 返回 `<ok>` 但 payload 全零。（`helpernext-inventory.md` §4.7 / `verify-mechanics.md` §1.4-1.5；该实测在 macOS 目标上完成，未在 Android 设备复跑。）

---

## 四、接口对应清单

图例：**covered**=可直接取代；**partial**=需宿主补东西或语义有差；**gap**=组件拿不到。
「复核」列：`confirmed` 表示独立复核维持原判定；**经独立复核修正**表示复核推翻了原结论的某一部分（按复核证据改写）。

### 4.1 目录与搜索域（20 项）

| 工程能力 | 工程证据 | 组件方法 | 组件证据 | 结论 | 备注（含复核） |
|---|---|---|---|---|---|
| 搜索·歌曲（含分页） | `data/api/SearchApi.kt:36-42`；分页 `ui/search/SearchScreen.kt:138-171,214-221`；空结果语义 `:185` | `search_songs` | `src/methods.rs:305-321` → `src/catalog.rs:1361-1420`；`total` 取 `meta.sum` `:1409-1411`；`body.item_song` `:1395` | **partial**（复核 confirmed） | 同端点同参数；差异：① `Track` 无 `fileSizes/genre`（→ 查看格式入口消失 `TrackDialogs.kt:210`、智能调音失效）；② `mediaMid` 恒 null（`methods.rs:921`，实测 `firstHasMediaMid:false`）；③ 强制 android 档案 + 设备会话（`methods.rs:104-118`）；④ 默认 limit=20、上限 50，工程默认 30 → 须显式传 `limit:30`。**复核修正**：原结论称「标题 `<em>` 已剥…都对得上」**对歌曲不成立**——`strip_highlight`（`catalog.rs:1468-1470`）只作用于 artist/album/playlist 三个 mapper（`:1434/1451/1476`），歌曲走 `decoded_tracks` 不剥；且工程侧搜索歌曲反而**开着** highlight（`SearchApi.kt:27` `highlight = (type==0)`）。宿主需自行剥 `<em>` 或接受它。`total` 可取代工程「本页 0 条=到底」的推断（`SearchScreen.kt:164-166`） |
| 搜索·歌手 | `data/api/SearchApi.kt:45-60`；消费 `ui/search/SearchScreen.kt:631-655`、`Cards.kt:234-238` | `search_artists` | `src/catalog.rs:1430-1444`（`singerMid/name/coverURL/songCount/albumCount/fanCount`，名字剥 `<em>`、封面 https） | **covered**（复核 confirmed） | 逐字段对上。工程的 `SearchSinger.id`（源 `singerID`，`SearchApi.kt:53`）组件不给，但**全工程无消费点**（复核实跑 grep 全命中电台/导航 id；`Nav.Singer` 只用 mid/name/pic/songNum/albumNum，`ui/Nav.kt:29-35`）→ 不构成缺口。`fanCount` 白送。「精确名优先」两行留在宿主（`SingerApi.kt:29-32`） |
| 搜索·专辑 | `data/api/SearchApi.kt:63-76`；展示 `SearchScreen.kt:660-680`、`Cards.kt:152` | `search_albums` | `src/catalog.rs:1446-1463`（`id/title/albumMid/coverURL/artist/releaseDate`） | **partial**（复核 confirmed） | **没有 `song_num`** → 专辑卡右侧「N 首」消失（`Cards.kt:152` 是 `if (count > 0)` 门槛，**复核更正行号 158→152**，本轮实读复核）。封面可回退 `T002R800x800M000{mid}.jpg`（https）。宿主需补计数，或让组件 `map_album` 带出上游 `song_num` |
| 搜索·歌单 | `data/api/SearchApi.kt:79-91`；展示 `SearchScreen.kt:681-687` | `search_playlists` | `src/catalog.rs:1472-1489`（`id`=dissid/title/coverURL=logo（`:1482` 注释）/creator/songCount/playCount） | **covered**（复核 confirmed） | 全部对上，`id` 可直接当 `fetch_playlist_tracks` 的 `songlistId` |
| 曲目字段合集（fileSizes / genre / mediaMid） | `data/Models.kt:16-18`；`QqMapper.kt:35,39-48,61`；用途 `SongApi.kt:87-94`、`TrackDialogs.kt:210,236`、`SmartEq.kt:55` | `decode_track`（全方法共用） | `src/methods.rs:917-933` 映射里无 `file`/`genre`；`src/models.rs:131-155` 无对应字段 | **gap**（复核 confirmed） | 结构性缺失：上游响应里有 `file.size_*`/`genre`/`media_mid`，组件丢弃（`media_mid` 还因 `:921` 写法恒 null）。不是传参问题。宿主若要音质过滤/格式弹窗/智能调音，必须改组件 `decode_track` 或放弃这三项。**口径注**：本行按"字段集"判定，与 §4.2 的三行**有意重叠**——`fileSizes`、`genre` 在 §4.2 各有独立 gap 行，`mediaMid` 则在 §4.2「曲目字段」这条**更宽的 partial 行**里（该行判 partial 是因为 songId/singers/isVip 等其余字段可用）。§4.3 按行计数，不看字段交叠；字段级交叠不构成重复计数 |
| 我喜欢列表（分页+总数） | `data/api/PlaylistApi.kt:31-43`；`data/LikedStore.kt:36-48`（**每页 300**）；`ui/home/Screens.kt:217-247` | `fetch_liked_songs` | `src/methods.rs:661-693`（同端点 `music.srfDissInfo.DissInfo/CgiGetDiss`、`dirid=201`、总数 `dirinfo.songnum`、limit clamp 1–100、page 语义） | **partial**（复核 confirmed） | 语义一致；差异：① page/limit（上限 100）vs 工程 offset 每页 300 → 宿主改 page 循环（479 首 = 5 次往返，工程 2 次）；② **不需 euin**（收益）；③ 曲目字段缺口同上一行。实测 `total:479 got:3 firstHasMid:true` |
| 我喜欢总数（主页卡片） | `ui/home/HomeScreen.kt:236`（`likedPage(0,1).total`）、`:516`（`—` 兜底） | `fetch_liked_songs` | `src/methods.rs:690`（`total`，取不到时退化为本页条数） | **covered**（复核 confirmed） | `page:1,limit:1` 即可。差异：组件 total 恒有值，与工程「total=null 显示 —」语义不同；要保留「—」需宿主自己判断 |
| 收藏的歌单 | `data/api/PlaylistApi.kt:63-70`（`PlaylistFavRead`，`v_list[]`）；消费 `HomeScreen.kt:189`、`Screens.kt:614` | `fetch_user_playlists` | `src/methods.rs:744-773` 走**老 fcgi** `reqtype=3`（`src/upstream.rs:273-299`）；字段 `id/title/coverURL/creator/songCount/playCount`；无 offset/page（`sin=0&ein=limit` 固定，`upstream.rs:283-285`） | **partial**（复核 confirmed） | ① **端点不同**，两者对同一账号是否同一集合**本次未验证**（工程侧条数无留档）；② **没有分页**（只读 limit）——工程 UI 目前也只取第一页，够用但 >100 条无法翻全；③「我喜欢」保留目录被跳过（`:756-761`），工程它是独立卡片不受影响。实测 `count:4` |
| 收藏的专辑 | `data/api/PlaylistApi.kt:73-80`（`AlbumFavRead`）；消费 `HomeScreen.kt:590`、`Screens.kt:667` | `fetch_liked_albums` | `src/methods.rs:776-801` 老 fcgi `reqtype=2`；字段 `id/title/albumMid/coverURL/artist/releaseDate`（pubtime +08:00 转日期 `:935-954`） | **partial**（复核 confirmed） | **没有 `songnum`** → 主页卡片与列表副标题显示「0 首」（`HomeScreen.kt:590` 不做 0 判断）→ 宿主补或组件补。`artist` 白送（工程注释称收藏接口不给）。实测 `count:4 numericId:true` |
| 歌单内曲目 | `data/api/PlaylistApi.kt:47-59`；消费 `ui/AppRoot.kt:635` | `fetch_playlist_tracks` | `src/methods.rs:701-741`：显式 `offset` 优先、否则 page 换算（`:714-717`），limit clamp 1–200，`total` 可为 null（`:737-740`） | **partial**（复核 confirmed） | 与工程 `Page(offset,num)` **语义可直接替代**，只剩曲目字段缺口。**本行未实测**（探针未调用），结论来自源码 |
| 专辑内曲目 | `data/api/PlaylistApi.kt:86-97`（驼峰 `albumMid`、`songList[].songInfo`、`totalNum`）；消费 `AppRoot.kt:642` | `fetch_album_tracks` | `src/methods.rs:154-163` → `src/catalog.rs:415-451`（参数逐字一致；`decoded_tracks` 候选键含 `songList/songInfo`） | **partial**（复核 confirmed） | 端点/参数/`totalNum` 逐字一致；组件 limit 默认 200 vs 工程 100，传参即可。只剩曲目字段缺口。实测 `total:1 got:1` |
| 歌手解析（名字 → mid/pic/计数） | `data/api/SingerApi.kt:29-32`；消费 `PlayerBar.kt:152`、`SingerScreen.kt:110-120` | `search_artists` | 同搜索·歌手（`catalog.rs:1430-1444`）；组件内部 name→mid 也走同一搜索（`catalog.rs:590-614`，limit=1） | **covered**（复核 confirmed） | 宿主保留「精确名优先」两行即可；计数来自 `songCount/albumCount` |
| 歌手歌曲（分页/排序/总数） | `data/api/SingerApi.kt:35-47`（`order=1/2`、`totalNum`）；消费 `SingerScreen.kt:141,167`、底部文案 `:334-338` | `fetch_artist_songs` | `src/methods.rs:164-173` → `src/catalog.rs:459-504`（`order` 硬编码 1；`latest` 本地按 releaseDate 页内排序 `:487-502`；**载荷只有 `{tracks}` 无 total**） | **partial**（复核 confirmed） | ① **没有 total** → 底部「共 T 首」消失，只能显示「已加载 N 首」；② 只认 page（limit≤100）而非 offset；③ **`latest` 只是页内排序**（上游忽略排序参数）——工程 `order=2` 是服务端全序；④ 曲目字段缺口。**本行未实测** |
| 歌手专辑（分页/总数/每张歌数） | `data/api/SingerApi.kt:52-81` + **`fillSongCounts:84-102`**（30 张合并 1 次 `musicu.fcg` 多请求块）；消费 `SingerScreen.kt:147,176,284-296` | `fetch_artist_albums` | `src/methods.rs:174-183` → `src/catalog.rs:507-559`（无 total；`latest` 页内排序；不提供每张专辑歌数） | **partial**（复核 confirmed） | ① 无 total；② **无每张专辑歌数**；③ **组件公开调用面不支持多请求块合并**（`src/upstream.rs:240,348-355` 恒 `vec![call]`）→ 工程「30 张合成 1 次 HTTP」在组件上膨胀成 30 次请求，与工程降低请求频次的纪律相反——**本域最实质的能力退化**。**本行未实测** |
| 歌手资料与计数 | `ui/singer/SingerScreen.kt:247-259`（计数来自搜索解析）；工程无简介 | `fetch_artist_detail` | `src/methods.rs:184-193` → `src/catalog.rs:574-690`（`GetHomepageHeader`，强制 android）；给 `songCount/albumCount/fanCount/followCount/description/region/foreignName` | **partial**（复核 confirmed） | ① 上游返回空壳资料时**报错**而非回「未知歌手」（`catalog.rs:636-641`）；② typed FFI `artist_detail` 不可用（`api.rs:274` 包装 + `parse` 只解 7 键）→ 必须 stdio 或 `call_with_platform`；③ `description` 多数为空（上游本来就没有）。工程目前完全没有歌手简介/详情，属净新增。**本行未实测** |
| 关注的歌手 | `data/api/UserApi.kt:19-43`（`List[*]` 的 MID/Name/AvatarUrl/Desc + Total；**euin 空不发请求 `:20-21`**）；消费 `HomeScreen.kt:224-231,774-781` | `fetch_followed_artists` | `src/methods.rs:804-855`：`List`（大写 L）已处理、`MID/Name/AvatarUrl/fanCount`；`HostUin` 数字 uin 兜底（`:816-818`） | **partial**（复核 confirmed） | ① **`Desc` 不返回**（只给 fanCount）→ 卡片第二行文案要宿主自拼或留空；② **无 Total**（工程目前也只消费 `.first`，当前影响为零）；③ **euin 依赖解除**（收益）。实测 `count:3` |
| 电台分组 | `data/api/RadioApi.kt:16-20`（`commRadio`）、`QqMapper.kt:113-124`；`HomeScreen.kt:206` | `fetch_radio_stations` | `src/methods.rs:205-206` → `src/catalog.rs:794-846`（分组/电台层级正确，`:838` 取 item 自己的 id） | **covered**（复核 confirmed） | 组件不返回 `listenDesc`（工程读但**从不显示**，grep 只命中 Models/QqMapper/HomeCache）→ 无影响；多给 `listenerCount`。工程按标题字符串「猜你喜欢」定位 station（`HomeScreen.kt:163-165,220-222,248-251`），组件原样透传 `title`，可继续匹配。实测 11 组 134 台 |
| 电台曲目（无限流） | `data/api/RadioApi.kt:45-63`（`get_radio_track`、每批 5 首、三次批 0 重叠、全批失败**抛异常** `:59-61`）；消费 `AppRoot.kt:645-666`、`RecommendStore.kt:81` | `fetch_radio_tracks` | `src/methods.rs:207-215` → `src/catalog.rs:850-873`：**换了上游方法** `pf.radiosvr/GetRadiosonglist`；param 只有 `{id,firstplay,num}`；无 exclude | **partial**（复核 confirmed） | ① 换了上游方法 → 「每次调用回不同的一批」这个无限流前提**本次未验证**（源码形状具备：无 cursor、同请求可无限取；但只有组件文档断言，无活体证据）；② 无 exclude → 去重分批仍归宿主（工程已有）；③ `firstPlay` 默认 true → 首屏/续批可区分；④ 拒答码统一报错 → 保住「异常=可重试 vs 空表=到底」（`RadioApi.kt:59-61` 原则可保留）。**本行未实测** |
| 猜你喜欢 / 推荐流 | `HomeScreen.kt:150-175`（推荐歌 + 找「猜你喜欢」电台）；`RecommendStore.kt:71-119`（预缓冲 5 首 + excluded 去重 + 介绍）；介绍端点 `SongApi.kt:119-133` | `fetch_recommend_feed` | `src/catalog.rs:900-916`：直连 `id=99` 猜你喜欢；参数**写死** `{num:5,from:0,scene:0,song_ids:[]}`；需登录 + 强制 android | **partial**（复核 confirmed） | ① 直连 id=99 → 可省掉按标题找电台（收益）；② **没有 exclude** → 预缓冲「每次给不重复的新歌」的核心前提（`RecommendStore.kt:86-91`）无法表达，只能靠宿主按 mid 去重；若上游回同一批会落进 delay 重试分支；③ 介绍文案改用 `fetch_song_detail.description`（同一 `info.intro.content[].value`；空即答案，与 `SongApi.intro` 返回 null 一致）。实测 `count:5` |
| 昵称（与 §4.2 同名行是**同一能力的重复记录**，总量按 1 项计） | `data/api/UserApi.kt:46-50`（`GetLoginUserInfo` → `info.nick`）；`SettingsScreen.kt:852` → `NicknameCache` | `get_login_status` | `src/methods.rs:625-657`（`login.nickname` 源 `info.nick`；被拒时回 `loggedIn:false` 而不抛错） | **covered**（复核 confirmed） | 一次调用另带 `musicId/vipType/expired/hasPlaybackKey`。实测 `hasNickname:true hasPlaybackKey:true`。工程主页问候语当前**不消费昵称**（`HomeScreen.kt:103,215-217`）——工程侧既有的文档/代码不一致，与本组件无关 |

### 4.2 播放、歌词与写入域（22 项）

| 工程能力 | 工程证据 | 组件方法 | 组件证据 | 结论 | 备注（含复核） |
|---|---|---|---|---|---|
| 播放直链 + 音质档位降级 | `data/api/SongApi.kt:51-84`（降级链）、`:98-112`（fetchPurl）、`ui/AppRoot.kt:174-176` → `player/PlayerHost.kt:175-197` | `resolve_song_url` | `src/methods.rs:350-359` → `src/catalog.rs:1041-1123`；档位表 `:25-30`；失败分类 `:1126-1158` | **partial**（复核 confirmed） | 同端点同 CDN、同「按档位探测、104003 不中断」。差异：① 只有 flac/320/128/aac **4 档**，工程 6 档里的 O600/O800 没有；传未知 label **静默退回整链**（`catalog.rs:1054-1061`）；② 无 `file.*` 概念、不做档位过滤（每档各发一次请求）；③ 不可播是返回值 `playable:false`+`restriction`+`tried[]`+`reason` 而非异常，「连最低档都 104003 才判无权限」不替宿主做；④ `101404`/`22` 无单独分支（只有 `describe_result` 兜底 `:1157`，本轮 grep 复核 `101404` 在组件源码零命中）。**成功路径已实测**（`probe-output.json` #15 `playable:true/flac/F000/isure`）；**失败路径未实测** |
| 音质档位定义（6 档、前缀+扩展名成对） | `data/Prefs.kt:11-17`（M500/M800/C400/O600/O800/F000）；`SettingsScreen.kt:222-239`；`SongApi.kt:87-94` | 无等价物 | `src/catalog.rs:25-30,1054-1061` | **partial**（复核 confirmed；**口径注**：「组件方法」列的「无等价物」指组件没有 *6 档* 的等价表（它只有 flac/320/128/aac 四档，`catalog.rs:25-30`）；其中 O600/O800 两个子项按 §4.1 图例是 **gap**（完全拿不到），但本行作为一条"工程能力"在 §4.2 表内与 §4.3 计数里都计 **partial**——两种数法的对齐见 §4.3 口径表） | 组件无「该曲有哪些档位（size>0）」数据。**复核修正**：原结论说「唯一能发原始 musicu.fcg 的入口是 `call_with_platform`」不准确——`call_with_platform` 也只进 `methods::dispatch`，而 dispatch 受 `METHODS` 名单约束、全组件没有任何方法接受任意 param/filename 透传（`grep '"filename"'` 只命中 `catalog.rs:1079/1100` 组件自建）⇒ **OGG 两档在组件上完全无法取得，连 `call_with_platform` 也不行**，只能保留工程原生请求 |
| 播放失败人话文案（1000/104003/101404/22） | `SongApi.kt:60-83`；`TrackRow.kt:338-343`；`SongApi.kt:156-159` | `resolve_song_url` + `upstream.rs` | `catalog.rs:1126-1158`；`src/upstream.rs:524-531` | **partial**（复核 confirmed） | 组件给结构化 `restriction`（paid_required/device_restricted/ticket_required/unavailable）+ 逐档 `tried[]`，比工程好；但风控码被压成中文文本：`1000/104401/104400`→「登录已过期」（`upstream.rs:528`）、`2001`→「触发风控」（`:527`）。**复核修正**：不是「拿不到原始数字码」——数字嵌在错误文本 `上游返回错误（1000）：…`（`upstream.rs:250-251`）里，可再解析；准确说法是「拿不到结构化码，只有兜底文案里的数字」 |
| 歌曲详情/简介（推荐卡介绍框） | `data/api/SongApi.kt:119-133`（commWeb + `info.intro.content[].value` 拼接、空→null）；消费 `RecommendStore.kt:95` → `HomeScreen.kt:696` | `fetch_song_detail` | `src/methods.rs:129-141` → `src/catalog.rs:161-275`（同端点、同路径、`join("\n")` `:264`、空即答案 `:249-251`） | **covered**（复核 confirmed） | 多给 `genreTags/language/labelOrCompany/releaseDate/duration/songId/albumMid/imageURL` 与溯源键。**复核修正**：原结论「默认档案 Web 与工程 commWeb 一致」不准确——组件 Web 档案 = `ct:24/cv:4747474`（`upstream.rs:60-65`），工程 `commWeb` = `ct:19/cv:1873`（`QqCore.kt:40`），不是同一个 comm（端点两边都能答）。`configure(default_platform)` 在 dispatch 路径是死配置（`methods.rs:401` 用 `Platform::default()`=Web）。FFI typed `song_detail` 报 `missing field songMid` → 走 stdio/`call_with_platform`。实测 `hasDescription:false`（空即答案） |
| 逐字歌词（QRC 卡拉 OK） | `data/api/LyricApi.kt:69-116,374-413`；`data/api/QrcCodec.kt:202-227`；模型 `data/Lyrics.kt:3-30`；渲染 `ui/player/LyricsView.kt:415-473,397-403` | `fetch_lyric` | `src/methods.rs:227-266` → `src/catalog.rs:949-999`（加密路）+ `src/qrc.rs:374-410,440-489,507-536` | **partial**（复核 confirmed） | 数据同源（同加密路、同一 QQ 私有类 DES、来源同为 MIT qrc-decoder、已知答案测试）。但**形态是「词级 LRC 文本」**（每个词前一个时间戳，`qrc.rs:507-525`）。宿主**必须写新解析器**——工程 `parseLrc` 只取最后一个时间标签之后的文本（`LyricApi.kt:337`），直接喂会把词全吃掉。组件**丢弃行时长/字时长**，工程模型需要 `endMs`（`Lyrics.kt:29`，用于重叠清洗与扫色插值），宿主只能用「下一词起点」合成。**复核收紧两点**：① 「扫色粒度从字退化成词」对 CJK 不成立——QRC 把中文按**单字**切（`qrc.rs:589-601`），中文仍是逐字扫色，拉丁文才是词级；真正损失是**词时长被量化**、每行/全曲最后一个词没有可靠 end；② 「两位小数无损读回」只对组件已输出的值成立（`lrc_timestamp` 是 ms/10 截断 `qrc.rs:492-500`，对原始 QRC 毫秒有 ≤10ms 量化）。`wordLyric:null` 不是错误且同时给明文整行 lyric 兜底 → 可替掉工程的 hex/base64 分流。实测：日文曲 544 个词时间戳、目标曲 596；另有曲无逐字 |
| 翻译（「译」开关） | `LyricApi.kt:99-105`（qrc=1 trans 常空 → 补一趟）、`:169-180`（`mergeTranslation` ±300ms、丢 `//`）；`Lyrics.kt:37` | `fetch_lyric.translation` | `methods.rs:252-259`；`catalog.rs:997`（加密路解 trans）、`:1027`（明文兜底） | **partial**（复核 confirmed） | 能拿到（实测 `hasTranslation:true`）。组件给整行 LRC 文本、不分行 → 宿主 parse+合并（工程 `mergeTranslation` 可复用）；`//` 占位组件不丢 → 保留工程 `LyricApi.kt:177` 的过滤；可用性门控 `hasTranslation` 由宿主自算。**关不掉**：`translation:false` 只跳过明文路，加密路只要取逐字就写死 `trans:1`（`methods.rs:236-240`、`catalog.rs:970`），省不了往返 |
| 音译（「音」/roma 开关） | `LyricApi.kt:108-110`（`roma:1` hex QRC）；`Lyrics.kt:39`；开关 `PlayerScreen.kt:258-262` | **无** | `src/catalog.rs:970` 写死 `"roma": 0`；`EncryptedLyrics` 只有 word/translation（`:933-939`）；`Lyric.romanization` 恒 `None`（`models.rs:393`） | **gap**（复核 confirmed） | 实测三首歌 `hasRomanization` 全 false——但这是写死 `roma:0` 的必然结果，**不能证明上游没有数据**（工程用 `roma:1` 拿到过）。宿主必须自己按 `PlayLyricInfo{roma:1}` 再取一次并用 `QrcCodec` 解 hex（工程已有全套）。⚠️ `src/api.rs:326-327` 注释声称能拿 romanisation，与实现不符，别按注释期待 |
| 注音（「注」/kana 开关） | `LyricApi.kt:196-209`（`parseKanaTokens` 从 trans 找 `[kana:…]`）、`:224-266`（`attachKana`：只有汉字消费 token） | `fetch_lyric.translation` | `catalog.rs:997`（`[kana:…]` 元数据原样带出；全组件 grep `kana` 零命中——本轮实跑复核） | **partial**（经独立复核修正） | 数据在翻译里（实测日文曲 `hasKanaMeta:true`），组件不解析 → 把工程 `parseKanaTokens` 接到组件 translation 上即可；注意工程内部先 `decodeLrc`（`LyricApi.kt:204`），组件给的是已解密文本。**经独立复核修正**：原结论称「靠 `LyricApi.kt:165` 的 `getOrElse{raw}` 兜底可容纳」被复核**实测推翻**——复核编译了 Android SDK 的 `android/util/Base64.java` 并实调 `Base64.decode(s, Base64.DEFAULT)`：多数含 `[kana:…]` 的样本**不抛异常**而是返回乱码（`getOrElse` 永不触发，kana 被吃掉），也有抛异常的样本。⇒ 宿主**必须显式跳过/绕过 `decodeLrc`**（组件来源的 translation 直接当明文用），否则注音在多数日文曲上会静默消失。字级/行级 kana 仍由宿主算 |
| 喜欢写入（AddSonglist/DelSonglist） | `SongApi.kt:143-160`（数字 songId 前置 `:145`、成功判定 `:155-159`）；`TrackRow.kt:315-343`；`PlayerScreen.kt:398-412`；`LikedStore.kt:59-62` | `set_liked` | `src/catalog.rs:1262-1312`（param `:1300-1308`；mid→数字 id `:1276-1295`）；dispatch `methods.rs:338-349`；FFI `api.rs:398-404` | **partial**（复核 confirmed） | 同端点同参数同强制 android、都不自动重试。两个风险：① 组件**从不看 `data.retCode`**（`upstream.rs:242-259` 只看外层 code 与 data 是否为空）——外层 0 但 retCode≠0 时会**当成功返回**（`catalog.rs:1311`），宿主红心会与服务端不一致；② 业务码被压成文本，`1000` 被译成「登录已过期」→ 工程的 `LikeResult.Rejected(1000)`「操作太频繁」拿不到结构化码。组件接受 mid 并内部解析数字 id（比工程宽容），但 FFI 只暴露 mid 且返回 `Result<(),_>`。**mid→数字 id 的解析代价（本轮实读 `catalog.rs:1276-1295`）**：`song_id <= 0` 且给了 `songMid` 时，组件会先调一次 `song_detail(…)` 取 `songId`——即**多一次上游往返**（`catalog.rs:1276-1295` → `song_detail()`）。工程侧是本地判定 `songId <= 0` 直接回 `Unavailable`、不发任何请求（`SongApi.kt:145`）——所以走组件的 mid 路径对"没有 songId 的曲目"会多花一次 `get_song_detail_yqq`。**未实测**（`probe-output.json` 无 `set_liked`）。**复核新增发现**：组件 android comm 写 `tmeLoginType:1`（`upstream.rs:371`，本轮实读复核），工程写 `2`（`QqCore.kt:53`，本轮实读复核）——AGENTS.md 明说换 comm 是写成败关键，**该差异未验证**，集成前应做一次受控实测 |
| 我喜欢列表（红心状态/计数） | `PlaylistApi.kt:31-43`；`LikedStore.kt:31-54`（每页 300 翻全）；`HomeScreen.kt:236-241` | `fetch_liked_songs` | `src/methods.rs:661-693`（page/limit，limit clamp 1–100；total `:690` 可退化本页条数）；FFI `api.rs:90` | **partial**（复核 confirmed） | 每页最多 100 → 479 首要 5 次往返（工程 2 次），宿主需改分页算法。组件**不要求 euin**（`:666` 只 require_login）→ 工程 `requireEuin()` 与「缺 euin 收藏不可用」的 UI 提示（`SettingsScreen.kt:855-857`）可删。`total` 可能被本页条数兜底，不能当「到底了」判据。实测 `total:479, coverIsHttps:true` |
| 封面 URL（模板拼装 + http→https，通知线程直拉） | `Models.kt:20-23`（T002R300x300M000）；`QqCore.kt:128-131`（toHttps）；`PlaybackService.kt:125-137`（`java.net.URL.openStream`） | `normalized_artwork_url` + 各处归一（`map_album:1450-1464`、`map_playlist:1481-1484` 键在 logo、`map_artist:1436-1440`、电台 `:822-824`、关注歌手 `:850`） | `src/methods.rs:975-996` | **partial**（复核 confirmed） | https 强制组件更全（多处理协议相对 `//`）。尺寸档不同：专辑模板 R800x800 vs 工程统一 R300x300。空值形态：组件全 `Option(None)`、工程按空字符串判断（`Cards.kt:84/128`）→ 宿主必须 `None→orEmpty()`，否则首字母占位/爱心不触发。通知封面依赖纯 https 直链，实测 `coverIsHttps:true` 满足。**复核修正**：原结论「组件曲目用上游原始 imageURL（可能非 300）」不对——`methods.rs:927` 是 `normalized_artwork_url(album_mid.map(album_cover_url))`，即专辑模板 `T002R800x800M000{mid}.jpg`（`:979-981`） |
| 网页 cookie 登录（uin+qm_keyst，euin） | `SettingsScreen.kt:839-860`（WebView cookie 读三件）；`Prefs.kt:352-374`；`QqCore.kt:49-61`；`PlaylistApi.kt:24-28`（euin 硬前提） | `import_cookies` / FFI `import_credential(uin, qm_keyst)` | `src/methods.rs:615-621`；`src/bin/stdio.rs:135-150`；`api.rs:69-78`；`credential.rs:180-198`（qm_keyst 必需、uin/qqmusic_uin/musicid 任一、encrypt_uin 选填）；回退数字 uin `upstream.rs:146-172`、`methods.rs:816-821` | **partial**（复核 confirmed） | cookie 只需 uin+qm_keyst，**euin 从硬前提降为可选**（require_login 只看 music_id+music_key，`credential.rs:46-49`）。实测凭据文件只有 `musicid/musickey/str_musicid` 三键（本轮 `jq keys` 复核），仍 `loggedIn:true/hasPlaybackKey:true`。宿主需改：凭据改存组件文件（原子写、保留未知键）；设置页显示账号要自己读该 JSON（`get_login_status` 是真问上游）；euin 分级提示重写。`uin` 的 `o` 前缀要宿主自己剥（组件不做） |
| 扫码登录（QQ） | 没有独立扫码：二维码是 `y.qq.com` 页面 iframe（`SettingsScreen.kt:879-880` 注释） | `start_login` / `poll_login` | `src/login.rs:92-121,124-169,173-257`；事件码 `:58-79`；dispatch `methods.rs:322-337`；FFI `api.rs:411-420` | **covered**（复核 confirmed） | 组件补上原生扫码：PNG base64 + qrsig 标识，事件码 SCAN/CONF/DONE/TIMEOUT/REFUSE，最后一步 android 档案 + `tmeLoginType:2`。限制：**只有 QQ，没有微信**（`docs/endpoints.md:104`，源码无微信分支）。可保留现有 WebView 登录并存。**未实测 DONE 路径**（需真人扫码） |
| 退出登录 | `SettingsScreen.kt:199-208`（清 Prefs + ApiCache + LikedStore + HomeCache + TrackListCache） | `logout` | stdio 真删文件（**实际行号 `src/bin/stdio.rs:192-197`**，复核更正；原引 180-184 是 aria2 段）；FFI typed `logout` 真删（`api.rs:82-86` → `credential.rs:166-172`）；`methods.rs:449` 的 **dispatch 分支只回 loggedIn:false、不删文件** | **partial**（复核 confirmed） | 坑：宿主若按普通 method 派发（或 `call_with_platform("logout")`）会「退出后重启仍登录」。**`logout` 不属 §2.2 注里那 11 个命令面方法**——它在 `dispatch` 里**有**分支（`:449`），只是行为弱于 stdio/FFI；FFI 侧的正确调法是 typed 导出 `logout()`，不是 `call_with_platform`。组件只清自己的凭据文件，工程那四份缓存仍要宿主清 |
| 昵称（与 §4.1 同名行是**同一能力的重复记录**，总量按 1 项计） | `UserApi.kt:46-50`（同端点同字段 `info.nick`）；`NicknameCache.get()` 无消费点 | `get_login_status.nickname` | `src/methods.rs:648-656`；实测 `hasNickname:true` | **covered**（复核 confirmed） | 一次调用另带 `musicId/vipType/expired/hasPlaybackKey`。工程侧 NicknameCache 目前无消费点，影响很小。**计数口径**：§4.3 的 42 行含这**两行**，本行不因与 §4.1 重复而在表内标重——两域各记一行，合计 42；能力总量按 1 项计（见 §4.3 明细） |
| 下载（取直链→拉流→写公共目录） | `Downloader.kt:21-24,26-35,37-62,81-87`；`DownloadStore.kt`；`SettingsScreen.kt:497-535` | `aria2_*` 系列（**9 个**） | `src/methods.rs:541-612`；`src/aria2.rs:153-159,212-223` | **partial**（复核 confirmed） | 引擎缺席时报错、宿主应退回自己的下载方式（`docs/endpoints.md` §四末）。实跑 `find /tmp/helpernext_probe -maxdepth 3 -name 'aria2*'` 只命中 `src/aria2.rs`；`dist/` 只有 android jniLibs——**仓库里没有 aria2-next 二进制**。下载目录被钉死在 `<组件目录>/Downloads`，工程三选目录（`Prefs.kt:47-51`）无法保留，Android 上还要自己从私有目录导入 MediaStore。**复核修正**：FFI 面不可用的 aria2 方法是 **9 个**不是 11 个——「11」是盘点把 `set_rate_limit`/`set_breaker` 一起算的「11 个命令面方法」。命名/MIME/非法字符仍是宿主的（add 只收 url+out）。组件能补的是进度 tell/list（completed/total/speed）与取消（会删临时文件） |
| 已下载台账/列表置灰 | `DownloadStore.kt:9-17,56-63`；UI `Screens.kt:438-448` | **无** | `Aria2Task`（`models.rs:93-109`）只有 gid/status/…；`aria2_list` 是引擎任务列表 | **gap**（复核 confirmed） | 组件不提供「该 mid 是否已下载」。`out` 名与工程 `displayName` 规则不能互认，代码依据：`aria2_add` 只校验 `out` 是文件名（非空、不含 `/`，`src/methods.rs:569-580`，本轮实读复核）并把命名权留给宿主（doc 注释原文 "the app picks it so the imported track is named the way the rest of the pipeline expects"），而工程 `Downloader.kt:34` 的 `displayName` 是 `"${track.singer} - ${track.name}${quality.ext}"` 去掉非法字符——两者都**不含 mid**，所以无法从下游文件名反查 mid。接组件后仍要自己维护台账并按 mid 回填 |
| 曲目字段（songId/mediaMid/singers/isVip/interval） | `Models.kt:4-27`；`QqMapper.kt:27-63`（mediaMid 兜底 `:35`、songId `:59`、genre `:61`） | `decode_track` | `src/methods.rs:889-933`（mediaMid `:921`、artist 用 `", "` join `:907-911`、imageURL `:927`）；`models.rs:131-155` | **partial**（复核 confirmed） | 实测 `firstHasMediaMid:false`（probe 第 3 行；**复核更正引用为 `probe-output.json:33`**）→ 别假设 mediaMid 非空。**兜底在 `src/catalog.rs:1066-1069`**（本轮实读复核）：`match media_mid.filter(\|mid\| !mid.trim().is_empty()) { Some(mid) => format!("{prefix}{mid}{extension}"), None => format!("{prefix}{song_mid}{song_mid}{extension}") }`，即无 mediaMid 时用 `{前缀}{songMid}{songMid}{扩展名}`；probe 实测 `playable:true`（`probe-output.json` #15）正是走这条兜底路径。多歌手分隔符是 `", "`（逗号+空格），**不在**工程切分集合 `/`、`、`、`,`（`PlayerBar.kt:161`）里 → 会被当一个名字解析失败；宿主补切分或改用组件的 `singers[{mid,name}]` 数组（`models.rs:120-127`）。组件 Track 无 fileSizes/genre；`payPlay` 是 `Option<i64>`（工程 Boolean，`QqMapper.kt:58`）；组件必填仅 `songMid`，其余 Option |
| fileSizes（格式弹窗 + 降级过滤） | `QqMapper.kt:39-48`；`TrackDialogs.kt:210,236`；`SongApi.kt:87-94` | **无** | 全组件 grep `size_` 只命中 aria2 的 `min_split_size_mib`（本轮实跑复核）；`decode_track` 不产 file 字段 | **gap**（复核 confirmed） | 组件只回答「哪档被授予」，不回答「有哪些档、各档多大」。工程「查看格式」弹窗与「先过滤再请求」两条都失去数据源。**口径注**：本行与 §4.1「曲目字段合集」行**有意重叠**（同一字段集的两种切法），两者都判 gap；§4.3 计数按行、不看字段交叠——勿因此认为重复计数 |
| genre 曲风码（智能调音） | `QqMapper.kt:61`（int）；`SmartEq.kt:25-35`（实测码表）、`:55`（genre==0 不动）；`PlayerHost.kt:195`；`EqualizerScreen.kt:303-340` | **无**（只有字符串标签） | `SongDetail.genreTags[]`（`catalog.rs:251-254`、`split_tags:692-701`）、`ArtistDetail.genreTags/genre`（`:673-679`）、`AlbumDetail.genreTags`（`:386-388`） | **gap**（复核 confirmed） | Track 无 genre；`genreTags` 是字符串标签，与 int 码表不可直接换算，且要走 `fetch_song_detail` 每首一次请求；工程是列表接口免费带的。实测 `genreIsArray:false`（载荷键叫 `genreTags`）——不能当「genre 可用」的证据。保留智能调音就得保留原生字段或自建映射并接受额外请求。**口径注**：本行与 §4.1「曲目字段合集」行**有意重叠**（同一字段集的两种切法），两者都判 gap；§4.3 计数按行、不看字段交叠 |
| 播放条/播放页歌手名 → 歌手页 | `SingerApi.kt:29-32`；`PlayerBar.kt:150-167`（按 `/`、`、`、`,` 切分）；`AppRoot.kt:417-446` | `search_artists` | `catalog.rs:1361-1421,1430-1444`（强制 android） | **partial**（复核 confirmed） | 语义一致（名字→mid/封面/计数，多给 fanCount）。三点差异：① 组件不给歌手数字 id，但工程无消费点（影响为零）；② 档案不同：工程匿名 web、组件强制 android + 设备会话；**组件源码注释说明原因**——`src/catalog.rs:46` 与 `:1360` 都写「搜索端点需要设备身份，其它档案一律答 `meta.sum = 0`」（本轮实读复核，原文 "every other profile answers `meta.sum = 0`"、"answers `meta.sum = 0` for everything without them"）；工程侧的对照实测在 `helpernext-inventory.md:150`（§4.1 第 2 条：同一查询 web 档案 `{"total":0,"tracks":[]}` / android 档案 `total:999`）；③ 多歌手分隔符坑（见曲目字段行）。`<em>` 两边都剥。实测 `total:1004`（设备会话就绪） |
| 后台播放通知封面（另一线程直拉 URL） | `PlaybackService.kt:125-137`；`PlayerHost.kt:206-215` | `normalized_artwork_url` + `Track.imageURL` | `methods.rs:987-996,927` | **covered**（复核 confirmed） | 组件所有封面 URL 强制 https，实测 `coverIsHttps:true`，满足「另一线程直接 GET」的要求，无额外鉴权头 |

### 4.3 汇总

| 结论 | 目录/搜索域（20 行） | 播放/写入域（22 行） | 合计 |
|---|---|---:|---:|
| covered（可直接取代） | 6 | 4 | **10** |
| partial（部分覆盖） | 13 | 14 | **27** |
| gap（缺失） | 1 | 4 | **5** |
| 合计 | 20 | 22 | **42** |

明细：目录域 covered = 搜索歌手、搜索歌单、我喜欢总数、歌手解析、电台分组、昵称；播放域 covered = 歌曲详情、扫码登录、昵称、通知封面（两域"昵称"是同一能力的两处记录，计 2 行）。gap = 目录域「曲目字段合集」+ 播放域「音译 roma / fileSizes / genre / 已下载台账」。其余为 partial。10+27+5=42。

**口径对齐说明（避免读者被"五项/5 行"绕住）**——三套计数口径 + 一条不单列的 OGG 子项：

| 口径 | 计数 | 内容 | 为什么数字不同 |
|---|---|---|---|
| §4.3 gap 行（按"工程能力"分行） | **5** | 曲目字段合集、roma、fileSizes、genre、已下载台账 | 表中一行可以覆盖多个字段（「曲目字段合集」一行同时含 fileSizes/genre/mediaMid 三个字段）；OGG 被归在「音质档位定义」那一行（该行整体判 partial，因为工程档位表本身也有其他差异） |
| §一.3 数据字段类缺口（按"拿不到的字段"计） | **3** | fileSizes、genre、roma | 同上；`mediaMid` 单独归入"实现缺陷"而不是"字段缺失"——上游有、组件只是写错了取值逻辑 |
| §一.3 实现缺陷/覆盖面类 | **2** | `mediaMid` 恒 null（bug）、OGG 两档（覆盖面） | 这两项都不是"数据不存在"，而是"组件实现/表里没有" |
| §4.2「音质档位定义」行里的 OGG 子项 | （不单列计数） | O600/O800 两档 | 子项按 §4.1 图例是 **gap**（组件无等价物），但它没有独立行，落在一条整体判 **partial** 的能力行里——所以 §4.3 的 gap 行仍是 5、不是 6；该行的 partial 是"工程能力行"口径下的判定，两者不矛盾 |

即：**「缺口五项」= 3 个字段 + 1 个 bug + 1 个覆盖面**；**§4.3 的 5 行 gap 是另一种切法**（按能力行）：`曲目字段合集`、`fileSizes`、`genre`、`roma`、`已下载台账`。§4.2「音质档位定义」行内的 OGG 两档子项按 §4.1 图例是 gap（"组件无等价物"），但该行作为一条能力行在 §4.2 表内判 **partial**、§4.3 也按 partial 计数——本报告不改已复核的逐行判定，只在此处把口径写清（两种数法差在 OGG：它没有独立的 gap 行，而是落在一条 partial 行里；另注 `曲目字段合集` 与 `fileSizes`/`genre` 行有重叠计数，见 §4.1 对应行）。

### 4.4 可用 FFI 导出 ↔ 42 行能力（实施对照表）

「16 个可用」指的是**实测反序列化成功、字段非全空**的 typed 导出（`helpernext-inventory.md` §4.7 的全量 47 项实测；本轮未重跑 FFI，行号与结论按该材料与 `verify-mechanics.md` §1.4/§1.5）。除它之外的每一行都必须走 `call_with_platform` 拿原始 JSON 自己解析。

| 可用 typed 导出（16） | 它覆盖本报告的哪些能力行 |
|---|---|
| `component_info` / `login_status` / `import_credential` / `logout` | 昵称（目录域 + 播放域各一行）、网页 cookie 登录、退出登录 |
| `liked_songs` | 我喜欢列表、我喜欢总数（红心/计数也走它） |
| `playlist_tracks`（**total 被 FFI 丢弃**，模型是 `Vec<Track>`） | 歌单内曲目（**要 total 必须走 call_with_platform**） |
| `user_playlists` / `liked_albums` | 收藏的歌单、收藏的专辑 |
| `followed_artists` | 关注的歌手 |
| `album_tracks` | 专辑内曲目 |
| `search_songs` / `search_artists` / `search_albums` / `search_playlists` | 搜索四类、搜索·歌手解析、播放条歌手→歌手页 |
| `set_liked`（返回 `Result<(),_>`，拿不到回执） | 喜欢写入（**要看 `retCode` 必须走 call_with_platform**） |
| `call_with_platform` | 上表之外的兜底入口（见下） |

**必须走 `call_with_platform` 的能力行**（typed 导出不可用或会丢字段）：歌曲详情、逐字歌词/翻译/注音（`lyric` 载荷不匹配）、播放直链（`stream` 载荷不匹配）、歌手歌曲、歌手专辑、歌手资料、专辑详情、电台分组、电台曲目、推荐流、新歌、榜单、封面匹配 `search_*_artwork`、歌手简介 `fetch_artist_biography`、扫码登录 `start_login`/`poll_login`、限流/熔断回读 `guard_status`。

**`call_with_platform` 的签名与载荷约定**（本轮实读生成物与源码）：

- Kotlin 侧（生成物 `/tmp/helpernext_pack_rehearsal/repo/dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt:3225`）：
  `fun callWithPlatform(method: String, paramsJson: String, platform: String): String`
- Rust 侧（`src/api.rs:378-394`）：`#[export] pub fn call_with_platform(method: String, params_json: String, platform: String) -> Result<String, HelperError>`
- 行为：`params_json` 必须能解析成 **JSON 对象**（否则 `HelperError::InvalidRequest("params 必须是 JSON 对象")`）；把 `platform` 作为 `params.platform` 注入；`platform` 只认 `web`/`yqq`/`yqq.json`/`android`（`src/upstream.rs:49-55`，未知值报 `未知的平台档案：…`）。返回的是 `methods::dispatch` 的**原始载荷 JSON 字符串**，不经过 `parse()` 的信封拆解——所以拿到的可能是 `{"detail":…}`、`{"stream":…}`、`{"lyric":…,"wordLyric":…}` 这类带信封的形状，宿主自己取。
- **但注意它不拓宽方法面**：`call_with_platform` 最终仍进 `methods::dispatch`，受 `METHODS` 名单约束——调 `set_rate_limit`/`aria2_*` 依然得到「不支持的方法」（`api.rs:392` → dispatch `other` 分支）。这是 §4.2「音质档位定义」行里"连 call_with_platform 也发不出 OGG"的机制原因（组件没有任何接受任意 params 透传的方法）。

### 4.5 未探针方法的补充验证方案（最小集）

`probe-output.json` 只覆盖 17 行（`readOnly:true, calls:17, ok:17, failed:0`；本轮解析复核）。未覆盖但推荐路线核心的方法，可以复用现成探针脚本 `zcode/workflow-drafts/helpernext-probe.mjs` 的 `ask()` 一次性补测（它已经是只读、带 `QQMUSIC_HELPER_NEXT_DIR`、输出只留布尔/计数的形状）：

| 补充项 | 探针调用 | 要看什么 |
|---|---|---|
| 搜索歌手/专辑/歌单（三个 typed 导出可用，但要确认字段） | 同一批 `search_artists` / `search_albums` / `search_playlists`，`{keyword:"Lemon", page:1, limit:3}` | `total` 是否非 0（检验设备会话）、`songCount/albumCount` 是否存在、专辑结果有无 `songCount`（预期无） |
| 歌手页三连 | `fetch_artist_songs` `{singerMid:…, sort:"hot", page:1, limit:3}`、`fetch_artist_albums` 同参、`fetch_artist_detail` `{singerMid:…}` | 前两者载荷是否只有 `{tracks}`/`{albums}`（确认无 total）、`artist_detail` 是否回 `description/region/foreignName` |
| 歌单内曲目 | `fetch_playlist_tracks` `{songlistId:<搜索歌单拿到的 id>, offset:0, limit:3}` | 是否同时回 `tracks` 与 `total`（`total` 可为 null） |
| **电台无限流（最关键）** | 同一 `stationId` 连续调 `fetch_radio_tracks` 三次（`{stationId, limit:20, firstPlay:true/false}`），按 `songMid` 求交集 | 连续批是否有重叠；重叠则工程"加载更多"的前提不成立 |
| 专辑详情 | `fetch_album_detail` `{albumMid:…}` | 是否返回 `description/genreTags/songCount`（typed 导出在本环境会静默全空，探针要走 stdio 才对） |
| 写操作 | **不要用这个探针**——单独设计一次受控实测，见 §6.2 P4 | `retCode` 与 `tmeLoginType` |
| 扫码登录 | 不适用同一个探针（`start_login`/`poll_login` 需真人扫码） | 已测过 bogus qrsig 被 403 拒绝（§七.4）；DONE 路径的验证标准是「凭据文件出现 musicid/musickey 且 `loggedIn:true`」，需人工执行一次 |

（上表只是"用哪些参数调、看哪几个字段"的清单；本报告**没有执行**这些补充调用——本次调研的探针只跑了 17 行，其余仍未实测。上表覆盖了 §3.4 列出的 11 个未探针方法中的全部可自动探测项。）

### 4.6 组件净新增能力——值不值得顺带接

材料里有依据、且工程侧完全缺的三项（实测数据均来自 `probe-output.json`，本轮复核）：

| 能力 | 组件方法 | 实测 | 接入代价 | 建议 |
|---|---|---|---|---|
| **排行榜** | `fetch_toplist_categories`（`catalog.rs:703-790`）+ `fetch_toplist_tracks`（曲目在 `songInfoList`、总数 `totalNum`） | `groups: 4`（#10） | 两个方法都属"载荷不匹配"→ 走 `call_with_platform` 或 stdio；工程侧需要新页面与卡片（现无榜单 UI） | **值得**——纯增量内容面，工程完全没有；`fetch_toplist_tracks` 的 `{tracks,total}` 形状与工程 `Page` 同构，适配成本低 |
| **新歌（按地区）** | `fetch_new_songs`（`regionType` 0 最新/1 内地/2 港台/3 欧美/4 日本/5 韩国） | `count: 52`（#11） | 同上（载荷不匹配）；需要新页面 | **看产品**——"新歌"是常见入口，但工程当前定位是"收藏+搜索+电台"，优先级低于榜单 |
| **歌手简介/外文名/地区** | `fetch_artist_detail`（`description/region/foreignName/genreTags`）+ `fetch_artist_biography` | 本报告未实测（探针未覆盖）；复核记「上游对多数歌手没有简介文字，`description` 空是正常答案」 | 强制 android；typed 导出不可用；`description` 多数为空，价值主要在外文名/地区 | **可选**——工程歌手页现在只有名字与两个计数（`SingerScreen.kt:247-259`），加"外文名/地区"是低成本增强，但别指望简介文案 |

（另有 `search_track_artwork` / `search_artist_artwork` / `search_album_artwork` 三个封面候选方法：工程目前**无本地曲库**，没有消费者——情况变了再谈，现在接是纯负债。）

---

## 五、缺失与风险

逐条给出：影响 | 证据 | 是否经独立复核。

### 5.1 许可与合规

| # | 风险 | 影响 | 证据 | 独立复核 |
|---|---|---|---|---|
| L1 | 组件 GPL-3.0-or-later，以 `--whole-archive` 静态链进同一个 `.so` 随 APK 分发，**构成组合作品还是聚合使用无定论** | 若按解读 A，整个 App 需以 GPL-3.0 提供**完整对应源码**（含组件 Rust 源码与构建脚本）；若解读 B 则各自许可。工程目前 `README.md:61` 仍写 "License TBD/All Rights Reserved" 与 `AGENTS.md:79` 的 GPL-3.0 声明**自相矛盾**，且仓库根无 LICENSE 文件 | 事实：`Cargo.toml:6`、`LICENSE`（GPLv3 全文、无链接例外）、`link.rs:322-349`；工程 `README.md:61`、`AGENTS.md:79`（均本轮实读）。两种解读并列于 `verify-mechanics.md` §0 | 是（`verify-mechanics.md` §0）。**经独立复核修正**：原提问前提「AGENTS.md 写明 GPL/AGPL 一律不引入」被推翻——原文 `:81` 放行、`:82` 明说旧红线失效 |
| L2 | 作者对链接例外/商业授权**无书面表态** | 「作者是否默认接受专有宿主静态链接」在仓库内无答案 | README/LICENSE 全文无补充条款 | 是（同上） |
| L3 | 若未来要求「可整体替换组件/独立进程」，GPL 组合形态会带来更多义务 | 架构上把组件做成独立进程可弱化解读 A，但 Android 上子进程不可行（见 A1）——两者冲突 | 见 A1 | 是 |

### 5.2 Android 落地机制

| # | 风险 | 影响 | 证据 | 独立复核 |
|---|---|---|---|---|
| A1 | **子进程路径在 Android 不可行** | 只能走 FFI/JNI；意味着 `set_rate_limit`/`set_breaker`/`aria2_*`（= §2.2 注里 11 个命令面方法的全部 11 个：2 + 9）若 FFI 面不修就永远调不到。**`import_cookies`/`logout` 不属于这 11 个**：它们在 FFI 面**可用**（`import_credential` / `logout` 两个导出，`api.rs:69-86` 本轮实读），只是行为与 stdio 版不同（`import_cookies` 经 `dispatch` 会明确报错、`logout` 经 `dispatch` 不删文件）——见 §4.2「网页 cookie 登录」「退出登录」两行 | `pack android` 产物 0 个可执行（本轮复核 8 文件清单）；boltffi 对每个 target 走 `--lib`（路径在 boltffi CLI 的 crate 里：`~/.cargo/registry/src/index.crates.io-1949cf8c6b5b557f/boltffi_cli-0.31.0/src/build.rs:306`，本轮 `grep -rn 'arg("--lib")'` 实跑定位；**不是**组件仓库的 `build.rs`）；Android 10 行为变更原文 + 工程 `targetSdk=35` | 是（`verify-mechanics.md` §3，confirmed）。例外路径「解到 `nativeLibraryDir` 再 exec」**复核未能证实**（无官方表述、未在设备验证） |
| A2 | **FFI 无 `configure` 入口**，数据目录只能靠环境变量 | 不处理则凭据/`device.json` 落到进程 cwd（Android 上通常是 `/`，不可写）→「登录了但没登录」；处理了也要注意时序：**必须在第一次调用组件之前** `Os.setenv("QQMUSIC_HELPER_NEXT_DIR", filesDir, true)`，否则凭据落新目录、`device.json` 留旧目录的劈叉 | `src/lib.rs:88-107`；`src/api.rs:19-26`；`src/upstream.rs:120,135`；`javap android.system.Os`（setenv API 21+，工程 minSdk 26） | 是（`verify-mechanics.md` §1，confirmed）。**经独立复核修正**：原推论「Android 上没有办法设、必须改组件」被复核推翻——`Os.setenv` 是可行（虽绕）的第三条路，代价是上面的时序陷阱 |
| A3 | **47 个导出里 31 个不可用**（11 个「不支持的方法」+18 个载荷不匹配+2 个静默全空） | 宿主若照文档调用 typed API 会拿到 `HelperError` 或**合法的空数据**（后者更危险：`album_detail`/`fetch_artist_biography` 返回 `<ok>` + 全零 payload）；本域受影响的包括 `song_detail`、`artist_*`、电台、推荐流、歌词、取流、`start_login` 等 | `src/api.rs:41-53`（parse 只解 7 键）；`src/methods.rs:394-459`（dispatch 无 11 个命令面方法）；`helpernext-inventory.md` §4.7、`verify-mechanics.md` §1.4/§1.5（含 ctypes 实测） | 是（同上）。绕过：`call_with_platform`（实测可用）或子进程 |
| A4 | **`set_rate_limit`/`set_breaker` 在 FFI 上推不动** | 组件限流/熔断只能跑内置默认值（10s/100、5 次/60s/30s），宿主无法下推用户设置；「宿主每次启动重推」这个设计动作落空。这两个方法**属于 §2.2 注的 11 个命令面方法**（另 9 个是 `aria2_*`） | `api.rs:484-516` 经 `methods::dispatch`；`dispatch`（`methods.rs:394-460`）无这两个分支（本轮实读复核：花括号匹配两函数体后逐名比对 `METHODS`，未覆盖者恰为 11 个；`grep -c set_rate_limit` 在 dispatch 段 = 0）；仅 `src/bin/stdio.rs:146-152` 能用 | 是（`verify-mechanics.md` §4，confirmed） |
| A5 | **38 MiB 包体 + 4KB 页对齐** | 未加链接参数时 `.so` 是 4KB 对齐；16KB 页对齐的设备（Android 15+ 的 16KB 页机型）加载未对齐的 `.so` 会失败 → `rehearsal` 那份补丁就是加 `-Wl,-z,max-page-size=16384`；且 release 包约 **66% 是调试信息/符号表**（4 ABI 逐个实测，见 §3.5），加 strip 类参数是可能的瘦身方向（**砍幅未实测，本报告不给定量承诺**）；4 个 ABI 全打，若只发 arm64 可再裁 | 本轮实测：fresh arm64 LOAD=0x1000、rehearsal 4/4 ABI=0x4000；sizes 合计 39,893,388 B；debug+sym 合计 26,337,038 B（66.0%，逐个 ABI 见 §3.5）。**「Google Play 自 2025-11 对 targetSdk 35+ 要求 16KB 对齐」这一政策陈述在本报告材料内没有官方来源，属于背景常识、待验证** | 是（`verify-mechanics.md` §2，confirmed：38MB、0x1000/0x4000、4/4 ABI）。补充：201.7 MiB 那份是 **debug profile**，不是页面大小差异 |
| A6 | **组件崩溃 = 宿主进程崩溃**；FFI 函数**同步阻塞**（全局超时 20s） | 必须全部丢到 `Dispatchers.IO`，否则 ANR；限流等待也用 `std::thread::sleep`，同样占着调用线程 | 超时：`src/upstream.rs:124-131,179`（`timeout_global(Some(Duration::from_secs(20)))`；`docs/ffi.md:62` 写的 12 秒与源码不符，以源码为准）。等待：`src/guard.rs:189`（`std::thread::sleep(duration.min(Duration::from_secs(2)))`，本轮实读复核）——**限流等待确实在调用线程里**。**「组件崩溃 = 宿主进程崩溃」是 FFI 同进程加载的机制推论，本报告材料内没有实测引用**（`helpernext-mechanics.md` §6.2 把它列为 README 未写、宿主须知的一条，属设计层推断而非实测）→ 按待验证看待 | 部分：超时 20s 与 sleep 位置经复核（confirmed）；「崩溃带走宿主」**无独立复核**，待验证 |
| A7 | `min_sdk=24` 低于工程 `minSdk=26`；包名是示例 `com.example.…` | 对接时改 `boltffi.toml`：`min_sdk=26`、真实包名；改包名会进 `jni_glue.c`，必须重新链接（`pack android` 默认 `--regenerate=true`） | `boltffi.toml:40,43`（本轮实读）；`jni_glue.c:44` 的 `FindClass` | 是（`verify-mechanics.md` §5b，confirmed） |

### 5.3 能力缺口与语义风险

| # | 风险 | 影响 | 证据 | 独立复核 |
|---|---|---|---|---|
| C1 | `Track` 无 `fileSizes` / `genre` / `mediaMid`（前者结构性缺失，后者源码 bug） | 「查看格式」弹窗消失、音质档位过滤失效（每档都发请求）、智能调音失效；`mediaMid` 恒 null 需宿主兜底 | `methods.rs:917-933`（本轮实读复核）、`models.rs:131-155`、`:921` 的 `first_int(...).and(...)`；实测 `firstHasMediaMid:false` | 是（两域复核均 confirmed） |
| C2 | 无 `roma`（写死 `roma:0`） | 「音」开关失效，除非宿主保留原生 `LyricApi` 自己按 `roma:1` 取 | `catalog.rs:970`（本轮实读复核）、`models.rs:393`；实测三首全 false | 是 |
| C3 | 逐字歌词形态是词级 LRC 文本、无行/字时长 | 宿主必须写新解析器 + 合成 `endMs`；直接喂工程 `parseLrc` 会每行只剩最后一个词 | `qrc.rs:507-525`；`LyricApi.kt:337`（本轮实读复核）；两域复核 | 是（含两处收紧，见 §4.2） |
| C4 | 若干「总数」丢失（歌手歌曲/专辑、关注歌手 Total、收藏专辑歌数、收藏歌单分页） | 底部文案退化、收藏 >100 条无法翻全 | `methods.rs:164-183,804-867,776-801,744-773` | 是 |
| C5 | **不支持多请求块合并** | 工程「30 张专辑 1 次 HTTP 补歌数」（`SingerApi.kt:84-102`）在组件上膨胀成 30 次请求 | `src/upstream.rs:240,348-355` 恒 `vec![call]`；复核实跑 grep | 是（`verify-catalog.md` id 13，confirmed） |
| C6 | 分页语义 offset → page；`latest` 只页内排序；`fetch_user_playlists` 无分页 | 宿主分页算法改写；「最新」跨页不是全序 | `methods.rs:479,487-502,714-717,744-773` | 是 |
| C7 | 写操作：不校验 `data.retCode`、业务码文本化、`tmeLoginType` 1 vs 2 | 红心可能与服务端不一致；限流提示拿不到结构化码；写成败关键参数有差异（未验证） | `upstream.rs:242-259,528`、`catalog.rs:1311`、`upstream.rs:371` vs `QqCore.kt:53` | 是（复核确认前两条；`tmeLoginType` 差异为复核新增，未实测） |
| C8 | 空值形态：组件全 `Option(None)`、工程按空字符串 | 封面占位/爱心不触发，必须逐处 `orEmpty()` | `models.rs:146-147,174-176`；`Cards.kt:84,128`（本轮实读复核） | 是 |
| C9 | 电台曲目换了上游方法，无限流前提未验证 | 若 `GetRadiosonglist` 不回轮换，工程无限加载失效 | `catalog.rs:850-873`；`RadioApi.kt:36-63` | 是（复核确认「未验证」属实） |
| C10 | 推荐流参数写死、无 exclude | 预缓冲去重只能留在宿主；上游回同一批时预缓冲退化 | `catalog.rs:900-916`；`RecommendStore.kt:86-91` | 是 |
| C11 | 限流语义不同：组件是「窗口计数+分桶」（超限等待），工程有**最小间隔**闸（120ms） | 组件的 10s/100 ≈ 均 100ms，短时突发在额度内可连发——**不提供最小间隔语义**；熔断是净新增 | `src/guard.rs:13,91-95,189,241-247`；`QqCore.kt:73-90`（本轮实读复核） | 是（`verify-mechanics.md` §4，confirmed） |
| C12 | aria2 相关：引擎二进制不在仓库、产物不含它、目录钉死、FFI 面 9 个方法全死 | Android 上必须自带下载实现（不是可选退让，是默认路径） | `aria2.rs:153-159,212-223,196-200`；`docs/endpoints.md` §四；本轮 find 复核 | 是（复核确认；计数修正 11→9） |
| C13 | 取消/暂停在 FFI 传 `Some(gid)` 时的行为**未定论** | 可能是复核者自己的 ABI 编码问题，**不要当成组件缺陷** | `helpernext-mechanics.md` §7#4 | 是（明确标「未定论」） |

---

## 六、接入方案建议

### 6.1 推荐路径

**走 FFI/JNI（唯一 Android 可行路径），以「组件优先、原生兜底」的混合形态接入**：

- 用组件承接**能干净对上**的读取能力（搜索四类、收藏/我喜欢、专辑/歌单曲目、歌手解析、电台分组、推荐流、歌曲简介、整句歌词+翻译、昵称、封面、扫码登录）；
- 保留工程原生 `QqCore` 直连作为**兜底与补充**：OGG 两档、`roma` 音译、写操作回执校验（`retCode`）、以及组件承载不了的批量请求合并（歌手专辑歌数）与 `fileSizes/genre`（若不愿改组件）；
- `Os.setenv` 时序、错误映射、`None→orEmpty`、分页换算、mid 去重、失败/空语义区分，全部收进一个新的宿主适配层。

### 6.2 需要先解决的前置项（按优先级）

| # | 前置项 | 不做的后果 |
|---|---|---|
| P1 | **定许可**：确认采用解读 A 还是 B；同步 `README.md:61` 与 `AGENTS.md:79`（当前自相矛盾）；仓库补 LICENSE 文件 | 法律义务不明；README 与 AGENTS 冲突 |
| P1a | **许可 checklist（可执行版）**：① 先决定分发形态——**随 APK 分发预编译 `.so`**（解读 A 风险最高）还是**只作外部工具/自用不分发**（解读 B 最稳）；② 若随 APK：按 GPLv3 §6 准备"完整对应源码"的交付物清单（组件 Rust 源码 + `boltffi.toml` + 构建脚本 + 本工程源码），并确认 `AGENTS.md:79` 的 GPL-3.0 声明与 `README.md` 的实际发布一致；③ 向组件作者确认"作者是否接受专有宿主静态链接"（仓库内无书面表态，只有这一条外部事实缺口）；④ 若宁可绕开：**改走独立进程/独立工具**（桌面可行、Android 不可行，见 A1）或**不用组件、保留原生直连**；⑤ 拿不准时找法律意见——本报告只列事实不下结论 | 这是唯一一条"做错了需要事后补救、且补救成本极高"的前置项 |
| P2 | **向上游提修组件**（或接受 16 个可用导出 + `call_with_platform`）：a. `dispatch` 接入 **11 个命令面方法**（名单见 §2.2 注）；b. `parse()` 补信封键（或改为不透传模型、直接返回 JSON）；c. `decode_track` 补 `fileSizes/genre` + 修 `mediaMid` 的 `first_int` bug；d. `fetch_lyric` 补 `roma:1`；e. `set_liked` 校验 `data.retCode`；f. `boltffi.toml` 改包名/min_sdk=26、加 strip 与 `-Wl,-z,max-page-size=16384`（**砍幅未实测**） | 限流配置推不动、18 个 typed 方法不可用、格式弹窗/智能调音/音译失效、红心可能误报、包体与对齐不合规 |
| P3 | **数据目录时序**：`Application.onCreate` 里 `Os.setenv("QQMUSIC_HELPER_NEXT_DIR", filesDir.absolutePath, true)`，必须在**首次调用组件之前**（含任何 `get_login_status`） | 凭据/device.json 劈叉或永远存不下，表现为「登录了但没登录」 |
| P4 | **受控实测写成败**：组件 android comm `tmeLoginType:1` vs 工程 `2`——做一次最小写实测（注意：先读 AGENTS.md 的 `1000` 限流教训，**绝不反复重试**） | 红心写失败或静默不一致 |
| P5 | **验证电台无限流**：`GetRadiosonglist` 是否「每次调用回不同的一批」 | 电台「加载更多」失效 |
| P6 | **FFI 调用线程**：所有组件调用丢 `Dispatchers.IO`（同步阻塞、最长 20s）；`HelperError` 统一映射 | ANR |

### 6.3 按模块的替换顺序（建议）

1. **搜索四类**（最干净；补 `limit:30`；歌曲结果记得自行剥 `<em>`、补 `mediaMid` 兜底）——可先只读灰度。
2. **收藏三列表 + 我喜欢**（改 page 分页、处理 `songnum` 缺失、删 euin 前置）。
3. **专辑/歌单内曲目**（语义直接对应）。
4. **歌手页**（补 `total` 退化文案、`latest` 页内排序接受度、专辑歌数方案：保留原生批量 or 30 次请求）。
5. **电台/推荐**（先验证 C9）。
6. **歌曲简介/封面/昵称/扫码登录**（covered 项，低风险）。
7. **歌词**（需先写词级 LRC 解析器 + `endMs` 合成；`roma` 与 kana 策略同时定）。
8. **取流**（先定 OGG/fileSizes 策略）。
9. **写操作**（最后，P4 之后）。
10. **下载**（组件 aria2 在 Android 默认不可用，保持宿主实现；组件仅作将来 macOS/桌面的并列选项）。

### 6.4 文件处置

| 处置 | 文件 | 说明 |
|---|---|---|
| **可删除** | `data/api/SearchApi.kt`、`data/api/SingerApi.kt`、`data/api/UserApi.kt`、`data/api/RadioApi.kt`、`SongApi.intro` 部分 | 前提是接口在主流程上改调组件适配层；`RadioApi.nextTracks` 的去重/分批逻辑要**搬进适配层**而不是直接删（组件无 exclude） |
| **变薄壳（推荐）** | 整个 `data/api/` 变成「组件适配层」：`QqCore.kt` 只保留被兜底能力用到的直连（OGG、roma、写回执、批量合并），`QqMapper.kt` 改成「组件模型 → 工程 Track」的映射；`PlaylistApi.likedPage` 等改走组件 | 不建议一步全删——工程侧的切分/去重/分页/占位判断仍是必需的宿主逻辑 |
| **必须保留** | `data/LikedStore.kt`（本地红心集合）、`data/HomeCache.kt`/`RecommendStore.kt`（宿主缓存与预缓冲）、`data/DownloadStore.kt`（台账）、`ui/player/LyricsView.kt`+`data/Lyrics.kt`（渲染与扫色）、`player/SmartEq.kt`（除非放弃智能调音）、`ui/common/TrackDialogs.kt`（除非放弃格式弹窗）、`player/PlaybackService.kt`（通知封面线程）、`player/PlayerHost.kt`（播放器状态机） | 这些是纯宿主能力，组件不涉及 |
| **条件保留** | `data/api/QrcCodec.kt`、`data/api/LyricApi.kt` | 只在保留 `roma:1` 原生路径或需要字级渲染时才需要；若走组件词级 LRC，需要的是**新解析器**而不是 `parseQrcXml` |
| **新增** | 组件宿主层（建议名 `data/helper/HelperHost.kt`）：`setenv` 时序、IO 线程封装、`HelperError`→工程异常、`None`→`orEmpty`、分页换算、`singers[]`/多歌手切分、`mediaMid` 兜底、`<em>` 清洗 | 全部宿主责任集中一处，避免散落 |

### 6.5 工程量、包体与迁移（材料能支撑的部分）

> 这一节只写**材料里有依据**的数字；本报告**没有**做工作量估算（没有人日数据，也没有可引用的同类先例），不做编造。

**代码规模（本轮 `wc -l` 实跑，`app/src/main/java/com/neumusic/player/data/api/`）**：

| 文件 | 行数 | 替换后的处置 |
|---|---:|---|
| `QqCore.kt` | 144 | 变薄壳（只留兜底直连 + 节奏闸） |
| `QqMapper.kt` | 125 | 变薄壳（改成组件模型 → 工程 `Track`） |
| `SongApi.kt` | 161 | 拆分：`intro`/`setLiked`/`playUrl` 三条路各定策略 |
| `LyricApi.kt` | 417 | 条件保留（`roma`/kana/字级渲染）；新增词级 LRC 解析器 |
| `QrcCodec.kt` | 228 | 条件保留（只在保留 `roma:1` 原生路径时需要） |
| `SingerApi.kt` | 103 | `resolve` 改调组件；`fillSongCounts` 需替代方案 |
| `PlaylistApi.kt` | 98 | 改调组件 + 分页换算 |
| `SearchApi.kt` | 92 | 改调组件 |
| `RadioApi.kt` | 64 | `nextTracks` 的批间 delay/mid 去重搬进适配层 |
| `UserApi.kt` | 59 | 改调组件（euin 前置可删） |
| `ApiCache.kt` | 56 | 保留 |
| **合计** | **1547**（`wc -l data/api/*.kt` 尾行实跑） | 其中约 700 行（`SongApi`+`LyricApi`+`QrcCodec`）属于"条件保留"池，不是净删除 |

**工程量（为什么本报告不给"人日"，以及能给什么）**：本报告材料里**没有任何人日/工时数据**，也没有可引用的同类移植先例——按"不编造"原则，这里**不给总人日**。能支撑的只有**范围口径**（可当作估算输入，不是估算结果）：

| 计量口径 | 材料里的数 | 出处 |
|---|---:|---|
| 需改动的工程在线层 | **1547 行 / 11 个文件**（其中 `ApiCache.kt` 56 行按现方案"保留"不动、~700 行属条件保留池 ⇒ **净需动的规模小于 1547 行**，但本报告材料无法给出净行数） | 本节上表（`wc -l` 实跑） |
| 必须新建的宿主适配层 | 未定行数（§6.4「新增」列了 8 类职责：`setenv` 时序、IO 线程封装、`HelperError`→工程异常、`None`→`orEmpty`、分页换算、`singers[]`/多歌手切分、`mediaMid` 兜底、`<em>` 清洗） | §6.4、§6.5 各表 |
| 需新写的解析器 | 1 个（词级 LRC → 工程 `LyricLine`+`endMs` 合成），现有 `parseQrcXml`/`parseLrc` 都不可直接用 | §4.2「逐字歌词」行、§6.3 第 7 步 |
| 必须保留的原生路径 | 5 类（OGG 两档、`roma` 音译、写操作回执校验、批量请求合并、`fileSizes/genre`） | §6.1、§6.3 |
| 前置实测次数 | 至少 4 次受控实跑（P4 写成败、P5 电台无限流、§4.5 探针补充、扫码 DONE 路径） | §6.2 P4/P5、§4.5、§7.4 |
| 逐域替换顺序 | 10 步，其中 4 步（第 5/7/8/9 步）写明"先验证/先写/先定/在 P4 之后"的前置条件 | §6.3 |

即：工程侧**至少**要动 11 个文件、新建 1 个适配层与 1 个歌词解析器、跑 4 组前置实测；**"几天还是几周"本报告材料无法回答**，需要工作时序数据或打样一次才知道。

**App 包体增量**：4 个 ABI 合计 **39,893,388 B ≈ 38.05 MiB**（本轮 `stat` 实跑）。只发 arm64-v8a 的增量是 **10,720,072 B ≈ 10.2 MiB**（`stat` 实跑）。这两个数是**加进 APK 的 `.so` 裸大小**，未计 AAB/Play 分发压缩（本报告没有实测压缩后的下载增量）。**除这一条 `.so` 裸大小外，本报告没有别的包体增量估算**（不含 R8/资源/依赖变更）。


**凭据迁移（从 `Prefs` 到组件凭据文件）**：工程现状是 `Prefs.credential`（`neumusic.xml` 里一段 JSON，字段 `uin/musickey/euin`，`Prefs.kt:352-374`）；组件要的是 `<data_dir>/Credential/qqmusic-credential.json`（键 `musicid/str_musicid/musickey/encrypt_uin`，`credential.rs:117-164`）。迁移路径（材料可支撑的方案，**未实测**）：

1. **一次性 `importCredential(uin, qm_keyst)`**（`api.rs:70`，两参数）把已有 cookie 写进组件文件；`uin` 记得去 `o` 前缀（组件不做，`correspondence-playback.md` A12）。
2. **先设 `Os.setenv` 再调 `importCredential`**（顺序错了会写进 cwd，见 A2）。
3. **写完校验**：`loginStatus()` 是真问上游（`methods.rs:625-660`），凭据不可用会如实回 `loggedIn:false`；这一步失败**不要重试多次**（避免无谓往返）。
4. **回滚路径**：`Prefs.credential` **先别删**——组件侧失败时直接退回原生直连（`Prefs` 还在，`QqCore.commAuth()` 原样可用）；确认组件路线稳定后再清。这段"迁移期双写"是本报告的建议，**没有实测过**。
5. 页面显示账号要自己读组件 JSON（`get_login_status` 只回 musicId/昵称，不回 euin；实测凭据文件里本就没有 euin）。

**.so 加载/初始化失败的降级（建议，未实测）**：`System.loadLibrary` 失败会抛 `UnsatisfiedLinkError`（生成物 `dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt:641-651` 的 `Native` object：Android 运行时走 `System.loadLibrary("qqmusic_api_helper_next")`，本轮实读复核），进程内首次触碰组件（含 `get_helper_info`）即触发。建议：① 用一个 `runCatching` 包住首次初始化，失败即把"组件可用"标记置 false；② 适配层所有调用点按该标记走原生直连；③ 对用户可见的只有性能/能力降级（如音译/OGG 仍可用，搜索改走原生），不弹错误。**这条降级行为在报告材料里没有现成设计，属建议而非结论。**

**各模块验收/回归标准（建议，未执行）**：本轮**没有跑任何工程侧测试**（约束：不跑 gradle），所以下面只给"该验什么"的口径，不给通过结果：

| 模块 | 该验的行为 | 判定方式 |
|---|---|---|
| 搜索四类 | 分页到底（用组件 `total` 而非"本页 0 条"）、`<em>` 已剥、`mediaMid` 兜底 | 对同一关键词比对原生与组件两侧的条数与首项字段 |
| 收藏/我喜欢 | 479 条（实测总数）能翻全、红心集合与 `LikedStore` 一致、缺 euin 不再阻断 | 用真实账号跑一次全量翻页并与工程原生结果做集合 diff |
| 曲目/歌手/专辑列表 | 底部文案在 `total` 缺失时退化正确、`latest` 页内排序的观感接受度 | 人工比对排序前 3 页 |
| 歌词 | 逐字扫色仍逐字（CJK）、`endMs` 合成不越界、译/音/注开关门控 | 播放页目视（由用户做，见 §5 禁令） |
| 取流 | 降级链命中、失败文案分级、`playable:false` 映射成异常 | 对同一首 VIP/免费曲两侧各取一次 |
| 写喜欢 | `retCode` 校验、`1000` 限流提示、不自动重试 | 受控单次实测（P4）——**不要反复验证**，AGENTS.md `:131-132` 有实测教训 |
| 下载 | 引擎缺席时走宿主实现；`installed==false` 时不显示可用按钮 | 一次下载到公共目录 |

### 6.6 边界强约束（写进 CI/流程，避免"改了它却忘了旧行号"）

- **组件本地仓库与文档基准是两棵树**：工程内引用必须写清基准提交。本报告已按文件给出漂移（`api.rs` +8 / `upstream.rs` +145 / `methods.rs` +13 / `catalog.rs` +0，§表头），**上游再演进时这四个数会变**。维护方式（建议）：① 把 `/tmp/helpernext_probe` 固定在一个 tag/commit（2ff7e71）作为"报告基准树"，任何涉及行号的新工作都从它 checkout；② 换基准时重跑一次 `wc -l` 逐文件对照 + `diff`（本报告表头就是这条命令的产物）；③ 报告/清单里所有引用都写"文件:行 @ 基准提交"，不写裸行号。
- **绑定重生成流程**：改了 `boltffi.toml`（包名/min_sdk/`extra_args`）后必须整条重跑 `boltffi pack android --release`（默认 `--regenerate=true` 会重生成 Kotlin + 头 + `jni_glue.c`，`cli.rs:144-150`，`pack/android/mod.rs:83-105`）——**包名会进 `jni_glue.c` 的 `FindClass`，只改 Kotlin 不改 `.so` 会运行期找不到类**（本轮实读复核 `jni_glue.c` 与 `verify-mechanics.md` §5b）。重生成后要复跑的检查：① `grep -c "#\[export\]" src/api.rs` vs 头文件 `boltffi_function_*` vs Kotlin 顶层 `fun` 三方计数一致（当前 47=47=47）；② 重新 `llvm-readelf -lW` 确认 LOAD 对齐；③ 用真实账号重跑 §4.5 的探针（**只读**）。
- **谁负责**：本报告材料里没有指定负责人/流程——这是一条需要团队自己定的**缺口**，不是可引用的结论。

---

## 七、未覆盖与未验证

如实记录，避免读者把推断当实测：

1. **没有跑任何构建**（约束：不跑 cargo/gradle/boltffi）。§三 的打包/编译/测试结论均来自本次调研工作流的执行记录与磁盘上**已存在**的产物；撰写本报告时我只重跑了只读复核（文件清单、尺寸、对齐、class 计数、probe JSON 解析、许可与 git 提交）。
2. **没有在 Android 设备/模拟器上验证**（约束：不连 adb）。所有 FFI 实测都在 **macOS dylib** 上完成（`/tmp/helpernext_probe/target/debug/deps/libqqmusic_api_helper_next.dylib`，ctypes 调用）；`.so` 本身从未跑过。A2 的「Android 上 `.` 不可写」是平台常识 + 代码路径推导，不是设备实测。
3. **未实测写操作 `set_liked`**（只读原则，`probe-output.json` 无该行）。C7 全部来自源码；`tmeLoginType` 差异对写成败的影响**未验证**。
4. **未实测 `start_login`/`poll_login` 的 DONE 路径**（需真人扫码；只测到 bogus qrsig 被 403 拒绝）。
5. **探针未覆盖的组件方法**：`search_artists/albums/playlists`、`fetch_playlist_tracks`、`fetch_album_detail`、`fetch_artist_songs/albums/detail`、`fetch_radio_tracks`——这些行的 verdict 只有源码依据。
6. **「收藏的歌单」两条端点（`PlaylistFavRead` vs 老 fcgi `reqtype=3`）对同一账号是否同一集合未验证**（工程侧条数无留档，本轮无网络）。
7. **「解到 `nativeLibraryDir` 再 exec」这条例外路径 复核未能证实**（无官方表述、未在设备验证）。
8. **`aria2_pause/unpause/cancel` 传 `Some(gid)` 时的 `ptr=NULL/len=0` 未定论**——很可能是复核者自己的 ABI 编码问题，**不要当成组件缺陷**（传 `None` 时的「不支持的方法」是确定的）。
9. **组件返回的 `translation` 是否含 `//` 占位、是裸 LRC 还是带 XML 壳未验证**（probe 只报布尔）；建议宿主解析前先打印一次确认。
10. **未做逐字歌词端到端还原**（未把组件 `wordLyric` 实际喂给工程解析器）——解析代价结论来自两侧源码对照与静态推演。
11. **组件 `min_sdk=24` 在产物里的体现**：复核只到 `.note.android.ident` api_level=24（python 解）+ 目录名，未反汇编确认全部细节。
12. **两份 probe 输出有细微差异**：`/tmp/helpernext-research/probe-output.json` 与 `/tmp/helpernext-workflow-cred/probe-output.json` 的 `fetch_new_songs.count` 为 69 vs 52，其余逐字相同（`correspondence-catalog.md` 的命令记录）；不影响本报告结论，但没有解释差异来源。
13. **文档与源码的出入**（以源码为准）：`docs/ffi.md:62` 写「12 秒超时」而源码是 20 秒；`src/api.rs:326-327` 注释声称能拿 romanisation 而实现恒 null；`helpernext-mechanics.md` §6.2 列出的 macOS README 与 Android FFI 形态不适用项（stdio 协议、双份查找、ad-hoc 签名等）整段不适用。
14. **许可的法律性质不下结论**：只列事实与两种解读；作者意图无从核实。
15. **本报告 §4.5 的补充验证方案未执行**：那张表只是"调哪些参数、看哪几个字段"的清单，本次调研的探针只跑了 17 行，其余仍未实测（见附录 E35）。
16. **几处以"待验证"标注的背景陈述**（材料内无官方来源，不应当作结论）：① 「Google Play 自 2025-11 对 targetSdk 35+ 要求 16KB 对齐」——本报告只实测了"未加参数是 4KB、加参数是 16KB"，政策文本未引官方文档（§5.2 A5）；② 「组件崩溃 = 宿主进程崩溃」是同进程加载 FFI 的机制推论，材料内无实测引用（§5.2 A6）；③ 「加 strip 参数能砍多少包体」未实测，本报告只给了"release 包 66% 是调试信息"这一实测比例与 cargo 侧 stripped 产物 3,408,120 B 的量级参照（§3.5）。
17. **两处"部分查过"的检查**（如实标注覆盖范围）：`llvm-nm -D --undefined-only` 的 `getenv` 与 `-D --defined-only` 的 `boltffi_function_*` 计数，定稿轮对**4/4 个 Android ABI 全部实跑过**（结果：exports=47、`getenv@LIBC` 各 1，见 E10）；但**那 47 个导出在 Android `.so` 上从未被调用过**——材料里的全量 47 项调用实测是在 **macOS dylib** 上做的（`verify-mechanics.md` §1.4/§1.5），所以"某导出不可用"的结论严格说是"macOS 目标上实测 + 源码结构一致"，Android 侧的等价性靠源码（`grep -rn "target_os" src/` 无命中，无平台条件编译）推断。

---

## 附录：原始材料与提交号

### A. 材料路径（工作区内）

> 正文里出现的 `verify-mechanics.md` / `verify-catalog.md` / `verify-playback.md`、`helpernext-inventory.md`、`helpernext-mechanics.md`、`correspondence-*.md` **不是外部附件**：它们是本仓库同一目录（`.zcode/research/`）下的姊妹文档，就在本报告旁边，可直接打开查复核命令与输出。正文里标「复核 confirmed」「经独立复核修正」「本轮实读复核」的条目，其原始命令与结果都在这些文件里，本报告只引用结论。**为方便不打开姊妹文档的读者，正文所依赖的命令与关键输出已集中转录到附录 E（可复跑清单，含「定稿轮实跑」与「引自 xxx」两类来源标注）。**

| 文件 | 内容 |
|---|---|
| `.zcode/research/helpernext-inventory.md` | 组件接口盘点（46 方法总表、35 数据模型、FFI 面 31/47 不可用实测） |
| `.zcode/research/helpernext-mechanics.md` | 组件落地机制（绑定面、凭据、限流熔断、aria2、打包、macOS README 对照） |
| `.zcode/research/project-api-inventory.md` | 工程在线能力盘点（11 个 api .kt 的对象/函数/上游/调用点） |
| `.zcode/research/project-requirements.md` | 工程功能需求（28 项用户可见功能对在线层的依赖 + 跨功能约束） |
| `.zcode/research/correspondence-catalog.md` | 目录/搜索域 20 条逐条对应 |
| `.zcode/research/correspondence-playback.md` | 播放/写入域 22 条逐条对应 |
| `.zcode/research/verify-mechanics.md` | 机制独立复核（许可、数据目录、打包、子进程、限流、BoltFFI） |
| `.zcode/research/verify-catalog.md` | 目录域独立复核（20 条，无 refuted） |
| `.zcode/research/verify-playback.md` | 播放域独立复核（22 条，1 处子句被实测推翻） |
| `.zcode/workflow-drafts/helpernext-integration-research.ts` | 本次调研工作流脚本（含实测命令与报告条目） |
| `.zcode/workflow-drafts/helpernext-probe.mjs` | 只读探针脚本 |

### B. 工作区外的实测产物（只读引用）

| 路径 | 内容 |
|---|---|
| `/tmp/helpernext_probe` | 组件源码工作副本 @ **2ff7e71**（本报告行号基准） |
| `/tmp/helpernext-workflow-cred/probe-output.json` | 真实账号只读探针输出（`readOnly:true, calls:17, ok:17, failed:0`），168 行 |
| `/tmp/helpernext-workflow-cred/Credential/qqmusic-credential.json` | 探针凭据（键 `musicid,musickey,str_musicid`，无 encrypt_uin） |
| `/tmp/helpernext-workflow-cred/device.json` | 设备身份（含 QIMEI 与 session_uid/sid/vkey） |
| `/tmp/helpernext-workflow-pack/repo` | `pack android --release` 的干净副本产物（4 .so = 39,893,388 B；`boltffi.toml:59 extra_args=[]`） |
| `/tmp/helpernext_pack_rehearsal/repo` | 打过 16KB 页对齐补丁的对照产物（4/4 ABI LOAD=0x4000） |
| `/tmp/helpernext-research/kotlin-out` | Kotlin 绑定编译产物（88 个 .class） |
| `~/Library/Application Support/kmgccc.player/QQMusicHelperNext/` | 参考宿主目录（macOS 二进制 + README + aria2-next），仅只读引用 |

### C. 两边提交号

| 侧 | 提交 | 说明 |
|---|---|---|
| 组件（本报告行号基准） | `2ff7e71` | `feat: stop when the host stops, however it stopped`（2026-10-01） |
| 组件（本地仓库当前 HEAD） | `5cb2e82` | port 提交；相对基准 `2ff7e71` 的**逐文件**漂移（2026-10-02 实跑 `wc -l`）：`src/api.rs` **+8**（596→604）、`src/upstream.rs` **+145**（578→723）、`src/methods.rs` **+13**（1092→1105）、`src/catalog.rs` **+0**（1699，md5 两处同为 `2ccdc2d2…`）——**不是统一的 +13**，以本表第一列为准 |
| 工程 | `b6c5c29` | `Seventh batch: one transition style, unclipped flight origin, trap the intro scroll`。另一 agent 的播放详情改动**已提交为 `f6b2255`**（"Player page: port the vinyl turntable from the lab into the real player"）：`git diff --numstat b6c5c29 HEAD -- app/src/main` → `PlayerScreen.kt` **80+/63−**、新增 `VinylTurntable.kt` **760+/0**；工作区相对 HEAD 干净（`git status --short -- app/src/main` 空）。**本报告引用的工程行号全部是 b6c5c29 的** |

### D. 复核判定汇总（机制 5 条，来自工作流复核员）

| # | 结论 | 复核判定 |
|---|---|---|
| 0 | 组件 GPL-3.0-or-later；「AGENTS.md 写明 GPL/AGPL 一律不引入」 | 前半 confirmed；**后半 refuted**（原文 `:81-83` 方向相反）→ 本报告 §2.3 已改写 |
| 1 | FFI 无 `configure`/数据目录入口；只认 configure/环境变量/`.` | confirmed；由它推出的「Android 必须改组件」**refuted**（`Os.setenv` 可行，但有时序陷阱）→ 本报告 §5.2 A2 已改写 |
| 2 | 4 个 jniLibs ≈38MB；0x1000 → 加参数后 0x4000 | confirmed（4/4 ABI）；补充：201.7 MiB 是 debug profile，38 MiB 含 **66.0%** 调试信息/符号表（4 ABI 逐个实测合计 26,337,038/39,893,388 B，见 §3.5；**不给 strip 砍幅的定量承诺**） |
| 3 | Android 上不能把组件当子进程驱动 ⇒ 必须 FFI/JNI | confirmed；例外路径「nativeLibraryDir 再 exec」**复核未能证实** |
| 4 | 限流/熔断是内存态、宿主每次要重推 | confirmed；并列反例：`set_rate_limit`/`set_breaker` 在 FFI 路径**推不动** |
| 5 | 多语句 wire writer 会跳过函数；当前无跳过；包名/min_sdk 在 boltffi.toml 改 | confirmed（47=47=47=47；`.note.android.ident` api_level=24 可验） |

### E. 复核命令与关键输出（可复跑）

下表把正文里「复核 confirmed / 经独立复核修正 / 本轮实读复核」所依赖的命令与输出集中列出，尽量让读者不必打开姊妹文档也能自查。**标注「定稿轮实跑」的是撰写本报告期间（2026-10-03）由本报告作者在本机重新执行的**；标注「引自 …」的来自工作流执行记录或姊妹文档（该次命令的原始输出在其文件里），**本报告作者未重跑**。E35 列的是本次**没有**执行、因而不作为通过依据的检查。

| # | 命令（可复跑） | 关键输出 | 用于正文 |
|---|---|---|---|
| E1 | 定稿轮实跑：对 `src/{api,upstream,methods,catalog}.rs` 在两棵组件树各跑 `wc -l` | `api.rs` 596→604（+8）、`upstream.rs` 578→723（**+145**）、`methods.rs` 1092→1105（+13）、`catalog.rs` 1699→1699（+0） | 表头行号基准、附录 C |
| E2 | 定稿轮实跑：`grep -n "fn parse" <两树的 src/api.rs>`；`grep -n "fn profile_assets" <两树的 src/upstream.rs>` | `parse` 41（probe）→ 49（HEAD）；`profile_assets` 273 → 343 | 表头「不是统一 +13」 |
| E3 | 定稿轮实跑：`grep -c "#\[export\]" /tmp/helpernext_probe/src/api.rs` | `47` | §2.2、§5.2 A3 |
| E4 | 定稿轮实跑：`grep -rl "kana" /tmp/helpernext_probe/src/` | 无输出（0 命中） | §4.2 注音行 |
| E5 | 定稿轮实跑：`grep -rn '"roma"' /tmp/helpernext_probe/src/` | 仅 `src/catalog.rs:970: "roma": 0,` | §4.2 音译行 |
| E6 | 定稿轮实跑：`grep -rn "size_" /tmp/helpernext_probe/src/` | 只命中 `src/models.rs:63` 与 `src/aria2.rs` 的 `min_split_size_mib` | §4.2 fileSizes 行 |
| E7 | 定稿轮实跑：`grep -rn "101404" /tmp/helpernext_probe/src/` | 0 命中 | §4.2 播放直链行 |
| E8 | 定稿轮实跑：对 `catalog_dispatch`(92..363) + `dispatch`(394..460) 做花括号匹配取函数体后，与 `METHODS`（46 项）逐名比对 | 未被任何执行臂覆盖的方法恰为 **11 个**（名单见 §2.2 注）；`grep -c set_rate_limit` 在 dispatch 段 = 0 | §2.2 注、§5.2 A4 |
| E9 | 定稿轮实跑：读 `sed -n '25,83p' src/methods.rs`（`METHODS` 数组） | 46 项，与 `probe-output.json` #1 的 `methodCount: 46` 一致 | §2.1 |
| E10 | 定稿轮实跑：`strings -a <4 个 Android .so> \| grep -c QQMUSIC_HELPER_NEXT_DIR`；`llvm-nm -D --undefined-only <so> \| grep getenv`；`llvm-nm -D --defined-only <so> \| grep -c boltffi_function` | 4/4 ABI：字符串各 `1`、`getenv@LIBC` 各 1、导出符号各 **47** | §2.2、§3.6、§七.17 |
| E11 | 定稿轮实跑：`llvm-readelf -lW <so> \| awk '/^  LOAD/{print $NF}'`（NDK 27.3.13750724 的 llvm-readelf） | `/tmp/helpernext-workflow-pack/repo`（未加参数）arm64 = `0x1000`；`/tmp/helpernext_pack_rehearsal/repo`（加了 `-Wl,-z,max-page-size=16384`）4/4 ABI = `0x4000` | §3.5、§5.2 A5 |
| E12 | 定稿轮实跑：`llvm-readelf -SW <so>` 累加 `.debug_*`+`.symtab`+`.strtab`+`.shstrtab` | 4 ABI：67.9% / 74.8% / 59.2% / 63.3%，合计 **26,337,038 / 39,893,388 = 66.0%** | §3.5 |
| E13 | 定稿轮实跑：`stat -f %z <4 个 release .so>`；`find dist/android -type f` | 8,662,608 + 9,912,900 + 10,720,072 + 10,597,808 = **39,893,388 B ≈ 38.05 MiB**；`dist/android` 共 **8 个文件、0 个可执行** | §3.5、§5.2 A1 |
| E14 | 定稿轮实跑：`shasum -a 256 LICENSE`（`/tmp/helpernext_probe` 与 `/tmp/helpernext_pack_rehearsal/repo`） | 两份均为 `c53a65c2fd561c87eaabf1072ef5dcab8653042bc15308465f52413585eb6271`；`head -3` = GPLv3 全文 | §2.3 |
| E15 | 定稿轮实跑：`jq -r 'keys\|join(",")' /tmp/helpernext-workflow-cred/Credential/qqmusic-credential.json` | `musicid,musickey,str_musicid`（**无 encrypt_uin**） | §4.2 网页登录行 |
| E16 | 定稿轮实跑：解析 `/tmp/helpernext-workflow-cred/probe-output.json` | `readOnly:true, calls:17, ok:17, failed:0`；逐行明细见 §3.4 表 | §3.4、§4.4 |
| E17 | 定稿轮实跑：`find /tmp/helpernext-research/kotlin-out -name '*.class' \| wc -l` | `88` | §3.3 |
| E18 | 定稿轮实跑：`grep -rn 'arg("--lib")' ~/.cargo/registry/src/*/boltffi_cli-0.31.0/src/build.rs` | `build.rs:306: command.arg("--lib");`（路径在 boltffi CLI crate，不是组件仓库） | §5.2 A1 |
| E19 | 定稿轮实跑：`sed -n '569,580p' src/methods.rs`（`aria2_add` 校验）；`sed -n '33,36p' <工程>data/Downloader.kt` | 组件：`out.trim().is_empty() \|\| out.contains('/')` → 报错「out 必须是文件名」；工程：`displayName = "${track.singer} - ${track.name}${quality.ext}"` 去非法字符 | §4.1 已下载台账行 |
| E20 | 定稿轮实跑：`sed -n '1272,1298p' src/catalog.rs`（`set_liked` 的 mid→数字 id） | `song_id <= 0` 且给了 `songMid` → 先调一次 `song_detail(…)` 取 `songId` | §4.2 喜欢写入行 |
| E21 | 定稿轮实跑：读 `src/guard.rs:186-190` | `:189 std::thread::sleep(duration.min(Duration::from_secs(2)))` | §5.2 A6 |
| E22 | 定稿轮实跑：`grep -n "meta.sum" src/catalog.rs` | `:46` 与 `:1360` 两处注释："every other profile answers `meta.sum = 0`" / "answers `meta.sum = 0` for everything without them" | §4.2 播放条歌手行 |
| E23 | 定稿轮实跑：`sed -n '1064,1070p' src/catalog.rs` | `None => format!("{prefix}{song_mid}{song_mid}{extension}")` | §4.2 曲目字段行 |
| E24 | 定稿轮实跑：`grep -n "loadLibrary" <生成物 QqmusicApiHelperNext.kt>` | `:641-651` 的 `Native` object：Android 运行时 `System.loadLibrary("qqmusic_api_helper_next")` | §6.5 |
| E25 | 定稿轮实跑：`sed -n '3225p' <生成物 QqmusicApiHelperNext.kt>` | `fun callWithPlatform(method: String, paramsJson: String, platform: String): String` | §4.4 |
| E26 | 定稿轮实跑：`sed -n '378,394p' src/api.rs` | `#[export] pub fn call_with_platform(method, params_json, platform) -> Result<String, HelperError>`；`params_json` 非对象时报 `params 必须是 JSON 对象` | §4.4 |
| E27 | 定稿轮实跑：`sed -n '49,55p' src/upstream.rs` | `"web" \| "yqq" \| "yqq.json" => Web`、`"android" => Android`，其余 `None` | §4.4 |
| E28 | 定稿轮实跑：`wc -l data/api/*.kt`（工程） | 合计 1547 行；逐文件见 §6.5 表 | §6.5 |
| E29 | 定稿轮实跑：`git diff --numstat b6c5c29 HEAD -- app/src/main` | `PlayerScreen.kt` **80+/63−**、`VinylTurntable.kt` **760+/0**（合计 840 insertions / 63 deletions）；工作区相对 HEAD 干净 | 表头工程行号规则、附录 C |
| E30 | 引自 `verify-mechanics.md` §2：两树 `diff boltffi.toml` | 唯一差异 `- extra_args = []` / `+ extra_args = ["-Wl,-z,max-page-size=16384"]` | §3.5 |
| E31 | 引自 `verify-mechanics.md` §1.4/§1.5：ctypes 调全部 47 个 `boltffi_function_*`（macOS dylib） | 11 个命令面方法 → `Upstream("不支持的方法：…")`；`song_detail`→`missing field songMid`；`album_detail`/`fetch_artist_biography`→`<ok>` + 全零 payload | §2.2、§5.2 A3 |
| E32 | 引自 `verify-mechanics.md` §0：`sha256 LICENSE` + `grep -rln "Copyright\|SPDX" src/` | 两份 LICENSE 一致；组件自带源码**无版权/SPDX 头** | §2.3 |
| E33 | 引自 `verify-playback.md` id 7：编译 Android SDK `android/util/Base64.java` 后实调 `Base64.decode(s, Base64.DEFAULT)` | 含 `[kana:…]` 的样本多数**不抛异常**而返回乱码（`getOrElse` 不触发） | §4.2 注音行（**经独立复核修正**） |
| E34 | 引自 `helpernext-mechanics.md` §5.3 / `verify-mechanics.md` §5b：`llvm-readelf -dW`、解 `.note.android.ident` | `.so` 只依赖 libm/liblog/libdl/libc；api_level=24（4/4 ABI） | §2.2、§5.2 A7 |
| E35 | **本次未执行**：`cargo test`、`boltffi pack android`、K2JVMCompiler、真实账号探针、`.so` 在 Android 设备上加载 | —（见 §七） | §3.2-3.5 的「工作流执行记录」标注即由此而来；这些**不作为本报告作者本轮亲自通过的检查** |
