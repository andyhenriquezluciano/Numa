package dev.sfg.numa.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistBackdropSourceTest {
    @Test fun removedCoverMarkerRestoresHeartRatherThanSong() {
        assertEquals("heart", playlistBackdropSource("LM", "", "song", "heart"))
        assertEquals("heart", playlistBackdropSource("VLFEmusic_liked_videos", "", "song", "heart"))
    }

    @Test fun missingOverrideAlsoUsesHeartAndManualOverrideIsPreserved() {
        assertEquals("heart", playlistBackdropSource("LM", null, "song", "heart"))
        assertEquals("custom", playlistBackdropSource("LM", "custom", "song", "heart"))
    }
}
