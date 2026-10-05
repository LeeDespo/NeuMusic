# 独立复核：HelperNext 如何被 Android 宿主接入（反证版）
> **历史快照注记（2026-10-05）**：本文写于 HelperNext 组件接入前后，文中引用的 `data/api/QqCore.kt`、`data/api/QrcCodec.kt` 及「原生 Kotlin 直连」均为**当时的工程状态**——这些文件现已删除，QQ 请求/签名/设备档案/凭据/QRC 解密改由 HelperNext Rust 组件经 BoltFFI/JNI 执行（见 `AGENTS.md` 顶部「2026-10-05 当前 QQ 数据架构」）。本文仅作调研证据保留，其中的机制描述与代码行号引用不再反映现状；端点、参数与实测结论仍有参考价值。

复核对象：`/tmp/helpernext_probe`（组件源码 + probe 打包产物）、`/tmp/helpernext_pack_rehearsal/repo`（打过补丁的打包排练产物）、工程 `AGENTS.md`。

约束遵守情况：本次全部为只读操作；只写了本文件；未改动 `/tmp` 下任何内容；未跑任何构建；未读工程源码以外的工程文件、未运行 gradle、未碰 adb/模拟器。查找工具时只用到 NDK 里的 `llvm-readelf`/`llvm-nm`（`PATH` 里没有 `readelf`）。

复核方式说明：本文件对每条结论**独立取证**，不引用 `helpernext-mechanics.md` 的转述；凡与该文件不一致处直接写明。

---

## 0. 许可：GPL-3.0-or-later，以及「预编译组件 + FFI 链接随 App 分发」的性质

### 可核实的事实

| 事实 | 证据 |
|---|---|
| `Cargo.toml` 声明 `license = "GPL-3.0-or-later"` | `/tmp/helpernext_probe/Cargo.toml:6` |
| `LICENSE` 是 **GPLv3 全文**（非 LGPL、非 AGPL） | `/tmp/helpernext_probe/LICENSE:1-2` = `GNU GENERAL PUBLIC LICENSE` / `Version 3, 29 June 2007`；全文 674 行；sha256 `c53a65c2fd561c87eaabf1072ef5dcab8653042bc15308465f52413585eb6271`（两份拷贝一致） |
| LICENSE 是**纯 GPLv3 正文**，仓库里**没有**任何补充授权（无 linking exception、无附加条款） | `tail -25 LICENSE` 只有 FSF 的标准样板（含那句 "The GNU General Public License does not permit incorporating your program into proprietary programs. … use the GNU Lesser General Public License instead"）；`grep -rln "Copyright\|SPDX" src/` 无命中（**组件自带源码文件里没有任何版权/SPDX 头**） |
| README 自称 GPLv3-or-later，并说明它是 QQMusicApi 的 Rust 移植、协议认知来自该项目 | `README.md` 徽章 `license-GPL--3.0--or--later`；`## 📄 许可证` 段：「本项目采用 **GNU General Public License v3.0 or later**，与 QQMusicApi 保持一致」 |
| 分发形态 = **预编译 `.so` + 生成的 Kotlin/JNI 胶水**；Rust 源码不在分发物里 | `pack android` 产物只有 4 个 `.so` + `.h` + `.kt` + `jni_glue.c`（`find dist -type f` 8 个文件，下面的 §2 列全）；宿主只拿到二进制与绑定 |
| 链接形态 = **静态链接进同一个 `.so`**：Rust 静态库被 `--whole-archive` 整体吃进 JNI 共享库 | `boltffi_cli-0.31.0/src/pack/android/link.rs:322-349` 的链接参数列表含 `-Wl,--whole-archive <library.path> -Wl,--no-whole-archive`；产物里 47 个 `boltffi_function_*` 与 JNI 入口同处一个 `.so`（`llvm-readelf --dyn-syms`） |
| 宿主工程现状：**README 仍写着「License TBD / All Rights Reserved」，且仓库根目录没有任何 LICENSE 文件** | `README.md:61` 原文「License TBD —— 在明确的开源许可添加之前，仓库代码默认保留所有权利（All Rights Reserved）」；`ls LICENSE* COPYING*` → `no matches found` |
| 宿主工程的 `AGENTS.md` 已改口为 GPL-3.0，且**没有**「GPL 一律不引入」这条 | `AGENTS.md:77` 「## 开源协议与第三方代码（2026-10-02 起：本项目 GPL-3.0）」；`:79` 「**本项目以 GPL-3.0 开源**（用户 2026-10-02 定，取代此前的『License TBD / All Rights Reserved』；`README.md` 的协议段落尚未同步、待改）」；`:81` 「**GPL-3 兼容许可下的代码可以参阅、乃至照搬**」；`:82` 明确写旧红线「一律不搬」**已失效** |

### ⚠️ 对提问措辞的更正（这一条是 refuted）

提问里写「本工程 AGENTS.md 写明 **GPL/AGPL 代码一律不引入**」——**当前 AGENTS.md 不是这个意思，而且原文里没有「引入」这个词**。
`grep -n "引入\|不搬" AGENTS.md` 的命中只有第 8 行的 UI 无关条目、第 81/82/83 行的三条：
- `:81` 说 GPL-3 兼容许可「**可以参阅、乃至照搬**」；
- `:82` 说旧红线「一律不搬」**已失效**；
- `:83` 说**只有**「不与 GPL-3 兼容的许可」才仍然不搬。

