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
import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.Artist
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.model.PlaybackTargetState
import dev.sfg.numa.mobile.playback.LocalPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

data class MusicVideoState(
    val trackId: String = "",
    val videoId: String = "",
    val checking: Boolean = false,
    val playing: Boolean = false,
    /** Height of the picture playing, 0 until the stream resolves. */
    val height: Int = 0,
    val heights: List<Int> = emptyList(),
    /** The saved ceiling; 0 takes the best available. */
    val maxHeight: Int = 0,
) {
    val available: Boolean get() = videoId.isNotBlank()
}

/** What the player shows beside the audio: artwork, artist credits, lyrics and the music video. */
internal class NowPlayingMetadata(
    private val graph: NumaGraph,
    private val scope: CoroutineScope,
    private val local: LocalPlaybackController,
) {
    private val mutableArtwork = MutableStateFlow<TrackArtwork?>(null)
    val artwork: StateFlow<TrackArtwork?> = mutableArtwork.asStateFlow()
    private val mutableArtistCredits = MutableStateFlow<Pair<String, List<Artist>>?>(null)
    val artistCredits: StateFlow<Pair<String, List<Artist>>?> = mutableArtistCredits.asStateFlow()
    private val mutableLyrics = MutableStateFlow<LoadState<List<LyricLine>>>(LoadState.Idle)
    val lyrics: StateFlow<LoadState<List<LyricLine>>> = mutableLyrics.asStateFlow()
    private val mutableMusicVideo = MutableStateFlow(MusicVideoState())
    val musicVideo: StateFlow<MusicVideoState> = mutableMusicVideo.asStateFlow()

    fun observeArtwork(targetPlayback: Flow<PlaybackSnapshot>) {
        scope.launch {
            combine(
                targetPlayback.map { it.currentTrack },
                graph.downloads.downloads,
            ) { track, downloaded -> track to track?.id?.let(downloaded::get) }
                .distinctUntilChanged { old, new ->
                    old.first?.id == new.first?.id &&
                        old.second?.cachedAnimatedArtworkUrl == new.second?.cachedAnimatedArtworkUrl &&
                        old.second?.track?.artworkUrl == new.second?.track?.artworkUrl &&
                        old.second?.cachedAnimatedArtworkVerticalUrl == new.second?.cachedAnimatedArtworkVerticalUrl
                }
                .collectLatest { (track, downloaded) ->
                    mutableArtwork.value = when {
                        track == null -> null
                        // The user's own cover and loop, never an online lookup.
                        track.isLocal -> TrackArtwork(track.id, track.artworkUrl, track.animatedArtworkUrl)
                        downloaded != null && (
                            downloaded.cachedAnimatedArtworkUrl.isNotBlank() ||
                                downloaded.cachedAnimatedArtworkVerticalUrl.isNotBlank()
                        ) -> TrackArtwork(
                            track.id,
                            graph.downloads.artworkUri(track),
                            downloaded.cachedAnimatedArtworkUrl,
                            downloaded.cachedAnimatedArtworkVerticalUrl,
                        )
                        track.animatedArtworkVerticalUrl.isNotBlank() || track.animatedArtworkUrl.isNotBlank() ->
                            TrackArtwork(track.id, dev.sfg.numa.mobile.download.OfflineArtwork.usableArtworkUrl(track.artworkUrl), track.animatedArtworkUrl, track.animatedArtworkVerticalUrl)
                        else -> {
                            val fallback = TrackArtwork(track.id, graph.downloads.artworkUri(track))
                            mutableArtwork.value = fallback
                            delay(250)
                            val resolved = graph.artwork.artwork(track)
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val resolvedStatic = dev.sfg.numa.mobile.download.OfflineArtwork
                                .usableArtworkUrl(resolved?.staticUrl.orEmpty())
                            if (!fallback.staticUrl.startsWith("file:") && resolvedStatic.isNotBlank()) {
                                graph.downloads.refreshTrackArtwork(listOf(track.copy(artworkUrl = resolvedStatic)))
                            }
                            val staticUrl = fallback.staticUrl.takeIf { it.startsWith("file:") }
                                ?: resolvedStatic.ifBlank { fallback.staticUrl }
                            resolved?.copy(staticUrl = staticUrl) ?: fallback.copy(staticUrl = staticUrl)


                        }
                    }
                }
        }
    }

    fun observeArtistCredits(targetPlayback: Flow<PlaybackSnapshot>) {
        scope.launch {
            targetPlayback.map { it.currentTrack }.distinctUntilChanged { old, new -> old?.id == new?.id }
                .collectLatest { track ->
                    if (track == null) {
                        mutableArtistCredits.value = null
                        return@collectLatest
                    }
                    val existing = track.artists
                        .filter { it.id.isNotBlank() }
                        .distinctBy { it.id }
                    mutableArtistCredits.value = track.id to existing
                    if (!track.playbackSource.equals("youtube", ignoreCase = true) || existing.size > 1) {
                        return@collectLatest
                    }
                    delay(250)
                    val resolved = try { graph.catalog.trackArtists(track.id) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (resolved.isNotEmpty()) mutableArtistCredits.value = track.id to resolved
                }
        }
    }

    fun observeLyrics(playback: Flow<PlaybackSnapshot>) {
        scope.launch {
            combine(playback, graph.translationConditions, graph.settings.settings) { snapshot, limits, settings ->
                snapshot.upcoming.firstOrNull()?.takeIf {
                    snapshot.isPlaying && snapshot.positionMs >= 2_000 && limits.allowPreparation &&
                        settings.translateLyrics && settings.lyricTranslationProvider == dev.sfg.numa.mobile.model.LyricTranslationProvider.LOCAL
                }
            }.distinctUntilChangedBy { it?.id }.collectLatest { next ->
                graph.lyricTranslation.setNextLyrics(null, "")
                if (next == null) return@collectLatest
                delay(300)
                try {
                    val lines = if (next.isLocal) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        graph.localLibrary.lyricsFor(next)
                    } else graph.lyrics.lyrics(next)
                    graph.lyricTranslation.setNextLyrics(lines, next.id)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* Preparation is optional; the current lyrics stay available. */ }
            }
        }
        scope.launch {
            combine(playback, graph.localLibrary.library, graph.streams.sources, graph.networkMonitor.isOnline) { snapshot, library, _, online ->
                val track = snapshot.currentTrack?.let {
                    if (snapshot.durationMs > 0) it.copy(durationMs = snapshot.durationMs) else it
                }
                val localLyrics = library.songsById[track?.id]?.let { Triple(it.lyricsPath, it.embeddedLyrics, it.automaticLyricsVersion) }
                Triple(track, localLyrics, track?.let { graph.lyrics.requestKey(it) } to online)
            }.distinctUntilChanged { old, new -> old.first?.id == new.first?.id && old.second == new.second && old.third == new.third }
                .collectLatest { (track, _, _) ->
                    graph.lyricTranslation.setPlaybackLyrics(null, track?.id.orEmpty())
                    if (track == null) {
                        mutableLyrics.value = LoadState.Idle
                        return@collectLatest
                    }
                    mutableLyrics.value = LoadState.Loading
                    if (!track.isLocal) delay(250)
                    // A song from this phone uses the user's own lyrics, and is never looked up online.
                    mutableLyrics.value = runCatching {
                        if (track.isLocal) dev.sfg.numa.mobile.local.localPlaybackLyrics(graph, track) else graph.lyrics.lyrics(track)
                    }
                        .fold(
                            onSuccess = {
                                graph.lyricTranslation.setPlaybackLyrics(it, track.id)
                                if (it.isEmpty()) LoadState.Empty("Lyrics are not available for this track.")
                                else LoadState.Content(it)
                            },
                            onFailure = {
                                if (it is kotlinx.coroutines.CancellationException) throw it
                                LoadState.Error(it.message ?: "Lyrics could not be loaded.")
                            },
                        )
                }
        }
    }

    fun observeMusicVideo(targets: Flow<PlaybackTargetState>) {
        scope.launch {
            combine(local.snapshot, targets) { snapshot, targetState ->
                snapshot.currentTrack.takeIf { targetState.selected is PlaybackTarget.LocalPhone }
            }
                .distinctUntilChangedBy { track ->
                    track?.let { Triple(it.id, it.musicVideoType, it.musicVideoId) }
                }
                .collectLatest { track ->
                    if (track == null || track.isQobuz || track.isLocal) {
                        mutableMusicVideo.value = MusicVideoState()
                        return@collectLatest
                    }
                    mutableMusicVideo.value = MusicVideoState(
                        trackId = track.id,
                        checking = true,
                        playing = local.snapshot.value.playingVideo,
                    )
                    delay(350)
                    val videoId = graph.videoVersions.videoId(track).orEmpty()
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    mutableMusicVideo.value = MusicVideoState(
                        trackId = track.id,
                        videoId = videoId,
                        playing = local.snapshot.value.playingVideo,
                    )
                }
        }
        scope.launch {
            combine(local.snapshot, targets) { snapshot, targetState ->
                snapshot.playingVideo && targetState.selected is PlaybackTarget.LocalPhone
            }.distinctUntilChanged().collect { playing ->
                mutableMusicVideo.update { it.copy(playing = playing) }
            }
        }
    }
}
