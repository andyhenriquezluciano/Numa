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
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// Up next list with queue actions.
ColumnLayout {
    id: pane

    property var queueItems: [
        { title: "Midnight City", artist: "M83", duration: "4:03" },
        { title: "Intro", artist: "The xx", duration: "2:07" },
        { title: "Genesis", artist: "Grimes", duration: "4:15" }
    ]
    property bool autoplayEnabled: true
    property bool continuous: false
    property var historyItems: []
    property var currentTrack: ({})
    readonly property int historyOffset: continuous ? Math.max(0, historyItems.length - 2) : 0
    readonly property var visibleHistory: continuous ? historyItems.slice(historyOffset) : []
    readonly property int historyCount: visibleHistory.length
    readonly property bool hasCurrent: continuous && Boolean(currentTrack.id)
    readonly property int queueStart: historyCount + (hasCurrent ? 1 : 0)
    readonly property var rowItems: continuous ? visibleHistory.concat(hasCurrent ? [currentTrack] : [], queueItems) : queueItems
    property real paneSwap: 1
    property color accentColor: NumaAppearance.textColor
    property color primaryText: "#f7f5f0"
    property color secondaryText: "#c3c6bf"
    property color mutedText: "#8d928a"
    property alias list: upNext
    property alias clearButton: clearButton
    property alias scrollFollow: scrollFollow
    property int dragIndex: -1
    property int dropIndex: -1
    property real dragY: 0
    signal dragStarted(int queueIndex, real position)
    signal dragMoved(real position)
    signal dragFinished()
    signal dragCanceled()
    signal scrollDrag()

    QueueScrollFollow {
        id: scrollFollow
        view: upNext
        active: pane.visible
        continuous: pane.continuous
        currentIndex: pane.hasCurrent ? pane.historyCount : -1
        dragging: pane.dragIndex >= 0
    }

    QueueListModel {
        id: queueModel
        items: pane.rowItems
        onUpdated: if (scrollFollow.following && !scrollFollow.restoring) Qt.callLater(() => scrollFollow.center())
    }

    Timer {
        interval: 40
        repeat: true
        running: pane.dragIndex >= 0
        onTriggered: pane.scrollDrag()
    }

    spacing: 14

    RowLayout {
        Layout.fillWidth: true
        spacing: 8

        Text {
            Layout.fillWidth: true
            text: pane.continuous ? qsTr("Queue") : qsTr("Up next")
            color: pane.primaryText
            font.family: "Inter"
            font.pixelSize: 28
            font.weight: Font.Bold
            font.letterSpacing: -0.6
        }

    }

    RowLayout {
        Layout.fillWidth: true
        spacing: 8

        QueueBestMixButton {
            panel: pane
            count: pane.queueItems.length
            Layout.maximumWidth: Math.max(0, pane.width - clearButton.implicitWidth - 8)
        }

        Item { Layout.fillWidth: true }

        Button {
            id: clearButton
            HandCursor {}
            objectName: "clearFullscreenQueue"
            text: qsTr("Clear")
            enabled: pane.queueItems.length > 0
            implicitHeight: 28
            leftPadding: 10
            rightPadding: 10
            Accessible.name: qsTr("Clear queue")

            background: Rectangle {
                radius: 8
                color: NumaAppearance.rosePine ? (clearButton.hovered || clearButton.down || clearButton.activeFocus ? NumaAppearance.highlightColor : "transparent")
                    : clearButton.down ? "#26ffffff" : clearButton.hovered ? "#1affffff" : "transparent"
                border.color: clearButton.activeFocus ? pane.accentColor : "transparent"
            }

            contentItem: Text {
                text: clearButton.text
                color: clearButton.hovered ? pane.primaryText : pane.secondaryText
                opacity: clearButton.enabled ? 1 : 0.45
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

    ListView {
        id: upNext
        Layout.fillWidth: true
        Layout.fillHeight: true
        clip: true
        spacing: 4
        model: queueModel
        move: Transition { NumberAnimation { properties: "x,y"; duration: 220; easing.type: Easing.OutCubic } }
        displaced: Transition { NumberAnimation { properties: "x,y"; duration: 220; easing.type: Easing.OutCubic } }
        interactive: pane.dragIndex < 0
        cacheBuffer: pane.dragIndex >= 0 ? contentHeight : 320
        boundsBehavior: Flickable.StopAtBounds

        ScrollBar.vertical: ThemedScrollBar {
            id: upNextBar
            policy: upNext.contentHeight > upNext.height ? ScrollBar.AsNeeded : ScrollBar.AlwaysOff
        }

        // The logic layer binds playback and removal for each row.
        delegate: FullscreenQueueRow {
            required property string entryJson
            required property int index
            width: upNext.width - 8
            track: JSON.parse(entryJson)
            rowIndex: index
            dragPane: pane
            queueIndex: index - pane.queueStart
            previous: index < pane.historyCount
            current: pane.hasCurrent && index === pane.historyCount
            sectionLabel: !pane.continuous ? "" : previous && index === 0 ? qsTr("PLAYED")
                : current ? qsTr("NOW PLAYING") : index === pane.queueStart ? qsTr("UP NEXT") : ""
            paneSwap: pane.paneSwap
            accentColor: pane.accentColor
            primaryText: pane.primaryText
            mutedText: pane.mutedText
        }

        FlickableWheel {
            view: upNext
            enabled: upNext.interactive
            onUserScrolled: scrollFollow.hold()
        }

        FullscreenQueueRow {
            parent: upNext
            objectName: "fullscreenQueueDragPreview"
            visible: pane.dragIndex >= 0
            enabled: false
            z: 20
            x: 0
            y: Math.max(0, Math.min(upNext.height - height, pane.dragY - height / 2))
            width: upNext.width - 8
            track: pane.dragIndex >= 0 ? (pane.queueItems[pane.dragIndex] || {}) : ({})
            dragPane: pane
            queueIndex: pane.dragIndex
            dragPreview: true
            accentColor: pane.accentColor
            primaryText: pane.primaryText
            mutedText: pane.mutedText
        }
    }

    Text {
        Layout.fillWidth: true
        Layout.fillHeight: true
        visible: upNext.count === 0
        text: pane.autoplayEnabled ? qsTr("Nothing queued. Autoplay has this covered.") : qsTr("Nothing queued.")
        color: pane.mutedText
        font.family: "Inter"
        font.pixelSize: 14
    }
}
