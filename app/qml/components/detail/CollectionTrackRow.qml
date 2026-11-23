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
import ".."
import "../home"
import "../MediaNavigation.js" as MediaNavigation

// One song in an album or playlist. Must stay cheap: playlists run into the thousands.
ItemDelegate {
    id: root

    property Item artworkViewport: null
    HandCursor {}

    property var track: ({})
    property int trackIndex: 0
    // Views raise this while a freshly loaded list settles; rows built later by scrolling skip it.
    property bool reveal: false
    property bool showArtwork: true
    property bool showAlbum: false
    property bool showArtist: true
    property bool searchMatch: false
    property string fallbackArtist
    property var menuMedia: track
    property string removablePlaylistId: ""
    property string removablePlaylistTitle: ""
    property bool likedPlaylist: false
    property bool showLocalExtras: false
    property color accentColor: "#b79bed"
    property color accentSoftColor: "#c4a7e7"
    readonly property color rowAccent: NumaAppearance.rosePine ? NumaAppearance.borderColor : root.accentColor
    readonly property color rowAccentSoft: NumaAppearance.rosePine ? NumaAppearance.borderColor : root.accentSoftColor
    readonly property color playingIndicatorColor: NumaAppearance.rosePine ? NumaAppearance.accentColor
        : (root.Window.window?.playbackAccentColor ?? root.rowAccentSoft)

    readonly property bool current: Boolean(NumaPlayback.track.id) && NumaPlayback.track.id === track.id
    // The revision read makes the icon follow downloads finishing or being removed.
    readonly property bool downloaded: NumaDownloads.revision >= 0 && NumaDownloads.isDownloaded(track.id || "")
    readonly property real albumWidth: showAlbum ? Math.round(width * 0.26) : 0
    readonly property bool menuVisible: trackMenu.item?.visible ?? false
    readonly property string artistLabel: (track.artists && track.artists.length ? track.artists.join(", ") : "")
                                          || track.artist
                                          || fallbackArtist
                                          || qsTr("Unknown artist")

    signal albumRequested(var album)
    signal artistRequested(var artist)
    signal playlistRequested(var playlist)

    function tint(color, alpha) { return Qt.rgba(color.r, color.g, color.b, alpha); }

    height: showArtwork ? 60 : 54
    padding: 0
    hoverEnabled: true
    Accessible.name: (trackIndex + 1) + ". " + (track.title || qsTr("Untitled track")) + ", " + artistLabel
                     + (downloaded ? ", " + qsTr("downloaded") : "")

    function useTrackMenu(action) {
        trackMenu.active = true;
        if (trackMenu.item)
            action(trackMenu.item);
        Qt.callLater(releaseTrackMenu);
    }

    function releaseTrackMenu() {
        const menu = trackMenu.item;
        if (menu && !menu.visible && !menu.artistChoices.visible)
            trackMenu.active = false;
    }

    Loader {
        id: trackMenu
        objectName: "trackMenuLoader"
        active: false
        sourceComponent: Component {
            MediaMenu {
                media: root.menuMedia
                removablePlaylistId: root.removablePlaylistId
                removablePlaylistTitle: root.removablePlaylistTitle
                likedPlaylist: root.likedPlaylist
                onAlbumRequested: function(album) { root.albumRequested(album); }
                onArtistRequested: function(artist) { root.artistRequested(artist); }
                onPlaylistRequested: function(playlist) { root.playlistRequested(playlist); }
                onClosed: Qt.callLater(root.releaseTrackMenu)
            }
        }
    }
    Connections {
        target: trackMenu.item?.artistChoices ?? null
        function onClosed() { Qt.callLater(root.releaseTrackMenu); }
    }
    TapHandler {
        acceptedButtons: Qt.RightButton
        onTapped: function(point) {
            root.useTrackMenu(menu => menu.popup(root, point.position.x, point.position.y));
        }
    }

    background: RoundedSurfaceFrame {
        radius: 10
        softGlow: root.current || root.hovered || root.menuVisible || root.searchMatch
        showBorder: softGlow || root.activeFocus
        fillColor: NumaAppearance.rosePine ? (root.current || root.searchMatch ? NumaAppearance.highlightColor
                 : NumaAppearance.theme === "rose_pine_dawn" ? (root.hovered || root.menuVisible ? "#ede8e4" : "transparent")
                 : root.tint(NumaAppearance.textColor, (root.hovered || root.menuVisible) ? 0.08 : 0.0))
                 : root.searchMatch ? root.tint(root.rowAccent, 0.3)
                 : root.current ? root.tint(root.rowAccent, (root.hovered || root.menuVisible) ? 0.22 : 0.14)
                 : (root.hovered || root.menuVisible) ? "#16ffffff" : "transparent"
        border.color: NumaAppearance.rosePine ? (showBorder ? NumaAppearance.borderColor : "transparent")
                    : root.searchMatch ? root.rowAccent
                    : root.activeFocus ? root.rowAccentSoft
                    : root.current ? root.tint(root.rowAccentSoft, (root.hovered || root.menuVisible) ? 0.52 : 0.34)
                    : (root.hovered || root.menuVisible) ? "#32ffffff" : "transparent"
        glowColor: root.current ? root.rowAccentSoft : "#ffffff"
        Behavior on fillColor {
            enabled: NumaAppearance.theme !== "rose_pine_dawn"
            ColorAnimation { duration: 110 }
        }
        Behavior on border.color { ColorAnimation { duration: 110 } }
    }

    Component.onCompleted: if (reveal) { opacity = 0; entrance.start(); }
    // Capped at a dozen rows so the back of the line doesn't sit through the whole opening act.
    SequentialAnimation {
        id: entrance
        PauseAnimation { duration: Math.min(root.trackIndex, 12) * Motion.stagger }
        NumberAnimation { target: root; property: "opacity"; to: 1; duration: Motion.normal; easing.type: Motion.enter }
    }

    contentItem: Item {
        Item {
            id: numberCell
            x: 12
            width: 32
            height: parent.height

            Text {
                anchors.centerIn: parent
                visible: !root.hovered && !root.current
                text: root.trackIndex + 1
                color: NumaAppearance.textColor
                font.family: "Inter"
                font.pixelSize: 13
                font.features: { "tnum": 1 }
            }
            LucideIcon {
                anchors.centerIn: parent
                visible: root.hovered && !root.current
                name: "play"
                width: 16
                height: 16
                color: NumaAppearance.textColor
            }
            // Three dancing bars: the universal sign for "yes, this one".
            Loader {
                anchors.centerIn: parent
                active: root.current
                sourceComponent: Item {
                    width: 14
                    height: 14
                    Repeater {
                        model: [0.9, 0.55, 0.75]
                        Rectangle {
                            id: bar
                            objectName: "playingIndicatorBar"
                            required property real modelData
                            required property int index
                            x: index * 5
                            y: parent.height - height
                            width: 3
                            radius: 1.5
                            height: 14 * modelData
                            color: root.playingIndicatorColor
                            SequentialAnimation on height {
                                running: NumaPlayback.playing
                                loops: Animation.Infinite
                                NumberAnimation { to: 3; duration: 260 + bar.index * 90; easing.type: Easing.InOutSine }
                                NumberAnimation { to: 14; duration: 300 + bar.index * 70; easing.type: Easing.InOutSine }
                                NumberAnimation { to: 14 * bar.modelData; duration: 240 + bar.index * 60; easing.type: Easing.InOutSine }
                            }
                        }
                    }
                }
            }
        }

        RoundedArtwork {
            loadViewport: root.artworkViewport
            id: art
            x: numberCell.x + numberCell.width + 14
            anchors.verticalCenter: parent.verticalCenter
            visible: root.showArtwork
            width: visible ? 44 : 0
            height: 44
            radius: 7
            source: root.showArtwork ? (root.track.thumbnail || "") : ""
        }

        Column {
            id: titleBlock
            x: root.showArtwork ? art.x + art.width + 14 : numberCell.x + numberCell.width + 14
            width: durationText.x - x - 16 - (root.showAlbum ? root.albumWidth + 16 : 0)
            anchors.verticalCenter: parent.verticalCenter
            spacing: 3

            Row {
                width: parent.width
                spacing: 6
                Text {
                    width: Math.min(implicitWidth, parent.width - (badge.visible ? badge.width + 6 : 0)
                                    - (slopBadge.visible ? slopBadge.width + 6 : 0)
                                    - (downloadedIcon.visible ? downloadedIcon.width + 6 : 0)
                                    - (localLyricsIcon.visible ? 20 : 0) - (localArtworkIcon.visible ? 20 : 0))
                    text: root.track.title || qsTr("Untitled track")
                    color: root.current ? root.rowAccentSoft : NumaAppearance.textColor
                    font.family: "Inter"
                    font.pixelSize: 14
                    font.weight: Font.Medium
                    elide: Text.ElideRight
                }
                ExplicitBadge {
                    id: badge
                    visible: Boolean(root.track.explicit)
                    anchors.verticalCenter: parent.verticalCenter
                }
                SlopBadge {
                    id: slopBadge
                    trackId: root.track.id || ""
                    anchors.verticalCenter: parent.verticalCenter
                }
                LucideIcon {
                    id: localLyricsIcon
                    objectName: "localLyricsBadge"
                    visible: root.showLocalExtras && Boolean(root.track.hasDownloadedLyrics)
                    anchors.verticalCenter: parent.verticalCenter
                    name: "music-2"
                    width: 14; height: 14
                    color: NumaAppearance.accentColor
                    HoverHandler { id: lyricsHover }
                    GlassToolTip {
                        control: localLyricsIcon
                        visible: lyricsHover.hovered
                        text: qsTr("Downloaded lyrics")
                    }
                    Accessible.name: qsTr("Downloaded lyrics")
                }
                LucideIcon {
                    id: localArtworkIcon
                    objectName: "localArtworkBadge"
                    visible: root.showLocalExtras && Boolean(root.track.hasDownloadedArtwork)
                    anchors.verticalCenter: parent.verticalCenter
                    name: "sparkles"
                    width: 14; height: 14
                    color: NumaAppearance.accentColor
                    HoverHandler { id: artworkHover }
                    GlassToolTip {
                        control: localArtworkIcon
                        visible: artworkHover.hovered
                        text: qsTr("Downloaded animated artwork")
                    }
                    Accessible.name: qsTr("Downloaded animated artwork")
                }
                // Saved on this computer, so it plays without a connection.
                LucideIcon {
                    id: downloadedIcon
                    visible: root.downloaded
                    anchors.verticalCenter: parent.verticalCenter
                    width: 14
                    height: 14
                    name: "circle-arrow-down"
                    color: NumaAppearance.downloadedColor
                }
            }
            Text {
                visible: root.showArtist
                width: Math.min(implicitWidth, parent.width)
                text: root.artistLabel
                color: NumaAppearance.rosePine ? (artistMouse.containsMouse ? NumaAppearance.textColor : NumaAppearance.mutedColor) : artistMouse.containsMouse ? "white" : "#9ba19a"
                font.family: "Inter"
                font.pixelSize: 12
                font.underline: artistMouse.containsMouse
                elide: Text.ElideRight
                MouseArea {
                    id: artistMouse
                    anchors.fill: parent
                    enabled: MediaNavigation.artistIds(root.menuMedia).length > 0 && !NumaNetwork.offline
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: function(mouse) {
                        root.useTrackMenu(menu => menu.viewArtist(parent, mouse.x, mouse.y));
                    }
                }
            }
        }

        Text {
            x: durationText.x - 16 - root.albumWidth
            width: Math.min(implicitWidth, root.albumWidth)
            visible: root.showAlbum
            anchors.verticalCenter: parent.verticalCenter
            text: root.track.album || ""
            color: NumaAppearance.rosePine ? (albumMouse.containsMouse ? NumaAppearance.textColor : NumaAppearance.mutedColor) : albumMouse.containsMouse ? "white" : "#9ba19a"
            font.family: "Inter"
            font.pixelSize: 12
            font.underline: albumMouse.containsMouse
            elide: Text.ElideRight
            MouseArea {
                id: albumMouse
                anchors.fill: parent
                enabled: Boolean(MediaNavigation.albumId(root.menuMedia)) && !NumaNetwork.offline
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: root.useTrackMenu(menu => menu.viewAlbum())
            }
        }

        Text {
            id: durationText
            x: moreButton.x - width
            width: 48
            anchors.verticalCenter: parent.verticalCenter
            horizontalAlignment: Text.AlignRight
            text: root.track.duration || "–"
            color: NumaAppearance.rosePine ? NumaAppearance.mutedColor : "#8c928c"
            font.family: "Inter"
            font.pixelSize: 12
            font.features: { "tnum": 1 }
        }

        ToolButton {
            id: moreButton
            HandCursor {}
            x: parent.width - 8 - width
            width: 36
            height: 36
            anchors.verticalCenter: parent.verticalCenter
            padding: 10
            opacity: root.hovered || root.menuVisible || activeFocus ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: 110 } }
            Accessible.name: qsTr("More options for %1").arg(root.track.title || qsTr("track"))
            onClicked: root.useTrackMenu(menu => menu.popup(moreButton, 0, moreButton.height))
            background: Item {}
            contentItem: LucideIcon { name: "ellipsis"; color: NumaAppearance.textColor }
        }
    }
}
