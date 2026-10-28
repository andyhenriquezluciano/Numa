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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.RepeatMode

/**
 * The queue as a mode of the player rather than a destination: the transport below stays put and
 * the list takes the space the artwork was using, so reordering never loses sight of what is
 * playing. Styled for the artwork backdrop, so everything here is white-on-translucent.
 */
@Composable
fun PlayerQueuePanel(
    playback: PlaybackSnapshot,
    editable: Boolean,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onShuffleUpcoming: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    autoplayEnabled: Boolean = true,
    autoplayLoading: Boolean = false,
    autoplayError: String = "",
    onAutoplayEnabled: ((Boolean) -> Unit)? = null,
    smartCrossfade: Boolean = false,
    onBestMixUpcoming: ((onProgress: (String) -> Unit, onComplete: () -> Unit) -> Unit)? = null,
    sleepTimerRemainingSeconds: Long = 0L,
    sleepTimerEndOfTrack: Boolean = false,
    onSleepTimer: () -> Unit = {},
) {
    val history = playback.history.takeLast(4)
    val historyStart = playback.currentIndex - history.size
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember { if (compact && history.isNotEmpty()) history.size + 2 else 0 },
    )
    val drag = rememberQueueDrag(listState, playback.queue.map { it.id }, playback.currentIndex, editable, onMove)
    if (playback.queue.isEmpty()) {
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                QueueTopControls(
                    compact = compact,
                    shuffle = playback.shuffle,
                    repeatMode = playback.repeatMode,
                    onShuffle = onShuffle?.takeIf { editable },
                    onRepeat = onRepeat?.takeIf { editable },
                    autoplayEnabled = autoplayEnabled,
                    autoplayLoading = autoplayLoading,
                    autoplayError = autoplayError,
                    onAutoplayEnabled = onAutoplayEnabled,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                    onSleepTimer = onSleepTimer,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QueueNotice(uiText("Queue is empty", "La cola está vacía"), uiText("Play an album, playlist, or song to get started.", "Reproduce un álbum, una lista o una canción para empezar."))
                }
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().then(drag.modifier),
        userScrollEnabled = !drag.active,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        item {
            QueueTopControls(
                compact = compact,
                shuffle = playback.shuffle,
                repeatMode = playback.repeatMode,
                onShuffle = onShuffle?.takeIf { editable },
                onRepeat = onRepeat?.takeIf { editable },
                autoplayEnabled = autoplayEnabled,
                autoplayLoading = autoplayLoading,
                autoplayError = autoplayError,
                onAutoplayEnabled = onAutoplayEnabled,
                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                onSleepTimer = onSleepTimer,
            )
        }
        if (history.isNotEmpty()) {
            item { QueueSectionHeader(uiText("Played", "Reproducida")) }
            itemsIndexed(history, key = { index, track -> "history:$index:${track.id}" }) { offset, track ->
                val index = historyStart + offset
                QueueTrackRow(
                    track = track, index = index, queueSize = playback.queue.size,
                    editable = false, isHistory = true,
                    onPlay = { onPlayIndex(index) }, onRemove = onRemove, onMove = onMove,
                    modifier = Modifier.riseIn(offset, cascadeOnScroll = true),
                )
            }
        }
        item {
            QueueSectionHeader(
                title = uiText("Playing next", "A continuación"),
                trailing = if (playback.upcoming.isNotEmpty() && editable) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            onShuffleUpcoming?.let { QueueShuffleButton(it, playback.shuffle) }
                            if (playback.upcoming.size > 1 && onBestMixUpcoming != null) {
                                QueueBestMixButton(onBestMixUpcoming)
                                Spacer(Modifier.width(4.dp))
                            }
                            QueueClearButton(onClearUpcoming)
                        }
                    }
                } else {
                    null
                },
            )
        }
        item {
            dev.sfg.numa.mobile.model.LocalBestMixJob.current?.takeIf { it.key == dev.sfg.numa.mobile.model.BestMixJob.QUEUE_KEY }?.let {
                Text(it.status, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                Text(uiText("Analyzing available audio. Offline, the queue stays unchanged. Tap × to cancel.", "Se analiza el audio disponible. Si falta conexión, se conserva la cola. Pulsa × para cancelar."),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        if (playback.upcoming.isEmpty()) {
            item { QueueNotice(uiText("End of the queue", "Al terminar la cola"), uiText("Add more music from Search or Library.", "Añade más música desde Buscar o Biblioteca.")) }
        }
        itemsIndexed(playback.upcoming, key = { index, track -> "upcoming:$index:${track.id}" }) { offset, track ->
            val index = playback.currentIndex + 1 + offset
            QueueTrackRow(
                track = track, index = index, queueSize = playback.queue.size,
                editable = editable, isHistory = false,
                onPlay = { onPlayIndex(index) }, onRemove = onRemove, onMove = onMove,
                modifier = Modifier.then(drag.row("upcoming:$offset:${track.id}")),
            )
        }
    }
}

/** Tiles for the queue's standing modes: shuffle and repeat, then sleep timer and autoplay. */
@Composable
fun QueueTopControls(
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onShuffle: (() -> Unit)?,
    onRepeat: (() -> Unit)?,
    autoplayEnabled: Boolean,
    autoplayLoading: Boolean,
    autoplayError: String,
    onAutoplayEnabled: ((Boolean) -> Unit)?,
    sleepTimerRemainingSeconds: Long,
    sleepTimerEndOfTrack: Boolean,
    onSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        CompactQueueModes(repeatMode, onRepeat, autoplayEnabled, onAutoplayEnabled)
        return
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onRepeat != null) QueueModeTile(
            icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            title = uiText("Repeat", "Repetir"),
            status = when (repeatMode) {
                RepeatMode.OFF -> uiText("Off", "Desactivado")
                RepeatMode.ALL -> uiText("All", "Todo")
                RepeatMode.ONE -> uiText("One", "Una")
            },
            active = repeatMode != RepeatMode.OFF, tint = RepeatTint,
            onClick = onRepeat, modifier = Modifier.weight(1f),
        )
        if (onAutoplayEnabled != null) QueueModeTile(
            icon = Icons.Rounded.AllInclusive,
            title = uiText("Autoplay", "Reproducción automática"),
            status = when {
                !autoplayEnabled -> uiText("Off", "Desactivado")
                autoplayLoading -> uiText("Finding songs…", "Buscando canciones…")
                autoplayError.isNotBlank() -> autoplayError
                else -> uiText("On", "Activado")
            },
            active = autoplayEnabled, tint = AutoplayTint,
            onClick = { onAutoplayEnabled(!autoplayEnabled) }, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueueModeTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = if (active) 0.16f else 0.07f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(68.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (active) tint.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) tint else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) tint else Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val AutoplayTint = Color(0xFF80DEEA)
private val RepeatTint = Color(0xFFFFCC80)