即：AGENTS.md 已经**反向放行**了 GPL-3.0-or-later 的代码。提问所依据的前提在本仓库里不成立（`git show HEAD:AGENTS.md` → `fatal: path 'AGENTS.md' exists on disk, but not in 'HEAD'`，即 AGENTS.md 仍未入库，无法用 git 历史核对本次改动；`git log -- AGENTS.md` 无输出）。

### 两种解读（如实并列，不下法律定论）

**解读 A：构成组合作品，整体须 GPL-3.0 分发。**
依据：GPLv3 §5 把「基于该程序的作品」定义为需要整体按 GPL 授权；本组件被 `--whole-archive` 静态链接同一个 `.so`、与宿主 App 同一进程运行、功能上深度耦合（宿主所有 QQ 音乐访问都经它），且分发的是二进制而非源码。FSF 的立场一贯是「动态/静态链接到 GPL 库、形成单一可执行体」即构成组合作品；GPLv3 §6 要求向接收者提供**完整对应源码**（含用于生成该二进制的脚本、以及组件的 Rust 源码）——按此解读，宿主一旦随 APK 分发这个 `.so`，整个 App 需要以 GPL-3.0 提供源码。宿主的 `AGENTS.md:79` 恰好已宣布本项目以 GPL-3.0 开源，与解读 A 相容。

**解读 B：不构成组合作品，属「聚合/独立程序」，各自许可即可。**
依据：GPLv3 §5 末段与 §2 允许「把其他独立程序与本程序一起聚合」而不扩大许可，条件是**不是基于该程序**的组合作品；预编译组件对宿主而言是「拿来用的第三方部件」，JNI 调用的是一组稳定 ABI 的导出符号（`boltffi_function_*`），宿主并不含组件的源码或派生代码。软件工程界（如 Linux 内核的 syscall 边界论）常以此论证「进程内但接口隔离 = 聚合」。但这条在**同进程 + 静态链接 + 单一 `.so`** 的形态下比「两个独立可执行文件互相 exec」弱得多——`--whole-archive` 把目标码真正焊进了同一个 load segment，这是解读 A 最有力的事实。

**未能核实、不应被当成定论的点：** 我**没有**在本会话里查到作者对「链接例外/商业授权」的任何书面表态（`README.md` 全文读完没有授权例外段落，`LICENSE` 无附加条款）。所以「作者是否默认接受专有宿主静态链接」这个问题**在仓库内无答案**，需向作者确认。

### verdict: **confirmed**（事实层面）；提问措辞中的 AGENTS.md 前提 **refuted**

---

## 1. Android 数据目录：生成的 Kotlin 绑定里没有 `configure` 入口

### 核实过程与证据

**① 生成物 Kotlin 里确实没有 `configure` / `dataDir` 入口。**
```
$ grep -nicE "configure" .../QqmusicApiHelperNext.kt
4
$ grep -niE "datadir|data_dir|setenv|getenv|QQMUSIC" .../QqmusicApiHelperNext.kt
3:package com.example.qqmusic_api_helper_next
641:        val androidLibrary = "qqmusic_api_helper_next"     ← 库名，非目录
642/643:  desktopPreferredLibrary / desktopFallbackLibrary   ← 库名
```
三次 `configure` 命中全是 **aria2 引擎参数**：`:817` 的 native 声明 `..._api_aria2_configure`、`:3220` 的文档注释、`:3442` 的 wrapper 调用。
```
$ grep -niE "fun [a-z]*[Dd]ataDir|fun configure|QQMUSIC_HELPER" .../QqmusicApiHelperNext.kt | wc -l
0
```
**没有任何接受目录参数的公开函数**（唯一带 `dir` 的私有成员是 `:729 bundledLibraryDirectory()` / `:753 desktopNativeDirectories()`，都是**动态库搜索路径**，与组件数据目录无关）。

**② C 头文件与动态符号同样没有。**
```
$ grep -nE "configure|data_dir|dataDir|QQMUSIC|env" dist/android/include/qqmusic_api_helper_next.h
193:FfiBuf_u8 boltffi_function_..._api_aria2_configure(int64_t split, ...);   ← 只有 aria2
$ llvm-readelf --dyn-syms <shipped .so> | grep -iE "configure|data_dir|data_directory"
（只有 Java_..._aria2_1configure 与 boltffi_function_..._api_aria2_configure）
```

**③ 源码侧：`configure` 存在但**未导出**，因此正确 —— 提问描述的机制成立。**
```
$ grep -n "fn configure" src/lib.rs        →  88:pub fn configure(configuration: Configuration)
$ grep -c "#\[export\]" src/lib.rs          →  0        ← 整个 lib.rs 没有一处 #[export]
$ grep -rn "#\[export\]" src/ | wc -l       →  47，且全部在 src/api.rs
```
`CONFIGURATION: OnceLock<Configuration>` 只被 `lib.rs:89` 的 `configure()` 写入；`configure()` 仅两个调用点：`src/bin/stdio.rs:48`（子进程适配器自己调）与 `src/lib.rs:158`（单测）。**`.so` 里没有任何代码路径能写它。**

