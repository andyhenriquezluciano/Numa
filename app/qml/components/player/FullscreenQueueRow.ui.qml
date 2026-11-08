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
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// One Up next entry. Rows cascade in whenever the pane swaps to the queue.
ItemDelegate {
    id: row
    HandCursor {}

    property var track: ({ title: "Midnight City", artist: "M83", duration: "4:03" })
    property int rowIndex: 0
    property bool previous: false
    property bool current: false
    property var dragPane: null
    property int queueIndex: -1
    property bool dragPreview: false
    readonly property bool draggable: dragPane !== null && queueIndex >= 0 && !previous && !current && !dragPreview
    property string sectionLabel: ""
    readonly property int sectionHeight: sectionLabel ? 26 : 0
    // Artist line; the logic layer joins the artists list when there is no single artist.
    property string artistText: (track.artists || []).join(", ") || track.artist || ""
    property real paneSwap: 1
    property color accentColor: NumaAppearance.textColor
    property color primaryText: "#f7f5f0"
    property color mutedText: "#8d928a"
    property alias removeButton: removeButton
    readonly property real enter: dragPreview ? 1 : Math.max(0, Math.min(1, paneSwap * 1.8 - Math.min(rowIndex, 8) * 0.1))

    width: 400
    height: 62 + sectionHeight
    padding: 8
    leftPadding: 12
    rightPadding: 12
    topPadding: sectionHeight + 8
    bottomPadding: 8
    topInset: sectionHeight
    hoverEnabled: true
    opacity: enter * (previous ? 0.6 : 1) * (!dragPreview && dragPane && dragPane.dragIndex === queueIndex ? 0.35 : 1)
    Behavior on opacity {
        enabled: !row.dragPreview && row.paneSwap >= 0.999
        NumberAnimation { duration: 160; easing.type: Easing.OutCubic }
    }
    transform: Translate { x: 30 * (1 - row.enter) }
    Accessible.name: (track.title || "") + ", " + artistText

    Rectangle {
        x: 8
        y: row.sectionHeight
        width: parent.width - 16
        height: 2
        radius: 1
        color: row.accentColor
        visible: !row.dragPreview && row.draggable && row.dragPane.dragIndex >= 0 && row.dragPane.dropIndex === row.queueIndex
    }

    Text {
        visible: row.sectionLabel !== ""
        x: 8
        y: 8
        text: row.sectionLabel
        color: row.mutedText
        font.family: "Inter"
        font.pixelSize: 10
        font.bold: true
        font.letterSpacing: 0.9
    }

    background: RoundedSurfaceFrame {
        radius: 14
        softGlow: row.hovered || row.down || row.activeFocus
        showBorder: softGlow
        fillColor: NumaAppearance.theme === "rose_pine_dawn" ? (row.current || row.down ? NumaAppearance.highlightColor : row.hovered || row.dragPreview ? "#ede8e4" : "transparent")
            : row.current ? Qt.rgba(row.accentColor.r, row.accentColor.g, row.accentColor.b, 0.18)
            : row.down ? "#24ffffff" : row.hovered || row.dragPreview ? "#16ffffff" : "transparent"
        border.color: row.activeFocus ? row.accentColor : row.hovered ? "#32ffffff" : "transparent"
        Behavior on fillColor {
            enabled: NumaAppearance.theme !== "rose_pine_dawn"
            ColorAnimation { duration: 140 }
        }
        Behavior on border.color { ColorAnimation { duration: 140 } }
    }

    contentItem: RowLayout {
        spacing: 14

        Item {
            visible: row.dragPane !== null
            Layout.preferredWidth: 18
            Layout.fillHeight: true
            Grid {
                anchors.centerIn: parent
                columns: 2
                spacing: 3
                opacity: row.draggable && (row.hovered || dragArea.containsMouse || dragArea.pressed) ? 0.7 : 0
                Repeater {
                    model: 6
                    Rectangle { width: 2.5; height: 2.5; radius: 1.25; color: row.mutedText }
                }
            }
            MouseArea {
                id: dragArea
                objectName: "fullscreenQueueDragHandle"
                anchors.fill: parent
                enabled: row.draggable
                hoverEnabled: true
                preventStealing: true
                cursorShape: pressed ? Qt.ClosedHandCursor : Qt.OpenHandCursor
                onPressed: function(mouse) { row.dragPane.dragStarted(row.queueIndex, mapToItem(row.dragPane.list, mouse.x, mouse.y).y); }
                onPositionChanged: function(mouse) { if (pressed) row.dragPane.dragMoved(mapToItem(row.dragPane.list, mouse.x, mouse.y).y); }
                onReleased: row.dragPane.dragFinished()
                onCanceled: row.dragPane.dragCanceled()
            }
        }

        RoundedArtwork {
            Layout.preferredWidth: 46
            Layout.preferredHeight: 46
            radius: 8
            source: row.track.thumbnail || ""
            scale: row.hovered ? 1.06 : 1
            Behavior on scale { NumberAnimation { duration: 240; easing.type: Easing.OutBack; easing.overshoot: 2 } }
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            Text {
                Layout.fillWidth: true
                text: row.track.title || ""
                color: row.current ? row.accentColor : row.primaryText
                font.family: "Inter"
                font.pixelSize: 15
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }

            Text {
                Layout.fillWidth: true
                text: row.artistText
                color: row.mutedText
                font.family: "Inter"
                font.pixelSize: 12
                elide: Text.ElideRight
            }
        }

        Text {
            text: row.track.duration || ""
            color: row.mutedText
            font.family: "Inter"
            font.pixelSize: 12
        }

        FullscreenIconButton {
            id: removeButton
            visible: !row.previous && !row.current
            objectName: "removeFullscreenQueuedSong"
            Layout.preferredWidth: 32
            Layout.preferredHeight: 32
            glyph: "x"
            glyphSize: 16
            accentColor: row.accentColor
            primaryText: row.primaryText
            secondaryText: row.mutedText
            ToolTip.text: qsTr("Remove %1 from queue").arg(row.track.title || "")
        }
    }
}
