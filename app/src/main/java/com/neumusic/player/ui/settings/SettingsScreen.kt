package com.neumusic.player.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.neumusic.player.data.AppLog
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.neumusic.player.data.CredentialInfo
import com.neumusic.player.data.NicknameCache
import com.neumusic.player.data.AccentMode
import com.neumusic.player.data.DownloadDir
import com.neumusic.player.data.LightingMode
import com.neumusic.player.data.LyricTextSize
import com.neumusic.player.data.HomeCache
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Quality
import com.neumusic.player.data.Relief
import com.neumusic.player.data.ThemeMode
import com.neumusic.player.data.api.ApiCache
import com.neumusic.player.data.api.UserApi
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.DayLightHost
import com.neumusic.player.shade.OFFSET_RANGE
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.formatHour
import com.neumusic.player.shade.periodName
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.HorizontalShadeSlider
import com.neumusic.player.ui.common.SegmentedControl
import com.neumusic.player.ui.common.TopEdgeFade
import com.neumusic.player.ui.common.ShadeSwitchRow
import com.neumusic.player.ui.common.rememberPlayerBarSpace
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.home.DetailTopBar
import kotlinx.coroutines.launch

/** 设置页分区（分段控制器的选项，顺序即 tab 下标）。 */
private val SETTING_TABS = listOf("账号", "外观", "光影", "播放", "下载", "其它")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalShadeColors.current

    var cred by remember { mutableStateOf(Prefs.credential) }
    var showWebLogin by remember { mutableStateOf(false) }
    // 分区（用户 2026-10-01 规格：设置项按区分组、用分段控制器切换）
    var tab by remember { mutableIntStateOf(0) }
    val barSpace = rememberPlayerBarSpace()
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }   // 换区回到顶部
    var quality by remember { mutableStateOf(Prefs.quality) }
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var accentMode by remember { mutableStateOf(Prefs.accentMode) }
    var relief by remember { mutableStateOf(Prefs.relief) }
    var lyricSize by remember { mutableStateOf(Prefs.lyricTextSize) }
    var showTrans by remember { mutableStateOf(Prefs.showLyricTranslation) }
    var downloadDir by remember { mutableStateOf(Prefs.downloadDir) }
    var downloadQuality by remember { mutableStateOf(Prefs.downloadQuality) }
    var vinyl by remember { mutableStateOf(Prefs.vinylMode) }
    var logging by remember { mutableStateOf(Prefs.loggingEnabled) }
    var logMaxMbText by remember { mutableStateOf(Prefs.logMaxMb.toString()) }
    var logCleared by remember { mutableStateOf(false) }
    val exportLogs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { AppLog.exportTo(context.contentResolver, it) }
    }

    if (showWebLogin) {
        WebLoginOverlay(
            onClose = { showWebLogin = false },
            onLoggedIn = {
                showWebLogin = false
                cred = Prefs.credential
            },
        )
        return
    }

    // 顶栏随页面滚动（用户规定）：放进滚动列第一项
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .imePadding(),
        ) {
            DetailTopBar("设置", onBack, horizontalPadding = 16.dp)

            // ── 分区切换（用户 2026-10-01 规格）：平的分段控制器，选中=凹陷圆角矩形 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                SegmentedControl(
                    options = SETTING_TABS,
                    selected = tab,
                    onSelect = { tab = it },
                )
            }
            Spacer(Modifier.height(6.dp))

            AnimatedVisibility(
                visible = tab == 0,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 账号 ──
            SectionTitle("账号")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(46.dp).shadeInset(cornerRadius = 16.dp, offset = 3.dp, blur = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person, null,
                            tint = if (cred != null) colors.accent else colors.textTertiary,
                        )
                    }
                    Column {
                        Text(
                            if (cred != null) "已登录  ${cred!!.uin}" else "未登录",
                            fontSize = 15.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (cred?.euin.isNullOrEmpty()) "登录后同步我喜欢 / 歌单 / 专辑" else "凭据完整，可读取全部收藏",
                            fontSize = 12.sp, color = colors.textSecondary,
                        )
                    }
                }
                ShadeButton("网页登录", Modifier.fillMaxWidth(), primary = true) { showWebLogin = true }
                if (cred != null) {
                    ShadeButton("退出登录", Modifier.fillMaxWidth(), primary = false) {
                        Prefs.clearCredential()
                        cred = null
                        scope.launch { ApiCache.clear() }
                        LikedStore.clear()
                        HomeCache.clear()
                        com.neumusic.player.ui.home.TrackListCache.clear()
                        toastMain(context, "已退出登录")
                    }
                }
            }
            }
            Spacer(Modifier.height(if (tab == 0) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 3,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 音质 ──
            SectionTitle("播放音质")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Quality.entries.forEach { q ->
                    QualityRow(
                        label = q.label,
                        selected = q == quality,
                        onClick = {
                            quality = q
                            Prefs.quality = q
                            toastMain(context, "音质已切换为 ${q.label}")
                        },
                    )
                }
                Text(
                    "取不到所选音质时会自动降级到较低档位；无损/母带需对应的会员权益。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )
            }
            }
            Spacer(Modifier.height(if (tab == 3) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 1,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 外观 ──
            SectionTitle("外观")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 深色模式：三个选项，选中的那行凹陷（凸起容器内以凹陷表选中）。
                Text(
                    "深色模式", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
                Box(Modifier.padding(start = 6.dp)) {
                    SegmentedControl(
                        options = ThemeMode.entries.map { it.label },
                        selected = themeMode.ordinal,
                        onSelect = {
                            themeMode = ThemeMode.entries[it]
                            Prefs.themeMode = ThemeMode.entries[it]
                        },
                    )
                }
                Text(
                    "「随系统」跟随 Android 的深色设置；浅色/深色为强制固定。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 强调色：经典 / 莫奈壁纸取色
                Text(
                    "强调色", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.padding(start = 6.dp)) {
                    SegmentedControl(
                        options = AccentMode.entries.map { it.label },
                        selected = accentMode.ordinal,
                        onSelect = {
                            accentMode = AccentMode.entries[it]
                            Prefs.accentMode = AccentMode.entries[it]
                        },
                    )
                }
                Text(
                    if (android.os.Build.VERSION.SDK_INT >= 31)
                        "「跟随壁纸取色」用 Material You 从壁纸提取强调色，与新拟物表面结合。"
                    else
                        "壁纸取色需要 Android 12 及以上，当前设备仅支持经典配色。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 立体感：影响全部凸起/凹陷的阴影位移与模糊强度。
                Text(
                    "立体感", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.padding(start = 6.dp)) {
                    SegmentedControl(
                        options = Relief.entries.map { it.label },
                        selected = relief.ordinal,
                        onSelect = {
                            relief = Relief.entries[it]
                            Prefs.relief = Relief.entries[it]
                        },
                    )
                }
                Text(
                    "越强则凸起/凹陷的阴影与补光对比越明显（弱 / 标准 / 强）。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 歌词字号
                Text(
                    "歌词字号", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.padding(start = 6.dp)) {
                    SegmentedControl(
                        options = LyricTextSize.entries.map { it.label },
                        selected = lyricSize.ordinal,
                        onSelect = {
                            lyricSize = LyricTextSize.entries[it]
                            Prefs.lyricTextSize = LyricTextSize.entries[it]
                        },
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 歌词翻译开关（只在曲目确实带翻译时才有内容 —— 多数曲目没有）
                Text(
                    "歌词翻译", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                ShadeSwitchRow(
                    label = "显示翻译",
                    checked = showTrans,
                    subtitle = "仅当曲目本身带翻译时才会显示（QQ 只对少部分曲目提供，多数为空）。",
                ) { showTrans = it; Prefs.showLyricTranslation = it }
            }
            }
            Spacer(Modifier.height(if (tab == 1) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 2,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 光影（随时间变化的新拟物光照；模型与曲线见 shade/DayLight.kt）──
            SectionTitle("光影")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val lMode = DayLightHost.mode
                val lHour = DayLightHost.displayHour()
                val lx = DayLightHost.preview(colors.isDark)
                var calibOpen by remember { mutableStateOf(false) }

                // 时间行为：平的分段控制器（选中 = 凹陷圆角矩形，同歌手页）
                Text(
                    "时间行为", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.padding(start = 6.dp)) {
                    SegmentedControl(
                        options = LightingMode.entries.map { it.label },
                        selected = lMode.ordinal,
                        onSelect = { DayLightHost.mode = LightingMode.entries[it] },
                    )
                }
                Text(
                    "「随时间变化」跟着真实时钟走（约半小时一档）；「固定光影」停在下面选定的时刻。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )
                Text(
                    "取哪一套曲线由深色模式决定：浅色主题一直是白天那套、深色主题一直是黑夜那套、" +
                        "随系统则跟着系统深浅色走。黑夜是固定色温，只有偏移随时间扫过。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 时刻
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("时刻", fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text("${formatHour(lHour)} · ${periodName(lHour)}", fontSize = 12.sp, color = colors.accent)
                }
                HorizontalShadeSlider(
                    progress = lHour / 24f,
                    onProgress = { DayLightHost.fixedHour = it * 24f },
                    enabled = lMode == LightingMode.FIXED,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                )
                Text(
                    "色温 %+.2f · 方向 (%+.2f, %+.2f) · 偏移 %.1f / %.1f dp · 模糊 %.2f× / %.2f× · 强度 %.2f / %.2f".format(
                        lx.warmth, lx.ux, lx.uy, lx.lenDark, lx.lenLight,
                        lx.blurDark, lx.blurLight, lx.alphaDark, lx.alphaLight,
                    ),
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 标定（默认收起）
                Row(
                    Modifier.fillMaxWidth()
                        .flatPressable(cornerRadius = 12.dp) { calibOpen = !calibOpen }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("标定 · 色温锚点 / 阴影偏移", fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(if (calibOpen) "收起" else "展开", fontSize = 12.sp, color = colors.accent)
                }
                if (calibOpen) {
                    CalibSlider("白天最暖 2000K（日出/日落）", DayLightHost.dayWarm) { DayLightHost.dayWarm = it }
                    CalibSlider("白天最冷 5500K（正午）", DayLightHost.dayCold) { DayLightHost.dayCold = it }
                    CalibSlider("黑夜 8000K（月光）", DayLightHost.nightWarm) { DayLightHost.nightWarm = it }
                    CalibSlider(
                        "暗色阴影最大偏移", DayLightHost.darkMax, 0f..OFFSET_RANGE,
                        text = "%.1f dp".format(DayLightHost.darkMax),
                    ) { DayLightHost.darkMax = it }
                    CalibSlider(
                        "暗色阴影最小偏移", DayLightHost.darkMin, 0f..OFFSET_RANGE,
                        text = "%.1f dp".format(DayLightHost.darkMin),
                    ) { DayLightHost.darkMin = it }
                    CalibSlider(
                        "高光阴影最大偏移", DayLightHost.lightMax, 0f..OFFSET_RANGE,
                        text = "%.1f dp".format(DayLightHost.lightMax),
                    ) { DayLightHost.lightMax = it }
                    CalibSlider(
                        "高光阴影最小偏移", DayLightHost.lightMin, 0f..OFFSET_RANGE,
                        text = "%.1f dp".format(DayLightHost.lightMin),
                    ) { DayLightHost.lightMin = it }
                    Text(
                        "两条阴影共用同一条变化曲线（正午/午夜最短、日出/日落最长），只是各自的取值区间不同；" +
                            "组件自己的 offset 在此基础上等比缩放。",
                        fontSize = 11.sp, color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "恢复默认",
                            fontSize = 12.sp, color = colors.accent,
                            modifier = Modifier
                                .flatPressable(cornerRadius = 12.dp) { DayLightHost.resetCalib() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            }
            Spacer(Modifier.height(if (tab == 2) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 4,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 下载 ──
            SectionTitle("下载")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "保存目录", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                DownloadDir.entries.forEach { d ->
                    ChoiceRow(
                        label = d.label,
                        selected = d == downloadDir,
                        onClick = { Prefs.downloadDir = d; downloadDir = d },
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "下载音质", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Quality.entries.forEach { q ->
                    ChoiceRow(
                        label = q.label,
                        selected = q == downloadQuality,
                        onClick = { Prefs.downloadQuality = q; downloadQuality = q },
                    )
                }
                Text(
                    "取不到所选音质时自动降级；无损/母带需对应会员权益。下载完成后曲目在列表里显示为已下载（灰色）。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )
            }
            }
            Spacer(Modifier.height(if (tab == 4) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 3,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 播放栏与播放页（原本混在「下载」区里，2026-10-01 分区整理独立出来）──
            SectionTitle("播放栏与播放页")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 黑胶唱片模式（用户请我取名：封面按唱片机样式呈现）
                Text(
                    "播放页", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                ShadeSwitchRow(
                    label = "黑胶唱片模式",
                    checked = vinyl,
                    subtitle = "封面按唱片机样式呈现：画框加宽并刻上唱片纹路，播放时缓缓旋转，阴影保持不动。",
                ) { vinyl = it; Prefs.vinylMode = it }
            }
            }
            Spacer(Modifier.height(if (tab == 3) 18.dp else 0.dp))

            AnimatedVisibility(
                visible = tab == 5,
                // 切换分区：与搜索页/歌手页分段切换同款 —— **纯淡入淡出**，时长短。
                // （早前带 slideInVertically(it/10)：分区分高，位移 = 分区高度的 1/10，
                //  长的分区一下子滑动几百像素，看着很怪。）
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(120)),
            ) {
            // ── 诊断日志（2026-09-30 规格）：结构化、可开关、可清空、可限容、可导出 ──
            SectionTitle("诊断日志")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShadeSwitchRow(
                    label = "启用诊断日志",
                    checked = logging,
                    subtitle = "记录推荐/播放/收藏等关键请求的结构化日志；关闭后完全静默。超上限自动裁掉较早的一半。",
                ) { logging = it; Prefs.loggingEnabled = it }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 清空：圆形凸起 + 垃圾桶
                    Box(
                        Modifier.size(46.dp)
                            .shadePressable(cornerRadius = 23.dp, offset = 4.dp, blur = 7.dp) {
                                AppLog.clear()
                                logCleared = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Delete, "清空日志", tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    // 导出：凸起按钮
                    Box(
                        Modifier.shadePressable(cornerRadius = 14.dp, offset = 4.dp, blur = 7.dp) {
                            exportLogs.launch("neumusic-diagnostics.log")
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.FileDownload, null, tint = colors.accent, modifier = Modifier.size(17.dp))
                            Text("导出日志", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 容量上限：凹陷圆角数字框，单位 MB
                    Row(
                        Modifier
                            .width(120.dp)
                            .shadeInset(cornerRadius = 12.dp, offset = 3.dp, blur = 5.dp)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = logMaxMbText,
                            onValueChange = { v ->
                                logMaxMbText = v.filter { it.isDigit() }.take(2)
                                logMaxMbText.toIntOrNull()?.let { Prefs.logMaxMb = it }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Text("MB", color = colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                    }
                }
                if (logCleared) {
                    Text("已清空", fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp))
                }
            }
            Spacer(Modifier.height(if (tab == 5) 18.dp else 0.dp))

            // ── 关于 ──
            SectionTitle("关于")
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("NeuMusic 0.4.0", fontSize = 14.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                Text(
                    "全程原生直连 QQ 音乐接口：搜索、电台、我喜欢（含收藏/取消）、收藏歌单/专辑、歌单与专辑内歌曲、多档音质播放链接、歌词、下载，以及账号昵称。",
                    fontSize = 11.sp, color = colors.textSecondary,
                )
                Text(
                    "UI 风格参考 ShadeCraft。仅供个人学习使用，请勿批量抓取。",
                    fontSize = 11.sp, color = colors.textTertiary,
                )
            }
            }
            // 所有标签共用的滚动内容都在播放栏和系统导航栏之前结束，保证各分区都有完整底部留白。
            Spacer(Modifier.height(24.dp + barSpace))
        }
            if (scroll.value > 0) TopEdgeFade()
        }
    }
}

/** 光影标定的滑杆行：标签 + 数值 + 新拟物滑杆（色温用 −1..1，偏移用 0..满量程）。 */
@Composable
private fun CalibSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    text: String = "%+.2f".format(value),
    onChange: (Float) -> Unit,
) {
    val colors = LocalShadeColors.current
    val span = range.endInclusive - range.start
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(text, fontSize = 12.sp, color = colors.accent)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalShadeSlider(
            progress = ((value - range.start) / span).coerceIn(0f, 1f),
            onProgress = { onChange(range.start + it * span) },
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 二选一行：凸起行 + 选中时整行凹陷 + 对勾（凸起容器内以凹陷表选中）。 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Row(
        Modifier.fillMaxWidth().height(46.dp)
            // 位于凸起卡片内：选中用「凹陷」表达，未选中是「平」，都不再凸起。
            .then(
                if (selected) Modifier.shadeInset(cornerRadius = 16.dp, offset = 3.dp, blur = 5.dp)
                else Modifier
            )
            .flatPressable(cornerRadius = 16.dp, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = 14.sp, modifier = Modifier.weight(1f),
            color = if (selected) colors.accent else colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** 音质选项：选中凹陷 + 对勾。 */
@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .then(
                if (selected) Modifier.shadeInset(cornerRadius = 18.dp, offset = 3.dp, blur = 5.dp)
                else Modifier
            )
            .flatPressable(cornerRadius = 18.dp, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = 14.sp, modifier = Modifier.weight(1f),
            color = if (selected) colors.accent else colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    // 分区标题坐在自己的凸起小块上（用户 2026-10-01 规格：不要平铺在底色上）
    val colors = LocalShadeColors.current
    Box(Modifier.padding(start = 16.dp).padding(bottom = 10.dp)) {
        Box(
            Modifier
                .shadeSurface(cornerRadius = 14.dp, offset = 4.dp, blur = 7.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ShadeButton(label: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val colors = LocalShadeColors.current
    Box(
        // 这类按钮都放在 shadeSurface 卡片里，所以用「平」按压（不在凸起上做凸起）。
        modifier.height(48.dp).flatPressable(cornerRadius = 20.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) colors.accent else colors.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ───────────────────────── 网页登录 ─────────────────────────

@Composable
private fun WebLoginOverlay(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    // 登录弹窗（桌面站点"登录"后 window.open 出来的子窗，二维码画在里面）。
    // 必须真正挂到界面上 —— 之前开在隐形 WebView 里，弹窗等于不存在。
    var popup by remember { mutableStateOf<WebView?>(null) }
    val chromeClient = remember {
        QqLoginChromeClient(onPopup = { popup = it }, onClosePopup = { popup = null })
    }
    val context = LocalContext.current
    val colors = LocalShadeColors.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(colors.background).statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).shadePressable(cornerRadius = 20.dp, offset = 4.dp, blur = 6.dp) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = colors.textPrimary, modifier = Modifier.size(20.dp),
                )
            }
            Text(
                "网页登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.height(42.dp).shadePressable(cornerRadius = 18.dp, offset = 4.dp, blur = 7.dp) {
                    val cookie = CookieManager.getInstance().getCookie("https://y.qq.com/") ?: ""
                    val map = cookie.split(";").mapNotNull {
                        val p = it.trim().split("=", limit = 2)
                        if (p.size == 2) p[0] to p[1] else null
                    }.toMap()
                    val uin = map["uin"]?.removePrefix("o").orEmpty()
                    val musickey = map["qm_keyst"].orEmpty()
                    val euin = map["euin"].orEmpty()
                    if (uin.isEmpty() || musickey.isEmpty()) {
                        toastMain(context, "未检测到登录 Cookie，请先在页面中完成登录")
                    } else {
                        Prefs.saveCredential(CredentialInfo(uin, musickey, euin))
                        // 拉一次昵称供主页问候语使用
                        scope.launch { NicknameCache.set(runCatching { UserApi.nickname() }.getOrNull()) }
                        toastMain(
                            context,
                            if (euin.isEmpty())
                                "已登录（缺 euin，收藏列表可能不可用；建议重新登录一次）"
                            else "登录成功",
                        )
                        onLoggedIn()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "完成登录", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        // 主页 WebView 与登录子窗叠放：子窗必须**盖在主页之上**，
        // 之前子窗排在 Column 里、被 fillMaxSize 的主页挤出屏幕外 —— 弹窗开了却看不见。
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        // 扫码登录的二维码是 graph.qq.com 的 iframe，必须允许三方 cookie
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        // 桌面版站点：手机版首页没有"登录"入口（原登录方式是一键登录，
                        // 已被协议拦截屏蔽）；桌面版右上角"登录"打开官方登录窗。
                        settings.userAgentString = DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true

                        webViewClient = QqLoginWebViewClient()
                        webChromeClient = chromeClient
                        loadUrl("https://y.qq.com/")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // 登录子窗（二维码/账号密码在这个窗里），盖在主页之上
            popup?.let { pw ->
                AndroidView(
                    factory = { pw },
                    modifier = Modifier.fillMaxSize().background(colors.background),
                )
            }
        }
    }
}

/**
 * 登录页专用 WebViewClient。
 *
 * ## 为什么必须拦非 http(s) 的跳转（用户实测的登录 bug）
 * y.qq.com 登录页检测到手机装了 QQ/微信/TIM 时会出现「一键登录」，
 * 点击后页面会试图通过 `wtlogin://`、`mqq://`、`tim://`、`weixin://`、`intent://`
 * 等协议拉起外部应用（没有对应应用时落到默认浏览器）。WebView 没设 client 时
 * 这些跳转由系统默认处理 —— 一旦跳出去，本 App 的 CookieManager 就拿不到登录
 * cookie，登录等于失败。
 *
 * 对策：**只放行 http/https**，其余一律拦下。拦掉后页面留在 WebView 里，
 * 用户改走扫码/账号密码登录，cookie 落在 webview 的 CookieManager 中可读。
 * 另外，尽力用 JS 把「一键登录」入口藏掉（页面结构变了也不影响拦截本身）。
 */
private class QqLoginWebViewClient : android.webkit.WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: android.webkit.WebResourceRequest,
    ): Boolean = blocked(request.url.scheme)

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        blocked(android.net.Uri.parse(url).scheme)

    /** true = 拦下不走。能拉起外部应用的协议一律拦（一键登录就是这么跳出去的）。 */
    private fun blocked(scheme: String?): Boolean = when (scheme?.lowercase()) {
        "http", "https", "blob", "data", "about", "javascript" -> false
        else -> true
    }

    override fun onPageFinished(view: WebView, url: String) {
        view.evaluateJavascript(HIDE_QUICK_LOGIN_JS, null)
    }
}

/** 尽力隐藏「一键登录 / 快捷登录」入口，让扫码与账号密码登录成为唯一路径。 */
private val HIDE_QUICK_LOGIN_JS = """
(function(){
  var KW = ['一键登录','快捷登录','快速登录','一键','本机号码'];
  function scan(){
    try{
      var els = document.querySelectorAll('a,button,div,span,p,li');
      for (var i=0;i<els.length;i++){
        var el = els[i];
        var t = (el.textContent||'').replace(/\s+/g,'');
        if(!t || t.length>10 || el.children.length>2) continue;
        for (var k=0;k<KW.length;k++){
          if (t.indexOf(KW[k]) > -1){
            el.style.display='none';
            el.style.visibility='hidden';
            break;
          }
        }
      }
    }catch(e){}
  }
  scan();
  setTimeout(scan, 1200);
  setTimeout(scan, 3000);
})();
""".trimIndent() + ";"

/** 桌面版 UA：让 y.qq.com 出桌面布局（含"登录"入口）。 */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/**
 * 登录页的 WebChromeClient。
 *
 * - 登录弹窗可能用 `window.open` 开新窗：WebView 不真正开新窗，而是把目标 URL
 *   **在当前 view 里继续加载**（登录流程页少跳转少，这样最稳）。
 * - `alert`/`confirm` 必须实现，否则页面弹窗会被静默吞掉，登录流程卡死在等对话框。
 */
private class QqLoginChromeClient(
    private val onPopup: (WebView) -> Unit,
    private val onClosePopup: () -> Unit,
) : android.webkit.WebChromeClient() {

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean {
        // 桌面站"登录"走 window.open 子窗（二维码页在其中渲染）。
        // 子窗必须交给界面显示（onPopup 挂到 Compose 里），否则登录无门。
        val newView = WebView(view.context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = QqLoginWebViewClient()   // 同样拦外部协议，防止子窗再跳出去
        }
        val transport = resultMsg.obj as? android.webkit.WebView.WebViewTransport ?: return false
        transport.setWebView(newView)
        resultMsg.sendToTarget()
        onPopup(newView)
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        onClosePopup()
    }

    override fun onJsAlert(
        view: WebView,
        url: String?,
        message: String?,
        result: android.webkit.JsResult,
    ): Boolean {
        android.app.AlertDialog.Builder(view.context)
            .setMessage(message.orEmpty())
            .setPositiveButton("确定") { _, _ -> result.confirm() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: android.webkit.JsResult,
    ): Boolean {
        android.app.AlertDialog.Builder(view.context)
            .setMessage(message.orEmpty())
            .setPositiveButton("确定") { _, _ -> result.confirm() }
            .setNegativeButton("取消") { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }
}
