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
import Numa
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// Numa Connect, volume and the queue button.
RowLayout {
    id: sideControls

    // The player bar this lives in; it owns the colours and helpers.
    required property Item bar

    Layout.alignment: Qt.AlignVCenter
    spacing: 4

    Button {
        id: videoButton

        // NumaMusicVideo is supplied as a QQmlContext property by main.cpp.
        // qmllint disable unqualified
        visible: !bar.narrow && NumaMusicVideo.status !== "" && NumaMusicVideo.status !== "unavailable"
        enabled: NumaMusicVideo.available
        implicitWidth: bar.controlSize
        implicitHeight: bar.controlSize
        hoverEnabled: true
        focusPolicy: Qt.TabFocus
        Accessible.name: qsTr("Watch music video")
        onClicked: NumaMusicVideo.show()

        GlassToolTip { control: videoButton; text: videoButton.Accessible.name; accentColor: bar.accentColor }

        background: Rectangle {
            radius: width / 2
            color: videoButton.hovered ? "#1fffffff" : "transparent"
            border.width: 1
            border.color: videoButton.activeFocus ? bar.primaryText : "transparent"

            Behavior on color { ColorAnimation { duration: 90 } }
        }

        contentItem: LucideIcon {
            name: "video"
            color: bar.controlColor
            opacity: videoButton.enabled ? 1 : 0.4
            implicitWidth: 17
            implicitHeight: 17
        }
    }

    ConnectButton {
        visible: !bar.narrow
        backdrop: bar.backdrop
        accentColor: bar.accentColor
        controlColor: bar.controlColor
        focusColor: bar.primaryText
    }

    Button {
        id: volumeButton

        implicitWidth: bar.controlSize
        implicitHeight: bar.controlSize
        hoverEnabled: true
        focusPolicy: Qt.TabFocus
        Accessible.name: qsTr("Volume")
        onClicked: {
            if (bar.canopy || bar.compact)
                volumePopup.visible ? volumePopup.close() : volumePopup.open();
        }

        GlassToolTip { control: volumeButton; text: volumeButton.Accessible.name; accentColor: bar.accentColor }

        // Compact layouts use the vertical control instead of an inline slider.
        WheelHandler {
            enabled: bar.canopy || bar.compact
            target: null
            acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad
            onWheel: function(event) {
                const step = (event.inverted ? -event.angleDelta.y : event.angleDelta.y) / 120 * 0.05;
                bar.setVolumeLevel(Math.max(0, Math.min(1, bar.volumeLevel + step)));
            }
        }

        VolumePopup {
            id: volumePopup
            bar: sideControls.bar
        }

        background: Rectangle {
            radius: width / 2
            color: volumeButton.hovered ? "#1fffffff" : "transparent"
            border.width: 1
            border.color: volumeButton.activeFocus ? bar.primaryText : "transparent"

            Behavior on color { ColorAnimation { duration: 90 } }
        }

        contentItem: LucideIcon {
            // NumaHome is supplied as a QQmlContext property by main.cpp.
            // qmllint disable unqualified
            name: bar.volumeLevel <= 0.001 ? "volume-x" : "volume-2"
            color: bar.controlColor
            implicitWidth: 17
            implicitHeight: 17
        }
    }

    Slider {
        id: volume

        visible: !bar.compact && !bar.canopy
        Layout.preferredWidth: 72
        from: 0
        to: 1
        stepSize: 0.01
        hoverEnabled: true
        value: bar.volumeLevel
        onMoved: bar.setVolumeLevel(value)

        // One wheel notch is 5%. Touchpads send fractional notches and glide smoothly.
        // Scrolling up for louder, as nature intended. Scrolling down is for regret.
        WheelHandler {
            target: null
            acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad
            onWheel: function(event) {
                const delta = event.angleDelta.y !== 0 ? event.angleDelta.y : -event.angleDelta.x;
                const step = (event.inverted ? -delta : delta) / 120 * 0.05;
                bar.setVolumeLevel(Math.max(0, Math.min(1, bar.volumeLevel + step)));
            }
        }

        background: Item {
            x: volume.leftPadding
            y: volume.topPadding
            width: volume.availableWidth
            height: volume.availableHeight

            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width
                height: volume.hovered || volume.pressed ? 6 : 4
                radius: height / 2
                color: "#2effffff"

                Behavior on height {
                    NumberAnimation { duration: 180; easing.type: Easing.OutBack; easing.overshoot: 2 }
                }

                Rectangle {
                    width: volume.visualPosition * parent.width
                    height: parent.height
                    radius: parent.radius
                    color: Qt.rgba(1, 1, 1, volume.hovered || volume.pressed ? 1 : 0.75)
                    Behavior on color { ColorAnimation { duration: 120 } }
                }
            }
        }

        handle: Item {}
    }

    Button {
        id: compactButton

        implicitWidth: bar.controlSize
        implicitHeight: bar.controlSize
        hoverEnabled: true
        focusPolicy: Qt.TabFocus
        Accessible.name: qsTr("Compact mode")
        onClicked: bar.compactRequested()

        GlassToolTip { control: compactButton; text: compactButton.Accessible.name; accentColor: bar.accentColor }

        background: Rectangle {
            radius: width / 2
            color: compactButton.hovered ? "#1fffffff" : "transparent"
            border.width: 1
            border.color: compactButton.activeFocus ? bar.primaryText : "transparent"
        }

        contentItem: LucideIcon {
            name: "minimize"
            color: bar.controlColor
            implicitWidth: 17
            implicitHeight: 17
        }
    }

    Button {
        id: queueButton

        implicitWidth: bar.controlSize
        implicitHeight: bar.controlSize
        hoverEnabled: true
        focusPolicy: Qt.TabFocus
        Accessible.name: qsTr("Queue")
        Accessible.checkable: true
        Accessible.checked: bar.queueOpen
        onClicked: bar.queueRequested()

        GlassToolTip { control: queueButton; text: queueButton.Accessible.name; accentColor: bar.accentColor }

        background: Rectangle {
            radius: width / 2
            color: bar.queueOpen ? (queueButton.hovered ? "#3dffffff" : "#2effffff") : queueButton.hovered ? "#1fffffff" : "transparent"
            border.width: 1
            border.color: queueButton.activeFocus ? bar.primaryText : "transparent"

            Behavior on color { ColorAnimation { duration: 90 } }
        }

        contentItem: LucideIcon {
            name: "list-music"
            color: bar.queueOpen ? "#ffffff" : bar.controlColor
            implicitWidth: 17
            implicitHeight: 17
        }
    }
}
