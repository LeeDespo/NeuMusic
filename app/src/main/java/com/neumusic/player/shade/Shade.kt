package com.neumusic.player.shade

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.AccentMode
import com.neumusic.player.data.ThemeMode

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
    val scheme = if (colors.isDark) darkColorScheme(
        background = colors.background, surface = colors.background,
        onBackground = colors.textPrimary, onSurface = colors.textPrimary, primary = colors.accent,
    ) else lightColorScheme(
        background = colors.background, surface = colors.background,
        onBackground = colors.textPrimary, onSurface = colors.textPrimary, primary = colors.accent,
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

/**
 * 统一阴影绘制。绘制顺序：凸起外阴影 → 背景圆角矩形 → 凹陷内阴影。
 * 光源固定左上：凸起时亮影偏左上/暗影偏右下；凹陷时暗内影在左上内缘、亮内影在右下内缘。
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
    offsetPx: Float,
    blurPx: Float,
) {
    val path = roundedPath(cornerPx)
    if (outerAlpha > 0.01f) {
        drawIntoCanvas { canvas ->
            drawOffsetPath(canvas, path, -offsetPx, -offsetPx, nativePaint(colors.shadowLight, outerAlpha, blurPx))
            drawOffsetPath(canvas, path, offsetPx, offsetPx, nativePaint(colors.shadowDark, outerAlpha, blurPx))
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
                    val blur = blurPx * innerStrength
                    val d = offsetPx * 0.9f
                    drawOffsetPath(c, complement, d, d, nativePaint(colors.shadowDark, innerAlpha * innerStrength, blur))
                    drawOffsetPath(c, complement, -d, -d, nativePaint(colors.shadowLight, innerAlpha * 0.9f, blur))
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
            colors = colors, offsetPx = offset.toPx() * relief, blurPx = blur.toPx() * relief)
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
            colors = colors, offsetPx = offset.toPx() * relief, blurPx = blur.toPx() * relief)
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
        if (a > 0.01f) {
            val shadow = roundedPath(cornerRadius.toPx())
            drawIntoCanvas { canvas ->
                drawOffsetPath(canvas, shadow, -off, -off, nativePaint(colors.shadowLight, a, blurPx))
                drawOffsetPath(canvas, shadow, off, off, nativePaint(colors.shadowDark, a, blurPx))
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
        val ext = blurPx * 2.5f + off + 2.dp.toPx()   // 纵向外延：裁剪窗口内不能出现局部模糊边
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
                val lightSlice = Path().apply {
                    addRoundRect(
                        when {
                            isHead && isTail -> RoundRect(0f, 0f, w, h, topLeftCornerRadius = r, topRightCornerRadius = r, bottomRightCornerRadius = r, bottomLeftCornerRadius = r)
                            isHead -> RoundRect(0f, 0f, w, h, topLeftCornerRadius = r, topRightCornerRadius = r)
                            else -> RoundRect(0f, -ext, w, h, bottomLeftCornerRadius = r, bottomRightCornerRadius = r)
                        }
                    )
                }
                drawIntoCanvas { canvas ->
                    drawOffsetPath(canvas, lightSlice, -off, -off, nativePaint(colors.shadowLight, a, blurPx))
                    drawOffsetPath(canvas, darkSlice, off, off, nativePaint(colors.shadowDark, a, blurPx))
                }
            }
            val r = CornerRadius(corner, corner)
            val z = CornerRadius.Zero
            val fill = Path().apply {
                addRoundRect(
                    when (position) {
                        BlockSlice.Head -> RoundRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), topLeft = r, topRight = r, bottomRight = z, bottomLeft = z)
                        BlockSlice.Tail -> RoundRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), topLeft = z, topRight = z, bottomRight = r, bottomLeft = r)
                        BlockSlice.Single -> RoundRect(0f, 0f, w, h, r)
                        BlockSlice.Middle -> RoundRect(0f, 0f, w, h, z)
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

// ───────────────────────── 路径式标签栏（选中标签与下方列表融合） ─────────────────────────

/**
 * 融合式标签栏容器：整体是一块凸起面，标签行位于其顶部。
 * 标签行与内容之间画一道浅槽作为分隔，**但选中标签的宽度范围内不画**，
 * 于是选中的标签与下方列表连成一整块（参考设计图 sample_1 / ShadeCraft ShadeTabPathBar）。
 *
 * 用法：把标签标题行与列表内容一起传进 [content]，布局需保证标签行高度 = [tabHeight]。
 */
@Composable
fun ShadeFusedTabs(
    tabCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 52.dp,
    cornerRadius: Dp = 22.dp,
    offset: Dp = 6.dp,
    blur: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        modifier
            .shadeSurface(cornerRadius = cornerRadius, offset = offset, blur = blur)
            .drawWithContent {
                drawContent()
                // 标签行与内容之间的浅槽，在选中标签宽度内断开，使二者连成一块。
                val y = tabHeight.toPx()
                val tabW = size.width / tabCount
                val gapStart = selectedIndex * tabW
                val gapEnd = gapStart + tabW
                val inset = cornerRadius.toPx() * 0.6f
                val groove = nativePaint(colors.shadowDark, 0.5f, 0f)
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    // 左侧段：inset → gapStart
                    if (gapStart > inset) nc.drawLine(inset, y, gapStart, y, groove)
                    // 右侧段：gapEnd → 右内边
                    if (size.width - inset > gapEnd) nc.drawLine(gapEnd, y, size.width - inset, y, groove)
                }
            }
    ) {
        content()
    }
}

/** 单个标签标题。选中时用 accent 色加粗，配合容器缺口形成「长进内容」的效果。 */
@Composable
fun ShadeFusedTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxHeight()
            .pointerInput(label, selected) {
                detectTapGestures(
                    onTap = {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.accent else colors.textTertiary,
            fontSize = if (selected) 15.sp else 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
