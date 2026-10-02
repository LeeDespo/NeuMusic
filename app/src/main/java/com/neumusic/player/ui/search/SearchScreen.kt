package com.neumusic.player.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.data.Downloader
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.SearchSinger
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.SearchApi
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.ui.common.rememberPlayerBarSpace
import com.neumusic.player.ui.common.TopEdgeFade
import com.neumusic.player.ui.common.rememberTopContentInset
import com.neumusic.player.ui.common.cardGridItems
import com.neumusic.player.ui.common.gridColumns
import com.neumusic.player.ui.common.MediaCard
import com.neumusic.player.ui.common.SingerCard
import com.neumusic.player.ui.common.RowDivider
import com.neumusic.player.ui.common.SelectionBar
import com.neumusic.player.ui.common.TrackFormatsDialog
import com.neumusic.player.ui.common.TrackInfoDialog
import com.neumusic.player.ui.common.TrackListBlock
import com.neumusic.player.ui.common.TrackRow
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.common.TrackMoreDialog
import com.neumusic.player.ui.common.doubleTapToTop
import com.neumusic.player.ui.common.playQueue
import com.neumusic.player.ui.common.rememberSelectionBusSync
import com.neumusic.player.ui.common.toggleLike
import kotlinx.coroutines.launch
import com.neumusic.player.data.SearchHistoryStore

