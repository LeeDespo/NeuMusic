package com.neumusic.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.Downloader
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.ui.common.TOP_FADE_BELOW_STATUS_BAR
import com.neumusic.player.ui.common.TopEdgeFade
import com.neumusic.player.ui.common.rememberTopContentInset
import com.neumusic.player.ui.common.rememberPlayerBarSpace
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.RowDivider
import com.neumusic.player.ui.common.BlockRowSurface
import com.neumusic.player.shade.BlockSlice
import com.neumusic.player.ui.common.TrackListBlock
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.common.TrackRow
import com.neumusic.player.ui.common.playQueue
import com.neumusic.player.ui.common.doubleTapToTop
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.common.toggleLike
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.mutableStateListOf
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.ui.common.SelectionBar
import com.neumusic.player.ui.common.TrackFormatsDialog
import com.neumusic.player.ui.common.TrackInfoDialog
import com.neumusic.player.ui.common.TrackMoreDialog
import com.neumusic.player.ui.common.rememberSelectionBusSync
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * 二级页通用顶栏：凸起返回按钮 + 标题。
 * **顶栏随页面滚动**（用户规定）：调用方必须把它放进滚动容器的第一项，
 * 不允许固定悬浮在滚动区外。
 */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    horizontalPadding: Dp = 16.dp,
    /** 页面层不再统一加状态栏内缩，顶栏自己带上（内容仍可滚到状态栏底下）。 */
    withStatusBarInset: Boolean = true,
) {
    val colors = LocalShadeColors.current
    // 顶部内缩：状态栏 + 渐隐线偏移(8dp) + 12dp —— 与主页/列表页首个内容同高
    val topPad = if (withStatusBarInset) rememberTopContentInset(extra = 12.dp) else 12.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = topPad, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.accent, modifier = Modifier.size(19.dp))
        }
        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 曲目列表页（歌单/专辑/电台共用）。
 *
 * 三个要点：
 * 1. **翻页取全**：接口会返回总数，[loadPage] 按 offset/num 翻到底为止，
 *    边翻边显示，底部给出「已加载 N / 共 M」——不再固定只取前 100 首。
 * 2. 列表是**一整块凸起**，行间用短横线分隔（不逐行凸起）。
 * 3. 底部留足空间，避免最后一行被播放栏压住。
 */
