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

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) advance()
        }
    }

    fun init(context: Context) {
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
            } else startInternal(url, onError)
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

    private fun startInternal(url: String, onError: (String) -> Unit) {
        main.post {
            val p = player ?: return@post
            try {
                p.setMediaItem(MediaItem.fromUri(url))
                p.repeatMode = repeatOf(Prefs.playMode)
                p.prepare()
                p.play()
            } catch (e: Exception) {
                onError(e.message ?: "播放失败")
            }
        }
    }

    fun toggle() {
        main.post {
            val p = player ?: return@post
            // 关键修复：取链接失败（如限流）后播放器处于 IDLE，此时点"播放"
            // 不能只是 play()（对空播放器无效，表现为"按钮点不动"），
            // 而要为当前曲目重新解析链接再播。
            if (p.playbackState == Player.STATE_IDLE && _current.value != null) {
                playCurrent {}
                return@post
            }
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        recordHistory()
        index = pickNext()
        playCurrent {}
    }

    /**
     * 「下一首播放」：把 [track] 插到当前曲目之后。
     * 队列为空时等价于直接播放这一首（单曲队列）。
     * 已在队列中的同曲目会先移除再插入（避免重复）；插入点始终是"当前曲目之后"。
     */
    fun playNext(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val currentMid = queue.getOrNull(index)?.mid
        val dedup = queue.filterNot { it.mid == track.mid }
        // 去重后当前曲目下标可能前移：被移除的是当前曲时保持它在原位前，
        // 否则看被移除项是否在当前曲之前。
        var cur = index
        if (currentMid == track.mid) {
            cur = index.coerceAtMost(dedup.lastIndex)
        } else if (queue.take(index).any { it.mid == track.mid }) {
            cur -= 1
        }
        queue = dedup.take(cur + 1) + track + dedup.drop(cur + 1)
        index = cur + 1
    }

    /**
     * 上一首：随机模式按**播放历史**回跳（回到真正上一首播过的歌），
     * 顺序模式按下标回退（循环）。
     */
    fun previous() {
        if (queue.isEmpty()) return
        if (Prefs.playMode == PlayMode.SHUFFLE) {
            val prev = playedHistory.removeLastOrNull()
            if (prev != null && prev != index) {
                index = prev
                playCurrent {}
                return
            }
            val others = queue.indices.filter { it != index }
            if (others.isNotEmpty()) {
                index = others.random()
                playCurrent {}
            }
            return
        }
        index = if (index - 1 < 0) queue.lastIndex else index - 1
        playCurrent {}
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
        playCurrent {}
    }

    /** 播放结束自动前进。单曲循环由 REPEAT_MODE_ONE 处理，不会走到这里。 */
    private fun advance() {
        if (queue.isEmpty()) return
        recordHistory()
        index = pickNext()
        playCurrent {}
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