/** 搜索结果的四个标签。 */
private enum class SearchTab(val label: String, val api: Int) {
    SONGS("歌曲", 0), SINGERS("歌手", 1), ALBUMS("专辑", 2), PLAYLISTS("歌单", 3);
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit = {},
    onOpenSinger: (SearchSinger, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
) {
    val barSpace = rememberPlayerBarSpace()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalShadeColors.current
    val listState = rememberLazyListState()
    val likedIds by LikedStore.liked.collectAsState()
    val downloadedMap by DownloadStore.records.collectAsState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    var query by rememberSaveable { mutableStateOf("") }
    val history by SearchHistoryStore.items.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchedQuery by remember { mutableStateOf<String?>(null) } // 已搜过的词
    var tab by remember { mutableIntStateOf(0) }

    // 各类结果按 (词, tab) 缓存：切标签不重复请求。
    var songResults by remember { mutableStateOf<List<Track>?>(null) }
    var singerResults by remember { mutableStateOf<List<SearchSinger>?>(null) }
    var albumResults by remember { mutableStateOf<List<AlbumItem>?>(null) }
    var playlistResults by remember { mutableStateOf<List<PlaylistItem>?>(null) }

    // 选择模式（仅歌曲标签下可进入；激活时通知 AppRoot 沉下播放栏给选择底栏让位）
    var selecting by remember { mutableStateOf(false) }
    val selectedMids = remember { mutableStateListOf<String>() }
    var downloadProgress by remember { mutableStateOf<String?>(null) }
    rememberSelectionBusSync(selecting)
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var formatTrack by remember { mutableStateOf<Track?>(null) }

    // ── 分页（用户 2026-10-01：搜索结果原来有上限，滚到底不会继续加载）──
    val pageByTab = remember { mutableStateMapOf<SearchTab, Int>() }
    val exhausted = remember { mutableStateMapOf<SearchTab, Boolean>() }
    var appending by remember { mutableStateOf(false) }

    /** 取某标签的第 p 页并**追加**（p=1 时重置）。返回本页条数。 */
    suspend fun loadPage(t: SearchTab, q: String, p: Int): Int = when (t) {
        SearchTab.SONGS -> SearchApi.songs(q, page = p).also {
            songResults = if (p == 1) it else songResults.orEmpty() + it
        }.size
        SearchTab.SINGERS -> SearchApi.singers(q, page = p).also {
            singerResults = if (p == 1) it else singerResults.orEmpty() + it
        }.size
        SearchTab.ALBUMS -> SearchApi.albums(q, page = p).also {
            albumResults = if (p == 1) it else albumResults.orEmpty() + it
        }.size
        SearchTab.PLAYLISTS -> SearchApi.playlists(q, page = p).also {
            playlistResults = if (p == 1) it else playlistResults.orEmpty() + it
        }.size
    }

    fun appendMore() {
        val q = searchedQuery ?: return
        val t = SearchTab.entries[tab]
        if (appending || loading || exhausted[t] == true) return
        val cur = pageByTab[t] ?: return
        appending = true
        scope.launch {
            val next = cur + 1
            val n = runCatching { loadPage(t, q, next) }.getOrDefault(0)
            if (n <= 0) exhausted[t] = true else pageByTab[t] = next
            appending = false
        }
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = null
            pageByTab.clear(); exhausted.clear()
            songResults = null; singerResults = null; albumResults = null; playlistResults = null
            runCatching { loadPage(SearchTab.SONGS, q, 1) }
                .onSuccess {
                    searchedQuery = q
                    tab = 0
                    pageByTab[SearchTab.SONGS] = 1
                    error = if (songResults.isNullOrEmpty()) "没有找到相关歌曲" else null
                    SearchHistoryStore.add(q)   // 点击搜索才记录
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    /** 首次切到某标签时按需加载该类结果。 */
    fun loadTab(t: SearchTab, q: String) {
        val done = when (t) {
            SearchTab.SONGS -> songResults != null
            SearchTab.SINGERS -> singerResults != null
            SearchTab.ALBUMS -> albumResults != null
            SearchTab.PLAYLISTS -> playlistResults != null
        }
        if (done || loading) return
        scope.launch {
            loading = true
            error = null
            runCatching { loadPage(t, q, 1) }
                .onSuccess { pageByTab[t] = 1 }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        // 滚到底部前几项就续页（每页 30 首）；必须在组合上下文里，不能放进 LazyListScope。
        LaunchedEffect(listState, tab, searchedQuery) {
            androidx.compose.runtime.snapshotFlow {
                val info = listState.layoutInfo
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
            }.collect { (last, total) ->
                if (total > 0 && last >= total - 3) appendMore()
            }
        }
        // 2026-09-30 改版：**所有元素都随内容一起上滑**（返回/标题/下载钮、搜索框、标签栏
        // 全部是列表的表头 item，不再悬浮折叠），与主页/列表页一致；内容从状态栏底下滚过。
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(colors.background).doubleTapToTop(listState),
            contentPadding = PaddingValues(bottom = barSpace),
        ) {
            item(key = "header") {
                Column(
                    // 与主页/列表页同高：状态栏 + 渐隐线偏移(8dp) + 12dp
                    Modifier.fillMaxWidth()
                        .padding(top = rememberTopContentInset(extra = 12.dp))
                        .padding(horizontal = 16.dp),
                ) {
                    // 顶栏行：返回 + 标题 + 下载（选择模式中变 ✕）
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(42.dp)
                                .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.accent, modifier = Modifier.size(19.dp))
                        }
                        Text(
                            "搜索", Modifier.weight(1f),
                            color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (selecting || (searchedQuery != null && SearchTab.entries[tab] == SearchTab.SONGS)) {
                            Box(
                                Modifier.size(42.dp)
                                    .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp) {
                                        if (selecting) {
                                            selecting = false
                                            selectedMids.clear()
                                        } else {
                                            selecting = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(selecting, animationSpec = tween(160), label = "dlIcon") { sel ->
                                    Icon(
                                        if (sel) Icons.Filled.Close else Icons.Filled.Download,
                                        if (sel) "退出选择" else "选择下载",
                                        tint = colors.accent, modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 搜索框（凹陷轨道；内部按钮只能「平」）
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .shadeInset(cornerRadius = 18.dp, offset = 3.dp, blur = 5.dp)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) Text("歌曲 / 歌手 / 专辑", color = colors.textTertiary, fontSize = 14.sp)
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                                        cursorBrush = SolidColor(colors.accent),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (query.isNotEmpty()) {
                                    Box(
                                        Modifier.size(26.dp).flatPressable(cornerRadius = 13.dp) {
                                            query = ""
                                            searchedQuery = null   // 清空后回到历史列表
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Close, "清除", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier.size(50.dp).shadePressable(cornerRadius = 18.dp, offset = 4.dp, blur = 7.dp) { doSearch() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent,
                                )
                            } else {
                                Icon(Icons.Filled.Search, "搜索", tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    // 标签栏：平的分段控制（选中=凹陷圆角矩形，同歌手页分段控制器）
                    if (searchedQuery != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SearchTab.entries.forEachIndexed { i, t ->
                                val sel = i == tab
                                Box(
                                    Modifier
                                        .then(if (sel) Modifier.shadeInset(cornerRadius = 13.dp, offset = 2.dp, blur = 4.dp) else Modifier)
                                        .flatPressable(cornerRadius = 13.dp) {
                                            tab = i
                                            loadTab(t, searchedQuery ?: return@flatPressable)
                                            if (t != SearchTab.SONGS && selecting) {
                                                selecting = false; selectedMids.clear()
                                            }
                                        }
                                        .padding(horizontal = 15.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        t.label,
                                        color = if (sel) colors.accent else colors.textSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        // 分段控制器与下方结果之间留出呼吸（用户 2026-10-01：原来贴在一起）
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
            val q = searchedQuery
            when {
                error != null && q != null -> item(key = "error") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(error!!, color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
                q != null -> {
                    val current = SearchTab.entries[tab]
                    if (loading && !hasAnyResult(current, songResults, singerResults, albumResults, playlistResults)) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                            }
                        }
                    } else {
                        searchResultItems(
                            tab = current,
                            songs = songResults,
                            singers = singerResults,
                            albums = albumResults,
                            playlists = playlistResults,
                            likedIds = likedIds,
                            downloadedMap = downloadedMap,
                            selecting = selecting,
                            selectedMids = selectedMids,
                            onPlayQueue = { list, i -> playQueue(context, list, i) },
                            onLike = { t, liked -> toggleLike(context, t, liked) },
                            onMore = { moreTrack = it },
                            onToggleSelect = { t ->
                                if (t.mid in selectedMids) selectedMids.remove(t.mid)
                                else selectedMids.add(t.mid)
                            },
                            onOpenAlbum = onOpenAlbum,
                            onOpenSinger = onOpenSinger,
                            screenWidth = screenWidth,
                        )
                        if (appending) {
                            item(key = "appending") {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 搜索历史：仅当确有历史时才出现（用户要求：没有历史就什么都不显示）。
                    if (history.isNotEmpty()) {
                        // 与上面的搜索框拉开（用户：贴太近）
                        item(key = "historyGap") { Spacer(Modifier.height(18.dp)) }
                        item(key = "history") {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                TrackListBlock {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "搜索历史",
                                            color = colors.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "清空",
                                            color = colors.accent,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .flatPressable(cornerRadius = 12.dp) {
                                                    SearchHistoryStore.clear()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                    RowDivider()
                                    history.forEach { h ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                Modifier.weight(1f)
                                                    .flatPressable(cornerRadius = 12.dp) {
                                                        query = h
                                                        doSearch()
                                                    }
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Filled.Search, null,
                                                    tint = colors.textTertiary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.size(10.dp))
                                                Text(
                                                    h,
                                                    color = colors.textPrimary,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Box(
                                                Modifier.size(34.dp)
                                                    .flatPressable(cornerRadius = 17.dp) {
                                                        SearchHistoryStore.remove(h)
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close, "删除 $h",
                                                    tint = colors.textTertiary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        if (h != history.last()) RowDivider(0.4f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 顶部渐隐：盖过系统状态栏、范围加大；底部与主页同款
        if (listState.canScrollBackward) TopEdgeFade()
        VerticalEdgeFades(state = listState, height = 26.dp, top = false)

        // 选择模式：底部工具栏上升出现（全选/反选/下载三图标钮）；退出时下滑消失
        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
        ) {
            SelectionBar(
                selectedCount = selectedMids.size,
                downloading = downloadProgress,
                onSelectAll = {
                    selectedMids.clear()
                    songResults?.filter { !downloadedMap.containsKey(it.mid) }
                        ?.forEach { selectedMids.add(it.mid) }
                },
                onInvert = {
                    val invert = songResults.orEmpty()
                        .filter { !downloadedMap.containsKey(it.mid) }
                        .map { it.mid to (it.mid !in selectedMids) }
                    selectedMids.clear()
                    invert.forEach { (mid, sel) -> if (sel) selectedMids.add(mid) }
                },
                onDownload = {
                    scope.launch {
                        val picks = songResults.orEmpty().filter { it.mid in selectedMids }
                        picks.forEachIndexed { idx, t ->
                            downloadProgress = "${idx + 1}/${picks.size}"
                            runCatching { Downloader.download(context, t) }
                        }
                        downloadProgress = null
                        selectedMids.clear()
                        selecting = false
                    }
                },
            )
        }
    }

    // 弹窗
    moreTrack?.let { t ->
        val all = songResults.orEmpty()
        TrackMoreDialog(
            track = t,
            onDismiss = { moreTrack = null },
            onPlay = {
                val idx = all.indexOfFirst { it.mid == t.mid }.coerceAtLeast(0)
                if (all.isNotEmpty()) playQueue(context, all, idx)
            },
            onPlayNext = { com.neumusic.player.player.PlayerHost.playNext(t) },
            onInfo = { infoTrack = t },
            onOpenAlbum = { onOpenAlbum(AlbumItem(t.albumMid, t.albumName, t.coverUrl, 0)) },
            onFormats = { formatTrack = t },
        )
    }
    infoTrack?.let { TrackInfoDialog(it) { infoTrack = null } }
    formatTrack?.let { TrackFormatsDialog(it) { formatTrack = null } }
}

private fun hasAnyResult(
    tab: SearchTab,
    songs: List<Track>?,
    singers: List<SearchSinger>?,
    albums: List<AlbumItem>?,
    playlists: List<PlaylistItem>?,
): Boolean = when (tab) {
    SearchTab.SONGS -> songs != null
    SearchTab.SINGERS -> singers != null
    SearchTab.ALBUMS -> albums != null
    SearchTab.PLAYLISTS -> playlists != null
}

/** 按当前标签渲染结果；歌曲 = 整块列表，其余 = 整块行列表。 */
/**
 * 搜索结果条目（LazyListScope 扩展，2026-09-30 改版）：与页头同在一个 LazyColumn，
 * 滚动位置统一；歌手/专辑/歌单为卡片网格（歌手卡限宽 300dp 居中）。
 */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResultItems(
    tab: SearchTab,
    songs: List<Track>?,
    singers: List<SearchSinger>?,
    albums: List<AlbumItem>?,
    playlists: List<PlaylistItem>?,
    likedIds: Set<Long>,
    downloadedMap: Map<String, com.neumusic.player.data.DownloadRecord>,
    selecting: Boolean,
    selectedMids: List<String>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onLike: (Track, Boolean) -> Unit,
    onMore: (Track) -> Unit,
    onToggleSelect: (Track) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenSinger: (SearchSinger, androidx.compose.ui.geometry.Rect) -> Unit,
    screenWidth: androidx.compose.ui.unit.Dp,
) {
    when (tab) {
        SearchTab.SONGS -> {
            val list = songs.orEmpty()
            if (list.isEmpty()) {
                item(key = "songsEmpty") { EmptyText("没有找到相关歌曲") }
            } else item(key = "songs") {
                Column(
                    Modifier
                        .animateItem(   // 切标签时整块淡出/淡入（与卡片行同款）
                            fadeInSpec = androidx.compose.animation.core.tween(170),
                            fadeOutSpec = androidx.compose.animation.core.tween(120),
                        )
                        .padding(horizontal = 16.dp),
                ) {
                    TrackListBlock {
                        list.forEachIndexed { i, track ->
                            if (i > 0) RowDivider()
                            TrackRow(
                                track = track,
                                onPlay = { onPlayQueue(list, i) },
                                onMore = { onMore(track) },
                                liked = likedIds.contains(track.songId),
                                onLike = { onLike(track, likedIds.contains(track.songId)) },
                                selecting = selecting,
                                selected = track.mid in selectedMids,
                                downloaded = downloadedMap.containsKey(track.mid),
                                onToggleSelect = { onToggleSelect(track) },
                            )
                        }
                    }
                }
            }
        }
        // 歌手：一行一张大卡（宽屏自适应多张）。点卡片进歌手页时带上卡片自身坐标——
        // 「飞位」共享元素转场（卡片飞到歌手页头像位，其余元素淡出/浮现）需要起点。
        SearchTab.SINGERS -> {
            val list = singers.orEmpty()
            if (list.isEmpty()) item(key = "singersEmpty") { EmptyText("没有找到相关歌手") }
            else cardGridItems(
                items = list,
                columns = gridColumns(screenWidth, minCard = 300.dp),
                key = { it.mid },
                maxCardWidth = 300.dp,
                horizontalPadding = 16.dp,
            ) { s ->
                var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Box(
                    Modifier
                        .onGloballyPositioned { c ->
                            // 同上：边缘卡片不能被裁剪（否则飞位克隆卡缩成小框）
                            cardBounds = androidx.compose.ui.geometry.Rect(c.localToRoot(androidx.compose.ui.geometry.Offset.Zero), androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
                        }
                        .fillMaxWidth(),
                ) {
                    SingerCard(
                        name = s.name, pic = s.pic, songNum = s.songNum, albumNum = s.albumNum,
                        onClick = { cardBounds?.let { r -> onOpenSinger(s, r) } },
                    )
                }
            }
        }
        // 专辑/歌单：歌手页同款的封面卡网格
        SearchTab.ALBUMS -> {
            val list = albums.orEmpty()
            if (list.isEmpty()) item(key = "albumsEmpty") { EmptyText("没有找到相关专辑") }
            else cardGridItems(
                items = list,
                columns = gridColumns(screenWidth, minCard = 170.dp),
                key = { it.mid },
                horizontalPadding = 16.dp,
            ) { a ->
                MediaCard(
                    logo = a.logo, title = a.name, count = a.songnum,
                    line2 = a.singerName.ifEmpty { null },
                    onClick = { onOpenAlbum(a) },
                )
            }
        }
        SearchTab.PLAYLISTS -> {
            val list = playlists.orEmpty()
            if (list.isEmpty()) item(key = "playlistsEmpty") { EmptyText("没有找到相关歌单") }
            else cardGridItems(
                items = list,
                columns = gridColumns(screenWidth, minCard = 170.dp),
                key = { it.tid.toString() },
                horizontalPadding = 16.dp,
            ) { p ->
                MediaCard(logo = p.logo, title = p.name, count = p.songnum, line2 = null)
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    val colors = LocalShadeColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.textTertiary, fontSize = 13.sp)
    }
}

