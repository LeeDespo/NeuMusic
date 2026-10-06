package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.Album as ComponentAlbum
import com.example.qqmusic_api_helper_next.AlbumPage as ComponentAlbumPage
import com.example.qqmusic_api_helper_next.Artist as ComponentArtist
import com.example.qqmusic_api_helper_next.Lyric as ComponentLyric
import com.example.qqmusic_api_helper_next.Playlist as ComponentPlaylist
import com.example.qqmusic_api_helper_next.QrcLine as ComponentQrcLine
import com.example.qqmusic_api_helper_next.QrcWord as ComponentQrcWord
import com.example.qqmusic_api_helper_next.RadioGroup as ComponentRadioGroup
import com.example.qqmusic_api_helper_next.RadioStation as ComponentRadioStation
import com.example.qqmusic_api_helper_next.Singer as ComponentSinger
import com.example.qqmusic_api_helper_next.Track as ComponentTrack
import com.example.qqmusic_api_helper_next.TrackPage as ComponentTrackPage
import com.example.qqmusic_api_helper_next.UserFavAlbumItem
import com.example.qqmusic_api_helper_next.UserFavSonglistItem
import org.junit.Assert.*
import org.junit.Test

class HelperNextMappingTest {
    /** 最小 typed 曲目（16 参全给，便于各用例裁剪）。 */
    private fun componentTrack(mid: String) = ComponentTrack(123L, mid, null, "T", "A", null, null, null,
        null, null, null, null, null, null, null, emptyList())
    private fun componentAlbum(mid: String?) = ComponentAlbum(1L, "专辑", mid, "//y.gtimg.cn/p.jpg", "歌手", null, 7L)

