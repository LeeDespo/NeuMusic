package com.neumusic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadePressable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 「选择下载模式是否在某页激活」的全局信号：AppRoot 的播放栏据此下降让位
 * （选择底栏上升时播放栏必须先沉下去，二者共用屏幕底部）。
 * 各有选择模式的页面（曲目列表、搜索结果）进出时置位/复位。
 */
object SelectionBus {
    val active = MutableStateFlow(false)
}

/** 页面接入 [SelectionBus] 的标准写法：selecting 置位/复位都同步到总线。 */
@Composable
fun rememberSelectionBusSync(selecting: Boolean) {
    androidx.compose.runtime.DisposableEffect(selecting) {
        if (selecting) SelectionBus.active.value = true
        onDispose { SelectionBus.active.value = false }
    }
}

/**
 * 选择模式的**底部工具栏**（用户 2026-09-29 规格）：全选 / 反选 / 下载 三个无文字图标钮，
 * 随选择模式上升出现、退出时下滑消失（外层用 AnimatedVisibility 驱动进出）。
 * 样式与播放栏同款：交界渐隐条 + 顶角圆的凸起面板（导航栏区域由面板延伸盖住）。
 * 取消入口是页面顶栏上的下载钮（选择模式中变为 ✕）。
 */
@Composable
fun SelectionBar(
    selectedCount: Int,
    downloading: String?,   // null = 空闲；"12/34" = 进度文案
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    val busy = downloading != null
    Column(modifier) {
        BottomBarFade(height = 20.dp)
        Column(
            Modifier
                .fillMaxWidth()
                // 与播放栏同款：不画阴影、不做圆角（最外围光影会与上方渐隐条糊在一起）
                .background(colors.background)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (busy) "下载中 $downloading" else "已选 $selectedCount",
                color = if (busy) colors.accent else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionIconButton(Icons.Filled.SelectAll, "全选", enabled = !busy, onClick = onSelectAll)
                SelectionIconButton(Icons.Filled.IndeterminateCheckBox, "反选", enabled = !busy, onClick = onInvert)
                SelectionIconButton(
                    Icons.Filled.Download, "下载所选",
                    enabled = !busy && selectedCount > 0, accent = true, onClick = onDownload,
                )
            }
        }
    }
}

/** 选择底栏里的凸起圆角矩形图标钮：按压凸→凹；禁用时变灰且不可点。 */
@Composable
private fun SelectionIconButton(
    icon: ImageVector,
    desc: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        Modifier
            .size(46.dp)
            .then(
                if (enabled) Modifier.shadePressable(cornerRadius = 15.dp, offset = 4.dp, blur = 6.dp, onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = when {
                !enabled -> colors.textTertiary
                accent -> colors.accent
                else -> colors.textSecondary
            },
            modifier = Modifier.size(21.dp),
        )
    }
}
