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
            if (root.ownerKind === "playlist")
                NumaLocal.setPlaylistCover(root.ownerId, selectedFile);
            else
                NumaLocal.setTrackCover(root.ownerId, selectedFile);
        }
    }

    FileDialog {
        id: lyricsDialog
        title: qsTr("Choose lyrics")
        nameFilters: NumaLocal.lyricsNameFilters
        onAccepted: NumaLocal.setTrackLyrics(root.ownerId, selectedFile)
    }
}
