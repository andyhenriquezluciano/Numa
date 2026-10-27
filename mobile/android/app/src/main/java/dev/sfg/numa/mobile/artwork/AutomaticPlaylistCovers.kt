package dev.sfg.numa.mobile.artwork

import android.content.Context
import dev.sfg.numa.mobile.download.OfflineArtwork
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val AUTOMATIC_COVER_PIXELS = 1200

internal fun coverPlaylistKey(id: String): String = id.removePrefix("offline-playlist:").removePrefix("VL").let {
    if (it == "LM") "FEmusic_liked_videos" else it
}

data class AutomaticCoverState(val enabled: Set<String> = emptySet(), val artwork: Map<String, String> = emptyMap())

internal fun automaticCoverCandidate(sourceId: String, track: Track?, enabled: Set<String>, members: Set<String>): String? {
    if (sourceId.isBlank() || coverPlaylistKey(sourceId) !in enabled || track == null || track.id !in members) return null
    return OfflineArtwork.usableArtworkUrl(track.artworkUrl, AUTOMATIC_COVER_PIXELS).takeIf { it.isNotBlank() }
}

internal fun automaticCoverTargets(sourceId: String, track: Track?, enabled: Set<String>, members: (String) -> Set<String>): Set<String> {
    if (track == null || OfflineArtwork.usableArtworkUrl(track.artworkUrl, AUTOMATIC_COVER_PIXELS).isBlank()) return emptySet()
    val key = coverPlaylistKey(sourceId)
    val genericSource = sourceId.isBlank() || key in setOf("offline_downloads", "downloads", "local_downloads")
    return enabled.filterTo(mutableSetOf()) { candidate ->
        (genericSource || candidate == key) && track.id in members(candidate)
    }
}

/** Automatic covers retain the last song from their own playback source across app restarts. */
class AutomaticPlaylistCovers(context: Context) {
    private val preferences = context.getSharedPreferences("automatic-playlist-covers", Context.MODE_PRIVATE)
    private val backup = android.app.backup.BackupManager(context.applicationContext)
    private val mutable = MutableStateFlow(loadInitialState())
    val state = mutable.asStateFlow()
    fun reload() { mutable.value = loadInitialState() }
    private val membershipRevision = MutableStateFlow(0L)
    private var observation: Job? = null

    private fun loadInitialState(): AutomaticCoverState {
        val all = preferences.all
        val enabled = all.filter { it.key.startsWith("mode:") && it.value == true }
            .keys.map { it.removePrefix("mode:") }.toSet()
        val artwork = mutableMapOf<String, String>()
        val editor = preferences.edit()
        var dirty = false
        for ((rawKey, value) in all) {
            if (!rawKey.startsWith("art:") || value !is String) continue
            val key = rawKey.removePrefix("art:")
            val usable = OfflineArtwork.usableArtworkUrl(value, AUTOMATIC_COVER_PIXELS)
            if (usable.isBlank()) {
                editor.remove(rawKey)
                dirty = true
            } else {
                artwork[key] = usable
                if (usable != value) {
                    editor.putString(rawKey, usable)
                    dirty = true
                }
            }
        }
        if (dirty) editor.apply()
        return AutomaticCoverState(enabled = enabled, artwork = artwork)
    }

    fun setEnabled(id: String, enabled: Boolean, candidateArtwork: String? = null) {
        val key = coverPlaylistKey(id)
        val editor = preferences.edit().putBoolean("mode:$key", enabled)
        val sanitized = OfflineArtwork.usableArtworkUrl(candidateArtwork.orEmpty(), AUTOMATIC_COVER_PIXELS)
        val nextArt = if (enabled && sanitized.isNotBlank() && mutable.value.artwork[key].isNullOrBlank()) {
            editor.putString("art:$key", sanitized)
            mutable.value.artwork + (key to sanitized)
        } else {
            mutable.value.artwork
        }
        editor.apply()
        mutable.update { it.copy(enabled = if (enabled) it.enabled + key else it.enabled - key, artwork = nextArt) }
        runCatching { backup.dataChanged() }
    }

    @Synchronized
    fun rememberSource(id: String, tracks: List<Track>) {
        if (id.isBlank()) return
        val playlistKey = coverPlaylistKey(id)
        val key = "tracks:$playlistKey"
        val members = preferences.getStringSet(key, emptySet()).orEmpty() + tracks.map { it.id }
        val editor = preferences.edit().putStringSet(key, members)
        if (playlistKey in mutable.value.enabled && mutable.value.artwork[playlistKey].isNullOrBlank()) {
            val seed = tracks.firstNotNullOfOrNull {
                OfflineArtwork.usableArtworkUrl(it.artworkUrl, AUTOMATIC_COVER_PIXELS).takeIf(String::isNotBlank)
            }
            if (seed != null) {
                editor.putString("art:$playlistKey", seed)
                mutable.update { it.copy(artwork = it.artwork + (playlistKey to seed)) }
            }
        }
        editor.apply()
        membershipRevision.value++
    }

    fun purge(id: String) {
        val key = coverPlaylistKey(id)
        preferences.edit().remove("mode:$key").remove("tracks:$key").remove("art:$key").apply()
        mutable.update { it.copy(enabled = it.enabled - key, artwork = it.artwork - key) }
        runCatching { backup.dataChanged() }
    }

    fun observe(playback: StateFlow<PlaybackSnapshot>, scope: CoroutineScope) {
        observation?.cancel()
        observation = scope.launch(Dispatchers.IO) {
            combine(
                playback.map { it.sourcePlaylistId to it.currentTrack }.distinctUntilChanged(),
                state.map { it.enabled }.distinctUntilChanged(),
                membershipRevision,
            ) { (id, track), enabled, _ -> Triple(id, track, enabled) }
                .collect { (id, track, enabled) ->
                    val keys = automaticCoverTargets(id, track, enabled) { key ->
                        preferences.getStringSet("tracks:$key", emptySet()).orEmpty()
                    }
                    val url = track?.artworkUrl
                        ?.let { OfflineArtwork.usableArtworkUrl(it, AUTOMATIC_COVER_PIXELS) }
                        ?.takeIf { it.isNotBlank() }
                        ?: return@collect
                    val changed = keys.filter { mutable.value.artwork[it] != url }
                    if (changed.isNotEmpty()) {
                        preferences.edit().apply { changed.forEach { putString("art:$it", url) } }.apply()
                        mutable.update { it.copy(artwork = it.artwork + changed.associateWith { url }) }
                    }
                }
        }
    }
}
