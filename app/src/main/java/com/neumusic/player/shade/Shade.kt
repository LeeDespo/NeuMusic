package com.neumusic.player.shade

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.AccentMode
import com.neumusic.player.data.ThemeMode
import kotlin.math.abs

// ───────────────────────── 色板（源自 ShadeCraft ConstantColor） ─────────────────────────

data class ShadeColors(
    val background: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val isDark: Boolean,
) {
    // ── M3 角色位（v2 唱臂三件套 / LED 胶囊的色板，m3Roles 表）──
    // 全部由 background / accent **推导**（浅色往黑压、深色往白提），于是：
    //  - 跟随深浅色主题（两套都校过）；
    //  - 莫奈取色只换 accent → 这些角色跟着走，底色与两影不动（新拟物的明度关系不被破坏）。
    // 数值与实验室 ~/Documents/VinylLab 的 VinylSurface.kt 同一套（复审逐像素核过的那组）：
    //   surfaceContainerHighest (215,218,221) / secondaryContainer (114,110,156) /
    //   secondary (63,53,134) / headPanel (81,69,173) / headHi (125,116,194) / ledOff (189,192,194)。

    private val neutral: Color get() = if (isDark) Color.White else Color.Black

    /** 派生用：a→b 按 t 混合（clamp 0..1）。 */
    private fun mix(a: Color, b: Color, t: Float): Color = Color(
        (a.red + (b.red - a.red) * t).coerceIn(0f, 1f),
        (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
        (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f),
        a.alpha,
    )

    /** m3Roles[0]＝唱臂底座（同心圆）：中性容器，比底色暗/亮一档。 */
    val surfaceContainerHighest: Color get() = mix(background, neutral, if (isDark) 0.08f else 0.09f)

    /** m3Roles[4] 系＝LED 胶囊「灭」：比凹槽底再暗一档（复审：21 级看不见，现 44 级）。 */
    val surfaceVariant: Color get() = mix(background, neutral, if (isDark) 0.13f else 0.14f)

    /** m3Roles[1]＝唱臂胶囊：弱化的强调色容器。 */
    val secondaryContainer: Color
        get() = mix(mix(accent, background, 0.50f), neutral, if (isDark) 0.30f else 0.34f)

    /** m3Roles[2]＝唱头倒角矩形：强调色压暗/压亮，读得出「另一个部件」。 */
    val secondary: Color get() = mix(accent, neutral, if (isDark) 0.18f else 0.42f)

    /** 唱头内嵌面板（secondary 亮一档）。 */
    val headPanel: Color get() = mix(secondary, accent, 0.40f)

    /** 唱头受光侧高光（面板再亮一档，随光照强度走）。 */
    val headHi: Color get() = mix(headPanel, Color.White, if (isDark) 0.18f else 0.34f)

    /** 针尖标记点（primary 系；与唱头本体同色就读不出是第二块料）。 */
    val spindleDot: Color get() = mix(accent, Color.White, if (isDark) 0.05f else 0.25f)

    /** m3Roles[3]＝LED 胶囊「亮」。 */
    val ledOn: Color get() = accent

    /** LED 胶囊点亮条的顶端高光（亮一档，读出"灯芯"）。 */
    val ledOnHot: Color get() = mix(accent, Color.White, if (isDark) 0.30f else 0.42f)

    /** LED 胶囊「灭」（surfaceVariant 再压一档）。 */
    val ledOff: Color get() = mix(background, neutral, if (isDark) 0.14f else 0.20f)

    companion object {
        val Light = ShadeColors(
            background = Color(0xFFECF0F3),
            shadowLight = Color(0xFFFFFFFF),
            shadowDark = Color(0xFFD9D9D9),
            textPrimary = Color(0xFF2D3436),
            textSecondary = Color(0xFF636E72),
            textTertiary = Color(0xFFB2BEC3),
            accent = Color(0xFF6C5CE7),
            isDark = false,
        )
        val Dark = ShadeColors(
            background = Color(0xFF303234),
            shadowLight = Color(0xFF2C2C2C),
            shadowDark = Color(0xFF111111),
            textPrimary = Color(0xFFF0F0F3),
            textSecondary = Color(0xFFA0A3B1),
            textTertiary = Color(0xFF5A5D6E),
            accent = Color(0xFFA29BFE),
            isDark = true,
        )
    }
}

val LocalShadeColors = staticCompositionLocalOf { ShadeColors.Light }

/**
 * 场景阴影的整体透明度（0..1），正常恒为 1。
 *
 * 一二级页面的推拉转场会把整个场景放大重绘——卡片阴影的高斯模糊每帧按放大倍率
 * 重新执行，在模拟器上直接把帧时间拖到上百毫秒（实测）。转场期间由 AppRoot 把它
 * 逐帧压到 0（阴影随推拉淡出/淡回），各 shade 修饰符在 drawBehind 里逐帧读取：
 * 只触发重绘、不触发重组。**必须捕获 State 后在 draw lambda 里读 .floatValue**。
 */
val LocalShadeShadowAlpha = compositionLocalOf { mutableFloatStateOf(1f) }

/**
 * 当前立体感强度。所有阴影绘制（凸起/凹陷）的位移与模糊都乘以这个系数，
 * 让「立体感设置」能一处生效、全局同步。1.0 = 标准。
 */
val LocalReliefScale = staticCompositionLocalOf { 1f }

@Composable
fun ShadeTheme(content: @Composable () -> Unit) {
    // 订阅设置，改了能即时重绘（早前写死 isSystemInDarkTheme()，所以切不了）。
    val mode by Prefs.themeFlow.collectAsState()
    val relief by Prefs.reliefFlow.collectAsState()
    val accentMode by Prefs.accentFlow.collectAsState()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var colors = if (dark) ShadeColors.Dark else ShadeColors.Light

    // ── 莫奈取色 × 新拟物 ──
    // 只换 accent，不动底色与阴影：新拟物的立体感完全依赖
    // 「底色 vs 亮影/暗影」的明度关系，背景一旦被壁纸色带偏，
    // 阴影强度就要整套重调；而 accent 是纯叠加层，换色零风险。
    // 这样得到的是「中性浮雕表面 + 壁纸色点缀」——莫奈与新拟物的安全结合点。
    if (accentMode == AccentMode.MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        colors = colors.copy(accent = scheme.primary)
    }
    // ⚠️ v2 唱臂三件套 / LED 胶囊从 colorScheme 角色取色（m3Roles 表）。
    //    这里只显式设了 5 个角色的话，surfaceContainerHighest / secondaryContainer /
    //    secondary / surfaceVariant 等会落到 M3 基线紫 —— 必须按 ShadeColors + accent 补齐
    //    （浅色往黑压、深色往白提，两套都校过；accent 走莫奈时它们自动跟随）。
    val scheme = if (colors.isDark) darkColorScheme(
        background = colors.background, surface = colors.background,
        onBackground = colors.textPrimary, onSurface = colors.textPrimary, primary = colors.accent,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        surfaceVariant = colors.surfaceVariant,
        secondaryContainer = colors.secondaryContainer,
        secondary = colors.secondary,
    ) else lightColorScheme(
        background = colors.background, surface = colors.background,
        onBackground = colors.textPrimary, onSurface = colors.textPrimary, primary = colors.accent,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        surfaceVariant = colors.surfaceVariant,
        secondaryContainer = colors.secondaryContainer,
        secondary = colors.secondary,
    )
    CompositionLocalProvider(
        LocalShadeColors provides colors,
        LocalReliefScale provides relief.scale,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ───────────────────── 绘制核心：外阴影 → 背景 → 内阴影（单次自绘，顺序可控） ─────────────────────

/** 用原生 android.graphics.Paint 承载 BlurMaskFilter（Compose Paint 的模糊能力受限）。 */
private fun nativePaint(color: Color, alpha: Float, blurPx: Float): android.graphics.Paint =
    android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = if (alpha >= 1f) color.toArgb() else color.copy(alpha = color.alpha * alpha).toArgb()
        if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }

private fun DrawScope.roundedPath(cornerPx: Float): Path = Path().apply {
    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerPx, cornerPx)))
}

