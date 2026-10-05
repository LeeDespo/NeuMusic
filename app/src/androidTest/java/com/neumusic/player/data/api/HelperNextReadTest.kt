package com.neumusic.player.data.api

import androidx.test.platform.app.InstrumentationRegistry
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.LikedStore
import com.neumusic.player.data.Quality
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Explicit live reads using an existing account; no login or write calls. */
class HelperNextReadTest {
    @Before fun existingAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Prefs.init(context)
        assertNotNull("Existing QQ account is required", Prefs.credential)
        assertFalse(context.getSharedPreferences("neumusic", 0).contains("credential"))
    }

    @Test fun searchesKeepMetadataAndPaging() = runBlocking {
        val methods = HelperNext.call("get_helper_info").getJSONObject("helper").getJSONArray("methods")
        assertTrue(methods.length() > 60)
        val songs = SearchApi.songs("周杰伦 晴天", 3)
        assertTrue(songs.isNotEmpty())
        assertTrue(songs.any { it.fileSizes.isNotEmpty() })
        assertTrue(songs.any { it.genre > 0 })
        assertTrue(songs.all { it.mediaMid.isNotBlank() })
        assertTrue(SearchApi.songs("周杰伦", 3, 2).isNotEmpty())
        assertTrue(SearchApi.singers("周杰伦", 3).isNotEmpty())
        val albums = SearchApi.albums("周杰伦", 3)
        assertTrue(albums.isNotEmpty()); assertTrue(albums.any { it.songnum > 0 })
        assertTrue(SearchApi.playlists("周杰伦", 3).isNotEmpty())
    }

    @Test fun accountCollectionsAndFullLikedMembership() = runBlocking {
        val liked = PlaylistApi.likedPage(0, 3)
        val next = PlaylistApi.likedPage(3, 3)
        assertNotNull(liked.total); assertTrue(liked.songs.isNotEmpty())
        assertTrue(next.songs.none { row -> liked.songs.any { it.mid == row.mid } })
        val snapshot = componentLikedSnapshot()
        assertEquals(liked.total, snapshot.total)
        println("HelperNext liked consumed=${snapshot.consumed} total=${snapshot.total} identified=${snapshot.ids.size}")
        LikedStore.clear(); LikedStore.refresh()
        assertTrue(LikedStore.loaded.value); assertEquals(snapshot.ids, LikedStore.liked.value)
        PlaylistApi.favPlaylists(1, 3); PlaylistApi.favAlbums(1, 3)
        UserApi.followSingers(0, 3)
        assertNotNull(UserApi.nickname())
    }

    @Test fun artistSortsTotalsAndAlbumPlaylistPages() = runBlocking {
        val artist = SingerApi.resolve("周杰伦")!!
        assertTrue(artist.songNum > 0)
        val hot = SingerApi.songs(artist.mid, SingerApi.ORDER_HOT, 0, 3)
        val latest = SingerApi.songs(artist.mid, SingerApi.ORDER_NEW, 0, 3)
        assertTrue(hot.songs.isNotEmpty()); assertTrue(latest.songs.isNotEmpty()); assertNotNull(hot.total)
        val next = SingerApi.songs(artist.mid, SingerApi.ORDER_NEW, 3, 3)
        assertEquals(latest.total, next.total)
        assertTrue(next.songs.none { row -> latest.songs.any { it.mid == row.mid } })
        val artistAlbums = SingerApi.albums(artist.mid, SingerApi.ORDER_NEW, 0, 3)
        assertTrue(artistAlbums.albums.isNotEmpty()); assertTrue(artistAlbums.albums.any { it.songnum > 0 })
        val album = PlaylistApi.albumPage(SearchApi.albums("周杰伦", 3).first().mid, 0, 3)
        assertTrue(album.songs.isNotEmpty()); assertNotNull(album.total)
        val playlist = PlaylistApi.playlistPage(SearchApi.playlists("周杰伦", 3).first().tid, 0, 3)
        assertTrue(playlist.songs.isNotEmpty()); assertNotNull(playlist.total)
    }

    @Test fun radioRotationAndAllSixPlaybackFormats() = runBlocking {
        val groups = RadioApi.groups()
        assertTrue(groups.isNotEmpty())
        val station = groups.flatMap { it.stations }.firstOrNull { it.title == "猜你喜欢" }
            ?: groups.first().stations.first()
        val first = RadioApi.nextTracks(station.id, true, emptySet(), 1)
        assertTrue(first.isNotEmpty())
        RadioApi.nextTracks(station.id, false, first.map { it.mid }.toSet(), 1)
        val song = SearchApi.songs("周杰伦 晴天", 3).first()
        assertTrue(SongApi.playUrl(song, Quality.STANDARD).startsWith("https://"))
        for (type in listOf("MP3_128", "MP3_320", "ACC_96", "OGG_192", "OGG_320", "FLAC")) {
            val response = HelperNext.call("resolve_song_urls", JSONObject().put("fileType", type)
                .put("fileInfo", JSONArray().put(JSONObject().put("mid", song.mid)
                    .put("mediaMid", song.mediaMid).put("songType", 0))))
            val result = response.getJSONArray("data").getJSONObject(0)
            assertTrue(result.has("result"))
            println("HelperNext read format=$type result=${result.getInt("result")}")
        }
        val raw = HelperNext.call("fetch_song_detail", JSONObject().put("songMid", song.mid))
        assertEquals(song.mid, raw.getJSONObject("detail").getString("songMid"))
    }

    @Test fun japaneseLyricsRetainWordTimingRomanAndKana() = runBlocking {
        val song = SearchApi.songs("米津玄師 Lemon", 3).first()
        val lyric = LyricApi.lyricsFor(song.mid)!!
        assertTrue(lyric.hasWordTiming); assertTrue(lyric.hasRoman); assertTrue(lyric.hasKana)
        assertTrue(lyric.lines.flatMap { it.words }.all { it.endMs >= it.startMs })
    }
}
