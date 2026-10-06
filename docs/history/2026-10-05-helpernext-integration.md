# NeuMusic 接入 HelperNext：旧报告复核与实施记录

原报告：`.zcode/research/HELPERNEXT_INTEGRATION.md`，2026-10-03，HelperNext 基准 `2ff7e71`。
本次开始前已提交并推送：HelperNext `7ce4a64`；NeuMusic `13cad5e`。

旧报告的“47 个 FFI 导出中 31 个不可用”已过时：返回外壳解包、限流/熔断/aria2 分发已修复；
收藏与关注的 port 接口已与 Android 原端点对应。六档音质已存在于 `resolve_song_urls`，旧四档 `resolve_song_url` 不是全部能力。
还需处理真实曲目 metadata、精确 offset/total/order、数字 ID 写入回执、歌词持续时间/roma，以及 Android 原电台无限流端点。

## 逐项对照（保留原报告的 42 行计数口径）

表中的开始时状态基于 `7ce4a64`；本次实施已完成，验证结果见文末。昵称与曲目字段等重叠行不代表不同端点。

| 原能力 | 原判定 | 本次开始时 | 本次处理 |
|---|---|---|---|
| 搜索·歌曲（含分页） | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 搜索·歌手 | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 搜索·专辑 | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 搜索·歌单 | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 曲目字段合集（fileSizes / genre / mediaMid） | gap | 仍缺字段 | Rust 曲目模型与解码补齐，Android 映射 |
| 我喜欢列表（分页+总数） | partial | 读取存在，需保持精确偏移/总数 | 新增 total/nextOffset 歌曲页；以原始行数推进，过滤不可用歌曲后仍不漏页 |
| 我喜欢总数（主页卡片） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 收藏的歌单 | partial | 新增 port 同端点列表已实现 | 直接接 port 列表，保留分页/数量/简介 |
| 收藏的专辑 | partial | 新增 port 同端点列表已实现 | 直接接 port 列表，保留分页/数量/简介 |
| 歌单内曲目 | partial | 读取存在，需保持精确偏移/总数 | 新增 total/nextOffset 歌曲页；以原始行数推进，过滤不可用歌曲后仍不漏页 |
| 专辑内曲目 | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 歌手解析（名字 → mid/pic/计数） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 歌手歌曲（分页/排序/总数） | partial | 旧接口丢总数/页内排序 | 新增精确 offset/limit 分页、上游 hot/latest 全局排序与批量专辑歌数 |
| 歌手专辑（分页/总数/每张歌数） | partial | 旧接口丢总数/页内排序 | 新增精确 offset/limit 分页、上游 hot/latest 全局排序与批量专辑歌数 |
| 歌手资料与计数 | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 关注的歌手 | partial | 新增 port 同端点列表已实现 | 直接接 port 列表，保留分页/数量/简介 |
| 电台分组 | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 电台曲目（无限流） | partial | 分组已实现，旋转端点有差异 | 接原 get_radio_track；宿主保留多批/排重/全部失败抛错 |
| 猜你喜欢 / 推荐流 | partial | 分组已实现，旋转端点有差异 | 接原 get_radio_track；宿主保留多批/排重/全部失败抛错 |
| 昵称（与 §4.2 同名行是**同一能力的重复记录**，总量按 1 项计） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 播放直链 + 音质档位降级 | partial | port 六档音质和逐项 result 已实现 | 用 resolve_song_urls 保留降级顺序/格式筛选/错误码 |
| 音质档位定义（6 档、前缀+扩展名成对） | partial | port 六档音质和逐项 result 已实现 | 用 resolve_song_urls 保留降级顺序/格式筛选/错误码 |
| 播放失败人话文案（1000/104003/101404/22） | partial | port 六档音质和逐项 result 已实现 | 用 resolve_song_urls 保留降级顺序/格式筛选/错误码 |
| 歌曲详情/简介（推荐卡介绍框） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 逐字歌词（QRC 卡拉 OK） | partial | QRC 解码已有，roma/持续时间缺失 | 新增毫秒级 QRC/roma 结构；宿主保留 kana 与显示优化 |
| 翻译（「译」开关） | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 音译（「音」/roma 开关） | gap | QRC 解码已有，roma/持续时间缺失 | 新增毫秒级 QRC/roma 结构；宿主保留 kana 与显示优化 |
| 注音（「注」/kana 开关） | partial | QRC 解码已有，roma/持续时间缺失 | 新增毫秒级 QRC/roma 结构；宿主保留 kana 与显示优化 |
| 喜欢写入（AddSonglist/DelSonglist） | partial | 已测可逆写入，旧 set_liked 回执仍不严格 | 数字歌曲 ID 写入与结构化业务码，失败不自动重试 |
| 我喜欢列表（红心状态/计数） | partial | 读取存在，需保持精确偏移/总数 | 新增 total/nextOffset 歌曲页；以原始行数推进，过滤不可用歌曲后仍不漏页 |
| 封面 URL（模板拼装 + http→https，通知线程直拉） | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 网页 cookie 登录（uin+qm_keyst，euin） | partial | 导入可用，缺 FFI 初始化/完整 euin 导入 | 新增安全初始化与可选 euin 导入，迁移凭据 |
| 扫码登录（QQ） | covered | QQ/微信登录接口已实现 | 保持现有 WebView 登录；不新增或测试扫码流程 |
| 退出登录 | partial | 类型化 logout 正常，raw logout 未删文件 | 只调类型化 logout，同时清宿主缓存 |
| 昵称（与 §4.1 同名行是**同一能力的重复记录**，总量按 1 项计） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 下载（取直链→拉流→写公共目录） | partial | 直链能力已实现；aria2 不替代 Android 存储 | 组件提供六档直链，Android 保留 MediaStore/台账/命名 |
| 已下载台账/列表置灰 | gap | 直链能力已实现；aria2 不替代 Android 存储 | 组件提供六档直链，Android 保留 MediaStore/台账/命名 |
| 曲目字段（songId/mediaMid/singers/isVip/interval） | partial | 字段已在，mediaMid 解码不完整 | Rust 曲目模型与解码补齐，Android 映射 |
| fileSizes（格式弹窗 + 降级过滤） | gap | 仍缺字段 | Rust 曲目模型与解码补齐，Android 映射 |
| genre 曲风码（智能调音） | gap | 仍缺字段 | Rust 曲目模型与解码补齐，Android 映射 |
| 播放条/播放页歌手名 → 歌手页 | partial | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |
| 后台播放通知封面（另一线程直拉 URL） | covered | 已有可用组件路径 | 薄适配保留 UI 语义，缺省字段归一 |

