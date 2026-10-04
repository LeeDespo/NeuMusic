package com.neumusic.player.ui.player

import android.util.Log
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.shade.DayLightHost
import com.neumusic.player.shade.Lighting
import com.neumusic.player.shade.LocalReliefScale
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.LocalShadeShadowAlpha
import com.neumusic.player.shade.REF_OFFSET_DP
import com.neumusic.player.shade.tempTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 黑胶转盘（主工程，**v2**）。几何、光影画法、阶段机全部从唱片实验室 `~/Documents/VinylLab`
 * 移植，以它为基准接入真实播放器；2026-10-04 另按用户新要求重画机械件并修复换片：
 *
 *  1. **光照来源**：实验室是 ShadeLab 的绝对 dp 版（`LightState`），这里换成主工程
 *     `shade/DayLight.kt` 的**倍数**版 [Lighting] —— 位移/模糊按
 *     `offsetPx / (REF_OFFSET_DP × density)` 缩放，与 `shadeSurface` 同一套算法、
 *     同一个 `DayLightHost`，所以设置里的光影时刻/标定对转盘同样生效。
 *  2. **驱动源**：实验室是假时间轴，这里是真播放器（`PlayerHost`）。
 *
 * ⚠️ **本组件只跟随播放、不驱动播放**（这是对规格 §5.3 的有意简化，理由如下）：
 * 旧规格要求「armLower 结束才 play()、进 discSwap 时 nextPrepared()」，那需要给
 * `PlayerHost` 新增 `nextPrepared()` / `setAutoAdvanceSuppressed()` 并接管全部 7 个
 * 播放入口。本工程已有 `onPlaybackStateChanged(STATE_ENDED) → advance()` 这套成熟机制，
 * 两套「谁负责换歌」并存必然双切（规格 §5.2 自己也警告过）。所以这里**只观察**：
 * `PlayerHost` 照旧换歌，转盘把「抬针 → 回臂 → 减速 → 换片 → 重新起播」演出来。
 * 阶段机不碰播放，就不会和 `advance()` 抢。
 */
enum class VinylPhase(val ms: Int) {
    idle(0), discAccel(900), armCue(1200), armLower(260),
    playing(0), armLift(240), armReturn(620), discDecel(700), discSwap(620),
}

/** 起播弧线总时长 = armCue + armLower：**一条缓动曲线横跨两个阶段**（规格 §3.2 段 1）。 */
const val START_ARC_MS = 1200 + 260

/** 起播弧线的缓动：加速—减速到外圈收住（到点即稳，没有中段停顿）。 */
val START_ARC_EASING: Easing = FastOutSlowInEasing

/** 换片段时长（旧封面滑出 / 新封面滑入）。 */
private const val SWAP_MS = 620

// GEO-CHECK stageW=1.540 discCx=0.770 discCy=0.630 pivotX=1.270 pivotY=0.210 rOut=0.480 rIn=0.320 headOffset=20.0 innerOuter=0.320
// ↑ 机器可读：字段与实验室启动日志的 `GEO …` 完全一致（discD 是运行时 dp，故不在常量表里）。
//   三个臂角由 P=0.6529931/θp=−40.03026° 反解（81.3315 / 96.8770 / 111.6255），不手填。
/**
 * 唱片几何：全部以**唱片直径 D** 为单位，数值来自参考图实测 + 实验室四轮视觉评审校准
 * （原读数记在实验室 README），舞台和纹路保留；机械件按 2026-10-04 要求调整。
 */
object VinylGeo {
    /** 主工程原有的盘径常量（`PlayerScreen.DISC_SIZE = 264.dp`）。 */
    const val DISC_D = 264f

    /** 片心标签 = 封面圆，直径 ÷ D（参考实测米色标签外缘 0.425，取 0.42）。 */
    const val COVER_RATIO = 0.42f

    /** 中心轴孔直径 ÷ D。 */
    const val HOLE_RATIO = 0.030f

    // ── v2 双层纹理：外层（疏，沿用参考弧线）+ 内层（致密更深）+ 过渡带 ──
    /** 外层纹理半径表（÷ D）：参考照片反校正实测的那批弧线，v1 的「密集外圈」。 */
    val GROOVE_OUTER = floatArrayOf(
        0.361f, 0.372f, 0.383f, 0.394f, 0.399f, 0.407f, 0.418f,
        0.432f, 0.447f, 0.454f, 0.466f, 0.480f, 0.490f, 0.500f,
    )

    /**
     * 内层纹理半径表（÷ D）：**致密 + 更深**（步长 0.008 < 外层均步长 0.0107）。
     * 最外圈 0.320 == [R_IN]（唱头播放终点落在内外层交界处）；最内圈 0.240 距片心
     * 封面外缘 0.21 还有 0.030 D。两表无交集 → 0.320→0.361 是过渡空带。
     */
    val GROOVE_INNER = floatArrayOf(
        0.240f, 0.248f, 0.256f, 0.264f, 0.272f, 0.280f,
        0.288f, 0.296f, 0.304f, 0.312f, 0.320f,
    )

    /** 内层纹理最外圈半径 —— 与 [R_IN] 是**同一个常量**（唱头终点必落在交界处）。 */
    const val INNER_OUTER = 0.32f