private fun drawOffsetPath(
    canvas: Canvas, path: Path, dx: Float, dy: Float, paint: android.graphics.Paint,
) {
    val nc = canvas.nativeCanvas
    val androidPath = path.asAndroidPath()
    nc.save()
    nc.translate(dx, dy)
    nc.drawPath(androidPath, paint)
    nc.restore()
}

/** 两影的位移向量（px）。组件自己的 offset 相对基准 [REF_OFFSET_DP] 等比取用光照长度。 */
private class ShadowVectors(val dx: Float, val dy: Float, val lx: Float, val ly: Float)

/**
 * 由光照 + 组件自身 offset 算出两影位移。暗色阴影与高光阴影**方向相反、长度各自独立**
 * （两条长度来自同一条变化曲线，只是取值区间不同），长度都按组件 offset / 基准 6dp 等比缩放。
 */
private fun DrawScope.shadowVectors(light: Lighting, offsetPx: Float): ShadowVectors {
    val w = offsetPx / (REF_OFFSET_DP * density)
    return ShadowVectors(
        dx = light.ux * light.lenDark * w,
        dy = light.uy * light.lenDark * w,
        lx = -light.ux * light.lenLight * w,
        ly = -light.uy * light.lenLight * w,
    )
}

/**
 * 统一阴影绘制。绘制顺序：凸起外阴影 → 背景圆角矩形 → 凹陷内阴影。
 *
 * 光源方向/长度/强度/色温都由当前 [Lighting] 决定（见 [DayLightHost]）。
 * `offsetPx` / `blurPx` 是**组件自己的标称尺寸**（各调用点传的 dp），
 * 光照只做按轴缩放，于是小控件与大卡片之间的比例关系不变。
 *
 * @param outerAlpha 凸起外阴影强度（1=全凸）
 * @param innerAlpha 凹陷内阴影强度（1=全凹）
 * @param innerStrength 内阴影加深系数（>1 表示「按进已有的凹陷」）
 * @param bgColor 背景色（调用方已完成按压变暗插值）
 */
