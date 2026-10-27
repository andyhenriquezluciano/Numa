package dev.sfg.numa.mobile.ui.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassArtworkOwnerTest {
    @Test fun aPaneCanSampleArtworkFromItsOwnScreen() {
        val owner = Any()
        assertTrue(glassArtworkMatchesOwner(owner, owner))
    }

    @Test fun libraryCannotSampleLikedSongsDuringBackNavigation() {
        assertFalse(glassArtworkMatchesOwner(Any(), Any()))
    }

    @Test fun activityChromeCannotReuseADiscardedScreenSample() {
        assertFalse(glassArtworkMatchesOwner(Any(), null))
    }
}
