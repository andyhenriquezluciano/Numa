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

pragma ComponentBehavior: Bound

import "../../components"
import "../../components/home"
import Numa
import QtQuick
import QtQuick.Controls

Item {
    id: root

    property string filter: "all"
    // Applies to the songs table; empty keeps the order YouTube Music sent.
    property string sortKey: ""
    property bool sortDescending: false
    readonly property string query: header.query.trim().toLowerCase()
    readonly property var categories: [
        { key: "metrics", title: qsTr("Métricas"), empty: qsTr("Las canciones aparecen aquí después de 15 segundos de escucha.") },
        { key: "artists", title: qsTr("Subscribed artists"), empty: qsTr("Artists you subscribe to on YouTube will appear here.") },
        { key: "albums", title: qsTr("Albums"), empty: qsTr("Albums you save to your YouTube Music library will appear here.") },
        { key: "songs", title: qsTr("Songs"), empty: qsTr("Songs you save to your YouTube Music library will appear here.") },
        { key: "playlists", title: qsTr("Playlists"), empty: qsTr("Playlists you create or save on YouTube Music will appear here.") },
        { key: "localPlaylists", title: qsTr("Local playlists"), empty: qsTr("Playlists made from files on this computer will appear here.") },
        { key: "localSongs", title: qsTr("Local songs"), empty: qsTr("Use Add local files to bring in music from this computer.") }
    ]
    // Everything loaded, before the filter box and sort touch it.
    readonly property var loadedSections: categories.map(category => {
        const data = sectionData(category.key);
        return { key: category.key, title: category.title, empty: category.empty,
                 items: data.items || [], error: data.error || "" };
    })
    readonly property var sections: loadedSections.map(section => visibleSection(section))
    readonly property var shelfSections: query ? sections.filter(section => section.items.length > 0) : sections
    readonly property var selectedSection: sections.find(section => section.key === filter) || ({ items: [] })
    readonly property var counts: tally(loadedSections)

    signal albumRequested(var album)
    signal artistRequested(var artist)
    signal playlistRequested(var playlist)
    signal unsupportedRequested(var media)

    // YouTube's sections come from the home controller; local ones from this computer's library.
    function sectionData(key) {
        if (key === "localPlaylists")
            return { items: NumaLocal.playlists };
        if (key === "localSongs")
            return { items: NumaLocal.songs };
        const section = (NumaHome.librarySections || []).find(section => section.key === key) || {};
        const cloud = typeof NumaSync !== "undefined" && NumaSync.revision >= 0 ? NumaSync.section(key) : [];
        const normalized = id => key === "playlists" ? String(id || "").replace(/^VL/, "").replace(/^LM$/, "FEmusic_liked_videos") : id;
        const ids = new Set(cloud.map(item => normalized(item.id)));
        const native = (section.items || []).filter(item => {
            const id = item.id || item.playlistId || item.browseId || "";
            if (ids.has(normalized(id))) return false;
            if (typeof NumaSync === "undefined") return true;
            if (key === "playlists") return !NumaSync.isHiddenPlaylist(id);
            return key !== "artists" && key !== "albums" || NumaSync.isSaved(key, id);
        });
        return {items: cloud.concat(native), error: cloud.length ? "" : section.error};
    }

    function isSongs(key) {
        return key === "songs" || key === "localSongs" || key === "metrics";
    }

    function isPlaylists(key) {
        return key === "playlists" || key === "localPlaylists";
    }

    function tally(list) {
        const result = {};
        for (const section of list)
            result[section.key] = section.items.length;
        return result;
    }

    function summaryText() {
        const parts = [];
        for (const category of categories) {
            const count = counts[category.key];
            if (count > 0)
                parts.push(count.toLocaleString(Qt.locale(), "f", 0) + " " + category.title.toLowerCase());
        }
        return parts.join(" · ");
    }

    function matches(item) {
        const text = [item.title, item.artist, (item.artists || []).join(" "), item.album, item.subtitle]
            .filter(Boolean).join(" ").toLowerCase();
        return text.includes(query);
    }

    function sortValue(item) {
        if (sortKey === "artist")
            return (item.artists || []).join(", ") || item.artist;
        if (sortKey === "duration")
            return Number(item.durationSeconds) || 0;
        return item[sortKey] || "";
    }

    function sortedTracks(items) {
        const direction = sortDescending ? -1 : 1;
        return items.slice().sort((a, b) => {
            const left = sortValue(a);
            const right = sortValue(b);
            return direction * (typeof left === "number" ? left - right : String(left).localeCompare(String(right)));
        });
    }

    // Filter first, then sort, so the queue plays what the table shows.
    function visibleSection(section) {
        let items = query ? section.items.filter(item => matches(item)) : section.items;
        if (isSongs(section.key) && sortKey)
            items = sortedTracks(items);
        return { key: section.key, title: section.title, empty: section.empty,
                 error: section.error, items: items, total: section.items.length };
    }

    function sectionStatus(section) {
        if (String(section.key || "").startsWith("local"))
            return query && section.total > 0
                ? qsTr("Nothing in %1 matches \"%2\".").arg(section.title.toLowerCase()).arg(header.query.trim())
                : section.empty || "";
        if (NumaHome.libraryLoading)
            return qsTr("Loading your library…");
        if (section.error)
            return qsTr("Could not load %1: %2").arg(section.title.toLowerCase()).arg(section.error);
        if (query && section.total > 0)
            return qsTr("Nothing in %1 matches \"%2\".").arg(section.title.toLowerCase()).arg(header.query.trim());
        if (NumaHome.libraryError)
            return qsTr("Refresh to try again.");
        return section.empty || "";
    }

    function resetScroll() {
        shelves.positionViewAtBeginning();
        grid.positionViewAtBeginning();
        songs.positionViewAtBeginning();
    }

    function activate(media, section) {
        if (media.type === "album") {
            albumRequested(media);
        } else if (media.type === "artist") {
            artistRequested(media);
        } else if (media.type === "playlist") {
            playlistRequested(media);
        } else if (media.type === "video") {
            unsupportedRequested(media);
        } else if (media.id && !media.unplayable) {
            const tracks = [];
            const items = section.items || [];
            const isSongsSection = section.key === "songs";
            // The queue gets this record crate, not the entire warehouse.
            for (let index = 0; index < items.length; ++index) {
                const item = items[index];
                if (item.id && !item.unplayable && (item.type === "song" || item.type === "track"))
                    tracks.push(isSongsSection ? Object.assign({}, item, { liked: true }) : item);
            }
            const index = tracks.findIndex(track => track.id === media.id);
            if (index >= 0)
                NumaPlayback.playCollection(tracks, index);
        }
    }

    onVisibleChanged: if (visible) NumaHome.loadLibrary()
    Component.onCompleted: if (visible) NumaHome.loadLibrary()
    onQueryChanged: resetScroll()
    // Views swap on filter; fade the incoming one so the swap isn't a hard cut.

    NumberAnimation {
        id: filterEntrance
        property: "opacity"
        from: 0
        to: 1
        duration: Motion.normal
        easing.type: Motion.enter
    }
    onFilterChanged: {
        resetScroll();
        filterEntrance.target = filter === "all" ? shelves : isSongs(filter) ? songs : grid;
        filterEntrance.restart();
    }

    component Caption: Text {
        color: "#f2eee7"
        font.family: "Inter"
        font.pixelSize: 13
    }

    Column {
        id: headerColumn
        width: parent.width
        spacing: 18

        LibraryHeader {
            id: header
            width: parent.width
            categories: root.categories
            counts: root.counts
            filter: root.filter
            summary: root.summaryText()
            loading: NumaHome.libraryLoading
            onFilterSelected: key => root.filter = key
            onRefreshRequested: NumaHome.refreshLibrary()
        }

        Row {
            width: parent.width
            spacing: 8
            visible: NumaHome.libraryLoading || Boolean(NumaHome.libraryError)

            BusyIndicator {
                width: 22
                height: 22
                running: root.visible && NumaHome.libraryLoading
                visible: NumaHome.libraryLoading
            }
            Caption {
                width: parent.width - (NumaHome.libraryLoading ? 30 : 0)
                text: NumaHome.libraryLoading ? qsTr("Loading your library…") : NumaHome.libraryError
                color: NumaHome.libraryError ? "#e6a197" : "#a4adb1"
                wrapMode: Text.Wrap
            }
        }
    }

    DetailListView {
        id: shelves
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: headerColumn.bottom
        anchors.topMargin: 24
        anchors.bottom: parent.bottom
        visible: root.filter === "all"
        spacing: 24
        cacheBuffer: 400
        model: root.shelfSections
        ScrollBar.vertical.width: 5

        delegate: Column {
            id: sectionColumn
            required property var modelData
            readonly property int shelfLimit: root.isSongs(modelData.key) ? 24 : 20
            // Shelves peek at the start; See all opens the full category.
            readonly property var shelfSection: ({ key: modelData.key, title: modelData.title,
                                                    items: modelData.items.slice(0, shelfLimit) })
            width: shelves.width
            spacing: 12

            ShelfView {
                width: parent.width
                height: visible ? implicitHeight : 0
                visible: sectionColumn.modelData.items.length > 0
                section: sectionColumn.shelfSection
                presentation: root.isSongs(section.key) ? "songlist" : root.isPlaylists(section.key) ? "playlist" : "album"
                songsEnabled: true
                seeAllEnabled: true
                onSeeAllRequested: root.filter = sectionColumn.modelData.key
                onSongRequested: song => root.activate(song, sectionColumn.modelData)
                onMediaRequested: media => root.activate(media, sectionColumn.modelData)
                onUnsupportedRequested: media => root.unsupportedRequested(media)
                onAlbumRequested: album => root.albumRequested(album)
                onArtistRequested: artist => root.artistRequested(artist)
                onPlaylistRequested: playlist => root.playlistRequested(playlist)
                onVerticalScrollRequested: (delta, smooth) => shelves.scrollBy(delta, smooth)
            }

            Caption {
                width: parent.width
                visible: sectionColumn.modelData.items.length === 0
                text: sectionColumn.modelData.title
                font.pixelSize: 20
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }
            Caption {
                width: parent.width
                visible: sectionColumn.modelData.items.length === 0 || Boolean(sectionColumn.modelData.error)
                text: root.sectionStatus(sectionColumn.modelData)
                color: sectionColumn.modelData.error ? "#e6a197" : "#a4adb1"
                wrapMode: Text.Wrap
                bottomPadding: 12
            }
        }

        footer: Item { width: 1; height: 28 }

        Caption {
            anchors.centerIn: parent
            width: Math.min(parent.width - 48, 440)
            visible: shelves.count === 0 && root.query !== "" && !NumaHome.libraryLoading
            text: qsTr("Nothing in your library matches \"%1\".").arg(header.query.trim())
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap
            color: "#a4adb1"
        }
    }

    LibrarySongList {
        id: songs
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: headerColumn.bottom
        anchors.topMargin: 24
        anchors.bottom: parent.bottom
        visible: root.isSongs(root.filter)
        tracks: root.isSongs(root.filter) ? root.selectedSection.items : []
        sortKey: root.sortKey
        sortDescending: root.sortDescending
        emptyText: root.sectionStatus(root.selectedSection)
        emptyColor: root.selectedSection.error ? "#e6a197" : "#a4adb1"
        onSortRequested: (key, descending) => {
            root.sortKey = key;
            root.sortDescending = descending;
        }
        onTrackRequested: track => root.activate(track, root.selectedSection)
        onAlbumRequested: album => root.albumRequested(album)
        onArtistRequested: artist => root.artistRequested(artist)
        onPlaylistRequested: playlist => root.playlistRequested(playlist)
    }

    // Virtualize a full category so large libraries don't create every card at once.
    GridView {
        id: grid
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: headerColumn.bottom
        anchors.topMargin: 24
        anchors.bottom: parent.bottom
        visible: root.filter !== "all" && !root.isSongs(root.filter)
        clip: true
        boundsBehavior: Flickable.StopAtBounds
        readonly property string presentation: root.isPlaylists(root.filter) ? "playlist" : "album"
        readonly property int cardWidth: presentation === "playlist" ? 178 : 158
        cellWidth: width / Math.max(1, Math.floor(width / (cardWidth + 14)))
        cellHeight: (presentation === "playlist" ? 242 : 210) + 14
        model: visible ? root.selectedSection.items : []
        cacheBuffer: 300
        bottomMargin: shelves.playerInset
        ScrollBar.vertical: ScrollBar { width: 5; bottomPadding: shelves.playerInset }

        delegate: MediaCard {
            required property var modelData
            media: modelData
            width: Math.min(grid.cardWidth, grid.cellWidth - 14)
            presentation: grid.presentation
            navigable: media.type === "album" || media.type === "artist" || media.type === "playlist"
            onActivated: media => root.activate(media, root.selectedSection)
            onAlbumRequested: album => root.albumRequested(album)
            onArtistRequested: artist => root.artistRequested(artist)
            onPlaylistRequested: playlist => root.playlistRequested(playlist)
        }

        Caption {
            anchors.centerIn: parent
            width: Math.min(parent.width - 48, 440)
            visible: grid.count === 0
            text: root.sectionStatus(root.selectedSection)
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap
            color: root.selectedSection.error ? "#e6a197" : "#a4adb1"
        }

        footer: Item { width: 1; height: 28 }

        FlickableWheel {
            view: grid
        }
    }
}
