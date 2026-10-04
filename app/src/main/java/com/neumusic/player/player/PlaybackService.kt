package com.neumusic.player.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 播放前台服务（用户 2026-10-01 真机反馈「后台播放一两分钟就自动停」的修复）。
 *
 * **Android 14+ 起，后台继续出声必须有 `foregroundServiceType=mediaPlayback` 的前台服务**，
 * 否则系统直接把进程回收。先试过 media3 的 `MediaSessionService`（指望它自动提升前台），
 * 实测在这台设备上它不提升（`startForegroundCount=0`、通知也没挂出），于是改成
 * **自己显式 startForeground**——这是硬要求，不能依赖库的隐式时机。
 *
 * 播放器本体仍归 [PlayerHost]（进程内单例），本服务只负责前台身份与通知；
 * 锁屏/媒体键由 PlayerHost 里的 MediaSession 负责。
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.neumusic.player.PLAYBACK_UPDATE"
        const val ACTION_TOGGLE = "com.neumusic.player.PLAYBACK_TOGGLE"
        const val ACTION_RELEASE = "com.neumusic.player.PLAYBACK_RELEASE"
        const val ACTION_STOP = "com.neumusic.player.PLAYBACK_STOP"

        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ART = "art"
        const val EXTRA_PLAYING = "playing"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "neumusic_playback"

        /** 播放开始时拉起（必须由前台的应用发起，后台启动前台服务会被系统限制）。 */
        fun start(context: Context, title: String, artist: String, art: String, playing: Boolean) {
            val i = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ART, art)
                putExtra(EXTRA_PLAYING, playing)
            }
            runCatching { context.startForegroundService(i) }
                .onFailure { runCatching { context.startService(i) } }
        }

        /** 暂停/停止时收摊（前台服务留着会白占一个常驻通知）。 */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PlaybackService::class.java).apply { action = ACTION_STOP }
                )
            }
        }
    }

    private var artwork: Bitmap? = null
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastPlaying = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                PlayerHost.toggle()
                return START_STICKY   // 通知由 PlayerHost 的播放态回调再刷一次
            }
            ACTION_RELEASE -> {
                PlayerHost.release()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                // Internal notification cleanup follows an already completed pause/release.
                // It must not pause a player that has since resumed or been recreated.
                stopForegroundCompat()
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifEmpty { "NeuMusic" }
        val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
        val art = intent?.getStringExtra(EXTRA_ART).orEmpty()
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
        startForegroundCompat(buildNotification(title, artist, art, playing))
        return START_STICKY
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
    }

    /** 封面解码不能压主线程，交给后台线程，拿到后再刷一次通知。 */
    private fun loadArtwork(url: String) {
        if (url.isEmpty()) return
        Thread {
            val bmp = runCatching {
                val bytes = java.net.URL(url).openStream().use { it.readBytes() }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: return@Thread
            artwork = bmp
            val nm = getSystemService(NotificationManager::class.java) ?: return@Thread
            runCatching { nm.notify(NOTIFICATION_ID, buildNotification(lastTitle, lastArtist, url, lastPlaying)) }
        }.start()
    }

    private fun buildNotification(title: String, artist: String, art: String, playing: Boolean): Notification {
        lastTitle = title; lastArtist = artist; lastPlaying = playing
        if (artwork == null && art.isNotEmpty()) loadArtwork(art)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.neumusic.player.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val close = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_RELEASE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放", toggle,
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", close)
            .build()
    }
}
