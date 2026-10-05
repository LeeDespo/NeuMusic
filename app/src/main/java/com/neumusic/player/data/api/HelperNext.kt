package com.neumusic.player.data.api

import android.content.Context
import android.content.SharedPreferences
import com.neumusic.player.data.CredentialInfo
import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.callWithPlatform
import com.example.qqmusic_api_helper_next.initialize
import com.example.qqmusic_api_helper_next.importCredentialWithEncryptUin
import com.example.qqmusic_api_helper_next.logout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Embedded Rust owns QQ requests, devices, credentials and request guards. */
object HelperNext {
    private lateinit var credentialFile: File
    @Volatile private var savedCredential: CredentialInfo? = null

    @Synchronized fun init(context: Context, preferences: SharedPreferences) {
        val directory = File(context.filesDir, "HelperNext")
        initialize(directory.absolutePath, "android")
        credentialFile = File(directory, "Credential/qqmusic-credential.json")
        // Import once, and remove the old copy only after Rust confirms the atomic write.
        preferences.getString("credential", null)?.let { raw ->
            val legacy = runCatching {
                val value = JSONObject(raw)
                CredentialInfo(value.getString("uin"), value.getString("musickey"), value.text("euin"))
                    .takeIf { it.uin.removePrefix("o").toLongOrNull()?.let { id -> id > 0 } == true && it.musickey.isNotBlank() }
            }.getOrNull()
            if (readCredential() == null && legacy != null) saveCredential(legacy)
            if (readCredential() != null) {
                check(preferences.edit().remove("credential").commit()) { "凭据迁移保存失败" }
            }
        }
        refreshCredential()
    }

    fun credential(): CredentialInfo? = savedCredential

    private fun refreshCredential() { savedCredential = readCredential() }

    private fun readCredential(): CredentialInfo? {
        if (!::credentialFile.isInitialized || !credentialFile.exists()) return null
        val value = runCatching { JSONObject(credentialFile.readText()) }.getOrNull() ?: return null
        val uin = value.text("str_musicid").ifEmpty { value.text("musicid") }
        val key = value.text("musickey")
        return if (uin.toLongOrNull()?.let { it > 0 } != true || key.isBlank() || key == "null") null else CredentialInfo(uin, key, value.text("encrypt_uin"))
    }

    fun saveCredential(value: CredentialInfo) {
        importCredentialWithEncryptUin(value.uin.removePrefix("o"), value.musickey, value.euin.takeIf { it.isNotBlank() })
        refreshCredential()
    }
    fun clearCredential() { logout(); savedCredential = null }

    suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        try { JSONObject(callWithPlatform(method, params.toString(), "android")) }
        catch (error: HelperError) { throw IllegalStateException(message(error), error) }
    }

    private fun message(error: HelperError): String = when (error) {
        HelperError.NotLoggedIn -> "请先登录 QQ 音乐"
        is HelperError.Throttled -> error.field0
        is HelperError.Upstream -> error.field0
        is HelperError.InvalidRequest -> error.field0
        is HelperError.Unsupported -> error.field0
    }
}

internal fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
internal fun JSONObject.total(): Int? = if (isNull("total") || !has("total")) null else optInt("total").takeIf { it >= 0 }
internal inline fun <T> JSONArray?.items(transform: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }
internal fun String.toHttps(): String = when {
    startsWith("http://") -> "https://" + removePrefix("http://")
    startsWith("//") -> "https:" + this
    else -> this
}
internal fun String.cleanHighlight(): String = replace("<em>", "").replace("</em>", "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim()