    /** 过渡带分隔弧半径（0.320 → 0.361 之间）与线宽。 */
    const val TRANSITION_R = 0.3405f
    const val TRANSITION_W = 1.8f
    const val GROOVE_W = 1f

    // ── v2 几何：盘居中、底座 x=盘最右缘、y 抬高（常量表逐字）──
    /** 舞台 ÷ D；盘心 (0.77,0.63) = **舞台正中**（左右留白 0.27/0.27、上下 0.13/0.13）。 */
    const val STAGE_W = 1.54f
    const val STAGE_H = 1.26f
    const val DISC_CX = 0.77f
    const val DISC_CY = 0.63f

    /** 底座圆心：x = 盘最右端点 x（0.77+0.50）、y 抬到盘心上方 **0.42 D**（盘顶缘以上 0.08 D）。 */
    const val PIVOT_X = 1.27f
    const val PIVOT_Y = 0.21f

    /** 支点反算：P=√(0.5²+0.42²)=0.6529931、θp=atan2(−0.42,0.50)=−40.03026°。 */
    const val PIVOT_DIST = 0.6529931f
    const val PIVOT_ANGLE = -40.03026f
    /** 支点 → 唱头前端的有效长度 0.654 D；臂管长度由内折角反解。 */
    const val ARM_LEN = 0.654f

    /** 臂角由目标半径反解（不手填）：cosArg=(r²−P²−L²)/(2PL)，α=θp+acos(cosArg)。 */
    fun armAngleForRadius(r: Float): Float {
        val cosArg = ((r * r - PIVOT_DIST * PIVOT_DIST - ARM_LEN * ARM_LEN) /
            (2f * PIVOT_DIST * ARM_LEN)).coerceIn(-1f, 1f)
        return PIVOT_ANGLE + Math.toDegrees(acos(cosArg.toDouble())).toFloat()
    }

    // ── 三个半径（口径 A：针尖 = 臂末端）──
    /** 待机：针尖在盘缘外 0.140 D（臂管中心线距盘心最近 0.5576 D，减半管宽仍净空 0.033 D）。 */
    const val R_STANDBY = 0.64f
    /** 播放起点：外圈沟槽 0.480（∈ [GROOVE_OUTER]）。 */
    const val R_OUT = 0.48f
    /** 播放终点：内外层纹理交界处，== [INNER_OUTER]。 */
    const val R_IN = 0.32f
    /** armLift 的抬臂量（半径外移），让「暂停」看得见一个离盘动作。 */
    const val R_LIFT = 0.03f

    /** 唱头相对臂轴的内折角（度，屏幕系 y 向下取 **+20°**，偏向盘心/切向那支）。 */
    const val HEAD_OFFSET_DEG = 20f
    /** M3 Expressive 大色块底座及细胶囊臂管尺寸。 */
    const val PIVOT_BASE_R = 0.10f
    const val HEAD_L = 0.135f
    const val HEAD_W = 0.090f
    const val ARM_W = 0.034f

    /** 播放段：进度 → 半径的**线性**绑定（规格 §3.2 段 2）。 */
    fun radiusForProgress(p: Float): Float = R_OUT + (R_IN - R_OUT) * p.coerceIn(0f, 1f)

    /** 底座圆心（舞台画布系，px）。 */
    fun pivotX(discPx: Float): Float = discPx * PIVOT_X
    fun pivotY(discPx: Float): Float = discPx * PIVOT_Y

    /** 唱臂绘制的**量化步长**（D）：播放段半径只有 ~0.004 D/s，逐帧重绘纯属浪费。 */
    const val DRAW_QUANT_D = 0.001f

    /** 目标转速（度/秒）：360/24 s。 */
    const val DEG_PER_SEC = 15f
}

/** 阶段日志：主工程同样用 `Log.i("VinylLab", …)` 直写 logcat（不走 AppLog，见规格 §7）。 */
private fun logPhase(p: VinylPhase, t: Int) {
    Log.i("VinylLab", "PHASE=${p.name} t=$t")
}

/**
 * 转盘状态机（单协程、帧驱动）。
 *
 * 三个外部信号（`PlayerScreen` 喂进来）：
 *  - [setPlaying]：`PlayerHost.isPlaying`
 *  - [onTrackChanged]：`PlayerHost.current` 的封面 —— 曲目切换
 *  - [onPosition]：暂停时更新位置/时长，播放中按真实进度走弧线
 *
 * 阶段链（与实验室一致）：
 *  - 起播：discAccel(900) → armCue(1200) → armLower(260) → playing
 *  - 停：armLift(240) → armReturn(620) → discDecel(700) → idle
 *  - 换片：armLift → armReturn → discDecel → discSwap(620) → （还在播则）discAccel → …
 *
 * **等待一律走 `withFrameNanos`**（挂起，不空转），且在每帧重新判断谓词 ——
 * 没有「pending 未消费导致自转移」的坑（实验室那版用事件通道 + pending，是因为它要
 * 校验 24 条转移表；主工程只需要「跟随状态」）。
 */
class VinylTurntableState(private val scope: CoroutineScope) {

    var phase by mutableStateOf(VinylPhase.idle); private set

