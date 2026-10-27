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

package dev.sfg.numa.mobile.app
import dev.sfg.numa.mobile.library.containsPlaylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.LocalMaxActive
import dev.sfg.numa.mobile.model.CatalogKind
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.ui.components.PlaylistPickerSheet
import dev.sfg.numa.mobile.ui.motion.NavMotion
import dev.sfg.numa.mobile.ui.navigation.Routes
import dev.sfg.numa.mobile.ui.screens.DetailScreen
import dev.sfg.numa.mobile.ui.screens.HomeScreen
import dev.sfg.numa.mobile.ui.screens.LibraryScreen
import dev.sfg.numa.mobile.ui.screens.NativeLoginScreen
import dev.sfg.numa.mobile.ui.screens.SettingsScreen
import dev.sfg.numa.mobile.ui.screens.SettingsHomeLayout

@Composable
internal fun NumaNavigation(
    nav: NavHostController,
    viewModel: NumaViewModel,
    playback: dev.sfg.numa.mobile.model.PlaybackSnapshot,
    targets: dev.sfg.numa.mobile.model.PlaybackTargetState,
    library: dev.sfg.numa.mobile.model.LibrarySnapshot,
    settings: dev.sfg.numa.mobile.model.NumaSettings,
    onOpenSearch: () -> Unit,
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val detailRefreshing by viewModel.detailRefreshing.collectAsStateWithLifecycle()
    val detailArtwork by viewModel.detailArtwork.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val artistImages by viewModel.artistImages.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val discordStatus by viewModel.discordStatus.collectAsStateWithLifecycle()
    val lastfmState by viewModel.lastfmState.collectAsStateWithLifecycle()
    val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val localSnapshot by viewModel.localSnapshot.collectAsStateWithLifecycle()
    val localBusy by viewModel.localLibrary.busy.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadsList = androidx.compose.runtime.remember(downloads) { downloads.values.toList() }
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsStateWithLifecycle()
    val totalBytesUsed by viewModel.totalBytesUsed.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
    val qobuzStatus by viewModel.qobuzStatus.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    // Connect targets accept every queue command.
    val canControlQueue = true
    val canShuffle = true
    val context = androidx.compose.ui.platform.LocalContext.current
    var playlistPickerTrack by remember { mutableStateOf<dev.sfg.numa.mobile.model.Track?>(null) }

    // Without this a track the resolver refuses shows up only as the spinner stopping,
    // which is indistinguishable from the play button having died. Keyed on the message
    // so a repeated attempt on the same track says so again rather than staying silent.
    LaunchedEffect(playback.errorMessage) {
        if (playback.errorMessage.isNotBlank()) {
            android.widget.Toast
                .makeText(context, playback.errorMessage, android.widget.Toast.LENGTH_LONG)
                .show()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = NavMotion.enter,
        exitTransition = NavMotion.exit,
        popEnterTransition = NavMotion.popEnter,
        popExitTransition = NavMotion.popExit,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                state = home,
                library = library,
                auth = auth,
                downloads = downloadsList,
                downloadedTrackIds = downloadedTrackIds,
                isOffline = !isOnline,
                onRefresh = viewModel::refreshHome,
                onSearch = onOpenSearch,
                onLibrary = { filter ->
                    viewModel.selectLibraryFilter(filter)
                    nav.openTopLevel(Routes.LIBRARY)
                },
                onPlay = { viewModel.play(it, "Home") },
                onOpenDetail = { id -> viewModel.openDetail(id); nav.navigate(Routes.detail(id)) },
                onEditLayout = { nav.navigate(Routes.SETTINGS_HOME_LAYOUT) },
                onToggleLike = viewModel::toggleLiked,
                onPlayNext = if (canControlQueue) viewModel::playNext else null,
                onAddToQueue = if (canControlQueue) viewModel::addToQueue else null,
                onAddToPlaylist = { playlistPickerTrack = it },
                onShare = viewModel::shareTrack,
                onOpenProfile = { nav.openTopLevel(Routes.SETTINGS) },
                onSignIn = { nav.navigate(Routes.LOGIN) { launchSingleTop = true } },
                onFetchSectionItems = viewModel::fetchSectionItems,
                onPlayItem = viewModel::playItem,
                onPlayCollection = { id, title -> viewModel.playCollection(id, title) },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                library = library,
                filter = libraryFilter,
                onFilterChange = viewModel::selectLibraryFilter,
                downloads = downloadsList,
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                totalBytesUsed = totalBytesUsed,
                onPlay = { viewModel.play(it, libraryFilter.sourceTitle()) },
                onPlayNext = if (canControlQueue) viewModel::playNext else null,
                onAddToQueue = if (canControlQueue) viewModel::addToQueue else null,
                onOpenDetail = { id -> viewModel.openDetail(id); nav.navigate(Routes.detail(id)) },
                onDownloadTrack = viewModel::downloadTrack,
                onRemoveDownloadTrack = viewModel::removeDownload,
                onShare = viewModel::shareTrack,
                localLibrary = viewModel.localLibrary,
                localSnapshot = localSnapshot,
                localBusy = localBusy,
                youtubeAvailable = auth is dev.sfg.numa.mobile.auth.AuthState.SignedIn,
                onCreateYouTubePlaylist = { title -> viewModel.createPlaylist(title, null) },
            )
        }
        composable(Routes.DOWNLOADS) {
            dev.sfg.numa.mobile.ui.screens.DownloadsScreen(
                downloads = downloadsList,
                totalBytesUsed = totalBytesUsed,
                onPlay = { viewModel.play(it, "Downloads") },
                onRemoveDownload = viewModel::removeDownload,
            )
        }
        composable(Routes.ROOMS) {
            val party by viewModel.listeningParty.collectAsStateWithLifecycle()
            dev.sfg.numa.mobile.ui.screens.RoomsScreen(
                party = party,
                onCreate = { viewModel.createListeningParty() },
                onJoin = { viewModel.joinListeningParty(it) },
                onLeave = viewModel::leaveListeningParty,
                onTransferHost = viewModel::transferListeningPartyHost,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                auth = auth,
                discordStatus = discordStatus,
                lastfmState = lastfmState,
                listenBrainzState = listenBrainzState,
                updateState = updateState,
                cacheSizeBytes = cacheSizeBytes,
                isClearingCache = isClearingCache,
                onClearCache = {
                    viewModel.clearCache { bytesCleared ->
                        val formatted = dev.sfg.numa.mobile.settings.CacheManager.formatStorageSize(bytesCleared)
                        android.widget.Toast.makeText(context, "Cache cleared ($formatted freed)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                downloadedBytes = totalBytesUsed,
                onDeleteAllDownloads = {
                    viewModel.removeAllDownloads()
                    android.widget.Toast.makeText(context, "Downloads deleted", android.widget.Toast.LENGTH_SHORT).show()
                },
                onRefreshCacheSize = viewModel::refreshCacheSize,
                onSettings = viewModel::updateSettings,
                onAutoplayEnabled = viewModel::setAutoplayEnabled,
                onSignIn = { nav.navigate(Routes.LOGIN) },
                onSwitchAccount = { nav.navigate(Routes.ACCOUNT_SWITCH) },
                onSignOut = viewModel::signOut,
                onConnectLastfm = { viewModel.connectLastfm(context) },
                onCompleteLastfm = viewModel::completeLastfmConnection,
                onDisconnectLastfm = viewModel::disconnectLastfm,
                onConnectListenBrainz = viewModel::connectListenBrainz,
                onDisconnectListenBrainz = viewModel::disconnectListenBrainz,
                onConnectSpotify = { nav.navigate(Routes.SPOTIFY_LOGIN) },
                qobuzStatus = qobuzStatus,
                onConnectQobuz = { nav.navigate(Routes.QOBUZ_LOGIN) },
                onDisconnectQobuz = viewModel::disconnectQobuz,
                onQobuzEnabledChange = viewModel::setQobuzEnabled,
                onQobuzQualityChange = viewModel::setQobuzQuality,
                onCheckForUpdates = viewModel::checkForUpdates,
                onInstallUpdate = viewModel::installUpdate,
                onHomeLayout = { nav.navigate(Routes.SETTINGS_HOME_LAYOUT) },
            )
        }
        composable(Routes.SETTINGS_HOME_LAYOUT) {
            SettingsHomeLayout(
                settings = settings,
                auth = auth,
                onSettings = viewModel::updateSettings,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LOGIN) {
            NativeLoginScreen(
                auth = auth,
                onBegin = viewModel::beginSignIn,
                onSession = { cookie, visitorData, dataSyncId, accountIndex, avatarUrl ->
                    viewModel.completeSignIn(cookie, visitorData, dataSyncId, accountIndex, pageAvatarUrl = avatarUrl)
                },
                onCancel = viewModel::cancelSignIn,
                // Scoped to this entry so a repeated call cannot pop whatever
                // sent the user here. From Welcome that would be the whole back
                // stack, leaving an empty NavHost and a black screen.
                onComplete = { nav.popBackStack(Routes.LOGIN, inclusive = true) },
            )
        }
        composable(Routes.ACCOUNT_SWITCH) {
            NativeLoginScreen(
                auth = auth,
                onBegin = viewModel::beginSignIn,
                onSession = { cookie, visitorData, dataSyncId, accountIndex, avatarUrl ->
                    viewModel.completeSignIn(cookie, visitorData, dataSyncId, accountIndex, switchingAccount = true, pageAvatarUrl = avatarUrl)
                },
                onCancel = viewModel::cancelSignIn,
                onComplete = { nav.popBackStack(Routes.ACCOUNT_SWITCH, inclusive = true) },
                switchingAccount = true,
                initialSession = viewModel.youtubeSession(),
            )
        }
        composable(Routes.SPOTIFY_LOGIN) {
            dev.sfg.numa.mobile.ui.screens.SpotifyLoginScreen(
                onSpdcCaptured = { spdc ->
                    viewModel.updateSettings(settings.copy(spotifySpdc = spdc))
                    nav.popBackStack()
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(Routes.QOBUZ_LOGIN) {
            val graph = NumaGraph.from(context)
            dev.sfg.numa.mobile.ui.screens.QobuzLoginScreen(
                qobuz = graph.qobuzResolver,
                onSuccess = { token, userId ->
                    viewModel.connectQobuz(token, userId)
                    nav.popBackStack()
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            // A restored navigation stack may recreate this screen after the
            // process state holder has been lost; reload its actual route id.
            androidx.compose.runtime.LaunchedEffect(id) {
                val current = (detail as? LoadState.Content)?.value?.id
                if (id.isNotBlank() && current != id) viewModel.openDetail(id)
            }
            val isSaved = (detail as? LoadState.Content)?.value?.let { detailVal ->
                when (detailVal.kind) {
                    CatalogKind.ALBUM -> library.savedAlbums.any { it.id == detailVal.id }
                    CatalogKind.PLAYLIST -> library.containsPlaylist(detailVal.id)
                    CatalogKind.ARTIST -> library.savedArtists.any { it.id == detailVal.id }
                    CatalogKind.TRACK -> false
                }
            } ?: false

            // The hero is square, so the wide asset crops far better than the 9:16 one the
            // full player wants; vertical is only a fallback when there is nothing else.
            val animatedArtworkUrl = if (settings.animatedArtwork) {
                detailArtwork?.let { it.videoUrl.ifBlank { it.videoUrlVertical } }.orEmpty()
            } else ""

            // Only artist pages swap in TheAudioDB's photograph; albums keep their cover, which
            // also drives the page palette.
            val portrait = artistImages?.portraitUrl.orEmpty()
            val shownDetail = (detail as? LoadState.Content)
                ?.takeIf { portrait.isNotBlank() && it.value.kind == CatalogKind.ARTIST }
                ?.let { LoadState.Content(it.value.copy(artworkUrl = portrait)) }
                ?: detail

            // A playlist on this phone also edits its songs, cover and name from the detail page.
            val sourcePlaylistId = (detail as? LoadState.Content)?.value?.takeIf { it.kind == CatalogKind.PLAYLIST }?.id.orEmpty()
            val localDetail = (detail as? LoadState.Content)?.value?.takeIf { dev.sfg.numa.mobile.local.isLocalPlaylistId(it.id) }
            val localEdit = localDetail?.let { current ->
                dev.sfg.numa.mobile.ui.screens.rememberLocalPlaylistEdit(
                    repository = viewModel.localLibrary,
                    playlistId = current.id,
                    hasCustomCover = localSnapshot.playlists.firstOrNull { it.id == current.id }?.let {
                        !it.defaultArtwork && (it.coverPath.isNotBlank() || it.animatedPath.isNotBlank())
                    } == true,
                    onDeleted = nav::popBackStack,
                )
            }

            DetailScreen(
                state = shownDetail,
                playback = playback,
                onTogglePlayback = viewModel::togglePlayback,
                localEdit = localEdit,
                onDeletePlaylist = { id -> viewModel.deletePlaylist(id) { nav.popBackStack() } },
                onBack = nav::popBackStack,
                onPlayAll = { tracks, source ->
                    if (localDetail != null) viewModel.playLocalPlaylist(localDetail.id, tracks)
                    else viewModel.playAll(tracks, contextTitle = source, playlistId = sourcePlaylistId)
                },
                onShuffle = { tracks, source ->
                    if (localDetail != null) viewModel.playLocalPlaylist(localDetail.id, tracks, shuffle = true)
                    else viewModel.shuffleAll(tracks, source, playlistId = sourcePlaylistId)
                },
                shuffleAvailable = canShuffle,
                onPlay = { track, source -> viewModel.play(track, source) },
                onPlayTrack = { tracks, index, source ->
                    if (localDetail != null) viewModel.playLocalPlaylist(localDetail.id, tracks, startIndex = index)
                    else viewModel.playAll(tracks, startIndex = index, contextTitle = source, playlistId = sourcePlaylistId)
                },
                onPlayNext = if (canControlQueue) viewModel::playNext else null,
                onAddToQueue = if (canControlQueue) viewModel::addToQueue else null,
                onAddToPlaylist = { playlistPickerTrack = it },
                onRemoveFromPlaylist = viewModel::removeTrackFromCurrentPlaylist,
                onRemoveSelected = viewModel::removeSelectedFromCurrentPlaylist,
                onMovePlaylistTrack = viewModel::moveTrackInCurrentPlaylist,
                onSave = viewModel::saveDetail,
                onOpenDetail = { next -> viewModel.openDetail(next); nav.navigate(Routes.detail(next)) },
                isSaved = isSaved,
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                onDownloadTrack = viewModel::downloadTrack,
                onDownloadTracks = viewModel::downloadTracks,
                onRemoveDownloadTrack = viewModel::removeDownload,
                onRemoveDownloadTracks = viewModel::removeDownloads,
                animatedArtworkUrl = animatedArtworkUrl,
                artistPortraitUrl = artistImages?.portraitUrl.orEmpty(),
                onShareTrack = viewModel::shareTrack,
                onShareCollection = viewModel::shareCollection,
                onFetchSectionItems = viewModel::fetchSectionItems,
                smartCrossfadeEnabled = settings.smartCrossfade && !LocalMaxActive.current,
                onPlayBestMix = { tracks, title, progress, complete ->
                    if (localDetail != null) viewModel.playLocalPlaylist(localDetail.id, tracks, bestMix = true)
                    else viewModel.playBestMix(tracks, title, progress, complete, playlistId = sourcePlaylistId)
                },
                isRefreshing = detailRefreshing,
                onRefresh = viewModel::refreshDetail,
            )
        }
    }

    playlistPickerTrack?.let { track ->
        PlaylistPickerSheet(
            track = track,
            playlists = viewModel.playlistChoices(track, library.savedPlaylists),
            onDismiss = { playlistPickerTrack = null },
            onSelect = { playlist ->
                playlistPickerTrack = null
                viewModel.addTrackToPlaylist(playlist.id, track)
            },
        )
    }
}
