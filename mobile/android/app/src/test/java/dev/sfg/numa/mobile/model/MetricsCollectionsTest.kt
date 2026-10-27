package dev.sfg.numa.mobile.model

import org.junit.Assert.*
import org.junit.Test

class MetricsCollectionsTest {
    private val a = Track("a", "One", "Artist", album = "Album", albumId = "album", artworkUrl = "cover",
        artists = listOf(Artist("artist", "Artist", "photo")))
    private fun library(vararg tracks: Track) = LibrarySnapshot(playedTracks = tracks.toList(),
        playCounts = tracks.associate { it.id to 2 })

    @Test fun albumsCountDistinctSongsAndKeepArtworkAndListens() {
        val lib = library(a, a.copy(id = "b"))
        val row = lib.listeningReport(MetricsPeriod.ALL).collections(lib, false).single()
        assertEquals("Album", row.name)
        assertEquals("cover", row.artworkUrl)
        assertEquals(2, row.songCount)
        assertEquals(4, row.listens)
    }
    @Test fun artistsUsePortraitsAndCreditCollaboratorsSeparately() {
        val b = a.copy(id = "b", artists = listOf(Artist("artist", "Artist", "photo"), Artist("guest", "Guest")))
        val lib = library(a, b)
        val rows = lib.listeningReport(MetricsPeriod.ALL).collections(lib, true)
        assertEquals(listOf("Artist", "Guest"), rows.map { it.name })
        assertEquals(listOf(2, 1), rows.map { it.songCount })
        assertEquals("photo", rows.first().artworkUrl)
        assertEquals("", rows.last().artworkUrl)
    }
    @Test fun differentAlbumsWithSameTitleStaySeparate() {
        val lib = library(a, a.copy(id = "b", albumId = "other", artist = "Other"))
        assertEquals(2, lib.listeningReport(MetricsPeriod.ALL).collections(lib, false).size)
    }
    @Test fun artistFallbackUsesSavedPortraitNeverSongCover() {
        val lib = library(a.copy(artists = emptyList())).copy(savedArtists = listOf(Artist("artist", "Artist", "saved-photo")))
        val row = lib.listeningReport(MetricsPeriod.ALL).collections(lib, true).single()
        assertEquals("saved-photo", row.artworkUrl)
        assertEquals("artist", row.browseId)
    }
}