    /**
     * **v2 唯一的唱臂动画量**：针尖（= 臂末端，口径 A）到盘心的半径，单位 D。
     * 唱头 + 唱臂是刚体，姿态完全由它决定（臂角 = [VinylGeo.armAngleForRadius] 反解）。
     * 唱针与下压动画（v1 的 [stylusDown]）已按方案 §3.1 删除。
     */
    var tipRadius by mutableFloatStateOf(VinylGeo.R_STANDBY); private set

    /** 臂角（度）—— 纯由 [tipRadius] 反解，不单独存状态。 */
    val armAngle: Float get() = VinylGeo.armAngleForRadius(tipRadius)

    /**
     * 绘制用的半径：**量化后的独立 state**（不是 `get() = round(tipRadius/步长)*步长` ——
     * 那种派生写法仍订阅逐帧变的 [tipRadius]，唱臂层照样每帧失效）。
     * 播放段半径 ~0.004 D/s（0.04 px/帧），量化到 [VinylGeo.DRAW_QUANT_D]（0.001 D ≈ 0.63 px）
     * 后每 ~250ms 才失效一次；起播段/拖进度条的位移远大于步长，看不出台阶。
     */
    var drawTipRadius by mutableFloatStateOf(VinylGeo.R_STANDBY); private set

    var platterAngle by mutableFloatStateOf(0f); private set
    var platterSpeed by mutableFloatStateOf(0f); private set

    /** 换片用的两张封面（真实 coverUrl；空串 = 无封面，画占位）。 */
    var coverOld by mutableStateOf(""); private set
    var coverNew by mutableStateOf(""); private set
    var swapOutAlpha by mutableFloatStateOf(1f); private set
    var swapOutDx by mutableFloatStateOf(0f); private set
    var swapInAlpha by mutableFloatStateOf(1f); private set
    var swapInDx by mutableFloatStateOf(0f); private set
    /** 当前稳定显示的封面（非换片期）。 */
    var cover by mutableStateOf(""); private set

    /**
     * **细粒度位置源**（v2，规格 §5）：`PlayerScreen` 原有的 500ms 轮询对进度条够用，
     * 唱头还要及时响应 seek 与播放速率变化，所以保留每帧位置源。
     * 帧循环里每帧调它取位置（ExoPlayer 的 `currentPosition` 是音频时钟插值，代价低），
     * 只驱动唱臂半径、不进任何 Compose 状态 → 不引起重组。
     */
    var positionSource: (() -> Long)? = null
    var durationSource: (() -> Long)? = null

    /** 最新采到的位置/时长（供起播弧线取目标半径）。 */
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    /** 外部信号（主线程读写）。 */
    private var playing = false
    private var pendingCover: String? = null
    private var currentTrackId: String? = null
    private var trackRevision = 0L
    private var pendingAt = 0L