@Composable
fun TrackListScreen(
    title: String,
    onBack: () -> Unit,
    /** 缓存键（如 "liked" / "playlist:123"）。相同键二次进入直接复用结果，不再重拉。 */
    cacheKey: String,
    loadPage: suspend (offset: Int, num: Int) -> PlaylistApi.Page,
    /** 总数已知时的提示（可选）；null 表示不显示进度文案。 */
    knownTotal: Int? = null,
    /**
     * **无限流**（电台、猜你喜欢这类没有"总数/到底"概念的来源）：为 true 时
     * 首屏只取一块，滚动到底部自动调 [loadPage] 追加，直到它返回空表为止。
     * 为 false（默认）时沿用"翻页取全"。
     */
    endless: Boolean = false,
    /** 「更多 → 查看专辑」的跳转回调。 */
    onOpenAlbum: (Track) -> Unit = {},
) {
    val barSpace = rememberPlayerBarSpace()
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // 进程内缓存：相同 cacheKey 二次进入直接显示上次结果（用户反馈"偶尔会重新加载"）。
    val cachedTracks = remember(cacheKey) { TrackListCache.get(cacheKey) }
    val cachedScroll = remember(cacheKey) { TrackListCache.scroll(cacheKey) }
    var tracks by remember { mutableStateOf(cachedTracks ?: emptyList()) }
    var loaded by remember { mutableStateOf(cachedTracks != null) }
    var failed by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(TrackListCache.total(cacheKey) ?: knownTotal) }
    var loadingMore by remember { mutableStateOf(false) }
    var failedAt by remember { mutableStateOf<Int?>(null) } // 失败时的 offset，重试从这继续
    // 单轮 50 页上限触发的「正常截断」：按暂停续传处理而非失败（否则大歌单会显示假的
    // 「加载失败」且不落缓存）。与 failedAt 分开记；随缓存持久化，重进后仍可继续。
    var pausedAt by remember(cacheKey) { mutableStateOf(TrackListCache.pausedAt(cacheKey)) }
    // 无限流：数据源已循环到底（loadPage 返回空）就不再请求
    var exhausted by remember { mutableStateOf(false) }
    val endlessMode = endless && total == null
    // 滚动位置随缓存一起remember：手动页面栈下 rememberSaveable 会被销毁，
    // 所以进入时从缓存取上次位置，离开时（DisposableEffect）写回。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = cachedScroll.first,
        initialFirstVisibleItemScrollOffset = cachedScroll.second,
    )
    androidx.compose.runtime.DisposableEffect(cacheKey) {
        onDispose {
            TrackListCache.saveScroll(
                cacheKey, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
            )
        }
    }
    val likedIds by LikedStore.liked.collectAsState()
    val downloadedMap by DownloadStore.records.collectAsState()

    // 选择模式（顶栏下载按钮触发）：激活时通知 AppRoot 把播放栏沉下去，给选择底栏让位
    var selecting by remember { mutableStateOf(false) }
    val selectedMids = remember { mutableStateListOf<String>() }
    var downloadProgress by remember { mutableStateOf<String?>(null) }
    rememberSelectionBusSync(selecting)

    // 「更多」弹窗
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var formatTrack by remember { mutableStateOf<Track?>(null) }

    suspend fun fetchPage(offset: Int, num: Int): PlaylistApi.Page? {
        // 每页最多试 3 次，失败后退避重试（0.6s / 1.2s）；3 次都失败就放弃本轮，
        // 由底部重试入口续传。连续瞬时重试只会加重风控。
        repeat(3) { attempt ->
            runCatching { loadPage(offset, num) }
                .onSuccess { return it }
                .onFailure {
                    if (attempt == 2) android.util.Log.w("TrackList", "page@$offset failed", it)
                    else kotlinx.coroutines.delay(600L * (attempt + 1))
                }
        }
        return null
    }

    val loadAll = remember(title) {
        suspend {
            loaded = false
            failed = false
            if (endlessMode) {
                // 无限流：首屏只取一块，剩下交给滚动触发的追加。
                // 首屏失败**或为空**都按可重试失败处理（exhausted 只在"成功但全重复"时出现）
                loadingMore = true
                val page = fetchPage(0, 100)
                loadingMore = false
                if (page == null || page.songs.isEmpty()) {
                    failedAt = 0
                } else {
                    failedAt = null
                    tracks = tracks + page.songs.filter { new -> tracks.none { it.mid == new.mid } }
                }
            } else {
                val pageSize = 100
                var offset = 0
                var guard = 0
                while (guard++ < 50) {
                    loadingMore = true
                    val page = fetchPage(offset, pageSize)
                    loadingMore = false
                    if (page == null) {
                        failedAt = offset
                        break
                    }
                    failedAt = null
                    total = page.total ?: total
                    tracks = tracks + page.songs.filter { new -> tracks.none { it.mid == new.mid } }
                    val next = runCatching { page.advanceFrom(offset) }.getOrNull()
                    val t = total
                    if (next == null || (next <= offset && t != null && offset < t)) {
                        failedAt = offset
                        break
                    }
                    if (next <= offset || (t != null && next >= t)) break
                    offset = next
                    if (t == null && page.nextOffset == null && page.songs.size < pageSize) break
                }
                if (guard > 50) pausedAt = offset
            }
            loaded = true
            if (tracks.isNotEmpty() && failedAt == null) TrackListCache.put(cacheKey, tracks, total, pausedAt)
            LikedStore.refresh()
        }
    }

    // 从失败点继续翻页（底部重试入口用）。
    val resumeFrom: (Int) -> Unit = { fromOffset ->
        scope.launch {
            pausedAt = null // 续传开始先撤「暂停」标记，结束时按实际结果再挂
            val pageSize = 100
            var offset = fromOffset
            var guard = 0
            while (guard++ < 50) {
                loadingMore = true
                val page = fetchPage(offset, pageSize)
                loadingMore = false
                if (page == null) { failedAt = offset; break }
                failedAt = null
                total = page.total ?: total
                tracks = tracks + page.songs.filter { new -> tracks.none { it.mid == new.mid } }
                val next = runCatching { page.advanceFrom(offset) }.getOrNull()
                val t = total
                if (next == null || (next <= offset && t != null && offset < t)) {
                    failedAt = offset
                    break
                }
                if (next <= offset || (t != null && next >= t)) break
                offset = next
                if (t == null && page.nextOffset == null && page.songs.size < pageSize) break
            }
            if (guard > 50) pausedAt = offset
            if (tracks.isNotEmpty() && failedAt == null) TrackListCache.put(cacheKey, tracks, total, pausedAt)
        }
    }

    // 无限流：滚动到底部（footer 可见）就追加下一块，直到数据源枯竭
    suspend fun appendNext() {
        if (!endlessMode || exhausted || failedAt != null) return
        loadingMore = true
        val page = fetchPage(tracks.size, 100)
        loadingMore = false
        if (page == null) {
            failedAt = tracks.size
            return
        }
        failedAt = null
        val fresh = page.songs.filter { new -> tracks.none { it.mid == new.mid } }
        if (fresh.isEmpty()) {
            exhausted = true
            return
        }
        tracks = tracks + fresh
        TrackListCache.put(cacheKey, tracks, total)
    }

    // 有缓存就不再请求；无缓存才翻页取全。
    LaunchedEffect(cacheKey) {
        if (!loaded) loadAll()
    }

    // 失败后「继续往下滑重新加载」：底部失败条进入可见区就自动续传一次。
    var retriedVisible by remember { mutableStateOf(false) }
    val footerVisible by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(footerVisible, failedAt, pausedAt, exhausted, endlessMode) {
        if (footerVisible && loaded && !loadingMore) {
            if (failedAt != null) {
                if (retriedVisible) return@LaunchedEffect
                retriedVisible = true
                failedAt?.let { offset -> resumeFrom(offset) }
            } else if (pausedAt != null) {
                // 暂停续传：滑到底部也自动续一次，节流与失败重试同一套（retriedVisible）
                if (retriedVisible) return@LaunchedEffect
                retriedVisible = true
                pausedAt?.let { offset -> resumeFrom(offset) }
            } else if (endlessMode && !exhausted) {
                appendNext()
            }
        }
        if (!footerVisible) retriedVisible = false
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val list = tracks
        // LazyColumn 恒渲染：顶栏（返回键）属于页面本身，空列表/加载中也不能消失
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().doubleTapToTop(listState),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 0.dp,   // 状态栏内缩由顶栏（ListTopBarRow）自己负责，这里不再叠加
                // 底部留白：按播放栏实测高度（可视化模式面板更高），最后一行才不会被压住。
                bottom = barSpace,
            ),
        ) {
            // 顶栏和主页一样是页面的一部分：往上滑就跟着滚走，不再悬浮折叠
            item(key = "topbar") {
                ListTopBarRow(title, onBack) {
                    // 下载钮：普通态是下载图标，选择模式中变为 ✕（退出选择）
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
            when {
                !loaded -> item(key = "state") {
                    Box(
                        Modifier.fillMaxWidth().height(520.dp),
                        contentAlignment = Alignment.Center,
                    ) { LoadingBox() }
                }
                list.isEmpty() -> item(key = "state") {
                    Box(
                        Modifier.fillMaxWidth().height(520.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (failed) "加载失败，请稍后重试" else "这里还没有歌曲",
                            color = colors.textTertiary, fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    // 逐行 item：几百行的歌单也能虚拟化（整块一个 item 会随行数线性变卡）
                    itemsIndexed(list, key = { _, t -> t.mid }) { i, t ->
                        val position = when {
                            list.size == 1 -> BlockSlice.Single
                            i == 0 -> BlockSlice.Head
                            i == list.lastIndex -> BlockSlice.Tail
                            else -> BlockSlice.Middle
                        }
                        BlockRowSurface(position = position) {
                            Column {
                                if (i > 0) RowDivider()
                                TrackRow(
                                    track = t,
                                    onPlay = {
                                        if (selecting) return@TrackRow
                                        playQueue(ctx, list, i)
                                    },
                                    onMore = { moreTrack = t },
                                    liked = likedIds.contains(t.songId),
                                    onLike = { toggleLike(ctx, t, likedIds.contains(t.songId)) },
                                    selecting = selecting,
                                    selected = t.mid in selectedMids,
                                    downloaded = downloadedMap.containsKey(t.mid),
                                    onToggleSelect = {
                                        if (t.mid in selectedMids) selectedMids.remove(t.mid)
                                        else selectedMids.add(t.mid)
                                    },
                                )
                                if (i == 0) Spacer(Modifier.height(6.dp))
                                if (i == list.lastIndex) Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                    item {
                        ListFooter(
                            loadedCount = list.size,
                            total = total,
                            loading = loadingMore,
                            failedAt = failedAt,
                            pausedAt = pausedAt,
                            exhausted = exhausted,
                            onRetry = {
                                val from = failedAt ?: pausedAt
                                from?.let(resumeFrom)
                            },
                        )
                    }
                }
            }
        }
        // 列表上下边缘的渐隐（与主页一致）
        if (listState.canScrollBackward) TopEdgeFade()
        VerticalEdgeFades(state = listState, height = 30.dp, top = false)

        // 选择模式：底部工具栏上升出现（全选/反选/下载三图标钮）；退出时下滑消失。
        // 播放栏由 AppRoot 监听 SelectionBus 同步沉降让位。
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
                    list.filter { !downloadedMap.containsKey(it.mid) }
                        .forEach { selectedMids.add(it.mid) }
                },
                onInvert = {
                    val invert = list.filter { !downloadedMap.containsKey(it.mid) }
                        .map { it.mid to (it.mid !in selectedMids) }
                    selectedMids.clear()
                    invert.forEach { (mid, sel) -> if (sel) selectedMids.add(mid) }
                },
                onDownload = {
                    scope.launch {
                        val picks = list.filter { it.mid in selectedMids }
                        picks.forEachIndexed { idx, t ->
                            downloadProgress = "${idx + 1}/${picks.size}"
                            runCatching { Downloader.download(ctx, t) }
                        }
                        downloadProgress = null
                        selectedMids.clear()
                        selecting = false
                    }
                },
            )
        }
    }

    // 「更多」弹窗（tracks 是已加载全量）
    moreTrack?.let { t ->
        val all = tracks
        TrackMoreDialog(
            track = t,
            onDismiss = { moreTrack = null },
            onPlay = {
                val idx = all.indexOfFirst { it.mid == t.mid }.coerceAtLeast(0)
                if (all.isNotEmpty()) playQueue(ctx, all, idx)
            },
            onPlayNext = { PlayerHost.playNext(t); toastMain(ctx, "已加入下一首播放") },
            onInfo = { infoTrack = t },
            onOpenAlbum = { onOpenAlbum(t) },
            onFormats = { formatTrack = t },
        )
    }
    infoTrack?.let { TrackInfoDialog(it) { infoTrack = null } }
    formatTrack?.let { TrackFormatsDialog(it) { formatTrack = null } }
}

/**
 * 详情列表的进程内缓存（歌单/专辑/我喜欢/电台共用）。
 *
 * 缓存**已取全**或因单轮上限暂停（[put] 的 pausedAt）的结果；
 * 超过 [MAX] 个条目时整体清空（条目都是小列表，粗暴但要够用）。
 * 退出登录时由设置页调用 [clear]（收藏类数据与账号绑定）。
 */
object TrackListCache {
    private const val MAX = 12

    /** 曲目 + 总数 + 上次滚动位置（首项下标、像素偏移）+ 单轮上限暂停的续传点。 */
    private class Entry(
        val tracks: List<Track>,
        val total: Int?,
        var scrollIndex: Int = 0,
        var scrollOffset: Int = 0,
        val pausedAt: Int? = null,
    )

    private val map = HashMap<String, Entry>()

    fun get(key: String): List<Track>? = synchronized(map) { map[key]?.tracks }

    /** 总数。 */
    fun total(key: String): Int? = synchronized(map) { map[key]?.total }

    /** 单轮上限暂停的续传点（null=已取全或无缓存）。 */
    fun pausedAt(key: String): Int? = synchronized(map) { map[key]?.pausedAt }

    /** 上次滚动位置（下标、偏移）。 */
    fun scroll(key: String): Pair<Int, Int> =
        synchronized(map) { map[key]?.let { it.scrollIndex to it.scrollOffset } ?: (0 to 0) }

    fun put(key: String, tracks: List<Track>, total: Int?, pausedAt: Int? = null) {
        synchronized(map) {
            val old = map[key]
            if (map.size >= MAX && old == null) map.clear()
            map[key] = Entry(tracks, total, old?.scrollIndex ?: 0, old?.scrollOffset ?: 0, pausedAt)
        }
    }

    fun saveScroll(key: String, index: Int, offset: Int) {
        synchronized(map) { map[key]?.let { it.scrollIndex = index; it.scrollOffset = offset } }
    }

    fun clear() = synchronized(map) { map.clear() }
}

/** 列表底部状态：翻页中 / 已取全 / 失败（点击或滑到底重试）。 */
@Composable
private fun ListFooter(
    loadedCount: Int,
    total: Int?,
    loading: Boolean,
    failedAt: Int?,
    pausedAt: Int? = null,
    exhausted: Boolean = false,
    onRetry: () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent,
                )
                Spacer(Modifier.size(8.dp))
                Text("加载中…", color = colors.textTertiary, fontSize = 12.sp)
            }
            failedAt != null -> Row(
                Modifier.flatPressable(cornerRadius = 14.dp, onClick = onRetry).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("加载失败，点击重试", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            // 单轮上限的正常截断：不是失败，提供「继续加载」入口（滑到底也会自动续传）
            pausedAt != null -> Row(
                Modifier.flatPressable(cornerRadius = 14.dp, onClick = onRetry).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已加载 $loadedCount 首 · 点击继续加载", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            failedAt == null && exhausted -> Text("没有更多了", color = colors.textTertiary, fontSize = 12.sp)
            else -> {
                val t = total
                Text(
                    if (t != null && t > 0) "已加载 $loadedCount / 共 $t 首" else "已加载 $loadedCount 首",
                    color = colors.textTertiary, fontSize = 12.sp,
                )
            }
        }
    }
}

/** 批量列表里的红心点击走 common 的 [com.neumusic.player.ui.common.toggleLike]。 */


/** 列表页的顶栏行：放在列表第一项里，随内容一起滚走（与主页一致，不再悬浮折叠）。 */
@Composable
private fun ListTopBarRow(title: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val colors = LocalShadeColors.current
    // 状态栏 + 渐隐线偏移(8dp) + 12dp：与主页首个内容（topInset + 12dp）同高
    val topContentTop = rememberTopContentInset(extra = 12.dp)
    Row(
        // 顶栏自己带内缩：状态栏 + TOP_FADE_BELOW_STATUS_BAR(8dp) + 12dp，
        // 与主页的 contentPadding(topInset + 12dp) 完全同高 —— 首页与列表页的
        // 「首个内容位置 / 渐隐界线视觉高度」才对得上（用户按页面比过）。
        Modifier
            .fillMaxWidth()
            .padding(top = topContentTop, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp)
                .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "返回",
                tint = colors.accent, modifier = Modifier.size(19.dp),
            )
        }
        Text(
            title, Modifier.weight(1f),
            color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

/** 收藏的歌单列表（首页「更多」）。 */
@Composable
fun PlaylistsScreen(onBack: () -> Unit, onOpen: (PlaylistItem) -> Unit) {
    val barSpace = rememberPlayerBarSpace()
    val colors = LocalShadeColors.current
    val playlistsState = rememberLazyListState()
    var items by remember { mutableStateOf<List<PlaylistItem>?>(null) }
    LaunchedEffect(Unit) { items = runCatching { PlaylistApi.favPlaylists() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        val list = items ?: return@Column LoadingBox()
        if (list.isEmpty()) return@Column EmptyBox("还没有收藏的歌单")
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = playlistsState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = barSpace),
            ) {
                item(key = "topbar") { ListTopBarRow("收藏的歌单", onBack) }
                item {
                    TrackListBlock {
                        list.forEachIndexed { i, p ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = p.name,
                                subtitle = "${p.songnum} 首",
                                logo = p.logo,
                                onClick = { onOpen(p) },
                            )
                        }
                    }
                }
            }
            if (playlistsState.canScrollBackward) TopEdgeFade()
            VerticalEdgeFades(state = playlistsState, height = 26.dp, top = false)
        }
    }
}

