package com.neumusic.player.data.api

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.Quality
import com.neumusic.player.player.PlayerHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual embedded API -> Media3 streaming; muted and restores local settings. */
class HelperNextPlaybackTest {
    @Test fun realComponentUrlPlaysThroughMedia3() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val sp = context.getSharedPreferences("neumusic", Context.MODE_PRIVATE)
        val saved = sp.all.toMap()
        fun main(block: () -> Unit) = inst.runOnMainSync(block)
        try {
            main { Prefs.init(context); AppLog.init(context) }
            assertNotNull(awaitLogin())
            val song = SearchApi.songs("周杰伦 晴天", 3).first()
            main {
                PlayerHost.resolveUrl = { SongApi.playUrl(it, Quality.STANDARD) }
                PlayerHost.init(context)
            }
            val creationDeadline = System.nanoTime() + 8_000_000_000L
            var created = false
            while (System.nanoTime() < creationDeadline && !created) {
                main { created = PlayerHost.exoPlayer() != null }
                if (!created) Thread.sleep(40)
            }
            assertTrue("Player must finish asynchronous creation", created)
            main {
                PlayerHost.exoPlayer()!!.volume = 0f
                PlayerHost.playQueue(listOf(song), 0)
            }
            val deadline = System.nanoTime() + 30_000_000_000L
            var played = false
            while (System.nanoTime() < deadline && !played) {
                main {
                    val error = PlayerHost.exoPlayer()?.playerError
                    assertNull("Real playback error code=${error?.errorCode}", error?.errorCode)
                    played = PlayerHost.isPlaying.value && PlayerHost.positionMs() > 500
                }
                if (!played) Thread.sleep(100)
            }
            assertTrue("Real component stream must advance playback", played)
        } finally {
            main { PlayerHost.release() }
            inst.waitForIdleSync()
            main {
                val editor = sp.edit().clear()
                for ((key, value) in saved) when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
                assertTrue(editor.commit())
                Prefs.init(context)
                PlayerHost.resolveUrl = null
            }
        }
    }
}
