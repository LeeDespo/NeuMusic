package com.neumusic.player.ui.singer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.SingerApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.BlockSlice
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.rememberPlayerBarSpace
import com.neumusic.player.ui.common.SingerAvatarFrame
import com.neumusic.player.ui.common.rememberTopContentInset
import com.neumusic.player.ui.common.BlockRowSurface
import com.neumusic.player.ui.common.CoverPlaceholder
import com.neumusic.player.ui.common.MediaCard
import com.neumusic.player.ui.common.RowDivider
import com.neumusic.player.ui.common.TopEdgeFade
import com.neumusic.player.ui.common.TrackFormatsDialog
import com.neumusic.player.ui.common.TrackInfoDialog
import com.neumusic.player.ui.common.TrackMoreDialog
import com.neumusic.player.ui.common.TrackRow
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.common.cardGridItems
import com.neumusic.player.ui.common.gridColumns
import com.neumusic.player.ui.common.playQueue
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.common.toggleLike
import com.neumusic.player.ui.home.LoadingBox
import kotlinx.coroutines.launch

/**
 * 歌手页（全屏覆盖层，升起动画与播放页同款；搜索页/主页歌手卡进入走「飞位」转场）。
 *
 * 结构（用户 2026-09-30 规格）：
 * - 最上：歌手头像居中、方形、凸起画框；
 * - 下面是**平的**两行居中文字（不再用凹陷标签）：第一行歌手名，第二行小字
 *   「N 首歌 · N 张专辑」（数值来自搜索解析；关注列表等无计数的来源进来后自动按名字补齐）；
 * - 再下两个**平的**分段控制器，中间一道「|」：左 = 歌曲 | 专辑，右 = 最新 | 热门；选中项为凹陷圆角矩形；
 * - 内容：歌曲列表或专辑卡片网格——**同一个 LazyColumn**（专辑按列数分块成行），
 *   切换种类/排序时滚动位置天然保持统一（用户要求，不再各算各的）。
 * - 边缘渐隐：顶部盖状态栏、范围加大（[TopEdgeFade]），底部与主页同款。
 *
 * 数据（端点实测见 [SingerApi] 注释）：歌曲与专辑都支持分页；排序切换复用已取内容缓存。
 */
