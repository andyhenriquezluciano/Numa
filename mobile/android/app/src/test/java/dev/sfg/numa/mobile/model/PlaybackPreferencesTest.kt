package dev.sfg.numa.mobile.model

import org.junit.Assert.*
import org.junit.Test

class PlaybackPreferencesTest {
    @Test fun transitionChoicesMapToEngineFlagsWithoutConflictingSwitches() {
        for (mode in TransitionPreference.entries) {
            val changed = NumaSettings(playbackOrder = PlaybackOrder.BEST_MIX).withTransition(mode)
            assertEquals(mode, changed.transitionPreference)
            assertEquals(mode != TransitionPreference.OFF, changed.crossfadeEnabled)
            assertEquals(mode == TransitionPreference.ADAPTIVE, changed.smartCrossfade)
            assertEquals(PlaybackOrder.BEST_MIX, changed.playbackOrder)
        }
    }

    @Test fun turningTransitionsOffPreservesTheirDuration() {
        val changed = NumaSettings(crossfadeEnabled = true, smartCrossfade = true, crossfadeSeconds = 8)
            .withTransition(TransitionPreference.OFF)
        assertEquals(8, changed.crossfadeSeconds)
        assertEquals(0L, changed.crossfadeMs)
        assertFalse(changed.smartCrossfade)
    }

    @Test fun explicitShuffleOverridesPreferredBestMix() {
        assertEquals(PlaybackOrder.SHUFFLE, effectivePlaybackOrder(PlaybackOrder.BEST_MIX, true))
        assertEquals(PlaybackOrder.BEST_MIX, effectivePlaybackOrder(PlaybackOrder.BEST_MIX, false))
        assertEquals(PlaybackOrder.ORIGINAL, effectivePlaybackOrder(PlaybackOrder.ORIGINAL, false))
    }

    @Test fun resumingAnOrderedPlaylistPreparesBestMixWithoutRestartingIt() {
        val tracks = List(3) { Track(id = "track-$it", title = "Song $it", artist = "Artist") }
        val paused = PlaybackSnapshot(
            currentTrack = tracks.first(), queue = tracks, currentIndex = 0, contextTitle = "Música que me gusta",
        )
        assertTrue(paused.needsBestMixOnResume(PlaybackOrder.BEST_MIX))
        assertFalse(paused.needsBestMixOnResume(PlaybackOrder.ORIGINAL))
        assertFalse(paused.copy(isPlaying = true).needsBestMixOnResume(PlaybackOrder.BEST_MIX))
        assertFalse(paused.copy(alphabeticalQueue = true).needsBestMixOnResume(PlaybackOrder.BEST_MIX))
        assertFalse(paused.copy(queue = tracks.take(2)).needsBestMixOnResume(PlaybackOrder.BEST_MIX))

        val mixed = paused.copy(contextTitle = "Música que me gusta • Best Mix")
        assertFalse(mixed.needsBestMixOnResume(PlaybackOrder.BEST_MIX))
        assertTrue(mixed.copy(shuffle = true).needsBestMixOnResume(PlaybackOrder.BEST_MIX))
    }

    @Test fun newProfilesStartWithoutFadesOrExponentialVolume() {
        val defaults = NumaSettings()
        assertFalse(defaults.crossfadeEnabled)
        assertFalse(defaults.exponentialVolumeEnabled)
        assertEquals(TransitionPreference.OFF, defaults.transitionPreference)
        assertEquals(PlaybackOrder.ORIGINAL, defaults.playbackOrder)
    }
}
