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

package dev.sfg.numa.mobile.ui.foldable

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.download.DownloadItem
import dev.sfg.numa.mobile.download.DownloadStatus
import dev.sfg.numa.mobile.library.isItemHidden
import dev.sfg.numa.mobile.library.isPlaylistHidden
import dev.sfg.numa.mobile.model.BuiltInHomeSection
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.CatalogSection
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.components.CatalogSectionBottomSheet
import dev.sfg.numa.mobile.ui.components.HomeSectionShimmer
import dev.sfg.numa.mobile.ui.screens.homeItemKey

data class FoldableSheetState(
    val title: String,
    val initialItems: List<CatalogItem>,
    val browseId: String = "",
    val params: String = "",
)

internal fun catalogItemSubtitle(item: CatalogItem): String =
    when (item) {
        is CatalogItem.Song -> item.track.artist
        is CatalogItem.Collection -> item.playlist.author
        is CatalogItem.Record -> item.album.artist
        is CatalogItem.Performer -> item.artist.subtitle
        is CatalogItem.Category -> item.title
    }

private inline fun <reified T : CatalogItem> extractItemsOfKind(
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot? = null,
): List<T> {
    if (state !is LoadState.Content) return emptyList()
    val items = state.value.flatMap { it.items }.filterIsInstance<T>()
    return if (library != null) items.filterNot { library.isItemHidden(it) } else items
}

