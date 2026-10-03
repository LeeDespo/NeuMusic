# 黑胶唱片模式 · 规格（v3，2026-10-02 —— 已按第二轮评审返工）

**状态**：几何与阶段机已在 `~/Documents/VinylLab`（唱片实验室）实现并**逐条实测**；
主工程 `Music_app` 的播放页**尚未改动** —— 本文件是照着写代码的依据。

> **v3 相对 v2 的返工**（评审 12 条 + 7 个歧义）
> ① 修掉一条**会 ANR 的死循环**（discSwap + 播放暂停）：所有阶段出口一律消费 `pending`，`playPause()` 把 discSwap 归进「播放中」一侧 —— 已复现、已修、已回归。
> ② §4 把「起播三阶段 + 切歌」与「playing + 切歌」**拆成两行**（前者不入 playing、不出声）。
> ③ §5 补齐**机器的对外入口**与主工程 6 个调用点的改法。
> ④ §1 半径公式的符号改正（`+` 号）并注明夹角是 `180° − |α − θp|`。
> ⑤ §2 `drawShadeAt` 的 relief 规则与 `drawShade` 统一（**调用点乘、函数不乘**）。
> ⑥ §2.2 分层写清（静止层 = 底盘 + 沟槽 + 轴孔；旋转层 = 封面圆），沟槽永不转。
> ⑦ §5.6 缩放算术改正 + 定死盘心对齐基准。
> ⑧ §5.4 抑制标志给出**完整 on/off 矩阵**（含兜底关闭）。
> ⑨ §5.7 删除范围改成整段 563–675。
> ⑩ §0 「0.71 D」改正（注明用哪条主轴归一化）。
> ⑪ §6.1 计时阶段列全 7 个（原来把 discAccel 写了两遍）。
> ⑫ §4 行 20「并出声」删除（与 idle = 静音待机 的语义统一）。
> ⑬ 性能节重写：换成健康模拟器上的实测（旧数据来自一台退化到 81 ms 中位帧的模拟器，是假象）。
> ⑭ README 的「五个控制按钮」改成 7 个。

---

## 0. 参考图实测（数据来源）

`/Users/mac/Downloads/5517.jpg_wh860.jpg`（860×573）。脚本：`/tmp/BBox.java`、`/tmp/Final2.java`、
`/tmp/Geo2.java`、`/tmp/Overlay.java`、`/tmp/DeckMock.java`、`/tmp/VinylEdge.java`、`/tmp/LabelScan.java`
（JDK 21 `java X.java` 直跑；本机无 PIL/numpy/Quartz）。

**唱片是椭圆（真实唱机的透视）**。逐列扫上下缘（`BBox.java`）：

```
x=430 : top=32  bottom=539  h=508  mid=285.5    ← 最大高度 = 竖直径 2b
x=410 : top=33  bottom=538  h=506
x=450 : top=33  bottom=538  h=506
x=250 : top=82  bottom=490  h=409               ← 收窄
```

竖直 mid 稳定在 **285.5**；水平 mid 取右缘（左缘 x≈115 起有镜面高光带，不可靠）。

| 量 | 实测 | 归一化用哪条轴 |
|---|---|---|
| 盘心 | **(435, 285.5)** | — |
| 竖直半轴 b | **254 px**（2b = 508，最稳的数字） | — |
| 水平半轴 a | ≈ 308 px（右缘 ≈ 743，乘以对称假设） | — |
| **米色标签外缘** | x=429 竖扫 162..378 → 半高 108.5 px = **0.427** | ÷ b（竖扫对 b） |
| **红心外缘** | x=429 竖扫 185..351 → 半高 83.5 px = **0.329** | ÷ b |
| 轴孔 | 15 px 直径 = **0.030 D** | ÷ 2b |
| 唱臂支点 | (647.9, 172.0)：到**盘心** 241.3 px、方位角 **−28.1°** | — |
| 唱针尖 | (672, 410)：到支点 241 px | — |
| **支点→针尖** | 241 px = **0.475**（÷ 2b＝508）或 0.783（÷ 2a＝615） | 竖直主轴被压扁，**两个口径都不可信**；只作量级参考 |

> **评审第 10 条已改**：上一版写「241 px = 0.71 D」，那是凭空来的。按规格自己的 D（竖 508 px）应是
> **0.47**，按水平（615 px）是 0.39。照片竖直被压扁（b/a = 0.82），两条轴给出的比例都不等于真实值，
> 所以**臂长不能从照片反推**：实现取 L = 0.650 D，由「落针点必须落在沟槽带上」反解校验（见 §1）。

---

## 1. 几何（`VinylGeo`，主工程与实验室共用一张表）

单位 **D = 唱片直径 = 264 dp**。

