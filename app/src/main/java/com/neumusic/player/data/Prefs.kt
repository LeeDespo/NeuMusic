package com.neumusic.player.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** 播放音质档位（由组件转换为对应文件类型，扩展名用于 Android 下载命名）。 */
enum class Quality(val label: String, val ext: String) {
    STANDARD("标准 128k", ".mp3"),
    HQ("高品质 320k", ".mp3"),
    AAC96("流畅 96k（AAC）", ".m4a"),
    OGG192("高品质 192k（OGG）", ".ogg"),
    OGG320("极高 320k（OGG）", ".ogg"),
    FLAC("无损 FLAC", ".flac");

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
 * 光影的时间行为：随时间变化（跟着真实时钟走）或固定光影（停在选定的时刻）。
 *
 * 注意：**选哪一套曲线（白天/黑夜）由深色模式决定**，不由这两个选项决定——
 * 浅色主题一直是白天那套、深色主题一直是黑夜那套、随系统则跟着系统深浅色走。
 */
enum class LightingMode(val label: String) {
    TIME("随时间变化"),
    FIXED("固定光影");
}

/**
 * 轻量配置存取：组件登录凭据、音质、播放模式、
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
        com.neumusic.player.data.api.HelperNext.init(context.applicationContext, sp)
        // 把磁盘里的持久值灌进响应式通道，供 UI 首帧就取到正确主题。
        themeFlow.value = themeMode
        accentFlow.value = accentMode
        reliefFlow.value = relief
        lyricSizeFlow.value = lyricTextSize
        lyricTransFlow.value = showLyricTranslation
        downloadDirFlow.value = downloadDir
        downloadQualityFlow.value = downloadQuality
        barVizFlow.value = barViz
        vinylModeFlow.value = vinylMode
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

    /** 是否显示歌词音译（罗马音行；仅曲带 roma 数据时才有内容）。 */
    var showLyricRoman: Boolean
        get() = sp.getBoolean("showLyricRoman", false)
        set(v) = sp.edit().putBoolean("showLyricRoman", v).apply()

    /** 是否显示歌词注音（假名读音；仅曲带 kana 元数据时才有内容）。 */
    var showLyricKana: Boolean
        get() = sp.getBoolean("showLyricKana", false)
        set(v) = sp.edit().putBoolean("showLyricKana", v).apply()

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

    /**
     * 预设存储（JSON）：`{"presets":[{"name":"Pop","gains":[毫贝×5],"builtin":true}],…}`。
     * 内置预设首次挂载时整表快照进来，此后**所有预设都可改**（拖滑杆即写回当前预设），
     * 用户也可以新增/删除自己的预设。由 EqualizerHost 读写，UI 不直接碰。
     */
    var eqStore: String
        get() = sp.getString("eqStore", "") ?: ""
        set(v) = sp.edit().putString("eqStore", v).apply()

    /** 内置预设是否已完成快照（只做一次）。 */
    var eqSeeded: Boolean
        get() = sp.getBoolean("eqSeeded", false)
        set(v) = sp.edit().putBoolean("eqSeeded", v).apply()

    /** 当前选中的预设名；null = 手动（不挂预设）。 */
    var eqSelected: String?
        get() = sp.getString("eqSelected", null)
        set(v) = sp.edit().putString("eqSelected", v).apply()

    /** 曲风码 → 预设名（智能调音的映射，UI 可改）。JSON：`{"1":"Pop","22":"Rock"}`。 */
    var eqGenreMap: String
        get() = sp.getString("eqGenreMap", "") ?: ""
        set(v) = sp.edit().putString("eqGenreMap", v).apply()

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

