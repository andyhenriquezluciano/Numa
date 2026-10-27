package dev.sfg.numa.mobile.model

import org.junit.Assert.*
import org.junit.Test

class ListeningMetricsTest {
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
