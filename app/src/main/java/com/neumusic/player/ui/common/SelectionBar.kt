package com.neumusic.player.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable

/**
 * 选择模式的顶栏工具条：全选 / 反选 / 取消 / 下载所选。
 *
 * 位置上替代折叠顶栏的标题区；进入/退出用滑动+淡入淡出，
 * 顶栏右侧的下载触发图标则做旋转动画（见调用处）。
 * 按钮都在页面底色上，用凸起（shadePressable）—— 不是任何容器的内部。
 */
@Composable
fun SelectionBar(
    selecting: Boolean,
    selectedCount: Int,
    downloading: String?,   // null = 空闲；"12/34" = 进度文案
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(66.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "已选 $selectedCount",
            color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (downloading != null) {
            Text(
                "下载中 $downloading",
                color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            )
            return
        }
        SelectionButton("全选", onSelectAll)
        SelectionButton("反选", onInvert)
        SelectionButton("取消", onCancel)
        SelectionButton(
            "下载所选",
            onClick = onDownload,
            accent = true,
            enabled = selectedCount > 0,
        )
    }
}

/** 选择工具条里的按钮。 */
@Composable
private fun SelectionButton(
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalShadeColors.current
    Row(
        Modifier
            .then(if (enabled) Modifier.flatPressable(cornerRadius = 14.dp, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = when {
                !enabled -> colors.textTertiary
                accent -> colors.accent
                else -> colors.textSecondary
            },
            fontSize = 13.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/**
 * 顶栏标题区与选择工具条的过渡容器（配合下载触发按钮的旋转）。
 * [downloadRotation] 由调用处根据 selecting 状态用 animateFloatAsState 提供。
 */
@Composable
fun TopBarContentSwitch(
    selecting: Boolean,
    normal: @Composable () -> Unit,
    selection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = selecting,
        transitionSpec = {
            (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160)))
        },
        modifier = modifier,
        label = "topBarSwitch",
    ) { sel ->
        if (sel) selection() else normal()
    }
}

/** 下载触发按钮的旋转动画值（选中模式 90°，普通 0°）。 */
@Composable
fun downloadIconRotation(selecting: Boolean): Float =
    animateFloatAsState(if (selecting) 90f else 0f, tween(220), label = "downloadRot").value
