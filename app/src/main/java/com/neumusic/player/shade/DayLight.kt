package com.neumusic.player.shade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.neumusic.player.data.LightingMode
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 一份「当前光照」：把每个组件**自己的** offset/blur 按轴缩放，并给出两侧强度与色温。
 *
 * 移植自 ShadeLab 实验（`~/Documents/ShadeLab`，用户逐轮验收过），三件事原样保留：
 * 1. 偏移方向随天体方位角扫过，**日夜共用同一条连续曲线**（所以昼夜交界处偏移天然接上）；
 * 2. **白天/黑夜各一套完整 0–24h 曲线**（色温、强度、模糊、补光各自成篇）；
 * 3. 色温由三个锚点（2000K 白天最暖 / 5500K 白天最冷 / 8000K 黑夜）标定，整条曲线随之重塑。
 *
 * 与实验的差别只在「怎么落到组件上」：实验里所有组件共用一组绝对 dp 值，这里改成
 * **倍数**——组件自己的 `offset`/`blur` 是它的标称尺寸，光照只做按轴缩放，这样各自的比例
 * 关系（小按钮 vs 卡片）不会丢。倍率的基准取本工程最常用的 6dp / 10dp，
 * 于是默认参数下「offset=6dp、blur=10dp 的组件」与实验里的观感一致。
 */
class Lighting(
    /** 暗影偏移倍数（正 = 右/下）。亮影取其相反值。 */
    val sx: Float,
    val sy: Float,
    /** 两侧模糊倍率（乘组件自己的 blur）。 */
    val blurDark: Float,
    val blurLight: Float,
    /** 两侧强度（乘组件自己的 alpha）。 */
    val alphaDark: Float,
    val alphaLight: Float,
    /** 色温 −1(冷)..+1(暖)。**只给两影着色，不动底色**（立体感依赖底色与两影的明度关系）。 */
    val warmth: Float,
    /** 「最大偏移」倍率（相对基准 10dp）。 */
    val k: Float,
)

// ───────────────────────── 偏移：日夜共用的一条连续扫描 ─────────────────────────

/** 偏移关键帧（相对组件自身 offset 的倍数）。 */
private class OffKey(val hour: Float, val sx: Float, val sy: Float)

/**
 * 6:00 最左 → 12:00 归零 → 18:00 最右 → 24:00 归零 → 回到 6:00 最左。
 * 白天与黑夜共用这一条，所以白天黑夜怎么分都不影响它的连续性。
 */
private val OFFSET_KEYS = listOf(
    OffKey(0f, 0f, 0.58f),      // 月在中天：纯顶光
    OffKey(3f, -0.85f, 0.70f),
    OffKey(5f, -1.42f, 0.80f),
    OffKey(6f, -1.70f, 0.83f),  // 日出：最左
    OffKey(7f, -1.13f, 0.75f),
    OffKey(9.5f, -0.57f, 0.67f),
    OffKey(12f, 0f, 0.58f),     // 正午：纯顶光
    OffKey(15f, 0.57f, 0.67f),
    OffKey(17f, 1.13f, 0.75f),
    OffKey(18f, 1.70f, 0.83f),  // 日落：最右
    OffKey(19f, 1.42f, 0.80f),
    OffKey(21f, 0.76f, 0.70f),
    OffKey(24f, 0f, 0.58f),
)

// ───────────────────────── 属性：白天 / 黑夜两套完整 0–24h 曲线 ─────────────────────────

/** 白天属性关键帧（真实色温 + 受光/背光的模糊与强度）。 */
private class DayKey(
    val hour: Float, val kelvin: Float,
    val blurDark: Float, val blurLight: Float,
    val alphaDark: Float, val alphaLight: Float,
)

/**
 * 白天的有效变化区间是 6:00–18:00（日出到日落）。**区间外沿用端点值**，
 * 于是这套曲线在整个 0–24h 上都有定义（浅色模式下任何时刻都取得到白天那套观感）。
 */
