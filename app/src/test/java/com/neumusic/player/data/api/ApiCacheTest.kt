package com.neumusic.player.data.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ApiCacheTest {
    @Test fun clearRejectsPendingLoaderAndPreventsOldRefill() = runBlocking {
        ApiCache.clear()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val pending = async {
            runCatching { ApiCache.getOrPut("account-list") { started.complete(Unit); finish.await(); "old" } }
        }
        withTimeout(5000) { started.await() }
        ApiCache.clear()
        finish.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals("new", ApiCache.getOrPut("account-list") { "new" })
        ApiCache.clear()
    }
    @Test fun nullableReadResultCanBeCached() = runBlocking {
        ApiCache.clear()
        var calls = 0
        assertNull(ApiCache.getOrPut<Int?>("missing-total") { calls++; null })
        assertNull(ApiCache.getOrPut<Int?>("missing-total") { calls++; 1 })
        assertEquals(1, calls)
        ApiCache.clear()
    }
}
