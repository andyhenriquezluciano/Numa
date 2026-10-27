package dev.sfg.numa.mobile.social

import org.junit.Assert.assertEquals
import org.junit.Test

class PartyClockTest {
    @Test fun tinyDriftUsesBoundedSpeedCorrectionInsteadOfSeeking() {
        assertEquals(1f, partyCorrectionSpeed(5, true), 0.0001f)
        assertEquals(1.015f, partyCorrectionSpeed(30, true), 0.0001f)
        assertEquals(1.02f, partyCorrectionSpeed(200, true), 0.0001f)
        assertEquals(0.98f, partyCorrectionSpeed(-200, true), 0.0001f)
        assertEquals(1f, partyCorrectionSpeed(800, true), 0.0001f)
        assertEquals(1f, partyCorrectionSpeed(200, false), 0.0001f)
    }
    @Test fun compensatesDifferentDeviceClocksAndAudioLoading() {
        val clock = PartyClock()
        clock.sample(100_000, 107_050, 100_100)
        val state = PartyPlaybackState(currentTime = 37.0, sentAt = 107_050, isPlaying = true)
        assertEquals(42_000L, clock.positionMs(state, 105_050))
    }
    @Test fun pausedPlaybackDoesNotAdvanceDuringLoading() {
        val state = PartyPlaybackState(currentTime = 37.0, sentAt = 100_000, isPlaying = false)
        assertEquals(37_000L, PartyClock().positionMs(state, 105_000))
    }
    @Test fun noisySamplesDoNotReplaceTheBestClockMeasurement() {
        val clock = PartyClock()
        clock.sample(100_000, 107_050, 100_100)
        clock.sample(101_000, 130_000, 105_000)
        val state = PartyPlaybackState(currentTime = 37.0, sentAt = 107_050, isPlaying = true)
        assertEquals(37_000L, clock.positionMs(state, 100_050))
    }
    @Test fun positionNeverExceedsTheSongDuration() {
        val state = PartyPlaybackState(currentTime = 37.0, duration = 38.0, sentAt = 100_000, isPlaying = true)
        assertEquals(38_000L, PartyClock().positionMs(state, 105_000))
    }
}