**④ 回退链三级：`configure` → 环境变量 → `"."`**（`src/lib.rs:95-107`）：① 经 `CONFIGURATION.get()` 且非空 → ② `std::env::var("QQMUSIC_HELPER_NEXT_DIR")` 且非空 → ③ `PathBuf::from(".")`。

**⑤ 环境变量路线在**分发产物**里真的存在（不是死代码）：**
```
$ llvm-nm -D --undefined-only <shipped .so> | grep -i getenv
                 U getenv@LIBC
$ strings -a <shipped .so> | grep -c "QQMUSIC_HELPER_NEXT_DIR"
1
```

**⑥ 「先设进程环境变量再调用」在 Android 上**技术上可行**，且是公开 API：**
```
$ $JAVA_HOME/bin/javap -classpath ~/Library/Android/sdk/platforms/android-37.0/android.jar android.system.Os
  public static java.lang.String getenv(java.lang.String);
  public static void setenv(java.lang.String, java.lang.String, boolean) throws android.system.ErrnoException;
  public static void unsetenv(java.lang.String) throws android.system.ErrnoException;
$ python3 查 api-versions.xml → class android/system/Os since='21'，setenv/getenv 无更晚的 since 覆盖
```
`Os.setenv` 自 API 21 起可用，本工程 `minSdk = 26`（`app/build.gradle.kts:13`）→ **可用**。
注意 `android.system.Os` **没有** `chdir`（`javap … | grep -ci chdir` → `0`），所以「改 cwd」那条路在公开 API 上不存在，只能靠环境变量。

### ⚠️ 提问里「没有就明确说『需要改组件或改上游』」不成立（部分 refuted）

提问把结论框成二选一（FFI 无入口 ⇒ 必须改组件）。**实际有第三条可行路径**：在**第一次调用组件之前**用 `Os.setenv("QQMUSIC_HELPER_NEXT_DIR", filesDir.absolutePath, true)`。这条路在源码里是设计好的公开回退（`lib.rs:101-105`），二进制里符号与字符串都在，API 级别也够。

**但它是「可行」不是「等价」**，有两个必须写清的时序陷阱（源码可证）：

- **凭据目录是每次调用重新求值的**：`store()`（`src/api.rs:24-26`）每次 `CredentialStore::for_directory(&data_directory())`，而 `CONFIGURATION` 永不写入 ⇒ 每次都重读环境变量。
- **设备身份与 aria2 目录是首次构造时冻结的**：`upstream()`（`src/api.rs:19-22`）= `static INSTANCE: OnceLock<Upstream>`，`Upstream::new()` → `DeviceStore::for_directory(&crate::data_directory())`（`src/upstream.rs:120`）与 `Aria2::new(crate::data_directory())`（`src/upstream.rs:135`）。
  ⇒ 环境变量设晚了会出现「凭据落到新目录、`device.json` 留在 `"."`」的劈叉：`device.json` 写不进去（Android 进程 cwd 通常是 `/`，不可写）→ 设备身份每次启动重新生成（`src/device.rs:169-177` 读不到就新建再存）。

**所以我的核实结论是：** 提问描述的事实（Kotlin 无 configure 入口、只认 configure/环境变量/`.`）**全部成立**；但由它推出的「Android 上没有办法设，必须改组件」**不成立**——有 `Os.setenv` 这条可用（虽然绕）的路。「需要改组件或改上游」只在**不想依赖进程级副作用**、或**必须在首次调用后才决定目录**时才成立。

### verdict: **confirmed**（机制事实）/ 由它推出的「Android 必须改组件」**refuted**

---

## 2. 体积与页面大小

### 核实过程与证据

**① 两份 `.so` 的 LOAD 对齐，逐个 ABI 用 `llvm-readelf -lW` 复核（不是抽样）：**

```
$ for abi in arm64-v8a armeabi-v7a x86 x86_64; do llvm-readelf -lW .../$abi/libqqmusic_api_helper_next.so | awk '/^  LOAD/{print $NF}'; done

### /tmp/helpernext_probe            （未加链接参数）
  arm64-v8a    0x1000 0x1000 0x1000 0x1000
  armeabi-v7a  0x1000 0x1000 0x1000 0x1000
  x86          0x1000 0x1000 0x1000 0x1000
  x86_64       0x1000 0x1000 0x1000 0x1000

### /tmp/helpernext_pack_rehearsal/repo   （加了 -Wl,-z,max-page-size=16384）
  arm64-v8a    0x4000 0x4000 0x4000 0x4000
  armeabi-v7a  0x4000 0x4000 0x4000 0x4000
  x86          0x4000 0x4000 0x4000 0x4000
  x86_64       0x4000 0x4000 0x4000 0x4000
```
即 4 KB ↔ 16 KB，**提问的两个数字都对**，且是 4/4 ABI 全中。两棵树的 `boltffi.toml` 唯一差异确实就是这一行：
```
$ diff boltffi.toml /tmp/helpernext_pack_rehearsal/repo/boltffi.toml
- extra_args = []
+ extra_args = ["-Wl,-z,max-page-size=16384"]
```
该参数透传路径：`boltffi_cli-0.31.0/src/pack/android/link.rs:322-349` 的 `android_shared_link_args()` 末尾 `args.extend(extra_args…)`。

**② 「4 个 jniLibs 合计约 38MB」—— 对 rehearsal 那份成立，但对 probe 那份差了 5 倍。**

