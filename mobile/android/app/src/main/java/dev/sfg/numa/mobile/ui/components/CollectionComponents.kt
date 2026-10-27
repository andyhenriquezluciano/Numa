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

import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenuItem

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.sfg.numa.mobile.model.Track

/**
 * Helper to produce the collection download / delete callback based on current download state.
 */
fun collectionDownloadAction(
    tracks: List<Track>,
    downloadedTrackIds: Set<String>,
    onDownloadTracks: ((List<Track>) -> Unit)?,
    onRemoveDownloadTracks: ((List<Track>) -> Unit)?,
): (() -> Unit)? {
    if (onDownloadTracks == null || onRemoveDownloadTracks == null || tracks.isEmpty()) {
        return null
    }
    val allDownloaded = tracks.all { downloadedTrackIds.contains(it.id) }
    return {
        if (allDownloaded) {
            onRemoveDownloadTracks(tracks)
        } else {
            onDownloadTracks(tracks)
        }
    }
}

/** Floating album/playlist chrome: back circle plus a search, share and overflow pill. */
@Composable
fun CollectionTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    isSaved: Boolean,
    scrimProgress: State<Float>,
    modifier: Modifier = Modifier,
    palette: ArtworkPalette? = null,
    onAbout: (() -> Unit)? = null,
    onBestMix: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    isSearching: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onCloseSearch: (() -> Unit)? = null,
    searchPlaceholder: String = uiText("Find in playlist", "Buscar en la lista"),
    aboutLabel: String = uiText("About", "Acerca de"),
    onDownload: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    shareVisible: Boolean = true,
    saveVisible: Boolean = true,
    extraMenuItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    if (isSearching && onSearchQueryChange != null && onCloseSearch != null) {
        // Opaque enough that the field never sits on raw artwork.
        CollectionTopSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClose = onCloseSearch,
            placeholder = searchPlaceholder,
            modifier = modifier.background(Color.Black.copy(alpha = 0.55f)),
        )
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    val noun = if (aboutLabel.contains("album", true)) "album" else "playlist"

    DetailFloatingChrome(onBack = onBack, scrimProgress = scrimProgress, modifier = modifier, palette = palette) {
        if (onSearch != null) {
            ChromeAction(Icons.Rounded.Search, uiText("Search in collection", "Buscar en la colección"), onSearch)
        }
        if (shareVisible) ChromeAction(Icons.Rounded.IosShare, uiText("Share", "Compartir"), onShare)
        Box {
            ChromeAction(Icons.Rounded.MoreHoriz, uiText("More options", "Más opciones"), { menuOpen = true })
            GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                extraMenuItems.forEach { (label, action) ->
                    GlassDropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                }
                if (onDownload != null) {
                    GlassDropdownMenuItem(
                        text = { Text(if (isDownloaded) uiText("Remove download", "Eliminar descarga") else uiText("Download $noun", "Descargar colección")) },
                        onClick = {
                            menuOpen = false
                            onDownload()
                        },
                    )
                }
                if (saveVisible) GlassDropdownMenuItem(
                    text = { Text(if (isSaved) uiText("Remove from library", "Quitar de la biblioteca") else uiText("Add to library", "Añadir a la biblioteca")) },
                    onClick = {
                        menuOpen = false
                        onSave()
                    },
                )
                if (onAbout != null) {
                    GlassDropdownMenuItem(
                        text = { Text(aboutLabel) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
                if (onBestMix != null) {
                    GlassDropdownMenuItem(
                        text = { Text(uiText("Play with Best Mix", "Reproducir con Mejor mezcla")) },
                        onClick = {
                            menuOpen = false
                            onBestMix()
                        },
                    )
                }
            }
        }
    }
}
