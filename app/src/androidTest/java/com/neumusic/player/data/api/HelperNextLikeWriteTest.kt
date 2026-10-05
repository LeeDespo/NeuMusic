package com.neumusic.player.data.api

import androidx.test.platform.app.InstrumentationRegistry
import com.neumusic.player.data.Prefs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in reversible write test. Never removes a song liked before this test. */
class HelperNextLikeWriteTest {
    @Test fun numericIdLikeReceiptAndAccountRestoration() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("executeWrites") == "true")
        Prefs.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertNotNull(Prefs.credential)
        val before = componentLikedSnapshot()
        val candidate = SearchApi.songs("天外来物", 10).firstOrNull { it.songId > 0 && it.songId !in before.ids }
            ?: error("No absent candidate; do not alter an existing like")
        println("HelperNext write candidate songId=${candidate.songId}, mid=${candidate.mid}")
        try {
            assertEquals(LikeResult.Success, SongApi.setLiked(candidate, true))
            assertTrue(candidate.songId in componentLikedSnapshot().ids)
        } finally {
            withContext(NonCancellable) {
                // The candidate was absent before the test. Attempt removal even if a read
                // or the add receipt fails; a network error may hide a successful add.
                val cleanup = runCatching { SongApi.setLiked(candidate, false) }
                val final = runCatching { componentLikedSnapshot() }
                val restored = final.getOrNull() == before
                println("HelperNext set_liked_by_id add/remove; cleanupAcknowledged=${cleanup.getOrNull() == LikeResult.Success}; restored=$restored")
                assertTrue("Account restoration must be verified; cleanup=${cleanup.exceptionOrNull()?.javaClass?.simpleName}; read=${final.exceptionOrNull()?.javaClass?.simpleName}", restored)
            }
        }
    }

}