| 元素 | 规格 |
|---|---|
| 底盘（凸起 `shadeSurface`） | 圆形，直径 **D**，`cornerRadius = D/2`，**修饰符自己的** `offset = 6 dp` / `blur = 12 dp`。**静止不转** |
| 片心标签 = 封面圆 | 直径 **0.42 D = 110.9 dp**（实测米色环 0.427、红心 0.329；取米色环那一圈）。现实现 `D − 76 dp = 188 dp = 0.712 D`，**缩 41%** —— 要求 A 的落点 |
| 中心轴孔 | **0.030 D = 7.9 dp**，凹陷 `shadeInset(cornerRadius = d/2, offset = 2 dp, blur = 3 dp)` |
| 沟槽 | **7 圈**细凹环，**半径（单位是 D）0.240 → 0.480**（63.4 → 126.7 dp）等间距；盘缘 = 0.5 D、标签外缘 = 0.21 D。线宽 1 dp。每圈上缘暗影半弧（`startAngle 180°`，α = 0.34 × `alphaDark`）、下缘亮影半弧（`0°`，α = 0.40 × `alphaLight`）。**旋转对称 → 不随自转** |
| 自转 | 只有封面圆转；底盘、沟槽、轴孔**都不动** |
| 转速 | **15°/s** = 360°/24 s |
| 支点底盘 | 圆形凸起，半径 **0.10 D = 26.4 dp** |
| 支点到盘心 | **0.620 D @ −30°**（桌面系 0° = 右、90° = 下） |
| 臂长（支点→针尖） | **0.650 D = 171.6 dp** |
| 臂角 | **待机 57.7° / 到外圈 97.7° / 落针 104.5°** |
| 唱头 | 沿臂向 0.10 D × 宽 0.055 D；臂管宽 0.022 D；针尖 2.2 dp accent 圆点 |
| 舞台 | **1.50 D × 1.12 D**，盘心在舞台内 (0.50, 0.56) |

### 1.1 臂角怎么来的（评审第 4 条：公式符号）

```
支点 P = (|P|cos θp, |P|sin θp)，θp = PIVOT_ANGLE = −30°，|P| = 0.620
针尖 S(α) = P + L·(cos α, sin α)，L = ARM_LEN = 0.650
```

**三角形的两条边是 |P| 与 L，它们之间的夹角不是 |α − θp|，而是 180° − |α − θp|**（顶点在支点）。
所以：

```
r(α) = √( |P|² + L² − 2|P|L·cos(180° − |α − θp|) )
     = √( |P|² + L² + 2|P|L·cos(α − θp) )          ← 等价形式，**是加号**
```

> ⚠️ 上一版印成 `√(|P|² + L² − 2|P|L·cos(α − θp))`（减号），代入会得到 1.14 / 1.17 D（针根本够不到盘）。
> 减号只在「夹角 = |α − θp|」时才对；这里必须用**内角**。

反解 α（`Geo2.java:23` 的 `cosArg = (r² − p² − L²)/(2|P|L)` → `α = θp ± acos(...)`，取 y > 0 那支）：

```
α = 97.74°  →  S = (0.4494, 0.3341)   r = 0.5600 D   （盘缘外侧，落针起点）
α = 104.49° →  S = (0.3743, 0.3193)   r = 0.4920 D
α = 57.74°  →  S = (0.8839, 0.2396)   r = 0.9158 D   （待机，盘外）
支点底盘内缘 r = 0.620 − 0.10 = 0.520 D  → 清开盘缘 0.500 ✔
```

### 1.2 落针点（评审歧义 6：定死）

**落针点取 r = 0.492 D**（不是 0.480）。理由：

- 0.492 D 落在**最外圈沟槽（0.480 D）与盘缘（0.500 D）之间**，即「针刚落到唱片上、还没进沟槽深处」，
  这正是真唱机落针的瞬间；唱针一旦开始循迹会向内走，静止画面里把它停在这里最自然。
- 若要正好压在 0.480 D 上，取 **α = 105.66°**（反解，见 §1.1 的脚本）。
- 两者差 **0.012 D = 3.2 dp**，视觉上分不出；**规格定死用 0.492 D**。

### 1.3 舞台与对齐（评审歧义 7）

盘心在 1.50 D 舞台的 **0.50** 处 → 盘居中于舞台左半、唱臂伸向右侧。

**主工程的页面对齐基准（定死）**：舞台在播放页里**水平居中**（`Box(contentAlignment = Center)`），
即**盘的左边距 = 唱臂右边距**。因为是 1.50 D 宽、盘心在 0.50 D，所以盘心落在屏幕中心**左侧 0.25 D**，
这是刻意的（右侧要给唱臂扫掠留白），不是 bug。

---

## 2. 光影（怎么吃到 shade 系列）

主工程光照是 `shade/DayLight.kt` 的 `Lighting`：字段为**倍数 + 单位方向**。
`Shade.kt:180-188` 的 `shadowVectors()` 按 `组件自己的 offsetPx / (REF_OFFSET_DP = 6dp × density)` 缩放长度。

