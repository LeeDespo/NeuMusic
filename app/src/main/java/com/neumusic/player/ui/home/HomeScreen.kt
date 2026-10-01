package com.neumusic.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import com.neumusic.player.data.RecommendStore
import com.neumusic.player.data.AppLog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neumusic.player.data.AlbumItem
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.FollowSinger
import com.neumusic.player.data.api.UserApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.ApiCache
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.RadioApi
import com.neumusic.player.data.RadioGroup
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.TopEdgeFade
import com.neumusic.player.ui.common.rememberTopContentInset
import com.neumusic.player.ui.common.rememberPlayerBarSpace
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.CoverPlaceholder
import com.neumusic.player.ui.common.SingerAvatarFrame
import com.neumusic.player.ui.common.toastMain
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import com.neumusic.player.data.HomeCache
import com.neumusic.player.data.LikedStore
import com.neumusic.player.ui.common.cardTap
import com.neumusic.player.ui.common.playQueue
import com.neumusic.player.ui.common.toggleLike
import com.neumusic.player.ui.common.doubleTapToTop
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import com.neumusic.player.ui.common.HorizontalEdgeFades
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.Nav
import com.neumusic.player.ui.NavRequest
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 随机问候语池（顶栏空间有限，**不带用户名**——用户要求）。
 * `greetingForHour` 按时段优先取贴切的，60% 概率时段语、40% 通用池，避免死板。
 */
val GREETINGS = listOf(
    "你好！", "早上好", "下午好", "晚上好", "夜深了，听首轻的？", "欢迎回来",
    "今天想听点什么？", "外面吵的话，戴耳机吧", "好久不见", "来点新歌？",
    "又是听歌的一天", "心情不好就多听两首", "午后的歌最安逸", "深夜电台已就绪",
    "要不要试试随机播放？", "歌单该更新了", "这里永远有歌等你",
)