    private var job: Job? = null
    private var frameJob: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { loop() }
        // **转盘角度必须有独立的帧循环**：积分只在 `tweenTo` 里做的话，
        // `playing` 阶段停在 `await {}`（不跑 tween）时盘就不转了
        // —— 实测两帧截图在标签区 0 差异，就是这个问题。
        frameJob = scope.launch { frameLoop() }
    }

    /**
     * 帧循环：把 [platterSpeed]（度/秒）连续积分进 [platterAngle]。
     *
     * 这条循环**始终在跑**（不论停在哪个阶段）：起播时从 0 加速、暂停时减速到 0、
     * 播放中匀速 —— 与实验室的 `frameLoop` 同一套做法。
     * 写 [platterAngle] 只触发**重绘**（它只在 graphicsLayer/layout 的 lambda 里读），
     * 不引起重组，所以逐帧写它是安全的。
     */
    private suspend fun frameLoop() {
        var last = 0L
        var armLoggedAt = 0L
        var armLoggedR = Float.NaN
        while (true) {
            withFrameNanos { ns ->
                if (last != 0L && platterSpeed > 0.01f) {
                    val dt = (ns - last) / 1_000_000_000f
                    platterAngle = (platterAngle + platterSpeed * dt) % 360f
                }
                // ── 播放段：唱头半径与播放进度**线性绑定**（规格 §3.2 段 2）──
                if (last != 0L && phase == VinylPhase.playing) {
                    positionSource?.let { src -> lastPositionMs = src.invoke() }
                    durationSource?.let { src -> lastDurationMs = src.invoke() }
                    if (lastDurationMs > 0L) {
                        writeTipRadius(
                            VinylGeo.radiusForProgress(lastPositionMs.toFloat() / lastDurationMs),
                        )
                    }
                }
                last = ns
                // ── ARM 日志（与实验室同契约）：半径一变就打，节流 ≥85ms ──
                val ms = ns / 1_000_000L
                if ((armLoggedR.isNaN() || kotlin.math.abs(tipRadius - armLoggedR) > 1e-6f) &&
                    (armLoggedR.isNaN() || ms - armLoggedAt >= 85L)
                ) {
                    armLoggedR = tipRadius
                    armLoggedAt = ms
                    Log.i(
                        "VinylLab",
                        "ARM r=%.4f deg=%.2f".format(tipRadius, VinylGeo.armAngleForRadius(tipRadius)),
                    )
                }
            }
        }
    }

    /** 写半径的**统一入口**：同时更新契约值 [tipRadius] 与量化绘制值 [drawTipRadius]。 */
    private fun writeTipRadius(r: Float) {
        tipRadius = r
        drawTipRadius = kotlin.math.round(r / VinylGeo.DRAW_QUANT_D) * VinylGeo.DRAW_QUANT_D
    }

    // ───────────────────────── 外部信号 ─────────────────────────

    fun setPlaying(v: Boolean) {
        playing = v
    }

    /**
     * 曲目切换。**首次装载不算换片**（从无到有直接把封面摆上去，不演抬针回臂）。
     * 同一首歌重复上报时忽略（切歌→seek→回到同一首会重复触发）。
     *
     * ⚠️ 方法名不能叫 `setCover`：`cover` 属性的生成 setter 就是 `setCover`，会 JVM 签名冲突。
     */
    fun onTrackChanged(url: String, trackId: String = url) {
        if (trackId == currentTrackId) return
        val first = currentTrackId == null
        currentTrackId = trackId
        trackRevision++
        if (first) {
            cover = url; coverOld = url; coverNew = url
            return
        }
        pendingCover = url
        pendingAt = System.nanoTime()
    }

    /** 暂停时的进度采样；播放段由 frameLoop 细粒度采样，不预抬臂接管片尾。 */
    fun onPosition(positionMs: Long, durationMs: Long) {
        lastPositionMs = positionMs
        lastDurationMs = durationMs
    }

    /** 回到待机（退出播放页等场景可选调用）。 */
    fun reset() {
        playing = false
        pendingCover = null
        platterSpeed = 0f
        platterAngle = 0f
        writeTipRadius(VinylGeo.R_STANDBY)
        swapOutAlpha = 1f; swapOutDx = 0f; swapInAlpha = 1f; swapInDx = 0f
        coverOld = cover; coverNew = cover
        enter(VinylPhase.idle)
    }

    // ───────────────────────── 主循环 ─────────────────────────

    /** 起播弧线的起点半径与目标半径（进入 armCue 时记下；armLower 沿用同一条曲线）。 */
    private var arcStart = VinylGeo.R_STANDBY
    private var arcTargetR = VinylGeo.R_OUT

    /**
     * **一条缓动曲线横跨多个阶段**：只走 [startFrac]→[endFrac] 那一段（曲线是整条
     * [START_ARC_EASING]），回调喂**整条曲线的进度**、由调用方算半径 ——
     * 于是拆阶段不会在接缝处引入半径/速度的不连续（v1 的「顿挫」根因就是把一条弧
     * 拆成两段各自缓动，每段末速都归零）。实跑时长 = 跨度 × [START_ARC_MS]。
     */
    private suspend fun arcTween(startFrac: Float, endFrac: Float, sink: (Float) -> Unit) {
        val span = (endFrac - startFrac).coerceAtLeast(1e-4f)
        val durMs = span * START_ARC_MS
        val t0 = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val p = ((now - t0) / (durMs * 1_000_000f)).coerceIn(0f, 1f)
            sink(startFrac + span * p)
            if (p >= 1f) break
        }
        sink(endFrac)
    }

    private suspend fun loop() {
        while (true) {
            when (phase) {
                VinylPhase.idle -> {
                    writeTipRadius(VinylGeo.R_STANDBY)
                    await { playing || pendingCover != null }
                    // 停在待机时换来的新片：先演换片，再起播
                    if (pendingCover != null) {
                        awaitTrackQuiet()
                        enter(VinylPhase.discSwap)
                    } else enter(VinylPhase.discAccel)
                }

                VinylPhase.discAccel -> {
                    tweenSpeed(VinylGeo.DEG_PER_SEC, VinylPhase.discAccel.ms, FastOutLinearInEasing)
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.armCue)
                }

                // ── 段 1 · 起播：待机 → 外圈起点。**一条缓动曲线横跨 armCue + armLower**，
                //    不再是 v1 的「520ms 缓动 + 380ms 线性」两段相接（接缝处末速归零又起步，
                //    就是用户投诉的「先快后停再动」）。曲目标半径取**当前进度的半径**：
                //    从头播 = R_OUT（规格 §3.2 段 1），中途恢复 = 直接落到对应半径、不跳。
                VinylPhase.armCue -> {
                    arcStart = tipRadius
                    // 曲线目标 = **当前进度的半径**：从头播就是 R_OUT（规格 §3.2 段 1），
                    // 中途恢复则直接落到对应半径、落针后不跳。
                    arcTargetR = if (lastDurationMs > 0L) {
                        VinylGeo.radiusForProgress(lastPositionMs.toFloat() / lastDurationMs)
                    } else {
                        VinylGeo.R_OUT
                    }
                    arcTween(0f, VinylPhase.armCue.ms.toFloat() / START_ARC_MS) { frac ->
                        writeTipRadius(
                            arcStart + (arcTargetR - arcStart) * START_ARC_EASING.transform(frac),
                        )
                    }
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.armLower)
                }

                // 段 1 后半：沿**同一条曲线**走完（唱针下压已按方案 §3.1 删除）。
                VinylPhase.armLower -> {
                    arcTween(VinylPhase.armCue.ms.toFloat() / START_ARC_MS, 1f) { frac ->
                        writeTipRadius(
                            arcStart + (arcTargetR - arcStart) * START_ARC_EASING.transform(frac),
                        )
                    }
                    writeTipRadius(arcTargetR)
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.playing)
                }

                // ── 段 2 · 播放：半径由帧循环按进度线性绑定（见 frameLoop）──
                VinylPhase.playing -> {
                    await { !playing || pendingCover != null }
                    enter(VinylPhase.armLift)
                }

                VinylPhase.armLift -> {
                    // 抬臂：半径向外挪一点（看得见的「离盘」动作；唱针/下压已删）
                    tweenTo(tipRadius, tipRadius + VinylGeo.R_LIFT, VinylPhase.armLift.ms, LinearOutSlowInEasing) {
                        writeTipRadius(it)
                    }
                    leaveTo(VinylPhase.armReturn)
                }

                VinylPhase.armReturn -> {
                    tweenTo(tipRadius, VinylGeo.R_STANDBY, VinylPhase.armReturn.ms, FastOutSlowInEasing) {
                        writeTipRadius(it)
                    }
                    leaveTo(VinylPhase.discDecel)
                }

                VinylPhase.discDecel -> {
                    tweenSpeed(0f, VinylPhase.discDecel.ms, LinearOutSlowInEasing)
                    // 快速切歌仅延后视觉换片，音频仍由 PlayerHost 即时切换。
                    if (pendingCover != null) awaitTrackQuiet()
                    // 减速走完才换片（新片必须在静止状态滑入）
                    leaveTo(if (pendingCover != null) VinylPhase.discSwap else VinylPhase.idle)
                }

                VinylPhase.discSwap -> {
                    swapStep()
                    if (pendingCover != null) {
                        awaitTrackQuiet()
                        leaveTo(VinylPhase.discSwap)
                    } else {
                        leaveTo(if (playing) VinylPhase.discAccel else VinylPhase.idle)
                    }
                }
            }
        }
    }

    /** 计时阶段收尾：打日志、进入下一个阶段。 */
    private fun leaveTo(next: VinylPhase) {
        logPhase(phase, phase.ms)
        enter(next)
    }

    private fun enter(p: VinylPhase) {
        phase = p
        logPhase(p, 0)
    }

    /** 换片：旧封面左滑淡出 → 新封面右滑淡入；结束才把 [cover] 换成新的。 */
    private suspend fun swapStep() {
        var next = pendingCover ?: cover
        var revision = trackRevision
        coverOld = cover
        coverNew = next
        swapOutAlpha = 1f; swapOutDx = 0f; swapInAlpha = 0f; swapInDx = 0.10f
        tweenTo(0f, 1f, SWAP_MS, FastOutSlowInEasing) { t ->
            // 新封面尚未入场时可重定位；入场后新请求留给下一轮，不能清掉。
            if (t <= 0.5f && trackRevision != revision) {
                next = pendingCover ?: next
                revision = trackRevision
                coverNew = next
            }
            val out = (t / 0.5f).coerceIn(0f, 1f)
            val inn = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
            swapOutAlpha = 1f - out
            swapOutDx = -0.10f * out
            swapInAlpha = inn
            swapInDx = 0.10f * (1f - inn)
        }
        cover = next
        coverOld = next
        swapOutAlpha = 1f; swapOutDx = 0f; swapInAlpha = 1f; swapInDx = 0f
        if (trackRevision == revision) pendingCover = null
    }

    private suspend fun awaitTrackQuiet() {
        await { System.nanoTime() - pendingAt >= 3_000_000_000L }
    }

    /** 帧驱动等待：每帧重算一次谓词，条件成立才返回（挂起，不空转）。 */
    private suspend fun await(pred: () -> Boolean) {
        while (!pred()) withFrameNanos { }
    }

    private suspend fun tweenSpeed(target: Float, ms: Int, easing: Easing) {
        tweenTo(platterSpeed, target, ms, easing) { platterSpeed = it }
    }

    /**
     * 定长 tween：帧驱动、时长严格 = ms。
     *
     * 转盘角度的积分**不在这里**（见 [frameLoop]）—— 那条循环始终在跑，
     * 所以暂停/播放都从当前角度续，不回零。
     */
    private suspend fun tweenTo(from: Float, to: Float, ms: Int, easing: Easing, sink: (Float) -> Unit) {
        if (ms <= 0) { sink(to); return }
        val t0 = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val p = ((now - t0) / (ms * 1_000_000f)).coerceIn(0f, 1f)
            sink(from + (to - from) * easing.transform(p))
            if (p >= 1f) break
        }
        sink(to)
    }
}

