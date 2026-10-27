/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelsTest {

    @Test
    fun defaultSettingsValues() {
        val settings = NumaSettings()
        assertEquals(GlassTheme.CRISTAL, settings.glassTheme)
        assertEquals(GlassBorders.PREDETERMINADO, settings.glassBorders)
        assertFalse(settings.lockScreenPlayer)
        assertTrue(settings.showSettingsTab)
        assertTrue(settings.playerGesturesEnabled)
        assertTrue(settings.translateLyrics)
        assertTrue(settings.expandedAnimatedArtwork)
        assertEquals(LyricTranslationProvider.GOOGLE, settings.lyricTranslationProvider)
        assertFalse(settings.onboardingCompleted)
        assertTrue(settings.animatedArtwork)
        assertTrue(settings.sendYouTubeHistory)
        assertEquals(listOf(ArtworkSource.M8TEC, ArtworkSource.BOIDU, ArtworkSource.SPOTIFY), settings.artworkSourceOrder)
        assertTrue(settings.downloadAnimatedArtwork)
        assertTrue(settings.animatedBackground)
        assertFalse(settings.crossfadeEnabled)
        assertFalse(settings.smartCrossfade)
        assertFalse(settings.volumeNormalizationEnabled)
        assertFalse(settings.exponentialVolumeEnabled)
        assertFalse(settings.showBitrate)
        assertEquals(1440, settings.videoMaxHeight)
        assertEquals(NonMusicSkipMode.AUTO, settings.nonMusicSkip)
        assertEquals(LyricTranslationQuality.HIGH, settings.lyricTranslationQuality)
        assertEquals(AudioQuality.HIGH, settings.audioQuality)
        assertEquals(NumaSettings.DEFAULT_CACHE_SIZE_MB, settings.cacheSizeMb)
        assertEquals(0L, settings.crossfadeMs)
    }

    @Test
    fun crossfadeCalculation() {
        val enabledSettings = NumaSettings(
            crossfadeEnabled = true,
            crossfadeSeconds = 8,
            smartCrossfade = true,
        )
        assertTrue(enabledSettings.crossfadeEnabled)
        assertTrue(enabledSettings.smartCrossfade)
        assertEquals(8000L, enabledSettings.crossfadeMs)

        val clampedSettings = NumaSettings(
            crossfadeEnabled = true,
            crossfadeSeconds = 99,
        )
        assertEquals(NumaSettings.MAX_CROSSFADE_SECONDS * 1000L, clampedSettings.crossfadeMs)
    }

    @Test
    fun onboardingCompletionFlow() {
        val initial = NumaSettings()
        assertFalse(initial.onboardingCompleted)

        val configured = initial.copy(
            onboardingCompleted = true,
            animatedBackground = true,
            crossfadeEnabled = true,
            crossfadeSeconds = 5,
            smartCrossfade = true,
            audioQuality = AudioQuality.HIGH,
            exponentialVolumeEnabled = true,
            cacheSizeMb = 2048,
        )

        assertTrue(configured.onboardingCompleted)
        assertTrue(configured.animatedBackground)
        assertTrue(configured.crossfadeEnabled)
        assertTrue(configured.smartCrossfade)
        assertTrue(configured.exponentialVolumeEnabled)
        assertEquals(5000L, configured.crossfadeMs)
        assertEquals(2048 * 1024L * 1024L, configured.cacheSizeBytes)
    }
}
