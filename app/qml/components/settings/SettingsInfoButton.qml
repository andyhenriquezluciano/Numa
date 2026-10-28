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

Button {
    id: root
    property string heading
    property string message
    implicitWidth: 24
    implicitHeight: 24
    padding: 4
    hoverEnabled: true
    Accessible.name: qsTr("About %1").arg(heading)
    onClicked: information.open()
    contentItem: LucideIcon { name: "info"; color: NumaAppearance.mutedColor }
    background: Rectangle { radius: 12; color: root.hovered ? "#20ffffff" : "transparent" }
    GlassToolTip { control: root; text: root.Accessible.name }
    Popup {
        id: information
        objectName: "settingsInformationPopup"
        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(460, parent ? parent.width - 40 : 460)
        modal: true
        focus: true
        padding: 24
        closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
        Overlay.modal: Rectangle { color: "#44000000" }
        background: PopupBloomSurface { radius: 20 }
        contentItem: ColumnLayout {
            spacing: 20
            RowLayout {
                spacing: 12
                Rectangle {
                    Layout.preferredWidth: 36
                    Layout.preferredHeight: 36
                    radius: 10
                    color: "#12ffffff"
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 20
                        height: 20
                        name: "info"
                        color: NumaAppearance.accentColor
                    }
                }
                Label {
                    Layout.fillWidth: true
                    text: root.heading
                    font.family: "Inter"
                    font.pixelSize: 18
                    font.weight: Font.DemiBold
                    color: NumaAppearance.textColor
                    wrapMode: Text.WordWrap
                }
            }
            Label {
                Layout.fillWidth: true
                text: root.message
                color: NumaAppearance.textColor
                wrapMode: Text.WordWrap
                font.family: "Inter"
                font.pixelSize: 14
                lineHeight: 1.3
            }
            SettingsButton {
                Layout.alignment: Qt.AlignRight
                Layout.minimumWidth: 104
                text: qsTr("Close")
                onClicked: information.close()
            }
        }
    }
}
