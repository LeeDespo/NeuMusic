package com.neumusic.player.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.core.net.toUri
import com.neumusic.player.data.Lyrics
import com.neumusic.player.data.api.LyricApi
import com.neumusic.player.data.PlayMode
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Track
import com.neumusic.player.player.EqualizerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 全局播放器宿主：ExoPlayer 单例 + 播放队列 / 模式 / 喜欢状态。
 * 所有播放器操作投递到主线程。
 */
object PlayerHost {
    private var player: ExoPlayer? = null
    private var mediaSession: androidx.media3.session.MediaSession? = null
    private val main = Handler(Looper.getMainLooper())

    /** 播放条可视化的 PCM 数据源（透传处理器，免 RECORD_AUDIO 权限）。 */
    val vizProcessor = VizProcessor()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** 当前曲目是否已在「我喜欢」。 */
    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked

    /** 当前曲目的歌词；null = 尚未加载或无歌词。 */
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics

    /** 播放队列与当前位置，供上一首/下一首使用。 */
    private var queue: List<Track> = emptyList()
    private var index = -1

    /** 随机模式下"真正播放过的曲目"栈：上一首按它回跳。 */
    private val playedHistory = ArrayDeque<Int>()

    /** 取链回调由 UI 层注入（需要 suspend 访问网络层）。 */
    var resolveUrl: (suspend (Track) -> String?)? = null

    /** 全局错误回调（AppRoot 注入 toast）。自动切换失败也必须可见，不能静默。 */
    var onError: ((String) -> Unit)? = null

    private val defaultOnError: (String) -> Unit = { msg -> onError?.invoke(msg) }

