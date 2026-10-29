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

// Round translucent action button on the artist banner.
Button {
    id: ghost

    property string glyph: "shuffle"
    property color accentSoftColor: "#c4a7e7"

    width: 44
    height: 44
    padding: 12
    Accessible.name: ToolTip.text
    ToolTip.visible: false
    ToolTip.delay: 400

    GlassToolTip {
        control: ghost
        text: ghost.ToolTip.text
        accentColor: ghost.accentSoftColor
    }
    scale: ghost.down ? 0.94 : ghost.hovered ? 1.06 : 1
    Behavior on scale { NumberAnimation { duration: 120; easing.type: Easing.OutCubic } }

    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: ghost.width / 2
            color: ghost.down ? "#38ffffff" : ghost.hovered ? "#28ffffff" : "#14ffffff"
            Behavior on color { ColorAnimation { duration: 120 } }
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: ghost.width / 2
            softGlow: true
            showBorder: true
            border.width: 1
            border.color: ghost.activeFocus ? ghost.accentSoftColor : (ghost.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }

    contentItem: LucideIcon { name: ghost.glyph; color: "#f2f3ef" }
}
