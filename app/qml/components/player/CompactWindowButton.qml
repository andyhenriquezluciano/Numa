/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import ".."
import QtQuick
import QtQuick.Controls

Button {
    id: control

    required property string glyph
    required property string label
    required property var colors
    property bool active: false
    property bool minimizeGlyph: false

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
        radius: height / 2
        color: control.down ? "#26ffffff"
                            : control.hovered || control.activeFocus ? "#1affffff"
                                                                     : "transparent"
        border.width: 1
        border.color: control.activeFocus ? control.colors.accentColor : "transparent"
        Behavior on color { ColorAnimation { duration: 120 } }
    }

    contentItem: Item {
        LucideIcon {
            anchors.centerIn: parent
            width: 16
            height: 16
            name: control.glyph
            color: control.active ? control.colors.accentColor
                                  : control.hovered ? control.colors.primaryText
                                                    : control.colors.secondaryText
            visible: !control.minimizeGlyph
        }

        Rectangle {
            anchors.centerIn: parent
            width: 11
            height: 1.4
            radius: 0.7
            color: control.hovered ? control.colors.primaryText
                                   : control.colors.secondaryText
            visible: control.minimizeGlyph
        }
    }
}
