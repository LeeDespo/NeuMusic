package com.neumusic.player.ui.player

import android.util.Log
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
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
import com.neumusic.player.shade.ShadeColors
import com.neumusic.player.shade.tempTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 黑胶转盘（主工程）。几何、光影画法、阶段机全部从唱片实验室 `~/Documents/VinylLab`
 * 移植，只换两处（规格 §8 说好的唯一差异）：
 *
 *  1. **光照来源**：实验室是 ShadeLab 的绝对 dp 版（`LightState`），这里换成主工程
 *     `shade/DayLight.kt` 的**倍数**版 [Lighting] —— 位移/模糊按
 *     `offsetPx / (REF_OFFSET_DP × density)` 缩放，与 `shadeSurface` 同一套算法、
 *     同一个 `DayLightHost`，所以设置里的光影时刻/标定对转盘同样生效。
 *  2. **驱动源**：实验室是假时间轴，这里是真播放器（`PlayerHost`）。
 *
 * ⚠️ **本组件只跟随播放、不驱动播放**（这是对规格 §5.3 的有意简化，理由如下）：
 * 规格 §5.3 要求「armLower 结束才 play()、进 discSwap 时 nextPrepared()」，那需要给
 * `PlayerHost` 新增 `nextPrepared()` / `setAutoAdvanceSuppressed()` 并接管全部 7 个
 * 播放入口。本工程已有 `onPlaybackStateChanged(STATE_ENDED) → advance()` 这套成熟机制，
 * 两套「谁负责换歌」并存必然双切（规格 §5.2 自己也警告过）。所以这里**只观察**：
 * `PlayerHost` 照旧换歌，转盘把「抬针 → 回臂 → 减速 → 换片 → 重新起播」演出来。
 * 阶段机不碰播放，就不会和 `advance()` 抢。
 */
enum class VinylPhase(val ms: Int) {
    idle(0), discAccel(900), armCue(900), armLower(260),
    playing(0), armLift(240), armReturn(620), discDecel(700), discSwap(620),
}

/**
 * 「片尾提前量」= 抬针 + 回臂 + 减速 = 240 + 620 + 700 = **1560 ms**。
 *
 * 规格的 `AUTO_LEAD_MS = 4240` 是**换片全套**（含 discSwap 与重新起播），那属于
 * 「动画自己负责换歌」的算法。本组件只跟随播放：唱针要在**歌真的放完之前**抬起来、
 * 盘在换片前停住，而「换片」本身得等 `PlayerHost` 把新曲目送来（ENDED→advance）
 * 才有新封面可画。所以提前量只需要这 1560 ms —— 走到「时长 − 1560」时抬针，
 * 正好在歌结束时臂已归位、盘已停，接着换片。
 */
const val END_LEAD_MS: Int = 240 + 620 + 700

/** 换片段时长（旧封面滑出 / 新封面滑入）。 */
private const val SWAP_MS = 620

/**
 * 唱片几何：全部以**唱片直径 D** 为单位，数值来自参考图实测 + 实验室四轮视觉评审校准
 * （每一版的读数都记在实验室 README 的「第 N 轮改了什么」里），移植时原样照搬。
 */
object VinylGeo {
    /** 主工程原有的盘径常量（`PlayerScreen.DISC_SIZE = 264.dp`）。 */
    const val DISC_D = 264f

    /** 片心标签 = 封面圆，直径 ÷ D（参考实测米色标签外缘 0.425，取 0.42）。 */
    const val COVER_RATIO = 0.42f

    /** 中心轴孔直径 ÷ D。 */
    const val HOLE_RATIO = 0.030f

    /**
     * 沟槽半径表（单位 D）——**疏—密两段、中间留空白带**。
     * 参考照片反校正实测：内簇 0.222/0.253/0.270 → 0.28–0.33 **无弧线** → 0.361 起密集外圈。
     * 落针点 0.480 就在这张表上，所以针尖正好压在沟槽里。
     */
    val GROOVE_RADII = floatArrayOf(
        0.222f, 0.253f, 0.270f,
        0.361f, 0.372f, 0.383f, 0.394f, 0.399f, 0.407f, 0.418f,
        0.432f, 0.447f, 0.454f, 0.466f, 0.480f, 0.490f, 0.500f,
    )
    const val GROOVE_W = 1f

