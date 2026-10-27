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
import QtQuick.Layouts

// Small "Up next" card that surfaces in the last seconds before a song ends or mixes.
Item {
    id: card

    property var track: ({ title: "Midnight City", thumbnail: "" })
    property string artistText: "M83"
    // 0 hidden, 1 shown.
    property real shown: 1
    property color inkColor: "#0b0d0a"
    property color primaryText: "#f7f5f0"
    property color mutedText: "#8d928a"
    property alias area: area

    implicitWidth: Math.max(340, Math.min(460, content.implicitWidth + 32))
    implicitHeight: 56
    visible: shown > 0.001
    opacity: shown
    transform: Translate { y: -10 * (1 - card.shown) }
    Accessible.name: qsTr("Up next: %1").arg(card.track.title || "")

    PopupBloomSurface {
        anchors.fill: parent
        radius: 14
        neutral: true
    }


    Rectangle {
        anchors.fill: parent
        radius: 14
        color: area.containsMouse ? "#14ffffff" : "transparent"
        Behavior on color { ColorAnimation { duration: 160 } }
    }

    RowLayout {
        id: content
        anchors.fill: parent
        anchors.leftMargin: 10
        anchors.rightMargin: 18
        spacing: 12

        RoundedArtwork {
            Layout.preferredWidth: 40
            Layout.preferredHeight: 40
            radius: 8
            source: card.track.thumbnail || ""
        }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.minimumWidth: 0
            spacing: 2

            Text {
                text: qsTr("UP NEXT")
                color: card.mutedText
                font.family: "Inter"
                font.pixelSize: 9
                font.weight: Font.DemiBold
                font.letterSpacing: 1.4
            }

            Text {
                Layout.fillWidth: true
                Layout.maximumWidth: 340
                text: card.track.title || ""
                color: card.primaryText
                font.family: "Inter"
                font.pixelSize: 13
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }

            Text {
                Layout.fillWidth: true
                Layout.maximumWidth: 340
                text: card.artistText
                color: card.mutedText
                font.family: "Inter"
                font.pixelSize: 11
                elide: Text.ElideRight
            }
        }
    }

    MouseArea {
        id: area
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
    }
}
