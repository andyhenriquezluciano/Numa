package dev.sfg.numa.mobile.artwork

import org.junit.Assert.*
import org.junit.Test

class PlaylistCoverPolicyTest {
    @Test fun defaultArtworkCannotBeRemoved() {
        assertFalse(hasRemovablePlaylistCover(false, false))
    }
    @Test fun manualAndAutomaticArtworkCanBeRemoved() {
        assertTrue(hasRemovablePlaylistCover(true, false))
        assertTrue(hasRemovablePlaylistCover(false, true))
        assertTrue(hasRemovablePlaylistCover(true, true))
    }
}
