package dev.sfg.numa.mobile.artwork

import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.*
import org.junit.Test

class AutomaticPlaylistCoversTest {
    private val song = Track(id = "song", title = "Song", artist = "Artist", artworkUrl = "https://example.com/cover.jpg")

    @Test fun `missing source uses membership without changing unrelated playlists`() {
        val enabled = setOf("PL-one", "PL-two")
        val members = mapOf("PL-one" to setOf("song"), "PL-two" to setOf("other"))
        assertEquals(setOf("PL-one"), automaticCoverTargets("", song, enabled) { members[it].orEmpty() })
        assertTrue(automaticCoverTargets("PL-two", song, enabled) { members[it].orEmpty() }.isEmpty())
    }

    @Test fun `offline collection still updates the original playlist cover`() {
        assertEquals(setOf("FEmusic_liked_videos"), automaticCoverTargets("offline_downloads", song, setOf("FEmusic_liked_videos")) { setOf("song") })
    }

    @Test fun `only the source playlist follows its song`() {
        val enabled = setOf("PL-one")
        assertEquals(song.artworkUrl, automaticCoverCandidate("VLPL-one", song, enabled, setOf("song")))
        assertNull(automaticCoverCandidate("PL-two", song, enabled, setOf("song")))
        assertNull(automaticCoverCandidate("", song, enabled, setOf("song")))
    }

    @Test fun `recommendations and unavailable images do not replace cover`() {
        assertNull(automaticCoverCandidate("PL-one", song, setOf("PL-one"), setOf("another-song")))
        assertNull(automaticCoverCandidate("PL-one", song.copy(artworkUrl = ""), setOf("PL-one"), setOf("song")))
        assertNull(automaticCoverCandidate("PL-one", null, setOf("PL-one"), setOf("song")))
    }

    @Test fun `liked music aliases share one mode`() {
        assertEquals("FEmusic_liked_videos", coverPlaylistKey("VLLM"))
        assertEquals("FEmusic_liked_videos", coverPlaylistKey("offline-playlist:VLLM"))
        assertEquals(song.artworkUrl, automaticCoverCandidate("LM", song, setOf("FEmusic_liked_videos"), setOf("song")))
    }

    @Test fun `disabled mode keeps manual cover`() {
        assertNull(automaticCoverCandidate("local-playlist:one", song, emptySet(), setOf("song")))
    }

    @Test fun `upgrades google thumbnail urls for automatic playlist covers`() {
        val thumbSong = song.copy(artworkUrl = "https://lh3.googleusercontent.com/art=w60-h60-l90-rj")
        assertEquals(
            "https://lh3.googleusercontent.com/art=w1200-h1200-l90-rj",
            automaticCoverCandidate("VLLM", thumbSong, setOf("FEmusic_liked_videos"), setOf("song")),
        )
    }
}