| ABI | probe（字节） | rehearsal（字节） |
|---|---:|---:|
| arm64-v8a | 55,567,392 | 10,720,072 |
| armeabi-v7a | 48,059,680 | 8,662,608 |
| x86 | 51,885,880 | 9,912,900 |
| x86_64 | 56,022,216 | 10,597,808 |
| **合计** | **211,535,168 B = 201.7 MiB** | **39,893,388 B = 38.05 MiB / 39.89 MB** |

`du -sh dist/android`：probe `202M`，rehearsal `38M`。

**③ 差 5 倍的原因我查清了，不是页面大小，是「打包时用的 cargo profile 不同」。** 这一点是本复核里最实质的补充：

```
$ ls target/boltffi/android/aarch64-linux-android/
probe:      debug          ← boltffi 的 scratch 目录名 = 本次打包用的 profile
rehearsal:  release

$ find target/aarch64-linux-android/ -maxdepth 1 -type d
probe:      CACHEDIR.TAG  debug  release   （debug/ 是最后打包时建的时间戳 21:21）
rehearsal:  CACHEDIR.TAG  release

$ stat -f "%Sm" -t "%H:%M:%S" target/aarch64-linux-android/release/qqmusic-helper-next
21:45:20
$ stat -f "%Sm" -t "%H:%M:%S" target/aarch64-linux-android/release/libqqmusic_api_helper_next.{so,a}
22:41:35      ← 打包时重新编译（timestamp 与 dist 产物 22:41:36 相接）
```
两棵树源码与 Cargo.toml 逐字节相同（`diff -rq src/ ...` 无输出；`diff Cargo.toml` 无输出），且 **release 静态库 md5 相同**（`e5ed31f815f269071c6edfdc352b316e`）。所以 probe 那份 201 MiB 是 **debug-profile 的打包产物**，rehearsal 那份 38 MiB 是 **release-profile 的打包产物**（`boltffi pack android` 的 `--release` 是**可选 flag**，`boltffi_cli/src/cli.rs:236-243` 的 `Android { #[arg(long)] release: bool, … }`，默认 false ⇒ debug；profile 决议见 `pack/android/mod.rs:56-57` → `build::resolve_build_profile`）。

**④ 38 MiB 的构成（这条指控对 release 产物也成立）：`.so` 是 `with debug_info, not stripped`。**
```
$ file .../rehearsal/.../libqqmusic_api_helper_next.so
ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, with debug_info, not stripped

$ llvm-readelf -S <arm64 .so> | awk '$2 ~ /\.debug_|\.symtab|\.strtab/ {sum+=$6} END{}'  →  7,280,642 B = 6.94 MiB
（.debug_info 0x172611 + .debug_str 0x1e4a2a + .debug_ranges 0x10d10d + .debug_line 0xc212d + … + .symtab 0xad640 + .strtab 0xdefe4）
文件总大小 10,720,072 B  →  **65% 是调试信息/符号表**
```
机制上说得通：`Cargo.toml` 末段虽有 `[profile.release] strip = true`，但 boltffi 打包是**第二步**——用 NDK clang 把 `jni_glue.o` 与 staticlib 重新链接（`link.rs:322-349` 的参数里**没有 `-s`/`--strip-debug`**），`[targets.android.debug_symbols] enabled = false` 只控制**是否额外汇出符号归档**（`link.rs:233-241`），不控制 strip。

### verdict: **confirmed**（38MB、0x1000/0x4000 两个数字与补丁效果全部核实无误；4/4 ABI）

**补充事实（不是提问要求，但影响结论的普适性）：** 201 MiB 那份是 debug profile 的产物，**38 MiB 不是「pack android 的必然产出」而是「`--release` 下的产出」**。表述时若不写 `--release`，读者会以为另一个数字是错的。

---

## 3. 子进程模式在 Android 上是否可行

### 核实过程与证据

**① 组件确实提供了子进程适配器，且它是个**真正的 Android ELF 可执行文件**：**
```
$ file target/aarch64-linux-android/release/qqmusic-helper-next
ELF 64-bit LSB pie executable, ARM aarch64, version 1 (SYSV), dynamically linked,
interpreter /system/bin/linker64, stripped
$ llvm-readelf -l …/qqmusic-helper-next | grep INTERP
      [Requesting program interpreter: /system/bin/linker64]
$ llvm-readelf -d …/qqmusic-helper-next | grep NEEDED
   libdl.so / libc.so      （只依赖 bionic，2,766,064 B — 与两份拷贝一致）
```
交叉编译目标里**确实产出了它能跑的可执行文件**，不是只产出 `.so`。

**② 但 `pack android` 的产物里没有它。**
```
$ find dist/android -type f
dist/android/include/qqmusic_api_helper_next.h
dist/android/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libqqmusic_api_helper_next.so
dist/android/kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt
dist/android/kotlin/jni/{qqmusic_jni_glue.c, qqmusic_api_helper_next.h}
   → 8 个文件，**0 个可执行文件**（`find dist -type f ! -name "*.so" ! -name "*.kt" ! -name "*.h" ! -name "*.c" | wc -l` → 0）
$ grep -rn "aria2\|exec\|bin/" boltffi.toml
   → 无命中（`targets.android` 段落只有 output/min_sdk/kotlin/header/pack/link/debug_symbols）
```
机制上：boltffi 对每个 target 走的是 `--lib`（`boltffi_cli/src/build.rs:306` 的 `command.arg("--lib")`），**不构建 `[[bin]]`**。所以那个 `qqmusic-helper-next` 是**碰巧同 target 目录里的遗留物**（来自更早的 `cargo build --target aarch64-linux-android`），**不在 `pack android` 的交付路径上**。子进程适配器的源码 `src/bin/stdio.rs` 也不进 `.so`。

