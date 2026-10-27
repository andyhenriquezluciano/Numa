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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import ".."
import "../home"

// Album and playlist side panel: cover, titles, actions and blurb in one column.
Item {
    id: root

    property string kind
    property var cloudMedia: ({})
    property string title
    property string byline
    property bool bylineInteractive: false
    property string year
    property var meta: []
    property string description
    property string artwork
    property bool gradientCover: false
    property bool localGradientCover: false
    property url animatedArtwork
    // Albums behind a four-cover playlist collage; see CollageArtwork.
    property var collageAlbums: []
    property bool explicit: false
    // Qobuz quality for albums it can stream; see QualityBadge.
    property var streamQuality: ({})
    property bool compact: false
    property bool actionsEnabled: true
    property bool playing: false
    property bool searchable: false
    // "none", "partial", "busy" or "done" for the songs on this page.
    property bool downloadable: false
    property string downloadState: "none"
    property bool downloadEnabled: true
    property string shareLabel
    property bool shareEnabled: true
    property bool shareVisible: true
    property bool deleteVisible: false

    property color accentColor: "#7fbe90"
    property color accentSoftColor: "#96caa4"
    property color accentInkColor: "#09100b"
    property color inkColor: "#080c0a"

    readonly property int coverRadius: 12
    readonly property real coverSize: compact ? Math.min(width, 240) : Math.max(0, width - 16)

    signal bylineClicked(Item anchor, real x, real y)
    signal playClicked
    signal shuffleClicked
    signal shareClicked
    signal deleteClicked
    signal shareOpenRequested
    signal searchClicked
    signal downloadClicked
    signal removeDownloadsClicked

    function tint(color, alpha) { return Qt.rgba(color.r, color.g, color.b, alpha); }

    implicitHeight: card.height

    component GhostButton: CollectionActionButton {
        width: root.width < 340 ? 36 : 40
        padding: root.width < 340 ? 9 : 11
        accentColor: root.accentSoftColor
    }

    Column {
        id: card
        width: root.width
        spacing: 0

        Item {
            id: cover
            width: root.coverSize
            height: root.coverSize
            x: (card.width - width) / 2

            RoundedArtwork {
                anchors.fill: parent
                radius: root.coverRadius
                source: root.gradientCover ? "" : root.artwork
                visible: !root.gradientCover
            }
            Rectangle {
                anchors.fill: parent
                radius: root.coverRadius
                visible: root.gradientCover
                antialiasing: true
                gradient: Gradient {
                    GradientStop { position: 0; color: root.localGradientCover ? "#35c8ed" : "#9464ee" }
                    GradientStop { position: 1; color: root.localGradientCover ? "#7950dc" : "#f54fa1" }
                }
                LucideIcon {
                    anchors.centerIn: parent
                    width: parent.width * 0.42
                    height: width
                    name: "list-music"
                    color: "#ffffff"
                }
            }
            Loader {
                anchors.fill: parent
                active: !root.gradientCover && NumaAppearance.animatedArtworkEnabled
                        && root.animatedArtwork.toString().length > 0 && root.visible
                sourceComponent: AnimatedArtwork {
                    radius: root.coverRadius
                    source: root.animatedArtwork
                    playing: root.visible
                }
            }
            Loader {
                anchors.fill: parent
                // Opt-in separately: this one runs up to four videos at once.
                active: !root.gradientCover && NumaAppearance.animatedArtworkEnabled
                        && NumaAppearance.animatedCollageEnabled
                        && root.collageAlbums.length === 4 && root.visible
                sourceComponent: CollageArtwork {
                    radius: root.coverRadius
                    albums: root.collageAlbums
                    playing: root.visible
                }
            }
            LucideIcon {
                anchors.centerIn: parent
                width: 56
                height: 56
                name: "music-2"
                color: root.tint(root.accentSoftColor, 0.7)
                visible: !root.artwork && !root.gradientCover
            }
            Rectangle {
                anchors.fill: parent
                radius: root.coverRadius
                color: "transparent"
                border.color: "#1affffff"
                visible: !root.gradientCover
            }
        }

        Item { width: 1; height: 16 }

        Row {
            spacing: 6
            Text {
                text: [root.kind, root.year].filter(Boolean).join("  ·  ")
                color: "#9aa09a"
                font.family: "Inter"
                font.pixelSize: 12
            }
            ExplicitBadge {
                visible: root.explicit
                anchors.verticalCenter: parent.verticalCenter
            }
            QualityBadge {
                anchors.verticalCenter: parent.verticalCenter
                quality: root.streamQuality
                color: root.tint(root.accentSoftColor, 0.18)
                textColor: root.accentSoftColor
            }
        }
        Item { width: 1; height: 6 }
        Text {
            width: parent.width
            text: root.title
            color: "#f6f4ef"
            font.family: "Inter"
            font.pixelSize: root.title.length > 40 ? 22 : 28
            font.weight: Font.Bold
            font.letterSpacing: -0.5
            lineHeight: 1.0
            wrapMode: Text.Wrap
            maximumLineCount: 3
            elide: Text.ElideRight
        }
        Item { width: 1; height: bylineText.visible ? 10 : 0 }
        Text {
            id: bylineText
            width: Math.min(implicitWidth, parent.width)
            visible: text.length > 0
            text: root.byline
            color: bylineMouse.containsMouse ? "white" : "#f6f4ef"
            font.family: "Inter"
            font.pixelSize: 14
            font.weight: Font.DemiBold
            font.underline: bylineMouse.containsMouse
            elide: Text.ElideRight
            MouseArea {
                id: bylineMouse
                anchors.fill: parent
                enabled: root.bylineInteractive
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: function(mouse) { root.bylineClicked(bylineText, mouse.x, mouse.y); }
            }
        }
        Item { width: 1; height: 4 }
        Text {
            width: parent.width
            visible: text.length > 0
            text: root.meta.filter(Boolean).join("  ·  ")
            color: "#9aa09a"
            font.family: "Inter"
            font.pixelSize: 12
            elide: Text.ElideRight
        }
        Item { width: 1; height: 20 }

        Row {
            spacing: root.width < 340 ? 6 : 8

            Button {
                id: playButton
                width: playRow.implicitWidth + 36
                height: 40
                enabled: root.actionsEnabled
                opacity: enabled ? 1 : 0.4
                Accessible.name: root.playing ? qsTr("Pause") : qsTr("Play %1").arg(root.kind.toLowerCase())
                ToolTip.visible: false
                ToolTip.text: root.playing ? qsTr("Pause") : qsTr("Play")
                onClicked: root.playClicked()
                scale: down ? 0.95 : 1
                Behavior on scale { NumberAnimation { duration: 120; easing.type: Easing.OutCubic } }
                background: Item {
                    Rectangle {
                        anchors.fill: parent
                        radius: 10
                        color: playButton.down ? "#26ffffff" : playButton.hovered ? "#22ffffff" : "#14ffffff"
                        Behavior on color { ColorAnimation { duration: 120 } }
                    }
                    RoundedSurfaceFrame {
                        anchors.fill: parent
                        radius: 10
                        softGlow: true
                        showBorder: true
                        border.color: playButton.activeFocus ? root.accentSoftColor : (playButton.hovered ? "#45ffffff" : "#30ffffff")
                        fillColor: "transparent"
                    }
                }
                contentItem: Item {
                    Row {
                        id: playRow
                        anchors.centerIn: parent
                        spacing: 8
                        LucideIcon {
                            width: 16
                            height: 16
                            anchors.verticalCenter: parent.verticalCenter
                            name: root.playing ? "pause" : "play"
                            color: "#f7f5f0"
                        }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            text: root.playing ? qsTr("Pause") : qsTr("Play")
                            color: "#f7f5f0"
                            font.family: "Inter"
                            font.pixelSize: 13
                            font.weight: Font.DemiBold
                        }
                    }
                }

                GlassToolTip {
                    control: playButton
                    text: playButton.ToolTip.text
                    accentColor: root.accentSoftColor
                }
            }
            GhostButton {
                glyph: "shuffle"
                enabled: root.actionsEnabled
                Accessible.name: qsTr("Shuffle")
                ToolTip.text: qsTr("Shuffle")
                onClicked: root.shuffleClicked()
            }
            GhostButton {
                id: downloadButton
                visible: root.downloadable
                glyph: root.downloadState === "done" ? "circle-arrow-down" : "download"
                glyphColor: root.downloadState === "done" ? root.accentSoftColor : "#eef0ec"
                enabled: root.downloadEnabled
                opacity: !enabled ? 0.4 : root.downloadState === "busy" ? 0.7 : 1
                Accessible.name: ToolTip.text
                ToolTip.text: root.downloadState === "done" ? qsTr("Downloaded. Click for options.")
                    : root.downloadState === "busy" ? qsTr("Downloading…")
                    : root.downloadState === "partial" ? qsTr("Download the rest for offline listening")
                    : qsTr("Download for offline listening")
                onClicked: {
                    if (root.downloadState === "done")
                        downloadMenu.popup(downloadButton, 0, downloadButton.height);
                    else
                        root.downloadClicked();
                }
            }
            CloudCollectionAction { media: root.cloudMedia; accentColor: root.accentSoftColor; buttonWidth: root.width < 340 ? 36 : 40 }
            GhostButton {
                visible: root.deleteVisible
                glyph: "trash-2"
                Accessible.name: qsTr("Delete playlist")
                ToolTip.text: qsTr("Delete playlist")
                onClicked: root.deleteClicked()
            }
            GhostButton {
                glyph: "share-2"
                visible: root.shareVisible
                enabled: root.shareEnabled
                Accessible.name: root.shareLabel
                ToolTip.text: qsTr("%1 (right-click to open)").arg(root.shareLabel)
                onClicked: root.shareClicked()
                TapHandler {
                    acceptedButtons: Qt.RightButton
                    onTapped: root.shareOpenRequested()
                }
            }
            GhostButton {
                visible: root.searchable
                glyph: "search"
                enabled: root.actionsEnabled
                Accessible.name: qsTr("Find in %1").arg(root.kind.toLowerCase())
                ToolTip.text: qsTr("Find in %1 (or just start typing)").arg(root.kind.toLowerCase())
                onClicked: root.searchClicked()
            }
        }

        Item { width: 1; height: descriptionText.visible ? 20 : 0 }
        Text {
            id: descriptionText
            property bool expanded: false
            width: parent.width
            visible: text.length > 0
            text: root.description
            color: "#9aa09a"
            font.family: "Inter"
            font.pixelSize: 12
            lineHeight: 1.35
            wrapMode: Text.Wrap
            maximumLineCount: expanded ? 40 : 4
            elide: Text.ElideRight
        }
        Item { width: 1; height: readMore.visible ? 14 : 0 }
        Text {
            id: readMore
            visible: descriptionText.truncated || descriptionText.expanded
            text: descriptionText.expanded ? qsTr("Show less") : qsTr("Read more")
            color: readMoreMouse.containsMouse ? "white" : "#d8dcd6"
            font.family: "Inter"
            font.pixelSize: 12
            font.weight: Font.DemiBold
            MouseArea {
                id: readMoreMouse
                anchors.fill: parent
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: descriptionText.expanded = !descriptionText.expanded
            }
        }
    }

    Menu {
        id: downloadMenu
        width: 210
        padding: 5
        background: PopupBloomSurface { accentColor: root.accentColor; radius: 12 }

        MenuItem {
            id: removeItem
            text: qsTr("Remove downloads")
            implicitHeight: 36
            onTriggered: root.removeDownloadsClicked()
            background: Rectangle { radius: 6; color: removeItem.highlighted ? "#20ffffff" : "transparent" }
            contentItem: Text {
                text: removeItem.text
                color: "#f2f0eb"
                font.family: "Inter"
                font.pixelSize: 13
                verticalAlignment: Text.AlignVCenter
            }
        }
    }
}
