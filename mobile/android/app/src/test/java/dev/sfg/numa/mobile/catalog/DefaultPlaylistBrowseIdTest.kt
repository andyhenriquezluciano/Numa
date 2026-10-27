package dev.sfg.numa.mobile.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPlaylistBrowseIdTest {
    @Test fun restoredAndLegacyLikedListsUseTheAuthenticatedPlaylistEndpoint() {
        listOf("LM", "VLLM", "FEmusic_liked_videos", "VLFEmusic_liked_videos").forEach {
            assertEquals("VLLM", defaultPlaylistBrowseId(it))
        }
    }

    @Test fun restoredEpisodesUseTheirBrowseId() {
        assertEquals("VLSE", defaultPlaylistBrowseId("SE"))
        assertEquals("VLSE", defaultPlaylistBrowseId("VLSE"))
    }

    @Test fun ordinaryCollectionsAreUnchanged() {
        assertEquals("VLPL123", defaultPlaylistBrowseId("VLPL123"))
        assertEquals("MPRE123", defaultPlaylistBrowseId("MPRE123"))
    }
}
