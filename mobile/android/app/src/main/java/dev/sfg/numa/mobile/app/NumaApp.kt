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

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.LibraryFilter
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.ui.components.ArtworkBackdrop
import dev.sfg.numa.mobile.ui.components.CanopyReadout
import dev.sfg.numa.mobile.ui.components.NumaBottomBar
import dev.sfg.numa.mobile.ui.components.SongShareBottomSheet
import dev.sfg.numa.mobile.ui.components.rememberArtworkPalette
import dev.sfg.numa.mobile.ui.components.LocalPlayerClock
import dev.sfg.numa.mobile.ui.components.rememberHandoffMarker
import dev.sfg.numa.mobile.ui.components.rememberPlayerPresentation
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.glass.LocalGlassScene
import dev.sfg.numa.mobile.ui.glass.glassSceneSource
import dev.sfg.numa.mobile.ui.glass.glassWashSource
import dev.sfg.numa.mobile.ui.glass.rememberGlassScene
import dev.sfg.numa.mobile.ui.glass.GlassCapture
import dev.sfg.numa.mobile.ui.glass.LocalGlassCapture
import dev.sfg.numa.mobile.ui.glass.rememberGlassAnimationClock
import dev.sfg.numa.mobile.ui.glass.rememberGlassStyle
import dev.sfg.numa.mobile.ui.navigation.Routes
import dev.sfg.numa.mobile.ui.scroll.NumaScrollPhysics
import dev.sfg.numa.mobile.ui.theme.LocalAccent