// ───────────────────────────── 绘制（v2） ─────────────────────────────

/** 黑胶盘面填充：**必须明显暗于页面底色**，否则唱片读成一块磨砂玻璃。 */
private fun vinylFillColor(bg: Color, isDark: Boolean): Color =
    lerp(bg, Color.Black, if (isDark) 0.45f else 0.62f)

private fun nativePaint(color: Color, alpha: Float, blurPx: Float): android.graphics.Paint =
    android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
        if (blurPx > 0f) maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

/**
 * 两影位移（px）。与 `Shade.kt` 的 `shadowVectors` **同一套算法**：
 * 组件自己的 offset 相对 6dp 基准等比取用光照长度。
 */
private fun DrawScope.shadeVec(light: Lighting, offsetPx: Float): FloatArray {
    val w = offsetPx / (REF_OFFSET_DP * density)
    return floatArrayOf(
        light.ux * light.lenDark * w,          // 暗影 dx
        light.uy * light.lenDark * w,          // 暗影 dy
        -light.ux * light.lenLight * w,        // 亮影 dx
        -light.uy * light.lenLight * w,        // 亮影 dy
    )
}

/**
 * 唱片纹路（v2 · **双层面板**）：
 * - **外层**（[VinylGeo.GROOVE_OUTER]，0.361..0.500）：参考照片那圈疏密相间的弧线；
 * - **内层**（[VinylGeo.GROOVE_INNER]，0.240..0.320）：**致密 + 更深**（先铺一整块更深的
 *   底色，方案 §2 明确允许「内层干脆用一个色块」）；
 * - **过渡带**（0.320 → 0.361）：留空 + 一条 1.8dp 的分隔弧（[VinylGeo.TRANSITION_R]）。
 *
 * 三条硬约束（实验室五轮评审量出来的，继续有效）：
 *  ① 两段笔画都必须**比盘面暗**（深色盘面上借新拟物亮影只会变亮）；
 *  ② **不用 `Brush.sweepGradient`** —— 每圈一个 shader，playing 中位帧 21→31ms、掉帧 12.6%；
 *     接缝用「两段纯色半弧各外扩 2°」盖住，零 shader；
 *  ③ 沟槽是旋转对称的，**永远不放进旋转层**。
 */
