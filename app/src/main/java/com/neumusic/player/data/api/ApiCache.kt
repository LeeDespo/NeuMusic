package com.neumusic.player.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程内 TTL 缓存（改造3 审阅时补上）。
 *
 * 之前只有歌词与昵称有缓存，主页三栏每次回主页都重新拉三组接口。
 * 这里给"读多改少"的列表数据加一层轻量内存缓存：
 * - 命中且未过 TTL → 直接返回，不发请求；
 * - 过期/未命中 → 请求后写入；
 * - 上限淘汰：超出容量先清最旧的（粗暴但足够，条目都是小列表）。
 *
 * 注意：只缓存**幂等读**；写操作与凭据相关请求一律不进缓存。
 * 条目按非敏感登录快照（[HelperNext.login]）键控：账号切换后旧条目不命中，
 * 在途结果在登录态变化时整体作废。
 */
object ApiCache {
    private class Entry(val value: Any?, val atMs: Long, val login: LoginStatus?)

    private const val DEFAULT_TTL_MS = 5 * 60_000L
    private const val MAX_ENTRIES = 32

    private val mutex = Mutex()
    private var generation = 0L
    private val map = LinkedHashMap<String, Entry>(16, 0.75f, true)

    suspend fun <T> getOrPut(key: String, ttlMs: Long = DEFAULT_TTL_MS, loader: suspend () -> T): T {
        val login = HelperNext.login.value
        val requestGeneration = mutex.withLock {
            val e = map[key]
            if (e != null && e.login == login && System.currentTimeMillis() - e.atMs < ttlMs) {
                @Suppress("UNCHECKED_CAST")
                return e.value as T
            }
            generation
        }
        // 加载放在锁外，避免慢请求把其它缓存读写全堵住。
        val v = loader()
        mutex.withLock {
            if (generation != requestGeneration || HelperNext.login.value != login)
                throw CancellationException("账号或缓存已失效")
            if (map.size >= MAX_ENTRIES) {
                val eldest = map.keys.firstOrNull()
                if (eldest != null) map.remove(eldest)
            }
            map[key] = Entry(v, System.currentTimeMillis(), login)
        }
        return v
    }

    /** 主动失效（退出登录等场景）。 */
    suspend fun invalidate(prefix: String) {
        mutex.withLock {
            generation++
            map.keys.removeAll { it.startsWith(prefix) }
        }
    }

    suspend fun clear() {
        mutex.withLock { generation++; map.clear() }
    }
}
