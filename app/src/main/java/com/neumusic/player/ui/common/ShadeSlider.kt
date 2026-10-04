package com.neumusic.player.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset

/** 横向凹陷轨道；局部拖动值，抬手/取消/点击/无障碍操作时提交一次。 */
@Composable
fun HorizontalShadeSlider(
    progress: Float,
    onProgress: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCommit: (Float) -> Unit = {},
) {
    val colors = LocalShadeColors.current
    var trackW by remember { mutableFloatStateOf(1f) }
    var value by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val preview by rememberUpdatedState(onProgress)
    val commit by rememberUpdatedState(onCommit)
    val fill by animateColorAsState(
        if (enabled) colors.accent else colors.textTertiary.copy(alpha = 0.4f),
        tween(200), label = "sliderFill",
    )
    fun update(x: Float) {
        if (!enabled) return
        value = (x / trackW).coerceIn(0f, 1f)
        preview(value)
    }
    Box(
        modifier.heightIn(min = 48.dp)
            .onSizeChanged { trackW = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                if (!enabled) disabled()
                setProgress { target ->
                    if (enabled) {
                        value = target.coerceIn(0f, 1f)
                        preview(value); commit(value); true
                    } else false
                }
            }
            .pointerInput(enabled) {
                detectTapGestures { offset -> if (enabled) { update(offset.x); commit(value) } }
            }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures(
                    onDragStart = { update(it.x) },
                    onDragEnd = { commit(value) },
                    onDragCancel = { commit(value) },
                ) { change, _ -> update(change.position.x) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(20.dp)
            .shadeInset(cornerRadius = 10.dp, offset = 3.dp, blur = 4.dp)) {
            Box(Modifier.fillMaxWidth(value).fillMaxHeight().padding(3.dp)
                .background(fill, RoundedCornerShape(7.dp)))
        }
    }
}
