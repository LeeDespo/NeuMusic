package com.neumusic.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.neumusic.player.data.Track
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface

/**
 * 新拟物弹层（用户 2026-10-01 规格）：**整页呈现**，不再是「半透明压暗背景 + 居中浮层」。
 *
 * - 背景是**纯底色**（不再压暗、不再模糊），整页淡入浮现（透明度 + 轻微上浮）；
 * - 顶栏一个凸起圆形收起钮 + 标题（与其它二级页面同一种形态）；
 * - 内容放在**凸起卡片**里，卡片的光影就是全 App 那套（早前浮层压在压暗背景上时，
 *   只有亮影看得见，观感像"两侧都是高光"，与别处不一致）。
 */
@Composable
fun ShadeDialog(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalShadeColors.current
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    // 关闭也要有动画（用户 2026-10-01）：先倒放一遍进场动画，再真正通知调用方关闭。
    // 用 Animatable 而不是 animateFloatAsState——后者无法在动画播完后才执行动作。
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    val t = appear.value
    fun close() {
        if (appear.value < 1f) return          // 进场还没走完，忽略
        scope.launch {
            appear.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .graphicsLayer {
                alpha = t
                translationY = (1f - t) * 16.dp.toPx()
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = { close() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, "收起",
                        tint = colors.textPrimary, modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    title,
                    color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .weight(1f, fill = false)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(vertical = 8.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * 播放队列弹窗（整页）：当前播放队列，点选跳播。
 * 播放详情页与播放栏共用（播放栏的第二行「播放列表」按钮也弹它）。
 */
@Composable
fun QueueDialog(
    tracks: List<Track>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val colors = LocalShadeColors.current
    ShadeDialog(title = "播放队列（${tracks.size} 首）", onDismiss = onDismiss) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { i, t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .flatPressable(cornerRadius = 0.dp) { onPick(i) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}.",
                        color = if (i == currentIndex) colors.accent else colors.textTertiary,
                        fontSize = 12.sp, modifier = Modifier.width(34.dp),
                    )
                    Text(
                        t.name,
                        color = if (i == currentIndex) colors.accent else colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (i == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 弹层里的一行选项（平面按压）。 */
@Composable
fun ShadeDialogRow(label: String, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .flatPressable(cornerRadius = 0.dp, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Text(label, color = colors.textPrimary, fontSize = 15.sp)
    }
}

/** 弹层里的只读信息行。 */
@Composable
fun ShadeDialogInfo(label: String, value: String) {
    val colors = LocalShadeColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.weight(0.32f))
        Text(value, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(0.68f))
    }
}

/** 歌曲行右侧「更多」弹窗内容。 */
@Composable
fun TrackMoreDialog(
    track: Track,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onInfo: () -> Unit,
    onOpenAlbum: () -> Unit,
    onFormats: () -> Unit,
) {
    ShadeDialog(title = track.name, onDismiss = onDismiss) {
        Column(Modifier.padding(bottom = 10.dp)) {
            ShadeDialogRow("播放") { onDismiss(); onPlay() }
            ShadeDialogRow("下一首播放") { onDismiss(); onPlayNext() }
            ShadeDialogRow("查看歌曲信息") { onDismiss(); onInfo() }
            if (track.albumMid.isNotEmpty()) ShadeDialogRow("查看专辑") { onDismiss(); onOpenAlbum() }
            if (track.fileSizes.isNotEmpty()) ShadeDialogRow("查看格式") { onDismiss(); onFormats() }
        }
    }
}

/** 歌曲信息弹窗。 */
@Composable
fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    ShadeDialog(title = "歌曲信息", onDismiss = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ShadeDialogInfo("歌名", track.name)
            ShadeDialogInfo("歌手", track.singer)
            if (track.albumName.isNotEmpty()) ShadeDialogInfo("专辑", track.albumName)
            ShadeDialogInfo("时长", track.durationText)
            ShadeDialogInfo("歌曲 MID", track.mid)
            if (track.songId > 0) ShadeDialogInfo("数字 ID", track.songId.toString())
            ShadeDialogInfo("收费", if (track.isVip) "VIP / 付费" else "免费")
        }
    }
}

/** 格式弹窗：该曲各格式与文件大小。 */
@Composable
fun TrackFormatsDialog(track: Track, onDismiss: () -> Unit) {
    ShadeDialog(title = "可用格式", onDismiss = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            track.fileSizes.forEach { (key, bytes) ->
                ShadeDialogInfo(formatLabel(key), formatBytes(bytes))
            }
        }
    }
}

/** 把 file.* 的 key 翻成人话。 */
private fun formatLabel(key: String): String = when (key) {
    "128mp3" -> "标准 128k MP3"
    "192mp3" -> "192k MP3"
    "320mp3" -> "高品质 320k MP3"
    "96aac" -> "流畅 96k AAC"
    "48aac" -> "48k AAC"
    "24aac" -> "24k AAC"
    "192ogg" -> "192k OGG"
    "320ogg" -> "320k OGG"
    "192aac" -> "192k AAC"
    "flac" -> "无损 FLAC"
    "ape" -> "无损 APE"
    "dts" -> "DTS"
    "try" -> "试听片段"
    else -> key
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1 shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
