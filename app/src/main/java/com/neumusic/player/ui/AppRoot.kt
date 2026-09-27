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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.RadioApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.VizHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.loadUrl
import com.neumusic.player.ui.home.AlbumsScreen
import com.neumusic.player.ui.home.HomeDest
import com.neumusic.player.ui.home.HomeScreen
import com.neumusic.player.ui.home.PlaylistsScreen
import com.neumusic.player.ui.home.RadioScreen
import com.neumusic.player.ui.home.TrackListScreen
import com.neumusic.player.ui.player.PlayerScreen
import com.neumusic.player.ui.search.SearchScreen
import com.neumusic.player.ui.settings.SettingsScreen
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.neumusic.player.shade.shadeInset

/** 主页之外的页面（无导航条，靠页面内的凸起返回按钮回主页）。 */
private sealed interface Page {
    data object Home : Page
    data object Search : Page
    data object Settings : Page
    data class Playlists(val noArg: Int = 0) : Page
    data class Albums(val noArg: Int = 0) : Page
    data object Radio : Page
    /** 均衡器（播放页顶栏进入）。 */
    data object Equalizer : Page
    /** 「我喜欢」列表（首页「收藏的歌单」第一张卡片）。 */
    data object Liked : Page
    data class PlaylistDetail(val tid: Long, val name: String, val songnum: Int? = null) : Page
    data class AlbumDetail(val mid: String, val name: String, val songnum: Int? = null) : Page
    data class RadioDetail(val id: Int, val title: String) : Page
}

@Composable
fun AppRoot() {
    val colors = LocalShadeColors.current
    var page by remember { mutableStateOf<Page>(Page.Home) }
    var playerOpen by remember { mutableStateOf(false) }

    // 让 PlayerHost 能取链（播放器不直接依赖网络层）。
    LaunchedEffect(Unit) {
        PlayerHost.resolveUrl = { track -> loadUrl(track) }
    }

    // 系统返回键：先收播放页，再回主页；已在主页则交还系统（退出）。
    // 页面栈是手写的，不接 BackHandler 的话在二级页按返回会直接退出 App。
    androidx.activity.compose.BackHandler(
        enabled = playerOpen || page != Page.Home,
    ) {
        if (playerOpen) playerOpen = false else page = Page.Home
    }

    // 0 = 播放页完全在屏幕下方（隐藏）；1 = 完全覆盖主页。
    val slide by animateFloatAsState(if (playerOpen) 1f else 0f, tween(320), label = "playerSlide")
    val screenH = with(LocalDensity.current) { 900.dp.toPx() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // ── 底层：主页面栈 ──
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (val p = page) {
                Page.Home -> HomeScreen(
                    onOpenSearch = { page = Page.Search },
                    onOpenSettings = { page = Page.Settings },
                    onOpenDest = { dest ->
                        page = when (dest) {
                            HomeDest.Playlists -> Page.Playlists()
                            HomeDest.Albums -> Page.Albums()
                            HomeDest.Radio -> Page.Radio
                            HomeDest.Liked -> Page.Liked
                            is HomeDest.PlaylistDetail -> Page.PlaylistDetail(dest.tid, dest.name, dest.songnum)
                            is HomeDest.AlbumDetail -> Page.AlbumDetail(dest.mid, dest.name, dest.songnum)
                            is HomeDest.RadioDetail -> Page.RadioDetail(dest.id, dest.title)
                        }
                    },
                )
                Page.Search -> SearchScreen(
                    onBack = { page = Page.Home },
                    onOpenAlbum = { page = Page.AlbumDetail(it.mid, it.name, it.songnum) },
                )
                Page.Settings -> SettingsScreen(onBack = { page = Page.Home })
                is Page.Playlists -> PlaylistsScreen(
                    onBack = { page = Page.Home },
                    onOpen = { page = Page.PlaylistDetail(it.tid, it.name, it.songnum) },
                )
                is Page.Albums -> AlbumsScreen(
                    onBack = { page = Page.Home },
                    onOpen = { page = Page.AlbumDetail(it.mid, it.name, it.songnum) },
                )
                Page.Radio -> RadioScreen(
                    onBack = { page = Page.Home },
                    onOpenStation = { id, title -> page = Page.RadioDetail(id, title) },
                )
                Page.Equalizer -> com.neumusic.player.ui.player.EqualizerScreen(
                    onBack = { page = Page.Home },
                )
                is Page.Liked -> TrackListScreen(
                    title = "我喜欢", onBack = { page = Page.Home },
                    cacheKey = "liked",
                    loadPage = { off, num -> PlaylistApi.likedPage(off, num) },
                    onOpenAlbum = { t -> page = Page.AlbumDetail(t.albumMid, t.albumName) },
                )
                is Page.PlaylistDetail -> TrackListScreen(
                    title = p.name, onBack = { page = Page.Playlists() },
                    cacheKey = "playlist:${p.tid}",
                    knownTotal = p.songnum,
                    loadPage = { off, num -> PlaylistApi.playlistPage(p.tid, off, num) },
                    onOpenAlbum = { t -> page = Page.AlbumDetail(t.albumMid, t.albumName) },
                )
                is Page.AlbumDetail -> TrackListScreen(
                    title = p.name, onBack = { page = Page.Albums() },
                    cacheKey = "album:${p.mid}",
                    knownTotal = p.songnum,
                    loadPage = { off, num -> PlaylistApi.albumPage(p.mid, off, num) },
                    onOpenAlbum = { t -> page = Page.AlbumDetail(t.albumMid, t.albumName) },
                )
                is Page.RadioDetail -> TrackListScreen(
                    title = p.title, onBack = { page = Page.Radio },
                    cacheKey = "radio:${p.id}",
                    // 电台没有总数概念，一次取一大页即可。
                    loadPage = { off, num ->
                        PlaylistApi.Page(
                            songs = RadioApi.tracks(p.id, num = if (off == 0) 200 else 0) ?: emptyList(),
                            total = null,
                        )
                    },
                    onOpenAlbum = { t -> page = Page.AlbumDetail(t.albumMid, t.albumName) },
                )
            }
        }

        // ── 底部播放栏：随播放页展开而上移淡出，制造「被带出去」的连续感 ──
        val current by PlayerHost.current.collectAsState()
        if (current != null) {
            MiniPlayerBar(
                onOpen = { playerOpen = true },
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
                    onBack = { playerOpen = false },
                    onOpenEqualizer = {
                        playerOpen = false
                        page = Page.Equalizer
                    },
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
