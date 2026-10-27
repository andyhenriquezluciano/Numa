package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenuItem
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.LocalAccent

@Composable
internal fun LibraryCategoryMenu(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val groups = listOf(
        uiText("Collection", "Colección") to listOf(LibraryFilter.PLAYLISTS, LibraryFilter.SONGS, LibraryFilter.ALBUMS, LibraryFilter.ARTISTS),
        uiText("Activity", "Actividad") to listOf(LibraryFilter.RECENT, LibraryFilter.METRICS),
        uiText("On this device", "En este dispositivo") to listOf(LibraryFilter.DOWNLOADS, LibraryFilter.LOCAL),
    )
    groups.forEach { (title, filters) ->
        Text(title, color = SettingsStyle.Description, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
        filters.forEach { filter ->
            val tint = if (filter == selected) LocalAccent.current else SettingsStyle.Title
            GlassDropdownMenuItem(
                text = { Text(if (filter == LibraryFilter.PLAYLISTS) uiText("Playlists", "Listas") else filter.label, color = tint) },
                onClick = { onSelect(filter) },
                leadingIcon = { Icon(filter.categoryIcon(), null, Modifier.size(20.dp), tint = tint) },
                trailingIcon = if (filter == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = tint) }) else null,
            )
        }
    }
}

private fun LibraryFilter.categoryIcon(): ImageVector = when (this) {
    LibraryFilter.PLAYLISTS -> Icons.Rounded.QueueMusic
    LibraryFilter.SONGS -> Icons.Rounded.MusicNote
    LibraryFilter.ALBUMS -> Icons.Rounded.Album
    LibraryFilter.ARTISTS -> Icons.Rounded.Person
    LibraryFilter.RECENT -> Icons.Rounded.History
    LibraryFilter.METRICS -> Icons.Rounded.BarChart
    LibraryFilter.DOWNLOADS -> Icons.Rounded.Download
    LibraryFilter.LOCAL -> Icons.Rounded.Folder
}
