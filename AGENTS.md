# AGENTS.md — NeuMusic

> 仓库级工作规则：定义 NeuMusic 的角色、边界、不变量、事实来源与验证路线。
> 通用工作原则见全局 `AGENTS.md`；当前架构与实现细节见 `docs/ARCHITECTURE.md`。

## Repository Role

NeuMusic 是 Kotlin + Jetpack Compose 的新拟物风安卓音乐播放器（在线 QQ 音乐 + 本地音乐），包名 `com.neumusic.player`，以 GPL-3.0 开源。

NeuMusic（宿主）拥有：

- Compose UI 与新拟物设计系统、导航、页面状态与产品行为；
- Media3 / ExoPlayer 播放、队列与播放模式；
- AudioFx / 均衡器 / 可视化；
- MediaStore 下载落位与用户可见的下载台账；
- 应用偏好与缓存；
- 歌词呈现、对齐、注音/音译显示与渲染；
- Android 生命周期、前台服务、通知与平台集成。

QQ 音乐协议实现不归本仓库。

## HelperNext Boundary

`QQMusicApi_HelperNext` 是 QQ 能力的权威组件：Rust 0.2.0 经 BoltFFI/JNI 内嵌执行 QQ 请求/签名/设备档案/凭据存储/QRC 解密。**没有边车、没有 Python 运行时。**

HelperNext 拥有：上游 module/method/params；请求信封与平台档案；签名与设备/会话身份；凭据持久化；限流/熔断；上游响应解析；分页协议语义；QRC 取回/解密与组件级歌词模型；播放链接解析与 QQ 特有结果码语义。

NeuMusic 是 HelperNext 的 **consumer**。生产代码应使用 generated **typed BoltFFI Kotlin API**（迁移目标，见文末「迁移期注意」）。

不得新增基于以下内容的生产路径：

```text
HelperNext.call(...)
callWithPlatform(...)
raw HelperNext 方法串
raw QQ module/method 名
musicu 请求信封
QQ 签名 / g_tk / QIMEI / 设备会话逻辑
raw QQ 上游 JSON 解析
```

宿主需要组件缺失的 QQ 能力时：① 先改 HelperNext；② 在组件侧补测试/文档；③ 发布新 Release；④ 更新本仓库 pin。**不要为走捷径在宿主里重实现协议行为。**

## HelperNext Release Artifacts

NeuMusic 消费钉住的官方 HelperNext Android Release：

- 钉住版本记录在 `app/helpernext.lock.json`；
- 展开的产物集在 `app/helpernext/`（generated/vendor，不手改）。

不得手改 generated Kotlin 绑定、JNI glue、native `.so`、generated 组件 manifest。

**Kotlin 绑定、JNI glue 与全部四个 ABI 的 native 库是同一版本的一套原子产物**——只能通过仓库的 HelperNext update 脚本成套更新；**不得单独替换其中一份，不得混用不同 HelperNext revision/Release 的文件**。组件目标 minSdk 24（应用 26），所有 ELF LOAD 段 16KB 对齐。

供应/升级细节见 `docs/HELPERNEXT.md`。

## Credentials

宿主可经 Android UI/WebView 收集登录值并导入 HelperNext。秘密流向：

```text
登录 UI → 导入组件 → 组件私有存储
```

凭据唯一持久来源是组件管理的 `files/HelperNext/Credential/qqmusic-credential.json`（组件读写，路径与 schema 归组件）。

规则（迁移目标）：

- 宿主不得读取/依赖 HelperNext 私有凭据文件的路径或 JSON schema；
- 不得为判断登录态而解析/返回存储的 `qm_keyst` 等会话秘密；非敏感会话状态用 typed 账号/登录状态接口；
- 任何输出、日志、commit 信息、报告中不得出现 uin/musickey/euin/cookie 的值；
- 账号切换/退出必须清理宿主缓存及其在途结果（`ApiCache` 按 credential 键控、`LikedStore` generation 守卫）；
- 旧凭据只允许一次性迁移导入，成功后删除宿主侧副本。

存量 `data/api/HelperNext.kt` 直接读组件凭据文件，按 Phase C/D 退役。

