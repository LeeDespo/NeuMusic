package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.GetFavNumResponse
import com.example.qqmusic_api_helper_next.HelperError
import com.example.qqmusic_api_helper_next.LikeReceipt
import com.example.qqmusic_api_helper_next.SongDetail
import com.example.qqmusic_api_helper_next.SongFileInfo
import com.example.qqmusic_api_helper_next.UrlinfoItem
import com.example.qqmusic_api_helper_next.fetchSongFavCount
import com.example.qqmusic_api_helper_next.resolveSongUrls
import com.example.qqmusic_api_helper_next.setLikedById
import com.example.qqmusic_api_helper_next.songDetail
import com.neumusic.player.data.Quality
import com.neumusic.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LikeResult {
    data object Success : LikeResult

    /** 被服务端拒绝（多半是短时风控限流）。 */
    data class Rejected(val code: Int) : LikeResult

    /** 本地条件不满足，压根没发请求。 */
    data class Unavailable(val reason: String) : LikeResult

    val ok: Boolean get() = this is Success
}

/**
 * 歌曲域 typed 消费：取流/详情/喜欢写/收藏数。上游请求与解析归组件；宿主保留的
 * 产品策略是逐档降级链（[playUrl]）与 Quality → 组件 file_type 的唯一映射。
 *
 * 取流走批量 typed `resolveSongUrls`，不用便捷版 `resolveSongUrl`/`StreamResolution`：
 * 后者不回档位级数字 result 码，撑不起降级链「104003 是档位级失败、不中断」的实测规则，
 * 也分不清取流限流与凭据过期。URL 形状（purl 相对路径）与 result 数字语义由 typed
 * UrlinfoItem 承载，宿主只在 [absoluteUrl] 与下方常量处消费一次。
 */
object SongApi {
    class PlayUrlException(message: String, val result: Int) : IllegalStateException(message)

    // ── 上游取流 result 码。typed UrlinfoItem.result 只以数字承载（组件注释给 0 成功、
    //    104003 无权限、104004 取票失败、104013 播放设备受限；101404 取流限流与
    //    22 凭据过期是实测码），组件未提供枚举，宿主分类集中在这一处。──
    private const val RESULT_OK = 0L
    private const val RESULT_VIP_ONLY = 104003L
    private const val RESULT_THROTTLED = 101404L
    private const val RESULT_CREDENTIAL_EXPIRED = 22L

    /** Quality → 组件 file_type（绑定接受参考枚举成员名，缺省即 MP3_128）。宿主唯一一处映射。 */
    internal fun format(quality: Quality): String = when (quality) {
        Quality.STANDARD -> "MP3_128"
        Quality.HQ -> "MP3_320"
        Quality.AAC96 -> "ACC_96"
        Quality.OGG192 -> "OGG_192"
        Quality.OGG320 -> "OGG_320"
        Quality.FLAC -> "FLAC"
    }

