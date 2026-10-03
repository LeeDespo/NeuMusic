# QQMusicApi_HelperNext 组件落地机制调查

调查对象：

- 源码 `/tmp/helpernext_probe`（提交 `2ff7e71`，`git log --oneline -1` = `2ff7e71 feat: stop when the host stops, however it stopped`）
- 已打包产物 `/tmp/helpernext_pack_rehearsal/repo`（含 `dist/android` 与 `boltffi.toml`）
- 参考宿主 `/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/README.md`

两处均只读；本文件是唯一写入。未运行任何构建（无 `cargo`/`gradle`/`boltffi pack`），未连接 adb/模拟器，未改动本工程源码。

两棵树的源码是同一份，已用命令核对：

```
$ diff -r --brief /tmp/helpernext_probe/src /tmp/helpernext_pack_rehearsal/repo/src
(无输出，退出码 0)
$ md5 /tmp/helpernext_probe/src/methods.rs /tmp/helpernext_pack_rehearsal/repo/src/methods.rs
MD5 (...) = a70a11b69addf0cc6f3afc036743d60f   # 两者相同
$ md5 /tmp/helpernext_probe/src/api.rs /tmp/helpernext_pack_rehearsal/repo/src/api.rs
MD5 (...) = c3a22cff49ed5f3ca0105ff0c3c70abf   # 两者相同
```

两树唯一差异是 `boltffi.toml` 的一行（见第 5 节）。

---

## 0. 摘要：最要紧的三件事

1. **不存在 `configure` 的 FFI 入口。** 生成的 Kotlin 绑定里没有任何设置数据目录的函数（47 个 `fun` 中无 `configure`/`dataDir` 相关）。`data_directory()` 在宿主未配置时回退到 `QQMUSIC_HELPER_NEXT_DIR` 环境变量，再回退到相对路径 `"."`（`src/lib.rs:95-107`）。Android 宿主若不做处理，凭据与 `device.json` 会落到**进程当前工作目录**——在 Android 上 `"."` 通常是 `/`（不可写），写入会失败。**这是本调查最重的一条**，详见第 1 节。
2. **47 个导出函数在 Kotlin 里一一对应、顺序一致、没有跳过**，但其中 **11 个在 FFI 路径上是死的**：`set_rate_limit` / `set_breaker` / `aria2_*` 九个 走 `api.rs::call()` → `methods::dispatch()`，而 `dispatch` 没有这些分支，只会返回 `Upstream("不支持的方法：…")`。这些方法只在**子进程 stdio 适配器**里实现（`src/bin/stdio.rs:146-190`）。已用原生 FFI 实测复现（第 1、3、4 节）。
3. **另有一批导出把 JSON 包在具名 key 里，而 `api.rs::parse()` 不认识这些 key**，于是解析失败：`guard_status`（包在 `status`）、`toplist_categories`（`toplistGroups`）、`radio_stations`（`radioGroups`）、`recommend_feed`/`new_songs`/`artist_songs`/`artist_albums`/`radio_tracks`（`tracks`/`albums`）、`start_login`（`qrcode`）、`song_detail`/`artist_detail`（`detail`）、`search_*_artwork`（`candidates`）、`lyric`（`lyric`+`wordLyric`）。相对地 `album_detail`（`detail`）与 `fetch_artist_biography`（`artistDetail`）**静默解析成全空对象**而不是报错。已实测复现（第 1.5 节）。

---

## 1. 绑定面

### 1.1 导出数量：47

```
$ grep -c "#\[export\]" /tmp/helpernext_probe/src/api.rs
47
$ grep -c "^pub fn" /tmp/helpernext_probe/src/api.rs
47
$ grep -rn "#\[export" /tmp/helpernext_probe/src/
（全部 47 处都在 src/api.rs；其它模块 0 处）
```

`#[export]` 只出现在 `src/api.rs`（`src/api.rs:57` … `src/api.rs:590`）。

### 1.2 Kotlin 绑定一一对应，没有被跳过的

生成物 `/tmp/helpernext_pack_rehearsal/repo/dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt`（3557 行，包名 `com.example.qqmusic_api_helper_next`）。

三方计数完全一致：

```
$ grep -c "^fun " .../QqmusicApiHelperNext.kt
47
$ grep -c "^pub fn" /tmp/helpernext_probe/src/api.rs
47
$ grep -c "external fun boltffi_function" .../QqmusicApiHelperNext.kt
47
$ grep -c "boltffi_function_" .../dist/android/include/qqmusic_api_helper_next.h
47
$ grep -c "^JNIEXPORT" .../dist/android/kotlin/jni/jni_glue.c
47
```

名字映射（snake→camel）与顺序逐一核对，用脚本归一化后取差集：

```
rust exports: 47 unique 47
kotlin top-level funs: 47 unique 47
in rust not kotlin: []
in kotlin not rust: []
same order: True
```

所以**没有任何函数被绑定生成器跳过**。这一点值得强调，因为 `docs/ffi.md` 记录了生成器的已知缺陷（`Vec<(String,String)>` 这种元组向量会让 Swift 目标直接失败、Kotlin 目标静默跳过并打印 `multi-statement wire writer`）。该缺陷的规避方式已经写进源码：`import_credential(uin, qm_keyst)` 收两个字段而不是 cookie 表（`src/api.rs:62-78` 的文档注释原话：「A `Vec<(String, String)>` also happens to be the one shape the binding generator cannot render, so the two-field form is what hosts on both platforms actually get.」）。

> 注：`dist/android/kotlin/` 下**没有** `skipped` 报告文件；`target/boltffi-metadata/` 下也没有（只有 cargo 的 fingerprint）。所以「没被跳过」的判据是上面四方计数一致，而不是生成器的自我报告。

### 1.3 没有 configure 入口，以及它对 Android 宿主意味着什么

**FFI 面里没有 `configure`。** 三条独立证据：

```
$ grep -in "configure\|data_dir\|datadir" /tmp/helpernext_pack_rehearsal/repo/dist/android/include/qqmusic_api_helper_next.h
193:FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_configure(...)   # 唯一的 configure，是 aria2 的
（共 1 条命中，且是 aria2 的引擎参数）

$ grep -in "configure\|dataDir\|Configuration" .../QqmusicApiHelperNext.kt
（仅命中 aria2Configure 与它的 JNI stub，共 2 条；无 data-dir 相关）

$ llvm-nm -D --defined-only .../arm64-v8a/libqqmusic_api_helper_next.so | grep -i "configure\|data_dir"
Java_..._aria2_1configure
boltffi_function_qqmusic_api_helper_next_api_aria2_configure
（只有 aria2 的）
```

对照 `src/api.rs:484-516` 虽然定义了 `set_rate_limit` / `set_breaker`，那也只是「设置推送」而非数据目录。真正的目录配置函数 `configure()` 在 `src/lib.rs:88-90`，它**没有 `#[export]`**，因此不进 FFI 面；它只被两处调用：`src/bin/stdio.rs:48`（子进程适配器自己调）和 `src/lib.rs:158`（单测）。

**回退链**（`src/lib.rs:95-107`，原文）：

```rust
95  pub fn data_directory() -> PathBuf {
96      if let Some(configuration) = CONFIGURATION.get() {
97          if !configuration.data_dir.is_empty() {
98              return PathBuf::from(&configuration.data_dir);
99          }
100     }
101     if let Ok(explicit) = std::env::var("QQMUSIC_HELPER_NEXT_DIR") {
102         if !explicit.is_empty() {
103             return PathBuf::from(explicit);
104         }
105     }
106     PathBuf::from(".")
107 }
```

三级顺序：① `configure()` 设过的（FFI 宿主设不到）→ ② `QQMUSIC_HELPER_NEXT_DIR` → ③ **相对路径 `"."`**。

**这对 Android 宿主意味着什么**（实测过第 ③ 级的真实行为，用未设环境变量的 dylib 调 `import_credential`）：

```
cwd was: /private/tmp/hn_datadir_test
=== files created in cwd ===
./Credential
./Credential/qqmusic-credential.json
```

即：**数据目录 = 调用线程所在进程的当前工作目录**。组件不报错、不提示、静默落盘。

Android 上的后果分三种情况：

- 宿主**不处理**：`"."` 在 Android 应用进程里通常是 `/`（app 进程的 cwd 由 zygote 继承，`/` 不可写），`Credential/` 建不出来 → `store()` 返回 `InvalidRequest("…No such file or directory")` → 登录凭据永远存不下，而 `get_login_status` 会一直返回 `loggedIn: false`。**表现为「登录了但没登录」。**
- 宿主**在任意一个调用之前**先设好 `QQMUSIC_HELPER_NEXT_DIR`：可行（第 ② 级）。Android 上可用 `android.system.Os.setenv("QQMUSIC_HELPER_NEXT_DIR", dir, true)`——已确认它是公开 API：

  ```
  $ $JAVA_HOME/bin/javap -classpath ~/Library/Android/sdk/platforms/android-36/android.jar android.system.Os | grep -E "setenv|getenv"
    public static java.lang.String getenv(java.lang.String);
    public static void setenv(java.lang.String, java.lang.String, boolean) throws android.system.ErrnoException;
    public static void unsetenv(java.lang.String) throws android.system.ErrnoException;
  ```

- 宿主**改 cwd**：技术上也能成（`"."` 随即指向该目录），但进程级 cwd 是全局副作用，通常不可取。

**求值时机有两套，必须分清**（这是个容易踩的坑）：

