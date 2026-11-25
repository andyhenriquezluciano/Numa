package dev.sfg.numa.mobile.model

import org.junit.Assert.*
import org.junit.Test

class PlaybackPreferencesTest {
    @Test fun transitionChoicesMapToEngineFlagsWithoutConflictingSwitches() {
        for (mode in TransitionPreference.entries) {
            val changed = NumaSettings().withTransition(mode)
            assertEquals(mode, changed.transitionPreference)
            assertEquals(mode != TransitionPreference.OFF, changed.crossfadeEnabled)
            assertEquals(mode == TransitionPreference.ADAPTIVE, changed.smartCrossfade)
        }
    }

    @Test fun turningTransitionsOffPreservesTheirDuration() {
        val changed = NumaSettings(crossfadeEnabled = true, smartCrossfade = true, crossfadeSeconds = 8)
            .withTransition(TransitionPreference.OFF)
        assertEquals(8, changed.crossfadeSeconds)
        assertEquals(0L, changed.crossfadeMs)
        assertFalse(changed.smartCrossfade)
    }

    @Test fun newProfilesStartWithoutFadesOrExponentialVolume() {
        val defaults = NumaSettings()
        assertFalse(defaults.crossfadeEnabled)
        assertFalse(defaults.exponentialVolumeEnabled)
        assertEquals(TransitionPreference.OFF, defaults.transitionPreference)
    }
}