## App Models

宿主保持并应继续维护自己的产品/UI 模型。数据 API 层允许 `HelperNext typed 模型 → NeuMusic 模型` 的映射（`QqMapper` 模式有效）。不得把宿主模型变成 raw QQ 上游 JSON 的镜像。

## Lyrics

HelperNext 拥有 QQ 歌词取回与 QRC 解密。宿主拥有呈现行为：歌词缓存、翻译对齐、音译对齐、注音映射、视觉时机调整、卡拉 OK 渲染与 Compose 呈现。不要为了减少 Kotlin 代码把产品/显示逻辑搬进 HelperNext。消费侧可执行规则（QRC 字时间、kana token 流、优化策略、渲染硬经验）见 `docs/ARCHITECTURE.md`「歌词」。

## Playback and Downloads

HelperNext 提供 QQ 特有的直链解析。宿主拥有：音质偏好、Media3 播放、队列/历史、MediaStore 目标与命名、用户可见下载台账、Android 存储行为。不要把 MediaStore/产品下载语义搬进组件。若组件接受字符串音质标识，宿主 `Quality` → 组件音质的映射只放在一个适配位置。降级链与结果码的消费侧语义见 `docs/ARCHITECTURE.md`。

## Sources of Truth

```text
README.md              项目简介与 GPL-3.0 协议声明
docs/ARCHITECTURE.md   当前架构、数据消费规则、UI/交互硬规则、随时间光影
docs/HELPERNEXT.md     组件供应/升级/typed 消费规则/所有权边界
docs/TESTING.md        测试分层、设备授权、真实账号安全、验收交付
SPEC_VINYL.md          黑胶唱片机模式规格
```

HelperNext 协议/API 细节的权威在 `QQMusicApi_HelperNext` 仓库与其 pinned Release，不在本仓库副本。历史调查/审计材料在 `docs/history/`。`.zcode/` 研究文件与工作流记录不是现行需求。

## Required Reading

只读与任务相关的部分：

- 架构/所有权改动 → `docs/ARCHITECTURE.md`
- HelperNext API、vendor、登录、组件升级 → `docs/HELPERNEXT.md` + `app/helpernext.lock.json` + `app/helpernext/README.md`；需要精确 API 名/类型时看 generated Kotlin 绑定
- 测试/模拟器验证/真实账号 → `docs/TESTING.md`
- 黑胶 UI → `SPEC_VINYL.md`
- 历史回归/接入调查 → `docs/history/`（仅当现行文档不够时）

## HelperNext Upgrade Rule

升级完成的判据（五处一致）：

```text
lock 版本 = 下载的 Release = vendor manifest = generated Kotlin 绑定 = 全部四个 ABI native 库
```

update 脚本替换 vendor 目录前必须校验官方 Release 产物 checksum。升级不需要在本仓库内重建 HelperNext 源码。

## Live Account Safety

真实账号测试不属于常规单测/构建验证。任何 live 测试前先读 `docs/TESTING.md`：

- live 写必须显式意图（`executeWrites` 门控）、优先可逆操作、核对复原；
- 绝不提交账号凭据或捕获的秘密；
- 不得为证明端点存在而扩大写测试；
- 写失败不自动重试（风控限流会延长），如实呈报。

## Quality Gates

验证强度与任务相称，服从全局规则。Kotlin/应用行为改动的本地基线：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

改动需要 Android 运行时证据时才跑 instrumentation/设备测试。HelperNext live 测试与常规 Gradle 验证分离。

## Repository Hygiene

`AGENTS.md` 是项目文档、应被 git 跟踪。`.zcode/`、`.agents/`、`.claude/`、`.continue/` 等工具本地材料不是仓库现行事实。不在仓库根/生产源码树留临时产物；现行视觉/文档资产放 `docs/assets/`，历史验证证据放 `docs/history/`。

## Release

若 NeuMusic 未来有正式的应用发布流程，细节放 `docs/RELEASING.md` 并从这里引用，不重复。HelperNext 组件的发布规则归 HelperNext 仓库，不归 NeuMusic。

## Invariants

涉及 HelperNext 集成的改动收尾前确认生产代码没有引入：

