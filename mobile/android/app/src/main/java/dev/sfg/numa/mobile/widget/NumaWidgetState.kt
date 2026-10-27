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

package dev.sfg.numa.mobile.widget

import android.content.Context
import androidx.core.content.edit
import dev.sfg.numa.mobile.library.LibraryCache
import dev.sfg.numa.mobile.model.CatalogJson
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.playback.PlaybackStateStore
import org.json.JSONObject

internal data class NumaWidgetState(
    val currentTrack: Track? = null,
    val recentlyPlayed: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentLyric: String? = null,
    val upcomingLyric: String? = null,
    val lyricsLoading: Boolean = false,
)

/** Small durable projection so widgets remain useful when Android has stopped the player process. */
internal object NumaWidgetStateCodec {
    fun encode(state: NumaWidgetState): JSONObject = JSONObject()
        .put("version", 1)
        .put("currentTrack", state.currentTrack?.let(CatalogJson::track))
        .put("recentlyPlayed", CatalogJson.tracks(state.recentlyPlayed.take(4)))
        .put("isPlaying", state.isPlaying)
        .put("positionMs", state.positionMs.coerceAtLeast(0))
        .put("durationMs", state.durationMs.coerceAtLeast(0))
        .put("currentLyric", state.currentLyric)
        .put("upcomingLyric", state.upcomingLyric)
        .put("lyricsLoading", state.lyricsLoading)

    fun decode(root: JSONObject): NumaWidgetState = NumaWidgetState(
        currentTrack = root.optJSONObject("currentTrack")?.let(CatalogJson::track),
        recentlyPlayed = CatalogJson.tracks(root.optJSONArray("recentlyPlayed")).take(4),
        isPlaying = root.optBoolean("isPlaying"),
        positionMs = root.optLong("positionMs").coerceAtLeast(0),
        durationMs = root.optLong("durationMs").coerceAtLeast(0),
        currentLyric = root.optString("currentLyric").takeIf { it.isNotBlank() },
        upcomingLyric = root.optString("upcomingLyric").takeIf { it.isNotBlank() },
        lyricsLoading = root.optBoolean("lyricsLoading"),
    )
}

internal class NumaWidgetStateStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): NumaWidgetState {
        val stored = preferences.getString(SNAPSHOT, null)
        if (!stored.isNullOrBlank()) {
            runCatching { return NumaWidgetStateCodec.decode(JSONObject(stored)) }
        }
        return bootstrap()
    }

    fun save(state: NumaWidgetState) {
        preferences.edit { putString(SNAPSHOT, NumaWidgetStateCodec.encode(state).toString()) }
    }

    /** Uses the existing durable playback and library stores the first time a widget is added. */
    private fun bootstrap(): NumaWidgetState {
        val playback = PlaybackStateStore(context).load()
        val library = LibraryCache(context).load()
        val track = playback.queue.getOrNull(playback.currentIndex)
        return NumaWidgetState(
            currentTrack = track,
            recentlyPlayed = library.recentlyPlayed.take(4),
            durationMs = track?.durationMs ?: 0,
            positionMs = playback.positionMs,
        )
    }

    private companion object {
        const val PREFERENCES = "numa-widget-state"
        const val SNAPSHOT = "snapshot"
    }
}