| 元素 | 标称 offset / blur | 消费方式 |
|---|---|---|
| 底盘（凸起圆） | 6 dp / 12 dp | `shadeSurface(cornerRadius = D/2, offset = 6.dp, blur = 12.dp)` —— 照现状不动 |
| 轴孔（凹陷圆） | 2 dp / 3 dp | `shadeInset(cornerRadius = d/2, offset = 2.dp, blur = 3.dp)` |
| 沟槽凹环 | 自绘 | 两半颜色 `tempTint(shadowDark/shadowLight, warmth)` × `alphaDark/alphaLight`；**强度再 × `relief` × `shadowAlpha`** |
| 支点底盘（凸起圆） | 5 dp / 8 dp | 自绘 `drawShadeAt`（签名见下） |
| 唱臂管 | 自绘 | 受光侧 `tempTint(shadowLight, warmth)`、背光侧 `tempTint(shadowDark, warmth)` × `alphaDark`；投影方向 `(ux·lenDark, uy·lenDark)·w`，`w = 6dp / REF_OFFSET_DP` |

### 2.1 `drawShadeAt` 的签名与 relief 规则（评审第 5 条）

**与 `drawShade` 同规矩：`relief` 由调用点乘、函数内部不再乘。**
`Shade.kt:255-265`（`shadeSurface`）与 `:269-279`（`shadeInset`）都是这么做的 ——
它们把 `offset.toPx() * relief` 传给 `drawShade`，`drawShade` 内部只把它交给 `shadowVectors()`。
函数内部再乘一次就是 `relief²`，用户拖「立体感强度」时这个独立凸起的位移会非线性。

```kotlin
/**
 * 任意圆心的凸起/凹陷圆。参数语义与 drawShade **完全一致**：
 *
 * @param offsetPx 组件自己的标称 offset，**调用点已经乘过 relief**（与 shadeSurface 同规矩！
 *                 本函数内部**不再**乘 relief，再乘一次就是 relief²）。
 * @param blurPx   同上，调用点已乘过 relief。
 *
 * 本函数内部要做的只有两件与 drawShade 一致的事：
 *   1) 外/内阴影强度 × LocalShadeShadowAlpha.current.floatValue
 *      —— 在 draw lambda 里读 .floatValue（读 State 只触发重绘、不重组，Shade.kt:102 的用法）；
 *   2) 凹陷时内阴影位移 ×0.9、亮内影强度 ×0.9（Shade.kt:239-247 的比例），并按圆裁剪。
 */
internal fun DrawScope.drawShadeAt(
    center: Offset, radiusPx: Float, bgColor: Color, raised: Boolean,
    colors: ShadeColors, light: Lighting, offsetPx: Float, blurPx: Float,
)
```

调用：

```kotlin
val relief = LocalReliefScale.current            // 组合期读一次
drawShadeAt(
    center = pivot, radiusPx = 0.10f * D,
    bgColor = colors.background, raised = true, colors = colors,
    light = DayLightHost.current(colors.isDark),
    offsetPx = 5.dp.toPx() * relief,             // ★ relief 在这里乘
    blurPx = 8.dp.toPx() * relief,               // ★
)
```

**沟槽与唱臂的自绘强度同样要 × `relief` × `shadowAlpha`**（这两个值在 draw lambda 里读）。
否则拖「立体感强度」时，转盘上的支点底盘、沟槽凹环、唱臂会是**全 App 唯一不跟着变的部分**。

### 2.2 分层与性能（**性能红线**，评审第 6 条）

**层必须这样分**（照抄实验室 `VinylDeck.kt`）：

| 层 | 内容 | 会不会每帧变 |
|---|---|---|
| 静止层 1 | 底盘 `shadeSurface`（含两遍 BlurMaskFilter） | 否 |
| 静止层 2 | **沟槽**（7 圈凹环）+ 中心轴孔 + 落针标记 | 否 |
| **旋转层** | **只有封面圆** | 是（自转 / 换片滑动） |
| 静止层 3 | 唱臂 + 支点底盘 | 只在 armCue/armLower/armLift 期间变 |

**沟槽是旋转对称的，永远不要放进旋转层**（转它没有视觉收益，却让每帧多光栅化一大片）。

**自转的写法**：把封面画进**一个 Canvas**，在 **draw lambda 里读角度**并用 `rotate()` 变换画布。

| 写法 | playing 中位帧 | 掉帧 |
|---|---|---|
| 封面层 `graphicsLayer { rotationZ = … }` 且与底盘同层 | 81 ms | 73% |
| 封面层 `graphicsLayer { rotationZ = … }`，与底盘分层 | 65 ms | 62% |
| **单 Canvas + draw 期 `rotate()`（本规格采用）** | **17 ms** | **0%** |

### 2.3 性能实测（评审第 13 条）

