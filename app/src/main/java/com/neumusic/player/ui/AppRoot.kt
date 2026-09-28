package com.neumusic.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.RadioApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.loadUrl
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.home.AlbumsScreen
import com.neumusic.player.ui.home.HomeScreen
import com.neumusic.player.ui.home.PlaylistsScreen
import com.neumusic.player.ui.home.TrackListScreen
import com.neumusic.player.ui.player.EqualizerScreen
import com.neumusic.player.ui.player.PlayerScreen
import com.neumusic.player.ui.search.SearchScreen
import com.neumusic.player.ui.settings.SettingsScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * 页面栈根节点（无导航条）。
 *
 * 页面栈是 [StackEntry] 的列表：**所有返回入口都只弹出栈顶**，所以「从哪来就回哪去」
 * 对任何进入方式都成立（同一页面可能有多个来源）。栈底是主页，栈只剩主页时
 * 把返回交还系统（退出 App）。
 *
 * 播放页是全屏覆盖层而非普通页面：它压在当前页面之上，`translationY` 由是否处于
 * 栈顶驱动；均衡器等页面则可以压在播放页之上，返回时播放页原样回来。
 *
 * 带来源卡片的页面（主页卡片 → 二级页）播放**容器变换**动画：页面从卡片的位置和
 * 尺寸展开到全屏（卡片封面在容器里淡出、页面内容淡入），返回时反向收缩回卡片。
 */
