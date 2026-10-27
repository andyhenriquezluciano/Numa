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

package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.model.PlaybackTargetState
import dev.sfg.numa.mobile.model.RepeatMode
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.playback.ListeningPartyManager
import dev.sfg.numa.mobile.playback.LocalPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Transport and queue commands, routed to this phone or the listening-party host. */
internal class Transport(
    private val graph: NumaGraph,
    private val scope: CoroutineScope,
    private val local: LocalPlaybackController,
    private val party: ListeningPartyManager,
    private val playback: StateFlow<PlaybackSnapshot>,
    private val targets: StateFlow<PlaybackTargetState>,
    private val musicVideo: StateFlow<MusicVideoState>,
    private val showWarning: (String) -> Unit,
) {
    fun playNext(track: Track) { if (!party.interceptTransport("play-next")) scope.launch { local.playNext(track) } }
    fun addToQueue(track: Track) { if (!party.interceptTransport("add-queue")) scope.launch { local.addToQueue(track) } }
    fun togglePlayback() = partyOrLocal(
        if (playback.value.isPlaying) "pause" else "play", local::toggle,
    )

    fun toggleMusicVideo() {
        if (party.interceptTransport("video")) return
        if (targets.value.selected !is PlaybackTarget.LocalPhone) {
            showWarning("Music videos play on this device only.")
            return
        }
        val state = musicVideo.value
        val currentId = local.snapshot.value.currentTrack?.id
        if (state.trackId != currentId) return
        when {
            state.playing -> local.setVideoMode(null)
            state.videoId.isNotBlank() -> local.setVideoMode(state.videoId, videoMaxHeight())
            state.checking -> Unit
            else -> showWarning("No music video is available for this track.")
        }
    }

    /** Turns the video on once the lookup for [trackId] settles, unless another track took over. */
    fun showMusicVideoWhenReady(trackId: String) {
        if (targets.value.selected !is PlaybackTarget.LocalPhone) return
        scope.launch {
            val state = withTimeoutOrNull(VIDEO_LOOKUP_TIMEOUT_MS) {
                musicVideo.first { it.trackId == trackId && !it.checking }
            } ?: return@launch
            if (state.available && !state.playing && local.snapshot.value.currentTrack?.id == trackId)
                local.setVideoMode(state.videoId, videoMaxHeight())
        }
    }

    /** Saves the picture height and reloads a playing video at it, keeping the playhead. */
    fun setVideoMaxHeight(height: Int) {
        val settings = graph.settings.settings.value
        if (settings.videoMaxHeight != height) graph.settings.updateSettings(settings.copy(videoMaxHeight = height))
        val state = musicVideo.value
        if (state.playing && state.videoId.isNotBlank()) local.setVideoMode(state.videoId, height)
    }

    private fun videoMaxHeight() = graph.settings.settings.value.videoMaxHeight

    fun next() = partyOrLocal("next", local::next)
    fun previous() = partyOrLocal("previous", local::previous)
    fun seek(positionMs: Long) {
        if (!party.requestSeek(positionMs)) local.seek(positionMs)
    }
    fun toggleShuffle() = partyOrLocal("toggle-shuffle") { local.setShuffle(!playback.value.shuffle) }
    fun cycleRepeat() = partyOrLocal("cycle-repeat", local::cycleRepeat)
    fun playQueueIndex(index: Int) = partyOrLocal("play-track") { local.playQueueIndex(index) }
    fun removeQueueIndex(index: Int) = partyOrLocal("remove-queue") { local.remove(index) }
    fun moveQueueItem(from: Int, to: Int) = partyOrLocal("move-queue") { local.move(from, to) }
    fun clearUpcoming() = partyOrLocal("clear-queue", local::clearUpcoming)
    fun clearQueue() = partyOrLocal("clear-queue", local::clearQueue)

    private fun partyOrLocal(action: String, localAction: () -> Unit) {
        if (!party.interceptTransport(action)) localAction()
    }
}

/** Covers queue start plus the video lookup on a slow network. */
private const val VIDEO_LOOKUP_TIMEOUT_MS = 20_000L