private fun DrawScope.drawGroovesV2(
    lighting: Lighting,
    discPx: Float,
    bg: Color,
    isDark: Boolean,
) {
    val fill = vinylFillColor(bg, isDark)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val lit = lerp(fill, Color.Black, 0.135f * lighting.alphaLight)
    val shade = lerp(fill, Color.Black, 0.27f * lighting.alphaDark)
    // 内层「更深」：先铺一整块更深的底（规格允许「干脆用一个色块」），再压致密纹路
    val innerBlock = lerp(fill, Color.Black, 0.30f * (0.5f + 0.5f * lighting.alphaDark))
    val litIn = lerp(innerBlock, Color.Black, 0.30f * lighting.alphaLight)
    val shadeIn = lerp(innerBlock, Color.Black, 0.45f * lighting.alphaDark)

    drawCircle(innerBlock, radius = VinylGeo.INNER_OUTER * discPx, center = Offset(cx, cy))
    val stroke = VinylGeo.GROOVE_W.dp.toPx()
    for (rRatio in VinylGeo.GROOVE_OUTER) grooveRing(cx, cy, rRatio * discPx, stroke, lit, shade)
    for (rRatio in VinylGeo.GROOVE_INNER) grooveRing(cx, cy, rRatio * discPx, stroke, litIn, shadeIn)
    // 过渡带的分隔弧：比普通沟槽粗一档（"界线"而不是沟槽）
    grooveRing(
        cx, cy, VinylGeo.TRANSITION_R * discPx, VinylGeo.TRANSITION_W.dp.toPx(),
        lerp(innerBlock, Color.White, 0.055f * lighting.alphaLight),
        lerp(innerBlock, Color.Black, 0.34f * lighting.alphaDark),
    )
}

/** 一圈沟槽：两段纯色半弧各外扩 2°（盖住 0°/180° 处 1–2px 的抗锯齿缺口），零 shader。 */
private fun DrawScope.grooveRing(
    cx: Float, cy: Float, r: Float, strokePx: Float, lit: Color, shade: Color,
) {
    if (r <= 0f) return
    val tl = Offset(cx - r, cy - r)
    val sz = androidx.compose.ui.geometry.Size(2f * r, 2f * r)
    val w = strokePx
    drawArc(
        color = lit, startAngle = -2f, sweepAngle = 184f, useCenter = false,
        topLeft = tl, size = sz, style = Stroke(width = w, cap = StrokeCap.Butt),
    )
    drawArc(
        color = shade, startAngle = 178f, sweepAngle = 184f, useCenter = false,
        topLeft = tl, size = sz, style = Stroke(width = w, cap = StrokeCap.Butt),
    )
}

/** 盘面填充 + 盘缘界线。 */
private fun DrawScope.drawDiscBody(bg: Color, isDark: Boolean, discPx: Float) {
    val fill = vinylFillColor(bg, isDark)
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    drawCircle(fill, radius = r - 0.5f, center = c)
    val rim = 1.4.dp.toPx()
    drawCircle(
        color = lerp(fill, Color.Black, 0.45f),
        radius = r - rim / 2f, center = c, style = Stroke(width = rim),
    )
}

/**
 * M3 elevation 影：两层柔和阴影（ambient 长而虚 + key 短而实），中性黑、只做透明度叠加。
 * **不是**新拟物的「一亮一暗两影」——这里没有亮影，方向固定向下（M3 语汇：光从上方来）。
 */
