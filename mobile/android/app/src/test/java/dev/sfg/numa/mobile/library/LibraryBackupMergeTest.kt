package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.*
import org.junit.Assert.*
import org.junit.Test

class LibraryBackupMergeTest {
    @Test fun hiddenPlaylistsStayHiddenAfterMerge() {
        val saved = LibrarySnapshot(savedPlaylists = listOf(Playlist(id = "hidden", title = "Hidden", author = "User")))
        val restored = saved.mergeBackup(LibrarySnapshot(hiddenPlaylistIds = setOf("hidden")))
        assertTrue(restored.savedPlaylists.isEmpty())
        assertEquals(setOf("hidden"), restored.hiddenPlaylistIds)
    }
    @Test fun importingSameBackupTwiceNeverDuplicatesListensOrFavorites() {
        val track = Track("a", "Song", "Artist")
        val saved = LibrarySnapshot(likedTracks = listOf(track), playCounts = mapOf("a" to 3),
            listeningDays = listOf(ListeningDay("2026-10-25", "a", 3, 90_000)))
        val restored = LibrarySnapshot().mergeBackup(saved)
        assertEquals(restored, restored.mergeBackup(saved))
    }
    @Test fun mergePreservesCurrentMetadataAndHigherCounters() {
        val current = LibrarySnapshot(likedTracks = listOf(Track("a", "New", "Artist")), playCounts = mapOf("a" to 8))
        val old = LibrarySnapshot(likedTracks = listOf(Track("a", "Old", "Artist"), Track("b", "Other", "Artist")),
            playCounts = mapOf("a" to 2))
        val merged = current.mergeBackup(old)
        assertEquals(8, merged.playCounts["a"])
        assertEquals(listOf("New", "Other"), merged.likedTracks.map { it.title })
    }
}
