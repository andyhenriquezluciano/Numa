package dev.sfg.numa.mobile.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class BundledPreferencesTest {
    @Test fun freshProfilesReceiveDesktopPreferencesWithoutPersonalData() {
        val values = mutablePreferencesOf()
        assertTrue(seedBundledPreferences(values))
        assertEquals(false, values[booleanPreferencesKey("show_bitrate")])
        assertEquals(false, values[booleanPreferencesKey("crossfade_enabled")])
        assertEquals(false, values[booleanPreferencesKey("exponential_volume_enabled")])
        assertEquals(3, values[intPreferencesKey("crossfade_seconds")])
        assertEquals(1440, values[intPreferencesKey("video_max_height")])
        assertEquals(9, values.asMap().size)
    }

    @Test fun existingProfilesAreNotChanged() {
        val key = booleanPreferencesKey("show_bitrate")
        val values = mutablePreferencesOf(key to true)
        assertFalse(seedBundledPreferences(values))
        assertEquals(true, values[key])
        assertEquals(1, values.asMap().size)
    }

    @Test fun explicitlyEnabledAudioPreferencesSurviveDefaultChanges() {
        val crossfade = booleanPreferencesKey("crossfade_enabled")
        val exponential = booleanPreferencesKey("exponential_volume_enabled")
        val values = mutablePreferencesOf(crossfade to true, exponential to true)
        assertFalse(seedBundledPreferences(values))
        assertEquals(true, values[crossfade])
        assertEquals(true, values[exponential])
    }
}
