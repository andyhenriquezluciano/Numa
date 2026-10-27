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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.playback.MediaItemMapper
import dev.sfg.numa.mobile.playback.sourcePositionMs
import dev.sfg.numa.mobile.playback.sourceDurationMs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Player reads and session changes stay on Main; bitmap work runs on IO. */
@androidx.annotation.OptIn(UnstableApi::class)
object NumaWidgetUpdater {
    private val renderMutex = Mutex()
    private var source: Player? = null
    @Volatile private var state = NumaWidgetState()
    private var lines = emptyList<LyricLine>()
    private var lyricsJob: Job? = null
    private var ticker: Job? = null
    private var renderJob: Job? = null
    @Volatile private var revision = 0L

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(NumaPlayerWidgetProvider::class.java, NumaRecentWidgetProvider::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun onPlayerChanged(context: Context, player: Player, forcePaused: Boolean = false) {
        val app = context.applicationContext
        val graph = NumaGraph.from(app)
        source = if (forcePaused) null else player
        if (!hasWidgets(app)) { lyricsJob?.cancel(); lyricsJob = null; ticker?.cancel(); ticker = null; renderJob?.cancel(); return }
        val current = player.currentMediaItem?.let(MediaItemMapper::toTrack)?.let {
            it.copy(artworkUrl = graph.downloads.artworkUri(it), durationMs = player.sourceDurationMs().takeIf { duration -> duration > 0 } ?: it.durationMs)
        }
        val changedTrack = state.currentTrack?.id != current?.id || state.currentTrack?.durationMs != current?.durationMs
        if (player.isPlaying && !state.isPlaying && lines.isEmpty() && lyricsJob?.isActive != true) lyricsJob = null
        if (changedTrack) { lyricsJob?.cancel(); lines = emptyList() }
        val position = player.sourcePositionMs().coerceAtLeast(0)
        val lyric = widgetLyricLines(lines, position)
        state = NumaWidgetState(current, graph.library.library.value.recentlyPlayed.take(4),
            player.isPlaying && !forcePaused, position, player.sourceDurationMs().coerceAtLeast(0),
            lyric.first, lyric.second, lyricsLoading = current != null && (changedTrack || lyricsJob?.isActive == true))
        publish(app)
        if (current != null && (changedTrack || lyricsJob == null)) {
            lyricsJob = graph.applicationScope.launch(Dispatchers.Main.immediate) {
                val loaded = try { withContext(Dispatchers.IO) { if (current.isLocal) dev.sfg.numa.mobile.local.localPlaybackLyrics(graph, current) else graph.lyrics.lyrics(current) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                if (state.currentTrack?.id != current.id) return@launch
                lines = loaded
                val text = widgetLyricLines(lines, state.positionMs)
                state = state.copy(currentLyric = text.first, upcomingLyric = text.second, lyricsLoading = false)
                publish(app)
            }
        }
        if (!state.isPlaying) { ticker?.cancel(); ticker = null }
        else if (ticker?.isActive != true) ticker = graph.applicationScope.launch(Dispatchers.Main.immediate) {
            val power = app.getSystemService(PowerManager::class.java)
            var ticks = 0
            while (isActive) {
                delay(500)
                if (++ticks % 20 == 0 && !hasWidgets(app)) break
                val active = source ?: break
                if (active.currentMediaItem?.mediaId != state.currentTrack?.id) continue
                if (!power.isInteractive || !active.isPlaying) continue
                val positionMs = active.sourcePositionMs().coerceAtLeast(0)
                val text = widgetLyricLines(lines, positionMs)
                val next = state.copy(positionMs = positionMs, currentLyric = text.first, upcomingLyric = text.second)
                val refresh = next.currentLyric != state.currentLyric || next.upcomingLyric != state.upcomingLyric ||
                    positionMs / 2000 != state.positionMs / 2000
                state = next
                if (refresh) {
                    val expected = revision
                    graph.applicationScope.launch(Dispatchers.IO) {
                        renderMutex.withLock {
                            if (expected != revision) return@withLock
                            NumaWidgetStateStore(app).save(next)
                            NumaWidgetRenderer.updateProgressAndLyrics(app, next)
                        }
                    }
                }
            }
        }
    }

    private fun publish(context: Context) {
        val graph = NumaGraph.from(context)
        val snapshot = state
        val expected = ++revision
        renderJob?.cancel()
        renderJob = graph.applicationScope.launch(Dispatchers.IO) {
            renderMutex.withLock {
                if (revision != expected) return@withLock
                NumaWidgetStateStore(context).save(snapshot)
                NumaWidgetRenderer.updateAll(context, snapshot, graph.http)
            }
        }
    }

    fun requestRender(context: Context) {
        val app = context.applicationContext
        NumaGraph.from(app).applicationScope.launch(Dispatchers.Main.immediate) {
            if (!hasWidgets(app)) { ticker?.cancel(); lyricsJob?.cancel(); renderJob?.cancel(); return@launch }
            source?.let { onPlayerChanged(app, it); return@launch }
            state = NumaWidgetStateStore(app).load().copy(isPlaying = false, lyricsLoading = false)
            publish(app)
        }
    }
}
