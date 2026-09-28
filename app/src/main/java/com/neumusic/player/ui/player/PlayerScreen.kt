package com.neumusic.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.Lyrics
import com.neumusic.player.data.PlayMode
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.SongApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.toastMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import com.neumusic.player.ui.common.CoverPlaceholder
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.api.LikeResult
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import com.neumusic.player.shade.flatPressable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.List

/** 播放页（覆盖主页）。[onBack] 由外层驱动下滑动画。左右滑动切换封面页 / 歌词页。 */
@Composable
fun PlayerScreen(onBack: () -> Unit, onOpenEqualizer: () -> Unit = {}) {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val track by PlayerHost.current.collectAsState()
    val playing by PlayerHost.isPlaying.collectAsState()
    val liked by PlayerHost.liked.collectAsState()
    val lyrics by PlayerHost.lyrics.collectAsState()

    // 可视化电平：FFT（Visualizer）优先；模拟器等无实现时回退 PCM 电平。
    // 之前 CoverDisc 只订阅 VizHost.levels，回退场景下恒为零（"频谱环没效果"的原因）。
    val usingFft by VizHost.usingFft.collectAsState()
    val fftLevels by VizHost.levels.collectAsState()
    val pcmLevels by PlayerHost.vizProcessor.levels.collectAsState()
    val vizLevels = if (usingFft) fftLevels else pcmLevels
    val lyricSize by Prefs.lyricSizeFlow.collectAsState()
    val transPref by Prefs.lyricTransFlow.collectAsState()
    // 本地副本，便于页面上的按钮即时切换（同时写回设置）。
    var showTrans by remember(transPref) { mutableStateOf(transPref) }
    var showRoman by remember { mutableStateOf(Prefs.showLyricRoman) }
    var showKana by remember { mutableStateOf(Prefs.showLyricKana) }

    var mode by remember { mutableStateOf(Prefs.playMode) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    // 拖动进度条时用拖动的值覆盖轮询值，松手后回到真实进度。
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableFloatStateOf(1f) }
    var showQueue by remember { mutableStateOf(false) }

    val pager = rememberPagerState(pageCount = { 2 })

    // 进度轮询（ExoPlayer 无 Flow，用轻量轮询足够）。
    LaunchedEffect(track) {
        while (true) {
            if (!dragging) pos = PlayerHost.positionMs()
            dur = PlayerHost.durationMs().takeIf { it > 0 } ?: (track?.intervalSec?.times(1000L) ?: 0L)
            delay(500)
        }
    }

    // 切歌后的红心状态：直接读全局缓存（LikedList 已翻页取全），
    // 不再每首歌都发一次请求。缓存未就绪时顺带拉一次。
    val likedIds by LikedStore.liked.collectAsState()
    LaunchedEffect(track?.mid) {
        val t = track ?: return@LaunchedEffect
        if (Prefs.credential == null || t.songId <= 0L) {
            PlayerHost.setLiked(false)
            return@LaunchedEffect
        }
        if (!LikedStore.loaded.value) LikedStore.refresh()
        PlayerHost.setLiked(t.songId in likedIds)
    }

    val frac = if (dragging) dragFrac
    else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 顶栏：返回 + 均衡器（都是凸起）。跨两页常驻。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Filled.KeyboardArrowDown, "收起", 46.dp, onBack)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Equalizer, "均衡器", 46.dp) { onOpenEqualizer() }
        }

        // 左右滑动：第 0 页封面，第 1 页歌词。
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            if (page == 0) {
                CoverPage(
                    trackName = track?.name,
                    singer = track?.singer.orEmpty(),
                    coverUrl = track?.coverUrl.orEmpty(),
                    playing = playing,
                    levels = vizLevels,
                )
            } else {
                LyricsView(
                    lyrics = lyrics,
                    positionMs = pos,
                    textSize = lyricSize,
                    showTranslation = showTrans && (lyrics?.hasTranslation == true),
                    showRoman = showRoman && (lyrics?.hasRoman == true),
                    showKana = showKana && (lyrics?.hasKana == true),
                    // 只有当前曲目真有对应数据时才给按钮
                    canToggleTranslation = lyrics?.hasTranslation == true,
                    canToggleRoman = lyrics?.hasRoman == true,
                    canToggleKana = lyrics?.hasKana == true,
                    onToggleTranslation = {
                        val next = !showTrans
                        Prefs.showLyricTranslation = next
                        showTrans = next
                    },
                    onToggleRoman = {
                        val next = !showRoman
                        Prefs.showLyricRoman = next
                        showRoman = next
                    },
                    onToggleKana = {
                        val next = !showKana
                        Prefs.showLyricKana = next
                        showKana = next
                    },
                )
            }
        }

        // 直线进度条（用户要求：常见形式，放在播放按钮上方）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(fmt(pos), color = colors.textTertiary, fontSize = 11.sp)
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .onSizeChanged { barWidth = it.width.toFloat().coerceAtLeast(1f) }
                    .shadeInset(cornerRadius = 8.dp, offset = 3.dp, blur = 4.dp)
                    .pointerInput(dur) {
                        detectTapGestures { offset ->
                            dragging = true
                            dragFrac = (offset.x / barWidth).coerceIn(0f, 1f)
                            PlayerHost.seekTo((dragFrac * dur).toLong())
                            pos = (dragFrac * dur).toLong()
                            dragging = false
                        }
                    }
                    .pointerInput(dur) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                PlayerHost.seekTo((dragFrac * dur).toLong())
                                pos = (dragFrac * dur).toLong()
                            },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            dragFrac = (change.position.x / barWidth).coerceIn(0f, 1f)
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(16.dp)
                        .padding(3.dp)
                        .background(colors.accent, RoundedCornerShape(5.dp)),
                )
            }
            Text(fmt(dur), color = colors.textTertiary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))

        // 底部控制：**一条凸起**，内部全部「平」（凸起上不可再做凸起）
        Row(
            Modifier
                .fillMaxWidth()
                .shadeSurface(cornerRadius = 32.dp, offset = 6.dp, blur = 10.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上一首
            FlatRoundIcon(Icons.Filled.SkipPrevious, "上一首") { PlayerHost.previous() }
            // 播放/暂停（稍大）
            Box(
                Modifier.size(52.dp).flatPressable(cornerRadius = 26.dp) { PlayerHost.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = colors.accent, modifier = Modifier.size(30.dp),
                )
            }
            // 下一首
            FlatRoundIcon(Icons.Filled.SkipNext, "下一首") { PlayerHost.next() }
            // 播放模式（图标循环切换；当前模式用 accent 表示）
            Box(
                Modifier.size(42.dp).flatPressable(cornerRadius = 21.dp) {
                    mode = mode.next()
                    PlayerHost.playMode = mode
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (mode) {
                        PlayMode.ORDER -> Icons.Filled.Repeat
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                    },
                    contentDescription = "播放模式 ${mode.label}",
                    tint = if (mode == PlayMode.ORDER) colors.textSecondary else colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 喜欢
            Box(
                Modifier.size(42.dp)
                    .then(
                        if (liked) Modifier.shadeInset(cornerRadius = 21.dp, offset = 2.dp, blur = 3.dp)
                        else Modifier
                    )
                    .flatPressable(cornerRadius = 21.dp) {
                        val t = track ?: return@flatPressable
                        val target = !liked
                        scope.launch {
                            when (val r = runCatching { SongApi.setLiked(t, target) }
                                .getOrElse { LikeResult.Rejected(-1) }) {
                                is LikeResult.Success -> {
                                    PlayerHost.setLiked(target)
                                    LikedStore.mark(t.songId, target)
                                    toastMain(ctx, if (target) "已加入我喜欢" else "已取消喜欢")
                                }
                                is LikeResult.Unavailable -> toastMain(ctx, r.reason)
                                is LikeResult.Rejected -> toastMain(
                                    ctx,
                                    if (r.code == 1000) "操作太频繁，已被限流，请稍后再试"
                                    else "操作失败（错误码 ${r.code}）",
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "取消喜欢" else "喜欢",
                    tint = if (liked) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            // 队列列表
            Box(
                Modifier.size(42.dp).flatPressable(cornerRadius = 21.dp) { showQueue = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "歌曲列表",
                    tint = colors.textSecondary, modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 队列弹窗
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
}

/** 队列弹窗：当前播放队列，点选跳播。 */
@Composable
private fun QueueDialog(tracks: List<com.neumusic.player.data.Track>, currentIndex: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = com.neumusic.player.shade.LocalShadeColors.current
    com.neumusic.player.ui.common.ShadeDialog(title = "播放队列（${tracks.size} 首）", onDismiss = onDismiss) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
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

/** 凸起容器内的平面圆图标钮。 */
@Composable
private fun FlatRoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Box(
        Modifier.size(42.dp).flatPressable(cornerRadius = 21.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/**
 * 封面页：凸起圆盘（外圈频谱环）+ 曲名/歌手。
 * 进度条已移到公共控制区上方（直线形式，用户要求）。
 */
@Composable
private fun CoverPage(
    trackName: String?,
    singer: String,
    coverUrl: String,
    playing: Boolean,
    levels: FloatArray,
) {
    val colors = LocalShadeColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        CoverDisc(
            coverUrl = coverUrl,
            frac = 0f,
            playing = playing,
            levels = levels,
            onDragStart = {},
            onDrag = {},
            onDragEnd = {},
        )
        Spacer(Modifier.height(28.dp))
        Text(
            trackName ?: "未在播放",
            color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            singer,
            color = colors.textSecondary, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** 圆盘直径、频谱环参数。 */
private val DISC_SIZE = 264.dp
private val RING_WIDTH = 6.dp
/** 环要离盘有距离（用户反馈：太贴、太粗）。 */
private val RING_GAP = 16.dp
/** 频谱柱最大伸出长度。 */
private val RING_MAX_LEN = 26.dp

/**
 * 封面圆盘：**凸起底盘**托着封面（封面上不再有凸起），外圈套一圈**进度环**，
 * 播放时圆盘缓慢自转。
 *
 * 交互：环上任意位置可按下拖动 seek —— 圆环是"旋转了 90° 的进度条"，
 * 按角度换算比例（从 12 点方向顺时针）。
 */
@Composable
private fun CoverDisc(
    coverUrl: String,
    frac: Float,
    playing: Boolean,
    levels: FloatArray,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalShadeColors.current
    val animatedFrac by animateFloatAsState(frac, tween(160), label = "ringFrac")
    val ringMaxLen = RING_MAX_LEN

    // 自转：播放时持续旋转，暂停时停在当前角度。
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(24000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            rotation.stop()
        }
    }

    Box(
        Modifier.size(DISC_SIZE + (RING_GAP + RING_WIDTH) * 2),
        contentAlignment = Alignment.Center,
    ) {
        // 频谱环：围绕圆盘的径向电平柱（播放条可视化的详情页形态）。
        Canvas(Modifier.fillMaxSize()) {
            val gap = RING_GAP.toPx()
            val discRadius = DISC_SIZE.toPx() / 2f
            val innerR = discRadius + gap
            val maxLen = ringMaxLen.toPx()
            val barW = 2.5.dp.toPx()
            val n = levels.size
            for (i in 0 until n) {
                val level = levels[i].coerceIn(0f, 1f)
                val shown = if (playing) level else level * 0.15f
                val angle = -90f + 360f * i / n
                // 从盘边缘向外伸出的径向柱
                drawLine(
                    color = colors.accent.copy(alpha = 0.25f + 0.75f * shown),
                    start = Offset(
                        size.width / 2f + innerR * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat(),
                        size.height / 2f + innerR * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat(),
                    ),
                    end = Offset(
                        size.width / 2f + (innerR + maxLen * shown.coerceAtLeast(0.06f)) *
                            kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat(),
                        size.height / 2f + (innerR + maxLen * shown.coerceAtLeast(0.06f)) *
                            kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat(),
                    ),
                    strokeWidth = barW,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 凸起底盘 + 圆封面（裁成圆形）；随播放缓慢自转。
        Box(
            Modifier
                .size(DISC_SIZE)
                .graphicsLayer { rotationZ = rotation.value }
                .shadeSurface(cornerRadius = DISC_SIZE / 2, offset = 6.dp, blur = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isEmpty()) {
                Box(
                    Modifier.size(DISC_SIZE - 16.dp).clip(CircleShape).background(colors.background),
                    contentAlignment = Alignment.Center,
                ) { CoverPlaceholder() }
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(DISC_SIZE - 16.dp)
                        .clip(CircleShape)
                        .background(colors.background),
                )
            }
        }

        // 环上的 seek 手势层。
        //
        // 只有落在**环带**上的触摸才被接管（见 RingSeekArea）；落在圆盘内部的触摸
        // 一律放行，让 HorizontalPager 能正常左右滑动切页
        // —— 早期版本让整块圆盘都接管拖动，结果在盘上左右滑切不了页（实测复现）。
        RingSeekArea(
            discDiameterPx = with(LocalDensity.current) { DISC_SIZE.toPx() },
            ringWidthPx = with(LocalDensity.current) { RING_WIDTH.toPx() },
            ringGapPx = with(LocalDensity.current) { RING_GAP.toPx() },
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
}

/**
 * 圆环上的 seek 手势：仅在「环带」范围内接管触摸，其余放行给外层的分页器。
 * 按角度换算进度（12 点方向为 0、顺时针增长），支持按住拖动连续改变。
 */
@Composable
private fun BoxScope.RingSeekArea(
    discDiameterPx: Float,
    ringWidthPx: Float,
    ringGapPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(discDiameterPx, ringWidthPx, ringGapPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val full = size.width.toFloat()
                    val center = Offset(full / 2f, full / 2f)
                    val r = (down.position - center).getDistance()
                    // 环带取**窄环**：以环的描边中心为基准，向内外各留一点容差。
                    // 内边界不要贪大 —— 否则圆盘内部也会被接管，分页器就滑不动了（实测踩过）。
                    // 内边界必须落在**圆盘视觉半径之外**：手势区只在环带（盘外那一圈），
                    // 圆盘内部一律放行，否则在盘上左右滑切不了页（实测把内边界画在盘内就会这样）。
                    // 盘内一律放行；只在环带（盘外那一圈）接管。
                    val discRadius = discDiameterPx / 2f
                    if (r < discRadius + ringGapPx * 0.35f) return@awaitEachGesture
                    // 命中环带：接管这次手势
                    down.consume()
                    onDragStart()
                    onDrag(angleFrac(down.position, full))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            onDragEnd()
                            break
                        }
                        onDrag(angleFrac(change.position, full))
                        change.consume()
                    }
                }
            },
    )
}

/**
 * 把触摸点换算成进度比例：以圆心为原点、**12 点方向为 0**、顺时针增长。
 * 结果是 0..1。
 */
private fun angleFrac(offset: Offset, sizePx: Float): Float {
    val cx = sizePx / 2f
    val dx = offset.x - cx
    val dy = offset.y - cx
    // atan2(dx, -dy)：12 点方向为 0，顺时针为正。
    var deg = Math.toDegrees(kotlin.math.atan2(dx, -dy).toDouble()).toFloat()
    if (deg < 0f) deg += 360f
    return (deg / 360f).coerceIn(0f, 1f)
}


/** 圆形图标按钮（凸起）。 */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        Modifier
            .size(size)
            .shadePressable(cornerRadius = size / 2, offset = 5.dp, blur = 8.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = colors.accent, modifier = Modifier.size(size * 0.42f))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