    @Test fun typedTrackMirrorsRawMappingSemantics() {
        val t = QqMapper.track(ComponentTrack(123L, "song", "media", "<em>Title</em>", "A, B",
            "Album", "album", 9L, null, 211L, 1L, null,
            listOf(ComponentSinger("a", "A"), ComponentSinger("b", ""), ComponentSinger("c", null)),
            null, 34L, listOf(com.example.qqmusic_api_helper_next.TrackFileSize("128mp3", 1200L),
                com.example.qqmusic_api_helper_next.TrackFileSize("320ogg", 5000L))))!!
        assertEquals("media", t.mediaMid); assertEquals("A", t.singer)
        assertEquals("Title", t.name); assertEquals(34, t.genre)
        assertEquals(5000L, t.fileSizes["320ogg"]); assertEquals(123L, t.songId)
        assertTrue(t.isVip)
    }
    @Test fun typedTrackFallsBackWhenFieldsMissing() {
        assertNull(QqMapper.track(componentTrack("")))
        val t = QqMapper.track(componentTrack("song"))!!
        assertEquals("song", t.mediaMid)   // mediaMid 缺省回退 mid
        assertEquals("A", t.singer)        // singers 缺省时用组件 artist 串
        assertEquals("", t.albumName)
        assertFalse(t.isVip); assertEquals(123L, t.songId); assertEquals(0, t.genre)
        assertEquals(0, t.intervalSec); assertTrue(t.fileSizes.isEmpty())
    }
    @Test fun missingTotalStaysUnknownWhileExplicitZeroSurvives() {
        assertNull(PlaylistApi.page(ComponentTrackPage(emptyList(), null, null)).total)
        assertEquals(0, PlaylistApi.page(ComponentTrackPage(emptyList(), 0L, null)).total)
    }
    @Test fun typedTrackPagePrefersServerOffsetAndFiltersMidlessRows() {
        // nextOffset 是原始行偏移（无 MID 畸形行占位但被组件省略），优先于本页行数推进。
        val page = PlaylistApi.page(ComponentTrackPage(listOf(componentTrack(""), componentTrack("m1")), 5L, 7L))
        assertEquals(listOf("m1"), page.songs.map { it.mid })
        assertEquals(5, page.total)
        assertEquals(7, page.advanceFrom(0))
    }
    @Test fun artistAlbumPagePrefersServerOffsetOverFilteredCount() {
        // 组件回传 nextOffset 时按它推进；无 mid 行被组件省略但已占上游位置。
        val page = SingerApi.albumPage(ComponentAlbumPage(listOf(componentAlbum("a"), componentAlbum(null)), 100L, 30L))
        assertEquals(1, page.albums.size)
        assertEquals(30, page.advanceFrom(0))
    }
    @Test fun artistAlbumPageFallsBackToRowCountAndIgnoresNegativeOffset() {
        // 防御回退按本页行数（typed 模型不再暴露 raw 行数；现行端点恒回传 nextOffset）。
        val without = SingerApi.albumPage(ComponentAlbumPage(listOf(componentAlbum("a")), 100L, null))
        assertEquals(1, without.advanceFrom(0))
        assertNull(SingerApi.albumPage(ComponentAlbumPage(emptyList(), null, -1L)).nextOffset)
    }
    @Test fun artistAlbumAdvanceNeverGoesBackwards() {
        val page = SingerApi.AlbumPage(emptyList(), 100, nextOffset = 5)
        assertThrows(IllegalStateException::class.java) { page.advanceFrom(10) }
    }
    @Test fun lyricsKeepExactMillisecondsDurationAndPlainKanaMetadata() {
        val lyrics = LyricApi.fromComponent(ComponentLyric("[00:01.001]原文",
            "[kana:1ゆめ]\n[00:01.001]翻译", null, null,
            listOf(ComponentQrcLine(1001L, 399L, listOf(ComponentQrcWord("夢", 1001L, 399L)))),
            listOf(ComponentQrcLine(1001L, 399L, listOf(ComponentQrcWord("yume", 1001L, 399L))))))!!
        val line = lyrics.lines.single()
        assertEquals(1001L, line.words.single().startMs)
        assertEquals(1400L, line.words.single().endMs)
        assertEquals("翻译", line.translation); assertEquals("yume", line.roman)
        assertEquals("ゆめ", line.words.single().kana)
        assertTrue(lyrics.hasWordTiming); assertTrue(lyrics.hasRoman); assertTrue(lyrics.hasKana)
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

    @Test fun favPlaylistKeepsHttpsLogoAndCounts() {
        val p = QqMapper.favPlaylist(UserFavSonglistItem(42L, null, "歌单", "http://qpic.y.qq.com/x",
            null, 9L, null, null, null, null, null, null, null, null, null, null, null, null, null, null))
        assertEquals(42L, p.tid); assertEquals("歌单", p.name)
        assertEquals("https://qpic.y.qq.com/x", p.logo); assertEquals(9, p.songnum)
    }
    @Test fun favAlbumBuildsCoverFromPmidAndFallsBackToMid() {
        // typed 收藏专辑只给 pmid：封面沿用宿主 pmid → y.gtimg.cn 拼接；pmid 空回退 mid。
        val album = QqMapper.favAlbum(UserFavAlbumItem(1L, "albumMid", "", "标题", null, null,
            "pmid1", 9L, null, null, null, null, null))!!
        assertEquals("albumMid", album.mid)
        assertEquals("标题", album.name)   // name 空回退 title
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000pmid1.jpg", album.logo)
        assertEquals(9, album.songnum)
        val fallback = QqMapper.favAlbum(UserFavAlbumItem(1L, "mid2", "名", null, null, null,
            null, null, null, null, null, null, null))!!
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000mid2.jpg", fallback.logo)
        assertNull(QqMapper.favAlbum(UserFavAlbumItem(1L, "", "名", null, null, null,
            null, null, null, null, null, null, null)))   // 无 mid 行不可用
    }
    @Test fun radioGroupNarrowsStationIdToHostIntAndHttps() {
        val g = QqMapper.radioGroup(ComponentRadioGroup("热门",
            listOf(ComponentRadioStation(106L, "电台", "http://y.gtimg.cn/x.jpg"))))
        assertEquals("热门", g.title)
        assertEquals(106, g.stations.single().id)
        assertEquals("https://y.gtimg.cn/x.jpg", g.stations.single().picUrl)
    }
}
