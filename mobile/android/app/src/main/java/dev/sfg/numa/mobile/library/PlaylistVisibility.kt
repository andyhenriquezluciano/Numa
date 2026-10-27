package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist

internal fun libraryPlaylistId(id: String): String = id.removePrefix("VL").let {
    if (it == "LM") "FEmusic_liked_videos" else it
}

internal fun LibrarySnapshot.isPlaylistHidden(id: String): Boolean =
    libraryPlaylistId(id) in hiddenPlaylistIds

internal fun LibrarySnapshot.isItemHidden(item: CatalogItem): Boolean = when (item) {
    is CatalogItem.Collection -> isPlaylistHidden(item.playlist.id)
    else -> isPlaylistHidden(item.stableId)
}

internal fun LibrarySnapshot.withoutPlaylist(id: String): LibrarySnapshot {
    val key = libraryPlaylistId(id)
    return copy(savedPlaylists = savedPlaylists.filterNot { libraryPlaylistId(it.id) == key },
        hiddenPlaylistIds = hiddenPlaylistIds + key)
}

internal fun LibrarySnapshot.togglePlaylist(playlist: Playlist): LibrarySnapshot {
    val key = libraryPlaylistId(playlist.id)
    if (savedPlaylists.any { libraryPlaylistId(it.id) == key }) return withoutPlaylist(key)
    return copy(savedPlaylists = listOf(playlist) + savedPlaylists,
        hiddenPlaylistIds = hiddenPlaylistIds - key)
}

internal fun LibrarySnapshot.containsPlaylist(id: String): Boolean =
    savedPlaylists.any { libraryPlaylistId(it.id) == libraryPlaylistId(id) }

internal fun LibrarySnapshot.mergeVisiblePlaylists(remote: List<Playlist>): List<Playlist> {
    val cached = savedPlaylists.associateBy { libraryPlaylistId(it.id) }
    return (remote + savedPlaylists).asSequence().filterNot { libraryPlaylistId(it.id) in hiddenPlaylistIds }
        .distinctBy { libraryPlaylistId(it.id) }.map { playlist ->
            val previous = cached[libraryPlaylistId(playlist.id)]
            if (playlist.tracks.isEmpty() && previous?.tracks?.isNotEmpty() == true)
                playlist.copy(tracks = previous.tracks) else playlist
        }.toList()
}