private fun DrawScope.m3Elevation(
    path: Path, keyDy: Float, keyBlur: Float, ambDy: Float, ambBlur: Float, alphaScale: Float,
) {
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save(); nc.translate(0f, ambDy)
        nc.drawPath(path.asAndroidPath(), nativePaint(Color.Black, 0.14f * alphaScale, ambBlur))
        nc.restore()
        nc.save(); nc.translate(0f, keyDy)
        nc.drawPath(path.asAndroidPath(), nativePaint(Color.Black, 0.20f * alphaScale, keyBlur))
        nc.restore()
    }
}

/** M3 Expressive 底座：一个完整色块，不叠加金属高光或同心装饰。 */
private fun DrawScope.drawPivotBaseV2(
    c: Offset, r: Float, palette: M3Palette, alphaScale: Float,
) {
    val base = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    m3Elevation(base, 2.dp.toPx(), 3.dp.toPx(), 5.dp.toPx(), 8.dp.toPx(), alphaScale)
    drawPath(base, palette.base)
}

/**
 * 保留针尖的既有半径轨迹，反解连接点：唱头从连接点沿内折 20° 的轴伸出。
 * ARM_LEN 是支点到针尖的有效长度；臂管与唱头构成固定刚体，不绕唱头中心折转。
 */
private fun DrawScope.drawTonearmV2(
    palette: M3Palette,
    alphaScale: Float,
    discPx: Float,
    tipRadius: Float,
) {
    val pivot = Offset(VinylGeo.pivotX(discPx), VinylGeo.pivotY(discPx))
    val effectiveLength = discPx * VinylGeo.ARM_LEN
    val tipAngle = Math.toRadians(VinylGeo.armAngleForRadius(tipRadius).toDouble())
    val offsetAngle = Math.toRadians(VinylGeo.HEAD_OFFSET_DEG.toDouble())
    val headL = discPx * VinylGeo.HEAD_L
    val headW = discPx * VinylGeo.HEAD_W
    val armW = discPx * VinylGeo.ARM_W
    val lateral = headL * sin(offsetAngle)
    val axial = kotlin.math.sqrt(effectiveLength * effectiveLength - lateral * lateral)
    val armAngle = tipAngle - kotlin.math.atan2(lateral, axial.toDouble())
    val armLength = axial - headL * cos(offsetAngle)
    val joint = pivot + Offset(
        (armLength * cos(armAngle)).toFloat(),
        (armLength * sin(armAngle)).toFloat(),
    )
    val headRotation = Math.toDegrees(armAngle + offsetAngle).toFloat() - 90f

    // 细胶囊臂管：单一色块，短柔影，不再画亮条。
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save()
        nc.translate(0f, 2.dp.toPx())
        nc.drawLine(pivot.x, pivot.y, joint.x, joint.y,
            nativePaint(Color.Black, 0.16f * alphaScale, 4.dp.toPx()).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = armW
                strokeCap = android.graphics.Paint.Cap.ROUND
            })
        nc.restore()
    }
    drawLine(palette.arm, pivot, joint, strokeWidth = armW, cap = StrokeCap.Round)

    // 局部 +y 从连接点向针尖延伸；旋转支点就是 joint。
    val corner = headW * 0.25f
    val head = Path().apply {
        addRoundRect(RoundRect(
            joint.x - headW / 2f, joint.y - armW * 0.20f,
            joint.x + headW / 2f, joint.y + headL,
            androidx.compose.ui.geometry.CornerRadius(corner, corner),
        ))
        // Finger Lift：外侧短胶囊与唱头合为同一轮廓、同一色块。
        val liftTop = joint.y + headL * 0.34f
        val liftHeight = headW * 0.24f
        addRoundRect(RoundRect(
            joint.x + headW * 0.38f, liftTop,
            joint.x + headW * 0.82f, liftTop + liftHeight,
            androidx.compose.ui.geometry.CornerRadius(liftHeight / 2f, liftHeight / 2f),
        ))
    }
    rotate(headRotation, joint) {
        m3Elevation(head, 2.dp.toPx(), 3.dp.toPx(), 4.dp.toPx(), 7.dp.toPx(), alphaScale)
        drawPath(head, palette.head)
    }
}

/** 三个独立完整色块均来自主题角色。 */
private class M3Palette(val base: Color, val arm: Color, val head: Color)

/**
 * 黑胶转盘。调用方给**唱片直径**（[discD]）与状态机（[state]），
 * 封面按 [VinylGeo.COVER_RATIO] 缩小后贴进片心、随 [VinylTurntableState.platterAngle] 自转。
 *
 * [coverContent] 负责画真实封面：`url`（空串=无封面，画占位）、`alpha`（换片淡入淡出）、
 * `dxFrac`（换片滑动量，**单位是 D**，调用方乘 `discD.toPx()` 即得像素）。
 */
