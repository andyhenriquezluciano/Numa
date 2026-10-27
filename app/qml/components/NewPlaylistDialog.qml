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

// Names a new private playlist, on YouTube Music or on this computer. A seed
// song is optional: the picker passes one, the sidebar and library do not.
// Created on demand, destroyed on close.
Popup {
    id: root

    property var track: ({})
    // "youtube" or "local". Only a song's own kind can seed a playlist.
    property string source: root.seedIsLocal || NumaNetwork.offline ? "local" : "youtube"
    readonly property bool hasSeed: Boolean(root.track && root.track.id)
    readonly property bool seedIsLocal: root.hasSeed && String(root.track.id).indexOf("local:") === 0
    readonly property bool local: root.source === "local"
    readonly property bool canCreate: nameField.text.trim().length > 0
        && (root.local || !NumaLibrary.saving)

    function create(thenAddSongs) {
        if (!root.canCreate)
            return;
        const name = nameField.text;
        if (root.local) {
            const files = root.seedIsLocal ? [root.track.localPath] : [];
            const id = NumaLocal.createPlaylist(name, files);
            if (thenAddSongs)
                LocalPickers.addSongs(id);
        } else if (root.hasSeed) {
            NumaLibrary.createPlaylist(root.track, name);
        } else {
            NumaLibrary.createEmptyPlaylist(name);
        }
        root.close();
    }

    function hint() {
        if (root.local)
            return root.hasSeed ? qsTr("“%1” will be added to it.").arg(root.track.title || qsTr("This song"))
                                : qsTr("Songs stay where they are on your computer. NÜMA only remembers the list.");
        return root.hasSeed ? qsTr("“%1” will be added to it. New playlists start private.").arg(root.track.title || qsTr("This song"))
                            : qsTr("Created on YouTube Music, private until you share it.");
    }

    parent: Overlay.overlay
    anchors.centerIn: parent
    width: Math.min(400, parent.width - 48)
    padding: 20
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
    onOpened: nameField.forceActiveFocus()
    onClosed: root.destroy()

    Overlay.modal: Rectangle {
        color: "transparent"
    }

    enter: Transition {
        NumberAnimation { property: "opacity"; from: 0.0; to: 1.0; duration: 160; easing.type: Easing.OutCubic }
    }

    exit: Transition {
        NumberAnimation { property: "opacity"; from: 1.0; to: 0.0; duration: 120; easing.type: Easing.InCubic }
    }

    background: PopupBloomSurface {
        radius: 14
        tintOpacity: 0.12
        refractionEnabled: false
    }

    // One half of the source switch. A song can only live in its own kind of playlist.
    component SourceChoice: Button {
        id: choice

        property string value
        readonly property bool chosen: root.source === value

        Layout.fillWidth: true
        implicitHeight: 34
        enabled: (!root.hasSeed || root.seedIsLocal === (value === "local")) && (value === "local" || !NumaNetwork.offline)
        onClicked: root.source = value
        contentItem: Text {
            text: choice.text
            color: choice.chosen ? "#ffffff" : choice.enabled ? "#e5e8e3" : "#b0b5ae"
            font.family: "Inter"
            font.pixelSize: 12
            font.weight: Font.DemiBold
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
        background: RoundedSurfaceFrame {
            radius: 8
            fillColor: choice.chosen ? "#26ffffff" : choice.hovered && choice.enabled ? "#22ffffff" : "#14ffffff"
            border.color: choice.chosen || choice.activeFocus ? "#60ffffff" : "#30ffffff"
        }
    }

    contentItem: ColumnLayout {
        spacing: 14

        Text {
            text: qsTr("New playlist")
            color: "#f2f0eb"
            font.family: "Inter"
            font.pixelSize: 17
            font.weight: Font.DemiBold
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 6

            SourceChoice { text: qsTr("YouTube Music"); value: "youtube" }
            SourceChoice { text: qsTr("On this computer"); value: "local" }
        }

        Text {
            Layout.fillWidth: true
            text: root.hint()
            color: "#d0d5cd"
            font.family: "Inter"
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }

        TextField {
            id: nameField

            Layout.fillWidth: true
            implicitHeight: 40
            leftPadding: 14
            rightPadding: 14
            selectByMouse: true
            maximumLength: 150
            placeholderText: qsTr("Playlist name")
            placeholderTextColor: "#bbc2b8"
            color: NumaAppearance.textColor
            font.family: "Inter"
            font.pixelSize: 13
            onAccepted: root.create(false)
            background: RoundedSurfaceFrame {
                radius: 10
                fillColor: "#30101410"
                border.color: nameField.activeFocus ? "#50ffffff" : "#20ffffff"
            }
        }

        RowLayout {
            Layout.alignment: Qt.AlignRight
            spacing: 8

            Button {
                id: cancelButton

                text: qsTr("Cancel")
                flat: true
                onClicked: root.close()
                contentItem: Text {
                    text: cancelButton.text
                    color: "#d6d9d4"
                    font.family: "Inter"
                    font.pixelSize: 13
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                background: RoundedSurfaceFrame {
                    implicitWidth: 84
                    implicitHeight: 34
                    radius: 8
                    fillColor: cancelButton.hovered ? "#22ffffff" : "#14ffffff"
                }
            }

            // Local playlists usually start from a pile of files, so offer to pick them straight away.
            Button {
                id: withSongsButton

                visible: root.local && !root.hasSeed
                text: qsTr("Create and add songs…")
                enabled: root.canCreate
                onClicked: root.create(true)
                contentItem: Text {
                    text: withSongsButton.text
                    color: withSongsButton.enabled ? "#f2f4ef" : "#aab1a6"
                    font.family: "Inter"
                    font.pixelSize: 13
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                background: RoundedSurfaceFrame {
                    implicitHeight: 34
                    implicitWidth: 168
                    radius: 8
                    fillColor: withSongsButton.hovered && withSongsButton.enabled ? "#22ffffff" : "#14ffffff"
                }
            }

            Button {
                id: createButton

                text: qsTr("Create")
                enabled: root.canCreate
                onClicked: root.create(false)
                contentItem: Text {
                    text: createButton.text
                    color: createButton.enabled ? "#ffffff" : "#aab1a6"
                    font.family: "Inter"
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                background: RoundedSurfaceFrame {
                    implicitWidth: 84
                    implicitHeight: 34
                    radius: 8
                    fillColor: createButton.enabled && createButton.hovered ? "#2effffff" : "#18ffffff"
                }
            }
        }
    }
}
