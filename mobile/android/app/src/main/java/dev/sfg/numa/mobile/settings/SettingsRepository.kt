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

package dev.sfg.numa.mobile.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sfg.numa.mobile.model.AudioQuality
import dev.sfg.numa.mobile.model.ArtworkSource
import dev.sfg.numa.mobile.model.EqualizerConfig
import dev.sfg.numa.mobile.model.LyricTranslationQuality
import dev.sfg.numa.mobile.model.LyricTranslationProvider
import dev.sfg.numa.mobile.model.NonMusicSkipMode
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.BuiltInHomeSection
import dev.sfg.numa.mobile.model.HomeSectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Context.numaDataStore: DataStore<Preferences> by preferencesDataStore(name = "numa_settings")

/** Transactional settings and bounded search history backed by DataStore. */
class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.numaDataStore
    suspend fun lockScreenEnabled(): Boolean = store.data.first()[LOCK_SCREEN_PLAYER] ?: false
    init {
        scope.launch {
            store.edit { values -> seedBundledPreferences(values) }
        }
    }
    val settings: StateFlow<NumaSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { values ->
            NumaSettings(
                glassTheme = runCatching { GlassTheme.valueOf(values[GLASS_THEME].orEmpty()) }.getOrDefault(GlassTheme.CRISTAL),
                glassBorders = runCatching { GlassBorders.valueOf(values[GLASS_BORDERS].orEmpty()) }.getOrDefault(GlassBorders.PREDETERMINADO),
                lockScreenPlayer = values[LOCK_SCREEN_PLAYER] ?: false,
                animatedArtwork = values[ANIMATED_ARTWORK] ?: true,
                expandedAnimatedArtwork = values[EXPANDED_ANIMATED_ARTWORK] ?: true,
                artworkSourceOrder = decodeArtworkOrder(values[ARTWORK_SOURCE_ORDER]),
                sendYouTubeHistory = values[SEND_YOUTUBE_HISTORY] ?: true,
                downloadAnimatedArtwork = values[DOWNLOAD_ANIMATED_ARTWORK] ?: true,
                downloadToSdCard = values[DOWNLOAD_TO_SD_CARD] ?: false,
                audioQuality = runCatching { AudioQuality.valueOf(values[AUDIO_QUALITY].orEmpty()) }
                    .getOrDefault(AudioQuality.HIGH).let { if (it == AudioQuality.MAX) AudioQuality.HIGH else it },
                downloadQuality = runCatching { AudioQuality.valueOf(values[DOWNLOAD_QUALITY].orEmpty()) }
                    .getOrDefault(AudioQuality.HIGH),
                cellularAudioQuality = runCatching { AudioQuality.valueOf(values[CELLULAR_AUDIO_QUALITY].orEmpty()) }
                    .getOrDefault(AudioQuality.HIGH).let { if (it == AudioQuality.MAX) AudioQuality.HIGH else it },
                useSystemColors = false,
                animatedBackground = values[ANIMATED_BACKGROUND] ?: true,
                crossfadeEnabled = values[CROSSFADE_ENABLED] ?: false,
                crossfadeSeconds = values[CROSSFADE_SECONDS] ?: NumaSettings.DEFAULT_CROSSFADE_SECONDS,
                smartCrossfade = values[SMART_CROSSFADE] ?: false,
                playbackOrder = dev.sfg.numa.mobile.model.PlaybackOrder.entries.firstOrNull { it.name == values[PLAYBACK_ORDER] } ?: dev.sfg.numa.mobile.model.PlaybackOrder.ORIGINAL,
                cacheSizeMb = values[CACHE_SIZE_MB] ?: NumaSettings.DEFAULT_CACHE_SIZE_MB,
                onboardingCompleted = values[ONBOARDING_COMPLETED] ?: false,
                discordPresenceEnabled = values[DISCORD_PRESENCE_ENABLED] ?: true,
                showBitrate = values[SHOW_BITRATE] ?: false,
                spotifySpdc = values[SPOTIFY_SPDC] ?: "",
                spotifyCanvasEnabled = values[SPOTIFY_CANVAS_ENABLED] ?: true,
                volumeNormalizationEnabled = values[VOLUME_NORMALIZATION_ENABLED] ?: false,
                exponentialVolumeEnabled = values[EXPONENTIAL_VOLUME_ENABLED] ?: false,
                autoplayEnabled = values[AUTOPLAY_ENABLED] ?: true,
                nonMusicSkip = runCatching { NonMusicSkipMode.valueOf(values[NON_MUSIC_SKIP].orEmpty()) }
                    .getOrDefault(NonMusicSkipMode.AUTO),
                equalizerConfig = EqualizerConfig(
                    enabled = values[EQUALIZER_ENABLED] ?: false,
                    presetId = values[EQUALIZER_PRESET] ?: "flat",
                    gains = decodeFloatList(values[EQUALIZER_GAINS].orEmpty(), 10),
                    preampDb = values[EQUALIZER_PREAMP] ?: 0f,
                    bassBoost = values[EQUALIZER_BASS_BOOST] ?: 0f,
                ),
                playerGesturesEnabled = values[PLAYER_GESTURES_ENABLED] ?: true,
                showSettingsTab = values[SHOW_SETTINGS_TAB] ?: true,
                homeLayoutOnline = decodeHomeLayout(values[HOME_LAYOUT_ONLINE], true),
                homeLayoutOffline = decodeHomeLayout(values[HOME_LAYOUT_OFFLINE], false),
                customDeviceName = values[CUSTOM_DEVICE_NAME] ?: "",
                betaChannelEnabled = values[BETA_CHANNEL_ENABLED] ?: false,
                videoMaxHeight = values[VIDEO_MAX_HEIGHT] ?: 1080,
                translateLyrics = values[TRANSLATE_LYRICS] ?: true,
                lyricTranslationQuality = LyricTranslationQuality.entries
                    .firstOrNull { it.key == values[LYRIC_TRANSLATION_QUALITY] } ?: LyricTranslationQuality.HIGH,
                lyricTranslationProvider = LyricTranslationProvider.entries
                    .firstOrNull { it.key == values[LYRIC_TRANSLATION_PROVIDER] &&
                        it in listOf(LyricTranslationProvider.LOCAL, LyricTranslationProvider.GOOGLE) }
                    ?: LyricTranslationProvider.GOOGLE,
                lyricTranslationModels = runCatching {
                    val json = org.json.JSONObject(values[LYRIC_TRANSLATION_MODELS].orEmpty())
                    json.keys().asSequence().associateWith { json.optString(it) }
                }.getOrDefault(emptyMap()),
                lyricTranslationEndpoint = values[LYRIC_TRANSLATION_ENDPOINT] ?: "",
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, NumaSettings())

    val searchHistory: StateFlow<List<String>> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeHistory(it[SEARCH_HISTORY].orEmpty()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun updateSettings(value: NumaSettings) {
        scope.launch {
            store.edit {
                it[GLASS_THEME] = value.glassTheme.name
                it[GLASS_BORDERS] = value.glassBorders.name
                it[LOCK_SCREEN_PLAYER] = value.lockScreenPlayer
                it[ANIMATED_ARTWORK] = value.animatedArtwork
                it[EXPANDED_ANIMATED_ARTWORK] = value.expandedAnimatedArtwork
                it[ARTWORK_SOURCE_ORDER] = value.artworkSourceOrder.joinToString(",") { source -> source.name }
                it[SEND_YOUTUBE_HISTORY] = value.sendYouTubeHistory
                it[DOWNLOAD_ANIMATED_ARTWORK] = value.downloadAnimatedArtwork
                it[DOWNLOAD_TO_SD_CARD] = value.downloadToSdCard
                it[AUDIO_QUALITY] = value.audioQuality.name
                it[DOWNLOAD_QUALITY] = value.downloadQuality.name
                it[CELLULAR_AUDIO_QUALITY] = value.cellularAudioQuality.name
                it[SYSTEM_COLORS] = false
                it[ANIMATED_BACKGROUND] = value.animatedBackground
                it[CROSSFADE_ENABLED] = value.crossfadeEnabled
                it[CROSSFADE_SECONDS] = value.crossfadeSeconds
                it[SMART_CROSSFADE] = value.smartCrossfade
                it[PLAYBACK_ORDER] = value.playbackOrder.name
                it[CACHE_SIZE_MB] = value.cacheSizeMb
                it[ONBOARDING_COMPLETED] = value.onboardingCompleted
                it[DISCORD_PRESENCE_ENABLED] = value.discordPresenceEnabled
                it[SHOW_BITRATE] = value.showBitrate
                it[SPOTIFY_SPDC] = value.spotifySpdc
                it[SPOTIFY_CANVAS_ENABLED] = value.spotifyCanvasEnabled
                it[VOLUME_NORMALIZATION_ENABLED] = value.volumeNormalizationEnabled
                it[EXPONENTIAL_VOLUME_ENABLED] = value.exponentialVolumeEnabled
                it[AUTOPLAY_ENABLED] = value.autoplayEnabled
                it[NON_MUSIC_SKIP] = value.nonMusicSkip.name
                it[EQUALIZER_ENABLED] = value.equalizerConfig.enabled
                it[EQUALIZER_PRESET] = value.equalizerConfig.presetId
                it[EQUALIZER_GAINS] = value.equalizerConfig.gains.joinToString(",")
                it[EQUALIZER_PREAMP] = value.equalizerConfig.preampDb
                it[EQUALIZER_BASS_BOOST] = value.equalizerConfig.bassBoost
                it[PLAYER_GESTURES_ENABLED] = value.playerGesturesEnabled
                it[SHOW_SETTINGS_TAB] = value.showSettingsTab
                it[HOME_LAYOUT_ONLINE] = encodeHomeLayout(value.homeLayoutOnline)
                it[HOME_LAYOUT_OFFLINE] = encodeHomeLayout(value.homeLayoutOffline)
                it[CUSTOM_DEVICE_NAME] = value.customDeviceName
                it[BETA_CHANNEL_ENABLED] = value.betaChannelEnabled
                it[VIDEO_MAX_HEIGHT] = value.videoMaxHeight
                it[TRANSLATE_LYRICS] = value.translateLyrics
                it[LYRIC_TRANSLATION_QUALITY] = value.lyricTranslationQuality.key
                it[LYRIC_TRANSLATION_PROVIDER] = value.lyricTranslationProvider.key
                it[LYRIC_TRANSLATION_MODELS] = org.json.JSONObject(value.lyricTranslationModels).toString()
                it[LYRIC_TRANSLATION_ENDPOINT] = value.lyricTranslationEndpoint
            }
        }
    }

    fun recordSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        scope.launch {
            store.edit { values ->
                val next = listOf(normalized) + decodeHistory(values[SEARCH_HISTORY].orEmpty())
                    .filterNot { it.equals(normalized, ignoreCase = true) }
                    .take(9)
                values[SEARCH_HISTORY] = JSONArray(next).toString()
            }
        }
    }

    fun clearSearchHistory() {
        scope.launch { store.edit { it.remove(SEARCH_HISTORY) } }
    }

    fun removeSearchHistoryItem(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        scope.launch {
            store.edit { values ->
                val next = decodeSearchHistory(values[SEARCH_HISTORY].orEmpty())
                    .filterNot { it.equals(normalized, ignoreCase = true) }
                values[SEARCH_HISTORY] = JSONArray(next).toString()
            }
        }
    }

    private fun decodeHistory(value: String): List<String> = decodeSearchHistory(value)

    private fun decodeArtworkOrder(value: String?): List<ArtworkSource> =
        value.orEmpty().split(',').mapNotNull { name ->
            ArtworkSource.entries.firstOrNull { it.name == name }
        }.distinct().let { saved -> saved + ArtworkSource.entries.filterNot(saved::contains) }

    private fun decodeFloatList(value: String, expectedSize: Int): List<Float> {
        if (value.isBlank()) return List(expectedSize) { 0f }
        val parts = value.split(",")
        return List(expectedSize) { i -> parts.getOrNull(i)?.toFloatOrNull() ?: 0f }
    }

    private fun decodeHomeLayout(value: String?, online: Boolean): List<HomeSectionConfig> {
        if (value.isNullOrBlank()) {
            return if (online) NumaSettings().homeLayoutOnline else NumaSettings().homeLayoutOffline
        }
        return runCatching {
            val arr = JSONArray(value)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val sectionStr = obj.optString("section")
                    val enabled = obj.optBoolean("enabled", true)
                    runCatching { BuiltInHomeSection.valueOf(sectionStr) }.getOrNull()?.let { section ->
                        add(HomeSectionConfig(section, enabled))
                    }
                }
            }
        }.getOrDefault(if (online) NumaSettings().homeLayoutOnline else NumaSettings().homeLayoutOffline)
    }

    private fun encodeHomeLayout(layout: List<HomeSectionConfig>): String {
        val arr = JSONArray()
        layout.forEach { config ->
            val obj = org.json.JSONObject()
            obj.put("section", config.section.name)
            obj.put("enabled", config.enabled)
            arr.put(obj)
        }
        return arr.toString()
    }


    private companion object {
        val GLASS_THEME = stringPreferencesKey("glass_theme")
        val GLASS_BORDERS = stringPreferencesKey("glass_borders")
        val LOCK_SCREEN_PLAYER = booleanPreferencesKey("lock_screen_player")
        val ANIMATED_ARTWORK = booleanPreferencesKey("animated_artwork")
        val EXPANDED_ANIMATED_ARTWORK = booleanPreferencesKey("expanded_animated_artwork")
        val ARTWORK_SOURCE_ORDER = stringPreferencesKey("artwork_source_order")
        val SEND_YOUTUBE_HISTORY = booleanPreferencesKey("send_youtube_history")
        val DOWNLOAD_ANIMATED_ARTWORK = booleanPreferencesKey("download_animated_artwork")
        val DOWNLOAD_TO_SD_CARD = booleanPreferencesKey("download_to_sd_card")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val CELLULAR_AUDIO_QUALITY = stringPreferencesKey("cellular_audio_quality")
        val VIDEO_MAX_HEIGHT = intPreferencesKey("video_max_height")
        val TRANSLATE_LYRICS = booleanPreferencesKey("translate_lyrics")
        val LYRIC_TRANSLATION_QUALITY = stringPreferencesKey("lyric_translation_quality")
        val LYRIC_TRANSLATION_PROVIDER = stringPreferencesKey("lyric_translation_provider")
        val LYRIC_TRANSLATION_MODELS = stringPreferencesKey("lyric_translation_models")
        val LYRIC_TRANSLATION_ENDPOINT = stringPreferencesKey("lyric_translation_endpoint")
        val SYSTEM_COLORS = booleanPreferencesKey("system_colors")
        val ANIMATED_BACKGROUND = booleanPreferencesKey("animated_background")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val SMART_CROSSFADE = booleanPreferencesKey("smart_crossfade")
        val PLAYBACK_ORDER = stringPreferencesKey("playback_order")
        val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DISCORD_PRESENCE_ENABLED = booleanPreferencesKey("discord_presence_enabled")
        val SHOW_BITRATE = booleanPreferencesKey("show_bitrate")
        val SPOTIFY_SPDC = stringPreferencesKey("spotify_spdc")
        val SPOTIFY_CANVAS_ENABLED = booleanPreferencesKey("spotify_canvas_enabled")
        val VOLUME_NORMALIZATION_ENABLED = booleanPreferencesKey("volume_normalization_enabled")
        val EXPONENTIAL_VOLUME_ENABLED = booleanPreferencesKey("exponential_volume_enabled")
        val AUTOPLAY_ENABLED = booleanPreferencesKey("autoplay_enabled")
        val NON_MUSIC_SKIP = stringPreferencesKey("non_music_skip")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_GAINS = stringPreferencesKey("equalizer_gains")
        val EQUALIZER_PREAMP = floatPreferencesKey("equalizer_preamp")
        val EQUALIZER_BASS_BOOST = floatPreferencesKey("equalizer_bass_boost")
        val PLAYER_GESTURES_ENABLED = booleanPreferencesKey("player_gestures_enabled")
        val SHOW_SETTINGS_TAB = booleanPreferencesKey("show_settings_tab")
        val HOME_LAYOUT_ONLINE = stringPreferencesKey("home_layout_online")
        val HOME_LAYOUT_OFFLINE = stringPreferencesKey("home_layout_offline")
        val CUSTOM_DEVICE_NAME = stringPreferencesKey("custom_device_name")
        val BETA_CHANNEL_ENABLED = booleanPreferencesKey("beta_channel_enabled")
    }
}

internal fun decodeSearchHistory(value: String): List<String> = runCatching {
    val values = JSONArray(value)
    buildList {
        for (index in 0 until values.length()) values.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}.getOrDefault(emptyList())