- **凭据目录是每次调用重新求值的。** `store()`（`src/api.rs:24-26`）每次调用都 `CredentialStore::for_directory(&data_directory())`。`.so` 里的 `data_directory` 只有一个 `OnceLock<Configuration>` 且永不写入（无 FFI 入口），所以实际效果是：**每次调 `api.rs` 的函数都重读一次 `QQMUSIC_HELPER_NEXT_DIR`**。
- **设备身份与 aria2 的目录是首次构造时冻结的。** `Upstream::new()` → `with_device(DeviceStore::for_directory(&crate::data_directory()))`（`src/upstream.rs:118-130`），而 `Upstream` 是 `static INSTANCE: OnceLock<Upstream>`（`src/api.rs:19-22`）——第一次调用组件时建一次，之后 `device.json` 与 `<dir>/aria2-next` 的路径就固定了（`src/upstream.rs:135` 的 `Aria2::new(crate::data_directory())` 同样只跑一次）。

**实践含义：必须在「第一次调用组件之前」就把 `QQMUSIC_HELPER_NEXT_DIR` 设好**。若设晚了，会出现凭据落到新目录、而 `device.json` 留在旧目录（`"."`，即那次进程的 cwd）的**劈叉状态**——组件不会报错，但 `device.json` 那条路径写不进去时设备身份就不会持久化（每次重启换一套伪装身份，见上）。

已确认 `.so` 真的会读该环境变量（不是死代码）：

```
$ llvm-nm -D --undefined-only .../libqqmusic_api_helper_next.so | grep -i getenv
                 U getenv@LIBC
$ strings -a .../libqqmusic_api_helper_next.so | grep -c "QQMUSIC_HELPER_NEXT_DIR"
1
```

同样的回退链还影响另外两个落盘文件（都由 `data_directory()` 派生）：

| 文件 | 位置 | 出处 |
|---|---|---|
| 凭据 | `<dir>/Credential/qqmusic-credential.json` | `src/credential.rs:120-123` |
| 设备身份 | `<dir>/device.json` | `src/device.rs:159-163` |
| aria2 二进制（期望） | `<dir>/aria2-next` | `src/aria2.rs:154-156` |
| aria2 下载目录 | `<dir>/Downloads/` | `src/aria2.rs:212-214` |

`device.json` 若不落地，每次进程启动都会重新生成一套伪装设备身份（`src/device.rs:169-177` 的 `load_or_create` 读不到就 `Device::default()` 再存），这对上游来说看起来像「一 fleet 新设备」——`src/device.rs:12-13` 的注释原话就是「a fresh one on every launch would look like a fleet of new devices rather than one user」。

### 1.4 附加发现：11 个导出在 FFI 路径上是死的

> 这一条不在原始问题清单里，但它是「组件能不能落地」的直接障碍，必须记录。

47 个导出分两类：

- 走 `src/api.rs:29-36` 的 `call()` → `methods::dispatch()`。
- `dispatch()`（`src/methods.rs:394-460`）先转 `catalog_dispatch()`（`src/methods.rs:92-363`），再自己 match 一把。

把两个函数的全部 match 臂逐行导出（按 brace 匹配取行号区间，而非正则猜），覆盖集是：

```
catalog_dispatch lines 92..363
dispatch          lines 394..460
handled by dispatch: 35
METHODS not reachable from methods::dispatch (11):
  set_rate_limit, set_breaker, aria2_status, aria2_restart, aria2_configure,
  aria2_add, aria2_tell, aria2_list, aria2_pause, aria2_unpause, aria2_cancel
```

这 11 个恰好与 `api.rs` 里对应的 11 个 `#[export]` 重合。它们的真实实现在 `src/methods.rs:468-613`（`configure_rate_limit` / `configure_breaker` / `aria2_status` / …），但 **`dispatch` 不调用它们**；只有 `src/bin/stdio.rs:146-190` 会按方法名前缀把它们分流过去。

**实测复现**（不是读代码推断）。用一个能加载的本机构建产物直接调原生 FFI：

```
$ python3 -c '...'   # ctypes.CDLL("/tmp/helpernext_probe/target/debug/deps/libqqmusic_api_helper_next.dylib")
调用 47 个 boltffi_function_qqmusic_api_helper_next_api_* 符号，读回 FfiBuf_u8 返回值
```

结果（错误 buffer 是 wire 格式 `4 字节 tag + 4 字节 len + utf8`，tag 2 = `HelperError::Upstream`，见 `src/lib.rs:114-125` 与 Kotlin 侧 `QqmusicApiHelperNext.kt:2880-2892` 的 tag 映射）：

```
set_rate_limit           Upstream(不支持的方法：set_rate_limit)  payload_len=0
set_breaker              Upstream(不支持的方法：set_breaker)     payload_len=0
aria2_status             Upstream(不支持的方法：aria2_status)    payload_len=0
aria2_restart            Upstream(不支持的方法：aria2_restart)   payload_len=0
aria2_configure          Upstream(不支持的方法：aria2_configure) payload_len=0
aria2_list               Upstream(不支持的方法：aria2_list)      payload_len=0
aria2_add                Upstream(不支持的方法：aria2_add)       payload_len=0
aria2_tell               Upstream(不支持的方法：aria2_tell)      payload_len=0
aria2_pause / unpause / cancel：
   传 Some(gid) → 返回 ptr=NULL/len=0，payload 也为空（32 字节的 Option 载荷被当成别的 ABI 解释，无有效结果）
   传 None      → Upstream(不支持的方法：aria2_pause / aria2_unpause / aria2_cancel)
```

对照：**同一份实现，子进程路径是好的**。用 macOS 那份预编译二进制跑同样的方法：

```
$ printf '%s\n%s\n%s\n%s\n' \
   '{"id":"1","method":"aria2_status","params":{"ensure":false}}' \
   '{"id":"2","method":"set_rate_limit","params":{"enabled":true,"windowSeconds":30,"maxRequests":50}}' \
   '{"id":"3","method":"get_status","params":{}}' \
   '{"id":"4","method":"set_breaker","params":{"enabled":true,"failureThreshold":3,"failureWindowSeconds":120,"openSeconds":300}}' \
 | QQMUSIC_HELPER_NEXT_DIR=$SCRATCH "/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/qqmusic-helper-next"

{"breaker":{"enabled":true,"failureThreshold":3,"failureWindowSeconds":120,"openSeconds":300},"id":"4","ok":true}
{"id":"2","ok":true,"rateLimit":{"enabled":true,"maxRequests":50,"windowSeconds":30}}
{"id":"3","ok":true,"status":{"breaker":"closed","breakerConfig":{...},"rateLimit":{...}}}
{"aria2":{"binary":"/tmp/hn_stdio_51686/aria2-next","installed":false,"running":false,"options":{...}},"id":"1","ok":true}
```

（这条命令我用的是 `/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/qqmusic-helper-next`，它是在场产物里唯一能跑的组件二进制。它证明**协议层实现是完整的**，缺的只是 FFI 路径的分流。）

`api.rs` 里的 `api_surface_matches` 测试（`src/api.rs:150-215`）只断言「每个协议方法在 api.rs 里有一个 `pub fn` 同名 wrapper」——它检查的是**源码文本里有没有这个函数名**，不检查该函数是否可达。所以这个测试全绿，但 11 个 wrapper 依然是死的。

### 1.5 附加发现：`parse()` 的 wrapper key 覆盖不全

`api.rs::call()` 在把 JSON 反序列化成模型前，先经 `parse()`（`src/api.rs:41-53`）。`parse()` 的已识别 key 是：

```
$ sed -n '41,53p' src/api.rs
"fetch_liked_songs" => value.get("likedSongs")…
"fetch_login_status" | "get_login_status" => value.get("login")…
"get_helper_info" => value.get("helper")…
"fetch_user_playlists" => value.get("playlists")…
"fetch_liked_albums" => value.get("albums")…
"fetch_followed_artists" => value.get("artists")…
"fetch_playlist_tracks" => value.get("tracks")…
_ => value,          # ← 其余一律原样
```

而 dispatch 一侧包出来的 key 有 17 个（逐行扫 `\.map\(\|[a-z_]+\|\s*json!\(\{\s*"(\w+)"`）：

| 方法 | dispatch 包成的 key | `parse()` 认识吗 | 实测结果（正确 wire 编码下复测） |
|---|---|---|---|
| `fetch_song_detail` | `detail` | ✗ | `Upstream(missing field 'songMid')` |
| `fetch_album_detail` | `detail` | ✗ | **`<ok>` 但 payload 只有 11 字节全零**（`[0]*11`）= 每个字段都 null 的空 `AlbumDetail` |
| `fetch_artist_songs` | `tracks` | ✗ | `Upstream(invalid type: map, expected a sequence)` |
| `fetch_artist_albums` | `albums` | ✗ | 同族 |
| `fetch_artist_detail` | `detail` | ✗ | `Upstream(missing field 'singerMid')` |
| `fetch_toplist_categories` | `toplistGroups` | ✗ | `Upstream(invalid type: map, expected a sequence)` |
| `fetch_radio_stations` | `radioGroups` | ✗ | 同族 |
| `fetch_radio_tracks` | `tracks` | ✗ | 同族 |
| `fetch_new_songs` | `tracks` | ✗ | `Upstream(invalid type: map, expected a sequence)` |
| `fetch_recommend_feed` | `tracks` | ✗ | 同族 |
| `search_track_artwork` 等三个 | `candidates` | ✗ | 同族 |
| `fetch_artist_biography` | `artistDetail` | ✗ | **`<ok>` 但 payload 只有 7 字节全零** = 空 `ArtistBiography` |
| `fetch_lyric` | `lyric` + `wordLyric` 两个 key | ✗ | `Upstream(invalid type: map, expected a string)` |
| `poll_login` | `login` | **✓** | — |
| `set_liked` | `like` | ✗ | 解析成 `Value`，不校验（无害） |
| `resolve_song_url` | `stream` | ✗ | **未测到**（该函数先撞上登录门，见下） |

