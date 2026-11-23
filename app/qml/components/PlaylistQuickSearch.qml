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
import QtQuick.Layouts
import Numa
import "."
import "home"

Popup {
    id: root

    required property Item backdrop
    property var tracks: []
    property var detail: ({})
    property string playlistTitle: detail.title || ""

    property var matches: []
    property int activeIndex: 0
    property int focusedTrackIndex: -1
    property var searchIndex: null
    readonly property string fallbackArtist: detail.author || detail.subtitle || ""

    signal matchFocused(int trackIndex)
    signal trackSelected(var track, int trackIndex)

    parent: Overlay.overlay
    x: parent ? Math.round((parent.width - width) / 2) : 0
    y: 70
    width: Math.min(520, parent.width - 32)
    height: Math.min(460, contentCol.implicitHeight + 2)
    padding: 0
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

    Overlay.modal: Rectangle {
        color: "#75000000"
    }

    enter: Transition {
        ParallelAnimation {
            NumberAnimation {
                property: "opacity"
                from: 0.0
                to: 1.0
                duration: 200
                easing.type: Easing.OutCubic
            }
            NumberAnimation {
                property: "scale"
                from: 0.96
                to: 1.0
                duration: 200
                easing.type: Easing.OutCubic
            }
        }
    }

    exit: Transition {
        ParallelAnimation {
            NumberAnimation {
                property: "opacity"
                from: 1.0
                to: 0.0
                duration: 160
                easing.type: Easing.InCubic
            }
            NumberAnimation {
                property: "scale"
                from: 1.0
                to: 0.96
                duration: 160
                easing.type: Easing.InCubic
            }
        }
    }

    background: PopupBloomSurface {
        radius: 14
    }

    function normalizeText(value) {
        return String(value || "")
            .normalize("NFKD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    function matchScore(text, query, containsWhitespace, baseScore) {
        if (text === query)
            return baseScore;
        let position = text.indexOf(query);
        if (position < 0)
            return -1;
        if (position === 0)
            return baseScore + 1;
        if (!containsWhitespace) {
            while (position > 0) {
                if (/\s/.test(text[position - 1]))
                    return baseScore + 2;
                position = text.indexOf(query, position + 1);
            }
        }
        return baseScore + 3;
    }

    function trackArtist(track) {
        if (track.artists && track.artists.length) {
            const joined = track.artists.map(a => (a && a.name) ? a.name : a).filter(Boolean).join(", ");
            if (joined)
                return joined;
        }
        if (track.artist)
            return track.artist;
        return root.fallbackArtist;
    }

    function makeSearchIndex() {
        return tracks.map(track => {
            const artistDisplay = trackArtist(track);
            return {
                title: normalizeText(track.title),
                artist: normalizeText(artistDisplay),
                album: normalizeText(track.album),
                artistDisplay: artistDisplay
            };
        });
    }

    function invalidateSearchIndex() {
        searchIndex = null;
        if (visible)
            Qt.callLater(refreshVisibleMatches);
    }

    function refreshVisibleMatches() {
        if (visible)
            updateMatches();
    }

    onTracksChanged: invalidateSearchIndex()
    onFallbackArtistChanged: invalidateSearchIndex()

    function computeMatches(q) {
        const query = normalizeText(q);
        if (!query || !root.tracks || !root.tracks.length)
            return [];

        if (searchIndex === null)
            searchIndex = makeSearchIndex();
        const rows = searchIndex;
        const containsWhitespace = /\s/.test(query);
        const buckets = Array.from({ length: 12 }, () => []);
        const limit = 10;
        for (let i = 0; i < rows.length; ++i) {
            const row = rows[i];
            let score = matchScore(row.title, query, containsWhitespace, 0);
            if (score < 0)
                score = matchScore(row.artist, query, containsWhitespace, 4);
            if (score < 0)
                score = matchScore(row.album, query, containsWhitespace, 8);
            if (score >= 0 && buckets[score].length < limit)
                buckets[score].push(i);
            if (buckets[0].length === limit)
                break;
        }

        const results = [];
        for (let score = 0; score < buckets.length; ++score) {
            for (const i of buckets[score]) {
                const track = tracks[i];
                const subtitleParts = [];
                const artistDisplay = rows[i].artistDisplay;
                if (artistDisplay)
                    subtitleParts.push(artistDisplay);
                if (track.album)
                    subtitleParts.push(track.album);

                results.push({
                    id: `${track.id || i}:${i}`,
                    index: i,
                    score: score,
                    track: track,
                    title: track.title || qsTr("Untitled track"),
                    subtitle: subtitleParts.join(" • "),
                    artwork: track.thumbnail || root.detail.thumbnail || ""
                });
                if (results.length === limit)
                    return results;
            }
        }
        return results;
    }

    function updateMatches() {
        const q = searchInput.text.trim();
        root.matches = computeMatches(q);
        if (root.activeIndex >= root.matches.length) {
            root.activeIndex = Math.max(0, root.matches.length - 1);
        }
        notifyMatchFocus();
    }

    function notifyMatchFocus() {
        if (root.matches.length > 0 && root.activeIndex >= 0 && root.activeIndex < root.matches.length) {
            const targetIndex = root.matches[root.activeIndex].index;
            root.focusedTrackIndex = targetIndex;
            root.matchFocused(targetIndex);
        } else {
            root.focusedTrackIndex = -1;
        }
    }

    function moveActiveIndex(offset) {
        if (!root.matches.length)
            return;
        root.activeIndex = (root.activeIndex + offset + root.matches.length) % root.matches.length;
        resultsList.positionViewAtIndex(root.activeIndex, ListView.Contain);
        notifyMatchFocus();
    }

    function selectActiveMatch() {
        if (root.matches.length > 0 && root.activeIndex >= 0 && root.activeIndex < root.matches.length) {
            const match = root.matches[root.activeIndex];
            root.close();
            root.trackSelected(match.track, match.index);
        }
    }

    function openWithQuery(initialQuery) {
        searchInput.text = initialQuery || "";
        root.activeIndex = 0;
        root.open();
        root.updateMatches();
        searchInput.forceActiveFocus();
        searchInput.cursorPosition = searchInput.text.length;
    }

    onOpened: {
        searchInput.forceActiveFocus();
        searchInput.cursorPosition = searchInput.text.length;
    }

    onClosed: {
        root.focusedTrackIndex = -1;
    }

    contentItem: ColumnLayout {
        id: contentCol
        spacing: 0
        clip: true

        // Top Search Input Row
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 50
            Layout.leftMargin: 14
            Layout.rightMargin: 12
            spacing: 10

            LucideIcon {
                Layout.preferredWidth: 18
                Layout.preferredHeight: 18
                name: "search"
                color: "#9ca7a0"
            }

            TextField {
                id: searchInput
                Layout.fillWidth: true
                placeholderText: root.playlistTitle ? qsTr("Find in %1…").arg(root.playlistTitle) : qsTr("Find in playlist…")
                placeholderTextColor: "#6a7770"
                color: "#f2f5f2"
                font.family: "Inter"
                font.pixelSize: 14
                background: Item {}
                selectByMouse: true

                onTextEdited: {
                    root.updateMatches();
                }

                Keys.onDownPressed: function(event) {
                    event.accepted = true;
                    root.moveActiveIndex(1);
                }
                Keys.onUpPressed: function(event) {
                    event.accepted = true;
                    root.moveActiveIndex(-1);
                }
                Keys.onReturnPressed: function(event) {
                    event.accepted = true;
                    root.selectActiveMatch();
                }
                Keys.onEnterPressed: function(event) {
                    event.accepted = true;
                    root.selectActiveMatch();
                }
                Keys.onEscapePressed: function(event) {
                    event.accepted = true;
                    root.close();
                }
            }

            Text {
                text: {
                    if (!searchInput.text.trim())
                        return qsTr("Type to find");
                    const count = root.matches.length;
                    return count === 1 ? qsTr("1 match") : qsTr("%1 matches").arg(count);
                }
                color: "#748078"
                font.family: "Inter"
                font.pixelSize: 11
            }

            Button {
                id: clearBtn
                Layout.preferredWidth: 26
                Layout.preferredHeight: 26
                visible: searchInput.text.length > 0
                Accessible.name: qsTr("Clear search")
                onClicked: {
                    searchInput.clear();
                    root.updateMatches();
                    searchInput.forceActiveFocus();
                }
                background: Rectangle {
                    radius: 13
                    color: clearBtn.hovered ? "#22ffffff" : "transparent"
                }
                contentItem: Text {
                    text: "×"
                    color: "#a4aca6"
                    font.pixelSize: 18
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
            }

            Rectangle {
                Layout.preferredHeight: 20
                Layout.preferredWidth: escText.implicitWidth + 10
                radius: 4
                color: "#18ffffff"
                border.color: "#20ffffff"

                Text {
                    id: escText
                    anchors.centerIn: parent
                    text: "ESC"
                    color: "#828e86"
                    font.family: "Inter"
                    font.pixelSize: 9
                    font.weight: Font.DemiBold
                }

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.close()
                }
            }
        }

        // Subtly separates search bar from results
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 1
            color: "#14ffffff"
        }

        // Results List
        ListView {
            id: resultsList
            Layout.fillWidth: true
            Layout.preferredHeight: Math.min(320, contentHeight)
            Layout.margins: 6
            clip: true
            spacing: 2
            visible: root.matches.length > 0
            model: root.matches

            ScrollBar.vertical: ThemedScrollBar {
                width: 4; padding: 0
                policy: ScrollBar.AsNeeded
            }

            delegate: PlaylistQuickSearchRow {
                controller: root
            }

            FlickableWheel {
                view: resultsList
            }
        }

        // Empty Status Placeholder
        Item {
            Layout.fillWidth: true
            Layout.preferredHeight: 80
            visible: searchInput.text.trim().length > 0 && root.matches.length === 0

            ColumnLayout {
                anchors.centerIn: parent
                spacing: 6

                LucideIcon {
                    Layout.alignment: Qt.AlignHCenter
                    width: 24
                    height: 24
                    name: "search"
                    color: "#525e56"
                }

                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: qsTr("No tracks match “%1”").arg(searchInput.text.trim())
                    color: "#7e8a81"
                    font.family: "Inter"
                    font.pixelSize: 12
                }
            }
        }

        // Footer Hint Bar
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 1
            color: "#12ffffff"
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 26
            Layout.leftMargin: 12
            Layout.rightMargin: 12

            Text {
                text: qsTr("↑↓ Navigate   ↵ Play   Esc Close")
                color: "#556059"
                font.family: "Inter"
                font.pixelSize: 10
            }

            Item { Layout.fillWidth: true }

            Text {
                text: qsTr("Quick Search")
                color: "#4a544d"
                font.family: "Inter"
                font.pixelSize: 10
                font.weight: Font.DemiBold
            }
        }
    }
}