@Composable
fun NumaApp(viewModel: NumaViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // Structural playback only; the clock is handed to the few readers that draw time.
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val playbackClock = viewModel.playbackClock.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listeningParty by viewModel.listeningParty.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()
    val warning by viewModel.warning.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val successAtTop by viewModel.successAtTop.collectAsStateWithLifecycle()
    val playerNoticeScene = rememberGlassScene(snapshotOnly = true)
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val transitionMarker by viewModel.transitionMarker.collectAsStateWithLifecycle()
    val nonMusicSegment = viewModel.nonMusicSegment.collectAsStateWithLifecycle()
    val nonMusicSkip = remember(viewModel) {
        dev.sfg.numa.mobile.ui.components.NonMusicSkip(nonMusicSegment, viewModel::skipNonMusic)
    }
    val qobuzStatus by viewModel.qobuzStatus.collectAsStateWithLifecycle()
    val bestMixJob by viewModel.bestMixJob.collectAsStateWithLifecycle()
    val maxActive by viewModel.maxActive.collectAsStateWithLifecycle()
    val albumQualityLoader: (suspend (dev.sfg.numa.mobile.model.BrowseDetail) -> dev.sfg.numa.mobile.model.QobuzAlbumQuality?)? =
        if (maxActive) viewModel::qobuzAlbumQuality else null
    // Transition plans belong to the phone's two-player engine, never a selected Connect target.
    val localTransitionMarker = rememberHandoffMarker(
        transitionMarker.takeIf { targets.selected is PlaybackTarget.LocalPhone },
        playback.currentTrack?.id,
    )
    val playerPresentation = rememberPlayerPresentation(playback, playbackClock, localTransitionMarker)
    val playerPlayback = playerPresentation.playback
    // Once the incoming song owns the mix, its scrubber is a normal full-track scrubber. The raw
    // overlap progress still drives the transition glow and artwork motion separately.
    val playerMarker = localTransitionMarker.takeUnless { playerPresentation.incomingDominant }

    // The player is an overlay, not a destination. As a destination the NavHost tore the
    // screen underneath out of the tree, so dragging the player down uncovered nothing but
    // black — and the pill it collapses into did not exist to animate towards.
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    // Search is an overlay, not a route; the nav bar's Search tab only raises it.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val chromeRoute = if (searchOpen) Routes.SEARCH else route
    val selectTopLevel: (String) -> Unit = { target ->
        if (target == Routes.SEARCH) searchOpen = true else navController.openTopLevel(target)
    }
    // Where the pill sits on screen, so the player can shrink into it rather than slide off.
    var readoutBounds by remember { mutableStateOf<Rect?>(null) }
    // And its thumbnail specifically, which the player's cover flies into.
    var readoutArtworkBounds by remember { mutableStateOf<Rect?>(null) }
    // Outlives the player so the cover can fly on the way in as well as on the way out.
    var playerCoverBounds by remember { mutableStateOf<Rect?>(null) }

    val chromeHidden = route == Routes.LOGIN || route == Routes.ACCOUNT_SWITCH || route == Routes.WELCOME || route == Routes.QOBUZ_LOGIN
    // Collection artwork runs under the status bar, so these screens take no top inset and
    // apply it themselves where the content actually needs it.
    val isDetail = route == Routes.DETAIL || route?.startsWith("detail") == true
    // Phone Home pads its own header for the status bar so rails scroll under it.
    val isPhoneHome = route == Routes.HOME && !dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout()
    val artworkUnderStatusBar = isDetail || isPhoneHome

    // When in an album / collection, sample the album cover's palette so the frosted glass
    // and bottom navigation dynamically reflect the album being viewed.
    val detailState by viewModel.detail.collectAsStateWithLifecycle()
    val detailArtwork by viewModel.detailArtwork.collectAsStateWithLifecycle()
    val collection = (detailState as? LoadState.Content)?.value
    val detailArtistImages by viewModel.artistImages.collectAsStateWithLifecycle()
    val artistPortrait = detailArtistImages?.portraitUrl?.takeIf { collection?.kind == dev.sfg.numa.mobile.model.CatalogKind.ARTIST && it.isNotBlank() }
    val resolvedDetailArtwork = artistPortrait ?: detailArtwork?.staticUrl?.takeIf { it.isNotBlank() }
        ?: (detailState as? LoadState.Content)?.value?.artworkUrl.orEmpty()
    val detailArtworkUrl = if (collection?.kind == dev.sfg.numa.mobile.model.CatalogKind.PLAYLIST)
        dev.sfg.numa.mobile.ui.components.rememberPlaylistBackdropSource(collection.id, resolvedDetailArtwork)
        else resolvedDetailArtwork

    val activeCoverUrl = if (isDetail) {
        detailArtworkUrl
    } else {
        playerPlayback.currentTrack?.artworkUrl.orEmpty()
    }

    // Sampled once here and handed to both the wash and the glass panes, so the cover the app is
    // tinted by and the cover its panes are tinted by can never disagree.
    val backdropPalette = rememberArtworkPalette(activeCoverUrl)
    val glassTint = animateColorAsState(
        targetValue = if (activeCoverUrl.isBlank()) Color(0xFFD1B8FF) else backdropPalette.accent,
        animationSpec = tween(900),
        label = "GlassTint",
    )
    val glass = rememberGlassStyle(glassTint, settings.glassTheme, settings.glassBorders)
    val glassScene = rememberGlassScene(
        animationTick = rememberGlassAnimationClock(
            settings.animatedBackground && (playerPlayback.isPlaying || isDetail) && !playerOpen),
    )
    val effectiveAccent = if (settings.glassTheme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) {
        dev.sfg.numa.mobile.ui.theme.RosePineColors.Rose
    } else {
        dev.sfg.numa.mobile.ui.theme.CanopyColors.Accent
    }
    val localLibraryUi = dev.sfg.numa.mobile.ui.components.rememberLocalLibraryUi(viewModel.localLibrary)
    // Bug reports float above navigation so a capture can wander off and come back.
    val supportUi = dev.sfg.numa.mobile.ui.support.rememberSupportUi()

    CompositionLocalProvider(
        dev.sfg.numa.mobile.ui.components.LocalLibraryUiLocal provides localLibraryUi,
        dev.sfg.numa.mobile.ui.support.LocalSupportUi provides supportUi,
        LocalGlass provides glass,
        LocalGlassScene provides glassScene,
        LocalAccent provides effectiveAccent,
        dev.sfg.numa.mobile.ui.theme.LocalGlassTheme provides settings.glassTheme,
        dev.sfg.numa.mobile.ui.theme.LocalGlassBorders provides settings.glassBorders,
        LocalOverscrollFactory provides NumaScrollPhysics.overscrollFactory,
        LocalPlayerClock provides playerPresentation.clock,
        dev.sfg.numa.mobile.ui.components.LocalNonMusicSkip provides nonMusicSkip,
        dev.sfg.numa.mobile.model.LocalBestMixJob provides bestMixJob,
        dev.sfg.numa.mobile.model.LocalQobuzLinked provides qobuzStatus.isConnected,
        dev.sfg.numa.mobile.model.LocalMaxActive provides maxActive,
        dev.sfg.numa.mobile.model.LocalQobuzAlbumQuality provides albumQualityLoader,
    ) {
        Scaffold(
            // Transparent so the artwork wash below shows through every screen.
            containerColor = Color.Transparent,
        ) { padding ->
            // Collection artwork runs under the status bar, so those screens take no top inset.
            val contentInset = if (artworkUnderStatusBar) {
                Modifier.padding(bottom = padding.calculateBottomPadding())
            } else {
                Modifier.padding(padding)
            }

            // Everything the chrome floats over is recorded here, and only here. A pane cannot
            // blur a recording it is itself part of, which is why the pill and the bar were
            // lifted out of this box and into the overlay below.
            Box(Modifier.fillMaxSize().glassSceneSource(if (playerOpen) null else glassScene)) {
                Box(Modifier.fillMaxSize().glassWashSource(if (playerOpen) null else glassScene)) {
                    CompositionLocalProvider(LocalGlassCapture provides GlassCapture.WASH) {
                        if (settings.glassTheme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(dev.sfg.numa.mobile.ui.theme.RosePineColors.Base)
                            )
                        } else {
                            ArtworkBackdrop(
                                palette = backdropPalette,
                                animated = settings.animatedBackground && !playerOpen,
                            )
                            // Home, Settings and Library wear the current song; the shader idles once playback pauses.
                            dev.sfg.numa.mobile.ui.components.HomeBackdrop(
                                visible = !playerOpen && (isDetail || route == Routes.HOME || route == Routes.SETTINGS || route == Routes.SETTINGS_HOME_LAYOUT || route == Routes.LIBRARY),
                                artworkUrl = activeCoverUrl,
                                isPlaying = (isDetail || playerPlayback.isPlaying) && settings.animatedBackground,
                            )
                        }
                    }
                }

                val isFoldable = dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout()
                val contentModifier = if (isFoldable && !chromeHidden) {
                    Modifier
                        .fillMaxSize()
                        .padding(start = dev.sfg.numa.mobile.ui.foldable.FoldableNavRailWidth)
                        .then(contentInset)
                } else {
                    Modifier.fillMaxSize().then(contentInset)
                }

                Box(contentModifier) {
                    CompositionLocalProvider(LocalGlassCapture provides GlassCapture.SCENE) {
                        NumaNavigation(navController, viewModel, playback, targets, library, settings, onOpenSearch = { searchOpen = true })
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                val isFoldable = dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout()
                if (!chromeHidden) {
                    if (isFoldable) {
                        dev.sfg.numa.mobile.ui.foldable.NumaNavigationRail(
                            currentRoute = chromeRoute,
                            onSelect = selectTopLevel,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = dev.sfg.numa.mobile.ui.foldable.FoldableNavRailWidth)
                                .then(contentInset),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            CanopyReadout(
                                dismissEnabled = !listeningParty.isActive,
                                playback = playerPlayback,
                                animatedArtworkEnabled = settings.animatedArtwork && !playerOpen,
                                transition = playerMarker,
                                mixProgress = playerPresentation.mixProgress,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                    .onGloballyPositioned { readoutBounds = it.boundsInRoot() },
                                onArtworkBounds = { readoutArtworkBounds = it },
                                onOpen = { playerOpen = true },
                                onToggle = viewModel::togglePlayback,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onClear = {
                                    if (!viewModel.listeningParty.value.isActive) {
                                        playerOpen = false
                                        viewModel.clearQueue()
                                    }
                                },
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().then(contentInset)) {
                            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                                CanopyReadout(
                                    dismissEnabled = !listeningParty.isActive,
                                    playback = playerPlayback,
                                    animatedArtworkEnabled = settings.animatedArtwork && !playerOpen,
                                    transition = playerMarker,
                                    mixProgress = playerPresentation.mixProgress,
                                    modifier = Modifier.onGloballyPositioned { readoutBounds = it.boundsInRoot() },
                                    onArtworkBounds = { readoutArtworkBounds = it },
                                    onOpen = { playerOpen = true },
                                    onToggle = viewModel::togglePlayback,
                                    onNext = viewModel::next,
                                    onPrevious = viewModel::previous,
                                    onClear = {
                                        if (!viewModel.listeningParty.value.isActive) {
                                            playerOpen = false
                                            viewModel.clearQueue()
                                        }
                                    },
                                )
                                NumaBottomBar(chromeRoute, selectTopLevel, showSettings = settings.showSettingsTab)
                            }
                        }
                    }
                }

                // Above the chrome so its scrim dims the bar too, below the player so a played
                // result can still open the full-screen view.
                SearchOverlayHost(
                    open = searchOpen,
                    onClose = { searchOpen = false },
                    nav = navController,
                    viewModel = viewModel,
                    library = library,
                    backdropArtworkUrl = playerPlayback.currentTrack?.artworkUrl.orEmpty(),
                    backdropPlaying = playerPlayback.isPlaying && settings.animatedBackground,
                    // These routes already draw this backdrop underneath.
                    showBackdrop = route != Routes.HOME && route != Routes.SETTINGS && route != Routes.LIBRARY,
                    onOpenPlayer = { playerOpen = true },
                    modifier = Modifier.zIndex(40f),
                )

                // Sibling of the padded content rather than a child of it: the player is
                // edge-to-edge and draws its own insets, and it has to paint over the pill
                // it collapses into.
                NowPlayingOverlay(
                    playerScene = playerNoticeScene,
                    open = playerOpen,
                    onOpenChange = { playerOpen = it },
                    collapseBounds = readoutBounds,
                    collapseArtworkBounds = readoutArtworkBounds,
                    restingCoverBounds = playerCoverBounds,
                    onRestingCoverBounds = { playerCoverBounds = it },
                    nav = navController,
                    viewModel = viewModel,
                    playback = playerPlayback,
                    transition = localTransitionMarker,
                    mixProgress = playerPresentation.mixProgress,
                    targets = targets,
                    library = library,
                    settings = settings,
                    modifier = Modifier.zIndex(50f),
                )

                // Over the player too: a bug in the full player is still a bug worth a screenshot.
                dev.sfg.numa.mobile.ui.support.SupportHost(
                    ui = supportUi,
                    page = if (playerOpen) "now-playing" else route.orEmpty(),
                    modifier = Modifier.zIndex(60f),
                )

                dev.sfg.numa.mobile.ui.components.WarningBanner(
                    message = warning,
                    onDismiss = viewModel::dismissWarning,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(100f),
                )

                ThemedSessionNotice(
                    message = success, onDismiss = viewModel::dismissSuccess,
                    atTop = successAtTop, scene = if (playerOpen) playerNoticeScene else glassScene,
                    modifier = Modifier.align(if (successAtTop) Alignment.TopCenter else Alignment.Center).zIndex(101f),
                )

                dev.sfg.numa.mobile.ui.components.UpdateDialog(
                    state = updateState,
                    onInstall = viewModel::installUpdate,
                    onDismiss = viewModel::dismissUpdate,
                )
            }
        }

        shareState?.takeIf { !playerOpen }?.let { state ->
            SongShareBottomSheet(
                state = state,
                onDismiss = viewModel::dismissShare,
            )
        }
    }
}

internal fun NavHostController.openTopLevel(route: String) {
    val startId = graph.findStartDestination().id
    popBackStack(startId, false)
    navigate(route) {
        popUpTo(startId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun LibraryFilter.sourceTitle(): String = when (this) {
    LibraryFilter.PLAYLISTS -> "Your playlists"
    LibraryFilter.ARTISTS -> "Your artists"
    LibraryFilter.ALBUMS -> "Your albums"
    LibraryFilter.SONGS -> "Liked songs"
    LibraryFilter.RECENT -> "Recently played"
    LibraryFilter.DOWNLOADS -> "Downloads"
    LibraryFilter.LOCAL -> "Local files"
    LibraryFilter.METRICS -> "Listening metrics"
}