@Composable
fun AppRoot() {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf(StackEntry(Nav.Home)) }
    var morphBack by remember { mutableStateOf(false) }

    fun open(req: NavRequest) {
        stack.add(StackEntry(req.nav, req.origin, req.hero))
    }

    // 弹出栈顶。栈底（主页）不弹——交还系统处理（退出 App）。
    // 带来源卡片的页面先做收缩动画，动画结束再真正弹栈。
    fun back() {
        if (stack.size <= 1) return
        val top = stack.last()
        if (top.origin != null && !morphBack) {
            if (top.settled) {
                stack[stack.lastIndex] = top.copy(settled = false)
            }
            morphBack = true
            return
        }
        stack.removeAt(stack.lastIndex)
    }

    // 让 PlayerHost 能取链（播放器不直接依赖网络层）；自动切歌失败也弹给人看。
    LaunchedEffect(Unit) {
        PlayerHost.resolveUrl = { track -> loadUrl(track) }
        PlayerHost.onError = { msg -> toastMain(ctx, msg) }
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { back() }

    val top = stack.last()
    val playerOpen = top.nav == Nav.Player
    // 底层渲染哪个页面：播放页压顶时取最后一个非 Player；
    // 该页面正在做容器变换（未落定）时渲染它**下面**的那层——变换覆盖层自己渲染它。
    val lastNonPlayer = stack.lastOrNull { it.nav != Nav.Player }
    val underEntry = when {
        lastNonPlayer == null -> stack.first()
        lastNonPlayer.origin != null && !lastNonPlayer.settled ->
            stack.getOrNull(stack.indexOf(lastNonPlayer) - 1) ?: lastNonPlayer
        else -> lastNonPlayer
    }
    // 容器变换覆盖层的宿主：栈顶那个带来源且未落定的条目（不含播放页）。
    val morphEntry = stack.lastOrNull { it.nav != Nav.Player && it.origin != null && !it.settled }

    // 0 = 播放页完全在屏幕下方（隐藏）；1 = 完全覆盖。
    val slide by animateFloatAsState(if (playerOpen) 1f else 0f, tween(320), label = "playerSlide")
    val screenH = with(LocalDensity.current) { 900.dp.toPx() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // ── 底层：当前页面（切页做轻淡入淡出；容器变换的页面不在这一层渲染）──
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = underEntry.nav,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(140)))
                },
                label = "pageSwap",
            ) { nav ->
                PageContent(
                    nav = nav,
                    onOpen = ::open,
                    onBack = ::back,
                )
            }
        }

        // ── 底部播放栏：随播放页展开而上移淡出，制造「被带出去」的连续感 ──
        val current by PlayerHost.current.collectAsState()
        if (current != null) {
            MiniPlayerBar(
                onOpen = { if (stack.last().nav != Nav.Player) open(NavRequest(Nav.Player)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = -screenH * 0.42f * slide
                        alpha = 1f - slide
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // ── 容器变换覆盖层：页面从来源卡片展开/收缩 ──
        morphEntry?.let { entry ->
            MorphPage(
                entry = entry,
                reverse = morphBack,
                onSettled = {
                    val i = stack.indexOf(entry)
                    if (i >= 0) stack[i] = entry.copy(settled = true)
                },
                onShrunk = {
                    stack.remove(entry)
                    morphBack = false
                },
            )
        }

        // ── 播放页覆盖层：整块从下方上移 ──
        if (slide > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .graphicsLayer {
                        translationY = (1f - slide) * screenH
                        alpha = (slide * 1.6f).coerceAtMost(1f)
                    },
            ) {
                PlayerScreen(
                    onBack = { back() },
                    onOpenEqualizer = { open(NavRequest(Nav.Equalizer)) },
                )
            }
        }
    }
}

/** 页面栈里普通页面的渲染（底层与容器变换覆盖层共用）。 */
@Composable
private fun PageContent(
    nav: Nav,
    onOpen: (NavRequest) -> Unit,
    onBack: () -> Unit,
) {
    when (nav) {
        Nav.Home -> HomeScreen(
            onOpenSearch = { onOpen(NavRequest(Nav.Search)) },
            onOpenSettings = { onOpen(NavRequest(Nav.Settings)) },
            onOpenDest = onOpen,
        )
        Nav.Search -> SearchScreen(
            onBack = onBack,
            onOpenAlbum = { onOpen(NavRequest(Nav.AlbumDetail(it.mid, it.name, it.songnum))) },
        )
        Nav.Settings -> SettingsScreen(onBack = onBack)
        Nav.Playlists -> PlaylistsScreen(
            onBack = onBack,
            onOpen = { onOpen(NavRequest(Nav.PlaylistDetail(it.tid, it.name, it.songnum))) },
        )
        Nav.Albums -> AlbumsScreen(
            onBack = onBack,
            onOpen = { onOpen(NavRequest(Nav.AlbumDetail(it.mid, it.name, it.songnum))) },
        )
        Nav.Liked -> key("liked") { TrackListScreen(
            title = "我喜欢", onBack = onBack,
            cacheKey = "liked",
            loadPage = { off, num -> PlaylistApi.likedPage(off, num) },
            onOpenAlbum = { t -> onOpen(NavRequest(Nav.AlbumDetail(t.albumMid, t.albumName))) },
        ) }
        Nav.Equalizer -> EqualizerScreen(onBack = onBack)
        is Nav.PlaylistDetail -> key("playlist:${nav.tid}") { TrackListScreen(
            title = nav.name, onBack = onBack,
            cacheKey = "playlist:${nav.tid}",
            knownTotal = nav.songnum,
            loadPage = { off, num -> PlaylistApi.playlistPage(nav.tid, off, num) },
            onOpenAlbum = { t -> onOpen(NavRequest(Nav.AlbumDetail(t.albumMid, t.albumName))) },
        ) }
        is Nav.AlbumDetail -> key("album:${nav.mid}") { TrackListScreen(
            title = nav.name, onBack = onBack,
            cacheKey = "album:${nav.mid}",
            knownTotal = nav.songnum,
            loadPage = { off, num -> PlaylistApi.albumPage(nav.mid, off, num) },
            onOpenAlbum = { t -> onOpen(NavRequest(Nav.AlbumDetail(t.albumMid, t.albumName))) },
        ) }
        is Nav.RadioDetail -> key("radio:${nav.id}") { TrackListScreen(
            title = nav.title, onBack = onBack,
            cacheKey = "radio:${nav.id}",
            // 电台没有总数概念，一次取一大页即可。
            loadPage = { off, num ->
                PlaylistApi.Page(
                    songs = RadioApi.tracks(nav.id, num = if (off == 0) 200 else 0) ?: emptyList(),
                    total = null,
                )
            },
            onOpenAlbum = { t -> onOpen(NavRequest(Nav.AlbumDetail(t.albumMid, t.albumName))) },
        ) }
        // 播放页只作为覆盖层出现，正常情况下不会走到这里；兜底不渲染。
        Nav.Player -> Unit
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * 容器变换：页面从来源卡片 [StackEntry.origin] 展开到全屏。
 *
 * 实现：整屏 Box 用 `clipRect` 挖出「当前容器矩形」（卡片矩形 → 全屏插值），
 * 页面内容全尺寸铺在下面随进度淡入；容器矩形里先铺背景与卡片封面（Hero），
 * 封面随展开淡出——观感就是「卡片长大变成页面」。返回时同一条曲线倒放。
 */
@Composable
private fun MorphPage(
    entry: StackEntry,
    reverse: Boolean,
    onSettled: () -> Unit,
    onShrunk: () -> Unit,
) {
    val colors = LocalShadeColors.current
    val origin = entry.origin ?: return
    val progress = remember { Animatable(if (reverse) 1f else 0f) }

    LaunchedEffect(reverse) {
        if (!reverse) {
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            onSettled()
        } else {
            progress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            onShrunk()
        }
    }

    val t = progress.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullW = with(density) { maxWidth.toPx() }
        val fullH = with(density) { maxHeight.toPx() }
        val left = lerp(origin.left, 0f, t)
        val top = lerp(origin.top, 0f, t)
        val right = lerp(origin.right, fullW, t)
        val bottom = lerp(origin.bottom, fullH, t)
        val corner = lerp(18f, 0f, t)
        val contentAlpha = ((t - 0.35f) / 0.5f).coerceIn(0f, 1f)
        val heroAlpha = (1f - t * 1.8f).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .zIndex(3f)
                .drawWithContent {
                    // 容器矩形外不画：页面内容只在这个不断变大的「窗口」里可见
                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            // 卡片母体：背景圆角块 + 封面/爱心，展开过程里淡出
            if (heroAlpha > 0f) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = left
                            translationY = top
                            alpha = heroAlpha
                        }
                        .pxSize(density, right - left, bottom - top)
                        .clip(RoundedCornerShape(with(density) { corner.toDp() }))
                        .background(colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    val hero = entry.hero
                    when (hero) {
                        is Hero.Image -> AsyncImage(
                            model = hero.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .pxSize(density, right - left - 12f, bottom - top - 12f)
                                .clip(RoundedCornerShape(with(density) { (corner * 0.7f).toDp() })),
                        )
                        is Hero.Heart -> Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(44.dp),
                        )
                        null -> Unit
                    }
                }
            }
            // 页面内容：全尺寸铺放，随窗口展开淡入
            Box(Modifier.graphicsLayer { alpha = contentAlpha }) {
                PageContent(
                    nav = entry.nav,
                    onOpen = { },   // 变换进行中不响应二级跳转
                    onBack = { },
                )
            }
        }
    }
}

