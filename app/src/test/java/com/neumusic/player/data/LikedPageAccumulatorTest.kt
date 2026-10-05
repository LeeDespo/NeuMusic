package com.neumusic.player.data

import com.neumusic.player.data.api.PlaylistApi
import org.junit.Assert.*
import org.junit.Test

class LikedPageAccumulatorTest {
    private fun track(id: Long) = Track("m$id", "", "", "", "", "", 0, false, id)
    @Test fun filteredRowsStillAdvanceToServerBoundary() {
        val pages = LikedPageAccumulator()
        assertFalse(pages.consume(PlaylistApi.Page(listOf(track(1), track(2)), 5, 3)))
        assertEquals(3, pages.offset)
        assertTrue(pages.consume(PlaylistApi.Page(listOf(track(3)), 5, 5)))
        assertEquals(setOf(1L, 2L, 3L), pages.ids)
    }
    @Test fun allUnavailablePageCanBeFollowedByValidRows() {
        val pages = LikedPageAccumulator()
        assertFalse(pages.consume(PlaylistApi.Page(emptyList(), 4, 2)))
        assertTrue(pages.consume(PlaylistApi.Page(listOf(track(7), track(7)), 4, 4)))
        assertEquals(setOf(7L), pages.ids)
    }
    @Test(expected = IllegalStateException::class) fun emptyBeforeKnownBoundaryIsIncomplete() {
        LikedPageAccumulator().consume(PlaylistApi.Page(emptyList(), 4, 0))
    }
    @Test fun unknownTotalCompletesOnlyAfterSourceEnds() {
        val pages = LikedPageAccumulator()
        assertFalse(pages.consume(PlaylistApi.Page(listOf(track(1)), null, 2)))
        assertTrue(pages.consume(PlaylistApi.Page(emptyList(), null, 2)))
    }
}
