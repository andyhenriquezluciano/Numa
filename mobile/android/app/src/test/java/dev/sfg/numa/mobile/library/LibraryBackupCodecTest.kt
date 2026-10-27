package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryBackupCodecTest {
    @Test fun archiveRoundTripPreservesHistoryAndLibraryMetadata() {
        val song = Track("song", "Title", "Artist", album = "Album")
        val source = LibrarySnapshot(likedTracks = listOf(song), playedTracks = listOf(song),
            playCounts = mapOf("song" to 3), listeningDays = listOf(ListeningDay("2026-10-25", "song", 3, 50_000)),
            hiddenPlaylistIds = setOf("hidden"))
        assertEquals(source, LibraryBackupCodec.decode(JSONObject(LibraryBackupCodec.encode(source).toString())))
    }
    @Test fun legacyCacheHasEmptyDatedHistoryWithoutLosingCounts() {
        val result = LibraryBackupCodec.decode(JSONObject("""{"version":1,"playCounts":{"song":8}}"""))
        assertEquals(8, result.playCounts["song"])
        assertTrue(result.listeningDays.isEmpty())
    }
}
