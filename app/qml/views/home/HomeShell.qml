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
import "../../components/player"
import Numa
import QtQuick
import QtQuick.Window

Item {
    id: root
    visible: !NumaMusicVideo.open

    property bool queueOpen: false
    property bool compactMode: false
    property bool queuePanelRetained: false
    property bool fullscreenOpen: false
    property bool fullscreenRetained: false
    property rect fullscreenOrigin: Qt.rect(0, 0, 44, 44)
    // Scene and pill are fully hidden behind the player, so skip drawing them.
    readonly property bool fullscreenCovering: fullscreenLoader.item !== null && fullscreenLoader.item.covering
    property string currentPage: "home"
    property string searchQuery: ""
    property string searchFilter: "all"
    property string noticeMessage: ""
    readonly property alias searchDebounce: searchDebounce
    property var history: []
    property var currentItem: ({
    })
    readonly property bool canopy: NumaAppearance.layoutStyle === "canopy"
    readonly property bool immersiveReady: NumaAppearance.immersiveBackground && scene.immersive.ready

    function playPlaylist(index, shuffle) {
        // Unloaded pages arrive in playlist order, so a sorted queue stops at what is loaded.
        const continuation = NumaPlaylist.sortKey ? "" : NumaPlaylist.detail.continuation || "";
        const detail = NumaPlaylist.detail || {};
        const pid = String(detail.browseId || detail.playlistId || detail.id || "");
        const ptitle = String(detail.title || "").toLowerCase();
        const isLiked = pid.endsWith("LM") || pid === "LM" || pid === "VLLM"
            || ptitle === "liked music" || ptitle === "your likes"
            || ptitle.indexOf("gusta") !== -1;
        const origin = {
            kind: "playlist",
            id: pid,
            title: detail.title || "",
            liked: isLiked
        };
        const tracks = (detail.tracks || []).map(item => Object.assign({}, item, {
            queueOrigin: origin,
            liked: isLiked ? true : Boolean(item.liked)
        }));
        NumaPlayback.playCollection(tracks, index, shuffle, detail.browseId || "", continuation);
    }

    function showNotice(message) {
        root.noticeMessage = message;
        noticeTimer.restart();
    }

    function openSettings() { overlays.openSettings(); }
    function openDocs(page) { overlays.openDocs(page); }
    function openSupport(reportId) { overlays.openSupport(reportId); }
    function openSpotlight(query) { overlays.openSpotlight(query); }
    function requestCompactMode(compact) {
        const window = root.Window.window;
        if (window && typeof window.requestCompactMode === "function")
            window.requestCompactMode(compact);
        else
            root.compactMode = compact;
    }

    function navigateTo(page) {
        searchDebounce.stop();
        root.history = [];
        root.currentItem = ({
        });
        if (page !== "search") {
            root.searchFilter = "all";
            NumaSearch.clear();
            scene.topBar.clearSearch();
        }
        root.currentPage = page;
        if (page === "search") {
            scene.topBar.focusSearch();
            if (root.searchQuery.trim())
                searchDebounce.restart();

        }
    }

    function detailKey(item) {
        if (!item)
            return "";
        return item.browseId || (item.browsePayload || {}).browseId || item.playlistId || "";
    }

    function openDetail(page, item) {
        // Re-opening the visible page would push a duplicate history entry and replay the load.
        const key = detailKey(item);
        if (key && page === currentPage && key === detailKey(currentItem))
            return ;
        history = history.concat([{
            "page": currentPage,
            "item": currentItem
        }]);
        showDetail(page, item);
    }

    function showDetail(page, item) {
        currentItem = item;
        currentPage = page;
        if (page === "artist")
            NumaArtist.openAlbum(item);

        if (page === "album")
            NumaAlbum.openAlbum(item);

        if (page === "playlist") {
            NumaPlaylist.openPlaylist(item);
            Qt.callLater(() => {
                if (scene.playlistLoader.item)
                    scene.playlistLoader.item.forceActiveFocus();
            });
        }
    }

    // Back from a restored detail lands on home.
    function restorePage(saved) {
        const page = saved.page;
        if (page === "library") {
            navigateTo(page);
        } else if (page === "search") {
            searchFilter = saved.filter || "all";
            currentPage = page;
            scene.topBar.query = saved.query || "";
        } else if ((page === "album" || page === "artist" || page === "playlist") && detailKey(saved.item)) {
            showDetail(page, saved.item);
        }
    }

    function pageItem(page) {
        return ({ home: (NumaNetwork.offline || NumaNetwork.retrying) ? scene.offlineHomeLoader : scene.home, library: scene.libraryLoader, search: scene.searchLoader,
                  album: scene.albumLoader, artist: scene.artistLoader, playlist: scene.playlistLoader })[page] || null;
    }

    // Results come from YouTube online and from downloads offline, so they restart when that flips.
    function rerunSearch() {
        NumaSearch.clear();
        if (root.currentPage === "search" && root.searchQuery.trim())
            searchDebounce.restart();
    }

    function goBack() {
        if (!history.length) {
            currentPage = "home";
            return ;
        }
        const previous = history[history.length - 1];
        history = history.slice(0, -1);
        showDetail(previous.page, previous.item);
    }

    // Re-measure on both edges so the cover flies to wherever the pill sits right now.
    function measureFullscreenOrigin() {
        const art = player.artworkItem;
        const origin = art.mapToItem(root, 0, 0);
        root.fullscreenOrigin = Qt.rect(origin.x, origin.y, art.width, art.height);
    }

    // Keyboard shortcuts bring the idle fullscreen controls back.
    function wakeFullscreen() {
        if (fullscreenOpen && fullscreenLoader.item)
            fullscreenLoader.item.wake();
    }

    function setFullscreen(open) {
        root.measureFullscreenOrigin();
        root.fullscreenOpen = open;
    }

    function openFromFullscreen(page, item) {
        root.setFullscreen(false);
        root.openDetail(page, item);
    }

    function isEditableFocused() {
        const item = root.Window.window ? root.Window.window.activeFocusItem : null;
        if (!item)
            return false;

        return item.cursorPosition !== undefined || item.inputMethodHints !== undefined;
    }

    // The window resize lands mid-flight and moves the pill; keep the cover aimed at it.
    onWidthChanged: if (fullscreenRetained) Qt.callLater(measureFullscreenOrigin)
    onHeightChanged: if (fullscreenRetained) Qt.callLater(measureFullscreenOrigin)
    // Detail-to-detail hops keep the page but swap the item, so both replay the entrance.
    onCurrentPageChanged: pageEntrance.play(pageItem(currentPage))
    onCurrentItemChanged: pageEntrance.play(pageItem(currentPage))
    onFullscreenOpenChanged: {
        if (fullscreenOpen) {
            fullscreenRetained = true;
            fullscreenReleaseTimer.stop();
        } else {
            fullscreenReleaseTimer.restart();
        }
    }
    onQueueOpenChanged: {
        if (queueOpen) {
            queuePanelRetained = true;
            queueReleaseTimer.stop();
            Qt.callLater(() => {
                if (queueHost.item)
                    queueHost.item.forceActiveFocus();
            });
        } else {
            queueReleaseTimer.restart();
        }
    }
    anchors.fill: parent
    Keys.onPressed: function(event) {
        if (root.currentPage === "playlist" && scene.playlistLoader.item)
            scene.playlistLoader.item.handleKeyPress(event);

    }
    Component.onCompleted: {
        if (NumaAuth.isSignedIn && !NumaNetwork.offline && !NumaNetwork.retrying)
            NumaHome.refresh();

    }

    PageMemory {
        page: root.currentPage
        item: root.currentItem
        query: root.searchQuery
        filter: root.searchFilter
        onRestoreRequested: function(saved) {
            root.restorePage(saved);
        }
    }

    Timer {
        id: searchDebounce

        interval: 320
        repeat: false
        onTriggered: {
            if (root.searchQuery.trim())
                NumaSearch.search(root.searchQuery, root.searchFilter);

        }
    }

    Timer {
        id: noticeTimer

        interval: 3200
        repeat: false
        onTriggered: root.noticeMessage = ""
    }

    Timer {
        id: queueReleaseTimer
        // Outlives the panel's 340ms slide-out so it isn't unloaded mid-exit.
        interval: 400
        repeat: false
        onTriggered: root.queuePanelRetained = false
    }
    Timer {
        id: fullscreenReleaseTimer
        // Outlives the 620ms flight home.
        interval: 700
        repeat: false
        onTriggered: root.fullscreenRetained = false
    }

    // Queue lyrics and fullscreen lyrics share one fetcher.
    Binding {
        target: NumaLyrics
        property: "active"
        value: !NumaMusicVideo.open && (((root.queueOpen || root.compactMode) && queueHost.item !== null && queueHost.item.lyricsMode)
               || (root.fullscreenOpen && fullscreenLoader.item !== null && fullscreenLoader.item.pane === "lyrics")
               )
    }

    // Forward backend announcements (like copied links) straight into the UI pill.

    Connections {
        function onNoticeRequested(message) {
            root.showNotice(message);
        }

        target: NumaBackend
    }

    PlaylistPicker {
        id: playlistPicker
    }

    Connections {
        target: NumaNetwork

        // Album and artist pages need YouTube; the offline home is the way back.
        function onLost() {
            if (root.currentPage === "album" || root.currentPage === "artist")
                root.navigateTo("home");
            if (["videos", "albums", "artists"].includes(root.searchFilter))
                root.searchFilter = "all";
            root.rerunSearch();
            root.showNotice(qsTr("You're offline. Showing your downloads."));
        }

        function onRestored() {
            root.rerunSearch();
            root.showNotice(qsTr("Back online."));
            NumaHome.refresh();
        }
    }

    PageEntrance {
        id: pageEntrance
    }

    Connections {
        function onNoticeRequested(message) {
            root.showNotice(message);
        }

        function onPlaylistPickerRequested(track, position) {
            playlistPicker.openFor(track, position);
        }

        target: NumaLibrary
    }

    HomeScene {
        id: scene

        shell: root
        dockHeight: root.canopy ? 12 : player.height + 26
        visible: !root.compactMode && !root.fullscreenCovering
    }

    PlayerBar {
        id: player

        backdrop: scene
        // Pages scroll under the glass, so the capture tracks every repaint.
        backdropLive: visible && !NumaWindowState.backgroundIdle && !NumaAppearance.performanceMode
        queueOpen: root.queueOpen
        visible: !root.compactMode && !root.fullscreenCovering
        onQueueRequested: root.queueOpen = !root.queueOpen
        onCompactRequested: root.requestCompactMode(true)
        onExpandRequested: root.setFullscreen(true)
        onAlbumRequested: function(album) {
            root.openDetail("album", album);
        }
        onArtistRequested: function(artist) {
            root.openDetail("artist", artist);
        }
        onPlaylistRequested: function(playlist) {
            root.openDetail("playlist", playlist);
        }
        // Glade: centered in the content column at the bottom. Canopy: top row, ending before the search box.
        x: root.canopy ? scene.topBar.x : (parent.width - width) / 2 + scene.sidebarWidth / 2
        y: root.canopy ? scene.topBar.y + (scene.topBar.height - height) / 2 : parent.height - height - 14
        width: root.canopy ? scene.topBar.canopyPlayerWidth : Math.min(parent.width - scene.sidebarWidth - 32, 1120)
        height: root.canopy ? 52 : 64
        z: 6
    }

    // Above the player bar, which sits in the top row on canopy.
    TopErrorPill {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        topInset: root.canopy ? scene.topBar.y + scene.topBar.height + 8 : 24
        z: root.noticeMessage ? 300 : 8
        visible: !root.compactMode
        text: root.noticeMessage || NumaPlayback.errorMessage || (NumaPlayback.loading ? qsTr("Loading audio…") : "")
        pillColor: "#c0242725"
        textColor: "#f2f0eb"
    }

    QueuePanelHost {
        id: queueHost
        anchors.fill: parent
        shell: root
        scene: scene
        player: player
    }

    Loader {
        id: fullscreenLoader

        anchors.fill: parent
        z: 20
        active: root.fullscreenOpen || root.fullscreenRetained
        onLoaded: Qt.callLater(() => {
            if (item)
                item.open = Qt.binding(() => root.fullscreenOpen);
        })

        sourceComponent: Component {
            FullscreenPlayer {
                accentColor: player.accentColor
                mixAccentColor: player.mixAccentColor
                inkColor: player.inkColor
                originRect: root.fullscreenOrigin
                onCloseRequested: root.setFullscreen(false)
                onAlbumRequested: function(album) { root.openFromFullscreen("album", album); }
                onArtistRequested: function(artist) { root.openFromFullscreen("artist", artist); }
                onPlaylistRequested: function(playlist) { root.openFromFullscreen("playlist", playlist); }
            }
        }
    }

    // Detail scroll views pad their ends by this so the last row clears the pill.
    Binding {
        target: root.Window.window
        property: "playerInset"
        value: scene.playerDock.height
        when: root.Window.window !== null
    }

    HomeOverlays {
        id: overlays

        shell: root
        stage: scene
    }
    Connections {
        target: typeof NumaSync !== "undefined" ? NumaSync : null
        function onPlaylistRemoved(id) {
            const detail = NumaPlaylist.detail || {}
            const currentId = detail.playlistId || detail.browseId || detail.id || ""
            if (root.currentPage === "playlist" && NumaSync.isHiddenPlaylist(currentId)) root.navigateTo("home")
        }
    }

}
