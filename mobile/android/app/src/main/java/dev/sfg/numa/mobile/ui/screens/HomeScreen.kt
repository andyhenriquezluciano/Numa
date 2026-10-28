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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.download.DownloadItem
import dev.sfg.numa.mobile.download.DownloadStatus
import dev.sfg.numa.mobile.library.isItemHidden
import dev.sfg.numa.mobile.library.isPlaylistHidden
import dev.sfg.numa.mobile.model.Artist
import dev.sfg.numa.mobile.model.BuiltInHomeSection
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.CatalogSection
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.model.Playlist
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.components.CatalogSectionBottomSheet
import dev.sfg.numa.mobile.ui.components.HomeSectionShimmer
import dev.sfg.numa.mobile.ui.components.MessagePanel
import dev.sfg.numa.mobile.ui.components.NumaChromeHeight
import dev.sfg.numa.mobile.ui.theme.CanopyColors

internal data class QuickGridItem(
    val id: String,
    val title: String,
    val artworkUrl: String,
    val icon: ImageVector? = null,
    val gradient: Brush? = null,
    val onClick: () -> Unit,
    val onPlay: () -> Unit,
)

data class HomeSectionSheetState(
    val title: String,
    val initialItems: List<CatalogItem>,
    val browseId: String = "",
    val params: String = "",
)

