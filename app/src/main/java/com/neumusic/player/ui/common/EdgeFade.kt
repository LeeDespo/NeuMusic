package com.neumusic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.shade.LocalShadeColors

/**
 * 列表/卡片行边缘的渐隐遮罩。
 *
 * ## 解决的两个问题（用户反馈）
 * 1. **边缘截断**：LazyRow/LazyColumn 在视口边界直接把内容切掉，滑动到边缘的卡片
 *    或行会"咔"地消失一半。渐隐遮罩让内容在到达边界前先淡入背景色，观感上优雅消失。
 * 2. **阴影被截**：卡片阴影（位移+模糊）超出卡片自身边界，被行的视口裁掉，
 *    导致第一张卡片的左侧、最后一张卡片的右侧阴影不完整。配套做法是给行加
 *    `contentPadding`，让首尾卡片离视口边缘留出一整段阴影空间（见 HomeScreen）。
 *
 * 遮罩只是画渐变色、**不拦截触摸**（Box 无 pointerInput，事件照常穿透）。
 * `canScrollBackward/Forward` 决定只在实际可滚动的那一侧显示，避免内容不满一屏
 * 时也蒙一层灰。
 */
@Composable
fun BoxScope.HorizontalEdgeFades(
    state: LazyListState,
    width: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    if (state.canScrollBackward) {
        Box(
            modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        0f to colors.background,
                        1f to colors.background.copy(alpha = 0f),
                    )
                ),
        )
    }
    if (state.canScrollForward) {
        Box(
            modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        0f to colors.background.copy(alpha = 0f),
                        1f to colors.background,
                    )
                ),
        )
    }
}

/**
 * 顶部渐隐（2026-09-30 规格）：**盖过系统状态栏**、范围更大（状态栏高度 + [extraHeight]）。
 * 放在页面根 Box 里、statusBarsPadding 子树之外，列表内容从状态栏底下滚过时先溶进底色。
 * 由调用方按 `canScrollBackward` 决定显隐（列表在顶部时不蒙灰）。
 */
@Composable
fun BoxScope.TopEdgeFade(extraHeight: Dp = 48.dp, modifier: Modifier = Modifier) {
    val colors = LocalShadeColors.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val total = statusTop + extraHeight
    // 「彻底消失线」在状态栏上缘：状态栏以上完全不透明（内容彻底消失），状态栏偏下还能
    // 隐约看到渐隐中的内容，往下 48dp 内渐变到全透明（2026-09-30 用户规格）。
    val solidAt = (statusTop / total).coerceIn(0.05f, 0.95f)
    Box(
        modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(total)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to colors.background,
                        solidAt to colors.background,
                        1f to colors.background.copy(alpha = 0f),
                    )
                )
            ),
    )
}

/** 垂直列表的上下渐隐。[top]/[bottom] 可单独关掉某一侧。 */
@Composable
fun BoxScope.VerticalEdgeFades(
    state: LazyListState,
    height: Dp = 30.dp,
    top: Boolean = true,
    bottom: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    if (top && state.canScrollBackward) {
        Box(
            modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        0f to colors.background,
                        1f to colors.background.copy(alpha = 0f),
                    )
                ),
        )
    }
    if (bottom && state.canScrollForward) {
        Box(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = 0f),
                        1f to colors.background,
                    )
                ),
        )
    }
}
