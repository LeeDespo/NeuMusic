package com.neumusic.player.data.api

import com.example.qqmusic_api_helper_next.Album as ComponentAlbum
import com.example.qqmusic_api_helper_next.AlbumPage as ComponentAlbumPage
import com.example.qqmusic_api_helper_next.Artist as ComponentArtist
import com.example.qqmusic_api_helper_next.GetFavNumResponse
import com.example.qqmusic_api_helper_next.LikeReceipt
import com.example.qqmusic_api_helper_next.Lyric as ComponentLyric
import com.example.qqmusic_api_helper_next.Playlist as ComponentPlaylist
import com.example.qqmusic_api_helper_next.QrcLine as ComponentQrcLine
import com.example.qqmusic_api_helper_next.QrcWord as ComponentQrcWord
import com.example.qqmusic_api_helper_next.RadioGroup as ComponentRadioGroup
import com.example.qqmusic_api_helper_next.RadioStation as ComponentRadioStation
import com.example.qqmusic_api_helper_next.Singer as ComponentSinger
import com.example.qqmusic_api_helper_next.SongDetail
import com.example.qqmusic_api_helper_next.Track as ComponentTrack
import com.example.qqmusic_api_helper_next.TrackPage as ComponentTrackPage
import com.example.qqmusic_api_helper_next.UrlinfoItem
import com.example.qqmusic_api_helper_next.UserFavAlbumItem
import com.example.qqmusic_api_helper_next.UserFavSonglistItem
import com.neumusic.player.data.Quality
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

    @Test fun songQualityMapsCoverAllSixTiersWithoutClash() {
        val fileTypes = Quality.entries.associateWith(SongApi::format)
        val sizeKeys = Quality.entries.associateWith(SongApi::sizeKey)
        // 六档各配一个 file_type（组件枚举成员名）与 fileSizes 键，互不重复。
        assertEquals(setOf("MP3_128", "MP3_320", "ACC_96", "OGG_192", "OGG_320", "FLAC"), fileTypes.values.toSet())
        assertEquals(setOf("128mp3", "320mp3", "96aac", "192ogg", "320ogg", "flac"), sizeKeys.values.toSet())
        assertEquals(6, fileTypes.size); assertEquals(6, sizeKeys.size)
    }

    @Test fun playUrlChainPrefersRequestedTierThenSkipsMissingSizes() {
        // 无 fileSizes（落盘推荐曲目冷启动）走全档位分支，请求档仍在最前。
        assertEquals(listOf(Quality.FLAC, Quality.STANDARD, Quality.HQ, Quality.AAC96,
            Quality.OGG192, Quality.OGG320), SongApi.chain(Quality.FLAC, emptyMap()))
        // 有 fileSizes 时只保留有大小的档位，请求档最前、其余按枚举序。
        val sizes = mapOf("128mp3" to 1L, "320ogg" to 2L)
        assertEquals(listOf(Quality.OGG320, Quality.STANDARD), SongApi.chain(Quality.OGG320, sizes))
        // 全被滤空回退标准档。
        assertEquals(listOf(Quality.STANDARD), SongApi.chain(Quality.FLAC, mapOf("128mp3" to 0L)))
    }

    @Test fun typedPurlJoinsIsureDomainAndNormalizesAbsoluteToHttps() {
        // 相对 purl 拼isure 域名；带 scheme / // 的按 https 归一。
        assertEquals("https://isure.stream.qqmusic.qq.com/M500xxx.mp3",
            SongApi.absoluteUrl(UrlinfoItem(null, null, "M500xxx.mp3", null, null, 0L)))
        assertEquals("https://dl.stream.qqmusic.qq.com/x.m4a",
            SongApi.absoluteUrl(UrlinfoItem(null, null, "http://dl.stream.qqmusic.qq.com/x.m4a", null, null, 0L)))
        assertEquals("https://dl.stream.qqmusic.qq.com/x.m4a",
            SongApi.absoluteUrl(UrlinfoItem(null, null, "//dl.stream.qqmusic.qq.com/x.m4a", null, null, 0L)))
        // result 非 0（104003 无权限等）、缺 purl、result 缺省一律不可用。
        assertNull(SongApi.absoluteUrl(UrlinfoItem(null, null, "M500xxx.mp3", null, null, 104003L)))
        assertNull(SongApi.absoluteUrl(UrlinfoItem(null, null, null, null, null, 0L)))
        assertNull(SongApi.absoluteUrl(UrlinfoItem(null, null, "M500xxx.mp3", null, null, null)))
    }

    @Test fun likeReceiptMapsSuccessThrottleAndRawBusinessCode() {
        assertEquals(LikeResult.Success, SongApi.receipt(LikeReceipt(true, 0L, false)))
        // throttled 是组件对上游写限流码 1000 的标记，映射回既有 UI 限流文案路径。
        assertEquals(LikeResult.Rejected(1000), SongApi.receipt(LikeReceipt(false, 1000L, true)))
        assertEquals(LikeResult.Rejected(80105), SongApi.receipt(LikeReceipt(false, 80105L, false)))
    }

    @Test fun songDetailIntroOnlyForNonBlankDescription() {
        val detail = SongDetail("mid", 1L, null, null, null, null, "简介", emptyList(), null, null, null, null)
        assertEquals("简介", SongApi.introOf(detail))
        assertNull(SongApi.introOf(SongDetail("mid", null, null, null, null, null, "", emptyList(), null, null, null, null)))
    }

    @Test fun favNumsKeepNumericSongIdsAndDropMalformedKeys() {
        val (numbers, show) = SongApi.favNums(
            GetFavNumResponse(mapOf("123" to 45L, "x" to 6L), mapOf("123" to "45", "x" to "六")))
        assertEquals(mapOf(123L to 45L), numbers)
        assertEquals(mapOf(123L to "45"), show)
        assertEquals(Pair(emptyMap<Long, Long>(), emptyMap<Long, String>()), SongApi.favNums(GetFavNumResponse(null, null)))
    }
}
