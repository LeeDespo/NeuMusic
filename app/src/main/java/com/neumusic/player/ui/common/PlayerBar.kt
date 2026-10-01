package com.neumusic.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.SearchSinger
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.SingerApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.shade.shadeSurfaceTop
import kotlinx.coroutines.launch

/**
 * 页面内容与底栏交界处的渐隐条：与列表上下边缘的渐隐同款（背景色梯度），
 * 内容滚到底栏跟前先「溶」进底色再被底栏接住，避免硬切。不拦截触摸。
 */
@Composable
fun BottomBarFade(height: Dp = 24.dp) {
    val colors = LocalShadeColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0f),
                    1f to colors.background,
                )
            ),
    )
}

/** 无裁剪的点击层：给带外阴影的子内容（如凸起画框封面）包点击时不能用 flatPressable——它的 clip 会把阴影裁掉。 */
private fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }

/**
 * 底部播放栏（用户 2026-09-29 两行版规格 + 2026-09-30 改版）：
 * - 覆盖底部的凸起面板（顶角圆、底边贴屏幕下缘，导航栏区域由面板自身延伸盖住）；
 * - 左侧封面**跨两行、占位更大**：凸起画框圆角矩形；设置开了「播放条音频可视化」时
 *   封面与画框都变圆形，四周围一圈播放详情页同款的径向电平柱；
 * - 右侧分两行：**第一行只放歌名**（大字号、跑马灯滚动）；**第二行从左到右是
 *   歌手（可点进歌手页）、暂停、喜欢、播放列表**；
 * - 切歌手势：整条面板上**左滑=下一首、右滑=上一首**，触发后内容向滑动方向滑出、
 *   新曲目从对侧滑入（动画期间曲目数据实时替换）；按钮的 tap 不受影响；
 * - 点封面或歌名块打开播放详情。
 *
 * 面板上的画框/标题块/按钮都是「凸起上再做凸起」——底栏是容器级凸起，用户已放开该禁令。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    track: Track,
    onOpen: () -> Unit,
    onOpenSinger: (SearchSinger) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val playing by PlayerHost.isPlaying.collectAsState()
    val likedIds by LikedStore.liked.collectAsState()
    val vizOn by Prefs.barVizFlow.collectAsState()
    val liked = track.songId > 0L && track.songId in likedIds
    var showQueue by remember { mutableStateOf(false) }

    // 切歌动画：内容向滑动方向滑出（exit），新曲目从对侧滑入（enter）。
    // swap.value ∈ [-1,1]：负=偏左。切换动作立即触发，曲目数据在动画期间由流替换。
    val swap = remember { Animatable(0f) }
    fun swapSwitch(toNext: Boolean) {
        scope.launch {
            val dir = if (toNext) -1f else 1f
            swap.animateTo(dir, tween(130, easing = LinearOutSlowInEasing))
            swap.snapTo(-dir)
            swap.animateTo(0f, tween(190, easing = LinearOutSlowInEasing))
        }
    }

    // 歌手：多歌手先弹窗问访问哪个（用户规格），单歌手直接解析进页。
    var singerPick by remember { mutableStateOf<List<String>?>(null) }
    fun resolveAndOpen(name: String) {
        scope.launch {
            val s = runCatching { SingerApi.resolve(name) }.getOrNull()
            if (s == null || s.mid.isEmpty()) {
                toastMain(ctx, "没找到歌手「$name」")
            } else {
                onOpenSinger(s)
            }
        }
    }
    fun openSinger(raw: String) {
        val names = raw.split("/", "、", ",").map { it.trim() }.filter { it.isNotEmpty() }
        when {
            names.isEmpty() -> Unit
            names.size == 1 -> resolveAndOpen(names[0])
            else -> singerPick = names
        }
    }

    Column(modifier) {
        BottomBarFade()
        Box(
            Modifier
                .fillMaxWidth()
                .shadeSurfaceTop(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                // 切歌手势：左滑下一首、右滑上一首。按钮的 tap 在拖动超距后自然取消，互不干扰。
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val threshold = 80.dp.toPx()
                            when {
                                total <= -threshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swapSwitch(toNext = true)
                                    PlayerHost.next()
                                }
                                total >= threshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swapSwitch(toNext = false)
                                    PlayerHost.previous()
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        total += dragAmount
                        change.consume()
                    }
                }
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // 面板内容整体参与切歌滑出/滑入动画（面板本身与阴影不动）
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = swap.value * 110.dp.toPx()
                        alpha = (1f - kotlin.math.abs(swap.value) * 1.15f).coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 封面：跨两行
                if (vizOn) {
                    CircularCoverViz(track.coverUrl, playing, Modifier.tap(onOpen))
                } else {
                    Box(Modifier.tap(onOpen)) {
                        AlbumArt(track.coverUrl, 84.dp, corner = 20.dp, plate = true)
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // 第一行：歌名凸起块（大字号，跑马灯）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .shadeSurface(cornerRadius = 12.dp, offset = 3.dp, blur = 5.dp)
                            .flatPressable(cornerRadius = 12.dp, onClick = onOpen)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            track.name,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                    }
                    // 第二行：歌手（可点进歌手页）/ 暂停 / 喜欢 / 播放列表
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            Text(
                                track.singer,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .flatPressable(cornerRadius = 10.dp) { openSinger(track.singer) }
                                    .basicMarquee()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        BarButton(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playing) "暂停" else "播放",
                            colors.accent,
                        ) { PlayerHost.toggle() }
                        BarButton(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (liked) "取消喜欢" else "喜欢",
                            if (liked) colors.accent else colors.textSecondary,
                        ) { toggleLike(ctx, track, liked) }
                        BarButton(Icons.AutoMirrored.Filled.List, "播放列表", colors.textSecondary) {
                            showQueue = true
                        }
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueDialog(
            tracks = PlayerHost.queueSnapshot(),
            currentIndex = PlayerHost.currentIndex(),
            onDismiss = { showQueue = false },
            onPick = {
                showQueue = false
                PlayerHost.playAt(it)
            },
        )
    }

    // 多歌手弹窗：点歌手名后先问访问哪个
    singerPick?.let { names ->
        ShadeDialog(title = "访问哪个歌手？", onDismiss = { singerPick = null }) {
            names.forEach { n ->
                ShadeDialogRow(n) {
                    singerPick = null
                    resolveAndOpen(n)
                }
            }
        }
    }
}

/** 播放栏里的凸起圆角矩形图标钮：按压凸→凹。 */
@Composable
private fun BarButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .shadePressable(cornerRadius = 13.dp, offset = 3.dp, blur = 5.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * 圆形封面 + 圆形凸起画框 + 四周围一圈径向电平柱（[PlayerBar] 可视化形态）。
 * 频段数与播放详情页的频谱环一致（[VIZ_BARS] 根，从 [VizHost.BARS] 段降采样映射）。
 */
@Composable
private fun CircularCoverViz(url: String, playing: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalShadeColors.current
    // 电平流在此订阅（高频流）：重组被隔离在封面子树，不带动整条播放栏
    val usingFft by VizHost.usingFft.collectAsState()
    val fftLevels by VizHost.levels.collectAsState()
    val pcmLevels by PlayerHost.vizProcessor.levels.collectAsState()
    val levels = if (usingFft) fftLevels else pcmLevels
    val disc = 66.dp
    val gap = 4.dp
    val maxLen = 8.dp
    Box(modifier.size(disc + (gap + maxLen) * 2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val discR = disc.toPx() / 2f
            val innerR = discR + gap.toPx()
            val len = maxLen.toPx()
            val barW = 1.8.dp.toPx()
            for (i in 0 until VIZ_BARS) {
                val level = levels.getOrElse(i * levels.size / VIZ_BARS) { 0f }
                val shown = (if (playing) level else level * 0.15f).coerceIn(0f, 1f)
                val angle = -90.0 + 360.0 * i / VIZ_BARS
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = colors.accent.copy(alpha = 0.25f + 0.75f * shown),
                    start = Offset(size.width / 2f + innerR * cos, size.height / 2f + innerR * sin),
                    end = Offset(
                        size.width / 2f + (innerR + len * shown.coerceAtLeast(0.06f)) * cos,
                        size.height / 2f + (innerR + len * shown.coerceAtLeast(0.06f)) * sin,
                    ),
                    strokeWidth = barW,
                    cap = StrokeCap.Round,
                )
            }
        }
        // 圆形凸起画框 + 圆封面
        Box(
            Modifier
                .size(disc)
                .shadeSurface(cornerRadius = disc / 2, offset = 4.dp, blur = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isEmpty()) {
                Box(
                    Modifier.size(disc - 8.dp).clip(CircleShape).background(colors.background),
                    contentAlignment = Alignment.Center,
                ) { CoverPlaceholder() }
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(disc - 8.dp).clip(CircleShape).background(colors.background),
                )
            }
        }
    }
}

/** 径向电平柱的根数（播放栏封面与播放详情页频谱环统一）。 */
const val VIZ_BARS = 40