**③ Android 平台层面的策略（这部分我引的是官方文档原文，不是推断）：**

Android 10（API 29）行为变更，`https://developer.android.google.cn/about/versions/10/behavior-changes-10`，小节 **"Removed execute permission for app home directory"**：
> "Execution of files from the writable app home directory is a W^X violation. **Apps should load only the binary code that's embedded within an app's APK file.** Untrusted apps that target Android 10 cannot invoke `execve()` directly on files within the app's home directory."
（中文版同节：「移除了应用主目录的执行权限 … 应用应该仅加载嵌入到应用的 APK 文件中的二进制代码。以 Android 10 为目标平台的不可信应用无法再直接针对应用主目录中的文件调用 `execve()`。」）

本工程 `targetSdk = 35`（`app/build.gradle.kts:14`），远在 29 之上 ⇒ 该规则**对本工程生效**。把二进制解到 `filesDir`（= app home directory）再 `execve` **不可行**。

**④ 「解释到 `nativeLibraryDir` 再 exec」这条路我没有在设备上验证，只查到间接证据：**
- 一个 APK 的 `nativeLibraryDir` 里**可以有非 `.so` 的可执行文件**吗？我搜到的最接近的官方线索是 NDK 的 `wrap.sh` 文档（`https://developer.android.google.cn/ndk/guides/wrap-script`），它明确说「Android Studio 仅打包 `lib/` 目录中的 `.so` 文件」，例外的 `wrap.sh` 需要 `useLegacyPackaging=true` 且只能在**可调试** APK 上用。这条**不能**证明任意可执行文件能塞进 `jniLibs` 并被 `execve`。
- 我**没有**找到「把可执行文件改名放进 `jniLibs` 然后 exec」的官方支持表述，也**没有**在本次会话里跑过任何 Android 设备验证。
- **因此我把「nativeLibraryDir 里 exec 另一个可执行文件」标为 `unclear`（未验证）**，而不是当成可行或不可行。`Os.execv/execve` 本身是公开 API（`javap android.system.Os` 有 `public static void execv/execve`），**但这只说明调用得出去，不说明内核/策略会放行**。

### verdict: **confirmed**（对「Android 上把组件当子进程驱动」这一条的**否定**结论）

- 组件**没有把可执行文件交付给 Android 宿主**（`pack android` 不含它、boltffi 只构建 `--lib`）—— 这是**代码/产物可证的**，不依赖平台策略。
- 即使自己交叉编译出那份可执行文件，把它放到 app home directory（`filesDir`）再 exec **被 Android 10+ 的 W^X 明令禁止**，本工程 `targetSdk=35` 适用 —— 文档原文可证。
- **⇒ 结论成立：Android 必须走 FFI/JNI。**（例外路径「解到 `nativeLibraryDir` 再 exec」我未能验证，标 `unclear`；但它需要宿主自定义打包、放弃 `pack android` 的现成交付形态，已经超出「把组件当子进程驱动」的命题范围。）

---

## 4. 限流与熔断：进程内存态 + 宿主每次重推

### 核实过程与证据

**① 配置确实是**纯内存态**，组件二进制里没有任何持久化路径。**
```
$ grep -n "data_dir\|data_directory\|File::\|fs::\|save\|load\|persist" src/guard.rs
（无输出 —— guard.rs 完全不碰文件系统）
```
`RateLimit` 字段 = `window: Duration / windows: Mutex<HashMap<Class,Window>> / global: Mutex<GlobalWindow>`（`src/guard.rs:91-95`）；`CircuitBreaker` = `config: Mutex<BreakerConfig> / failures: Mutex<Vec<Instant>> / state: Mutex<BreakerState>`（`src/guard.rs:241-247`）。**没有 serde、没有路径、没有落盘**。进程退出即全部丢失。

**② 默认值（未推时生效）：**
```
RateLimitConfig::default()  = { enabled: true, window: 10s, max_calls: 100 }   （guard.rs:73-81）
BreakerConfig::default()    = { enabled: true, failure_threshold: 5,
                                failure_window: 60s, open_for: 30s }          （guard.rs:222-232）
Class::budget() 每类：Read 30 / Interactive 12 / Playback 20 / Account 12 / Write 6   （guard.rs:42-50）
```

**③ 语义是「等待，不丢弃」（这是与工程现有节奏闸最关键的差异）：**
`RateLimit::acquire()` 注释原文「Calls over the limit **wait (they are not dropped)**」（`guard.rs:13`）；实现里超限时算出 `wait` 后 `std::thread::sleep(duration.min(2s))` 并 `continue` 重试（`guard.rs:189`）。窗口 10s、类预算与全局预算取 `max`。

**④ `set_rate_limit` / `set_breaker` 的**设计意图**就是「每次启动重推」：**
`src/api.rs:481-482` 原文「A configuration push rather than a read: the app sends it on startup and whenever the settings change, so the numbers survive a component restart.」
`configure_rate_limit` 的 doc「The app sends it on startup and whenever the numbers change」（`methods.rs:464-467`）。