实测输出（同一批原生 FFI 调用，输入用与生成的 Kotlin 完全相同的 wire 编码）：

```
song_detail            -> Upstream(missing field `songMid`)
artist_detail          -> Upstream(missing field `singerMid`)
toplist_categories     -> Upstream(invalid type: map, expected a sequence)
radio_stations         -> Upstream(invalid type: map, expected a sequence)
recommend_feed         -> Upstream(invalid type: map, expected a sequence)
new_songs              -> Upstream(invalid type: map, expected a sequence)
search_artist_artwork  -> Upstream(invalid type: map, expected a sequence)
lyric                  -> Upstream(invalid type: map, expected a string)
start_login            -> Upstream(missing field `identifier`)
album_detail           -> <ok>   payload = 11 字节全零   ← 静默空
fetch_artist_biography -> <ok>   payload = 7 字节全零    ← 静默空
```

「静默空 vs 报错」的分界是**模型的必填字段**：

- `SongDetail.song_mid: String`、`ArtistDetail.singer_mid: String`、`LoginQrCode.identifier: String`、`TrackPage.tracks: Vec<Track>`、`Vec<...>` 这类**必填** → 拿到一个 map（JSON 对象）而期望别的类型 → 报 `missing field` / `invalid type: map, expected a sequence`。
- `AlbumDetail`、`ArtistBiography`、`ArtworkCandidate` 的字段**全是 `Option`** → map 反序列化成功但一个字段都对不上 → 得到一个**全 null 的合法值**（payload 就是 11 / 7 字节的零）。

> **一处需要更正的早期误判**：我最初把 `resolve_song_url` 也归入「静默全空」，那是**错的**。复测（正确编码）得到的是 `Upstream(需要登录后才能读取)`——它先撞上了登录门（`src/catalog.rs:1050` 的 `require_login(credential)?`），根本没走到解析那一步。所以 `resolve_song_url` 的 wrapper-key 问题**在这个无凭据的测试环境里测不到**，我把它从「已实测」下调为「未测到（被登录门遮挡）」。
>
> 顺带，这也给出一份「哪些方法需要登录」的精确清单（`require_login` 的全部调用点是 `src/catalog.rs:1050` (`stream_url`)、`src/catalog.rs:1271` (`set_liked`)、`src/methods.rs:666/706/749/781/809`）：
>
> **需要登录**：`resolve_song_url`、`set_liked`、`liked_songs`、`playlist_tracks`、`user_playlists`、`liked_albums`、`followed_artists`（后六个即 `methods.rs` 的几个 `fetch_*`）。
> **不需要登录**：`search_*`、`*_artwork`、`song_detail`/`album_detail`/`artist_detail`/`artist_biography`、`toplist_*`、`radio_*`、`new_songs`、`recommend_feed`、`lyric`、`start_login`/`poll_login`。
>
> 这也是「支持未登录试听/浏览」的边界：上面那 7 个方法在无凭据时**必然**返回 `HelperError::NotLoggedIn`（tag 0），而不是空结果。

### 1.6 `export_tokens` 相关：`helper.get_helper_info` 说 `credentialDir: true`

`src/methods.rs:406-414` 返回的 `get_helper_info` 里有一个字段叫 `credentialDir`，值是布尔 `true`。注意它**不是目录路径**——`ComponentInfo` 模型（`src/models.rs:234-244`）里根本没有这个字段，所以它到不了 Kotlin 侧（Kotlin `ComponentInfo` 只有 `helperVersion/protocolVersion/libraryVersion/methods`，`QqmusicApiHelperNext.kt:1707-1716`）。它是给子进程路径的 JSON 消费者看的占位布尔，值为恒 `true`。别把它当成「目录已配置」的判据。

---

## 2. 凭据

### 2.1 存/读位置与格式

- 路径：`<data_dir>/Credential/qqmusic-credential.json`（`src/credential.rs:120-123`）
- 读：`load()`（`src/credential.rs:130-143`）→ 解析 JSON → `Credential::from_value()` → 若 `str_musicid`/`musicid` 与 `musickey` 都非空则可用（`is_usable`，`src/credential.rs:47-49`），否则返回 `None`（→ 上层报 `NotLoggedIn`）。
- 写：`store()`（`src/credential.rs:145-164`）——**先读旧文件、合并、只覆盖自己拥有的 key**，再 `tmp + rename` 原子落盘（`src/credential.rs:161-163`）。保留的 key 包括 `refresh_token` 之类组件不读的字段（`src/credential.rs:150-160` 与单测 `str_musicid_wins_over_the_numeric_one` 断言 `refresh_token` 幸存）。
- 格式：扁平 JSON 对象，字段名沿用上游自己的拼写。写入时固定插四个键：

  | key | 来源 |
  |---|---|
  | `musicid` | `Credential.music_id` |
  | `str_musicid` | 同上（同一个值写两份） |
  | `musickey` | `Credential.music_key` |
  | `encrypt_uin` | 仅当非空时写 |

  读取时 `str_musicid` **优先于** `musicid`（`src/credential.rs:78-90`），`encrypt_uin` 与 `encryptUin` 两种拼写都认（`src/credential.rs:100-108`）。

  实际磁盘上的样子（只读了目录清单与文件大小，未读内容）：`/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/Credential/qqmusic-credential.json`，179 字节。

- 凭据**从不经过 JSON 回复**：`src/methods.rs:446-448` 的 `import_cookies` 分支直接返回错误，注释原话「the component never puts a credential in a JSON reply」；`src/bin/stdio.rs:133-134` 同款注释。这是有意的防泄漏设计。

### 2.2 `import_cookies` 的参数

协议层（子进程路径）收 `{"cookies": {...}}`（`src/methods.rs:615-623`）：

```rust
pub fn credential_from_params(params: &Value) -> Result<Credential, UpstreamError> {
    let cookies = params.get("cookies").ok_or_else(|| UpstreamError::Upstream("缺少 cookies".into()))?;
    credential_from_cookies(cookies)
        .ok_or_else(|| UpstreamError::Upstream("cookie 里没有 qm_keyst 或 uin，无法登录".into()))
}
```

`credential_from_cookies`（`src/credential.rs:180-197`）的取值规则：

| 字段 | 接受的名字（按序） | 必需 |
|---|---|---|
| 会话票据 | `qm_keyst` | **必需**（缺则整体返回 `None`） |
| 账号 | `uin` → `qqmusic_uin` → `musicid` | **必需**（缺则 `None`） |
| `encrypt_uin` | `encrypt_uin` | 可选 |

注意 `qqmusic_key` 这个 cookie 名**不被读取**（它只出现在 `cookie_header()` 的**输出**里，`src/credential.rs:57-65`）。子进程路径写回一个 `{"login":{"loggedIn":true}}`（`src/bin/stdio.rs:135-145`）。

**FFI 路径是另一个签名**：`importCredential(uin: String, qmKeyst: String)`（`QqmusicApiHelperNext.kt:2916`），对应 `src/api.rs:70`。它绕开 `credential_from_params`，直接构造：

```rust
71  let credential: Credential = methods::credential_from_params(&json!({
72      "cookies": { "uin": uin, "qm_keyst": qm_keyst }
73  }))
```

即**两条路径最终都走同一个取值函数**，只是 FFI 只暴露 `uin` 与 `qm_keyst` 两个位置参数。这一点很关键：`encrypt_uin` 在 FFI 路径上**无法传入**，但组件不需要它——关注歌手接口在缺失时会退回数字 uin（见 2.4）。

`uin` 是否要剥掉网页版的前缀 `o`（`o0123456789`）？**组件没做这件事**——`credential.rs` 全文没有 `trim_start_matches`/`strip_prefix`，`music_id` 原样存原样用（`src/credential.rs:189-193`）。所以**去 `o` 前缀的责任在宿主**。本工程 AGENTS.md 记录的正是宿主侧做法：「读 cookie 的 `uin`（去 `o` 前缀）」。这是一条**必须由 Android 宿主自己做**的事，组件不会兜底。

### 2.3 扫码登录产出什么

`start_login`（`src/login.rs:92-120`）返回 `LoginQrCode`（`src/models.rs:415-428`）：

| 字段 | 内容 |
|---|---|
| `identifier` | `ptqrshow` 响应里的 `qrsig` cookie（`src/login.rs:106-109`），回传给 `poll_login` |
| `loginType` | 固定 `"qq"`（`src/methods.rs:326`） |
| `mimetype` | 固定 `"image/png"`（`src/login.rs:118`） |
| `imageBase64` | PNG 字节的 **base64**（`src/login.rs:119`，`base64::engine::general_purpose::STANDARD`）——宿主可直接塞进 `<img>`/`BitmapFactory` |

`poll_login(identifier)`（`src/login.rs:124-171`）返回 `LoginPoll`（`src/models.rs:429-441`）：

| 字段 | 内容 |
|---|---|
| `event` | `"SCAN"` / `"CONF"` / `"DONE"` / `"TIMEOUT"` / `"REFUSE"`（`src/login.rs:141-146` 的 `as_str`） |
| `loggedIn` | 仅 `DONE` 时为 `true` |
| `login` | `DONE` 时为 `LoginStatus`（昵称/是否 VIP/是否有播放票据） |

事件码映射（`src/login.rs:64-73`）：`0|405→DONE`、`66|408→SCAN`、`67|404→CONF`、`65|402→TIMEOUT`、`68|403→REFUSE`。

**关键：扫码流程在 `DONE` 时自己落盘，不需要宿主再调 `import_cookies`。** `poll_login` → `exchange_for_credential()`（`src/login.rs:173-256`）走完五步握手（`check_sig` → `authorize` → `QQConnectLogin.LoginServer/QQLogin`），最后 `store.store(&credential)`（`src/login.rs:254-255`）。所以宿主拿到 `event == "DONE"` 时凭据已经在 `<data_dir>/Credential/qqmusic-credential.json` 里了。

