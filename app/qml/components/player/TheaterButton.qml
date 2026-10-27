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
import QtQuick
import QtQuick.Controls

// Round icon button for the music video theater.
Button {
    id: root

    property string glyph: ""
    property string label: ""
    property int size: 44
    property int iconSize: 20
    property bool active: false
    property bool outlined: false
    property color fill: "transparent"
    property color iconColor: "#f7f5f0"

    implicitWidth: size
    implicitHeight: size
    hoverEnabled: true
    focusPolicy: Qt.TabFocus
    Accessible.name: label

    GlassToolTip {
        control: root
        text: root.label
        accentColor: root.iconColor
    }

    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: parent.width / 2
            color: root.fill.a > 0 ? root.fill
                 : root.active ? "#1fffffff"
                 : root.hovered ? "#14ffffff" : "transparent"
            Behavior on color { ColorAnimation { duration: 90 } }
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: parent.width / 2
            softGlow: true
            showBorder: root.activeFocus || root.outlined
            border.width: 1
            border.color: root.activeFocus ? NumaAppearance.textColor : (root.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }

    contentItem: Item {
        LucideIcon {
            anchors.centerIn: parent
            name: root.glyph
            color: root.enabled ? root.iconColor : Qt.rgba(root.iconColor.r, root.iconColor.g, root.iconColor.b, 0.4)
            width: root.iconSize
            height: root.iconSize
            scale: root.down ? 0.88 : 1

            Behavior on scale { NumberAnimation { duration: 90 } }
        }
    }
}