    /** 唱臂支点：到**盘心** 0.614 D @ −39°（支点底盘内缘 0.514 D 清开盘缘）。 */
    const val PIVOT_DIST = 0.614f
    const val PIVOT_ANGLE = -39f
    /** 支点 → 唱针尖 0.44 D（由参考的支点/待机针尖两点反推）。 */
    const val ARM_LEN = 0.44f

    /** 臂角由目标半径反解（规格 §1.1：支点处内角 180°−|α−θp|，所以 cos 项取**加号**）。 */
    fun armAngleForRadius(r: Float): Float {
        val cosArg = ((r * r - PIVOT_DIST * PIVOT_DIST - ARM_LEN * ARM_LEN) /
            (2f * PIVOT_DIST * ARM_LEN)).coerceIn(-1f, 1f)
        return PIVOT_ANGLE + Math.toDegrees(acos(cosArg.toDouble())).toFloat()
    }

    /** 待机：针尖贴盘缘外 0.075 D。 */
    const val STANDBY_R = 0.575f
    /** armCue 前半程：摆到外圈（盘缘外一点）。 */
    const val CUE_R = 0.550f
    /** 落针：0.480 D，**压在沟槽上**。 */
    const val DROP_R = 0.480f

    val ARM_A_STANDBY = armAngleForRadius(STANDBY_R)
    val ARM_A_CUE = armAngleForRadius(CUE_R)
    val ARM_A_DROP = armAngleForRadius(DROP_R)

    /** 支点底盘 0.10 D（凸起圆盘）、唱头 / 臂管尺寸。 */
    const val PIVOT_BASE_R = 0.10f
    const val HEAD_L = 0.135f
    const val HEAD_W = 0.105f
    const val ARM_W = 0.029f

    /** 舞台（含唱臂扫掠）÷ D；盘心在 (DISC_CX, DISC_CY)——**单位是 D**，不是舞台比例。 */
    const val STAGE_W = 1.225f
    const val STAGE_H = 1.100f
    const val DISC_CX = 0.50f
    const val DISC_CY = 0.56f

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
 *  - [setCover]：`PlayerHost.current` 的封面 —— 曲目切换
 *  - [onPosition]：`位置 + 时长` —— 走到「时长 − 提前量」就抬针，为片尾换片让路
 *
 * 阶段链（与实验室一致）：
 *  - 起播：discAccel(900) → armCue(900) → armLower(260) → playing
 *  - 停：armLift(240) → armReturn(620) → discDecel(700) → idle
 *  - 换片：armLift → armReturn → discDecel → discSwap(620) → （还在播则）discAccel → …
 *
 * **等待一律走 `withFrameNanos`**（挂起，不空转），且在每帧重新判断谓词 ——
 * 没有「pending 未消费导致自转移」的坑（实验室那版用事件通道 + pending，是因为它要
 * 校验 24 条转移表；主工程只需要「跟随状态」）。
 */
class VinylTurntableState(private val scope: CoroutineScope) {

    var phase by mutableStateOf(VinylPhase.idle); private set
    var armAngle by mutableFloatStateOf(VinylGeo.ARM_A_STANDBY); private set
    var stylusDown by mutableFloatStateOf(0f); private set
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

    /** 外部信号（主线程读写）。 */
    private var playing = false
    private var pendingCover: String? = null
    private var armedForSwap = false
    private var lastEndParkAt = 0L

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
        while (true) {
            withFrameNanos { ns ->
                if (last != 0L && platterSpeed > 0.01f) {
                    val dt = (ns - last) / 1_000_000_000f
                    platterAngle = (platterAngle + platterSpeed * dt) % 360f
                }
                last = ns
            }
        }
    }

