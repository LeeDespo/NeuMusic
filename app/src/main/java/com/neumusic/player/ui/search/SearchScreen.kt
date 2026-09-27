package com.neumusic.player.ui.search

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.CollapsingTopBar
import com.neumusic.player.ui.common.RowDivider
import com.neumusic.player.ui.common.SelectionBar
import com.neumusic.player.ui.common.TrackFormatsDialog
import com.neumusic.player.ui.common.TrackInfoDialog
import com.neumusic.player.ui.common.TrackListBlock
import com.neumusic.player.ui.common.TrackRow
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.common.TrackMoreDialog
import com.neumusic.player.ui.common.TopBarContentSwitch
import com.neumusic.player.ui.common.doubleTapToTop
import com.neumusic.player.ui.common.downloadIconRotation
import com.neumusic.player.ui.common.playQueue
import com.neumusic.player.ui.common.rememberTopBarVisible
import com.neumusic.player.ui.home.DetailTopBar
import com.neumusic.player.ui.home.toggleLike
import com.neumusic.player.shade.ShadeFusedTab
import com.neumusic.player.shade.ShadeFusedTabs
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalShadeColors.current
    val listState = rememberLazyListState()
    val barVisible = rememberTopBarVisible(listState)
    val likedIds by LikedStore.liked.collectAsState()
    val downloadedMap by DownloadStore.records.collectAsState()

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

    // 选择模式（仅歌曲标签下可用）
    var selecting by remember { mutableStateOf(false) }
    val selectedMids = remember { mutableStateListOf<String>() }
    var downloadProgress by remember { mutableStateOf<String?>(null) }
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var formatTrack by remember { mutableStateOf<Track?>(null) }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = null
            runCatching { SearchApi.songs(q) }
                .onSuccess {
                    songResults = it
                    singerResults = null; albumResults = null; playlistResults = null
                    searchedQuery = q
                    tab = 0
                    error = if (it.isEmpty()) "没有找到相关歌曲" else null
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
            runCatching {
                when (t) {
                    SearchTab.SONGS -> songResults = SearchApi.songs(q)
                    SearchTab.SINGERS -> singerResults = SearchApi.singers(q)
                    SearchTab.ALBUMS -> albumResults = SearchApi.albums(q)
                    SearchTab.PLAYLISTS -> playlistResults = SearchApi.playlists(q)
                }
            }.onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // 顶栏悬浮在上面（66dp），内容从它下方开始，避免重叠
                .padding(top = 74.dp)
                .padding(horizontal = 16.dp),
        ) {
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
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent,
                        )
                    } else {
                        Icon(Icons.Filled.Search, "搜索", tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val q = searchedQuery
            when {
                error != null && q != null -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(error!!, color = colors.textSecondary, fontSize = 13.sp)
                }
                q != null -> {
                    // 标签栏：融合式（与列表连成一块）
                    val tabs = SearchTab.entries
                    Column(Modifier.weight(1f)) {
                        ShadeFusedTabs(
                            tabCount = tabs.size,
                            selectedIndex = tab,
                            tabHeight = 44.dp,
                            cornerRadius = 18.dp,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().height(44.dp)) {
                                    tabs.forEachIndexed { i, t ->
                                        ShadeFusedTab(
                                            label = t.label,
                                            selected = i == tab,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            tab = i
                                            loadTab(t, q)
                                            if (t != SearchTab.SONGS && selecting) {
                                                selecting = false; selectedMids.clear()
                                            }
                                        }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    // 结果列表上下边缘渐隐
                                    VerticalEdgeFades(state = listState, height = 26.dp)
                                    val current = tabs[tab]
                                    if (loading && !hasAnyResult(current, songResults, singerResults, albumResults, playlistResults)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent,
                                            )
                                        }
                                    } else {
                                        SearchResults(
                                            tab = current,
                                            songs = songResults,
                                            singers = singerResults,
                                            albums = albumResults,
                                            playlists = playlistResults,
                                            likedIds = likedIds,
                                            downloadedMap = downloadedMap,
                                            selecting = selecting,
                                            selectedMids = selectedMids,
                                            listState = listState,
                                            onPlayQueue = { list, i -> playQueue(context, list, i) },
                                            onLike = { t, liked -> toggleLike(context, t, liked) },
                                            onMore = { moreTrack = it },
                                            onToggleSelect = { t ->
                                                if (t.mid in selectedMids) selectedMids.remove(t.mid)
                                                else selectedMids.add(t.mid)
                                            },
                                            onOpenAlbum = onOpenAlbum,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 搜索历史：仅当确有历史时才出现（用户要求：没有历史就什么都不显示）。
                    if (history.isNotEmpty()) {
                        LazyColumn(Modifier.weight(1f)) {
                            item {
                                TrackListBlock {
                                    // 头行：标题 + 清空全部
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
                                            // 词：点击即搜
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
                                            // 单条删除
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
                            item { Spacer(Modifier.height(124.dp)) }
                        }
                    }
                    // 没有历史：什么都不显示（连"搜一首想听的歌吧"也不出，按用户要求）
                }
            }
        }

        // 顶栏：普通 ↔ 选择（歌曲标签下才给下载钮）
        val downloadRot = downloadIconRotation(selecting)
        TopBarContentSwitch(
            selecting = selecting,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(2f),
            normal = {
                CollapsingTopBar(
                    title = "搜索",
                    onBack = onBack,
                    visible = barVisible,
                    trailing = {
                        if (searchedQuery != null && SearchTab.entries[tab] == SearchTab.SONGS) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .graphicsLayer { rotationZ = downloadRot }
                                    .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp) {
                                        selecting = !selecting
                                        if (!selecting) selectedMids.clear()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Download, "选择下载", tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                )
            },
            selection = {
                SelectionBar(
                    selecting = selecting,
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
                    onCancel = { selecting = false; selectedMids.clear() },
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
            },
        )
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
@Composable
private fun SearchResults(
    tab: SearchTab,
    songs: List<Track>?,
    singers: List<SearchSinger>?,
    albums: List<AlbumItem>?,
    playlists: List<PlaylistItem>?,
    likedIds: Set<Long>,
    downloadedMap: Map<String, com.neumusic.player.data.DownloadRecord>,
    selecting: Boolean,
    selectedMids: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onLike: (Track, Boolean) -> Unit,
    onMore: (Track) -> Unit,
    onToggleSelect: (Track) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().doubleTapToTop(listState)) {
        when (tab) {
            SearchTab.SONGS -> {
                val list = songs.orEmpty()
                if (list.isEmpty()) {
                    item { EmptyText("没有找到相关歌曲") }
                } else item {
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
            SearchTab.SINGERS -> {
                val list = singers.orEmpty()
                if (list.isEmpty()) item { EmptyText("没有找到相关歌手") }
                else item {
                    TrackListBlock {
                        list.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            SimpleRow(title = s.name, subtitle = "歌曲 ${s.songNum} · 专辑 ${s.albumNum}", logo = s.pic)
                        }
                    }
                }
            }
            SearchTab.ALBUMS -> {
                val list = albums.orEmpty()
                if (list.isEmpty()) item { EmptyText("没有找到相关专辑") }
                else item {
                    TrackListBlock {
                        list.forEachIndexed { i, a ->
                            if (i > 0) RowDivider()
                            SimpleRow(
                                title = a.name,
                                subtitle = buildString {
                                    append(if (a.singerName.isNotEmpty()) a.singerName else "专辑")
                                    if (a.songnum > 0) append(" · ${a.songnum} 首")
                                },
                                logo = a.logo,
                                onClick = { onOpenAlbum(a) },
                            )
                        }
                    }
                }
            }
            SearchTab.PLAYLISTS -> {
                val list = playlists.orEmpty()
                if (list.isEmpty()) item { EmptyText("没有找到相关歌单") }
                else item {
                    TrackListBlock {
                        list.forEachIndexed { i, p ->
                            if (i > 0) RowDivider()
                            SimpleRow(title = p.name, subtitle = "${p.songnum} 首", logo = p.logo)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(124.dp)) }
    }
}

@Composable
private fun EmptyText(text: String) {
    val colors = LocalShadeColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.textTertiary, fontSize = 13.sp)
    }
}

/** 歌手/专辑/歌单的行（平面按压；位于整块凸起内）。 */
@Composable
private fun SimpleRow(
    title: String,
    subtitle: String,
    logo: String,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalShadeColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.flatPressable(cornerRadius = 14.dp, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(logo, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
