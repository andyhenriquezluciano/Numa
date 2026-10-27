package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist
import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.*
import org.junit.Test

class PlaylistVisibilityTest {
    private fun playlist(id: String) = Playlist(id, id, "User")

    @Test fun `youtube refresh retains a playlist received from another Numa device`() {
        val shared = playlist("PL-from-PC").copy(tracks = listOf(Track("song", "Song", "Artist")))
        val state = LibrarySnapshot(savedPlaylists = listOf(playlist("LM"), shared))
        val refreshed = state.mergeVisiblePlaylists(listOf(playlist("VLLM")))
        assertEquals(2, refreshed.size)
        assertEquals(shared, refreshed.single { it.id == shared.id })
        assertEquals(listOf("VLLM"), state.withoutPlaylist(shared.id).mergeVisiblePlaylists(listOf(playlist("VLLM"))).map { it.id })
    }

    @Test fun `removed special lists stay hidden after remote refresh`() {
        val remote = listOf(playlist("LM"), playlist("SE"), playlist("PL-other"))
        val state = LibrarySnapshot(savedPlaylists = remote).withoutPlaylist("VLLM").withoutPlaylist("VLSE")
        assertEquals(listOf("PL-other"), state.mergeVisiblePlaylists(remote).map { it.id })
        assertEquals(setOf("FEmusic_liked_videos", "SE"), state.hiddenPlaylistIds)
    }

    @Test fun `recreated library with persisted visibility rejects remote reappearance`() {
        val hidden = LibrarySnapshot().withoutPlaylist("VLPL-one").hiddenPlaylistIds.toSet()
        val restarted = LibrarySnapshot(hiddenPlaylistIds = hidden)
        assertTrue(restarted.mergeVisiblePlaylists(listOf(playlist("PL-one"))).isEmpty())
    }

    @Test fun `explicit save restores a hidden playlist`() {
        val hidden = LibrarySnapshot().withoutPlaylist("LM")
        val saved = hidden.togglePlaylist(playlist("FEmusic_liked_videos"))
        assertTrue(saved.hiddenPlaylistIds.isEmpty())
        assertEquals(1, saved.mergeVisiblePlaylists(listOf(playlist("VLLM"))).size)
    }

    @Test fun `prefix aliases toggle one list without creating duplicates`() {
        val state = LibrarySnapshot(savedPlaylists = listOf(playlist("VLPL-one")))
        val removed = state.togglePlaylist(playlist("PL-one"))
        assertTrue(removed.savedPlaylists.isEmpty())
        assertEquals(setOf("PL-one"), removed.hiddenPlaylistIds)
    }

    @Test fun `merge preserves cached tracks and deduplicates normalized identifiers`() {
        val track = Track("song", "Song", "Artist")
        val state = LibrarySnapshot(savedPlaylists = listOf(playlist("PL-one").copy(tracks = listOf(track))))
        val merged = state.mergeVisiblePlaylists(listOf(playlist("VLPL-one"), playlist("PL-one")))
        assertEquals(1, merged.size)
        assertEquals(listOf(track), merged.single().tracks)
    }

    @Test fun `removing a list never removes liked songs or another saved list`() {
        val track = Track("song", "Song", "Artist")
        val state = LibrarySnapshot(likedTracks = listOf(track), savedPlaylists = listOf(playlist("LM"), playlist("PL-two")))
        val removed = state.withoutPlaylist("FEmusic_liked_videos")
        assertEquals(listOf(track), removed.likedTracks)
        assertEquals(listOf("PL-two"), removed.savedPlaylists.map { it.id })
    }

    @Test fun `isPlaylistHidden and isItemHidden identify hidden playlists across prefix variants`() {
        val state = LibrarySnapshot().withoutPlaylist("VLPL-target")
        assertTrue(state.isPlaylistHidden("PL-target"))
        assertTrue(state.isPlaylistHidden("VLPL-target"))
        assertFalse(state.isPlaylistHidden("PL-other"))

        val collection = dev.sfg.numa.mobile.model.CatalogItem.Collection(playlist("PL-target"))
        val otherCollection = dev.sfg.numa.mobile.model.CatalogItem.Collection(playlist("PL-other"))
        assertTrue(state.isItemHidden(collection))
        assertFalse(state.isItemHidden(otherCollection))
    }
}