    // ───────────────────────── 外部信号 ─────────────────────────

    fun setPlaying(v: Boolean) {
        playing = v
        if (!v) armedForSwap = false
    }

    /**
     * 曲目切换。**首次装载不算换片**（从无到有直接把封面摆上去，不演抬针回臂）。
     * 同一首歌重复上报时忽略（切歌→seek→回到同一首会重复触发）。
     *
     * ⚠️ 方法名不能叫 `setCover`：`cover` 属性的生成 setter 就是 `setCover`，会 JVM 签名冲突。
     */
    fun onTrackChanged(url: String) {
        if (url == cover && pendingCover == null) return
        if (cover.isEmpty()) {
            cover = url; coverOld = url; coverNew = url
            return
        }
        pendingCover = url
    }

    /** 位置采样：走到「时长 − 提前量」就抬针（一次片尾只触发一次）。 */
    fun onPosition(positionMs: Long, durationMs: Long) {
        if (!playing || durationMs <= 0L) return
        if (phase != VinylPhase.playing) return
        val lead = minOf(END_LEAD_MS.toLong(), (durationMs / 2).coerceAtLeast(1L))
        if (positionMs >= durationMs - lead) {
            val now = System.nanoTime()
            if (now - lastEndParkAt > 2_000_000_000L) {   // 2s 去抖
                lastEndParkAt = now
                armedForSwap = true
            }
        }
    }

    /** 回到待机（退出播放页等场景可选调用）。 */
    fun reset() {
        playing = false
        pendingCover = null
        armedForSwap = false
        platterSpeed = 0f
        platterAngle = 0f
        stylusDown = 0f
        armAngle = VinylGeo.ARM_A_STANDBY
        swapOutAlpha = 1f; swapOutDx = 0f; swapInAlpha = 1f; swapInDx = 0f
        coverOld = cover; coverNew = cover
        enter(VinylPhase.idle)
    }

    // ───────────────────────── 主循环 ─────────────────────────

