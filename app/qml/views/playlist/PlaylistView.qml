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

import Numa
import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import "../../components"
import "../../components/home"
import "../../components/detail"
import "../../components/local"

Item {
    id: root

    property bool sharedBackground: false
    property var detail: NumaPlaylist.detail || ({})
    property var artworkPalette: NumaPlaylist.palette || ({})
    readonly property var tracks: detail.tracks || []
    property string filterQuery: ""
    // Playlists kept on this computer can be edited, reordered and never leave it.
    readonly property bool isLocal: detail.source === "local"
    // Saved for offline listening; shows only the songs still downloaded.
    readonly property bool isOffline: detail.source === "download"
    readonly property bool isLikedPlaylist: {
        const id = String(detail.playlistId || detail.browseId || detail.id || "").replace(/^VL/, "");
        return !isLocal && (id === "LM" || ["liked music", "liked songs", "your likes", "música que te gusta"].includes(String(detail.title || "").toLowerCase()));
    }
    readonly property var downloadInfo: NumaDownloads.revision >= 0 ? NumaDownloads.collectionState(tracks) : ({})
    readonly property string downloadState: downloadInfo.total > 0 && downloadInfo.downloaded >= downloadInfo.total ? "done"
        : downloadInfo.pending > 0 ? "busy" : downloadInfo.downloaded > 0 ? "partial" : "none"

    readonly property color accentColor: rgb(artworkPalette.accent, [183, 155, 237])
    readonly property color accentSoftColor: rgb(artworkPalette.accentSoft, [196, 167, 231])
    readonly property color deepColor: rgb(artworkPalette.deep, [15, 21, 18])
    readonly property color inkColor: rgb(artworkPalette.ink, [8, 12, 10])
    readonly property color onAccentColor: rgb(artworkPalette.onAccent, [9, 16, 11])

    // Rows reveal on the first non-empty load. Removals, sorts and pages keep the list put.
    property bool hadTracks: false
    readonly property bool revealRows: !hadTracks || revealWindow.running
    function noteTracks() {
        if (tracks.length > 0 && !hadTracks)
            revealWindow.restart();
        hadTracks = tracks.length > 0;
    }
    // Assigning a new array resets the list to the top, so appended pages restore the scroll position.
    property var shownTracks: []
    function normalizeSearchText(value) {
        return String(value || "").normalize("NFKD")
            .replace(/[\u0300-\u036f]/g, "").toLowerCase();
    }
    function trackMatches(track, query) {
        const artists = track.artists && track.artists.length
            ? track.artists.map(artist => artist && artist.name ? artist.name : artist).join(" ") : "";
        const searchable = [track.title, track.artist, artists, track.album,
                            track.show, track.podcast, track.author].join(" ");
        return normalizeSearchText(searchable).includes(query);
    }
    function filteredTracks() {
        const query = normalizeSearchText(filterQuery).trim();
        return query ? tracks.filter(track => trackMatches(track, query)) : tracks;
    }
    function showTracks() {
        const previous = shownTracks;
        const next = filteredTracks();
        const appended = !filterQuery.trim() && previous.length > 0 && next.length > previous.length
            && next[0].id === previous[0].id && !scroll.pinnedToTop;
        const y = scroll.contentY;
        shownTracks = next;
        if (appended)
            scroll.contentY = y;
        else if (filterQuery.trim())
            scroll.positionViewAtBeginning();
    }
    onTracksChanged: { showTracks(); noteTracks(); }
    onFilterQueryChanged: showTracks()
    Component.onCompleted: { showTracks(); noteTracks(); }

    Timer {
        id: revealWindow
        interval: 400
    }

    signal albumRequested(var album)
    signal artistRequested(var artist)
    signal playlistRequested(var playlist)
    signal backRequested
    signal playRequested
    signal shuffleRequested
    signal trackRequested(var track, int index)

    function rgb(value, fallback) {
        const color = value && value.length >= 3 ? value : fallback;
        return Qt.rgba(Number(color[0]) / 255,
                       Number(color[1]) / 255,
                       Number(color[2]) / 255, 1);
    }

    function localizedSystemText(value) {
        const text = String(value || "");
        if (text === "Liked Music")
            return qsTr("Liked Music");
        if (text === "Only the music you like in YouTube Music will show here. If you want liked music from YouTube to show, you can change this in Settings.")
            return qsTr("Only the music you like in YouTube Music will show here. If you want liked music from YouTube to show, you can change this in Settings.");
        return text;
    }

    // Four albums when the cover is a real collage of the first four tracks' art.
    readonly property var collageAlbums: NumaPlaylist.collageAlbums

    function highResolutionArtwork(url) {
        let value = String(url || "");
        if (!value)
            return value;
        value = value.replace(/=w\d+-h\d+[^&]*/, "=w1200-h1200");
        value = value.replace(/w\d+-h\d+/g, "w1200-h1200");
        return value;
    }

    readonly property bool allTracksLoaded: !NumaPlaylist.loading && !NumaPlaylist.loadingPage && !Boolean(root.detail.hasMoreTracks) && root.tracks.length > 0

    function isEditableFocused() {
        const item = root.Window.window ? root.Window.window.activeFocusItem : null;
        if (!item) return false;
        return item.cursorPosition !== undefined || item.inputMethodHints !== undefined;
    }

    // Type-to-find for when scrolling through a 2,000-song playlist starts feeling like an ultramarathon.
    function handleKeyPress(event) {
        if (event.isAutoRepeat) return false;
        if (quickSearch.visible) return false;
        if (!root.allTracksLoaded) return false;
        if (root.isEditableFocused()) return false;
        if (event.key === Qt.Key_Escape) return false;
        if (event.modifiers & (Qt.ControlModifier | Qt.AltModifier | Qt.MetaModifier)) return false;
        if (event.key === Qt.Key_Slash) return false; // Reserved for global spotlight

        const text = event.text;
        if (text && text.length === 1 && text.trim().length > 0) {
            quickSearch.openWithQuery(text);
            event.accepted = true;
            return true;
        }
        return false;
    }

    focus: visible
    Keys.onPressed: function(event) {
        root.handleKeyPress(event);
    }
    Keys.onReleased: function(event) {
        if (event.key === Qt.Key_Escape) {
            root.backRequested();
            event.accepted = true;
        }
    }

    readonly property bool compact: width < 760
    readonly property int pageInset: width < 600 ? 14 : 24
    readonly property int sideWidth: 300
    readonly property int listLeft: compact ? pageInset : pageInset + sideWidth + 40
    // Keep song titles readable when the sidebar leaves a narrow track list.
    readonly property bool showAlbumColumn: !compact && scroll.width >= 580
    function tint(color, alpha) { return Qt.rgba(color.r, color.g, color.b, alpha); }

    // Sum of loaded durations. Unloaded pages stay out of the math until they show up.
    readonly property int totalSeconds: tracks.reduce((sum, track) => sum + (Number(track.durationSeconds) || 0), 0)
    function lengthLabel(seconds) {
        if (seconds < 60)
            return qsTr("%1 sec").arg(Math.round(seconds));
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.round((seconds % 3600) / 60);
        if (hours > 0)
            return qsTr("%1 hr %2 min").arg(hours).arg(minutes);
        return qsTr("%1 min").arg(minutes);
    }
    readonly property int trackCount: Math.max(detail.totalTrackCount || 0, tracks.length)
    readonly property string shareId: detail.playlistId || detail.browseId || detail.id || ""
    // Whether the player is currently somewhere inside this playlist.
    readonly property bool containsCurrent: {
        const id = NumaPlayback.track.id;
        return Boolean(id) && tracks.some(track => track.id === id);
    }
    readonly property bool collectionPlaying: containsCurrent && NumaPlayback.playing
    function playOrToggle() {
        if (containsCurrent)
            NumaPlayback.toggle();
        else
            root.playRequested();
    }
    function showsDescription(text) {
        return Boolean(text) && text !== "Playlist" && !text.startsWith("Playlist •") && !text.startsWith("Playlist  •");
    }

    Rectangle {
        anchors.fill: parent
        visible: !root.sharedBackground
        gradient: Gradient {
            GradientStop { position: 0; color: root.deepColor }
            GradientStop { position: 1; color: root.inkColor }
        }
    }

    // Wide: pinned left panel. Compact: stacked in the list header (see heroSlot).
    DetailFlickable {
        id: sideScroll

        x: root.pageInset
        width: root.sideWidth
        height: parent.height
        visible: !root.compact
        contentWidth: width
        contentHeight: sideColumn.height + 28
        topMargin: 14
    }

    Column {
        id: sideColumn

        parent: root.compact && scroll.headerItem ? scroll.headerItem.slot : sideScroll.contentItem
        y: root.compact ? 0 : 14
        width: root.compact ? root.width - root.pageInset * 2 : root.sideWidth
        spacing: 14

        Button {
            id: backButton
            width: 132
            height: 34
            text: qsTr("Back to home")
            onClicked: root.backRequested()
            background: Rectangle {
                radius: 17
                color: backButton.hovered ? "#18ffffff" : "transparent"
                border.color: backButton.activeFocus ? root.accentSoftColor : "transparent"
            }
            contentItem: Row {
                spacing: 8
                LucideIcon { name: "chevron-left"; width: 16; height: 16; anchors.verticalCenter: parent.verticalCenter; color: root.accentSoftColor }
                Text { text: backButton.text; color: root.accentSoftColor; font.family: "Inter"; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
            }
        }

        CollectionHero {
            cloudMedia: Object.assign({}, root.detail, {type: "playlist", id: root.detail.playlistId || root.detail.browseId || root.detail.id})
            id: hero
            width: parent.width
            compact: root.compact
            kind: root.isLocal ? qsTr("Local playlist")
                : root.isOffline ? (root.detail.collectionKind === "album" ? qsTr("Downloaded album") : qsTr("Downloaded playlist"))
                : qsTr("Playlist")
            title: root.localizedSystemText(root.detail.title) || qsTr("Playlist")
            byline: root.detail.author || ""
            meta: [
                root.trackCount ? (root.trackCount === 1 ? qsTr("1 song") : qsTr("%1 songs").arg(root.trackCount)) : "",
                root.totalSeconds > 0 ? (root.detail.hasMoreTracks ? qsTr("%1+").arg(root.lengthLabel(root.totalSeconds)) : root.lengthLabel(root.totalSeconds)) : ""
            ]
            description: root.showsDescription(root.detail.description)
                         ? root.localizedSystemText(root.detail.description) : ""
            artwork: typeof NumaSync !== "undefined" && NumaSync.revision >= 0 ? NumaSync.artworkFor(cloudMedia.id || "", root.highResolutionArtwork(root.detail.thumbnail || "")) : root.highResolutionArtwork(root.detail.thumbnail || "")
            gradientCover: (typeof NumaSync === "undefined" || NumaSync.revision < 0 || !NumaSync.artworkFor(cloudMedia.id || "", "")) && artwork === root.highResolutionArtwork(root.detail.thumbnail || "") && !root.isLikedPlaylist && !/episodes.*later|episodios.*tarde/i.test(root.detail.title || "")
                           && String(root.detail.playlistId || root.detail.browseId || "").replace(/^VL/, "") !== "SE" && (!root.isLocal || !root.detail.hasCustomCover)
            localGradientCover: root.isLocal
            collageAlbums: root.collageAlbums
            // The user's own GIF or MP4 loop; nothing here is ever looked up online.
            animatedArtwork: root.isLocal && (typeof NumaSync === "undefined" || NumaSync.revision < 0 || !NumaSync.artworkFor(cloudMedia.id || "", "")) ? (root.detail.animatedArtwork || "") : ""
            actionsEnabled: root.tracks.length > 0 && !NumaPlaylist.loading
            playing: root.collectionPlaying
            searchable: false
            shareLabel: qsTr("Copy playlist link")
            shareVisible: false
            deleteVisible: playlistActions.canDelete && !root.isOffline
            onDeleteClicked: playlistActions.confirmDelete()
            shareEnabled: Boolean(root.shareId) && !root.isLocal && !root.isOffline
            downloadable: !root.isLocal && root.tracks.length > 0
            downloadState: root.downloadState
            downloadEnabled: root.downloadState === "done"
                || (root.allTracksLoaded && !NumaNetwork.offline && root.downloadState !== "busy")
            accentColor: root.accentColor
            accentSoftColor: root.accentSoftColor
            accentInkColor: root.onAccentColor
            inkColor: root.inkColor
            onPlayClicked: root.playOrToggle()
            onShuffleClicked: root.shuffleRequested()
            onShareClicked: NumaBackend.copySongLink(root.shareId, qsTr("playlist"))
            onShareOpenRequested: NumaBackend.openSongLink(root.shareId, "playlist")
            onSearchClicked: quickSearch.openWithQuery("")
            onDownloadClicked: NumaDownloads.downloadAll(root.tracks, root.detail)
            onRemoveDownloadsClicked: NumaDownloads.removeAll(root.tracks, NumaOffline.collectionIdFor(root.detail))
        }
    }

    DetailListView {
        id: scroll
        anchors.fill: parent
        anchors.leftMargin: root.listLeft
        anchors.rightMargin: root.pageInset

        topMargin: 14
        bottomMargin: 14 + playerInset

        model: root.shownTracks
        // Read by ReorderableRow while a song is being dragged.
        property int dragFrom: -1
        property int dropIndex: -1

        header: Item {
            width: scroll.width
            height: content.height + 6

            readonly property Item slot: heroSlot
            readonly property real heroBottom: heroSlot.y + sideColumn.y + hero.y + hero.height

            Column {
                id: content
                width: parent.width
                spacing: 14

                Item {
                    id: heroSlot
                    width: parent.width
                    height: root.compact ? sideColumn.height : 0
                }

                LocalPlaylistToolbar {
                    visible: root.isLocal
                    width: parent.width
                    playlist: root.detail
                    accentColor: root.accentColor
                    accentSoftColor: root.accentSoftColor
                    onDeleted: root.backRequested()
                }

                Text {
                    x: 12
                    width: parent.width - 24
                    visible: NumaPlaylist.loadingPage || Boolean(root.detail.hasMoreTracks)
                    objectName: "playlistTrackCount"
                    text: qsTr("%1 of %2 songs loaded").arg(root.tracks.length).arg(root.trackCount)
                    color: root.tint(root.accentSoftColor, 0.65)
                    font.family: "Inter"
                    font.pixelSize: 12
                }

                CollectionTrackHeader {
                    width: parent.width
                    showArtwork: true
                    showAlbum: root.showAlbumColumn
                    labelColor: root.tint(root.accentSoftColor, 0.6)
                    activeColor: root.accentSoftColor
                    sortable: true
                    sortKey: NumaPlaylist.sortKey
                    sortDescending: NumaPlaylist.sortDescending
                    onSortRequested: function(key, descending) { NumaPlaylist.setSort(key, descending); }
                }
            }
        }

        delegate: ReorderableRow {
            id: slot
            required property var modelData
            view: scroll
            // Only the playlist's own order can be rearranged; a sorted view is a lens, not a list.
            reorderable: root.isLocal && !NumaPlaylist.sortKey && !root.filterQuery.trim()
            accentColor: root.accentColor
            onMoveRequested: function(from, to) { NumaLocal.moveTrack(root.detail.id, from, to); }

            CollectionTrackRow {
                id: trackRow
                width: parent.width
                track: slot.modelData
                trackIndex: slot.index
                reveal: root.revealRows
                showArtwork: true
                showAlbum: root.showAlbumColumn
                fallbackArtist: root.detail.author || ""
                removablePlaylistId: !root.isLikedPlaylist && root.detail.editable ? (root.detail.playlistId || String(root.detail.browseId || "").replace(/^VL/, "")) : ""
                removablePlaylistTitle: root.detail.title || ""
                likedPlaylist: root.isLikedPlaylist
                searchMatch: !root.filterQuery.trim() && quickSearch.visible && quickSearch.focusedTrackIndex === slot.index
                accentColor: root.accentColor
                accentSoftColor: root.accentSoftColor
                onClicked: root.trackRequested(slot.modelData, slot.index)
                onAlbumRequested: function(album) { root.albumRequested(album); }
                onArtistRequested: function(artist) { root.artistRequested(artist); }
                onPlaylistRequested: function(playlist) { root.playlistRequested(playlist); }
            }
        }

        footer: Item {
            width: scroll.width
            height: NumaPlaylist.loadingPage || NumaPlaylist.errorMessage ? 100 : emptyMessage.visible ? 88 : 16

            BusyIndicator {
                anchors.centerIn: parent
                running: NumaPlaylist.loadingPage
                visible: running
            }
            Column {
                anchors.centerIn: parent
                width: parent.width
                visible: Boolean(NumaPlaylist.errorMessage) && root.tracks.length > 0
                Text { width: parent.width; text: NumaPlaylist.errorMessage; color: root.accentSoftColor; wrapMode: Text.Wrap; horizontalAlignment: Text.AlignHCenter }
                Button { anchors.horizontalCenter: parent.horizontalCenter; text: qsTr("Try again"); onClicked: NumaPlaylist.retry() }
            }
            Text {
                id: emptyMessage
                anchors.fill: parent
                anchors.margins: 16
                visible: !root.shownTracks.length && !NumaPlaylist.loading && !NumaPlaylist.loadingPage
                text: root.filterQuery.trim() ? qsTr("No tracks match “%1”").arg(root.filterQuery.trim())
                                   : root.isLocal ? qsTr("Nothing here yet. Use Add songs, or drag audio files onto this page.")
                                   : qsTr("No tracks available for this playlist.")
                color: root.accentSoftColor
                verticalAlignment: Text.AlignVCenter
                horizontalAlignment: Text.AlignHCenter
                wrapMode: Text.Wrap
                font.family: "Inter"
                font.pixelSize: 13
            }
        }
    }

    CollectionStickyBar {
        x: root.pageInset
        y: 8
        width: root.width - root.pageInset * 2
        // Header y sits before originY, so measure the hero from the header itself.
        shown: root.compact && scroll.headerItem !== null && !NumaPlaylist.loading
               && scroll.contentY > scroll.headerItem.y + scroll.headerItem.heroBottom - 40
        title: root.localizedSystemText(root.detail.title) || qsTr("Playlist")
        artwork: root.detail.thumbnail || ""
        playing: root.collectionPlaying
        actionsEnabled: root.tracks.length > 0
        accentColor: root.accentColor
        accentInkColor: root.onAccentColor
        inkColor: root.inkColor
        onPlayClicked: root.playOrToggle()
        onBackToTopClicked: scroll.positionViewAtBeginning()
    }

    PageLoadState {
        anchors.fill: parent
        loading: NumaPlaylist.loading
        errorMessage: root.tracks.length ? "" : NumaPlaylist.errorMessage
        accentColor: root.accentColor
        onRetryRequested: NumaPlaylist.retry()
    }

    // Audio files dragged in from the file manager land at the end of the playlist.
    DropArea {
        anchors.fill: parent
        enabled: root.isLocal
        keys: ["text/uri-list"]
        onDropped: function(drop) {
            if (drop.hasUrls)
                NumaLocal.importFiles(drop.urls, root.detail.id);
        }
    }

    PlaylistActions { id: playlistActions; playlist: root.detail }
    Connections {
        target: NumaLibrary
        function onPlaylistDeleted(id) { if (id === playlistActions.playlistId) root.backRequested(); }
    }
    PlaylistQuickSearch {
        id: quickSearch
        backdrop: root
        tracks: root.tracks
        detail: root.detail
        onMatchFocused: function(trackIndex) {
            scroll.positionViewAtIndex(trackIndex, ListView.Center);
        }
        onTrackSelected: function(track, trackIndex) {
            scroll.positionViewAtIndex(trackIndex, ListView.Center);
            root.trackRequested(track, trackIndex);
        }
    }
}
