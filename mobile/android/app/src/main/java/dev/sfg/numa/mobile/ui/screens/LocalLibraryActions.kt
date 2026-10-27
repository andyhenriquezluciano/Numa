/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.local.LocalLibraryRepository
import dev.sfg.numa.mobile.ui.components.NewPlaylistSheet
import dev.sfg.numa.mobile.ui.components.rememberSongPicker
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun LocalLibraryActions(
    repository: LocalLibraryRepository,
    busy: Boolean,
    youtubeAvailable: Boolean,
    onCreateYouTubePlaylist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var pendingPlaylist by remember { mutableStateOf("") }
    val addSongs = rememberSongPicker { uris ->
        if (pendingPlaylist.isNotBlank()) repository.importUris(uris, pendingPlaylist)
    }
    IconButton(onClick = { creating = true }, enabled = !busy,
        modifier = Modifier.padding(end = 12.dp).size(48.dp)) {
        Icon(Icons.Rounded.Add, uiText("New playlist", "Nueva lista"), tint = SettingsStyle.Title,
            modifier = Modifier.size(23.dp))
    }
    if (creating) {
        NewPlaylistSheet(youtubeAvailable = youtubeAvailable, onDismiss = { creating = false },
            onCreate = { title, local, thenAddSongs ->
                creating = false
                if (!local) onCreateYouTubePlaylist(title) else {
                    val id = repository.createPlaylist(title)
                    pendingPlaylist = id
                    if (thenAddSongs) addSongs() else onOpenPlaylist(id)
                }
            })
    }
}