```text
HelperNext.call(
callWithPlatform(
HelperNext 私有凭据路径
QQ 上游 module/method 信封
QQ 签名/设备/会话实现
```

`app/helpernext/` 下 generated 代码除外。依赖方向永远是：

```text
NeuMusic → typed HelperNext 绑定 → HelperNext → QQ 音乐
```

绝不反向。

---

## 产品规范与工作环境

以下是从既有工程经验提炼的现行操作规则；实现细节与完整清单见 `docs/ARCHITECTURE.md`，历史证据见 `docs/history/`。

### 视觉与交互（新拟物，不可违反）

- **新拟物三原则**：① 不在凸起上再做凸起（例外：播放栏/选择底栏这类**容器级凸起**面板上的独立控件可再凸起；普通内容面板内部保持平）；② 不在凹陷中再做凹陷；③ 凸起母体中以「凹陷」表示选中、以「平」表示未选中。
- **基元白名单**：新拟物控件只用 `shadeSurface`/`shadeInset`/`shadePressable`（`ShadeFusedTabs` 基元保留）；黑胶机械件与音频胶囊使用明确授权的 M3 Expressive 例外；其余页面不引入 Material elevation/ripple。
- **风格分工**：新拟物为主、M3 为辅——元素硬做新拟物会「全是凸起或光影糊成一团」时（细长杆件、机械零件、悬浮小构件）才换 M3；M3 角色色由 `ShadeTheme` 依 `ShadeColors`+强调色推导，不用 M3 基线默认值。
- **`shadePressable` 三点规范**（新控件直接复用）：凸→凹阴影 160ms 交叉插值（绝不瞬间翻转）；凹陷再按内阴影 ×1.45；按下背景向黑混 6%。
- **卡片点击必须 `cardTap`（pointerInput）**——`flatPressable` 的 clip 会截断画框阴影。
- 其余硬规则（歌手画框 `SingerAvatarFrame` 单一来源、飞位转场全套、`singerSlide`、`basicMarquee`、`ShadeSlider` 横向拖动、`TopEdgeFade`、`blockSlice`、底栏 `barTint`、`BottomBarMetrics` 留白、整页 `ShadeDialog`、详情槽 nestedScroll、设置页 6 区）见 `docs/ARCHITECTURE.md`「视觉与交互硬规则」。

### 数据消费要点

- 歌曲页 `nextOffset` 按**原始行偏移**推进，不能按过滤/去重后曲目数或请求 num 推进；无 MID 不可用记录占上游位置；total 与可播数可不同；歌手歌曲与专辑加载状态分别缓存。
- 取数必须由 `LaunchedEffect` 驱动（共享互斥 + `scope.launch` 会卡死/漏加载）；列表字段用 `mutableStateOf` 装；推荐补货挂页面级 scope。
- 喜欢写操作失败**不自动重试**（业务码 1000=限流）；必须用数字 `songId`。

### 播放器行为

- 取链接失败后播放器停在 IDLE：`toggle()` 对「IDLE 或 ENDED 且 current 仍在」走 playCurrent 重解析；自动切换失败经 `PlayerHost.onError` 注入 toast，不能静默。
- `playNext` 用 `pendingNext` 待播队列（随机模式也保证先播）；随机「上一首」按 `playedHistory` 回跳；变速/变调用 `PlaybackParameters`（0.5..2.0）。
- 可视化频段：播放栏/播放页 `VIZ_BARS=40`，从 `VizHost.BARS=16` 段电平降采样；FFT 不可用回退 PCM，动态 State 只在绘制阶段读。
- 黑胶模式：曲目身份按 mid 判断；视觉状态机不接管音频切歌；快速切歌 3 秒窗口仅视觉；不做片尾提前抬臂；唱头半径 0.48D→0.32D。

### 测试、设备与验收

