# HELPERNEXT — QQ 能力组件的消费与升级

> `QQMusicApi_HelperNext`（https://github.com/LeeDespo/QQMusicApi_HelperNext ，GPL-3.0-or-later）是 QQ 音乐能力的权威实现：Rust 核心 + BoltFFI，以 Kotlin/JNI/四 ABI native 库形式内嵌进本应用。
> 本文档描述 NeuMusic 作为**消费方**如何供应、升级、调用该组件。
> QQ 协议端点/comm/参数语义归 HelperNext 仓库所有，**不在本仓库复制**；需要协议细节去看组件仓库与其 pinned Release。

## 所有权边界

HelperNext 拥有：上游 module/method/params、请求信封与平台档案（comm）、签名与设备/会话身份、凭据持久化、限流/熔断、上游响应解析、分页协议语义、QRC 取回/解密与组件级歌词模型、播放链接解析与 QQ 特有结果码语义。

NeuMusic 是 **consumer**。宿主需要组件缺失的 QQ 能力时，顺序是：

1. 先改 HelperNext；
2. 在组件侧补测试/文档；
3. 发布新的 HelperNext Release；
4. 更新本仓库的 pin（lock）。

**不要为走捷径在宿主里重实现协议行为。**

## 供应模式（Release vendor）

- 钉住的官方 Release 版本记录在 `app/helpernext.lock.json`。
- 展开的产物集在 `app/helpernext/`：`kotlin/`（generated 绑定 + JNI glue）、`jniLibs/`（四 ABI `.so`）、`manifest.json`（来源与校验和）、`LICENSE`。**这是 generated/vendor 内容。**
- 升级只能走仓库的 HelperNext update 脚本：校验官方 Release 产物 checksum → **整体原子替换** vendor 目录。
- 升级不需要在本仓库内重建 HelperNext 源码。

### 升级完成判据（缺一不可）

```text
lock 版本 = 下载的 Release = vendor manifest = generated Kotlin 绑定 = 全部四个 ABI native 库
```

## vendor 纪律（红线）

- **不得手改**：generated Kotlin 绑定、JNI glue（`jni_glue.c`/`.h`）、native `.so`、generated 组件 manifest。
- **Kotlin 绑定、JNI glue 与四个 ABI 的 native 库是一套原子的版本化产物**：只能成套更新，**不得单独替换其中一份，不得混用不同 HelperNext revision/Release 的文件**（混版本即破坏 JNI 契约，只改 Kotlin 包名或只换一个 `.so` 同样如此）。
- `manifest.json` 记录来源仓库、revision、源码 SHA-256、BoltFFI 版本、minSdk、ABI 列表与每个文件的 SHA-256；`LICENSE` 随套走。
- 组件目标 **minSdk 24**（应用 26）；所有 ELF LOAD 段 **16KB 对齐**。
- `app/helpernext/README.md` 是 vendor 目录内的出处说明，与本文件一致。

## 消费规则（typed-only，迁移目标）

生产代码应使用 generated **typed BoltFFI Kotlin API**。不得新增基于以下内容的路径：

```text
HelperNext.call(...)
callWithPlatform(...)
raw HelperNext 方法串
raw QQ module/method 名
musicu 请求信封
QQ 签名 / g_tk / QIMEI / 设备会话逻辑
raw QQ 上游 JSON 解析
```

需要精确 API 名/类型时，直接查看 `app/helpernext/kotlin/` 下的 generated 绑定。

**过渡措辞**：typed-only 是迁移目标——存量 16 处 raw `HelperNext.call(...)` 域适配调用（`data/api/` 的 UserApi/SingerApi/LyricApi/SongApi/RadioApi/SearchApi/PlaylistApi）按 Phase C/D 退役；**在此之前不得新增同类调用**，只允许维护既有行为。

## 凭据边界（迁移目标）

- 宿主可经 UI/WebView 收集登录值并导入组件；秘密流向是 `登录 UI → 导入组件 → 组件私有存储`。
- **宿主不得读取/依赖 HelperNext 私有凭据文件（`files/HelperNext/Credential/qqmusic-credential.json`）的路径或 JSON schema**；不得为判断登录态解析/返回 `qm_keyst` 等会话秘密；非敏感会话状态用 typed 账号/登录状态接口。
- 存量 `data/api/HelperNext.kt` 的直接读取与一次性旧凭据迁移按 Phase C/D 退役。
- 任何输出/日志/commit 中不得出现凭据票据值。

## 升级脚本

- 现行更新入口：`scripts/update-helpernext.sh`（配合 `scripts/install-helpernext.py`），按 `app/helpernext.lock.json` 钉住的官方 Android Release 资产执行：下载 → sha256 校验 → `install-helpernext.py` 复核归档 manifest（componentVersion/gitCommit 对 lock、ABI 集、逐文件 SHA-256 与必备文件）→ 原子替换 `app/helpernext/`。不在本仓库重建 HelperNext 源码。
- 「成套原子、禁混版本」「不手改生成物」红线照旧适用。

## 组件行为实测结论（2026-10 接入期调研）

以下结论出自接入期调研（原稿归档于 `docs/history/research/HELPERNEXT_INTEGRATION.md`），排障与验收仍适用：

- **网络 FFI 调用同步阻塞**：全局超时以组件源码为准（组件 `docs/ffi.md` 写 12s，源码为 20s）。所有组件调用必须挂 `Dispatchers.IO`，否则 ANR；限流等待同样占着调用线程。
- **组件自检 `api_surface_matches` 只查源码文本里有同名函数，不查载荷可达**——「组件测试全绿」≠ FFI 面可用，验收以真实调用为准。
- **typed 模型字段全为 Option：载荷/信封不匹配时静默全 `None`**——调用成功但数据全空，比报错更危险；空结果先怀疑信封不匹配。
- **`get_helper_info` 的 `credentialDir` 恒为 `true`**：是给子进程 JSON 消费者的占位布尔，不是「凭据目录已配置」的判据。
- **`call_with_platform` 是发原始 `musicu.fcg` 请求的逃生口**（返回原始 JSON 字符串）：仅限排障/兜底；生产路径按上文消费规则禁入。
- **组件有 `fetch_recommend_feed` 专用端点**（猜你喜欢，每次 5 首，不依赖标题）；宿主现行「电台列表按标题匹配『猜你喜欢』」可由它替换。
- **`RecommendStore` 落盘恢复的 Track 无 `fileSizes`**：冷启动推荐曲目按 `SongApi.chain` 的空 fileSizes 行为走全档位探测。

## 边界自检（收尾检查）

涉及 HelperNext 集成的改动收尾前确认生产代码没有引入：

```text
HelperNext.call(
callWithPlatform(
HelperNext 私有凭据路径
QQ 上游 module/method 信封
QQ 签名/设备/会话实现
```

`app/helpernext/` 下 generated 代码除外。
