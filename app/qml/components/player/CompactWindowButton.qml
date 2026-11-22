/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import ".."
import QtQuick
import QtQuick.Controls

Button {
    HandCursor {}
    id: control

    required property string glyph
    required property string label
    required property var colors
    property bool active: false
    property bool minimizeGlyph: false
    property bool lyricsToggle: false
    readonly property bool dawn: NumaAppearance.theme === "rose_pine_dawn"
    readonly property color hoverColor: minimizeGlyph ? (dawn ? "#ea9d34" : "#f6c177")
        : glyph === "x" ? (dawn ? "#b4637a" : "#eb6f92")
        : glyph === "maximize" || glyph === "minimize" ? (dawn ? "#286983" : "#31748f")
        : lyricsToggle ? (dawn ? "#907aa9" : "#c4a7e7")
        : NumaAppearance.textColor
    readonly property color glyphColor: NumaAppearance.rosePine
        ? (hovered ? hoverColor : NumaAppearance.textColor)
        : active ? colors.accentColor : hovered ? colors.primaryText : colors.secondaryText

    Accessible.name: label
    implicitWidth: 30
    implicitHeight: 30
    hoverEnabled: true
    focusPolicy: Qt.TabFocus

    GlassToolTip {
        control: control
        text: control.label
        accentColor: control.colors.accentColor
    }

    background: Rectangle {
        radius: 10
        color: NumaAppearance.rosePine
            ? (control.hovered && control.glyph !== "chevron-left"
                ? Qt.rgba(control.hoverColor.r, control.hoverColor.g, control.hoverColor.b, control.down ? 0.23 : 0.16)
                : control.hovered || control.down || control.activeFocus ? NumaAppearance.highlightColor : "transparent")
            : control.down ? "#26ffffff"
                            : control.hovered || control.activeFocus ? "#1affffff"
                                                                     : "transparent"
        border.width: 1
        border.color: control.activeFocus ? control.colors.accentColor : "transparent"
    }

    contentItem: Item {
        LucideIcon {
            anchors.centerIn: parent
            width: 16
            height: 16
            name: control.glyph
            color: control.glyphColor
            visible: !control.minimizeGlyph
        }

        Rectangle {
            anchors.centerIn: parent
            width: 11
            height: 1.4
            radius: 0.7
            color: control.glyphColor
            visible: control.minimizeGlyph
        }
    }
}
