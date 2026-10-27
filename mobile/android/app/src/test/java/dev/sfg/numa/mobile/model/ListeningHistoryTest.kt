package dev.sfg.numa.mobile.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ListeningHistoryTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val track = Track("a", "Song", "Artist", album = "Album")

    @Test fun periodIncludesBoundaryAndExcludesFutureAndOlderRows() {
        val snapshot = LibrarySnapshot(playedTracks = listOf(track), playCounts = mapOf("a" to 20), listeningDays = listOf(
            ListeningDay("2026-10-19", "a", 2, 60_000), ListeningDay("2026-10-18", "a", 3, 90_000),
            ListeningDay("2026-10-26", "a", 4, 120_000)))
        assertEquals(2, snapshot.listeningReport(MetricsPeriod.WEEK, today).counts["a"])
        assertEquals(60_000L, snapshot.listeningReport(MetricsPeriod.WEEK, today).heardMs)
        assertEquals(20, snapshot.listeningReport(MetricsPeriod.ALL, today).counts["a"])
    }
    @Test fun timeWithoutQualifiedListenDoesNotEnterRanking() {
        val snapshot = LibrarySnapshot().withListeningTime(track, 14_999, today)
        assertTrue(snapshot.listeningReport(MetricsPeriod.WEEK, today).tracks.isEmpty())
        assertEquals(14_999L, snapshot.listeningReport(MetricsPeriod.WEEK, today).heardMs)
    }
    @Test fun groupsArtistsAndAlbumsByQualifiedListens() {
        val snapshot = LibrarySnapshot(playedTracks = listOf(track, track.copy(id = "b")), playCounts = mapOf("a" to 2, "b" to 3))
        assertEquals(listOf("Artist" to 5), snapshot.listeningReport(MetricsPeriod.ALL, today).artists)
        assertEquals(listOf("Album" to 5), snapshot.listeningReport(MetricsPeriod.ALL, today).albums)
    }
}
