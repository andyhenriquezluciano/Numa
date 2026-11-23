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
import ".."

// Initial page loading and recoverable errors.
Item {
    id: root

    property bool loading: false
    property bool showSkeleton: loading
    property bool showLoadingLine: true
    property string errorMessage
    property color accentColor: "#b79bed"

    signal retryRequested

    // Fast loads finish before the hairline appears.
    readonly property bool lineShown: loading && showLoadingLine && delay.passed && !skeleton.active

    onLoadingChanged: delay.passed = false

    Timer {
        id: delay

        property bool passed: false

        interval: Motion.loadDelay
        running: root.loading && root.visible && !passed
        onTriggered: passed = true
    }

    LoadingSkeleton {
        id: skeleton
        anchors.centerIn: parent
        width: Math.max(0, Math.min(parent.width - 48, 420))
        loading: root.loading && root.showSkeleton
    }

    Item {
        id: track

        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: 2
        clip: true
        opacity: root.lineShown ? 1 : 0
        visible: root.showLoadingLine && opacity > 0

        Behavior on opacity {
            NumberAnimation { duration: Motion.normal; easing.type: Motion.enter }
        }

        Rectangle {
            id: segment

            width: track.width * 0.4
            height: parent.height
            radius: 1
            color: root.accentColor
        }

        // The segment glides across and re-enters from the left edge.
        NumberAnimation {
            target: segment
            property: "x"
            from: -segment.width
            to: track.width
            duration: Motion.sweep
            easing.type: Easing.InOutSine
            loops: Animation.Infinite
            running: track.visible
        }
    }

    Column {
        anchors.centerIn: parent
        width: Math.min(parent.width - 48, 420)
        spacing: 14
        visible: !root.loading && root.errorMessage.length > 0

        Text {
            width: parent.width
            text: root.errorMessage
            color: "#edf0ec"
            font.family: "Inter"
            font.pixelSize: 13
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap
        }

        Button {
            anchors.horizontalCenter: parent.horizontalCenter
            text: qsTr("Try again")
            onClicked: root.retryRequested()
        }
    }
}