private val DAY_KEYS = listOf(
    DayKey(6f, 2000f, 0.30f, 0.25f, 0.95f, 1f),     // 旭日：长而锐、最暖
    DayKey(7f, 3500f, 0.35f, 0.40f, 0.95f, 1f),     // 黄金时刻
    DayKey(9.5f, 4500f, 0.50f, 0.70f, 0.95f, 0.95f),// 上午
    DayKey(12f, 5500f, 0.50f, 1.10f, 1f, 1f),       // 正午：暗影最短、受光最宽最柔
    DayKey(15f, 5000f, 0.50f, 0.70f, 0.95f, 0.95f), // 下午
    DayKey(17f, 3500f, 0.35f, 0.40f, 0.95f, 1f),
    DayKey(18f, 2000f, 0.30f, 0.25f, 0.95f, 1f),    // 夕阳
)

/** 黑夜属性：整夜恒定。月光色温固定、光弱而弥散、几乎没有明确高光点。 */
private const val NIGHT_KELVIN = 8000f
private const val NIGHT_BLUR_DARK = 1.0f
private const val NIGHT_BLUR_LIGHT = 1.2f
private const val NIGHT_ALPHA_DARK = 0.6f
private const val NIGHT_ALPHA_LIGHT = 0.35f

/** 偏移倍率的基准（组件的 offset 等于它时，默认参数下取到实验里的观感）。 */
private const val REF_HOUR_DEFAULT = 12f

/** 「最大偏移」滑杆的基准值与满量程（dp）。 */
const val MAX_OFFSET_BASE = 10f
const val MAX_OFFSET_RANGE = 40f

/** 色温锚点默认值（与实验一致）。 */
const val DEFAULT_DAY_WARM = 0.53f
const val DEFAULT_DAY_COLD = -0.23f
const val DEFAULT_NIGHT_WARM = -0.78f

/**
 * 色温 K → warmth：分段线性，锚点来自设置里的三个值。
 * 2000K→dayWarm、5500K→dayCold、8000K→night；段内线性插值，两段在 5500K 处自然接上。
 */
fun warmthOfK(kelvin: Float, dayWarm: Float, dayCold: Float, night: Float): Float =
    if (kelvin <= 5500f) dayWarm + (dayCold - dayWarm) * ((kelvin - 2000f) / 3500f)
    else dayCold + (night - dayCold) * ((kelvin - 5500f) / 2500f)

/**
 * 色温着色：只动色温不动明度结构——暖 = R↑B↓（白变奶油、灰变暖褐），
 * 冷 = B↑R↓（白变冰蓝、灰变青灰），G 几乎不动。只用于两影色，底色不碰。
 */
fun tempTint(c: Color, warmth: Float): Color {
    if (warmth == 0f) return c
    return Color(
        (c.red + warmth * 0.10f).coerceIn(0f, 1f),
        (c.green + warmth * 0.02f).coerceIn(0f, 1f),
        (c.blue - warmth * 0.12f).coerceIn(0f, 1f),
        c.alpha,
    )
}

// ───────────────────────── 采样 ─────────────────────────

/** 关键帧区间下标（相邻两帧之间做 smoothstep：帧处导数为零 → 每段有自然停留）。 */
private fun segIndex(hours: List<Float>, h: Float): Int {
    var i = 0
    while (i < hours.size - 2 && hours[i + 1] < h) i++
    return i
}

private fun smooth(a: Float, b: Float, h: Float): Float {
    val raw = if (b == a) 1f else (h - a) / (b - a)
    val r = raw.coerceIn(0f, 1f)
    return r * r * (3f - 2f * r)
}

private val OFFSET_HOURS = OFFSET_KEYS.map { it.hour }
private val DAY_HOURS = DAY_KEYS.map { it.hour }

