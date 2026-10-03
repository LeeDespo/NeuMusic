package com.neumusic.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.PlayMode
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.SongApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.ui.common.QueueDialog
import com.neumusic.player.ui.common.VIZ_BARS
import com.neumusic.player.ui.common.toastMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 播放页显示模式（0 纯封面 / 1 封面+歌词 / 2 纯歌词）。跨「关闭再打开播放页」保持——覆盖层会销毁重组，记忆放在单例里。 */
object PlayerUi {
    var displayMode by androidx.compose.runtime.mutableIntStateOf(0)
}

/** 播放页（覆盖主页）。[onBack] 由外层驱动下滑动画。上下滑切换 纯封面/封面+歌词/纯歌词 三模式。 */
@Composable
fun PlayerScreen(onBack: () -> Unit, onOpenEqualizer: () -> Unit = {}) {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val track by PlayerHost.current.collectAsState()
    val playing by PlayerHost.isPlaying.collectAsState()
    val liked by PlayerHost.liked.collectAsState()
    val lyrics by PlayerHost.lyrics.collectAsState()

    // 可视化电平改由 CoverDisc 内部订阅（2026-10-01 性能审计）：电平流每次音频块都发射
    // （约 20-50ms 一次），若在页顶层 collect 会带动整页高频重组（歌词/控制区全部陪跑）。
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

    // 进度轮询（ExoPlayer 无 Flow，用轻量轮询足够）。
    LaunchedEffect(track) {
        while (true) {
            if (!dragging) pos = PlayerHost.positionMs()
            dur = PlayerHost.durationMs().takeIf { it > 0 } ?: (track?.intervalSec?.times(1000L) ?: 0L)
            delay(500)
        }
    }

    // ── 黑胶转盘状态机（2026-10-03 移植自 ~/Documents/VinylLab）──
    // 只跟随播放、不驱动播放：PlayerHost 照旧负责换歌，转盘把「抬针→回臂→减速→换片」
    // 演出来（详见 VinylTurntable.kt 的说明）。三个信号都在这里喂。
    val turntable = remember { VinylTurntableState(scope) }
    LaunchedEffect(Unit) { turntable.start() }
    LaunchedEffect(playing) { turntable.setPlaying(playing) }
    LaunchedEffect(track?.mid) { turntable.onTrackChanged(track?.coverUrl.orEmpty()) }
    // v2 §5：唱头半径与播放进度**线性绑定**，500ms 轮询对进度条够用、对唱头不够
    // （半径全程只走 0.16 D，500ms 一步 ≈ 9 px 的一顿）。给转盘挂一个**每帧采样**的位置源，
    // 只驱动唱臂、不进任何 Compose 状态 → 不引起重组。
    LaunchedEffect(Unit) {
        turntable.positionSource = { PlayerHost.positionMs() }
        turntable.durationSource = { PlayerHost.durationMs() }
    }
    // 自动换片的提前量：真实播放器不通知「还剩几秒」，用「位置 + 时长」自己算。
    // 500ms 轮询 → 触发最多晚 0.5s（规格 §5.5 接受这个抖动）。
    LaunchedEffect(pos, dur, playing) { turntable.onPosition(pos, dur) }

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

        // ── 三模式容器（用户 2026-09-30 规格）：纯封面(0) / 封面+歌词(1) / 纯歌词(2)。
        // 上滑前进、下滑后退，**一次手势最多走一级**（纯封面↔纯歌词不直连）。
        // 进度 modeT 是连续值：封面随它上移/缩小/淡出，歌词面板从底部升起、长高到全屏；
        // 逐帧值只在 graphicsLayer/layout lambda 里读（derivedStateOf 门控的布尔才会重组）。
        // 纯歌词模式下竖直拖动由歌词列表正常消费（浏览歌词）；到顶继续下拉由 LyricsView
        // 的 nestedScroll 连接回传收起（2→1）。──
        val modeT = remember { Animatable(PlayerUi.displayMode.toFloat()) }
        var dragBase by remember { mutableFloatStateOf(0f) }
        val fullLyrics by remember { derivedStateOf { modeT.value >= 1.5f } }
        fun settleMode() {
            scope.launch {
                val m = modeT.value.roundToInt().coerceIn(0, 2)
                PlayerUi.displayMode = m
                modeT.animateTo(m.toFloat(), tween(240, easing = FastOutSlowInEasing))
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragBase = modeT.value },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            scope.launch {
                                val step = (size.height * 0.42f).coerceAtLeast(1f)
                                modeT.snapTo(
                                    (modeT.value - dy / step)
                                        .coerceIn(dragBase - 1f, dragBase + 1f)
                                        .coerceIn(0f, 2f),
                                )
                            }
                        },
                        onDragEnd = { settleMode() },
                        onDragCancel = { settleMode() },
                    )
                },
        ) {
            // 封面（含曲名/歌手）：随进度上移、缩小；1→2 段淡出消失
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = modeT.value
                        val step1 = min(t, 1f)
                        val step2 = max(0f, t - 1f)
                        val miniH = 180.dp.toPx()
                        translationY = -(miniH / 2f) * step1 - size.height * 0.22f * step2
                        val s = 1f - 0.22f * step1 - 0.1f * step2
                        scaleX = s
                        scaleY = s
                        alpha = (1f - step2 * 1.6f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CoverPage(
                    trackName = track?.name,
                    singer = track?.singer.orEmpty(),
                    coverUrl = track?.coverUrl.orEmpty(),
                    playing = playing,
                    turntable = turntable,
                )
            }
            // 歌词面板：0→1 从底部升起并长到三行迷你（带翻译/注音位）；1→2 长到全屏
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
                userScrollEnabled = fullLyrics,
                onPullDownCollapse = if (fullLyrics) {
                    {
                        PlayerUi.displayMode = 1
                        scope.launch { modeT.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
                    }
                } else null,
                showToggles = fullLyrics,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val t = modeT.value
                        val mini = 180.dp.roundToPx()
                        val full = constraints.maxHeight
                        val grow = (t - 1f).coerceIn(0f, 1f)
                        val h = (mini + ((full - mini) * grow).roundToInt())
                            .times(min(t, 1f)).roundToInt()
                            .coerceIn(1, full)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = h, maxHeight = h),
                        )
                        layout(placeable.width, constraints.maxHeight) {
                            // 面板底边始终贴容器底：升起=高度变大，顶边随之上升
                            placeable.placeRelative(0, constraints.maxHeight - h)
                        }
                    }
                    .graphicsLayer { alpha = (modeT.value * 1.6f).coerceIn(0f, 1f) },
            )
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

