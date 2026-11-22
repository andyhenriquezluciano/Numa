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

// Top bar button for offline status and manual reconnect check.
OfflineActionButton {
    id: root

    readonly property bool shown: NumaNetwork.offline || NumaNetwork.retrying
    property real spinAngle: 0

    visible: shown
    enabled: !NumaNetwork.checking
    iconName: NumaNetwork.checking ? "refresh-cw" : "wifi-off"
    iconObjectName: "offlineConnectionIcon"
    iconRotation: NumaNetwork.checking ? root.spinAngle : 0
    Accessible.name: NumaNetwork.checking ? qsTr("Checking connection…") : qsTr("Offline. Click to check connection.")
    onClicked: if (!NumaNetwork.checking) NumaNetwork.retry()

    NumberAnimation on spinAngle {
        running: NumaNetwork.checking
        loops: Animation.Infinite
        from: 0
        to: 360
        duration: 1000
    }

}