private fun sampleOffset(h: Float): Pair<Float, Float> {
    val i = segIndex(OFFSET_HOURS, h)
    val a = OFFSET_KEYS[i]
    val b = OFFSET_KEYS[i + 1]
    val t = smooth(a.hour, b.hour, h)
    return (a.sx + (b.sx - a.sx) * t) to (a.sy + (b.sy - a.sy) * t)
}

/** 白天属性：6:00 之前与 18:00 之后沿用端点值（曲线在 0–24h 上完整）。 */
private fun sampleDayAttr(h: Float): DayKey {
    if (h <= DAY_HOURS.first()) return DAY_KEYS.first()
    if (h >= DAY_HOURS.last()) return DAY_KEYS.last()
    val i = segIndex(DAY_HOURS, h)
    val a = DAY_KEYS[i]
    val b = DAY_KEYS[i + 1]
    val t = smooth(a.hour, b.hour, h)
    return DayKey(
        hour = h,
        kelvin = a.kelvin + (b.kelvin - a.kelvin) * t,
        blurDark = a.blurDark + (b.blurDark - a.blurDark) * t,
        blurLight = a.blurLight + (b.blurLight - a.blurLight) * t,
        alphaDark = a.alphaDark + (b.alphaDark - a.alphaDark) * t,
        alphaLight = a.alphaLight + (b.alphaLight - a.alphaLight) * t,
    )
}

/**
 * 采样当前光照。
 *
 * 选哪一套由**深色模式**决定（不是由时刻决定）：浅色主题 → 白天那套，
 * 深色主题 → 黑夜那套，「随系统」则由系统深浅色决定——于是深色模式选浅色时
 * 光影变化一直是白天的、选深色时一直是黑夜的。时刻只负责在这套曲线里取位置。
 */
private fun sample(hour: Float, night: Boolean): Lighting {
    val h = hour.coerceIn(0f, 24f)
    val (sx, sy) = sampleOffset(h)
    val k = DayLightHost.maxOffset / MAX_OFFSET_BASE
    return if (night) {
        Lighting(
            sx = sx, sy = sy,
            blurDark = NIGHT_BLUR_DARK, blurLight = NIGHT_BLUR_LIGHT,
            alphaDark = NIGHT_ALPHA_DARK, alphaLight = NIGHT_ALPHA_LIGHT,
            warmth = warmthOfK(NIGHT_KELVIN, DayLightHost.dayWarm, DayLightHost.dayCold, DayLightHost.nightWarm),
            k = k,
        )
    } else {
        val d = sampleDayAttr(h)
        Lighting(
            sx = sx, sy = sy,
            blurDark = d.blurDark, blurLight = d.blurLight,
            alphaDark = d.alphaDark, alphaLight = d.alphaLight,
            warmth = warmthOfK(d.kelvin, DayLightHost.dayWarm, DayLightHost.dayCold, DayLightHost.nightWarm),
            k = k,
        )
    }
}

/**
 * 光照的运行时来源。所有字段都是**快照状态**，且只在 `drawBehind` / `graphicsLayer`
 * 的 lambda 里读取（[current]），所以设置一改就重绘、不会引起整树重组。
 *
 * 写入直接赋值即可（setter 顺带落盘 [Prefs]）；[clockHour] 由 [runClock] 维护，只读。
 */
object DayLightHost {
    private var _mode by mutableStateOf(LightingMode.FIXED)

    /** 光影的时间行为：随时间变化 / 固定光影。 */
    var mode: LightingMode
        get() = _mode
        set(v) {
            _mode = v
            Prefs.lightingMode = v
        }

    private var _fixedHour by mutableFloatStateOf(REF_HOUR_DEFAULT)

    /** 固定光影模式下的时刻（0..24，写入时对齐到 0.5h）。 */
    var fixedHour: Float
        get() = _fixedHour
        set(v) {
            val snapped = ((v * 2f).roundToInt() / 2f).coerceIn(0f, 24f)
            _fixedHour = snapped
            Prefs.lightingFixedHour = snapped
        }

