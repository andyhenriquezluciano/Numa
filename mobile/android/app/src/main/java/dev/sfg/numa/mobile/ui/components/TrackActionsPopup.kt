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

package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import dev.sfg.numa.mobile.ui.glass.GlassBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import dev.sfg.numa.mobile.ui.glass.desktopMenuHighlight
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.Track
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import dev.sfg.numa.mobile.ui.motion.pressScale
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent

/** Bottom sheet popup shown when tapping the "..." button on a track row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionsPopup(
    track: Track,
    onDismiss: () -> Unit,
    // Null where the action does not apply; the now playing player already plays this track,
    // so it offers no "Play Now" but does offer the queue.
    onPlay: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    removeFromPlaylistLabel: String? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onViewQueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
) {
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = CanopyColors.Muted.copy(alpha = 0.4f)) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            // Track preview header
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkTile(track.artworkUrl, track.title, Modifier.size(52.dp), 10)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CanopyColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (track.explicit) {
                            Spacer(Modifier.width(6.dp))
                            ExplicitBadge()
                        }
                    }
                    if (track.artist.isNotBlank()) {
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CanopyColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(CanopyColors.Rule))
            Spacer(Modifier.height(6.dp))

            // Action items cascade in as the sheet rises; slot counts only the rows shown.
            var slot = 0
            onPlay?.let { action ->
                PopupActionRow(Icons.Rounded.PlayArrow, uiText("Play Now", "Reproducir ahora"), slot++) { onDismiss(); action() }
            }
            onPlayNext?.let { action ->
                PopupActionRow(Icons.Rounded.SkipNext, uiText("Play Next", "Reproducir a continuación"), slot++) { onDismiss(); action() }
            }
            onAddToQueue?.let { action ->
                PopupActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, uiText("Add to Queue", "Añadir a la cola"), slot++) { onDismiss(); action() }
            }
            onAddToPlaylist?.let { action ->
                PopupActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, uiText("Add to Playlist", "Añadir a una lista"), slot++) { onDismiss(); action() }
            }
            onRemoveFromPlaylist?.let { action ->
                PopupActionRow(Icons.Rounded.Delete, removeFromPlaylistLabel ?: androidx.compose.ui.res.stringResource(dev.sfg.numa.connect.R.string.qol_remove_from_playlist), slot++) { onDismiss(); action() }
            }
            onMoveUp?.let { action ->
                PopupActionRow(Icons.Rounded.KeyboardArrowUp, uiText("Move Up", "Mover hacia arriba"), slot++) { onDismiss(); action() }
            }
            onMoveDown?.let { action ->
                PopupActionRow(Icons.Rounded.KeyboardArrowDown, uiText("Move Down", "Mover hacia abajo"), slot++) { onDismiss(); action() }
            }
            onViewQueue?.let { action ->
                PopupActionRow(Icons.AutoMirrored.Rounded.List, uiText("View Queue", "Ver cola"), slot++) { onDismiss(); action() }
            }
            onDownload?.takeUnless { track.isLocal }?.let { action ->
                PopupActionRow(Icons.Rounded.Download, uiText("Download Offline", "Descargar para escuchar sin conexión"), slot++) { onDismiss(); action() }
            }
            onRemoveDownload?.let { action ->
                PopupActionRow(Icons.Rounded.Delete, uiText("Remove Download", "Eliminar descarga"), slot++) { onDismiss(); action() }
            }
            onViewAlbum?.let { action ->
                if (track.albumId.isNotBlank()) {
                    PopupActionRow(Icons.Rounded.Album, uiText("View Album", "Ver álbum"), slot++) { onDismiss(); action() }
                }
            }
            onViewArtist?.let { action ->
                PopupActionRow(Icons.Rounded.Person, uiText("View Artist", "Ver artista"), slot++) { onDismiss(); action() }
            }
            onShare?.takeUnless { track.isLocal }?.let { action ->
                PopupActionRow(Icons.Rounded.Share, uiText("Share Song", "Compartir canción"), slot++) { onDismiss(); action() }
            }
            LocalLibraryUiLocal.current?.takeIf { track.isLocal }?.let { ui ->
                if (onRemoveFromPlaylist == null) PopupActionRow(Icons.Rounded.Delete, uiText("Remove from Library", "Quitar de la biblioteca"), slot++) {
                    onDismiss(); ui.repository.removeSong(track.id)
                }
            }
        }
    }
}

@Composable
private fun PopupActionRow(icon: ImageVector, label: String, index: Int, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        interactionSource = source,
        modifier = Modifier.fillMaxWidth().desktopMenuHighlight(pressed || hovered || focused),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = dev.sfg.numa.mobile.ui.glass.DesktopGlass.Text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = CanopyColors.Text,
            )
        }
    }
}