**QR 流程是无状态的**：`qrsig` 由调用方持有、每次 poll 回传，组件内部不留登录态（`src/login.rs:19-21` 的设计说明）。所以「换 Activity / 进程被杀」不影响 —— 只要宿主自己存着 `identifier`。

### 2.4 宿主把已有 uin + qm_keyst 交给组件的最短路径

**最短路径 = 一次 `importCredential(uin, qmKeyst)` 调用**，之后组件自给自足：

1. **先把数据目录解决掉**（否则落盘会失败，见 1.3）。FFI 没有 configure，所以在**第一次调用组件之前**执行一次：
   ```kotlin
   android.system.Os.setenv("QQMUSIC_HELPER_NEXT_DIR", context.filesDir.absolutePath, true)
   ```
   必须在加载/调用之前，且建议放在 `Application.onCreate`。之后 `Os.setenv(..., true)` 允许覆盖，所以即使调用过也能纠正。
2. `importCredential(uin = <去掉 "o" 前缀的数字账号>, qmKeyst = <qm_keyst cookie>)`（`QqmusicApiHelperNext.kt:2916` / `src/api.rs:70`）。**不要**在后台线程之外调用——所有导出都是同步阻塞的（见第 6 节）。
3. 校验：`loginStatus()`（`src/api.rs:58`）→ `loggedIn`。注意它是**真·上游往返**（`GetLoginUserInfo`），票据过期会如实返回 `loggedIn: false`（`src/methods.rs:625-660` 的注释「an expired session is visible as such」）。
4. 若上游不返回昵称等，不影响播放。`encrypt_uin` 缺失也无妨：关注歌手接口会退回数字 uin（`src/methods.rs:810-818` 的注释与 `upstream.encrypted_uin(credential).unwrap_or_else(|_| credential.music_id.clone())`），实测注释说「verified live: same rows, same account」。

如果宿主**没有** cookie（例如不想内置 WebView），那就反过来走 QR：`startLogin()` 画二维码 → 循环 `pollLogin(identifier)` 直到 `event == DONE`，凭据自动落盘。`docs/endpoints.md:33` 也确认这两条是仅有的入口（「微信扫码登录未实现」）。

---

## 3. 限流与熔断

### 3.1 语义

两者都在 `src/guard.rs`。**都是「延迟」而非「丢弃」**（`src/guard.rs:11-18` 的设计说明）。

**限流 `RateLimit`** —— 两层：

1. **按类别的滑动窗口**（`Class`，`src/guard.rs:36-58`）：每类一个 `budget`，窗口固定 10 秒（`src/upstream.rs:132` 的 `RateLimit::new(Duration::from_secs(10))`）。
   | 类别 | 每 10 秒预算 |
   |---|---|
   | `Read`（目录读） | 30 |
   | `Interactive`（搜索/电台） | 12 |
   | `Playback`（取流/歌词） | 20 |
   | `Account`（我喜欢/歌单） | 12 |
   | `Write`（写） | 6 |
2. **全局总闸**（用户可推的 `RateLimitConfig`）：默认 `enabled: true, window: 10s, max_calls: 100`（`src/guard.rs:73-81`）。**默认是开着的**（不是「关着=只有分类预算」——注释说 off 才是那样）。

`acquire()`（`src/guard.rs:117-176`）的等待算法：在锁内算出 `max(类别等待, 全局等待)`，**在锁外 sleep**（避免把并发调用者串行化在睡眠者后面），然后重新检查。单次 sleep 上限 2 秒以免长时间不可中断。

推送侧 `set_rate_limit(enabled, window_seconds, max_requests)`（`src/api.rs:484-497`）：`window_seconds` clamp 到 `[1, 3600]`、`max_requests` clamp 到 `[1, 100000]`（`src/methods.rs:468-493`）。**改配置会重置窗口**（`src/guard.rs:112-122`：`global.started = Instant::now(); global.calls = 0`），这是有意的——注释说「用户刚敲的数字必须现在就生效，而不是等旧窗口碰巧过期」。

**熔断 `CircuitBreaker`** —— 默认 `enabled: true, failure_threshold: 5, failure_window: 60s, open_for: 30s`（`src/guard.rs:222-231`）。三态：`Closed` / `Open{until}` / `HalfOpen`（`src/guard.rs:233-238`）。

- `check()`（`src/guard.rs:282-306`）：`Open` 且未到期 → 返回带剩余秒数的中文原因；到期 → 转 `HalfOpen` 并放行**一个**探测；`HalfOpen` 期间其余调用返回「正在探测上游是否恢复，请稍后再试」。
- `record_failure()`（`src/guard.rs:314-330`）：先按 `failure_window` 剔除旧失败时间戳，再计数；达阈值则开 `open_for`。
- `record_success()`：清失败计数、状态回 `Closed`。
- **推送配置会清空熔断状态**（`src/guard.rs:270-274`）：`configure()` 除了换配置，还把 `failures` 清空、状态强制回 `Closed`。注释理由是「调参是刻意行为，留着开着的新数字要等旧 open 期结束才生效，读起来像『设置没送达到』」。
- `set_breaker(enabled, failureThreshold, failureWindowSeconds, openSeconds)`：clamp 分别是 `[1,100]` / `[1,3600]` / `[1,3600]`（`src/methods.rs:508-536`）。
- `enabled: false` 的语义是**「不因失败开路，但失败照常报给调用方」**（`src/guard.rs:214-216`）。

**`get_status` / `guardStatus()` 回读**：`src/methods.rs:419-445` 组装 `status.breakerConfig` + `status.breaker` + `status.rateLimit.config` + 五类 `usage`。Kotlin 模型 `GuardStatus(breaker: String, rateLimit: RateLimitUsage)` 与 `RateLimitUsage(read/interactive/playback/account/write: UInt)`（`QqmusicApiHelperNext.kt:1760-1765`, `1803-1808`）。设计意图（`src/methods.rs:416-418` 注释）：「am I being throttled by my own limiter, or is the upstream refusing me」——宿主应展示**组件当前生效的值**，而不是用户输入的数字。

**但注意：`guardStatus()` 在 FFI 上是坏的**（1.5 节已实测）：它返回 `Upstream(missing field 'breaker')`，因为 `get_status` 包在 `status` 里而 `parse()` 不认这个 key。所以 Android 宿主**拿不到限流/熔断的回读**，只能显示自己推下去的值。

### 3.2 进程重启后宿主是否需要重推 —— 需要

**限流配置与熔断配置都是纯内存状态，没有任何持久化。**

```
$ grep -n "std::fs\|File::\|write\b\|path" /tmp/helpernext_probe/src/guard.rs
  1://! Politeness: a rate limiter and a circuit breaker, both on the request path.
（全文只有这一条命中，且是文档注释。没有任何文件 IO。）
```

`RateLimitConfig` / `BreakerConfig` 都只在 `Upstream` 的字段里（`src/upstream.rs:88-89`：`pub limiter: RateLimit` / `pub breaker: CircuitBreaker`），而 `Upstream` 是进程内单例（`src/api.rs:19-22` 的 `static INSTANCE: OnceLock<Upstream>`）。重启后重新构造，回到默认值（`RateLimit::new(10s)` + `RateLimitConfig::default()` = 10s/100 次；`CircuitBreaker::default()` = 5 次/60s/30s）。

**结论：宿主每次进程启动都要重推 `set_rate_limit` / `set_breaker`**，并把用户设置持久化在宿主自己那边（`Prefs` 之类）。macOS 宿主 README 也是这么写的（`:35`「每次组件重启后都会重推（组件是独立进程，退出即忘）」）。

**但在 Android 上这个「重推」目前推不动**——`setRateLimit` / `setBreaker` 在 FFI 路径返回「不支持的方法」（1.4 节实测）。要落地必须先修组件（把 11 个方法接进 `dispatch`），或者退而改走子进程路径（Android 上不现实，见第 5、6 节）。

顺带：熔断/限流的状态**跨调用是共享的**（`Upstream` 单例 + 内部 Mutex），所以宿主的多线程调用会共用一份预算——这是设计意图（`src/guard.rs:3-5`「this component is the only thing that talks to the upstream now, so it is the only place that can see *all* of the traffic」）。

---

## 4. 下载引擎（aria2-next）

### 4.1 放哪

**固定路径：`<data_dir>/aria2-next`**，即宿主传给组件的数据目录**旁边**（`src/aria2.rs:9` 的模块注释「The binary travels next to this component (`<dir>/aria2-next`, shipped in the same release package)」；实现 `src/aria2.rs:153-156`）：

```rust
153  /// The binary that travels with this component.
154  pub fn binary(&self) -> PathBuf {
155      self.directory.join("aria2-next")
156  }
```

`directory` 来自 `Aria2::new(crate::data_directory())`（`src/upstream.rs:135`）。**组件不去 PATH 找、不看环境变量、不检查多个候选**——只有一个路径。`binary_in()`（`src/aria2.rs:600-602`）是同一个 `join("aria2-next")` 的公开版本。

实测（子进程路径）报出来的就是那个路径：

```
{"aria2":{"binary":"/tmp/hn_stdio_51686/aria2-next","installed":false,"running":false,...}}
```

macOS 现场的目录结构可作对照：`qqmusic-helper-next` 与 `aria2-next` 并列（11.7 MB 的 `aria2-next` 就躺在 `/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/`）。

**下载的输出目录**：`<data_dir>/Downloads/`，启动时 `create_dir_all`（`src/aria2.rs:212-214`），并作为 `--dir=` 传给引擎（`src/aria2.rs:223`）。`aria2_add(url, out)` 的 `out` 是**该目录内的文件名**，组件要求它不含 `/`（`src/methods.rs:569-582` 的校验：`out.trim().is_empty() || out.contains('/')` → 「out 必须是文件名」）。

