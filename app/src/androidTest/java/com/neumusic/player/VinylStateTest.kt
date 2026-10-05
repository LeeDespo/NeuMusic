package com.neumusic.player

import android.os.Bundle
import org.junit.Test

/** Run the existing state checks through the installed AndroidJUnitRunner. */
class VinylStateTest {
    @Test fun recordProgressAndRapidTrackChangesRemainConsistent() {
        VinylReviewInstrumentation().verifyState(Bundle())
    }
}