/** 收藏的专辑列表。 */
@Composable
fun AlbumsScreen(onBack: () -> Unit, onOpen: (AlbumItem) -> Unit) {
    val barSpace = rememberPlayerBarSpace()
    val colors = LocalShadeColors.current
    val albumsState = rememberLazyListState()
    var items by remember { mutableStateOf<List<AlbumItem>?>(null) }
    LaunchedEffect(Unit) { items = runCatching { PlaylistApi.favAlbums() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        val list = items ?: return@Column LoadingBox()
        if (list.isEmpty()) return@Column EmptyBox("还没有收藏的专辑")
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = albumsState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = barSpace),
            ) {
                item(key = "topbar") { ListTopBarRow("收藏的专辑", onBack) }
                item {
                    TrackListBlock {
                        list.forEachIndexed { i, a ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = a.name,
                                subtitle = "${a.songnum} 首",
                                logo = a.logo,
                                onClick = { onOpen(a) },
                            )
                        }
                    }
                }
            }
            if (albumsState.canScrollBackward) TopEdgeFade()
            VerticalEdgeFades(state = albumsState, height = 26.dp, top = false)
        }
    }
}

/** 区块内的一行（封面平贴 + 标题/副标题）。整块的凸起由 TrackListBlock 提供。 */
@Composable
private fun ListRow(title: String, subtitle: String, logo: String, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Row(
        Modifier
            .fillMaxWidth()
            // 位于 TrackListBlock（凸起）内部，所以用"平"面按压，不能再凸起。
            .flatPressable(cornerRadius = 16.dp, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(url = logo, size = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoadingBox() {
    val colors = LocalShadeColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
    }
}

@Composable
fun EmptyBox(text: String) {
    val colors = LocalShadeColors.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.textTertiary, fontSize = 14.sp)
    }
}
