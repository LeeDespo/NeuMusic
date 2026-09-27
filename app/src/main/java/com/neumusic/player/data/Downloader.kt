package com.neumusic.player.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.neumusic.player.data.api.SongApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 下载歌曲：完全原生 —— SongApi.playUrl（登录凭据使 VIP 可取）拿直链，
 * OkHttp 拉流写入公共 Music/NeuMusic 目录（MediaStore）。
 */
object Downloader {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun download(context: Context, track: Track, quality: Quality = Prefs.downloadQuality) = withContext(Dispatchers.IO) {
        val url = try {
            SongApi.playUrl(track, quality)
        } catch (e: Exception) {
            // message 已是人话（会员权限/限流/网络），直接展示。
            toast(context, "下载失败：${e.message}")
            return@withContext
        }
        val displayName = "${track.singer} - ${track.name}${quality.ext}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    toast(context, "下载失败：HTTP ${resp.code}")
                    return@withContext
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 关键：MediaStore.Downloads 只允许 Download/ 主目录；
                    // 存到 Music/ 必须用 Audio 集合，否则 insert 直接被拒
                    // （"Primary directory Music not allowed for content://media/external/downloads"）。
                    val isMusic = Prefs.downloadDir.relativePath.startsWith(Environment.DIRECTORY_MUSIC)
                    val collection = if (isMusic) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(quality.ext))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Prefs.downloadDir.relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(collection, values)
                        ?: throw IllegalStateException("MediaStore insert 失败")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        resp.body!!.byteStream().copyTo(out)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                } else {
                    toast(context, "需要 Android 10+ 才能保存到公共音乐目录")
                    return@withContext
                }
            }
            DownloadStore.mark(
                DownloadRecord(
                    mid = track.mid, name = track.name, singer = track.singer,
                    quality = quality.label, fileName = displayName,
                    timeMs = System.currentTimeMillis(),
                )
            )
            toast(context, "已下载：$displayName")
        } catch (e: Exception) {
            toast(context, "下载失败：${e.message}")
        }
    }

    private fun mimeOf(ext: String): String = when (ext) {
        ".mp3" -> "audio/mpeg"
        ".m4a" -> "audio/mp4"
        ".ogg" -> "audio/ogg"
        ".flac" -> "audio/flac"
        else -> "audio/mpeg"
    }

    private fun toast(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