## 接入分工与精简

Android 通过同步 BoltFFI/JNI 接入，阻塞网络调用在 Dispatchers.IO。Rust 使用应用私有 filesDir 初始化；已有 SharedPreferences 凭据成功迁移后删除旧副本。有效文件作为唯一持久凭据来源，账号切换/退出同步清理歌词和宿主账号缓存。

移除 Kotlin 的 QQ 请求信封/comm/设备字段、QRC 密码解码器与 upstream 多形状解析。保留各域薄适配、歌词对齐/注音/优化/渲染、Media3 控制、MediaStore 下载、已下载台账与列表缓存。

Kotlin/JNI 原生库由同次 BoltFFI 打包生成，记录源码和产物 SHA-256；四 ABI 与16KB页对齐共同检查。新增 Rust API 保持原 macOS API 名称与返回外壳，旧 word-LRC 字段仍保留。FFI 版本为 0.2.0，新增模型改变位置编码；Swift/Kotlin 绑定和原生库必须成套更新，不能混用 0.1 产物。现有 macOS 安装未替换。

## 验证与写接口报备

已完成的验证：

- Rust：238 项单元测试、2 项 stdio 集成测试、1 项文档测试通过；7 项需要额外环境的测试按原配置忽略。
- Android JVM：29 项通过，包括元数据/歌词映射、账号缓存失效、分页原始偏移与完整性，以及既有播放器/音效回归。
- Android 真账号只读：5 项通过，覆盖四类搜索、收藏/关注/昵称、完整喜欢分页、歌手热度/最新排序与专辑数量、电台首批/续批、详情、六档音质和日文 QRC/roma/kana。喜欢列表服务器总数 479，原始行数 479，可识别曲目 477；两条缺 MID 的歌曲不影响分页结束判断。
- Android 真账号可逆写：1 项通过，操作前后喜欢列表 ID 集合、服务器总数与原始分页行数一致。
- Android Media3/音效生命周期：1 项通过，涵盖播放、暂停、恢复、音频会话重建、释放及忽略取消的旧请求不会复活播放器。
- Android 实际播放与唱片状态：2 项通过。组件解析真实歌曲直链后，静音 Media3 播放进度正常推进；唱片进度和快速切歌状态断言通过，测试恢复本地设置。
- macOS：最新 Swift 绑定生成、类型检查及真实 FFI 程序通过；验证初始化幂等、已有凭据状态、搜索元数据、喜欢分页、QRC 与 roma。
- Android 打包：四 ABI 全部成功，原生 LOAD 段与 APK 的 16 KB 对齐检查通过。当前源代码、全部产物 SHA-256、基准提交加 source.patch 重建均一致。

本次写接口仅为 `set_liked_by_id`：对原本未喜欢的歌曲 `songId=272125057`、`mid=0013WPvt4fQH2b` 执行 `music.musicasset.PlaylistDetailWrite/AddSonglist` 一次，随后 `DelSonglist` 一次。两次均获成功回执，最终快照确认复原。未执行其他账号写入。

使用用户提供的现有凭据。恢复任务时模拟器旧票据已失效，同账号的最新文件可用，已同步到应用私有目录；未执行登录、扫码或微信登录测试。未查看或生成测试截图。仅在 `emulator-5554`（arm64）运行 Android 验收；其他三 ABI 完成编译和产物检查，未宣称设备运行验证。

分页读取同时处理不可用条目、已知/未知总数、停滞重试和部分页不入完整缓存；账号切换会使在途结果失效。歌手页分别记录歌曲/专辑加载状态，避免切换标签漏加载。

最终 debug APK 已在 emulator-5554 以保留数据方式安装，并启动 MainActivity；前台状态确认正常，未发现 AndroidRuntime 崩溃日志。APK SHA-256：`b18e5d90315c78b6d47ec3e2129c9a86fb92979571339680ba40acc9555bade4`。

本次集成改动保留在两个工作区，尚未提交或推送；上述开始前的基线提交已推送。
