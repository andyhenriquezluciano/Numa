package dev.sfg.numa.mobile.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.library.containsPlaylist
import dev.sfg.numa.mobile.library.defaultPlaylistIds
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun RestoreLibraryLists(library: LibrarySnapshot) {
    if (defaultPlaylistIds.all { library.containsPlaylist(it) }) return
    val graph = NumaGraph.from(LocalContext.current)
    IconButton(onClick = { graph.library.restoreDefaultPlaylists() }) {
        Icon(Icons.Rounded.Restore, uiText("Restore lists", "Restaurar listas"), tint = SettingsStyle.Title)
    }
}