    /** Quality → [Track.fileSizes] 的键（组件 TrackFileSize.name）。宿主唯一一处映射。 */
    internal fun sizeKey(quality: Quality): String = when (quality) {
        Quality.STANDARD -> "128mp3"
        Quality.HQ -> "320mp3"
        Quality.AAC96 -> "96aac"
        Quality.OGG192 -> "192ogg"
        Quality.OGG320 -> "320ogg"
        Quality.FLAC -> "flac"
    }

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: HelperError) { throw IllegalStateException(error.userMessage(), error) }
    }

    /**
     * 降级链（App 播放策略）：请求档位最前，其余按 Quality 枚举序补全；fileSizes
     * 非空时只保留有文件大小的档位，全被滤空回退标准档。落盘推荐曲目没有
     * fileSizes，冷启动走全档位分支（现状行为）。
     */
    internal fun chain(quality: Quality, fileSizes: Map<String, Long>): List<Quality> {
        val wanted = listOf(quality) + Quality.entries.filter { it != quality }
        return if (fileSizes.isEmpty()) wanted else wanted.filter {
            (fileSizes[sizeKey(it)] ?: 0L) > 0L
        }.ifEmpty { listOf(Quality.STANDARD) }
    }

    /**
     * typed UrlinfoItem → 绝对直链；不可用（result 非 0 或缺 purl）回 null。
     * purl 是相对路径（要与 CDN 域名拼接才能访问），isure 域名拼接是宿主职责；
     * 上游偶尔也回带 scheme 或 `//` 开头的完整地址，按 https 归一。
     */
    internal fun absoluteUrl(item: UrlinfoItem): String? {
        val purl = item.purl.orEmpty()
        if (item.result != RESULT_OK || purl.isBlank()) return null
        return when {
            purl.startsWith("http") || purl.startsWith("//") -> purl.toHttps()
            else -> "https://isure.stream.qqmusic.qq.com/${purl.trimStart('/')}"
        }
    }

    suspend fun playUrl(track: Track, quality: Quality = Quality.STANDARD): String {
        var sawVipOnly = false
        var sawThrottle = false
        var lastResult = -1L
        for (tier in chain(quality, track.fileSizes)) {
            val response = call {
                resolveSongUrls(listOf(SongFileInfo(mid = track.mid, fileType = null,
                    songType = 0L, mediaMid = track.mediaMid)), fileType = format(tier))
            }
            val info = response.data?.firstOrNull()
                ?: throw PlayUrlException("播放地址响应不完整", -1)
            lastResult = info.result ?: -1L
            absoluteUrl(info)?.let { return it }
            if (lastResult == RESULT_VIP_ONLY) sawVipOnly = true
            if (lastResult == RESULT_THROTTLED) sawThrottle = true
        }
        throw PlayUrlException(when {
            sawVipOnly && lastResult == RESULT_VIP_ONLY ->
                "该曲目/音质需要会员播放权限（错误 $RESULT_VIP_ONLY），请确认会员状态或重新登录"
            sawThrottle -> "取链接被限流，请稍后重试"
            lastResult == RESULT_CREDENTIAL_EXPIRED -> "播放凭据已过期，请重新登录"
            else -> "拿不到播放链接（错误 $lastResult）"
        }, lastResult.toInt())
    }

    /** typed SongDetail → 简介；绝大多数歌曲 description 为空串（是答案不是失败）。 */
    internal fun introOf(detail: SongDetail): String? = detail.description.takeIf(String::isNotBlank)

    suspend fun intro(mid: String): String? = runCatching { introOf(call { songDetail(mid) }) }.getOrNull()

    /**
     * typed LikeReceipt → 宿主写结果。throttled 是组件对上游写限流码 1000 的标记，
     * 映射回既有 Rejected(1000) 文案路径（likeFailMessage 的限流提示）。
     */
    internal fun receipt(receipt: LikeReceipt): LikeResult = when {
        receipt.success -> LikeResult.Success
        receipt.throttled -> LikeResult.Rejected(1000)
        else -> LikeResult.Rejected(receipt.code.toInt())
    }

    suspend fun setLiked(track: Track, liked: Boolean): LikeResult {
        if (HelperNext.login.value == null) return LikeResult.Unavailable("未登录")
        if (track.songId <= 0L) return LikeResult.Unavailable("这首没有可用的歌曲 id")
        return receipt(call { setLikedById(track.songId, liked) })
    }

    /**
     * typed 收藏数回值以歌曲 id 字符串为键；非数字键（畸形行）直接丢弃。
     * 原始值与展示文案并列返回，展示文案直接可用（如「3.2万」）。
     */
    internal fun favNums(response: GetFavNumResponse): Pair<Map<Long, Long>, Map<Long, String>> = Pair(
        response.numbers.orEmpty().mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }.toMap(),
        response.show.orEmpty().mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }.toMap(),
    )

    /** 歌曲收藏人数；键为数字歌曲 id。 */
    suspend fun favCounts(songIds: LongArray): Pair<Map<Long, Long>, Map<Long, String>> =
        call { favNums(fetchSongFavCount(songIds)) }
}