**⑤ ⚠️ 但它们在 FFI 路径上是**死的**——这是本复核最重要的反例。**
```
$ grep -n -A80 "fn dispatch" src/methods.rs | grep -E "set_rate_limit|set_breaker"
（无命中）
$ sed -n '394,460p' src/methods.rs   ← match method 的分支：get_helper_info / get_login_status /
    get_status / import_cookies / logout / fetch_liked_songs / fetch_liked_albums /
    fetch_user_playlists / fetch_followed_artists / fetch_playlist_tracks
src/methods.rs:458:  other => Err(UpstreamError::Upstream(format!("不支持的方法：{other}"))),
```
而 `api.rs` 的 `set_rate_limit`（`:484-497`）与 `set_breaker`（`:501-516`）走的是 `call("set_rate_limit", …)` → `api.rs:31` 的 `methods::dispatch(...)`。**`dispatch` 没有这两个分支** ⇒ 一律落进 `other` 分支，返回 `Upstream("不支持的方法：set_rate_limit")`，再经 `From<UpstreamError> for HelperError` 变成 `HelperError::Upstream`（`lib.rs:141-149`）。
这两个函数**没有** `#[export]` 以外的实现路径，`configure_rate_limit`（`methods.rs:468`）/`configure_breaker`（`methods.rs:508`）只被 `src/bin/stdio.rs:149/152` 调用 —— 即**只有子进程路径能用**。

**⑥ 「替代了工程现有 120ms 节奏闸的哪部分」——数据源在工程侧：**
`app/src/main/java/com/neumusic/player/data/api/QqCore.kt:73-90`：
```kotlin
/** 全局请求节奏闸：相邻两次请求的**发出时刻**至少间隔 [MIN_INTERVAL_MS]。只闸"起跑"，不串行"跑步"… */
private const val MIN_INTERVAL_MS = 120L
```
相位上二者完全不同：

| | 工程 120ms 闸 | 组件 RateLimit |
|---|---|---|
| 判据 | **相邻两请求的最小时间间隔**（`elapsedRealtime() - lastCallAt`，起跑线串行化） | **窗口内计数**（10s 内 ≤100 次 + 每类预算） |
| 粒度 | 单一全局 | 5 个 Class（Read/Interactive/Playback/Account/Write） |
| 超限行为 | `delay(gap)` 后放行（也是等待） | `sleep` 后重试（也是等待，上限 2s/轮） |
| 跨进程/跨重启 | 内存（`lastCallAt` 在对象里） | 内存（组件进程/实例内） |
| 工程里有没有对应物 | 有（这是被替代的那部分） | **工程里没有熔断层**：`grep -rn "熔断\|breaker\|Breaker" app/src/main/java/` 无命中 |

⇒ **能说得实的替代关系是：** 组件 RateLimit 覆盖了工程 120ms 闸**没做的那部分**——**按类别分桶 + 窗口总量上限**；但 120ms 的**「最小间隔」语义组件不提供**（10s/100 次 ≈ 平均 100ms 一次，比 120ms 松；且组件是**计数窗口**不是**间隔**，短时突发在窗口额度内可以直接连发）。工程侧那部分仍然只能靠保留 `QqCore.kt` 的闸，或改推 `set_rate_limit` 到**更紧的窗口参数**（但见 ⑤，FFI 推不动）。
熔断层则是**净新增**能力，工程里原本没有。

### verdict: **confirmed**（内存态 / 需重推 / 与 120ms 闸的关系都成立）

**但必须并列的反例（提问未提，且直接决定可行性）：** 「宿主每次拉起都要重推 `set_rate_limit` / `set_breaker`」这个动作**在 FFI 路径上当前做不到** —— 两个函数经 `dispatch` 一律返回 `HelperError.Upstream("不支持的方法：…")`。所以「限流熔断配置」目前**是组件内置默认值（enabled=true / 10s / 100 / 阈值 5 / 60s / 30s）在起作用，宿主无法调**，除非先改组件把这两个方法接进 `dispatch`。这与 `helpernext-mechanics.md:459` 的记述一致，本次复核对 `dispatch` 源码独立复核后确认。

---

## 5. BoltFFI 已知限制

### 5a. 「多语句 wire writer 会让绑定生成跳过函数」

**机制的注释证据：**
```
$ grep -rn "multi-statement wire writer" ~/.cargo/registry/src/…/boltffi_backend-0.31.0/src/
…/target/kotlin/codec/write.rs:225:   _ => Err(KotlinHost::unsupported("multi-statement wire writer")),
…/target/java/codec/write.rs:202:     _ => Err(JavaHost::unsupported("multi-statement wire writer")),
…/target/python/codec/write.rs:49:     shape: "multi-statement wire writer",
…/target/swift/codec/write.rs:91:      _ => Err(SwiftHost::unsupported("multi-statement codec write")),
```
`boltffi_cli/src/commands/generate/bindings.rs:846-873` 的 `print_coverage()`：有 unsupported 就打印 `kind / name / reason` 表（`:852-853` 的表头正是 `kind name reason`），`deny_skipped` 为真时再报错退出。
**⇒ 机制存在，且提问引述的那段输出格式与 `print_coverage` 的实现完全对得上。**