**按需启动**，不在进程启动时拉起（`src/aria2.rs:10-11`「It is started **on first use**, not at launch」）。`aria2_status(ensure=true)` 会顺手拉起，`ensure=false` 只报告（`src/methods.rs:541-547`）。

**引擎的进程行为**（`src/aria2.rs:216-235` 的 `Command` 参数，逐条）：

```
--no-conf
--enable-rpc
--rpc-listen-all=false                      # 只回环
--rpc-listen-port={port}                    # 默认 16800（不是 aria2 默认的 6800，src/aria2.rs:30）
--rpc-secret={secret}                       # 每次启动重新生成
--dir=<data_dir>/Downloads
--auto-file-renaming=false
--allow-overwrite=true
--continue=true
--file-allocation=none
--referer=https://y.qq.com/
--user-agent=QQMusic/1.0 (macOS)            # ← 注意写死 macOS
（再拼接 options.to_flags()：--split 等）
```

端口被占用时会自动改用空闲端口（`src/aria2.rs:203-208`）。启动后最多等 5 秒轮询 `aria2.getVersion`（`src/aria2.rs:277-291`）。

**关闭**：`shutdown()` 先 `aria2.forceShutdown` RPC，等 2 秒，再 `kill()`（`src/aria2.rs:256-270`）。子进程适配器在 stdin 关闭时（`src/bin/stdio.rs:105-107`）以及检测到父进程消失时（`src/bin/stdio.rs:110-126` 的 `watch_parent`：每 2 秒比一次 `getppid()`，变了就 `shutdown()` + `exit(0)`）都会调它。

### 4.2 缺席时的行为

`ensure_running()`（`src/aria2.rs:192-241`）第一步就是：

```rust
196      if !self.is_installed() {
197          return Err(UpstreamError::Upstream(format!(
198              "没有随组件携带的 aria2-next（期望在 {}）",
199              self.binary().display()
200          )));
```

`is_installed()` = `self.binary().is_file()`（`src/aria2.rs:158-160`）——**只判「文件存在」**，不判可执行位、不判架构匹配、不判是否真能跑。

所以缺席时：

- `aria2_add` / `aria2_tell` / `aria2_pause` / `aria2_unpause` / `aria2_cancel` → **报错**（它们都先经 `ensure_running()`，见 `src/aria2.rs:343` 的 `add()`、`src/aria2.rs:461` 的 `control()`）。
- `aria2_status(ensure=false)` → **不报错**，返回 `installed: false, running: false, binary: "<path>", options: {...}`（`src/aria2.rs:294-306` 的 `status()` 在 `!running` 时直接构造这个对象）。这是个**只读探测**入口，正好用来做「引擎在不在」的判定。
- `aria2_restart` → 报错（`src/aria2.rs:272-275` → `ensure_running()`）。
- `aria2_list` → **返回空列表，不报错**（`src/aria2.rs:376-380`）：`if !self.is_running() { return Ok(Vec::new()); }`。

### 4.3 宿主应该如何退让

组件文档给了明确答案，`docs/endpoints.md:90`：

> 引擎缺席时这些方法报错，宿主应退回自己的下载方式。

macOS 宿主 README 也是同一句（`:126`「引擎缺席（老版本组件目录）时，应用**自动退回自己的下载实现**，功能不受影响」）。

给 Android 宿主的具体建议：

- **判定入口用 `aria2_status(ensure=false)` 的 `installed` 字段**，不要靠 `aria2_add` 报错来探测（那会先触发 `create_dir_all(<dir>/Downloads)` 之类的副作用，且错误信息是中文文案不利于分支）。
- **`installed == false` 就整个走宿主自己的下载器**，并且**不要**把 aria2 相关按钮显示成「可用」。
- **注意 Android 的现实**：`aria2-next` 是 ELF 可执行文件，Android 应用**无法**从 `filesDir` 直接 `execve` 一个含 `.so`/可执行文件——除非应用把二进制解压到一个可执行位置（`nativeLibraryDir` / app-specific 可写并可执行的目录），并且 targetSdk 的限制（W^X、`exec` 分区策略）会拦。**而且 `/tmp/helpernext_pack_rehearsal/repo/dist/android/` 里根本没有 `aria2-next`**：

  ```
  $ find /tmp/helpernext_pack_rehearsal/repo/dist -name "aria2*"
  (无输出)
  $ ls /tmp/helpernext_pack_rehearsal/repo/dist/android/jniLibs/arm64-v8a/
  libqqmusic_api_helper_next.so        # 只有一个 .so
  ```

  这不是打包漏了：`boltffi.toml` 里没有任何 aria2 相关条目（`grep -rn "aria2" boltffi.toml Cargo.toml` 无命中）。所以 **`boltffi pack android` 的产物天然不含下载引擎**，「随组件一同发布」这句话只对 macOS 的发行包成立。Android 宿主**必须自带下载实现**——不是可选退让，而是默认路径。

  另外 `src/aria2.rs:233` 把 UA 写死成 `--user-agent=QQMusic/1.0 (macOS)`；即便引擎能跑起来，这个 UA 在 Android 宿主场景下也不合适（CDN 是否介意未经实测）。
- **不要**用 aria2 的 16800 端口做任何假设：端口可能被占用而漂移到随机空闲端口（`src/aria2.rs:203-208`），实际端口以 `aria2_status` 返回的 `port` 为准（`src/aria2.rs:335-338`，`status()` 里 `"port": self.port()`）。

---

## 5. 打包

### 5.1 `boltffi.toml` 里 Android 相关的开关

`/tmp/helpernext_pack_rehearsal/repo/boltffi.toml`（行号即该文件）：

```toml
37  [targets.android]
38  enabled = true
39  output = "dist/android"
40  min_sdk = 24

42  [targets.android.kotlin]
43  package = "com.example.qqmusic_api_helper_next"
44  desktop_loader = "bundled"
45  api_style = "top_level"
46  error_style = "throwing"
47  factory_style = "constructors"

49  [targets.android.kotlin.desktop_pack]
50  enabled = false

54  [targets.android.header]          # 空 → 默认 dist/android/include

56  [targets.android.pack]            # 空 → 默认 dist/android/jniLibs

58  [targets.android.link]
59  extra_args = ["-Wl,-z,max-page-size=16384"]     # ← 两树唯一的差异

61  [targets.android.debug_symbols]
62  enabled = false
63  format = "zip"
64  bundle = "unstripped"
65  standalone_archive = true
```

各开关的实际作用（对照 boltffi_cli 0.31.0 源码 `~/.cargo/registry/src/…/boltffi_cli-0.31.0/`）：

| 开关 | 值 | 作用 | 出处 |
|---|---|---|---|
| `min_sdk` | `24` | 选 NDK 的 `{abi}{min_sdk}-clang` 作为链接器 | `src/toolchain/android.rs:150-151` (`clang_for_abi` 拼 `format!("{}{}-clang", abi.clang_prefix(), min_sdk)`)。本机 NDK 27.3.13750724 确实有 `aarch64-linux-android24-clang` |
| `package` | `com.example.qqmusic_api_helper_next` | 生成 Kotlin 的 `package` 声明与 JNI 符号名 | 见下 |
| `desktop_loader` | `bundled` | 生成的 Kotlin 里 `Native.ensureInitialized()` 用 `System.loadLibrary("qqmusic_api_helper_next")` | `QqmusicApiHelperNext.kt:641-651` |
| `api_style` | `top_level` | 顶层 `fun` 而不是 `object` 成员 | 生成物里 47 个都是顶层 `fun` |
| `error_style` | `throwing` | Rust `Result::Err` → Kotlin `throw HelperError` | `QqmusicApiHelperNext.kt:2902` 的 `throw HelperError.fromReader(...)` |
| `factory_style` | `constructors` | `#[data]` 类型用主构造函数 | 生成物全是 `data class X(...)` |
| `extra_args` | `["-Wl,-z,max-page-size=16384"]` | 透传给 clang 链接 → **LOAD 段 16 KB 对齐** | `src/pack/android/link.rs:347` (`args.extend(extra_args…)`)；这行是 compiled-in 的额外参数拼在最后 |
| `debug_symbols.enabled` | `false` | 不做符号归档 | `src/pack/android/link.rs:238-241` |

**`min_sdk` 只影响链接器选择，不改 Rust target 的 triple**：targets 仍是 `aarch64-linux-android` / `armv7-linux-androideabi` / `x86_64-linux-android` / `i686-linux-android`（`src/target.rs:389-407`），实际 artifact 目录也确实叫这几个名字（`target/{aarch64-linux-android,armv7-linux-androideabi,x86_64-linux-android,i686-linux-android}/release/`）。所以「min_sdk=24」并不意味着产物的 ELF 标了 API 24——它体现在链接时的 `-target` 与所用 libc stub 上。

**`jniLibs` 没有独立开关**：它由 `output` + `pack.output` 推导（`boltffi_cli-0.31.0/src/config/mod.rs:648-655`：`android_pack_output()` = `targets.android.pack.output` ?? `targets.android.output.join("jniLibs")`）。所以「jniLibs」不是一个可配项，而是默认落点。

**唯一两树差异**（`diff`）：

```
$ diff /tmp/helpernext_probe/boltffi.toml /tmp/helpernext_pack_rehearsal/repo/boltffi.toml
59c59
< extra_args = []
---
> extra_args = ["-Wl,-z,max-page-size=16384"]
$ cd /tmp/helpernext_pack_rehearsal/repo && git status --short
 M boltffi.toml
```

