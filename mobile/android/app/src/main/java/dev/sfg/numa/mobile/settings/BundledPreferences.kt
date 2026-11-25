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
    values[booleanPreferencesKey("show_bitrate")] = defaults.showBitrate
    values[booleanPreferencesKey("download_wifi_only")] = defaults.downloadWifiOnly
    values[booleanPreferencesKey("send_youtube_history")] = defaults.sendYouTubeHistory
    values[booleanPreferencesKey("discord_presence_enabled")] = defaults.discordPresenceEnabled
    values[booleanPreferencesKey("autoplay_enabled")] = defaults.autoplayEnabled
    values[booleanPreferencesKey("translate_lyrics")] = defaults.translateLyrics
    values[stringPreferencesKey("cellular_audio_quality")] = defaults.cellularAudioQuality.name
    values[stringPreferencesKey("motion_mode")] = defaults.motionMode.name
    return true
}