设备 `emulator-5554`（`ro.build.version.sdk=36`，宿主 Apple M3 Pro，`hw.gpu.mode=host`，
渲染器 `skiagl`）。命令：

```bash
adb shell dumpsys gfxinfo com.neumusic.vinyl reset   # 先复位
adb shell dumpsys gfxinfo com.neumusic.vinyl         # 10s 后读
adb shell top -b -n 1 -o PID,%CPU,ARGS
```

| 状态 | 帧数 / 10 s | 掉帧 | 中位帧 | 90 分位 |
|---|---|---|---|---|
| idle（盘停） | **0** | 0% | —（一次都不画） | — |
| playing（15°/s 自转） | **603** | **0%** | **17 ms** | 18 ms |
| discSwap（换片） | 363 | 0% | 21 ms | — |

> 上一版报的「24–32% CPU / 中位帧 65–81 ms / 73% 掉帧」来自一台**已经退化**的模拟器
> （同一份代码重启模拟器后是 0% 掉帧 / 17 ms）。**性能结论以本节为准**。

---

## 3. 阶段表（stageTable）

| 阶段 | 时长 ms | 缓动 | 这一段画面上发生什么 |
|---|---|---|---|
| `idle` | — | — | 待机：臂 **57.7°**（针尖 r = 0.916 D，盘外）、`stylusDown = 0`、`speed = 0`、**静音**。等事件；手动切歌的 3 秒静默窗口也在这里等 |
| `discAccel` | **900** | FastOutLinearIn | 转盘 `speed 0 → 15°/s`；`platterAngle` 由 `speed·dt` 逐帧积分（暂停/播放都从当前角度续，不回零） |
| `armCue` | **900** | ①520ms FastOutSlowIn ②380ms Linear | 臂角 `57.7° → 97.7°`（摆到唱片外圈）→ `97.7° → 104.5°`（**向内缓慢移动**，针尖径向内移 0.068 D = 18 dp）；两段都只动 `armAngle`，唱针仍抬起 |
| `armLower` | **260** | FastOutLinearIn | 只做**下压**：`stylusDown 0 → 1`。结束时**出声** |
| `playing` | — | — | `speed` 恒 15°/s；假时间轴走 `positionMs += dt`，走到「总长 − 提前量」发一次自动换片 |
| `armLift` | **240** | LinearOutSlowIn | `stylusDown 1 → 0`；**立刻静音**（时间轴停） |
| `armReturn` | **620** | FastOutSlowIn | 臂角 `当前 → 57.7°` |
| `discDecel` | **700** | LinearOutSlowIn | `speed → 0`；**减速走完才允许换片** |
| `discSwap` | **620** | FastOutSlowIn | 旧片：前 50% 左移 0.10 D 且 `alpha 1 → 0`（画 `coverIndex`）；新片：后 50% 从右 0.10 D 处滑入且 `alpha 0 → 1`（画 `incomingCover`）。**离开本阶段才 `discNo += 1`**、时间轴归零 |
| （接回 `discAccel`） | 900 | — | 换片后重新加速，再接 `armCue → armLower → playing` |

**全套计时**（要求 E 的「提前量」）：
- **换片全套 = 240 + 620 + 700 + 620 + 900 + 900 + 260 = `AUTO_LEAD_MS` = 4240 ms** ← 自动播放的提前量
- 从静止起播 = 900 + 900 + 260 = `START_MS` = 2060 ms
- 退回待机（手动切歌的半程）= 240 + 620 + 700 = `ABORT_MS` = 1560 ms
- 静默窗口 = `SWITCH_QUIET_MS` = 3000 ms

**播放到结尾**：走 `armLift → armReturn → discDecel`（= ABORT_MS）后**不停下来**，
`idle` 出口立刻进 `discSwap`（`pendingCover` 已排好）。

---

## 4. 状态机转移表（transitionTable）

**总规则**：单协程顺序执行，**每个阶段一定跑完自己的时长**；中途到的请求只写进 `pending`
（后到覆盖先到），在**阶段出口**才被看到。`RESET` **与 PAUSE/SWITCH 一样写进 `pending`**。

> **纪律（评审第 1 条，会 ANR）**：每个阶段的出口必须是**穷尽的 `when (pending)`**，
> 且每个分支要么 `consume()`、要么明确说明「pending 留着往哪带」。
> 任何「`else` 分支不消费就返回当前阶段」都会变成 `next == phase` 的**自转移死循环**：
> 唯一的挂起点 `events.receive()` 永远到不了，主线程 100% 空转、系统弹 ANR。
> 实验室已加兜底不变量：`if (next == phase && pending != null && phase == playing) consume()`。

`playPause()` 的映射（与下表一致）：

| 当前阶段 | 发出 | 含义 |
|---|---|---|
| `idle` | `PLAY` | 起播 |
| `playing` / `discAccel` / `armCue` / `armLower` / **`discSwap`** | **`PAUSE`** | 暂停（抬针回待机） |
| `armLift` / `armReturn` / `discDecel` | `PLAY` | 「继续」→ 被消费，回到 `idle` 后重新起播 |