@Composable
fun VinylTurntable(
    state: VinylTurntableState,
    discD: Dp,
    coverContent: @Composable (url: String, alpha: Float, dxFrac: Float) -> Unit,
) {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val shadowAlphaState = LocalShadeShadowAlpha.current
    val d = discD
    val holeD = d * VinylGeo.HOLE_RATIO
    val coverD = d * VinylGeo.COVER_RATIO

    val scheme = MaterialTheme.colorScheme
    val palette = M3Palette(
        base = scheme.surfaceContainerHighest,
        arm = scheme.secondaryContainer,
        head = scheme.secondary,
    )

    Box(
        Modifier.size(d * VinylGeo.STAGE_W, d * VinylGeo.STAGE_H),
        contentAlignment = Alignment.TopStart,
    ) {
        val offX = d * VinylGeo.DISC_CX - d / 2
        val offY = d * VinylGeo.DISC_CY - d / 2

        // ── 静止层 1：凸起底盘 + 黑胶盘面（不随自转）──
        Box(
            Modifier
                .graphicsLayer { translationX = offX.toPx(); translationY = offY.toPx() }
                .size(d)
                .drawBehind {
                    val lighting = DayLightHost.current(colors.isDark)
                    // 与 shadeSurface 同一套算法：外阴影（亮/暗两影）→ 盘面
                    val a = shadowAlphaState.floatValue
                    val v = shadeVec(lighting, 6.dp.toPx() * relief)
                    val bg = colors.background
                    val oval = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        nc.save(); nc.translate(v[2], v[3])
                        nc.drawPath(oval.asAndroidPath(), nativePaint(tempTint(colors.shadowLight, lighting.warmth), a * lighting.alphaLight, 12.dp.toPx() * lighting.blurLight))
                        nc.restore()
                        nc.save(); nc.translate(v[0], v[1])
                        nc.drawPath(oval.asAndroidPath(), nativePaint(tempTint(colors.shadowDark, lighting.warmth), a * lighting.alphaDark, 12.dp.toPx() * lighting.blurDark))
                        nc.restore()
                        nc.drawPath(oval.asAndroidPath(), nativePaint(bg, 1f, 0f))
                    }
                    drawDiscBody(bg, colors.isDark, d.toPx())
                },
        )

        // ── 静止层 2：双层纹理（旋转对称，永远不跟着转）──
        Box(
            Modifier
                .graphicsLayer { translationX = offX.toPx(); translationY = offY.toPx() }
                .size(d),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGroovesV2(DayLightHost.current(colors.isDark), d.toPx(), colors.background, colors.isDark)
            }
        }

        // ── 旋转层：只有封面圆（0.42 D，面积小 → 每帧重光栅化的代价低）──
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (offX + (d - coverD) / 2).toPx()
                    translationY = (offY + (d - coverD) / 2).toPx()
                }
                .size(coverD),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = state.platterAngle },
            ) {
                val inSwap = state.phase == VinylPhase.discSwap
                if (!inSwap) {
                    coverContent(state.cover, 1f, 0f)
                } else {
                    if (state.swapOutAlpha > 0.01f) {
                        coverContent(state.coverOld, state.swapOutAlpha, state.swapOutDx)
                    }
                    if (state.swapInAlpha > 0.01f) {
                        coverContent(state.coverNew, state.swapInAlpha, state.swapInDx)
                    }
                }
            }
        }

        // ── 静止层 3：中心轴孔（暗凹陷，压在旋转层之上）──
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (offX + (d - holeD) / 2).toPx()
                    translationY = (offY + (d - holeD) / 2).toPx()
                }
                .size(holeD)
                .drawBehind {
                    val lighting = DayLightHost.current(colors.isDark)
                    val deep = lerp(colors.background, Color.Black, if (colors.isDark) 0.78f else 0.66f)
                    val wall = lerp(deep, Color.Black, 0.55f)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    val oval = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
                    val v = shadeVec(lighting, 2.dp.toPx() * relief)
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        nc.drawPath(oval.asAndroidPath(), nativePaint(deep, 1f, 0f))
                        nc.save()
                        nc.clipPath(oval.asAndroidPath())
                        val complement = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f))
                            addPath(oval)
                        }
                        nc.save(); nc.translate(v[0] * 0.9f, v[1] * 0.9f)
                        nc.drawPath(complement.asAndroidPath(), nativePaint(wall, 0.9f, 0.6.dp.toPx()))
                        nc.restore()
                        nc.restore()
                    }
                },
        )

        // ── 静止层 4：底座转轴（M3 完整圆形色块）——**独立一层**：它不随唱臂动，显示列表可缓存
        //    （与「底盘 vs 旋转封面」同一条性能纪律；两层大模糊每帧重录实测把帧时间拖爆）。
        Canvas(Modifier.fillMaxSize()) {
            drawPivotBaseV2(
                Offset(VinylGeo.pivotX(d.toPx()), VinylGeo.pivotY(d.toPx())),
                d.toPx() * VinylGeo.PIVOT_BASE_R,
                palette,
                shadowAlphaState.floatValue,
            )
        }

        // ── 静止层 5：唱臂 + 唱头（M3 刚体，绕底座转动）──
        Canvas(Modifier.fillMaxSize()) {
            drawTonearmV2(
                palette = palette,
                alphaScale = shadowAlphaState.floatValue,
                discPx = d.toPx(),
                tipRadius = state.drawTipRadius,
            )
        }
    }
}
