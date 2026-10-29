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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

import "../../components"
import "../../components/home"
import "../album"
import "../artist"
import "../library"
import "../offline"
import "../playlist"
import "../search"
import Numa
import QtQuick
import QtQuick.Window

// Backdrop, sidebar, top bar and pages of the signed-in shell. Navigation state lives in HomeShell.
Item {
    id: scene

    // HomeShell: owns navigation, history and overlays.
    required property Item shell
    // Bottom inset that keeps page content clear of the floating player.
    property real dockHeight: 0
    property real sidebarWidth: width < 1100 ? 76 : 218
    readonly property real queueWidth: Math.round(Math.min(380, Math.max(320, width * 0.26)))
    readonly property alias immersive: immersive
    readonly property alias home: home
    readonly property alias topBar: topBar
    readonly property alias offlineHomeLoader: offlineHomeLoader
    readonly property alias libraryLoader: libraryLoader
    readonly property alias searchLoader: searchLoader
    readonly property alias albumLoader: albumLoader
    readonly property alias artistLoader: artistLoader
    readonly property alias playlistLoader: playlistLoader
    readonly property alias playerDock: playerDock
    readonly property string playbackArtwork: (NumaPlayback.track || {}).thumbnail || ""
    readonly property string collectionArtwork: scene.detailThumbnail()
    readonly property color collectionDeep: {
        if (scene.shell.currentPage === "artist")
            return scene.paletteColor((NumaArtist.palette || {}).deep, [15, 21, 18]);
        if (scene.shell.currentPage === "playlist")
            return scene.paletteColor((NumaPlaylist.palette || {}).deep, [15, 21, 18]);
        return scene.paletteColor((NumaAlbum.palette || {}).deep, [15, 21, 18]);
    }
    readonly property color collectionInk: scene.shell.currentPage === "artist"
        ? scene.collectionDeep
        : scene.paletteColor(scene.shell.currentPage === "playlist"
            ? (NumaPlaylist.palette || {}).ink
            : (NumaAlbum.palette || {}).ink, [8, 12, 10])
    readonly property color collectionSeam: scene.shell.currentPage === "artist"
        ? scene.collectionDeep
        : scene.paletteColor(scene.shell.currentPage === "playlist"
            ? (NumaPlaylist.palette || {}).seam
            : (NumaAlbum.palette || {}).seam, [38, 48, 43])

    function paletteColor(value, fallback) {
        const color = value && value.length >= 3 ? value : fallback;
        return Qt.rgba(Number(color[0]) / 255, Number(color[1]) / 255, Number(color[2]) / 255, 1);
    }

    function tint(color, alpha) {
        return Qt.rgba(color.r, color.g, color.b, alpha);
    }

    function detailThumbnail() {
        if (scene.shell.currentPage === "album")
            return (NumaAlbum.detail || {}).thumbnail || "";
        if (scene.shell.currentPage === "playlist") {
            const detail = NumaPlaylist.detail || {};
            const fallback = detail.thumbnail || "";
            const id = detail.playlistId || detail.browseId || detail.id || "";
            return typeof NumaSync !== "undefined" && NumaSync.revision >= 0
                ? NumaSync.artworkFor(id, fallback) : fallback;
        }
        return "";
    }

    function localizedPlaylistTitle(title) {
        const text = String(title || "");
        if (text === "Liked Music")
            return qsTr("Liked Music");
        if (text === "Episodes for Later")
            return qsTr("Episodes for Later");
        return text;
    }

    anchors.fill: parent
    visible: !scene.shell.fullscreenCovering
    // Pages, pill and sidebar all follow this, so they slide together on compact toggles.
    Behavior on sidebarWidth {
        NumberAnimation { duration: Motion.slow; easing.type: Motion.enter }
    }
    // Render the page once into a texture that every glass panel samples directly, instead of
    // each panel re-rendering the whole page into its own capture. Frames where only the player
    // bar changes (the progress ticker) also skip redrawing the page. Draw once, blur everywhere.
    layer.enabled: scene.visible && !NumaAppearance.performanceMode

    ImmersiveBackground {
        id: immersive

        anchors.fill: parent
        source: NumaAppearance.immersiveBackground && !NumaAppearance.performanceMode ? (scene.playbackArtwork || scene.collectionArtwork) : ""
        visible: NumaAppearance.immersiveBackground && !NumaAppearance.performanceMode
        // The video ambient fully covers it on artist pages; no need to warp underneath.
        throttled: NumaWindowState.backgroundIdle
        running: scene.visible && !NumaAppearance.performanceMode && scene.shell.visible && visible && !videoAmbient.covering
                 && (NumaPlayback.playing || (!scene.playbackArtwork && scene.collectionArtwork.length > 0))
                 && scene.Window.window !== null && scene.Window.window.visibility !== Window.Minimized && scene.Window.window.visibility !== Window.Hidden
        speed: NumaAppearance.performanceMode ? 0 : NumaAppearance.speed
        intensity: NumaAppearance.intensity
        saturation: NumaAppearance.saturation
        brightness: NumaAppearance.brightness
    }

    // Use the viewed collection's palette across the whole shell, including
    // the navigation and title/search area. Other pages keep the mesh.
    Rectangle {
        anchors.fill: parent
        visible: !NumaAppearance.rosePine && (scene.shell.currentPage === "album" || scene.shell.currentPage === "artist" || scene.shell.currentPage === "playlist") && !scene.shell.immersiveReady

        gradient: Gradient {
            GradientStop {
                position: 0
                color: scene.collectionDeep
            }

            GradientStop {
                position: 1
                color: scene.shell.currentPage === "artist" ? Qt.darker(scene.collectionDeep, 1.5) : scene.collectionInk
            }

        }

    }

    Rectangle {
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        width: scene.sidebarWidth
        visible: !NumaAppearance.rosePine && (scene.shell.currentPage === "album" || scene.shell.currentPage === "artist" || scene.shell.currentPage === "playlist") && !scene.shell.immersiveReady

        gradient: Gradient {
            orientation: Gradient.Horizontal

            GradientStop {
                position: 0
                color: scene.tint(scene.shell.currentPage === "artist" ? scene.collectionDeep : scene.collectionInk, 0.65)
            }

            GradientStop {
                position: 1
                color: scene.tint(scene.collectionSeam, 0.22)
            }

        }

    }

    // Colors of the artist page's backdrop video, in place of the artwork background.
    VideoAmbient {
        id: videoAmbient

        readonly property bool covering: opacity === 1

        anchors.fill: parent
        colors: NumaArtistVideo.ambient
        opacity: scene.shell.currentPage === "artist" && NumaAppearance.artistVideoBackdrop && !NumaAppearance.performanceMode && ready ? 1 : 0
        visible: opacity > 0
        Behavior on opacity { NumberAnimation { duration: 1200; easing.type: Easing.InOutQuad } }
    }

    NavigationSidebar {
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        width: scene.sidebarWidth
        compact: scene.width < 1100
        currentPage: scene.shell.currentPage
        onPageRequested: function(page) {
            scene.shell.navigateTo(page);
        }
        onPlaylistRequested: function(playlist) {
            scene.shell.openDetail("playlist", playlist);
        }
    }

    HomeView {
        id: home

        anchors.left: parent.left
        anchors.leftMargin: scene.sidebarWidth + 28
        anchors.right: parent.right
        anchors.rightMargin: 28
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        visible: scene.shell.currentPage === "home" && !NumaNetwork.offline && !NumaNetwork.retrying
        onArtistRequested: function(artist) {
            scene.shell.openDetail("artist", artist);
        }
        onUnsupportedRequested: function(media) {
            NumaMusicVideo.playVideo(media);
        }
        onAlbumRequested: function(album) {
            scene.shell.openDetail("album", album);
        }
        onPlaylistRequested: function(playlist) {
            scene.shell.openDetail("playlist", playlist);
        }
    }

    Loader {
        id: offlineHomeLoader
        anchors.fill: home
        asynchronous: false
        active: scene.shell.currentPage === "home" && (NumaNetwork.offline || NumaNetwork.retrying)

        sourceComponent: Component {
            OfflineHomeView {
                onPlaylistRequested: function(playlist) {
                    scene.shell.openDetail("playlist", playlist);
                }
            }
        }
    }

    Loader {
        id: libraryLoader
        anchors.fill: home
        asynchronous: true
        active: scene.shell.currentPage === "library"
        sourceComponent: (NumaNetwork.offline || NumaNetwork.retrying) ? offlineLibraryComponent : onlineLibraryComponent
    }

    Component {
        id: offlineLibraryComponent

        OfflineLibraryView {
            onPlaylistRequested: function(playlist) {
                scene.shell.openDetail("playlist", playlist);
            }
        }
    }

    Component {
        id: onlineLibraryComponent

        LibraryView {
            onArtistRequested: function(artist) {
                scene.shell.openDetail("artist", artist);
            }
            onAlbumRequested: function(album) {
                scene.shell.openDetail("album", album);
            }
            onPlaylistRequested: function(playlist) {
                scene.shell.openDetail("playlist", playlist);
            }
            onUnsupportedRequested: function(media) {
                NumaMusicVideo.playVideo(media);
            }
        }
    }

    Loader {
        id: searchLoader
        anchors.left: home.left
        anchors.right: home.right
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        asynchronous: true
        active: scene.shell.currentPage === "search"

        sourceComponent: Component {
            SearchView {
                query: scene.shell.searchQuery
                filter: scene.shell.searchFilter
                sections: NumaSearch.sections
                loading: NumaSearch.loading
                errorMessage: NumaSearch.errorMessage
                onFilterRequested: function(filter) {
                    scene.shell.searchFilter = filter;
                    NumaSearch.clear();
                    if (scene.shell.searchQuery.trim())
                        scene.shell.searchDebounce.restart();
                }
                onRetryRequested: NumaSearch.retry()
                onSongRequested: function(song) {
                    const sections = NumaSearch.sections || [];
                    const tracks = [];
                    // Qt lists skipped the flatMap orientation. Copy their items explicitly.
                    for (let sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
                        const items = sections[sectionIndex].items || [];
                        for (let itemIndex = 0; itemIndex < items.length; ++itemIndex) tracks.push(items[itemIndex])
                    }
                    const index = tracks.findIndex((track) => {
                        return track.id === song.id;
                    });
                    if (index >= 0)
                        NumaPlayback.playCollection(tracks, index);
                    else
                        NumaPlayback.playSong(song);
                }
                onUnsupportedRequested: function(media) {
                    NumaMusicVideo.playVideo(media);
                }
                onAlbumRequested: function(album) {
                    scene.shell.openDetail("album", album);
                }
                onArtistRequested: function(artist) {
                    scene.shell.openDetail("artist", artist);
                }
                onPlaylistRequested: function(playlist) {
                    scene.shell.openDetail("playlist", playlist);
                }
            }
        }
    }

    AppTopBar {
        id: topBar

        anchors.left: home.left
        anchors.right: home.right
        anchors.top: parent.top
        anchors.topMargin: 32
        height: 62
        searchMode: scene.shell.currentPage === "playlist" ? "section"
            : ["home", "search", "library"].includes(scene.shell.currentPage) ? "global"
            : "hidden"
        sectionTitle: scene.shell.currentPage === "playlist"
            ? scene.localizedPlaylistTitle((NumaPlaylist.detail || {}).title || qsTr("Playlist")) : ""
        onSearchChanged: function(query) {
            if (searchMode === "section")
                return;
            if (searchMode !== "global")
                return;
            scene.shell.searchQuery = query;
            if (query.trim()) {
                if (scene.shell.currentPage !== "search") {
                    scene.shell.history = [];
                    scene.shell.currentItem = ({
                    });
                    scene.shell.currentPage = "search";
                }
                NumaSearch.clear();
                scene.shell.searchDebounce.restart();
            } else {
                scene.shell.searchDebounce.stop();
                NumaSearch.clear();
            }
        }
        onSettingsRequested: scene.shell.openSettings()
        onDocsRequested: scene.shell.openDocs()
        onSupportRequested: scene.shell.openSupport("")
    }

    Loader {
        id: albumLoader
        anchors.left: home.left
        anchors.right: home.right
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        asynchronous: true
        active: scene.shell.currentPage === "album"

        sourceComponent: Component {
            AlbumView {
                sharedBackground: true
                onBackRequested: scene.shell.goBack()
                onAlbumRequested: function(album) {
                    scene.shell.openDetail("album", album);
                }
                onArtistRequested: function(artist) {
                    scene.shell.openDetail("artist", artist);
                }
                onPlaylistRequested: function(playlist) {
                    scene.shell.openDetail("playlist", playlist);
                }
                // Album origin lets playback keep an in-order playthrough gapless.
                function albumTracks() {
                    const detail = NumaAlbum.detail || {};
                    const origin = { kind: "album", title: detail.title || "", artist: detail.artist || "" };
                    return (detail.tracks || []).map((item) => Object.assign({}, item, { queueOrigin: origin }));
                }
                onPlayRequested: NumaPlayback.playCollection(albumTracks(), 0, false)
                onShuffleRequested: NumaPlayback.playCollection(albumTracks(), 0, true)
                onTrackRequested: function(track, index) {
                    const tracks = albumTracks();
                    const selectedIndex = tracks.findIndex((item) => {
                        return item.id === track.id;
                    });
                    if (selectedIndex >= 0)
                        NumaPlayback.playCollection(tracks, selectedIndex, false);
                }
            }
        }
    }

    Loader {
        id: artistLoader
        anchors.left: home.left
        anchors.right: home.right
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        asynchronous: true
        active: scene.shell.currentPage === "artist"

        sourceComponent: Component {
            ArtistView {
                sharedBackground: true
                onBackRequested: scene.shell.goBack()
                onAlbumRequested: function(album) {
                    scene.shell.openDetail("album", album);
                }
                onArtistRequested: function(artist) {
                    scene.shell.openDetail("artist", artist);
                }
                onPlaylistRequested: function(playlist) {
                    scene.shell.openDetail("playlist", playlist);
                }
                onUnsupportedRequested: function(media) {
                    NumaMusicVideo.playVideo(media);
                }
            }
        }
    }

    Loader {
        id: playlistLoader
        anchors.left: home.left
        anchors.right: home.right
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        asynchronous: true
        active: scene.shell.currentPage === "playlist"
        onLoaded: if (scene.shell.currentPage === "playlist") item.forceActiveFocus()

        sourceComponent: Component {
            PlaylistView {
                sharedBackground: true
                filterQuery: topBar.query
                onBackRequested: scene.shell.goBack()
                onAlbumRequested: function(album) {
                    scene.shell.openDetail("album", album);
                }
                onArtistRequested: function(artist) {
                    scene.shell.openDetail("artist", artist);
                }
                onPlaylistRequested: function(playlist) {
                    scene.shell.openDetail("playlist", playlist);
                }
                onPlayRequested: scene.shell.playPlaylist(0, false)
                onShuffleRequested: scene.shell.playPlaylist(0, true)
                onTrackRequested: function(track, index) {
                    const tracks = NumaPlaylist.detail.tracks || [];
                    const sourceIndex = tracks.findIndex(item => {
                        if (track.setVideoId && item.setVideoId)
                            return item.setVideoId === track.setVideoId;
                        return item.id === track.id;
                    });
                    scene.shell.playPlaylist(sourceIndex >= 0 ? sourceIndex : index, false);
                }
            }
        }
    }

    // Pages scroll beneath the floating player; this is the inset they pad by.
    Item {
        id: playerDock

        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: scene.dockHeight
    }
}