private fun DrawScope.drawShade(
    cornerPx: Float,
    bgColor: Color,
    outerAlpha: Float,
    innerAlpha: Float,
    innerStrength: Float,
    colors: ShadeColors,
    light: Lighting,
    offsetPx: Float,
    blurPx: Float,
) {
    val path = roundedPath(cornerPx)
    val v = shadowVectors(light, offsetPx)
    val blurD = blurPx * light.blurDark
    val blurL = blurPx * light.blurLight
    val cLight = tempTint(colors.shadowLight, light.warmth)
    val cDark = tempTint(colors.shadowDark, light.warmth)

    if (outerAlpha > 0.01f) {
        drawIntoCanvas { canvas ->
            drawOffsetPath(canvas, path, v.lx, v.ly, nativePaint(cLight, outerAlpha * light.alphaLight, blurL))
            drawOffsetPath(canvas, path, v.dx, v.dy, nativePaint(cDark, outerAlpha * light.alphaDark, blurD))
        }
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), nativePaint(bgColor, 1f, 0f))
    }
    if (innerAlpha > 0.01f) {
        drawIntoCanvas { canvas ->
            val complement = Path().apply {
                fillType = PathFillType.EvenOdd
                addRoundRect(RoundRect(-size.width, -size.height, size.width * 2f, size.height * 2f, CornerRadius.Zero))
                addPath(path)
            }
            clipPath(path) {
                drawIntoCanvas { c ->
                    val d = 0.9f
                    drawOffsetPath(
                        c, complement, v.dx * d, v.dy * d,
                        nativePaint(cDark, innerAlpha * innerStrength * light.alphaDark, blurD * innerStrength),
                    )
                    drawOffsetPath(
                        c, complement, v.lx * d, v.ly * d,
                        nativePaint(cLight, innerAlpha * 0.9f * light.alphaLight, blurL * innerStrength),
                    )
                }
            }
        }
    }
}