**给接入方的实操细节（提问没问，但能防住这个问题）：**
- `boltffi` 有全局 flag `--deny-skipped`（`cli.rs:151-156`，help 原文 "Fail instead of emitting a binding with declarations left out"），把它打开可让「有函数被跳过」直接变成构建失败，而不是只在 stderr 打一张表。
- `pack android` **默认就会重新生成绑定**：`--regenerate` 的 `default_value = "true"`（`cli.rs:144-150`），对应 `pack/android/mod.rs:83-105` 的 `options.execution.regenerate` 分支会重跑 `GenerateTarget::Kotlin` + `GenerateTarget::Header`。所以改了包名/min_sdk 后正常跑一次 `pack android` 就够了，不需要单独先 `generate`。

**三元组计数（提问要求的那项核对）：**
```
$ grep -c "#\[export\]" src/api.rs            → 47
$ (从 api.rs 抽 pub fn 名) | sort             → 47 个
$ grep -oE "boltffi_function_…_[a-z0-9_]+" dist/android/include/qqmusic_api_helper_next.h | sort -u | wc -l   → 47
$ grep -oE "external fun boltffi_function_…_[a-z0-9_]+" QqmusicApiHelperNext.kt | sort -u | wc -l            → 47
$ grep -cE "^fun [a-zA-Z]" QqmusicApiHelperNext.kt                                                          → 47
$ comm -3 归一名后的三份清单  → 空（完全一致）
```
**⇒ 当前确实没有函数被跳过。** 47 = 47 = 47 = 47。

**并且「当前没有触发」这个状态是**有源码依据的**，不是碰巧：**
`src/api.rs:62-72` 的注释记着当初的收窄：「A `Vec<(String, String)>` … 是完整的登录，所以签名只收这两个字段而不是一个 cookie jar」；`:152-157` 的测试别名表把协议名 `import_cookies` 映射到 wrapper `import_credential`。
```
$ grep -rn "Vec<(String, String)>\|Vec<(String,String)>" src/
src/api.rs:66:  ← 只出现在**注释**里，不是签名
```
即：那个会触发 skip 的形状**已被从签名里移除**，现在 47 个导出里没有 `Vec<(String,String)>`。

### 5b. 「生成的包名与 min_sdk 需要在 `boltffi.toml` 里改」

**当前值（两棵树相同）：**
```
$ grep -n "package\|min_sdk" boltffi.toml
8:[package]                      ← 段名，不是包名
29:skip_package_swift = false    ← Apple
40:min_sdk = 24                  ← [targets.android]
43:package = "com.example.qqmusic_api_helper_next"   ← [targets.android.kotlin]
98:min_sdk = 24                  ← [targets.java.android]
```
**落到产物里的可核实痕迹：**
- 包名 → `QqmusicApiHelperNext.kt:3` 的 `package com.example.qqmusic_api_helper_next` 与**目录层级** `dist/android/kotlin/com/example/qqmusic_api_helper_next/`；`jni_glue.c:44` 的 `FindClass(env, "com/example/qqmusic_api_helper_next/BoltFfiErrorBufferException")` —— 这个出现在 **JNI 胶水 C 里**，编译进 `.so`，所以包名一改必须**同时重新链接**（`boltffi pack android` 默认 `--regenerate=true` 会重生成 Kotlin + 头 + 胶水，`pack/android/mod.rs:83-105`）。
- min_sdk → 直接进 `AndroidToolchain::discover(config.android_min_sdk(), …)`（`pack/android/link.rs:81`、`build.rs:173-177`），再 `clang_for_abi(abi, min_sdk)` 拼出 `aarch64-linux-android24-clang`（`toolchain/android.rs:150-153`）。**产物里能验证该值真的被用上了**：`.note.android.ident` 的第 1 个 `u32`（`android_api_level`）= **24**，4/4 ABI、两棵树都是 24：
```
$ python3 解 .note.android.ident（namesz/descsz 后按 <I 读）
probe:      arm64-v8a (24, 'r27d', '13750724')  armeabi-v7a (24,…)  x86 (24,…)  x86_64 (24,…)
rehearsal:  同上，全部 24
```
**⇒ 「包名与 min_sdk 要在 `boltffi.toml` 里改」成立。** 补充两点精确化：
1. `min_sdk = 24` 是 **boltffi 的默认值**（`config/targets/kotlin.rs:146-148` 的 `default_android_min_sdk() { 24 }`），不是必须显式写的；现成值 24 **低于**本工程的 `minSdk = 26`（`app/build.gradle.kts:13`）—— 对接时把 24 提到 26 可以避免链接到用不上的低版本 libc stub。
2. `min_sdk` **不改 Rust target triple**（triple 仍是 `aarch64-linux-android` 等，见 `target.rs` 与四份产物目录名），它只影响链接器选择与 `.note.android.ident` 的 api_level 字段。

### verdict: **confirmed**（5a 与 5b 都成立）

---

## 汇总

