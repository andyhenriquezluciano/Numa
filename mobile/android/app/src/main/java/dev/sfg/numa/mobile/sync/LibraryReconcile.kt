package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.model.LibrarySnapshot

internal fun <T> reconcileItems(base: List<T>, local: List<T>, remote: List<T>, key: (T) -> String): List<T> {
    val previous = base.associateBy(key); val current = local.associateBy(key)
    val result = remote.associateByTo(linkedMapOf(), key)
    (previous.keys + current.keys).forEach { id ->
        if (previous[id] != current[id]) {
            current[id]?.let { result[id] = it } ?: result.remove(id)
        }
    }
    return result.values.toList()
}

internal fun reconcileLibrary(base: LibrarySnapshot, local: LibrarySnapshot, remote: LibrarySnapshot): LibrarySnapshot = remote.copy(
    likedTracks = reconcileItems(base.likedTracks, local.likedTracks, remote.likedTracks) { it.id },
    savedArtists = reconcileItems(base.savedArtists, local.savedArtists, remote.savedArtists) { it.id },
    savedAlbums = reconcileItems(base.savedAlbums, local.savedAlbums, remote.savedAlbums) { it.id },
    savedPlaylists = reconcileItems(base.savedPlaylists, local.savedPlaylists, remote.savedPlaylists) { it.id },
    playedTracks = reconcileItems(base.playedTracks, local.playedTracks, remote.playedTracks) { it.id },
    recentlyPlayed = reconcileItems(base.recentlyPlayed, local.recentlyPlayed, remote.recentlyPlayed) { it.id }.take(50),
    hiddenPlaylistIds = reconcileItems(base.hiddenPlaylistIds.toList(), local.hiddenPlaylistIds.toList(), remote.hiddenPlaylistIds.toList()) { it }.toSet(),
)
