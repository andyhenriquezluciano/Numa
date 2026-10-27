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

import Numa
import QtQuick
import QtQuick.Controls

// One shell-owned picker. Song menus are often loaded per open and destroyed on
// close, which would take a picker built from their components down with them.
Menu {
    id: root

    property var track: ({})
    // Songs from this computer can only join playlists on this computer.
    readonly property bool localTrack: String(root.track.id || "").indexOf("local:") === 0
    property var localTargets: []
    readonly property var targets: root.localTrack ? root.localTargets : NumaLibrary.playlistTargets
    readonly property string status: root.localTrack
        ? (root.localTargets.length === 0 ? qsTr("No local playlists yet") : "")
        : NumaLibrary.targetsLoading ? qsTr("Loading playlists…")
        : NumaLibrary.targetsError || (NumaLibrary.playlistTargets.length === 0 ? qsTr("No editable playlists yet") : "")

    function openFor(track, position) {
        root.track = track;
        root.localTargets = root.localTrack ? NumaLocal.targetsFor(track.id) : [];
        root.popup(position.x, position.y);
    }

    function targetTitle(target) {
        const title = String(target.title || "");
        const id = String(target.id || "").replace(/^VL/, "");
        return id === "LM" || ["liked music", "liked songs", "your likes"].includes(title.toLowerCase())
                ? qsTr("Liked Music") : title;
    }

    function targetIsLocal(target) {
        return root.localTrack || target.source === "local" || String(target.id || "").startsWith("local-playlist:");
    }

    parent: Overlay.overlay
    // Keep it in the scene like MediaMenu; newer Qt would otherwise pick a native window.
    Component.onCompleted: if ("popupType" in root) root["popupType"] = 0
    width: 250
    padding: 5
    background: PopupBloomSurface { radius: 12 }
    // Grow from the click point. Menus that just appear are how you get jump scares in a music app.
    transformOrigin: Popup.TopLeft
    enter: Transition {
        NumberAnimation { property: "opacity"; from: 0; to: 1; duration: Motion.fast; easing.type: Motion.enter }
        NumberAnimation { property: "scale"; from: 0.94; to: 1; duration: Motion.normal; easing.type: Motion.enter }
    }
    exit: Transition {
        NumberAnimation { property: "opacity"; to: 0; duration: Motion.fast; easing.type: Motion.exit }
    }

    component Entry: MenuItem {
        id: entry
        property string sourceLabel: ""
        implicitHeight: 36
        background: RoundedSurfaceFrame {
            radius: 6
            softGlow: entry.highlighted
            showBorder: entry.highlighted
            fillColor: entry.highlighted ? "#16ffffff" : "transparent"
            border.color: entry.highlighted ? "#32ffffff" : "transparent"
            Behavior on fillColor { ColorAnimation { duration: Motion.fast } }
        }
        contentItem: Item {
            Text {
                anchors.left: parent.left
                anchors.right: badge.left
                anchors.rightMargin: badge.visible ? 8 : 0
                anchors.verticalCenter: parent.verticalCenter
                text: entry.text
                color: entry.enabled ? "#f2f0eb" : "#7c857f"
                font.family: "Inter"
                font.pixelSize: 13
                elide: Text.ElideRight
            }
            RoundedSurfaceFrame {
                id: badge
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                visible: entry.sourceLabel.length > 0
                width: visible ? badgeText.implicitWidth + 12 : 0
                height: 22
                radius: 6
                fillColor: "#12ffffff"
                Text { id: badgeText; anchors.centerIn: parent; text: entry.sourceLabel; color: "#dadbd5"; font.pixelSize: 10 }
            }
        }
    }

    Entry {
        text: qsTr("New playlist…")
        enabled: root.localTrack || !NumaLibrary.saving
        onTriggered: newPlaylistDialog.createObject(Overlay.overlay, {track: root.track}).open()
    }
    MenuSeparator {
        contentItem: Rectangle { implicitHeight: 1; color: "#455348" }
    }
    Entry {
        text: root.status
        visible: text.length > 0
        height: visible ? implicitHeight : 0
        enabled: false
    }
    // Offset past the three fixed items above. The playlists queue up behind the bouncer.
    Instantiator {
        model: root.targets
        delegate: Entry {
            required property var modelData
            text: modelData.containsTrack ? qsTr("%1 · added").arg(root.targetTitle(modelData)) : root.targetTitle(modelData)
            sourceLabel: root.targetIsLocal(modelData) ? qsTr("Local") : "YouTube Music"
            enabled: !modelData.containsTrack && (root.localTrack || !NumaLibrary.saving)
            onTriggered: root.localTrack
                ? NumaLocal.addTrackToPlaylist(modelData.id, root.track.id)
                : NumaLibrary.addToPlaylist(root.track, modelData.id, modelData.title)
        }
        onObjectAdded: function(index, object) { root.insertItem(index + 3, object); }
        onObjectRemoved: function(index, object) { root.removeItem(object); }
    }

    Component {
        id: newPlaylistDialog

        NewPlaylistDialog {}
    }
}
