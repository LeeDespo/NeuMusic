package com.neumusic.player.data.api

import com.neumusic.player.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐接口层的共享底座：三个 comm（通信档位）、统一信封请求、以及响应解析工具。
 *
 * 所有端点模块（同目录下的 `SongApi`/`PlaylistApi` 等）都只依赖这里，
 * 不各自持有 HTTP 客户端，也不各自拼鉴权字段 —— 这是为了「换 comm / 加公共字段」
 * 这类改动只发生在一个地方。
 *
 * 三套 comm（全部实测验证，用错就拿不到数据）：
 * - [commWeb]  匿名网页档：搜索。
 * - [commRadio] 电台档：电台列表。
 * - [commAuth] 登录档：其余全部（含写操作，必须用 Android 档而非网页档）。
 */
object QqCore {
    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    internal val jsonMedia = "application/json; charset=utf-8".toMediaType()

    internal const val FCG = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    /** 匿名网页档：只有这个组合能用综合模式搜到歌。 */
    internal val commWeb: JSONObject = JSONObject().put("ct", 19).put("cv", 1873)

    /** 电台档：`pf.radiosvr` 要求。 */
    internal val commRadio: JSONObject = JSONObject().put("ct", 24).put("cv", 0)

    /**
     * 登录档（Android 档位）。`qq`=uin、`authst`=musickey、`tmeLoginType:2`。
     * QIMEI 等设备字段服务端接受自报随机值，无需真实置备 —— 这是能纯原生实现的关键。
     */
    internal fun commAuth(): JSONObject {
        val c = JSONObject()
            .put("ct", 11).put("cv", 14090008).put("v", 14090008)
            .put("tmeAppID", "qqmusic").put("chid", "10003505")
            .put("tmeLoginType", 2)
            .put("QIMEI", Prefs.qimei)
            .put("QIMEI36", Prefs.qimei)
            .put("OpenUDID", Prefs.guid)
            .put("udid", Prefs.guid)
            .put("os_ver", "12").put("phonetype", "Android")
        Prefs.credential?.let {
            c.put("qq", it.uin).put("authst", it.musickey)
        }
        return c
    }

    /** 一个业务请求块：`{"module":..,"method":..,"param":{..}}`。 */
    internal data class Req(val module: String, val method: String, val param: JSONObject)

    /**
     * 统一信封 POST。可以把多个 [Req] 放进同一次往返（`req_1`、`req_2`…），
     * 服务端支持单次请求多接口，这是降低请求频次的主要手段。
     */
    internal suspend fun call(
        comm: JSONObject,
        vararg reqs: Pair<String, Req>,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("comm", comm)
        reqs.forEach { (name, r) ->
            body.put(name, JSONObject()
                .put("module", r.module)
                .put("method", r.method)
                .put("param", r.param))
        }
        val request = Request.Builder()
            .url(FCG)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { resp ->
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    /** 取某个请求块的 `data`。默认 `req_1`（单请求场景）。 */
    internal fun data(root: JSONObject, key: String = "req_1"): JSONObject? =
        root.optJSONObject(key)?.optJSONObject("data")

    /** 取某个请求块的业务码；成功判定通常还需再看 `data.retCode`。 */
    internal fun code(root: JSONObject, key: String = "req_1"): Int =
        root.optJSONObject(key)?.optInt("code", -1) ?: -1

    /** JSONArray → List，跳过非对象项。 */
    internal inline fun <T> mapItems(arr: JSONArray?, transform: (JSONObject) -> T?): List<T> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(transform) }
    }
}

/**
 * 封面 URL 规范化：接口给的多是 `http://`，而 Android 默认禁明文流量，
 * 直接用会导致封面**静默不显示**。两个 CDN 都支持 https，统一升级。
 */
internal fun String.toHttps(): String = when {
    startsWith("http://") -> "https://" + removePrefix("http://")
    else -> this
}

/**
 * 剥掉搜索结果里的服务端高亮标记与 HTML 实体。
 * 实测即便 `highlight:false`，专辑/歌单结果的名字仍带 `<em>`。
 */
internal fun String.cleanHighlight(): String = this
    .replace("<em>", "")
    .replace("</em>", "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .trim()
