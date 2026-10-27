package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.playerMotionArtwork
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerArtworkSourceTest {
    private val track = Track("song", "Title", "Artist")

    @Test fun bothPlayerSizesPreferThePortraitCover() {
        assertEquals("portrait.mp4", track.copy(animatedArtworkUrl = "square.mp4",
            animatedArtworkVerticalUrl = "portrait.mp4").playerMotionArtwork())
    }

    @Test fun offlineGifAndSquareOnlyCoversKeepTheirSource() {
        assertEquals("file:///cover.gif", track.copy(animatedArtworkUrl = "file:///cover.gif").playerMotionArtwork())
        assertEquals("", track.playerMotionArtwork())
    }
}