| # | 当前阶段 + 事件 | → 目标阶段 | 动作 |
|---|---|---|---|
| 1 | `idle` + 播放 | `discAccel` | 清空待办；转盘起转 |
| 2 | `idle` + 手动切歌 | `idle`（留在原地） | 记 `pendingCover`、重置静默计时 |
| 3 | `idle` + 自动换片 | `discSwap` | 不等静默窗口 |
| 4 | `idle` + 重置 | `idle` | 臂回待机、`speed = 0`、`discNo = 1`、封面回第 1 张、时间轴归零 |
| 5 | `discAccel` + 暂停/重置 | `discDecel` | 本段 900 ms 走完再减速；重置由 `discDecel` 出口执行 `resetLab()` |
| 6 | `discAccel` + 切歌 | `armCue` | pending 留着；**不停在 discAccel 换片** |
| 7 | `armCue` + 暂停/重置 | `armLift` | 本段 900 ms 走完再抬针 |
| 8 | `armCue` + 切歌 | `armLower` | pending 留着，到 `armLower` 出口抬针 |
| 9 | `armLower` + 暂停/重置 | `armLift` | **本段 260 ms 走完（针已压到位）再抬针，不入 `playing`、不出声** |
| 10 | **`discAccel` / `armCue` / `armLower` + 切歌** | 本段走完 → `armLower` 出口 → `armLift` | **全程不进 `playing`、不出声**（实测：`discAccel 900 → armCue 900 → armLower 260 → armLift 0`，没有 playing） |
| 11 | `playing` + 暂停 | `armLift` | 静音 → 抬针 → 回臂 → 减速 → `idle` |
| 12 | `playing` + 切歌 | `armLift` | 抬针回臂减速后进 `idle`，**在那里**等 3 秒静默 |
| 13 | `playing` + 自动换片 | `armLift` | 同上但不设静默窗口 |
| 14 | `playing` + 重置 | `armLift` | 抬针回臂减速 → `idle`；`discDecel` 出口 `resetLab()` |
| 15 | `playing` + 播放 | `playing` | `PLAY` 消费掉（已经在播），留在原地 |
| 16 | `armLift` / `armReturn` / `discDecel` + 切歌 | 不回退，走完本段 | 只更新 `pendingCover` + 重置静默计时；**这一段绝不中止**。实测：`discDecel t=700 → idle → idle t=3006 → discSwap` |
| 17 | `armLift` / `armReturn` / `discDecel` + 播放 | 不回退，走完本段 | `PLAY` 被消费；回到 `idle` 后**重新起播**（实测：`idle → discAccel → armCue → armLower → playing`） |
| 18 | `armLift` / `armReturn` / `discDecel` + 重置 | 不回退 | `resetRequested` 置位，`discDecel` 出口执行 `resetLab()` |
| 19 | `discSwap` + 切歌 | `discSwap`（续）→ `discAccel` | **re-aim**：本阶段结束前先 drain，把换入的那张直接改成最新请求；`discNo` 只 +1、**不再补一次换片** |
| 20 | `discSwap` + 暂停 | `idle` | 换片走完、`discNo += 1`、**停在新片、转盘停、静音**（不置 `audioOn`、不再走起播链） |
| 21 | `discSwap` + 播放 | `discAccel` | 正在换片、换完就要接着播 → `PLAY` 消费掉、继续换片链 |
| 22 | `discSwap` + 重置 | `discDecel` → `idle` | 换片走完 → `resetLab()` |
| 23 | **任意阶段 + 重置** | 抬针 → 回臂 → 减速 → `idle` → `resetLab()` | 已在 `idle` 则立即重置 |
| 24 | `idle` + 暂停 | `idle` | `PAUSE` 丢弃（已静止） |

**静默窗口实现**：`lastManualAt` 每次手动切歌刷新；`idle` 里若 `now − lastManualAt < 3000`，
就**留在 `idle` 阶段内**（不返回主循环、不重复打日志），用 `withTimeoutOrNull(剩余时间)` 等新事件。
自动换片走同一个 `pending` 但 `pendingManual = false`，**不等窗口**。

---

## 5. 主工程接线

### 5.1 `PlayerHost` 需要新增的接口

现有公开 API 只有 `toggle()` / `pause()` / `next()` / `previous()` / `playAt()`（`player/PlayerHost.kt:227-254`），
**没有公开的 `play()`**；`next()` 内部是 `recordHistory → playCurrent → startInternal → p.play()`
（`player/PlayerHost.kt:248-254`、`199-224`）—— 一调用就立刻出声并联网取链接。所以要补：

