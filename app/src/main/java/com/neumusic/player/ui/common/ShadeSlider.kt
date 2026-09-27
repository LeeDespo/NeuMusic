package com.neumusic.player.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset

/**
 * 横向新拟物滑杆：凹陷轨道 + accent 填充（"凹陷中不再做凹陷"，填充是纯色块）。
 * 音效页/均衡器页共用。进度 0..1，由调用方换算语义值。
 */
@Composable
fun HorizontalShadeSlider(
    progress: Float,
    onProgress: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    var trackW by remember { mutableFloatStateOf(1f) }
    var value by remember(progress) { mutableFloatStateOf(progress) }

    val fill by animateColorAsState(
        if (enabled) colors.accent else colors.textTertiary.copy(alpha = 0.4f),
        tween(200), label = "sliderFill",
    )

    fun update(x: Float) {
        if (!enabled) return
        value = (x / trackW).coerceIn(0f, 1f)
        onProgress(value)
    }

    Box(
        modifier
            .height(20.dp)
            .onSizeChanged { trackW = it.width.toFloat().coerceAtLeast(1f) }
            .shadeInset(cornerRadius = 10.dp, offset = 3.dp, blur = 4.dp)
            .pointerInput(enabled) {
                detectTapGestures { offset -> update(offset.x) }
            }
            .pointerInput(enabled) {
                detectDragGestures { change, _ -> update(change.position.x) }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
                .fillMaxHeight()
                .padding(3.dp)
                .background(fill, RoundedCornerShape(7.dp)),
        )
    }
}
