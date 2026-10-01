package com.neumusic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeSurface

/**
 * 无裁剪的卡片点击层。**带外阴影的卡片不能用 flatPressable**——它的 clip 会把封面画框
 * 左右两侧的阴影直接截断（用户实测指出"专辑卡左右光影被截断"），所以用这个。
 */
fun Modifier.cardTap(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }

/**
 * 专辑/歌单卡片（歌手页与搜索结果共用，用户 2026-09-30 规格）：
 * - 凸起画框封面，**画框细**（封面内缩 8dp）；
 * - 下方第一行 = 标题（左，最多两行）+ 歌曲数（靠右、与标题同字号，count<=0 不显示）；
 * - 第二行 = 副文本（歌手名等，**最多两行自动换行**，过长才省略；null 不显示）。
 */
@Composable
fun MediaCard(
    logo: String,
    title: String,
    count: Int,
    line2: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LocalShadeColors.current
    Column(modifier.fillMaxWidth().cardTap(onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadeSurface(cornerRadius = 16.dp, offset = 5.dp, blur = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(colors.background),
                    contentAlignment = Alignment.Center,
                ) { CoverPlaceholder() }
            } else {
                AsyncImage(
                    model = logo,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(colors.background),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                title,
                color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count > 0) {
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("$count 首", color = colors.textPrimary, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (!line2.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                line2,
                color = colors.textTertiary, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 自适应列数：可用宽度（含两侧 16dp contentPadding）下能放下几张 minCard 宽的卡（卡间 16dp）。
 */
fun gridColumns(screenWidth: androidx.compose.ui.unit.Dp, minCard: androidx.compose.ui.unit.Dp): Int =
    (((screenWidth - 32.dp) + 16.dp) / (minCard + 16.dp)).toInt().coerceAtLeast(1)

/**
 * 把结果按列数分块，一行一个 item 的卡片网格（仍是单个 LazyColumn：
 * 顶栏折叠、双击回顶、边缘渐隐都继续走同一个 listState，滚动位置跨标签统一）。
 * [maxCardWidth] 可限制单卡最大宽度（限宽后行内居中）。
 */
fun <T> androidx.compose.foundation.lazy.LazyListScope.cardGridItems(
    items: List<T>,
    columns: Int,
    key: (T) -> String,
    maxCardWidth: androidx.compose.ui.unit.Dp? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    itemContent: @Composable (T) -> Unit,
) {
    items.chunked(columns).forEach { row ->
        item(key = row.joinToString("|") { key(it) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { m ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.then(maxCardWidth?.let { Modifier.widthIn(max = it) } ?: Modifier)) {
                            itemContent(m)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
@Composable
/**
 * 歌手卡（搜索结果/主页关注栏用）：方形画框头像 + 名字（最多两行）+ 「歌曲 N · 专辑 N」。
 */
fun SingerCard(
    name: String,
    pic: String,
    songNum: Int,
    albumNum: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LocalShadeColors.current
    Column(modifier.fillMaxWidth().cardTap(onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadeSurface(cornerRadius = 20.dp, offset = 6.dp, blur = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (pic.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp)
                        .clip(RoundedCornerShape(14.dp)).background(colors.background),
                    contentAlignment = Alignment.Center,
                ) { CoverPlaceholder() }
            } else {
                AsyncImage(
                    model = pic,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp)
                        .clip(RoundedCornerShape(14.dp)).background(colors.background),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "歌曲 $songNum · 专辑 $albumNum",
            color = colors.textTertiary, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
