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

Item {
    id: root
    property var model: []
    property var currentValue
    readonly property alias currentIndex: select.currentIndex
    readonly property alias displayText: select.displayText
    signal picked(var value)
    Layout.fillWidth: false
    implicitWidth: 185
    implicitHeight: select.implicitHeight
    SettingsSelect {
        id: select
        anchors.fill: parent
        model: root.model
        currentIndex: {
            for (let i = 0; i < root.model.length; ++i) if (root.model[i].value === root.currentValue) return i
            return -1
        }
        displayText: currentIndex >= 0 ? root.model[currentIndex].label : ""
        onActivated: index => root.picked(root.model[index].value)
        delegate: ItemDelegate {
            id: option
            required property int index
            required property var modelData
            width: ListView.view.width
            implicitHeight: Math.max(38, label.implicitHeight + 16)
            enabled: modelData.enabled !== false
            highlighted: select.highlightedIndex === index
            Accessible.name: modelData.name || modelData.label
            contentItem: Text {
                id: label
                text: option.modelData.label
                color: NumaAppearance.textColor
                opacity: option.enabled ? 1 : 0.4
                font: select.font
                wrapMode: Text.WordWrap
                verticalAlignment: Text.AlignVCenter
            }
            background: Rectangle { radius: 7; color: option.highlighted ? "#20ffffff" : "transparent" }
        }
    }
}
