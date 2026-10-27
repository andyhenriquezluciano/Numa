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
import QtQuick
import QtQuick.Controls
import Numa
import "."

Popup {
    id: root

    required property Item backdrop

    property var spotlightRows: []
    property int activeIndex: 0

    signal pageRequested(string page)
    signal searchRequested(string query)
    signal detailRequested(string page, var item)
    signal songRequested(var song)
    signal unsupportedRequested(var media)
    signal settingsRequested
    signal docsRequested
    signal releasesRequested
    signal supportRequested
    signal queueRequested

    parent: Overlay.overlay
    x: parent ? Math.round((parent.width - width) / 2) : 0
    y: 64
    width: Math.min(620, parent.width - 32)
    height: Math.min(520, parent.height - 96)
    padding: 0
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

    Overlay.modal: Rectangle {
        color: "#75000000"
    }

    // Silky smooth entrance: fade + gentle scale up
    enter: Transition {
        ParallelAnimation {
            NumberAnimation {
                property: "opacity"
                from: 0.0
                to: 1.0
                duration: 220
                easing.type: Easing.OutCubic
            }
            NumberAnimation {
                property: "scale"
                from: 0.96
                to: 1.0
                duration: 220
                easing.type: Easing.OutCubic
            }
        }
    }

    // Graceful exit: fade + slight shrink
    exit: Transition {
        ParallelAnimation {
            NumberAnimation {
                property: "opacity"
                from: 1.0
                to: 0.0
                duration: 180
                easing.type: Easing.InCubic
            }
            NumberAnimation {
                property: "scale"
                from: 1.0
                to: 0.96
                duration: 180
                easing.type: Easing.InCubic
            }
        }
    }

    background: Loader {
        active: root.visible
        sourceComponent: Item {
            Glass {
                anchors.fill: parent
                backdrop: root.backdrop
                radius: 14
                visible: !NumaAppearance.performanceMode
            }

            Rectangle {
                anchors.fill: parent
                radius: 14
                color: NumaAppearance.rosePine ? NumaAppearance.surfaceColor : "#180e1210"
                border.color: NumaAppearance.performanceMode ? "#35ffffff" : "#28ffffff"
                border.width: 1
            }
        }
    }

    function openSpotlight(initialQuery) {
        spotlightContent.setQuery(initialQuery || "");
        root.activeIndex = 0;
        root.open();
        root.updateRows();
    }

    onAboutToShow: {
        root.activeIndex = 0;
        root.updateRows();
    }

    onOpened: {
        spotlightContent.focusInput();
    }

    function moveActiveIndex(offset) {
        if (!spotlightRows.length)
            return;
        root.activeIndex = (root.activeIndex + offset + spotlightRows.length) % spotlightRows.length;
        spotlightContent.positionIndex(root.activeIndex);
    }

    function activateRow(row) {
        if (!row || !row.action)
            return;
        root.close();
        row.action();
    }

    function activateActiveRow() {
        if (spotlightRows.length > 0 && root.activeIndex >= 0 && root.activeIndex < spotlightRows.length) {
            activateRow(spotlightRows[root.activeIndex]);
        } else if (spotlightContent.query.trim().length > 0) {
            const q = spotlightContent.query.trim();
            root.close();
            root.searchRequested(q);
        }
    }

    function activateMedia(item, category) {
        if (category === "videos" || item.type === "video") {
            root.unsupportedRequested(item);
        } else if (item.type === "album" || category === "albums") {
            root.detailRequested("album", item);
        } else if (item.type === "artist" || category === "artists") {
            root.detailRequested("artist", item);
        } else if (item.type === "playlist" || category === "playlists") {
            root.detailRequested("playlist", item);
        } else {
            root.songRequested(item);
        }
    }

    function buildRows() {
        const queryText = spotlightContent.query.trim();
        const q = queryText.toLowerCase();
        const rows = [];

        // Only use things we have now in v3:
        const allCommands = [
            {
                id: "cmd:home",
                title: qsTr("Home"),
                subtitle: qsTr("Library and recommendations"),
                icon: "home",
                type: qsTr("Page"),
                action: () => { root.pageRequested("home"); }
            },
            {
                id: "cmd:library",
                title: qsTr("Your library"),
                subtitle: qsTr("Saved playlists and songs"),
                icon: "library-big",
                type: qsTr("Page"),
                action: () => { root.pageRequested("library"); }
            },
            {
                id: "cmd:queue",
                title: qsTr("Queue"),
                subtitle: qsTr("Upcoming playback queue"),
                icon: "music-2",
                type: qsTr("Action"),
                action: () => { root.queueRequested(); }
            },
            {
                id: "cmd:settings",
                title: qsTr("Settings"),
                subtitle: qsTr("Playback, appearance, and integrations"),
                icon: "compass",
                type: qsTr("Settings"),
                action: () => { root.settingsRequested(); }
            },
            {
                id: "cmd:docs",
                title: qsTr("Docs"),
                subtitle: qsTr("Guides for every NÜMA feature"),
                icon: "book-open",
                type: qsTr("Help"),
                action: () => { root.docsRequested(); }
            },
            {
                id: "cmd:releases",
                title: qsTr("Release notes"),
                subtitle: qsTr("What changed in each NÜMA release"),
                icon: "sparkles",
                type: qsTr("Help"),
                action: () => { root.releasesRequested(); }
            },
            {
                id: "cmd:support",
                title: qsTr("Report a bug"),
                subtitle: qsTr("Send a bug, idea, or feedback with a screenshot"),
                icon: "bug",
                type: qsTr("Help"),
                action: () => { root.supportRequested(); }
            }
        ];

        if (!queryText) {
            rows.push({
                id: "cmd:search",
                title: qsTr("Search NÜMA"),
                subtitle: qsTr("Songs, albums, artists, and playlists"),
                icon: "search",
                type: qsTr("Page"),
                action: () => { root.pageRequested("search"); }
            });
            for (let i = 0; i < allCommands.length; ++i) {
                rows.push(allCommands[i]);
            }
            return rows;
        }

        // Search query row is king of the hill when typing
        rows.push({
            id: "cmd:full-search",
            title: qsTr("Search for “%1”").arg(queryText),
            subtitle: qsTr("Open full search results"),
            icon: "search",
            type: qsTr("Search"),
            action: () => { root.searchRequested(queryText); }
        });

        // Filter matching navigation commands
        for (let i = 0; i < allCommands.length; ++i) {
            const cmd = allCommands[i];
            const searchable = (cmd.title + " " + cmd.subtitle).toLowerCase();
            if (searchable.includes(q)) {
                rows.push(cmd);
            }
        }

        // Map live catalog sections into spotlight results
        const sections = NumaSearch.sections || [];
        for (let s = 0; s < sections.length; ++s) {
            const section = sections[s];
            const items = section.items || [];
            for (let i = 0; i < items.length; ++i) {
                const item = items[i];
                const sectionTitle = section.title || "";
                const credit = (item.artists || []).join(", ") || item.artist || item.subtitle || "";
                const subtitleParts = [];
                if (credit)
                    subtitleParts.push(credit);
                if (sectionTitle && sectionTitle !== "Top result" && sectionTitle !== "Songs") {
                    subtitleParts.push(sectionTitle);
                }

                let typeLabel = "";
                let fallbackIcon = "music-2";
                if (item.type === "song" || section.key === "songs") {
                    typeLabel = qsTr("Song");
                    fallbackIcon = "music-2";
                } else if (item.type === "album" || section.key === "albums") {
                    typeLabel = qsTr("Album");
                    fallbackIcon = "library-big";
                } else if (item.type === "artist" || section.key === "artists") {
                    typeLabel = qsTr("Artist");
                    fallbackIcon = "circle-user-round";
                } else if (item.type === "playlist" || section.key === "playlists") {
                    typeLabel = qsTr("Playlist");
                    fallbackIcon = "library-big";
                } else if (item.type === "video" || section.key === "videos") {
                    typeLabel = qsTr("Video");
                    fallbackIcon = "play";
                } else {
                    typeLabel = item.type || "";
                }

                rows.push({
                    id: "media:" + section.key + ":" + (item.id || i),
                    title: item.title || qsTr("Untitled"),
                    subtitle: subtitleParts.join(" • "),
                    type: typeLabel,
                    icon: fallbackIcon,
                    artwork: typeof NumaSync !== "undefined" && NumaSync.revision >= 0 && item.type === "playlist" ? NumaSync.artworkFor(item.playlistId || item.browseId || item.id || "", item.thumbnail || "") : item.thumbnail || "",
                    item: item,
                    category: section.key,
                    action: () => {
                        root.activateMedia(item, section.key);
                    }
                });

                if (rows.length >= 10)
                    break;
            }
            if (rows.length >= 10)
                break;
        }

        return rows;
    }

    function updateRows() {
        root.spotlightRows = buildRows();
        if (root.activeIndex >= root.spotlightRows.length) {
            root.activeIndex = Math.max(0, root.spotlightRows.length - 1);
        }
    }

    contentItem: SpotlightContent {
        id: spotlightContent
        controller: root
    }
}
