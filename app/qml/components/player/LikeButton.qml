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
import Numa

// Likes the playing song on YouTube Music.
Button {
    id: root

    property color iconColor: "#d8dcd6"
    property color likedColor: "#8cc5a5"
    property int iconSize: 17
    readonly property bool liked: NumaLibrary.currentLiked

    implicitWidth: 32
    implicitHeight: 32
    hoverEnabled: true
    focusPolicy: Qt.TabFocus
    enabled: Boolean(NumaPlayback.track.id) && NumaAuth.isSignedIn && !NumaLibrary.likeBusy
    onClicked: NumaLibrary.toggleCurrentLike()
    Accessible.name: liked ? qsTr("Remove from liked songs") : qsTr("Like")
    Accessible.checkable: true
    Accessible.checked: liked

    GlassToolTip {
        control: root
        text: root.liked ? qsTr("Remove from liked songs") : qsTr("Like")
        accentColor: root.likedColor
    }

    background: Rectangle {
        radius: width / 2
        border.width: 1
        border.color: root.activeFocus ? root.iconColor : "transparent"
        color: root.hovered && root.enabled ? "#1fffffff" : "transparent"
        Behavior on color { ColorAnimation { duration: 90 } }
    }

    contentItem: LucideIcon {
        name: root.liked ? "heart-filled" : "heart"
        color: root.liked ? root.likedColor : root.iconColor
        // Hollow at half strength until YouTube says where this song stands.
        opacity: !NumaAuth.isSignedIn || !NumaPlayback.track.id ? 0.4 : NumaLibrary.likeKnown ? 1 : 0.6
        implicitWidth: root.iconSize
        implicitHeight: root.iconSize
        scale: root.down ? 0.85 : 1
        Behavior on scale { NumberAnimation { duration: 120; easing.type: Easing.OutBack } }
    }
}