```kotlin
/** 只装载不播放：切到下一首、取好链接、停在 IDLE，不出声。 */
fun nextPrepared(onError: (String) -> Unit)

/** 抑制 PlayerHost 自己的自动 advance（挂在 onPlaybackStateChanged(STATE_ENDED) 上，
 *  PlayerHost.kt:84-86）。动画自己负责换片；不禁用会在动画期间打两次。 */
fun setAutoAdvanceSuppressed(v: Boolean)
```

### 5.2 机器的对外入口 + 主工程 6 个调用点（评审第 3 条）

**机器的完整对外入口**：

```kotlin
machine.playPause()        // 播放/暂停（映射见 §4 的表）
machine.skip()             // 下一首（手动）
machine.previous()         // 上一首（走同一条换片动画）
machine.reset()            // 回到待机
machine.seekToLeadPoint()  // 跳到片尾（进度条拖进提前量窗口由 §5.5 的轮询处理）
machine.onSuppressChange = { v -> PlayerHost.setAutoAdvanceSuppressed(v) }
machine.onDiscChanged = { track -> PlayerHost.nextPrepared(...) }   // 进 discSwap 时触发
```

**主工程所有入口一律改调机器，不再直接调 `PlayerHost`**：

| 文件:行 | 现在 | 改成 |
|---|---|---|
| `ui/common/PlayerBar.kt:189` | 左滑 → `PlayerHost.next()` | `machine.skip()` |
| `ui/common/PlayerBar.kt:194` | 右滑 → `PlayerHost.previous()` | `machine.previous()` |
| `ui/player/PlayerScreen.kt:357` | 上一首 → `PlayerHost.previous()` | `machine.previous()` |
| `ui/player/PlayerScreen.kt:370` | 下一首 → `PlayerHost.next()` | `machine.skip()` |
| `ui/player/PlayerScreen.kt:360` | `PlayerHost.toggle()` | `machine.playPause()` |
| `playback/PlaybackService.kt:80` | 通知栏 toggle → `PlayerHost.toggle()` | `machine.playPause()` |
| `ui/.../HomeScreen.kt:282` | 推荐卡点播 → `PlayerHost.playQueue(...)` | 见下 |

- **`previous()` 的语义**：与 `skip()` 同一条路径（一样走整套换片），只是换入的封面往回走一张。
- **`playQueue()` / `playAt()`（换整批队列）**：这类调用**直接换歌、不走动画**（用户点了列表里的歌，
  期望立刻听到）。机器只负责把 `coverIndex` / 时间轴对齐到新曲目，并**取消进行中的动画**（回 idle）。
- **机器不走「只观察」路线**：主工程 UI 入口太多（7 处且分散），从 `PlayerHost.current` 反推
  「用户是切歌还是自动换片」无法区分，会双切。**定死：所有入口改调机器。**

### 5.3 出声时机与切歌时机

- **`armLower` 结束、进入 `playing` 的那一帧才 `play()`**；只有 `armLower` **完整走完且未被打断**才 play()。
- **切歌动作在进入 `discSwap` 的瞬间**调用 `nextPrepared()`（**只装载不出声**）。
- 旧封面：`nextPrepared()` 之后 `PlayerHost.current` 已是新曲目 → 机器在 `playing` 期间就记住
  `currentCoverUrl`，进 `discSwap` 时 `oldCoverUrl = currentCoverUrl`，换片结束再更新。

### 5.4 抑制标志的完整 on/off 矩阵（评审第 8 条）

| 时机 | 值 |
|---|---|
| 进入 `armLift` | **true** |
| 进入 `playing` | false |
| 进入 `idle` | false |
| `resetLab()`（重置/初始化） | false |
| abort 分支（armLift→armReturn→discDecel）走完回到 `idle` | false（由「进入 idle」那一条覆盖） |

**兜底**：`setSuppress()` 只在值真变化时回调；`idle` 与 `resetLab` 都会置 false，
所以「只要回到静止或稳定播放，标志必定是 false」—— 不会出现「自动切换永久失效」。

**它与 PlayerHost 自身 ENDED→advance 的关系**：`PlayerHost.kt:84-86` 的
`onPlaybackStateChanged(STATE_ENDED) → advance()` 是**旧机制**；黑胶模式下由动画接管换片，
所以动画期间（`armLift`…`discAccel` 这一整段）必须抑制它，避免「动画换一次 + PlayerHost 又换一次」。
**非黑胶模式（`vinylMode = false`）不抑制**，旧行为完全不变。

### 5.5 提前量与 seek（评审歧义 5）

```kotlin
fun autoLeadMs(durationMs: Long) = minOf(AUTO_LEAD_MS, (durationMs / 2).toInt().coerceAtLeast(1))
```

**措辞修正**：这个 `min(...)` 只防「一点就换」（短曲 `duration < 4240` 时裸条件恒真），
**不保证动画在曲尾前跑完**：8 秒以下的歌，提前量 < 4240 ms，动画一定会跨过曲尾。
**但不会出错** —— 因为 `armLift` 一开始就 `pause()`，**动画是自己计时的**，
不依赖播放器的时间轴（这正是不需要「必须在曲尾前跑完」的原因）。

