package com.neumusic.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.neumusic.player.shade.LocalShadeShadowAlpha
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 页面栈根节点（无导航条）。
 *
 * 页面栈是 [StackEntry] 的列表：**所有返回入口都只弹出栈顶**，所以「从哪来就回哪去」
 * 对任何进入方式都成立（同一页面可能有多个来源）。栈底是主页，栈只剩主页时
 * 把返回交还系统（退出 App）。
 *
 * 播放页是全屏覆盖层而非普通页面（zIndex 高于一切页面内容），`translationY` 由是否
 * 处于栈顶驱动。均衡器压在播放页之上：**打开时播放页整体下滑露出音效页，返回时
 * 播放页重新从底部升起盖住音效页，动画结束后才把音效页真正弹出栈**（早前立即弹栈，
 * 底下的页面闪现一下再重放升起动画——实测踩过）。
 *
 * 一二级页面切换是**整体场景缩放（摄像机推拉）**，操作对象是一级页面的根容器：
 * - 进入：整个一级场景（背景+列表+所有卡片）做 scale + translation——uniform 缩放
 *   放大、相机中心从屏幕中心滑向卡片中心（卡片只是焦点），卡片区域最终铺满屏幕；
 *   放大完成后二级页面才整屏淡入（末段 crossfade，无跳变）。
 * - 返回：同一条 tween(400, FastOutSlowInEasing) 精确倒放——二级页面先淡出，
 *   场景再回缩，露出原样的一级页面。
 * - **性能关键**：推拉进度只能被 graphicsLayer 的 lambda 读取（图层属性逐帧更新，
 *   不触发重组）；在组合期读 Animatable.value 会让 AppRoot 每帧整树重组（实测卡死）。
 *   真实页面内容推迟到 t>0.7 才组合，起播帧保持轻。
 */