/** 队列弹窗已提到 common（[com.neumusic.player.ui.common.QueueDialog]），播放栏与播放页共用。 */

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
 * 封面页：黑胶模式 = [VinylTurntable]（唱片 + 唱臂）；普通模式 = 凸起圆盘 + 外圈频谱环。
 * 曲名/歌手在两旁。
 */
@Composable
private fun CoverPage(
    trackName: String?,
    singer: String,
    coverUrl: String,
    playing: Boolean,
    turntable: VinylTurntableState,
) {
    val colors = LocalShadeColors.current
    val vinyl by Prefs.vinylModeFlow.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        if (vinyl) {
            // 黑胶模式：舞台宽 1.54 D（盘居中、右侧留给唱臂扫掠），按可用宽度缩放。
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val discD = minOf(
                    maxWidth / VinylGeo.STAGE_W,
                    VinylGeo.DISC_D.dp,
                )
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VinylTurntable(state = turntable, discD = discD) { url, alpha, dxFrac ->
                        CoverImage(url = url, alpha = alpha, dxFrac = dxFrac, discD = discD)
                    }
                    // v2 §5：LED 胶囊可视化——唱片与歌名之间、居中、长度 = 唱片直径，
                    // 只在黑胶模式。（订阅在 VinylLedBar 内部，电平流 ~20–50ms 一帧，
                    // 不能放到页面顶层。）
                    Spacer(Modifier.height(14.dp))
                    VinylLedBar(span = discD)
                }
            }
        } else {
            CoverDisc(
                coverUrl = coverUrl,
                frac = 0f,
                playing = playing,
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
            )
        }
        Spacer(Modifier.height(if (vinyl) 18.dp else 28.dp))
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