/** 静态凸起面（封面、卡片、顶栏等非按压表面）。 */
@Composable
fun Modifier.shadeSurface(cornerRadius: Dp = 20.dp, offset: Dp = 6.dp, blur: Dp = 10.dp): Modifier {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val shadowAlpha = LocalShadeShadowAlpha.current
    return this.drawBehind {
        drawShade(cornerRadius.toPx(), colors.background,
            outerAlpha = shadowAlpha.floatValue.coerceIn(0f, 1f), innerAlpha = 0f, innerStrength = 1f,
            colors = colors, light = DayLightHost.current(colors.isDark),
            offsetPx = offset.toPx() * relief, blurPx = blur.toPx() * relief)
    }
}

/** 静态凹陷面（输入框、轨道、选中的 tab 胶囊）。 */
@Composable
fun Modifier.shadeInset(cornerRadius: Dp = 16.dp, offset: Dp = 4.dp, blur: Dp = 6.dp): Modifier {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val shadowAlpha = LocalShadeShadowAlpha.current
    return this.drawBehind {
        drawShade(cornerRadius.toPx(), colors.background,
            outerAlpha = 0f, innerAlpha = shadowAlpha.floatValue.coerceIn(0f, 1f), innerStrength = 1f,
            colors = colors, light = DayLightHost.current(colors.isDark),
            offsetPx = offset.toPx() * relief, blurPx = blur.toPx() * relief)
    }
}

/**
 * 底部停靠的凸起面板（播放栏、选择模式底栏）：阴影四周都画（底边在屏幕外，画了也看不见），
 * 表面填充**只有顶角是圆的**——底边贴着屏幕下缘，不能出现圆角缝隙。
 */
@Composable
fun Modifier.shadeSurfaceTop(cornerRadius: Dp = 22.dp, offset: Dp = 6.dp, blur: Dp = 10.dp): Modifier {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val shadowAlpha = LocalShadeShadowAlpha.current
    return this.drawBehind {
        val off = offset.toPx() * relief
        val blurPx = blur.toPx() * relief
        val a = shadowAlpha.floatValue.coerceIn(0f, 1f)
        val light = DayLightHost.current(colors.isDark)
        val v = shadowVectors(light, off)
        val blurD = blurPx * light.blurDark
        val blurL = blurPx * light.blurLight
        val cLight = tempTint(colors.shadowLight, light.warmth)
        val cDark = tempTint(colors.shadowDark, light.warmth)
        if (a > 0.01f) {
            val shadow = roundedPath(cornerRadius.toPx())
            drawIntoCanvas { canvas ->
                drawOffsetPath(canvas, shadow, v.lx, v.ly, nativePaint(cLight, a * light.alphaLight, blurL))
                drawOffsetPath(canvas, shadow, v.dx, v.dy, nativePaint(cDark, a * light.alphaDark, blurD))
            }
        }
        val r = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        val z = CornerRadius.Zero
        val fill = Path().apply {
            addRoundRect(
                RoundRect(
                    0f, 0f, size.width, size.height,
                    topLeftCornerRadius = r, topRightCornerRadius = r,
                    bottomRightCornerRadius = z, bottomLeftCornerRadius = z,
                )
            )
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(fill.asAndroidPath(), nativePaint(colors.background, 1f, 0f))
        }
    }
}

/** 「整块凸面」切片的行位：决定圆角与投影出现在哪一端。 */
enum class BlockSlice { Head, Middle, Tail, Single }

/**
 * 「整块凸面」的**逐行切片**：虚拟化列表里每行画自己那一片。
 *
 * 曲目列表几百行时不能把整块画进一个 item（组合/排版/光栅随行数线性膨胀）。
 * 每行的阴影用**纵向外延的整块轮廓**做模糊，再按行位垂直裁剪：
 * Middle 行的上下边缘落在模糊的内部（外延部分被裁掉/被相邻行盖住），整块浑然一体；
 * Head/Tail 露出真实的圆角，块尾的投影向下露出。左右投影随行裁剪窗口自然连续。
 * 阴影透明度逐帧读 [LocalShadeShadowAlpha]（转场期淡出，只重绘不重组）。
 */