/** 以像素设定子项尺寸（容器矩形跟随动画逐帧变化）。 */
private fun Modifier.pxSize(density: androidx.compose.ui.unit.Density, wPx: Float, hPx: Float): Modifier =
    this.requiredSize(with(density) { wPx.coerceAtLeast(0f).toDp() }, with(density) { hPx.coerceAtLeast(0f).toDp() })

/** 底部播放栏：点封面/信息带出播放页。开启可视化时显示实时电平胶囊。 */
@Composable
private fun MiniPlayerBar(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShadeColors.current
    val track by PlayerHost.current.collectAsState()
    val playing by PlayerHost.isPlaying.collectAsState()
    val vizOn by Prefs.barVizFlow.collectAsState()
    // FFT（Visualizer）优先；模拟器等不支持时回退到 PCM 分段电平
    val usingFft by VizHost.usingFft.collectAsState()
    val fftLevels by VizHost.levels.collectAsState()
    val pcmLevels by PlayerHost.vizProcessor.levels.collectAsState()
    val levels = if (usingFft) fftLevels else pcmLevels
    val t = track ?: return
    Row(
        modifier
            .fillMaxWidth()
            .shadeSurface(cornerRadius = 26.dp, offset = 6.dp, blur = 10.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 整条播放栏本身是凸起面，内部一律「平」按压 —— 不在凸起上再做凸起。
        Box(Modifier.flatPressable(cornerRadius = 20.dp, onClick = onOpen)) {
            AlbumArt(t.coverUrl, 44.dp, corner = 12.dp)
        }
        Column(
            Modifier.weight(1f)
                .flatPressable(cornerRadius = 14.dp, onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                t.name, fontSize = 14.sp, color = colors.textPrimary,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(t.singer, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
        }
        // 音频可视化：小凹陷"电平屏"（设置里开关；暂停/关闭时不显示）
        if (vizOn) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .shadeInset(cornerRadius = 8.dp, offset = 2.dp, blur = 3.dp)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = 5
                    val gap = 3.dp.toPx()
                    val barW = (size.width - gap * (n - 1)) / n
                    for (i in 0 until n) {
                        val level = levels.getOrElse(i * VizHost.BARS / n) { 0f }
                        val shown = if (playing) level else level * 0.12f
                        val h = (size.height * (0.12f + 0.88f * shown)).coerceAtLeast(2.dp.toPx())
                        drawRoundRect(
                            color = colors.accent,
                            topLeft = Offset(i * (barW + gap), size.height - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 3f),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.size(40.dp).flatPressable(cornerRadius = 20.dp) { PlayerHost.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = colors.accent, modifier = Modifier.size(20.dp),
            )
        }
        Box(
            Modifier.size(40.dp).flatPressable(cornerRadius = 20.dp) { PlayerHost.next() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SkipNext, "下一首", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
