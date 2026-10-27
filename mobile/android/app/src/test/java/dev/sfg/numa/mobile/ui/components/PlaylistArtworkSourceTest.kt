package dev.sfg.numa.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistArtworkSourceTest {
    @Test fun changingSongsNeverUsesThePreviousSongsCachedFile() {
        assertEquals("https://art/new.jpg", playlistArtworkSource(
            "https://art/new.jpg", "https://art/old.jpg" to "file:/old.png"))
    }

    @Test fun matchingCachedArtworkUsesTheLocalFile() {
        assertEquals("file:/new.png", playlistArtworkSource(
            "https://art/new.jpg", "https://art/new.jpg" to "file:/new.png"))
    }

    @Test fun rapidChangesDoNotAssociateAnIntermediateImageWithTheCurrentSong() {
        assertEquals("https://art/third.jpg", playlistArtworkSource(
            "https://art/third.jpg", "https://art/second.jpg" to "file:/second.png"))
    }
}
