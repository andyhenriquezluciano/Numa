package dev.sfg.numa.mobile.playback

import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPlaybackTest {
    private val song = Track(id = "song", title = "Song", artist = "Artist")
    @Test fun playingCollectionCanPause() {
        assertTrue(collectionOwnsPlayback("LM", setOf("song"), PlaybackSnapshot(currentTrack = song, sourcePlaylistId = "LM", isPlaying = true)))
    }
    @Test fun pausedCollectionCanResumeWithoutReplacingTheQueue() {
        assertTrue(collectionOwnsPlayback("LM", setOf("song"), PlaybackSnapshot(currentTrack = song, sourcePlaylistId = "VLLM", isPlaying = false)))
    }
    @Test fun anotherPlaylistDoesNotPauseJustBecauseItSharesASong() {
        assertFalse(collectionOwnsPlayback("other", setOf("song"), PlaybackSnapshot(currentTrack = song, sourcePlaylistId = "LM", isPlaying = true)))
    }
    @Test fun singleTracksAndAlbumsUseMembershipWhenThereIsNoPlaylistSource() {
        assertTrue(collectionOwnsPlayback("album", setOf("song"), PlaybackSnapshot(currentTrack = song)))
        assertFalse(collectionOwnsPlayback("album", setOf("different"), PlaybackSnapshot(currentTrack = song)))
    }
    @Test fun emptyPlaybackNeverBelongsToACollection() {
        assertFalse(collectionOwnsPlayback("LM", setOf("song"), PlaybackSnapshot()))
    }
    @Test fun theSourcePlaylistRemainsActiveWhileItsSongsAreStillLoading() {
        assertTrue(collectionOwnsPlayback("LM", emptySet(), PlaybackSnapshot(currentTrack = song, sourcePlaylistId = "LM")))
    }
}
