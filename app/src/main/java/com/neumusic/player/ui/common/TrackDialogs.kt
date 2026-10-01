package com.neumusic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neumusic.player.data.Track
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface

/**
 * 新拟物弹层：半透明压暗背景 + 居中凸起面板。
 * 面板内部按 UI 原则只放「平」的行（凸起容器内不再凸起）。
 * 点击背景关闭。
 */
@Composable
fun ShadeDialog(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }   // 点背景关闭
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .shadeSurface(cornerRadius = 26.dp, offset = 8.dp, blur = 14.dp)
                // 面板消费点击，避免穿透到背景把弹窗关掉。
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(
                title,
                color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            content()
        }
    }
}

/**
 * 播放队列弹窗：当前播放队列，点选跳播。
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
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
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
