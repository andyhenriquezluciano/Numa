package dev.sfg.numa.mobile.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.local.toPlaylist
import dev.sfg.numa.mobile.local.isLocalPlaylistId
import dev.sfg.numa.mobile.ui.components.*
import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.text.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CollectionSelectionBar(
    selected: List<Track>, onClear: () -> Unit, onSelectAll: () -> Unit,
    showActions: Boolean, onShowActions: () -> Unit, onHideActions: () -> Unit,
    onPlay: (List<Track>) -> Unit, onPlayNext: ((Track) -> Unit)?,
    onAdd: ((Track) -> Unit)?, onRemove: ((List<Track>) -> Unit)?,
    onDownload: ((List<Track>) -> Unit)?, onRemoveDownload: ((List<Track>) -> Unit)?,
    downloadedIds: Set<String>, removeLabel: String, onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    var choosingPlaylist by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 76.dp, start = 16.dp, end = 16.dp)
        .glassPane(RoundedCornerShape(24.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, uiText("Cancel selection", "Cancelar selección")) }
        Text(uiText("${selected.size} selected", "${selected.size} seleccionadas"), Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.DoneAll, uiText("Select all", "Seleccionar todas")) }
        IconButton(onClick = onShowActions) { Icon(Icons.Rounded.MoreVert, uiText("Selection options", "Opciones de la selección")) }
    }
    if (showActions && selected.isNotEmpty()) TrackActionsPopup(
        track = selected.first().copy(title = uiText("${selected.size} songs selected", "${selected.size} canciones seleccionadas")),
        onDismiss = onHideActions,
        onPlay = { onPlay(selected); onClear() },
        onPlayNext = onPlayNext?.let { action -> { selected.asReversed().forEach(action); onClear() } },
        onAddToQueue = onAdd?.let { action -> { selected.forEach(action); onClear() } },
        onAddToPlaylist = { choosingPlaylist = true },
        onViewAlbum = selected.map { it.albumId }.distinct().singleOrNull()?.takeIf { it.isNotBlank() }?.let { id -> { onOpen(id); onClear() } },
        onViewArtist = selected.map { it.artistId }.distinct().singleOrNull()?.takeIf { it.isNotBlank() }?.let { id -> { onOpen(id); onClear() } },
        onRemoveFromPlaylist = onRemove?.let { { confirmingRemoval = true } },
        removeFromPlaylistLabel = removeLabel,
        onDownload = if (selected.any { !it.isLocal && it.id !in downloadedIds }) onDownload?.let {
            { it(selected.filter { !it.isLocal && it.id !in downloadedIds }); onClear() }
        } else null,
        onRemoveDownload = if (selected.any { it.id in downloadedIds }) onRemoveDownload?.let {
            { it(selected.filter { it.id in downloadedIds }); onClear() }
        } else null,
        onShare = if (selected.none { it.isLocal }) ({
            val text = selected.joinToString("\n") { "${it.title} — ${it.artist}\nhttps://music.youtube.com/watch?v=${it.id}" }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
        }) else null,
    )
    if (confirmingRemoval) GlassAlertDialog(
        onDismissRequest = { confirmingRemoval = false },
        title = { Text(removeLabel) },
        text = { Text(uiText("Remove ${selected.size} songs? The original files will not be deleted.",
            uiText("Remove ${selected.size} songs? The original files will be kept.", "¿Quitar ${selected.size} canciones? No se borrarán los archivos originales."))) },
        confirmButton = { TextButton(onClick = {
            onRemove?.invoke(selected); confirmingRemoval = false; onClear()
        }) { Text(uiText("Remove", "Quitar")) } },
        dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text(uiText("Cancel", "Cancelar")) } },
    )
    if (choosingPlaylist) BulkPlaylistPicker(selected, onDismiss = { choosingPlaylist = false }, onDone = onClear)
}

@Composable
private fun BulkPlaylistPicker(tracks: List<Track>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val graph = NumaGraph.from(LocalContext.current)
    val library by graph.library.library.collectAsState()
    val local by graph.localLibrary.library.collectAsState()
    val lists = if (tracks.all { it.isLocal }) local.playlists.map { it.toPlaylist(local) } else library.savedPlaylists
    PlaylistPickerSheet(
        track = tracks.first(), tracks = tracks, playlists = lists,
        onDismiss = onDismiss,
        onSelect = { playlist ->
            onDismiss()
            val chosen = tracks.toList()
            graph.applicationScope.launch {
                var added = 0
                var failed = 0
                chosen.filter { candidate -> playlist.tracks.none { it.id == candidate.id } }.forEach { track ->
                    runCatching {
                        if (isLocalPlaylistId(playlist.id)) graph.localLibrary.addToPlaylist(playlist.id, track.id)
                        else withContext(Dispatchers.IO) { graph.playlistActions.add(playlist.id, track) }
                    }.onSuccess { added++ }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        failed++
                    }
                }
                if (failed > 0) graph.postWarning(uiText("Added $added songs; $failed could not be added.", "Se añadieron $added canciones; no se pudieron añadir $failed."))
                else graph.successEvent.tryEmit(uiText("Added $added songs to the playlist.", "Se añadieron $added canciones a la lista."))
            }
            onDone()
        },
    )
}
