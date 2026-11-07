package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.playerMotionArtwork
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerArtworkSourceTest {
    private val track = Track("song", "Title", "Artist")

    @Test fun fullBleedPrefersThePortraitCover() {
        assertEquals("portrait.mp4", track.copy(animatedArtworkUrl = "square.mp4",
            animatedArtworkVerticalUrl = "portrait.mp4").playerMotionArtwork())
    }

    @Test fun squarePlayerPrefersSquareAndFallsBackToPortrait() {
        assertEquals("square.mp4", track.copy(animatedArtworkUrl = "square.mp4",
            animatedArtworkVerticalUrl = "portrait.mp4").playerMotionArtwork(square = true))
        assertEquals("portrait.mp4", track.copy(animatedArtworkVerticalUrl = "portrait.mp4")
            .playerMotionArtwork(square = true))
        assertEquals("file:///cover.gif", track.copy(animatedArtworkUrl = "file:///cover.gif")
            .playerMotionArtwork(square = true))
    }

    @Test fun offlineGifAndSquareOnlyCoversKeepTheirSource() {
        assertEquals("file:///cover.gif", track.copy(animatedArtworkUrl = "file:///cover.gif").playerMotionArtwork())
        assertEquals("", track.playerMotionArtwork())
    }
}
