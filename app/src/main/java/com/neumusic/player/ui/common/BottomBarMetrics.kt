package com.neumusic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neumusic.player.player.PlayerHost

/**
 * 播放栏（覆盖在底部的底栏）的实测高度。由 AppRoot 在播放栏 `onSizeChanged` 时写入，
 * 各滚动页面据此在**底部留白**，内容滑到底不会被播放栏压住。
 *
 * 早前各页面各自硬编码 `bottom = 150.dp`，既不准（可视化模式下面板更高）又容易漏；
 * 现在统一从这里取。
 */
object BottomBarMetrics {
    var heightPx by mutableFloatStateOf(0f)
}

/**
 * 页面底部需要留出的空白：有曲目时 = 播放栏实测高度 + [extra]，没有曲目时 = 0
 * （此时播放栏是沉下去的，留白反而是一块莫名的空档）。
 */
@Composable
fun rememberPlayerBarSpace(extra: Dp = 8.dp): Dp {
    val current by PlayerHost.current.collectAsState()
    val h = BottomBarMetrics.heightPx
    if (current == null || h <= 0f) return 0.dp
    return with(LocalDensity.current) { h.toDp() } + extra
}
