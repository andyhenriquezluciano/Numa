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

import androidx.compose.ui.graphics.Color

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
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
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.download.DownloadItem
import dev.sfg.numa.mobile.download.DownloadStatus
import dev.sfg.numa.mobile.model.Track
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.sfg.numa.mobile.ui.motion.pressScale
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.components.ArtworkTile
import dev.sfg.numa.mobile.ui.components.ExplicitBadge
import dev.sfg.numa.mobile.ui.components.MessagePanel
import dev.sfg.numa.mobile.ui.components.NumaChromeHeight
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    totalBytesUsed: Long,
    onPlay: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
) {
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED }
    val batch by dev.sfg.numa.mobile.NumaGraph.from(LocalContext.current).downloads.batchProgress.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NumaChromeHeight),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp)) {
                Text(
                    uiText("Downloads", "Descargas"),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = CanopyColors.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    uiText("Offline tracks • ${formatStorageSize(totalBytesUsed)} used", "Canciones sin conexión · ${formatStorageSize(totalBytesUsed)} utilizados"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CanopyColors.Muted,
                )
            }
        }

        if (active.isNotEmpty()) {
            item {
                Text(
                    uiText("${batch.completed} of ${batch.total} songs downloaded", "${batch.completed} de ${batch.total} canciones descargadas"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = LocalAccent.current,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(active, key = { "active_${it.track.id}" }) { item ->
                DownloadingRow(item = item, onCancel = { onRemoveDownload(item.track.id) }, modifier = Modifier.animateItem())
            }
        }

        if (completed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "DOWNLOADED (${completed.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CanopyColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(completed, key = { "completed_${it.track.id}" }) { item ->
                DownloadedTrackRow(
                    modifier = Modifier.animateItem().riseIn(cascadeOnScroll = true),
                    item = item,
                    onPlay = { onPlay(item.track) },
                    onDelete = { onRemoveDownload(item.track.id) },
                )
            }
        } else if (active.isEmpty() && failed.isEmpty()) {
            item {
                MessagePanel(
                    title = uiText("No downloaded tracks", "No hay canciones descargadas"),
                    message = uiText("Tap the '...' menu on any track to download it for offline listening.", "Toca el menú «…» de una canción para descargarla y escucharla sin conexión."),
                )
            }
        }

        if (failed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    uiText("FAILED (${failed.size})", "FALLIDAS (${failed.size})"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(failed, key = { "failed_${it.track.id}" }) { item ->
                DownloadingRow(item = item, onCancel = { onRemoveDownload(item.track.id) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
internal fun DownloadedTrackRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onPlay,
        color = androidx.compose.ui.graphics.Color.Transparent,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().pressScale(source, 0.97f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(item.track.artworkUrl, item.track.title, Modifier.size(48.dp), 8)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CanopyColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.track.explicit) {
                        Spacer(Modifier.width(6.dp))
                        ExplicitBadge()
                    }
                }
                Text(
                    "${item.track.artist} • ${formatStorageSize(item.bytesDownloaded)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CanopyColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = uiText("Play offline", "Reproducir sin conexión"), tint = CanopyColors.Text)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = uiText("Delete download", "Eliminar descarga"), tint = CanopyColors.Muted)
            }
        }
    }
}

@Composable
internal fun DownloadingRow(
    item: DownloadItem,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Progress arrives in coarse chunks; glide between them.
    val progress by animateFloatAsState(item.progress, tween(450), label = "DownloadProgress")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(item.track.artworkUrl, item.track.title, Modifier.size(48.dp), 8)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CanopyColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.track.explicit) {
                    Spacer(Modifier.width(6.dp))
                    ExplicitBadge()
                }
            }
            Spacer(Modifier.height(4.dp))
            if (item.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else if (item.status == DownloadStatus.FAILED) {
                Text(
                    item.errorMessage.ifBlank { uiText("Download failed", "La descarga falló") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            } else {
                Text(
                    "Queued...",
                    style = MaterialTheme.typography.bodySmall,
                    color = CanopyColors.Muted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Delete, contentDescription = uiText("Cancel download", "Cancelar descarga"), tint = CanopyColors.Muted)
        }
    }
}

internal fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) {
        String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024)
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    }
}
