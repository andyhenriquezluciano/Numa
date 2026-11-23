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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */
 
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ColumnLayout {
    id: root
    property string label: "Label"
    property string displayValue: "50%"
    property real value: 0.5
    property real from: 0
    property real to: 1
    property real stepSize: 0.01
    property alias slider: slider
    spacing: 4
    RowLayout {
        Layout.fillWidth: true
        Text {
            Layout.fillWidth: true
            text: root.label
            color: root.enabled ? NumaAppearance.textColor : "#92998f"
            font.family: "Inter"
            font.pixelSize: 13
        }
        Text {
            text: root.displayValue
            color: NumaAppearance.mutedColor
            font.family: "Inter"
            font.pixelSize: 12
        }
    }
    Slider {
        id: slider
        objectName: "slider"
        Layout.fillWidth: true
        from: root.from
        to: root.to
        stepSize: root.stepSize
        value: root.value
        hoverEnabled: true
        Accessible.name: root.label
        background: Item {
            x: slider.leftPadding
            y: slider.topPadding
            width: slider.availableWidth
            height: slider.availableHeight
            implicitHeight: 20

            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width
                height: slider.enabled && (slider.hovered || slider.pressed || slider.activeFocus) ? 6 : 4
                radius: height / 2
                color: NumaAppearance.rosePine ? NumaAppearance.highlightColor : "#3e423b"

                Behavior on height {
                    NumberAnimation { duration: 180; easing.type: Easing.OutBack; easing.overshoot: 2 }
                }

                Rectangle {
                    width: slider.visualPosition * parent.width
                    height: parent.height
                    radius: parent.radius
                    color: root.enabled ? NumaAppearance.accentColor : "#69756b"
                }
                Rectangle {
                    anchors.fill: parent
                    radius: parent.radius
                    color: "transparent"
                    border.color: NumaAppearance.textColor
                    visible: slider.visualFocus
                }
            }
        }
        handle: Item {}
    }
}
