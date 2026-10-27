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

import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog

import dev.sfg.numa.mobile.ui.text.uiText

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.sfg.numa.connect.R
import dev.sfg.numa.mobile.local.LocalLibraryRepository
import dev.sfg.numa.mobile.ui.components.rememberCoverPicker
import dev.sfg.numa.mobile.ui.components.rememberFolderPicker
import dev.sfg.numa.mobile.ui.components.rememberSongPicker

/** What a playlist kept on this phone can do beyond what an online one can. */
class LocalPlaylistEdit(
    val onAddSongs: () -> Unit,
    val onAddFolder: () -> Unit,
    val onPickCover: () -> Unit,
    /** Null when no artwork can be removed. */
    val onClearCover: (() -> Unit)?,
    val onRename: (String) -> Unit,
    val onDelete: () -> Unit,
)

/** Wires the pickers and repository calls for one local playlist. */
@Composable
internal fun rememberLocalPlaylistEdit(
    repository: LocalLibraryRepository,
    playlistId: String,
    hasCustomCover: Boolean,
    onDeleted: () -> Unit,
): LocalPlaylistEdit {
    val addSongs = rememberSongPicker { uris: List<Uri> -> repository.importUris(uris, playlistId) }
    val addFolder = rememberFolderPicker { tree: Uri -> repository.importTree(tree, playlistId) }
    val graph = dev.sfg.numa.mobile.NumaGraph.from(androidx.compose.ui.platform.LocalContext.current)
    val automatic = graph.automaticPlaylistCovers
    val pickCover = dev.sfg.numa.mobile.ui.components.rememberPlaylistCoverPicker { uri: Uri ->
        automatic.setEnabled(playlistId, false)
        repository.setPlaylistCover(playlistId, uri)
    }
    return remember(playlistId, hasCustomCover, addSongs, addFolder, pickCover) {
        LocalPlaylistEdit(
            onAddSongs = addSongs,
            onAddFolder = addFolder,
            onPickCover = pickCover,
            onClearCover = if (hasCustomCover) ({ repository.clearPlaylistCover(playlistId) }) else null,
            onRename = { repository.renamePlaylist(playlistId, it) },
            onDelete = {
                repository.deletePlaylist(playlistId)
                graph.library.removePlaylist(playlistId)
                graph.playlistCovers.purge(playlistId)
                automatic.purge(playlistId)
                onDeleted()
            },
        )
    }
}

/** Import actions aligned to opposite edges of the local playlist. */
@Composable
internal fun PlaylistEditBar(local: LocalPlaylistEdit, onShuffle: () -> Unit, shuffleEnabled: Boolean, modifier: Modifier = Modifier) {
    val graph = dev.sfg.numa.mobile.NumaGraph.from(androidx.compose.ui.platform.LocalContext.current)
    val progress by graph.automaticPlaylistLyrics.progress.collectAsState()
    androidx.compose.foundation.layout.Column(modifier) {
    if (progress != null) Text(progress.orEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.sfg.numa.mobile.ui.components.HeroCircleButton(
            onClick = local.onAddSongs, icon = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = uiText("Add songs", "Añadir canciones"))
        dev.sfg.numa.mobile.ui.components.HeroCircleButton(onClick = onShuffle,
            icon = androidx.compose.material.icons.Icons.Rounded.Shuffle,
            contentDescription = uiText("Shuffle", "Aleatorio"), enabled = shuffleEnabled)
        dev.sfg.numa.mobile.ui.components.HeroCircleButton(
            onClick = local.onAddFolder, icon = androidx.compose.material.icons.Icons.Rounded.CreateNewFolder,
            contentDescription = uiText("Add folder", "Añadir carpeta"))
    }
    }
}

@Composable
internal fun LocalPlaylistDialogs(title: String, local: LocalPlaylistEdit?, renaming: Boolean,
    confirmingDelete: Boolean, onRenameDismiss: () -> Unit, onDeleteDismiss: () -> Unit) {
    if (local != null && renaming) {
        var name by remember { mutableStateOf(title) }
        GlassAlertDialog(
            onDismissRequest = onRenameDismiss,
            title = { Text(uiText("Rename playlist", "Cambiar nombre de la lista")) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(150) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { local.onRename(name); onRenameDismiss() }) { Text(uiText("Rename", "Cambiar nombre")) }
            },
            dismissButton = { TextButton(onClick = onRenameDismiss) { Text(uiText("Cancel", "Cancelar")) } },
        )
    }
    if (local != null && confirmingDelete) {
        GlassAlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.qol_delete_title, title)) },
            text = { Text(stringResource(R.string.qol_local_delete_message)) },
            confirmButton = { dev.sfg.numa.mobile.ui.components.GlassConfirmButton(stringResource(R.string.qol_delete)) { onDeleteDismiss(); local.onDelete() } },
            dismissButton = { dev.sfg.numa.mobile.ui.components.GlassConfirmButton(stringResource(R.string.qol_cancel)) { onDeleteDismiss() } },
        )
    }
}
