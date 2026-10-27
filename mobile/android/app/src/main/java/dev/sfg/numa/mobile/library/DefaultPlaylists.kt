package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist
import dev.sfg.numa.mobile.ui.text.uiText

internal val defaultPlaylistIds = setOf("FEmusic_liked_videos", "SE")

internal fun LibrarySnapshot.withDefaultPlaylists(): LibrarySnapshot {
    val defaults = listOf(
        Playlist("VLLM", uiText("Liked music", "Música que me gusta"), tracks = likedTracks),
        Playlist("VLSE", uiText("Episodes for later", "Episodios para más tarde")),
    )
    val missing = defaults.filterNot { containsPlaylist(it.id) }
    return copy(savedPlaylists = savedPlaylists + missing,
        hiddenPlaylistIds = hiddenPlaylistIds.filterNot { libraryPlaylistId(it) in defaultPlaylistIds }.toSet())
}