    private var _clockHour by mutableFloatStateOf(REF_HOUR_DEFAULT)

    /** 「随时间变化」模式下的时刻，由 [runClock] 每 30 秒刷新。 */
    val clockHour: Float get() = _clockHour

    private var _dayWarm by mutableFloatStateOf(DEFAULT_DAY_WARM)

    /** 色温标定：白天最暖（2000K 日出/日落）。 */
    var dayWarm: Float
        get() = _dayWarm
        set(v) { _dayWarm = v; Prefs.lightingDayWarm = v }

    private var _dayCold by mutableFloatStateOf(DEFAULT_DAY_COLD)

    /** 色温标定：白天最冷（5500K 正午）。 */
    var dayCold: Float
        get() = _dayCold
        set(v) { _dayCold = v; Prefs.lightingDayCold = v }

    private var _nightWarm by mutableFloatStateOf(DEFAULT_NIGHT_WARM)

    /** 色温标定：黑夜（8000K 月光，整夜恒定）。 */
    var nightWarm: Float
        get() = _nightWarm
        set(v) { _nightWarm = v; Prefs.lightingNightWarm = v }

    private var _maxOffset by mutableFloatStateOf(MAX_OFFSET_BASE)

    /** 光影标定：最大偏移（dp，基准 10）。 */
    var maxOffset: Float
        get() = _maxOffset
        set(v) { _maxOffset = v; Prefs.lightingMaxOffset = v }

    /** 由 MainActivity 在 Prefs.init 之后调用，把磁盘值灌进运行时状态。 */
    fun install() {
        _mode = Prefs.lightingMode
        _fixedHour = Prefs.lightingFixedHour
        _dayWarm = Prefs.lightingDayWarm
        _dayCold = Prefs.lightingDayCold
        _nightWarm = Prefs.lightingNightWarm
        _maxOffset = Prefs.lightingMaxOffset
        setClockNow()
    }

    fun resetCalib() {
        dayWarm = DEFAULT_DAY_WARM
        dayCold = DEFAULT_DAY_COLD
        nightWarm = DEFAULT_NIGHT_WARM
        maxOffset = MAX_OFFSET_BASE
    }

    /** 「随时间变化」用的时钟：每 30 秒对一次（时刻轴只有 0.5h 粒度，够用）。 */
    suspend fun runClock() {
        while (true) {
            setClockNow()
            delay(30_000)
        }
    }

    private fun setClockNow() {
        val t = LocalTime.now()
        _clockHour = t.hour + t.minute / 60f
    }

    /** 当前**显示中**的时刻。 */
    fun displayHour(): Float = if (_mode == LightingMode.TIME) _clockHour else _fixedHour

    /** draw 期读取当前光照（[night] 通常传 `colors.isDark`）。 */
    fun current(night: Boolean): Lighting = sample(displayHour(), night)

    /** 组合期读取读数（设置页显示用；与 [current] 同一套曲线）。 */
    fun preview(night: Boolean): Lighting = sample(displayHour(), night)
}

/** 时刻 → 时段名（设置页读数用）。 */
fun periodName(hour: Float): String = when (val h = ((hour % 24f) + 24f) % 24f) {
    in 6f..<6.5f -> "旭日"
    in 6.5f..<8f -> "黄金时刻"
    in 8f..<11f -> "上午"
    in 11f..<13.5f -> "正午"
    in 13.5f..<16f -> "下午"
    in 16f..<17.5f -> "黄金时刻"
    in 17.5f..<19f -> "夕阳"
    else -> "月光"
}

/** 时刻 → "HH:MM"。 */
fun formatHour(hour: Float): String {
    val totalMin = (hour * 60f).roundToInt().mod(24 * 60)
    return "%02d:%02d".format(totalMin / 60, totalMin % 60)
}
