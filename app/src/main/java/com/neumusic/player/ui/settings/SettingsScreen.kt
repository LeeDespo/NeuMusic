package com.neumusic.player.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.neumusic.player.data.CredentialInfo
import com.neumusic.player.data.NicknameCache
import com.neumusic.player.data.AccentMode
import com.neumusic.player.data.DownloadDir
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
import com.neumusic.player.shade.flatPressable
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadePressable
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.home.DetailTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalShadeColors.current

    var cred by remember { mutableStateOf(Prefs.credential) }
    var showWebLogin by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(Prefs.quality) }
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var accentMode by remember { mutableStateOf(Prefs.accentMode) }
    var relief by remember { mutableStateOf(Prefs.relief) }
    var lyricSize by remember { mutableStateOf(Prefs.lyricTextSize) }
    var showTrans by remember { mutableStateOf(Prefs.showLyricTranslation) }
    var downloadDir by remember { mutableStateOf(Prefs.downloadDir) }
    var downloadQuality by remember { mutableStateOf(Prefs.downloadQuality) }
    var barViz by remember { mutableStateOf(Prefs.barViz) }

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

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("设置", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).imePadding(),
        ) {
            // ── 账号 ──
            SectionTitle("账号")
            Column(
                Modifier.fillMaxWidth()
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
            Spacer(Modifier.height(18.dp))

            // ── 音质 ──
            SectionTitle("播放音质")
            Column(
                Modifier.fillMaxWidth()
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
            Spacer(Modifier.height(18.dp))

            // ── 外观 ──
            SectionTitle("外观")
            Column(
                Modifier.fillMaxWidth()
                    .shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 深色模式：三个选项，选中的那行凹陷（凸起容器内以凹陷表选中）。
                Text(
                    "深色模式", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
                ThemeMode.entries.forEach { m ->
                    ChoiceRow(
                        label = m.label,
                        selected = m == themeMode,
                        onClick = {
                            Prefs.themeMode = m
                            themeMode = m
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
                AccentMode.entries.forEach { a ->
                    ChoiceRow(
                        label = a.label,
                        selected = a == accentMode,
                        onClick = {
                            Prefs.accentMode = a
                            accentMode = a
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
                Relief.entries.forEach { r ->
                    ChoiceRow(
                        label = r.label,
                        selected = r == relief,
                        onClick = {
                            Prefs.relief = r
                            relief = r
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
                LyricTextSize.entries.forEach { t ->
                    ChoiceRow(
                        label = t.label,
                        selected = t == lyricSize,
                        onClick = {
                            Prefs.lyricTextSize = t
                            lyricSize = t
                        },
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 歌词翻译开关（只在曲目确实带翻译时才有内容 —— 多数曲目没有）
                Text(
                    "歌词翻译", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                ChoiceRow(
                    label = "显示翻译",
                    selected = showTrans,
                    onClick = { showTrans = true; Prefs.showLyricTranslation = true },
                )
                ChoiceRow(
                    label = "关闭翻译",
                    selected = !showTrans,
                    onClick = { showTrans = false; Prefs.showLyricTranslation = false },
                )
                Text(
                    "仅当曲目本身带翻译时才会显示（QQ 只对少部分曲目提供翻译，多数为空）。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.height(18.dp))

            // ── 下载 ──
            SectionTitle("下载")
            Column(
                Modifier.fillMaxWidth()
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

                Spacer(Modifier.height(4.dp))

                // 播放条音频可视化
                Text(
                    "播放栏", fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                ChoiceRow(
                    label = "播放条音频可视化",
                    selected = barViz,
                    onClick = { Prefs.barViz = !barViz; barViz = !barViz },
                )
                Text(
                    "在底部播放栏显示随音乐起伏的电平条。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.height(18.dp))

            // ── 关于 ──
            SectionTitle("关于")
            Column(
                Modifier.fillMaxWidth()
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
            Spacer(Modifier.height(24.dp))
        }
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
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = LocalShadeColors.current.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
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
