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

import QtQuick

// Three breathing dots for an instrumental gap or a loading state.
Row {
    id: dots

    property bool active: false
    property bool running: true
    property color color: NumaAppearance.textColor

    spacing: 4
    height: active ? 40 : 0
    opacity: active ? 1 : 0
    clip: true
    Behavior on height { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
    Behavior on opacity { NumberAnimation { duration: 240 } }

    // Every line carries one of these; the dots and their loops exist only while one is open.
    Repeater {
        model: dots.active || dots.height > 0 ? 3 : 0
        Item {
            id: slot
            required property int index
            width: 13
            height: 40

            Rectangle {
                id: dot
                anchors.centerIn: parent
                width: 9
                height: 9
                radius: 4.5
                antialiasing: true
                color: dots.color
                scale: 0.85
                opacity: 0.45

                SequentialAnimation {
                    running: dots.active && dots.running
                    loops: Animation.Infinite
                    onRunningChanged: if (!running) { dot.scale = 0.85; dot.opacity = 0.45; }
                    PauseAnimation { duration: slot.index * 200 }
                    ParallelAnimation {
                        NumberAnimation { target: dot; property: "scale"; to: 1.25; duration: 700; easing.type: Easing.InOutSine }
                        NumberAnimation { target: dot; property: "opacity"; to: 1; duration: 700; easing.type: Easing.InOutSine }
                    }
                    ParallelAnimation {
                        NumberAnimation { target: dot; property: "scale"; to: 0.85; duration: 700; easing.type: Easing.InOutSine }
                        NumberAnimation { target: dot; property: "opacity"; to: 0.45; duration: 700; easing.type: Easing.InOutSine }
                    }
                    PauseAnimation { duration: (2 - slot.index) * 200 }
                }
            }
        }
    }
}
