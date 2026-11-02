package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAliasTest {
    @Test fun `likes aliases from PC and Android reconcile into one playlist`() {
        val local = LibrarySnapshot(savedPlaylists = listOf(Playlist("VLLM", "Liked music")))
        val remote = LibrarySnapshot(savedPlaylists = listOf(Playlist("FEmusic_liked_videos", "Liked music")))
        val merged = reconcileLibrary(LibrarySnapshot(), local, remote)
        assertEquals(1, merged.savedPlaylists.size)
        assertEquals("VLLM", merged.savedPlaylists.single().id)
    }
}
