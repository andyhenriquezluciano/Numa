package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.CatalogKind
import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.*
import org.junit.Test

class LikedDetailTest {
    private val track = Track(id = "song", title = "Song", artist = "Artist")

    @Test fun removesImmediatelyForEveryLikedPlaylistAlias() {
        listOf("LM", "VLLM", "FEmusic_liked_videos", "VLFEmusic_liked_videos").forEach { id ->
            val detail = BrowseDetail(id, CatalogKind.PLAYLIST, "Me gusta", tracks = listOf(track))
            assertTrue(detail.withLikedTrack(track, false).tracks.isEmpty())
            assertEquals(listOf(track), detail.withLikedTrack(track, false).withLikedTrack(track, true).tracks)
        }
    }

    @Test fun leavesOtherCollectionsUntouchedAndDoesNotDuplicateLikes() {
        val other = BrowseDetail("PL-other", CatalogKind.PLAYLIST, "Otra", tracks = listOf(track))
        assertSame(other, other.withLikedTrack(track, false))
        val likes = other.copy(id = "LM")
        assertSame(likes, likes.withLikedTrack(track, true))
    }
}
