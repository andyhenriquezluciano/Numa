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

import Numa
import QtQuick
import QtQuick.Controls

// Top bar button for offline status and manual reconnect check.
Button {
    id: root

    readonly property bool shown: NumaNetwork.offline || NumaNetwork.retrying

    visible: shown
    width: shown ? 38 : 0
    height: 38
    hoverEnabled: true
    Accessible.name: NumaNetwork.checking ? qsTr("Checking connection…") : qsTr("Offline. Click to check connection.")
    onClicked: NumaNetwork.retry()

    background: Rectangle {
        radius: height / 2
        color: root.down ? "#26ffffff" : root.hovered ? "#1fffffff" : "#14ffffff"
        border.color: root.activeFocus ? "#ffffff" : "#2effffff"
        border.width: 1
        Behavior on color { ColorAnimation { duration: Motion.fast } }
    }

    contentItem: Item {
        LucideIcon {
            id: icon
            anchors.centerIn: parent
            width: 18
            height: 18
            name: NumaNetwork.checking ? "refresh-cw" : "wifi-off"
            color: root.hovered ? "#ffffff" : "#ffcf8a"

            NumberAnimation on rotation {
                running: NumaNetwork.checking
                loops: Animation.Infinite
                from: 0
                to: 360
                duration: 1000
            }
        }
    }
}