    private suspend fun loop() {
        while (true) {
            when (phase) {
                VinylPhase.idle -> {
                    armedForSwap = false
                    await { playing }
                    // 停在待机时换来的新片：先演换片，再起播
                    if (pendingCover != null) enter(VinylPhase.discSwap)
                    else enter(VinylPhase.discAccel)
                }

                VinylPhase.discAccel -> {
                    tweenSpeed(VinylGeo.DEG_PER_SEC, VinylPhase.discAccel.ms, FastOutLinearInEasing)
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.armCue)
                }

                VinylPhase.armCue -> {
                    tweenTo(armAngle, VinylGeo.ARM_A_CUE, 520, FastOutSlowInEasing) { armAngle = it }
                    tweenTo(armAngle, VinylGeo.ARM_A_DROP, 380, LinearEasing) { armAngle = it }
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.armLower)
                }

                VinylPhase.armLower -> {
                    tweenTo(stylusDown, 1f, VinylPhase.armLower.ms, FastOutLinearInEasing) { stylusDown = it }
                    leaveTo(if (!playing) VinylPhase.armLift else VinylPhase.playing)
                }

                VinylPhase.playing -> {
                    await { !playing || pendingCover != null || armedForSwap }
                    enter(VinylPhase.armLift)
                }

                VinylPhase.armLift -> {
                    tweenTo(stylusDown, 0f, VinylPhase.armLift.ms, LinearOutSlowInEasing) { stylusDown = it }
                    leaveTo(VinylPhase.armReturn)
                }

                VinylPhase.armReturn -> {
                    tweenTo(armAngle, VinylGeo.ARM_A_STANDBY, VinylPhase.armReturn.ms, FastOutSlowInEasing) { armAngle = it }
                    leaveTo(VinylPhase.discDecel)
                }

                VinylPhase.discDecel -> {
                    tweenSpeed(0f, VinylPhase.discDecel.ms, LinearOutSlowInEasing)
                    // 减速走完才换片（新片必须在静止状态滑入）
                    leaveTo(if (pendingCover != null) VinylPhase.discSwap else VinylPhase.idle)
                }

                VinylPhase.discSwap -> {
                    swapStep()
                    leaveTo(if (playing) VinylPhase.discAccel else VinylPhase.idle)
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
        val next = pendingCover ?: cover
        coverOld = cover
        coverNew = next
        swapOutAlpha = 1f; swapOutDx = 0f; swapInAlpha = 0f; swapInDx = 0.10f
        tweenTo(0f, 1f, SWAP_MS, FastOutSlowInEasing) { t ->
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
        pendingCover = null
        armedForSwap = false
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

// ───────────────────────────── 绘制 ─────────────────────────────

/** 黑胶盘面填充：**必须明显暗于页面底色**，否则唱片读成一块磨砂玻璃。 */
private fun vinylFillColor(bg: Color, isDark: Boolean): Color =
    lerp(bg, Color.Black, if (isDark) 0.45f else 0.62f)

/** 金属亮色（臂管杆身）：**不透明**——半透明的近白合成到页面底色上会等于底色（实验室实测过）。 */
private fun metalHi(l: Lighting): Color = Color(0xFFF8FAFC)

/** 金属暗色（臂管的硬暗边、唱头体块、唱针楔块）。 */
private fun metalLo(l: Lighting): Color = Color(0xFF2A2E33)

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

/** 唱片纹路（与实验室同一套）：两段半弧各外扩 2°、平头端帽，接缝被覆盖且零 shader。 */
private fun DrawScope.drawGrooves(fill: Color, lighting: Lighting, discPx: Float) {
    val stroke = VinylGeo.GROOVE_W.dp.toPx()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val lit = lerp(fill, Color.Black, 0.135f * lighting.alphaLight)
    val shade = lerp(fill, Color.Black, 0.27f * lighting.alphaDark)
    for (rRatio in VinylGeo.GROOVE_RADII) {
        val r = rRatio * discPx
        val tl = Offset(cx - r, cy - r)
        val sz = androidx.compose.ui.geometry.Size(2f * r, 2f * r)
        drawArc(
            color = lit, startAngle = -2f, sweepAngle = 184f, useCenter = false,
            topLeft = tl, size = sz, style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        drawArc(
            color = shade, startAngle = 178f, sweepAngle = 184f, useCenter = false,
            topLeft = tl, size = sz, style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
    }
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

/** 支点底盘：硬边圆盘 + 盘内「左上吃光 / 右下吃暗」两条弧（强度配平过）。 */
private fun DrawScope.drawPivotBase(
    c: Offset, r: Float, bg: Color, lighting: Lighting, relief: Float, shadowAlpha: Float,
) {
    val oval = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    val v = shadeVec(lighting, 5.dp.toPx() * relief)
    val hi = metalHi(lighting); val lo = metalLo(lighting)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save(); nc.translate(v[0] * 0.9f, v[1] * 0.9f)
        nc.drawPath(oval.asAndroidPath(), nativePaint(lo, 0.42f * lighting.alphaDark * shadowAlpha, 3.dp.toPx()))
        nc.restore()
        val face = lerp(bg, Color.Black, 0.06f)
        nc.drawPath(oval.asAndroidPath(), nativePaint(face, 1f, 0f))
        nc.drawPath(
            oval.asAndroidPath(),
            nativePaint(lo, 0.80f * shadowAlpha, 0f).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.4.dp.toPx()
            },
        )
        nc.save()
        nc.clipPath(oval.asAndroidPath())
        pivotArc(nc, c, r * 0.74f, 225f, 150f, r * 0.20f, hi, 1f, 1.2.dp.toPx() * relief, v[2] * 0.7f, v[3] * 0.7f, shadowAlpha)
        pivotArc(nc, c, r * 0.74f, 45f, 150f, r * 0.20f, lo, 0.15f * lighting.alphaDark, 1.4.dp.toPx() * relief, v[0] * 0.7f, v[1] * 0.7f, shadowAlpha)
        nc.restore()
    }
}

/**
 * 盘内弧带：**必须用 `arcTo` + STROKE 描边**，不能用 `addOval` + fill ——
 * 后者会把整个内圆填满（实验室里踩过：盘内 46% 面积被刷暗，读成「白球上一团灰」）。
 * 屏幕系角度：0°=右、90°=下 → 受光弧中心 225°（左上）、暗弧 45°（右下）。
 */
private fun pivotArc(
    nc: android.graphics.Canvas, c: Offset, radius: Float,
    centerDeg: Float, sweepDeg: Float, armW: Float,
    color: Color, alpha: Float, blur: Float, dx: Float, dy: Float, shadowAlpha: Float,
) {
    val rect = android.graphics.RectF(c.x - radius, c.y - radius, c.x + radius, c.y + radius)
    val p = android.graphics.Path()
    p.arcTo(rect, centerDeg - sweepDeg / 2f, sweepDeg, true)
    nc.save()
    nc.translate(dx, dy)
    nc.drawPath(
        p,
        nativePaint(color, alpha * shadowAlpha, blur).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = armW
            strokeCap = android.graphics.Paint.Cap.ROUND
        },
    )
    nc.restore()
}

/**
 * 唱臂：臂管（满宽近黑 + 70% 宽亮杆身 = 两侧硬暗边）、唱头（渐变三面 + 整圈暗轮廓）、
 * 唱针（唱头外的小楔形 + 2 dp 红尖）。
 */
private fun DrawScope.drawTonearm(
    bg: Color, lighting: Lighting, relief: Float, shadowAlpha: Float,
    discPx: Float, armAngle: Float, stylusDown: Float,
) {
    val hi = metalHi(lighting); val lo = metalLo(lighting)
    val pivot = Offset(
        discPx * VinylGeo.DISC_CX, discPx * VinylGeo.DISC_CY,
    ) + Offset(
        discPx * VinylGeo.PIVOT_DIST * cos(Math.toRadians(VinylGeo.PIVOT_ANGLE.toDouble())).toFloat(),
        discPx * VinylGeo.PIVOT_DIST * sin(Math.toRadians(VinylGeo.PIVOT_ANGLE.toDouble())).toFloat(),
    )
    val armLen = discPx * VinylGeo.ARM_LEN
    val tip = Offset(
        pivot.x + armLen * cos(Math.toRadians(armAngle.toDouble())).toFloat(),
        pivot.y + armLen * sin(Math.toRadians(armAngle.toDouble())).toFloat(),
    )
    val armW = discPx * VinylGeo.ARM_W
    val headL = discPx * VinylGeo.HEAD_L
    val headW = discPx * VinylGeo.HEAD_W
    val pivotR = discPx * VinylGeo.PIVOT_BASE_R

    val dir = tip - pivot
    val len = dir.getDistance().coerceAtLeast(1f)
    val ux = dir.x / len; val uy = dir.y / len
    val nx = -uy; val ny = ux
    val v = shadeVec(lighting, 6.dp.toPx() * relief)

    val down = stylusDown.coerceIn(0f, 1f)
    val stL = headL * (0.30f + 0.10f * down)
    val headBack = Offset(tip.x - ux * stL, tip.y - uy * stL)
    val headFront = Offset(headBack.x - ux * headL, headBack.y - uy * headL)
    val tubeEnd = Offset(headFront.x + ux * headL * 0.45f, headFront.y + uy * headL * 0.45f)
    val shadowEnd = Offset(tip.x - ux * headL * 1.6f, tip.y - uy * headL * 1.6f)

    // 1. 台面投影（末端收在唱头体内，否则会拖出一块孤立的灰药丸）
    drawLine(
        color = lo.copy(alpha = 0.5f * lighting.alphaDark * shadowAlpha),
        start = Offset(pivot.x + v[0] * 0.55f, pivot.y + v[1] * 0.55f),
        end = Offset(shadowEnd.x + v[0] * 0.55f, shadowEnd.y + v[1] * 0.55f),
        strokeWidth = armW * 1.15f, cap = StrokeCap.Round,
    )

    // 2. 支点底盘
    drawPivotBase(pivot, pivotR, bg, lighting, relief, shadowAlpha)

    // 3. 臂管：满宽暗边 + 70% 亮杆身
    drawLine(lo, pivot, tubeEnd, strokeWidth = armW, cap = StrokeCap.Round)
    drawLine(hi, pivot, tubeEnd, strokeWidth = armW * 0.70f, cap = StrokeCap.Round)

    // 4. 唱头：渐变三面 + 前缘暗线 + 整圈暗轮廓
    val h1 = Offset(headBack.x + nx * headW / 2f, headBack.y + ny * headW / 2f)
    val h2 = Offset(headBack.x - nx * headW / 2f, headBack.y - ny * headW / 2f)
    val t1 = Offset(headFront.x + nx * headW / 2f, headFront.y + ny * headW / 2f)
    val t2 = Offset(headFront.x - nx * headW / 2f, headFront.y - ny * headW / 2f)
    val head = Path().apply {
        moveTo(h1.x, h1.y); lineTo(t1.x, t1.y); lineTo(t2.x, t2.y); lineTo(h2.x, h2.y); close()
    }
    drawPath(head, color = lo)
    drawPath(
        path = head,
        brush = Brush.linearGradient(
            0f to lerp(hi, lo, 0.42f),
            1f to lo,
            start = h1,
            end = Offset(h1.x - nx * headW, h1.y - ny * headW),
        ),
    )
    val litW = headW * 0.30f
    drawPath(
        path = Path().apply {
            val b1 = Offset(h1.x - nx * litW, h1.y - ny * litW)
            val b2 = Offset(t1.x - nx * litW, t1.y - ny * litW)
            moveTo(h1.x, h1.y); lineTo(t1.x, t1.y); lineTo(b2.x, b2.y); lineTo(b1.x, b1.y); close()
        },
        color = lerp(hi, lo, 0.18f).copy(alpha = 0.85f),
    )
    drawLine(lo, t1, t2, strokeWidth = 1.2.dp.toPx())
    drawPath(head, color = lo.copy(alpha = 0.70f), style = Stroke(width = 1.2.dp.toPx()))

    // 5. 唱针：唱头**之外**的小楔形 + 2 dp 红尖
    val stW = headW * 0.42f
    val w1 = Offset(headBack.x + nx * stW / 2f, headBack.y + ny * stW / 2f)
    val w2 = Offset(headBack.x - nx * stW / 2f, headBack.y - ny * stW / 2f)
    val stylus = Path().apply {
        moveTo(w1.x, w1.y); lineTo(tip.x, tip.y); lineTo(w2.x, w2.y); close()
    }
    drawPath(stylus, color = lerp(lo, Color.Black, 0.45f))
    drawPath(stylus, color = hi.copy(alpha = 0.22f), style = Stroke(width = 1f))
    drawCircle(Color(0xFFE2564B), 2.dp.toPx(), tip)
}

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
    val lighting = DayLightHost.current(colors.isDark)
    val d = discD
    val holeD = d * VinylGeo.HOLE_RATIO
    val coverD = d * VinylGeo.COVER_RATIO

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

        // ── 静止层 2：沟槽（旋转对称，永远不跟着转）──
        Box(
            Modifier
                .graphicsLayer { translationX = offX.toPx(); translationY = offY.toPx() }
                .size(d),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGrooves(vinylFillColor(colors.background, colors.isDark), lighting, d.toPx())
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

        // ── 静止层 4：唱臂（armCue / armLower / armLift 期间才变）──
        Canvas(Modifier.fillMaxSize()) {
            drawTonearm(
                bg = colors.background,
                lighting = lighting,
                relief = relief,
                shadowAlpha = shadowAlphaState.floatValue,
                discPx = d.toPx(),
                armAngle = state.armAngle,
                stylusDown = state.stylusDown,
            )
        }
    }
}
