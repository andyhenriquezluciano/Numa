/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick
import QtQuick.Controls
import ".."

Button {
    id: control
    property string glyph
    property color accentColor: NumaAppearance.accentColor
    property color glyphColor: "#f7f5f0"
    property bool round: false
    width: 40
    height: 40
    padding: 11
    hoverEnabled: true
    opacity: enabled ? 1 : 0.4
    Accessible.name: text || ToolTip.text
    ToolTip.visible: false
    scale: down ? 0.95 : hovered ? 1.05 : 1
    Behavior on scale { NumberAnimation { duration: 120; easing.type: Easing.OutCubic } }
    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: control.round ? control.width / 2 : 10
            color: control.down ? "#26ffffff" : control.hovered ? "#22ffffff" : "#14ffffff"
            Behavior on color { ColorAnimation { duration: 120 } }
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: control.round ? control.width / 2 : 10
            softGlow: true
            showBorder: true
            border.color: control.activeFocus ? control.accentColor : control.hovered ? "#45ffffff" : "#30ffffff"
            fillColor: "transparent"
        }
    }
    contentItem: LucideIcon { name: control.glyph; color: control.glyphColor }
    GlassToolTip { control: control; text: control.text || control.ToolTip.text; accentColor: control.accentColor }
}
