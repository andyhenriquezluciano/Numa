/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sfg.numa.mobile.model.NumaSettings

internal fun seedBundledPreferences(values: MutablePreferences): Boolean {
    if (values.asMap().isNotEmpty()) return false
    val defaults = NumaSettings()
    values[booleanPreferencesKey("download_animated_artwork")] = defaults.downloadAnimatedArtwork
    values[booleanPreferencesKey("animated_background")] = defaults.animatedBackground
    values[booleanPreferencesKey("crossfade_enabled")] = defaults.crossfadeEnabled
    values[intPreferencesKey("crossfade_seconds")] = defaults.crossfadeSeconds
    values[booleanPreferencesKey("exponential_volume_enabled")] = defaults.exponentialVolumeEnabled
    values[stringPreferencesKey("non_music_skip")] = defaults.nonMusicSkip.name
    values[intPreferencesKey("video_max_height")] = defaults.videoMaxHeight
    values[stringPreferencesKey("lyric_translation_quality")] = defaults.lyricTranslationQuality.key
    values[booleanPreferencesKey("show_bitrate")] = false
    return true
}
