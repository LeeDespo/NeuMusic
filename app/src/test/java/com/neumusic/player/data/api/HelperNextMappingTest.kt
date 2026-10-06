package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.Album as ComponentAlbum
import com.example.qqmusic_api_helper_next.Artist as ComponentArtist
import com.example.qqmusic_api_helper_next.Playlist as ComponentPlaylist
import com.example.qqmusic_api_helper_next.Singer as ComponentSinger
import com.example.qqmusic_api_helper_next.Track as ComponentTrack
import com.example.qqmusic_api_helper_next.TrackFileSize as ComponentTrackFileSize
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HelperNextMappingTest {
    @Test fun trackKeepsMediaIdentityFormatSizesGenreAndMultipleSingers() {
        val t = QqMapper.track(JSONObject("""{"songMid":"song","mediaMid":"media","title":"<em>Title</em>",
            "singers":[{"name":"A"},{"name":"B"}],"album":"Album","albumMid":"album",
            "songId":123,"genre":34,"duration":211,"payPlay":1,
            "fileSizes":[{"name":"128mp3","bytes":1200},{"name":"320ogg","bytes":5000}]}"""))!!
        assertEquals("media", t.mediaMid); assertEquals("A / B", t.singer)
        assertEquals("Title", t.name); assertEquals(34, t.genre)
        assertEquals(5000L, t.fileSizes["320ogg"]); assertEquals(123L, t.songId)
        assertTrue(t.isVip)
    }
    @Test fun missingTotalStaysUnknownWhileExplicitZeroSurvives() {
        assertNull(PlaylistApi.page(JSONObject("""{"tracks":[],"total":null}""")).total)
        assertEquals(0, PlaylistApi.page(JSONObject("""{"tracks":[],"total":0}""")).total)
    }
    @Test fun artistAlbumPagePrefersServerOffsetOverFilteredCount() {
        // 组件回传 nextOffset 时按它推进；无 albumMid 的行被过滤但已占上游位置。
        val page = SingerApi.albumPage(JSONObject("""{"albums":[{"albumMid":"a"},{"title":"no-mid"}],
            "total":100,"nextOffset":30}"""))
        assertEquals(1, page.albums.size)
        assertEquals(30, page.advanceFrom(0))
    }
    @Test fun artistAlbumPageFallsBackToRawRowsAndIgnoresNegativeOffset() {
        val without = SingerApi.albumPage(JSONObject("""{"albums":[{"albumMid":"a"},{"title":"no-mid"}],"total":100}"""))
        assertEquals(2, without.advanceFrom(0))
        assertNull(SingerApi.albumPage(JSONObject("""{"albums":[],"nextOffset":-1}""")).nextOffset)
    }
    @Test fun artistAlbumAdvanceNeverGoesBackwards() {
        val page = SingerApi.AlbumPage(emptyList(), 100, nextOffset = 5)
        assertThrows(IllegalStateException::class.java) { page.advanceFrom(10) }
    }
    @Test fun lyricsKeepExactMillisecondsDurationAndPlainKanaMetadata() {
        val lyrics = LyricApi.fromComponent(JSONObject("""{"lyric":{"lyric":"[00:01.001]原文",
            "translation":"[kana:1ゆめ]\n[00:01.001]翻译",
            "qrcLines":[{"startMs":1001,"durationMs":399,"words":[{"text":"夢","startMs":1001,"durationMs":399}]}],
            "romanLines":[{"startMs":1001,"durationMs":399,"words":[{"text":"yume","startMs":1001,"durationMs":399}]}]}}"""))!!
        val line = lyrics.lines.single()
        assertEquals(1001L, line.words.single().startMs)
        assertEquals(1400L, line.words.single().endMs)
        assertEquals("翻译", line.translation); assertEquals("yume", line.roman)
        assertEquals("ゆめ", line.words.single().kana)
        assertTrue(lyrics.hasWordTiming); assertTrue(lyrics.hasRoman); assertTrue(lyrics.hasKana)
    }
    @Test fun literalNullStringsDontLeakIntoUi() {
        val t = QqMapper.track(JSONObject("""{"songMid":"m","title":null,"album":null,"albumMid":null,"artist":"artist"}"""))!!
        assertEquals("", t.name); assertEquals("", t.albumName); assertEquals("m", t.mediaMid)
    }

    @Test fun typedSearchTrackMirrorsRawMappingSemantics() {
        val t = QqMapper.track(ComponentTrack(123L, "song", "media", "<em>Title</em>", "A, B",
            "Album", "album", 9L, null, 211L, 1L, null,
            listOf(ComponentSinger("a", "A"), ComponentSinger("b", ""), ComponentSinger("c", null)),
            null, 34L, listOf(ComponentTrackFileSize("128mp3", 1200L), ComponentTrackFileSize("320ogg", 5000L))))!!
        assertEquals("media", t.mediaMid); assertEquals("A", t.singer)
        assertEquals("Title", t.name); assertEquals(34, t.genre)
        assertEquals(5000L, t.fileSizes["320ogg"]); assertEquals(123L, t.songId)
        assertTrue(t.isVip)
    }
    @Test fun typedSearchTrackFallsBackLikeRawWhenFieldsMissing() {
        assertNull(QqMapper.track(ComponentTrack(null, "", null, "T", "A", null, null, null,
            null, null, null, null, null, null, null, emptyList())))
        val t = QqMapper.track(ComponentTrack(null, "song", null, "T", "A, B", null, null, null,
            null, null, null, null, null, null, null, emptyList()))!!
        assertEquals("song", t.mediaMid)   // mediaMid 缺省回退 mid
        assertEquals("A, B", t.singer)     // singers 缺省时用组件 artist 串
        assertEquals("", t.albumName)
        assertFalse(t.isVip); assertEquals(0L, t.songId); assertEquals(0, t.genre)
        assertEquals(0, t.intervalSec); assertTrue(t.fileSizes.isEmpty())
    }
    @Test fun typedSearchArtistAlbumPlaylistKeepHighlightCleanAndHttps() {
        val a = QqMapper.artist(ComponentArtist("mid", "<em>周</em>杰", "http://y.gtimg.cn/x.jpg", 10L, 5L, null))
        assertEquals("周杰", a.name); assertEquals("https://y.gtimg.cn/x.jpg", a.pic)
        assertEquals(10, a.songNum); assertEquals(5, a.albumNum)
        assertNull(QqMapper.album(ComponentAlbum(1L, "专辑", null, "//y.gtimg.cn/p.jpg", "歌手", null, 7L)))
        val album = QqMapper.album(ComponentAlbum(1L, "<em>专辑</em>", "albumMid", "//y.gtimg.cn/p.jpg", "歌手", null, 7L))!!
        assertEquals("albumMid", album.mid); assertEquals("专辑", album.name)
        assertEquals("https://y.gtimg.cn/p.jpg", album.logo)
        assertEquals(7, album.songnum); assertEquals("歌手", album.singerName)
        val p = QqMapper.playlist(ComponentPlaylist(42L, "<em>歌单</em>", "http://qpic.y.qq.com/x", null, 9L, null))
        assertEquals(42L, p.tid); assertEquals("歌单", p.name)
        assertEquals("https://qpic.y.qq.com/x", p.logo); assertEquals(9, p.songnum)
    }
}
