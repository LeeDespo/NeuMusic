package com.neumusic.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.animation.core.AnimationVector1D
import com.neumusic.player.data.Track
import com.neumusic.player.data.api.PlaylistApi
import com.neumusic.player.data.api.RadioApi
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.LocalShadeShadowAlpha
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.BottomBarMetrics
import com.neumusic.player.ui.common.PlayerBar
import com.neumusic.player.ui.common.SelectionBus
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
import com.neumusic.player.ui.singer.SingerScreen
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 页面栈根节点（无导航条）。
 *
 * 页面栈是 [StackEntry] 的列表：**所有返回入口都只弹出栈顶**，所以「从哪来就回哪去」
 * 对任何进入方式都成立（同一页面可能有多个来源）。栈底是主页，栈只剩主页时
 * 把返回交还系统（退出 App）。
 *
 * 播放页是全屏覆盖层而非普通页面（zIndex 高于一切页面内容）。**它的进出场和播放栏是
 * 同一条时间轴**：打开时整条播放栏从原位一路升到屏幕顶外，「带出」挂在它下方的播放页
 * （页面顶边始终贴着底栏底边，二者锁步上移）；返回精确倒放。均衡器压在播放页之上：
 * **打开时播放页整体下滑露出音效页，返回时播放页重新从底部升起盖住音效页，动画结束后
 * 才把音效页真正弹出栈**（早前立即弹栈，底下的页面闪现一下再重放升起动画——实测踩过）。
 *
 * 一二级页面切换是**整体场景缩放（摄像机推拉）**，操作对象是一级页面的根容器：
 * - 进入：**只有**一级场景的根容器做 scale + translation——uniform 缩放、相机中心
 *   从屏幕中心滑向卡片中心（卡片只是焦点），放大到卡片区域恰好铺满屏幕；
 *   放大**完成后**二级页面才整屏淡入（1.06→1 回落），与放大末态 crossfade 无缝衔接。
 * - 返回：同一条曲线精确倒放——二级页面**先**淡出，场景根容器**再**回缩到原位。
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

    // ── 歌手页「飞位」转场（搜索页歌手卡 → 歌手页头像位）──
    // 声明在 open/back 之前：back() 要置位/复位这些状态。
    var singerBackPending by remember { mutableStateOf(false) }
    var lastSingerEntry by remember { mutableStateOf<StackEntry?>(null) }

    fun open(req: NavRequest) {
        if (morphBack || eqPopPending || singerBackPending) return   // 转场进行中不接新入口（快速连点防护）
        // 覆盖层防连点：播放页/歌手页已在栈顶时不重复压栈
        if (stack.last().nav == req.nav && (req.nav == Nav.Player || req.nav is Nav.Singer)) return
        stack.add(StackEntry(req.nav, req.origin))
    }


    // 弹出栈顶。栈底（主页）不弹——交还系统处理（退出 App）。
    fun back() {
        if (stack.size <= 1 || morphBack || eqPopPending || singerBackPending) return
        val top = stack.last()
        val below = stack.getOrNull(stack.lastIndex - 1)
        if (top.nav == Nav.Equalizer && below?.nav == Nav.Player) {
            // 音效页返回：先让播放页从底部升起盖住音效页，动画结束再真正弹栈。
            eqPopPending = true
            playerRising = true
            scope.launch {
                delay(440)   // ≈ slide 的 tween(400) + 余量：升起动画走完再弹栈
                stack.removeAt(stack.lastIndex)
                playerRising = false
                eqPopPending = false
            }
            return
        }
        if (top.nav is Nav.Singer && top.origin != null) {
            // 搜索页歌手卡「飞位」进入的歌手页：返回 = 页面先淡出、克隆卡飞回卡片原位，
            // 动画走完才真正弹栈（与均衡器返回同款套路）。
            if (!top.settled) return   // 前向飞行中不接受返回
            singerBackPending = true
            scope.launch {
                delay(800)   // 页面淡出 140ms + 回飞 440ms + 余量
                stack.removeAt(stack.lastIndex)
                lastSingerEntry = null
                singerBackPending = false
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
    // 0 = 播放页完全在屏幕下方（隐藏）；1 = 完全覆盖。播放栏的上升与本进度锁步。
    val slide by animateFloatAsState(
        targetValue = if (playerOpen || playerRising) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "playerSlide",
    )
    // 歌手页覆盖层：升起动画与播放页同款（整条底栏同步上移「带出」）。
    //
    // 只有「不带 origin」的入口（从播放栏点歌手名进入）才走升起 + 底栏带出；
    // 带 origin 的是搜索页/主页歌手卡的「飞位」路径，页面是原地淡入的，底栏只做淡出。
    // 早前这里只看 `top.nav is Nav.Singer`：飞位期间 singerSlide 悄悄升到 1 而底栏的
    // ride 被写成 0（停在底部），弹栈那一帧 ride 又跳回 singerSlide=1 —— 单帧突跳，
    // 就是用户报的「返回主页时播放栏一瞬移动到底部再落下来」。
    val singerOpen = top.nav is Nav.Singer
    val singerRise = singerOpen && top.origin == null
    val singerSlide by animateFloatAsState(
        targetValue = if (singerRise) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "singerSlide",
    )

    // ── 歌手页「飞位」转场（搜索页歌手卡，用户 2026-09-30 规格）──
    // 进入：页面其余元素随背景色遮罩淡出（前半程加浓）→ 克隆卡从卡片原位缓动飞到
    // 歌手页头像落点（FastOutSlowIn 曲线，卡片文字飞行途中淡出）→ 就位后歌手页其余
    // 元素淡入 + 上浮浮现（0.72→1 段，与落位轻微重叠）。返回精确倒放。
    // 进度只在图层/绘制 lambda 里读；可见性用 derivedStateOf 门控重组。
    val singerCardT = remember { Animatable(0f) }
    var singerAvatarTarget by remember { mutableStateOf<Rect?>(null) }
    if (top.nav is Nav.Singer) lastSingerEntry = stack.last()
    val singerEntry = lastSingerEntry
    // 两段式（用户 2026-09-30 规格）：克隆卡先飞完（440ms），**之后**歌手页才淡入（200ms）——
    // 页面绝不在飞行途中提前出现。克隆卡陪到页面完全显形才隐去（同位同规格，无缝交接）。
    val singerPageT = remember { Animatable(0f) }
    LaunchedEffect(singerEntry?.nav, singerEntry?.settled == true, singerBackPending) {
        val entry = singerEntry ?: return@LaunchedEffect
        if (entry.origin == null) return@LaunchedEffect
        when {
            singerBackPending -> {
                singerPageT.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                singerCardT.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
            }
            entry.settled -> { singerCardT.snapTo(1f); singerPageT.snapTo(1f) }
            else -> {
                singerCardT.snapTo(0f)
                singerPageT.snapTo(0f)
                singerCardT.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
                singerPageT.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
                val i = stack.indexOf(entry)
                if (i >= 0) stack[i] = entry.copy(settled = true)
            }
        }
    }
    val singerFlying by remember { derivedStateOf { singerCardT.value > 0.001f } }
    // 整段序列（飞行+页面淡入）未完成：透明触摸拦截层只在这段时间存在。
    // 早前用 singerFlying 判断——落位后它恒为 true，拦截层把全屏点击永远吃掉（"应用卡死"实测根因）。
    val singerLanding by remember { derivedStateOf { singerCardT.value < 0.999f || singerPageT.value < 0.999f } }
    // 从歌手页压入专辑详情时整层淡出（不回飞克隆卡）；返回时淡入复原
    val singerOnTop = singerEntry != null && top.nav == singerEntry.nav
    val singerCoverAlpha by animateFloatAsState(
        targetValue = if (singerOnTop || !singerFlying) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "singerCover",
    )
    val singerOverlayVisible by remember {
        derivedStateOf { singerCardT.value > 0.001f || singerPageT.value > 0.001f || singerCoverAlpha > 0.001f }
    }

    // 摄像机推拉的宿主：最顶上那个带来源的页面（其上最多只有播放页/歌手页覆盖层，
    // 否则覆盖层盖在它上面时推拉层会跟着显示）。
    val lastOriginIdx = stack.indexOfLast { it.origin != null && it.nav != Nav.Player && it.nav !is Nav.Singer }
    val morphEntry = if (lastOriginIdx >= 0 && stack.drop(lastOriginIdx + 1).all { it.nav == Nav.Player || it.nav is Nav.Singer }) {
        stack[lastOriginIdx]
    } else null

    // 两段接力（用户最终规格）：
    // 进入 = 场景根容器推拉 400ms（模糊随进度逐步增强，推满时完全模糊）
    //        → 停 80ms → 二级页面淡入 + 上浮 12dp（easeOut）→ 落定。
    // 返回 = 二级页面淡出下沉 → 场景回缩 + 模糊渐消 400ms → 弹栈。
    // 进度只在图层 lambda 里读（零重组）。
    val zoomAnimatable = remember(morphEntry?.nav) {
        Animatable(if (morphEntry?.settled == true) 1f else 0f)
    }
    val pageAnimatable = remember(morphEntry?.nav) {
        Animatable(if (morphEntry?.settled == true) 1f else 0f)
    }
    LaunchedEffect(morphEntry?.nav, morphEntry?.settled == true, morphBack) {
        when {
            morphEntry == null -> Unit
            morphBack -> {
                pageAnimatable.animateTo(0f, tween(200, easing = LinearOutSlowInEasing))
                zoomAnimatable.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                stack.remove(morphEntry)
                morphBack = false
            }
            morphEntry.settled -> {
                zoomAnimatable.snapTo(1f)
                pageAnimatable.snapTo(1f)
            }
            else -> {
                zoomAnimatable.snapTo(0f)
                pageAnimatable.snapTo(0f)
                zoomAnimatable.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                delay(80)
                pageAnimatable.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
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
        else -> (stack.lastOrNull { it.nav != Nav.Player && it.nav !is Nav.Singer } ?: stack.first()).nav
    }

    // 转场期间把场景阴影逐帧淡出（放大的高斯模糊逐帧重执行极其昂贵，实测帧 150ms+）；
    // 同时把背景色遮罩逐帧加浓（用户规格）：场景在推拉中逐渐"沉入"页面底色，
    // 二级页面的淡入上浮就是在同色底上浮现，衔接无缝。深浅色模式取各自 background。
    val flightShadow = remember { mutableFloatStateOf(1f) }
    val flightScrim = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(morphEntry?.nav, morphBack, morphEntry?.settled == true) {
        if (morphEntry == null) {
            flightShadow.floatValue = 1f
            flightScrim.floatValue = 0f
        } else {
            snapshotFlow { zoomAnimatable.value }.collect {
                flightShadow.floatValue = 1f - it
                flightScrim.floatValue = it
            }
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
                        // 模糊随推拉逐步增强，铺满屏幕时完全模糊（用户规格）
                        val blurEnabled = true
                        if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t > 0.01f) {
                            val b = 26f * t
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(b, b, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
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

        // ── 底部播放栏（用户 2026-09-29 规格）：覆盖底部的底栏；无音乐时下沉消失、
        // 有音乐时上升出现；选择模式时沉降给选择底栏让位；点击打开播放页时整条一路
        // 升到屏幕顶外，「带出」挂在它下方的播放页（与 slide 同一条时间轴、锁步）──
        val current by PlayerHost.current.collectAsState()
        val selectionActive by SelectionBus.active.collectAsState()
        // 消失要「沉下去」而不是瞬间移除：记住最后一条曲目，沉底后仍在组合但不可见
        var lastTrack by remember { mutableStateOf<Track?>(null) }
        if (current != null) lastTrack = current
        var barHeightPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val barVisible = current != null && !selectionActive
        val barDrop by animateFloatAsState(
            targetValue = if (barVisible) 0f else barHeightPx + with(density) { 30.dp.toPx() },
            animationSpec = tween(340, easing = FastOutSlowInEasing),
            label = "barDrop",
        )
        val barTrack = lastTrack
        if (barTrack != null) {
            PlayerBar(
                track = barTrack,
                onOpen = { if (stack.last().nav != Nav.Player) open(NavRequest(Nav.Player)) },
                onOpenSinger = { s ->
                    if (stack.last().nav !is Nav.Singer) {
                        open(NavRequest(Nav.Singer(s.mid, s.name, s.pic, s.songNum, s.albumNum)))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 必须浮在推拉层(zIndex 3)之上——否则二级页面盖住播放栏（实测踩过）；
                    // 仍低于播放页覆盖层(4)
                    .zIndex(3.5f)
                    .onSizeChanged {
                        barHeightPx = it.height.toFloat()
                        BottomBarMetrics.heightPx = it.height.toFloat()   // 各页面据此留出底部空白
                    }
                    .graphicsLayer {
                        // 只在图层 lambda 里读进度：逐帧平移，零重组。
                        // 播放页/普通歌手页由底栏带出（锁步上升）；搜索卡「飞位」进入的
                        // 歌手页则不做底栏上升，底栏随转场进度淡出（被浮现的页面盖住）。
                        val sh = sceneSize.height.toFloat()
                        val cardFlight = singerEntry?.origin != null && top.nav is Nav.Singer
                        val ride = if (cardFlight) 0f else singerSlide
                        alpha = if (cardFlight) (1f - singerCardT.value).coerceIn(0f, 1f) else 1f
                        translationY = if (sh > 0f) -maxOf(slide, ride) * sh + barDrop else barDrop
                    },
            )
        }

        // ── 二级页面：放大完成后整屏淡入（推拉的最后一站）──
        if (morphEntry != null) {
        // ── 背景色遮罩：随推拉加浓（drawBehind 逐帧读，零重组），沉入页面底色 ──
        if (morphEntry != null) {
            val scrim = flightScrim
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(2.5f)
                    .drawBehind {
                        val a = scrim.floatValue.coerceIn(0f, 1f)
                        if (a > 0.001f) drawRect(colors.background, alpha = a)
                    },
            )
        }

            SecondPageOverlay(
                entry = morphEntry,
                page = pageAnimatable.asState(),
                showContent = remember(morphEntry?.nav) {
                    derivedStateOf { zoomAnimatable.value > 0.05f }
                },
                onOpen = openCb,
                onBack = backCb,
            )
        }

        // ── 播放页覆盖层：顶边始终贴着播放栏的底边，与底栏锁步上升（zIndex 高于推拉层）；
        // 整块是实心页面，飞行中不做透明度渐变（它本来就被底栏从屏幕外拖上来）──
        if (slide > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .graphicsLayer {
                        val sh = sceneSize.height.toFloat()
                        if (sh > 0f) translationY = (1f - slide) * sh
                    },
            ) {
                PlayerScreen(
                    onBack = { back() },
                    onOpenEqualizer = { open(NavRequest(Nav.Equalizer)) },
                )
            }
        }

        // ── 歌手页「飞位」转场的背景色遮罩：页面其余元素在前半程淡出（被点击的卡片
        // 由克隆卡原样盖住，视觉上保持不动）──
        if (singerFlying && singerEntry?.origin != null) {
            val tState = singerCardT
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(2.6f)
                    .drawBehind {
                        val a = (tState.value * 2f).coerceIn(0f, 1f)
                        if (a > 0.001f) drawRect(colors.background, alpha = a)
                    },
            )
        }

        // ── 飞行的克隆卡：从歌手卡原位飞向歌手页头像落点（缓动曲线，非匀速）。
        // 按卡片原尺寸布局、逐帧缩放平移到目标，落位瞬间与页面头像无缝交接。──
        if (singerFlying && singerEntry?.origin != null) {
            val entry = singerEntry
            val s = entry.nav as Nav.Singer
            val tState = singerCardT
            val target = singerAvatarTarget
            val cardW = with(LocalDensity.current) { (entry.origin?.width ?: 1f).toDp() }
            Box(
                Modifier
                    .zIndex(3.7f)
                    .width(cardW)
                    .graphicsLayer {
                        val t = tState.value
                        val o = entry.origin ?: return@graphicsLayer
                        // 落点 = 歌手页头像画框（布局完成后上报）；未量出前用估算兜底
                        val tw = target?.width ?: (sceneSize.width * 0.55f)
                        val tl = target?.left ?: (sceneSize.width - tw) / 2f
                        val tt = target?.top ?: (sceneSize.height * 0.1f)
                        translationX = lerp(o.left, tl, t)
                        translationY = lerp(o.top, tt, t)
                        val sc = lerp(1f, tw / o.width.coerceAtLeast(1f), t)
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0f, 0f)
                        // 飞行+页面淡入全部完成后才隐去（页面头像同规格，交接无跳变）
                        if (t >= 0.999f && singerPageT.value >= 0.999f) alpha = 0f
                    },
            ) {
                SingerFlightCard(s = s, t = tState)
            }
        }

        // ── 歌手页覆盖层（zIndex 4）：两条路径。
        // 播放栏进入（无 origin）= 整块自下方升起，底栏同步上移带出；
        // 搜索卡进入（有 origin）= 原地淡入 + 上浮浮现（飞位转场的最后一站），
        // 飞行中拦截触摸；被专辑详情压住时整层淡出而非回飞。──
        if (singerEntry != null && singerOverlayVisible) {
            val hasOrigin = singerEntry.origin != null
            val showRise = !hasOrigin && singerSlide > 0.001f
            if (showRise || (hasOrigin && singerOverlayVisible)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(4f)
                        .graphicsLayer {
                            val sh = sceneSize.height.toFloat()
                            if (!hasOrigin && sh > 0f) translationY = (1f - singerSlide) * sh
                        },
                ) {
                    if (hasOrigin) {
                        // 纯淡入、**不做上浮**：上浮会让头像在「落点测量期」处于瞬态偏移位置，
                        // 克隆卡落位后页面归位出现错位（用户实测"最后一段不能完全重合"）。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = singerPageT.value * singerCoverAlpha
                                },
                        ) {
                            SingerScreen(
                                singer = singerEntry.nav as Nav.Singer,
                                onBack = { back() },
                                onOpenAlbum = { mid, name -> open(NavRequest(Nav.AlbumDetail(mid, name))) },
                                onAvatarBounds = { singerAvatarTarget = it },
                            )
                        }
                        // 飞行中页面尚透明，但会吃触摸——落位前放一块透明拦截层
                        if (singerLanding) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) { detectTapGestures { } },
                            )
                        }
                    } else {
                        SingerScreen(
                            singer = singerEntry.nav as Nav.Singer,
                            onBack = { back() },
                            onOpenAlbum = { mid, name -> open(NavRequest(Nav.AlbumDetail(mid, name))) },
                        )
                    }
                }
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
            onOpenSinger = { s, origin -> onOpen(NavRequest(Nav.Singer(s.mid, s.name, s.pic, s.songNum, s.albumNum), origin)) },
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
        // 播放页与歌手页只作为覆盖层出现，正常情况下不会走到这里；兜底不渲染。
        Nav.Player -> Unit
        is Nav.Singer -> Unit
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * 「飞位」转场的克隆卡：外观与搜索结果里的 [com.neumusic.player.ui.common.SingerCard]
 * 完全一致（同字体/间距/画框规格），仅文字部分随飞行进度淡出——卡就位后只剩头像，
 * 与歌手页头像（同规格：内缩 10dp、圆角 14）无缝交接。进度只在图层 lambda 里读。
 */
@Composable
private fun SingerFlightCard(s: Nav.Singer, t: Animatable<Float, AnimationVector1D>) {
    val colors = LocalShadeColors.current
    Column {
        // 画框必须与歌手卡/歌手页头像用**同一个**按比例换算的组件，否则飞行的克隆卡
        // 与它替换掉的原卡片画框粗细不一致（用户实测"过渡时能看到画框不统一"）。
        com.neumusic.player.ui.common.SingerAvatarFrame(
            pic = s.pic,
            desc = "",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            s.name,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - t.value * 2.4f).coerceIn(0f, 1f) },
            color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "歌曲 ${s.songNum} · 专辑 ${s.albumNum}",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - t.value * 2.4f).coerceIn(0f, 1f) },
            color = colors.textTertiary, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 推拉的二级页面端：推拉（含完全模糊）完成后 80ms，整屏淡入 + 上浮 12dp
 * （easeOut，用户规格）。单一实例贯穿飞行与落定（alpha=1 即落定态）；
 * 内容在推拉起步段就组合好（淡入开始时无需再组合）。
 */
@Composable
private fun SecondPageOverlay(
    entry: StackEntry,
    page: androidx.compose.runtime.State<Float>,
    showContent: androidx.compose.runtime.State<Boolean>,
    onOpen: (NavRequest) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalShadeColors.current
    if (!showContent.value) return
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .statusBarsPadding()
            .graphicsLayer {
                val a = page.value.coerceIn(0f, 1f)
                alpha = a
                translationY = (1f - a) * 12.dp.toPx()   // 上浮 12dp
            }
            // background 必须在 graphicsLayer **之后**：在图层内才受 alpha 控制，
            // 否则从组合起就不透明地盖住场景，推拉全程不可见（实测踩过）
            .background(colors.background),
    ) {
        PageContent(nav = entry.nav, onOpen = onOpen, onBack = onBack)
    }
}

/** 底部播放栏已移到 [com.neumusic.player.ui.common.PlayerBar]（底栏式，见该文件注释）。 */