@Composable
fun AppRoot() {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf(StackEntry(Nav.Home)) }
    var morphBack by remember { mutableStateOf(false) }
    var playerRising by remember { mutableStateOf(false) }
    var eqPopPending by remember { mutableStateOf(false) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }

    fun open(req: NavRequest) {
        if (morphBack || eqPopPending) return   // 转场进行中不接新入口（快速连点防护）
        stack.add(StackEntry(req.nav, req.origin))
    }


    // 弹出栈顶。栈底（主页）不弹——交还系统处理（退出 App）。
    fun back() {
        if (stack.size <= 1 || morphBack || eqPopPending) return
        val top = stack.last()
        val below = stack.getOrNull(stack.lastIndex - 1)
        if (top.nav == Nav.Equalizer && below?.nav == Nav.Player) {
            // 音效页返回：先让播放页从底部升起盖住音效页，动画结束再真正弹栈。
            eqPopPending = true
            playerRising = true
            scope.launch {
                delay(360)   // ≈ slide 的 tween(320)
                stack.removeAt(stack.lastIndex)
                playerRising = false
                eqPopPending = false
            }
            return
        }
        if (top.origin != null) {
            morphBack = true   // 触发推拉倒放，动画结束由 LaunchedEffect 弹栈
            return
        }
        stack.removeAt(stack.lastIndex)
    }

    // 记住回调实例：否则每次重组都是新函数对象，HomeScreen 等整页永远无法跳过重组
    val openCb = remember { { req: NavRequest -> open(req) } }
    val backCb = remember { { back() } }

    // 让 PlayerHost 能取链（播放器不直接依赖网络层）；自动切歌失败也弹给人看。
    LaunchedEffect(Unit) {
        PlayerHost.resolveUrl = { track -> loadUrl(track) }
        PlayerHost.onError = { msg -> toastMain(ctx, msg) }
    }

    // ── 预热（回答"能不能异步把界面渲染好"）——二级列表页第一次组合要加载类+JIT，
    // 冷启动 140ms 左右全落在用户第一次点进列表的那一帧上。主页稳定后把它在
    // 幕后（alpha≈0、被场景盖住、不可交互）组合并光栅一遍，成本摊进空闲期；
    // 数据也在此时进了 TrackListCache。只此一次，之后卸载。
    var warmupDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2500)
        warmupDone = true
        delay(3500)
        warmupDone = false
    }
    if (warmupDone && stack.size == 1) {
        Box(Modifier.fillMaxSize().zIndex(-1f).graphicsLayer { alpha = 0.01f }) {
            TrackListScreen(
                title = "warmup",
                onBack = {},
                cacheKey = "__warmup__",
                loadPage = { _, _ ->
                    PlaylistApi.Page(
                        songs = listOf(
                            com.neumusic.player.data.Track(
                                mid = "warmup", name = "warmup", mediaMid = "",
                                singer = "", albumName = "", albumMid = "", intervalSec = 0,
                                isVip = false,
                            ),
                        ),
                        total = 1,
                    )
                },
            )
        }
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { back() }

    val top = stack.last()
    val playerOpen = top.nav == Nav.Player
    // 0 = 播放页完全在屏幕下方（隐藏）；1 = 完全覆盖。
    val slide by animateFloatAsState(if (playerOpen || playerRising) 1f else 0f, tween(320), label = "playerSlide")
    val screenH = with(LocalDensity.current) { 900.dp.toPx() }

    // 摄像机推拉的宿主：最顶上那个带来源的页面（其上最多只有播放页覆盖层，
    // 否则播放页盖在它上面时推拉层会跟着显示）。
    val lastOriginIdx = stack.indexOfLast { it.origin != null && it.nav != Nav.Player }
    val morphEntry = if (lastOriginIdx >= 0 && stack.drop(lastOriginIdx + 1).all { it.nav == Nav.Player }) {
        stack[lastOriginIdx]
    } else null

    // 推拉进度 0..1：按条目记忆（每个条目只播一次进场）；返回时从当前值倒放。
    val zoomAnimatable = remember(morphEntry?.nav) {
        Animatable(if (morphEntry?.settled == true) 1f else 0f)
    }
    LaunchedEffect(morphEntry?.nav, morphEntry?.settled == true, morphBack) {
        when {
            morphEntry == null -> Unit
            morphBack -> {
                zoomAnimatable.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                stack.remove(morphEntry)
                morphBack = false
            }
            morphEntry.settled -> zoomAnimatable.snapTo(1f)
            else -> {
                zoomAnimatable.snapTo(0f)
                zoomAnimatable.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                val i = stack.indexOf(morphEntry)
                if (i >= 0) stack[i] = morphEntry.copy(settled = true)
            }
        }
    }
    // 落定后场景按原样渲染（被二级页面完全盖住，省掉整场景的放大绘制）；
    // 该布尔只在进场完成/返回开始时翻转一次——绝不能在组合期读 Animatable.value。
    val zoomSteady = morphEntry != null && morphEntry.settled && !morphBack
    val underNav = when {
        morphEntry != null -> stack.getOrNull(lastOriginIdx - 1)?.nav ?: Nav.Home
        else -> (stack.lastOrNull { it.nav != Nav.Player } ?: stack.first()).nav
    }

    // 转场期间把场景阴影逐帧淡出（放大的高斯模糊逐帧重执行极其昂贵，实测帧 150ms+）
    val flightShadow = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(morphEntry?.nav, morphBack, morphEntry?.settled == true) {
        if (morphEntry == null) {
            flightShadow.floatValue = 1f
        } else {
            snapshotFlow { zoomAnimatable.value }.collect { flightShadow.floatValue = 1f - it }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // ── 底层：整个一级场景。推拉时绕卡片中心整体放大/回缩（uniform 相机）──
        CompositionLocalProvider(LocalShadeShadowAlpha provides flightShadow) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { sceneSize = it }
                .graphicsLayer {
                    // 只在图层 lambda 里读推拉进度：逐帧更新图层属性，零重组
                    val entry = morphEntry
                    if (!zoomSteady && entry != null && sceneSize != IntSize.Zero) {
                        val o = entry.origin ?: return@graphicsLayer
                        val w = sceneSize.width.toFloat()
                        val h = sceneSize.height.toFloat()
                        val t = zoomAnimatable.value
                        val s = maxOf(w / o.width, h / o.height)   // 卡片区域恰好铺满屏幕
                        val z = 1f + (s - 1f) * t
                        val cx = lerp(w / 2f, o.center.x, t)       // 相机中心滑向卡片中心
                        val cy = lerp(h / 2f, o.center.y, t)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = z
                        scaleY = z
                        translationX = w / 2f - cx * z
                        translationY = h / 2f - cy * z
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AnimatedContent(
                    targetState = underNav,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                    label = "pageSwap",
                ) { nav ->
                    PageContent(nav = nav, onOpen = openCb, onBack = backCb)
                }
            }
        }
        }

        // ── 底部播放栏：随播放页展开而上移淡出，制造「被带出去」的连续感 ──
        val current by PlayerHost.current.collectAsState()
        if (current != null) {
            MiniPlayerBar(
                onOpen = { if (stack.last().nav != Nav.Player) open(NavRequest(Nav.Player)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 必须浮在推拉层(zIndex 3)之上——否则二级页面盖住播放栏（实测踩过）；
                    // 仍低于播放页覆盖层(4)
                    .zIndex(3.5f)
                    .graphicsLayer {
                        translationY = -screenH * 0.42f * slide
                        alpha = 1f - slide
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // ── 二级页面：放大完成后整屏淡入（推拉的最后一站）──
        if (morphEntry != null) {
            SecondPageOverlay(
                entry = morphEntry,
                progress = zoomAnimatable.asState(),
                onOpen = openCb,
                onBack = backCb,
            )
        }

        // ── 播放页覆盖层：整块从下方上移（zIndex 高于推拉层）──
        if (slide > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(4f)
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

/** 页面栈里普通页面的渲染（底层与推拉层共用）。 */
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
        is Nav.RadioDetail -> key("radio:${nav.id}") {
            // 电台是**无限流**：接口每批只给 5 首且每次都不同（实测），
            // 这里按调用累积去重，每次给约 20 首，由列表滚动到底时继续取。
            // 已见 mid 集合从缓存播种：重进同一电台时 firstplay/去重都基于已缓存内容
            val seen = remember(nav.id) {
                mutableSetOf<String>().apply {
                    com.neumusic.player.ui.home.TrackListCache.get("radio:${nav.id}")?.forEach { add(it.mid) }
                }
            }
            TrackListScreen(
                title = nav.title, onBack = onBack,
                cacheKey = "radio:${nav.id}",
                endless = true,
                loadPage = { _, _ ->
                    val first = seen.isEmpty()
                    val batch = RadioApi.nextTracks(nav.id, firstplay = first, exclude = seen)
                    seen.addAll(batch.map { it.mid })
                    PlaylistApi.Page(songs = batch, total = null)
                },
                onOpenAlbum = { t -> onOpen(NavRequest(Nav.AlbumDetail(t.albumMid, t.albumName))) },
            )
        }
        // 播放页只作为覆盖层出现，正常情况下不会走到这里；兜底不渲染。
        Nav.Player -> Unit
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * 推拉的二级页面端：**不做容器动画**——场景缩放才是主角，二级页面整屏待命，
 * 在推拉的最后四分之一淡入（带一点 1.06→1 的回落），与放大末态 crossfade 无缝衔接。
 * 进度只从图层 lambda 里读（零重组）；落定后按普通页面渲染。
 */
@Composable
private fun SecondPageOverlay(
    entry: StackEntry,
    progress: androidx.compose.runtime.State<Float>,
    onOpen: (NavRequest) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalShadeColors.current
    // **单一实例**：飞行段与落定态共用同一个 PageContent 子树（只靠图层 alpha 差异）。
    // 早期版本按 steady 切换两个分支——落定瞬间飞行实例被销毁，列表的加载协程随
    // rememberCoroutineScope 一起取消，新实例从头再拉，表现为列表偶发空白（实测踩过）。
    val showContent by remember { derivedStateOf { progress.value > 0.05f } }
    if (!showContent) return
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .background(colors.background)   // 必须盖到状态栏区域：否则淡入时透出放大的卡片封面（实测踩过）
            .statusBarsPadding()
            .graphicsLayer {
                val a = ((progress.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
                alpha = a
                val sc = 1f + 0.06f * (1f - a)
                scaleX = sc
                scaleY = sc
            },
    ) {
        PageContent(nav = entry.nav, onOpen = onOpen, onBack = onBack)
    }
}

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