**轮询抖动**：`PlayerScreen.kt:137-143` 是 500 ms 轮询 → 触发最多晚 0.5 s。
**规格接受这个抖动**（换片起点晚 0.5 s 看不出来）。抑制标志**不必提前打开** ——
`armLift` 时打开就够；ENDED 也要等播放器真的走到结尾，而那时我们早已 pause()。

**任意 seek 后的重新武装**：

| 情形 | 处理 |
|---|---|
| `seek` 且 `pos < duration − lead` | `autoArmed = true; autoFired = false` |
| `seek` 且 `pos ≥ duration − lead`（拖进提前量窗口） | `autoFired = false; autoArmed = false; seekAnchorMs = pos` —— 让时间轴**自然涨过一帧**才武装，否则一点就触发、整段动画看不见 |
| 「跳到片尾」 | 同上一行 |
| 拖回开头 / 换曲 | `autoFired = false; autoArmed = true` |

**接线定死（歧义 4）**：进度条**不改**成调机器；机器**每 500 ms 轮询** `PlayerHost.positionMs()`，
发现 `|pos − 上次pos| > 1500 ms` 且不是自己触发的换片 → 判定为「用户 seek」，按上表重新武装。
（选轮询而非 UI 通知：进度条的拖动/点击有两处调用点，改 UI 会漏；轮询一处收口。）

### 5.6 舞台缩放与可见性（评审第 7 条 + 歧义 1、2）

**缩放（算术改正）**：`discD = minOf(avail / STAGE_W, 264.dp)`，`STAGE_W = 1.50`。

| 屏宽 | avail = 屏宽 − 2×28dp | `avail / 1.50` | 取到的 D |
|---|---|---|---|
| 411 dp（常见） | 355 dp | **236.7 dp** | **236.7 dp** |
| 360 dp（小屏） | 304 dp | 202.7 dp | 202.7 dp |
| 452 dp 及以上 | ≥ 396 dp | ≥ 264 dp | **264 dp**（封顶，与现值一致） |

**定死：按上式缩放，普通手机上转盘约 237 dp**（比现在的 264 dp 小）。
不选「保持 264 dp 让舞台溢出」—— 溢出的唱臂会被裁掉，而唱臂是本设计的三个关键元素之一。

**三个显示模式下哪些显示转盘（歧义 1）**：

| 模式 | 显示什么 |
|---|---|
| 0 纯封面 | **转盘（VinylDeck）**，D 按上式 |
| 1 封面+歌词 | **转盘**（画面被 `modeT` 缩到 0.78、上移；1.50 D 舞台按 0.78 缩后仍 ≤ 屏宽，不溢出） |
| 2 纯歌词 | 不显示转盘 |

**`Prefs.vinylMode` 的语义（歧义 2，定死）**：

| `vinylMode` | 播放页画什么 |
|---|---|
| `false`（默认） | **普通圆盘**：现状的封面圆盘（封面 `D − 16 dp` 贴在凸起底盘上、**无沟槽、无唱臂、不转**），除转盘外代码路径完全不变 |
| `true` | **转盘**：`VinylDeck`（底盘 + 封面 0.42 D + 沟槽 + 轴孔 + 唱臂），自转与全部阶段机只在此时运行 |

即 `vinylMode` 是「要不要转盘」的唯一开关；关掉时阶段机不启动、一帧都不多画。

### 5.7 主工程改哪些文件

1. **`ui/player/VinylMachine.kt`（新）**：搬实验室的 `VinylMachine`（§5.2 的入口 + §5.3 的时机 + §5.4 的矩阵）。
2. **`ui/player/PlayerScreen.kt`**：
   - **整段 563–675 行一并删除**（评审第 9 条）—— 包含 ① 频谱环 Canvas（569–597）
     ② `CoverDisc` 的黑胶分支与 `coverDiameter = DISC_SIZE - 76.dp`（599–660）
     ③ `RingSeekArea` 的定义与调用（667–674、683–712）。保留的常量只有 `DISC_SIZE = 264.dp`；
     `RING_WIDTH` / `RING_GAP` / `RING_MAX_LEN` / `angleFrac` 一并删（环上 seek 取消，§5.8）。
   - 换成 `VinylDeck(machine, discD)`；`CoverPage` 的三模式布局不变。
3. **`shade/Shade.kt`**：新增 `drawShadeAt`（签名与 relief 规则见 §2.1）。
4. **§5.2 表里的 7 个调用点**改成调机器。
5. **设置页**：`黑胶唱片模式` 开关已有（`data/Prefs.kt:279-283`），不需要新设置项。
6. **合成器（可选）**：设 `graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`
   在旋转层上，避免每帧重建原生层。