@Composable
fun HomeScreen(
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
    if (dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout()) {
        dev.sfg.numa.mobile.ui.foldable.FoldableHomeScreen(
            settings = settings,
            state = state,
            library = library,
            auth = auth,
            downloads = downloads,
            downloadedTrackIds = downloadedTrackIds,
            isOffline = isOffline,
            onRefresh = onRefresh,
            onSearch = onSearch,
            onLibrary = onLibrary,
            onPlay = onPlay,
            onOpenDetail = onOpenDetail,
            onEditLayout = onEditLayout,
            onToggleLike = onToggleLike,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onShare = onShare,
            onOpenProfile = onOpenProfile,
            onSignIn = onSignIn,
            onFetchSectionItems = onFetchSectionItems,
            onPlayItem = onPlayItem,
            onPlayCollection = onPlayCollection,
        )
        return
    }

    var activeSectionSheet by remember { mutableStateOf<HomeSectionSheetState?>(null) }

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

    val offlinePlaylistItems = remember(downloadedTracks, downloadedTrackIds, library.savedPlaylists) {
        offlinePlaylistItems(downloadedTracks, downloadedTrackIds, library.savedPlaylists)
    }
    val offlineArtistItems = remember(downloadedTracks, library.savedArtists) {
        offlineArtistItems(downloadedTracks, library.savedArtists)
    }
    val offlineAlbumItems = remember(downloadedTracks) { offlineAlbumItems(downloadedTracks) }
    val spotlight = remember(state, library) { discoverEntries(state, library) }
    val quickGridItems = remember(library, state) {
        quickGridItems(library, state, onOpenDetail, openCatalogItem, playCatalogItem)
    }

    fun seeAll(title: String, items: List<CatalogItem>, browseId: String = "", params: String = ""): () -> Unit =
        { activeSectionSheet = HomeSectionSheetState(title, items, browseId, params) }

    // Card style shared by several rails.
    val collectionCard: @Composable (CatalogItem) -> Unit = { item ->
        GlassRecentlyPlayedCard(item = item, onClick = { openCatalogItem(item) })
    }
    val artistCard: @Composable (CatalogItem) -> Unit = { item ->
        CircularArtistCard(item = item, onClick = { openCatalogItem(item) })
    }
    val rankedRow: @Composable (Int, Track) -> Unit = { index, track ->
        RankedSongRow(
            rank = index + 1,
            track = track,
            liked = library.likedTracks.any { it.id == track.id },
            onPlay = { onPlay(track) },
            onToggleLike = { onToggleLike(track) },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onShare = onShare,
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NumaChromeHeight),
        ) {
            // Masthead. Greets you, judges your 3am listening silently.
            item {
                GlassHomeHeader(auth = auth, onSearch = onSearch, onProfile = onOpenProfile)
            }

            if (quickGridItems.isNotEmpty() && !effectiveOffline) {
                item {
                    SpotifyQuickGrid(items = quickGridItems)
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (!effectiveOffline && spotlight.isNotEmpty()) {
                item {
                    DiscoverCarousel(
                        title = uiText("Your daily discover", "Tu descubrimiento diario"),
                        entries = spotlight,
                        onPlayAll = { playCatalogItem(spotlight.first().item) },
                        onClick = openCatalogItem,
                    )
                }
            }

            if (effectiveOffline) {
                item {
                    OfflineModeBanner(downloadedTracks.size)
                    Spacer(Modifier.height(14.dp))
                }
            }

            val layoutConfig =
                if (effectiveOffline) settings.homeLayoutOffline else settings.homeLayoutOnline

            layoutConfig.forEach { config ->
                if (!config.enabled) return@forEach

                when (config.section) {
                    BuiltInHomeSection.YOUR_PLAYLISTS -> {
                        if (effectiveOffline) return@forEach
                        val playlistItems =
                            library.savedPlaylists
                                .filterNot { library.isPlaylistHidden(it.id) }
                                .map { CatalogItem.Collection(it) }
                                .ifEmpty { extractItemsOfKind<CatalogItem.Collection>(state, library) }
                                .distinctBy { it.stableId }
                        if (playlistItems.isNotEmpty()) {
                            val openAll = seeAll(uiText("Your Playlists", "Tus listas"), playlistItems)
                            homeRail(
                                uiText("Your Playlists", "Tus listas"), playlistItems, "pl", openAll,
                                header = { AccountSectionTitle(uiText("Your playlists", "Tus listas"), auth, openAll) },
                                card = collectionCard,
                            )
                        }
                    }
                    BuiltInHomeSection.SUBSCRIBED_ARTISTS -> {
                        if (effectiveOffline) return@forEach
                        val artistItems =
                            library.savedArtists
                                .map { CatalogItem.Performer(it) }
                                .ifEmpty { extractItemsOfKind<CatalogItem.Performer>(state) }
                                .distinctBy { it.stableId }
                        if (artistItems.isNotEmpty()) {
                            homeRail(
                                uiText("Keep listening", "Sigue escuchando"), artistItems, "art", seeAll(uiText("Keep listening", "Sigue escuchando"), artistItems),
                                subtitle = if (library.savedArtists.isNotEmpty()) uiText("Artists you follow", "Artistas que sigues")
                                    else uiText("Recommended artists", "Artistas recomendados para ti"), spacing = 16.dp, card = artistCard,
                            )
                        }
                    }
                    BuiltInHomeSection.TOP_SONGS -> {
                        if (effectiveOffline) return@forEach
                        val songTracks =
                            library.mostPlayed
                                .ifEmpty { library.likedTracks }
                                .ifEmpty { library.recentlyPlayed }
                                .ifEmpty {
                                    extractItemsOfKind<CatalogItem.Song>(state).map { it.track }
                                }
                                .distinctBy { it.id }
                        if (songTracks.isNotEmpty()) {
                            homeTrackSection(
                                uiText("Latest Songs", "Canciones recientes"), songTracks.take(5), "top",
                                seeAll(uiText("Latest Songs", "Canciones recientes"), songTracks.map { CatalogItem.Song(it) }),
                                subtitle = uiText("Top picks and recent favorites", "Selecciones y favoritos recientes"), row = rankedRow,
                            )
                        }
                    }
                    BuiltInHomeSection.RECOMMENDATIONS -> {
                        if (effectiveOffline) return@forEach
                        when (state) {
                            is LoadState.Content -> {
                                state.value.forEachIndexed { sectionIndex, section ->
                                    val visibleItems = section.items.filterNot { library.isItemHidden(it) }
                                    if (visibleItems.isNotEmpty()) {
                                        homeRail(
                                            section.title, visibleItems, section.id,
                                            seeAll(section.title, visibleItems, section.browseId, section.params),
                                            sectionKey = section.id,
                                        ) { item ->
                                            // Alternate card styles so consecutive rails read differently.
                                            if (sectionIndex % 2 == 1) {
                                                GlassSquircleCard(item = item, onClick = { openCatalogItem(item) }, onPlay = { playCatalogItem(item) })
                                            } else {
                                                GlassRecentlyPlayedCard(item = item, onClick = { openCatalogItem(item) })
                                            }
                                        }
                                    }
                                }
                            }
                            LoadState.Loading -> {
                                item { HomeSectionShimmer(uiText("Recommendations", "Recomendaciones")) }
                            }
                            is LoadState.Error -> {
                                item { HomeLoadFailure(auth, state.message, onSignIn, onRefresh) }
                            }
                            is LoadState.Empty -> {
                                item {
                                    MessagePanel("A quiet numa", state.message, uiText("Refresh", "Actualizar"), onRefresh)
                                }
                            }
                            else -> Unit
                        }
                    }
                    BuiltInHomeSection.DOWNLOADED_PLAYLISTS -> {
                        if (!effectiveOffline) return@forEach
                        if (offlinePlaylistItems.isNotEmpty()) {
                            homeRail(
                                uiText("Downloaded Playlists", "Listas descargadas"), offlinePlaylistItems, "off_pl",
                                seeAll(uiText("Downloaded Playlists", "Listas descargadas"), offlinePlaylistItems), card = collectionCard,
                            )
                        }
                    }
                    BuiltInHomeSection.DOWNLOADED_ARTISTS -> {
                        if (!effectiveOffline) return@forEach
                        if (offlineArtistItems.isNotEmpty()) {
                            homeRail(
                                uiText("Downloaded Artists", "Artistas descargados"), offlineArtistItems, "off_art",
                                seeAll(uiText("Downloaded Artists", "Artistas descargados"), offlineArtistItems), spacing = 16.dp, card = artistCard,
                            )
                        }
                    }
                    BuiltInHomeSection.DOWNLOADED_ALBUMS -> {
                        if (!effectiveOffline) return@forEach
                        if (offlineAlbumItems.isNotEmpty()) {
                            homeRail(
                                uiText("Downloaded Albums", "Álbumes descargados"), offlineAlbumItems, "off_alb",
                                seeAll(uiText("Downloaded Albums", "Álbumes descargados"), offlineAlbumItems),
                            ) { item ->
                                GlassRecentlyPlayedCard(item = item, onClick = { openCatalogItem(item) })
                            }
                        }
                    }
                    BuiltInHomeSection.DOWNLOADED_SONGS -> {
                        if (!effectiveOffline) return@forEach
                        if (downloadedTracks.isNotEmpty()) {
                            homeTrackSection(
                                uiText("Downloaded Songs", "Canciones descargadas"), downloadedTracks, "off_trk",
                                seeAll(uiText("Downloaded Songs", "Canciones descargadas"), downloadedTracks.map { CatalogItem.Song(it) }),
                                row = rankedRow,
                            )
                        } else {
                            item {
                                MessagePanel(
                                    title = uiText("No downloaded music", "No hay música descargada"),
                                    message =
                                        uiText("You are currently offline. Connect to the internet to stream music, or download tracks to listen offline.", "No tienes conexión. Conéctate a Internet para reproducir música o descarga canciones para escucharlas sin conexión."),
                                    actionLabel = uiText("Try reconnecting", "Intentar reconectar"),
                                    onAction = onRefresh,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private inline fun <reified T : CatalogItem> extractItemsOfKind(
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot? = null,
): List<T> {
    if (state !is LoadState.Content) return emptyList()
    val items = state.value.flatMap { it.items }.filterIsInstance<T>()
    return if (library != null) items.filterNot { library.isItemHidden(it) } else items
}

internal fun catalogItemBadge(item: CatalogItem): String =
    when (item) {
        is CatalogItem.Song -> "SONG"
        is CatalogItem.Record -> "ALBUM"
        is CatalogItem.Performer -> "ARTIST"
        is CatalogItem.Collection -> {
            if (item.playlist.title.contains("mix", ignoreCase = true) ||
                item.playlist.title.contains("radio", ignoreCase = true)
            ) {
                "MIX"
            } else {
                "PLAYLIST"
            }
        }
        is CatalogItem.Category -> "CATEGORY"
    }

internal fun catalogSubtitle(item: CatalogItem): String =
    when (item) {
        is CatalogItem.Song -> item.track.artist
        is CatalogItem.Record -> item.album.artist
        is CatalogItem.Performer -> dev.sfg.numa.mobile.ui.text.catalogCaption(item.artist.subtitle).ifBlank { uiText("Artist", "Artista") }
        is CatalogItem.Collection -> dev.sfg.numa.mobile.ui.text.playlistDisplayAuthor(item.playlist)
            .ifBlank { uiText("Playlists", "Listas de reproducción") }
        is CatalogItem.Category -> ""
    }

private fun openItem(item: CatalogItem, play: (Track) -> Unit, detail: (String) -> Unit) {
    when (item) {
        is CatalogItem.Song -> play(item.track)
        else -> detail(item.stableId)
    }
}
