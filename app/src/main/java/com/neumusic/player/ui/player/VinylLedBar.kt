package com.neumusic.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.ShadeColors

/**
 * 黑胶模式的 **LED 胶囊可视化**（方案 §5）。
 *
 * 一排 16 颗等宽小胶囊（[VizHost.BARS] = 16，1:1 无需降采样），横向排开：
 *  - **槽**：新拟物凹陷（[shadeInset]，与播放页其它控件同一套凹陷）；
 *  - **胶囊**：M3 角色色 —— 亮 = [MaterialTheme.colorScheme.primary]、灭 = surfaceVariant 系。
 *    「靠色块区分、不描边」，与本页唱臂三件套同一套 M3 语汇。
 *
 * 长度 = **唱片直径**（[span]），水平居中由调用方的 Column 对齐负责；
 * 位置在唱片与歌名之间（方案 §5.1）。
 *
 * ⚠️ **订阅必须在组件内部**：[VizHost.levels] 每个音频块都发射（~20–50ms），订阅放在
 * 页面顶层会把整个播放页拖进每帧重组（与 `CircularCoverViz` / `CoverDisc` 同一条性能纪律）。
 * 这里 `collectAsState()` 只在本组件的作用域里发生，电平变化只让这一块 Canvas 重绘。
 *
 * @param span 可见胶囊行的总长度（= 唱片直径）。凹槽另在两侧各让 [PAD_X] 的槽壁。
 */
@Composable
fun VinylLedBar(span: Dp, modifier: Modifier = Modifier) {
    val colors = LocalShadeColors.current
    // 订阅在组件内部（见上）：电平流 ~20–50ms 一帧
    val levels by VizHost.levels.collectAsState()
    val lit = MaterialTheme.colorScheme.primary
    val hot = colors.ledOnHot
    val off = colors.ledOff
    val h = 34.dp

    Box(
        modifier
            .size(width = span + PAD_X * 2, height = h)
            .shadeInset(cornerRadius = h / 2),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = VizHost.BARS
            val padX = PAD_X.toPx()
            val padY = 5.dp.toPx()
            val gap = 2.dp.toPx()
            val cw = (size.width - padX * 2 - gap * (n - 1)) / n
            val ch = size.height - padY * 2
            val r = minOf(cw, ch) * 0.42f

            // ① 熄灭底：一次画满 16 格（同色），读起来是一排"待命"的灯
            for (i in 0 until n) {
                val x = padX + i * (cw + gap)
                drawRoundRect(
                    color = off, topLeft = Offset(x, padY),
                    size = Size(cw, ch), cornerRadius = CornerRadius(r, r),
                )
            }
            // ② 点亮：按电平从槽底向上长，先收集成一条 Path（1 次模糊 + 1 次实色 + 1 次高光）
            val litPath = Path()
            val hotPath = Path()
            var any = false
            for (i in 0 until n) {
                val lv = levels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                if (lv <= 0.02f) continue
                any = true
                val x = padX + i * (cw + gap)
                // 圆角跟随自身高度：矮条带用整格半径会被啃成细线（实验室实测）
                val litH = (ch * lv).coerceAtLeast(ch * 0.06f)
                val lr = minOf(r, litH / 2f, cw / 2f)
                val top = padY + ch - litH
                litPath.addRoundRect(RoundRect(x, top, x + cw, padY + ch, CornerRadius(lr, lr)))
                val hotH = (litH * 0.42f).coerceAtLeast(1.5f)
                hotPath.addRoundRect(
                    RoundRect(x, top, x + cw, top + hotH, CornerRadius(minOf(lr, hotH / 2f), minOf(lr, hotH / 2f))),
                )
            }
            if (any) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(
                        litPath.asAndroidPath(),
                        android.graphics.Paint().apply {
                            isAntiAlias = true
                            this.color = android.graphics.Color.argb(
                                (255 * 0.45f).toInt(), (lit.red * 255).toInt(), (lit.green * 255).toInt(), (lit.blue * 255).toInt(),
                            )
                            maskFilter = android.graphics.BlurMaskFilter(5.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        },
                    )
                }
                drawPath(litPath, lit)
                drawPath(hotPath, hot.copy(alpha = 0.90f))
            }
        }
    }
}

/** 凹陷槽在两侧各让出的槽壁：可见胶囊行 = [VinylLedBar] 的 span（= 唱片直径）。 */
private val PAD_X = 3.dp