// Foldable-optimized Home screen interface with full-bleed cinematic hero presentation.
@Composable
fun FoldableHomeScreen(
    settings: NumaSettings,
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot,
    auth: AuthState = AuthState.SignedOut,
    downloads: List<DownloadItem> = emptyList(),
    downloadedTrackIds: Set<String> = emptySet(),
    isOffline: Boolean = false,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: (LibraryFilter) -> Unit,
    onPlay: (Track) -> Unit,
    onOpenDetail: (String) -> Unit,
    onEditLayout: () -> Unit = {},
    onToggleLike: (Track) -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onShare: ((Track) -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    onSignIn: () -> Unit = onOpenProfile,
    onFetchSectionItems: (suspend (String, String) -> List<CatalogItem>)? = null,
    onPlayItem: ((CatalogItem) -> Unit)? = null,
    onPlayCollection: ((String, String) -> Unit)? = null,
) {
    var activeSectionSheet by remember { mutableStateOf<FoldableSheetState?>(null) }
    var selectedCategory by remember { mutableStateOf(uiText("All", "Todo")) }
    val categories = remember { listOf(uiText("All", "Todo"), uiText("Playlists", "Listas de reproducción"), uiText("Artists", "Artistas"), uiText("Albums", "Álbumes"), uiText("Songs", "Canciones")) }

    val playCatalogItem: (CatalogItem) -> Unit = { item ->
        if (onPlayItem != null) {
            onPlayItem(item)
        } else if (onPlayCollection != null) {
            when (item) {
                is CatalogItem.Song -> onPlay(item.track)
                is CatalogItem.Collection -> onPlayCollection(item.playlist.id, item.title)
                is CatalogItem.Record -> onPlayCollection(item.album.id, item.title)
                is CatalogItem.Performer -> onPlayCollection(item.artist.id, item.title)
                is CatalogItem.Category -> onOpenDetail(item.stableId)
            }
        } else {
            when (item) {
                is CatalogItem.Song -> onPlay(item.track)
                else -> onOpenDetail(item.stableId)
            }
        }
    }

    val openCatalogItem: (CatalogItem) -> Unit = { item ->
        when (item) {
            is CatalogItem.Song -> onPlay(item.track)
            else -> onOpenDetail(item.stableId)
        }
    }

    activeSectionSheet?.let { sheet ->
        CatalogSectionBottomSheet(
            title = sheet.title,
            initialItems = sheet.initialItems,
            browseId = sheet.browseId,
            params = sheet.params,
            onFetchFullItems = onFetchSectionItems,
            onPlay = onPlay,
            onPlayItem = playCatalogItem,
            onOpen = onOpenDetail,
            onDismiss = { activeSectionSheet = null },
        )
    }

    val completedDownloads =
        remember(downloads) {
            downloads.filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotBlank() }
        }
    val downloadedTracks: List<Track> =
        remember(completedDownloads) { completedDownloads.map { it.track } }
    val effectiveOffline = isOffline

    // Featured hero items: high-impact showcase items from catalog, playlists, albums, and tracks
    val featuredHeroItems =
        remember(state, library) {
            val fromState =
                if (state is LoadState.Content) {
                    state.value
                        .flatMap { it.items }
                        .filterNot { library.isItemHidden(it) }
                        .filter { it.artworkUrl.isNotBlank() }
                        .distinctBy(::homeItemKey)
                } else emptyList()
            val fromPlaylists = library.savedPlaylists.filterNot { library.isPlaylistHidden(it.id) }.map { CatalogItem.Collection(it) }
            val fromAlbums = library.savedAlbums.map { CatalogItem.Record(it) }
            val fromTracks = library.likedTracks.map { CatalogItem.Song(it) }
            (fromState + fromPlaylists + fromAlbums + fromTracks).distinctBy(::homeItemKey).take(6)
        }

    fun seeAll(title: String, items: List<CatalogItem>, browseId: String = "", params: String = ""): () -> Unit =
        { activeSectionSheet = FoldableSheetState(title, items, browseId, params) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FoldableChromeHeight),
    ) {
        // Full Bleed Hero Banner
        if (featuredHeroItems.isNotEmpty()) {
            item {
                FoldableFullBleedHero(
                    items = featuredHeroItems,
                    auth = auth,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = {
                        selectedCategory = if (selectedCategory == it) uiText("All", "Todo") else it
                    },
                    onSearch = onSearch,
                    onProfile = onOpenProfile,
                    onPlay = playCatalogItem,
                    onClick = openCatalogItem,
                )
            }
        } else {
            // Fallback Masthead if library/state is entirely empty
            item {
                FoldableMasthead(
                    auth = auth,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = {
                        selectedCategory = if (selectedCategory == it) uiText("All", "Todo") else it
                    },
                    onSearch = onSearch,
                    onProfile = onOpenProfile,
                )
            }
        }

        // Offline Mode Banner
        if (effectiveOffline) {
            item {
                FoldableOfflineBanner(downloadedTracks.size)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (!effectiveOffline && state is LoadState.Error) {
            item { dev.sfg.numa.mobile.ui.screens.HomeLoadFailure(auth, state.message, onSignIn, onRefresh) }
        }
        // Loading shimmer state
        if (state is LoadState.Loading) {
            item { HomeSectionShimmer(title = "Loading...") }
        }

        // Content Sections filtered dynamically by selected category
        val layoutConfig =
            if (effectiveOffline) settings.homeLayoutOffline else settings.homeLayoutOnline

        layoutConfig.forEach { config ->
            if (!config.enabled) return@forEach

            when (config.section) {
                BuiltInHomeSection.YOUR_PLAYLISTS -> {
                    if (effectiveOffline) return@forEach
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Playlists", "Listas de reproducción"))) return@forEach
                    val playlistItems =
                        library.savedPlaylists
                            .filterNot { library.isPlaylistHidden(it.id) }
                            .map { CatalogItem.Collection(it) }
                            .ifEmpty { extractItemsOfKind<CatalogItem.Collection>(state, library) }
                            .distinctBy { it.stableId }

                    if (playlistItems.isNotEmpty()) {
                        foldableRail(uiText("Your Playlists", "Tus listas"), playlistItems, seeAll(uiText("Your Playlists", "Tus listas"), playlistItems), openCatalogItem)
                    }
                }

                BuiltInHomeSection.SUBSCRIBED_ARTISTS -> {
                    if (effectiveOffline) return@forEach
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Artists", "Artistas"))) return@forEach
                    val artistItems =
                        library.savedArtists
                            .map { CatalogItem.Performer(it) }
                            .ifEmpty { extractItemsOfKind<CatalogItem.Performer>(state) }
                            .distinctBy { it.stableId }

                    if (artistItems.isNotEmpty()) {
                        val artistTitle = if (library.savedArtists.isNotEmpty()) uiText("Artists you follow", "Artistas que sigues") else uiText("Discover artists", "Descubre artistas")
                        foldableRail(artistTitle, artistItems, seeAll(artistTitle, artistItems), openCatalogItem, cardWidth = 150.dp)
                    }
                }

                BuiltInHomeSection.TOP_SONGS -> {
                    if (effectiveOffline) return@forEach
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Songs", "Canciones"))) return@forEach
                    val songItems =
                        library.mostPlayed
                            .ifEmpty { library.likedTracks }
                            .ifEmpty { library.recentlyPlayed }
                            .distinctBy { it.id }
                            .map { CatalogItem.Song(it) }

                    if (songItems.isNotEmpty()) {
                        foldableRail(uiText("Top Songs", "Canciones destacadas"), songItems, seeAll(uiText("Top Songs", "Canciones destacadas"), songItems), openCatalogItem)
                    }
                }

                BuiltInHomeSection.RECOMMENDATIONS -> {
                    if (effectiveOffline) return@forEach
                    if (selectedCategory != uiText("All", "Todo")) return@forEach
                    if (state is LoadState.Content) {
                        state.value.forEach { section ->
                            val visibleItems = section.items.filterNot { library.isItemHidden(it) }
                            if (visibleItems.isNotEmpty()) {
                                foldableRail(
                                    section.title, visibleItems,
                                    seeAll(section.title, visibleItems, section.browseId, section.params),
                                    openCatalogItem, sectionKey = section.id,
                                )
                            }
                        }
                    }
                }

                BuiltInHomeSection.DOWNLOADED_PLAYLISTS -> {
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Playlists", "Listas de reproducción"))) return@forEach
                    val downloadedPlaylists =
                        library.savedPlaylists
                            .filter { playlist ->
                                playlist.tracks.any { it.id in downloadedTrackIds }
                            }
                            .map { CatalogItem.Collection(it) }

                    if (downloadedPlaylists.isNotEmpty()) {
                        foldableRail(uiText("Downloaded Playlists", "Listas descargadas"), downloadedPlaylists, seeAll(uiText("Downloaded Playlists", "Listas descargadas"), downloadedPlaylists), openCatalogItem)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_ARTISTS -> {
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Artists", "Artistas"))) return@forEach
                    val downloadedArtists =
                        library.savedArtists
                            .filter { artist ->
                                downloadedTracks.any {
                                    it.artist.equals(artist.name, ignoreCase = true)
                                }
                            }
                            .map { CatalogItem.Performer(it) }

                    if (downloadedArtists.isNotEmpty()) {
                        foldableRail(uiText("Downloaded Artists", "Artistas descargados"), downloadedArtists, seeAll(uiText("Downloaded Artists", "Artistas descargados"), downloadedArtists), openCatalogItem, cardWidth = 150.dp)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_ALBUMS -> {
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Albums", "Álbumes"))) return@forEach
                    val downloadedAlbums =
                        library.savedAlbums
                            .filter { album ->
                                downloadedTracks.any {
                                    it.albumId == album.id ||
                                        it.album.equals(album.title, ignoreCase = true)
                                }
                            }
                            .map { CatalogItem.Record(it) }

                    if (downloadedAlbums.isNotEmpty()) {
                        foldableRail(uiText("Downloaded Albums", "Álbumes descargados"), downloadedAlbums, seeAll(uiText("Downloaded Albums", "Álbumes descargados"), downloadedAlbums), openCatalogItem)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_SONGS -> {
                    if (selectedCategory !in listOf(uiText("All", "Todo"), uiText("Songs", "Canciones"))) return@forEach
                    if (downloadedTracks.isNotEmpty()) {
                        val downloadedSongItems = downloadedTracks.map { CatalogItem.Song(it) }
                        foldableRail(uiText("Downloaded Songs", "Canciones descargadas"), downloadedSongItems, seeAll(uiText("Downloaded Songs", "Canciones descargadas"), downloadedSongItems), openCatalogItem)
                    }
                }
            }
        }
    }
}