@Composable
fun Modifier.blockSlice(
    position: BlockSlice,
    cornerRadius: Dp = 22.dp,
    offset: Dp = 6.dp,
    blur: Dp = 10.dp,
): Modifier {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val shadowAlpha = LocalShadeShadowAlpha.current
    return this.drawBehind {
        val w = size.width
        val h = size.height
        val off = offset.toPx() * relief
        val blurPx = blur.toPx() * relief
        val light = DayLightHost.current(colors.isDark)
        val v = shadowVectors(light, off)
        val blurD = blurPx * light.blurDark
        val blurL = blurPx * light.blurLight
        // 纵向外延：裁剪窗口内不能出现局部模糊边（取两侧模糊的较大者）
        val ext = maxOf(blurD, blurL) * 2.5f + maxOf(abs(v.dx), abs(v.dy), abs(v.lx), abs(v.ly)) + 2.dp.toPx()
        val corner = cornerRadius.toPx()
        val isHead = position == BlockSlice.Head || position == BlockSlice.Single
        val isTail = position == BlockSlice.Tail || position == BlockSlice.Single
        clipRect(left = -ext, top = if (isHead) -ext else 0f, right = w + ext, bottom = if (isTail) h + ext else h) {
            if (shadowAlpha.floatValue > 0.02f) {
                val a = shadowAlpha.floatValue.coerceIn(0f, 1f)
                // 双影的切片矩形**方向不对称**：
                // 暗影 offset 向下 → 它的底边就是块尾的真实投影，纵向向下外延；
                // 亮影 offset 向上 → 它的顶边是块首的真实受光，纵向向上外延。
                // 反向外延会让对侧漏出一大坨模糊（实测"一大坨光影糊在顶部/底部"）。
                // 切片的可见端带与列表同款的圆角（块首顶角/块尾底角），投影才不显得生硬
                val r = CornerRadius(corner, corner)
                val darkSlice = Path().apply {
                    addRoundRect(
                        when {
                            isHead && isTail -> RoundRect(0f, 0f, w, h, topLeftCornerRadius = r, topRightCornerRadius = r, bottomRightCornerRadius = r, bottomLeftCornerRadius = r)
                            isHead -> RoundRect(0f, 0f, w, h + ext, topLeftCornerRadius = r, topRightCornerRadius = r)
                            isTail -> RoundRect(0f, -ext, w, h, bottomLeftCornerRadius = r, bottomRightCornerRadius = r)
                            else -> RoundRect(0f, -ext, w, h + ext)
                        }
                    )
                }
                // 受光切片的几何**不能照抄暗影**（用户实测"断裂的是高光阴影"）：
                // 圆角只能出现在**块的真实端点**上。中间行若也带圆角（在 h 处收口 22dp 圆弧），
                // 圆弧会落进行内，每行都留下一条高光断痕——而暗影切片的中间行是无圆角矩形，
                // 所以只有高光看得出断裂。中间行改成两端外延的纯矩形，端点才用圆角。
                val lightSlice = Path().apply {
                    addRoundRect(
                        when {
                            isHead && isTail -> RoundRect(0f, 0f, w, h, topLeftCornerRadius = r, topRightCornerRadius = r, bottomRightCornerRadius = r, bottomLeftCornerRadius = r)
                            isHead -> RoundRect(0f, 0f, w, h + ext, topLeftCornerRadius = r, topRightCornerRadius = r)
                            isTail -> RoundRect(0f, -ext, w, h, bottomLeftCornerRadius = r, bottomRightCornerRadius = r)
                            else -> RoundRect(0f, -ext, w, h + ext)
                        }
                    )
                }
                drawIntoCanvas { canvas ->
                    drawOffsetPath(canvas, lightSlice, v.lx, v.ly,
                        nativePaint(tempTint(colors.shadowLight, light.warmth), a * light.alphaLight, blurL))
                    drawOffsetPath(canvas, darkSlice, v.dx, v.dy,
                        nativePaint(tempTint(colors.shadowDark, light.warmth), a * light.alphaDark, blurD))
                }
            }
            val r = CornerRadius(corner, corner)
            val z = CornerRadius.Zero
            // 相邻行的填充**纵向各让 1px 重叠**：两块抗锯齿的矩形严丝合缝相接时，
            // 交界那一列像素的覆盖率之和 < 1，会漏出底下的阴影色——就是用户报的
            // 「光影在每一行间发生断裂」（整块渲染的搜索页没有这个问题，正是因为它没有行缝）。
            // 重叠的是同色填充，看不出来；圆角端不动（Head 只延下边、Tail 只延上边）。
            val ov = 1.dp.toPx()
            val fill = Path().apply {
                addRoundRect(
                    when (position) {
                        BlockSlice.Head -> RoundRect(0f, 0f, w, h + ov, topLeftCornerRadius = r, topRightCornerRadius = r)
                        BlockSlice.Tail -> RoundRect(0f, -ov, w, h, bottomRightCornerRadius = r, bottomLeftCornerRadius = r)
                        BlockSlice.Single -> RoundRect(0f, 0f, w, h, r)
                        BlockSlice.Middle -> RoundRect(0f, -ov, w, h + ov)
                    }
                )
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(fill.asAndroidPath(), nativePaint(colors.background, 1f, 0f))
            }
        }
    }
}

