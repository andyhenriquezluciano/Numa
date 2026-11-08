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

Button {
    id: control
    HandCursor {}
    property bool prominent: false
    property bool surfaceBackground: false
    property color labelColor: NumaAppearance.theme === "rose_pine" && control.prominent ? NumaAppearance.baseColor
        : control.prominent && NumaAppearance.theme === "rose_pine_dawn" ? NumaAppearance.surfaceColor : NumaAppearance.textColor
    property color buttonColor: {
        if (NumaAppearance.rosePine) {
            if (control.prominent) return NumaAppearance.accentColor;
            if (control.hovered || control.down || control.highlighted) return NumaAppearance.highlightColor;
            if (NumaAppearance.theme === "rose_pine") return "#1f1d2e";
            return control.surfaceBackground ? NumaAppearance.surfaceColor : NumaAppearance.overlayColor;
        }
        return control.highlighted ? "#514060" : control.down ? "#2cffffff" : control.hovered ? "#1cffffff" : "#0fffffff";
    }
    implicitHeight: 38
    padding: 12
    font.family: "Inter"
    font.pixelSize: 13
    hoverEnabled: true
    contentItem: Text {
        text: control.text
        font: control.font
        color: control.labelColor
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
    }
    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: 10
            color: control.buttonColor
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: 10
            softGlow: true
            showBorder: true
            border.width: NumaAppearance.controlBorderWidth
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : control.activeFocus ? NumaAppearance.accentColor : control.highlighted ? "#a28bc2" : (control.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }
}
