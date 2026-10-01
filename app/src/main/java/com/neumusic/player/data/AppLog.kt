package com.neumusic.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内**结构化诊断日志**（2026-09-30 规格）：逐行 `时间 级别/标签: 消息 — 异常`，
 * 追加写入 filesDir/diagnostics.log。设置里可开关、可清空（垃圾桶圆钮）、可限制存储上限
 * 自动裁剪（超限砍掉前一半）、可导出（SAF 选位置）。
 * 开关关着时完全静默（不写盘）；同时镜像一份到 logcat 方便开发。
 */
object AppLog {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var file: File? = null
    private val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostics.log")
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        if (!Prefs.loggingEnabled) return
        val line = buildString {
            append(ts.format(Date())).append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (t != null) append(" — ").append(t.javaClass.simpleName).append(": ").append(t.message)
        }
        android.util.Log.println(android.util.Log.INFO, tag, line)
        io.launch {
            val f = file ?: return@launch
            synchronized(this@AppLog) {
                runCatching {
                    f.appendText(line + "\n")
                    val maxBytes = Prefs.logMaxMb.coerceIn(1, 64) * 1024L * 1024L
                    if (f.length() > maxBytes) {
                        val text = f.readText()
                        f.writeText(text.substring(text.length / 2))   // 裁掉前一半
                    }
                }
            }
        }
    }

    /** 清空（设置里的垃圾桶按钮）。 */
    fun clear() {
        io.launch {
            synchronized(this@AppLog) { runCatching { file?.takeIf { it.exists() }?.delete() } }
        }
    }

    fun readAll(): String =
        synchronized(this@AppLog) {
            file?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        }.orEmpty()

    /** 导出到用户选择的位置（SAF）。 */
    fun exportTo(resolver: android.content.ContentResolver, uri: android.net.Uri) {
        io.launch {
            runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(readAll().toByteArray())
                    out.flush()
                }
            }
        }
    }
}