/**
 * 片心封面（黑胶模式的标签）：真实封面图 / 首字母占位，换片时做滑动 + 淡入淡出。
 * 尺寸固定为 `discD × VinylGeo.COVER_RATIO`（= 0.42 D，与实验室同一比例）。
 */
@Composable
private fun CoverImage(url: String, alpha: Float, dxFrac: Float, discD: Dp) {
    val colors = LocalShadeColors.current
    val size = discD * VinylGeo.COVER_RATIO
    val slidePx = with(LocalDensity.current) { (discD.toPx() * dxFrac) }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                this.alpha = alpha
                translationX = slidePx
            }
            .clip(CircleShape)
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isEmpty()) {
            CoverPlaceholder()
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalShadeColors.current
    val animatedFrac by animateFloatAsState(frac, tween(160), label = "ringFrac")
    val ringMaxLen = RING_MAX_LEN
    // 电平流在此订阅（而不是页面顶层）：高频重组被隔离在圆盘子树内
    val usingFft by VizHost.usingFft.collectAsState()
    val fftLevels by VizHost.levels.collectAsState()
    val pcmLevels by PlayerHost.vizProcessor.levels.collectAsState()
    val levels = if (usingFft) fftLevels else pcmLevels

    // 自转已随黑胶模式一并搬到 VinylTurntable（2026-10-03 移植）：
    // CoverDisc 现在只画**非黑胶**的普通圆盘 —— 用户规格「关闭黑胶就是不转的普通圆盘」，
    // 所以这里不再需要 rotation/自转动画（原来的 `Animatable` 循环已删除）。

    Box(
        Modifier.size(DISC_SIZE + (RING_GAP + RING_WIDTH) * 2),
        contentAlignment = Alignment.Center,
    ) {
        // 频谱环：围绕圆盘的径向电平柱（与播放栏封面的可视化同款、同频段数）。
        // 电平源只有 VizHost.BARS(16) 段，这里按 VIZ_BARS(40) 根铺开（降采样映射）。
        Canvas(Modifier.fillMaxSize()) {
            val gap = RING_GAP.toPx()
            val discRadius = DISC_SIZE.toPx() / 2f
            val innerR = discRadius + gap
            val maxLen = ringMaxLen.toPx()
            val barW = 2.5.dp.toPx()
            val n = VIZ_BARS
            for (i in 0 until n) {
                val level = levels.getOrElse(i * levels.size / n) { 0f }.coerceIn(0f, 1f)
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

        // 凸起底盘：**保持静止**——阴影不能跟着唱片转（用户规格），旋转只发生在盘面内容上。
        // 黑胶模式已改由 CoverPage → VinylTurntable 承担（转盘自带底盘/沟槽/唱臂），
        // 这里只负责**非黑胶**的普通圆盘（频谱环也只在普通模式画，见规格 §5.8）。
        Box(
            Modifier
                .size(DISC_SIZE)
                .shadeSurface(cornerRadius = DISC_SIZE / 2, offset = 6.dp, blur = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 盘面内容（封面）：普通模式不转（黑胶模式的自转在转盘组件里）
            val coverDiameter = DISC_SIZE - 16.dp
            Box(
                Modifier
                    .size(coverDiameter),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isEmpty()) {
                    Box(
                        Modifier.size(coverDiameter).clip(CircleShape).background(colors.background),
                        contentAlignment = Alignment.Center,
                    ) { CoverPlaceholder() }
                } else {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(coverDiameter)
                            .clip(CircleShape)
                            .background(colors.background),
                    )
                }
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
