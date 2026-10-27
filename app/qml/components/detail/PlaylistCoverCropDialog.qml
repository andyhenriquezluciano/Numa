/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import ".."
import "../settings"

Dialog {
    id: root
    property string errorText: ""
    property url imageSource
    signal cropAccepted(rect area)
    parent: Overlay.overlay
    anchors.centerIn: parent
    width: Math.min(390, parent ? parent.width - 32 : 390)
    modal: true
    padding: 22
    title: qsTr("Adjust artwork")
    background: PopupBloomSurface { radius: 20 }
    header: Label {
        text: root.title
        padding: 22
        color: NumaAppearance.textColor
        font.family: "Inter"
        font.pixelSize: 20
    }
    function centerImage() {
        viewport.contentX = Math.max(0, (photo.width - viewport.width) / 2)
        viewport.contentY = Math.max(0, (photo.height - viewport.height) / 2)
    }
    onOpened: { errorText = ""; zoom.value = 1; Qt.callLater(centerImage) }
    contentItem: ColumnLayout {
        spacing: 14
        Label {
            Layout.fillWidth: true
            text: qsTr("Drag the image and zoom to frame the cover.")
            wrapMode: Text.WordWrap
            color: NumaAppearance.mutedColor
        }
        Flickable {
            id: viewport
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: Math.max(100, Math.min(root.availableWidth, (root.parent ? root.parent.height : 600) - 300))
            Layout.preferredHeight: width
            contentWidth: photo.width
            contentHeight: photo.height
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            Image {
                id: photo
                source: root.imageSource
                autoTransform: true
                readonly property real fit: sourceSize.width > 0 && sourceSize.height > 0 ? Math.max(viewport.width / sourceSize.width, viewport.height / sourceSize.height) : 1
                width: Math.max(viewport.width, sourceSize.width * fit * zoom.value)
                height: Math.max(viewport.height, sourceSize.height * fit * zoom.value)
                onStatusChanged: if (status === Image.Ready) Qt.callLater(root.centerImage)
            }
            Label {
                anchors.centerIn: parent
                visible: photo.status === Image.Error
                text: qsTr("This image could not be opened.")
                color: NumaAppearance.textColor
            }
        }
        Slider {
            id: zoom
            Layout.fillWidth: true
            from: 1; to: 3; value: 1
            Accessible.name: qsTr("Artwork zoom")
            onMoved: root.centerImage()
        }
        Label {
            Layout.fillWidth: true
            visible: root.errorText.length > 0
            text: root.errorText
            color: "#ef9999"
            wrapMode: Text.WordWrap
        }
        RowLayout {
            Layout.alignment: Qt.AlignRight
            SettingsButton { text: qsTr("Cancel"); onClicked: root.reject() }
            SettingsButton {
                objectName: "savePlaylistArtwork"
                text: qsTr("Save")
                enabled: photo.status === Image.Ready
                onClicked: {
                    root.cropAccepted(Qt.rect(viewport.contentX / photo.width, viewport.contentY / photo.height, viewport.width / photo.width, viewport.height / photo.height))
                }
            }
        }
    }
}
