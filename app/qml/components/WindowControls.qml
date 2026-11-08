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
import QtQuick.Controls
import QtQuick.Window

Row {
    id: root
    required property Window targetWindow
    property bool autoHide: false
    property bool awake: true
    readonly property bool hovered: minimizeButton.hovered || maximizeButton.hovered || closeButton.hovered
    readonly property bool maximized: targetWindow && targetWindow.visibility === Window.Maximized
    readonly property bool fullscreen: targetWindow && targetWindow.visibility === Window.FullScreen
    readonly property real shown: !autoHide || awake || hovered ? 1 : 0
    property real reveal: shown

    spacing: 4
    height: 38
    opacity: reveal
    visible: reveal > 0.01
    transform: Translate { y: -10 * (1 - root.reveal) }
    onAutoHideChanged: wake()
    Behavior on reveal { NumberAnimation { duration: 420; easing.type: Easing.OutCubic } }
    function wake() { awake = true; idleTimer.restart(); }
    Timer { id: idleTimer; interval: 2200; onTriggered: root.awake = false }

    component CaptionButton: Button {
        id: captionControl
        property string windowAction
        readonly property color danger: NumaAppearance.theme === "rose_pine_dawn" ? "#b4637a" : "#eb6f92"
        readonly property color glyphColor: hovered ? danger : NumaAppearance.mutedColor
        width: 40
        height: 30
        y: 4
        padding: 8
        hoverEnabled: true
        focusPolicy: Qt.TabFocus
        enabled: Boolean(root.targetWindow)
        HandCursor {}
        Accessible.name: text
        background: Rectangle {
            radius: 10
            color: !captionControl.hovered && !captionControl.down ? "transparent"
                : NumaAppearance.theme === "rose_pine_dawn" ? NumaAppearance.highlightColor
                : Qt.rgba(captionControl.danger.r, captionControl.danger.g, captionControl.danger.b, 0.16)
        }
        contentItem: Item {
            Item {
                anchors.centerIn: parent
                width: 14; height: 14
                Rectangle {
                    visible: captionControl.windowAction === "minimize"
                    width: 11; height: 1.5; radius: 0.75
                    anchors.centerIn: parent
                    color: captionControl.glyphColor
                }
                Rectangle {
                    visible: captionControl.windowAction === "maximize" && !root.maximized && !root.fullscreen
                    x: 2; y: 2; width: 10; height: 10; radius: 1.5
                    color: "transparent"
                    border.width: 1.3
                    border.color: captionControl.glyphColor
                }
                LucideIcon {
                    anchors.fill: parent
                    visible: captionControl.windowAction === "maximize" && (root.maximized || root.fullscreen)
                    name: "copy"
                    color: captionControl.glyphColor
                }
                Rectangle {
                    visible: captionControl.windowAction === "close"
                    anchors.centerIn: parent
                    width: 13; height: 1.5; radius: 0.75
                    color: captionControl.glyphColor
                    rotation: 45
                }
                Rectangle {
                    visible: captionControl.windowAction === "close"
                    anchors.centerIn: parent
                    width: 13; height: 1.5; radius: 0.75
                    color: captionControl.glyphColor
                    rotation: -45
                }
            }
        }
        GlassToolTip { control: captionControl; text: captionControl.text }
    }

    CaptionButton {
        id: minimizeButton
        windowAction: "minimize"
        text: qsTr("Minimize")
        onClicked: "minimizeSmoothly" in root.targetWindow ? root.targetWindow.minimizeSmoothly() : root.targetWindow.showMinimized()
    }
    CaptionButton {
        id: maximizeButton
        windowAction: "maximize"
        text: root.fullscreen ? qsTr("Exit full screen") : root.maximized ? qsTr("Restore") : qsTr("Maximize")
        onClicked: {
            if (root.fullscreen && "requestFullscreenVisibility" in root.targetWindow)
                root.targetWindow.requestFullscreenVisibility(Window.Maximized);
            else if (root.maximized)
                root.targetWindow.showNormal();
            else
                root.targetWindow.showMaximized();
        }
    }
    CaptionButton {
        id: closeButton
        windowAction: "close"
        text: qsTr("Close")
        onClicked: root.targetWindow.close()
    }
}