    /** 音效 v2：保留旧预设键供迁移；精确引擎参数独立保存。 */
    var eqEngine: String
        get() = sp.getString("eqEngine", "PLATFORM") ?: "PLATFORM"
        set(v) = sp.edit().putString("eqEngine", v).apply()
    var eqStoreV2: String
        get() = sp.getString("eqStoreV2", "") ?: ""
        set(v) = sp.edit().putString("eqStoreV2", v).apply()
    var eqPreciseBands: IntArray
        get() = sp.getString("eqPreciseBands", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray() ?: IntArray(10)
        set(v) = sp.edit().putString("eqPreciseBands", v.joinToString(",")).apply()
    var eqPreampDb: Float
        get() = sp.getFloat("eqPreampDb", 0f)
        set(v) = sp.edit().putFloat("eqPreampDb", v).apply()
    var eqDvcMode: String
        get() = sp.getString("eqDvcMode", null) ?: if (eqDvc) "medium" else "off"
        set(v) = sp.edit().putString("eqDvcMode", v).apply()
    var eqLimiterEnabled: Boolean
        get() = sp.getBoolean("eqLimiterEnabled", true)
        set(v) = sp.edit().putBoolean("eqLimiterEnabled", v).apply()
    var eqLimiterThreshold: Float
        get() = sp.getFloat("eqLimiterThreshold", -2f)
        set(v) = sp.edit().putFloat("eqLimiterThreshold", v).apply()
    var eqLimiterRelease: Float
        get() = sp.getFloat("eqLimiterRelease", 60f)
        set(v) = sp.edit().putFloat("eqLimiterRelease", v).apply()
    var eqLoudness: Int
        get() = sp.getInt("eqLoudness", 0)
        set(v) = sp.edit().putInt("eqLoudness", v).apply()
    var vizPermissionAsked: Boolean
        get() = sp.getBoolean("vizPermissionAsked", false)
        set(v) = sp.edit().putBoolean("vizPermissionAsked", v).apply()

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

    /** 结构化诊断日志开关（默认开，关着时 AppLog 完全静默）。 */
    var loggingEnabled: Boolean
        get() = sp.getBoolean("loggingEnabled", true)
        set(v) = sp.edit().putBoolean("loggingEnabled", v).apply()

    /** 诊断日志存储上限（MB），超限自动裁掉前一半。 */
    var logMaxMb: Int
        get() = sp.getInt("logMaxMb", 2)
        set(v) = sp.edit().putInt("logMaxMb", v.coerceIn(1, 64)).apply()

    /** 黑胶唱片模式：播放页封面按唱片机样式呈现（宽画框 + 细纹路 + 播放时旋转）。 */
    var vinylMode: Boolean
        get() = sp.getBoolean("vinylMode", false)
        set(v) {
            sp.edit().putBoolean("vinylMode", v).apply()
            vinylModeFlow.value = v
        }

    // ── 光影（随时间变化的新拟物光照，模型见 shade/DayLight.kt）──

    /** 光影的时间行为：随时间变化 / 固定光影。 */
    var lightingMode: LightingMode
        get() = LightingMode.entries.firstOrNull { it.name == sp.getString("lightingMode", null) }
            ?: LightingMode.FIXED
        set(v) = sp.edit().putString("lightingMode", v.name).apply()

    /** 固定光影模式下的时刻（0..24，0.5h 对齐）。 */
    var lightingFixedHour: Float
        get() = sp.getFloat("lightingFixedHour", 12f)
        set(v) = sp.edit().putFloat("lightingFixedHour", v.coerceIn(0f, 24f)).apply()

    /** 色温标定：白天最暖（2000K 日出/日落）。 */
    var lightingDayWarm: Float
        get() = sp.getFloat("lightingDayWarm", 0.53f)
        set(v) = sp.edit().putFloat("lightingDayWarm", v.coerceIn(-1f, 1f)).apply()

    /** 色温标定：白天最冷（5500K 正午）。 */
    var lightingDayCold: Float
        get() = sp.getFloat("lightingDayCold", -0.23f)
        set(v) = sp.edit().putFloat("lightingDayCold", v.coerceIn(-1f, 1f)).apply()

    /** 色温标定：黑夜（8000K 月光，整夜恒定）。 */
    var lightingNightWarm: Float
        get() = sp.getFloat("lightingNightWarm", -0.78f)
        set(v) = sp.edit().putFloat("lightingNightWarm", v.coerceIn(-1f, 1f)).apply()

    /** 光影标定：暗色阴影最大偏移（dp，基准 6dp 组件；日出/日落处取到）。 */
    var lightingDarkMax: Float
        get() = sp.getFloat("lightingDarkMax", 10f)
        set(v) = sp.edit().putFloat("lightingDarkMax", v.coerceIn(0f, 40f)).apply()

    /** 光影标定：暗色阴影最小偏移（dp；正午/午夜处取到）。 */
    var lightingDarkMin: Float
        get() = sp.getFloat("lightingDarkMin", 3.5f)
        set(v) = sp.edit().putFloat("lightingDarkMin", v.coerceIn(0f, 40f)).apply()

    /** 光影标定：高光阴影最大偏移（dp；与暗色阴影共用同一条曲线）。 */
    var lightingLightMax: Float
        get() = sp.getFloat("lightingLightMax", 5f)
        set(v) = sp.edit().putFloat("lightingLightMax", v.coerceIn(0f, 40f)).apply()

    /** 光影标定：高光阴影最小偏移（dp；默认与暗色阴影的最小白一致）。 */
    var lightingLightMin: Float
        get() = sp.getFloat("lightingLightMin", 3.5f)
        set(v) = sp.edit().putFloat("lightingLightMin", v.coerceIn(0f, 40f)).apply()

    /** 响应式通道：初值在 [init] 时从磁盘读一次。 */
    val themeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val accentFlow = MutableStateFlow(AccentMode.CLASSIC)
    val reliefFlow = MutableStateFlow(Relief.NORMAL)
    val lyricSizeFlow = MutableStateFlow(LyricTextSize.NORMAL)
    val lyricTransFlow = MutableStateFlow(true)
    val downloadDirFlow = MutableStateFlow(DownloadDir.MUSIC_NEUMUSIC)
    val downloadQualityFlow = MutableStateFlow(Quality.STANDARD)
    val barVizFlow = MutableStateFlow(false)
    val vinylModeFlow = MutableStateFlow(false)

    val credential: CredentialInfo?
        get() = com.neumusic.player.data.api.HelperNext.credential()

    fun saveCredential(info: CredentialInfo) {
        com.neumusic.player.data.api.HelperNext.saveCredential(info)
        sp.edit().remove("credential").commit()
        NicknameCache.set(null)
        com.neumusic.player.data.api.LyricApi.clear()
    }

    fun clearCredential() {
        com.neumusic.player.data.api.HelperNext.clearCredential()
        sp.edit().remove("credential").commit()
        NicknameCache.set(null)
        com.neumusic.player.data.api.LyricApi.clear()
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
