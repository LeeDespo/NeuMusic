package com.neumusic.player.data.api

import android.content.Context
import android.content.SharedPreferences
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.CredentialInfo
import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.callWithPlatform
import com.example.qqmusic_api_helper_next.initialize
import com.example.qqmusic_api_helper_next.importCredentialWithEncryptUin
import com.example.qqmusic_api_helper_next.loginStatus
import com.example.qqmusic_api_helper_next.logout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * Embedded Rust owns QQ requests, devices, credentials and request guards.
 *
 * 登录态：宿主只持有组件 typed loginStatus() 的**非敏感快照**（[login]，
 * null = 未登录或尚未解析），不读取、不解析组件私有凭据文件；凭据值仅作为
 * 导入前 DTO（[CredentialInfo]）即取即用。loginStatus() 需要上游应答，
 * 所以快照在 init/导入后于后台刷新，绝不在主线程同步等待。
 */
object HelperNext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 非敏感登录状态快照（typed loginStatus 的宿主映射）。 */
    private val _login = MutableStateFlow<LoginStatus?>(null)
    val login: StateFlow<LoginStatus?> = _login

    @Synchronized fun init(context: Context, preferences: SharedPreferences) {
        val directory = File(context.filesDir, "HelperNext")
        initialize(directory.absolutePath, "android")
        // 一次性旧凭据迁移与登录快照解析都要问组件（typed loginStatus，可能走上游），
        // 放后台执行；迁移成功判据是 loginStatus().loggedIn，不读组件私有文件。
        val legacy = preferences.getString("credential", null)
        scope.launch {
            migrateLegacyCredential(legacy, preferences)
            refreshLogin()
        }
    }

    /** 导入网页登录捕获的凭据（值即取即用，不打印、不落日志）。 */
    fun saveCredential(value: CredentialInfo) {
        importCredentialWithEncryptUin(value.uin.removePrefix("o"), value.musickey, value.euin.takeIf { it.isNotBlank() })
        // 先给本地快照（导入 uin 即账号 id），后台再用 typed loginStatus 校正昵称等。
        _login.value = LoginStatus(value.uin.removePrefix("o").toLongOrNull(), null)
        scope.launch { refreshLogin() }
    }

    fun clearCredential() {
        logout()
        _login.value = null
    }

    /** 用 typed loginStatus() 重建快照；上游拒绝视为未登录，传输失败保留旧快照。 */
    private suspend fun refreshLogin() {
        val status = runCatching { loginStatus() }.getOrNull() ?: return
        _login.value = status.takeIf { it.loggedIn }
            ?.let { LoginStatus(it.musicId, it.nickname?.takeIf(String::isNotBlank)) }
    }

    /**
     * 一次性迁移旧 SharedPreferences「credential」→ 组件导入。
     * 组件已持有可用登录（loginStatus().loggedIn）时只删旧副本；导入/删除失败
     * 只记日志，迁移是幂等的，下次启动重试。
     */
    private suspend fun migrateLegacyCredential(raw: String?, preferences: SharedPreferences) {
        if (raw == null) return
        val legacy = runCatching {
            val value = JSONObject(raw)
            CredentialInfo(value.getString("uin"), value.getString("musickey"),
                if (value.isNull("euin")) "" else value.optString("euin"))
                .takeIf { it.uin.removePrefix("o").toLongOrNull()?.let { id -> id > 0 } == true && it.musickey.isNotBlank() }
        }.getOrNull()
        var loggedIn = runCatching { loginStatus() }.getOrNull()?.loggedIn == true
        if (!loggedIn && legacy != null) {
            runCatching {
                importCredentialWithEncryptUin(legacy.uin.removePrefix("o"), legacy.musickey, legacy.euin.takeIf { it.isNotBlank() })
            }.onFailure { AppLog.w("HelperNext", "credential migration: import failed; will retry next launch", it) }
            loggedIn = runCatching { loginStatus() }.getOrNull()?.loggedIn == true
        }
        if (loggedIn && !preferences.edit().remove("credential").commit()) {
            // 删旧副本失败（如磁盘满）不阻断启动——下次启动会走到同一分支重试。
            AppLog.w("HelperNext", "credential migration: legacy copy removal failed; will retry next launch")
        }
    }

    suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        try { JSONObject(callWithPlatform(method, params.toString(), "android")) }
        catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }
}

/** HelperError → 用户可读消息（raw 桥与 typed 调用共用的薄映射）。 */
internal fun HelperError.userMessage(): String = when (this) {
    HelperError.NotLoggedIn -> "请先登录 QQ 音乐"
    is HelperError.Throttled -> field0
    is HelperError.Upstream -> field0
    is HelperError.InvalidRequest -> field0
    is HelperError.Unsupported -> field0
}

internal fun String.toHttps(): String = when {
    startsWith("http://") -> "https://" + removePrefix("http://")
    startsWith("//") -> "https:" + this
    else -> this
}
internal fun String.cleanHighlight(): String = replace("<em>", "").replace("</em>", "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim()
