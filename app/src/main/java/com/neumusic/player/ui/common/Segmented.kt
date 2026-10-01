package com.neumusic.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset

/**
 * 平的分段控制器（歌手页同款）：选项并排，选中项为凹陷圆角矩形、未选中为纯文字，
 * 中间可选一道「|」分隔。宿主必须是「平」的面（或凸起容器，此时凹陷仍表示选中）。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    withDivider: Boolean = false,
) {
    val colors = LocalShadeColors.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, label ->
            if (withDivider && i > 0) {
                Text("|", color = colors.textTertiary, fontSize = 13.sp)
            }
            val sel = i == selected
            Box(
                Modifier
                    .then(if (sel) Modifier.shadeInset(cornerRadius = 12.dp, offset = 2.dp, blur = 4.dp) else Modifier)
                    .flatPressable(cornerRadius = 12.dp) { onSelect(i) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = if (sel) colors.accent else colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
