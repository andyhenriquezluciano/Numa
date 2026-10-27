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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.download.DownloadItem
import dev.sfg.numa.mobile.download.DownloadStatus
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.components.CatalogCard
import dev.sfg.numa.mobile.ui.components.TrackRow

/** Empty shelf with an optional jump to the new releases page. */
private fun LazyListScope.emptyShelf(
    icon: ImageVector,
    title: String,
    message: String,
    onBrowse: (() -> Unit)?,
) {
    item { LibraryEmpty(icon, title, message, onBrowse?.let { uiText("Browse new releases", "Explorar novedades") }, onBrowse ?: {}) }
}

internal fun LazyListScope.downloadsList(
    downloads: List<DownloadItem>,
    totalBytesUsed: Long,
    onPlay: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
) {
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED }

    if (active.isEmpty() && completed.isEmpty() && failed.isEmpty()) {
        emptyShelf(
            Icons.Rounded.Download,
            uiText("No downloaded tracks", "No hay canciones descargadas"),
            uiText("Tap the download icon on any track, album, or playlist to listen offline.", "Toca el icono de descarga de una canción, álbum o lista para escuchar sin conexión."),
            null,
        )
        return
    }

    if (active.isNotEmpty()) {
        item { LibraryLabel("Downloading (${active.size})") }
        itemsIndexed(active, key = { _, it -> "active_${it.track.id}" }) { index, item ->
            DownloadingRow(
                item = item,
                onCancel = { onRemoveDownload(item.track.id) },
                modifier = Modifier.animateItem().panelSegment(index == 0, index == active.lastIndex),
            )
        }
    }

    if (completed.isNotEmpty()) {
        item { LibraryLabel(uiText("Downloaded (${completed.size}) \u00b7 ${formatStorageSize(totalBytesUsed)} used", "Descargadas (${completed.size}) · ${formatStorageSize(totalBytesUsed)} utilizados")) }
        itemsIndexed(completed, key = { _, it -> "completed_${it.track.id}" }) { index, item ->
            DownloadedTrackRow(
                modifier = Modifier.animateItem().panelSegment(index == 0, index == completed.lastIndex),
                item = item,
                onPlay = { onPlay(item.track) },
                onDelete = { onRemoveDownload(item.track.id) },
            )
        }
    }

    if (failed.isNotEmpty()) {
        item { LibraryLabel(uiText("Failed (${failed.size})", "Fallidas (${failed.size})"), MaterialTheme.colorScheme.error) }
        itemsIndexed(failed, key = { _, it -> "failed_${it.track.id}" }) { index, item ->
            DownloadingRow(
                item = item,
                onCancel = { onRemoveDownload(item.track.id) },
                modifier = Modifier.animateItem().panelSegment(index == 0, index == failed.lastIndex),
            )
        }
    }
}

internal fun LazyListScope.tracks(
    title: String,
    values: List<Track>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    downloadedTrackIds: Set<String>,
    downloadingTrackIds: Set<String> = emptySet(),
    onPlay: (Track) -> Unit,
    onPlayNext: ((Track) -> Unit)?,
    onAdd: ((Track) -> Unit)?,
    onDownloadTrack: ((Track) -> Unit)? = null,
    onRemoveDownloadTrack: ((String) -> Unit)? = null,
    onShare: ((Track) -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null,
    onBrowse: (() -> Unit)? = null,
) {
    if (values.isEmpty()) {
        emptyShelf(emptyIcon, emptyTitle, emptyMessage, onBrowse)
        return
    }
    item { LibraryLabel(title) }
    itemsIndexed(values, key = { index, track -> "${track.id}_$index" }) { index, track ->
        TrackRow(
            track = track,
            onPlay = { onPlay(track) },
            modifier = Modifier
                .animateItem(fadeInSpec = null)
                .riseIn(index, cascadeOnScroll = true)
                .padding(horizontal = 8.dp),
            onPlayNext = onPlayNext?.let { action -> { action(track) } },
            onAddToQueue = onAdd?.let { action -> { action(track) } },
            onDownload = onDownloadTrack?.let { action -> { action(track) } },
            onRemoveDownload = onRemoveDownloadTrack?.let { action -> { action(track.id) } },
            isDownloaded = downloadedTrackIds.contains(track.id),
            isDownloading = downloadingTrackIds.contains(track.id),
            onShare = onShare?.let { action -> { action(track) } },
            onViewAlbum = onOpen?.takeIf { track.albumId.isNotBlank() }?.let { nav -> { nav(track.albumId) } },
            onViewArtist = onOpen?.takeIf { track.artistId.isNotBlank() }?.let { nav -> { nav(track.artistId) } },
        )
    }
}

internal fun LazyListScope.collections(
    title: String,
    values: List<CatalogItem>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    onBrowse: (() -> Unit)? = null,
    columns: Int = 2,
) {
    if (values.isEmpty()) {
        emptyShelf(emptyIcon, emptyTitle, emptyMessage, onBrowse)
        return
    }

    item { LibraryLabel(title) }

    val columnCount = columns.coerceAtLeast(1)
    val rows = values.chunked(columnCount)

    itemsIndexed(
        items = rows,
        key = { _, row -> row.joinToString("|") { item -> item.stableId } },
    ) { index, row ->
        Row(
            modifier = Modifier
                .animateItem(fadeInSpec = null)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            row.forEachIndexed { col, catalogItem ->
                Box(Modifier.weight(1f).riseIn(col, fromScale = 0.9f, cascadeOnScroll = true)) {
                    CatalogCard(catalogItem, { onOpen(catalogItem.stableId) }, modifier = Modifier.fillMaxWidth())
                }
            }
            repeat(columnCount - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

internal val LibraryFilter.label: String
    get() = when (this) {
        LibraryFilter.PLAYLISTS -> uiText("Playlists", "Listas de reproducción")
        LibraryFilter.ARTISTS -> uiText("Artists", "Artistas")
        LibraryFilter.ALBUMS -> uiText("Albums", "Álbumes")
        LibraryFilter.SONGS -> uiText("Songs", "Canciones")
        LibraryFilter.RECENT -> uiText("Recent", "Recientes")
        LibraryFilter.DOWNLOADS -> uiText("Downloads", "Descargas")
        LibraryFilter.LOCAL -> "Local"
        LibraryFilter.METRICS -> uiText("Metrics", "Métricas")
    }