@Composable
fun SingerScreen(
    singer: com.neumusic.player.ui.Nav.Singer,
    onBack: () -> Unit,
    onOpenAlbum: (mid: String, name: String) -> Unit,
    /** 头像画框的根坐标上报：歌手卡「飞位」转场以此为落点（AppRoot 用）。 */
    onAvatarBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val barSpace = rememberPlayerBarSpace()
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 关注列表等来源没有歌数/专辑数：为 0 时按名字解析补齐（只补一次）
    var info by remember { mutableStateOf(singer) }
    LaunchedEffect(singer.mid, singer.name) {
        if (singer.songNum <= 0 && singer.albumNum <= 0) {
            runCatching { SingerApi.resolve(singer.name) }.onSuccess { s ->
                if (s != null && s.mid.isNotEmpty()) {
                    info = com.neumusic.player.ui.Nav.Singer(s.mid, s.name, s.pic, s.songNum, s.albumNum)
                }
            }
        }
    }

    // ── 内容状态：按排序键缓存（切回去不用重拉）。
    // 取数由 LaunchedEffect 驱动：切换键会取消上一次取数协程 + finally 复位 fetching——
    // 早前版本用共享 loading 互斥，切换时新键的加载被在途请求挡掉（"切过去是空的"实测踩过）。
    // 列表字段用 mutableStateOf，追加时逐帧可观察。──
    var kindAlbums by remember { mutableStateOf(false) }          // false=歌曲 true=专辑
    var orderNew by remember { mutableStateOf(true) }             // true=最新(2) false=热门(1)
    var fetching by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    val apiOrder = if (orderNew) SingerApi.ORDER_NEW else SingerApi.ORDER_HOT

    // 每个排序键一份数据；字段用 mutableStateOf，追加/首次加载都能驱动重组
    val store = remember { HashMap<Int, KeyedData>() }

    LaunchedEffect(kindAlbums, orderNew) {
        val st = store.getOrPut(apiOrder) { KeyedData() }
        if (!st.loaded) {
            try {
                fetching = true
                if (!kindAlbums) {
                    runCatching { SingerApi.songs(singer.mid, apiOrder, 0, 100) }.onSuccess { p ->
                        st.songs = p.songs
                        p.total?.let { st.songsTotal = it }
                        st.loaded = true
                    }
                } else {
                    runCatching { SingerApi.albums(singer.mid, apiOrder, 0, 30) }.onSuccess { p ->
                        st.albums = p.albums
                        p.total?.let { st.albumsTotal = it }
                        st.loaded = true
                    }
                }
            } finally {
                fetching = false
            }
        }
    }

    fun loadMore() {
        if (fetching || loadingMore) return
        val st = store[apiOrder] ?: return
        scope.launch {
            loadingMore = true
            try {
                if (!kindAlbums) {
                    if (st.songsTotal <= 0 || st.songs.size < st.songsTotal) {
                        runCatching { SingerApi.songs(singer.mid, apiOrder, st.songs.size, 100) }.onSuccess { p ->
                            if (p.songs.isNotEmpty()) {
                                st.songs = st.songs + p.songs
                                p.total?.let { st.songsTotal = it }
                            }
                        }
                    }
                } else {
                    if (st.albumsTotal <= 0 || st.albums.size < st.albumsTotal) {
                        runCatching { SingerApi.albums(singer.mid, apiOrder, st.albums.size, 30) }.onSuccess { p ->
                            if (p.albums.isNotEmpty()) {
                                st.albums = st.albums + p.albums
                                p.total?.let { st.albumsTotal = it }
                            }
                        }
                    }
                }
            } finally {
                loadingMore = false
            }
        }
    }

    val cur = store[apiOrder]
    val songs = cur?.songs ?: emptyList()
    val albums = cur?.albums ?: emptyList()

    // 「更多」弹窗状态（放在 LazyColumn 外：LazyListScope 不是 composable 作用域）
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var formatTrack by remember { mutableStateOf<Track?>(null) }
    val likedIds by LikedStore.liked.collectAsState()
    val downloadedMap by DownloadStore.records.collectAsState()

    val screenW = LocalConfiguration.current.screenWidthDp.dp

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val listState = rememberLazyListState()
        // 滚到底自动翻页
        val atBottom by remember {
            derivedStateOf {
                val l = listState.layoutInfo
                val last = l.visibleItemsInfo.lastOrNull()
                last != null && last.index == l.totalItemsCount - 1
            }
        }
        LaunchedEffect(atBottom) { if (atBottom && !fetching) loadMore() }

        // 单一 LazyColumn：歌曲行与专辑卡片行共用，滚动位置跨切换统一
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = barSpace),
        ) {
            item(key = "header") {
                Column(
                    // 与主页/列表页同高：状态栏 + 渐隐线偏移(8dp) + 12dp
                    Modifier.fillMaxWidth().padding(top = rememberTopContentInset(extra = 12.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 返回钮靠左（随内容滚走，与列表页一致）
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)) {
                        Box(
                            Modifier.size(42.dp)
                                .shadePressable(cornerRadius = 21.dp, offset = 4.dp, blur = 6.dp, onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.accent, modifier = Modifier.size(19.dp))
                        }
                    }
                    // 方形头像 + 凸起画框（SingerAvatarFrame，216dp = 这套规格的基准尺寸，
                    // 搜索页歌手卡 / 主页关注歌手卡都按同一比例的等比换算，飞位交接才无缝）。
                    Box(Modifier.onGloballyPositioned { onAvatarBounds(it.boundsInRoot()) }) {
                        SingerAvatarFrame(
                            pic = info.pic,
                            desc = "歌手头像",
                            modifier = Modifier.size(216.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // 平的两行居中文字（不再凹陷标签）：歌手名 / N首歌 · N张专辑
                    Text(
                        info.name,
                        color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${info.songNum} 首歌 · ${info.albumNum} 张专辑",
                        color = colors.textSecondary, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    // 双分段控制器（平的）：左 歌曲|专辑，右 最新|热门，中间「|」
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SegPair(listOf("歌曲", "专辑"), if (kindAlbums) 1 else 0) { kindAlbums = it == 1 }
                        Spacer(Modifier.weight(1f))
                        Text("|", color = colors.textTertiary.copy(alpha = 0.55f), fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        SegPair(listOf("最新", "热门"), if (orderNew) 0 else 1) { orderNew = it == 0 }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            when {
                fetching && songs.isEmpty() && albums.isEmpty() -> item(key = "state") {
                    Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) { LoadingBox() }
                }
                !fetching && (if (kindAlbums) albums.isEmpty() else songs.isEmpty()) -> item(key = "state") {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Text(if (kindAlbums) "还没有专辑" else "还没有歌曲", color = colors.textTertiary, fontSize = 14.sp)
                    }
                }
                kindAlbums -> cardGridItems(
                    items = albums,
                    columns = gridColumns(screenW, minCard = 170.dp),
                    key = { it.mid },
                ) { album ->
                    MediaCard(
                        logo = album.logo,
                        title = album.name,
                        count = album.songnum,
                        line2 = album.singerName.ifEmpty { singer.name },
                        onClick = { onOpenAlbum(album.mid, album.name) },
                    )
                }
                else -> itemsIndexed(songs, key = { _, t -> t.mid }) { i, t ->
                    val position = when {
                        songs.size == 1 -> BlockSlice.Single
                        i == 0 -> BlockSlice.Head
                        i == songs.lastIndex -> BlockSlice.Tail
                        else -> BlockSlice.Middle
                    }
                    BlockRowSurface(
                        position = position,
                        // 切「歌曲|专辑」/「最新|热门」时整行淡入淡出（与搜索页卡片行同款）
                        modifier = Modifier.animateItem(
                            fadeInSpec = androidx.compose.animation.core.tween(170),
                            fadeOutSpec = androidx.compose.animation.core.tween(120),
                        ),
                    ) {
                        Column {
                            if (i > 0) RowDivider()
                            TrackRow(
                                track = t,
                                onPlay = { playQueue(ctx, songs, i) },
                                onMore = { moreTrack = t },
                                liked = likedIds.contains(t.songId),
                                onLike = { toggleLike(ctx, t, likedIds.contains(t.songId)) },
                                downloaded = downloadedMap.containsKey(t.mid),
                            )
                            if (i == 0) Spacer(Modifier.height(6.dp))
                            if (i == songs.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            if (!fetching && !(if (kindAlbums) albums.isEmpty() else songs.isEmpty())) {
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
                        } else {
                            val total = if (kindAlbums) cur?.albumsTotal ?: -1 else cur?.songsTotal ?: -1
                            val loaded = if (kindAlbums) albums.size else songs.size
                            val unit = if (kindAlbums) "张" else "首"
                            Text(
                                if (total > 0) "已加载 $loaded / 共 $total $unit" else "已加载 $loaded $unit",
                                color = colors.textTertiary, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        // 顶部渐隐：盖过状态栏、范围加大；底部与主页同款
        if (listState.canScrollBackward) TopEdgeFade()
        VerticalEdgeFades(state = listState, height = 30.dp, top = false)
    }

    // 「更多」弹窗（歌曲列表）
    moreTrack?.let { t ->
        TrackMoreDialog(
            track = t,
            onDismiss = { moreTrack = null },
            onPlay = { playQueue(ctx, songs, songs.indexOfFirst { it.mid == t.mid }.coerceAtLeast(0)) },
            onPlayNext = { PlayerHost.playNext(t); toastMain(ctx, "已加入下一首播放") },
            onInfo = { infoTrack = t },
            onOpenAlbum = { onOpenAlbum(t.albumMid, t.albumName) },
            onFormats = { formatTrack = t },
        )
    }
    infoTrack?.let { TrackInfoDialog(it) { infoTrack = null } }
    formatTrack?.let { TrackFormatsDialog(it) { formatTrack = null } }
}

/** 平的分段控制器：选项并排，选中项为凹陷圆角矩形，未选中为纯文字。 */
@Composable
private fun SegPair(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalShadeColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .then(if (sel) Modifier.shadeInset(cornerRadius = 12.dp, offset = 2.dp, blur = 4.dp) else Modifier)
                    .flatPressable(cornerRadius = 12.dp) { onSelect(i) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = if (sel) colors.accent else colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 一个排序键（最新/热门）下的已取数据。字段全部 mutableStateOf：
 * 追加翻页、首次加载完成都能直接驱动重组（早前用普通 HashMap 装列表，写进去不通知，
 * 靠 loading 翻转"顺便"触发重组——脆弱且是切换空白问题的另一半根源）。
 */
private class KeyedData {
    var songs by androidx.compose.runtime.mutableStateOf<List<Track>>(emptyList())
    var albums by androidx.compose.runtime.mutableStateOf<List<AlbumItem>>(emptyList())
    var songsTotal by androidx.compose.runtime.mutableIntStateOf(-1)
    var albumsTotal by androidx.compose.runtime.mutableIntStateOf(-1)
    var loaded by androidx.compose.runtime.mutableStateOf(false)
}
