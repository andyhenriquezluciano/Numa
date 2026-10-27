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
import Numa
import QtQuick
import QtQuick.Controls

// SponsorBlock skip offer over the picture, shown while the playhead is in a marked span.
Button {
    id: root
    property Item videoBackdrop: null

    readonly property var segment: NumaPlayback.nonMusicSegment
    readonly property bool offered: NumaPlayback.nonMusicSkipMode === "button"
                                    && segment !== undefined && segment.endTime !== undefined

    function categoryLabel(category) {
        switch (category) {
        case "sponsor": return qsTr("Skip sponsor");
        case "selfpromo": return qsTr("Skip self-promotion");
        case "interaction": return qsTr("Skip reminder");
        default: return qsTr("Skip non-music");
        }
    }

    visible: opacity > 0.01
    opacity: offered ? 1 : 0
    implicitHeight: 44
    implicitWidth: label.implicitWidth + 64
    hoverEnabled: true
    focusPolicy: Qt.TabFocus
    onClicked: NumaPlayback.skipNonMusic()
    Accessible.name: label.text

    Behavior on opacity { NumberAnimation { duration: 140 } }

    background: Item {
        PopupBloomSurface {
            anchors.fill: parent
            radius: 8
            backdrop: root.videoBackdrop
        }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: 8
            softGlow: true
            showBorder: true
            border.width: 1
            border.color: root.activeFocus ? NumaAppearance.textColor : (root.hovered ? "#45ffffff" : "#30ffffff")
            fillColor: "transparent"
        }
    }

    contentItem: Row {
        spacing: 10
        leftPadding: 18

        Text {
            id: label

            anchors.verticalCenter: parent.verticalCenter
            text: root.offered ? root.categoryLabel(root.segment.category || "") : ""
            color: "#f7f5f0"
            font.pixelSize: 14
            font.weight: Font.DemiBold
        }

        LucideIcon {
            anchors.verticalCenter: parent.verticalCenter
            name: "skip-forward"
            color: "#f7f5f0"
            width: 16
            height: 16
        }
    }
}
