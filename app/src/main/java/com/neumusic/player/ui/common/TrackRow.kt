package com.neumusic.player.ui.common

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.SongApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface

/**
 * 统一播放入口：把整个列表交给 PlayerHost 作为队列，从 [startAt] 开始播。
 * 这样上一首/下一首/随机/单曲循环才有意义。
 */
fun playQueue(context: Context, tracks: List<Track>, startAt: Int) {
    PlayerHost.playQueue(tracks, startAt) { msg -> toastMain(context, msg) }
}

/** 任意线程安全地弹 Toast。 */
fun toastMain(context: Context, msg: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 列表整块凸起容器。
 *
 * 设计原则：**整个列表是一块凸起**，而不是每行各自凸起；
 * 行与行之间只用一条短横线分隔（见 [RowDivider]）。
 * 这样列表内部是「平」的，符合「不在凸起上再做凸起」。
 */
@Composable
fun TrackListBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .shadeSurface(cornerRadius = cornerRadius, offset = 6.dp, blur = 10.dp)
            .padding(vertical = 6.dp),
    ) {
        Column { content() }
    }
}

/** 行间短横线：居中、约一半宽、两端不触及边缘，避免通栏分割线的割裂感。 */
@Composable
fun RowDivider(widthFraction: Float = 0.52f) {
    val colors = LocalShadeColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 1.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(1.dp)
                .background(colors.textTertiary.copy(alpha = 0.42f), RoundedCornerShape(0.5.dp)),
        )
    }
}

/**
 * 统一的曲目行（位于 [TrackListBlock] 内部，所以**自身不再凸起**）。
 *
 * 按压反馈用背景微暗表达，不做阴影形变 —— 因为它所在的面已经是凸起。
 * 喜欢按钮：未喜欢=平+空心爱心，已喜欢=凹陷+实心爱心（凸起中以凹陷表选中）。
 */
@Composable
fun TrackRow(
    track: Track,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    liked: Boolean = false,
    onLike: (() -> Unit)? = null,
    /** 选择模式（顶栏下载按钮触发）：此时点行 = 选中/取消，不播放。 */
    selecting: Boolean = false,
    selected: Boolean = false,
    /** 已下载：整行置灰、不可选（用户要求）。 */
    downloaded: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    val colors = LocalShadeColors.current
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        when {
            selected -> colors.accent.copy(alpha = 0.14f)
            pressed -> lerp(colors.background, Color.Black, 0.05f)
            else -> Color.Transparent
        },
        tween(140), label = "rowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ▸ 可播放区：只有"封面 + 文字"这一块响应点击播放。
        //   右侧按钮**不在**这个区域内 —— 否则整行的大点击区会和按钮抢手势
        //   （Compose 里父子都有 pointerInput 时，子按钮实测点不动，故刻意分开）。
        //   选择模式下点击 = 选中/取消；已下载曲目置灰且不可选。
        val contentColor = if (downloaded) colors.textTertiary else colors.textPrimary
        Row(
            Modifier
                .weight(1f)
                .flatPressable(cornerRadius = 14.dp) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when {
                        downloaded -> Unit // 已下载：不响应（置灰不可选）
                        selecting -> onToggleSelect()
                        else -> onPlay()
                    }
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlbumArt(track.coverUrl, 48.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.name,
                        color = contentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track.isVip) {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.accent.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("VIP", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    buildString {
                        append(track.singer)
                        append(" · ")
                        append(track.albumName.ifEmpty { track.durationText })
                        if (downloaded) append(" · 已下载")
                    },
                    color = colors.textTertiary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onLike != null) {
            // 已喜欢：凹陷 + 实心；未喜欢：平 + 空心。凹陷在凸起面上表"选中"。
            Box(
                Modifier
                    .size(38.dp)
                    .then(
                        if (liked) Modifier.shadeInset(cornerRadius = 19.dp, offset = 3.dp, blur = 4.dp)
                        else Modifier
                    )
                    .flatPressable(cornerRadius = 19.dp) { onLike() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "取消喜欢" else "喜欢",
                    tint = if (liked) colors.accent else colors.textTertiary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        // 「更多」：播放/下一首播放/歌曲信息/查看专辑/查看格式（用户要求：行内不再放下载）。
        Box(
            Modifier.size(38.dp).flatPressable(cornerRadius = 19.dp) { onMore() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert, contentDescription = "更多",
                tint = colors.textTertiary, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 封面。默认**平贴**（无阴影）—— 因为它多数时候坐在凸起卡片/区块里，
 * 再套一层凸起底盘就违反「不在凸起上做凸起」。
 * 只有直接坐在页面底色上时（播放页大封面）才传 `plate = true` 垫凸起底盘。
 */
@Composable
fun AlbumArt(url: String, size: Dp, corner: Dp = 14.dp, plate: Boolean = false) {
    val colors = LocalShadeColors.current
    val inner = if (plate) size - 10.dp else size
    val innerCorner = if (plate) corner - 4.dp else corner

    Box(
        Modifier
            .size(size)
            .then(if (plate) Modifier.shadeSurface(cornerRadius = corner, offset = 5.dp, blur = 8.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isEmpty()) {
            Box(
                Modifier.size(inner).clip(RoundedCornerShape(innerCorner)).background(colors.background),
                contentAlignment = Alignment.Center,
            ) { CoverPlaceholder() }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(inner)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(colors.background),
            )
        }
    }
}

/** 占位图标（无封面时用首字母，或退化为音符图标）。 */
@Composable
fun CoverPlaceholder(icon: ImageVector = Icons.Filled.MusicNote, text: String? = null) {
    val colors = LocalShadeColors.current
    if (text != null) {
        Text(text, color = colors.textTertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    } else {
        Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
    }
}

/**
 * 取播放直链（按设置里的音质，取不到自动降级）。
 * 失败抛 [com.neumusic.player.data.api.SongApi.PlayUrlException]，message 可直接展示。
 */
suspend fun loadUrl(track: Track): String? = SongApi.playUrl(track, Prefs.quality)
