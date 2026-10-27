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

// Shown instead of lyrics while they load, when none exist, or before anything plays.
ColumnLayout {
    id: placeholder

    property color accentColor: NumaAppearance.textColor
    property color primaryText: NumaAppearance.textColor
    property color dotColor: accentColor
    property bool running: true

    spacing: 10
    opacity: NumaLyrics.status !== "ready" ? 1 : 0
    visible: opacity > 0
    Behavior on opacity { NumberAnimation { duration: 220 } }

    Item {
        Layout.alignment: Qt.AlignHCenter
        Layout.preferredWidth: 64
        Layout.preferredHeight: 44
        visible: NumaLyrics.status === "loading"

        LyricsPauseDots {
            anchors.centerIn: parent
            color: placeholder.dotColor
            running: placeholder.running
            active: parent.visible
        }
    }

    LucideIcon {
        Layout.alignment: Qt.AlignHCenter
        Layout.preferredWidth: 24
        Layout.preferredHeight: 24
        visible: NumaLyrics.status !== "loading"
        name: "mic-vocal"
        color: placeholder.accentColor
        opacity: 0.8
    }

    Text {
        Layout.fillWidth: true
        text: NumaLyrics.status === "loading" ? qsTr("Loading lyrics")
              : NumaLyrics.status === "unavailable" ? qsTr("Lyrics unavailable")
              : qsTr("Play something to see its lyrics")
        color: placeholder.primaryText
        font.family: "Inter"
        font.pixelSize: 13
        font.bold: true
        horizontalAlignment: Text.AlignHCenter
    }

    Button {
        id: retry
        Layout.alignment: Qt.AlignHCenter
        visible: NumaLyrics.status === "unavailable"
        text: qsTr("Try again")
        implicitHeight: 26
        leftPadding: 10
        rightPadding: 10
        onClicked: NumaLyrics.reload()
        background: Rectangle {
            radius: height / 2
            color: retry.down ? "#26ffffff" : retry.hovered ? "#1affffff" : "#10ffffff"
        }
        contentItem: Text {
            text: retry.text
            color: placeholder.primaryText
            font.family: "Inter"
            font.pixelSize: 11
            font.bold: true
            verticalAlignment: Text.AlignVCenter
        }
    }
}
