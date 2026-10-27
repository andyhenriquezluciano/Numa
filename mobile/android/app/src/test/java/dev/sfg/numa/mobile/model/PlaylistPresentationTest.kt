package dev.sfg.numa.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPresentationTest {
    @Test fun listsAreOrderedWithoutUsingTheirTitles() {
        val ids = listOf("VLSE", "PLcustom", "VLLM", "local-playlist:123")
        assertEquals(listOf("local-playlist:123", "VLLM", "PLcustom", "VLSE"), ids.sortedBy(::playlistDisplayRank))
        assertEquals(1, playlistDisplayRank("FEmusic_liked_videos"))
    }
}