    /** 「下一首播放」待播队列：自动切歌与手动下一首都优先消费它（随机模式下也保证先播）。 */
    private val pendingNext = ArrayDeque<Track>()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            // 播放中必须有一个 mediaPlayback 类型的前台服务，否则系统会在后台
            // 直接把进程回收（真机实测：后台播放一两分钟自动停）。暂停后不主动停，
            // 由 MediaSessionService 自己按空闲规则收尾。
            val t = _current.value
            if (isPlaying) {
                if (t != null) PlaybackService.start(requireNotNull(appContext), t.name, t.singer, t.coverUrl, true)
            } else if (t != null) {
                PlaybackService.stop(requireNotNull(appContext))
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) advance()
        }
    }

    private var appContext: Context? = null

    /** 供前台服务使用：确保播放器已创建（必须在主线程调用，服务与 App 同进程）。 */
    fun ensurePlayer(context: Context) {
        init(context)
    }

    /** 供前台服务包装 MediaSession 用（服务不持有所有权，见 PlaybackService）。 */
    fun exoPlayer(): ExoPlayer? = player

    fun init(context: Context) {
        appContext = context.applicationContext
        if (player != null) return
        main.post {
            val renderersFactory = object : DefaultRenderersFactory(context.applicationContext) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(vizProcessor))
                        .build()
                }
            }
            player = ExoPlayer.Builder(context.applicationContext, renderersFactory).build().also {
                it.addListener(listener)
                // MediaSession：锁屏/耳机媒体键的控制入口。前台服务只负责「前台身份 + 通知」，
                // 会话挂在这里（进程内单例），与播放器同生共死。
                runCatching {
                    mediaSession = androidx.media3.session.MediaSession.Builder(context.applicationContext, it)
                        .setSessionActivity(
                            android.app.PendingIntent.getActivity(
                                context.applicationContext, 0,
                                android.content.Intent(context.applicationContext, com.neumusic.player.MainActivity::class.java),
                                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                            )
                        )
                        .build()
                }
                it.repeatMode = repeatOf(Prefs.playMode)
                // 均衡器/动态处理挂到播放器的音频会话上（设备不支持时静默降级）。
                runCatching {
                    var sid = it.audioSessionId
                    if (sid == 0) {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        sid = am.generateAudioSessionId()
                        it.setAudioSessionId(sid)
                    }
                    Prefs.sessionIdForFx = sid
                    EqualizerHost.attach(sid)
                    DynamicsFxHost.attach(sid)
                    VizHost.attach(sid)
                    applyPlaybackParams()
                }
            }
        }
    }

    var playMode: PlayMode
        get() = Prefs.playMode
        set(v) {
            Prefs.playMode = v
            main.post { player?.repeatMode = repeatOf(v) }
        }

    private fun repeatOf(m: PlayMode): Int = when (m) {
        PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        // 顺序/随机必须 REPEAT_MODE_OFF：我们一次只 set 单曲，
        // repeat-all 会把这一首无限循环（"播完自动切换只会重复播放"的根因），
        // 队列推进统一由 onEnded -> advance() 负责。
        PlayMode.ORDER, PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
    }

    /** 设置队列并从第 [startAt] 首开始播放。 */
    fun playQueue(tracks: List<Track>, startAt: Int, onError: (String) -> Unit = {}) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startAt.coerceIn(0, tracks.size - 1)
        playedHistory.clear()
        pendingNext.clear()
        playCurrent(onError)
    }

    private fun playCurrent(onError: (String) -> Unit) {
        val track = queue.getOrNull(index) ?: return
        _current.value = track
        _liked.value = false
        _lyrics.value = null
        val resolver = resolveUrl ?: run { onError("播放器未就绪"); return }
        CoroutineScope(Dispatchers.Main).launch {
            val result = runCatching { resolver(track) }
            val url = result.getOrNull()
            if (url == null) {
                // 异常 message 已是人话（VIP 权限/限流/网络），直接透传。
                onError(result.exceptionOrNull()?.message ?: "拿不到播放链接（可能限流，请重试）")
            } else startInternal(track, url, onError)
        }
        // 歌词与播放链接并行拉取，互不阻塞。
        CoroutineScope(Dispatchers.Main).launch {
            _lyrics.value = runCatching { LyricApi.lyricsFor(track.mid) }.getOrNull()
        }
        // 智能调音：开启时按曲目风格自动套用对应预设（无映射/未知风格保持现状）。
        CoroutineScope(Dispatchers.Main).launch {
            if (Prefs.smartEq) SmartEq.applyFor(track)
        }
    }

    private fun startInternal(track: Track, url: String, onError: (String) -> Unit) {
        main.post {
            val p = player ?: return@post
            try {
                // 带上元数据：前台服务的媒体通知 / 锁屏控件靠它显示歌名歌手封面
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaId(track.mid)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(track.name)
                                .setArtist(track.singer)
                                .setArtworkUri(track.coverUrl.takeIf { it.isNotEmpty() }?.toUri())
                                .build()
                        )
                        .build()
                )
                p.repeatMode = repeatOf(Prefs.playMode)
                p.prepare()
                p.play()
            } catch (e: Exception) {
                onError(e.message ?: "播放失败")
            }
        }
    }

    /** 暂停（通知栏「关闭」用）。 */
    fun pause() {
        main.post { player?.pause() }
    }

    fun toggle() {
        main.post {
            val p = player ?: return@post
            // 关键修复：取链接失败（如限流）后播放器处于 IDLE；自动切换失败后处于
            // ENDED（advance 失败不会再有下一首）。这两种状态点"播放"都不能只是
            // play()——对 IDLE 无效（"按钮点不动"），对 ENDED 会把刚放完的这首歌
            // 从头再放一遍（"自动切换变成重播"的观感）——都要为当前曲目重新解析。
            if ((p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED)
                && _current.value != null
            ) {
                playCurrent(defaultOnError)
                return@post
            }
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        recordHistory()
        if (playPendingNext()) return
        index = pickNext()
        playCurrent(defaultOnError)
    }

    /**
     * 「下一首播放」：把 [track] 排到当前曲目之后，**保证**下一次切歌（自动或手动）
     * 先播它 —— 随机模式下也一样，所以用待播队列而不是只动队列下标。
     * 早期版本直接 `index = cur + 1` 却不播，结果 ENDED 时 advance 又从那个下标
     * 往前跳了一格，"下一首播放"被整个跳过。
     */
    fun playNext(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        queue = queue.filterNot { it.mid == track.mid }
        pendingNext.removeAll { it.mid == track.mid }
        val cur = index.coerceIn(0, queue.lastIndex)
        queue = queue.take(cur + 1) + track + queue.drop(cur + 1)
        pendingNext.addLast(track)
    }

    /** 下一首（自动/手动共用）：有待播队列就先播它。返回是否消费了待播曲目。 */
    private fun playPendingNext(): Boolean {
        val pending = pendingNext.removeFirstOrNull() ?: return false
        val i = queue.indexOfFirst { it.mid == pending.mid }
        if (i >= 0) {
            index = i
        } else {
            // 待播曲目不在队列里（队列被换过）：插到当前曲目之后补进来。
            val cur = index.coerceIn(0, queue.lastIndex)
            queue = queue.take(cur + 1) + pending + queue.drop(cur + 1)
            index = cur + 1
        }
        playCurrent(defaultOnError)
        return true
    }

    /**
     * 上一首：随机模式按**播放历史**回跳（回到真正上一首播过的歌）；
     * 历史为空（刚开播就按上一首）按顺序回退 —— 早前回退到「随机选一首」，
     * 用户报的"随机上一首不是播过的上一首"就是这个。顺序/单曲循环按下标回退（循环）。
     */
    fun previous() {
        if (queue.isEmpty()) return
        if (Prefs.playMode == PlayMode.SHUFFLE) {
            val prev = playedHistory.removeLastOrNull()
            if (prev != null && prev != index) {
                index = prev
                playCurrent(defaultOnError)
                return
            }
        }
        index = if (index - 1 < 0) queue.lastIndex else index - 1
        playCurrent(defaultOnError)
    }

    /** 当前曲目入历史栈（供随机"上一首"回跳），避免栈顶连续重复。 */
    private fun recordHistory() {
        if (index >= 0 && (playedHistory.isEmpty() || playedHistory.last() != index)) {
            playedHistory.addLast(index)
            if (playedHistory.size > 64) playedHistory.removeFirst()
        }
    }

    /** 供队列弹窗展示。 */
    fun queueSnapshot(): List<Track> = queue

    fun currentIndex(): Int = index

    /** 跳到队列第 [i] 首播放。 */
    fun playAt(i: Int) {
        if (i < 0 || i >= queue.size) return
        recordHistory()
        index = i
        playCurrent(defaultOnError)
    }

    /** 播放结束自动前进。单曲循环由 REPEAT_MODE_ONE 处理，不会走到这里。 */
    private fun advance() {
        if (queue.isEmpty()) return
        recordHistory()
        if (playPendingNext()) return
        index = pickNext()
        playCurrent(defaultOnError)
    }

    private fun pickNext(): Int = when (Prefs.playMode) {
        PlayMode.SHUFFLE -> if (queue.size == 1) 0 else queue.indices.filter { it != index }.random()
        else -> (index + 1) % queue.size
    }

    fun setLiked(v: Boolean) { _liked.value = v }

    // ── 播放速度 / 音调（media3 PlaybackParameters 原生支持）──

    private val _speed = MutableStateFlow(Prefs.playSpeed)
    val speed: StateFlow<Float> = _speed

    private val _pitch = MutableStateFlow(Prefs.playPitch)
    val pitch: StateFlow<Float> = _pitch

    /** 同时改速度与音调（范围 0.5..2.0），并持久化。 */
    fun setPlaybackParams(speed: Float, pitch: Float) {
        val sp = speed.coerceIn(0.5f, 2f)
        val pt = pitch.coerceIn(0.5f, 2f)
        Prefs.playSpeed = sp
        Prefs.playPitch = pt
        _speed.value = sp
        _pitch.value = pt
        applyPlaybackParams()
    }

    private fun applyPlaybackParams() {
        main.post {
            player?.playbackParameters = PlaybackParameters(Prefs.playSpeed, Prefs.playPitch)
        }
    }

    fun seekTo(ms: Long) {
        main.post { player?.seekTo(ms) }
    }

    fun positionMs(): Long = player?.currentPosition ?: 0L

    fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: 0L
}