| # | 结论 | verdict |
|---|---|---|
| 0 | GPL-3.0-or-later（Cargo.toml + LICENSE 全文） | **confirmed**；提问里「AGENTS.md 写明 GPL/AGPL 一律不引入」**refuted**（原文是 `:81` 放行「可以参阅、乃至照搬」，`:82` 明说旧红线已失效） |
| 1 | 生成的 Kotlin 绑定无 `configure`/数据目录入口；只认 configure（未导出）/ 环境变量 / `.` | **confirmed**；由它推出的「Android 上没有可行方式、必须改组件」**refuted** —— `Os.setenv`（API 21+、`minSdk=26` 可用）是可行（虽绕）的第三条路，但有「首次调用前必须设好」的时序陷阱 |
| 2 | 38MB / 0x1000 → 0x4000（加 `-Wl,-z,max-page-size=16384`） | **confirmed**（4/4 ABI 复核）；补充：201 MiB vs 38 MiB 的差异来自 **debug vs release profile**（boltffi scratch 目录名 + 编译时间戳 + 相同静态库 md5 三重证据），不是页面大小；38 MiB 里 65% 是调试信息 |
| 3 | Android 上不能把组件当子进程驱动 ⇒ 必须 FFI/JNI | **confirmed**（产物不含可执行文件 + Android 10 W^X 文档原文 + `targetSdk=35`）；「解到 nativeLibraryDir 再 exec」这一例外路径 **unclear（未验证）** |
| 4 | 限流/熔断是进程内存态、宿主每次要重推；对照工程 120ms 闸 | **confirmed**；并列反例：`set_rate_limit`/`set_breaker` 在 FFI 路径经 `dispatch` 一律返回「不支持的方法」**（推不动）** |
| 5 | 多语句 wire writer 会跳过函数；当前无跳过；包名/min_sdk 在 boltffi.toml 改 | **confirmed**（机制有源码、47=47=47 无跳过、`.note.android.ident` api_level=24 可验 min_sdk 生效） |

### 本次未验证 / 无法验证的事（不填补猜值）

1. **没有跑任何构建**（约束）。所有产物结论都是读已存在的 `/tmp` 产物 + 读 boltffi 0.31.0 源码配置逻辑得出的。
2. **没有在 Android 设备/模拟器上跑过任何东西**（约束：不许碰 adb/模拟器）。第 3 条的「nativeLibraryDir 里 exec 另一个可执行文件能否成」纯粹是**未验证**，我只把它标 unclear，没有替它编结论。
3. **许可的法律性质不结论。** 我只列事实与两种解读；`README`/`LICENSE` 里**没有**任何链接例外或商业授权条款，作者意图无从核实。
4. **`pack android` 默认 profile 是 debug 这件事**是从 `cli.rs:236-243`（`--release` 是可选 flag、无 `default_value`）与 probe 的 scratch 目录名 `debug/` 推出来的；我**没有**执行 `boltffi pack --help` 或用 `--no-build` 复现，所以「默认 debug」是**推断**，「probe 那次用了 debug / rehearsal 那次用了 release」是**证据**。
5. **probe 那份 debug 产物的来源**未完全确定：我查不到与 55,567,392 字节逐字节相同的中间产物（`find -size` 无命中），只能从 `target/boltffi/…/debug/` 与 `libqqmusic_api_helper_next.a`(release) 的关系推断它是「debug profile 下、用 release 静态库 + 加 `-g` 编译的 `jni_glue.o`」重新链接的结果。这不影响「38 MiB 是 release」的结论，但「probe 那份为什么恰好 55 MB」我没有闭合。

### 复核用到的主要命令（全部只读）

```bash
# ELF 检查（PATH 里没有 readelf，用 NDK 自带的）
RE=~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf
$RE -lW   <so>            # LOAD 段 Align（4/4 ABI，两棵树）
$RE -S    <so>            # .debug_* / .symtab / .strtab 体积
$RE --dyn-syms <so>       # 47 个 boltffi_function_*；无 configure/data_dir
$RE -h/-l/-d ./qqmusic-helper-next   # DYN / PIE / INTERP=/system/bin/linker64 / NEEDED
~/…/llvm-nm -D --undefined-only <so> | grep getenv    # → U getenv@LIBC
strings -a <so> | grep QQMUSIC_HELPER_NEXT_DIR         # → 1

# 计数三元组
grep -c "#\[export\]" src/api.rs                                        # 47
grep -oE "boltffi_function_…_[a-z0-9_]+" dist/…/qqmusic_api_helper_next.h | sort -u | wc -l   # 47
grep -oE "external fun boltffi_function_…" QqmusicApiHelperNext.kt | sort -u | wc -l          # 47
grep -cE "^fun [a-zA-Z]" QqmusicApiHelperNext.kt                                             # 47

# Kotlin/头文件的 configure 缺席
grep -niE "configure|datadir|data_dir|setenv|getenv|QQMUSIC" QqmusicApiHelperNext.kt
grep -nE "configure|data_dir|dataDir|QQMUSIC|env" dist/android/include/qqmusic_api_helper_next.h

# Android 平台 API 与策略
$JAVA_HOME/bin/javap -classpath ~/Library/Android/sdk/platforms/android-37.0/android.jar android.system.Os
python3 查 platforms/android-37.0/data/api-versions.xml（Os 类 since=21）
curl -sL https://developer.android.google.cn/about/versions/10/behavior-changes-10      # W^X 小节
curl -sL https://developer.android.google.cn/ndk/guides/wrap-script                     # lib/ 只打 .so

# 解 .note.android.ident 取 api_level（自写 python，只读）
python3 - <<'EOF'  # 见正文 §5b，读出 (24, 'r27d', '13750724')
EOF
```
