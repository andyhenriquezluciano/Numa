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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.PlaybackTargetState
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.TransitionMarker
import dev.sfg.numa.mobile.ui.components.AnimatedArtworkVideo
import dev.sfg.numa.mobile.ui.components.RemoteArtwork

/** Two-column layout used when the screen is wider than 600dp (tablets, foldables unfolded, landscape). */
@Composable
fun TabletPlayerBody(
    track: Track,
    playback: PlaybackSnapshot,
    targets: PlaybackTargetState,
    lyrics: LoadState<List<dev.sfg.numa.mobile.model.LyricLine>>,
    /** Sampled by the caller from the full-bleed backdrop so both agree on the colour. */
    lyricAccent: Color,
    /** The framed cover's bounds; on a tablet this, not the backdrop, is the cover you see. */
    onCoverBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    transition: TransitionMarker?,
    mixProgress: Float? = null,
    canControl: Boolean,
    localControls: Boolean,
    liked: Boolean,
    panel: PlayerPanel,
    animatedArtworkEnabled: Boolean,
    showBitrate: Boolean,
    bitrateKbps: Int,
    isQobuz: Boolean = false,
    remoteVolume: Float,
    modifier: Modifier,
    onRemoteVolumeChange: (Float) -> Unit,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLiked: () -> Unit,
    onDevices: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    downloadedTrackIds: Set<String> = emptySet(),
    onDownloadTrack: ((Track) -> Unit)? = null,
    onRemoveDownloadTrack: ((String) -> Unit)? = null,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onShare: (() -> Unit)?,
    onOpenCollection: ((String) -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onLyricsPanel: () -> Unit,
    onQueuePanel: () -> Unit,
    sleepTimerRemainingSeconds: Long = 0L,
    sleepTimerEndOfTrack: Boolean = false,
    onSleepTimer: () -> Unit = {},
    autoplayEnabled: Boolean = true,
    autoplayLoading: Boolean = false,
    autoplayError: String = "",
    onAutoplayEnabled: ((Boolean) -> Unit)? = null,
    smartCrossfade: Boolean = false,
    onBestMixUpcoming: ((onProgress: (String) -> Unit, onComplete: () -> Unit) -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
    val compact = maxHeight < 480.dp
    val coverSize = minOf((maxHeight - 64.dp).coerceAtLeast(48.dp), maxWidth * 0.42f)
    Column(Modifier.fillMaxSize()) {
        PlayerTopHandle(onDismiss = onBack, modifier = modifier)
        val activeProgress = mixProgress ?: 0f
        val incomingTrack = remember(playback.queue, transition?.incomingTrackId) {
            val id = transition?.incomingTrackId
            if (id.isNullOrBlank()) null else playback.queue.firstOrNull { it.id == id }
        }
        val outgoingTrack = remember(playback.queue, transition?.trackId, track) {
            val id = transition?.trackId
            if (id.isNullOrBlank()) track else playback.queue.firstOrNull { it.id == id } ?: track
        }

        val panelContent: @Composable () -> Unit = {
                        if (panel == PlayerPanel.QUEUE) {
                            PlayerQueuePanel(
                                playback = playback,
                                compact = compact,
                                editable = canControl,
                                onPlayIndex = onPlayQueueIndex,
                                onRemove = onRemoveQueueIndex,
                                onMove = onMoveQueueItem,
                                onClearUpcoming = onClearUpcoming,
                                onShuffleUpcoming = onShuffle,
                                onShuffle = onShuffle,
                                onRepeat = onRepeat,
                                autoplayEnabled = autoplayEnabled,
                                autoplayLoading = autoplayLoading,
                                autoplayError = autoplayError,
                                onAutoplayEnabled = onAutoplayEnabled,
                                smartCrossfade = smartCrossfade,
                                onBestMixUpcoming = onBestMixUpcoming,
                                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                                sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                                onSleepTimer = onSleepTimer,
                            )
                        } else {
                            when (lyrics) {
                                is LoadState.Content -> LyricLines(
                                    lines = lyrics.value,
                                    songId = track.id,
                                    playing = playback.isPlaying,
                                    onSeek = onSeek,
                                    contentPadding = PaddingValues(vertical = if (compact) 8.dp else 24.dp),
                                    accent = lyricAccent,
                                )

                                LoadState.Loading -> LyricsNotice(uiText("Finding lyrics…", "Buscando letras…"), isLoading = true)
                                is LoadState.Empty -> LyricsNotice(lyrics.message, icon = Icons.Rounded.MusicNote)
                                is LoadState.Error -> LyricsNotice(lyrics.message, icon = Icons.Rounded.Info)
                                LoadState.Idle -> LyricsNotice(uiText("Start a song to see its lyrics.", "Reproduce una canción para ver su letra."), icon = Icons.Rounded.MusicNote)
                            }
                        }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The cover is framed rather than full-bleed. At this size the
            // blurred backdrop behind it reads as the room, and the square reads
            // as the record.
            Box(
                modifier = Modifier
                    .then(if (compact && panel != PlayerPanel.NONE) Modifier.weight(1f).fillMaxHeight() else Modifier.size(coverSize)),
                contentAlignment = Alignment.Center,
            ) {
                if (compact && panel != PlayerPanel.NONE) panelContent() else {
                NowPlayingArtworkCard(
                    track = track,
                    incomingTrack = incomingTrack,
                    outgoingTrack = outgoingTrack,
                    transitionProgress = activeProgress,
                    transitionStyle = transition?.style.orEmpty(),
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    isPlaying = playback.isPlaying,
                    onArtworkBounds = onCoverBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                }
            }

            Spacer(Modifier.width(if (compact) 24.dp else 40.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .then(if (panel == PlayerPanel.NONE || compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.Center,
            ) {
                dev.sfg.numa.mobile.ui.components.SmartCrossfadeBadge(
                    visible = activeProgress in 0.001f..0.999f && transition != null,
                    style = transition?.style.orEmpty(),
                    incomingTrack = incomingTrack,
                    progress = activeProgress,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TrackInfoRow(
                    track = track,
                    liked = liked,
                    onLiked = onLiked,
                    onMore = onQueuePanel,
                    onShare = onShare,
                    isDownloaded = downloadedTrackIds.contains(track.id),
                    onDownload = onDownloadTrack?.let { action -> { action(track) } },
                    onRemoveDownload = onRemoveDownloadTrack?.let { action -> { action(track.id) } },
                    onAddToPlaylist = onAddToPlaylist?.let { action -> { action(track) } },
                    onOpenAlbum = track.albumId.takeIf { it.isNotBlank() }
                        ?.let { id -> onOpenCollection?.let { open -> { open(id) } } },
                    onOpenArtist = onOpenArtist,
                )

                if (panel != PlayerPanel.NONE && !compact) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { panelContent() }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
                }

                PlayerControlStack(
                    compact = compact,
                    playback = playback,
                    targets = targets,
                    canControl = canControl,
                    localControls = localControls,
                    transition = transition,
                    mixProgress = mixProgress,
                    showBitrate = showBitrate,
                    bitrateKbps = bitrateKbps,
                    isQobuz = isQobuz,
                    remoteVolume = remoteVolume,
                    lyricsActive = panel == PlayerPanel.LYRICS,
                    queueActive = panel == PlayerPanel.QUEUE,
                    onRemoteVolumeChange = onRemoteVolumeChange,
                    onSeek = onSeek,
                    onToggle = onToggle,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onLyrics = onLyricsPanel,
                    onDevices = onDevices,
                    onQueue = onQueuePanel,
                    sleepTimerActive = sleepTimerRemainingSeconds > 0 || sleepTimerEndOfTrack,
                    onSleepTimer = onSleepTimer,
                    gap = { Spacer(Modifier.height(if (compact) 8.dp else it)) },
                )
            }
        }
    }
    }
}

