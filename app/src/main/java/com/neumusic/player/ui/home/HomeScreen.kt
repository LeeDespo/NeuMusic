package com.neumusic.player.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.neumusic.player.data.NicknameCache
import com.neumusic.player.data.PlaylistItem
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.api.ApiCache
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.RadioApi
import com.neumusic.player.data.api.UserApi
import com.neumusic.player.data.RadioGroup
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.AlbumArt
import com.neumusic.player.ui.common.CoverPlaceholder
import com.neumusic.player.ui.common.toastMain
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import com.neumusic.player.data.HomeCache
import com.neumusic.player.data.LikedStore
import com.neumusic.player.ui.common.doubleTapToTop
import com.neumusic.player.ui.common.HorizontalEdgeFades
import com.neumusic.player.ui.common.VerticalEdgeFades
import com.neumusic.player.ui.Nav
import com.neumusic.player.ui.Hero
import com.neumusic.player.ui.NavRequest
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** 随机问候语（未登录时用，不带用户名）。 */
private val GREETINGS_NO_NAME = listOf(
    "你好！", "早上好", "下午好", "晚上好", "夜深了，听首轻的？", "欢迎回来",
    "今天想听点什么？", "外面吵的话，戴耳机吧", "好久不见", "来点新歌？",
    "又是听歌的一天", "心情不好就多听两首", "午后的歌最安逸", "深夜电台已就绪",
    "要不要试试随机播放？", "歌单该更新了", "这里永远有歌等你",
)

/** 登录后问候语模板，{n} 替换为昵称。 */
private val GREETINGS_WITH_NAME = listOf(
    "你好！{n}", "早上好，{n}", "下午好，{n}", "晚上好，{n}", "欢迎回来，{n}",
    "{n}，今天想听什么？", "又见面了，{n}", "{n}，来点新歌？", "夜深了，{n}",
    "{n}，你的收藏还在等你", "戴上耳机吧，{n}", "{n}，随机一首怎么样？",
    "好久不见，{n}", "{n}，今天也听歌了吗", "欢迎回来，{n}，歌单没变",
)

/** 按当前时段挑一条更贴切的问候语（未登录版）。 */
private fun greetingForHour(hour: Int): String {
    val slot = when (hour) {
        in 5..8 -> listOf("早上好", "早上好，听点轻的？", "清晨第一首")
        in 9..11 -> listOf("上午好", "上午好，来点节奏？")
        in 12..13 -> listOf("中午好", "午后困了？听点提神的")
        in 14..17 -> listOf("下午好", "下午好，来首歌")
        in 18..22 -> listOf("晚上好", "晚上好，放松一下")
        else -> listOf("夜深了，听首轻的？", "还没睡？陪你一会儿", "夜深了")
    }
    // 时段内 60% 概率用时段的，其余用通用池，避免太死板。
    return if ((0..9).random() < 6) slot.random() else GREETINGS_NO_NAME.random()
}

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDest: (NavRequest) -> Unit,
) {
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

        // ── 3) 问候语：立即用时段语；昵称命中缓存就用，否则回来再升级，不阻塞任何请求 ──
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting = greetingForHour(hour)
        if (logged) {
            val nick = NicknameCache.get()
            if (nick != null) {
                greeting = GREETINGS_WITH_NAME.random().replace("{n}", nick)
            } else {
                scope.launch {
                    UserApi.nickname()?.let { n ->
                        NicknameCache.set(n)
                        greeting = GREETINGS_WITH_NAME.random().replace("{n}", n)
                    }
                }
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

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.background).doubleTapToTop(listState),
        // 左右只留 2dp：卡片行的"留白 + 阴影空间"由 [HomeCardRow] 自管（全宽视口），
        // 否则首尾卡片的阴影会被行视口裁掉（用户反馈的"第一张左侧/最后一张右侧截断"）。
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { GreetingHeader(greeting, onOpenSearch, onOpenSettings) }

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
                        LikedCard(likedCount) { b -> onOpenDest(NavRequest(Nav.Liked, b, Hero.Heart)) }
                    }
                    items(playlists) { p ->
                        PlaylistCard(p) { b ->
                            val hero = if (p.name == "我喜欢" || p.tid == 201L) Hero.Heart
                            else if (p.logo.isNotEmpty()) Hero.Image(p.logo) else null
                            onOpenDest(NavRequest(Nav.PlaylistDetail(p.tid, p.name, p.songnum), b, hero))
                        }
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
                        AlbumCard(a) { b ->
                            val hero = if (a.logo.isNotEmpty()) Hero.Image(a.logo) else null
                            onOpenDest(NavRequest(Nav.AlbumDetail(a.mid, a.name, a.songnum), b, hero))
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
                            StationCard(s.title, s.picUrl) { b ->
                                onOpenDest(NavRequest(Nav.RadioDetail(s.id, s.title), b, Hero.Image(s.picUrl)))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
    VerticalEdgeFades(state = listState, height = 26.dp)
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
 * [EDGE_ROOM]（比阴影位移+模糊略大），阴影就不再被裁 —— 单张卡片时两侧
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

private val EDGE_ROOM = 0.dp