/** 按当前时段挑一条问候语。 */
private fun greetingForHour(hour: Int): String {
    val slot = when (hour) {
        in 5..8 -> listOf("早上好", "早上好，听点轻的？", "清晨第一首")
        in 9..11 -> listOf("上午好", "上午好，来点节奏？")
        in 12..13 -> listOf("中午好", "午后困了？听点提神的")
        in 14..17 -> listOf("下午好", "下午好，来首歌")
        in 18..22 -> listOf("晚上好", "晚上好，放松一下")
        else -> listOf("夜深了，听首轻的？", "还没睡？陪你一会儿", "夜深了")
    }
    return if ((0..9).random() < 6) slot.random() else GREETINGS.random()
}

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDest: (NavRequest) -> Unit,
) {
    val topInset = rememberTopContentInset()
    val barSpace = rememberPlayerBarSpace()
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var greeting by remember { mutableStateOf("你好！") }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var radioGroups by remember { mutableStateOf<List<RadioGroup>>(emptyList()) }
    var likedCount by remember { mutableStateOf<Int?>(null) }
    var albumsLoaded by remember { mutableStateOf(false) }
    var radioLoaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ── 推荐歌曲（主页顶部，无标题）：RecommendStore 预缓冲 5 首（曲目+介绍，落盘持久）——
    // 打开应用推荐即显、刷新即时换歌，都不用等。缓冲不足时后台补货。──
    val recQueue by RecommendStore.queue.collectAsState()
    val rec = recQueue.firstOrNull()
    var recJoiningRadio by remember { mutableStateOf(false) }
    var followedSingers by remember { mutableStateOf<List<FollowSinger>?>(null) }

    // 点播放 = **接入猜你喜欢的播放**（用推荐歌做首曲，后面跟一批电台曲目作队列），
    // 而不是把单曲丢进一个一首歌的队列（那样播完循环，用户实测指出）。
    suspend fun playRecommended(track: Track) {
        if (recJoiningRadio) return
        recJoiningRadio = true
        try {
            val station = radioGroups.map { it.stations }.flatten()
                .firstOrNull { st -> st.title == "猜你喜欢" }
            val queue = if (station != null) {
                listOf(track) + runCatching {
                    RadioApi.nextTracks(station.id, firstplay = false, exclude = setOf(track.mid), batches = 4)
                }.onFailure { AppLog.w("Recommend", "join radio failed", it) }
                    .getOrDefault(emptyList())
            } else {
                listOf(track)
            }
            playQueue(ctx, queue, 0)
        } finally {
            recJoiningRadio = false
        }
    }

    LaunchedEffect(Unit) {
        val logged = Prefs.credential != null

        // ── 1) 磁盘快照立即上屏（冷启动不再白屏等网络）──
        HomeCache.playlists()?.let { playlists = it }
        HomeCache.albums()?.let { albums = it }
        HomeCache.radioGroups()?.let { radioGroups = it }
        HomeCache.likedCount()?.let { likedCount = it }

        // ── 2) 三栏网络刷新最先发（stale-while-revalidate：回来覆盖 + 写两级缓存）──
        // 早前这里先同步 await 昵称接口，三栏要等它回来才发——主页"只有我喜欢"的直接原因。
        scope.launch {
            runCatching { ApiCache.getOrPut("favPlaylists") { PlaylistApi.favPlaylists() } }
                .onSuccess {
                    playlists = it
                    HomeCache.savePlaylists(it)
                }
                .onFailure { if (error == null && playlists.isEmpty()) error = it.message }
        }
        scope.launch {
            runCatching { ApiCache.getOrPut("favAlbums") { PlaylistApi.favAlbums() } }
                .onSuccess {
                    albums = it
                    albumsLoaded = true
                    HomeCache.saveAlbums(it)
                }
                .onFailure { albumsLoaded = true }   // 有快照就静默用旧的，不打扰
        }
        scope.launch {
            runCatching { ApiCache.getOrPut("radioGroups") { RadioApi.groups() } }
                .onSuccess {
                    radioGroups = it
                    radioLoaded = true
                    HomeCache.saveRadioGroups(it)
                }
                .onFailure { radioLoaded = true }
        }

        // ── 3) 问候语：不带用户名（顶栏放不下，用户要求），按时段随机 ──
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting = greetingForHour(hour)

        // ── 3.4) 推荐缓冲：电台分组就绪后告诉 store 猜你喜欢的电台 id，后台把缓冲补满 ──
        val station = radioGroups.map { it.stations }.flatten()
            .firstOrNull { st -> st.title == "猜你喜欢" }
        if (station != null) RecommendStore.stationId = station.id

        // ── 3.5) 关注的歌手（需登录 + euin）──
        scope.launch {
            followedSingers = if (Prefs.credential?.euin?.isNotEmpty() == true) {
                runCatching { UserApi.followSingers(0, 30).first }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

        // ── 4) 我喜欢全量刷新与数量（放最后，不跟三栏抢首批请求）──
        scope.launch { LikedStore.refresh() }
        scope.launch {
            runCatching { ApiCache.getOrPut("likedCount") { PlaylistApi.likedPage(0, 1).total } }
                .getOrNull()?.let { c ->
                    likedCount = c
                    HomeCache.saveLikedCount(c)
                }
        }
    }

    // 电台分组到位后触发推荐缓冲补货。必须挂页面级 scope：挂在 LaunchedEffect(radioGroups)
    // 上会被分组数据刷新中途取消（LeftCompositionCancellationException 实测）。
    LaunchedEffect(radioGroups) {
        if (radioGroups.isNotEmpty()) {
            radioGroups.map { it.stations }.flatten()
                .firstOrNull { st -> st.title == "猜你喜欢" }
                ?.let { RecommendStore.stationId = it.id }
            scope.launch { RecommendStore.refill() }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.background).doubleTapToTop(listState),
        // 左右只留 2dp：卡片行的"留白 + 阴影空间"由 [HomeCardRow] 自管（全宽视口），
        // 否则首尾卡片的阴影会被行视口裁掉（用户反馈的"第一张左侧/最后一张右侧截断"）。
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = topInset + 12.dp, bottom = barSpace),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { GreetingHeader(greeting, onOpenSearch, onOpenSettings) }

        // 推荐歌曲（无标题，用户 2026-09-30 规格；缓冲即显）
        item {
            val current = rec
            if (current != null) {
                val t = current.track
                // 必须 collectAsState：读 .value 不会订阅，喜欢/播放态变化时按钮不会更新
                // （用户实测"点喜欢按钮不变"）。
                val playingNow by PlayerHost.current.collectAsState()
                val isPlaying by PlayerHost.isPlaying.collectAsState()
                val likedNow by LikedStore.liked.collectAsState()
                val playingThis = playingNow?.mid == t.mid && isPlaying
                RecommendCard(
                    entry = current,
                    liked = t.songId > 0L && likedNow.contains(t.songId),
                    playingThis = playingThis,
                    onPlayPause = {
                        if (playingNow?.mid == t.mid) PlayerHost.toggle()
                        else scope.launch { playRecommended(t) }
                    },
                    onLike = { toggleLike(ctx, t, likedNow.contains(t.songId)) },
                    onRefresh = {
                        RecommendStore.advance()
                        scope.launch {
                            kotlinx.coroutines.delay(400)
                            if (RecommendStore.queue.value.size < RecommendStore.CAP) RecommendStore.refill()
                        }
                    },
                )
            }
        }

        item {
            Section(
                title = "收藏的歌单",
                onMore = { onOpenDest(NavRequest(Nav.Playlists)) },
                // 「我喜欢」始终在，所以这一栏永远不为空；未登录时只显示它。
                empty = false,
                emptyText = "",
            ) {
                HomeCardRow {
                    // 「我喜欢」固定排第一（用户要求），再跟收藏的歌单。
                    item {
                        LikedCard(likedCount) { b -> onOpenDest(NavRequest(Nav.Liked, b)) }
                    }
                    items(playlists) { p ->
                        PlaylistCard(p) { b -> onOpenDest(NavRequest(Nav.PlaylistDetail(p.tid, p.name, p.songnum), b)) }
                    }
                }
            }
        }

        item {
            Section(
                title = "收藏的专辑",
                onMore = { onOpenDest(NavRequest(Nav.Albums)) },
                empty = albums.isEmpty(),
                emptyText = when {
                    !albumsLoaded -> "加载中…"
                    Prefs.credential == null -> "登录后显示"
                    else -> "还没有收藏的专辑"
                },
            ) {
                HomeCardRow {
                    items(albums) { a ->
                        AlbumCard(a) { b -> onOpenDest(NavRequest(Nav.AlbumDetail(a.mid, a.name, a.songnum), b)) }
                    }
                }
            }
        }

        // 关注的歌手（收藏的专辑下面，用户 2026-09-30 规格）：搜索页歌手卡同款设计、缩小一号
        item {
            val f = followedSingers
            Section(
                title = "关注的歌手",
                onMore = null,
                empty = f.isNullOrEmpty(),
                emptyText = when {
                    f == null -> "加载中…"
                    Prefs.credential == null -> "登录后显示"
                    else -> "还没有关注的歌手"
                },
            ) {
                HomeCardRow(spacing = 12.dp) {
                    items(f!!) { fs ->
                        SmallSingerCard(fs) { b ->
                            onOpenDest(NavRequest(Nav.Singer(fs.mid, fs.name, fs.pic), b))
                        }
                    }
                }
            }
        }

        // 电台：**每个分组都是一栏**（用户要求：取消「更多」入口，所有类别都做成栏目）。
        if (radioGroups.isEmpty()) {
            item {
                Section(
                    title = "电台",
                    onMore = null,
                    empty = true,
                    emptyText = if (radioLoaded) "电台加载失败，请稍后重试" else "加载中…",
                ) {}
            }
        } else {
            items(radioGroups, key = { "radio_${it.title}" }) { g ->
                Section(title = g.title, onMore = null, empty = false, emptyText = "") {
                    HomeCardRow(spacing = 12.dp) {
                        items(g.stations) { s ->
                            StationCard(s.title, s.picUrl) { b -> onOpenDest(NavRequest(Nav.RadioDetail(s.id, s.title), b)) }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
        // 顶部渐隐：内容从状态栏底下滚过时先溶进底色（与搜索/歌手页同一条线）
        if (listState.canScrollBackward) TopEdgeFade()
        VerticalEdgeFades(state = listState, height = 26.dp, top = false)
    }

    error?.let { msg ->
        LaunchedEffect(msg) { toastMain(ctx, msg) }
    }

}

/** 电台卡片（主页栏目的横向单元）。 */
@Composable
private fun StationCard(title: String, picUrl: String, onClick: (Rect) -> Unit) {
    val colors = LocalShadeColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.width(104.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .shadePressable(cornerRadius = 18.dp, offset = 5.dp, blur = 8.dp) { onClick(bounds) }
            .padding(6.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            AlbumArt(url = picUrl, size = 92.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title, color = colors.textPrimary, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 顶部：问候语 + 右上角搜索/设置图标按钮。 */
@Composable
private fun GreetingHeader(greeting: String, onSearch: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalShadeColors.current
    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NeuMusic", color = colors.textSecondary, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                greeting, color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(Icons.Filled.Search, "搜索", onSearch)
        Spacer(Modifier.width(10.dp))
        IconButton(Icons.Filled.Settings, "设置", onSettings)
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    val colors = LocalShadeColors.current
    Box(
        Modifier.size(46.dp).shadePressable(cornerRadius = 23.dp, offset = 5.dp, blur = 8.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = colors.accent, modifier = Modifier.size(20.dp))
    }
}

/** 栏：标题 + 右侧「更多」。 */
@Composable
private fun Section(
    title: String,
    onMore: (() -> Unit)?,
    empty: Boolean,
    emptyText: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalShadeColors.current
    Column {
        Row(
            Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onMore != null) {
                Box(
                    Modifier
                        .shadePressable(cornerRadius = 14.dp, offset = 3.dp, blur = 5.dp, onClick = onMore)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("更多", color = colors.accent, fontSize = 12.sp)
                }
            }
        }
        if (empty) Text(emptyText, color = colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp)) else content()
    }
}

/**
 * 「我喜欢」卡片：固定排在「收藏的歌单」第一位。
 * 与其它卡片同样是**凸起卡片**，内部只有无背景的爱心图标（不构成凸起套凸起）。
 */
@Composable
private fun LikedCard(count: Int?, onClick: (Rect) -> Unit) {
    val colors = LocalShadeColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.width(104.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .shadePressable(cornerRadius = 18.dp, offset = 5.dp, blur = 8.dp) { onClick(bounds) }
            .padding(6.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Favorite, contentDescription = null,
                tint = colors.accent, modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "我喜欢", color = colors.textPrimary, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (count != null) "$count 首" else "—",
            color = colors.textTertiary, fontSize = 10.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 歌单卡片：凸起底盘 + 封面；无封面用首字母占位；「我喜欢」用无背景爱心。 */
@Composable
private fun PlaylistCard(item: PlaylistItem, onClick: (Rect) -> Unit) {
    val colors = LocalShadeColors.current
    val isLiked = item.name == "我喜欢" || item.tid == 201L
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.width(104.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .shadePressable(cornerRadius = 18.dp, offset = 5.dp, blur = 8.dp) { onClick(bounds) }
            .padding(6.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            when {
                isLiked -> Icon(
                    Icons.Filled.Favorite, contentDescription = null,
                    tint = colors.accent, modifier = Modifier.size(44.dp),
                )
                item.logo.isNotEmpty() -> AsyncImage(
                    model = item.logo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)),
                )
                else -> CoverPlaceholder(text = item.name.take(1))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name, color = colors.textPrimary, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${item.songnum} 首", color = colors.textTertiary, fontSize = 10.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AlbumCard(item: AlbumItem, onClick: (Rect) -> Unit) {
    val colors = LocalShadeColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.width(104.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .shadePressable(cornerRadius = 18.dp, offset = 5.dp, blur = 8.dp) { onClick(bounds) }
            .padding(6.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            if (item.logo.isNotEmpty()) AsyncImage(
                model = item.logo, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)),
            ) else CoverPlaceholder(text = item.name.take(1))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name, color = colors.textPrimary, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${item.songnum} 首", color = colors.textTertiary, fontSize = 10.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 主页卡片行：全宽视口 + 两端 contentPadding。
 *
 * 视口**通到屏幕边缘**（卡片能滑到屏幕边再消失），首尾卡片离视口边缘留
 * 留白比阴影位移+模糊略大，阴影就不再被裁 —— 单张卡片时两侧
 * 阴影同样完整。两端再叠一层边缘渐隐，滑出去的卡片优雅淡入背景而非硬切。
 */
@Composable
private fun HomeCardRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    Box(modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        HorizontalEdgeFades(state = state, width = 20.dp)
    }
}

/**
 * 推荐歌曲卡（主页顶部，用户 2026-10-01 三次规格）：整块大圆角凸起承载卡，
 * **两列两行**的方正排布——
 * 左上 = 封面画框（占地最大，点封面 = 播放/暂停）；左下 = 歌名 + 歌手两行（超长跑马灯）；
 * 右上 = 歌曲详情（凹陷槽，**大小固定**、不随字数变化，文字超出可滚）；右下 = 三个动作钮。
 */
@Composable
private fun RecommendCard(
    entry: RecommendStore.Entry,
    liked: Boolean,
    playingThis: Boolean,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalShadeColors.current
    val track = entry.track
    val cover = 158.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadeSurface(cornerRadius = 26.dp, offset = 6.dp, blur = 12.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            // 左列：封面（上）+ 歌名/歌手（下）
            Column(
                Modifier.width(cover),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.cardTap { onPlayPause() }) {
                    AlbumArt(track.coverUrl, cover, corner = 22.dp, plate = true)
                }
                Text(
                    track.name,
                    color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                if (track.singer.isNotEmpty()) {
                    Text(
                        track.singer,
                        color = colors.textSecondary, fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                }
            }
            // 右列：详情（上，大小固定）+ 三个动作钮（下）
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 与封面同高：字数多少都不改变大小；超出在槽内滚动
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(cover)
                        .shadeInset(cornerRadius = 14.dp, offset = 3.dp, blur = 5.dp)
                        .padding(horizontal = 10.dp),
                ) {
                    Text(
                        entry.intro.ifEmpty { "该歌曲暂无歌曲详情" },
                        color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecBtn(
                        if (playingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playingThis) "暂停" else "播放", colors.accent,
                    ) { onPlayPause() }
                    RecBtn(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "取消喜欢" else "喜欢",
                        if (liked) colors.accent else colors.textSecondary,
                    ) { onLike() }
                    RecBtn(Icons.Filled.Refresh, "刷新推荐", colors.textSecondary) { onRefresh() }
                }
            }
        }
        Text("来自猜你喜欢", color = colors.textTertiary, fontSize = 10.sp)
    }
}


@Composable
private fun RecBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .shadePressable(cornerRadius = 14.dp, offset = 3.dp, blur = 5.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * 关注的歌手卡（主页栏）：搜索页歌手卡同款设计、缩小一号（约主页栏目卡片的 1.5 倍宽）。
 * 点击带出「飞位」转场进歌手页（与搜索页同款，坐标随卡片上报）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallSingerCard(s: FollowSinger, onClick: (Rect) -> Unit) {
    val colors = LocalShadeColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier
            .width(156.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .cardTap { onClick(bounds) },
    ) {
        // 画框规格按比例与歌手页头像一致（SingerAvatarFrame：216dp 基准等比换算），
        // 三处宽度不同也不会在飞位交接时跳。
        SingerAvatarFrame(pic = s.pic, desc = s.name, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        // 名字过长自动换行（最多两行，不再截断）
        Text(
            s.name,
            color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (s.desc.isNotEmpty()) {
            Text(
                s.desc,
                color = colors.textTertiary, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

