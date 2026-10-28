package dev.sfg.numa.mobile.model

import org.junit.Assert.*
import org.junit.Test

class ListeningMetricsTest {
    @Test fun historicalEntriesRecoverCoversAndAlbumIdsWithoutChangingCounts() {
        val old = Track("song", "Song", "Artist", album = "Album")
        val fresh = old.copy(artworkUrl = "https://example.com/cover.jpg", albumId = "MPRE-album",
            artists = listOf(Artist("UC-artist", "Artist", "https://example.com/artist.jpg")))
        val library = LibrarySnapshot(playedTracks = listOf(old), likedTracks = listOf(fresh), playCounts = mapOf("song" to 4))
        val report = library.listeningReport(MetricsPeriod.ALL)
        assertEquals(1, report.tracks.size)
        assertEquals(fresh.artworkUrl, report.tracks.single().artworkUrl)
        assertEquals(fresh.albumId, report.tracks.single().albumId)
        assertEquals(4, report.counts["song"])
        assertEquals("https://example.com/artist.jpg", report.collections(library, true).single().artworkUrl)
    }

    private fun track(id: String) = Track(id, id, "Artist")

    @Test fun ranksCountsDescendingAndIncludesOneListenButNotZero() {
        val snapshot = LibrarySnapshot(
            likedTracks = listOf(track("zero"), track("one"), track("most")),
            playCounts = mapOf("zero" to 0, "one" to 1, "most" to 8),
        )
        assertEquals(listOf("most", "one"), snapshot.mostPlayed.map(Track::id))
    }

    @Test fun olderSongsRemainRankedAfterLeavingRecentHistory() {
        val old = track("old")
        var snapshot = LibrarySnapshot().withRecordedListen(old).withRecordedListen(old)
        repeat(60) { snapshot = snapshot.withRecordedListen(track("song-$it")) }
        assertFalse(snapshot.recentlyPlayed.any { it.id == old.id })
        assertEquals(old, snapshot.mostPlayed.first())
        assertEquals(61, snapshot.mostPlayed.size)
    }

    @Test fun duplicateMetadataIsRankedOnceAndUpdatedOnTheNextListen() {
        val song = track("song")
        val snapshot = LibrarySnapshot(likedTracks = listOf(song))
            .withRecordedListen(song).withRecordedListen(song.copy(title = "Updated"))
        assertEquals(2, snapshot.playCounts[song.id])
        assertEquals(listOf("Updated"), snapshot.mostPlayed.map(Track::title))
    }

    @Test fun missingAndNegativeCountsDoNotBecomeListens() {
        val snapshot = LibrarySnapshot(recentlyPlayed = listOf(track("none"), track("negative")),
            playCounts = mapOf("negative" to -1))
        assertTrue(snapshot.mostPlayed.isEmpty())
    }
}
