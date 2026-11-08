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
import QtQuick.Layouts
import ".."

// Edit actions for a playlist kept on this computer: songs, cover, name, delete.
Flow {
    id: root

    // The playlist detail map from NumaPlaylist.
    property var playlist: ({})
    property color accentColor: "#b79bed"
    property color accentSoftColor: "#c4a7e7"
    property bool showCoverActions: true
    property bool showDeleteAction: true
    property bool showRenameAction: true

    function rename() {
        renamePopup.openWith(root.playlist.title || "");
    }

    readonly property string playlistId: playlist.id || ""

    signal deleted

    spacing: 8

    component Action: Button {
        id: action
        HandCursor {}

        property string glyph

        implicitHeight: 32
        implicitWidth: row.implicitWidth + 26
        hoverEnabled: true
        contentItem: Row {
            id: row
            spacing: 7
            leftPadding: 2

            LucideIcon {
                anchors.verticalCenter: parent.verticalCenter
                width: 14
                height: 14
                name: action.glyph
                color: root.accentSoftColor
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: action.text
                color: NumaAppearance.textColor
                font.family: "Inter"
                font.pixelSize: 12
            }
        }
        background: Rectangle {
            radius: 10
            color: NumaAppearance.rosePine ? (action.hovered || action.down ? NumaAppearance.highlightColor : NumaAppearance.overlayColor)
                : action.hovered ? "#22ffffff" : "#0effffff"
            border.width: 0
        }
    }

    Action { glyph: "folder-plus"; text: qsTr("Add songs"); onClicked: LocalPickers.addSongs(root.playlistId) }
    Action { glyph: "folder-plus"; text: qsTr("Add folder"); onClicked: LocalPickers.addFolder(root.playlistId) }
    Action { visible: root.showCoverActions; glyph: "image"; text: qsTr("Cover"); onClicked: LocalPickers.pickCover("playlist", root.playlistId) }
    Action {
        visible: root.showCoverActions && Boolean(root.playlist.hasCustomCover)
        glyph: "x"
        text: qsTr("Auto cover")
        onClicked: NumaLocal.clearPlaylistCover(root.playlistId)
    }
    Action { visible: root.showRenameAction; glyph: "pencil"; text: qsTr("Rename"); onClicked: root.rename() }
    Action { visible: root.showDeleteAction; glyph: "trash-2"; text: qsTr("Delete"); onClicked: deletePopup.open() }

    // One small popup for renaming. A playlist called "New playlist (2)" deserves better.
    Popup {
        id: renamePopup

        function openWith(title) {
            nameField.text = title;
            open();
        }

        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(360, parent.width - 48)
        padding: 18
        modal: true
        focus: true
        closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
        onOpened: {
            nameField.forceActiveFocus();
            nameField.selectAll();
        }
        Overlay.modal: Rectangle { color: "#66000000" }
        background: PopupBloomSurface { radius: 14 }

        contentItem: ColumnLayout {
            spacing: 12

            Text {
                text: qsTr("Rename playlist")
                color: NumaAppearance.textColor
                font.family: "Inter"
                font.pixelSize: 16
                font.weight: Font.DemiBold
            }
            TextField {
                id: nameField

                Layout.fillWidth: true
                implicitHeight: 38
                maximumLength: 150
                selectByMouse: true
                selectionColor: NumaAppearance.highlightColor
                selectedTextColor: NumaAppearance.textColor
                color: NumaAppearance.textColor
                font.family: "Inter"
                font.pixelSize: 13
                onAccepted: {
                    NumaLocal.renamePlaylist(root.playlistId, text);
                    renamePopup.close();
                }
                background: Rectangle {
                    radius: 10
                    color: NumaAppearance.rosePine ? NumaAppearance.overlayColor : "#18ffffff"
                    border.color: NumaAppearance.rosePine ? (nameField.activeFocus ? NumaAppearance.accentColor : "transparent")
                        : nameField.activeFocus ? "#50ffffff" : "#20ffffff"
                }
            }
        }
    }

    Popup {
        id: deletePopup

        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(360, parent.width - 48)
        padding: 18
        modal: true
        focus: true
        closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
        Overlay.modal: Rectangle { color: "#66000000" }
        background: PopupBloomSurface { radius: 14 }

        contentItem: ColumnLayout {
            spacing: 12

            Text {
                Layout.fillWidth: true
                text: qsTr("Delete “%1”?").arg(root.playlist.title || "")
                color: NumaAppearance.textColor
                font.family: "Inter"
                font.pixelSize: 16
                font.weight: Font.DemiBold
                wrapMode: Text.WordWrap
            }
            Text {
                Layout.fillWidth: true
                text: qsTr("This local playlist and its songs will be removed from NÜMA, including other local playlists that contain them. Your audio files will stay on this computer.")
                color: NumaAppearance.mutedColor
                font.family: "Inter"
                font.pixelSize: 12
                wrapMode: Text.WordWrap
            }
            RowLayout {
                Layout.alignment: Qt.AlignRight
                spacing: 8

                Action {
                    text: qsTr("Cancel")
                    flat: true
                    onClicked: deletePopup.close()
                }
                Action {
                    text: qsTr("Delete")
                    onClicked: {
                        deletePopup.close();
                        NumaLocal.deletePlaylist(root.playlistId, true);
                        root.deleted();
                    }
                }
            }
        }
    }
}
