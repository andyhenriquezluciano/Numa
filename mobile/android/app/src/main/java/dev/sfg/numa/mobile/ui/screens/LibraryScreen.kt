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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.sfg.numa.mobile.download.DownloadItem
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.local.toPlaylist
import dev.sfg.numa.mobile.local.toTrack
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.components.NumaChromeHeight
import dev.sfg.numa.mobile.ui.components.filterCatalogItems
import dev.sfg.numa.mobile.ui.components.filterTracks
import dev.sfg.numa.mobile.ui.components.normalizeSearchText
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn

private const val NEW_RELEASES_ID = "FEmusic_new_releases"

@Composable
fun LibraryScreen(
    library: LibrarySnapshot,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    downloads: List<DownloadItem> = emptyList(),
    downloadedTrackIds: Set<String> = emptySet(),
    downloadingTrackIds: Set<String> = emptySet(),
    totalBytesUsed: Long = 0L,
    onPlay: (Track) -> Unit,
    onPlayNext: ((Track) -> Unit)?,
    onAddToQueue: ((Track) -> Unit)?,
    onOpenDetail: (String) -> Unit,
    onDownloadTrack: ((Track) -> Unit)? = null,
    onRemoveDownloadTrack: ((String) -> Unit)? = null,
    onShare: ((Track) -> Unit)? = null,
    localLibrary: dev.sfg.numa.mobile.local.LocalLibraryRepository? = null,
    localSnapshot: dev.sfg.numa.mobile.local.LocalSnapshot = dev.sfg.numa.mobile.local.LocalSnapshot(),
    localBusy: Boolean = false,
    youtubeAvailable: Boolean = false,
    onCreateYouTubePlaylist: (String) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var metricsPeriod by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(dev.sfg.numa.mobile.model.MetricsPeriod.ALL) }
    var expandedMetrics by androidx.compose.runtime.saveable.rememberSaveable(metricsPeriod, searchQuery) {
        mutableStateOf(emptyList<String>())
    }
    val searching = searchQuery.isNotBlank()
    val browse = { onOpenDetail(NEW_RELEASES_ID) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = ((maxWidth.value - 16) / 180).toInt().coerceIn(2, 6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = NumaChromeHeight)) {
        item {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    LibraryHeader(library.summary(localSnapshot.songs.size), Modifier.weight(1f))
                    RestoreLibraryLists(library)
                    localLibrary?.let { repository ->
                        LocalLibraryActions(
                            repository = repository,
                            busy = localBusy,
                            youtubeAvailable = youtubeAvailable,
                            onCreateYouTubePlaylist = onCreateYouTubePlaylist,
                            onOpenPlaylist = onOpenDetail,
                        )
                    }
                }
                LibraryChips(filter, onFilterChange)
                LibraryFilterField(searchQuery, uiText("Filter ${filter.label.lowercase()}", "Filtrar ${filter.label.lowercase()}")) { searchQuery = it }
                androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
            }
        }
        when (filter) {
            LibraryFilter.METRICS -> listeningMetrics(library, searchQuery, onPlay, metricsPeriod, { metricsPeriod = it }, onOpenDetail,
                expandedMetrics, { section ->
                    expandedMetrics = if (section in expandedMetrics) expandedMetrics - section else expandedMetrics + section
                })
            LibraryFilter.PLAYLISTS -> collections(
                uiText("Your playlists", "Tus listas"),
                filterCatalogItems((localSnapshot.playlists.map { it.toPlaylist(localSnapshot) } + library.savedPlaylists)
                    .distinctBy { it.id }.sortedBy { dev.sfg.numa.mobile.model.playlistDisplayRank(it.id) }
                    .map { CatalogItem.Collection(it) }, searchQuery),
                Icons.Rounded.LibraryMusic,
                if (searching) uiText("No matching playlists", "No hay listas que coincidan") else uiText("No saved playlists", "No hay listas guardadas"),
                if (searching) uiText("No playlists matching \"$searchQuery\"", "No hay listas que coincidan con «$searchQuery»") else uiText("Save a playlist and it will stay close at hand.", "Guarda una lista para tenerla siempre a mano."),
                onOpenDetail,
                browse.takeUnless { searching },
                columns = columns,
            )
            LibraryFilter.ARTISTS -> collections(
                uiText("Saved artists", "Artistas guardados"),
                filterCatalogItems(library.savedArtists.map { CatalogItem.Performer(it) }, searchQuery),
                Icons.Rounded.Person,
                if (searching) uiText("No matching artists", "No hay artistas que coincidan") else uiText("No saved artists", "No hay artistas guardados"),
                if (searching) uiText("No artists matching \"$searchQuery\"", "No hay artistas que coincidan con «$searchQuery»") else uiText("Follow an artist to build this shelf.", "Sigue a un artista para verlo aquí."),
                onOpenDetail,
                browse.takeUnless { searching },
                columns = columns,
            )
            LibraryFilter.ALBUMS -> collections(
                uiText("Saved albums", "Álbumes guardados"),
                filterCatalogItems(library.savedAlbums.map { CatalogItem.Record(it) }, searchQuery),
                Icons.Rounded.Album,
                if (searching) uiText("No matching albums", "No hay álbumes que coincidan") else uiText("No saved albums", "No hay álbumes guardados"),
                if (searching) uiText("No albums matching \"$searchQuery\"", "No hay álbumes que coincidan con «$searchQuery»") else uiText("Albums you save will be available here.", "Aquí aparecerán los álbumes que guardes."),
                onOpenDetail,
                browse.takeUnless { searching },
                columns = columns,
            )
            LibraryFilter.SONGS -> tracks(
                title = uiText("Liked songs", "Música que te gusta"),
                values = filterTracks(library.likedTracks, searchQuery),
                emptyIcon = Icons.Rounded.MusicNote,
                emptyTitle = if (searching) uiText("No matching songs", "No hay canciones que coincidan") else uiText("No songs saved", "No hay canciones guardadas"),
                emptyMessage = if (searching) uiText("No songs matching \"$searchQuery\"", "No hay canciones que coincidan con «$searchQuery»") else uiText("Save songs to your library to see them here", "Guarda canciones en tu biblioteca para verlas aquí"),
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onAdd = onAddToQueue,
                onDownloadTrack = onDownloadTrack,
                onRemoveDownloadTrack = onRemoveDownloadTrack,
                onShare = onShare,
                onOpen = onOpenDetail,
                onBrowse = browse.takeUnless { searching },
            )
            LibraryFilter.RECENT -> tracks(
                title = uiText("Recently played", "Reproducido recientemente"),
                values = filterTracks(library.recentlyPlayed, searchQuery),
                emptyIcon = Icons.Rounded.History,
                emptyTitle = if (searching) uiText("No matching recent tracks", "No hay canciones recientes que coincidan") else uiText("Nothing played yet", "Todavía no has reproducido música"),
                emptyMessage = if (searching) uiText("No recently played tracks matching \"$searchQuery\"", "No hay canciones recientes que coincidan con «$searchQuery»") else uiText("Start a song and NÜMA will remember it here.", "Reproduce una canción y NÜMA la recordará aquí."),
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onAdd = onAddToQueue,
                onDownloadTrack = onDownloadTrack,
                onRemoveDownloadTrack = onRemoveDownloadTrack,
                onShare = onShare,
                onOpen = onOpenDetail,
            )
            LibraryFilter.LOCAL -> {
                if (localSnapshot.playlists.isEmpty() && localSnapshot.songs.isEmpty() && !searching) {
                    item { LibraryEmpty(Icons.Rounded.LibraryMusic,
                        uiText("Your music, here", "Tu música, aquí"),
                        uiText("Tap + to create a playlist and add songs from your phone.", "Pulsa + para crear una lista y añadir canciones del teléfono.")) }
                } else {
                collections(
                    uiText("Local playlists", "Listas locales"),
                    filterCatalogItems(localSnapshot.playlists.map { CatalogItem.Collection(it.toPlaylist()) }, searchQuery),
                    Icons.Rounded.LibraryMusic,
                    if (searching) uiText("No matching playlists", "No hay listas que coincidan") else uiText("No local playlists", "No hay listas locales"),
                    if (searching) uiText("No playlists matching \"$searchQuery\"", "No hay listas que coincidan con «$searchQuery»") else uiText("Make one with New playlist, then add songs from this phone.", "Crea una lista con Nueva lista y añade canciones del teléfono."),
                    onOpenDetail,
                    columns = columns,
                )
                tracks(
                    title = uiText("Local songs", "Canciones locales"),
                    values = filterTracks(localSnapshot.songs.map { it.toTrack() }, searchQuery),
                    emptyIcon = Icons.Rounded.MusicNote,
                    emptyTitle = if (searching) uiText("No matching songs", "No hay canciones que coincidan") else uiText("No local songs", "No hay canciones locales"),
                    emptyMessage = if (searching) uiText("No songs matching \"$searchQuery\"", "No hay canciones que coincidan con «$searchQuery»") else uiText("Use Add local files to bring in music from this phone.", "Usa Añadir archivos locales para importar música del teléfono."),
                    downloadedTrackIds = emptySet(),
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAdd = onAddToQueue,
                    onShare = null,
                )
                }
            }
            LibraryFilter.DOWNLOADS -> {
                val query = normalizeSearchText(searchQuery)
                val filtered = if (query.isNotBlank()) {
                    downloads.filter {
                        normalizeSearchText(it.track.title).contains(query) ||
                            normalizeSearchText(it.track.artist).contains(query) ||
                            normalizeSearchText(it.track.album).contains(query)
                    }
                } else downloads
                downloadsList(
                    downloads = filtered,
                    totalBytesUsed = totalBytesUsed,
                    onPlay = onPlay,
                    onRemoveDownload = { id -> onRemoveDownloadTrack?.invoke(id) },
                )
            }
        }
    }
}

}

private fun LibrarySnapshot.summary(localSongs: Int): String {
    if (likedTracks.isEmpty() && savedAlbums.isEmpty() && savedArtists.isEmpty() && savedPlaylists.isEmpty() && localSongs == 0) {
        return uiText("Nothing saved yet", "Todavía no hay contenido guardado")
    }
    val local = if (localSongs > 0) uiText(", $localSongs on this phone", ", $localSongs en este teléfono") else ""
    return uiText("${likedTracks.size} songs, ${savedAlbums.size} albums, ${savedArtists.size} artists$local", "${likedTracks.size} canciones, ${savedAlbums.size} álbumes, ${savedArtists.size} artistas$local")
}