### 5.8 频谱环与环上 seek（歧义 3 的答案）

**两者都取消**：

- 频谱环：① 舞台 1.50 D 宽、盘心偏左，环会与唱臂扫掠范围重叠；② 参考图盘外就是桌面；
  ③ 要求 B 说「黑胶只需要三个关键元素」。
- 环上 seek：改用播放页已有的**直线进度条**（`PlayerScreen.kt:299-343`），它本来就在。
- 转盘本身**不接管**点按/拖动（避免与上下滑切模式的手势打架，`PlayerScreen.kt:198-215`）。
- 若要保留频谱环，只在 `vinylMode = false`（非黑胶）时画。

---

## 6. 调试契约（debugContract）

**本节只对实验室（`~/Documents/VinylLab`，包名 `com.neumusic.vinyl`）成立**；
主工程播放页没有这些按钮，也没有那行读数（评审第 9 条）。主工程验收见 §7。

### 6.1 日志（三句话，与实现一致）

```
Log.i("VinylLab", "PHASE=<阶段名> t=<自阶段开始的毫秒> disc=<唱片编号>")
```

1. **计时阶段**（**7 个**）：`armCue` 900 / `armLower` 260 / `armLift` 240 / `armReturn` 620 /
   `discDecel` 700 / `discSwap` 620 / `discAccel` 900
   —— 进入时一条 `t=0`，**真正离开时**再一条 `t=该阶段标称时长`。
2. **`idle` 与 `playing`**（零时长的等待态）：进入时一条 `t=0`；**真正离开时**补一条
   `t=实际停留毫秒`（例 `PHASE=idle t=3006 disc=1` 就是等满 3 秒静默窗口那次）。自转移不重复打。
3. **`disc` 取打印时刻的当前唱片编号** ⇒ `discSwap` 的进入行是 N、收尾行才 N+1 ——
   **`discSwap` 的收尾行是唯一能看到 +1 的地方**，且 `t` 恒为 620（换片途中 re-aim 也照样 620）。

阶段名**固定九个、不许改名**：`idle`、`armCue`、`armLower`、`playing`、`armLift`、`armReturn`、
`discDecel`、`discSwap`、`discAccel`。

### 6.2 屏幕读数

```
PHASE=<阶段名> t=<自阶段开始的毫秒> disc=<唱片编号>
```

例 `PHASE=playing t=0 disc=2`。脚本校验正则：
`^PHASE=(idle|armCue|armLower|playing|armLift|armReturn|discDecel|discSwap|discAccel) t=\d+ disc=\d+$`

### 6.3 控制按钮（**7 个**，content-desc 固定不变）

| content-desc | 作用 |
|---|---|
| `播放暂停` | 映射见 §4 的表 |
| `切歌` | 手动切歌（3 秒防抖） |
| `跳到片尾` | 假时间轴设到「总时长 − 提前量」，让时间轴自然跑完触发自动换片 |
| `重置` | 回 `idle`（臂回待机、转盘停、`discNo = 1`、封面回第 1 张、时间轴归零） |
| `时长20秒` / `时长40秒` / `时长60秒` | 可配置的假时间轴总长（**默认 40 秒**） |

> 原题写「五个控制按钮」是笔误；实际 **4 个动作钮 + 3 个时长钮 = 7 个**，
> 4 个动作钮的名字逐字照原题、一个字符没改。

### 6.4 实验室的播放是假的

可配置总长（默认 40 秒）的时间轴；**不联网、不播放音频**；「出声/静音」只是阶段机的状态量。

---

## 7. 主工程的验收

| 检查 | 怎么验 |
|---|---|
| 阶段日志 | 沿用 `Log.i("VinylLab", …)` 作 tag（与实验室同一套格式），触发源是真播放器 |
| 日志开关 | ⚠️ `AppLog.write` 在 `Prefs.loggingEnabled = false` 时直接 return（`data/AppLog.kt:29`、`33-39`）。**定死**：主工程的阶段日志**不走 AppLog**，只用 `android.util.Log.i` 直写 logcat —— 于是 `adb logcat -s VinylLab:I` 在任何日志开关状态下都能抓到，校验脚本不依赖诊断日志文件 |
| 播放页读数 | 只在 `Prefs.vinylMode = true` 时显示一行小字阶段读数（便于截图核对） |
| 按钮 | 播放页原有的收起/均衡器/控制区按钮不变；**不新增**实验室那 7 个 |
| 打断 | 逐条比对 §4 的表 |

---

## 8. 实验室

`~/Documents/VinylLab`（样板 = `~/Documents/ShadeLab`：工程结构、gradle 配置、Theme、DayLight 全照搬改名）。
唯一有意差异：实验室是 ShadeLab 的**绝对 dp** 光照版（`LightState`），主工程是**倍数**版（`Lighting`）——
移植时只换光照来源，几何与阶段机原样照搬。详见该目录 `README.md`。
