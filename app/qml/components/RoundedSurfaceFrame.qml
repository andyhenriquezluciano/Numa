/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import QtQuick

Rectangle {
    id: frameRoot

    property color fillColor: "transparent"
    property color glowColor: "#ffffff"
    property bool softGlow: true
    property bool showBorder: true

    radius: 20
    color: frameRoot.fillColor
    border.color: !frameRoot.showBorder ? "transparent" : NumaAppearance.rosePine ? NumaAppearance.borderColor : frameRoot.softGlow ? "#30ffffff" : "#26ffffff"
    border.width: frameRoot.showBorder ? NumaAppearance.controlBorderWidth : 0
    antialiasing: true

    Rectangle {
        anchors.fill: parent
        anchors.margins: 1
        radius: Math.max(0, frameRoot.radius - 1)
        color: "transparent"
        border.color: Qt.rgba(frameRoot.glowColor.r, frameRoot.glowColor.g, frameRoot.glowColor.b, 0.1)
        border.width: 1
        antialiasing: true
        visible: frameRoot.softGlow && !NumaAppearance.rosePine && NumaAppearance.bordersEnabled
    }

    Rectangle {
        anchors.fill: parent
        anchors.margins: 2
        radius: Math.max(0, frameRoot.radius - 2)
        color: "transparent"
        border.color: Qt.rgba(frameRoot.glowColor.r, frameRoot.glowColor.g, frameRoot.glowColor.b, 0.04)
        border.width: 1
        antialiasing: true
        visible: frameRoot.softGlow && !NumaAppearance.rosePine && NumaAppearance.bordersEnabled
    }

    Rectangle {
        anchors.top: parent.top
        anchors.left: parent.left
        anchors.right: parent.right
        height: frameRoot.height < 80 ? frameRoot.height : Math.min(parent.height * 0.35, 120)
        radius: frameRoot.radius
        color: "transparent"
        visible: frameRoot.softGlow && !NumaAppearance.rosePine && NumaAppearance.bordersEnabled
        gradient: Gradient {
            GradientStop { position: 0.0; color: Qt.rgba(frameRoot.glowColor.r, frameRoot.glowColor.g, frameRoot.glowColor.b, 0.06) }
            GradientStop { position: frameRoot.height < 80 ? 0.5 : 1.0; color: "transparent" }
        }
    }
}

