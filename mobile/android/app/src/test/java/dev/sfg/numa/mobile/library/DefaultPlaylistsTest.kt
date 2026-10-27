package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist
import org.junit.Assert.*
import org.junit.Test

class DefaultPlaylistsTest {
    @Test fun restoredListsSurviveRemoteRefresh() {
        val restored = LibrarySnapshot().withDefaultPlaylists()
        val refreshed = restored.mergeVisiblePlaylists(listOf(Playlist("PLother", "Other")))
        assertEquals(setOf("VLLM", "VLSE", "PLother"), refreshed.map { it.id }.toSet())
    }

    @Test fun restoresHiddenBuiltInsWithoutUnhidingOtherLists() {
        val restored = LibrarySnapshot(hiddenPlaylistIds = setOf("FEmusic_liked_videos", "SE", "PLother")).withDefaultPlaylists()
        assertTrue(restored.containsPlaylist("LM"))
        assertTrue(restored.containsPlaylist("SE"))
        assertEquals(setOf("PLother"), restored.hiddenPlaylistIds)
    }

    @Test fun restorationIsIdempotentAndRecognizesAliases() {
        val original = LibrarySnapshot(savedPlaylists = listOf(Playlist("VLLM", "My liked songs")))
        val restored = original.withDefaultPlaylists()
        assertEquals(2, restored.savedPlaylists.size)
        assertEquals(restored, restored.withDefaultPlaylists())
        assertEquals("My liked songs", restored.savedPlaylists.first().title)
    }
}
