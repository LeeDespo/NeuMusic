package com.neumusic.player.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.neumusic.player.ui.home.AlbumsScreen
import com.neumusic.player.ui.home.HomeScreen
import com.neumusic.player.ui.home.PlaylistsScreen
import com.neumusic.player.ui.home.TrackListScreen
import com.neumusic.player.ui.player.EqualizerScreen
import com.neumusic.player.ui.player.PlayerScreen
import com.neumusic.player.ui.search.SearchScreen
import com.neumusic.player.ui.settings.SettingsScreen
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * 页面栈根节点（无导航条）。
 *
 * 页面栈是 [Nav] 的列表：**所有返回入口都只弹出栈顶**，所以「从哪来就回哪去」
 * 对任何进入方式都成立（同一页面可能有多个来源）。栈底是主页，栈只剩主页时
 * 把返回交还系统（退出 App）。
 *
 * 播放页是全屏覆盖层而非普通页面：它压在当前页面之上，`translationY` 由是否处于
 * 栈顶驱动；均衡器等页面则可以压在播放页之上，返回时播放页原样回来。
 */
@Composable
fun AppRoot() {
    val colors = LocalShadeColors.current
    val stack = remember { mutableStateListOf<Nav>(Nav.Home) }

    fun open(dest: Nav) {
        stack.add(dest)
    }

    // 弹出栈顶。栈底（主页）不弹——交还系统处理（退出 App）。
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // 让 PlayerHost 能取链（播放器不直接依赖网络层）。
    LaunchedEffect(Unit) {
        PlayerHost.resolveUrl = { track -> loadUrl(track) }
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { back() }

    val top = stack.last()
    val playerOpen = top == Nav.Player
    // 播放页之下那一层才是要渲染的页面（播放页盖在它上面）。
    val page = if (playerOpen) stack.lastOrNull { it != Nav.Player } ?: Nav.Home else top

    // 0 = 播放页完全在屏幕下方（隐藏）；1 = 完全覆盖。
    val slide by animateFloatAsState(if (playerOpen) 1f else 0f, tween(320), label = "playerSlide")
    val screenH = with(LocalDensity.current) { 900.dp.toPx() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // ── 底层：当前页面 ──
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (page) {
                Nav.Home -> HomeScreen(
                    onOpenSearch = { open(Nav.Search) },
                    onOpenSettings = { open(Nav.Settings) },
                    onOpenDest = { dest -> open(dest) },
                )
                Nav.Search -> SearchScreen(
                    onBack = { back() },
                    onOpenAlbum = { open(Nav.AlbumDetail(it.mid, it.name, it.songnum)) },
                )
                Nav.Settings -> SettingsScreen(onBack = { back() })
                Nav.Playlists -> PlaylistsScreen(
                    onBack = { back() },
                    onOpen = { open(Nav.PlaylistDetail(it.tid, it.name, it.songnum)) },
                )
                Nav.Albums -> AlbumsScreen(
                    onBack = { back() },
                    onOpen = { open(Nav.AlbumDetail(it.mid, it.name, it.songnum)) },
                )
                Nav.Liked -> key("liked") { TrackListScreen(
                    title = "我喜欢", onBack = { back() },
                    cacheKey = "liked",
                    loadPage = { off, num -> PlaylistApi.likedPage(off, num) },
                    onOpenAlbum = { t -> open(Nav.AlbumDetail(t.albumMid, t.albumName)) },
                ) }
                Nav.Equalizer -> EqualizerScreen(onBack = { back() })
                is Nav.PlaylistDetail -> key("playlist:${page.tid}") { TrackListScreen(
                    title = page.name, onBack = { back() },
                    cacheKey = "playlist:${page.tid}",
                    knownTotal = page.songnum,
                    loadPage = { off, num -> PlaylistApi.playlistPage(page.tid, off, num) },
                    onOpenAlbum = { t -> open(Nav.AlbumDetail(t.albumMid, t.albumName)) },
                ) }
                is Nav.AlbumDetail -> key("album:${page.mid}") { TrackListScreen(
                    title = page.name, onBack = { back() },
                    cacheKey = "album:${page.mid}",
                    knownTotal = page.songnum,
                    loadPage = { off, num -> PlaylistApi.albumPage(page.mid, off, num) },
                    onOpenAlbum = { t -> open(Nav.AlbumDetail(t.albumMid, t.albumName)) },
                ) }
                is Nav.RadioDetail -> key("radio:${page.id}") { TrackListScreen(
                    title = page.title, onBack = { back() },
                    cacheKey = "radio:${page.id}",
                    // 电台没有总数概念，一次取一大页即可。
                    loadPage = { off, num ->
                        PlaylistApi.Page(
                            songs = RadioApi.tracks(page.id, num = if (off == 0) 200 else 0) ?: emptyList(),
                            total = null,
                        )
                    },
                    onOpenAlbum = { t -> open(Nav.AlbumDetail(t.albumMid, t.albumName)) },
                ) }
                // 播放页只作为覆盖层出现，正常情况下不会是 `page`；兜底不渲染底层内容。
                Nav.Player -> Unit
            }
        }

        // ── 底部播放栏：随播放页展开而上移淡出，制造「被带出去」的连续感 ──
        val current by PlayerHost.current.collectAsState()
        if (current != null) {
            MiniPlayerBar(
                onOpen = { if (stack.last() != Nav.Player) open(Nav.Player) },
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
                    onOpenEqualizer = { open(Nav.Equalizer) },
                )
            }
        }
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
