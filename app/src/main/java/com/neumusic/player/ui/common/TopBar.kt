package com.neumusic.player.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadePressable

/**
 * 随滑动上移隐藏的顶栏高度。
 *
 * Compose 没有现成的 `enterAlways` 折叠行为，这里用滚动偏移驱动一个
 * 0→1 的进度：往下滚时顶栏上移并淡出，回到顶部时复位。
 */
private val TOP_BAR_HEIGHT = 66.dp

/**
 * 折叠顶栏：返回按钮 + 标题。
 *
 * [visible] 由调用方根据滚动状态算好传入（见 [rememberTopBarVisible]）。
 * 顶栏用 `graphicsLayer` 做位移，不参与布局，所以隐藏时不会顶动内容。
 */
@Composable
fun CollapsingTopBar(
    title: String,
    onBack: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    /** 顶栏右侧的附加按钮（如下载触发钮）；null 则不放。 */
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalShadeColors.current
    val density = LocalDensity.current
    val shift by animateFloatAsState(
        if (visible) 0f else 1f,
        tween(220), label = "topBarShift",
    )
    val hPx = with(density) { (TOP_BAR_HEIGHT + 8.dp).toPx() }

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .graphicsLayer {
                translationY = -shift * hPx
                alpha = 1f - shift
            }
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "返回",
                tint = colors.accent, modifier = Modifier.size(19.dp),
            )
        }
        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 由列表滚动位置推导顶栏是否可见：接近顶部时显示，向下滚过阈值就隐藏。
 * 带一点阻尼，避免轻微滚动造成闪烁。
 */
@Composable
fun rememberTopBarVisible(state: LazyListState): Boolean {
    val visible by remember(state) {
        derivedStateOf {
            // firstVisibleItemIndex==0 且偏移很小 → 视为在顶部
            val atTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 24
            val scrollingDown = state.isScrollInProgress && state.firstVisibleItemScrollOffset > 0
            atTop || !scrollingDown
        }
    }
    return visible
}
