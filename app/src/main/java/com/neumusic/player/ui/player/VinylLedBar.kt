package com.neumusic.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import kotlin.math.sqrt

/**
 * 黑胶模式的 M3 Expressive 胶囊：用角色色与圆头形状表达真实音频电平。
 * 不画槽、描边或发光。保留 State 到绘制阶段才读取，音频帧只触发重绘。
 */
@Composable
fun VinylLedBar(span: Dp, modifier: Modifier = Modifier) {
    val usingFft = VizHost.usingFft.collectAsState()
    val fftLevels = VizHost.levels.collectAsState()
    val pcmLevels = PlayerHost.vizProcessor.levels.collectAsState()
    val playing = PlayerHost.isPlaying.collectAsState()
    val palette = MaterialTheme.colorScheme
    val off = palette.surfaceContainerHighest
    val lit = palette.primary
    val secondary = palette.secondary

    // 原排宽 span、原间隙 3dp；总长度、胶囊宽度与间隙统一乘 2/3，保持 16 根不变。
    // 调用方在居中 Column 中放置，因此缩短后仍与唱片水平居中。
    val widthScale = 2f / 3f
    Canvas(modifier.size(width = span * widthScale, height = 48.dp)) {
        val frame = if (usingFft.value) fftLevels.value else pcmLevels.value
        val isPlaying = playing.value
        val gap = 3.dp.toPx() * widthScale
        val width = (size.width - gap * (VizHost.BARS - 1)) / VizHost.BARS
        val maxHeight = size.height - 4.dp.toPx()
        // 待机也保留圆头短点；增长、变色只来自实际测量值，没有循环假波形。
        val minHeight = width.coerceAtMost(maxHeight)
        for (i in 0 until VizHost.BARS) {
            // 线性 PCM 平均振幅通常很低，平方根压缩让弱音变化可辨，静音仍严格为零。
            val level = if (isPlaying) sqrt(frame.getOrElse(i) { 0f }.coerceIn(0f, 1f)) else 0f
            val height = minHeight + (maxHeight - minHeight) * level
            val activeColor = lerp(secondary, lit, i.toFloat() / (VizHost.BARS - 1))
            drawRoundRect(
                color = if (level > 0.01f) activeColor else off,
                topLeft = Offset(i * (width + gap), (size.height - height) / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2f, width / 2f),
            )
        }
    }
}
