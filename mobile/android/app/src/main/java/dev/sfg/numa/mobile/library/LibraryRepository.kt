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

package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.withRecordedListen
import dev.sfg.numa.mobile.model.withListeningTime

import dev.sfg.numa.mobile.catalog.CatalogRepository
import dev.sfg.numa.mobile.model.Album
import dev.sfg.numa.mobile.model.Artist
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.CatalogKind
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Playlist
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One source of truth for cached and signed-in library content. */
class LibraryRepository(
    private val cache: LibraryCache,
    private val catalog: CatalogRepository,
    private val scope: CoroutineScope,
) {
    private val mutableLibrary = MutableStateFlow(LibrarySnapshot())
    val library: StateFlow<LibrarySnapshot> = mutableLibrary.asStateFlow()
    internal var onLocalListen: ((Track, Long, Boolean) -> Unit)? = null
    internal var onManualRestore: (() -> Unit)? = null
    private val cacheUpdates = Channel<LibrarySnapshot>(Channel.CONFLATED)
    private val stateLock = Any()
    private val refreshLock = Mutex()
    private val initialized = scope.async(Dispatchers.IO) { mutableLibrary.value = cache.load() }
    private val edits = Channel<(LibrarySnapshot) -> LibrarySnapshot>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            initialized.await()
            for (transform in edits) applyUpdate(transform)
        }
        // One writer prevents rapid likes/history updates from finishing out of
        // order and leaving an older snapshot on disk.
        scope.launch(Dispatchers.IO) {
            for (snapshot in cacheUpdates) cache.save(snapshot)
        }
    }

    fun toggleLiked(track: Track) = update { current ->
        current.withLiked(track, current.likedTracks.none { it.id == track.id })
    }

    fun setLiked(track: Track, liked: Boolean) = update { current -> current.withLiked(track, liked) }

    fun saveAlbum(album: Album) = update { current ->
        current.copy(savedAlbums = toggle(current.savedAlbums, album, Album::id))
    }

    fun saveArtist(artist: Artist) = update { current ->
        current.copy(savedArtists = toggle(current.savedArtists, artist, Artist::id))
    }

    fun setArtistSaved(artist: Artist, saved: Boolean) = update { current ->
        current.copy(
            savedArtists = if (saved) {
                (current.savedArtists.filterNot { it.id == artist.id } + artist)
            } else {
                current.savedArtists.filterNot { it.id == artist.id }
            },
        )
    }

    fun savePlaylist(playlist: Playlist) = update { current ->
        current.togglePlaylist(playlist)
    }

    fun removePlaylist(playlistId: String) = update { current ->
        current.withoutPlaylist(playlistId)
    }

    fun restoreDefaultPlaylists() = update { it.withDefaultPlaylists() }

    /** Keeps a locally saved playlist in sync after an authenticated mutation. */
    fun refreshPlaylist(playlist: Playlist) = update { current ->
        val id = playlist.id.removePrefix("VL")
        current.copy(savedPlaylists = current.savedPlaylists.map { saved ->
            if (saved.id.removePrefix("VL") == id) playlist else saved
        })
    }

    /** Stores full collection membership after a browse page loads, without changing saved state. */
    fun cacheDetail(detail: BrowseDetail) {
        val saved = when (detail.kind) {
            CatalogKind.PLAYLIST -> mutableLibrary.value.savedPlaylists.any {
                it.id.removePrefix("VL") == detail.id.removePrefix("VL")
            }
            CatalogKind.ALBUM -> mutableLibrary.value.savedAlbums.any { it.id == detail.id }
            else -> false
        }
        if (!saved) return

        update { current ->
            when (detail.kind) {
                CatalogKind.PLAYLIST -> current.copy(
                    savedPlaylists = current.savedPlaylists.map { saved ->
                        if (saved.id.removePrefix("VL") == detail.id.removePrefix("VL")) {
                            Playlist(
                                id = saved.id,
                                title = detail.title.ifBlank { saved.title },
                                author = detail.artist.ifBlank { detail.subtitle.ifBlank { saved.author } },
                                artworkUrl = detail.artworkUrl.ifBlank { saved.artworkUrl },
                                description = detail.description.ifBlank { saved.description },
                                tracks = detail.tracks.ifEmpty { saved.tracks },
                                explicit = detail.explicit || saved.explicit,
                            )
                        } else saved
                    },
                )
                CatalogKind.ALBUM -> current.copy(
                    savedAlbums = current.savedAlbums.map { saved ->
                        if (saved.id == detail.id) saved.copy(
                            title = detail.title.ifBlank { saved.title },
                            artist = detail.artist.ifBlank { detail.subtitle.ifBlank { saved.artist } },
                            artworkUrl = detail.artworkUrl.ifBlank { saved.artworkUrl },
                            year = detail.year.ifBlank { saved.year },
                            tracks = detail.tracks.ifEmpty { saved.tracks },
                            explicit = detail.explicit || saved.explicit,
                        ) else saved
                    },
                )
                else -> current
            }
        }
    }

    fun recordPlayed(track: Track) = update { current ->
        onLocalListen?.invoke(track, 0, true)
        current.withRecordedListen(track)
    }
    fun recordListeningTime(track: Track, ms: Long) = update { current ->
        onLocalListen?.invoke(track, ms, false)
        current.withListeningTime(track, ms)
    }

    internal suspend fun transformSync(transform: (LibrarySnapshot) -> LibrarySnapshot): LibrarySnapshot {
        val result = kotlinx.coroutines.CompletableDeferred<LibrarySnapshot>()
        update { current ->
            try { transform(current).also { result.complete(it) } }
            catch (error: Exception) { result.completeExceptionally(error); current }
        }
        return result.await()
    }

    suspend fun exportSnapshot(): LibrarySnapshot {
        val result = kotlinx.coroutines.CompletableDeferred<LibrarySnapshot>()
        update { current -> result.complete(current); current }
        return result.await()
    }

    suspend fun importSnapshot(snapshot: LibrarySnapshot) {
        val result = kotlinx.coroutines.CompletableDeferred<Unit>()
        update { current ->
            val merged = current.mergeBackup(snapshot)
            onManualRestore?.invoke()
            result.complete(Unit)
            merged
        }
        result.await()
        exportSnapshot()
    }

    suspend fun refreshSignedInLibrary(): Result<Unit> = refreshLock.withLock {
        runCatching {
            coroutineScope {
                val landing = async(Dispatchers.IO) { catalog.library() }
                val liked = async(Dispatchers.IO) { runCatching { catalog.likedSongs().tracks } }
                val albums = async(Dispatchers.IO) { runCatching { catalog.sectionItems(LIKED_ALBUMS) } }
                val playlists = async(Dispatchers.IO) { runCatching { catalog.sectionItems(LIKED_PLAYLISTS) } }
                val items = landing.await().flatMap { it.items }
                val remoteLiked = liked.await().getOrDefault(emptyList())
                val remoteAlbums = albums.await().getOrDefault(emptyList())
                    .filterIsInstance<CatalogItem.Record>().map { it.album }.distinctBy(Album::id)
                val remotePlaylists = playlists.await().getOrDefault(emptyList())
                    .filterIsInstance<CatalogItem.Collection>().map { it.playlist }
                update { current ->
                    current.copy(
                        likedTracks = remoteLiked.ifEmpty { current.likedTracks },
                        savedAlbums = remoteAlbums
                            .ifEmpty { items.filterIsInstance<CatalogItem.Record>().map { it.album }.distinctBy(Album::id) }
                            .ifEmpty { current.savedAlbums },
                        savedArtists = items.filterIsInstance<CatalogItem.Performer>().map { it.artist }.distinctBy(Artist::id)
                            .ifEmpty { current.savedArtists },
                        savedPlaylists = current.mergeVisiblePlaylists(remotePlaylists
                            .ifEmpty { items.filterIsInstance<CatalogItem.Collection>().map { it.playlist } }
                            .ifEmpty { current.savedPlaylists }),
                    )
                }
            }
        }
    }

    private fun update(transform: (LibrarySnapshot) -> LibrarySnapshot) {
        edits.trySend(transform).getOrThrow()
    }

    private fun applyUpdate(transform: (LibrarySnapshot) -> LibrarySnapshot) = synchronized(stateLock) {
        val current = mutableLibrary.value
        val next = transform(current)
        if (next == current) return@synchronized
        if (next.hiddenPlaylistIds != current.hiddenPlaylistIds) cache.saveHiddenPlaylists(next.hiddenPlaylistIds)
        mutableLibrary.value = next
        cacheUpdates.trySend(next).getOrThrow()
    }

    private fun <T> toggle(values: List<T>, candidate: T, id: (T) -> String): List<T> {
        val existing = values.indexOfFirst { id(it) == id(candidate) }
        return if (existing >= 0) values.toMutableList().apply { removeAt(existing) } else listOf(candidate) + values
    }

    private companion object {
        /** YouTube Music's own Albums tab — the albums the user actually saved. */
        const val LIKED_ALBUMS = "FEmusic_liked_albums"
        /** The complete saved-playlist grid; the library landing page contains only recent items. */
        const val LIKED_PLAYLISTS = "FEmusic_liked_playlists"
    }
}

private fun LibrarySnapshot.withLiked(track: Track, liked: Boolean): LibrarySnapshot {
    val withoutTrack = likedTracks.filterNot { it.id == track.id }
    return copy(likedTracks = if (liked) listOf(track) + withoutTrack else withoutTrack)
}

/** Library grid payloads are metadata-only; never let one erase cached offline membership. */
internal fun Playlist.withCachedTracksFrom(cached: List<Playlist>): Playlist {
    if (tracks.isNotEmpty()) return this
    val previous = cached.firstOrNull { it.id.removePrefix("VL") == id.removePrefix("VL") }
    return if (previous?.tracks?.isNotEmpty() == true) copy(tracks = previous.tracks) else this
}
