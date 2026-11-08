/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import ".."

SettingsSection {
    id: root
    property var downloads: typeof NumaLocalDownloads !== "undefined" ? NumaLocalDownloads : null
    visible: downloads !== null
    title: qsTr("Local songs")
    Layout.fillWidth: true
    Text {
        Layout.fillWidth: true
        text: qsTr("Choose what to save for your local songs, then start the download. Original audio files are kept unchanged.")
        color: NumaAppearance.mutedColor
        font.pixelSize: 13
        wrapMode: Text.Wrap
    }
    Repeater {
        model: [qsTr("Lyrics"), qsTr("Translation"), qsTr("Animated artwork")]
        SettingsRow {
            id: option
            required property int index
            required property string modelData
            title: modelData
            iconName: index === 0 ? "music-2" : index === 1 ? "languages" : "sparkles"
            SettingsSwitch {
                objectName: "localDownloadSwitch" + option.index
                enabled: root.downloads && !root.downloads.busy
                checked: root.downloads ? (option.index === 0 ? root.downloads.lyrics : option.index === 1 ? root.downloads.translation : root.downloads.artwork) : false
                Accessible.name: option.modelData
                HandCursor {}
                onToggled: {
                    if (option.index === 0) root.downloads.lyrics = checked;
                    else if (option.index === 1) root.downloads.translation = checked;
                    else root.downloads.artwork = checked;
                }
            }
        }
    }
    Text {
        Layout.fillWidth: true
        text: qsTr("Translation is saved together with lyrics, in the language selected for lyric translation. Extras are only available when a matching source is found.")
        color: NumaAppearance.mutedColor
        font.pixelSize: 12
        wrapMode: Text.Wrap
    }
    Row {
        spacing: 8
        SettingsButton {
            text: root.downloads && root.downloads.busy ? qsTr("Stop") : qsTr("Download for local songs")
            surfaceBackground: true
            enabled: root.downloads && (root.downloads.busy || ((!NumaNetwork.offline) && (root.downloads.lyrics || root.downloads.artwork) && NumaLocal.songs.length > 0))
            onClicked: root.downloads.busy ? root.downloads.cancel() : root.downloads.start()
        }
    }
    ProgressBar {
        id: downloadProgress
        visible: root.downloads && root.downloads.busy
        Layout.fillWidth: true
        implicitHeight: 8
        padding: 0
        from: 0
        to: root.downloads ? Math.max(1, root.downloads.total) : 1
        value: root.downloads ? root.downloads.completed : 0
        background: Rectangle {
            implicitHeight: 8
            radius: height / 2
            color: Qt.rgba(NumaAppearance.textColor.r, NumaAppearance.textColor.g, NumaAppearance.textColor.b, 0.12)
        }
        contentItem: Item {
            Rectangle {
                width: parent.width * downloadProgress.visualPosition
                height: parent.height
                radius: height / 2
                color: NumaAppearance.accentColor
                Behavior on width {
                    enabled: downloadProgress.visible && downloadProgress.value > 0
                    NumberAnimation { duration: 280; easing.type: Easing.OutCubic }
                }
            }
        }
        Accessible.name: qsTr("Local songs")
        Accessible.description: root.downloads ? qsTr("%1 of %2 songs processed").arg(root.downloads.completed).arg(root.downloads.total) : ""
    }
    Text {
        Layout.fillWidth: true
        visible: Boolean(text)
        text: root.downloads ? (root.downloads.busy ? qsTr("%1 of %2 songs processed").arg(root.downloads.completed).arg(root.downloads.total) + " · " : "") + root.downloads.message : ""
        color: NumaAppearance.mutedColor
        font.pixelSize: 12
        wrapMode: Text.Wrap
    }
}
