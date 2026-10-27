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
    Dialog {
        id: information
        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(430, parent ? parent.width - 40 : 430)
        modal: true
        padding: 22
        title: root.heading
        standardButtons: Dialog.Ok
        background: PopupBloomSurface { radius: 16 }
        header: Label { text: root.heading; padding: 22; font.pixelSize: 18; color: NumaAppearance.textColor; wrapMode: Text.WordWrap }
        contentItem: Label { text: root.message; color: NumaAppearance.textColor; wrapMode: Text.WordWrap; font.pixelSize: 14 }
    }
}
