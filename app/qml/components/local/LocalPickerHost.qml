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
import QtQuick.Dialogs
import QtQuick.Window

// The only owner of the file dialogs; see LocalPickers.
Item {
    id: root

    property string playlistId: ""
    property string ownerKind: ""
    property string ownerId: ""

    function addSongs(id) {
        root.playlistId = id;
        songDialog.open();
    }

    function addFolder(id) {
        root.playlistId = id;
        folderDialog.open();
    }

    function pickCover(kind, id) {
        root.ownerKind = kind;
        root.ownerId = id;
        coverDialog.open();
    }

    function pickLyrics(id) {
        root.ownerId = id;
        lyricsDialog.open();
    }

    visible: false
    // Dialogs need to find a window; the host is parked in the main one.
    Component.onCompleted: LocalPickers.host = root

    Popup {
        id: importProgress
        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(400, parent.width - 48)
        padding: 24
        modal: true
        focus: true
        closePolicy: Popup.NoAutoClose
        visible: Boolean(NumaLocal.importing)
        Overlay.modal: Rectangle { color: "#30000000" }
        background: PopupBloomSurface {
            radius: 14
            tintOpacity: 0.12
            refractionEnabled: false
        }
        contentItem: ColumnLayout {
            spacing: 16
            Label {
                Layout.fillWidth: true
                text: NumaLocal.scanningImport ? qsTr("Finding audio files…") : qsTr("Adding songs…")
                color: NumaAppearance.textColor
                font.pixelSize: 18
                font.bold: true
                wrapMode: Text.Wrap
            }
            Label {
                Layout.fillWidth: true
                text: NumaLocal.scanningImport ? qsTr("Preparing the folder. This may take a moment.")
                    : qsTr("%1 of %2 songs processed").arg(NumaLocal.importCompleted || 0).arg(NumaLocal.importTotal || 0)
                color: NumaAppearance.mutedColor
                wrapMode: Text.Wrap
            }
            ProgressBar {
                Layout.fillWidth: true
                from: 0
                to: Math.max(1, NumaLocal.importTotal || 0)
                value: NumaLocal.importCompleted || 0
                indeterminate: Boolean(NumaLocal.scanningImport)
                palette.highlight: NumaAppearance.accentColor
                palette.dark: NumaAppearance.overlayColor
            }
        }
    }

    FileDialog {
        id: songDialog
        title: qsTr("Add songs")
        fileMode: FileDialog.OpenFiles
        nameFilters: NumaLocal.audioNameFilters
        onAccepted: NumaLocal.importFiles(selectedFiles, root.playlistId)
    }

    FolderDialog {
        id: folderDialog
        title: qsTr("Add a music folder")
        onAccepted: NumaLocal.importFiles([selectedFolder], root.playlistId)
    }

    FileDialog {
        id: coverDialog
        title: qsTr("Choose a cover")
        nameFilters: NumaLocal.imageNameFilters
        onAccepted: {
            if (root.ownerKind === "playlist") {
                const animated = /\.(gif|mp4|webm|mov|m4v)$/i.test(selectedFile.toString());
                if (animated) {
                    NumaLocal.setPlaylistCover(root.ownerId, selectedFile);
                } else {
                    localCrop.playlistId = root.ownerId;
                    localCrop.imageSource = selectedFile;
                    localCrop.open();
                }
            } else {
                NumaLocal.setTrackCover(root.ownerId, selectedFile);
            }
        }
    }

    PlaylistCoverCropDialog {
        id: localCrop
        property string playlistId
        onCropAccepted: area => {
            if (NumaLocal.setPlaylistCoverCrop(playlistId, imageSource, area)) accept();
            else errorText = qsTr("The artwork could not be saved. Try again.");
        }
    }

    FileDialog {
        id: lyricsDialog
        title: qsTr("Choose lyrics")
        nameFilters: NumaLocal.lyricsNameFilters
        onAccepted: NumaLocal.setTrackLyrics(root.ownerId, selectedFile)
    }
}
