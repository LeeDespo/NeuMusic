package com.neumusic.player.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.UUID

/** 播放音质档位（对应 UrlGetVkey 的 filename 前缀，全部实测可取 purl）。 */
enum class Quality(val label: String, val prefix: String, val ext: String) {
    STANDARD("标准 128k", "M500", ".mp3"),
    HQ("高品质 320k", "M800", ".mp3"),
    AAC96("流畅 96k（AAC）", "C400", ".m4a"),
    OGG192("高品质 192k（OGG）", "O600", ".ogg"),
    OGG320("极高 320k（OGG）", "O800", ".ogg"),
    FLAC("无损 FLAC", "F000", ".flac");

    companion object {
        fun of(name: String?): Quality = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** 播放模式。 */
enum class PlayMode(val label: String) {
    ORDER("顺序播放"), SHUFFLE("随机播放"), REPEAT_ONE("单曲循环");

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}

/** 强调色来源：经典配色，或 Android 12+ 的壁纸取色（莫奈/Material You）。 */
enum class AccentMode(val label: String) {
    CLASSIC("经典"),
    MONET("跟随壁纸取色");

    fun next(): AccentMode = entries[(ordinal + 1) % entries.size]
}

/** 深色模式。 */
enum class ThemeMode(val label: String) {
    SYSTEM("随系统"), LIGHT("浅色"), DARK("深色");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** 下载保存目录（公共目录，MediaStore RELATIVE_PATH）。 */
enum class DownloadDir(val label: String, val relativePath: String) {
    MUSIC_NEUMUSIC("音乐/NeuMusic", Environment.DIRECTORY_MUSIC + "/NeuMusic"),
    MUSIC("音乐（根目录）", Environment.DIRECTORY_MUSIC),
    DOWNLOAD_NEUMUSIC("下载/NeuMusic", Environment.DIRECTORY_DOWNLOADS + "/NeuMusic"),
}

/** 立体感强度：影响阴影位移/模糊与补光强度。 */
enum class Relief(val label: String, val scale: Float) {
    SUBTLE("弱", 0.7f), NORMAL("标准", 1.0f), STRONG("强", 1.45f);

    fun next(): Relief = entries[(ordinal + 1) % entries.size]
}

/**
 * 轻量配置存取：登录凭据（uin + musickey + euin）、guid、音质、播放模式、
 * 深色模式、立体感强度。
 *
 * 关于昵称：不持久化。登录后从 GetLoginUserInfo 拉取缓存于内存（见 [NicknameCache]）。
 *
 * 关于响应式：这些值要驱动整个 Compose 树重建，所以除写盘外还各带一个
 * `MutableStateFlow`，由 UI 层 `collectAsState` 订阅，避免改了设置界面不动。
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("neumusic", Context.MODE_PRIVATE)
        // 把磁盘里的持久值灌进响应式通道，供 UI 首帧就取到正确主题。
        themeFlow.value = themeMode
        accentFlow.value = accentMode
        reliefFlow.value = relief
        lyricSizeFlow.value = lyricTextSize
        lyricTransFlow.value = showLyricTranslation
        downloadDirFlow.value = downloadDir
        downloadQualityFlow.value = downloadQuality
        barVizFlow.value = barViz
    }

    val guid: String
        get() {
            var g = sp.getString("guid", null)
            if (g == null) {
                g = UUID.randomUUID().toString().replace("-", "")
                sp.edit().putString("guid", g).apply()
            }
            return g
        }

    /** 稳定的设备指纹字段（服务端接受自报的 QIMEI，无需真实置备）。 */
    val qimei: String
        get() {
            var q = sp.getString("qimei", null)
            if (q == null) {
                q = UUID.randomUUID().toString().replace("-", "")
                sp.edit().putString("qimei", q).apply()
            }
            return q
        }

    var quality: Quality
        get() = Quality.of(sp.getString("quality", null))
        set(v) = sp.edit().putString("quality", v.name).apply()

    var playMode: PlayMode
        get() = PlayMode.entries.firstOrNull { it.name == sp.getString("playMode", null) } ?: PlayMode.ORDER
        set(v) = sp.edit().putString("playMode", v.name).apply()

    /** 深色模式。变更会推送 [themeFlow]，界面据此即时重绘。 */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == sp.getString("themeMode", null) } ?: ThemeMode.SYSTEM
        set(v) {
            sp.edit().putString("themeMode", v.name).apply()
            themeFlow.value = v
        }

    /** 立体感强度。变更会推送 [reliefFlow]。 */
    var relief: Relief
        get() = Relief.entries.firstOrNull { it.name == sp.getString("relief", null) } ?: Relief.NORMAL
        set(v) {
            sp.edit().putString("relief", v.name).apply()
            reliefFlow.value = v
        }

    /** 歌词文字大小。 */
    var lyricTextSize: LyricTextSize
        get() = LyricTextSize.entries.firstOrNull { it.name == sp.getString("lyricTextSize", null) }
            ?: LyricTextSize.NORMAL
        set(v) {
            sp.edit().putString("lyricTextSize", v.name).apply()
            lyricSizeFlow.value = v
        }

    /** 是否显示歌词翻译（仅在曲目确实带翻译时才有内容）。 */
    var showLyricTranslation: Boolean
        get() = sp.getBoolean("showLyricTranslation", true)
        set(v) {
            sp.edit().putBoolean("showLyricTranslation", v).apply()
            lyricTransFlow.value = v
        }

    /** 下载保存目录（MediaStore RELATIVE_PATH，限公共音乐/下载目录）。 */
    var downloadDir: DownloadDir
        get() = DownloadDir.entries.firstOrNull { it.name == sp.getString("downloadDir", null) }
            ?: DownloadDir.MUSIC_NEUMUSIC
        set(v) {
            sp.edit().putString("downloadDir", v.name).apply()
            downloadDirFlow.value = v
        }

    /** 下载音质（独立于在线播放音质）。 */
    var downloadQuality: Quality
        get() = Quality.of(sp.getString("downloadQuality", null))
        set(v) {
            sp.edit().putString("downloadQuality", v.name).apply()
            downloadQualityFlow.value = v
        }

    /** 强调色模式。变更推送 [accentFlow]，ShadeTheme 据此重建整套颜色。 */
    var accentMode: AccentMode
        get() = AccentMode.entries.firstOrNull { it.name == sp.getString("accentMode", null) }
            ?: AccentMode.CLASSIC
        set(v) {
            sp.edit().putString("accentMode", v.name).apply()
            accentFlow.value = v
        }

    // ── 均衡器 ──
    var eqEnabled: Boolean
        get() = sp.getBoolean("eqEnabled", false)
        set(v) = sp.edit().putBoolean("eqEnabled", v).apply()

    /** 预设下标；-1 = 自定义。 */
    var eqPreset: Int
        get() = sp.getInt("eqPreset", -1)
        set(v) = sp.edit().putInt("eqPreset", v).apply()

    /** 各频段电平（毫贝），逗号分隔。 */
    var eqBands: IntArray
        get() = sp.getString("eqBands", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray()
            ?: IntArray(0)
        set(v) = sp.edit().putString("eqBands", v.joinToString(",")).apply()

    /** 低音增强 0..1000。 */
    var eqBass: Int
        get() = sp.getInt("eqBass", 0)
        set(v) = sp.edit().putInt("eqBass", v).apply()

    /** 动态范围压缩（DVC，夜间听歌压响度差）。 */
    var eqDvc: Boolean
        get() = sp.getBoolean("eqDvc", false)
        set(v) = sp.edit().putBoolean("eqDvc", v).apply()

    /** 声道平衡 -100（左）..100（右），0=居中。 */
    var eqBalance: Int
        get() = sp.getInt("eqBalance", 0)
        set(v) = sp.edit().putInt("eqBalance", v).apply()

    /** 播放速度 0.5..2.0（1=原速）。 */
    var playSpeed: Float
        get() = sp.getFloat("playSpeed", 1f)
        set(v) = sp.edit().putFloat("playSpeed", v).apply()

    /** 音调 0.5..2.0（1=原调）。 */
    var playPitch: Float
        get() = sp.getFloat("playPitch", 1f)
        set(v) = sp.edit().putFloat("playPitch", v).apply()

    /** 智能调音：按曲目风格自动套用均衡器预设。 */
    var smartEq: Boolean
        get() = sp.getBoolean("smartEq", false)
        set(v) = sp.edit().putBoolean("smartEq", v).apply()

    /** 播放条音频可视化。变更推送 [barVizFlow]。 */
    var barViz: Boolean
        get() = sp.getBoolean("barViz", false)
        set(v) {
            sp.edit().putBoolean("barViz", v).apply()
            barVizFlow.value = v
        }

    /** ExoPlayer 的音频会话 id（PlayerHost 创建播放器后写入，供各音效挂载）。 */
    @Volatile
    var sessionIdForFx: Int = 0
        set(v) {
            field = v
        }

    /** 响应式通道：初值在 [init] 时从磁盘读一次。 */
    val themeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val accentFlow = MutableStateFlow(AccentMode.CLASSIC)
    val reliefFlow = MutableStateFlow(Relief.NORMAL)
    val lyricSizeFlow = MutableStateFlow(LyricTextSize.NORMAL)
    val lyricTransFlow = MutableStateFlow(true)
    val downloadDirFlow = MutableStateFlow(DownloadDir.MUSIC_NEUMUSIC)
    val downloadQualityFlow = MutableStateFlow(Quality.STANDARD)
    val barVizFlow = MutableStateFlow(false)

    val credential: CredentialInfo?
        get() {
            val raw = sp.getString("credential", null) ?: return null
            return runCatching {
                val o = JSONObject(raw)
                CredentialInfo(o.getString("uin"), o.getString("musickey"), o.optString("euin"))
            }.getOrNull()
        }

    fun saveCredential(info: CredentialInfo) {
        sp.edit().putString("credential", JSONObject().apply {
            put("uin", info.uin); put("musickey", info.musickey); put("euin", info.euin)
        }.toString()).apply()
        NicknameCache.set(null)
    }

    fun clearCredential() {
        sp.edit().remove("credential").apply()
        NicknameCache.set(null)
    }
}

data class CredentialInfo(val uin: String, val musickey: String, val euin: String = "")

/** 登录用户昵称的内存缓存（用于主页问候语）。 */
object NicknameCache {
    @Volatile
    private var value: String? = null

    fun get(): String? = value
    fun set(v: String?) { value = v }
}