/**
 * 全 App 统一的按压交互（设计规范）：
 * 1. 凸→凹阴影渐变形变：150ms 内外阴影透明度交叉淡入淡出，绝无瞬间跳变；
 * 2. 已是凹陷（selected 常驻凹）时再按压 → 内阴影加深（强度 +45%）；
 * 3. 按下时背景向黑混入 ~6% 模拟受压，同样带动画。
 */
@Composable
fun Modifier.shadePressable(
    cornerRadius: Dp = 20.dp,
    selected: Boolean = false,
    offset: Dp = 7.dp,
    blur: Dp = 10.dp,
    onClick: () -> Unit,
): Modifier {
    val colors = LocalShadeColors.current
    val relief = LocalReliefScale.current
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val sink by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "sink")
    val morph by animateFloatAsState(if (pressed && !selected) 1f else 0f, tween(160), label = "morph")
    val deep by animateFloatAsState(if (pressed && selected) 1f else 0f, tween(160), label = "deep")
    val bg by animateColorAsState(
        when {
            pressed -> lerp(colors.background, Color.Black, 0.06f)
            selected -> lerp(colors.background, Color.Black, 0.02f)
            else -> colors.background
        },
        tween(160), label = "bg",
    )

    val outerAlpha = (1f - sink) * (1f - morph)
    val innerAlpha = maxOf(sink, morph)
    val shadowAlpha = LocalShadeShadowAlpha.current

    return this
        .drawBehind {
            val a = shadowAlpha.floatValue.coerceIn(0f, 1f)
            drawShade(
                cornerPx = cornerRadius.toPx(),
                bgColor = bg,
                outerAlpha = outerAlpha * a,
                innerAlpha = innerAlpha * a,
                innerStrength = 1f + 0.45f * deep,
                colors = colors,
                light = DayLightHost.current(colors.isDark),
                offsetPx = offset.toPx() * relief,
                blurPx = blur.toPx() * relief,
            )
        }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
        }
}

/**
 * 「平」面按压：**不带任何阴影**，只用背景微暗 + 触感表达按下。
 *
 * 用途：位于凸起容器（如整块凸起的列表）里的按钮。这类按钮按 UI 原则**不能**再凸起
 * （「不在凸起上做凸起」），所以用这个而不是 [shadePressable]。
 * 需要表达"选中"时，在外层再叠 [shadeInset]（凸起中的凹陷=选中）。
 */
@Composable
fun Modifier.flatPressable(
    cornerRadius: Dp = 16.dp,
    onClick: () -> Unit,
): Modifier {
    val colors = LocalShadeColors.current
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) lerp(colors.background, Color.Black, 0.06f) else Color.Transparent,
        tween(140), label = "flatBg",
    )
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
        }
}
