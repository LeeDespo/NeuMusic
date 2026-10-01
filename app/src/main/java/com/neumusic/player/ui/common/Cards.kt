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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeSurface

/**
 * 歌手头像画框的**基准规格**：歌手页头像（216dp 见方）。
 * 搜索页歌手卡、主页关注歌手卡与它之间会「飞位」交接，所以画框必须**按比例**统一——
 * 三处卡片宽度不同（216/300/156dp），若用固定 dp 内缩，克隆卡缩放后画框粗细与圆角
 * 就对不上目标头像，交接瞬间会跳（用户实测的「动画有漏洞」）。这里所有尺寸都按
 * 「相对 216dp 的倍率」换算，于是任何卡片宽度下归一化规格都等于歌手页头像。
 */
private const val SINGER_FRAME_REF = 216f
private const val SINGER_FRAME_INSET = 10f
private const val SINGER_FRAME_CORNER = 14f
private const val SINGER_FRAME_SHADE_RADIUS = 20f
private const val SINGER_FRAME_SHADE_OFFSET = 6f
private const val SINGER_FRAME_SHADE_BLUR = 12f

/**
 * 歌手头像（凸起方形画框 + 方形头像）。歌手页头像、搜索页歌手卡、主页关注歌手卡共用，
 * 规格按 [SINGER_FRAME_REF] 等比换算——三处必须调这一个组件，不要再各自写一份。
 */
@Composable
fun SingerAvatarFrame(
    pic: String,
    desc: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    val density = LocalDensity.current
    // 先量出自身边长再换算（首帧 r=1，随后立刻校正；尺寸由父级决定，不会来回震荡）
    var size by remember { mutableStateOf(0.dp) }
    val r = if (size > 0.dp) size.value / SINGER_FRAME_REF else 1f
    Box(
        modifier
            .aspectRatio(1f)
            .onSizeChanged { size = with(density) { it.width.toDp() } }
            .shadeSurface(
                cornerRadius = (SINGER_FRAME_SHADE_RADIUS * r).dp,
                offset = (SINGER_FRAME_SHADE_OFFSET * r).dp,
                blur = (SINGER_FRAME_SHADE_BLUR * r).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val img = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding((SINGER_FRAME_INSET * r).dp)
            .clip(RoundedCornerShape((SINGER_FRAME_CORNER * r).dp))
            .background(colors.background)
        if (pic.isEmpty()) {
            Box(img, contentAlignment = Alignment.Center) { CoverPlaceholder() }
        } else {
            AsyncImage(
                model = pic,
                contentDescription = desc,
                contentScale = ContentScale.Crop,
                modifier = img,
            )
        }
    }
}

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
                Modifier
                    .fillMaxWidth()
                    .animateItem(   // 切标签时整行淡出/淡入（短过渡）
                        fadeInSpec = androidx.compose.animation.core.tween(170),
                        fadeOutSpec = androidx.compose.animation.core.tween(120),
                    )
                    .padding(horizontal = horizontalPadding).padding(bottom = 18.dp),
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
        // 画框规格按比例与歌手页头像一致（见 SingerAvatarFrame）
        SingerAvatarFrame(pic = pic, desc = name, modifier = Modifier.fillMaxWidth())
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
