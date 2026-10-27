package dev.sfg.numa.mobile.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackListenCounterTest {
    private var time = 0L
    private val counter = PlaybackListenCounter { time }

    @Test fun countsOnceAfterFifteenSeconds() {
        assertFalse(counter.shouldRecord("song", true))
        time = 14_999
        assertFalse(counter.shouldRecord("song", true))
        time = 15_000
        assertTrue(counter.shouldRecord("song", true))
        time = 60_000
        assertFalse(counter.shouldRecord("song", true))
    }

    @Test fun loadingAndPausedTimeDoNotCount() {
        assertFalse(counter.shouldRecord("song", false))
        time = 100_000
        assertFalse(counter.shouldRecord("song", true))
        time += 8_000
        assertFalse(counter.shouldRecord("song", false))
        time += 300_000
        assertFalse(counter.shouldRecord("song", true))
        time += 6_999
        assertFalse(counter.shouldRecord("song", true))
        time++
        assertTrue(counter.shouldRecord("song", true))
    }

    @Test fun skipsAndRepeatsStartTheirOwnThreshold() {
        counter.shouldRecord("first", true)
        time = 14_000
        assertFalse(counter.shouldRecord("second", true))
        time += 15_000
        assertTrue(counter.shouldRecord("second", true))
        counter.repeatTrack()
        assertFalse(counter.shouldRecord("second", true))
        time += 15_000
        assertTrue(counter.shouldRecord("second", true))
    }

    @Test fun clearingAndReplayingDoesNotKeepHeardTime() {
        counter.shouldRecord("song", true)
        time = 14_000
        assertFalse(counter.shouldRecord(null, false))
        counter.shouldRecord("song", true)
        time += 1_000
        assertFalse(counter.shouldRecord("song", true))
        time += 14_000
        assertTrue(counter.shouldRecord("song", true))
    }

    @Test fun elapsedTimeBelongsToOutgoingTrackAndExcludesPauses() {
        counter.shouldRecord("first", true)
        time = 4_000
        counter.shouldRecord("second", true)
        assertEquals("first", counter.elapsedTrackId)
        assertEquals(4_000L, counter.elapsedMs)
        time = 6_000
        counter.shouldRecord("second", false)
        assertEquals(2_000L, counter.elapsedMs)
        time = 90_000
        counter.shouldRecord("second", true)
        assertEquals(0L, counter.elapsedMs)
    }
}