也就是说：**rehearsal 产物比 probe 源码多加了 16 KB 页对齐（Google Play 从 2025-11 起对 targetSdk 35+ 强制要求）**，而这一步尚未提交回上游仓库。

### 5.2 `pack android` 产出哪些文件与体积

`find dist -type f` 全量：

| 文件 | 字节 | 说明 |
|---|---|---|
| `dist/android/include/qqmusic_api_helper_next.h` | 12,603 | C 头（`targets.android.header`） |
| `dist/android/kotlin/jni/qqmusic_api_helper_next.h` | 12,603 | 与上面同（JNI 编译用） |
| `dist/android/kotlin/jni/jni_glue.c` | 45,102 | JNI 胶水（47 个 `JNIEXPORT`） |
| `dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt` | 153,658 | Kotlin 绑定（3557 行） |
| `dist/android/jniLibs/arm64-v8a/libqqmusic_api_helper_next.so` | 10,720,072 | |
| `dist/android/jniLibs/armeabi-v7a/libqqmusic_api_helper_next.so` | 8,662,608 | |
| `dist/android/jniLibs/x86/libqqmusic_api_helper_next.so` | 9,912,900 | |
| `dist/android/jniLibs/x86_64/libqqmusic_api_helper_next.so` | 10,597,808 | |
| **`dist/android` 合计** | **38 MB** (`du -sh`) | |
| `dist/apple` | 空 | |
| `dist/wasm` | 不存在 | `targets.wasm.enabled = true` 但未产出 |

**四个 ABI 全打**（没有 `architectures` 白名单，`AndroidConfig.architectures: Option<…>` 为 `None` → 默认全打）。这对 APK 体积的影响很直接：如果宿主只发 arm64，另外三个可以裁掉——按 `du` 计，四个 `.so` 占 38 MB 中的绝大部分。

**体积构成（重要）**：这些 `.so` 是 **`with debug_info, not stripped`**：

```
$ file dist/android/jniLibs/arm64-v8a/libqqmusic_api_helper_next.so
ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, with debug_info, not stripped
```

arm64 那一个按 section 拆分（用 `llvm-readelf -SW` 累加）：

```
total section bytes: 10.72 MB
debug+.symtab+.strtab: 7.28 MB     ← 68%
non-debug payload:     3.44 MB     ← 32%
```

即 **10.7 MB 里有 7.3 MB 是 DWARF 调试信息**。DWARF 里含绝对源码路径（`strings -a` 能看到 `/Users/mac/.cargo/registry/src/…/ring-0.17.14/src/limb.rs`、`src/lib.rs` 等）。

对照 cargo 自己产出的那个（同一份 `.a` 链接、但被 `Cargo.toml` 的 `strip = true` 处理过）：

```
$ ls -la target/aarch64-linux-android/release/libqqmusic_api_helper_next.so
-rwxr-xr-x  3408120                    # 3.4 MB，stripped
$ file … ; ELF 64-bit LSB shared object, ARM aarch64, …, stripped
```

**为什么 dist 里的是带调试信息的**：`Cargo.toml` 的 `[profile.release] strip = true`（`/tmp/helpernext_probe/Cargo.toml` 末段），但那**管不到** boltffi 的第二步——boltffi 是用 NDK clang 把 `jni_glue.o` 与 **staticlib**（`libqqmusic_api_helper_next.a`，64 MB）重新链接一遍（`src/pack/android/link.rs:168-227` 的 `link_shared_library`，args 见 `:322-349`：`-shared -o <dest> <jni_glue.o> -Wl,--whole-archive <library.path> -Wl,--no-whole-archive -Xlinker --version-script … -Wl,--gc-sections -Wl,-z,nodelete -lm -llog -ldl` + `extra_args`）。这个链接命令里有 `--gc-sections` 但**没有 `-s`/`--strip-debug`**，且 `debug_symbols.enabled = false` 只影响是否额外汇出符号归档，不影响是否 strip（`link.rs:233-241`，`emit_debug_info` 只控制 `jni_glue.c` 是否带 `-g`）。

**给宿主的两条实际含义**：
1. `-Wl,-s`（或 `--strip-debug`）加进 `targets.android.link.extra_args` 就能砍掉约 2/3 体积——这是一个改一行配置的优化，值得在接入前先做。
2. 更彻底的做法是让 `jni_glue.o` 走 NDK clang、而不是重链整个 staticlib；那属于上游改造。

**注意 probe 与 rehearsal 的 `.so` 不一样大**（55.5 MB vs 10.7 MB），因为 probe 那次用的是 `targets.android.debug_symbols.enabled = true` 相关的编译配置（编译 `jni_glue.o` 时加了 `-g`、且 cargo profile 带 debuginfo；fingerprint 显示两次的 `profile` hash 不同：`10362365388272316749` vs `5037023634454849727`，而两者链接的 `libqqmusic_api_helper_next.a` 是**同一个 md5** `e5ed31f815f269071c6edfdc352b316e`）。所以这个差异来自 boltffi 的编译/profile 选项，不是源码差异。

### 5.3 `llvm-readelf` 看 arm64 `.so` 的 LOAD 段对齐

命令（NDK 27.3.13750724 的 `llvm-readelf`；系统 PATH 里没有 `readelf`/`llvm-readelf`，`find ~/Library/Android/sdk/ndk -name "llvm-readelf"` 定位到）：

```
$ ~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf -lW \
    /tmp/helpernext_pack_rehearsal/repo/dist/android/jniLibs/arm64-v8a/libqqmusic_api_helper_next.so
```

完整输出：

```
Elf file type is DYN (Shared object file)
Entry point 0x0
There are 10 program headers, starting at offset 64

Program Headers:
  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align
  PHDR           0x000040 0x0000000000000040 0x0000000000000040 0x000230 0x000230 R   0x8
  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0x0ffbf0 0x0ffbf0 R   0x4000
  LOAD           0x0ffc00 0x0000000000103c00 0x0000000000103c00 0x224920 0x224920 R E 0x4000
  LOAD           0x324520 0x000000000032c520 0x000000000032c520 0x017318 0x017ae0 RW  0x4000
  LOAD           0x33b838 0x0000000000347838 0x0000000000347838 0x00b628 0x00bfd8 RW  0x4000
  DYNAMIC        0x33af50 0x0000000000342f50 0x0000000000342f50 0x0001b0 0x0001b0 RW  0x8
  GNU_RELRO      0x324520 0x000000000032c520 0x000000000032c520 0x017318 0x017ae0 R   0x1
  GNU_EH_FRAME   0x0abe54 0x00000000000abe54 0x00000000000abe54 0x00a224 0x00a224 R   0x4
  GNU_STACK      0x000000 0x0000000000000000 0x0000000000000000 0x000000 0x000000 RW  0x0
  NOTE           0x000270 0x0000000000000270 0x0000000000000270 0x000098 0x000098 R   0x4

 Section to Segment mapping:
  Segment Sections...
   00
   01     .note.android.ident .dynsym .gnu.version .gnu.version_r .gnu.hash .dynstr .rela.dyn .rela.plt .rodata .gcc_except_table .eh_frame_hdr .eh_frame
   02     .text .plt
   03     .data.rel.ro .fini_array .init_array .dynamic .got .got.plt .relro_padding
   04     .data .bss
   05     .dynamic
   06     .data.rel.ro .fini_array .init_array .dynamic .got .got.plt .relro_padding
   07     .eh_frame_hdr
   08
   09     .note.android.ident
   None   .comment .debug_loc .debug_abbrev .debug_info .debug_ranges .debug_str .debug_line .debug_aranges .debug_frame .symtab .shstrtab .strtab
```

**答案：四个 LOAD 段的 Align 全是 `0x4000` = 16384 = 16 KB。** 这正是 `extra_args = ["-Wl,-z,max-page-size=16384"]` 的效果。

四个 ABI 全部一致：

```
$ for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    llvm-readelf -lW dist/android/jniLibs/$ABI/libqqmusic_api_helper_next.so | awk '/LOAD/{print $NF}'
  done
arm64-v8a:   size=10720072 LOAD_align=(0x4000 0x4000 0x4000 0x4000)
armeabi-v7a: size=8662608  LOAD_align=(0x4000 0x4000 0x4000 0x4000)
x86:         size=9912900  LOAD_align=(0x4000 0x4000 0x4000 0x4000)
x86_64:      size=10597808 LOAD_align=(0x4000 0x4000 0x4000 0x4000)
```

**对照**：不加这个 flag 时（probe 树里 `extra_args = []` 的那个产物）确实是 4 KB：

```
$ llvm-readelf -lW /tmp/helpernext_probe/dist/android/jniLibs/arm64-v8a/….so | grep LOAD
  LOAD  0x000000 … R   0x1000
  LOAD  0x212780 … R E 0x1000
  LOAD  0x688ce0 … RW  0x1000
  LOAD  0x6c2358 … RW  0x1000
```

这就把「rehearsal 那行改动做了什么」钉死了：**它是把 4 KB 对齐改成 16 KB 对齐的那一步**，其他一切不变（Kotlin 与头文件逐字节相同：`diff -q` 两个文件的输出是 `KOTLIN IDENTICAL` / `HEADER IDENTICAL`）。

**另一处佐证**：`.note.android.ident` 里的 NT_ANDROID_TYPE_IDENT 内容是 `r27d` + `13750724`——即用 NDK **r27d / 27.3.13750724** 构建，与 `targets.android.min_sdk` 无关（那是构建环境信息）。

**动态段读完（顺带记录了宿主需要知道的事实）**：

```
$ llvm-readelf -dW …so | head
  0x1 (NEEDED)  Shared library: [libm.so]
  0x1 (NEEDED)  Shared library: [liblog.so]
  0x1 (NEEDED)  Shared library: [libdl.so]
  0x1 (NEEDED)  Shared library: [libc.so]
  0x1e (FLAGS)  BIND_NOW
  0x6ffffffb (FLAGS_1)  NOW NODELETE
```

