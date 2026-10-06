package com.neumusic.player.data.api

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
}
