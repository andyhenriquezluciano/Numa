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

import ".."
import "../home"
import Numa
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window

// Floating glass queue card. Lives outside the backdrop it blurs, same as the player pill.
Item {
    id: root

    required property Item backdrop
    property bool backdropLive: false
    property bool open: false
    property bool compactMode: false
    property color accentColor: NumaAppearance.textColor
    property color mixAccentColor: NumaAppearance.textColor
    property color inkColor: "#0b0d0a"
    readonly property bool compactDetailsOpen: compactNowPlaying.open
    readonly property real compactPulse: compactNowPlaying.artworkPulse
    // Off under fullscreen: two 400 MB decoders for one visible cover is a bit much.
    property bool motionArtwork: true

    // Lyrics swap in over the queue list; the hero and header stay put.
    property bool lyricsMode: false
    property real lyricsReveal: lyricsMode ? 1 : 0
    Behavior on lyricsReveal {
        NumberAnimation { duration: 320; easing.type: Easing.OutCubic }
    }

    signal closeRequested()
    signal compactExitRequested()

    property int dragIndex: -1
    property int dropIndex: -1
    property real dragY: 0

    // 0 hidden, 1 shown. Drives the slide and the row cascade.
    property real reveal: open ? 1 : 0
    Behavior on reveal {
        NumberAnimation { duration: 340; easing.type: Easing.OutCubic }
    }

    readonly property color primaryText: NumaAppearance.textColor
    readonly property color secondaryText: NumaAppearance.mutedColor
    readonly property color mutedText: "#858a82"
    readonly property real radius: compactMode ? 20 : 22
    readonly property var queue: NumaPlayback.queue || []
    // Continuous lists played songs, the current song and the queue as one list.
    readonly property bool continuous: NumaPlayback.queueLayout === "continuous"
    readonly property string trackId: NumaPlayback.track.id || ""
    // Snapshot so list rows rebuild on song changes, not on every playback tick.
    property var currentTrack: ({})
    readonly property var history: continuous ? (NumaPlayback.history || []) : []
    readonly property bool hasCurrent: continuous && trackId !== ""
    readonly property int leading: history.length + (hasCurrent ? 1 : 0)
    readonly property var rows: continuous
        ? history.concat(hasCurrent ? [currentTrack] : [], queue) : queue
    readonly property real queueSeconds: {
        let total = 0;
        for (let i = 0; i < root.queue.length; ++i)
            total += root.seconds(root.queue[i]);
        return total;
    }

    function showCurrent() {
        if (continuous && hasCurrent)
            Qt.callLater(() => queueList.positionViewAtIndex(history.length, ListView.Beginning));
    }

    function showCompactArtwork() {
        if (!compactMode || !trackId)
            return;
        lyricsMode = false;
        compactNowPlaying.open = true;
    }

    onTrackIdChanged: {
        currentTrack = NumaPlayback.track;
        showCurrent();
    }
    onContinuousChanged: showCurrent()
    onOpenChanged: showCurrent()
    Component.onCompleted: currentTrack = NumaPlayback.track

    function tint(color, alpha) {
        return Qt.rgba(color.r, color.g, color.b, alpha);
    }

    function seconds(track) {
        if (!track)
            return 0;
        const direct = Number(track.durationSeconds || 0);
        if (direct > 0)
            return direct;
        const parts = String(track.duration || "").split(":");
        let result = 0;
        for (let i = 0; i < parts.length; ++i) {
            const n = parseInt(parts[i], 10);
            if (!Number.isFinite(n))
                return 0;
            result = result * 60 + n;
        }
        return result;
    }

    function clock(value) {
        if (!Number.isFinite(value) || value <= 0)
            return "";
        const total = Math.floor(value);
        const minutes = Math.floor(total / 60);
        return minutes + ":" + String(total % 60).padStart(2, "0");
    }

    function lengthLabel(value) {
        const minutes = Math.round(value / 60);
        if (minutes < 60)
            return qsTr("%1 min").arg(minutes);
        return qsTr("%1 hr %2 min").arg(Math.floor(minutes / 60)).arg(minutes % 60);
    }

    // If someone drags a song past the event horizon, clamp it instead of summoning a black hole.
    function updateDrop() {
        const index = queueList.indexAt(1, dragY + queueList.contentY) - leading;
        dropIndex = index >= 0 ? index : (dragY < 0 ? 0 : queue.length - 1);
    }

    visible: reveal > 0.001
    opacity: Math.min(1, reveal * 1.4)
    transform: Translate { x: (1 - root.reveal) * (root.width * 0.35 + 24) }
    Keys.onEscapePressed: root.compactMode ? root.compactExitRequested() : root.closeRequested()

    Connections {
        target: NumaPlayback
        function onQueueChanged() {
            root.dragIndex = -1;
            root.dropIndex = -1;
        }
    }

    // Auto-scroll when dragging tracks close to the list boundaries
    Timer {
        interval: 40
        repeat: true
        running: root.dragIndex >= 0
        onTriggered: {
            const delta = root.dragY < 30 ? -12 : root.dragY > queueList.height - 30 ? 12 : 0;
            queueList.contentY = Math.max(0, Math.min(Math.max(0, queueList.contentHeight - queueList.height), queueList.contentY + delta));
            root.updateDrop();
        }
    }

    // A painted card is not a bouncer: explicitly stop input passing through.
    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.AllButtons
        onWheel: function(wheel) { wheel.accepted = true; }
    }

    Glass {
        id: glass
        anchors.fill: parent
        backdrop: root.backdrop
        radius: root.radius
        live: root.backdropLive && root.visible
        shadowOpacity: root.compactMode ? 0 : 0.16
        shadowSpread: root.compactMode ? 0 : 18
        shadowOffsetY: root.compactMode ? 0 : 10
        visible: !root.compactDetailsOpen
    }

    // Ink wash for legibility, plus an accent glow bleeding in from the top.
    RoundedSurfaceFrame {
        anchors.fill: parent
        radius: root.radius
        softGlow: false
        showBorder: true
        fillColor: NumaAppearance.rosePine ? NumaAppearance.surfaceColor : root.tint(root.inkColor, root.compactMode ? 0.34 : 0.6)
        visible: !root.compactDetailsOpen
    }

    Rectangle {
        anchors.fill: parent
        radius: root.radius
        antialiasing: true
        gradient: Gradient {
            GradientStop { position: 0; color: root.tint(root.accentColor, root.compactMode ? 0.08 : 0.16) }
            GradientStop { position: 0.4; color: "transparent" }
        }
        visible: !root.compactDetailsOpen && !NumaAppearance.performanceMode
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 12
        visible: !root.compactDetailsOpen

        RowLayout {
            Layout.fillWidth: true
            spacing: 8

            DragHandler {
                target: null
                enabled: root.compactMode
                grabPermissions: PointerHandler.ApprovesTakeOverByAnything
                onActiveChanged: {
                    if (active && root.Window.window)
                        root.Window.window.startSystemMove();
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 1

                Text {
                    text: root.lyricsMode ? qsTr("Lyrics") : qsTr("Queue")
                    color: root.primaryText
                    font.family: "Inter"
                    font.pixelSize: 20
                    font.bold: true
                    font.letterSpacing: -0.4
                }

                Text {
                    Layout.fillWidth: true
                    text: root.lyricsMode
                          ? (NumaLyrics.status === "ready"
                             ? (NumaLyrics.mode === "synced" ? qsTr("Synced to the music") : qsTr("Not time-synced"))
                             : NumaLyrics.status === "loading" ? qsTr("Looking around…") : qsTr("Nothing to sing along to"))
                          : root.queue.length === 0 ? qsTr("Nothing lined up")
                          : root.queueSeconds > 0
                          ? qsTr("%n song(s) · %1", "", root.queue.length).arg(root.lengthLabel(root.queueSeconds))
                          : qsTr("%n song(s)", "", root.queue.length)
                    color: root.mutedText
                    font.family: "Inter"
                    font.pixelSize: 11
                    elide: Text.ElideRight
                }
            }

            CompactWindowButton {
                objectName: "lyricsToggle"
                colors: root
                glyph: root.lyricsMode ? "list-music" : "mic-vocal"
                active: root.lyricsMode
                label: root.lyricsMode ? qsTr("Show queue") : qsTr("Show lyrics")
                onClicked: root.lyricsMode = !root.lyricsMode
            }

            CompactWindowButton {
                colors: root
                visible: root.compactMode
                glyph: "maximize"
                label: qsTr("Exit compact mode")
                onClicked: root.compactExitRequested()
            }

            CompactWindowButton {
                colors: root
                visible: root.compactMode
                glyph: ""
                minimizeGlyph: true
                label: qsTr("Minimize")
                onClicked: if (root.Window.window) root.Window.window.showMinimized()
            }

            CompactWindowButton {
                colors: root
                visible: root.compactMode
                glyph: "x"
                label: qsTr("Close")
                onClicked: if (root.Window.window) root.Window.window.close()
            }
        }

        QueueHero { panel: root; visible: !root.continuous || root.compactMode }

        // Queue and lyrics share this slot and cross-fade on the header toggle.
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true

            ColumnLayout {
                anchors.fill: parent
                spacing: 12
                visible: root.lyricsReveal < 0.999
                opacity: 1 - root.lyricsReveal
                enabled: !root.lyricsMode
                transform: Translate { x: -18 * root.lyricsReveal }

                // Up Next header with count, Best Mix and Clear
                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 2
                    spacing: 6

                    Text {
                        visible: !root.continuous
                        text: qsTr("UP NEXT")
                        color: root.mutedText
                        font.family: "Inter"
                        font.pixelSize: 10
                        font.bold: true
                        font.letterSpacing: 0.9
                    }

                    Item { Layout.fillWidth: true }

                    QueueBestMixButton { panel: root; count: root.queue.length }

                    Button {
                        id: clearBtn
                        objectName: "clearQueue"
                        visible: !root.compactMode
                        text: qsTr("Clear")
                        enabled: root.queue.length > 0
                        implicitHeight: 28
                        leftPadding: 10
                        rightPadding: 10
                        onClicked: NumaPlayback.clearQueue()
                        background: Rectangle {
                            radius: height / 2
                            color: clearBtn.down ? "#26ffffff" : clearBtn.hovered || clearBtn.activeFocus ? "#1affffff" : "transparent"
                            border.color: clearBtn.activeFocus ? root.accentColor : "transparent"
                        }
                        contentItem: Text {
                            text: clearBtn.text
                            color: !clearBtn.enabled ? "#55ffffff" : clearBtn.hovered ? root.primaryText : root.secondaryText
                            font.family: "Inter"
                            font.pixelSize: 11
                            font.bold: true
                            verticalAlignment: Text.AlignVCenter
                        }
                    }
                }

                Text {
                    Layout.fillWidth: true
                    visible: Boolean(NumaPlayback.bestMix.error)
                    text: NumaPlayback.bestMix.error
                    color: "#e8c29b"
                    font.family: "Inter"
                    font.pixelSize: 11
                    wrapMode: Text.Wrap
                }

                QueueErrorBanner {}

                ListView {
                    id: queueList
                    objectName: "queueList"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.leftMargin: -6
                    Layout.rightMargin: -6
                    clip: true
                    cacheBuffer: root.dragIndex >= 0 ? contentHeight : 320
                    interactive: root.dragIndex < 0
                    spacing: 2
                    model: root.rows
                    boundsBehavior: Flickable.StopAtBounds

                    ScrollBar.vertical: ScrollBar {
                        id: vbar
                        active: true
                        policy: queueList.contentHeight > queueList.height ? ScrollBar.AsNeeded : ScrollBar.AlwaysOff
                        contentItem: Rectangle {
                            implicitWidth: 4
                            radius: 2
                            color: vbar.pressed ? "#80ffffff" : vbar.hovered ? "#55ffffff" : "#30ffffff"
                        }
                    }

                    delegate: QueueRow { panel: root; list: queueList }

                    QueueEmptyState { panel: root; visible: root.rows.length === 0 }

                    FlickableWheel {
                        view: queueList
                        enabled: queueList.interactive
                    }
                }
            }

            LyricsView {
                objectName: "queueLyrics"
                anchors.fill: parent
                visible: root.lyricsReveal > 0.001
                opacity: root.lyricsReveal
                transform: Translate { x: 18 * (1 - root.lyricsReveal) }
                accentColor: root.accentColor
                primaryText: root.primaryText
                mutedText: root.mutedText
            }
        }

        QueueAutoplayCard {
            panel: root
            visible: !root.compactMode && root.lyricsReveal < 0.999
        }
    }

    CompactNowPlaying {
        id: compactNowPlaying
        anchors.fill: parent
        panel: root
    }

    // Floating drag preview
    Rectangle {
        visible: root.dragIndex >= 0 && !root.compactDetailsOpen
        z: 20
        x: 20
        y: Math.max(queueList.mapToItem(root, 0, 0).y,
                    Math.min(queueList.mapToItem(root, 0, queueList.height).y - height,
                             queueList.mapToItem(root, 0, root.dragY).y - height / 2))
        width: root.width - 40
        height: 48
        radius: 12
        color: Qt.rgba(root.inkColor.r * 0.8 + 0.08, root.inkColor.g * 0.8 + 0.08, root.inkColor.b * 0.8 + 0.08, 0.96)
        border.color: root.accentColor
        border.width: 1.5

        RowLayout {
            anchors.fill: parent
            anchors.margins: 6
            spacing: 10

            RoundedArtwork {
                Layout.preferredWidth: 36
                Layout.preferredHeight: 36
                radius: 7
                source: root.dragIndex >= 0 ? (root.queue[root.dragIndex]?.thumbnail || "") : ""
            }

            Text {
                Layout.fillWidth: true
                text: root.dragIndex >= 0 ? (root.queue[root.dragIndex]?.title || "") : ""
                color: root.primaryText
                font.family: "Inter"
                font.pixelSize: 12
                font.bold: true
                elide: Text.ElideRight
                verticalAlignment: Text.AlignVCenter
            }
        }
    }
}