`BIND_NOW` 是因为链接参数里的 `-Wl,-z,nodelete`（`src/pack/android/link.rs:342`）与 NDK 默认的 `-z now`。`NODELETE` 意味着这个库一旦 `dlopen` 就不会被 `dlclose` 卸载——对宿主无影响，但解释了为什么「重新加载新版本的 .so」在同一进程内不可能。

**只依赖 libm/liblog/libdl/libc**，没有 `libc++_shared.so`（Rust 不用 libstdc++），所以宿主**不需要**为它打包 C++ 运行时。这对 APK 是个好消息。

---

## 6. 参考宿主（macOS）README 的要点：哪些对 Android 成立、哪些不成立

文件：`/Users/mac/Library/Application Support/kmgccc.player/QQMusicHelperNext/README.md`（159 行，已全文阅读）。它是**针对子进程路径**写的（组件二进制 + `QQMUSIC_HELPER_NEXT_DIR`），而 Android 宿主走的是 **FFI 路径**。两者的成立性差异是这节的重点。

### 6.1 对 Android 同样成立

1. **「组件自己管凭据」/目录与文件名**（README `:29-30`）：路径 `<目录>/Credential/qqmusic-credential.json`、二维码登录产出也写这里。Android 上完全一样（`src/credential.rs:120-123`）。
2. **`set_rate_limit` / `set_breaker` 的协议形状、clamp 范围、语义**（README `:38-59`）：请求/回值 JSON、`windowSeconds/maxRequests` clamp `[1,3600]/[1,100000]`、`failureThreshold/failureWindowSeconds/openSeconds` clamp `[1,100]/[1,3600]/[1,3600]`、「超限是等待不是丢弃」、「改配置会清空熔断状态」、「`get_status` 回显两者」。这些语义都在 `src/guard.rs` 与 `src/methods.rs:468-536`，与传输方式无关。
   - ⚠️ **但可达性不同**：README 假设这些方法**能调**；Android 走 FFI 时它们全部返回「不支持的方法」（第 1.4 节实测）。所以「语义成立」而「入口不成立」。
3. **「每次组件重启后都会重推」（`:35`）**：对 Android 完全成立——限流/熔断是纯内存状态（`src/guard.rs` 无文件 IO），进程重启即忘。
4. **「`get_status` 会回显两者，所以应用能显示组件当前生效的值」**：设计意图成立，但**Android FFI 上 `guardStatus()` 解析失败**（1.5 节实测 `Upstream(missing field 'breaker')`），这条对 Android 宿主**不成立**。
5. **aria2 的架构设计**（README `:62-72`：「为什么是另一个进程」、组件管接口/aria2 管字节、应用只剩一句「要这个文件」）：设计说明成立。
6. **aria2 的协议方法表与要点**（README `:74-126`）：方法名/参数/`out` 是引擎目录内文件名、端口默认 16800 与可配、**端口运行中不可改**（监听套接字搬不了）所以下次启动生效、端口被占用自动改用空闲端口、只监听回环、`--rpc-secret` 每次启动重新生成、参数 clamp（split/连接数 1–16、任务数 1–10、最小分块 1MB 起）、取消会删临时文件（先读 `path`→`forceRemove`→删文件连 `.aria2`→`removeDownloadResult`）、引擎按需启动。**全部与传输方式无关，对 Android 成立**。
   - ⚠️ 同样地，**Android FFI 上这 9 个方法全是死的**（1.4 节实测）。
   - 另外 README `:122`「参数会被 clamp——上游对这些字段是零容忍的」对应 `src/aria2.rs:76-97` 的 `sanitised()`。
7. **「引擎缺席时应用自动退回自己的下载实现，功能不受影响」**（`:126`）：对 Android 成立，而且**在 Android 上是默认路径**——`pack android` 的产物里根本没有 `aria2-next`（第 4.3 节实测 `find dist -name "aria2*"` 无输出）。
8. **兼容性一节的大部分结论**（`:142-159`）：按固定键名解码、缺键不报错只静默为空（**这条对 Android 更严重**——1.5 节实测 `album_detail` 就静默返回空对象）；`fetch_artist_biography` 包在 `artistDetail`（不是 `detail`）；`fetch_toplist_categories`/`fetch_radio_stations` 同时给 `name` 与 `title`；`resolve_song_url` 成功与失败都给全 `extension`/`tried`/`restriction`/`source`；歌单与排行榜分页并回报总数；风控码一律报错；`HostUin` 用数字账号 id 不依赖 `encrypt_uin`。
   - ⚠️ 但 README 说「**取流必须走 android profile**（票据绑在设备会话上，同一首歌 web profile 只给 128 或直接拒绝）」——这条对 Android 宿主**反而是自动的**：`src/methods.rs:104-121` 里 `resolve_song_url` 属于「默认走 Android profile」的一组，宿主不用额外做什么（除非显式传 `platform: "web"`）。
9. **`import_cookies` 只要 `uin` + `qm_keyst`**（`docs/endpoints.md:33`，README 的等价句在 `:30` 附近）：成立。FFI 签名也是这两个（`src/api.rs:70`）。

### 6.2 对 Android 不成立 / 需要改写

1. **`QQMUSIC_HELPER_NEXT_DIR` 不是给 Android FFI 宿主的推荐手段**（README `:30` 的原话「目录由应用通过 `QQMUSIC_HELPER_NEXT_DIR` 告诉它」）。在 FFI 路径上：一是 FFI 里根本没有 `configure`（1.3 节），二是 README 的 macOS 默认值逻辑**只存在于子进程适配器里**（`src/bin/stdio.rs:32-43`：`QQMUSIC_HELPER_DIR` → `$HOME/Library/Application Support/kmgccc.player/QQMusicHelperNext`）。Android 上既没有 `HOME` 也没有那个路径，且 `bin/stdio.rs` **不参与** `.so` 的构建。Android 只能用 `Os.setenv` 设那个环境变量（可行但绕，见 1.3），或者推动上游给 `configure` 加 `#[export]`。
2. **「应用优先从外部目录加载它：`~/Library/Application Support/…`；bundle 内那份只是兜底」**（`:4-10`）：macOS 特有的双份查找。Android 上对应概念是 `jniLibs` 打进 APK、由 `System.loadLibrary` 从 `nativeLibraryDir` 加载（`QqmusicApiHelperNext.kt:641-651` 的 `Native` object；`loadDesktopLibraries` 那一套 `bundledLibraryResourceCandidates` 只在非 Android 运行时走）。**这条要整段替换**。
3. **`cargo build --release && cargo test; cp target/release/qqmusic-helper-next <目录>/` 的更新方式**（`:14-19`）：Android 的更新路径是 `boltffi pack android` + `jniLibs` 进 APK，没有可替换的单个可执行文件。**不成立。**
4. **「协议：stdin 一行一个请求、stdout 一行一个响应，每个响应都回带请求的 `id`（应用按它配对，漏掉会等到 15 秒超时）」**（`:22-23`）**以及 aria2 那整节的 JSON 请求/回值样例**：这是子进程协议。Android 走 FFI 时**没有 `id`、没有行、没有超时配对**——是同步函数调用，错误以 `HelperError` 抛出（`QqmusicApiHelperNext.kt:2902`）。这一整块的**传输层细节不适用**（但同一个方法名/字段语义仍在 FFI 里）。
5. **`codesign --force --sign - <二进制>` 与 `xattr -cr` 的 ad-hoc 签名要求**（`:128-140`：「不签名的可执行文件在带 `com.apple.provenance` 的位置会被系统直接杀掉，退出码 137」）：这是 macOS Gatekeeper/AMFI 的行为。**Android 上完全不适用**——.so 由 APK 签名机制与安装时校验保证，不需要单独签名。
6. **「组件是独立进程，退出即忘」的隐含前提**：README 把「进程」当成必然。在 FFI 路径上组件活在宿主进程内，所以：
   - 没有「组件退出」这回事——宿主进程死它就死。
   - 反过来，**组件一旦崩溃就是宿主进程崩溃**（一次段错误直接带走 App）。这不在 README 里，但是 Android 宿主必须知道的风险。
   - 因此 `src/bin/stdio.rs:110-126` 的 `watch_parent`（用 `getppid()` 检测宿主消失、顺手 `shutdown()` aria2）**在 FFI 路径上完全没有对应物**（它不是 `#[export]`）。如果哪天 Android 上真能跑 aria2，那个进程**不会被组件回收**——不过 4.3 节已说明 Android 上根本不该指望 aria2。
7. **`aria2-next` 与组件一起发布、放在组件旁边**（`:62-65`、`:130-137` 的安装脚本）：macOS 成立，Android 不成立（第 4.3 节实测 `pack android` 产物里没有它）。README 的这段会让 Android 宿主误以为「装上就有下载引擎」。
8. **`qqmusic-helper-next` 可执行文件「约 2.4 MB，静态链接，无解释器」的体积印象**（`:3`）：对 FFI 产物不适用（4 个 ABI × 10.7 MB = 38 MB，其中 68% 是调试信息；第 5.2 节实测）。
9. **README 里 `resolve_song_url` 那段「下载会按 `.mp3` 命名一个 flac 文件」的坑**（`:154`）：这条**在本次调查里没有测到**。该函数先撞登录门（`src/catalog.rs:1050`），所以在无凭据环境下只能看到 `NotLoggedIn`，看不到解析层的行为。它的 wrapper key `stream` 确实不在 `parse()` 名单里（代码上确定），因此**理论上**存在同样的静默空风险，但我**没有实测证据**——登录后再验才算数。

### 6.3 README 没写、Android 宿主必须自己补的