- 黑胶状态机回归 = `VinylStateTest` 经 AndroidJUnitRunner；不要直接跑旧自定义 runner 名（见 `docs/TESTING.md`）。`HelperNextPlaybackTest`=真实直链静音播放、`AudioFxLifecycleTest`=音效生命周期；测试均恢复本地设置。
- **设备只允许 `emulator-5554`，不访问真机**（先 `adb devices` 确认实际名）；禁止 `screencap`、禁止 Read 图片/视频；登录/微信/扫码流程不测。非视觉验证：`uiautomator dump`、接口回读、`shared_prefs` 回读、logcat。
- 真实账号读/可逆写入口在 androidTest 的 `HelperNext*Test`；写测试必须显式 `executeWrites=true` 并核对复原。
- **用户验收交付**：改完必须 构建 → 安装到 `emulator-5554` → 启动 `com.neumusic.player/.MainActivity` 确认前台；保留应用数据。仅构建/提交不算完成交付；失败如实报告。

### 工程纪律

- 大改动（新功能批次/方案替换/回退）开始前先 `git add + commit` 当前状态；批次边界必须有提交。
- 回退不做整树 `git checkout`——先确认未提交区里有哪些其他批次改动，精准回退目标文件的相关改动。

### 环境（本机）

- `export JAVA_HOME=~/tools/jdk-21.0.2.jdk/Contents/Home`。compileSdk 36、targetSdk 35（Android 15+ 边到边）、minSdk 26。
- 依赖走国内镜像（`settings.gradle.kts` 阿里云 + wrapper 腾讯镜像）；镜像拿不到才走代理兜底；GitHub 一律走代理 `http://127.0.0.1:12450`。
- Python 直连 `*.qq.com` 的 TLS 会被掐断；网络探针一律用 `curl`（可带 `-x http://127.0.0.1:12450`），别用 urllib。
- AVD 名 `neu`（arm64）；模拟器会自己掉线/卡死（`adb devices` 变 offline、`adb shell` 挂住）——kill qemu 后重启 AVD。深色模式切换：`adb shell cmd uimode night yes|no`。
- 项目根 `.venv` 仅分析用（qqmusic-api-python），App 不依赖。

### 开源协议与第三方代码（GPL-3.0）

- 本项目 GPL-3.0。**GPL-3 兼容许可（GPL-3.0 / LGPL-3.0 / MIT / Apache-2.0 / AGPL-3.0）的代码可参阅、乃至照搬**：照搬必须保留原版权声明与许可文本、注明来源（仓库+文件+commit/日期）、文件头写清改动；AGPL 组合时保留 AGPL 自身义务并在文件头记清。
- **不与 GPL-3 兼容的许可不搬**（专有/All-Rights-Reserved、CC-NC 等）；拿不准先查 `api.github.com/repos/<owner>/<repo>` 的 `license.spdx_id`。
- 判例：SaltPlayerSource（MIT 但仓库无应用源码）只能当产品形态参考；AMLL（AGPL-3.0、JS/WebGL 栈）协议可组合但技术栈不通，歌词渲染以自研 Kotlin 为主、只借鉴纯算法；均衡器/音效优先平台 audiofx（AOSP，Apache-2.0）+ media3 AudioProcessor 链；Material You 莫奈取色只替换 `ShadeColors.accent`，底色与阴影保持中性。

### 合规

仅供个人学习/自用播放；VIP 内容需用户自己的登录凭据；不批量抓取、不分发；控制请求频率。

## 迁移期注意

- 本批次落地的是**边界规则文档**：上文的 typed-only 消费规则、凭据文件禁读规则与 Release 供应模式自本文件起生效，约束一切**新增**代码。
- typed 迁移本体按后续阶段推进：当前 `data/api/` 仍有 16 处 raw `HelperNext.call(...)` 域适配调用，`data/api/HelperNext.kt` 仍直接读取组件凭据文件（启动迁移与账号态）——**存量按 Phase C/D 退役，退役前按「不得新增、只维护既有行为」对待**。
- `app/helpernext.lock.json` 尚未建立：当前 vendor 由 `scripts/update-helpernext.sh` 从组件源码重建供应；Release 供应（lock → Release 下载 → checksum 校验 → 原子替换）随下一批次切换，切换前「成套原子、禁混版本」红线照旧适用。
- 过渡期发现规则与现状冲突时，以「不扩大存量、不引入新违规」为底线，并在任务报告里如实说明。
