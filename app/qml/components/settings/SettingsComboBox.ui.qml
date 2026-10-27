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
import QtQuick.Layouts
import ".."

ComboBox {
    id: control
    implicitHeight: 36
    leftPadding: 16
    rightPadding: 38
    hoverEnabled: true
    font.family: "Inter"
    font.pixelSize: 13

    contentItem: Text {
        text: control.displayText
        font: control.font
        color: NumaAppearance.textColor
        elide: Text.ElideRight
        verticalAlignment: Text.AlignVCenter
    }

    indicator: LucideIcon {
        x: control.width - width - 14
        y: (control.height - height) / 2
        width: 14
        height: 14
        name: "chevron-down"
        color: "#b4beb9"
        rotation: control.popup.visible ? 180 : 0
        Behavior on rotation { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }
    }

    background: Item {
        opacity: control.enabled ? 1 : 0.5
        Rectangle {
            anchors.fill: parent
            radius: control.height / 2
            color: control.hovered ? "#1cffffff" : "#10ffffff"
            Behavior on color { ColorAnimation { duration: 120 } }
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: control.height / 2
            softGlow: true
            showBorder: true
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : control.visualFocus ? "#a6d4bf" : (control.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }

    delegate: ItemDelegate {
        id: option
        required property var modelData
        required property int index
        width: control.popup.width - 12
        height: 36
        leftPadding: 12
        rightPadding: 12
        highlighted: control.highlightedIndex === index

        contentItem: Text {
            text: option.modelData[control.textRole] ?? option.modelData
            font: control.font
            color: control.currentIndex === option.index ? "#ffffff" : NumaAppearance.textColor
            elide: Text.ElideRight
            verticalAlignment: Text.AlignVCenter
        }
        background: Rectangle {
            radius: 10
            color: option.highlighted || option.hovered ? "#1cffffff" : "transparent"
        }
    }

    popup: Popup {
        y: control.height + 6
        width: Math.max(control.width, 220)
        implicitHeight: Math.min(contentItem.implicitHeight + 12, 280)
        padding: 6

        contentItem: ListView {
            clip: true
            implicitHeight: contentHeight
            model: control.popup.visible ? control.delegateModel : null
            currentIndex: control.highlightedIndex
            ScrollIndicator.vertical: ScrollIndicator {}
        }
        background: PopupBloomSurface { radius: 14 }
    }
}