（这一节是调查里发现的空白，不是 README 的引述。）

1. **数据目录怎么给**（1.3 节）：FFI 无 `configure`，只能 `Os.setenv("QQMUSIC_HELPER_NEXT_DIR", …)` 放在最早，或者改上游。
2. **别在主线程调用**：`docs/ffi.md` 的「宿主需要做的两件事」第 2 条说得最清楚——「导出的函数是**同步阻塞**的（内部是一个 HTTP 往返，最长 12 秒超时）」，「`viewModelScope.launch(Dispatchers.IO)`」。
   - 补充一个 README 没提的实际数值：`Upstream::with_device` 里 `timeout_global(Some(Duration::from_secs(20)))`（`src/upstream.rs:124-127`），注释还说明 QIMEI 握手可以更慢、「它一天才发生一次」。所以**最坏阻塞时间是 20 秒级**，不是 12 秒。这一点对 ANR 预算很重要。
3. **`uin` 的 `o` 前缀要宿主自己剥**（2.2 节实测：组件不做）。
4. **11 个方法在 FFI 上是死的**（1.4 节）：`set_rate_limit` / `set_breaker` / 全部 aria2。宿主如果按 README 的 JSON 样例去调，拿到的是 `HelperError.Upstream("不支持的方法：…")`。**这是接入前必须先和上游确认/修复的阻塞项。**
5. **一批模型的 wrapper key 没被 `parse()` 解包**（1.5 节）：`toplist_categories` / `radio_stations` / `recommend_feed` / `new_songs` / `song_detail` / `artist_detail` / `search_*_artwork` / `lyric` / `start_login` 会**报错**；`album_detail` / `fetch_artist_biography` 会**静默解析成空对象**（`<ok>` + 全零 payload）。后者更危险，因为宿主收到的是一份合法的空数据。`resolve_song_url` 的 wrapper key `stream` 同样不在名单里，但它先撞登录门，**本次未能实测**。
6. **`resolve_song_url` 的 `filename` 前缀规则**：`src/catalog.rs:25-30` 的 `QUALITY_LADDER` = `flac/F000/.flac`、`320/M800/.mp3`、`128/M500/.mp3`、`aac/C400/.m4a`，逐级探测、命中即停（`src/catalog.rs:1050-1090`）。宿主若自己拼下载文件名，必须用返回的 `extension`，不能假定 `.mp3`。
7. **`result=104003` 是档位级的、降级链不能中断**：`src/catalog.rs:32-36` 有这几个常量（`RESULT_NO_PERMISSION=104003`、`RESULT_VKEY_FAILED=104004`、`RESULT_DEVICE_RESTRICTED=104013`）。这正好是本工程 AGENTS.md 里记的那条坑（「降级链里见到 104003 绝不能中断」），组件这边已经处理了。

---

## 7. 未做/无法验证的事

诚实记录，避免读者把推断当实测：

1. **没有运行任何构建**（用户约束）。所以「`pack android` 会产出什么」是**读已存在的产物**得出的，不是我在本次会话里跑出来的。
2. **没有在 Android 设备/模拟器上验证**（用户约束：不启动/连接 adb、模拟器）。1.3 节里「Android 上 `"."` 通常是 `/` 且不可写」是**平台常识 + 组件代码路径推导**，不是在本机 Android 上的实测。
3. **本节所有 ctypes 实测都是在 macOS 上、用 `/tmp/helpernext_probe/target/debug/deps/libqqmusic_api_helper_next.dylib`（debug 构建，Mach-O arm64）做的**。它与我验证过的 `methods.rs`/`api.rs` 源码是同一份 **方法分发逻辑**（`src/bin/stdio.rs` 与 `api.rs` 两个入口都指向 `methods::dispatch`），但由于它是 macOS 目标而非 Android 目标，理论上仍存在「平台条件编译导致行为不同」的余地。我在源码里找了 `cfg(target_os)` 之类：`grep -rn "target_os" /tmp/helpernext_probe/src/` 没有任何命中，所以 platform-gated 的行为差异在这份代码里不存在。**但严格说，Android `.so` 本身没有跑过。**
   - 具体地说：`target/release/deps/libqqmusic_api_helper_next.dylib` 这个 release 版**加载失败**（`dlopen: mis-aligned LINKEDIT string pool`），我改用的 debug 版能加载。debug 版与 release 版的**分发逻辑来自同一份源码**（两棵树源码 md5 已核对一致），所以这一条影响不大，但它是「我用的是 debug 不是 release」的如实说明。
4. **`aria2_pause/unpause/cancel` 传 `Some(gid)` 时返回 `ptr=NULL/len=0`（既非成功也非错误）** 这一条，我无法确定它在真机上是否是可靠行为。它**很可能是我自己的 ABI 错误**（`Option<String>` 的 wire 编码是 `1 字节 tag + 4 字节 len + utf8`，我用 ctypes 手工拼的载荷可能在长度字段上偏了一位），而不是组件行为。传 `None` 时返回清晰的「不支持的方法」错误，那一条是确定的。**这一条按「未定论」处理，不要当成组件缺陷。**
5. **我在调查中途发现并更正过一次自己的误判**，记在这里以免读者重复踩：`resolve_song_url` 我起初报「静默全空」，复测发现那是**登录门**（`require_login`）而不是解析层。同理，第 1.5 节表里所有「报错」与「静默空」的结论，都是在我把 wire 编码改对之后（`Option` 用 `1 字节 tag + payload`、`String` 用 `4 字节 LE len + utf8`、`Vec` 用 `4 字节 count + 元素`，依据是生成物 `QqmusicApiHelperNext.kt:311-317`（`readOptional`）与 `:572-579`（`writeOptional`））才得出的。**此前用错编码跑出来的那几行结论已全部作废并从报告里改掉。**
6. **`min_sdk = 24` 究竟如何在产物里体现**（是否只用于选 linker、是否影响 libc stub 版本）我读的是 boltffi_cli 的实现与产物目录名，**没有反汇编 ELF 去确认 API level 标记**。
7. **`dist/wasm` 虽然 `boltffi.toml` 里 `enabled = true` 但产物不存在**——我没有调查原因（不在本次范围）。
8. **`aria2-next` 在 Android 上能否被 exec** 我按 Android 平台策略做了推断，**没有在设备上实测**。
9. **`get_helper_info` 的 `credentialDir: true` 是什么语义**未定论。`ComponentInfo` 模型里没有这个字段（`src/models.rs:234-244`），所以它到不了 Kotlin 侧；我判断它是给子进程 JSON 消费者的占位布尔，但**这是推断**。
10. **`search_*` / `*_artwork` / `toplist_*` / `radio_*` 这些「不需要登录」的方法，其解析失败结论是在无凭据环境下取得的。** 解析是纯本地步骤，与登录无关，所以我有信心；但严格说它们同样没在「已登录」状态下复测过。

---

## 附录：本次调查用到的关键命令

```bash
# —— 只读检查 ——
diff -r --brief /tmp/helpernext_probe/src /tmp/helpernext_pack_rehearsal/repo/src
md5 /tmp/helpernext_probe/src/methods.rs /tmp/helpernext_pack_rehearsal/repo/src/methods.rs
diff /tmp/helpernext_probe/boltffi.toml /tmp/helpernext_pack_rehearsal/repo/boltffi.toml
grep -c "#\[export\]" /tmp/helpernext_probe/src/api.rs                      # 47
grep -c "^fun " …/dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt   # 47
grep -c "external fun boltffi_function" …/QqmusicApiHelperNext.kt           # 47
grep -c "^JNIEXPORT" …/dist/android/kotlin/jni/jni_glue.c                   # 47
grep -c "boltffi_function_" …/dist/android/include/qqmusic_api_helper_next.h  # 47

# normalize + diff the two surfaces (snake_case vs camelCase)
python3 -c '...'   # 见正文 1.2：in rust not kotlin: []; in kotlin not rust: []

# —— LOAD 对齐（NDK 的 llvm-readelf；PATH 里没有 readelf）——
~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf -lW \
  /tmp/helpernext_pack_rehearsal/repo/dist/android/jniLibs/arm64-v8a/libqqmusic_api_helper_next.so
# → 4× LOAD, Align = 0x4000

# —— 动态符号 ——
~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm -D --defined-only \
  …/arm64-v8a/libqqmusic_api_helper_next.so | grep -i configure     # 只有 aria2 的

# —— 子进程路径的实证（用 macOS 那份已打包二进制）——
printf '%s\n' '{"id":"1","method":"aria2_status","params":{"ensure":false}}' \
 | QQMUSIC_HELPER_NEXT_DIR=$SCRATCH "…/QQMusicHelperNext/qqmusic-helper-next"

# —— FFI 路径的实证（原生调用 47 个 boltffi_function_* 符号）——
python3 -c 'ctypes.CDLL("/tmp/helpernext_probe/target/debug/deps/libqqmusic_api_helper_next.dylib"); ...'
# → set_rate_limit / set_breaker / aria2_* 全部 Upstream("不支持的方法：…")

# —— 数据目录回退的实证 ——
python3 -c '不设 QQMUSIC_HELPER_NEXT_DIR，cd 到临时目录，调 import_credential'
# → 临时目录下出现 Credential/qqmusic-credential.json

# —— 环境变量是公开 API ——
$JAVA_HOME/bin/javap -classpath ~/Library/Android/sdk/platforms/android-36/android.jar android.system.Os
# → public static void setenv(java.lang.String, java.lang.String, boolean)
```

（所有临时目录 `rm -rf` 清理；`/tmp/helpernext_probe`、`/tmp/helpernext_pack_rehearsal/repo`、`~/Library/Application Support/kmgccc.player/QQMusicHelperNext/` 三处未写入任何内容。）
