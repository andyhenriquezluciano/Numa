/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick
import QtQuick.Controls

Button {
    id: root
    property alias iconName: glyph.name
    property alias iconRotation: glyph.rotation
    property alias iconObjectName: glyph.objectName

    width: visible ? 38 : 0
    height: 38
    hoverEnabled: true
    HandCursor {}
    GlassToolTip { control: root; text: root.Accessible.name }

    background: Rectangle {
        radius: 12
        readonly property color pink: NumaAppearance.rosePine ? NumaAppearance.accentColor : "#ebbcba"
        color: root.down ? Qt.darker(pink, 1.12) : root.hovered ? Qt.lighter(pink, 1.08) : pink
        border.color: NumaAppearance.baseColor
        border.width: root.activeFocus ? 2 : 0
    }

    contentItem: Item {
        LucideIcon {
            id: glyph
            anchors.centerIn: parent
            width: 18
            height: 18
            color: "#191724"
        }
    }
}
