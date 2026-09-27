package com.neumusic.player.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.launch

/**
 * 「双击回到顶部」。
 *
 * ## 为什么不是真的挂在系统状态栏上
 * Android **没有**公开 API 让应用感知"系统状态栏被双击"。状态栏区域由 SystemUI 的窗口
 * 独占，应用窗口即使 edge-to-edge 也收不到那里的触摸 —— 实测：手指从状态栏下滑会拉起
 * 通知栏而不是滚动列表；在那里双击也到不了应用。系统里没有等价接口，硬做就是造假。
 *
 * ## 采用的等效做法
 * 把双击检测**挂在列表本身**，而不是铺一层覆盖热区。这样：
 * - 列表任意位置双击都能回顶，比窄热区好按；
 * - **不挡滑动** —— 早期版本用覆盖热区时，Compose 的命中测试先命中上层节点，
 *   导致该区域拖不动列表（实测复现），故改掉；
 * - 行点击不受影响：拖动超过触摸阈值时，tap 检测会自动让位给滚动。
 *
 * 用法：`LazyColumn(modifier = Modifier.fillMaxSize().doubleTapToTop(listState))`。
 */
fun Modifier.doubleTapToTop(state: LazyListState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val timeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val slop = LocalViewConfiguration.current.touchSlop

    this.pointerInput(state, timeout) {
        awaitPointerEventScope {
            var lastTapUpTime = 0L
            var downPos = Offset.Zero
            var movedBeyondSlop = false
            while (true) {
                // 在 Initial 阶段**只观察不消费**：事件照常下传给列表，
                // 所以滚动/点击完全不受影响。
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                when {
                    change.pressed && !change.previousPressed -> {
                        downPos = change.position
                        movedBeyondSlop = false
                    }
                    change.pressed -> {
                        if ((change.position - downPos).getDistance() > slop) movedBeyondSlop = true
                    }
                    change.previousPressed && !change.pressed -> {
                        // 只有"真·点击"才计一次 tap：手指移动超过触摸阈值就是滑动，直接作废。
                        // 早前只看抬手时间，导致**连续快速上滑**被误判成双击、列表莫名跳回顶部（实测复现）。
                        if (movedBeyondSlop) {
                            lastTapUpTime = 0L
                        } else {
                            val now = change.uptimeMillis
                            if (lastTapUpTime != 0L && now - lastTapUpTime <= timeout) {
                                lastTapUpTime = 0L
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { state.animateScrollToItem(0) }
                            } else {
                                lastTapUpTime = now
                            }
                        }
                    }
                }
            }
        }
    }
}
