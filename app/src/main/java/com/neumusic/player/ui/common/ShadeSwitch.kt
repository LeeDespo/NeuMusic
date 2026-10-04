package com.neumusic.player.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface

/**
 * 新拟物开关（用户 2026-10-01 规格）：**凹陷轨道 + 凸起滑块**，
 * 打开时轨道染强调色、滑块滑到右侧；点轨道任意处切换。
 *
 * 与全 App 其它按压控件同一套路：凸起滑块带外阴影、轨道是凹槽（凹陷里只放平面与滑块本身，
 * 不再叠凹陷）。要标签就直接用 [ShadeSwitchRow]。
 */
@Composable
fun ShadeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalShadeColors.current
    val haptic = LocalHapticFeedback.current
    val w = 56.dp
    val h = 30.dp
    val knob = 22.dp
    val pad = 4.dp
    val t by animateFloatAsState(
        if (checked) 1f else 0f,
        tween(180, easing = FastOutSlowInEasing),
        label = "switchT",
    )
    Box(
        modifier
            .size(w, h)
            .shadeInset(cornerRadius = h / 2, offset = 3.dp, blur = 5.dp)
            .pointerInput(checked, enabled) {
                detectTapGestures {
                    if (!enabled) return@detectTapGestures
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道底色：关闭=平面，打开=强调色（低饱和，不抢滑块）
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(h / 2))
                // 打开时轨道直接用强调色（原来混了 55% 背景，看着比真正的强调色发白发灰）
                .background(lerp(colors.background, colors.accent, t)),
        )
        // 滑块：凸起小圆
        Box(
            Modifier
                .padding(start = lerp(pad, w - knob - pad, t))
                .size(knob)
                .shadeSurface(cornerRadius = knob / 2, offset = 2.dp, blur = 4.dp),
        )
    }
}

/** 一行开关：左标签（可带副标题）+ 右新拟物开关。 */
@Composable
fun ShadeSwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalShadeColors.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = colors.textPrimary, fontSize = 14.sp,
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.textTertiary, fontSize = 11.sp)
            }
        }
        ShadeSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
