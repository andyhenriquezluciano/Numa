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
import QtQuick.Layouts

// Dropdown used by the integration cards.
ComboBox {
    id: select
    property int hoveredOption: -1
    property bool tintedBackground: false

    Layout.fillWidth: true
    implicitHeight: 40
    font.family: "Inter"
    font.pixelSize: 13
    hoverEnabled: true

    contentItem: Text {
        leftPadding: 14
        rightPadding: select.indicator.width + 14
        text: select.displayText
        font: select.font
        color: NumaAppearance.textColor
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }

    indicator: LucideIcon {
        x: select.width - width - 14
        y: (select.height - height) / 2
        width: 14
        height: 14
        name: "chevron-right"
        color: NumaAppearance.mutedColor
        rotation: select.popup.visible ? -90 : 90
    }

    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: 10
            color: NumaAppearance.rosePine ? (select.tintedBackground ? NumaAppearance.highlightColor : NumaAppearance.surfaceColor) : select.hovered ? "#1cffffff" : "#10ffffff"
            Behavior on color { ColorAnimation { duration: 120 } }
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: 10
            softGlow: true
            showBorder: true
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : select.visualFocus || select.popup.visible ? "#d8c2f2" : (select.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }

    delegate: ItemDelegate {
        id: option
        hoverEnabled: true
        onHoveredChanged: {
            if (hovered) select.hoveredOption = index;
            else if (select.hoveredOption === index) select.hoveredOption = -1;
        }
        rightPadding: 32
        Rectangle {
            anchors.right: parent.right
            anchors.rightMargin: 12
            anchors.verticalCenter: parent.verticalCenter
            width: 8
            height: 8
            radius: 4
            visible: select.currentIndex === option.index
            color: NumaAppearance.accentColor
        }
        required property int index
        required property var modelData
        width: ListView.view.width
        implicitHeight: 36
        highlighted: select.highlightedIndex === index

        contentItem: Text {
            text: option.modelData
            color: NumaAppearance.textColor
            font.family: select.font.family
            font.pixelSize: select.font.pixelSize
            font.weight: select.currentIndex === option.index ? Font.DemiBold : Font.Normal
            verticalAlignment: Text.AlignVCenter
        }
        background: Rectangle {
            radius: 7
            color: (select.hoveredOption >= 0 ? select.hoveredOption === option.index : option.highlighted) ? (NumaAppearance.rosePine ? NumaAppearance.highlightColor : "#20ffffff") : "transparent"
        }
    }

    popup: Popup {
        y: select.height + 6
        width: select.width
        padding: 5
        implicitHeight: contentItem.implicitHeight + 10

        contentItem: ListView {
            clip: true
            implicitHeight: contentHeight
            model: select.popup.visible ? select.delegateModel : null
            currentIndex: select.highlightedIndex
        }
        background: PopupBloomSurface { radius: 12; performanceColor: NumaAppearance.surfaceColor }
    }
}
